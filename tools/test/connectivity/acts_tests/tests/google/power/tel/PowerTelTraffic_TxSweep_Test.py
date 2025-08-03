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


class PowerTelTraffic_TxSweep_Test(ctpt.PowerTelTxPowerSweepTest):

    @property
    def display_name_test_suite(self):
        return 'TechEng Tx Power Sweep'

    def test_lte_band_7_pul_low_bw_20_tm_3_dlmcs_28_mimo_2x2_direction_dlul_phich_16_cfi_1_sweep_n4_22(self):
        self.display_name_test_case = 'Tx Power Sweep - Band 7'
        self.power_tel_tx_sweep()

    def test_lte_band_12_pul_low_bw_10_tm_3_dlmcs_28_mimo_2x2_direction_dlul_phich_16_cfi_1_sweep_n4_22(self):
        self.display_name_test_case = 'Tx Power Sweep - Band 12'
        self.power_tel_tx_sweep()

    def test_lte_band_66_pul_low_bw_20_tm_3_dlmcs_28_mimo_2x2_direction_dlul_phich_16_cfi_1_sweep_n4_22(self):
        self.display_name_test_case = 'Tx Power Sweep - Band 66'
        self.power_tel_tx_sweep()

    def test_lte_band_20_pul_low_bw_20_tm_3_dlmcs_28_mimo_2x2_direction_dlul_phich_16_cfi_1_sweep_n4_22(self):
        self.display_name_test_case = 'Tx Power Sweep - Band 20'
        self.power_tel_tx_sweep()

    def test_lte_band_3_pul_low_bw_20_tm_3_dlmcs_28_mimo_2x2_direction_dlul_phich_16_cfi_1_sweep_n4_23(self):
        self.display_name_test_case = 'Tx Power Sweep - Band 3'
        self.power_tel_tx_sweep()

    def test_lte_band_2_pul_low_bw_20_tm_3_dlmcs_28_mimo_2x2_direction_dlul_phich_16_cfi_1_sweep_n4_22(self):
        self.display_name_test_case = 'Tx Power Sweep - Band 2'
        self.power_tel_tx_sweep()

    def test_lte_band_13_pul_low_bw_10_tm_3_dlmcs_28_mimo_2x2_direction_dlul_phich_16_cfi_1_sweep_n4_22(self):
        self.display_name_test_case = 'Tx Power Sweep - Band 13'
        self.power_tel_tx_sweep()

    def test_lte_band_1_pul_low_bw_20_tm_3_dlmcs_28_mimo_2x2_direction_dlul_phich_16_cfi_1_sweep_n4_23(self):
        self.display_name_test_case = 'Tx Power Sweep - Band 1'
        self.power_tel_tx_sweep()

    def test_lte_band_4_pul_low_bw_20_tm_3_dlmcs_28_mimo_2x2_direction_dlul_phich_16_cfi_1_sweep_n4_22(self):
        self.display_name_test_case = 'Tx Power Sweep - Band 4'
        self.power_tel_tx_sweep()

    def test_lte_band_41_pul_low_bw_20_tm_3_dlmcs_28_mimo_2x2_direction_dlul_phich_16_cfi_1_tddconfig_6_sweep_n4_21(self):
        self.display_name_test_case = 'Tx Power Sweep - Band 41, TDD 6 pattern'
        self.power_tel_tx_sweep()

