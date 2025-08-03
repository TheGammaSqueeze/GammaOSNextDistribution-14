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
from acts_contrib.test_utils.net import connectivity_const as cconst
from acts_contrib.test_utils.net import connectivity_test_utils as cutils
from acts_contrib.test_utils.net.net_test_utils import start_tcpdump
from acts_contrib.test_utils.net.net_test_utils import stop_tcpdump
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from scapy.all import rdpcap
from scapy.all import Scapy_Exception
from scapy.all import TCP
from scapy.all import UDP


DEFAULT_DNS_TIMEOUT = 5


class DnsOverHttpsTest(WifiBaseTest):
    """Tests for DnsoverHttps feature."""

    def setup_class(self):
        """Setup devices and OpenWrt for DnsoverHttps test and unpack params."""

        self.dut = self.android_devices[0]
        if len(self.android_devices) > 1:
            self.dut_b = self.android_devices[1]
        for ad in self.android_devices:
            wutils.reset_wifi(ad)
            ad.droid.setPrivateDnsMode(True)
        req_params = ("ping_hosts",)
        opt_params = ("wifi_network", "configure_OpenWrt",
                      "ipv4_only_network", "ipv4_ipv6_network")
        self.unpack_userparams(req_param_names=req_params,
                               opt_param_names=opt_params)
        if OPENWRT in self.user_params:
            self.openwrt = self.access_points[0]
            if hasattr(self, "configure_OpenWrt") and self.configure_OpenWrt == "skip":
                self.dut.log.info("Skip configure Wifi interface due to config setup.")
            else:
                self.configure_openwrt_ap_and_start(wpa_network=True)
        self.tcpdump_pid = None
        self.default_dns = None
        self.default_dns_v6 = None
        self._setup_doh(self.dut, self.get_wifi_network())

    def teardown_test(self):
        wutils.reset_wifi(self.dut)
        if OPENWRT in self.user_params:
            self.openwrt.network_setting.del_default_dns(self.default_dns)
            self.default_dns = None
            self.default_dns_v6 = None

    def teardown_class(self):
        for ad in self.android_devices:
            ad.droid.setPrivateDnsMode(True)
            self._setup_doh(ad, self.get_wifi_network(), enable=False)

    def on_fail(self, test_name, begin_time):
        self.dut.take_bug_report(test_name, begin_time)

    def get_wifi_network(self, ipv6_supported=False):
        """Return fit network for conditions.

        Args:
            ipv6_supported: Boolean for select network.
        Returns:
            A dict for network object for connect wifi.
        """
        if OPENWRT in self.user_params:
            if ipv6_supported:
                self.openwrt.network_setting.enable_ipv6()
                self.openwrt.network_setting.setup_ipv6_bridge()
            else:
                self.openwrt.network_setting.disable_ipv6()
                self.openwrt.network_setting.remove_ipv6_bridge()
            if self.default_dns:
                self.openwrt.network_setting.add_default_dns(self.default_dns)
            if self.default_dns_v6:
                for ipv6_dns in self.default_dns_v6:
                    self.openwrt.network_setting.add_default_v6_dns(ipv6_dns)
            if hasattr(self, "configure_OpenWrt") and self.configure_OpenWrt == "skip":
                return self.wifi_network
            return self.openwrt.get_wifi_network()
        if ipv6_supported:
            return self.ipv4_ipv6_network
        return self.ipv4_only_network

    def _verify_doh_queries(self, pcap_file, over_https):
        """Verify if DNS queries were over https or not.

        Args:
            pcap_file: tcpdump file
            over_https: True if excepted all dns go through doh.
        """
        try:
            packets = rdpcap(pcap_file)
        except Scapy_Exception:
            asserts.fail("Not a valid pcap file")

        for pkt in packets:
            summary = "%s" % pkt.summary()
            for host in self.ping_hosts:
                host = host.split(".")[-2]
                if UDP in pkt and pkt[UDP].sport == 53 and host in summary:
                    if over_https:
                        asserts.fail("Found query to port 53: %s" % summary)
                    else:
                        self.dut.log.info("Found query to port 53: %s" % summary)
                if TCP in pkt and pkt[TCP].sport == 853:
                    asserts.fail("Found query to port 853: %s" % summary)

    def _test_public_doh_mode(self, ad, net, dns_mode, hostname=None):
        """Test step for DoH.

        Args:
            ad: android device object.
            net: wifi network to connect to, LTE network if None.
            dns_mode: private DNS mode.
            hostname: private DNS hostname to set to.
        """

        # set private dns mode
        if dns_mode:
            cutils.set_private_dns(self.dut, dns_mode, hostname)
        # connect to wifi
        wutils.start_wifi_connection_scan_and_ensure_network_found(
            self.dut, net[wutils.WifiEnums.SSID_KEY])
        wutils.wifi_connect(self.dut, net)
        self._verify_tls_completed()

        # start tcpdump on the device
        self.tcpdump_pid = start_tcpdump(self.dut, self.test_name)

        # ping hosts should pass
        for host in self.ping_hosts:
            self.log.info("Pinging %s" % host)
            status = wutils.validate_connection(self.dut, host)
            asserts.assert_true(status, "Failed to ping host %s" % host)
            self.log.info("Ping successful")

        # stop tcpdump
        pcap_file = stop_tcpdump(self.dut, self.tcpdump_pid, self.test_name)

        # verify DNS queries
        overhttps = dns_mode != cconst.PRIVATE_DNS_MODE_OFF
        self._verify_doh_queries(pcap_file, overhttps)

        # reset wifi
        wutils.reset_wifi(self.dut)

    def _verify_tls_completed(self, retry_count=5):
        """Verify tls finish verification process.

        Expect all private dns server status, should be
        "success", or "fail".

        Args:
            retry_count: int for retry times.
        Raises:
            TimeoutError: if TLS verification stuck in processing.
        """
        for attempt in range(retry_count):
            out = self.dut.adb.shell("dumpsys dnsresolver")
            if "status{in_process}" in out:
                if attempt + 1 < retry_count:
                    self.dut.log.info("DoT still validating, retrying...")
                    time.sleep(DEFAULT_DNS_TIMEOUT)
            else:
                return
        raise TimeoutError("Fail to completed TLS verification.")

    def _setup_doh(self, ad, net, enable=True):
        """Enable/Disable DoH option.

        Args:
            ad: android devies.
            net: network as wifi.
            enable: if True, sets the 'doh' experiment flag.
        """
        if enable:
            ad.adb.shell("setprop persist.device_config.netd_native.doh 1")
        else:
            ad.adb.shell("setprop persist.device_config.netd_native.doh 0")
        wutils.wifi_connect(ad, net)
        wutils.reset_wifi(ad)
        out = ad.adb.shell("dumpsys dnsresolver |grep doh")
        ad.log.debug(out)

    def test_mix_server_ipv4_only_wifi_network_with_dns_strict_mode(self):
        """Test doh flag with below situation.

        - Android device in strict mode
        - DNS server supporting both Dns, DoT and DoH protocols
        - IPv4-only network
        """
        self._test_public_doh_mode(self.dut, self.get_wifi_network(),
                                   cconst.PRIVATE_DNS_MODE_STRICT,
                                   hostname=cconst.DNS_GOOGLE_HOSTNAME)

    def test_mix_server_ipv4_ipv6_wifi_network_with_dns_strict_mode(self):
        """Test doh flag with below situation.

        - Android device in strict mode
        - DNS server supporting both Dns, DoT and DoH protocols
        - IPv4-IPv6 network
        """
        self._test_public_doh_mode(self.dut, self.get_wifi_network(),
                                   cconst.PRIVATE_DNS_MODE_STRICT,
                                   hostname=cconst.DNS_GOOGLE_HOSTNAME)

    def test_pure_server_ipv4_only_wifi_network_with_dns_strict_mode(self):
        """Test doh flag with below situation.

        - Android device in strict mode
        - DNS server only supporting DoH protocols
        - IPv4-only network
        """
        self._test_public_doh_mode(self.dut, self.get_wifi_network(),
                                   cconst.PRIVATE_DNS_MODE_STRICT,
                                   hostname=cconst.DOH_CLOUDFLARE_HOSTNAME)

    def test_pure_server_ipv4_ipv6_wifi_network_with_dns_strict_mode(self):
        """Test doh flag with below situation.

        - Android device in strict mode
        - DNS server only supporting DoH protocols
        - IPv4-IPv6 network
        """
        self._test_public_doh_mode(self.dut, self.get_wifi_network(),
                                   cconst.PRIVATE_DNS_MODE_STRICT,
                                   hostname=cconst.DOH_CLOUDFLARE_HOSTNAME)

    def test_mix_server_ipv4_only_wifi_network_with_dns_opportunistic_mode(self):
        """Test doh flag with below situation.

        - Android device in opportunistic mode
        - DNS server supporting both Dns, DoT and DoH protocols
        - IPv4-only network
        """
        self.default_dns = cconst.DNS_GOOGLE_ADDR_V4
        self._test_public_doh_mode(self.dut, self.get_wifi_network(),
                                   cconst.PRIVATE_DNS_MODE_OPPORTUNISTIC)

    def test_mix_server_ipv4_ipv6_wifi_network_with_dns_opportunistic_mode(self):
        """Test doh flag with below situation.

        - Android device in opportunistic mode
        - DNS server supporting both Dns, DoT and DoH protocols
        - IPv4-IPv6 network
        """
        self.default_dns = cconst.DNS_GOOGLE_ADDR_V4
        self.default_dns_v6 = cconst.DNS_GOOGLE_ADDR_V6
        self._test_public_doh_mode(self.dut, self.get_wifi_network(),
                                   cconst.PRIVATE_DNS_MODE_OPPORTUNISTIC)

    def test_mix_server_ipv4_only_wifi_network_with_dns_off_mode(self):
        """Test doh with below situation.

        - Android device in dns off mode
        - DNS server supporting both Dns, DoT and DoH protocols
        - IPv4-only network
        """
        self.default_dns = cconst.DNS_GOOGLE_ADDR_V4
        self._test_public_doh_mode(self.dut, self.get_wifi_network(),
                                   cconst.PRIVATE_DNS_MODE_OFF)

    def test_mix_server_ipv4_ipv6_wifi_network_with_dns_off_mode(self):
        """Test doh with below situation.

        - Android device in dns off mode
        - DNS server supporting both Dns, DoT and DoH protocols
        - IPv4-IPv6 network
        """
        self.default_dns = cconst.DNS_GOOGLE_ADDR_V4
        self.default_dns_v6 = cconst.DNS_GOOGLE_ADDR_V6
        self._test_public_doh_mode(self.dut, self.get_wifi_network(),
                                   cconst.PRIVATE_DNS_MODE_OFF)
