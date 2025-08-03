#!/usr/bin/env python3
#
# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
"""Stream music through connected device from phone across different
attenuations."""

import json
import math
import time
import logging
import acts.controllers.iperf_client as ipc
import acts.controllers.iperf_server as ipf
import acts_contrib.test_utils.bt.bt_test_utils as btutils
from acts import asserts
from acts_contrib.test_utils.bt.A2dpBaseTest import A2dpBaseTest
from acts_contrib.test_utils.bt.loggers import bluetooth_metric_logger as log
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wpeutils
from acts_contrib.test_utils.wifi import wifi_power_test_utils as wputils
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.power.PowerBaseTest import ObjNew

MAX_ATTENUATION = 95
TEMP_FILE = '/sdcard/Download/tmp.log'
IPERF_CLIENT_ERROR = 'the client has unexpectedly closed the connection'


def setup_ap_connection(dut, network, ap, bandwidth=20):
    """Setup AP and connect DUT to it.

    Args:
        dut: the android device to connect and run traffic
        network: the network config for the AP to be setup
        ap: access point object
        bandwidth: bandwidth of the WiFi network to be setup
    Returns:
        brconfigs: dict for bridge interface configs
    """
    wutils.wifi_toggle_state(dut, True)
    brconfigs = wputils.ap_setup(ap, network, bandwidth=bandwidth)
    wutils.wifi_connect(dut, network, num_of_tries=3)
    return brconfigs


def start_iperf_client(traffic_pair_obj, duration):
    """Setup iperf traffic for TCP downlink.
    Args:
        traffic_pair_obj: obj to contain info on traffic pair
        duration: duration of iperf traffic to run
    """
    # Construct the iperf command based on the test params
    iperf_cmd = 'iperf3 -c {} -i 1 -t {} -p {} -J -R > {}'.format(
        traffic_pair_obj.server_address, duration,
        traffic_pair_obj.iperf_server.port, TEMP_FILE)
    # Start IPERF client
    traffic_pair_obj.dut.adb.shell_nb(iperf_cmd)


def unpack_custom_file(file):
    """Unpack the json file to .

    Args:
        file: custom json file.
    """
    with open(file, 'r') as f:
        params = json.load(f)
    return params


def get_iperf_results(iperf_server_obj):
    """Get the iperf results and process.

    Args:
        iperf_server_obj: the IperfServer object
    Returns:
         throughput: the average throughput during tests.
    """
    # Get IPERF results and add this to the plot title
    iperf_file = iperf_server_obj.log_files[-1]
    try:
        iperf_result = ipf.IPerfResult(iperf_file)
        # Compute the throughput in Mbit/s
        if iperf_result.error == IPERF_CLIENT_ERROR:
            rates = []
            for item in iperf_result.result['intervals']:
                rates.append(item['sum']['bits_per_second'] / 8 / 1024 / 1024)
            throughput = ((math.fsum(rates) / len(rates))) * 8 * (1.024**2)
        else:
            throughput = (math.fsum(iperf_result.instantaneous_rates) / len(
                iperf_result.instantaneous_rates)) * 8 * (1.024**2)
    except (ValueError, TypeError):
        throughput = 0
    return throughput


def locate_interference_pair_by_channel(wifi_int_pairs, interference_channels):
    """Function to find which attenautor to set based on channel info
    Args:
        interference_channels: list of interference channels
    Return:
        interference_pair_indices: list of indices for interference pair
            in wifi_int_pairs
    """
    interference_pair_indices = []
    for chan in interference_channels:
        for i in range(len(wifi_int_pairs)):
            if wifi_int_pairs[i].channel == chan:
                interference_pair_indices.append(i)
    return interference_pair_indices


def inject_static_wifi_interference(wifi_int_pairs, interference_level,
                                    channels):
    """Function to inject wifi interference to bt link and read rssi.

    Interference of IPERF traffic is always running, by setting attenuation,
    the gate is opened to release the interference to the setup.
    Args:
        interference_level: the signal strength of wifi interference, use
            attenuation level to represent this
        channels: wifi channels where interference will
            be injected, list
    """
    all_pair = range(len(wifi_int_pairs))
    interference_pair_indices = locate_interference_pair_by_channel(
        wifi_int_pairs, channels)
    inactive_interference_pairs_indices = [
        item for item in all_pair if item not in interference_pair_indices
    ]
    logging.info('WiFi interference at {} and inactive channels at {}'.format(
        interference_pair_indices, inactive_interference_pairs_indices))
    for i in interference_pair_indices:
        wifi_int_pairs[i].attenuator.set_atten(interference_level)
        logging.info('Set attenuation {} dB on attenuator {}'.format(
            wifi_int_pairs[i].attenuator.get_atten(), i + 1))
    for i in inactive_interference_pairs_indices:
        wifi_int_pairs[i].attenuator.set_atten(MAX_ATTENUATION)
        logging.info('Set attenuation {} dB on attenuator {}'.format(
            wifi_int_pairs[i].attenuator.get_atten(), i + 1))


class BtInterferenceBaseTest(A2dpBaseTest):
    def __init__(self, configs):
        super().__init__(configs)
        self.bt_logger = log.BluetoothMetricLogger.for_test_case()
        self.start_time = time.time()
        req_params = [
            'attenuation_vector', 'wifi_networks', 'codecs', 'custom_files',
            'audio_params'
        ]
        self.unpack_userparams(req_params)
        for file in self.custom_files:
            if 'static_interference' in file:
                self.static_wifi_interference = unpack_custom_file(file)
            elif 'dynamic_interference' in file:
                self.dynamic_wifi_interference = unpack_custom_file(file)

    def setup_class(self):
        super().setup_class()
        # Build object to store all necessary information for each pair of wifi
        # interference setup: phone, ap, network, channel, iperf server port/ip
        # object and bridge interface configs
        if len(self.android_devices) < 5 or len(self.attenuators) < 4:
            self.log.error('Need a 4 channel attenuator and 5 android phones'
                           'please update the config file')
        self.wifi_int_pairs = []
        for i in range(len(self.attenuators) - 1):
            tmp_dict = {
                'dut': self.android_devices[i + 1],
                'ap': self.access_points[i],
                'network': self.wifi_networks[i],
                'channel': self.wifi_networks[i]['channel'],
                'iperf_server': self.iperf_servers[i],
                'attenuator': self.attenuators[i + 1],
                'ether_int': self.packet_senders[i],
                'iperf_client':
                ipc.IPerfClientOverAdb(self.android_devices[i + 1])
            }
            tmp_obj = ObjNew(**tmp_dict)
            self.wifi_int_pairs.append(tmp_obj)
        ##Setup connection between WiFi APs and Phones and get DHCP address
        # for the interface
        for obj in self.wifi_int_pairs:
            brconfigs = setup_ap_connection(obj.dut, obj.network, obj.ap)
            iperf_server_address = wputils.wait_for_dhcp(
                obj.ether_int.interface)
            setattr(obj, 'server_address', iperf_server_address)
            setattr(obj, 'brconfigs', brconfigs)
            obj.attenuator.set_atten(MAX_ATTENUATION)
        # Enable BQR on master and slave Android device
        btutils.enable_bqr(self.dut)
        btutils.enable_bqr(self.bt_device_controller)

    def teardown_class(self):
        super().teardown_class()
        for obj in self.wifi_int_pairs:
            obj.ap.bridge.teardown(obj.brconfigs)
            self.log.info('Stop IPERF server at port {}'.format(
                obj.iperf_server.port))
            obj.iperf_server.stop()
            self.log.info('Stop IPERF process on {}'.format(obj.dut.serial))
            #only for glinux machine
            #            wputils.bring_down_interface(obj.ether_int.interface)
            obj.attenuator.set_atten(MAX_ATTENUATION)
            obj.ap.close()

    def teardown_test(self):

        super().teardown_test()
        for obj in self.wifi_int_pairs:
            obj.attenuator.set_atten(MAX_ATTENUATION)

    def play_and_record_audio(self, duration, queue):
        """Play and record audio for a set duration.

        Args:
            duration: duration in seconds for music playing
            que: multiprocess que to store the return value of this function
        Returns:
            audio_captured: captured audio file path
        """

        self.log.info('Play and record audio for {} second'.format(duration))
        self.media.play()
        self.audio_device.start()
        time.sleep(duration)
        audio_captured = self.audio_device.stop()
        self.media.stop()
        self.log.info('Audio play and record stopped')
        asserts.assert_true(audio_captured, 'Audio not recorded')
        queue.put(audio_captured)

    def locate_interference_pair_by_channel(self, interference_channels):
        """Function to find which attenautor to set based on channel info
        Args:
            interference_channels: list of interference channels
        Return:
            interference_pair_indices: list of indices for interference pair
                in self.wifi_int_pairs
        """
        interference_pair_indices = []
        for chan in interference_channels:
            for i in range(len(self.wifi_int_pairs)):
                if self.wifi_int_pairs[i].channel == chan:
                    interference_pair_indices.append(i)
        return interference_pair_indices

    def get_interference_rssi(self):
        """Function to read wifi interference RSSI level."""

        bssids = []
        self.interference_rssi = []
        wutils.wifi_toggle_state(self.android_devices[0], True)
        for item in self.wifi_int_pairs:
            ssid = item.network['SSID']
            bssid = item.ap.get_bssid_from_ssid(ssid, '2g')
            bssids.append(bssid)
            interference_rssi_dict = {
                "ssid": ssid,
                "bssid": bssid,
                "chan": item.channel,
                "rssi": 0
            }
            self.interference_rssi.append(interference_rssi_dict)
        scaned_rssi = wpeutils.get_scan_rssi(self.android_devices[0],
                                             bssids,
                                             num_measurements=2)
        for item in self.interference_rssi:
            item['rssi'] = scaned_rssi[item['bssid']]['mean']
            self.log.info('Interference RSSI at channel {} is {} dBm'.format(
                item['chan'], item['rssi']))
        wutils.wifi_toggle_state(self.android_devices[0], False)
