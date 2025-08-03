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

import acts_contrib.test_utils.power.cellular.cellular_traffic_power_test as ctpt


class PowerTelTraffic_Modem_Test(ctpt.PowerTelTrafficTest):

    @property
    def display_name_test_suite(self):
        return 'QComm dashboard - Traffic'

    def test_lte_band_13_pul_low_bw_10_tm_3_dlmcs_28_mimo_2x2_direction_dlul_phich_16_cfi_1(self):
        self.display_name_test_case = 'LTE1E - Cat3 B13'
        self.power_tel_traffic_test()

    def test_lte_band_7_pul_low_bw_20_tm_3_dlmcs_28_mimo_2x2_direction_dlul_phich_16_cfi_1(self):
        self.display_name_test_case = 'LTE7E - Cat4 B7'
        self.power_tel_traffic_test()

    def test_lte_band_4_pul_low_bw_20_tm_4_dlmcs_28_mimo_2x2_direction_dlul_phich_16_cfi_1(self):
        self.display_name_test_case = 'LTE7E - Cat4 B4'
        self.power_tel_traffic_test()

    def test_lte_band_38_pul_low_bw_20_tm_3_mimo_2x2_direction_dlul_tddconfig_1_phich_16_cfi_1_ssf_7(self):
        self.display_name_test_case = 'LTE5E - Cat3 B38'
        self.power_tel_traffic_test()

    def test_lteca_ca_3a7a_pul_low_bw_20_20_tm_3_3_mimo_2x2_2x2_direction_dlul_phich_16_cfi_1(self):
        self.display_name_test_case = 'LTE10E - Cat6 3A7A'
        self.power_tel_traffic_test()

    def test_lteca_ca_66c2a_pul_low_bw_20_20_20_tm_3_3_3_mimo_2x2_2x2_2x2_direction_dlul_phich_16_cfi_1(self):
        self.display_name_test_case = 'LTE21E - Cat9 66C2A'
        self.power_tel_traffic_test()
