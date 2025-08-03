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

import time
from acts import asserts
from acts import utils
from acts import signals
from acts.keys import Config
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.net import connectivity_const as cconsts
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.wifi.aware import aware_test_utils as autils
from acts.controllers.openwrt_lib.openwrt_constants import OpenWrtWifiSetting
import os

WifiEnums = wutils.WifiEnums


class WifiRoamingTest(WifiBaseTest):

    def setup_class(self):
        """Configure the required networks for testing roaming."""
        super().setup_class()

        self.dut = self.android_devices[0]
        self.dut_client = self.android_devices[1]
        req_params = ["roaming_attn", "sae_roaming_unspported_list"]
        self.unpack_userparams(req_param_names=req_params,)
        self.country_code = wutils.WifiEnums.CountryCode.US
        if hasattr(self, "country_code_file"):
            if isinstance(self.country_code_file, list):
                self.country_code_file = self.country_code_file[0]
            if not os.path.isfile(self.country_code_file):
                self.country_code_file = os.path.join(
                    self.user_params[Config.key_config_path.value],
                    self.country_code_file)
            self.country_code = utils.load_config(
                self.country_code_file)["country"]

        if "AccessPoint" in self.user_params:
            self.legacy_configure_ap_and_start(ap_count=2)

    def teardown_class(self):
        self.dut.ed.clear_all_events()
        if "AccessPoint" in self.user_params:
            del self.user_params["reference_networks"]
            del self.user_params["open_network"]

    def setup_test(self):
        super().setup_test()
        self.dut.ed.clear_all_events()
        self.dut.droid.wakeLockAcquireBright()
        self.dut.droid.wakeUpNow()
        asserts.skip_if("sae" in self.test_name and
                        self.dut.model in self.sae_roaming_unspported_list,
                        "%s doesn't support wpa3 roaming." % self.dut.model)

    def teardown_test(self):
        super().teardown_test()
        self.dut.droid.wakeLockRelease()
        self.dut.droid.goToSleepNow()
        wutils.reset_wifi(self.dut)
        wutils.set_attns(self.attenuators, "default")
        for ad in self.android_devices:
            wutils.set_wifi_country_code(ad, self.country_code)
        if "OpenWrtAP" in self.user_params:
            for ap in self.access_points:
                ap.close()

    ### Helper Methods ###

    def register_network_callback_for_internet(self):
        self.dut.log.debug("Registering network callback for wifi internet"
                           "connectivity")
        network_request = {
            cconsts.NETWORK_CAP_TRANSPORT_TYPE_KEY :
                cconsts.NETWORK_CAP_TRANSPORT_WIFI,
            cconsts.NETWORK_CAP_CAPABILITY_KEY :
                [cconsts.NETWORK_CAP_CAPABILITY_INTERNET]
        }
        key = self.dut.droid.connectivityRegisterNetworkCallback(network_request)
        return key

    def generate_wifi_info(self, network):
        return {
            WifiEnums.SSID_KEY : network[WifiEnums.SSID_KEY],
            # We need to use "BSSID" in WifiInfo map, also need to use lower
            # chars for bssid.
            WifiEnums.BSSID_KEY : network["bssid"].lower()
        }

    def roaming_from_AP1_and_AP2(self, AP1_network, AP2_network):
        """Test roaming between two APs.

        Args:
            AP1_network: AP-1's network information.
            AP2_network: AP-2's network information.

        Steps:
        1. Make AP1 visible, AP2 not visible.
        2. Connect to AP1's ssid.
        3. Make AP1 not visible, AP2 visible.
        4. Expect DUT to roam to AP2.
        5. Validate connection information and ping.
        """
        network_cb_key = None
        if self.dut.droid.isSdkAtLeastS():
            network_cb_key = self.register_network_callback_for_internet()
        wutils.set_attns(self.attenuators, "AP1_on_AP2_off", self.roaming_attn)
        wifi_config = AP1_network.copy()
        wifi_config.pop("bssid")
        wutils.connect_to_wifi_network(self.dut, wifi_config)
        if network_cb_key is not None:
            self.dut.log.info("Waiting for onAvailable and "
                              "onCapabilitiesChanged after connection")
            # Ensure that the connection completed and we got the ON_AVAILABLE
            # callback.
            autils.wait_for_event_with_keys(
                self.dut,
                cconsts.EVENT_NETWORK_CALLBACK,
                20,
                (cconsts.NETWORK_CB_KEY_ID, network_cb_key),
                (cconsts.NETWORK_CB_KEY_EVENT, cconsts.NETWORK_CB_AVAILABLE))
            autils.wait_for_event_with_keys(
                self.dut, cconsts.EVENT_NETWORK_CALLBACK, 10,
                (cconsts.NETWORK_CB_KEY_ID, network_cb_key),
                (cconsts.NETWORK_CB_KEY_EVENT,
                 cconsts.NETWORK_CB_CAPABILITIES_CHANGED),
                (cconsts.NETWORK_CB_KEY_TRANSPORT_INFO,
                 self.generate_wifi_info(AP1_network)))
        self.log.info("Roaming from %s to %s", AP1_network, AP2_network)
        wutils.trigger_roaming_and_validate(
            self.dut, self.attenuators, "AP1_off_AP2_on", AP2_network,
            self.roaming_attn)
        if network_cb_key is not None:
            self.dut.log.info("Waiting for onCapabilitiesChanged after"
                              " roaming")
            # Ensure that the roaming complete triggered a capabilities change
            # with the new bssid.
            autils.wait_for_event_with_keys(
                self.dut, cconsts.EVENT_NETWORK_CALLBACK, 10,
                (cconsts.NETWORK_CB_KEY_ID, network_cb_key),
                (cconsts.NETWORK_CB_KEY_EVENT,
                 cconsts.NETWORK_CB_CAPABILITIES_CHANGED),
                (cconsts.NETWORK_CB_KEY_TRANSPORT_INFO,
                 self.generate_wifi_info(AP2_network)))

    ### Test Cases ###

    @test_tracker_info(uuid="db8a46f9-713f-4b98-8d9f-d36319905b0a")
    def test_roaming_between_AP1_to_AP2_open_2g(self):
        if "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(open_network=True,
                                                ap_count=2,
                                                mirror_ap=True)
            self.log.info("BSSID map: %s" % self.bssid_map)
        ap1_network = self.open_network[0]["2g"]
        ap2_network = self.open_network[1]["2g"]
        if "OpenWrtAP" in self.user_params:
            ap1_network["bssid"] = self.bssid_map[0]["2g"][ap1_network["SSID"]]
            ap2_network["bssid"] = self.bssid_map[1]["2g"][ap2_network["SSID"]]
        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="0db67d9b-6ea9-4f40-acf2-155c4ecf9dc5")
    def test_roaming_between_AP1_to_AP2_open_5g(self):
        if "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(open_network=True,
                                                ap_count=2,
                                                mirror_ap=True)
        ap1_network = self.open_network[0]["5g"]
        ap2_network = self.open_network[1]["5g"]
        if "OpenWrtAP" in self.user_params:
            ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
            ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]
        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="eabc7319-d962-4bef-b679-725e9ff00420")
    def test_roaming_between_AP1_to_AP2_psk_2g(self):
        if "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(wpa_network=True,
                                                ap_count=2,
                                                mirror_ap=True)
        ap1_network = self.reference_networks[0]["2g"]
        ap2_network = self.reference_networks[1]["2g"]
        if "OpenWrtAP" in self.user_params:
            ap1_network["bssid"] = self.bssid_map[0]["2g"][ap1_network["SSID"]]
            ap2_network["bssid"] = self.bssid_map[1]["2g"][ap2_network["SSID"]]
        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="1cf9c681-4ff0-45c1-9719-f01629f6a7f7")
    def test_roaming_between_AP1_to_AP2_psk_5g(self):
        if "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(wpa_network=True,
                                                ap_count=2,
                                                mirror_ap=True)
        ap1_network = self.reference_networks[0]["5g"]
        ap2_network = self.reference_networks[1]["5g"]
        if "OpenWrtAP" in self.user_params:
            ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
            ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]
        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="a28f7d2e-fae4-4e66-b633-7ee59f8b46e0")
    def test_roaming_between_AP1_to_AP2_owe_2g(self):
        if "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(owe_network=True,
                                                ap_count=2,
                                                mirror_ap=True)
        ap1_network = self.owe_networks[0]["2g"]
        ap2_network = self.owe_networks[1]["2g"]
        if "OpenWrtAP" in self.user_params:
            ap1_network["bssid"] = self.bssid_map[0]["2g"][ap1_network["SSID"]]
            ap2_network["bssid"] = self.bssid_map[1]["2g"][ap2_network["SSID"]]
        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="3c39110a-9336-4abd-b885-acbba85dc10d")
    def test_roaming_between_AP1_to_AP2_owe_5g(self):
        if "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(owe_network=True,
                                                ap_count=2,
                                                mirror_ap=True)
        ap1_network = self.owe_networks[0]["5g"]
        ap2_network = self.owe_networks[1]["5g"]
        if "OpenWrtAP" in self.user_params:
            ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
            ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]
        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="68b2baf6-162a-44f2-a00d-4973e5ac9471")
    def test_roaming_between_AP1_to_AP2_sae_2g(self):
        if "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(sae_network=True,
                                                ap_count=2,
                                                mirror_ap=True)
        ap1_network = self.sae_networks[0]["2g"]
        ap2_network = self.sae_networks[1]["2g"]
        if "OpenWrtAP" in self.user_params:
            ap1_network["bssid"] = self.bssid_map[0]["2g"][ap1_network["SSID"]]
            ap2_network["bssid"] = self.bssid_map[1]["2g"][ap2_network["SSID"]]
        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="20e24ed3-0cd1-46dd-bd26-2183ffb443e6")
    def test_roaming_between_AP1_to_AP2_sae_5g(self):
        if "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(sae_network=True,
                                                ap_count=2,
                                                mirror_ap=True)
        ap1_network = self.sae_networks[0]["5g"]
        ap2_network = self.sae_networks[1]["5g"]
        if "OpenWrtAP" in self.user_params:
            ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
            ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]
        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="521269cb-5d2c-46e6-bc01-a03bd148ce28")
    def test_soft_2g_ap_channel_when_roam_to_chan_13(self):
        """Verify softAp 2G channel when after roaming to network on channel 13.

        Steps:
            1. Configure 2 APs - 1 on channel 6, the other on channel 13.
            2. Connect DUT to AP on channel 6.
            3. Start softAp on DUT on 2G band.
            4. Verify softAp is started on channel 6.
            5. Roam to AP2 with channel 13.
            6. Verify SoftAp on DUT changed to channel 13.
        """
        for ad in self.android_devices:
            wutils.set_wifi_country_code(
                ad, wutils.WifiEnums.CountryCode.AUSTRALIA)
        if "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(open_network=True,
                                                ap_count=2,
                                                channel_2g_ap2=13,
                                                mirror_ap=True)
        ap1_network = self.open_network[0]["2g"]
        ap2_network = self.open_network[1]["2g"]
        if "OpenWrtAP" in self.user_params:
            ap1_network["bssid"] = self.bssid_map[0]["2g"][ap1_network["SSID"]]
            ap2_network["bssid"] = self.bssid_map[1]["2g"][ap2_network["SSID"]]
        wutils.set_attns(self.attenuators, "AP1_on_AP2_off", self.roaming_attn)
        wutils.connect_to_wifi_network(self.dut, ap1_network)

        # start softap on 2G and verify the channel is 6.
        sap_config = {
            WifiEnums.SSID_KEY: "hotspot_%s" % utils.rand_ascii_str(6),
            WifiEnums.PWD_KEY: "pass_%s" % utils.rand_ascii_str(6),
            WifiEnums.AP_BAND_KEY: WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G}
        asserts.assert_true(
            self.dut.droid.wifiSetWifiApConfiguration(sap_config),
            "Failed to set WifiAp Configuration")
        wutils.start_wifi_tethering_saved_config(self.dut)
        softap_conf = self.dut.droid.wifiGetApConfiguration()
        self.log.info("softap conf: %s" % softap_conf)
        wutils.connect_to_wifi_network(self.dut_client, sap_config)
        conn_info = self.dut_client.droid.wifiGetConnectionInfo()
        self.log.info("Wifi connection info on dut_client: %s" % conn_info)
        softap_channel = wutils.WifiEnums.freq_to_channel[conn_info["frequency"]]
        asserts.assert_true(softap_channel == 6,
                            "Dut client did not connect to softAp on channel 6")

        # trigger roaming to AP2 with channel 13
        self.log.info("Roaming from %s to %s", ap1_network, ap2_network)
        wutils.trigger_roaming_and_validate(
            self.dut, self.attenuators, "AP1_off_AP2_on", ap2_network,
            self.roaming_attn)

        # verify softap is now moved to channel 13
        conn_info = self.dut_client.droid.wifiGetConnectionInfo()
        self.log.info("Wifi connection info on dut_client: %s" % conn_info)
        softap_channel = wutils.WifiEnums.freq_to_channel[conn_info["frequency"]]
        asserts.assert_true(softap_channel == 13,
                            "Dut client did not connect to softAp on channel 13")

    @test_tracker_info(uuid="3114d625-5cdd-4205-bb46-5a9d057dc80d")
    def test_roaming_fail_psk_2g(self):
        """Verify roaming fail with mismatch passwords.

        Steps:
            DUT connect to AP1.
            Change AP2's password.
            DUT try roaming from AP1 to AP2 with mismatched password.
            Change AP2's password back to original one.
            DUT try roaming from AP1 to AP2 with matched passwords.
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        if "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(wpa_network=True,
                                                ap_count=2,
                                                mirror_ap=True)
            self.openwrt1 = self.access_points[0]
            self.openwrt2 = self.access_points[1]
            ap1_network = self.reference_networks[0]["2g"]
            ap2_network = self.reference_networks[1]["2g"]
            # Get APs' BSSIDs.
            ap1_bssid = self.bssid_map[0]["2g"][ap1_network["SSID"]]
            ap2_bssid = self.bssid_map[1]["2g"][ap2_network["SSID"]]
            # Make AP's configs.
            ap1_network["bssid"] = ap1_bssid
            ap2_network["bssid"] = ap2_bssid

            # Change AP2 2G to password.
            self.openwrt2.set_password(pwd_2g=utils.rand_ascii_str(8))

            try:
                # DUT roaming from AP1 to AP2 with mismatched passwords.
                self.dut.log.info("Roaming via mismatched passwords to AP2 [{}]"
                                  .format(ap2_bssid))
                self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)
            except:
                self.dut.log.info("Failed roaming to AP2")
                self.dut.log.info("Roaming via matched passwords to AP2 [{}]"
                                  .format(ap1_bssid))
                wutils.set_attns_steps(self.attenuators, "AP1_on_AP2_off",
                                       self.roaming_attn)
                self.openwrt2.set_password(pwd_2g=ap2_network["password"])
                self.dut.log.info("Toggling wifi OFF.")
                wutils.wifi_toggle_state(self.dut, False)
                self.dut.log.info("Toggling wifi ON.")
                wutils.wifi_toggle_state(self.dut, True)
                self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)
            else:
                raise signals.TestFailure("DUT unexpectedly connect to Wi-Fi.")

        # Use Google OnHub as Wi-Fi AP to test when OpenWrt is no available.
        elif "AccessPoint" in self.user_params:
            network = {'SSID':'test_roaming_fail', 'password':'roam123456@'}
            # AP2 network with incorrect password.
            network_fail = {'SSID':'test_roaming_fail', 'password':'roam123456@#$%^'}
            # Setup AP1 with the correct password.
            wutils.ap_setup(self, 0, self.access_points[0], network)
            network_bssid = self.access_points[0].get_bssid_from_ssid(
                network["SSID"], '2g')
            # Setup AP2 with the incorrect password.
            wutils.ap_setup(self, 1, self.access_points[1], network_fail)
            network_fail_bssid = self.access_points[1].get_bssid_from_ssid(
                network_fail["SSID"], '2g')
            network['bssid'] = network_bssid
            network_fail['bssid'] = network_fail_bssid
            try:
                # Initiate roaming with AP2 configured with incorrect password.
                self.roaming_from_AP1_and_AP2(network, network_fail)
            except:
                self.log.info("Roaming failed to AP2 with incorrect password.")
                # Re-configure AP2 after roaming failed, with correct password.
                self.log.info("Re-configuring AP2 with correct password.")
                wutils.ap_setup(self, 1, self.access_points[1], network)
            self.roaming_from_AP1_and_AP2(network, network_fail)

    @test_tracker_info(uuid="b6d73094-22bc-4460-9d55-ce34a0a6a8c9")
    def test_roaming_fail_different_bssid(self):
        """Verify devices is disconnect with difference bssid after roaming

        Steps:
            1. Configure 2 APs
            2. Connect DUT to AP 1
            3. Roam to AP2
            4. Verify the bssid is difference and the device can't connect
            5. Verify device is disconnect after roaming.
        """
        if "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(wpa_network=True,
                                                ap_count=2,
                                                mirror_ap=True)
        ap1_network = self.reference_networks[0]["2g"]
        ap2_network = self.reference_networks[1]["2g"]
        if "OpenWrtAP" in self.user_params:
            ap1_network["bssid"] = self.bssid_map[0]["2g"][ap1_network["SSID"]]
            ap2_network["bssid"] = self.bssid_map[1]["2g"][ap2_network["SSID"]]
        wutils.set_attns(self.attenuators, "AP1_on_AP2_off")
        wutils.connect_to_wifi_network(self.dut, ap1_network)

        # Initiate roaming with AP2
        wutils.set_attns(self.attenuators, "AP1_off_AP2_on")
        time.sleep(10)
        try:
            wutils.verify_wifi_connection_info(self.dut, ap2_network)
        except:
            self.log.info("Roaming failed to AP2 with incorrect BSSID")
            wutils.wait_for_disconnect(self.dut)
            self.log.info("Device is disconnect")

    @test_tracker_info(uuid="641b35c1-7bf8-4d85-a813-1bc30d7bd0d4")
    def test_roaming_between_psk_2g_to_psk_5g(self):
        """Verify psk when after roaming from 2.4 Ghz to 5 Ghz with OpenWrtAP

         Steps:
             1. Configure 2 APs
             2. Configure AP2 5Ghz and AP1 2.4Ghz WiFi to same SSID and password
             3. Connect DUT to AP1 2.4Ghz WiFi
             4. Roam to AP2 5Ghz WiFi
             5. Verify the DUT can connect from 2.4Ghz to 5Ghz
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ap_count=2)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.reference_networks[0]["2g"]
        ap2_network = self.reference_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["2g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Change 2 APs to same password and SSID
        self.openwrt1.set_ssid(ssid_2g="testSSID")
        self.openwrt2.set_ssid(ssid_5g="testSSID")
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        ap1_network["SSID"] = "testSSID"
        ap2_network["SSID"] = "testSSID"
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="fa1fe3bb-abb9-4d75-88bd-cd88c04073c6")
    def test_roaming_between_psk_withoutPMF_5g(self):
        """Verify psk without PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs without PMF
             2. Connect DUT to AP1
             3. Roam to AP2
             4. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=0)
        ap1_network = self.reference_networks[0]["5g"]
        ap2_network = self.reference_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="496e048a-3da5-45c4-aa0f-c5bd127eb06f")
    def test_roaming_between_psk_withPMF_5g(self):
        """Verify psk with PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs with PMF
             2. Connect DUT to AP1
             3. Roam to AP2
             4. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=2)
        ap1_network = self.reference_networks[0]["5g"]
        ap2_network = self.reference_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="524c97c5-4bca-46b0-97f9-aad749768cd8")
    def test_roaming_between_psk_wPMF_to_psk_woPMF_5g(self):
        """Verify psk with/without PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs without PMF
             2. Configure AP1 with PMF
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=0)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.reference_networks[0]["5g"]
        ap2_network = self.reference_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Configure AP1 to enable PMF.
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ieee80211w=2)
        ap1_network = self.reference_networks[0]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_5g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="714ceac1-d8af-4679-9385-2c9c6d885634")
    def test_roaming_between_psk_woPMF_to_psk_wPMF_5g(self):
        """Verify psk without/with PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs with PMF
             2. Configure AP1 without PMF
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=2)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.reference_networks[0]["5g"]
        ap2_network = self.reference_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Configure AP1 to disable PMF.
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ieee80211w=0)
        ap1_network = self.reference_networks[0]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_5g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="4b201d7b-38ec-49c2-95c3-81cba51ba11d")
    def test_roaming_between_psk_capablePMF_5g(self):
        """Verify psk capable PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs with capable PMF
             2. Connect DUT to AP1
             3. Roam to AP2
             4. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=1)
        ap1_network = self.reference_networks[0]["5g"]
        ap2_network = self.reference_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="588a9279-1f84-44d2-acbb-051c5ba0bcb1")
    def test_roaming_between_psk_capPMF_to_psk_woPMF_5g(self):
        """Verify psk capable/without PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs without PMF
             2. Configure AP1 with capable PMF
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=0)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.reference_networks[0]["5g"]
        ap2_network = self.reference_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Configure AP1 to enable capable PMF.
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ieee80211w=1)
        ap1_network = self.reference_networks[0]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_5g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="d239b56d-515c-4589-b63a-ebce62c321af")
    def test_roaming_between_psk_capPMF_to_psk_wPMF_5g(self):
        """Verify psk capable/with PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs with PMF
             2. Configure AP1 with capable PMF
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=2)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.reference_networks[0]["5g"]
        ap2_network = self.reference_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Configure AP1 to enable capable PMF.
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ieee80211w=1)
        ap1_network = self.reference_networks[0]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_5g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="57ec9401-b336-40cb-bc46-c2b4cfc22516")
    def test_roaming_between_psk_wPMF_to_psk_capPMF_5g(self):
        """Verify psk with/capable PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs with capable PMF
             2. Configure AP1 with PMF
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=1)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.reference_networks[0]["5g"]
        ap2_network = self.reference_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Configure AP1 to enable PMF.
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ieee80211w=2)
        ap1_network = self.reference_networks[0]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_5g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="a32d24f0-36cc-41f0-90b4-db74973db29f")
    def test_roaming_between_psk_woPMF_to_psk_capPMF_5g(self):
        """Verify psk without/capable PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs with capable PMF
             2. Configure AP1 without PMF
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=1)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.reference_networks[0]["5g"]
        ap2_network = self.reference_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Configure AP1 to disable PMF.
        self.configure_openwrt_ap_and_start(wpa_network=True,
                                            ieee80211w=0)
        ap1_network = self.reference_networks[0]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_5g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="44f45dc9-e85c-419d-bbab-d02e9548a377")
    def test_roaming_between_psk_to_saemixed_woPMF_5g(self):
        """Verify psk to saemixed without PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs security type to saemixed without PMF
             2. Configure AP1 security type to psk
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(saemixed_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=0)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.saemixed_networks[0]["5g"]
        ap2_network = self.saemixed_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Configure AP1 security type.
        self.configure_openwrt_ap_and_start(wpa_network=True)
        ap1_network = self.reference_networks[0]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_5g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="9827f3b3-51cd-406b-ba2b-3ef4a9df7378")
    def test_roaming_between_psk_to_saemixed_wPMF_5g(self):
        """Verify psk to saemixed with PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs security type to saemixed with PMF
             2. Configure AP1 security type to psk
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(saemixed_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=2)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.saemixed_networks[0]["5g"]
        ap2_network = self.saemixed_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Configure AP1 security type.
        self.configure_openwrt_ap_and_start(wpa_network=True)
        ap1_network = self.reference_networks[0]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_5g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="037602b1-2ce8-4dbb-b46a-00e0a582d976")
    def test_roaming_between_saemixed_withoutPMF_5g(self):
        """Verify saemixed without PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs to saemixed without PMF
             2. Connect DUT to AP1
             3. Roam to AP2
             4. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(saemixed_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=0)
        ap1_network = self.saemixed_networks[0]["5g"]
        ap2_network = self.saemixed_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="94514f72-9e90-4d73-bba5-65b95e721cd8")
    def test_roaming_between_saemixed_withPMF_5g(self):
        """Verify saemixed with PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs to saemixed with PMF
             2. Connect DUT to AP1
             3. Roam to AP2
             4. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(saemixed_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=2)
        ap1_network = self.saemixed_networks[0]["5g"]
        ap2_network = self.saemixed_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="05834382-5c3f-48d9-a6e6-8794ebf176f8")
    def test_roaming_between_saemixed_woPMF_to_saemixed_wPMF_5g(self):
        """Verify saemixed without/with PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs to saemixed with PMF
             2. Configure AP1 to saemixed without PMF
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(saemixed_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=2)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.saemixed_networks[0]["5g"]
        ap2_network = self.saemixed_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Configure AP1 to disable PMF.
        self.configure_openwrt_ap_and_start(saemixed_network=True,
                                            ieee80211w=0)
        ap1_network = self.saemixed_networks[0]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_5g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="4d7cba89-40f1-4a2a-891d-4bb757f73442")
    def test_roaming_between_saemixed_wPMF_to_saemixed_woPMF_5g(self):
        """Verify saemixed with/without PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs to saemixed without PMF
             2. Configure AP1 to saemixed with PMF
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(saemixed_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=0)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.saemixed_networks[0]["5g"]
        ap2_network = self.saemixed_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Configure AP1 to enable PMF.
        self.configure_openwrt_ap_and_start(saemixed_network=True,
                                            ieee80211w=2)
        ap1_network = self.saemixed_networks[0]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_5g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="57caaf0a-5251-4191-b4d1-de31d7c12295")
    def test_roaming_between_sae_to_saemixed_woPMF_5g(self):
        """Verify sae to saemixed without PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs security type to saemixed without PMF
             2. Configure AP1 security type to sae
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(saemixed_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=0)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.saemixed_networks[0]["5g"]
        ap2_network = self.saemixed_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Configure AP1 security type.
        self.configure_openwrt_ap_and_start(sae_network=True)
        ap1_network = self.sae_networks[0]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_5g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="0f5ca9d9-ef40-4a95-bf55-4c78358a9a3e")
    def test_roaming_between_sae_to_saemixed_wPMF_5g(self):
        """Verify sae to saemixed with PMF when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs security type to saemixed with PMF
             2. Configure AP1 security type to sae
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(saemixed_network=True,
                                            ap_count=2,
                                            mirror_ap=True,
                                            ieee80211w=2)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.saemixed_networks[0]["5g"]
        ap2_network = self.saemixed_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Configure AP1 security type.
        self.configure_openwrt_ap_and_start(sae_network=True)
        ap1_network = self.sae_networks[0]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_5g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="e875233f-d242-4ddd-b357-8e3e215af050")
    def test_roaming_between_psk_to_sae_5g(self):
        """Verify fail with diff security type after roaming with OpenWrtAP

         This test will pass after design change but this is not seamless roaming.
         Steps:
             1. Configure 2 APs security type to sae
             2. Configure AP1 security type to psk
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT connect to AP2 after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(sae_network=True,
                                            ap_count=2,
                                            mirror_ap=True)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.sae_networks[0]["5g"]
        ap2_network = self.sae_networks[1]["5g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        # Configure AP1 security type.
        self.configure_openwrt_ap_and_start(wpa_network=True)
        ap1_network = self.reference_networks[0]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_5g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_5g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        # DUT roaming from AP1 to AP2 with diff security type.
        # Expect device disconnect from AP1 and connect to AP2 due to
        # saved network contain AP2.
        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="76098016-56a4-4b92-8c13-7333a21c9a1b")
    def test_roaming_between_psk_to_saemixed_2g(self):
        """Verify psk to saemixed with 11n when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs security type to saemixed with 11n mode
             2. Configure AP1 security type to psk with 11n mode
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(saemixed_network=True,
                                            ap_count=2,
                                            mirror_ap=True)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.saemixed_networks[0]["2g"]
        ap2_network = self.saemixed_networks[1]["2g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["2g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["2g"][ap2_network["SSID"]]

        # Configure AP1 security type.
        self.configure_openwrt_ap_and_start(wpa_network=True)
        ap1_network = self.reference_networks[0]["2g"]
        ap1_network["bssid"] = self.bssid_map[0]["2g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_2g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_2g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="2caf668d-6b05-4ba5-ac6b-1ec989fdf9ff")
    def test_roaming_between_psk1_to_saemixed_2g(self):
        """Verify psk1 to saemixed with 11n when after roaming with OpenWrtAP

         Steps:
             1. Configure 2 APs security type to saemixed with 11n mode
             2. Configure AP1 security type to psk1 with 11n mode
             3. Connect DUT to AP1
             4. Roam to AP2
             5. Verify the DUT can connect after roaming
        """
        # Use OpenWrt as Wi-Fi AP when it's available in testbed.
        asserts.skip_if("OpenWrtAP" not in self.user_params, "OpenWrtAP not in user_params")
        self.configure_openwrt_ap_and_start(saemixed_network=True,
                                            ap_count=2,
                                            mirror_ap=True)
        self.openwrt1 = self.access_points[0]
        self.openwrt2 = self.access_points[1]
        ap1_network = self.saemixed_networks[0]["2g"]
        ap2_network = self.saemixed_networks[1]["2g"]
        # Get & Setup APs' BSSIDs.
        ap1_network["bssid"] = self.bssid_map[0]["2g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["2g"][ap2_network["SSID"]]

        # Configure AP1 security type.
        self.configure_openwrt_ap_and_start(wpa1_network=True)
        ap1_network = self.wpa1_networks[0]["2g"]
        ap1_network["bssid"] = self.bssid_map[0]["2g"][ap1_network["SSID"]]

        # Change AP2 password and SSID same as AP1.
        self.openwrt2.set_password(pwd_2g=ap1_network["password"])
        self.openwrt2.set_ssid(ssid_2g=ap1_network["SSID"])
        ap2_network["SSID"] = ap1_network["SSID"]
        ap2_network["password"] = ap1_network["password"]

        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="cc8781ec-12fb-47a4-86d4-80d13231c75d")
    def test_connect_to_wpa2_psk_2g_80211r(self):
        """Test WPA2-PSK 802.11r 2G connectivity ant roaming."""
        if "OpenWrtAP" in self.user_params:
            self.openwrt1 = self.access_points[0]
            self.openwrt2 = self.access_points[1]
            self.configure_openwrt_ap_and_start(wpa_network=True,
                                                ap_count=2,
                                                mirror_ap=True)
        ap1_network = self.reference_networks[0]["2g"]
        ap2_network = self.reference_networks[1]["2g"]
        ap1_network["bssid"] = self.bssid_map[0]["2g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["2g"][ap2_network["SSID"]]

        md = self.openwrt1.generate_mobility_domain()
        self.openwrt1.enable_80211r(OpenWrtWifiSetting.IFACE_2G, md)
        self.openwrt2.enable_80211r(OpenWrtWifiSetting.IFACE_2G, md)
        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)

    @test_tracker_info(uuid="8b0ee65e-bba2-4786-a8b9-8990316d123d")
    def test_connect_to_wpa2_psk_5g_80211r(self):
        """Test WPA2-PSK 802.11r 5G connectivity and roaming."""
        if "OpenWrtAP" in self.user_params:
            self.openwrt1 = self.access_points[0]
            self.openwrt2 = self.access_points[1]
            self.configure_openwrt_ap_and_start(wpa_network=True,
                                                ap_count=2,
                                                mirror_ap=True)
        ap1_network = self.reference_networks[0]["5g"]
        ap2_network = self.reference_networks[1]["5g"]
        ap1_network["bssid"] = self.bssid_map[0]["5g"][ap1_network["SSID"]]
        ap2_network["bssid"] = self.bssid_map[1]["5g"][ap2_network["SSID"]]

        md = self.openwrt1.generate_mobility_domain()
        self.openwrt1.enable_80211r(OpenWrtWifiSetting.IFACE_5G, md)
        self.openwrt2.enable_80211r(OpenWrtWifiSetting.IFACE_5G, md)
        self.roaming_from_AP1_and_AP2(ap1_network, ap2_network)
