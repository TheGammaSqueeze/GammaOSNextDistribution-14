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

from mobly import signals
import multiprocessing as mp
import random
import time

from acts import utils
from acts import asserts
from acts.controllers import iperf_server
from acts.controllers import iperf_client
from acts.controllers.access_point import setup_ap, AccessPoint
from acts.controllers.ap_lib import hostapd_constants
from acts.controllers.ap_lib import hostapd_security
from acts.controllers.ap_lib.hostapd_utils import generate_random_password
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device

CONNECTIVITY_MODE_LOCAL = 'local_only'
CONNECTIVITY_MODE_UNRESTRICTED = 'unrestricted'
DEFAULT_AP_PROFILE = 'whirlwind'
DEFAULT_IPERF_PORT = 5201
DEFAULT_STRESS_TEST_ITERATIONS = 10
DEFAULT_TIMEOUT = 30
DEFAULT_IPERF_TIMEOUT = 60
DEFAULT_NO_ADDR_EXPECTED_TIMEOUT = 5
INTERFACE_ROLE_AP = 'Ap'
INTERFACE_ROLE_CLIENT = 'Client'
OPERATING_BAND_2G = 'only_2_4_ghz'
OPERATING_BAND_5G = 'only_5_ghz'
OPERATING_BAND_ANY = 'any'
SECURITY_OPEN = 'none'
SECURITY_WEP = 'wep'
SECURITY_WPA = 'wpa'
SECURITY_WPA2 = 'wpa2'
SECURITY_WPA3 = 'wpa3'
STATE_UP = True
STATE_DOWN = False
TEST_TYPE_ASSOCIATE_ONLY = 'associate_only'
TEST_TYPE_ASSOCIATE_AND_PING = 'associate_and_ping'
TEST_TYPE_ASSOCIATE_AND_PASS_TRAFFIC = 'associate_and_pass_traffic'
TEST_TYPES = {
    TEST_TYPE_ASSOCIATE_ONLY, TEST_TYPE_ASSOCIATE_AND_PING,
    TEST_TYPE_ASSOCIATE_AND_PASS_TRAFFIC
}


def get_test_name_from_settings(settings):
    return settings['test_name']


def get_ap_params_from_config_or_default(config):
    """Retrieves AP parameters from ACTS config, or returns default settings.

    Args:
        config: dict, from ACTS config, that may contain custom ap parameters

    Returns:
        dict, containing all AP parameters
    """
    profile = config.get('profile', DEFAULT_AP_PROFILE)
    ssid = config.get(
        'ssid', utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G))
    channel = config.get('channel', hostapd_constants.AP_DEFAULT_CHANNEL_2G)
    security_mode = config.get('security_mode', None)
    password = config.get('password', None)
    if security_mode:
        if not password:
            password = generate_random_password(security_mode=security_mode)
        security = hostapd_security.Security(security_mode, password)
    else:
        security = None

    return {
        'profile': profile,
        'ssid': ssid,
        'channel': channel,
        'security': security,
        'password': password
    }


def get_soft_ap_params_from_config_or_default(config):
    """Retrieves SoftAp parameters from ACTS config or returns default settings.

    Args:
        config: dict, from ACTS config, that may contain custom soft ap
            parameters

    Returns:
        dict, containing all soft AP parameters
    """
    ssid = config.get(
        'ssid', utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G))
    connectivity_mode = config.get('connectivity_mode',
                                   CONNECTIVITY_MODE_LOCAL)
    operating_band = config.get('operating_band', OPERATING_BAND_2G)
    security_type = config.get('security_type', SECURITY_OPEN)
    password = config.get('password', '')

    # The SoftAP API uses 'open' security instead of None, '' password
    # instead of None, and security_type instead of security_mode, hence
    # the difference between ap_params and soft_ap_params
    if security_type != SECURITY_OPEN and password == '':
        password = generate_random_password(security_mode=security_type)

    return {
        'ssid': ssid,
        'connectivity_mode': connectivity_mode,
        'operating_band': operating_band,
        'security_type': security_type,
        'password': password
    }


class StressTestIterationFailure(Exception):
    """Used to differentiate a subtest failure from an actual exception"""


class SoftApTest(WifiBaseTest):
    """Tests for Fuchsia SoftAP

    Testbed requirement:
    * One Fuchsia device
    * At least one client (Android) device
        * For multi-client tests, at least two client (Android) devices are
          required. Test will be skipped if less than two client devices are
          present.
    * For any tests that exercise client-mode (e.g. toggle tests, simultaneous
        tests), a physical AP (whirlwind) is also required. Those tests will be
        skipped if physical AP is not present.
    """

    def setup_class(self):
        self.soft_ap_test_params = self.user_params.get(
            'soft_ap_test_params', {})
        self.dut = create_wlan_device(self.fuchsia_devices[0])

        # TODO(fxb/51313): Add in device agnosticity for clients
        # Create a wlan device and iperf client for each Android client
        self.clients = []
        self.iperf_clients_map = {}
        for device in self.android_devices:
            client_wlan_device = create_wlan_device(device)
            self.clients.append(client_wlan_device)
            self.iperf_clients_map[
                client_wlan_device] = client_wlan_device.create_iperf_client()
        self.primary_client = self.clients[0]

        # Create an iperf server on the DUT, which will be used for any streaming.
        self.iperf_server_config = {
            'user': self.dut.device.ssh_username,
            'host': self.dut.device.ip,
            'ssh_config': self.dut.device.ssh_config
        }
        self.iperf_server = iperf_server.IPerfServerOverSsh(
            self.iperf_server_config, DEFAULT_IPERF_PORT, use_killall=True)
        self.iperf_server.start()

        # Attempt to create an ap iperf server. AP is only required for tests
        # that use client mode.
        try:
            self.access_point = self.access_points[0]
            self.ap_iperf_client = iperf_client.IPerfClientOverSsh(
                self.user_params['AccessPoint'][0]['ssh_config'])
        except AttributeError:
            self.access_point = None
            self.ap_iperf_client = None

        self.iperf_clients_map[self.access_point] = self.ap_iperf_client

    def teardown_class(self):
        # Because this is using killall, it will stop all iperf processes
        self.iperf_server.stop()

    def setup_test(self):
        for ad in self.android_devices:
            ad.droid.wakeLockAcquireBright()
            ad.droid.wakeUpNow()
        for client in self.clients:
            client.disconnect()
            client.reset_wifi()
            client.wifi_toggle_state(True)
        self.stop_all_soft_aps()
        if self.access_point:
            self.access_point.stop_all_aps()
        self.dut.disconnect()

    def teardown_test(self):
        for client in self.clients:
            client.disconnect()
        for ad in self.android_devices:
            ad.droid.wakeLockRelease()
            ad.droid.goToSleepNow()
        self.stop_all_soft_aps()
        if self.access_point:
            self.download_ap_logs()
            self.access_point.stop_all_aps()
        self.dut.disconnect()

    def start_soft_ap(self, settings):
        """Starts a softAP on Fuchsia device.

        Args:
            settings: a dict containing softAP configuration params
                ssid: string, SSID of softAP network
                security_type: string, security type of softAP network
                    - 'none', 'wep', 'wpa', 'wpa2', 'wpa3'
                password: string, password if applicable
                connectivity_mode: string, connecitivity_mode for softAP
                    - 'local_only', 'unrestricted'
                operating_band: string, band for softAP network
                    - 'any', 'only_5_ghz', 'only_2_4_ghz'
        """
        ssid = settings['ssid']
        security_type = settings['security_type']
        password = settings.get('password', '')
        connectivity_mode = settings['connectivity_mode']
        operating_band = settings['operating_band']

        self.log.info('Starting SoftAP on DUT with settings: %s' % settings)

        response = self.dut.device.sl4f.wlan_ap_policy_lib.wlanStartAccessPoint(
            ssid, security_type, password, connectivity_mode, operating_band)
        if response.get('error'):
            raise EnvironmentError('SL4F: Failed to setup SoftAP. Err: %s' %
                                   response['error'])

        self.log.info('SoftAp network (%s) is up.' % ssid)

    def stop_soft_ap(self, settings):
        """ Stops a specific SoftAP On Fuchsia device.

        Args:
            settings: a dict containing softAP config params (see start_soft_ap)
                for details

        Raises:
            EnvironmentError, if StopSoftAP call fails.
        """
        ssid = settings['ssid']
        security_type = settings['security_type']
        password = settings.get('password', '')

        response = self.dut.device.sl4f.wlan_ap_policy_lib.wlanStopAccessPoint(
            ssid, security_type, password)
        if response.get('error'):
            raise EnvironmentError('SL4F: Failed to stop SoftAP. Err: %s' %
                                   response['error'])

    def stop_all_soft_aps(self):
        """ Stops all SoftAPs on Fuchsia Device.

        Raises:
            EnvironmentError, if StopAllAps call fails.
        """
        response = self.dut.device.sl4f.wlan_ap_policy_lib.wlanStopAllAccessPoint(
        )
        if response.get('error'):
            raise EnvironmentError(
                'SL4F: Failed to stop all SoftAPs. Err: %s' %
                response['error'])

    def associate_with_soft_ap(self, device, soft_ap_settings):
        """Associates client device with softAP on Fuchsia device.

        Args:
            device: wlan_device to associate with the softAP
            settings: a dict containing softAP config params (see start_soft_ap)
                for details

        Raises:
            TestFailure, if association fails
        """
        self.log.info(
            'Attempting to associate client %s with SoftAP on FuchsiaDevice '
            '(%s).' % (device.identifier, self.dut.identifier))

        check_connectivity = soft_ap_settings[
            'connectivity_mode'] == CONNECTIVITY_MODE_UNRESTRICTED
        associated = device.associate(
            soft_ap_settings['ssid'],
            target_pwd=soft_ap_settings.get('password'),
            target_security=hostapd_constants.
            SECURITY_STRING_TO_DEFAULT_TARGET_SECURITY.get(
                soft_ap_settings['security_type'], None),
            check_connectivity=check_connectivity)

        if not associated:
            self.log.error('Failed to connect to SoftAp.')
            return False

        self.log.info('Client successfully associated with SoftAP.')
        return True

    def disconnect_from_soft_ap(self, device):
        """Disconnects client device from SoftAP.

        Args:
            device: wlan_device to disconnect from SoftAP
        """
        self.log.info('Disconnecting device %s from SoftAP.' %
                      device.identifier)
        device.disconnect()

    def get_device_test_interface(self, device, role=None, channel=None):
        """Retrieves test interface from a provided device, which can be the
        FuchsiaDevice DUT, the AccessPoint, or an AndroidClient.

        Args:
            device: the device do get the test interface from. Either
                FuchsiaDevice (DUT), Android client, or AccessPoint.
            role: str, either "client" or "ap". Required for FuchsiaDevice (DUT)
            channel: int, channel of the ap network. Required for AccessPoint.

        Returns:
            String, name of test interface on given device.
        """

        if device is self.dut:
            device.device.wlan_controller.update_wlan_interfaces()
            if role == INTERFACE_ROLE_CLIENT:
                return device.device.wlan_client_test_interface_name
            elif role == INTERFACE_ROLE_AP:
                return device.device.wlan_ap_test_interface_name
            else:
                raise ValueError('Unsupported interface role: %s' % role)
        elif isinstance(device, AccessPoint):
            if not channel:
                raise ValueError(
                    'Must provide a channel to get AccessPoint interface')
            if channel < 36:
                return device.wlan_2g
            else:
                return device.wlan_5g
        else:
            return device.get_default_wlan_test_interface()

    def wait_for_ipv4_address(self,
                              device,
                              interface_name,
                              timeout=DEFAULT_TIMEOUT):
        """ Waits for interface on a wlan_device to get an ipv4 address.

        Args:
            device: wlan_device or AccessPoint to check interface
            interface_name: name of the interface to check
            timeout: seconds to wait before raising an error

        Raises:
            ValueError, if interface does not have an ipv4 address after timeout
        """
        if isinstance(device, AccessPoint):
            comm_channel = device.ssh
        else:
            comm_channel = device.device
        end_time = time.time() + timeout
        while time.time() < end_time:
            ips = utils.get_interface_ip_addresses(comm_channel,
                                                   interface_name)
            if len(ips['ipv4_private']) > 0:
                self.log.info('Device %s interface %s has ipv4 address %s' %
                              (device.identifier, interface_name,
                               ips['ipv4_private'][0]))
                return ips['ipv4_private'][0]
            else:
                time.sleep(1)
        raise ConnectionError(
            'After %s seconds, device %s still does not have an ipv4 address '
            'on interface %s.' % (timeout, device.identifier, interface_name))

    def device_can_ping_addr(self, device, dest_ip, timeout=DEFAULT_TIMEOUT):
        """ Verify wlan_device can ping a destination ip.

        Args:
            device: wlan_device to initiate ping
            dest_ip: ip to ping from wlan_device

        Raises:
            TestFailure, if ping fails
        """
        end_time = time.time() + timeout
        while time.time() < end_time:
            with utils.SuppressLogOutput():
                ping_result = device.can_ping(dest_ip)

            if ping_result:
                self.log.info('Ping successful from device %s to dest ip %s.' %
                              (device.identifier, dest_ip))
                return True
            else:
                self.log.debug(
                    'Device %s could not ping dest ip %s. Retrying in 1 second.'
                    % (device.identifier, dest_ip))
                time.sleep(1)
        else:
            self.log.info('Failed to ping from device %s to dest ip %s.' %
                          (device.identifier, dest_ip))
            return False

    def run_iperf_traffic(self, ip_client, server_address, server_port=5201):
        """Runs traffic between client and ap an verifies throughput.

        Args:
            ip_client: iperf client to use
            server_address: ipv4 address of the iperf server to use
            server_port: port of the iperf server

        Raises:
            TestFailure, if no traffic passes in either direction
        """
        ip_client_identifier = self.get_iperf_client_identifier(ip_client)

        self.log.info(
            'Running traffic from iperf client %s to iperf server %s.' %
            (ip_client_identifier, server_address))
        client_to_ap_path = ip_client.start(
            server_address, '-i 1 -t 10 -J -p %s' % server_port,
            'client_to_soft_ap')

        client_to_ap_result = iperf_server.IPerfResult(client_to_ap_path)
        if (not client_to_ap_result.avg_receive_rate):
            raise ConnectionError(
                'Failed to pass traffic from iperf client %s to iperf server %s.'
                % (ip_client_identifier, server_address))

        self.log.info(
            'Passed traffic from iperf client %s to iperf server %s with avg '
            'rate of %s MB/s.' % (ip_client_identifier, server_address,
                                  client_to_ap_result.avg_receive_rate))

        self.log.info(
            'Running traffic from iperf server %s to iperf client %s.' %
            (server_address, ip_client_identifier))
        ap_to_client_path = ip_client.start(
            server_address, '-i 1 -t 10 -R -J -p %s' % server_port,
            'soft_ap_to_client')

        ap_to_client_result = iperf_server.IPerfResult(ap_to_client_path)
        if (not ap_to_client_result.avg_receive_rate):
            raise ConnectionError(
                'Failed to pass traffic from iperf server %s to iperf client %s.'
                % (server_address, ip_client_identifier))

        self.log.info(
            'Passed traffic from iperf server %s to iperf client %s with avg '
            'rate of %s MB/s.' % (server_address, ip_client_identifier,
                                  ap_to_client_result.avg_receive_rate))

    def run_iperf_traffic_parallel_process(self,
                                           ip_client,
                                           server_address,
                                           error_queue,
                                           server_port=5201):
        """ Executes run_iperf_traffic using a queue to capture errors. Used
        when running iperf in a parallel process.

        Args:
            ip_client: iperf client to use
            server_address: ipv4 address of the iperf server to use
            error_queue: multiprocessing queue to capture errors
            server_port: port of the iperf server
        """
        try:
            self.run_iperf_traffic(ip_client,
                                   server_address,
                                   server_port=server_port)
        except ConnectionError as err:
            error_queue.put('In iperf process from %s to %s: %s' %
                            (self.get_iperf_client_identifier(ip_client),
                             server_address, err))

    def get_iperf_client_identifier(self, ip_client):
        """ Retrieves an indentifer string from iperf client, for logging.

        Args:
            ip_client: iperf client to grab identifier from
        """
        if type(ip_client) == iperf_client.IPerfClientOverAdb:
            return ip_client._android_device_or_serial.serial
        return ip_client._ssh_settings.hostname

    def device_is_connected_to_ap(self,
                                  client,
                                  ap,
                                  channel=None,
                                  check_traffic=False,
                                  timeout=DEFAULT_TIMEOUT):
        """ Returns whether client device can ping (and optionally pass traffic)
        to the ap device.

        Args:
            client: device that should be associated. Either FuchsiaDevice (DUT)
                or Android client
            ap: device acting as AP. Either FuchsiaDevice (DUT) or AccessPoint.
            channel: int, channel the AP is using. Required if ap is an
                AccessPoint object.
            check_traffic: bool, whether to attempt to pass traffic between
                client and ap devices.
            timeout: int, time in seconds to wait for devices to have ipv4
                addresses
        """
        try:
            # Get interfaces
            client_interface = self.get_device_test_interface(
                client, INTERFACE_ROLE_CLIENT)
            ap_interface = self.get_device_test_interface(
                ap, role=INTERFACE_ROLE_AP, channel=channel)

            # Get addresses
            client_ipv4 = self.wait_for_ipv4_address(client,
                                                     client_interface,
                                                     timeout=timeout)
            ap_ipv4 = self.wait_for_ipv4_address(ap,
                                                 ap_interface,
                                                 timeout=timeout)
        except ConnectionError as err:
            self.log.error(
                'Failed to retrieve interfaces and addresses. Err: %s' % err)
            return False

        if not self.device_can_ping_addr(client, ap_ipv4):
            self.log.error('Failed to ping from client to ap.')
            return False

        if not self.device_can_ping_addr(ap, client_ipv4):
            self.log.error('Failed to ping from ap to client.')
            return False

        if check_traffic:
            try:
                if client is self.dut:
                    self.run_iperf_traffic(self.iperf_clients_map[ap],
                                           client_ipv4)
                else:
                    self.run_iperf_traffic(self.iperf_clients_map[client],
                                           ap_ipv4)
            except ConnectionError as err:
                self.log.error('Failed to run traffic between DUT and AP.')
                return False
        return True

    def verify_soft_ap_connectivity_from_state(self, state, client):
        """Verifies SoftAP state based on a client connection.

        Args:
            state: bool, whether SoftAP should be up
            client: SoftApClient, to verify connectivity (or lack therof)
        """
        if state == STATE_UP:
            return self.device_is_connected_to_ap(client, self.dut)
        else:
            with utils.SuppressLogOutput():
                try:
                    return not self.device_is_connected_to_ap(
                        client,
                        self.dut,
                        timeout=DEFAULT_NO_ADDR_EXPECTED_TIMEOUT)
                # Allow a failed to find ap interface error
                except LookupError as err:
                    self.log.debug('Hit expected LookupError: %s' % err)
                    return True

    def verify_client_mode_connectivity_from_state(self, state, channel):
        """Verifies client mode state based on DUT-AP connection.

        Args:
            state: bool, whether client mode should be up
            channel: int, channel of the APs network
        """
        if state == STATE_UP:
            return self.device_is_connected_to_ap(self.dut,
                                                  self.access_point,
                                                  channel=channel)
        else:
            with utils.SuppressLogOutput():
                try:
                    return not self.device_is_connected_to_ap(
                        self.dut,
                        self.access_point,
                        channel=channel,
                        timeout=DEFAULT_NO_ADDR_EXPECTED_TIMEOUT)
                # Allow a failed to find client interface error
                except LookupError as err:
                    self.log.debug('Hit expected LookupError: %s' % err)
                    return True

# Test Types

    def verify_soft_ap_associate_only(self, client, soft_ap_settings):
        if not self.associate_with_soft_ap(client, soft_ap_settings):
            asserts.fail('Failed to associate client with SoftAP.')

    def verify_soft_ap_associate_and_ping(self, client, soft_ap_settings):
        self.verify_soft_ap_associate_only(client, soft_ap_settings)
        if not self.device_is_connected_to_ap(client, self.dut):
            asserts.fail('Client and SoftAP could not ping eachother.')

    def verify_soft_ap_associate_and_pass_traffic(self, client, settings):
        self.verify_soft_ap_associate_only(client, settings)
        if not self.device_is_connected_to_ap(
                client, self.dut, check_traffic=True):
            asserts.fail(
                'Client and SoftAP not responding to pings and passing traffic '
                'as expected.')

# Runners for Generated Test Cases

    def run_soft_ap_association_stress_test(self, settings):
        """Sets up a SoftAP, and repeatedly associates and disassociates a
        client.

        Args:
            settings: test configuration settings, see
                test_soft_ap_association_stress for details
        """
        client = settings['client']
        soft_ap_params = settings['soft_ap_params']
        test_type = settings['test_type']
        if not test_type in TEST_TYPES:
            raise ValueError('Unrecognized test type %s' % test_type)
        iterations = settings['iterations']
        self.log.info(
            'Running association stress test type %s in iteration %s times' %
            (test_type, iterations))

        self.start_soft_ap(soft_ap_params)

        passed_count = 0
        for run in range(iterations):
            try:
                self.log.info('Starting SoftAp association run %s' %
                              str(run + 1))

                if test_type == TEST_TYPE_ASSOCIATE_ONLY:
                    self.verify_soft_ap_associate_only(client, soft_ap_params)

                elif test_type == TEST_TYPE_ASSOCIATE_AND_PING:
                    self.verify_soft_ap_associate_and_ping(
                        client, soft_ap_params)

                elif test_type == TEST_TYPE_ASSOCIATE_AND_PASS_TRAFFIC:
                    self.verify_soft_ap_associate_and_pass_traffic(
                        client, soft_ap_params)

                else:
                    raise AttributeError('Invalid test type: %s' % test_type)

            except signals.TestFailure as err:
                self.log.error(
                    'SoftAp association stress run %s failed. Err: %s' %
                    (str(run + 1), err.details))
            else:
                self.log.info('SoftAp association stress run %s successful.' %
                              str(run + 1))
                passed_count += 1

        if passed_count < iterations:
            asserts.fail(
                'SoftAp association stress test passed on %s/%s runs.' %
                (passed_count, iterations))

        asserts.explicit_pass(
            'SoftAp association stress test passed on %s/%s runs.' %
            (passed_count, iterations))

# Alternate SoftAP and Client mode test

    def run_soft_ap_and_client_mode_alternating_test(self, settings):
        """Runs a single soft_ap and client alternating stress test.

        See test_soft_ap_and_client_mode_alternating_stress for details.
        """
        iterations = settings['iterations']
        pass_count = 0
        current_soft_ap_state = STATE_DOWN
        current_client_mode_state = STATE_DOWN

        self.client_mode_toggle_pre_test(settings)
        for iteration in range(iterations):
            passes = True

            # Attempt to toggle SoftAP on, then off. If the first toggle fails
            # to occur, exit early.
            for _ in range(2):
                (current_soft_ap_state, err) = self.run_toggle_iteration_func(
                    self.soft_ap_toggle_test_iteration, settings,
                    current_soft_ap_state)
                if err:
                    self.log.error('Iteration %s failed. Err: %s' %
                                   (str(iteration + 1), err))
                    passes = False
                if current_soft_ap_state == STATE_DOWN:
                    break

            # Attempt to toggle Client mode on, then off. If the first toggle,
            # fails to occur, exit early.
            for _ in range(2):
                (current_client_mode_state,
                 err) = self.run_toggle_iteration_func(
                     self.client_mode_toggle_test_iteration, settings,
                     current_client_mode_state)
                if err:
                    self.log.error('Iteration %s failed. Err: %s' %
                                   (str(iteration + 1), err))
                    passes = False
                if current_client_mode_state == STATE_DOWN:
                    break

            if passes:
                pass_count += 1

        if pass_count == iterations:
            asserts.explicit_pass(
                'Toggle SoftAP and client mode stress test passed %s/%s times.'
                % (pass_count, iterations))
        else:
            asserts.fail(
                'Toggle SoftAP and client mode stress test only passed %s/%s '
                'times.' % (pass_count, iterations))

# Toggle Stress Test Helper Functions

    def run_toggle_stress_test(self, settings):
        """Runner function for toggle stress tests.

        Repeats some test function through stress test iterations, logging
        failures, tracking pass rate, managing states, etc.

        Args:
            settings: dict, stress test settings

        Asserts:
            PASS: if all iterations of the test function pass
            FAIL: if any iteration of the test function fails
        """
        test_runner_func = settings['test_runner_func']
        pre_test_func = settings.get('pre_test_func', None)
        iterations = settings['iterations']
        if pre_test_func:
            pre_test_func(settings)

        pass_count = 0
        current_state = STATE_DOWN
        for iteration in range(iterations):
            (current_state,
             err) = self.run_toggle_iteration_func(test_runner_func, settings,
                                                   current_state)
            if err:
                self.log.error('Iteration %s failed. Err: %s' %
                               (str(iteration + 1), err))
            else:
                pass_count += 1

        if pass_count == iterations:
            asserts.explicit_pass('Stress test passed %s/%s times.' %
                                  (pass_count, iterations))
        else:
            asserts.fail('Stress test only passed %s/%s '
                         'times.' % (pass_count, iterations))

    def run_toggle_iteration_func(self, func, settings, current_state):
        """Runs a toggle iteration function, updating the current state
        based on what the toggle iteration function raises.

        Used for toggle stress tests.

        Note on EnvironmentError vs StressTestIterationFailure:
            StressTestIterationFailure is raised by func when the toggle occurs
                but connectivty or some other post-toggle check fails (i.e. the
                next iteration should toggle to the next state.)

            EnvironmentError is raise by func when the toggle itself fails (i.e
                the next iteration should retry the same toggle again.)

        Args:
            func: toggle iteration func to run (e.g soft_ap_toggle_iteration)
            settings: dict, stress test settings
            current_state: bool, the current state of the mode being toggled

        Returns:
            (new_state, err):
                new_state: bool, state of the mode after toggle attempt
                err: exception, if any are raise, else None
        """
        try:
            func(settings, current_state)
        except EnvironmentError as err:
            return (current_state, err)
        except StressTestIterationFailure as err:
            return (not current_state, err)
        else:
            return (not current_state, None)

# Stress Test Toggle Functions

    def start_soft_ap_and_verify_connected(self, client, soft_ap_params):
        """Sets up SoftAP, associates a client, then verifies connection.

        Args:
            client: SoftApClient, client to use to verify SoftAP
            soft_ap_params: dict, containing parameters to setup softap

        Raises:
            StressTestIterationFailure, if toggle occurs, but connection
            is not functioning as expected
        """
        # Change SSID every time, to avoid client connection issues.
        soft_ap_params['ssid'] = utils.rand_ascii_str(
            hostapd_constants.AP_SSID_LENGTH_2G)
        self.start_soft_ap(soft_ap_params)
        associated = self.associate_with_soft_ap(client, soft_ap_params)
        if not associated:
            raise StressTestIterationFailure(
                'Failed to associated client to DUT SoftAP. '
                'Continuing with iterations.')

        if not self.verify_soft_ap_connectivity_from_state(STATE_UP, client):
            raise StressTestIterationFailure(
                'Failed to ping between client and DUT. Continuing '
                'with iterations.')

    def stop_soft_ap_and_verify_disconnected(self, client, soft_ap_params):
        """Tears down SoftAP, and verifies connection is down.

        Args:
            client: SoftApClient, client to use to verify SoftAP
            soft_ap_params: dict, containing parameters of SoftAP to teardown

        Raise:
            EnvironmentError, if client and AP can still communicate
        """
        self.log.info('Stopping SoftAP on DUT.')
        self.stop_soft_ap(soft_ap_params)

        if not self.verify_soft_ap_connectivity_from_state(STATE_DOWN, client):
            raise EnvironmentError(
                'Client can still ping DUT. Continuing with '
                'iterations.')

    def start_client_mode_and_verify_connected(self, ap_params):
        """Connects DUT to AP in client mode and verifies connection

        Args:
            ap_params: dict, containing parameters of the AP network

        Raises:
            EnvironmentError, if DUT fails to associate altogether
            StressTestIterationFailure, if DUT associates but connection is not
                functioning as expected.
        """
        ap_ssid = ap_params['ssid']
        ap_password = ap_params['password']
        ap_channel = ap_params['channel']
        ap_security = ap_params.get('security')

        if ap_security:
            ap_security_mode = ap_security.security_mode_string
        else:
            ap_security_mode = None

        self.log.info('Associating DUT with AP network: %s' % ap_ssid)
        associated = self.dut.associate(
            target_ssid=ap_ssid,
            target_pwd=ap_password,
            target_security=hostapd_constants.
            SECURITY_STRING_TO_DEFAULT_TARGET_SECURITY.get(
                ap_security_mode, None))
        if not associated:
            raise EnvironmentError('Failed to associate DUT in client mode.')
        else:
            self.log.info('Association successful.')

        if not self.verify_client_mode_connectivity_from_state(
                STATE_UP, ap_channel):
            raise StressTestIterationFailure('Failed to ping AP from DUT.')

    def stop_client_mode_and_verify_disconnected(self, ap_params):
        """Disconnects DUT from AP and verifies connection is down.

        Args:
            ap_params: dict, containing parameters of the AP network

        Raises:
            EnvironmentError, if DUT and AP can still communicate
        """
        self.log.info('Disconnecting DUT from AP.')
        self.dut.disconnect()
        if not self.verify_client_mode_connectivity_from_state(
                STATE_DOWN, ap_params['channel']):
            raise EnvironmentError('DUT can still ping AP.')

# Toggle Stress Test Iteration and Pre-Test Functions

# SoftAP Toggle Stress Test Helper Functions

    def soft_ap_toggle_test_iteration(self, settings, current_state):
        """Runs a single iteration of SoftAP toggle stress test

        Args:
            settings: dict, containing test settings
            current_state: bool, current state of SoftAP (True if up,
                else False)

        Raises:
            StressTestIterationFailure, if toggle occurs but mode isn't
                functioning correctly.
            EnvironmentError, if toggle fails to occur at all
        """
        soft_ap_params = settings['soft_ap_params']
        self.log.info('Toggling SoftAP %s.' %
                      ('down' if current_state else 'up'))

        if current_state == STATE_DOWN:
            self.start_soft_ap_and_verify_connected(self.primary_client,
                                                    soft_ap_params)

        else:
            self.stop_soft_ap_and_verify_disconnected(self.primary_client,
                                                      soft_ap_params)

# Client Mode Toggle Stress Test Helper Functions

    def client_mode_toggle_pre_test(self, settings):
        """Prepares the AP before client mode toggle tests

        Args:
            settings: dict, stress test settings

        Raises:
            ConnectionError, if AP setup fails
        """
        ap_params = settings['ap_params']
        ap_channel = ap_params['channel']
        ap_profile = ap_params.pop('profile')
        self.log.info('Setting up AP with params: %s' % ap_params)
        setup_ap(access_point=self.access_point,
                 profile_name=ap_profile,
                 **ap_params)
        # Confirms AP assigned itself an address
        ap_interface = self.get_device_test_interface(self.access_point,
                                                      channel=ap_channel)
        self.wait_for_ipv4_address(self.access_point, ap_interface)

    def client_mode_toggle_test_iteration(self, settings, current_state):
        """Runs a single iteration of client mode toggle stress test

        Args:
            settings: dict, containing test settings
            current_state: bool, current state of client mode (True if up,
                else False)

        Raises:
            StressTestIterationFailure, if toggle occurs but mode isn't
                functioning correctly.
            EnvironmentError, if toggle fails to occur at all
        """
        ap_params = settings['ap_params']
        self.log.info('Toggling client mode %s' %
                      ('off' if current_state else 'on'))

        if current_state == STATE_DOWN:
            self.start_client_mode_and_verify_connected(ap_params)

        else:
            self.stop_client_mode_and_verify_disconnected(ap_params)

# Toggle SoftAP with Client Mode Up Test Helper Functions

    def soft_ap_toggle_with_client_mode_pre_test(self, settings):
        """Sets up and verifies client mode before SoftAP toggle test.
        Args:
            settings: dict, stress test settings

        Raises:
            ConnectionError, if client mode setup fails
        """
        self.client_mode_toggle_pre_test(settings)
        try:
            self.start_client_mode_and_verify_connected(settings['ap_params'])
        except StressTestIterationFailure as err:
            # This prevents it being treated as a routine error
            raise ConnectionError(
                'Failed to set up DUT client mode before SoftAP toggle test.'
                'Err: %s' % err)

    def soft_ap_toggle_with_client_mode_iteration(
        self,
        settings,
        current_state,
    ):
        """Runs single iteration of SoftAP toggle stress with client mode test.

        Args:
            settings: dict, containing test settings
            current_state: bool, current state of SoftAP (True if up,
                else False)

        Raises:
            StressTestIterationFailure, if toggle occurs but mode isn't
                functioning correctly.
            EnvironmentError, if toggle fails to occur at all
        """
        ap_params = settings['ap_params']
        ap_channel = ap_params['channel']
        self.soft_ap_toggle_test_iteration(settings, current_state)
        if not self.device_is_connected_to_ap(
                self.dut, self.access_point, channel=ap_channel):
            raise StressTestIterationFailure(
                'DUT client mode is no longer functional after SoftAP toggle.')

# Toggle Client Mode with SoftAP Up Test Helper Functions

    def client_mode_toggle_with_soft_ap_pre_test(self, settings):
        """Sets up and verifies softap before client mode toggle test.
        Args:
            settings: dict, stress test settings

        Raises:
            ConnectionError, if softap setup fails
        """
        self.client_mode_toggle_pre_test(settings)
        try:
            self.start_soft_ap_and_verify_connected(self.primary_client,
                                                    settings['soft_ap_params'])
        except StressTestIterationFailure as err:
            # This prevents it being treated as a routine error
            raise ConnectionError(
                'Failed to set up SoftAP before client mode toggle test. Err: %s'
                % err)

    def client_mode_toggle_with_soft_ap_iteration(self, settings,
                                                  current_state):
        """Runs single iteration of client mode toggle stress with SoftAP test.

        Args:
            settings: dict, containing test settings
            current_state: bool, current state of client mode (True if up,
                else False)

        Raises:
            StressTestIterationFailure, if toggle occurs but mode isn't
                functioning correctly.
            EnvironmentError, if toggle fails to occur at all
        """
        self.client_mode_toggle_test_iteration(settings, current_state)
        if not self.device_is_connected_to_ap(self.primary_client, self.dut):
            raise StressTestIterationFailure(
                'SoftAP is no longer functional after client mode toggle.')

# Toggle SoftAP and Client Mode Randomly

    def run_soft_ap_and_client_mode_random_toggle_stress_test(self, settings):
        """Runner function for SoftAP and client mode random toggle tests.

        Each iteration, randomly chooses if a mode will be toggled or not.

        Args:
            settings: dict, containing test settings
        """
        iterations = settings['iterations']
        pass_count = 0
        current_soft_ap_state = STATE_DOWN
        current_client_mode_state = STATE_DOWN
        ap_channel = settings['ap_params']['channel']

        self.client_mode_toggle_pre_test(settings)
        for iteration in range(iterations):
            self.log.info('Starting iteration %s out of %s.' %
                          (str(iteration + 1), iterations))
            passes = True

            # Randomly determine if softap, client mode, or both should
            # be toggled.
            rand_toggle_choice = random.randrange(0, 3)
            if rand_toggle_choice <= 1:
                (current_soft_ap_state, err) = self.run_toggle_iteration_func(
                    self.soft_ap_toggle_test_iteration, settings,
                    current_soft_ap_state)
                if err:
                    self.log.error(
                        'Iteration %s failed toggling SoftAP. Err: %s' %
                        (str(iteration + 1), err))
                    passes = False
            if rand_toggle_choice >= 1:
                (current_client_mode_state,
                 err) = self.run_toggle_iteration_func(
                     self.client_mode_toggle_test_iteration, settings,
                     current_client_mode_state)
                if err:
                    self.log.error(
                        'Iteration %s failed toggling client mode. Err: %s' %
                        (str(iteration + 1), err))
                    passes = False

            soft_ap_verified = self.verify_soft_ap_connectivity_from_state(
                current_soft_ap_state, self.primary_client)
            client_mode_verified = self.verify_client_mode_connectivity_from_state(
                current_client_mode_state, ap_channel)

            if not soft_ap_verified or not client_mode_verified:
                passes = False
            if passes:
                pass_count += 1

        if pass_count == iterations:
            asserts.explicit_pass('Stress test passed %s/%s times.' %
                                  (pass_count, iterations))
        else:
            asserts.fail('Stress test only passed %s/%s '
                         'times.' % (pass_count, iterations))


# Test Cases

    def test_soft_ap_2g_open_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G),
            'security_type': SECURITY_OPEN,
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_2G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_5g_open_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_OPEN,
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_5G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_any_open_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_OPEN,
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_ANY
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_2g_wep_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G),
            'security_type': SECURITY_WEP,
            'password': generate_random_password(security_mode=SECURITY_WEP),
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_2G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_5g_wep_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WEP,
            'password': generate_random_password(security_mode=SECURITY_WEP),
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_5G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_any_wep_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WEP,
            'password': generate_random_password(security_mode=SECURITY_WEP),
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_ANY
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client, )

    def test_soft_ap_2g_wpa_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G),
            'security_type': SECURITY_WPA,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_2G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_5g_wpa_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WPA,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_5G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_any_wpa_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WPA,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_ANY
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_2g_wpa2_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G),
            'security_type': SECURITY_WPA2,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_2G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_5g_wpa2_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WPA2,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_5G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_any_wpa2_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WPA2,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_ANY
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_2g_wpa3_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G),
            'security_type': SECURITY_WPA3,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_2G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_5g_wpa3_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WPA3,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_ANY
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_any_wpa3_local(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WPA3,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_LOCAL,
            'operating_band': OPERATING_BAND_ANY
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_2g_open_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G),
            'security_type': SECURITY_OPEN,
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_2G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_5g_open_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_OPEN,
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_5G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_any_open_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_OPEN,
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_ANY
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_2g_wep_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G),
            'security_type': SECURITY_WEP,
            'password': generate_random_password(security_mode=SECURITY_WEP),
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_2G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_5g_wep_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WEP,
            'password': generate_random_password(security_mode=SECURITY_WEP),
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_5G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_any_wep_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WEP,
            'password': generate_random_password(security_mode=SECURITY_WEP),
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_ANY
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_2g_wpa_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G),
            'security_type': SECURITY_WPA,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_2G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_5g_wpa_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WPA,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_5G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_any_wpa_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WPA,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_ANY
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_2g_wpa2_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G),
            'security_type': SECURITY_WPA2,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_2G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_5g_wpa2_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WPA2,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_5G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_any_wpa2_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WPA2,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_ANY
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_2g_wpa3_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G),
            'security_type': SECURITY_WPA3,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_2G
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_5g_wpa3_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WPA3,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_ANY
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_soft_ap_any_wpa3_unrestricted(self):
        soft_ap_params = {
            'ssid': utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G),
            'security_type': SECURITY_WPA3,
            'password': generate_random_password(),
            'connectivity_mode': CONNECTIVITY_MODE_UNRESTRICTED,
            'operating_band': OPERATING_BAND_ANY
        }
        self.start_soft_ap(soft_ap_params)
        self.verify_soft_ap_associate_and_pass_traffic(self.primary_client,
                                                       soft_ap_params)

    def test_multi_client(self):
        """Tests multi-client association with a single soft AP network.

        This tests associates a variable length list of clients, verfying it can
        can ping the SoftAP and pass traffic, and then verfies all previously
        associated clients can still ping and pass traffic.

        The same occurs in reverse for disassocations.

        SoftAP parameters can be changed from default via ACTS config:
        Example Config
        "soft_ap_test_params" : {
            "multi_client_test_params": {
                "ssid": "testssid",
                "security_type": "wpa2",
                "password": "password",
                "connectivity_mode": "local_only",
                "operating_band": "only_2_4_ghz"
            }
        }
        """
        asserts.skip_if(
            len(self.clients) < 2, 'Test requires at least 2 SoftAPClients')

        test_params = self.soft_ap_test_params.get('multi_client_test_params',
                                                   {})
        soft_ap_params = get_soft_ap_params_from_config_or_default(
            test_params.get('soft_ap_params', {}))

        self.start_soft_ap(soft_ap_params)

        associated = []

        for client in self.clients:
            # Associate new client
            self.verify_soft_ap_associate_and_ping(client, soft_ap_params)

            # Verify previously associated clients still behave as expected
            for associated_client in associated:
                self.log.info(
                    'Verifying previously associated client %s still functions correctly.'
                    % associated_client['device'].identifier)
                if not self.device_is_connected_to_ap(
                        associated_client['device'], self.dut,
                        check_traffic=True):
                    asserts.fail(
                        'Previously associated client %s failed checks after '
                        'client %s associated.' %
                        (associated_client['device'].identifier,
                         client.identifier))

            client_interface = self.get_device_test_interface(client)
            client_ipv4 = self.wait_for_ipv4_address(client, client_interface)
            associated.append({"device": client, "address": client_ipv4})

        self.log.info('All devices successfully associated.')

        self.log.info('Verifying all associated clients can ping eachother.')
        for transmitter in associated:
            for receiver in associated:
                if transmitter != receiver:
                    if not transmitter['device'].can_ping(receiver['address']):
                        asserts.fail(
                            'Could not ping from one associated client (%s) to another (%s).'
                            % (transmitter['address'], receiver['address']))
                    else:
                        self.log.info(
                            'Successfully pinged from associated client (%s) to another (%s)'
                            % (transmitter['address'], receiver['address']))

        self.log.info(
            'All associated clients can ping eachother. Beginning disassociations.'
        )

        while len(associated) > 0:
            # Disassociate client
            client = associated.pop()['device']
            self.disconnect_from_soft_ap(client)

            # Verify still connected clients still behave as expected
            for associated_client in associated:
                self.log.info(
                    'Verifying still associated client %s still functions '
                    'correctly.' % associated_client['device'].identifier)
                if not self.device_is_connected_to_ap(
                        associated_client['device'], self.dut,
                        check_traffic=True):
                    asserts.fail(
                        'Previously associated client %s failed checks after'
                        ' client %s disassociated.' %
                        (associated_client['device'].identifier,
                         client.identifier))

        self.log.info('All disassociations occurred smoothly.')

    def test_simultaneous_soft_ap_and_client(self):
        """ Tests FuchsiaDevice DUT can act as a client and a SoftAP
        simultaneously.

        Raises:
            ConnectionError: if DUT fails to connect as client
            RuntimeError: if parallel processes fail to join
            TestFailure: if DUT fails to pass traffic as either a client or an
                AP
        """
        asserts.skip_if(not self.access_point, 'No access point provided.')

        self.log.info('Setting up AP using hostapd.')
        test_params = self.soft_ap_test_params.get(
            'soft_ap_and_client_test_params', {})

        # Configure AP
        ap_params = get_ap_params_from_config_or_default(
            test_params.get('ap_params', {}))

        # Setup AP and associate DUT
        ap_profile = ap_params.pop('profile')
        setup_ap(access_point=self.access_point,
                 profile_name=ap_profile,
                 **ap_params)
        try:
            self.start_client_mode_and_verify_connected(ap_params)
        except Exception as err:
            asserts.fail('Failed to set up client mode. Err: %s' % err)

        # Setup SoftAP
        soft_ap_params = get_soft_ap_params_from_config_or_default(
            test_params.get('soft_ap_params', {}))
        self.start_soft_ap_and_verify_connected(self.primary_client,
                                                soft_ap_params)

        # Get FuchsiaDevice test interfaces
        dut_ap_interface = self.get_device_test_interface(
            self.dut, role=INTERFACE_ROLE_AP)
        dut_client_interface = self.get_device_test_interface(
            self.dut, role=INTERFACE_ROLE_CLIENT)

        # Get FuchsiaDevice addresses
        dut_ap_ipv4 = self.wait_for_ipv4_address(self.dut, dut_ap_interface)
        dut_client_ipv4 = self.wait_for_ipv4_address(self.dut,
                                                     dut_client_interface)

        # Set up secondary iperf server of FuchsiaDevice
        self.log.info('Setting up second iperf server on FuchsiaDevice DUT.')
        secondary_iperf_server = iperf_server.IPerfServerOverSsh(
            self.iperf_server_config, DEFAULT_IPERF_PORT + 1, use_killall=True)
        secondary_iperf_server.start()

        # Set up iperf client on AP
        self.log.info('Setting up iperf client on AP.')
        ap_iperf_client = iperf_client.IPerfClientOverSsh(
            self.user_params['AccessPoint'][0]['ssh_config'])

        # Setup iperf processes:
        #     Primary client <-> SoftAP interface on FuchsiaDevice
        #     AP <-> Client interface on FuchsiaDevice
        process_errors = mp.Queue()
        iperf_soft_ap = mp.Process(
            target=self.run_iperf_traffic_parallel_process,
            args=[
                self.iperf_clients_map[self.primary_client], dut_ap_ipv4,
                process_errors
            ])

        iperf_fuchsia_client = mp.Process(
            target=self.run_iperf_traffic_parallel_process,
            args=[ap_iperf_client, dut_client_ipv4, process_errors],
            kwargs={'server_port': 5202})

        # Run iperf processes simultaneously
        self.log.info('Running simultaneous iperf traffic: between AP and DUT '
                      'client interface, and DUT AP interface and client.')

        iperf_soft_ap.start()
        iperf_fuchsia_client.start()

        # Block until processes can join or timeout
        for proc in [iperf_soft_ap, iperf_fuchsia_client]:
            proc.join(timeout=DEFAULT_IPERF_TIMEOUT)
            if proc.is_alive():
                proc.terminate()
                proc.join()
                raise RuntimeError('Failed to join process %s' % proc)

        # Stop iperf server (also stopped in teardown class as failsafe)
        secondary_iperf_server.stop()

        # Check errors from parallel processes
        if process_errors.empty():
            asserts.explicit_pass(
                'FuchsiaDevice was successfully able to pass traffic as a '
                'client and an AP simultaneously.')
        else:
            while not process_errors.empty():
                self.log.error('Error in iperf process: %s' %
                               process_errors.get())
            asserts.fail(
                'FuchsiaDevice failed to pass traffic as a client and an AP '
                'simultaneously.')

    def test_soft_ap_association_stress(self):
        """ Sets up a single AP and repeatedly associate/disassociate
        a client, verifying connection every time

        Each test creates 1 SoftAP and repeatedly associates/disassociates
        client.

        Example Config
        "soft_ap_test_params" : {
            "soft_ap_association_stress_tests": [
                {
                    "ssid": "test_network",
                    "security_type": "wpa2",
                    "password": "password",
                    "connectivity_mode": "local_only",
                    "operating_band": "only_2_4_ghz",
                    "iterations": 10
                }
            ]
        }
        """
        tests = self.soft_ap_test_params.get(
            'test_soft_ap_association_stress',
            [dict(test_name='test_soft_ap_association_stress_default')])

        test_settings_list = []
        for config_settings in tests:
            soft_ap_params = get_soft_ap_params_from_config_or_default(
                config_settings.get('soft_ap_params', {}))
            test_type = config_settings.get('test_type',
                                            'associate_and_pass_traffic')
            iterations = config_settings.get('iterations',
                                             DEFAULT_STRESS_TEST_ITERATIONS)
            test_settings = {
                'test_name':
                config_settings.get(
                    'test_name',
                    'test_soft_ap_association_stress_%s_iterations' %
                    iterations),
                'client':
                self.primary_client,
                'soft_ap_params':
                soft_ap_params,
                'test_type':
                test_type,
                'iterations':
                iterations
            }
            test_settings_list.append(test_settings)

        self.run_generated_testcases(self.run_soft_ap_association_stress_test,
                                     test_settings_list,
                                     name_func=get_test_name_from_settings)

    def test_soft_ap_and_client_mode_alternating_stress(self):
        """ Runs tests that alternate between SoftAP and Client modes.

        Each tests sets up an AP. Then, for each iteration:
            - DUT starts up SoftAP, client associates with SoftAP,
                connection is verified, then disassociates
            - DUT associates to the AP, connection is verified, then
                disassociates

        Example Config:
        "soft_ap_test_params": {
            "toggle_soft_ap_and_client_tests": [
                {
                    "test_name": "test_wpa2_client_ap_toggle",
                    "ap_params": {
                        "channel": 6,
                        "ssid": "test-ap-network",
                        "security_mode": "wpa2",
                        "password": "password"
                    },
                    "soft_ap_params": {
                        "ssid": "test-soft-ap-network",
                        "security_type": "wpa2",
                        "password": "other-password",
                        "connectivity_mode": "local_only",
                        "operating_band": "only_2_4_ghz"
                    },
                    "iterations": 5
                }
            ]
        }
        """
        asserts.skip_if(not self.access_point, 'No access point provided.')
        tests = self.soft_ap_test_params.get(
            'test_soft_ap_and_client_mode_alternating_stress', [
                dict(test_name=
                     'test_soft_ap_and_client_mode_alternating_stress_default')
            ])

        test_settings_list = []
        for config_settings in tests:
            ap_params = get_ap_params_from_config_or_default(
                config_settings.get('ap_params', {}))
            soft_ap_params = get_soft_ap_params_from_config_or_default(
                config_settings.get('soft_ap_params', {}))
            iterations = config_settings.get('iterations',
                                             DEFAULT_STRESS_TEST_ITERATIONS)

            test_settings = {
                'test_name':
                config_settings.get(
                    'test_name',
                    'test_soft_ap_and_client_mode_alternating_stress_%s_iterations'
                    % iterations),
                'iterations':
                iterations,
                'soft_ap_params':
                soft_ap_params,
                'ap_params':
                ap_params,
            }

            test_settings_list.append(test_settings)
        self.run_generated_testcases(
            test_func=self.run_soft_ap_and_client_mode_alternating_test,
            settings=test_settings_list,
            name_func=get_test_name_from_settings)

    def test_soft_ap_toggle_stress(self):
        """ Runs SoftAP toggling stress test.

        Each iteration toggles SoftAP to the opposite state (up or down).

        If toggled up, a client is associated and connection is verified
        If toggled down, test verifies client is not connected

        Will run with default params, but custom tests can be provided in the
        ACTS config.

        Example Config
        "soft_ap_test_params" : {
            "test_soft_ap_toggle_stress": [
                "soft_ap_params": {
                    "security_type": "wpa2",
                    "password": "password",
                    "connectivity_mode": "local_only",
                    "operating_band": "only_2_4_ghz",
                },
                "iterations": 10
            ]
        }
        """
        tests = self.soft_ap_test_params.get(
            'test_soft_ap_toggle_stress',
            [dict(test_name='test_soft_ap_toggle_stress_default')])

        test_settings_list = []
        for config_settings in tests:
            soft_ap_params = get_soft_ap_params_from_config_or_default(
                config_settings.get('soft_ap_params', {}))
            iterations = config_settings.get('iterations',
                                             DEFAULT_STRESS_TEST_ITERATIONS)
            test_settings = {
                'test_name':
                config_settings.get(
                    'test_name',
                    'test_soft_ap_toggle_stress_%s_iterations' % iterations),
                'test_runner_func':
                self.soft_ap_toggle_test_iteration,
                'soft_ap_params':
                soft_ap_params,
                'iterations':
                iterations
            }
            test_settings_list.append(test_settings)

        self.run_generated_testcases(self.run_toggle_stress_test,
                                     test_settings_list,
                                     name_func=get_test_name_from_settings)

    def test_client_mode_toggle_stress(self):
        """ Runs client mode toggling stress test.

        Each iteration toggles client mode to the opposite state (up or down).

        If toggled up, DUT associates to AP, and connection is verified
        If toggled down, test verifies DUT is not connected to AP

        Will run with default params, but custom tests can be provided in the
        ACTS config.

        Example Config
        "soft_ap_test_params" : {
            "test_client_mode_toggle_stress": [
                "soft_ap_params": {
                    'ssid': ssid,
                    'channel': channel,
                    'security_mode': security,
                    'password': password
                },
                "iterations": 10
            ]
        }
        """
        asserts.skip_if(not self.access_point, 'No access point provided.')
        tests = self.soft_ap_test_params.get(
            'test_client_mode_toggle_stress',
            [dict(test_name='test_client_mode_toggle_stress_default')])

        test_settings_list = []
        for config_settings in tests:
            ap_params = get_ap_params_from_config_or_default(
                config_settings.get('ap_params', {}))
            iterations = config_settings.get('iterations',
                                             DEFAULT_STRESS_TEST_ITERATIONS)
            test_settings = {
                'test_name':
                config_settings.get(
                    'test_name',
                    'test_client_mode_toggle_stress_%s_iterations' %
                    iterations),
                'test_runner_func':
                self.client_mode_toggle_test_iteration,
                'pre_test_func':
                self.client_mode_toggle_pre_test,
                'ap_params':
                ap_params,
                'iterations':
                iterations
            }
            test_settings_list.append(test_settings)
        self.run_generated_testcases(self.run_toggle_stress_test,
                                     test_settings_list,
                                     name_func=get_test_name_from_settings)

    def test_soft_ap_toggle_stress_with_client_mode(self):
        """Same as test_soft_ap_toggle_stress, but client mode is set up
        at test start and verified after every toggle."""
        asserts.skip_if(not self.access_point, 'No access point provided.')
        tests = self.soft_ap_test_params.get(
            'test_soft_ap_toggle_stress_with_client_mode', [
                dict(test_name=
                     'test_soft_ap_toggle_stress_with_client_mode_default')
            ])

        test_settings_list = []
        for config_settings in tests:
            soft_ap_params = get_soft_ap_params_from_config_or_default(
                config_settings.get('soft_ap_params', {}))
            ap_params = get_ap_params_from_config_or_default(
                config_settings.get('ap_params', {}))
            iterations = config_settings.get('iterations',
                                             DEFAULT_STRESS_TEST_ITERATIONS)
            test_settings = {
                'test_name':
                config_settings.get(
                    'test_name',
                    'test_soft_ap_toggle_stress_with_client_mode_%s_iterations'
                    % iterations),
                'test_runner_func':
                self.soft_ap_toggle_with_client_mode_iteration,
                'pre_test_func':
                self.soft_ap_toggle_with_client_mode_pre_test,
                'soft_ap_params':
                soft_ap_params,
                'ap_params':
                ap_params,
                'iterations':
                iterations
            }
            test_settings_list.append(test_settings)
        self.run_generated_testcases(self.run_toggle_stress_test,
                                     test_settings_list,
                                     name_func=get_test_name_from_settings)

    def test_client_mode_toggle_stress_with_soft_ap(self):
        """Same as test_client_mode_toggle_stress, but softap is set up at
        test start and verified after every toggle."""
        asserts.skip_if(not self.access_point, 'No access point provided.')
        tests = self.soft_ap_test_params.get(
            'test_client_mode_toggle_stress_with_soft_ap', [
                dict(test_name=
                     'test_client_mode_toggle_stress_with_soft_ap_default')
            ])

        test_settings_list = []
        for config_settings in tests:
            soft_ap_params = get_soft_ap_params_from_config_or_default(
                config_settings.get('soft_ap_params', {}))
            ap_params = get_ap_params_from_config_or_default(
                config_settings.get('ap_params', {}))
            iterations = config_settings.get('iterations',
                                             DEFAULT_STRESS_TEST_ITERATIONS)
            test_settings = {
                'test_name':
                config_settings.get(
                    'test_name',
                    'test_client_mode_toggle_stress_with_soft_ap_%s_iterations'
                    % iterations),
                'test_runner_func':
                self.client_mode_toggle_with_soft_ap_iteration,
                'pre_test_func':
                self.client_mode_toggle_with_soft_ap_pre_test,
                'soft_ap_params':
                soft_ap_params,
                'ap_params':
                ap_params,
                'iterations':
                iterations
            }
            test_settings_list.append(test_settings)
        self.run_generated_testcases(self.run_toggle_stress_test,
                                     test_settings_list,
                                     name_func=get_test_name_from_settings)

    def test_soft_ap_and_client_mode_random_toggle_stress(self):
        """Same as above toggle stres tests, but each iteration, either softap,
        client mode, or both are toggled, then states are verified."""
        asserts.skip_if(not self.access_point, 'No access point provided.')
        tests = self.soft_ap_test_params.get(
            'test_soft_ap_and_client_mode_random_toggle_stress', [
                dict(
                    test_name=
                    'test_soft_ap_and_client_mode_random_toggle_stress_default'
                )
            ])

        test_settings_list = []
        for config_settings in tests:
            soft_ap_params = get_soft_ap_params_from_config_or_default(
                config_settings.get('soft_ap_params', {}))
            ap_params = get_ap_params_from_config_or_default(
                config_settings.get('ap_params', {}))
            iterations = config_settings.get('iterations',
                                             DEFAULT_STRESS_TEST_ITERATIONS)
            test_settings = {
                'test_name':
                config_settings.get(
                    'test_name',
                    'test_soft_ap_and_client_mode_random_toggle_stress_%s_iterations'
                    % iterations),
                'soft_ap_params':
                soft_ap_params,
                'ap_params':
                ap_params,
                'iterations':
                iterations
            }
            test_settings_list.append(test_settings)
        self.run_generated_testcases(
            self.run_soft_ap_and_client_mode_random_toggle_stress_test,
            test_settings_list,
            name_func=get_test_name_from_settings)
