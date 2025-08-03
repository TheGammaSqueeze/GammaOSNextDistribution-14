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
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from ..WifiPnoTest import WifiPnoTest

MAX_ATTN = 95


class WifiPno11axTest(WifiPnoTest):
  """Tests for pno for 11ax.

  Test Bed Requirement:
    1 Android devices, 2 Asus AXE11000 Access Point.
  """

  def __init__(self, configs):
    super().__init__(configs)
    self.tests = (
        "test_simple_pno_connection_to_2g",
        "test_simple_pno_connection_to_5g",
        "test_pno_connection_with_multiple_saved_networks"
    )

  def setup_class(self):
    WifiBaseTest.setup_class(self)

    self.dut = self.android_devices[0]
    req_params = [
        "attn_vals", "pno_interval", "reference_networks", "wifi6_models"
    ]
    self.unpack_userparams(req_param_names=req_params,)
    self.attn_a = self.attenuators[0]
    self.attn_b = self.attenuators[2]
    self.attenuators[1].set_atten(MAX_ATTN)
    self.attenuators[3].set_atten(MAX_ATTN)
    time.sleep(self.pno_interval)  # wait for sometime for 5GHz to come up.
    self.set_attns("default")
    self.pno_network_a = self.reference_networks[0]["2g"]
    self.pno_network_b = self.reference_networks[1]["5g"]

  def teardown_class(self):
    self.attenuators[1].set_atten(0)
    self.attenuators[3].set_atten(0)

