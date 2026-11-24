#!/usr/bin/env python3
"""Verify the distributable server and the assets needed for first client launch."""
import hashlib
import io
import json
import os
import subprocess
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import zipfile

from client_runtime import ASSET_PATH, SHA256 as CLIENT_JRE_SHA256

source_assets = Path(__file__).resolve().parents[1] / 'app_pojavlauncher/src/main/assets'


def verify_client_assets(apk):
    # These are consumed before a fresh installation can launch a client.
    shipped = [
        'rt4.jar',
        'config.json',
        'components/caciocavallo/version',
        'components/caciocavallo/ResConfHack.jar',
        'components/caciocavallo/cacio-androidnw-1.10-SNAPSHOT.jar',
        'components/caciocavallo/cacio-shared-1.10-SNAPSHOT.jar',
        'components/caciocavallo17/version',
        'components/caciocavallo17/cacio-shared-1.18-SNAPSHOT.jar',
        'components/caciocavallo17/cacio-tta-1.18-SNAPSHOT.jar',
        'components/lwjgl3/lwjgl-glfw-classes.jar',
        'components/security/version',
        'components/security/java_sandbox.policy',
        'components/security/log4j-rce-patch-1.12.xml',
        'components/security/log4j-rce-patch-1.7.xml',
        'components/security/pro-grade.jar',
    ]
    for path in shipped:
        content = apk.read('assets/' + path)
        assert content and content == (source_assets / path).read_bytes(), path

    for name, expected in CLIENT_JRE_SHA256.items():
        assert hashlib.sha256(apk.read(ASSET_PATH + name)).hexdigest() == expected, name
    assert 'lib/arm64-v8a/libawt_xawt.so' in apk.namelist()

    defaults = json.loads(apk.read('assets/config.json'))
    assert isinstance(defaults, dict), 'Client configuration must be a JSON object'
    jar = apk.read('assets/components/lwjgl3/lwjgl-glfw-classes.jar')
    marker = apk.read('assets/components/lwjgl3/version')
    expected_marker = hashlib.sha256(jar).hexdigest().encode('ascii')
    assert marker == expected_marker, 'LWJGL component version does not match its JAR'


def verify_software_view_distance(apk):
    test = Path(__file__).with_name('SoftwareViewDistanceTest.java').resolve()
    with TemporaryDirectory() as directory:
        work = Path(directory)
        for name, entry in [('rt4.jar', 'assets/rt4.jar'), ('ViewDistance.zip', 'assets/plugins/ViewDistance.zip')]:
            (work / name).write_bytes(apk.read(entry))
        classpath = os.pathsep.join(['rt4.jar', 'ViewDistance.zip'])
        subprocess.run(['java', '-Djava.awt.headless=true', '-DpluginDir=.',
                        '-Dorg.lwjgl.libname=' + str(work / 'no-gl-context'),
                        '-cp', classpath, str(test)], cwd=work, check=True, timeout=30)


with zipfile.ZipFile(sys.argv[1]) as apk:
    verify_client_assets(apk)
    verify_software_view_distance(apk)
    manifest = json.loads(apk.read('assets/singleplayer/manifest.json'))
    for name, digest in manifest['archives'].items():
        assert hashlib.sha256(apk.read('assets/singleplayer/' + name)).hexdigest() == digest, name
    for name, digest in manifest['native'].items():
        data = apk.read('lib/arm64-v8a/' + name)
        assert hashlib.sha256(data).hexdigest() == digest, name
        assert data[:6] == b'\x7fELF\x02\x01' and int.from_bytes(data[18:20], 'little') == 183, name
    assert 'lib/arm64-v8a/libscape_server.so' in apk.namelist()
    with zipfile.ZipFile(io.BytesIO(apk.read('assets/singleplayer/runtime.zip'))) as runtime:
        assert 'lib/modules' in runtime.namelist()
        assert any(name.startswith('legal/') for name in runtime.namelist())
        for name in runtime.namelist():
            assert not runtime.read(name).startswith(b'\x7fELF'), name
print('Single-player APK client startup assets, ARM64 libraries, server archives and notices verified')
