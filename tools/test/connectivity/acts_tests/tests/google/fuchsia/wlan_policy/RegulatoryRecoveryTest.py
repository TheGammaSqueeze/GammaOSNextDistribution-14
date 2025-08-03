#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
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
#   limitations under the License

from acts import signals
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest


class RegulatoryRecoveryTest(WifiBaseTest):
    """Tests the policy layer's response to setting country code.

    Test Bed Requirements:
    * One Fuchsia device that is capable of operating as a WLAN client and AP.

    Example Config:
    "regulatory_recovery_test_params": {
        "country_code": "US"
    }

    If no configuration information is provided, the test will default to
    toggling between WW and US.
    """

    def setup_class(self):
        super().setup_class()
        if len(self.fuchsia_devices) < 1:
            raise EnvironmentError("No Fuchsia devices found.")

        self.config_test_params = self.user_params.get(
            "regulatory_recovery_test_params", {})
        self.country_code = self.config_test_params.get("country_code", "US")
        self.negative_test = self.config_test_params.get(
            "negative_test", False)

        for fd in self.fuchsia_devices:
            fd.configure_wlan(association_mechanism='policy')

    def teardown_class(self):
        if not self.negative_test:
            for fd in self.fuchsia_devices:
                fd.wlan_controller.set_country_code(self.country_code)

        super().teardown_class()

    def setup_test(self):
        for fd in self.fuchsia_devices:
            # Remove all network configs.  These tests do not require
            # connecting to a network, they merely verify whether or not client
            # mode is functional.
            if not fd.wlan_policy_controller.remove_all_networks():
                raise EnvironmentError(
                    "Failed to remove all networks in setup")

            # To ensure that DUTs are initially in a known state, set all of
            # their PHYs to world-wide mode.  Also disable client and AP
            # functionality so that there is no automated WLAN behavior.
            fd.wlan_controller.set_country_code("WW")
            fd.wlan_policy_controller.stop_client_connections()
            fd.sl4f.wlan_ap_policy_lib.wlanStopAllAccessPoint()

    def set_country_code(self, fd):
        try:
            fd.wlan_controller.set_country_code(self.country_code)
        except EnvironmentError as e:
            if self.negative_test:
                # In the negative case, setting the country code for an
                # invalid country should fail.
                pass
            else:
                # If this is not a negative test case, re-raise the
                # exception.
                raise e
        else:
            # The negative test case should have failed to set the country
            # code and the positive test case should succeed.
            if self.negative_test:
                raise EnvironmentError(
                    "Setting invalid country code succeeded.")
            else:
                pass

    def test_interfaces_not_recreated_when_initially_disabled(self):
        """This test ensures that after a new regulatory region is applied
        while client connections and access points are disabled, no new
        interfaces are automatically recreated.
        """
        for fd in self.fuchsia_devices:
            # Set the region code.
            self.set_country_code(fd)

            # Reset the listeners and verify the current state.
            fd.sl4f.wlan_policy_lib.wlanSetNewListener()
            fd.sl4f.wlan_ap_policy_lib.wlanSetNewListener()

            # Verify that the client and AP are still stopped.
            client_state = fd.sl4f.wlan_policy_lib.wlanGetUpdate()
            if client_state["error"]:
                raise signals.TestFailure(
                    "error querying client state: {}".format(
                        client_state["error"]))
            elif client_state["result"]["state"] != "ConnectionsDisabled":
                raise signals.TestFailure(
                    "client connections in unexpected state: {}".format(
                        client_state["result"]["state"]))

            ap_state = fd.sl4f.wlan_ap_policy_lib.wlanGetUpdate()
            if ap_state["error"]:
                raise signals.TestFailure("error querying AP state: {}".format(
                    ap_state["error"]))

            ap_updates = ap_state["result"]
            if ap_updates:
                raise signals.TestFailure(
                    "AP in unexpected state: {}".format(ap_updates))

    def test_interfaces_recreated_when_initially_enabled(self):
        """This test ensures that after a new regulatory region is applied
        while client connections and access points are enabled, all
        interfaces are recreated.
        """
        test_ssid = "test_ssid"
        test_security_type = "none"
        for fd in self.fuchsia_devices:
            # Start client connections and start an AP before setting the
            # country code.
            fd.wlan_policy_controller.start_client_connections()
            fd.sl4f.wlan_ap_policy_lib.wlanStartAccessPoint(
                test_ssid, test_security_type, "", "local_only", "any")

            # Set the country code.
            self.set_country_code(fd)

            # Reset the listeners and verify the current state.
            fd.sl4f.wlan_policy_lib.wlanSetNewListener()
            fd.sl4f.wlan_ap_policy_lib.wlanSetNewListener()

            # Verify that client connections are enabled and the AP is brought
            # up again.
            client_state = fd.sl4f.wlan_policy_lib.wlanGetUpdate()
            if client_state["error"]:
                raise signals.TestFailure(
                    "error querying client state: {}".format(
                        client_state["error"]))
            elif client_state["result"]["state"] != "ConnectionsEnabled":
                raise signals.TestFailure(
                    "client connections in unexpected state: {}".format(
                        client_state["result"]["state"]))

            ap_state = fd.sl4f.wlan_ap_policy_lib.wlanGetUpdate()
            if ap_state["error"]:
                raise signals.TestFailure("error querying AP state: {}".format(
                    ap_state["error"]))

            ap_updates = ap_state["result"]
            if len(ap_updates) != 1:
                raise signals.TestFailure(
                    "No APs are running: {}".format(ap_updates))
            else:
                if ap_updates[0]["id"]["ssid"] != test_ssid or ap_updates[0][
                        "id"]["type_"].lower() != test_security_type:
                    raise signals.TestFailure(
                        "AP in unexpected state: {}".format(ap_updates[0]))
