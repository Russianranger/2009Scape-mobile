from pathlib import Path
from zipfile import ZipFile
from contextlib import ExitStack
import csv
import io
import json
import os
import re
import shutil
import struct
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
    process = adb('shell', f'run-as {pkg} find runtimes/Internal/lib -name libjvm.so', stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False)
    return process.returncode == 0 and bool(process.stdout.strip())



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
    upgrade = sys.argv[2:] == ['upgrade']
    if upgrade:
        adb('install', '-r', '-g', 'preview3.apk')
        adb('shell','am','start','-W','-n',pkg+'/net.kdt.pojavlaunch.TestStorageActivity')
        for _ in range(110):
            p = adb('shell', f'run-as {pkg} ls plugins/ViewDistance/plugin.class plugins.version',stdout=subprocess.PIPE,check=False)
            if p.returncode==0 and installed(): break
            time.sleep(2)
        else: raise RuntimeError('Preview3 game/runtime/plugins did not stage')
        save('preview3-plugins.txt','shell','run-as',pkg,'cat','plugins.version')
        adb('shell',f"run-as {pkg} sh -c 'mkdir -p files && echo preview3-test-data > files/qa-upgrade-marker'")
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
    if upgrade:
        if save('upgrade-marker.txt','exec-out','run-as',pkg,'cat','files/qa-upgrade-marker').strip() != 'preview3-test-data':
            raise RuntimeError('Existing app data missing after Android APK update')
    save('current-plugins.txt','shell','run-as',pkg,'cat','plugins.version')
    for filename in ('config.json', 'singleplayer-client.json'):
        adb('shell', f"run-as {pkg} sh -c 'cat > {filename}'", input=json.dumps(cfg).encode())
    proc = subprocess.Popen(['sudo','tcpdump','-ni','lo','-l','tcp port 43595'], stdout=resources.enter_context((out/'connections.txt').open('w')), stderr=subprocess.DEVNULL)
    resources.callback(subprocess.run, ['sudo', 'kill', '-TERM', str(proc.pid)], check=False)
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
            text = '\n'.join(subprocess.run(['tesseract',str(out/f'screen-{tick}.png'),'stdout','--psm',p],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True).stdout for p in ('11','3','12'))
            print(tick, text, flush=True)
            (out / f'ocr-{tick}.txt').write_text(text)
            if re.search(r'existing user|new user|create (an? )?account|returning player|existing account|username|password|log\s*in', text, re.I):
                seen_login = True
                screen = out / f'screen-{tick}.png'
                width, height = struct.unpack('>II',screen.read_bytes()[16:24])
                coords=[]
                for psm in ('3','12','6','11'):
                    rows = subprocess.run(['tesseract',str(screen),'stdout','--psm',psm,'tsv'],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True).stdout
                    for word in csv.DictReader(io.StringIO(rows),delimiter='\t'):
                        if re.sub('[^a-z]','',word['text'].lower()) in ('log','login','existing'):
                            x, y = (int(word['left'])+int(word['width'])//2, int(word['top'])+int(word['height'])//2)
                            if x>width*.19 and x<width*.85 and y<height*.65:
                                coords.append((x,y))
                                break
                    if coords: break
                coords.extend([(width//2,int(height*.11)),(width//2,int(height*.25))])
                for attempt,(x,y) in enumerate(coords):
                    if mode=='hd':
                        adb('shell','input','stylus','swipe',str(x),str(y),str(x),str(y),'220')
                    else:
                        adb('shell','input','tap',str(x),str(y))
                    if mode=='hd' and attempt == 1:
                        # The primary mouse button in the stock Pojav layout clicks the last canvas position.
                        adb('shell','input','tap',str(x),str(y))
                        time.sleep(.25)
                        px,py=str(int(width*.124)),str(int(height*.11))
                        adb('shell','input','swipe',px,py,px,py,'180')
                    time.sleep(2)
                    after_name=f'login-tapped-{attempt}.png'
                    save(after_name,'exec-out','screencap','-p')
                    after='\n'.join(subprocess.run(['tesseract',str(out/after_name),'stdout','--psm',p],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True).stdout for p in ('11','6','3'))
                    (out/f'after-tap-{attempt}.txt').write_text(after)
                    if re.search(r'(user\s*name|pass\s*word|credentials|enter your)',after,re.I):
                        shutil.copyfile(out/after_name, out/'login-tapped.png')
                        break
                break
    cache_files('end')
    save('draw-probe.txt','exec-out','run-as',pkg,'cat','files/draw-probe.log',limit=800000)
    save('hotspot.txt','shell', f'for f in /data/user/0/{pkg}/files/client-diagnostics/*hotspot* /data/user/0/{pkg}/hs_err_pid*.log; do test -s "$f" && cat "$f"; done',limit=1800000)
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
    if not (out/'login-tapped.png').is_file():
        raise SystemExit('World screen reached, but post-tap credential fields need visual confirmation.')
