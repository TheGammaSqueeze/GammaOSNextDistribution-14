#!/usr/bin/env python3.4
#
#   Copyright 2020 - The Android Open Source Project
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
import logging
import os
from acts import asserts
from acts import base_test
from acts import utils
from acts.controllers import iperf_server as ipf
from acts.controllers import iperf_client as ipc
from acts.metrics.loggers.blackbox import BlackboxMappedMetricLogger
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi import ota_sniffer
from acts_contrib.test_utils.wifi import wifi_retail_ap as retail_ap
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils
from functools import partial
from WifiRvrTest import WifiRvrTest

AccessPointTuple = collections.namedtuple(('AccessPointTuple'),
                                          ['ap_settings'])


class WifiTdlsRvrTest(WifiRvrTest):
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
        req_params = [
            'tdls_rvr_test_params', 'testbed_params', 'RetailAccessPoints'
        ]
        opt_params = ['ap_networks', 'OTASniffer']
        self.unpack_userparams(req_params, opt_params)
        self.access_point = retail_ap.create(self.RetailAccessPoints)[0]
        self.testclass_params = self.tdls_rvr_test_params
        self.num_atten = self.attenuators[0].instrument.num_atten
        self.iperf_server = ipf.create([{
            'AndroidDevice':
            self.android_devices[0].serial,
            'port':
            '5201'
        }])[0]
        self.iperf_client = ipc.create([{
            'AndroidDevice':
            self.android_devices[1].serial,
            'port':
            '5201'
        }])[0]

        self.log_path = os.path.join(logging.log_path, 'results')
        if hasattr(self,
                   'OTASniffer') and self.testbed_params['sniffer_enable']:
            self.sniffer = ota_sniffer.create(self.OTASniffer)[0]
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
            self.log.info('Turning on airplane mode.')
            for ad in self.android_devices:
                asserts.assert_true(utils.force_airplane_mode(ad, True),
                                    "Can not turn on airplane mode.")
        for ad in self.android_devices:
            wutils.wifi_toggle_state(ad, True)

    def teardown_class(self):
        # Turn WiFi OFF
        for dev in self.android_devices:
            wutils.wifi_toggle_state(dev, False)
        self.process_testclass_results()
        # Teardown AP and release its lockfile
        self.access_point.teardown()

    def setup_test(self):
        for ad in self.android_devices:
            wputils.start_wifi_logging(ad)

    def teardown_test(self):
        self.iperf_server.stop()
        for ad in self.android_devices:
            wutils.reset_wifi(ad)
            wputils.stop_wifi_logging(ad)

    def on_exception(self, test_name, begin_time):
        for ad in self.android_devices:
            ad.take_bug_report(test_name, begin_time)
            ad.cat_adb_log(test_name, begin_time)
            wutils.get_ssrdumps(ad)

    def compute_test_metrics(self, rvr_result):
        #Set test metrics
        rvr_result['metrics'] = {}
        rvr_result['metrics']['peak_tput'] = max(
            rvr_result['throughput_receive'])
        if self.publish_testcase_metrics:
            self.testcase_metric_logger.add_metric(
                'peak_tput', rvr_result['metrics']['peak_tput'])

        test_mode = rvr_result['testcase_params']['mode']
        tput_below_limit = [
            tput <
            self.testclass_params['tput_metric_targets'][test_mode]['high']
            for tput in rvr_result['throughput_receive']
        ]
        rvr_result['metrics']['high_tput_range'] = -1
        for idx in range(len(tput_below_limit)):
            if all(tput_below_limit[idx:]):
                if idx == 0:
                    #Throughput was never above limit
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

    def setup_aps(self, testcase_params):
        self.log.info('Setting AP to channel {} {}'.format(
            testcase_params['channel'], testcase_params['bandwidth']))
        self.access_point.set_channel(testcase_params['interface_id'],
                                      testcase_params['channel'])
        self.access_point.set_bandwidth(testcase_params['interface_id'],
                                        testcase_params['bandwidth'])

    def setup_duts(self, testcase_params):
        # Check battery level before test
        for ad in self.android_devices:
            if not wputils.health_check(ad, 20):
                asserts.skip('Overheating or Battery low. Skipping test.')
            ad.go_to_sleep()
            wutils.reset_wifi(ad)
        # Turn screen off to preserve battery
        for ad in self.android_devices:
            wutils.wifi_connect(
                ad,
                self.ap_networks[0][testcase_params['interface_id']],
                num_of_tries=5,
                check_connectivity=True)

    def setup_tdls_connection(self, testcase_params):

        tdls_config = {}
        for idx, ad in enumerate(self.android_devices):
            tdls_config[idx] = {
                'ip_address':
                ad.droid.connectivityGetIPv4Addresses('wlan0')[0],
                'mac_address': ad.droid.wifiGetConnectionInfo()['mac_address'],
                'tdls_supported': ad.droid.wifiIsTdlsSupported(),
                'off_channel_supported':
                ad.droid.wifiIsOffChannelTdlsSupported()
            }
        self.android_devices[0].droid.wifiSetTdlsEnabledWithMacAddress(
            tdls_config[1]['mac_address'], True)

        testcase_params['iperf_server_address'] = tdls_config[0]['ip_address']
        testcase_params['tdls_config'] = tdls_config
        testcase_params['channel'] = testcase_params['channel']
        testcase_params['mode'] = testcase_params['bandwidth']
        testcase_params['test_network'] = self.ap_networks[0][
            testcase_params['interface_id']]

    def setup_tdls_rvr_test(self, testcase_params):
        # Setup the aps
        self.setup_aps(testcase_params)
        # Setup the duts
        self.setup_duts(testcase_params)
        # Set attenuator to 0 dB
        for attenuator in self.attenuators:
            attenuator.set_atten(0, strict=False)
        # Setup the aware connection
        self.setup_tdls_connection(testcase_params)
        # Set DUT to monitor RSSI and LLStats on
        self.monitored_dut = self.android_devices[1]

    def compile_test_params(self, testcase_params):
        """Function that completes all test params based on the test name.

        Args:
            testcase_params: dict containing test-specific parameters
        """
        for ad in self.android_devices:
            wputils.check_skip_conditions(testcase_params, ad,
                                          self.access_point)

        # Compile RvR parameters
        num_atten_steps = int((self.testclass_params['atten_stop'] -
                               self.testclass_params['atten_start']) /
                              self.testclass_params['atten_step'])
        testcase_params['atten_range'] = [
            self.testclass_params['atten_start'] +
            x * self.testclass_params['atten_step']
            for x in range(0, num_atten_steps)
        ]

        # Compile iperf arguments
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
        testcase_params['iperf_args'] = wputils.get_iperf_arg_string(
            duration=self.testclass_params['iperf_duration'],
            reverse_direction=(testcase_params['traffic_direction'] == 'DL'),
            socket_size=testcase_params['iperf_socket_size'],
            num_processes=testcase_params['iperf_processes'],
            traffic_type=testcase_params['traffic_type'],
            ipv6=False)
        testcase_params['use_client_output'] = (
            testcase_params['traffic_direction'] == 'DL')

        # Compile AP and infrastructure connection parameters
        testcase_params['interface_id'] = '2G' if testcase_params[
            'channel'] < 13 else '5G_1'
        return testcase_params

    def _test_tdls_rvr(self, testcase_params):
        """ Function that gets called for each test case

        Args:
            testcase_params: dict containing test-specific parameters
        """
        # Compile test parameters from config and test name
        testcase_params = self.compile_test_params(testcase_params)

        # Prepare devices and run test
        self.setup_tdls_rvr_test(testcase_params)
        rvr_result = self.run_rvr_test(testcase_params)

        # Post-process results
        self.testclass_results.append(rvr_result)
        self.process_test_results(rvr_result)
        self.pass_fail_check(rvr_result)

    def generate_test_cases(self, ap_config_list, traffic_type,
                            traffic_directions):
        """Function that auto-generates test cases for a test class."""
        test_cases = []

        for ap_config, traffic_direction in itertools.product(
                ap_config_list, traffic_directions):
            test_name = 'test_tdls_rvr_{}_{}_ch{}_{}'.format(
                traffic_type, traffic_direction, ap_config[0], ap_config[1])
            test_params = collections.OrderedDict(
                traffic_type=traffic_type,
                traffic_direction=traffic_direction,
                channel=ap_config[0],
                bandwidth=ap_config[1])
            test_class = self.__class__.__name__
            if "uuid_list" in self.user_params:
                test_tracker_uuid = self.user_params["uuid_list"][
                    test_class][test_name]
                test_case = test_tracker_info(uuid=test_tracker_uuid)(
                    lambda: self._test_tdls_rvr(test_params))
            else:
                test_case = partial(self._test_tdls_rvr,test_params)
            setattr(self, test_name, test_case)
            test_cases.append(test_name)
        return test_cases


class WifiTdlsRvr_FCC_TCP_Test(WifiTdlsRvrTest):
    def __init__(self, controllers):
        super().__init__(controllers)
        ap_config_list = [[6, 'bw20'], [36, 'bw20'], [36, 'bw40'],
                          [36, 'bw80'], [149, 'bw20'], [149, 'bw40'],
                          [149, 'bw80']]
        self.country_code = 'US'
        self.tests = self.generate_test_cases(ap_config_list=ap_config_list,
                                              traffic_type='TCP',
                                              traffic_directions=['DL', 'UL'])


class WifiTdlsRvr_FCC_UDP_Test(WifiTdlsRvrTest):
    def __init__(self, controllers):
        super().__init__(controllers)
        ap_config_list = [[6, 'bw20'], [36, 'bw20'], [36, 'bw40'],
                          [36, 'bw80'], [149, 'bw20'], [149, 'bw40'],
                          [149, 'bw80']]
        self.country_code = 'US'
        self.tests = self.generate_test_cases(ap_config_list=ap_config_list,
                                              traffic_type='UDP',
                                              traffic_directions=['DL', 'UL'])


class WifiTdlsRvr_ETSI_TCP_Test(WifiTdlsRvrTest):
    def __init__(self, controllers):
        super().__init__(controllers)
        ap_config_list = [[6, 'bw20'], [36, 'bw20'], [36, 'bw40'],
                          [36, 'bw80'], [149, 'bw20'], [149, 'bw40'],
                          [149, 'bw80']]
        self.country_code = 'GB'
        self.tests = self.generate_test_cases(ap_config_list=ap_config_list,
                                              traffic_type='TCP',
                                              traffic_directions=['DL', 'UL'])


class WifiTdlsRvr_ETSI_UDP_Test(WifiTdlsRvrTest):
    def __init__(self, controllers):
        super().__init__(controllers)
        ap_config_list = [[6, 'bw20'], [36, 'bw20'], [36, 'bw40'],
                          [36, 'bw80'], [149, 'bw20'], [149, 'bw40'],
                          [149, 'bw80']]
        self.country_code = 'GB'
        self.tests = self.generate_test_cases(ap_config_list=ap_config_list,
                                              traffic_type='UDP',
                                              traffic_directions=['DL', 'UL'])
