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
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts.controllers.ap_lib import hostapd_constants
from acts.controllers.openwrt_lib.openwrt_constants import OpenWrtWifiSecurity
from acts.test_decorators import test_tracker_info
from acts import asserts
from acts import utils
from acts import signals


WifiEnums = wutils.WifiEnums


class WifiWpa2PersonalTest(WifiBaseTest):
  """ Wi-Fi WPA2 test

      Test Bed Requirement:
        * One Android device
        * One OpenWrt Wi-Fi AP.
  """
  def __init__(self, configs):
    super().__init__(configs)
    self.enable_packet_log = True

  def setup_class(self):
    super().setup_class()
    self.dut = self.android_devices[0]
    req_params = ["roaming_attn"]
    opt_params = []
    self.unpack_userparams(req_params, opt_params)

  def setup_test(self):
    super().setup_test()
    for ad in self.android_devices:
      ad.droid.wakeLockAcquireBright()
      ad.droid.wakeUpNow()
      wutils.wifi_toggle_state(ad, True)
    wutils.reset_wifi(self.dut)

  def teardown_test(self):
    super().teardown_test()
    for ad in self.android_devices:
      ad.droid.wakeLockRelease()
      ad.droid.goToSleepNow()
    wutils.reset_wifi(self.dut)

  def start_openwrt(self, channel_2g=None, channel_5g=None):
    """Enable one OpenWrt to generate a Wi-Fi network.

      Args:
        channel_2g: Optional; An integer to represent a Wi-Fi 2g channel.
          The default value is 6 if it's not given.
        channel_5g: Optional; An integer to represent a Wi-Fi 5g channel.
          The default value is 36 if it's not given.
  """
    if not channel_2g:
      channel_2g = hostapd_constants.AP_DEFAULT_CHANNEL_2G
    if not channel_5g:
      channel_5g = hostapd_constants.AP_DEFAULT_CHANNEL_5G
    if "OpenWrtAP" in self.user_params:
      self.openwrt = self.access_points[0]
      self.configure_openwrt_ap_and_start(wpa_network=True,
                                          channel_2g=channel_2g,
                                          channel_5g=channel_5g)
      self.wpa2_psk_2g = self.wpa_networks[0]["2g"]
      self.wpa2_psk_5g = self.wpa_networks[0]["5g"]

  def verify_wpa_network_encryption(self, encryption):
    result = wutils.get_wlan0_link(self.dut)
    if encryption == 'psk2+ccmp':
      asserts.assert_true(
          result['pairwise_cipher'] == 'CCMP' and
          result['group_cipher'] == 'CCMP' and
          result['key_mgmt'] == "WPA2-PSK",
          'DUT does not connect to {} encryption network'.format(encryption))
    elif encryption == 'psk2+tkip':
      asserts.assert_true(
          result['pairwise_cipher'] == 'TKIP' and
          result['group_cipher'] == 'TKIP' and
          result['key_mgmt'] == "WPA2-PSK",
          'DUT does not connect to {} encryption network'.format(encryption))
    elif encryption == 'psk2+tkip+ccmp':
      asserts.assert_true(
          result['pairwise_cipher'] == 'CCMP' and
          result['group_cipher'] == 'TKIP' and
          result['key_mgmt'] == "WPA2-PSK",
          'DUT does not connect to {} encryption network'.format(encryption))

  """ Tests"""

  @test_tracker_info(uuid="d1f984c9-d85f-4b0d-8d64-2e8d6ce74c48")
  def test_connect_to_wpa2_psk_ccmp_2g(self):
    """Generate a Wi-Fi network.
       Change AP's security type to "WPA2" and cipher to "CCMP".
       Connect to 2g network.
    """
    self.start_openwrt()
    self.openwrt.log.info("Enable WPA2-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)
    wutils.connect_to_wifi_network(self.dut, self.wpa2_psk_2g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)

  @test_tracker_info(uuid="0f9631e8-04a9-4b9c-8225-ab30b4d1173b")
  def test_connect_to_wpa2_psk_ccmp_5g(self):
    """Generate a Wi-Fi network.
       Change AP's security type to "WPA2" and cipher to "CCMP".
       Connect to 5g network.
    """
    self.start_openwrt()
    self.openwrt.log.info("Enable WPA2-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)
    wutils.connect_to_wifi_network(self.dut, self.wpa2_psk_5g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)

  @test_tracker_info(uuid="e6eb3932-10cc-476f-a5d7-936e2631afc1")
  def test_connect_to_wpa2_psk_tkip_2g(self):
    """Generate a Wi-Fi network.
       Change AP's security type to "WPA2" and cipher to "TKIP".
       Connect to 2g network.
    """
    self.start_openwrt()
    self.openwrt.log.info("Enable WPA2-PSK TKIP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA2_PSK_TKIP)
    wutils.connect_to_wifi_network(self.dut, self.wpa2_psk_2g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA2_PSK_TKIP)

  @test_tracker_info(uuid="59ba3cd4-dbc5-44f9-9290-48ae468a51da")
  def test_connect_to_wpa2_psk_tkip_5g(self):
    """Generate a Wi-Fi network.
       Change AP's security type to "WPA2" and cipher to "TKIP".
       Connect to 5g network.
    """
    self.start_openwrt()
    self.openwrt.log.info("Enable WPA2-PSK TKIP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA2_PSK_TKIP)
    wutils.connect_to_wifi_network(self.dut, self.wpa2_psk_5g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA2_PSK_TKIP)

  @test_tracker_info(uuid="a06be3db-d653-4549-95f3-87bbeb0db813")
  def test_connect_to_wpa2_psk_tkip_and_ccmp_2g(self):
    """Generate a Wi-Fi network.
       Change AP's security type to "WPA2" and cipher to "CCMP and TKIP".
       Connect to 2g network.
    """
    self.start_openwrt()
    self.openwrt.log.info("Enable WPA2-PSK CCMP and TKIP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA2_PSK_TKIP_AND_CCMP)
    wutils.connect_to_wifi_network(self.dut, self.wpa2_psk_2g)
    self.verify_wpa_network_encryption(
        OpenWrtWifiSecurity.WPA2_PSK_TKIP_AND_CCMP)

  @test_tracker_info(uuid="ac9b9581-0b32-42b4-8e76-de702c837b86")
  def test_connect_to_wpa2_psk_tkip_and_ccmp_5g(self):
    """Generate a Wi-Fi network.
       Change AP's security type to "WPA2" and cipher to "CCMP and TKIP".
       Connect to 5g network.
    """
    self.start_openwrt()
    self.openwrt.log.info("Enable WPA2-PSK CCMP and TKIP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA2_PSK_TKIP_AND_CCMP)
    wutils.connect_to_wifi_network(self.dut, self.wpa2_psk_5g)
    self.verify_wpa_network_encryption(
        OpenWrtWifiSecurity.WPA2_PSK_TKIP_AND_CCMP)

  @test_tracker_info(uuid="4e8286a0-1b7c-4186-9d86-1cc5cd7a6be2")
  def test_connect_to_wpa2_psk_ccmp_2g_after_airplane_mode(self):
    """Test Wi-Fi reconnection after enabling Airplane Mode.

    Steps:
        DUT connect to 2GHz Wi-Fi network.
        DUT turns ON Airplane Mode.
        DUT turns ON Wi-Fi.
        DUT verify internet connection with HTTP ping.
        DUT turns OFF Airplane Mode.
        DUT verify internet connection with HTTP ping.
    """
    self.start_openwrt()
    self.openwrt.log.info("Enable WPA2-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)
    wutils.connect_to_wifi_network(self.dut, self.wpa2_psk_2g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)
    # Turn ON DUT's Airplane Mode.
    self.dut.log.info("Toggle Airplane Mode ON")
    utils.force_airplane_mode(self.dut, True)
    self.dut.log.info("Toggle Wi-Fi ON")
    # Turn ON DUT's Wi-Fi
    wutils.wifi_toggle_state(self.dut, True)
    wutils.wait_for_connect(self.dut,
                            self.wpa2_psk_2g[WifiEnums.SSID_KEY])
    wutils.validate_connection(self.dut)
    utils.force_airplane_mode(self.dut, False)
    wutils.validate_connection(self.dut)

  @test_tracker_info(uuid="091c9b6a-d729-41d0-85e7-acf777aa3d1f")
  def test_connect_to_wpa2_psk_ccmp_2g_after_wifi_off(self):
    """Test Wi-Fi reconnection after Turn OFF Wi-Fi.

    Steps:
        DUT connect to 2GHz Wi-Fi network.
        DUT turns OFF Wi-Fi.
        DUT turns ON Wi-Fi.
        DUT verify internet connection with HTTP ping.
    """
    self.start_openwrt()
    self.openwrt.log.info("Enable WPA2-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)
    wutils.connect_to_wifi_network(self.dut, self.wpa2_psk_2g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)
    self.dut.log.info("Toggle Wi-Fi OFF")
    # Turn OFF DUT's Wi-Fi then Turn if ON.
    wutils.wifi_toggle_state(self.dut, False)
    wutils.wifi_toggle_state(self.dut, True)
    wutils.wait_for_connect(self.dut,
                            self.wpa2_psk_2g[WifiEnums.SSID_KEY])
    wutils.validate_connection(self.dut)

  @test_tracker_info(uuid="9eb74878-1527-4c5b-980e-1a9305f601aa")
  def test_connect_to_wpa2_psk_ccmp_2g_after_suspend_resume(self):
    """Test Wi-Fi reconnection after Suspend.

    Steps:
        DUT connect to 2GHz Wi-Fi network.
        DUT suspend and resume.
        DUT verify internet connection with HTTP ping.
    """
    self.start_openwrt()
    self.openwrt.log.info("Enable WPA2-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)
    wutils.connect_to_wifi_network(self.dut, self.wpa2_psk_2g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)
    self.dut.log.info("Suspend the DUT and wait for 10 seconds")
    # Suspend and Resume the DUT.
    self.dut.go_to_sleep()
    time.sleep(10)
    self.dut.log.info("Resume the DUT")
    self.dut.wakeup_screen()
    wutils.validate_connection(self.dut)

  @test_tracker_info(uuid="44e5d946-620a-4e30-9578-f921460fe3f3")
  def test_connect_to_wpa2_psk_ccmp_2g_after_reboot(self):
    """Test Wi-Fi reconnection after reboot.

    Steps:
        DUT connect to 2GHz Wi-Fi network.
        DUT reboot.
        DUT verify internet connection with HTTP ping.
    """
    self.start_openwrt()
    self.openwrt.log.info("Enable WPA2-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)
    wutils.connect_to_wifi_network(self.dut, self.wpa2_psk_2g)
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)
    # Reboot the DUT.
    self.dut.log.info("Reboot the DUT")
    self.dut.reboot()
    self.dut.wait_for_boot_completion()
    wutils.wait_for_connect(self.dut,
                            self.wpa2_psk_2g[WifiEnums.SSID_KEY])
    wutils.validate_connection(self.dut)

  @test_tracker_info(uuid="7cb462c8-0a6e-4f33-b2fc-bf630d57e087")
  def test_connect_to_wpa2_psk_ccmp_2g_after_incorrect_password(self):
    """Test Wi-Fi reconnection after incorrect password.

    Steps:
        DUT connect to 2GHz Wi-Fi network.
        DUT try to connect to the Wi-Fi network with incorrect password.
        Connection fail as expected.
        DUT connect to the Wi-Fi network with correct password.
        DUT verify internet connection with HTTP ping.
    """
    self.start_openwrt()
    self.openwrt.log.info("Enable WPA2-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)
    self.wpa2_psk_2g_fail = self.wpa2_psk_2g.copy()
    self.wpa2_psk_2g_fail["password"] = "incorrect_password"
    # Try to connect a Wi-Fi network with incorrect password.
    try:
      self.dut.log.info("Connect to Wi-Fi with wrong password")
      wutils.wifi_connect(self.dut, self.wpa2_psk_2g_fail, num_of_tries=1)
    except:
      self.dut.log.info("Connect to Wi-Fi with correct password")
      wutils.wifi_connect(self.dut, self.wpa2_psk_2g)
    else:
      raise signals.TestFailure("DUT connect to Wi-Fi with wrong password")
    self.verify_wpa_network_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)
    wutils.validate_connection(self.dut)

  @test_tracker_info(uuid="890712de-2a3e-4b24-8529-e93893c8d99c")
  def test_connect_to_wpa2_psk_ccmp_2g_after_out_of_range(self):
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
    self.start_openwrt()
    self.openwrt.log.info("Enable WPA2-PSK CCMP on OpenWrt AP")
    self.openwrt.set_wpa_encryption(OpenWrtWifiSecurity.WPA2_PSK_CCMP)
    wutils.connect_to_wifi_network(self.dut, self.wpa2_psk_2g)
    # Make the DUT out of range.
    wutils.set_attns(self.attenuators,
                     "atten1_off_atten2_off",
                     self.roaming_attn)
    wutils.wait_for_disconnect(self.dut, 30) # 30s is experimental choice
    wutils.start_wifi_connection_scan_and_ensure_network_not_found(
        self.dut,
        self.wpa2_psk_2g[WifiEnums.SSID_KEY])
    # Make the DUT back in range.
    wutils.set_attns(self.attenuators,
                     "atten1_on_atten2_on",
                     self.roaming_attn)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wpa2_psk_2g[WifiEnums.SSID_KEY])
    wutils.connect_to_wifi_network(self.dut, self.wpa2_psk_2g)
