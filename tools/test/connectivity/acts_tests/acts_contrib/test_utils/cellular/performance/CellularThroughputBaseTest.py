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
import time
from acts import asserts
from acts import context
from acts import base_test
from acts import utils
from acts.metrics.loggers.blackbox import BlackboxMappedMetricLogger
from acts.controllers.utils_lib import ssh
from acts.controllers.android_lib.tel import tel_utils
from acts.controllers import iperf_server as ipf
from acts_contrib.test_utils.cellular.keysight_5g_testapp import Keysight5GTestApp
from acts_contrib.test_utils.cellular.keysight_chamber import KeysightChamber
from acts_contrib.test_utils.cellular.performance import cellular_performance_test_utils as cputils
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils
from functools import partial

LONG_SLEEP = 10
MEDIUM_SLEEP = 2
IPERF_TIMEOUT = 10
SHORT_SLEEP = 1
VERY_SHORT_SLEEP = 0.1
SUBFRAME_LENGTH = 0.001
STOP_COUNTER_LIMIT = 3


class CellularThroughputBaseTest(base_test.BaseTestClass):
    """Base class for Cellular Throughput Testing

    This base class enables cellular throughput tests on a lab/callbox setup
    with PHY layer or iperf traffic.
    """

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        self.testclass_params = None

    def setup_class(self):
        """Initializes common test hardware and parameters.

        This function initializes hardwares and compiles parameters that are
        common to all tests in this class.
        """
        # Setup controllers
        self.dut = self.android_devices[-1]
        self.keysight_test_app = Keysight5GTestApp(
            self.user_params['Keysight5GTestApp'])
        if 'KeysightChamber' in self.user_params:
            self.keysight_chamber = KeysightChamber(
                self.user_params['KeysightChamber'])
        self.iperf_server = self.iperf_servers[0]
        self.iperf_client = self.iperf_clients[0]
        self.remote_server = ssh.connection.SshConnection(
            ssh.settings.from_config(
                self.user_params['RemoteServer']['ssh_config']))

        # Configure Tester
        if self.testclass_params.get('reload_scpi', 1):
            self.keysight_test_app.import_scpi_file(
                self.testclass_params['scpi_file'])

        # Declare testclass variables
        self.testclass_results = collections.OrderedDict()

        # Configure test retries
        self.user_params['retry_tests'] = [self.__class__.__name__]

        # Turn Airplane mode on
        #asserts.assert_true(utils.force_airplane_mode(self.dut, True),
        #                    'Can not turn on airplane mode.')
        tel_utils.toggle_airplane_mode(self.log, self.dut, True)

    def teardown_class(self):
        self.log.info('Turning airplane mode on')
        try:
            #asserts.assert_true(utils.force_airplane_mode(self.dut, True),
            #                    'Can not turn on airplane mode.')
            tel_utils.toggle_airplane_mode(self.log, self.dut, True)
        except:
            self.log.warning('Cannot perform teardown operations on DUT.')
        try:
            self.keysight_test_app.turn_all_cells_off()
            self.keysight_test_app.destroy()
        except:
            self.log.warning('Cannot perform teardown operations on tester.')
        self.process_testclass_results()

    def setup_test(self):
        self.retry_flag = False
        if self.testclass_params['enable_pixel_logs']:
            cputils.start_pixel_logger(self.dut)

    def teardown_test(self):
        self.retry_flag = False
        self.log.info('Turing airplane mode on')
        #asserts.assert_true(utils.force_airplane_mode(self.dut, True),
        #                    'Can not turn on airplane mode.')
        tel_utils.toggle_airplane_mode(self.log, self.dut, True)
        self.log.info('Turning all cells off.')
        self.keysight_test_app.turn_all_cells_off()
        log_path = os.path.join(
            context.get_current_context().get_full_output_path(), 'pixel_logs')
        os.makedirs(self.log_path, exist_ok=True)
        if self.testclass_params['enable_pixel_logs']:
            cputils.stop_pixel_logger(self.dut, log_path)
        self.process_testcase_results()
        self.pass_fail_check()

    def on_retry(self):
        """Function to control test logic on retried tests.

        This function is automatically executed on tests that are being
        retried. In this case the function resets wifi, toggles it off and on
        and sets a retry_flag to enable further tweaking the test logic on
        second attempts.
        """
        #asserts.assert_true(utils.force_airplane_mode(self.dut, True),
        #                    'Can not turn on airplane mode.')
        tel_utils.toggle_airplane_mode(self.log, self.dut, True)
        if self.keysight_test_app.get_cell_state('LTE', 'CELL1'):
            self.log.info('Turning LTE off.')
            self.keysight_test_app.set_cell_state('LTE', 'CELL1', 0)
        self.retry_flag = True

    def pass_fail_check(self):
        pass

    def process_testcase_results(self):
        pass

    def process_testclass_results(self):
        pass

    def get_per_cell_power_sweeps(self, testcase_params):
        raise NotImplementedError(
            'get_per_cell_power_sweeps must be implemented.')

    def compile_test_params(self, testcase_params):
        """Function that completes all test params based on the test name.

        Args:
            testcase_params: dict containing test-specific parameters
        """
        # Measurement Duration
        testcase_params['bler_measurement_length'] = int(
            self.testclass_params['traffic_duration'] / SUBFRAME_LENGTH)
        # Cell power sweep
        # TODO: Make this a function to support single power and sweep modes for each cell
        testcase_params['cell_power_sweep'] = self.get_per_cell_power_sweeps(
            testcase_params)
        # Traffic & iperf params
        if self.testclass_params['traffic_type'] == 'PHY':
            return testcase_params
        if self.testclass_params['traffic_type'] == 'TCP':
            testcase_params['iperf_socket_size'] = self.testclass_params.get(
                'tcp_socket_size', None)
            testcase_params['iperf_processes'] = self.testclass_params.get(
                'tcp_processes', 1)
        elif self.testclass_params['traffic_type'] == 'UDP':
            testcase_params['iperf_socket_size'] = self.testclass_params.get(
                'udp_socket_size', None)
            testcase_params['iperf_processes'] = self.testclass_params.get(
                'udp_processes', 1)
        adb_iperf_server = isinstance(self.iperf_server,
                                      ipf.IPerfServerOverAdb)
        if testcase_params['traffic_direction'] == 'DL':
            reverse_direction = 0 if adb_iperf_server else 1
            testcase_params[
                'use_client_output'] = False if adb_iperf_server else True
        elif testcase_params['traffic_direction'] == 'UL':
            reverse_direction = 1 if adb_iperf_server else 0
            testcase_params[
                'use_client_output'] = True if adb_iperf_server else False
        testcase_params['iperf_args'] = wputils.get_iperf_arg_string(
            duration=self.testclass_params['traffic_duration'],
            reverse_direction=reverse_direction,
            traffic_type=self.testclass_params['traffic_type'],
            socket_size=testcase_params['iperf_socket_size'],
            num_processes=testcase_params['iperf_processes'],
            udp_throughput=self.testclass_params['UDP_rates'].get(
                testcase_params['num_dl_cells'],
                self.testclass_params['UDP_rates']["default"]),
            udp_length=1440)
        return testcase_params

    def run_iperf_traffic(self, testcase_params):
        self.iperf_server.start(tag=0)
        dut_ip = self.dut.droid.connectivityGetIPv4Addresses('rmnet0')[0]
        if 'iperf_server_address' in self.testclass_params:
            iperf_server_address = self.testclass_params[
                'iperf_server_address']
        elif isinstance(self.iperf_server, ipf.IPerfServerOverAdb):
            iperf_server_address = dut_ip
        else:
            iperf_server_address = wputils.get_server_address(
                self.remote_server, dut_ip, '255.255.255.0')
        client_output_path = self.iperf_client.start(
            iperf_server_address, testcase_params['iperf_args'], 0,
            self.testclass_params['traffic_duration'] + IPERF_TIMEOUT)
        server_output_path = self.iperf_server.stop()
        # Parse and log result
        if testcase_params['use_client_output']:
            iperf_file = client_output_path
        else:
            iperf_file = server_output_path
        try:
            iperf_result = ipf.IPerfResult(iperf_file)
            current_throughput = numpy.mean(iperf_result.instantaneous_rates[
                self.testclass_params['iperf_ignored_interval']:-1]) * 8 * (
                    1.024**2)
        except:
            self.log.warning(
                'ValueError: Cannot get iperf result. Setting to 0')
            current_throughput = 0
        return current_throughput

    def run_single_throughput_measurement(self, testcase_params):
        result = collections.OrderedDict()
        self.log.info('Starting BLER & throughput tests.')
        if testcase_params['endc_combo_config']['nr_cell_count']:
            self.keysight_test_app.start_bler_measurement(
                'NR5G', testcase_params['endc_combo_config']['nr_dl_carriers'],
                testcase_params['bler_measurement_length'])
        if testcase_params['endc_combo_config']['lte_cell_count']:
            self.keysight_test_app.start_bler_measurement(
                'LTE', testcase_params['endc_combo_config']['lte_dl_carriers'][0],
                testcase_params['bler_measurement_length'])

        if self.testclass_params['traffic_type'] != 'PHY':
            result['iperf_throughput'] = self.run_iperf_traffic(
                testcase_params)

        if testcase_params['endc_combo_config']['nr_cell_count']:
            result['nr_bler_result'] = self.keysight_test_app.get_bler_result(
                'NR5G', testcase_params['endc_combo_config']['nr_dl_carriers'],
                testcase_params['endc_combo_config']['nr_ul_carriers'],
                testcase_params['bler_measurement_length'])
            result['nr_tput_result'] = self.keysight_test_app.get_throughput(
                'NR5G', testcase_params['endc_combo_config']['nr_dl_carriers'],
                testcase_params['endc_combo_config']['nr_ul_carriers'])
        if testcase_params['endc_combo_config']['lte_cell_count']:
            result['lte_bler_result'] = self.keysight_test_app.get_bler_result(
                cell_type='LTE', dl_cells=testcase_params['endc_combo_config']['lte_dl_carriers'],
                ul_cells=testcase_params['endc_combo_config']['lte_ul_carriers'],
                length=testcase_params['bler_measurement_length'])
            result['lte_tput_result'] = self.keysight_test_app.get_throughput(
                'LTE', testcase_params['endc_combo_config']['lte_dl_carriers'],
                testcase_params['endc_combo_config']['lte_ul_carriers'])
        return result

    def print_throughput_result(self, result):
        # Print Test Summary
        if 'nr_tput_result' in result:

            self.log.info(
                "NR5G DL PHY Tput (Mbps) (Min/Avg/Max/Th): {:.2f} / {:.2f} / {:.2f} / {:.2f}\tBLER: {:.2f}"
                .format(
                    result['nr_tput_result']['total']['DL']['min_tput'],
                    result['nr_tput_result']['total']['DL']['average_tput'],
                    result['nr_tput_result']['total']['DL']['max_tput'],
                    result['nr_tput_result']['total']['DL']['theoretical_tput'],
                    result['nr_bler_result']['total']['DL']['nack_ratio'] * 100))
            self.log.info(
                "NR5G UL PHY Tput (Mbps) (Min/Avg/Max/Th): {:.2f} / {:.2f} / {:.2f} / {:.2f}\tBLER: {:.2f}"
                .format(
                    result['nr_tput_result']['total']['UL']['min_tput'],
                    result['nr_tput_result']['total']['UL']['average_tput'],
                    result['nr_tput_result']['total']['UL']['max_tput'],
                    result['nr_tput_result']['total']['UL']['theoretical_tput'],
                    result['nr_bler_result']['total']['UL']['nack_ratio'] * 100))
        if 'lte_tput_result' in result:
            self.log.info(
                "LTE DL PHY Tput (Mbps) (Min/Avg/Max/Th): {:.2f} / {:.2f} / {:.2f} / {:.2f}\tBLER: {:.2f}"
                .format(
                    result['lte_tput_result']['total']['DL']['min_tput'],
                    result['lte_tput_result']['total']['DL']['average_tput'],
                    result['lte_tput_result']['total']['DL']['max_tput'],
                    result['lte_tput_result']['total']['DL']['theoretical_tput'],
                    result['lte_bler_result']['total']['DL']['nack_ratio'] * 100))
            if self.testclass_params['lte_ul_mac_padding']:
                self.log.info(
                    "LTE UL PHY Tput (Mbps) (Min/Avg/Max/Th): {:.2f} / {:.2f} / {:.2f} / {:.2f}\tBLER: {:.2f}"
                    .format(
                        result['lte_tput_result']['total']['UL']['min_tput'],
                        result['lte_tput_result']['total']['UL']['average_tput'],
                        result['lte_tput_result']['total']['UL']['max_tput'],
                        result['lte_tput_result']['total']['UL']['theoretical_tput'],
                        result['lte_bler_result']['total']['UL']['nack_ratio'] * 100))
            if self.testclass_params['traffic_type'] != 'PHY':
                self.log.info("{} Tput: {:.2f} Mbps".format(
                    self.testclass_params['traffic_type'],
                    result['iperf_throughput']))

    def setup_tester(self, testcase_params):
        # Configure all cells
        for cell_idx, cell in enumerate(
                testcase_params['endc_combo_config']['cell_list']):
            if cell['cell_type'] == 'NR5G':
                self.keysight_test_app.set_nr_cell_type(
                    cell['cell_type'], cell['cell_number'],
                    cell['nr_cell_type'])
            self.keysight_test_app.set_cell_duplex_mode(
                cell['cell_type'], cell['cell_number'], cell['duplex_mode'])
            self.keysight_test_app.set_cell_band(cell['cell_type'],
                                                 cell['cell_number'],
                                                 cell['band'])
            self.keysight_test_app.set_cell_dl_power(
                cell['cell_type'], cell['cell_number'],
                testcase_params['cell_power_sweep'][cell_idx][0], 1)
            self.keysight_test_app.set_cell_input_power(
                cell['cell_type'], cell['cell_number'],
               self.testclass_params['input_power'][cell['cell_type']])
            if cell['cell_type'] == 'LTE' and cell['pcc'] == 0:
                pass
            else:
                self.keysight_test_app.set_cell_ul_power_control(
                    cell['cell_type'], cell['cell_number'],
                    self.testclass_params['ul_power_control_mode'],
                    self.testclass_params.get('ul_power_control_target',0)
                )
            if cell['cell_type'] == 'NR5G':
                self.keysight_test_app.set_nr_subcarrier_spacing(
                    cell['cell_number'], cell['subcarrier_spacing'])
            if 'channel' in cell and cell['channel'] is not None:
                self.keysight_test_app.set_cell_channel(
                    cell['cell_type'], cell['cell_number'], cell['channel'])
            self.keysight_test_app.set_cell_bandwidth(cell['cell_type'],
                                                      cell['cell_number'],
                                                      cell['dl_bandwidth'])
            self.keysight_test_app.set_cell_mimo_config(
                cell['cell_type'], cell['cell_number'], 'DL',
                cell['dl_mimo_config'])
            if cell['cell_type'] == 'LTE':
                self.keysight_test_app.set_lte_cell_transmission_mode(
                    cell['cell_number'], cell['transmission_mode'])
                self.keysight_test_app.set_lte_control_region_size(
                    cell['cell_number'], 1)
            if cell['ul_enabled'] and cell['cell_type'] == 'NR5G':
                self.keysight_test_app.set_cell_mimo_config(
                    cell['cell_type'], cell['cell_number'], 'UL',
                    cell['ul_mimo_config'])
            if 'fading_scenario' in self.testclass_params:
                self.keysight_test_app.configure_channel_emulator(
                    cell['cell_type'], cell['cell_number'],
                    self.testclass_params['fading_scenario'][cell['cell_type']])

        if testcase_params.get('force_contiguous_nr_channel', False):
            self.keysight_test_app.toggle_contiguous_nr_channels(1)

        if testcase_params['endc_combo_config']['lte_cell_count']:
            self.keysight_test_app.set_lte_cell_mcs(
                'CELL1', testcase_params['lte_dl_mcs_table'],
                testcase_params['lte_dl_mcs'],
                testcase_params['lte_ul_mcs_table'],
                testcase_params['lte_ul_mcs'])
            self.keysight_test_app.set_lte_ul_mac_padding(
                self.testclass_params['lte_ul_mac_padding'])

        if testcase_params['endc_combo_config']['nr_cell_count']:
            if 'schedule_scenario' in testcase_params:
                self.keysight_test_app.set_nr_cell_schedule_scenario(
                    'CELL1',
                    testcase_params['schedule_scenario'])
                if testcase_params['schedule_scenario'] == 'FULL_TPUT':
                    self.keysight_test_app.set_nr_schedule_slot_ratio(
                        'CELL1',
                        testcase_params['schedule_slot_ratio'])
            self.keysight_test_app.set_nr_ul_dft_precoding(
                'CELL1', testcase_params['transform_precoding'])
            self.keysight_test_app.set_nr_cell_mcs(
                'CELL1', testcase_params['nr_dl_mcs'],
                testcase_params['nr_ul_mcs'])
            self.keysight_test_app.set_dl_carriers(
                testcase_params['endc_combo_config']['nr_dl_carriers'])
            self.keysight_test_app.set_ul_carriers(
                testcase_params['endc_combo_config']['nr_ul_carriers'])

        if testcase_params['endc_combo_config']['lte_cell_count']:
            # Connect flow for LTE and LTE+FR1 ENDC
            # Turn on LTE cells
            for cell in testcase_params['endc_combo_config']['cell_list']:
                if cell['cell_type'] == 'LTE' and not self.keysight_test_app.get_cell_state(
                        cell['cell_type'], cell['cell_number']):
                    self.log.info('Turning LTE Cell {} on.'.format(
                        cell['cell_number']))
                    self.keysight_test_app.set_cell_state(cell['cell_type'],
                                                          cell['cell_number'], 1)
            self.log.info('Waiting for LTE connections')
            # Turn airplane mode off
            num_apm_toggles = 10
            for idx in range(num_apm_toggles):
                self.log.info('Turning off airplane mode')
                cputils.toggle_airplane_mode(self.log, self.dut, False, False, idx)
                if self.keysight_test_app.wait_for_cell_status(
                        'LTE', 'CELL1', 'CONN', 10*(idx+1)):
                    self.log.info('Connected! Waiting for {} seconds.'.format(LONG_SLEEP))
                    time.sleep(LONG_SLEEP)
                    break
                elif idx < num_apm_toggles - 1:
                    self.log.info('Turning on airplane mode')
                    cputils.toggle_airplane_mode(self.log, self.dut, True, False, idx)
                    time.sleep(MEDIUM_SLEEP)
                else:
                    asserts.fail('DUT did not connect to LTE.')
            # Activate LTE aggregation if applicable
            if testcase_params['endc_combo_config']['lte_scc_list']:
                self.keysight_test_app.apply_lte_carrier_agg(
                    testcase_params['endc_combo_config']['lte_scc_list'])

            if testcase_params['endc_combo_config']['nr_cell_count']:
                self.keysight_test_app.apply_carrier_agg()
                self.log.info('Waiting for 5G connection')
                connected = self.keysight_test_app.wait_for_cell_status(
                    'NR5G', testcase_params['endc_combo_config']['nr_cell_count'],
                    ['ACT', 'CONN'], 60)
                if not connected:
                    asserts.fail('DUT did not connect to NR.')
            time.sleep(SHORT_SLEEP)
        elif testcase_params['endc_combo_config']['nr_cell_count']:
            # Connect flow for NR FR1 Standalone
            # Turn on NR cells
            for cell in testcase_params['endc_combo_config']['cell_list']:
                if cell['cell_type'] == 'NR5G' and not self.keysight_test_app.get_cell_state(
                        cell['cell_type'], cell['cell_number']):
                    self.log.info('Turning NR Cell {} on.'.format(
                        cell['cell_number']))
                    self.keysight_test_app.set_cell_state(cell['cell_type'],
                                                          cell['cell_number'], 1)
            num_apm_toggles = 10
            for idx in range(num_apm_toggles):
                self.log.info('Turning off airplane mode now.')
                cputils.toggle_airplane_mode(self.log, self.dut, False, False, idx)
                if self.keysight_test_app.wait_for_cell_status(
                        'NR5G', 'CELL1', 'CONN', 10*(idx+1)):
                    self.log.info('Connected! Waiting for {} seconds.'.format(LONG_SLEEP))
                    time.sleep(LONG_SLEEP)
                    break
                elif idx < num_apm_toggles - 1:
                    self.log.info('Turning on airplane mode now.')
                    cputils.toggle_airplane_mode(self.log, self.dut, True, False, idx)
                    time.sleep(MEDIUM_SLEEP)
                else:
                    asserts.fail('DUT did not connect to NR.')

        if 'fading_scenario' in self.testclass_params and self.testclass_params['fading_scenario']['enable']:
            self.log.info('Enabling fading.')
            self.keysight_test_app.set_channel_emulator_state(self.testclass_params['fading_scenario']['enable'])
        else:
            self.keysight_test_app.set_channel_emulator_state(0)

    def _test_throughput_bler(self, testcase_params):
        """Test function to run cellular throughput and BLER measurements.

        The function runs BLER/throughput measurement after configuring the
        callbox and DUT. The test supports running PHY or TCP/UDP layer traffic
        in a variety of band/carrier/mcs/etc configurations.

        Args:
            testcase_params: dict containing test-specific parameters
        Returns:
            result: dict containing throughput results and meta data
        """
        # Prepare results dicts
        testcase_params = self.compile_test_params(testcase_params)
        testcase_results = collections.OrderedDict()
        testcase_results['testcase_params'] = testcase_params
        testcase_results['results'] = []

        # Setup ota chamber if needed
        if hasattr(self, 'keysight_chamber') and 'orientation' in testcase_params:
            self.keysight_chamber.move_theta_phi_abs(
                self.keysight_chamber.preset_orientations[testcase_params['orientation']]['theta'],
                self.keysight_chamber.preset_orientations[testcase_params['orientation']]['phi'])

        # Setup tester and wait for DUT to connect
        self.setup_tester(testcase_params)

        # Run throughput test loop
        stop_counter = 0
        if testcase_params['endc_combo_config']['nr_cell_count']:
            self.keysight_test_app.select_display_tab('NR5G', 1, 'BTHR',
                                                      'OTAGRAPH')
        else:
            self.keysight_test_app.select_display_tab('LTE', 1, 'BTHR',
                                                      'OTAGRAPH')
        for power_idx in range(len(testcase_params['cell_power_sweep'][0])):
            result = collections.OrderedDict()
            # Check that cells are still connected
            connected = 1
            for cell in testcase_params['endc_combo_config']['cell_list']:
                if not self.keysight_test_app.wait_for_cell_status(
                    cell['cell_type'], cell['cell_number'],
                        ['ACT', 'CONN'], VERY_SHORT_SLEEP,VERY_SHORT_SLEEP):
                    connected = 0
            if not connected:
                self.log.info('DUT lost connection to cells. Ending test.')
                break
            # Set DL cell power
            for cell_idx, cell in enumerate(
                    testcase_params['endc_combo_config']['cell_list']):
                cell_power_array = []
                current_cell_power = testcase_params['cell_power_sweep'][
                    cell_idx][power_idx]
                cell_power_array.append(current_cell_power)
                self.keysight_test_app.set_cell_dl_power(
                    cell['cell_type'], cell['cell_number'], current_cell_power,
                    1)
            result['cell_power'] = cell_power_array
            # Start BLER and throughput measurements
            current_throughput = self.run_single_throughput_measurement(testcase_params)
            lte_rx_meas = cputils.get_rx_measurements(self.dut, 'LTE')
            nr_rx_meas = cputils.get_rx_measurements(self.dut, 'NR5G')
            result['throughput_measurements'] = current_throughput
            result['lte_rx_measurements'] = lte_rx_meas
            result['nr_rx_measurements'] = nr_rx_meas

            self.print_throughput_result(current_throughput)
            self.log.info('LTE Rx Measurements: {}'.format(lte_rx_meas))
            self.log.info('NR Rx Measurements: {}'.format(nr_rx_meas))

            testcase_results['results'].append(result)
            if (('lte_bler_result' in result['throughput_measurements']
                 and result['throughput_measurements']['lte_bler_result']['total']['DL']['nack_ratio'] *
                 100 > 99) or
                ('nr_bler_result' in result['throughput_measurements']
                 and result['throughput_measurements']['nr_bler_result']['total']['DL']['nack_ratio'] *
                 100 > 99)):
                stop_counter = stop_counter + 1
            else:
                stop_counter = 0
            if stop_counter == STOP_COUNTER_LIMIT:
                break

        # Save results
        self.testclass_results[self.current_test_name] = testcase_results
