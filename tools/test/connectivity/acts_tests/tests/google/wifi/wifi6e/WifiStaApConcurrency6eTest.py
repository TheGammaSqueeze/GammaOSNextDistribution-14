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

from acts import asserts
import acts.signals as signals
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from ..WifiStaApConcurrencyTest import WifiStaApConcurrencyTest

WifiEnums = wutils.WifiEnums
WIFI_CONFIG_SOFTAP_BAND_2G = WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G
WIFI_CONFIG_SOFTAP_BAND_5G = WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G
WIFI_CONFIG_SOFTAP_BAND_2G_5G = WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G


class WifiStaApConcurrency6eTest(WifiStaApConcurrencyTest):
  """Tests for sta+ap concurrency for 6ghz.

  Test Bed Requirement:
    2 Android devices, 1 Asus AXE11000 Access Point.
  """

  def __init__(self, configs):
    super().__init__(configs)
    self.tests = (
        "test_wifi_connection_6G_softap_2G",
        "test_wifi_connection_6G_softap_5G",
        "test_wifi_connection_6G_softap_2G_with_location_scan_on",
        "test_softap_2G_wifi_connection_6G",
        "test_softap_5G_wifi_connection_6G",
        "test_softap_5G_wifi_connection_6G_with_location_scan_on",
        "test_wifi_connection_6G_softap_2G_to_softap_5G",
    )

  def setup_class(self):
    WifiBaseTest.setup_class(self)

    self.dut = self.android_devices[0]
    self.dut_client = self.android_devices[1]
    req_params = [
        "iperf_server_address", "iperf_server_port", "wifi6_models",
        "dbs_supported_models"
    ]
    self.unpack_userparams(req_param_names=req_params,)
    self.ap1 = self.access_points[0]
    self.wifi_network_6g = self.ap1.get_wifi_network("6g")

  def teardown_test(self):
    try:
      wutils.stop_wifi_tethering(self.dut)
    except signals.TestFailure:
      pass
    for ad in self.android_devices:
      ad.droid.wakeLockRelease()
      ad.droid.goToSleepNow()
      wutils.reset_wifi(ad)
      wutils.wifi_toggle_state(ad, True)
    self.turn_location_on_and_scan_toggle_on()

  ### Helper Methods ###

  def _verify_softap_band(self, expected_band):
    """Verify softap band is correctly set.

    Args:
      expected_band: Expected softap band.
    """
    configured_band = self.dut.droid.wifiGetApConfiguration()["apBand"]
    asserts.assert_true(configured_band == expected_band,
                        "Expected softap band: %s, Configured softap band: %s",
                        (expected_band, configured_band))

  ### Tests ###

  @test_tracker_info(uuid="bdd8cbf0-526d-4a20-b10b-3796f648f8d0")
  def test_wifi_connection_6G_softap_2G(self):
    """Test connection to 6G network followed by SoftAp on 2G."""
    self.connect_to_wifi_network_and_start_softap(self.wifi_network_6g,
                                                  WIFI_CONFIG_SOFTAP_BAND_2G)
    self._verify_softap_band(WIFI_CONFIG_SOFTAP_BAND_2G)

  @test_tracker_info(uuid="94946b34-7cff-4582-a311-3e4e3690324b")
  def test_wifi_connection_6G_softap_5G(self):
    """Test connection to 6G network followed by SoftAp on 5G."""
    self.connect_to_wifi_network_and_start_softap(self.wifi_network_6g,
                                                  WIFI_CONFIG_SOFTAP_BAND_5G)
    self._verify_softap_band(WIFI_CONFIG_SOFTAP_BAND_2G_5G)

  @test_tracker_info(uuid="34e85416-42b5-44b0-b97a-a4b0818e6cae")
  def test_wifi_connection_6G_softap_2G_with_location_scan_on(self):
    """Test connection to 6G network, SoftAp on 2G with location scan on."""
    self.turn_location_on_and_scan_toggle_on()
    self.connect_to_wifi_network_and_start_softap(self.wifi_network_6g,
                                                  WIFI_CONFIG_SOFTAP_BAND_2G)
    self._verify_softap_band(WIFI_CONFIG_SOFTAP_BAND_2G)
    # Now toggle wifi off & ensure we can still scan.
    wutils.wifi_toggle_state(self.dut, False)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wifi_network_6g[WifiEnums.SSID_KEY])

  @test_tracker_info(uuid="9de42708-fa93-4901-8704-1411f435b256")
  def test_softap_2G_wifi_connection_6G(self):
    """Test SoftAp on 2G followed by connection to 6G network."""
    self.start_softap_and_connect_to_wifi_network(self.wifi_network_6g,
                                                  WIFI_CONFIG_SOFTAP_BAND_2G)
    self._verify_softap_band(WIFI_CONFIG_SOFTAP_BAND_2G)

  @test_tracker_info(uuid="9cdafc82-e971-4610-9989-e1c1d39ad18f")
  def test_softap_5G_wifi_connection_6G(self):
    """Test SoftAp on 5G followed by connection to 6G network."""
    self.start_softap_and_connect_to_wifi_network(self.wifi_network_6g,
                                                  WIFI_CONFIG_SOFTAP_BAND_5G)
    self._verify_softap_band(WIFI_CONFIG_SOFTAP_BAND_2G_5G)

  @test_tracker_info(uuid="bfad2e86-d1d7-44b3-b8c8-eb49f3f97742")
  def test_softap_5G_wifi_connection_6G_with_location_scan_on(self):
    """Test SoftAp on 5G, connection to 6G network with location scan on."""
    self.turn_location_on_and_scan_toggle_on()
    self.start_softap_and_connect_to_wifi_network(self.wifi_network_6g,
                                                  WIFI_CONFIG_SOFTAP_BAND_5G)
    self._verify_softap_band(WIFI_CONFIG_SOFTAP_BAND_2G_5G)
    # Now toggle wifi off & ensure we can still scan.
    wutils.wifi_toggle_state(self.dut, False)
    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wifi_network_6g[WifiEnums.SSID_KEY])

  @test_tracker_info(uuid="e50a0901-e07c-4b6f-9343-35e0dec14f2b")
  def test_wifi_connection_6G_softap_2G_to_softap_5G(self):
    """Test connection to 6G network, softap on 2G and switch to 5G."""
    self.connect_to_wifi_network_and_start_softap(self.wifi_network_6g,
                                                  WIFI_CONFIG_SOFTAP_BAND_2G)
    self._verify_softap_band(WIFI_CONFIG_SOFTAP_BAND_2G)
    self.softap_change_band(self.dut)
    self._verify_softap_band(WIFI_CONFIG_SOFTAP_BAND_2G_5G)
