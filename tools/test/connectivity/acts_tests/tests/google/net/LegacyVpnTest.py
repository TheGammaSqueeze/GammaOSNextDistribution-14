#
#   Copyright 2016 - The Android Open Source Project
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

from acts.controllers.openwrt_ap import MOBLY_CONTROLLER_CONFIG_NAME as OPENWRT
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.net import connectivity_const
from acts_contrib.test_utils.net import net_test_utils as nutils
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest


VPN_CONST = connectivity_const.VpnProfile
VPN_TYPE = connectivity_const.VpnProfileType
VPN_PARAMS = connectivity_const.VpnReqParams


class LegacyVpnTest(WifiBaseTest):
    """Tests for Legacy VPN in Android.

        Testbed requirement:
            1. One Android device
            2. A Wi-Fi network that can reach the VPN servers
    """

    def setup_class(self):
        """Setup wi-fi connection and unpack params."""
        self.dut = self.android_devices[0]
        req_params = dir(VPN_PARAMS)
        req_params = [
            x for x in req_params if not x.startswith("__")
        ]
        opt_params = ["wifi_network", "vpn_cert_country",
                      "vpn_cert_org", "configure_OpenWrt"]
        self.unpack_userparams(req_param_names=req_params,
                               opt_param_names=opt_params)

        wutils.wifi_test_device_init(self.dut)
        wutils.wifi_toggle_state(self.dut, True)
        if OPENWRT in self.user_params:
            self.openwrt = self.access_points[0]
            if hasattr(self, "configure_OpenWrt") and self.configure_OpenWrt == "skip":
                self.dut.log.info("Skip configure Wifi interface due to config setup.")
            else:
                self.configure_openwrt_ap_and_start(wpa_network=True)
                self.wifi_network = self.openwrt.get_wifi_network()

            # Wait for OpenWrt statement update
            time.sleep(10)
            self.openwrt.network_setting.setup_vpn_pptp_server(
                self.vpn_verify_addresses["PPTP"][0],
                self.vpn_username,
                self.vpn_password
            )
            self.openwrt.network_setting.setup_vpn_l2tp_server(
                self.vpn_server_hostname,
                self.vpn_verify_addresses["L2TP_IPSEC_RSA"][0],
                self.vpn_username,
                self.vpn_password,
                self.vpn_identity,
                "l2tp-server",
                self.vpn_cert_country,
                self.vpn_cert_org
            )
        wutils.start_wifi_connection_scan_and_ensure_network_found(
            self.dut, self.wifi_network["SSID"])
        wutils.wifi_connect(self.dut, self.wifi_network)
        time.sleep(3)

        self.vpn_params = {"vpn_username": self.vpn_username,
                           "vpn_password": self.vpn_password,
                           "psk_secret": self.psk_secret,
                           "client_pkcs_file_name": self.client_pkcs_file_name,
                           "cert_path_vpnserver": self.cert_path_vpnserver,
                           "cert_password": self.cert_password}

    def teardown_class(self):
        """Reset wifi to make sure VPN tears down cleanly."""
        wutils.reset_wifi(self.dut)

    def on_fail(self, test_name, begin_time):
        self.dut.take_bug_report(test_name, begin_time)

    @test_tracker_info(uuid="d2ac5a65-41fb-48de-a0a9-37e589b5456b")
    def test_legacy_vpn_pptp(self):
        """Verify PPTP VPN connection."""
        vpn = VPN_TYPE.PPTP
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][0],
            self.ipsec_server_type[2],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][0]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)

    @test_tracker_info(uuid="99af78dd-40b8-483a-8344-cd8f67594971")
    def legacy_vpn_l2tp_ipsec_psk_libreswan(self):
        """Verify L2TP IPSec PSK VPN connection to libreSwan server."""
        vpn = VPN_TYPE.L2TP_IPSEC_PSK
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][2],
            self.ipsec_server_type[2],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][2]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)

    @test_tracker_info(uuid="e67d8c38-92c3-4167-8b6c-a49ef939adce")
    def legacy_vpn_l2tp_ipsec_rsa_libreswan(self):
        """Verify L2TP IPSec RSA VPN connection to libreSwan server."""
        vpn = VPN_TYPE.L2TP_IPSEC_RSA
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][2],
            self.ipsec_server_type[2],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][2]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)

    @test_tracker_info(uuid="8b3517dc-6a3b-44c2-a85d-bd7b969df3cf")
    def legacy_vpn_ipsec_xauth_psk_libreswan(self):
        """Verify IPSec XAUTH PSK VPN connection to libreSwan server."""
        vpn = VPN_TYPE.IPSEC_XAUTH_PSK
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][2],
            self.ipsec_server_type[2],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][2]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)

    @test_tracker_info(uuid="abac663d-1d91-4b87-8e94-11c6e44fb07b")
    def legacy_vpn_ipsec_xauth_rsa_libreswan(self):
        """Verify IPSec XAUTH RSA VPN connection to libreSwan server."""
        vpn = VPN_TYPE.IPSEC_XAUTH_RSA
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][2],
            self.ipsec_server_type[2],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][2]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)

    @test_tracker_info(uuid="84140d24-53c0-4f6c-866f-9d66e04442cc")
    def test_legacy_vpn_l2tp_ipsec_psk_openswan(self):
        """Verify L2TP IPSec PSK VPN connection to openSwan server."""
        vpn = VPN_TYPE.L2TP_IPSEC_PSK
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][1],
            self.ipsec_server_type[1],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][1]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)

    @test_tracker_info(uuid="f7087592-7eed-465d-bfe3-ed7b6d9d5f9a")
    def test_legacy_vpn_l2tp_ipsec_rsa_openswan(self):
        """Verify L2TP IPSec RSA VPN connection to openSwan server."""
        vpn = VPN_TYPE.L2TP_IPSEC_RSA
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][1],
            self.ipsec_server_type[1],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][1]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)

    @test_tracker_info(uuid="ed78973b-13ee-4dd4-b998-693ab741c6f8")
    def test_legacy_vpn_ipsec_xauth_psk_openswan(self):
        """Verify IPSec XAUTH PSK VPN connection to openSwan server."""
        vpn = VPN_TYPE.IPSEC_XAUTH_PSK
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][1],
            self.ipsec_server_type[1],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][1]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)

    @test_tracker_info(uuid="cfd125c4-b64c-4c49-b8e4-fbf05a9be8ec")
    def test_legacy_vpn_ipsec_xauth_rsa_openswan(self):
        """Verify IPSec XAUTH RSA VPN connection to openSwan server."""
        vpn = VPN_TYPE.IPSEC_XAUTH_RSA
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][1],
            self.ipsec_server_type[1],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][1]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)

    @test_tracker_info(uuid="419370de-0aa1-4a56-8c22-21567fa1cbb7")
    def test_legacy_vpn_l2tp_ipsec_psk_strongswan(self):
        """Verify L2TP IPSec PSk VPN connection to strongSwan server."""
        vpn = VPN_TYPE.L2TP_IPSEC_PSK
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][0],
            self.ipsec_server_type[0],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][0]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)

    @test_tracker_info(uuid="f7694081-8bd6-4e31-86ec-d538c4ff1f2e")
    def test_legacy_vpn_l2tp_ipsec_rsa_strongswan(self):
        """Verify L2TP IPSec RSA VPN connection to strongSwan server."""
        vpn = VPN_TYPE.L2TP_IPSEC_RSA
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][0],
            self.ipsec_server_type[0],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][0]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)

    @test_tracker_info(uuid="2f86eb98-1e05-42cb-b6a6-fd90789b6cde")
    def test_legacy_vpn_ipsec_xauth_psk_strongswan(self):
        """Verify IPSec XAUTH PSK connection to strongSwan server."""
        vpn = VPN_TYPE.IPSEC_XAUTH_PSK
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][0],
            self.ipsec_server_type[0],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][0]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)

    @test_tracker_info(uuid="af0cd7b1-e86c-4327-91b4-e9062758f2cf")
    def test_legacy_vpn_ipsec_xauth_rsa_strongswan(self):
        """Verify IPSec XAUTH RSA connection to strongswan server."""
        vpn = VPN_TYPE.IPSEC_XAUTH_RSA
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][0],
            self.ipsec_server_type[0],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][0]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)

    @test_tracker_info(uuid="7b970d0a-1c7d-4a5a-b406-4815e190ef26")
    def test_legacy_vpn_ipsec_hybrid_rsa_strongswan(self):
        """Verify IPSec Hybrid RSA connection to strongswan server."""
        vpn = VPN_TYPE.IPSEC_HYBRID_RSA
        vpn_profile = nutils.generate_legacy_vpn_profile(
            self.dut, self.vpn_params,
            vpn, self.vpn_server_addresses[vpn.name][0],
            self.ipsec_server_type[0],
            self.log_path)
        vpn_addr = self.vpn_verify_addresses[vpn.name][0]
        nutils.legacy_vpn_connection_test_logic(self.dut, vpn_profile, vpn_addr)
