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

"""Unit tests for RemoteHostGoldfishDeviceFactory."""

import os
import tempfile
import unittest
import zipfile

from unittest import mock

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import driver_test_lib
from acloud.public.actions import remote_host_gf_device_factory as gf_factory


class RemoteHostGoldfishDeviceFactoryTest(driver_test_lib.BaseDriverTest):
    """Unit tests for RemoteHostGoldfishDeviceFactory."""

    _EMULATOR_INFO = "require version-emulator=111111\n"
    _X86_64_BUILD_INFO = {
        constants.BUILD_ID: "123456",
        constants.BUILD_TARGET: "sdk_x86_64-sdk",
    }
    _X86_64_INSTANCE_NAME = (
        "host-goldfish-192.0.2.1-5554-123456-sdk_x86_64-sdk")
    _ARM64_BUILD_INFO = {
        constants.BUILD_ID: "123456",
        constants.BUILD_TARGET: "sdk_arm64-sdk",
    }
    _ARM64_INSTANCE_NAME = (
        "host-goldfish-192.0.2.1-5556-123456-sdk_arm64-sdk")
    _CFG_ATTRS = {
        "ssh_private_key_path": "cfg_key_path",
        "extra_args_ssh_tunnel": "extra args",
        "emulator_build_target": "emulator-linux_x64_nolocationui",
    }
    _AVD_SPEC_ATTRS = {
        "cfg": None,
        "image_source": constants.IMAGE_SRC_REMOTE,
        "remote_image": _X86_64_BUILD_INFO,
        "image_download_dir": None,
        "host_user": "user",
        "remote_host": "192.0.2.1",
        "host_ssh_private_key_path": None,
        "emulator_build_id": None,
        "emulator_build_target": None,
        "emulator_zip": None,
        "system_build_info": {},
        "boot_build_info": {},
        "local_image_artifact": None,
        "local_kernel_image": None,
        "local_system_image": None,
        "local_system_dlkm_image": None,
        "local_tool_dirs": [],
        "base_instance_num": None,
        "boot_timeout_secs": None,
        "hw_customize": False,
        "hw_property": {},
        "gpu": "auto",
    }
    _LOGS = [{"path": "acloud_gf_1/instance/kernel.log", "type": "KERNEL_LOG"},
             {"path": "acloud_gf_1/instance/emu_stderr.txt", "type": "TEXT"},
             {"path": "acloud_gf_1/instance/logcat.txt", "type": "LOGCAT"}]
    _VERIFIED_BOOT_PARAMS_COMMAND = (
        "'test -f acloud_gf_1/image/x86_64/VerifiedBootParams.textproto && "
        r'echo -e \\nparam: \"androidboot.verifiedbootstate=orange\" >> '
        "acloud_gf_1/image/x86_64/VerifiedBootParams.textproto'"
    )
    _SSH_COMMAND = (
        "'export ANDROID_PRODUCT_OUT=~/acloud_gf_1/image/x86_64 "
        "ANDROID_TMP=~/acloud_gf_1/instance "
        "ANDROID_BUILD_TOP=~/acloud_gf_1/instance ; "
        "nohup acloud_gf_1/emulator/emulator/emulator -verbose "
        "-show-kernel -read-only -ports 5554,5555 -no-window "
        "-logcat-output acloud_gf_1/instance/logcat.txt -gpu auto "
        "1> acloud_gf_1/instance/kernel.log "
        "2> acloud_gf_1/instance/emu_stderr.txt &'"
    )

    def setUp(self):
        super().setUp()
        self._mock_ssh = mock.Mock()
        self.Patch(gf_factory.ssh, "Ssh", return_value=self._mock_ssh)
        self._mock_remote_host_client = mock.Mock()
        self.Patch(gf_factory.remote_host_client, "RemoteHostClient",
                   return_value=self._mock_remote_host_client)
        self._mock_create_credentials = self.Patch(
            gf_factory.auth, "CreateCredentials")
        # Emulator console
        self._mock_console = mock.MagicMock()
        self._mock_console.__enter__.return_value = self._mock_console
        self._mock_console.Kill.side_effect = errors.DeviceConnectionError
        ping_results = [False, True]
        self._mock_poll_and_wait = self.Patch(
            gf_factory.utils,
            "PollAndWait",
            side_effect=lambda func, **kwargs: [func() for _ in ping_results])
        self._mock_console.Ping.side_effect = ping_results
        self.Patch(gf_factory.emulator_console, "RemoteEmulatorConsole",
                   return_value=self._mock_console)
        # Android build client.
        self._mock_android_build_client = mock.Mock()
        self._mock_android_build_client.DownloadArtifact.side_effect = (
            self._MockDownloadArtifact)
        self.Patch(gf_factory.android_build_client, "AndroidBuildClient",
                   return_value=self._mock_android_build_client)
        # AVD spec.
        mock_cfg = mock.Mock(spec=list(self._CFG_ATTRS.keys()),
                             **self._CFG_ATTRS)
        self._mock_avd_spec = mock.Mock(spec=list(self._AVD_SPEC_ATTRS.keys()),
                                        **self._AVD_SPEC_ATTRS)
        self._mock_avd_spec.cfg = mock_cfg

    @staticmethod
    def _CreateSdkRepoZip(path):
        """Create a zip file that contains a subdirectory."""
        with zipfile.ZipFile(path, "w") as zip_file:
            zip_file.writestr("x86_64/build.prop", "")
            zip_file.writestr("x86_64/test", "")

    @staticmethod
    def _CreateImageZip(path):
        """Create a zip file containing images."""
        with zipfile.ZipFile(path, "w") as zip_file:
            zip_file.writestr("system.img", "")

    def _MockDownloadArtifact(self, _build_target, _build_id, resource_id,
                              local_path, _attempt):
        if resource_id.endswith(".zip"):
            if (resource_id.startswith("sdk-repo-") or
                    resource_id.startswith("emu-extra-")):
                self._CreateSdkRepoZip(local_path)
            else:
                self._CreateImageZip(local_path)
        elif resource_id == "emulator-info.txt":
            with open(local_path, "w", encoding="utf-8") as file:
                file.write(self._EMULATOR_INFO)
        else:
            with open(local_path, "w", encoding="utf-8") as file:
                pass

    def testCreateInstanceWithCfg(self):
        """Test RemoteHostGoldfishDeviceFactory with default config."""
        factory = gf_factory.RemoteHostGoldfishDeviceFactory(
            self._mock_avd_spec)
        instance_name = factory.CreateInstance()

        self.assertEqual(self._X86_64_INSTANCE_NAME, instance_name)
        self.assertEqual(self._X86_64_BUILD_INFO, factory.GetBuildInfoDict())
        self.assertEqual([5555], factory.GetAdbPorts())
        self.assertEqual([None], factory.GetVncPorts())
        self.assertEqual({}, factory.GetFailures())
        self.assertEqual({instance_name: self._LOGS}, factory.GetLogs())
        # Artifacts.
        self._mock_android_build_client.DownloadArtifact.assert_any_call(
            "emulator-linux_x64_nolocationui", "111111",
            "sdk-repo-linux-emulator-111111.zip", mock.ANY, mock.ANY)
        self._mock_android_build_client.DownloadArtifact.assert_any_call(
            "sdk_x86_64-sdk", "123456",
            "sdk-repo-linux-system-images-123456.zip", mock.ANY, mock.ANY)
        self._mock_android_build_client.DownloadArtifact.assert_any_call(
            "sdk_x86_64-sdk", "123456",
            "emulator-info.txt", mock.ANY, mock.ANY)
        self.assertEqual(
            3, self._mock_android_build_client.DownloadArtifact.call_count)
        # Commands.
        self._mock_ssh.Run.assert_called_with(self._SSH_COMMAND)
        self._mock_console.Kill.assert_called_once()
        self.assertEqual(self._mock_console.Ping.call_count,
                         self._mock_console.Reconnect.call_count + 1)
        self._mock_console.Reconnect.assert_called()
        # RemoteHostClient.
        self._mock_remote_host_client.RecordTime.assert_has_calls([
            mock.call(constants.TIME_GCE, mock.ANY),
            mock.call(constants.TIME_ARTIFACT, mock.ANY),
            mock.call(constants.TIME_LAUNCH, mock.ANY)])
        self.assertEqual(3,
                         self._mock_remote_host_client.RecordTime.call_count)
        self._mock_remote_host_client.SetStage.assert_has_calls([
            mock.call(constants.STAGE_SSH_CONNECT),
            mock.call(constants.STAGE_ARTIFACT),
            mock.call(constants.STAGE_BOOT_UP)])
        self.assertEqual(3, self._mock_remote_host_client.SetStage.call_count)

    def testCreateInstanceWithAvdSpec(self):
        """Test RemoteHostGoldfishDeviceFactory with command options."""
        self._mock_avd_spec.remote_image = self._ARM64_BUILD_INFO
        self._mock_avd_spec.host_ssh_private_key_path = "key_path"
        self._mock_avd_spec.emulator_build_id = "999999"
        self._mock_avd_spec.emulator_build_target = "aarch64_sdk_tools_mac"
        self._mock_avd_spec.base_instance_num = 2
        self._mock_avd_spec.boot_timeout_secs = 1
        self._mock_avd_spec.hw_customize = True
        self._mock_avd_spec.hw_property = {"disk": "4096"}
        self._mock_create_credentials.side_effect = AssertionError(
            "CreateCredentials should not be called.")
        # All artifacts are cached.
        with tempfile.TemporaryDirectory() as download_dir:
            self._mock_avd_spec.image_download_dir = download_dir
            artifact_paths = (
                os.path.join(download_dir, "999999",
                             "aarch64_sdk_tools_mac",
                             "sdk-repo-darwin_aarch64-emulator-999999.zip"),
                os.path.join(download_dir, "123456",
                             "sdk_arm64-sdk",
                             "sdk-repo-linux-system-images-123456.zip"),
            )
            for artifact_path in artifact_paths:
                os.makedirs(os.path.dirname(artifact_path), exist_ok=True)
                self._CreateSdkRepoZip(artifact_path)

            factory = gf_factory.RemoteHostGoldfishDeviceFactory(
                self._mock_avd_spec)
            instance_name = factory.CreateInstance()

        self.assertEqual(self._ARM64_INSTANCE_NAME, instance_name)
        self.assertEqual(self._ARM64_BUILD_INFO, factory.GetBuildInfoDict())
        self.assertEqual([5557], factory.GetAdbPorts())
        self.assertEqual([None], factory.GetVncPorts())
        self.assertEqual({}, factory.GetFailures())

    @mock.patch("acloud.public.actions.remote_host_gf_device_factory."
                "goldfish_utils")
    def testCreateInstanceWithSystemBuild(self, mock_gf_utils):
        """Test RemoteHostGoldfishDeviceFactory with system build."""
        self._mock_avd_spec.system_build_info = {
            constants.BUILD_ID: "111111",
            constants.BUILD_TARGET: "aosp_x86_64-userdebug"}
        mock_gf_utils.ConvertAvdSpecToArgs.return_value = ["-gpu", "auto"]
        mock_gf_utils.MixDiskImage.return_value = "/mixed/disk"
        mock_gf_utils.SYSTEM_QEMU_IMAGE_NAME = "system-qemu.img"
        mock_gf_utils.VERIFIED_BOOT_PARAMS_FILE_NAME = (
            "VerifiedBootParams.textproto")

        factory = gf_factory.RemoteHostGoldfishDeviceFactory(
            self._mock_avd_spec)
        factory.CreateInstance()
        # Artifacts.
        self._mock_android_build_client.DownloadArtifact.assert_any_call(
            "sdk_x86_64-sdk", "123456",
            "emu-extra-linux-system-images-123456.zip", mock.ANY, mock.ANY)
        self._mock_android_build_client.DownloadArtifact.assert_any_call(
            "aosp_x86_64-userdebug", "111111",
            "aosp_x86_64-img-111111.zip", mock.ANY, mock.ANY)
        self._mock_android_build_client.DownloadArtifact.assert_any_call(
            "sdk_x86_64-sdk", "123456",
            "otatools.zip", mock.ANY, mock.ANY)
        self.assertEqual(
            5, self._mock_android_build_client.DownloadArtifact.call_count)
        # Images.
        mock_gf_utils.MixDiskImage.assert_called_once()
        self._mock_ssh.ScpPushFile.assert_called_with(
            "/mixed/disk", "acloud_gf_1/image/x86_64/system-qemu.img")
        self._mock_ssh.Run.assert_any_call(self._VERIFIED_BOOT_PARAMS_COMMAND)

        mock_gf_utils.FormatRemoteHostInstanceName.assert_called()
        self.assertEqual(self._X86_64_BUILD_INFO, factory.GetBuildInfoDict())
        self.assertEqual([5555], factory.GetAdbPorts())
        self.assertEqual([None], factory.GetVncPorts())
        self.assertEqual({}, factory.GetFailures())

    @mock.patch("acloud.public.actions.remote_host_gf_device_factory."
                "goldfish_utils")
    def testCreateInstanceWithBootBuild(self, mock_gf_utils):
        """Test RemoteHostGoldfishDeviceFactory with boot build."""
        self._mock_avd_spec.boot_build_info = {
            constants.BUILD_ID: "111111",
            constants.BUILD_TARGET: "gki_x86_64-userdebug",
            constants.BUILD_ARTIFACT: "boot-5.10.img"}
        mock_gf_utils.ConvertAvdSpecToArgs.return_value = ["-gpu", "auto"]
        mock_gf_utils.MixWithBootImage.return_value = (
            "/path/to/kernel", "/path/to/ramdisk")

        factory = gf_factory.RemoteHostGoldfishDeviceFactory(
            self._mock_avd_spec)
        factory.CreateInstance()
        # Artifacts.
        self._mock_android_build_client.DownloadArtifact.assert_any_call(
            "sdk_x86_64-sdk", "123456",
            "sdk-repo-linux-system-images-123456.zip", mock.ANY, mock.ANY)
        self._mock_android_build_client.DownloadArtifact.assert_any_call(
            "gki_x86_64-userdebug", "111111",
            "boot-5.10.img", mock.ANY, mock.ANY)
        self._mock_android_build_client.DownloadArtifact.assert_any_call(
            "sdk_x86_64-sdk", "123456",
            "otatools.zip", mock.ANY, mock.ANY)
        self.assertEqual(
            5, self._mock_android_build_client.DownloadArtifact.call_count)
        # Images.
        mock_gf_utils.MixWithBootImage.assert_called_once()
        self._mock_ssh.ScpPushFile.assert_any_call(
            "/path/to/kernel", "acloud_gf_1/kernel")
        self._mock_ssh.ScpPushFile.assert_any_call(
            "/path/to/ramdisk", "acloud_gf_1/mixed_ramdisk")

        mock_gf_utils.FormatRemoteHostInstanceName.assert_called()
        self.assertEqual(self._X86_64_BUILD_INFO, factory.GetBuildInfoDict())
        self.assertEqual([5555], factory.GetAdbPorts())
        self.assertEqual([None], factory.GetVncPorts())
        self.assertEqual({}, factory.GetFailures())

    @mock.patch("acloud.public.actions.remote_host_gf_device_factory."
                "ota_tools")
    @mock.patch("acloud.public.actions.remote_host_gf_device_factory."
                "goldfish_utils")
    def testCreateInstanceWithLocalFiles(self, mock_gf_utils, mock_ota_tools):
        """Test RemoteHostGoldfishDeviceFactory with local files."""
        with tempfile.TemporaryDirectory() as temp_dir:
            emulator_zip_path = os.path.join(temp_dir, "emulator.zip")
            self._CreateSdkRepoZip(emulator_zip_path)
            image_zip_path = os.path.join(temp_dir, "image.zip")
            self._CreateSdkRepoZip(image_zip_path)
            boot_image_path = os.path.join(temp_dir, "boot.img")
            self.CreateFile(boot_image_path, b"ANDROID!")
            system_image_path = os.path.join(temp_dir, "system.img")
            self.CreateFile(system_image_path)
            system_dlkm_image_path = os.path.join(temp_dir, "system_dlkm.img")
            self.CreateFile(system_dlkm_image_path)
            self._mock_avd_spec.emulator_zip = emulator_zip_path
            self._mock_avd_spec.image_source = constants.IMAGE_SRC_LOCAL
            self._mock_avd_spec.remote_image = {}
            self._mock_avd_spec.local_image_artifact = image_zip_path
            self._mock_avd_spec.local_kernel_image = boot_image_path
            self._mock_avd_spec.local_system_image = system_image_path
            self._mock_avd_spec.local_system_dlkm_image = (
                system_dlkm_image_path)
            self._mock_avd_spec.local_tool_dirs.append("/otatools")
            mock_gf_utils.ConvertAvdSpecToArgs.return_value = ["-gpu", "auto"]
            mock_gf_utils.MixWithBootImage.return_value = (
                "/path/to/kernel", "/path/to/ramdisk")
            self._mock_create_credentials.side_effect = AssertionError(
                "CreateCredentials should not be called.")

            factory = gf_factory.RemoteHostGoldfishDeviceFactory(
                self._mock_avd_spec)
            factory.CreateInstance()

            mock_gf_utils.FindSystemDlkmImage.assert_called_once()
            mock_gf_utils.MixWithBootImage.assert_called_once()
            mock_gf_utils.MixDiskImage.assert_called_once()
            mock_ota_tools.FindOtaToolsDir.assert_called_once()
            self.assertEqual("/otatools",
                             mock_ota_tools.FindOtaToolsDir.call_args[0][0][0])

            mock_gf_utils.FormatRemoteHostInstanceName.assert_called()
            self.assertEqual({}, factory.GetBuildInfoDict())
            self.assertEqual([5555], factory.GetAdbPorts())
            self.assertEqual([None], factory.GetVncPorts())
            self.assertEqual({}, factory.GetFailures())

    def testCreateInstanceInitError(self):
        """Test RemoteHostGoldfishDeviceFactory with SSH error."""
        self._mock_ssh.Run.side_effect = errors.DeviceConnectionError

        factory = gf_factory.RemoteHostGoldfishDeviceFactory(
            self._mock_avd_spec)
        factory.CreateInstance()

        failures = factory.GetFailures()
        self.assertIsInstance(failures.get(self._X86_64_INSTANCE_NAME),
                              errors.DeviceConnectionError)
        self.assertEqual({}, factory.GetLogs())
        self._mock_remote_host_client.RecordTime.assert_called_once_with(
            constants.TIME_GCE, mock.ANY)
        self._mock_remote_host_client.SetStage.assert_called_once_with(
            constants.STAGE_SSH_CONNECT)

    def testCreateInstanceDownloadError(self):
        """Test RemoteHostGoldfishDeviceFactory with download error."""
        self._mock_android_build_client.DownloadArtifact.side_effect = (
            errors.DriverError)

        factory = gf_factory.RemoteHostGoldfishDeviceFactory(
            self._mock_avd_spec)
        factory.CreateInstance()

        failures = factory.GetFailures()
        self.assertIsInstance(failures.get(self._X86_64_INSTANCE_NAME),
                              errors.DriverError)
        self.assertEqual({}, factory.GetLogs())
        self._mock_remote_host_client.RecordTime.assert_has_calls([
            mock.call(constants.TIME_GCE, mock.ANY),
            mock.call(constants.TIME_ARTIFACT, mock.ANY)])
        self.assertEqual(2,
                         self._mock_remote_host_client.RecordTime.call_count)
        self._mock_remote_host_client.SetStage.assert_has_calls([
            mock.call(constants.STAGE_SSH_CONNECT),
            mock.call(constants.STAGE_ARTIFACT)])
        self.assertEqual(2, self._mock_remote_host_client.SetStage.call_count)

    def testCreateInstanceBootError(self):
        """Test RemoteHostGoldfishDeviceFactory with boot error."""
        self._mock_console.Reconnect.side_effect = (
            errors.DeviceConnectionError)

        factory = gf_factory.RemoteHostGoldfishDeviceFactory(
            self._mock_avd_spec)
        factory.CreateInstance()

        failures = factory.GetFailures()
        self.assertIsInstance(failures.get(self._X86_64_INSTANCE_NAME),
                              errors.DeviceBootError)
        self.assertEqual({self._X86_64_INSTANCE_NAME: self._LOGS},
                         factory.GetLogs())
        self.assertEqual(3,
                         self._mock_remote_host_client.RecordTime.call_count)
        self.assertEqual(3, self._mock_remote_host_client.SetStage.call_count)

    def testCreateInstanceTimeout(self):
        """Test RemoteHostGoldfishDeviceFactory with timeout."""
        self._mock_avd_spec.boot_timeout_secs = 1
        self._mock_poll_and_wait.side_effect = errors.DeviceBootTimeoutError()

        factory = gf_factory.RemoteHostGoldfishDeviceFactory(
            self._mock_avd_spec)
        factory.CreateInstance()

        self._mock_poll_and_wait.assert_called_once_with(
            func=mock.ANY,
            expected_return=True,
            timeout_exception=errors.DeviceBootTimeoutError,
            timeout_secs=1,
            sleep_interval_secs=mock.ANY)
        failures = factory.GetFailures()
        self.assertIsInstance(failures.get(self._X86_64_INSTANCE_NAME),
                              errors.DeviceBootTimeoutError)
        self.assertEqual({self._X86_64_INSTANCE_NAME: self._LOGS},
                         factory.GetLogs())
        self.assertEqual(3,
                         self._mock_remote_host_client.RecordTime.call_count)
        self.assertEqual(3, self._mock_remote_host_client.SetStage.call_count)


if __name__ == "__main__":
    unittest.main()
