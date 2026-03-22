#!/usr/bin/env python3
"""
Generate an OTA manifest.json for GammaOS OTA packages.

Usage:
    python3 gen_ota_manifest.py \
        --version 1.2.3 \
        --device ayaneo_pocket_air \
        --dir ota_staging/ \
        --output ota_staging/manifest.json

The script scans the directory for .img.xz files, computes SHA-256 checksums
for both compressed and uncompressed data, and generates the manifest.
"""

import argparse
import hashlib
import json
import lzma
import os
import struct
import subprocess
import sys
import time


def sha256_file(path):
    """Compute SHA-256 of a file."""
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        while True:
            chunk = f.read(1024 * 1024)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def sha256_xz_uncompressed(path):
    """Compute SHA-256 of the uncompressed content of an .xz file."""
    h = hashlib.sha256()
    with lzma.open(path, 'rb') as f:
        while True:
            chunk = f.read(1024 * 1024)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def uncompressed_size(path):
    """Get the uncompressed size of an .xz file."""
    size = 0
    with lzma.open(path, 'rb') as f:
        while True:
            chunk = f.read(1024 * 1024)
            if not chunk:
                break
            size += len(chunk)
    return size


# Known partition types
PHYSICAL_PARTITIONS = {
    'boot', 'vendor_boot', 'dtbo',
    'vbmeta', 'vbmeta_system', 'vbmeta_vendor',
    'init_boot',
}

LOGICAL_PARTITIONS = {
    'system', 'system_ext', 'vendor', 'vendor_dlkm',
    'product', 'odm', 'system_dlkm',
}


def detect_partition_type(name):
    """Detect if a partition is logical or physical."""
    if name in PHYSICAL_PARTITIONS:
        return 'physical'
    if name in LOGICAL_PARTITIONS:
        return 'logical'
    # Default to logical for unknown partitions
    print(f"Warning: Unknown partition '{name}', defaulting to logical", file=sys.stderr)
    return 'logical'


def main():
    parser = argparse.ArgumentParser(description='Generate GammaOS OTA manifest')
    parser.add_argument('--version', required=True, help='Version string (e.g. 1.2.3)')
    parser.add_argument('--version-code', type=int, default=0, help='Version code integer')
    parser.add_argument('--device', action='append', default=[], help='Target device (can repeat)')
    parser.add_argument('--dir', required=True, help='Directory containing .img.xz files')
    parser.add_argument('--output', required=True, help='Output manifest.json path')
    parser.add_argument('--min-battery', type=int, default=50, help='Minimum battery percentage')
    args = parser.parse_args()

    if not args.device:
        args.device = []  # empty = compatible with all devices

    partitions = []
    img_dir = args.dir

    # Scan for .img.xz files
    for filename in sorted(os.listdir(img_dir)):
        if not filename.endswith('.img.xz'):
            continue

        filepath = os.path.join(img_dir, filename)
        part_name = filename.replace('.img.xz', '')
        part_type = detect_partition_type(part_name)

        print(f"Processing {filename}...")
        print(f"  Type: {part_type}")

        # Compute checksums
        print(f"  Computing compressed SHA-256...")
        sha_compressed = sha256_file(filepath)
        print(f"  SHA-256 (compressed): {sha_compressed[:16]}...")

        print(f"  Computing uncompressed SHA-256 + size...")
        sha_uncompressed = sha256_xz_uncompressed(filepath)
        size = uncompressed_size(filepath)
        print(f"  SHA-256 (uncompressed): {sha_uncompressed[:16]}...")
        print(f"  Uncompressed size: {size} bytes ({size / 1024 / 1024:.1f} MB)")

        entry = {
            'name': part_name,
            'type': part_type,
            'file': filename,
            'sha256': sha_compressed,
            'sha256_uncompressed': sha_uncompressed,
        }

        # Always include size (needed for resize on logical, verification on physical)
        entry['size'] = size

        partitions.append(entry)

    if not partitions:
        print(f"Error: No .img.xz files found in {img_dir}", file=sys.stderr)
        sys.exit(1)

    manifest = {
        'version': args.version,
        'version_code': args.version_code or int(args.version.replace('.', '') + '00'),
        'datetime': int(time.time()),
        'device': args.device,
        'min_battery': args.min_battery,
        'partitions': partitions,
    }

    with open(args.output, 'w') as f:
        json.dump(manifest, f, indent=2)

    print(f"\nManifest written to {args.output}")
    print(f"  Version: {args.version}")
    print(f"  Devices: {args.device or '(all)'}")
    print(f"  Partitions: {len(partitions)}")
    for p in partitions:
        size_str = f" ({p.get('size', 0) / 1024 / 1024:.0f} MB)" if 'size' in p else ''
        print(f"    {p['name']} ({p['type']}){size_str}")


if __name__ == '__main__':
    main()
