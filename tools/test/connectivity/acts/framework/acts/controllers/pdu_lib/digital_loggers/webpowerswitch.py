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

from acts import signals
from acts.controllers import pdu

# Create an optional dependency for dlipower since it has a transitive
# dependency on beautifulsoup4. This library is difficult to maintain as a
# third_party dependency in Fuchsia since it is hosted on launchpad.
#
# TODO(b/246999212): Explore alternatives to the dlipower package
try:
    import dlipower
    HAS_IMPORT_DLIPOWER = True
except ImportError:
    HAS_IMPORT_DLIPOWER = False


class PduDevice(pdu.PduDevice):
    """Implementation of pure abstract PduDevice object for the Digital Loggers
    WebPowerSwitch PDUs.

    This controller supports the following Digital Loggers PDUs:
        - Pro (VII)
        - WebPowerSwitch V
        - WebPowerSwitch IV
        - WebPowerSwitch III
        - WebPowerSwitch II
        - Ethernet Power Controller III
    """

    def __init__(self, host, username, password):
        """
        Note: This may require allowing plaintext password sign in on the
        power switch, which can be configure in the device's control panel.
        """
        super(PduDevice, self).__init__(host, username, password)

        if not HAS_IMPORT_DLIPOWER:
            raise signals.ControllerError(
                'Digital Loggers PDUs are not supported with current installed '
                'packages; install the dlipower package to add support')

        self.power_switch = dlipower.PowerSwitch(hostname=host,
                                                 userid=username,
                                                 password=password)
        # Connection is made at command execution, this verifies the device
        # can be reached before continuing.
        if not self.power_switch.statuslist():
            raise pdu.PduError(
                'Failed to connect get WebPowerSwitch status. Incorrect host, '
                'userid, or password?')
        else:
            self.log.info('Connected to WebPowerSwitch (%s).' % host)

    def on_all(self):
        """Turn on power to all outlets."""
        for outlet in self.power_switch:
            outlet.on()
            self._verify_state(outlet.name, 'ON')

    def off_all(self):
        """Turn off power to all outlets."""
        for outlet in self.power_switch:
            outlet.off()
            self._verify_state(outlet.name, 'OFF')

    def on(self, outlet):
        """Turn on power to given outlet

        Args:
            outlet: string or int, the outlet name/number
        """
        self.power_switch.command_on_outlets('on', str(outlet))
        self._verify_state(outlet, 'ON')

    def off(self, outlet):
        """Turn off power to given outlet

        Args:
            outlet: string or int, the outlet name/number
        """
        self.power_switch.command_on_outlets('off', str(outlet))
        self._verify_state(outlet, 'OFF')

    def reboot(self, outlet):
        """Cycle the given outlet to OFF and back ON.

        Args:
            outlet: string or int, the outlet name/number
        """
        self.power_switch.command_on_outlets('cycle', str(outlet))
        self._verify_state(outlet, 'ON')

    def status(self):
        """ Return the status of the switch outlets.

        Return:
            a dict mapping outlet string numbers to:
                True if outlet is ON
                False if outlet is OFF
        """
        status_list = self.power_switch.statuslist()
        return {str(outlet): state == 'ON' for outlet, _, state in status_list}

    def close(self):
        # Since there isn't a long-running connection, close is not applicable.
        pass

    def _verify_state(self, outlet, expected_state, timeout=3):
        """Verify that the state of a given outlet is at an expected state.
        There can be a slight delay in when the device receives the
        command and when the state actually changes (especially when powering
        on). This function is used to verify the change has occurred before
        exiting.

        Args:
            outlet: string, the outlet name or number to check state.
            expected_state: string, 'ON' or 'OFF'

        Returns if actual state reaches expected state.

        Raises:
            PduError: if state has not reached expected state at timeout.
        """
        for _ in range(timeout):
            actual_state = self.power_switch.status(str(outlet))
            if actual_state == expected_state:
                return
            else:
                self.log.debug('Outlet %s not yet in state %s' %
                               (outlet, expected_state))
        raise pdu.PduError(
            'Outlet %s on WebPowerSwitch (%s) failed to reach expected state. \n'
            'Expected State: %s\n'
            'Actual State: %s' %
            (outlet, self.host, expected_state, actual_state))
