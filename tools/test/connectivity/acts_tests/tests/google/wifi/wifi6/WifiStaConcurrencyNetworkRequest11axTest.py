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

import acts.utils as utils
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from ..WifiStaConcurrencyNetworkRequestTest import WifiStaConcurrencyNetworkRequestTest


class WifiStaConcurrencyNetworkRequest11axTest(
    WifiStaConcurrencyNetworkRequestTest):
  """Tests for STA concurrency network request 11ax.

  Test Bed Requirement:
    One Android device, 2 Asus AXE11000 Access Points.
  """

  def __init__(self, configs):
    super().__init__(configs)
    self.tests = (
        "test_connect_to_2g_p2p_while_connected_to_5g_internet",
        "test_connect_to_2g_internet_while_connected_to_5g_p2p",
        "test_connect_to_2g_internet_while_connected_to_2g_p2p",
        "test_connect_to_5g_internet_while_connected_to_5g_p2p",
        "test_connect_to_5g_dfs_internet_while_connected_to_5g_dfs_p2p",
        "test_connect_to_2g_internet_while_connected_to_2g_p2p_same_ssid",
        "test_connect_to_5g_p2p_while_connected_to_5g_internet_same_ssid",
    )

  def setup_class(self):
    WifiBaseTest.setup_class(self)

    self.dut = self.android_devices[0]
    wutils.wifi_test_device_init(self.dut)
    req_params = ["sta_concurrency_supported_models", "wifi6_models"]
    self.unpack_userparams(req_param_names=req_params,)
    self.ap1 = self.access_points[0]
    self.ap2 = self.access_points[1]

  def teardown_test(self):
    WifiBaseTest.teardown_test(self)
    self.disconnect_both()
    self.dut.droid.wakeLockRelease()
    self.dut.droid.goToSleepNow()
    self.dut.droid.wifiDisconnect()
    wutils.reset_wifi(self.dut)
    # Ensure we disconnected from the current network before the next test.
    if self.dut.droid.wifiGetConnectionInfo(
    )["supplicant_state"] != "disconnected":
      wutils.wait_for_disconnect(self.dut)
    wutils.wifi_toggle_state(self.dut, False)
    self.dut.ed.clear_all_events()

  def configure_ap(self,
                   channel_2g=None,
                   channel_5g=None,
                   channel_2g_ap2=None,
                   channel_5g_ap2=None,
                   mirror_ap=False,
                   ap_count=1):
    """Configure AP based on test case requirements."""
    if ap_count == 1:
      self.ap1.set_channel_and_apply("2g", channel_2g)
      self.ap1.set_channel_and_apply("5g", channel_5g)
    elif ap_count == 2 and channel_2g_ap2:
      ssid1 = "test_2g_1"
      ssid2 = "test_2g_2"
      if mirror_ap:
        ssid1 = "test_%s" % utils.rand_ascii_str(4)
        ssid2 = ssid1
      self.ap1.set_channel_and_apply("2g", channel_2g)
      self.ap2.set_channel_and_apply("2g", channel_2g_ap2)
      self.ap1.configure_ap({"2g": {"ssid": ssid1},})
      self.ap2.configure_ap({"2g": {"ssid": ssid2},})
    elif ap_count == 2 and channel_5g_ap2:
      ssid1 = "test_5g_1"
      ssid2 = "test_5g_2"
      if mirror_ap:
        ssid1 = "test_%s" % utils.rand_ascii_str(4)
        ssid2 = ssid1
      self.ap1.set_channel_and_apply("5g", channel_5g)
      self.ap2.set_channel_and_apply("5g", channel_5g_ap2)
      self.ap1.configure_ap({"5g": {"ssid": ssid1},})
      self.ap2.configure_ap({"5g": {"ssid": ssid2},})

