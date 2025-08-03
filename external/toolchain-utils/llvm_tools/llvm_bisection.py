#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Performs bisection on LLVM based off a .JSON file."""

import argparse
import enum
import errno
import json
import os
import subprocess
import sys

import chroot
import get_llvm_hash
import git_llvm_rev
import modify_a_tryjob
import update_chromeos_llvm_hash
import update_tryjob_status


class BisectionExitStatus(enum.Enum):
    """Exit code when performing bisection."""

    # Means that there are no more revisions available to bisect.
    BISECTION_COMPLETE = 126


def GetCommandLineArgs():
    """Parses the command line for the command line arguments."""

    # Default path to the chroot if a path is not specified.
    cros_root = os.path.expanduser("~")
    cros_root = os.path.join(cros_root, "chromiumos")

    # Create parser and add optional command-line arguments.
    parser = argparse.ArgumentParser(
        description="Bisects LLVM via tracking a JSON file."
    )

    # Add argument for other change lists that want to run alongside the tryjob
    # which has a change list of updating a package's git hash.
    parser.add_argument(
        "--parallel",
        type=int,
        default=3,
        help="How many tryjobs to create between the last good version and "
        "the first bad version (default: %(default)s)",
    )

    # Add argument for the good LLVM revision for bisection.
    parser.add_argument(
        "--start_rev",
        required=True,
        type=int,
        help="The good revision for the bisection.",
    )

    # Add argument for the bad LLVM revision for bisection.
    parser.add_argument(
        "--end_rev",
        required=True,
        type=int,
        help="The bad revision for the bisection.",
    )

    # Add argument for the absolute path to the file that contains information
    # on the previous tested svn version.
    parser.add_argument(
        "--last_tested",
        required=True,
        help="the absolute path to the file that contains the tryjobs",
    )

    # Add argument for the absolute path to the LLVM source tree.
    parser.add_argument(
        "--src_path",
        help="the path to the LLVM source tree to use (used for retrieving the "
        "git hash of each version between the last good version and first bad "
        "version)",
    )

    # Add argument for other change lists that want to run alongside the tryjob
    # which has a change list of updating a package's git hash.
    parser.add_argument(
        "--extra_change_lists",
        type=int,
        nargs="+",
        help="change lists that would like to be run alongside the change list "
        "of updating the packages",
    )

    # Add argument for custom options for the tryjob.
    parser.add_argument(
        "--options",
        required=False,
        nargs="+",
        help="options to use for the tryjob testing",
    )

    # Add argument for the builder to use for the tryjob.
    parser.add_argument(
        "--builder", required=True, help="builder to use for the tryjob testing"
    )

    # Add argument for the description of the tryjob.
    parser.add_argument(
        "--description",
        required=False,
        nargs="+",
        help="the description of the tryjob",
    )

    # Add argument for a specific chroot path.
    parser.add_argument(
        "--chroot_path",
        default=cros_root,
        help="the path to the chroot (default: %(default)s)",
    )

    # Add argument for whether to display command contents to `stdout`.
    parser.add_argument(
        "--nocleanup",
        action="store_false",
        dest="cleanup",
        help="Abandon CLs created for bisectoin",
    )

    args_output = parser.parse_args()

    assert (
        args_output.start_rev < args_output.end_rev
    ), "Start revision %d is >= end revision %d" % (
        args_output.start_rev,
        args_output.end_rev,
    )

    if args_output.last_tested and not args_output.last_tested.endswith(
        ".json"
    ):
        raise ValueError(
            'Filed provided %s does not end in ".json"'
            % args_output.last_tested
        )

    return args_output


def GetRemainingRange(start, end, tryjobs):
    """Gets the start and end intervals in 'json_file'.

    Args:
        start: The start version of the bisection provided via the command line.
        end: The end version of the bisection provided via the command line.
        tryjobs: A list of tryjobs where each element is in the following
        format:
        [
            {[TRYJOB_INFORMATION]},
            {[TRYJOB_INFORMATION]},
            ...,
            {[TRYJOB_INFORMATION]}
        ]

    Returns:
        The new start version and end version for bisection, a set of revisions
        that are 'pending' and a set of revisions that are to be skipped.

    Raises:
        ValueError: The value for 'status' is missing or there is a mismatch
        between 'start' and 'end' compared to the 'start' and 'end' in the JSON
        file.
        AssertionError: The new start version is >= than the new end version.
    """

    if not tryjobs:
        return start, end, {}, {}

    # Verify that each tryjob has a value for the 'status' key.
    for cur_tryjob_dict in tryjobs:
        if not cur_tryjob_dict.get("status", None):
            raise ValueError(
                '"status" is missing or has no value, please '
                "go to %s and update it" % cur_tryjob_dict["link"]
            )

    all_bad_revisions = [end]
    all_bad_revisions.extend(
        cur_tryjob["rev"]
        for cur_tryjob in tryjobs
        if cur_tryjob["status"] == update_tryjob_status.TryjobStatus.BAD.value
    )

    # The minimum value for the 'bad' field in the tryjobs is the new end
    # version.
    bad_rev = min(all_bad_revisions)

    all_good_revisions = [start]
    all_good_revisions.extend(
        cur_tryjob["rev"]
        for cur_tryjob in tryjobs
        if cur_tryjob["status"] == update_tryjob_status.TryjobStatus.GOOD.value
    )

    # The maximum value for the 'good' field in the tryjobs is the new start
    # version.
    good_rev = max(all_good_revisions)

    # The good version should always be strictly less than the bad version;
    # otherwise, bisection is broken.
    assert (
        good_rev < bad_rev
    ), "Bisection is broken because %d (good) is >= " "%d (bad)" % (
        good_rev,
        bad_rev,
    )

    # Find all revisions that are 'pending' within 'good_rev' and 'bad_rev'.
    #
    # NOTE: The intent is to not launch tryjobs between 'good_rev' and 'bad_rev'
    # that have already been launched (this set is used when constructing the
    # list of revisions to launch tryjobs for).
    pending_revisions = {
        tryjob["rev"]
        for tryjob in tryjobs
        if tryjob["status"] == update_tryjob_status.TryjobStatus.PENDING.value
        and good_rev < tryjob["rev"] < bad_rev
    }

    # Find all revisions that are to be skipped within 'good_rev' and 'bad_rev'.
    #
    # NOTE: The intent is to not launch tryjobs between 'good_rev' and 'bad_rev'
    # that have already been marked as 'skip' (this set is used when
    # constructing the list of revisions to launch tryjobs for).
    skip_revisions = {
        tryjob["rev"]
        for tryjob in tryjobs
        if tryjob["status"] == update_tryjob_status.TryjobStatus.SKIP.value
        and good_rev < tryjob["rev"] < bad_rev
    }

    return good_rev, bad_rev, pending_revisions, skip_revisions


def GetCommitsBetween(
    start, end, parallel, src_path, pending_revisions, skip_revisions
):
    """Determines the revisions between start and end."""

    with get_llvm_hash.LLVMHash().CreateTempDirectory() as temp_dir:
        # We have guaranteed contiguous revision numbers after this,
        # and that guarnatee simplifies things considerably, so we don't
        # support anything before it.
        assert (
            start >= git_llvm_rev.base_llvm_revision
        ), f"{start} was too long ago"

        with get_llvm_hash.CreateTempLLVMRepo(temp_dir) as new_repo:
            if not src_path:
                src_path = new_repo
            index_step = (end - (start + 1)) // (parallel + 1)
            if not index_step:
                index_step = 1
            revisions = [
                rev
                for rev in range(start + 1, end, index_step)
                if rev not in pending_revisions and rev not in skip_revisions
            ]
            git_hashes = [
                get_llvm_hash.GetGitHashFrom(src_path, rev) for rev in revisions
            ]
            return revisions, git_hashes


def Bisect(
    revisions,
    git_hashes,
    bisect_state,
    last_tested,
    update_packages,
    chroot_path,
    extra_change_lists,
    options,
    builder,
):
    """Adds tryjobs and updates the status file with the new tryjobs."""

    try:
        for svn_revision, git_hash in zip(revisions, git_hashes):
            tryjob_dict = modify_a_tryjob.AddTryjob(
                update_packages,
                git_hash,
                svn_revision,
                chroot_path,
                extra_change_lists,
                options,
                builder,
                svn_revision,
            )

            bisect_state["jobs"].append(tryjob_dict)
    finally:
        # Do not want to lose progress if there is an exception.
        if last_tested:
            new_file = "%s.new" % last_tested
            with open(new_file, "w", encoding="utf-8") as json_file:
                json.dump(
                    bisect_state, json_file, indent=4, separators=(",", ": ")
                )

            os.rename(new_file, last_tested)


def LoadStatusFile(last_tested, start, end):
    """Loads the status file for bisection."""

    try:
        with open(last_tested, encoding="utf-8") as f:
            return json.load(f)
    except IOError as err:
        if err.errno != errno.ENOENT:
            raise

    return {"start": start, "end": end, "jobs": []}


def main(args_output):
    """Bisects LLVM commits.

    Raises:
        AssertionError: The script was run inside the chroot.
    """

    chroot.VerifyOutsideChroot()
    chroot.VerifyChromeOSRoot(args_output.chroot_path)
    start = args_output.start_rev
    end = args_output.end_rev

    bisect_state = LoadStatusFile(args_output.last_tested, start, end)
    if start != bisect_state["start"] or end != bisect_state["end"]:
        raise ValueError(
            f"The start {start} or the end {end} version provided is "
            f'different than "start" {bisect_state["start"]} or "end" '
            f'{bisect_state["end"]} in the .JSON file'
        )

    # Pending and skipped revisions are between 'start_rev' and 'end_rev'.
    start_rev, end_rev, pending_revs, skip_revs = GetRemainingRange(
        start, end, bisect_state["jobs"]
    )

    revisions, git_hashes = GetCommitsBetween(
        start_rev,
        end_rev,
        args_output.parallel,
        args_output.src_path,
        pending_revs,
        skip_revs,
    )

    # No more revisions between 'start_rev' and 'end_rev', so
    # bisection is complete.
    #
    # This is determined by finding all valid revisions between 'start_rev'
    # and 'end_rev' and that are NOT in the 'pending' and 'skipped' set.
    if not revisions:
        if pending_revs:
            # Some tryjobs are not finished which may change the actual bad
            # commit/revision when those tryjobs are finished.
            no_revisions_message = (
                f"No revisions between start {start_rev} "
                f"and end {end_rev} to create tryjobs\n"
            )

            if pending_revs:
                no_revisions_message += (
                    "The following tryjobs are pending:\n"
                    + "\n".join(str(rev) for rev in pending_revs)
                    + "\n"
                )

            if skip_revs:
                no_revisions_message += (
                    "The following tryjobs were skipped:\n"
                    + "\n".join(str(rev) for rev in skip_revs)
                    + "\n"
                )

            raise ValueError(no_revisions_message)

        print(f"Finished bisecting for {args_output.last_tested}")
        if args_output.src_path:
            bad_llvm_hash = get_llvm_hash.GetGitHashFrom(
                args_output.src_path, end_rev
            )
        else:
            bad_llvm_hash = get_llvm_hash.LLVMHash().GetLLVMHash(end_rev)
        print(
            f"The bad revision is {end_rev} and its commit hash is "
            f"{bad_llvm_hash}"
        )
        if skip_revs:
            skip_revs_message = (
                "\nThe following revisions were skipped:\n"
                + "\n".join(str(rev) for rev in skip_revs)
            )
            print(skip_revs_message)

        if args_output.cleanup:
            # Abandon all the CLs created for bisection
            gerrit = os.path.join(
                args_output.chroot_path, "chromite/bin/gerrit"
            )
            for build in bisect_state["jobs"]:
                try:
                    subprocess.check_output(
                        [gerrit, "abandon", str(build["cl"])],
                        stderr=subprocess.STDOUT,
                        encoding="utf-8",
                    )
                except subprocess.CalledProcessError as err:
                    # the CL may have been abandoned
                    if "chromite.lib.gob_util.GOBError" not in err.output:
                        raise

        return BisectionExitStatus.BISECTION_COMPLETE.value

    for rev in revisions:
        if (
            update_tryjob_status.FindTryjobIndex(rev, bisect_state["jobs"])
            is not None
        ):
            raise ValueError(f'Revision {rev} exists already in "jobs"')

    Bisect(
        revisions,
        git_hashes,
        bisect_state,
        args_output.last_tested,
        update_chromeos_llvm_hash.DEFAULT_PACKAGES,
        args_output.chroot_path,
        args_output.extra_change_lists,
        args_output.options,
        args_output.builder,
    )


if __name__ == "__main__":
    sys.exit(main(GetCommandLineArgs()))
