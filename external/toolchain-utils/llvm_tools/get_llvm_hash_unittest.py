#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit tests for retrieving the LLVM hash."""

import contextlib
import subprocess
import unittest
from unittest import mock

import get_llvm_hash
import subprocess_helpers


# We grab protected stuff from get_llvm_hash. That's OK.
# pylint: disable=protected-access


def mock_run_results(returncode: int, stderr: bytes) -> mock.MagicMock:
    m = mock.MagicMock()
    m.returncode = returncode
    m.stderr = stderr
    return m


class TestGetLLVMHash(unittest.TestCase):
    """The LLVMHash test class."""

    @mock.patch.object(subprocess, "run")
    def testCloneRepoSucceedsWhenGitSucceeds(self, run_mock):
        run_mock.return_value = mock_run_results(returncode=0, stderr=b"")
        llvm_hash = get_llvm_hash.LLVMHash()

        into_tempdir = "/tmp/tmpTest"
        llvm_hash.CloneLLVMRepo(into_tempdir)
        run_mock.assert_called_with(
            ["git", "clone", get_llvm_hash._LLVM_GIT_URL, into_tempdir],
            check=False,
            stderr=subprocess.PIPE,
        )

    @mock.patch.object(subprocess, "run")
    def testCloneRepoFailsWhenGitFails(self, run_mock):
        run_mock.return_value = mock_run_results(
            returncode=1, stderr=b"some stderr"
        )

        with self.assertRaisesRegex(ValueError, "Failed to clone.*some stderr"):
            get_llvm_hash.LLVMHash().CloneLLVMRepo("/tmp/tmp1")

    @mock.patch.object(get_llvm_hash, "GetGitHashFrom")
    def testGetGitHashWorks(self, mock_get_git_hash):
        mock_get_git_hash.return_value = "a13testhash2"

        self.assertEqual(
            get_llvm_hash.GetGitHashFrom("/tmp/tmpTest", 100), "a13testhash2"
        )

        mock_get_git_hash.assert_called_once()

    @mock.patch.object(get_llvm_hash.LLVMHash, "GetLLVMHash")
    @mock.patch.object(get_llvm_hash, "GetGoogle3LLVMVersion")
    def testReturnGoogle3LLVMHash(
        self, mock_google3_llvm_version, mock_get_llvm_hash
    ):
        mock_get_llvm_hash.return_value = "a13testhash3"
        mock_google3_llvm_version.return_value = 1000
        self.assertEqual(
            get_llvm_hash.LLVMHash().GetGoogle3LLVMHash(), "a13testhash3"
        )
        mock_get_llvm_hash.assert_called_once_with(1000)

    @mock.patch.object(get_llvm_hash.LLVMHash, "GetLLVMHash")
    @mock.patch.object(get_llvm_hash, "GetGoogle3LLVMVersion")
    def testReturnGoogle3UnstableLLVMHash(
        self, mock_google3_llvm_version, mock_get_llvm_hash
    ):
        mock_get_llvm_hash.return_value = "a13testhash3"
        mock_google3_llvm_version.return_value = 1000
        self.assertEqual(
            get_llvm_hash.LLVMHash().GetGoogle3UnstableLLVMHash(),
            "a13testhash3",
        )
        mock_get_llvm_hash.assert_called_once_with(1000)

    @mock.patch.object(subprocess, "check_output")
    def testSuccessfullyGetGitHashFromToTOfLLVM(self, mock_check_output):
        mock_check_output.return_value = "a123testhash1 path/to/main\n"
        self.assertEqual(
            get_llvm_hash.LLVMHash().GetTopOfTrunkGitHash(), "a123testhash1"
        )
        mock_check_output.assert_called_once()

    @mock.patch.object(subprocess, "Popen")
    def testCheckoutBranch(self, mock_popen):
        mock_popen.return_value = contextlib.nullcontext(
            mock.MagicMock(communicate=lambda: (None, None), returncode=0)
        )
        get_llvm_hash.CheckoutBranch("fake/src_dir", "fake_branch")
        self.assertEqual(
            mock_popen.call_args_list[0][0],
            (["git", "-C", "fake/src_dir", "checkout", "fake_branch"],),
        )
        self.assertEqual(
            mock_popen.call_args_list[1][0],
            (["git", "-C", "fake/src_dir", "pull"],),
        )

    def testParseLLVMMajorVersion(self):
        cmakelist_42 = (
            "set(CMAKE_BUILD_WITH_INSTALL_NAME_DIR ON)\n"
            "if(NOT DEFINED LLVM_VERSION_MAJOR)\n"
            "  set(LLVM_VERSION_MAJOR 42)\n"
            "endif()"
        )
        self.assertEqual(
            get_llvm_hash.ParseLLVMMajorVersion(cmakelist_42), "42"
        )

    def testParseLLVMMajorVersionInvalid(self):
        invalid_cmakelist = "invalid cmakelist.txt contents"
        with self.assertRaises(ValueError):
            get_llvm_hash.ParseLLVMMajorVersion(invalid_cmakelist)

    @mock.patch.object(get_llvm_hash, "GetAndUpdateLLVMProjectInLLVMTools")
    @mock.patch.object(get_llvm_hash, "ParseLLVMMajorVersion")
    @mock.patch.object(subprocess_helpers, "CheckCommand")
    @mock.patch.object(get_llvm_hash, "CheckoutBranch")
    @mock.patch(
        "get_llvm_hash.open",
        mock.mock_open(read_data="mock contents"),
        create=True,
    )
    def testGetLLVMMajorVersion(
        self,
        mock_checkout_branch,
        mock_git_checkout,
        mock_major_version,
        mock_llvm_project_path,
    ):
        mock_llvm_project_path.return_value = "path/to/llvm-project"
        mock_major_version.return_value = "1234"
        self.assertEqual(get_llvm_hash.GetLLVMMajorVersion("314159265"), "1234")
        # Second call should be memoized
        self.assertEqual(get_llvm_hash.GetLLVMMajorVersion("314159265"), "1234")
        mock_llvm_project_path.assert_called_once()
        mock_major_version.assert_called_with("mock contents")
        mock_git_checkout.assert_called_once_with(
            ["git", "-C", "path/to/llvm-project", "checkout", "314159265"]
        )
        mock_checkout_branch.assert_called_once_with(
            "path/to/llvm-project", "main"
        )


if __name__ == "__main__":
    unittest.main()
