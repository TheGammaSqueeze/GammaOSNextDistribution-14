#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
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
from acts.controllers.openwrt_lib.openwrt_constants import OpenWrtWifiSecurity
from acts.test_decorators import test_tracker_info
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest


WifiEnums = wutils.WifiEnums


class WifiWpaPersonalTest(WifiBaseTest):
  """Test for WPA Personal.

  Test Bed Requirement:
    * One Android device without sim card.
    * One OpenWrt Wi-Fi AP.
  """

  def setup_class(self):
    super().setup_class()
    self.dut = self.android_devices[0]

    if "OpenWrtAP" in self.user_params:
      self.openwrt = self.access_points[0]
      self.configure_openwrt_ap_and_start(wpa1_network=True)

    req_params = ["OpenWrtAP", "roaming_attn"]
    opt_params = []
    self.unpack_userparams(req_params, opt_params)
    self.wpa_psk_2g = self.wpa1_networks[0]["2g"]
    self.wpa_psk_5g = self.wpa1_networks[0]["5g"]

  def setup_test(self):
    super().setup_test()
    for ad in self.android_devices:
      ad.droid.wakeLockAcquireBright()
      ad.droid.wakeUpNow()
      wutils.wifi_toggle_state(ad, True)

  def teardown_test(self):
    super().teardown_test()
    for ad in self.android_devices:
      ad.droid.wakeLockRelease()
      ad.droid.goToSleepNow()
    wutils.reset_wifi(self.dut)
    utils.force_airplane_mode(self.dut, False)

  def teardown_class(self):
    super().teardown_class()

  def on_fail(self, test_name, begin_time):
    super().on_fail(test_name, begin_time)
    self.dut.cat_adb_log(test_name, begin_time)
    self.dut.take_bug_report(test_name, begin_time)

  def verify_wpa_network_encryption(self, encryption):
    result = wutils.get_wlan0_link(self.dut)
    if encryption == "psk+ccmp":
      asserts.assert_true(
          result["pairwise_cipher"] == "CCMP" and
          result["group_cipher"] == "CCMP",
          "DUT does not connect to {} encryption network".format(encryption))
    elif encryption == "psk+tkip":
      asserts.assert_true(
          result["pairwise_cipher"] == "TKIP" and
          result["group_cipher"] == "TKIP",
          "DUT does not connect to {} encryption network".format(encryption))
    elif encryption == "psk+tkip+ccmp":
      asserts.assert_true(
          result["pairwise_cipher"] == "CCMP" and
          result["group_cipher"] == "TKIP",
          "DUT does not connect to {} encryption network".format(encryption))

  ### Tests ###

  @test_tracker_info(uuid="0c68a772-b70c-47d6-88ab-1b069c1d8005")
  def test_connect_to_wpa_psk_ccmp_2g(self):
    """Test connection between DUT and WPA PSK CCMP 2G.

    Steps:
      Change AP's security type to "WPA" and cipher to "CCMP".
      Connect to 2g network.
    """
    self.openwrt.log.info("Enable WPA-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa_psk_2g[WifiEnums.SSID_KEY])
    wutils.connect_to_wifi_network(self.dut, self.wpa_psk_2g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)

  @test_tracker_info(uuid="4722dffc-2960-4459-9729-0f8114af2321")
  def test_connect_to_wpa_psk_ccmp_5g(self):
    """Test connection between DUT and WPA PSK CCMP 5G.

    Steps:
      Change AP's security type to "WPA" and cipher to "CCMP".
      Connect to 5g network.
    """
    self.openwrt.log.info("Enable WPA-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa_psk_5g[WifiEnums.SSID_KEY])
    wutils.connect_to_wifi_network(self.dut, self.wpa_psk_5g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)

  @test_tracker_info(uuid="4759503e-ef9c-430b-9306-b96a347ca3de")
  def test_connect_to_wpa_psk_tkip_2g(self):
    """Test connection between DUT and WPA PSK TKIP 2G.

    Steps:
      Change AP's security type to "WPA" and cipher to "TKIP".
      Connect to 2g network.
    """
    self.openwrt.log.info("Enable WPA-TKIP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA_PSK_TKIP)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa_psk_2g[WifiEnums.SSID_KEY])
    wutils.connect_to_wifi_network(self.dut, self.wpa_psk_2g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA_PSK_TKIP)

  @test_tracker_info(uuid="9c836ca6-af14-4d6b-a98e-227fb29e84ee")
  def test_connect_to_wpa_psk_tkip_5g(self):
    """Test connection between DUT and WPA PSK TKIP 5G.

    Steps:
      Change AP's security type to "WPA" and cipher to "TKIP".
      Connect to 5g network.
    """
    self.openwrt.log.info("Enable WPA-PSK TKIP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA_PSK_TKIP)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa_psk_5g[WifiEnums.SSID_KEY])
    wutils.connect_to_wifi_network(self.dut, self.wpa_psk_5g)

  @test_tracker_info(uuid="c03b362b-cd03-4e34-a99a-ef80a9db6db9")
  def test_connect_to_wpa_psk_tkip_and_ccmp_2g(self):
    """Test connection between DUT and WPA PSK CCMP+TKIP 2G.

    Steps:
      Change AP's security type to "WPA" and cipher to "CCMP and TKIP".
      Connect to 2g network.
    """
    self.openwrt.log.info("Enable WPA-PSK CCMP and TKIP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA_PSK_TKIP_AND_CCMP)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa_psk_2g[WifiEnums.SSID_KEY])
    wutils.connect_to_wifi_network(self.dut, self.wpa_psk_2g)
    self.verify_wpa_network_encryption(
        OpenWrtWifiSecurity.WPA_PSK_TKIP_AND_CCMP)

  @test_tracker_info(uuid="203d7e7f-536d-4feb-9aa2-648f1f9a685d")
  def test_connect_to_wpa_psk_tkip_and_ccmp_5g(self):
    """Test connection between DUT and WPA PSK CCMP+TKIP 5G.

    Steps:
        Change AP's security type to "WPA" and cipher to "CCMP and TKIP".
        Connect to 5g network.
    """
    self.openwrt.log.info("Enable WPA-PSK CCMP and TKIP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA_PSK_TKIP_AND_CCMP)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa_psk_5g[WifiEnums.SSID_KEY])
    wutils.connect_to_wifi_network(self.dut, self.wpa_psk_5g)
    self.verify_wpa_network_encryption(
        OpenWrtWifiSecurity.WPA_PSK_TKIP_AND_CCMP)

  @test_tracker_info(uuid="20a41f61-4fda-4fe9-82ee-482ecd8c82eb")
  def test_connect_to_wpa_psk_ccmp_2g_after_airplane_mode(self):
    """Test Wi-Fi reconnection after enabling Airplane Mode.

    Steps:
        DUT connect to 2GHz Wi-Fi network.
        DUT turns ON Airplane Mode.
        DUT turns ON Wi-Fi.
        DUT verify internet connection with HTTP ping.
        DUT turns OFF Airplane Mode.
        DUT verify internet connection with HTTP ping.
    """
    self.openwrt.log.info("Enable WPA-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa_psk_2g[WifiEnums.SSID_KEY])
    wutils.connect_to_wifi_network(self.dut, self.wpa_psk_2g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)
    # Turn ON DUT"s Airplane Mode.
    self.dut.log.info("Toggle Airplane Mode ON")
    utils.force_airplane_mode(self.dut, True)
    self.dut.log.info("Toggle Wi-Fi ON")
    # Turn ON DUT"s Wi-Fi
    wutils.wifi_toggle_state(self.dut, True)
    wutils.wait_for_connect(self.dut,
                            self.wpa_psk_2g[WifiEnums.SSID_KEY])
    wutils.validate_connection(self.dut)
    utils.force_airplane_mode(self.dut, False)
    wutils.validate_connection(self.dut)

  @test_tracker_info(uuid="df89c92b-a30c-4485-ab45-daef5240c027")
  def test_connect_to_wpa_psk_ccmp_2g_after_wifi_off(self):
    """Test Wi-Fi reconnection after Turn OFF Wi-Fi.

    Steps:
        DUT connect to 2GHz Wi-Fi network.
        DUT turns OFF Wi-Fi.
        DUT turns ON Wi-Fi.
        DUT verify internet connection with HTTP ping.
    """
    self.openwrt.log.info("Enable WPA-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa_psk_2g[WifiEnums.SSID_KEY])
    wutils.connect_to_wifi_network(self.dut, self.wpa_psk_2g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)
    self.dut.log.info("Toggle Wi-Fi OFF")
    # Turn OFF DUT"s Wi-Fi then Turn if ON.
    wutils.wifi_toggle_state(self.dut, False)
    wutils.wifi_toggle_state(self.dut, True)
    wutils.wait_for_connect(self.dut,
                            self.wpa_psk_2g[WifiEnums.SSID_KEY])
    wutils.validate_connection(self.dut)

  @test_tracker_info(uuid="c591e687-340c-42e6-8d85-58a1f930b6b1")
  def test_connect_to_wpa_psk_ccmp_2g_after_suspend_resume(self):
    """Test Wi-Fi reconnection after Suspend.

    Steps:
        DUT connect to 2GHz Wi-Fi network.
        DUT suspend and resume.
        DUT verify internet connection with HTTP ping.
    """
    self.openwrt.log.info("Enable WPA-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa_psk_2g[WifiEnums.SSID_KEY])
    wutils.connect_to_wifi_network(self.dut, self.wpa_psk_2g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)
    self.dut.log.info("Suspend the DUT and wait for 10 seconds")
    # Suspend and Resume the DUT.
    self.dut.go_to_sleep()
    time.sleep(10)
    self.dut.log.info("Resume the DUT")
    self.dut.wakeup_screen()
    wutils.validate_connection(self.dut)

  @test_tracker_info(uuid="d3e34869-f2ae-4614-983d-19be238d8499")
  def test_connect_to_wpa_psk_ccmp_2g_after_reboot(self):
    """Test Wi-Fi reconnection after reboot.

    Steps:
        DUT connect to 2GHz Wi-Fi network.
        DUT reboot.
        DUT verify internet connection with HTTP ping.
    """
    self.openwrt.log.info("Enable WPA-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa_psk_2g[WifiEnums.SSID_KEY])
    wutils.connect_to_wifi_network(self.dut, self.wpa_psk_2g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)
    # Reboot the DUT.
    self.dut.log.info("Reboot the DUT")
    self.dut.reboot()
    self.dut.wait_for_boot_completion()
    wutils.wait_for_connect(self.dut,
                            self.wpa_psk_2g[WifiEnums.SSID_KEY])
    wutils.validate_connection(self.dut)

  @test_tracker_info(uuid="ebd8dd7f-dc36-4e99-b18c-5f725a2f88b2")
  def test_connect_to_wpa_psk_ccmp_2g_after_incorrect_password(self):
    """Test Wi-Fi reconnection after incorrect password.

    Steps:
        DUT connect to 2GHz Wi-Fi network.
        DUT try to connect to the Wi-Fi network with incorrect password.
        Connection fail as expected.
        DUT connect to the Wi-Fi network with correct password.
        DUT verify internet connection with HTTP ping.
    """
    self.openwrt.log.info("Enable WPA-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa_psk_2g[WifiEnums.SSID_KEY])
    self.wpa_psk_2g_fail = self.wpa_psk_2g.copy()
    self.wpa_psk_2g_fail["password"] = "incorrect_password"
    # Try to connect a Wi-Fi network with incorrect passwlrd.
    try:
      self.dut.log.info("Connect to Wi-Fi with wrong password")
      wutils.wifi_connect(self.dut, self.wpa_psk_2g_fail, num_of_tries=1)
    except:
      self.dut.log.info("Connect to Wi-Fi with correct password")
      wutils.wifi_connect(self.dut, self.wpa_psk_2g)
    else:
      raise signals.TestFailure("DUT connect to Wi-Fi with wrong password")
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)
    wutils.validate_connection(self.dut)

  @test_tracker_info(uuid="d20fc634-8dcc-4336-9640-2a6907ca1894")
  def test_connect_to_wpa_psk_ccmp_2g_after_out_of_range(self):
    """Test Wi-Fi reconnection after out of range.

    Steps:
        DUT connect to 2GHz Wi-Fi network.
        DUT out of Wi-Fi range.
        Make Wi-Fi network is not visible by DUT.
        DUT back in Wi-Fi range.
        Wi-Fi network is visible by DUT.
        DUT connect to the Wi-Fi network.
        DUT verify internet connection with HTTP ping.
    """
    self.openwrt.log.info("Enable WPA-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA_PSK_CCMP)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa_psk_2g[WifiEnums.SSID_KEY])
    wutils.connect_to_wifi_network(self.dut, self.wpa_psk_2g)
    # Make the DUT out of range.
    wutils.set_attns(self.attenuators,
                     "atten1_off_atten2_off",
                     self.roaming_attn)
    wutils.start_wifi_connection_scan_and_ensure_network_not_found(
        self.dut,
        self.wpa_psk_2g[WifiEnums.SSID_KEY])
    # Make the DUT back in range.
    wutils.set_attns(self.attenuators,
                     "atten1_on_atten2_on",
                     self.roaming_attn)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa_psk_2g[WifiEnums.SSID_KEY])
    wutils.connect_to_wifi_network(self.dut, self.wpa_psk_2g)
