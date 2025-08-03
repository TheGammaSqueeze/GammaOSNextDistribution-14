#!/usr/bin/env python3
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

from acts_contrib.test_utils.wifi.wifi_retail_ap import NetgearR7000AP


class NetgearRAX80AP(NetgearR7000AP):
    """Class that implements Netgear RAX AP.

    Since most of the class' implementation is shared with the R7000, this
    class inherits from NetgearR7000AP and simply redefines config parameters
    """
    def init_gui_data(self):
        super().init_gui_data()
        # Overwrite minor differences from R7000 AP
        self.config_page = (
            '{protocol}://{username}:{password}@'
            '{ip_address}:{port}/WLG_wireless_dual_band_r10.htm').format(
                protocol=self.ap_settings['protocol'],
                username=self.ap_settings['admin_username'],
                password=self.ap_settings['admin_password'],
                ip_address=self.ap_settings['ip_address'],
                port=self.ap_settings['port'])
        self.config_page_nologin = (
            '{protocol}://{ip_address}:{port}/'
            'WLG_wireless_dual_band_r10.htm').format(
                protocol=self.ap_settings['protocol'],
                ip_address=self.ap_settings['ip_address'],
                port=self.ap_settings['port'])
        self.config_page_advanced = (
            '{protocol}://{username}:{password}@'
            '{ip_address}:{port}/WLG_adv_dual_band2.htm').format(
                protocol=self.ap_settings['protocol'],
                username=self.ap_settings['admin_username'],
                password=self.ap_settings['admin_password'],
                ip_address=self.ap_settings['ip_address'],
                port=self.ap_settings['port'])
        self.capabilities = {
            'interfaces': ['2G', '5G_1', '5G_2'],
            'channels': {
                '2G': [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
                '5G_1': [36, 40, 44, 48],
                '5G_2': [149, 153, 157, 161, 165]
            },
            'modes': {
                '2G': ['VHT20', 'VHT40'],
                '5G_1': ['VHT20', 'VHT40', 'VHT80'],
                '5G_2': ['VHT20', 'VHT40', 'VHT80']
            },
            'default_mode': 'VHT'
        }
        for interface in self.capabilities['interfaces']:
            self.ap_settings[interface] = {}

        self.bw_mode_values = {
            'g and b': '11g',
            '145Mbps': 'VHT20',
            '300Mbps': 'VHT40',
            'HT80': 'VHT80',
            'HT160': 'VHT160'
        }
        self.bw_mode_text = {
            '11g': 'Up to 54 Mbps',
            'VHT20': 'Up to 600 Mbps',
            'VHT40': 'Up to 1200 Mbps',
            'VHT80': 'Up to 2400 Mbps',
            'VHT160': 'Up to 4800 Mbps'
        }
