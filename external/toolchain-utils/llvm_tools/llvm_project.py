# Copyright 2020 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Module for manipulating llvm-project-copy. Generally intended for tests."""

import datetime
import os
import subprocess
import sys

import get_llvm_hash
import git_llvm_rev


def get_location() -> str:
    """Gets the absolute path for llvm-project-copy."""
    my_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(my_dir, "llvm-project-copy")


def ensure_up_to_date() -> None:
    """Ensures that llvm-project-copy is checked out and semi-up-to-date."""

    checkout = get_location()
    if not os.path.isdir(checkout):
        print(
            "No llvm-project exists locally; syncing it. This takes a while.",
            file=sys.stderr,
        )
        actual_checkout = get_llvm_hash.GetAndUpdateLLVMProjectInLLVMTools()
        assert checkout == actual_checkout, "%s != %s" % (
            actual_checkout,
            checkout,
        )

    commit_timestamp = subprocess.check_output(
        [
            "git",
            "log",
            "-n1",
            "--format=%ct",
            "origin/" + git_llvm_rev.MAIN_BRANCH,
        ],
        cwd=checkout,
        encoding="utf-8",
    )

    commit_time = datetime.datetime.fromtimestamp(int(commit_timestamp.strip()))
    now = datetime.datetime.now()

    time_since_last_commit = now - commit_time

    # Arbitrary, but if it's been more than 2d since we've seen a commit, it's
    # probably best to bring us up-to-date.
    if time_since_last_commit <= datetime.timedelta(days=2):
        return

    print(
        "%d days have elapsed since the last commit to %s; auto-syncing"
        % (time_since_last_commit.days, checkout),
        file=sys.stderr,
    )

    result = subprocess.run(
        ["git", "fetch", "origin"], check=False, cwd=checkout
    )
    if result.returncode:
        print(
            "Sync failed somehow; hoping that things are fresh enough, then...",
            file=sys.stderr,
        )
