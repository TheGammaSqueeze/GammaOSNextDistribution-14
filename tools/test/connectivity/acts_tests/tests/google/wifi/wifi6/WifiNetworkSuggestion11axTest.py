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

from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from ..WifiNetworkSuggestionTest import WifiNetworkSuggestionTest

WifiEnums = wutils.WifiEnums
# EAP Macros
EAP = WifiEnums.Eap
EapPhase2 = WifiEnums.EapPhase2
Ent = WifiEnums.Enterprise


class WifiNetworkSuggestion11axTest(WifiNetworkSuggestionTest):
  """Tests for Enterprise network suggestion 11ax.

  Test Bed Requirement:
    One Android device, 1 Asus AXE11000 Access Point and Radius server
  """

  def __init__(self, configs):
    super().__init__(configs)
    self.tests = (
        "test_connect_to_wpa_ent_config_ttls_pap_reboot_config_store",
        "test_connect_to_wpa_ent_config_aka_reboot_config_store",
    )

  def setup_class(self):
    WifiBaseTest.setup_class(self)

    self.dut = self.android_devices[0]
    req_params = [
        "radius_conf_2g", "wifi6_models", "ca_cert", "eap_identity",
        "eap_password", "domain_suffix_match"
    ]
    self.unpack_userparams(req_params)
    self.ap = self.access_points[0]
    self.ap.configure_ap({
        "2g": {
            "security": "wpa2",
            "radius_server_ip": self.radius_conf_2g["radius_server_ip"],
            "radius_server_port": self.radius_conf_2g["radius_server_port"],
            "radius_server_secret": self.radius_conf_2g["radius_server_secret"],
        }
    })
    self.ent_network_2g = self.ap.get_wifi_network("2g")
    self.dut.droid.wifiRemoveNetworkSuggestions([])

  def setup_test(self):
    WifiBaseTest.setup_test(self)

    self.dut.droid.wakeLockAcquireBright()
    self.dut.droid.wakeUpNow()
    self.dut.unlock_screen()
    self.clear_user_disabled_networks()
    wutils.wifi_toggle_state(self.dut, True)
    self.dut.ed.clear_all_events()
    self.clear_carrier_approved(str(self.dut.droid.telephonyGetSimCarrierId()))
