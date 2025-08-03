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
import random
import logging
from acts.base_test import BaseTestClass
from acts_contrib.test_utils.bt.BtInterferenceBaseTest import inject_static_wifi_interference
from acts_contrib.test_utils.bt.BtInterferenceBaseTest import unpack_custom_file
from acts_contrib.test_utils.power.PowerBaseTest import ObjNew
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wpeutils
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
import time

MAX_ATTENUATION = 95
INIT_ATTEN = 0
SCAN = 'wpa_cli scan'
SCAN_RESULTS = 'wpa_cli scan_results'


class InjectWifiInterferenceTest(BaseTestClass):
    def __init__(self, configs):
        super().__init__(configs)
        req_params = ['custom_files', 'wifi_networks']
        self.unpack_userparams(req_params)
        for file in self.custom_files:
            if 'static_interference' in file:
                self.static_wifi_interference = unpack_custom_file(file)
            elif 'dynamic_interference' in file:
                self.dynamic_wifi_interference = unpack_custom_file(file)

    def setup_class(self):

        self.dut = self.android_devices[0]
        # Set attenuator to minimum attenuation
        if hasattr(self, 'attenuators'):
            self.attenuator = self.attenuators[0]
            self.attenuator.set_atten(INIT_ATTEN)
        self.wifi_int_pairs = []
        for i in range(len(self.attenuators) - 1):
            tmp_dict = {
                'attenuator': self.attenuators[i + 1],
                'network': self.wifi_networks[i],
                'channel': self.wifi_networks[i]['channel']
            }
            tmp_obj = ObjNew(**tmp_dict)
            self.wifi_int_pairs.append(tmp_obj)
        ##Setup connection between WiFi APs and Phones and get DHCP address
        # for the interface
        for obj in self.wifi_int_pairs:
            obj.attenuator.set_atten(INIT_ATTEN)

    def setup_test(self):
        self.log.info("Setup test initiated")

    def teardown_class(self):
        for obj in self.wifi_int_pairs:
            obj.attenuator.set_atten(MAX_ATTENUATION)

    def teardown_test(self):
        for obj in self.wifi_int_pairs:
            obj.attenuator.set_atten(MAX_ATTENUATION)

    def test_inject_static_wifi_interference(self):
        condition = True
        while condition:
            attenuation = [
                int(x) for x in input(
                    "Please enter 4 channel attenuation value followed by comma :\n"
                ).split(',')
            ]
            self.set_atten_all_channel(attenuation)
            # Read interference RSSI
            self.interference_rssi = get_interference_rssi(
                self.dut, self.wifi_int_pairs)
            self.log.info('Under the WiFi interference condition: '
                          'channel 1 RSSI: {} dBm, '
                          'channel 6 RSSI: {} dBm'
                          'channel 11 RSSI: {} dBm'.format(
                              self.interference_rssi[0]['rssi'],
                              self.interference_rssi[1]['rssi'],
                              self.interference_rssi[2]['rssi']))
            condition = True
        return True

    def test_inject_dynamic_interface(self):
        atten = int(input("Please enter the attenuation level for CHAN1 :"))
        self.attenuator.set_atten(atten)
        self.log.info("Attenuation for CHAN1 set to:{} dB".format(atten))
        interference_rssi = None
        self.channel_change_interval = self.dynamic_wifi_interference[
            'channel_change_interval_second']
        self.wifi_int_levels = list(
            self.dynamic_wifi_interference['interference_level'].keys())
        for wifi_level in self.wifi_int_levels:
            interference_atten_level = self.dynamic_wifi_interference[
                'interference_level'][wifi_level]
            all_pair = range(len(self.wifi_int_pairs))
            # Set initial WiFi interference at channel 1
            logging.info('Start with interference at channel 1')
            self.wifi_int_pairs[0].attenuator.set_atten(
                interference_atten_level)
            self.wifi_int_pairs[1].attenuator.set_atten(MAX_ATTENUATION)
            self.wifi_int_pairs[2].attenuator.set_atten(MAX_ATTENUATION)
            current_int_pair = [0]
            inactive_int_pairs = [
                item for item in all_pair if item not in current_int_pair
            ]
            logging.info(
                'Inject random changing channel (1,6,11) wifi interference'
                'every {} second'.format(self.channel_change_interval))
            while True:
                current_int_pair = [
                    random.randint(inactive_int_pairs[0],
                                   inactive_int_pairs[1])
                ]
                inactive_int_pairs = [
                    item for item in all_pair if item not in current_int_pair
                ]
                self.wifi_int_pairs[current_int_pair[0]].attenuator.set_atten(
                    interference_atten_level)
                logging.info('Current interference at channel {}'.format(
                    self.wifi_int_pairs[current_int_pair[0]].channel))
                for i in inactive_int_pairs:
                    self.wifi_int_pairs[i].attenuator.set_atten(
                        MAX_ATTENUATION)
                # Read interference RSSI
                self.interference_rssi = get_interference_rssi(
                    self.dut, self.wifi_int_pairs)
                self.log.info('Under the WiFi interference condition: '
                              'channel 1 RSSI: {} dBm, '
                              'channel 6 RSSI: {} dBm'
                              'channel 11 RSSI: {} dBm'.format(
                                  self.interference_rssi[0]['rssi'],
                                  self.interference_rssi[1]['rssi'],
                                  self.interference_rssi[2]['rssi']))
                time.sleep(self.channel_change_interval)
            return True

    def set_atten_all_channel(self, attenuation):
        self.attenuators[0].set_atten(attenuation[0])
        self.attenuators[1].set_atten(attenuation[1])
        self.attenuators[2].set_atten(attenuation[2])
        self.attenuators[3].set_atten(attenuation[3])
        self.log.info(
            "Attenuation set to CHAN1:{},CHAN2:{},CHAN3:{},CHAN4:{}".format(
                self.attenuators[0].get_atten(),
                self.attenuators[1].get_atten(),
                self.attenuators[2].get_atten(),
                self.attenuators[3].get_atten()))


def get_interference_rssi(dut, wifi_int_pairs):
    """Function to read wifi interference RSSI level."""

    bssids = []
    interference_rssi = []
    wutils.wifi_toggle_state(dut, True)
    for item in wifi_int_pairs:
        ssid = item.network['SSID']
        bssid = item.network['bssid']
        bssids.append(bssid)
        interference_rssi_dict = {
            "ssid": ssid,
            "bssid": bssid,
            "chan": item.channel,
            "rssi": 0
        }
        interference_rssi.append(interference_rssi_dict)
    scaned_rssi = wpeutils.get_scan_rssi(dut, bssids, num_measurements=2)
    for item in interference_rssi:
        item['rssi'] = scaned_rssi[item['bssid']]['mean']
        logging.info('Interference RSSI at channel {} is {} dBm'.format(
            item['chan'], item['rssi']))
    wutils.wifi_toggle_state(dut, False)
    return interference_rssi
