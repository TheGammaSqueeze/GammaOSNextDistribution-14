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
r"""GoldfishRemoteHost class.

Create class that is responsible for creating a goldfish instance with remote
or local images on a remote host.
"""

import logging

from acloud.create import base_avd_create
from acloud.internal import constants
from acloud.internal.lib import utils
from acloud.public.actions import common_operations
from acloud.public.actions import remote_host_gf_device_factory

logger = logging.getLogger(__name__)


class GoldfishRemoteHost(base_avd_create.BaseAVDCreate):
    """Create goldfish on a remote host."""

    @utils.TimeExecute(function_description="Total time: ",
                       print_before_call=False, print_status=False)
    def _CreateAVD(self, avd_spec, no_prompts):
        """Create the AVD.

        Args:
            avd_spec: AVDSpec object that tells us what we're going to create.
            no_prompts: Boolean, True to skip all prompts.

        Returns:
            A Report instance.
        """
        device_factory = remote_host_gf_device_factory.RemoteHostGoldfishDeviceFactory(
            avd_spec=avd_spec)
        if avd_spec.num != 1:
            logger.warning("Multiple goldfish instances on remote host are "
                           "not supported.")
        if avd_spec.connect_webrtc:
            logger.warning("Goldfish on remote host does not support WebRTC.")
        if avd_spec.serial_log_file:
            logger.warning("Goldfish on remote host does not support serial "
                           "log file.")
        return common_operations.CreateDevices(
            "create", avd_spec.cfg, device_factory, avd_spec.num,
            constants.TYPE_GF,
            report_internal_ip=avd_spec.report_internal_ip,
            autoconnect=avd_spec.autoconnect,
            serial_log_file=avd_spec.serial_log_file,
            client_adb_port=avd_spec.client_adb_port,
            boot_timeout_secs=avd_spec.boot_timeout_secs,
            unlock_screen=avd_spec.unlock_screen, wait_for_boot=False,
            connect_webrtc=avd_spec.connect_webrtc,
            ssh_private_key_path=avd_spec.host_ssh_private_key_path,
            ssh_user=avd_spec.host_user)
