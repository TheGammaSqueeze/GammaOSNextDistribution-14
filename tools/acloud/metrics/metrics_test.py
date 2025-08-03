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
"""Tests for metrics."""

import sys
import unittest

from unittest import mock

# pylint: disable=import-error, no-name-in-module, wrong-import-position
sys.modules["asuite"] = mock.MagicMock()
sys.modules["asuite.metrics"] = mock.MagicMock()
from asuite.metrics import metrics_utils
from acloud.internal.lib import driver_test_lib
from acloud.metrics import metrics


class MetricsTest(driver_test_lib.BaseDriverTest):
    """Test metrics methods."""
    def testLogUsage(self):
        """Test LogUsage."""
        self.Patch(metrics_utils, "print_data_collection_notice")
        self.Patch(metrics_utils, "send_start_event")
        argv = ["create", "--local-instance"]
        self.assertTrue(metrics.LogUsage(argv))

        # Test arguments with "--no-metrics"
        argv = ["create", "--no-metrics"]
        self.assertFalse(metrics.LogUsage(argv))

        # Don't collect metrics for "delete" command.
        argv = ["delete", "--all"]
        self.assertFalse(metrics.LogUsage(argv))

    def testLogExitEvent(self):
        """Test LogExitEvent."""
        exit_code = 0
        self.Patch(metrics_utils, "send_exit_event")
        metrics.LogExitEvent(exit_code)
        metrics_utils.send_exit_event.assert_called_once_with(
            exit_code, stacktrace="", logs="")


if __name__ == "__main__":
    unittest.main()
