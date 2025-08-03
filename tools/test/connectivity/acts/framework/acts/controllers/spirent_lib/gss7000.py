#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""
Python module for Spirent GSS7000 GNSS simulator.
@author: Clay Liao (jianhsiungliao@)
"""
from time import sleep
import xml.etree.ElementTree as ET
from acts.controllers import abstract_inst


def get_xml_text(xml_string='', tag=''):
    """Parse xml from string and return specific tag

        Args:
            xml_string: xml string,
                Type, Str.
            tag: tag in xml,
                Type, Str.

        Returns:
            text: Text content in the tag
                Type, Str.
        """
    if xml_string and tag:
        root = ET.fromstring(xml_string)
        try:
            text = str(root.find(tag).text).rstrip().lstrip()
        except ValueError:
            text = 'INVALID DATA'
    else:
        text = 'INVALID DATA'
    return text


class GSS7000Error(abstract_inst.SocketInstrumentError):
    """GSS7000 Instrument Error Class."""


class AbstractInstGss7000(abstract_inst.SocketInstrument):
    """Abstract instrument for  GSS7000"""

    def _query(self, cmd):
        """query instrument via Socket.

        Args:
            cmd: Command to send,
                Type, Str.

        Returns:
            resp: Response from Instrument via Socket,
                Type, Str.
        """
        self._send(cmd)
        self._wait()
        resp = self._recv()
        return resp

    def _wait(self, wait_time=1):
        """wait function
        Args:
            wait_time: wait time in sec.
                Type, int,
                Default, 1.
        """
        sleep(wait_time)


class GSS7000Ctrl(AbstractInstGss7000):
    """GSS7000 control daemon class"""

    def __init__(self, ip_addr, ip_port=7717):
        """Init method for GSS7000 Control Daemon.

        Args:
            ip_addr: IP Address.
                Type, str.
            ip_port: TCPIP Port.
                Type, str.
        """
        super().__init__(ip_addr, ip_port)
        self.idn = 'Spirent-GSS7000 Control Daemon'

    def connect(self):
        """Init and Connect to GSS7000 Control Daemon."""
        # Connect socket then connect socket again
        self._close_socket()
        self._connect_socket()
        # Stop GSS7000 Control Daeamon Then Start
        self._query('STOP_ENGINE')
        self._wait()
        self._query('START_ENGINE')

    def close(self):
        """Close GSS7000 control daemon"""
        self._close_socket()
        self._logger.debug('Closed connection to GSS7000 control daemon')


class GSS7000(AbstractInstGss7000):
    """GSS7000 Class, inherted from abstract_inst SocketInstrument."""

    def __init__(self, ip_addr, engine_ip_port=15650, ctrl_ip_port=7717):
        """Init method for GSS7000.

        Args:
            ip_addr: IP Address.
                Type, str.
            engine_ip_port: TCPIP Port for
                Type, str.
            ctrl_ip_port: TCPIP Port for Control Daemon
        """
        super().__init__(ip_addr, engine_ip_port)
        self.idn = ''
        self.connected = False
        self.capability = []
        self.gss7000_ctrl_daemon = GSS7000Ctrl(ip_addr, ctrl_ip_port)
        # Close control daemon and engine sockets at the beginning
        self.gss7000_ctrl_daemon._close_socket()
        self._close_socket()

    def connect(self):
        """Connect GSS7000 engine daemon"""
        # Connect control daemon socket
        self._logger.debug('Connect to GSS7000')
        self.gss7000_ctrl_daemon.connect()
        # Connect to remote engine socket
        self._wait()
        self._connect_socket()
        self.connected = True
        self.get_hw_capability()

    def close(self):
        """Close GSS7000 engine daemon"""
        # Close GSS7000 control daemon
        self.gss7000_ctrl_daemon.close()
        # Close GSS7000 engine daemon
        self._close_socket()
        self._logger.debug('Closed connection to GSS7000 engine daemon')

    def _parse_hw_cap(self, xml):
        """Parse GSS7000 hardware capability xml to list.
            Args:
                xml: hardware capability xml,
                    Type, str.

            Returns:
                capability: Hardware capability dictionary
                    Type, list.
        """
        root = ET.fromstring(xml)
        capability_ls = []
        sig_cap_list = root.find('data').find('Signal_capabilities').findall(
            'Signal')
        for signal in sig_cap_list:
            value = str(signal.text).rstrip().lstrip()
            capability_ls.extend(value.upper().split(' '))
        return capability_ls

    def get_hw_capability(self):
        """Check GSS7000 hardware capability

            Returns:
                capability: Hardware capability dictionary,
                    Type, list.
        """
        if self.connected:
            capability_xml = self._query('GET_LICENCED_HARDWARE_CAPABILITY')
            self.capability = self._parse_hw_cap(capability_xml)

        return self.capability

    def get_idn(self):
        """Get the SimREPLAYplus Version

        Returns:
            SimREPLAYplus Version
        """
        idn_xml = self._query('*IDN?')
        self.idn = get_xml_text(idn_xml, 'data')
        return self.idn

    def load_scenario(self, scenario=''):
        """Load the scenario.

        Args:
            scenario: path of scenario,
                Type, str
        """
        if scenario == '':
            errmsg = ('Missing scenario file')
            raise GSS7000Error(error=errmsg, command='load_scenario')
        self._logger.debug('Stopped the original scenario')
        self._query('-,EN,1')
        cmd = 'SC,' + scenario
        self._logger.debug('Loading scenario')
        self._query(cmd)
        self._logger.debug('Scenario is loaded')
        return True

    def start_scenario(self, scenario=''):
        """Load and Start the running scenario.

        Args:
            scenario: path of scenario,
                Type, str
        """
        if scenario:
            if self.load_scenario(scenario):
                self._query('RU')
        # TODO: Need to refactor the logic design to solve the comment in ag/19222896
        # Track the issue in b/241200605
            else:
                infmsg = 'No scenario is loaded. Stop running scenario'
                self._logger.debug(infmsg)
        else:
            pass

        if scenario:
            infmsg = f'Started running scenario {scenario}'
        else:
            infmsg = 'Started running current scenario'

        self._logger.debug(infmsg)

    def get_scenario_name(self):
        """Get current scenario name"""
        sc_name_xml = self._query('SC_NAME')
        return get_xml_text(sc_name_xml, 'data')

    def stop_scenario(self):
        """Stop the running scenario."""
        self._query('-,EN,1')
        self._logger.debug('Stopped running scenario')

    def set_power_offset(self, ant=1, power_offset=0):
        """Set Power Offset of GSS7000 Tx
        Args:
            ant: antenna number of GSS7000
            power_offset: transmit power offset level
                Type, float.
                Decimal, unit [dB]

        Raises:
            GSS7000Error: raise when power offset level is not in [-49, 15] range.
        """
        if not -49 <= power_offset <= 15:
            errmsg = (f'"power_offset" must be within [-49, 15], '
                      f'current input is {power_offset}')
            raise GSS7000Error(error=errmsg, command='set_power_offset')

        cmd = f'-,POW_LEV,V1_A{ant},{power_offset},GPS,0,0,1,1,1,1,0'
        self._query(cmd)

        infmsg = f'Set veichel 1 antenna {ant} power offset: {power_offset}'
        self._logger.debug(infmsg)

    def set_ref_power(self, ref_dBm=-130):
        """Set Ref Power of GSS7000 Tx
        Args:
            ref_dBm: transmit reference power level in dBm for GSS7000
                Type, float.
                Decimal, unit [dBm]

        Raises:
            GSS7000Error: raise when power offset level is not in [-170, -115] range.
        """
        if not -170 <= ref_dBm <= -115:
            errmsg = (f'"power_offset" must be within [-170, -115], '
                      f'current input is {ref_dBm}')
            raise GSS7000Error(error=errmsg, command='set_ref_power')
        cmd = f'REF_DBM,{ref_dBm:.1f}'
        self._query(cmd)
        infmsg = f'Set reference power level: {ref_dBm:.1f}'
        self._logger.debug(infmsg)

    def get_status(self, return_txt=False):
        """Get current GSS7000 Status
        Args:
            return_txt: booling for determining the return results
                Type, booling.
        """
        status_xml = self._query('NULL')
        status = get_xml_text(status_xml, 'status')
        if return_txt:
            status_dict = {
                '0': 'No Scenario loaded',
                '1': 'Not completed loading a scenario',
                '2': 'Idle, ready to run a scenario',
                '3': 'Arming the scenario',
                '4': 'Completed arming; or waiting for a command or'
                     'trigger signal to start the scenario',
                '5': 'Scenario running',
                '6': 'Current scenario is paused.',
                '7': 'Active scenario has stopped and has not been reset.'
                     'Waiting for further commands.'
            }
            return status_dict.get(status)
        return int(status)

    def set_power(self, power_level=-130):
        """Set Power Level of GSS7000 Tx
        Args:
            power_level: transmit power level
                Type, float.
                Decimal, unit [dBm]

        Raises:
            GSS7000Error: raise when power level is not in [-170, -115] range.
        """
        if not -170 <= power_level <= -115:
            errmsg = (f'"power_level" must be within [-170, -115], '
                      f'current input is {power_level}')
            raise GSS7000Error(error=errmsg, command='set_power')

        power_offset = power_level + 130
        self.set_power_offset(1, power_offset)
        self.set_power_offset(2, power_offset)

        infmsg = f'Set GSS7000 transmit power to "{power_level:.1f}"'
        self._logger.debug(infmsg)

    def power_lev_offset_cal(self, power_level=-130, sat='GPS', band='L1'):
        """Convert target power level to power offset for GSS7000 power setting
        Args:
            power_level: transmit power level
                Type, float.
                Decimal, unit [dBm]
                Default. -130
            sat_system: to set power level for all Satellites
                Type, str
                Option 'GPS/GLO/GAL'
                Type, str
            freq_band: Frequency band to set the power level
                Type, str
                Option 'L1/L5/B1I/B1C/B2A/E5'
                Default, '', assumed to be L1.
        Return:
            power_offset: The calculated power offset for setting GSS7000 GNSS target power.
        """
        gss7000_tx_pwr = {
            'GPS_L1': -130,
            'GPS_L5': -127.9,
            'GLONASS_F1': -131,
            'GALILEO_L1': -127,
            'GALILEO_E5': -122,
            'BEIDOU_B1I': -133,
            'BEIDOU_B1C': -130,
            'BEIDOU_B2A': -127,
            'QZSS_L1': -128.5,
            'QZSS_L5': -124.9,
            'IRNSS_L5': -130
        }

        sat_band = f'{sat}_{band}'
        infmsg = f'Target satellite system and band: {sat_band}'
        self._logger.debug(infmsg)
        default_pwr_lev = gss7000_tx_pwr.get(sat_band, -130)
        power_offset = power_level - default_pwr_lev
        infmsg = (
            f'Targer power: {power_level}; Default power: {default_pwr_lev};'
            f' Power offset: {power_offset}')
        self._logger.debug(infmsg)

        return power_offset

    def sat_band_convert(self, sat, band):
        """Satellite system and operation band conversion and check.
        Args:
            sat: to set power level for all Satellites
                Type, str
                Option 'GPS/GLO/GAL/BDS'
                Type, str
            band: Frequency band to set the power level
                Type, str
                Option 'L1/L5/B1I/B1C/B2A/F1/E5'
                Default, '', assumed to be L1.
        """
        sat_system_dict = {
            'GPS': 'GPS',
            'GLO': 'GLONASS',
            'GAL': 'GALILEO',
            'BDS': 'BEIDOU',
            'IRNSS': 'IRNSS',
            'ALL': 'GPS'
        }
        sat = sat_system_dict.get(sat, 'GPS')
        if band == '':
            infmsg = 'No band is set. Set to default band = L1'
            self._logger.debug(infmsg)
            band = 'L1'
        if sat == '':
            infmsg = 'No satellite system is set. Set to default sat = GPS'
            self._logger.debug(infmsg)
            sat = 'GPS'
        sat_band = f'{sat}_{band}'
        self._logger.debug(f'Current band: {sat_band}')
        self._logger.debug(f'Capability: {self.capability}')
        # Check if satellite standard and band are supported
        # If not in support list, return GPS_L1 as default
        if not sat_band in self.capability:
            errmsg = (
                f'Satellite system and band ({sat_band}) are not supported.'
                f'The GSS7000 support list: {self.capability}')
            raise GSS7000Error(error=errmsg, command='set_scenario_power')

        sat_band_tp = tuple(sat_band.split('_'))

        return sat_band_tp

    def set_scenario_power(self,
                           power_level=-130,
                           sat_id='',
                           sat_system='',
                           freq_band='L1'):
        """Set dynamic power for the running scenario.
        Args:
            power_level: transmit power level
                Type, float.
                Decimal, unit [dBm]
                Default. -130
            sat_id: set power level for specific satellite identifiers
                Type, int.
            sat_system: to set power level for specific system
                Type, str
                Option 'GPS/GLO/GAL/BDS'
                Type, str
                Default, '', assumed to be GPS.
            freq_band: Frequency band to set the power level
                Type, str
                Option 'L1/L5/B1I/B1C/B2A/F1/E5'
                Default, '', assumed to be L1.
        Raises:
            GSS7000Error: raise when power offset is not in [-49, -15] range.
        """
        band_dict = {
            'L1': 1,
            'L5': 2,
            'B2A': 2,
            'B1I': 1,
            'B1C': 1,
            'F1': 1,
            'E5': 2
        }

        # Convert and check satellite system and band
        sat, band = self.sat_band_convert(sat_system, freq_band)
        # Get freq band setting
        band_cmd = band_dict.get(band, 1)

        # When set sat_id --> control specific SV power.
        # When set is not set --> control all SVs of specific system power.
        if not sat_id:
            sat_id = 1
            all_tx_type = 1
        else:
            all_tx_type = 0
        # Convert absolute power level to absolute power offset.
        power_offset = self.power_lev_offset_cal(power_level, sat, band)

        if not -49 <= power_offset <= 15:
            errmsg = (f'"power_offset" must be within [-49, 15], '
                      f'current input is {power_offset}')
            raise GSS7000Error(error=errmsg, command='set_power_offset')

        # If no specific sat_system is set, the default is GPS L1.
        if band_cmd == 1:
            cmd = f'-,POW_LEV,v1_a1,{power_offset},{sat},{sat_id},0,0,0,1,1,{all_tx_type}'
            self._query(cmd)
        elif band_cmd == 2:
            cmd = f'-,POW_LEV,v1_a2,{power_offset},{sat},{sat_id},0,0,0,1,1,{all_tx_type}'
            self._query(cmd)
