#!/usr/bin/env python3
"""Check the distributable APK, and write a checksum next to it."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import zipfile


def output(*command):
    return subprocess.check_output(command, text=True)


def certificate(apksigner, apk):
    result = output(apksigner, "verify", "--verbose", "--print-certs", str(apk))
    match = re.search(r"Signer #1 certificate SHA-256 digest: (\S+)", result)
    if not match:
        raise RuntimeError("APK signing certificate could not be verified")
    return match.group(1)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--build-tools", required=True, type=Path)
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--previous-apk", type=Path)
    args = parser.parse_args()

    badging = output(str(args.build_tools / "aapt"), "dump", "badging", str(args.apk))
    package_line = next(line for line in badging.splitlines() if line.startswith("package:"))
    attributes = dict(re.findall(r"(\w+)='([^']*)'", package_line))
    expected = {"name": "net.kdt.pojavlaunch.debug", "versionName": args.version,
                "versionCode": str(args.version_code)}
    for key, value in expected.items():
        if attributes.get(key) != value:
            raise RuntimeError(f"APK {key}: expected {value}, got {attributes.get(key)}")

    apksigner = str(args.build_tools / "apksigner")
    digest = certificate(apksigner, args.apk)
    if args.previous_apk and certificate(apksigner, args.previous_apk) != digest:
        raise RuntimeError("Signing certificate differs from the previous release")

    required = {"assets/rt4.jar", "assets/components/lwjgl3/lwjgl-glfw-classes.jar",
                "assets/plugins/TouchDrag.zip", "assets/plugins/KeyboardRoom.zip",
                "assets/plugins/ViewDistance.zip", "assets/plugins/AudioFix.zip",
                "lib/arm64-v8a/libpojavexec.so", "lib/arm64-v8a/libpojavexec_awt.so"}
    with zipfile.ZipFile(args.apk) as archive:
        missing = required - set(archive.namelist())
        if missing:
            raise RuntimeError(f"APK is missing required assets: {sorted(missing)}")
        broken = archive.testzip()
        if broken:
            raise RuntimeError(f"APK contains a corrupt entry: {broken}")

    sha256 = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    checksum = args.apk.with_name(args.apk.name + ".sha256")
    checksum.write_text(f"{sha256}  {args.apk.name}\n")
    print(f"Verified {args.apk.name}: {args.version} ({args.version_code})")
    print(f"Signing certificate SHA-256: {digest}")
    print(f"APK SHA-256: {sha256}")


if __name__ == "__main__":
    main()
