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
"""Tests for create_common."""

import collections
import os
import shutil
import tempfile
import unittest

from unittest import mock

from acloud import errors
from acloud.create import create_common
from acloud.internal.lib import android_build_client
from acloud.internal.lib import auth
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import utils


ExtraFile = collections.namedtuple("ExtraFile", ["source", "target"])


class FakeZipFile:
    """Fake implementation of ZipFile()"""

    # pylint: disable=invalid-name,unused-argument,no-self-use
    def write(self, filename, arcname=None, compress_type=None):
        """Fake write method."""
        return

    # pylint: disable=invalid-name,no-self-use
    def close(self):
        """Fake close method."""
        return


# pylint: disable=invalid-name,protected-access
class CreateCommonTest(driver_test_lib.BaseDriverTest):
    """Test create_common functions."""

    # pylint: disable=protected-access
    def testProcessHWPropertyWithInvalidArgs(self):
        """Test ParseKeyValuePairArgs with invalid args."""
        # Checking wrong property value.
        args_str = "cpu:3,disk:"
        with self.assertRaises(errors.MalformedDictStringError):
            create_common.ParseKeyValuePairArgs(args_str)

        # Checking wrong property format.
        args_str = "cpu:3,disk"
        with self.assertRaises(errors.MalformedDictStringError):
            create_common.ParseKeyValuePairArgs(args_str)

    def testParseHWPropertyStr(self):
        """Test ParseKeyValuePairArgs."""
        expected_dict = {"cpu": "2", "resolution": "1080x1920", "dpi": "240",
                         "memory": "4g", "disk": "4g"}
        args_str = "cpu:2,resolution:1080x1920,dpi:240,memory:4g,disk:4g"
        result_dict = create_common.ParseKeyValuePairArgs(args_str)
        self.assertTrue(expected_dict == result_dict)

    def testGetNonEmptyEnvVars(self):
        """Test GetNonEmptyEnvVars."""
        with mock.patch.dict("acloud.internal.lib.utils.os.environ",
                             {"A": "", "B": "b"},
                             clear=True):
            self.assertEqual(
                ["b"], create_common.GetNonEmptyEnvVars("A", "B", "C"))

    def testParseExtraFilesArgs(self):
        """Test ParseExtraFilesArgs."""
        expected_result = [ExtraFile(source="local_path", target="gce_path")]
        files_info = ["local_path,gce_path"]
        self.assertEqual(expected_result,
                         create_common.ParseExtraFilesArgs(files_info))

        # Test multiple files
        expected_result = [ExtraFile(source="local_path1", target="gce_path1"),
                           ExtraFile(source="local_path2", target="gce_path2")]
        files_info = ["local_path1,gce_path1",
                      "local_path2,gce_path2"]
        self.assertEqual(expected_result,
                         create_common.ParseExtraFilesArgs(files_info))

        # Test wrong file info format.
        files_info = ["local_path"]
        with self.assertRaises(errors.MalformedDictStringError):
            create_common.ParseExtraFilesArgs(files_info)

    def testGetCvdHostPackage(self):
        """test GetCvdHostPackage."""
        # Can't find the cvd host package
        with mock.patch("os.path.exists") as exists:
            exists.return_value = False
            self.assertRaises(
                errors.GetCvdLocalHostPackageError,
                create_common.GetCvdHostPackage)

        self.Patch(os.environ, "get", return_value="/fake_dir2")
        self.Patch(utils, "GetDistDir", return_value="/fake_dir1")
        self.Patch(os.path, "exists",
                   side_effect=lambda path:
                       path == "/fake_dir1/cvd-host_package.tar.gz")

        # Find cvd host in dist dir.
        self.assertEqual(
            create_common.GetCvdHostPackage(),
            "/fake_dir1/cvd-host_package.tar.gz")

        # Find cvd host in host out dir.
        self.Patch(os.environ, "get", return_value="/fake_dir2")
        self.Patch(utils, "GetDistDir", return_value=None)
        with mock.patch("os.path.exists") as exists:
            exists.side_effect = lambda path: \
                path == "/fake_dir2/cvd-host_package.tar.gz"
            self.assertEqual(
                create_common.GetCvdHostPackage(),
                "/fake_dir2/cvd-host_package.tar.gz")
        with mock.patch("os.path.exists") as exists:
            exists.side_effect = lambda path: \
                path == "/fake_dir2/cvd-host_package"
            self.assertEqual(
                create_common.GetCvdHostPackage(),
                "/fake_dir2/cvd-host_package")

        # Find cvd host in specified path.
        package_path = "/tool_dir/cvd-host_package.tar.gz"
        self.Patch(utils, "GetDistDir", return_value=None)
        with mock.patch("os.path.exists") as exists:
            exists.return_value = True
            self.assertEqual(
                create_common.GetCvdHostPackage(package_path),
                "/tool_dir/cvd-host_package.tar.gz")

    @mock.patch("acloud.create.create_common.os.path.isfile",
                side_effect=lambda path: path == "/dir/name")
    @mock.patch("acloud.create.create_common.os.path.isdir",
                side_effect=lambda path: path == "/dir")
    @mock.patch("acloud.create.create_common.os.listdir",
                return_value=["name", "name2"])
    def testFindLocalImage(self, _mock_listdir, _mock_isdir, _mock_isfile):
        """Test FindLocalImage."""
        self.assertEqual(
            "/dir/name",
            create_common.FindLocalImage("/test/../dir/name", "not_exist"))

        self.assertEqual("/dir/name",
                         create_common.FindLocalImage("/dir/", "name"))


        self.assertIsNone(create_common.FindLocalImage("/dir", "not_exist",
                                                       raise_error=False))
        with self.assertRaises(errors.GetLocalImageError):
            create_common.FindLocalImage("/dir", "not_exist")

        with self.assertRaises(errors.GetLocalImageError):
            create_common.FindLocalImage("/dir", "name.?", raise_error=False)

    def testFindBootImage(self):
        """Test FindBootImage."""
        with tempfile.TemporaryDirectory() as temp_dir:
            with self.assertRaises(errors.GetLocalImageError):
                create_common.FindBootImage(temp_dir)

            boot_image_path = os.path.join(temp_dir, "boot.img")
            self.CreateFile(boot_image_path, b"invalid")
            with self.assertRaises(errors.GetLocalImageError):
                create_common.FindBootImage(temp_dir)
            os.remove(boot_image_path)

            boot_image_path = os.path.join(temp_dir, "boot.img")
            self.CreateFile(boot_image_path, b"ANDROID!")
            self.assertEqual(boot_image_path,
                             create_common.FindBootImage(temp_dir))
            os.remove(boot_image_path)

            boot_image_path = os.path.join(temp_dir, "boot-5.10.img")
            self.CreateFile(boot_image_path, b"ANDROID!")
            self.assertEqual(boot_image_path,
                             create_common.FindBootImage(temp_dir))

    def testFindSystemImages(self):
        """Test FindSystemImages."""
        with tempfile.TemporaryDirectory() as temp_dir:
            with self.assertRaises(errors.GetLocalImageError):
                create_common.FindSystemImages(temp_dir)

            image_dir = os.path.join(temp_dir, "IMAGES")
            system_image_path = os.path.join(image_dir, "system.img")
            self.CreateFile(system_image_path)
            self.assertEqual((system_image_path, None, None),
                             create_common.FindSystemImages(temp_dir))
            self.assertEqual((system_image_path, None, None),
                             create_common.FindSystemImages(image_dir))
            self.assertEqual((system_image_path, None, None),
                             create_common.FindSystemImages(system_image_path))

            system_ext_image_path = os.path.join(image_dir, "system_ext.img")
            self.CreateFile(system_ext_image_path)
            product_image_path = os.path.join(image_dir, "product.img")
            self.CreateFile(product_image_path)
            self.assertEqual(
                (system_image_path, system_ext_image_path, product_image_path),
                create_common.FindSystemImages(temp_dir))
            self.assertEqual(
                (system_image_path, system_ext_image_path, product_image_path),
                create_common.FindSystemImages(image_dir))

    @mock.patch.object(utils, "Decompress")
    def testDownloadRemoteArtifact(self, mock_decompress):
        """Test Download cuttlefish package."""
        mock_build_client = mock.MagicMock()
        self.Patch(
            android_build_client,
            "AndroidBuildClient",
            return_value=mock_build_client)
        self.Patch(auth, "CreateCredentials", return_value=mock.MagicMock())
        avd_spec = mock.MagicMock()
        avd_spec.cfg = mock.MagicMock()
        avd_spec.remote_image = {"build_target" : "aosp_cf_x86_64_phone-userdebug",
                                 "build_id": "1234"}
        build_id = "1234"
        build_target = "aosp_cf_x86_64_phone-userdebug"
        checkfile1 = "aosp_cf_x86_phone-img-1234.zip"
        checkfile2 = "cvd-host_package.tar.gz"
        extract_path = "/tmp/1234"

        create_common.DownloadRemoteArtifact(
            avd_spec.cfg,
            avd_spec.remote_image["build_target"],
            avd_spec.remote_image["build_id"],
            checkfile1,
            extract_path,
            decompress=True)

        self.assertEqual(mock_build_client.DownloadArtifact.call_count, 1)
        mock_build_client.DownloadArtifact.assert_called_once_with(
            build_target,
            build_id,
            checkfile1,
            "%s/%s" % (extract_path, checkfile1))
        self.assertEqual(mock_decompress.call_count, 1)

        mock_decompress.call_count = 0
        mock_build_client.DownloadArtifact.call_count = 0
        create_common.DownloadRemoteArtifact(
            avd_spec.cfg,
            avd_spec.remote_image["build_target"],
            avd_spec.remote_image["build_id"],
            checkfile2,
            extract_path)

        self.assertEqual(mock_build_client.DownloadArtifact.call_count, 1)
        mock_build_client.DownloadArtifact.assert_called_once_with(
            build_target,
            build_id,
            checkfile2,
            "%s/%s" % (extract_path, checkfile2))
        self.assertEqual(mock_decompress.call_count, 0)

    def testPrepareLocalInstanceDir(self):
        """test PrepareLocalInstanceDir."""
        temp_dir = tempfile.mkdtemp()
        try:
            cvd_home_dir = os.path.join(temp_dir, "local-instance-1")
            mock_avd_spec = mock.Mock(local_instance_dir=None)
            create_common.PrepareLocalInstanceDir(cvd_home_dir, mock_avd_spec)
            self.assertTrue(os.path.isdir(cvd_home_dir) and
                            not os.path.islink(cvd_home_dir))

            link_target_dir = os.path.join(temp_dir, "cvd_home")
            os.mkdir(link_target_dir)
            mock_avd_spec.local_instance_dir = link_target_dir
            create_common.PrepareLocalInstanceDir(cvd_home_dir, mock_avd_spec)
            self.assertTrue(os.path.islink(cvd_home_dir) and
                            os.path.samefile(cvd_home_dir, link_target_dir))
        finally:
            shutil.rmtree(temp_dir)


if __name__ == "__main__":
    unittest.main()
