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

import itertools
import os
import time

from multiprocessing import Process

from acts import asserts
from acts import context
from acts import utils
from acts.controllers import iperf_client
from acts.controllers import iperf_server
from acts.controllers.access_point import setup_ap
from acts.controllers.ap_lib import hostapd_constants
from acts.controllers.ap_lib.hostapd_security import Security
from acts.controllers.ap_lib.hostapd_utils import generate_random_password
from acts.controllers.ap_lib.radvd import Radvd
from acts.controllers.ap_lib import radvd_constants
from acts.controllers.ap_lib.radvd_config import RadvdConfig
from acts.controllers.fuchsia_device import IP_ADDRESS_TIMEOUT
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest

# Constants, for readibility
AP = 'ap'
DUT = 'dut'
DEVICES = [AP, DUT]

SOFT = 'soft'
HARD = 'hard'
REBOOT_TYPES = [SOFT, HARD]

BAND_2G = '2g'
BAND_5G = '5g'
BANDS = [BAND_2G, BAND_5G]

IPV4 = 'ipv4'
IPV6 = 'ipv6'
DUAL_IPV4_IPV6 = {IPV4: True, IPV6: True}
IPV4_ONLY = {IPV4: True, IPV6: False}
IPV6_ONLY = {IPV4: False, IPV6: True}
IP_VERSIONS = [IPV4_ONLY, IPV6_ONLY, DUAL_IPV4_IPV6]

INTERRUPTS = [True, False]
OPEN_ENCRYPTION_STRING = "open"
SECURITY_MODES = [
    OPEN_ENCRYPTION_STRING, hostapd_constants.WPA2_STRING,
    hostapd_constants.WPA3_STRING
]

DEFAULT_IPERF_TIMEOUT = 30

DUT_NETWORK_CONNECTION_TIMEOUT = 60

# Constants for Custom Reboot Tests
ALL = 'all'
BOTH = 'both'

CUSTOM_TEST_REBOOT_DEVICES = {AP: [AP], DUT: [DUT], ALL: [AP, DUT]}
CUSTOM_TEST_REBOOT_TYPES = {SOFT: [SOFT], HARD: [HARD], ALL: [SOFT, HARD]}
CUSTOM_TEST_BANDS = {
    BAND_2G: [BAND_2G],
    BAND_5G: [BAND_5G],
    ALL: [BAND_2G, BAND_5G]
}
CUSTOM_TEST_IP_VERSIONS = {
    IPV4: [IPV4_ONLY],
    IPV6: [IPV6_ONLY],
    BOTH: [DUAL_IPV4_IPV6],
    ALL: [IPV4_ONLY, IPV6_ONLY, DUAL_IPV4_IPV6]
}
CUSTOM_TEST_INTERRUPTS = {'true': [True], 'false': [False], ALL: [True, False]}


class WlanRebootTest(WifiBaseTest):
    """Tests wlan reconnects in different reboot scenarios.

    Testbed Requirement:
    * One ACTS compatible device (dut)
    * One Whirlwind Access Point (will also serve as iperf server)
    * One PduDevice
    """

    def __init__(self, controllers):
        super().__init__(controllers)

    def setup_generated_tests(self):
        self._read_wlan_reboot_test_params()
        self.generate_tests(test_logic=self.run_reboot_test,
                            name_func=self.generate_test_name,
                            arg_sets=self.generate_test_args())

    def setup_class(self):
        super().setup_class()
        self.android_devices = getattr(self, 'android_devices', [])
        self.fuchsia_devices = getattr(self, 'fuchsia_devices', [])

        if 'dut' in self.user_params:
            if self.user_params['dut'] == 'fuchsia_devices':
                self.dut = create_wlan_device(self.fuchsia_devices[0])
            elif self.user_params['dut'] == 'android_devices':
                self.dut = create_wlan_device(self.android_devices[0])
            else:
                raise ValueError('Invalid DUT specified in config. (%s)' %
                                 self.user_params['dut'])
        else:
            # Default is an android device, just like the other tests
            self.dut = create_wlan_device(self.android_devices[0])

        self.access_point = self.access_points[0]

        self.iperf_server_on_ap = None
        self.iperf_client_on_dut = None
        if not self.skip_iperf:
            if hasattr(self, "iperf_clients") and self.iperf_clients:
                self.iperf_client_on_dut = self.iperf_clients[0]
            else:
                self.iperf_client_on_dut = self.dut.create_iperf_client()
        else:
            self.log.info(
                'Skipping iperf throughput validation as requested by ACTS '
                'config')

        self.router_adv_daemon = None

    def setup_test(self):
        self.access_point.stop_all_aps()
        if self.router_adv_daemon:
            self.router_adv_daemon.stop()
        self.dut.wifi_toggle_state(True)
        for ad in self.android_devices:
            ad.droid.wakeLockAcquireBright()
            ad.droid.wakeUpNow()
        self.dut.disconnect()
        self.router_adv_daemon = None
        self.ssid = utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G)

    def teardown_test(self):
        self.download_ap_logs()
        self.access_point.stop_all_aps()
        if self.router_adv_daemon:
            output_path = context.get_current_context().get_base_output_path()
            full_output_path = os.path.join(output_path, "radvd_log.txt")
            radvd_log_file = open(full_output_path, 'w')
            radvd_log_file.write(self.router_adv_daemon.pull_logs())
            radvd_log_file.close()
            self.router_adv_daemon.stop()
            self.router_adv_daemon = None
        self.dut.disconnect()
        for ad in self.android_devices:
            ad.droid.wakeLockRelease()
            ad.droid.goToSleepNow()
        self.dut.turn_location_off_and_scan_toggle_off()
        self.dut.reset_wifi()

    def setup_ap(self,
                 ssid,
                 band,
                 ipv4=True,
                 ipv6=False,
                 security_mode=None,
                 password=None):
        """Setup ap with basic config.

        Args:
            ssid: string, ssid to setup on ap
            band: string ('2g' or '5g') of band to setup.
            ipv4: True if using ipv4 (dhcp), else False.
            ipv6: True if using ipv6 (radvd), else False.
        """
        # TODO(fxb/63719): Add varying AP parameters
        security_profile = None
        if security_mode:
            security_profile = Security(security_mode=security_mode,
                                        password=password)
        if band == BAND_2G:
            setup_ap(access_point=self.access_point,
                     profile_name='whirlwind',
                     channel=11,
                     ssid=ssid,
                     security=security_profile)
        elif band == BAND_5G:
            setup_ap(access_point=self.access_point,
                     profile_name='whirlwind',
                     channel=36,
                     ssid=ssid,
                     security=security_profile)

        if not ipv4:
            self.access_point.stop_dhcp()
        if ipv6:
            if band == BAND_2G:
                self.router_adv_daemon = Radvd(self.access_point.ssh,
                                               self.access_point.wlan_2g)
            elif band == BAND_5G:
                self.router_adv_daemon = Radvd(self.access_point.ssh,
                                               self.access_point.wlan_5g)
            radvd_config = RadvdConfig()
            self.router_adv_daemon.start(radvd_config)

        self.log.info('Network (SSID: %s) is up.' % ssid)

    def setup_iperf_server_on_ap(self, band):
        """Configures iperf server based on the tests band.

        Args:
            band: string ('2g' or '5g') of band to setup.
        """
        if band == BAND_2G:
            return iperf_server.IPerfServerOverSsh(
                self.user_params['AccessPoint'][0]['ssh_config'],
                5201,
                test_interface=self.access_point.wlan_2g)
        elif band == BAND_5G:
            return iperf_server.IPerfServerOverSsh(
                self.user_params['AccessPoint'][0]['ssh_config'],
                5201,
                test_interface=self.access_point.wlan_5g)

    def get_iperf_server_address(self, iperf_server_on_ap, ip_version):
        """Retrieves the ip address of the iperf server.

        Args:
            iperf_server_on_ap: IPerfServer object, linked to AP
            ip_version: string, the ip version (ipv4 or ipv6)

        Returns:
            String, the ip address of the iperf_server
        """
        iperf_server_addresses = iperf_server_on_ap.get_interface_ip_addresses(
            iperf_server_on_ap.test_interface)
        if ip_version == IPV4:
            iperf_server_ip_address = (
                iperf_server_addresses['ipv4_private'][0])
        elif ip_version == IPV6:
            if iperf_server_addresses['ipv6_private_local']:
                iperf_server_ip_address = (
                    iperf_server_addresses['ipv6_private_local'][0])
            else:
                iperf_server_ip_address = (
                    '%s%%%s' % (iperf_server_addresses['ipv6_link_local'][0],
                                self.iperf_client_on_dut.test_interface))
        else:
            raise ValueError('Invalid IP version: %s' % ip_version)

        return iperf_server_ip_address

    def verify_traffic_between_dut_and_ap(self,
                                          iperf_server_on_ap,
                                          iperf_client_on_dut,
                                          ip_version=IPV4):
        """Runs IPerf traffic from the iperf client (dut) and the iperf
        server (and vice versa) and verifies traffic was able to pass
        successfully.

        Args:
            iperf_server_on_ap: IPerfServer object, linked to AP
            iperf_client_on_dut: IPerfClient object, linked to DUT
            ip_version: string, the ip version (ipv4 or ipv6)

        Raises:
            ValueError, if invalid ip_version is passed.
            ConnectionError, if traffic is not passed successfully in both
                directions.
        """
        dut_ip_addresses = self.dut.device.get_interface_ip_addresses(
            iperf_client_on_dut.test_interface)

        iperf_server_ip_address = self.get_iperf_server_address(
            iperf_server_on_ap, ip_version)

        self.log.info(
            'Attempting to pass traffic from DUT to IPerf server (%s).' %
            iperf_server_ip_address)
        tx_file = iperf_client_on_dut.start(iperf_server_ip_address,
                                            '-i 1 -t 3 -J',
                                            'reboot_tx',
                                            timeout=DEFAULT_IPERF_TIMEOUT)
        tx_results = iperf_server.IPerfResult(tx_file)
        if not tx_results.avg_receive_rate or tx_results.avg_receive_rate == 0:
            raise ConnectionError(
                'Failed to pass IPerf traffic from DUT to server (%s). TX '
                'Average Receive Rate: %s' %
                (iperf_server_ip_address, tx_results.avg_receive_rate))
        else:
            self.log.info(
                'Success: Traffic passed from DUT to IPerf server (%s).' %
                iperf_server_ip_address)
        self.log.info(
            'Attempting to pass traffic from IPerf server (%s) to DUT.' %
            iperf_server_ip_address)
        rx_file = iperf_client_on_dut.start(iperf_server_ip_address,
                                            '-i 1 -t 3 -R -J',
                                            'reboot_rx',
                                            timeout=DEFAULT_IPERF_TIMEOUT)
        rx_results = iperf_server.IPerfResult(rx_file)
        if not rx_results.avg_receive_rate or rx_results.avg_receive_rate == 0:
            raise ConnectionError(
                'Failed to pass IPerf traffic from server (%s) to DUT. RX '
                'Average Receive Rate: %s' %
                (iperf_server_ip_address, rx_results.avg_receive_rate))
        else:
            self.log.info(
                'Success: Traffic passed from IPerf server (%s) to DUT.' %
                iperf_server_ip_address)

    def start_dut_ping_process(self, iperf_server_on_ap, ip_version=IPV4):
        """Creates a  process that pings the AP from the DUT.

        Runs in parallel for 15 seconds, so it can be interrupted by a reboot.
        Sleeps for a few seconds to ensure pings have started.

        Args:
            iperf_server_on_ap: IPerfServer object, linked to AP
            ip_version: string, the ip version (ipv4 or ipv6)
        """
        ap_address = self.get_iperf_server_address(iperf_server_on_ap,
                                                   ip_version)
        if ap_address:
            self.log.info(
                'Starting ping process to %s in parallel. Logs from this '
                'process will be suppressed, since it will be intentionally '
                'interrupted.' % ap_address)
            ping_proc = Process(target=self.dut.ping,
                                args=[ap_address],
                                kwargs={'count': 15})
            with utils.SuppressLogOutput():
                ping_proc.start()
            # Allow for a few seconds of pinging before allowing it to be
            # interrupted.
            time.sleep(3)
        else:
            raise ConnectionError('Failed to retrieve APs iperf address.')

    def prepare_dut_for_reconnection(self):
        """Perform any actions to ready DUT for reconnection.

        These actions will vary depending on the DUT. eg. android devices may
        need to be woken up, ambient devices should not require any interaction,
        etc.
        """
        self.dut.wifi_toggle_state(True)
        for ad in self.android_devices:
            ad.droid.wakeUpNow()

    def wait_for_dut_network_connection(self, ssid):
        """Checks if device is connected to given network. Sleeps 1 second
        between retries.

        Args:
            ssid: string of ssid
        Raises:
            ConnectionError, if DUT is not connected after all timeout.
        """
        self.log.info(
            'Checking if DUT is connected to %s network. Will retry for %s '
            'seconds.' % (ssid, self.dut_network_connection_timeout))
        timeout = time.time() + self.dut_network_connection_timeout
        while time.time() < timeout:
            try:
                is_connected = self.dut.is_connected(ssid=ssid)
            except Exception as err:
                self.log.debug('SL4* call failed. Retrying in 1 second.')
                is_connected = False
            finally:
                if is_connected:
                    self.log.info('Success: DUT has connected.')
                    break
                else:
                    self.log.debug(
                        'DUT not connected to network %s...retrying in 1 second.'
                        % ssid)
                    time.sleep(1)
        else:
            raise ConnectionError('DUT failed to connect to the network.')

    def write_csv_time_to_reconnect(self, test_name, time_to_reconnect):
        """Writes the time to reconnect to a csv file.
        Args:
            test_name: the name of the test case
            time_to_reconnect: the time from when the rebooted device came back
                up to when it reassociated (or 'FAIL'), if it failed to
                reconnect.
        """
        log_context = context.get_current_context()
        log_path = os.path.join(log_context.get_base_output_path(),
                                'WlanRebootTest/')
        csv_file_name = '%stime_to_reconnect.csv' % log_path
        self.log.info('Writing to %s' % csv_file_name)
        with open(csv_file_name, 'a') as csv_file:
            csv_file.write('%s,%s\n' % (test_name, time_to_reconnect))

    def log_and_continue(self, run, time_to_reconnect=None, error=None):
        """Writes the time to reconnect to the csv file before continuing, used
        in stress tests runs.

        Args:
            time_to_reconnect: the time from when the rebooted device came back
                ip to when reassociation occurred.
            run: the run number in a looped stress tested.,
            error: string, error message to log before continuing with the test
        """
        if error:
            self.log.info(
                'Device failed to reconnect to network %s on run %s. Error: %s'
                % (self.ssid, run, error))
            self.write_csv_time_to_reconnect(
                '%s_run_%s' % (self.test_name, run), 'FAIL')

        else:
            self.log.info(
                'Device successfully reconnected to network %s after %s seconds'
                ' on run %s.' % (self.ssid, time_to_reconnect, run))
            self.write_csv_time_to_reconnect(
                '%s_run_%s' % (self.test_name, run), time_to_reconnect)

    def run_reboot_test(self, settings):
        """Runs a reboot test based on a given config.
            1. Setups up a network, associates the dut, and saves the network.
            2. Verifies the dut receives ip address(es).
            3. Verifies traffic between DUT and AP (IPerf client and server).
            4. Reboots (hard or soft) the device (dut or ap).
                - If the ap was rebooted, setup the same network again.
            5. Wait for reassociation or timeout.
            6. If reassocation occurs:
                - Verifies the dut receives ip address(es).
                - Verifies traffic between DUT and AP (IPerf client and server).
            7. Logs time to reconnect (or failure to reconnect)
            8. If stress testing, repeats steps 4 - 7 for N iterations.

        Args:
            settings: dictionary containing the following values:
                reboot_device: string ('dut' or 'ap') of the device to reboot.
                reboot_type: string ('soft' or 'hard') of how to reboot the
                    reboot_device.
                band: string ('2g' or '5g') of band to setup.
                ipv4: True if using ipv4 (dhcp), else False.
                ipv6: True if using ipv6 (radvd), else False.

                Optional:
                    interrupt: if True, the DUT will be pinging the AP in a
                        parallel process when the reboot occurs. This is used to
                        compare reconnect times when idle to active.
                    test_name: name of the test, used when stress testing.
                    iterations: number of times to perform test, used when stress
                        testing.

        Raises:
            ValueError, if ipv4 and ipv6 are both False
            ValueError, if band is not '2g' or '5g'
            ValueError, if reboot_device is not 'dut' or 'ap'
            ValueError, if reboot_type is not 'soft' or 'hard'

        """
        iterations = settings.get('iterations', 1)
        passed_count = 0
        ipv4 = settings.get('ipv4', None)
        ipv6 = settings.get('ipv6', None)
        reboot_device = settings['reboot_device']
        reboot_type = settings['reboot_type']
        band = settings['band']
        security_mode = settings.get('security_mode', None)
        password = settings.get('password', None)
        if security_mode:
            if security_mode.lower() == 'open':
                security_mode = None
            elif not password:
                password = generate_random_password(
                    security_mode=security_mode)
        interrupt = settings.get('interrupt', None)
        # Skip hard reboots if no PDU present
        asserts.skip_if(
            reboot_type == HARD
            and len(self.user_params.get('PduDevice', [])) < 1,
            'Hard reboots require a PDU device.')
        # Skip DUT reboot w/ interrupt tests, since they are not more helpful
        # and may cause threading issues.
        asserts.skip_if(
            (reboot_device == DUT) and interrupt,
            'Stream interrupts for DUT reboots are prone to threading issues '
            'and are not supported.')

        # Validate test settings.
        if not ipv4 and not ipv6:
            raise ValueError('Either ipv4, ipv6, or both must be True.')
        if reboot_device != DUT and reboot_device != AP:
            raise ValueError('Invalid reboot device: %s' % reboot_device)
        if reboot_type != SOFT and reboot_type != HARD:
            raise ValueError('Invalid reboot type: %s' % reboot_type)
        if band != BAND_2G and band != BAND_5G:
            raise ValueError('Invalid band: %s' % band)

        self.setup_ap(self.ssid, band, ipv4, ipv6, security_mode, password)
        if not self.dut.associate(
                self.ssid,
                target_security=hostapd_constants.
                SECURITY_STRING_TO_DEFAULT_TARGET_SECURITY.get(security_mode),
                target_pwd=password):
            raise EnvironmentError('Initial network connection failed.')

        if not self.skip_iperf:
            dut_test_interface = self.iperf_client_on_dut.test_interface
            if ipv4:
                self.dut.device.wait_for_ipv4_addr(dut_test_interface)
            if ipv6:
                self.dut.device.wait_for_ipv6_addr(dut_test_interface)

            self.iperf_server_on_ap = self.setup_iperf_server_on_ap(band)
            self.iperf_server_on_ap.start()

            if ipv4:
                self.verify_traffic_between_dut_and_ap(
                    self.iperf_server_on_ap, self.iperf_client_on_dut)
            if ipv6:
                self.verify_traffic_between_dut_and_ap(
                    self.iperf_server_on_ap,
                    self.iperf_client_on_dut,
                    ip_version=IPV6)

        # Looping reboots for stress testing
        for run in range(iterations):
            run += 1
            self.log.info('Starting run %s of %s.' % (run, iterations))

            # Ping from DUT to AP during AP reboot
            if interrupt:
                if ipv4:
                    self.start_dut_ping_process(self.iperf_server_on_ap)
                if ipv6:
                    self.start_dut_ping_process(self.iperf_server_on_ap,
                                                ip_version=IPV6)

            # DUT reboots
            if reboot_device == DUT:
                if not self.skip_iperf and type(
                        self.iperf_client_on_dut
                ) == iperf_client.IPerfClientOverSsh:
                    self.iperf_client_on_dut.close_ssh()
                if reboot_type == SOFT:
                    self.dut.device.reboot()
                elif reboot_type == HARD:
                    self.dut.hard_power_cycle(self.pdu_devices)

            # AP reboots
            elif reboot_device == AP:
                if reboot_type == SOFT:
                    self.log.info('Cleanly stopping ap.')
                    self.access_point.stop_all_aps()
                elif reboot_type == HARD:
                    if not self.skip_iperf:
                        self.iperf_server_on_ap.close_ssh()
                    self.access_point.hard_power_cycle(self.pdu_devices)
                self.setup_ap(self.ssid, band, ipv4, ipv6, security_mode,
                              password)

            self.prepare_dut_for_reconnection()
            uptime = time.time()
            try:
                self.wait_for_dut_network_connection(self.ssid)
                time_to_reconnect = time.time() - uptime

                if not self.skip_iperf:
                    if ipv4:
                        self.dut.device.wait_for_ipv4_addr(dut_test_interface)
                    if ipv6:
                        self.dut.device.wait_for_ipv6_addr(dut_test_interface)

                    self.iperf_server_on_ap.start()

                    if ipv4:
                        self.verify_traffic_between_dut_and_ap(
                            self.iperf_server_on_ap, self.iperf_client_on_dut)
                    if ipv6:
                        self.verify_traffic_between_dut_and_ap(
                            self.iperf_server_on_ap,
                            self.iperf_client_on_dut,
                            ip_version=IPV6)

            except ConnectionError as err:
                self.log_and_continue(run, error=err)
            else:
                passed_count += 1
                self.log_and_continue(run, time_to_reconnect=time_to_reconnect)

        if passed_count == iterations:
            asserts.explicit_pass(
                'Test Summary: device successfully reconnected to network %s '
                '%s/%s times.' % (self.ssid, passed_count, iterations))

        else:
            asserts.fail(
                'Test Summary: device failed reconnection test. Reconnected to '
                'network %s %s/%s times.' %
                (self.ssid, passed_count, iterations))

    def generate_test_name(self, settings):
        """Generates a test case name based on the reboot settings passed.

        Args:
            settings: A dictionary of settings related to reboot test.

        Returns:
            A string that represents a test case name.
        """
        test_name = "test_{reboot_type}_reboot_{reboot_device}_{band}_{security_mode}".format(
            **settings)

        if settings.get(IPV4):
            test_name += "_ipv4"

        if settings.get(IPV6):
            test_name += "_ipv6"

        if settings.get('interrupt'):
            test_name += "_interrupt"

        if settings.get('iterations'):
            test_name += f"_with_{settings['iterations']}_iterations"

        return test_name

    def generate_test_args(self):
        # If custom reboot tests present in ACTS config then run just those
        test_args = self._generate_custom_reboots_test_args()
        if test_args:
            return test_args

        # Interrupt tests requires using iperf. So do not run interrupt tests
        # when skip_iperf is True
        if self.skip_iperf is True:
            interrupts = [False]
        else:
            interrupts = INTERRUPTS

        for (reboot_device, reboot_type, band, ip_version, interrupt,
             security_mode) in itertools.product(DEVICES, REBOOT_TYPES, BANDS,
                                                 IP_VERSIONS, interrupts,
                                                 SECURITY_MODES):
            settings = {
                "reboot_device": reboot_device,
                "reboot_type": reboot_type,
                "band": band,
                "security_mode": security_mode,
                "ipv4": ip_version["ipv4"],
                "ipv6": ip_version["ipv6"],
                "interrupt": interrupt,
            }
            test_args.append((settings, ))

        return test_args

    def _generate_custom_reboots_test_args(self):
        """Used to create custom reboot tests from ACTS config. Can be
        individual tests or permutation sets (i.e. setting "all" for a
        test param will run a test with every permutation).

        Parameters:
            reboot_device: string - "ap", "dut", or "all"
            reboot_type: string - "soft", "hard", or "all"
            band: string, "2g" - "5g", "all"
            ip_version: string - "ipv4", "ipv6", "both", or "all"
            interrupt: bool - whether to have traffic flowing at reboot
            security_modes: optional, string or list - "open", "wep", "wpa",
                "wpa2", "wpa/wpa2", "wpa3", "wpa2/wpa3"
            iterations: int - number of iterations for each test

        Example:
        "wlan_reboot_test_params": {
            "test_custom_reboots": [
                {
                    "reboot_device": "dut",
                    "reboot_type": "soft",
                    "band": "2g",
                    "ip_version": "both"
                },
                {
                    "reboot_device": "all",
                    "reboot_type": "hard",
                    "band": "all",
                    "ip_version": ipv4",
                    "security_modes": "wpa2",
                    "iterations": 10
                },
                {
                    "reboot_device": "dut",
                    "reboot_type": "hard",
                    "band": "5g",
                    "ip_version": "ipv4",
                    "security_modes": ["open", "wpa3"]
                }
            ]
        }

        The first example runs a single DUT soft reboot test with a 2.4GHz
        network and dual ipv4/ipv6.

        The second example runs 4 tests, each with 10 iterations. It runs hard
        reboots with ipv4 for the permutations of DUT/AP and 2.4GHz/5GHz.

        The third example runs two tests, both hard reboots of the DUT with 5g
        and ipv4 only, one with open security and one with WPA3.
        """
        if 'test_custom_reboots' not in self.wlan_reboot_test_params:
            self.log.info('No custom reboots provided in ACTS config.')
            return []

        test_args = []
        for test in self.wlan_reboot_test_params['test_custom_reboots']:
            # Ensure required params are present
            try:
                reboot_device = test['reboot_device'].lower()
                reboot_type = test['reboot_type'].lower()
                band = test['band'].lower()
                ip_version = test['ip_version'].lower()
            except KeyError as err:
                raise AttributeError(
                    'Must provide reboot_type, reboot_device, ip_version, and '
                    'band (optionally interrupt and iterations) in custom test '
                    'config. See test_custom_reboots docstring for details. '
                    'Err: %s' % err)
            security_modes = test.get('security_modes', 'open')
            interrupt = str(test.get('interrupt', False)).lower()
            iterations = test.get('iterations', 1)

            if interrupt == "true" and self.skip_iperf:
                raise AttributeError(
                    "Interrupt can't be set to True when iperf is disabled. "
                    "Update 'skip_iperf' to 'false' in ACTS config and run again"
                )

            # Validate parameters and convert to lists (for permutations)
            try:
                reboot_devices = CUSTOM_TEST_REBOOT_DEVICES[reboot_device]
                reboot_types = CUSTOM_TEST_REBOOT_TYPES[reboot_type]
                bands = CUSTOM_TEST_BANDS[band]
                ip_versions = CUSTOM_TEST_IP_VERSIONS[ip_version]
                interrupts = CUSTOM_TEST_INTERRUPTS[interrupt]
                if isinstance(security_modes, str):
                    security_modes = [security_modes]
            except KeyError as err:
                raise AttributeError(
                    'Invalid custom test parameter provided. Err: %s' % err)

            for (reboot_device, reboot_type, band, ip_version, interrupt,
                 security_mode) in itertools.product(reboot_devices,
                                                     reboot_types, bands,
                                                     ip_versions, interrupts,
                                                     security_modes):
                settings = {
                    "reboot_device": reboot_device,
                    "reboot_type": reboot_type,
                    "band": band,
                    "security_mode": security_mode,
                    "ipv4": ip_version[IPV4],
                    "ipv6": ip_version[IPV6],
                    "interrupt": interrupt,
                    "iterations": iterations,
                }

                test_args.append((settings, ))
        return test_args

    def _read_wlan_reboot_test_params(self):
        self.wlan_reboot_test_params = self.user_params.get(
            'wlan_reboot_test_params', {})
        self.skip_iperf = self.wlan_reboot_test_params.get('skip_iperf', False)
        # Times (in seconds) to wait for DUT network connection and assigning an
        # ip address to the wlan interface.
        self.dut_network_connection_timeout = self.wlan_reboot_test_params.get(
            'dut_network_connection_timeout', DUT_NETWORK_CONNECTION_TIMEOUT)
        self.dut_ip_address_timeout = self.wlan_reboot_test_params.get(
            'dut_ip_address_timeout', IP_ADDRESS_TIMEOUT)
