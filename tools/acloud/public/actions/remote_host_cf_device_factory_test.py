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

"""Tests for remote_host_cf_device_factory."""

import time
import unittest
from unittest import mock

from acloud.internal import constants
from acloud.internal.lib import driver_test_lib
from acloud.public.actions import remote_host_cf_device_factory

class RemoteHostDeviceFactoryTest(driver_test_lib.BaseDriverTest):
    """Test RemoteHostDeviceFactory."""

    def setUp(self):
        """Set up the test."""
        super().setUp()
        self.Patch(remote_host_cf_device_factory.auth, "CreateCredentials")
        mock_client = mock.Mock()
        self.Patch(remote_host_cf_device_factory.remote_host_client,
                   "RemoteHostClient", return_value=mock_client)
        mock_client.RecordTime.side_effect = (
            lambda _stage, _start_time: time.time())
        self._mock_build_api = mock.Mock()
        self.Patch(remote_host_cf_device_factory.android_build_client,
                   "AndroidBuildClient", return_value=self._mock_build_api)

    @staticmethod
    def _CreateMockAvdSpec():
        """Create a mock AvdSpec with necessary attributes."""
        mock_cfg = mock.Mock(spec=[],
                             ssh_private_key_path="/mock/id_rsa",
                             extra_args_ssh_tunnel="extra args",
                             fetch_cvd_version="123456",
                             creds_cache_file="credential",
                             service_account_json_private_key_path="/mock/key")
        return mock.Mock(spec=[],
                         remote_image={
                             "branch": "aosp-android12-gsi",
                             "build_id": "100000",
                             "build_target": "aosp_cf_x86_64_phone-userdebug"},
                         system_build_info={},
                         kernel_build_info={},
                         boot_build_info={},
                         bootloader_build_info={},
                         ota_build_info={},
                         host_package_build_info={},
                         remote_host="192.0.2.100",
                         remote_image_dir=None,
                         host_user="user1",
                         host_ssh_private_key_path=None,
                         report_internal_ip=False,
                         image_source=constants.IMAGE_SRC_REMOTE,
                         local_image_dir=None,
                         ins_timeout_secs=200,
                         boot_timeout_secs=100,
                         gpu="auto",
                         no_pull_log=False,
                         remote_fetch=False,
                         fetch_cvd_wrapper=None,
                         base_instance_num=None,
                         num_avds_per_instance=None,
                         fetch_cvd_version="123456",
                         openwrt=True,
                         cfg=mock_cfg)

    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.ssh")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory."
                "cvd_utils")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.pull")
    def testCreateInstanceWithImageDir(self, mock_pull, mock_cvd_utils,
                                       mock_ssh):
        """Test CreateInstance with local image directory."""
        mock_avd_spec = self._CreateMockAvdSpec()
        mock_avd_spec.image_source = constants.IMAGE_SRC_LOCAL
        mock_avd_spec.local_image_dir = "/mock/target_files"
        mock_avd_spec.base_instance_num = 2
        mock_avd_spec.num_avds_per_instance = 3
        mock_ssh_obj = mock.Mock()
        mock_ssh.Ssh.return_value = mock_ssh_obj
        factory = remote_host_cf_device_factory.RemoteHostDeviceFactory(
            mock_avd_spec, cvd_host_package_artifact="/mock/cvd.tar.gz")

        log = {"path": "/log.txt"}
        mock_cvd_utils.GetRemoteHostBaseDir.return_value = "acloud_cf_2"
        mock_cvd_utils.FormatRemoteHostInstanceName.return_value = "inst"
        mock_cvd_utils.AreTargetFilesRequired.return_value = True
        mock_cvd_utils.UploadExtraImages.return_value = ["extra"]
        mock_cvd_utils.ExecuteRemoteLaunchCvd.return_value = "failure"
        mock_cvd_utils.FindRemoteLogs.return_value = [log]

        self.assertEqual("inst", factory.CreateInstance())
        # InitRemotehost
        mock_cvd_utils.CleanUpRemoteCvd.assert_called_once_with(
            mock_ssh_obj, "acloud_cf_2", raise_error=False)
        mock_cvd_utils.GetRemoteHostBaseDir.assert_called_with(2)
        # ProcessRemoteHostArtifacts
        mock_ssh_obj.Run.assert_called_with("mkdir -p acloud_cf_2")
        self._mock_build_api.GetFetchBuildArgs.assert_not_called()
        mock_cvd_utils.UploadArtifacts.assert_called_with(
            mock_ssh_obj, "acloud_cf_2", "/mock/target_files",
            "/mock/cvd.tar.gz")
        mock_cvd_utils.UploadExtraImages.assert_called_with(
            mock_ssh_obj, "acloud_cf_2", mock_avd_spec, "/mock/target_files")
        mock_cvd_utils.GetConfigFromRemoteAndroidInfo.assert_called_with(
            mock_ssh_obj, "acloud_cf_2")
        # LaunchCvd
        mock_cvd_utils.GetRemoteLaunchCvdCmd.assert_called_with(
            "acloud_cf_2", mock_avd_spec, mock.ANY, ["extra"])
        mock_cvd_utils.ExecuteRemoteLaunchCvd.assert_called()
        # FindLogFiles
        mock_cvd_utils.FindRemoteLogs.assert_called_with(
            mock_ssh_obj, "acloud_cf_2", 2, 3)
        mock_pull.GetAllLogFilePaths.assert_called_once()
        mock_pull.PullLogs.assert_called_once()
        factory.GetAdbPorts()
        mock_cvd_utils.GetAdbPorts.assert_called_with(2, 3)
        factory.GetVncPorts()
        mock_cvd_utils.GetVncPorts.assert_called_with(2, 3)
        self.assertEqual({"inst": "failure"}, factory.GetFailures())
        self.assertDictEqual({"inst": [log]}, factory.GetLogs())

    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.ssh")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory."
                "cvd_utils")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.pull")
    def testCreateInstanceWithImageZip(self, mock_pull, mock_cvd_utils,
                                       mock_ssh):
        """Test CreateInstance with local image zip."""
        mock_avd_spec = self._CreateMockAvdSpec()
        mock_avd_spec.image_source = constants.IMAGE_SRC_LOCAL
        mock_ssh_obj = mock.Mock()
        mock_ssh.Ssh.return_value = mock_ssh_obj
        factory = remote_host_cf_device_factory.RemoteHostDeviceFactory(
            mock_avd_spec, local_image_artifact="/mock/img.zip",
            cvd_host_package_artifact="/mock/cvd.tar.gz")

        mock_cvd_utils.GetRemoteHostBaseDir.return_value = "acloud_cf_1"
        mock_cvd_utils.FormatRemoteHostInstanceName.return_value = "inst"
        mock_cvd_utils.AreTargetFilesRequired.return_value = False
        mock_cvd_utils.ExecuteRemoteLaunchCvd.return_value = ""
        mock_cvd_utils.FindRemoteLogs.return_value = []

        self.assertEqual("inst", factory.CreateInstance())
        # InitRemotehost
        mock_cvd_utils.GetRemoteHostBaseDir.assert_called_with(None)
        mock_cvd_utils.CleanUpRemoteCvd.assert_called_once()
        # ProcessRemoteHostArtifacts
        mock_ssh_obj.Run.assert_called_with("mkdir -p acloud_cf_1")
        self._mock_build_api.GetFetchBuildArgs.assert_not_called()
        mock_cvd_utils.UploadArtifacts.assert_called_with(
            mock_ssh_obj, "acloud_cf_1", "/mock/img.zip", "/mock/cvd.tar.gz")
        mock_cvd_utils.UploadExtraImages.assert_called_with(
            mock_ssh_obj, "acloud_cf_1", mock_avd_spec, None)
        # LaunchCvd
        mock_cvd_utils.ExecuteRemoteLaunchCvd.assert_called()
        # FindLogFiles
        mock_cvd_utils.FindRemoteLogs.assert_called_with(
            mock_ssh_obj, "acloud_cf_1", None, None)
        mock_pull.GetAllLogFilePaths.assert_not_called()
        mock_pull.PullLogs.assert_not_called()
        factory.GetAdbPorts()
        mock_cvd_utils.GetAdbPorts.assert_called_with(None, None)
        factory.GetVncPorts()
        mock_cvd_utils.GetVncPorts.assert_called_with(None, None)
        self.assertFalse(factory.GetFailures())
        self.assertDictEqual({"inst": []}, factory.GetLogs())

    # pylint: disable=invalid-name
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.ssh")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory."
                "cvd_utils")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.pull")
    def testCreateInstanceWithTargetFilesZip(self, mock_pull, mock_cvd_utils,
                                             mock_ssh):
        """Test CreateInstance with local target_files zip."""
        mock_avd_spec = self._CreateMockAvdSpec()
        mock_avd_spec.image_source = constants.IMAGE_SRC_LOCAL
        mock_ssh_obj = mock.Mock()
        mock_ssh.Ssh.return_value = mock_ssh_obj
        factory = remote_host_cf_device_factory.RemoteHostDeviceFactory(
            mock_avd_spec, local_image_artifact="/mock/target_files.zip",
            cvd_host_package_artifact="/mock/cvd.tar.gz")

        mock_cvd_utils.GetRemoteHostBaseDir.return_value = "acloud_cf_1"
        mock_cvd_utils.FormatRemoteHostInstanceName.return_value = "inst"
        mock_cvd_utils.AreTargetFilesRequired.return_value = True
        mock_cvd_utils.ExecuteRemoteLaunchCvd.return_value = ""
        mock_cvd_utils.FindRemoteLogs.return_value = []

        self.assertEqual("inst", factory.CreateInstance())
        # InitRemotehost
        mock_cvd_utils.GetRemoteHostBaseDir.assert_called_with(None)
        mock_cvd_utils.CleanUpRemoteCvd.assert_called_once()
        # ProcessRemoteHostArtifacts
        mock_ssh_obj.Run.assert_called_with("mkdir -p acloud_cf_1")
        mock_cvd_utils.ExtractTargetFilesZip.assert_called_with(
            "/mock/target_files.zip", mock.ANY)
        self._mock_build_api.GetFetchBuildArgs.assert_not_called()
        mock_cvd_utils.UploadExtraImages.assert_called_with(
            mock_ssh_obj, "acloud_cf_1", mock_avd_spec, mock.ANY)
        mock_cvd_utils.UploadArtifacts.assert_called_with(
            mock_ssh_obj, "acloud_cf_1", mock.ANY, "/mock/cvd.tar.gz")
        self.assertIn("acloud_remote_host",  # temp dir prefix
                      mock_cvd_utils.UploadArtifacts.call_args[0][2])
        # LaunchCvd
        mock_cvd_utils.ExecuteRemoteLaunchCvd.assert_called()
        # FindLogFiles
        mock_cvd_utils.FindRemoteLogs.assert_called_with(
            mock_ssh_obj, "acloud_cf_1", None, None)
        mock_pull.GetAllLogFilePaths.assert_not_called()
        mock_pull.PullLogs.assert_not_called()
        factory.GetAdbPorts()
        mock_cvd_utils.GetAdbPorts.assert_called_with(None, None)
        factory.GetVncPorts()
        mock_cvd_utils.GetVncPorts.assert_called_with(None, None)
        self.assertFalse(factory.GetFailures())
        self.assertDictEqual({"inst": []}, factory.GetLogs())

    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.ssh")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory."
                "cvd_utils")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory."
                "subprocess.check_call")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.glob")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.pull")
    def testCreateInstanceWithRemoteImages(self, mock_pull, mock_glob,
                                           mock_check_call, mock_cvd_utils,
                                           mock_ssh):
        """Test CreateInstance with remote images."""
        mock_avd_spec = self._CreateMockAvdSpec()
        mock_avd_spec.image_source = constants.IMAGE_SRC_REMOTE
        mock_ssh_obj = mock.Mock()
        mock_ssh.Ssh.return_value = mock_ssh_obj
        mock_ssh_obj.GetBaseCmd.return_value = "/mock/ssh"
        mock_glob.glob.return_value = ["/mock/super.img"]
        factory = remote_host_cf_device_factory.RemoteHostDeviceFactory(
            mock_avd_spec)

        mock_cvd_utils.GetRemoteHostBaseDir.return_value = "acloud_cf_1"
        mock_cvd_utils.FormatRemoteHostInstanceName.return_value = "inst"
        mock_cvd_utils.AreTargetFilesRequired.return_value = True
        mock_cvd_utils.GetMixBuildTargetFilename.return_value = "mock.zip"
        mock_cvd_utils.ExecuteRemoteLaunchCvd.return_value = ""
        mock_cvd_utils.FindRemoteLogs.return_value = []

        self._mock_build_api.GetFetchBuildArgs.return_value = ["-test"]

        self.assertEqual("inst", factory.CreateInstance())
        # InitRemoteHost
        mock_cvd_utils.CleanUpRemoteCvd.assert_called_once()
        # ProcessRemoteHostArtifacts
        mock_ssh_obj.Run.assert_called_with("mkdir -p acloud_cf_1")
        self._mock_build_api.DownloadArtifact.assert_called_once_with(
            "aosp_cf_x86_64_phone-userdebug", "100000", "mock.zip", mock.ANY)
        mock_cvd_utils.ExtractTargetFilesZip.assert_called_once()
        self._mock_build_api.DownloadFetchcvd.assert_called_once()
        mock_check_call.assert_called_once()
        mock_ssh.ShellCmdWithRetry.assert_called_once()
        self.assertRegex(mock_ssh.ShellCmdWithRetry.call_args[0][0],
                         r"^tar -cf - --lzop -S -C \S+ super\.img \| "
                         r"/mock/ssh -- tar -xf - --lzop -S -C acloud_cf_1$")
        # LaunchCvd
        mock_cvd_utils.ExecuteRemoteLaunchCvd.assert_called()
        # FindLogFiles
        mock_pull.GetAllLogFilePaths.assert_not_called()
        mock_pull.PullLogs.assert_not_called()
        self.assertFalse(factory.GetFailures())
        self.assertDictEqual({"inst": []}, factory.GetLogs())

    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.ssh")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory."
                "cvd_utils")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.glob")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.shutil")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.pull")
    def testCreateInstanceWithRemoteFetch(self, mock_pull, mock_shutil,
                                          mock_glob, mock_cvd_utils, mock_ssh):
        """Test CreateInstance with remotely fetched images."""
        mock_avd_spec = self._CreateMockAvdSpec()
        mock_avd_spec.remote_fetch = True
        mock_ssh_obj = mock.Mock()
        mock_ssh.Ssh.return_value = mock_ssh_obj
        mock_ssh_obj.GetBaseCmd.return_value = "/mock/ssh"
        mock_glob.glob.return_value = ["/mock/fetch_cvd"]
        factory = remote_host_cf_device_factory.RemoteHostDeviceFactory(
            mock_avd_spec)

        log = {"path": "/log.txt"}
        mock_cvd_utils.GetRemoteHostBaseDir.return_value = "acloud_cf_1"
        mock_cvd_utils.FormatRemoteHostInstanceName.return_value = "inst"
        mock_cvd_utils.AreTargetFilesRequired.return_value = False
        mock_cvd_utils.ExecuteRemoteLaunchCvd.return_value = ""
        mock_cvd_utils.FindRemoteLogs.return_value = []
        mock_cvd_utils.GetRemoteFetcherConfigJson.return_value = log

        self._mock_build_api.GetFetchBuildArgs.return_value = ["-test"]

        self.assertEqual("inst", factory.CreateInstance())
        mock_cvd_utils.CleanUpRemoteCvd.assert_called_once()
        mock_ssh_obj.Run.assert_called_with("mkdir -p acloud_cf_1")
        self._mock_build_api.DownloadFetchcvd.assert_called_once()
        mock_shutil.copyfile.assert_called_with("/mock/key", mock.ANY)
        self.assertRegex(mock_ssh.ShellCmdWithRetry.call_args_list[0][0][0],
                         r"^tar -cf - --lzop -S -C \S+ fetch_cvd \| "
                         r"/mock/ssh -- tar -xf - --lzop -S -C acloud_cf_1$")
        self.assertRegex(mock_ssh.ShellCmdWithRetry.call_args_list[1][0][0],
                         r"^/mock/ssh -- acloud_cf_1/fetch_cvd "
                         r"-directory=acloud_cf_1 "
                         r"-credential_source=acloud_cf_1/credential_key.json "
                         r"-test$")
        mock_cvd_utils.ExecuteRemoteLaunchCvd.assert_called()
        mock_pull.GetAllLogFilePaths.assert_not_called()
        mock_pull.PullLogs.assert_not_called()
        self.assertFalse(factory.GetFailures())
        self.assertDictEqual({"inst": [log]}, factory.GetLogs())

    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.ssh")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory."
                "cvd_utils")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.glob")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.shutil")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.pull")
    def testCreateInstanceWithFetchCvdWrapper(self, mock_pull, mock_shutil,
                                              mock_glob, mock_cvd_utils,
                                              mock_ssh):
        """Test CreateInstance with remotely fetched images."""
        mock_avd_spec = self._CreateMockAvdSpec()
        mock_avd_spec.remote_fetch = True
        mock_avd_spec.fetch_cvd_wrapper = (
            r"GOOGLE_APPLICATION_CREDENTIALS=/fake_key.json,"
            r"CACHE_CONFIG=/home/shared/cache.properties,"
            r"java,-jar,/home/shared/FetchCvdWrapper.jar"
        )
        mock_ssh_obj = mock.Mock()
        mock_ssh.Ssh.return_value = mock_ssh_obj
        mock_ssh_obj.GetBaseCmd.return_value = "/mock/ssh"
        mock_glob.glob.return_value = ["/mock/fetch_cvd"]
        factory = remote_host_cf_device_factory.RemoteHostDeviceFactory(
            mock_avd_spec)

        log = {"path": "/log.txt"}
        mock_cvd_utils.GetRemoteHostBaseDir.return_value = "acloud_cf_1"
        mock_cvd_utils.FormatRemoteHostInstanceName.return_value = "inst"
        mock_cvd_utils.AreTargetFilesRequired.return_value = False
        mock_cvd_utils.ExecuteRemoteLaunchCvd.return_value = ""
        mock_cvd_utils.FindRemoteLogs.return_value = []
        mock_cvd_utils.GetRemoteFetcherConfigJson.return_value = log

        self._mock_build_api.GetFetchBuildArgs.return_value = ["-test"]

        self.assertEqual("inst", factory.CreateInstance())
        mock_cvd_utils.CleanUpRemoteCvd.assert_called_once()
        mock_ssh_obj.Run.assert_called_with("mkdir -p acloud_cf_1")
        self._mock_build_api.DownloadFetchcvd.assert_called_once()
        mock_shutil.copyfile.assert_called_with("/mock/key", mock.ANY)
        self.assertRegex(mock_ssh.ShellCmdWithRetry.call_args_list[0][0][0],
                         r"^tar -cf - --lzop -S -C \S+ fetch_cvd \| "
                         r"/mock/ssh -- tar -xf - --lzop -S -C acloud_cf_1$")
        self.assertRegex(mock_ssh.ShellCmdWithRetry.call_args_list[1][0][0],
                         r"^/mock/ssh -- "
                         r"GOOGLE_APPLICATION_CREDENTIALS=/fake_key.json "
                         r"CACHE_CONFIG=/home/shared/cache.properties "
                         r"java -jar /home/shared/FetchCvdWrapper.jar "
                         r"-directory=acloud_cf_1 "
                         r"-fetch_cvd_path=acloud_cf_1/fetch_cvd "
                         r"-credential_source=acloud_cf_1/credential_key.json "
                         r"-test$")
        mock_cvd_utils.ExecuteRemoteLaunchCvd.assert_called()
        mock_pull.GetAllLogFilePaths.assert_not_called()
        mock_pull.PullLogs.assert_not_called()
        self.assertFalse(factory.GetFailures())
        self.assertDictEqual({"inst": [log]}, factory.GetLogs())

    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.ssh")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory."
                "cvd_utils")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory."
                "subprocess.check_call")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.glob")
    @mock.patch("acloud.public.actions.remote_host_cf_device_factory.pull")
    def testCreateInstanceWithRemoteImageDir(self, _mock_pull, mock_glob,
                                             _mock_check_call, mock_cvd_utils,
                                             mock_ssh):
        """Test CreateInstance with AvdSpec.remote_image_dir."""
        mock_avd_spec = self._CreateMockAvdSpec()
        mock_avd_spec.remote_image_dir = "mock_img_dir"

        mock_ssh_obj = mock.Mock()
        mock_ssh.Ssh.return_value = mock_ssh_obj
        mock_ssh_obj.GetBaseCmd.return_value = "/mock/ssh"
        # Test initializing the remote image dir.
        mock_ssh_obj.Run.return_value = ""
        mock_glob.glob.return_value = ["/mock/super.img"]
        factory = remote_host_cf_device_factory.RemoteHostDeviceFactory(
            mock_avd_spec)

        mock_cvd_utils.GetRemoteHostBaseDir.return_value = "acloud_cf_1"
        mock_cvd_utils.FormatRemoteHostInstanceName.return_value = "inst"
        mock_cvd_utils.LoadRemoteImageArgs.return_value = None
        mock_cvd_utils.AreTargetFilesRequired.return_value = False
        mock_cvd_utils.UploadExtraImages.return_value = ["arg1"]
        mock_cvd_utils.ExecuteRemoteLaunchCvd.return_value = ""
        mock_cvd_utils.FindRemoteLogs.return_value = []

        self._mock_build_api.GetFetchBuildArgs.return_value = ["-test"]

        self.assertEqual("inst", factory.CreateInstance())
        mock_cvd_utils.LoadRemoteImageArgs.assert_called_once_with(
            mock_ssh_obj, "mock_img_dir/acloud_image_args.txt")
        mock_cvd_utils.SaveRemoteImageArgs.assert_called_once_with(
            mock_ssh_obj, "mock_img_dir/acloud_image_args.txt", ["arg1"])
        self._mock_build_api.DownloadFetchcvd.assert_called_once()
        print(mock_cvd_utils.GetRemoteLaunchCvdCmd.call_args[0][3])
        self.assertEqual(["arg1"],
                         mock_cvd_utils.GetRemoteLaunchCvdCmd.call_args[0][3])

        # Test reusing the remote image dir.
        mock_cvd_utils.LoadRemoteImageArgs.return_value = ["arg2"]
        mock_cvd_utils.SaveRemoteImageArgs.reset_mock()
        self._mock_build_api.DownloadFetchcvd.reset_mock()

        self.assertEqual("inst", factory.CreateInstance())
        mock_cvd_utils.SaveRemoteImageArgs.assert_not_called()
        self._mock_build_api.DownloadFetchcvd.assert_not_called()
        self.assertEqual(["arg2"],
                         mock_cvd_utils.GetRemoteLaunchCvdCmd.call_args[0][3])


if __name__ == "__main__":
    unittest.main()
