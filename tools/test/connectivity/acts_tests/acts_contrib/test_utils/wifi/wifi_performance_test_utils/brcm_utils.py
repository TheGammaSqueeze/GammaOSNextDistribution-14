#!/usr/bin/env python3.4
#
#   Copyright 2021 - The Android Open Source Project
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
import hashlib
import itertools
import logging
import math
import numpy
import re
import statistics
import time

VERY_SHORT_SLEEP = 0.5
SHORT_SLEEP = 1
MED_SLEEP = 6
DISCONNECTION_MESSAGE_BRCM = 'driver adapter not found'
RSSI_ERROR_VAL = float('nan')
RATE_TABLE = {
    'HT': {
        1: {
            20: [7.2, 14.4, 21.7, 28.9, 43.4, 57.8, 65.0, 72.2],
            40: [15.0, 30.0, 45.0, 60.0, 90.0, 120.0, 135.0, 150.0]
        },
        2: {
            20: [
                0, 0, 0, 0, 0, 0, 0, 0, 14.4, 28.8, 43.4, 57.8, 86.8, 115.6,
                130, 144.4
            ],
            40: [0, 0, 0, 0, 0, 0, 0, 0, 30, 60, 90, 120, 180, 240, 270, 300]
        }
    },
    'VHT': {
        1: {
            20: [
                7.2, 14.4, 21.7, 28.9, 43.4, 57.8, 65.0, 72.2, 86.7, 96.2,
                129.0, 143.4
            ],
            40: [
                15.0, 30.0, 45.0, 60.0, 90.0, 120.0, 135.0, 150.0, 180.0,
                200.0, 258, 286.8
            ],
            80: [
                32.5, 65.0, 97.5, 130.0, 195.0, 260.0, 292.5, 325.0, 390.0,
                433.3, 540.4, 600.4
            ],
            160: [
                65.0, 130.0, 195.0, 260.0, 390.0, 520.0, 585.0, 650.0, 780.0,
                1080.8, 1200.8
            ]
        },
        2: {
            20: [
                14.4, 28.8, 43.4, 57.8, 86.8, 115.6, 130, 144.4, 173.4, 192.4,
                258, 286.8
            ],
            40: [30, 60, 90, 120, 180, 240, 270, 300, 360, 400, 516, 573.6],
            80: [
                65, 130, 195, 260, 390, 520, 585, 650, 780, 866.6, 1080.8,
                1200.8
            ],
            160:
            [130, 260, 390, 520, 780, 1040, 1170, 1300, 1560, 2161.6, 2401.6]
        },
    },
    'HE': {
        1: {
            20: [
                8.6, 17.2, 25.8, 34.4, 51.6, 68.8, 77.4, 86.0, 103.2, 114.7,
                129.0, 143.4
            ],
            40: [
                17.2, 34.4, 51.6, 68.8, 103.2, 137.6, 154.8, 172, 206.4, 229.4,
                258, 286.8
            ],
            80: [
                36.0, 72.1, 108.1, 144.1, 216.2, 288.2, 324.3, 360.3, 432.4,
                480.4, 540.4, 600.4
            ],
            160: [
                72, 144.2, 216.2, 288.2, 432.4, 576.4, 648.6, 720.6, 864.8,
                960.8, 1080.8, 1200.8
            ]
        },
        2: {
            20: [
                17.2, 34.4, 51.6, 68.8, 103.2, 137.6, 154.8, 172, 206.4, 229.4,
                258, 286.8
            ],
            40: [
                34.4, 68.8, 103.2, 137.6, 206.4, 275.2, 309.6, 344, 412.8,
                458.8, 516, 573.6
            ],
            80: [
                72, 144.2, 216.2, 288.2, 432.4, 576.4, 648.6, 720.6, 864.8,
                960.8, 1080.8, 1200.8
            ],
            160: [
                144, 288.4, 432.4, 576.4, 864.8, 1152.8, 1297.2, 1441.2,
                1729.6, 1921.6, 2161.6, 2401.6
            ]
        },
    },
    'EHT': {
        1: {
            20: [
                8.6, 17.2, 25.8, 34.4, 51.6, 68.8, 77.4, 86.0, 103.2, 114.7,
                129.0, 143.4, 154.9, 172.1, 0, 0
            ],
            40: [
                17.2, 34.4, 51.6, 68.8, 103.2, 137.6, 154.9, 172.1, 206.5, 229.4,
                258.1, 286.8, 309.7, 344.1, 0, 0
            ],
            80: [
                36.0, 72.1, 108.1, 144.1, 216.2, 288.2, 324.3, 360.3, 432.4,
                480.4, 540.4, 600.4, 648.5, 720.6, 0, 0
            ],
            160: [
                72.1, 144.1, 216.2, 288.2, 432.4, 576.5, 648.5, 720.6, 864.7,
                960.7, 1080.9, 1201, 1297.1, 1441.2, 0, 0
            ]
        },
        2: {
            20: [
                17.2, 34.4, 51.6, 68.8, 103.2, 137.6, 154.8, 172, 206.4, 229.4,
                258, 286.8, 309.8, 344.2, 0, 0
            ],
            40: [
                34.4, 68.8, 103.2, 137.6, 206.4, 275.2, 309.6, 344, 412.8,
                458.8, 516, 573.6, 619.4, 688.2, 0, 0
            ],
            80: [
                72, 144.2, 216.2, 288.2, 432.4, 576.4, 648.6, 720.6, 864.8,
                960.8, 1080.8, 1200.8, 1297, 1441.2, 0, 0
            ],
            160: [
                144, 288.4, 432.4, 576.4, 864.8, 1152.8, 1297.2, 1441.2,
                1729.6, 1921.6, 2161.6, 2401.6, 2594.2, 2882.4, 0, 0
            ]
        },
    },
}


# Rssi Utilities
def empty_rssi_result():
    return collections.OrderedDict([('data', []), ('mean', None),
                                    ('stdev', None)])


def get_connected_rssi(dut,
                       num_measurements=1,
                       polling_frequency=SHORT_SLEEP,
                       first_measurement_delay=0,
                       disconnect_warning=True,
                       ignore_samples=0,
                       interface='wlan0'):
    # yapf: disable
    connected_rssi = collections.OrderedDict(
        [('time_stamp', []),
         ('bssid', []), ('ssid', []), ('frequency', []),
         ('signal_poll_rssi', empty_rssi_result()),
         ('signal_poll_avg_rssi', empty_rssi_result()),
         ('chain_0_rssi', empty_rssi_result()),
         ('chain_1_rssi', empty_rssi_result())])

    # yapf: enable
    previous_bssid = 'disconnected'
    t0 = time.time()
    time.sleep(first_measurement_delay)
    for idx in range(num_measurements):
        measurement_start_time = time.time()
        connected_rssi['time_stamp'].append(measurement_start_time - t0)
        # Get signal poll RSSI
        try:
            status_output = dut.adb.shell(
                'wpa_cli -i {} status'.format(interface))
        except:
            status_output = ''
        match = re.search('bssid=.*', status_output)
        if match:
            current_bssid = match.group(0).split('=')[1]
            connected_rssi['bssid'].append(current_bssid)
        else:
            current_bssid = 'disconnected'
            connected_rssi['bssid'].append(current_bssid)
            if disconnect_warning and previous_bssid != 'disconnected':
                logging.warning('WIFI DISCONNECT DETECTED!')

        previous_bssid = current_bssid
        match = re.search('\s+ssid=.*', status_output)
        if match:
            ssid = match.group(0).split('=')[1]
            connected_rssi['ssid'].append(ssid)
        else:
            connected_rssi['ssid'].append('disconnected')

        #TODO: SEARCH MAP ; PICK CENTER CHANNEL
        match = re.search('\s+freq=.*', status_output)
        if match:
            frequency = int(match.group(0).split('=')[1])
            connected_rssi['frequency'].append(frequency)
        else:
            connected_rssi['frequency'].append(RSSI_ERROR_VAL)

        if interface == 'wlan0':
            try:
                per_chain_rssi = dut.adb.shell('wl phy_rssi_ant')
                chain_0_rssi = re.search(
                    r'rssi\[0\]\s(?P<chain_0_rssi>[0-9\-]*)', per_chain_rssi)
                if chain_0_rssi:
                    chain_0_rssi = int(chain_0_rssi.group('chain_0_rssi'))
                else:
                    chain_0_rssi = -float('inf')
                chain_1_rssi = re.search(
                    r'rssi\[1\]\s(?P<chain_1_rssi>[0-9\-]*)', per_chain_rssi)
                if chain_1_rssi:
                    chain_1_rssi = int(chain_1_rssi.group('chain_1_rssi'))
                else:
                    chain_1_rssi = -float('inf')
            except:
                chain_0_rssi = RSSI_ERROR_VAL
                chain_1_rssi = RSSI_ERROR_VAL
            connected_rssi['chain_0_rssi']['data'].append(chain_0_rssi)
            connected_rssi['chain_1_rssi']['data'].append(chain_1_rssi)
            combined_rssi = math.pow(10, chain_0_rssi / 10) + math.pow(
                10, chain_1_rssi / 10)
            combined_rssi = 10 * math.log10(combined_rssi)
            connected_rssi['signal_poll_rssi']['data'].append(combined_rssi)
            connected_rssi['signal_poll_avg_rssi']['data'].append(
                combined_rssi)
        else:
            try:
                signal_poll_output = dut.adb.shell(
                    'wpa_cli -i {} signal_poll'.format(interface))
            except:
                signal_poll_output = ''
            match = re.search('RSSI=.*', signal_poll_output)
            if match:
                temp_rssi = int(match.group(0).split('=')[1])
                if temp_rssi == -9999 or temp_rssi == 0:
                    connected_rssi['signal_poll_rssi']['data'].append(
                        RSSI_ERROR_VAL)
                else:
                    connected_rssi['signal_poll_rssi']['data'].append(
                        temp_rssi)
            else:
                connected_rssi['signal_poll_rssi']['data'].append(
                    RSSI_ERROR_VAL)
            connected_rssi['chain_0_rssi']['data'].append(RSSI_ERROR_VAL)
            connected_rssi['chain_1_rssi']['data'].append(RSSI_ERROR_VAL)
        measurement_elapsed_time = time.time() - measurement_start_time
        time.sleep(max(0, polling_frequency - measurement_elapsed_time))

    # Statistics, Statistics
    for key, val in connected_rssi.copy().items():
        if 'data' not in val:
            continue
        filtered_rssi_values = [x for x in val['data'] if not (math.isnan(x) or math.isinf(x))]
        if len(filtered_rssi_values) > ignore_samples:
            filtered_rssi_values = filtered_rssi_values[ignore_samples:]
        if filtered_rssi_values:
            connected_rssi[key]['mean'] = statistics.mean(filtered_rssi_values)
            if len(filtered_rssi_values) > 1:
                connected_rssi[key]['stdev'] = statistics.stdev(
                    filtered_rssi_values)
            else:
                connected_rssi[key]['stdev'] = 0
        else:
            connected_rssi[key]['mean'] = RSSI_ERROR_VAL
            connected_rssi[key]['stdev'] = RSSI_ERROR_VAL

    return connected_rssi


def get_scan_rssi(dut, tracked_bssids, num_measurements=1):
    scan_rssi = collections.OrderedDict()
    for bssid in tracked_bssids:
        scan_rssi[bssid] = empty_rssi_result()
    for idx in range(num_measurements):
        scan_output = dut.adb.shell('cmd wifi start-scan')
        time.sleep(MED_SLEEP)
        scan_output = dut.adb.shell('cmd wifi list-scan-results')
        for bssid in tracked_bssids:
            bssid_result = re.search(bssid + '.*',
                                     scan_output,
                                     flags=re.IGNORECASE)
            if bssid_result:
                bssid_result = bssid_result.group(0).split()
                scan_rssi[bssid]['data'].append(int(bssid_result[2]))
            else:
                scan_rssi[bssid]['data'].append(RSSI_ERROR_VAL)
    # Compute mean RSSIs. Only average valid readings.
    # Output RSSI_ERROR_VAL if no readings found.
    for key, val in scan_rssi.items():
        filtered_rssi_values = [x for x in val['data'] if not math.isnan(x)]
        if filtered_rssi_values:
            scan_rssi[key]['mean'] = statistics.mean(filtered_rssi_values)
            if len(filtered_rssi_values) > 1:
                scan_rssi[key]['stdev'] = statistics.stdev(
                    filtered_rssi_values)
            else:
                scan_rssi[key]['stdev'] = 0
        else:
            scan_rssi[key]['mean'] = RSSI_ERROR_VAL
            scan_rssi[key]['stdev'] = RSSI_ERROR_VAL
    return scan_rssi


def get_sw_signature(dut):
    bdf_output = dut.adb.shell('cksum /vendor/firmware/bcmdhd*')
    logging.debug('BDF Checksum output: {}'.format(bdf_output))
    bdf_signature = sum(
        [int(line.split(' ')[0]) for line in bdf_output.splitlines()]) % 1000

    fw_version = dut.adb.shell('getprop vendor.wlan.firmware.version')
    driver_version = dut.adb.shell('getprop vendor.wlan.driver.version')
    logging.debug('Firmware version : {}. Driver version: {}'.format(
        fw_version, driver_version))
    fw_signature = '{}+{}'.format(fw_version, driver_version)
    fw_signature = int(hashlib.md5(fw_signature.encode()).hexdigest(),
                       16) % 1000
    serial_hash = int(hashlib.md5(dut.serial.encode()).hexdigest(), 16) % 1000
    return {
        'config_signature': bdf_signature,
        'fw_signature': fw_signature,
        'serial_hash': serial_hash
    }


def get_country_code(dut):
    try:
        country_code = dut.adb.shell('wl country').split(' ')[0]
    except:
        country_code = 'XZ'
    if country_code == 'XZ':
        country_code = 'WW'
    logging.debug('Country code: {}'.format(country_code))
    return country_code


def push_config(dut, config_file):
    config_files_list = dut.adb.shell('ls /vendor/etc/*.cal').splitlines()
    for dst_file in config_files_list:
        dut.push_system_file(config_file, dst_file)
    dut.reboot()


def start_wifi_logging(dut):
    pass


def stop_wifi_logging(dut):
    pass


def push_firmware(dut, firmware_files):
    """Function to push Wifi firmware files

    Args:
        dut: dut to push bdf file to
        firmware_files: path to wlanmdsp.mbn file
        datamsc_file: path to Data.msc file
    """
    for file in firmware_files:
        dut.push_system_file(file, '/vendor/firmware/')
    dut.reboot()


def disable_beamforming(dut):
    dut.adb.shell('wl down')
    time.sleep(VERY_SHORT_SLEEP)
    try:
        dut.adb.shell('wl txbf 0')
        dut.adb.shell('wl txbf_bfe_cap 0')
    except:
        logging.warning('Could not disable beamforming.')
    dut.adb.shell('wl up')


def set_nss_capability(dut, nss):
    dut.adb.shell('wl he omi -r {} -t {}'.format(nss, nss))


def set_chain_mask(dut, chain):
    if chain == '2x2':
        chain = 3
    else:
        chain = chain + 1
    # Get current chain mask
    try:
        curr_tx_chain = int(dut.adb.shell('wl txchain'))
        curr_rx_chain = int(dut.adb.shell('wl rxchain'))
    except:
        curr_tx_chain = -1
        curr_rx_chain = -1
    if curr_tx_chain == chain and curr_rx_chain == chain:
        return
    # Set chain mask if needed
    dut.adb.shell('wl down')
    time.sleep(SHORT_SLEEP)
    dut.adb.shell('wl txchain 0x{}'.format(chain))
    dut.adb.shell('wl rxchain 0x{}'.format(chain))
    dut.adb.shell('wl up')

    try:
        curr_tx_chain = int(dut.adb.shell('wl txchain'))
        curr_rx_chain = int(dut.adb.shell('wl rxchain'))
    except:
        curr_tx_chain = -1
        curr_rx_chain = -1
    if curr_tx_chain != chain or curr_rx_chain != chain:
        logging.error('Set chain mask failed.')


class LinkLayerStats():

    LLSTATS_CMD = 'wl dump ampdu; wl counters;'
    LL_STATS_CLEAR_CMD = 'wl dump_clear ampdu; wl reset_cnts;'
    BRCM_PHY_LOG_CLEAR_CMD = 'wl dump phycal; wl dump_clear txbf;'
    BRCM_PHY_LOG_CMD = 'wl phy_rssi_ant; wl phy_snr_ant; wl nrate; wl dump phycal; wl tvpm; wl dump txbf;'
    BW_REGEX = re.compile(r'Chanspec:.+ (?P<bandwidth>[0-9]+)MHz')
    MCS_REGEX = re.compile(r'(?P<count>[0-9]+)\((?P<percent>[0-9]+)%\)')
    RX_REGEX = re.compile(
        r'RX (?P<mode>MCS|VHT|HE|EHT)\s+:\s*(?P<nss1>[0-9, ,(,),%]*)'
        '\n\s*:?\s*(?P<nss2>[0-9, ,(,),%]*)')
    TX_REGEX = re.compile(
        r'TX (?P<mode>MCS|VHT|HE|EHT)\s+:\s*(?P<nss1>[0-9, ,(,),%]*)'
        '\n\s*:?\s*(?P<nss2>[0-9, ,(,),%]*)')
    TX_PER_REGEX = re.compile(
        r'(?P<mode>\S+) PER\s+:\s*(?P<nss1>[0-9, ,(,),%]*)'
        '\n\s*:?\s*(?P<nss2>[0-9, ,(,),%]*)')
    RX_GOOD_FCS_REGEX = re.compile(r'goodfcs (?P<rx_good_fcs>[0-9]*)')
    RX_BAD_FCS_REGEX = re.compile(r'rxbadfcs (?P<rx_bad_fcs>[0-9]*)')
    RX_AGG_REGEX = re.compile(r'rxmpduperampdu (?P<aggregation>[0-9]*)')
    TX_AGG_REGEX = re.compile(r' mpduperampdu (?P<aggregation>[0-9]*)')
    TX_AGG_STOP_REGEX = re.compile(
        r'agg stop reason: tot_agg_tried (?P<agg_tried>[0-9]+) agg_txcancel (?P<agg_canceled>[0-9]+) (?P<agg_stop_reason>.+)'
    )
    TX_AGG_STOP_REASON_REGEX = re.compile(
        r'(?P<reason>\w+) [0-9]+ \((?P<value>[0-9]+%)\)')
    MCS_ID = collections.namedtuple(
        'mcs_id', ['mode', 'num_streams', 'bandwidth', 'mcs', 'gi'])
    MODE_MAP = {'0': '11a/g', '1': '11b', '2': '11n', '3': '11ac'}
    BW_MAP = {'0': 20, '1': 40, '2': 80}

    def __init__(self, dut, llstats_enabled=True):
        self.dut = dut
        self.llstats_enabled = llstats_enabled
        self.llstats_cumulative = self._empty_llstats()
        self.llstats_incremental = self._empty_llstats()
        self.bandwidth = None

    def update_stats(self):
        if self.llstats_enabled:
            try:
                llstats_output = self.dut.adb.shell(self.LLSTATS_CMD,
                                                    timeout=1)

                self.dut.adb.shell_nb(self.LL_STATS_CLEAR_CMD)

                wl_join = self.dut.adb.shell("wl status")
                if not self.bandwidth:
                    self.bandwidth = int(
                        re.search(self.BW_REGEX, wl_join).group('bandwidth'))
            except:
                logging.debug('Failed to get counters and ampdu dumps.')
                llstats_output = ''
            try:
                phy_log_output = self.dut.adb.shell(self.BRCM_PHY_LOG_CMD,
                                                    ignore_status=True,
                                                    timeout=1)
                self.dut.adb.shell_nb(self.BRCM_PHY_LOG_CLEAR_CMD)
            except:
                logging.debug('Failed to get phy log.')
                phy_log_output = ''
        else:
            llstats_output = ''
            phy_log_output = ''
        self._update_stats(llstats_output, phy_log_output)

    def reset_stats(self):
        self.llstats_cumulative = self._empty_llstats()
        self.llstats_incremental = self._empty_llstats()

    def _empty_llstats(self):
        return collections.OrderedDict(mcs_stats=collections.OrderedDict(),
                                       mpdu_stats=collections.OrderedDict(),
                                       summary=collections.OrderedDict())

    def _empty_mcs_stat(self):
        return collections.OrderedDict(txmpdu=0,
                                       rxmpdu=0,
                                       mpdu_lost=0,
                                       retries=0,
                                       retries_short=0,
                                       retries_long=0)

    def _mcs_id_to_string(self, mcs_id):
        mcs_string = '{} Nss{} MCS{} GI{}'.format(mcs_id.mode,
                                                  mcs_id.num_streams,
                                                  mcs_id.mcs, mcs_id.gi)
        return mcs_string

    def _parse_mcs_stats(self, llstats_output):
        llstats_dict = {}
        # Look for per-peer stats
        match = re.search(self.RX_REGEX, llstats_output)
        if not match:
            self.reset_stats()
            return collections.OrderedDict()
        # Find and process all matches for per stream stats
        rx_match_iter = re.finditer(self.RX_REGEX, llstats_output)
        tx_match_iter = re.finditer(self.TX_REGEX, llstats_output)
        tx_per_match_iter = re.finditer(self.TX_PER_REGEX, llstats_output)
        for rx_match, tx_match, tx_per_match in zip(rx_match_iter,
                                                    tx_match_iter,
                                                    tx_per_match_iter):
            mode = rx_match.group('mode')
            mode = 'HT' if mode == 'MCS' else mode
            for nss in [1, 2]:
                rx_mcs_iter = re.finditer(self.MCS_REGEX,
                                          rx_match.group(nss + 1))
                tx_mcs_iter = re.finditer(self.MCS_REGEX,
                                          tx_match.group(nss + 1))
                tx_per_iter = re.finditer(self.MCS_REGEX,
                                          tx_per_match.group(nss + 1))
                for mcs, (rx_mcs_stats, tx_mcs_stats,
                          tx_per_mcs_stats) in enumerate(
                              itertools.zip_longest(rx_mcs_iter, tx_mcs_iter,
                                                    tx_per_iter)):
                    current_mcs = self.MCS_ID(
                        mode, nss, self.bandwidth,
                        mcs + int(8 * (mode == 'HT') * (nss - 1)), 0)
                    current_stats = collections.OrderedDict(
                        txmpdu=int(tx_mcs_stats.group('count'))
                        if tx_mcs_stats else 0,
                        rxmpdu=int(rx_mcs_stats.group('count'))
                        if rx_mcs_stats else 0,
                        mpdu_lost=0,
                        retries=tx_per_mcs_stats.group('count')
                        if tx_per_mcs_stats else 0,
                        retries_short=0,
                        retries_long=0,
                        mcs_id=current_mcs)
                    llstats_dict[self._mcs_id_to_string(
                        current_mcs)] = current_stats
        return llstats_dict

    def _parse_mpdu_stats(self, llstats_output):
        rx_agg_match = re.search(self.RX_AGG_REGEX, llstats_output)
        tx_agg_match = re.search(self.TX_AGG_REGEX, llstats_output)
        tx_agg_stop_match = re.search(self.TX_AGG_STOP_REGEX, llstats_output)
        rx_good_fcs_match = re.search(self.RX_GOOD_FCS_REGEX, llstats_output)
        rx_bad_fcs_match = re.search(self.RX_BAD_FCS_REGEX, llstats_output)

        mpdu_stats = collections.OrderedDict(
            rx_aggregation=int(rx_agg_match.group('aggregation'))
            if rx_agg_match else 0,
            tx_aggregation=int(tx_agg_match.group('aggregation'))
            if tx_agg_match else 0,
            tx_agg_tried=int(tx_agg_stop_match.group('agg_tried'))
            if tx_agg_stop_match else 0,
            tx_agg_canceled=int(tx_agg_stop_match.group('agg_canceled'))
            if tx_agg_stop_match else 0,
            rx_good_fcs=int(rx_good_fcs_match.group('rx_good_fcs'))
            if rx_good_fcs_match else 0,
            rx_bad_fcs=int(rx_bad_fcs_match.group('rx_bad_fcs'))
            if rx_bad_fcs_match else 0,
            agg_stop_reason=collections.OrderedDict())
        if tx_agg_stop_match:
            agg_reason_match = re.finditer(
                self.TX_AGG_STOP_REASON_REGEX,
                tx_agg_stop_match.group('agg_stop_reason'))
            for reason_match in agg_reason_match:
                mpdu_stats['agg_stop_reason'][reason_match.group(
                    'reason')] = reason_match.group('value')

        return mpdu_stats

    def _generate_stats_summary(self, llstats_dict):
        llstats_summary = collections.OrderedDict(common_tx_mcs=None,
                                                  common_tx_mcs_count=0,
                                                  common_tx_mcs_freq=0,
                                                  common_rx_mcs=None,
                                                  common_rx_mcs_count=0,
                                                  common_rx_mcs_freq=0,
                                                  rx_per=float('nan'))
        mcs_ids = []
        tx_mpdu = []
        rx_mpdu = []
        phy_rates = []
        for mcs_str, mcs_stats in llstats_dict['mcs_stats'].items():
            mcs_id = mcs_stats['mcs_id']
            mcs_ids.append(mcs_str)
            tx_mpdu.append(mcs_stats['txmpdu'])
            rx_mpdu.append(mcs_stats['rxmpdu'])
            phy_rates.append(RATE_TABLE[mcs_id.mode][mcs_id.num_streams][
                mcs_id.bandwidth][mcs_id.mcs])
        if len(tx_mpdu) == 0 or len(rx_mpdu) == 0:
            return llstats_summary
        llstats_summary['common_tx_mcs'] = mcs_ids[numpy.argmax(tx_mpdu)]
        llstats_summary['common_tx_mcs_count'] = numpy.max(tx_mpdu)
        llstats_summary['common_rx_mcs'] = mcs_ids[numpy.argmax(rx_mpdu)]
        llstats_summary['common_rx_mcs_count'] = numpy.max(rx_mpdu)
        if sum(tx_mpdu):
            llstats_summary['mean_tx_phy_rate'] = numpy.average(
                phy_rates, weights=tx_mpdu)
            llstats_summary['common_tx_mcs_freq'] = (
                llstats_summary['common_tx_mcs_count'] / sum(tx_mpdu))
        else:
            llstats_summary['mean_tx_phy_rate'] = 0
            llstats_summary['common_tx_mcs_freq'] = 0
        if sum(rx_mpdu):
            llstats_summary['mean_rx_phy_rate'] = numpy.average(
                phy_rates, weights=rx_mpdu)
            llstats_summary['common_rx_mcs_freq'] = (
                llstats_summary['common_rx_mcs_count'] / sum(rx_mpdu))
            total_rx_frames = llstats_dict['mpdu_stats'][
                'rx_good_fcs'] + llstats_dict['mpdu_stats']['rx_bad_fcs']
            if total_rx_frames:
                llstats_summary['rx_per'] = (
                    llstats_dict['mpdu_stats']['rx_bad_fcs'] /
                    total_rx_frames) * 100
        else:
            llstats_summary['mean_rx_phy_rate'] = 0
            llstats_summary['common_rx_mcs_freq'] = 0
            llstats_summary['rx_per'] = 0
        return llstats_summary

    def _update_stats(self, llstats_output, phy_log_output):
        self.llstats_cumulative = self._empty_llstats()
        self.llstats_incremental = self._empty_llstats()
        self.llstats_incremental['raw_output'] = llstats_output
        self.llstats_incremental['phy_log_output'] = phy_log_output
        self.llstats_incremental['mcs_stats'] = self._parse_mcs_stats(
            llstats_output)
        self.llstats_incremental['mpdu_stats'] = self._parse_mpdu_stats(
            llstats_output)
        self.llstats_incremental['summary'] = self._generate_stats_summary(
            self.llstats_incremental)
        self.llstats_cumulative['summary'] = self._generate_stats_summary(
            self.llstats_cumulative)
