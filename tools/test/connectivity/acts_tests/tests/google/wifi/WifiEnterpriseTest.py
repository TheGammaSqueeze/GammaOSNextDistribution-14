#!/usr/bin/env python3.4
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
import acts.utils

from acts import asserts
from acts import signals
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest

WifiEnums = wutils.WifiEnums
DEFAULT_TIMEOUT = 10

# EAP Macros
EAP = WifiEnums.Eap
EapPhase2 = WifiEnums.EapPhase2
# Enterprise Config Macros
Ent = WifiEnums.Enterprise


class WifiEnterpriseTest(WifiBaseTest):
    def setup_class(self):
        super().setup_class()
        self.enable_packet_log = True

        self.dut = self.android_devices[0]
        # If running in a setup with attenuators, set attenuation on all
        # channels to zero.
        if getattr(self, "attenuators", []):
            for a in self.attenuators:
                a.set_atten(0)
        required_userparam_names = (
            "ca_cert", "client_cert", "client_key", "eap_identity",
            "eap_password", "invalid_ca_cert", "invalid_client_cert",
            "invalid_client_key", "device_password", "radius_conf_2g",
            "radius_conf_5g", "radius_conf_pwd", "wifi6_models", "sim_supported_models")
        opt_param_names = ["domain_suffix_match"]
        self.unpack_userparams(required_userparam_names,
                               opt_param_names,
                               roaming_consortium_ids=None,
                               plmn=None,
                               ocsp=0)

        if "AccessPoint" in self.user_params:
            self.legacy_configure_ap_and_start(
                ent_network=True,
                radius_conf_2g=self.radius_conf_2g,
                radius_conf_5g=self.radius_conf_5g,
                ent_network_pwd=True,
                radius_conf_pwd=self.radius_conf_pwd,
                wpa_network=True,
                ap_count=2,
            )
        elif "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(
                ent_network=True,
                radius_conf_2g=self.radius_conf_2g,
                radius_conf_5g=self.radius_conf_5g,
                ent_network_pwd=True,
                radius_conf_pwd=self.radius_conf_pwd,
                wpa_network=True,
                ap_count=2,
            )
        self.ent_network_2g = self.ent_networks[0]["2g"]
        self.ent_network_5g = self.ent_networks[0]["5g"]
        self.ent_network_pwd = self.ent_networks_pwd[0]["2g"]
        if hasattr(self, "reference_networks") and \
            isinstance(self.reference_networks, list):
              self.wpa_psk_2g = self.reference_networks[0]["2g"]
              self.wpa_psk_5g = self.reference_networks[0]["5g"]

        # Default configs for EAP networks.
        self.config_peap0 = {
            Ent.EAP: int(EAP.PEAP),
            Ent.CA_CERT: self.ca_cert,
            Ent.IDENTITY: self.eap_identity,
            Ent.PASSWORD: self.eap_password,
            Ent.PHASE2: int(EapPhase2.MSCHAPV2),
            WifiEnums.SSID_KEY: self.ent_network_5g[WifiEnums.SSID_KEY],
            Ent.OCSP: self.ocsp,
            Ent.DOM_SUFFIX_MATCH: self.domain_suffix_match,
        }
        self.config_peap1 = dict(self.config_peap0)
        self.config_peap1[WifiEnums.SSID_KEY] = \
            self.ent_network_2g[WifiEnums.SSID_KEY]
        self.config_tls = {
            Ent.EAP: int(EAP.TLS),
            Ent.CA_CERT: self.ca_cert,
            WifiEnums.SSID_KEY: self.ent_network_2g[WifiEnums.SSID_KEY],
            Ent.CLIENT_CERT: self.client_cert,
            Ent.PRIVATE_KEY_ID: self.client_key,
            Ent.IDENTITY: self.eap_identity,
            Ent.OCSP: self.ocsp,
            Ent.DOM_SUFFIX_MATCH: self.domain_suffix_match,
        }
        self.config_ttls = {
            Ent.EAP: int(EAP.TTLS),
            Ent.CA_CERT: self.ca_cert,
            Ent.IDENTITY: self.eap_identity,
            Ent.PASSWORD: self.eap_password,
            Ent.PHASE2: int(EapPhase2.MSCHAPV2),
            WifiEnums.SSID_KEY: self.ent_network_2g[WifiEnums.SSID_KEY],
            Ent.OCSP: self.ocsp,
            Ent.DOM_SUFFIX_MATCH: self.domain_suffix_match,
        }
        self.config_pwd = {
            Ent.EAP: int(EAP.PWD),
            Ent.IDENTITY: self.eap_identity,
            Ent.PASSWORD: self.eap_password,
            WifiEnums.SSID_KEY: self.ent_network_pwd[WifiEnums.SSID_KEY],
        }
        self.config_sim = {
            Ent.EAP: int(EAP.SIM),
            WifiEnums.SSID_KEY: self.ent_network_2g[WifiEnums.SSID_KEY],
        }
        self.config_aka = {
            Ent.EAP: int(EAP.AKA),
            WifiEnums.SSID_KEY: self.ent_network_2g[WifiEnums.SSID_KEY],
        }
        self.config_aka_prime = {
            Ent.EAP: int(EAP.AKA_PRIME),
            WifiEnums.SSID_KEY: self.ent_network_2g[WifiEnums.SSID_KEY],
        }

        # Set screen lock password so ConfigStore is unlocked.
        self.dut.droid.setDevicePassword(self.device_password)

    def teardown_class(self):
        wutils.reset_wifi(self.dut)
        self.dut.droid.disableDevicePassword(self.device_password)
        self.dut.ed.clear_all_events()

    def setup_test(self):
        super().setup_test()
        self.dut.droid.wifiStartTrackingStateChange()
        self.dut.droid.wakeLockAcquireBright()
        self.dut.droid.wakeUpNow()
        wutils.reset_wifi(self.dut)
        self.dut.ed.clear_all_events()

    def teardown_test(self):
        super().teardown_test()
        self.dut.droid.wakeLockRelease()
        self.dut.droid.goToSleepNow()
        self.dut.droid.wifiStopTrackingStateChange()
        # Turn off airplane mode
        acts.utils.force_airplane_mode(self.dut, False)
        wutils.set_attns(self.attenuators, "default")

    """Helper Functions"""

    def eap_negative_connect_logic(self, config, ad):
        """Tries to connect to an enterprise network with invalid credentials
        and expect a failure.

        Args:
            config: A dict representing an invalid EAP credential.

        Returns:
            True if connection failed as expected, False otherwise.
        """
        with asserts.assert_raises(signals.TestFailure, extras=config):
            verdict = wutils.wifi_connect(ad, config)
        asserts.explicit_pass("Connection failed as expected.")

    def gen_negative_configs(self, config, neg_params):
        """Generic function used to generate negative configs.

        For all the valid configurations, if a param in the neg_params also
        exists in a config, a copy of the config is made with an invalid value
        of the param.

        Args:
            config: A valid configuration.
            neg_params: A dict that has all the invalid values.

        Returns:
            An invalid configurations generated based on the valid
            configuration. Each invalid configuration has a different invalid
            field.
        """
        negative_config = dict(config)
        if negative_config in [
                self.config_sim, self.config_aka, self.config_aka_prime
        ]:
            negative_config[WifiEnums.SSID_KEY] = 'wrong_hostapd_ssid'
        for k, v in neg_params.items():
            # Skip negative test for TLS's identity field since it's not
            # used for auth.
            if config[Ent.EAP] == EAP.TLS and k == Ent.IDENTITY:
                continue
            if k in config:
                negative_config[k] = v
                negative_config["invalid_field"] = k
        return negative_config

    def gen_negative_eap_configs(self, config):
        """Generates invalid configurations for different EAP authentication
        types.

        For all the valid EAP configurations, if a param that is part of the
        authentication info exists in a config, a copy of the config is made
        with an invalid value of the param.

        Args:
            A valid network configration

        Returns:
            An invalid EAP configuration.
        """
        neg_params = {
            Ent.CLIENT_CERT: self.invalid_client_cert,
            Ent.CA_CERT: self.invalid_ca_cert,
            Ent.PRIVATE_KEY_ID: self.invalid_client_key,
            Ent.IDENTITY: "fake_identity",
            Ent.PASSWORD: "wrong_password"
        }
        return self.gen_negative_configs(config, neg_params)

    def eap_connect_toggle_wifi(self, config, *args):
        """Connects to an enterprise network, toggles wifi state and ensures
        that the device reconnects.

        This logic expect the enterprise network to have Internet access.

        Args:
            config: A dict representing a wifi enterprise configuration.
            args: args to be passed to |wutils.eap_connect|.

        Returns:
            True if the connection is successful and Internet access works.
        """
        ad = args[0]
        wutils.wifi_connect(ad, config)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)
        wutils.toggle_wifi_and_wait_for_reconnection(ad,
                                                     config,
                                                     num_of_tries=5)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    def toggle_out_of_range_stress(self, stress_count=3):
        """toggle_out_of_range_stress."""
        current_network = self.dut.droid.wifiGetConnectionInfo()
        self.log.info("Current network: {}".format(current_network))
        for count in range(stress_count):
            # move the DUT out of range
            self.attenuators[0].set_atten(95)
            self.attenuators[1].set_atten(95)
            self.attenuators[2].set_atten(95)
            self.attenuators[3].set_atten(95)
            time.sleep(20)
            try:
                wutils.start_wifi_connection_scan(self.dut)
                wifi_results = self.dut.droid.wifiGetScanResults()
                self.log.debug("Scan result {}".format(wifi_results))
                time.sleep(20)
                current_network = self.dut.droid.wifiGetConnectionInfo()
                self.log.info("Current network: {}".format(current_network))
                asserts.assert_true(
                    ('network_id' in current_network and
                    current_network['network_id'] == -1),
                    "Device is connected to network {}".format(current_network))
                time.sleep(DEFAULT_TIMEOUT)
            finally:
                self.dut.droid.wifiLockRelease()
            # move the DUT back in range
            wutils.set_attns(self.attenuators, "default")
            time.sleep(30)
            try:
                wutils.start_wifi_connection_scan(self.dut)
                wifi_results = self.dut.droid.wifiGetScanResults()
                self.log.debug("Scan result {}".format(wifi_results))
                time.sleep(20)
                current_network = self.dut.droid.wifiGetConnectionInfo()
                self.log.info("Current network: {}".format(current_network))
                asserts.assert_true(
                    ('network_id' in current_network and
                    current_network['network_id'] != -1),
                    "Device is disconnected to network {}".format(current_network))
                time.sleep(DEFAULT_TIMEOUT)
            finally:
                self.dut.droid.wifiLockRelease()

    def check_connection(self, network_bssid):
        """Check current wifi connection networks.
        Args:
            network_bssid: Network bssid to which connection.
        Returns:
            True if connection to given network happen, else return False.
        """
        time.sleep(10)  #time for connection state to be updated
        self.log.info("Check network for {}".format(network_bssid))
        current_network = self.dut.droid.wifiGetConnectionInfo()
        self.log.debug("Current network:  {}".format(current_network))
        if WifiEnums.BSSID_KEY in current_network:
            return current_network[WifiEnums.BSSID_KEY] == network_bssid
        return False

    """ Tests """

    # EAP connect tests
    """ Test connecting to enterprise networks of different authentication
        types.

        The authentication types tested are:
            EAP-TLS
            EAP-PEAP with different phase2 types.
            EAP-TTLS with different phase2 types.

        Procedures:
            For each enterprise wifi network
            1. Connect to the network.
            2. Send a GET request to a website and check response.

        Expect:
            Successful connection and Internet access through the enterprise
            networks.
    """

    @test_tracker_info(uuid="4e720cac-ea17-4de7-a540-8dc7c49f9713")
    def test_eap_connect_with_config_tls(self):
        wutils.wifi_connect(self.dut, self.config_tls)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="10e3a5e9-0018-4162-a9fa-b41500f13340")
    def test_eap_connect_with_config_pwd(self):
        wutils.wifi_connect(self.dut, self.config_pwd)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="b4513f78-a1c4-427f-bfc7-2a6b3da714b5")
    def test_eap_connect_with_config_sim(self):
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "DUT can not equip with mobile SIM card")
        wutils.wifi_connect(self.dut, self.config_sim)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="7d390e30-cb67-4b55-bf00-567adad2d9b0")
    def test_eap_connect_with_config_aka(self):
        wutils.wifi_connect(self.dut, self.config_aka)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="742f921b-27c3-4b68-a3ca-88e64fe79c1d")
    def test_eap_connect_with_config_aka_prime(self):
        wutils.wifi_connect(self.dut, self.config_aka_prime)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="d34e30f3-6ef6-459f-b47a-e78ed90ce4c6")
    def test_eap_connect_with_config_ttls_none(self):
        config = dict(self.config_ttls)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.NONE.value
        wutils.wifi_connect(self.dut, config)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="0dca3a15-472e-427c-8e06-4e38088ee973")
    def test_eap_connect_with_config_ttls_pap(self):
        config = dict(self.config_ttls)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.PAP.value
        wutils.wifi_connect(self.dut, config)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="47c4b459-2cb1-4fc7-b4e7-82534e8e090e")
    def test_eap_connect_with_config_ttls_mschap(self):
        config = dict(self.config_ttls)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.MSCHAP.value
        wutils.wifi_connect(self.dut, config)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="fdb286c7-8069-481d-baf0-c5dd7a31ff03")
    def test_eap_connect_with_config_ttls_mschapv2(self):
        config = dict(self.config_ttls)
        config[
            WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.MSCHAPV2.value
        wutils.wifi_connect(self.dut, config)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="d9315962-7987-4ee7-905d-6972c78ce8a1")
    def test_eap_connect_with_config_ttls_gtc(self):
        config = dict(self.config_ttls)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.GTC.value
        wutils.wifi_connect(self.dut, config)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="90a67bd3-30da-4daf-8ab0-d964d7ad19be")
    def test_eap_connect_with_config_peap0_mschapv2(self):
        config = dict(self.config_peap0)
        config[
            WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.MSCHAPV2.value
        wutils.wifi_connect(self.dut, config)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="3c451ba4-0c83-4eef-bc95-db4c21893008")
    def test_eap_connect_with_config_peap0_gtc(self):
        config = dict(self.config_peap0)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.GTC.value
        wutils.wifi_connect(self.dut, config)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="6b45157d-0325-417a-af18-11af5d240d79")
    def test_eap_connect_with_config_peap1_mschapv2(self):
        config = dict(self.config_peap1)
        config[
            WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.MSCHAPV2.value
        wutils.wifi_connect(self.dut, config)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="1663decc-71ae-4f95-a027-8a6dbf9c337f")
    def test_eap_connect_with_config_peap1_gtc(self):
        config = dict(self.config_peap1)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.GTC.value
        wutils.wifi_connect(self.dut, config)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    # EAP connect negative tests
    """ Test connecting to enterprise networks.

        Procedures:
            For each enterprise wifi network
            1. Connect to the network with invalid credentials.

        Expect:
            Fail to establish connection.
    """

    @test_tracker_info(uuid="b2a91f1f-ccd7-4bd1-ab81-19aab3d8ee38")
    def test_eap_connect_negative_with_config_tls(self):
        config = self.gen_negative_eap_configs(self.config_tls)
        self.eap_negative_connect_logic(config, self.dut)

    @test_tracker_info(uuid="b7fb8517-5d52-468e-890a-40ea24129bf1")
    def test_network_selection_status_wpa2_eap_tls_invalid_cert(self):
        config = self.gen_negative_eap_configs(self.config_tls)
        try:
            wutils.connect_to_wifi_network(self.dut, config)
            asserts.fail(
                "WPA2 EAP TLS worked with invalid cert. Expected to fail.")
        except:
            asserts.assert_true(
                self.dut.droid.wifiIsNetworkTemporaryDisabledForNetwork(config),
                "WiFi network is not temporary disabled.")
            asserts.explicit_pass(
                "Connection failed with correct network selection status.")

    @test_tracker_info(uuid="6466abde-1d16-4168-9dd8-1e7a0a19889b")
    def test_eap_connect_negative_with_config_pwd(self):
        config = self.gen_negative_eap_configs(self.config_pwd)
        self.eap_negative_connect_logic(config, self.dut)

    @test_tracker_info(uuid="d7742a2a-85b0-409a-99d8-47711ddc5612")
    def test_eap_connect_negative_with_config_sim(self):
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "DUT can not equip with mobile SIM card")
        config = self.gen_negative_eap_configs(self.config_sim)
        self.eap_negative_connect_logic(config, self.dut)

    @test_tracker_info(uuid="0ec0de93-cab3-4f41-960b-c0af64ff48c4")
    def test_eap_connect_negative_with_config_aka(self):
        config = self.gen_negative_eap_configs(self.config_aka)
        self.eap_negative_connect_logic(config, self.dut)

    @test_tracker_info(uuid="bb640ea4-32a6-48ea-87c9-f7128fffbbf6")
    def test_eap_connect_negative_with_config_aka_prime(self):
        config = self.gen_negative_eap_configs(self.config_aka_prime)
        self.eap_negative_connect_logic(config, self.dut)

    @test_tracker_info(uuid="86336ada-0ced-45a4-8a22-c4aa23c81111")
    def test_eap_connect_negative_with_config_ttls_none(self):
        config = dict(self.config_ttls)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.NONE.value
        config = self.gen_negative_eap_configs(config)
        self.eap_negative_connect_logic(config, self.dut)

    @test_tracker_info(uuid="71e0498d-9973-4958-94bd-79051c328920")
    def test_eap_connect_negative_with_config_ttls_pap(self):
        config = dict(self.config_ttls)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.PAP.value
        config = self.gen_negative_eap_configs(config)
        self.eap_negative_connect_logic(config, self.dut)

    @test_tracker_info(uuid="c04142a8-b204-4d2d-98dc-150b16c8397e")
    def test_eap_connect_negative_with_config_ttls_mschap(self):
        config = dict(self.config_ttls)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.MSCHAP.value
        config = self.gen_negative_eap_configs(config)
        self.eap_negative_connect_logic(config, self.dut)

    @test_tracker_info(uuid="625e7aa5-e3e6-4bbe-98c0-5aad8ca1555b")
    def test_eap_connect_negative_with_config_ttls_mschapv2(self):
        config = dict(self.config_ttls)
        config[
            WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.MSCHAPV2.value
        config = self.gen_negative_eap_configs(config)
        self.eap_negative_connect_logic(config, self.dut)

    @test_tracker_info(uuid="24ea0d80-0a3f-41c2-8e05-d6387e589058")
    def test_eap_connect_negative_with_config_ttls_gtc(self):
        config = dict(self.config_ttls)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.GTC.value
        config = self.gen_negative_eap_configs(config)
        self.eap_negative_connect_logic(config, self.dut)

    @test_tracker_info(uuid="b7c1f0f8-6338-4501-8e1d-c9b136aaba88")
    def test_eap_connect_negative_with_config_peap0_mschapv2(self):
        config = dict(self.config_peap0)
        config[
            WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.MSCHAPV2.value
        config = self.gen_negative_eap_configs(config)
        self.eap_negative_connect_logic(config, self.dut)

    @test_tracker_info(uuid="9cf83dcb-38ad-4f75-9ea9-98de1cfaf7f3")
    def test_eap_connect_negative_with_config_peap0_gtc(self):
        config = dict(self.config_peap0)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.GTC.value
        config = self.gen_negative_eap_configs(config)
        self.eap_negative_connect_logic(config, self.dut)

    @test_tracker_info(uuid="89bb2b6b-d073-402a-bdc1-68ac5f8752a3")
    def test_eap_connect_negative_with_config_peap1_mschapv2(self):
        config = dict(self.config_peap1)
        config[
            WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.MSCHAPV2.value
        config = self.gen_negative_eap_configs(config)
        self.eap_negative_connect_logic(config, self.dut)

    @test_tracker_info(uuid="2252a864-9ff7-43b5-82d9-afe57d1f5e5f")
    def test_eap_connect_negative_with_config_peap1_gtc(self):
        config = dict(self.config_peap1)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.GTC.value
        config = self.gen_negative_eap_configs(config)
        self.eap_negative_connect_logic(config, self.dut)

    # EAP connect config store tests
    """ Test connecting to enterprise networks of different authentication
        types after wifi toggle.

        The authentication types tested are:
            EAP-TLS
            EAP-PEAP with different phase2 types.
            EAP-TTLS with different phase2 types.

        Procedures:
            For each enterprise wifi network
            1. Connect to the network.
            2. Send a GET request to a website and check response.
            3. Toggle wifi.
            4. Ensure that the device reconnects to the same network.

        Expect:
            Successful connection and Internet access through the enterprise
            networks.
    """

    @test_tracker_info(uuid="2a933b7f-27d7-4201-a34f-25b9d8072a8c")
    def test_eap_connect_config_store_with_config_tls(self):
        self.eap_connect_toggle_wifi(self.config_tls, self.dut)

    @test_tracker_info(uuid="08dc071b-9fea-408a-a3f6-d3493869f6d4")
    def test_eap_connect_config_store_with_config_pwd(self):
        self.eap_connect_toggle_wifi(self.config_pwd, self.dut)

    @test_tracker_info(uuid="230cb03e-58bc-41cb-b9b3-7215c2ab2325")
    def test_eap_connect_config_store_with_config_sim(self):
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "DUT can not equip with mobile SIM card")
        self.eap_connect_toggle_wifi(self.config_sim, self.dut)

    @test_tracker_info(uuid="dfc3e59c-2309-4598-8c23-bb3fe95ef89f")
    def test_eap_connect_config_store_with_config_aka(self):
        self.eap_connect_toggle_wifi(self.config_aka, self.dut)

    @test_tracker_info(uuid="6050a1d1-4f3a-476d-bf93-638abd066790")
    def test_eap_connect_config_store_with_config_aka_prime(self):
        self.eap_connect_toggle_wifi(self.config_aka_prime, self.dut)

    @test_tracker_info(uuid="03108057-cc44-4a80-8331-77c93694099c")
    def test_eap_connect_config_store_with_config_ttls_none(self):
        config = dict(self.config_ttls)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.NONE.value
        self.eap_connect_toggle_wifi(config, self.dut)

    @test_tracker_info(uuid="53dd8195-e272-4589-a261-b8fa3607ad8d")
    def test_eap_connect_config_store_with_config_ttls_pap(self):
        config = dict(self.config_ttls)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.PAP.value
        self.eap_connect_toggle_wifi(config, self.dut)

    @test_tracker_info(uuid="640f697b-9c62-4b19-bd76-53b236a152e0")
    def test_eap_connect_config_store_with_config_ttls_mschap(self):
        config = dict(self.config_ttls)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.MSCHAP.value
        self.eap_connect_toggle_wifi(config, self.dut)

    @test_tracker_info(uuid="f0243684-fae0-46f3-afbd-bf525fc712e2")
    def test_eap_connect_config_store_with_config_ttls_mschapv2(self):
        config = dict(self.config_ttls)
        config[
            WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.MSCHAPV2.value
        self.eap_connect_toggle_wifi(config, self.dut)

    @test_tracker_info(uuid="49ec7202-3b00-49c3-970a-201360888c74")
    def test_eap_connect_config_store_with_config_ttls_gtc(self):
        config = dict(self.config_ttls)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.GTC.value
        self.eap_connect_toggle_wifi(config, self.dut)

    @test_tracker_info(uuid="1c6abfa3-f344-4e28-b891-5481ab79efcf")
    def test_eap_connect_config_store_with_config_peap0_mschapv2(self):
        config = dict(self.config_peap0)
        config[
            WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.MSCHAPV2.value
        self.eap_connect_toggle_wifi(config, self.dut)

    @test_tracker_info(uuid="2815bc76-49fa-43a5-a4b6-84788f9809d5")
    def test_eap_connect_config_store_with_config_peap0_gtc(self):
        config = dict(self.config_peap0)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.GTC.value
        self.eap_connect_toggle_wifi(config, self.dut)

    @test_tracker_info(uuid="e93f7472-6895-4e36-bff2-9b2dcfd07ad0")
    def test_eap_connect_config_store_with_config_peap1_mschapv2(self):
        config = dict(self.config_peap1)
        config[
            WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.MSCHAPV2.value
        self.eap_connect_toggle_wifi(config, self.dut)

    @test_tracker_info(uuid="6da72fa0-b858-4475-9559-46fe052d0d64")
    def test_eap_connect_config_store_with_config_peap1_gtc(self):
        config = dict(self.config_peap1)
        config[WifiEnums.Enterprise.PHASE2] = WifiEnums.EapPhase2.GTC.value
        self.eap_connect_toggle_wifi(config, self.dut)

    # Airplane mode on with wifi connect tests
    """ Test connecting to enterprise networks of different authentication
        types after airplane mode on.

        The authentication types tested are:
            EAP-SIM
            EAP-AKA
            EAP-AKA_PRIME

        Procedures:
            For each enterprise wifi network
            1. Turn on Airplane mode
            2. Toggle wifi..
            3. Ensure that the device connects to the enterprise network.

        Expect:
            Successful connection and Internet access through the enterprise
            networks with Airplane mode on.
    """

    @test_tracker_info(uuid="54b96a6c-f366-421c-9a72-80d7ee8fac8f")
    def test_eap_connect_with_config_sim_airplane_on(self):
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "DUT can not equip with mobile SIM card")
        self.log.info("Toggling Airplane mode ON")
        asserts.assert_true(
            acts.utils.force_airplane_mode(self.dut, True),
            "Can not turn on airplane mode on: %s" % self.dut.serial)
        time.sleep(DEFAULT_TIMEOUT)
        wutils.wifi_toggle_state(self.dut, True)
        wutils.wifi_connect(self.dut, self.config_sim)
        self.log.info("Toggling Airplane mode OFF")
        asserts.assert_true(
            acts.utils.force_airplane_mode(self.dut, False),
            "Can not turn OFF airplane mode: %s" % self.dut.serial)
        wutils.start_wifi_connection_scan_and_ensure_network_found(
          self.dut, self.config_sim[WifiEnums.SSID_KEY])

    @test_tracker_info(uuid="344f63f6-7c99-4507-8036-757f9f911d20")
    def test_eap_connect_with_config_aka_airplane_on(self):
        self.log.info("Toggling Airplane mode ON")
        asserts.assert_true(
            acts.utils.force_airplane_mode(self.dut, True),
            "Can not turn on airplane mode on: %s" % self.dut.serial)
        time.sleep(DEFAULT_TIMEOUT)
        wutils.wifi_toggle_state(self.dut, True)
        wutils.wifi_connect(self.dut, self.config_aka)
        self.log.info("Toggling Airplane mode OFF")
        asserts.assert_true(
            acts.utils.force_airplane_mode(self.dut, False),
            "Can not turn OFF airplane mode: %s" % self.dut.serial)
        wutils.start_wifi_connection_scan_and_ensure_network_found(
          self.dut, self.config_aka[WifiEnums.SSID_KEY])

    @test_tracker_info(uuid="5502b8c8-89d7-4ce9-afee-cae50e71f5f4")
    def test_eap_connect_with_config_aka_prime_airplane_on(self):
        self.log.info("Toggling Airplane mode ON")
        asserts.assert_true(
            acts.utils.force_airplane_mode(self.dut, True),
            "Can not turn on airplane mode on: %s" % self.dut.serial)
        time.sleep(DEFAULT_TIMEOUT)
        wutils.wifi_toggle_state(self.dut, True)
        wutils.wifi_connect(self.dut, self.config_aka_prime)
        self.log.info("Toggling Airplane mode OFF")
        asserts.assert_true(
            acts.utils.force_airplane_mode(self.dut, False),
            "Can not turn OFF airplane mode: %s" % self.dut.serial)
        wutils.start_wifi_connection_scan_and_ensure_network_found(
          self.dut, self.config_aka_prime[WifiEnums.SSID_KEY])

    @test_tracker_info(uuid="360a6fec-f4ee-4ecd-9b15-e836c977d6db")
    def test_connect_eap_sim_network_out_of_range_back(self):
        """Test connecting to enterprise networks to do out of range
        then back in range 3 times
         1. Connecting EAP-SIM network
         2. Move DUT out of range then back in range 3 times
         3. Check that device is connected to network.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "DUT can not equip with mobile SIM card")
        wutils.wifi_connect(self.dut, self.config_sim)
        self.toggle_out_of_range_stress()

    @test_tracker_info(uuid="9fb71afb-5599-4ca1-b458-09752c40bb0d")
    def test_eap_sim_network_out_of_range_back_airplane(self):
        """Test connecting to enterprise networks with airplne mode on
        to do out of range then back in range 3 times
         1. Turn on airplane mode
         2. Connecting EAP-SIM network
         3. Move DUT out of range then back in range 3 times
         4. Check that device is connected to network.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "DUT can not equip with mobile SIM card")
        self.log.debug("Toggling Airplane mode ON")
        asserts.assert_true(
            acts.utils.force_airplane_mode(self.dut, True),
            "Can not turn on airplane mode on: %s" % self.dut.serial)
        time.sleep(DEFAULT_TIMEOUT)
        self.log.debug("Toggling WiFi mode ON")
        wutils.wifi_toggle_state(self.dut, True)
        time.sleep(DEFAULT_TIMEOUT)
        wutils.wifi_connect(self.dut, self.config_sim)
        self.toggle_out_of_range_stress()

    @test_tracker_info(uuid="9e899c55-1a62-498c-bbf1-e9472e42e84f")
    def test_eap_sim_network_reboot(self):
        """Test connecting to enterprise networks with airplne mode on
        to do out of range then back in range 3 times
         1. Connecting EAP-SIM network
         3. Check that device is connected to network after reboot.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "DUT can not equip with mobile SIM card")
        self.dut.droid.disableDevicePassword(self.device_password)
        wutils.wifi_connect(self.dut, self.config_sim)
        current_network = self.dut.droid.wifiGetConnectionInfo()
        self.log.info("Current network: {}".format(current_network))
        self.dut.reboot()
        time.sleep(DEFAULT_TIMEOUT)
        self.check_connection(self.config_sim)

    @test_tracker_info(uuid="8e7465fb-5b16-4abb-92d8-a2c79355e377")
    def test_connect_to_EAP_SIM_network_switch_to_WPA2(self):
        """Test connecting PSK's AP1 and one EAP AP2 network switch test
        1. Connect to a PSK's AP1 before connect to EAP-SIM AP2 network.
        2. Out of PSK's AP1 range.
        3. Connect to EAP-SIM network, then in AP1 range to switch WPA2-PSK network.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "DUT can not equip with mobile SIM card")
        attn1 = self.attenuators[0]
        attn2 = self.attenuators[2]
        if "OpenWrtAP" in self.user_params:
            attn2 = self.attenuators[1]
        ap1_network = self.config_sim
        ap2_network = self.reference_networks[1]["2g"]
        attn1.set_atten(0)
        attn2.set_atten(0)
        wutils.connect_to_wifi_network(self.dut, ap2_network)
        #Enable EAP network signal
        attn2.set_atten(95)
        time.sleep(DEFAULT_TIMEOUT)
        wutils.connect_to_wifi_network(self.dut, ap1_network)
        current_network = self.dut.droid.wifiGetConnectionInfo()
        self.log.info("Current network: {}".format(current_network))
        #Enable SSID1 network signal
        attn1.set_atten(95)
        attn2.set_atten(0)
        time.sleep(20)
        self.check_connection(ap2_network)
