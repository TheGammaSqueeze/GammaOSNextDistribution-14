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

import logging
import multiprocessing
import time

from datetime import datetime
from uuid import uuid4

from acts import signals
from acts import tracelogger
from acts import utils
from acts.controllers import iperf_client
from acts.controllers import iperf_server

AC_VO = 'AC_VO'
AC_VI = 'AC_VI'
AC_BE = 'AC_BE'
AC_BK = 'AC_BK'

# TODO(fxb/61421): Add tests to check all DSCP classes are mapped to the correct
# AC (there are many that aren't included here). Requires implementation of
# sniffer.
DEFAULT_AC_TO_TOS_TAG_MAP = {
    AC_VO: '0xC0',
    AC_VI: '0x80',
    AC_BE: '0x0',
    AC_BK: '0x20'
}
UDP = 'udp'
TCP = 'tcp'
DEFAULT_IPERF_PORT = 5201
DEFAULT_STREAM_TIME = 10
DEFAULT_IP_ADDR_TIMEOUT = 15
PROCESS_JOIN_TIMEOUT = 60
AVAILABLE = True
UNAVAILABLE = False


class WmmTransceiverError(signals.ControllerError):
    pass


def create(config, identifier=None, wlan_devices=None, access_points=None):
    """Creates a WmmTransceiver from a config.

    Args:
        config: dict, config parameters for the transceiver. Contains:
            - iperf_config: dict, the config to use for creating IPerfClients
                and IPerfServers (excluding port).
            - port_range_start: int, the lower bound of the port range to use
                for creating IPerfServers. Defaults to 5201.
            - wlan_device: string, the identifier of the wlan_device used for
                this WmmTransceiver (optional)

        identifier: string, identifier for the WmmTransceiver. Must be provided
            either as arg or in the config.
        wlan_devices: list of WlanDevice objects from which to get the
            wlan_device, if any, used as this transceiver
        access_points: list of AccessPoint objects from which to get the
            access_point, if any, used as this transceiver
    """
    try:
        # If identifier is not provided as func arg, it must be provided via
        # config file.
        if not identifier:
            identifier = config['identifier']
        iperf_config = config['iperf_config']

    except KeyError as err:
        raise WmmTransceiverError(
            'Parameter not provided as func arg, nor found in config: %s' %
            err)

    if wlan_devices is None:
        wlan_devices = []

    if access_points is None:
        access_points = []

    port_range_start = config.get('port_range_start', DEFAULT_IPERF_PORT)

    wd = None
    ap = None
    if 'wlan_device' in config:
        wd = _find_wlan_device(config['wlan_device'], wlan_devices)
    elif 'access_point' in config:
        ap = _find_access_point(config['access_point'], access_points)

    return WmmTransceiver(iperf_config,
                          identifier,
                          wlan_device=wd,
                          access_point=ap,
                          port_range_start=port_range_start)


def _find_wlan_device(wlan_device_identifier, wlan_devices):
    """Returns WlanDevice based on string identifier (e.g. ip, serial, etc.)

    Args:
        wlan_device_identifier: string, identifier for the desired WlanDevice
        wlan_devices: list, WlanDevices to search through

    Returns:
        WlanDevice, with identifier matching wlan_device_identifier

    Raises:
        WmmTransceiverError, if no WlanDevice matches identifier
    """
    for wd in wlan_devices:
        if wlan_device_identifier == wd.identifier:
            return wd
    raise WmmTransceiverError('No WlanDevice with identifier: %s' %
                              wlan_device_identifier)


def _find_access_point(access_point_ip, access_points):
    """Returns AccessPoint based on string ip address

    Args:
        access_point_ip: string, control plane ip addr of the desired AP,
        access_points: list, AccessPoints to search through

    Returns:
        AccessPoint, with hostname matching access_point_ip

    Raises:
        WmmTransceiverError, if no AccessPoint matches ip"""
    for ap in access_points:
        if ap.ssh_settings.hostname == access_point_ip:
            return ap
    raise WmmTransceiverError('No AccessPoint with ip: %s' % access_point_ip)


class WmmTransceiver(object):
    """Object for handling WMM tagged streams between devices"""
    def __init__(self,
                 iperf_config,
                 identifier,
                 wlan_device=None,
                 access_point=None,
                 port_range_start=5201):

        self.identifier = identifier
        self.log = tracelogger.TraceLogger(
            WmmTransceiverLoggerAdapter(logging.getLogger(),
                                        {'identifier': self.identifier}))
        # WlanDevice or AccessPoint, that is used as the transceiver. Only one
        # will be set. This helps consolodate association, setup, teardown, etc.
        self.wlan_device = wlan_device
        self.access_point = access_point

        # Parameters used to create IPerfClient and IPerfServer objects on
        # device
        self._iperf_config = iperf_config
        self._test_interface = self._iperf_config.get('test_interface')
        self._port_range_start = port_range_start
        self._next_server_port = port_range_start

        # Maps IPerfClients, used for streams from this device, to True if
        # available, False if reserved
        self._iperf_clients = {}

        # Maps IPerfServers, used to receive streams from other devices, to True
        # if available, False if reserved
        self._iperf_servers = {}

        # Maps ports of servers, which are provided to other transceivers, to
        # the actual IPerfServer objects
        self._iperf_server_ports = {}

        # Maps stream UUIDs to IPerfClients reserved for that streams use
        self._reserved_clients = {}

        # Maps stream UUIDs to (WmmTransceiver, IPerfServer) tuples, where the
        # server is reserved on the transceiver for that streams use
        self._reserved_servers = {}

        # Maps with shared memory functionality to be used across the parallel
        # streams. active_streams holds UUIDs of streams that are currently
        # running on this device (mapped to True, since there is no
        # multiprocessing set). stream_results maps UUIDs of streams completed
        # on this device to IPerfResult results for that stream.
        self._manager = multiprocessing.Manager()
        self._active_streams = self._manager.dict()
        self._stream_results = self._manager.dict()

        # Holds parameters for streams that are prepared to run asynchronously
        # (i.e. resources have been allocated). Maps UUIDs of the future streams
        # to a dict, containing the stream parameters.
        self._pending_async_streams = {}

        # Set of UUIDs of asynchronous streams that have at least started, but
        # have not had their resources reclaimed yet
        self._ran_async_streams = set()

        # Set of stream parallel process, which can be joined if completed
        # successfully, or  terminated and joined in the event of an error
        self._running_processes = set()

    def run_synchronous_traffic_stream(self, stream_parameters, subnet):
        """Runs a traffic stream with IPerf3 between two WmmTransceivers and
        saves the results.

        Args:
            stream_parameters: dict, containing parameters to used for the
                stream. See _parse_stream_parameters for details.
            subnet: string, the subnet of the network to use for the stream

        Returns:
            uuid: UUID object, identifier of the stream
        """
        (receiver, access_category, bandwidth,
         stream_time) = self._parse_stream_parameters(stream_parameters)
        uuid = uuid4()

        (client, server_ip,
         server_port) = self._get_stream_resources(uuid, receiver, subnet)

        self._validate_server_address(server_ip, uuid)

        self.log.info('Running synchronous stream to %s WmmTransceiver' %
                      receiver.identifier)
        self._run_traffic(uuid,
                          client,
                          server_ip,
                          server_port,
                          self._active_streams,
                          self._stream_results,
                          access_category=access_category,
                          bandwidth=bandwidth,
                          stream_time=stream_time)

        self._return_stream_resources(uuid)
        return uuid

    def prepare_asynchronous_stream(self, stream_parameters, subnet):
        """Reserves resources and saves configs for upcoming asynchronous
        traffic streams, so they can be started more simultaneously.

        Args:
            stream_parameters: dict, containing parameters to used for the
                stream. See _parse_stream_parameters for details.
            subnet: string, the subnet of the network to use for the stream

        Returns:
            uuid: UUID object, identifier of the stream
        """
        (receiver, access_category, bandwidth,
         time) = self._parse_stream_parameters(stream_parameters)
        uuid = uuid4()

        (client, server_ip,
         server_port) = self._get_stream_resources(uuid, receiver, subnet)

        self._validate_server_address(server_ip, uuid)

        pending_stream_config = {
            'client': client,
            'server_ip': server_ip,
            'server_port': server_port,
            'access_category': access_category,
            'bandwidth': bandwidth,
            'time': time
        }

        self._pending_async_streams[uuid] = pending_stream_config
        self.log.info('Stream to %s WmmTransceiver prepared.' %
                      receiver.identifier)
        return uuid

    def start_asynchronous_streams(self, start_time=None):
        """Starts pending asynchronous streams between two WmmTransceivers as
        parallel processes.

        Args:
            start_time: float, time, seconds since epoch, at which to start the
                stream (for better synchronicity). If None, start immediately.
        """
        for uuid in self._pending_async_streams:
            pending_stream_config = self._pending_async_streams[uuid]
            client = pending_stream_config['client']
            server_ip = pending_stream_config['server_ip']
            server_port = pending_stream_config['server_port']
            access_category = pending_stream_config['access_category']
            bandwidth = pending_stream_config['bandwidth']
            time = pending_stream_config['time']

            process = multiprocessing.Process(target=self._run_traffic,
                                              args=[
                                                  uuid, client, server_ip,
                                                  server_port,
                                                  self._active_streams,
                                                  self._stream_results
                                              ],
                                              kwargs={
                                                  'access_category':
                                                  access_category,
                                                  'bandwidth': bandwidth,
                                                  'stream_time': time,
                                                  'start_time': start_time
                                              })

            # This needs to be set here to ensure its marked active before
            # it even starts.
            self._active_streams[uuid] = True
            process.start()
            self._ran_async_streams.add(uuid)
            self._running_processes.add(process)

        self._pending_async_streams.clear()

    def cleanup_asynchronous_streams(self, timeout=PROCESS_JOIN_TIMEOUT):
        """Releases reservations on resources (IPerfClients and IPerfServers)
        that were held for asynchronous streams, both pending and finished.
        Attempts to join any running processes, logging an error if timeout is
        exceeded.

        Args:
            timeout: time, in seconds, to wait for each running process, if any,
                to join
        """
        self.log.info('Cleaning up any asynchronous streams.')

        # Releases resources for any streams that were prepared, but no run
        for uuid in self._pending_async_streams:
            self.log.error(
                'Pending asynchronous stream %s never ran. Cleaning.' % uuid)
            self._return_stream_resources(uuid)
        self._pending_async_streams.clear()

        # Attempts to join any running streams, terminating them after timeout
        # if necessary.
        while self._running_processes:
            process = self._running_processes.pop()
            process.join(timeout)
            if process.is_alive():
                self.log.error(
                    'Stream process failed to join in %s seconds. Terminating.'
                    % timeout)
                process.terminate()
                process.join()
        self._active_streams.clear()

        # Release resources for any finished streams
        while self._ran_async_streams:
            uuid = self._ran_async_streams.pop()
            self._return_stream_resources(uuid)

    def get_results(self, uuid):
        """Retrieves a streams IPerfResults from stream_results

        Args:
            uuid: UUID object, identifier of the stream
        """
        return self._stream_results.get(uuid, None)

    def destroy_resources(self):
        for server in self._iperf_servers:
            server.stop()
        self._iperf_servers.clear()
        self._iperf_server_ports.clear()
        self._iperf_clients.clear()
        self._next_server_port = self._port_range_start
        self._stream_results.clear()

    @property
    def has_active_streams(self):
        return bool(self._active_streams)

    # Helper Functions

    def _run_traffic(self,
                     uuid,
                     client,
                     server_ip,
                     server_port,
                     active_streams,
                     stream_results,
                     access_category=None,
                     bandwidth=None,
                     stream_time=DEFAULT_STREAM_TIME,
                     start_time=None):
        """Runs an iperf3 stream.

        1. Adds stream UUID to active_streams
        2. Runs stream
        3. Saves results to stream_results
        4. Removes stream UUID from active_streams

        Args:
            uuid: UUID object, identifier for stream
            client: IPerfClient object on device
            server_ip: string, ip address of IPerfServer for stream
            server_port: int, port of the IPerfServer for stream
            active_streams: multiprocessing.Manager.dict, which holds stream
                UUIDs of active streams on the device
            stream_results: multiprocessing.Manager.dict, which maps stream
                UUIDs of streams to IPerfResult objects
            access_category: string, WMM access category to use with iperf
                (AC_BK, AC_BE, AC_VI, AC_VO). Unset if None.
            bandwidth: int, bandwidth in mbps to use with iperf. Implies UDP.
                Unlimited if None.
            stream_time: int, time in seconds, to run iperf stream
            start_time: float, time, seconds since epoch, at which to start the
                stream (for better synchronicity). If None, start immediately.
        """
        active_streams[uuid] = True
        # SSH sessions must be started within the process that is going to
        # use it.
        if type(client) == iperf_client.IPerfClientOverSsh:
            with utils.SuppressLogOutput():
                client.start_ssh()

        ac_flag = ''
        bandwidth_flag = ''
        time_flag = '-t %s' % stream_time

        if access_category:
            ac_flag = ' -S %s' % DEFAULT_AC_TO_TOS_TAG_MAP[access_category]

        if bandwidth:
            bandwidth_flag = ' -u -b %sM' % bandwidth

        iperf_flags = '-p %s -i 1 %s%s%s -J' % (server_port, time_flag,
                                                ac_flag, bandwidth_flag)
        if not start_time:
            start_time = time.time()
        time_str = datetime.fromtimestamp(start_time).strftime('%H:%M:%S.%f')
        self.log.info(
            'At %s, starting %s second stream to %s:%s with (AC: %s, Bandwidth: %s)'
            % (time_str, stream_time, server_ip, server_port, access_category,
               bandwidth if bandwidth else 'Unlimited'))

        # If present, wait for stream start time
        if start_time:
            current_time = time.time()
            while current_time < start_time:
                current_time = time.time()
        path = client.start(server_ip, iperf_flags, '%s' % uuid)
        stream_results[uuid] = iperf_server.IPerfResult(
            path, reporting_speed_units='mbps')

        if type(client) == iperf_client.IPerfClientOverSsh:
            client.close_ssh()
        active_streams.pop(uuid)

    def _get_stream_resources(self, uuid, receiver, subnet):
        """Reserves an IPerfClient and IPerfServer for a stream.

        Args:
            uuid: UUID object, identifier of the stream
            receiver: WmmTransceiver object, which will be the streams receiver
            subnet: string, subnet of test network, to retrieve the appropriate
                server address

        Returns:
            (IPerfClient, string, int) representing the client, server address,
            and server port to use for the stream
        """
        client = self._get_client(uuid)
        server_ip, server_port = self._get_server(receiver, uuid, subnet)
        return (client, server_ip, server_port)

    def _return_stream_resources(self, uuid):
        """Releases reservations on a streams IPerfClient and IPerfServer, so
        they can be used by a future stream.

        Args:
            uuid: UUID object, identifier of the stream
        """
        if uuid in self._active_streams:
            raise EnvironmentError('Resource still being used by stream %s' %
                                   uuid)
        (receiver, server_port) = self._reserved_servers.pop(uuid)
        receiver._release_server(server_port)
        client = self._reserved_clients.pop(uuid)
        self._iperf_clients[client] = AVAILABLE

    def _get_client(self, uuid):
        """Retrieves and reserves IPerfClient for use in a stream. If none are
        available, a new one is created.

        Args:
            uuid: UUID object, identifier for stream, used to link client to
                stream for teardown

        Returns:
            IPerfClient on device
        """
        reserved_client = None
        for client in self._iperf_clients:
            if self._iperf_clients[client] == AVAILABLE:
                reserved_client = client
                break
        else:
            reserved_client = iperf_client.create([self._iperf_config])[0]
            # Due to the nature of multiprocessing, ssh connections must
            # be started inside the parallel processes, so it must be closed
            # here.
            if type(reserved_client) == iperf_client.IPerfClientOverSsh:
                reserved_client.close_ssh()

        self._iperf_clients[reserved_client] = UNAVAILABLE
        self._reserved_clients[uuid] = reserved_client
        return reserved_client

    def _get_server(self, receiver, uuid, subnet):
        """Retrieves the address and port of a reserved IPerfServer object from
        the receiver object for use in a stream.

        Args:
            receiver: WmmTransceiver, to get an IPerfServer from
            uuid: UUID, identifier for stream, used to link server to stream
                for teardown
            subnet: string, subnet of test network, to retrieve the appropriate
                server address

        Returns:
            (string, int) representing the IPerfServer address and port
        """
        (server_ip, server_port) = receiver._reserve_server(subnet)
        self._reserved_servers[uuid] = (receiver, server_port)
        return (server_ip, server_port)

    def _reserve_server(self, subnet):
        """Reserves an available IPerfServer for use in a stream from another
        WmmTransceiver. If none are available, a new one is created.

        Args:
            subnet: string, subnet of test network, to retrieve the appropriate
                server address

        Returns:
            (string, int) representing the IPerfServer address and port
        """
        reserved_server = None
        for server in self._iperf_servers:
            if self._iperf_servers[server] == AVAILABLE:
                reserved_server = server
                break
        else:
            iperf_server_config = self._iperf_config
            iperf_server_config.update({'port': self._next_server_port})
            self._next_server_port += 1
            reserved_server = iperf_server.create([iperf_server_config])[0]
            self._iperf_server_ports[reserved_server.port] = reserved_server

        self._iperf_servers[reserved_server] = UNAVAILABLE
        reserved_server.start()
        end_time = time.time() + DEFAULT_IP_ADDR_TIMEOUT
        while time.time() < end_time:
            if self.wlan_device:
                addresses = utils.get_interface_ip_addresses(
                    self.wlan_device.device, self._test_interface)
            else:
                addresses = reserved_server.get_interface_ip_addresses(
                    self._test_interface)
            for addr in addresses['ipv4_private']:
                if utils.ip_in_subnet(addr, subnet):
                    return (addr, reserved_server.port)
        raise AttributeError(
            'Reserved server has no ipv4 address in the %s subnet' % subnet)

    def _release_server(self, server_port):
        """Releases reservation on IPerfServer, which was held for a stream
        from another WmmTransceiver.

        Args:
            server_port: int, the port of the IPerfServer being returned (since)
                it is the identifying characteristic
        """
        server = self._iperf_server_ports[server_port]
        server.stop()
        self._iperf_servers[server] = AVAILABLE

    def _validate_server_address(self, server_ip, uuid, timeout=60):
        """ Verifies server address can be pinged before attempting to run
        traffic, since iperf is unforgiving when the server is unreachable.

        Args:
            server_ip: string, ip address of the iperf server
            uuid: string, uuid of the stream to use this server
            timeout: int, time in seconds to wait for server to respond to pings

        Raises:
            WmmTransceiverError, if, after timeout, server ip is unreachable.
        """
        self.log.info('Verifying server address (%s) is reachable.' %
                      server_ip)
        end_time = time.time() + timeout
        while time.time() < end_time:
            if self.can_ping(server_ip):
                break
            else:
                self.log.debug(
                    'Could not ping server address (%s). Retrying in 1 second.'
                    % (server_ip))
                time.sleep(1)
        else:
            self._return_stream_resources(uuid)
            raise WmmTransceiverError('IPerfServer address (%s) unreachable.' %
                                      server_ip)

    def can_ping(self, dest_ip):
        """ Utilizes can_ping function in wlan_device or access_point device to
        ping dest_ip

        Args:
            dest_ip: string, ip address to ping

        Returns:
            True, if dest address is reachable
            False, otherwise
        """
        if self.wlan_device:
            return self.wlan_device.can_ping(dest_ip)
        else:
            return self.access_point.can_ping(dest_ip)

    def _parse_stream_parameters(self, stream_parameters):
        """Parses stream_parameters from dictionary.

        Args:
            stream_parameters: dict of stream parameters
                'receiver': WmmTransceiver, the receiver for the stream
                'access_category': String, the access category to use for the
                    stream. Unset if None.
                'bandwidth': int, bandwidth in mbps for the stream. If set,
                    implies UDP. If unset, implies TCP and unlimited bandwidth.
                'time': int, time in seconds to run stream.

        Returns:
            (receiver, access_category, bandwidth, time) as
            (WmmTransceiver, String, int, int)
        """
        receiver = stream_parameters['receiver']
        access_category = stream_parameters.get('access_category', None)
        bandwidth = stream_parameters.get('bandwidth', None)
        time = stream_parameters.get('time', DEFAULT_STREAM_TIME)
        return (receiver, access_category, bandwidth, time)


class WmmTransceiverLoggerAdapter(logging.LoggerAdapter):
    def process(self, msg, kwargs):
        if self.extra['identifier']:
            log_identifier = ' | %s' % self.extra['identifier']
        else:
            log_identifier = ''
        msg = "[WmmTransceiver%s] %s" % (log_identifier, msg)
        return (msg, kwargs)
