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


class PowerTelStandby_LTE_Test(cipt.PowerTelIdleTest):

    @property
    def display_name_test_suite(self):
        return 'LTE Standby Test'

    def test_lte_band_1_pul_low_bw_20_tm_1_dlmcs_28_mimo_1x1_paging_1280_rrcstatuschangetimer_5_drx_10_200_2_320_8(self):
        self.display_name_test_case = 'Standby with background traffic - B1'
        self.power_tel_idle_test()
