#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
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

import random
import time
import re

from acts import asserts
from acts import utils
from acts.controllers.access_point import setup_ap, AccessPoint
from acts.controllers.ap_lib import dhcp_config
from acts.controllers.ap_lib import hostapd_constants
from acts.controllers.ap_lib.hostapd_security import Security
from acts.controllers.ap_lib.hostapd_utils import generate_random_password
from acts.controllers.utils_lib.commands import ip
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest


class Dhcpv4InteropFixture(WifiBaseTest):
    """Test helpers for validating DHCPv4 Interop

    Test Bed Requirement:
    * One Android device or Fuchsia device
    * One Access Point
    """
    access_point: AccessPoint

    def setup_class(self):
        super().setup_class()
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
        self.access_point.stop_all_aps()

    def setup_test(self):
        if hasattr(self, "android_devices"):
            for ad in self.android_devices:
                ad.droid.wakeLockAcquireBright()
                ad.droid.wakeUpNow()
        self.dut.wifi_toggle_state(True)

    def teardown_test(self):
        if hasattr(self, "android_devices"):
            for ad in self.android_devices:
                ad.droid.wakeLockRelease()
                ad.droid.goToSleepNow()
        self.dut.turn_location_off_and_scan_toggle_off()
        self.dut.disconnect()
        self.dut.reset_wifi()
        self.access_point.stop_all_aps()

    def connect(self, ap_params):
        asserts.assert_true(
            self.dut.associate(ap_params['ssid'],
                               target_pwd=ap_params['password'],
                               target_security=ap_params['target_security']),
            'Failed to connect.')

    def setup_ap(self):
        """Generates a hostapd config and sets up the AP with that config.
        Does not run a DHCP server.

        Returns: A dictionary of information about the AP.
        """
        ssid = utils.rand_ascii_str(20)
        security_mode = hostapd_constants.WPA2_STRING
        security_profile = Security(
            security_mode=security_mode,
            password=generate_random_password(length=20),
            wpa_cipher='CCMP',
            wpa2_cipher='CCMP')
        password = security_profile.password
        target_security = hostapd_constants.SECURITY_STRING_TO_DEFAULT_TARGET_SECURITY.get(
            security_mode)

        ap_ids = setup_ap(access_point=self.access_point,
                          profile_name='whirlwind',
                          mode=hostapd_constants.MODE_11N_MIXED,
                          channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                          n_capabilities=[],
                          ac_capabilities=[],
                          force_wmm=True,
                          ssid=ssid,
                          security=security_profile,
                          password=password)

        if len(ap_ids) > 1:
            raise Exception("Expected only one SSID on AP")

        configured_subnets = self.access_point.get_configured_subnets()
        if len(configured_subnets) > 1:
            raise Exception("Expected only one subnet on AP")
        router_ip = configured_subnets[0].router
        network = configured_subnets[0].network

        self.access_point.stop_dhcp()

        return {
            'ssid': ssid,
            'password': password,
            'target_security': target_security,
            'ip': router_ip,
            'network': network,
            'id': ap_ids[0],
        }

    def device_can_ping(self, dest_ip):
        """Checks if the DUT can ping the given address.

        Returns: True if can ping, False otherwise"""
        self.log.info('Attempting to ping %s...' % dest_ip)
        ping_result = self.dut.can_ping(dest_ip, count=2)
        if ping_result:
            self.log.info('Success pinging: %s' % dest_ip)
        else:
            self.log.info('Failure pinging: %s' % dest_ip)
        return ping_result

    def get_device_ipv4_addr(self, interface=None, timeout=20):
        """Checks if device has an ipv4 private address. Sleeps 1 second between
        retries.

        Args:
            interface: string, name of interface from which to get ipv4 address.

        Raises:
            ConnectionError, if DUT does not have an ipv4 address after all
            timeout.

        Returns:
            The device's IP address

        """
        self.log.debug('Fetching updated WLAN interface list')
        if interface is None:
            interface = self.dut.device.wlan_client_test_interface_name
        self.log.info(
            'Checking if DUT has received an ipv4 addr on iface %s. Will retry for %s '
            'seconds.' % (interface, timeout))
        timeout = time.time() + timeout
        while time.time() < timeout:
            ip_addrs = self.dut.device.get_interface_ip_addresses(interface)

            if len(ip_addrs['ipv4_private']) > 0:
                ip = ip_addrs['ipv4_private'][0]
                self.log.info('DUT has an ipv4 address: %s' % ip)
                return ip
            else:
                self.log.debug(
                    'DUT does not yet have an ipv4 address...retrying in 1 '
                    'second.')
                time.sleep(1)
        else:
            raise ConnectionError('DUT failed to get an ipv4 address.')

    def run_test_case_expect_dhcp_success(self, settings):
        """Starts the AP and DHCP server, and validates that the client
        connects and obtains an address.

        Args:
            settings: a dictionary containing:
                dhcp_parameters: a dictionary of DHCP parameters
                dhcp_options: a dictionary of DHCP options
        """
        ap_params = self.setup_ap()
        subnet_conf = dhcp_config.Subnet(
            subnet=ap_params['network'],
            router=ap_params['ip'],
            additional_parameters=settings['dhcp_parameters'],
            additional_options=settings['dhcp_options'])
        dhcp_conf = dhcp_config.DhcpConfig(subnets=[subnet_conf])

        self.log.debug('DHCP Configuration:\n' +
                       dhcp_conf.render_config_file() + "\n")

        dhcp_logs_before = self.access_point.get_dhcp_logs().split('\n')
        self.access_point.start_dhcp(dhcp_conf=dhcp_conf)
        self.connect(ap_params=ap_params)

        # Typical log lines look like:
        # dhcpd[26695]: DHCPDISCOVER from f8:0f:f9:3d:ce:d1 via wlan1
        # dhcpd[26695]: DHCPOFFER on 192.168.9.2 to f8:0f:f9:3d:ce:d1 via wlan1
        # dhcpd[26695]: DHCPREQUEST for 192.168.9.2 (192.168.9.1) from f8:0f:f9:3d:ce:d1 via wlan1
        # dhcpd[26695]: DHCPACK on 192.168.9.2 to f8:0f:f9:3d:ce:d1 via wlan1

        try:
            ip = self.get_device_ipv4_addr()
        except ConnectionError:
            self.log.warn(dhcp_logs)
            asserts.fail(f'DUT failed to get an IP address')

        # Get updates to DHCP logs
        dhcp_logs = self.access_point.get_dhcp_logs()
        for line in dhcp_logs_before:
            dhcp_logs = dhcp_logs.replace(line, '')

        expected_string = f'DHCPDISCOVER from'
        asserts.assert_equal(
            dhcp_logs.count(expected_string), 1,
            f'Incorrect count of DHCP Discovers ("{expected_string}") in logs:\n'
            + dhcp_logs + "\n")

        expected_string = f'DHCPOFFER on {ip}'
        asserts.assert_equal(
            dhcp_logs.count(expected_string), 1,
            f'Incorrect count of DHCP Offers ("{expected_string}") in logs:\n'
            + dhcp_logs + "\n")

        expected_string = f'DHCPREQUEST for {ip}'
        asserts.assert_true(
            dhcp_logs.count(expected_string) >= 1,
            f'Incorrect count of DHCP Requests ("{expected_string}") in logs: '
            + dhcp_logs + "\n")

        expected_string = f'DHCPACK on {ip}'
        asserts.assert_true(
            dhcp_logs.count(expected_string) >= 1,
            f'Incorrect count of DHCP Acks ("{expected_string}") in logs: ' +
            dhcp_logs + "\n")

        asserts.assert_true(self.device_can_ping(ap_params['ip']),
                            f'DUT failed to ping router at {ap_params["ip"]}')


class Dhcpv4InteropFixtureTest(Dhcpv4InteropFixture):
    """Tests which validate the behavior of the Dhcpv4InteropFixture.

    In theory, these are more similar to unit tests than ACTS tests, but
    since they interact with hardware (specifically, the AP), we have to
    write and run them like the rest of the ACTS tests."""

    def test_invalid_options_not_accepted(self):
        """Ensures the DHCP server doesn't accept invalid options"""
        ap_params = self.setup_ap()
        subnet_conf = dhcp_config.Subnet(subnet=ap_params['network'],
                                         router=ap_params['ip'],
                                         additional_options={'foo': 'bar'})
        dhcp_conf = dhcp_config.DhcpConfig(subnets=[subnet_conf])
        with asserts.assert_raises_regex(Exception, r'failed to start'):
            self.access_point.start_dhcp(dhcp_conf=dhcp_conf)

    def test_invalid_parameters_not_accepted(self):
        """Ensures the DHCP server doesn't accept invalid parameters"""
        ap_params = self.setup_ap()
        subnet_conf = dhcp_config.Subnet(subnet=ap_params['network'],
                                         router=ap_params['ip'],
                                         additional_parameters={'foo': 'bar'})
        dhcp_conf = dhcp_config.DhcpConfig(subnets=[subnet_conf])
        with asserts.assert_raises_regex(Exception, r'failed to start'):
            self.access_point.start_dhcp(dhcp_conf=dhcp_conf)

    def test_no_dhcp_server_started(self):
        """Validates that the test fixture does not start a DHCP server."""
        ap_params = self.setup_ap()
        self.connect(ap_params=ap_params)
        with asserts.assert_raises(ConnectionError):
            self.get_device_ipv4_addr()


class Dhcpv4InteropBasicTest(Dhcpv4InteropFixture):
    """DhcpV4 tests which validate basic DHCP client/server interactions."""

    def test_basic_dhcp_assignment(self):
        self.run_test_case_expect_dhcp_success(settings={
            'dhcp_options': {},
            'dhcp_parameters': {}
        })

    def test_pool_allows_unknown_clients(self):
        self.run_test_case_expect_dhcp_success(settings={
            'dhcp_options': {},
            'dhcp_parameters': {
                'allow': 'unknown-clients'
            }
        })

    def test_pool_disallows_unknown_clients(self):
        ap_params = self.setup_ap()
        subnet_conf = dhcp_config.Subnet(
            subnet=ap_params['network'],
            router=ap_params['ip'],
            additional_parameters={'deny': 'unknown-clients'})
        dhcp_conf = dhcp_config.DhcpConfig(subnets=[subnet_conf])
        self.access_point.start_dhcp(dhcp_conf=dhcp_conf)

        self.connect(ap_params=ap_params)
        with asserts.assert_raises(ConnectionError):
            self.get_device_ipv4_addr()

        dhcp_logs = self.access_point.get_dhcp_logs()
        asserts.assert_true(
            re.search(r'DHCPDISCOVER from .*no free leases', dhcp_logs),
            "Did not find expected message in dhcp logs: " + dhcp_logs + "\n")

    def test_lease_renewal(self):
        """Validates that a client renews their DHCP lease."""
        LEASE_TIME = 30
        ap_params = self.setup_ap()
        subnet_conf = dhcp_config.Subnet(subnet=ap_params['network'],
                                         router=ap_params['ip'])
        dhcp_conf = dhcp_config.DhcpConfig(subnets=[subnet_conf],
                                           default_lease_time=LEASE_TIME,
                                           max_lease_time=LEASE_TIME)
        self.access_point.start_dhcp(dhcp_conf=dhcp_conf)
        self.connect(ap_params=ap_params)
        ip = self.get_device_ipv4_addr()

        dhcp_logs_before = self.access_point.get_dhcp_logs()
        SLEEP_TIME = LEASE_TIME + 3
        self.log.info(f'Sleeping {SLEEP_TIME}s to await DHCP renewal')
        time.sleep(SLEEP_TIME)

        dhcp_logs_after = self.access_point.get_dhcp_logs()
        dhcp_logs = dhcp_logs_after.replace(dhcp_logs_before, '')
        # Fuchsia renews at LEASE_TIME / 2, so there should be at least 2 DHCPREQUESTs in logs.
        # The log lines look like:
        # INFO dhcpd[17385]: DHCPREQUEST for 192.168.9.2 from f8:0f:f9:3d:ce:d1 via wlan1
        # INFO dhcpd[17385]: DHCPACK on 192.168.9.2 to f8:0f:f9:3d:ce:d1 via wlan1
        expected_string = f'DHCPREQUEST for {ip}'
        asserts.assert_true(
            dhcp_logs.count(expected_string) >= 2,
            f'Not enough DHCP renewals ("{expected_string}") in logs: ' +
            dhcp_logs + "\n")


class Dhcpv4DuplicateAddressTest(Dhcpv4InteropFixture):

    def setup_test(self):
        super().setup_test()
        self.extra_addresses = []
        self.ap_params = self.setup_ap()
        self.ap_ip_cmd = ip.LinuxIpCommand(self.access_point.ssh)

    def teardown_test(self):
        super().teardown_test()
        for ip in self.extra_addresses:
            self.ap_ip_cmd.remove_ipv4_address(self.ap_params['id'], ip)

    def test_duplicate_address_assignment(self):
        """It's possible for a DHCP server to assign an address that already exists on the network.
        DHCP clients are expected to perform a "gratuitous ARP" of the to-be-assigned address, and
        refuse to assign that address. Clients should also recover by asking for a different
        address.
        """
        # Modify subnet to hold fewer addresses.
        # A '/29' has 8 addresses (6 usable excluding router / broadcast)
        subnet = next(self.ap_params['network'].subnets(new_prefix=29))
        subnet_conf = dhcp_config.Subnet(
            subnet=subnet,
            router=self.ap_params['ip'],
            # When the DHCP server is considering dynamically allocating an IP address to a client,
            # it first sends an ICMP Echo request (a ping) to the address being assigned. It waits
            # for a second, and if no ICMP Echo response has been heard, it assigns the address.
            # If a response is heard, the lease is abandoned, and the server does not respond to
            # the client.
            # The ping-check configuration parameter can be used to control checking - if its value
            # is false, no ping check is done.
            additional_parameters={'ping-check': 'false'})
        dhcp_conf = dhcp_config.DhcpConfig(subnets=[subnet_conf])
        self.access_point.start_dhcp(dhcp_conf=dhcp_conf)

        # Add each of the usable IPs as an alias for the router's interface, such that the router
        # will respond to any pings on it.
        for ip in subnet.hosts():
            self.ap_ip_cmd.add_ipv4_address(self.ap_params['id'], ip)
            # Ensure we remove the address in self.teardown_test() even if the test fails
            self.extra_addresses.append(ip)

        self.connect(ap_params=self.ap_params)
        with asserts.assert_raises(ConnectionError):
            self.get_device_ipv4_addr()

        # Per spec, the flow should be:
        # Discover -> Offer -> Request -> Ack -> client optionally performs DAD
        dhcp_logs = self.access_point.get_dhcp_logs()
        for expected_message in [
                r'DHCPDISCOVER from \S+',
                r'DHCPOFFER on [0-9.]+ to \S+',
                r'DHCPREQUEST for [0-9.]+',
                r'DHCPACK on [0-9.]+',
                r'DHCPDECLINE of [0-9.]+ from \S+ via .*: abandoned',
                r'Abandoning IP address [0-9.]+: declined',
        ]:
            asserts.assert_true(
                re.search(expected_message, dhcp_logs),
                f'Did not find expected message ({expected_message}) in dhcp logs: {dhcp_logs}'
                + "\n")

        # Remove each of the IP aliases.
        # Note: this also removes the router's address (e.g. 192.168.1.1), so pinging the
        # router after this will not work.
        while self.extra_addresses:
            self.ap_ip_cmd.remove_ipv4_address(self.ap_params['id'],
                                               self.extra_addresses.pop())

        # Now, we should get an address successfully
        ip = self.get_device_ipv4_addr()
        dhcp_logs = self.access_point.get_dhcp_logs()

        expected_string = f'DHCPREQUEST for {ip}'
        asserts.assert_true(
            dhcp_logs.count(expected_string) >= 1,
            f'Incorrect count of DHCP Requests ("{expected_string}") in logs: '
            + dhcp_logs + "\n")

        expected_string = f'DHCPACK on {ip}'
        asserts.assert_true(
            dhcp_logs.count(expected_string) >= 1,
            f'Incorrect count of DHCP Acks ("{expected_string}") in logs: ' +
            dhcp_logs + "\n")


class Dhcpv4InteropCombinatorialOptionsTest(Dhcpv4InteropFixture):
    """DhcpV4 tests which validate combinations of DHCP options."""
    OPT_NUM_DOMAIN_SEARCH = 119
    OPT_NUM_DOMAIN_NAME = 15

    def setup_generated_tests(self):
        self._generate_dhcp_options()

        test_args = []
        for test in self.DHCP_OPTIONS:
            for option_list in self.DHCP_OPTIONS[test]:
                test_args.append(({
                    'dhcp_options': option_list,
                    'dhcp_parameters': {}
                }, ))

        self.generate_tests(test_logic=self.run_test_case_expect_dhcp_success,
                            name_func=self.generate_test_name,
                            arg_sets=test_args)

    def generate_test_name(self, settings):
        return settings["dhcp_options"]["test_name"]

    def _generate_dhcp_options(self):
        self.DHCP_OPTIONS = {
            'domain-name-tests': [{
                'domain-name': '"example.invalid"',
                'dhcp-parameter-request-list': self.OPT_NUM_DOMAIN_NAME,
                'test_name': "test_domain_name_invalid_tld"
            }, {
                'domain-name': '"example.test"',
                'dhcp-parameter-request-list': self.OPT_NUM_DOMAIN_NAME,
                'test_name': "test_domain_name_valid_tld"
            }],
            'domain-search-tests': [{
                'domain-search':
                '"example.invalid"',
                'dhcp-parameter-request-list':
                self.OPT_NUM_DOMAIN_SEARCH,
                'test_name':
                "test_domain_search_invalid_tld"
            }, {
                'domain-search': '"example.test"',
                'dhcp-parameter-request-list': self.OPT_NUM_DOMAIN_SEARCH,
                'test_name': "test_domain_search_valid_tld"
            }]
        }

        # The RFC limits DHCP payloads to 576 bytes unless the client signals it can handle larger
        # payloads, which it does by sending DHCP option 57, "Maximum DHCP Message Size". Despite
        # being able to accept larger payloads, clients typically don't advertise this.
        # The test verifies that the client accepts a large message split across multiple ethernet
        # frames.
        # The test is created by sending many bytes of options through the domain-name-servers
        # option, which is of unbounded length (though is compressed per RFC1035 section 4.1.4).
        typical_ethernet_mtu = 1500
        self.DHCP_OPTIONS['max-message-size-tests'] = []

        long_dns_setting = ', '.join(
            f'"ns{num}.example"'
            for num in random.sample(range(100_000, 1_000_000), 250))
        # RFC1035 compression means any shared suffix ('.example' in this case) will
        # be deduplicated. Calculate approximate length by removing that suffix.
        long_dns_setting_len = len(
            long_dns_setting.replace(', ', '').replace('"', '').replace(
                '.example', '').encode('utf-8'))
        asserts.assert_true(
            long_dns_setting_len > typical_ethernet_mtu,
            "Expected to generate message greater than ethernet mtu")
        self.DHCP_OPTIONS['max-message-size-tests'].append({
            'dhcp-max-message-size':
            long_dns_setting_len * 2,
            'domain-search':
            long_dns_setting,
            'dhcp-parameter-request-list':
            self.OPT_NUM_DOMAIN_SEARCH,
            'test_name':
            "test_max_sized_message",
        })
