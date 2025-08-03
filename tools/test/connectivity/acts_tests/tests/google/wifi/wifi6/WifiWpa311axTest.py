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
from ..WifiWpa3OweTest import WifiWpa3OweTest


class WifiWpa311axTest(WifiWpa3OweTest):
  """Tests for WPA3 11ax.

  Test Bed Requirement:
    One Android device and 1 Asus AXE11000 Access Point.
  """

  def __init__(self, configs):
    super().__init__(configs)
    self.tests = (
        "test_connect_to_wpa3_personal_2g",
        "test_connect_to_wpa3_personal_5g",
        "test_connect_to_wpa3_personal_reconnection"
    )

  def setup_class(self):
    WifiBaseTest.setup_class(self)

    self.dut = self.android_devices[0]
    req_params = ["wifi6_models",]
    self.unpack_userparams(req_param_names=req_params,)
    self.ap = self.access_points[0]
    self.ap.configure_ap({"2g": {"security": "sae"},
                          "5g": {"security": "sae"}})
    self.wpa3_personal_2g = self.ap.get_wifi_network("2g")
    self.wpa3_personal_5g = self.ap.get_wifi_network("5g")
