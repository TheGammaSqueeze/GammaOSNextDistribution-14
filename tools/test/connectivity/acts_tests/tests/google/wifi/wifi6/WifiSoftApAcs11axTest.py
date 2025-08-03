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
from acts.controllers.ap_lib import hostapd_constants
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from ..WifiSoftApAcsTest import WifiSoftApAcsTest


class WifiSoftApAcs11axTest(WifiSoftApAcsTest):
  """Tests for Automatic Channel Selection for 11ax."""

  def __init__(self, configs):
    super().__init__(configs)
    self.tests = (
        "test_softap_2G_clean_env",
        "test_softap_5G_clean_env",
        "test_softap_auto_clean_env",
        "test_softap_2G_avoid_channel_6",
        "test_softap_5G_avoid_channel_6",
        "test_softap_2G_avoid_channel_36",
        "test_softap_5G_avoid_channel_36",
        "test_softap_2G_avoid_channel_1",
        "test_softap_5G_avoid_channel_1",
        "test_softap_2G_avoid_channel_2",
        "test_softap_5G_avoid_channel_2",
        "test_softap_2G_avoid_channel_3",
        "test_softap_5G_avoid_channel_3",
        "test_softap_2G_avoid_channel_4",
        "test_softap_5G_avoid_channel_4",
        "test_softap_2G_avoid_channel_5",
        "test_softap_5G_avoid_channel_5",
        "test_softap_2G_avoid_channel_7",
        "test_softap_5G_avoid_channel_7",
        "test_softap_2G_avoid_channel_8",
        "test_softap_5G_avoid_channel_8",
        "test_softap_2G_avoid_channel_9",
        "test_softap_5G_avoid_channel_9",
        "test_softap_2G_avoid_channel_10",
        "test_softap_5G_avoid_channel_10",
        "test_softap_2G_avoid_channel_11",
        "test_softap_5G_avoid_channel_11",
        "test_softap_2G_avoid_channel_40",
        "test_softap_5G_avoid_channel_40",
        "test_softap_2G_avoid_channel_44",
        "test_softap_5G_avoid_channel_44",
        "test_softap_2G_avoid_channel_48",
        "test_softap_5G_avoid_channel_48",
        "test_softap_2G_avoid_channel_149",
        "test_softap_5G_avoid_channel_149",
        "test_softap_2G_avoid_channel_153",
        "test_softap_5G_avoid_channel_153",
        "test_softap_2G_avoid_channel_157",
        "test_softap_5G_avoid_channel_157",
        "test_softap_2G_avoid_channel_161",
        "test_softap_5G_avoid_channel_161",
        "test_softap_2G_avoid_channel_165",
        "test_softap_5G_avoid_channel_165",
    )

  def setup_class(self):
    WifiBaseTest.setup_class(self)

    self.dut = self.android_devices[0]
    self.dut_client = self.android_devices[1]
    for ad in self.android_devices:
      # Enable verbose logging on the duts
      ad.droid.wifiEnableVerboseLogging(1)
      asserts.assert_equal(ad.droid.wifiGetVerboseLoggingLevel(), 1,
                           "Failed to enable WiFi verbose logging.")
    req_params = ["reference_networks", "wifi6_models",]
    opt_params = ["iperf_server_address", "iperf_server_port"]
    self.unpack_userparams(
        req_param_names=req_params, opt_param_names=opt_params)
    self.ap = self.access_points[0]
    self.chan_map = {v: k for k, v in hostapd_constants.CHANNEL_MAP.items()}

  def setup_test(self):
    WifiBaseTest.setup_test(self)
    self.dut.droid.wakeLockAcquireBright()
    self.dut.droid.wakeUpNow()

  def teardown_test(self):
    WifiBaseTest.teardown_test(self)
    self.dut.droid.wakeLockRelease()
    self.dut.droid.goToSleepNow()
    wutils.stop_wifi_tethering(self.dut)
    for ad in self.android_devices:
      wutils.reset_wifi(ad)

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
