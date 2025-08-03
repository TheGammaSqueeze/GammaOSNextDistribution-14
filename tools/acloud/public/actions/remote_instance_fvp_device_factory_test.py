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
"""Tests for remote_instance_cf_device_factory."""

import glob
import os
import unittest

from unittest import mock

from acloud.create import avd_spec
from acloud.internal import constants
from acloud.internal.lib import android_build_client
from acloud.internal.lib import auth
from acloud.internal.lib import cvd_compute_client_multi_stage
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import ssh
from acloud.list import list as list_instances
from acloud.public.actions import remote_instance_fvp_device_factory


class RemoteInstanceDeviceFactoryTest(driver_test_lib.BaseDriverTest):
    """Test RemoteInstanceDeviceFactory."""

    def setUp(self):
        super().setUp()
        self.Patch(auth, "CreateCredentials", return_value=mock.MagicMock())
        self.Patch(android_build_client.AndroidBuildClient, "InitResourceHandle")
        self.Patch(cvd_compute_client_multi_stage.CvdComputeClient, "InitResourceHandle")
        self.Patch(list_instances, "GetInstancesFromInstanceNames", return_value=mock.MagicMock())
        self.Patch(list_instances, "ChooseOneRemoteInstance", return_value=mock.MagicMock())
        self.Patch(glob, "glob", return_vale=["fake.img"])

    # pylint: disable=protected-access
    @staticmethod
    @mock.patch.object(
        remote_instance_fvp_device_factory.RemoteInstanceDeviceFactory,
        "CreateGceInstance")
    @mock.patch.object(ssh, "ShellCmdWithRetry")
    @mock.patch.dict(os.environ, {
        constants.ENV_BUILD_TARGET:'fvp',
        "ANDROID_HOST_OUT":'/path/to/host/out',
        "ANDROID_PRODUCT_OUT":'/path/to/product/out',
        "MODEL_BIN":'/path/to/model/FVP_Base_RevC-2xAEMv8A',
    })
    def testCreateInstance(mock_shell, mock_create_gce):
        """Test CreateInstance."""
        fake_ip = ssh.IP(external="1.1.1.1", internal="10.1.1.1")
        args = mock.MagicMock()
        # Test local image extract from image zip case.
        args.config_file = ""
        args.avd_type = constants.TYPE_FVP
        args.flavor = "phone"
        args.local_image = "fake_local_image"
        args.local_system_image = None
        args.adb_port = None
        args.launch_args = None
        avd_spec_local_image = avd_spec.AVDSpec(args)
        factory = remote_instance_fvp_device_factory.RemoteInstanceDeviceFactory(
            avd_spec_local_image)
        factory._ssh = ssh.Ssh(ip=fake_ip,
                               user=constants.GCE_USER,
                               ssh_private_key_path="/fake/acloud_rea")
        mock_open = mock.mock_open(read_data = (
            "bl1.bin\n"
            "boot.img\n"
            "fip.bin\n"
            "system-qemu.img\n"
            "userdata.img\n"))
        with mock.patch("builtins.open", mock_open):
            factory.CreateInstance()

        mock_create_gce.assert_called_once()

        expected_cmds = [
            ("tar -cf - --lzop -S -C /path/to/product/out bl1.bin boot.img "
             "fip.bin system-qemu.img userdata.img | "
             "%s -- tar -xf - --lzop -S" %
             factory._ssh.GetBaseCmd(constants.SSH_BIN)),
            ("tar -cf - --lzop -S -C /path/to/model . | "
             "%s -- tar -xf - --lzop -S" %
             factory._ssh.GetBaseCmd(constants.SSH_BIN)),
            ("%s device/generic/goldfish/fvpbase/run_model_only "
             "vsoc-01@1.1.1.1:run_model_only" %
             factory._ssh.GetBaseCmd(constants.SCP_BIN)),
            ("%s -- mkdir -p lib64" %
             factory._ssh.GetBaseCmd(constants.SSH_BIN)),
            ("%s /path/to/host/out/lib64/bind_to_localhost.so "
             "vsoc-01@1.1.1.1:lib64/bind_to_localhost.so" %
             factory._ssh.GetBaseCmd(constants.SCP_BIN)),
            ("%s -- sh -c \"'ANDROID_HOST_OUT=. ANDROID_PRODUCT_OUT=. "
             "MODEL_BIN=./FVP_Base_RevC-2xAEMv8A "
             "./run_model_only > /dev/null 2> /dev/null &'\"" %
             factory._ssh.GetBaseCmd(constants.SSH_BIN)),
        ]
        mock_shell.assert_has_calls([mock.call(cmd) for cmd in expected_cmds])

if __name__ == "__main__":
    unittest.main()
