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


from acts import asserts
from acts.controllers.openwrt_ap import MOBLY_CONTROLLER_CONFIG_NAME as OPENWRT
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest


class MulticastDNSTest(WifiBaseTest):
    """Verify Multicast DNS can work on Android devices."""

    def setup_class(self):
        """Setup Openwrt and unpack params for mDNS test."""
        self.dut = self.android_devices[0]
        req_params = []
        opt_params = ["configure_OpenWrt", "wifi_network"]
        self.unpack_userparams(req_params, opt_params)
        if OPENWRT in self.user_params:
            self.openwrt = self.access_points[0]
            if hasattr(self, "configure_OpenWrt") and self.configure_OpenWrt == "skip":
                self.dut.log.info("Skip configure Wifi interface due to config setup.")
            else:
                self.configure_openwrt_ap_and_start(wpa_network=True)
                self.wifi_network = self.openwrt.get_wifi_network()

    def on_fail(self, test_name, begin_time):
        """Take bugreport if test failed."""
        self.dut.take_bug_report(test_name, begin_time)

    def teardown_test(self):
        """Reset wifi settings after test case."""
        wutils.reset_wifi(self.dut)

    def verify_ping(self, hostname, expect_ping_pass=True):
        """Verify if result of the ping as excepted.

        Args:
            hostname: ping address.
            expect_ping_pass: excepted ping result is True or False.
        Returns:
            Boolean if ping result work as expected.
        """
        out = self.dut.adb.shell("ping -c 1 %s" % hostname)
        result = ("100%" not in out) == expect_ping_pass
        if not result:
            self.dut.log.info(out)
        return result

    def test_mdns_query_ipv4_only(self):
        """Verify mdns query work in ipv4 only network."""
        self.openwrt.network_setting.disable_ipv6()
        self.openwrt.network_setting.setup_mdns()
        wutils.wifi_connect(self.dut, self.wifi_network)
        asserts.assert_true(self.verify_ping("openwrt.local"),
                            "Fail to ping openwrt.local.")

    def test_mdns_query_ipv4_ipv6(self):
        """Verify mdns query work in ipv4 & ipv6 network."""
        self.openwrt.network_setting.enable_ipv6()
        self.openwrt.network_setting.setup_mdns()
        wutils.wifi_connect(self.dut, self.wifi_network)
        asserts.assert_true(self.verify_ping("openwrt.local"),
                            "Fail to ping openwrt.local.")

