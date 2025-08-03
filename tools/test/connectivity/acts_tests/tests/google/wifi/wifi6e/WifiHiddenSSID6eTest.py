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
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest


class WifiHiddenSSID6eTest(WifiBaseTest):
  """Tests for hidden 6e tests.

  Test Bed Requirement:
    1 Android devices, 1 Asus AXE11000 Access Point.
  """

  def setup_class(self):
    super().setup_class()

    self.dut = self.android_devices[0]
    self.ap = self.access_points[0]
    self.ap.configure_hidden_network_and_apply("6g", True)

  def teardown_class(self):
    super.teardown_class()
    self.ap.configure_hidden_network_and_apply("6g", False)

  def setup_test(self):
    super().setup_test()
    for ad in self.android_devices:
      ad.droid.wakeLockAcquireBright()
      ad.droid.wakeUpNow()

  def teardown_test(self):
    super().teardown_test()
    for ad in self.android_devices:
      ad.droid.wakeLockRelease()
      ad.droid.goToSleepNow()
    wutils.reset_wifi(self.dut)

  ### Tests ###

  @test_tracker_info(uuid="233b1019-2025-4a5b-8c6f-c442c189fe17")
  def test_connect_to_6g_wpa3_sae_hidden(self):
    """Connect to 6GHz WPA3 SAE hidden."""
    self.ap.configure_ap({"6g": {"security": "sae"}})
    wifi_network = self.ap.get_wifi_network("6g")
    wifi_network["hiddenSSID"] = True
    wutils.connect_to_wifi_network(self.dut, wifi_network, hidden=True)

  @test_tracker_info(uuid="303b0d51-5427-4718-9ac1-45b65dbde56f")
  def test_connect_to_6g_wpa3_owe_hidden(self):
    """Connect to 6GHz WPA3 OWE hidden."""
    self.ap.configure_ap({"6g": {"security": "owe"}})
    wifi_network = self.ap.get_wifi_network("6g")
    wutils.connect_to_wifi_network(self.dut, wifi_network, hidden=True)
