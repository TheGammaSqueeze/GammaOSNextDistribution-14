"""Python module for Spectracom/Orolia GSG-6 GNSS simulator."""

from acts.controllers import abstract_inst


class GSG6Error(abstract_inst.SocketInstrumentError):
    """GSG-6 Instrument Error Class."""


class GSG6(abstract_inst.SocketInstrument):
    """GSG-6 Class, inherted from abstract_inst SocketInstrument."""

    def __init__(self, ip_addr, ip_port):
        """Init method for GSG-6.

        Args:
            ip_addr: IP Address.
                Type, str.
            ip_port: TCPIP Port.
                Type, str.
        """
        super(GSG6, self).__init__(ip_addr, ip_port)

        self.idn = ''

    def connect(self):
        """Init and Connect to GSG-6."""
        self._connect_socket()

        self.get_idn()

        infmsg = 'Connected to GSG-6, with ID: {}'.format(self.idn)
        self._logger.debug(infmsg)

    def close(self):
        """Close GSG-6."""
        self._close_socket()

        self._logger.debug('Closed connection to GSG-6')

    def get_idn(self):
        """Get the Idenification of GSG-6.

        Returns:
            GSG-6 Identifier
        """
        self.idn = self._query('*IDN?')

        return self.idn

    def start_scenario(self, scenario=''):
        """Start to run scenario.

        Args:
            scenario: Scenario to run.
                Type, str.
                Default, '', which will run current selected one.
        """
        if scenario:
            cmd = 'SOUR:SCEN:LOAD ' + scenario
            self._send(cmd)

        self._send('SOUR:SCEN:CONT START')

        if scenario:
            infmsg = 'Started running scenario {}'.format(scenario)
        else:
            infmsg = 'Started running current scenario'

        self._logger.debug(infmsg)

    def stop_scenario(self):
        """Stop the running scenario."""

        self._send('SOUR:SCEN:CONT STOP')

        self._logger.debug('Stopped running scenario')

    def preset(self):
        """Preset GSG-6 to default status."""
        self._send('*RST')

        self._logger.debug('Reset GSG-6')

    def set_power(self, power_level):
        """set GSG-6 transmit power on all bands.

        Args:
            power_level: transmit power level
                Type, float.
                Decimal, unit [dBm]

        Raises:
            GSG6Error: raise when power level is not in [-160, -65] range.
        """
        if not -160 <= power_level <= -65:
            errmsg = ('"power_level" must be within [-160, -65], '
                      'current input is {}').format(str(power_level))
            raise GSG6Error(error=errmsg, command='set_power')

        self._send(':SOUR:POW ' + str(round(power_level, 1)))

        infmsg = 'Set GSG-6 transmit power to "{}"'.format(round(
            power_level, 1))
        self._logger.debug(infmsg)

    def get_nmealog(self):
        """Get GSG6 NMEA data.

        Returns:
            GSG6's NMEA data
        """
        nmea_data = self._query('SOUR:SCEN:LOG?')

        return nmea_data

    def toggle_scenario_power(self,
                              toggle_onoff='ON',
                              sat_id='',
                              sat_system=''):
        """Toggle ON OFF scenario.

        Args:
            toggle_onoff: turn on or off the satellites
                Type, str. Option ON/OFF
                Default, 'ON'
            sat_id: satellite identifiers
                Type, str.
                Option 'Gxx/Rxx/Exx/Cxx/Jxx/Ixx/Sxxx'
                where xx is satellite identifiers no.
                e.g.: G10
            sat_system: to toggle On/OFF for all Satellites
                Type, str
                Option [GPS, GLO, GAL, BDS, QZSS, IRNSS, SBAS]
        Raises:
            GSG6Error: raise when toggle is not set.
        """
        if not sat_id and not sat_system:
            self._send(':SOUR:SCEN:POW ' + str(toggle_onoff))
            infmsg = 'Set GSG-6 Power to "{}"'.format(toggle_onoff)
            self._logger.debug(infmsg)

        elif sat_id and not sat_system:
            self._send(':SOUR:SCEN:POW ' + str(sat_id) + ',' +
                       str(toggle_onoff))
            infmsg = ('Set GSG-6 Power to "{}" for "{}" satellite '
                      'identifiers').format(toggle_onoff, sat_id)
            self._logger.debug(infmsg)

        elif not sat_id and sat_system:
            self._send(':SOUR:SCEN:POW ' + str(sat_system) + ',' +
                       str(toggle_onoff))
            infmsg = 'Set GSG-6 Power to "{}" for "{}" satellite system'.format(
                toggle_onoff, sat_system)
            self._logger.debug(infmsg)

        else:
            errmsg = ('"toggle power" must have either of these value [ON/OFF],'
                      ' current input is {}').format(str(toggle_onoff))
            raise GSG6Error(error=errmsg, command='toggle_scenario_power')

    def set_scenario_power(self,
                           power_level,
                           sat_id='',
                           sat_system='',
                           freq_band=''):
        """Set dynamic power for the running scenario.

        Args:
            power_level: transmit power level
                Type, float.
                Decimal, unit [dBm]
            sat_id: set power level for specific satellite identifiers
                Type, str. Option
                'Gxx/Rxx/Exx/Cxx/Jxx/Ixx/Sxxx'
                where xx is satellite identifiers number
                e.g.: G10
            sat_system: to set power level for all Satellites
                Type, str
                Option [GPS, GLO, GAL, BDS, QZSS, IRNSS, SBAS]
            freq_band: Frequency band to set the power level
                Type, str
                Option  [L1, L2, L5, ALL]
                Default, '', assumed to be L1.
        Raises:
            GSG6Error: raise when power level is not in [-160, -65] range.
        """
        if freq_band == 'ALL':
            if not -100 <= power_level <= 100:
                errmsg = ('"power_level" must be within [-100, 100], for '
                          '"freq_band"="ALL", current input is {}').format(
                              str(power_level))
                raise GSG6Error(error=errmsg, command='set_scenario_power')
        else:
            if not -160 <= power_level <= -65:
                errmsg = ('"power_level" must be within [-160, -65], for '
                          '"freq_band" != "ALL", current input is {}').format(
                              str(power_level))
                raise GSG6Error(error=errmsg, command='set_scenario_power')

        if sat_id and not sat_system:
            self._send(':SOUR:SCEN:POW ' + str(sat_id) + ',' +
                       str(round(power_level, 1)) + ',' + str(freq_band))
            infmsg = ('Set GSG-6 transmit power to "{}" for "{}" '
                      'satellite id').format(round(power_level, 1), sat_id)
            self._logger.debug(infmsg)

        elif not sat_id and sat_system:
            self._send(':SOUR:SCEN:POW ' + str(sat_system) + ',' +
                       str(round(power_level, 1)) + ',' + str(freq_band))
            infmsg = ('Set GSG-6 transmit power to "{}" for "{}" '
                      'satellite system').format(round(power_level, 1),
                                                 sat_system)
            self._logger.debug(infmsg)

        else:
            errmsg = ('sat_id or sat_system must have value, current input of '
                      'sat_id {} and sat_system {}').format(sat_id, sat_system)
            raise GSG6Error(error=errmsg, command='set_scenario_power')
