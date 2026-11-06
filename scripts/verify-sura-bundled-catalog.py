#!/usr/bin/env python3
"""Verify install-time full catalog assets before building a signed APK.

Usage:
  python3 scripts/verify-sura-bundled-catalog.py app/src/main/assets/catalog --required
No user-provided source code is executed.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path

FULL_RECORDS = 4227218
FULL_BYTES = 1830764544
FULL_SHA256 = "567fbba0e54a863879c790a848866a7e16d48ad272c66e286172f87b67fd0e9f"

def verify(directory: Path, required: bool) -> bool:
    manifest_path = directory / "manifest.json"
    if not manifest_path.is_file():
        if required:
            raise ValueError("Full catalog is absent; refusing to publish a falsely complete APK.")
        print("No bundled catalog present; normal lightweight build is allowed.")
        return False

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert manifest["schema"] == "sura-offline-catalog-bundle-v1"
    assert manifest["record_count"] == FULL_RECORDS
    assert manifest["database_bytes"] == FULL_BYTES
    assert manifest["database_sha256"] == FULL_SHA256
    parts = manifest["parts"]
    assert 1 <= len(parts) <= 32
    digest = hashlib.sha256()
    total = 0

    for index, part in enumerate(parts):
        name = f"full-{index:02d}.gz"
        assert part["file"] == name
        path = directory / name
        assert path.is_file(), f"Missing catalog asset: {path}"
        compressed = hashlib.sha256()
        with path.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                compressed.update(chunk)
        assert compressed.hexdigest() == part["compressed_sha256"], f"Corrupted {name}"
        assert path.stat().st_size == part["compressed_bytes"]
        length = 0
        with gzip.open(path, "rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
                length += len(chunk)
        assert length == part["bytes"], f"Wrong expanded length {name}"
        total += length
        print(f"OK {name}: {length} bytes")

    assert total == FULL_BYTES
    assert digest.hexdigest() == FULL_SHA256
    print(f"COMPLETE: {FULL_RECORDS:,} identifiers; {total:,} bytes; SHA256 verified")
    return True

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("assets_dir", type=Path)
    parser.add_argument("--required", action="store_true")
    options = parser.parse_args()
    verify(options.assets_dir, options.required)
