#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
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

from acts_contrib.test_utils.gnss.GnssBlankingBase import GnssBlankingBase


class GnssBlankingThTest(GnssBlankingBase):
    """ LAB GNSS Cellular Coex Tx Power Sweep TTFF/FFPE Tests"""

    def gnss_wwan_blanking_sweep_base(self):
        """
        GNSS WWAN blanking cellular power sweep base function
        """
        # Get parameters from user params.
        first_wait = self.user_params.get('first_wait', 300)

        # Start the test item with gnss_init_power_setting.
        ret, pwr_lvl = self.gnss_init_power_setting(first_wait)
        if ret:
            self.log.info(f'Successfully set the GNSS power level to {pwr_lvl}')
            self.log.info('Start searching for cellular power level threshold')
            # After the GNSS power initialization is done, start the cellular power sweep.
            self.result_cell_pwr = self.cell_power_sweep()
        else:
            raise AttributeError('Init power sweep is missing')

    def test_gnss_gsm850_sweep(self):
        """
        GNSS WWAN blanking cellular power sweep GSM850, Ch190.
        """
        self.eecoex_func = 'CELLR,2,850,190,1,1,{}'
        self.start_pwr = self.gsm_sweep_params[0]
        self.stop_pwr = self.gsm_sweep_params[1]
        self.offset = self.gsm_sweep_params[2]
        self.gnss_wwan_blanking_sweep_base()

    def test_gnss_gsm900_sweep(self):
        """
        GNSS WWAN blanking cellular power sweep GSM900, Ch20.
        """
        self.eecoex_func = 'CELLR,2,900,20,1,1,{}'
        self.start_pwr = self.gsm_sweep_params[0]
        self.stop_pwr = self.gsm_sweep_params[1]
        self.offset = self.gsm_sweep_params[2]
        self.gnss_wwan_blanking_sweep_base()

    def test_gnss_gsm1800_sweep(self):
        """
        GNSS WWAN blanking cellular power sweep GSM1800, Ch699.
        """
        self.eecoex_func = 'CELLR,2,1800,699,1,1,{}'
        self.start_pwr = self.gsm_sweep_params[0]
        self.stop_pwr = self.gsm_sweep_params[1]
        self.offset = self.gsm_sweep_params[2]
        self.gnss_wwan_blanking_sweep_base()

    def test_gnss_gsm1900_sweep(self):
        """
        GNSS WWAN blanking cellular power sweep GSM1900, Ch661.
        """
        self.eecoex_func = 'CELLR,2,1900,661,1,1,{}'
        self.start_pwr = self.gsm_sweep_params[0]
        self.stop_pwr = self.gsm_sweep_params[1]
        self.offset = self.gsm_sweep_params[2]
        self.gnss_wwan_blanking_sweep_base()

    def test_gnss_lte_b38_sweep(self):
        """
        GNSS WWAN blanking cellular power sweep LTE-TDD, B38, 10M, 12RB@0, Ch38000.
        """
        self.eecoex_func = 'CELLR,5,38,38000,true,PRIMARY,{},10MHz,0,12'
        self.start_pwr = self.lte_tdd_pc3_sweep_params[0]
        self.stop_pwr = self.lte_tdd_pc3_sweep_params[1]
        self.offset = self.lte_tdd_pc3_sweep_params[2]
        self.gnss_wwan_blanking_sweep_base()

    def test_gnss_lte_b39_sweep(self):
        """
        GNSS WWAN blanking cellular power sweep LTE-TDD, B39, 10M, 12RB@0, Ch38450.
        """
        self.eecoex_func = 'CELLR,5,39,38450,true,PRIMARY,{},10MHz,0,12'
        self.start_pwr = self.lte_tdd_pc3_sweep_params[0]
        self.stop_pwr = self.lte_tdd_pc3_sweep_params[1]
        self.offset = self.lte_tdd_pc3_sweep_params[2]
        self.gnss_wwan_blanking_sweep_base()

    def test_gnss_lte_b40_sweep(self):
        """
        GNSS WWAN blanking cellular power sweep LTE-TDD, B40, 10M, 12RB@0, Ch39150.
        """
        self.eecoex_func = 'CELLR,5,40,39150,true,PRIMARY,{},10MHz,0,12'
        self.start_pwr = self.lte_tdd_pc3_sweep_params[0]
        self.stop_pwr = self.lte_tdd_pc3_sweep_params[1]
        self.offset = self.lte_tdd_pc3_sweep_params[2]
        self.gnss_wwan_blanking_sweep_base()

    def test_gnss_lte_b41_sweep(self):
        """
        GNSS WWAN blanking cellular power sweep LTE-TDD, B41, 10M, 12RB@0, Ch40620.
        """
        self.eecoex_func = 'CELLR,5,41,40620,true,PRIMARY,{},10MHz,0,12'
        self.start_pwr = self.lte_tdd_pc3_sweep_params[0]
        self.stop_pwr = self.lte_tdd_pc3_sweep_params[1]
        self.offset = self.lte_tdd_pc3_sweep_params[2]
        self.gnss_wwan_blanking_sweep_base()

    def test_gnss_lte_b42_sweep(self):
        """
        GNSS WWAN blanking cellular power sweep LTE-TDD, B42, 10M, 12RB@0, Ch42590.
        """
        self.eecoex_func = 'CELLR,5,42,42590,true,PRIMARY,{},10MHz,0,12'
        self.start_pwr = self.lte_tdd_pc3_sweep_params[0]
        self.stop_pwr = self.lte_tdd_pc3_sweep_params[1]
        self.offset = self.lte_tdd_pc3_sweep_params[2]
        self.gnss_wwan_blanking_sweep_base()

    def test_gnss_lte_b48_sweep(self):
        """
        GNSS WWAN blanking cellular power sweep LTE-TDD, B48, 10M, 12RB@0, Ch55990.
        """
        self.eecoex_func = 'CELLR,5,48,55990,true,PRIMARY,{},10MHz,0,12'
        self.start_pwr = self.lte_tdd_pc3_sweep_params[0]
        self.stop_pwr = self.lte_tdd_pc3_sweep_params[1]
        self.offset = self.lte_tdd_pc3_sweep_params[2]
        self.gnss_wwan_blanking_sweep_base()

    def test_gnss_stand_alone_gnss(self):
        """
        GNSS stand alone test item.
        """
        self.eecoex_func = ''
        self.start_pwr = 0
        self.stop_pwr = 0
        self.offset = 0
        self.gnss_wwan_blanking_sweep_base()
