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
import csv
import itertools
import numpy
import json
import re
import os
from acts import context
from acts import base_test
from acts.metrics.loggers.blackbox import BlackboxMappedMetricLogger
from acts_contrib.test_utils.cellular.performance import cellular_performance_test_utils as cputils
from acts_contrib.test_utils.cellular.performance.CellularThroughputBaseTest import CellularThroughputBaseTest
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils
from acts_contrib.test_utils.wifi.wifi_performance_test_utils.bokeh_figure import BokehFigure
from functools import partial


class CellularLteSensitivityTest(CellularThroughputBaseTest):
    """Class to test single cell LTE sensitivity"""

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        self.testclass_params = self.user_params['lte_sensitivity_test_params']
        self.tests = self.generate_test_cases(dl_mcs_list=list(
            numpy.arange(27, -1, -1)),
                                              lte_dl_mcs_table='QAM256',
                                              lte_ul_mcs_table='QAM256',
                                              lte_ul_mcs=4,
                                              transform_precoding=0)

    def process_testclass_results(self):
        # Plot individual test id results raw data and compile metrics
        plots = collections.OrderedDict()
        compiled_data = collections.OrderedDict()
        for testcase_name, testcase_data in self.testclass_results.items():
            cell_config = testcase_data['testcase_params'][
                'endc_combo_config']['cell_list'][0]
            test_id = tuple(('band', cell_config['band']))
            if test_id not in plots:
                # Initialize test id data when not present
                compiled_data[test_id] = {
                    'mcs': [],
                    'average_throughput': [],
                    'theoretical_throughput': [],
                    'cell_power': [],
                }
                plots[test_id] = BokehFigure(
                    title='Band {} ({}) - BLER Curves'.format(
                        cell_config['band'],
                        testcase_data['testcase_params']['lte_dl_mcs_table']),
                    x_label='Cell Power (dBm)',
                    primary_y_label='BLER (Mbps)')
                test_id_rvr = test_id + tuple('RvR')
                plots[test_id_rvr] = BokehFigure(
                    title='Band {} ({}) - RvR'.format(
                        cell_config['band'],
                        testcase_data['testcase_params']['lte_dl_mcs_table']),
                    x_label='Cell Power (dBm)',
                    primary_y_label='PHY Rate (Mbps)')
            # Compile test id data and metrics
            compiled_data[test_id]['average_throughput'].append(
                testcase_data['average_throughput_list'])
            compiled_data[test_id]['cell_power'].append(
                testcase_data['cell_power_list'])
            compiled_data[test_id]['mcs'].append(
                testcase_data['testcase_params']['lte_dl_mcs'])
            # Add test id to plots
            plots[test_id].add_line(
                testcase_data['cell_power_list'],
                testcase_data['bler_list'],
                'MCS {}'.format(
                    testcase_data['testcase_params']['lte_dl_mcs']),
                width=1)
            plots[test_id_rvr].add_line(
                testcase_data['cell_power_list'],
                testcase_data['average_throughput_list'],
                'MCS {}'.format(
                    testcase_data['testcase_params']['lte_dl_mcs']),
                width=1,
                style='dashed')

        for test_id, test_data in compiled_data.items():
            test_id_rvr = test_id + tuple('RvR')
            cell_power_interp = sorted(set(sum(test_data['cell_power'], [])))
            average_throughput_interp = []
            for mcs, cell_power, throughput in zip(
                    test_data['mcs'], test_data['cell_power'],
                    test_data['average_throughput']):
                throughput_interp = numpy.interp(cell_power_interp,
                                                 cell_power[::-1],
                                                 throughput[::-1])
                average_throughput_interp.append(throughput_interp)
            rvr = numpy.max(average_throughput_interp, 0)
            plots[test_id_rvr].add_line(cell_power_interp, rvr,
                                        'Rate vs. Range')

        figure_list = []
        for plot_id, plot in plots.items():
            plot.generate_figure()
            figure_list.append(plot)
        output_file_path = os.path.join(self.log_path, 'results.html')
        BokehFigure.save_figures(figure_list, output_file_path)

        """Saves CSV with all test results to enable comparison."""
        results_file_path = os.path.join(
            context.get_current_context().get_full_output_path(),
            'results.csv')
        with open(results_file_path, 'w', newline='') as csvfile:
            field_names = [
                'Test Name', 'Sensitivity'
            ]
            writer = csv.DictWriter(csvfile, fieldnames=field_names)
            writer.writeheader()

            for testcase_name, testcase_results in self.testclass_results.items(
            ):
                row_dict = {
                    'Test Name': testcase_name,
                    'Sensitivity': testcase_results['sensitivity']
                }
                writer.writerow(row_dict)

    def process_testcase_results(self):
        if self.current_test_name not in self.testclass_results:
            return
        testcase_data = self.testclass_results[self.current_test_name]

        bler_list = []
        average_throughput_list = []
        theoretical_throughput_list = []
        cell_power_list = testcase_data['testcase_params']['cell_power_sweep'][
            0]
        for result in testcase_data['results']:
            bler_list.append(result['throughput_measurements']
                             ['lte_bler_result']['total']['DL']['nack_ratio'])
            average_throughput_list.append(
                result['throughput_measurements']['lte_tput_result']['total']
                ['DL']['average_tput'])
            theoretical_throughput_list.append(
                result['throughput_measurements']['lte_tput_result']['total']
                ['DL']['theoretical_tput'])
        padding_len = len(cell_power_list) - len(average_throughput_list)
        average_throughput_list.extend([0] * padding_len)
        theoretical_throughput_list.extend([0] * padding_len)

        bler_above_threshold = [
            bler > self.testclass_params['bler_threshold']
            for bler in bler_list
        ]
        for idx in range(len(bler_above_threshold)):
            if all(bler_above_threshold[idx:]):
                sensitivity_idx = max(idx, 1) - 1
                break
        else:
            sensitivity_idx = -1
        sensitivity = cell_power_list[sensitivity_idx]
        self.log.info('LTE Band {} Table {} MCS {} Sensitivity = {}dBm'.format(
            testcase_data['testcase_params']['endc_combo_config']['cell_list']
            [0]['band'], testcase_data['testcase_params']['lte_dl_mcs_table'],
            testcase_data['testcase_params']['lte_dl_mcs'], sensitivity))

        testcase_data['bler_list'] = bler_list
        testcase_data['average_throughput_list'] = average_throughput_list
        testcase_data[
            'theoretical_throughput_list'] = theoretical_throughput_list
        testcase_data['cell_power_list'] = cell_power_list
        testcase_data['sensitivity'] = sensitivity

        results_file_path = os.path.join(
            context.get_current_context().get_full_output_path(),
            '{}.json'.format(self.current_test_name))
        with open(results_file_path, 'w') as results_file:
            json.dump(wputils.serialize_dict(testcase_data),
                      results_file,
                      indent=4)

    def get_per_cell_power_sweeps(self, testcase_params):
        # get reference test
        current_band = testcase_params['endc_combo_config']['cell_list'][0][
            'band']
        reference_test = None
        reference_sensitivity = None
        for testcase_name, testcase_data in self.testclass_results.items():
            if testcase_data['testcase_params']['endc_combo_config'][
                    'cell_list'][0]['band'] == current_band:
                reference_test = testcase_name
                reference_sensitivity = testcase_data['sensitivity']
        if reference_test and reference_sensitivity and not self.retry_flag:
            start_atten = reference_sensitivity + self.testclass_params[
                'adjacent_mcs_gap']
            self.log.info(
                "Reference test {} found. Sensitivity {} dBm. Starting at {} dBm"
                .format(reference_test, reference_sensitivity, start_atten))
        else:
            start_atten = self.testclass_params['lte_cell_power_start']
            self.log.info(
                "Reference test not found. Starting at {} dBm".format(
                    start_atten))
        # get current cell power start
        cell_power_sweeps = [
            list(
                numpy.arange(start_atten,
                             self.testclass_params['lte_cell_power_stop'],
                             self.testclass_params['lte_cell_power_step']))
        ]
        return cell_power_sweeps

    def generate_test_cases(self, dl_mcs_list, lte_dl_mcs_table,
                            lte_ul_mcs_table, lte_ul_mcs, **kwargs):
        test_cases = []
        with open(self.testclass_params['lte_single_cell_configs'],
                  'r') as csvfile:
            test_configs = csv.DictReader(csvfile)
            for test_config, lte_dl_mcs in itertools.product(
                    test_configs, dl_mcs_list):
                if int(test_config['skip_test']):
                    continue
                endc_combo_config = cputils.generate_endc_combo_config_from_csv_row(
                    test_config)
                test_name = 'test_lte_B{}_dl_{}_mcs{}'.format(
                    test_config['lte_band'], lte_dl_mcs_table, lte_dl_mcs)
                test_params = collections.OrderedDict(
                    endc_combo_config=endc_combo_config,
                    lte_dl_mcs_table=lte_dl_mcs_table,
                    lte_dl_mcs=lte_dl_mcs,
                    lte_ul_mcs_table=lte_ul_mcs_table,
                    lte_ul_mcs=lte_ul_mcs,
                    **kwargs)
                setattr(self, test_name,
                        partial(self._test_throughput_bler, test_params))
                test_cases.append(test_name)
        return test_cases


class CellularLteSensitivity_SampleMCS_Test(CellularLteSensitivityTest):
    """Class to test single cell LTE sensitivity"""

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        self.testclass_params = self.user_params['lte_sensitivity_test_params']
        self.tests = self.generate_test_cases(dl_mcs_list=[27,25,16,9],
                                              lte_dl_mcs_table='QAM256',
                                              lte_ul_mcs_table='QAM256',
                                              lte_ul_mcs=4,
                                              transform_precoding=0)