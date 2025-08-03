#!/usr/bin/env python3.4
#
#   Copyright 2017 - The Android Open Source Project
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
import csv
import itertools
import json
import logging
import math
import os
import re
import scipy.stats
import time
from acts import asserts
from acts import context
from acts import base_test
from acts import utils
from acts.controllers.utils_lib import ssh
from acts.metrics.loggers.blackbox import BlackboxMappedMetricLogger
from acts_contrib.test_utils.wifi import ota_sniffer
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils
from acts_contrib.test_utils.wifi import wifi_retail_ap as retail_ap
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from functools import partial


class WifiTxPowerCheckTest(base_test.BaseTestClass):
    """Class for ping-based Wifi performance tests.

    This class implements WiFi ping performance tests such as range and RTT.
    The class setups up the AP in the desired configurations, configures
    and connects the phone to the AP, and runs  For an example config file to
    run this test class see example_connectivity_performance_ap_sta.json.
    """

    TEST_TIMEOUT = 10
    RSSI_POLL_INTERVAL = 0.2
    SHORT_SLEEP = 1
    MED_SLEEP = 5
    MAX_CONSECUTIVE_ZEROS = 5
    DISCONNECTED_PING_RESULT = {
        'connected': 0,
        'rtt': [],
        'time_stamp': [],
        'ping_interarrivals': [],
        'packet_loss_percentage': 100
    }

    BRCM_SAR_MAPPING = {
        0: 'disable',
        1: 'head',
        2: 'grip',
        16: 'bt',
        32: 'hotspot'
    }

    BAND_TO_CHANNEL_MAP = {
        ('2g', 1): [1, 6, 11],
        ('5g', 1): [36, 40, 44, 48],
        ('5g', 2): [52, 56, 60, 64],
        ('5g', 3): range(100, 148, 4),
        ('5g', 4): [149, 153, 157, 161],
        ('6g', 1): ['6g{}'.format(channel) for channel in range(1, 46, 4)],
        ('6g', 2): ['6g{}'.format(channel) for channel in range(49, 94, 4)],
        ('6g', 3): ['6g{}'.format(channel) for channel in range(97, 114, 4)],
        ('6g', 4): ['6g{}'.format(channel) for channel in range(117, 158, 4)],
        ('6g', 5): ['6g{}'.format(channel) for channel in range(161, 186, 4)],
        ('6g', 6): ['6g{}'.format(channel) for channel in range(189, 234, 4)]
    }

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        self.tests = self.generate_test_cases(
            ap_power='standard',
            channels=[6, 36, 52, 100, 149, '6g37', '6g117', '6g213'],
            modes=['bw20', 'bw40', 'bw80', 'bw160'],
            test_types=[
                'test_tx_power',
            ],
            country_codes=['US', 'GB', 'JP', 'CA', 'AU'],
            sar_states=range(-1, 13))

    def setup_class(self):
        self.dut = self.android_devices[-1]
        req_params = [
            'tx_power_test_params', 'testbed_params', 'main_network',
            'RetailAccessPoints', 'RemoteServer'
        ]
        opt_params = ['OTASniffer']
        self.unpack_userparams(req_params, opt_params)
        self.testclass_params = self.tx_power_test_params
        self.num_atten = self.attenuators[0].instrument.num_atten
        self.ping_server = ssh.connection.SshConnection(
            ssh.settings.from_config(self.RemoteServer[0]['ssh_config']))
        self.access_point = retail_ap.create(self.RetailAccessPoints)[0]
        if hasattr(self,
                   'OTASniffer') and self.testbed_params['sniffer_enable']:
            try:
                self.sniffer = ota_sniffer.create(self.OTASniffer)[0]
            except:
                self.log.warning('Could not start sniffer. Disabling sniffs.')
                self.testbed_params['sniffer_enable'] = 0
        self.log.info('Access Point Configuration: {}'.format(
            self.access_point.ap_settings))
        self.log_path = os.path.join(logging.log_path, 'results')
        os.makedirs(self.log_path, exist_ok=True)
        self.atten_dut_chain_map = {}
        self.testclass_results = []

        # Turn WiFi ON
        if self.testclass_params.get('airplane_mode', 1):
            self.log.info('Turning on airplane mode.')
            asserts.assert_true(utils.force_airplane_mode(self.dut, True),
                                'Can not turn on airplane mode.')
        wutils.wifi_toggle_state(self.dut, True)
        self.dut.droid.wifiEnableVerboseLogging(1)
        asserts.assert_equal(self.dut.droid.wifiGetVerboseLoggingLevel(), 1,
                             "Failed to enable WiFi verbose logging.")

        # decode nvram
        self.nvram_sar_data = self.read_nvram_sar_data()
        self.csv_sar_data = self.read_sar_csv(self.testclass_params['sar_csv'])

        # Configure test retries
        self.user_params['retry_tests'] = [self.__class__.__name__]

    def teardown_class(self):
        # Turn WiFi OFF and reset AP
        self.access_point.teardown()
        for dev in self.android_devices:
            wutils.wifi_toggle_state(dev, False)
            dev.go_to_sleep()
        self.process_testclass_results()

    def setup_test(self):
        self.retry_flag = False

    def teardown_test(self):
        self.retry_flag = False

    def on_retry(self):
        """Function to control test logic on retried tests.

        This function is automatically executed on tests that are being
        retried. In this case the function resets wifi, toggles it off and on
        and sets a retry_flag to enable further tweaking the test logic on
        second attempts.
        """
        self.retry_flag = True
        for dev in self.android_devices:
            wutils.reset_wifi(dev)
            wutils.toggle_wifi_off_and_on(dev)

    def read_sar_csv(self, sar_csv):
        """Reads SAR powers from CSV.

        This function reads SAR powers from a CSV and generate a dictionary
        with all programmed TX powers on a per band and regulatory domain
        basis.

        Args:
            sar_csv: path to SAR data file.
        Returns:
            sar_powers: dict containing all SAR data
        """

        sar_powers = {}
        sar_csv_data = []
        with open(sar_csv, mode='r') as f:
            reader = csv.DictReader(f)
            for row in reader:
                row['Sub-band Powers'] = [
                    float(val) for key, val in row.items()
                    if 'Sub-band' in key and val != ''
                ]
                sar_csv_data.append(row)

        for row in sar_csv_data:
            sar_powers.setdefault(int(row['Scenario Index']), {})
            sar_powers[int(row['Scenario Index'])].setdefault('SAR Powers', {})
            sar_row_key = (row['Regulatory Domain'], row['Mode'], row['Band'])
            sar_powers[int(row['Scenario Index'])]['SAR Powers'].setdefault(
                sar_row_key, {})
            sar_powers[int(
                row['Scenario Index'])]['SAR Powers'][sar_row_key][int(
                    row['Chain'])] = row['Sub-band Powers']
        return sar_powers

    def read_nvram_sar_data(self):
        """Reads SAR powers from NVRAM.

        This function reads SAR powers from the NVRAM found on the DUT and
        generates a dictionary with all programmed TX powers on a per band and
        regulatory domain basis. NThe NVRAM file is chosen based on the build,
        but if no NVRAM file is found matching the expected name, the default
        NVRAM will be loaded. The choice of NVRAM is not guaranteed to be
        correct.

        Returns:
            nvram_sar_data: dict containing all SAR data
        """

        self._read_sar_config_info()
        try:
            hardware_version = self.dut.adb.shell(
                'getprop ro.boot.hardware.revision')
            nvram_path = '/vendor/firmware/bcmdhd.cal_{}'.format(
                hardware_version)
            nvram = self.dut.adb.shell('cat {}'.format(nvram_path))
        except:
            nvram = self.dut.adb.shell('cat /vendor/firmware/bcmdhd.cal')
        current_context = context.get_current_context().get_full_output_path()
        file_path = os.path.join(current_context, 'nvram_file')
        with open(file_path, 'w') as file:
            file.write(nvram)
        nvram_sar_data = {}
        for line in nvram.splitlines():
            if 'dynsar' in line:
                sar_config, sar_powers = self._parse_nvram_sar_line(line)
                nvram_sar_data[sar_config] = sar_powers
        file_path = os.path.join(current_context, 'nvram_sar_data')
        with open(file_path, 'w') as file:
            json.dump(wputils.serialize_dict(nvram_sar_data), file, indent=4)

        return nvram_sar_data

    def _read_sar_config_info(self):
        """Function to read SAR scenario mapping,

        This function reads sar_config.info file which contains the mapping
        of SAR scenarios to NVRAM data tables.
        """

        self.sar_state_mapping = collections.OrderedDict([(-2, {
            "google_name":
            'WIFI_POWER_SCENARIO_INVALID'
        }), (-1, {
            "google_name": 'WIFI_POWER_SCENARIO_DISABLE'
        }), (0, {
            "google_name": 'WIFI_POWER_SCENARIO_VOICE_CALL'
        }), (1, {
            "google_name": 'WIFI_POWER_SCENARIO_ON_HEAD_CELL_OFF'
        }), (2, {
            "google_name": 'WIFI_POWER_SCENARIO_ON_HEAD_CELL_ON'
        }), (3, {
            "google_name": 'WIFI_POWER_SCENARIO_ON_BODY_CELL_OFF'
        }), (4, {
            "google_name": 'WIFI_POWER_SCENARIO_ON_BODY_CELL_ON'
        }), (5, {
            "google_name": 'WIFI_POWER_SCENARIO_ON_BODY_BT'
        }), (6, {
            "google_name": 'WIFI_POWER_SCENARIO_ON_HEAD_HOTSPOT'
        }), (7, {
            "google_name": 'WIFI_POWER_SCENARIO_ON_HEAD_HOTSPOT_MMW'
        }), (8, {
            "google_name": 'WIFI_POWER_SCENARIO_ON_BODY_CELL_ON_BT'
        }), (9, {
            "google_name": 'WIFI_POWER_SCENARIO_ON_BODY_HOTSPOT'
        }), (10, {
            "google_name": 'WIFI_POWER_SCENARIO_ON_BODY_HOTSPOT_BT'
        }), (11, {
            "google_name": 'WIFI_POWER_SCENARIO_ON_BODY_HOTSPOT_MMW'
        }), (12, {
            "google_name": 'WIFI_POWER_SCENARIO_ON_BODY_HOTSPOT_BT_MMW'
        })])
        sar_config_path = '/vendor/firmware/sarconfig.info'
        sar_config = self.dut.adb.shell(
            'cat {}'.format(sar_config_path)).splitlines()
        sar_config = [line.split(',') for line in sar_config]
        sar_config = [[int(x) for x in line] for line in sar_config]

        for sar_state in sar_config:
            self.sar_state_mapping[sar_state[0]]['brcm_index'] = (
                self.BRCM_SAR_MAPPING[sar_state[1]], bool(sar_state[2]))
        current_context = context.get_current_context().get_full_output_path()
        file_path = os.path.join(current_context, 'sarconfig')
        with open(file_path, 'w') as file:
            json.dump(wputils.serialize_dict(self.sar_state_mapping),
                      file,
                      indent=4)

    def _parse_nvram_sar_line(self, sar_line):
        """Helper function to decode SAR NVRAM data lines.

        Args:
            sar_line: single line of text from NVRAM file containing SAR data.
        Returns:
            sar_config: sar config referenced in this line
            decoded_values: tx powers configured in this line
        """

        sar_config = collections.OrderedDict()
        list_of_countries = ['fcc', 'jp', 'ca']
        try:
            sar_config['country'] = next(country
                                         for country in list_of_countries
                                         if country in sar_line.split('=')[0])
        except:
            sar_config['country'] = 'row'

        list_of_sar_states = ['grip', 'bt', 'hotspot']
        try:
            sar_config['state'] = next(state for state in list_of_sar_states
                                       if state in sar_line.split('=')[0])
        except:
            sar_config['state'] = 'head'

        list_of_bands = ['2g', '5g', '6g']
        sar_config['band'] = next(band for band in list_of_bands
                                  if band in sar_line.split('=')[0])

        sar_config['rsdb'] = 'rsdb' if 'rsdb' in sar_line else 'mimo'
        sar_config['airplane_mode'] = '_2=' in sar_line

        sar_powers = sar_line.split('=')[1].split(',')
        decoded_powers = []
        for sar_power in sar_powers:
            # Note that core 0 and 1 are flipped in the NVRAM entries
            decoded_powers.append([
                (int(sar_power[4:], 16) & int('7f', 16)) / 4,
                (int(sar_power[2:4], 16) & int('7f', 16)) / 4
            ])

        return tuple(sar_config.values()), decoded_powers

    def get_sar_power_from_nvram(self, testcase_params):
        """Function to get current expected SAR power from nvram

        This functions gets the expected SAR TX power from the DUT NVRAM data.
        The SAR power is looked up based on the current channel and regulatory
        domain,

        Args:
            testcase_params: dict containing channel, sar state, country code
        Returns:
            sar_config: current expected sar config
            sar_powers: current expected sar powers
        """

        if testcase_params['country_code'] == 'US':
            reg_domain = 'fcc'
        elif testcase_params['country_code'] == 'JP':
            reg_domain = 'jp'
        elif testcase_params['country_code'] == 'CA':
            reg_domain = 'ca'
        else:
            reg_domain = 'row'
        for band, channels in self.BAND_TO_CHANNEL_MAP.items():
            if testcase_params['channel'] in channels:
                current_band = band[0]
                sub_band_idx = band[1]
                break
        sar_config = (reg_domain, self.sar_state_mapping[
            testcase_params['sar_state']]['brcm_index'][0], current_band,
                      'mimo', self.sar_state_mapping[
                          testcase_params['sar_state']]['brcm_index'][1])
        sar_powers = self.nvram_sar_data[sar_config][sub_band_idx - 1]
        return sar_config, sar_powers

    def get_sar_power_from_csv(self, testcase_params):
        """Function to get current expected SAR power from CSV.

        This functions gets the expected SAR TX power from the DUT NVRAM data.
        The SAR power is looked up based on the current channel and regulatory
        domain,

        Args:
            testcase_params: dict containing channel, sar state, country code
        Returns:
            sar_config: current expected sar config
            sar_powers: current expected sar powers
        """

        if testcase_params['country_code'] == 'US':
            reg_domain = 'fcc'
        elif testcase_params['country_code'] == 'JP':
            reg_domain = 'jp'
        elif testcase_params['country_code'] == 'CA':
            reg_domain = 'ca'
        else:
            reg_domain = 'row'
        for band, channels in self.BAND_TO_CHANNEL_MAP.items():
            if testcase_params['channel'] in channels:
                current_band = band[0]
                sub_band_idx = band[1]
                break
        sar_config = (reg_domain, 'mimo', current_band)
        sar_powers = [
            self.csv_sar_data[testcase_params['sar_state']]['SAR Powers']
            [sar_config][0][sub_band_idx - 1],
            self.csv_sar_data[testcase_params['sar_state']]['SAR Powers']
            [sar_config][1][sub_band_idx - 1]
        ]
        return sar_config, sar_powers

    def process_wl_curpower(self, wl_curpower_file, testcase_params):
        """Function to parse wl_curpower output.

        Args:
            wl_curpower_file: path to curpower output file.
            testcase_params: dict containing channel, sar state, country code
        Returns:
            wl_curpower_dict: dict formatted version of curpower data.
        """

        with open(wl_curpower_file, 'r') as file:
            wl_curpower_out = file.read()

        channel_regex = re.compile(r'Current Channel:\s+(?P<channel>[0-9]+)')
        bandwidth_regex = re.compile(
            r'Channel Width:\s+(?P<bandwidth>\S+)MHz\n')

        channel = int(
            re.search(channel_regex, wl_curpower_out).group('channel'))
        bandwidth = int(
            re.search(bandwidth_regex, wl_curpower_out).group('bandwidth'))

        regulatory_limits = self.generate_regulatory_table(
            wl_curpower_out, channel, bandwidth)
        board_limits = self.generate_board_limit_table(wl_curpower_out,
                                                       channel, bandwidth)
        wl_curpower_dict = {
            'channel': channel,
            'bandwidth': bandwidth,
            'country': testcase_params['country_code'],
            'regulatory_limits': regulatory_limits,
            'board_limits': board_limits
        }
        return wl_curpower_dict

    def generate_regulatory_table(self, wl_curpower_out, channel, bw):
        """"Helper function to generate regulatory limit table from curpower.

        Args:
            wl_curpower_out: curpower output
            channel: current channel
            bw: current bandwidth
        Returns:
            regulatory_table: dict with regulatory limits for current config
        """

        regulatory_group_map = {
            'DSSS':
            [('CCK', rate, 1)
             for rate in ['{}Mbps'.format(mbps) for mbps in [1, 2, 5.5, 11]]],
            'OFDM_CDD1': [('LEGACY', rate, 1) for rate in [
                '{}Mbps'.format(mbps)
                for mbps in [6, 9, 12, 18, 24, 36, 48, 54]
            ]],
            'MCS0_7_CDD1':
            [(mode, rate, 1)
             for (mode,
                  rate) in itertools.product(['HT' + str(bw), 'VHT' +
                                              str(bw)], range(0, 8))],
            'VHT8_9SS1_CDD1': [('VHT' + str(bw), 8, 1),
                               ('VHT' + str(bw), 9, 1)],
            'VHT10_11SS1_CDD1': [('VHT' + str(bw), 10, 1),
                                 ('VHT' + str(bw), 11, 1)],
            'MCS8_15':
            [(mode, rate - 8 * ('VHT' in mode), 2)
             for (mode,
                  rate) in itertools.product(['HT' + str(bw), 'VHT' +
                                              str(bw)], range(8, 16))],
            'VHT8_9SS2': [('VHT' + str(bw), 8, 2), ('VHT' + str(bw), 9, 2)],
            'VHT10_11SS2': [('VHT' + str(bw), 10, 2),
                            ('VHT' + str(bw), 11, 2)],
            'HE_MCS0-11_CDD1': [('HE' + str(bw), rate, 1)
                                for rate in range(0, 12)],
            'HE_MCS0_11SS2': [('HE' + str(bw), rate, 2)
                              for rate in range(0, 12)],
        }
        tx_power_regex = re.compile(
            '(?P<mcs>\S+)\s+(?P<chain>[2])\s+(?P<power_1>[0-9.-]+)\s*(?P<power_2>[0-9.-]*)\s*(?P<power_3>[0-9.-]*)\s*(?P<power_4>[0-9.-]*)'
        )

        regulatory_section_regex = re.compile(
            r'Regulatory Limits:(?P<regulatory_limits>[\S\s]+)Board Limits:')
        regulatory_list = re.search(regulatory_section_regex,
                                    wl_curpower_out).group('regulatory_limits')
        regulatory_list = re.findall(tx_power_regex, regulatory_list)
        regulatory_dict = {entry[0]: entry[2:] for entry in regulatory_list}

        bw_index = int(math.log(bw / 10, 2)) - 1
        regulatory_table = collections.OrderedDict()
        for regulatory_group, rates in regulatory_group_map.items():
            for rate in rates:
                reg_power = regulatory_dict.get(regulatory_group,
                                                ['0', '0', '0', '0'])[bw_index]
                regulatory_table[rate] = float(
                    reg_power) if reg_power != '-' else 0
        return regulatory_table

    def generate_board_limit_table(self, wl_curpower_out, channel, bw):
        """"Helper function to generate board limit table from curpower.

        Args:
            wl_curpower_out: curpower output
            channel: current channel
            bw: current bandwidth
        Returns:
            board_limit_table: dict with board limits for current config
        """

        tx_power_regex = re.compile(
            '(?P<mcs>\S+)\s+(?P<chain>[2])\s+(?P<power_1>[0-9.-]+)\s*(?P<power_2>[0-9.-]*)\s*(?P<power_3>[0-9.-]*)\s*(?P<power_4>[0-9.-]*)'
        )

        board_section_regex = re.compile(
            r'Board Limits:(?P<board_limits>[\S\s]+)Power Targets:')
        board_limits_list = re.search(board_section_regex,
                                      wl_curpower_out).group('board_limits')
        board_limits_list = re.findall(tx_power_regex, board_limits_list)
        board_limits_dict = {
            entry[0]: entry[2:]
            for entry in board_limits_list
        }

        mcs_regex_list = [[
            re.compile('DSSS'),
            [('CCK', rate, 1)
             for rate in ['{}Mbps'.format(mbps) for mbps in [1, 2, 5.5, 11]]]
        ], [re.compile('OFDM(?P<mcs>[0-9]+)_CDD1'), [('LEGACY', '{}Mbps', 1)]],
                          [
                              re.compile('MCS(?P<mcs>[0-7])_CDD1'),
                              [('HT{}'.format(bw), '{}', 1),
                               ('VHT{}'.format(bw), '{}', 1)]
                          ],
                          [
                              re.compile('VHT(?P<mcs>[8-9])SS1_CDD1'),
                              [('VHT{}'.format(bw), '{}', 1)]
                          ],
                          [
                              re.compile('VHT10_11SS1_CDD1'),
                              [('VHT{}'.format(bw), '10', 1),
                               ('VHT{}'.format(bw), '11', 1)]
                          ],
                          [
                              re.compile('MCS(?P<mcs>[0-9]{2})'),
                              [('HT{}'.format(bw), '{}', 2)]
                          ],
                          [
                              re.compile('VHT(?P<mcs>[0-9])SS2'),
                              [('VHT{}'.format(bw), '{}', 2)]
                          ],
                          [
                              re.compile('VHT10_11SS2'),
                              [('VHT{}'.format(bw), '10', 2),
                               ('VHT{}'.format(bw), '11', 2)]
                          ],
                          [
                              re.compile('HE_MCS(?P<mcs>[0-9]+)_CDD1'),
                              [('HE{}'.format(bw), '{}', 1)]
                          ],
                          [
                              re.compile('HE_MCS(?P<mcs>[0-9]+)SS2'),
                              [('HE{}'.format(bw), '{}', 2)]
                          ]]

        bw_index = int(math.log(bw / 10, 2)) - 1
        board_limit_table = collections.OrderedDict()
        for mcs, board_limit in board_limits_dict.items():
            for mcs_regex_tuple in mcs_regex_list:
                mcs_match = re.match(mcs_regex_tuple[0], mcs)
                if mcs_match:
                    for possible_mcs in mcs_regex_tuple[1]:
                        try:
                            curr_mcs = (possible_mcs[0],
                                        possible_mcs[1].format(
                                            mcs_match.group('mcs')),
                                        possible_mcs[2])
                        except:
                            curr_mcs = (possible_mcs[0], possible_mcs[1],
                                        possible_mcs[2])
                        board_limit_table[curr_mcs] = float(
                            board_limit[bw_index]
                        ) if board_limit[bw_index] != '-' else 0
                    break
        return board_limit_table

    def pass_fail_check(self, result):
        """Function to evaluate if current TX powqe matches CSV/NVRAM settings.

        This function assesses whether the current TX power reported by the
        DUT matches the powers programmed in NVRAM and CSV after applying the
        correct TX power backoff used to account for CLPC errors.
        """

        if isinstance(result['testcase_params']['channel'],
                      str) and '6g' in result['testcase_params']['channel']:
            mode = 'HE' + str(result['testcase_params']['bandwidth'])
        else:
            mode = 'HE' + str(result['testcase_params']['bandwidth'])
        regulatory_power = result['wl_curpower']['regulatory_limits'][(mode, 0,
                                                                       2)]
        board_power = result['wl_curpower']['board_limits'][(mode, str(0), 2)]
        # try:
        sar_config, nvram_powers = self.get_sar_power_from_nvram(
            result['testcase_params'])
        # except:
        #     nvram_powers = [99, 99]
        #     sar_config = 'SAR DISABLED'
        try:
            csv_config, csv_powers = self.get_sar_power_from_csv(
                result['testcase_params'])
        except:
            #get from wl_curpower
            csv_powers = [99, 99]
        self.log.info("SAR state: {} ({})".format(
            result['testcase_params']['sar_state'],
            self.sar_state_mapping[result['testcase_params']['sar_state']],
        ))
        self.log.info("Country Code: {}".format(
            result['testcase_params']['country_code']))
        self.log.info('BRCM SAR Table: {}'.format(sar_config))
        expected_power = [
            min([csv_powers[0], regulatory_power, board_power]) - 1.5,
            min([csv_powers[1], regulatory_power, board_power]) - 1.5
        ]
        power_str = "NVRAM Powers: {}, CSV Powers: {}, Reg Powers: {}, Board Power: {}, Expected Powers: {}, Reported Powers: {}".format(
            nvram_powers, csv_powers, [regulatory_power] * 2,
            [board_power] * 2, expected_power, result['tx_powers'])
        max_error = max([
            abs(expected_power[idx] - result['tx_powers'][idx])
            for idx in [0, 1]
        ])
        if max_error > 1:
            asserts.fail(power_str)
        else:
            asserts.explicit_pass(power_str)

    def process_testclass_results(self):
        pass

    def run_tx_power_test(self, testcase_params):
        """Main function to test tx power.

        The function sets up the AP & DUT in the correct channel and mode
        configuration, starts ping traffic and queries the current TX power.

        Args:
            testcase_params: dict containing all test parameters
        Returns:
            test_result: dict containing ping results and other meta data
        """
        # Prepare results dict
        llstats_obj = wputils.LinkLayerStats(
            self.dut, self.testclass_params.get('llstats_enabled', True))
        test_result = collections.OrderedDict()
        test_result['testcase_params'] = testcase_params.copy()
        test_result['test_name'] = self.current_test_name
        test_result['ap_config'] = self.access_point.ap_settings.copy()
        test_result['attenuation'] = testcase_params['atten_range']
        test_result['fixed_attenuation'] = self.testbed_params[
            'fixed_attenuation'][str(testcase_params['channel'])]
        test_result['rssi_results'] = []
        test_result['ping_results'] = []
        test_result['llstats'] = []
        # Setup sniffer
        if self.testbed_params['sniffer_enable']:
            self.sniffer.start_capture(
                testcase_params['test_network'],
                chan=testcase_params['channel'],
                bw=testcase_params['bandwidth'],
                duration=testcase_params['ping_duration'] *
                len(testcase_params['atten_range']) + self.TEST_TIMEOUT)
        # Set sar state
        if testcase_params['sar_state'] == -1:
            self.dut.adb.shell('halutil -sar disable')
        else:
            self.dut.adb.shell('halutil -sar enable {}'.format(
                testcase_params['sar_state']))
        # Run ping and sweep attenuation as needed
        self.log.info('Starting ping.')
        thread_future = wputils.get_ping_stats_nb(self.ping_server,
                                                  self.dut_ip, 10, 0.02, 64)

        for atten in testcase_params['atten_range']:
            for attenuator in self.attenuators:
                attenuator.set_atten(atten, strict=False, retry=True)
            # Set mcs
            if isinstance(testcase_params['channel'],
                          int) and testcase_params['channel'] < 13:
                self.dut.adb.shell('wl 2g_rate -e 0 -s 2 -b {}'.format(
                    testcase_params['bandwidth']))
            elif isinstance(testcase_params['channel'],
                            int) and testcase_params['channel'] > 13:
                self.dut.adb.shell('wl 5g_rate -e 0 -s 2 -b {}'.format(
                    testcase_params['bandwidth']))
            else:
                self.dut.adb.shell('wl 6g_rate -e 0 -s 2 -b {}'.format(
                    testcase_params['bandwidth']))
            # Refresh link layer stats
            llstats_obj.update_stats()
            # Check sar state
            self.log.info('Current Country: {}'.format(
                self.dut.adb.shell('wl country')))
            # Dump last est power multiple times
            chain_0_power = []
            chain_1_power = []
            for idx in range(30):
                last_est_out = self.dut.adb.shell(
                    "wl curpower | grep 'Last est. power'", ignore_status=True)
                if "Last est. power" in last_est_out:
                    try:
                        per_chain_powers = last_est_out.split(
                            ':')[1].strip().split('  ')
                        per_chain_powers = [
                            float(power) for power in per_chain_powers
                        ]
                    except:
                        per_chain_powers = [0, 0]
                        self.log.warning(
                            'Could not parse output: {}'.format(last_est_out))
                    self.log.info(
                        'Current Tx Powers = {}'.format(per_chain_powers))
                    if per_chain_powers[0] > 0:
                        chain_0_power.append(per_chain_powers[0])
                    if per_chain_powers[1] > 0:
                        chain_1_power.append(per_chain_powers[1])
                time.sleep(0.25)
            # Check if empty
            if len(chain_0_power) == 0 or len(chain_1_power) == 0:
                test_result['tx_powers'] = [0, 0]
                tx_power_frequency = [100, 100]
            else:
                test_result['tx_powers'] = [
                    scipy.stats.mode(chain_0_power).mode[0],
                    scipy.stats.mode(chain_1_power).mode[0]
                ]
                tx_power_frequency = [
                    100 * scipy.stats.mode(chain_0_power).count[0] /
                    len(chain_0_power),
                    100 * scipy.stats.mode(chain_1_power).count[0] /
                    len(chain_0_power)
                ]
            self.log.info(
                'Filtered Tx Powers = {}. Frequency = [{:.0f}%, {:.0f}%]'.
                format(test_result['tx_powers'], tx_power_frequency[0],
                       tx_power_frequency[1]))
            llstats_obj.update_stats()
            curr_llstats = llstats_obj.llstats_incremental.copy()
            test_result['llstats'].append(curr_llstats)
            # DUMP wl curpower one
            try:
                wl_curpower = self.dut.adb.shell('wl curpower')
            except:
                time.sleep(0.25)
                wl_curpower = self.dut.adb.shell('wl curpower',
                                                 ignore_status=True)
            current_context = context.get_current_context(
            ).get_full_output_path()
            wl_curpower_path = os.path.join(current_context,
                                            'wl_curpower_output')
            with open(wl_curpower_path, 'w') as file:
                file.write(wl_curpower)
            wl_curpower_dict = self.process_wl_curpower(
                wl_curpower_path, testcase_params)
            wl_curpower_path = os.path.join(current_context,
                                            'wl_curpower_dict')
            with open(wl_curpower_path, 'w') as file:
                json.dump(wputils.serialize_dict(wl_curpower_dict),
                          file,
                          indent=4)
            test_result['wl_curpower'] = wl_curpower_dict
        thread_future.result()
        if self.testbed_params['sniffer_enable']:
            self.sniffer.stop_capture()
        return test_result

    def setup_ap(self, testcase_params):
        """Sets up the access point in the configuration required by the test.

        Args:
            testcase_params: dict containing AP and other test params
        """
        band = self.access_point.band_lookup_by_channel(
            testcase_params['channel'])
        if '6G' in band:
            frequency = wutils.WifiEnums.channel_6G_to_freq[int(
                testcase_params['channel'].strip('6g'))]
        else:
            if testcase_params['channel'] < 13:
                frequency = wutils.WifiEnums.channel_2G_to_freq[
                    testcase_params['channel']]
            else:
                frequency = wutils.WifiEnums.channel_5G_to_freq[
                    testcase_params['channel']]
        if frequency in wutils.WifiEnums.DFS_5G_FREQUENCIES:
            self.access_point.set_region(self.testbed_params['DFS_region'])
        else:
            self.access_point.set_region(self.testbed_params['default_region'])
        self.access_point.set_channel_and_bandwidth(band,
                                                    testcase_params['channel'],
                                                    testcase_params['mode'])
        #self.access_point.set_channel(band, testcase_params['channel'])
        #self.access_point.set_bandwidth(band, testcase_params['mode'])
        if 'low' in testcase_params['ap_power']:
            self.log.info('Setting low AP power.')
            self.access_point.set_power(
                band, self.testclass_params['low_ap_tx_power'])
        self.log.info('Access Point Configuration: {}'.format(
            self.access_point.ap_settings))

    def setup_dut(self, testcase_params):
        """Sets up the DUT in the configuration required by the test.

        Args:
            testcase_params: dict containing AP and other test params
        """
        # Turn screen off to preserve battery
        if self.testbed_params.get('screen_on',
                                   False) or self.testclass_params.get(
                                       'screen_on', False):
            self.dut.droid.wakeLockAcquireDim()
        else:
            self.dut.go_to_sleep()
        if wputils.validate_network(self.dut,
                                    testcase_params['test_network']['SSID']):
            current_country = self.dut.adb.shell('wl country')
            self.log.info('Current country code: {}'.format(current_country))
            if testcase_params['country_code'] in current_country:
                self.log.info('Already connected to desired network')
                self.dut_ip = self.dut.droid.connectivityGetIPv4Addresses(
                    'wlan0')[0]
                return
        testcase_params['test_network']['channel'] = testcase_params['channel']
        wutils.wifi_toggle_state(self.dut, False)
        wutils.set_wifi_country_code(self.dut, testcase_params['country_code'])
        wutils.wifi_toggle_state(self.dut, True)
        wutils.reset_wifi(self.dut)
        if self.testbed_params.get('txbf_off', False):
            wputils.disable_beamforming(self.dut)
        wutils.set_wifi_country_code(self.dut, testcase_params['country_code'])
        current_country = self.dut.adb.shell('wl country')
        self.log.info('Current country code: {}'.format(current_country))
        if testcase_params['country_code'] not in current_country:
            asserts.fail('Country code not correct.')
        chan_list = self.dut.adb.shell('wl chan_info_list')
        if str(testcase_params['channel']) not in chan_list:
            asserts.skip('Channel {} not supported in {}'.format(
                testcase_params['channel'], testcase_params['country_code']))
        wutils.wifi_connect(self.dut,
                            testcase_params['test_network'],
                            num_of_tries=5,
                            check_connectivity=True)
        self.dut_ip = self.dut.droid.connectivityGetIPv4Addresses('wlan0')[0]

    def setup_tx_power_test(self, testcase_params):
        """Function that gets devices ready for the test.

        Args:
            testcase_params: dict containing test-specific parameters
        """
        # Configure AP
        self.setup_ap(testcase_params)
        # Set attenuator to 0 dB
        for attenuator in self.attenuators:
            attenuator.set_atten(0, strict=False, retry=True)
        # Reset, configure, and connect DUT
        self.setup_dut(testcase_params)

    def check_skip_conditions(self, testcase_params):
        """Checks if test should be skipped."""
        # Check battery level before test
        if not wputils.health_check(self.dut, 10):
            asserts.skip('DUT battery level too low.')
        if testcase_params[
                'channel'] in wputils.CHANNELS_6GHz and not self.dut.droid.is6GhzBandSupported(
                ):
            asserts.skip('DUT does not support 6 GHz band.')
        if not self.access_point.band_lookup_by_channel(
                testcase_params['channel']):
            asserts.skip('AP does not support requested channel.')

    def compile_test_params(self, testcase_params):
        """Function to compile all testcase parameters."""

        self.check_skip_conditions(testcase_params)

        band = self.access_point.band_lookup_by_channel(
            testcase_params['channel'])
        testcase_params['test_network'] = self.main_network[band]
        testcase_params['attenuated_chain'] = -1
        testcase_params.update(
            ping_interval=self.testclass_params['ping_interval'],
            ping_duration=self.testclass_params['ping_duration'],
            ping_size=self.testclass_params['ping_size'],
        )

        testcase_params['atten_range'] = [0]
        return testcase_params

    def _test_ping(self, testcase_params):
        """ Function that gets called for each range test case

        The function gets called in each range test case. It customizes the
        range test based on the test name of the test that called it

        Args:
            testcase_params: dict containing preliminary set of parameters
        """
        # Compile test parameters from config and test name
        testcase_params = self.compile_test_params(testcase_params)
        # Run ping test
        self.setup_tx_power_test(testcase_params)
        result = self.run_tx_power_test(testcase_params)
        self.pass_fail_check(result)

    def generate_test_cases(self, ap_power, channels, modes, test_types,
                            country_codes, sar_states):
        """Function that auto-generates test cases for a test class."""
        test_cases = []
        allowed_configs = {
            20: [
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 36, 40, 44, 48, 52, 64, 100,
                116, 132, 140, 149, 153, 157, 161
            ],
            40: [36, 44, 100, 149, 157],
            80: [36, 100, 149],
            160: [36, '6g37', '6g117', '6g213']
        }

        for channel, mode, test_type, country_code, sar_state in itertools.product(
                channels, modes, test_types, country_codes, sar_states):
            bandwidth = int(''.join([x for x in mode if x.isdigit()]))
            if channel not in allowed_configs[bandwidth]:
                continue
            testcase_name = '{}_ch{}_{}_{}_sar_{}'.format(
                test_type, channel, mode, country_code, sar_state)
            testcase_params = collections.OrderedDict(
                test_type=test_type,
                ap_power=ap_power,
                channel=channel,
                mode=mode,
                bandwidth=bandwidth,
                country_code=country_code,
                sar_state=sar_state)
            setattr(self, testcase_name,
                    partial(self._test_ping, testcase_params))
            test_cases.append(testcase_name)
        return test_cases


class WifiTxPowerCheck_BasicSAR_Test(WifiTxPowerCheckTest):

    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())
        self.testclass_metric_logger = (
            BlackboxMappedMetricLogger.for_test_class())
        self.publish_testcase_metrics = True
        self.tests = self.generate_test_cases(
            ap_power='standard',
            channels=[6, 36, 52, 100, 149, '6g37'],
            modes=['bw20', 'bw160'],
            test_types=[
                'test_tx_power',
            ],
            country_codes=['US', 'GB', 'JP', 'CA'],
            sar_states=[-1, 0, 1, 2, 3, 4])
