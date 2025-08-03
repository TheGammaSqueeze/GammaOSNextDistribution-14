import collections
import numpy
import paramiko
import time
from acts_contrib.test_utils.wifi.wifi_retail_ap import WifiRetailAP
from acts_contrib.test_utils.wifi.wifi_retail_ap import BlockingBrowser

BROWSER_WAIT_SHORT = 1
BROWSER_WAIT_MED = 3
BROWSER_WAIT_LONG = 10
BROWSER_WAIT_EXTRA_LONG = 60
SSH_WAIT_SHORT = 0.1
SSH_READ_BYTES = 600000


class BrcmRefAP(WifiRetailAP):
    """Class that implements Netgear RAX200 AP.

    Since most of the class' implementation is shared with the R7000, this
    class inherits from NetgearR7000AP and simply redefines config parameters
    """

    def __init__(self, ap_settings):
        super().__init__(ap_settings)
        self.init_gui_data()
        # Initialize SSH connection
        self.init_ssh_connection()
        # Read and update AP settings
        self.read_ap_settings()
        self.update_ap_settings(ap_settings)

    def teardown(self):
        """Function to perform destroy operations."""
        if self.ap_settings.get('lock_ap', 0):
            self._unlock_ap()
        self.close_ssh_connection()

    def init_ssh_connection(self):
        self.ssh_client = paramiko.SSHClient()
        self.ssh_client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self.ssh_client.connect(hostname=self.ap_settings['ip_address'],
                                username=self.ap_settings['admin_username'],
                                password=self.ap_settings['admin_password'],
                                look_for_keys=False,
                                allow_agent=False)

    def close_ssh_connection(self):
        self.ssh_client.close()

    def run_ssh_cmd(self, command):
        with self.ssh_client.invoke_shell() as shell:
            shell.send('sh\n')
            time.sleep(SSH_WAIT_SHORT)
            shell.recv(SSH_READ_BYTES)
            shell.send('{}\n'.format(command))
            time.sleep(SSH_WAIT_SHORT)
            response = shell.recv(SSH_READ_BYTES).decode('utf-8').splitlines()
            response = [line for line in response[1:] if line != '# ']
        return response

    def init_gui_data(self):
        self.config_page = ('{protocol}://{username}:{password}@'
                            '{ip_address}:{port}/info.html').format(
                                protocol=self.ap_settings['protocol'],
                                username=self.ap_settings['admin_username'],
                                password=self.ap_settings['admin_password'],
                                ip_address=self.ap_settings['ip_address'],
                                port=self.ap_settings['port'])
        self.config_page_nologin = (
            '{protocol}://{ip_address}:{port}/'
            'wlrouter/radio.asp').format(
                protocol=self.ap_settings['protocol'],
                ip_address=self.ap_settings['ip_address'],
                port=self.ap_settings['port'])

        self.capabilities = {
            'interfaces': ['2G_5G', '6G'],
            'channels': {
                '2G_5G': [
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 36, 40, 44, 48, 52, 56,
                    60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136,
                    140, 144, 149, 153, 157, 161, 165
                ],
                '6G': ['6g' + str(ch) for ch in numpy.arange(1, 222, 4)]
            },
            'modes': {
                '2G_5G': [
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
        self.ap_settings['region'] = 'United States'
        for interface in self.capabilities['interfaces']:
            self.ap_settings[interface] = {
                'ssid': 'BrcmAP0' if interface == '6G' else 'BrcmAP1',
                'security_type': 'Open',
                'password': '1234567890'
            }
        self.config_page_fields = collections.OrderedDict({
            ('2G_5G', 'interface'): ('wl_unit', 1),
            ('2G_5G', 'band'):
            'wl_nband',
            ('2G_5G', 'bandwidth'):
            'wl_bw_cap',
            ('2G_5G', 'channel'):
            'wl_chanspec',
            ('6G', 'interface'): ('wl_unit', 0),
            ('6G', 'band'):
            'wl_nband',
            ('6G', 'bandwidth'):
            'wl_bw_cap',
            ('6G', 'channel'):
            'wl_chanspec',
        })

        self.band_mode_values = {'1': '5 GHz', '2': '2.4 GHz', '4': '6 GHz'}

        self.band_values = {'5 GHz': 1, '2.4 GHz': 2, '6 GHz': 4}

        self.bandwidth_mode_values = {
            '1': 'HE20',
            '3': 'HE40',
            '7': 'HE80',
            '15': 'HE160'
        }

    def _decode_channel_string(self, channel_string):
        if channel_string == '0':
            return 'Auto'
        if 'u' in channel_string or 'l' in channel_string:
            channel_string = channel_string[0:-1]
        elif len(channel_string.split('/')) > 1:
            channel_string = channel_string.split('/')[0]
        if '6g' in channel_string:
            return channel_string
        else:
            return int(channel_string)

    def _get_channel_str(self, interface, channel, bandwidth):
        bandwidth = int(''.join([x for x in bandwidth if x.isdigit()]))
        if bandwidth == 20:
            channel_str = str(channel)
        elif bandwidth in [80, 160]:
            channel_str = str(channel) + '/' + str(bandwidth)
        elif interface == '6G' and bandwidth == 40:
            channel_str = str(channel) + '/' + str(bandwidth)
        elif interface == '2G_5G' and bandwidth == 40:
            lower_lookup = [
                36, 44, 52, 60, 100, 108, 116, 124, 132, 140, 149, 157
            ]
            if int(channel) in lower_lookup:
                channel_str = str(channel) + 'l'
            else:
                channel_str = str(channel) + 'u'
        return channel_str

    def read_ap_settings(self):
        with BlockingBrowser(self.ap_settings['headless_browser'],
                             900) as browser:
            # Visit URL
            browser.visit_persistent(self.config_page, BROWSER_WAIT_MED, 10)
            browser.visit_persistent(self.config_page_nologin,
                                     BROWSER_WAIT_MED, 10, self.config_page)

            for key in self.config_page_fields.keys():
                if 'interface' in key:
                    config_item = browser.find_by_name(
                        self.config_page_fields[key][0]).first
                    config_item.select(self.config_page_fields[key][1])
                    time.sleep(BROWSER_WAIT_SHORT)
                else:
                    config_item = browser.find_by_name(
                        self.config_page_fields[key]).first
                    if 'band' in key:
                        self.ap_settings[key[0]][
                            key[1]] = self.band_mode_values[config_item.value]
                    elif 'bandwidth' in key:
                        self.ap_settings[key[0]][key[
                            1]] = self.bandwidth_mode_values[config_item.value]
                    elif 'channel' in key:
                        self.ap_settings[key[0]][
                            key[1]] = self._decode_channel_string(
                                config_item.value)
                    else:
                        self.ap_settings[key[0]][key[1]] = config_item.value

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

        updating_6G = '6G' in settings_to_update.keys()
        updating_2G_5G = '2G_5G' in settings_to_update.keys()

        if updating_2G_5G:
            if 'channel' in settings_to_update['2G_5G']:
                band = '2.4 GHz' if int(
                    settings_to_update['2G_5G']['channel']) < 13 else '5 GHz'
                if band == '2.4 GHz':
                    settings_to_update['2G_5G']['bandwidth'] = 'HE20'
                settings_to_update['2G_5G']['band'] = band
        self.ap_settings, updates_requested, status_toggle_flag = self._update_settings_dict(
            self.ap_settings, settings_to_update)
        if updates_requested:
            self.configure_ap(updating_2G_5G, updating_6G)

    def configure_ap(self, updating_2G_5G, updating_6G):

        with BlockingBrowser(self.ap_settings['headless_browser'],
                             900) as browser:

            interfaces_to_update = []
            if updating_2G_5G:
                interfaces_to_update.append('2G_5G')
            if updating_6G:
                interfaces_to_update.append('6G')
            for interface in interfaces_to_update:
                # Visit URL
                browser.visit_persistent(self.config_page, BROWSER_WAIT_MED,
                                         10)
                browser.visit_persistent(self.config_page_nologin,
                                         BROWSER_WAIT_MED, 10,
                                         self.config_page)

                config_item = browser.find_by_name(
                    self.config_page_fields[(interface, 'interface')][0]).first
                config_item.select(self.config_page_fields[(interface,
                                                            'interface')][1])
                time.sleep(BROWSER_WAIT_SHORT)

                for key, value in self.config_page_fields.items():
                    if 'interface' in key or interface not in key:
                        continue
                    config_item = browser.find_by_name(
                        self.config_page_fields[key]).first
                    if 'band' in key:
                        config_item.select(
                            self.band_values[self.ap_settings[key[0]][key[1]]])
                    elif 'bandwidth' in key:
                        config_item.select_by_text(
                            str(self.ap_settings[key[0]][key[1]])[2:] + ' MHz')
                    elif 'channel' in key:
                        channel_str = self._get_channel_str(
                            interface, self.ap_settings[interface][key[1]],
                            self.ap_settings[interface]['bandwidth'])
                        config_item.select_by_text(channel_str)
                    else:
                        self.ap_settings[key[0]][key[1]] = config_item.value
                    time.sleep(BROWSER_WAIT_SHORT)
                # Apply
                config_item = browser.find_by_name('action')
                config_item.first.click()
                time.sleep(BROWSER_WAIT_MED)
                config_item = browser.find_by_name('action')
                time.sleep(BROWSER_WAIT_SHORT)
                config_item.first.click()
                time.sleep(BROWSER_WAIT_LONG)
                browser.visit_persistent(self.config_page, BROWSER_WAIT_LONG,
                                         10)

    def set_power(self, interface, power):
        """Function that sets interface transmit power.

        Args:
            interface: string containing interface identifier (2G_5G, 6G)
            power: power level in dBm
        """
        wl_interface = 'wl0' if interface == '6G' else 'wl1'

        if power == 'auto':
            response = self.run_ssh_cmd(
                'wl -i {} txpwr1 -1'.format(wl_interface))
        else:
            power_qdbm = int(power * 4)
            response = self.run_ssh_cmd('wl -i {} txpwr1 -o -q {}'.format(
                wl_interface, power_qdbm))

        self.ap_settings[interface]['power'] = power_qdbm / 4

    def get_power(self, interface):
        """Function to get power used by AP

        Args:
            interface: interface to get rate on (2G_5G, 6G)
        Returns:
            power string returned by AP.
        """
        wl_interface = 'wl0' if interface == '6G' else 'wl1'
        return self.run_ssh_cmd('wl -i {} txpwr1'.format(wl_interface))

    def set_rate(self,
                 interface,
                 mode=None,
                 num_streams=None,
                 rate='auto',
                 short_gi=0,
                 tx_expansion=0):
        """Function that sets rate.

        Args:
            interface: string containing interface identifier (2G, 5G_1)
            mode: string indicating the WiFi standard to use
            num_streams: number of MIMO streams. used only for VHT
            rate: data rate of MCS index to use
            short_gi: boolean controlling the use of short guard interval
        """
        wl_interface = 'wl0' if interface == '6G' else 'wl1'

        if interface == '6G':
            band_rate = '6g_rate'
        elif self.ap_settings['2G_5G']['channel'] < 13:
            band_rate = '2g_rate'
        else:
            band_rate = '5g_rate'

        if rate == 'auto':
            cmd_string = 'wl -i {} {} auto'.format(wl_interface, band_rate)
        elif 'legacy' in mode.lower():
            cmd_string = 'wl -i {} {} -r {} -x {}'.format(
                wl_interface, band_rate, rate, tx_expansion)
        elif 'ht' in mode.lower():
            cmd_string = 'wl -i {} {} -h {} -x {}'.format(
                wl_interface, band_rate, rate, tx_expansion)
            if short_gi:
                cmd_string = cmd_string + '--sgi'
        elif 'vht' in mode.lower():
            cmd_string = 'wl -i {} {} -v {}x{} -x {}'.format(
                wl_interface, band_rate, rate, num_streams, tx_expansion)
            if short_gi:
                cmd_string = cmd_string + '--sgi'
        elif 'he' in mode.lower():
            cmd_string = 'wl -i {} {} -e {}x{} -l -x {}'.format(
                wl_interface, band_rate, rate, num_streams, tx_expansion)
            if short_gi:
                cmd_string = cmd_string + '-i {}'.format(short_gi)

        response = self.run_ssh_cmd(cmd_string)

        self.ap_settings[interface]['mode'] = mode
        self.ap_settings[interface]['num_streams'] = num_streams
        self.ap_settings[interface]['rate'] = rate
        self.ap_settings[interface]['short_gi'] = short_gi

    def get_rate(self, interface):
        """Function to get rate used by AP

        Args:
            interface: interface to get rate on (2G_5G, 6G)
        Returns:
            rate string returned by AP.
        """

        wl_interface = 'wl0' if interface == '6G' else 'wl1'

        if interface == '6G':
            band_rate = '6g_rate'
        elif self.ap_settings['2G_5G']['channel'] < 13:
            band_rate = '2g_rate'
        else:
            band_rate = '5g_rate'
        return self.run_ssh_cmd('wl -i {} {}'.format(wl_interface, band_rate))

    def set_rts_enable(self, interface, enable):
        """Function to enable or disable RTS/CTS

        Args:
            interface: interface to be configured (2G_5G, 6G)
            enable: boolean controlling RTS/CTS behavior
        """
        wl_interface = 'wl0' if interface == '6G' else 'wl1'
        if enable:
            self.run_ssh_cmd('wl -i {} ampdu_rts 1'.format(wl_interface))
            self.run_ssh_cmd('wl -i {} rtsthresh 2437'.format(wl_interface))
        else:
            self.run_ssh_cmd('wl -i {} ampdu_rts 0'.format(wl_interface))
            self.run_ssh_cmd('wl -i {} rtsthresh 15000'.format(wl_interface))

    def set_tx_beamformer(self, interface, enable):
        """Function to enable or disable transmit beamforming

        Args:
            interface: interface to be configured (2G_5G, 6G)
            enable: boolean controlling beamformer behavior
        """
        wl_interface = 'wl0' if interface == '6G' else 'wl1'

        self.run_ssh_cmd('wl down')
        self.run_ssh_cmd('wl -i {} txbf {}'.format(wl_interface, int(enable)))
        self.run_ssh_cmd('wl up')

    def get_sta_rssi(self, interface, sta_macaddr):
        """Function to get RSSI from connected STA

        Args:
            interface: interface to be configured (2G_5G, 6G)
            sta_macaddr: mac address of STA of interest
        """
        wl_interface = 'wl0' if interface == '6G' else 'wl1'

        return self.run_ssh_cmd('wl -i {} phy_rssi_ant {}'.format(
            wl_interface, sta_macaddr))
