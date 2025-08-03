#!/usr/bin/env python3
# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This script generates a PGO profile for llvm-next.

Do not run it inside of a chroot. It establishes a chroot of its own.
"""

import argparse
import dataclasses
import logging
import os
from pathlib import Path
import re
import shlex
import shutil
import sys
from typing import List

import pgo_tools


@dataclasses.dataclass(frozen=True)
class ChrootInfo:
    """Info that describes a unique chroot."""

    chroot_name: str
    out_dir_name: str


def find_repo_root(base_dir: Path) -> Path:
    """Returns the root of the user's ChromeOS checkout."""
    if (base_dir / ".repo").exists():
        return base_dir

    for parent in base_dir.parents:
        if (parent / ".repo").exists():
            return parent

    raise ValueError(f"No repo found above {base_dir}")


def create_fresh_bootstrap_chroot(repo_root: Path, chroot_info: ChrootInfo):
    """Creates a `--bootstrap` chroot without any updates applied."""
    pgo_tools.run(
        [
            "cros_sdk",
            "--replace",
            f"--chroot={chroot_info.chroot_name}",
            f"--out-dir={chroot_info.out_dir_name}",
            "--bootstrap",
            "--skip-chroot-upgrade",
        ],
        cwd=repo_root,
    )


def generate_pgo_profile(
    repo_root: Path,
    chroot_info: ChrootInfo,
    chroot_output_file: Path,
    use_var: str,
):
    """Generates a PGO profile to `chroot_output_file`."""
    pgo_tools.run(
        [
            "cros_sdk",
            f"--chroot={chroot_info.chroot_name}",
            f"--out-dir={chroot_info.out_dir_name}",
            "--skip-chroot-upgrade",
            f"USE={use_var}",
            "--",
            "/mnt/host/source/src/third_party/toolchain-utils/pgo_tools/"
            "generate_pgo_profile.py",
            f"--output={chroot_output_file}",
        ],
        cwd=repo_root,
    )


def compress_pgo_profile(pgo_profile: Path) -> Path:
    """Compresses a PGO profile for upload to gs://."""
    pgo_tools.run(
        ["xz", "-9", "-k", pgo_profile],
    )
    return Path(str(pgo_profile) + ".xz")


def translate_chroot_path_to_out_of_chroot(
    repo_root: Path, path: Path, info: ChrootInfo
) -> Path:
    """Translates a chroot path into an out-of-chroot path."""
    path_str = str(path)
    assert path_str.startswith("/tmp"), path
    # Remove the leading `/` from the output file so it joins properly.
    return repo_root / info.out_dir_name / str(path)[1:]


def locate_current_llvm_ebuild(repo_root: Path) -> Path:
    """Returns the path to our current LLVM ebuild."""
    llvm_subdir = (
        repo_root / "src/third_party/chromiumos-overlay/sys-devel/llvm"
    )
    candidates = [
        x for x in llvm_subdir.glob("*pre*ebuild") if not x.is_symlink()
    ]
    assert (
        len(candidates) == 1
    ), f"Found {len(candidates)} viable ebuilds; expected 1: {candidates}"
    return candidates[0]


def parse_llvm_next_hash(llvm_ebuild_contents: str) -> List[str]:
    """Parses the LLVM_NEXT hash from our LLVM ebuild."""
    matches = re.findall(
        r'^LLVM_NEXT_HASH="([a-f0-9A-F]{40})" # r\d+$',
        llvm_ebuild_contents,
        re.MULTILINE,
    )
    assert (
        len(matches) == 1
    ), f"Got {len(matches)} matches for llvm hash; expected 1"
    return matches[0]


def determine_upload_command(
    repo_root: Path, profile_path: Path
) -> pgo_tools.Command:
    """Returns a command that can be used to upload our PGO profile."""
    llvm_ebuild = locate_current_llvm_ebuild(repo_root)
    llvm_next_hash = parse_llvm_next_hash(
        llvm_ebuild.read_text(encoding="utf-8")
    )
    upload_target = (
        "gs://chromeos-localmirror/distfiles/llvm-profdata-"
        f"{llvm_next_hash}.xz"
    )
    return [
        "gsutil",
        "cp",
        "-n",
        "-a",
        "public-read",
        profile_path,
        upload_target,
    ]


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
        "--chroot",
        default="llvm-next-pgo-chroot",
        help="""
        Name of the chroot to create. Will be clobbered if it exists already.
        """,
    )
    parser.add_argument(
        "--out-dir",
        default="llvm-next-pgo-chroot_out",
        help="""
        Name of the out/ directory to use. Will be clobbered if it exists
        already.
        """,
    )
    parser.add_argument(
        "--upload",
        action="store_true",
        help="Upload the profile after creation. Implies --compress.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="""
        Additionally put the uncompressed profile at the this path after
        creation.
        """,
    )
    # This flag is required because the most common use-case (pardon the pun)
    # for this script is "generate the PGO profile for the next LLVM roll." It:
    # - seems very easy to forget to apply `USE=llvm-next`,
    # - is awkward to _force_ llvm-next silently, since the "most common"
    #   use-case is not the _only_ use-case, and
    # - is awkward to have a duo of `--llvm-next` / `--no-llvm-next` flags,
    #   since a single `--use=` provides way more flexibility.
    parser.add_argument(
        "--use",
        required=True,
        help="""
        The value to set for the USE variable when generating the profile. If
        you're the mage, you want --use=llvm-next. If you don't want to use
        anything, just pass `--use=`.
        """,
    )
    opts = parser.parse_args(argv)

    pgo_tools.exit_if_in_chroot()

    repo_root = find_repo_root(Path(os.getcwd()))
    logging.info("Repo root is %s", repo_root)

    logging.info("Creating new SDK")
    chroot_info = ChrootInfo(opts.chroot, opts.out_dir)
    try:
        create_fresh_bootstrap_chroot(repo_root, chroot_info)
        chroot_profile_path = Path("/tmp/llvm-next-pgo-profile.prof")
        generate_pgo_profile(
            repo_root, chroot_info, chroot_profile_path, opts.use
        )
        profile_path = translate_chroot_path_to_out_of_chroot(
            repo_root, chroot_profile_path, chroot_info
        )
        if opts.output:
            shutil.copyfile(profile_path, opts.output)

        compressed_profile_path = compress_pgo_profile(profile_path)
        upload_command = determine_upload_command(
            repo_root, compressed_profile_path
        )
        if opts.upload:
            pgo_tools.run(upload_command)
        else:
            friendly_upload_command = " ".join(
                shlex.quote(str(x)) for x in upload_command
            )
            logging.info(
                "To upload the profile, run %r in %r",
                friendly_upload_command,
                repo_root,
            )
    except:
        logging.warning(
            "NOTE: Chroot left at %s and out dir is left at %s. "
            "If you don't plan to rerun this script, delete them.",
            chroot_info.chroot_name,
            chroot_info.out_dir_name,
        )
        raise
    else:
        logging.info(
            "Feel free to delete chroot %s and out dir %s when you're done "
            "with them.",
            chroot_info.chroot_name,
            chroot_info.out_dir_name,
        )


if __name__ == "__main__":
    main(sys.argv[1:])
