#!/usr/bin/env python3
"""Run the packaged Java server on the host; does not claim Android device validation."""
import json
import os
from pathlib import Path
import shutil
import socket
import sqlite3
import struct
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'app_pojavlauncher/build'
ASSETS = BUILD / 'generated/singleplayer/assets/singleplayer'
WORK = BUILD / 'singleplayer-test'


def main():
    if WORK.exists(): shutil.rmtree(WORK)
    WORK.mkdir(parents=True)
    for name in ('world', 'engine'):
        with zipfile.ZipFile(ASSETS / f'{name}.zip') as archive:
            archive.extractall(WORK / name)
    # Different JVM native libraries on the host; use SQLite's packaged Linux variant.
    command = ['java', '-Xms256m', '-Xmx2g', '-Djava.awt.headless=true', '-Dscape.session=integration-test',
               '-cp', os.pathsep.join(str(WORK / 'engine' / f) for f in ('bootstrap.jar', 'sqlite.jar', 'lib/*', 'server.jar')),
               'scape.ServerMain', 'worldprops/default.conf']
    store = WORK / 'world/data/serverstore'
    store.mkdir(parents=True, exist_ok=True)
    sentinel = store / 'android-integration.json'
    sentinel.write_text(json.dumps({'preserved': 'android-save-reload', 'value': 42}))
    for cycle in range(2):
        logpath = WORK / f'session-{cycle+1}.log'
        with logpath.open('w') as log:
            p = subprocess.Popen(command, cwd=WORK / 'world', stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
            try:
                deadline = time.monotonic() + 180
                while time.monotonic() < deadline:
                    log.flush()
                    if '[scape] SERVER_READY integration-test' in logpath.read_text(): break
                    if p.poll() is not None: raise AssertionError(f'Startup failed: {logpath.read_text()}')
                    time.sleep(.2)
                else: raise AssertionError('Startup timed out')
                # The RS530 JS5 handshake must be answered by this server.
                with socket.create_connection(('127.0.0.1', 43595), timeout=3) as client:
                    client.sendall(bytes([15]) + struct.pack('>I', 530))
                    response = client.recv(1)
                    assert response == b'\x00', f'Unexpected JS5 response: {response!r}'
                # Check the socket really is local, not wildcard, in Linux procfs.
                port = f'{43595:04X}'
                listeners = []
                for proc in ('/proc/net/tcp', '/proc/net/tcp6'):
                    if Path(proc).exists(): listeners += Path(proc).read_text().splitlines()[1:]
                matches = [line.split()[1].split(':')[0] for line in listeners if line.split()[1].endswith(':' + port) and line.split()[3] == '0A']
                # HotSpot may use an IPv4-mapped IPv6 socket on dual-stack hosts.
                assert len(matches) == 1 and matches[0] in ('0100007F', '0000000000000000FFFF00000100007F'), matches
                # Real JVM measurements must arrive during normal operation; no forced GC.
                deadline = time.monotonic() + 40
                while time.monotonic() < deadline:
                    samples = [json.loads(line.removeprefix('[scape-memory] '))
                               for line in logpath.read_text().splitlines() if line.startswith('[scape-memory] ')]
                    phases = {sample['phase'] for sample in samples}
                    if {'startup', 'ready', 'periodic'} <= phases: break
                    if p.poll() is not None: raise AssertionError(logpath.read_text())
                    time.sleep(.2)
                else: raise AssertionError('Memory diagnostics missing: ' + logpath.read_text())
                for sample in samples:
                    # The first MXBean snapshot can report zero before allocation accounting updates.
                    assert 0 <= sample['heapUsedBytes'] <= sample['heapCommittedBytes'] <= sample['heapMaxBytes']
                    if sample['phase'] != 'startup': assert sample['heapUsedBytes'] > 0
                    assert sample['heapMaxBytes'] == 2 * 1024 ** 3
                    assert sample['threads'] > 0 and sample['rssKiB'] > 0
                    assert sample['gcCount'] >= 0 and sample['gcTimeMs'] >= 0
                assert samples[-1]['uptimeMs'] > samples[0]['uptimeMs']
                p.stdin.write('stop\n'); p.stdin.flush()
                assert p.wait(timeout=45) == 0, logpath.read_text()
                assert 'Terminating' in logpath.read_text(), 'Shutdown hook did not run'
                assert 'NullPointerException' not in logpath.read_text(), logpath.read_text()
                assert 'ScriptException' not in logpath.read_text(), logpath.read_text()
            finally:
                if p.poll() is None:
                    p.terminate()
                    try: p.wait(timeout=10)
                    except subprocess.TimeoutExpired: p.kill(); p.wait()
        db = WORK / 'world/data/eco/grandexchange.db'
        assert db.is_file(), 'Economy database not persisted'
        with sqlite3.connect(db) as connection:
            assert connection.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
        store = WORK / 'world/data/serverstore'
        assert store.exists(), 'World store not persisted'
        assert json.loads(sentinel.read_text()) == {'preserved': 'android-save-reload', 'value': 42}
        for entry in store.glob('*.json'): json.loads(entry.read_text())
        print(f'Cycle {cycle+1}: boot, local bind, JS5 handshake, memory samples, shutdown, SQLite integrity and world store passed')
    print('Host integration passed. Android execution and interactive player save/reload remain device tests.')


if __name__ == '__main__': main()
