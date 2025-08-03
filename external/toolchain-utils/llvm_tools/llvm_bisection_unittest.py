#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# pylint: disable=protected-access

"""Tests for LLVM bisection."""

import json
import os
import subprocess
import unittest
from unittest import mock

import chroot
import get_llvm_hash
import git_llvm_rev
import llvm_bisection
import modify_a_tryjob
import test_helpers


class LLVMBisectionTest(unittest.TestCase):
    """Unittests for LLVM bisection."""

    def testGetRemainingRangePassed(self):
        start = 100
        end = 150

        test_tryjobs = [
            {
                "rev": 110,
                "status": "good",
                "link": "https://some_tryjob_1_url.com",
            },
            {
                "rev": 120,
                "status": "good",
                "link": "https://some_tryjob_2_url.com",
            },
            {
                "rev": 130,
                "status": "pending",
                "link": "https://some_tryjob_3_url.com",
            },
            {
                "rev": 135,
                "status": "skip",
                "link": "https://some_tryjob_4_url.com",
            },
            {
                "rev": 140,
                "status": "bad",
                "link": "https://some_tryjob_5_url.com",
            },
        ]

        # Tuple consists of the new good revision, the new bad revision, a set
        # of 'pending' revisions, and a set of 'skip' revisions.
        expected_revisions_tuple = 120, 140, {130}, {135}

        self.assertEqual(
            llvm_bisection.GetRemainingRange(start, end, test_tryjobs),
            expected_revisions_tuple,
        )

    def testGetRemainingRangeFailedWithMissingStatus(self):
        start = 100
        end = 150

        test_tryjobs = [
            {
                "rev": 105,
                "status": "good",
                "link": "https://some_tryjob_1_url.com",
            },
            {
                "rev": 120,
                "status": None,
                "link": "https://some_tryjob_2_url.com",
            },
            {
                "rev": 140,
                "status": "bad",
                "link": "https://some_tryjob_3_url.com",
            },
        ]

        with self.assertRaises(ValueError) as err:
            llvm_bisection.GetRemainingRange(start, end, test_tryjobs)

        error_message = (
            '"status" is missing or has no value, please '
            "go to %s and update it" % test_tryjobs[1]["link"]
        )
        self.assertEqual(str(err.exception), error_message)

    def testGetRemainingRangeFailedWithInvalidRange(self):
        start = 100
        end = 150

        test_tryjobs = [
            {
                "rev": 110,
                "status": "bad",
                "link": "https://some_tryjob_1_url.com",
            },
            {
                "rev": 125,
                "status": "skip",
                "link": "https://some_tryjob_2_url.com",
            },
            {
                "rev": 140,
                "status": "good",
                "link": "https://some_tryjob_3_url.com",
            },
        ]

        with self.assertRaises(AssertionError) as err:
            llvm_bisection.GetRemainingRange(start, end, test_tryjobs)

        expected_error_message = (
            "Bisection is broken because %d (good) is >= "
            "%d (bad)" % (test_tryjobs[2]["rev"], test_tryjobs[0]["rev"])
        )

        self.assertEqual(str(err.exception), expected_error_message)

    @mock.patch.object(get_llvm_hash, "GetGitHashFrom")
    def testGetCommitsBetweenPassed(self, mock_get_git_hash):
        start = git_llvm_rev.base_llvm_revision
        end = start + 10
        test_pending_revisions = {start + 7}
        test_skip_revisions = {
            start + 1,
            start + 2,
            start + 4,
            start + 8,
            start + 9,
        }
        parallel = 3
        abs_path_to_src = "/abs/path/to/src"

        revs = ["a123testhash3", "a123testhash5"]
        mock_get_git_hash.side_effect = revs

        git_hashes = [
            git_llvm_rev.base_llvm_revision + 3,
            git_llvm_rev.base_llvm_revision + 5,
        ]

        self.assertEqual(
            llvm_bisection.GetCommitsBetween(
                start,
                end,
                parallel,
                abs_path_to_src,
                test_pending_revisions,
                test_skip_revisions,
            ),
            (git_hashes, revs),
        )

    def testLoadStatusFilePassedWithExistingFile(self):
        start = 100
        end = 150

        test_bisect_state = {"start": start, "end": end, "jobs": []}

        # Simulate that the status file exists.
        with test_helpers.CreateTemporaryJsonFile() as temp_json_file:
            with open(temp_json_file, "w", encoding="utf-8") as f:
                test_helpers.WritePrettyJsonFile(test_bisect_state, f)

            self.assertEqual(
                llvm_bisection.LoadStatusFile(temp_json_file, start, end),
                test_bisect_state,
            )

    def testLoadStatusFilePassedWithoutExistingFile(self):
        start = 200
        end = 250

        expected_bisect_state = {"start": start, "end": end, "jobs": []}

        last_tested = "/abs/path/to/file_that_does_not_exist.json"

        self.assertEqual(
            llvm_bisection.LoadStatusFile(last_tested, start, end),
            expected_bisect_state,
        )

    @mock.patch.object(modify_a_tryjob, "AddTryjob")
    def testBisectPassed(self, mock_add_tryjob):
        git_hash_list = ["a123testhash1", "a123testhash2", "a123testhash3"]
        revisions_list = [102, 104, 106]

        # Simulate behavior of `AddTryjob()` when successfully launched a
        # tryjob for the updated packages.
        @test_helpers.CallCountsToMockFunctions
        def MockAddTryjob(
            call_count,
            _packages,
            _git_hash,
            _revision,
            _chroot_path,
            _extra_cls,
            _options,
            _builder,
            _svn_revision,
        ):
            if call_count < 2:
                return {"rev": revisions_list[call_count], "status": "pending"}

            # Simulate an exception happened along the way when updating the
            # packages' `LLVM_NEXT_HASH`.
            if call_count == 2:
                raise ValueError("Unable to launch tryjob")

            assert False, "Called `AddTryjob()` more than expected."

        # Use the test function to simulate `AddTryjob()`.
        mock_add_tryjob.side_effect = MockAddTryjob

        start = 100
        end = 110

        bisection_contents = {"start": start, "end": end, "jobs": []}

        args_output = test_helpers.ArgsOutputTest()

        packages = ["sys-devel/llvm"]

        # Create a temporary .JSON file to simulate a status file for bisection.
        with test_helpers.CreateTemporaryJsonFile() as temp_json_file:
            with open(temp_json_file, "w", encoding="utf-8") as f:
                test_helpers.WritePrettyJsonFile(bisection_contents, f)

            # Verify that the status file is updated when an exception happened
            # when attempting to launch a revision (i.e. progress is not lost).
            with self.assertRaises(ValueError) as err:
                llvm_bisection.Bisect(
                    revisions_list,
                    git_hash_list,
                    bisection_contents,
                    temp_json_file,
                    packages,
                    args_output.chroot_path,
                    args_output.extra_change_lists,
                    args_output.options,
                    args_output.builders,
                )

            expected_bisection_contents = {
                "start": start,
                "end": end,
                "jobs": [
                    {"rev": revisions_list[0], "status": "pending"},
                    {"rev": revisions_list[1], "status": "pending"},
                ],
            }

            # Verify that the launched tryjobs were added to the status file
            # when an exception happened.
            with open(temp_json_file, encoding="utf-8") as f:
                json_contents = json.load(f)

                self.assertEqual(json_contents, expected_bisection_contents)

        self.assertEqual(str(err.exception), "Unable to launch tryjob")

        self.assertEqual(mock_add_tryjob.call_count, 3)

    @mock.patch.object(subprocess, "check_output", return_value=None)
    @mock.patch.object(
        get_llvm_hash.LLVMHash, "GetLLVMHash", return_value="a123testhash4"
    )
    @mock.patch.object(llvm_bisection, "GetCommitsBetween")
    @mock.patch.object(llvm_bisection, "GetRemainingRange")
    @mock.patch.object(llvm_bisection, "LoadStatusFile")
    @mock.patch.object(chroot, "VerifyChromeOSRoot")
    @mock.patch.object(chroot, "VerifyOutsideChroot", return_value=True)
    def testMainPassed(
        self,
        mock_outside_chroot,
        mock_chromeos_root,
        mock_load_status_file,
        mock_get_range,
        mock_get_revision_and_hash_list,
        _mock_get_bad_llvm_hash,
        mock_abandon_cl,
    ):
        start = 500
        end = 502
        cl = 1

        bisect_state = {
            "start": start,
            "end": end,
            "jobs": [{"rev": 501, "status": "bad", "cl": cl}],
        }

        skip_revisions = {501}
        pending_revisions = {}

        mock_load_status_file.return_value = bisect_state

        mock_get_range.return_value = (
            start,
            end,
            pending_revisions,
            skip_revisions,
        )

        mock_get_revision_and_hash_list.return_value = [], []

        args_output = test_helpers.ArgsOutputTest()
        args_output.start_rev = start
        args_output.end_rev = end
        args_output.parallel = 3
        args_output.src_path = None
        args_output.chroot_path = "somepath"
        args_output.cleanup = True

        self.assertEqual(
            llvm_bisection.main(args_output),
            llvm_bisection.BisectionExitStatus.BISECTION_COMPLETE.value,
        )

        mock_chromeos_root.assert_called_once()

        mock_outside_chroot.assert_called_once()

        mock_load_status_file.assert_called_once()

        mock_get_range.assert_called_once()

        mock_get_revision_and_hash_list.assert_called_once()

        mock_abandon_cl.assert_called_once()
        self.assertEqual(
            mock_abandon_cl.call_args,
            mock.call(
                [
                    os.path.join(
                        args_output.chroot_path, "chromite/bin/gerrit"
                    ),
                    "abandon",
                    str(cl),
                ],
                stderr=subprocess.STDOUT,
                encoding="utf-8",
            ),
        )

    @mock.patch.object(llvm_bisection, "LoadStatusFile")
    @mock.patch.object(chroot, "VerifyChromeOSRoot")
    @mock.patch.object(chroot, "VerifyOutsideChroot", return_value=True)
    def testMainFailedWithInvalidRange(
        self, mock_chromeos_root, mock_outside_chroot, mock_load_status_file
    ):
        start = 500
        end = 502

        bisect_state = {
            "start": start - 1,
            "end": end,
        }

        mock_load_status_file.return_value = bisect_state

        args_output = test_helpers.ArgsOutputTest()
        args_output.start_rev = start
        args_output.end_rev = end
        args_output.parallel = 3
        args_output.src_path = None

        with self.assertRaises(ValueError) as err:
            llvm_bisection.main(args_output)

        error_message = (
            f"The start {start} or the end {end} version provided is "
            f'different than "start" {bisect_state["start"]} or "end" '
            f'{bisect_state["end"]} in the .JSON file'
        )

        self.assertEqual(str(err.exception), error_message)

        mock_chromeos_root.assert_called_once()

        mock_outside_chroot.assert_called_once()

        mock_load_status_file.assert_called_once()

    @mock.patch.object(llvm_bisection, "GetCommitsBetween")
    @mock.patch.object(llvm_bisection, "GetRemainingRange")
    @mock.patch.object(llvm_bisection, "LoadStatusFile")
    @mock.patch.object(chroot, "VerifyChromeOSRoot")
    @mock.patch.object(chroot, "VerifyOutsideChroot", return_value=True)
    def testMainFailedWithPendingBuilds(
        self,
        mock_chromeos_root,
        mock_outside_chroot,
        mock_load_status_file,
        mock_get_range,
        mock_get_revision_and_hash_list,
    ):
        start = 500
        end = 502
        rev = 501

        bisect_state = {
            "start": start,
            "end": end,
            "jobs": [{"rev": rev, "status": "pending"}],
        }

        skip_revisions = {}
        pending_revisions = {rev}

        mock_load_status_file.return_value = bisect_state

        mock_get_range.return_value = (
            start,
            end,
            pending_revisions,
            skip_revisions,
        )

        mock_get_revision_and_hash_list.return_value = [], []

        args_output = test_helpers.ArgsOutputTest()
        args_output.start_rev = start
        args_output.end_rev = end
        args_output.parallel = 3
        args_output.src_path = None

        with self.assertRaises(ValueError) as err:
            llvm_bisection.main(args_output)

        error_message = (
            f"No revisions between start {start} and end {end} to "
            "create tryjobs\nThe following tryjobs are pending:\n"
            f"{rev}\n"
        )

        self.assertEqual(str(err.exception), error_message)

        mock_chromeos_root.assert_called_once()

        mock_outside_chroot.assert_called_once()

        mock_load_status_file.assert_called_once()

        mock_get_range.assert_called_once()

        mock_get_revision_and_hash_list.assert_called_once()

    @mock.patch.object(llvm_bisection, "GetCommitsBetween")
    @mock.patch.object(llvm_bisection, "GetRemainingRange")
    @mock.patch.object(llvm_bisection, "LoadStatusFile")
    @mock.patch.object(chroot, "VerifyChromeOSRoot")
    @mock.patch.object(chroot, "VerifyOutsideChroot", return_value=True)
    def testMainFailedWithDuplicateBuilds(
        self,
        mock_outside_chroot,
        mock_chromeos_root,
        mock_load_status_file,
        mock_get_range,
        mock_get_revision_and_hash_list,
    ):
        start = 500
        end = 502
        rev = 501
        git_hash = "a123testhash1"

        bisect_state = {
            "start": start,
            "end": end,
            "jobs": [{"rev": rev, "status": "pending"}],
        }

        skip_revisions = {}
        pending_revisions = {rev}

        mock_load_status_file.return_value = bisect_state

        mock_get_range.return_value = (
            start,
            end,
            pending_revisions,
            skip_revisions,
        )

        mock_get_revision_and_hash_list.return_value = [rev], [git_hash]

        args_output = test_helpers.ArgsOutputTest()
        args_output.start_rev = start
        args_output.end_rev = end
        args_output.parallel = 3
        args_output.src_path = None

        with self.assertRaises(ValueError) as err:
            llvm_bisection.main(args_output)

        error_message = 'Revision %d exists already in "jobs"' % rev
        self.assertEqual(str(err.exception), error_message)

        mock_chromeos_root.assert_called_once()

        mock_outside_chroot.assert_called_once()

        mock_load_status_file.assert_called_once()

        mock_get_range.assert_called_once()

        mock_get_revision_and_hash_list.assert_called_once()

    @mock.patch.object(subprocess, "check_output", return_value=None)
    @mock.patch.object(
        get_llvm_hash.LLVMHash, "GetLLVMHash", return_value="a123testhash4"
    )
    @mock.patch.object(llvm_bisection, "GetCommitsBetween")
    @mock.patch.object(llvm_bisection, "GetRemainingRange")
    @mock.patch.object(llvm_bisection, "LoadStatusFile")
    @mock.patch.object(chroot, "VerifyChromeOSRoot")
    @mock.patch.object(chroot, "VerifyOutsideChroot", return_value=True)
    def testMainFailedToAbandonCL(
        self,
        mock_outside_chroot,
        mock_chromeos_root,
        mock_load_status_file,
        mock_get_range,
        mock_get_revision_and_hash_list,
        _mock_get_bad_llvm_hash,
        mock_abandon_cl,
    ):
        start = 500
        end = 502

        bisect_state = {
            "start": start,
            "end": end,
            "jobs": [{"rev": 501, "status": "bad", "cl": 0}],
        }

        skip_revisions = {501}
        pending_revisions = {}

        mock_load_status_file.return_value = bisect_state

        mock_get_range.return_value = (
            start,
            end,
            pending_revisions,
            skip_revisions,
        )

        mock_get_revision_and_hash_list.return_value = ([], [])

        error_message = "Error message."
        mock_abandon_cl.side_effect = subprocess.CalledProcessError(
            returncode=1, cmd=[], output=error_message
        )

        args_output = test_helpers.ArgsOutputTest()
        args_output.start_rev = start
        args_output.end_rev = end
        args_output.parallel = 3
        args_output.src_path = None
        args_output.cleanup = True

        with self.assertRaises(subprocess.CalledProcessError) as err:
            llvm_bisection.main(args_output)

        self.assertEqual(err.exception.output, error_message)

        mock_chromeos_root.assert_called_once()

        mock_outside_chroot.assert_called_once()

        mock_load_status_file.assert_called_once()

        mock_get_range.assert_called_once()


if __name__ == "__main__":
    unittest.main()
