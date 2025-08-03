#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
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


class PowerTelIdle_Modem_Test(cipt.PowerTelIdleTest):

    @property
    def display_name_test_suite(self):
        return 'QComm dashboard - Idle'

    def test_lte_band_1_pul_low_bw_20_tm_1_dlmcs_28_mimo_1x1_paging_1280_rrcstatuschangetimer_10(self):
        self.display_name_test_case = 'LS11 - B1'
        self.power_tel_idle_test(filter_results=True)

    def test_lte_band_13_pul_low_bw_10_tm_3_dlmcs_28_mimo_2x2_paging_2560_rrcstatuschangetimer_10(self):
        self.display_name_test_case = 'LS1 - B13'
        self.power_tel_idle_test(filter_results=True)

    def test_lte_band_41_pul_low_bw_10_tm_3_dlmcs_28_mimo_2x2_tddconfig_1_ssf_7_paging_2560_rrcstatuschangetimer_10(self):
        self.display_name_test_case = 'LS3 - B41'
        self.power_tel_idle_test(filter_results=True)
