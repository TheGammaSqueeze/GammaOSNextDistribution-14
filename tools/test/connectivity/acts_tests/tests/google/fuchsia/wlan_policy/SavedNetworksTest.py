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
#   limitations under the License.
"""
A test that saves various networks and verifies the behavior of save, get, and
remove through the ClientController API of WLAN policy.
"""

from acts import signals
from acts.controllers.access_point import setup_ap
from acts.controllers.ap_lib import hostapd_constants
from acts.controllers.ap_lib import hostapd_security
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts.utils import rand_ascii_str, rand_hex_str

PSK_LEN = 64
TIME_WAIT_FOR_DISCONNECT = 30
TIME_WAIT_FOR_CONNECT = 30

STATE_CONNECTED = "Connected"
STATE_CONNECTING = "Connecting"
CONNECTIONS_ENABLED = "ConnectionsEnabled"
CONNECTIONS_DISABLED = "ConnectionsDisabled"
SECURITY_NONE = "none"
WEP = "wep"
WPA = "wpa"
WPA2 = "wpa2"
WPA3 = "wpa3"
CREDENTIAL_TYPE_NONE = "none"
PASSWORD = "password"
PSK = "psk"
CREDENTIAL_VALUE_NONE = ""


class SavedNetworksTest(WifiBaseTest):
    """WLAN policy commands test class.

    Test Bed Requirement:
    * One or more Fuchsia devices
    * One Access Point
    """

    def setup_class(self):
        super().setup_class()
        # Keep track of whether we have started an access point in a test
        if len(self.fuchsia_devices) < 1:
            raise EnvironmentError("No Fuchsia devices found.")
        for fd in self.fuchsia_devices:
            fd.configure_wlan(association_mechanism='policy',
                              preserve_saved_networks=True)

    def setup_test(self):
        for fd in self.fuchsia_devices:
            if not fd.wlan_policy_controller.remove_all_networks():
                raise EnvironmentError(
                    "Failed to remove all networks in setup")
        self.access_points[0].stop_all_aps()

    def teardown_class(self):
        for fd in self.fuchsia_devices:
            fd.wlan_policy_controller.remove_all_networks()
        self.access_points[0].stop_all_aps()

    def save_bad_network(self, fd, ssid, security_type, password=""):
        """ Saves a network as specified on the given device and verify that we
        Args:
            fd: The Fuchsia device to save the network on
            ssid: The SSID or name of the network to save.
            security_type: The security type to save the network as, ie "none",
                        "wep", "wpa", "wpa2", or "wpa3"
            password: The password to save for the network. Empty string represents
                    no password, and PSK should be provided as 64 character hex string.
        """
        if fd.wlan_policy_controller.save_network(ssid,
                                                  security_type,
                                                  password=password):
            self.log.info(
                "Attempting to save bad network config %s did not give an error"
                % ssid)
            raise signals.TestFailure("Failed to get error saving bad network")

    def check_get_saved_network(self, fd, ssid, security_type, credential_type,
                                credential_value):
        """ Verify that get saved networks sees the single specified network. Used
            for the tests that save and get a single network. Maps security types of
            expected and actual to be case insensitive.
        Args:
            fd: Fuchsia device to run on.
            ssid: The name of the network to check for.
            security_type: The security of the network, ie "none", "wep", "wpa",
                        "wpa2", or "wpa3".
            credential_type: The type of credential saved for the network, ie
                            "none", "password", or "psk".
            credential_value: The actual credential, or "" if there is no credential.
        """
        expected_networks = [{
            "ssid": ssid,
            "security_type": security_type,
            "credential_type": credential_type,
            "credential_value": credential_value
        }]
        self.check_saved_networks(fd, expected_networks)

    def check_saved_networks(self, fd, expected_networks):
        """ Verify that the saved networks we get from the device match the provided
            list of networks.
        Args:
            fd: The Fuchsia device to run on.
            expected_networks: The list of networks we expect to get from the device,
                            unordered and in the same format as we would get:
                            [{"credential_type": _, "credential_value": _,
                            "security_type": _, "ssid": _}, ...] There should be
                            no duplicates in expected networks.
        """
        actual_networks = list(
            map(self.lower_case_network,
                fd.wlan_policy_controller.get_saved_networks()))
        expected_networks = list(
            map(self.lower_case_network,
                fd.wlan_policy_controller.get_saved_networks()))

        if len(actual_networks) != len(expected_networks):
            self.log.info(
                "Number of expected saved networks does not match the actual number."
                "Expected: %d, actual: %d" %
                (len(actual_networks), len(expected_networks)))
            raise signals.TestFailure(
                "Failed to get the expected number of saved networks")
        for network in actual_networks:
            if network not in expected_networks:
                self.log.info(
                    "Actual and expected networks do not match. Actual: %s,\n"
                    "Expected: %s" % (actual_networks, expected_networks))
                raise signals.TestFailure("Got an unexpected saved network")

    def lower_case_network(self, network):
        if "security_type" not in network:
            self.log.error("Missing security type in network %s" % network)
            raise signals.TestFailure("Network is missing security type")
        if "credential_type" not in network:
            self.log.error("Missing credential type in network %s" % network)
            raise signals.TestFailure("Network is missing credential type")
        {"ssid": network["ssid"], "security_type": network["security_type"]}

    def save_and_check_network(self, ssid, security_type, password=""):
        """ Perform a test for saving, getting, and removing a single network on each
            device.
        Args:
            ssid: The network name to use.
            security_type: The security of the network as a string, ie "none",
                        "wep", "wpa", "wpa2", or "wpa3" (case insensitive)
            password: The password of the network. PSK should be given as 64
                    hexadecimal characters and none should be an empty string.
        """
        for fd in self.fuchsia_devices:
            if not fd.wlan_policy_controller.save_network(
                    ssid, security_type, password=password):
                raise signals.TestFailure("Failed to save network")
            self.check_get_saved_network(fd, ssid, security_type,
                                         self.credentialType(password),
                                         password)

    def start_ap(self, ssid, security_type, password=None, hidden=False):
        """ Starts an access point.
        Args:
            ssid: the SSID of the network to broadcast
            security_type: the security type of the network to be broadcasted. This can be
                None, "wep" "wpa", "wpa2", or "wpa3" (or from hostapd_constants.py)
            password: the password to connect to the broadcasted network. The password is ignored
                if security type is none.
        """
        # Put together the security configuration of the network to be
        # broadcasted. Open networks are represented by no security.
        if security_type == None or security_type.upper() == SECURITY_NONE:
            security = None
        else:
            security = hostapd_security.Security(security_mode=security_type,
                                                 password=password)

        if len(self.access_points) > 0:
            # Create an AP with default values other than the specified values.
            setup_ap(self.access_points[0],
                     'whirlwind',
                     hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                     ssid,
                     security=security)

        else:
            self.log.error(
                "No access point available for test, please check config")
            raise EnvironmentError("Failed to set up AP for test")

    def credentialType(self, credentialValue):
        """ Returns the type of the credential to compare against values reported """
        if len(credentialValue) == PSK_LEN:
            return PSK
        elif len(credentialValue) == 0:
            return "none"
        else:
            return PASSWORD

    def same_network_identifier(self, net_id, ssid, security_type):
        """ Returns true if the network id is made of the given ssid and security
            type, and false otherwise. Security type check is case insensitive.
        """
        return net_id["ssid"] == ssid and net_id["type_"].upper(
        ) == security_type.upper()

    """Tests"""

    def test_open_network_with_password(self):
        for fd in self.fuchsia_devices:
            # Save an open network with a password and verify that it fails to
            # save.
            self.save_bad_network(fd, rand_ascii_str(10), SECURITY_NONE,
                                  rand_ascii_str(8))
            self.check_saved_networks(fd, {})

    def test_open_network(self):
        ssid = rand_ascii_str(10)
        self.save_and_check_network(ssid, SECURITY_NONE)

    def test_network_with_psk(self):
        ssid = rand_ascii_str(11)
        # PSK are translated from hex to bytes when saved, and when returned
        # by get_saved_networks it will be lower case.
        psk = rand_hex_str(PSK_LEN).lower()
        self.save_and_check_network(ssid, WPA2, psk)

    def test_wep_network(self):
        ssid = rand_ascii_str(12)
        password = rand_ascii_str(13)
        self.save_and_check_network(ssid, WEP, password)

    def test_wpa2_network(self):
        ssid = rand_ascii_str(9)
        password = rand_ascii_str(15)
        self.save_and_check_network(ssid, WPA2, password)

    def test_wpa_network(self):
        ssid = rand_ascii_str(16)
        password = rand_ascii_str(9)
        self.save_and_check_network(ssid, WPA, password)

    def test_wpa3_network(self):
        ssid = rand_ascii_str(9)
        password = rand_ascii_str(15)
        self.save_and_check_network(ssid, WPA3, password)

    def test_save_network_persists(self):
        ssid = rand_ascii_str(10)
        security = WPA2
        password = rand_ascii_str(10)
        for fd in self.fuchsia_devices:
            if not fd.wlan_policy_controller.save_network(
                    ssid, security, password=password):
                raise signals.TestFailure("Failed to save network")
            # Reboot the device. The network should be persistently saved
            # before the command is completed.
            fd.reboot()
            self.check_get_saved_network(fd, ssid, security, PASSWORD,
                                         password)

    def test_same_ssid_diff_security(self):
        for fd in self.fuchsia_devices:
            saved_networks = fd.wlan_policy_controller.get_saved_networks()
            ssid = rand_ascii_str(19)
            password = rand_ascii_str(12)
            if not fd.wlan_policy_controller.save_network(
                    ssid, WPA2, password=password):
                raise signals.TestFailure("Failed to save network")
            saved_networks.append({
                "ssid": ssid,
                "security_type": WPA2,
                "credential_type": PASSWORD,
                "credential_value": password
            })
            if not fd.wlan_policy_controller.save_network(ssid, SECURITY_NONE):
                raise signals.TestFailure("Failed to save network")
            saved_networks.append({
                "ssid": ssid,
                "security_type": SECURITY_NONE,
                "credential_type": CREDENTIAL_TYPE_NONE,
                "credential_value": CREDENTIAL_VALUE_NONE
            })
            actual_networks = fd.wlan_policy_controller.get_saved_networks()
            # Both should be saved and present in network store since the have
            # different security types and therefore different network identifiers.
            self.check_saved_networks(fd, actual_networks)

    def test_remove_disconnects(self):
        # If we save, connect to, then remove the network while still connected
        # to it, we expect the network will disconnect. This test requires a
        # wpa2 network in the test config. Remove all other networks first so
        # that we can't auto connect to them
        ssid = rand_ascii_str(10)
        security = WPA2
        password = rand_ascii_str(10)
        self.start_ap(ssid, security, password)

        for fd in self.fuchsia_devices:
            fd.wlan_policy_controller.wait_for_no_connections()

            if not fd.wlan_policy_controller.save_and_connect:
                raise signals.TestFailure(
                    "Failed to saved and connect to network")

            if not fd.wlan_policy_controller.remove_all_networks_and_wait_for_no_connections(
            ):
                raise signals.TestFailure(
                    "Failed to disconnect from removed network")

    def test_auto_connect_open(self):
        # Start up AP with an open network with a random SSID
        ssid = rand_ascii_str(10)
        self.start_ap(ssid, None)
        for fd in self.fuchsia_devices:
            fd.wlan_policy_controller.wait_for_no_connections()

            # Save the network and make sure that we see the device auto connect to it.
            security = SECURITY_NONE
            password = CREDENTIAL_VALUE_NONE
            if not fd.wlan_policy_controller.save_network(
                    ssid, security, password=password):
                raise signals.TestFailure("Failed to save network")
            if not fd.wlan_policy_controller.wait_for_connect(
                    ssid, security, timeout=TIME_WAIT_FOR_CONNECT):
                raise signals.TestFailure("Failed to connect to network")

    def test_auto_connect_wpa3(self):
        # Start up AP with an open network with a random SSID
        ssid = rand_ascii_str(10)
        security = WPA3
        password = rand_ascii_str(10)
        self.start_ap(ssid, security, password)
        for fd in self.fuchsia_devices:
            fd.wlan_policy_controller.wait_for_no_connections()

            # Save the network and make sure that we see the device auto connect to it.
            if not fd.wlan_policy_controller.save_network(
                    ssid, security, password=password):
                raise signals.TestFailure("Failed to save network")
            if not fd.wlan_policy_controller.wait_for_connect(
                    ssid, security, timeout=TIME_WAIT_FOR_CONNECT):
                raise signals.TestFailure("Failed to connect to network")
