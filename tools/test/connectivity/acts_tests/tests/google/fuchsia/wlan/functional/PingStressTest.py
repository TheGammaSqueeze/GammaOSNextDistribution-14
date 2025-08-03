#!/usr/bin/env python3
#
# Copyright (C) 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
"""
PingStressTest exercises sending ICMP and ICMPv6 pings to a wireless access
router and another device behind the AP. Note, this does not reach out to the
internet. The DUT is only responsible for sending a routable packet; any
communication past the first-hop is not the responsibility of the DUT.
"""

import threading

from collections import namedtuple

from acts import signals
from acts import utils

from acts.controllers.access_point import setup_ap
from acts.controllers.ap_lib import hostapd_constants
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device
from acts.utils import rand_ascii_str

LOOPBACK_IPV4 = '127.0.0.1'
LOOPBACK_IPV6 = '::1'
PING_RESULT_TIMEOUT_SEC = 60 * 5

Test = namedtuple(
    typename='Args',
    field_names=['name', 'dest_ip', 'count', 'interval', 'timeout', 'size'],
    defaults=[3, 1000, 1000, 25])

Addrs = namedtuple(
    typename='Addrs',
    field_names=['gateway_ipv4', 'gateway_ipv6', 'remote_ipv4', 'remote_ipv6'])


class PingStressTest(WifiBaseTest):

    def setup_generated_tests(self):
        self.generate_tests(
            self.send_ping, lambda test_name, *_: f'test_{test_name}', [
                Test("loopback_ipv4", LOOPBACK_IPV4),
                Test("loopback_ipv6", LOOPBACK_IPV6),
                Test("gateway_ipv4", lambda addrs: addrs.gateway_ipv4),
                Test("gateway_ipv6", lambda addrs: addrs.gateway_ipv6),
                Test("remote_ipv4_small_packet",
                     lambda addrs: addrs.remote_ipv4),
                Test("remote_ipv6_small_packet",
                     lambda addrs: addrs.remote_ipv6),
                Test("remote_ipv4_small_packet_long",
                     lambda addrs: addrs.remote_ipv4,
                     count=50),
                Test("remote_ipv6_small_packet_long",
                     lambda addrs: addrs.remote_ipv6,
                     count=50),
                Test("remote_ipv4_medium_packet",
                     lambda addrs: addrs.remote_ipv4,
                     size=64),
                Test("remote_ipv6_medium_packet",
                     lambda addrs: addrs.remote_ipv6,
                     size=64),
                Test("remote_ipv4_medium_packet_long",
                     lambda addrs: addrs.remote_ipv4,
                     count=50,
                     timeout=1500,
                     size=64),
                Test("remote_ipv6_medium_packet_long",
                     lambda addrs: addrs.remote_ipv6,
                     count=50,
                     timeout=1500,
                     size=64),
                Test("remote_ipv4_large_packet",
                     lambda addrs: addrs.remote_ipv4,
                     size=500),
                Test("remote_ipv6_large_packet",
                     lambda addrs: addrs.remote_ipv6,
                     size=500),
                Test("remote_ipv4_large_packet_long",
                     lambda addrs: addrs.remote_ipv4,
                     count=50,
                     timeout=5000,
                     size=500),
                Test("remote_ipv6_large_packet_long",
                     lambda addrs: addrs.remote_ipv6,
                     count=50,
                     timeout=5000,
                     size=500),
            ])

    def setup_class(self):
        super().setup_class()
        self.ssid = rand_ascii_str(10)
        self.dut = create_wlan_device(self.fuchsia_devices[0])
        self.access_point = self.access_points[0]
        self.iperf_server = self.iperf_servers[0]
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.ssid,
                 setup_bridge=True,
                 is_ipv6_enabled=True,
                 is_nat_enabled=False)

        ap_bridges = self.access_point.interfaces.get_bridge_interface()
        if len(ap_bridges) != 1:
            raise signals.TestAbortClass(
                f'Expected one bridge interface on the AP, got {ap_bridges}')
        self.ap_ipv4 = utils.get_addr(self.access_point.ssh, ap_bridges[0])
        self.ap_ipv6 = utils.get_addr(self.access_point.ssh,
                                      ap_bridges[0],
                                      addr_type='ipv6_link_local')
        self.log.info(
            f"Gateway finished setup ({self.ap_ipv4} | {self.ap_ipv6})")

        self.iperf_server.renew_test_interface_ip_address()
        self.iperf_server_ipv4 = self.iperf_server.get_addr()
        self.iperf_server_ipv6 = self.iperf_server.get_addr(
            addr_type='ipv6_private_local')
        self.log.info(
            f"Remote finished setup ({self.iperf_server_ipv4} | {self.iperf_server_ipv6})"
        )

        self.dut.associate(self.ssid)

        # Wait till the DUT has valid IP addresses after connecting.
        self.dut.device.wait_for_ipv4_addr(
            self.dut.device.wlan_client_test_interface_name)
        self.dut.device.wait_for_ipv6_addr(
            self.dut.device.wlan_client_test_interface_name)
        self.log.info("DUT has valid IP addresses on test network")

    def teardown_class(self):
        self.dut.disconnect()
        self.dut.reset_wifi()
        self.download_ap_logs()
        self.access_point.stop_all_aps()

    def send_ping(self,
                  _,
                  get_addr_fn,
                  count=3,
                  interval=1000,
                  timeout=1000,
                  size=25):
        dest_ip = get_addr_fn(
            Addrs(
                gateway_ipv4=self.ap_ipv4,
                # IPv6 link-local addresses require specification of the
                # outgoing interface as the scope ID when sending packets.
                gateway_ipv6=
                f'{self.ap_ipv6}%{self.dut.get_default_wlan_test_interface()}',
                remote_ipv4=self.iperf_server_ipv4,
                # IPv6 global addresses do not require scope IDs.
                remote_ipv6=self.iperf_server_ipv6)) if callable(
                    get_addr_fn) else get_addr_fn

        self.log.info(f'Attempting to ping {dest_ip}...')
        ping_result = self.dut.can_ping(dest_ip, count, interval, timeout,
                                        size)
        if ping_result:
            self.log.info('Ping was successful.')
        else:
            raise signals.TestFailure('Ping was unsuccessful.')

    def test_simultaneous_pings(self):
        ping_urls = [
            self.iperf_server_ipv4,
            self.ap_ipv4,
            self.iperf_server_ipv6,
            f'{self.ap_ipv6}%{self.dut.get_default_wlan_test_interface()}',
        ]
        ping_threads = []
        ping_results = []

        def ping_thread(self, dest_ip, ping_results):
            self.log.info('Attempting to ping %s...' % dest_ip)
            ping_result = self.dut.can_ping(dest_ip, count=10, size=50)
            if ping_result:
                self.log.info('Success pinging: %s' % dest_ip)
            else:
                self.log.info('Failure pinging: %s' % dest_ip)
            ping_results.append(ping_result)

        try:
            # Start multiple ping at the same time
            for index, url in enumerate(ping_urls):
                t = threading.Thread(target=ping_thread,
                                     args=(self, url, ping_results))
                ping_threads.append(t)
                t.start()

            # Wait for all threads to complete or timeout
            for t in ping_threads:
                t.join(PING_RESULT_TIMEOUT_SEC)

        finally:
            is_alive = False

            for index, t in enumerate(ping_threads):
                if t.is_alive():
                    t = None
                    is_alive = True

            if is_alive:
                raise signals.TestFailure(
                    f'Timed out while pinging {ping_urls[index]}')

        for index in range(0, len(ping_results)):
            if not ping_results[index]:
                raise signals.TestFailure(f'Failed to ping {ping_urls[index]}')
        return True
