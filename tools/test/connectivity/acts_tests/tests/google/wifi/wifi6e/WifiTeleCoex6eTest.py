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


from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from ..WifiTeleCoexTest import WifiTeleCoexTest


class WifiTeleCoex6eTest(WifiTeleCoexTest):
  """Tests for wifi tel coex 6e tests.

  Test Bed Requirement:
    1 Android devices, 1 Asus AXE11000 Access Point, 1 Carrier SIM.
  """

  def __init__(self, configs):
    super().__init__(configs)

    self.tests = (
        "test_toggle_wifi_call_6e",
        "test_toggle_airplane_call_6e",
        "test_toggle_airplane_and_wifi_call_6e",
    )

  def setup_class(self):
    TelephonyBaseTest.setup_class(self)

    self.ads = self.android_devices
    self.dut = self.android_devices[0]
    req_params = ["reference_networks",]
    self.unpack_userparams(req_param_names=req_params,)
    self.network = self.reference_networks[0]["6g"]
    self.wifi_network_ssid = self.network["SSID"]

  ### Tests ###

  @test_tracker_info(uuid="2ca35dcd-f449-4e74-9d5a-9238168f25a5")
  def test_toggle_wifi_call_6e(self):
    super().test_toggle_wifi_call()

  @test_tracker_info(uuid="d819a404-609b-477a-95f6-9fc68ce04947")
  def test_toggle_airplane_call_6e(self):
    super().test_toggle_airplane_call()

  @test_tracker_info(uuid="bc802fc9-2763-4789-94b0-15655333af81")
  def test_toggle_airplane_and_wifi_call_6e(self):
    super().test_toggle_airplane_and_wifi_call()
