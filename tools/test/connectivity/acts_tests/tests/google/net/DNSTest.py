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

from acts import asserts
from acts.controllers.openwrt_ap import MOBLY_CONTROLLER_CONFIG_NAME as OPENWRT
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.net import connectivity_const as cconst
from acts_contrib.test_utils.net import connectivity_test_utils as cutils
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from scapy.all import rdpcap, DNSRR, DNSQR, IP, IPv6


WLAN = "wlan0"
PING_ADDR = "google.com"


class DNSTest(WifiBaseTest):
    """DNS related test for Android."""

    def setup_class(self):
        self.dut = self.android_devices[0]
        wutils.wifi_test_device_init(self.dut)

        req_params = []
        opt_param = ["wifi_network", "configure_OpenWrt"]
        self.unpack_userparams(
            req_param_names=req_params, opt_param_names=opt_param)

        asserts.assert_true(OPENWRT in self.user_params,
                            "OpenWrtAP is not in testbed.")
        self.openwrt = self.access_points[0]
        if hasattr(self, "configure_OpenWrt") and self.configure_OpenWrt == "skip":
            self.dut.log.info("Skip configure Wifi interface due to config setup.")
        else:
            self.configure_openwrt_ap_and_start(wpa_network=True)
            self.wifi_network = self.openwrt.get_wifi_network()

        asserts.assert_true(self.openwrt.verify_wifi_status(),
                            "OpenWrt Wifi interface is not ready.")

    def teardown_class(self):
        """Reset wifi to make sure VPN tears down cleanly."""
        wutils.reset_wifi(self.dut)

    def teardown_test(self):
        """Reset wifi to make sure VPN tears down cleanly."""
        wutils.reset_wifi(self.dut)

    def ping(self, addr, ignore_status=True, timeout=60):
        """Start a ping from DUT and return ping result.

        Args:
            addr: Address to ping.
            ignore_status: ignore non zero return.
            timeout: cmd timeout.
        Returns:
            Boolean for ping result.
        """
        return "100%" not in self.dut.adb.shell("ping -c 1 %s" % addr,
                                                ignore_status=ignore_status,
                                                timeout=timeout)

    def generate_query_qname(self):
        """Return a random query name."""
        return "%s-ds.metric.gstatic.com" % random.randint(0, 99999999)

    def _block_dns_response_and_ping(self, test_qname):
        """Block the DNS response and ping

        Args:
            test_qname: Address to ping
        Returns:
            Packets for the ping result
        """
        # Start tcpdump on OpenWrt
        remote_pcap_path = \
            self.openwrt.network_setting.start_tcpdump(self.test_name)
        self.dut.log.info("Test query name = %s" % test_qname)
        # Block the DNS response only before sending the DNS query
        self.openwrt.network_setting.block_dns_response()
        # Start send a query
        self.ping(test_qname)
        # Un-block the DNS response right after DNS query
        self.openwrt.network_setting.unblock_dns_response()
        local_pcap_path = self.openwrt.network_setting.stop_tcpdump(
            remote_pcap_path, self.dut.device_log_path)
        self.dut.log.info("pcap file path : %s" % local_pcap_path)
        # Check DNSQR.qname in tcpdump to verify device retransmit the query
        packets = rdpcap(local_pcap_path)
        return packets

    def _get_dnsqr_packets(self, packets, layer, qname):
        """Filter the DNSQR packets with specific layer

        Args:
            packets: Packets that came from rdpcap function
            layer: Keep the packets that contains this layer
            qname: Keep the packets that related to this qname
        Returns:
            List of filtered packets
        """
        filtered_packets = []
        for pkt in packets:
            if not pkt.haslayer(DNSQR):
                continue
            if pkt[DNSQR].qname.decode().strip(".") != qname:
                continue
            if pkt.haslayer(layer):
                filtered_packets.append(pkt)
        return filtered_packets

    @test_tracker_info(uuid="dd7b8c92-c0f4-4403-a0ae-57a703162d83")
    def test_dns_query(self):
        # Setup environment
        wutils.connect_to_wifi_network(self.dut, self.wifi_network)
        # Start tcpdump on OpenWrt
        remote_pcap_path = self.openwrt.network_setting.start_tcpdump(self.test_name)
        # Generate query name
        test_qname = self.generate_query_qname()
        self.dut.log.info("Test query name = %s" % test_qname)
        # Start send a query
        ping_result = self.ping(test_qname)
        local_pcap_path = self.openwrt.network_setting.stop_tcpdump(remote_pcap_path,
                                                                    self.dut.device_log_path)
        # Check DNSRR.rrname in tcpdump to verify DNS response
        packets = rdpcap(local_pcap_path)
        self.dut.log.info("pcap file path : %s" % local_pcap_path)
        pkt_count = 0
        for pkt in packets:
            if pkt.haslayer(DNSRR) and pkt[DNSRR].rrname.decode().strip(".") == test_qname:
                pkt_count = pkt_count + 1
        self.dut.log.info("DNS query response count : %s" % pkt_count)
        if not ping_result:
            asserts.assert_true(pkt_count > 0,
                                "Did not find match standard query response in tcpdump.")
        asserts.assert_true(ping_result, "Device ping fail.")

    @test_tracker_info(uuid="cd20c6e7-9c2e-4286-b08e-c8e40e413da5")
    def test_dns_query_retransmit(self):
        # Setup environment
        wutils.connect_to_wifi_network(self.dut, self.wifi_network)
        test_qname = self.generate_query_qname()
        packets = self._block_dns_response_and_ping(test_qname)
        pkts = self._get_dnsqr_packets(packets, IP, test_qname)
        pkts6 = self._get_dnsqr_packets(packets, IPv6, test_qname)
        self.dut.log.info("IPv4 DNS query count : %s" % len(pkts))
        self.dut.log.info("IPv6 DNS query count : %s" % len(pkts6))
        asserts.assert_true(len(pkts) >= 2 or len(pkts6) >= 2,
                            "Did not find match standard query in tcpdump.")

    @test_tracker_info(uuid="5f58775d-ee7b-4d2e-8e77-77d41e821415")
    def test_private_dns_query_retransmit(self):
        # set private DNS mode
        cutils.set_private_dns(self.dut, cconst.PRIVATE_DNS_MODE_STRICT)

        # Setup environment
        wutils.connect_to_wifi_network(self.dut, self.wifi_network)
        test_qname = self.generate_query_qname()
        packets = self._block_dns_response_and_ping(test_qname)
        pkts = self._get_dnsqr_packets(packets, IP, test_qname)
        pkts6 = self._get_dnsqr_packets(packets, IPv6, test_qname)
        self.dut.log.info("IPv4 DNS query count : %s" % len(pkts))
        self.dut.log.info("IPv6 DNS query count : %s" % len(pkts6))
        asserts.assert_true(len(pkts) >= 2 or len(pkts6) >= 2,
                            "Did not find match standard query in tcpdump.")

