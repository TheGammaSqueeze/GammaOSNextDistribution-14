#!/usr/bin/env python
#
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
"""Tests for acloud.internal.lib.oxygen_client."""

import subprocess

import unittest

from unittest import mock

from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import oxygen_client


class OxygenClentTest(driver_test_lib.BaseDriverTest):
    """Test OxygenClient."""

    @staticmethod
    @mock.patch.object(subprocess, "check_output")
    def testLeaseDevice(mock_subprocess):
        """Test LeaseDevice."""
        build_target = "fake_target"
        build_id = "fake_id"
        oxygen_client_path = "oxygen_client_path"
        build_branch = "master-branch"

        # Test mixed build lease request.
        lease_args = ""
        expected_cmd = [oxygen_client_path, "-lease", "-build_id", build_id,
                        "-build_target", build_target, "-build_branch",
                        build_branch, "-system_build_id", "system_build_id1",
                        "-system_build_target", "system_build_target1",
                        "-kernel_build_id", "kernel_build_id2",
                        "-kernel_build_target", "kernel_build_target2"]
        oxygen_client.OxygenClient.LeaseDevice(
            build_target, build_id, build_branch, "system_build_target1",
            "system_build_id1", "kernel_build_target2", "kernel_build_id2",
            oxygen_client_path, lease_args)
        mock_subprocess.assert_called_with(expected_cmd,
                                           stderr=subprocess.STDOUT,
                                           encoding='utf-8')

        # Test with lease command args.
        lease_args = "-user user@gmail.com"
        expected_cmd = [oxygen_client_path, "-lease", "-build_id", build_id,
                        "-build_target", build_target, "-build_branch",
                        build_branch, "-user", "user@gmail.com"]
        oxygen_client.OxygenClient.LeaseDevice(
            build_target, build_id, build_branch, "", "", "", "",
            oxygen_client_path, lease_args)
        mock_subprocess.assert_called_with(expected_cmd,
                                           stderr=subprocess.STDOUT,
                                           encoding='utf-8')


if __name__ == "__main__":
    unittest.main()
