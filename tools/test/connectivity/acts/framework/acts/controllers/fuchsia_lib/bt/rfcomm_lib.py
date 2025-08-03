#!/usr/bin/env python3
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

from acts.controllers.fuchsia_lib.base_lib import BaseLib


class FuchsiaRfcommLib(BaseLib):

    def __init__(self, addr: str) -> None:
        super().__init__(addr, "rfcomm")

    def init(self):
        """Initializes the RFCOMM service.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "rfcomm_facade.RfcommInit"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def removeService(self):
        """Removes the RFCOMM service from the Fuchsia device

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "rfcomm_facade.RfcommRemoveService"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def disconnectSession(self, peer_id):
        """Closes the RFCOMM Session with the remote peer

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "rfcomm_facade.DisconnectSession"
        test_args = {"peer_id": peer_id}

        return self.send_command(test_cmd, test_args)

    def connectRfcommChannel(self, peer_id, server_channel_number):
        """Makes an outgoing RFCOMM connection to the remote peer

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "rfcomm_facade.ConnectRfcommChannel"
        test_args = {
            "peer_id": peer_id,
            "server_channel_number": server_channel_number
        }

        return self.send_command(test_cmd, test_args)

    def disconnectRfcommChannel(self, peer_id, server_channel_number):
        """Closes the RFCOMM channel with the remote peer

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "rfcomm_facade.DisconnectRfcommChannel"
        test_args = {
            "peer_id": peer_id,
            "server_channel_number": server_channel_number
        }

        return self.send_command(test_cmd, test_args)

    def sendRemoteLineStatus(self, peer_id, server_channel_number):
        """Sends a Remote Line Status update to the remote peer for the provided channel number

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "rfcomm_facade.SendRemoteLineStatus"
        test_args = {
            "peer_id": peer_id,
            "server_channel_number": server_channel_number
        }

        return self.send_command(test_cmd, test_args)

    def writeRfcomm(self, peer_id, server_channel_number, data):
        """Sends data to the remote peer over the RFCOMM channel

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "rfcomm_facade.RfcommWrite"
        test_args = {
            "peer_id": peer_id,
            "server_channel_number": server_channel_number,
            "data": data
        }

        return self.send_command(test_cmd, test_args)
