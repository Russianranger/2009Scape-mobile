#!/usr/bin/env python3
"""Reproducibly package the pinned headless server and Android ARM64 Java runtime."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'app_pojavlauncher/build/generated/singleplayer'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def safe(name):
    p = PurePosixPath(name)
    if p.is_absolute() or '..' in p.parts or '\\' in name:
        raise ValueError('Unsafe archive path: ' + name)
    return str(p)


def put(z, name, data):
    info = zipfile.ZipInfo(safe(name), (2026, 9, 14, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    z.writestr(info, data)


def javac(*args):
    command = [shutil.which('javac')] if shutil.which('javac') else ['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main']
    subprocess.run(command + list(map(str, args)), check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--cache', type=Path, default=ROOT / 'app_pojavlauncher/build/server-downloads')
    parser.add_argument('--host-only', action='store_true', help='Prepare server artifacts for desktop integration tests')
    args = parser.parse_args()
    pins = json.loads((ROOT / 'server-runtime/dependencies.json').read_text())
    args.cache.mkdir(parents=True, exist_ok=True)
    for name, pin in pins.items():
        dest = args.cache / name
        if not dest.exists() or sha(dest.read_bytes()) != pin['sha256']:
            partial = dest.with_suffix('.partial')
            subprocess.run(['curl', '-fsSL', '--retry', '3', '--max-time', '600', pin['url'], '-o', str(partial)], check=True)
            if sha(partial.read_bytes()) != pin['sha256']:
                raise ValueError('Dependency checksum mismatch: ' + name)
            partial.replace(dest)
    if OUTPUT.exists():
        shutil.rmtree(OUTPUT)
    assets = OUTPUT / 'assets/singleplayer'
    native = OUTPUT / 'jniLibs/arm64-v8a'
    work = OUTPUT / 'work'
    for path in (assets, native, work):
        path.mkdir(parents=True)

    links = {}
    with tarfile.open(args.cache / 'runtime.tar.xz') as src, zipfile.ZipFile(assets / 'runtime.zip', 'w') as out:
        for member in src:
            path = safe(member.name)
            if member.isdir() or path == '.' or path.startswith('bin/'):
                continue
            if not (member.isfile() or member.issym() or member.islnk()):
                raise ValueError('Unsupported runtime entry ' + path)
            content = src.extractfile(member).read()
            helpers = {'lib/jspawnhelper': 'libscape_rt_jspawnhelper.so', 'lib/jexec': 'libscape_rt_jexec.so'}
            if path.endswith('.so') or path in helpers:
                if content[:6] != b'\x7fELF\x02\x01' or int.from_bytes(content[18:20], 'little') != 183:
                    raise ValueError('Expected Android ARM64 ELF: ' + path)
                original = PurePosixPath(path).name
                name = helpers.get(path, 'libscape_rt_' + original[3:])
                target = native / name
                if target.exists() and target.read_bytes() != content:
                    raise ValueError('Duplicate native name: ' + name)
                target.write_bytes(content)
                links[path] = name
            else:
                if content.startswith(b'\x7fELF'):
                    raise ValueError('Executable in writable runtime data: ' + path)
                put(out, path, content)
        put(out, 'native-links.properties', '\n'.join(f'{p}={n}' for p, n in sorted(links.items())).encode())
    if 'lib/server/libjvm.so' not in links:
        raise ValueError('Runtime lacks HotSpot')

    with zipfile.ZipFile(args.cache / 'sqlite-android.jar') as src:
        (native / 'libscape_sqlitejdbc.so').write_bytes(src.read('org/sqlite/native/Linux-Android/aarch64/libsqlitejdbc.so'))
    with zipfile.ZipFile(args.cache / 'server.zip') as src:
        root = next(n[:-len('game/server.jar')] for n in src.namelist() if n.endswith('/game/server.jar'))
        original_server = src.read(root + 'game/server.jar')
        if sha(original_server) != 'db550017851c6b0c610f451c8cbcd7777b15e93033e3892f7c477a958d2428eb':
            raise ValueError('Unexpected server JAR')
        (work / 'upstream.jar').write_bytes(original_server)
        with zipfile.ZipFile(io.BytesIO(original_server)) as jar:
            nio = jar.read('core/net/NioReactor.class')
            if sha(nio) != '6f669107a99ce3c52988705af91ab61f0cd5e52c429dd09922c8eeffe2b3d36d':
                raise ValueError('Unexpected server listener')
            (work / 'NioReactor.class').write_bytes(nio)
        javac('--release', '11', '-cp', args.cache / 'asm.jar', '-d', work, ROOT / 'server-runtime/LocalServerPatcher.java')
        subprocess.run(['java', '-cp', str(work) + os.pathsep + str(args.cache / 'asm.jar'), 'LocalServerPatcher',
                        str(work / 'NioReactor.class'), str(work / 'NioReactor.local.class')], check=True)
        with zipfile.ZipFile(io.BytesIO(original_server)) as jar, zipfile.ZipFile(work / 'server.jar', 'w') as out:
            for item in jar.infolist():
                if item.is_dir() or item.filename.startswith('org/sqlite/'):
                    continue
                content = (work / 'NioReactor.local.class').read_bytes() if item.filename == 'core/net/NioReactor.class' else jar.read(item)
                put(out, item.filename, content)
        classes = work / 'classes'
        classes.mkdir()
        javac('--release', '11', '-cp', work / 'server.jar', '-d', classes, ROOT / 'server-runtime/src/scape/ServerMain.java')
        with zipfile.ZipFile(work / 'bootstrap.jar', 'w') as out:
            for p in sorted(classes.rglob('*.class')):
                put(out, str(p.relative_to(classes)), p.read_bytes())
        with zipfile.ZipFile(assets / 'engine.zip', 'w') as out:
            for name in ['server.jar', 'bootstrap.jar']:
                put(out, name, (work / name).read_bytes())
            # Keep upstream JDBC artifact byte-identical; force APK native path at launch.
            put(out, 'sqlite.jar', (args.cache / 'sqlite.jar').read_bytes())
            for name in sorted(pins):
                if name.startswith('nashorn'):
                    put(out, 'lib/' + name, (args.cache / name).read_bytes())
            put(out, 'UPSTREAM-LICENSE', src.read(root + 'LICENSE'))
            config = src.read(root + 'game/worldprops/default.conf').decode().replace('log_level = "silent"', 'log_level = "detailed"')
            config = config.replace('[server]', '[server]\nwebsocket_enabled = false')
            put(out, 'default.conf', config.encode())
        with zipfile.ZipFile(assets / 'world.zip', 'w') as out:
            for name in sorted(src.namelist()):
                if name.endswith('/') or not name.startswith(root + 'game/'):
                    continue
                path = name[len(root + 'game/'):]
                if not (path.startswith('data/') or path.startswith('worldprops/')) or path.endswith('.gitignore'):
                    continue
                content = src.read(name)
                if path == 'worldprops/default.conf':
                    content = content.decode().replace('log_level = "silent"', 'log_level = "detailed"')
                    content = content.replace('[server]', '[server]\nwebsocket_enabled = false')
                    content = content.encode()
                put(out, path, content)

    if not args.host_only:
        ndk = Path(os.environ.get('ANDROID_NDK_HOME', os.environ.get('ANDROID_NDK_ROOT', '')))
        compiler = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang'
        subprocess.run([str(compiler), '-O2', '-Wall', '-Wextra', '-Werror', '-fPIE', '-pie',
                        '-Wl,-z,max-page-size=16384', '-Wl,--export-dynamic-symbol=dl_iterate_phdr',
                        '-Wl,--export-dynamic-symbol=dladdr',
                        *map(str, [ROOT / 'server-runtime/native' / n for n in ['server_runner.c', 'jvm_layout.c', 'world_lock.c']]),
                        '-pthread', '-ldl', '-o', str(native / 'libscape_server.so')], check=True)
        readelf = compiler.parent / 'llvm-readelf'
        # Every dependency must exist either in the runtime image or Android's public system libraries.
        provided = {PurePosixPath(p).name for p in links} | {'libc.so','libm.so','libdl.so','liblog.so','libz.so','libandroid.so'}
        for p in native.glob('*.so'):
            dynamic = subprocess.check_output([str(readelf), '-d', str(p)], text=True)
            for dep in re.findall(r'\(NEEDED\).*?\[(.*?)\]', dynamic):
                if dep not in provided:
                    raise ValueError(f'Missing dependency {dep} in {p.name}')
    manifest = {'format': 1, 'worldVersion': '2009scape-f00fcb7-1', 'javaVersion': '17.0.20',
                'sourceCommit': 'f00fcb7e8f8916ed016cc8eb7a9f0bb19f490b60',
                'archives': {p.name: sha(p.read_bytes()) for p in sorted(assets.glob('*.zip'))},
                'native': {p.name: sha(p.read_bytes()) for p in sorted(native.glob('*.so'))}, 'dependencies': pins}
    manifest['id'] = sha(json.dumps(manifest, sort_keys=True).encode())[:16]
    (assets / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print('Prepared pinned headless server, SQLite and Android Java runtime:', manifest['id'])


if __name__ == '__main__':
    main()
