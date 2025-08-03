#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Returns the latest LLVM version's hash."""

import argparse
import contextlib
import functools
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from typing import Iterator, Optional, Tuple, Union

import git_llvm_rev
import subprocess_helpers


_LLVM_GIT_URL = (
    "https://chromium.googlesource.com/external/github.com/llvm/llvm-project"
)

KNOWN_HASH_SOURCES = {"google3", "google3-unstable", "tot"}


def GetVersionFrom(src_dir: Union[Path, str], git_hash: str) -> int:
    """Obtain an SVN-style version number based on the LLVM git hash passed in.

    Args:
        src_dir: LLVM's source directory.
        git_hash: The git hash.

    Returns:
        An SVN-style version number associated with the git hash.
    """

    version = git_llvm_rev.translate_sha_to_rev(
        git_llvm_rev.LLVMConfig(remote="origin", dir=src_dir), git_hash
    )
    # Note: branches aren't supported
    assert version.branch == git_llvm_rev.MAIN_BRANCH, version.branch
    return version.number


def GetGitHashFrom(src_dir: Union[Path, str], version: int) -> str:
    """Finds the commit hash(es) of the LLVM version in the git log history.

    Args:
        src_dir: The LLVM source tree.
        version: The version number.

    Returns:
        A git hash string corresponding to the version number.

    Raises:
        subprocess.CalledProcessError: Failed to find a git hash.
    """

    return git_llvm_rev.translate_rev_to_sha(
        git_llvm_rev.LLVMConfig(remote="origin", dir=src_dir),
        git_llvm_rev.Rev(branch=git_llvm_rev.MAIN_BRANCH, number=version),
    )


def CheckoutBranch(src_dir: Union[Path, str], branch: str) -> None:
    """Checks out and pulls from a branch in a git repo.

    Args:
        src_dir: The LLVM source tree.
        branch: The git branch to checkout in src_dir.

    Raises:
        ValueError: Failed to checkout or pull branch version
    """
    subprocess_helpers.CheckCommand(["git", "-C", src_dir, "checkout", branch])
    subprocess_helpers.CheckCommand(["git", "-C", src_dir, "pull"])


def ParseLLVMMajorVersion(cmakelist: str):
    """Reads CMakeList.txt file contents for LLVMMajor Version.

    Args:
        cmakelist: contents of CMakeList.txt

    Returns:
        The major version number as a string

    Raises:
        ValueError: The major version cannot be parsed from cmakelist
    """
    match = re.search(
        r"\n\s+set\(LLVM_VERSION_MAJOR (?P<major>\d+)\)", cmakelist
    )
    if not match:
        raise ValueError("Failed to parse CMakeList for llvm major version")
    return match.group("major")


@functools.lru_cache(maxsize=1)
def GetLLVMMajorVersion(git_hash: Optional[str] = None) -> str:
    """Reads llvm/CMakeList.txt file contents for LLVMMajor Version.

    Args:
        git_hash: git hash of llvm version as string or None for top of trunk

    Returns:
        The major version number as a string

    Raises:
        ValueError: The major version cannot be parsed from cmakelist or
          there was a failure to checkout git_hash version
        FileExistsError: The src directory doe not contain CMakeList.txt
    """
    src_dir = GetAndUpdateLLVMProjectInLLVMTools()
    cmakelists_path = os.path.join(src_dir, "llvm", "CMakeLists.txt")
    if git_hash:
        subprocess_helpers.CheckCommand(
            ["git", "-C", src_dir, "checkout", git_hash]
        )
    try:
        with open(cmakelists_path, encoding="utf-8") as cmakelists_file:
            return ParseLLVMMajorVersion(cmakelists_file.read())
    finally:
        if git_hash:
            CheckoutBranch(src_dir, git_llvm_rev.MAIN_BRANCH)


@contextlib.contextmanager
def CreateTempLLVMRepo(temp_dir: str) -> Iterator[str]:
    """Adds a LLVM worktree to 'temp_dir'.

    Creating a worktree because the LLVM source tree in
    '../toolchain-utils/llvm_tools/llvm-project-copy' should not be modified.

    This is useful for applying patches to a source tree but do not want to
    modify the actual LLVM source tree in 'llvm-project-copy'.

    Args:
        temp_dir: An absolute path to the temporary directory to put the
        worktree in (obtained via 'tempfile.mkdtemp()').

    Yields:
        The absolute path to 'temp_dir'.

    Raises:
        subprocess.CalledProcessError: Failed to remove the worktree.
        ValueError: Failed to add a worktree.
    """

    abs_path_to_llvm_project_dir = GetAndUpdateLLVMProjectInLLVMTools()
    subprocess_helpers.CheckCommand(
        [
            "git",
            "-C",
            abs_path_to_llvm_project_dir,
            "worktree",
            "add",
            "--detach",
            temp_dir,
            "origin/%s" % git_llvm_rev.MAIN_BRANCH,
        ]
    )

    try:
        yield temp_dir
    finally:
        if os.path.isdir(temp_dir):
            subprocess_helpers.check_output(
                [
                    "git",
                    "-C",
                    abs_path_to_llvm_project_dir,
                    "worktree",
                    "remove",
                    "-f",
                    temp_dir,
                ]
            )


def GetAndUpdateLLVMProjectInLLVMTools() -> str:
    """Gets the absolute path to 'llvm-project-copy' directory in 'llvm_tools'.

    The intent of this function is to avoid cloning the LLVM repo and then
    discarding the contents of the repo. The function will create a directory
    in '../toolchain-utils/llvm_tools' called 'llvm-project-copy' if this
    directory does not exist yet. If it does not exist, then it will use the
    LLVMHash() class to clone the LLVM repo into 'llvm-project-copy'.
    Otherwise, it will clean the contents of that directory and then fetch from
    the chromium LLVM mirror. In either case, this function will return the
    absolute path to 'llvm-project-copy' directory.

    Returns:
        Absolute path to 'llvm-project-copy' directory in 'llvm_tools'

    Raises:
        ValueError: LLVM repo (in 'llvm-project-copy' dir.) has changes or
        failed to checkout to main or failed to fetch from chromium mirror of
        LLVM.
    """

    abs_path_to_llvm_tools_dir = os.path.dirname(os.path.abspath(__file__))

    abs_path_to_llvm_project_dir = os.path.join(
        abs_path_to_llvm_tools_dir, "llvm-project-copy"
    )

    if not os.path.isdir(abs_path_to_llvm_project_dir):
        print(
            f"Checking out LLVM to {abs_path_to_llvm_project_dir}\n"
            "so that we can map between commit hashes and revision numbers.\n"
            "This may take a while, but only has to be done once.",
            file=sys.stderr,
        )
        os.mkdir(abs_path_to_llvm_project_dir)

        LLVMHash().CloneLLVMRepo(abs_path_to_llvm_project_dir)
    else:
        # `git status` has a '-s'/'--short' option that shortens the output.
        # With the '-s' option, if no changes were made to the LLVM repo, then
        # the output (assigned to 'repo_status') would be empty.
        repo_status = subprocess_helpers.check_output(
            ["git", "-C", abs_path_to_llvm_project_dir, "status", "-s"]
        )

        if repo_status.rstrip():
            raise ValueError(
                "LLVM repo in %s has changes, please remove."
                % abs_path_to_llvm_project_dir
            )

        CheckoutBranch(abs_path_to_llvm_project_dir, git_llvm_rev.MAIN_BRANCH)

    return abs_path_to_llvm_project_dir


def GetGoogle3LLVMVersion(stable: bool) -> int:
    """Gets the latest google3 LLVM version.

    Args:
        stable: boolean, use the stable version or the unstable version

    Returns:
        The latest LLVM SVN version as an integer.

    Raises:
        subprocess.CalledProcessError: An invalid path has been provided to the
        `cat` command.
    """

    subdir = "stable" if stable else "llvm_unstable"

    # Cmd to get latest google3 LLVM version.
    cmd = [
        "cat",
        os.path.join(
            "/google/src/head/depot/google3/third_party/crosstool/v18",
            subdir,
            "installs/llvm/git_origin_rev_id",
        ),
    ]

    # Get latest version.
    git_hash = subprocess_helpers.check_output(cmd)

    # Change type to an integer
    return GetVersionFrom(
        GetAndUpdateLLVMProjectInLLVMTools(), git_hash.rstrip()
    )


def IsSvnOption(svn_option: str) -> Union[int, str]:
    """Validates whether the argument (string) is a git hash option.

    The argument is used to find the git hash of LLVM.

    Args:
        svn_option: The option passed in as a command line argument.

    Returns:
        lowercase svn_option if it is a known hash source, otherwise the
        svn_option as an int

    Raises:
        ValueError: Invalid svn option provided.
    """

    if svn_option.lower() in KNOWN_HASH_SOURCES:
        return svn_option.lower()

    try:
        svn_version = int(svn_option)

        return svn_version

    # Unable to convert argument to an int, so the option is invalid.
    #
    # Ex: 'one'.
    except ValueError:
        pass

    raise ValueError("Invalid LLVM git hash option provided: %s" % svn_option)


def GetLLVMHashAndVersionFromSVNOption(
    svn_option: Union[int, str]
) -> Tuple[str, int]:
    """Gets the LLVM hash and LLVM version based off of the svn option.

    Args:
        svn_option: A valid svn option obtained from the command line.
          Ex. 'google3', 'tot', or <svn_version> such as 365123.

    Returns:
        A tuple that is the LLVM git hash and LLVM version.
    """

    new_llvm_hash = LLVMHash()

    # Determine which LLVM git hash to retrieve.
    if svn_option == "tot":
        git_hash = new_llvm_hash.GetTopOfTrunkGitHash()
        version = GetVersionFrom(GetAndUpdateLLVMProjectInLLVMTools(), git_hash)
    elif isinstance(svn_option, int):
        version = svn_option
        git_hash = GetGitHashFrom(GetAndUpdateLLVMProjectInLLVMTools(), version)
    else:
        assert svn_option in ("google3", "google3-unstable")
        version = GetGoogle3LLVMVersion(stable=svn_option == "google3")

        git_hash = GetGitHashFrom(GetAndUpdateLLVMProjectInLLVMTools(), version)

    return git_hash, version


class LLVMHash:
    """Provides methods to retrieve a LLVM hash."""

    @staticmethod
    @contextlib.contextmanager
    def CreateTempDirectory() -> Iterator:
        temp_dir = tempfile.mkdtemp()

        try:
            yield temp_dir
        finally:
            if os.path.isdir(temp_dir):
                shutil.rmtree(temp_dir, ignore_errors=True)

    def CloneLLVMRepo(self, temp_dir: str) -> None:
        """Clones the LLVM repo.

        Args:
            temp_dir: The temporary directory to clone the repo to.

        Raises:
            ValueError: Failed to clone the LLVM repo.
        """
        clone_cmd = ["git", "clone", _LLVM_GIT_URL, temp_dir]
        clone_cmd_obj = subprocess.run(
            clone_cmd, check=False, stderr=subprocess.PIPE
        )
        if clone_cmd_obj.returncode:
            raise ValueError(
                "Failed to clone the LLVM repo; stderr: "
                f"{repr(clone_cmd_obj.stderr)}"
            )

    def GetLLVMHash(self, version: int) -> str:
        """Retrieves the LLVM hash corresponding to the LLVM version passed in.

        Args:
            version: The LLVM version to use as a delimiter.

        Returns:
            The hash as a string that corresponds to the LLVM version.
        """

        hash_value = GetGitHashFrom(
            GetAndUpdateLLVMProjectInLLVMTools(), version
        )
        return hash_value

    def GetGoogle3LLVMHash(self) -> str:
        """Retrieves the google3 LLVM hash."""

        return self.GetLLVMHash(GetGoogle3LLVMVersion(stable=True))

    def GetGoogle3UnstableLLVMHash(self) -> str:
        """Retrieves the LLVM hash of google3's unstable compiler."""
        return self.GetLLVMHash(GetGoogle3LLVMVersion(stable=False))

    def GetTopOfTrunkGitHash(self) -> str:
        """Gets the latest git hash from top of trunk of LLVM."""

        path_to_main_branch = "refs/heads/main"
        llvm_tot_git_hash = subprocess_helpers.check_output(
            ["git", "ls-remote", _LLVM_GIT_URL, path_to_main_branch]
        )
        return llvm_tot_git_hash.rstrip().split()[0]


def main() -> None:
    """Prints the git hash of LLVM.

    Parses the command line for the optional command line
    arguments.
    """

    # Create parser and add optional command-line arguments.
    parser = argparse.ArgumentParser(description="Finds the LLVM hash.")
    parser.add_argument(
        "--llvm_version",
        type=IsSvnOption,
        required=True,
        help="which git hash of LLVM to find. Either a svn revision, or one "
        "of %s" % sorted(KNOWN_HASH_SOURCES),
    )

    # Parse command-line arguments.
    args_output = parser.parse_args()

    cur_llvm_version = args_output.llvm_version

    new_llvm_hash = LLVMHash()

    if isinstance(cur_llvm_version, int):
        # Find the git hash of the specific LLVM version.
        print(new_llvm_hash.GetLLVMHash(cur_llvm_version))
    elif cur_llvm_version == "google3":
        print(new_llvm_hash.GetGoogle3LLVMHash())
    elif cur_llvm_version == "google3-unstable":
        print(new_llvm_hash.GetGoogle3UnstableLLVMHash())
    else:
        assert cur_llvm_version == "tot"
        print(new_llvm_hash.GetTopOfTrunkGitHash())


if __name__ == "__main__":
    main()
