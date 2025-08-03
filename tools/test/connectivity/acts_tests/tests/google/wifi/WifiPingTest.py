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
import os
import statistics
from acts import asserts
from acts import context
from acts import base_test
from acts import utils
from acts.controllers.utils_lib import ssh
from acts.metrics.loggers.blackbox import BlackboxMappedMetricLogger
from acts_contrib.test_utils.wifi import ota_chamber
from acts_contrib.test_utils.wifi import ota_sniffer
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils
from acts_contrib.test_utils.wifi.wifi_performance_test_utils.bokeh_figure import BokehFigure
from acts_contrib.test_utils.wifi import wifi_retail_ap as retail_ap
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from functools import partial


class WifiPingTest(base_test.BaseTestClass):
    """Class for ping-based Wifi performance tests.

    This class implements WiFi ping performance tests such as range and RTT.
    The class setups up the AP in the desired configurations, configures
    and connects the phone to the AP, and runs  For an example config file to
    run this test class see example_connectivity_performance_ap_sta.json.
    """

    TEST_TIMEOUT = 10
    RSSI_POLL_INTERVAL = 0.2
    SHORT_SLEEP = 1
    MED_SLEEP = 5
    MAX_CONSECUTIVE_ZEROS = 5
    DISCONNECTED_PING_RESULT = {
        'connected': 0,
        'rtt': [],
        'time_stamp': [],
        'ping_interarrivals': [],
        'packet_loss_percentage': 100
    }

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True

    def setup_class(self):
        self.dut = self.android_devices[-1]
        req_params = [
            'ping_test_params', 'testbed_params', 'main_network',
            'RetailAccessPoints', 'RemoteServer'
        ]
        opt_params = ['OTASniffer']
        self.unpack_userparams(req_params, opt_params)
        self.testclass_params = self.ping_test_params
        self.num_atten = self.attenuators[0].instrument.num_atten
        self.ping_server = ssh.connection.SshConnection(
            ssh.settings.from_config(self.RemoteServer[0]['ssh_config']))
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
        self.atten_dut_chain_map = {}
        self.testclass_results = []

        # Turn WiFi ON
        if self.testclass_params.get('airplane_mode', 1):
            self.log.info('Turning on airplane mode.')
            asserts.assert_true(utils.force_airplane_mode(self.dut, True),
                                'Can not turn on airplane mode.')
        wutils.wifi_toggle_state(self.dut, True)

        # Configure test retries
        self.user_params['retry_tests'] = [self.__class__.__name__]

    def teardown_class(self):
        for attenuator in self.attenuators:
            attenuator.set_atten(0, strict=False, retry=True)
        # Turn WiFi OFF and reset AP
        self.access_point.teardown()
        for dev in self.android_devices:
            wutils.wifi_toggle_state(dev, False)
            dev.go_to_sleep()
        self.process_testclass_results()

    def setup_test(self):
        self.retry_flag = False

    def teardown_test(self):
        self.retry_flag = False

    def on_retry(self):
        """Function to control test logic on retried tests.

        This function is automatically executed on tests that are being
        retried. In this case the function resets wifi, toggles it off and on
        and sets a retry_flag to enable further tweaking the test logic on
        second attempts.
        """
        self.retry_flag = True
        for dev in self.android_devices:
            wutils.reset_wifi(dev)
            wutils.toggle_wifi_off_and_on(dev)

    def process_testclass_results(self):
        """Saves all test results to enable comparison."""
        testclass_summary = {}
        for test in self.testclass_results:
            if 'range' in test['test_name']:
                testclass_summary[test['test_name']] = test['range']
        # Save results
        results_file_path = os.path.join(self.log_path,
                                         'testclass_summary.json')
        with open(results_file_path, 'w') as results_file:
            json.dump(wputils.serialize_dict(testclass_summary),
                      results_file,
                      indent=4)

    def pass_fail_check_ping_rtt(self, result):
        """Check the test result and decide if it passed or failed.

        The function computes RTT statistics and fails any tests in which the
        tail of the ping latency results exceeds the threshold defined in the
        configuration file.

        Args:
            result: dict containing ping results and other meta data
        """
        ignored_fraction = (self.testclass_params['rtt_ignored_interval'] /
                            self.testclass_params['rtt_ping_duration'])
        sorted_rtt = [
            sorted(x['rtt'][round(ignored_fraction * len(x['rtt'])):])
            for x in result['ping_results']
        ]
        disconnected = any([len(x) == 0 for x in sorted_rtt])
        if disconnected:
            asserts.fail('Test failed. DUT disconnected at least once.')

        rtt_at_test_percentile = [
            x[int((1 - self.testclass_params['rtt_test_percentile'] / 100) *
                  len(x))] for x in sorted_rtt
        ]
        # Set blackbox metric
        if self.publish_testcase_metrics:
            self.testcase_metric_logger.add_metric('ping_rtt',
                                                   max(rtt_at_test_percentile))
        # Evaluate test pass/fail
        rtt_failed = any([
            rtt > self.testclass_params['rtt_threshold'] * 1000
            for rtt in rtt_at_test_percentile
        ])
        if rtt_failed:
            #TODO: figure out how to cleanly exclude RTT tests from retry
            asserts.explicit_pass(
                'Test failed. RTTs at test percentile = {}'.format(
                    rtt_at_test_percentile))
        else:
            asserts.explicit_pass(
                'Test Passed. RTTs at test percentile = {}'.format(
                    rtt_at_test_percentile))

    def pass_fail_check_ping_range(self, result):
        """Check the test result and decide if it passed or failed.

        Checks whether the attenuation at which ping packet losses begin to
        exceed the threshold matches the range derived from golden
        rate-vs-range result files. The test fails is ping range is
        range_gap_threshold worse than RvR range.

        Args:
            result: dict containing ping results and meta data
        """
        # Evaluate test pass/fail
        test_message = ('Attenuation at range is {}dB. '
                        'LLStats at Range: {}'.format(
                            result['range'], result['llstats_at_range']))
        if result['peak_throughput_pct'] < 95:
            asserts.fail('(RESULT NOT RELIABLE) {}'.format(test_message))

        # If pass, set Blackbox metric
        if self.publish_testcase_metrics:
            self.testcase_metric_logger.add_metric('ping_range',
                                                   result['range'])
        asserts.explicit_pass(test_message)

    def pass_fail_check(self, result):
        if 'range' in result['testcase_params']['test_type']:
            self.pass_fail_check_ping_range(result)
        else:
            self.pass_fail_check_ping_rtt(result)

    def process_ping_results(self, testcase_params, ping_range_result):
        """Saves and plots ping results.

        Args:
            ping_range_result: dict containing ping results and metadata
        """
        # Compute range
        ping_loss_over_att = [
            x['packet_loss_percentage']
            for x in ping_range_result['ping_results']
        ]
        ping_loss_above_threshold = [
            x > self.testclass_params['range_ping_loss_threshold']
            for x in ping_loss_over_att
        ]
        for idx in range(len(ping_loss_above_threshold)):
            if all(ping_loss_above_threshold[idx:]):
                range_index = max(idx, 1) - 1
                break
        else:
            range_index = -1
        ping_range_result['atten_at_range'] = testcase_params['atten_range'][
            range_index]
        ping_range_result['peak_throughput_pct'] = 100 - min(
            ping_loss_over_att)
        ping_range_result['total_attenuation'] = [
            ping_range_result['fixed_attenuation'] + att
            for att in testcase_params['atten_range']
        ]
        ping_range_result['range'] = (ping_range_result['atten_at_range'] +
                                      ping_range_result['fixed_attenuation'])
        ping_range_result['llstats_at_range'] = (
            'TX MCS = {0} ({1:.1f}%). '
            'RX MCS = {2} ({3:.1f}%)'.format(
                ping_range_result['llstats'][range_index]['summary']
                ['common_tx_mcs'], ping_range_result['llstats'][range_index]
                ['summary']['common_tx_mcs_freq'] * 100,
                ping_range_result['llstats'][range_index]['summary']
                ['common_rx_mcs'], ping_range_result['llstats'][range_index]
                ['summary']['common_rx_mcs_freq'] * 100))

        # Save results
        results_file_path = os.path.join(
            self.log_path, '{}.json'.format(self.current_test_name))
        with open(results_file_path, 'w') as results_file:
            json.dump(wputils.serialize_dict(ping_range_result),
                      results_file,
                      indent=4)

        # Plot results
        if 'rtt' in self.current_test_name:
            figure = BokehFigure(self.current_test_name,
                                 x_label='Timestamp (s)',
                                 primary_y_label='Round Trip Time (ms)')
            for idx, result in enumerate(ping_range_result['ping_results']):
                if len(result['rtt']) > 1:
                    x_data = [
                        t - result['time_stamp'][0]
                        for t in result['time_stamp']
                    ]
                    figure.add_line(
                        x_data, result['rtt'], 'RTT @ {}dB'.format(
                            ping_range_result['attenuation'][idx]))

            output_file_path = os.path.join(
                self.log_path, '{}.html'.format(self.current_test_name))
            figure.generate_figure(output_file_path)

    def run_ping_test(self, testcase_params):
        """Main function to test ping.

        The function sets up the AP in the correct channel and mode
        configuration and calls get_ping_stats while sweeping attenuation

        Args:
            testcase_params: dict containing all test parameters
        Returns:
            test_result: dict containing ping results and other meta data
        """
        # Prepare results dict
        llstats_obj = wputils.LinkLayerStats(
            self.dut, self.testclass_params.get('llstats_enabled', True))
        test_result = collections.OrderedDict()
        test_result['testcase_params'] = testcase_params.copy()
        test_result['test_name'] = self.current_test_name
        test_result['ap_config'] = self.access_point.ap_settings.copy()
        test_result['attenuation'] = testcase_params['atten_range']
        test_result['fixed_attenuation'] = self.testbed_params[
            'fixed_attenuation'][str(testcase_params['channel'])]
        test_result['rssi_results'] = []
        test_result['ping_results'] = []
        test_result['llstats'] = []
        # Setup sniffer
        if self.testbed_params['sniffer_enable']:
            self.sniffer.start_capture(
                testcase_params['test_network'],
                chan=testcase_params['channel'],
                bw=testcase_params['bandwidth'],
                duration=testcase_params['ping_duration'] *
                len(testcase_params['atten_range']) + self.TEST_TIMEOUT)
        # Run ping and sweep attenuation as needed
        zero_counter = 0
        pending_first_ping = 1
        for atten in testcase_params['atten_range']:
            for attenuator in self.attenuators:
                attenuator.set_atten(atten, strict=False, retry=True)
            if self.testclass_params.get('monitor_rssi', 1):
                rssi_future = wputils.get_connected_rssi_nb(
                    self.dut,
                    int(testcase_params['ping_duration'] / 2 /
                        self.RSSI_POLL_INTERVAL), self.RSSI_POLL_INTERVAL,
                    testcase_params['ping_duration'] / 2)
            # Refresh link layer stats
            llstats_obj.update_stats()
            if testcase_params.get('ping_from_dut', False):
                current_ping_stats = wputils.get_ping_stats(
                    self.dut,
                    wputils.get_server_address(self.ping_server, self.dut_ip,
                                               '255.255.255.0'),
                    testcase_params['ping_duration'],
                    testcase_params['ping_interval'],
                    testcase_params['ping_size'])
            else:
                current_ping_stats = wputils.get_ping_stats(
                    self.ping_server, self.dut_ip,
                    testcase_params['ping_duration'],
                    testcase_params['ping_interval'],
                    testcase_params['ping_size'])
            if self.testclass_params.get('monitor_rssi', 1):
                current_rssi = rssi_future.result()
            else:
                current_rssi = collections.OrderedDict([
                    ('time_stamp', []), ('bssid', []), ('ssid', []),
                    ('frequency', []),
                    ('signal_poll_rssi', wputils.empty_rssi_result()),
                    ('signal_poll_avg_rssi', wputils.empty_rssi_result()),
                    ('chain_0_rssi', wputils.empty_rssi_result()),
                    ('chain_1_rssi', wputils.empty_rssi_result())
                ])
            test_result['rssi_results'].append(current_rssi)
            llstats_obj.update_stats()
            curr_llstats = llstats_obj.llstats_incremental.copy()
            test_result['llstats'].append(curr_llstats)
            if current_ping_stats['connected']:
                llstats_str = 'TX MCS = {0} ({1:.1f}%). RX MCS = {2} ({3:.1f}%)'.format(
                    curr_llstats['summary']['common_tx_mcs'],
                    curr_llstats['summary']['common_tx_mcs_freq'] * 100,
                    curr_llstats['summary']['common_rx_mcs'],
                    curr_llstats['summary']['common_rx_mcs_freq'] * 100)
                self.log.info(
                    'Attenuation = {0}dB\tPacket Loss = {1:.1f}%\t'
                    'Avg RTT = {2:.2f}ms\tRSSI = {3:.1f} [{4:.1f},{5:.1f}]\t{6}\t'
                    .format(atten,
                            current_ping_stats['packet_loss_percentage'],
                            statistics.mean(current_ping_stats['rtt']),
                            current_rssi['signal_poll_rssi']['mean'],
                            current_rssi['chain_0_rssi']['mean'],
                            current_rssi['chain_1_rssi']['mean'], llstats_str))
                if current_ping_stats['packet_loss_percentage'] == 100:
                    zero_counter = zero_counter + 1
                else:
                    zero_counter = 0
                    pending_first_ping = 0
            else:
                self.log.info(
                    'Attenuation = {}dB. Disconnected.'.format(atten))
                zero_counter = zero_counter + 1
            test_result['ping_results'].append(current_ping_stats.as_dict())
            # Test ends when ping loss stable at 0. If test has successfully
            # started, test ends on MAX_CONSECUTIVE_ZEROS. In case of a restry
            # extra zeros are allowed to ensure a test properly starts.
            if self.retry_flag and pending_first_ping:
                allowable_zeros = self.MAX_CONSECUTIVE_ZEROS**2
            else:
                allowable_zeros = self.MAX_CONSECUTIVE_ZEROS
            if zero_counter == allowable_zeros:
                self.log.info('Ping loss stable at 100%. Stopping test now.')
                for idx in range(
                        len(testcase_params['atten_range']) -
                        len(test_result['ping_results'])):
                    test_result['ping_results'].append(
                        self.DISCONNECTED_PING_RESULT)
                break
        # Set attenuator to initial setting
        for attenuator in self.attenuators:
            attenuator.set_atten(testcase_params['atten_range'][0],
                                 strict=False,
                                 retry=True)
        if self.testbed_params['sniffer_enable']:
            self.sniffer.stop_capture()
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
        if 'low' in testcase_params['ap_power']:
            self.log.info('Setting low AP power.')
            self.access_point.set_power(
                band, self.testclass_params['low_ap_tx_power'])
        self.log.info('Access Point Configuration: {}'.format(
            self.access_point.ap_settings))

    def validate_and_connect(self, testcase_params):
        if wputils.validate_network(self.dut,
                                    testcase_params['test_network']['SSID']):
            self.log.info('Already connected to desired network')
        else:
            current_country = wputils.get_country_code(self.dut)
            if current_country != self.testclass_params['country_code']:
                self.log.warning(
                    'Requested CC: {}, Current CC: {}. Resetting WiFi'.format(
                        self.testclass_params['country_code'],
                        current_country))
                wutils.wifi_toggle_state(self.dut, False)
                wutils.set_wifi_country_code(
                    self.dut, self.testclass_params['country_code'])
                wutils.wifi_toggle_state(self.dut, True)
                wutils.reset_wifi(self.dut)
                wutils.set_wifi_country_code(
                    self.dut, self.testclass_params['country_code'])
            if self.testbed_params.get('txbf_off', False):
                wputils.disable_beamforming(self.dut)
            testcase_params['test_network']['channel'] = testcase_params[
                'channel']
            wutils.wifi_connect(self.dut,
                                testcase_params['test_network'],
                                num_of_tries=5,
                                check_connectivity=True)

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
        self.validate_and_connect(testcase_params)
        self.dut_ip = self.dut.droid.connectivityGetIPv4Addresses('wlan0')[0]
        if testcase_params['channel'] not in self.atten_dut_chain_map.keys():
            self.atten_dut_chain_map[testcase_params[
                'channel']] = wputils.get_current_atten_dut_chain_map(
                    self.attenuators, self.dut, self.ping_server)
        self.log.info('Current Attenuator-DUT Chain Map: {}'.format(
            self.atten_dut_chain_map[testcase_params['channel']]))
        for idx, atten in enumerate(self.attenuators):
            if self.atten_dut_chain_map[testcase_params['channel']][
                    idx] == testcase_params['attenuated_chain']:
                atten.offset = atten.instrument.max_atten
            else:
                atten.offset = 0

    def setup_ping_test(self, testcase_params):
        """Function that gets devices ready for the test.

        Args:
            testcase_params: dict containing test-specific parameters
        """
        # Configure AP
        self.setup_ap(testcase_params)
        # Set attenuator to starting attenuation
        band = wputils.CHANNEL_TO_BAND_MAP[testcase_params['channel']]
        for attenuator in self.attenuators:
            attenuator.set_atten(
                self.testclass_params['range_atten_start'].get(band, 0),
                strict=False,
                retry=True)
        # Reset, configure, and connect DUT
        self.setup_dut(testcase_params)

    def get_range_start_atten(self, testcase_params):
        """Gets the starting attenuation for this ping test.

        The function gets the starting attenuation by checking whether a test
        at the same configuration has executed. If so it sets the starting
        point a configurable number of dBs below the reference test.

        Args:
            testcase_params: dict containing all test parameters
        Returns:
            start_atten: starting attenuation for current test
        """
        band = wputils.CHANNEL_TO_BAND_MAP[testcase_params['channel']]
        # If the test is being retried, start from the beginning
        if self.retry_flag:
            self.log.info('Retry flag set. Setting attenuation to minimum.')
            return self.testclass_params['range_atten_start'].get(band, 0)
        # Get the current and reference test config. The reference test is the
        # one performed at the current MCS+1
        ref_test_params = wputils.extract_sub_dict(
            testcase_params, testcase_params['reference_params'])
        # Check if reference test has been run and set attenuation accordingly
        previous_params = [
            wputils.extract_sub_dict(result['testcase_params'],
                                     testcase_params['reference_params'])
            for result in self.testclass_results
        ]
        try:
            ref_index = previous_params[::-1].index(ref_test_params)
            ref_index = len(previous_params) - 1 - ref_index
            start_atten = self.testclass_results[ref_index][
                'atten_at_range'] - (
                    self.testclass_params['adjacent_range_test_gap'])
        except ValueError:
            start_atten = self.testclass_params['range_atten_start'].get(
                band, 0)
            self.log.info(
                'Reference test not found. Starting from {} dB'.format(
                    start_atten))
        return start_atten

    def compile_test_params(self, testcase_params):
        # Check if test should be skipped.
        wputils.check_skip_conditions(testcase_params, self.dut,
                                      self.access_point,
                                      getattr(self, 'ota_chamber', None))

        band = self.access_point.band_lookup_by_channel(
            testcase_params['channel'])
        testcase_params['test_network'] = self.main_network[band]
        testcase_params['band'] = band
        if testcase_params['chain_mask'] in ['0', '1']:
            testcase_params['attenuated_chain'] = 'DUT-Chain-{}'.format(
                1 if testcase_params['chain_mask'] == '0' else 0)
        else:
            # Set attenuated chain to -1. Do not set to None as this will be
            # compared to RF chain map which may include None
            testcase_params['attenuated_chain'] = -1
        if testcase_params['test_type'] == 'test_ping_range':
            testcase_params.update(
                ping_interval=self.testclass_params['range_ping_interval'],
                ping_duration=self.testclass_params['range_ping_duration'],
                ping_size=self.testclass_params['ping_size'],
            )
        elif testcase_params['test_type'] == 'test_fast_ping_rtt':
            testcase_params.update(
                ping_interval=self.testclass_params['rtt_ping_interval']
                ['fast'],
                ping_duration=self.testclass_params['rtt_ping_duration'],
                ping_size=self.testclass_params['ping_size'],
            )
        elif testcase_params['test_type'] == 'test_slow_ping_rtt':
            testcase_params.update(
                ping_interval=self.testclass_params['rtt_ping_interval']
                ['slow'],
                ping_duration=self.testclass_params['rtt_ping_duration'],
                ping_size=self.testclass_params['ping_size'])

        if testcase_params['test_type'] == 'test_ping_range':
            start_atten = self.get_range_start_atten(testcase_params)
            num_atten_steps = int(
                (self.testclass_params['range_atten_stop'] - start_atten) /
                self.testclass_params['range_atten_step'])
            testcase_params['atten_range'] = [
                start_atten + x * self.testclass_params['range_atten_step']
                for x in range(0, num_atten_steps)
            ]
        else:
            testcase_params['atten_range'] = self.testclass_params[
                'rtt_test_attenuation']
        return testcase_params

    def _test_ping(self, testcase_params):
        """ Function that gets called for each range test case

        The function gets called in each range test case. It customizes the
        range test based on the test name of the test that called it

        Args:
            testcase_params: dict containing preliminary set of parameters
        """
        # Compile test parameters from config and test name
        testcase_params = self.compile_test_params(testcase_params)
        # Run ping test
        self.setup_ping_test(testcase_params)
        ping_result = self.run_ping_test(testcase_params)
        # Postprocess results
        self.process_ping_results(testcase_params, ping_result)
        self.testclass_results.append(ping_result)
        self.pass_fail_check(ping_result)

    def generate_test_cases(self, ap_power, channels, modes, chain_mask,
                            test_types, **kwargs):
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

        for channel, mode, chain, test_type in itertools.product(
                channels, modes, chain_mask, test_types):
            bandwidth = int(''.join([x for x in mode if x.isdigit()]))
            if channel not in allowed_configs[bandwidth]:
                continue
            testcase_name = '{}_ch{}_{}_ch{}'.format(test_type, channel, mode,
                                                     chain)
            testcase_params = collections.OrderedDict(test_type=test_type,
                                                      ap_power=ap_power,
                                                      channel=channel,
                                                      mode=mode,
                                                      bandwidth=bandwidth,
                                                      chain_mask=chain,
                                                      **kwargs)
            setattr(self, testcase_name,
                    partial(self._test_ping, testcase_params))
            test_cases.append(testcase_name)
        return test_cases


class WifiPing_TwoChain_Test(WifiPingTest):

    def __init__(self, controllers):
        super().__init__(controllers)
        self.tests = self.generate_test_cases(
            ap_power='standard',
            channels=[
                1, 6, 11, 36, 40, 44, 48, 149, 153, 157, 161, '6g37', '6g117',
                '6g213'
            ],
            modes=['bw20', 'bw80', 'bw160'],
            test_types=[
                'test_ping_range', 'test_fast_ping_rtt', 'test_slow_ping_rtt'
            ],
            chain_mask=['2x2'],
            reference_params=['band', 'chain_mask'])


class WifiPing_PerChainRange_Test(WifiPingTest):

    def __init__(self, controllers):
        super().__init__(controllers)
        self.tests = self.generate_test_cases(
            ap_power='standard',
            chain_mask=['0', '1', '2x2'],
            channels=[
                1, 6, 11, 36, 40, 44, 48, 149, 153, 157, 161, '6g37', '6g117',
                '6g213'
            ],
            modes=['bw20', 'bw80', 'bw160'],
            test_types=['test_ping_range'],
            reference_params=['band', 'chain_mask'])


class WifiPing_LowPowerAP_Test(WifiPingTest):

    def __init__(self, controllers):
        super().__init__(controllers)
        self.tests = self.generate_test_cases(
            ap_power='low_power',
            chain_mask=['0', '1', '2x2'],
            channels=[1, 6, 11, 36, 40, 44, 48, 149, 153, 157, 161],
            modes=['bw20', 'bw80'],
            test_types=['test_ping_range'],
            reference_params=['band', 'chain_mask'])


# Over-the air version of ping tests
class WifiOtaPingTest(WifiPingTest):
    """Class to test over-the-air ping

    This class tests WiFi ping performance in an OTA chamber. It enables
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
        WifiPingTest.setup_class(self)
        self.ota_chamber = ota_chamber.create(
            self.user_params['OTAChamber'])[0]

    def teardown_class(self):
        WifiPingTest.teardown_class(self)
        self.process_testclass_results()
        self.ota_chamber.reset_chamber()

    def process_testclass_results(self):
        """Saves all test results to enable comparison."""
        WifiPingTest.process_testclass_results(self)

        range_vs_angle = collections.OrderedDict()
        for test in self.testclass_results:
            curr_params = test['testcase_params']
            curr_config = wputils.extract_sub_dict(
                curr_params, ['channel', 'mode', 'chain_mask'])
            curr_config_id = tuple(curr_config.items())
            if curr_config_id in range_vs_angle:
                if curr_params['position'] not in range_vs_angle[
                        curr_config_id]['position']:
                    range_vs_angle[curr_config_id]['position'].append(
                        curr_params['position'])
                    range_vs_angle[curr_config_id]['range'].append(
                        test['range'])
                    range_vs_angle[curr_config_id]['llstats_at_range'].append(
                        test['llstats_at_range'])
                else:
                    range_vs_angle[curr_config_id]['range'][-1] = test['range']
                    range_vs_angle[curr_config_id]['llstats_at_range'][
                        -1] = test['llstats_at_range']
            else:
                range_vs_angle[curr_config_id] = {
                    'position': [curr_params['position']],
                    'range': [test['range']],
                    'llstats_at_range': [test['llstats_at_range']]
                }
        chamber_mode = self.testclass_results[0]['testcase_params'][
            'chamber_mode']
        if chamber_mode == 'orientation':
            x_label = 'Angle (deg)'
        elif chamber_mode == 'stepped stirrers':
            x_label = 'Position Index'
        figure = BokehFigure(
            title='Range vs. Position',
            x_label=x_label,
            primary_y_label='Range (dB)',
        )
        for curr_config_id, curr_config_data in range_vs_angle.items():
            curr_config = collections.OrderedDict(curr_config_id)
            figure.add_line(x_data=curr_config_data['position'],
                            y_data=curr_config_data['range'],
                            hover_text=curr_config_data['llstats_at_range'],
                            legend='{}'.format(curr_config_id))
            average_range = sum(curr_config_data['range']) / len(
                curr_config_data['range'])
            self.log.info('Average range for {} is: {}dB'.format(
                curr_config_id, average_range))
            metric_name = 'ota_summary_ch{}_{}_ch{}.avg_range'.format(
                curr_config['channel'], curr_config['mode'],
                curr_config['chain_mask'])
            self.testclass_metric_logger.add_metric(metric_name, average_range)
        current_context = context.get_current_context().get_full_output_path()
        plot_file_path = os.path.join(current_context, 'results.html')
        figure.generate_figure(plot_file_path)

        # Save results
        results_file_path = os.path.join(current_context,
                                         'testclass_summary.json')
        with open(results_file_path, 'w') as results_file:
            json.dump(wputils.serialize_dict(range_vs_angle),
                      results_file,
                      indent=4)

    def setup_dut(self, testcase_params):
        """Sets up the DUT in the configuration required by the test.

        Args:
            testcase_params: dict containing AP and other test params
        """
        wputils.set_chain_mask(self.dut, testcase_params['chain_mask'])
        # Turn screen off to preserve battery
        if self.testbed_params.get('screen_on',
                                   False) or self.testclass_params.get(
                                       'screen_on', False):
            self.dut.droid.wakeLockAcquireDim()
        else:
            self.dut.go_to_sleep()
        self.validate_and_connect(testcase_params)
        self.dut_ip = self.dut.droid.connectivityGetIPv4Addresses('wlan0')[0]

    def setup_ping_test(self, testcase_params):
        # Setup turntable
        if testcase_params['chamber_mode'] == 'orientation':
            self.ota_chamber.set_orientation(testcase_params['position'])
        elif testcase_params['chamber_mode'] == 'stepped stirrers':
            self.ota_chamber.step_stirrers(testcase_params['total_positions'])
        # Continue setting up ping test
        WifiPingTest.setup_ping_test(self, testcase_params)

    def generate_test_cases(self, ap_power, channels, modes, chain_masks,
                            chamber_mode, positions, **kwargs):
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
        for channel, mode, chain_mask, position in itertools.product(
                channels, modes, chain_masks, positions):
            bandwidth = int(''.join([x for x in mode if x.isdigit()]))
            if channel not in allowed_configs[bandwidth]:
                continue
            testcase_name = 'test_ping_range_ch{}_{}_ch{}_pos{}'.format(
                channel, mode, chain_mask, position)
            testcase_params = collections.OrderedDict(
                test_type='test_ping_range',
                ap_power=ap_power,
                channel=channel,
                mode=mode,
                bandwidth=bandwidth,
                chain_mask=chain_mask,
                chamber_mode=chamber_mode,
                total_positions=len(positions),
                position=position,
                **kwargs)
            setattr(self, testcase_name,
                    partial(self._test_ping, testcase_params))
            test_cases.append(testcase_name)
        return test_cases


class WifiOtaPing_TenDegree_Test(WifiOtaPingTest):

    def __init__(self, controllers):
        WifiOtaPingTest.__init__(self, controllers)
        self.tests = self.generate_test_cases(
            ap_power='standard',
            channels=[6, 36, 149, '6g37', '6g117', '6g213'],
            modes=['bw20', 'bw80', 'bw160'],
            chain_masks=['2x2'],
            chamber_mode='orientation',
            positions=list(range(0, 360, 10)),
            reference_params=['channel', 'mode', 'chain_mask'])


class WifiOtaPing_SteppedStirrers_Test(WifiOtaPingTest):

    def __init__(self, controllers):
        WifiOtaPingTest.__init__(self, controllers)
        self.tests = self.generate_test_cases(
            ap_power='standard',
            channels=[6, 36, 149],
            modes=['bw20'],
            chain_masks=['2x2'],
            chamber_mode='stepped stirrers',
            positions=list(range(100)),
            reference_params=['channel', 'mode', 'chain_mask'])


class WifiOtaPing_LowPowerAP_TenDegree_Test(WifiOtaPingTest):

    def __init__(self, controllers):
        WifiOtaPingTest.__init__(self, controllers)
        self.tests = self.generate_test_cases(
            ap_power='low_power',
            channels=[6, 36, 149],
            modes=['bw20'],
            chain_masks=['2x2'],
            chamber_mode='orientation',
            positions=list(range(0, 360, 10)),
            reference_params=['channel', 'mode', 'chain_mask'])


class WifiOtaPing_LowPowerAP_SteppedStirrers_Test(WifiOtaPingTest):

    def __init__(self, controllers):
        WifiOtaPingTest.__init__(self, controllers)
        self.tests = self.generate_test_cases(
            ap_power='low_power',
            channels=[6, 36, 149],
            modes=['bw20'],
            chain_masks=['2x2'],
            chamber_mode='stepped stirrers',
            positions=list(range(100)),
            reference_params=['channel', 'mode', 'chain_mask'])


class WifiOtaPing_LowPowerAP_PerChain_TenDegree_Test(WifiOtaPingTest):

    def __init__(self, controllers):
        WifiOtaPingTest.__init__(self, controllers)
        self.tests = self.generate_test_cases(
            ap_power='low_power',
            channels=[6, 36, 149],
            modes=['bw20'],
            chain_masks=[0, 1, '2x2'],
            chamber_mode='orientation',
            positions=list(range(0, 360, 10)),
            reference_params=['channel', 'mode', 'chain_mask'])


class WifiOtaPing_PerChain_TenDegree_Test(WifiOtaPingTest):

    def __init__(self, controllers):
        WifiOtaPingTest.__init__(self, controllers)
        self.tests = self.generate_test_cases(
            ap_power='standard',
            channels=[6, 36, 149, '6g37', '6g117', '6g213'],
            modes=['bw20'],
            chain_masks=[0, 1, '2x2'],
            chamber_mode='orientation',
            positions=list(range(0, 360, 10)),
            reference_params=['channel', 'mode', 'chain_mask'])
