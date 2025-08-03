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
import json
import re
import os
from acts import context
from acts import base_test
from acts.metrics.loggers.blackbox import BlackboxMappedMetricLogger
from acts_contrib.test_utils.cellular.performance import cellular_performance_test_utils as cputils
from acts_contrib.test_utils.cellular.performance.CellularThroughputBaseTest import CellularThroughputBaseTest
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils

from functools import partial

LONG_SLEEP = 10
MEDIUM_SLEEP = 2
IPERF_TIMEOUT = 10
SHORT_SLEEP = 1
SUBFRAME_LENGTH = 0.001
STOP_COUNTER_LIMIT = 3


class CellularLtePlusFr1PeakThroughputTest(CellularThroughputBaseTest):
    """Base class to test cellular LTE and FR1 throughput

    This class implements cellular LTE & FR1 throughput tests on a callbox setup.
    The class setups up the callbox in the desired configurations, configures
    and connects the phone, and runs traffic/iperf throughput.
    """

    def process_testcase_results(self):
        """Publish test case metrics and save results"""
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
        testcase_result = testcase_data['results'][0]
        metric_map = {
            'tcp_udp_tput': testcase_result.get('iperf_throughput',
                                                float('nan'))
        }
        if testcase_data['testcase_params']['endc_combo_config'][
                'nr_cell_count']:
            metric_map.update({
                'nr_min_dl_tput':
                testcase_result['throughput_measurements']['nr_tput_result']['total']['DL']['min_tput'],
                'nr_max_dl_tput':
                testcase_result['throughput_measurements']['nr_tput_result']['total']['DL']['max_tput'],
                'nr_avg_dl_tput':
                testcase_result['throughput_measurements']['nr_tput_result']['total']['DL']
                ['average_tput'],
                'nr_theoretical_dl_tput':
                testcase_result['throughput_measurements']['nr_tput_result']['total']['DL']
                ['theoretical_tput'],
                'nr_dl_bler':
                testcase_result['throughput_measurements']['nr_bler_result']['total']['DL']['nack_ratio']
                * 100,
                'nr_min_dl_tput':
                testcase_result['throughput_measurements']['nr_tput_result']['total']['UL']['min_tput'],
                'nr_max_dl_tput':
                testcase_result['throughput_measurements']['nr_tput_result']['total']['UL']['max_tput'],
                'nr_avg_dl_tput':
                testcase_result['throughput_measurements']['nr_tput_result']['total']['UL']
                ['average_tput'],
                'nr_theoretical_dl_tput':
                testcase_result['throughput_measurements']['nr_tput_result']['total']['UL']
                ['theoretical_tput'],
                'nr_ul_bler':
                testcase_result['throughput_measurements']['nr_bler_result']['total']['UL']['nack_ratio']
                * 100
            })
        if testcase_data['testcase_params']['endc_combo_config'][
                'lte_cell_count']:
            metric_map.update({
                'lte_min_dl_tput':
                testcase_result['throughput_measurements']['lte_tput_result']['total']['DL']['min_tput'],
                'lte_max_dl_tput':
                testcase_result['throughput_measurements']['lte_tput_result']['total']['DL']['max_tput'],
                'lte_avg_dl_tput':
                testcase_result['throughput_measurements']['lte_tput_result']['total']['DL']
                ['average_tput'],
                'lte_theoretical_dl_tput':
                testcase_result['throughput_measurements']['lte_tput_result']['total']['DL']
                ['theoretical_tput'],
                'lte_dl_bler':
                testcase_result['throughput_measurements']['lte_bler_result']['total']['DL']['nack_ratio']
                * 100,
                'lte_min_dl_tput':
                testcase_result['throughput_measurements']['lte_tput_result']['total']['UL']['min_tput'],
                'lte_max_dl_tput':
                testcase_result['throughput_measurements']['lte_tput_result']['total']['UL']['max_tput'],
                'lte_avg_dl_tput':
                testcase_result['throughput_measurements']['lte_tput_result']['total']['UL']
                ['average_tput'],
                'lte_theoretical_dl_tput':
                testcase_result['throughput_measurements']['lte_tput_result']['total']['UL']
                ['theoretical_tput'],
                'lte_ul_bler':
                testcase_result['throughput_measurements']['lte_bler_result']['total']['UL']['nack_ratio']
                * 100
            })
        if self.publish_testcase_metrics:
            for metric_name, metric_value in metric_map.items():
                self.testcase_metric_logger.add_metric(metric_name,
                                                       metric_value)

    def process_testclass_results(self):
        """Saves CSV with all test results to enable comparison."""
        results_file_path = os.path.join(
            context.get_current_context().get_full_output_path(),
            'results.csv')
        with open(results_file_path, 'w', newline='') as csvfile:
            field_names = [
                'Test Name', 'NR DL Min. Throughput', 'NR DL Max. Throughput',
                'NR DL Avg. Throughput', 'NR DL Theoretical Throughput',
                'NR UL Min. Throughput', 'NR UL Max. Throughput',
                'NR UL Avg. Throughput', 'NR UL Theoretical Throughput',
                'NR DL BLER (%)', 'NR UL BLER (%)', 'LTE DL Min. Throughput',
                'LTE DL Max. Throughput', 'LTE DL Avg. Throughput',
                'LTE DL Theoretical Throughput', 'LTE UL Min. Throughput',
                'LTE UL Max. Throughput', 'LTE UL Avg. Throughput',
                'LTE UL Theoretical Throughput', 'LTE DL BLER (%)',
                'LTE UL BLER (%)', 'TCP/UDP Throughput'
            ]
            writer = csv.DictWriter(csvfile, fieldnames=field_names)
            writer.writeheader()

            for testcase_name, testcase_results in self.testclass_results.items(
            ):
                for result in testcase_results['results']:
                    row_dict = {
                        'Test Name': testcase_name,
                        'TCP/UDP Throughput':
                        result.get('iperf_throughput', 0)
                    }
                    if testcase_results['testcase_params'][
                            'endc_combo_config']['nr_cell_count']:
                        row_dict.update({
                            'NR DL Min. Throughput':
                            result['throughput_measurements']['nr_tput_result']['total']['DL']
                            ['min_tput'],
                            'NR DL Max. Throughput':
                            result['throughput_measurements']['nr_tput_result']['total']['DL']
                            ['max_tput'],
                            'NR DL Avg. Throughput':
                            result['throughput_measurements']['nr_tput_result']['total']['DL']
                            ['average_tput'],
                            'NR DL Theoretical Throughput':
                            result['throughput_measurements']['nr_tput_result']['total']['DL']
                            ['theoretical_tput'],
                            'NR UL Min. Throughput':
                            result['throughput_measurements']['nr_tput_result']['total']['UL']
                            ['min_tput'],
                            'NR UL Max. Throughput':
                            result['throughput_measurements']['nr_tput_result']['total']['UL']
                            ['max_tput'],
                            'NR UL Avg. Throughput':
                            result['throughput_measurements']['nr_tput_result']['total']['UL']
                            ['average_tput'],
                            'NR UL Theoretical Throughput':
                            result['throughput_measurements']['nr_tput_result']['total']['UL']
                            ['theoretical_tput'],
                            'NR DL BLER (%)':
                            result['throughput_measurements']['nr_bler_result']['total']['DL']
                            ['nack_ratio'] * 100,
                            'NR UL BLER (%)':
                            result['throughput_measurements']['nr_bler_result']['total']['UL']
                            ['nack_ratio'] * 100
                        })
                    if testcase_results['testcase_params'][
                            'endc_combo_config']['lte_cell_count']:
                        row_dict.update({
                            'LTE DL Min. Throughput':
                            result['throughput_measurements']['lte_tput_result']['total']['DL']
                            ['min_tput'],
                            'LTE DL Max. Throughput':
                            result['throughput_measurements']['lte_tput_result']['total']['DL']
                            ['max_tput'],
                            'LTE DL Avg. Throughput':
                            result['throughput_measurements']['lte_tput_result']['total']['DL']
                            ['average_tput'],
                            'LTE DL Theoretical Throughput':
                            result['throughput_measurements']['lte_tput_result']['total']['DL']
                            ['theoretical_tput'],
                            'LTE UL Min. Throughput':
                            result['throughput_measurements']['lte_tput_result']['total']['UL']
                            ['min_tput'],
                            'LTE UL Max. Throughput':
                            result['throughput_measurements']['lte_tput_result']['total']['UL']
                            ['max_tput'],
                            'LTE UL Avg. Throughput':
                            result['throughput_measurements']['lte_tput_result']['total']['UL']
                            ['average_tput'],
                            'LTE UL Theoretical Throughput':
                            result['throughput_measurements']['lte_tput_result']['total']['UL']
                            ['theoretical_tput'],
                            'LTE DL BLER (%)':
                            result['throughput_measurements']['lte_bler_result']['total']['DL']
                            ['nack_ratio'] * 100,
                            'LTE UL BLER (%)':
                            result['throughput_measurements']['lte_bler_result']['total']['UL']
                            ['nack_ratio'] * 100
                        })
                    writer.writerow(row_dict)

    def get_per_cell_power_sweeps(self, testcase_params):
        """Function to get per cell power sweep lists

        Args:
            testcase_params: dict containing all test case params
        Returns:
            cell_power_sweeps: list of cell power sweeps for each cell under test
        """
        cell_power_sweeps = []
        for cell in testcase_params['endc_combo_config']['cell_list']:
            if cell['cell_type'] == 'LTE':
                sweep = [self.testclass_params['lte_cell_power']]
            else:
                sweep = [self.testclass_params['nr_cell_power']]
            cell_power_sweeps.append(sweep)
        return cell_power_sweeps


class CellularLteFr1EndcPeakThroughputTest(CellularLtePlusFr1PeakThroughputTest
                                           ):
    """Class to test cellular LTE/FR1 ENDC combo list"""

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        self.testclass_params = self.user_params['throughput_test_params']
        self.tests = self.generate_test_cases([(27, 4), (4, 27)],
                                              lte_dl_mcs_table='QAM256',
                                              lte_ul_mcs_table='QAM256',
                                              transform_precoding=0,
                                              schedule_scenario='FULL_TPUT',
                                              schedule_slot_ratio=80)

    def generate_test_cases(self, mcs_pair_list, **kwargs):
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
                for mcs_pair in mcs_pair_list:
                    test_name = 'test_lte_fr1_endc_{}_dl_mcs{}_ul_mcs{}'.format(
                        endc_combo_str, mcs_pair[0], mcs_pair[1])
                    test_params = collections.OrderedDict(
                        endc_combo_config=endc_combo_config,
                        nr_dl_mcs=mcs_pair[0],
                        nr_ul_mcs=mcs_pair[1],
                        lte_dl_mcs=mcs_pair[0],
                        lte_ul_mcs=mcs_pair[1],
                        **kwargs)
                    setattr(self, test_name,
                            partial(self._test_throughput_bler, test_params))
                    test_cases.append(test_name)
        return test_cases


class CellularFr1SingleCellPeakThroughputTest(CellularLtePlusFr1PeakThroughputTest
                                              ):
    """Class to test single cell FR1 NSA mode"""

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        self.testclass_params = self.user_params['throughput_test_params']
        self.tests = self.generate_test_cases(
            nr_mcs_pair_list=[(27, 4), (4, 27)],
            nr_channel_list=['LOW', 'MID', 'HIGH'],
            schedule_scenario='FULL_TPUT',
            schedule_slot_ratio=80,
            transform_precoding=0,
            lte_dl_mcs=4,
            lte_dl_mcs_table='QAM256',
            lte_ul_mcs=4,
            lte_ul_mcs_table='QAM64')

    def generate_test_cases(self, nr_mcs_pair_list, nr_channel_list, **kwargs):

        test_cases = []
        with open(self.testclass_params['nr_single_cell_configs'],
                  'r') as csvfile:
            test_configs = csv.DictReader(csvfile)
            for test_config, nr_channel, nr_mcs_pair in itertools.product(
                    test_configs, nr_channel_list, nr_mcs_pair_list):
                if int(test_config['skip_test']):
                    continue
                endc_combo_config = cputils.generate_endc_combo_config_from_csv_row(
                    test_config)
                endc_combo_config['cell_list'][endc_combo_config['lte_cell_count']]['channel'] = nr_channel
                test_name = 'test_fr1_{}_{}_dl_mcs{}_ul_mcs{}'.format(
                    test_config['nr_band'], nr_channel.lower(), nr_mcs_pair[0],
                    nr_mcs_pair[1])
                test_params = collections.OrderedDict(
                    endc_combo_config=endc_combo_config,
                    nr_dl_mcs=nr_mcs_pair[0],
                    nr_ul_mcs=nr_mcs_pair[1],
                    **kwargs)
                setattr(self, test_name,
                        partial(self._test_throughput_bler, test_params))
                test_cases.append(test_name)
        return test_cases


class CellularLteSingleCellPeakThroughputTest(CellularLtePlusFr1PeakThroughputTest
                                              ):
    """Class to test single cell LTE"""

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        self.testclass_params = self.user_params['throughput_test_params']
        self.tests = self.generate_test_cases(lte_mcs_pair_list=[
            (('QAM256', 27), ('QAM256', 4)), (('QAM256', 4), ('QAM256', 27))
        ],
                                              transform_precoding=0)

    def generate_test_cases(self, lte_mcs_pair_list, **kwargs):
        test_cases = []
        with open(self.testclass_params['lte_single_cell_configs'],
                  'r') as csvfile:
            test_configs = csv.DictReader(csvfile)
            for test_config, lte_mcs_pair in itertools.product(
                    test_configs, lte_mcs_pair_list):
                if int(test_config['skip_test']):
                    continue
                endc_combo_config = cputils.generate_endc_combo_config_from_csv_row(
                    test_config)
                test_name = 'test_lte_B{}_dl_{}_mcs{}_ul_{}_mcs{}'.format(
                    test_config['lte_band'], lte_mcs_pair[0][0],
                    lte_mcs_pair[0][1], lte_mcs_pair[1][0], lte_mcs_pair[1][1])
                test_params = collections.OrderedDict(
                    endc_combo_config=endc_combo_config,
                    lte_dl_mcs_table=lte_mcs_pair[0][0],
                    lte_dl_mcs=lte_mcs_pair[0][1],
                    lte_ul_mcs_table=lte_mcs_pair[1][0],
                    lte_ul_mcs=lte_mcs_pair[1][1],
                    **kwargs)
                setattr(self, test_name,
                        partial(self._test_throughput_bler, test_params))
                test_cases.append(test_name)
        return test_cases
