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

import time
from acts_contrib.test_utils.wifi.wifi_retail_ap import WifiRetailAP
from acts_contrib.test_utils.wifi.wifi_retail_ap import BlockingBrowser

BROWSER_WAIT_SHORT = 1
BROWSER_WAIT_MED = 3
BROWSER_WAIT_LONG = 30
BROWSER_WAIT_EXTRA_LONG = 60


class NetgearR7000AP(WifiRetailAP):
    """Class that implements Netgear R7000 AP."""

    def __init__(self, ap_settings):
        self.log.warning(
            'This AP model is no longer maintained and must be updated/verified.'
        )
        super().__init__(ap_settings)
        self.init_gui_data()
        # Read and update AP settings
        self.read_ap_settings()
        self.update_ap_settings(ap_settings)

    def init_gui_data(self):
        """Function to initialize data used while interacting with web GUI"""
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
            'interfaces': ['2G', '5G_1'],
            'channels': {
                '2G': [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
                '5G_1': [
                    36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116,
                    120, 124, 128, 132, 136, 140, 149, 153, 157, 161, 165
                ]
            },
            'modes': {
                '2G': ['VHT20', 'VHT40'],
                '5G_1': ['VHT20', 'VHT40', 'VHT80']
            },
            'default_mode': 'VHT'
        }
        for interface in self.capabilities['interfaces']:
            self.ap_settings[interface] = {}

        self.region_map = {
            '1': 'Africa',
            '2': 'Asia',
            '3': 'Australia',
            '4': 'Canada',
            '5': 'Europe',
            '6': 'Israel',
            '7': 'Japan',
            '8': 'Korea',
            '9': 'Mexico',
            '10': 'South America',
            '11': 'United States',
            '12': 'Middle East(Algeria/Syria/Yemen)',
            '14': 'Russia',
            '16': 'China',
            '17': 'India',
            '18': 'Malaysia',
            '19': 'Middle East(Iran/Labanon/Qatar)',
            '20': 'Middle East(Turkey/Egypt/Tunisia/Kuwait)',
            '21': 'Middle East(Saudi Arabia)',
            '22': 'Middle East(United Arab Emirates)',
            '23': 'Singapore',
            '24': 'Taiwan'
        }
        self.config_page_fields = {
            'region': 'WRegion',
            ('2G', 'status'): 'enable_ap',
            ('5G_1', 'status'): 'enable_ap_an',
            ('2G', 'ssid'): 'ssid',
            ('5G_1', 'ssid'): 'ssid_an',
            ('2G', 'channel'): 'w_channel',
            ('5G_1', 'channel'): 'w_channel_an',
            ('2G', 'bandwidth'): 'opmode',
            ('5G_1', 'bandwidth'): 'opmode_an',
            ('2G', 'power'): 'enable_tpc',
            ('5G_1', 'power'): 'enable_tpc_an',
            ('2G', 'security_type'): 'security_type',
            ('5G_1', 'security_type'): 'security_type_an',
            ('2G', 'password'): 'passphrase',
            ('5G_1', 'password'): 'passphrase_an'
        }
        self.bw_mode_values = {
            'g and b': '11g',
            '145Mbps': 'VHT20',
            '300Mbps': 'VHT40',
            'HT80': 'VHT80'
        }
        self.power_mode_values = {
            '1': '100%',
            '2': '75%',
            '3': '50%',
            '4': '25%'
        }
        self.bw_mode_text = {
            '11g': 'Up to 54 Mbps',
            'VHT20': 'Up to 289 Mbps',
            'VHT40': 'Up to 600 Mbps',
            'VHT80': 'Up to 1300 Mbps'
        }

    def read_ap_settings(self):
        """Function to read ap settings."""
        with BlockingBrowser(self.ap_settings['headless_browser'],
                             900) as browser:
            # Visit URL
            browser.visit_persistent(self.config_page, BROWSER_WAIT_MED, 10)

            for key, value in self.config_page_fields.items():
                if 'status' in key:
                    browser.visit_persistent(self.config_page_advanced,
                                             BROWSER_WAIT_MED, 10)
                    config_item = browser.find_by_name(value)
                    self.ap_settings[key[0]][key[1]] = int(
                        config_item.first.checked)
                    browser.visit_persistent(self.config_page,
                                             BROWSER_WAIT_MED, 10)
                else:
                    config_item = browser.find_by_name(value)
                    if 'bandwidth' in key:
                        self.ap_settings[key[0]][key[1]] = self.bw_mode_values[
                            config_item.first.value]
                    elif 'power' in key:
                        self.ap_settings[key[0]][
                            key[1]] = self.power_mode_values[
                                config_item.first.value]
                    elif 'region' in key:
                        self.ap_settings['region'] = self.region_map[
                            config_item.first.value]
                    elif 'security_type' in key:
                        for item in config_item:
                            if item.checked:
                                self.ap_settings[key[0]][key[1]] = item.value
                    else:
                        config_item = browser.find_by_name(value)
                        self.ap_settings[key[0]][
                            key[1]] = config_item.first.value
        return self.ap_settings.copy()

    def configure_ap(self, **config_flags):
        """Function to configure ap wireless settings."""
        # Turn radios on or off
        if config_flags['status_toggled']:
            self.configure_radio_on_off()
        # Configure radios
        with BlockingBrowser(self.ap_settings['headless_browser'],
                             900) as browser:
            # Visit URL
            browser.visit_persistent(self.config_page, BROWSER_WAIT_MED, 10)
            browser.visit_persistent(self.config_page_nologin,
                                     BROWSER_WAIT_MED, 10, self.config_page)

            # Update region, and power/bandwidth for each network
            config_item = browser.find_by_name(
                self.config_page_fields['region']).first
            config_item.select_by_text(self.ap_settings['region'])
            for key, value in self.config_page_fields.items():
                if 'power' in key:
                    config_item = browser.find_by_name(value).first
                    config_item.select_by_text(
                        self.ap_settings[key[0]][key[1]])
                elif 'bandwidth' in key:
                    config_item = browser.find_by_name(value).first
                    try:
                        config_item.select_by_text(self.bw_mode_text[
                            self.ap_settings[key[0]][key[1]]])
                    except AttributeError:
                        self.log.warning(
                            'Cannot select bandwidth. Keeping AP default.')

            # Update security settings (passwords updated only if applicable)
            for key, value in self.config_page_fields.items():
                if 'security_type' in key:
                    browser.choose(value, self.ap_settings[key[0]][key[1]])
                    if self.ap_settings[key[0]][key[1]] == 'WPA2-PSK':
                        config_item = browser.find_by_name(
                            self.config_page_fields[(key[0],
                                                     'password')]).first
                        config_item.fill(self.ap_settings[key[0]]['password'])

            # Update SSID and channel for each network
            # NOTE: Update ordering done as such as workaround for R8000
            # wherein channel and SSID get overwritten when some other
            # variables are changed. However, region does have to be set before
            # channel in all cases.
            for key, value in self.config_page_fields.items():
                if 'ssid' in key:
                    config_item = browser.find_by_name(value).first
                    config_item.fill(self.ap_settings[key[0]][key[1]])
                elif 'channel' in key:
                    config_item = browser.find_by_name(value).first
                    try:
                        config_item.select(self.ap_settings[key[0]][key[1]])
                        time.sleep(BROWSER_WAIT_SHORT)
                    except AttributeError:
                        self.log.warning(
                            'Cannot select channel. Keeping AP default.')
                    try:
                        alert = browser.get_alert()
                        alert.accept()
                    except:
                        pass

            time.sleep(BROWSER_WAIT_SHORT)
            browser.find_by_name('Apply').first.click()
            time.sleep(BROWSER_WAIT_SHORT)
            try:
                alert = browser.get_alert()
                alert.accept()
                time.sleep(BROWSER_WAIT_SHORT)
            except:
                time.sleep(BROWSER_WAIT_SHORT)
            browser.visit_persistent(self.config_page, BROWSER_WAIT_EXTRA_LONG,
                                     10)

    def configure_radio_on_off(self):
        """Helper configuration function to turn radios on/off."""
        with BlockingBrowser(self.ap_settings['headless_browser'],
                             900) as browser:
            # Visit URL
            browser.visit_persistent(self.config_page, BROWSER_WAIT_MED, 10)
            browser.visit_persistent(self.config_page_advanced,
                                     BROWSER_WAIT_MED, 10)

            # Turn radios on or off
            for key, value in self.config_page_fields.items():
                if 'status' in key:
                    config_item = browser.find_by_name(value).first
                    if self.ap_settings[key[0]][key[1]]:
                        config_item.check()
                    else:
                        config_item.uncheck()

            time.sleep(BROWSER_WAIT_SHORT)
            browser.find_by_name('Apply').first.click()
            time.sleep(BROWSER_WAIT_EXTRA_LONG)
            browser.visit_persistent(self.config_page, BROWSER_WAIT_EXTRA_LONG,
                                     10)


class NetgearR7000NAAP(NetgearR7000AP):
    """Class that implements Netgear R7000 NA AP."""

    def init_gui_data(self):
        """Function to initialize data used while interacting with web GUI"""
        super().init_gui_data()
        self.region_map['11'] = 'North America'
