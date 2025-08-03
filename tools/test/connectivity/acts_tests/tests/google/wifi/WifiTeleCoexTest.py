#!/usr/bin/env python3.4

import time

import acts.base_test
import acts_contrib.test_utils.tel.tel_wifi_utils
import acts_contrib.test_utils.wifi.wifi_test_utils as wifi_utils
import acts_contrib.test_utils.tel.tel_test_utils as tele_utils
import acts_contrib.test_utils.tel.tel_mms_utils as mms_utils
import acts.utils

from acts import asserts
from acts import signals
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_ims_utils import toggle_volte
from acts_contrib.test_utils.tel.tel_ims_utils import set_wfc_mode
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_voice_general
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_network_generation
from acts_contrib.test_utils.tel.tel_voice_utils import two_phone_call_short_seq
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_iwlan
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_idle_iwlan
from acts_contrib.test_utils.tel.tel_defines import DIRECTION_MOBILE_ORIGINATED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import GEN_4G
from acts_contrib.test_utils.tel.tel_defines import NETWORK_SERVICE_DATA
from acts_contrib.test_utils.net import net_test_utils as nutil

WifiEnums = wifi_utils.WifiEnums

ATTENUATORS = "attenuators"
WIFI_SSID = "wifi_network_ssid"
WIFI_PWD = "wifi_network_pass"
STRESS_COUNT = "stress_iteration"
DEFAULT_TIMEOUT = 10


class WifiTeleCoexTest(TelephonyBaseTest):
    """Tests for WiFi, Celular Co-existance."""

    def setup_class(self):
        TelephonyBaseTest.setup_class(self)

        self.dut = self.android_devices[0]
        wifi_utils.wifi_test_device_init(self.dut)
        # Set attenuation to 0 on all channels.
        if getattr(self, ATTENUATORS, []):
            for a in self.attenuators:
                a.set_atten(0)
        self.ads = self.android_devices
        self.dut = self.android_devices[0]
        self.wifi_network_ssid = self.user_params.get(WIFI_SSID)
        self.wifi_network_pass = self.user_params.get(WIFI_PWD)
        self.network = {
            WifiEnums.SSID_KEY: self.wifi_network_ssid,
            WifiEnums.PWD_KEY: self.wifi_network_pass
        }
        self.stress_count = self.user_params.get(STRESS_COUNT)

    def setup_test(self):
        """ Setup test make sure the DUT is wake and screen unlock"""
        for ad in self.android_devices:
            ad.droid.wakeLockAcquireBright()
            ad.droid.wakeUpNow()
            ad.droid.telephonyToggleDataConnection(True)
        wifi_utils.wifi_toggle_state(self.dut, True)

    def teardown_test(self):
        """ End test make sure the DUT return idle"""
        for ad in self.android_devices:
            wifi_utils.reset_wifi(ad)
            ad.droid.telephonyToggleDataConnection(False)
        ensure_phones_idle(self.log, self.android_devices)
        nutil.stop_usb_tethering(self.dut)

    """Helper Functions"""

    def connect_to_wifi(self, ad, network):
        """Connection logic for open and psk wifi networks.

        Args:
            ad: Android device object.
            network: A JSON dict of the WiFi network configuration.

        """
        ad.ed.clear_all_events()
        wifi_utils.start_wifi_connection_scan(ad)
        scan_results = ad.droid.wifiGetScanResults()
        wifi_utils.assert_network_in_list(
            {WifiEnums.SSID_KEY: self.wifi_network_ssid}, scan_results)
        wifi_utils.wifi_connect(ad, network)
        self.log.debug("Connected to %s network on %s device" %
                       (network[WifiEnums.SSID_KEY], ad.serial))

    def stress_toggle_wifi(self, stress_count):
        """Toggle WiFi in a loop.

        Args:
            stress_count: Number of times to toggle WiFi OFF and ON.

        """
        for count in range(stress_count):
            self.log.debug("stress_toggle_wifi: Iteration %d" % count)
            wifi_utils.toggle_wifi_off_and_on(self.dut)

        if not self.dut.droid.wifiGetisWifiEnabled():
            raise signals.TestFailure("WiFi did not turn on after toggling it"
                                      " %d times" % self.stress_count)

    def stress_toggle_airplane(self, stress_count):
        """Toggle Airplane mode in a loop.

        Args:
            stress_count: Number of times to toggle Airplane mode OFF and ON.

        """
        for count in range(stress_count):
            self.log.debug("stress_toggle_airplane: Iteration %d" % count)
            wifi_utils.toggle_airplane_mode_on_and_off(self.dut)

        if not self.dut.droid.wifiGetisWifiEnabled():
            raise signals.TestFailure("WiFi did not turn on after toggling it"
                                      " %d times" % self.stress_count)

    def stress_toggle_airplane_and_wifi(self, stress_count):
        """Toggle Airplane and WiFi modes in a loop.

        Args:
            stress_count: Number of times to perform Airplane mode ON, WiFi ON,
                          Airplane mode OFF, in a sequence.

        """
        for count in range(stress_count):
            self.log.debug("stress_toggle_airplane_and_wifi: Iteration %d" %
                           count)
            self.log.debug("Toggling Airplane mode ON")
            asserts.assert_true(
                acts.utils.force_airplane_mode(self.dut, True),
                "Can not turn on airplane mode on: %s" % self.dut.serial)
            # Sleep for atleast 500ms so that, call to enable wifi is not deferred.
            time.sleep(1)
            self.log.debug("Toggling wifi ON")
            wifi_utils.wifi_toggle_state(self.dut, True)
            # Sleep for 1s before getting new WiFi state.
            time.sleep(1)
            if not self.dut.droid.wifiGetisWifiEnabled():
                raise signals.TestFailure(
                    "WiFi did not turn on after turning ON"
                    " Airplane mode")
            asserts.assert_true(
                acts.utils.force_airplane_mode(self.dut, False),
                "Can not turn on airplane mode on: %s" % self.dut.serial)

        if not self.dut.droid.wifiGetisWifiEnabled():
            raise signals.TestFailure("WiFi did not turn on after toggling it"
                                      " %d times" % self.stress_count)

    def setup_cellular_voice_calling(self):
        """Setup phone for voice general calling and make sure phone is
           attached to voice."""
        # Make sure Phone A and B are attached to voice network.
        for ad in self.ads:
            if not phone_setup_voice_general(self.log, ad):
                raise signals.TestFailure("Phone failed to setup for voice"
                                          " calling serial:%s" % ad.serial)
        self.log.debug("Finished setting up phones for voice calling")

    def validate_cellular_and_wifi(self):
        """Validate WiFi, make some cellular calls.

        Steps:
            1. Check if device is still connected to the WiFi network.
            2. If WiFi looks good, check if deivce is attached to voice.
            3. Make a short sequence voice call between Phone A and B.

        """
        # Sleep for 30s before getting new WiFi state.
        time.sleep(30)
        wifi_info = self.dut.droid.wifiGetConnectionInfo()
        if wifi_info[WifiEnums.SSID_KEY] != self.wifi_network_ssid:
            raise signals.TestFailure(
                "Phone failed to connect to %s network on"
                " %s" % (self.wifi_network_ssid, self.dut.serial))

        # Make short call sequence between Phone A and Phone B.
        two_phone_call_short_seq(self.log, self.ads[0], None, None,
                                 self.ads[1], None, None)

    def _phone_idle_iwlan(self):
        return phone_idle_iwlan(self.log, self.android_devices[0])

    def _wfc_phone_setup_apm_wifi_preferred(self):
        return self._wfc_phone_setup(True, WFC_MODE_WIFI_PREFERRED)

    def _wfc_phone_setup(self, is_airplane_mode, wfc_mode, volte_mode=True):
        """Enables WiFi calling by turning off Airplane Mode and setting up volte

        Args:
          is_airplane_mode: boolean, True/False to turn on/off Airplane Mode.
          wfc_mode: str, String stating what WFC Mode is used.
          volte_mode: boolean, True/False to turn on/off VoLTE Mode.

        Returns:
          False, when 4G fails or wrong wfc_mode or WiFi does not connect,
          (failure is logged) True otherwise.

        """
        tele_utils.toggle_airplane_mode(self.log, self.android_devices[0],
                                        False)
        toggle_volte(self.log, self.android_devices[0], volte_mode)
        if not ensure_network_generation(self.log,
                                         self.android_devices[0],
                                         GEN_4G,
                                         voice_or_data=NETWORK_SERVICE_DATA):
            return False
        if not set_wfc_mode(self.log, self.android_devices[0], wfc_mode):
            self.log.error("{} set WFC mode failed.".format(
                self.android_devices[0].serial))
            return False
        tele_utils.toggle_airplane_mode(self.log, self.android_devices[0],
                                        is_airplane_mode)
        if not tel_wifi_utils.ensure_wifi_connected(
                self.log, self.android_devices[0], self.wifi_network_ssid,
                self.wifi_network_pass):
            self.log.error("{} connect WiFI failed".format(
                self.android_devices[0].serial))
            return False
        return True

    """Tests"""

    @test_tracker_info(uuid="8b9b6fb9-964b-43e7-b75f-675774ee346f")
    @TelephonyBaseTest.tel_test_wrap
    def test_toggle_wifi_call(self):
        """Test to toggle WiFi and then perform WiFi connection and
           cellular calls.

        Steps:
            1. Attach device to voice subscription network.
            2. Connect to a WiFi network.
            3. Toggle WiFi OFF and ON.
            4. Verify device auto-connects to the WiFi network.
            5. Verify device is attached to voice network.
            6. Make short sequence voice calls.

        """
        self.setup_cellular_voice_calling()
        self.connect_to_wifi(self.dut, self.network)
        wifi_utils.toggle_wifi_off_and_on(self.dut)
        self.validate_cellular_and_wifi()
        return True

    @test_tracker_info(uuid="caf22447-6354-4a2e-99e5-0ff235fc8f20")
    @TelephonyBaseTest.tel_test_wrap
    def test_toggle_airplane_call(self):
        """Test to toggle Airplane mode and perform WiFi connection and
           cellular calls.

        Steps:
            1. Attach device to voice subscription network.
            2. Connect to a WiFi network.
            3. Toggle Airplane mode OFF and ON.
            4. Verify device auto-connects to the WiFi network.
            5. Verify device is attached to voice network.
            6. Make short sequence voice calls.

        """
        self.setup_cellular_voice_calling()
        self.connect_to_wifi(self.dut, self.network)
        wifi_utils.toggle_airplane_mode_on_and_off(self.dut)
        self.validate_cellular_and_wifi()
        return True

    @test_tracker_info(uuid="dd888b35-f820-409a-89af-4b0f6551e4d6")
    @TelephonyBaseTest.tel_test_wrap
    def test_toggle_airplane_and_wifi_call(self):
        """Test to toggle WiFi in a loop and perform WiFi connection and
           cellular calls.

        Steps:
            1. Attach device to voice subscription network.
            2. Connect to a WiFi network.
            3. Toggle Airplane mode ON.
            4. Turn WiFi ON.
            5. Toggle Airplane mode OFF.
            3. Verify device auto-connects to the WiFi network.
            4. Verify device is attached to voice network.
            5. Make short sequence voice calls.

        """
        self.setup_cellular_voice_calling()
        self.connect_to_wifi(self.dut, self.network)
        self.stress_toggle_airplane_and_wifi(1)
        self.validate_cellular_and_wifi()
        return True

    @test_tracker_info(uuid="15db5b7e-827e-4bc8-8e77-7fcce343a323")
    @TelephonyBaseTest.tel_test_wrap
    def test_stress_toggle_wifi_call(self):
        """Stress test to toggle WiFi in a loop, then perform WiFi connection
           and cellular calls.

        Steps:
            1. Attach device to voice subscription network.
            2. Connect to a WiFi network.
            3. Toggle WiFi OFF and ON in a loop.
            4. Verify device auto-connects to the WiFi network.
            5. Verify device is attached to voice network.
            6. Make short sequence voice calls.

        """
        self.setup_cellular_voice_calling()
        self.connect_to_wifi(self.dut, self.network)
        self.stress_toggle_wifi(self.stress_count)
        self.validate_cellular_and_wifi()
        return True

    @test_tracker_info(uuid="80a2f1bf-5e41-453a-9b8e-be3b41d4d313")
    @TelephonyBaseTest.tel_test_wrap
    def test_stress_toggle_airplane_call(self):
        """Stress test to toggle Airplane mode in a loop, then perform WiFi and
           cellular calls.

        Steps:
            1. Attach device to voice subscription network.
            2. Connect to a WiFi network.
            3. Toggle Airplane mode OFF and ON in a loop.
            4. Verify device auto-connects to the WiFi network.
            5. Verify device is attached to voice network.
            6. Make short sequence voice calls.

        """
        self.setup_cellular_voice_calling()
        self.connect_to_wifi(self.dut, self.network)
        self.stress_toggle_airplane(self.stress_count)
        self.validate_cellular_and_wifi()
        return True

    @test_tracker_info(uuid="b88ad3e7-6462-4280-ad57-22d0ac91fdd8")
    @TelephonyBaseTest.tel_test_wrap
    def test_stress_toggle_airplane_and_wifi_call(self):
        """Stress test to toggle Airplane and WiFi mode in a loop, then perform
           WiFi connection and cellular calls.

        Steps:
            1. Attach device to voice subscription network.
            2. Connect to a WiFi network.
            3. Toggle Airplane mode ON.
            4. Turn WiFi ON.
            5. Toggle Airplane mode OFF.
            6. Repeat 3, 4 & 5, in a loop.
            7. Verify device auto-connects to the WiFi network.
            8. Verify device is attached to voice network.
            9. Make short sequence voice calls.

        """
        self.setup_cellular_voice_calling()
        self.connect_to_wifi(self.dut, self.network)
        self.stress_toggle_airplane_and_wifi(self.stress_count)
        self.validate_cellular_and_wifi()
        return True

    @test_tracker_info(uuid="7cd9698c-7cde-4c99-b73a-67a2246ca4ec")
    @TelephonyBaseTest.tel_test_wrap
    def test_toggle_WFC_call(self):
        """Test to toggle WiFi and then perform WiFi connection and
           cellular calls.

        Raises:
          signals.TestFailure:The Wifi calling test is failed.

        Steps:
            1. Attach device to voice subscription network.
            2. Connect to a WiFi network.
            3. Turn on airplane mode
            4. Toggle WiFi OFF and ON.
            5. Make WiFi calling
            6. Verify device is in WiFi calling
            5. Hang up the call

        Verification:
            The device is using WiFi calling to call out.

        """
        mo_mt = []
        if mo_mt == DIRECTION_MOBILE_ORIGINATED:
            ad_caller = self.ads[0]
            ad_callee = self.ads[1]
        else:
            ad_caller = self.ads[1]
            ad_callee = self.ads[0]
        caller_number = tele_utils.get_phone_number(self.log, ad_caller)
        callee_number = tele_utils.get_phone_number(self.log, ad_callee)
        self._wfc_phone_setup_apm_wifi_preferred()

        self.connect_to_wifi(self.dut, self.network)
        asserts.assert_true(
            acts.utils.force_airplane_mode(self.dut, True),
            "Can not turn on airplane mode on: %s" % self.dut.serial)
        time.sleep(1)
        self.log.info("Toggling wifi ON")
        wifi_utils.wifi_toggle_state(self.dut, True)
        time.sleep(10)
        tele_utils.initiate_call(self.log, ad_caller, callee_number)
        tele_utils.wait_and_answer_call(self.log, ad_callee, caller_number)
        if not self._phone_idle_iwlan():
            self.log.error("no in wifi calling")
            raise signals.TestFailure("The Wifi calling test is failed."
                                      "WiFi State = %d" %
                                      self.dut.droid.wifiCheckState())
        tele_utils.hangup_call(self.log, self.ads[0])

    @test_tracker_info(uuid="c1f0e0a7-b651-4d6c-a4a5-f946cabf56ef")
    @TelephonyBaseTest.tel_test_wrap
    def test_back_to_back_of_the_modem_restart(self):
        """Make sure DUT can connect to AP after modem restart

        Raises:
          signals.TestFailure: The Wifi connect failed after modem restart.

        From b/171275893:
          b/170702695 has modem back to back restart,
          it causes WIFi failure and only reboot device can recover.
          Currently modem team has this test case but they only check modem status.
          Therefore, we need to add the same test case and the criteria is to check
          if WiFi works well after back to back modem restart.
          For the interval setting between 2 restarts, we suggest to use 20s.
          We can change to different interval if necessary.

        Steps:
            1.Restart the modem once
            2.Waiting for 20s
            3.Restart the modem again
            4.Go to Settings ->Network & internet ->Wi-Fi
            5.To check the DUT can find WiFi AP then connect it
        Verification:
            DUT can connect to AP successfully
            DUT can access the Internet after connect to AP.

        """
        self.dut = self.android_devices[0]
        try:
            tele_utils.trigger_modem_crash_by_modem(self.dut)
            time.sleep(20)
            tele_utils.trigger_modem_crash_by_modem(self.dut)
            time.sleep(20)
            self.connect_to_wifi(self.dut, self.network)
        except:
            raise signals.TestFailure(
                "The Wifi connect failed after modem restart."
                "WiFi State = %d" % self.dut.droid.wifiCheckState())

    @test_tracker_info(uuid="a72ff9da-3855-4c21-b447-b80f43227961")
    @TelephonyBaseTest.tel_test_wrap
    def test_internet_accessing_over_wifi_and_mms_test(self):
        """Verify when MMS is working WLAN connection can work normally as well.

        Raises:
          signals.TestFailure: Internet_connection is stop

        Steps:
            1. Connect to WiFi
            2. Prepare two DUT for testing (DUT1 and DUT2)
            3. Send 5 MMS from DUT1 to DUT2

        Verification:
            The internet cannot be impacted by MMS delivery.
            MMS can be sent / received successfully and content is correct.
        """
        self.dut1 = self.android_devices[0]
        self.dut2 = self.android_devices[1]
        self.connect_to_wifi(self.dut1, self.network)
        wifi_utils.wifi_toggle_state(self.dut2, True)
        self.connect_to_wifi(self.dut2, self.network)
        mms = 5
        for count in range(mms):
            mms_utils._mms_test(self.log, self.ads)
            if not tele_utils.verify_internet_connection(
                    self.dut2.log, self.dut2):
                raise signals.TestFailure("The internet connection is stop."
                                          "Current WiFi state is %d" %
                                          self.dut2.droid.wifiCheckState())
            time.sleep(30)

    @test_tracker_info(uuid="a7d774e4-ead3-465c-b4a6-f39a6397dfe3")
    @TelephonyBaseTest.tel_test_wrap
    def test_internet_accessing_wifi_and_data_test(self):
        """Verify interwork between Wi-Fi and data.

        Raises:
          signals.TestFailure: Internet_connection is stop for WiFi off or
          Data off

        Steps:
            1. Connect to WiFi
            2. To Wi-Fi and data switched for 5 times,
            3. DUT is kept awake during testing.
        Verification:
            The internet cannot be impacted after data path switched

        """
        self.dut = self.android_devices[0]
        self.connect_to_wifi(self.dut, self.network)
        data_count = 5
        for count in range(data_count):
            wifi_utils.wifi_toggle_state(self.dut, False)
            time.sleep(60)
            if not tele_utils.verify_internet_connection(
                    self.dut.log, self.dut):
                raise signals.TestFailure(
                    "The internet connection is stop"
                    "for WiFi off. Current WiFi state is %d" %
                    self.dut.droid.wifiCheckState())
            wifi_utils.wifi_toggle_state(self.dut, True)
            time.sleep(DEFAULT_TIMEOUT)
            self.dut.log.info("DUT data is disable")
            self.dut.droid.telephonyToggleDataConnection(False)
            time.sleep(30)
            if not tele_utils.verify_internet_connection(
                    self.dut.log, self.dut):
                raise signals.TestFailure(
                    "The internet connection is stop"
                    "for Data off. Current WiFi state is %d" %
                    self.dut.droid.wifiCheckState())
            self.dut.log.info("DUT data is enable")
            self.dut.droid.telephonyToggleDataConnection(True)
            time.sleep(DEFAULT_TIMEOUT)

    @test_tracker_info(uuid="e53adef6-d537-4098-a354-1e63457ab444")
    @TelephonyBaseTest.tel_test_wrap
    def test_internet_accessing_wifi_and_usb_tethering(self):
        """Verify interwork between Wi-Fi and USB_TETHERED.

        Raises:
          signals.TestFailure: Internet_connection is stop for enable or
          disable USB tethering

        Steps:
            1.Connect to WiFi
            2. enable/disable USB tethering for 5 cycles,

        Verification:
            The Internet cannot be impacted after enable/disable USB tethering

        """
        self.dut = self.android_devices[0]
        nutil.verify_lte_data_and_tethering_supported(self.dut)
        self.connect_to_wifi(self.dut, self.network)
        usb_count = 5
        for count in range(usb_count):
            time.sleep(DEFAULT_TIMEOUT)
            nutil.start_usb_tethering(self.dut)
            time.sleep(DEFAULT_TIMEOUT)
            if not tele_utils.verify_internet_connection(
                    self.dut.log, self.dut):
                raise signals.TestFailure(
                    "The internet connection is stop"
                    "for tethering enable. Current WiFi state is %d" %
                    self.dut.droid.wifiCheckState())
            nutil.stop_usb_tethering(self.dut)
            time.sleep(DEFAULT_TIMEOUT)
            if not tele_utils.verify_internet_connection(
                    self.dut.log, self.dut):
                raise signals.TestFailure(
                    "The internet connection is stop"
                    "for tethering disable. Current WiFi state is %d" %
                    self.dut.droid.wifiCheckState())
