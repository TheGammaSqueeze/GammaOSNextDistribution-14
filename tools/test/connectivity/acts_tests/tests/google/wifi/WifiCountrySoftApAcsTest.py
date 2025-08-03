#!/usr/bin/env python3
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
from acts import signals
from acts.test_decorators import test_tracker_info
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts.controllers.ap_lib.hostapd_constants import BAND_2G
from acts.controllers.ap_lib.hostapd_constants import BAND_5G
from acts.controllers.ap_lib import hostapd_constants

# TODO: Find a better way to get real country code and channels data.
COUNTRY_5G_NOT_ALLOWED = ["JP", "GB", "DE"]
WIFI_5G_NON_DFS_CHANNELS = [36, 38, 40, 42, 44, 46, 48, 149, 153, 157, 161, 165]
WIFI_5G_DFS_CHANNELS = [52, 56, 60, 64, 100, 104, 108, 112, 116, 132, 136, 140]
WIFI_EU_SRD_CHANNELS = [149, 153, 157, 161, 165]

BRIDGED_AP_LAUNCH_INTERVAL_5_SECONDS = 5
WifiEnums = wutils.WifiEnums


class WifiCountrySoftApAcsTest(WifiBaseTest):
    """WiFi WifiSoftApCountryAcsTest test class.

    Test Bed Requirement:
        * Android DUT x 1.
        * OpenWrt x 1.
    """

    def setup_class(self):
        super().setup_class()

        self.dut = self.android_devices[0]
        self.client = self.android_devices[1]

        if "OpenWrtAP" in self.user_params:
            self.openwrt = self.access_points[0]
            self.openwrt.log.info("Rebooting OpenWrt")
            self.openwrt.reboot()
            self.openwrt.verify_wifi_status(timeout=60)

        req_params = []
        opt_param = []

        self.unpack_userparams(
            req_param_names=req_params, opt_param_names=opt_param)

    def setup_test(self):
        super().setup_test()
        for ad in self.android_devices:
            wutils.reset_wifi(ad)
        wutils.wifi_toggle_state(self.dut, True)
        wutils.wifi_toggle_state(self.client, True)

    def teardown_test(self):
        super().teardown_test()
        if self.dut.droid.wifiIsApEnabled():
            wutils.stop_wifi_tethering(self.dut)

        for ad in self.android_devices:
            wutils.reset_wifi(ad)
            wutils.set_wifi_country_code(
                ad, wutils.WifiEnums.CountryCode.US)

    def teardown_class(self):
        super().teardown_class()
        for ad in self.android_devices:
            wutils.reset_wifi(ad)

        if "AccessPoint" in self.user_params:
            del self.user_params["reference_networks"]
            del self.user_params["open_network"]

    def is_bridgedap_supported(self, *args):
        return self.dut.droid.wifiIsBridgedApConcurrencySupported()

    def set_country_code_and_verify(self, ad, country_code):
        """ Set Country Code to DUT.

        Args:
            ad: An AndroidDevice object.
            country_code: String; 2 letter ISO country code, e,g,. "US".
        """
        wutils.set_wifi_country_code(ad, country_code)
        # Wi-Fi OFF and ON to make sure country code take effect.
        wutils.wifi_toggle_state(ad, False)
        wutils.wifi_toggle_state(ad, True)

        country = ad.droid.wifiGetCountryCode()
        asserts.assert_true(country == country_code,
                            "country code {} is not set".format(country_code))
        ad.log.info("Country code set to : {}".format(country))

    def connect_wifi_network(self, init_sta_band, init_sta_chan):
        """Enable OpenWrt with a 2G/5G channels and a DUT connect to it.

        Args:
            init_sta_band: String; "2g" or "5g".
            init_sta_chan: Integer; use to setup OpenWrt 2G/5G channel.

        Returns:
            ap_freq: Integer'; represent the frequency of the AP which
            the DUT connect to.
        """

        # Enable a Wi-Fi network and DUT connect to it.
        if init_sta_band == BAND_2G:
            connect = BAND_2G
            channel_2g = init_sta_chan
            channel_5g = hostapd_constants.AP_DEFAULT_CHANNEL_5G
        elif init_sta_band == BAND_5G:
            connect = BAND_5G
            channel_2g = hostapd_constants.AP_DEFAULT_CHANNEL_2G
            channel_5g = init_sta_chan

        # Enable OpenWrt AP.
        if "OpenWrtAP" in self.user_params:
            self.openwrt = self.access_points[0]
            self.configure_openwrt_ap_and_start(wpa_network=True,
                                                channel_2g=channel_2g,
                                                channel_5g=channel_5g)
            self.ap1_2g = self.wpa_networks[0][BAND_2G]
            self.ap1_5g = self.wpa_networks[0][BAND_5G]

            self.openwrt.log.info("OpenWrt AP 2G: {}".format(self.ap1_2g))
            self.openwrt.log.info("OpenWrt AP 5G: {}".format(self.ap1_5g))

        if connect == BAND_2G:
            wutils.connect_to_wifi_network(self.dut, self.ap1_2g)
        elif connect == BAND_5G:
            wutils.connect_to_wifi_network(self.dut, self.ap1_5g)

        ap_freq = self.dut.droid.wifiGetConnectionInfo()["frequency"]
        self.dut.log.info("DUT connected to AP on freq: {}, chan: {}".
                          format(ap_freq, WifiEnums.freq_to_channel[ap_freq]))
        return ap_freq

    def enable_softap(self, ad):
        """ Enable SoftAp of the DUT

        Args:
            ad: An AndroidDevice object.

        Returns:
            (freq1, freq2): Integer; a 2G frequency and a 5G frequency if DUT
                            support BridgedAp.
            freq: Integer; a frequency from SoftAp.
            None, bandwidth: Just a placeholder, won't be used.

        Raises:
            TestFailure if no BridgedAp instances.
        """
        # Enable SoftAp
        # Create SoftAp config.
        config = wutils.create_softap_config()
        # If DUT support BridgedAp, then two BridgedAp instances enabled.
        if self.dut.droid.wifiIsBridgedApConcurrencySupported():
            wutils.save_wifi_soft_ap_config(
                ad,
                config,
                bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        # If DUT does not support BridgedAp, 2G OR 5G SoftAp enabled.
        else:
            if self.init_softap_band == BAND_2G:
                band = WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G
            elif self.init_softap_band == BAND_5G:
                band = WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G
            wutils.save_wifi_soft_ap_config(ad, config, band=band)
        wutils.start_wifi_tethering_saved_config(ad)
        time.sleep(BRIDGED_AP_LAUNCH_INTERVAL_5_SECONDS)

        # if DUT support BridgedAp:
        if ad.droid.wifiIsBridgedApConcurrencySupported():
            callbackId = ad.droid.registerSoftApCallback()
            infos = wutils.get_current_softap_infos(ad, callbackId, True)
            ad.droid.unregisterSoftApCallback(callbackId)
            # if DUT BridgedAp has two instances, return two frequencies.
            if len(infos) == 2:
                freq_1 = infos[0]["frequency"]
                freq_2 = infos[1]["frequency"]
                return freq_1, freq_2
            # if DUT BridgedAp has only one instances, return the frequency.
            elif len(infos) == 1:
                freq = infos[0]["frequency"]
                return freq, None
            else:
                raise signals.TestFailure("There should be SoftAp instance.")
        # if DUT does not support BridgedAp:
        else:
            # Return SoftAp frequency.
            callbackId = ad.droid.registerSoftApCallback()
            freq, bandwidth = wutils.get_current_softap_info(ad, callbackId,
                                                             True)
            ad.log.info("SoftAp freq: {}".format(freq))
            ad.droid.unregisterSoftApCallback(callbackId)
            return freq, bandwidth

    def collect_acs_failures(self, freq1, freq2, country, init_sta_band,
                             init_sta_chan, init_softap_band):
        """ Verify SoftAp ACS rules and return error message when fail.

        Args:
            freq1: Integer; frequency from SoftAp.
            freq2: Integer; frequency from SoftAp.
            country: String; Two letters country code, e.g., "US".
            init_sta_band: String; "2g" or "5g".
            init_sta_chan: Integer; use to setup OpenWrt 2G/5G channel.
            init_softap_band: String: "2g" or "5g".

        Returns: List of string; contains failure messages.
        """
        # If DUT support BridgedAp(Dual SoftAp).
        # Decide which is softap_2g_freq, which is softap_5g_freq
        self.softap_freq_1 = freq1
        if self.dut.droid.wifiIsBridgedApConcurrencySupported():
            self.softap_freq_2 = freq2
            if self.softap_freq_1 in WifiEnums.ALL_2G_FREQUENCIES:
                self.softap_2g_freq = self.softap_freq_1
            elif self.softap_freq_1 in WifiEnums.ALL_5G_FREQUENCIES:
                self.softap_5g_freq = self.softap_freq_1
            if self.softap_freq_2 in WifiEnums.ALL_2G_FREQUENCIES:
                self.softap_2g_freq = self.softap_freq_2
            elif self.softap_freq_2 in WifiEnums.ALL_5G_FREQUENCIES:
                self.softap_5g_freq = self.softap_freq_2
        # If DUT does not support BridgedAp(Dual SoftAp).
        # Decide the frequency is softap_2g_freq or softap_5g_freq
        else:
            if self.softap_freq_1 in WifiEnums.ALL_2G_FREQUENCIES:
                self.softap_2g_freq = self.softap_freq_1
            elif self.softap_freq_1 in WifiEnums.ALL_5G_FREQUENCIES:
                self.softap_5g_freq = self.softap_freq_1

        # Verify ACS when SoftAp 2G enabled.
        failures = []
        if init_softap_band == BAND_2G:
            if init_sta_band == BAND_2G:
                self.dut.log.info("Verifying 2G SoftAp chan == 2G STA chan")
                if self.softap_2g_freq != self.actual_sta_freq:
                    failures.append("Expect 2G SoftAp chan == 2G STA chan")
            else:
                self.dut.log.info("Verifying SoftAp still operates on 2G")
                if self.softap_2g_freq not in WifiEnums.ALL_2G_FREQUENCIES:
                    failures.append("Expect SoftAp still operates on 2G")

        # Verify ACS when SoftAp 5G enabled.
        elif init_softap_band == BAND_5G:
            if (country in COUNTRY_5G_NOT_ALLOWED or
               init_sta_chan in WIFI_5G_DFS_CHANNELS or
               init_sta_chan in WIFI_EU_SRD_CHANNELS):
                self.dut.log.info("Verifying SoftAp fallback to 2G")
                if self.softap_2g_freq not in WifiEnums.ALL_2G_FREQUENCIES:
                    failures.append("Expect SoftAp fallback to 2G.")
            else:
                if init_sta_band == BAND_2G:
                    self.dut.log.info("Verifying SoftAp still operates on 5G")
                    if self.softap_5g_freq not in WifiEnums.ALL_5G_FREQUENCIES:
                        failures.append("Expect SoftAp still operates on 5G.")
                elif init_sta_chan in WIFI_5G_NON_DFS_CHANNELS:
                    self.dut.log.info("Verify 5G SoftAp chan == 5g STA chan")
                    if self.softap_5g_freq != self.actual_sta_freq:
                        failures.append("Expect 5G SoftAp chan == 5G STA chan")
        failures = "\n".join(failures)
        return failures

    def validate_country_softap_acs(self, country, init_sta_band,
                                    init_sta_chan, init_softap_band):
        """ Verify SoftAp ACS on certain country work as expected.

        Steps:
            Get country, STA band, STA channel from test case name.
            Set a country code to the DUT.
            Enable a Wi-Fi network.
            DUT connects to the Wi-Fi  network.
            DUT enable SoftAp.
                P20 and previous
                    Enable SoftAp (2G OR 5G).
                P21 and later:
                    Enable BridgedAp (2G AND 5G)
            Get SoftAp(or BridgedAp) channel.
            Get AP channel.
            Verify Country SoftAp ACS.

        Args:
            country: String; Two letters country code, e.g., "US".
            init_sta_band: String; "2g" or "5g".
            init_sta_chan: Integer; use to setup OpenWrt 2G/5G channel.
            init_softap_band: String: "2g" or "5g".

        Returns: List of string; contains failure messages.
         """
        self.init_softap_band = init_softap_band
        # Set a country code to the DUT.
        self.set_country_code_and_verify(self.dut, country)
        # Get DUT STA frequency.
        self.actual_sta_freq = self.connect_wifi_network(init_sta_band,
                                                         init_sta_chan)
        # DUT Enable SoftAp.
        freq1, freq2 = self.enable_softap(self.dut)
        # Verify Country SoftAp ACS.
        return self.collect_acs_failures(freq1, freq2, country, init_sta_band,
                                         init_sta_chan, init_softap_band)

    # Tests

    @test_tracker_info(uuid="003c67f7-f4cc-4f04-ab34-28c71a7602d9")
    def test_country_us_softap_acs_sta_2g_ch_1_softap_2g(self):
        """Verify SoftAp ACS on STA 2G CH1 and SoftAp 2G in US.
           Steps: See docstring of validate_country_softap_acs()."""
        failures = self.validate_country_softap_acs("US", "2g", 1, "2g")
        asserts.assert_false(failures, str(failures))

    @test_tracker_info(uuid="b3c0a7a4-150f-469c-9191-8d446b2e2593")
    def test_country_us_softap_acs_sta_5g_ch_36_softap_2g(self):
        """Verify SoftAp ACS on STA 5G NON-DFS CH36 and SoftAp 2G in US.
           Steps: See docstring of validate_country_softap_acs()."""
        failures = self.validate_country_softap_acs("US", "5g", 36, "2g")
        asserts.assert_false(failures, str(failures))

    @test_tracker_info(uuid="7c660706-e63d-4753-bb6e-dacdf4c36cc0")
    def test_country_us_softap_acs_sta_5g_ch_132_softap_2g(self):
        """Verify SoftAp ACS on STA 5G DFS CH52 and SoftAp 2G in US.
           Steps: See docstring of validate_country_softap_acs()."""
        failures = self.validate_country_softap_acs("US", "5g", 132, "2g")
        asserts.assert_false(failures, str(failures))

    @test_tracker_info(uuid="31973348-852e-4cd7-9a72-6e8f333623c5")
    def test_country_de_softap_acs_sta_5g_ch_161_softap_2g(self):
        """Verify SoftAp ACS on STA 5G EU SRD CH149 and SoftAp 2G in DE.
           Steps: See docstring of validate_country_softap_acs()."""
        failures = self.validate_country_softap_acs("US", "5g", 161, "2g")
        asserts.assert_false(failures, str(failures))

    @test_tracker_info(uuid="8ebba60c-a32c-46b3-b9da-411b1ef66288")
    def test_country_us_softap_acs_sta_2g_ch_1_softap_5g(self):
        """Verify SoftAp ACS on STA 2G CH1 and SoftAp 5G in US.
           Steps: See docstring of validate_country_softap_acs()."""
        failures = self.validate_country_softap_acs("US", "2g", 1, "5g")
        asserts.assert_false(failures, str(failures))

    @test_tracker_info(uuid="503ece09-3030-4a69-ae15-320f5104ddd2")
    def test_country_us_softap_acs_sta_5g_ch_36_softap_5g(self):
        """Verify SoftAp ACS on STA 5G NON-DFS CH36 and SoftAp 5G in US.
           Steps: See docstring of validate_country_softap_acs()."""
        failures = self.validate_country_softap_acs("US", "5g", 36, "5g")
        asserts.assert_false(failures, str(failures))

    @test_tracker_info(uuid="35a5f2f5-067d-4d67-aeb8-58fb253f4b97")
    def test_country_us_softap_acs_sta_5g_ch_132_softap_5g(self):
        """Verify SoftAp ACS on STA 5G DFS CH52 and SoftAp 5G in US.
           Steps: See docstring of validate_country_softap_acs()."""
        failures = self.validate_country_softap_acs("US", "5g", 132, "5g")
        asserts.assert_false(failures, str(failures))

    @test_tracker_info(uuid="866954a3-72b6-4e7d-853f-9e1659cdf305")
    def test_country_de_softap_acs_sta_5g_ch_161_softap_5g(self):
        """Verify SoftAp ACS on STA 5G EU SRD CH149 and SoftAp 5G in DE.
           Steps: See docstring of validate_country_softap_acs()."""
        failures = self.validate_country_softap_acs("DE", "5g", 161, "5g")
        asserts.assert_false(failures, str(failures))

    @test_tracker_info(uuid="866954a3-72b6-4e7d-853f-9e1659cdf305")
    def test_country_jp_softap_acs_sta_5g_ch_36_softap_5g(self):
        """Verify SoftAp ACS on STA 5G EU SRD CH149 and SoftAp 5G in DE.
           Steps: See docstring of validate_country_softap_acs()."""
        failures = self.validate_country_softap_acs("JP", "5g", 36, "5g")
        asserts.assert_false(failures, str(failures))
