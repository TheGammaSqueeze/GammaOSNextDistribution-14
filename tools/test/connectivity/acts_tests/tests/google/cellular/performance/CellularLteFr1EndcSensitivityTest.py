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


class CellularLteFr1EndcSensitivityTest(CellularThroughputBaseTest):
    """Class to test ENDC sensitivity"""

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        self.testclass_params = self.user_params['endc_sensitivity_test_params']
        self.tests = self.generate_test_cases(lte_dl_mcs_list=list(numpy.arange(27,0,-1)),
                                              lte_dl_mcs_table='QAM256',
                                              lte_ul_mcs_table='QAM256',
                                              lte_ul_mcs=4,
                                              nr_dl_mcs_list=list(numpy.arange(27,0,-1)),
                                              nr_ul_mcs=4,
                                              transform_precoding=0,
                                              schedule_scenario='FULL_TPUT',
                                              schedule_slot_ratio=80)

    def process_testclass_results(self):
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
        test_cell_idx = testcase_data['testcase_params']['test_cell_idx']
        test_cell_config = testcase_data['testcase_params']['endc_combo_config']['cell_list'][test_cell_idx]
        cell_power_list = testcase_data['testcase_params']['cell_power_sweep'][
            test_cell_idx]

        for result in testcase_data['results']:
            if test_cell_config['cell_type'] == 'LTE':
                bler_list.append(1-result['throughput_measurements']
                                 ['lte_bler_result'][test_cell_config['cell_number']]['DL']['ack_ratio'])
                average_throughput_list.append(
                    result['throughput_measurements']['lte_tput_result'][test_cell_config['cell_number']]
                    ['DL']['average_tput'])
                theoretical_throughput_list.append(
                    result['throughput_measurements']['lte_tput_result'][test_cell_config['cell_number']]
                    ['DL']['theoretical_tput'])
            else:
                bler_list.append(1-result['throughput_measurements']
                                 ['nr_bler_result'][test_cell_config['cell_number']]['DL']['ack_ratio'])
                average_throughput_list.append(
                    result['throughput_measurements']['nr_tput_result'][test_cell_config['cell_number']]
                    ['DL']['average_tput'])
                theoretical_throughput_list.append(
                    result['throughput_measurements']['nr_tput_result'][test_cell_config['cell_number']]
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
                sensitivity = cell_power_list[sensitivity_idx]
                break
        else:
            sensitivity = float('nan')


        if test_cell_config['cell_type'] == 'LTE':
            test_mcs = testcase_data['testcase_params']['lte_dl_mcs']
        else:
            test_mcs = testcase_data['testcase_params']['nr_dl_mcs']
        self.log.info('{} Band {} MCS {} Sensitivity = {}dBm'.format(
            test_cell_config['cell_type'],
            test_cell_config['band'],
            test_mcs,
            sensitivity))

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
        cell_power_sweeps = []
        # Construct test cell sweep
        test_cell = testcase_params['endc_combo_config']['cell_list'][testcase_params['test_cell_idx']]
        if test_cell['cell_type'] == 'LTE':
            test_cell_sweep = list(
                numpy.arange(self.testclass_params['lte_cell_power_start'],
                             self.testclass_params['lte_cell_power_stop'],
                             self.testclass_params['lte_cell_power_step']))
        else:
            test_cell_sweep = list(
                numpy.arange(self.testclass_params['nr_cell_power_start'],
                             self.testclass_params['nr_cell_power_stop'],
                             self.testclass_params['nr_cell_power_step']))

        for cell_idx, cell_config in enumerate(testcase_params['endc_combo_config']['cell_list']):
            if cell_idx == testcase_params['test_cell_idx']:
                cell_power_sweeps.append(test_cell_sweep)
            elif cell_config['cell_type'] == 'LTE':
                lte_sweep = [self.testclass_params['lte_cell_power_start']
                             ] * len(test_cell_sweep)
                cell_power_sweeps.append(lte_sweep)
            elif cell_config['cell_type'] == 'NR5G':
                nr_sweep = [self.testclass_params['nr_cell_power_start']
                             ] * len(test_cell_sweep)
                cell_power_sweeps.append(nr_sweep)
        return cell_power_sweeps

    def generate_test_cases(self, lte_dl_mcs_list, lte_dl_mcs_table,
                            lte_ul_mcs_table, lte_ul_mcs, nr_dl_mcs_list,
                            nr_ul_mcs, **kwargs):
        test_cases = []
        with open(self.testclass_params['endc_combo_file'],
                  'r') as endc_combos:
            for endc_combo_str in endc_combos:
                if endc_combo_str[0] == '#':
                    continue
                endc_combo_config = cputils.generate_endc_combo_config_from_string(
                    endc_combo_str)
                special_chars = '+[]=;,\n'
                for char in special_chars:
                    endc_combo_str = endc_combo_str.replace(char, '_')
                endc_combo_str = endc_combo_str.replace('__', '_')
                endc_combo_str = endc_combo_str.strip('_')
                for cell_idx, cell_config in enumerate(endc_combo_config['cell_list']):
                    if cell_config['cell_type'] == 'LTE':
                        dl_mcs_list = lte_dl_mcs_list
                    else:
                        dl_mcs_list = nr_dl_mcs_list
                    for dl_mcs in dl_mcs_list:
                        test_name = 'test_sensitivity_{}_cell_{}_mcs{}'.format(
                            endc_combo_str, cell_idx, dl_mcs)
                        if cell_config['cell_type'] == 'LTE':
                            test_params = collections.OrderedDict(
                                endc_combo_config=endc_combo_config,
                                test_cell_idx=cell_idx,
                                lte_dl_mcs_table=lte_dl_mcs_table,
                                lte_dl_mcs=dl_mcs,
                                lte_ul_mcs_table=lte_ul_mcs_table,
                                lte_ul_mcs=lte_ul_mcs,
                                nr_dl_mcs=4,
                                nr_ul_mcs=nr_ul_mcs,
                                **kwargs)
                        else:
                            test_params = collections.OrderedDict(
                                endc_combo_config=endc_combo_config,
                                test_cell_idx=cell_idx,
                                lte_dl_mcs_table=lte_dl_mcs_table,
                                lte_dl_mcs=4,
                                lte_ul_mcs_table=lte_ul_mcs_table,
                                lte_ul_mcs=lte_ul_mcs,
                                nr_dl_mcs=dl_mcs,
                                nr_ul_mcs=nr_ul_mcs,
                                **kwargs)
                        setattr(self, test_name,
                                partial(self._test_throughput_bler, test_params))
                        test_cases.append(test_name)
        return test_cases


class CellularLteFr1EndcSensitivity_SampleMCS_Test(CellularLteFr1EndcSensitivityTest):
    """Class to test single cell LTE sensitivity"""

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        self.testclass_params = self.user_params['endc_sensitivity_test_params']
        self.tests = self.generate_test_cases(lte_dl_mcs_list=[27,25,16,9],
                                              lte_dl_mcs_table='QAM256',
                                              lte_ul_mcs_table='QAM256',
                                              lte_ul_mcs=4,
                                              nr_dl_mcs_list=[27,25,16,9],
                                              nr_ul_mcs=4,
                                              transform_precoding=0,
                                              schedule_scenario='FULL_TPUT',
                                              schedule_slot_ratio=80)