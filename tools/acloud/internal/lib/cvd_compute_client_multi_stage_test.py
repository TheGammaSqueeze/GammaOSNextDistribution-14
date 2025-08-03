#!/usr/bin/env python
#
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

"""Tests for acloud.internal.lib.cvd_compute_client_multi_stage."""

import collections
import glob
import os
import unittest

from unittest import mock

from acloud import errors
from acloud.create import avd_spec
from acloud.internal import constants
from acloud.internal.lib import android_build_client
from acloud.internal.lib import cvd_compute_client_multi_stage
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import gcompute_client
from acloud.internal.lib import utils
from acloud.internal.lib.ssh import Ssh
from acloud.internal.lib.ssh import IP
from acloud.list import list as list_instances
from acloud.setup import mkcert


ExtraFile = collections.namedtuple("ExtraFile", ["source", "target"])


class CvdComputeClientTest(driver_test_lib.BaseDriverTest):
    """Test CvdComputeClient."""

    SSH_PUBLIC_KEY_PATH = ""
    INSTANCE = "fake-instance"
    IMAGE = "fake-image"
    IMAGE_PROJECT = "fake-iamge-project"
    MACHINE_TYPE = "fake-machine-type"
    NETWORK = "fake-network"
    ZONE = "fake-zone"
    PROJECT = "fake-project"
    BRANCH = "fake-branch"
    TARGET = "aosp_cf_x86_64_phone-userdebug"
    BUILD_ID = "2263051"
    KERNEL_BRANCH = "fake-kernel-branch"
    KERNEL_BUILD_ID = "1234567"
    KERNEL_BUILD_TARGET = "kernel"
    DPI = 160
    X_RES = 720
    Y_RES = 1280
    METADATA = {"metadata_key": "metadata_value"}
    EXTRA_DATA_DISK_SIZE_GB = 4
    BOOT_DISK_SIZE_GB = 10
    LAUNCH_ARGS = "--setupwizard_mode=REQUIRED"
    EXTRA_SCOPES = ["scope1"]
    GPU = "fake-gpu"
    DISK_TYPE = "fake-disk-type"
    FAKE_IP = IP(external="1.1.1.1", internal="10.1.1.1")

    def _GetFakeConfig(self):
        """Create a fake configuration object.

        Returns:
            A fake configuration mock object.
        """
        fake_cfg = mock.MagicMock()
        fake_cfg.ssh_public_key_path = self.SSH_PUBLIC_KEY_PATH
        fake_cfg.machine_type = self.MACHINE_TYPE
        fake_cfg.network = self.NETWORK
        fake_cfg.zone = self.ZONE
        fake_cfg.project = self.PROJECT
        fake_cfg.resolution = "{x}x{y}x32x{dpi}".format(
            x=self.X_RES, y=self.Y_RES, dpi=self.DPI)
        fake_cfg.metadata_variable = self.METADATA
        fake_cfg.extra_data_disk_size_gb = self.EXTRA_DATA_DISK_SIZE_GB
        fake_cfg.launch_args = self.LAUNCH_ARGS
        fake_cfg.extra_scopes = self.EXTRA_SCOPES
        return fake_cfg

    # pylint: disable=protected-access
    def setUp(self):
        """Set up the test."""
        super().setUp()
        self.Patch(cvd_compute_client_multi_stage.CvdComputeClient, "InitResourceHandle")
        self.Patch(cvd_compute_client_multi_stage.CvdComputeClient, "_VerifyZoneByQuota",
                   return_value=True)
        self.Patch(android_build_client.AndroidBuildClient, "InitResourceHandle")
        self.Patch(android_build_client.AndroidBuildClient, "DownloadArtifact")
        self.Patch(list_instances, "GetInstancesFromInstanceNames", return_value=mock.MagicMock())
        self.Patch(list_instances, "ChooseOneRemoteInstance", return_value=mock.MagicMock())
        self.Patch(Ssh, "ScpPushFile")
        self.Patch(Ssh, "WaitForSsh")
        self.Patch(Ssh, "GetBaseCmd")
        self.cvd_compute_client_multi_stage = cvd_compute_client_multi_stage.CvdComputeClient(
            self._GetFakeConfig(), mock.MagicMock(), gpu=self.GPU)
        self.cvd_compute_client_multi_stage._ssh = Ssh(
            ip=self.FAKE_IP,
            user=constants.GCE_USER,
            ssh_private_key_path="/fake/acloud_rea")
        self.args = mock.MagicMock()
        self.args.local_image = constants.FIND_IN_BUILD_ENV
        self.args.local_system_image = None
        self.args.config_file = ""
        self.args.avd_type = constants.TYPE_CF
        self.args.flavor = "phone"
        self.args.adb_port = None
        self.args.base_instance_num = None
        self.args.hw_property = "cpu:2,resolution:1080x1920,dpi:240,memory:4g,disk:10g"
        self.args.num_avds_per_instance = 2
        self.args.remote_host = False
        self.args.launch_args = self.LAUNCH_ARGS
        self.args.disable_external_ip = False
        self.args.autoconnect = False
        self.args.disk_type = self.DISK_TYPE
        self.args.openwrt = False
        self.args.webrtc_device_id = "cvd-1"

    @mock.patch.object(utils, "GetBuildEnvironmentVariable", return_value="fake_env_cf_x86")
    @mock.patch.object(glob, "glob", return_value=["fake.img"])
    @mock.patch.object(gcompute_client.ComputeClient, "CompareMachineSize",
                       return_value=1)
    @mock.patch.object(gcompute_client.ComputeClient, "GetImage",
                       return_value={"diskSizeGb": 10})
    @mock.patch.object(gcompute_client.ComputeClient, "CreateInstance")
    @mock.patch.object(cvd_compute_client_multi_stage.CvdComputeClient, "_GetDiskArgs",
                       return_value=[{"fake_arg": "fake_value"}])
    def testCreateInstance(self, _get_disk_args, mock_create, _get_image,
                           _compare_machine_size, _mock_check_img, _mock_env):
        """Test CreateInstance."""
        expected_disk_args = [{"fake_arg": "fake_value"}]
        fake_avd_spec = avd_spec.AVDSpec(self.args)
        fake_avd_spec._instance_name_to_reuse = None
        fake_avd_spec.hw_property[constants.HW_X_RES] = str(self.X_RES)
        fake_avd_spec.hw_property[constants.HW_Y_RES] = str(self.Y_RES)
        fake_avd_spec.hw_property[constants.HW_ALIAS_DPI] = str(self.DPI)
        fake_avd_spec.hw_property[constants.HW_ALIAS_DISK] = str(
            self.EXTRA_DATA_DISK_SIZE_GB * 1024)

        local_image_metadata = dict(self.METADATA)
        local_image_metadata["avd_type"] = constants.TYPE_CF
        local_image_metadata["flavor"] = "phone"
        local_image_metadata[constants.INS_KEY_WEBRTC_DEVICE_ID] = "cvd-1"
        local_image_metadata[constants.INS_KEY_DISPLAY] = ("%sx%s (%s)" % (
            fake_avd_spec.hw_property[constants.HW_X_RES],
            fake_avd_spec.hw_property[constants.HW_Y_RES],
            fake_avd_spec.hw_property[constants.HW_ALIAS_DPI]))
        self.cvd_compute_client_multi_stage.CreateInstance(
            self.INSTANCE, self.IMAGE, self.IMAGE_PROJECT,
            fake_avd_spec, self.EXTRA_SCOPES)

        mock_create.assert_called_with(
            self.cvd_compute_client_multi_stage,
            instance=self.INSTANCE,
            image_name=self.IMAGE,
            image_project=self.IMAGE_PROJECT,
            disk_args=expected_disk_args,
            metadata=local_image_metadata,
            machine_type=self.MACHINE_TYPE,
            network=self.NETWORK,
            zone=self.ZONE,
            extra_scopes=self.EXTRA_SCOPES,
            gpu=self.GPU,
            disk_type=self.DISK_TYPE,
            disable_external_ip=False)

    def testSetStage(self):
        """Test SetStage"""
        device_stage = "fake_stage"
        self.cvd_compute_client_multi_stage.SetStage(device_stage)
        self.assertEqual(self.cvd_compute_client_multi_stage.stage,
                         device_stage)

    @mock.patch.object(Ssh, "Run")
    @mock.patch.object(Ssh, "ScpPushFiles")
    def testUpdateCertificate(self, mock_upload, mock_trustremote):
        """Test UpdateCertificate"""
        # Certificate is not ready
        self.Patch(mkcert, "AllocateLocalHostCert", return_value=False)
        self.cvd_compute_client_multi_stage.UpdateCertificate()
        self.assertEqual(mock_upload.call_count, 0)
        self.assertEqual(mock_trustremote.call_count, 0)

        # Certificate is ready
        self.Patch(mkcert, "AllocateLocalHostCert", return_value=True)
        local_cert_dir = os.path.join(os.path.expanduser("~"),
                                      constants.SSL_DIR)
        self.cvd_compute_client_multi_stage.UpdateCertificate()
        mock_upload.assert_called_once_with(
            ["%s/server.crt" % local_cert_dir,
             "%s/server.key" % local_cert_dir,
             "%s/%s.pem" % (local_cert_dir, constants.SSL_CA_NAME)],
            constants.WEBRTC_CERTS_PATH)
        mock_trustremote.assert_called_once()

    def testGetBootTimeout(self):
        """Test GetBootTimeout"""
        self.cvd_compute_client_multi_stage._execution_time = {
            "fetch_artifact_time": 100}
        self.assertEqual(
            400, self.cvd_compute_client_multi_stage._GetBootTimeout(500))

    @mock.patch.object(Ssh, "ScpPushFile")
    def testUploadExtraFiles(self, mock_upload):
        """Test UploadExtraFiles."""
        self.Patch(os.path, "exists", return_value=True)
        extra_files = [ExtraFile(source="local_path", target="gce_path")]
        self.cvd_compute_client_multi_stage.UploadExtraFiles(extra_files)
        mock_upload.assert_called_once_with("local_path", "gce_path")

        # Test the local file doesn't exist.
        self.Patch(os.path, "exists", return_value=False)
        with self.assertRaises(errors.CheckPathError):
            self.cvd_compute_client_multi_stage.UploadExtraFiles(extra_files)


if __name__ == "__main__":
    unittest.main()
