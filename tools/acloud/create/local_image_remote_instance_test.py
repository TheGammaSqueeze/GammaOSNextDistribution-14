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
"""Tests for LocalImageRemoteInstance."""

import unittest

from unittest import mock

from acloud.create import create_common
from acloud.create import local_image_remote_instance
from acloud.internal import constants
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import utils
from acloud.public import report
from acloud.public.actions import common_operations
from acloud.public.actions import remote_instance_cf_device_factory
from acloud.public.actions import remote_instance_fvp_device_factory


class LocalImageRemoteInstanceTest(driver_test_lib.BaseDriverTest):
    """Test LocalImageRemoteInstance method."""

    def setUp(self):
        """Initialize new LocalImageRemoteInstance."""
        super().setUp()
        self.local_image_remote_instance = local_image_remote_instance.LocalImageRemoteInstance()

    # pylint: disable=protected-access
    @mock.patch.object(utils, "LaunchVNCFromReport")
    @mock.patch.object(utils, "LaunchBrowserFromReport")
    @mock.patch.object(remote_instance_fvp_device_factory,
                       "RemoteInstanceDeviceFactory")
    @mock.patch.object(remote_instance_cf_device_factory,
                       "RemoteInstanceDeviceFactory")
    def testCreateAVD(self, mock_cf_factory, mock_fvp_factory,
                      mock_launch_browser, mock_launch_vnc):
        """Test CreateAVD."""
        spec = mock.MagicMock()
        spec.avd_type = constants.TYPE_CF
        spec.instance_type = constants.INSTANCE_TYPE_REMOTE
        spec.image_source = constants.IMAGE_SRC_LOCAL
        spec.connect_vnc = False
        spec.connect_webrtc = True
        create_report = mock.Mock()
        create_report.status = report.Status.SUCCESS
        self.Patch(common_operations, "CreateDevices",
                   return_value=create_report)
        self.Patch(create_common, "GetCvdHostPackage")
        # cuttfish with webrtc
        self.local_image_remote_instance._CreateAVD(
            spec, no_prompts=True)
        mock_cf_factory.assert_called_once()
        mock_launch_browser.assert_called_once()

        # fvp with vnc
        spec.avd_type = constants.TYPE_FVP
        spec.connect_vnc = True
        spec.connect_webrtc = False
        self.local_image_remote_instance._CreateAVD(
            spec, no_prompts=True)
        mock_fvp_factory.assert_called_once()
        mock_launch_vnc.assert_called_once()


if __name__ == "__main__":
    unittest.main()
