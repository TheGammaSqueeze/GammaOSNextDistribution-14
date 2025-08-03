#!/usr/bin/env python3
#
#   Copyright 2022 - The Android Open Source Project
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

import json
import os
import tempfile
import subprocess
import time

from pathlib import Path
from typing import Any, MutableMapping, Optional

from acts import context
from acts import logger
from acts import signals
from acts import utils


FFX_DEFAULT_COMMAND_TIMEOUT: int = 60


class FFXError(signals.TestError):
    """Non-zero error code returned from a ffx command."""

    def __init__(self, command: str,
                 process: subprocess.CalledProcessError) -> None:
        self.command = command
        self.stdout: str = process.stdout.decode('utf-8', errors='replace')
        self.stderr: str = process.stderr.decode('utf-8', errors='replace')
        self.exit_status = process.returncode

    def __str__(self) -> str:
        return f'ffx subcommand "{self.command}" returned {self.exit_status}, stdout: "{self.stdout}", stderr: "{self.stderr}"'


class FFXTimeout(signals.TestError):
    """Timed out running a ffx command."""


class FFX:
    """Device-specific controller for the ffx tool.

    Attributes:
        log: Logger for the device-specific instance of ffx.
        binary_path: Path to the ffx binary.
        mdns_name: mDNS nodename of the default Fuchsia target.
        ip: IP address of the default Fuchsia target.
        ssh_private_key_path: Path to Fuchsia DUT SSH private key.
    """

    def __init__(self,
                 binary_path: str,
                 mdns_name: str,
                 ip: str = None,
                 ssh_private_key_path: str = None):
        """
        Args:
            binary_path: Path to ffx binary.
            target: Fuchsia mDNS nodename of default target.
            ssh_private_key_path: Path to SSH private key for talking to the
                Fuchsia DUT.
        """
        self.log = logger.create_tagged_trace_logger(f"ffx | {mdns_name}")
        self.binary_path = binary_path
        self.mdns_name = mdns_name
        self.ip = ip
        self.ssh_private_key_path = ssh_private_key_path

        self._env_config_path: Optional[str] = None
        self._ssh_auth_sock_path: Optional[str] = None
        self._overnet_socket_path: Optional[str] = None
        self._has_been_reachable = False
        self._has_logged_version = False

    def clean_up(self) -> None:
        if self._env_config_path:
            self.run("daemon stop", skip_reachability_check=True)
        if self._ssh_auth_sock_path:
            Path(self._ssh_auth_sock_path).unlink(missing_ok=True)
        if self._overnet_socket_path:
            Path(self._overnet_socket_path).unlink(missing_ok=True)

        self._env_config_path = None
        self._ssh_auth_sock_path = None
        self._overnet_socket_path = None
        self._has_been_reachable = False
        self._has_logged_version = False

    def run(self,
            command: str,
            timeout_sec: int = FFX_DEFAULT_COMMAND_TIMEOUT,
            skip_status_code_check: bool = False,
            skip_reachability_check: bool = False
            ) -> subprocess.CompletedProcess:
        """Runs an ffx command.

        Verifies reachability before running, if it hasn't already.

        Args:
            command: Command to run with ffx.
            timeout_sec: Seconds to wait for a command to complete.
            skip_status_code_check: Whether to check for the status code.
            verify_reachable: Whether to verify reachability before running.

        Raises:
            FFXTimeout: when the command times out.
            FFXError: when the command returns non-zero and skip_status_code_check is False.

        Returns:
            The results of the command. Note subprocess.CompletedProcess returns
            stdout and stderr as a byte-array, not a string. Treat these members
            as such or convert to a string using bytes.decode('utf-8').
        """
        if not self._env_config_path:
            self._create_isolated_environment()
        if not self._has_been_reachable and not skip_reachability_check:
            self.log.info(f'Verifying reachability before running "{command}"')
            self.verify_reachable()

        self.log.debug(f'Running "{command}".')
        full_command = f'{self.binary_path} -e {self._env_config_path} {command}'

        try:
            result = subprocess.run(full_command.split(),
                                    capture_output=True,
                                    timeout=timeout_sec,
                                    check=not skip_status_code_check)
        except subprocess.CalledProcessError as e:
            raise FFXError(command, e) from e
        except subprocess.TimeoutExpired as e:
            raise FFXTimeout(f'Timed out running "{full_command}"') from e

        return result

    def _create_isolated_environment(self) -> None:
        """ Create a new isolated environment for ffx.

        This is needed to avoid overlapping ffx daemons while testing in
        parallel, causing the ffx invocations to “upgrade” one daemon to
        another, which appears as a flap/restart to another test.
        """
        # Store ffx files in a unique directory. Timestamp is used to prevent
        # files from being overwritten in the case when a test intentionally
        # reboots or resets the device such that a new isolated ffx environment
        # is created.
        root_dir = context.get_current_context().get_full_output_path()
        epoch = utils.get_current_epoch_time()
        time_stamp = logger.normalize_log_line_timestamp(
            logger.epoch_to_log_line_timestamp(epoch))
        target_dir = os.path.join(root_dir, f'{self.mdns_name}_{time_stamp}')
        os.makedirs(target_dir, exist_ok=True)

        # Sockets need to be created in a different directory to be guaranteed
        # to stay under the maximum socket path length of 104 characters.
        # See https://unix.stackexchange.com/q/367008
        self._ssh_auth_sock_path = tempfile.mkstemp(suffix="ssh_auth_sock")[1]
        self._overnet_socket_path = tempfile.mkstemp(
            suffix="overnet_socket")[1]

        config: MutableMapping[str, Any] = {
            "target": {
                "default": self.mdns_name,
            },
            # Use user-specific and device-specific locations for sockets.
            # Avoids user permission errors in a multi-user test environment.
            # Avoids daemon upgrades when running tests in parallel in a CI
            # environment.
            "ssh": {
                "auth-sock": self._ssh_auth_sock_path,
            },
            "overnet": {
                "socket": self._overnet_socket_path,
            },
            # Configure the ffx daemon to log to a place where we can read it.
            # Note, ffx client will still output to stdout, not this log
            # directory.
            "log": {
                "enabled": True,
                "dir": [target_dir],
            },
            # Disable analytics to decrease noise on the network.
            "ffx": {
                "analytics": {
                    "disabled": True,
                },
            },
        }

        if self.ip:
            config["discovery"] = {
                "mdns": {
                    "enabled": False,
                },
            }

        # ffx looks for the private key in several default locations. For
        # testbeds which have the private key in another location, set it now.
        if self.ssh_private_key_path:
            config["ssh"]["priv"] = self.ssh_private_key_path

        config_path = os.path.join(target_dir, "ffx_config.json")
        with open(config_path, 'w', encoding="utf-8") as f:
            json.dump(config, f, ensure_ascii=False, indent=4)

        env = {
            "user": config_path,
            "build": None,
            "global": None,
        }
        self._env_config_path = os.path.join(target_dir, "ffx_env.json")
        with open(self._env_config_path, 'w', encoding="utf-8") as f:
            json.dump(env, f, ensure_ascii=False, indent=4)

        # The ffx daemon will started automatically when needed. There is no
        # need to start it manually here.

    def verify_reachable(self,
                         timeout_sec: int = FFX_DEFAULT_COMMAND_TIMEOUT
                         ) -> None:
        """Verify the target is reachable via RCS and various services.

        Blocks until the device allows for an RCS connection. If the device
        isn't reachable within a short time, logs a warning before waiting
        longer.

        Verifies the RCS connection by fetching information from the device,
        which exercises several debug and informational FIDL services.

        When called for the first time, the versions will be checked for
        compatibility.

        Args:
            timeout_sec: Seconds to wait for reachability check

        Raises:
            FFXError: when an unknown error occurs
            FFXTimeout: when the target is unreachable
        """
        cmd = "target wait"
        if self.ip:
            # `target add` does what `target wait` does but adds an entry
            # to ensure connections can happen without mDNS.
            # TODO(https://fxbug.dev/105530): Update manual target parsing in
            # ffx.
            cmd = f"target add {self.ip}"

        timeout = time.perf_counter() + timeout_sec
        while True:
            try:
                self.run(cmd, timeout_sec=5, skip_reachability_check=True)
                break
            except FFXError as e:
                if 'took too long connecting to ascendd socket' in e.stderr:
                    err = e
                else:
                    raise e
            except FFXTimeout as e:
                err = e

            if time.perf_counter() > timeout:
                raise FFXTimeout(
                    f'Waited over {timeout_sec}s for ffx to become reachable'
                ) from err

        # Use a shorter timeout than default because device information
        # gathering can hang for a long time if the device is not actually
        # connectable.
        try:
            result = self.run("target show --json",
                              timeout_sec=15,
                              skip_reachability_check=True)
        except Exception as e:
            self.log.error(
                f'Failed to reach target device. Try running "{self.binary_path}'
                + ' doctor" to diagnose issues.')
            raise e

        self._has_been_reachable = True

        if not self._has_logged_version:
            self._has_logged_version = True
            self.compare_version(result)

    def compare_version(
            self, target_show_result: subprocess.CompletedProcess) -> None:
        """Compares the version of Fuchsia with the version of ffx.

        Args:
            target_show_result: Result of the target show command with JSON
                output mode enabled
        """
        result_json = json.loads(target_show_result.stdout)
        build_info = next(
            filter(lambda s: s.get('label') == 'build', result_json))
        version_info = next(
            filter(lambda s: s.get('label') == 'version', build_info['child']))
        device_version = version_info.get('value')
        ffx_version = self.run("version").stdout.decode('utf-8')

        self.log.info(
            f"Device version: {device_version}, ffx version: {ffx_version}")
        if device_version != ffx_version:
            self.log.warning(
                "ffx versions that differ from device versions may" +
                " have compatibility issues. It is recommended to" +
                " use versions within 6 weeks of each other.")
