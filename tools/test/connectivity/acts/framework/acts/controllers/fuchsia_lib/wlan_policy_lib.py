# Lint as: python3
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
#
#   This class provides pipeline betweem python tests and WLAN policy facade.

from acts import logger
from acts.controllers.fuchsia_lib.base_lib import BaseLib

COMMAND_START_CLIENT_CONNECTIONS = "wlan_policy.start_client_connections"
COMMAND_STOP_CLIENT_CONNECTIONS = "wlan_policy.stop_client_connections"
COMMAND_SCAN_FOR_NETWORKS = "wlan_policy.scan_for_networks"
COMMAND_SAVE_NETWORK = "wlan_policy.save_network"
COMMAND_REMOVE_NETWORK = "wlan_policy.remove_network"
COMMAND_REMOVE_ALL_NETWORKS = "wlan_policy.remove_all_networks"
COMMAND_GET_SAVED_NETWORKS = "wlan_policy.get_saved_networks"
COMMAND_CONNECT = "wlan_policy.connect"
COMMAND_CREATE_CLIENT_CONTROLLER = "wlan_policy.create_client_controller"
COMMAND_SET_NEW_LISTENER = "wlan_policy.set_new_update_listener"
COMMAND_REMOVE_ALL_NETWORKS = "wlan_policy.remove_all_networks"
COMMAND_GET_UPDATE = "wlan_policy.get_update"


class FuchsiaWlanPolicyLib(BaseLib):

    def __init__(self, addr: str) -> None:
        super().__init__(addr, "wlan_policy")

    def wlanStartClientConnections(self):
        """ Enables device to initiate connections to networks """

        test_cmd = COMMAND_START_CLIENT_CONNECTIONS

        return self.send_command(test_cmd, {})

    def wlanStopClientConnections(self):
        """ Disables device for initiating connections to networks """

        test_cmd = COMMAND_STOP_CLIENT_CONNECTIONS

        return self.send_command(test_cmd, {})

    def wlanScanForNetworks(self):
        """ Scans for networks that can be connected to
                Returns:
                    A list of network names and security types
         """

        test_cmd = COMMAND_SCAN_FOR_NETWORKS

        return self.send_command(test_cmd, {})

    def wlanSaveNetwork(self, target_ssid, security_type, target_pwd=None):
        """ Saveds a network to the device for future connections
                Args:
                    target_ssid: the network to attempt a connection to
                    security_type: the security protocol of the network
                    target_pwd: (optional) credential being saved with the network. No password
                                is equivalent to empty string.

                Returns:
                    boolean indicating if the connection was successful
        """
        if not target_pwd:
            target_pwd = ''
        test_cmd = COMMAND_SAVE_NETWORK
        test_args = {
            "target_ssid": target_ssid,
            "security_type": str(security_type).lower(),
            "target_pwd": target_pwd
        }

        return self.send_command(test_cmd, test_args)

    def wlanRemoveNetwork(self, target_ssid, security_type, target_pwd=None):
        """ Removes or "forgets" a network from saved networks
                Args:
                    target_ssid: the network to attempt a connection to
                    security_type: the security protocol of the network
                    target_pwd: (optional) credential of the network to remove. No password and
                                empty string are equivalent.
        """
        if not target_pwd:
            target_pwd = ''
        test_cmd = COMMAND_REMOVE_NETWORK
        test_args = {
            "target_ssid": target_ssid,
            "security_type": str(security_type).lower(),
            "target_pwd": target_pwd
        }

        return self.send_command(test_cmd, test_args)

    def wlanRemoveAllNetworks(self):
        """ Removes or "forgets" all networks from saved networks
                Returns:
                    A boolean indicating if the action was successful
        """

        test_cmd = COMMAND_REMOVE_ALL_NETWORKS

        return self.send_command(test_cmd, {})

    def wlanGetSavedNetworks(self):
        """ Gets networks saved on device. Any PSK of a saved network will be
            lower case regardless of how it was saved.
                Returns:
                    A list of saved network names and security protocols
        """

        test_cmd = COMMAND_GET_SAVED_NETWORKS

        return self.send_command(test_cmd, {})

    def wlanConnect(self, target_ssid, security_type):
        """ Triggers connection to a network
                Args:
                    target_ssid: the network to attempt a connection to. Must have been previously
                                 saved in order for a successful connection to happen.
                    security_type: the security protocol of the network

            Returns:
                    boolean indicating if the connection was successful
        """

        test_cmd = COMMAND_CONNECT
        test_args = {
            "target_ssid": target_ssid,
            "security_type": str(security_type).lower()
        }

        return self.send_command(test_cmd, test_args)

    def wlanCreateClientController(self):
        """ Initializes the client controller of the facade that is used to make Client Controller
            API calls
        """
        test_cmd = COMMAND_CREATE_CLIENT_CONTROLLER

        return self.send_command(test_cmd, {})

    def wlanSetNewListener(self):
        """ Sets the update listener stream of the facade to a new stream so that updates will be
            reset. Intended to be used between tests so that the behaviour of updates in a test is
            independent from previous tests.
        """
        test_cmd = COMMAND_SET_NEW_LISTENER

        return self.send_command(test_cmd, {})

    def wlanRemoveAllNetworks(self):
        """ Deletes all saved networks on the device. Relies directly on the get_saved_networks and
            remove_network commands
        """
        test_cmd = COMMAND_REMOVE_ALL_NETWORKS

        return self.send_command(test_cmd, {})

    def wlanGetUpdate(self, timeout=30):
        """ Gets one client listener update. This call will return with an update immediately the
            first time the update listener is initialized by setting a new listener or by creating
            a client controller before setting a new listener. Subsequent calls will hang until
            there is an update.
            Returns:
                An update of connection status. If there is no error, the result is a dict with a
                structure that matches the FIDL ClientStateSummary struct given for updates.
        """
        test_cmd = COMMAND_GET_UPDATE

        return self.send_command(test_cmd, {}, response_timeout=timeout)
