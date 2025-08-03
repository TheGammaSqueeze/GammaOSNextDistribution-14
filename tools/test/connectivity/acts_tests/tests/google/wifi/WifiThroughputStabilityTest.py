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
from acts import context
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

TEST_TIMEOUT = 10
SHORT_SLEEP = 1
MED_SLEEP = 6


class WifiThroughputStabilityTest(base_test.BaseTestClass):
    """Class to test WiFi throughput stability.

    This class tests throughput stability and identifies cases where throughput
    fluctuates over time. The class setups up the AP, configures and connects
    the phone, and runs iperf throughput test at several attenuations For an
    example config file to run this test class see
    example_connectivity_performance_ap_sta.json.
    """

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        # Define metrics to be uploaded to BlackBox
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        # Generate test cases
        self.tests = self.generate_test_cases(
            [6, 36, 149, '6g37'], ['bw20', 'bw40', 'bw80', 'bw160'],
            ['TCP', 'UDP'], ['DL', 'UL'], ['high', 'low'])

    def generate_test_cases(self, channels, modes, traffic_types,
                            traffic_directions, signal_levels):
        """Function that auto-generates test cases for a test class."""
        allowed_configs = {
            20: [
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 36, 40, 44, 48, 64, 100,
                116, 132, 140, 149, 153, 157, 161, '6g37', '6g117', '6g213'
            ],
            40: [36, 44, 100, 149, 157, '6g37', '6g117', '6g213'],
            80: [36, 100, 149, '6g37', '6g117', '6g213'],
            160: [36, '6g37', '6g117', '6g213']
        }

        test_cases = []
        for channel, mode, signal_level, traffic_type, traffic_direction in itertools.product(
                channels,
                modes,
                signal_levels,
                traffic_types,
                traffic_directions,
        ):
            bandwidth = int(''.join([x for x in mode if x.isdigit()]))
            if channel not in allowed_configs[bandwidth]:
                continue
            testcase_params = collections.OrderedDict(
                channel=channel,
                mode=mode,
                bandwidth=bandwidth,
                traffic_type=traffic_type,
                traffic_direction=traffic_direction,
                signal_level=signal_level)
            testcase_name = ('test_tput_stability'
                             '_{}_{}_{}_ch{}_{}'.format(
                                 signal_level, traffic_type, traffic_direction,
                                 channel, mode))
            setattr(self, testcase_name,
                    partial(self._test_throughput_stability, testcase_params))
            test_cases.append(testcase_name)
        return test_cases

    def setup_class(self):
        self.dut = self.android_devices[0]
        req_params = [
            'throughput_stability_test_params', 'testbed_params',
            'main_network', 'RetailAccessPoints', 'RemoteServer'
        ]
        opt_params = ['OTASniffer']
        self.unpack_userparams(req_params, opt_params)
        self.testclass_params = self.throughput_stability_test_params
        self.num_atten = self.attenuators[0].instrument.num_atten
        self.remote_server = ssh.connection.SshConnection(
            ssh.settings.from_config(self.RemoteServer[0]['ssh_config']))
        self.iperf_server = self.iperf_servers[0]
        self.iperf_client = self.iperf_clients[0]
        self.access_point = retail_ap.create(self.RetailAccessPoints)[0]
        if hasattr(self,
                   'OTASniffer') and self.testbed_params['sniffer_enable']:
            try:
                self.sniffer = ota_sniffer.create(self.OTASniffer)[0]
            except:
                self.log.warning('Could not start sniffer. Disabling sniffs.')
                self.testbed_params['sniffer_enable'] = 0
        self.sniffer_subsampling = 1
        self.log_path = os.path.join(logging.log_path, 'test_results')
        os.makedirs(self.log_path, exist_ok=True)
        self.log.info('Access Point Configuration: {}'.format(
            self.access_point.ap_settings))
        self.ref_attenuations = {}
        self.testclass_results = []

        # Turn WiFi ON
        if self.testclass_params.get('airplane_mode', 1):
            self.log.info('Turning on airplane mode.')
            asserts.assert_true(utils.force_airplane_mode(self.dut, True),
                                'Can not turn on airplane mode.')
        wutils.wifi_toggle_state(self.dut, True)

    def teardown_test(self):
        self.iperf_server.stop()

    def teardown_class(self):
        self.access_point.teardown()
        # Turn WiFi OFF
        for dev in self.android_devices:
            wutils.wifi_toggle_state(dev, False)
            dev.go_to_sleep()

    def pass_fail_check(self, test_result):
        """Check the test result and decide if it passed or failed.

        Checks the throughput stability test's PASS/FAIL criteria based on
        minimum instantaneous throughput, and standard deviation.

        Args:
            test_result_dict: dict containing attenuation, throughput and other
            meta data
        """
        # Evaluate pass/fail
        min_throughput_check = (
            (test_result['iperf_summary']['min_throughput'] /
             test_result['iperf_summary']['avg_throughput']) *
            100) > self.testclass_params['min_throughput_threshold']
        std_deviation_check = test_result['iperf_summary'][
            'std_dev_percent'] < self.testclass_params[
                'std_deviation_threshold']

        if min_throughput_check and std_deviation_check:
            asserts.explicit_pass('Test Passed.')
        asserts.fail('Test Failed.')

    def post_process_results(self, test_result):
        """Extracts results and saves plots and JSON formatted results.

        Args:
            test_result: dict containing attenuation, iPerfResult object and
            other meta data
        Returns:
            test_result_dict: dict containing post-processed results including
            avg throughput, other metrics, and other meta data
        """
        # Save output as text file
        results_file_path = os.path.join(
            self.log_path, '{}.txt'.format(self.current_test_name))
        with open(results_file_path, 'w') as results_file:
            json.dump(wputils.serialize_dict(test_result), results_file)
        # Plot and save
        # Set blackbox metrics
        if self.publish_testcase_metrics:
            self.testcase_metric_logger.add_metric(
                'avg_throughput',
                test_result['iperf_summary']['avg_throughput'])
            self.testcase_metric_logger.add_metric(
                'min_throughput',
                test_result['iperf_summary']['min_throughput'])
            self.testcase_metric_logger.add_metric(
                'std_dev_percent',
                test_result['iperf_summary']['std_dev_percent'])
            figure = BokehFigure(self.current_test_name,
                                 x_label='Time (s)',
                                 primary_y_label='Throughput (Mbps)')
            time_data = list(
                range(
                    0,
                    len(test_result['iperf_summary']['instantaneous_rates'])))
            figure.add_line(
                time_data,
                test_result['iperf_summary']['instantaneous_rates'],
                legend=self.current_test_name,
                marker='circle')
            output_file_path = os.path.join(
                self.log_path, '{}.html'.format(self.current_test_name))
            figure.generate_figure(output_file_path)
        return test_result

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
        self.access_point.set_channel(band, testcase_params['channel'])
        self.access_point.set_bandwidth(band, testcase_params['mode'])
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
            self.dut.droid.wakeLockAcquireDim()
        else:
            self.dut.go_to_sleep()
        band = self.access_point.band_lookup_by_channel(
            testcase_params['channel'])
        if wputils.validate_network(self.dut,
                                    testcase_params['test_network']['SSID']):
            self.log.info('Already connected to desired network')
        else:
            wutils.wifi_toggle_state(self.dut, True)
            wutils.reset_wifi(self.dut)
            if self.testbed_params.get('txbf_off', False):
                wputils.disable_beamforming(self.dut)
            wutils.set_wifi_country_code(self.dut,
                                         self.testclass_params['country_code'])
            self.main_network[band]['channel'] = testcase_params['channel']
            wutils.wifi_connect(self.dut,
                                testcase_params['test_network'],
                                num_of_tries=5,
                                check_connectivity=True)
        self.dut_ip = self.dut.droid.connectivityGetIPv4Addresses('wlan0')[0]

    def setup_throughput_stability_test(self, testcase_params):
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
            self.log.info('Waiting before the first test.')
            time.sleep(first_test_delay)
            self.setup_dut(testcase_params)
        # Get and set attenuation levels for test
        testcase_params['atten_level'] = self.get_target_atten(testcase_params)
        self.log.info('Setting attenuation to {} dB'.format(
            testcase_params['atten_level']))
        for attenuator in self.attenuators:
            attenuator.set_atten(testcase_params['atten_level'])
        # Configure iperf
        if isinstance(self.iperf_server, ipf.IPerfServerOverAdb):
            testcase_params['iperf_server_address'] = self.dut_ip
        else:
            testcase_params[
                'iperf_server_address'] = wputils.get_server_address(
                    self.remote_server, self.dut_ip, '255.255.255.0')

    def run_throughput_stability_test(self, testcase_params):
        """Main function to test throughput stability.

        The function sets up the AP in the correct channel and mode
        configuration and runs an iperf test to measure throughput.

        Args:
            testcase_params: dict containing test specific parameters
        Returns:
            test_result: dict containing test result and meta data
        """
        # Run test and log result
        # Start iperf session
        self.log.info('Starting iperf test.')
        test_result = collections.OrderedDict()
        llstats_obj = wputils.LinkLayerStats(self.dut)
        llstats_obj.update_stats()
        if self.testbed_params['sniffer_enable'] and len(
                self.testclass_results) % self.sniffer_subsampling == 0:
            self.sniffer.start_capture(
                network=testcase_params['test_network'],
                chan=testcase_params['channel'],
                bw=testcase_params['bandwidth'],
                duration=self.testclass_params['iperf_duration'] / 5)
        self.iperf_server.start(tag=str(testcase_params['atten_level']))
        current_rssi = wputils.get_connected_rssi_nb(
            dut=self.dut,
            num_measurements=self.testclass_params['iperf_duration'] - 1,
            polling_frequency=1,
            first_measurement_delay=1,
            disconnect_warning=1,
            ignore_samples=1)
        client_output_path = self.iperf_client.start(
            testcase_params['iperf_server_address'],
            testcase_params['iperf_args'], str(testcase_params['atten_level']),
            self.testclass_params['iperf_duration'] + TEST_TIMEOUT)
        current_rssi = current_rssi.result()
        server_output_path = self.iperf_server.stop()
        # Stop sniffer
        if self.testbed_params['sniffer_enable'] and len(
                self.testclass_results) % self.sniffer_subsampling == 0:
            self.sniffer.stop_capture()
        # Set attenuator to 0 dB
        for attenuator in self.attenuators:
            attenuator.set_atten(0)
        # Parse and log result
        if testcase_params['use_client_output']:
            iperf_file = client_output_path
        else:
            iperf_file = server_output_path
        try:
            iperf_result = ipf.IPerfResult(iperf_file)
        except:
            iperf_result = ipf.IPerfResult('{}')  #empty iperf result
            self.log.warning('Cannot get iperf result.')
        if iperf_result.instantaneous_rates:
            instantaneous_rates_Mbps = [
                rate * 8 * (1.024**2)
                for rate in iperf_result.instantaneous_rates[
                    self.testclass_params['iperf_ignored_interval']:-1]
            ]
            tput_standard_deviation = iperf_result.get_std_deviation(
                self.testclass_params['iperf_ignored_interval']) * 8
        else:
            instantaneous_rates_Mbps = [float('nan')]
            tput_standard_deviation = float('nan')
        test_result['iperf_summary'] = {
            'instantaneous_rates':
            instantaneous_rates_Mbps,
            'avg_throughput':
            numpy.mean(instantaneous_rates_Mbps),
            'std_deviation':
            tput_standard_deviation,
            'min_throughput':
            min(instantaneous_rates_Mbps),
            'std_dev_percent':
            (tput_standard_deviation / numpy.mean(instantaneous_rates_Mbps)) *
            100
        }
        llstats_obj.update_stats()
        curr_llstats = llstats_obj.llstats_incremental.copy()
        test_result['testcase_params'] = testcase_params.copy()
        test_result['ap_settings'] = self.access_point.ap_settings.copy()
        test_result['attenuation'] = testcase_params['atten_level']
        test_result['iperf_result'] = iperf_result
        test_result['rssi_result'] = current_rssi
        test_result['llstats'] = curr_llstats

        llstats = (
            'TX MCS = {0} ({1:.1f}%). '
            'RX MCS = {2} ({3:.1f}%)'.format(
                test_result['llstats']['summary']['common_tx_mcs'],
                test_result['llstats']['summary']['common_tx_mcs_freq'] * 100,
                test_result['llstats']['summary']['common_rx_mcs'],
                test_result['llstats']['summary']['common_rx_mcs_freq'] * 100))

        test_message = (
            'Atten: {0:.2f}dB, RSSI: {1:.2f}dB. '
            'Throughput (Mean: {2:.2f}, Std. Dev:{3:.2f}%, Min: {4:.2f} Mbps).'
            'LLStats : {5}'.format(
                test_result['attenuation'],
                test_result['rssi_result']['signal_poll_rssi']['mean'],
                test_result['iperf_summary']['avg_throughput'],
                test_result['iperf_summary']['std_dev_percent'],
                test_result['iperf_summary']['min_throughput'], llstats))

        self.log.info(test_message)

        self.testclass_results.append(test_result)
        return test_result

    def get_target_atten(self, testcase_params):
        """Function gets attenuation used for test

        The function fetches the attenuation at which the test should be
        performed.

        Args:
            testcase_params: dict containing test specific parameters
        Returns:
            test_atten: target attenuation for test
        """
        # Get attenuation from reference test if it has been run
        ref_test_fields = ['channel', 'mode', 'signal_level']
        test_id = wputils.extract_sub_dict(testcase_params, ref_test_fields)
        test_id = tuple(test_id.items())
        if test_id in self.ref_attenuations:
            return self.ref_attenuations[test_id]

        # Get attenuation for target RSSI
        if testcase_params['signal_level'] == 'low':
            target_rssi = self.testclass_params['low_throughput_rssi_target']
        else:
            target_rssi = self.testclass_params['high_throughput_rssi_target']
        target_atten = wputils.get_atten_for_target_rssi(
            target_rssi, self.attenuators, self.dut, self.remote_server)

        self.ref_attenuations[test_id] = target_atten
        return self.ref_attenuations[test_id]

    def compile_test_params(self, testcase_params):
        """Function that completes setting the test case parameters."""
        # Check if test should be skipped based on parameters.
        wputils.check_skip_conditions(testcase_params, self.dut,
                                      self.access_point,
                                      getattr(self, 'ota_chamber', None))

        band = self.access_point.band_lookup_by_channel(
            testcase_params['channel'])
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

    def _test_throughput_stability(self, testcase_params):
        """ Function that gets called for each test case

        The function gets called in each test case. The function customizes
        the test based on the test name of the test that called it

        Args:
            testcase_params: dict containing test specific parameters
        """
        testcase_params = self.compile_test_params(testcase_params)
        self.setup_throughput_stability_test(testcase_params)
        test_result = self.run_throughput_stability_test(testcase_params)
        test_result_postprocessed = self.post_process_results(test_result)
        self.pass_fail_check(test_result_postprocessed)


# Over-the air version of ping tests
class WifiOtaThroughputStabilityTest(WifiThroughputStabilityTest):
    """Class to test over-the-air ping

    This class tests WiFi ping performance in an OTA chamber. It enables
    setting turntable orientation and other chamber parameters to study
    performance in varying channel conditions
    """

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        # Define metrics to be uploaded to BlackBox
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = False

    def setup_class(self):
        WifiThroughputStabilityTest.setup_class(self)
        self.ota_chamber = ota_chamber.create(
            self.user_params['OTAChamber'])[0]

    def teardown_class(self):
        WifiThroughputStabilityTest.teardown_class(self)
        self.ota_chamber.reset_chamber()
        self.process_testclass_results()

    def extract_test_id(self, testcase_params, id_fields):
        test_id = collections.OrderedDict(
            (param, testcase_params[param]) for param in id_fields)
        return test_id

    def process_testclass_results(self):
        """Saves all test results to enable comparison."""
        testclass_data = collections.OrderedDict()
        for test in self.testclass_results:
            current_params = test['testcase_params']
            channel_data = testclass_data.setdefault(current_params['channel'],
                                                     collections.OrderedDict())
            test_id = tuple(
                self.extract_test_id(current_params, [
                    'mode', 'traffic_type', 'traffic_direction', 'signal_level'
                ]).items())
            test_data = channel_data.setdefault(
                test_id, collections.OrderedDict(position=[], throughput=[]))
            test_data['position'].append(current_params['position'])
            test_data['throughput'].append(
                test['iperf_summary']['avg_throughput'])

        chamber_mode = self.testclass_results[0]['testcase_params'][
            'chamber_mode']
        if chamber_mode == 'orientation':
            x_label = 'Angle (deg)'
        elif chamber_mode == 'stepped stirrers':
            x_label = 'Position Index'

        # Publish test class metrics
        for channel, channel_data in testclass_data.items():
            for test_id, test_data in channel_data.items():
                test_id_dict = dict(test_id)
                metric_tag = 'ota_summary_{}_{}_{}_ch{}_{}'.format(
                    test_id_dict['signal_level'], test_id_dict['traffic_type'],
                    test_id_dict['traffic_direction'], channel,
                    test_id_dict['mode'])
                metric_name = metric_tag + '.avg_throughput'
                metric_value = numpy.nanmean(test_data['throughput'])
                self.testclass_metric_logger.add_metric(
                    metric_name, metric_value)
                metric_name = metric_tag + '.min_throughput'
                metric_value = min(test_data['throughput'])
                self.testclass_metric_logger.add_metric(
                    metric_name, metric_value)

        # Plot test class results
        plots = []
        for channel, channel_data in testclass_data.items():
            current_plot = BokehFigure(
                title='Channel {} - Rate vs. Position'.format(channel),
                x_label=x_label,
                primary_y_label='Rate (Mbps)',
            )
            for test_id, test_data in channel_data.items():
                test_id_dict = dict(test_id)
                legend = '{}, {} {}, {} RSSI'.format(
                    test_id_dict['mode'], test_id_dict['traffic_type'],
                    test_id_dict['traffic_direction'],
                    test_id_dict['signal_level'])
                current_plot.add_line(test_data['position'],
                                      test_data['throughput'], legend)
            current_plot.generate_figure()
            plots.append(current_plot)
        current_context = context.get_current_context().get_full_output_path()
        plot_file_path = os.path.join(current_context, 'results.html')
        BokehFigure.save_figures(plots, plot_file_path)

    def setup_throughput_stability_test(self, testcase_params):
        WifiThroughputStabilityTest.setup_throughput_stability_test(
            self, testcase_params)
        # Setup turntable
        if testcase_params['chamber_mode'] == 'orientation':
            self.ota_chamber.set_orientation(testcase_params['position'])
        elif testcase_params['chamber_mode'] == 'stepped stirrers':
            self.ota_chamber.step_stirrers(testcase_params['total_positions'])

    def get_target_atten(self, testcase_params):
        band = wputils.CHANNEL_TO_BAND_MAP[testcase_params['channel']]
        if testcase_params['signal_level'] == 'high':
            test_atten = self.testclass_params['ota_atten_levels'][band][0]
        elif testcase_params['signal_level'] == 'low':
            test_atten = self.testclass_params['ota_atten_levels'][band][1]
        return test_atten

    def _test_throughput_stability_over_orientation(self, testcase_params):
        """ Function that gets called for each test case

        The function gets called in each test case. The function customizes
        the test based on the test name of the test that called it

        Args:
            testcase_params: dict containing test specific parameters
        """
        testcase_params = self.compile_test_params(testcase_params)
        for position in testcase_params['positions']:
            testcase_params['position'] = position
            self.setup_throughput_stability_test(testcase_params)
            test_result = self.run_throughput_stability_test(testcase_params)
            self.post_process_results(test_result)

    def generate_test_cases(self, channels, modes, traffic_types,
                            traffic_directions, signal_levels, chamber_mode,
                            positions):
        allowed_configs = {
            20: [
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 36, 40, 44, 48, 64, 100,
                116, 132, 140, 149, 153, 157, 161, '6g37', '6g117', '6g213'
            ],
            40: [36, 44, 100, 149, 157, '6g37', '6g117', '6g213'],
            80: [36, 100, 149, '6g37', '6g117', '6g213'],
            160: [36, '6g37', '6g117', '6g213']
        }

        test_cases = []
        for channel, mode, signal_level, traffic_type, traffic_direction in itertools.product(
                channels, modes, signal_levels, traffic_types,
                traffic_directions):
            bandwidth = int(''.join([x for x in mode if x.isdigit()]))
            if channel not in allowed_configs[bandwidth]:
                continue
            testcase_params = collections.OrderedDict(
                channel=channel,
                mode=mode,
                bandwidth=bandwidth,
                traffic_type=traffic_type,
                traffic_direction=traffic_direction,
                signal_level=signal_level,
                chamber_mode=chamber_mode,
                total_positions=len(positions),
                positions=positions)
            testcase_name = ('test_tput_stability'
                             '_{}_{}_{}_ch{}_{}'.format(
                                 signal_level, traffic_type, traffic_direction,
                                 channel, mode))
            setattr(
                self, testcase_name,
                partial(self._test_throughput_stability_over_orientation,
                        testcase_params))
            test_cases.append(testcase_name)
        return test_cases


class WifiOtaThroughputStability_TenDegree_Test(WifiOtaThroughputStabilityTest
                                                ):

    def __init__(self, controllers):
        WifiOtaThroughputStabilityTest.__init__(self, controllers)
        self.tests = self.generate_test_cases([6, 36, 149, '6g37'],
                                              ['bw20', 'bw80', 'bw160'],
                                              ['TCP'], ['DL', 'UL'],
                                              ['high', 'low'], 'orientation',
                                              list(range(0, 360, 10)))

    def setup_class(self):
        WifiOtaThroughputStabilityTest.setup_class(self)
        self.sniffer_subsampling = 6


class WifiOtaThroughputStability_45Degree_Test(WifiOtaThroughputStabilityTest):

    def __init__(self, controllers):
        WifiOtaThroughputStabilityTest.__init__(self, controllers)
        self.tests = self.generate_test_cases([6, 36, 149, '6g37'],
                                              ['bw20', 'bw80', 'bw160'],
                                              ['TCP'], ['DL', 'UL'],
                                              ['high', 'low'], 'orientation',
                                              list(range(0, 360, 45)))


class WifiOtaThroughputStability_SteppedStirrers_Test(
        WifiOtaThroughputStabilityTest):

    def __init__(self, controllers):
        WifiOtaThroughputStabilityTest.__init__(self, controllers)
        self.tests = self.generate_test_cases([6, 36, 149, '6g37'],
                                              ['bw20', 'bw80', 'bw160'],
                                              ['TCP'], ['DL', 'UL'],
                                              ['high', 'low'],
                                              'stepped stirrers',
                                              list(range(100)))

    def setup_class(self):
        WifiOtaThroughputStabilityTest.setup_class(self)
        self.sniffer_subsampling = 10
