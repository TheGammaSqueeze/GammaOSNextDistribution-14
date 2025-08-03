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


class FuchsiaHfpLib(BaseLib):

    def __init__(self, addr: str) -> None:
        super().__init__(addr, "hfp")

    def init(self):
        """Initializes the HFP service.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.HfpInit"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def removeService(self):
        """Removes the HFP service from the Fuchsia device

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.HfpRemoveService"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def listPeers(self):
        """List all connected HFP peer devices.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.ListPeers"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def setActivePeer(self, peer_id):
        """Set the active HFP peer device. All peer specific commands will be
        directed to this device.

        Args:
            peer_id: The id of the peer to set as active. Use "listPeers" to
            find connected peer ids.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetActivePeer"
        test_args = {"peer_id": peer_id}

        return self.send_command(test_cmd, test_args)

    def listCalls(self):
        """List all calls known to the sl4f component.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.ListCalls"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def newCall(self, remote, state, direction):
        """Opens a new call channel and alerts the HFP peer.

        Args:
            remote: The number of the remote party.
            state: The state of the call.
            direction: The direction of the call. Can be "incoming" or "outgoing".

        Returns:
            Dictionary, call_id if success, error if error.
        """
        test_cmd = "hfp_facade.NewCall"
        test_args = {"remote": remote, "state": state, "direction": direction}

        return self.send_command(test_cmd, test_args)

    def initiateIncomingCall(self, remote):
        """Opens an incoming call channel and alerts the HFP peer.

        Args:
            remote: The number of the remote party.

        Returns:
            Dictionary, call_id if success, error if error.
        """
        test_cmd = "hfp_facade.IncomingCall"
        test_args = {"remote": remote}

        return self.send_command(test_cmd, test_args)

    def initiateIncomingWaitingCall(self, remote):
        """Opens an incoming call when there is an onging call and alerts
        the HFP peer.

        Args:
            remote: The number of the remote party.

        Returns:
            Dictionary, call_id if success, error if error.
        """
        test_cmd = "hfp_facade.IncomingWaitingCall"
        test_args = {"remote": remote}

        return self.send_command(test_cmd, test_args)

    def initiateOutgoingCall(self, remote):
        """Opens an outgoing call channel and alerts the HFP peer.

        Args:
            remote: The number of the remote party.

        Returns:
            Dictionary, call_id if success, error if error.
        """
        test_cmd = "hfp_facade.OutgoingCall"
        test_args = {"remote": remote}

        return self.send_command(test_cmd, test_args)

    def setCallActive(self, call_id):
        """Sets the specified call to the "OngoingActive" state.

        Args:
            call_id: The unique id of the call.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetCallActive"
        test_args = {"call_id": call_id}

        return self.send_command(test_cmd, test_args)

    def setCallHeld(self, call_id):
        """Sets the specified call to the "OngoingHeld" state.

        Args:
            call_id: The unique id of the call.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetCallHeld"
        test_args = {"call_id": call_id}

        return self.send_command(test_cmd, test_args)

    def setCallTerminated(self, call_id):
        """Sets the specified call to the "Terminated" state.

        Args:
            call_id: The unique id of the call.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetCallTerminated"
        test_args = {"call_id": call_id}

        return self.send_command(test_cmd, test_args)

    def setCallTransferredToAg(self, call_id):
        """Sets the specified call to the "TransferredToAg" state.

        Args:
            call_id: The unique id of the call.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetCallTransferredToAg"
        test_args = {"call_id": call_id}

        return self.send_command(test_cmd, test_args)

    def setSpeakerGain(self, value):
        """Sets the active peer's speaker gain.

        Args:
            value: The gain value to set. Must be between 0-15 inclusive.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetSpeakerGain"
        test_args = {"value": value}

        return self.send_command(test_cmd, test_args)

    def setMicrophoneGain(self, value):
        """Sets the active peer's microphone gain.

        Args:
            value: The gain value to set. Must be between 0-15 inclusive.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetMicrophoneGain"
        test_args = {"value": value}

        return self.send_command(test_cmd, test_args)

    def setServiceAvailable(self, value):
        """Sets the simulated network service status reported by the call manager.

        Args:
            value: True to set the network service to available.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetServiceAvailable"
        test_args = {"value": value}

        return self.send_command(test_cmd, test_args)

    def setRoaming(self, value):
        """Sets the simulated roaming status reported by the call manager.

        Args:
            value: True to set the network connection to roaming.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetRoaming"
        test_args = {"value": value}

        return self.send_command(test_cmd, test_args)

    def setSignalStrength(self, value):
        """Sets the simulated signal strength reported by the call manager.

        Args:
            value: The signal strength value to set. Must be between 0-5 inclusive.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetSignalStrength"
        test_args = {"value": value}

        return self.send_command(test_cmd, test_args)

    def setSubscriberNumber(self, value):
        """Sets the subscriber number reported by the call manager.

        Args:
            value: The subscriber number to set. Maximum length 128 characters.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetSubscriberNumber"
        test_args = {"value": value}

        return self.send_command(test_cmd, test_args)

    def setOperator(self, value):
        """Sets the operator value reported by the call manager.

        Args:
            value: The operator value to set. Maximum length 16 characters.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetOperator"
        test_args = {"value": value}

        return self.send_command(test_cmd, test_args)

    def setNrecSupport(self, value):
        """Sets the noise reduction/echo cancelation support reported by the call manager.

        Args:
            value: The nrec support bool.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetNrecSupport"
        test_args = {"value": value}

        return self.send_command(test_cmd, test_args)

    def setBatteryLevel(self, value):
        """Sets the battery level reported by the call manager.

        Args:
            value: The integer battery level value. Must be 0-5 inclusive.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetBatteryLevel"
        test_args = {"value": value}

        return self.send_command(test_cmd, test_args)

    def setLastDialed(self, number):
        """Sets the last dialed number in the call manager.

        Args:
            number: The number of the remote party.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetLastDialed"
        test_args = {"number": number}

        return self.send_command(test_cmd, test_args)

    def clearLastDialed(self):
        """Clears the last dialed number in the call manager.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.ClearLastDialed"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def setMemoryLocation(self, location, number):
        """Sets a memory location to point to a remote number.

        Args:
            location: The memory location at which to store the number.
            number: The number of the remote party to be stored.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetMemoryLocation"
        test_args = {"location": location, "number": number}

        return self.send_command(test_cmd, test_args)

    def clearMemoryLocation(self, location):
        """Clear a memory location so that it no longer points to a remote
        number.

        Args:
            localtion: The memory location to clear.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.ClearMemoryLocation"
        test_args = {"location": location}

        return self.send_command(test_cmd, test_args)

    def setDialResult(self, number, status):
        """Sets the status result to be returned when the number is dialed.

        Args:
            number: The number of the remote party.
            status: The status to be returned when an outgoing call is
                    initiated to the number.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetDialResult"
        test_args = {"number": number, "status": status}

        return self.send_command(test_cmd, test_args)

    def getState(self):
        """Get the call manager's state.

        Returns:
            Dictionary, State dictionary if success, error if error.
        """
        test_cmd = "hfp_facade.GetState"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def setConnectionBehavior(self, autoconnect):
        """Set the Service Level Connection behavior when a new peer connects.

        Args:
            autoconnect: Enable/Disable autoconnection of SLC.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "hfp_facade.SetConnectionBehavior"
        test_args = {"autoconnect": autoconnect}

        return self.send_command(test_cmd, test_args)
