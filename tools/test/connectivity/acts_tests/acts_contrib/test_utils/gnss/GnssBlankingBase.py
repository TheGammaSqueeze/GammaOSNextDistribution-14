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
'''GNSS Base Class for Blanking and Hot Start Sensitivity Search'''

import os
import re
from time import sleep
from collections import namedtuple
from itertools import product
from numpy import arange
from pandas import DataFrame, merge
from acts.signals import TestError
from acts.signals import TestFailure
from acts.logger import epoch_to_log_line_timestamp
from acts.context import get_current_context
from acts_contrib.test_utils.gnss import LabTtffTestBase as lttb
from acts_contrib.test_utils.gnss.LabTtffTestBase import glob_re
from acts_contrib.test_utils.gnss.gnss_test_utils import launch_eecoexer
from acts_contrib.test_utils.gnss.gnss_test_utils import execute_eecoexer_function
from acts_contrib.test_utils.gnss.gnss_test_utils import start_gnss_by_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import get_current_epoch_time
from acts_contrib.test_utils.gnss.gnss_test_utils import check_current_focus_app
from acts_contrib.test_utils.gnss.gnss_test_utils import process_ttff_by_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import check_ttff_data
from acts_contrib.test_utils.gnss.gnss_test_utils import process_gnss_by_gtw_gpstool
from acts_contrib.test_utils.gnss.dut_log_test_utils import get_gpstool_logs
from acts_contrib.test_utils.gnss.gnss_testlog_utils import parse_gpstool_ttfflog_to_df


def range_wi_end(dut, start, stop, step):
    """
    Generate a list of data from start to stop with the step. The list includes start and stop value
    and also supports floating point.
    Args:
        dut: An AndroidDevice object.
        start: start value.
            Type, int or float.
        stop: stop value.
            Type, int or float.
        step: step value.
            Type, int or float.
    Returns:
        range_ls: the list of data.
    """
    if step == 0:
        dut.log.warn('Step is 0. Return empty list')
        range_ls = []
    else:
        if start == stop:
            range_ls = [stop]
        else:
            range_ls = list(arange(start, stop, step))
            if len(range_ls) > 0:
                if (step < 0 and range_ls[-1] > stop) or (step > 0 and
                                                          range_ls[-1] < stop):
                    range_ls.append(stop)
    dut.log.debug(f'The range list is: {range_ls}')
    return range_ls


def check_ttff_pe(dut, ttff_data, ttff_mode, pe_criteria):
    """Verify all TTFF results from ttff_data.

    Args:
        dut: An AndroidDevice object.
        ttff_data: TTFF data of secs, position error and signal strength.
        ttff_mode: TTFF Test mode for current test item.
        pe_criteria: Criteria for current test item.

    """
    ret = True
    no_iteration = len(ttff_data.keys())
    dut.log.info(
        f'{no_iteration} iterations of TTFF {ttff_mode} tests finished.')
    dut.log.info(f'{ttff_mode} PASS criteria is {pe_criteria} meters')
    dut.log.debug(f'{ttff_mode} TTFF data: {ttff_data}')

    if len(ttff_data.keys()) == 0:
        dut.log.error("GTW_GPSTool didn't process TTFF properly.")
        raise TestFailure("GTW_GPSTool didn't process TTFF properly.")

    if any(
            float(ttff_data[key].ttff_pe) >= pe_criteria
            for key in ttff_data.keys()):
        dut.log.error(
            f'One or more TTFF {ttff_mode} are over test criteria {pe_criteria} meters'
        )
        ret = False
    else:
        dut.log.info(
            f'All TTFF {ttff_mode} are within test criteria {pe_criteria} meters.'
        )
        ret = True
    return ret


class GnssBlankingBase(lttb.LabTtffTestBase):
    """ LAB GNSS Cellular Coex Tx Power Sweep TTFF/FFPE Tests"""
    GNSS_PWR_SWEEP = 'gnss_pwr_sweep'
    CELL_PWR_SWEEP = 'cell_pwr_sweep'

    def __init__(self, controllers):
        """ Initializes class attributes. """
        super().__init__(controllers)
        self.eecoex_func = ''
        self.start_pwr = 10
        self.stop_pwr = 24
        self.offset = 1
        self.result_cell_pwr = 10
        self.gsm_sweep_params = None
        self.lte_tdd_pc3_sweep_params = None
        self.lte_tdd_pc2_sweep_params = None
        self.coex_stop_cmd = None
        self.scen_sweep = False
        self.gnss_pwr_sweep_init_ls = []
        self.gnss_pwr_sweep_fine_sweep_ls = []

    def setup_class(self):
        super().setup_class()

        # Required parameters
        req_params = [self.GNSS_PWR_SWEEP]
        self.unpack_userparams(req_param_names=req_params)
        self.unpack_gnss_pwr_sweep()

        # Optional parameters
        cell_sweep_params = self.user_params.get(self.CELL_PWR_SWEEP, [])

        if cell_sweep_params:
            self.gsm_sweep_params = cell_sweep_params.get("GSM", [10, 33, 1])
            self.lte_tdd_pc3_sweep_params = cell_sweep_params.get(
                "LTE_TDD_PC3", [10, 24, 1])
            self.lte_tdd_pc2_sweep_params = cell_sweep_params.get(
                "LTE_TDD_PC2", [10, 26, 1])

    def setup_test(self):
        super().setup_test()
        launch_eecoexer(self.dut)

        # Set DUT temperature the limit to 60 degree
        self.dut.adb.shell(
            'setprop persist.com.google.eecoexer.cellular.temperature_limit 60')

        # Get current context full path to create the log folder.
        cur_test_item_dir = get_current_context().get_full_output_path()
        self.gnss_log_path = os.path.join(self.log_path, cur_test_item_dir)
        os.makedirs(self.gnss_log_path, exist_ok=True)

        ## Start GNSS chip log
        self.start_dut_gnss_log()

    def teardown_test(self):
        super().teardown_test()
        # Set gnss_vendor_log_path based on GNSS solution vendor.
        gnss_vendor_log_path = os.path.join(self.gnss_log_path,
                                            self.diag_option)
        os.makedirs(gnss_vendor_log_path, exist_ok=True)

        # Stop GNSS chip log and pull the logs to local file system
        self.stop_and_pull_dut_gnss_log(gnss_vendor_log_path)

        # Stop cellular Tx and close GPStool and EEcoexer APPs.
        self.stop_coex_tx()
        self.log.debug('Close GPStool APP')
        self.dut.force_stop_apk("com.android.gpstool")
        self.log.debug('Close EEcoexer APP')
        self.dut.force_stop_apk("com.google.eecoexer")

    def derive_sweep_list(self, data):
        """
        Derive sweep list from config
        Args:
            data: GNSS simulator scenario power setting.
                type, dictionary.
        """
        match_tag = r'(?P<sat>[a-z]+)_(?P<band>[a-z]+\d\S*)'
        sweep_all_ls = []
        set_all_ls = []
        regex_match = re.compile(match_tag)
        method = data.get('method')
        for key, value in data.items():
            result = regex_match.search(key)
            if result:
                set_all_ls.append(result.groupdict())
                sweep_all_ls.append(
                    range_wi_end(self.dut, value[0], value[1], value[2]))
        if method == 'product':
            swp_result_ls = list(product(*sweep_all_ls))
        else:
            swp_result_ls = list(zip(*sweep_all_ls))

        self.log.debug(f'set_all_ls: {set_all_ls}')
        self.log.debug(f'swp_result_ls: {swp_result_ls}')
        return set_all_ls, swp_result_ls

    def unpack_gnss_pwr_sweep(self):
        """ Unpack gnss_pwr_sweep and construct sweep parameters
        """

        for key, value in self.gnss_pwr_sweep.items():
            if key == 'init':
                self.gnss_pwr_sweep_init_ls = []
                self.log.info(f'Sweep: {value}')
                result = self.derive_sweep_list(value)
                self.gnss_pwr_sweep_init_ls.append(result)
            elif key == 'fine_sweep':
                self.gnss_pwr_sweep_fine_sweep_ls = []
                self.log.info(f'Sweep: {value}')
                result = self.derive_sweep_list(value)
                self.gnss_pwr_sweep_fine_sweep_ls.append(result)
            else:
                self.log.error(f'{key} is a unsupported key in gnss_pwr_sweep.')

    def stop_coex_tx(self):
        """
        Stop EEcoexer Tx power.
        """
        # Stop cellular Tx by EEcoexer.
        if self.coex_stop_cmd:
            self.log.info(f'Stop EEcoexer Test Command: {self.coex_stop_cmd}')
            execute_eecoexer_function(self.dut, self.coex_stop_cmd)

    def analysis_ttff_ffpe(self, ttff_data, json_tag=''):
        """
        Pull logs and parsing logs into json file.
        Args:
            ttff_data: ttff_data from test results.
                Type, list.
            json_tag: tag for parsed json file name.
                Type, str.
        """
        # Create log directory.
        gps_log_path = os.path.join(self.gnss_log_path,
                                    'Cell_Pwr_Sweep_Results')

        # Pull logs of GTW GPStool.
        get_gpstool_logs(self.dut, gps_log_path, False)

        # Parsing the log of GTW GPStool into pandas dataframe.
        target_dir = os.path.join(gps_log_path, 'GPSLogs', 'files')
        gps_api_log_ls = glob_re(self.dut, target_dir, r'GNSS_\d+')
        latest_gps_api_log = max(gps_api_log_ls, key=os.path.getctime)
        self.log.info(f'Get latest GPStool log is: {latest_gps_api_log}')
        df_ttff_ffpe = DataFrame(
            parse_gpstool_ttfflog_to_df(latest_gps_api_log))
        # Add test case, TTFF and FFPE data into the dataframe.
        ttff_dict = {}
        for i in ttff_data:
            data = ttff_data[i]._asdict()
            ttff_dict[i] = dict(data)

        ttff_data_df = DataFrame(ttff_dict).transpose()
        ttff_data_df = ttff_data_df[[
            'ttff_loop', 'ttff_sec', 'ttff_pe', 'ttff_haccu'
        ]]
        try:
            df_ttff_ffpe = merge(df_ttff_ffpe,
                                 ttff_data_df,
                                 left_on='loop',
                                 right_on='ttff_loop')
        except:  # pylint: disable=bare-except
            self.log.warning("Can't merge ttff_data and df.")
        df_ttff_ffpe['test_case'] = json_tag

        json_file = f'gps_log_{json_tag}.json'
        ttff_data_json_file = f'gps_log_{json_tag}_ttff_data.json'
        json_path = os.path.join(gps_log_path, json_file)
        ttff_data_json_path = os.path.join(gps_log_path, ttff_data_json_file)
        # Save dataframe into json file.
        df_ttff_ffpe.to_json(json_path, orient='table', index=False)
        ttff_data_df.to_json(ttff_data_json_path, orient='table', index=False)

    def hot_start_ttff_ffpe_process(self, iteration, wait):
        """
        Function to run hot start TTFF/FFPE by GTW GPSTool
        Args:
            iteration: TTFF/FFPE test iteration.
                type, integer.
            wait: wait time before the hot start TTFF/FFPE test.
                type, integer.
        """
        # Start GTW GPStool.
        self.dut.log.info("Restart GTW GPSTool")
        start_gnss_by_gtw_gpstool(self.dut, state=True)
        if wait > 0:
            self.log.info(
                f'Wait for {wait} seconds before TTFF to acquire data.')
            sleep(wait)
        # Get current time and convert to human readable format
        begin_time = get_current_epoch_time()
        log_begin_time = epoch_to_log_line_timestamp(begin_time)
        self.dut.log.debug(f'Start time is {log_begin_time}')

        # Run hot start TTFF
        for i in range(3):
            self.log.info(f'Start hot start attempt {i + 1}')
            self.dut.adb.shell(
                f'am broadcast -a com.android.gpstool.ttff_action '
                f'--es ttff hs --es cycle {iteration} --ez raninterval False')
            sleep(1)
            if self.dut.search_logcat(
                    "act=com.android.gpstool.start_test_action", begin_time):
                self.dut.log.info("Send TTFF start_test_action successfully.")
                break
        else:
            check_current_focus_app(self.dut)
            raise TestError("Fail to send TTFF start_test_action.")
        return begin_time

    def gnss_hot_start_ttff_ffpe_test(self,
                                      iteration,
                                      sweep_enable=False,
                                      json_tag='',
                                      wait=0):
        """
        GNSS hot start ttff ffpe tset

        Args:
            iteration: hot start TTFF test iteration.
                    Type, int.
                    Default, 1.
            sweep_enable: Indicator for the function to check if it is run by cell_power_sweep()
                    Type, bool.
                    Default, False.
            json_tag: if the function is run by cell_power_sweep(), the function would use
                    this as a part of file name to save TTFF and FFPE results into json file.
                    Type, str.
                    Default, ''.
            wait: wait time before ttff test.
                    Type, int.
                    Default, 0.
        Raise:
            TestError: fail to send TTFF start_test_action.
        """
        test_type = namedtuple('Type', ['command', 'criteria'])
        test_type_ttff = test_type('Hot Start', self.hs_criteria)
        test_type_pe = test_type('Hot Start', self.hs_ttff_pecriteria)

        # Verify hot start TTFF results
        begin_time = self.hot_start_ttff_ffpe_process(iteration, wait)
        try:
            ttff_data = process_ttff_by_gtw_gpstool(self.dut, begin_time,
                                                    self.simulator_location)
        except:  # pylint: disable=bare-except
            self.log.warning('Fail to acquire TTFF data. Retry again.')
            begin_time = self.hot_start_ttff_ffpe_process(iteration, wait)
            ttff_data = process_ttff_by_gtw_gpstool(self.dut, begin_time,
                                                    self.simulator_location)

        # Stop GTW GPSTool
        self.dut.log.info("Stop GTW GPSTool")
        start_gnss_by_gtw_gpstool(self.dut, state=False)

        if sweep_enable:
            self.analysis_ttff_ffpe(ttff_data, json_tag)

        result_ttff = check_ttff_data(self.dut,
                                      ttff_data,
                                      ttff_mode=test_type_ttff.command,
                                      criteria=test_type_ttff.criteria)
        result_pe = check_ttff_pe(self.dut,
                                  ttff_data,
                                  ttff_mode=test_type_pe.command,
                                  pe_criteria=test_type_pe.criteria)
        if not result_ttff or not result_pe:
            self.dut.log.warning('%s TTFF fails to reach '
                                 'designated criteria' % test_type_ttff.command)
            self.dut.log.info("Stop GTW GPSTool")
            return False

        return True

    def hot_start_gnss_power_sweep(self,
                                   sweep_ls,
                                   wait=0,
                                   iteration=1,
                                   sweep_enable=False,
                                   title=''):
        """
        GNSS simulator power sweep of hot start test.

        Args:
            sweep_ls: list of sweep parameters.
                    Type, tuple.
            wait: Wait time before the power sweep.
                    Type, int.
                    Default, 0.
            iteration: The iteration times of hot start test.
                    Type, int.
                    Default, 1.
            sweep_enable: Indicator for power sweep.
                          It will be True only in GNSS sensitivity search case.
                    Type, bool.
                    Defaule, False.
            title: the target log folder title for GNSS sensitivity search test items.
                    Type, str.
                    Default, ''.
        Return:
            Bool, gnss_pwr_params.
        """

        # Calculate loop range list from gnss_simulator_power_level and sa_sensitivity

        self.log.debug(
            f'Start the GNSS simulator power sweep. The sweep tuple is [{sweep_ls}]'
        )

        if sweep_enable:
            self.start_gnss_and_wait(wait)
        else:
            self.dut.log.info('Wait %d seconds to start TTFF HS' % wait)
            sleep(wait)

        # Sweep GNSS simulator power level in range_ls.
        # Do hot start for every power level.
        # Check the TTFF result if it can pass the criteria.
        gnss_pwr_params = None
        previous_pwr_lvl = None
        current_pwr_lvl = None
        return_pwr_lvl = {}
        for j, gnss_pwr_params in enumerate(sweep_ls[1]):
            json_tag = f'{title}_'
            current_pwr_lvl = gnss_pwr_params
            if j == 0:
                previous_pwr_lvl = current_pwr_lvl
            for i, pwr in enumerate(gnss_pwr_params):
                sat_sys = sweep_ls[0][i].get('sat').upper()
                band = sweep_ls[0][i].get('band').upper()
                # Set GNSS Simulator power level
                self.gnss_simulator.ping_inst()
                self.gnss_simulator.set_scenario_power(power_level=pwr,
                                                       sat_system=sat_sys,
                                                       freq_band=band)
                self.log.info(f'Set {sat_sys} {band} with power {pwr}')
                json_tag = json_tag + f'{sat_sys}_{band}_{pwr}'
            # Wait 30 seconds if major power sweep level is changed.
            wait = 0
            if j > 0:
                if current_pwr_lvl[0] != previous_pwr_lvl[0]:
                    wait = 30
            # GNSS hot start test
            if not self.gnss_hot_start_ttff_ffpe_test(iteration, sweep_enable,
                                                      json_tag, wait):
                result = False
                break
            previous_pwr_lvl = current_pwr_lvl
        result = True
        for i, pwr in enumerate(previous_pwr_lvl):
            key = f'{sweep_ls[0][i].get("sat").upper()}_{sweep_ls[0][i].get("band").upper()}'
            return_pwr_lvl.setdefault(key, pwr)
        return result, return_pwr_lvl

    def gnss_init_power_setting(self, first_wait=180):
        """
        GNSS initial power level setting.
        Args:
            first_wait: wait time after the cold start.
                        Type, int.
                        Default, 180.
        Returns:
            True if the process is done successully and hot start results pass criteria.
        Raise:
            TestFailure: fail TTFF test criteria.
        """

        # Start and set GNSS simulator
        self.start_set_gnss_power()

        # Start 1st time cold start to obtain ephemeris
        process_gnss_by_gtw_gpstool(self.dut, self.test_types['cs'].criteria)

        # Read initial power sweep settings
        if self.gnss_pwr_sweep_init_ls:
            for sweep_ls in self.gnss_pwr_sweep_init_ls:
                ret, gnss_pwr_lvl = self.hot_start_gnss_power_sweep(
                    sweep_ls, first_wait)
        else:
            self.log.warning('Skip initial power sweep.')
            ret = False
            gnss_pwr_lvl = None

        return ret, gnss_pwr_lvl

    def cell_power_sweep(self):
        """
        Linear search cellular power level. Doing GNSS hot start with cellular coexistence
        and checking if hot start can pass hot start criteria or not.

        Returns: final power level of cellular power
        """
        # Get parameters from user params.
        ttft_iteration = self.user_params.get('ttff_iteration', 25)
        wait_before_test = self.user_params.get('wait_before_test', 60)
        wait_between_pwr = self.user_params.get('wait_between_pwr', 60)
        power_th = self.start_pwr

        # Generate the power sweep list.
        power_search_ls = range_wi_end(self.dut, self.start_pwr, self.stop_pwr,
                                       self.offset)

        # Create gnss log folders for init and cellular sweep
        gnss_init_log_dir = os.path.join(self.gnss_log_path, 'GNSS_init')

        # Pull all exist GPStool logs into GNSS_init folder
        get_gpstool_logs(self.dut, gnss_init_log_dir, False)

        if power_search_ls:
            # Run the cellular and GNSS coexistence test item.
            for i, pwr_lvl in enumerate(power_search_ls):
                self.log.info(f'Cellular power sweep loop: {i}')
                self.log.info(f'Cellular target power: {pwr_lvl}')

                # Enable GNSS to receive satellites' signals for "wait_between_pwr" seconds.
                # Wait more time before 1st power level
                if i == 0:
                    wait = wait_before_test
                else:
                    wait = wait_between_pwr
                self.start_gnss_and_wait(wait)

                # Set cellular Tx power level.
                eecoex_cmd = self.eecoex_func.format(pwr_lvl)
                eecoex_cmd_file_str = eecoex_cmd.replace(',', '_')
                execute_eecoexer_function(self.dut, eecoex_cmd)

                # Get the last power level that can pass hots start ttff/ffpe spec.
                if self.gnss_hot_start_ttff_ffpe_test(ttft_iteration, True,
                                                      eecoex_cmd_file_str):
                    if i + 1 == len(power_search_ls):
                        power_th = pwr_lvl
                else:
                    if i == 0:
                        power_th = self.start_pwr
                    else:
                        power_th = power_search_ls[i - 1]

                # Stop cellular Tx after a test cycle.
                self.stop_coex_tx()

        else:
            # Run the stand alone test item.
            self.start_gnss_and_wait(wait_between_pwr)

            eecoex_cmd_file_str = 'no_cellular_coex'
            self.gnss_hot_start_ttff_ffpe_test(ttft_iteration, True,
                                               eecoex_cmd_file_str)

        self.log.info(f'The GNSS WWAN coex celluar Tx power is {power_th}')

        return power_th
