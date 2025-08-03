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

import time
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from ..WifiPnoTest import WifiPnoTest

MAX_ATTN = 95


class WifiPno6eTest(WifiPnoTest):
  """Tests for pno for 6ghz.

  Test Bed Requirement:
    1 Android devices, 2 Asus AXE11000 Access Point.
  """

  def __init__(self, configs):
    super().__init__(configs)
    self.tests = (
        "test_simple_pno_connection_to_6G",
        "test_pno_connection_with_multiple_saved_networks",
    )

  def setup_class(self):
    WifiBaseTest.setup_class(self)

    self.dut = self.android_devices[0]
    req_params = [
        "attn_vals", "pno_interval", "reference_networks", "wifi6_models"
    ]
    self.unpack_userparams(req_param_names=req_params,)
    self.attn_a = self.attenuators[0]
    self.attn_b = self.attenuators[1]
    self.attenuators[2].set_atten(MAX_ATTN)
    self.attenuators[3].set_atten(MAX_ATTN)
    self.set_attns("default")
    self.pno_network_a = self.reference_networks[0]["2g"]
    self.pno_network_b = self.reference_networks[0]["6g"]

  def teardown_class(self):
    WifiBaseTest.teardown_class(self)
    self.attenuators[2].set_atten(0)
    self.attenuators[3].set_atten(0)

  ### Tests ###

  @test_tracker_info(uuid="4367138e-8c29-40f2-a963-44094223e525")
  def test_simple_pno_connection_to_6G(self):
    self.add_network_and_enable(self.pno_network_a)
    self.add_network_and_enable(self.pno_network_b)
    self.trigger_pno_and_assert_connect("b_on_a_off", self.pno_network_b)

  @test_tracker_info(uuid="fb147268-87bb-4403-a971-ce3e0bd36d2c")
  def test_pno_connection_with_multiple_saved_networks(self):
    self.add_and_enable_test_networks(16)
    self.add_network_and_enable(self.pno_network_a)
    self.add_network_and_enable(self.pno_network_b)
    # Force single scan so that both networks become preferred before PNO.
    wutils.start_wifi_connection_scan_and_return_status(self.dut)
    self.dut.droid.goToSleepNow()
    wutils.wifi_toggle_state(self.dut, False)
    wutils.wifi_toggle_state(self.dut, True)
    time.sleep(10)
    self.trigger_pno_and_assert_connect("b_on_a_off", self.pno_network_b)

