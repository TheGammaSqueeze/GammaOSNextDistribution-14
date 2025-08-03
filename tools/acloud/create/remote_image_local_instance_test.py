# Copyright 2018 - The Android Open Source Project
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
"""Tests for remote_image_local_instance."""

import builtins
from collections import namedtuple
import os
import shutil
import subprocess
import unittest

from unittest import mock

from acloud import errors
from acloud.create import create_common
from acloud.create import remote_image_local_instance
from acloud.internal import constants
from acloud.internal.lib import android_build_client
from acloud.internal.lib import auth
from acloud.internal.lib import cvd_utils
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import ota_tools
from acloud.internal.lib import utils
from acloud.setup import setup_common


# pylint: disable=invalid-name, protected-access, no-member
class RemoteImageLocalInstanceTest(driver_test_lib.BaseDriverTest):
    """Test remote_image_local_instance methods."""

    def setUp(self):
        """Initialize remote_image_local_instance."""
        super().setUp()
        self.build_client = mock.MagicMock()
        self.Patch(
            android_build_client,
            "AndroidBuildClient",
            return_value=self.build_client)
        self.Patch(auth, "CreateCredentials", return_value=mock.MagicMock())
        self.RemoteImageLocalInstance = remote_image_local_instance.RemoteImageLocalInstance()
        self._fake_remote_image = {"build_target" : "aosp_cf_x86_64_phone-userdebug",
                                   "build_id": "1234",
                                   "branch": "aosp_master"}
        self._extract_path = "/tmp/acloud_image_artifacts/1234"

    @mock.patch.object(remote_image_local_instance, "DownloadAndProcessImageFiles")
    def testGetImageArtifactsPath(self, mock_proc):
        """Test get image artifacts path."""
        mock_proc.return_value = "/unit/test"
        avd_spec = mock.MagicMock()
        avd_spec.local_system_image = None
        avd_spec.local_kernel_image = None
        avd_spec.local_vendor_image = None
        # raise errors.NoCuttlefishCommonInstalled
        self.Patch(setup_common, "PackageInstalled", return_value=False)
        self.assertRaises(errors.NoCuttlefishCommonInstalled,
                          self.RemoteImageLocalInstance.GetImageArtifactsPath,
                          avd_spec)

        # Valid _DownloadAndProcessImageFiles run.
        mock_launch_cvd = os.path.join("/unit/test", "bin", "launch_cvd")
        self.Patch(setup_common, "PackageInstalled", return_value=True)
        self.Patch(remote_image_local_instance,
                   "ConfirmDownloadRemoteImageDir", return_value="/tmp")
        mock_exists = self.Patch(
            os.path, "exists", side_effect=lambda p: p == mock_launch_cvd)
        paths = self.RemoteImageLocalInstance.GetImageArtifactsPath(avd_spec)
        mock_proc.assert_called_once_with(avd_spec)
        self.assertEqual(paths.image_dir, "/unit/test")
        self.assertEqual(paths.host_bins, "/unit/test")

        # GSI
        avd_spec.local_system_image = "/test_local_system_image_dir"
        avd_spec.local_tool_dirs = "/test_local_tool_dirs"
        avd_spec.cfg = None
        avd_spec.remote_image = self._fake_remote_image
        self.Patch(os, "makedirs")
        self.Patch(create_common, "DownloadRemoteArtifact")
        self.Patch(create_common, "GetNonEmptyEnvVars")
        self.Patch(cvd_utils, "FindMiscInfo",
                   return_value="/mix_image_1234/MISC")
        self.Patch(cvd_utils, "FindImageDir",
                   return_value="/mix_image_1234/IMAGES")
        self.Patch(ota_tools, "FindOtaToolsDir", return_value="/ota_tools_dir")
        self.Patch(create_common, "FindSystemImages",
                   return_value=("/system_image_path",
                                 "/system_ext_image_path",
                                 "/product_image_path"))
        self.Patch(self.RemoteImageLocalInstance, "_FindCvdHostBinaries",
                   side_effect=errors.GetCvdLocalHostPackageError("not found"))
        paths = self.RemoteImageLocalInstance.GetImageArtifactsPath(avd_spec)
        create_common.DownloadRemoteArtifact.assert_called_with(
            avd_spec.cfg, "aosp_cf_x86_64_phone-userdebug", "1234",
            "aosp_cf_x86_64_phone-target_files-1234.zip", "/unit/test/mix_image_1234",
            decompress=True)
        self.assertEqual(paths.image_dir, "/mix_image_1234/IMAGES")
        self.assertEqual(paths.misc_info, "/mix_image_1234/MISC")
        self.assertEqual(paths.host_bins, "/unit/test")
        self.assertEqual(paths.ota_tools_dir, "/ota_tools_dir")
        self.assertEqual(paths.system_image, "/system_image_path")
        self.assertEqual(paths.system_ext_image, "/system_ext_image_path")
        self.assertEqual(paths.product_image, "/product_image_path")
        self.RemoteImageLocalInstance._FindCvdHostBinaries.assert_not_called()

        # Boot and kernel images
        avd_spec.local_kernel_image = "/test_local_kernel_image_dir"
        self.Patch(self.RemoteImageLocalInstance, "FindBootOrKernelImages",
                   return_value=("/boot_image_path", "/vendor_boot_image_path",
                                 None, None))
        paths = self.RemoteImageLocalInstance.GetImageArtifactsPath(avd_spec)
        self.RemoteImageLocalInstance.FindBootOrKernelImages.assert_called()
        self.assertEqual(paths.boot_image, "/boot_image_path")
        self.assertEqual(paths.vendor_boot_image, "/vendor_boot_image_path")
        self.assertIsNone(paths.kernel_image)
        self.assertIsNone(paths.initramfs_image)

        # local vendor image, local tool including host bins
        avd_spec.local_vendor_image = "/test_local_vendor_image_dir"
        vendor_image_paths = cvd_utils.VendorImagePaths(
            "vendor.img", "vendor_dlkm.img", "odm.img", "odm_dlkm.img")
        self.Patch(cvd_utils, "FindVendorImages",
                   return_value=vendor_image_paths)
        self.Patch(self.RemoteImageLocalInstance, "_FindCvdHostBinaries",
                   return_value="/test_local_tool_dirs")
        paths = self.RemoteImageLocalInstance.GetImageArtifactsPath(avd_spec)
        self.assertEqual(paths.host_bins, "/test_local_tool_dirs")

        # local vendor image, local tool without host bins
        avd_spec.local_vendor_image = "/test_local_vendor_image_dir"
        self.Patch(self.RemoteImageLocalInstance, "_FindCvdHostBinaries",
                   side_effect=errors.GetCvdLocalHostPackageError("not found"))
        paths = self.RemoteImageLocalInstance.GetImageArtifactsPath(avd_spec)
        self.assertEqual(paths.host_bins, "/unit/test")

        create_common.DownloadRemoteArtifact.reset_mock()

        mock_exists.side_effect = lambda p: p in (
            mock_launch_cvd, os.path.join("/unit/test", "mix_image_1234"))
        self.RemoteImageLocalInstance.GetImageArtifactsPath(avd_spec)
        create_common.DownloadRemoteArtifact.assert_not_called()

    @mock.patch.object(shutil, "rmtree")
    def testDownloadAndProcessImageFiles(self, mock_rmtree):
        """Test process remote cuttlefish image."""
        avd_spec = mock.MagicMock()
        avd_spec.cfg = mock.MagicMock()
        avd_spec.cfg.creds_cache_file = "cache.file"
        avd_spec.remote_image = self._fake_remote_image
        avd_spec.image_download_dir = "/tmp"
        avd_spec.force_sync = True
        self.Patch(os.path, "exists", side_effect=[True, False])
        self.Patch(os, "makedirs")
        self.Patch(subprocess, "check_call")
        mock_open = self.Patch(builtins, "open")
        fetch_dir = remote_image_local_instance.DownloadAndProcessImageFiles(
            avd_spec)
        self.assertEqual(mock_rmtree.call_count, 1)
        self.assertEqual(self.build_client.GetFetchBuildArgs.call_count, 1)
        self.assertEqual(self.build_client.GetFetchCertArg.call_count, 1)
        cvd_config_filename = os.path.join(fetch_dir,
                                           constants.FETCH_CVD_ARGS_FILE)
        mock_open.assert_called_once_with(cvd_config_filename, "w")

    def testConfirmDownloadRemoteImageDir(self):
        """Test confirm download remote image dir"""
        self.Patch(os.path, "exists", return_value=True)
        self.Patch(os, "makedirs")
        # Default minimum avail space should be more than 10G
        # then return download_dir directly.
        self.Patch(os, "statvfs", return_value=namedtuple(
            "statvfs", "f_bavail, f_bsize")(11, 1073741824))
        download_dir = "/tmp"
        self.assertEqual(
            remote_image_local_instance.ConfirmDownloadRemoteImageDir(
                download_dir), "/tmp")

        # Test when insuficient disk space and input 'q' to exit.
        self.Patch(os, "statvfs", return_value=namedtuple(
            "statvfs", "f_bavail, f_bsize")(9, 1073741824))
        self.Patch(utils, "InteractWithQuestion", return_value="q")
        self.assertRaises(SystemExit,
                          remote_image_local_instance.ConfirmDownloadRemoteImageDir,
                          download_dir)

        # If avail space detect as 9GB, and 2nd input 7GB both less than 10GB
        # 3rd input over 10GB, so return path should be "/tmp3".
        self.Patch(os, "statvfs", side_effect=[
            namedtuple("statvfs", "f_bavail, f_bsize")(9, 1073741824),
            namedtuple("statvfs", "f_bavail, f_bsize")(7, 1073741824),
            namedtuple("statvfs", "f_bavail, f_bsize")(11, 1073741824)])
        self.Patch(utils, "InteractWithQuestion", side_effect=["/tmp2",
                                                               "/tmp3"])
        self.assertEqual(
            remote_image_local_instance.ConfirmDownloadRemoteImageDir(
                download_dir), "/tmp3")

        # Test when path not exist, define --image-download-dir
        # enter anything else to exit out.
        download_dir = "/image_download_dir1"
        self.Patch(os.path, "exists", return_value=False)
        self.Patch(utils, "InteractWithQuestion", return_value="")
        self.assertRaises(SystemExit,
                          remote_image_local_instance.ConfirmDownloadRemoteImageDir,
                          download_dir)

        # Test using --image-dowload-dir and makedirs.
        # enter 'y' to create it.
        self.Patch(utils, "InteractWithQuestion", return_value="y")
        self.Patch(os, "statvfs", return_value=namedtuple(
            "statvfs", "f_bavail, f_bsize")(10, 1073741824))
        self.assertEqual(
            remote_image_local_instance.ConfirmDownloadRemoteImageDir(
                download_dir), "/image_download_dir1")

        # Test when 1st check fails for insufficient disk space, user inputs an
        # alternate dir but it doesn't exist and the user choose to exit.
        self.Patch(os, "statvfs", side_effect=[
            namedtuple("statvfs", "f_bavail, f_bsize")(9, 1073741824),
            namedtuple("statvfs", "f_bavail, f_bsize")(11, 1073741824)])
        self.Patch(os.path, "exists", side_effect=[True, False])
        self.Patch(utils, "InteractWithQuestion",
                   side_effect=["~/nopath", "not_y"])
        self.assertRaises(
            SystemExit,
            remote_image_local_instance.ConfirmDownloadRemoteImageDir,
            download_dir)

        # Test when 1st check fails for insufficient disk space, user inputs an
        # alternate dir but it doesn't exist and they request to create it.
        self.Patch(os, "statvfs", side_effect=[
            namedtuple("statvfs", "f_bavail, f_bsize")(9, 1073741824),
            namedtuple("statvfs", "f_bavail, f_bsize")(10, 1073741824)])
        self.Patch(os.path, "exists", side_effect=[True, False])
        self.Patch(utils, "InteractWithQuestion", side_effect=["~/nopath", "y"])
        self.assertEqual(
            remote_image_local_instance.ConfirmDownloadRemoteImageDir(
                download_dir), os.path.expanduser("~/nopath"))


if __name__ == "__main__":
    unittest.main()
