#!/usr/bin/env python3
# Copyright 2022 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Copies rust-bootstrap artifacts from an SDK build to localmirror.

We use localmirror to host these artifacts, but they've changed a bit over
time, so simply `gsutil cp $FROM $TO` doesn't work. This script allows the
convenience of the old `cp` command.
"""

import argparse
import logging
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from typing import List


_LOCALMIRROR_ROOT = "gs://chromeos-localmirror/distfiles/"


def _is_in_chroot() -> bool:
    return Path("/etc/cros_chroot_version").exists()


def _ensure_lbzip2_is_installed():
    if shutil.which("lbzip2"):
        return

    logging.info("Auto-installing lbzip2...")
    subprocess.run(["sudo", "emerge", "-g", "lbzip2"], check=True)


def determine_target_path(sdk_path: str) -> str:
    """Determine where `sdk_path` should sit in localmirror."""
    gs_prefix = "gs://"
    if not sdk_path.startswith(gs_prefix):
        raise ValueError(f"Invalid GS path: {sdk_path!r}")

    file_name = Path(sdk_path[len(gs_prefix) :]).name
    return _LOCALMIRROR_ROOT + file_name


def _download(remote_path: str, local_file: Path):
    """Downloads the given gs:// path to the given local file."""
    logging.info("Downloading %s -> %s", remote_path, local_file)
    subprocess.run(
        ["gsutil", "cp", remote_path, str(local_file)],
        check=True,
        stdin=subprocess.DEVNULL,
    )


def _debinpkgify(binpkg_file: Path) -> Path:
    """Converts a binpkg into the files it installs.

    Note that this function makes temporary files in the same directory as
    `binpkg_file`. It makes no attempt to clean them up.
    """
    logging.info("Converting %s from a binpkg...", binpkg_file)

    # The SDK builder produces binary packages:
    # https://wiki.gentoo.org/wiki/Binary_package_guide
    #
    # Which means that `binpkg_file` is in the XPAK format. We want to split
    # that out, and recompress it from zstd (which is the compression format
    # that CrOS uses) to bzip2 (which is what we've historically used, and
    # which is what our ebuild expects).
    tmpdir = binpkg_file.parent

    def _mkstemp(suffix=None) -> Path:
        fd, file_path = tempfile.mkstemp(dir=tmpdir, suffix=suffix)
        os.close(fd)
        return Path(file_path)

    # First, split the actual artifacts that land in the chroot out to
    # `temp_file`.
    artifacts_file = _mkstemp()
    logging.info(
        "Extracting artifacts from %s into %s...", binpkg_file, artifacts_file
    )
    with artifacts_file.open("wb") as f:
        subprocess.run(
            [
                "qtbz2",
                "-s",
                "-t",
                "-O",
                str(binpkg_file),
            ],
            check=True,
            stdout=f,
        )

    decompressed_artifacts_file = _mkstemp()
    decompressed_artifacts_file.unlink()
    logging.info(
        "Decompressing artifacts from %s to %s...",
        artifacts_file,
        decompressed_artifacts_file,
    )
    subprocess.run(
        [
            "zstd",
            "-d",
            str(artifacts_file),
            "-o",
            str(decompressed_artifacts_file),
        ],
        check=True,
    )

    # Finally, recompress it as a tbz2.
    tbz2_file = _mkstemp(".tbz2")
    logging.info(
        "Recompressing artifacts from %s to %s (this may take a while)...",
        decompressed_artifacts_file,
        tbz2_file,
    )
    with tbz2_file.open("wb") as f:
        subprocess.run(
            [
                "lbzip2",
                "-9",
                "-c",
                str(decompressed_artifacts_file),
            ],
            check=True,
            stdout=f,
        )
    return tbz2_file


def _upload(local_file: Path, remote_path: str, force: bool):
    """Uploads the local file to the given gs:// path."""
    logging.info("Uploading %s -> %s", local_file, remote_path)
    cmd_base = ["gsutil", "cp", "-a", "public-read"]
    if not force:
        cmd_base.append("-n")
    subprocess.run(
        cmd_base + [str(local_file), remote_path],
        check=True,
        stdin=subprocess.DEVNULL,
    )


def main(argv: List[str]):
    logging.basicConfig(
        format=">> %(asctime)s: %(levelname)s: %(filename)s:%(lineno)d: "
        "%(message)s",
        level=logging.INFO,
    )

    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    parser.add_argument(
        "sdk_artifact",
        help="Path to the SDK rust-bootstrap artifact to copy. e.g., "
        "gs://chromeos-prebuilt/host/amd64/amd64-host/"
        "chroot-2022.07.12.134334/packages/dev-lang/"
        "rust-bootstrap-1.59.0.tbz2.",
    )
    parser.add_argument(
        "-n",
        "--dry-run",
        action="store_true",
        help="Do everything except actually uploading the artifact.",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Upload the artifact even if one exists in localmirror already.",
    )
    opts = parser.parse_args(argv)

    if not _is_in_chroot():
        parser.error("Run me from within the chroot.")
    _ensure_lbzip2_is_installed()

    target_path = determine_target_path(opts.sdk_artifact)
    with tempfile.TemporaryDirectory() as tempdir:
        download_path = Path(tempdir) / "sdk_artifact"
        _download(opts.sdk_artifact, download_path)
        file_to_upload = _debinpkgify(download_path)
        if opts.dry_run:
            logging.info(
                "--dry-run specified; skipping upload of %s to %s",
                file_to_upload,
                target_path,
            )
        else:
            _upload(file_to_upload, target_path, opts.force)


if __name__ == "__main__":
    main(sys.argv[1:])
