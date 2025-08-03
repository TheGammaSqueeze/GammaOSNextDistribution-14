# Copyright 2020 - The Android Open Source Project
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
"""Tests for create."""
import os

import unittest

from unittest import mock

from acloud import errors
from acloud.create import create_args
from acloud.internal import constants
from acloud.internal.lib import driver_test_lib


def _CreateArgs():
    """set default pass in arguments."""
    mock_args = mock.MagicMock(
        flavor=None,
        num=1,
        adb_port=None,
        hw_property=None,
        stable_cheeps_host_image_name=None,
        stable_cheeps_host_image_project=None,
        username=None,
        password=None,
        cheeps_betty_image=None,
        cheeps_features=[],
        local_image=None,
        local_kernel_image=None,
        local_system_image=None,
        local_instance_dir=None,
        kernel_branch=None,
        kernel_build_id=None,
        kernel_build_target="kernel",
        boot_branch=None,
        boot_build_id=None,
        boot_build_target=None,
        boot_artifact=None,
        system_branch=None,
        system_build_id=None,
        system_build_target=None,
        local_instance=None,
        remote_host=None,
        remote_image_dir=None,
        host_user=constants.GCE_USER,
        host_ssh_private_key_path=None,
        emulator_build_id=None,
        emulator_build_target=None,
        emulator_zip=None,
        avd_type=constants.TYPE_CF,
        autoconnect=constants.INS_KEY_WEBRTC)
    return mock_args


# pylint: disable=invalid-name,protected-access
class CreateArgsTest(driver_test_lib.BaseDriverTest):
    """Test create_args functions."""

    def testVerifyArgs(self):
        """test VerifyArgs."""
        mock_args = _CreateArgs()
        # Test args default setting shouldn't raise error.
        self.assertEqual(None, create_args.VerifyArgs(mock_args))

    def testVerifyArgs_Goldfish(self):
        """test goldfish arguments."""
        # emulator_build_id with wrong avd_type.
        mock_args = _CreateArgs()
        mock_args.emulator_build_id = "123456"
        self.assertRaises(errors.UnsupportedCreateArgs,
                          create_args.VerifyArgs, mock_args)
        # Valid emulator_build_id.
        mock_args.avd_type = constants.TYPE_GF
        create_args.VerifyArgs(mock_args)
        # emulator_build_target with wrong avd_type.
        mock_args.avd_type = constants.TYPE_CF
        mock_args.emulator_build_id = None
        mock_args.emulator_build_target = "emulator-linux_x64_nolocationui"
        mock_args.remote_host = "192.0.2.2"
        self.assertRaises(errors.UnsupportedCreateArgs,
                          create_args.VerifyArgs, mock_args)
        mock_args.emulator_build_target = None
        # Incomplete system build info.
        mock_args.avd_type = constants.TYPE_GF
        mock_args.system_build_target = "aosp_x86_64-userdebug"
        mock_args.remote_host = "192.0.2.2"
        self.assertRaises(errors.UnsupportedCreateArgs,
                          create_args.VerifyArgs, mock_args)
        mock_args.system_build_target = None
        # Incomplete boot build info.
        mock_args.avd_type = constants.TYPE_GF
        mock_args.boot_build_target = "gki_x86_64-userdebug"
        mock_args.remote_host = "192.0.2.2"
        self.assertRaises(errors.UnsupportedCreateArgs,
                          create_args.VerifyArgs, mock_args)
        mock_args.boot_build_target = None
        # System build info without remote_host.
        mock_args.system_branch = "aosp-master"
        mock_args.system_build_target = "aosp_x86_64-userdebug"
        mock_args.system_build_id = "123456"
        mock_args.remote_host = None
        self.assertRaises(errors.UnsupportedCreateArgs,
                          create_args.VerifyArgs, mock_args)
        # Valid build info.
        mock_args.emulator_build_target = "emulator-linux_x64_nolocationui"
        mock_args.system_branch = "aosp-master"
        mock_args.system_build_target = "aosp_x86_64-userdebug"
        mock_args.system_build_id = "123456"
        mock_args.boot_branch = "aosp-master"
        mock_args.boot_build_target = "aosp_x86_64-userdebug"
        mock_args.boot_build_id = "123456"
        mock_args.boot_artifact = "boot-5.10.img"
        mock_args.remote_host = "192.0.2.2"
        create_args.VerifyArgs(mock_args)

    def testVerifyArgs_ConnectWebRTC(self):
        """test VerifyArgs args.autconnect webrtc.

        WebRTC only apply to remote cuttlefish instance

        """
        mock_args = _CreateArgs()
        mock_args.autoconnect = constants.INS_KEY_WEBRTC
        # Test remote instance and avd_type cuttlefish(default)
        # Test args.autoconnect webrtc shouldn't raise error.
        self.assertEqual(None, create_args.VerifyArgs(mock_args))

    def testVerifyLocalArgs(self):
        """Test _VerifyLocalArgs."""
        mock_args = _CreateArgs()
        # verify local image case.
        mock_args.local_image = "/tmp/local_image_dir"
        self.Patch(os.path, "exists", return_value=False)
        self.assertRaises(errors.CheckPathError,
                          create_args._VerifyLocalArgs, mock_args)

        # verify local instance
        mock_args = _CreateArgs()
        mock_args.local_instance_dir = "/tmp/local_instance_dir"
        self.Patch(os.path, "exists", return_value=False)
        self.assertRaises(errors.CheckPathError,
                          create_args._VerifyLocalArgs, mock_args)

        # verify local system image
        mock_args = _CreateArgs()
        mock_args.local_system_image = "/tmp/local_system_image_dir"
        mock_args.avd_type = "cheeps"
        self.assertRaises(errors.UnsupportedCreateArgs,
                          create_args._VerifyLocalArgs, mock_args)
        mock_args.avd_type = "cuttlefish"
        self.Patch(os.path, "exists", return_value=False)
        self.assertRaises(errors.CheckPathError,
                          create_args._VerifyLocalArgs, mock_args)

        # unsupport local-image with kernel build
        mock_args = _CreateArgs()
        mock_args.local_instance = None
        mock_args.local_image = "/tmp/local_image_dir"
        self.Patch(os.path, "exists", return_value=True)
        mock_args.kernel_branch = "common-android12-5.4"
        self.assertRaises(errors.UnsupportedCreateArgs,
                          create_args._VerifyLocalArgs, mock_args)
        mock_args.kernel_branch = None
        mock_args.kernel_build_id = "fake_kernel_1234567"
        self.assertRaises(errors.UnsupportedCreateArgs,
                          create_args._VerifyLocalArgs, mock_args)


if __name__ == "__main__":
    unittest.main()
