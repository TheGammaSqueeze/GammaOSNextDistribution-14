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
"""Tests for instance class."""

import collections
import datetime
import os
import subprocess
import unittest

from unittest import mock

# pylint: disable=import-error
import dateutil.parser
import dateutil.tz

from acloud.internal import constants
from acloud.internal.lib import cvd_runtime_config
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import gcompute_client
from acloud.internal.lib import utils
from acloud.internal.lib.adb_tools import AdbTools
from acloud.list import instance


class InstanceTest(driver_test_lib.BaseDriverTest):
    """Test instance."""
    PS_SSH_TUNNEL = ("/fake_ps_1 --fake arg \n"
                     "/fake_ps_2 --fake arg \n"
                     "/usr/bin/ssh -i ~/.ssh/acloud_rsa "
                     "-o UserKnownHostsFile=/dev/null "
                     "-o StrictHostKeyChecking=no -L 54321:127.0.0.1:6520 "
                     "-L 12345:127.0.0.1:6444 -N -f -l user 1.1.1.1").encode()
    GCE_INSTANCE = {
        constants.INS_KEY_NAME: "fake_ins_name",
        constants.INS_KEY_CREATETIME: "fake_create_time",
        constants.INS_KEY_STATUS: "fake_status",
        constants.INS_KEY_ZONE: "test/zones/fake_zone",
        "networkInterfaces": [{"accessConfigs": [{"natIP": "1.1.1.1"}]}],
        "labels": {constants.INS_KEY_AVD_TYPE: "fake_type",
                   constants.INS_KEY_AVD_FLAVOR: "fake_flavor"},
        "metadata": {
            "items":[{"key":constants.INS_KEY_AVD_TYPE,
                      "value":"fake_type"},
                     {"key":constants.INS_KEY_AVD_FLAVOR,
                      "value":"fake_flavor"},
                     {"key":constants.INS_KEY_WEBRTC_PORT,
                      "value":"fake_webrtc_port"}]}
    }

    @staticmethod
    def _MockCvdRuntimeConfig():
        """Create a mock CvdRuntimeConfig."""
        return mock.MagicMock(
            instance_id=2,
            display_configs=[{'dpi': 480, 'x_res': 1080, 'y_res': 1920}],
            instance_dir="fake_instance_dir",
            adb_port=6521,
            vnc_port=6445,
            adb_ip_port="127.0.0.1:6521",
            cvd_tools_path="fake_cvd_tools_path",
            config_path="fake_config_path",
            instances={},
            root_dir="/tmp/acloud_cvd_temp/local-instance-2/cuttlefish_runtime"
        )

    @mock.patch("acloud.list.instance.AdbTools")
    def testCreateLocalInstance(self, mock_adb_tools):
        """Test getting local instance info from cvd runtime config."""
        mock_adb_tools_object = mock.Mock(device_information={})
        mock_adb_tools_object.IsAdbConnected.return_value = True
        mock_adb_tools.return_value = mock_adb_tools_object
        self.Patch(cvd_runtime_config, "CvdRuntimeConfig",
                   return_value=self._MockCvdRuntimeConfig())
        self.Patch(instance.LocalInstance, "_GetDevidInfoFromCvdStatus",
                   return_value=None)
        local_instance = instance.LocalInstance("fake_config_path")

        self.assertEqual("local-instance-2", local_instance.name)
        self.assertEqual(True, local_instance.islocal)
        self.assertEqual(["1080x1920 (480)"], local_instance.display)
        expected_full_name = ("device serial: 0.0.0.0:%s %s (%s) elapsed time: %s"
                              % ("6521",
                                 "cvd-2",
                                 "local-instance-2",
                                 "None"))
        self.assertEqual(expected_full_name, local_instance.fullname)
        self.assertEqual(6521, local_instance.adb_port)
        self.assertEqual(6445, local_instance.vnc_port)
        self.assertEqual(8444, local_instance.webrtc_port)

    # pylint: disable=protected-access
    def testGetCvdEnv(self):
        """Test GetCvdEnv."""
        self.Patch(cvd_runtime_config, "CvdRuntimeConfig",
                   return_value=self._MockCvdRuntimeConfig())
        self.Patch(instance, "_IsProcessRunning", return_value=False)
        local_instance = instance.LocalInstance("fake_config_path")
        cvd_env = local_instance._GetCvdEnv()
        self.assertEqual(cvd_env[constants.ENV_CUTTLEFISH_INSTANCE], "2")
        self.assertEqual(cvd_env[constants.ENV_CUTTLEFISH_CONFIG_FILE],
                         "fake_config_path")

    # pylint: disable=protected-access
    def testParsingCvdFleetOutput(self):
        """Test ParsingCvdFleetOutput."""
        cvd_fleet_output = """WARNING: cvd_server client version does not match
{
"adb_serial" : "0.0.0.0:6520",
"instance_name" : "cvd-1",
}"""

        expected_result = """{
"adb_serial" : "0.0.0.0:6520",
"instance_name" : "cvd-1",
}"""

        self.assertEqual(
            instance.LocalInstance._ParsingCvdFleetOutput(cvd_fleet_output),
            expected_result)

    # pylint: disable=protected-access
    def testIsProcessRunning(self):
        """Test IsProcessRunning."""
        process = "cvd_server"
        self.Patch(utils, "CheckOutput",
                   return_value="/bin/cvd_server -server_fd=4")
        self.assertEqual(instance._IsProcessRunning(process), True)

        self.Patch(utils, "CheckOutput", return_value="/bin/cvd start")
        self.assertEqual(instance._IsProcessRunning(process), False)

    @mock.patch("acloud.list.instance.AdbTools")
    def testDeleteLocalInstance(self, mock_adb_tools):
        """Test executing 'cvd stop' command."""
        self.Patch(cvd_runtime_config, "CvdRuntimeConfig",
                   return_value=self._MockCvdRuntimeConfig())
        mock_adb_tools_object = mock.Mock(device_information={})
        mock_adb_tools_object.IsAdbConnected.return_value = True
        mock_adb_tools.return_value = mock_adb_tools_object
        self.Patch(utils, "AddUserGroupsToCmd",
                   side_effect=lambda cmd, groups: cmd)
        self.Patch(instance.LocalInstance, "_GetDevidInfoFromCvdStatus",
                   return_value=None)
        mock_check_call = self.Patch(subprocess, "check_call")
        mock_check_output = self.Patch(
            subprocess, "check_output",
            return_value="cvd_internal_stop E stop cvd failed")

        local_instance = instance.LocalInstance("fake_config_path")
        with mock.patch.dict("acloud.list.instance.os.environ", clear=True):
            local_instance.Delete()

        expected_env = {
            "CUTTLEFISH_INSTANCE": "2",
            "HOME": "/tmp/acloud_cvd_temp/local-instance-2",
            "CUTTLEFISH_CONFIG_FILE": "fake_config_path",
            "ANDROID_HOST_OUT": "",
            "ANDROID_SOONG_HOST_OUT": "",
        }
        mock_check_output.assert_called_with(
            "/tmp/acloud_cvd_temp/local-instance-2/host_bins/bin/cvd stop",
            stderr=subprocess.STDOUT, shell=True, env=expected_env, text=True,
            timeout=instance._CVD_TIMEOUT)
        mock_check_call.assert_called_with(
            "/tmp/acloud_cvd_temp/local-instance-2/host_bins/bin/stop_cvd",
            stderr=subprocess.STDOUT, shell=True, env=expected_env)
        mock_adb_tools_object.DisconnectAdb.assert_called()

    @mock.patch("acloud.list.instance.tempfile")
    @mock.patch("acloud.list.instance.AdbTools")
    def testCreateLocalGoldfishInstance(self, mock_adb_tools, mock_tempfile):
        """"Test the attributes of LocalGoldfishInstance."""
        mock_tempfile.gettempdir.return_value = "/unit/test"
        mock_adb_tools.return_value = mock.Mock(device_information={})

        inst = instance.LocalGoldfishInstance(1)

        self.assertEqual(inst.name, "local-goldfish-instance-1")
        self.assertEqual(inst.avd_type, constants.TYPE_GF)
        self.assertEqual(inst.adb_port, 5555)
        self.assertTrue(inst.islocal)
        self.assertEqual(inst.console_port, 5554)
        self.assertEqual(inst.device_serial, "emulator-5554")
        self.assertEqual(inst.instance_dir,
                         "/unit/test/acloud_gf_temp/local-goldfish-instance-1")

    @mock.patch("acloud.list.instance.AdbTools")
    def testGetLocalGoldfishInstances(self, mock_adb_tools):
        """Test LocalGoldfishInstance.GetExistingInstances."""
        mock_adb_tools.GetDeviceSerials.return_value = [
            "127.0.0.1:6520", "emulator-5554", "ABCD", "emulator-5558"]

        instances = instance.LocalGoldfishInstance.GetExistingInstances()

        self.assertEqual(len(instances), 2)
        self.assertEqual(instances[0].console_port, 5554)
        self.assertEqual(instances[0].name, "local-goldfish-instance-1")
        self.assertEqual(instances[1].console_port, 5558)
        self.assertEqual(instances[1].name, "local-goldfish-instance-3")

    def testGetMaxNumberOfGoldfishInstances(self):
        """Test LocalGoldfishInstance.GetMaxNumberOfInstances."""
        mock_environ = {}
        with mock.patch.dict("acloud.list.instance.os.environ",
                             mock_environ, clear=True):
            num = instance.LocalGoldfishInstance.GetMaxNumberOfInstances()
        self.assertEqual(num, 16)

        mock_environ["ADB_LOCAL_TRANSPORT_MAX_PORT"] = "5565"
        with mock.patch.dict("acloud.list.instance.os.environ",
                             mock_environ, clear=True):
            num = instance.LocalGoldfishInstance.GetMaxNumberOfInstances()
        self.assertEqual(num, 6)

    # pylint: disable=protected-access
    def testGetElapsedTime(self):
        """Test _GetElapsedTime"""
        # Instance time can't parse
        start_time = "error time"
        self.assertEqual(instance._MSG_UNABLE_TO_CALCULATE,
                         instance._GetElapsedTime(start_time))

        # Remote instance elapsed time
        now = "2019-01-14T13:00:00.000-07:00"
        start_time = "2019-01-14T03:00:00.000-07:00"
        self.Patch(instance, "datetime")
        instance.datetime.datetime.now.return_value = dateutil.parser.parse(now)
        self.assertEqual(
            datetime.timedelta(hours=10), instance._GetElapsedTime(start_time))

        # Local instance elapsed time
        now = "Mon Jan 14 10:10:10 2019"
        start_time = "Mon Jan 14 08:10:10 2019"
        instance.datetime.datetime.now.return_value = dateutil.parser.parse(
            now).replace(tzinfo=dateutil.tz.tzlocal())
        self.assertEqual(
            datetime.timedelta(hours=2), instance._GetElapsedTime(start_time))

    # pylint: disable=protected-access
    def testGetAdbVncPortFromSSHTunnel(self):
        """"Test Get forwarding adb and vnc port from ssh tunnel."""
        self.Patch(subprocess, "check_output", return_value=self.PS_SSH_TUNNEL)
        self.Patch(instance, "_GetElapsedTime", return_value="fake_time")
        self.Patch(instance.RemoteInstance, "_GetZoneName", return_value="fake_zone")
        self.Patch(instance.RemoteInstance,
                   "_GetProjectName",
                   return_value="fake_project")
        self.Patch(gcompute_client, "GetGCEHostName", return_value="fake_hostname")
        forwarded_ports = instance.RemoteInstance(
            mock.MagicMock()).GetAdbVncPortFromSSHTunnel(
                "1.1.1.1", "fake_hostname", constants.TYPE_CF)
        self.assertEqual(54321, forwarded_ports.adb_port)
        self.assertEqual(12345, forwarded_ports.vnc_port)

        # If avd_type is undefined in utils.AVD_PORT_DICT.
        forwarded_ports = instance.RemoteInstance(
            mock.MagicMock()).GetAdbVncPortFromSSHTunnel(
                "1.1.1.1", "fake_hostname", "undefined_avd_type")
        self.assertEqual(None, forwarded_ports.adb_port)
        self.assertEqual(None, forwarded_ports.vnc_port)

    # pylint: disable=protected-access
    def testProcessGceInstance(self):
        """"Test process instance detail."""
        fake_adb = 123456
        fake_vnc = 654321
        forwarded_ports = collections.namedtuple("ForwardedPorts",
                                                 [constants.VNC_PORT,
                                                  constants.ADB_PORT])
        self.Patch(instance.RemoteInstance,
                   "_GetProjectName",
                   return_value="fake_project")
        self.Patch(
            instance.RemoteInstance,
            "GetAdbVncPortFromSSHTunnel",
            return_value=forwarded_ports(vnc_port=fake_vnc, adb_port=fake_adb))
        self.Patch(utils, "GetWebrtcPortFromSSHTunnel",
                   return_value="fake_webrtc_port")
        self.Patch(instance, "_GetElapsedTime", return_value="fake_time")
        self.Patch(AdbTools, "IsAdbConnected", return_value=True)

        # test ssh_tunnel_is_connected will be true if ssh tunnel connection is found
        instance_info = instance.RemoteInstance(self.GCE_INSTANCE)
        self.assertTrue(instance_info.ssh_tunnel_is_connected)
        self.assertEqual(instance_info.adb_port, fake_adb)
        self.assertEqual(instance_info.vnc_port, fake_vnc)
        self.assertEqual("1.1.1.1", instance_info.ip)
        self.assertEqual("fake_status", instance_info.status)
        self.assertEqual("fake_type", instance_info.avd_type)
        self.assertEqual("fake_flavor", instance_info.avd_flavor)
        expected_full_name = "device serial: 127.0.0.1:%s (%s) elapsed time: %s" % (
            fake_adb, self.GCE_INSTANCE[constants.INS_KEY_NAME], "fake_time")
        self.assertEqual(expected_full_name, instance_info.fullname)

        # test ssh tunnel is connected but adb is disconnected
        self.Patch(AdbTools, "IsAdbConnected", return_value=False)
        instance_info = instance.RemoteInstance(self.GCE_INSTANCE)
        self.assertTrue(instance_info.ssh_tunnel_is_connected)
        expected_full_name = "device serial: not connected (%s) elapsed time: %s" % (
            instance_info.name, "fake_time")
        self.assertEqual(expected_full_name, instance_info.fullname)

        # test ssh_tunnel_is_connected will be false if ssh tunnel connection is not found
        self.Patch(
            instance.RemoteInstance,
            "GetAdbVncPortFromSSHTunnel",
            return_value=forwarded_ports(vnc_port=None, adb_port=None))
        instance_info = instance.RemoteInstance(self.GCE_INSTANCE)
        self.assertFalse(instance_info.ssh_tunnel_is_connected)
        expected_full_name = "device serial: not connected (%s) elapsed time: %s" % (
            self.GCE_INSTANCE[constants.INS_KEY_NAME], "fake_time")
        self.assertEqual(expected_full_name, instance_info.fullname)

    def testInstanceSummary(self):
        """Test instance summary."""
        fake_adb = 123456
        fake_vnc = 654321
        forwarded_ports = collections.namedtuple("ForwardedPorts",
                                                 [constants.VNC_PORT,
                                                  constants.ADB_PORT])
        self.Patch(instance.RemoteInstance,
                   "_GetProjectName",
                   return_value="fake_project")
        self.Patch(
            instance.RemoteInstance,
            "GetAdbVncPortFromSSHTunnel",
            return_value=forwarded_ports(vnc_port=fake_vnc, adb_port=fake_adb))
        self.Patch(utils, "GetWebrtcPortFromSSHTunnel", return_value=8443)
        self.Patch(instance, "_GetElapsedTime", return_value="fake_time")
        self.Patch(AdbTools, "IsAdbConnected", return_value=True)
        remote_instance = instance.RemoteInstance(self.GCE_INSTANCE)
        result_summary = (" name: fake_ins_name\n "
                          "   IP: 1.1.1.1\n "
                          "   create time: fake_create_time\n "
                          "   elapse time: fake_time\n "
                          "   status: fake_status\n "
                          "   avd type: fake_type\n "
                          "   display: None\n "
                          "   vnc: 127.0.0.1:654321\n "
                          "   zone: fake_zone\n "
                          "   autoconnect: webrtc\n "
                          "   webrtc port: fake_webrtc_port\n "
                          "   webrtc forward port: 8443\n "
                          "   adb serial: 127.0.0.1:123456\n "
                          "   product: None\n "
                          "   model: None\n "
                          "   device: None\n "
                          "   transport_id: None")
        self.assertEqual(remote_instance.Summary(), result_summary)

        self.Patch(
            instance.RemoteInstance,
            "GetAdbVncPortFromSSHTunnel",
            return_value=forwarded_ports(vnc_port=None, adb_port=None))
        self.Patch(instance, "_GetElapsedTime", return_value="fake_time")
        self.Patch(AdbTools, "IsAdbConnected", return_value=False)
        remote_instance = instance.RemoteInstance(self.GCE_INSTANCE)
        result_summary = (" name: fake_ins_name\n "
                          "   IP: 1.1.1.1\n "
                          "   create time: fake_create_time\n "
                          "   elapse time: fake_time\n "
                          "   status: fake_status\n "
                          "   avd type: fake_type\n "
                          "   display: None\n "
                          "   vnc: 127.0.0.1:None\n "
                          "   zone: fake_zone\n "
                          "   autoconnect: webrtc\n "
                          "   webrtc port: fake_webrtc_port\n "
                          "   webrtc forward port: 8443\n "
                          "   adb serial: disconnected")
        self.assertEqual(remote_instance.Summary(), result_summary)

    def testGetZoneName(self):
        """Test GetZoneName."""
        zone_info = "v1/projects/project/zones/us-central1-c"
        expected_result = "us-central1-c"
        self.assertEqual(instance.RemoteInstance._GetZoneName(zone_info),
                         expected_result)
        # Test can't get zone name from zone info.
        zone_info = "v1/projects/project/us-central1-c"
        self.assertEqual(instance.RemoteInstance._GetZoneName(zone_info), None)

    def testGetProjectName(self):
        """Test GetProjectName."""
        zone_info = "v1/projects/fake_project/zones/us-central1-c"
        expected_result = "fake_project"
        self.assertEqual(instance.RemoteInstance._GetProjectName(zone_info),
                         expected_result)

    def testGetLocalInstanceConfig(self):
        """Test GetLocalInstanceConfig."""
        self.Patch(instance, "GetLocalInstanceHomeDir",
                  return_value="ins_home")
        self.Patch(os.path, "isfile", return_value=False)
        instance_id = 1
        self.assertEqual(instance.GetLocalInstanceConfig(instance_id), None)

        # Test config in new folder path.
        self.Patch(os.path, "isfile", return_value=True)
        expected_result = "ins_home/cuttlefish_assembly/cuttlefish_config.json"
        self.assertEqual(
            instance.GetLocalInstanceConfig(instance_id), expected_result)

    def testGetAutoConnect(self):
        """Test GetAutoConnect."""
        name = "ins_name"
        fullname = "fake_fullname"
        display = "1080x1920 (480)"
        ip = "fake_ip"
        ins_webrtc = instance.Instance(
            name, fullname, display, ip, webrtc_port=8443)
        self.assertEqual(ins_webrtc._GetAutoConnect(), constants.INS_KEY_WEBRTC)

        ins_webrtc = instance.Instance(
            name, fullname, display, ip, vnc_port=6520)
        self.assertEqual(ins_webrtc._GetAutoConnect(), constants.INS_KEY_VNC)

        ins_webrtc = instance.Instance(
            name, fullname, display, ip, adb_port=6666)
        self.assertEqual(ins_webrtc._GetAutoConnect(), constants.INS_KEY_ADB)

        ins_webrtc = instance.Instance(name, fullname, display, ip)
        self.assertEqual(ins_webrtc._GetAutoConnect(), None)

    @mock.patch("acloud.list.instance.LocalInstance")
    def testGetCuttleFishLocalInstances(self, mock_local_instance):
        """Test GetCuttleFishLocalInstances."""
        self.Patch(cvd_runtime_config, "CvdRuntimeConfig",
                   return_value=mock.MagicMock(instance_ids=["2", "3"]))
        instance.GetCuttleFishLocalInstances("fake_config_path")
        self.assertEqual(mock_local_instance.call_count, 2)

    def testGetDeviceFullName(self):
        """Test GetDeviceFullName."""
        device_serial = "0.0.0.0:6520"
        webrtc_device_id = "codelab"
        instance_name = "local-instance-1"
        elapsed_time = "10:10:24"

        expected_result = ("device serial: 0.0.0.0:6520 codelab "
                           "(local-instance-1) elapsed time: 10:10:24")
        self.assertEqual(expected_result, instance._GetDeviceFullName(
            device_serial, instance_name, elapsed_time, webrtc_device_id))

        # Test with no webrtc_device_id
        webrtc_device_id = None
        expected_result = ("device serial: 0.0.0.0:6520 (local-instance-1) "
                           "elapsed time: 10:10:24")
        self.assertEqual(expected_result, instance._GetDeviceFullName(
            device_serial, instance_name, elapsed_time, webrtc_device_id))


if __name__ == "__main__":
    unittest.main()
