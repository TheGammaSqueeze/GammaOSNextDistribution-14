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
"""Tests for RemoteImageRemoteInstance."""

import unittest

from unittest import mock

from acloud.create import remote_image_remote_instance
from acloud.internal import constants
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import oxygen_client
from acloud.internal.lib import utils
from acloud.public import report
from acloud.public.actions import common_operations
from acloud.public.actions import remote_instance_cf_device_factory


ONE_LINE_LEASE_RESPONSE = ("2021/08/02 11:28:52 session_id:\"fake_device\" "
                           "server_url:\"10.1.1.1\" ports:{type:WATERFALL value:0}")
MULTIPLE_LINES_LEASE_RESPONSE = """
2021/08/02 11:28:52
session_id:"fake_device"
server_url:"10.1.1.1"
"""
LEASE_FAILURE_RESPONSE = """
2021/08/16 18:07:36 message 1
2021/08/16 18:11:36 Error received while trying to lease device: rpc error: details
"""


class RemoteImageRemoteInstanceTest(driver_test_lib.BaseDriverTest):
    """Test RemoteImageRemoteInstance method."""

    def setUp(self):
        """Initialize new RemoteImageRemoteInstance."""
        super().setUp()
        self.remote_image_remote_instance = remote_image_remote_instance.RemoteImageRemoteInstance()

    # pylint: disable=protected-access
    @mock.patch.object(utils, "LaunchVNCFromReport")
    @mock.patch.object(utils, "LaunchBrowserFromReport")
    @mock.patch.object(remote_image_remote_instance.RemoteImageRemoteInstance,
                       "_LeaseOxygenAVD")
    @mock.patch.object(remote_instance_cf_device_factory,
                       "RemoteInstanceDeviceFactory")
    def testCreateAVD(self, mock_factory, mock_lease, mock_launch_browser,
                      mock_launch_vnc):
        """test CreateAVD."""
        avd_spec = mock.Mock()
        avd_spec.oxygen = False
        avd_spec.connect_webrtc = True
        avd_spec.connect_vnc = False
        avd_spec.gce_only = False
        create_report = mock.Mock()
        create_report.status = report.Status.SUCCESS
        self.Patch(common_operations, "CreateDevices",
                   return_value=create_report)
        self.remote_image_remote_instance._CreateAVD(
            avd_spec, no_prompts=True)
        mock_factory.assert_called_once()
        mock_launch_browser.assert_called_once()

        # Test launch VNC case.
        avd_spec.connect_webrtc = False
        avd_spec.connect_vnc = True
        self.remote_image_remote_instance._CreateAVD(
            avd_spec, no_prompts=True)
        mock_launch_vnc.assert_called_once()

        # Test launch with Oxgen case.
        avd_spec.oxygen = True
        self.remote_image_remote_instance._CreateAVD(
            avd_spec, no_prompts=True)
        mock_lease.assert_called_once()

    def testLeaseOxygenAVD(self):
        """test LeaseOxygenAVD."""
        avd_spec = mock.Mock()
        avd_spec.oxygen = True
        avd_spec.remote_image = {constants.BUILD_TARGET: "fake_target",
                                 constants.BUILD_ID: "fake_id",
                                 constants.BUILD_BRANCH: "fake_branch"}
        avd_spec.system_build_info = {constants.BUILD_TARGET: "fake_target",
                                      constants.BUILD_ID: "fake_id",
                                      constants.BUILD_BRANCH: "fake_branch"}
        avd_spec.kernel_build_info = {constants.BUILD_TARGET: "fake_target",
                                      constants.BUILD_ID: "fake_id",
                                      constants.BUILD_BRANCH: "fake_branch"}
        response_fail = "Lease device fail."
        self.Patch(oxygen_client.OxygenClient, "LeaseDevice",
                   side_effect=[ONE_LINE_LEASE_RESPONSE, response_fail])
        expected_status = report.Status.SUCCESS
        reporter = self.remote_image_remote_instance._LeaseOxygenAVD(avd_spec)
        self.assertEqual(reporter.status, expected_status)

        expected_status = report.Status.FAIL
        reporter = self.remote_image_remote_instance._LeaseOxygenAVD(avd_spec)
        self.assertEqual(reporter.status, expected_status)

    def testOxygenLeaseFailure(self):
        """test LeaseOxygenAVD when the lease call failed."""
        avd_spec = mock.Mock()
        avd_spec.oxygen = True
        avd_spec.remote_image = {constants.BUILD_TARGET: "fake_target",
                                 constants.BUILD_ID: "fake_id",
                                 constants.BUILD_BRANCH: "fake_branch"}
        avd_spec.system_build_info = {constants.BUILD_TARGET: "fake_target",
                                      constants.BUILD_ID: "fake_id",
                                      constants.BUILD_BRANCH: "fake_branch"}
        avd_spec.kernel_build_info = {constants.BUILD_TARGET: "fake_target",
                                      constants.BUILD_ID: "fake_id",
                                      constants.BUILD_BRANCH: "fake_branch"}
        response_fail = "Lease device fail."
        self.Patch(oxygen_client.OxygenClient, "LeaseDevice",
                   side_effect=[LEASE_FAILURE_RESPONSE, response_fail])
        expected_status = report.Status.FAIL
        reporter = self.remote_image_remote_instance._LeaseOxygenAVD(avd_spec)
        self.assertEqual(reporter.status, expected_status)
        self.assertEqual(reporter.error_type, constants.ACLOUD_OXYGEN_LEASE_ERROR)
        self.assertEqual(reporter.errors, ["rpc error: details"])

    def testGetDeviceInfoFromResponse(self):
        """test GetDeviceInfoFromResponse."""
        expect_session_id = "fake_device"
        expect_server_url = "10.1.1.1"
        self.assertEqual(
            self.remote_image_remote_instance._GetDeviceInfoFromResponse(
                ONE_LINE_LEASE_RESPONSE),
            (expect_session_id, expect_server_url))

        self.assertEqual(
            self.remote_image_remote_instance._GetDeviceInfoFromResponse(
                MULTIPLE_LINES_LEASE_RESPONSE),
            (expect_session_id, expect_server_url))


if __name__ == '__main__':
    unittest.main()
