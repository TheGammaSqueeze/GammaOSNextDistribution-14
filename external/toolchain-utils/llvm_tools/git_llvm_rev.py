#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Maps LLVM git SHAs to synthetic revision numbers and back.

Revision numbers are all of the form '(branch_name, r1234)'. As a shorthand,
r1234 is parsed as '(main, 1234)'.
"""

import argparse
from pathlib import Path
import re
import subprocess
import sys
from typing import IO, Iterable, List, NamedTuple, Optional, Tuple, Union


MAIN_BRANCH = "main"

# Note that after base_llvm_sha, we reach The Wild West(TM) of commits.
# So reasonable input that could break us includes:
#
#   Revert foo
#
#   This reverts foo, which had the commit message:
#
#   bar
#   llvm-svn: 375505
#
# While saddening, this is something we should probably try to handle
# reasonably.
base_llvm_revision = 375505
base_llvm_sha = "186155b89c2d2a2f62337081e3ca15f676c9434b"

# Known pairs of [revision, SHA] in ascending order.
# The first element is the first non-`llvm-svn` commit that exists. Later ones
# are functional nops, but speed this script up immensely, since `git` can take
# quite a while to walk >100K commits.
known_llvm_rev_sha_pairs = (
    (base_llvm_revision, base_llvm_sha),
    (425000, "af870e11aed7a5c475ae41a72e3015c4c88597d1"),
    (450000, "906ebd5830e6053b50c52bf098e3586b567e8499"),
    (475000, "530d14a99611a71f8f3eb811920fd7b5c4d4e1f8"),
    (500000, "173855f9b0bdfe45d71272596b510650bfc1ca33"),
)

# Represents an LLVM git checkout:
#  - |dir| is the directory of the LLVM checkout
#  - |remote| is the name of the LLVM remote. Generally it's "origin".
LLVMConfig = NamedTuple(
    "LLVMConfig", (("remote", str), ("dir", Union[Path, str]))
)


class Rev(NamedTuple("Rev", (("branch", str), ("number", int)))):
    """Represents a LLVM 'revision', a shorthand identifies a LLVM commit."""

    @staticmethod
    def parse(rev: str) -> "Rev":
        """Parses a Rev from the given string.

        Raises a ValueError on a failed parse.
        """
        # Revs are parsed into (${branch_name}, r${commits_since_base_commit})
        # pairs.
        #
        # We support r${commits_since_base_commit} as shorthand for
        # (main, r${commits_since_base_commit}).
        if rev.startswith("r"):
            branch_name = MAIN_BRANCH
            rev_string = rev[1:]
        else:
            match = re.match(r"\((.+), r(\d+)\)", rev)
            if not match:
                raise ValueError("%r isn't a valid revision" % rev)

            branch_name, rev_string = match.groups()

        return Rev(branch=branch_name, number=int(rev_string))

    def __str__(self) -> str:
        branch_name, number = self
        if branch_name == MAIN_BRANCH:
            return "r%d" % number
        return "(%s, r%d)" % (branch_name, number)


def is_git_sha(xs: str) -> bool:
    """Returns whether the given string looks like a valid git commit SHA."""
    return (
        len(xs) > 6
        and len(xs) <= 40
        and all(x.isdigit() or "a" <= x.lower() <= "f" for x in xs)
    )


def check_output(command: List[str], cwd: Union[Path, str]) -> str:
    """Shorthand for subprocess.check_output. Auto-decodes any stdout."""
    result = subprocess.run(
        command,
        cwd=cwd,
        check=True,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        encoding="utf-8",
    )
    return result.stdout


def translate_prebase_sha_to_rev_number(
    llvm_config: LLVMConfig, sha: str
) -> int:
    """Translates a sha to a revision number (e.g., "llvm-svn: 1234").

    This function assumes that the given SHA is an ancestor of |base_llvm_sha|.
    """
    commit_message = check_output(
        ["git", "log", "-n1", "--format=%B", sha, "--"],
        cwd=llvm_config.dir,
    )
    last_line = commit_message.strip().splitlines()[-1]
    svn_match = re.match(r"^llvm-svn: (\d+)$", last_line)

    if not svn_match:
        raise ValueError(
            f"No llvm-svn line found for {sha}, which... shouldn't happen?"
        )

    return int(svn_match.group(1))


def translate_sha_to_rev(llvm_config: LLVMConfig, sha_or_ref: str) -> Rev:
    """Translates a sha or git ref to a Rev."""

    if is_git_sha(sha_or_ref):
        sha = sha_or_ref
    else:
        sha = check_output(
            ["git", "rev-parse", "--revs-only", sha_or_ref, "--"],
            cwd=llvm_config.dir,
        )
        sha = sha.strip()

    for base_rev, base_sha in reversed(known_llvm_rev_sha_pairs):
        merge_base = check_output(
            ["git", "merge-base", base_sha, sha, "--"],
            cwd=llvm_config.dir,
        )
        merge_base = merge_base.strip()
        if merge_base == base_sha:
            result = check_output(
                [
                    "git",
                    "rev-list",
                    "--count",
                    "--first-parent",
                    f"{base_sha}..{sha}",
                    "--",
                ],
                cwd=llvm_config.dir,
            )
            count = int(result.strip())
            return Rev(branch=MAIN_BRANCH, number=count + base_rev)

    # Otherwise, either:
    # - |merge_base| is |sha| (we have a guaranteed llvm-svn number on |sha|)
    # - |merge_base| is neither (we have a guaranteed llvm-svn number on
    #                            |merge_base|, but not |sha|)
    merge_base_number = translate_prebase_sha_to_rev_number(
        llvm_config, merge_base
    )
    if merge_base == sha:
        return Rev(branch=MAIN_BRANCH, number=merge_base_number)

    distance_from_base = check_output(
        [
            "git",
            "rev-list",
            "--count",
            "--first-parent",
            f"{merge_base}..{sha}",
            "--",
        ],
        cwd=llvm_config.dir,
    )

    revision_number = merge_base_number + int(distance_from_base.strip())
    branches_containing = check_output(
        ["git", "branch", "-r", "--contains", sha],
        cwd=llvm_config.dir,
    )

    candidates = []

    prefix = llvm_config.remote + "/"
    for branch in branches_containing.splitlines():
        branch = branch.strip()
        if branch.startswith(prefix):
            candidates.append(branch[len(prefix) :])

    if not candidates:
        raise ValueError(
            f"No viable branches found from {llvm_config.remote} with {sha}"
        )

    # It seems that some `origin/release/.*` branches have
    # `origin/upstream/release/.*` equivalents, which is... awkward to deal
    # with. Prefer the latter, since that seems to have newer commits than the
    # former. Technically n^2, but len(elements) should be like, tens in the
    # worst case.
    candidates = [x for x in candidates if f"upstream/{x}" not in candidates]
    if len(candidates) != 1:
        raise ValueError(
            f"Ambiguity: multiple branches from {llvm_config.remote} have "
            f"{sha}: {sorted(candidates)}"
        )

    return Rev(branch=candidates[0], number=revision_number)


def parse_git_commit_messages(
    stream: Union[Iterable[str], IO[str]], separator: str
) -> Iterable[Tuple[str, str]]:
    """Parses a stream of git log messages.

    These are expected to be in the format:

    40 character sha
    commit
    message
    body
    separator
    40 character sha
    commit
    message
    body
    separator
    """

    lines = iter(stream)
    while True:
        # Looks like a potential bug in pylint? crbug.com/1041148
        # pylint: disable=stop-iteration-return
        sha = next(lines, None)
        if sha is None:
            return

        sha = sha.strip()
        assert is_git_sha(sha), f"Invalid git SHA: {sha}"

        message = []
        for line in lines:
            if line.strip() == separator:
                break
            message.append(line)

        yield sha, "".join(message)


def translate_prebase_rev_to_sha(llvm_config: LLVMConfig, rev: Rev) -> str:
    """Translates a Rev to a SHA.

    This function assumes that the given rev refers to a commit that's an
    ancestor of |base_llvm_sha|.
    """
    # Because reverts may include reverted commit messages, we can't just |-n1|
    # and pick that.
    separator = ">!" * 80
    looking_for = f"llvm-svn: {rev.number}"

    git_command = [
        "git",
        "log",
        "--grep",
        f"^{looking_for}$",
        f"--format=%H%n%B{separator}",
        base_llvm_sha,
    ]

    with subprocess.Popen(
        git_command,
        cwd=llvm_config.dir,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        encoding="utf-8",
    ) as subp:
        assert subp.stdout is not None
        for sha, message in parse_git_commit_messages(subp.stdout, separator):
            last_line = message.splitlines()[-1]
            if last_line.strip() == looking_for:
                subp.terminate()
                return sha
        if subp.wait() != 0:
            raise subprocess.CalledProcessError(subp.returncode, git_command)

    raise ValueError(f"No commit with revision {rev} found")


def translate_rev_to_sha_from_baseline(
    llvm_config: LLVMConfig,
    parent_sha: str,
    parent_rev: int,
    child_sha: str,
    child_rev: Optional[int],
    want_rev: int,
    branch_name: str,
) -> str:
    """Translates a revision number between a parent & child to a SHA.

    Args:
        llvm_config: LLVM config to use.
        parent_sha: SHA of the parent that the revision number is a child of.
        parent_rev: Revision number of `parent_sha`.
        child_sha: A child of `parent_sha` to find a rev on.
        child_rev: Optional note of what the child's revision number is.
        want_rev: The desired revision number between child and parent.
        branch_name: Name of the branch to refer to if a ValueError is raised.

    Raises:
        ValueError if the given child isn't far enough away from the parent to
        find `want_rev`.
    """
    # As a convenience, have a fast path for want_rev < parent_rev. In
    # particular, branches can hit this case.
    if want_rev < parent_rev:
        baseline_git_sha = parent_sha
        commits_behind_baseline = parent_rev - want_rev
    else:
        if child_rev is None:
            commits_between_parent_and_child = check_output(
                [
                    "git",
                    "rev-list",
                    "--count",
                    "--first-parent",
                    f"{parent_sha}..{child_sha}",
                    "--",
                ],
                cwd=llvm_config.dir,
            )
            child_rev = parent_rev + int(
                commits_between_parent_and_child.strip()
            )
        if child_rev < want_rev:
            raise ValueError(
                "Revision {want_rev} is past "
                f"{llvm_config.remote}/{branch_name}. Try updating your tree?"
            )
        baseline_git_sha = child_sha
        commits_behind_baseline = child_rev - want_rev

    if not commits_behind_baseline:
        return baseline_git_sha

    result = check_output(
        [
            "git",
            "rev-parse",
            "--revs-only",
            f"{baseline_git_sha}~{commits_behind_baseline}",
        ],
        cwd=llvm_config.dir,
    )
    return result.strip()


def translate_rev_to_sha(llvm_config: LLVMConfig, rev: Rev) -> str:
    """Translates a Rev to a SHA.

    Raises a ValueError if the given Rev doesn't exist in the given config.
    """
    branch, number = rev

    branch_tip = check_output(
        ["git", "rev-parse", "--revs-only", f"{llvm_config.remote}/{branch}"],
        cwd=llvm_config.dir,
    ).strip()

    if branch != MAIN_BRANCH:
        main_merge_point = check_output(
            [
                "git",
                "merge-base",
                f"{llvm_config.remote}/{MAIN_BRANCH}",
                branch_tip,
            ],
            cwd=llvm_config.dir,
        )
        main_merge_point = main_merge_point.strip()
        main_rev = translate_sha_to_rev(llvm_config, main_merge_point)
        return translate_rev_to_sha_from_baseline(
            llvm_config,
            parent_sha=main_merge_point,
            parent_rev=main_rev.number,
            child_sha=branch_tip,
            child_rev=None,
            want_rev=number,
            branch_name=branch,
        )

    if number < base_llvm_revision:
        return translate_prebase_rev_to_sha(llvm_config, rev)

    # Technically this could be a binary search, but the list has fewer than 10
    # elems, and won't grow fast. Linear is easier.
    last_cached_rev = None
    last_cached_sha = branch_tip
    for cached_rev, cached_sha in reversed(known_llvm_rev_sha_pairs):
        if cached_rev == number:
            return cached_sha

        if cached_rev < number:
            return translate_rev_to_sha_from_baseline(
                llvm_config,
                parent_sha=cached_sha,
                parent_rev=cached_rev,
                child_sha=last_cached_sha,
                child_rev=last_cached_rev,
                want_rev=number,
                branch_name=branch,
            )

        last_cached_rev = cached_rev
        last_cached_sha = cached_sha

    # This is only hit if `number >= base_llvm_revision` _and_ there's no
    # coverage for `number` in `known_llvm_rev_sha_pairs`, which contains
    # `base_llvm_revision`.
    assert False, "Couldn't find a base SHA for a rev on main?"


def find_root_llvm_dir(root_dir: str = ".") -> str:
    """Finds the root of an LLVM directory starting at |root_dir|.

    Raises a subprocess.CalledProcessError if no git directory is found.
    """
    result = check_output(
        ["git", "rev-parse", "--show-toplevel"],
        cwd=root_dir,
    )
    return result.strip()


def main(argv: List[str]) -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--llvm_dir",
        help="LLVM directory to consult for git history, etc. Autodetected "
        "if cwd is inside of an LLVM tree",
    )
    parser.add_argument(
        "--upstream",
        default="origin",
        help="LLVM upstream's remote name. Defaults to %(default)s.",
    )
    sha_or_rev = parser.add_mutually_exclusive_group(required=True)
    sha_or_rev.add_argument(
        "--sha", help="A git SHA (or ref) to convert to a rev"
    )
    sha_or_rev.add_argument("--rev", help="A rev to convert into a sha")
    opts = parser.parse_args(argv)

    llvm_dir = opts.llvm_dir
    if llvm_dir is None:
        try:
            llvm_dir = find_root_llvm_dir()
        except subprocess.CalledProcessError:
            parser.error(
                "Couldn't autodetect an LLVM tree; please use --llvm_dir"
            )

    config = LLVMConfig(
        remote=opts.upstream,
        dir=opts.llvm_dir or find_root_llvm_dir(),
    )

    if opts.sha:
        rev = translate_sha_to_rev(config, opts.sha)
        print(rev)
    else:
        sha = translate_rev_to_sha(config, Rev.parse(opts.rev))
        print(sha)


if __name__ == "__main__":
    main(sys.argv[1:])
