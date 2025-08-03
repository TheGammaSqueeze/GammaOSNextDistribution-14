#!/usr/bin/env python
#
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
"""Tests for LocalImageLocalInstance."""

import builtins
import os
import subprocess
import tempfile
import unittest

from unittest import mock

from acloud import errors
from acloud.create import local_image_local_instance
from acloud.list import instance
from acloud.list import list as list_instance
from acloud.internal import constants
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import utils
from acloud.public import report


class LocalImageLocalInstanceTest(driver_test_lib.BaseDriverTest):
    """Test LocalImageLocalInstance method."""

    LAUNCH_CVD_CMD_WITH_DISK = """sg group1 <<EOF
sg group2
bin/cvd start -daemon -config=phone -system_image_dir fake_image_dir -instance_dir fake_cvd_dir -undefok=report_anonymous_usage_stats,config -report_anonymous_usage_stats=y -cpus fake -x_res fake -y_res fake -dpi fake -memory_mb fake -blank_data_image_mb fake -data_policy always_create -start_vnc_server=true
EOF"""

    LAUNCH_CVD_CMD_NO_DISK = """sg group1 <<EOF
sg group2
bin/cvd start -daemon -config=phone -system_image_dir fake_image_dir -instance_dir fake_cvd_dir -undefok=report_anonymous_usage_stats,config -report_anonymous_usage_stats=y -cpus fake -x_res fake -y_res fake -dpi fake -memory_mb fake -start_vnc_server=true
EOF"""

    LAUNCH_CVD_CMD_NO_DISK_WITH_GPU = """sg group1 <<EOF
sg group2
bin/cvd start -daemon -config=phone -system_image_dir fake_image_dir -instance_dir fake_cvd_dir -undefok=report_anonymous_usage_stats,config -report_anonymous_usage_stats=y -cpus fake -x_res fake -y_res fake -dpi fake -memory_mb fake -start_vnc_server=true
EOF"""

    LAUNCH_CVD_CMD_WITH_WEBRTC = """sg group1 <<EOF
sg group2
bin/cvd start -daemon -config=auto -system_image_dir fake_image_dir -instance_dir fake_cvd_dir -undefok=report_anonymous_usage_stats,config -report_anonymous_usage_stats=y -start_webrtc=true
EOF"""

    LAUNCH_CVD_CMD_WITH_MIXED_IMAGES = """sg group1 <<EOF
sg group2
bin/cvd start -daemon -config=phone -system_image_dir fake_image_dir -instance_dir fake_cvd_dir -undefok=report_anonymous_usage_stats,config -report_anonymous_usage_stats=y -start_vnc_server=true -super_image=fake_super_image -boot_image=fake_boot_image -vendor_boot_image=fake_vendor_boot_image
EOF"""

    LAUNCH_CVD_CMD_WITH_KERNEL_IMAGES = """sg group1 <<EOF
sg group2
bin/cvd start -daemon -config=phone -system_image_dir fake_image_dir -instance_dir fake_cvd_dir -undefok=report_anonymous_usage_stats,config -report_anonymous_usage_stats=y -start_vnc_server=true -kernel_path=fake_kernel_image -initramfs_path=fake_initramfs_image
EOF"""

    LAUNCH_CVD_CMD_WITH_VBMETA_IMAGE = """sg group1 <<EOF
sg group2
bin/cvd start -daemon -config=phone -system_image_dir fake_image_dir -instance_dir fake_cvd_dir -undefok=report_anonymous_usage_stats,config -report_anonymous_usage_stats=y -start_vnc_server=true -vbmeta_image=fake_vbmeta_image
EOF"""

    LAUNCH_CVD_CMD_WITH_ARGS = """sg group1 <<EOF
sg group2
bin/cvd start -daemon -config=phone -system_image_dir fake_image_dir -instance_dir fake_cvd_dir -undefok=report_anonymous_usage_stats,config -report_anonymous_usage_stats=y -start_vnc_server=true -setupwizard_mode=REQUIRED
EOF"""

    LAUNCH_CVD_CMD_WITH_OPENWRT = """sg group1 <<EOF
sg group2
bin/launch_cvd -daemon -config=phone -system_image_dir fake_image_dir -instance_dir fake_cvd_dir -undefok=report_anonymous_usage_stats,config -report_anonymous_usage_stats=y -start_vnc_server=true -console=true
EOF"""

    LAUNCH_CVD_CMD_WITH_PET_NAME = """sg group1 <<EOF
sg group2
bin/cvd start -daemon -config=phone -system_image_dir fake_image_dir -instance_dir fake_cvd_dir -undefok=report_anonymous_usage_stats,config -report_anonymous_usage_stats=y -start_vnc_server=true -webrtc_device_id=pet-name
EOF"""

    LAUNCH_CVD_CMD_WITH_NO_CVD = """sg group1 <<EOF
sg group2
bin/launch_cvd -daemon -config=phone -system_image_dir fake_image_dir -instance_dir fake_cvd_dir -undefok=report_anonymous_usage_stats,config -report_anonymous_usage_stats=y -start_vnc_server=true
EOF"""

    LAUNCH_CVD_CMD_WITH_INS_IDS = """sg group1 <<EOF
sg group2
bin/cvd start -daemon -config=phone -system_image_dir fake_image_dir -instance_dir fake_cvd_dir -undefok=report_anonymous_usage_stats,config -report_anonymous_usage_stats=y -start_vnc_server=true -instance_nums=1,2
EOF"""

    _EXPECTED_DEVICES_IN_REPORT = [
        {
            "instance_name": "local-instance-1",
            "ip": "0.0.0.0:6520",
            "adb_port": 6520,
            "vnc_port": 6444,
            "webrtc_port": 8443,
            'logs': [{'path': '/log/launcher.log', 'type': 'TEXT'}],
            "screen_command": "screen /instances/cvd/console"
        }
    ]

    _EXPECTED_DEVICES_IN_FAILED_REPORT = [
        {
            "instance_name": "local-instance-1",
            "ip": "0.0.0.0",
            'logs': [{'path': '/log/launcher.log', 'type': 'TEXT'}],
        }
    ]

    def setUp(self):
        """Initialize new LocalImageLocalInstance."""
        super().setUp()
        self.local_image_local_instance = local_image_local_instance.LocalImageLocalInstance()

    # pylint: disable=protected-access
    @mock.patch("acloud.create.local_image_local_instance.utils")
    @mock.patch.object(local_image_local_instance.LocalImageLocalInstance,
                       "GetImageArtifactsPath")
    @mock.patch.object(local_image_local_instance.LocalImageLocalInstance,
                       "_SelectAndLockInstance")
    @mock.patch.object(local_image_local_instance.LocalImageLocalInstance,
                       "_CheckRunningCvd")
    @mock.patch.object(local_image_local_instance.LocalImageLocalInstance,
                       "_CreateInstance")
    def testCreateAVD(self, mock_create, mock_check_running_cvd,
                      mock_lock_instance, mock_get_image, mock_utils):
        """Test _CreateAVD."""
        mock_utils.IsSupportedPlatform.return_value = True
        mock_get_image.return_value = local_image_local_instance.ArtifactPaths(
            "/image/path", "/host/bin/path", "host/usr/path",
            None, None,  # misc_info
            None, None, None,  # system
            None, None, None, None,  # boot
            None, None, None, None)  # vendor
        mock_check_running_cvd.return_value = True
        mock_avd_spec = mock.Mock()
        mock_avd_spec.num_avds_per_instance = 1
        mock_avd_spec.local_instance_dir = None
        mock_lock = mock.Mock()
        mock_lock.Unlock.return_value = False
        mock_lock_instance.return_value = (1, mock_lock)
        mock_report = mock.Mock()
        mock_create.return_value = mock_report

        # Success
        mock_report.status = report.Status.SUCCESS
        self.local_image_local_instance._CreateAVD(
            mock_avd_spec, no_prompts=True)
        mock_lock_instance.assert_called_once()
        mock_lock.SetInUse.assert_called_once_with(True)
        mock_lock.Unlock.assert_called_once()

        mock_lock_instance.reset_mock()
        mock_lock.SetInUse.reset_mock()
        mock_lock.Unlock.reset_mock()

        # Failure with report
        mock_report.status = report.Status.BOOT_FAIL
        self.local_image_local_instance._CreateAVD(
            mock_avd_spec, no_prompts=True)
        mock_lock_instance.assert_called_once()
        mock_lock.SetInUse.assert_not_called()
        mock_lock.Unlock.assert_called_once()

        mock_lock_instance.reset_mock()
        mock_lock.Unlock.reset_mock()

        # Failure with no report
        mock_create.side_effect = ValueError("unit test")
        with self.assertRaises(ValueError):
            self.local_image_local_instance._CreateAVD(
                mock_avd_spec, no_prompts=True)
        mock_lock_instance.assert_called_once()
        mock_lock.SetInUse.assert_not_called()
        mock_lock.Unlock.assert_called_once()

    def testSelectAndLockInstances(self):
        """test _SelectAndLockInstances."""
        mock_avd_spec = mock.Mock(num_avds_per_instance=1)
        mock_main_lock = mock.Mock()
        self.Patch(local_image_local_instance.LocalImageLocalInstance,
                   "_SelectAndLockInstance", return_value=(1, mock_main_lock))
        ins_ids, ins_locks = self.local_image_local_instance._SelectAndLockInstances(
            mock_avd_spec)
        self.assertEqual([1], ins_ids)
        self.assertEqual([mock_main_lock], ins_locks)

        mock_avd_spec.num_avds_per_instance = 2
        mock_second_lock = mock.Mock()
        self.Patch(local_image_local_instance.LocalImageLocalInstance,
                   "_SelectOneFreeInstance", return_value=(2, mock_second_lock))
        ins_ids, ins_locks = self.local_image_local_instance._SelectAndLockInstances(
            mock_avd_spec)
        self.assertEqual([1,2], ins_ids)
        self.assertEqual([mock_main_lock, mock_second_lock], ins_locks)

    def testSelectAndLockInstance(self):
        """test _SelectAndLockInstance."""
        mock_avd_spec = mock.Mock(local_instance_id=0)
        mock_lock = mock.Mock()
        mock_lock.Lock.return_value = True
        mock_lock.LockIfNotInUse.side_effect = (False, True)
        self.Patch(instance, "GetLocalInstanceLock",
                   return_value=mock_lock)

        ins_id, _ = self.local_image_local_instance._SelectAndLockInstance(
            mock_avd_spec)
        self.assertEqual(2, ins_id)
        mock_lock.Lock.assert_not_called()
        self.assertEqual(2, mock_lock.LockIfNotInUse.call_count)

        mock_lock.LockIfNotInUse.reset_mock()

        mock_avd_spec.local_instance_id = 1
        ins_id, _ = self.local_image_local_instance._SelectAndLockInstance(
            mock_avd_spec)
        self.assertEqual(1, ins_id)
        mock_lock.Lock.assert_called_once()
        mock_lock.LockIfNotInUse.assert_not_called()

    @mock.patch.object(local_image_local_instance.LocalImageLocalInstance,
                       "_TrustCertificatesForWebRTC")
    @mock.patch("acloud.create.local_image_local_instance.utils")
    @mock.patch("acloud.create.local_image_local_instance.ota_tools")
    @mock.patch("acloud.create.local_image_local_instance.create_common")
    @mock.patch.object(local_image_local_instance.LocalImageLocalInstance,
                       "_LogCvdVersion")
    @mock.patch.object(local_image_local_instance.LocalImageLocalInstance,
                       "_LaunchCvd")
    @mock.patch.object(local_image_local_instance.LocalImageLocalInstance,
                       "PrepareLaunchCVDCmd")
    @mock.patch("acloud.create.local_image_local_instance.cvd_utils")
    @mock.patch("acloud.create.local_image_local_instance.instance")
    def testCreateInstance(self, mock_instance, mock_cvd_utils,
                           _mock_prepare_cmd, mock_launch_cvd,
                           mock_log_cvd_version, _mock_create_common,
                           mock_ota_tools, _mock_utils, mock_trust_certs):
        """Test the report returned by _CreateInstance."""
        mock_instance.GetLocalInstanceHomeDir.return_value = (
            "/local-instance-1")
        mock_instance.GetLocalInstanceName.return_value = "local-instance-1"
        mock_instance.GetLocalInstanceRuntimeDir.return_value = (
            "/instances/cvd")
        mock_instance.GetLocalInstanceConfig.return_value = (
            "/instances/cvd/config")
        mock_cvd_utils.FindLocalLogs.return_value = [
            {'path': '/log/launcher.log', 'type': 'TEXT'}]
        artifact_paths = local_image_local_instance.ArtifactPaths(
            "/image/path", "/host/bin/path", "/host/usr/path",
            "/misc/info/path", "/ota/tools/dir", "/system/image/path",
            "/system_ext/image/path", "/product/image/path", "/boot/image/path",
            "/vendor_boot/image/path", "/kernel/image/path",
            "/initramfs/image/path", "/vendor/image/path",
            "/vendor_dlkm/image/path", "/odm/image/path",
            "/odm_dlkm/image/path")
        mock_ota_tools_object = mock.Mock()
        mock_ota_tools.OtaTools.return_value = mock_ota_tools_object
        mock_avd_spec = mock.Mock(
            unlock_screen=False, connect_webrtc=True, openwrt=True,
            use_launch_cvd=False)
        local_ins = mock.Mock(
            adb_port=6520,
            vnc_port=6444
        )
        local_ins.CvdStatus.return_value = True
        self.Patch(instance, "LocalInstance",
                   return_value=local_ins)
        self.Patch(list_instance, "GetActiveCVD",
                   return_value=local_ins)
        self.Patch(os, "symlink")

        ins_ids = [1]
        # Success
        result_report = self.local_image_local_instance._CreateInstance(
            ins_ids, artifact_paths, mock_avd_spec, no_prompts=True)

        self.assertEqual(result_report.data.get("devices"),
                         self._EXPECTED_DEVICES_IN_REPORT)
        mock_ota_tools.OtaTools.assert_called_with("/ota/tools/dir")
        mock_ota_tools_object.MixSuperImage.assert_called_with(
            "/local-instance-1/mixed_super.img", "/misc/info/path",
            "/image/path",
            system_image="/system/image/path",
            system_ext_image="/system_ext/image/path",
            product_image="/product/image/path",
            vendor_image="/vendor/image/path",
            vendor_dlkm_image="/vendor_dlkm/image/path",
            odm_image="/odm/image/path",
            odm_dlkm_image="/odm_dlkm/image/path")
        mock_ota_tools_object.MakeDisabledVbmetaImage.assert_called_once()
        mock_cvd_utils.FindLocalLogs.assert_called_with(
            "/instances/cvd", 1)
        mock_log_cvd_version.assert_called_with("/host/bin/path")

        # should call _TrustCertificatesForWebRTC
        mock_trust_certs.assert_called_once()
        mock_trust_certs.reset_mock()

        # should not call _TrustCertificatesForWebRTC
        mock_avd_spec.connect_webrtc = False
        self.local_image_local_instance._CreateInstance(
            ins_ids, artifact_paths, mock_avd_spec, no_prompts=True)
        mock_trust_certs.assert_not_called()

        # Failure
        mock_cvd_utils.reset_mock()
        mock_launch_cvd.side_effect = errors.LaunchCVDFail("unit test")

        result_report = self.local_image_local_instance._CreateInstance(
            ins_ids, artifact_paths, mock_avd_spec, no_prompts=True)

        self.assertEqual(result_report.data.get("devices_failing_boot"),
                         self._EXPECTED_DEVICES_IN_FAILED_REPORT)
        self.assertIn("unit test", result_report.errors[0])
        mock_cvd_utils.FindLocalLogs.assert_called_with(
            "/instances/cvd", 1)

    # pylint: disable=protected-access
    @mock.patch("acloud.create.local_image_local_instance.os.path.isfile")
    def testFindCvdHostBinaries(self, mock_isfile):
        """Test FindCvdHostBinaries."""
        cvd_host_dir = "/unit/test"
        mock_isfile.return_value = None

        with self.assertRaises(errors.GetCvdLocalHostPackageError):
            self.local_image_local_instance._FindCvdHostBinaries(
                [cvd_host_dir])

        mock_isfile.side_effect = (
            lambda path: path == "/unit/test/bin/launch_cvd")

        path = self.local_image_local_instance._FindCvdHostBinaries(
            [cvd_host_dir])
        self.assertEqual(path, cvd_host_dir)

    @staticmethod
    def _CreateEmptyFile(path):
        driver_test_lib.BaseDriverTest.CreateFile(path)

    @mock.patch("acloud.create.local_image_local_instance.ota_tools")
    def testGetImageArtifactsPath(self, mock_ota_tools):
        """Test GetImageArtifactsPath without system image dir."""
        with tempfile.TemporaryDirectory() as temp_dir:
            image_dir = os.path.join(temp_dir, "image")
            cvd_dir = os.path.join(temp_dir, "cvd-host_package")
            self._CreateEmptyFile(os.path.join(cvd_dir, "bin", "launch_cvd"))
            self._CreateEmptyFile(os.path.join(cvd_dir, "usr/share/webrtc/certs", "server.crt"))

            mock_avd_spec = mock.Mock(
                local_image_dir=image_dir,
                local_kernel_image=None,
                local_system_image=None,
                local_vendor_image=None,
                local_tool_dirs=[cvd_dir])

            with self.assertRaisesRegex(
                    errors.GetLocalImageError,
                    r"The directory is expected to be an extracted img zip "
                    r"or ANDROID_PRODUCT_OUT\."):
                self.local_image_local_instance.GetImageArtifactsPath(
                    mock_avd_spec)

            self._CreateEmptyFile(os.path.join(image_dir, "super.img"))

            paths = self.local_image_local_instance.GetImageArtifactsPath(
                mock_avd_spec)

        mock_ota_tools.FindOtaToolsDir.assert_not_called()
        self.assertEqual(
            paths,
            (image_dir, cvd_dir, cvd_dir,
             None, None,  # misc_info
             None, None, None,  # system
             None, None, None, None,  # boot
             None, None, None, None))  # vendor

    # pylint: disable=too-many-locals
    @mock.patch("acloud.create.local_image_local_instance.ota_tools")
    def testGetImageFromBuildEnvironment(self, mock_ota_tools):
        """Test GetImageArtifactsPath with files in build environment."""
        with tempfile.TemporaryDirectory() as temp_dir:
            image_dir = os.path.join(temp_dir, "image")
            cvd_dir = os.path.join(temp_dir, "cvd-host_package")
            mock_ota_tools.FindOtaToolsDir.return_value = cvd_dir
            extra_image_dir = os.path.join(temp_dir, "extra_image")
            system_image_path = os.path.join(extra_image_dir, "system.img")
            system_ext_image_path = os.path.join(extra_image_dir,
                                                 "system_ext.img")
            product_image_path = os.path.join(extra_image_dir, "product.img")
            misc_info_path = os.path.join(image_dir, "misc_info.txt")
            boot_image_path = os.path.join(extra_image_dir, "boot.img")
            vendor_boot_image_path = os.path.join(extra_image_dir,
                                                  "vendor_boot.img")
            vendor_image_path = os.path.join(extra_image_dir, "vendor.img")
            vendor_dlkm_image_path = os.path.join(extra_image_dir, "vendor_dlkm.img")
            odm_image_path = os.path.join(extra_image_dir, "odm.img")
            odm_dlkm_image_path = os.path.join(extra_image_dir, "odm_dlkm.img")
            self._CreateEmptyFile(os.path.join(image_dir, "vbmeta.img"))
            self._CreateEmptyFile(os.path.join(cvd_dir, "bin", "launch_cvd"))
            self._CreateEmptyFile(os.path.join(cvd_dir, "usr/share/webrtc/certs", "server.crt"))
            self._CreateEmptyFile(system_image_path)
            self._CreateEmptyFile(system_ext_image_path)
            self._CreateEmptyFile(product_image_path)
            self._CreateEmptyFile(os.path.join(extra_image_dir,
                                               "boot-debug.img"))
            self._CreateEmptyFile(misc_info_path)
            self._CreateEmptyFile(vendor_image_path)
            self._CreateEmptyFile(vendor_dlkm_image_path)
            self._CreateEmptyFile(odm_image_path)
            self._CreateEmptyFile(odm_dlkm_image_path)
            self.CreateFile(boot_image_path, b"ANDROID!test_boot_image")
            self.CreateFile(vendor_boot_image_path)

            mock_avd_spec = mock.Mock(
                local_image_dir=image_dir,
                local_kernel_image=extra_image_dir,
                local_system_image=extra_image_dir,
                local_vendor_image=extra_image_dir,
                local_tool_dirs=[])

            with mock.patch.dict("acloud.create.local_image_local_instance."
                                 "os.environ",
                                 {"ANDROID_SOONG_HOST_OUT": cvd_dir,
                                  "ANDROID_HOST_OUT": "/cvd"},
                                 clear=True):
                paths = self.local_image_local_instance.GetImageArtifactsPath(
                    mock_avd_spec)

        mock_ota_tools.FindOtaToolsDir.assert_called_with([cvd_dir, "/cvd"])
        self.assertEqual(
            paths,
            (image_dir, cvd_dir, cvd_dir, misc_info_path, cvd_dir,
             system_image_path, system_ext_image_path, product_image_path,
             boot_image_path, vendor_boot_image_path, None, None,
             vendor_image_path, vendor_dlkm_image_path,
             odm_image_path, odm_dlkm_image_path))

    @mock.patch("acloud.create.local_image_local_instance.ota_tools")
    def testGetImageFromTargetFiles(self, mock_ota_tools):
        """Test GetImageArtifactsPath with extracted target files."""
        ota_tools_dir = "/mock_ota_tools"
        mock_ota_tools.FindOtaToolsDir.return_value = ota_tools_dir
        with tempfile.TemporaryDirectory() as temp_dir:
            image_dir = os.path.join(temp_dir, "image")
            cvd_dir = os.path.join(temp_dir, "cvd-host_package")
            system_image_path = os.path.join(temp_dir, "system", "test.img")
            misc_info_path = os.path.join(image_dir, "META", "misc_info.txt")
            kernel_image_dir = os.path.join(temp_dir, "kernel_image")
            kernel_image_path = os.path.join(kernel_image_dir, "Image")
            initramfs_image_path = os.path.join(kernel_image_dir,
                                                "initramfs.img")

            self.CreateFile(os.path.join(kernel_image_dir, "boot.img"))
            self.CreateFile(os.path.join(image_dir, "IMAGES", "vbmeta.img"))
            self.CreateFile(os.path.join(cvd_dir, "bin", "launch_cvd"))
            self.CreateFile(os.path.join(cvd_dir, "usr/share/webrtc/certs",
                                         "server.crt"))
            self.CreateFile(system_image_path)
            self.CreateFile(misc_info_path)
            self.CreateFile(kernel_image_path)
            self.CreateFile(initramfs_image_path)

            mock_avd_spec = mock.Mock(
                local_image_dir=image_dir,
                local_kernel_image=kernel_image_dir,
                local_system_image=system_image_path,
                local_vendor_image=None,
                local_tool_dirs=[ota_tools_dir, cvd_dir])

            with mock.patch.dict("acloud.create.local_image_local_instance."
                                 "os.environ",
                                 clear=True):
                paths = self.local_image_local_instance.GetImageArtifactsPath(
                    mock_avd_spec)

        mock_ota_tools.FindOtaToolsDir.assert_called_with(
            [ota_tools_dir, cvd_dir])
        self.assertEqual(
            paths,
            (os.path.join(image_dir, "IMAGES"), cvd_dir, cvd_dir,
             misc_info_path, ota_tools_dir,
             system_image_path, None, None,
             None, None, kernel_image_path, initramfs_image_path,
             None, None, None, None))

    @mock.patch.object(utils, "CheckUserInGroups")
    def testPrepareLaunchCVDCmd(self, mock_usergroups):
        """test PrepareLaunchCVDCmd."""
        mock_usergroups.return_value = False
        self.Patch(os.path, "isfile", return_value=True)
        hw_property = {"cpu": "fake", "x_res": "fake", "y_res": "fake",
                       "dpi":"fake", "memory": "fake", "disk": "fake"}
        constants.LIST_CF_USER_GROUPS = ["group1", "group2"]
        mock_artifact_paths = mock.Mock(
            spec=[],
            image_dir="fake_image_dir",
            host_bins="",
            host_artifacts="host_artifacts",
            misc_info=None,
            ota_tools_dir=None,
            system_image=None,
            boot_image=None,
            vendor_boot_image=None,
            kernel_image=None,
            initramfs_image=None,
            vendor_image=None,
            vendor_dlkm_image=None,
            odm_image=None,
            odm_dlkm_image=None)

        launch_cmd = self.local_image_local_instance.PrepareLaunchCVDCmd(
            hw_property, True, mock_artifact_paths, "fake_cvd_dir", False,
            True, None, None, "phone")
        self.assertEqual(launch_cmd, self.LAUNCH_CVD_CMD_WITH_DISK)

        # "disk" doesn't exist in hw_property.
        hw_property = {"cpu": "fake", "x_res": "fake", "y_res": "fake",
                       "dpi": "fake", "memory": "fake"}
        launch_cmd = self.local_image_local_instance.PrepareLaunchCVDCmd(
            hw_property, True, mock_artifact_paths, "fake_cvd_dir", False,
            True, None, None, "phone")
        self.assertEqual(launch_cmd, self.LAUNCH_CVD_CMD_NO_DISK)

        # "gpu" is enabled with "default"
        launch_cmd = self.local_image_local_instance.PrepareLaunchCVDCmd(
            hw_property, True, mock_artifact_paths, "fake_cvd_dir", False,
            True, None, None, "phone")
        self.assertEqual(launch_cmd, self.LAUNCH_CVD_CMD_NO_DISK_WITH_GPU)

        # Following test with hw_property is None.
        launch_cmd = self.local_image_local_instance.PrepareLaunchCVDCmd(
            None, True, mock_artifact_paths, "fake_cvd_dir", True, False,
            None, None, "auto")
        self.assertEqual(launch_cmd, self.LAUNCH_CVD_CMD_WITH_WEBRTC)

        # Mix super and boot images.
        mock_artifact_paths.boot_image = "fake_boot_image"
        mock_artifact_paths.vendor_boot_image = "fake_vendor_boot_image"
        launch_cmd = self.local_image_local_instance.PrepareLaunchCVDCmd(
            None, True, mock_artifact_paths, "fake_cvd_dir", False, True,
            "fake_super_image", None, "phone")
        self.assertEqual(launch_cmd, self.LAUNCH_CVD_CMD_WITH_MIXED_IMAGES)
        mock_artifact_paths.boot_image = None
        mock_artifact_paths.vendor_boot_image = None

        # Mix kernel images.
        mock_artifact_paths.kernel_image = "fake_kernel_image"
        mock_artifact_paths.initramfs_image = "fake_initramfs_image"
        launch_cmd = self.local_image_local_instance.PrepareLaunchCVDCmd(
            None, True, mock_artifact_paths, "fake_cvd_dir", False, True,
            None, None, "phone")
        self.assertEqual(launch_cmd, self.LAUNCH_CVD_CMD_WITH_KERNEL_IMAGES)
        mock_artifact_paths.kernel_image = None
        mock_artifact_paths.initramfs_image = None

        # Specify vbmeta image.
        launch_cmd = self.local_image_local_instance.PrepareLaunchCVDCmd(
            None, True, mock_artifact_paths, "fake_cvd_dir", False, True,
            None, None, "phone", vbmeta_image_path="fake_vbmeta_image"
        )
        self.assertEqual(launch_cmd, self.LAUNCH_CVD_CMD_WITH_VBMETA_IMAGE)

        # Add args into launch command with "-setupwizard_mode=REQUIRED"
        launch_cmd = self.local_image_local_instance.PrepareLaunchCVDCmd(
            None, True, mock_artifact_paths, "fake_cvd_dir", False, True,
            None, "-setupwizard_mode=REQUIRED", "phone")
        self.assertEqual(launch_cmd, self.LAUNCH_CVD_CMD_WITH_ARGS)

        # Test with "openwrt" and "use_launch_cvd" are enabled.
        launch_cmd = self.local_image_local_instance.PrepareLaunchCVDCmd(
            None, True, mock_artifact_paths, "fake_cvd_dir", False, True,
            None, None, "phone", openwrt=True, use_launch_cvd=True)
        self.assertEqual(launch_cmd, self.LAUNCH_CVD_CMD_WITH_OPENWRT)

        # Test with instance_ids
        launch_cmd = self.local_image_local_instance.PrepareLaunchCVDCmd(
            None, True, mock_artifact_paths, "fake_cvd_dir", False, True,
            None, None, "phone", instance_ids=[1,2])
        self.assertEqual(launch_cmd, self.LAUNCH_CVD_CMD_WITH_INS_IDS)

        # Test with "pet-name"
        launch_cmd = self.local_image_local_instance.PrepareLaunchCVDCmd(
            None, True, mock_artifact_paths, "fake_cvd_dir", False, True,
            None, None, "phone", webrtc_device_id="pet-name")
        self.assertEqual(launch_cmd, self.LAUNCH_CVD_CMD_WITH_PET_NAME)

        # Test with "cvd" doesn't exist
        self.Patch(os.path, "isfile", return_value=False)
        launch_cmd = self.local_image_local_instance.PrepareLaunchCVDCmd(
            None, True, mock_artifact_paths, "fake_cvd_dir", False, True,
            None, None, "phone", openwrt=False, use_launch_cvd=False)
        self.assertEqual(launch_cmd, self.LAUNCH_CVD_CMD_WITH_NO_CVD)

    @mock.patch("acloud.create.local_image_local_instance.subprocess.run")
    def testLogCvdVersion(self, mock_run):
        """Test _LogCvdVersion."""
        with tempfile.TemporaryDirectory() as temp_dir:
            # cvd does not exist in old versions.
            self.local_image_local_instance._LogCvdVersion(temp_dir)
            mock_run.assert_not_called()

            # cvd command completes.
            mock_run.return_value = mock.Mock(
                returncode=1, stdout=None, stderr="err")
            cvd_path = os.path.join(temp_dir, "bin", "cvd")
            self.CreateFile(cvd_path)
            self.local_image_local_instance._LogCvdVersion(temp_dir)
            mock_run.assert_called_once()
            self.assertEqual(mock_run.call_args[0][0], f"{cvd_path} version")

            # cvd cannot run.
            mock_run.reset_mock()
            mock_run.side_effect = subprocess.SubprocessError
            self.local_image_local_instance._LogCvdVersion(temp_dir)
            mock_run.assert_called_once()

    @mock.patch.object(utils, "GetUserAnswerYes")
    @mock.patch.object(list_instance, "GetActiveCVD")
    def testCheckRunningCvd(self, mock_cvd_running, mock_get_answer):
        """test _CheckRunningCvd."""
        local_instance_id = 3

        # Test that launch_cvd is running.
        mock_cvd_running.return_value = True
        mock_get_answer.return_value = False
        answer = self.local_image_local_instance._CheckRunningCvd(
            local_instance_id)
        self.assertFalse(answer)

        # Test that launch_cvd is not running.
        mock_cvd_running.return_value = False
        answer = self.local_image_local_instance._CheckRunningCvd(
            local_instance_id)
        self.assertTrue(answer)

    # pylint: disable=protected-access
    @mock.patch("acloud.create.local_image_local_instance.subprocess.Popen")
    @mock.patch.dict("os.environ", clear=True)
    def testLaunchCVD(self, mock_popen):
        """test _LaunchCvd should call subprocess.Popen with the env."""
        self.Patch(builtins, "open", mock.mock_open())
        local_instance_id = 3
        launch_cvd_cmd = "launch_cvd"
        host_bins_path = "host_bins_path"
        host_artifacts_path = "host_artifacts_path"
        cvd_home_dir = "fake_home"
        timeout = 100
        mock_proc = mock.Mock(returncode=0)
        mock_popen.return_value = mock_proc
        mock_proc.communicate.return_value = ("stdout", "stderr")

        self.local_image_local_instance._LaunchCvd(launch_cvd_cmd,
                                                   local_instance_id,
                                                   host_bins_path,
                                                   host_artifacts_path,
                                                   cvd_home_dir,
                                                   timeout)

        mock_popen.assert_called_once()
        mock_proc.communicate.assert_called_once_with(timeout=timeout)

    @mock.patch("acloud.create.local_image_local_instance.subprocess.Popen")
    def testLaunchCVDFailure(self, mock_popen):
        """test _LaunchCvd with subprocess errors."""
        self.Patch(builtins, "open", mock.mock_open())
        mock_proc = mock.Mock(returncode=9)
        mock_popen.return_value = mock_proc
        with self.assertRaises(errors.LaunchCVDFail) as launch_cvd_failure:
            self.local_image_local_instance._LaunchCvd("launch_cvd",
                                                       3,
                                                       "host_bins_path",
                                                       "host_artifacts_path",
                                                       "cvd_home_dir",
                                                       100)
        self.assertIn("returned 9", str(launch_cvd_failure.exception))

    @mock.patch("acloud.create.local_image_local_instance.list_instance")
    @mock.patch("acloud.create.local_image_local_instance.subprocess.Popen")
    def testLaunchCVDTimeout(self, mock_popen, mock_list_instance):
        """test _LaunchCvd with subprocess timeout."""
        self.Patch(builtins, "open", mock.mock_open())
        mock_proc = mock.Mock(returncode=255)
        mock_popen.return_value = mock_proc
        mock_proc.communicate.side_effect = [
            subprocess.TimeoutExpired(cmd="launch_cvd", timeout=100),
            ("stdout", "stderr")
        ]
        mock_instance = mock.Mock()
        mock_list_instance.GetActiveCVD.return_value = mock_instance
        mock_instance.Delete.side_effect = subprocess.CalledProcessError(
            cmd="stop_cvd", returncode=255)
        with self.assertRaises(errors.LaunchCVDFail) as launch_cvd_failure:
            self.local_image_local_instance._LaunchCvd("launch_cvd",
                                                       3,
                                                       "host_bins_path",
                                                       "host_artifacts_path",
                                                       "cvd_home_dir",
                                                       100)
        self.assertIn("100 secs", str(launch_cvd_failure.exception))
        mock_list_instance.GetActiveCVD.assert_called_with(3)
        mock_instance.Delete.assert_called()
        mock_proc.terminate.assert_called()

    def testGetWebrtcSigServerPort(self):
        """test GetWebrtcSigServerPort."""
        instance_id = 3
        expected_port = 8445
        self.assertEqual(
            self.local_image_local_instance.GetWebrtcSigServerPort(instance_id),
            expected_port)

    def testGetConfigFromAndroidInfo(self):
        """Test GetConfigFromAndroidInfo"""
        self.Patch(os.path, "exists", return_value=True)
        mock_open = mock.mock_open(read_data="config=phone")
        expected = "phone"
        with mock.patch("builtins.open", mock_open):
            self.assertEqual(
                self.local_image_local_instance._GetConfigFromAndroidInfo("file"),
                expected)


if __name__ == "__main__":
    unittest.main()
