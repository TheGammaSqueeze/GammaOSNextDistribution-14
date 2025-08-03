#!/usr/bin/env python3.4
#
#   Copyright 2016 - The Android Open Source Project
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

import os
import time

from acts import asserts
from acts import utils
from acts.keys import Config
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel import tel_test_utils as tel_utils
from acts_contrib.test_utils.wifi import wifi_constants
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest

WifiEnums = wutils.WifiEnums
WIFI_CONFIG_APBAND_2G = WifiEnums.WIFI_CONFIG_APBAND_2G
WIFI_CONFIG_APBAND_5G = WifiEnums.WIFI_CONFIG_APBAND_5G
WIFI_CONFIG_APBAND_AUTO = WifiEnums.WIFI_CONFIG_APBAND_AUTO
WPA3_SAE_TRANSITION_SOFTAP = WifiEnums.SoftApSecurityType.WPA3_SAE_TRANSITION
WPA3_SAE_SOFTAP = WifiEnums.SoftApSecurityType.WPA3_SAE
WAIT_AFTER_REBOOT = 10


class WifiSoftApTest(WifiBaseTest):

    def setup_class(self):
        """It will setup the required dependencies from config file and configure
           the devices for softap mode testing.

        Returns:
            True if successfully configured the requirements for testing.
        """
        super().setup_class()
        self.dut = self.android_devices[0]
        self.dut_client = self.android_devices[1]
        req_params = ["dbs_supported_models", "sta_concurrency_supported_models",
                      "sap_wpa3_supported_models", "wifi6_models", "sim_supported_models"]
        opt_param = ["reference_networks"]
        self.unpack_userparams(
            req_param_names=req_params, opt_param_names=opt_param)
        if "AccessPoint" in self.user_params:
            self.legacy_configure_ap_and_start()
        elif "OpenWrtAP" in self.user_params:
            self.configure_openwrt_ap_and_start(wpa_network=True)
        self.wifi_network = self.reference_networks[0]["2g"]
        # Do a simple version of init - mainly just sync the time and enable
        # verbose logging.  This test will fail if the DUT has a sim and cell
        # data is disabled.  We would also like to test with phones in less
        # constrained states (or add variations where we specifically
        # constrain).
        utils.require_sl4a((self.dut, self.dut_client))
        utils.sync_device_time(self.dut)
        utils.sync_device_time(self.dut_client)
        # Enable verbose logging on the duts
        self.dut.droid.wifiEnableVerboseLogging(1)
        asserts.assert_equal(self.dut.droid.wifiGetVerboseLoggingLevel(), 1,
            "Failed to enable WiFi verbose logging on the softap dut.")
        self.dut_client.droid.wifiEnableVerboseLogging(1)
        asserts.assert_equal(self.dut_client.droid.wifiGetVerboseLoggingLevel(), 1,
            "Failed to enable WiFi verbose logging on the client dut.")
        wutils.wifi_toggle_state(self.dut, True)
        wutils.wifi_toggle_state(self.dut_client, True)
        self.AP_IFACE = 'wlan0'
        if self.dut.model in self.dbs_supported_models:
            self.AP_IFACE = 'wlan1'
        if self.dut.model in self.sta_concurrency_supported_models:
            self.AP_IFACE = 'wlan2'
        if len(self.android_devices) > 2:
            utils.sync_device_time(self.android_devices[2])
            self.android_devices[2].droid.wifiEnableVerboseLogging(1)
            asserts.assert_equal(self.android_devices[2].droid.wifiGetVerboseLoggingLevel(), 1,
                "Failed to enable WiFi verbose logging on the client dut.")
            self.dut_client_2 = self.android_devices[2]
        self.country_code = wutils.WifiEnums.CountryCode.US
        if hasattr(self, "country_code_file"):
            if isinstance(self.country_code_file, list):
                self.country_code_file = self.country_code_file[0]
            if not os.path.isfile(self.country_code_file):
                self.country_code_file = os.path.join(
                    self.user_params[Config.key_config_path.value],
                    self.country_code_file)
            self.country_code = utils.load_config(
                self.country_code_file)["country"]

    def teardown_class(self):
        if self.dut.droid.wifiIsApEnabled():
            wutils.stop_wifi_tethering(self.dut)
        wutils.reset_wifi(self.dut)
        wutils.reset_wifi(self.dut_client)
        if "AccessPoint" in self.user_params:
            del self.user_params["reference_networks"]
            del self.user_params["open_network"]

    def setup_test(self):
        super().setup_test()
        for ad in self.android_devices:
            wutils.wifi_toggle_state(ad, True)
        if "chan_13" in self.test_name and "OpenWrtAP" in self.user_params:
            self.access_points[0].close()
            self.configure_openwrt_ap_and_start(wpa_network=True,
                                                channel_2g=13)
            self.wifi_network = self.reference_networks[0]["2g"]
            for ad in self.android_devices:
                wutils.set_wifi_country_code(
                        ad, wutils.WifiEnums.CountryCode.AUSTRALIA)

    def teardown_test(self):
        super().teardown_test()
        self.dut.log.debug("Toggling Airplane mode OFF.")
        asserts.assert_true(utils.force_airplane_mode(self.dut, False),
                            "Can not turn off airplane mode: %s" % self.dut.serial)
        if self.dut.droid.wifiIsApEnabled():
            wutils.stop_wifi_tethering(self.dut)
        if "chan_13" in self.test_name and "OpenWrtAP" in self.user_params:
            self.access_points[0].close()
            self.configure_openwrt_ap_and_start(wpa_network=True)
            self.wifi_network = self.reference_networks[0]["2g"]
            for ad in self.android_devices:
                wutils.set_wifi_country_code(ad, self.country_code)

    """ Helper Functions """
    def create_softap_config(self):
        """Create a softap config with ssid and password."""
        ap_ssid = "softap_" + utils.rand_ascii_str(8)
        ap_password = utils.rand_ascii_str(8)
        self.dut.log.info("softap setup: %s %s", ap_ssid, ap_password)
        config = {wutils.WifiEnums.SSID_KEY: ap_ssid}
        config[wutils.WifiEnums.PWD_KEY] = ap_password
        return config

    def confirm_softap_in_scan_results(self, ap_ssid):
        """Confirm the ap started by wifi tethering is seen in scan results.

        Args:
            ap_ssid: SSID of the ap we are looking for.
        """
        wutils.start_wifi_connection_scan_and_ensure_network_found(
            self.dut_client, ap_ssid);

    def confirm_softap_not_in_scan_results(self, ap_ssid):
        """Confirm the ap started by wifi tethering is not seen in scan results.

        Args:
            ap_ssid: SSID of the ap we are looking for.
        """
        wutils.start_wifi_connection_scan_and_ensure_network_not_found(
            self.dut_client, ap_ssid);

    def check_cell_data_and_enable(self):
        """Make sure that cell data is enabled if there is a sim present.

        If a sim is active, cell data needs to be enabled to allow provisioning
        checks through (when applicable).  This is done to relax hardware
        requirements on DUTs - without this check, running this set of tests
        after other wifi tests may cause failures.
        """
        # We do have a sim.  Make sure data is enabled so we can tether.
        if not self.dut.droid.telephonyIsDataEnabled():
            self.dut.log.info("need to enable data")
            self.dut.droid.telephonyToggleDataConnection(True)
            asserts.assert_true(self.dut.droid.telephonyIsDataEnabled(),
                                "Failed to enable cell data for softap dut.")

    def validate_full_tether_startup(self, band=None, hidden=None,
                                     test_ping=False, test_clients=None,
                                     security=None):
        """Test full startup of wifi tethering

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
        """
        initial_wifi_state = self.dut.droid.wifiCheckState()
        initial_cell_state = tel_utils.is_sim_ready(self.log, self.dut)
        self.dut.log.info("current state: %s", initial_wifi_state)
        self.dut.log.info("is sim ready? %s", initial_cell_state)
        config = self.create_softap_config()
        wutils.start_wifi_tethering(self.dut,
                                    config[wutils.WifiEnums.SSID_KEY],
                                    config[wutils.WifiEnums.PWD_KEY],
                                    band,
                                    hidden,
                                    security)
        if hidden:
            # First ensure it's not seen in scan results.
            self.confirm_softap_not_in_scan_results(
                config[wutils.WifiEnums.SSID_KEY])
            # If the network is hidden, it should be saved on the client to be
            # seen in scan results.
            config[wutils.WifiEnums.HIDDEN_KEY] = True
            ret = self.dut_client.droid.wifiAddNetwork(config)
            asserts.assert_true(ret != -1, "Add network %r failed" % config)
            self.dut_client.droid.wifiEnableNetwork(ret, 0)
        self.confirm_softap_in_scan_results(config[wutils.WifiEnums.SSID_KEY])
        if test_ping:
            self.validate_ping_between_softap_and_client(config)
        if test_clients:
            if len(self.android_devices) > 2:
                self.validate_ping_between_two_clients(config)
        wutils.stop_wifi_tethering(self.dut)
        asserts.assert_false(self.dut.droid.wifiIsApEnabled(),
                             "SoftAp is still reported as running")
        if initial_wifi_state:
            wutils.wait_for_wifi_state(self.dut, True)
        elif self.dut.droid.wifiCheckState():
            asserts.fail("Wifi was disabled before softap and now it is enabled")

    def validate_ping_between_softap_and_client(self, config):
        """Test ping between softap and its client.

        Connect one android device to the wifi hotspot.
        Verify they can ping each other.

        Args:
            config: wifi network config with SSID, password
        """
        wutils.wifi_connect(self.dut_client, config, check_connectivity=False)

        dut_ip = self.dut.droid.connectivityGetIPv4Addresses(self.AP_IFACE)[0]
        dut_client_ip = self.dut_client.droid.connectivityGetIPv4Addresses('wlan0')[0]
        wutils.verify_11ax_softap(self.dut, self.dut_client, self.wifi6_models)

        self.dut.log.info("Try to ping %s" % dut_client_ip)
        asserts.assert_true(
            utils.adb_shell_ping(self.dut, count=10, dest_ip=dut_client_ip, timeout=20),
            "%s ping %s failed" % (self.dut.serial, dut_client_ip))

        self.dut_client.log.info("Try to ping %s" % dut_ip)
        asserts.assert_true(
            utils.adb_shell_ping(self.dut_client, count=10, dest_ip=dut_ip, timeout=20),
            "%s ping %s failed" % (self.dut_client.serial, dut_ip))

    def validate_ping_between_two_clients(self, config):
        """Test ping between softap's clients.

        Connect two android device to the wifi hotspot.
        Verify the clients can ping each other.

        Args:
            config: wifi network config with SSID, password
        """
        # Connect DUT to Network
        ad1 = self.dut_client
        ad2 = self.android_devices[2]

        wutils.wifi_connect(ad1, config, check_connectivity=False)
        wutils.wifi_connect(ad2, config, check_connectivity=False)
        ad1_ip = ad1.droid.connectivityGetIPv4Addresses('wlan0')[0]
        ad2_ip = ad2.droid.connectivityGetIPv4Addresses('wlan0')[0]

        # Ping each other
        ad1.log.info("Try to ping %s" % ad2_ip)
        asserts.assert_true(
            utils.adb_shell_ping(ad1, count=10, dest_ip=ad2_ip, timeout=20),
            "%s ping %s failed" % (ad1.serial, ad2_ip))

        ad2.log.info("Try to ping %s" % ad1_ip)
        asserts.assert_true(
            utils.adb_shell_ping(ad2, count=10, dest_ip=ad1_ip, timeout=20),
            "%s ping %s failed" % (ad2.serial, ad1_ip))

    def validate_softap_after_reboot(self, band, security, hidden=False):
        config = self.create_softap_config()
        softap_config = config.copy()
        softap_config[WifiEnums.AP_BAND_KEY] = band
        softap_config[WifiEnums.SECURITY] = security
        if hidden:
            softap_config[WifiEnums.HIDDEN_KEY] = hidden
        asserts.assert_true(
            self.dut.droid.wifiSetWifiApConfiguration(softap_config),
            "Failed to update WifiAp Configuration")
        self.dut.reboot()
        time.sleep(WAIT_AFTER_REBOOT)
        wutils.start_wifi_tethering_saved_config(self.dut)
        wutils.connect_to_wifi_network(self.dut_client, config, hidden=hidden)

    """ Tests Begin """

    @test_tracker_info(uuid="495f1252-e440-461c-87a7-2c45f369e129")
    def test_check_wifi_tethering_supported(self):
        """Test check for wifi tethering support.

         1. Call method to check if wifi hotspot is supported
        """
        # TODO(silberst): wifiIsPortableHotspotSupported() is currently failing.
        # Remove the extra check and logging when b/30800811 is resolved
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        hotspot_supported = self.dut.droid.wifiIsPortableHotspotSupported()
        tethering_supported = self.dut.droid.connectivityIsTetheringSupported()
        self.log.info(
            "IsPortableHotspotSupported: %s, IsTetheringSupported %s." % (
            hotspot_supported, tethering_supported))
        asserts.assert_true(hotspot_supported,
                            "DUT should support wifi tethering but is reporting false.")
        asserts.assert_true(tethering_supported,
                            "DUT should also support wifi tethering when called from ConnectivityManager")

    @test_tracker_info(uuid="09c19c35-c708-48a5-939b-ac2bbb403d54")
    def test_full_tether_startup(self):
        """Test full startup of wifi tethering in default band.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        self.validate_full_tether_startup()

    @test_tracker_info(uuid="6437727d-7db1-4f69-963e-f26a7797e47f")
    def test_full_tether_startup_2G(self):
        """Test full startup of wifi tethering in 2G band.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        self.validate_full_tether_startup(WIFI_CONFIG_APBAND_2G)

    @test_tracker_info(uuid="970272fa-1302-429b-b261-51efb4dad779")
    def test_full_tether_startup_5G(self):
        """Test full startup of wifi tethering in 5G band.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        self.validate_full_tether_startup(WIFI_CONFIG_APBAND_5G)

    @test_tracker_info(uuid="f76ed37a-519a-48b4-b260-ee3fc5a9cae0")
    def test_full_tether_startup_auto(self):
        """Test full startup of wifi tethering in auto-band.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        self.validate_full_tether_startup(WIFI_CONFIG_APBAND_AUTO)

    @test_tracker_info(uuid="d26ee4df-5dcb-4191-829f-05a10b1218a7")
    def test_full_tether_startup_2G_hidden(self):
        """Test full startup of wifi tethering in 2G band using hidden AP.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        self.validate_full_tether_startup(WIFI_CONFIG_APBAND_2G, True)

    @test_tracker_info(uuid="229cd585-a789-4c9a-8948-89fa72de9dd5")
    def test_full_tether_startup_5G_hidden(self):
        """Test full startup of wifi tethering in 5G band using hidden AP.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        self.validate_full_tether_startup(WIFI_CONFIG_APBAND_5G, True)

    @test_tracker_info(uuid="d546a143-6047-4ffd-b3c6-5ec81a38001f")
    def test_full_tether_startup_auto_hidden(self):
        """Test full startup of wifi tethering in auto-band using hidden AP.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        self.validate_full_tether_startup(WIFI_CONFIG_APBAND_AUTO, True)

    @test_tracker_info(uuid="25996696-e9c8-4cd3-816a-44536166e69f")
    def test_full_tether_startup_wpa3(self):
        """Test full startup of softap in default band and wpa3 security.

        Steps:
        1. Configure softap in default band and wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA3 softAp")
        self.validate_full_tether_startup(security=WPA3_SAE_SOFTAP)

    @test_tracker_info(uuid="65ecdd4b-857e-4bda-87e7-3db578cee7aa")
    def test_full_tether_startup_2G_wpa3(self):
        """Test full startup of softap in 2G band and wpa3 security.

        Steps:
        1. Configure softap in 2G band and wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA3 softAp")
        self.validate_full_tether_startup(
            WIFI_CONFIG_APBAND_2G, security=WPA3_SAE_SOFTAP)

    @test_tracker_info(uuid="dbc788dc-bf11-48aa-b88f-c2ee767cd13d")
    def test_full_tether_startup_5G_wpa3(self):
        """Test full startup of softap in 5G band and wpa3 security.

        Steps:
        1. Configure softap in 5G band and wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA3 softAp")
        self.validate_full_tether_startup(
            WIFI_CONFIG_APBAND_5G, security=WPA3_SAE_SOFTAP)

    @test_tracker_info(uuid="1192c522-824a-4a79-a6cd-bd63b7d19e82")
    def test_full_tether_startup_auto_wpa3(self):
        """Test full startup of softap in auto band and wpa3 security.

        Steps:
        1. Configure softap in auto band and wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA3 softAp")
        self.validate_full_tether_startup(
            WIFI_CONFIG_APBAND_AUTO, security=WPA3_SAE_SOFTAP)

    @test_tracker_info(uuid="120d2dcb-ded6-40d3-854c-366c200c8deb")
    def test_full_tether_startup_hidden_wpa3(self):
        """Test full startup of hidden softap in default band and wpa3 security.

        Steps:
        1. Configure hidden softap in default band and wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA3 softAp")
        self.validate_full_tether_startup(security=WPA3_SAE_SOFTAP, hidden=True)

    @test_tracker_info(uuid="82fc2329-480b-4cab-bf9d-e1c397673e4a")
    def test_full_tether_startup_2G_hidden_wpa3(self):
        """Test full startup of hidden softap in 2G band and wpa3 security.

        Steps:
        1. Configure hidden softap in 2G band and wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA3 softAp")
        self.validate_full_tether_startup(
            WIFI_CONFIG_APBAND_2G, True, security=WPA3_SAE_SOFTAP)

    @test_tracker_info(uuid="0da2958e-de0b-4567-aff1-d4ba5439eb4e")
    def test_full_tether_startup_5G_hidden_wpa3(self):
        """Test full startup of hidden softap in 5G band and wpa3 security.

        Steps:
        1. Configure hidden softap in 5G band and wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA3 softAp")
        self.validate_full_tether_startup(
            WIFI_CONFIG_APBAND_5G, True, security=WPA3_SAE_SOFTAP)

    @test_tracker_info(uuid="1412f928-e89b-4e84-8ad0-1b14e936b239")
    def test_full_tether_startup_auto_hidden_wpa3(self):
        """Test full startup of hidden softap in auto band and wpa3 security.

        Steps:
        1. Configure hidden softap in auto band and wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA3 softAp")
        self.validate_full_tether_startup(
            WIFI_CONFIG_APBAND_AUTO, True, security=WPA3_SAE_SOFTAP)

    @test_tracker_info(uuid="e1433f7e-57f6-4475-822c-754d77817bbc")
    def test_full_tether_startup_wpa2_wpa3(self):
        """Test full startup of softap in default band and wpa2/wpa3 security.

        Steps:
        1. Configure softap in default band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA2/WPA3 softAp")
        self.validate_full_tether_startup(security=WPA3_SAE_TRANSITION_SOFTAP)

    @test_tracker_info(uuid="8f55209f-0b9a-4600-a416-84d075c349af")
    def test_full_tether_startup_2G_wpa2_wpa3(self):
        """Test full startup of softap in 2G band and wpa2/wpa3 security.

        Steps:
        1. Configure softap in default band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA2/WPA3 softAp")
        self.validate_full_tether_startup(
            WIFI_CONFIG_APBAND_2G, security=WPA3_SAE_TRANSITION_SOFTAP)

    @test_tracker_info(uuid="3d54b7c5-cc34-473f-b484-fc3bf1773a90")
    def test_full_tether_startup_5G_wpa2_wpa3(self):
        """Test full startup of softap in 5G band and wpa2/wpa3 security.

        Steps:
        1. Configure softap in default band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA2/WPA3 softAp")
        self.validate_full_tether_startup(
            WIFI_CONFIG_APBAND_5G, security=WPA3_SAE_TRANSITION_SOFTAP)

    @test_tracker_info(uuid="f07bedd2-d768-497d-8922-2e5fe1cd9365")
    def test_full_tether_startup_auto_wpa2_wpa3(self):
        """Test full startup of softap in auto band and wpa2/wpa3 security.

        Steps:
        1. Configure softap in default band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA2/WPA3 softAp")
        self.validate_full_tether_startup(
            WIFI_CONFIG_APBAND_AUTO, security=WPA3_SAE_TRANSITION_SOFTAP)

    @test_tracker_info(uuid="9a54f97f-eaca-4a64-ad20-f9e52f8b16a1")
    def test_full_tether_startup_2G_hidden_wpa2_wpa3(self):
        """Test full startup of hidden softap in 2G band and wpa2/wpa3.

        Steps:
        1. Configure hidden softap in 2G band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA2/WPA3 softAp")
        self.validate_full_tether_startup(
            WIFI_CONFIG_APBAND_2G, True, security=WPA3_SAE_TRANSITION_SOFTAP)

    @test_tracker_info(uuid="1baef45f-c6c9-46bc-8227-1aacf410e60d")
    def test_full_tether_startup_5G_hidden_wpa2_wpa3(self):
        """Test full startup of hidden softap in 5G band and wpa2/wpa3 security.

        Steps:
        1. Configure hidden softap in 5G band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA2/WPA3 softAp")
        self.validate_full_tether_startup(
            WIFI_CONFIG_APBAND_5G, True, security=WPA3_SAE_TRANSITION_SOFTAP)

    @test_tracker_info(uuid="1976ac84-f967-4961-bdb9-4fcfe297fe22")
    def test_full_tether_startup_auto_hidden_wpa2_wpa3(self):
        """Test full startup of hidden softap in auto band and wpa2/wpa3.

        Steps:
        1. Configure hidden softap in auto band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA3 softAp")
        self.validate_full_tether_startup(
            WIFI_CONFIG_APBAND_AUTO, True, security=WPA3_SAE_TRANSITION_SOFTAP)

    @test_tracker_info(uuid="dd4c79dc-169f-4d8f-a700-95ba2923af35")
    def test_softap_wpa3_2g_after_reboot(self):
        """Test full startup of softap in 2G band, wpa3 security after reboot.

        Steps:
        1. Save softap in 2G band and wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA3 softAp")
        self.validate_softap_after_reboot(
            WIFI_CONFIG_APBAND_2G, WPA3_SAE_SOFTAP, False)

    @test_tracker_info(uuid="02f080d8-91e7-4363-a291-da3c87e74758")
    def test_softap_wpa3_5g_after_reboot(self):
        """Test full startup of softap in 5G band, wpa3 security after reboot.

        Steps:
        1. Save softap in 5G band and wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA3 softAp")
        self.validate_softap_after_reboot(
            WIFI_CONFIG_APBAND_5G, WPA3_SAE_SOFTAP, False)

    @test_tracker_info(uuid="2224f94e-88e8-4ebf-bbab-f78ab24cefda")
    def test_softap_wpa2_wpa3_2g_after_reboot(self):
        """Test softap in 2G band, wpa2/wpa3 security after reboot.

        Steps:
        1. Save softap in 2G band and wpa2/wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA2/WPA3 softAp")
        self.validate_softap_after_reboot(
            WIFI_CONFIG_APBAND_2G, WPA3_SAE_TRANSITION_SOFTAP, False)

    @test_tracker_info(uuid="320643e2-9e13-4c8c-a2cb-1903b6bd3741")
    def test_softap_wpa2_wpa3_5g_after_reboot(self):
        """Test softap in 5G band, wpa2/wpa3 security after reboot.

        Steps:
        1. Save softap in 5G band and wpa2/wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA2/WPA3 softAp")
        self.validate_softap_after_reboot(
            WIFI_CONFIG_APBAND_5G, WPA3_SAE_TRANSITION_SOFTAP, False)

    @test_tracker_info(uuid="52005efc-45a2-41df-acd1-5fd551e88a3c")
    def test_softap_wpa3_2g_hidden_after_reboot(self):
        """Test hidden softap in 2G band, wpa3 security after reboot.

        Steps:
        1. Save hidden softap in 2G band and wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA3 softAp")
        self.validate_softap_after_reboot(
            WIFI_CONFIG_APBAND_2G, WPA3_SAE_SOFTAP, True)

    @test_tracker_info(uuid="78e07c03-f628-482d-b78b-84bdfba0bfaf")
    def test_softap_wpa3_5g_hidden_after_reboot(self):
        """Test hidden softap in 5G band, wpa3 security after reboot.

        Steps:
        1. Save hidden softap in 5G band and wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA3 softAp")
        self.validate_softap_after_reboot(
            WIFI_CONFIG_APBAND_5G, WPA3_SAE_SOFTAP, True)

    @test_tracker_info(uuid="749ba522-dd1f-459a-81bd-957943201c32")
    def test_softap_wpa2_wpa3_2g_hidden_after_reboot(self):
        """Test hidden softap in 2G band, wpa2/wpa3 security after reboot.

        Steps:
        1. Save hidden softap in 2G band and wpa2/wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA2/WPA3 softAp")
        self.validate_softap_after_reboot(
            WIFI_CONFIG_APBAND_2G, WPA3_SAE_TRANSITION_SOFTAP, True)

    @test_tracker_info(uuid="638f1456-8556-448d-8cad-63e6b72337ca")
    def test_softap_wpa2_wpa3_5g_hidden_after_reboot(self):
        """Test hidden softap in 5G band, wpa2/wpa3 security after reboot.

        Steps:
        1. Save hidden softap in 5G band and wpa2/wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(self.dut.model not in self.sap_wpa3_supported_models,
                        "DUT does not support WPA2/WPA3 softAp")
        self.validate_softap_after_reboot(
            WIFI_CONFIG_APBAND_5G, WPA3_SAE_TRANSITION_SOFTAP, True)

    @test_tracker_info(uuid="b2f75330-bf33-4cdd-851a-de390f891ef7")
    def test_tether_startup_while_connected_to_a_network(self):
        """Test full startup of wifi tethering in auto-band while the device
        is connected to a network.

        1. Connect to an open network.
        2. Turn on AP mode (in auto band).
        3. Verify SoftAP active.
        4. Make a client connect to the AP.
        5. Shutdown wifi tethering.
        6. Ensure that the client disconnected.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        wutils.wifi_toggle_state(self.dut, True)
        wutils.wifi_connect(self.dut, self.wifi_network)
        config = self.create_softap_config()
        wutils.start_wifi_tethering(self.dut,
                                    config[wutils.WifiEnums.SSID_KEY],
                                    config[wutils.WifiEnums.PWD_KEY],
                                    WIFI_CONFIG_APBAND_AUTO)
        asserts.assert_true(self.dut.droid.wifiIsApEnabled(),
                             "SoftAp is not reported as running")
        # local hotspot may not have internet connectivity
        self.confirm_softap_in_scan_results(config[wutils.WifiEnums.SSID_KEY])
        wutils.wifi_connect(self.dut_client, config, check_connectivity=False)
        wutils.verify_11ax_softap(self.dut, self.dut_client, self.wifi6_models)
        wutils.stop_wifi_tethering(self.dut)
        wutils.wait_for_disconnect(self.dut_client)

    @test_tracker_info(uuid="f2cf56ad-b8b9-43b6-ab15-a47b1d96b92e")
    def test_full_tether_startup_2G_with_airplane_mode_on(self):
        """Test full startup of wifi tethering in 2G band with
        airplane mode on.

        1. Turn on airplane mode.
        2. Report current state.
        3. Switch to AP mode.
        4. verify SoftAP active.
        5. Shutdown wifi tethering.
        6. verify back to previous mode.
        7. Turn off airplane mode.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        self.dut.log.debug("Toggling Airplane mode ON.")
        asserts.assert_true(utils.force_airplane_mode(self.dut, True),
                            "Can not turn on airplane mode: %s" % self.dut.serial)
        wutils.wifi_toggle_state(self.dut, True)
        self.validate_full_tether_startup(WIFI_CONFIG_APBAND_2G)

    @test_tracker_info(uuid="883dd5b1-50c6-4958-a50f-bb4bea77ccaf")
    def test_full_tether_startup_2G_one_client_ping_softap(self):
        """(AP) 1 Device can connect to 2G hotspot

        Steps:
        1. Turn on DUT's 2G softap
        2. Client connects to the softap
        3. Client and DUT ping each other
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        self.validate_full_tether_startup(WIFI_CONFIG_APBAND_2G, test_ping=True)

    @test_tracker_info(uuid="6604e848-99d6-422c-9fdc-2882642438b6")
    def test_full_tether_startup_5G_one_client_ping_softap(self):
        """(AP) 1 Device can connect to 5G hotspot

        Steps:
        1. Turn on DUT's 5G softap
        2. Client connects to the softap
        3. Client and DUT ping each other
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        self.validate_full_tether_startup(WIFI_CONFIG_APBAND_5G, test_ping=True)

    @test_tracker_info(uuid="17725ecd-f900-4cf7-8b2d-d7515b0a595c")
    def test_softap_2G_two_clients_ping_each_other(self):
        """Test for 2G hotspot with 2 clients

        1. Turn on 2G hotspot
        2. Two clients connect to the hotspot
        3. Two clients ping each other
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(len(self.android_devices) < 3,
                        "No extra android devices. Skip test")
        self.validate_full_tether_startup(WIFI_CONFIG_APBAND_2G, test_clients=True)

    @test_tracker_info(uuid="98c09888-1021-4f79-9065-b3cf9b132146")
    def test_softap_5G_two_clients_ping_each_other(self):
        """Test for 5G hotspot with 2 clients

        1. Turn on 5G hotspot
        2. Two clients connect to the hotspot
        3. Two clients ping each other
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(len(self.android_devices) < 3,
                        "No extra android devices. Skip test")
        self.validate_full_tether_startup(WIFI_CONFIG_APBAND_5G, test_clients=True)

    @test_tracker_info(uuid="b991129e-030a-4998-9b08-0687270bec24")
    def test_number_of_softap_clients(self):
        """Test for number of softap clients to be updated correctly

        1. Turn of hotspot
        2. Register softap callback
        3. Let client connect to the hotspot
        4. Register second softap callback
        5. Force client connect/disconnect to hotspot
        6. Unregister second softap callback
        7. Force second client connect to hotspot (if supported)
        8. Turn off hotspot
        9. Verify second softap callback doesn't respond after unregister
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        config = wutils.start_softap_and_verify(self, WIFI_CONFIG_APBAND_AUTO)
        # Register callback after softap enabled to avoid unnecessary callback
        # impact the test
        callbackId = self.dut.droid.registerSoftApCallback()
        # Verify clients will update immediately after register callback
        wutils.wait_for_expected_number_of_softap_clients(
                self.dut, callbackId, 0)
        wutils.wait_for_expected_softap_state(self.dut, callbackId,
                wifi_constants.WIFI_AP_ENABLED_STATE)

        # Force DUTs connect to Network
        wutils.wifi_connect(self.dut_client, config,
                check_connectivity=False)
        wutils.wait_for_expected_number_of_softap_clients(
                self.dut, callbackId, 1)

        # Register another callback to verify multi callback clients case
        callbackId_2 = self.dut.droid.registerSoftApCallback()
        # Verify clients will update immediately after register callback
        wutils.wait_for_expected_number_of_softap_clients(
                self.dut, callbackId_2, 1)
        wutils.wait_for_expected_softap_state(self.dut, callbackId_2,
                wifi_constants.WIFI_AP_ENABLED_STATE)

        # Client Off/On Wifi to verify number of softap clients will be updated
        wutils.toggle_wifi_and_wait_for_reconnection(self.dut_client, config)

        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                callbackId, 0)
        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                callbackId_2, 0)
        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                callbackId, 1)
        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                callbackId_2, 1)

        # Unregister callbackId_2 to verify multi callback clients case
        self.dut.droid.unregisterSoftApCallback(callbackId_2)

        if len(self.android_devices) > 2:
            wutils.wifi_connect(self.android_devices[2], config,
                    check_connectivity=False)
            wutils.wait_for_expected_number_of_softap_clients(
                    self.dut, callbackId, 2)

        # Turn off softap when clients connected
        wutils.stop_wifi_tethering(self.dut)
        wutils.wait_for_disconnect(self.dut_client)
        if len(self.android_devices) > 2:
            wutils.wait_for_disconnect(self.android_devices[2])

        # Verify client number change back to 0 after softap stop if client
        # doesn't disconnect before softap stop
        wutils.wait_for_expected_softap_state(self.dut, callbackId,
                wifi_constants.WIFI_AP_DISABLING_STATE)
        wutils.wait_for_expected_softap_state(self.dut, callbackId,
                wifi_constants.WIFI_AP_DISABLED_STATE)
        wutils.wait_for_expected_number_of_softap_clients(
                self.dut, callbackId, 0)
        # Unregister callback
        self.dut.droid.unregisterSoftApCallback(callbackId)

        # Check no any callbackId_2 event after unregister
        asserts.assert_equal(
                wutils.get_current_number_of_softap_clients(
                self.dut, callbackId_2), None)

    @test_tracker_info(uuid="35bc4ba1-bade-42ee-a563-0c73afb2402a")
    def test_softap_auto_shut_off(self):
        """Test for softap auto shut off

        1. Turn off hotspot
        2. Register softap callback
        3. Let client connect to the hotspot
        4. Start wait [wifi_constants.DEFAULT_SOFTAP_TIMEOUT_S] seconds
        5. Check hotspot doesn't shut off
        6. Let client disconnect to the hotspot
        7. Start wait [wifi_constants.DEFAULT_SOFTAP_TIMEOUT_S] seconds
        8. Check hotspot auto shut off
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        config = wutils.start_softap_and_verify(self, WIFI_CONFIG_APBAND_AUTO)
        # Register callback after softap enabled to avoid unnecessary callback
        # impact the test
        callbackId = self.dut.droid.registerSoftApCallback()
        # Verify clients will update immediately after register callback
        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                callbackId, 0)
        wutils.wait_for_expected_softap_state(self.dut, callbackId,
                wifi_constants.WIFI_AP_ENABLED_STATE)

        # Force DUTs connect to Network
        wutils.wifi_connect(self.dut_client, config, check_connectivity=False)
        wutils.wait_for_expected_number_of_softap_clients(
                self.dut, callbackId, 1)

        self.dut.log.info("Start waiting %s seconds with 1 clients ",
                wifi_constants.DEFAULT_SOFTAP_TIMEOUT_S*1.1)
        time.sleep(wifi_constants.DEFAULT_SOFTAP_TIMEOUT_S*1.1)

        # When client connected, softap should keep as enabled
        asserts.assert_true(self.dut.droid.wifiIsApEnabled(),
                "SoftAp is not reported as running")

        wutils.wifi_toggle_state(self.dut_client, False)
        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                callbackId, 0)
        self.dut.log.info("Start waiting %s seconds with 0 client",
                wifi_constants.DEFAULT_SOFTAP_TIMEOUT_S*1.1)
        time.sleep(wifi_constants.DEFAULT_SOFTAP_TIMEOUT_S*1.1)
        # Softap should stop since no client connected
        # doesn't disconnect before softap stop
        wutils.wait_for_expected_softap_state(self.dut, callbackId,
                wifi_constants.WIFI_AP_DISABLING_STATE)
        wutils.wait_for_expected_softap_state(self.dut, callbackId,
                wifi_constants.WIFI_AP_DISABLED_STATE)
        asserts.assert_false(self.dut.droid.wifiIsApEnabled(),
                "SoftAp is not reported as running")
        self.dut.droid.unregisterSoftApCallback(callbackId)

    @test_tracker_info(uuid="3a10c7fd-cd8d-4d46-9d12-88a68640e060")
    def test_softap_auto_shut_off_with_customized_timeout(self):
        """Test for softap auto shut off
        1. Turn on hotspot
        2. Register softap callback
        3. Backup original shutdown timeout value
        4. Set up test_shutdown_timeout_value
        5. Let client connect to the hotspot
        6. Start wait test_shutdown_timeout_value * 1.1 seconds
        7. Check hotspot doesn't shut off
        8. Let client disconnect to the hotspot
        9. Start wait test_shutdown_timeout_value seconds
        10. Check hotspot auto shut off
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()
        # This config only included SSID and Password which used for connection
        # only.
        config = wutils.start_softap_and_verify(self, WIFI_CONFIG_APBAND_AUTO)

        # Get current configuration to use for update configuration
        current_softap_config = self.dut.droid.wifiGetApConfiguration()
        # Register callback after softap enabled to avoid unnecessary callback
        # impact the test
        callbackId = self.dut.droid.registerSoftApCallback()
        # Verify clients will update immediately after register callback
        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                callbackId, 0)
        wutils.wait_for_expected_softap_state(self.dut, callbackId,
                wifi_constants.WIFI_AP_ENABLED_STATE)

        # Setup shutdown timeout value
        test_shutdown_timeout_value_s = 10
        wutils.save_wifi_soft_ap_config(self.dut, current_softap_config,
            shutdown_timeout_millis=test_shutdown_timeout_value_s * 1000)
        # Force DUTs connect to Network
        wutils.wifi_connect(self.dut_client, config, check_connectivity=False)
        wutils.wait_for_expected_number_of_softap_clients(
                self.dut, callbackId, 1)

        self.dut.log.info("Start waiting %s seconds with 1 clients ",
                test_shutdown_timeout_value_s * 1.1)
        time.sleep(test_shutdown_timeout_value_s * 1.1)

        # When client connected, softap should keep as enabled
        asserts.assert_true(self.dut.droid.wifiIsApEnabled(),
                "SoftAp is not reported as running")

        wutils.wifi_toggle_state(self.dut_client, False)
        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                callbackId, 0)
        self.dut.log.info("Start waiting %s seconds with 0 client",
                test_shutdown_timeout_value_s * 1.1)
        time.sleep(test_shutdown_timeout_value_s * 1.1)
        # Softap should stop since no client connected
        # doesn't disconnect before softap stop
        wutils.wait_for_expected_softap_state(self.dut, callbackId,
                wifi_constants.WIFI_AP_DISABLING_STATE)
        wutils.wait_for_expected_softap_state(self.dut, callbackId,
                wifi_constants.WIFI_AP_DISABLED_STATE)
        asserts.assert_false(self.dut.droid.wifiIsApEnabled(),
                "SoftAp is not reported as running")
        self.dut.droid.unregisterSoftApCallback(callbackId)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="a9444699-f0d3-4ac3-922b-05e9d4f67968")
    def test_softap_configuration_update(self):
      """Test for softap configuration update
        1. Get current softap configuration
        2. Update to Open Security configuration
        3. Update to WPA2_PSK configuration
        4. Update to Multi-Channels, Mac Randomization off,
           bridged_shutdown off, 11ax off configuration which are introduced in S.
        5. Restore the configuration
      """
      asserts.skip_if(self.dut.model not in self.sim_supported_models,
                      "Device does not support SIM card, softAp not applicable.")
      # Backup config
      original_softap_config = self.dut.droid.wifiGetApConfiguration()
      wutils.save_wifi_soft_ap_config(self.dut, {"SSID":"ACTS_TEST"},
          band=WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G, hidden=False,
          security=WifiEnums.SoftApSecurityType.OPEN, password="",
          channel=11, max_clients=0, shutdown_timeout_enable=False,
          shutdown_timeout_millis=0, client_control_enable=True,
          allowedList=[], blockedList=[])

      wutils.save_wifi_soft_ap_config(self.dut, {"SSID":"ACTS_TEST"},
          band=WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G, hidden=True,
          security=WifiEnums.SoftApSecurityType.WPA2, password="12345678",
          channel=0, max_clients=1, shutdown_timeout_enable=True,
          shutdown_timeout_millis=10000, client_control_enable=False,
          allowedList=["aa:bb:cc:dd:ee:ff"], blockedList=["11:22:33:44:55:66"])

      if self.dut.droid.isSdkAtLeastS():
          wutils.save_wifi_soft_ap_config(self.dut, {"SSID":"ACTS_TEST"},
              channel_frequencys=[2412,5745],
              mac_randomization_setting = wifi_constants.SOFTAP_RANDOMIZATION_NONE,
              bridged_opportunistic_shutdown_enabled=False,
              ieee80211ax_enabled=False)

      # Restore config
      wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

    @test_tracker_info(uuid="8a5d81fa-649c-4679-a823-5cef50828a94")
    def test_softap_client_control(self):
        """Test Client Control feature
        1. Check SoftApCapability to make sure feature is supported
        2. Backup config
        3. Setup configuration which used to start softap
        4. Register callback after softap enabled
        5. Trigger client connect to softap
        6. Verify blocking event
        7. Add client into allowed list
        8. Verify client connected
        9. Restore Config
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        # Register callback to check capability first
        callbackId = self.dut.droid.registerSoftApCallback()
        # Check capability first to make sure DUT support this test.
        capabilityEventStr = wifi_constants.SOFTAP_CALLBACK_EVENT + str(
            callbackId) + wifi_constants.SOFTAP_CAPABILITY_CHANGED
        capability = self.dut.ed.pop_event(capabilityEventStr, 10)
        asserts.skip_if(not capability['data'][wifi_constants
            .SOFTAP_CAPABILITY_FEATURE_CLIENT_CONTROL],
            "Client control isn't supported, ignore test")

        # Unregister callback before start test to avoid
        # unnecessary callback impact the test
        self.dut.droid.unregisterSoftApCallback(callbackId)

        # start the test

        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()
        # Setup configuration which used to start softap
        wutils.save_wifi_soft_ap_config(self.dut, {"SSID":"ACTS_TEST"},
            band=WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G, hidden=False,
            security=WifiEnums.SoftApSecurityType.WPA2, password="12345678",
            client_control_enable=True)

        wutils.start_wifi_tethering_saved_config(self.dut)
        current_softap_config = self.dut.droid.wifiGetApConfiguration()
        # Register callback after softap enabled to avoid unnecessary callback
        # impact the test
        callbackId = self.dut.droid.registerSoftApCallback()

        # Verify clients will update immediately after register callback
        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                callbackId, 0)
        wutils.wait_for_expected_softap_state(self.dut, callbackId,
                wifi_constants.WIFI_AP_ENABLED_STATE)

        # Trigger client connection
        self.dut_client.droid.wifiConnectByConfig(current_softap_config)

        eventStr = wifi_constants.SOFTAP_CALLBACK_EVENT + str(
            callbackId) + wifi_constants.SOFTAP_BLOCKING_CLIENT_CONNECTING
        blockedClient = self.dut.ed.pop_event(eventStr, 10)
        asserts.assert_equal(blockedClient['data'][wifi_constants.
            SOFTAP_BLOCKING_CLIENT_REASON_KEY],
            wifi_constants.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER,
            "Blocked reason code doesn't match")

        # Update configuration, add client into allowed list
        wutils.save_wifi_soft_ap_config(self.dut, current_softap_config,
            allowedList=[blockedClient['data'][wifi_constants.
            SOFTAP_BLOCKING_CLIENT_WIFICLIENT_KEY]])

        # Wait configuration updated
        time.sleep(3)
        # Trigger connection again
        self.dut_client.droid.wifiConnectByConfig(current_softap_config)

        # Verify client connected
        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                callbackId, 1)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

        # Unregister callback
        self.dut.droid.unregisterSoftApCallback(callbackId)

    @test_tracker_info(uuid="d0b61b58-fa2b-4ced-bc52-3366cb826e79")
    def test_softap_max_client_setting(self):
        """Test Client Control feature
        1. Check device number and capability to make sure feature is supported
        2. Backup config
        3. Setup configuration which used to start softap
        4. Register callback after softap enabled
        5. Trigger client connect to softap
        6. Verify blocking event
        7. Extend max client setting
        8. Verify client connected
        9. Restore Config
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if(len(self.android_devices) < 3,
                        "Device less than 3, skip the test.")
        # Register callback to check capability first
        callbackId = self.dut.droid.registerSoftApCallback()
        # Check capability first to make sure DUT support this test.
        capabilityEventStr = wifi_constants.SOFTAP_CALLBACK_EVENT + str(
            callbackId) + wifi_constants.SOFTAP_CAPABILITY_CHANGED
        capability = self.dut.ed.pop_event(capabilityEventStr, 10)
        asserts.skip_if(not capability['data'][wifi_constants
            .SOFTAP_CAPABILITY_FEATURE_CLIENT_CONTROL],
            "Client control isn't supported, ignore test")

        # Unregister callback before start test to avoid
        # unnecessary callback impact the test
        self.dut.droid.unregisterSoftApCallback(callbackId)

        # Backup config
        original_softap_config = self.dut.droid.wifiGetApConfiguration()
        # Setup configuration which used to start softap
        wutils.save_wifi_soft_ap_config(self.dut, {"SSID":"ACTS_TEST"},
            band=WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G, hidden=False,
            security=WifiEnums.SoftApSecurityType.WPA2, password="12345678",
            max_clients=1)

        wutils.start_wifi_tethering_saved_config(self.dut)
        current_softap_config = self.dut.droid.wifiGetApConfiguration()
        # Register callback again after softap enabled to avoid
        # unnecessary callback impact the test
        callbackId = self.dut.droid.registerSoftApCallback()

        # Verify clients will update immediately after register calliback
        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                callbackId, 0)
        wutils.wait_for_expected_softap_state(self.dut, callbackId,
                wifi_constants.WIFI_AP_ENABLED_STATE)

        # Trigger client connection
        self.dut_client.droid.wifiConnectByConfig(current_softap_config)
        self.dut_client_2.droid.wifiConnectByConfig(current_softap_config)
        # Wait client connect
        time.sleep(3)

        # Verify one client connected and one blocked due to max client
        eventStr = wifi_constants.SOFTAP_CALLBACK_EVENT + str(
            callbackId) + wifi_constants.SOFTAP_BLOCKING_CLIENT_CONNECTING
        blockedClient = self.dut.ed.pop_event(eventStr, 10)
        asserts.assert_equal(blockedClient['data'][wifi_constants.
            SOFTAP_BLOCKING_CLIENT_REASON_KEY],
            wifi_constants.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS,
            "Blocked reason code doesn't match")
        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                callbackId, 1)

        # Update configuration, extend client to 2
        wutils.save_wifi_soft_ap_config(self.dut, current_softap_config,
            max_clients=2)

        # Wait configuration updated
        time.sleep(3)
        # Trigger connection again
        self.dut_client_2.droid.wifiConnectByConfig(current_softap_config)
        # Wait client connect
        time.sleep(3)
        # Verify client connected
        wutils.wait_for_expected_number_of_softap_clients(self.dut,
                callbackId, 2)

        # Restore config
        wutils.save_wifi_soft_ap_config(self.dut, original_softap_config)

        # Unregister callback
        self.dut.droid.unregisterSoftApCallback(callbackId)

    @test_tracker_info(uuid="07b4e5b3-48ce-49b9-a83e-3e288bb88e91")
    def test_softap_5g_preferred_country_code_de(self):
        """Verify softap works when set to 5G preferred band
           with country code 'DE'.

        Steps:
            1. Set country code to Germany
            2. Save a softap configuration set to 5G preferred band.
            3. Start softap and verify it works
            4. Verify a client device can connect to it.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        wutils.set_wifi_country_code(
            self.dut, wutils.WifiEnums.CountryCode.GERMANY)
        sap_config = self.create_softap_config()
        wifi_network = sap_config.copy()
        sap_config[
            WifiEnums.AP_BAND_KEY] = WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G
        sap_config[WifiEnums.SECURITY] = WifiEnums.SoftApSecurityType.WPA2
        asserts.assert_true(
            self.dut.droid.wifiSetWifiApConfiguration(sap_config),
            "Failed to set WifiAp Configuration")
        wutils.start_wifi_tethering_saved_config(self.dut)
        softap_conf = self.dut.droid.wifiGetApConfiguration()
        self.log.info("softap conf: %s" % softap_conf)
        sap_band = softap_conf[WifiEnums.AP_BAND_KEY]
        asserts.assert_true(
            sap_band == WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G_5G,
            "Soft AP didn't start in 5G preferred band")
        wutils.connect_to_wifi_network(self.dut_client, wifi_network)
        wutils.verify_11ax_softap(self.dut, self.dut_client, self.wifi6_models)

    @test_tracker_info(uuid="dbcd653c-ec65-400f-a6ce-77bb13add473")
    def test_softp_2g_channel_when_connected_to_chan_13(self):
        """Verify softAp 2G channel when connected to network on channel 13.

        Steps:
            1. Configure AP in channel 13 on 2G band and connect DUT to it.
            2. Start softAp on DUT on 2G band.
            3. Verify softAp is started on channel 13.
        """
        asserts.skip_if(self.dut.model not in self.sim_supported_models,
                        "Device does not support SIM card, softAp not applicable.")
        asserts.skip_if("OpenWrtAP" not in self.user_params,
                        "Need openwrt AP to configure channel 13.")
        wutils.connect_to_wifi_network(self.dut, self.wifi_network)
        sap_config = self.create_softap_config()
        sap_config[WifiEnums.AP_BAND_KEY] = WifiEnums.WIFI_CONFIG_SOFTAP_BAND_2G
        asserts.assert_true(
            self.dut.droid.wifiSetWifiApConfiguration(sap_config),
            "Failed to set WifiAp Configuration")
        wutils.start_wifi_tethering_saved_config(self.dut)
        softap_conf = self.dut.droid.wifiGetApConfiguration()
        self.log.info("softap conf: %s" % softap_conf)
        wutils.connect_to_wifi_network(self.dut_client, sap_config)
        softap_channel = self.dut_client.droid.wifiGetConnectionInfo(
                )
        conn_info = self.dut_client.droid.wifiGetConnectionInfo()
        self.log.info("Wifi connection info on dut_client: %s" % conn_info)
        softap_channel = wutils.WifiEnums.freq_to_channel[conn_info["frequency"]]
        asserts.assert_true(softap_channel == 13,
                            "Dut client did not connect to softAp on channel 13")

    """ Tests End """


if __name__ == "__main__":
    pass
