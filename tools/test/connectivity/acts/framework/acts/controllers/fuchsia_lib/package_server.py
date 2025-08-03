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
import shutil
import socket
import subprocess
import tarfile
import tempfile

from dataclasses import dataclass
from datetime import datetime
from typing import TextIO, List, Optional

from acts import context
from acts import logger
from acts import signals
from acts import utils

from acts.controllers.fuchsia_lib.ssh import FuchsiaSSHError, SSHProvider
from acts.controllers.fuchsia_lib.utils_lib import wait_for_port
from acts.tracelogger import TraceLogger

DEFAULT_FUCHSIA_REPO_NAME = "fuchsia.com"
PM_SERVE_STOP_TIMEOUT_SEC = 5


class PackageServerError(signals.TestAbortClass):
    pass


def random_port() -> int:
    s = socket.socket()
    s.bind(('', 0))
    return s.getsockname()[1]


@dataclass
class Route:
    """Represent a route in the routing table."""
    preferred_source: Optional[str]


def find_routes_to(dest_ip) -> List[Route]:
    """Find the routes used to reach a destination.

    Look through the routing table for the routes that would be used without
    sending any packets. This is especially helpful for when the device is
    currently unreachable.

    Only natively supported on Linux. MacOS has iproute2mac, but it doesn't
    support JSON formatted output.

    TODO(http://b/238924195): Add support for MacOS.

    Args:
        dest_ip: IP address of the destination

    Throws:
        CalledProcessError: if the ip command returns a non-zero exit code
        JSONDecodeError: if the ip command doesn't return JSON

    Returns:
        Routes with destination to dest_ip.
    """
    resp = subprocess.run(f"ip -json route get {dest_ip}".split(),
                          capture_output=True,
                          check=True)
    routes = json.loads(resp.stdout)
    return [Route(r.get("prefsrc")) for r in routes]


def find_host_ip(device_ip: str) -> str:
    """Find the host's source IP used to reach a device.

    Not all host interfaces can talk to a given device. This limitation can
    either be physical through hardware or virtual through routing tables.
    Look through the routing table without sending any packets then return the
    preferred source IP address.

    Args:
        device_ip: IP address of the device

    Raises:
        PackageServerError: if there are multiple or no routes to device_ip, or
            if the route doesn't contain "prefsrc"

    Returns:
        The host IP used to reach device_ip.
    """
    routes = find_routes_to(device_ip)
    if len(routes) != 1:
        raise PackageServerError(
            f"Expected only one route to {device_ip}, got {routes}")

    route = routes[0]
    if not route.preferred_source:
        raise PackageServerError(f'Route does not contain "prefsrc": {route}')
    return route.preferred_source


class PackageServer:
    """Package manager for Fuchsia; an interface to the "pm" CLI tool."""

    def __init__(self, packages_archive_path: str) -> None:
        """
        Args:
            packages_archive_path: Path to an archive containing the pm binary
                and amber-files.
        """
        self.log: TraceLogger = logger.create_tagged_trace_logger("pm")

        self._server_log: Optional[TextIO] = None
        self._server_proc: Optional[subprocess.Popen] = None
        self._log_path: Optional[str] = None

        self._tmp_dir = tempfile.mkdtemp(prefix="packages-")
        tar = tarfile.open(packages_archive_path, "r:gz")
        tar.extractall(self._tmp_dir)

        self._binary_path = os.path.join(self._tmp_dir, "pm")
        self._packages_path = os.path.join(self._tmp_dir, "amber-files")
        self._port = random_port()

        self._assert_repo_has_not_expired()

    def clean_up(self) -> None:
        if self._server_proc:
            self.stop_server()
        if self._tmp_dir:
            shutil.rmtree(self._tmp_dir)

    def _assert_repo_has_not_expired(self) -> None:
        """Abort if the repository metadata has expired.

        Raises:
            TestAbortClass: when the timestamp.json file has expired
        """
        with open(f'{self._packages_path}/repository/timestamp.json',
                  'r') as f:
            data = json.load(f)
            expiresAtRaw = data["signed"]["expires"]
            expiresAt = datetime.strptime(expiresAtRaw, '%Y-%m-%dT%H:%M:%SZ')
            if expiresAt <= datetime.now():
                raise signals.TestAbortClass(
                    f'{self._packages_path}/repository/timestamp.json has expired on {expiresAtRaw}'
                )

    def start(self) -> None:
        """Start the package server.

        Does not check for errors; view the log file for any errors.
        """
        if self._server_proc:
            self.log.warn(
                "Skipping to start the server since it has already been started"
            )
            return

        pm_command = f'{self._binary_path} serve -c 2 -repo {self._packages_path} -l :{self._port}'

        root_dir = context.get_current_context().get_full_output_path()
        epoch = utils.get_current_epoch_time()
        time_stamp = logger.normalize_log_line_timestamp(
            logger.epoch_to_log_line_timestamp(epoch))
        self._log_path = os.path.join(root_dir, f'pm_server.{time_stamp}.log')

        self._server_log = open(self._log_path, 'a+')
        self._server_proc = subprocess.Popen(pm_command.split(),
                                             preexec_fn=os.setpgrp,
                                             stdout=self._server_log,
                                             stderr=subprocess.STDOUT)
        try:
            wait_for_port('127.0.0.1', self._port)
        except TimeoutError as e:
            if self._server_log:
                self._server_log.close()
            if self._log_path:
                with open(self._log_path, 'r') as f:
                    logs = f.read()
            raise TimeoutError(
                f"pm serve failed to expose port {self._port}. Logs:\n{logs}"
            ) from e

        self.log.info(f'Serving packages on port {self._port}')

    def configure_device(self,
                         ssh: SSHProvider,
                         repo_name=DEFAULT_FUCHSIA_REPO_NAME) -> None:
        """Configure the device to use this package server.

        Args:
            ssh: Device SSH transport channel
            repo_name: Name of the repo to alias this package server
        """
        # Remove any existing repositories that may be stale.
        try:
            ssh.run(f'pkgctl repo rm fuchsia-pkg://{repo_name}')
        except FuchsiaSSHError as e:
            if 'NOT_FOUND' not in e.result.stderr:
                raise e

        # Configure the device with the new repository.
        host_ip = find_host_ip(ssh.config.host_name)
        repo_url = f"http://{host_ip}:{self._port}"
        ssh.run(
            f"pkgctl repo add url -f 2 -n {repo_name} {repo_url}/config.json")
        self.log.info(
            f'Added repo "{repo_name}" as {repo_url} on device {ssh.config.host_name}'
        )

    def stop_server(self) -> None:
        """Stop the package server."""
        if not self._server_proc:
            self.log.warn(
                "Skipping to stop the server since it hasn't been started yet")
            return

        self._server_proc.terminate()
        try:
            self._server_proc.wait(timeout=PM_SERVE_STOP_TIMEOUT_SEC)
        except subprocess.TimeoutExpired:
            self.log.warn(
                f"Taking over {PM_SERVE_STOP_TIMEOUT_SEC}s to stop. Killing the server"
            )
            self._server_proc.kill()
            self._server_proc.wait(timeout=PM_SERVE_STOP_TIMEOUT_SEC)
        finally:
            if self._server_log:
                self._server_log.close()

        self._server_proc = None
        self._log_path = None
        self._server_log = None
