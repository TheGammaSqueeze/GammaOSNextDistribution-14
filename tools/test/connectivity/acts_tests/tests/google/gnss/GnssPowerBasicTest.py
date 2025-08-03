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


class GnssPowerBasicTest(PowerGTWGnssBaseTest):
    """Gnss Power Basic Test"""

    # Test cases
    # Standalone tests
    def test_standalone_gps_power_baseline(self):
        self.baseline_test()

    def test_standalone_DPO_strong_cn(self):
        self.start_gnss_tracking_with_power_data()

    def test_standalone_NDPO_strong_cn(self):
        self.enable_DPO(False)
        self.start_gnss_tracking_with_power_data()

    def test_standalone_DPO_weak_cn(self):
        self.set_attenuation(self.atten_level['weak_signal'])
        self.start_gnss_tracking_with_power_data()

    def test_standalone_DPO_weakest_cn(self):
        self.set_attenuation(self.atten_level['weakest_signal'])
        self.start_gnss_tracking_with_power_data()

    def test_standalone_no_cn(self):
        self.set_attenuation(self.atten_level['no_signal'])
        self.start_gnss_tracking_with_power_data()

    # Long Interval tests
    def test_standalone_DPO_strong_cn_long(self):
        self.start_gnss_tracking_with_power_data(freq=self.interval)

    def test_standalone_NDPO_strong_cn_long(self):
        self.enable_DPO(False)
        self.start_gnss_tracking_with_power_data(freq=self.interval)

    def test_standalone_DPO_weak_cn_long(self):
        self.set_attenuation(self.atten_level['weak_signal'])
        self.start_gnss_tracking_with_power_data(freq=self.interval)

