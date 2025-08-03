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
import logging
import math
import os
import re
import statistics
import numpy
import time
from acts import asserts

SHORT_SLEEP = 1
MED_SLEEP = 6
STATION_DUMP = 'iw {} station dump'
SCAN = 'wpa_cli scan'
SCAN_RESULTS = 'wpa_cli scan_results'
SIGNAL_POLL = 'wpa_cli signal_poll'
WPA_CLI_STATUS = 'wpa_cli status'
RSSI_ERROR_VAL = float('nan')
FW_REGEX = re.compile(r'FW:(?P<firmware>\S+) HW:')


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
        try:
            signal_poll_output = dut.adb.shell(
                'wpa_cli -i {} signal_poll'.format(interface))
        except:
            signal_poll_output = ''
        match = re.search('FREQUENCY=.*', signal_poll_output)
        if match:
            frequency = int(match.group(0).split('=')[1])
            connected_rssi['frequency'].append(frequency)
        else:
            connected_rssi['frequency'].append(RSSI_ERROR_VAL)
        match = re.search('RSSI=.*', signal_poll_output)
        if match:
            temp_rssi = int(match.group(0).split('=')[1])
            if temp_rssi == -9999 or temp_rssi == 0:
                connected_rssi['signal_poll_rssi']['data'].append(
                    RSSI_ERROR_VAL)
            else:
                connected_rssi['signal_poll_rssi']['data'].append(temp_rssi)
        else:
            connected_rssi['signal_poll_rssi']['data'].append(RSSI_ERROR_VAL)
        match = re.search('AVG_RSSI=.*', signal_poll_output)
        if match:
            connected_rssi['signal_poll_avg_rssi']['data'].append(
                int(match.group(0).split('=')[1]))
        else:
            connected_rssi['signal_poll_avg_rssi']['data'].append(
                RSSI_ERROR_VAL)

        # Get per chain RSSI
        try:
            per_chain_rssi = dut.adb.shell(STATION_DUMP.format(interface))
        except:
            per_chain_rssi = ''
        match = re.search('.*signal avg:.*', per_chain_rssi)
        if match:
            per_chain_rssi = per_chain_rssi[per_chain_rssi.find('[') +
                                            1:per_chain_rssi.find(']')]
            per_chain_rssi = per_chain_rssi.split(', ')
            connected_rssi['chain_0_rssi']['data'].append(
                int(per_chain_rssi[0]))
            connected_rssi['chain_1_rssi']['data'].append(
                int(per_chain_rssi[1]))
        else:
            connected_rssi['chain_0_rssi']['data'].append(RSSI_ERROR_VAL)
            connected_rssi['chain_1_rssi']['data'].append(RSSI_ERROR_VAL)
        measurement_elapsed_time = time.time() - measurement_start_time
        time.sleep(max(0, polling_frequency - measurement_elapsed_time))

    # Compute mean RSSIs. Only average valid readings.
    # Output RSSI_ERROR_VAL if no valid connected readings found.
    for key, val in connected_rssi.copy().items():
        if 'data' not in val:
            continue
        filtered_rssi_values = [x for x in val['data'] if not math.isnan(x)]
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
        scan_output = dut.adb.shell(SCAN)
        time.sleep(MED_SLEEP)
        scan_output = dut.adb.shell(SCAN_RESULTS)
        for bssid in tracked_bssids:
            bssid_result = re.search(bssid + '.*',
                                     scan_output,
                                     flags=re.IGNORECASE)
            if bssid_result:
                bssid_result = bssid_result.group(0).split('\t')
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
    bdf_output = dut.adb.shell('cksum /vendor/firmware/bdwlan*')
    logging.debug('BDF Checksum output: {}'.format(bdf_output))
    bdf_signature = sum(
        [int(line.split(' ')[0]) for line in bdf_output.splitlines()]) % 1000

    fw_output = dut.adb.shell('halutil -logger -get fw')
    logging.debug('Firmware version output: {}'.format(fw_output))
    fw_version = re.search(FW_REGEX, fw_output).group('firmware')
    fw_signature = fw_version.split('.')[-3:-1]
    fw_signature = float('.'.join(fw_signature))
    serial_hash = int(hashlib.md5(dut.serial.encode()).hexdigest(), 16) % 1000
    return {
        'config_signature': bdf_signature,
        'fw_signature': fw_signature,
        'serial_hash': serial_hash
    }


def get_country_code(dut):
    country_code = dut.adb.shell('iw reg get | grep country | head -1')
    country_code = country_code.split(':')[0].split(' ')[1]
    if country_code == '00':
        country_code = 'WW'
    return country_code


def push_config(dut, config_file):
    config_files_list = dut.adb.shell(
        'ls /vendor/firmware/bdwlan*').splitlines()
    for dst_file in config_files_list:
        dut.push_system_file(config_file, dst_file)
    dut.reboot()


def start_wifi_logging(dut):
    dut.droid.wifiEnableVerboseLogging(1)
    msg = "Failed to enable WiFi verbose logging."
    asserts.assert_equal(dut.droid.wifiGetVerboseLoggingLevel(), 1, msg)
    logging.info('Starting CNSS logs')
    dut.adb.shell("find /data/vendor/wifi/wlan_logs/ -type f -delete",
                  ignore_status=True)
    dut.adb.shell_nb('cnss_diag -f -s')


def stop_wifi_logging(dut):
    logging.info('Stopping CNSS logs')
    dut.adb.shell('killall cnss_diag')
    logs = dut.get_file_names("/data/vendor/wifi/wlan_logs/")
    if logs:
        dut.log.info("Pulling cnss_diag logs %s", logs)
        log_path = os.path.join(dut.device_log_path,
                                "CNSS_DIAG_%s" % dut.serial)
        os.makedirs(log_path, exist_ok=True)
        dut.pull_files(logs, log_path)


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


def _set_ini_fields(ini_file_path, ini_field_dict):
    template_regex = r'^{}=[0-9,.x-]+'
    with open(ini_file_path, 'r') as f:
        ini_lines = f.read().splitlines()
        for idx, line in enumerate(ini_lines):
            for field_name, field_value in ini_field_dict.items():
                line_regex = re.compile(template_regex.format(field_name))
                if re.match(line_regex, line):
                    ini_lines[idx] = '{}={}'.format(field_name, field_value)
                    print(ini_lines[idx])
    with open(ini_file_path, 'w') as f:
        f.write('\n'.join(ini_lines) + '\n')


def _edit_dut_ini(dut, ini_fields):
    """Function to edit Wifi ini files."""
    dut_ini_path = '/vendor/firmware/wlan/qcom_cfg.ini'
    local_ini_path = os.path.expanduser('~/qcom_cfg.ini')
    dut.pull_files(dut_ini_path, local_ini_path)

    _set_ini_fields(local_ini_path, ini_fields)

    dut.push_system_file(local_ini_path, dut_ini_path)
    # For 1x1 mode, we need to wait for sl4a to load (To avoid crashes)
    dut.reboot(timeout=300, wait_after_reboot_complete=120)


def set_chain_mask(dut, chain_mask):
    curr_mask = getattr(dut, 'chain_mask', '2x2')
    if curr_mask == chain_mask:
        return
    dut.chain_mask = chain_mask
    if chain_mask == '2x2':
        ini_fields = {
            'gEnable2x2': 2,
            'gSetTxChainmask1x1': 1,
            'gSetRxChainmask1x1': 1,
            'gDualMacFeatureDisable': 6,
            'gDot11Mode': 0
        }
    else:
        ini_fields = {
            'gEnable2x2': 0,
            'gSetTxChainmask1x1': chain_mask + 1,
            'gSetRxChainmask1x1': chain_mask + 1,
            'gDualMacFeatureDisable': 1,
            'gDot11Mode': 0
        }
    _edit_dut_ini(dut, ini_fields)


def set_wifi_mode(dut, mode):
    TX_MODE_DICT = {
        'Auto': 0,
        '11n': 4,
        '11ac': 9,
        '11abg': 1,
        '11b': 2,
        '11': 3,
        '11g only': 5,
        '11n only': 6,
        '11b only': 7,
        '11ac only': 8
    }

    ini_fields = {
        'gEnable2x2': 2,
        'gSetTxChainmask1x1': 1,
        'gSetRxChainmask1x1': 1,
        'gDualMacFeatureDisable': 6,
        'gDot11Mode': TX_MODE_DICT[mode]
    }
    _edit_dut_ini(dut, ini_fields)


class LinkLayerStats():

    LLSTATS_CMD = 'cat /d/wlan0/ll_stats'
    MOUNT_CMD = 'mount -t debugfs debugfs /sys/kernel/debug'
    PEER_REGEX = 'LL_STATS_PEER_ALL'
    MCS_REGEX = re.compile(
        r'preamble: (?P<mode>\S+), nss: (?P<num_streams>\S+), bw: (?P<bw>\S+), '
        'mcs: (?P<mcs>\S+), bitrate: (?P<rate>\S+), txmpdu: (?P<txmpdu>\S+), '
        'rxmpdu: (?P<rxmpdu>\S+), mpdu_lost: (?P<mpdu_lost>\S+), '
        'retries: (?P<retries>\S+), retries_short: (?P<retries_short>\S+), '
        'retries_long: (?P<retries_long>\S+)')
    MCS_ID = collections.namedtuple(
        'mcs_id', ['mode', 'num_streams', 'bandwidth', 'mcs', 'rate'])
    MODE_MAP = {'0': '11a/g', '1': '11b', '2': '11n', '3': '11ac', '4': '11ax'}
    BW_MAP = {'0': 20, '1': 40, '2': 80, '3':160}

    def __init__(self, dut, llstats_enabled=True):
        self.dut = dut
        self.llstats_enabled = llstats_enabled
        self.llstats_cumulative = self._empty_llstats()
        self.llstats_incremental = self._empty_llstats()

    def update_stats(self):
        if self.llstats_enabled:
            # Checking the files to see if the device is mounted to enable
            # llstats capture
            mount_check = len(self.dut.get_file_names('/d/wlan0'))
            if not(mount_check):
              self.dut.adb.shell(self.MOUNT_CMD, timeout=10)

            try:
                llstats_output = self.dut.adb.shell(self.LLSTATS_CMD,
                                                    timeout=0.1)
            except:
                llstats_output = ''
        else:
            llstats_output = ''
        self._update_stats(llstats_output)

    def reset_stats(self):
        self.llstats_cumulative = self._empty_llstats()
        self.llstats_incremental = self._empty_llstats()

    def _empty_llstats(self):
        return collections.OrderedDict(mcs_stats=collections.OrderedDict(),
                                       summary=collections.OrderedDict())

    def _empty_mcs_stat(self):
        return collections.OrderedDict(txmpdu=0,
                                       rxmpdu=0,
                                       mpdu_lost=0,
                                       retries=0,
                                       retries_short=0,
                                       retries_long=0)

    def _mcs_id_to_string(self, mcs_id):
        mcs_string = '{} {}MHz Nss{} MCS{} {}Mbps'.format(
            mcs_id.mode, mcs_id.bandwidth, mcs_id.num_streams, mcs_id.mcs,
            mcs_id.rate)
        return mcs_string

    def _parse_mcs_stats(self, llstats_output):
        llstats_dict = {}
        # Look for per-peer stats
        match = re.search(self.PEER_REGEX, llstats_output)
        if not match:
            self.reset_stats()
            return collections.OrderedDict()
        # Find and process all matches for per stream stats
        match_iter = re.finditer(self.MCS_REGEX, llstats_output)
        for match in match_iter:
            current_mcs = self.MCS_ID(self.MODE_MAP[match.group('mode')],
                                      int(match.group('num_streams')) + 1,
                                      self.BW_MAP[match.group('bw')],
                                      int(match.group('mcs'), 16),
                                      int(match.group('rate'), 16) / 1000)
            current_stats = collections.OrderedDict(
                txmpdu=int(match.group('txmpdu')),
                rxmpdu=int(match.group('rxmpdu')),
                mpdu_lost=int(match.group('mpdu_lost')),
                retries=int(match.group('retries')),
                retries_short=int(match.group('retries_short')),
                retries_long=int(match.group('retries_long')))
            llstats_dict[self._mcs_id_to_string(current_mcs)] = current_stats
        return llstats_dict

    def _diff_mcs_stats(self, new_stats, old_stats):
        stats_diff = collections.OrderedDict()
        for stat_key in new_stats.keys():
            stats_diff[stat_key] = new_stats[stat_key] - old_stats[stat_key]
        return stats_diff

    def _generate_stats_summary(self, llstats_dict):
        llstats_summary = collections.OrderedDict(common_tx_mcs=None,
                                                  common_tx_mcs_count=0,
                                                  common_tx_mcs_freq=0,
                                                  common_rx_mcs=None,
                                                  common_rx_mcs_count=0,
                                                  common_rx_mcs_freq=0,
                                                  rx_per=float('nan'))

        phy_rates=[]
        tx_mpdu=[]
        rx_mpdu=[]
        txmpdu_count = 0
        rxmpdu_count = 0
        for mcs_id, mcs_stats in llstats_dict['mcs_stats'].items():
            # Extract the phy-rates
            mcs_id_split=mcs_id.split();
            phy_rates.append(float(mcs_id_split[len(mcs_id_split)-1].split('M')[0]))
            rx_mpdu.append(mcs_stats['rxmpdu'])
            tx_mpdu.append(mcs_stats['txmpdu'])
            if mcs_stats['txmpdu'] > llstats_summary['common_tx_mcs_count']:
                llstats_summary['common_tx_mcs'] = mcs_id
                llstats_summary['common_tx_mcs_count'] = mcs_stats['txmpdu']
            if mcs_stats['rxmpdu'] > llstats_summary['common_rx_mcs_count']:
                llstats_summary['common_rx_mcs'] = mcs_id
                llstats_summary['common_rx_mcs_count'] = mcs_stats['rxmpdu']
            txmpdu_count += mcs_stats['txmpdu']
            rxmpdu_count += mcs_stats['rxmpdu']

        if len(tx_mpdu) == 0 or len(rx_mpdu) == 0:
            return llstats_summary

        # Calculate the average tx/rx -phy rates
        if sum(tx_mpdu) and sum(rx_mpdu):
            llstats_summary['mean_tx_phy_rate'] = numpy.average(phy_rates, weights=tx_mpdu)
            llstats_summary['mean_rx_phy_rate'] = numpy.average(phy_rates, weights=rx_mpdu)

        if txmpdu_count:
            llstats_summary['common_tx_mcs_freq'] = (
                llstats_summary['common_tx_mcs_count'] / txmpdu_count)
        if rxmpdu_count:
            llstats_summary['common_rx_mcs_freq'] = (
                llstats_summary['common_rx_mcs_count'] / rxmpdu_count)
        return llstats_summary

    def _update_stats(self, llstats_output):
        # Parse stats
        new_llstats = self._empty_llstats()
        new_llstats['mcs_stats'] = self._parse_mcs_stats(llstats_output)
        # Save old stats and set new cumulative stats
        old_llstats = self.llstats_cumulative.copy()
        self.llstats_cumulative = new_llstats.copy()
        # Compute difference between new and old stats
        self.llstats_incremental = self._empty_llstats()
        for mcs_id, new_mcs_stats in new_llstats['mcs_stats'].items():
            old_mcs_stats = old_llstats['mcs_stats'].get(
                mcs_id, self._empty_mcs_stat())
            self.llstats_incremental['mcs_stats'][
                mcs_id] = self._diff_mcs_stats(new_mcs_stats, old_mcs_stats)
        # Generate llstats summary
        self.llstats_incremental['summary'] = self._generate_stats_summary(
            self.llstats_incremental)
        self.llstats_cumulative['summary'] = self._generate_stats_summary(
            self.llstats_cumulative)
