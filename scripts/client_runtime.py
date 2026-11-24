#!/usr/bin/env python3
"""Stage the unchanged Android OpenJDK 8 binpack from upstream 2009Scape Mobile 2.4."""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
from tempfile import TemporaryDirectory
import zipfile


APK = "2009Scape-HD-2.4-debug.apk"
URL = "https://github.com/2009scape/2009Scape-mobile/releases/download/hd-2.4/" + APK
APK_SHA256 = "ccc622be8d72f0506643b4cc955768123f1436f601ceab4611048807262936cd"
ASSET_PATH = "assets/components/jre/"
SHA256 = {
    "version": "680d3e0191d8ceb894ebc4316cc364b26a80692f592568c4e412c3e7affe9039",
    "universal.tar.xz": "eafb9737ec5858367f2f6fd1ee3550e933ec52423ef65b467cf50f39f9b549ca",
    "bin-arm64.tar.xz": "ff28d361741005421bacb7596132ca9ae0a180b53925f4c7100607d865ce5e1d",
    "bin-arm.tar.xz": "57ef5491dc7cd76bfe17a42ab77144e455effe9b67518002123c7171c2c8cb17",
    "bin-x86.tar.xz": "c061de344f47f8f283132a6dedad5f68af47c54ef42b7f53445591355a83eeda",
    "bin-x86_64.tar.xz": "4a4942697efff1ba17dfcb28be6c9d32910744bb6e05ae8264722af93a9ec630",
}


def digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as src:
        for block in iter(lambda: src.read(1024 * 1024), b""):
            checksum.update(block)
    return checksum.hexdigest()


def prepare(output, cache_dir):
    cache_dir.mkdir(parents=True, exist_ok=True)
    cached = cache_dir / APK
    if not cached.is_file() or digest(cached) != APK_SHA256:
        with TemporaryDirectory(dir=cache_dir) as temp:
            download = Path(temp) / APK
            subprocess.run(["curl", "--fail", "--location", "--retry", "3", "--max-time", "600",
                            "--silent", "--show-error", "--output", str(download), URL], check=True)
            if digest(download) != APK_SHA256:
                raise ValueError("Upstream 2009Scape 2.4 APK checksum mismatch")
            download.replace(cached)

    output.mkdir(parents=True, exist_ok=True)
    # Stage outside the merged assets. A failed extraction must never leave a partial binpack.
    with TemporaryDirectory(dir=output.parent) as temp, zipfile.ZipFile(cached) as source:
        for name, expected in SHA256.items():
            dest = Path(temp) / name
            with source.open(ASSET_PATH + name) as src, dest.open("wb") as out:
                shutil.copyfileobj(src, out)
            if digest(dest) != expected:
                raise ValueError("Client runtime checksum mismatch: " + name)
        target = output / "components" / "jre"
        target.mkdir(parents=True, exist_ok=True)
        for name in SHA256:
            (Path(temp) / name).replace(target / name)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--cache-dir", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.output, args.cache_dir)
