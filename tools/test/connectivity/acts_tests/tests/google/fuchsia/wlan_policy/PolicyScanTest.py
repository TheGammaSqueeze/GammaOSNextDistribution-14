#!/usr/bin/env python3.4
#
# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
#
"""This test exercises the Scan functionality for the WLAN Policy API."""

from datetime import datetime

from acts import signals
from acts.controllers.ap_lib import (hostapd_ap_preset, hostapd_bss_settings,
                                     hostapd_constants, hostapd_security)
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest


class PolicyScanTest(WifiBaseTest):
    """WLAN policy scan test class.

    Test Bed Requirement:
    * One or more Fuchsia devices
    * One Whirlwind Access Point
    """

    def setup_class(self):
        super().setup_class()
        if len(self.fuchsia_devices) < 1:
            raise signals.TestFailure("No fuchsia devices found.")
        for fd in self.fuchsia_devices:
            fd.configure_wlan(association_mechanism='policy',
                              preserve_saved_networks=True)
        if len(self.access_points) < 1:
            raise signals.TestFailure("No access points found.")
        # Prepare the AP
        self.access_point = self.access_points[0]
        self.access_point.stop_all_aps()
        # Generate network params.
        bss_settings_2g = []
        bss_settings_5g = []
        open_network = self.get_open_network(False, [])
        self.open_network_2g = open_network["2g"]
        self.open_network_5g = open_network["5g"]
        wpa2_settings = self.get_psk_network(False, [])
        self.wpa2_network_2g = wpa2_settings["2g"]
        self.wpa2_network_5g = wpa2_settings["5g"]
        bss_settings_2g.append(
            hostapd_bss_settings.BssSettings(
                name=self.wpa2_network_2g["SSID"],
                ssid=self.wpa2_network_2g["SSID"],
                security=hostapd_security.Security(
                    security_mode=self.wpa2_network_2g["security"],
                    password=self.wpa2_network_2g["password"])))
        bss_settings_5g.append(
            hostapd_bss_settings.BssSettings(
                name=self.wpa2_network_5g["SSID"],
                ssid=self.wpa2_network_5g["SSID"],
                security=hostapd_security.Security(
                    security_mode=self.wpa2_network_5g["security"],
                    password=self.wpa2_network_5g["password"])))
        self.ap_2g = hostapd_ap_preset.create_ap_preset(
            iface_wlan_2g=self.access_points[0].wlan_2g,
            iface_wlan_5g=self.access_points[0].wlan_5g,
            channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
            ssid=self.open_network_2g["SSID"],
            bss_settings=bss_settings_2g)
        self.ap_5g = hostapd_ap_preset.create_ap_preset(
            iface_wlan_2g=self.access_points[0].wlan_2g,
            iface_wlan_5g=self.access_points[0].wlan_5g,
            channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
            ssid=self.open_network_5g["SSID"],
            bss_settings=bss_settings_5g)
        # Start the networks
        self.access_point.start_ap(hostapd_config=self.ap_2g)
        self.access_point.start_ap(hostapd_config=self.ap_5g)
        # Save the SSIDs
        self.all_ssids = [
            self.open_network_2g["SSID"],
            self.wpa2_network_2g["SSID"],
            self.open_network_5g["SSID"],
            self.wpa2_network_5g["SSID"],
        ]

    def setup_test(self):
        for fd in self.fuchsia_devices:
            # stub for setting up all the fuchsia devices in the testbed.
            return fd.wlan_policy_controller.remove_all_networks_and_wait_for_no_connections(
            )

    def teardown_test(self):
        for fd in self.fuchsia_devices:
            # stub until policy layer has something useful to use here.
            pass

    def teardown_class(self):
        pass

    def on_fail(self, test_name, begin_time):
        for fd in self.fuchsia_devices:
            try:
                fd.take_bug_report(test_name, begin_time)
                fd.get_log(test_name, begin_time)
            except Exception:
                pass

            try:
                if fd.device.hard_reboot_on_fail:
                    fd.hard_power_cycle(self.pdu_devices)
            except AttributeError:
                pass

    """Helper Functions"""

    def perform_scan(self, fd):
        """ Initiates scan on a Fuchsia device and returns results

        Args:
            fd: A fuchsia device

        Raises:
            signals.TestFailure: if an error is reported by the device during
            the scan

        Returns:
            A list of scan results
        """
        start_time = datetime.now()

        scan_response = fd.sl4f.wlan_policy_lib.wlanScanForNetworks()

        # first check if we received an error
        if scan_response.get("error") is not None:
            # the response indicates an error - log and raise failure
            raise signals.TestFailure("Aborting test - scan failed with "
                                      "error: %s" % scan_response.get("error"))

        # the scan command did not get an error response - go ahead
        # and check for scan results
        scan_results = scan_response["result"]
        total_time_ms = (datetime.now() - start_time).total_seconds() * 1000

        self.log.info("scan contained %d results", len(scan_results))
        self.log.info("scan time: %d ms", total_time_ms)

        return scan_results

    def connect_to_network(self, wlan_network_params, fd):
        """ Connects the Fuchsia device to the specified network

        Args:
            wlan_network_params: A dictionary containing wlan information.
            fd: A fuchsia device

        Raises:
            signals.TestFailure: if the device fails to connect
        """
        target_ssid = wlan_network_params["SSID"]
        target_pwd = wlan_network_params.get("password")
        target_security = wlan_network_params.get("security")

        # TODO(mnck): use the Policy version of this call, when it is available.
        connection_response = fd.wlan_policy_controller.save_and_connect(
            target_ssid, target_security, password=target_pwd)
        if not connection_response:
            raise signals.TestFailure("Aborting test - Connect call failed")
        self.log.info("Network connection successful.")

    def assert_network_is_in_results(self, scan_results, *, ssid):
        """ Verified scan results contain a specified network

        Args:
            scan_results: Scan results from a fuchsia Policy API scan
            ssid: SSID for network that should be in the results

        Raises:
            signals.TestFailure: if the network is not present in the scan
            results
        """
        if ssid not in scan_results:
            raise signals.TestFailure(
                'Network "%s" was not found in scan results: %s', ssid,
                scan_results)

    """Tests"""

    def test_basic_scan_request(self):
        """Verify a scan returns all expected networks"""
        for fd in self.fuchsia_devices:
            scan_results = self.perform_scan(fd)
            if len(scan_results) == 0:
                raise signals.TestFailure("Scan failed or did not "
                                          "find any networks")
            for ssid in self.all_ssids:
                self.assert_network_is_in_results(scan_results, ssid=ssid)

    def test_scan_while_connected_open_network_2g(self):
        """Connect to an open 2g network and perform a scan"""
        for fd in self.fuchsia_devices:
            self.connect_to_network(self.open_network_2g, fd)
            scan_results = self.perform_scan(fd)
            for ssid in self.all_ssids:
                self.assert_network_is_in_results(scan_results, ssid=ssid)

    def test_scan_while_connected_wpa2_network_2g(self):
        """Connect to a WPA2 2g network and perform a scan"""
        for fd in self.fuchsia_devices:
            self.connect_to_network(self.wpa2_network_2g, fd)
            scan_results = self.perform_scan(fd)
            for ssid in self.all_ssids:
                self.assert_network_is_in_results(scan_results, ssid=ssid)

    def test_scan_while_connected_open_network_5g(self):
        """Connect to an open 5g network and perform a scan"""
        for fd in self.fuchsia_devices:
            self.connect_to_network(self.open_network_5g, fd)
            scan_results = self.perform_scan(fd)
            for ssid in self.all_ssids:
                self.assert_network_is_in_results(scan_results, ssid=ssid)

    def test_scan_while_connected_wpa2_network_5g(self):
        """Connect to a WPA2 5g network and perform a scan"""
        for fd in self.fuchsia_devices:
            self.connect_to_network(self.wpa2_network_5g, fd)
            scan_results = self.perform_scan(fd)
            for ssid in self.all_ssids:
                self.assert_network_is_in_results(scan_results, ssid=ssid)
