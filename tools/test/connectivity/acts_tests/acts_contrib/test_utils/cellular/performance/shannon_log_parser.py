#!/usr/bin/env python3.4
#
#   Copyright 2022 - The Android Open Source Project
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

import datetime
import gzip
import itertools
import logging
import numpy
import os
import re
import shutil
import subprocess
import zipfile
from acts import context
from pathlib import Path

_DIGITS_REGEX = re.compile(r'-?\d+')
_TX_PWR_MAX = 100
_TX_PWR_MIN = -100


class LastNrPower:
    last_time = 0
    last_pwr = 0


class LastLteValue:
    last_time = 0
    last_pwr = 0


def _string_to_float(input_string):
    """Convert string to float value."""
    try:
        tmp = float(input_string)
    except ValueError:
        print(input_string)
        tmp = float('nan')
    return tmp


def to_time(time_str):
    """Convert time string to data time."""
    return datetime.datetime.strptime(time_str, '%H:%M:%S.%f')


def to_time_sec(time_str, start_time):
    """"Converts time string to seconds elapsed since start time."""
    return (to_time(time_str) - start_time).total_seconds()


class LogParser(object):
    """Base class to parse log csv files."""

    def __init__(self):
        self.timestamp_col = -1
        self.message_col = -1
        self.start_time = None

        # Dictionary of {log_item_header: log_time_parser} elements
        self.PARSER_INFO = {
            r'###[AS] RSRP[': self._parse_lte_rsrp,
            r'|CC0| PCell RSRP ': self._parse_lte_rsrp2,
            r'|CC0 Mx0 Sc0| PCell RSRP ': self._parse_lte_rsrp2,
            r'LT12 PUSCH_Power': self._parse_lte_power,
            r'UL_PWR(PUSCH)=>pwr_val:': self._parse_lte_power,
            r'[BM]SSB_RSRP(1)': self._parse_nr_rsrp,
            r'[BM]SSB_RSRP(0)': self._parse_nr_rsrp,
            r'###[AS] CurAnt': self._parse_nr_rsrp2,
            r'[PHY] RF module(': self._parse_fr2_rsrp,
            #r'[RF] PD : CC0 (monitoring) target_pwr': _parse_fr2_power,
            #r'[NrPhyTxScheduler][PuschCalcPower] Po_nominal_pusch': _parse_nr_power,
            r'[NrPhyTxPuschScheduler][PuschCalcPower] Po_nominal_pusch':
            self._parse_fr2_power,
            r'[RF NR SUB6] PD : CC0 (monitoring) target_pwr':
            self._parse_nr_power2,
            r'[RF NR SUB6] PD : CC1 (monitoring) target_pwr':
            self._parse_nr_power2,
            r'[AS] RFAPI_ChangeAntennaSwitch': self._parse_lte_ant_sel,
            r'[AS] Ant switching': self._parse_lte_ant_sel2,
            r'###[AS] Select Antenna': self._parse_nr_ant_sel,
            r'###[AS] CurAnt(': self._parse_nr_ant_sel2,
            r'[SAR][RESTORE]': self._parse_sar_mode,
            r'[SAR][NORMAL]': self._parse_sar_mode,
            r'[SAR][LIMITED-TAPC]': self._parse_tapc_sar_mode,
            r'###[TAS] [0] ProcessRestoreStage:: [RESTORE]':
            self._parse_nr_sar_mode,
            r'###[TAS] [0] ProcessNormalStage:: [NORMAL]':
            self._parse_nr_sar_mode,
            r'|CC0| UL Power : PRACH ': self._parse_lte_avg_power,
            r'activeStackId=0\, [Monitor] txPower ': self._parse_wcdma_power,
            r'[SAR][DYNAMIC] EN-DC(2) UsedAvgSarLTE': self._parse_sar_values,
            #r'[SAR][DYNAMIC] UsedAvgSarLTE_100s': self._parse_sar_values2,
            r'[SAR][DYNAMIC] EN-DC(0) UsedAvgSarLTE':
            self._parse_lte_sar_values,
            r'###[TAS] [0] CalcGain:: TotalUsedSar': self._parse_nr_sar_values,
            r'[SAR][DYNAMIC] IsLTEOn(1) IsNROn(0) ':
            self._parse_lte_sar_values,
            r'[SAR][DYNAMIC] IsLTEOn(1) IsNROn(1) ': self._parse_sar_values,
            r'[MAIN][VolteStatusInd] Volte status ': self._parse_volte_status,
            r'[PHY] CC0 SLP : dlGrantRatio(3)/ulGrantRatio(3)/RbRatio(3)':
            self._parse_df_value,
            r'CC0 AVG: CELLGROUP(0) DCI(D/U):': self._parse_df_value,
            r'[OL-AIT] band': self._parse_ul_mimo,
        }

        self.SAR_MODES = [
            'none', 'MIN', 'SAV_1', 'SAV_2', 'MAIN', 'PRE_SAV', 'LIMITED-TAPC',
            'MAX', 'none'
        ]
        self.SAR_MODES_DESC = [
            '', 'Minimum', 'Slope Saving1', 'Slope Saving2', 'Maintenance',
            'Pre-Save', 'Limited TAPC', 'Maximum', ''
        ]

    def parse_header(self, header_line):
        header = header_line.split(',')
        try:
            self.timestamp_col = header.index('PC Time')
            self.message_col = header.index('Message')
        except ValueError:
            print('Error: PC Time and Message fields are not present')
        try:
            self.core_id_col = header.index('Core ID')
        except:
            self.core_id_col = self.timestamp_col

    def parse_log(self, log_file, gap_options=0):
        """Extract required data from the exported CSV file."""

        log_data = LogData()
        log_data.gap_options = gap_options
        # Fr-1 as default
        fr_id = 0

        with open(log_file, 'r') as file:
            # Read header line
            header = file.readline()
            try:
                self.parse_header(header)
            except:
                print('Could not parse header')
                return log_data

            # Use first message for start time
            line = file.readline()
            print(line)
            line_data = line[1:-2].split('","')
            if len(line_data) < self.message_col:
                print('Error: Empty exported file')
                return log_data

            start_time = to_time(line_data[self.timestamp_col])

            print('Parsing log file ... ', end='', flush=True)
            for line in file:
                line_data = line[1:-2].split('","')
                if len(line_data) < self.message_col + 1:
                    continue

                message = line_data[self.message_col]
                if "frIdx 1 " in message:
                    fr_id = 1
                elif "frIdx 0 " in message:
                    fr_id = 0
                for line_prefix, line_parser in self.PARSER_INFO.items():
                    if message.startswith(line_prefix):
                        timestamp = to_time_sec(line_data[self.timestamp_col],
                                                start_time)
                        if self.core_id_col == self.timestamp_col:
                            line_parser(timestamp, message[len(line_prefix):],
                                        'L1', log_data, fr_id)
                        else:
                            if " CC1 " in message:
                                line_parser(timestamp,
                                            message[len(line_prefix):], 'L2',
                                            log_data, fr_id)
                            else:
                                line_parser(timestamp,
                                            message[len(line_prefix):],
                                            line_data[self.core_id_col],
                                            log_data, fr_id)
                        break

            if log_data.nr.tx_pwr_time:
                if log_data.nr.tx_pwr_time[1] > log_data.nr.tx_pwr_time[0] + 50:
                    log_data.nr.tx_pwr_time = log_data.nr.tx_pwr_time[1:]
                    log_data.nr.tx_pwr = log_data.nr.tx_pwr[1:]

            self._find_cur_ant(log_data.lte)
            self._find_cur_ant(log_data.nr)
        return log_data

    def get_file_start_time(self, log_file):
        # Fr-1 as default

        with open(log_file, 'r') as file:
            # Read header line
            header = file.readline()
            try:
                self.parse_header(header)
            except:
                print('Could not parse header')
                return None

            # Use first message for start time
            line = file.readline()
            line_data = line[1:-2].split('","')
            if len(line_data) < self.message_col:
                print('Error: Empty exported file')
                return None

            start_time = to_time(line_data[self.timestamp_col])
            return start_time

    def set_start_time(self, line):
        """Set start time of logs to the time in the line."""
        if len(line) == 0:
            print("Empty Line")
            return
        line_data = line[1:-2].split('","')
        self.start_time = to_time(line_data[self.timestamp_col])

    def get_message(self, line):
        """Returns message and timestamp for the line."""
        line_data = line[1:-2].split('","')
        if len(line_data) < self.message_col + 1:
            return None

        self.line_data = line_data
        return line_data[self.message_col]

    def get_time(self, line):
        """Convert time string to time in seconds from the start time."""
        line_data = line[1:-2].split('","')
        if len(line_data) < self.timestamp_col + 1:
            return 0

        return to_time_sec(line_data[self.timestamp_col], self.start_time)

    def _feed_nr_power(self, timestamp, tx_pwr, option, lte_nr, window,
                       default, interval):
        if option < 101 and LastNrPower.last_time > 0 and timestamp - LastNrPower.last_time > interval:
            #print ('window=',window, ' interval=',interval, ' gap=',timestamp-LastNrPower.last_time)
            ti = LastNrPower.last_time
            while ti < timestamp:
                ti += (timestamp - LastNrPower.last_time) / window
                lte_nr.tx_pwr_time.append(ti)
                if option == 0:
                    lte_nr.tx_pwr.append(tx_pwr / default)
                elif option == 1:
                    lte_nr.tx_pwr.append(LastNrPower.last_pwr)
                elif option == 2:
                    lte_nr.tx_pwr.append((tx_pwr + LastNrPower.last_pwr) / 2)
                elif option == 3:
                    lte_nr.tx_pwr.append(0)
                else:
                    lte_nr.tx_pwr.append(option)
        else:
            lte_nr.tx_pwr_time.append(timestamp)
            lte_nr.tx_pwr.append(tx_pwr)
        LastNrPower.last_time = timestamp
        LastNrPower.last_pwr = tx_pwr

    def _feed_lte_power(self, timestamp, tx_pwr, log_data, lte_nr, window,
                        default, interval):
        if log_data.gap_options <= 100 and LastLteValue.last_time > 0 and timestamp - LastLteValue.last_time > interval:
            #print ('window=',window, ' interval=',interval, ' gap=',timestamp-LastLteValue.last_time)
            ti = LastLteValue.last_time
            while ti < timestamp:
                ti += (timestamp - LastLteValue.last_time) / window
                lte_nr.tx_pwr_time.append(ti)
                if log_data.gap_options == 0:
                    lte_nr.tx_pwr.append(tx_pwr / default)
                elif log_data.gap_options == 1:
                    lte_nr.tx_pwr.append(LastLteValue.last_pwr)
                elif log_data.gap_options == 2:
                    lte_nr.tx_pwr.append((tx_pwr + LastLteValue.last_pwr) / 2)
                elif log_data.gap_options == 3:
                    lte_nr.tx_pwr.append(0)
                else:
                    lte_nr.tx_pwr.append(log_data.gap_options)
        else:
            lte_nr.tx_pwr_time.append(timestamp)
            lte_nr.tx_pwr.append(tx_pwr)
        LastLteValue.last_time = timestamp
        LastLteValue.last_pwr = tx_pwr

    def _parse_lte_power(self, timestamp, message, core_id, log_data, fr_id):
        match = re.search(r'-?\d+', message)
        if match:
            tx_pwr = _string_to_float(match.group())
            if _TX_PWR_MIN < tx_pwr < _TX_PWR_MAX:
                self._feed_lte_power(timestamp, tx_pwr, log_data, log_data.lte,
                                     20, 1, 1)

    def _parse_lte_rsrp(self, timestamp, message, core_id, log_data, fr_id):
        data = _DIGITS_REGEX.findall(message)
        rsrp0 = _string_to_float(data[0]) / 100.0
        rsrp1 = _string_to_float(data[1]) / 100.0
        if rsrp0 != 0.0 and rsrp1 != 0.0:
            log_data.lte.rsrp_time.append(timestamp)
            log_data.lte.rsrp_rx0.append(rsrp0)
            log_data.lte.rsrp_rx1.append(rsrp1)

    def _parse_lte_rsrp2(self, timestamp, message, core_id, log_data, fr_id):
        m = re.search('^\[ ?-?\d+ \((.*?)\)', message)
        if not m:
            return
        data = _DIGITS_REGEX.findall(m.group(1))
        if len(data) < 2:
            return
        rsrp0 = _string_to_float(data[0])
        rsrp1 = _string_to_float(data[1])
        if rsrp0 != 0.0 and rsrp1 != 0.0:
            log_data.lte.rsrp2_time.append(timestamp)
            log_data.lte.rsrp2_rx0.append(rsrp0)
            log_data.lte.rsrp2_rx1.append(rsrp1)

    def _parse_nr_rsrp(self, timestamp, message, core_id, log_data, fr_id):
        index = message.find('rx0/rx1/rx2/rx3')
        if index != -1:
            data = _DIGITS_REGEX.findall(message[index:])
            log_data.nr.rsrp_time.append(timestamp)
            log_data.nr.rsrp_rx0.append(_string_to_float(data[4]) / 100)
            log_data.nr.rsrp_rx1.append(_string_to_float(data[5]) / 100)

    def _parse_nr_rsrp2(self, timestamp, message, core_id, log_data, fr_id):
        index = message.find('Rsrp')
        if index != -1:
            data = _DIGITS_REGEX.findall(message[index:])
            log_data.nr.rsrp2_time.append(timestamp)
            log_data.nr.rsrp2_rx0.append(_string_to_float(data[0]) / 100)
            log_data.nr.rsrp2_rx1.append(_string_to_float(data[1]) / 100)

    def _parse_fr2_rsrp(self, timestamp, message, core_id, log_data, fr_id):
        index = message.find('rsrp')
        data = _DIGITS_REGEX.search(message)
        module_index = _string_to_float(data.group(0))
        data = _DIGITS_REGEX.findall(message[index:])
        rsrp = _string_to_float(data[0])

        if rsrp == 0:
            return
        if module_index == 0:
            log_data.fr2.rsrp0_time.append(timestamp)
            log_data.fr2.rsrp0.append(rsrp if rsrp < 999 else float('nan'))
        elif module_index == 1:
            log_data.fr2.rsrp1_time.append(timestamp)
            log_data.fr2.rsrp1.append(rsrp if rsrp < 999 else float('nan'))

    def _parse_fr2_power(self, timestamp, message, core_id, log_data, fr_id):
        data = _DIGITS_REGEX.findall(message)
        tx_pwr = _string_to_float(data[-1]) / 10
        if _TX_PWR_MIN < tx_pwr < _TX_PWR_MAX:
            log_data.fr2.tx_pwr_time.append(timestamp)
            log_data.fr2.tx_pwr.append(tx_pwr)

    def _parse_nr_power(self, timestamp, message, core_id, log_data, fr_id):
        data = _DIGITS_REGEX.findall(message)
        tx_pwr = _string_to_float(data[-1]) / 10
        if _TX_PWR_MIN < tx_pwr < _TX_PWR_MAX:
            if core_id == 'L2':
                self._feed_nr_power(timestamp, tx_pwr, log_data.gap_options,
                                    log_data.nr2, 5, 1, 1)
            else:
                self._feed_nr_power(timestamp, tx_pwr, log_data.gap_options,
                                    log_data.nr, 5, 1, 1)

    def _parse_nr_power2(self, timestamp, message, core_id, log_data, fr_id):
        if fr_id != 0:
            return
        data = _DIGITS_REGEX.findall(message)
        tx_pwr = _string_to_float(data[0]) / 10
        if _TX_PWR_MIN < tx_pwr < _TX_PWR_MAX:
            if core_id == 'L2':
                self._feed_nr_power(timestamp, tx_pwr, log_data.gap_options,
                                    log_data.nr2, 5, 1, 1)
            else:
                self._feed_nr_power(timestamp, tx_pwr, log_data.gap_options,
                                    log_data.nr, 5, 1, 1)

    def _parse_lte_ant_sel(self, timestamp, message, core_id, log_data, fr_id):
        data = _DIGITS_REGEX.findall(message)
        new_ant = _string_to_float(data[1])
        old_ant = _string_to_float(data[2])
        log_data.lte.ant_sel_time.append(timestamp)
        log_data.lte.ant_sel_old.append(old_ant)
        log_data.lte.ant_sel_new.append(new_ant)

    def _parse_lte_ant_sel2(self, timestamp, message, core_id, log_data,
                            fr_id):
        data = _DIGITS_REGEX.findall(message)
        if data[0] == '0':
            log_data.lte.cur_ant_time.append(timestamp)
            log_data.lte.cur_ant.append(0)
        elif data[0] == '1':
            log_data.lte.cur_ant_time.append(timestamp)
            log_data.lte.cur_ant.append(1)
        elif data[0] == '10':
            log_data.lte.cur_ant_time.append(timestamp)
            log_data.lte.cur_ant.append(1)
            log_data.lte.cur_ant_time.append(timestamp)
            log_data.lte.cur_ant.append(0)
        elif data[0] == '01':
            log_data.lte.cur_ant_time.append(timestamp)
            log_data.lte.cur_ant.append(0)
            log_data.lte.cur_ant_time.append(timestamp)
            log_data.lte.cur_ant.append(1)

    def _parse_nr_ant_sel(self, timestamp, message, core_id, log_data, fr_id):
        data = _DIGITS_REGEX.findall(message)
        log_data.nr.ant_sel_time.append(timestamp)
        log_data.nr.ant_sel_new.append(_string_to_float(data[1]))
        log_data.nr.ant_sel_old.append(_string_to_float(data[0]))

    def _parse_nr_ant_sel2(self, timestamp, message, core_id, log_data, fr_id):
        data = _DIGITS_REGEX.findall(message)
        log_data.nr.ant_sel_time.append(timestamp)
        sel_ant = _string_to_float(data[0])
        log_data.nr.ant_sel_new.append(sel_ant)
        if log_data.nr.ant_sel_new:
            log_data.nr.ant_sel_old.append(log_data.nr.ant_sel_new[-1])

    def _parse_sar_mode(self, timestamp, message, core_id, log_data, fr_id):
        sar_mode = len(self.SAR_MODES) - 1
        for i, mode in enumerate(self.SAR_MODES):
            if message.startswith('[' + mode + ']'):
                sar_mode = i
        log_data.lte.sar_mode_time.append(timestamp)
        log_data.lte.sar_mode.append(sar_mode)

    def _parse_tapc_sar_mode(self, timestamp, message, core_id, log_data,
                             fr_id):
        log_data.lte.sar_mode_time.append(timestamp)
        log_data.lte.sar_mode.append(self.SAR_MODES.index('LIMITED-TAPC'))

    def _parse_nr_sar_mode(self, timestamp, message, core_id, log_data, fr_id):
        sar_mode = len(self.SAR_MODES) - 1
        for i, mode in enumerate(self.SAR_MODES):
            if message.startswith('[' + mode + ']'):
                sar_mode = i

        log_data.nr.sar_mode_time.append(timestamp)
        log_data.nr.sar_mode.append(sar_mode)

    def _parse_lte_avg_power(self, timestamp, message, core_id, log_data,
                             fr_id):
        data = _DIGITS_REGEX.findall(message)
        tx_pwr = _string_to_float(data[2])
        if _TX_PWR_MIN < tx_pwr < _TX_PWR_MAX:
            log_data.lte.tx_avg_pwr_time.append(timestamp)
            log_data.lte.tx_avg_pwr.append(tx_pwr)

    def _parse_wcdma_power(self, timestamp, message, core_id, log_data, fr_id):
        match = re.search(r'-?\d+', message)
        if match:
            tx_pwr = _string_to_float(match.group()) / 10
            if tx_pwr < _TX_PWR_MAX and tx_pwr > _TX_PWR_MIN:
                log_data.wcdma.tx_pwr_time.append(timestamp)
                log_data.wcdma.tx_pwr.append(tx_pwr)

    def _parse_sar_values(self, timestamp, message, core_id, log_data, fr_id):
        data = _DIGITS_REGEX.findall(message)
        log_data.endc_sar_time.append(timestamp)
        log_data.endc_sar_lte.append(_string_to_float(data[0]) / 1000.0)
        log_data.endc_sar_nr.append(_string_to_float(data[1]) / 1000.0)

    def _parse_sar_values2(self, timestamp, message, core_id, log_data, fr_id):
        data = _DIGITS_REGEX.findall(message)
        log_data.endc_sar_time.append(timestamp)
        log_data.endc_sar_lte.append(_string_to_float(data[-3]) / 1000.0)
        log_data.endc_sar_nr.append(_string_to_float(data[-1]) / 1000.0)

    def _parse_lte_sar_values(self, timestamp, message, core_id, log_data,
                              fr_id):
        data = _DIGITS_REGEX.findall(message)
        log_data.lte_sar_time.append(timestamp)
        log_data.lte_sar.append(_string_to_float(data[0]) / 1000.0)

    def _parse_nr_sar_values(self, timestamp, message, core_id, log_data,
                             fr_id):
        data = _DIGITS_REGEX.findall(message)
        log_data.nr_sar_time.append(timestamp)
        log_data.nr_sar.append(_string_to_float(data[0]) / 1000.0)

    def _parse_df_value(self, timestamp, message, core_id, log_data, fr_id):
        match = re.search(r' \d+', message)
        if match:
            nr_df = _string_to_float(match.group())
            log_data.nr.df = (nr_df / 1000 % 1000) / 100
            log_data.nr.duty_cycle_time.append(timestamp)
            log_data.nr.duty_cycle.append(log_data.nr.df * 100)
        else:
            match = re.search(r'\d+\\,\d+', message)
            if match:
                lte_df = match.group(0).split(",")
                log_data.lte.df = _string_to_float(lte_df[1]) / 100
                log_data.lte.duty_cycle_time.append(timestamp)
                log_data.lte.duty_cycle.append(log_data.nr.df * 100)

    def _parse_volte_status(self, timestamp, message, core_id, log_data,
                            fr_id):
        if message.startswith('[0 -> 1]'):
            log_data.volte_time.append(timestamp)
            log_data.volte_status.append(1)
        elif message.startswith('[3 -> 0]'):
            log_data.volte_time.append(timestamp)
            log_data.volte_status.append(0)

    def _parse_ul_mimo(self, timestamp, message, core_id, log_data, fr_id):
        match = re.search(r'UL-MIMO', message)
        if match:
            log_data.ul_mimo = 1

    def _find_cur_ant(self, log_data):
        """Interpolate antenna selection from antenna switching data."""
        if not log_data.cur_ant_time and log_data.ant_sel_time:
            if log_data.rsrp_time:
                start_time = log_data.rsrp_time[0]
                end_time = log_data.rsrp_time[-1]
            elif log_data.tx_pwr:
                start_time = log_data.tx_pwr_time[0]
                end_time = log_data.tx_pwr_time[-1]
            else:
                start_time = log_data.ant_sel_time[0]
                end_time = log_data.ant_sel_time[-1]

            [sel_time,
             sel_ant] = self.get_ant_selection(log_data.ant_sel_time,
                                               log_data.ant_sel_old,
                                               log_data.ant_sel_new,
                                               start_time, end_time)

            log_data.cur_ant_time = sel_time
            log_data.cur_ant = sel_ant

    def get_ant_selection(self, config_time, old_antenna_config,
                          new_antenna_config, start_time, end_time):
        """Generate antenna selection data from antenna switching information."""
        sel_time = []
        sel_ant = []
        if not config_time:
            return [sel_time, sel_ant]

        # Add data point for the start time
        if config_time[0] > start_time:
            sel_time = [start_time]
            sel_ant = [old_antenna_config[0]]

        # Add one data point before the switch and one data point after the switch.
        for i in range(len(config_time)):
            if not (i > 0
                    and old_antenna_config[i - 1] == old_antenna_config[i]
                    and new_antenna_config[i - 1] == new_antenna_config[i]):
                sel_time.append(config_time[i])
                sel_ant.append(old_antenna_config[i])
            sel_time.append(config_time[i])
            sel_ant.append(new_antenna_config[i])

        # Add data point for the end time
        if end_time > config_time[-1]:
            sel_time.append(end_time)
            sel_ant.append(new_antenna_config[-1])

        return [sel_time, sel_ant]


class RatLogData:
    """Log data structure for each RAT (LTE/NR)."""

    def __init__(self, label):

        self.label = label

        self.rsrp_time = []  # RSRP time
        self.rsrp_rx0 = []  # RSRP for receive antenna 0
        self.rsrp_rx1 = []  # RSRP for receive antenna 1

        # second set of RSRP logs
        self.rsrp2_time = []  # RSRP time
        self.rsrp2_rx0 = []  # RSRP for receive antenna 0
        self.rsrp2_rx1 = []  # RSRP for receive antenna 1

        self.ant_sel_time = []  # Antenna selection/switch time
        self.ant_sel_old = []  # Previous antenna selection
        self.ant_sel_new = []  # New antenna selection

        self.cur_ant_time = []  # Antenna selection/switch time
        self.cur_ant = []  # Previous antenna selection

        self.tx_pwr_time = []  # TX power time
        self.tx_pwr = []  # TX power

        self.tx_avg_pwr_time = []
        self.tx_avg_pwr = []

        self.sar_mode = []
        self.sar_mode_time = []

        self.df = 1.0  # Duty factor for UL transmission
        self.duty_cycle = []  # Duty factors for UL transmission
        self.duty_cycle_time = []  # Duty factors for UL transmission
        self.initial_power = 0
        self.sar_limit_dbm = None
        self.avg_window_size = 100


class LogData:
    """Log data structure."""

    def __init__(self):
        self.lte = RatLogData('LTE')
        self.lte.avg_window_size = 100

        self.nr = RatLogData('NR CC0')
        self.nr.avg_window_size = 100

        # NR 2nd CC
        self.nr2 = RatLogData('NR CC1')
        self.nr2.avg_window_size = 100

        self.wcdma = RatLogData('WCDMA')

        self.fr2 = RatLogData('FR2')
        self.fr2.rsrp0_time = []
        self.fr2.rsrp0 = []
        self.fr2.rsrp1_time = []
        self.fr2.rsrp1 = []
        self.fr2.avg_window_size = 4

        self.lte_sar_time = []
        self.lte_sar = []

        self.nr_sar_time = []
        self.nr_sar = []

        self.endc_sar_time = []
        self.endc_sar_lte = []
        self.endc_sar_nr = []

        self.volte_time = []
        self.volte_status = []

        # Options to handle data gaps
        self.gap_options = 0

        self.ul_mimo = 0  # Is UL_MIMO


class ShannonLogger(object):

    def __init__(self, dut=None, modem_bin=None, filter_file_path=None):
        self.dm_app = shutil.which(r'DMConsole')
        self.dut = dut
        if self.dut:
            self.modem_bin = self.pull_modem_file()
        elif modem_bin:
            self.modem_bin = modem_bin
        else:
            raise (RuntimeError,
                   'ShannonLogger requires AndroidDevice or modem binary.')
        self.filter_file = filter_file_path

    def pull_modem_file(self):
        local_modem_path = os.path.join(
            context.get_current_context().get_full_output_path(), 'modem_bin')
        os.makedirs(local_modem_path, exist_ok=True)
        try:
            self.dut.pull_files(
                '/mnt/vendor/modem_img/images/default/modem.bin',
                local_modem_path)
            modem_bin_file = os.path.join(local_modem_path, 'modem.bin')
        except:
            self.dut.pull_files(
                '/mnt/vendor/modem_img/images/default/modem.bin.gz',
                local_modem_path)
            modem_zip_file = os.path.join(local_modem_path, 'modem.bin.gz')
            modem_bin_file = modem_zip_file[:-3]
            with open(modem_zip_file, 'rb') as in_file:
                with open(modem_bin_file, 'wb') as out_file:
                    file_content = gzip.decompress(in_file.read())
                    out_file.write(file_content)
        return modem_bin_file

    def _unzip_log(self, log_zip_file, in_place=1):
        log_zip_file = Path(log_zip_file)
        with zipfile.ZipFile(log_zip_file, 'r') as zip_ref:
            file_names = zip_ref.namelist()
            if in_place:
                zip_dir = log_zip_file.parent
            else:
                zip_dir = log_zip_file.with_suffix('')
            zip_ref.extractall(zip_dir)
        unzipped_files = [
            os.path.join(zip_dir, file_name) for file_name in file_names
        ]
        return unzipped_files

    def unzip_modem_logs(self, log_zip_file):
        log_files = self._unzip_log(log_zip_file, in_place=0)
        sdm_files = []
        for log_file in log_files:
            if zipfile.is_zipfile(log_file):
                sdm_files.append(self._unzip_log(log_file, in_place=1)[0])
                os.remove(log_file)
            elif Path(
                    log_file
            ).suffix == '.sdm' and 'sbuff_power_on_log.sdm' not in log_file:
                sdm_files.append(log_file)
        return sorted(set(sdm_files))

    def _export_single_log(self, file):
        temp_file = str(Path(file).with_suffix('.csv'))
        if self.filter_file:
            export_cmd = [
                self.dm_app, 'traceexport', '-c', '-csv', '-f',
                self.filter_file, '-b', self.modem_bin, '-o', temp_file, file
            ]
        else:
            export_cmd = [
                self.dm_app, 'traceexport', '-c', '-csv', '-b', self.modem_bin,
                '-o', temp_file, file
            ]
        logging.debug('Executing: {}'.format(export_cmd))
        subprocess.call(export_cmd)
        return temp_file

    def _export_logs(self, log_files):
        csv_files = []
        for file in log_files:
            csv_files.append(self._export_single_log(file))
        return csv_files

    def _filter_log(self, input_filename, output_filename, write_header):
        """Export log messages from input file to output file."""
        log_parser = LogParser()
        with open(input_filename, 'r') as input_file:
            with open(output_filename, 'a') as output_file:

                header_line = input_file.readline()
                log_parser.parse_header(header_line)
                if log_parser.message_col == -1:
                    return

                if write_header:
                    output_file.write(header_line)
                    # Write next line for time reference.
                    output_file.write(input_file.readline())

                for line in input_file:
                    message = log_parser.get_message(line)
                    if message:
                        for filter_str in log_parser.PARSER_INFO:
                            if message.startswith(filter_str):
                                output_file.write(line)
                                break

    def _export_filtered_logs(self, csv_files):
        start_times = []
        log_parser = LogParser()
        reordered_csv_files = []
        for file in csv_files:
            start_time = log_parser.get_file_start_time(file)
            if start_time:
                start_times.append(start_time)
                reordered_csv_files.append(file)
        print(reordered_csv_files)
        print(start_times)
        file_order = numpy.argsort(start_times)
        print(file_order)
        reordered_csv_files = [reordered_csv_files[i] for i in file_order]
        print(reordered_csv_files)
        log_directory = Path(reordered_csv_files[0]).parent
        exported_file = os.path.join(log_directory, 'modem_log.csv')
        write_header = True
        for file in reordered_csv_files:
            self._filter_log(file, exported_file, write_header)
            write_header = False
        return exported_file

    def _parse_log(self, log_file, gap_options=0):
        """Extract required data from the exported CSV file."""
        log_parser = LogParser()
        log_data = log_parser.parse_log(log_file, gap_options=0)
        return log_data

    def process_log(self, log_zip_file):
        sdm_log_files = self.unzip_modem_logs(log_zip_file)
        csv_log_files = self._export_logs(sdm_log_files)
        exported_log = self._export_filtered_logs(csv_log_files)
        log_data = self._parse_log(exported_log, 0)
        for file in itertools.chain(sdm_log_files, csv_log_files):
            os.remove(file)
        return log_data
