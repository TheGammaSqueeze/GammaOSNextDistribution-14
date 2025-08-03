#!/usr/bin/env python
#
# Copyright 2016 - The Android Open Source Project
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

"""Command report.

Report class holds the results of a command execution.
Each driver API call will generate a report instance.

If running the CLI of the driver, a report will
be printed as logs. And it will also be dumped to a json file
if requested via command line option.

The json format of a report dump looks like:

  - A failed "delete" command:
  {
    "command": "delete",
    "data": {},
    "errors": [
      "Can't find instances: ['104.197.110.255']"
    ],
    "error_type": "error_type_1",
    "status": "FAIL"
  }

  - A successful "create" command:
  {
    "command": "create",
    "data": {
       "devices": [
          {
            "instance_name": "instance_1",
            "ip": "104.197.62.36"
          },
          {
            "instance_name": "instance_2",
            "ip": "104.197.62.37"
          }
       ]
    },
    "errors": [],
    "status": "SUCCESS"
  }
"""

import json
import logging
import os

from acloud.internal import constants


logger = logging.getLogger(__name__)


class Status():
    """Status of acloud command."""

    SUCCESS = "SUCCESS"
    FAIL = "FAIL"
    BOOT_FAIL = "BOOT_FAIL"
    UNKNOWN = "UNKNOWN"

    SEVERITY_ORDER = {UNKNOWN: 0, SUCCESS: 1, FAIL: 2, BOOT_FAIL: 3}

    @classmethod
    def IsMoreSevere(cls, candidate, reference):
        """Compare the severity of two statuses.

        Args:
            candidate: One of the statuses.
            reference: One of the statuses.

        Returns:
            True if candidate is more severe than reference,
            False otherwise.

        Raises:
            ValueError: if candidate or reference is not a known state.
        """
        if (candidate not in cls.SEVERITY_ORDER or
                reference not in cls.SEVERITY_ORDER):
            raise ValueError(
                "%s or %s is not recognized." % (candidate, reference))
        return cls.SEVERITY_ORDER[candidate] > cls.SEVERITY_ORDER[reference]


def LogFile(path, log_type, name=None):
    """Create a log entry that can be added to the report.

    Args:
        path: A string, the local or remote path to the log file.
        log_type: A string, the type of the log file.
        name: A string, the optional entry name.

    Returns:
        The log entry as a dictionary.
    """
    log = {"path": path, "type": log_type}
    if name:
        log["name"] = name
    return log


class Report():
    """A class that stores and generates report."""

    def __init__(self, command):
        """Initialize.

        Args:
            command: A string, name of the command.
        """
        self.command = command
        self.status = Status.UNKNOWN
        self.errors = []
        self.error_type = ""
        self.data = {}

    def AddData(self, key, value):
        """Add a key-val to the report.

        Args:
            key: A key of basic type.
            value: A value of any json compatible type.
        """
        self.data.setdefault(key, []).append(value)

    def UpdateData(self, dict_data):
        """Update a dict data to the report.

        Args:
            dict_data: A dict of report data.
        """
        self.data.update(dict_data)

    def AddError(self, error):
        """Add error message.

        Args:
            error: A string.
        """
        self.errors.append(error)

    def AddErrors(self, errors):
        """Add a list of error messages.

        Args:
            errors: A list of string.
        """
        self.errors.extend(errors)

    def SetErrorType(self, error_type):
        """Set error type.

        Args:
            error_type: String of error type.
        """
        self.error_type = error_type

    def SetStatus(self, status):
        """Set status.

        Args:
            status: One of the status in Status.
        """
        if Status.IsMoreSevere(status, self.status):
            self.status = status
        else:
            logger.debug(
                "report: Current status is %s, "
                "requested to update to a status with lower severity %s, ignored.",
                self.status, status)

    def AddDevice(self, instance_name, ip_address, adb_port, vnc_port,
                  webrtc_port=None, device_serial=None, logs=None,
                  key="devices", update_data=None):
        """Add a record of a device.

        Args:
            instance_name: A string.
            ip_address: A string.
            adb_port: An integer.
            vnc_port: An integer.
            webrtc_port: An integer, the port to display device screen.
            device_serial: String of device serial.
            logs: A list of LogFile.
            key: A string, the data entry where the record is added.
            update_data: A dict to update device data.
        """
        device = {constants.INSTANCE_NAME: instance_name}
        if adb_port:
            device[constants.ADB_PORT] = adb_port
            device[constants.IP] = "%s:%d" % (ip_address, adb_port)
        else:
            device[constants.IP] = ip_address

        if device_serial:
            device[constants.DEVICE_SERIAL] = device_serial

        if vnc_port:
            device[constants.VNC_PORT] = vnc_port

        if webrtc_port:
            device[constants.WEBRTC_PORT] = webrtc_port

        if logs:
            device[constants.LOGS] = logs

        if update_data:
            device.update(update_data)
        self.AddData(key=key, value=device)

    def AddDeviceBootFailure(self, instance_name, ip_address, adb_port,
                             vnc_port, error, device_serial=None,
                             webrtc_port=None, logs=None):
        """Add a record of device boot failure.

        Args:
            instance_name: A string.
            ip_address: A string.
            adb_port: An integer.
            vnc_port: An integer. Can be None if the device doesn't support it.
            error: A string, the error message.
            device_serial: String of device serial.
            webrtc_port: An integer.
            logs: A list of LogFile.
        """
        self.AddDevice(instance_name, ip_address, adb_port, vnc_port,
                       webrtc_port, device_serial, logs,
                       "devices_failing_boot")
        self.AddError(error)

    def UpdateFailure(self, error, error_type=None):
        """Update the falure information of report.

        Args:
            error: String, the error message.
            error_type: String, the error type.
        """
        self.AddError(error)
        self.SetStatus(Status.FAIL)
        if error_type:
            self.SetErrorType(error_type)

    def Dump(self, report_file):
        """Dump report content to a file.

        Args:
            report_file: A path to a file where result will be dumped to.
                         If None, will only output result as logs.
        """
        result = dict(
            command=self.command,
            status=self.status,
            errors=self.errors,
            error_type=self.error_type,
            data=self.data)
        logger.info("Report: %s", json.dumps(result, indent=2, sort_keys=True))
        if not report_file:
            return
        try:
            with open(report_file, "w") as f:
                json.dump(result, f, indent=2, sort_keys=True)
            logger.info("Report file generated at %s",
                        os.path.abspath(report_file))
        except OSError as e:
            logger.error("Failed to dump report to file: %s", str(e))
