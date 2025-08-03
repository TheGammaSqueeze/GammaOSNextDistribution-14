#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import os
import time

from statistics import pstdev

from bokeh.plotting import ColumnDataSource
from bokeh.plotting import figure
from bokeh.plotting import output_file
from bokeh.plotting import save

from acts import asserts
from acts import context
from acts import utils
from acts.controllers.access_point import setup_ap
from acts.controllers.ap_lib import hostapd_config
from acts.controllers.ap_lib import hostapd_constants
from acts.controllers.ap_lib.hostapd_security import Security
from acts.controllers.iperf_server import IPerfResult
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device

N_CAPABILITIES_DEFAULT = [
    hostapd_constants.N_CAPABILITY_LDPC, hostapd_constants.N_CAPABILITY_SGI20,
    hostapd_constants.N_CAPABILITY_SGI40,
    hostapd_constants.N_CAPABILITY_TX_STBC,
    hostapd_constants.N_CAPABILITY_RX_STBC1
]

AC_CAPABILITIES_DEFAULT = [
    hostapd_constants.AC_CAPABILITY_MAX_MPDU_11454,
    hostapd_constants.AC_CAPABILITY_RXLDPC,
    hostapd_constants.AC_CAPABILITY_SHORT_GI_80,
    hostapd_constants.AC_CAPABILITY_TX_STBC_2BY1,
    hostapd_constants.AC_CAPABILITY_RX_STBC_1,
    hostapd_constants.AC_CAPABILITY_MAX_A_MPDU_LEN_EXP7,
    hostapd_constants.AC_CAPABILITY_RX_ANTENNA_PATTERN,
    hostapd_constants.AC_CAPABILITY_TX_ANTENNA_PATTERN
]

DEFAULT_MIN_THROUGHPUT = 0
DEFAULT_MAX_STD_DEV = 1
DEFAULT_IPERF_TIMEOUT = 30

DEFAULT_TIME_TO_WAIT_FOR_IP_ADDR = 30
GRAPH_CIRCLE_SIZE = 10
IPERF_NO_THROUGHPUT_VALUE = 0
MAX_2_4_CHANNEL = 14
TIME_TO_SLEEP_BETWEEN_RETRIES = 1
TIME_TO_WAIT_FOR_COUNTRY_CODE = 10
WEP_HEX_STRING_LENGTH = 10

MEGABITS_PER_SECOND = 'Mbps'


def get_test_name(settings):
    """Retrieves the test_name value from test_settings"""
    return settings.get('test_name')


class ChannelSweepTest(WifiBaseTest):
    """Tests channel performance and regulatory compliance..

    Testbed Requirement:
    * One ACTS compatible device (dut)
    * One Access Point
    * One Linux Machine used as IPerfServer if running performance tests
    Note: Performance tests should be done in isolated testbed.
    """

    def __init__(self, controllers):
        super().__init__(controllers)
        if 'channel_sweep_test_params' in self.user_params:
            self.time_to_wait_for_ip_addr = self.user_params[
                'channel_sweep_test_params'].get(
                    'time_to_wait_for_ip_addr',
                    DEFAULT_TIME_TO_WAIT_FOR_IP_ADDR)
        else:
            self.time_to_wait_for_ip_addr = DEFAULT_TIME_TO_WAIT_FOR_IP_ADDR

    def setup_class(self):
        super().setup_class()
        if 'dut' in self.user_params:
            if self.user_params['dut'] == 'fuchsia_devices':
                self.dut = create_wlan_device(self.fuchsia_devices[0])
            elif self.user_params['dut'] == 'android_devices':
                self.dut = create_wlan_device(self.android_devices[0])
            else:
                raise ValueError('Invalid DUT specified in config. (%s)' %
                                 self.user_params['dut'])
        else:
            # Default is an android device, just like the other tests
            self.dut = create_wlan_device(self.android_devices[0])

        self.android_devices = getattr(self, 'android_devices', [])

        self.access_point = self.access_points[0]
        self.access_point.stop_all_aps()

        self.iperf_server = None
        self.iperf_client = None

        self.channel_sweep_test_params = self.user_params.get(
            'channel_sweep_test_params', {})
        # Allows users to skip the iperf throughput measurements, just verifying
        # association.
        if not self.channel_sweep_test_params.get('skip_performance'):
            try:
                self.iperf_server = self.iperf_servers[0]
                self.iperf_server.start()
            except AttributeError:
                self.log.warn(
                    'Missing iperf config. Throughput cannot be measured, so only '
                    'association will be tested.')

            if hasattr(self, "iperf_clients") and self.iperf_clients:
                self.iperf_client = self.iperf_clients[0]
            else:
                self.iperf_client = self.dut.create_iperf_client()

        self.regulatory_results = "====CountryCode,Channel,Frequency,ChannelBandwith,Connected/Not-Connected====\n"

    def teardown_class(self):
        super().teardown_class()
        output_path = context.get_current_context().get_base_output_path()
        regulatory_save_path = '%s/ChannelSweepTest/%s' % (
            output_path, "regulatory_results.txt")
        f = open(regulatory_save_path, "w")
        f.write(self.regulatory_results)
        f.close()

    def setup_test(self):
        # TODO(fxb/46417): Uncomment when wlanClearCountry is implemented up any
        # country code changes.
        # for fd in self.fuchsia_devices:
        #     phy_ids_response = fd.wlan_lib.wlanPhyIdList()
        #     if phy_ids_response.get('error'):
        #         raise ConnectionError(
        #             'Failed to retrieve phy ids from FuchsiaDevice (%s). '
        #             'Error: %s' % (fd.ip, phy_ids_response['error']))
        #     for id in phy_ids_response['result']:
        #         clear_country_response = fd.wlan_lib.wlanClearCountry(id)
        #         if clear_country_response.get('error'):
        #             raise EnvironmentError(
        #                 'Failed to reset country code on FuchsiaDevice (%s). '
        #                 'Error: %s' % (fd.ip, clear_country_response['error'])
        #                 )
        self.access_point.stop_all_aps()
        for ad in self.android_devices:
            ad.droid.wakeLockAcquireBright()
            ad.droid.wakeUpNow()
        self.dut.wifi_toggle_state(True)
        self.dut.disconnect()

    def teardown_test(self):
        for ad in self.android_devices:
            ad.droid.wakeLockRelease()
            ad.droid.goToSleepNow()
        self.dut.turn_location_off_and_scan_toggle_off()
        self.dut.disconnect()
        self.download_ap_logs()
        self.access_point.stop_all_aps()

    def set_dut_country_code(self, country_code):
        """Set the country code on the DUT. Then verify that the country
        code was set successfully

        Args:
            country_code: string, the 2 character country code to set
        """
        self.log.info('Setting DUT country code to %s' % country_code)
        country_code_response = self.dut.device.sl4f.regulatory_region_lib.setRegion(
            country_code)
        if country_code_response.get('error'):
            raise EnvironmentError(
                'Failed to set country code (%s) on DUT. Error: %s' %
                (country_code, country_code_response['error']))

        self.log.info('Verifying DUT country code was correctly set to %s.' %
                      country_code)
        phy_ids_response = self.dut.device.sl4f.wlan_lib.wlanPhyIdList()
        if phy_ids_response.get('error'):
            raise ConnectionError('Failed to get phy ids from DUT. Error: %s' %
                                  (country_code, phy_ids_response['error']))

        end_time = time.time() + TIME_TO_WAIT_FOR_COUNTRY_CODE
        while time.time() < end_time:
            for id in phy_ids_response['result']:
                get_country_response = self.dut.device.sl4f.wlan_lib.wlanGetCountry(
                    id)
                if get_country_response.get('error'):
                    raise ConnectionError(
                        'Failed to query PHY ID (%s) for country. Error: %s' %
                        (id, get_country_response['error']))

                set_code = ''.join([
                    chr(ascii_char)
                    for ascii_char in get_country_response['result']
                ])
                if set_code != country_code:
                    self.log.debug(
                        'PHY (id: %s) has incorrect country code set. '
                        'Expected: %s, Got: %s' % (id, country_code, set_code))
                    break
            else:
                self.log.info('All PHYs have expected country code (%s)' %
                              country_code)
                break
            time.sleep(TIME_TO_SLEEP_BETWEEN_RETRIES)
        else:
            raise EnvironmentError('Failed to set DUT country code to %s.' %
                                   country_code)

    def setup_ap(self, channel, channel_bandwidth, security_profile=None):
        """Start network on AP with basic configuration.

        Args:
            channel: int, channel to use for network
            channel_bandwidth: int, channel bandwidth in mhz to use for network,
            security_profile: Security object, or None if open

        Returns:
            string, ssid of network running

        Raises:
            ConnectionError if network is not started successfully.
        """
        if channel > MAX_2_4_CHANNEL:
            vht_bandwidth = channel_bandwidth
        else:
            vht_bandwidth = None

        if channel_bandwidth == hostapd_constants.CHANNEL_BANDWIDTH_20MHZ:
            n_capabilities = N_CAPABILITIES_DEFAULT + [
                hostapd_constants.N_CAPABILITY_HT20
            ]
        elif (channel_bandwidth == hostapd_constants.CHANNEL_BANDWIDTH_40MHZ or
              channel_bandwidth == hostapd_constants.CHANNEL_BANDWIDTH_80MHZ):
            if hostapd_config.ht40_plus_allowed(channel):
                extended_channel = [hostapd_constants.N_CAPABILITY_HT40_PLUS]
            elif hostapd_config.ht40_minus_allowed(channel):
                extended_channel = [hostapd_constants.N_CAPABILITY_HT40_MINUS]
            else:
                raise ValueError('Invalid Channel: %s' % channel)
            n_capabilities = N_CAPABILITIES_DEFAULT + extended_channel
        else:
            raise ValueError('Invalid Bandwidth: %s' % channel_bandwidth)
        ssid = utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G)
        try:
            setup_ap(access_point=self.access_point,
                     profile_name='whirlwind',
                     channel=channel,
                     security=security_profile,
                     n_capabilities=n_capabilities,
                     ac_capabilities=None,
                     force_wmm=True,
                     ssid=ssid,
                     vht_bandwidth=vht_bandwidth,
                     setup_bridge=True)
        except Exception as err:
            raise ConnectionError(
                'Failed to setup ap on channel: %s, channel bandwidth: %smhz. '
                'Error: %s' % (channel, channel_bandwidth, err))
        else:
            self.log.info(
                'Network (ssid: %s) up on channel %s w/ channel bandwidth %smhz'
                % (ssid, channel, channel_bandwidth))

        return ssid

    def get_and_verify_iperf_address(self, channel, device, interface=None):
        """Get ip address from a devices interface and verify it belongs to
        expected subnet based on APs DHCP config.

        Args:
            channel: int, channel network is running on, to determine subnet
            device: device to get ip address for
            interface (default: None): interface on device to get ip address.
                If None, uses device.test_interface.

        Returns:
            String, ip address of device on given interface (or test_interface)

        Raises:
            ConnectionError, if device does not have a valid ip address after
                all retries.
        """
        if channel <= MAX_2_4_CHANNEL:
            subnet = self.access_point._AP_2G_SUBNET_STR
        else:
            subnet = self.access_point._AP_5G_SUBNET_STR
        end_time = time.time() + self.time_to_wait_for_ip_addr
        while time.time() < end_time:
            if interface:
                device_addresses = device.get_interface_ip_addresses(interface)
            else:
                device_addresses = device.get_interface_ip_addresses(
                    device.test_interface)

            if device_addresses['ipv4_private']:
                for ip_addr in device_addresses['ipv4_private']:
                    if utils.ip_in_subnet(ip_addr, subnet):
                        return ip_addr
                    else:
                        self.log.debug(
                            'Device has an ip address (%s), but it is not in '
                            'subnet %s' % (ip_addr, subnet))
            else:
                self.log.debug(
                    'Device does not have a valid ip address. Retrying.')
            time.sleep(TIME_TO_SLEEP_BETWEEN_RETRIES)
        raise ConnectionError('Device failed to get an ip address.')

    def get_iperf_throughput(self,
                             iperf_server_address,
                             iperf_client_address,
                             reverse=False):
        """Run iperf between client and server and get the throughput.

        Args:
            iperf_server_address: string, ip address of running iperf server
            iperf_client_address: string, ip address of iperf client (dut)
            reverse (default: False): If True, run traffic in reverse direction,
                from server to client.

        Returns:
            int, iperf throughput OR IPERF_NO_THROUGHPUT_VALUE, if iperf fails
        """
        if reverse:
            self.log.info(
                'Running IPerf traffic from server (%s) to dut (%s).' %
                (iperf_server_address, iperf_client_address))
            iperf_results_file = self.iperf_client.start(
                iperf_server_address,
                '-i 1 -t 10 -R -J',
                'channel_sweep_rx',
                timeout=DEFAULT_IPERF_TIMEOUT)
        else:
            self.log.info(
                'Running IPerf traffic from dut (%s) to server (%s).' %
                (iperf_client_address, iperf_server_address))
            iperf_results_file = self.iperf_client.start(
                iperf_server_address,
                '-i 1 -t 10 -J',
                'channel_sweep_tx',
                timeout=DEFAULT_IPERF_TIMEOUT)
        if iperf_results_file:
            iperf_results = IPerfResult(
                iperf_results_file, reporting_speed_units=MEGABITS_PER_SECOND)
            return iperf_results.avg_send_rate
        else:
            return IPERF_NO_THROUGHPUT_VALUE

    def log_to_file_and_throughput_data(self, channel, channel_bandwidth,
                                        tx_throughput, rx_throughput):
        """Write performance info to csv file and to throughput data.

        Args:
            channel: int, channel that test was run on
            channel_bandwidth: int, channel bandwidth the test used
            tx_throughput: float, throughput value from dut to iperf server
            rx_throughput: float, throughput value from iperf server to dut
        """
        test_name = self.throughput_data['test']
        output_path = context.get_current_context().get_base_output_path()
        log_path = '%s/ChannelSweepTest/%s' % (output_path, test_name)
        if not os.path.exists(log_path):
            os.makedirs(log_path)
        log_file = '%s/%s_%smhz.csv' % (log_path, test_name, channel_bandwidth)
        self.log.info('Writing IPerf results for %s to %s' %
                      (test_name, log_file))
        with open(log_file, 'a') as csv_file:
            csv_file.write('%s,%s,%s\n' %
                           (channel, tx_throughput, rx_throughput))
        self.throughput_data['results'][str(channel)] = {
            'tx_throughput': tx_throughput,
            'rx_throughput': rx_throughput
        }

    def write_graph(self):
        """Create graph html files from throughput data, plotting channel vs
        tx_throughput and channel vs rx_throughput.
        """
        # If performance measurement is skipped
        if not self.iperf_server:
            return
        output_path = context.get_current_context().get_base_output_path()
        test_name = self.throughput_data['test']
        channel_bandwidth = self.throughput_data['channel_bandwidth']
        output_file_name = '%s/ChannelSweepTest/%s/%s_%smhz.html' % (
            output_path, test_name, test_name, channel_bandwidth)
        output_file(output_file_name)
        channels = []
        tx_throughputs = []
        rx_throughputs = []
        for channel in self.throughput_data['results']:
            channels.append(str(channel))
            tx_throughputs.append(
                self.throughput_data['results'][channel]['tx_throughput'])
            rx_throughputs.append(
                self.throughput_data['results'][channel]['rx_throughput'])
        channel_vs_throughput_data = ColumnDataSource(
            data=dict(channels=channels,
                      tx_throughput=tx_throughputs,
                      rx_throughput=rx_throughputs))
        TOOLTIPS = [('Channel', '@channels'),
                    ('TX_Throughput', '@tx_throughput'),
                    ('RX_Throughput', '@rx_throughput')]
        channel_vs_throughput_graph = figure(title='Channels vs. Throughput',
                                             x_axis_label='Channels',
                                             x_range=channels,
                                             y_axis_label='Throughput',
                                             tooltips=TOOLTIPS)
        channel_vs_throughput_graph.sizing_mode = 'stretch_both'
        channel_vs_throughput_graph.title.align = 'center'
        channel_vs_throughput_graph.line('channels',
                                         'tx_throughput',
                                         source=channel_vs_throughput_data,
                                         line_width=2,
                                         line_color='blue',
                                         legend_label='TX_Throughput')
        channel_vs_throughput_graph.circle('channels',
                                           'tx_throughput',
                                           source=channel_vs_throughput_data,
                                           size=GRAPH_CIRCLE_SIZE,
                                           color='blue')
        channel_vs_throughput_graph.line('channels',
                                         'rx_throughput',
                                         source=channel_vs_throughput_data,
                                         line_width=2,
                                         line_color='red',
                                         legend_label='RX_Throughput')
        channel_vs_throughput_graph.circle('channels',
                                           'rx_throughput',
                                           source=channel_vs_throughput_data,
                                           size=GRAPH_CIRCLE_SIZE,
                                           color='red')

        channel_vs_throughput_graph.legend.location = "top_left"
        graph_file = save([channel_vs_throughput_graph])
        self.log.info('Saved graph to %s' % graph_file)

    def verify_standard_deviation(self, max_std_dev):
        """Verifies the standard deviation of the throughput across the channels
        does not exceed the max_std_dev value.

        Args:
            max_std_dev: float, max standard deviation of throughput for a test
                to pass (in Mb/s)

        Raises:
            TestFailure, if standard deviation of throughput exceeds max_std_dev
        """
        # If performance measurement is skipped
        if not self.iperf_server:
            return
        self.log.info('Verifying standard deviation across channels does not '
                      'exceed max standard deviation of %s Mb/s' % max_std_dev)
        tx_values = []
        rx_values = []
        for channel in self.throughput_data['results']:
            if self.throughput_data['results'][channel][
                    'tx_throughput'] is not None:
                tx_values.append(
                    self.throughput_data['results'][channel]['tx_throughput'])
            if self.throughput_data['results'][channel][
                    'rx_throughput'] is not None:
                rx_values.append(
                    self.throughput_data['results'][channel]['rx_throughput'])
        tx_std_dev = pstdev(tx_values)
        rx_std_dev = pstdev(rx_values)
        if tx_std_dev > max_std_dev or rx_std_dev > max_std_dev:
            asserts.fail(
                'With %smhz channel bandwidth, throughput standard '
                'deviation (tx: %s Mb/s, rx: %s Mb/s) exceeds max standard '
                'deviation (%s Mb/s).' %
                (self.throughput_data['channel_bandwidth'], tx_std_dev,
                 rx_std_dev, max_std_dev))
        else:
            asserts.explicit_pass(
                'Throughput standard deviation (tx: %s Mb/s, rx: %s Mb/s) '
                'with %smhz channel bandwidth does not exceed maximum (%s Mb/s).'
                % (tx_std_dev, rx_std_dev,
                   self.throughput_data['channel_bandwidth'], max_std_dev))

    def run_channel_performance_tests(self, settings):
        """Test function for running channel performance tests. Used by both
        explicit test cases and debug test cases from config. Runs a performance
        test for each channel in test_channels with test_channel_bandwidth, then
        writes a graph and csv file of the channel vs throughput.

        Args:
            settings: dict, containing the following test settings
                test_channels: list of channels to test.
                test_channel_bandwidth: int, channel bandwidth to use for test.
                test_security (optional): string, security type to use for test.
                min_tx_throughput (optional, default: 0): float, minimum tx
                    throughput threshold to pass individual channel tests
                    (in Mb/s).
                min_rx_throughput (optional, default: 0): float, minimum rx
                    throughput threshold to pass individual channel tests
                    (in Mb/s).
                max_std_dev (optional, default: 1): float, maximum standard
                    deviation of throughput across all test channels to pass
                    test (in Mb/s).
                base_test_name (optional): string, test name prefix to use with
                    generated subtests.
                country_name (optional): string, country name from
                    hostapd_constants to set on device.
                country_code (optional): string, two-char country code to set on
                    the DUT. Takes priority over country_name.
                test_name (debug tests only): string, the test name for this
                    parent test case from the config file. In explicit tests,
                    this is not necessary.

        Writes:
            CSV file: channel, tx_throughput, rx_throughput
                for every test channel.
            Graph: channel vs tx_throughput & channel vs rx_throughput

        Raises:
            TestFailure, if throughput standard deviation across channels
                exceeds max_std_dev

            Example Explicit Test (see EOF for debug JSON example):
            def test_us_2g_20mhz_wpa2(self):
                self.run_channel_performance_tests(
                        dict(
                        test_channels=hostapd_constants.US_CHANNELS_2G,
                        test_channel_bandwidth=20,
                        test_security=hostapd_constants.WPA2_STRING,
                        min_tx_throughput=2,
                        min_rx_throughput=4,
                        max_std_dev=0.75,
                        country_code='US',
                        base_test_name='test_us'))
        """
        test_channels = settings['test_channels']
        test_channel_bandwidth = settings['test_channel_bandwidth']
        test_security = settings.get('test_security', None)
        test_name = settings.get('test_name', self.test_name)
        base_test_name = settings.get('base_test_name', 'test')
        min_tx_throughput = settings.get('min_tx_throughput',
                                         DEFAULT_MIN_THROUGHPUT)
        min_rx_throughput = settings.get('min_rx_throughput',
                                         DEFAULT_MIN_THROUGHPUT)
        max_std_dev = settings.get('max_std_dev', DEFAULT_MAX_STD_DEV)
        country_code = settings.get('country_code')
        country_name = settings.get('country_name')
        country_label = None

        if country_code:
            country_label = country_code
            self.set_dut_country_code(country_code)
        elif country_name:
            country_label = country_name
            code = hostapd_constants.COUNTRY_CODE[country_name]['country_code']
            self.set_dut_country_code(code)

        self.throughput_data = {
            'test': test_name,
            'channel_bandwidth': test_channel_bandwidth,
            'results': {}
        }
        test_list = []
        for channel in test_channels:
            sub_test_name = 'test_%schannel_%s_%smhz_%s_performance' % (
                '%s_' % country_label if country_label else '', channel,
                test_channel_bandwidth,
                test_security if test_security else 'open')
            test_list.append({
                'test_name': sub_test_name,
                'channel': int(channel),
                'channel_bandwidth': int(test_channel_bandwidth),
                'security': test_security,
                'min_tx_throughput': min_tx_throughput,
                'min_rx_throughput': min_rx_throughput
            })
        self.run_generated_testcases(self.get_channel_performance,
                                     settings=test_list,
                                     name_func=get_test_name)
        self.log.info('Channel tests completed.')
        self.write_graph()
        self.verify_standard_deviation(max_std_dev)

    def get_channel_performance(self, settings):
        """Run a single channel performance test and logs results to csv file
        and throughput data. Run with generated sub test cases in
        run_channel_performance_tests.

        1. Sets up network with test settings
        2. Associates DUT
        3. Runs traffic between DUT and iperf server (both directions)
        4. Logs channel, tx_throughput (Mb/s), and rx_throughput (Mb/s) to
           log file and throughput data.
        5. Checks throughput values against minimum throughput thresholds.

        Args:
            settings: see run_channel_performance_tests

        Raises:
            TestFailure, if throughput (either direction) is less than
                the directions given minimum throughput threshold.
        """
        channel = settings['channel']
        channel_bandwidth = settings['channel_bandwidth']
        security = settings['security']
        test_name = settings['test_name']
        min_tx_throughput = settings['min_tx_throughput']
        min_rx_throughput = settings['min_rx_throughput']
        if security:
            if security == hostapd_constants.WEP_STRING:
                password = utils.rand_hex_str(WEP_HEX_STRING_LENGTH)
            else:
                password = utils.rand_ascii_str(
                    hostapd_constants.MIN_WPA_PSK_LENGTH)
            security_profile = Security(security_mode=security,
                                        password=password)
            target_security = hostapd_constants.SECURITY_STRING_TO_DEFAULT_TARGET_SECURITY.get(
                security)
        else:
            password = None
            security_profile = None
            target_security = None
        ssid = self.setup_ap(channel, channel_bandwidth, security_profile)
        associated = self.dut.associate(ssid,
                                        target_pwd=password,
                                        target_security=target_security)
        if not associated:
            if self.iperf_server:
                self.log_to_file_and_throughput_data(channel,
                                                     channel_bandwidth, None,
                                                     None)
            asserts.fail('Device failed to associate with network %s' % ssid)
        self.log.info('DUT (%s) connected to network %s.' %
                      (self.dut.device.ip, ssid))
        if self.iperf_server:
            self.iperf_server.renew_test_interface_ip_address()
            self.log.info(
                'Getting ip address for iperf server. Will retry for %s seconds.'
                % self.time_to_wait_for_ip_addr)
            iperf_server_address = self.get_and_verify_iperf_address(
                channel, self.iperf_server)
            self.log.info(
                'Getting ip address for DUT. Will retry for %s seconds.' %
                self.time_to_wait_for_ip_addr)
            iperf_client_address = self.get_and_verify_iperf_address(
                channel, self.dut, self.iperf_client.test_interface)
            tx_throughput = self.get_iperf_throughput(iperf_server_address,
                                                      iperf_client_address)
            rx_throughput = self.get_iperf_throughput(iperf_server_address,
                                                      iperf_client_address,
                                                      reverse=True)
            self.log_to_file_and_throughput_data(channel, channel_bandwidth,
                                                 tx_throughput, rx_throughput)
            self.log.info('Throughput (tx, rx): (%s Mb/s, %s Mb/s), '
                          'Minimum threshold (tx, rx): (%s Mb/s, %s Mb/s)' %
                          (tx_throughput, rx_throughput, min_tx_throughput,
                           min_rx_throughput))
            base_message = (
                'Actual throughput (on channel: %s, channel bandwidth: '
                '%s, security: %s)' % (channel, channel_bandwidth, security))
            if (not tx_throughput or not rx_throughput
                    or tx_throughput < min_tx_throughput
                    or rx_throughput < min_rx_throughput):
                asserts.fail('%s below the minimum threshold.' % base_message)
            asserts.explicit_pass('%s above the minimum threshold.' %
                                  base_message)
        else:
            asserts.explicit_pass(
                'Association test pass. No throughput measurement taken.')

    def verify_regulatory_compliance(self, settings):
        """Test function for regulatory compliance tests. Verify device complies
        with provided regulatory requirements.

        Args:
            settings: dict, containing the following test settings
                test_channels: dict, mapping channels to a set of the channel
                    bandwidths to test (see example for using JSON). Defaults
                    to hostapd_constants.ALL_CHANNELS.
                country_code: string, two-char country code to set on device
                    (prioritized over country_name)
                country_name: string, country name from hostapd_constants to set
                    on device.
                base_test_name (optional): string, test name prefix to use with
                    generatedsubtests.
                test_name: string, the test name for this
                    parent test case from the config file. In explicit tests,
                    this is not necessary.
        """
        country_name = settings.get('country_name')
        country_code = settings.get('country_code')
        if not (country_code or country_name):
            raise ValueError('No country code or name provided.')

        test_channels = settings.get('test_channels',
                                     hostapd_constants.ALL_CHANNELS)
        allowed_channels = settings['allowed_channels']

        base_test_name = settings.get('base_test_name', 'test_compliance')

        if country_code:
            code = country_code
        else:
            code = hostapd_constants.COUNTRY_CODE[country_name]['country_code']

        self.set_dut_country_code(code)

        test_list = []
        for channel in test_channels:
            for channel_bandwidth in test_channels[channel]:
                sub_test_name = '%s_channel_%s_%smhz' % (
                    base_test_name, channel, channel_bandwidth)
                should_associate = (channel in allowed_channels
                                    and channel_bandwidth
                                    in allowed_channels[channel])
                # Note: these int conversions because when these tests are
                # imported via JSON, they may be strings since the channels
                # will be keys. This makes the json/list test_channels param
                # behave exactly like the in code dict/set test_channels.
                test_list.append({
                    'country_code': code,
                    'channel': int(channel),
                    'channel_bandwidth': int(channel_bandwidth),
                    'should_associate': should_associate,
                    'test_name': sub_test_name
                })
        self.run_generated_testcases(test_func=self.verify_channel_compliance,
                                     settings=test_list,
                                     name_func=get_test_name)

    def verify_channel_compliance(self, settings):
        """Verify device complies with provided regulatory requirements for a
        specific channel and channel bandwidth. Run with generated test cases
        in the verify_regulatory_compliance parent test.
_
        Args:
            settings: see verify_regulatory_compliance`
        """
        channel = settings['channel']
        channel_bandwidth = settings['channel_bandwidth']
        code = settings['country_code']
        should_associate = settings['should_associate']

        ssid = self.setup_ap(channel, channel_bandwidth)

        self.log.info(
            'Attempting to associate with network (%s) on channel %s @ %smhz. '
            'Expected behavior: %s' %
            (ssid, channel, channel_bandwidth, 'Device should associate'
             if should_associate else 'Device should NOT associate.'))

        associated = self.dut.associate(ssid)

        regulatory_result_marker = "REGTRACKER: %s,%s,%s,%s,%s" % (
            code, channel, '2.4' if channel < 36 else '5', channel_bandwidth,
            'c' if associated else 'nc')
        self.regulatory_results += regulatory_result_marker + "\n"
        self.log.info(regulatory_result_marker)

        if associated == should_associate:
            asserts.explicit_pass(
                'Device complied with %s regulatory requirement for channel %s '
                ' with channel bandwidth %smhz. %s' %
                (code, channel, channel_bandwidth,
                 'Associated.' if associated else 'Refused to associate.'))
        else:
            asserts.fail(
                'Device failed compliance with regulatory domain %s for '
                'channel %s with channel bandwidth %smhz. Expected: %s, Got: %s'
                % (code, channel, channel_bandwidth, 'Should associate'
                   if should_associate else 'Should not associate',
                   'Associated' if associated else 'Did not associate'))

    # Helper functions to allow explicit tests throughput and standard deviation
    # thresholds to be passed in via config.
    def _get_min_tx_throughput(self, test_name):
        return self.user_params.get('channel_sweep_test_params',
                                    {}).get(test_name,
                                            {}).get('min_tx_throughput',
                                                    DEFAULT_MIN_THROUGHPUT)

    def _get_min_rx_throughput(self, test_name):
        return self.user_params.get('channel_sweep_test_params',
                                    {}).get(test_name,
                                            {}).get('min_rx_throughput',
                                                    DEFAULT_MIN_THROUGHPUT)

    def _get_max_std_dev(self, test_name):
        return self.user_params.get('channel_sweep_test_params',
                                    {}).get(test_name,
                                            {}).get('min_std_dev',
                                                    DEFAULT_MAX_STD_DEV)

    # Channel Performance of US Channels: 570 Test Cases
    # 36 Test Cases
    def test_us_20mhz_open_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_2G +
                 hostapd_constants.US_CHANNELS_5G,
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_20MHZ,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 35 Test Cases
    def test_us_40mhz_open_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_2G +
                 hostapd_constants.US_CHANNELS_5G[:-1],
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_40MHZ,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 24 Test Cases
    def test_us_80mhz_open_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_5G[:-1],
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_80MHZ,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 36 Test Cases
    def test_us_20mhz_wep_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_2G +
                 hostapd_constants.US_CHANNELS_5G,
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_20MHZ,
                 test_security=hostapd_constants.WEP_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 35 Test Cases
    def test_us_40mhz_wep_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_2G +
                 hostapd_constants.US_CHANNELS_5G[:-1],
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_40MHZ,
                 test_security=hostapd_constants.WEP_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 24 Test Cases
    def test_us_80mhz_wep_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_5G[:-1],
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_80MHZ,
                 test_security=hostapd_constants.WEP_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 36 Test Cases
    def test_us_20mhz_wpa_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_2G +
                 hostapd_constants.US_CHANNELS_5G,
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_20MHZ,
                 test_security=hostapd_constants.WPA_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 35 Test Cases
    def test_us_40mhz_wpa_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_2G +
                 hostapd_constants.US_CHANNELS_5G[:-1],
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_40MHZ,
                 test_security=hostapd_constants.WPA_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 24 Test Cases
    def test_us_80mhz_wpa_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_5G[:-1],
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_80MHZ,
                 test_security=hostapd_constants.WPA_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 36 Test Cases
    def test_us_20mhz_wpa2_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_2G +
                 hostapd_constants.US_CHANNELS_5G,
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_20MHZ,
                 test_security=hostapd_constants.WPA2_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 35 Test Cases
    def test_us_40mhz_wpa2_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_2G +
                 hostapd_constants.US_CHANNELS_5G[:-1],
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_40MHZ,
                 test_security=hostapd_constants.WPA2_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 24 Test Cases
    def test_us_80mhz_wpa2_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_5G[:-1],
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_80MHZ,
                 test_security=hostapd_constants.WPA2_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 36 Test Cases
    def test_us_20mhz_wpa_wpa2_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_2G +
                 hostapd_constants.US_CHANNELS_5G,
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_20MHZ,
                 test_security=hostapd_constants.WPA_MIXED_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 35 Test Cases
    def test_us_40mhz_wpa_wpa2_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_2G +
                 hostapd_constants.US_CHANNELS_5G[:-1],
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_40MHZ,
                 test_security=hostapd_constants.WPA_MIXED_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 24 Test Cases
    def test_us_80mhz_wpa_wpa2_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_5G[:-1],
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_80MHZ,
                 test_security=hostapd_constants.WPA_MIXED_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 36 Test Cases
    def test_us_20mhz_wpa3_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_2G +
                 hostapd_constants.US_CHANNELS_5G,
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_20MHZ,
                 test_security=hostapd_constants.WPA3_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 35 Test Cases
    def test_us_40mhz_wpa3_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_2G +
                 hostapd_constants.US_CHANNELS_5G[:-1],
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_40MHZ,
                 test_security=hostapd_constants.WPA3_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    # 24 Test Cases
    def test_us_80mhz_wpa3_channel_performance(self):
        self.run_channel_performance_tests(
            dict(test_channels=hostapd_constants.US_CHANNELS_5G[:-1],
                 test_channel_bandwidth=hostapd_constants.
                 CHANNEL_BANDWIDTH_80MHZ,
                 test_security=hostapd_constants.WPA3_STRING,
                 base_test_name=self.test_name,
                 min_tx_throughput=self._get_min_tx_throughput(self.test_name),
                 min_rx_throughput=self._get_min_rx_throughput(self.test_name),
                 max_std_dev=self._get_max_std_dev(self.test_name)))

    def test_channel_performance_debug(self):
        """Run channel performance test cases from the ACTS config file.

        Example:
        "channel_sweep_test_params": {
            "debug_channel_performance_tests": [
                {
                    "test_name": "test_123_20mhz_wpa2_performance"
                    "test_channels": [1, 2, 3],
                    "test_channel_bandwidth": 20,
                    "test_security": "wpa2",
                    "base_test_name": "test_123_perf",
                    "min_tx_throughput": 1.1,
                    "min_rx_throughput": 3,
                    "max_std_dev": 0.5
                },
                ...
            ]
        }

        """
        asserts.skip_if(
            'debug_channel_performance_tests'
            not in self.user_params.get('channel_sweep_test_params', {}),
            'No custom channel performance tests provided in config.')
        base_tests = self.user_params['channel_sweep_test_params'][
            'debug_channel_performance_tests']
        self.run_generated_testcases(self.run_channel_performance_tests,
                                     settings=base_tests,
                                     name_func=get_test_name)

    def test_regulatory_compliance(self):
        """Run regulatory compliance test case from the ACTS config file.
        Note: only one country_name OR country_code is required.

        Example:
        "channel_sweep_test_params": {
            "regulatory_compliance_tests": [
                {
                    "test_name": "test_japan_compliance_1_13_36"
                    "country_name": "JAPAN",
                    "country_code": "JP",
                    "test_channels": {
                        "1": [20, 40], "13": [40], "36": [20, 40, 80]
                    },
                    "allowed_channels": {
                        "1": [20, 40], "36": [20, 40, 80]
                    },
                    "base_test_name": "test_japan"
                },
                ...
            ]
        }
        """
        asserts.skip_if(
            'regulatory_compliance_tests'
            not in self.user_params.get('channel_sweep_test_params', {}),
            'No custom regulatory compliance tests provided in config.')
        base_tests = self.user_params['channel_sweep_test_params'][
            'regulatory_compliance_tests']
        self.run_generated_testcases(self.verify_regulatory_compliance,
                                     settings=base_tests,
                                     name_func=get_test_name)
