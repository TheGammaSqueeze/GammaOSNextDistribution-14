#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unittests for running tests after updating packages."""

import json
import subprocess
import unittest
from unittest import mock

import chroot
import get_llvm_hash
import git
import test_helpers
import update_chromeos_llvm_hash
import update_packages_and_run_tests


# Testing with tryjobs.
class UpdatePackagesAndRunTryjobsTest(unittest.TestCase):
    """Unittests when running tryjobs after updating packages."""

    def testNoLastTestedFile(self):
        self.assertEqual(
            update_packages_and_run_tests.UnchangedSinceLastRun(None, {}), False
        )

    def testEmptyLastTestedFile(self):
        with test_helpers.CreateTemporaryFile() as temp_file:
            self.assertEqual(
                update_packages_and_run_tests.UnchangedSinceLastRun(
                    temp_file, {}
                ),
                False,
            )

    def testLastTestedFileDoesNotExist(self):
        # Simulate 'open()' on a lasted tested file that does not exist.
        mock.mock_open(read_data="")

        self.assertEqual(
            update_packages_and_run_tests.UnchangedSinceLastRun(
                "/some/file/that/does/not/exist.txt", {}
            ),
            False,
        )

    def testMatchedLastTestedFile(self):
        with test_helpers.CreateTemporaryFile() as last_tested_file:
            arg_dict = {
                "svn_version": 1234,
                "ebuilds": [
                    "/path/to/package1-r2.ebuild",
                    "/path/to/package2/package2-r3.ebuild",
                ],
                "builders": [
                    "kevin-llvm-next-toolchain-tryjob",
                    "eve-llvm-next-toolchain-tryjob",
                ],
                "extra_cls": [10, 1],
                "tryjob_options": ["latest-toolchain", "hwtest"],
            }

            with open(last_tested_file, "w", encoding="utf-8") as f:
                f.write(json.dumps(arg_dict, indent=2))

            self.assertEqual(
                update_packages_and_run_tests.UnchangedSinceLastRun(
                    last_tested_file, arg_dict
                ),
                True,
            )

    def testGetTryJobCommandWithNoExtraInformation(self):
        change_list = 1234

        builder = "nocturne"

        expected_cmd = [
            "cros",
            "tryjob",
            "--yes",
            "--json",
            "-g",
            "%d" % change_list,
            builder,
        ]

        self.assertEqual(
            update_packages_and_run_tests.GetTryJobCommand(
                change_list, None, None, builder
            ),
            expected_cmd,
        )

    def testGetTryJobCommandWithExtraInformation(self):
        change_list = 4321
        extra_cls = [1000, 10]
        options = ["option1", "option2"]
        builder = "kevin"

        expected_cmd = [
            "cros",
            "tryjob",
            "--yes",
            "--json",
            "-g",
            "%d" % change_list,
            "-g",
            "%d" % extra_cls[0],
            "-g",
            "%d" % extra_cls[1],
            "--%s" % options[0],
            "--%s" % options[1],
            builder,
        ]

        self.assertEqual(
            update_packages_and_run_tests.GetTryJobCommand(
                change_list, extra_cls, options, builder
            ),
            expected_cmd,
        )

    @mock.patch.object(
        update_packages_and_run_tests,
        "GetCurrentTimeInUTC",
        return_value="2019-09-09",
    )
    @mock.patch.object(update_packages_and_run_tests, "AddLinksToCL")
    @mock.patch.object(subprocess, "check_output")
    def testSuccessfullySubmittedTryJob(
        self, mock_cmd, mock_add_links_to_cl, mock_launch_time
    ):
        expected_cmd = [
            "cros",
            "tryjob",
            "--yes",
            "--json",
            "-g",
            "%d" % 900,
            "-g",
            "%d" % 1200,
            "--some_option",
            "builder1",
        ]

        bb_id = "1234"
        url = "http://ci.chromium.org/b/%s" % bb_id

        mock_cmd.return_value = json.dumps([{"id": bb_id, "url": url}])

        chroot_path = "/some/path/to/chroot"
        cl = 900
        extra_cls = [1200]
        options = ["some_option"]
        builders = ["builder1"]

        tests = update_packages_and_run_tests.RunTryJobs(
            cl, extra_cls, options, builders, chroot_path
        )

        expected_tests = [
            {
                "launch_time": mock_launch_time.return_value,
                "link": url,
                "buildbucket_id": int(bb_id),
                "extra_cls": extra_cls,
                "options": options,
                "builder": builders,
            }
        ]

        self.assertEqual(tests, expected_tests)

        mock_cmd.assert_called_once_with(
            expected_cmd, cwd=chroot_path, encoding="utf-8"
        )

        mock_add_links_to_cl.assert_called_once()

    @mock.patch.object(update_packages_and_run_tests, "AddLinksToCL")
    @mock.patch.object(subprocess, "check_output")
    def testSuccessfullySubmittedRecipeBuilders(
        self, mock_cmd, mock_add_links_to_cl
    ):
        expected_cmd = [
            "bb",
            "add",
            "-json",
            "-cl",
            "crrev.com/c/%s" % 900,
            "-cl",
            "crrev.com/c/%s" % 1200,
            "some_option",
            "builder1",
        ]

        bb_id = "1234"
        create_time = "2020-04-18T00:03:53.978767Z"

        mock_cmd.return_value = json.dumps(
            {"id": bb_id, "createTime": create_time}
        )

        chroot_path = "/some/path/to/chroot"
        cl = 900
        extra_cls = [1200]
        options = ["some_option"]
        builders = ["builder1"]

        tests = update_packages_and_run_tests.StartRecipeBuilders(
            cl, extra_cls, options, builders, chroot_path
        )

        expected_tests = [
            {
                "launch_time": create_time,
                "link": "http://ci.chromium.org/b/%s" % bb_id,
                "buildbucket_id": bb_id,
                "extra_cls": extra_cls,
                "options": options,
                "builder": builders,
            }
        ]

        self.assertEqual(tests, expected_tests)

        mock_cmd.assert_called_once_with(
            expected_cmd, cwd=chroot_path, encoding="utf-8"
        )

        mock_add_links_to_cl.assert_called_once()

    @mock.patch.object(subprocess, "check_output", return_value=None)
    def testSuccessfullyAddedTestLinkToCL(self, mock_exec_cmd):
        chroot_path = "/abs/path/to/chroot"

        test_cl_number = 1000

        tests = [{"link": "https://some_tryjob_link.com"}]

        update_packages_and_run_tests.AddLinksToCL(
            tests, test_cl_number, chroot_path
        )

        expected_gerrit_message = [
            "%s/chromite/bin/gerrit" % chroot_path,
            "message",
            str(test_cl_number),
            "Started the following tests:\n%s" % tests[0]["link"],
        ]

        mock_exec_cmd.assert_called_once_with(expected_gerrit_message)

    @mock.patch.object(update_packages_and_run_tests, "RunTryJobs")
    @mock.patch.object(update_chromeos_llvm_hash, "UpdatePackages")
    @mock.patch.object(update_packages_and_run_tests, "GetCommandLineArgs")
    @mock.patch.object(get_llvm_hash, "GetLLVMHashAndVersionFromSVNOption")
    @mock.patch.object(chroot, "VerifyChromeOSRoot")
    @mock.patch.object(chroot, "VerifyOutsideChroot", return_value=True)
    @mock.patch.object(chroot, "GetChrootEbuildPaths")
    def testUpdatedLastTestedFileWithNewTestedRevision(
        self,
        mock_get_chroot_build_paths,
        mock_outside_chroot,
        mock_chromeos_root,
        mock_get_hash_and_version,
        mock_get_commandline_args,
        mock_update_packages,
        mock_run_tryjobs,
    ):
        # Create a temporary file to simulate the last tested file that
        # contains a revision.
        with test_helpers.CreateTemporaryFile() as last_tested_file:
            builders = [
                "kevin-llvm-next-toolchain-tryjob",
                "eve-llvm-next-toolchain-tryjob",
            ]
            extra_cls = [10, 1]
            tryjob_options = ["latest-toolchain", "hwtest"]
            ebuilds = [
                "/path/to/package1/package1-r2.ebuild",
                "/path/to/package2/package2-r3.ebuild",
            ]

            arg_dict = {
                "svn_version": 100,
                "ebuilds": ebuilds,
                "builders": builders,
                "extra_cls": extra_cls,
                "tryjob_options": tryjob_options,
            }
            # Parepared last tested file
            with open(last_tested_file, "w", encoding="utf-8") as f:
                json.dump(arg_dict, f, indent=2)

            # Call with a changed LLVM svn version
            args_output = test_helpers.ArgsOutputTest()
            args_output.is_llvm_next = True
            args_output.extra_change_lists = extra_cls
            args_output.last_tested = last_tested_file
            args_output.reviewers = []

            args_output.subparser_name = "tryjobs"
            args_output.builders = builders
            args_output.options = tryjob_options

            mock_get_commandline_args.return_value = args_output

            mock_get_chroot_build_paths.return_value = ebuilds

            mock_get_hash_and_version.return_value = ("a123testhash2", 200)

            mock_update_packages.return_value = git.CommitContents(
                url="https://some_cl_url.com", cl_number=12345
            )

            mock_run_tryjobs.return_value = [
                {"link": "https://some_tryjob_url.com", "buildbucket_id": 1234}
            ]

            update_packages_and_run_tests.main()

            # Verify that the lasted tested file has been updated to the new
            # LLVM revision.
            with open(last_tested_file, encoding="utf-8") as f:
                arg_dict = json.load(f)

                self.assertEqual(arg_dict["svn_version"], 200)

        mock_outside_chroot.assert_called_once()

        mock_chromeos_root.assert_called_once()

        mock_get_commandline_args.assert_called_once()

        mock_get_hash_and_version.assert_called_once()

        mock_run_tryjobs.assert_called_once()

        mock_update_packages.assert_called_once()
        commit_msg_lines = mock_update_packages.call_args[1][
            "extra_commit_msg_lines"
        ]
        self.assertTrue(
            isinstance(commit_msg_lines, list), repr(commit_msg_lines)
        )


class UpdatePackagesAndRunTestCQTest(unittest.TestCase):
    """Unittests for CQ dry run after updating packages."""

    def testGetCQDependString(self):
        test_no_changelists = []
        test_single_changelist = [1234]
        test_multiple_changelists = [1234, 5678]

        self.assertIsNone(
            update_packages_and_run_tests.GetCQDependString(test_no_changelists)
        )

        self.assertEqual(
            update_packages_and_run_tests.GetCQDependString(
                test_single_changelist
            ),
            "Cq-Depend: chromium:1234",
        )

        self.assertEqual(
            update_packages_and_run_tests.GetCQDependString(
                test_multiple_changelists
            ),
            "Cq-Depend: chromium:1234, chromium:5678",
        )

    def testGetCQIncludeTrybotsString(self):
        test_no_trybot = None
        test_valid_trybot = "llvm-next"
        test_invalid_trybot = "invalid-name"

        self.assertIsNone(
            update_packages_and_run_tests.GetCQIncludeTrybotsString(
                test_no_trybot
            )
        )

        self.assertEqual(
            update_packages_and_run_tests.GetCQIncludeTrybotsString(
                test_valid_trybot
            ),
            "Cq-Include-Trybots:chromeos/cq:cq-llvm-next-orchestrator",
        )

        with self.assertRaises(ValueError) as context:
            update_packages_and_run_tests.GetCQIncludeTrybotsString(
                test_invalid_trybot
            )

        self.assertIn("is not a valid llvm trybot", str(context.exception))

    @mock.patch.object(subprocess, "check_output", return_value=None)
    def testStartCQDryRunNoDeps(self, mock_exec_cmd):
        chroot_path = "/abs/path/to/chroot"
        test_cl_number = 1000

        # test with no deps cls.
        extra_cls = []
        update_packages_and_run_tests.StartCQDryRun(
            test_cl_number, extra_cls, chroot_path
        )

        expected_gerrit_message = [
            "%s/chromite/bin/gerrit" % chroot_path,
            "label-cq",
            str(test_cl_number),
            "1",
        ]

        mock_exec_cmd.assert_called_once_with(expected_gerrit_message)

    # Mock ExecCommandAndCaptureOutput for the gerrit command execution.
    @mock.patch.object(subprocess, "check_output", return_value=None)
    # test with a single deps cl.
    def testStartCQDryRunSingleDep(self, mock_exec_cmd):
        chroot_path = "/abs/path/to/chroot"
        test_cl_number = 1000

        extra_cls = [2000]
        update_packages_and_run_tests.StartCQDryRun(
            test_cl_number, extra_cls, chroot_path
        )

        expected_gerrit_cmd_1 = [
            "%s/chromite/bin/gerrit" % chroot_path,
            "label-cq",
            str(test_cl_number),
            "1",
        ]
        expected_gerrit_cmd_2 = [
            "%s/chromite/bin/gerrit" % chroot_path,
            "label-cq",
            str(2000),
            "1",
        ]

        self.assertEqual(mock_exec_cmd.call_count, 2)
        self.assertEqual(
            mock_exec_cmd.call_args_list[0], mock.call(expected_gerrit_cmd_1)
        )
        self.assertEqual(
            mock_exec_cmd.call_args_list[1], mock.call(expected_gerrit_cmd_2)
        )

    # Mock ExecCommandAndCaptureOutput for the gerrit command execution.
    @mock.patch.object(subprocess, "check_output", return_value=None)
    def testStartCQDryRunMultipleDep(self, mock_exec_cmd):
        chroot_path = "/abs/path/to/chroot"
        test_cl_number = 1000

        # test with multiple deps cls.
        extra_cls = [3000, 4000]
        update_packages_and_run_tests.StartCQDryRun(
            test_cl_number, extra_cls, chroot_path
        )

        expected_gerrit_cmd_1 = [
            "%s/chromite/bin/gerrit" % chroot_path,
            "label-cq",
            str(test_cl_number),
            "1",
        ]
        expected_gerrit_cmd_2 = [
            "%s/chromite/bin/gerrit" % chroot_path,
            "label-cq",
            str(3000),
            "1",
        ]
        expected_gerrit_cmd_3 = [
            "%s/chromite/bin/gerrit" % chroot_path,
            "label-cq",
            str(4000),
            "1",
        ]

        self.assertEqual(mock_exec_cmd.call_count, 3)
        self.assertEqual(
            mock_exec_cmd.call_args_list[0], mock.call(expected_gerrit_cmd_1)
        )
        self.assertEqual(
            mock_exec_cmd.call_args_list[1], mock.call(expected_gerrit_cmd_2)
        )
        self.assertEqual(
            mock_exec_cmd.call_args_list[2], mock.call(expected_gerrit_cmd_3)
        )

    # Mock ExecCommandAndCaptureOutput for the gerrit command execution.
    @mock.patch.object(subprocess, "check_output", return_value=None)
    # test with no reviewers.
    def testAddReviewersNone(self, mock_exec_cmd):
        chroot_path = "/abs/path/to/chroot"
        reviewers = []
        test_cl_number = 1000

        update_packages_and_run_tests.AddReviewers(
            test_cl_number, reviewers, chroot_path
        )
        self.assertTrue(mock_exec_cmd.not_called)

    # Mock ExecCommandAndCaptureOutput for the gerrit command execution.
    @mock.patch.object(subprocess, "check_output", return_value=None)
    # test with multiple reviewers.
    def testAddReviewersMultiple(self, mock_exec_cmd):
        chroot_path = "/abs/path/to/chroot"
        reviewers = ["none1@chromium.org", "none2@chromium.org"]
        test_cl_number = 1000

        update_packages_and_run_tests.AddReviewers(
            test_cl_number, reviewers, chroot_path
        )

        expected_gerrit_cmd_1 = [
            "%s/chromite/bin/gerrit" % chroot_path,
            "reviewers",
            str(test_cl_number),
            "none1@chromium.org",
        ]
        expected_gerrit_cmd_2 = [
            "%s/chromite/bin/gerrit" % chroot_path,
            "reviewers",
            str(test_cl_number),
            "none2@chromium.org",
        ]

        self.assertEqual(mock_exec_cmd.call_count, 2)
        self.assertEqual(
            mock_exec_cmd.call_args_list[0], mock.call(expected_gerrit_cmd_1)
        )
        self.assertEqual(
            mock_exec_cmd.call_args_list[1], mock.call(expected_gerrit_cmd_2)
        )


if __name__ == "__main__":
    unittest.main()
