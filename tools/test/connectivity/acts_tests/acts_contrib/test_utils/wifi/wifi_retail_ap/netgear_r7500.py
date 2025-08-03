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

import selenium
import time
from acts_contrib.test_utils.wifi.wifi_retail_ap import WifiRetailAP
from acts_contrib.test_utils.wifi.wifi_retail_ap import BlockingBrowser

BROWSER_WAIT_SHORT = 1
BROWSER_WAIT_MED = 3
BROWSER_WAIT_LONG = 30
BROWSER_WAIT_EXTRA_LONG = 60


class NetgearR7500AP(WifiRetailAP):
    """Class that implements Netgear R7500 AP."""

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
        self.config_page = ('{protocol}://{username}:{password}@'
                            '{ip_address}:{port}/index.htm').format(
                                protocol=self.ap_settings['protocol'],
                                username=self.ap_settings['admin_username'],
                                password=self.ap_settings['admin_password'],
                                ip_address=self.ap_settings['ip_address'],
                                port=self.ap_settings['port'])
        self.config_page_advanced = (
            '{protocol}://{username}:{password}@'
            '{ip_address}:{port}/adv_index.htm').format(
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
            ('2G', 'security_type'): 'security_type',
            ('5G_1', 'security_type'): 'security_type_an',
            ('2G', 'password'): 'passphrase',
            ('5G_1', 'password'): 'passphrase_an'
        }
        self.region_map = {
            '0': 'Africa',
            '1': 'Asia',
            '2': 'Australia',
            '3': 'Canada',
            '4': 'Europe',
            '5': 'Israel',
            '6': 'Japan',
            '7': 'Korea',
            '8': 'Mexico',
            '9': 'South America',
            '10': 'United States',
            '11': 'China',
            '12': 'India',
            '13': 'Malaysia',
            '14': 'Middle East(Algeria/Syria/Yemen)',
            '15': 'Middle East(Iran/Labanon/Qatar)',
            '16': 'Middle East(Turkey/Egypt/Tunisia/Kuwait)',
            '17': 'Middle East(Saudi Arabia)',
            '18': 'Middle East(United Arab Emirates)',
            '19': 'Russia',
            '20': 'Singapore',
            '21': 'Taiwan'
        }
        self.bw_mode_text = {
            '2G': {
                '11g': 'Up to 54 Mbps',
                'VHT20': 'Up to 289 Mbps',
                'VHT40': 'Up to 600 Mbps'
            },
            '5G_1': {
                'VHT20': 'Up to 347 Mbps',
                'VHT40': 'Up to 800 Mbps',
                'VHT80': 'Up to 1733 Mbps'
            }
        }
        self.bw_mode_values = {
            '1': '11g',
            '2': 'VHT20',
            '3': 'VHT40',
            '7': 'VHT20',
            '8': 'VHT40',
            '9': 'VHT80'
        }
        self.security_mode_values = {
            '2G': {
                'Disable': 'security_disable',
                'WPA2-PSK': 'security_wpa2'
            },
            '5G_1': {
                'Disable': 'security_an_disable',
                'WPA2-PSK': 'security_an_wpa2'
            }
        }

    def read_ap_settings(self):
        """Function to read ap wireless settings."""
        # Get radio status (on/off)
        self.read_radio_on_off()
        # Get radio configuration. Note that if both radios are off, the below
        # code will result in an error
        with BlockingBrowser(self.ap_settings['headless_browser'],
                             900) as browser:
            browser.visit_persistent(self.config_page,
                                     BROWSER_WAIT_MED,
                                     10,
                                     check_for_element='wireless')
            wireless_button = browser.find_by_id('wireless').first
            wireless_button.click()
            time.sleep(BROWSER_WAIT_MED)

            with browser.get_iframe('formframe') as iframe:
                for key, value in self.config_page_fields.items():
                    if 'bandwidth' in key:
                        config_item = iframe.find_by_name(value).first
                        self.ap_settings[key[0]][key[1]] = self.bw_mode_values[
                            config_item.value]
                    elif 'region' in key:
                        config_item = iframe.find_by_name(value).first
                        self.ap_settings['region'] = self.region_map[
                            config_item.value]
                    elif 'password' in key:
                        try:
                            config_item = iframe.find_by_name(value).first
                            self.ap_settings[key[0]][
                                key[1]] = config_item.value
                            self.ap_settings[
                                key[0]]['security_type'] = 'WPA2-PSK'
                        except:
                            self.ap_settings[key[0]][
                                key[1]] = 'defaultpassword'
                            self.ap_settings[
                                key[0]]['security_type'] = 'Disable'
                    elif ('channel' in key) or ('ssid' in key):
                        config_item = iframe.find_by_name(value).first
                        self.ap_settings[key[0]][key[1]] = config_item.value
                    else:
                        pass
        return self.ap_settings.copy()

    def configure_ap(self, **config_flags):
        """Function to configure ap wireless settings."""
        # Turn radios on or off
        if config_flags['status_toggled']:
            self.configure_radio_on_off()
        # Configure radios
        with BlockingBrowser(self.ap_settings['headless_browser'],
                             900) as browser:
            browser.visit_persistent(self.config_page,
                                     BROWSER_WAIT_MED,
                                     10,
                                     check_for_element='wireless')
            wireless_button = browser.find_by_id('wireless').first
            wireless_button.click()
            time.sleep(BROWSER_WAIT_MED)

            with browser.get_iframe('formframe') as iframe:
                # Update AP region. Must be done before channel setting
                config_item = iframe.find_by_name(
                    self.config_page_fields['region']).first
                config_item.select_by_text(self.ap_settings['region'])
                # Update wireless settings for each network
                for key, value in self.config_page_fields.items():
                    if 'ssid' in key:
                        config_item = iframe.find_by_name(value).first
                        config_item.fill(self.ap_settings[key[0]][key[1]])
                    elif 'channel' in key:
                        channel = self.ap_settings[key[0]][key[1]]
                        if int(channel) < 10:
                            channel_string = '0' + str(channel)
                        elif int(channel) > 48 and int(channel) < 149:
                            channel_string = str(channel) + 'DFS'
                        else:
                            channel_string = str(channel)
                        config_item = iframe.find_by_name(value).first
                        try:
                            config_item.select_by_text(channel_string)
                        except AttributeError:
                            self.log.warning(
                                'Cannot select channel. Keeping AP default.')
                    elif 'bandwidth' in key:
                        config_item = iframe.find_by_name(value).first
                        try:
                            config_item.select_by_text(
                                str(self.bw_mode_text[key[0]][self.ap_settings[
                                    key[0]][key[1]]]))
                        except AttributeError:
                            self.log.warning(
                                'Cannot select bandwidth. Keeping AP default.')
                # Update passwords for WPA2-PSK protected networks
                # (Must be done after security type is selected)
                for key, value in self.config_page_fields.items():
                    if 'security_type' in key:
                        security_option = browser.driver.find_element_by_id(
                            self.security_mode_values[key[0]][self.ap_settings[
                                key[0]][key[1]]])
                        action = selenium.webdriver.common.action_chains.ActionChains(
                            browser.driver)
                        action.move_to_element(
                            security_option).click().perform()
                        if self.ap_settings[key[0]][key[1]] == 'WPA2-PSK':
                            config_item = iframe.find_by_name(
                                self.config_page_fields[(key[0],
                                                         'password')]).first
                            config_item.fill(
                                self.ap_settings[key[0]]['password'])

                apply_button = iframe.find_by_name('Apply')
                apply_button[0].click()
                time.sleep(BROWSER_WAIT_SHORT)
                try:
                    alert = browser.get_alert()
                    alert.accept()
                except:
                    pass
                time.sleep(BROWSER_WAIT_SHORT)
                try:
                    alert = browser.get_alert()
                    alert.accept()
                except:
                    pass
                time.sleep(BROWSER_WAIT_SHORT)
            time.sleep(BROWSER_WAIT_EXTRA_LONG)
            browser.visit_persistent(self.config_page, BROWSER_WAIT_EXTRA_LONG,
                                     10)

    def configure_radio_on_off(self):
        """Helper configuration function to turn radios on/off."""
        with BlockingBrowser(self.ap_settings['headless_browser'],
                             900) as browser:
            browser.visit_persistent(self.config_page, BROWSER_WAIT_MED, 10)
            browser.visit_persistent(self.config_page_advanced,
                                     BROWSER_WAIT_MED,
                                     10,
                                     check_for_element='advanced_bt')
            advanced_button = browser.find_by_id('advanced_bt').first
            advanced_button.click()
            time.sleep(BROWSER_WAIT_MED)
            wireless_button = browser.find_by_id('wladv').first
            wireless_button.click()
            time.sleep(BROWSER_WAIT_MED)

            with browser.get_iframe('formframe') as iframe:
                # Turn radios on or off
                for key, value in self.config_page_fields.items():
                    if 'status' in key:
                        config_item = iframe.find_by_name(value).first
                        if self.ap_settings[key[0]][key[1]]:
                            config_item.check()
                        else:
                            config_item.uncheck()

                time.sleep(BROWSER_WAIT_SHORT)
                browser.find_by_name('Apply').first.click()
                time.sleep(BROWSER_WAIT_EXTRA_LONG)
                browser.visit_persistent(self.config_page,
                                         BROWSER_WAIT_EXTRA_LONG, 10)

    def read_radio_on_off(self):
        """Helper configuration function to read radio status."""
        with BlockingBrowser(self.ap_settings['headless_browser'],
                             900) as browser:
            browser.visit_persistent(self.config_page, BROWSER_WAIT_MED, 10)
            browser.visit_persistent(self.config_page_advanced,
                                     BROWSER_WAIT_MED,
                                     10,
                                     check_for_element='advanced_bt')
            advanced_button = browser.find_by_id('advanced_bt').first
            advanced_button.click()
            time.sleep(BROWSER_WAIT_SHORT)
            wireless_button = browser.find_by_id('wladv').first
            wireless_button.click()
            time.sleep(BROWSER_WAIT_MED)

            with browser.get_iframe('formframe') as iframe:
                # Turn radios on or off
                for key, value in self.config_page_fields.items():
                    if 'status' in key:
                        config_item = iframe.find_by_name(value).first
                        self.ap_settings[key[0]][key[1]] = int(
                            config_item.checked)


class NetgearR7500NAAP(NetgearR7500AP):
    """Class that implements Netgear R7500 NA AP."""

    def init_gui_data(self):
        """Function to initialize data used while interacting with web GUI"""
        super().init_gui_data()
        self.region_map['10'] = 'North America'
