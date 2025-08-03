#!/usr/bin/env python
#
# Copyright 2018 - The Android Open Source Project
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

"""GCE device factory.

GCEDeviceFactory provides a base class for AVDs that run on GCE.
"""

import os

from acloud.internal import constants
from acloud.internal.lib import auth
from acloud.internal.lib import cvd_compute_client_multi_stage
from acloud.internal.lib import ssh
from acloud.public.actions import base_device_factory


class GCEDeviceFactory(base_device_factory.BaseDeviceFactory):
    """A base class for AVDs that run on GCE."""

    _USER_BUILD = "userbuild"

    def __init__(self, avd_spec, local_image_artifact=None):
        """Constructs a new remote instance device factory."""
        self._avd_spec = avd_spec
        self._cfg = avd_spec.cfg
        self._local_image_artifact = local_image_artifact
        self._report_internal_ip = avd_spec.report_internal_ip
        self._all_failures = {}
        self.credentials = auth.CreateCredentials(avd_spec.cfg)
        # Control compute_client with enable_multi_stage
        compute_client = cvd_compute_client_multi_stage.CvdComputeClient(
            acloud_config=avd_spec.cfg,
            oauth2_credentials=self.credentials,
            ins_timeout_secs=avd_spec.ins_timeout_secs,
            report_internal_ip=avd_spec.report_internal_ip,
            gpu=avd_spec.gpu)
        super().__init__(compute_client)
        self._ssh = None

    def CreateGceInstance(self):
        """Create a single configured GCE instance.

        build_target: The format is like "aosp_cf_x86_phone". We only get info
                      from the user build image file name. If the file name is
                      not custom format (no "-"), we will use $TARGET_PRODUCT
                      from environment variable as build_target.

        Returns:
            A string, representing instance name.
        """
        image_name = os.path.basename(
            self._local_image_artifact) if self._local_image_artifact else ""
        build_target = (os.environ.get(constants.ENV_BUILD_TARGET) if "-" not
                        in image_name else image_name.split("-")[0])
        build_id = self._USER_BUILD
        if self._avd_spec.image_source == constants.IMAGE_SRC_REMOTE:
            build_id = self._avd_spec.remote_image[constants.BUILD_ID]
            build_target = self._avd_spec.remote_image[constants.BUILD_TARGET]

        if self._avd_spec.instance_name_to_reuse:
            instance = self._avd_spec.instance_name_to_reuse
        else:
            instance = self._compute_client.GenerateInstanceName(
                build_target=build_target, build_id=build_id)

        host_image_name = self._compute_client.GetHostImageName(
            self._avd_spec.stable_host_image_name,
            self._cfg.stable_host_image_family,
            self._cfg.stable_host_image_project)

        # Create an instance from Stable Host Image
        self._compute_client.CreateInstance(
            instance=instance,
            image_name=host_image_name,
            image_project=self._cfg.stable_host_image_project,
            avd_spec=self._avd_spec)
        ip = self._compute_client.GetInstanceIP(instance)
        self._all_failures = self._compute_client.all_failures
        self._ssh = ssh.Ssh(ip=ip,
                            user=constants.GCE_USER,
                            ssh_private_key_path=self._cfg.ssh_private_key_path,
                            extra_args_ssh_tunnel=self._cfg.extra_args_ssh_tunnel,
                            report_internal_ip=self._report_internal_ip,
                            gce_hostname=self._compute_client.gce_hostname)
        return instance

    def GetFailures(self):
        """Get failures from all devices.

        Returns:
            A dictionary that contains all the failures.
            The key is the name of the instance that fails to boot,
            and the value is a string or an errors.DeviceBootError object.
        """
        return self._all_failures

    def _SetFailures(self, instance, error_msg):
        """Set failures from this device.

        Record the failures for any steps in AVD creation.

        Args:
            instance: String of instance name.
            error_msg: String of error message.
        """
        self._all_failures[instance] = error_msg
