#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for auto bisection of LLVM."""

import json
import os
import subprocess
import time
import traceback
import unittest
from unittest import mock

import auto_llvm_bisection
import chroot
import llvm_bisection
import test_helpers
import update_tryjob_status


class AutoLLVMBisectionTest(unittest.TestCase):
    """Unittests for auto bisection of LLVM."""

    @mock.patch.object(chroot, "VerifyChromeOSRoot")
    @mock.patch.object(chroot, "VerifyOutsideChroot", return_value=True)
    @mock.patch.object(
        llvm_bisection,
        "GetCommandLineArgs",
        return_value=test_helpers.ArgsOutputTest(),
    )
    @mock.patch.object(time, "sleep")
    @mock.patch.object(traceback, "print_exc")
    @mock.patch.object(llvm_bisection, "main")
    @mock.patch.object(os.path, "isfile")
    @mock.patch.object(auto_llvm_bisection, "open")
    @mock.patch.object(json, "load")
    @mock.patch.object(auto_llvm_bisection, "GetBuildResult")
    @mock.patch.object(os, "rename")
    def testAutoLLVMBisectionPassed(
        self,
        # pylint: disable=unused-argument
        mock_rename,
        mock_get_build_result,
        mock_json_load,
        # pylint: disable=unused-argument
        mock_open,
        mock_isfile,
        mock_llvm_bisection,
        mock_traceback,
        mock_sleep,
        mock_get_args,
        mock_outside_chroot,
        mock_chromeos_root,
    ):
        mock_isfile.side_effect = [False, False, True, True]
        mock_llvm_bisection.side_effect = [
            0,
            ValueError("Failed to launch more tryjobs."),
            llvm_bisection.BisectionExitStatus.BISECTION_COMPLETE.value,
        ]
        mock_json_load.return_value = {
            "start": 369410,
            "end": 369420,
            "jobs": [
                {
                    "buildbucket_id": 12345,
                    "rev": 369411,
                    "status": update_tryjob_status.TryjobStatus.PENDING.value,
                }
            ],
        }
        mock_get_build_result.return_value = (
            update_tryjob_status.TryjobStatus.GOOD.value
        )

        # Verify the excpetion is raised when successfully found the bad
        # revision. Uses `sys.exit(0)` to indicate success.
        with self.assertRaises(SystemExit) as err:
            auto_llvm_bisection.main()

        self.assertEqual(err.exception.code, 0)

        mock_outside_chroot.assert_called_once()
        mock_get_args.assert_called_once()
        self.assertEqual(mock_isfile.call_count, 3)
        self.assertEqual(mock_llvm_bisection.call_count, 3)
        mock_traceback.assert_called_once()
        mock_sleep.assert_called_once()

    @mock.patch.object(chroot, "VerifyChromeOSRoot")
    @mock.patch.object(chroot, "VerifyOutsideChroot", return_value=True)
    @mock.patch.object(time, "sleep")
    @mock.patch.object(traceback, "print_exc")
    @mock.patch.object(llvm_bisection, "main")
    @mock.patch.object(os.path, "isfile")
    @mock.patch.object(
        llvm_bisection,
        "GetCommandLineArgs",
        return_value=test_helpers.ArgsOutputTest(),
    )
    def testFailedToStartBisection(
        self,
        mock_get_args,
        mock_isfile,
        mock_llvm_bisection,
        mock_traceback,
        mock_sleep,
        mock_outside_chroot,
        mock_chromeos_root,
    ):
        mock_isfile.return_value = False
        mock_llvm_bisection.side_effect = ValueError(
            "Failed to launch more tryjobs."
        )

        # Verify the exception is raised when the number of attempts to launched
        # more tryjobs is exceeded, so unable to continue
        # bisection.
        with self.assertRaises(SystemExit) as err:
            auto_llvm_bisection.main()

        self.assertEqual(err.exception.code, "Unable to continue bisection.")

        mock_chromeos_root.assert_called_once()
        mock_outside_chroot.assert_called_once()
        mock_get_args.assert_called_once()
        self.assertEqual(mock_isfile.call_count, 2)
        self.assertEqual(mock_llvm_bisection.call_count, 3)
        self.assertEqual(mock_traceback.call_count, 3)
        self.assertEqual(mock_sleep.call_count, 2)

    @mock.patch.object(chroot, "VerifyChromeOSRoot")
    @mock.patch.object(chroot, "VerifyOutsideChroot", return_value=True)
    @mock.patch.object(
        llvm_bisection,
        "GetCommandLineArgs",
        return_value=test_helpers.ArgsOutputTest(),
    )
    @mock.patch.object(time, "time")
    @mock.patch.object(time, "sleep")
    @mock.patch.object(os.path, "isfile")
    @mock.patch.object(auto_llvm_bisection, "open")
    @mock.patch.object(json, "load")
    @mock.patch.object(auto_llvm_bisection, "GetBuildResult")
    def testFailedToUpdatePendingTryJobs(
        self,
        mock_get_build_result,
        mock_json_load,
        # pylint: disable=unused-argument
        mock_open,
        mock_isfile,
        mock_sleep,
        mock_time,
        mock_get_args,
        mock_outside_chroot,
        mock_chromeos_root,
    ):
        # Simulate behavior of `time.time()` for time passed.
        @test_helpers.CallCountsToMockFunctions
        def MockTimePassed(call_count):
            if call_count < 3:
                return call_count

            assert False, "Called `time.time()` more than expected."

        mock_isfile.return_value = True
        mock_json_load.return_value = {
            "start": 369410,
            "end": 369420,
            "jobs": [
                {
                    "buildbucket_id": 12345,
                    "rev": 369411,
                    "status": update_tryjob_status.TryjobStatus.PENDING.value,
                }
            ],
        }
        mock_get_build_result.return_value = None
        mock_time.side_effect = MockTimePassed
        # Reduce the polling limit for the test case to terminate faster.
        auto_llvm_bisection.POLLING_LIMIT_SECS = 1

        # Verify the exception is raised when unable to update tryjobs whose
        # 'status' value is 'pending'.
        with self.assertRaises(SystemExit) as err:
            auto_llvm_bisection.main()

        self.assertEqual(
            err.exception.code, "Failed to update pending tryjobs."
        )

        mock_outside_chroot.assert_called_once()
        mock_get_args.assert_called_once()
        self.assertEqual(mock_isfile.call_count, 2)
        mock_sleep.assert_called_once()
        self.assertEqual(mock_time.call_count, 3)

    @mock.patch.object(subprocess, "check_output")
    def testGetBuildResult(self, mock_chroot_command):
        buildbucket_id = 192
        status = auto_llvm_bisection.BuilderStatus.PASS.value
        tryjob_contents = {buildbucket_id: {"status": status}}
        mock_chroot_command.return_value = json.dumps(tryjob_contents)
        chroot_path = "/some/path/to/chroot"

        self.assertEqual(
            auto_llvm_bisection.GetBuildResult(chroot_path, buildbucket_id),
            update_tryjob_status.TryjobStatus.GOOD.value,
        )

        mock_chroot_command.assert_called_once_with(
            [
                "cros",
                "buildresult",
                "--buildbucket-id",
                str(buildbucket_id),
                "--report",
                "json",
            ],
            cwd="/some/path/to/chroot",
            stderr=subprocess.STDOUT,
            encoding="utf-8",
        )

    @mock.patch.object(subprocess, "check_output")
    def testGetBuildResultPassedWithUnstartedTryjob(self, mock_chroot_command):
        buildbucket_id = 192
        chroot_path = "/some/path/to/chroot"
        mock_chroot_command.side_effect = subprocess.CalledProcessError(
            returncode=1, cmd=[], output="No build found. Perhaps not started"
        )
        auto_llvm_bisection.GetBuildResult(chroot_path, buildbucket_id)
        mock_chroot_command.assert_called_once_with(
            [
                "cros",
                "buildresult",
                "--buildbucket-id",
                "192",
                "--report",
                "json",
            ],
            cwd=chroot_path,
            stderr=subprocess.STDOUT,
            encoding="utf-8",
        )

    @mock.patch.object(subprocess, "check_output")
    def testGetBuildReusultFailedWithInvalidBuildStatus(
        self, mock_chroot_command
    ):
        chroot_path = "/some/path/to/chroot"
        buildbucket_id = 50
        invalid_build_status = "querying"
        tryjob_contents = {buildbucket_id: {"status": invalid_build_status}}
        mock_chroot_command.return_value = json.dumps(tryjob_contents)

        # Verify an exception is raised when the return value of `cros
        # buildresult` is not in the `builder_status_mapping`.
        with self.assertRaises(ValueError) as err:
            auto_llvm_bisection.GetBuildResult(chroot_path, buildbucket_id)

        self.assertEqual(
            str(err.exception),
            '"cros buildresult" return value is invalid: %s'
            % invalid_build_status,
        )

        mock_chroot_command.assert_called_once_with(
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


if __name__ == "__main__":
    unittest.main()
