#!/usr/bin/env python3
# Copyright 2024 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Sets up src/third_party/llvm-project for cros workon from an LLVM ebuild."""

import argparse
import dataclasses
import logging
from pathlib import Path
import re
import shlex
import subprocess
import sys
from typing import List, Union

import git_llvm_rev


@dataclasses.dataclass(frozen=True)
class LLVMSourceDir:
    """An LLVM source dir, with convenient additional accessors."""

    path: Path

    def cros_workon_subdir(self):
        """Returns the subdir used for communicating with the ebuild."""
        return self.path / ".ebuild"


def apply_patches(
    llvm_dir: LLVMSourceDir,
    patch_manager: Path,
    patch_metadata_file: Path,
    current_rev: git_llvm_rev.Rev,
) -> None:
    """Applies patches using `patch_manager` to `llvm_dir`."""
    subprocess.run(
        [
            patch_manager,
            f"--svn_version={current_rev.number}",
            f"--src_path={llvm_dir.path}",
            f"--patch_metadata_file={patch_metadata_file}",
        ],
        check=True,
        stdin=subprocess.DEVNULL,
    )


def find_ebuild_in_dir(ebuild_dir: Path) -> Path:
    """Returns the path to a 9999 ebuild in `ebuild_dir`; raises if none."""
    candidates = list(ebuild_dir.glob("*-9999.ebuild"))
    if len(candidates) != 1:
        raise ValueError(
            f"Expected exactly one 9999 ebuild in {ebuild_dir}; found "
            f"{candidates}"
        )
    return candidates[0]


def write_gentoo_cmake_hack(llvm_dir: LLVMSourceDir, ebuild_dir: Path) -> None:
    """Modifies cmake files in LLVM so cmake.eclass doesn't modify them."""
    # Upstream's `cmake.eclass` will try to override "dangerous" configurations
    # that override Gentoo settings. There's no way to skip this override, but
    # it _does_ have logic to detect if it has already run & skips all
    # modifications in that case. Since LLVM has no such "dangerous" settings,
    # and the `9999` ebuild never "goes live," it's safe to skip these.

    # The file to modify is the 'main' cmake file, which is determined based on
    # `CMAKE_USE_DIR`. Parsing that out isn't _too_ painful, so try it.
    ebuild_path = find_ebuild_in_dir(ebuild_dir)
    ebuild_contents = ebuild_path.read_text(encoding="utf-8")
    cmake_use_dir_re = re.compile(
        # Use string concatenation rather than re.VERBOSE, since this regex
        # goes in an error message on failure, and that's _really_ hard to
        # read.
        r"^\s*"
        # While these all use `export`, it's not strictly required by
        # cmake.eclass.
        r"(?:export\s+)?" r'CMAKE_USE_DIR="\$\{S\}/([^"]+)"',
        re.MULTILINE,
    )
    cmake_use_dirs = cmake_use_dir_re.findall(ebuild_contents)
    if len(cmake_use_dirs) != 1:
        raise ValueError(
            f"Expected to find 1 match of {cmake_use_dir_re} in "
            f"{ebuild_path}; found {len(cmake_use_dirs)}"
        )

    cmake_file = llvm_dir.path / cmake_use_dirs[0] / "CMakeLists.txt"
    special_marker = "<<< Gentoo configuration >>>"
    if special_marker in cmake_file.read_text(encoding="utf-8"):
        return

    with cmake_file.open("a", encoding="utf-8") as f:
        f.write(f"\n# HACK from setup_from_workon.py:\n# {special_marker}")


def write_patch_application_stamp(
    llvm_dir: LLVMSourceDir, package_name: str
) -> None:
    """Writes a stamp file to note that patches have been applied."""
    stamp_path = (
        llvm_dir.cros_workon_subdir()
        / "stamps"
        / "patches_applied"
        / package_name
    )
    stamp_path.parent.mkdir(parents=True, exist_ok=True)
    stamp_path.touch()


def main(argv: List[str]) -> None:
    logging.basicConfig(
        format=">> %(asctime)s: %(levelname)s: %(filename)s:%(lineno)d: "
        "%(message)s",
        level=logging.INFO,
    )

    my_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--llvm-dir",
        type=lambda x: LLVMSourceDir(path=Path(x)),
        default=LLVMSourceDir(path=my_dir.parent.parent / "llvm-project"),
        help="Path containing a directory with llvm sources.",
    )
    parser.add_argument(
        "--ebuild-dir",
        type=Path,
        help="""
        Directory of the ebuild we're trying to set up. If this isn't
        specified, `--package` should be specified, and this will be
        autodetected. Example: ${cros_overlay}/sys-devel/llvm.
        """,
    )
    parser.add_argument(
        "--checkout",
        help="""
        If specified, the llvm directory will be checked out to the given SHA.
        """,
    )
    parser.add_argument(
        "--clean-llvm",
        action="store_true",
        help="""
        If passed, a series of commands will be run to reset the LLVM directory
        to HEAD prior to applying patches. **This flag deletes all staged
        unstaged changes, and deletes all untracked files**.
        """,
    )
    parser.add_argument(
        "--package",
        help="""
        Name of the package to set up for, in the form '${CATEGORY}/${PN}'.
        This must be provided unless `--ebuild-dir` is provided. Example:
        sys-devel/llvm.
        """,
    )
    parser.add_argument(
        "--no-commit",
        dest="commit",
        action="store_false",
        help="Don't create a commit with all changes applied.",
    )
    parser.add_argument(
        "--workon-board",
        dest="workon_board",
        help="""
        Ensure cros workon for the given board after applying changes.
        Set to 'host' for working on the host system, and not a board.
        """,
    )
    opts = parser.parse_args(argv)

    ebuild_dir = opts.ebuild_dir
    package_name = opts.package
    if not ebuild_dir and not package_name:
        parser.error(
            "At least one of --ebuild-dir or --package must be specified."
        )

    if not ebuild_dir:
        # All of these are in chromiumos-overlay, so just use that as a basis.
        ebuild_dir = my_dir.parent.parent / "chromiumos-overlay" / package_name
        logging.info("Ebuild directory is %s.", ebuild_dir)
    elif not package_name:
        package_name = f"{ebuild_dir.parent.name}/{ebuild_dir.name}"
        logging.info("Package is %s.", package_name)

    git_housekeeping_commands: List[List[Union[Path, str]]] = []
    if opts.clean_llvm:
        git_housekeeping_commands += (
            ["git", "clean", "-fd", "."],
            ["git", "reset", "--hard", "HEAD"],
        )

    if opts.checkout:
        git_housekeeping_commands.append(
            ["git", "checkout", "--quiet", opts.checkout],
        )

    for cmd in git_housekeeping_commands:
        subprocess.run(
            cmd,
            cwd=opts.llvm_dir.path,
            check=True,
            stdin=subprocess.DEVNULL,
        )

    rev = git_llvm_rev.translate_sha_to_rev(
        git_llvm_rev.LLVMConfig(
            remote="cros",
            dir=opts.llvm_dir.path,
        ),
        subprocess.run(
            ["git", "rev-parse", "HEAD"],
            check=True,
            cwd=opts.llvm_dir.path,
            stdin=subprocess.DEVNULL,
            encoding="utf-8",
            stdout=subprocess.PIPE,
        ).stdout.strip(),
    )

    logging.info("Applying patches...")
    files_dir = ebuild_dir / "files"
    apply_patches(
        opts.llvm_dir,
        patch_manager=files_dir / "patch_manager" / "patch_manager.py",
        patch_metadata_file=files_dir / "PATCHES.json",
        current_rev=rev,
    )
    write_patch_application_stamp(opts.llvm_dir, package_name)
    write_gentoo_cmake_hack(opts.llvm_dir, ebuild_dir)

    if opts.commit:
        subprocess.run(
            ["git", "add", "."],
            check=True,
            cwd=opts.llvm_dir.path,
            stdin=subprocess.DEVNULL,
        )
        subprocess.run(
            [
                "git",
                "commit",
                "--message",
                "Patches applied and markers added.",
            ],
            check=True,
            cwd=opts.llvm_dir.path,
            stdin=subprocess.DEVNULL,
        )

    if not opts.workon_board:
        logging.warning(
            "Didn't ensure 'workon' for any board or host."
            " Make sure you've called 'cros workon [...] start %s'"
            " before building!",
            package_name,
        )
        return

    if opts.workon_board == "host":
        cmd = ["cros", "workon", "--host", "start", package_name]
    else:
        cmd = [
            "cros",
            "workon",
            f"-b={opts.workon_board}",
            "start",
            package_name,
        ]
    subprocess.run(cmd, check=True, stdin=subprocess.DEVNULL)
    logging.info(
        "Successfully workon-ed: %s", shlex.join([str(c) for c in cmd])
    )


if __name__ == "__main__":
    main(sys.argv[1:])
