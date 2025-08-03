#!/usr/bin/env python3
#
# Copyright 2021 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Unit tests for RemoteEmulatorConsole."""

import unittest
import subprocess

from unittest import mock

from acloud import errors
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import emulator_console


class EmulatorConsoleTest(driver_test_lib.BaseDriverTest):
    """Unit tests for RemoteEmulatorConsole."""

    _LOCAL_PORT = 56789
    _TIMEOUT_SECS = 100

    def setUp(self):
        """Create mock objects."""
        super().setUp()
        self._mock_pick_free_port = self.Patch(
            emulator_console.utils, "PickFreePort",
            return_value=self._LOCAL_PORT)
        self._mock_establish_ssh_tunnel = self.Patch(
            emulator_console.utils,
            "EstablishSshTunnel")
        self._mock_release_port = self.Patch(
            emulator_console.utils,
            "ReleasePort")
        self._mock_connection = mock.Mock()
        self._mock_create_connection = self.Patch(
            emulator_console.socket,
            "create_connection",
            return_value=self._mock_connection)

    def _CreateRemoteEmulatorConsole(self):
        """_Create a RemoteEmulatorConsole."""
        console = emulator_console.RemoteEmulatorConsole(
            "192.0.2.1", 5444, "user", "key_path", "extra args",
            self._TIMEOUT_SECS)
        self._mock_pick_free_port.assert_called_once()
        self._mock_establish_ssh_tunnel.assert_called_once_with(
            "192.0.2.1", "key_path", "user",
            [(self._LOCAL_PORT, 5444)], "extra args")
        self._mock_create_connection.assert_called_once_with(
            ("127.0.0.1", self._LOCAL_PORT), self._TIMEOUT_SECS)
        self._mock_connection.settimeout.assert_called_once_with(
            self._TIMEOUT_SECS)
        self._mock_release_port.assert_not_called()
        self._mock_connection.close.assert_not_called()

        self._mock_pick_free_port.reset_mock()
        self._mock_establish_ssh_tunnel.reset_mock()
        self._mock_create_connection.reset_mock()
        self._mock_connection.settimeout.reset_mock()
        return console

    def testInitSshTunnelError(self):
        """Test not releasing port if SSH tunnel fails."""
        self._mock_establish_ssh_tunnel.side_effect = (
            subprocess.CalledProcessError(returncode=1, cmd="ssh"))
        with self.assertRaises(errors.DeviceConnectionError):
            emulator_console.RemoteEmulatorConsole(
                "192.0.2.1", 5444, "user", "key_path", "extra args",
                self._TIMEOUT_SECS)
        self._mock_connection.settimeout.assert_not_called()
        self._mock_connection.close.assert_not_called()
        self._mock_release_port.assert_not_called()

    def testInitConnectionError(self):
        """Test releasing port when create_connection fails."""
        self._mock_create_connection.side_effect = OSError()
        with self.assertRaises(errors.DeviceConnectionError):
            emulator_console.RemoteEmulatorConsole(
                "192.0.2.1", 5444, "user", "key_path", "extra args",
                self._TIMEOUT_SECS)
        self._mock_connection.settimeout.assert_not_called()
        self._mock_connection.close.assert_not_called()
        self._mock_release_port.assert_called_once()

    def testInitSocketError(self):
        """Test closing socket when settimeout fails."""
        self._mock_connection.settimeout.side_effect = OSError()
        with self.assertRaises(errors.DeviceConnectionError):
            emulator_console.RemoteEmulatorConsole(
                "192.0.2.1", 5444, "user", "key_path", "extra args",
                self._TIMEOUT_SECS)
        self._mock_connection.settimeout.assert_called_once()
        self._mock_connection.close.assert_called_once()
        self._mock_release_port.assert_called_once()

    def testContext(self):
        """Test RemoteEmulatorConsole as a context manager."""
        with self._CreateRemoteEmulatorConsole():
            pass
        self._mock_connection.close.assert_called_once()
        self._mock_release_port.assert_called_once()

    def testReconnect(self):
        """Test RemoteEmulatorConsole.Reconnect."""
        console = self._CreateRemoteEmulatorConsole()
        console.Reconnect()
        self._mock_pick_free_port.assert_not_called()
        self._mock_establish_ssh_tunnel.assert_not_called()
        self._mock_release_port.assert_not_called()
        self._mock_connection.close.assert_called_once()
        self._mock_create_connection.assert_called_once_with(
            ("127.0.0.1", self._LOCAL_PORT), self._TIMEOUT_SECS)
        self._mock_connection.settimeout.assert_called_once_with(
            self._TIMEOUT_SECS)

    def testSend(self):
        """Test RemoteEmulatorConsole.Send."""
        console = self._CreateRemoteEmulatorConsole()
        console.Send("ping")
        self._mock_connection.send.assert_called_with(b"ping\n")

        self._mock_connection.send.side_effect = OSError()
        with self.assertRaises(errors.DeviceConnectionError):
            console.Send("ping")

    def testRecv(self):
        """Test RemoteEmulatorConsole.Recv."""
        console = self._CreateRemoteEmulatorConsole()

        self._mock_connection.recv.side_effect = [b"1", b"1"]
        self.assertEqual("11", console.Recv("11", buffer_size=1))
        self._mock_connection.recv.side_effect = [b"12"]
        self.assertEqual("12", console.Recv("2"))

        self._mock_connection.recv.side_effect = [b"1", b""]
        with self.assertRaises(errors.DeviceConnectionError):
            console.Recv("2")
        self._mock_connection.recv.side_effect = OSError()
        with self.assertRaises(errors.DeviceConnectionError):
            console.Recv("1")

    def testPing(self):
        """Test RemoteEmulatorConsole.Ping."""
        console = self._CreateRemoteEmulatorConsole()

        self._mock_connection.recv.side_effect = [b"I am alive!\r\nOK\r\n"]
        self.assertTrue(console.Ping())

        self._mock_connection.recv.side_effect = [b""]
        self.assertFalse(console.Ping())

        self._mock_connection.recv.side_effect = OSError()
        self.assertFalse(console.Ping())

    def testKill(self):
        """Test RemoteEmulatorConsole.Kill."""
        console = self._CreateRemoteEmulatorConsole()
        self._mock_connection.recv.side_effect = [b"bye bye\r\n"]
        console.Kill()


if __name__ == "__main__":
    unittest.main()
