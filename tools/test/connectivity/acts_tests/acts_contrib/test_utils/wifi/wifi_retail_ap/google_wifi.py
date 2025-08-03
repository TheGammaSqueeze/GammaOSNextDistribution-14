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

from acts.controllers import access_point
from acts.controllers.ap_lib import bridge_interface
from acts.controllers.ap_lib import hostapd_security
from acts.controllers.ap_lib import hostapd_ap_preset
from acts_contrib.test_utils.wifi.wifi_retail_ap import WifiRetailAP


class GoogleWifiAP(WifiRetailAP):
    """ Class that implements Google Wifi AP.

    This class is a work in progress
    """

    def __init__(self, ap_settings):
        super().__init__(ap_settings)
        # Initialize AP
        if self.ap_settings['2G']['status'] and self.ap_settings['5G_1'][
                'status']:
            raise ValueError('Error initializing Google Wifi AP. '
                             'Only one interface can be enabled at a time.')

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
            self.ap_settings.setdefault(interface, {})

        self.BW_MODE_MAP = {
            'legacy': 20,
            'VHT20': 20,
            'VHT40': 40,
            'VHT80': 80
        }
        self.default_settings = {
            'region': 'United States',
            'brand': 'Google',
            'model': 'Wifi',
            'hostapd_profile': 'whirlwind',
            '2G': {
                'status': 0,
                'ssid': 'GoogleWifi_2G',
                'channel': 11,
                'bandwidth': 'VHT20',
                'power': 'auto',
                'mode': None,
                'num_streams': None,
                'rate': 'auto',
                'short_gi': 0,
                'security_type': 'Open',
                'password': 'password',
                'subnet': '192.168.1.0/24'
            },
            '5G_1': {
                'status': 0,
                'ssid': 'GoogleWifi_2G',
                'channel': 11,
                'bandwidth': 'VHT20',
                'power': 'auto',
                'mode': None,
                'num_streams': None,
                'rate': 'auto',
                'short_gi': 0,
                'security_type': 'Open',
                'password': 'password',
                'subnet': '192.168.9.0/24'
            }
        }
        for setting in self.default_settings.keys():
            if setting in self.capabilities['interfaces']:
                continue
            elif setting not in self.ap_settings:
                self.log.debug(
                    '{0} {1} not found during init. Setting {0} = {1}'.format(
                        setting, self.default_settings[setting]))
                self.ap_settings[setting] = self.default_settings[setting]

        for interface in self.capabilities['interfaces']:
            for setting in self.default_settings[interface].keys():
                if setting not in self.ap_settings[interface]:
                    self.log.debug(
                        '{0} {1} not found during init. Setting {1} = {2}'.
                        format(interface, setting,
                               self.default_settings[interface][setting]))
                    self.ap_settings[interface][
                        setting] = self.default_settings[interface][setting]
        init_settings = self.ap_settings.copy()
        init_settings['ap_subnet'] = {
            '2g': self.ap_settings['2G']['subnet'],
            '5g': self.ap_settings['5G_1']['subnet']
        }
        self.access_point = access_point.AccessPoint(init_settings)
        self.configure_ap()

    def teardown(self):
        self.access_point.stop_all_aps()
        super().teardown()

    def read_ap_settings(self):
        """Function that reads current ap settings."""
        return self.ap_settings.copy()

    def update_ap_settings(self, dict_settings={}, **named_settings):
        """Function to update settings of existing AP.

        Function copies arguments into ap_settings and calls configure_ap
        to apply them.

        Args:
            dict_settings: single dictionary of settings to update
            **named_settings: named settings to update
            Note: dict and named_settings cannot contain the same settings.
        """
        settings_to_update = dict(dict_settings, **named_settings)
        if len(settings_to_update) != len(dict_settings) + len(named_settings):
            raise KeyError('The following keys were passed twice: {}'.format(
                (set(dict_settings.keys()).intersection(
                    set(named_settings.keys())))))

        updating_2G = '2G' in settings_to_update.keys()
        updating_5G_1 = '5G_1' in settings_to_update.keys()
        if updating_2G and updating_5G_1:
            raise ValueError(
                'Error updating Google WiFi AP. '
                'One interface can be activated and updated at a time')
        elif updating_2G:
            # If updating an interface and not explicitly setting its status,
            # it is assumed that the interface is to be ENABLED and updated
            if 'status' not in settings_to_update['2G']:
                settings_to_update['2G']['status'] = 1
                settings_to_update['5G_1'] = {'status': 0}
        elif updating_5G_1:
            if 'status' not in settings_to_update['5G_1']:
                settings_to_update['2G'] = {'status': 0}
                settings_to_update['5G_1']['status'] = 1
        self.ap_settings, updates_requested, status_toggle_flag = self._update_settings_dict(
            self.ap_settings, settings_to_update)
        if updates_requested:
            self.configure_ap()

    def configure_ap(self):
        """Function to configure Google Wifi."""
        self.log.info('Stopping Google Wifi interfaces.')
        print(self.ap_settings)
        self.access_point.stop_all_aps()

        if self.ap_settings['2G']['status'] == 1:
            interface = '2G'
            self.log.info('Bringing up 2.4 GHz interface.')
        elif self.ap_settings['5G_1']['status'] == 1:
            interface = '5G_1'
            self.log.info('Bringing up 5 GHz interface.')
        else:
            return

        bss_settings = []
        ssid = self.ap_settings[interface]['ssid']
        security_mode = self.ap_settings[interface]['security_type'].lower()
        if 'wpa' in security_mode:
            password = self.ap_settings[interface]['password']
            security = hostapd_security.Security(security_mode=security_mode,
                                                 password=password)
        else:
            security = hostapd_security.Security(security_mode=None,
                                                 password=None)
        channel = int(self.ap_settings[interface]['channel'])
        bandwidth = self.BW_MODE_MAP[self.ap_settings[interface]['bandwidth']]
        config = hostapd_ap_preset.create_ap_preset(
            channel=channel,
            ssid=ssid,
            security=security,
            bss_settings=bss_settings,
            vht_bandwidth=bandwidth,
            profile_name=self.ap_settings['hostapd_profile'],
            iface_wlan_2g=self.access_point.wlan_2g,
            iface_wlan_5g=self.access_point.wlan_5g)
        config_bridge = self.access_point.generate_bridge_configs(channel)
        brconfigs = bridge_interface.BridgeInterfaceConfigs(
            config_bridge[0], 'lan0', config_bridge[2])
        self.access_point.bridge.startup(brconfigs)
        self.access_point.start_ap(config)
        self.set_power(interface, self.ap_settings[interface]['power'])
        self.set_rate(interface,
                      mode=self.ap_settings[interface]['mode'],
                      num_streams=self.ap_settings[interface]['num_streams'],
                      rate=self.ap_settings[interface]['rate'],
                      short_gi=self.ap_settings[interface]['short_gi'])
        self.log.info('AP started on channel {} with SSID {}'.format(
            channel, ssid))

    def set_power(self, interface, power):
        """Function that sets interface transmit power.

        Args:
            interface: string containing interface identifier (2G, 5G_1)
            power: power level in dBm
        """
        if power == 'auto':
            power_string = 'auto'
        else:
            if not float(power).is_integer():
                self.log.info(
                    'Power in dBm must be an integer. Setting to {}'.format(
                        int(power)))
            power = int(power)
            power_string = 'fixed {}'.format(int(power) * 100)

        if '2G' in interface:
            interface_long = self.access_point.wlan_2g
            self.ap_settings[interface]['power'] = power
        elif '5G_1' in interface:
            interface_long = self.access_point.wlan_5g
            self.ap_settings[interface]['power'] = power
        self.access_point.ssh.run('iw dev {} set txpower {}'.format(
            interface_long, power_string))

    def set_rate(self,
                 interface,
                 mode=None,
                 num_streams=None,
                 rate='auto',
                 short_gi=0):
        """Function that sets rate.

        Args:
            interface: string containing interface identifier (2G, 5G_1)
            mode: string indicating the WiFi standard to use
            num_streams: number of MIMO streams. used only for VHT
            rate: data rate of MCS index to use
            short_gi: boolean controlling the use of short guard interval
        """
        if '2G' in interface:
            interface_long = self.access_point.wlan_2g
            interface_short = '2.4'
        elif '5G_1' in interface:
            interface_long = self.access_point.wlan_5g
            interface_short = '5'
        self.ap_settings[interface]['mode'] = mode
        self.ap_settings[interface]['num_streams'] = num_streams
        self.ap_settings[interface]['rate'] = rate
        self.ap_settings[interface]['short_gi'] = short_gi

        if rate == 'auto':
            cmd_string = 'iw dev {0} set bitrates'.format(interface_long)
        elif 'legacy' in mode.lower():
            cmd_string = 'iw dev {0} set bitrates legacy-{1} {2} ht-mcs-{1} vht-mcs-{1}'.format(
                interface_long, interface_short, rate)
        elif 'vht' in mode.lower():
            cmd_string = 'iw dev {0} set bitrates legacy-{1} ht-mcs-{1} vht-mcs-{1} {2}:{3}'.format(
                interface_long, interface_short, num_streams, rate)
            if short_gi:
                cmd_string = cmd_string + ' sgi-{}'.format(interface_short)
        elif 'ht' in mode.lower():
            cmd_string = 'iw dev {0} set bitrates legacy-{1} ht-mcs-{1} {2} vht-mcs-{1}'.format(
                interface_long, interface_short, rate)
            if short_gi:
                cmd_string = cmd_string + ' sgi-{}'.format(interface_short)
        self.access_point.ssh.run(cmd_string)
