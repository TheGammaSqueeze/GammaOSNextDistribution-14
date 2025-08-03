#!/usr/bin/env python3.4
#
#   Copyright 2019 - The Android Open Source Project
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

import acts_contrib.test_utils.wifi.wifi_test_utils as wutils

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest


class WifiWpa3OweTest(WifiBaseTest):
    """Tests for APIs in Android's WifiManager class.

    Test Bed Requirement:
    * At least one Android device and atleast two Access Points.
    * Several Wi-Fi networks visible to the device.
    """
    def __init__(self, configs):
        super().__init__(configs)
        self.enable_packet_log = True

    def setup_class(self):
        super().setup_class()

        self.dut = self.android_devices[0]
        opt_params = ["owe_networks", "sae_networks"]
        req_params = ["wpa3_sae_gcmp_128", "wpa3_sae_gcmp_256", "wifi6_models"]
        self.unpack_userparams(opt_param_names=opt_params,
                               req_param_names=req_params)
        if "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(owe_network=True,
                                                sae_network=True)
        self.owe_2g = self.owe_networks[0]["2g"]
        self.owe_5g = self.owe_networks[0]["5g"]
        self.wpa3_personal_2g = self.sae_networks[0]["2g"]
        self.wpa3_personal_5g = self.sae_networks[0]["5g"]

    def setup_test(self):
        super().setup_test()
        for ad in self.android_devices:
            ad.droid.wakeLockAcquireBright()
            ad.droid.wakeUpNow()
            wutils.wifi_toggle_state(ad, True)

    def teardown_test(self):
        super().teardown_test()
        for ad in self.android_devices:
            ad.droid.wakeLockRelease()
            ad.droid.goToSleepNow()
        wutils.reset_wifi(self.dut)

    ### Test cases ###

    @test_tracker_info(uuid="a7755f1f-5740-4d45-8c29-3711172b1bd7")
    def test_connect_to_owe_2g(self):
        wutils.connect_to_wifi_network(self.dut, self.owe_2g)

    @test_tracker_info(uuid="9977765e-03da-4614-ab96-4c1597101118")
    def test_connect_to_owe_5g(self):
        wutils.connect_to_wifi_network(self.dut, self.owe_5g)

    @test_tracker_info(uuid="3670702a-3d78-4184-b5e1-7fcf5fa48fd8")
    def test_connect_to_wpa3_personal_2g(self):
        wutils.connect_to_wifi_network(self.dut, self.wpa3_personal_2g)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="c4528eaf-7960-4ecd-8f11-d5439bdf1c58")
    def test_connect_to_wpa3_personal_5g(self):
        wutils.connect_to_wifi_network(self.dut, self.wpa3_personal_5g)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="a8fb46be-3487-4dc8-a393-5af992b27f45")
    def test_connect_to_wpa3_personal_reconnection(self):
        """ This is to catch auth reject which is caused by PMKSA cache.
            Steps:
            ------------------------------
            1. Connect STA to WPA3 AP
            2. Turn off the WiFi or connect to a different AP
            3. Turn on the WiFi or connect back to WPA3 AP.
            4. Initial connect request fails
               Second connect request from framework succeeds.
        """
        wutils.connect_to_wifi_network(self.dut, self.wpa3_personal_2g)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)
        wutils.toggle_wifi_off_and_on(self.dut)
        wutils.connect_to_wifi_network(self.dut, self.wpa3_personal_2g)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    @test_tracker_info(uuid="b1502202-10c3-4834-a899-5023947fb21d")
    def test_connect_to_wpa3_personal_gcmp_128(self):
        """Test connect to WPA3 SAE GCMP 128."""
        wutils.connect_to_wifi_network(self.dut, self.wpa3_sae_gcmp_128)

    @test_tracker_info(uuid="4d8c3c63-75bf-4131-bb07-fe3f6020389c")
    def test_connect_to_wpa3_personal_gcmp_256(self):
        """Test connect to WPA3 SAE GCMP 256."""
        wutils.connect_to_wifi_network(self.dut, self.wpa3_sae_gcmp_256)
