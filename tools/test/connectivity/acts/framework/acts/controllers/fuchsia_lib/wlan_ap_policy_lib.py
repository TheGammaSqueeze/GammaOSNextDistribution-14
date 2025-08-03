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

from acts.controllers.fuchsia_lib.base_lib import BaseLib


class FuchsiaWlanApPolicyLib(BaseLib):

    def __init__(self, addr: str) -> None:
        super().__init__(addr, "wlan_ap_policy")

    def wlanStartAccessPoint(self, target_ssid, security_type, target_pwd,
                             connectivity_mode, operating_band):
        """ Start an Access Point.
                Args:
                    target_ssid: the network to attempt a connection to
                    security_type: the security protocol of the network. Possible inputs:
                    	"none", "wep", "wpa", "wpa2", "wpa3"
                    target_pwd: (optional) credential being saved with the network. No password
                                is equivalent to empty string.
                    connectivity_mode: the connectivity mode to use. Possible inputs:
                    	"local_only", "unrestricted"
                    operating_band: The operating band to use. Possible inputs:
                    	"any", "only_2_4_ghz", "only_5_ghz"

                Returns:
                    boolean indicating if the action was successful
        """

        test_cmd = "wlan_ap_policy.start_access_point"

        test_args = {
            "target_ssid": target_ssid,
            "security_type": security_type.lower(),
            "target_pwd": target_pwd,
            "connectivity_mode": connectivity_mode,
            "operating_band": operating_band,
        }

        return self.send_command(test_cmd, test_args)

    def wlanStopAccessPoint(self, target_ssid, security_type, target_pwd=""):
        """ Stops an active Access Point.
                Args:
                    target_ssid: the network to attempt a connection to
                    security_type: the security protocol of the network
                    target_pwd: (optional) credential being saved with the network. No password
                                is equivalent to empty string.

                Returns:
                    boolean indicating if the action was successful
        """

        test_cmd = "wlan_ap_policy.stop_access_point"

        test_args = {
            "target_ssid": target_ssid,
            "security_type": security_type.lower(),
            "target_pwd": target_pwd
        }

        return self.send_command(test_cmd, test_args)

    def wlanStopAllAccessPoint(self):
        """ Stops all Access Points

                Returns:
                    boolean indicating if the actions were successful
        """

        test_cmd = "wlan_ap_policy.stop_all_access_points"

        test_args = {}

        return self.send_command(test_cmd, test_args)

    def wlanSetNewListener(self):
        """ Sets the update listener stream of the facade to a new stream so that updates will be
            reset. Intended to be used between tests so that the behaviour of updates in a test is
            independent from previous tests.
        """
        test_cmd = "wlan_ap_policy.set_new_update_listener"

        return self.send_command(test_cmd, {})

    def wlanGetUpdate(self, timeout=30):
        """ Gets a list of AP state updates. This call will return with an update immediately the
            first time the update listener is initialized by setting a new listener or by creating
            a client controller before setting a new listener. Subsequent calls will hang until
            there is an update.
            Returns:
                A list of AP state updated. If there is no error, the result is a list with a
                structure that matches the FIDL AccessPointState struct given for updates.
        """
        test_cmd = "wlan_ap_policy.get_update"

        return self.send_command(test_cmd, {}, response_timeout=timeout)
