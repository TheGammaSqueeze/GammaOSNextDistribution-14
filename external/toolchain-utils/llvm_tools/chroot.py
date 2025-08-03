#!/usr/bin/env python3
# Copyright 2020 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Chroot helper functions."""

import os
from pathlib import Path
import subprocess
from typing import Iterable, List, Union


def InChroot() -> bool:
    """Returns True if currently in the chroot."""
    return "CROS_WORKON_SRCROOT" in os.environ


def VerifyOutsideChroot() -> None:
    """Checks whether the script invoked was executed in the chroot.

    Raises:
        AssertionError: The script was run inside the chroot.
    """

    assert not InChroot(), "Script should be run outside the chroot."


def VerifyChromeOSRoot(chromeos_root: Union[Path, str]) -> None:
    """Checks whether the path actually points to ChromiumOS checkout root.

    Raises:
        AssertionError: The path is not ChromiumOS checkout root.
    """

    subdir = "src/third_party/chromiumos-overlay"
    path = Path(chromeos_root).expanduser() / subdir
    msg = f"Wrong ChromeOS path. No {subdir} directory in {chromeos_root} ."
    assert path.is_dir(), msg


def GetChrootEbuildPaths(
    chromeos_root: Union[Path, str], packages: Iterable[str]
) -> List[str]:
    """Gets the chroot path(s) of the package(s).

    Args:
        chromeos_root: The absolute path to the chroot to
        use for executing chroot commands.
        packages: A list of a package/packages to
        be used to find their chroot path.

    Returns:
        A list of chroot paths of the packages' ebuild files.

    Raises:
        ValueError: Failed to get the chroot path of a package.
    """

    chroot_paths = []

    # Find the chroot path for each package's ebuild.
    for package in packages:
        chroot_path = subprocess.check_output(
            ["cros_sdk", "--", "equery", "w", package],
            cwd=chromeos_root,
            encoding="utf-8",
        )
        chroot_paths.append(chroot_path.strip())

    return chroot_paths


def ConvertChrootPathsToAbsolutePaths(
    chromeos_root: str,
    chroot_paths: List[str],
) -> List[str]:
    """Converts the chroot path(s) to absolute symlink path(s).

    Args:
        chromeos_root: The absolute path to the chroot.
        chroot_paths: A list of chroot paths to convert to absolute paths.

    Returns:
        A list of absolute path(s).

    Raises:
        ValueError: Invalid prefix for the chroot path or
        invalid chroot paths were provided.
    """

    abs_paths = []
    chroot_prefix = "/mnt/host/source/"
    # Iterate through the chroot paths.
    # For each chroot file path, remove '/mnt/host/source/' prefix
    # and combine the chroot path with the result and add it to the list.
    for chroot_path in chroot_paths:
        if not chroot_path.startswith(chroot_prefix):
            raise ValueError(
                "Invalid prefix for the chroot path: %s" % chroot_path
            )
        rel_path = chroot_path[len(chroot_prefix) :]
        # combine the chromeos root path + '/src/...'
        abs_path = os.path.join(chromeos_root, rel_path)
        abs_paths.append(abs_path)
    return abs_paths
