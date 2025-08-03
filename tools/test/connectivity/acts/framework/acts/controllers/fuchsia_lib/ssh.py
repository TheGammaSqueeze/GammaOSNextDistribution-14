#!/usr/bin/env python3
#
#   Copyright 2022 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0SSHResults
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import subprocess
import time

from dataclasses import dataclass
from typing import List, Union

from acts import logger
from acts import signals

DEFAULT_SSH_USER: str = "fuchsia"
DEFAULT_SSH_PORT: int = 22
DEFAULT_SSH_TIMEOUT_SEC: int = 60
DEFAULT_SSH_CONNECT_TIMEOUT_SEC: int = 30
DEFAULT_SSH_SERVER_ALIVE_INTERVAL: int = 30
# The default package repository for all components.
FUCHSIA_PACKAGE_REPO_NAME = 'fuchsia.com'


class SSHResult:
    """Result of an SSH command."""

    def __init__(
        self, process: Union[subprocess.CompletedProcess,
                             subprocess.CalledProcessError]
    ) -> None:
        self._raw_stdout = process.stdout
        self._stdout = process.stdout.decode('utf-8', errors='replace')
        self._stderr = process.stderr.decode('utf-8', errors='replace')
        self._exit_status: int = process.returncode

    def __str__(self):
        if self.exit_status == 0:
            return self.stdout
        return f'status {self.exit_status}, stdout: "{self.stdout}", stderr: "{self.stderr}"'

    @property
    def stdout(self) -> str:
        return self._stdout

    @property
    def stderr(self) -> str:
        return self._stderr

    @property
    def exit_status(self) -> int:
        return self._exit_status

    @property
    def raw_stdout(self) -> bytes:
        return self._raw_stdout


class FuchsiaSSHError(signals.TestError):
    """A SSH command returned with a non-zero status code."""

    def __init__(self, command: str, result: SSHResult):
        super().__init__(
            f'SSH command "{command}" unexpectedly returned {result}')
        self.result = result


class SSHTimeout(signals.TestError):
    """A SSH command timed out."""

    def __init__(self, err: subprocess.TimeoutExpired):
        super().__init__(
            f'SSH command "{err.cmd}" timed out after {err.timeout}s, '
            f'stdout="{err.stdout}", stderr="{err.stderr}"')


class FuchsiaSSHTransportError(signals.TestError):
    """Failure to send an SSH command."""


@dataclass
class SSHConfig:
    """SSH client config."""

    # SSH flags. See ssh(1) for full details.
    host_name: str
    identity_file: str

    ssh_binary: str = 'ssh'
    config_file: str = '/dev/null'
    port: int = 22
    user: str = DEFAULT_SSH_USER

    # SSH options. See ssh_config(5) for full details.
    connect_timeout: int = DEFAULT_SSH_CONNECT_TIMEOUT_SEC
    server_alive_interval: int = DEFAULT_SSH_SERVER_ALIVE_INTERVAL
    strict_host_key_checking: bool = False
    user_known_hosts_file: str = "/dev/null"
    log_level: str = "ERROR"

    def full_command(self, command: str, force_tty: bool = False) -> List[str]:
        """Generate the complete command to execute command over SSH.

        Args:
            command: The command to run over SSH
            force_tty: Force pseudo-terminal allocation. This can be used to
                execute arbitrary screen-based programs on a remote machine,
                which can be very useful, e.g. when implementing menu services.

        Returns:
            Arguments composing the complete call to SSH.
        """
        optional_flags = []
        if force_tty:
            # Multiple -t options force tty allocation, even if ssh has no local
            # tty. This is necessary for launching ssh with subprocess without
            # shell=True.
            optional_flags.append('-tt')

        return [
            self.ssh_binary,
            # SSH flags
            '-i',
            self.identity_file,
            '-F',
            self.config_file,
            '-p',
            str(self.port),
            # SSH configuration options
            '-o',
            f'ConnectTimeout={self.connect_timeout}',
            '-o',
            f'ServerAliveInterval={self.server_alive_interval}',
            '-o',
            f'StrictHostKeyChecking={"yes" if self.strict_host_key_checking else "no"}',
            '-o',
            f'UserKnownHostsFile={self.user_known_hosts_file}',
            '-o',
            f'LogLevel={self.log_level}',
        ] + optional_flags + [
            f'{self.user}@{self.host_name}'
        ] + command.split()


class SSHProvider:
    """Device-specific provider for SSH clients."""

    def __init__(self, config: SSHConfig) -> None:
        """
        Args:
            config: SSH client config
        """
        logger_tag = f"ssh | {config.host_name}"
        if config.port != DEFAULT_SSH_PORT:
            logger_tag += f':{config.port}'

        # Check if the private key exists

        self.log = logger.create_tagged_trace_logger(logger_tag)
        self.config = config

    def run(self,
            command: str,
            timeout_sec: int = DEFAULT_SSH_TIMEOUT_SEC,
            connect_retries: int = 3,
            force_tty: bool = False) -> SSHResult:
        """Run a command on the device then exit.

        Args:
            command: String to send to the device.
            timeout_sec: Seconds to wait for the command to complete.
            connect_retries: Amount of times to retry connect on fail.
            force_tty: Force pseudo-terminal allocation.

        Raises:
            FuchsiaSSHError: if the SSH command returns a non-zero status code
            FuchsiaSSHTimeout: if there is no response within timeout_sec
            FuchsiaSSHTransportError: if SSH fails to run the command

        Returns:
            SSHResults from the executed command.
        """
        err: Exception
        for i in range(0, connect_retries):
            try:
                return self._run(command, timeout_sec, force_tty)
            except FuchsiaSSHTransportError as e:
                err = e
                self.log.warn(f'Connect failed: {e}')
        raise err

    def _run(self, command: str, timeout_sec: int, force_tty: bool) -> SSHResult:
        full_command = self.config.full_command(command, force_tty)
        self.log.debug(f'Running "{" ".join(full_command)}"')
        try:
            process = subprocess.run(full_command,
                                     capture_output=True,
                                     timeout=timeout_sec,
                                     check=True)
        except subprocess.CalledProcessError as e:
            if e.returncode == 255:
                stderr = e.stderr.decode('utf-8', errors='replace')
                if 'Name or service not known' in stderr or 'Host does not exist' in stderr:
                    raise FuchsiaSSHTransportError(
                        f'Hostname {self.config.host_name} cannot be resolved to an address'
                    ) from e
                if 'Connection timed out' in stderr:
                    raise FuchsiaSSHTransportError(
                        f'Failed to establish a connection to {self.config.host_name} within {timeout_sec}s'
                    ) from e
                if 'Connection refused' in stderr:
                    raise FuchsiaSSHTransportError(
                        f'Connection refused by {self.config.host_name}') from e

            raise FuchsiaSSHError(command, SSHResult(e)) from e
        except subprocess.TimeoutExpired as e:
            raise SSHTimeout(e) from e

        return SSHResult(process)

    def start_v1_component(self,
                           component: str,
                           timeout_sec: int = 5,
                           repo: str = FUCHSIA_PACKAGE_REPO_NAME) -> None:
        """Start a CFv1 component in the background.

        Args:
            component: Name of the component without ".cmx".
            timeout_sec: Seconds to wait for the process to show up in 'ps'.
            repo: Default package repository for all components.

        Raises:
            TimeoutError: when the component doesn't launch within timeout_sec
        """
        # The "run -d" command will hang when executed without a pseudo-tty
        # allocated.
        self.run(
            f'run -d fuchsia-pkg://{repo}/{component}#meta/{component}.cmx', force_tty=True)

        timeout = time.perf_counter() + timeout_sec
        while True:
            ps_cmd = self.run("ps")
            if f'{component}.cmx' in ps_cmd.stdout:
                return
            if time.perf_counter() > timeout:
                raise TimeoutError(
                    f'Failed to start "{component}.cmx" after {timeout_sec}s')

    def stop_v1_component(self, component: str) -> None:
        """Stop all instances of a CFv1 component.

        Args:
            component: Name of the component without ".cmx"
        """
        try:
            self.run(f'killall {component}.cmx')
        except FuchsiaSSHError as e:
            if 'no tasks found' in e.result.stderr:
                return
            raise e
