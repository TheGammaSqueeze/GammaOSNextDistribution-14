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
import json
import logging
import os
from acts import asserts
from acts import base_test
from acts import utils
from acts.controllers import iperf_server as ipf
from acts.controllers import iperf_client as ipc
from acts.controllers.adb_lib.error import AdbCommandError
from acts.metrics.loggers.blackbox import BlackboxMappedMetricLogger
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi import ota_sniffer
from acts_contrib.test_utils.wifi import wifi_retail_ap as retail_ap
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils
from acts_contrib.test_utils.wifi.aware import aware_const as aconsts
from acts_contrib.test_utils.wifi.aware import aware_test_utils as autils
from functools import partial
from WifiRvrTest import WifiRvrTest

AccessPointTuple = collections.namedtuple(('AccessPointTuple'),
                                          ['ap_settings'])


class WifiAwareRvrTest(WifiRvrTest):

    # message ID counter to make sure all uses are unique
    msg_id = 0

    # offset (in seconds) to separate the start-up of multiple devices.
    # De-synchronizes the start-up time so that they don't start and stop scanning
    # at the same time - which can lead to very long clustering times.
    device_startup_offset = 2

    SERVICE_NAME = "GoogleTestServiceXYZ"

    PASSPHRASE = "This is some random passphrase - very very secure!!"
    PASSPHRASE2 = "This is some random passphrase - very very secure - but diff!!"

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
        req_params = ['aware_rvr_test_params', 'testbed_params']
        opt_params = ['RetailAccessPoints', 'ap_networks', 'OTASniffer']
        self.unpack_userparams(req_params, opt_params)
        if hasattr(self, 'RetailAccessPoints'):
            self.access_points = retail_ap.create(self.RetailAccessPoints)
            self.access_point = self.access_points[0]
        else:
            self.access_point = AccessPointTuple({})
        self.testclass_params = self.aware_rvr_test_params
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
        for ap in self.access_points:
            ap.teardown()
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
            if not ad.droid.doesDeviceSupportWifiAwareFeature():
                return
            ad.droid.wifiP2pClose()
            ad.droid.wifiAwareDestroyAll()
            autils.reset_device_parameters(ad)
            autils.validate_forbidden_callbacks(ad)
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
        for network in testcase_params['ap_networks']:
            self.log.info('Setting AP {} {} interface on channel {}'.format(
                network['ap_id'], network['interface_id'], network['channel']))
            self.access_points[network['ap_id']].set_channel(
                network['interface_id'], network['channel'])

    def setup_duts(self, testcase_params):
        # Check battery level before test
        for ad in self.android_devices:
            if not wputils.health_check(ad, 20):
                asserts.skip('Overheating or Battery low. Skipping test.')
            ad.go_to_sleep()
            wutils.reset_wifi(ad)
        # Turn screen off to preserve battery
        for network in testcase_params['ap_networks']:
            for connected_dut in network['connected_dut']:
                self.log.info("Connecting DUT {} to {}".format(
                    connected_dut, self.ap_networks[network['ap_id']][
                        network['interface_id']]))
                wutils.wifi_connect(self.android_devices[connected_dut],
                                    self.ap_networks[network['ap_id']][
                                        network['interface_id']],
                                    num_of_tries=5,
                                    check_connectivity=True)

    def setup_aware_connection(self, testcase_params):
        # Basic aware setup
        for ad in self.android_devices:
            asserts.skip_if(
                not ad.droid.doesDeviceSupportWifiAwareFeature(),
                "Device under test does not support Wi-Fi Aware - skipping test"
            )
            aware_avail = ad.droid.wifiIsAwareAvailable()
            ad.droid.wifiP2pClose()
            wutils.wifi_toggle_state(ad, True)
            utils.set_location_service(ad, True)
            if not aware_avail:
                self.log.info('Aware not available. Waiting ...')
                autils.wait_for_event(ad,
                                      aconsts.BROADCAST_WIFI_AWARE_AVAILABLE,
                                      timeout=30)
            ad.aware_capabilities = autils.get_aware_capabilities(ad)
            autils.reset_device_parameters(ad)
            autils.reset_device_statistics(ad)
            autils.set_power_mode_parameters(ad, 'INTERACTIVE')
            wutils.set_wifi_country_code(ad, self.country_code)
            try:
                autils.configure_ndp_allow_any_override(ad, True)
            except AdbCommandError as e:
                self.log.warning('AdbCommandError: {}'.format(e))
            # set randomization interval to 0 (disable) to reduce likelihood of
            # interference in tests
            autils.configure_mac_random_interval(ad, 0)
            ad.ed.clear_all_events()

        # Establish Aware Connection
        self.init_dut = self.android_devices[0]
        self.resp_dut = self.android_devices[1]

        # note: Publisher = Responder, Subscribe = Initiator
        (resp_req_key, init_req_key, resp_aware_if, init_aware_if, resp_ipv6,
         init_ipv6) = autils.create_ib_ndp(
             self.resp_dut, self.init_dut,
             autils.create_discovery_config(self.SERVICE_NAME,
                                            aconsts.PUBLISH_TYPE_UNSOLICITED),
             autils.create_discovery_config(self.SERVICE_NAME,
                                            aconsts.SUBSCRIBE_TYPE_PASSIVE),
             self.device_startup_offset)
        testcase_params['aware_config'] = {
            "init_req_key": init_req_key,
            "resp_req_key": resp_req_key,
            "init_aware_if": init_aware_if,
            "resp_aware_if": resp_aware_if,
            "init_ipv6": init_ipv6,
            "resp_ipv6": resp_ipv6
        }
        testcase_params['iperf_server_address'] = init_ipv6
        for ad in self.android_devices:
            self.log.warning(
                ad.adb.shell('cmd wifiaware native_cb get_channel_info'))
        ndp_config = self.android_devices[0].adb.shell(
            'cmd wifiaware native_cb get_channel_info')
        ndp_config = json.loads(ndp_config)
        ndp_config = ndp_config[list(ndp_config.keys())[0]]
        if ndp_config:
            testcase_params['channel'] = wutils.WifiEnums.freq_to_channel[
                ndp_config[0]['channelFreq']]
        else:
            self.log.warning('Unknown NDP channel. Setting sniffer to Ch149')
            testcase_params['channel'] = 149
        if testcase_params['channel'] < 13:
            testcase_params['mode'] = 'VHT20'
        else:
            testcase_params['mode'] = 'VHT80'
        testcase_params['test_network'] = {'SSID': 'Aware'}
        self.log.info('Wifi Aware Connection Established on Channel {} {} '
                      '(Interfaces: {},{})'.format(testcase_params['channel'],
                                                   testcase_params['mode'],
                                                   init_aware_if,
                                                   resp_aware_if))

    def setup_aware_rvr_test(self, testcase_params):
        # Setup the aps
        self.setup_aps(testcase_params)
        # Setup the duts
        self.setup_duts(testcase_params)
        # Set attenuator to 0 dB
        for attenuator in self.attenuators:
            attenuator.set_atten(0, strict=False)
        # Setup the aware connection
        self.setup_aware_connection(testcase_params)
        # Set DUT to monitor RSSI and LLStats on
        self.monitored_dut = self.android_devices[1]

    def cleanup_aware_rvr_test(self, testcase_params):
        # clean-up
        self.resp_dut.droid.connectivityUnregisterNetworkCallback(
            testcase_params['aware_config']['resp_req_key'])
        self.init_dut.droid.connectivityUnregisterNetworkCallback(
            testcase_params['aware_config']['init_req_key'])

    def compile_test_params(self, testcase_params):
        """Function that completes all test params based on the test name.

        Args:
            testcase_params: dict containing test-specific parameters
        """
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
            ipv6=True)
        testcase_params['use_client_output'] = (
            testcase_params['traffic_direction'] == 'DL')

        # Compile AP and infrastructure connection parameters
        ap_networks = []
        if testcase_params['concurrency_state'][0]:
            band = testcase_params['concurrency_state'][0].split('_')[0]
            ap_networks.append({
                'ap_id':
                0,
                'interface_id':
                band if band == '2G' else band + '_1',
                'band':
                band,
                'channel':
                1 if band == '2G' else 36,
                'connected_dut': [0]
            })

        if testcase_params['concurrency_state'][1]:
            if testcase_params['concurrency_state'][0] == testcase_params[
                    'concurrency_state'][1]:
                # if connected to same network, add it to the above
                ap_networks[0]['connected_dut'].append(1)
            else:
                band = testcase_params['concurrency_state'][1].split('_')[0]
                if not testcase_params['concurrency_state'][0]:
                    # if it is the only dut connected, assign it to ap 0
                    ap_id = 0
                elif band == ap_networks[0]['band']:
                    # if its connected to same band, connect to ap 1
                    ap_id = 1
                else:
                    # if its on a different band, connect to ap 0 as well
                    ap_id = 1
                ap_networks.append({
                    'ap_id':
                    ap_id,
                    'interface_id':
                    band if band == '2G' else band + '_1',
                    'band':
                    band,
                    'channel':
                    11 if band == '2G' else 149,
                    'connected_dut': [1]
                })
        testcase_params['ap_networks'] = ap_networks

        return testcase_params

    def _test_aware_rvr(self, testcase_params):
        """ Function that gets called for each test case

        Args:
            testcase_params: dict containing test-specific parameters
        """
        # Compile test parameters from config and test name
        testcase_params = self.compile_test_params(testcase_params)

        # Prepare devices and run test
        self.setup_aware_rvr_test(testcase_params)
        rvr_result = self.run_rvr_test(testcase_params)
        self.cleanup_aware_rvr_test(testcase_params)

        # Post-process results
        self.testclass_results.append(rvr_result)
        self.process_test_results(rvr_result)
        self.pass_fail_check(rvr_result)

    def generate_test_cases(self, concurrency_list, traffic_type,
                            traffic_directions):
        """Function that auto-generates test cases for a test class."""
        test_cases = []

        for concurrency_state, traffic_direction in itertools.product(
                concurrency_list, traffic_directions):
            connection_string = '_'.join([str(x) for x in concurrency_state
                                          ]).replace('False', 'disconnected')
            test_name = 'test_aware_rvr_{}_{}_{}'.format(
                traffic_type, traffic_direction, connection_string)
            test_params = collections.OrderedDict(
                traffic_type=traffic_type,
                traffic_direction=traffic_direction,
                concurrency_state=concurrency_state)
            test_class = self.__class__.__name__
            if "uuid_list" in self.user_params:
                test_tracker_uuid = self.user_params["uuid_list"][
                    test_class][test_name]
                test_case = test_tracker_info(uuid=test_tracker_uuid)(
                    lambda: self._test_aware_rvr(test_params))
            else:
                test_case = partial(self._test_aware_rvr,test_params)
            setattr(self, test_name, test_case)
            test_cases.append(test_name)
        return test_cases


class WifiAwareRvr_FCC_TCP_Test(WifiAwareRvrTest):
    def __init__(self, controllers):
        super().__init__(controllers)
        concurrency_list = [[False, False], ['2G_1', False], ['5G_1', False],
                            ['2G_1', '2G_1'], ['5G_1',
                                               '5G_1'], ['2G_1', '5G_1'],
                            ['2G_1', '2G_2'], ['5G_1', '5G_2']]
        self.country_code = 'US'
        self.tests = self.generate_test_cases(
            concurrency_list=concurrency_list,
            traffic_type='TCP',
            traffic_directions=['DL', 'UL'])


class WifiAwareRvr_FCC_UDP_Test(WifiAwareRvrTest):
    def __init__(self, controllers):
        super().__init__(controllers)
        concurrency_list = [[False, False], ['2G_1', False], ['5G_1', False],
                            ['2G_1', '2G_1'], ['5G_1',
                                               '5G_1'], ['2G_1', '5G_1'],
                            ['2G_1', '2G_2'], ['5G_1', '5G_2']]
        self.country_code = 'US'
        self.tests = self.generate_test_cases(
            concurrency_list=concurrency_list,
            traffic_type='UDP',
            traffic_directions=['DL', 'UL'])


class WifiAwareRvr_ETSI_TCP_Test(WifiAwareRvrTest):
    def __init__(self, controllers):
        super().__init__(controllers)
        concurrency_list = [[False, False], ['2G_1', False], ['5G_1', False],
                            ['2G_1', '2G_1'], ['5G_1',
                                               '5G_1'], ['2G_1', '5G_1'],
                            ['2G_1', '2G_2'], ['5G_1', '5G_2']]
        self.country_code = 'GB'
        self.tests = self.generate_test_cases(
            concurrency_list=concurrency_list,
            traffic_type='TCP',
            traffic_directions=['DL', 'UL'])


class WifiAwareRvr_ETSI_UDP_Test(WifiAwareRvrTest):
    def __init__(self, controllers):
        super().__init__(controllers)
        concurrency_list = [[False, False], ['2G_1', False], ['5G_1', False],
                            ['2G_1', '2G_1'], ['5G_1',
                                               '5G_1'], ['2G_1', '5G_1'],
                            ['2G_1', '2G_2'], ['5G_1', '5G_2']]
        self.country_code = 'GB'
        self.tests = self.generate_test_cases(
            concurrency_list=concurrency_list,
            traffic_type='UDP',
            traffic_directions=['DL', 'UL'])
