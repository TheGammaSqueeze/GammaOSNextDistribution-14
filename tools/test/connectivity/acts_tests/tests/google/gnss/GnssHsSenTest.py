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
"""Lab GNSS Hot Start Sensitivity Test"""

import os
from acts_contrib.test_utils.gnss.GnssBlankingBase import GnssBlankingBase
from acts_contrib.test_utils.gnss.dut_log_test_utils import get_gpstool_logs
from acts_contrib.test_utils.gnss.gnss_test_utils import execute_eecoexer_function


class GnssHsSenTest(GnssBlankingBase):
    """ LAB GNSS Cellular coex hot start sensitivity search"""

    def __init__(self, controllers):
        super().__init__(controllers)
        self.cell_tx_ant = None
        self.cell_pwr = None
        self.eecoex_func = ''
        self.coex_stop_cmd = ''
        self.coex_params = {}

    def setup_class(self):
        super().setup_class()
        self.coex_params = self.user_params.get('coex_params', {})
        self.cell_tx_ant = self.coex_params.get('cell_tx_ant', 'PRIMARY')
        self.cell_pwr = self.coex_params.get('cell_pwr', 'Infinity')

    def gnss_hot_start_sensitivity_search_base(self, coex_enable=False):
        """
        Perform GNSS hot start sensitivity search.

        Args:
                cellular_enable: argument to identify if Tx cellular signal is required or not.
                Type, bool.
                Default, False.
        """
        # Get parameters from user_params.
        first_wait = self.user_params.get('first_wait', 300)
        wait_between_pwr = self.user_params.get('wait_between_pwr', 60)
        ttft_iteration = self.user_params.get('ttff_iteration', 25)

        # Start the test item with gnss_init_power_setting.
        ret, pwr_lvl = self.gnss_init_power_setting(first_wait)
        if ret:
            self.log.info(f'Successfully set the GNSS power level to {pwr_lvl}')
            # Create gnss log folders for init and cellular sweep
            gnss_init_log_dir = os.path.join(self.gnss_log_path, 'GNSS_init')

            # Pull all exist GPStool logs into GNSS_init folder
            get_gpstool_logs(self.dut, gnss_init_log_dir, False)
        if coex_enable:
            self.log.info('Start coexistence test.')
            eecoex_cmd_file_str = self.eecoex_func.replace(',', '_')
            execute_eecoexer_function(self.dut, self.eecoex_func)
        else:
            self.log.info('Start stand alone test.')
            eecoex_cmd_file_str = 'Stand_alone'
        for i, gnss_pwr_swp in enumerate(self.gnss_pwr_sweep_fine_sweep_ls):
            self.log.info(f'Start fine GNSS power level sweep part {i + 1}')
            result, sensitivity = self.hot_start_gnss_power_sweep(
                gnss_pwr_swp, wait_between_pwr, ttft_iteration, True,
                eecoex_cmd_file_str)
            if not result:
                break
        self.log.info(f'The sensitivity level is: {sensitivity}')

    def test_hot_start_sensitivity_search(self):
        """
        GNSS hot start stand alone sensitivity search.
        """
        self.gnss_hot_start_sensitivity_search_base(False)

    def test_hot_start_sensitivity_search_gsm850(self):
        """
        GNSS hot start GSM850 Ch190 coexistence sensitivity search.
        """
        self.eecoex_func = f'CELLR,2,850,190,1,{self.cell_tx_ant},{self.cell_pwr}'
        self.coex_stop_cmd = 'CELLR,19'
        msg = f'Running GSM850 with {self.cell_tx_ant} antenna \
                and GNSS coexistence sensitivity search.'

        self.log.info(msg)
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_gsm900(self):
        """
        GNSS hot start GSM900 Ch20 coexistence sensitivity search.
        """
        self.eecoex_func = f'CELLR,2,900,20,1,{self.cell_tx_ant},{self.cell_pwr}'
        self.coex_stop_cmd = 'CELLR,19'
        msg = f'Running GSM900 with {self.cell_tx_ant} \
                antenna and GNSS coexistence sensitivity search.'

        self.log.info(msg)
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_gsm1800(self):
        """
        GNSS hot start GSM1800 Ch699 coexistence sensitivity search.
        """
        self.eecoex_func = f'CELLR,2,1800,699,1,{self.cell_tx_ant},{self.cell_pwr}'
        self.coex_stop_cmd = 'CELLR,19'
        msg = f'Running GSM1800 {self.cell_tx_ant} antenna and GNSS coexistence sensitivity search.'
        self.log.info(msg)
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_gsm1900(self):
        """
        GNSS hot start GSM1900 Ch661 coexistence sensitivity search.
        """
        self.eecoex_func = f'CELLR,2,1900,661,1,{self.cell_tx_ant},{self.cell_pwr}'
        self.coex_stop_cmd = 'CELLR,19'
        msg = f'Running GSM1900 {self.cell_tx_ant} antenna and GNSS coexistence sensitivity search.'
        self.log.info(msg)
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_lte_b38(self):
        """
        GNSS hot start LTE B38 Ch38000 coexistence sensitivity search.
        """
        self.eecoex_func = f'CELLR,5,38,38000,true,{self.cell_tx_ant},{self.cell_pwr},10MHz,0,12'
        self.coex_stop_cmd = 'CELLR,19'
        msg = f'Running LTE B38 {self.cell_tx_ant} antenna and GNSS coexistence sensitivity search.'
        self.log.info(msg)
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_lte_b39(self):
        """
        GNSS hot start LTE B39 Ch38450 coexistence sensitivity search.
        """
        self.eecoex_func = f'CELLR,5,39,38450,true,{self.cell_tx_ant},{self.cell_pwr},10MHz,0,12'
        self.coex_stop_cmd = 'CELLR,19'
        msg = f'Running LTE B38 {self.cell_tx_ant} antenna and GNSS coexistence sensitivity search.'
        self.log.info(msg)
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_lte_b40(self):
        """
        GNSS hot start LTE B40 Ch39150 coexistence sensitivity search.
        """
        self.eecoex_func = f'CELLR,5,40,39150,true,{self.cell_tx_ant},{self.cell_pwr},10MHz,0,12'
        self.coex_stop_cmd = 'CELLR,19'
        msg = f'Running LTE B38 {self.cell_tx_ant} antenna and GNSS coexistence sensitivity search.'
        self.log.info(msg)
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_lte_b41(self):
        """
        GNSS hot start LTE B41 Ch40620 coexistence sensitivity search.
        """
        self.eecoex_func = f'CELLR,5,41,40620,true,{self.cell_tx_ant},{self.cell_pwr},10MHz,0,12'
        self.coex_stop_cmd = 'CELLR,19'
        msg = f'Running LTE B41 {self.cell_tx_ant} antenna and GNSS coexistence sensitivity search.'
        self.log.info(msg)
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_lte_b42(self):
        """
        GNSS hot start LTE B42 Ch42590 coexistence sensitivity search.
        """
        self.eecoex_func = f'CELLR,5,42,42590,true,{self.cell_tx_ant},{self.cell_pwr},10MHz,0,12'
        self.coex_stop_cmd = 'CELLR,19'
        msg = f'Running LTE B42 {self.cell_tx_ant} antenna and GNSS coexistence sensitivity search.'
        self.log.info(msg)
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_lte_b48(self):
        """
        GNSS hot start LTE B48 Ch55990 coexistence sensitivity search.
        """
        self.eecoex_func = f'CELLR,5,48,55990,true,{self.cell_tx_ant},{self.cell_pwr},10MHz,0,12'
        self.coex_stop_cmd = 'CELLR,19'
        msg = f'Running LTE B48 {self.cell_tx_ant} antenna and GNSS coexistence sensitivity search.'
        self.log.info(msg)
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_search_fr2_n2605(self):
        """
        GNSS hot start 5G NR B260 CH2234165 coexistence sensitivity search.
        """
        self.eecoex_func = f'CELLR,30,260,2234165,183,{self.cell_pwr}'
        self.coex_stop_cmd = 'CELLR,19'
        msg = 'Running 5G NR B260 CH2234165 and GNSS coexistence sensitivity search.'
        self.log.info(msg)
        self.gnss_hot_start_sensitivity_search_base(True)

    def test_hot_start_sensitivity_custom_case(self):
        """
        GNSS hot start custom case coexistence sensitivity search.
        """
        cust_cmd = self.coex_params.get('custom_cmd', '')
        cust_stop_cmd = self.coex_params.get('custom_stop_cmd', '')
        if cust_cmd and cust_stop_cmd:
            self.eecoex_func = cust_cmd
            self.coex_stop_cmd = cust_stop_cmd
            msg = f'Running custom {self.eecoex_func} and GNSS coexistence sensitivity search.'
            self.log.info(msg)
            self.gnss_hot_start_sensitivity_search_base(True)
        else:
            self.log.warning('No custom coex command is provided')
