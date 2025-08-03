#!/usr/bin/env python3
# Copyright 2022 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Help creating a Rust ebuild with CRATES.

This script is meant to help someone creating a Rust ebuild of the type
currently used by sys-apps/ripgrep and sys-apps/rust-analyzer.

In these ebuilds, the CRATES variable is used to list all dependencies, rather
than creating an ebuild for each dependency. This style of ebuild can be used
for a crate which is only intended for use in the chromiumos SDK, and which has
many dependencies which otherwise won't be used.

To create such an ebuild, there are essentially two tasks that must be done:

1. Determine all transitive dependent crates and version and list them in the
CRATES variable. Ignore crates that are already included in the main crate's
repository.

2. Find which dependent crates are not already on a chromeos mirror, retrieve
them from crates.io, and upload them to `gs://chromeos-localmirror/distfiles`.

This script parses the crate's lockfile to list transitive dependent crates,
and either lists crates to be uploaded or actually uploads them.

Of course these can be done manually instead. If you choose to do these steps
manually, I recommend *not* using the `cargo download` tool, and instead obtain
dependent crates at
`https://crates.io/api/v1/crates/{crate_name}/{crate_version}/download`.

Example usage:

    # Here we instruct the script to ignore crateA and crateB, presumably
    # because they are already included in the same repository as some-crate.
    # This will not actually upload any crates to `gs`.
    python3 crate_ebuild_help.py --lockfile some-crate/Cargo.lock \
            --ignore crateA --ignore crateB --dry-run

    # Similar to the above, but here we'll actually carry out the uploads.
    python3 crate_ebuild_help.py --lockfile some-crate/Cargo.lock \
            --ignore crateA --ignore crateB

See the ebuild files for ripgrep or rust-analyzer for other details.
"""

import argparse
import concurrent.futures
from pathlib import Path
import subprocess
import tempfile
from typing import List, Tuple
import urllib.request

# Python 3.11 has `tomllib`, so maybe eventually we can switch to that.
import toml


def run(args: List[str]) -> bool:
    result = subprocess.run(
        args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False
    )
    return result.returncode == 0


def run_check(args: List[str]):
    subprocess.run(
        args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True
    )


def gs_address_exists(address: str) -> bool:
    # returns False if the file isn't there
    return run(["gsutil.py", "ls", address])


def crate_already_uploaded(crate_name: str, crate_version: str) -> bool:
    filename = f"{crate_name}-{crate_version}.crate"
    return gs_address_exists(
        f"gs://chromeos-localmirror/distfiles/{filename}"
    ) or gs_address_exists(f"gs://chromeos-mirror/gentoo/distfiles/{filename}")


def download_crate(crate_name: str, crate_version: str, localpath: Path):
    urllib.request.urlretrieve(
        f"https://crates.io/api/v1/crates/{crate_name}/{crate_version}/download",
        localpath,
    )


def upload_crate(crate_name: str, crate_version: str, localpath: Path):
    run_check(
        [
            "gsutil.py",
            "cp",
            "-n",
            "-a",
            "public-read",
            str(localpath),
            f"gs://chromeos-localmirror/distfiles/{crate_name}-{crate_version}.crate",
        ]
    )


def main():
    parser = argparse.ArgumentParser(
        description="Help prepare a Rust crate for an ebuild."
    )
    parser.add_argument(
        "--lockfile",
        type=str,
        required=True,
        help="Path to the lockfile of the crate in question.",
    )
    parser.add_argument(
        "--ignore",
        type=str,
        action="append",
        required=False,
        default=[],
        help="Ignore the crate by this name (may be used multiple times).",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Don't actually download/upload crates, just print their names.",
    )
    ns = parser.parse_args()

    to_ignore = set(ns.ignore)

    toml_contents = toml.load(ns.lockfile)
    packages = toml_contents["package"]

    crates = [
        (pkg["name"], pkg["version"])
        for pkg in packages
        if pkg["name"] not in to_ignore
    ]
    crates.sort()

    print("Dependent crates:")
    for name, version in crates:
        print(f"{name}-{version}")
    print()

    if ns.dry_run:
        print("Crates that would be uploaded (skipping ones already uploaded):")
    else:
        print("Uploading crates (skipping ones already uploaded):")

    def maybe_upload(crate: Tuple[str, str]) -> str:
        name, version = crate
        if crate_already_uploaded(name, version):
            return ""
        if not ns.dry_run:
            with tempfile.TemporaryDirectory() as temp_dir:
                path = Path(temp_dir.name, f"{name}-{version}.crate")
                download_crate(name, version, path)
                upload_crate(name, version, path)
        return f"{name}-{version}"

    # Simple benchmarking on my machine with rust-analyzer's Cargo.lock, using
    # the --dry-run option, gives a wall time of 277 seconds with max_workers=1
    # and 70 seconds with max_workers=4.
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        crates_len = len(crates)
        for i, s in enumerate(executor.map(maybe_upload, crates)):
            if s:
                j = i + 1
                print(f"[{j}/{crates_len}] {s}")
    print()


if __name__ == "__main__":
    main()
