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


class Wifi6eMultiChannelTest(WifiBaseTest):
  """Tests for 6GHz band."""

  def setup_class(self):
    super().setup_class()

    self.dut = self.android_devices[0]
    self.ap = self.access_points[0]
    self.wifi_network = self.ap.get_wifi_network("6g")

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

  @test_tracker_info(uuid="74df7ca9-2e50-4f96-94b3-6d776849847f")
  def test_connect_to_6g_channel_37(self):
    """Connect to 6GHz WPA3 SAE on channel 37."""
    self.ap.set_channel_and_apply("6g", 37)
    wutils.connect_to_wifi_network(self.dut, self.wifi_network)

  @test_tracker_info(uuid="9ebc2250-f98a-4f72-9d6e-94b4067ddd19")
  def test_connect_to_6g_channel_53(self):
    """Connect to 6GHz WPA3 SAE on channel 53."""
    self.ap.set_channel_and_apply("6g", 53)
    wutils.connect_to_wifi_network(self.dut, self.wifi_network)

  @test_tracker_info(uuid="c6aa700b-0216-4693-8584-d95ec243e801")
  def test_connect_to_6g_channel_69(self):
    """Connect to 6GHz WPA3 SAE on channel 69."""
    self.ap.set_channel_and_apply("6g", 69)
    wutils.connect_to_wifi_network(self.dut, self.wifi_network)

  @test_tracker_info(uuid="a122066c-ae55-4b8f-9260-240ac078167d")
  def test_connect_to_6g_channel_85(self):
    """Connect to 6GHz WPA3 SAE on channel 85."""
    self.ap.set_channel_and_apply("6g", 85)
    wutils.connect_to_wifi_network(self.dut, self.wifi_network)

  @test_tracker_info(uuid="d222163b-ffc4-4e97-a5e3-c55ce7d24ab1")
  def test_connect_to_6g_channel_101(self):
    """Connect to 6GHz WPA3 SAE on channel 101."""
    self.ap.set_channel_and_apply("6g", 101)
    wutils.connect_to_wifi_network(self.dut, self.wifi_network)

  @test_tracker_info(uuid="7251b966-7061-415c-9aca-4e7150383dd2")
  def test_connect_to_6g_channel_117(self):
    """Connect to 6GHz WPA3 SAE on channel 117."""
    self.ap.set_channel_and_apply("6g", 117)
    wutils.connect_to_wifi_network(self.dut, self.wifi_network)

  @test_tracker_info(uuid="53b1e07e-66df-46b7-b55b-8045daae500f")
  def test_connect_to_6g_channel_133(self):
    """Connect to 6GHz WPA3 SAE on channel 133."""
    self.ap.set_channel_and_apply("6g", 133)
    wutils.connect_to_wifi_network(self.dut, self.wifi_network)

  @test_tracker_info(uuid="16a09fcc-3fe1-4035-bb17-0f51b8d2b6f3")
  def test_connect_to_6g_channel_149(self):
    """Connect to 6GHz WPA3 SAE on channel 149."""
    self.ap.set_channel_and_apply("6g", 149)
    wutils.connect_to_wifi_network(self.dut, self.wifi_network)

  @test_tracker_info(uuid="6ea66361-86cf-4cf1-ae26-48008914cea1")
  def test_connect_to_6g_channel_165(self):
    """Connect to 6GHz WPA3 SAE on channel 165."""
    self.ap.set_channel_and_apply("6g", 165)
    wutils.connect_to_wifi_network(self.dut, self.wifi_network)

  @test_tracker_info(uuid="9e292afc-21fe-4bb7-81a9-f473b2e051bc")
  def test_connect_to_6g_channel_181(self):
    """Connect to 6GHz WPA3 SAE on channel 181."""
    self.ap.set_channel_and_apply("6g", 181)
    wutils.connect_to_wifi_network(self.dut, self.wifi_network)

  @test_tracker_info(uuid="a7167923-9ccf-4e4f-a34a-707c61044d97")
  def test_connect_to_6g_channel_197(self):
    """Connect to 6GHz WPA3 SAE on channel 197."""
    self.ap.set_channel_and_apply("6g", 197)
    wutils.connect_to_wifi_network(self.dut, self.wifi_network)

  @test_tracker_info(uuid="8d5c96f7-515a-4e85-a60d-eb2e7cc4613d")
  def test_connect_to_6g_channel_213(self):
    """Connect to 6GHz WPA3 SAE on channel 213."""
    self.ap.set_channel_and_apply("6g", 213)
    wutils.connect_to_wifi_network(self.dut, self.wifi_network)
