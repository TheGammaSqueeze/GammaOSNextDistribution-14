#   !/usr/bin/env python3.4
#
#   Copyright 2017 - The Android Open Source Project
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

from acts.libs.ota import ota_updater
import acts.signals as signals
from acts.test_decorators import test_tracker_info
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
import acts.utils as utils
from WifiAutoUpdateTest import WifiAutoUpdateTest

WifiEnums = wutils.WifiEnums
SSID = WifiEnums.SSID_KEY
PWD = WifiEnums.PWD_KEY
NETID = WifiEnums.NETID_KEY
# Default timeout used for reboot, toggle WiFi and Airplane mode,
# for the system to settle down after the operation.
EAP = WifiEnums.Eap
Ent = WifiEnums.Enterprise
WPA3_SECURITY = "SUITE_B_192"


class WifiWpa3AutoUpdateTest(WifiAutoUpdateTest):
    """Tests for APIs in Android's WifiManager class.

    Test Bed Requirement:
    * One Android device
    * Several Wi-Fi networks visible to the device, including an open Wi-Fi
      network.
    """

    def __init__(self, configs):
        super().__init__(configs)
        self.tests = ("test_check_wpa3_wifi_state_after_au",
                      "test_verify_wpa3_networks_after_au",
                      "test_wpa3_configstore_after_au",
                      "test_all_wpa3_networks_connectable_after_au",
                      "test_check_wpa3_wifi_toggling_after_au",
                      "test_wpa3_connection_to_new_networks",
                      "test_reset_wpa3_wifi_after_au")

    def setup_class(self):
        super(WifiBaseTest, self).setup_class()
        ota_updater.initialize(self.user_params, self.android_devices)
        self.dut = self.android_devices[0]
        self.dut_client = self.android_devices[1]
        wutils.wifi_test_device_init(self.dut)
        wutils.wifi_toggle_state(self.dut, True)

        # configure APs
        req_params = [
            "ec2_ca_cert", "ec2_client_cert", "ec2_client_key",
            "rsa3072_ca_cert", "rsa3072_client_cert", "rsa3072_client_key",
            "wpa3_ec2_network", "wpa3_rsa3072_network", "rsa2048_client_cert",
            "rsa2048_client_key", "rsa3072_client_cert_expired",
            "rsa3072_client_cert_corrupted", "rsa3072_client_cert_unsigned",
            "rsa3072_client_key_unsigned", "wpa3_sae_gcmp_128",
            "wpa3_sae_gcmp_256", "owe_networks", "sae_networks"
        ]
        self.unpack_userparams(req_param_names=req_params)
        self.owe_2g = self.owe_networks[0]["2g"]
        self.owe_5g = self.owe_networks[0]["5g"]
        self.wpa3_personal_2g = self.sae_networks[0]["2g"]
        self.wpa3_personal_5g = self.sae_networks[0]["5g"]

        self.config_rsa3072_tls = {
            Ent.EAP: int(EAP.TLS),
            Ent.CA_CERT: self.rsa3072_ca_cert,
            WifiEnums.SSID_KEY: self.wpa3_rsa3072_network[WifiEnums.SSID_KEY],
            Ent.CLIENT_CERT: self.rsa3072_client_cert,
            Ent.PRIVATE_KEY_ID: self.rsa3072_client_key,
            WifiEnums.SECURITY: WPA3_SECURITY,
            "identity": self.wpa3_rsa3072_network["identity"],
            "domain_suffix_match": self.wpa3_rsa3072_network["domain"]
        }

        # saved & connected networks, network suggestions
        # and new networks
        self.saved_networks = [self.wpa3_sae_gcmp_256]
        self.network_suggestions = [self.owe_2g]
        self.connected_networks = [
            self.config_rsa3072_tls, self.wpa3_personal_5g
        ]
        self.new_networks = [self.wpa3_personal_2g]
        # add pre ota upgrade configuration
        self.wifi_config_list = []
        self.pre_default_mac = {}
        self.pre_random_mac = {}
        self.pst_default_mac = {}
        self.pst_random_mac = {}
        self.add_pre_update_configuration()

        # Run OTA below, if ota fails then abort all tests.
        try:
            ota_updater.update(self.dut)
        except Exception as e:
            raise signals.TestAbortClass(
                "Failed up apply OTA update. Aborting tests: %s" % e)

    ### Tests

    @test_tracker_info(uuid="4d17a21c-3db6-4336-84ac-f3317e4a7fca")
    @WifiBaseTest.wifi_test_wrap
    def test_check_wpa3_wifi_state_after_au(self):
        """Check if the state of WiFi is enabled after Auto-update."""
        if not self.dut.droid.wifiCheckState():
            raise signals.TestFailure("WiFi is disabled after Auto-update!!!")

    @test_tracker_info(uuid="4dd106b0-6390-47d2-9b6d-00f21a0535f1")
    @WifiBaseTest.wifi_test_wrap
    def test_verify_wpa3_networks_after_au(self):
        """Check if the previously added networks are intact.

           Steps:
               Number of networs should be the same and match each network.

        """
        self.check_networks_after_autoupdate(self.wifi_config_list)

    @test_tracker_info(uuid="4e5107d1-17cc-4c4d-aee5-38052dec5ddd")
    @WifiBaseTest.wifi_test_wrap
    def test_wpa3_configstore_after_au(self):
        """Verify DUT automatically connects to wifi networks after ota.

           Steps:
               1. Connect to two wifi networks pre ota.
               2. Verify DUT automatically connects to 1 after ota.
               3. Re-connect to the other wifi network.
        """
        wifi_info = self.dut.droid.wifiGetConnectionInfo()
        self.pst_default_mac[wifi_info[SSID]] = self.get_sta_mac_address()
        self.pst_random_mac[wifi_info[SSID]] = \
            self.dut.droid.wifigetRandomizedMacAddress(wifi_info)
        reconnect_to = self.get_enabled_network(self.wifi_config_list[1],
                                                self.wifi_config_list[2])
        wutils.start_wifi_connection_scan_and_ensure_network_found(
            self.dut, reconnect_to[SSID])

        if reconnect_to[SSID] == self.connected_networks[0][SSID]:
            wutils.wifi_connect(self.dut,
                                self.connected_networks[0],
                                num_of_tries=6)
        else:
            wutils.wifi_connect(self.dut,
                                self.connected_networks[1],
                                num_of_tries=6)
        connect_data = self.dut.droid.wifiGetConnectionInfo()
        connect_ssid = connect_data[SSID]
        self.log.info("Expected SSID = %s" % reconnect_to[SSID])
        self.log.info("Connected SSID = %s" % connect_ssid)
        if connect_ssid != reconnect_to[SSID]:
            raise signals.TestFailure(
                "Device failed to reconnect to the correct"
                " network after reboot.")
        self.pst_default_mac[wifi_info[SSID]] = self.get_sta_mac_address()
        self.pst_random_mac[wifi_info[SSID]] = \
            self.dut.droid.wifigetRandomizedMacAddress(wifi_info)

        for network in self.connected_networks:
            wutils.wifi_forget_network(self.dut, network[SSID])

    @test_tracker_info(uuid="f1b59dde-b019-46c4-84b8-cf20f4afa08a")
    @WifiBaseTest.wifi_test_wrap
    def test_wpa3_connection_to_new_networks(self):
        """Check if we can connect to new networks after Auto-update.

           Steps:
               1. Connect to a wpa3 network.
               2. Forget ntworks added in 1.
        """
        for network in self.new_networks:
            wutils.connect_to_wifi_network(self.dut, network)
        for network in self.new_networks:
            wutils.wifi_forget_network(self.dut, network[SSID])

    @test_tracker_info(uuid="542a39c3-eea0-445c-89ae-8c74c6afb0bf")
    @WifiBaseTest.wifi_test_wrap
    def test_all_wpa3_networks_connectable_after_au(self):
        """Check if previously added networks are connectable.

           Steps:
               1. Connect to previously added wpa3 network using network id.
        """
        network = self.wifi_config_list[0]
        if not wutils.connect_to_wifi_network_with_id(self.dut, network[NETID],
                                                      network[SSID]):
            raise signals.TestFailure("Failed to connect to %s after OTA" %
                                      network[SSID])
        wutils.wifi_forget_network(self.dut, network[SSID])

    @test_tracker_info(uuid="68a34667-aca2-4630-b2fa-c25f1d234a92")
    @WifiBaseTest.wifi_test_wrap
    def test_check_wpa3_wifi_toggling_after_au(self):
        """Check if WiFi can be toggled ON/OFF after auto-update."""
        self.log.debug("Going from on to off.")
        wutils.wifi_toggle_state(self.dut, False)
        self.log.debug("Going from off to on.")
        wutils.wifi_toggle_state(self.dut, True)

    @test_tracker_info(uuid="39ba98de-cb49-4475-a218-7470122af885")
    @WifiBaseTest.wifi_test_wrap
    def test_reset_wpa3_wifi_after_au(self):
        """"Check if WiFi can be reset after auto-update."""
        wutils.reset_wifi(self.dut)
