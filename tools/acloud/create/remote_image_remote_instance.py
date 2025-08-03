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
r"""RemoteImageRemoteInstance class.

Create class that is responsible for creating a remote instance AVD with a
remote image.
"""

import logging
import re
import subprocess
import time

from acloud.create import base_avd_create
from acloud.internal import constants
from acloud.internal.lib import oxygen_client
from acloud.internal.lib import utils
from acloud.public.actions import common_operations
from acloud.public.actions import remote_instance_cf_device_factory
from acloud.public import report


logger = logging.getLogger(__name__)
_DEVICE = "device"
_DEVICES = "devices"
_LAUNCH_CVD_TIME = "launch_cvd_time"
_RE_SESSION_ID = re.compile(r".*session_id:\"(?P<session_id>[^\"]+)")
_RE_SERVER_URL = re.compile(r".*server_url:\"(?P<server_url>[^\"]+)")
_RE_OXYGEN_LEASE_ERROR = re.compile(
    r".*Error received while trying to lease device: (?P<error>.*)$", re.DOTALL)


class RemoteImageRemoteInstance(base_avd_create.BaseAVDCreate):
    """Create class for a remote image remote instance AVD."""

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
        if avd_spec.oxygen:
            return self._LeaseOxygenAVD(avd_spec)
        if avd_spec.gce_only:
            return self._CreateGceInstance(avd_spec)
        device_factory = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            avd_spec)
        create_report = common_operations.CreateDevices(
            "create_cf", avd_spec.cfg, device_factory, avd_spec.num,
            report_internal_ip=avd_spec.report_internal_ip,
            autoconnect=avd_spec.autoconnect,
            avd_type=constants.TYPE_CF,
            boot_timeout_secs=avd_spec.boot_timeout_secs,
            unlock_screen=avd_spec.unlock_screen,
            wait_for_boot=False,
            connect_webrtc=avd_spec.connect_webrtc,
            client_adb_port=avd_spec.client_adb_port)
        if create_report.status == report.Status.SUCCESS:
            if avd_spec.connect_vnc:
                utils.LaunchVNCFromReport(create_report, avd_spec, no_prompts)
            if avd_spec.connect_webrtc:
                utils.LaunchBrowserFromReport(create_report)

        return create_report

    def _LeaseOxygenAVD(self, avd_spec):
        """Lease the AVD from the AVD pool.

        Args:
            avd_spec: AVDSpec object that tells us what we're going to create.

        Returns:
            A Report instance.
        """
        timestart = time.time()
        session_id = None
        server_url = None
        try:
            response = oxygen_client.OxygenClient.LeaseDevice(
                avd_spec.remote_image[constants.BUILD_TARGET],
                avd_spec.remote_image[constants.BUILD_ID],
                avd_spec.remote_image[constants.BUILD_BRANCH],
                avd_spec.system_build_info[constants.BUILD_TARGET],
                avd_spec.system_build_info[constants.BUILD_ID],
                avd_spec.kernel_build_info[constants.BUILD_TARGET],
                avd_spec.kernel_build_info[constants.BUILD_ID],
                avd_spec.cfg.oxygen_client,
                avd_spec.cfg.oxygen_lease_args)
            session_id, server_url = self._GetDeviceInfoFromResponse(response)
            execution_time = round(time.time() - timestart, 2)
        except subprocess.CalledProcessError as e:
            logger.error("Failed to lease device from Oxygen, error: %s",
                e.output)
            response = e.output

        reporter = report.Report(command="create_cf")
        if session_id and server_url:
            reporter.SetStatus(report.Status.SUCCESS)
            device_data = {"instance_name": session_id,
                           "ip": server_url}
            device_data[_LAUNCH_CVD_TIME] = execution_time
            dict_devices = {_DEVICES: [device_data]}
            reporter.UpdateData(dict_devices)
        else:
            # Try to parse client error
            match = _RE_OXYGEN_LEASE_ERROR.match(response)
            if match:
                response = match.group("error").strip()

            reporter.SetStatus(report.Status.FAIL)
            reporter.SetErrorType(constants.ACLOUD_OXYGEN_LEASE_ERROR)
            reporter.AddError(response)

        return reporter

    @staticmethod
    def _GetDeviceInfoFromResponse(response):
        """Get session id and server url from response.

        Args:
            response: String of the response from oxygen proxy client.
                      e.g. "2021/08/02 11:28:52 session_id: "74b6b835"
                      server_url: "0.0.0.34" port:{type:WATERFALL ..."

        Returns:
            The session id and the server url of leased device.
        """
        session_id = ""
        for line in response.splitlines():
            session_id_match = _RE_SESSION_ID.match(line)
            if session_id_match:
                session_id = session_id_match.group("session_id")
                break

        server_url = ""
        for line in response.splitlines():
            server_url_match = _RE_SERVER_URL.match(line)
            if server_url_match:
                server_url = server_url_match.group("server_url")
                break
        return session_id, server_url

    @staticmethod
    def _CreateGceInstance(avd_spec):
        """Create the GCE instance.

        Args:
            avd_spec: AVDSpec object.

        Returns:
            A Report instance.
        """
        device_factory = remote_instance_cf_device_factory.RemoteInstanceDeviceFactory(
            avd_spec)
        instance = device_factory.CreateGceInstance()
        compute_client = device_factory.GetComputeClient()
        ip = compute_client.GetInstanceIP(instance)
        reporter = report.Report(command="create_cf")
        reporter.SetStatus(report.Status.SUCCESS)
        device_data = {"instance_name": instance,
                       "ip": ip.internal if avd_spec.report_internal_ip
                       else ip.external}
        dict_devices = {_DEVICES: [device_data]}
        reporter.UpdateData(dict_devices)
        return reporter
