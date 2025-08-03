#!/usr/bin/env python3
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
from acts import logger
from acts.controllers.fuchsia_lib.base_lib import BaseLib

COMMAND_SCAN = "wlan.scan"
COMMAND_SCAN_FOR_BSS_INFO = "wlan.scan_for_bss_info"
COMMAND_CONNECT = "wlan.connect"
COMMAND_DISCONNECT = "wlan.disconnect"
COMMAND_STATUS = "wlan.status"
COMMAND_GET_IFACE_ID_LIST = "wlan.get_iface_id_list"
COMMAND_GET_PHY_ID_LIST = "wlan.get_phy_id_list"
COMMAND_DESTROY_IFACE = "wlan.destroy_iface"
COMMAND_GET_COUNTRY = "wlan_phy.get_country"
COMMAND_GET_DEV_PATH = "wlan_phy.get_dev_path"
COMMAND_QUERY_IFACE = "wlan.query_iface"


class FuchsiaWlanLib(BaseLib):

    def __init__(self, addr: str) -> None:
        super().__init__(addr, "wlan")

    def wlanStartScan(self):
        """ Starts a wlan scan

        Returns:
            scan results
        """
        test_cmd = COMMAND_SCAN

        return self.send_command(test_cmd, {})

    def wlanScanForBSSInfo(self):
        """ Scans and returns BSS info

        Returns:
            A dict mapping each seen SSID to a list of BSS Description IE
            blocks, one for each BSS observed in the network
        """
        test_cmd = COMMAND_SCAN_FOR_BSS_INFO

        return self.send_command(test_cmd, {})

    def wlanConnectToNetwork(self,
                             target_ssid,
                             target_bss_desc,
                             target_pwd=None):
        """ Triggers a network connection
        Args:
            target_ssid: the network to attempt a connection to
            target_pwd: (optional) password for the target network

        Returns:
            boolean indicating if the connection was successful
        """
        test_cmd = COMMAND_CONNECT
        test_args = {
            "target_ssid": target_ssid,
            "target_pwd": target_pwd,
            "target_bss_desc": target_bss_desc
        }

        return self.send_command(test_cmd, test_args)

    def wlanDisconnect(self):
        """ Disconnect any current wifi connections"""
        test_cmd = COMMAND_DISCONNECT

        return self.send_command(test_cmd, {})

    def wlanDestroyIface(self, iface_id):
        """ Destroy WLAN interface by ID.
        Args:
            iface_id: the interface id.

        Returns:
            Dictionary, service id if success, error if error.
        """
        test_cmd = COMMAND_DESTROY_IFACE
        test_args = {"identifier": iface_id}

        return self.send_command(test_cmd, test_args)

    def wlanGetIfaceIdList(self):
        """ Get a list if wlan interface IDs.

        Returns:
            Dictionary, service id if success, error if error.
        """
        test_cmd = COMMAND_GET_IFACE_ID_LIST

        return self.send_command(test_cmd, {})

    def wlanPhyIdList(self):
        """ Get a list if wlan phy IDs.

        Returns:
            List of IDs if success, error if error.
        """
        test_cmd = COMMAND_GET_PHY_ID_LIST

        return self.send_command(test_cmd, {})

    def wlanStatus(self, iface_id=None):
        """ Request connection status

        Args:
            iface_id: unsigned 16-bit int, the wlan interface id
                (defaults to None)

        Returns:
            Client state summary containing WlanClientState and
            status of various networks connections
        """
        test_cmd = COMMAND_STATUS
        test_args = {}
        if iface_id:
            test_args = {'iface_id': iface_id}

        return self.send_command(test_cmd, test_args)

    def wlanGetCountry(self, phy_id):
        """ Reads the currently configured country for `phy_id`.

        Args:
            phy_id: unsigned 16-bit integer.

        Returns:
            Dictionary, String if success, error if error.
        """
        test_cmd = COMMAND_GET_COUNTRY
        test_args = {"phy_id": phy_id}

        return self.send_command(test_cmd, test_args)

    def wlanGetDevPath(self, phy_id):
        """ Queries the device path for `phy_id`.

        Args:
            phy_id: unsigned 16-bit integer.

        Returns:
            Dictionary, String if success, error if error.
        """
        test_cmd = COMMAND_GET_DEV_PATH
        test_args = {"phy_id": phy_id}

        return self.send_command(test_cmd, test_args)

    def wlanQueryInterface(self, iface_id):
        """ Retrieves interface info for given wlan iface id.

        Args:
            iface_id: unsigned 16-bit int, the wlan interface id.

        Returns:
            Dictionary, containing interface id, role, phy_id, phy_assigned_id
            and mac addr.
        """
        test_cmd = COMMAND_QUERY_IFACE
        test_args = {'iface_id': iface_id}

        return self.send_command(test_cmd, test_args)
