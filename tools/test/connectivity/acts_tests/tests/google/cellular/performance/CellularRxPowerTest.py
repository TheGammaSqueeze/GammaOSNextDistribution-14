#!/usr/bin/env python3.4
#
#   Copyright 2022 - The Android Open Source Project
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

import collections
import itertools
import json
import numpy
import os
import time
from acts import asserts
from acts import context
from acts import base_test
from acts import utils
from acts.metrics.loggers.blackbox import BlackboxMappedMetricLogger
from acts_contrib.test_utils.cellular.keysight_5g_testapp import Keysight5GTestApp
from acts_contrib.test_utils.cellular.performance import cellular_performance_test_utils as cputils
from acts_contrib.test_utils.cellular.performance.shannon_log_parser import ShannonLogger
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils
from acts_contrib.test_utils.wifi.wifi_performance_test_utils.bokeh_figure import BokehFigure
from functools import partial


class CellularRxPowerTest(base_test.BaseTestClass):
    """Class to test cellular throughput."""

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        self.tests = self.generate_test_cases(['N257', 'N258', 'N260', 'N261'],
                                              list(range(1, 9)))

    def setup_class(self):
        """Initializes common test hardware and parameters.

        This function initializes hardwares and compiles parameters that are
        common to all tests in this class.
        """
        self.dut = self.android_devices[-1]
        self.testclass_params = self.user_params['rx_power_params']
        self.keysight_test_app = Keysight5GTestApp(
            self.user_params['Keysight5GTestApp'])
        self.sdm_logger = ShannonLogger(self.dut)
        self.testclass_results = collections.OrderedDict()
        # Configure test retries
        self.user_params['retry_tests'] = [self.__class__.__name__]

        # Turn Airplane mode on
        asserts.assert_true(utils.force_airplane_mode(self.dut, True),
                            'Can not turn on airplane mode.')

    def teardown_class(self):
        self.log.info('Turning airplane mode on')
        asserts.assert_true(utils.force_airplane_mode(self.dut, True),
                            'Can not turn on airplane mode.')
        self.keysight_test_app.set_cell_state('LTE', 1, 0)
        self.keysight_test_app.destroy()

    def setup_test(self):
        cputils.start_pixel_logger(self.dut)

    def on_retry(self):
        """Function to control test logic on retried tests.

        This function is automatically executed on tests that are being
        retried. In this case the function resets wifi, toggles it off and on
        and sets a retry_flag to enable further tweaking the test logic on
        second attempts.
        """
        asserts.assert_true(utils.force_airplane_mode(self.dut, True),
                            'Can not turn on airplane mode.')
        if self.keysight_test_app.get_cell_state('LTE', 'CELL1'):
            self.log.info('Turning LTE off.')
            self.keysight_test_app.set_cell_state('LTE', 'CELL1', 0)

    def teardown_test(self):
        self.log.info('Turning airplane mode on')
        asserts.assert_true(utils.force_airplane_mode(self.dut, True),
                            'Can not turn on airplane mode.')
        log_path = os.path.join(
            context.get_current_context().get_full_output_path(), 'pixel_logs')
        os.makedirs(log_path, exist_ok=True)
        self.log.info(self.current_test_info)
        self.testclass_results.setdefault(self.current_test_name,
                                          collections.OrderedDict())
        self.testclass_results[self.current_test_name].setdefault(
            'log_path', [])
        self.testclass_results[self.current_test_name]['log_path'].append(
            cputils.stop_pixel_logger(self.dut, log_path))
        self.process_test_results()

    def process_test_results(self):
        test_result = self.testclass_results[self.current_test_name]

        # Save output as text file
        results_file_path = os.path.join(
            self.log_path, '{}.json'.format(self.current_test_name))
        with open(results_file_path, 'w') as results_file:
            json.dump(wputils.serialize_dict(test_result),
                      results_file,
                      indent=4)
        # Plot and save
        if test_result['log_path']:
            log_data = self.sdm_logger.process_log(test_result['log_path'][-1])
        else:
            return
        figure = BokehFigure(title=self.current_test_name,
                             x_label='Cell Power Setting (dBm)',
                             primary_y_label='Time')
        figure.add_line(log_data.lte.rsrp_time, log_data.lte.rsrp_rx0,
                        'LTE RSRP (Rx0)')
        figure.add_line(log_data.lte.rsrp_time, log_data.lte.rsrp_rx1,
                        'LTE RSRP (Rx1)')
        figure.add_line(log_data.lte.rsrp2_time, log_data.lte.rsrp2_rx0,
                        'LTE RSRP2 (Rx0)')
        figure.add_line(log_data.lte.rsrp2_time, log_data.lte.rsrp2_rx1,
                        'LTE RSRP2 (Rx0)')
        figure.add_line(log_data.nr.rsrp_time, log_data.nr.rsrp_rx0,
                        'NR RSRP (Rx0)')
        figure.add_line(log_data.nr.rsrp_time, log_data.nr.rsrp_rx1,
                        'NR RSRP (Rx1)')
        figure.add_line(log_data.nr.rsrp2_time, log_data.nr.rsrp2_rx0,
                        'NR RSRP2 (Rx0)')
        figure.add_line(log_data.nr.rsrp2_time, log_data.nr.rsrp2_rx1,
                        'NR RSRP2 (Rx0)')
        figure.add_line(log_data.fr2.rsrp0_time, log_data.fr2.rsrp0,
                        'NR RSRP (Rx0)')
        figure.add_line(log_data.fr2.rsrp1_time, log_data.fr2.rsrp1,
                        'NR RSRP2 (Rx1)')
        output_file_path = os.path.join(
            self.log_path, '{}.html'.format(self.current_test_name))
        figure.generate_figure(output_file_path)

    def _test_nr_rsrp(self, testcase_params):
        """Test function to run cellular RSRP tests.

        The function runs a sweep of cell powers while collecting pixel logs
        for later postprocessing and RSRP analysis.

        Args:
            testcase_params: dict containing test-specific parameters
        """

        result = collections.OrderedDict()
        testcase_params['power_range_vector'] = list(
            numpy.arange(self.testclass_params['cell_power_start'],
                         self.testclass_params['cell_power_stop'],
                         self.testclass_params['cell_power_step']))

        if not self.keysight_test_app.get_cell_state('LTE', 'CELL1'):
            self.log.info('Turning LTE on.')
            self.keysight_test_app.set_cell_state('LTE', 'CELL1', 1)
        self.log.info('Turning off airplane mode')
        asserts.assert_true(utils.force_airplane_mode(self.dut, False),
                            'Can not turn on airplane mode.')

        for cell in testcase_params['dl_cell_list']:
            self.keysight_test_app.set_cell_band('NR5G', cell,
                                                 testcase_params['band'])
        # Consider configuring schedule quick config
        self.keysight_test_app.set_nr_cell_schedule_scenario(
            testcase_params['dl_cell_list'][0], 'BASIC')
        self.keysight_test_app.set_dl_carriers(testcase_params['dl_cell_list'])
        self.keysight_test_app.set_ul_carriers(
            testcase_params['dl_cell_list'][0])
        self.log.info('Waiting for LTE and applying aggregation')
        if not self.keysight_test_app.wait_for_cell_status(
                'LTE', 'CELL1', 'CONN', 60):
            asserts.fail('DUT did not connect to LTE.')
        self.keysight_test_app.apply_carrier_agg()
        self.log.info('Waiting for 5G connection')
        connected = self.keysight_test_app.wait_for_cell_status(
            'NR5G', testcase_params['dl_cell_list'][-1], ['ACT', 'CONN'], 60)
        if not connected:
            asserts.fail('DUT did not connect to NR.')
        for cell_power in testcase_params['power_range_vector']:
            self.log.info('Setting power to {} dBm'.format(cell_power))
            for cell in testcase_params['dl_cell_list']:
                self.keysight_test_app.set_cell_dl_power(
                    'NR5G', cell, cell_power, True)
            #measure RSRP
            self.keysight_test_app.start_nr_rsrp_measurement(
                testcase_params['dl_cell_list'],
                self.testclass_params['rsrp_measurement_duration'])
            time.sleep(self.testclass_params['rsrp_measurement_duration'] *
                       1.5 / 1000)
            self.keysight_test_app.get_nr_rsrp_measurement_state(
                testcase_params['dl_cell_list'])
            self.keysight_test_app.get_nr_rsrp_measurement_results(
                testcase_params['dl_cell_list'])

        for cell in testcase_params['dl_cell_list'][::-1]:
            self.keysight_test_app.set_cell_state('NR5G', cell, 0)
        asserts.assert_true(utils.force_airplane_mode(self.dut, True),
                            'Can not turn on airplane mode.')
        # Save results
        result['testcase_params'] = testcase_params
        self.testclass_results[self.current_test_name] = result
        results_file_path = os.path.join(
            context.get_current_context().get_full_output_path(),
            '{}.json'.format(self.current_test_name))
        with open(results_file_path, 'w') as results_file:
            json.dump(wputils.serialize_dict(result), results_file, indent=4)

    def generate_test_cases(self, bands, num_cells_list):
        """Function that auto-generates test cases for a test class."""
        test_cases = []

        for band, num_cells in itertools.product(bands, num_cells_list):
            test_name = 'test_nr_rsrp_{}_{}CC'.format(band, num_cells)
            test_params = collections.OrderedDict(band=band,
                                                  num_cells=num_cells,
                                                  dl_cell_list=list(
                                                      range(1, num_cells + 1)))
            setattr(self, test_name, partial(self._test_nr_rsrp, test_params))
            test_cases.append(test_name)
        return test_cases
