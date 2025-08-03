#
#   Copyright 2018 - The Android Open Source Project
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
from acts_contrib.test_utils.net import connectivity_const as cconst
from acts_contrib.test_utils.net import connectivity_test_utils as cutils
from acts_contrib.test_utils.net import net_test_utils as nutils
from acts_contrib.test_utils.tel.tel_ims_utils import set_wfc_mode
from acts_contrib.test_utils.net.net_test_utils import start_tcpdump
from acts_contrib.test_utils.net.net_test_utils import stop_tcpdump
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_DISABLED
from acts_contrib.test_utils.tel.tel_test_utils import get_operator_name
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from scapy.all import rdpcap
from scapy.all import Scapy_Exception
from scapy.all import TCP
from scapy.all import UDP


RST = 0x04
SSID = wutils.WifiEnums.SSID_KEY


class DnsOverTlsTest(WifiBaseTest):
    """Tests for DNS-over-TLS."""

    def setup_class(self):
        """Setup devices for DNS-over-TLS test and unpack params."""

        self.dut = self.android_devices[0]
        if len(self.android_devices) > 1:
            self.dut_b = self.android_devices[1]
        for ad in self.android_devices:
            ad.droid.setPrivateDnsMode(True)
            if OPENWRT not in self.user_params:
                nutils.verify_lte_data_and_tethering_supported(ad)
            set_wfc_mode(self.log, ad, WFC_MODE_DISABLED)
        req_params = ("ping_hosts",)
        opt_params = ("ipv4_only_network", "ipv4_ipv6_network",
                      "dns_name", "configure_OpenWrt", "wifi_network")
        self.unpack_userparams(req_param_names=req_params,
                               opt_param_names=opt_params)

        if OPENWRT in self.user_params:
            self.openwrt = self.access_points[0]
            if hasattr(self, "configure_OpenWrt") and self.configure_OpenWrt == "skip":
                self.dut.log.info("Skip configure Wifi interface due to config setup.")
            else:
                self.configure_openwrt_ap_and_start(wpa_network=True)
                self.wifi_network = self.openwrt.get_wifi_network()
            self.private_dns_servers = [self.dns_name]
            self.openwrt.network_setting.setup_dns_server(self.dns_name)
        else:
            self.private_dns_servers = [cconst.DNS_GOOGLE_HOSTNAME,
                                        cconst.DNS_QUAD9_HOSTNAME,
                                        cconst.DNS_CLOUDFLARE_HOSTNAME]
        self.tcpdump_pid = None

    def teardown_test(self):
        wutils.reset_wifi(self.dut)

    def teardown_class(self):
        for ad in self.android_devices:
            ad.droid.setPrivateDnsMode(True)

        if OPENWRT in self.user_params:
            self.openwrt.network_setting.remove_dns_server()

    def on_fail(self, test_name, begin_time):
        self.dut.take_bug_report(test_name, begin_time)

    def get_wifi_network(self, ipv6_supported):
        if OPENWRT in self.user_params:
            if ipv6_supported:
                self.openwrt.network_setting.enable_ipv6()
            else:
                self.openwrt.network_setting.disable_ipv6()
            return self.openwrt.get_wifi_network()
        if ipv6_supported:
            return self.ipv4_ipv6_network
        return self.ipv4_only_network

    def _start_tcp_dump(self, ad):
        """Start tcpdump on the given dut.

        Args:
            ad: dut to run tcpdump on
        """
        self.tcpdump_pid = start_tcpdump(ad, self.test_name)

    def _stop_tcp_dump(self, ad):
        """Stop tcpdump and pull it to the test run logs.

        Args:
            ad: dut to pull tcpdump from
        """
        return stop_tcpdump(ad, self.tcpdump_pid, self.test_name)

    def _verify_dns_queries_over_tls(self, pcap_file, tls=True):
        """Verify if DNS queries were over TLS or not.

        Args:
            pcap_file: tcpdump file
            tls: if queries should be over TLS or port 853
        """
        try:
            packets = rdpcap(pcap_file)
        except Scapy_Exception:
            asserts.fail("Not a valid pcap file")
        for pkt in packets:
            summary = "%s" % pkt.summary()
            for host in self.ping_hosts:
                host = host.split(".")[-2]
                if tls and UDP in pkt and pkt[UDP].dport == 53 and \
                    host in summary:
                      asserts.fail("Found query to port 53: %s" % summary)
                elif not tls and TCP in pkt and pkt[TCP].dport == 853 and \
                    not pkt[TCP].flags:
                      asserts.fail("Found query to port 853: %s" % summary)

    def _verify_no_rst_packets_port_853(self, pcap_file):
        """Verify if RST packets are found in the pcap file.

        Args:
            pcap_file: full path of tcpdump file
        """
        packets = rdpcap(pcap_file)
        for pkt in packets:
            if TCP in pkt and pkt[TCP].flags == RST and pkt[TCP].dport == 853:
                asserts.fail("Found RST packets: %s" % pkt.summary())

    def _test_private_dns_mode(self, ad, net, dns_mode, use_tls, hostname=None):
        """Test private DNS mode.

        Args:
            ad: android device object
            net: wifi network to connect to, LTE network if None
            dns_mode: private DNS mode
            use_tls: if True, the DNS packets should be encrypted
            hostname: private DNS hostname to set to
        """

        # set private dns mode
        if dns_mode:
            cutils.set_private_dns(self.dut, dns_mode, hostname)

        # connect to wifi
        if net:
            wutils.start_wifi_connection_scan_and_ensure_network_found(
                self.dut, net[SSID])
            wutils.wifi_connect(self.dut, net)

        # start tcpdump on the device
        self._start_tcp_dump(self.dut)

        # ping hosts should pass
        for host in self.ping_hosts:
            self.log.info("Pinging %s" % host)
            status = wutils.validate_connection(self.dut, host)
            asserts.assert_true(status, "Failed to ping host %s" % host)
            self.log.info("Ping successful")

        # stop tcpdump
        pcap_file = self._stop_tcp_dump(self.dut)

        # verify DNS queries
        self._verify_dns_queries_over_tls(pcap_file, use_tls)

        # reset wifi
        wutils.reset_wifi(self.dut)

    def _test_invalid_private_dns(self, net, dns_mode, dns_hostname):
        """Test private DNS with invalid hostname, which should failed the ping.

        :param net: Wi-Fi network to connect to
        :param dns_mode: private DNS mode
        :param dns_hostname: private DNS hostname
        :return:
        """

        cutils.set_private_dns(self.dut, dns_mode, dns_hostname)
        if net:
            wutils.start_wifi_connection_scan_and_ensure_network_found(
                self.dut, net[SSID])
            wutils.wifi_connect(
                self.dut, net, assert_on_fail=False, check_connectivity=False)

        self._start_tcp_dump(self.dut)

        # ping hosts should NOT pass
        ping_result = False
        for host in self.ping_hosts:
            self.log.info("Pinging %s" % host)
            try:
                ping_result = self.dut.droid.httpPing(host)
            except:
                pass
            # Ping result should keep negative with invalid DNS,
            # so once it's positive we should break, and the test should fail
            if ping_result:
                break

        pcap_file = self._stop_tcp_dump(self.dut)
        self._verify_dns_queries_over_tls(pcap_file, True)
        wutils.reset_wifi(self.dut)
        return ping_result

    @test_tracker_info(uuid="2957e61c-d333-45fb-9ff9-2250c9c8535a")
    def test_private_dns_mode_off_wifi_ipv4_only_network(self):
        """Verify private dns mode off on ipv4 only network.

        Steps:
            1. Set private dns mode off
            2. Connect to wifi network. DNS server supports DNS/TLS
            3. Run HTTP ping to amazon.com, facebook.com, netflix.com
            4. Verify ping works to differnt hostnames
            5. Verify that all queries go to port 53
        """
        self._test_private_dns_mode(self.dut,
                                    self.get_wifi_network(False),
                                    cconst.PRIVATE_DNS_MODE_OFF,
                                    False)

    @test_tracker_info(uuid="ea036d22-25af-4df0-b6cc-0027bc1efbe9")
    def test_private_dns_mode_off_wifi_ipv4_ipv6_network(self):
        """Verify private dns mode off on ipv4-ipv6 network.

        Steps:
            1. Set private dns mode off
            2. Connect to wifi network. DNS server supports DNS/TLS
            3. Run HTTP ping to amazon.com, facebook.com, netflix.com
            4. Verify ping works to differnt hostnames
            5. Verify that all queries go to port 53
        """
        self._test_private_dns_mode(self.dut,
                                    self.get_wifi_network(True),
                                    cconst.PRIVATE_DNS_MODE_OFF,
                                    False)

    @test_tracker_info(uuid="4227abf4-0a75-4b4d-968c-dfc63052f5db")
    def test_private_dns_mode_opportunistic_wifi_ipv4_only_network(self):
        """Verify private dns mode opportunistic on ipv4 only network.

        Steps:
            1. Set private dns to opportunistic mode
            2. Connect to wifi network. DNS server supports DNS/TLS
            3. Run HTTP ping to amazon.com, facebook.com, netflix.com
            4. Verify ping works to differnt hostnames
            5. Verify that all queries go to port 853 and encrypted
        """
        self._test_private_dns_mode(self.dut,
                                    self.get_wifi_network(False),
                                    cconst.PRIVATE_DNS_MODE_OPPORTUNISTIC,
                                    True)

    @test_tracker_info(uuid="0c97cfef-4313-4346-b05b-395de63c5c3f")
    def test_private_dns_mode_opportunistic_wifi_ipv4_ipv6_network(self):
        """Verify private dns mode opportunistic on ipv4-ipv6 network.

        Steps:
            1. Set private dns to opportunistic mode
            2. Connect to wifi network. DNS server supports DNS/TLS
            3. Run HTTP ping to amazon.com, facebook.com, netflix.com
            4. Verify ping works to differnt hostnames
            5. Verify that all queries go to port 853
        """
        self._test_private_dns_mode(self.dut,
                                    self.get_wifi_network(True),
                                    cconst.PRIVATE_DNS_MODE_OPPORTUNISTIC,
                                    True)

    @test_tracker_info(uuid="b70569f1-2613-49d0-be49-fd3464dde305")
    def test_private_dns_mode_strict_wifi_ipv4_only_network(self):
        """Verify private dns mode strict on ipv4 only network.

        Steps:
            1. Set private dns to strict mode
            2. Connect to wifi network. DNS server supports DNS/TLS
            3. Run HTTP ping to amazon.com, facebook.com, netflix.com
            4. Verify ping works to differnt hostnames
            5. Verify that all queries go to port 853 and encrypted
        """
        for dns in self.private_dns_servers:
            self._test_private_dns_mode(self.dut,
                                        self.get_wifi_network(False),
                                        cconst.PRIVATE_DNS_MODE_STRICT,
                                        True,
                                        dns)

    @test_tracker_info(uuid="85738b52-823b-4c59-a0d5-219e2fab2929")
    def test_private_dns_mode_strict_wifi_ipv4_ipv6_network(self):
        """Verify private dns mode strict on ipv4-ipv6 network.

        Steps:
            1. Set private dns to strict mode
            2. Connect to wifi network. DNS server supports DNS/TLS
            3. Run HTTP ping to amazon.com, facebook.com, netflix.com
            4. Verify ping works to differnt hostnames
            5. Verify that all queries go to port 853 and encrypted
        """
        for dns in self.private_dns_servers:
            self._test_private_dns_mode(self.dut,
                                        self.get_wifi_network(True),
                                        cconst.PRIVATE_DNS_MODE_STRICT,
                                        True,
                                        dns)

    @test_tracker_info(uuid="727e280a-d2bd-463f-b2a1-653d4b3f7f29")
    def test_private_dns_mode_off_vzw_carrier(self):
        """Verify private dns mode off on VZW network.

        Steps:
            1. Set private dns mode off
            2. Connect to wifi network. VZW doesn't support DNS/TLS
            3. Run HTTP ping to amazon.com, facebook.com, netflix.com
            4. Verify ping works to differnt hostnames
            5. Verify that all queries go to port 53
        """
        carrier = get_operator_name(self.log, self.dut_b)
        asserts.skip_if(carrier != "vzw", "Carrier is not Verizon")
        self._test_private_dns_mode(self.dut_b,
                                    None,
                                    cconst.PRIVATE_DNS_MODE_OFF,
                                    False)

    @test_tracker_info(uuid="b16f6e2c-a24f-4efe-9003-2bfaf28b8d5e")
    def test_private_dns_mode_off_tmo_carrier(self):
        """Verify private dns mode off on TMO network.

        Steps:
            1. Set private dns to off mode
            2. Connect to wifi network. TMO supports DNS/TLS
            3. Run HTTP ping to amazon.com, facebook.com, netflix.com
            4. Verify ping works to differnt hostnames
            5. Verify that all queries go to port 53
        """
        carrier = get_operator_name(self.log, self.dut)
        asserts.skip_if(carrier != "tmo", "Carrier is not T-mobile")
        self._test_private_dns_mode(self.dut,
                                    None,
                                    cconst.PRIVATE_DNS_MODE_OFF,
                                    False)

    @test_tracker_info(uuid="edfa7bfe-3e52-46b4-9d72-7c6c300b3680")
    def test_private_dns_mode_opportunistic_vzw_carrier(self):
        """Verify private dns mode opportunistic on VZW network.

        Steps:
            1. Set private dns mode opportunistic
            2. Connect to wifi network. VZW doesn't support DNS/TLS
            3. Run HTTP ping to amazon.com, facebook.com, netflix.com
            4. Verify ping works to differnt hostnames
            5. Verify that all queries go to port 53
        """
        carrier = get_operator_name(self.log, self.dut_b)
        asserts.skip_if(carrier != "vzw", "Carrier is not Verizon")
        self._test_private_dns_mode(self.dut_b,
                                    None,
                                    cconst.PRIVATE_DNS_MODE_OPPORTUNISTIC,
                                    False)

    @test_tracker_info(uuid="41c3f2c4-11b7-4bb8-a3c9-fac63f6822f6")
    def test_private_dns_mode_opportunistic_tmo_carrier(self):
        """Verify private dns mode opportunistic on TMO network.

        Steps:
            1. Set private dns mode opportunistic
            2. Connect to wifi network. TMP supports DNS/TLS
            3. Run HTTP ping to amazon.com, facebook.com, netflix.com
            4. Verify ping works to differnt hostnames
            5. Verify that all queries go to port 853 and encrypted
        """
        carrier = get_operator_name(self.log, self.dut)
        asserts.skip_if(carrier != "tmo", "Carrier is not T-mobile")
        self._test_private_dns_mode(self.dut,
                                    None,
                                    cconst.PRIVATE_DNS_MODE_OPPORTUNISTIC,
                                    True)

    @test_tracker_info(uuid="65fd2052-f0c0-4446-b353-7ed2273e6c95")
    def test_private_dns_mode_strict_vzw_carrier(self):
        """Verify private dns mode strict on VZW network.

        Steps:
            1. Set private dns mode strict
            2. Connect to wifi network. VZW doesn't support DNS/TLS
            3. Run HTTP ping to amazon.com, facebook.com, netflix.com
            4. Verify ping works to differnt hostnames
            5. Verify that all queries go to port 853 and encrypted
        """
        carrier = get_operator_name(self.log, self.dut_b)
        asserts.skip_if(carrier != "vzw", "Carrier is not Verizon")
        for dns in self.private_dns_servers:
            self._test_private_dns_mode(self.dut_b,
                                        None,
                                        cconst.PRIVATE_DNS_MODE_STRICT,
                                        True,
                                        dns)

    @test_tracker_info(uuid="bca141f7-06c9-4e44-854e-4bdb9443b2da")
    def test_private_dns_mode_strict_tmo_carrier(self):
        """Verify private dns mode strict on TMO network.

        Steps:
            1. Set private dns mode strict
            2. Connect to wifi network. TMO supports DNS/TLS
            3. Run HTTP ping to amazon.com, facebook.com, netflix.com
            4. Verify ping works to differnt hostnames
            5. Verify that all queries go to port 853 and encrypted
        """
        carrier = get_operator_name(self.log, self.dut)
        asserts.skip_if(carrier != "tmo", "Carrier is not T-mobile")
        for dns in self.private_dns_servers:
            self._test_private_dns_mode(self.dut,
                                        None,
                                        cconst.PRIVATE_DNS_MODE_STRICT,
                                        True,
                                        dns)

    @test_tracker_info(uuid="7d977987-d9e3-4be1-b8fc-e5a84050ed48")
    def test_private_dns_mode_opportunistic_connectivity_toggle_networks(self):
        """Verify private DNS opportunistic mode connectivity by toggling networks.

        Steps:
            1. Set private DNS opportunistic mode
            2. DUT is connected to mobile network
            3. Verify connectivity and DNS queries going to port 853 for TMO
               and port 53 for VZW
            4. Switch to wifi network set with private DNS server
            5. Verify connectivity and DNS queries going to port 853
            6. Switch back to mobile network
            7. Verify connectivity and DNS queries going to port 853 for TMO
               and port 53 for VZW
            8. Repeat steps 1-7 for TMO, VZW and different private DNS servers
        """
        for ad in self.android_devices:
            carrier = get_operator_name(self.log, ad)
            self.log.info("Carrier is: %s" % carrier)
            use_tls = True if carrier == "tmo" else False
            for dns in self.private_dns_servers:
                self.log.info("Setting opportunistic private dns mode")
                # set private dns mode
                cutils.set_private_dns(ad, cconst.PRIVATE_DNS_MODE_OPPORTUNISTIC)

                # verify dns over tls on mobile network
                self._test_private_dns_mode(
                    self.dut, None, None, use_tls, dns)

                # verify dns over tls on wifi network
                self._test_private_dns_mode(
                    self.dut, self.ipv4_ipv6_network, None, True, dns)

                # verify dns over tls on mobile network
                wutils.reset_wifi(self.dut)
                self._test_private_dns_mode(
                    self.dut, None, None, use_tls, dns)

    @test_tracker_info(uuid="bc2f228f-e288-4539-a4b9-c02968209985")
    def test_private_dns_mode_strict_connectivity_toggle_networks(self):
        """Verify private DNS strict mode connectivity by toggling networks.

        Steps:
            1. Set private DNS strict mode
            2. DUT is connected to mobile network
            3. Verify connectivity and DNS queries going to port 853
            4. Switch to wifi network
            5. Verify connectivity and DNS queries going to port 853
            6. Switch back to mobile network
            7. Verify connectivity and DNS queries going to port 853
            8. Repeat steps 1-7 for TMO, VZW and different private DNS servers
        """
        for ad in self.android_devices:
            self.log.info("Carrier is: %s" % get_operator_name(self.log, ad))
            for dns in self.private_dns_servers:
                self.log.info("Setting strict mode private dns: %s" % dns)
                # set private dns mode
                cutils.set_private_dns(ad, cconst.PRIVATE_DNS_MODE_STRICT, dns)

                # verify dns over tls on mobile network
                self._test_private_dns_mode(
                    self.dut, None, None, True, dns)

                # verify dns over tls on wifi network
                self._test_private_dns_mode(
                    self.dut, self.ipv4_ipv6_network, None, True, dns)

                # verify dns over tls on mobile network
                wutils.reset_wifi(self.dut)
                self._test_private_dns_mode(
                    self.dut, None, None, True, dns)

    @test_tracker_info(uuid="1426673a-7728-4df7-8de5-dfb3529ada62")
    def test_dns_server_link_properties_strict_mode(self):
        """Verify DNS server in the link properties when set in strict mode.

        Steps:
            1. Set DNS server hostname in Private DNS settings (stict mode)
            2. Verify that DNS server set in settings is in link properties
            3. Verify for WiFi as well as LTE
        """
        # start tcpdump on device
        self._start_tcp_dump(self.dut)

        # set private DNS to strict mode
        cutils.set_private_dns(
            self.dut, cconst.PRIVATE_DNS_MODE_STRICT, cconst.DNS_GOOGLE_HOSTNAME)

        # connect DUT to wifi network
        wutils.start_wifi_connection_scan_and_ensure_network_found(
            self.dut, self.ipv4_ipv6_network[SSID])
        wutils.wifi_connect(self.dut, self.ipv4_ipv6_network)
        for host in self.ping_hosts:
            wutils.validate_connection(self.dut, host)

        # DNS server in link properties for wifi network
        link_prop = self.dut.droid.connectivityGetActiveLinkProperties()
        wifi_dns_servers = link_prop["PrivateDnsServerName"]
        self.log.info("Link prop: %s" % wifi_dns_servers)

        # DUT is on LTE data
        wutils.reset_wifi(self.dut)
        time.sleep(1)  # wait till lte network becomes active
        for host in self.ping_hosts:
            wutils.validate_connection(self.dut, host)

        # DNS server in link properties for cell network
        link_prop = self.dut.droid.connectivityGetActiveLinkProperties()
        lte_dns_servers = link_prop["PrivateDnsServerName"]
        self.log.info("Link prop: %s" % lte_dns_servers)

        # stop tcpdump on device
        pcap_file = self._stop_tcp_dump(self.dut)

        # Verify DNS server in link properties
        asserts.assert_true(cconst.DNS_GOOGLE_HOSTNAME in wifi_dns_servers,
                            "Hostname not in link properties - wifi network")
        asserts.assert_true(cconst.DNS_GOOGLE_HOSTNAME in lte_dns_servers,
                            "Hostname not in link properites - cell network")

    @test_tracker_info(uuid="525a6f2d-9751-474e-a004-52441091e427")
    def test_dns_over_tls_no_reset_packets(self):
        """Verify there are no TCP packets with RST flags on port 853.

        Steps:
            1. Enable opportunistic or strict mode
            2. Ping hosts and verify that there are TCP pkts with RST flags
        """
        # start tcpdump on device
        self._start_tcp_dump(self.dut)

        # set private DNS to opportunistic mode
        cutils.set_private_dns(self.dut, cconst.PRIVATE_DNS_MODE_OPPORTUNISTIC)

        # connect DUT to wifi network
        wutils.start_wifi_connection_scan_and_ensure_network_found(
            self.dut, self.ipv4_ipv6_network[SSID])
        wutils.wifi_connect(self.dut, self.ipv4_ipv6_network)
        for host in self.ping_hosts:
            wutils.validate_connection(self.dut, host)


        # stop tcpdump on device
        pcap_file = self._stop_tcp_dump(self.dut)

        # check that there no RST TCP packets
        self._verify_no_rst_packets_port_853(pcap_file)

    @test_tracker_info(uuid="af6e34f1-3ad5-4ab0-b3b9-53008aa08294")
    def test_private_dns_mode_strict_invalid_hostnames(self):
        """Verify that invalid hostnames are not able to ping for strict mode.

        Steps:
            1. Set private DNS to strict mode with invalid hostname
            2. Verify that invalid hostname is not saved
        """
        invalid_hostnames = ["!%@&!*", "12093478129", "9.9.9.9", "sdkfjhasdf"]
        for dns_hostname in invalid_hostnames:
            ping_result = self._test_invalid_private_dns(
                self.get_wifi_network(False),
                cconst.PRIVATE_DNS_MODE_STRICT,
                dns_hostname)
            asserts.assert_false(ping_result, "Ping success with invalid DNS.")

