#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Performs bisection on LLVM based off a .JSON file."""

import enum
import json
import os
import subprocess
import sys
import time
import traceback

import chroot
import llvm_bisection
import update_tryjob_status


# Used to re-try for 'llvm_bisection.py' to attempt to launch more tryjobs.
BISECTION_RETRY_TIME_SECS = 10 * 60

# Wait time to then poll each tryjob whose 'status' value is 'pending'.
POLL_RETRY_TIME_SECS = 30 * 60

# The number of attempts for 'llvm_bisection.py' to launch more tryjobs.
#
# It is reset (break out of the `for` loop/ exit the program) if successfully
# launched more tryjobs or bisection is finished (no more revisions between
# start and end of the bisection).
BISECTION_ATTEMPTS = 3

# The limit for updating all tryjobs whose 'status' is 'pending'.
#
# If the time that has passed for polling exceeds this value, then the program
# will exit with the appropriate exit code.
POLLING_LIMIT_SECS = 18 * 60 * 60


class BuilderStatus(enum.Enum):
    """Actual values given via 'cros buildresult'."""

    PASS = "pass"
    FAIL = "fail"
    RUNNING = "running"


# Writing a dict with `.value`s spelled out makes `black`'s style conflict with
# `cros lint`'s diagnostics.
builder_status_mapping = {
    a.value: b.value
    for a, b in (
        (BuilderStatus.PASS, update_tryjob_status.TryjobStatus.GOOD),
        (BuilderStatus.FAIL, update_tryjob_status.TryjobStatus.BAD),
        (BuilderStatus.RUNNING, update_tryjob_status.TryjobStatus.PENDING),
    )
}


def GetBuildResult(chroot_path, buildbucket_id):
    """Returns the conversion of the result of 'cros buildresult'."""

    # Calls 'cros buildresult' to get the status of the tryjob.
    try:
        tryjob_json = subprocess.check_output(
            [
                "cros",
                "buildresult",
                "--buildbucket-id",
                str(buildbucket_id),
                "--report",
                "json",
            ],
            cwd=chroot_path,
            stderr=subprocess.STDOUT,
            encoding="utf-8",
        )
    except subprocess.CalledProcessError as err:
        if "No build found. Perhaps not started" not in err.output:
            raise
        return None

    tryjob_content = json.loads(tryjob_json)

    build_result = str(tryjob_content["%d" % buildbucket_id]["status"])

    # The string returned by 'cros buildresult' might not be in the mapping.
    if build_result not in builder_status_mapping:
        raise ValueError(
            '"cros buildresult" return value is invalid: %s' % build_result
        )

    return builder_status_mapping[build_result]


def main():
    """Bisects LLVM using the result of `cros buildresult` of each tryjob.

    Raises:
        AssertionError: The script was run inside the chroot.
    """

    chroot.VerifyOutsideChroot()

    args_output = llvm_bisection.GetCommandLineArgs()

    chroot.VerifyChromeOSRoot(args_output.chroot_path)

    if os.path.isfile(args_output.last_tested):
        print("Resuming bisection for %s" % args_output.last_tested)
    else:
        print("Starting a new bisection for %s" % args_output.last_tested)

    while True:
        # Update the status of existing tryjobs
        if os.path.isfile(args_output.last_tested):
            update_start_time = time.time()
            with open(args_output.last_tested, encoding="utf-8") as json_file:
                json_dict = json.load(json_file)
            while True:
                print(
                    '\nAttempting to update all tryjobs whose "status" is '
                    '"pending":'
                )
                print("-" * 40)

                completed = True
                for tryjob in json_dict["jobs"]:
                    if (
                        tryjob["status"]
                        == update_tryjob_status.TryjobStatus.PENDING.value
                    ):
                        status = GetBuildResult(
                            args_output.chroot_path, tryjob["buildbucket_id"]
                        )
                        if status:
                            tryjob["status"] = status
                        else:
                            completed = False

                print("-" * 40)

                # Proceed to the next step if all the existing tryjobs have
                # completed.
                if completed:
                    break

                delta_time = time.time() - update_start_time

                if delta_time > POLLING_LIMIT_SECS:
                    # Something is wrong with updating the tryjobs's 'status'
                    # via `cros buildresult` (e.g. network issue, etc.).
                    sys.exit("Failed to update pending tryjobs.")

                print("-" * 40)
                print("Sleeping for %d minutes." % (POLL_RETRY_TIME_SECS // 60))
                time.sleep(POLL_RETRY_TIME_SECS)

            # There should always be update from the tryjobs launched in the
            # last iteration.
            temp_filename = "%s.new" % args_output.last_tested
            with open(temp_filename, "w", encoding="utf-8") as temp_file:
                json.dump(
                    json_dict, temp_file, indent=4, separators=(",", ": ")
                )
            os.rename(temp_filename, args_output.last_tested)

        # Launch more tryjobs.
        bisection_complete = (
            llvm_bisection.BisectionExitStatus.BISECTION_COMPLETE.value
        )
        for cur_try in range(1, BISECTION_ATTEMPTS + 1):
            try:
                print("\nAttempting to launch more tryjobs if possible:")
                print("-" * 40)

                bisection_ret = llvm_bisection.main(args_output)

                print("-" * 40)

                # Stop if the bisection has completed.
                if bisection_ret == bisection_complete:
                    sys.exit(0)

                # Successfully launched more tryjobs.
                break
            except Exception:
                traceback.print_exc()

                print("-" * 40)

                # Exceeded the number of times to launch more tryjobs.
                if cur_try == BISECTION_ATTEMPTS:
                    sys.exit("Unable to continue bisection.")

                num_retries_left = BISECTION_ATTEMPTS - cur_try

                print(
                    "Retries left to continue bisection %d." % num_retries_left
                )

                print(
                    "Sleeping for %d minutes."
                    % (BISECTION_RETRY_TIME_SECS // 60)
                )
                time.sleep(BISECTION_RETRY_TIME_SECS)


if __name__ == "__main__":
    main()
