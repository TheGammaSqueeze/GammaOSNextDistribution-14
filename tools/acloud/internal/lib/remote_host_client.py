# Copyright 2022 - The Android Open Source Project
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

"""This module implements the classes and functions needed for the common
creation flow."""

import time

from acloud.internal import constants
from acloud.internal.lib import ssh
from acloud.public import config


# Report keys
_VERSION = "version"


class RemoteHostClient:
    """A client that manages an instance on a remote host.

    Attributes:
        ip_addr: A string, the IP address of the host.
        execution_time: A dictionary that records the execution time. The
                        possible keys are defined as TIME_* in constants.py.
        stage: An integer. The possible values are defined as STAGE_* in
               constants.py.
        openwrt: A boolean, whether the openwrt device is created.
        dict_report: A dict containing the data to be written to the report.
    """

    def __init__(self, ip_addr):
        """Initialize the attribtues."""
        self._ip_addr = ip_addr
        self._execution_time = {}
        self._stage = constants.STAGE_INIT
        self._openwrt = False
        self._dict_report = {_VERSION: config.GetVersion()}

    def RecordTime(self, key, start_time):
        """Record the interval between the start time and the current time.

        Args:
            key: A string, the stage name.
            start_time: A float, the timestamp when the stage starts.

        Returns:
            A float, the current time.
        """
        current = time.time()
        self._execution_time[key] = current - start_time
        return current

    def SetStage(self, stage):
        """Set device creation progress."""
        self._stage = stage

    def ExtendReportData(self, key, value):
        """Add a key-value pair to the report."""
        self._dict_report[key] = value

    # The following methods are called by common_operations.py.
    def GetInstanceIP(self, _instance_name):
        """Return the IP address of the host."""
        return ssh.IP(ip=self._ip_addr)

    @staticmethod
    def WaitForBoot(_instance_name, _boot_timeout_secs):
        """Should not be called in the common creation flow."""
        raise NotImplementedError("The common creation flow should call "
                                  "GetFailures instead of this method.")

    @staticmethod
    def GetSerialPortOutput():
        """Remote hosts do not support serial log."""
        return ""

    @property
    def execution_time(self):
        """Return execution_time."""
        return self._execution_time

    @property
    def stage(self):
        """Return stage."""
        return self._stage

    @property
    def openwrt(self):
        """Return openwrt."""
        return self._openwrt

    @openwrt.setter
    def openwrt(self, value):
        """Set openwrt."""
        self._openwrt = value

    @property
    def dict_report(self):
        """Return the key-value pairs to be written to the report."""
        return self._dict_report
