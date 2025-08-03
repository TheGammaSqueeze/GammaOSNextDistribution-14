# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time
from xml.parsers import expat

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest
from autotest_lib.server.cros.servo import servo


class firmware_ECCharging(FirmwareTest):
    """
    Servo based EC charging control test.
    """
    version = 1

    # Flags set by battery
    BATT_FLAG_WANT_CHARGE = 0x1
    STATUS_FULLY_CHARGED = 0x20

    # Threshold of trickle charging current in mA
    TRICKLE_CHARGE_THRESHOLD = 100

    # We wait for up to 60 minutes for the battery to allow charging.
    # kodama in particular takes a long time to discharge
    DISCHARGE_TIMEOUT = 60 * 60

    # The period to check battery state while discharging.
    CHECK_BATT_STATE_WAIT = 60

    # The delay to wait for the AC state to update.
    AC_STATE_UPDATE_DELAY = 3

    # Wait a few seconds after discharging for voltage to stabilize
    BEGIN_CHARGING_TIMEOUT = 120

    # Sleep for a second between retries when waiting for voltage to stabilize
    BEGIN_CHARGING_RETRY_TIME = 1

    # After the battery reports it is not full, keep discharging for this long.
    # This should be >= BEGIN_CHARGING_TIMEOUT
    EXTRA_DISCHARGE_TIME = BEGIN_CHARGING_TIMEOUT + 30

    def initialize(self, host, cmdline_args):
        super(firmware_ECCharging, self).initialize(host, cmdline_args)
        # Don't bother if there is no Chrome EC.
        if not self.check_ec_capability():
            raise error.TestNAError(
                    "Nothing needs to be tested on this device")
        # Only run in normal mode
        self.switcher.setup_mode('normal')
        self.ec.send_command("chan 0")

    def cleanup(self):
        try:
            self.ec.send_command("chan 0xffffffff")
        except Exception as e:
            logging.error("Caught exception: %s", str(e))
        super(firmware_ECCharging, self).cleanup()

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

    def _get_charge_state(self):
        """Get charger and battery information in a single call."""
        output = self._retry_send_cmd("chgstate", [
                r"chg\.\*:",
                r"voltage = (-?\d+)mV",
                r"current = (-?\d+)mA",
                r"batt\.\*:",
                r"voltage = (-?\d+)mV",
                r"current = (-?\d+)mA",
                r"desired_voltage = (-?\d+)mV",
                r"desired_current = (-?\d+)mA",
        ])
        result = {
                "charger_target_voltage": int(output[1][1]),
                "charger_target_current": int(output[2][1]),
                "battery_actual_voltage": int(output[4][1]),
                "battery_actual_current": int(output[5][1]),
                "battery_desired_voltage": int(output[6][1]),
                "battery_desired_current": int(output[7][1]),
        }
        logging.info("Charger & battery info: %s", result)
        return result

    def _get_trickle_charging(self):
        """Check if we are trickle charging battery."""
        return (self.ec.get_battery_desired_current() <
                self.TRICKLE_CHARGE_THRESHOLD)

    def _check_voltages_and_currents(self):
        """Check that the battery and charger voltages and currents are within
        acceptable limits.

        Raise:
          error.TestFail: Raised when check fails.
        """
        state = self._get_charge_state()
        target_voltage = state['charger_target_voltage']
        desired_voltage = state['battery_desired_voltage']
        target_current = state['charger_target_current']
        desired_current = state['battery_desired_current']
        actual_voltage = state['battery_actual_voltage']
        actual_current = state['battery_actual_current']
        logging.info("Checking charger target values...")
        if (target_voltage >= 1.05 * desired_voltage):
            raise error.TestFail(
                    "Charger target voltage is too high. %d/%d=%f" %
                    (target_voltage, desired_voltage,
                     float(target_voltage) / desired_voltage))
        if (target_current >= 1.05 * desired_current):
            raise error.TestFail(
                    "Charger target current is too high. %d/%d=%f" %
                    (target_current, desired_current,
                     float(target_current) / desired_current))

        logging.info("Checking battery actual values...")
        if (actual_voltage >= 1.05 * target_voltage):
            raise error.TestFail(
                    "Battery actual voltage is too high. %d/%d=%f" %
                    (actual_voltage, target_voltage,
                     float(actual_voltage) / target_voltage))
        if (actual_current >= 1.05 * target_current):
            raise error.TestFail(
                    "Battery actual current is too high. %d/%d=%f" %
                    (actual_current, target_current,
                     float(actual_current) / target_current))

    def _check_if_discharge_on_ac(self):
        """Check if DUT is performing discharge on AC"""
        match = self._retry_send_cmd("battery", [
                r"Status:\s*(0x[0-9a-f]+)\s", r"Param flags:\s*([0-9a-f]+)\s"
        ])
        status = int(match[0][1], 16)
        params = int(match[1][1], 16)

        if (not (params & self.BATT_FLAG_WANT_CHARGE) and
                (status & self.STATUS_FULLY_CHARGED)):
            return True

        return False

    def _check_battery_discharging(self):
        """Check if AC is attached and if charge control is normal."""
        # chg_ctl_mode may look like: chg_ctl_mode = 2
        # or: chg_ctl_mode = DISCHARGE (2)
        # The regex needs to match either one.
        output = self._retry_send_cmd("chgstate", [
                r"ac\s*=\s*(\d)\s*",
                r"chg_ctl_mode\s*=\s*(\S* \(\d+\)|\d+)\r\n"
        ])
        ac_state = int(output[0][1])
        chg_ctl_mode = output[1][1]
        if ac_state == 0:
            return True
        if chg_ctl_mode == "2" or chg_ctl_mode == "DISCHARGE (2)":
            return True
        return False

    def _set_battery_discharge(self):
        """Instruct the EC to drain the battery."""
        # Ask EC to drain the battery
        output = self._retry_send_cmd("chgstate discharge on", [
                r"state =|Parameter 1 invalid",
        ])
        logging.debug("chgstate returned %s", output)
        if output[0] == 'Parameter 1 invalid':
            raise error.TestNAError(
                    "Device doesn't support CHARGER_DISCHARGE_ON_AC, "
                    "please drain battery below full and run the test again.")
        time.sleep(self.AC_STATE_UPDATE_DELAY)

        # Verify discharging. Either AC off or charge control discharge is
        # good.
        if not self._check_battery_discharging():
            raise error.TestFail("Battery is not discharging.")

    def _set_battery_normal(self):
        """Instruct the EC to charge the battery as normal."""
        self.ec.send_command("chgstate discharge off")
        time.sleep(self.AC_STATE_UPDATE_DELAY)

        # Verify AC is on and charge control is normal.
        if self._check_battery_discharging():
            raise error.TestFail("Fail to plug AC and enable charging.")
        self.ec.update_battery_info()

    def _consume_battery(self, deadline):
        """Perform battery intensive operation to make the battery discharge
        faster."""
        # Switch to servo drain after b/140965614.
        stress_time = deadline - time.time()
        if stress_time > self.CHECK_BATT_STATE_WAIT:
            stress_time = self.CHECK_BATT_STATE_WAIT
        self._client.run("stressapptest -s %d " % stress_time,
                         ignore_status=True)

    def _discharge_below_100(self):
        """Remove AC power until the battery is not full."""
        self._set_battery_discharge()
        logging.info(
                "Keep discharging until the battery reports charging allowed.")

        try:
            # Wait until DISCHARGE_TIMEOUT or charging allowed
            deadline = time.time() + self.DISCHARGE_TIMEOUT
            while time.time() < deadline:
                self.ec.update_battery_info()
                if self.ec.get_battery_charging_allowed():
                    break
                logging.info("Wait for the battery to discharge (%d mAh).",
                             self.ec.get_battery_remaining())
                self._consume_battery(deadline)
            else:
                raise error.TestFail(
                        "The battery does not report charging allowed "
                        "before timeout is reached.")

            # Wait another EXTRA_DISCHARGE_TIME just to be sure
            deadline = time.time() + self.EXTRA_DISCHARGE_TIME
            while time.time() < deadline:
                self.ec.update_battery_info()
                logging.info(
                        "Wait for the battery to discharge even more (%d mAh).",
                        self.ec.get_battery_remaining())
                self._consume_battery(deadline)
        finally:
            self._set_battery_normal()

        # For many devices, it takes some time after discharging for the
        # battery to actually start charging.
        deadline = time.time() + self.BEGIN_CHARGING_TIMEOUT
        while time.time() < deadline:
            self.ec.update_battery_info()
            if self.ec.get_battery_actual_current() >= 0:
                break
            logging.info(
                    'Battery actual current (%d) too low, wait a bit. (%d mAh)',
                    self.ec.get_battery_actual_current(),
                    self.ec.get_battery_remaining())
            self._consume_battery(deadline)

    def run_once(self):
        """Execute the main body of the test.
        """
        if not self.check_ec_capability(['battery', 'charging']):
            raise error.TestNAError(
                    "Nothing needs to be tested on this device")
        if not self.ec.get_battery_charging_allowed(
        ) or self.ec.get_battery_actual_current() < 0:
            logging.info(
                    "Battery is full or discharging. Forcing battery discharge "
                    "to test charging.")
            self._discharge_below_100()
            if not self.ec.get_battery_charging_allowed():
                raise error.TestFail(
                        "Battery reports charging is not allowed, even after "
                        "discharging.")
        if self._check_if_discharge_on_ac():
            raise error.TestNAError(
                    "DUT is performing discharge on AC. Unable to test.")
        if self._get_trickle_charging():
            raise error.TestNAError(
                    "Trickling charging battery. Unable to test.")
        if self.ec.get_battery_actual_current() < 0:
            raise error.TestFail(
                    "The device is not charging. Is the test run with AC "
                    "plugged?")

        self._check_voltages_and_currents()
