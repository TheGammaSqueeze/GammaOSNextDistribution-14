#!/usr/bin/env python3
# Copyright 2020 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Gets info about completed chromiumos-sdk runs.

Moreover, this script exists to get versions of published sdk tarballs in
gs://chromiumos-sdk/. The hope is that it'll help answer the question "when did
the toolchain ebuild ${x} go live?"
"""

import argparse
import json
import logging
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from typing import Dict, List


def fetch_all_sdk_manifest_paths() -> List[str]:
    """Fetches all paths of SDK manifests; newer = later in the return value."""
    results = subprocess.run(
        ["gsutil", "ls", "gs://chromiumos-sdk/cros-sdk-20??.*.Manifest"],
        check=True,
        stdout=subprocess.PIPE,
        encoding="utf-8",
    ).stdout
    # These are named so that sorted order == newest last.
    return sorted(x.strip() for x in results.splitlines())


def fetch_manifests_into(into_dir: Path, manifests: List[str]):
    # Wrap this in a `try` block because gsutil likes to print to stdout *and*
    # stderr even on success, so we silence them & only print on failure.
    try:
        subprocess.run(
            [
                "gsutil",
                "-m",
                "cp",
                "-I",
                str(into_dir),
            ],
            check=True,
            input="\n".join(manifests),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            encoding="utf-8",
        )
    except subprocess.CalledProcessError as e:
        logging.exception("gsutil failed; output:\n%s", e.stdout)


def load_manifest_versions(manifest: Path) -> Dict[str, str]:
    with manifest.open(encoding="utf-8") as f:
        raw_versions = json.load(f)

    # We get a dict of list of lists of versions and some other metadata, e.g.
    # {"foo/bar": [["1.2.3", {}]]}
    # Trim out the metadata.
    return {k: v[0][0] for k, v in raw_versions["packages"].items()}


def main():
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "-d", "--debug", action="store_true", help="Emit debugging output"
    )
    parser.add_argument(
        "-n",
        "--number",
        type=int,
        default=20,
        help="Number of recent manifests to fetch info about. 0 means "
        "unlimited.",
    )
    args = parser.parse_args()

    is_debug = args.debug
    logging.basicConfig(level=logging.DEBUG if is_debug else logging.INFO)

    logging.debug("Fetching SDK manifests")
    manifest_paths = fetch_all_sdk_manifest_paths()
    logging.debug("%d SDK manifests fetched", len(manifest_paths))

    number = args.number
    if number:
        manifest_paths = manifest_paths[-number:]

    tempdir = Path(tempfile.mkdtemp(prefix="cros-sdk-rolls"))
    try:
        logging.debug("Working in tempdir %r", tempdir)
        fetch_manifests_into(tempdir, manifest_paths)

        for path in manifest_paths:
            basename = os.path.basename(path)
            versions = load_manifest_versions(tempdir.joinpath(basename))
            print(f'{basename}: {versions["sys-devel/llvm"]}')
    finally:
        if is_debug:
            logging.debug("Keeping around tempdir %r to aid debugging", tempdir)
        else:
            shutil.rmtree(tempdir)


if __name__ == "__main__":
    sys.exit(main())
