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
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils
from acts_contrib.test_utils.wifi.wifi_performance_test_utils.bokeh_figure import BokehFigure
from acts_contrib.test_utils.cellular.performance.CellularThroughputBaseTest import CellularThroughputBaseTest

from functools import partial


class CellularFr2SensitivityTest(CellularThroughputBaseTest):
    """Class to test single cell FR1 NSA sensitivity"""

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        self.testclass_params = self.user_params['fr2_sensitivity_test_params']
        self.log.info('Hello')
        self.tests = self.generate_test_cases(
            band_list=['N257', 'N258', 'N260', 'N261'],
            channel_list=['low', 'mid', 'high'],
            dl_mcs_list=list(numpy.arange(27, -1, -1)),
            num_dl_cells_list=[1, 2, 4, 8],
            orientation_list=['A_Plane', 'B_Plane'],
            dl_mimo_config=2,
            nr_ul_mcs=4,
            lte_dl_mcs_table='QAM256',
            lte_dl_mcs=4,
            lte_ul_mcs_table='QAM256',
            lte_ul_mcs=4,
            schedule_scenario="FULL_TPUT",
            schedule_slot_ratio= 80,
            force_contiguous_nr_channel=True,
            transform_precoding=0)

    def process_testclass_results(self):
        # Plot individual test id results raw data and compile metrics
        plots = collections.OrderedDict()
        compiled_data = collections.OrderedDict()
        for testcase_name, testcase_data in self.testclass_results.items():
            nr_cell_index = testcase_data['testcase_params'][
                'endc_combo_config']['lte_cell_count']
            cell_config = testcase_data['testcase_params'][
                'endc_combo_config']['cell_list'][nr_cell_index]
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
                    title='Band {} - BLER Curves'.format(cell_config['band']),
                    x_label='Cell Power (dBm)',
                    primary_y_label='BLER (Mbps)')
                test_id_rvr = test_id + tuple('RvR')
                plots[test_id_rvr] = BokehFigure(
                    title='Band {} - RvR'.format(cell_config['band']),
                    x_label='Cell Power (dBm)',
                    primary_y_label='PHY Rate (Mbps)')
            # Compile test id data and metrics
            compiled_data[test_id]['average_throughput'].append(
                testcase_data['average_throughput_list'])
            compiled_data[test_id]['cell_power'].append(
                testcase_data['cell_power_list'])
            compiled_data[test_id]['mcs'].append(
                testcase_data['testcase_params']['nr_dl_mcs'])
            # Add test id to plots
            plots[test_id].add_line(
                testcase_data['cell_power_list'],
                testcase_data['bler_list'],
                'MCS {}'.format(testcase_data['testcase_params']['nr_dl_mcs']),
                width=1)
            plots[test_id_rvr].add_line(
                testcase_data['cell_power_list'],
                testcase_data['average_throughput_list'],
                'MCS {}'.format(testcase_data['testcase_params']['nr_dl_mcs']),
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
        nr_cell_index = testcase_data['testcase_params']['endc_combo_config'][
            'lte_cell_count']
        cell_power_list = testcase_data['testcase_params']['cell_power_sweep'][
            nr_cell_index]
        for result in testcase_data['results']:
            bler_list.append(result['throughput_measurements']
                             ['nr_bler_result']['total']['DL']['nack_ratio'])
            average_throughput_list.append(
                result['throughput_measurements']['nr_tput_result']['total']
                ['DL']['average_tput'])
            theoretical_throughput_list.append(
                result['throughput_measurements']['nr_tput_result']['total']
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
        self.log.info('NR Band {} MCS {} Sensitivity = {}dBm'.format(
            testcase_data['testcase_params']['endc_combo_config']['cell_list']
            [nr_cell_index]['band'],
            testcase_data['testcase_params']['nr_dl_mcs'], sensitivity))

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
        current_band = testcase_params['endc_combo_config']['cell_list'][1][
            'band']
        reference_test = None
        reference_sensitivity = None
        for testcase_name, testcase_data in self.testclass_results.items():
            if testcase_data['testcase_params']['endc_combo_config'][
                    'cell_list'][1]['band'] == current_band:
                reference_test = testcase_name
                reference_sensitivity = testcase_data['sensitivity']
        if reference_test and reference_sensitivity and not self.retry_flag:
            start_atten = reference_sensitivity + self.testclass_params[
                'adjacent_mcs_gap']
            self.log.info(
                "Reference test {} found. Sensitivity {} dBm. Starting at {} dBm"
                .format(reference_test, reference_sensitivity, start_atten))
        else:
            start_atten = self.testclass_params['nr_cell_power_start']
            self.log.info(
                "Reference test not found. Starting at {} dBm".format(
                    start_atten))
        # get current cell power start
        nr_cell_sweep = list(
            numpy.arange(start_atten,
                         self.testclass_params['nr_cell_power_stop'],
                         self.testclass_params['nr_cell_power_step']))
        lte_sweep = [self.testclass_params['lte_cell_power']
                     ] * len(nr_cell_sweep)
        cell_power_sweeps = [lte_sweep]
        cell_power_sweeps.extend(
            [nr_cell_sweep] *
            testcase_params['endc_combo_config']['nr_cell_count'])
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

    def generate_test_cases(self, band_list, channel_list, dl_mcs_list,
                            num_dl_cells_list, dl_mimo_config, orientation_list, **kwargs):
        """Function that auto-generates test cases for a test class."""
        test_cases = []
        for orientation, band, channel, num_dl_cells, nr_dl_mcs in itertools.product(
                orientation_list, band_list, channel_list, num_dl_cells_list, dl_mcs_list):
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
                'num_ul_cells': 1,
                'nr_dl_mimo_config': dl_mimo_config,
                'nr_ul_mimo_config': 1
            }
            endc_combo_config = self.generate_endc_combo_config(test_config)
            test_name = 'test_fr2_{}_{}_{}CC_mcs{}_{}x{}'.format(
                band, channel.lower(), num_dl_cells, nr_dl_mcs, dl_mimo_config,
                dl_mimo_config)
            test_params = collections.OrderedDict(
                endc_combo_config=endc_combo_config,
                nr_dl_mcs=nr_dl_mcs,
                orientation=orientation,
                **kwargs)
            setattr(self, test_name,
                    partial(self._test_throughput_bler, test_params))
            test_cases.append(test_name)
        return test_cases
