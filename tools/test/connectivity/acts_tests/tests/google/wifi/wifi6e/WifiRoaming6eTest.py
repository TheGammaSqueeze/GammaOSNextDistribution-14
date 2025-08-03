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
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest


class WifiRoaming6eTest(WifiBaseTest):
  """Tests for 6e roaming tests.

  Test Bed Requirement:
    1 Android devices, 2 Asus AXE11000 Access Point.
  """

  def setup_class(self):
    super().setup_class()
    self.dut = self.android_devices[0]
    self.ap1 = self.access_points[0]
    self.ap2 = self.access_points[1]
    req_params = ["roaming_attn_6g", "bssid_map"]
    self.unpack_userparams(req_param_names=req_params,)

  def setup_test(self):
    super().setup_test()
    self.dut.ed.clear_all_events()
    self.dut.droid.wakeLockAcquireBright()
    self.dut.droid.wakeUpNow()

  def teardown_test(self):
    super().teardown_test()
    wutils.set_attns(self.attenuators, "default")
    self.dut.droid.wakeLockRelease()
    self.dut.droid.goToSleepNow()
    self.dut.ed.clear_all_events()
    wutils.reset_wifi(self.dut)

  ### Helper methods ###

  def trigger_roaming_and_validate(self, attn_val, expected_conf):
    """Trigger roaming and validate the DUT connected to expected BSSID.

    Args:
      attn_val: Attenuator value.
      expected_conf: expected wifi network.
    """
    expected_con = {
        "SSID": expected_conf["SSID"],
        "BSSID": expected_conf["bssid"]
    }
    wutils.set_attns_steps(self.attenuators, attn_val, self.roaming_attn_6g)
    self.log.info("Wait %ss for roaming to finish.")
    self.dut.droid.wakeLockAcquireBright()
    self.dut.droid.wakeUpNow()
    wutils.verify_wifi_connection_info(self.dut, expected_con)
    expected_bssid = expected_con["BSSID"]
    self.log.info("Roamed to %s successfully", expected_bssid)

  def roaming_between_ap1_and_ap2(self, ap1_network, ap2_network, a_on_b_off,
                                  a_off_b_on):
    """Roaming logic between ap1 and ap2.

    Args:
      ap1_network: Wifi config for AP1.
      ap2_network: Wifi config for AP2.
      a_on_b_off: Attenuator value to roam from b to a.
      a_off_b_on: Attenautor value to roam from a to b.
    """
    ap1 = ap1_network.copy()
    ap1.pop("bssid")
    wutils.set_attns_steps(self.attenuators, a_on_b_off, self.roaming_attn_6g)
    wutils.connect_to_wifi_network(self.dut, ap1)
    self.log.info("Roaming from %s to %s", ap1_network, ap2_network)
    self.trigger_roaming_and_validate(a_off_b_on, ap2_network)
    self.log.info("Roaming from %s to %s", ap2_network, ap1_network)
    self.trigger_roaming_and_validate(a_on_b_off, ap1_network)

  ### Test cases ###

  @test_tracker_info(uuid="805eed41-b596-4d51-9ae5-3cf784b2969a")
  def test_roaming_between_6g_to_6g_sae(self):
    roam_ssid = "test_%s" % "_".join(self.test_name.split("_")[-4:-1])
    self.ap1.configure_ap({"6g": {"security": "sae", "SSID": roam_ssid}})
    ap1_network = self.ap1.get_wifi_network("6g")
    ap1_network["bssid"] = self.bssid_map[0]["6g"]
    self.ap2.configure_ap({"6g": {"security": "sae", "SSID": roam_ssid}})
    ap2_network = self.ap2.get_wifi_network("6g")
    ap2_network["bssid"] = self.bssid_map[1]["6g"]
    self.roaming_between_ap1_and_ap2(ap2_network, ap1_network,
                                     "AP1_6G_OFF_AP2_6G_ON",
                                     "AP1_6G_ON_AP2_6G_OFF")

  @test_tracker_info(uuid="25e8a30e-4bfe-4572-b877-fde48f433668")
  def test_roaming_between_6g_to_6g_owe(self):
    roam_ssid = "test_%s" % "_".join(self.test_name.split("_")[-4:-1])
    self.ap1.configure_ap({"6g": {"security": "owe", "SSID": roam_ssid}})
    ap1_network = self.ap1.get_wifi_network("6g")
    ap1_network["bssid"] = self.bssid_map[0]["6g"]
    self.ap2.configure_ap({"6g": {"security": "owe", "SSID": roam_ssid}})
    ap2_network = self.ap2.get_wifi_network("6g")
    ap2_network["bssid"] = self.bssid_map[1]["6g"]
    self.roaming_between_ap1_and_ap2(ap2_network, ap1_network,
                                     "AP1_6G_OFF_AP2_6G_ON",
                                     "AP1_6G_ON_AP2_6G_OFF")

  @test_tracker_info(uuid="b1aa0057-4887-47fd-b29d-af7aadcdc3ab")
  def test_roaming_between_6g_to_2g_sae(self):
    roam_ssid = "test_%s" % "_".join(self.test_name.split("_")[-4:-1])
    self.ap1.configure_ap({"6g": {"security": "sae", "SSID": roam_ssid}})
    ap1_network = self.ap1.get_wifi_network("6g")
    ap1_network["bssid"] = self.bssid_map[0]["6g"]
    self.ap2.configure_ap({"2g": {"security": "sae", "SSID": roam_ssid}})
    ap2_network = self.ap2.get_wifi_network("2g")
    ap2_network["bssid"] = self.bssid_map[1]["2g"]
    self.roaming_between_ap1_and_ap2(ap1_network, ap2_network,
                                     "AP1_6G_OFF_AP2_2G_ON",
                                     "AP1_6G_ON_AP2_2G_OFF")

  @test_tracker_info(uuid="78318f92-3cf5-49b8-89b0-7969cf631ad3")
  def test_roaming_between_6g_to_5g_sae(self):
    roam_ssid = "test_%s" % "_".join(self.test_name.split("_")[-4:-1])
    self.ap1.configure_ap({"6g": {"security": "sae", "SSID": roam_ssid}})
    ap1_network = self.ap1.get_wifi_network("6g")
    ap1_network["bssid"] = self.bssid_map[0]["6g"]
    self.ap2.configure_ap({"5g": {"security": "sae", "SSID": roam_ssid}})
    ap2_network = self.ap2.get_wifi_network("5g")
    ap2_network["bssid"] = self.bssid_map[1]["5g"]
    self.roaming_between_ap1_and_ap2(ap1_network, ap2_network,
                                     "AP1_6G_OFF_AP2_5G_ON",
                                     "AP1_6G_ON_AP2_5G_OFF")
