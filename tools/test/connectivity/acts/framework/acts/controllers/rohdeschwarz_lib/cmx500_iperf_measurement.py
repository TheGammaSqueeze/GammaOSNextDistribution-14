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
"""Provides classes for managing IPerf sessions on CMX500 callboxes."""

from enum import Enum
from acts import logger


class IPerfType(Enum):
    """Supported performance measurement types."""

    IPERF = "IPERF"
    IPERF3 = "IPERF3"
    NAT = "NAT"


class IPerfProtocol(Enum):
    """Supported IPerf protocol types."""

    TCP = "TCP"
    UDP = "UDP"


def delete_all_iperfs():
    from xlapi import meas
    meas.delete_all_iperfs()


class Cmx500IPerfMeasurement(object):

    DEFAULT_TEST_TIME = 30
    """Class for managing IPerf measurement sessions on CMX500."""

    def __init__(self):
        """Initializes a new CMW500 Iperf Measurement.

        Examples::

            Using just a single client:
                measurement = Cmx500IPerfMeasurement()
                measurement.configure_services(0, 1)
                measurement.test_time = 10
                ...
                measurement.start()
                time.sleep(10)
                client = measurement.clients[0]
                for result in client.all_results:
                    print(f"took measurement: {result}")
        """

        self.clients = []
        self.servers = []

        # Unlike with CMW500s, test_time is set per-service, keep track of it
        # in the measurement and just apply to all services at start to keep
        # things consistent.
        self.test_time = self.DEFAULT_TEST_TIME

    def configure_services(self, server_count, client_count):
        """Configures the IPerf services.

        Args:
            server_count: the number of servers to initialize.
            client_count: the number of clients to initialize.
        """
        from xlapi.meas import IPerfMode

        # If configuration matches, don't do anything.
        if len(self.clients) == client_count and len(
                self.servers) == server_count:
            return

        self.close()
        self.servers = [
            Cmx500IPerfService(IPerfMode.SERVER) for _ in range(server_count)
        ]
        self.clients = [
            Cmx500IPerfService(IPerfMode.CLIENT) for _ in range(client_count)
        ]

    def start(self):
        """Starts the performance measurement on the callbox."""
        from mrtype import Time
        test_time = Time.s(self.test_time)
        for service in self.servers + self.clients:
            service.start_single_shot(test_time)

    def stop(self):
        """Halts the measurement."""
        for service in self.clients + self.servers:
            service.stop()

    def close(self):
        """Halts the measurement and releases all resources."""
        self.stop()
        for service in self.clients + self.servers:
            service.close()

        self.clients.clear()
        self.servers.clear()

    @property
    def ipv4_address(self):
        """Gets the current DAU IPv4 address."""
        from xlapi import platform_manager

        data_service = platform_manager.get_default().mrt_data_service_stub
        addresses = data_service.GetStaticDauIpV4Addresses()
        if not addresses:
            raise CmxIPerfError('No DAU IP address available')
        # DAU can have multiple addresses, always use first one.
        return addresses[0].value

    @property
    def test_time(self):
        """Gets the duration of the IPerf measurement (in s)."""
        return self._time

    @test_time.setter
    def test_time(self, duration):
        """Sets the performance test duration.

        Args:
            duration: the length of the IPerf measurement (in s).
        """
        self._time = duration


class Cmx500IPerfService(object):
    """Class for controlling a single IPerf measurement instance."""

    def __init__(self, mode):
        from xlapi import meas

        self.logger = logger.create_logger()
        self._perf = meas.create_iperf()
        self._perf.mode = mode
        self._init_xlapi()

    def _init_xlapi(self):
        """Initialize xlapi types."""
        from mrtype import DataSize, DataRate
        self._data_size = DataSize
        self._data_rate = DataRate

    def start_single_shot(self, time):
        """Starts the IPerf client/server."""
        self._perf.start_single_shot(time)

    def stop(self):
        """Waits for current measurement session to finish."""
        if self._perf.is_running():
            self._perf.stop()

    def close(self):
        """Halts the measurement and releases all resources."""
        self.stop()
        self._perf.delete()

    @property
    def test_type(self):
        """Gets the type of IPerf application to use."""
        return self._perf.application

    @test_type.setter
    def test_type(self, mode):
        """Sets the type of IPerf application to use

        Args:
            mode: IPER/IP3
        """
        if not isinstance(mode, IPerfType):
            raise ValueError("mode should be the instance of IPerfType")

        from xlapi.meas import IPerfApplication

        if mode == IPerfType.IPERF:
            self._perf.application = IPerfApplication.IPERF
        elif mode == IPerfType.IPERF3:
            self._perf.application = IPerfApplication.IPERF3
        elif mode == IPerfType.NAT:
            self._perf.application = IPerfApplication.IPERF_NAT_
        else:
            raise CmxIPerfError(
                "Unsupported IPerf application: {}".format(mode))

    @property
    def protocol(self):
        """Gets the IPerf protocol."""
        return self._perf.protocol

    @protocol.setter
    def protocol(self, protocol):
        """Sets the IPerf protocol.

        Args:
            protocol: TCP/UDP
        """
        if not isinstance(protocol, IPerfProtocol):
            raise ValueError(
                "protocol should be the instance of IPerfProtocol")
        from xlapi.meas import IPerfProtocol as _IPerfProtocol

        if protocol == IPerfProtocol.TCP:
            self._perf.protocol = _IPerfProtocol.TCP
        else:
            self._perf.protocol = _IPerfProtocol.UDP

    @property
    def ip_address(self):
        """Gets the IPerf client/server port."""
        return self._perf.ip_address

    @ip_address.setter
    def ip_address(self, address):
        """Sets the service IP address (clients only).

        Args:
            address: ip address of the IPerf server
        """
        self._perf.ip_address = address

    @property
    def port(self):
        """Gets the IPerf client/server port."""
        return self._perf.port

    @port.setter
    def port(self, port):
        """Gets the IPerf client/server port.

        Args:
            port: the port number to use
        """
        self._perf.port = port

    @property
    def parallel_connections(self):
        """Gets the number of parallel connections (TCP only)"""
        return self._perf.parallel_connections

    @parallel_connections.setter
    def parallel_connections(self, parallel_count):
        """Sets the number of parallel connections (TCP only)

        Args:
            parallel_count: number of parallel connections to use
        """
        self._perf.parallel_connections = parallel_count

    @property
    def packet_size(self):
        """Gets the IPerf packet size."""
        return self._perf.packet_size.in_B()

    @packet_size.setter
    def packet_size(self, size):
        """Sets the IPerf packet size.

        Args:
            size: the packet size in B
        """
        self._perf.packet_size = self._data_size.B(size)

    @property
    def window_size(self):
        """Gets the IPerf window size."""
        return self._perf.tcp_window_size.in_kB()

    @window_size.setter
    def window_size(self, size):
        """Sets the IPerf window size.

        Args:
            size: the window size in kB
        """
        self._perf.tcp_window_size = self._data_size.kB(size)

    @property
    def max_bitrate(self):
        """Gets the maximum bitrate (UDP client only)."""
        return self._perf.bandwidth.in_bps()

    @max_bitrate.setter
    def max_bitrate(self, bitrate):
        """Sets the maximum bitrate (UDP client only).

        Args:
            bitrate: the maximum bitrate in bps
        """
        self._perf.bandwidth = self._data_rate.bps(bitrate)

    @property
    def all_results(self):
        """Gets all throughput results in bps.

        Returns:
            A list of chronological floating point throughput measurements.
        """
        from xlapi.meas import IPerfMode

        try:
            if self._perf.mode == IPerfMode.CLIENT:
                result = self._perf.result.dl.raw_data
            else:
                result = self._perf.result.ul.raw_data

            # xlapi returns results in reversed chronological order
            return [d.in_bps() for d in result][::-1]
        except Exception as e:
            # xlapi will raise an error if no results are available yet.
            self.logger.error("Failed to get results: {}".format(e))
            return []

    @property
    def count(self):
        """Gets the available result sample count."""
        results = self.all_results
        if results:
            return len(results)
        return 0

    @property
    def throughput(self):
        """Gets the most recent throughput or None if no data is available.

        Returns:
            The maximum throughput in bps.
        """
        results = self.all_results
        if results:
            return results[-1]
        return None


class CmxIPerfError(Exception):
    """Class to raise exceptions related to cmx IPerf measurements."""
