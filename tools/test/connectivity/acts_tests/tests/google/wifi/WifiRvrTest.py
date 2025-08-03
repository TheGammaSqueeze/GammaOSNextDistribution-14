#!/usr/bin/env python3.4
#
#   Copyright 2017 - The Android Open Source Project
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
import logging
import numpy
import os
import time
from acts import asserts
from acts import base_test
from acts import utils
from acts.controllers import iperf_server as ipf
from acts.controllers.utils_lib import ssh
from acts.metrics.loggers.blackbox import BlackboxMappedMetricLogger
from acts_contrib.test_utils.wifi import ota_chamber
from acts_contrib.test_utils.wifi import ota_sniffer
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils
from acts_contrib.test_utils.wifi.wifi_performance_test_utils.bokeh_figure import BokehFigure
from acts_contrib.test_utils.wifi import wifi_retail_ap as retail_ap
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from functools import partial


class WifiRvrTest(base_test.BaseTestClass):
    """Class to test WiFi rate versus range.

    This class implements WiFi rate versus range tests on single AP single STA
    links. The class setups up the AP in the desired configurations, configures
    and connects the phone to the AP, and runs iperf throughput test while
    sweeping attenuation. For an example config file to run this test class see
    example_connectivity_performance_ap_sta.json.
    """

    TEST_TIMEOUT = 6
    MAX_CONSECUTIVE_ZEROS = 3

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True

    def setup_class(self):
        """Initializes common test hardware and parameters.

        This function initializes hardwares and compiles parameters that are
        common to all tests in this class.
        """
        self.sta_dut = self.android_devices[0]
        req_params = [
            'RetailAccessPoints', 'rvr_test_params', 'testbed_params',
            'RemoteServer', 'main_network'
        ]
        opt_params = ['golden_files_list', 'OTASniffer']
        self.unpack_userparams(req_params, opt_params)
        self.testclass_params = self.rvr_test_params
        self.num_atten = self.attenuators[0].instrument.num_atten
        self.iperf_server = self.iperf_servers[0]
        self.remote_server = ssh.connection.SshConnection(
            ssh.settings.from_config(self.RemoteServer[0]['ssh_config']))
        self.iperf_client = self.iperf_clients[0]
        self.access_point = retail_ap.create(self.RetailAccessPoints)[0]
        if hasattr(self,
                   'OTASniffer') and self.testbed_params['sniffer_enable']:
            try:
                self.sniffer = ota_sniffer.create(self.OTASniffer)[0]
            except:
                self.log.warning('Could not start sniffer. Disabling sniffs.')
                self.testbed_params['sniffer_enable'] = 0
        self.log.info('Access Point Configuration: {}'.format(
            self.access_point.ap_settings))
        self.log_path = os.path.join(logging.log_path, 'results')
        os.makedirs(self.log_path, exist_ok=True)
        if not hasattr(self, 'golden_files_list'):
            if 'golden_results_path' in self.testbed_params:
                self.golden_files_list = [
                    os.path.join(self.testbed_params['golden_results_path'],
                                 file) for file in
                    os.listdir(self.testbed_params['golden_results_path'])
                ]
            else:
                self.log.warning('No golden files found.')
                self.golden_files_list = []
        self.testclass_results = []

        # Turn WiFi ON
        if self.testclass_params.get('airplane_mode', 1):
            for dev in self.android_devices:
                self.log.info('Turning on airplane mode.')
                asserts.assert_true(utils.force_airplane_mode(dev, True),
                                    'Can not turn on airplane mode.')
                wutils.reset_wifi(dev)
                wutils.wifi_toggle_state(dev, True)

    def teardown_test(self):
        self.iperf_server.stop()

    def teardown_class(self):
        # Turn WiFi OFF
        self.access_point.teardown()
        for dev in self.android_devices:
            wutils.wifi_toggle_state(dev, False)
            dev.go_to_sleep()
        self.process_testclass_results()

    def process_testclass_results(self):
        """Saves plot with all test results to enable comparison."""
        # Plot and save all results
        plots = collections.OrderedDict()
        for result in self.testclass_results:
            plot_id = (result['testcase_params']['channel'],
                       result['testcase_params']['mode'])
            if plot_id not in plots:
                plots[plot_id] = BokehFigure(
                    title='Channel {} {} ({})'.format(
                        result['testcase_params']['channel'],
                        result['testcase_params']['mode'],
                        result['testcase_params']['traffic_type']),
                    x_label='Attenuation (dB)',
                    primary_y_label='Throughput (Mbps)')
            plots[plot_id].add_line(result['total_attenuation'],
                                    result['throughput_receive'],
                                    result['test_name'].strip('test_rvr_'),
                                    hover_text=result['hover_text'],
                                    marker='circle')
            plots[plot_id].add_line(result['total_attenuation'],
                                    result['rx_phy_rate'],
                                    result['test_name'].strip('test_rvr_') +
                                    ' (Rx PHY)',
                                    hover_text=result['hover_text'],
                                    style='dashed',
                                    marker='inverted_triangle')
            plots[plot_id].add_line(result['total_attenuation'],
                                    result['tx_phy_rate'],
                                    result['test_name'].strip('test_rvr_') +
                                    ' (Tx PHY)',
                                    hover_text=result['hover_text'],
                                    style='dashed',
                                    marker='triangle')

        figure_list = []
        for plot_id, plot in plots.items():
            plot.generate_figure()
            figure_list.append(plot)
        output_file_path = os.path.join(self.log_path, 'results.html')
        BokehFigure.save_figures(figure_list, output_file_path)

    def pass_fail_check(self, rvr_result):
        """Check the test result and decide if it passed or failed.

        Checks the RvR test result and compares to a throughput limites for
        the same configuration. The pass/fail tolerances are provided in the
        config file.

        Args:
            rvr_result: dict containing attenuation, throughput and other data
        """
        try:
            throughput_limits = self.compute_throughput_limits(rvr_result)
        except:
            asserts.explicit_pass(
                'Test passed by default. Golden file not found')

        failure_count = 0
        for idx, current_throughput in enumerate(
                rvr_result['throughput_receive']):
            if (current_throughput < throughput_limits['lower_limit'][idx]
                    or current_throughput >
                    throughput_limits['upper_limit'][idx]):
                failure_count = failure_count + 1

        # Set test metrics
        rvr_result['metrics']['failure_count'] = failure_count
        if self.publish_testcase_metrics:
            self.testcase_metric_logger.add_metric('failure_count',
                                                   failure_count)

        # Assert pass or fail
        if failure_count >= self.testclass_params['failure_count_tolerance']:
            asserts.fail('Test failed. Found {} points outside limits.'.format(
                failure_count))
        asserts.explicit_pass(
            'Test passed. Found {} points outside throughput limits.'.format(
                failure_count))

    def compute_throughput_limits(self, rvr_result):
        """Compute throughput limits for current test.

        Checks the RvR test result and compares to a throughput limites for
        the same configuration. The pass/fail tolerances are provided in the
        config file.

        Args:
            rvr_result: dict containing attenuation, throughput and other meta
            data
        Returns:
            throughput_limits: dict containing attenuation and throughput limit data
        """
        test_name = self.current_test_name
        golden_path = next(file_name for file_name in self.golden_files_list
                           if test_name in file_name)
        with open(golden_path, 'r') as golden_file:
            golden_results = json.load(golden_file)
            golden_attenuation = [
                att + golden_results['fixed_attenuation']
                for att in golden_results['attenuation']
            ]
        attenuation = []
        lower_limit = []
        upper_limit = []
        for idx, current_throughput in enumerate(
                rvr_result['throughput_receive']):
            current_att = rvr_result['attenuation'][idx] + rvr_result[
                'fixed_attenuation']
            att_distances = [
                abs(current_att - golden_att)
                for golden_att in golden_attenuation
            ]
            sorted_distances = sorted(enumerate(att_distances),
                                      key=lambda x: x[1])
            closest_indeces = [dist[0] for dist in sorted_distances[0:3]]
            closest_throughputs = [
                golden_results['throughput_receive'][index]
                for index in closest_indeces
            ]
            closest_throughputs.sort()

            attenuation.append(current_att)
            lower_limit.append(
                max(
                    closest_throughputs[0] - max(
                        self.testclass_params['abs_tolerance'],
                        closest_throughputs[0] *
                        self.testclass_params['pct_tolerance'] / 100), 0))
            upper_limit.append(closest_throughputs[-1] + max(
                self.testclass_params['abs_tolerance'], closest_throughputs[-1]
                * self.testclass_params['pct_tolerance'] / 100))
        throughput_limits = {
            'attenuation': attenuation,
            'lower_limit': lower_limit,
            'upper_limit': upper_limit
        }
        return throughput_limits

    def plot_rvr_result(self, rvr_result):
        """Saves plots and JSON formatted results.

        Args:
            rvr_result: dict containing attenuation, throughput and other meta
            data
        """
        # Save output as text file
        results_file_path = os.path.join(
            self.log_path, '{}.json'.format(self.current_test_name))
        with open(results_file_path, 'w') as results_file:
            json.dump(wputils.serialize_dict(rvr_result),
                      results_file,
                      indent=4)
        # Plot and save
        figure = BokehFigure(title=self.current_test_name,
                             x_label='Attenuation (dB)',
                             primary_y_label='Throughput (Mbps)')
        try:
            golden_path = next(file_name
                               for file_name in self.golden_files_list
                               if self.current_test_name in file_name)
            with open(golden_path, 'r') as golden_file:
                golden_results = json.load(golden_file)
            golden_attenuation = [
                att + golden_results['fixed_attenuation']
                for att in golden_results['attenuation']
            ]
            throughput_limits = self.compute_throughput_limits(rvr_result)
            shaded_region = {
                'x_vector': throughput_limits['attenuation'],
                'lower_limit': throughput_limits['lower_limit'],
                'upper_limit': throughput_limits['upper_limit']
            }
            figure.add_line(golden_attenuation,
                            golden_results['throughput_receive'],
                            'Golden Results',
                            color='green',
                            marker='circle',
                            shaded_region=shaded_region)
        except:
            self.log.warning('ValueError: Golden file not found')

        # Generate graph annotatios
        rvr_result['hover_text'] = {
            'llstats': [
                'TX MCS = {0} ({1:.1f}%). RX MCS = {2} ({3:.1f}%)'.format(
                    curr_llstats['summary']['common_tx_mcs'],
                    curr_llstats['summary']['common_tx_mcs_freq'] * 100,
                    curr_llstats['summary']['common_rx_mcs'],
                    curr_llstats['summary']['common_rx_mcs_freq'] * 100)
                for curr_llstats in rvr_result['llstats']
            ],
            'rssi': [
                '{0:.2f} [{1:.2f},{2:.2f}]'.format(
                    rssi['signal_poll_rssi'],
                    rssi['chain_0_rssi'],
                    rssi['chain_1_rssi'],
                ) for rssi in rvr_result['rssi']
            ]
        }

        figure.add_line(rvr_result['total_attenuation'],
                        rvr_result['throughput_receive'],
                        'Measured Throughput',
                        hover_text=rvr_result['hover_text'],
                        color='black',
                        marker='circle')
        figure.add_line(
            rvr_result['total_attenuation'][0:len(rvr_result['rx_phy_rate'])],
            rvr_result['rx_phy_rate'],
            'Rx PHY Rate',
            hover_text=rvr_result['hover_text'],
            color='blue',
            style='dashed',
            marker='inverted_triangle')
        figure.add_line(
            rvr_result['total_attenuation'][0:len(rvr_result['rx_phy_rate'])],
            rvr_result['tx_phy_rate'],
            'Tx PHY Rate',
            hover_text=rvr_result['hover_text'],
            color='red',
            style='dashed',
            marker='triangle')

        output_file_path = os.path.join(
            self.log_path, '{}.html'.format(self.current_test_name))
        figure.generate_figure(output_file_path)

    def compute_test_metrics(self, rvr_result):
        # Set test metrics
        rvr_result['metrics'] = {}
        rvr_result['metrics']['peak_tput'] = max(
            rvr_result['throughput_receive'])
        if self.publish_testcase_metrics:
            self.testcase_metric_logger.add_metric(
                'peak_tput', rvr_result['metrics']['peak_tput'])

        test_mode = rvr_result['ap_settings'][rvr_result['testcase_params']
                                              ['band']]['bandwidth']
        tput_below_limit = [
            tput <
            self.testclass_params['tput_metric_targets'][test_mode]['high']
            for tput in rvr_result['throughput_receive']
        ]
        rvr_result['metrics']['high_tput_range'] = -1
        for idx in range(len(tput_below_limit)):
            if all(tput_below_limit[idx:]):
                if idx == 0:
                    # Throughput was never above limit
                    rvr_result['metrics']['high_tput_range'] = -1
                else:
                    rvr_result['metrics']['high_tput_range'] = rvr_result[
                        'total_attenuation'][max(idx, 1) - 1]
                break
        if self.publish_testcase_metrics:
            self.testcase_metric_logger.add_metric(
                'high_tput_range', rvr_result['metrics']['high_tput_range'])

        tput_below_limit = [
            tput <
            self.testclass_params['tput_metric_targets'][test_mode]['low']
            for tput in rvr_result['throughput_receive']
        ]
        for idx in range(len(tput_below_limit)):
            if all(tput_below_limit[idx:]):
                rvr_result['metrics']['low_tput_range'] = rvr_result[
                    'total_attenuation'][max(idx, 1) - 1]
                break
        else:
            rvr_result['metrics']['low_tput_range'] = -1
        if self.publish_testcase_metrics:
            self.testcase_metric_logger.add_metric(
                'low_tput_range', rvr_result['metrics']['low_tput_range'])

    def process_test_results(self, rvr_result):
        self.plot_rvr_result(rvr_result)
        self.compute_test_metrics(rvr_result)

    def run_rvr_test(self, testcase_params):
        """Test function to run RvR.

        The function runs an RvR test in the current device/AP configuration.
        Function is called from another wrapper function that sets up the
        testbed for the RvR test

        Args:
            testcase_params: dict containing test-specific parameters
        Returns:
            rvr_result: dict containing rvr_results and meta data
        """
        self.log.info('Start running RvR')
        # Refresh link layer stats before test
        llstats_obj = wputils.LinkLayerStats(
            self.monitored_dut,
            self.testclass_params.get('monitor_llstats', 1))
        zero_counter = 0
        throughput = []
        rx_phy_rate = []
        tx_phy_rate = []
        llstats = []
        rssi = []
        for atten in testcase_params['atten_range']:
            for dev in self.android_devices:
                if not wputils.health_check(dev, 5, 50):
                    asserts.skip('DUT health check failed. Skipping test.')
            # Set Attenuation
            for attenuator in self.attenuators:
                attenuator.set_atten(atten, strict=False, retry=True)
            # Refresh link layer stats
            llstats_obj.update_stats()
            # Setup sniffer
            if self.testbed_params['sniffer_enable']:
                self.sniffer.start_capture(
                    network=testcase_params['test_network'],
                    chan=testcase_params['channel'],
                    bw=testcase_params['bandwidth'],
                    duration=self.testclass_params['iperf_duration'] / 5)
            # Start iperf session
            if self.testclass_params.get('monitor_rssi', 1):
                rssi_future = wputils.get_connected_rssi_nb(
                    self.monitored_dut,
                    self.testclass_params['iperf_duration'] - 1,
                    1,
                    1,
                    interface=self.monitored_interface)
            self.iperf_server.start(tag=str(atten))
            client_output_path = self.iperf_client.start(
                testcase_params['iperf_server_address'],
                testcase_params['iperf_args'], str(atten),
                self.testclass_params['iperf_duration'] + self.TEST_TIMEOUT)
            server_output_path = self.iperf_server.stop()
            if self.testclass_params.get('monitor_rssi', 1):
                rssi_result = rssi_future.result()
                current_rssi = {
                    'signal_poll_rssi':
                    rssi_result['signal_poll_rssi']['mean'],
                    'chain_0_rssi': rssi_result['chain_0_rssi']['mean'],
                    'chain_1_rssi': rssi_result['chain_1_rssi']['mean']
                }
            else:
                current_rssi = {
                    'signal_poll_rssi': float('nan'),
                    'chain_0_rssi': float('nan'),
                    'chain_1_rssi': float('nan')
                }
            rssi.append(current_rssi)
            # Stop sniffer
            if self.testbed_params['sniffer_enable']:
                self.sniffer.stop_capture(tag=str(atten))
            # Parse and log result
            if testcase_params['use_client_output']:
                iperf_file = client_output_path
            else:
                iperf_file = server_output_path
            try:
                iperf_result = ipf.IPerfResult(iperf_file)
                curr_throughput = numpy.mean(iperf_result.instantaneous_rates[
                    self.testclass_params['iperf_ignored_interval']:-1]
                                             ) * 8 * (1.024**2)
            except:
                self.log.warning(
                    'ValueError: Cannot get iperf result. Setting to 0')
                curr_throughput = 0
            throughput.append(curr_throughput)
            llstats_obj.update_stats()
            curr_llstats = llstats_obj.llstats_incremental.copy()
            llstats.append(curr_llstats)
            rx_phy_rate.append(curr_llstats['summary'].get(
                'mean_rx_phy_rate', 0))
            tx_phy_rate.append(curr_llstats['summary'].get(
                'mean_tx_phy_rate', 0))
            self.log.info(
                ('Throughput at {0:.2f} dB is {1:.2f} Mbps. '
                 'RSSI = {2:.2f} [{3:.2f}, {4:.2f}].').format(
                     atten, curr_throughput, current_rssi['signal_poll_rssi'],
                     current_rssi['chain_0_rssi'],
                     current_rssi['chain_1_rssi']))
            if curr_throughput == 0:
                zero_counter = zero_counter + 1
            else:
                zero_counter = 0
            if zero_counter == self.MAX_CONSECUTIVE_ZEROS:
                self.log.info(
                    'Throughput stable at 0 Mbps. Stopping test now.')
                zero_padding = len(
                    testcase_params['atten_range']) - len(throughput)
                throughput.extend([0] * zero_padding)
                rx_phy_rate.extend([0] * zero_padding)
                tx_phy_rate.extend([0] * zero_padding)
                break
        for attenuator in self.attenuators:
            attenuator.set_atten(0, strict=False, retry=True)
        # Compile test result and meta data
        rvr_result = collections.OrderedDict()
        rvr_result['test_name'] = self.current_test_name
        rvr_result['testcase_params'] = testcase_params.copy()
        rvr_result['ap_settings'] = self.access_point.ap_settings.copy()
        rvr_result['fixed_attenuation'] = self.testbed_params[
            'fixed_attenuation'][str(testcase_params['channel'])]
        rvr_result['attenuation'] = list(testcase_params['atten_range'])
        rvr_result['total_attenuation'] = [
            att + rvr_result['fixed_attenuation']
            for att in rvr_result['attenuation']
        ]
        rvr_result['rssi'] = rssi
        rvr_result['throughput_receive'] = throughput
        rvr_result['rx_phy_rate'] = rx_phy_rate
        rvr_result['tx_phy_rate'] = tx_phy_rate
        rvr_result['llstats'] = llstats
        return rvr_result

    def setup_ap(self, testcase_params):
        """Sets up the access point in the configuration required by the test.

        Args:
            testcase_params: dict containing AP and other test params
        """
        band = self.access_point.band_lookup_by_channel(
            testcase_params['channel'])
        if '6G' in band:
            frequency = wutils.WifiEnums.channel_6G_to_freq[int(
                testcase_params['channel'].strip('6g'))]
        else:
            if testcase_params['channel'] < 13:
                frequency = wutils.WifiEnums.channel_2G_to_freq[
                    testcase_params['channel']]
            else:
                frequency = wutils.WifiEnums.channel_5G_to_freq[
                    testcase_params['channel']]
        if frequency in wutils.WifiEnums.DFS_5G_FREQUENCIES:
            self.access_point.set_region(self.testbed_params['DFS_region'])
        else:
            self.access_point.set_region(self.testbed_params['default_region'])
        self.access_point.set_channel_and_bandwidth(testcase_params['band'],
                                                    testcase_params['channel'],
                                                    testcase_params['mode'])
        self.log.info('Access Point Configuration: {}'.format(
            self.access_point.ap_settings))

    def setup_dut(self, testcase_params):
        """Sets up the DUT in the configuration required by the test.

        Args:
            testcase_params: dict containing AP and other test params
        """
        # Turn screen off to preserve battery
        if self.testbed_params.get('screen_on',
                                   False) or self.testclass_params.get(
                                       'screen_on', False):
            self.sta_dut.droid.wakeLockAcquireDim()
        else:
            self.sta_dut.go_to_sleep()
        if (wputils.validate_network(self.sta_dut,
                                     testcase_params['test_network']['SSID'])
                and not self.testclass_params.get('force_reconnect', 0)):
            self.log.info('Already connected to desired network')
        else:
            wutils.wifi_toggle_state(self.sta_dut, False)
            wutils.set_wifi_country_code(self.sta_dut,
                                         self.testclass_params['country_code'])
            wutils.wifi_toggle_state(self.sta_dut, True)
            wutils.reset_wifi(self.sta_dut)
            if self.testbed_params.get('txbf_off', False):
                wputils.disable_beamforming(self.sta_dut)
            wutils.set_wifi_country_code(self.sta_dut,
                                         self.testclass_params['country_code'])
            if self.testbed_params['sniffer_enable']:
                self.sniffer.start_capture(
                    network={'SSID': testcase_params['test_network']['SSID']},
                    chan=testcase_params['channel'],
                    bw=testcase_params['bandwidth'],
                    duration=180)
            try:
                wutils.wifi_connect(self.sta_dut,
                                    testcase_params['test_network'],
                                    num_of_tries=5,
                                    check_connectivity=True)
                if self.testclass_params.get('num_streams', 2) == 1:
                    wputils.set_nss_capability(self.sta_dut, 1)
            finally:
                if self.testbed_params['sniffer_enable']:
                    self.sniffer.stop_capture(tag='connection_setup')

    def setup_rvr_test(self, testcase_params):
        """Function that gets devices ready for the test.

        Args:
            testcase_params: dict containing test-specific parameters
        """
        # Configure AP
        self.setup_ap(testcase_params)
        # Set attenuator to 0 dB
        for attenuator in self.attenuators:
            attenuator.set_atten(0, strict=False, retry=True)
        # Reset, configure, and connect DUT
        self.setup_dut(testcase_params)
        # Wait before running the first wifi test
        first_test_delay = self.testclass_params.get('first_test_delay', 600)
        if first_test_delay > 0 and len(self.testclass_results) == 0:
            self.log.info('Waiting before the first RvR test.')
            time.sleep(first_test_delay)
            self.setup_dut(testcase_params)
        # Get iperf_server address
        sta_dut_ip = self.sta_dut.droid.connectivityGetIPv4Addresses(
            'wlan0')[0]
        if isinstance(self.iperf_server, ipf.IPerfServerOverAdb):
            testcase_params['iperf_server_address'] = sta_dut_ip
        else:
            if self.testbed_params.get('lan_traffic_only', True):
                testcase_params[
                    'iperf_server_address'] = wputils.get_server_address(
                        self.remote_server, sta_dut_ip, '255.255.255.0')
            else:
                testcase_params[
                    'iperf_server_address'] = wputils.get_server_address(
                        self.remote_server, sta_dut_ip, 'public')
        # Set DUT to monitor RSSI and LLStats on
        self.monitored_dut = self.sta_dut
        self.monitored_interface = 'wlan0'

    def compile_test_params(self, testcase_params):
        """Function that completes all test params based on the test name.

        Args:
            testcase_params: dict containing test-specific parameters
        """
        # Check if test should be skipped based on parameters.
        wputils.check_skip_conditions(testcase_params, self.sta_dut,
                                      self.access_point,
                                      getattr(self, 'ota_chamber', None))

        band = wputils.CHANNEL_TO_BAND_MAP[testcase_params['channel']]
        start_atten = self.testclass_params['atten_start'].get(band, 0)
        num_atten_steps = int(
            (self.testclass_params['atten_stop'] - start_atten) /
            self.testclass_params['atten_step'])
        testcase_params['atten_range'] = [
            start_atten + x * self.testclass_params['atten_step']
            for x in range(0, num_atten_steps)
        ]
        band = self.access_point.band_lookup_by_channel(
            testcase_params['channel'])
        testcase_params['band'] = band
        testcase_params['test_network'] = self.main_network[band]
        if testcase_params['traffic_type'] == 'TCP':
            testcase_params['iperf_socket_size'] = self.testclass_params.get(
                'tcp_socket_size', None)
            testcase_params['iperf_processes'] = self.testclass_params.get(
                'tcp_processes', 1)
        elif testcase_params['traffic_type'] == 'UDP':
            testcase_params['iperf_socket_size'] = self.testclass_params.get(
                'udp_socket_size', None)
            testcase_params['iperf_processes'] = self.testclass_params.get(
                'udp_processes', 1)
        if (testcase_params['traffic_direction'] == 'DL'
                and not isinstance(self.iperf_server, ipf.IPerfServerOverAdb)
            ) or (testcase_params['traffic_direction'] == 'UL'
                  and isinstance(self.iperf_server, ipf.IPerfServerOverAdb)):
            testcase_params['iperf_args'] = wputils.get_iperf_arg_string(
                duration=self.testclass_params['iperf_duration'],
                reverse_direction=1,
                traffic_type=testcase_params['traffic_type'],
                socket_size=testcase_params['iperf_socket_size'],
                num_processes=testcase_params['iperf_processes'],
                udp_throughput=self.testclass_params['UDP_rates'][
                    testcase_params['mode']])
            testcase_params['use_client_output'] = True
        else:
            testcase_params['iperf_args'] = wputils.get_iperf_arg_string(
                duration=self.testclass_params['iperf_duration'],
                reverse_direction=0,
                traffic_type=testcase_params['traffic_type'],
                socket_size=testcase_params['iperf_socket_size'],
                num_processes=testcase_params['iperf_processes'],
                udp_throughput=self.testclass_params['UDP_rates'][
                    testcase_params['mode']])
            testcase_params['use_client_output'] = False
        return testcase_params

    def _test_rvr(self, testcase_params):
        """ Function that gets called for each test case

        Args:
            testcase_params: dict containing test-specific parameters
        """
        # Compile test parameters from config and test name
        testcase_params = self.compile_test_params(testcase_params)

        # Prepare devices and run test
        self.setup_rvr_test(testcase_params)
        rvr_result = self.run_rvr_test(testcase_params)

        # Post-process results
        self.testclass_results.append(rvr_result)
        self.process_test_results(rvr_result)
        self.pass_fail_check(rvr_result)

    def generate_test_cases(self, channels, modes, traffic_types,
                            traffic_directions):
        """Function that auto-generates test cases for a test class."""
        test_cases = []
        allowed_configs = {
            20: [
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 36, 40, 44, 48, 64, 100,
                116, 132, 140, 149, 153, 157, 161, '6g37', '6g117', '6g213'
            ],
            40: [36, 44, 100, 149, 157, '6g37', '6g117', '6g213'],
            80: [36, 100, 149, '6g37', '6g117', '6g213'],
            160: [36, '6g37', '6g117', '6g213']
        }

        for channel, mode, traffic_type, traffic_direction in itertools.product(
                channels, modes, traffic_types, traffic_directions):
            bandwidth = int(''.join([x for x in mode if x.isdigit()]))
            if channel not in allowed_configs[bandwidth]:
                continue
            test_name = 'test_rvr_{}_{}_ch{}_{}'.format(
                traffic_type, traffic_direction, channel, mode)
            test_params = collections.OrderedDict(
                channel=channel,
                mode=mode,
                bandwidth=bandwidth,
                traffic_type=traffic_type,
                traffic_direction=traffic_direction)
            setattr(self, test_name, partial(self._test_rvr, test_params))
            test_cases.append(test_name)
        return test_cases


class WifiRvr_TCP_Test(WifiRvrTest):

    def __init__(self, controllers):
        super().__init__(controllers)
        self.tests = self.generate_test_cases(
            channels=[
                1, 6, 11, 36, 40, 44, 48, 149, 153, 157, 161, '6g37', '6g117',
                '6g213'
            ],
            modes=['bw20', 'bw40', 'bw80', 'bw160'],
            traffic_types=['TCP'],
            traffic_directions=['DL', 'UL'])


class WifiRvr_VHT_TCP_Test(WifiRvrTest):

    def __init__(self, controllers):
        super().__init__(controllers)
        self.tests = self.generate_test_cases(
            channels=[1, 6, 11, 36, 40, 44, 48, 149, 153, 157, 161],
            modes=['VHT20', 'VHT40', 'VHT80'],
            traffic_types=['TCP'],
            traffic_directions=['DL', 'UL'])


class WifiRvr_HE_TCP_Test(WifiRvrTest):

    def __init__(self, controllers):
        super().__init__(controllers)
        self.tests = self.generate_test_cases(
            channels=[
                1, 6, 11, 36, 40, 44, 48, 149, 153, 157, 161, '6g37', '6g117',
                '6g213'
            ],
            modes=['HE20', 'HE40', 'HE80', 'HE160'],
            traffic_types=['TCP'],
            traffic_directions=['DL', 'UL'])


class WifiRvr_SampleUDP_Test(WifiRvrTest):

    def __init__(self, controllers):
        super().__init__(controllers)
        self.tests = self.generate_test_cases(
            channels=[6, 36, 149, '6g37'],
            modes=['bw20', 'bw40', 'bw80', 'bw160'],
            traffic_types=['UDP'],
            traffic_directions=['DL', 'UL'])


class WifiRvr_VHT_SampleUDP_Test(WifiRvrTest):

    def __init__(self, controllers):
        super().__init__(controllers)
        self.tests = self.generate_test_cases(
            channels=[6, 36, 149],
            modes=['VHT20', 'VHT40', 'VHT80', 'VHT160'],
            traffic_types=['UDP'],
            traffic_directions=['DL', 'UL'])


class WifiRvr_HE_SampleUDP_Test(WifiRvrTest):

    def __init__(self, controllers):
        super().__init__(controllers)
        self.tests = self.generate_test_cases(
            channels=[6, 36, 149],
            modes=['HE20', 'HE40', 'HE80', 'HE160', '6g37'],
            traffic_types=['UDP'],
            traffic_directions=['DL', 'UL'])


class WifiRvr_SampleDFS_Test(WifiRvrTest):

    def __init__(self, controllers):
        super().__init__(controllers)
        self.tests = self.generate_test_cases(
            channels=[64, 100, 116, 132, 140],
            modes=['bw20', 'bw40', 'bw80'],
            traffic_types=['TCP'],
            traffic_directions=['DL', 'UL'])


class WifiRvr_SingleChain_TCP_Test(WifiRvrTest):

    def __init__(self, controllers):
        super().__init__(controllers)
        self.tests = self.generate_test_cases(
            channels=[
                1, 6, 11, 36, 40, 44, 48, 149, 153, 157, 161, '6g37', '6g117',
                '6g213'
            ],
            modes=['bw20', 'bw40', 'bw80', 'bw160'],
            traffic_types=['TCP'],
            traffic_directions=['DL', 'UL'],
            chains=[0, 1, '2x2'])

    def setup_dut(self, testcase_params):
        self.sta_dut = self.android_devices[0]
        wputils.set_chain_mask(self.sta_dut, testcase_params['chain'])
        WifiRvrTest.setup_dut(self, testcase_params)

    def generate_test_cases(self, channels, modes, traffic_types,
                            traffic_directions, chains):
        """Function that auto-generates test cases for a test class."""
        test_cases = []
        allowed_configs = {
            20: [
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 36, 40, 44, 48, 64, 100,
                116, 132, 140, 149, 153, 157, 161, '6g37', '6g117', '6g213'
            ],
            40: [36, 44, 100, 149, 157, '6g37', '6g117', '6g213'],
            80: [36, 100, 149, '6g37', '6g117', '6g213'],
            160: [36, '6g37', '6g117', '6g213']
        }

        for channel, mode, chain, traffic_type, traffic_direction in itertools.product(
                channels, modes, chains, traffic_types, traffic_directions):
            bandwidth = int(''.join([x for x in mode if x.isdigit()]))
            if channel not in allowed_configs[bandwidth]:
                continue
            test_name = 'test_rvr_{}_{}_ch{}_{}_ch{}'.format(
                traffic_type, traffic_direction, channel, mode, chain)
            test_params = collections.OrderedDict(
                channel=channel,
                mode=mode,
                bandwidth=bandwidth,
                traffic_type=traffic_type,
                traffic_direction=traffic_direction,
                chain=chain)
            setattr(self, test_name, partial(self._test_rvr, test_params))
            test_cases.append(test_name)
        return test_cases


# Over-the air version of RVR tests
class WifiOtaRvrTest(WifiRvrTest):
    """Class to test over-the-air RvR

    This class implements measures WiFi RvR tests in an OTA chamber. It enables
    setting turntable orientation and other chamber parameters to study
    performance in varying channel conditions
    """

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = False

    def setup_class(self):
        WifiRvrTest.setup_class(self)
        self.ota_chamber = ota_chamber.create(
            self.user_params['OTAChamber'])[0]

    def teardown_class(self):
        WifiRvrTest.teardown_class(self)
        self.ota_chamber.reset_chamber()

    def extract_test_id(self, testcase_params, id_fields):
        test_id = collections.OrderedDict(
            (param, testcase_params.get(param, None)) for param in id_fields)
        return test_id

    def process_testclass_results(self):
        """Saves plot with all test results to enable comparison."""
        # Plot individual test id results raw data and compile metrics
        plots = collections.OrderedDict()
        compiled_data = collections.OrderedDict()
        for result in self.testclass_results:
            test_id = tuple(
                self.extract_test_id(result['testcase_params'], [
                    'channel', 'mode', 'traffic_type', 'traffic_direction',
                    'chain'
                ]).items())
            if test_id not in plots:
                # Initialize test id data when not present
                compiled_data[test_id] = {
                    'throughput': [],
                    'rx_phy_rate': [],
                    'tx_phy_rate': [],
                    'metrics': {}
                }
                compiled_data[test_id]['metrics'] = {
                    key: []
                    for key in result['metrics'].keys()
                }
                plots[test_id] = BokehFigure(
                    title='Channel {} {} ({} {})'.format(
                        result['testcase_params']['channel'],
                        result['testcase_params']['mode'],
                        result['testcase_params']['traffic_type'],
                        result['testcase_params']['traffic_direction']),
                    x_label='Attenuation (dB)',
                    primary_y_label='Throughput (Mbps)')
                test_id_phy = test_id + tuple('PHY')
                plots[test_id_phy] = BokehFigure(
                    title='Channel {} {} ({} {}) (PHY Rate)'.format(
                        result['testcase_params']['channel'],
                        result['testcase_params']['mode'],
                        result['testcase_params']['traffic_type'],
                        result['testcase_params']['traffic_direction']),
                    x_label='Attenuation (dB)',
                    primary_y_label='PHY Rate (Mbps)')
            # Compile test id data and metrics
            compiled_data[test_id]['throughput'].append(
                result['throughput_receive'])
            compiled_data[test_id]['rx_phy_rate'].append(result['rx_phy_rate'])
            compiled_data[test_id]['tx_phy_rate'].append(result['tx_phy_rate'])
            compiled_data[test_id]['total_attenuation'] = result[
                'total_attenuation']
            for metric_key, metric_value in result['metrics'].items():
                compiled_data[test_id]['metrics'][metric_key].append(
                    metric_value)
            # Add test id to plots
            plots[test_id].add_line(result['total_attenuation'],
                                    result['throughput_receive'],
                                    result['test_name'].strip('test_rvr_'),
                                    hover_text=result['hover_text'],
                                    width=1,
                                    style='dashed',
                                    marker='circle')
            plots[test_id_phy].add_line(
                result['total_attenuation'],
                result['rx_phy_rate'],
                result['test_name'].strip('test_rvr_') + ' Rx PHY Rate',
                hover_text=result['hover_text'],
                width=1,
                style='dashed',
                marker='inverted_triangle')
            plots[test_id_phy].add_line(
                result['total_attenuation'],
                result['tx_phy_rate'],
                result['test_name'].strip('test_rvr_') + ' Tx PHY Rate',
                hover_text=result['hover_text'],
                width=1,
                style='dashed',
                marker='triangle')

        # Compute average RvRs and compute metrics over orientations
        for test_id, test_data in compiled_data.items():
            test_id_dict = dict(test_id)
            metric_tag = '{}_{}_ch{}_{}'.format(
                test_id_dict['traffic_type'],
                test_id_dict['traffic_direction'], test_id_dict['channel'],
                test_id_dict['mode'])
            high_tput_hit_freq = numpy.mean(
                numpy.not_equal(test_data['metrics']['high_tput_range'], -1))
            self.testclass_metric_logger.add_metric(
                '{}.high_tput_hit_freq'.format(metric_tag), high_tput_hit_freq)
            for metric_key, metric_value in test_data['metrics'].items():
                metric_key = '{}.avg_{}'.format(metric_tag, metric_key)
                metric_value = numpy.mean(metric_value)
                self.testclass_metric_logger.add_metric(
                    metric_key, metric_value)
            test_data['avg_rvr'] = numpy.mean(test_data['throughput'], 0)
            test_data['median_rvr'] = numpy.median(test_data['throughput'], 0)
            test_data['avg_rx_phy_rate'] = numpy.mean(test_data['rx_phy_rate'],
                                                      0)
            test_data['avg_tx_phy_rate'] = numpy.mean(test_data['tx_phy_rate'],
                                                      0)
            plots[test_id].add_line(test_data['total_attenuation'],
                                    test_data['avg_rvr'],
                                    legend='Average Throughput',
                                    marker='circle')
            plots[test_id].add_line(test_data['total_attenuation'],
                                    test_data['median_rvr'],
                                    legend='Median Throughput',
                                    marker='square')
            test_id_phy = test_id + tuple('PHY')
            plots[test_id_phy].add_line(test_data['total_attenuation'],
                                        test_data['avg_rx_phy_rate'],
                                        legend='Average Rx Rate',
                                        marker='inverted_triangle')
            plots[test_id_phy].add_line(test_data['total_attenuation'],
                                        test_data['avg_tx_phy_rate'],
                                        legend='Average Tx Rate',
                                        marker='triangle')

        figure_list = []
        for plot_id, plot in plots.items():
            plot.generate_figure()
            figure_list.append(plot)
        output_file_path = os.path.join(self.log_path, 'results.html')
        BokehFigure.save_figures(figure_list, output_file_path)

    def setup_rvr_test(self, testcase_params):
        # Continue test setup
        WifiRvrTest.setup_rvr_test(self, testcase_params)
        # Set turntable orientation
        self.ota_chamber.set_orientation(testcase_params['orientation'])

    def generate_test_cases(self, channels, modes, angles, traffic_types,
                            directions):
        test_cases = []
        allowed_configs = {
            20: [
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 36, 40, 44, 48, 64, 100,
                116, 132, 140, 149, 153, 157, 161
            ],
            40: [36, 44, 100, 149, 157],
            80: [36, 100, 149],
            160: [36, '6g37', '6g117', '6g213']
        }
        for channel, mode, angle, traffic_type, direction in itertools.product(
                channels, modes, angles, traffic_types, directions):
            bandwidth = int(''.join([x for x in mode if x.isdigit()]))
            if channel not in allowed_configs[bandwidth]:
                continue
            testcase_name = 'test_rvr_{}_{}_ch{}_{}_{}deg'.format(
                traffic_type, direction, channel, mode, angle)
            test_params = collections.OrderedDict(channel=channel,
                                                  mode=mode,
                                                  bandwidth=bandwidth,
                                                  traffic_type=traffic_type,
                                                  traffic_direction=direction,
                                                  orientation=angle)
            setattr(self, testcase_name, partial(self._test_rvr, test_params))
            test_cases.append(testcase_name)
        return test_cases


class WifiOtaRvr_StandardOrientation_Test(WifiOtaRvrTest):

    def __init__(self, controllers):
        WifiOtaRvrTest.__init__(self, controllers)
        self.tests = self.generate_test_cases(
            [1, 6, 11, 36, 40, 44, 48, 149, 153, 157, 161, '6g37'],
            ['bw20', 'bw40', 'bw80', 'bw160'], list(range(0, 360, 45)),
            ['TCP'], ['DL', 'UL'])


class WifiOtaRvr_SampleChannel_Test(WifiOtaRvrTest):

    def __init__(self, controllers):
        WifiOtaRvrTest.__init__(self, controllers)
        self.tests = self.generate_test_cases([6], ['bw20'],
                                              list(range(0, 360, 45)), ['TCP'],
                                              ['DL'])
        self.tests.extend(
            self.generate_test_cases([36, 149], ['bw80', 'bw160'],
                                     list(range(0, 360, 45)), ['TCP'], ['DL']))
        self.tests.extend(
            self.generate_test_cases(['6g37'], ['bw160'],
                                     list(range(0, 360, 45)), ['TCP'], ['DL']))

class WifiOtaRvr_SampleChannel_UDP_Test(WifiOtaRvrTest):

    def __init__(self, controllers):
        WifiOtaRvrTest.__init__(self, controllers)
        self.tests = self.generate_test_cases([6], ['bw20'],
                                              list(range(0, 360, 45)), ['UDP'],
                                              ['DL', 'UL'])
        self.tests.extend(
            self.generate_test_cases([36, 149], ['bw80', 'bw160'],
                                     list(range(0, 360, 45)), ['UDP'], ['DL', 'UL']))
        self.tests.extend(
            self.generate_test_cases(['6g37'], ['bw160'],
                                     list(range(0, 360, 45)), ['UDP'], ['DL', 'UL']))

class WifiOtaRvr_SingleOrientation_Test(WifiOtaRvrTest):

    def __init__(self, controllers):
        WifiOtaRvrTest.__init__(self, controllers)
        self.tests = self.generate_test_cases(
            [6, 36, 40, 44, 48, 149, 153, 157, 161, '6g37'],
            ['bw20', 'bw40', 'bw80', 'bw160'], [0], ['TCP'], ['DL', 'UL'])


class WifiOtaRvr_SingleChain_Test(WifiOtaRvrTest):

    def __init__(self, controllers):
        WifiOtaRvrTest.__init__(self, controllers)
        self.tests = self.generate_test_cases([6], ['bw20'],
                                              list(range(0, 360, 45)), ['TCP'],
                                              ['DL', 'UL'], [0, 1])
        self.tests.extend(
            self.generate_test_cases([36, 149], ['bw20', 'bw80', 'bw160'],
                                     list(range(0, 360, 45)), ['TCP'],
                                     ['DL', 'UL'], [0, 1, '2x2']))
        self.tests.extend(
            self.generate_test_cases(['6g37'], ['bw20', 'bw80', 'bw160'],
                                     list(range(0, 360, 45)), ['TCP'],
                                     ['DL', 'UL'], [0, 1, '2x2']))

    def setup_dut(self, testcase_params):
        self.sta_dut = self.android_devices[0]
        wputils.set_chain_mask(self.sta_dut, testcase_params['chain'])
        WifiRvrTest.setup_dut(self, testcase_params)

    def generate_test_cases(self, channels, modes, angles, traffic_types,
                            directions, chains):
        test_cases = []
        allowed_configs = {
            20: [
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 36, 40, 44, 48, 64, 100,
                116, 132, 140, 149, 153, 157, 161
            ],
            40: [36, 44, 100, 149, 157],
            80: [36, 100, 149],
            160: [36, '6g37', '6g117', '6g213']
        }
        for channel, mode, chain, angle, traffic_type, direction in itertools.product(
                channels, modes, chains, angles, traffic_types, directions):
            bandwidth = int(''.join([x for x in mode if x.isdigit()]))
            if channel not in allowed_configs[bandwidth]:
                continue
            testcase_name = 'test_rvr_{}_{}_ch{}_{}_ch{}_{}deg'.format(
                traffic_type, direction, channel, mode, chain, angle)
            test_params = collections.OrderedDict(channel=channel,
                                                  mode=mode,
                                                  bandwidth=bandwidth,
                                                  chain=chain,
                                                  traffic_type=traffic_type,
                                                  traffic_direction=direction,
                                                  orientation=angle)
            setattr(self, testcase_name, partial(self._test_rvr, test_params))
            test_cases.append(testcase_name)
        return test_cases
