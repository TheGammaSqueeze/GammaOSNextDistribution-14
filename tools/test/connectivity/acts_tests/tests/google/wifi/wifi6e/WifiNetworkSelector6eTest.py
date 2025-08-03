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
from ..WifiNetworkSelectorTest import WifiNetworkSelectorTest

# WifiNetworkSelector imposes a 10 seconds gap between two selections
NETWORK_SELECTION_TIME_GAP = 12
LVL_ATTN = 15
MIN_ATTN = 0
MAX_ATTN = 95
ATTN_SLEEP = 12


class WifiNetworkSelector6eTest(WifiNetworkSelectorTest):
  """Tests for network selector 6e tests.

  Test Bed Requirement:
    1 Android devices, 2 Asus AXE11000 Access Point.
  """

  def __init__(self, configs):
    super().__init__(configs)
    self.tests = (
        "test_network_selector_automatic_connection",
        "test_network_selector_basic_connection_prefer_6g_over_2g",
        "test_network_selector_basic_connection_prefer_6g_over_5g",
        "test_network_selector_prefer_stronger_rssi",
        "test_network_selector_excludelist_by_connection_failure",
        "test_network_selector_stay_on_user_selected_network",
        "test_network_selector_reselect_after_forget_network"
    )

  def setup_class(self):
    WifiBaseTest.setup_class(self)

    self.dut = self.android_devices[0]
    req_params = [
        "reference_networks", "bssid_map", "wifi6_models", "attn_vals_6g"
    ]
    self.unpack_userparams(req_param_names=req_params,)
    self.ap1 = self.reference_networks[0]["6g"]
    self.ap2 = self.reference_networks[1]["6g"]

  ### Tests ###

  @test_tracker_info(uuid="a5577d5c-3d4b-4c97-9dc2-25c0f6b54398")
  def test_network_selector_automatic_connection(self):
    """Network selection for automatic connection.

    Steps:
      1. Add a 6g network.
      2. Move DUT in range.
      3. Verify DUT is connected to the network.
    """

    # add a saved network to DUT
    self.add_networks(self.dut, [self.ap1])

    # move DUT in range
    self.attenuators[1].set_atten(MIN_ATTN)
    time.sleep(ATTN_SLEEP)

    # verify DUT is connect to 6g network
    network = self.ap1.copy()
    network["bssid"] = self.bssid_map[0]["6g"]
    self.connect_and_verify_connected_bssid(network)

  @test_tracker_info(uuid="29fc79fc-3b19-4e30-b325-4bc750be7eda")
  def test_network_selector_basic_connection_prefer_6g_over_2g(self):
    """Network selector prefers 6g network over 2g.

    Steps:
      1. Add two saved networks - 2g and 6g of similar RSSI.
      2. Move DUT in range.
      3. Verify DUT is connected to 6g network.
    """
    ap1 = self.reference_networks[1]["2g"]
    ap2 = self.reference_networks[1]["6g"]

    # add two saved networks
    self.add_networks(self.dut, [ap1, ap2])

    # move DUT in range
    self.attenuators[2].set_atten(self.attn_vals_6g[2]["ATTN_DIFF"])
    self.attenuators[3].set_atten(self.attn_vals_6g[3]["ATTN_DIFF"])
    time.sleep(ATTN_SLEEP)

    # verify DUT is connected to 6g network
    network = ap2.copy()
    network["bssid"] = self.bssid_map[1]["6g"]
    self.connect_and_verify_connected_bssid(network)

  @test_tracker_info(uuid="a1c11fab-2878-45fc-8d1b-057fc6c72019")
  def test_network_selector_basic_connection_prefer_6g_over_5g(self):
    """Network selector prefers 6g network over 5g.

    Steps:
      1. Add two saved networks - 5g and 6g of similar RSSI.
      2. Move DUT in range.
      3. Verify DUT is connected to 6g network.
    """
    ap1 = self.reference_networks[1]["5g"]
    ap2 = self.reference_networks[1]["6g"]

    # add two saved networks
    self.add_networks(self.dut, [ap1, ap2])

    # move DUT in range
    self.attenuators[2].set_atten(self.attn_vals_6g[2]["ATTN_DIFF"])
    self.attenuators[3].set_atten(self.attn_vals_6g[3]["ATTN_DIFF"])
    time.sleep(ATTN_SLEEP)

    # verify DUT is connected to 6g network
    network = ap2.copy()
    network["bssid"] = self.bssid_map[1]["6g"]
    self.connect_and_verify_connected_bssid(network)

  @test_tracker_info(uuid="7908a0a1-1786-4b8d-801c-a648bfaffaad")
  def test_network_selector_prefer_stronger_rssi(self):
    """Network selector test for stronger RSSI.

    Steps:
      1. Add two 6g networks, one with stronger RSSI.
      2. Move DUT in range.
      3. Verify DUT is connected to ap with stronger RSSI.
    """

    # add 2 6ghz networks.
    self.add_networks(self.dut, [self.ap1, self.ap2])

    # move DUT in range
    self.attenuators[1].set_atten(MIN_ATTN)
    self.attenuators[3].set_atten(LVL_ATTN)
    time.sleep(ATTN_SLEEP)

    # verify DUT is connected to ap1
    network = self.ap1.copy()
    network["bssid"] = self.bssid_map[0]["6g"]
    self.connect_and_verify_connected_bssid(network)

  @test_tracker_info(uuid="b7adde45-224c-46d5-9948-f8739a586912")
  def test_network_selector_excludelist_by_connection_failure(self):
    """Network selector for excludelisted network.

    Steps:
      1. Add two saved networks one with stronger RSSI and wrong password.
      2. Move DUT in range.
      3. Verify DUT is connected to weaker and correct ap.
    """
    ap1 = self.ap1.copy()
    ap1["password"] += "_haha"

    # add the wifi networks.
    self.add_networks(self.dut, [ap1, self.ap2])

    # move the DUT in range.
    self.attenuators[1].set_atten(MIN_ATTN)
    self.attenuators[3].set_atten(LVL_ATTN)
    time.sleep(ATTN_SLEEP)

    # start scans to make ap1 excludelisted
    for _ in range(3):
      wutils.start_wifi_connection_scan_and_return_status(self.dut)
      time.sleep(NETWORK_SELECTION_TIME_GAP)

    # verify DUT is connected to ap2.
    network = self.ap2.copy()
    network["bssid"] = self.bssid_map[1]["6g"]
    self.connect_and_verify_connected_bssid(network)

  @test_tracker_info(uuid="fae1a621-1302-4153-b51e-5d1a22018394")
  def test_network_selector_stay_on_user_selected_network(self):
    """Network selector with two saved 6GHz networks.

    Steps:
      1. Connect DUT to ap1 with low RSSI.
      2. Add ap2 as saved network.
      3. Start scan and verify DUT stays on ap1.
    """

    # set max attenuation on ap2 and make ap1 6G in range with low RSSI
    self.attenuators[1].set_atten(LVL_ATTN)
    self.attenuators[3].set_atten(MIN_ATTN)
    time.sleep(ATTN_SLEEP)

    # connect to ap1 via user selection and add, save ap2
    wutils.connect_to_wifi_network(self.dut, self.ap1)
    self.add_networks(self.dut, [self.ap2])

    # ensure the time gap between two network selections
    time.sleep(NETWORK_SELECTION_TIME_GAP)

    # verify we are still connected to ap1
    network = self.ap1.copy()
    network["bssid"] = self.bssid_map[0]["6g"]
    self.connect_and_verify_connected_bssid(network)

  @test_tracker_info(uuid="40d8ef8f-728b-43ac-8004-8847031a8198")
  def test_network_selector_reselect_after_forget_network(self):
    """Network selector with two 6GHz networks.

    Steps:
      1. Add two 6G networks X & Y to DUT with one having higher RSSI.
      2. Connect to X and forget it.
      3. Verify the DUT reselect and connect to Y.
    """

    # add two networks to DUT
    self.add_networks(self.dut, [self.ap1, self.ap2])

    # make both networks in range. AP1 has higher RSSI
    self.attenuators[1].set_atten(MIN_ATTN)
    self.attenuators[3].set_atten(LVL_ATTN)
    time.sleep(ATTN_SLEEP)

    # verify DUT connected to AP1
    network = self.ap1.copy()
    network["bssid"] = self.bssid_map[0]["6g"]
    self.connect_and_verify_connected_bssid(network)

    # forget AP_1
    wutils.wifi_forget_network(self.dut, self.ap1["SSID"])

    # verify DUT connected to AP2
    network = self.ap2.copy()
    network["bssid"] = self.bssid_map[1]["6g"]
    self.connect_and_verify_connected_bssid(network)

