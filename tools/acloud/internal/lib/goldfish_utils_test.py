#!/usr/bin/env python3
#
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

"""Unit tests for goldfish_utils."""

import os
import shutil
import tempfile
import unittest

from unittest import mock

from acloud import errors
from acloud.internal.lib import goldfish_utils


class GoldfishUtilsTest(unittest.TestCase):
    """Test functions in goldfish_utils."""

    # Remote host instance name.
    _IP_ADDRESS = "192.0.2.1"
    _CONSOLE_PORT = 5554
    _BUILD_INFO = {"build_id": "123456",
                   "build_target": "sdk_phone_x86_64-userdebug"}
    _INSTANCE_NAME = ("host-goldfish-192.0.2.1-5554-"
                      "123456-sdk_phone_x86_64-userdebug")
    _INSTANCE_NAME_WITHOUT_INFO = "host-goldfish-192.0.2.1-5554-userbuild"
    _INVALID_NAME = "host-192.0.2.1-123456-aosp_cf_x86_phone-userdebug"

    @staticmethod
    def _CreateEmptyFile(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w"):
            pass

    def setUp(self):
        """Create the temporary directory."""
        self._temp_dir = tempfile.mkdtemp("goldfish_utils_test")

    def tearDown(self):
        """Delete the temporary directory."""
        shutil.rmtree(self._temp_dir)

    def testMixWithBootImage(self):
        """Test MixWithBootImage."""
        boot_image_path = os.path.join(self._temp_dir, "boot.img")
        image_dir = os.path.join(self._temp_dir, "image_dir")
        self._CreateEmptyFile(boot_image_path)
        os.makedirs(image_dir)
        with open(os.path.join(image_dir, "ramdisk-qemu.img"), "w") as ramdisk:
            ramdisk.write("original")
        mix_dir = os.path.join(self._temp_dir, "mix_kernel")
        unpack_dir = os.path.join(mix_dir, "unpacked_boot_img")

        def _MockUnpackBootImg(out_dir, boot_img):
            self.assertEqual(unpack_dir, out_dir)
            self.assertEqual(boot_image_path, boot_img)
            self._CreateEmptyFile(os.path.join(out_dir, "kernel"))
            with open(os.path.join(out_dir, "ramdisk"), "w") as ramdisk:
                ramdisk.write("boot")

        mock_ota = mock.Mock()
        mock_ota.UnpackBootImg.side_effect = _MockUnpackBootImg

        kernel_path, ramdisk_path = goldfish_utils.MixWithBootImage(
            mix_dir, image_dir, boot_image_path, mock_ota)

        mock_ota.UnpackBootImg.assert_called_with(unpack_dir, boot_image_path)
        self.assertEqual(os.path.join(unpack_dir, "kernel"), kernel_path)
        self.assertEqual(os.path.join(mix_dir, "mixed_ramdisk"), ramdisk_path)
        with open(ramdisk_path, "r") as ramdisk:
            self.assertEqual("originalboot", ramdisk.read())

    def testFindKernelImage(self):
        """Test FindKernelImage."""
        with self.assertRaises(errors.GetLocalImageError):
            goldfish_utils.FindKernelImages(self._temp_dir)

        kernel_path = os.path.join(self._temp_dir, "kernel")
        ramdisk_path = os.path.join(self._temp_dir, "ramdisk.img")
        self._CreateEmptyFile(kernel_path)
        self._CreateEmptyFile(ramdisk_path)
        self.assertEqual((kernel_path, ramdisk_path),
                         goldfish_utils.FindKernelImages(self._temp_dir))

        kernel_path = os.path.join(self._temp_dir, "kernel-ranchu")
        ramdisk_path = os.path.join(self._temp_dir, "ramdisk-qemu.img")
        self._CreateEmptyFile(kernel_path)
        self._CreateEmptyFile(ramdisk_path)
        self.assertEqual((kernel_path, ramdisk_path),
                         goldfish_utils.FindKernelImages(self._temp_dir))

    def testFindSystemDlkmImage(self):
        """Test FindSystemDlkmImage."""
        system_dlkm_image_path = os.path.join(self._temp_dir, "test.img")
        self._CreateEmptyFile(system_dlkm_image_path)
        self.assertEqual(
            system_dlkm_image_path,
            goldfish_utils.FindSystemDlkmImage(system_dlkm_image_path))

        with self.assertRaises(errors.GetLocalImageError):
            goldfish_utils.FindSystemDlkmImage(self._temp_dir)

        system_dlkm_image_path = os.path.join(self._temp_dir,
                                              "system_dlkm.img")
        self._CreateEmptyFile(system_dlkm_image_path)
        self.assertEqual(system_dlkm_image_path,
                         goldfish_utils.FindSystemDlkmImage(self._temp_dir))

        system_dlkm_image_path = os.path.join(self._temp_dir,
                                              "system_dlkm.flatten.ext4.img")
        self._CreateEmptyFile(system_dlkm_image_path)
        self.assertEqual(system_dlkm_image_path,
                         goldfish_utils.FindSystemDlkmImage(self._temp_dir))

    def testFindDiskImage(self):
        """Test FindDiskImage."""
        with self.assertRaises(errors.GetLocalImageError):
            goldfish_utils.FindDiskImage(self._temp_dir)

        disk_path = os.path.join(self._temp_dir, "system.img")
        self._CreateEmptyFile(disk_path)
        self.assertEqual(disk_path,
                         goldfish_utils.FindDiskImage(self._temp_dir))

        disk_path = os.path.join(self._temp_dir, "system-qemu.img")
        self._CreateEmptyFile(disk_path)
        self.assertEqual(disk_path,
                         goldfish_utils.FindDiskImage(self._temp_dir))

    def testMixDiskImage(self):
        """Test MixDiskImage."""
        mock_ota = mock.Mock()
        mix_dir = os.path.join(self._temp_dir, "mix_disk")
        image_dir = os.path.join(self._temp_dir, "image_dir")
        misc_info_path = os.path.join(image_dir, "misc_info.txt")
        qemu_config_path = os.path.join(image_dir, "system-qemu-config.txt")
        system_image_path = os.path.join(self._temp_dir, "system.img")
        system_dlkm_image_path = os.path.join(self._temp_dir,
                                              "system_dlkm.img")
        vendor_image_path = os.path.join(image_dir, "vendor.img")
        vbmeta_image_path = os.path.join(mix_dir, "disabled_vbmeta.img")
        super_image_path = os.path.join(mix_dir, "mixed_super.img")
        self._CreateEmptyFile(misc_info_path)
        self._CreateEmptyFile(qemu_config_path)

        disk_image = goldfish_utils.MixDiskImage(
            mix_dir, image_dir, system_image_path, system_dlkm_image_path,
            mock_ota)

        self.assertTrue(os.path.isdir(mix_dir))
        self.assertEqual(os.path.join(mix_dir, "mixed_disk.img"), disk_image)

        mock_ota.BuildSuperImage.assert_called_with(
            os.path.join(mix_dir, "mixed_super.img"), misc_info_path, mock.ANY)
        get_image = mock_ota.BuildSuperImage.call_args[0][2]
        self._CreateEmptyFile(vendor_image_path)
        self._CreateEmptyFile(system_image_path)
        self._CreateEmptyFile(system_dlkm_image_path)
        self.assertEqual(system_image_path, get_image("system"))
        self.assertEqual(system_dlkm_image_path, get_image("system_dlkm"))
        self.assertEqual(vendor_image_path, get_image("vendor"))

        mock_ota.MakeDisabledVbmetaImage.assert_called_with(vbmeta_image_path)

        mock_ota.MkCombinedImg.assert_called_with(
            disk_image, qemu_config_path, mock.ANY)
        get_image = mock_ota.MkCombinedImg.call_args[0][2]
        self._CreateEmptyFile(vbmeta_image_path)
        self._CreateEmptyFile(super_image_path)
        self.assertEqual(vbmeta_image_path, get_image("vbmeta"))
        self.assertEqual(super_image_path, get_image("super"))

    def testParseRemoteHostConsoleAddress(self):
        """Test ParseRemoteHostConsoleAddress."""
        console_addr = goldfish_utils.ParseRemoteHostConsoleAddress(
            self._INSTANCE_NAME)
        self.assertEqual((self._IP_ADDRESS, self._CONSOLE_PORT), console_addr)

        console_addr = goldfish_utils.ParseRemoteHostConsoleAddress(
            self._INVALID_NAME)
        self.assertIsNone(console_addr)

    def testFormatInstanceName(self):
        """Test FormatRemoteHostInstanceName."""
        instance_name = goldfish_utils.FormatRemoteHostInstanceName(
            self._IP_ADDRESS, self._CONSOLE_PORT, self._BUILD_INFO)
        self.assertEqual(self._INSTANCE_NAME, instance_name)

        instance_name = goldfish_utils.FormatRemoteHostInstanceName(
            self._IP_ADDRESS, self._CONSOLE_PORT, {})
        self.assertEqual(self._INSTANCE_NAME_WITHOUT_INFO, instance_name)

    def testConvertAvdSpecToArgs(self):
        """Test ConvertAvdSpecToArgs."""
        hw_property = {
            "cpu": "2",
            "x_res": "1270",
            "y_res": "700",
            "memory": "2048",
            "disk": "4096"
        }
        mock_spec = mock.Mock(hw_customize=True, gpu='off',
                              hw_property=hw_property)
        self.assertEqual(["-gpu", "off", "-cores", "2", "-skin", "1270x700",
                          "-memory", "2048", "-partition-size", "4096"],
                         goldfish_utils.ConvertAvdSpecToArgs(mock_spec))

        mock_spec = mock.Mock(hw_customize=True, gpu=None, hw_property={})
        self.assertEqual([], goldfish_utils.ConvertAvdSpecToArgs(mock_spec))


if __name__ == "__main__":
    unittest.main()
