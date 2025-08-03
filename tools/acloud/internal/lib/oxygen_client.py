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

"""A client that talks to Oxygen proxy APIs."""

import logging
import shlex
import subprocess


logger = logging.getLogger(__name__)


class OxygenClient():
    """Client that manages Oxygen proxy api."""

    @staticmethod
    def LeaseDevice(build_target, build_id, build_branch, system_build_target,
                    system_build_id, kernel_build_target, kernel_build_id,
                    oxygen_client, cmd_args):
        """Lease one cuttlefish device.

        Args:
            build_target: Target name, e.g. "aosp_cf_x86_64_phone-userdebug"
            build_id: Build ID, a string, e.g. "2263051", "P2804227"
            build_branch: Build branch, e.g. "aosp-master"
            system_build_target: Target name of system build
            system_build_id: Build ID of system build
            kernel_build_target: Target name of kernel build
            kernel_build_id:  Build ID of kernel build
            oxygen_client: String of oxygen client path.
            cmd_args: String of lease command args. e.g. "-user user_mail"

        Returns:
            The response of calling oxygen proxy client.
        """
        cmd = [oxygen_client, "-lease", "-build_id", build_id, "-build_target",
               build_target, "-build_branch", build_branch]
        if cmd_args:
            cmd.extend(shlex.split(cmd_args))
        if system_build_id:
            cmd.extend(["-system_build_id", system_build_id])
            cmd.extend(["-system_build_target", system_build_target])
        if kernel_build_id:
            cmd.extend(["-kernel_build_id", kernel_build_id])
            cmd.extend(["-kernel_build_target", kernel_build_target])
        logger.debug("Command to oxygen client: %s", cmd)
        response = subprocess.check_output(
            cmd, stderr=subprocess.STDOUT, encoding='utf-8')
        logger.debug("The response from oxygen client: %s", response)
        return response

    @staticmethod
    def ReleaseDevice(session_id, server_url, oxygen_client):
        """Release one cuttlefish device.

        Args:
            session_id: String of session id.
            server_url: String of server url.
            oxygen_client: String of oxygen client path.
        """
        response = subprocess.check_output([
            oxygen_client, "-release", "-session_id", session_id,
            "-server_url", server_url
        ], stderr=subprocess.STDOUT, encoding='utf-8')
        logger.debug("The response from oxygen client: %s", response)
