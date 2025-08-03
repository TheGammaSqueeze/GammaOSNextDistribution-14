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
import os
from acts import context
from acts import base_test
from acts.metrics.loggers.blackbox import BlackboxMappedMetricLogger
from acts_contrib.test_utils.cellular.performance import cellular_performance_test_utils as cputils
from acts_contrib.test_utils.cellular.performance.CellularThroughputBaseTest import CellularThroughputBaseTest
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils
from acts_contrib.test_utils.wifi.wifi_performance_test_utils.bokeh_figure import BokehFigure

from functools import partial


class CellularFr1RvrTest(CellularThroughputBaseTest):
    """Class to test single cell FR1 NSA sensitivity"""

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        self.testclass_params = self.user_params['nr_rvr_test_params']
        self.tests = self.generate_test_cases(
            channel_list=['LOW', 'MID', 'HIGH'],
            nr_ul_mcs=4,
            lte_dl_mcs_table='QAM256',
            lte_dl_mcs=4,
            lte_ul_mcs_table='QAM256',
            lte_ul_mcs=4,
            transform_precoding=0)

    def process_testclass_results(self):
        pass

    def process_testcase_results(self):
        if self.current_test_name not in self.testclass_results:
            return
        testcase_data = self.testclass_results[self.current_test_name]
        results_file_path = os.path.join(
            context.get_current_context().get_full_output_path(),
            '{}.json'.format(self.current_test_name))
        with open(results_file_path, 'w') as results_file:
            json.dump(wputils.serialize_dict(testcase_data),
                      results_file,
                      indent=4)

        average_throughput_list = []
        theoretical_throughput_list = []
        nr_cell_index = testcase_data['testcase_params']['endc_combo_config']['lte_cell_count']
        cell_power_list = testcase_data['testcase_params']['cell_power_sweep'][nr_cell_index]
        for result in testcase_data['results']:
            average_throughput_list.append(
                result['throughput_measurements']['nr_tput_result']['total']['DL']['average_tput'])
            theoretical_throughput_list.append(
                result['throughput_measurements']['nr_tput_result']['total']['DL']['theoretical_tput'])
        padding_len = len(cell_power_list) - len(average_throughput_list)
        average_throughput_list.extend([0] * padding_len)
        theoretical_throughput_list.extend([0] * padding_len)

        testcase_data['average_throughput_list'] = average_throughput_list
        testcase_data[
            'theoretical_throughput_list'] = theoretical_throughput_list
        testcase_data['cell_power_list'] = cell_power_list

        plot = BokehFigure(
            title='Band {} - RvR'.format(testcase_data['testcase_params']['endc_combo_config']['cell_list'][nr_cell_index]['band']),
            x_label='Cell Power (dBm)',
            primary_y_label='PHY Rate (Mbps)')

        plot.add_line(
            testcase_data['cell_power_list'],
            testcase_data['average_throughput_list'],
            'Average Throughput',
            width=1)
        plot.add_line(
            testcase_data['cell_power_list'],
            testcase_data['theoretical_throughput_list'],
            'Average Throughput',
            width=1,
            style='dashed')
        plot.generate_figure()
        output_file_path = os.path.join(self.log_path, '{}.html'.format(self.current_test_name))
        BokehFigure.save_figure(plot, output_file_path)


    def get_per_cell_power_sweeps(self, testcase_params):
        nr_cell_index = testcase_params['endc_combo_config']['lte_cell_count']
        start_atten = self.testclass_params['nr_cell_power_start']
        # get current cell power start
        nr_cell_sweep = list(
            numpy.arange(start_atten,
                         self.testclass_params['nr_cell_power_stop'],
                         self.testclass_params['nr_cell_power_step']))
        lte_sweep = [self.testclass_params['lte_cell_power']
                     ] * len(nr_cell_sweep)
        if nr_cell_index == 0:
            cell_power_sweeps = [nr_cell_sweep]
        else:
            cell_power_sweeps = [lte_sweep, nr_cell_sweep]
        return cell_power_sweeps

    def generate_test_cases(self, channel_list, **kwargs):
        test_cases = []
        with open(self.testclass_params['nr_single_cell_configs'],
                  'r') as csvfile:
            test_configs = csv.DictReader(csvfile)
            for test_config, channel in itertools.product(
                    test_configs, channel_list):
                if int(test_config['skip_test']):
                    continue
                endc_combo_config = cputils.generate_endc_combo_config_from_csv_row(
                    test_config)
                test_name = 'test_fr1_{}_{}'.format(
                    test_config['nr_band'], channel.lower())
                test_params = collections.OrderedDict(
                    endc_combo_config=endc_combo_config,
                    nr_dl_mcs=self.testclass_params['link_adaptation_config'],
                    **kwargs)
                setattr(self, test_name,
                        partial(self._test_throughput_bler, test_params))
                test_cases.append(test_name)
        return test_cases
