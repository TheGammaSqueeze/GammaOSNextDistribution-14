#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
import re
from acts import signals
from collections import defaultdict

SVID_RANGE = {
    'GPS': [(1, 32)],
    'SBA': [(120, 192)],
    'GLO': [(1, 24), (93, 106)],
    'QZS': [(193, 200)],
    'BDS': [(1, 63)],
    'GAL': [(1, 36)],
    'NIC': [(1, 14)]
}

CARRIER_FREQUENCIES = {
    'GPS': {
        'L1': [1575.42],
        'L5': [1176.45]
    },
    'SBA': {
        'L1': [1575.42]
    },
    'GLO': {
        'L1': [round((1602 + i * 0.5625), 3) for i in range(-7, 7)]
    },
    'QZS': {
        'L1': [1575.42],
        'L5': [1176.45]
    },
    'BDS': {
        'B1': [1561.098],
        'B2a': [1176.45]
    },
    'GAL': {
        'E1': [1575.42],
        'E5a': [1176.45]
    },
    'NIC': {
        'L5': [1176.45]
    }
}


class RegexParseException(Exception):
    pass


class GnssSvidContainer:
    """A class to hold the satellite svid information

    Attributes:
        used_in_fix: A dict contains unique svid used in fixing location
        not_used_in_fix: A dict contains unique svid not used in fixing location
    """

    def __init__(self):
        self.used_in_fix = defaultdict(set)
        self.not_used_in_fix = defaultdict(set)

    def add_satellite(self, gnss_status):
        """Add satellite svid into container

        According to the attributes gnss_status.used_in_fix
            True: add svid into self.used_in_fix container
            False: add svid into self.not_used_in_fix container

        Args:
            gnss_status: A GnssStatus object
        """
        key = f'{gnss_status.constellation}_{gnss_status.frequency_band}'
        if gnss_status.used_in_fix:
            self.used_in_fix[key].add(gnss_status.svid)
        else:
            self.not_used_in_fix[key].add(gnss_status.svid)


class GnssStatus:
    """GnssStatus object, it will create an obj with a raw gnssstatus line.

    Attributes:
        raw_message: (string) The raw log from GSPTool
            example:
                Fix: true Type: NIC SV: 4 C/No: 45.10782, 40.9 Elevation: 78.0
                  Azimuth: 291.0
                Signal: L5 Frequency: 1176.45 EPH: true ALM: false
                Fix: false Type: GPS SV: 27 C/No: 34.728134, 30.5 Elevation:
                  76.0 Azimuth: 15.0
                Signal: L1 Frequency: 1575.42 EPH: true ALM: true
        used_in_fix: (boolean) Whether or not this satellite info is used to fix
          location
        constellation: (string) The constellation type i.e. GPS
        svid: (int) The unique id of the constellation
        cn: (float) The C/No value from antenna
        base_cn: (float) The C/No value from baseband
        elev: (float) The value of elevation
        azim: (float) The value of azimuth
        frequency_band: (string) The frequency_type of the constellation i.e. L1
          / L5
    """

    gnssstatus_re = (
        r'Fix: (.*) Type: (.*) SV: (.*) C/No: (.*), (.*) '
        r'Elevation: (.*) Azimuth: (.*) Signal: (.*) Frequency: (.*) EPH')
    failures = []

    def __init__(self, gnssstatus_raw):
        status_res = re.search(self.gnssstatus_re, gnssstatus_raw)
        if not status_res:
            raise RegexParseException(f'Gnss raw msg parse fail:\n{gnssstatus_raw}\n'
                                      f'Please check it manually.')
        self.raw_message = gnssstatus_raw
        self.used_in_fix = status_res.group(1).lower() == 'true'
        self.constellation = status_res.group(2)
        self.svid = int(status_res.group(3))
        self.cn = float(status_res.group(4))
        self.base_cn = float(status_res.group(5))
        self.elev = float(status_res.group(6))
        self.azim = float(status_res.group(7))
        self.frequency_band = status_res.group(8)
        self.carrier_frequency = float(status_res.group(9))

    def validate_gnssstatus(self):
        """A validate function for each property."""
        self._validate_sv()
        self._validate_cn()
        self._validate_elev()
        self._validate_azim()
        self._validate_carrier_frequency()
        if self.failures:
            failure_info = '\n'.join(self.failures)
            raise signals.TestFailure(
                f'Gnsstatus validate failed:\n{self.raw_message}\n{failure_info}'
            )

    def _validate_sv(self):
        """A validate function for SV ID."""
        if not self.constellation in SVID_RANGE.keys():
            raise signals.TestFailure(
                f'Satellite identify fail: {self.constellation}')
        for id_range in SVID_RANGE[self.constellation]:
            if id_range[0] <= self.svid <= id_range[1]:
                break
        else:
            fail_details = f'{self.constellation} ID {self.svid} not in SV Range'
            self.failures.append(fail_details)

    def _validate_cn(self):
        """A validate function for CN value."""
        if not 0 <= self.cn <= 63:
            self.failures.append(f'Ant CN not in range: {self.cn}')
        if not 0 <= self.base_cn <= 63:
            self.failures.append(f'Base CN not in range: {self.base_cn}')

    def _validate_elev(self):
        """A validate function for Elevation (should between 0-90)."""
        if not 0 <= self.elev <= 90:
            self.failures.append(f'Elevation not in range: {self.elev}')

    def _validate_azim(self):
        """A validate function for Azimuth (should between 0-360)."""
        if not 0 <= self.azim <= 360:
            self.failures.append(f'Azimuth not in range: {self.azim}')

    def _validate_carrier_frequency(self):
        """A validate function for carrier frequency (should fall in below range).

           'GPS': L1:1575.42, L5:1176.45
           'SBA': L1:1575.42
           'GLO': L1:Between 1598.0625 and 1605.375
           'QZS': L1:1575.42, L5:1176.45
           'BDS': B1:1561.098, B2a:1176.45
           'GAL': E1:1575.42, E5a:1176.45
           'NIC': L5:1176.45
        """
        if self.frequency_band in CARRIER_FREQUENCIES[
                self.constellation].keys():
            target_freq = CARRIER_FREQUENCIES[self.constellation][
                self.frequency_band]
        else:
            raise signals.TestFailure(
                f'Carrier frequency identify fail: {self.frequency_band}')
        if not self.carrier_frequency in target_freq:
            self.failures.append(
                f'{self.constellation}_{self.frequency_band} carrier'
                f'frequency not in range: {self.carrier_frequency}')
