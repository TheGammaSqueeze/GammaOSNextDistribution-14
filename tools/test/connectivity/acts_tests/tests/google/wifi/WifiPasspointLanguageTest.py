#!/usr/bin/env python3.4
#
#   Copyright 2022 - The Android Open Source Project
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

import itertools
import time

from acts_contrib.test_utils.net import ui_utils as uutils
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest

from acts import asserts
from acts import signals
from acts.test_decorators import test_tracker_info

WifiEnums = wutils.WifiEnums

DEFAULT_TIMEOUT = 15
OSU_TEST_TIMEOUT = 300

# Constants for providers.
OSU_BOINGO = 0
BOINGO = 1

# Constants used for various device operations.

UNKNOWN_FQDN = "@#@@!00fffffx"

# Constants for Boingo UI automator
EDIT_TEXT_CLASS_NAME = "android.widget.EditText"
PASSWORD_TEXT = "Password"
PASSPOINT_BUTTON = "Get Passpoint"


class WifiPasspointLanguageTest(WifiBaseTest):
    """Tests for APIs in Android's WifiManager class.

    Test Bed Requirement:
    * One Android device
    * Several Wi-Fi networks visible to the device, including an open Wi-Fi
      network.
    """
    BOINGO_UI_TEXT = {
        'CHT': "線上註冊",
        'FRA': "Inscription en ligne",
        'US': "Online Sign Up",
        'SPA': "Registro online",
        'ARA': "الاشتراك على الإنترنت"
    }

    def setup_class(self):
        super().setup_class()
        self.dut = self.android_devices[0]
        wutils.wifi_test_device_init(self.dut)
        req_params = [
            "passpoint_networks", "boingo_username", "boingo_password",
            "osu_configs"
        ]
        self.unpack_userparams(req_param_names=req_params, )
        asserts.assert_true(
            len(self.passpoint_networks) > 0,
            "Need at least one Passpoint network.")
        wutils.wifi_toggle_state(self.dut, True)
        self.unknown_fqdn = UNKNOWN_FQDN

    def setup_test(self):
        super().setup_test()
        self.dut.droid.wakeLockAcquireBright()
        self.dut.droid.wakeUpNow()
        self.dut.unlock_screen()
        self.dut.adb.shell("input keyevent KEYCODE_HOME")

    def teardown_test(self):
        super().teardown_test()
        self.dut.droid.wakeLockRelease()
        self.dut.droid.goToSleepNow()
        passpoint_configs = self.dut.droid.getPasspointConfigs()
        for config in passpoint_configs:
            wutils.delete_passpoint(self.dut, config)
        wutils.reset_wifi(self.dut)
        self.language_change('US')

    """Helper Functions"""

    def install_passpoint_profile(self, passpoint_config):
        """Install the Passpoint network Profile.

        Args:
            passpoint_config: A JSON dict of the Passpoint configuration.

        """
        asserts.assert_true(
            WifiEnums.SSID_KEY in passpoint_config,
            "Key '%s' must be present in network definition." %
            WifiEnums.SSID_KEY)
        # Install the Passpoint profile.
        self.dut.droid.addUpdatePasspointConfig(passpoint_config)

    def check_passpoint_connection(self, passpoint_network):
        """Verify the device is automatically able to connect to the Passpoint
           network.

           Args:
               passpoint_network: SSID of the Passpoint network.

        """
        ad = self.dut
        ad.ed.clear_all_events()
        try:
            wutils.start_wifi_connection_scan_and_return_status(ad)
            wutils.wait_for_connect(ad)
        except:
            pass
        # Re-verify we are connected to the correct network.
        network_info = self.dut.droid.wifiGetConnectionInfo()
        self.log.info("Network Info: %s" % network_info)
        if not network_info or not network_info[WifiEnums.SSID_KEY] or \
            network_info[WifiEnums.SSID_KEY] not in passpoint_network:
            raise signals.TestFailure(
                "Device did not connect to passpoint network.")

    def get_configured_passpoint_and_delete(self):
        """Get configured Passpoint network and delete using its FQDN."""
        passpoint_config = self.dut.droid.getPasspointConfigs()
        if not len(passpoint_config):
            raise signals.TestFailure("Failed to fetch the list of configured"
                                      "passpoint networks.")
        if not wutils.delete_passpoint(self.dut, passpoint_config[0]):
            raise signals.TestFailure(
                "Failed to delete Passpoint configuration"
                " with FQDN = %s" % passpoint_config[0])

    def language_change(self, lang):
        """Run UI automator for boingo passpoint.

        Args:
            lang: For testing language.

        """
        langs = {
            'CHT': "zh-TW",
            'FRA': "fr-FR",
            'US': "en-US",
            'ARA': "ar-SA",
            'SPA': "es-ES"
        }
        self.dut.ed.clear_all_events()
        self.dut.adb.shell('settings put system system_locales %s ' %
                           langs[lang])
        self.dut.reboot()
        time.sleep(DEFAULT_TIMEOUT)

    def ui_automator_boingo(self, lang):
        """Changing device language.

        Args:
            lang: For testing language.

        """
        # Verify the boingo login page shows
        langtext = self.BOINGO_UI_TEXT[lang]
        asserts.assert_true(uutils.has_element(self.dut, text=langtext),
                            "Failed to launch boingohotspot login page")
        # Go to the bottom of the page
        for _ in range(3):
            self.dut.adb.shell("input swipe 300 900 300 300")
        time.sleep(5)
        screen_dump = uutils.get_screen_dump_xml(self.dut)
        nodes = screen_dump.getElementsByTagName("node")
        index = 0
        for node in nodes:
            if uutils.match_node(node, class_name="android.widget.EditText"):
                x, y = eval(node.attributes["bounds"].value.split("][")[0][1:])
                self.dut.adb.shell("input tap %s %s" % (x, y))
                time.sleep(2)
                if index == 0:
                    #stop the ime launch
                    self.dut.adb.shell(
                        "am force-stop com.google.android.inputmethod.latin")
                    self.dut.adb.shell("input text %s" % self.boingo_username)
                    index += 1
                else:
                    self.dut.adb.shell("input text %s" % self.boingo_password)
                    break
                self.dut.adb.shell("input keyevent 111")
        self.dut.adb.shell("input keyevent 111")  # collapse keyboard
        self.dut.adb.shell(
            "input swipe 300 900 300 750")  # swipe up to show text

        # Login
        uutils.wait_and_click(self.dut, text=PASSPOINT_BUTTON)
        time.sleep(DEFAULT_TIMEOUT)

    def start_subscription_provisioning_language(self, lang):
        """Start subscription provisioning with a default provider.

        Args:
            lang: For testing language.

        """
        self.language_change(lang)
        self.unpack_userparams(('osu_configs', ))
        asserts.assert_true(
            len(self.osu_configs) > 0, "Need at least one osu config.")
        osu_config = self.osu_configs[OSU_BOINGO]
        # Clear all previous events.
        self.dut.ed.clear_all_events()
        self.dut.droid.startSubscriptionProvisioning(osu_config)
        start_time = time.time()
        while time.time() < start_time + OSU_TEST_TIMEOUT:
            dut_event = self.dut.ed.pop_event("onProvisioningCallback",
                                              DEFAULT_TIMEOUT * 18)
            if dut_event['data']['tag'] == 'success':
                self.log.info("Passpoint Provisioning Success")
                break
            if dut_event['data']['tag'] == 'failure':
                raise signals.TestFailure(
                    "Passpoint Provisioning is failed with %s" %
                    dut_event['data']['reason'])
                break
            if dut_event['data']['tag'] == 'status':
                self.log.info("Passpoint Provisioning status %s" %
                              dut_event['data']['status'])
                if int(dut_event['data']['status']) == 7:
                    time.sleep(DEFAULT_TIMEOUT)
                    self.ui_automator_boingo(lang)

        # Clear all previous events.
        self.dut.ed.clear_all_events()
        # Verify device connects to the Passpoint network.
        time.sleep(DEFAULT_TIMEOUT)
        current_passpoint = self.dut.droid.wifiGetConnectionInfo()
        if current_passpoint[
                WifiEnums.SSID_KEY] not in osu_config["expected_ssids"]:
            raise signals.TestFailure("Device did not connect to the %s"
                                      " passpoint network" %
                                      osu_config["expected_ssids"])
        self.get_configured_passpoint_and_delete()
        wutils.wait_for_disconnect(self.dut, timeout=15)

    """Tests"""

    @test_tracker_info(uuid="78a939e2-bddc-4bee-8e9d-75f4d953f9eb")
    def test_passpoint_release_2_connectivity_language_ara(self):
        """Changing the device's language to ARA(Arabic)
        to connected passpoint

        """

        self.start_subscription_provisioning_language('ARA')

    @test_tracker_info(uuid="e04ac983-1fe6-436b-a2e1-339c1d73cb95")
    def test_passpoint_release_2_connectivity_language_cht(self):
        """Changing the device's language to CHT(Chinese (Traditional))
        to connected passpoint

        """

        self.start_subscription_provisioning_language('CHT')

    @test_tracker_info(uuid="215452c8-f425-48ac-a057-6f67c2e84d9e")
    def test_passpoint_release_2_connectivity_language_fra(self):
        """Changing the device's language to FRA(French)
        to connected passpoint

        """

        self.start_subscription_provisioning_language('FRA')

    @test_tracker_info(uuid="b602c998-2d42-44bc-af4a-f4ee42febe65")
    def test_passpoint_release_2_connectivity_language_spa(self):
        """Changing the device's language to SPA(Spanish)
        to connected passpoint

        """

        self.start_subscription_provisioning_language('SPA')
