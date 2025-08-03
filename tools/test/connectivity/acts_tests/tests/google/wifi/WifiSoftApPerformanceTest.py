#!/usr/bin/env python3.4
#
#   Copyright 2018 - The Android Open Source Project
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
import logging
import os
from acts import asserts
from acts import base_test
from acts.controllers import iperf_server as ipf
from acts.controllers import iperf_client as ipc
from acts.metrics.loggers.blackbox import BlackboxMappedMetricLogger
from acts_contrib.test_utils.wifi import ota_sniffer
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils
from acts_contrib.test_utils.wifi import wifi_retail_ap as retail_ap
from WifiRvrTest import WifiRvrTest

AccessPointTuple = collections.namedtuple(('AccessPointTuple'),
                                          ['ap_settings'])


class WifiSoftApRvrTest(WifiRvrTest):
    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.tests = ('test_rvr_TCP_DL_2GHz', 'test_rvr_TCP_UL_2GHz',
                      'test_rvr_TCP_DL_5GHz', 'test_rvr_TCP_UL_5GHz',
                      'test_rvr_TCP_DL_2GHz_backhaul_2GHz',
                      'test_rvr_TCP_UL_2GHz_backhaul_2GHz',
                      'test_rvr_TCP_DL_5GHz_backhaul_2GHz',
                      'test_rvr_TCP_UL_5GHz_backhaul_2GHz',
                      'test_rvr_TCP_DL_2GHz_backhaul_5GHz',
                      'test_rvr_TCP_UL_2GHz_backhaul_5GHz',
                      'test_rvr_TCP_DL_5GHz_backhaul_5GHz',
                      'test_rvr_TCP_UL_5GHz_backhaul_5GHz')
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
            'sap_test_params', 'testbed_params', 'RetailAccessPoints',
            'ap_networks'
        ]
        opt_params = ['golden_files_list', 'OTASniffer']
        self.unpack_userparams(req_params, opt_params)
        self.access_points = retail_ap.create(self.RetailAccessPoints)
        self.testclass_params = self.sap_test_params
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
            try:
                self.sniffer = ota_sniffer.create(self.OTASniffer)[0]
            except:
                self.log.warning('Could not start sniffer. Disabling sniffs.')
                self.testbed_params['sniffer_enable'] = 0

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
        for dev in self.android_devices:
            wutils.wifi_toggle_state(dev, True)

    def teardown_class(self):
        # Turn WiFi OFF
        wutils.stop_wifi_tethering(self.android_devices[0])
        for dev in self.android_devices:
            wutils.wifi_toggle_state(dev, False)
            dev.go_to_sleep()
        self.process_testclass_results()
        # Teardown AP and release it's lockfile
        for ap in self.access_points:
            ap.teardown()

    def teardown_test(self):
        self.iperf_server.stop()
        wutils.stop_wifi_tethering(self.android_devices[0])

    def get_sap_connection_info(self):
        info = {}
        info['client_ip_address'] = self.android_devices[
            1].droid.connectivityGetIPv4Addresses('wlan0')[0]
        ifconfig_out = self.android_devices[0].adb.shell('ifconfig')
        soft_ap_interface = 'wlan1' if 'wlan1' in ifconfig_out else 'wlan2'
        info['ap_ip_address'] = self.android_devices[
            0].droid.connectivityGetIPv4Addresses(soft_ap_interface)[0]

        connection_rssi = wputils.get_connected_rssi(self.android_devices[1],
                                                     interface='wlan0')
        info['frequency'] = connection_rssi['frequency'][0]
        info['channel'] = wutils.WifiEnums.freq_to_channel[int(
            info['frequency'])]
        info['mode'] = 'bw20' if info['channel'] < 13 else 'bw80'
        return info

    def setup_aps(self, testcase_params):
        for network in testcase_params['ap_networks']:
            self.log.info('Setting AP {} {} interface on channel {}'.format(
                network['ap_id'], network['interface_id'], network['channel']))
            self.access_points[network['ap_id']].set_channel(
                network['interface_id'], network['channel'])

    def setup_duts(self, testcase_params):
        """Function that gets devices ready for the test.

        Args:
            testcase_params: dict containing test-specific parameters
        """
        self.ap_dut = self.android_devices[0]
        self.sta_dut = self.android_devices[1]
        for dev in self.android_devices:
            if not wputils.health_check(dev, 20):
                asserts.skip('DUT health check failed. Skipping test.')
        # Reset WiFi on all devices
        for dev in self.android_devices:
            dev.go_to_sleep()
            wutils.reset_wifi(dev)
            wutils.set_wifi_country_code(dev, wutils.WifiEnums.CountryCode.US)

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

    def setup_sap_connection(self, testcase_params):
        # Setup Soft AP
        sap_config = wutils.create_softap_config()
        self.log.info('SoftAP Config: {}'.format(sap_config))
        wutils.start_wifi_tethering(self.android_devices[0],
                                    sap_config[wutils.WifiEnums.SSID_KEY],
                                    sap_config[wutils.WifiEnums.PWD_KEY],
                                    testcase_params['sap_band_enum'])
        # Connect DUT to Network
        testcase_params['test_network'] = {
            'SSID': sap_config[wutils.WifiEnums.SSID_KEY],
            'password': sap_config[wutils.WifiEnums.PWD_KEY]
        }
        wutils.wifi_connect(self.sta_dut,
                            testcase_params['test_network'],
                            num_of_tries=5,
                            check_connectivity=False)
        # Compile meta data
        sap_info = self.get_sap_connection_info()
        print("SAP Info: {}".format(sap_info))
        testcase_params['channel'] = sap_info['channel']
        if testcase_params['channel'] < 13:
            testcase_params['band'] = '2GHz'
        else:
            testcase_params['band'] = '5GHz'
        testcase_params['mode'] = sap_info['mode']
        self.access_point = AccessPointTuple({
            testcase_params['band']: {
                'SSID': sap_config[wutils.WifiEnums.SSID_KEY],
                'password': sap_config[wutils.WifiEnums.PWD_KEY],
                'channel': sap_info['channel'],
                'mode': sap_info['mode'],
                'bandwidth': sap_info['mode'],
            }
        })
        testcase_params['iperf_server_address'] = sap_info['ap_ip_address']

    def setup_sap_rvr_test(self, testcase_params):
        """Function that gets devices ready for the test.

        Args:
            testcase_params: dict containing test-specific parameters
        """
        # Configure DUTs
        self.setup_aps(testcase_params)
        # Set attenuator to 0 dB
        for attenuator in self.attenuators:
            attenuator.set_atten(0, strict=False)
        # Configure DUTs
        self.setup_duts(testcase_params)
        # Setup sap connection
        self.setup_sap_connection(testcase_params)
        # Set DUT to monitor RSSI and LLStats on
        self.monitored_dut = self.sta_dut
        self.monitored_interface = 'wlan0'

    def compile_test_params(self, testcase_params):
        """Function that completes all test params based on the test name.

        Args:
            testcase_params: dict containing test-specific parameters
        """
        num_atten_steps = int((self.testclass_params['atten_stop'] -
                               self.testclass_params['atten_start']) /
                              self.testclass_params['atten_step'])
        testcase_params['atten_range'] = [
            self.testclass_params['atten_start'] +
            x * self.testclass_params['atten_step']
            for x in range(0, num_atten_steps)
        ]

        if testcase_params['traffic_direction'] == 'DL':
            testcase_params['iperf_args'] = wputils.get_iperf_arg_string(
                duration=self.testclass_params['iperf_duration'],
                reverse_direction=1,
                traffic_type=testcase_params['traffic_type'])
            testcase_params['use_client_output'] = True
        else:
            testcase_params['iperf_args'] = wputils.get_iperf_arg_string(
                duration=self.testclass_params['iperf_duration'],
                reverse_direction=0,
                traffic_type=testcase_params['traffic_type'])
            testcase_params['use_client_output'] = False

        # Compile AP and infrastructure connection parameters
        ap_networks = []
        if testcase_params['dut_connected'][0]:
            band = testcase_params['dut_connected'][0].split('_')[0]
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

        if testcase_params['dut_connected'][1]:
            if testcase_params['dut_connected'][0] == testcase_params[
                    'dut_connected'][1]:
                # if connected to same network, add it to the above
                ap_networks[0]['connected_dut'].append(1)
            else:
                band = testcase_params['dut_connected'][1].split('_')[0]
                if not testcase_params['dut_connected'][0]:
                    # if it's the only dut connected, assign it to ap 0
                    ap_id = 0
                else:
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

    def _test_sap_rvr(self, testcase_params):
        """ Function that gets called for each test case

        Args:
            testcase_params: dict containing test-specific parameters
        """
        # Compile test parameters from config and test name
        testcase_params = self.compile_test_params(testcase_params)

        self.setup_sap_rvr_test(testcase_params)
        result = self.run_rvr_test(testcase_params)
        self.testclass_results.append(result)
        self.process_test_results(result)
        self.pass_fail_check(result)

    #Test cases
    def test_rvr_TCP_DL_2GHz(self):
        testcase_params = collections.OrderedDict(
            sap_band='2GHz',
            sap_band_enum=wutils.WifiEnums.WIFI_CONFIG_APBAND_2G,
            traffic_type='TCP',
            traffic_direction='DL',
            dut_connected=[False, False])
        self._test_sap_rvr(testcase_params)

    def test_rvr_TCP_UL_2GHz(self):
        testcase_params = collections.OrderedDict(
            sap_band='2GHz',
            sap_band_enum=wutils.WifiEnums.WIFI_CONFIG_APBAND_2G,
            traffic_type='TCP',
            traffic_direction='UL',
            dut_connected=[False, False])
        self._test_sap_rvr(testcase_params)

    def test_rvr_TCP_DL_5GHz(self):
        testcase_params = collections.OrderedDict(
            sap_band='5GHz',
            sap_band_enum=wutils.WifiEnums.WIFI_CONFIG_APBAND_5G,
            traffic_type='TCP',
            traffic_direction='DL',
            dut_connected=[False, False])
        self._test_sap_rvr(testcase_params)

    def test_rvr_TCP_UL_5GHz(self):
        testcase_params = collections.OrderedDict(
            sap_band='5GHz',
            sap_band_enum=wutils.WifiEnums.WIFI_CONFIG_APBAND_5G,
            traffic_type='TCP',
            traffic_direction='UL',
            dut_connected=[False, False])
        self._test_sap_rvr(testcase_params)

    def test_rvr_TCP_DL_2GHz_backhaul_2GHz(self):
        testcase_params = collections.OrderedDict(
            sap_band='2GHz',
            sap_band_enum=wutils.WifiEnums.WIFI_CONFIG_APBAND_2G,
            traffic_type='TCP',
            traffic_direction='DL',
            dut_connected=['2G_1', False])
        self._test_sap_rvr(testcase_params)

    def test_rvr_TCP_UL_2GHz_backhaul_2GHz(self):
        testcase_params = collections.OrderedDict(
            sap_band='2GHz',
            sap_band_enum=wutils.WifiEnums.WIFI_CONFIG_APBAND_2G,
            traffic_type='TCP',
            traffic_direction='UL',
            dut_connected=['2G_1', False])
        self._test_sap_rvr(testcase_params)

    def test_rvr_TCP_DL_5GHz_backhaul_2GHz(self):
        testcase_params = collections.OrderedDict(
            sap_band='5GHz',
            sap_band_enum=wutils.WifiEnums.WIFI_CONFIG_APBAND_5G,
            traffic_type='TCP',
            traffic_direction='DL',
            dut_connected=['2G_1', False])
        self._test_sap_rvr(testcase_params)

    def test_rvr_TCP_UL_5GHz_backhaul_2GHz(self):
        testcase_params = collections.OrderedDict(
            sap_band='5GHz',
            sap_band_enum=wutils.WifiEnums.WIFI_CONFIG_APBAND_5G,
            traffic_type='TCP',
            traffic_direction='UL',
            dut_connected=['2G_1', False])
        self._test_sap_rvr(testcase_params)

    def test_rvr_TCP_DL_2GHz_backhaul_5GHz(self):
        testcase_params = collections.OrderedDict(
            sap_band='2GHz',
            sap_band_enum=wutils.WifiEnums.WIFI_CONFIG_APBAND_2G,
            traffic_type='TCP',
            traffic_direction='DL',
            dut_connected=['5G_1', False])
        self._test_sap_rvr(testcase_params)

    def test_rvr_TCP_UL_2GHz_backhaul_5GHz(self):
        testcase_params = collections.OrderedDict(
            sap_band='2GHz',
            sap_band_enum=wutils.WifiEnums.WIFI_CONFIG_APBAND_2G,
            traffic_type='TCP',
            traffic_direction='UL',
            dut_connected=['5G_1', False])
        self._test_sap_rvr(testcase_params)

    def test_rvr_TCP_DL_5GHz_backhaul_5GHz(self):
        testcase_params = collections.OrderedDict(
            sap_band='5GHz',
            sap_band_enum=wutils.WifiEnums.WIFI_CONFIG_APBAND_5G,
            traffic_type='TCP',
            traffic_direction='DL',
            dut_connected=['5G_1', False])
        self._test_sap_rvr(testcase_params)

    def test_rvr_TCP_UL_5GHz_backhaul_5GHz(self):
        testcase_params = collections.OrderedDict(
            sap_band='5GHz',
            sap_band_enum=wutils.WifiEnums.WIFI_CONFIG_APBAND_5G,
            traffic_type='TCP',
            traffic_direction='UL',
            dut_connected=['5G_1', False])
        self._test_sap_rvr(testcase_params)
