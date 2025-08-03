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
"""Tests for acloud.public.actions.common_operations."""

from __future__ import absolute_import
from __future__ import division

import unittest

from unittest import mock

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import android_build_client
from acloud.internal.lib import android_compute_client
from acloud.internal.lib import auth
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import utils
from acloud.internal.lib import ssh
from acloud.public import report
from acloud.public.actions import common_operations


class CommonOperationsTest(driver_test_lib.BaseDriverTest):
    """Test Common Operations."""
    maxDiff = None
    IP = ssh.IP(external="127.0.0.1", internal="10.0.0.1")
    INSTANCE = "fake-instance"
    CMD = "test-cmd"
    AVD_TYPE = "fake-type"
    BRANCH = "fake-branch"
    BUILD_TARGET = "fake-target"
    BUILD_ID = "fake-build-id"
    LOGS = [{"path": "/log", "type": "TEXT"}]

    # pylint: disable=protected-access
    def setUp(self):
        """Set up the test."""
        super().setUp()
        self.build_client = mock.MagicMock()
        self.device_factory = mock.MagicMock()
        self.Patch(
            android_build_client,
            "AndroidBuildClient",
            return_value=self.build_client)
        self.compute_client = mock.MagicMock()
        self.compute_client.gce_hostname = None
        self.Patch(
            android_compute_client,
            "AndroidComputeClient",
            return_value=self.compute_client)
        self.Patch(auth, "CreateCredentials", return_value=mock.MagicMock())
        self.Patch(self.compute_client, "GetInstanceIP", return_value=self.IP)
        self.Patch(
            self.device_factory, "CreateInstance", return_value=self.INSTANCE)
        self.Patch(
            self.device_factory,
            "GetComputeClient",
            return_value=self.compute_client)
        self.Patch(self.device_factory, "GetVncPorts", return_value=[6444])
        self.Patch(self.device_factory, "GetAdbPorts", return_value=[6520])
        self.Patch(self.device_factory, "GetBuildInfoDict",
                   return_value={"branch": self.BRANCH,
                                 "build_id": self.BUILD_ID,
                                 "build_target": self.BUILD_TARGET,
                                 "gcs_bucket_build_id": self.BUILD_ID})
        self.Patch(self.device_factory, "GetLogs",
                   return_value={self.INSTANCE: self.LOGS})
        self.Patch(
            self.device_factory,
            "GetFetchCvdWrapperLogIfExist", return_value={})

    @staticmethod
    def _CreateCfg():
        """A helper method that creates a mock configuration object."""
        cfg = mock.MagicMock()
        cfg.service_account_name = "fake@service.com"
        cfg.service_account_private_key_path = "/fake/path/to/key"
        cfg.zone = "fake_zone"
        cfg.disk_image_name = "fake_image.tar.gz"
        cfg.disk_image_mime_type = "fake/type"
        cfg.ssh_private_key_path = "cfg/private/key"
        cfg.ssh_public_key_path = ""
        cfg.extra_args_ssh_tunnel="extra args"
        return cfg

    def testDevicePoolCreateDevices(self):
        """Test Device Pool Create Devices."""
        pool = common_operations.DevicePool(self.device_factory)
        pool.CreateDevices(5)
        self.assertEqual(self.device_factory.CreateInstance.call_count, 5)
        self.assertEqual(len(pool.devices), 5)

    def testCreateDevices(self):
        """Test Create Devices."""
        cfg = self._CreateCfg()
        _report = common_operations.CreateDevices(self.CMD, cfg,
                                                  self.device_factory, 1,
                                                  self.AVD_TYPE)
        self.assertEqual(_report.command, self.CMD)
        self.assertEqual(_report.status, report.Status.SUCCESS)
        self.assertEqual(
            _report.data,
            {"devices": [{
                "ip": self.IP.external + ":6520",
                "instance_name": self.INSTANCE,
                "branch": self.BRANCH,
                "build_id": self.BUILD_ID,
                "build_target": self.BUILD_TARGET,
                "gcs_bucket_build_id": self.BUILD_ID,
                "logs": self.LOGS
            }]})

    def testCreateDevicesWithAdbPort(self):
        """Test Create Devices with adb port for cuttlefish avd type."""
        forwarded_ports = mock.Mock(adb_port=12345, vnc_port=56789)
        mock_auto_connect = self.Patch(utils, "AutoConnect",
                                       return_value=forwarded_ports)
        cfg = self._CreateCfg()
        _report = common_operations.CreateDevices(self.CMD, cfg,
                                                  self.device_factory, 1,
                                                  "cuttlefish",
                                                  autoconnect=True,
                                                  client_adb_port=12345)

        mock_auto_connect.assert_called_with(
            ip_addr="127.0.0.1", rsa_key_file="cfg/private/key",
            target_vnc_port=6444, target_adb_port=6520,
            ssh_user=constants.GCE_USER, client_adb_port=12345,
            extra_args_ssh_tunnel="extra args")
        self.assertEqual(_report.command, self.CMD)
        self.assertEqual(_report.status, report.Status.SUCCESS)
        self.assertEqual(
            _report.data,
            {"devices": [{
                "ip": self.IP.external + ":6520",
                "instance_name": self.INSTANCE,
                "branch": self.BRANCH,
                "build_id": self.BUILD_ID,
                "adb_port": 12345,
                "device_serial": "127.0.0.1:12345",
                "vnc_port": 56789,
                "build_target": self.BUILD_TARGET,
                "gcs_bucket_build_id": self.BUILD_ID,
                "logs": self.LOGS
            }]})

    def testCreateDevicesMultipleDevices(self):
        """Test Create Devices with multiple cuttlefish devices."""
        forwarded_ports_1 = mock.Mock(adb_port=12345, vnc_port=56789)
        forwarded_ports_2 = mock.Mock(adb_port=23456, vnc_port=67890)
        self.Patch(self.device_factory, "GetVncPorts", return_value=[6444, 6445])
        self.Patch(self.device_factory, "GetAdbPorts", return_value=[6520, 6521])
        self.Patch(utils, "PickFreePort", return_value=12345)
        mock_auto_connect = self.Patch(
            utils, "AutoConnect", side_effects=[forwarded_ports_1,
                                                forwarded_ports_2])
        cfg = self._CreateCfg()
        _report = common_operations.CreateDevices(self.CMD, cfg,
                                                  self.device_factory, 1,
                                                  "cuttlefish",
                                                  autoconnect=True,
                                                  client_adb_port=None)
        self.assertEqual(2, mock_auto_connect.call_count)
        mock_auto_connect.assert_any_call(
            ip_addr="127.0.0.1", rsa_key_file="cfg/private/key",
            target_vnc_port=6444, target_adb_port=6520,
            ssh_user=constants.GCE_USER, client_adb_port=None,
            extra_args_ssh_tunnel="extra args")
        mock_auto_connect.assert_any_call(
            ip_addr="127.0.0.1", rsa_key_file="cfg/private/key",
            target_vnc_port=6445, target_adb_port=6521,
            ssh_user=constants.GCE_USER, client_adb_port=None,
            extra_args_ssh_tunnel="extra args")
        self.assertEqual(_report.command, self.CMD)
        self.assertEqual(_report.status, report.Status.SUCCESS)

    def testCreateDevicesInternalIP(self):
        """Test Create Devices and report internal IP."""
        cfg = self._CreateCfg()
        _report = common_operations.CreateDevices(self.CMD, cfg,
                                                  self.device_factory, 1,
                                                  self.AVD_TYPE,
                                                  report_internal_ip=True)
        self.assertEqual(_report.command, self.CMD)
        self.assertEqual(_report.status, report.Status.SUCCESS)
        self.assertEqual(
            _report.data,
            {"devices": [{
                "ip": self.IP.internal + ":6520",
                "instance_name": self.INSTANCE,
                "branch": self.BRANCH,
                "build_id": self.BUILD_ID,
                "build_target": self.BUILD_TARGET,
                "gcs_bucket_build_id": self.BUILD_ID,
                "logs": self.LOGS
            }]})

    def testCreateDevicesWithSshParameters(self):
        """Test Create Devices with ssh user and key."""
        forwarded_ports = mock.Mock(adb_port=12345, vnc_port=56789)
        mock_auto_connect = self.Patch(utils, "AutoConnect",
                                       return_value=forwarded_ports)
        mock_establish_webrtc = self.Patch(utils, "EstablishWebRTCSshTunnel")
        self.Patch(utils, "PickFreePort", return_value=12345)
        cfg = self._CreateCfg()
        _report = common_operations.CreateDevices(
            self.CMD, cfg, self.device_factory, 1, constants.TYPE_CF,
            autoconnect=True, connect_webrtc=True,
            ssh_user="user", ssh_private_key_path="private/key")

        mock_auto_connect.assert_called_with(
            ip_addr="127.0.0.1", rsa_key_file="private/key",
            target_vnc_port=6444, target_adb_port=6520, ssh_user="user",
            client_adb_port=None, extra_args_ssh_tunnel="extra args")
        mock_establish_webrtc.assert_called_with(
            ip_addr="127.0.0.1", rsa_key_file="private/key",
            ssh_user="user", extra_args_ssh_tunnel="extra args",
            webrtc_local_port=12345)
        self.assertEqual(_report.status, report.Status.SUCCESS)

    def testGetErrorType(self):
        """Test GetErrorType."""
        # Test with CheckGCEZonesQuotaError()
        error = errors.CheckGCEZonesQuotaError()
        expected_result = constants.GCE_QUOTA_ERROR
        self.assertEqual(common_operations._GetErrorType(error), expected_result)

        # Test with DownloadArtifactError()
        error = errors.DownloadArtifactError()
        expected_result = constants.ACLOUD_DOWNLOAD_ARTIFACT_ERROR
        self.assertEqual(common_operations._GetErrorType(error), expected_result)

        # Test with DeviceConnectionError()
        error = errors.DeviceConnectionError()
        expected_result = constants.ACLOUD_SSH_CONNECT_ERROR
        self.assertEqual(common_operations._GetErrorType(error), expected_result)

        # Test with ACLOUD_UNKNOWN_ERROR
        error = errors.DriverError()
        expected_result = constants.ACLOUD_UNKNOWN_ERROR
        self.assertEqual(common_operations._GetErrorType(error), expected_result)

        # Test with error message about GCE quota issue
        error = errors.DriverError("Quota exceeded for quota read group.")
        expected_result = constants.GCE_QUOTA_ERROR
        self.assertEqual(common_operations._GetErrorType(error), expected_result)

        error = errors.DriverError("ZONE_RESOURCE_POOL_EXHAUSTED_WITH_DETAILS")
        expected_result = constants.GCE_QUOTA_ERROR
        self.assertEqual(common_operations._GetErrorType(error), expected_result)

    def testCreateDevicesWithFetchCvdWrapper(self):
        """Test Create Devices with FetchCvdWrapper."""
        self.Patch(
            self.device_factory,
            "GetFetchCvdWrapperLogIfExist", return_value={"fetch_log": "abc"})
        cfg = self._CreateCfg()
        _report = common_operations.CreateDevices(self.CMD, cfg,
                                                  self.device_factory, 1,
                                                  constants.TYPE_CF)
        self.assertEqual(_report.command, self.CMD)
        self.assertEqual(_report.status, report.Status.SUCCESS)
        self.assertEqual(
            _report.data,
            {"devices": [{
                "ip": self.IP.external + ":6520",
                "instance_name": self.INSTANCE,
                "branch": self.BRANCH,
                "build_id": self.BUILD_ID,
                "build_target": self.BUILD_TARGET,
                "gcs_bucket_build_id": self.BUILD_ID,
                "logs": self.LOGS,
                "fetch_cvd_wrapper_log": {
                    "fetch_log": "abc"
                },
            }]})


if __name__ == "__main__":
    unittest.main()
