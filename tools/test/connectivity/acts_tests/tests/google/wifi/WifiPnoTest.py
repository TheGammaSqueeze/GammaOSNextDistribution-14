#
#   Copyright 2014 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import logging
import time

from acts import asserts
from acts.test_decorators import test_tracker_info
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest

WifiEnums = wutils.WifiEnums
MAX_ATTN = 95
WAIT_WIFI_SCAN_RESULTS_SEC = 10
WAIT_ATTENUATION_SEC = 5
WAIT_WIFI_DISCONNECT_SEC = 60

class WifiPnoTest(WifiBaseTest):

    def __init__(self, configs):
        super().__init__(configs)
        self.enable_packet_log = True

    def setup_class(self):
        super().setup_class()

        self.dut = self.android_devices[0]
        wutils.wifi_test_device_init(self.dut)
        req_params = ["attn_vals", "pno_interval", "wifi6_models"]
        opt_param = ["reference_networks"]
        self.unpack_userparams(
            req_param_names=req_params, opt_param_names=opt_param)

        if "AccessPoint" in self.user_params:
            self.legacy_configure_ap_and_start()
        elif "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(wpa_network=True, ap_count=2)
            for i in range(len(self.user_params["OpenWrtAP"])):
              self.openwrt = self.access_points[i]
              logging.info(
                f"AP{i+1}: {self.openwrt.get_bssids_for_wifi_networks()}")
        self.pno_network_a = self.reference_networks[0]['2g']
        self.pno_network_b = self.reference_networks[0]['5g']
        if "OpenWrtAP" in self.user_params:
            self.pno_network_b = self.reference_networks[1]['5g']
        self.attn_a = self.attenuators[0]
        self.attn_b = self.attenuators[1]
        # Disable second AP's networks, so that it does not interfere during PNO
        self.attenuators[2].set_atten(MAX_ATTN)
        self.attenuators[3].set_atten(MAX_ATTN)
        self.set_attns("default")
        # Scan for WiFi networks right after APs launched and attenuators set.
        time.sleep(WAIT_ATTENUATION_SEC)
        wutils.list_scan_results(self.dut)

    def setup_test(self):
        super().setup_test()
        self.dut.droid.wifiStartTrackingStateChange()
        self.dut.droid.wakeLockRelease()
        self.dut.droid.goToSleepNow()
        wutils.reset_wifi(self.dut)
        self.dut.ed.clear_all_events()

    def teardown_test(self):
        super().teardown_test()
        self.dut.droid.wifiStopTrackingStateChange()
        wutils.reset_wifi(self.dut)
        self.dut.ed.clear_all_events()
        self.set_attns("default")

    def teardown_class(self):
        if "AccessPoint" in self.user_params:
            del self.user_params["reference_networks"]
            del self.user_params["open_network"]

    """Helper Functions"""

    def set_attns(self, attn_val_name):
        """Sets attenuation values on attenuators used in this test.

        Args:
            attn_val_name: Name of the attenuation value pair to use.
        """
        self.log.info("Set attenuation values to %s",
                      self.attn_vals[attn_val_name])
        try:
            self.attn_a.set_atten(self.attn_vals[attn_val_name][0])
            self.attn_b.set_atten(self.attn_vals[attn_val_name][1])
        except:
            self.log.error("Failed to set attenuation values %s.",
                           attn_val_name)
            raise

    def trigger_pno_and_assert_connect(self, ad, attn_val_name, expected_con):
        """Trigger PNO and verify the connection after PNO.

        Args:
            ad: Android Device to trigger PNO on.
            attn_val_name: Name of the attenuation value pair to use.
            expected_con: The expected info of the network to we expect the DUT
                to connect to.
        """
        connection_info = ad.droid.wifiGetConnectionInfo()

        # Stops APs to force DUT to disconnect and restart APs.
        for i in range(len(self.user_params["OpenWrtAP"])):
            self.openwrt = self.access_points[i]
            self.openwrt.stop_ap()

        wutils.wait_for_disconnect(self.dut,
                                   timeout=WAIT_WIFI_DISCONNECT_SEC)

        for i in range(len(self.user_params["OpenWrtAP"])):
            self.openwrt = self.access_points[i]
            self.openwrt.start_ap()

        self.set_attns(attn_val_name)

        ad.log.info("Wait %ss for triggering PNO scan, connect from %s to %s.",
                    self.pno_interval,
                    connection_info[WifiEnums.SSID_KEY],
                    expected_con[WifiEnums.SSID_KEY])
        time.sleep(self.pno_interval)

        try:
            ad.log.info("Expect it's connected to %s after PNO interval"
                        % ad.droid.wifiGetConnectionInfo()[WifiEnums.SSID_KEY])
            expected_ssid = expected_con[WifiEnums.SSID_KEY]
            verify_con = {WifiEnums.SSID_KEY: expected_ssid}
            wutils.verify_wifi_connection_info(ad, verify_con)
            ad.log.info("Connected to %s successfully after PNO",
                          expected_ssid)
            wutils.verify_11ax_wifi_connection(
                ad, self.wifi6_models, "wifi6_ap" in self.user_params)
        finally:
            pass

    def add_and_enable_test_networks(self, num_networks):
        """Add some test networks to the device and enable them.

        Args:
            num_networks: Number of networks to add.
        """
        ssid_name_base = "pno_test_network_"
        for i in range(0, num_networks):
            network = {}
            network[WifiEnums.SSID_KEY] = ssid_name_base + str(i)
            network[WifiEnums.PWD_KEY] = "pno_test"
            self.add_network_and_enable(network)

    def add_network_and_enable(self, network):
        """Add a network and enable it.

        Args:
            network : Network details for the network to be added.

        """
        ret = self.dut.droid.wifiAddNetwork(network)
        asserts.assert_true(ret != -1, "Add network %r failed" % network)
        self.dut.droid.wifiEnableNetwork(ret, 0)


    """ Tests Begin """

    @test_tracker_info(uuid="33d3cae4-5fa7-4e90-b9e2-5d3747bba64c")
    def test_simple_pno_connection_5g_to_2g(self):
        """Test PNO triggered autoconnect to a network.

        Steps:
        1. Puts the DUT to sleep.
        2. DUT connects to a 2G(a) DUT and a 5G(b) network so they will be
           saved network and won't be excluded from PNO scan.
        3. Stops APs to force DUT to disconnect and restart APs.
        4. Attenuates to (2G in range, 5G out of range).
        5. Waits for 120 seconds PNO interval.
        6. Checks the device connected to 2G network automatically.
        """
        wutils.connect_to_wifi_network(self.dut, self.pno_network_a)
        wutils.connect_to_wifi_network(self.dut, self.pno_network_b)
        self.trigger_pno_and_assert_connect(self.dut,
                                            "a_on_b_off",
                                            self.pno_network_a)

    @test_tracker_info(uuid="39b945a1-830f-4f11-9e6a-9e9641066a96")
    def test_simple_pno_connection_2g_to_5g(self):
        """Test PNO triggered autoconnect to a network.

        Steps:
        1. Puts the DUT to sleep.
        2. DUT connects to a 5G(b) DUT and a 2G(a) network so they will be
           saved network and won't be excluded from PNO scan.
        3. Stops APs to force DUT to disconnect  from WiFi and restart APs.
        4. Attenuates to (5G in range, 2G out of range).
        5. Waits for 120 seconds PNO interval.
        6. Checks the device connected to 5G network automatically.
        """
        # DUT connects to the saved networks so they won't be excluded from PNO scan.
        wutils.connect_to_wifi_network(self.dut, self.pno_network_b)
        wutils.connect_to_wifi_network(self.dut, self.pno_network_a)
        self.trigger_pno_and_assert_connect(self.dut,
                                            "b_on_a_off",
                                            self.pno_network_b)

    @test_tracker_info(uuid="844b15be-ff45-4b09-a11b-0b2b4bb13b22")
    def test_pno_connection_with_multiple_saved_networks(self):
        """Test autoconnect with multiple saved networks after PNO.

        Test PNO triggered autoconnect to a network when there are more
        than 16 networks saved in the device.

        16 is the max list size of PNO watch list for most devices. The device
        should automatically pick the 16 most recently connected networks.
        For networks that were never connected, the networks seen in the
        previous scan result would have higher priority.

        Steps:
        1. Puts the DUt to sleep.
        2. Saves 16 test network configurations in the device.
        3. DUT connects to a 5G(b) DUT and a 2G(a) network so they will be
           saved network and won't be excluded from PNO scan.
        4. Stops APs to force DUT to disconnect  from WiFi and restart APs.
        5. Attenuates to (5G in range, 2G out of range).
        6. Waits for 120 seconds PNO interval.
        7. Checks the device connected to 5G network automatically.
        """
        self.add_and_enable_test_networks(16)
        # DUT connects to the saved networks so they won't be excluded from PNO scan.
        wutils.connect_to_wifi_network(self.dut, self.pno_network_b)
        wutils.connect_to_wifi_network(self.dut, self.pno_network_a)
        # Force single scan so that both networks become preferred before PNO.
        wutils.start_wifi_connection_scan_and_return_status(self.dut)
        time.sleep(10)
        self.trigger_pno_and_assert_connect(self.dut,
                                            "b_on_a_off",
                                            self.pno_network_b)

    """ Tests End """
