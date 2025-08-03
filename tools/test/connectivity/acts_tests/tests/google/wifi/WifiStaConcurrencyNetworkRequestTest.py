#!/usr/bin/env python3.4
#
#   Copyright 2021 - The Android Open Source Project
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

import acts.asserts as asserts
from acts.controllers.android_device import SL4A_APK_NAME
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
import acts_contrib.test_utils.net.connectivity_const as cconsts
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
import acts_contrib.test_utils.wifi.aware.aware_test_utils as autils

WifiEnums = wutils.WifiEnums

WIFI_NETWORK_AP_CHANNEL_2G_1 = 1
WIFI_NETWORK_AP_CHANNEL_5G_1 = 36
WIFI_NETWORK_AP_CHANNEL_5G_DFS_1 = 132

WIFI_NETWORK_AP_CHANNEL_2G_2 = 2
WIFI_NETWORK_AP_CHANNEL_5G_2 = 40
WIFI_NETWORK_AP_CHANNEL_5G_DFS_2 = 136

class WifiStaConcurrencyNetworkRequestTest(WifiBaseTest):
    """STA + STA Tests for concurrency between intenet connectivity &
    peer to peer connectivity using NetworkRequest with WifiNetworkSpecifier
    API surface.

    Test Bed Requirement:
    * one Android device
    * Several Wi-Fi networks visible to the device, including an open Wi-Fi
      network.
    """
    def __init__(self, configs):
        super().__init__(configs)
        self.enable_packet_log = True
        self.p2p_key = None

    def setup_class(self):
        super().setup_class()

        self.dut = self.android_devices[0]
        wutils.wifi_test_device_init(self.dut)
        req_params = ["sta_concurrency_supported_models", "wifi6_models"]
        opt_param = [
            "open_network", "reference_networks"
        ]
        self.unpack_userparams(
            req_param_names=req_params, opt_param_names=opt_param)

    def setup_test(self):
        asserts.skip_if(
          self.dut.model not in self.sta_concurrency_supported_models,
          "Device %s doesn't support STA+STA, skipping tests")
        asserts.skip_if(
            "OpenWrtAP" not in self.user_params,
            "Setup doesn't support OpenWrt AP, skipping tests")

        super().setup_test()
        self.dut.droid.wakeLockAcquireBright()
        self.dut.droid.wakeUpNow()
        self.remove_approvals()
        self.clear_user_disabled_networks()
        wutils.wifi_toggle_state(self.dut, True)
        self.dut.ed.clear_all_events()

    def teardown_test(self):
        super().teardown_test()
        self.disconnect_both()
        self.dut.droid.wakeLockRelease()
        self.dut.droid.goToSleepNow()
        self.dut.droid.wifiDisconnect()
        wutils.reset_wifi(self.dut)
        # Ensure we disconnected from the current network before the next test.
        if self.dut.droid.wifiGetConnectionInfo()["supplicant_state"] != "disconnected":
            wutils.wait_for_disconnect(self.dut)
        wutils.wifi_toggle_state(self.dut, False)
        self.dut.ed.clear_all_events()
        # Reset access point state.
        for ap in self.access_points:
            ap.close()

    def teardown_class(self):
        if "AccessPoint" in self.user_params:
            del self.user_params["reference_networks"]
            del self.user_params["open_network"]

    """Helper Functions"""
    def remove_approvals(self):
        self.dut.log.debug("Removing all approvals from sl4a app")
        self.dut.adb.shell(
            "cmd wifi network-requests-remove-user-approved-access-points"
            + " " + SL4A_APK_NAME)

    def clear_user_disabled_networks(self):
        self.dut.log.debug("Clearing user disabled networks")
        self.dut.adb.shell(
            "cmd wifi clear-user-disabled-networks")

    def register_network_callback_for_internet(self):
        self.dut.log.debug("Registering network callback for wifi internet connectivity")
        network_request = {
            cconsts.NETWORK_CAP_TRANSPORT_TYPE_KEY :
                cconsts.NETWORK_CAP_TRANSPORT_WIFI,
            cconsts.NETWORK_CAP_CAPABILITY_KEY :
                [cconsts.NETWORK_CAP_CAPABILITY_INTERNET]
        }
        key = self.dut.droid.connectivityRegisterNetworkCallback(network_request)
        return key

    def connect_to_internet_and_wait_for_on_available(self, network):
        self.dut.log.info("Triggering internet connection after registering "
                           "network callback")
        self.internet_request_key = (
            self.register_network_callback_for_internet())
        wutils.connect_to_wifi_network(self.dut, network)
        # Ensure that the internet connection completed and we got the
        # ON_AVAILABLE callback.
        autils.wait_for_event_with_keys(
            self.dut,
            cconsts.EVENT_NETWORK_CALLBACK,
            20,
            (cconsts.NETWORK_CB_KEY_ID, self.internet_request_key),
            (cconsts.NETWORK_CB_KEY_EVENT, cconsts.NETWORK_CB_AVAILABLE))
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    def connect_to_p2p_and_wait_for_on_available(self, network):
        self.p2p_key = wutils.wifi_connect_using_network_request(self.dut,
                                                                 network,
                                                                 network)
        wutils.verify_11ax_wifi_connection(
            self.dut, self.wifi6_models, "wifi6_ap" in self.user_params)

    def ensure_both_connections_are_active(self):
        self.dut.log.info("Ensuring both connections are active")
        network_caps = (
            self.dut.droid.connectivityNetworkGetAllCapabilities())
        self.dut.log.info("Active network caps: %s", network_caps)
        num_wifi_networks = 0
        for network_cap in network_caps:
            transport_types = (
                network_cap[cconsts.NETWORK_CAP_TRANSPORT_TYPE_KEY])
            if cconsts.NETWORK_CAP_TRANSPORT_WIFI in transport_types:
              num_wifi_networks += 1
              self.dut.log.info("Wifi connection %s: %s",
                                num_wifi_networks, network_cap)
        asserts.assert_equal(2, num_wifi_networks, "Expected 2 wifi networks")


    def ensure_both_connections_are_active_and_dont_disconnect(self):
        self.ensure_both_connections_are_active()

        # Don't use the key_id in event to ensure there are no disconnects
        # from either connection.
        self.dut.log.info("Ensuring no connection loss")
        autils.fail_on_event_with_keys(
            self.dut,
            cconsts.EVENT_NETWORK_CALLBACK,
            20,
            (cconsts.NETWORK_CB_KEY_EVENT, cconsts.NETWORK_CB_LOST))

    def disconnect_both(self):
        self.dut.log.info("Disconnecting both connections")
        if self.p2p_key:
            asserts.assert_true(
                self.dut.droid.connectivityUnregisterNetworkCallback(
                    self.p2p_key),
                "Failed to release the p2p request")
            self.p2p_key = None
        self.dut.droid.wifiDisconnect()

    def configure_ap(self,
                     channel_2g=None,
                     channel_5g=None,
                     channel_2g_ap2=None,
                     channel_5g_ap2=None,
                     mirror_ap=False,
                     ap_count=1):
        """Configure ap based on test requirements."""
        if ap_count==1:
            self.configure_openwrt_ap_and_start(
                wpa_network=True,
                channel_2g=WIFI_NETWORK_AP_CHANNEL_2G_1,
                channel_5g=WIFI_NETWORK_AP_CHANNEL_5G_1,
                ap_count=1)
        elif ap_count == 2 and channel_2g_ap2:
            self.configure_openwrt_ap_and_start(
                wpa_network=True,
                channel_2g=WIFI_NETWORK_AP_CHANNEL_2G_1,
                channel_2g_ap2=WIFI_NETWORK_AP_CHANNEL_2G_2,
                mirror_ap=mirror_ap,
                ap_count=2)
        elif ap_count == 2 and channel_5g_ap2:
            self.configure_openwrt_ap_and_start(
                wpa_network=True,
                channel_5g=WIFI_NETWORK_AP_CHANNEL_5G_1,
                channel_5g_ap2=WIFI_NETWORK_AP_CHANNEL_5G_2,
                mirror_ap=mirror_ap,
                ap_count=2)

    @test_tracker_info(uuid="64a6c35f-d45d-431f-83e8-7fcfaef943e2")
    def test_connect_to_2g_p2p_while_connected_to_5g_internet(self):
        """
        Initiates a connection to a peer to peer network via network request while
        already connected to an internet connectivity network.

        Steps:
        1. Setup 5G & 2G band WPA-PSK networks.
        2. Connect to WPA-PSK 5G network for internet connectivity.
        3. Send a network specifier with the specific SSID/credentials of
           WPA-PSK 2G network.
        4. Wait for platform to scan and find matching networks.
        5. Simulate user selecting the network.
        6. Ensure that the device connects to the network.
        7. Ensure that the device remains connected to both the networks.
        8. Disconnect both connections.
        """
        self.configure_ap(
            channel_2g=WIFI_NETWORK_AP_CHANNEL_2G_1,
            channel_5g=WIFI_NETWORK_AP_CHANNEL_5G_1,
            ap_count=1)

        self.connect_to_internet_and_wait_for_on_available(
            self.wpa_networks[0]["5g"])
        self.connect_to_p2p_and_wait_for_on_available(
            self.wpa_networks[0]["2g"])

        self.ensure_both_connections_are_active_and_dont_disconnect()


    @test_tracker_info(uuid="aa8af713-5d97-4f05-8104-e697f0055d6e")
    def test_connect_to_2g_internet_while_connected_to_5g_p2p(self):
        """
        Initiates a connection to a peer to peer network via network request while
        already connected to an internet connectivity network.

        Steps:
        1. Setup 5G & 2G band WPA-PSK networks.
        2. Send a network specifier with the specific SSID/credentials of
           WPA-PSK 5G network.
        3. Wait for platform to scan and find matching networks.
        4. Simulate user selecting the network.
        5. Ensure that the device connects to the network.
        6. Connect to WPA-PSK 2G network for internet connectivity.
        7. Ensure that the device remains connected to both the networks.
        8. Disconnect both connections.
        """
        self.configure_ap(
            channel_2g=WIFI_NETWORK_AP_CHANNEL_2G_1,
            channel_5g=WIFI_NETWORK_AP_CHANNEL_5G_1,
            ap_count=1)

        self.connect_to_p2p_and_wait_for_on_available(
            self.wpa_networks[0]["5g"])
        self.connect_to_internet_and_wait_for_on_available(
            self.wpa_networks[0]["2g"])

        self.ensure_both_connections_are_active_and_dont_disconnect()


    @test_tracker_info(uuid="64dd09a7-28f6-4000-b1a1-10302922641b")
    def test_connect_to_2g_internet_while_connected_to_2g_p2p(self):
        """
        Initiates a connection to a peer to peer network via network request while
        already connected to an internet connectivity network.

        Steps:
        1. Setup 2 5G & 2G band WPA-PSK networks.
        2. Send a network specifier with the specific SSID/credentials of
           WPA-PSK 2G network.
        3. Wait for platform to scan and find matching networks.
        4. Simulate user selecting the network.
        5. Ensure that the device connects to the network.
        6. Connect to WPA-PSK 2G network for internet connectivity.
        7. Ensure that the device remains connected to both the networks.
        8. Disconnect both connections.
        """
        self.configure_ap(
            channel_2g=WIFI_NETWORK_AP_CHANNEL_2G_1,
            channel_2g_ap2=WIFI_NETWORK_AP_CHANNEL_2G_2,
            ap_count=2)

        self.connect_to_p2p_and_wait_for_on_available(
            self.wpa_networks[0]["2g"])
        self.connect_to_internet_and_wait_for_on_available(
            self.wpa_networks[1]["2g"])

        self.ensure_both_connections_are_active_and_dont_disconnect()


    @test_tracker_info(uuid="0c3df2f1-7311-4dd2-b0dc-1de4bd731495")
    def test_connect_to_5g_internet_while_connected_to_5g_p2p(self):
        """
        Initiates a connection to a peer to peer network via network request while
        already connected to an internet connectivity network.

        Steps:
        1. Setup 2 5G & 2G band WPA-PSK networks.
        2. Send a network specifier with the specific SSID/credentials of
           WPA-PSK 5G network.
        3. Wait for platform to scan and find matching networks.
        4. Simulate user selecting the network.
        5. Ensure that the device connects to the network.
        6. Connect to WPA-PSK 5G network for internet connectivity.
        7. Ensure that the device remains connected to both the networks.
        8. Disconnect both connections.
        """
        self.configure_ap(
            channel_5g=WIFI_NETWORK_AP_CHANNEL_5G_1,
            channel_5g_ap2=WIFI_NETWORK_AP_CHANNEL_5G_2,
            ap_count=2)

        self.connect_to_p2p_and_wait_for_on_available(
            self.wpa_networks[0]["5g"])
        self.connect_to_internet_and_wait_for_on_available(
            self.wpa_networks[1]["5g"])

        self.ensure_both_connections_are_active_and_dont_disconnect()

    @test_tracker_info(uuid="be22f7f8-b761-4f40-8d10-ff802761cb8b")
    def test_connect_to_5g_dfs_internet_while_connected_to_5g_dfs_p2p(self):
        """
        Initiates a connection to a peer to peer network via network request while
        already connected to an internet connectivity network.

        Steps:
        1. Setup 2 5G-DFS & 2G band WPA-PSK networks.
        2. Send a network specifier with the specific SSID/credentials of
           WPA-PSK 5G-DFS network.
        3. Wait for platform to scan and find matching networks.
        4. Simulate user selecting the network.
        5. Ensure that the device connects to the network.
        6. Connect to WPA-PSK 5G network for internet connectivity.
        7. Ensure that the device remains connected to both the networks.
        8. Disconnect both connections.
        """
        self.configure_ap(
            channel_5g=WIFI_NETWORK_AP_CHANNEL_5G_DFS_1,
            channel_5g_ap2=WIFI_NETWORK_AP_CHANNEL_5G_DFS_2,
            ap_count=2)

        self.connect_to_p2p_and_wait_for_on_available(
            self.wpa_networks[0]["5g"])
        self.connect_to_internet_and_wait_for_on_available(
            self.wpa_networks[1]["5g"])

        self.ensure_both_connections_are_active_and_dont_disconnect()

    @test_tracker_info(uuid="dc390b3f-2856-4c96-880b-9732e3dc228f")
    def test_connect_to_2g_internet_while_connected_to_2g_p2p_same_ssid(self):
        """
        Initiates a connection to a peer to peer network via network request while
        already connected to an internet connectivity network.

        Steps:
        1. Setup 2 5G & 2G band WPA-PSK networks with the same SSID.
        2. Send a network specifier with the specific SSID/credentials of
           WPA-PSK 2G network.
        3. Wait for platform to scan and find matching networks.
        4. Simulate user selecting the network.
        5. Ensure that the device connects to the network.
        6. Connect to WPA-PSK 2G network for internet connectivity.
        7. Ensure that the device remains connected to both the networks.
        8. Disconnect both connections.
        """
        self.configure_ap(
            channel_2g=WIFI_NETWORK_AP_CHANNEL_2G_1,
            channel_2g_ap2=WIFI_NETWORK_AP_CHANNEL_2G_2,
            mirror_ap=True,
            ap_count=2)

        self.connect_to_p2p_and_wait_for_on_available(
            self.wpa_networks[0]["2g"])
        self.connect_to_internet_and_wait_for_on_available(
            self.wpa_networks[0]["2g"])

        self.ensure_both_connections_are_active_and_dont_disconnect()

    @test_tracker_info(uuid="033d324d-94e0-440e-9532-993bc2682269")
    def test_connect_to_5g_p2p_while_connected_to_5g_internet_same_ssid(self):
        """
        Initiates a connection to a peer to peer network via network request while
        already connected to an internet connectivity network.

        Steps:
        1. Setup 2 5G & 2G band WPA-PSK networks with the same SSID.
        2. Connect to WPA-PSK 5G network for internet connectivity.
        3. Send a network specifier with the specific SSID/credentials of
           WPA-PSK 5G network (with the other bssid).
        4. Wait for platform to scan and find matching networks.
        5. Simulate user selecting the network.
        6. Ensure that the device connects to the network.
        7. Ensure that the device remains connected to both the networks.
        8. Disconnect both connections.
        """
        self.configure_ap(
            channel_5g=WIFI_NETWORK_AP_CHANNEL_5G_1,
            channel_5g_ap2=WIFI_NETWORK_AP_CHANNEL_5G_2,
            mirror_ap=True,
            ap_count=2)

        self.connect_to_internet_and_wait_for_on_available(
            self.wpa_networks[0]["5g"])

        ssid = self.wpa_networks[0]["5g"][WifiEnums.SSID_KEY]
        bssids = [self.bssid_map[0]["5g"][ssid], self.bssid_map[1]["5g"][ssid]]
        # Find the internet connection bssid.
        wifi_info = self.dut.droid.wifiGetConnectionInfo()
        connected_bssid = wifi_info[WifiEnums.BSSID_KEY].upper()
        # Find the bssid of the other access point with same ssid.
        p2p_bssid = set(bssids) - {connected_bssid}

        # Construct specifier for the other bssid.
        network_specifier_with_bssid = self.wpa_networks[0]["5g"].copy()
        network_specifier_with_bssid[WifiEnums.BSSID_KEY] = next(iter(p2p_bssid))
        self.connect_to_p2p_and_wait_for_on_available(
            network_specifier_with_bssid)

        self.ensure_both_connections_are_active_and_dont_disconnect()
