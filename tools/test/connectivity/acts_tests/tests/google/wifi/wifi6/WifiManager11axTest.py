#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from ..WifiManagerTest import WifiManagerTest


class WifiManager11axTest(WifiManagerTest):
  """Tests for WifiManager 11ax.

  Test Bed Requirement:
    One Android device and 2 Asus AXE11000 Access Point.
  """

  def __init__(self, configs):
    super().__init__(configs)
    self.tests = (
        "test_toggle_wifi_state_and_get_startupTime",
        "test_toggle_with_screen",
        "test_scan",
        "test_scan_with_wifi_off_and_location_scan_on",
        "test_scan_after_reboot_with_wifi_off_and_location_scan_on",
        "test_scan_with_wifi_off_and_location_scan_off",
        "test_add_network",
        "test_forget_network",
        "test_reconnect_to_previously_connected",
        "test_reconnect_toggle_wifi",
        "test_reconnect_toggle_wifi_with_location_scan_on",
        "test_reconnect_toggle_airplane",
        "test_reconnect_toggle_airplane_with_location_scan_on",
        "test_reboot_configstore_reconnect",
        "test_reboot_configstore_reconnect_with_location_scan_on",
        "test_toggle_wifi_reboot_configstore_reconnect",
        "test_toggle_wifi_reboot_configstore_reconnect_with_location_scan_on",
        "test_toggle_airplane_reboot_configstore_reconnect",
        "test_toggle_airplane_reboot_configstore_reconnect_with_location_scan_on",
        "test_reboot_configstore_reconnect_with_screen_lock",
        "test_connect_to_5g_after_reboot_without_unlock",
        "test_config_store_with_wpapsk_2g",
        "test_config_store_with_wpapsk_5g",
        "test_tdls_supported",
        "test_energy_info",
        "test_energy_info_connected",
        "test_connect_to_wpa_2g",
        "test_connect_to_wpa_5g",
        "test_connect_to_2g_can_be_pinged",
        "test_connect_to_5g_can_be_pinged",
        "test_wifi_saved_network_reset",
        "test_reboot_wifi_and_bluetooth_on",
        "test_scan_result_api",
        "test_enable_disable_auto_join_saved_network",
        "test_set_get_coex_unsafe_channels"
    )

  def setup_class(self):
    WifiBaseTest.setup_class(self)

    self.dut = self.android_devices[0]
    self.dut_client = self.android_devices[1]
    req_params = [
        "reference_networks", "wpa_networks", "iperf_server_address",
        "iperf_server_port", "coex_unsafe_channels", "coex_restrictions",
        "wifi6_models"
    ]
    self.unpack_userparams(req_param_names=req_params,)
    self.ap = self.access_points[1]
    self.ap.configure_ap({"2g": {"security": "open"},
                          "5g": {"security": "open"}})
    self.wpapsk_2g = self.reference_networks[0]["2g"]
    self.wpapsk_5g = self.reference_networks[0]["5g"]
    self.open_network_2g = self.ap.get_wifi_network("2g")
    self.open_network_5g = self.ap.get_wifi_network("5g")
