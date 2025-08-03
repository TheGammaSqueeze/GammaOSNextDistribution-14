#!/usr/bin/env python3
#
#   Copyright 2024 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#           http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
"""Provides classes for managing IPerf sessions on CMW500 callboxes."""

from enum import Enum
import time

_MEASUREMENT_SIZE = 5
_SERVER_LOSS_INDEX = 4
_CLIENT_THROUGHPUT_INDEX = 5
_SERVER_THROUGHPUT_INDEX = 3
_CLIENT_COUNT_INDEX = 2
_SERVER_COUNT_INDEX = 1


class IPerfMode(Enum):
    """Supported IPerf directions."""

    CLIENT = 'CLI'
    SERVER = 'SERV'


class IPerfType(Enum):
    """Supported Iperf applications."""

    IPERF = 'IPER'
    IPERF3 = 'IP3'
    # NAT = 'NAT' # unsupported ATM


class IPerfProtocol(Enum):
    """Supported protocol types."""

    TCP = 'TCP'
    UDP = 'UDP'


class IPerfMeasurementState(Enum):
    """Possible measurement states."""

    OFF = 'OFF'
    READY = 'RDY'
    RUN = 'RUN'


def _try_parse(s, fun):
    """Attempt to parse the value with the provided function or return None
    if an error is encountered.

    Args
        s: the string to parse
        fun: requested parse function.
    """
    try:
        return fun(s)
    except ValueError:
        return None


class Cmw500IPerfMeasurement(object):
    """Class for managing IPerf measurement sessions on CMX500."""
    def __init__(self, cmw, meas_id=1):
        """Initializes a new CMW500 Iperf Measurement.

        Examples::

            Using just a single client:
                measurement = Cmw500IPerfMeasurement(cmw)
                measurement.configure_services(0, 1)
                measurement.test_time = 10
                ...
                measurement.start()

                client = measurement.clients[0]
                last_count = None
                while measurement.state == IPerfMeasurementState.RUN:
                    count = client.count
                    throughput = client.throughput
                    if count != last_count:
                        last_count = count
                        print(f"Took measurement: {throughput}")
                    # Results are updated every 1 second, so wait 0.5 for margin
                    time.sleep(0.5)
        """
        self._cmw = cmw
        self._meas_id = meas_id

        # CMW can run up to a max of 8 client and 8 server instances in parallel
        # track of how many servers/cients have been requested.
        self._server_count = 0
        self._client_count = 0

        # CMW always has 8 clients and 8 servers, privately initialize all of
        # them so we can disable the ones that aren't requested.
        self._clients = [
            CMW500IPerfService(cmw, meas_id, i + 1, IPerfMode.CLIENT)
            for i in range(8)
        ]
        self._servers = [
            CMW500IPerfService(cmw, meas_id, i + 1, IPerfMode.SERVER)
            for i in range(8)
        ]

    def configure_services(self, server_count, client_count):
        """Configures the IPerf services.

        Args:
            server_count: the number of servers to initialize.
            client_count: the number of clients to initialize.
        """
        if server_count > 8:
            raise CmwIPerfError(
                'CMW500 supports a maximum of 8 active servers, {} requested'.
                format(server_count))
        if client_count > 8:
            raise CmwIPerfError(
                'CMW500 supports a maximum of 8 active clients, {} requested'.
                format(client_count))

        self._server_count = server_count
        self._client_count = client_count

        # CMW500 contains 8 servers/client applications, enable only requested
        # services and disable the rest.
        for i in range(server_count):
            self._servers[i].enabled = True
        for i in range(server_count, 8):
            self._servers[i].enabled = False

        for i in range(client_count):
            self._clients[i].enabled = True
        for i in range(client_count, 8):
            self._clients[i].enabled = False

    def start(self):
        """Starts the performance measurement on the callbox."""
        if self.test_type != IPerfType.IPERF:
            raise CmwIPerfError(
                'Unable to run performance test, test type {} not supported'.
                format(self.test_type))

        cmd = 'INITiate:DATA:MEAS{}:IPERf'.format(self._meas_id)
        self._cmw.send_and_recv(cmd)
        self._wait_for_state(
            {IPerfMeasurementState.RUN, IPerfMeasurementState.READY})

    def stop(self):
        """Halts the measurement immediately and puts it the RDY state."""
        cmd = 'STOP:DATA:MEAS{}:IPERf'.format(self._meas_id)
        self._cmw.send_and_recv(cmd)
        self._wait_for_state(
            {IPerfMeasurementState.OFF, IPerfMeasurementState.READY})

    def close(self):
        """Halts the measurement immediately and puts it the OFF state."""
        cmd = 'ABORt:DATA:MEAS{}:IPERf'.format(self._meas_id)
        self._cmw.send_and_recv(cmd)
        self._wait_for_state({IPerfMeasurementState.OFF})

    @property
    def servers(self):
        """Gets all enabled servers."""
        return self._servers[:self._server_count]

    @property
    def clients(self):
        """Gets all enabled clients."""
        return self._clients[:self._client_count]

    @property
    def ipv4_address(self):
        """Gets the current DAU IPv4 address."""
        cmd = 'SENSe:DATA:CONTrol:IPVFour:CURRent:IPADdress?'
        return self._cmw.send_and_recv(cmd).strip('"\'')

    @property
    def test_time(self):
        """Gets the duration of the IPerf measurement."""
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:TDURation?'.format(self._meas_id)
        return int(self._cmw.send_and_recv(cmd))

    @test_time.setter
    def test_time(self, duration):
        """Gets the duration of the IPerf measurement.

        Args:
            duration: the length of the IPerf measurement (in s).
        """
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:TDURation {}'.format(
            self._meas_id, duration)
        self._cmw.send_and_recv(cmd)

    @property
    def test_type(self):
        """Gets the type of IPerf application to use."""
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:TYPE?'.format(self._meas_id)
        return IPerfType(self._cmw.send_and_recv(cmd))

    @test_type.setter
    def test_type(self, mode):
        """Sets the type of IPerf application to use.

        Args:
            mode: IPER/IP3
        """
        if not isinstance(mode, IPerfType):
            raise ValueError('mode should be the instance of IPerfType')

        cmd = 'CONFigure:DATA:MEAS{}:IPERf:TYPE {}'.format(
            self._meas_id, mode.value)
        self._cmw.send_and_recv(cmd)

    @property
    def packet_size(self):
        """Gets the IPerf session packet size."""
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:PSIZe?'.format(self._meas_id)
        return int(self._cmw.send_and_recv(cmd))

    @packet_size.setter
    def packet_size(self, size):
        """Sets the IPerf session packet size.

        Args:
            size: the packet size in B
        """
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:PSIZe {}'.format(
            self._meas_id, size)
        self._cmw.send_and_recv(cmd)

    @property
    def state(self):
        """Gets the current state of the measurement"""
        cmd = 'FETCh:DATA:MEAS{}:IPERf:STATe?'.format(self._meas_id)
        return IPerfMeasurementState(self._cmw.send_and_recv(cmd))

    def _wait_for_state(self, states, timeout=10):
        """Polls the measurement state until it reaches an allowable state

        Args:
            states: the allowed states
            timeout: the maximum amount time to wait
        """
        while timeout > 0:
            if self.state in states:
                return

            time.sleep(1)
            timeout -= 1

        raise CmwIPerfError('Failed enter IPerf state: {}.'.format(states))


class CMW500IPerfService(object):
    """Class for controlling a single IPerf measurement instance."""
    def __init__(self, cmw, meas_id, service_id, mode):
        """Initializes a CMW500 IPerf service instance.

        Args:
            cmw: the cmw500 instrument controller.
            meas_idx: the cmw500 measurement instance to use (always 1).
            service_id: the client/sever id [1 - 8].
            mode: the IPerf mode to use (client/server).
        """
        self._cmw = cmw
        self._meas_id = meas_id
        self._service_id = service_id
        self._mode = mode

    @property
    def enabled(self):
        """Gets if the measurement is enabled."""
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:ENABle?'.format(
            self._meas_id, self._mode.value, self._service_id)
        return self._cmw.send_and_recv(cmd) == 'ON'

    @enabled.setter
    def enabled(self, enabled):
        """Sets if the measurement is enabled.

        Args:
            enabled: True/False
        """
        status = 'ON' if enabled else 'OFF'
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:ENABle {}'.format(
            self._meas_id, self._mode.value, self._service_id, status)
        self._cmw.send_and_recv(cmd)

    @property
    def protocol(self):
        """Gets the IPerf protocol."""
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:PROTocol?'.format(
            self._meas_id, self._mode.value, self._service_id)
        return IPerfProtocol(self._cmw.send_and_recv(cmd))

    @protocol.setter
    def protocol(self, protocol):
        """Sets the IPerf protocol.

        Args:
            protocol: TCP/UDP
        """
        if not isinstance(protocol, IPerfProtocol):
            raise ValueError(
                'protocol should be the instance of IPerfProtocolType')

        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:PROTocol {}'.format(
            self._meas_id, self._mode.value, self._service_id, protocol.value)
        self._cmw.send_and_recv(cmd)

    @property
    def ip_address(self):
        """Gets the service IP address (clients only)."""
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:IPADdress?'.format(
            self._meas_id, self._mode.value, self._service_id)
        return self._cmw.send_and_recv(cmd).strip('"\'')

    @ip_address.setter
    def ip_address(self, address):
        """Sets the service IP address (clients only).

        Args:
            address: ip address of the IPerf server
        """
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:IPADdress "{}"'.format(
            self._meas_id, self._mode.value, self._service_id, address)
        self._cmw.send_and_recv(cmd)

    @property
    def port(self):
        """Gets the IPerf client/server port."""
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:PORT?'.format(
            self._meas_id, self._mode.value, self._service_id)
        return int(self._cmw.send_and_recv(cmd))

    @port.setter
    def port(self, port):
        """Gets the IPerf client/server port.

        Args:
            port: the port number to use
        """
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:PORT {}'.format(
            self._meas_id, self._mode.value, self._service_id, port)
        self._cmw.send_and_recv(cmd)

    @property
    def parallel_connections(self):
        """Gets the number of parallel connections (TCP only)"""
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:PCONnection?'.format(
            self._meas_id, self._mode.value, self._service_id)
        return int(self._cmw.send_and_recv(cmd))

    @parallel_connections.setter
    def parallel_connections(self, parallel_count):
        """Sets the number of parallel connections (TCP only).

        Args:
            parallel_count: number of parallel connections to use
        """
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:PCONnection {}'.format(
            self._meas_id, self._mode.value, self._service_id, parallel_count)
        self._cmw.send_and_recv(cmd)

    @property
    def window_size(self):
        """Gets the IPerf window size."""
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:SBSize?'.format(
            self._meas_id, self._mode.value, self._service_id)
        return int(self._cmw.send_and_recv(cmd))

    @window_size.setter
    def window_size(self, size):
        """Sets the IPerf window size.

        Args:
            size: window size in kB
        """
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:SBSize {}'.format(
            self._meas_id, self._mode.value, self._service_id, size)
        self._cmw.send_and_recv(cmd)

    @property
    def max_bitrate(self):
        """Gets the maximum bitrate (UDP client only)."""
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:BITRate?'.format(
            self._meas_id, self._mode.value, self._service_id)
        return float(self._cmw.send_and_recv(cmd))

    @max_bitrate.setter
    def max_bitrate(self, bitrate):
        """Sets the maximum bitrate (UDP client only).

        Args:
            bitrate: the maximum bitrate in bps
        """
        cmd = 'CONFigure:DATA:MEAS{}:IPERf:{}{}:BITRate {}'.format(
            self._meas_id, self._mode.value, self._service_id, bitrate)
        self._cmw.send_and_recv(cmd)

    def _query_results(self):
        """Queries results for all IPerf services.

        Returns: a flat list of strings in the format:
            [reliability,
             server1_count, client1_count, server1_throughput, server1_loss,
             client1_throughput
             ...,
             server8_count, server8_throughput, server8_loss, client8_throughput
            ]

        Note:
            - The reliability field is not used
            - Missing values are set to "NAV" and their "count" will be 0

        Examples::

            Results from a single client on sample 5:
                ["0",
                 "0","5","NAV","NAV","2.791470E+08",
                 "0","0","NAV","NAV","NAV",
                 "0","0","NAV","NAV","NAV",
                 "0","0","NAV","NAV","NAV",
                 "0","0","NAV","NAV","NAV",
                 "0","0","NAV","NAV","NAV",
                 "0","0","NAV","NAV","NAV",
                 "0","0","NAV","NAV","NAV",
                ]
        """
        return self._cmw.send_and_recv('FETCh:DATA:MEAS{}:IPERf:ALL?'.format(
            self._service_id)).split(',')

    @property
    def count(self):
        """Gets the result sample ID or None if no samples are available.

        Note: CMW500 does not return all IPerf results, it only returns
        the most recent result. Results are differentiated with different "IDs"
        which is just the sample count number.
        """
        results = self._query_results()
        if self._mode == IPerfMode.CLIENT:
            index = (self._service_id -
                     1) * _MEASUREMENT_SIZE + _CLIENT_COUNT_INDEX
        else:
            index = (self._service_id -
                     1) * _MEASUREMENT_SIZE + _SERVER_COUNT_INDEX
        return _try_parse(results[index], int)

    @property
    def throughput(self):
        """Gets the current throughput, or None if no samples are available.

        Returns:
            The maximum throughput in bps.
        """
        results = self._query_results()
        if self._mode == IPerfMode.CLIENT:
            index = (self._service_id -
                     1) * _MEASUREMENT_SIZE + _CLIENT_THROUGHPUT_INDEX
        else:
            index = (self._service_id -
                     1) * _MEASUREMENT_SIZE + _SERVER_THROUGHPUT_INDEX
        return _try_parse(results[index], float)

    @property
    def loss(self):
        """Gets the current loss rate, or None if no samples are available.

        Note: Only applicable for UDP servers, otherwise will be 0.

        Returns:
            The loss rate in %.
        """
        results = self._query_results()
        if self._mode == IPerfMode.CLIENT:
            raise CmwIPerfError(
                'Loss is not available on IPerf Client measurements.')
        else:
            index = (self._service_id -
                     1) * _MEASUREMENT_SIZE + _SERVER_LOSS_INDEX
        return _try_parse(results[index], float)


class CmwIPerfError(Exception):
    """Class to raise exceptions related to cmx IPerf measurements."""
