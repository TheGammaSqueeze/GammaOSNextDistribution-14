#!/usr/bin/env python3.4
#
#   Copyright 2018 - The Android Open Source Project
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
import acts_contrib.test_utils.tel.tel_test_utils as tutils

from acts import asserts
from acts import signals
from acts import utils
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.bt.bt_test_utils import enable_bluetooth
from acts_contrib.test_utils.bt.bt_test_utils import pair_pri_to_sec
from acts_contrib.test_utils.bt.bt_test_utils import clear_bonded_devices
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.tel.tel_test_utils import WIFI_CONFIG_APBAND_2G
from acts_contrib.test_utils.tel.tel_test_utils import WIFI_CONFIG_APBAND_5G

WifiEnums = wutils.WifiEnums

WAIT_FOR_AUTO_CONNECT = 40
WAIT_BEFORE_CONNECTION = 30
DEFAULT_TIMEOUT = 10
PING_ADDR = 'www.google.com'
BAND_2GHZ = 0
BAND_5GHZ = 1
WIFI_NETWORK_AP_CHANNEL_2G = 1
WIFI_NETWORK_AP_CHANNEL_5G = 36


class WifiBtStressCoexTest(WifiBaseTest):
    """WiFi BT Coex Stress test class.

    Test Bed Requirement:
    * Two Android device
    * One Android device with simulator BT branch code
    * Several Wi-Fi networks visible to the device, including an open Wi-Fi
      network.
    """

    def __init__(self, configs):
        super().__init__(configs)
        self.enable_packet_log = True

    def setup_class(self):
        super().setup_class()

        self.dut = self.android_devices[0]
        if len(self.android_devices) > 1:
            self.dut_client = self.android_devices[1]
            self.headset = self.android_devices[2]
        else:
            self.dut_client = None
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
        asserts.assert_true(
            len(self.reference_networks) > 0,
            "Need at least one reference network with psk.")
        self.wpa_2g = self.reference_networks[0]["2g"]
        self.wpa_5g = self.reference_networks[0]["5g"]
        self.open_2g = self.open_network[0]["2g"]
        self.open_5g = self.open_network[0]["5g"]
        self.networks = [self.wpa_2g, self.wpa_5g, self.open_2g, self.open_5g]

    def setup_test(self):
        super().setup_test()
        self.dut.droid.wakeLockAcquireBright()
        self.dut.droid.wakeUpNow()
        wutils.wifi_toggle_state(self.dut_client, True)

    def teardown_test(self):
        super().teardown_test()
        if self.dut.droid.wifiIsApEnabled():
            wutils.stop_wifi_tethering(self.dut)
        self.dut.droid.wakeLockRelease()
        self.dut.droid.goToSleepNow()
        wutils.reset_wifi(self.dut)
        wutils.reset_wifi(self.dut_client)
        clear_bonded_devices(self.dut)
        clear_bonded_devices(self.headset)

    def teardown_class(self):
        wutils.reset_wifi(self.dut)
        if "AccessPoint" in self.user_params:
            del self.user_params["reference_networks"]
            del self.user_params["open_network"]

    """Helper Functions"""

    def scan_and_connect_by_ssid(self, ad, network):
        """Scan for network and connect using network information.

        Args:
            network: A dictionary representing the network to connect to.

        """
        ssid = network[WifiEnums.SSID_KEY]
        wutils.start_wifi_connection_scan_and_ensure_network_found(ad, ssid)
        wutils.wifi_connect(ad, network, num_of_tries=3)

    def scan_and_connect_by_id(self, network, net_id):
        """Scan for network and connect using network id.

        Args:
            net_id: Integer specifying the network id of the network.

        """
        ssid = network[WifiEnums.SSID_KEY]
        wutils.start_wifi_connection_scan_and_ensure_network_found(
            self.dut, ssid)
        wutils.wifi_connect_by_id(self.dut, net_id)

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

    def start_softap_and_verify(self, band, check_connectivity=True):
        """Test startup of softap.

        1. Bring up AP mode.
        2. Verify SoftAP active using the client device.

        Args:
            band: wifi band to start soft ap on
            check_connectivity: If set, verify internet connectivity

        Returns:
            config
        """
        config = self.create_softap_config()
        wutils.start_wifi_tethering(self.dut, config[WifiEnums.SSID_KEY],
                                    config[WifiEnums.PWD_KEY], band)
        for ad in self.android_devices[1:]:
            wutils.connect_to_wifi_network(
                ad, config, check_connectivity=check_connectivity)
        return config

    def verify_softap_full_on_off(self, network, softap_band):
        """Bring up AP mode and verify ap mode

        Args:
            softap_band: wifi band to start soft ap on
            network: android_device to connect ap with security type

        """
        self.start_softap_and_verify(softap_band)
        wutils.stop_wifi_tethering(self.dut)

    def connect_BT_paired(self, dut, headset):
        """Start to pairing the simulator BT device.

        Args:
            dut: Android device initiating connection
            headset: Android device accepting connection

        """
        enable_bluetooth(dut.droid, dut.ed)
        enable_bluetooth(headset.droid, headset.ed)
        time.sleep(DEFAULT_TIMEOUT)
        pair_pri_to_sec(dut, headset)

    """Tests"""

    @test_tracker_info(uuid="4bacb48d-1e31-4561-b7b3-8e86478a82b3")
    def test_wifi_on_off_with_2g(self):
        """Test wifi on/off state by connection to 2G network followed
           with BT paired.
        """
        wutils.wifi_toggle_state(self.dut, True)
        self.scan_and_connect_by_ssid(self.dut, self.wpa_2g)
        self.connect_BT_paired(self.dut, self.headset)
        time.sleep(5)
        for count in range(self.stress_count):
            """Test toggling wifi"""
            try:
                self.log.debug("Going from on to off.")
                wutils.wifi_toggle_state(self.dut, False)
                self.log.debug("Going from off to on.")
                startTime = time.time()
                wutils.wifi_toggle_state(self.dut, True)
                startup_time = time.time() - startTime
                self.log.debug("WiFi was enabled on the device in %s s." %
                               startup_time)
            except:
                raise signals.TestFailure(details="",
                                          extras={
                                              "Iterations":
                                              "%d" % self.stress_count,
                                              "Pass": "%d" % count
                                          })
        raise signals.TestPass(details="",
                               extras={
                                   "Iterations": "%d" % self.stress_count,
                                   "Pass": "%d" % (count + 1)
                               })

    @test_tracker_info(uuid="ed5b77ff-8a64-4ea7-a481-96ad3a3b91d0")
    def test_wifi_on_off_with_5g(self):
        """Test wifi on/off state by connection to 5G network followed
           with BT paired.
        """
        wutils.wifi_toggle_state(self.dut, True)
        self.scan_and_connect_by_ssid(self.dut, self.wpa_5g)
        self.connect_BT_paired(self.dut, self.headset)
        time.sleep(5)
        for count in range(self.stress_count):
            """Test toggling wifi"""
            try:
                self.log.debug("Going from on to off.")
                wutils.wifi_toggle_state(self.dut, False)
                self.log.debug("Going from off to on.")
                startTime = time.time()
                wutils.wifi_toggle_state(self.dut, True)
                startup_time = time.time() - startTime
                self.log.debug("WiFi was enabled on the device in %s s." %
                               startup_time)
            except:
                raise signals.TestFailure(details="",
                                          extras={
                                              "Iterations":
                                              "%d" % self.stress_count,
                                              "Pass": "%d" % count
                                          })
        raise signals.TestPass(details="",
                               extras={
                                   "Iterations": "%d" % self.stress_count,
                                   "Pass": "%d" % (count + 1)
                               })

    @test_tracker_info(uuid="cc132d79-d0ea-4f99-ba6f-0f6fbd21eba0")
    def test_2g_sta_mode_and_hotspot_5g_on_off_stress_bt_paired(self):
        """Tests connect to 2G network followed by SoftAp on 5G
           with BT paired.
        """
        wutils.wifi_toggle_state(self.dut, True)
        self.scan_and_connect_by_ssid(self.dut, self.open_2g)
        self.connect_BT_paired(self.dut, self.headset)
        time.sleep(5)
        for count in range(self.stress_count):
            """Test toggling softap"""
            self.log.info("Iteration %d", count + 1)
            self.verify_softap_full_on_off(self.open_2g, WIFI_CONFIG_APBAND_5G)

    @test_tracker_info(uuid="de3903ec-9484-4302-814a-2027631d6ddb")
    def test_2g_sta_mode_and_hotspot_2g_on_off_stress_bt_paired(self):
        """Tests connect to 2G network followed by SoftAp on 2G
           with BT paired.
        """
        wutils.wifi_toggle_state(self.dut, True)
        self.scan_and_connect_by_ssid(self.dut, self.open_2g)
        self.connect_BT_paired(self.dut, self.headset)
        time.sleep(5)
        for count in range(self.stress_count):
            """Test toggling softap"""
            self.log.info("Iteration %d", count + 1)
            self.verify_softap_full_on_off(self.open_2g, WIFI_CONFIG_APBAND_2G)

    @test_tracker_info(uuid="42a27ff4-aeb7-45f1-909b-e9695035fb95")
    def test_5g_sta_mode_and_hotspot_2g_on_off_stress_bt_paired(self):
        """Tests connect to 5G network followed by SoftAp on 2G
           with BT paired.
        """
        wutils.wifi_toggle_state(self.dut, True)
        self.scan_and_connect_by_ssid(self.dut, self.open_5g)
        self.connect_BT_paired(self.dut, self.headset)
        time.sleep(5)
        for count in range(self.stress_count):
            """Test toggling softap"""
            self.log.info("Iteration %d", count + 1)
            self.verify_softap_full_on_off(self.open_2g, WIFI_CONFIG_APBAND_2G)

    @test_tracker_info(uuid="bae3c34b-34dd-47e4-8a6b-3b2dd97e169e")
    def test_5g_sta_mode_and_hotspot_5g_on_off_stress_bt_paired(self):
        """Tests connect to 5G network followed by SoftAp on 5G
           with BT paired.
        """
        wutils.wifi_toggle_state(self.dut, True)
        self.scan_and_connect_by_ssid(self.dut, self.open_5g)
        self.connect_BT_paired(self.dut, self.headset)
        time.sleep(5)
        for count in range(self.stress_count):
            """Test toggling softap"""
            self.log.info("Iteration %d", count + 1)
            self.verify_softap_full_on_off(self.open_2g, WIFI_CONFIG_APBAND_5G)

    @test_tracker_info(uuid="4f39304b-18a1-4c54-8fa1-072ef3c1688d")
    def test_2g_hotspot_on_off_with_bt_paired(self):
        """Tests followed by turn on/off SoftAp on 2G with BT paired.
        """
        wutils.wifi_toggle_state(self.dut, True)
        self.connect_BT_paired(self.dut, self.headset)
        time.sleep(5)
        for count in range(self.stress_count):
            """Test toggling softap"""
            self.log.info("Iteration %d", count + 1)
            softap_config = wutils.create_softap_config()
            wutils.start_wifi_tethering(
                self.dut, softap_config[wutils.WifiEnums.SSID_KEY],
                softap_config[wutils.WifiEnums.PWD_KEY],
                wutils.WifiEnums.WIFI_CONFIG_APBAND_2G)
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

    @test_tracker_info(uuid="a6b1fa3d-be1f-4039-807b-4f117681b2dc")
    def test_5g_hotspot_on_off_with_bt_paired(self):
        """Tests followed by turn on/off SoftAp on 5G with BT paired.
        """
        wutils.wifi_toggle_state(self.dut, True)
        self.connect_BT_paired(self.dut, self.headset)
        time.sleep(5)
        for count in range(self.stress_count):
            """Test toggling softap"""
            self.log.info("Iteration %d", count + 1)
            softap_config = wutils.create_softap_config()
            wutils.start_wifi_tethering(
                self.dut, softap_config[wutils.WifiEnums.SSID_KEY],
                softap_config[wutils.WifiEnums.PWD_KEY],
                wutils.WifiEnums.WIFI_CONFIG_APBAND_5G)
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
