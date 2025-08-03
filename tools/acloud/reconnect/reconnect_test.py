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
"""Tests for reconnect."""

import collections
import unittest
import subprocess

from unittest import mock

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import auth
from acloud.internal.lib import android_compute_client
from acloud.internal.lib import cvd_runtime_config
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import gcompute_client
from acloud.internal.lib import utils
from acloud.internal.lib import ssh as ssh_object
from acloud.internal.lib.adb_tools import AdbTools
from acloud.list import list as list_instance
from acloud.public import config
from acloud.reconnect import reconnect


ForwardedPorts = collections.namedtuple("ForwardedPorts",
                                        [constants.VNC_PORT, constants.ADB_PORT])


class ReconnectTest(driver_test_lib.BaseDriverTest):
    """Test reconnect functions."""

    # pylint: disable=no-member, too-many-statements
    def testReconnectInstance(self):
        """Test Reconnect Instances."""
        ssh_private_key_path = "/fake/acloud_rsa"
        fake_report = mock.MagicMock()
        instance_object = mock.MagicMock()
        instance_object.name = "fake_name"
        instance_object.ip = "1.1.1.1"
        instance_object.islocal = False
        instance_object.adb_port = "8686"
        instance_object.avd_type = "cuttlefish"
        self.Patch(subprocess, "check_call", return_value=True)
        self.Patch(utils, "LaunchVncClient")
        self.Patch(utils, "AutoConnect")
        self.Patch(AdbTools, "IsAdbConnected", return_value=False)
        self.Patch(AdbTools, "IsAdbConnectionAlive", return_value=False)
        self.Patch(utils, "IsCommandRunning", return_value=False)
        fake_device_dict = {
            constants.IP: "1.1.1.1",
            constants.INSTANCE_NAME: "fake_name",
            constants.VNC_PORT: 6666,
            constants.ADB_PORT: "8686",
            constants.DEVICE_SERIAL: "127.0.0.1:8686"
        }

        # test ssh tunnel not connected, remote instance.
        instance_object.vnc_port = 6666
        instance_object.display = ""
        utils.AutoConnect.call_count = 0
        reconnect.ReconnectInstance(
            ssh_private_key_path, instance_object, fake_report, autoconnect="vnc")
        utils.AutoConnect.assert_not_called()
        utils.LaunchVncClient.assert_called_with(6666)
        fake_report.AddData.assert_called_with(key="devices", value=fake_device_dict)

        instance_object.display = "888x777 (99)"
        utils.AutoConnect.call_count = 0
        reconnect.ReconnectInstance(
            ssh_private_key_path, instance_object, fake_report, autoconnect="vnc")
        utils.AutoConnect.assert_not_called()
        utils.LaunchVncClient.assert_called_with(6666, "888", "777")
        fake_report.AddData.assert_called_with(key="devices", value=fake_device_dict)

        # test ssh tunnel connected , remote instance.
        instance_object.ssh_tunnel_is_connected = False
        instance_object.display = ""
        utils.AutoConnect.call_count = 0
        instance_object.vnc_port = 5555
        extra_args_ssh_tunnel = None
        self.Patch(utils, "AutoConnect",
                   return_value=ForwardedPorts(vnc_port=11111, adb_port=22222))
        reconnect.ReconnectInstance(
            ssh_private_key_path, instance_object, fake_report, autoconnect="vnc")
        utils.AutoConnect.assert_called_with(ip_addr=instance_object.ip,
                                             rsa_key_file=ssh_private_key_path,
                                             target_vnc_port=constants.CF_VNC_PORT,
                                             target_adb_port=constants.CF_ADB_PORT,
                                             ssh_user=constants.GCE_USER,
                                             extra_args_ssh_tunnel=extra_args_ssh_tunnel)
        utils.LaunchVncClient.assert_called_with(11111)
        fake_device_dict = {
            constants.IP: "1.1.1.1",
            constants.INSTANCE_NAME: "fake_name",
            constants.VNC_PORT: 11111,
            constants.ADB_PORT: 22222,
            constants.DEVICE_SERIAL: "127.0.0.1:22222"
        }
        fake_report.AddData.assert_called_with(key="devices", value=fake_device_dict)

        instance_object.display = "999x777 (99)"
        extra_args_ssh_tunnel = "fake_extra_args_ssh_tunnel"
        utils.AutoConnect.call_count = 0
        reconnect.ReconnectInstance(
            ssh_private_key_path, instance_object, fake_report,
            extra_args_ssh_tunnel=extra_args_ssh_tunnel,
            autoconnect="vnc")
        utils.AutoConnect.assert_called_with(ip_addr=instance_object.ip,
                                             rsa_key_file=ssh_private_key_path,
                                             target_vnc_port=constants.CF_VNC_PORT,
                                             target_adb_port=constants.CF_ADB_PORT,
                                             ssh_user=constants.GCE_USER,
                                             extra_args_ssh_tunnel=extra_args_ssh_tunnel)
        utils.LaunchVncClient.assert_called_with(11111, "999", "777")
        fake_report.AddData.assert_called_with(key="devices", value=fake_device_dict)

        # test fail reconnect report.
        self.Patch(utils, "AutoConnect",
                   return_value=ForwardedPorts(vnc_port=None, adb_port=None))
        reconnect.ReconnectInstance(
            ssh_private_key_path, instance_object, fake_report, autoconnect="vnc")
        fake_device_dict = {
            constants.IP: "1.1.1.1",
            constants.INSTANCE_NAME: "fake_name",
            constants.VNC_PORT: None,
            constants.ADB_PORT: None
        }
        fake_report.AddData.assert_called_with(key="device_failing_reconnect",
                                               value=fake_device_dict)

        # test reconnect local instance.
        instance_object.islocal = True
        instance_object.display = ""
        instance_object.vnc_port = 5555
        instance_object.ssh_tunnel_is_connected = False
        utils.AutoConnect.call_count = 0
        reconnect.ReconnectInstance(
            ssh_private_key_path, instance_object, fake_report, autoconnect="vnc")
        utils.AutoConnect.assert_not_called()
        utils.LaunchVncClient.assert_called_with(5555)
        fake_device_dict = {
            constants.IP: "1.1.1.1",
            constants.INSTANCE_NAME: "fake_name",
            constants.VNC_PORT: 5555,
            constants.ADB_PORT: "8686"
        }
        fake_report.AddData.assert_called_with(key="devices", value=fake_device_dict)

    # pylint: disable=no-member
    def testReconnectInstanceWithWebRTC(self):
        """Test reconnect instances with WebRTC."""
        ssh_private_key_path = "/fake/acloud_rsa"
        fake_report = mock.MagicMock()
        instance_object = mock.MagicMock()
        instance_object.ip = "1.1.1.1"
        instance_object.islocal = False
        instance_object.adb_port = "8686"
        instance_object.avd_type = "cuttlefish"
        self.Patch(subprocess, "check_call", return_value=True)
        self.Patch(utils, "LaunchVncClient")
        self.Patch(utils, "AutoConnect")
        self.Patch(utils, "LaunchBrowser")
        self.Patch(utils, "GetWebrtcPortFromSSHTunnel", return_value=None)
        self.Patch(utils, "EstablishWebRTCSshTunnel")
        self.Patch(utils, "PickFreePort", return_value=12345)
        self.Patch(AdbTools, "IsAdbConnected", return_value=False)
        self.Patch(AdbTools, "IsAdbConnectionAlive", return_value=False)
        self.Patch(utils, "IsCommandRunning", return_value=False)

        # test ssh tunnel not reconnect to the remote instance.
        instance_object.vnc_port = 6666
        instance_object.display = ""
        utils.AutoConnect.call_count = 0
        reconnect.ReconnectInstance(ssh_private_key_path, instance_object, fake_report,
                                    None, "webrtc")
        utils.AutoConnect.assert_not_called()
        utils.LaunchVncClient.assert_not_called()
        utils.EstablishWebRTCSshTunnel.assert_called_with(extra_args_ssh_tunnel=None,
                                                          webrtc_local_port=12345,
                                                          ip_addr='1.1.1.1',
                                                          rsa_key_file='/fake/acloud_rsa',
                                                          ssh_user='vsoc-01')
        utils.LaunchBrowser.assert_called_with('localhost', 12345)
        utils.PickFreePort.assert_called_once()
        utils.PickFreePort.reset_mock()

        self.Patch(utils, "GetWebrtcPortFromSSHTunnel", return_value="11111")
        reconnect.ReconnectInstance(ssh_private_key_path, instance_object, fake_report,
                                    None, "webrtc")
        utils.PickFreePort.assert_not_called()

        # local webrtc instance
        instance_object.islocal = True
        reconnect.ReconnectInstance(ssh_private_key_path, instance_object, fake_report,
                                    None, "webrtc")
        utils.PickFreePort.assert_not_called()

        # autoconnect adb only should launch nothing.
        utils.LaunchBrowser.reset_mock()
        utils.LaunchVncClient.reset_mock()
        reconnect.ReconnectInstance(ssh_private_key_path, instance_object, fake_report,
                                    None, "adb")
        utils.LaunchBrowser.assert_not_called()
        utils.LaunchVncClient.assert_not_called()


    def testReconnectInstanceAvdtype(self):
        """Test Reconnect Instances of avd_type."""
        ssh_private_key_path = "/fake/acloud_rsa"
        fake_report = mock.MagicMock()
        instance_object = mock.MagicMock()
        instance_object.ip = "1.1.1.1"
        instance_object.vnc_port = 9999
        instance_object.adb_port = "9999"
        instance_object.islocal = False
        instance_object.ssh_tunnel_is_connected = False
        self.Patch(utils, "AutoConnect")
        self.Patch(reconnect, "StartVnc")
        #test reconnect remote instance when avd_type as gce.
        instance_object.avd_type = "gce"
        reconnect.ReconnectInstance(
            ssh_private_key_path, instance_object, fake_report, autoconnect="vnc")
        utils.AutoConnect.assert_called_with(ip_addr=instance_object.ip,
                                             rsa_key_file=ssh_private_key_path,
                                             target_vnc_port=constants.GCE_VNC_PORT,
                                             target_adb_port=constants.GCE_ADB_PORT,
                                             ssh_user=constants.GCE_USER,
                                             extra_args_ssh_tunnel=None)
        reconnect.StartVnc.assert_called_once()

        #test reconnect remote instance when avd_type as cuttlefish.
        instance_object.avd_type = "cuttlefish"
        reconnect.StartVnc.call_count = 0
        reconnect.ReconnectInstance(
            ssh_private_key_path, instance_object, fake_report, autoconnect="vnc")
        utils.AutoConnect.assert_called_with(ip_addr=instance_object.ip,
                                             rsa_key_file=ssh_private_key_path,
                                             target_vnc_port=constants.CF_VNC_PORT,
                                             target_adb_port=constants.CF_ADB_PORT,
                                             ssh_user=constants.GCE_USER,
                                             extra_args_ssh_tunnel=None)
        reconnect.StartVnc.assert_called_once()

    def testReconnectInstanceUnknownAvdType(self):
        """Test reconnect instances of unknown avd type."""
        ssh_private_key_path = "/fake/acloud_rsa"
        fake_report = mock.MagicMock()
        instance_object = mock.MagicMock()
        instance_object.avd_type = "unknown"
        self.assertRaises(errors.UnknownAvdType,
                          reconnect.ReconnectInstance,
                          ssh_private_key_path,
                          instance_object,
                          fake_report)

    def testReconnectInstanceNoAvdType(self):
        """Test reconnect instances with no avd type."""
        ssh_private_key_path = "/fake/acloud_rsa"
        fake_report = mock.MagicMock()
        instance_object = mock.MagicMock()
        self.assertRaises(errors.UnknownAvdType,
                          reconnect.ReconnectInstance,
                          ssh_private_key_path,
                          instance_object,
                          fake_report)

    def testStartVnc(self):
        """Test start Vnc."""
        self.Patch(subprocess, "check_call", return_value=True)
        self.Patch(utils, "IsCommandRunning", return_value=False)
        self.Patch(utils, "LaunchVncClient")
        vnc_port = 5555
        display = ""
        reconnect.StartVnc(vnc_port, display)
        utils.LaunchVncClient.assert_called_with(5555)

        display = "888x777 (99)"
        utils.AutoConnect.call_count = 0
        reconnect.StartVnc(vnc_port, display)
        utils.LaunchVncClient.assert_called_with(5555, "888", "777")
        utils.LaunchVncClient.reset_mock()

        self.Patch(utils, "IsCommandRunning", return_value=True)
        reconnect.StartVnc(vnc_port, display)
        utils.LaunchVncClient.assert_not_called()

    # pylint: disable=protected-access
    def testIsWebrtcEnable(self):
        """Test _IsWebrtcEnable."""
        fake_ins = mock.MagicMock()
        fake_ins.islocal = True
        fake_ins.cf_runtime_cfg = mock.MagicMock()
        fake_ins.cf_runtime_cfg.enable_webrtc = False
        reconnect._IsWebrtcEnable(fake_ins, "fake_user", "ssh_pkey_path", "")
        self.assertFalse(reconnect._IsWebrtcEnable(fake_ins, "fake_user", "ssh_pkey_path", ""))

        fake_ins.islocal = False
        fake_runtime_config = mock.MagicMock()
        fake_runtime_config.enable_webrtc = True
        self.Patch(ssh_object, "Ssh")
        self.Patch(ssh_object.Ssh, "GetCmdOutput", return_value="fake_rawdata")
        self.Patch(cvd_runtime_config, "CvdRuntimeConfig",
                   return_value=fake_runtime_config)
        self.assertTrue(reconnect._IsWebrtcEnable(fake_ins, "fake_user", "ssh_pkey_path", ""))

        self.Patch(cvd_runtime_config, "CvdRuntimeConfig",
                   side_effect=errors.ConfigError)
        self.assertFalse(reconnect._IsWebrtcEnable(fake_ins, "fake_user", "ssh_pkey_path", ""))

    def testRun(self):
        """Test Run."""
        fake_args = mock.MagicMock()
        fake_args.autoconnect = "webrtc"
        fake_args.instance_names = ["fake-ins-name"]
        fake_ins1 = mock.MagicMock()
        fake_ins1.avd_type = "cuttlefish"
        fake_ins1.islocal = False
        fake_ins2 = mock.MagicMock()
        fake_ins2.avd_type = "cuttlefish"
        fake_ins2.islocal = False
        fake_ins_gf = mock.MagicMock()
        fake_ins_gf.avd_type = "goldfish"
        fake_ins_gf.islocal = False
        fake_ins_gf.vnc_port = 1234
        ins_to_reconnect = [fake_ins1]
        # mock args.all equal to True and return 3 instances.
        all_ins_to_reconnect = [fake_ins1, fake_ins2, fake_ins_gf]
        cfg = mock.MagicMock()
        cfg.ssh_private_key_path = None
        cfg.extra_args_ssh_tunnel = None
        self.Patch(config, "GetAcloudConfig", return_value=cfg)
        self.Patch(list_instance, "GetInstancesFromInstanceNames",
                   return_value=ins_to_reconnect)
        self.Patch(list_instance, "ChooseInstances",
                   return_value=all_ins_to_reconnect)
        self.Patch(auth, "CreateCredentials")
        self.Patch(android_compute_client, "AndroidComputeClient")
        self.Patch(android_compute_client.AndroidComputeClient,
                   "AddSshRsaInstanceMetadata")
        self.Patch(reconnect, "ReconnectInstance")

        reconnect.Run(fake_args)
        list_instance.GetInstancesFromInstanceNames.assert_called_once()
        self.assertEqual(reconnect.ReconnectInstance.call_count, 1)
        reconnect.ReconnectInstance.reset_mock()

        # should reconnect all instances
        fake_args.instance_names = None
        reconnect.Run(fake_args)
        list_instance.ChooseInstances.assert_called_once()
        self.assertEqual(reconnect.ReconnectInstance.call_count, 3)
        reconnect.ReconnectInstance.reset_mock()

        fake_ins1.islocal = True
        fake_ins2.avd_type = "unknown"
        self.Patch(list_instance, "ChooseInstances",
                   return_value=[fake_ins1, fake_ins2])
        reconnect.Run(fake_args)
        self.assertEqual(reconnect.ReconnectInstance.call_count, 1)

    def testGetSshConnectHostname(self):
        """Test GetSshConnectHostname."""
        self.Patch(gcompute_client, "GetGCEHostName", return_value="fake_host")
        instance = mock.MagicMock()
        instance.islocal = True
        cfg = mock.MagicMock()
        self.assertEqual(None, reconnect.GetSshConnectHostname(cfg, instance))

        # Remote instance will get the GCE hostname.
        instance.islocal = False
        cfg.connect_hostname = True
        self.assertEqual("fake_host",
                         reconnect.GetSshConnectHostname(cfg, instance))


if __name__ == "__main__":
    unittest.main()
