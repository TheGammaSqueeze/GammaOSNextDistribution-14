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
"""Tests for hostcleanup."""
import unittest

from unittest import mock

from acloud.internal.lib import driver_test_lib
from acloud.hostcleanup import hostcleanup
from acloud.hostcleanup import host_cleanup_runner


class HostcleanupTest(driver_test_lib.BaseDriverTest):
    """Test hostcleanup."""

    # pylint: disable=no-self-use
    @mock.patch.object(host_cleanup_runner, "PackagesUninstaller")
    def testRun(self, mock_uninstallpkgs):
        """test Run."""
        args = mock.MagicMock()
        hostcleanup.Run(args)
        mock_uninstallpkgs.assert_called_once()


if __name__ == '__main__':
    unittest.main()
