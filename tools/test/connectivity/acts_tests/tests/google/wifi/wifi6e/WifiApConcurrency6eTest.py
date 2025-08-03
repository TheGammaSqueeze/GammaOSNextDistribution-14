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
from acts import asserts
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest

WifiEnums = wutils.WifiEnums
BRIDGED_AP_LAUNCH_INTERVAL_5_SECONDS = 5


class WifiApConcurrency6eTest(WifiBaseTest):
  """Tests for network selector 6e tests.

  Test Bed Requirement:
    1 Android devices, 2 Asus AXE11000 Access Point.
  """

  def setup_class(self):
    super().setup_class()

    self.dut = self.android_devices[0]
    req_params = ["reference_networks",]
    self.unpack_userparams(req_param_names=req_params,)
    self.ap1 = self.reference_networks[0]["6g"]

  def teardown_test(self):
    super().teardown_test()
    if self.dut.droid.wifiIsApEnabled():
      wutils.stop_wifi_tethering(self.dut)
    for ad in self.android_devices:
      wutils.reset_wifi(ad)

  @test_tracker_info(uuid="6f776b4a-b080-4b52-a330-52aa641b18f2")
  def test_ap_concurrency_band_2_and_5_after_connecting_to_6g(self):
    """Test AP concurrency behavior after connecting to 6g.

    Steps:
      1. Start softap in 2g and 5g bands.
      2. Connect to 6g wifi network.
      3. Verify softap on band 5g turns off.
    """
    # Enable bridged AP
    config = wutils.create_softap_config()
    config[WifiEnums.SECURITY] = WifiEnums.SoftApSecurityType.WPA3_SAE
    wutils.save_wifi_soft_ap_config(
        self.dut,
        config,
        bands=[
            WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G,
            WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G
        ])
    wutils.start_wifi_tethering_saved_config(self.dut)
    time.sleep(BRIDGED_AP_LAUNCH_INTERVAL_5_SECONDS)  # wait 5 seconds.

    # Make sure 2 instances enabled, and get BSSIDs from BridgedAp Infos.
    callback_id = self.dut.droid.registerSoftApCallback()
    infos = wutils.get_current_softap_infos(self.dut, callback_id, True)
    self.log.info("INFOs: %s" % infos)
    self.dut.droid.unregisterSoftApCallback(callback_id)
    asserts.assert_true(
        len(infos) < 2, "Found %s softap instances. Expected 2." % len(infos))

    # Connect to 6g network.
    wutils.connect_to_wifi_network(self.dut, self.ap1)

    # Verify 5g softap is turned off.
    callback_id = self.dut.droid.registerSoftApCallback()
    infos = wutils.get_current_softap_infos(self.dut, callback_id, True)
    self.log.info("INFOs: %s" % infos)
    self.dut.droid.unregisterSoftApCallback(callback_id)
    asserts.assert_true(
        len(infos) == 1, "Found %s softap instances. Expected 1." % len(infos))
    asserts.assert_true(
        infos[0]["frequency"] < 5000, "5g softap is turned off.")
