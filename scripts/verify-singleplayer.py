#!/usr/bin/env python3
"""Verify distributable server contents and separation from the unmodified client."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as apk:
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
    source = Path(__file__).resolve().parents[1] / 'app_pojavlauncher/src/main/assets/rt4.jar'
    assert apk.read('assets/rt4.jar') == source.read_bytes(), 'Client binary changed'
print('Single-player APK assets, ARM64 libraries, checksums, notices and original client verified')
