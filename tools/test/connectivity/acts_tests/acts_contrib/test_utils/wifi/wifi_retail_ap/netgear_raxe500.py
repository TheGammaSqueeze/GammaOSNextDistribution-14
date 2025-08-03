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

import collections
import numpy
import re
import time
from acts_contrib.test_utils.wifi.wifi_retail_ap import WifiRetailAP
from acts_contrib.test_utils.wifi.wifi_retail_ap import BlockingBrowser

BROWSER_WAIT_SHORT = 1
BROWSER_WAIT_MED = 3
BROWSER_WAIT_LONG = 30
BROWSER_WAIT_EXTRA_LONG = 60


class NetgearRAXE500AP(WifiRetailAP):
    """Class that implements Netgear RAXE500 AP.

    Since most of the class' implementation is shared with the R7000, this
    class inherits from NetgearR7000AP and simply redefines config parameters
    """

    def __init__(self, ap_settings):
        super().__init__(ap_settings)
        self.init_gui_data()
        # Read and update AP settings
        self.read_ap_firmware()
        self.read_ap_settings()
        self.update_ap_settings(ap_settings)

    def init_gui_data(self):
        self.config_page = (
            '{protocol}://{username}:{password}@'
            '{ip_address}:{port}/WLG_wireless_tri_band.htm').format(
                protocol=self.ap_settings['protocol'],
                username=self.ap_settings['admin_username'],
                password=self.ap_settings['admin_password'],
                ip_address=self.ap_settings['ip_address'],
                port=self.ap_settings['port'])
        self.config_page_nologin = (
            '{protocol}://{ip_address}:{port}/'
            'WLG_wireless_tri_band.htm').format(
                protocol=self.ap_settings['protocol'],
                ip_address=self.ap_settings['ip_address'],
                port=self.ap_settings['port'])
        self.config_page_advanced = (
            '{protocol}://{username}:{password}@'
            '{ip_address}:{port}/WLG_adv_tri_band2.htm').format(
                protocol=self.ap_settings['protocol'],
                username=self.ap_settings['admin_username'],
                password=self.ap_settings['admin_password'],
                ip_address=self.ap_settings['ip_address'],
                port=self.ap_settings['port'])
        self.firmware_page = (
            '{protocol}://{username}:{password}@'
            '{ip_address}:{port}/ADVANCED_home2_tri_band.htm').format(
                protocol=self.ap_settings['protocol'],
                username=self.ap_settings['admin_username'],
                password=self.ap_settings['admin_password'],
                ip_address=self.ap_settings['ip_address'],
                port=self.ap_settings['port'])
        self.capabilities = {
            'interfaces': ['2G', '5G_1', '6G'],
            'channels': {
                '2G': [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
                '5G_1': [
                    36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116,
                    120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165
                ],
                '6G': ['6g' + str(ch) for ch in numpy.arange(37, 222, 16)]
            },
            'modes': {
                '2G': ['VHT20', 'VHT40', 'HE20', 'HE40'],
                '5G_1': [
                    'VHT20', 'VHT40', 'VHT80', 'VHT160', 'HE20', 'HE40',
                    'HE80', 'HE160'
                ],
                '6G': [
                    'VHT20', 'VHT40', 'VHT80', 'VHT160', 'HE20', 'HE40',
                    'HE80', 'HE160'
                ]
            },
            'default_mode': 'HE'
        }
        for interface in self.capabilities['interfaces']:
            self.ap_settings[interface] = {}

        self.region_map = {
            '3': 'Australia',
            '4': 'Canada',
            '5': 'Europe',
            '7': 'Japan',
            '8': 'Korea',
            '11': 'North America',
            '16': 'China',
            '17': 'India',
            '21': 'Middle East(Saudi Arabia/United Arab Emirates)',
            '23': 'Singapore',
            '25': 'Hong Kong',
            '26': 'Vietnam'
        }

        self.bw_mode_text = {
            '2G': {
                'g and b': 'Up to 54 Mbps',
                'HE20': 'Up to 600 Mbps',
                'HE40': 'Up to 1200 Mbps',
                'VHT20': 'Up to 433 Mbps',
                'VHT40': 'Up to 1000 Mbps'
            },
            '5G_1': {
                'HE20': 'Up to 600 Mbps',
                'HE40': 'Up to 1200 Mbps',
                'HE80': 'Up to 2400 Mbps',
                'HE160': 'Up to 4800 Mbps',
                'VHT20': 'Up to 433 Mbps',
                'VHT40': 'Up to 1000 Mbps',
                'VHT80': 'Up to 2165 Mbps',
                'VHT160': 'Up to 4330 Mbps'
            },
            '6G': {
                'HE20': 'Up to 600 Mbps',
                'HE40': 'Up to 1200 Mbps',
                'HE80': 'Up to 2400 Mbps',
                'HE160': 'Up to 4800 Mbps',
                'VHT20': 'Up to 600 Mbps',
                'VHT40': 'Up to 1200 Mbps',
                'VHT80': 'Up to 2400 Mbps',
                'VHT160': 'Up to 4800 Mbps'
            }
        }
        self.bw_mode_values = {
            # first key is a boolean indicating if 11ax is enabled
            0: {
                'g and b': '11g',
                'HT20': 'VHT20',
                'HT40': 'VHT40',
                'HT80': 'VHT80',
                'HT160': 'VHT160'
            },
            1: {
                'g and b': '11g',
                'HT20': 'HE20',
                'HT40': 'HE40',
                'HT80': 'HE80',
                'HT160': 'HE160'
            }
        }

        # Config ordering intentional to avoid GUI bugs
        self.config_page_fields = collections.OrderedDict([
            ('region', 'WRegion'), ('enable_ax', 'enable_he'),
            (('2G', 'status'), 'enable_ap'),
            (('5G_1', 'status'), 'enable_ap_an'),
            (('6G', 'status'), 'enable_ap_an_2'), (('2G', 'ssid'), 'ssid'),
            (('5G_1', 'ssid'), 'ssid_an'), (('6G', 'ssid'), 'ssid_an_2'),
            (('2G', 'channel'), 'w_channel'),
            (('5G_1', 'channel'), 'w_channel_an'),
            (('6G', 'channel'), 'w_channel_an_2'),
            (('2G', 'bandwidth'), 'opmode'),
            (('5G_1', 'bandwidth'), 'opmode_an'),
            (('6G', 'bandwidth'), 'opmode_an_2'),
            (('6G', 'security_type'), 'security_type_an_2'),
            (('5G_1', 'security_type'), 'security_type_an'),
            (('2G', 'security_type'), 'security_type'),
            (('2G', 'password'), 'passphrase'),
            (('5G_1', 'password'), 'passphrase_an'),
            (('6G', 'password'), 'passphrase_an_2')
        ])

    def _set_channel_and_bandwidth(self,
                                   network,
                                   channel=None,
                                   bandwidth=None):
        """Helper function that sets network bandwidth and channel.

        Args:
            network: string containing network identifier (2G, 5G_1, 5G_2)
            channel: desired channel
            bandwidth: string containing mode, e.g. 11g, VHT20, VHT40, VHT80.
        """

        setting_to_update = {network: {}}
        if channel:
            if channel not in self.capabilities['channels'][network]:
                raise RuntimeError('Ch{} is not supported on {} interface.'.format(
                    channel, network))
            if isinstance(channel, str) and '6g' in channel:
                channel = int(channel[2:])
            setting_to_update[network]['channel'] = channel

        if bandwidth is None:
            return setting_to_update

        if 'bw' in bandwidth:
            bandwidth = bandwidth.replace('bw',
                                          self.capabilities['default_mode'])
        if bandwidth not in self.capabilities['modes'][network]:
            raise RuntimeError('{} mode is not supported on {} interface.'.format(
                bandwidth, network))
        setting_to_update[network]['bandwidth'] = str(bandwidth)
        setting_to_update['enable_ax'] = int('HE' in bandwidth)
        # Check if other interfaces need to be changed too
        requested_mode = 'HE' if 'HE' in bandwidth else 'VHT'
        for other_network in self.capabilities['interfaces']:
            if other_network == network:
                continue
            other_mode = 'HE' if 'HE' in self.ap_settings[other_network][
                'bandwidth'] else 'VHT'
            other_bw = ''.join([
                x for x in self.ap_settings[other_network]['bandwidth']
                if x.isdigit()
            ])
            if other_mode != requested_mode:
                updated_mode = '{}{}'.format(requested_mode, other_bw)
                self.log.warning('All networks must be VHT or HE. '
                                 'Updating {} to {}'.format(
                                     other_network, updated_mode))
                setting_to_update.setdefault(other_network, {})
                setting_to_update[other_network]['bandwidth'] = updated_mode
        return setting_to_update

    def set_bandwidth(self, network, bandwidth):
        """Function that sets network bandwidth/mode.

        Args:
            network: string containing network identifier (2G, 5G_1, 5G_2)
            bandwidth: string containing mode, e.g. 11g, VHT20, VHT40, VHT80.
        """

        setting_to_update = self._set_channel_and_bandwidth(
            network, bandwidth=bandwidth)
        self.update_ap_settings(setting_to_update)

    def set_channel(self, network, channel):
        """Function that sets network channel.

        Args:
            network: string containing network identifier (2G, 5G_1, 5G_2)
            channel: string or int containing channel
        """
        setting_to_update = self._set_channel_and_bandwidth(network,
                                                            channel=channel)
        self.update_ap_settings(setting_to_update)

    def set_channel_and_bandwidth(self, network, channel, bandwidth):
        """Function that sets network bandwidth/mode.

        Args:
            network: string containing network identifier (2G, 5G_1, 5G_2)
            channel: desired channel
            bandwidth: string containing mode, e.g. 11g, VHT20, VHT40, VHT80.
        """
        setting_to_update = self._set_channel_and_bandwidth(
            network, channel=channel, bandwidth=bandwidth)
        self.update_ap_settings(setting_to_update)

    def read_ap_firmware(self):
        """Function to read ap settings."""
        with BlockingBrowser(self.ap_settings['headless_browser'],
                             900) as browser:

            # Visit URL
            browser.visit_persistent(self.firmware_page, BROWSER_WAIT_MED, 10)
            firmware_regex = re.compile(
                r'Firmware Version[\s\S]+V(?P<version>[0-9._]+)')
            #firmware_version = re.search(firmware_regex, browser.html)
            firmware_version = re.search(firmware_regex,
                                         browser.driver.page_source)
            if firmware_version:
                self.ap_settings['firmware_version'] = firmware_version.group(
                    'version')
            else:
                self.ap_settings['firmware_version'] = -1

    def read_ap_settings(self):
        """Function to read ap settings."""
        with BlockingBrowser(self.ap_settings['headless_browser'],
                             900) as browser:
            # Visit URL
            browser.visit_persistent(self.config_page, BROWSER_WAIT_MED, 10)

            for field_key, field_name in self.config_page_fields.items():
                if 'status' in field_key:
                    browser.visit_persistent(self.config_page_advanced,
                                             BROWSER_WAIT_MED, 10)
                    field_value = browser.get_element_value(field_name)
                    self.ap_settings[field_key[0]][field_key[1]] = int(
                        field_value)
                    browser.visit_persistent(self.config_page,
                                             BROWSER_WAIT_MED, 10)
                else:
                    field_value = browser.get_element_value(field_name)
                    if 'enable_ax' in field_key:
                        self.ap_settings[field_key] = int(field_value)
                    elif 'bandwidth' in field_key:
                        self.ap_settings[field_key[0]][
                            field_key[1]] = self.bw_mode_values[
                                self.ap_settings['enable_ax']][field_value]
                    elif 'region' in field_key:
                        self.ap_settings['region'] = self.region_map[
                            field_value]
                    elif 'security_type' in field_key:
                        self.ap_settings[field_key[0]][
                            field_key[1]] = field_value
                    elif 'channel' in field_key:
                        self.ap_settings[field_key[0]][field_key[1]] = int(
                            field_value)
                    else:
                        self.ap_settings[field_key[0]][
                            field_key[1]] = field_value
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
            if browser.is_element_enabled(self.config_page_fields['region']):
                browser.set_element_value(self.config_page_fields['region'],
                                          self.ap_settings['region'],
                                          select_method='text')
            else:
                self.log.warning('Cannot change region.')
            for field_key, field_name in self.config_page_fields.items():
                if 'enable_ax' in field_key:
                    browser.set_element_value(field_name,
                                              self.ap_settings['enable_ax'])
                elif 'bandwidth' in field_key:
                    try:
                        browser.set_element_value(
                            field_name,
                            self.bw_mode_text[field_key[0]][self.ap_settings[
                                field_key[0]][field_key[1]]],
                            select_method='text')
                    except AttributeError:
                        self.log.warning(
                            'Cannot select bandwidth. Keeping AP default.')

            # Update security settings (passwords updated only if applicable)
            for field_key, field_name in self.config_page_fields.items():
                if 'security_type' in field_key:
                    browser.set_element_value(
                        field_name,
                        self.ap_settings[field_key[0]][field_key[1]])
                    if 'WPA' in self.ap_settings[field_key[0]][field_key[1]]:
                        browser.set_element_value(
                            self.config_page_fields[(field_key[0],
                                                     'password')],
                            self.ap_settings[field_key[0]]['password'])

            for field_key, field_name in self.config_page_fields.items():
                if 'ssid' in field_key:
                    browser.set_element_value(
                        field_name,
                        self.ap_settings[field_key[0]][field_key[1]])
                elif 'channel' in field_key:
                    try:
                        browser.set_element_value(
                            field_name,
                            self.ap_settings[field_key[0]][field_key[1]])
                    except AttributeError:
                        self.log.warning(
                            'Cannot select channel. Keeping AP default.')
                    browser.accept_alert_if_present(BROWSER_WAIT_SHORT)

            time.sleep(BROWSER_WAIT_SHORT)
            browser.click_button('Apply')
            browser.accept_alert_if_present(BROWSER_WAIT_SHORT)
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
            for field_key, field_name in self.config_page_fields.items():
                if 'status' in field_key:
                    browser.set_element_value(
                        field_name,
                        self.ap_settings[field_key[0]][field_key[1]])

            time.sleep(BROWSER_WAIT_SHORT)
            browser.click_button('Apply')
            time.sleep(BROWSER_WAIT_EXTRA_LONG)
            browser.visit_persistent(self.config_page, BROWSER_WAIT_EXTRA_LONG,
                                     10)
