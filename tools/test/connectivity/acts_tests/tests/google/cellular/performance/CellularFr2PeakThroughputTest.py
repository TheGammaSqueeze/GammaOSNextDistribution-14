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
import numpy
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


class CellularFr2PeakThroughputTest(CellularThroughputBaseTest):
    """Base class to test cellular FR2 throughput

    This class implements cellular FR2 throughput tests on a callbox setup.
    The class setups up the callbox in the desired configurations, configures
    and connects the phone, and runs traffic/iperf throughput.
    """

    def __init__(self, controllers):
        super().__init__(controllers)
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True

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

    def generate_endc_combo_config(self, test_config):
        """Function to generate ENDC combo config from CSV test config

        Args:
            test_config: dict containing ENDC combo config from CSV
        Returns:
            endc_combo_config: dictionary with all ENDC combo settings
        """
        endc_combo_config = collections.OrderedDict()
        cell_config_list = []

        lte_cell_count = 1
        lte_carriers = [1]
        lte_scc_list = []
        endc_combo_config['lte_pcc'] = 1
        lte_cell = {
            'cell_type':
            'LTE',
            'cell_number':
            1,
            'pcc':
            1,
            'band':
            test_config['lte_band'],
            'dl_bandwidth':
            test_config['lte_bandwidth'],
            'ul_enabled':
            1,
            'duplex_mode':
            test_config['lte_duplex_mode'],
            'dl_mimo_config':
            'D{nss}U{nss}'.format(nss=test_config['lte_dl_mimo_config']),
            'ul_mimo_config':
            'D{nss}U{nss}'.format(nss=test_config['lte_ul_mimo_config']),
            'transmission_mode':
            'TM1'
        }
        cell_config_list.append(lte_cell)

        nr_cell_count = 0
        nr_dl_carriers = []
        nr_ul_carriers = []
        for nr_cell_idx in range(1, test_config['num_dl_cells'] + 1):
            nr_cell = {
                'cell_type':
                'NR5G',
                'cell_number':
                nr_cell_idx,
                'nr_cell_type': 'NSA',
                'band':
                test_config['nr_band'],
                'duplex_mode':
                test_config['nr_duplex_mode'],
                'channel':
                test_config['nr_channel'],
                'dl_mimo_config':
                'N{nss}X{nss}'.format(nss=test_config['nr_dl_mimo_config']),
                'dl_bandwidth_class':
                'A',
                'dl_bandwidth':
                test_config['nr_bandwidth'],
                'ul_enabled':
                1 if nr_cell_idx <= test_config['num_ul_cells'] else 0,
                'ul_bandwidth_class':
                'A',
                'ul_mimo_config':
                'N{nss}X{nss}'.format(nss=test_config['nr_ul_mimo_config']),
                'subcarrier_spacing':
                'MU3'
            }
            cell_config_list.append(nr_cell)
            nr_cell_count = nr_cell_count + 1
            nr_dl_carriers.append(nr_cell_idx)
            if nr_cell_idx <= test_config['num_ul_cells']:
                nr_ul_carriers.append(nr_cell_idx)

        endc_combo_config['lte_cell_count'] = lte_cell_count
        endc_combo_config['nr_cell_count'] = nr_cell_count
        endc_combo_config['nr_dl_carriers'] = nr_dl_carriers
        endc_combo_config['nr_ul_carriers'] = nr_ul_carriers
        endc_combo_config['cell_list'] = cell_config_list
        endc_combo_config['lte_scc_list'] = lte_scc_list
        endc_combo_config['lte_dl_carriers'] = lte_carriers
        endc_combo_config['lte_ul_carriers'] = lte_carriers
        return endc_combo_config

    def generate_test_cases(self, bands, channels, nr_mcs_pair_list,
                            num_dl_cells_list, num_ul_cells_list, orientation_list,
                            dl_mimo_config, ul_mimo_config, **kwargs):
        """Function that auto-generates test cases for a test class."""
        test_cases = []
        for orientation, band, channel, num_ul_cells, num_dl_cells, nr_mcs_pair in itertools.product(
                orientation_list, bands, channels, num_ul_cells_list, num_dl_cells_list,
                nr_mcs_pair_list):
            if num_ul_cells > num_dl_cells:
                continue
            if channel not in cputils.PCC_PRESET_MAPPING[band]:
                continue
            test_config = {
                'lte_band': 2,
                'lte_bandwidth': 'BW20',
                'lte_duplex_mode': 'FDD',
                'lte_dl_mimo_config': 1,
                'lte_ul_mimo_config': 1,
                'nr_band': band,
                'nr_bandwidth': 'BW100',
                'nr_duplex_mode': 'TDD',
                'nr_cell_type': 'NSA',
                'nr_channel': channel,
                'num_dl_cells': num_dl_cells,
                'num_ul_cells': num_ul_cells,
                'nr_dl_mimo_config': dl_mimo_config,
                'nr_ul_mimo_config': ul_mimo_config
            }
            endc_combo_config = self.generate_endc_combo_config(test_config)
            test_name = 'test_fr2_{}_{}_{}_DL_{}CC_mcs{}_{}x{}_UL_{}CC_mcs{}_{}x{}'.format(
                orientation, band, channel, num_dl_cells, nr_mcs_pair[0], dl_mimo_config,
                dl_mimo_config, num_ul_cells, nr_mcs_pair[1], ul_mimo_config,
                ul_mimo_config)
            test_params = collections.OrderedDict(
                endc_combo_config=endc_combo_config,
                nr_dl_mcs=nr_mcs_pair[0],
                nr_ul_mcs=nr_mcs_pair[1],
                orientation=orientation,
                **kwargs)
            setattr(self, test_name,
                    partial(self._test_throughput_bler, test_params))
            test_cases.append(test_name)
        return test_cases


class CellularFr2DlPeakThroughputTest(CellularFr2PeakThroughputTest):
    """Base class to test cellular FR2 throughput

    This class implements cellular FR2 throughput tests on a callbox setup.
    The class setups up the callbox in the desired configurations, configures
    and connects the phone, and runs traffic/iperf throughput.
    """

    def __init__(self, controllers):
        super().__init__(controllers)
        self.testclass_params = self.user_params['fr2_throughput_test_params']
        self.tests = self.generate_test_cases(['N257', 'N258', 'N260', 'N261'],
                                              ['low', 'mid', 'high'],
                                              [(16, 4), (27, 4)],
                                              list(range(1, 9)),
                                              list(range(1, 3)),
                                              ['A_Plane', 'B_Plane'],
                                              force_contiguous_nr_channel=True,
                                              dl_mimo_config=2,
                                              ul_mimo_config=1,
                                              schedule_scenario="FULL_TPUT",
                                              schedule_slot_ratio=80,
                                              traffic_direction='DL',
                                              transform_precoding=0,
                                              lte_dl_mcs=4,
                                              lte_dl_mcs_table='QAM256',
                                              lte_ul_mcs=4,
                                              lte_ul_mcs_table='QAM64')


class CellularFr2CpOfdmUlPeakThroughputTest(CellularFr2PeakThroughputTest):

    def __init__(self, controllers):
        super().__init__(controllers)
        self.testclass_params = self.user_params['throughput_test_params']
        self.tests = self.generate_test_cases(['N257', 'N258', 'N260', 'N261'],
                                              ['low', 'mid', 'high'],
                                              [(4, 16), (4, 27)], [1], [1],
                                              ['A_Plane', 'B_Plane'],
                                              force_contiguous_nr_channel=True,
                                              dl_mimo_config=2,
                                              ul_mimo_config=1,
                                              schedule_scenario="FULL_TPUT",
                                              schedule_slot_ratio=80,
                                              traffic_direction='UL',
                                              transform_precoding=0,
                                              lte_dl_mcs=4,
                                              lte_dl_mcs_table='QAM256',
                                              lte_ul_mcs=4,
                                              lte_ul_mcs_table='QAM64')
        self.tests.extend(
            self.generate_test_cases(['N257', 'N258', 'N260', 'N261'],
                                     ['low', 'mid', 'high'],
                                     [(4, 16), (4, 27)], [1], [1],
                                     ['A_Plane', 'B_Plane'],
                                     force_contiguous_nr_channel=True,
                                     dl_mimo_config=2,
                                     ul_mimo_config=2,
                                     schedule_scenario="FULL_TPUT",
                                     schedule_slot_ratio=80,
                                     traffic_direction='UL',
                                     transform_precoding=0,
                                     lte_dl_mcs=4,
                                     lte_dl_mcs_table='QAM256',
                                     lte_ul_mcs=4,
                                     lte_ul_mcs_table='QAM64'))
        self.tests.extend(
            self.generate_test_cases(['N257', 'N258', 'N260', 'N261'],
                                     ['low', 'mid', 'high'],
                                     [(4, 16), (4, 27)], [2], [2],
                                     ['A_Plane', 'B_Plane'],
                                     force_contiguous_nr_channel=True,
                                     dl_mimo_config=2,
                                     ul_mimo_config=2,
                                     schedule_scenario="FULL_TPUT",
                                     schedule_slot_ratio=80,
                                     traffic_direction='UL',
                                     transform_precoding=0,
                                     lte_dl_mcs=4,
                                     lte_dl_mcs_table='QAM256',
                                     lte_ul_mcs=4,
                                     lte_ul_mcs_table='QAM64'))
        self.tests.extend(
            self.generate_test_cases(['N257', 'N258', 'N260', 'N261'],
                                     ['low', 'mid', 'high'],
                                     [(4, 16), (4, 27)], [4], [4],
                                     ['A_Plane', 'B_Plane'],
                                     force_contiguous_nr_channel=True,
                                     dl_mimo_config=2,
                                     ul_mimo_config=2,
                                     schedule_scenario="FULL_TPUT",
                                     schedule_slot_ratio=80,
                                     traffic_direction='UL',
                                     transform_precoding=0,
                                     lte_dl_mcs=4,
                                     lte_dl_mcs_table='QAM256',
                                     lte_ul_mcs=4,
                                     lte_ul_mcs_table='QAM64'))


class CellularFr2DftsOfdmUlPeakThroughputTest(CellularFr2PeakThroughputTest):

    def __init__(self, controllers):
        super().__init__(controllers)
        self.testclass_params = self.user_params['throughput_test_params']
        self.tests = self.generate_test_cases(['N257', 'N258', 'N260', 'N261'],
                                              ['low', 'mid', 'high'],
                                              [(4, 16), (4, 27)], [1], [1],
                                              ['A_Plane', 'B_Plane'],
                                              force_contiguous_nr_channel=True,
                                              dl_mimo_config=2,
                                              ul_mimo_config=1,
                                              schedule_scenario="FULL_TPUT",
                                              schedule_slot_ratio=80,
                                              traffic_direction='UL',
                                              transform_precoding=1,
                                              lte_dl_mcs=4,
                                              lte_dl_mcs_table='QAM256',
                                              lte_ul_mcs=4,
                                              lte_ul_mcs_table='QAM64')
        self.tests.extend(
            self.generate_test_cases(['N257', 'N258', 'N260', 'N261'],
                                     ['low', 'mid', 'high'],
                                     [(4, 16), (4, 27)], [1], [1],
                                     ['A_Plane', 'B_Plane'],
                                     force_contiguous_nr_channel=True,
                                     dl_mimo_config=2,
                                     ul_mimo_config=2,
                                     schedule_scenario="FULL_TPUT",
                                     schedule_slot_ratio=80,
                                     traffic_direction='UL',
                                     transform_precoding=1,
                                     lte_dl_mcs=4,
                                     lte_dl_mcs_table='QAM256',
                                     lte_ul_mcs=4,
                                     lte_ul_mcs_table='QAM64'))
        self.tests.extend(
            self.generate_test_cases(['N257', 'N258', 'N260', 'N261'],
                                     ['low', 'mid', 'high'],
                                     [(4, 16), (4, 27)], [2], [2],
                                     ['A_Plane', 'B_Plane'],
                                     force_contiguous_nr_channel=True,
                                     dl_mimo_config=2,
                                     ul_mimo_config=2,
                                     schedule_scenario="FULL_TPUT",
                                     schedule_slot_ratio=80,
                                     traffic_direction='UL',
                                     transform_precoding=1,
                                     lte_dl_mcs=4,
                                     lte_dl_mcs_table='QAM256',
                                     lte_ul_mcs=4,
                                     lte_ul_mcs_table='QAM64'))
        self.tests.extend(
            self.generate_test_cases(['N257', 'N258', 'N260', 'N261'],
                                     ['low', 'mid', 'high'],
                                     [(4, 16), (4, 27)], [4], [4],
                                     ['A_Plane', 'B_Plane'],
                                     force_contiguous_nr_channel=True,
                                     dl_mimo_config=2,
                                     ul_mimo_config=2,
                                     schedule_scenario="FULL_TPUT",
                                     schedule_slot_ratio=80,
                                     traffic_direction='UL',
                                     transform_precoding=1,
                                     lte_dl_mcs=4,
                                     lte_dl_mcs_table='QAM256',
                                     lte_ul_mcs=4,
                                     lte_ul_mcs_table='QAM64'))


class CellularFr2DlFrequencySweepPeakThroughputTest(
        CellularFr2PeakThroughputTest):
    """Base class to test cellular FR2 throughput

    This class implements cellular FR2 throughput tests on a callbox setup.
    The class setups up the callbox in the desired configurations, configures
    and connects the phone, and runs traffic/iperf throughput.
    """

    def __init__(self, controllers):
        super().__init__(controllers)
        self.testclass_params = self.user_params['throughput_test_params']
        self.tests = self.generate_test_cases(
            ['N257', 'N258', 'N260', 'N261'],
            self.user_params['throughput_test_params']['frequency_sweep'],
            [(16, 4), (27, 4)],
            ['A_Plane', 'B_Plane'],
            force_contiguous_nr_channel=False,
            dl_mimo_config=2,
            ul_mimo_config=1,
            schedule_scenario="FULL_TPUT",
            schedule_slot_ratio=80,
            traffic_direction='DL',
            transform_precoding=0,
            lte_dl_mcs=4,
            lte_dl_mcs_table='QAM256',
            lte_ul_mcs=4,
            lte_ul_mcs_table='QAM64')

    def generate_test_cases(self, bands, channels, nr_mcs_pair_list,
                            num_dl_cells_list, num_ul_cells_list,
                            dl_mimo_config, ul_mimo_config, **kwargs):
        """Function that auto-generates test cases for a test class."""
        test_cases = []
        for band, channel, num_ul_cells, num_dl_cells, nr_mcs_pair in itertools.product(
                bands, channels, num_ul_cells_list, num_dl_cells_list,
                nr_mcs_pair_list):
            if num_ul_cells > num_dl_cells:
                continue
            if channel not in cputils.PCC_PRESET_MAPPING[band]:
                continue
            test_config = {
                'lte_band': 2,
                'lte_bandwidth': 'BW20',
                'lte_duplex_mode': 'FDD',
                'lte_dl_mimo_config': 1,
                'lte_ul_mimo_config': 1,
                'nr_band': band,
                'nr_bandwidth': 'BW100',
                'nr_duplex_mode': 'TDD',
                'nr_channel': channel,
                'num_dl_cells': num_dl_cells,
                'num_ul_cells': num_ul_cells,
                'nr_dl_mimo_config': dl_mimo_config,
                'nr_ul_mimo_config': ul_mimo_config
            }
            endc_combo_config = self.generate_endc_combo_config(test_config)
            test_name = 'test_fr2_{}_{}_DL_{}CC_mcs{}_{}x{}_UL_{}CC_mcs{}_{}x{}'.format(
                band, channel, num_dl_cells, nr_mcs_pair[0], dl_mimo_config,
                dl_mimo_config, num_ul_cells, nr_mcs_pair[1], ul_mimo_config,
                ul_mimo_config)
            test_params = collections.OrderedDict(
                endc_combo_config=endc_combo_config,
                nr_dl_mcs=nr_mcs_pair[0],
                nr_ul_mcs=nr_mcs_pair[1],
                **kwargs)
            setattr(self, test_name,
                    partial(self._test_throughput_bler, test_params))
            test_cases.append(test_name)
        return test_cases
