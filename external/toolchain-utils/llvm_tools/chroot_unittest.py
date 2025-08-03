#!/usr/bin/env python3
# Copyright 2020 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit tests for chroot helper functions."""

import subprocess
import unittest
from unittest import mock

import chroot


# These are unittests; protected access is OK to a point.
# pylint: disable=protected-access


class HelperFunctionsTest(unittest.TestCase):
    """Test class for updating LLVM hashes of packages."""

    @mock.patch.object(subprocess, "check_output")
    def testSucceedsToGetChrootEbuildPathForPackage(self, mock_chroot_command):
        package_chroot_path = "/chroot/path/to/package.ebuild"

        # Emulate ChrootRunCommandWOutput behavior when a chroot path is found
        # for a valid package.
        mock_chroot_command.return_value = package_chroot_path

        chroot_path = "/test/chroot/path"
        package_list = ["new-test/package"]

        self.assertEqual(
            chroot.GetChrootEbuildPaths(chroot_path, package_list),
            [package_chroot_path],
        )

        mock_chroot_command.assert_called_once()

    def testFailedToConvertChrootPathWithInvalidPrefix(self):
        chroot_path = "/path/to/chroot"
        chroot_file_path = "/src/package.ebuild"

        # Verify the exception is raised when a chroot path does not have the
        # prefix '/mnt/host/source/'.
        with self.assertRaises(ValueError) as err:
            chroot.ConvertChrootPathsToAbsolutePaths(
                chroot_path, [chroot_file_path]
            )

        self.assertEqual(
            str(err.exception),
            "Invalid prefix for the chroot path: " "%s" % chroot_file_path,
        )

    def testSucceedsToConvertChrootPathToAbsolutePath(self):
        chroot_path = "/path/to/chroot"
        chroot_file_paths = ["/mnt/host/source/src/package.ebuild"]

        expected_abs_path = "/path/to/chroot/src/package.ebuild"

        self.assertEqual(
            chroot.ConvertChrootPathsToAbsolutePaths(
                chroot_path, chroot_file_paths
            ),
            [expected_abs_path],
        )


if __name__ == "__main__":
    unittest.main()
