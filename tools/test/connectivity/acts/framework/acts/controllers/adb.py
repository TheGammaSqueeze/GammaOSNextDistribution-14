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

from builtins import str

import logging
import re
import shlex
import shutil

from acts.controllers.adb_lib.error import AdbCommandError
from acts.controllers.adb_lib.error import AdbError
from acts.libs.proc import job
from acts.metrics.loggers import usage_metadata_logger

DEFAULT_ADB_TIMEOUT = 60
DEFAULT_ADB_PULL_TIMEOUT = 180

ADB_REGEX = re.compile('adb:')
# Uses a regex to be backwards compatible with previous versions of ADB
# (N and above add the serial to the error msg).
DEVICE_NOT_FOUND_REGEX = re.compile('error: device (?:\'.*?\' )?not found')
DEVICE_OFFLINE_REGEX = re.compile('error: device offline')
# Raised when adb forward commands fail to forward a port.
CANNOT_BIND_LISTENER_REGEX = re.compile('error: cannot bind listener:')
# Expected output is "Android Debug Bridge version 1.0.XX
ADB_VERSION_REGEX = re.compile('Android Debug Bridge version 1.0.(\d+)')
GREP_REGEX = re.compile('grep(\s+)')

ROOT_USER_ID = '0'
SHELL_USER_ID = '2000'


def parsing_parcel_output(output):
    """Parsing the adb output in Parcel format.

    Parsing the adb output in format:
      Result: Parcel(
        0x00000000: 00000000 00000014 00390038 00340031 '........8.9.1.4.'
        0x00000010: 00300038 00300030 00300030 00340032 '8.0.0.0.0.0.2.4.'
        0x00000020: 00350034 00330035 00320038 00310033 '4.5.5.3.8.2.3.1.'
        0x00000030: 00000000                            '....            ')
    """
    output = ''.join(re.findall(r"'(.*)'", output))
    return re.sub(r'[.\s]', '', output)


class AdbProxy(object):
    """Proxy class for ADB.

    For syntactic reasons, the '-' in adb commands need to be replaced with
    '_'. Can directly execute adb commands on an object:
    >> adb = AdbProxy(<serial>)
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
        self.serial = serial
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
            remote_adb_cmd = "%s/adb %s root" % (temp_dir, "-s %s" % serial
                                                 if serial else "")
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
        return self.shell('id -u')

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

    def _exec_cmd(self, cmd, ignore_status=False, timeout=DEFAULT_ADB_TIMEOUT):
        """Executes adb commands in a new shell.

        This is specific to executing adb commands.

        Args:
            cmd: A string or list that is the adb command to execute.

        Returns:
            The stdout of the adb command.

        Raises:
            AdbError for errors in ADB operations.
            AdbCommandError for errors from commands executed through ADB.
        """
        if isinstance(cmd, list):
            cmd = ' '.join(cmd)
        result = job.run(cmd, ignore_status=True, timeout=timeout)
        ret, out, err = result.exit_status, result.stdout, result.stderr

        if any(pattern.match(err) for pattern in
               [ADB_REGEX, DEVICE_OFFLINE_REGEX, DEVICE_NOT_FOUND_REGEX,
                CANNOT_BIND_LISTENER_REGEX]):
            raise AdbError(cmd=cmd, stdout=out, stderr=err, ret_code=ret)
        if "Result: Parcel" in out:
            return parsing_parcel_output(out)
        if ignore_status or (ret == 1 and GREP_REGEX.search(cmd)):
            return out or err
        if ret != 0:
            raise AdbCommandError(cmd=cmd, stdout=out, stderr=err, ret_code=ret)
        return out

    def _exec_adb_cmd(self, name, arg_str, **kwargs):
        return self._exec_cmd(' '.join((self.adb_str, name, arg_str)),
                              **kwargs)

    def _exec_cmd_nb(self, cmd, **kwargs):
        """Executes adb commands in a new shell, non blocking.

        Args:
            cmds: A string that is the adb command to execute.

        """
        return job.run_async(cmd, **kwargs)

    def _exec_adb_cmd_nb(self, name, arg_str, **kwargs):
        return self._exec_cmd_nb(' '.join((self.adb_str, name, arg_str)),
                                 **kwargs)

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
            host_port = self._ssh_connection.create_ssh_tunnel(
                remote_port, local_port=host_port)
        output = self.forward("tcp:%d tcp:%d" % (host_port, device_port),
                              ignore_status=True)
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
                logging.warning("Cannot close unknown forwarded tcp port: %d",
                                host_port)
                return
            # The actual port we need to disable via adb is on the remote host.
            host_port = remote_port
        self.forward("--remove tcp:%d" % host_port)

    def getprop(self, prop_name):
        """Get a property of the device.

        This is a convenience wrapper for "adb shell getprop xxx".

        Args:
            prop_name: A string that is the name of the property to get.

        Returns:
            A string that is the value of the property, or None if the property
            doesn't exist.
        """
        return self.shell("getprop %s" % prop_name)

    # TODO: This should be abstracted out into an object like the other shell
    # command.
    def shell(self, command, ignore_status=False, timeout=DEFAULT_ADB_TIMEOUT):
        return self._exec_adb_cmd(
            'shell',
            shlex.quote(command),
            ignore_status=ignore_status,
            timeout=timeout)

    def shell_nb(self, command):
        return self._exec_adb_cmd_nb('shell', shlex.quote(command))

    def __getattr__(self, name):
        def adb_call(*args, **kwargs):
            usage_metadata_logger.log_usage(self.__module__, name)
            clean_name = name.replace('_', '-')
            if clean_name in ['pull', 'push', 'remount'] and 'timeout' not in kwargs:
                kwargs['timeout'] = DEFAULT_ADB_PULL_TIMEOUT
            arg_str = ' '.join(str(elem) for elem in args)
            return self._exec_adb_cmd(clean_name, arg_str, **kwargs)

        return adb_call

    def get_version_number(self):
        """Returns the version number of ADB as an int (XX in 1.0.XX).

        Raises:
            AdbError if the version number is not found/parsable.
        """
        version_output = self.version()
        match = re.search(ADB_VERSION_REGEX, version_output)

        if not match:
            logging.error('Unable to capture ADB version from adb version '
                          'output: %s' % version_output)
            raise AdbError('adb version', version_output, '', '')
        return int(match.group(1))
