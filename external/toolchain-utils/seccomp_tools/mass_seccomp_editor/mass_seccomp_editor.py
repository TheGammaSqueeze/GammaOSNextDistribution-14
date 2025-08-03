#!/usr/bin/env python3

# Copyright 2021 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Script to make mass, CrOS-wide seccomp changes."""

import argparse
import re
import subprocess
import sys
import shutil
from typing import Any, Iterable, Optional
from dataclasses import dataclass, field

# Pre-compiled regexes.
AMD64_RE = re.compile(r".*(amd|x86_)64.*\.policy")
X86_RE = re.compile(r".*x86.*\.policy")
AARCH64_RE = re.compile(r".*a(arch|rm)64.*\.policy")
ARM_RE = re.compile(r".*arm(v7)?.*\.policy")


@dataclass(frozen=True)
class Policies:
    """Dataclass to hold lists of policies which match certain types."""

    arm: list[str] = field(default_factory=list)
    x86_64: list[str] = field(default_factory=list)
    x86: list[str] = field(default_factory=list)
    arm64: list[str] = field(default_factory=list)
    none: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, list[str]]:
        """Convert this class to a dictionary."""
        return {**self.__dict__}


def main():
    """Run the program from cmd line"""
    args = parse_args()
    if all(x is None for x in [args.all, args.b64, args.b32, args.none]):
        print(
            "Require at least one of {--all, --b64, --b32, --none}",
            file=sys.stderr,
        )
        sys.exit(1)
    matches, success = find_potential_policy_files(args.packages)

    separated = Policies()

    for m in matches:
        if AMD64_RE.match(m):
            separated.x86_64.append(m)
            continue
        if X86_RE.match(m):
            separated.x86.append(m)
            continue
        if AARCH64_RE.match(m):
            separated.arm64.append(m)
            continue
        if ARM_RE.match(m):
            separated.arm.append(m)
            continue
        separated.none.append(m)

    syscall_lookup_table = _make_syscall_lookup_table(args)

    for (type_, val) in separated.to_dict().items():
        for fp in val:
            syscalls = syscall_lookup_table[type_]
            missing = check_missing_syscalls(syscalls, fp)
            if missing is None:
                print(f"E ({type_}) {fp}")
            elif len(missing) == 0:
                print(f"_ ({type_}) {fp}")
            else:
                missing_str = ",".join(missing)
                print(f"M ({type_}) {fp} :: {missing_str}")

    if not args.edit:
        sys.exit(0 if success else 2)

    for (type_, val) in separated.to_dict().items():
        for fp in val:
            syscalls = syscall_lookup_table[type_]
            if args.force:
                _confirm_add(fp, syscalls, args.yes)
                continue
            missing = check_missing_syscalls(syscalls, fp)
            if missing is None or len(missing) == 0:
                print(f"Already good for {fp} ({type_})")
            else:
                _confirm_add(fp, missing, args.yes)

    sys.exit(0 if success else 2)


def _make_syscall_lookup_table(args: Any) -> dict[str, list[str]]:
    """Make lookup table, segmented by all/b32/b64/none policies.

    Args:
      args: Direct output from parse_args.

    Returns:
      dict of syscalls we want to search for in each policy file,
      where the key is the policy file arch, and the value is
      a list of syscalls as strings.
    """
    syscall_lookup_table = Policies().to_dict()
    if args.all:
        split_syscalls = [x.strip() for x in args.all.split(",")]
        for v in syscall_lookup_table.values():
            v.extend(split_syscalls)
    if args.b32:
        split_syscalls = [x.strip() for x in args.b32.split(",")]
        syscall_lookup_table["x86"].extend(split_syscalls)
        syscall_lookup_table["arm"].extend(split_syscalls)
    if args.b64:
        split_syscalls = [x.strip() for x in args.b64.split(",")]
        syscall_lookup_table["x86_64"].extend(split_syscalls)
        syscall_lookup_table["arm64"].extend(split_syscalls)
    if args.none:
        split_syscalls = [x.strip() for x in args.none.split(",")]
        syscall_lookup_table["none"].extend(split_syscalls)
    return syscall_lookup_table


def _confirm_add(fp: str, syscalls: Iterable[str], noninteractive=None):
    """Interactive confirmation check you wish to add a syscall.

    Args:
      fp: filepath of the file to edit.
      syscalls: list-like of syscalls to add to append to the files.
      noninteractive: Just add the syscalls without asking.
    """
    if noninteractive:
        _update_seccomp(fp, list(syscalls))
        return
    syscalls_str = ",".join(syscalls)
    user_input = input(f"Add {syscalls_str} for {fp}? [y/N]> ")
    if user_input.lower().startswith("y"):
        _update_seccomp(fp, list(syscalls))
        print("Edited!")
    else:
        print(f"Skipping {fp}")


def check_missing_syscalls(syscalls: list[str], fp: str) -> Optional[set[str]]:
    """Return which specified syscalls are missing in the given file."""
    missing_syscalls = set(syscalls)
    with open(fp) as f:
        try:
            lines = f.readlines()
            for syscall in syscalls:
                for line in lines:
                    if re.match(syscall + r":\s*1", line):
                        missing_syscalls.remove(syscall)
        except UnicodeDecodeError:
            return None
    return missing_syscalls


def _update_seccomp(fp: str, missing_syscalls: list[str]):
    """Update the seccomp of the file based on the seccomp change type."""
    with open(fp, "a") as f:
        sorted_syscalls = sorted(missing_syscalls)
        for to_write in sorted_syscalls:
            f.write(to_write + ": 1\n")


def _search_cmd(query: str, use_fd=True) -> list[str]:
    if use_fd and shutil.which("fdfind") is not None:
        return [
            "fdfind",
            "-t",
            "f",
            "--full-path",
            f"^.*{query}.*\\.policy$",
        ]
    return [
        "find",
        ".",
        "-regex",
        f"^.*{query}.*\\.policy$",
        "-type",
        "f",
    ]


def find_potential_policy_files(packages: list[str]) -> tuple[list[str], bool]:
    """Find potentially related policy files to the given packages.

    Returns:
      (policy_files, successful): A list of policy file paths, and a boolean
      indicating whether all queries were successful in finding at least
      one related policy file.
    """
    all_queries_succeeded = True
    matches = []
    for p in packages:
        # It's quite common that hyphens are translated to underscores
        # and similarly common that underscores are translated to hyphens.
        # We make them agnostic here.
        hyphen_agnostic = re.sub(r"[-_]", "[-_]", p)
        cmd = subprocess.run(
            _search_cmd(hyphen_agnostic),
            stdout=subprocess.PIPE,
            check=True,
        )
        new_matches = [a for a in cmd.stdout.decode("utf-8").split("\n") if a]
        if not new_matches:
            print(f"WARNING: No matches found for {p}", file=sys.stderr)
            all_queries_succeeded = False
        else:
            matches.extend(new_matches)
    return matches, all_queries_succeeded


def parse_args() -> Any:
    """Handle command line arguments."""
    parser = argparse.ArgumentParser(
        description="Check for missing syscalls in"
        " seccomp policy files, or make"
        " mass seccomp changes.\n\n"
        "The format of this output follows the template:\n"
        "    status (arch) local/policy/filepath :: syscall,syscall,syscall\n"
        'Where the status can be "_" for present, "M" for missing,'
        ' or "E" for Error\n\n'
        "Example:\n"
        "    mass_seccomp_editor.py --all fstatfs --b32 fstatfs64"
        " modemmanager\n\n"
        "Exit Codes:\n"
        "    '0' for successfully found specific policy files\n"
        "    '1' for python-related error.\n"
        "    '2' for no matched policy files for a given query.",
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument("packages", nargs="+")
    parser.add_argument(
        "--all",
        type=str,
        metavar="syscalls",
        help="comma separated syscalls to check in all policy files",
    )
    parser.add_argument(
        "--b64",
        type=str,
        metavar="syscalls",
        help="Comma separated syscalls to check in 64bit architectures",
    )
    parser.add_argument(
        "--b32",
        type=str,
        metavar="syscalls",
        help="Comma separated syscalls to check in 32bit architectures",
    )
    parser.add_argument(
        "--none",
        type=str,
        metavar="syscalls",
        help="Comma separated syscalls to check in unknown architectures",
    )
    parser.add_argument(
        "--edit",
        action="store_true",
        help="Make changes to the listed files,"
        " rather than just printing out what is missing",
    )
    parser.add_argument(
        "-y",
        "--yes",
        action="store_true",
        help='Say "Y" to all interactive checks',
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Edit all files, regardless of missing status."
        " Does nothing without --edit.",
    )
    return parser.parse_args()


if __name__ == "__main__":
    main()
