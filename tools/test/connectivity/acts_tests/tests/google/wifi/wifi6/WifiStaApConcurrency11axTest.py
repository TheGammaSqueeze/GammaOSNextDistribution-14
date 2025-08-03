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

from acts.controllers.ap_lib import hostapd_constants
import acts.signals as signals
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from ..WifiStaApConcurrencyTest import WifiStaApConcurrencyTest


class WifiStaApConcurrency11axTest(WifiStaApConcurrencyTest):
  """Tests for STA+AP concurrency 11ax.

  Test Bed Requirement:
    One Android device, 1 Asus AXE11000 Access Point and Radius server
  """

  def __init__(self, configs):
    super().__init__(configs)
    self.tests = (
        "test_wifi_connection_5G_softap_5G",
        "test_wifi_connection_5G_softap_2G",
        "test_wifi_connection_5G_softap_2G_with_location_scan_on",
        "test_softap_5G_wifi_connection_5G",
        "test_softap_2G_wifi_connection_5G",
        "test_wifi_connection_2G_softap_2G",
        "test_wifi_connection_2G_softap_5G",
        "test_softap_2G_wifi_connection_2G",
        "test_wifi_connection_2G_softap_2G_to_softap_5g",
        "test_softap_5G_wifi_connection_2G",
        "test_softap_5G_wifi_connection_2G_with_location_scan_on",
        "test_wifi_connection_5G_softap_2G_to_softap_5g",
        "test_wifi_connection_5G_DFS_softap_5G",
        "test_wifi_connection_5G_DFS_softap_2G",
        "test_softap_5G_wifi_connection_5G_DFS",
        "test_softap_2G_wifi_connection_5G_DFS",
    )

  def setup_class(self):
    WifiBaseTest.setup_class(self)

    self.dut = self.android_devices[0]
    self.dut_client = self.android_devices[1]
    req_params = ["iperf_server_address", "iperf_server_port", "wifi6_models"]
    self.unpack_userparams(req_param_names=req_params,)
    self.ap = self.access_points[0]
    self.ap.configure_ap({"2g": {"security": "open"},
                          "5g": {"security": "open"}})
    self.open_2g = self.ap.get_wifi_network("2g")
    self.open_5g = self.ap.get_wifi_network("5g")

  def teardown_test(self):
    WifiBaseTest.teardown_test(self)
    # Prevent the stop wifi tethering failure to block ap close
    try:
      wutils.stop_wifi_tethering(self.dut)
    except signals.TestFailure:
      pass
    for ad in self.android_devices:
      ad.droid.wakeLockRelease()
      ad.droid.goToSleepNow()
      wutils.reset_wifi(ad)
    self.turn_location_on_and_scan_toggle_on()

  ### Helper Methods ###

  def configure_ap(self, channel_2g=None, channel_5g=None):
    """Configure and bring up AP on required channel.

    Args:
      channel_2g: The channel number to use for 2GHz network.
      channel_5g: The channel number to use for 5GHz network.
    """
    if not channel_2g:
      channel_2g = hostapd_constants.AP_DEFAULT_CHANNEL_2G
    if not channel_5g:
      channel_5g = hostapd_constants.AP_DEFAULT_CHANNEL_5G
    if int(self.ap.get_configured_channel("5g")) != channel_5g:
      self.ap.set_channel_and_apply("5g", channel_5g)
    if int(self.ap.get_configured_channel("2g")) != channel_2g:
      self.ap.set_channel_and_apply("2g", channel_2g)

