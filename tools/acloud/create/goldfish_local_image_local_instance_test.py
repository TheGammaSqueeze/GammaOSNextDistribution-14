# Copyright 2019 - The Android Open Source Project
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
"""Tests for GoldfishLocalImageLocalInstance."""

import os
import shutil
import tempfile
import unittest

from unittest import mock

from acloud import errors
import acloud.create.goldfish_local_image_local_instance as instance_module
from acloud.internal.lib import driver_test_lib


class GoldfishLocalImageLocalInstance(driver_test_lib.BaseDriverTest):
    """Test GoldfishLocalImageLocalInstance methods."""

    def setUp(self):
        self._goldfish = instance_module.GoldfishLocalImageLocalInstance()
        self._temp_dir = tempfile.mkdtemp()
        self._image_dir = os.path.join(self._temp_dir, "images")
        self._tool_dir = os.path.join(self._temp_dir, "tool")
        self._instance_dir = os.path.join(self._temp_dir, "instance")
        self._emulator_is_running = False
        self._mock_lock = mock.Mock()
        self._mock_lock.Lock.return_value = True
        self._mock_lock.LockIfNotInUse.side_effect = (False, True)
        self._mock_proc = mock.Mock()
        self._mock_proc.poll.side_effect = (
            lambda: None if self._emulator_is_running else 0)

        os.mkdir(self._image_dir)
        os.mkdir(self._tool_dir)

        # Create emulator binary
        self._emulator_path = os.path.join(self._tool_dir, "emulator",
                                           "emulator")
        self._CreateEmptyFile(self._emulator_path)

    def tearDown(self):
        shutil.rmtree(self._temp_dir, ignore_errors=True)

    @staticmethod
    def _CreateEmptyFile(path):
        parent_dir = os.path.dirname(path)
        if not os.path.exists(parent_dir):
            os.makedirs(parent_dir)
        with open(path, "w") as _:
            pass

    def _MockMixDiskImage(self, output_dir, *_args):
        """Mock goldfish_utils.MixDiskImage."""
        self.assertEqual(os.path.join(self._instance_dir, "mix_disk"),
                         output_dir)
        output_path = os.path.join(output_dir, "mixed_disk.img")
        self._CreateEmptyFile(output_path)
        return output_path

    def _MockMixWithBootImage(self, output_dir, *_args):
        """Mock goldfish_utils.MixWithBootImage."""
        self.assertEqual(os.path.join(self._instance_dir, "mix_kernel"),
                         output_dir)
        return (os.path.join(output_dir, "kernel"),
                os.path.join(output_dir, "ramdisk"))

    def _MockPopen(self, *_args, **_kwargs):
        self._emulator_is_running = True
        return self._mock_proc

    def _MockEmuCommand(self, *args):
        if not self._emulator_is_running:
            # Connection refused
            return 1

        if args == ("kill",):
            self._emulator_is_running = False
            return 0

        if args == ():
            return 0

        raise ValueError("Unexpected arguments " + str(args))

    def _SetUpMocks(self, mock_popen, mock_utils, mock_instance,
                    mock_gf_utils):
        mock_utils.IsSupportedPlatform.return_value = True

        mock_adb_tools = mock.Mock(side_effect=self._MockEmuCommand)

        mock_instance_object = mock.Mock(ip="127.0.0.1",
                                         adb_port=5555,
                                         console_port="5554",
                                         device_serial="unittest",
                                         instance_dir=self._instance_dir,
                                         adb=mock_adb_tools)
        # name is a positional argument of Mock().
        mock_instance_object.name = "local-goldfish-instance"

        mock_instance.return_value = mock_instance_object
        mock_instance.GetLockById.return_value = self._mock_lock
        mock_instance.GetMaxNumberOfInstances.return_value = 2

        mock_popen.side_effect = self._MockPopen

        mock_gf_utils.SYSTEM_QEMU_IMAGE_NAME = "system-qemu.img"
        mock_gf_utils.VERIFIED_BOOT_PARAMS_FILE_NAME = (
            "VerifiedBootParams.textproto")
        mock_gf_utils.MixDiskImage.side_effect = self._MockMixDiskImage
        mock_gf_utils.MixWithBootImage.side_effect = self._MockMixWithBootImage
        mock_gf_utils.ConvertAvdSpecToArgs.return_value = ["-gpu", "auto"]

    def _CreateMockAvdSpec(self, local_instance_id, autoconnect=True,
                           boot_timeout_secs=None,
                           local_instance_dir=None, local_kernel_image=None,
                           local_system_image=None, local_tool_dirs=None):
        """Return a mock avd_spec.AvdSpec with needed attributes."""
        attr_dict = {
            "autoconnect": autoconnect,
            "boot_timeout_secs": boot_timeout_secs,
            "flavor": "phone",
            "local_image_dir": self._image_dir,
            "local_instance_id": local_instance_id,
            "local_instance_dir": local_instance_dir,
            "local_kernel_image": local_kernel_image,
            "local_system_image": local_system_image,
            "local_tool_dirs": local_tool_dirs or [],
        }
        mock_avd_spec = mock.Mock(spec=list(attr_dict), **attr_dict)
        return mock_avd_spec

    def _GetExpectedEmulatorArgs(self, *extra_args):
        cmd = [
            self._emulator_path, "-verbose", "-show-kernel", "-read-only",
            "-ports", "5554,5555",
            "-logcat-output",
            os.path.join(self._instance_dir, "logcat.txt"),
            "-stdouterr-file",
            os.path.join(self._instance_dir, "kernel.log"),
            "-gpu", "auto"
        ]
        cmd.extend(extra_args)
        return cmd

    def _GetExpectedDevicesInReport(self):
        logcat_path = os.path.join(self._instance_dir, "logcat.txt")
        stdouterr_path = os.path.join(self._instance_dir, "kernel.log")
        return [
            {
                "instance_name": "local-goldfish-instance",
                "ip": "127.0.0.1:5555",
                "adb_port": 5555,
                "device_serial": "unittest",
                "logs": [
                    {"path": logcat_path, "type": "LOGCAT"},
                    {"path": stdouterr_path, "type": "KERNEL_LOG"}
                ]
            }
        ]

    # pylint: disable=protected-access
    @mock.patch("acloud.create.goldfish_local_image_local_instance.instance."
                "LocalGoldfishInstance")
    @mock.patch("acloud.create.goldfish_local_image_local_instance.utils")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "subprocess.Popen")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "goldfish_utils")
    def testCreateAVDInBuildEnvironment(self, mock_gf_utils, mock_popen,
                                        mock_utils, mock_instance):
        """Test _CreateAVD with build environment variables and files."""
        self._SetUpMocks(mock_popen, mock_utils, mock_instance, mock_gf_utils)

        self._CreateEmptyFile(os.path.join(self._image_dir,
                                           "system-qemu.img"))
        self._CreateEmptyFile(os.path.join(self._image_dir, "system",
                                           "build.prop"))

        mock_environ = {"ANDROID_EMULATOR_PREBUILTS":
                        os.path.join(self._tool_dir, "emulator")}

        mock_avd_spec = self._CreateMockAvdSpec(local_instance_id=1,
                                                boot_timeout_secs=100)

        # Test deleting an existing instance.
        self._emulator_is_running = True

        with mock.patch.dict("acloud.create."
                             "goldfish_local_image_local_instance.os.environ",
                             mock_environ, clear=True):
            report = self._goldfish._CreateAVD(mock_avd_spec, no_prompts=False)

        self.assertEqual(report.data.get("devices"),
                         self._GetExpectedDevicesInReport())

        self._mock_lock.Lock.assert_called_once()
        self._mock_lock.SetInUse.assert_called_once_with(True)
        self._mock_lock.Unlock.assert_called_once()

        mock_instance.assert_called_once_with(1, avd_flavor="phone")

        self.assertTrue(os.path.isdir(self._instance_dir))

        mock_utils.SetExecutable.assert_called_with(self._emulator_path)
        mock_popen.assert_called_once()
        self.assertEqual(mock_popen.call_args[0][0],
                         self._GetExpectedEmulatorArgs())
        self._mock_proc.poll.assert_called()

    # pylint: disable=protected-access
    @mock.patch("acloud.create.goldfish_local_image_local_instance.instance."
                "LocalGoldfishInstance")
    @mock.patch("acloud.create.goldfish_local_image_local_instance.utils")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "subprocess.Popen")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "goldfish_utils")
    def testCreateAVDFromSdkRepository(self, mock_gf_utils, mock_popen,
                                       mock_utils, mock_instance):
        """Test _CreateAVD with SDK repository files."""
        self._SetUpMocks(mock_popen, mock_utils, mock_instance, mock_gf_utils)

        self._CreateEmptyFile(os.path.join(self._image_dir, "x86",
                                           "system.img"))
        self._CreateEmptyFile(os.path.join(self._image_dir, "x86",
                                           "build.prop"))

        instance_dir = os.path.join(self._temp_dir, "local_instance_dir")
        os.mkdir(instance_dir)

        mock_avd_spec = self._CreateMockAvdSpec(
            local_instance_id=2,
            local_instance_dir=instance_dir,
            local_tool_dirs=[self._tool_dir])

        with mock.patch.dict("acloud.create."
                             "goldfish_local_image_local_instance.os.environ",
                             dict(), clear=True):
            report = self._goldfish._CreateAVD(mock_avd_spec, no_prompts=True)

        self.assertEqual(report.data.get("devices"),
                         self._GetExpectedDevicesInReport())

        self._mock_lock.Lock.assert_called_once()
        self._mock_lock.SetInUse.assert_called_once_with(True)
        self._mock_lock.Unlock.assert_called_once()

        mock_instance.assert_called_once_with(2, avd_flavor="phone")

        self.assertTrue(os.path.isdir(self._instance_dir) and
                        os.path.islink(self._instance_dir))

        mock_utils.SetExecutable.assert_called_with(self._emulator_path)
        mock_popen.assert_called_once()
        self.assertEqual(mock_popen.call_args[0][0],
                         self._GetExpectedEmulatorArgs())
        self._mock_proc.poll.assert_called()

        self.assertTrue(os.path.isfile(
            os.path.join(self._image_dir, "x86", "system", "build.prop")))

    # pylint: disable=protected-access
    @mock.patch("acloud.create.goldfish_local_image_local_instance.instance."
                "LocalGoldfishInstance")
    @mock.patch("acloud.create.goldfish_local_image_local_instance.utils")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "subprocess.Popen")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "goldfish_utils")
    def testCreateAVDTimeout(self, mock_gf_utils, mock_popen, mock_utils, mock_instance):
        """Test _CreateAVD with SDK repository files and timeout error."""
        self._SetUpMocks(mock_popen, mock_utils, mock_instance, mock_gf_utils)
        mock_utils.PollAndWait.side_effect = errors.DeviceBootTimeoutError(
            "timeout")

        self._CreateEmptyFile(os.path.join(self._image_dir, "system.img"))
        self._CreateEmptyFile(os.path.join(self._image_dir, "build.prop"))

        mock_avd_spec = self._CreateMockAvdSpec(
            local_instance_id=2,
            local_tool_dirs=[self._tool_dir])

        with mock.patch.dict("acloud.create."
                             "goldfish_local_image_local_instance.os.environ",
                             dict(), clear=True):
            report = self._goldfish._CreateAVD(mock_avd_spec, no_prompts=True)

        self._mock_lock.Lock.assert_called_once()
        self._mock_lock.SetInUse.assert_called_once_with(True)
        self._mock_lock.Unlock.assert_called_once()

        self.assertEqual(report.data.get("devices_failing_boot"),
                         self._GetExpectedDevicesInReport())
        self.assertEqual(report.errors, ["timeout"])

    # pylint: disable=protected-access
    @mock.patch("acloud.create.goldfish_local_image_local_instance.instance."
                "LocalGoldfishInstance")
    @mock.patch("acloud.create.goldfish_local_image_local_instance.utils")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "subprocess.Popen")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "goldfish_utils")
    def testCreateAVDWithoutReport(self, mock_gf_utils, mock_popen, mock_utils,
                                   mock_instance):
        """Test _CreateAVD with SDK repository files and no report."""
        self._SetUpMocks(mock_popen, mock_utils, mock_instance, mock_gf_utils)

        mock_avd_spec = self._CreateMockAvdSpec(
            local_instance_id=0,
            local_tool_dirs=[self._tool_dir])

        with mock.patch.dict("acloud.create."
                             "goldfish_local_image_local_instance.os.environ",
                             dict(), clear=True):
            with self.assertRaises(errors.GetLocalImageError):
                self._goldfish._CreateAVD(mock_avd_spec, no_prompts=True)

        self._mock_lock.Lock.assert_not_called()
        self.assertEqual(2, self._mock_lock.LockIfNotInUse.call_count)
        self._mock_lock.SetInUse.assert_not_called()
        self._mock_lock.Unlock.assert_called_once()

    # pylint: disable=protected-access
    @mock.patch("acloud.create.goldfish_local_image_local_instance.instance."
                "LocalGoldfishInstance")
    @mock.patch("acloud.create.goldfish_local_image_local_instance.utils")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "subprocess.Popen")
    @mock.patch("acloud.create.goldfish_local_image_local_instance.ota_tools")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "goldfish_utils")
    def testCreateAVDWithMixedImages(self, mock_gf_utils, mock_ota_tools,
                                     mock_popen, mock_utils, mock_instance):
        """Test _CreateAVD with mixed images and SDK repository files."""
        self._SetUpMocks(mock_popen, mock_utils, mock_instance, mock_gf_utils)

        system_image_path = os.path.join(self._image_dir, "x86", "system.img")
        self._CreateEmptyFile(system_image_path)
        self._CreateEmptyFile(os.path.join(self._image_dir, "x86", "system",
                                           "build.prop"))
        params_path = os.path.join(self._image_dir, "x86",
                                   "VerifiedBootParams.textproto")
        self._CreateEmptyFile(params_path)

        mock_avd_spec = self._CreateMockAvdSpec(
            local_instance_id=3,
            autoconnect=False,
            local_system_image=system_image_path,
            local_tool_dirs=[self._tool_dir])

        with mock.patch.dict("acloud.create."
                             "goldfish_local_image_local_instance.os.environ",
                             dict(), clear=True):
            report = self._goldfish._CreateAVD(mock_avd_spec, no_prompts=True)

        self.assertEqual(report.data.get("devices"),
                         self._GetExpectedDevicesInReport())

        mock_instance.assert_called_once_with(3, avd_flavor="phone")

        self.assertTrue(os.path.isdir(self._instance_dir))

        mock_ota_tools.FindOtaTools.assert_called_once_with([self._tool_dir])

        mock_gf_utils.MixDiskImage.assert_called_once_with(
            mock.ANY, os.path.join(self._image_dir, "x86"), system_image_path,
            None, mock_ota_tools.FindOtaTools.return_value)
        self.assertTrue(os.path.isfile(params_path + ".bak-non-mixed"))
        with open(params_path, "r", encoding="utf-8") as params_file:
            self.assertEqual(
                params_file.read(),
                '\nparam: "androidboot.verifiedbootstate=orange"\n')

        mock_utils.SetExecutable.assert_called_with(self._emulator_path)
        mock_popen.assert_called_once()
        self.assertEqual(mock_popen.call_args[0][0],
                         self._GetExpectedEmulatorArgs("-no-window"))
        self._mock_proc.poll.assert_called()

    # pylint: disable=protected-access
    @mock.patch("acloud.create.goldfish_local_image_local_instance.instance."
                "LocalGoldfishInstance")
    @mock.patch("acloud.create.goldfish_local_image_local_instance.utils")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "subprocess.Popen")
    @mock.patch("acloud.create.goldfish_local_image_local_instance.ota_tools")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "goldfish_utils")
    def testCreateAVDWithBootImage(self, mock_gf_utils, mock_ota_tools,
                                   mock_popen, mock_utils, mock_instance):
        """Test _CreateAVD with a boot image and SDK repository files."""
        self._SetUpMocks(mock_popen, mock_utils, mock_instance, mock_gf_utils)

        image_subdir = os.path.join(self._image_dir, "x86")
        boot_image_path = os.path.join(self._temp_dir, "kernel_images",
                                       "boot-5.10.img")
        self.CreateFile(boot_image_path, b"ANDROID!")
        self._CreateEmptyFile(os.path.join(image_subdir, "system.img"))
        self._CreateEmptyFile(os.path.join(image_subdir, "build.prop"))

        mock_avd_spec = self._CreateMockAvdSpec(
            local_instance_id=3,
            local_kernel_image=os.path.dirname(boot_image_path),
            local_tool_dirs=[self._tool_dir])

        with mock.patch.dict("acloud.create."
                             "goldfish_local_image_local_instance.os.environ",
                             dict(), clear=True):
            report = self._goldfish._CreateAVD(mock_avd_spec, no_prompts=True)

        self.assertEqual(report.data.get("devices"),
                         self._GetExpectedDevicesInReport())

        mock_gf_utils.MixWithBootImage.assert_called_once_with(
            mock.ANY, os.path.join(image_subdir), boot_image_path,
            mock_ota_tools.FindOtaTools.return_value)

        mock_popen.assert_called_once()
        self.assertEqual(
            mock_popen.call_args[0][0],
            self._GetExpectedEmulatorArgs(
                "-kernel",
                os.path.join(self._instance_dir, "mix_kernel", "kernel"),
                "-ramdisk",
                os.path.join(self._instance_dir, "mix_kernel", "ramdisk")))

    # pylint: disable=protected-access
    @mock.patch("acloud.create.goldfish_local_image_local_instance.instance."
                "LocalGoldfishInstance")
    @mock.patch("acloud.create.goldfish_local_image_local_instance.utils")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "subprocess.Popen")
    @mock.patch("acloud.create.goldfish_local_image_local_instance.ota_tools")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "goldfish_utils")
    def testCreateAVDWithKernelImages(self, mock_gf_utils, mock_ota_tools,
                                      mock_popen, mock_utils, mock_instance):
        """Test _CreateAVD with kernel images and SDK repository files."""
        self._SetUpMocks(mock_popen, mock_utils, mock_instance, mock_gf_utils)

        kernel_subdir = os.path.join(self._temp_dir, "kernel_images", "x86")
        kernel_image_path = os.path.join(kernel_subdir, "kernel-ranchu")
        ramdisk_image_path = os.path.join(kernel_subdir, "ramdisk.img")
        mock_gf_utils.FindKernelImages.return_value = (kernel_image_path,
                                                       ramdisk_image_path)

        os.makedirs(kernel_subdir)
        self._CreateEmptyFile(os.path.join(self._image_dir, "x86",
                                           "system.img"))
        self._CreateEmptyFile(os.path.join(self._image_dir, "x86",
                                           "build.prop"))

        mock_avd_spec = self._CreateMockAvdSpec(
            local_instance_id=3,
            local_kernel_image=os.path.dirname(kernel_subdir),
            local_tool_dirs=[self._tool_dir])

        with mock.patch.dict("acloud.create."
                             "goldfish_local_image_local_instance.os.environ",
                             dict(), clear=True):
            report = self._goldfish._CreateAVD(mock_avd_spec, no_prompts=True)

        self.assertEqual(report.data.get("devices"),
                         self._GetExpectedDevicesInReport())

        mock_ota_tools.FindOtaTools.assert_not_called()
        mock_gf_utils.FindKernelImages.assert_called_once_with(kernel_subdir)

        mock_popen.assert_called_once()
        self.assertEqual(
            mock_popen.call_args[0][0],
            self._GetExpectedEmulatorArgs(
                "-kernel", kernel_image_path,
                "-ramdisk", ramdisk_image_path))

    # pylint: disable=protected-access
    @mock.patch("acloud.create.goldfish_local_image_local_instance.instance."
                "LocalGoldfishInstance")
    @mock.patch("acloud.create.goldfish_local_image_local_instance.utils")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "subprocess.Popen")
    @mock.patch("acloud.create.goldfish_local_image_local_instance.ota_tools")
    @mock.patch("acloud.create.goldfish_local_image_local_instance."
                "goldfish_utils")
    def testCreateAVDWithMixedImageDirs(self, mock_gf_utils, mock_ota_tools,
                                        mock_popen, mock_utils, mock_instance):
        """Test _CreateAVD with mixed images in build environment."""
        self._SetUpMocks(mock_popen, mock_utils, mock_instance, mock_gf_utils)

        system_image_path = os.path.join(self._image_dir, "system.img")
        self._CreateEmptyFile(system_image_path)
        disk_image_path = os.path.join(self._image_dir, "system-qemu.img")
        self._CreateEmptyFile(disk_image_path)
        self._CreateEmptyFile(os.path.join(self._image_dir, "system",
                                           "build.prop"))
        params_path = os.path.join(self._image_dir,
                                   "VerifiedBootParams.textproto")
        self._CreateEmptyFile(params_path)

        mock_environ = {"ANDROID_EMULATOR_PREBUILTS":
                        os.path.join(self._tool_dir, "emulator"),
                        "ANDROID_HOST_OUT":
                        os.path.join(self._tool_dir, "host"),
                        "ANDROID_SOONG_HOST_OUT":
                        os.path.join(self._tool_dir, "soong")}

        mock_avd_spec = self._CreateMockAvdSpec(
            local_instance_id=3,
            autoconnect=False,
            local_system_image=self._image_dir)

        with mock.patch.dict("acloud.create."
                             "goldfish_local_image_local_instance.os.environ",
                             mock_environ, clear=True):
            report = self._goldfish._CreateAVD(mock_avd_spec, no_prompts=True)

        self.assertEqual(report.data.get("devices"),
                         self._GetExpectedDevicesInReport())

        mock_instance.assert_called_once_with(3, avd_flavor="phone")

        self.assertTrue(os.path.isdir(self._instance_dir))

        mock_ota_tools.FindOtaTools.assert_called_once_with([
            os.path.join(self._tool_dir, "soong"),
            os.path.join(self._tool_dir, "host")])

        mock_gf_utils.MixDiskImage.assert_called_once_with(
            mock.ANY, self._image_dir, system_image_path, None,
            mock_ota_tools.FindOtaTools.return_value)
        self.assertTrue(os.path.isfile(disk_image_path + ".bak-non-mixed"))
        self.assertTrue(os.path.isfile(params_path + ".bak-non-mixed"))
        with open(params_path, "r", encoding="utf-8") as params_file:
            self.assertEqual(
                params_file.read(),
                '\nparam: "androidboot.verifiedbootstate=orange"\n')

        mock_utils.SetExecutable.assert_called_with(self._emulator_path)
        mock_popen.assert_called_once()
        self.assertEqual(mock_popen.call_args[0][0],
                         self._GetExpectedEmulatorArgs("-no-window"))
        self._mock_proc.poll.assert_called()


if __name__ == "__main__":
    unittest.main()
