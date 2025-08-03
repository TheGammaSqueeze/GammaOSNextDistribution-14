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


import logging
import time
from acts import asserts
from acts import signals
from acts.test_decorators import test_tracker_info
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts_contrib.test_utils.wifi import wifi_constants
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts.controllers.ap_lib.hostapd_constants import BAND_2G
from acts.controllers.ap_lib.hostapd_constants import BAND_5G
from acts.controllers.ap_lib import hostapd_constants


WifiEnums = wutils.WifiEnums
# Each wait time add 5 seconds as buffer.
BRIDGED_AP_LAUNCH_INTERVAL_5_SECONDS = 5
BRIDGED_AP_SHUTDOWN_INTERVAL_5_MINUTES = 305
BRIDGED_AP_SHUTDOWN_INTERVAL_5_SECONDS = 5
SOFT_AP_SHUTDOWN_INTERVAL_10_MINUTES = 605
INTERVAL_9_MINUTES = 545
INTERVAL_2_MINUTES = 125
INTERVAL_1_MINUTES = 65


class WifiBridgedApTest(WifiBaseTest):
    """WiFi BridgedAp test class.

    Test Bed Requirement:
        * Android device x 1 with BridgedAp supported.
        * Android devices x2 as clients, at least Android 10.
        * OpenWrt AP x 1.
        * Google Wi-Fi x 2 as packet captures.
    """

    def setup_class(self):
        super().setup_class()

        if len(self.android_devices) == 3:
            self.dut = self.android_devices[0]
            self.client1 = self.android_devices[1]
            self.client2 = self.android_devices[2]
        else:
            raise signals.TestFailure("WifiBridgedApTest requires 3 DUTs")

        req_params = ["dbs_supported_models"]
        opt_param = []

        self.unpack_userparams(
            req_param_names=req_params, opt_param_names=opt_param)

    def setup_test(self):
        super().setup_test()
        asserts.skip_if(not self.dut.droid.wifiIsBridgedApConcurrencySupported(), "Phone %s doesn't support bridged AP." %  (self.dut.model))
        for ad in self.android_devices:
            wutils.reset_wifi(ad)
        wutils.wifi_toggle_state(self.dut, False)
        wutils.wifi_toggle_state(self.client1, True)
        wutils.wifi_toggle_state(self.client2, True)

    def teardown_test(self):
        super().teardown_test()
        # Reset unplugged status
        self.dut.adb.shell("cmd battery reset")
        if self.dut.droid.wifiIsApEnabled():
            wutils.stop_wifi_tethering(self.dut)
        for ad in self.android_devices:
            wutils.reset_wifi(ad)
            wutils.set_wifi_country_code(
                ad, wutils.WifiEnums.CountryCode.US)

        # Stop packet captures.
        if hasattr(self, "sniffer_procs"):
            for i in range(len(self.sniffer_procs)):
                try:
                    wutils.stop_pcap(
                        self.packet_capture[i], self.sniffer_procs[i], False)
                    logging.info("packet_capture[{}] is STOPPED".format(i))
                except:
                    logging.info("packet_capture[{}] is NOT STOPPED".format(i))

    def teardown_class(self):
        super().teardown_class()
        for ad in self.android_devices:
            wutils.reset_wifi(ad)
        if "AccessPoint" in self.user_params:
            del self.user_params["reference_networks"]
            del self.user_params["open_network"]

    def set_country_code_and_verify(self, ad, country_code):
        wutils.set_wifi_country_code(ad, country_code)
        # Wi-Fi ON and OFF to make sure country code take effect.
        wutils.wifi_toggle_state(ad, True)
        wutils.wifi_toggle_state(ad, False)

        country = ad.droid.wifiGetCountryCode()
        asserts.assert_true(country == country_code,
                            "country code {} is not set".format(country_code))
        ad.log.info("Country code set to : {}".format(country))

    def verify_clients_support_wpa3_sae(self, *args):
        """Check if clients support WPA3 SAE.

        Args:
            args: arbitrary number of clients. e.g., self.dut1, self.dut2, ...
        """
        duts = args
        for dut in duts:
            asserts.skip_if(not dut.droid.wifiIsWpa3SaeSupported(),
                            "All DUTs support WPA3 SAE is required")

    def verify_band_of_bridged_ap_instance(self, ad, infos, bands):
        """Check bands enabled as expected.
           This function won't be called directly.

        Args:
            infos: SoftAp info list.
            bands: A list of bands.
                   e,g,. [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                          WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G]
        """
        self.dut.log.info("Length of infos: {}, Length of bands: {}"
                          .format(len(infos), len(bands)))
        asserts.assert_true(len(infos) == len(bands),
                            "There should be {} BridgedAp instance, "
                            "instead of {}".format(len(bands), len(infos)))
        if len(bands) == 1 and (bands[0] == WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G):
            asserts.assert_true(infos[0][wifi_constants.
                                SOFTAP_INFO_FREQUENCY_CALLBACK_KEY]
                                in WifiEnums.softap_band_frequencies[bands[0]],
                                "This should be a {} instance".format(bands[0]))
        if len(bands) == 2 and (bands[0] == WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G):
            asserts.assert_true((infos[0][wifi_constants.
                                SOFTAP_INFO_FREQUENCY_CALLBACK_KEY]
                                in WifiEnums.ALL_2G_FREQUENCIES and
                                infos[1][wifi_constants.
                                SOFTAP_INFO_FREQUENCY_CALLBACK_KEY]
                                in WifiEnums.ALL_5G_FREQUENCIES) or
                                (infos[0][wifi_constants.
                                 SOFTAP_INFO_FREQUENCY_CALLBACK_KEY]
                                 in WifiEnums.ALL_5G_FREQUENCIES and
                                 infos[1][wifi_constants.
                                 SOFTAP_INFO_FREQUENCY_CALLBACK_KEY]
                                 in WifiEnums.ALL_2G_FREQUENCIES),
                                "There should only be 2G and 5G instances")

    def verify_softap_freq_equals_to_ap_freq(self, ad, infos):
        """Verify if instance frequency equal to AP frequency.
           This function won't be called directly.
        Args:
            infos: SoftAp info list.
        """
        wlan0_freq = wutils.get_wlan0_link(ad)["freq"]
        softap_freqs = []
        for i in range(len(infos)):
            softap_freqs.append(infos[i][WifiEnums.frequency_key])
        ad.log.info("softap_freqs : {}".format(softap_freqs))
        asserts.assert_true(int(wlan0_freq) in softap_freqs,
                            "AP freq != SoftAp freq")
        ad.log.info("softap freq == AP freq")

    def verify_number_band_freq_of_bridged_ap(self, ad, bands,
                                              freq_equal=False):
        """Verify every aspect of info list from BridgedAp.

        Args:
            bands: A list of bands,
                   e,g,. [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                          WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G]
            freq_equal: True if need to check SoftAp freq equals to STA freq.

        Returns:
            infos: A List of SoftAp info, e.g.,
                [{'autoShutdownTimeoutMillis': 600000,
                  'bandwidth': 4,
                  'bssid': '12:dd:0b:b9:4b:cc',
                  'frequency': 5180,
                  'wifiStandard': 6},
                 {'autoShutdownTimeoutMillis': 600000,
                  'bandwidth': 2,
                  'bssid': '12:22:f4:b9:4b:cc',
                  'frequency': 2462,
                  'wifiStandard': 4}]
        """
        callbackId = ad.droid.registerSoftApCallback()
        infos = wutils.get_current_softap_infos(ad, callbackId, True)
        self.verify_band_of_bridged_ap_instance(ad, infos, bands)
        if freq_equal:
            self.verify_softap_freq_equals_to_ap_freq(ad, infos)
        ad.droid.unregisterSoftApCallback(callbackId)
        return infos

    def verify_expected_number_of_softap_clients(self, ad, number):
        """Verify the number of softap clients.

        Args:
            number: expect number of client connect to SoftAp.
        """
        callbackId = self.dut.droid.registerSoftApCallback()
        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                                                          callbackId, number)
        ad.log.info("{} clients connect to soft ap".format(number))
        self.dut.droid.unregisterSoftApCallback(callbackId)

    def wait_interval(self, interval):
        """print different messages with different intervals.

        Args:
            interval: different interval for different situation.
        """
        if interval == BRIDGED_AP_LAUNCH_INTERVAL_5_SECONDS:
            logging.info("Wait {} seconds for BridgedAp launch"
                         .format(interval))
            time.sleep(interval)
        elif interval == BRIDGED_AP_SHUTDOWN_INTERVAL_5_MINUTES:
            logging.info("Wait {} minutes for BridgedAp shutdown"
                         .format(interval/60))
            time.sleep(interval)
        elif interval == SOFT_AP_SHUTDOWN_INTERVAL_10_MINUTES:
            logging.info("Wait {} minutes for SoftAp shutdown"
                         .format(interval/60))
            time.sleep(interval)
        elif interval == INTERVAL_9_MINUTES:
            logging.info("Wait {} minutes".format(interval/60))
            time.sleep(interval)
        elif interval == INTERVAL_2_MINUTES:
            logging.info("Wait {} minutes".format(interval/60))
            time.sleep(interval)
        elif interval == INTERVAL_1_MINUTES:
            logging.info("Wait {} minutes".format(interval/60))
            time.sleep(interval)

    def generate_softap_config(self, security):
        """Return a soft ap config

        Args:
            security: String; represent certain security. e.g., "WPA3_SAE"
        """
        config = wutils.create_softap_config()
        config[WifiEnums.SECURITY] = security
        return config

    def enable_bridged_ap(self, ad, security, bands, **kwargs):
        """Enable BridgedAp.

        Args:
            security: String; represent certain security. e.g., "WPA3_SAE"
            bands: specifies the band list for the soft ap.
        """
        self.config = self.generate_softap_config(security)
        wutils.save_wifi_soft_ap_config(
                                        ad,
                                        self.config,
                                        bands=bands,
                                        **kwargs)
        wutils.start_wifi_tethering_saved_config(self.dut)
        # Wait 5 seconds for BridgedAp launch.
        self.wait_interval(BRIDGED_AP_LAUNCH_INTERVAL_5_SECONDS)

    def sniffing_bridgedap_channels(self, infos):
        """Sniff BridgedAp channels.

        Args:
            infos: A List of SoftAp info. e.g.,
                [{'autoShutdownTimeoutMillis': 600000,
                  'bandwidth': 4,
                  'bssid': '12:dd:0b:b9:4b:cc',
                  'frequency': 5180,
                  'wifiStandard': 6},
                 {'autoShutdownTimeoutMillis': 600000,
                  'bandwidth': 2,
                  'bssid': '12:22:f4:b9:4b:cc',
                  'frequency': 2462,
                  'wifiStandard': 4}]
        """
        if hasattr(self, "packet_capture") and len(self.packet_capture) == 2:
            # Create sniffer_config.
            self.sniffer_config = {}
            for i in range(len(infos)):
                if (infos[i][WifiEnums.frequency_key] in
                   WifiEnums.ALL_5G_FREQUENCIES):
                    self.sniffer_config[BAND_5G] = WifiEnums.freq_to_channel[
                        int(infos[i][WifiEnums.frequency_key])]
                if (infos[i][WifiEnums.frequency_key] in
                   WifiEnums.ALL_2G_FREQUENCIES):
                    self.sniffer_config[BAND_2G] = WifiEnums.freq_to_channel[
                        int(infos[i][WifiEnums.frequency_key])]
            logging.info("sniffer_config : {}".format(self.sniffer_config))

            # Enable sniffers.
            self.sniffer_procs = []
            for i in range(len(self.sniffer_config)):
                self.packet_capture[i].log.info(
                    "Enable packet_capture[{}]".format(i))
                result = self.packet_capture[i].configure_monitor_mode(
                    list(self.sniffer_config.keys())[i],
                    list(self.sniffer_config.values())[i])
                if not result:
                    logging.error("Failed to enable packet_capture"
                                  "on {}".format(self.packet_capture[i]))
                self.pcap_procs = wutils.start_pcap(
                    self.packet_capture[i],
                    list(self.sniffer_config.keys())[i],
                    self.test_name)
                self.sniffer_procs.append(self.pcap_procs)
            logging.info("sniffer_procs: {}".format(self.sniffer_procs))
        else:
            logging.warning("Packet_capture not enabled, "
                            "because two packet_capture hardware required")

    def sniffing_ap_channels(self, channel1, channel2):
        """Sniff on two Wi-Fi channels.

        Args:
            channel1: Integer; a Wi-Fi channel, e.g., 6.
            channel2: Integer; a Wi-Fi channel, e,g,. 36.
        """
        if not channel1:
            channel1 = hostapd_constants.AP_DEFAULT_CHANNEL_2G
        if not channel2:
            channel2 = hostapd_constants.AP_DEFAULT_CHANNEL_5G

        if hasattr(self, "packet_capture") and len(self.packet_capture) == 2:
            # Create sniffer_config.
            self.sniffer_config = {}
            if channel1 in WifiEnums.channel_2G_to_freq:
                self.sniffer_config[BAND_2G] = channel1
            if channel1 in WifiEnums.channel_5G_to_freq:
                self.sniffer_config[BAND_5G] = channel1
            if channel2 in WifiEnums.channel_2G_to_freq:
                self.sniffer_config[BAND_2G] = channel2
            if channel2 in WifiEnums.channel_5G_to_freq:
                self.sniffer_config[BAND_5G] = channel2
            logging.info("sniffer_config : {}".format(self.sniffer_config))

            # Enable sniffers.
            self.sniffer_procs = []
            for i in range(len(self.sniffer_config)):
                self.packet_capture[i].log.info(
                    "Enable packet_capture[{}]".format(i))
                result = self.packet_capture[i].configure_monitor_mode(
                    list(self.sniffer_config.keys())[i],
                    list(self.sniffer_config.values())[i])
                if not result:
                    logging.error("Failed to enable packet_capture"
                                  "on {}".format(self.packet_capture[i]))
                self.pcap_procs = wutils.start_pcap(
                    self.packet_capture[i],
                    list(self.sniffer_config.keys())[i],
                    self.test_name)
                self.sniffer_procs.append(self.pcap_procs)
            logging.info("sniffer_procs: {}".format(self.sniffer_procs))
        else:
            logging.debug("Packet_capture not enabled, "
                          "because two packet_capture hardware required")

    def start_ap(self, channel_2g, channel_5g):
        """Enable OpenWrt AP with specific 2G/5G channels

        Args:
            channel_2g: Integer; a 2G channel, e.g., 6.
            channel_5g: Integer; a 5G channel, e,g,. 36
        """
        if not channel_2g:
            channel_2g = hostapd_constants.AP_DEFAULT_CHANNEL_2G
        if not channel_5g:
            channel_5g = hostapd_constants.AP_DEFAULT_CHANNEL_5G

        # Enable OpenWrt AP.
        if "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(wpa_network=True,
                                                channel_2g=channel_2g,
                                                channel_5g=channel_5g)

    def two_clients_connect_to_wifi_network(self, dut1, dut2, config):
        """Connect two clients to different BridgedAp instances.
           This function will be called only when BridgedAp ON.

        Args:
            config: Wi-Fi config, e.g., {"SSID": "xxx", "password": "xxx"}
        Steps:
            Backup config.
            Register SoftAp Callback.
            Get SoftAp Infos.
            Get BSSIDs from Infos.
            Connect two clients to different BridgedAp instances.
            Restore config.
        """
        # Make sure 2 instances enabled, and get BSSIDs from BridgedAp Infos.
        callbackId = self.dut.droid.registerSoftApCallback()
        infos = wutils.get_current_softap_infos(self.dut, callbackId, True)
        self.dut.droid.unregisterSoftApCallback(callbackId)

        if len(infos) == 0:
            raise signals.TestFailure("No BridgedAp instance")
        elif len(infos) == 1:
            raise signals.TestFailure(
                "Only one BridgedAp instance, should be two")
        else:
            bssid_5g = infos[0][wifi_constants.SOFTAP_INFO_BSSID_CALLBACK_KEY]
            bssid_2g = infos[1][wifi_constants.SOFTAP_INFO_BSSID_CALLBACK_KEY]

        # Two configs for BridgedAp 2G and 5G instances.
        config_5g = config.copy()
        config_2g = config.copy()
        config_5g[WifiEnums.BSSID_KEY] = bssid_5g
        config_2g[WifiEnums.BSSID_KEY] = bssid_2g

        # Connect two clients to BridgedAp.
        wutils.connect_to_wifi_network(dut1, config_5g,
                                       check_connectivity=False)
        wutils.connect_to_wifi_network(dut2, config_2g,
                                       check_connectivity=False)

        # Verify if Clients connect to the expected BridgedAp instances.
        client1_bssid = wutils.get_wlan0_link(
            self.client1)[wifi_constants.SOFTAP_INFO_BSSID_CALLBACK_KEY]
        client2_bssid = wutils.get_wlan0_link(
            self.client2)[wifi_constants.SOFTAP_INFO_BSSID_CALLBACK_KEY]
        asserts.assert_true(client1_bssid == bssid_5g,
                            "Client1 does not connect to the 5G instance")
        asserts.assert_true(client2_bssid == bssid_2g,
                            "Client2 does not connect to the 2G instance")

    def client_connects_to_a_bridgeap(self, ad, band):
        """One client connects to a BridgeAp instance

        Args:
            band: String; '2g' or '5g'.
        """
        callbackId = self.dut.droid.registerSoftApCallback()
        infos = wutils.get_current_softap_infos(self.dut, callbackId, True)
        self.dut.droid.unregisterSoftApCallback(callbackId)
        # Make one client connects to 2G instance if band == '2g'.
        if band == BAND_2G:
            if (infos[0][wifi_constants.SOFTAP_INFO_FREQUENCY_CALLBACK_KEY]
               in WifiEnums.ALL_2G_FREQUENCIES):
                bssid_2g = (infos[0][wifi_constants.
                            SOFTAP_INFO_BSSID_CALLBACK_KEY])
            else:
                bssid_2g = (infos[1][wifi_constants.
                            SOFTAP_INFO_BSSID_CALLBACK_KEY])
            config_2g = self.config.copy()
            config_2g[WifiEnums.BSSID_KEY] = bssid_2g
            wutils.connect_to_wifi_network(ad, config_2g,
                                           check_connectivity=False)
        # Make one client connects to 5G instance if band == '5g'.
        elif band == BAND_5G:
            if (infos[0][wifi_constants.SOFTAP_INFO_FREQUENCY_CALLBACK_KEY]
               in WifiEnums.ALL_5G_FREQUENCIES):
                bssid_5g = (infos[0][wifi_constants.
                            SOFTAP_INFO_BSSID_CALLBACK_KEY])
            else:
                bssid_5g = (infos[1][wifi_constants.
                            SOFTAP_INFO_BSSID_CALLBACK_KEY])
            config_5g = self.config.copy()
            config_5g[WifiEnums.BSSID_KEY] = bssid_5g
            wutils.connect_to_wifi_network(ad, config_5g,
                                           check_connectivity=False)

    # Tests

    @test_tracker_info(uuid="6f776b4a-b080-4b52-a330-52aa641b18f2")
    def test_two_clients_ping_bridged_ap_5g_2g_wpa3_country_us(self):
        """Test clients on different instances can ping each other.

        Steps:
            Backup config.
            Make sure clients support WPA3 SAE.
            Make sure DUT is able to enable BridgedAp.
            Enable BridgedAp with bridged configuration.
            RegisterSoftApCallback.
            Check the bridged AP enabled succeed.
            start sniffer and sniffing on BridgedAp channels.
            Force client#1 connect to 5G.
            Force client#2 connect to 2.4G.
            Trigger client#1 and client#2 each other.
            Stop sniffers.
            Restore config.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()
        # Make sure clients support WPA3 SAE.
        self.verify_clients_support_wpa3_sae(self.client1, self.client2)
        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        infos = self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)

        self.sniffing_bridgedap_channels(infos)

        self.two_clients_connect_to_wifi_network(self.client1, self.client2,
                                                 self.config)
        # Trigger client#1 and client#2 ping each other.
        wutils.validate_ping_between_two_clients(self.client1, self.client2)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)
    @test_tracker_info(uuid="8685a6cb-f7be-4384-b25b-23cecb4cb6dc")
    def test_two_clients_ping_bridged_ap_5g_2g_wpa3_sae_transition_country_us(self):
        """Test clients on different instances can ping each other.

        Steps:
            Refer to test_two_clients_ping_bridged_ap_5g_2g_wpa3_country_us,
            but this case enable WPA3/WPA2-Personal SoftAp security type.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()
        # Make sure clients support WPA3 SAE.
        self.verify_clients_support_wpa3_sae(self.client1, self.client2)
        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE_TRANSITION,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        infos = self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)

        self.sniffing_bridgedap_channels(infos)

        self.two_clients_connect_to_wifi_network(self.client1, self.client2,
                                                 self.config)
        # Trigger client#1 and client#2 ping each other.
        wutils.validate_ping_between_two_clients(self.client1, self.client2)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="b217d4de-cd09-4a8f-b27d-302ae5b86c7e")
    def test_two_clients_ping_bridged_ap_5g_2g_wpa2_country_us(self):
        """Test clients on different instances can ping each other.

        Steps:
            Refer to test_two_clients_ping_bridged_ap_5g_2g_wpa3_country_us,
            but this case enable WPA2-Personal SoftAp security type.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()
        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA2,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        infos = self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)

        self.sniffing_bridgedap_channels(infos)

        self.two_clients_connect_to_wifi_network(self.client1, self.client2,
                                                 self.config)
        # Trigger client#1 and client#2 ping each other.
        wutils.validate_ping_between_two_clients(self.client1, self.client2)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="ef98a5ea-c7d5-4cc4-a2f8-e5fd44648788")
    def test_two_clients_ping_bridged_ap_5g_2g_no_security_country_us(self):
        """Test clients on different instances can ping each other.

        Steps:
            Refer to test_two_clients_ping_bridged_ap_5g_2g_wpa3_country_us,
            but this case enable OPEN SoftAp security type.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()
        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.OPEN,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        infos = self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)

        self.sniffing_bridgedap_channels(infos)

        self.two_clients_connect_to_wifi_network(self.client1, self.client2,
                                                 self.config)
        # Trigger client#1 and client#2 ping each other.
        wutils.validate_ping_between_two_clients(self.client1, self.client2)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="0325ee58-ed8e-489e-9dee-55740406f896")
    def test_bridged_ap_5g_2g_and_sta_5g_dfs(self):
        """Test if auto fallback to one single 2G AP mode when
         BridgedAP enabled and STA connect to a 5G DFS channel.

        Steps:
            Backup config.
            DUT enable BridgedAp.
            start sniffer and sniffing on AP channels.
            DUT connect to a 5G DFS channel Wi-Fi network.
            Verify 5G instance is shutdown.
            Stop sniffers.
            Restore config.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()

        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)

        self.sniffing_ap_channels(channel1=None, channel2=132)
        self.start_ap(channel_2g=None, channel_5g=132)
        wutils.wifi_toggle_state(self.dut, True)
        wutils.connect_to_wifi_network(self.dut, self.wpa_networks[0][BAND_5G])

        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G], False)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="9a5d4ca9-67fc-412c-8114-01c43c34a76d")
    def test_bridged_ap_5g_2g_and_sta_5g_non_dfs(self):
        """Test 5G scc when BridgedAp enabled and 5G STA.

        Steps:
            Backup config.
            DUT enable BridgedAp.
            start sniffer and sniffing on AP channels.
            DUT connect to a 5G non-DFS channel Wi-Fi network.
            Verify STA frequency equals to 5G BridgedAp frequency.
            Stop sniffers.
            Restore config.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()

        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)

        self.sniffing_ap_channels(channel1=None, channel2=36)
        self.start_ap(channel_2g=None, channel_5g=36)
        wutils.wifi_toggle_state(self.dut, True)
        wutils.connect_to_wifi_network(self.dut, self.wpa_networks[0][BAND_5G])

        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], True)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="e26e8a72-3e21-47b3-8fd8-4c4db5fb2573")
    def test_bridged_ap_5g_2g_and_sta_2g(self):
        """ Test 2G SCC when BridgedAp enable and 2G STA.

        Steps:
            Backup config.
            DUT enable BridgedAp.
            start sniffer and sniffing on AP channels.
            STA connect to a 2G Wi-Fi network.
            Verify STA frequency equals to 2G BridgedAp frequency.
            Stop sniffers.
            Restore config.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()

        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)

        self.sniffing_ap_channels(channel1=6, channel2=None)
        self.start_ap(channel_2g=6, channel_5g=None)
        wutils.wifi_toggle_state(self.dut, True)
        wutils.connect_to_wifi_network(self.dut, self.wpa_networks[0][BAND_2G])

        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], True)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="b25f710a-2e53-456e-8280-dcdd37badd6c")
    def test_sta_5g_dfs_and_bridged_ap_5g_2g(self):
        """Test auto fallback to Single AP mode
           when STA connect to a DFS channel.

        Steps:
            Backup config.
            start sniffer and sniffing on AP channels.
            Enable a Wi-Fi network.
            DUT connect to a 5G DFS channel Wi-Fi network.
            DUT enable BridgedAp.
            Verify 5G instance is shutdown and only 2G instance enabled.
            Stop sniffers.
            Restore config.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()

        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        self.sniffing_ap_channels(channel1=None, channel2=132)
        self.start_ap(channel_2g=None, channel_5g=132)
        wutils.wifi_toggle_state(self.dut, True)
        wutils.connect_to_wifi_network(self.dut, self.wpa_networks[0][BAND_5G])

        # Enable BridgedAp
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        # # Verify only 2G instance enabled.
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G], False)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="6acaed3f-616f-4a03-a329-702e6cc537bd")
    def test_sta_5g_non_dfs_and_bridged_ap_5g_2g(self):
        """Test 5G scc when 5G STA and BridgedAp enabled.

        Steps:
            Backup config.
            start sniffer and sniffing on AP channels.
            Enable a Wi-Fi network.
            DUT connect to a 5G non-DFS channel Wi-Fi network.
            DUT enable BridgedAp.
            Verify STA frequency equals to 5G BridgedAp frequency.
            Stop sniffers.
            Restore config.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()

        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        self.sniffing_ap_channels(channel1=None, channel2=36)
        self.start_ap(channel_2g=None, channel_5g=36)
        wutils.wifi_toggle_state(self.dut, True)
        wutils.connect_to_wifi_network(self.dut, self.wpa_networks[0][BAND_5G])

        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        # Verify STA frequency equals to 5G BridgedAp frequency.
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], True)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="e24e7f2d-f1d5-4dc7-aa6a-487677864d1d")
    def test_sta_2g_and_bridged_ap_5g_2g(self):
        """ Test 2G SCC when 2G STA and BridgedAp enable.

        Steps:
            Backup config.
            start sniffer and sniffing on AP channels.
            Enable a Wi-Fi network.
            DUT connect to a 2G Wi-Fi network.
            DUT enable BridgedAp.
            Verify STA frequency equals to 2G BridgedAp frequency.
            Stop sniffers.
            Restore config.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()

        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        self.sniffing_ap_channels(channel1=6, channel2=None)
        self.start_ap(channel_2g=6, channel_5g=None)
        wutils.wifi_toggle_state(self.dut, True)
        wutils.connect_to_wifi_network(self.dut, self.wpa_networks[0][BAND_2G])

        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], True)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="927d564d-2ac4-4e6f-bb36-270efd519e0b")
    def test_bridged_ap_5g_2g_shutdown_5g_2g_no_client(self):
        """Test the BridgeAp shutdown mechanism with no client connect to it.

        Steps:
            Backup config.
            DUT turns ON BridgedAp.
            Verify no client connect to the BridgedAp.
            Wait for 5 minutes.
            Verify that 5G BridgedAp instance is shutdown.
            Maks sure there is still no client connect to the BridgedAp.
            Wait for 5 minutes.
            Verify that 2G BridgedAp instance is shutdown.
            Restore config.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()

        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Simulate the unplugged scenario
        self.dut.adb.shell("cmd battery unplug")
        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        infos = self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)

        # No client connection, wait 5 minutes, verify 5G is shutdown.
        self.verify_expected_number_of_softap_clients(self.dut, 0)
        self.wait_interval(BRIDGED_AP_SHUTDOWN_INTERVAL_5_MINUTES)
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G], False)
        # No client connection, wait 5 minutes, verify 2G is shutdown.
        self.verify_expected_number_of_softap_clients(self.dut, 0)
        self.wait_interval(BRIDGED_AP_SHUTDOWN_INTERVAL_5_MINUTES)

        self.verify_number_band_freq_of_bridged_ap(self.dut, [], False)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="4c5b210c-412f-40e4-84b6-2a12dbffa017")
    def test_bridged_ap_5g_2g_shutdown_5g_auto_shutdown_2g(self):
        """Test the BridgeAp shutdown mechanism with no client connect to it.

        Steps:
            Backup config.
            DUT turns ON BridgedAp.
            start sniffer and sniffing on BridgedAp channels.
            Verify no client connect to the BridgedAp.
            Wait for 5 minutes.
            Verify that 5G BridgedAp instance is shutdown.
            A client connect to the 2G BridgedAp.
            The client disconnect from 2G BridgedAp.
            Wait for 10 minutes.
            Verify 2G BridgedAp is shutdown, no BridgedAp instance enabled.
            Stop sniffers.
            Restore config.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()

        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Simulate the unplugged scenario
        self.dut.adb.shell("cmd battery unplug")
        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        infos = self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)

        self.sniffing_bridgedap_channels(infos)

        # Verify no connection to BridgedAp, wait for 5 mins, 5G shutdown.
        self.verify_expected_number_of_softap_clients(self.dut, 0)
        self.wait_interval(BRIDGED_AP_SHUTDOWN_INTERVAL_5_MINUTES)
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G], False)

        # A client connect to 2G BridgedAp instance.
        callbackId = self.dut.droid.registerSoftApCallback()
        infos = wutils.get_current_softap_infos(self.dut, callbackId, True)
        self.dut.droid.unregisterSoftApCallback(callbackId)
        bssid_2g = infos[0][wifi_constants.SOFTAP_INFO_BSSID_CALLBACK_KEY]
        config_2g = self.config.copy()
        config_2g[WifiEnums.BSSID_KEY] = bssid_2g
        wutils.connect_to_wifi_network(self.client1, config_2g,
                                       check_connectivity=False)
        self.wait_interval(BRIDGED_AP_SHUTDOWN_INTERVAL_5_SECONDS)

        # Client disconnect From 2G BridgedAp instance.
        is_disconnect = self.client1.droid.wifiDisconnect()
        self.client1.log.info("Disconnected from SoftAp")
        if not is_disconnect:
            raise signals.TestFailure("Wi-Fi is not disconnected as expect")
        wutils.reset_wifi(self.client1)

        # Verify 2G instance is still enabled.
        self.wait_interval(INTERVAL_9_MINUTES)
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G], False)

        # Verify all BridgedAp instances are shutdown after 10 minutes.
        self.wait_interval(INTERVAL_1_MINUTES)
        self.verify_number_band_freq_of_bridged_ap(self.dut, [], False)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="2b1b4579-d610-4983-83f4-5fc34b3cdd6b")
    def test_bridged_ap_5g_2g_shutdown_5g_auto_shutdown_2g_after_10_mins(self):
        """Test the BridgeAp shutdown mechanism with no client connect to it.

        Steps:
            Backup config.
            DUT turns ON BridgedAp.
            start sniffer and sniffing on BridgedAp channels.
            Verify no client connect to the BridgedAp.
            Wait for 5 minutes.
            Verify that 5G BridgedAp instance is shutdown.
            A client connect to the 2G BridgedAp at 7th minutes.
            The client disconnect from 2G BridgedAp at 9th minutes.
            Wait for 10 minutes.
            Verify 2G BridgedAp is shutdown, no BridgedAp instance enabled.
            Stop sniffers.
            Restore config.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()

        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Simulate the unplugged scenario
        self.dut.adb.shell("cmd battery unplug")
        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G],
                               bridged_opportunistic_shutdown_enabled=True,
                               shutdown_timeout_enable=True)
        infos = self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)

        self.sniffing_bridgedap_channels(infos)

        self.verify_expected_number_of_softap_clients(self.dut, 0)
        self.wait_interval(BRIDGED_AP_SHUTDOWN_INTERVAL_5_MINUTES)
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G], False)

        # Client connects to 2G instance at 7th mins.
        self.wait_interval(INTERVAL_2_MINUTES)
        callbackId = self.dut.droid.registerSoftApCallback()
        infos = wutils.get_current_softap_infos(self.dut, callbackId, True)
        self.dut.droid.unregisterSoftApCallback(callbackId)
        bssid_2g = infos[0][wifi_constants.SOFTAP_INFO_BSSID_CALLBACK_KEY]
        config_2g = self.config.copy()
        config_2g[WifiEnums.BSSID_KEY] = bssid_2g
        wutils.connect_to_wifi_network(self.client1, config_2g,
                                       check_connectivity=False)
        self.wait_interval(BRIDGED_AP_SHUTDOWN_INTERVAL_5_SECONDS)

        # Client disconnect From 2G BridgedAp instance at 9th mins.
        self.wait_interval(INTERVAL_2_MINUTES)
        is_disconnect = self.client1.droid.wifiDisconnect()
        self.client1.log.info("Disconnected from SoftAp")
        if not is_disconnect:
            raise signals.TestFailure("Wi-Fi is not disconnected as expect")
        wutils.reset_wifi(self.client1)

        # Make sure 2G instance is still enabled.
        self.wait_interval(INTERVAL_9_MINUTES)
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G], False)

        self.wait_interval(INTERVAL_1_MINUTES)
        self.verify_number_band_freq_of_bridged_ap(self.dut, [], False)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="1785a487-dd95-4c17-852d-3c9b7b7dd4c3")
    def test_bridged_ap_5g_2g_fallback_2g_country_jp(self):
        """Test BridgedAp fallback to Single AP mode with JP country code.

        Steps:
            Backup config.
            Set DUT country code to "JP".
            DUT turns ON BridgedAp.
            Verify only 2G BridgedAp instance is enabled.
            Restore config.
        """
        asserts.skip_if(not self.dut.droid.wifiIsBridgedApConcurrencySupported(),
                        "DUT %s doesn't support bridged AP." %  (self.dut.model))
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()

        # Set country code to JP and enable BridgedAp
        self.set_country_code_and_verify(self.dut, WifiEnums.CountryCode.JAPAN)

        # Enable BridgedAp
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        # Verify only 2G BridgedAp instance enabled.
        infos = self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G], False)
        self.sniffing_bridgedap_channels(infos)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="b5c7c6df-b181-4bba-a7b3-48fad0d7fa98")
    def test_bridged_ap_5g_2g_shutdown_2g_after_5_minutes(self):
        """Test the BridgeAp shutdown mechanism.

        Steps:
            Backup config.
            DUT turns ON BridgedAp.
            start sniffer and sniffing on BridgedAp channels.
            A Client connect to BridgeAp 5G instance.
            Wait for 5 minutes.
            Verify 2G instance is shutdown and only 5G instance exists.
            Stop sniffers.
            Restore config."""
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()

        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Simulate the unplugged scenario
        self.dut.adb.shell("cmd battery unplug")
        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        infos = self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)

        self.sniffing_bridgedap_channels(infos)

        # Client connects to the 5G BridgedAp instance.
        self.client_connects_to_a_bridgeap(self.client1, BAND_5G)
        self.wait_interval(BRIDGED_AP_SHUTDOWN_INTERVAL_5_MINUTES)
        # Verify there is only one client connects to the BridgedAp.
        self.verify_expected_number_of_softap_clients(self.dut, 1)
        # Verify the BridgedAp instance is 5G.
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)
        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="683b2c7f-7f24-4324-be09-0b6554d5d7a8")
    def test_bridged_ap_5g_2g_shutdown_5g_after_5_minutes(self):
        """Test the BridgeAp shutdown mechanism.

        Steps:
            Backup config.
            DUT turns ON BridgedAp.
            start sniffer and sniffing on BridgedAp channels.
            A Client connect to BridgeAp 2G instance.
            Wait for 5 minutes.
            Verify 5G instance is shutdown and only 2G instance exists.
            Stop sniffers.
            Restore config."""
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()

        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Simulate the unplugged scenario
        self.dut.adb.shell("cmd battery unplug")
        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        infos = self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)
        self.sniffing_bridgedap_channels(infos)

        # Client connects to the 2G BridgedAp instance.
        self.client_connects_to_a_bridgeap(self.client1, BAND_2G)
        self.wait_interval(BRIDGED_AP_SHUTDOWN_INTERVAL_5_MINUTES)
        # Verify there is only one client connects to the BridgedAp.
        self.verify_expected_number_of_softap_clients(self.dut, 1)
        # Verify the BridgedAp instance is 2G.
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G], False)
        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="6de46e5b-04c3-4fba-a21d-e914a3e34e24")
    def test_bridged_ap_5g_2g_no_shutdown_after_5_minutes(self):
        """Test clients on different instances can ping each other.

        Steps:
            Backup config.
            Make sure clients support WPA3 SAE.
            Make sure DUT is able to enable BridgedAp.
            Enable BridgedAp with bridged configuration.
            RegisterSoftApCallback.
            Check the bridged AP enabled succeed.
            start sniffer and sniffing on BridgedAp channels.
            Client#1 connect to 5G instance.
            Client#2 connect to 2G instance.
            Wait for 5 minutes.
            Verify both 2G/5G BridgedAp instances are exist.
            Stop sniffers.
            Restore config.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()
        # Make sure clients support WPA3 SAE.
        self.verify_clients_support_wpa3_sae(self.client1, self.client2)
        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Simulate the unplugged scenario
        self.dut.adb.shell("cmd battery unplug")
        # Enable BridgedAp and verify both 2G,5G instances have been enabled.
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G])
        infos = self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)

        self.sniffing_bridgedap_channels(infos)

        self.two_clients_connect_to_wifi_network(self.client1, self.client2,
                                                 self.config)
        self.wait_interval(BRIDGED_AP_SHUTDOWN_INTERVAL_5_MINUTES)
        # Verify there are two clients connect to the BridgedAp.
        self.verify_expected_number_of_softap_clients(self.dut, 2)
        # Verify both 2G/5G BridgedAp instances are exist.
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)
        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="f8742dfb-5232-4fe8-b568-3a99a9356d59")
    def test_bridged_ap_5g_2g_extend_compatibility_on_no_shutdown_5g(self):
        """Test BridgedAp mechanism when "Extend compatibility is ON.

        Steps:
            Backup config.
            Make sure clients support WPA3 SAE.
            Make sure DUT is able to enable BridgedAp.
            Enable BridgedAp with Extend compatibility is ON.
            RegisterSoftApCallback.
            Check the bridged AP enabled succeed.
            start sniffer and sniffing on BridgedAp channels.
            Client#1 connect to 2G.
            Verify both 2G/5G instances are exist.
            Stop sniffers.
            Restore config.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()
        # Make sure clients support WPA3 SAE.
        self.verify_clients_support_wpa3_sae(self.client1, self.client2)
        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Simulate the unplugged scenario
        self.dut.adb.shell("cmd battery unplug")
        # Enable BridgedAp with "Extend compatibility set to ON".
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G],
                               bridged_opportunistic_shutdown_enabled=False)
        # Client connects to the 2G BridgedAp instance and wait 5 minutes.
        self.client_connects_to_a_bridgeap(self.client1, BAND_2G)
        self.wait_interval(BRIDGED_AP_SHUTDOWN_INTERVAL_5_MINUTES)
        # Verify both 2G/5G BridgedAp instances exist.
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)
        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="050f8dd8-ed38-4f7a-87a9-9dbdade58cc7")
    def test_bridged_ap_5g_2g_extend_compatibility_on_no_shutdown_2g(self):
        """Test BridgedAp mechanism when "Extend compatibility is ON.

        Steps:
            Backup config.
            Make sure clients support WPA3 SAE.
            Make sure DUT is able to enable BridgedAp.
            Enable BridgedAp with Extend compatibility is ON.
            RegisterSoftApCallback.
            Check the bridged AP enabled succeed.
            start sniffer and sniffing on BridgedAp channels.
            Client#1 connect to 5G.
            Verify both 2G/5G instances are exist.
            Stop sniffers.
            Restore config.
        """
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()
        # Make sure clients support WPA3 SAE.
        self.verify_clients_support_wpa3_sae(self.client1, self.client2)
        # Make sure DUT is able to enable BridgedAp.
        is_supported = wutils.check_available_channels_in_bands_2_5(
            self.dut, wutils.WifiEnums.CountryCode.US)
        asserts.skip_if(not is_supported, "BridgedAp is not supported.")

        # Simulate the unplugged scenario
        self.dut.adb.shell("cmd battery unplug")
        # Enable BridgedAp with "Extend compatibility set to ON".
        self.enable_bridged_ap(self.dut,
                               WifiEnums.SoftApSecurityType.WPA3_SAE,
                               bands=[WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                                      WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G],
                               bridged_opportunistic_shutdown_enabled=False)
        # Client connects to the 5G BridgedAp instance and wait 5 minutes.
        self.client_connects_to_a_bridgeap(self.client1, BAND_5G)
        self.wait_interval(BRIDGED_AP_SHUTDOWN_INTERVAL_5_MINUTES)
        # Verify both 2G/5G BridgedAp instances exist.
        self.verify_number_band_freq_of_bridged_ap(
            self.dut, [WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                       WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G], False)
        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)
