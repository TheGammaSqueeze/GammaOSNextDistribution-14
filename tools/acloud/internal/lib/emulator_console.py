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

"""This module encapsulates emulator (goldfish) console.

Reference: https://developer.android.com/studio/run/emulator-console
"""

import logging
import socket
import subprocess

from acloud import errors
from acloud.internal.lib import utils

logger = logging.getLogger(__name__)
_DEFAULT_SOCKET_TIMEOUT_SECS = 20
_LOCALHOST_IP_ADDRESS = "127.0.0.1"


class RemoteEmulatorConsole:
    """Connection to a remote emulator console through SSH tunnel.

    Attributes:
        local_port: The local port of the SSH tunnel.
        socket: The TCP connection to the console.
        timeout_secs: The timeout for the TCP connection.
    """

    def __init__(self, ip_addr, port, ssh_user, ssh_private_key_path,
                 ssh_extra_args, timeout_secs=_DEFAULT_SOCKET_TIMEOUT_SECS):
        """Create a SSH tunnel and a TCP connection to an emulator console.

        Args:
            ip_addr: A string, the IP address of the emulator console.
            port: An integer, the port of the emulator console.
            ssh_user: A string, the user name for SSH.
            ssh_private_key_path: A string, the private key path for SSH.
            ssh_extra_args: A string, the extra arguments for SSH.
            timeout_secs: An integer, the timeout for the TCP connection.

        Raises:
            errors.DeviceConnectionError if the connection fails.
        """
        logger.debug("Connect to %s:%d", ip_addr, port)
        self._local_port = None
        self._socket = None
        self._timeout_secs = timeout_secs
        try:
            self._local_port = utils.PickFreePort()
            utils.EstablishSshTunnel(
                ip_addr,
                ssh_private_key_path,
                ssh_user,
                [(self._local_port, port)],
                ssh_extra_args)
        except (OSError, subprocess.CalledProcessError) as e:
            raise errors.DeviceConnectionError(
                "Cannot create SSH tunnel to %s:%d." % (ip_addr, port)) from e

        try:
            self._socket = socket.create_connection(
                (_LOCALHOST_IP_ADDRESS, self._local_port), timeout_secs)
            self._socket.settimeout(timeout_secs)
        except OSError as e:
            if self._socket:
                self._socket.close()
            utils.ReleasePort(self._local_port)
            raise errors.DeviceConnectionError(
                "Cannot connect to %s:%d." % (ip_addr, port)) from e

    def __enter__(self):
        return self

    def __exit__(self, exc_type, msg, trackback):
        self._socket.close()
        utils.ReleasePort(self._local_port)

    def Reconnect(self):
        """Retain the SSH tunnel and reconnect the console socket.

        Raises:
            errors.DeviceConnectionError if the connection fails.
        """
        logger.debug("Reconnect to %s:%d",
                     _LOCALHOST_IP_ADDRESS, self._local_port)
        try:
            self._socket.close()
            self._socket = socket.create_connection(
                (_LOCALHOST_IP_ADDRESS, self._local_port), self._timeout_secs)
            self._socket.settimeout(self._timeout_secs)
        except OSError as e:
            raise errors.DeviceConnectionError(
                "Fail to reconnect to %s:%d" %
                (_LOCALHOST_IP_ADDRESS, self._local_port)) from e

    def Send(self, command):
        """Send a command to the console.

        Args:
            command: A string, the command without newline character.

        Raises:
            errors.DeviceConnectionError if the socket fails.
        """
        logger.debug("Emu command: %s", command)
        try:
            self._socket.send(command.encode() + b"\n")
        except OSError as e:
            raise errors.DeviceConnectionError(
                "Fail to send to %s:%d." %
                (_LOCALHOST_IP_ADDRESS, self._local_port)) from e

    def Recv(self, expected_substring, buffer_size=128):
        """Receive from the console until getting the expected substring.

        Args:
            expected_substring: The expected substring in the received data.
            buffer_size: The buffer size in bytes for each recv call.

        Returns:
            The received data as a string.

        Raises:
            errors.DeviceConnectionError if the received data does not contain
            the expected substring.
        """
        expected_data = expected_substring.encode()
        data = bytearray()
        while True:
            try:
                new_data = self._socket.recv(buffer_size)
            except OSError as e:
                raise errors.DeviceConnectionError(
                    "Fail to receive from %s:%d." %
                    (_LOCALHOST_IP_ADDRESS, self._local_port)) from e
            if not new_data:
                raise errors.DeviceConnectionError(
                    "Connection to %s:%d is closed." %
                    (_LOCALHOST_IP_ADDRESS, self._local_port))

            logger.debug("Emu output: %s", new_data)
            data.extend(new_data)
            if expected_data in data:
                break
        return data.decode()

    def Ping(self):
        """Send ping command.

        Returns:
            Whether the console is active.
        """
        try:
            self.Send("ping")
            self.Recv("I am alive!")
        except errors.DeviceConnectionError as e:
            logger.debug("Fail to ping console: %s", str(e))
            return False
        return True

    def Kill(self):
        """Send kill command.

        Raises:
            errors.DeviceConnectionError if the console is not killed.
        """
        self.Send("kill")
        self.Recv("bye bye")
