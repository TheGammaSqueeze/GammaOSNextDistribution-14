# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time
from xml.parsers import expat

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.servo import servo
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_ECChargingState(FirmwareTest):
    """
    Type-C servo-v4 based EC charging state test.
    """
    version = 1

    # The delay to wait for the AC state to update.
    AC_STATE_UPDATE_DELAY = 3

    # We wait for up to 3 hrs for the battery to report fully charged.
    FULL_CHARGE_TIMEOUT = 60 * 60 * 3

    # The period to check battery state while charging.
    CHECK_BATT_STATE_WAIT = 60

    # The min battery charged percentage that can be considered "full" by
    # powerd. Should be kPowerSupplyFullFactorPref, which defaults to 98%, but
    # that is a pref so set it a little lower to be safe.
    FULL_BATTERY_PERCENT = 95

    # Battery status
    STATUS_FULLY_CHARGED = 0x20
    STATUS_DISCHARGING = 0x40
    STATUS_TERMINATE_CHARGE_ALARM = 0x4000
    STATUS_OVER_CHARGED_ALARM = 0x8000
    # TERMINATE_CHARGE_ALARM and OVER_CHARGED_ALARM are alarms that shows up during normal use.
    # Other alarms should not appear during testing.
    STATUS_ALARM_MASK = (0xFF00 & ~STATUS_TERMINATE_CHARGE_ALARM
                         & ~STATUS_OVER_CHARGED_ALARM)

    def initialize(self, host, cmdline_args):
        super(firmware_ECChargingState, self).initialize(host, cmdline_args)
        if not self.check_ec_capability(['battery', 'charging']):
            raise error.TestNAError("Nothing needs to be tested on this DUT")
        if not self.servo.is_servo_v4_type_c():
            raise error.TestNAError(
                    "This test can only be run with servo-v4 Type-C.")
        if host.is_ac_connected() != True:
            raise error.TestFail("This test must be run with AC power.")
        self.switcher.setup_mode('normal')
        self.ec.send_command("chan save")
        self.ec.send_command("chan 0")
        self.set_dut_low_power_idle_delay(20)

    def cleanup(self):
        try:
            self.ec.send_command("chan restore")
            self.restore_dut_low_power_idle_delay()
        except Exception as e:
            logging.error("Caught exception: %s", str(e))
        super(firmware_ECChargingState, self).cleanup()

    def check_ac_state(self):
        """Check if AC is plugged."""
        ac_state = int(
                self.ec.send_command_get_output("chgstate",
                                                ["ac\s*=\s*(0|1)\s*"])[0][1])
        if ac_state == 1:
            return 'on'
        elif ac_state == 0:
            return 'off'
        else:
            return 'unknown'

    def _retry_send_cmd(self, command, regex_list):
        """Send an EC command, and retry if it fails."""
        retries = 3
        while retries > 0:
            retries -= 1
            try:
                return self.ec.send_command_get_output(command, regex_list)
            except (servo.UnresponsiveConsoleError,
                    servo.ResponsiveConsoleError, expat.ExpatError) as e:
                if retries <= 0:
                    raise
                logging.warning('Failed to send EC cmd. %s', e)

    def _get_battery_info(self):
        """Return information about the battery in a dict."""
        match = self._retry_send_cmd("battery", [
                r"Status:\s*(0x[0-9a-f]+)\s",
                r"Param flags:\s*([0-9a-f]+)\s",
                r"Charge:\s+(\d+)\s+",
        ])
        status = int(match[0][1], 16)
        params = int(match[1][1], 16)
        level = int(match[2][1])

        result = {
                "status": status,
                "flags": params,
                "level": level,
        }

        if status & self.STATUS_ALARM_MASK != 0:
            raise error.TestFail("Battery should not throw alarms: %s" %
                                 result)

        # The battery may raise a TERMINATE_CHARGE alarm transiently as
        # it becomes fully charged. Exempt that case, but catch cases where
        # it's yelling to stop for something like invalid charge parameters.
        if (status & \
            (self.STATUS_TERMINATE_CHARGE_ALARM | \
             self.STATUS_FULLY_CHARGED)) == \
            self.STATUS_TERMINATE_CHARGE_ALARM:
            raise error.TestFail(
                    "Battery raising TERMINATE_CHARGE alarm non-full: %s" %
                    result)
        return result

    def _check_kernel_battery_state(
            self,
            sysfs_battery_state,
            ec_battery_info,
    ):
        if sysfs_battery_state == 'Charging':
            # Charging is just not-discharging. There is no ec battery status
            # for charging.
            if ec_battery_info['status'] & self.STATUS_DISCHARGING != 0:
                raise error.TestFail(
                        'Kernel reports battery %s, but actual state is %s',
                        sysfs_battery_state, ec_battery_info)
        elif sysfs_battery_state == 'Fully charged':
            # Powerd has it's own creative way of determining full, it doesn't
            # use the status from the EC. So we will consider it acceptable if
            # the battery level is actually full, or above
            if (
                    ec_battery_info['status'] & self.STATUS_FULLY_CHARGED == 0
                    and ec_battery_info['level'] < self.FULL_BATTERY_PERCENT):
                raise error.TestFail(
                        'Kernel reports battery %s, but actual state is %s',
                        sysfs_battery_state, ec_battery_info)
        elif (sysfs_battery_state == 'Not charging'
              or sysfs_battery_state == 'Discharging'):
            if ec_battery_info['status'] & self.STATUS_DISCHARGING == 0:
                raise error.TestFail(
                        'Kernel reports battery %s, but actual state is %s',
                        sysfs_battery_state, ec_battery_info)
        else:
            raise error.TestFail(
                    'Kernel reports battery %s, but actual state is %s',
                    sysfs_battery_state, ec_battery_info)

    def run_once(self, host):
        """Execute the main body of the test."""

        if host.is_ac_connected() != True:
            raise error.TestFail("This test must be run with AC power.")

        logging.info("Suspend, unplug AC, and then wake up the device.")
        self.suspend()
        self.switcher.wait_for_client_offline()

        # Call set_servo_v4_role_to_snk() instead of directly setting
        # servo_v4 role to snk, so servo_v4_role can be recovered to
        # default src in cleanup().
        self.set_servo_v4_role_to_snk()
        time.sleep(self.AC_STATE_UPDATE_DELAY)

        # Verify servo v4 is sinking power.
        if self.check_ac_state() != 'off':
            raise error.TestFail("Fail to unplug AC.")

        self.servo.power_normal_press()
        self.switcher.wait_for_client()

        battery = self._get_battery_info()
        sysfs_battery_state = host.get_battery_state()
        if battery['status'] & self.STATUS_DISCHARGING == 0:
            raise error.TestFail("Wrong battery status. Expected: "
                                 "Discharging, got: %s." % battery)
        self._check_kernel_battery_state(sysfs_battery_state, battery)

        logging.info("Suspend, plug AC, and then wake up the device.")
        self.suspend()
        self.switcher.wait_for_client_offline()
        self.servo.set_servo_v4_role('src')
        time.sleep(self.AC_STATE_UPDATE_DELAY)

        # Verify servo v4 is sourcing power.
        if self.check_ac_state() != 'on':
            raise error.TestFail("Fail to plug AC.")

        self.servo.power_normal_press()
        self.switcher.wait_for_client()

        battery = self._get_battery_info()
        sysfs_battery_state = host.get_battery_state()
        if (battery['status'] & self.STATUS_FULLY_CHARGED == 0
                    and battery['status'] & self.STATUS_DISCHARGING != 0):
            raise error.TestFail("Wrong battery state. Expected: "
                                 "Charging/Fully charged, got: %s." % battery)
        self._check_kernel_battery_state(host.get_battery_state(), battery)
        logging.info("Keep charging until the battery reports fully charged.")
        deadline = time.time() + self.FULL_CHARGE_TIMEOUT
        while time.time() < deadline:
            battery = self._get_battery_info()
            if battery['status'] & self.STATUS_FULLY_CHARGED != 0:
                logging.info("The battery reports fully charged.")
                self._check_kernel_battery_state(host.get_battery_state(),
                                                 battery)
                return
            elif battery['status'] & self.STATUS_DISCHARGING == 0:
                logging.info(
                        "Wait for the battery to be fully charged. "
                        "The current battery level is %d%%.", battery['level'])
            else:
                raise error.TestFail("Wrong battery state. Expected: "
                                     "Charging/Fully charged, got: %s." %
                                     battery)
            time.sleep(self.CHECK_BATT_STATE_WAIT)

        raise error.TestFail(
                "The battery does not report fully charged "
                "before timeout is reached. The final battery "
                "level is %d%%.", battery['level'])
