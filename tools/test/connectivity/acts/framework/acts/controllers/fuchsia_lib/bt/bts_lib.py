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

from acts.controllers.fuchsia_lib.base_lib import BaseLib


class FuchsiaBtsLib(BaseLib):
    # Class representing the Bluetooth Access Library.

    def __init__(self, addr: str) -> None:
        super().__init__(addr, "bt_sys")

    def setDiscoverable(self, discoverable):
        """Sets the device to be discoverable over BR/EDR.

        Args:
            discoverable: A bool object for setting Bluetooth
              device discoverable or not.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "bt_sys_facade.BluetoothSetDiscoverable"
        test_args = {"discoverable": discoverable}

        return self.send_command(test_cmd, test_args)

    def setName(self, name):
        """Sets the local Bluetooth name of the device.

        Args:
            name: A string that represents the name to set.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "bt_sys_facade.BluetoothSetName"
        test_args = {"name": name}

        return self.send_command(test_cmd, test_args)

    def inputPairingPin(self, pin):
        """Inputs the pairing pin to the Fuchsia devices' pairing delegate.

        Args:
            pin: A string that represents the pin to input.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "bt_sys_facade.BluetoothInputPairingPin"
        test_args = {"pin": pin}

        return self.send_command(test_cmd, test_args)

    def getPairingPin(self):
        """Gets the pairing pin from the Fuchsia devices' pairing delegate.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "bt_sys_facade.BluetoothGetPairingPin"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def initBluetoothSys(self):
        """Initialises the Bluetooth sys Interface proxy in SL4F.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "bt_sys_facade.BluetoothInitSys"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def requestDiscovery(self, discovery):
        """Start or stop Bluetooth Control device discovery.

        Args:
            discovery: A bool object representing starting or stopping
              device discovery.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "bt_sys_facade.BluetoothRequestDiscovery"
        test_args = {"discovery": discovery}

        return self.send_command(test_cmd, test_args)

    def getKnownRemoteDevices(self):
        """Get known remote BR/EDR and LE devices.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "bt_sys_facade.BluetoothGetKnownRemoteDevices"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def forgetDevice(self, identifier):
        """Forgets a devices pairing.

        Args:
            identifier: A string representing the device id.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "bt_sys_facade.BluetoothForgetDevice"
        test_args = {"identifier": identifier}

        return self.send_command(test_cmd, test_args)

    def disconnectDevice(self, identifier):
        """Disconnects a devices.

        Args:
            identifier: A string representing the device id.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "bt_sys_facade.BluetoothDisconnectDevice"
        test_args = {"identifier": identifier}

        return self.send_command(test_cmd, test_args)

    def connectDevice(self, identifier):
        """Connects to a devices.

        Args:
            identifier: A string representing the device id.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "bt_sys_facade.BluetoothConnectDevice"
        test_args = {"identifier": identifier}

        return self.send_command(test_cmd, test_args)

    def getActiveAdapterAddress(self):
        """Gets the current Active Adapter's address.

        Returns:
            Dictionary, String address if success, error if error.
        """
        test_cmd = "bt_sys_facade.BluetoothGetActiveAdapterAddress"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def pair(self, identifier, pairing_security_level, non_bondable,
             transport):
        """Pairs to a device.

        Args:
            identifier: A string representing the device id.
            pairing_security_level: The security level required for this pairing request
                represented as a u64. (Only for LE pairing)
                Available Values
                1 - ENCRYPTED: Encrypted without MITM protection (unauthenticated)
                2 - AUTHENTICATED: Encrypted with MITM protection (authenticated).
                None: No pairing security level.
            non_bondable: A bool representing whether the pairing mode is bondable or not. None is
                also accepted. False if bondable, True if non-bondable.
            transport: A u64 representing the transport type.
                Available Values
                1 - BREDR: Classic BR/EDR transport
                2 - LE: Bluetooth Low Energy Transport

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "bt_sys_facade.BluetoothPairDevice"
        test_args = {
            "identifier": identifier,
            "pairing_security_level": pairing_security_level,
            "non_bondable": non_bondable,
            "transport": transport,
        }

        return self.send_command(test_cmd, test_args)

    def acceptPairing(self,
                      input_capabilities="NONE",
                      output_capabilities="NONE"):
        """Accepts incoming pairing requests.

        Args:
            input: String - The input I/O capabilities to use
                Available Values:
                NONE - Input capability type None
                CONFIRMATION - Input capability type confirmation
                KEYBOARD - Input capability type Keyboard
            output: String - The output I/O Capabilities to use
                Available Values:
                NONE - Output capability type None
                DISPLAY - output capability type Display

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "bt_sys_facade.BluetoothAcceptPairing"
        test_args = {
            "input": input_capabilities,
            "output": output_capabilities,
        }

        return self.send_command(test_cmd, test_args)
