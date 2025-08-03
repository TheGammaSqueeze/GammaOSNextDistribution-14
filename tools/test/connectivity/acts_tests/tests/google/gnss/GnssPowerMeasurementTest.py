#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from acts import utils
from acts_contrib.test_utils.power.PowerGTWGnssBaseTest import PowerGTWGnssBaseTest


class GnssPowerMeasurementTest(PowerGTWGnssBaseTest):
    """Gnss Power Long Interval Test"""

    # Test cases
    def test_measurement_DPO(self):
        self.start_gnss_tracking_with_power_data(
            mode='standalone', freq=self.meas_interval, meas=True)

    def test_measurement_NDPO(self):
        self.enable_DPO(False)
        self.start_gnss_tracking_with_power_data(
            mode='standalone', freq=self.meas_interval, meas=True)

    # Long Interval tests
    def test_measurement_DPO_long(self):
        self.start_gnss_tracking_with_power_data(
            mode='standalone', freq=self.interval, meas=True)

    def test_measurement_NDPO_long(self):
        self.enable_DPO(False)
        self.start_gnss_tracking_with_power_data(
            mode='standalone', freq=self.interval, meas=True)
