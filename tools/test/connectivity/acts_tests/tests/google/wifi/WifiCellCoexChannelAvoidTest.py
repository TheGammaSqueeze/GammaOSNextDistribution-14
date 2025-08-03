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
import json
import pprint

from acts import asserts
from acts import utils
from acts.keys import Config
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi import wifi_constants
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.wifi.wifi_constants import\
    COEX_BAND, COEX_CHANNEL, COEX_POWER_CAP_DBM, KEY_COEX_UNSAFE_CHANNELS, KEY_COEX_RESTRICTIONS

WifiEnums = wutils.WifiEnums
WIFI_CONFIG_APBAND_2G = WifiEnums.WIFI_CONFIG_APBAND_2G
WIFI_CONFIG_APBAND_5G = WifiEnums.WIFI_CONFIG_APBAND_5G
WIFI_CONFIG_APBAND_AUTO = WifiEnums.WIFI_CONFIG_APBAND_AUTO
WPA3_SAE_TRANSITION_SOFTAP = WifiEnums.SoftApSecurityType.WPA3_SAE_TRANSITION
WPA3_SAE_SOFTAP = WifiEnums.SoftApSecurityType.WPA3_SAE
WAIT_AFTER_REBOOT = 10

BRIDGED_AP_LAUNCH_INTERVAL_5_SECONDS = 5


class WifiCellCoexChannelAvoidTest(WifiBaseTest):
    def __init__(self, configs):
        super().__init__(configs)
        self.generate_test_list()

    def generate_test_list(self):
        """Generates a test list sorted with coex_unsafe_list_sap list.
            Test with a sorted list can reduce lots of time
            on switch radio and band start up.
        """
        sorted_list = sorted(self.user_params["coex_unsafe_list_sap"],
                             key=lambda radio: radio["band"])
        for test_item in sorted_list:
            self.init_test_case(self.coex_unsafechannel_avoidance, test_item)
        pprint.pprint("self.tests = {}".format(self.tests))

    def init_test_case(self, coex_unsafechannel_avoidance, test_item):
        """Generates a single test case from the given data.

        Args:
            coex_unsafechannel_avoidance: The base test case function to run.
            test_item: test case required info include ["uuid","coex_unsafe_case"]
        """
        test_name = test_item["coex_unsafe_case"]
        test_tracker_uuid = test_item["uuid"]
        if not test_name.startswith("test_"):
            test_name = "test_{}".format(test_name)
        test_case = test_tracker_info(uuid=test_tracker_uuid)(
            lambda: coex_unsafechannel_avoidance(test_item))
        setattr(self, test_name, test_case)
        self.tests.append(test_name)

    def setup_class(self):
        """It will setup the required dependencies from config file and configure
           the devices for softap mode testing.

        Returns:
            True if successfully configured the requirements for testing.
        """
        super().setup_class()
        self.dut = self.android_devices[0]
        self.dut_client = self.android_devices[1]
        req_params = [
            "dbs_supported_models", "sta_concurrency_supported_models",
            "wifi6_models", "coex_unsafe_list_sap"
        ]
        opt_param = ["reference_networks"]
        self.unpack_userparams(req_param_names=req_params,
                               opt_param_names=opt_param)
        if "AccessPoint" in self.user_params:
            self.legacy_configure_ap_and_start()
        elif "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(wpa_network=True)
        self.wifi_network = self.reference_networks[0]["2g"]
        # Do a simple version of init - mainly just sync the time and enable
        # verbose logging.  This test will fail if the DUT has a sim and cell
        # data is disabled.  We would also like to test with phones in less
        # constrained states (or add variations where we specifically
        # constrain).
        utils.require_sl4a((self.dut, self.dut_client))
        utils.sync_device_time(self.dut)
        utils.sync_device_time(self.dut_client)
        # Enable verbose logging on the duts
        self.dut.droid.wifiEnableVerboseLogging(1)
        asserts.assert_equal(
            self.dut.droid.wifiGetVerboseLoggingLevel(), 1,
            "Failed to enable WiFi verbose logging on the softap dut.")
        self.dut_client.droid.wifiEnableVerboseLogging(1)
        asserts.assert_equal(
            self.dut_client.droid.wifiGetVerboseLoggingLevel(), 1,
            "Failed to enable WiFi verbose logging on the client dut.")
        wutils.wifi_toggle_state(self.dut, True)
        wutils.wifi_toggle_state(self.dut_client, True)
        self.AP_IFACE = 'wlan0'
        if self.dut.model in self.dbs_supported_models:
            self.AP_IFACE = 'wlan1'
        if self.dut.model in self.sta_concurrency_supported_models:
            self.AP_IFACE = 'wlan2'
        if len(self.android_devices) > 2:
            utils.sync_device_time(self.android_devices[2])
            self.android_devices[2].droid.wifiEnableVerboseLogging(1)
            asserts.assert_equal(
                self.android_devices[2].droid.wifiGetVerboseLoggingLevel(), 1,
                "Failed to enable WiFi verbose logging on the client dut.")
            self.dut_client_2 = self.android_devices[2]

    def teardown_class(self):
        super().teardown_class()
        for ad in self.android_devices:
            wutils.wifi_toggle_state(ad, True)
            wutils.reset_wifi(ad)
            time.sleep(WAIT_AFTER_REBOOT)
        if self.dut.droid.wifiIsApEnabled():
            wutils.stop_wifi_tethering(self.dut)
        if "AccessPoint" in self.user_params:
            del self.user_params["reference_networks"]
            del self.user_params["open_network"]

    def setup_test(self):
        super().setup_test()
        for ad in self.android_devices:
            wutils.wifi_toggle_state(ad, True)
        self.dut.reboot()
        time.sleep(WAIT_AFTER_REBOOT)

    def teardown_test(self):
        super().teardown_test()
        for ad in self.android_devices:
            wutils.wifi_toggle_state(ad, True)
        if self.dut.droid.wifiIsApEnabled():
            wutils.stop_wifi_tethering(self.dut)
        self.dut.log.debug("Toggling Airplane mode OFF.")
        asserts.assert_true(
            utils.force_airplane_mode(self.dut, False),
            "Can not turn off airplane mode: %s" % self.dut.serial)
        #reset coexcell setting
        self.dut.adb.shell('cmd wifi reset-coex-cell-channels')

    """ Helper Functions """

    def coex_unsafe_channel_key(self, unsafe_channel):
        if COEX_POWER_CAP_DBM in unsafe_channel:
            return (unsafe_channel[COEX_BAND], unsafe_channel[COEX_CHANNEL],
                    unsafe_channel[COEX_POWER_CAP_DBM])
        return (unsafe_channel[COEX_BAND], unsafe_channel[COEX_CHANNEL])

    def enable_softap(self, ad, band=None):
        """ Enable SoftAp of the DUT

        Returns:
            (freq1, freq2): Integer; a 2G frequency and a 5G frequency if DUT
                            support BridgedAp.
            freq: Integer; a frequency from SoftAp.
            None, bandwidth: Just a placeholder, won't be used.
        """
        # Enable SoftAp
        # Create SoftAp config.
        config = wutils.create_softap_config()
        # If DUT support BridgedAp, then two BridgedAp instances enabled.
        if self.dut.droid.wifiIsBridgedApConcurrencySupported():
            wutils.save_wifi_soft_ap_config(
                ad,
                config,
                bands=[
                    WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
                    WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G
                ])
        # If DUT does not support BridgedAp, 2G OR 5G SoftAp enabled.
        else:
            if self.init_softap_band == BAND_2G:
                band = WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G
            if self.init_softap_band == BAND_5G:
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
                self.dut.log.info(
                    "DUT connected to AP on freq: {},{}, chan: {} ,{}".format(
                        freq_1, freq_2, WifiEnums.freq_to_channel[freq_1],
                        WifiEnums.freq_to_channel[freq_2]))
                return freq_1, freq_2
            # if DUT BridgedAp has only one instances, return the frequency.
            elif len(infos) == 1:
                freq = infos[0]["frequency"]
                self.dut.log.info(
                    "DUT connected to AP on freq: {}, chan: {}".format(
                        freq, WifiEnums.freq_to_channel[freq]))
                return freq
            else:
                raise signals.TestFailure("There should be SoftAp instance.")
        # if DUT does not support BridgedAp:
        else:
            # Return SoftAp frequency.
            callbackId = ad.droid.registerSoftApCallback()
            freq, bandwidth = wutils.get_current_softap_info(
                ad, callbackId, True)
            ad.log.info("SoftAp freq: {}".format(freq))
            ad.droid.unregisterSoftApCallback(callbackId)
            self.dut.log.info(
                "DUT connected to AP on freq: {}, chan: {}".format(
                    freq, WifiEnums.freq_to_channel[freq]))
            return freq, bandwidth

    """ Tests Begin """

    def coex_unsafechannel_avoidance(self, test_item):
        self.radio = test_item["radio"]
        self.band = test_item["band"]
        self.cellchannels = test_item["setcoexcellchannels"]
        time.sleep(WAIT_AFTER_REBOOT)
        wutils.set_wifi_country_code(self.dut, country_code='US')
        asserts.skip_if(not self.dut.droid.isSdkAtLeastS(),
                        "Require SDK at least S to use wifi coex apis.")
        self.dut.ed.clear_all_events()
        #Listing the test coex setting from configuration
        self.dut.log.info(
            "DUT test cellcoex radio:{}, band:{}, channels setting:{}".format(
                self.radio, self.band, self.cellchannels))
        self.dut.adb.shell('cmd wifi set-coex-cell-channels %s %s %s' %
                           (self.radio, self.band, self.cellchannels))
        self.dut.droid.wifiRegisterCoexCallback()
        try:
            # Wait for the immediate callback from registering and store the current values
            event = self.dut.ed.pop_event(
                "WifiManagerCoexCallback#onCoexUnsafeChannelsChanged", 5)
        except queue.Empty:
            asserts.fail("Coex callback event not received after registering.")
        prev_unsafe_channels = sorted(json.loads(
            event["data"][KEY_COEX_UNSAFE_CHANNELS]),
                                      key=self.coex_unsafe_channel_key)
        prev_restrictions = sorted(
            json.loads(event["data"][KEY_COEX_RESTRICTIONS]))
        unsafe_channels = []
        for i in range(len(prev_unsafe_channels)):
            unsafe_channels.append(prev_unsafe_channels[i]['channel'])
        self.dut.log.info("DUT unsafe channels:{}".format(unsafe_channels))
        freq1, freq2 = self.enable_softap(self.dut)
        sapchan1, sapchan2 = WifiEnums.freq_to_channel[
            freq1], WifiEnums.freq_to_channel[freq2]
        if sapchan1 in unsafe_channels or sapchan2 in unsafe_channels:
            asserts.fail(
                "devices hotspot's channel open on current unsafe channels " +
                str(unsafe_channels))
        else:
            pass
        self.dut.droid.wifiUnregisterCoexCallback()
        self.dut.adb.shell('cmd wifi reset-coex-cell-channels')

    """ Tests End """


if __name__ == "__main__":
    pass
