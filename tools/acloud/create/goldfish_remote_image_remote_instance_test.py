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
"""Tests for GoldfishRemoteImageRemoteInstance."""

import unittest

from unittest import mock

from acloud.create import avd_spec
from acloud.create import create
from acloud.internal import constants
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import utils
from acloud.public.actions import create_goldfish_action

class GoldfishRemoteImageRemoteInstanceTest(driver_test_lib.BaseDriverTest):
    """Test GoldfishRemoteImageRemoteInstance method."""

    # pylint: disable=no-member
    def testRun(self):
        """Test Create AVD of goldfish remote image remote instance."""
        args = mock.MagicMock()
        args.skip_pre_run_check = True
        spec = mock.MagicMock()
        spec.avd_type = constants.TYPE_GF
        spec.instance_type = constants.INSTANCE_TYPE_REMOTE
        spec.image_source = constants.IMAGE_SRC_REMOTE
        spec.connect_vnc = False
        self.Patch(avd_spec, "AVDSpec", return_value=spec)
        self.Patch(create_goldfish_action,
                   "CreateDevices")
        create.Run(args)
        create_goldfish_action.CreateDevices.assert_called_once()

        spec.connect_vnc = True
        self.Patch(avd_spec, "AVDSpec", return_value=spec)
        self.Patch(utils, "LaunchVNCFromReport")
        create.Run(args)
        utils.LaunchVNCFromReport.assert_called_once()


if __name__ == '__main__':
    unittest.main()
