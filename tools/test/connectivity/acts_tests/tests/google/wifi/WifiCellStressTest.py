#!/usr/bin/env python3.4
#
#   Copyright 2022 - The Android Open Source Project
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

import time

import acts.base_test
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
import acts_contrib.test_utils.tel.tel_test_utils as tel_utils

from acts import asserts
from acts import signals
from acts import utils
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.tel.tel_test_utils import WIFI_CONFIG_APBAND_2G
from acts_contrib.test_utils.tel.tel_test_utils import WIFI_CONFIG_APBAND_5G

WifiEnums = wutils.WifiEnums

DEFAULT_TIMEOUT = 10
PING_ADDR = 'www.google.com'


class WifiCellStressTest(WifiBaseTest):
    """WiFi Cell Data Stress test class.

    Test Bed Requirement:
    * Two Android device and one of them with a SIM card
    * Several Wi-Fi networks visible to the device, including an open Wi-Fi
      network.
    """

    def __init__(self, configs):
        super().__init__(configs)
        self.enable_packet_log = True

    def setup_class(self):
        super().setup_class()
        self.dut = self.android_devices[0]
        self.dut_client = self.android_devices[1]
        wutils.wifi_test_device_init(self.dut)
        req_params = []
        opt_param = [
            "open_network", "reference_networks", "iperf_server_address",
            "stress_count", "stress_hours", "attn_vals", "pno_interval",
            "iperf_server_port", "dbs_supported_models",
            "sta_sta_supported_models"
        ]

        self.unpack_userparams(req_param_names=req_params,
                               opt_param_names=opt_param)
        self.ap_iface = 'wlan0'
        if self.dut.model in self.dbs_supported_models:
            self.ap_iface = 'wlan1'
        if self.dut.model in self.sta_sta_supported_models:
            self.ap_iface = 'wlan2'

        if "AccessPoint" in self.user_params:
            self.legacy_configure_ap_and_start(ap_count=2)
        elif "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(open_network=True,
                                                wpa_network=True,
                                                ap_count=2)

    def setup_test(self):
        super().setup_test()
        self.dut.droid.wakeLockAcquireBright()
        self.dut.droid.wakeUpNow()
        wutils.wifi_toggle_state(self.dut_client, True)
        init_sim_state = tel_utils.is_sim_ready(self.log, self.dut)
        if init_sim_state:
            self.check_cell_data_and_enable()

    def teardown_test(self):
        super().teardown_test()
        if self.dut.droid.wifiIsApEnabled():
            wutils.stop_wifi_tethering(self.dut)
        self.dut.droid.wakeLockRelease()
        self.dut.droid.goToSleepNow()
        wutils.reset_wifi(self.dut)
        wutils.reset_wifi(self.dut_client)
        self.log.debug("Toggling Airplane mode OFF")
        asserts.assert_true(
            acts.utils.force_airplane_mode(self.dut, False),
            "Can not turn airplane mode off: %s" % self.dut.serial)

    def teardown_class(self):
        wutils.reset_wifi(self.dut)
        if "AccessPoint" in self.user_params:
            del self.user_params["reference_networks"]
            del self.user_params["open_network"]

    """Helper Functions"""

    def check_cell_data_and_enable(self):
        """Make sure that cell data is enabled if there is a sim present.

        If a sim is active, cell data needs to be enabled to allow provisioning
        checks through (when applicable).  This is done to relax hardware
        requirements on DUTs - without this check, running this set of tests
        after other wifi tests may cause failures.
        """
        if not self.dut.droid.telephonyIsDataEnabled():
            self.dut.log.info("need to enable data")
            self.dut.droid.telephonyToggleDataConnection(True)
            asserts.assert_true(self.dut.droid.telephonyIsDataEnabled(),
                                "Failed to enable cell data for dut.")

    def run_ping(self, sec):
        """Run ping for given number of seconds.

        Args:
            sec: Time in seconds to run the ping traffic.

        """
        self.log.info("Running ping for %d seconds" % sec)
        result = self.dut.adb.shell("ping -w %d %s" % (sec, PING_ADDR),
                                    timeout=sec + 1)
        self.log.debug("Ping Result = %s" % result)
        if "100% packet loss" in result:
            raise signals.TestFailure("100% packet loss during ping")

    def create_softap_config(self):
        """Create a softap config with ssid and password."""
        ap_ssid = "softap_" + utils.rand_ascii_str(8)
        ap_password = utils.rand_ascii_str(8)
        self.dut.log.info("softap setup: %s %s", ap_ssid, ap_password)
        config = {wutils.WifiEnums.SSID_KEY: ap_ssid}
        config[wutils.WifiEnums.PWD_KEY] = ap_password
        return config

    def check_softap_under_airplane_mode_with_sim(self, band):
        """Create a softap on/off under airplane mode with sim """
        self.log.debug("Toggling Airplane mode ON")
        asserts.assert_true(
            acts.utils.force_airplane_mode(self.dut, True),
            "Can not turn on airplane mode on: %s" % self.dut.serial)
        time.sleep(DEFAULT_TIMEOUT)
        for count in range(self.stress_count):
            """Test toggling softap"""
            self.log.info("Iteration %d", count + 1)
            softap_config = wutils.create_softap_config()
            wutils.start_wifi_tethering(
                self.dut, softap_config[wutils.WifiEnums.SSID_KEY],
                softap_config[wutils.WifiEnums.PWD_KEY], band)
            config = {
                "SSID": softap_config[wutils.WifiEnums.SSID_KEY],
                "password": softap_config[wutils.WifiEnums.PWD_KEY]
            }
            wutils.stop_wifi_tethering(self.dut)
        self.log.debug("Toggling Airplane mode OFF")
        asserts.assert_true(
            acts.utils.force_airplane_mode(self.dut, False),
            "Can not turn off airplane mode on: %s" % self.dut.serial)
        self.check_cell_data_and_enable()
        softap_config = wutils.create_softap_config()
        wutils.start_wifi_tethering(self.dut,
                                    softap_config[wutils.WifiEnums.SSID_KEY],
                                    softap_config[wutils.WifiEnums.PWD_KEY],
                                    band)
        config = {
            "SSID": softap_config[wutils.WifiEnums.SSID_KEY],
            "password": softap_config[wutils.WifiEnums.PWD_KEY]
        }
        wutils.wifi_toggle_state(self.dut_client, True)
        wutils.connect_to_wifi_network(self.dut_client,
                                       config,
                                       check_connectivity=False)
        # Ping the DUT
        dut_addr = self.dut.droid.connectivityGetIPv4Addresses(
            self.ap_iface)[0]
        asserts.assert_true(
            utils.adb_shell_ping(self.dut_client,
                                 count=10,
                                 dest_ip=dut_addr,
                                 timeout=20),
            "%s ping %s failed" % (self.dut_client.serial, dut_addr))
        wutils.wifi_toggle_state(self.dut_client, True)
        wutils.stop_wifi_tethering(self.dut)

    """Tests"""

    @test_tracker_info(uuid="f48609e7-7cb4-4dcf-8a39-2f1ea7301740")
    def test_2g_hotspot_on_off_under_airplane_mode_with_SIM(self):
        """Tests followed by turn on/off SoftAp on 2G with Cell Data enable
        under airplane mode

        """
        self.check_softap_under_airplane_mode_with_sim(WIFI_CONFIG_APBAND_2G)

    @test_tracker_info(uuid="08744735-6d5f-47e5-96b2-af9ecd40597d")
    def test_5g_hotspot_on_off_under_airplane_mode_with_SIM(self):
        """Tests followed by turn on/off SoftAp on 5G with Cell Data enable
        under airplane mode

        """
        self.check_softap_under_airplane_mode_with_sim(WIFI_CONFIG_APBAND_5G)
