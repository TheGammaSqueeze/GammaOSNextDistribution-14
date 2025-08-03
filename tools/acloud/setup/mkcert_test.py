# Copyright 2022 - The Android Open Source Project
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
"""Tests for mkcert."""
import filecmp
import os
import shutil
import unittest

from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import utils
from acloud.setup import mkcert


class MkcertTest(driver_test_lib.BaseDriverTest):
    """Test Mkcert."""

    # pylint: disable=no-member
    def testInstall(self):
        """Test Install."""
        self.Patch(os.path, "isdir", return_value=False)
        self.Patch(os.path, "exists", return_value=False)
        self.Patch(os, "mkdir")
        self.Patch(mkcert, "IsRootCAReady")
        self.Patch(mkcert, "UnInstall")
        self.Patch(utils, "Popen")
        self.Patch(shutil, "rmtree")
        self.Patch(os, "stat")
        self.Patch(os, "chmod")
        os.stat().st_mode = 33188
        mkcert.Install()
        os.chmod.assert_not_called()
        shutil.rmtree.assert_not_called()
        mkcert.UnInstall.assert_not_called()
        self.assertEqual(4, utils.Popen.call_count)
        utils.Popen.reset_mock()

        self.Patch(os.path, "isdir", return_value=True)
        self.Patch(os.path, "exists", return_value=True)
        os.stat().st_mode = 33184
        mkcert.Install()
        os.chmod.assert_called_once()
        shutil.rmtree.assert_called_once()
        mkcert.UnInstall.assert_called_once()
        self.assertEqual(4, utils.Popen.call_count)


    def testAllocateLocalHostCert(self):
        """Test AllocateLocalHostCert."""
        self.Patch(mkcert, "IsRootCAReady", return_value=False)
        self.assertFalse(mkcert.AllocateLocalHostCert())

        self.Patch(mkcert, "IsRootCAReady", return_value=True)
        self.Patch(os.path, "exists", return_value=True)
        self.Patch(utils, "Popen")
        self.Patch(mkcert, "IsCertificateReady")
        mkcert.AllocateLocalHostCert()
        self.assertEqual(0, utils.Popen.call_count)

        self.Patch(os.path, "exists", return_value=False)
        mkcert.AllocateLocalHostCert()
        self.assertEqual(3, utils.Popen.call_count)


    def testIsRootCAReady(self):
        """Test IsRootCAReady."""
        self.Patch(os.path, "exists", return_value=True)
        self.Patch(filecmp, "cmp", return_value=True)
        self.assertTrue(mkcert.IsRootCAReady())

        self.Patch(filecmp, "cmp", return_value=False)
        self.assertFalse(mkcert.IsRootCAReady())

        self.Patch(os.path, "exists", return_value=False)
        self.assertFalse(mkcert.IsRootCAReady())


    def testIsCertificateReady(self):
        """Test IsCertificateReady."""
        self.Patch(os.path, "exists", return_value=False)
        self.assertFalse(mkcert.IsCertificateReady())

        self.Patch(os.path, "exists", return_value=True)
        self.assertTrue(mkcert.IsCertificateReady())


    def testUnInstall(self):
        """Test UnInstall."""
        self.Patch(utils, "Popen")
        mkcert.UnInstall()
        self.assertEqual(3, utils.Popen.call_count)


if __name__ == '__main__':
    unittest.main()
