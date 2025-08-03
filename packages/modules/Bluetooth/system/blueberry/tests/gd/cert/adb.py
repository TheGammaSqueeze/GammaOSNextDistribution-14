#!/usr/bin/env python3
#
#   Copyright 2016 - The Android Open Source Project
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

import encodings
import logging
import shlex
import shutil

from mobly.controllers.android_device_lib.adb import AdbError
from mobly.controllers.android_device_lib.adb import AdbProxy

ROOT_USER_ID = '0'
SHELL_USER_ID = '2000'
UTF_8 = encodings.utf_8.getregentry().name


class BlueberryAdbProxy(AdbProxy):
    """Proxy class for ADB.

    For syntactic reasons, the '-' in adb commands need to be replaced with
    '_'. Can directly execute adb commands on an object:
    >> adb = BlueberryAdbProxy(<serial>)
    >> adb.start_server()
    >> adb.devices() # will return the console output of "adb devices".
    """

    def __init__(self, serial="", ssh_connection=None):
        """Construct an instance of AdbProxy.

        Args:
            serial: str serial number of Android device from `adb devices`
            ssh_connection: SshConnection instance if the Android device is
                            connected to a remote host that we can reach via SSH.
        """
        super().__init__(serial)
        self._server_local_port = None
        adb_path = shutil.which('adb')
        adb_cmd = [shlex.quote(adb_path)]
        if serial:
            adb_cmd.append("-s %s" % serial)
        if ssh_connection is not None:
            # Kill all existing adb processes on the remote host (if any)
            # Note that if there are none, then pkill exits with non-zero status
            ssh_connection.run("pkill adb", ignore_status=True)
            # Copy over the adb binary to a temp dir
            temp_dir = ssh_connection.run("mktemp -d").stdout.strip()
            ssh_connection.send_file(adb_path, temp_dir)
            # Start up a new adb server running as root from the copied binary.
            remote_adb_cmd = "%s/adb %s root" % (temp_dir, "-s %s" % serial if serial else "")
            ssh_connection.run(remote_adb_cmd)
            # Proxy a local port to the adb server port
            local_port = ssh_connection.create_ssh_tunnel(5037)
            self._server_local_port = local_port

        if self._server_local_port:
            adb_cmd.append("-P %d" % local_port)
        self.adb_str = " ".join(adb_cmd)
        self._ssh_connection = ssh_connection

    def get_user_id(self):
        """Returns the adb user. Either 2000 (shell) or 0 (root)."""
        return self.shell('id -u').decode(UTF_8).rstrip()

    def is_root(self, user_id=None):
        """Checks if the user is root.

        Args:
            user_id: if supplied, the id to check against.
        Returns:
            True if the user is root. False otherwise.
        """
        if not user_id:
            user_id = self.get_user_id()
        return user_id == ROOT_USER_ID

    def ensure_root(self):
        """Ensures the user is root after making this call.

        Note that this will still fail if the device is a user build, as root
        is not accessible from a user build.

        Returns:
            False if the device is a user build. True otherwise.
        """
        self.ensure_user(ROOT_USER_ID)
        return self.is_root()

    def ensure_user(self, user_id=SHELL_USER_ID):
        """Ensures the user is set to the given user.

        Args:
            user_id: The id of the user.
        """
        if self.is_root(user_id):
            self.root()
        else:
            self.unroot()
        self.wait_for_device()
        return self.get_user_id() == user_id

    def tcp_forward(self, host_port, device_port):
        """Starts tcp forwarding from localhost to this android device.

        Args:
            host_port: Port number to use on localhost
            device_port: Port number to use on the android device.

        Returns:
            Forwarded port on host as int or command output string on error
        """
        if self._ssh_connection:
            # We have to hop through a remote host first.
            #  1) Find some free port on the remote host's localhost
            #  2) Setup forwarding between that remote port and the requested
            #     device port
            remote_port = self._ssh_connection.find_free_port()
            host_port = self._ssh_connection.create_ssh_tunnel(remote_port, local_port=host_port)
        try:
            output = self.forward(["tcp:%d" % host_port, "tcp:%d" % device_port])
        except AdbError as error:
            return error
        # If hinted_port is 0, the output will be the selected port.
        # Otherwise, there will be no output upon successfully
        # forwarding the hinted port.
        if not output:
            return host_port
        try:
            output_int = int(output)
        except ValueError:
            return output
        return output_int

    def remove_tcp_forward(self, host_port):
        """Stop tcp forwarding a port from localhost to this android device.

        Args:
            host_port: Port number to use on localhost
        """
        if self._ssh_connection:
            remote_port = self._ssh_connection.close_ssh_tunnel(host_port)
            if remote_port is None:
                logging.warning("Cannot close unknown forwarded tcp port: %d", host_port)
                return
            # The actual port we need to disable via adb is on the remote host.
            host_port = remote_port
        self.forward(["--remove", "tcp:%d" % host_port])

    def path_exists(self, path):
        """Check if a file path exists on an Android device

        :param path: file path, could be a directory
        :return: True if file path exists
        """
        try:
            ret = self.shell("ls {}".format(path))
            if ret is not None and len(ret) > 0:
                return True
            else:
                return False
        except AdbError as e:
            logging.debug("path {} does not exist, error={}".format(path, e))
            return False
