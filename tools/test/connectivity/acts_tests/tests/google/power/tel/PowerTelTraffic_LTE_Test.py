#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
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


class PowerTelTraffic_LTE_Test(ctpt.PowerTelTrafficTest):

    @property
    def display_name_test_suite(self):
        return 'TechEng LTE Traffic'

    def test_lte_traffic_band_12_pul_low_bw_10_tm_4_mimo_2x2_pattern_75_25_1(self):
        self.display_name_test_case = 'LTE traffic - Band 12, low UL power, 10 MHz BW, TM3 2x2, 75/25 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_12_pul_max_bw_5_tm_1_mimo_1x1_pattern_0_100_2(self):
        self.display_name_test_case = 'LTE traffic - Band 12, max UL power, 5 MHz BW, TM3 1x1, 0/100 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_12_pul_low_bw_14_tm_1_mimo_1x1_pattern_0_100_3(self):
        self.display_name_test_case = 'LTE traffic - Band 12, low UL power, 14 MHz BW, TM3 1x1, 0/100 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_20_pul_low_bw_5_tm_3_mimo_2x2_pattern_100_0_4(self):
        self.display_name_test_case = 'LTE traffic - Band 20, low UL power, 5 MHz BW, TM3 2x2, 100/0 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_13_pul_low_bw_5_tm_1_mimo_1x1_pattern_75_25_5(self):
        self.display_name_test_case = 'LTE traffic - Band 13, low UL power, 5 MHz BW, TM3 1x1, 75/25 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_13_pul_max_bw_10_tm_1_mimo_1x1_pattern_100_100_6(self):
        self.display_name_test_case = 'LTE traffic - Band 13, max UL power, 10 MHz BW, TM3 1x1, 100/100 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_5_pul_low_bw_10_tm_4_mimo_2x2_pattern_75_25_7(self):
        self.display_name_test_case = 'LTE traffic - Band 5, low UL power, 10 MHz BW, TM3 2x2, 75/25 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_1_pul_medium_bw_20_tm_3_mimo_4x4_pattern_100_0_8(self):
        self.display_name_test_case = 'LTE traffic - Band 1, medium UL power, 20 MHz BW, TM3 4x4, 100/0 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_1_pul_low_bw_10_tm_4_mimo_2x2_pattern_75_25_9(self):
        self.display_name_test_case = 'LTE traffic - Band 1, low UL power, 10 MHz BW, TM3 2x2, 75/25 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_3_pul_low_bw_10_tm_4_mimo_2x2_pattern_75_25_10(self):
        self.display_name_test_case = 'LTE traffic - Band 3, low UL power, 10 MHz BW, TM3 2x2, 75/25 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_3_pul_max_bw_10_tm_1_mimo_1x1_pattern_100_100_11(self):
        self.display_name_test_case = 'LTE traffic - Band 3, max UL power, 10 MHz BW, TM3 1x1, 100/100 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_2_pul_low_bw_3_tm_1_mimo_1x1_pattern_0_100_12(self):
        self.display_name_test_case = 'LTE traffic - Band 2, low UL power, 3 MHz BW, TM3 1x1, 0/100 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_2_pul_low_bw_10_tm_4_mimo_2x2_pattern_75_25_13(self):
        self.display_name_test_case = 'LTE traffic - Band 2, low UL power, 10 MHz BW, TM3 2x2, 75/25 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_2_pul_medium_bw_20_tm_3_mimo_4x4_pattern_100_0_14(self):
        self.display_name_test_case = 'LTE traffic - Band 2, medium UL power, 20 MHz BW, TM3 4x4, 100/0 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_4_pul_low_bw_10_tm_4_mimo_2x2_pattern_75_25_15(self):
        self.display_name_test_case = 'LTE traffic - Band 4, low UL power, 10 MHz BW, TM3 2x2, 75/25 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_4_pul_low_bw_5_tm_3_mimo_4x4_pattern_100_0_16(self):
        self.display_name_test_case = 'LTE traffic - Band 4, low UL power, 5 MHz BW, TM3 4x4, 100/0 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_4_pul_max_bw_5_tm_3_mimo_4x4_pattern_100_100_17(self):
        self.display_name_test_case = 'LTE traffic - Band 4, max UL power, 5 MHz BW, TM3 4x4, 100/100 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_4_pul_medium_bw_10_tm_3_mimo_4x4_pattern_100_0_18(self):
        self.display_name_test_case = 'LTE traffic - Band 4, medium UL power, 10 MHz BW, TM3 4x4, 100/0 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_4_pul_medium_bw_20_tm_3_mimo_4x4_pattern_100_0_19(self):
        self.display_name_test_case = 'LTE traffic - Band 4, medium UL power, 20 MHz BW, TM3 4x4, 100/0 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_4_pul_max_bw_20_tm_1_mimo_1x1_pattern_100_100_20(self):
        self.display_name_test_case = 'LTE traffic - Band 4, max UL power, 20 MHz BW, TM3 1x1, 100/100pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_7_pul_high_bw_15_tm_1_mimo_1x1_pattern_0_100_21(self):
        self.display_name_test_case = 'LTE traffic - Band 7, high UL power, 15 MHz BW, TM3 1x1, 0/100 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_7_pul_high_bw_20_tm_1_mimo_1x1_pattern_0_100_22(self):
        self.display_name_test_case = 'LTE traffic - Band 7, high UL power, 20 MHz BW, TM3 1x1, 0/100 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_7_pul_max_bw_10_tm_1_mimo_1x1_pattern_100_100_23(self):
        self.display_name_test_case = 'LTE traffic - Band 7, max UL power, 10 MHz BW, TM3 1x1, 100/100 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_7_pul_max_bw_20_tm_1_mimo_1x1_pattern_100_100_24(self):
        self.display_name_test_case = 'LTE traffic - Band 7, max UL power, 20 MHz BW, TM3 1x1, 100/100 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_7_pul_low_bw_10_tm_4_mimo_2x2_pattern_75_25_25(self):
        self.display_name_test_case = 'LTE traffic - Band 7, low UL power, 10 MHz BW, TM3 2x2, 75/25 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_7_pul_medium_bw_10_tm_3_mimo_4x4_pattern_100_0_26(self):
        self.display_name_test_case = 'LTE traffic - Band 7, medium UL power, 10 MHz BW, TM3 4x4, 100/0 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_7_pul_medium_bw_20_tm_3_mimo_4x4_pattern_100_0_27(self):
        self.display_name_test_case = 'LTE traffic - Band 7, medium UL power, 20 MHz BW, TM3 4x4, 100/0 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_38_pul_medium_bw_20_tm_3_mimo_4x4_tddconfig_2_28(self):
        self.display_name_test_case = 'LTE traffic - Band 38, medium UL power, 20 MHz BW, TM3 4x4, TDD 2 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_38_pul_max_bw_10_tm_1_mimo_1x1_tddconfig_1_29(self):
        self.display_name_test_case = 'LTE traffic - Band 38, max UL power, 10 MHz BW, TM3 1x1, TDD 1 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_38_pul_high_bw_5_tm_1_mimo_1x1_tddconfig_5_30(self):
        self.display_name_test_case = 'LTE traffic - Band 38, high UL power, 5 MHz BW, TM3 1x1, TDD 5 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_40_pul_low_bw_20_tm_4_mimo_2x2_tddconfig_2_31(self):
        self.display_name_test_case = 'LTE traffic - Band 40, low UL power, 20 MHz BW, TM3 2x2, TDD 2 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_40_pul_max_bw_10_tm_1_mimo_1x1_tddconfig_5_32(self):
        self.display_name_test_case = 'LTE traffic - Band 40, max UL power, 10 MHz BW, TM3 1x1, TDD 5 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_41_pul_medium_bw_20_tm_3_mimo_4x4_tddconfig_2_33(self):
        self.display_name_test_case = 'LTE traffic - Band 41, medium UL power, 20 MHz BW, TM3 4x4, TDD 2 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_41_pul_high_bw_15_tm_1_mimo_1x1_tddconfig_1_34(self):
        self.display_name_test_case = 'LTE traffic - Band 41, high UL power, 15 MHz BW, TM3 1x1, TDD 1 pattern'
        self.power_tel_traffic_test()

    def test_lte_traffic_band_42_pul_low_bw_20_tm_4_mimo_2x2_tddconfig_2_35(self):
        self.display_name_test_case = 'LTE traffic - Band 42, low UL power, 20 MHz BW, TM3 2x2, TDD 2 pattern'
        self.power_tel_traffic_test()
