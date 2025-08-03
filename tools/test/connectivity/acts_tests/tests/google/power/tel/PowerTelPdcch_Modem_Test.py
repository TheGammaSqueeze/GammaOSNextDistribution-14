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

import acts_contrib.test_utils.power.cellular.cellular_pdcch_power_test as cppt


class PowerTelPdcch_Modem_Test(cppt.PowerTelPDCCHTest):

    @property
    def display_name_test_suite(self):
        return 'QComm dashboard - PDCCH'

    def test_lte_band_13_pul_low_bw_10_tm_3_dlmcs_4_ulmcs_4_mimo_2x2_direction_dlul_phich_16_cfi_1(self):
        self.display_name_test_case = 'LTE17E-1 - B13'
        self.power_pdcch_test()

    def test_lte_band_7_pul_low_bw_20_tm_3_dlmcs_4_ulmcs_4_mimo_2x2_direction_dlul_phich_16_cfi_1(self):
        self.display_name_test_case = 'LTE17E-1 - B7'
        self.power_pdcch_test()

    def test_lte_band_4_pul_low_bw_20_tm_3_dlmcs_4_ulmcs_4_mimo_2x2_direction_dlul_phich_16_cfi_1(self):
        self.display_name_test_case = 'LTE17E-1 - B4'
        self.power_pdcch_test()

    def test_lte_band_40_pul_low_bw_20_tm_3_dlmcs_4_ulmcs_4_mimo_2x2_direction_dlul_phich_16_cfi_1_tddconfig_2(self):
        self.display_name_test_case = 'LTE40E-1 - B40'
        self.power_pdcch_test()

    def test_lte_band_1_pul_low_bw_20_tm_3_dlmcs_4_ulmcs_4_mimo_2x2_drx_10_200_2_320_8_scheduling_dynamic(self):
        self.display_name_test_case = 'CDRxS9 - B1'
        self.power_pdcch_test()

    def test_lte_band_41_pul_low_bw_20_tm_3_dlmcs_4_ulmcs_4_mimo_2x2_tddconfig_2_drx_10_200_2_320_8_scheduling_dynamic(self):
        self.display_name_test_case = 'CDRxS10 - B41'
        self.power_pdcch_test()

    def test_nr_1_n48_pdcch(self):
        self.display_name_test_case = 'PDCCH 5G Sub6 NSA'
        self.power_pdcch_test()

    def test_nr_1_n78_pdcch(self):
        self.display_name_test_case = 'PDCCH 5G Sub6 NSA'
        self.power_pdcch_test()

    def test_nr_1_n48_cdrx(self):
        self.display_name_test_case = 'CDRx 5G Sub6 NSA'
        self.power_pdcch_test()

    def test_nr_1_n78_cdrx(self):
        self.display_name_test_case = 'CDRx 5G Sub6 NSA'
        self.power_pdcch_test()
