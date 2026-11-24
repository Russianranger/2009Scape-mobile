from pathlib import Path
from zipfile import ZipFile
from contextlib import ExitStack
import json
import os
import re
import shutil
import subprocess
import sys
import time

out = Path('validation').resolve()
root = Path('host').resolve()
root.mkdir()
out.mkdir(exist_ok=True)
pkg = 'net.kdt.pojavlaunch.debug'
mode = sys.argv[1]
arm64 = sys.argv[2:] == ['arm64']
activity = 'MainActivity' if mode == 'hd' else 'JavaGUILauncherActivity'


def adb(*args, **opts):
    return subprocess.run(['adb', *args], timeout=150, check=opts.pop('check', True), **opts)


def save(path, *args, limit=None):
    proc = subprocess.run(['adb', *args], stdout=subprocess.PIPE, timeout=40)
    content = proc.stdout
    (out / path).write_bytes(content[-limit:] if limit else content)
    return content.decode(errors='replace') if not path.endswith('.png') else ''


def installed():
    return adb('shell', f'run-as {pkg} ls runtimes/Internal/lib', stdout=subprocess.PIPE).stdout.strip() and adb('shell', f'run-as {pkg} find runtimes/Internal/lib -name libjvm.so', stdout=subprocess.PIPE).stdout.strip()


with ZipFile('client.apk') as apk:
    for name in ('engine', 'world'):
        path = root / (name + '.zip')
        with path.open('wb') as f, apk.open('assets/singleplayer/' + name + '.zip') as src:
            shutil.copyfileobj(src, f)
        with ZipFile(path) as nested:
            nested.extractall(root / name)
    cfg = json.loads(apk.read('assets/config.json'))
    cfg.update(ip_address='127.0.0.1', ip_management='127.0.0.1')
cp = os.pathsep.join(str(root / 'engine' / f) for f in ('bootstrap.jar', 'sqlite.jar', 'lib/*', 'server.jar'))
with ExitStack() as resources:
    log = resources.enter_context((out / 'server.log').open('w'))
    server = subprocess.Popen(['java', '-Xms192m', '-Xmx1600m', '-XX:ActiveProcessorCount=2',
                               '-Djava.awt.headless=true', '-Dscape.session=ci-client', '-cp', cp,
                               'scape.ServerMain', 'worldprops/default.conf'], cwd=root / 'world',
                              stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
    resources.callback(server.terminate)
    save('abis.txt', 'shell', 'getprop', 'ro.product.cpu.abilist')
    save('bridge.txt', 'shell', 'getprop', 'ro.dalvik.vm.native.bridge')
    adb('install', '-r', '-g', *(['--abi', 'arm64-v8a'] if arm64 else []), 'client.apk')
    adb('shell', 'settings', 'put', 'secure', 'immersive_mode_confirmations', 'confirmed')
    adb('reverse', 'tcp:43595', 'tcp:43595')
    adb('shell', 'svc', 'wifi', 'disable')
    adb('shell', 'svc', 'data', 'disable')
    adb('logcat', '-c')
    adb('shell', 'am', 'start', '-W', '-n', pkg + '/net.kdt.pojavlaunch.TestStorageActivity')
    for _ in range(120):
        try:
            if installed() and '[scape] SERVER_READY ci-client' in (out / 'server.log').read_text():
                break
        except subprocess.CalledProcessError:
            pass
        time.sleep(2)
    else:
        save('boot-logcat.txt','logcat','-d','-v','threadtime',limit=2000000)
        raise RuntimeError('No complete Java 8 and local world found')
    for filename in ('config.json', 'singleplayer-client.json'):
        adb('shell', f"run-as {pkg} sh -c 'cat > {filename}'", input=json.dumps(cfg).encode())
    proc = subprocess.Popen(['sudo','tcpdump','-ni','lo','-l','tcp port 43595'], stdout=resources.enter_context((out/'connections.txt').open('w')), stderr=subprocess.DEVNULL)
    resources.callback(proc.terminate)
    adb('shell', 'am', 'start', '-W', '-n', f'{pkg}/net.kdt.pojavlaunch.{activity}', '--ez', 'scape.singleplayer', 'true')
    def cache_files(stage):
        save('cache-'+stage+'.txt','shell',f'find /data/user/0/{pkg} -type f | grep -Ei "main_file|runescape|jagex|\.idx|\.dat[0-9]*$" | head -120 | xargs -r ls -l',limit=30000)
    cache_files('start')
    start = time.monotonic()
    seen_login = False
    for tick in [7, 24, 50, 85, 135]:
        time.sleep(max(0, start + tick - time.monotonic()))
        save(f'screen-{tick}.png', 'exec-out', 'screencap', '-p')
        if shutil.which('tesseract'):
            text = subprocess.run(['tesseract', str(out / f'screen-{tick}.png'), 'stdout', '--psm', '11'],
                                  stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True).stdout
            print(tick, text, flush=True)
            (out / f'ocr-{tick}.txt').write_text(text)
            if re.search(r'existing user|new user|create (an? )?account|returning player|existing account|username|password', text, re.I):
                seen_login = True
                break
    cache_files('end')
    save('activities.txt','shell','dumpsys','activity','activities',limit=180000)
    save('exit-info.txt','shell','dumpsys','activity','exit-info',pkg,limit=120000)
    save('client.txt','shell',f'cat /data/media/0/Android/data/{pkg}/files/latestlog.txt || cat /sdcard/Android/data/{pkg}/files/latestlog.txt',limit=600000)
    save('client-files.txt','shell',f'find /data/media/0/Android/data/{pkg} /sdcard/Android/data/{pkg} /data/user/0/{pkg} -name "latest*"')
    save('sockets.txt','shell','ss','-ntp')
    pids=adb('shell', 'pidof', pkg+(':game' if mode=='hd' else ':launcher'),stdout=subprocess.PIPE,check=False).stdout.strip().decode()
    for pid in pids.split():
        if pid.isdecimal():
            save('backtrace-'+pid+'.txt','shell',f'timeout 10 debuggerd -b {pid}',limit=400000)
            adb('shell', f'kill -3 {pid}')
    time.sleep(3)
    save('client-after.txt','shell',f'cat /data/media/0/Android/data/{pkg}/files/latestlog.txt || cat /sdcard/Android/data/{pkg}/files/latestlog.txt',limit=700000)
    save('logcat.txt','logcat','-d','-v','threadtime',limit=3500000)
    adb('shell','getprop','ro.product.cpu.abilist')
    print('Login recognized by OCR:',seen_login,flush=True)
    if not seen_login:
        raise SystemExit('Login not confirmed by OCR. Inspect the screenshots and logs.')
