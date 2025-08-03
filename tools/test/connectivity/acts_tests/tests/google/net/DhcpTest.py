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
import time

from acts import asserts
from acts.controllers.openwrt_ap import MOBLY_CONTROLLER_CONFIG_NAME as OPENWRT
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.net.net_test_utils import start_tcpdump
from acts_contrib.test_utils.net.net_test_utils import stop_tcpdump
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from scapy.all import rdpcap, DHCP, IPv6
from scapy.layers.inet6 import ICMPv6ND_NA as NA

WLAN = "wlan0"
PING_ADDR = "google.com"
RAPID_COMMIT_OPTION = (80, b'')
DEFAULT_IPV6_ALLROUTERS = "ff02::2"


class DhcpTest(WifiBaseTest):
    """DHCP related test for Android."""

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
        self.openwrt.network_setting.setup_ipv6_bridge()
        asserts.assert_true(self.openwrt.verify_wifi_status(),
                            "OpenWrt Wifi interface is not ready.")

    def teardown_class(self):
        """Reset wifi and stop tcpdump cleanly."""
        wutils.reset_wifi(self.dut)
        self.openwrt.network_setting.clear_tcpdump()

    def teardown_test(self):
        """Reset wifi to make sure DUT tears down cleanly."""
        wutils.reset_wifi(self.dut)

    def _verify_ping(self, option="", dest=PING_ADDR):
        try:
            out = self.dut.adb.shell("ping%s -c1 %s" % (option, dest))
            return "100%" not in out
        except Exception as e:
            self.dut.log.debug(e)
            return False

    def _verify_device_address(self, ipv4=True, ipv6=True, timeout=15):
        """Verify device get assign address on wireless interface."""
        current_time = time.time()
        while time.time() < current_time + timeout:
            try:
                if ipv4:
                    ipv4_addr = self.dut.droid.connectivityGetIPv4Addresses(WLAN)[0]
                    self.dut.log.info("ipv4_address is %s" % ipv4_addr)
                if ipv6:
                    ipv6_addr = self.dut.droid.connectivityGetIPv6Addresses(WLAN)[0]
                    self.dut.log.info("ipv6_address is %s" % ipv6_addr)
                return True
            except:
                time.sleep(1)
        return False

    def verify_dhcp_packet(self, packets, support_rapid_commit):
        for pkt in packets:
            if pkt.haslayer(DHCP):
                if pkt[DHCP].options[0][1] == 1:
                    send_option = RAPID_COMMIT_OPTION in pkt[DHCP].options
                    asserts.assert_true(send_option == support_rapid_commit,
                                        "Unexpected result in DHCP DISCOVER.")
                elif pkt[DHCP].options[0][1] == 2:
                    asserts.assert_true(not support_rapid_commit,
                                        "Should not find DHCP OFFER when RAPID_COMMIT_OPTION supported.")
                elif pkt[DHCP].options[0][1] == 3:
                    asserts.assert_true(not support_rapid_commit,
                                        "Should not find DHCP REQUEST when RAPID_COMMIT_OPTION supported.")
                elif pkt[DHCP].options[0][1] == 5:
                    send_option = RAPID_COMMIT_OPTION in pkt[DHCP].options
                    asserts.assert_true(send_option == support_rapid_commit,
                                        "Unexpected result in DHCP ACK.")

    def verify_gratuitous_na(self, packets):
        ipv6localaddress = self.dut.droid.connectivityGetLinkLocalIpv6Address(WLAN).strip("%wlan0")
        self.dut.log.info("Device local address : %s" % ipv6localaddress)
        ipv6globaladdress = sorted(self.dut.droid.connectivityGetIPv6Addresses(WLAN))
        self.dut.log.info("Device global address : %s" % ipv6globaladdress)
        target_address = []
        for pkt in packets:
            if pkt.haslayer(NA) and pkt.haslayer(IPv6) and pkt[IPv6].src == ipv6localaddress\
                    and pkt[IPv6].dst == DEFAULT_IPV6_ALLROUTERS:
                # broadcast global address
                target_address.append(pkt.tgt)
        self.dut.log.info("Broadcast target address : %s" % target_address)
        asserts.assert_equal(ipv6globaladdress, sorted(target_address),
                             "Target address from NA is not match to device ipv6 address.")

    @test_tracker_info(uuid="01148659-6a3d-4a74-88b6-04b19c4acaaa")
    def test_ipv4_ipv6_network(self):
        """Verify device can get both ipv4 ipv6 address."""
        wutils.connect_to_wifi_network(self.dut, self.wifi_network)

        asserts.assert_true(self._verify_device_address(),
                            "Fail to get ipv4/ipv6 address.")
        asserts.assert_true(self._verify_ping(), "Fail to ping on ipv4.")
        asserts.assert_true(self._verify_ping("6"), "Fail to ping on ipv6.")

    @test_tracker_info(uuid="d3f37ba7-504e-48fc-95be-6eca9a148e4a")
    def test_ipv6_only_prefer_option(self):
        """Verify DUT can only get ipv6 address and ping out."""
        self.openwrt.network_setting.add_ipv6_prefer_option()
        wutils.connect_to_wifi_network(self.dut, self.wifi_network)

        asserts.assert_true(self._verify_device_address(ipv4=False),
                            "Fail to get ipv6 address.")
        asserts.assert_false(self._verify_ping(),
                             "Should not ping on success on ipv4.")
        asserts.assert_true(self._verify_ping("6"),
                            "Fail to ping on ipv6.")
        self.openwrt.network_setting.remove_ipv6_prefer_option()

    @test_tracker_info(uuid="a16f2a3c-e3ca-4fca-b3ee-bccb5cf34bab")
    def test_dhcp_rapid_commit(self):
        """Verify DUT can run with rapid commit on IPv4."""
        self.dut.adb.shell("device_config put connectivity dhcp_rapid_commit_version 1")
        self.openwrt.network_setting.add_dhcp_rapid_commit()
        remote_pcap_path = self.openwrt.network_setting.start_tcpdump(self.test_name)
        wutils.connect_to_wifi_network(self.dut, self.wifi_network)
        local_pcap_path = self.openwrt.network_setting.stop_tcpdump(
            remote_pcap_path, self.dut.device_log_path)
        self.dut.log.info("pcap file path : %s" % local_pcap_path)
        packets = rdpcap(local_pcap_path)
        self.verify_dhcp_packet(packets, True)
        self.openwrt.network_setting.remove_dhcp_rapid_commit()

    @test_tracker_info(uuid="cddb3d33-e5ef-4efd-8ae5-1325010a05c8")
    def test_dhcp_4_way_handshake(self):
        """Verify DUT can run with rapid commit on IPv4."""
        self.dut.adb.shell("device_config put connectivity dhcp_rapid_commit_version 0")
        remote_pcap_path = self.openwrt.network_setting.start_tcpdump(self.test_name)
        wutils.connect_to_wifi_network(self.dut, self.wifi_network)
        local_pcap_path = self.openwrt.network_setting.stop_tcpdump(
            remote_pcap_path, self.dut.device_log_path)
        self.dut.log.info("pcap file path : %s" % local_pcap_path)
        packets = rdpcap(local_pcap_path)
        self.verify_dhcp_packet(packets, False)

    @test_tracker_info(uuid="69fd9619-db35-406a-96e2-8425f8f5e8bd")
    def test_gratuitous_na(self):
        """Verify DUT will send NA after ipv6 address set."""
        self.dut.adb.shell("device_config put connectivity ipclient_gratuitous_na_version 1")
        remote_pcap_path = self.openwrt.network_setting.start_tcpdump(self.test_name)
        self.tcpdump_pid = start_tcpdump(self.dut, self.test_name)
        wutils.connect_to_wifi_network(self.dut, self.wifi_network)
        local_pcap_path = self.openwrt.network_setting.stop_tcpdump(
            remote_pcap_path, self.dut.device_log_path)
        stop_tcpdump(self.dut, self.tcpdump_pid, self.test_name)
        self.dut.log.info("pcap file path : %s" % local_pcap_path)
        packets = rdpcap(local_pcap_path)
        self.verify_gratuitous_na(packets)
