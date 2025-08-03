# Copyright 2021 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Tests for host_cleanup_runner."""
import os
import platform
import unittest
import subprocess

from unittest import mock

from acloud.hostcleanup import host_cleanup_runner
from acloud.hostcleanup.host_cleanup_runner import PackagesUninstaller
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import utils
from acloud.setup import setup_common

# pylint: disable=no-member
class PackagesUninstallerTest(driver_test_lib.BaseDriverTest):
    """Test PackagesUninstallerTest."""

    # pylint: disable=invalid-name
    def setUp(self):
        """Set up the test."""
        super().setUp()
        mock_stty = mock.MagicMock()
        mock_stty.read.return_value = "20 80"
        self.Patch(os, "popen", return_value=mock_stty)

        self.Patch(setup_common, "PackageInstalled", return_value=True)
        self.PackagesUninstaller = PackagesUninstaller()

    def testShouldRun(self):
        """Test ShouldRun."""
        self.Patch(platform, "system", return_value="Linux")
        self.assertTrue(self.PackagesUninstaller.ShouldRun())

        self.Patch(platform, "system", return_value="Mac")
        self.assertFalse(self.PackagesUninstaller.ShouldRun())

        self.Patch(platform, "system", return_value="Linux")
        self.Patch(setup_common, "PackageInstalled", return_value=False)
        self.PackagesUninstaller = PackagesUninstaller()
        self.assertFalse(self.PackagesUninstaller.ShouldRun())

    def testRun(self):
        """Test Run."""
        self.Patch(utils, "InteractWithQuestion", return_value="y")
        self.Patch(PackagesUninstaller, "ShouldRun", return_value=True)
        self.Patch(setup_common, "CheckCmdOutput")
        self.PackagesUninstaller.Run()
        setup_common.CheckCmdOutput.assert_called()
        setup_common.CheckCmdOutput.reset_mock()

        self.Patch(utils, "InteractWithQuestion", return_value="n")
        self.PackagesUninstaller.Run()
        setup_common.CheckCmdOutput.assert_not_called()

    def testRun_RaiseException(self):
        """Test raise exception."""
        self.Patch(utils, "InteractWithQuestion", return_value="y")
        self.Patch(PackagesUninstaller, "ShouldRun", return_value=True)
        self.Patch(subprocess, "check_output",
                   side_effect=subprocess.CalledProcessError(
                       None, "raise err."))
        self.Patch(host_cleanup_runner.logger, "error")
        self.PackagesUninstaller.Run()
        host_cleanup_runner.logger.error.assert_called()


if __name__ == "__main__":
    unittest.main()
