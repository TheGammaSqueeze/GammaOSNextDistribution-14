#!/usr/bin/env python3
#
#   Copyright 2018 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import acts_contrib.test_utils.power.cellular.cellular_idle_power_test as cipt


class PowerTelIdle_LTE_Test(cipt.PowerTelIdleTest):

    @property
    def display_name_test_suite(self):
        return 'TechEng LTE Idle'

    def test_lte_idle_band_13_pul_low_bw_10_tm_1_mimo_1x1_rrcstatuschangetimer_10_1(self):
        self.display_name_test_case = 'LTE Idle - Band 13, low UL power, 10 MHz BW, TM3 1x1'
        self.power_tel_idle_test(filter_results=True)

    def test_lte_idle_band_41_pul_low_bw_10_tm_1_mimo_1x1_rrcstatuschangetimer_10_tddconfig_2_2(self):
        self.display_name_test_case = 'LTE Idle - Band 41, low UL power, 10 MHz BW, TM3 1x1, TDD 2'
        self.power_tel_idle_test(filter_results=True)

    def test_lte_idle_band_4_pul_low_bw_10_tm_1_mimo_1x1_rrcstatuschangetimer_10_tddconfig_2_3(self):
        self.display_name_test_case = 'LTE Idle - Band 4, low UL power, 10 MHz BW, TM3 1x1, TDD 2'
        self.power_tel_idle_test(filter_results=True)
