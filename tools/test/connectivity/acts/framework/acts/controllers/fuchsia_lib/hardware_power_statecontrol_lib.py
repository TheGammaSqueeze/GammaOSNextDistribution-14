#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
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

import logging
import http

import acts.controllers.fuchsia_lib.base_lib as base_lib

HW_PWR_STATE_CONTROL_TIMEOUT = 5


class FuchsiaHardwarePowerStatecontrolLib(base_lib.BaseLib):

    def __init__(self, addr: str) -> None:
        super().__init__(addr, "hardware_power_statecontrol")

    def send_command(self, test_cmd, test_args, response_timeout=30):
        """Wrap send_command to allow disconnects after sending the request."""
        try:
            response = super().send_command(test_cmd, test_args,
                                            response_timeout)
        except (TimeoutError, http.client.RemoteDisconnected,
                base_lib.DeviceOffline) as e:
            logging.warn(f'Error while sending power command: {e}')
            return
        return response

    def suspendReboot(self, timeout=HW_PWR_STATE_CONTROL_TIMEOUT):
        """Call Suspend Reboot.

        Returns:
            None if success.
        """
        test_cmd = "hardware_power_statecontrol_facade.SuspendReboot"
        test_args = {}
        return self.send_command(test_cmd, test_args, response_timeout=timeout)

    def suspendRebootBootloader(self, timeout=HW_PWR_STATE_CONTROL_TIMEOUT):
        """Call Suspend Reboot Bootloader

        Returns:
            None if success.
        """
        test_cmd = "hardware_power_statecontrol_facade.SuspendRebootBootloader"
        test_args = {}
        return self.send_command(test_cmd, test_args, response_timeout=timeout)

    def suspendPoweroff(self, timeout=HW_PWR_STATE_CONTROL_TIMEOUT):
        """Call Suspend Poweroff

        Returns:
            None if success.
        """
        test_cmd = "hardware_power_statecontrol_facade.SuspendPoweroff"
        test_args = {}
        return self.send_command(test_cmd, test_args, response_timeout=timeout)

    def suspendMexec(self, timeout=HW_PWR_STATE_CONTROL_TIMEOUT):
        """Call Suspend Mexec

        Returns:
            None if success.
        """
        test_cmd = "hardware_power_statecontrol_facade.SuspendMexec"
        test_args = {}
        return self.send_command(test_cmd, test_args, response_timeout=timeout)

    def suspendRam(self, timeout=HW_PWR_STATE_CONTROL_TIMEOUT):
        """Call Suspend Ram

        Returns:
            None if success.
        """
        test_cmd = "hardware_power_statecontrol_facade.SuspendRam"
        test_args = {}
        return self.send_command(test_cmd, test_args, response_timeout=timeout)
