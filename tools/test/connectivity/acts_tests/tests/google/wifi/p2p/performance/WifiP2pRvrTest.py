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
from functools import partial
import itertools
import logging
import os
import re
import time

from acts import asserts
from acts import base_test
from acts import utils
from acts.controllers import iperf_client as ipc
from acts.controllers import iperf_server as ipf
from acts.metrics.loggers.blackbox import BlackboxMappedMetricLogger
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi import ota_sniffer
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils
from acts_contrib.test_utils.wifi import wifi_retail_ap as retail_ap
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.p2p import wifi_p2p_const as p2pconsts
from acts_contrib.test_utils.wifi.p2p import wifi_p2p_test_utils as wp2putils
from WifiRvrTest import WifiRvrTest

AccessPointTuple = collections.namedtuple(('AccessPointTuple'),
                                          ['ap_settings'])


class WifiP2pRvrTest(WifiRvrTest):
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
        req_params = ['p2p_rvr_test_params', 'testbed_params']
        opt_params = [
            'RetailAccessPoints', 'ap_networks', 'OTASniffer', 'uuid_list'
        ]
        self.unpack_userparams(req_params, opt_params)
        if hasattr(self, 'RetailAccessPoints'):
            self.access_points = retail_ap.create(self.RetailAccessPoints)
            self.access_point = self.access_points[0]
        else:
            self.access_point = AccessPointTuple({})
        self.testclass_params = self.p2p_rvr_test_params
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
        if hasattr(self,
                   'OTASniffer') and self.testbed_params['sniffer_enable']:
            self.sniffer = ota_sniffer.create(self.OTASniffer)[0]
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
        for ad in self.android_devices:
            self.init_device(ad)

        # Configure test retries
        self.user_params['retry_tests'] = [self.__class__.__name__]

    def init_device(self, ad):
        asserts.assert_true(utils.force_airplane_mode(ad, False),
                            "Can not turn off airplane mode.")
        utils.set_location_service(ad, True)
        ad.droid.wifiScannerToggleAlwaysAvailable(False)
        asserts.assert_true(not ad.droid.wifiScannerIsAlwaysAvailable(),
                            "Failed to turn off location service's scan.")
        wutils.reset_wifi(ad)
        utils.sync_device_time(ad)
        ad.droid.telephonyToggleDataConnection(False)
        wutils.set_wifi_country_code(ad, self.country_code)
        ad.droid.wifiP2pInitialize()
        time.sleep(p2pconsts.DEFAULT_FUNCTION_SWITCH_TIME)
        asserts.assert_true(
            ad.droid.wifiP2pIsEnabled(),
            "{} p2p was not properly initialized".format(ad.serial))
        ad.name = "Android_" + ad.serial
        ad.droid.wifiP2pSetDeviceName(ad.name)

    def teardown_class(self):
        for ap in self.access_points:
            ap.teardown()
        # Turn WiFi OFF
        for ad in self.android_devices:
            ad.droid.wifiP2pClose()
            utils.set_location_service(ad, False)
            #wutils.wifi_toggle_state(ad, False)
        self.process_testclass_results()
        # Teardown AP and release its lockfile
        self.access_point.teardown()

    def setup_test(self):
        for ad in self.android_devices:
            wputils.start_wifi_logging(ad)
            ad.droid.wakeLockAcquireBright()
            ad.droid.wakeUpNow()
            ad.ed.clear_all_events()
            ad.droid.wifiP2pRemoveGroup()
        time.sleep(p2pconsts.DEFAULT_FUNCTION_SWITCH_TIME)

    def teardown_test(self):
        self.iperf_server.stop()
        for ad in self.android_devices:
            ad.droid.wifiP2pRemoveGroup()
        time.sleep(p2pconsts.DEFAULT_FUNCTION_SWITCH_TIME)
        for ad in self.android_devices:
            # Clear p2p group info
            ad.droid.wifiP2pRequestPersistentGroupInfo()
            event = ad.ed.pop_event("WifiP2pOnPersistentGroupInfoAvailable",
                                    p2pconsts.DEFAULT_TIMEOUT)
            for network in event['data']:
                ad.droid.wifiP2pDeletePersistentGroup(network['NetworkId'])
            # Clear p2p local service
            ad.droid.wifiP2pClearLocalServices()
            ad.droid.wakeLockRelease()
            ad.droid.goToSleepNow()
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
            wutils.set_wifi_country_code(ad, self.country_code)
        # Turn BT on or off
        bt_status = self.testclass_params.get('bluetooth_enabled', 1)
        self.log.info('Setting Bluetooth status to {}.'.format(bt_status))
        for ad in self.android_devices:
            ad.droid.bluetoothToggleState(bt_status)
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

    def get_p2p_mac_address(self, ad):
        """Gets the current MAC address being used for Wi-Fi Direct."""
        out = ad.adb.shell("ifconfig p2p0")
        return re.match(".* HWaddr (\S+).*", out, re.S).group(1)

    def _setup_p2p_connection_join_group(self, testcase_params):
        if self.testbed_params['sniffer_enable']:
            self.sniffer.start_capture(network={'SSID': 'dummy'},
                                       chan=11,
                                       bw=20,
                                       duration=180)
        # Create a group
        self.go_dut = self.android_devices[0]
        self.gc_dut = self.android_devices[1]
        wp2putils.p2p_create_group(self.go_dut)
        self.go_dut.ed.pop_event(p2pconsts.CONNECTED_EVENT,
                                 p2pconsts.DEFAULT_TIMEOUT)
        time.sleep(p2pconsts.DEFAULT_FUNCTION_SWITCH_TIME)
        # Request the connection
        try:
            wp2putils.p2p_connect(
                self.gc_dut,
                self.go_dut,
                isReconnect=False,
                p2p_connect_type=p2pconsts.P2P_CONNECT_JOIN,
                wpsSetup=wp2putils.WifiP2PEnums.WpsInfo.WIFI_WPS_INFO_PBC)
        except Exception as e:
            # Stop sniffer
            if self.testbed_params['sniffer_enable']:
                self.sniffer.stop_capture(tag='connection_setup')
            raise e
        if self.testbed_params['sniffer_enable']:
            self.sniffer.stop_capture(tag='connection_setup')

    def _setup_p2p_connection_negotiation(self, testcase_params):
        if self.testbed_params['sniffer_enable']:
            self.sniffer.start_capture(network={'SSID': 'dummy'},
                                       chan=11,
                                       bw=20,
                                       duration=180)
        try:
            wp2putils.p2p_connect(
                self.android_devices[0],
                self.android_devices[1],
                False,
                wpsSetup=wp2putils.WifiP2PEnums.WpsInfo.WIFI_WPS_INFO_PBC)
            if wp2putils.is_go(self.android_devices[0]):
                self.log.info("DUT 1 is GO.")
                self.go_dut = self.android_devices[0]
                self.gc_dut = self.android_devices[1]
            elif wp2putils.is_go(self.android_devices[1]):
                self.log.info("DUT 2 is GO.")
                self.go_dut = self.android_devices[1]
                self.gc_dut = self.android_devices[0]
        except Exception as e:
            # Stop sniffer
            if self.testbed_params['sniffer_enable']:
                self.sniffer.stop_capture(tag='connection_setup')
            raise e
        if self.testbed_params['sniffer_enable']:
            self.sniffer.stop_capture(tag='connection_setup')

    def _get_gc_ip(self, subnet_mask='255.255.255.0'):
        subnet_mask = ['255', '255', '255', '0']
        go_ip = wp2putils.p2p_go_ip(self.gc_dut)
        dut_subnet = [
            int(dut) & int(subnet)
            for dut, subnet in zip(go_ip.split('.'), subnet_mask)
        ]
        ifconfig_out = self.gc_dut.adb.shell('ifconfig')
        ip_list = re.findall('inet (?:addr:)?(\d+.\d+.\d+.\d+)', ifconfig_out)
        for current_ip in ip_list:
            current_subnet = [
                int(ip) & int(subnet)
                for ip, subnet in zip(current_ip.split('.'), subnet_mask)
            ]
            if current_subnet == dut_subnet:
                return current_ip
        logging.error('No IP address found in requested subnet')

    def setup_p2p_connection(self, testcase_params):
        """Sets up WiFi Direct connection before running RvR."""

        if self.testclass_params['p2p_group_negotiaton']:
            self._setup_p2p_connection_negotiation(testcase_params)
        else:
            self._setup_p2p_connection_join_group(testcase_params)

        # Get iperf server address
        if wp2putils.is_go(self.android_devices[0]):
            testcase_params['iperf_server_address'] = wp2putils.p2p_go_ip(
                self.gc_dut)
        else:
            testcase_params['iperf_server_address'] = self._get_gc_ip()

        p2p_interface = wp2putils.p2p_get_current_group(
            self.gc_dut)['Interface']
        connection_rssi = wputils.get_connected_rssi(self.gc_dut,
                                                     interface=p2p_interface)
        testcase_params['test_network'] = {'SSID': connection_rssi['ssid'][0]}
        testcase_params['channel'] = wutils.WifiEnums.freq_to_channel[
            connection_rssi['frequency'][0]]
        if testcase_params['channel'] < 13:
            testcase_params['mode'] = 'VHT20'
        else:
            testcase_params['mode'] = 'VHT80'
        self.log.info('Wifi Direct Connection Established on Channel {} {} '
                      '(SSID: {})'.format(
                          testcase_params['channel'], testcase_params['mode'],
                          testcase_params['test_network']['SSID']))

    def setup_p2p_rvr_test(self, testcase_params):
        # Setup the aps
        self.setup_aps(testcase_params)
        # Setup the duts
        self.setup_duts(testcase_params)
        # Set attenuator to 0 dB
        for attenuator in self.attenuators:
            attenuator.set_atten(0, strict=False)
        # Setup the p2p connection
        self.setup_p2p_connection(testcase_params)
        # Set DUT to monitor RSSI and LLStats on
        self.monitored_dut = self.gc_dut
        self.monitored_interface = wp2putils.p2p_get_current_group(
            self.gc_dut)['Interface']

    def cleanup_p2p_rvr_test(self, testcase_params):
        # clean-up
        wp2putils.p2p_disconnect(self.go_dut)
        wp2putils.check_disconnect(self.gc_dut)
        time.sleep(p2pconsts.DEFAULT_FUNCTION_SWITCH_TIME)

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
            traffic_type=testcase_params['traffic_type'],
            socket_size=testcase_params['iperf_socket_size'],
            num_processes=testcase_params['iperf_processes'],
            ipv6=False)
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

    def _test_p2p_rvr(self, testcase_params):
        """ Function that gets called for each test case

        Args:
            testcase_params: dict containing test-specific parameters
        """
        # Compile test parameters from config and test name
        testcase_params = self.compile_test_params(testcase_params)

        # Prepare devices and run test
        self.setup_p2p_rvr_test(testcase_params)
        rvr_result = self.run_rvr_test(testcase_params)
        self.cleanup_p2p_rvr_test(testcase_params)

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
            test_name = 'test_p2p_rvr_{}_{}_{}'.format(traffic_type,
                                                       traffic_direction,
                                                       connection_string)
            test_params = collections.OrderedDict(
                traffic_type=traffic_type,
                traffic_direction=traffic_direction,
                concurrency_state=concurrency_state)
            test_class = self.__class__.__name__
            if "uuid_list" in self.user_params:
                test_tracker_uuid = self.user_params["uuid_list"][
                    test_class][test_name]
                test_case = test_tracker_info(uuid=test_tracker_uuid)(
                    lambda: self._test_p2p_rvr(test_params))
            else:
                test_case = partial(self._test_p2p_rvr, test_params)
            setattr(self, test_name, test_case)
            test_cases.append(test_name)
        return test_cases


class WifiP2pRvr_FCC_TCP_Test(WifiP2pRvrTest):
    def __init__(self, controllers):
        super().__init__(controllers)
        concurrency_list = [[False, False], ['2G_1', False], ['5G_1', False],
                            [False, '2G_1'], [False, '5G_1'], ['2G_1', '2G_1'],
                            ['5G_1', '5G_1'], ['2G_1',
                                               '5G_1'], ['5G_1', '2G_1'],
                            ['2G_1', '2G_2'], ['5G_1', '5G_2']]
        self.country_code = 'US'
        self.tests = self.generate_test_cases(
            concurrency_list=concurrency_list,
            traffic_type='TCP',
            traffic_directions=['DL', 'UL'])


class WifiP2pRvr_FCC_UDP_Test(WifiP2pRvrTest):
    def __init__(self, controllers):
        super().__init__(controllers)
        concurrency_list = [[False, False], ['2G_1', False], ['5G_1', False],
                            [False, '2G_1'], [False, '5G_1'], ['2G_1', '2G_1'],
                            ['5G_1', '5G_1'], ['2G_1',
                                               '5G_1'], ['5G_1', '2G_1'],
                            ['2G_1', '2G_2'], ['5G_1', '5G_2']]
        self.country_code = 'US'
        self.tests = self.generate_test_cases(
            concurrency_list=concurrency_list,
            traffic_type='UDP',
            traffic_directions=['DL', 'UL'])


class WifiP2pRvr_ETSI_TCP_Test(WifiP2pRvrTest):
    def __init__(self, controllers):
        super().__init__(controllers)
        concurrency_list = [[False, False], ['2G_1', False], ['5G_1', False],
                            [False, '2G_1'], [False, '5G_1'], ['2G_1', '2G_1'],
                            ['5G_1', '5G_1'], ['2G_1',
                                               '5G_1'], ['5G_1', '2G_1'],
                            ['2G_1', '2G_2'], ['5G_1', '5G_2']]
        self.country_code = 'GB'
        self.tests = self.generate_test_cases(
            concurrency_list=concurrency_list,
            traffic_type='TCP',
            traffic_directions=['DL', 'UL'])


class WifiP2pRvr_ETSI_UDP_Test(WifiP2pRvrTest):
    def __init__(self, controllers):
        super().__init__(controllers)
        concurrency_list = [[False, False], ['2G_1', False], ['5G_1', False],
                            [False, '2G_1'], [False, '5G_1'], ['2G_1', '2G_1'],
                            ['5G_1', '5G_1'], ['2G_1',
                                               '5G_1'], ['5G_1', '2G_1'],
                            ['2G_1', '2G_2'], ['5G_1', '5G_2']]
        self.country_code = 'GB'
        self.tests = self.generate_test_cases(
            concurrency_list=concurrency_list,
            traffic_type='UDP',
            traffic_directions=['DL', 'UL'])
