# Copyright (C) 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""
Copied from tools/rootcanal/scripts/test_channel.py
"""

import socket
import enum
from time import sleep


class Connection:

    def __init__(self, port):
        self._socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._socket.connect(("localhost", port))

    def close(self):
        self._socket.close()

    def send(self, data):
        self._socket.sendall(data.encode())

    def receive(self, size):
        return self._socket.recv(size)


class TestChannel:

    def __init__(self, port):
        self._connection = Connection(port)
        self._closed = False

    def close(self):
        self._connection.close()
        self._closed = True

    def send_command(self, name, args):
        args = [str(arg) for arg in args]
        name_size = len(name)
        args_size = len(args)
        self.lint_command(name, args, name_size, args_size)
        encoded_name = chr(name_size) + name
        encoded_args = chr(args_size) + "".join(chr(len(arg)) + arg for arg in args)
        command = encoded_name + encoded_args
        if self._closed:
            return
        self._connection.send(command)
        if name != "CLOSE_TEST_CHANNEL":
            return self.receive_response().decode()

    def receive_response(self):
        if self._closed:
            return b"Closed"
        size_chars = self._connection.receive(4)
        if not size_chars:
            return b"No response, assuming that the connection is broken"
        response_size = 0
        for i in range(0, len(size_chars) - 1):
            response_size |= size_chars[i] << (8 * i)
        response = self._connection.receive(response_size)
        return response

    def lint_command(self, name, args, name_size, args_size):
        assert name_size == len(name) and args_size == len(args)
        try:
            name.encode()
            for arg in args:
                arg.encode()
        except UnicodeError:
            print("Unrecognized characters.")
            raise
        if name_size > 255 or args_size > 255:
            raise ValueError  # Size must be encodable in one octet.
        for arg in args:
            if len(arg) > 255:
                raise ValueError  # Size must be encodable in one octet.


class Dongle(enum.Enum):
    DEFAULT = "default"
    LAIRD_BL654 = "laird_bl654"
    CSR_RCK_PTS_DONGLE = "csr_rck_pts_dongle"


class RootCanal:

    def __init__(self, port):
        self.channel = TestChannel(port)
        self.disconnected_dev_phys = None

        # discard initialization messages
        self.channel.receive_response()

    def close(self):
        self.channel.close()

    def select_pts_dongle(self, dongle: Dongle):
        """Use the control port to dynamically reconfigure the controller
        properties for the dongle used by the PTS tester.

        This method will cause a Reset on the controller.
        This method shall exclusively be called from the test_started
        interaction."""
        # The PTS is the device with the highest ID,
        # Android is always first to connect to root-canal.
        (devices, _) = self._read_device_list()
        pts_id = max([id for (id, _) in devices])
        self.channel.send_command("set_device_configuration", [pts_id, dongle.value])

    def move_out_of_range(self):
        """Space out the connected devices to generate a supervision
        timeout for all existing connections."""
        # Disconnect all devices from all phys.
        (devices, phys) = self._read_device_list()
        for (device_id, _) in devices:
            for (phy_id, _, phy_devices) in phys:
                if device_id in phy_devices:
                    self.channel.send_command("del_device_from_phy", [device_id, phy_id])

    def move_in_range(self):
        """Move the connected devices to the same point to ensure
        the reconnection of previous links."""
        # Reconnect all devices to all phys.
        # Beacons are only added back to LE phys.
        (devices, phys) = self._read_device_list()
        for (device_id, device_name) in devices:
            target_phys = ["LOW_ENERGY"]
            if device_name.startswith("hci_device"):
                target_phys.append("BR_EDR")

            for (phy_id, phy_name, phy_devices) in phys:
                if phy_name in target_phys and not device_id in phy_devices:
                    self.channel.send_command("add_device_to_phy", [device_id, phy_id])

    def _read_device_list(self):
        """Query the list of connected devices."""
        response = self.channel.send_command("list", [])

        devices = []
        phys = []
        category = None

        for line in response.split("\n"):
            line = line.strip()
            if not line:
                continue
            if line.startswith("Devices") or line.startswith("Phys"):
                category = line.split(":")[0]
            elif category == "Devices":
                parts = line.split(":")
                device_id = int(parts[0])
                device_name = parts[1]
                devices.append((device_id, device_name))
            elif category == "Phys":
                parts = line.split(":")
                phy_id = int(parts[0])
                phy_name = parts[1]
                phy_devices = [int(id.strip()) for id in parts[2].split(",") if id.strip()]
                phys.append((phy_id, phy_name, phy_devices))

        return (devices, phys)
