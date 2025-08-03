# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import time
import logging
from threading import Timer

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_ECPowerButton(FirmwareTest):
    """
    Servo based EC power button test.
    """
    version = 1

    # Delay between recovery screen and shutdown by power button
    RECOVERY_SCREEN_SHUTDOWN_DELAY = 3

    # Duration of holding down power button to test ignoring power button press
    POWER_BUTTON_IGNORE_PRESS_DURATION = 0.2

    # Delay after pressing power button to check power state
    POWER_BUTTON_IGNORE_PRESS_DELAY = 10

    # Number of tries when checking power state
    POWER_STATE_CHECK_TRIES = 20

    # After the device has reached the wanted shutdown power states (S5 or G3),
    # wait for a short time before executing a power button wakeup.
    SHUTDOWN_STABLE_DELAY = 1

    def initialize(self, host, cmdline_args):
        super(firmware_ECPowerButton, self).initialize(host, cmdline_args)

        # Duration of holding down power button to shut down with powerd
        self.POWER_BUTTON_POWERD_DURATION = (
                self.faft_config.hold_pwr_button_poweroff)
        # Duration of holding down power button to shut down without powerd
        self.POWER_BUTTON_NO_POWERD_DURATION = max(
                self.faft_config.hold_pwr_button_nopowerd_shutdown, 11)
        # Short duration of holding down power button to power on
        self.POWER_BUTTON_SHORT_POWER_ON_DURATION = max(
                self.faft_config.hold_pwr_button_poweron, 0.05)
        # Long duration of holding down power button to power on
        self.POWER_BUTTON_LONG_POWER_ON_DURATION = max(
                self.faft_config.hold_pwr_button_poweron, 1)
        # Only run in normal mode
        self.switcher.setup_mode('normal')
        self.has_display = host.has_internal_display() or \
                host.has_external_display()

    def kill_powerd(self):
        """Stop powerd on client."""
        self.faft_client.system.run_shell_command("stop powerd")

    def debounce_power_button(self):
        """Check if power button debouncing works.

        Press power button for a very short period and checks for power
        button keycode.
        """
        # Delay 3 seconds to ensure client machine is waiting for key press.
        # Press power button for only 10ms. Should be debounced.
        logging.info('ECPowerButton: debounce_power_button')
        Timer(3, self.servo.power_key, [0.001]).start()
        return self.faft_client.system.check_keys([116])

    def shutdown_and_wake(self, shutdown_powerkey_duration, power_state,
                          wake_powerkey_duration):
        """
        Shutdown the system by power button, delay, wait for requested power
        states and then power on by power button again.
        """

        # Shutdown the system by pressing the power button
        self.servo.power_key(shutdown_powerkey_duration)

        # Wait for the system to enter the requested power mode
        if not self.wait_power_state(power_state,
                                     self.POWER_STATE_CHECK_TRIES):
            raise error.TestFail('The device failed to reach %s.' %
                                 power_state)

        # Add a delay to confirm the system is stabily shut down
        time.sleep(self.SHUTDOWN_STABLE_DELAY)

        # Send a new line to wakeup EC from deepsleep,
        # it can happen if the EC console is not used for some time.
        self.ec.send_command("")

        # Power on the system by pressing the power button
        self.servo.power_key(wake_powerkey_duration)

        # Some platforms undergo extra power state transitions during power-on.
        # We need to wait for longer time for the power state to be stable.
        time.sleep(self.faft_config.delay_powerinfo_stable)

    def run_once(self):
        """Runs a single iteration of the test."""
        if not self.check_ec_capability():
            raise error.TestNAError("Nothing needs to be tested on this device")

        # Ensure that detachable is in OFF State for following test
        if self.faft_config.is_detachable:
            # Skip this test step for detachable
            # Setting Power State to off for entry to next step
            logging.info("Setting Power Off")
            self.servo.get_power_state_controller().power_off()
        else:
            # Run these test steps for non detachable devices
            logging.info("Boot to recovery screen.")
            self.switcher.enable_rec_mode_and_reboot(usb_state='host')
            time.sleep(self.faft_config.firmware_screen)
            if self.get_power_state() != self.POWER_STATE_S0:
                raise error.TestFail("DUT didn't boot to recovery screen")

            logging.info("Shutdown by short power button press.")
            self.servo.power_key(self.faft_config.hold_pwr_button_poweron)
            time.sleep(self.RECOVERY_SCREEN_SHUTDOWN_DELAY)
            power_state = self.get_power_state()
            if (power_state != self.POWER_STATE_S5
                        and power_state != self.POWER_STATE_G3):
                raise error.TestFail("DUT didn't shutdown by "
                                     "short power button press")
            if self.ec.check_feature('EC_FEATURE_EFS2'):
                logging.info("Check if EC jumped to RW.")
                if not self.ec.check_ro_rw('RW'):
                    raise error.TestFail("EC didn't jump to RW")

        logging.info("Boot by short power button press.")
        self.servo.power_key(self.faft_config.hold_pwr_button_poweron)
        self.switcher.wait_for_client()
        if self.get_power_state() != self.POWER_STATE_S0:
            raise error.TestFail("DUT didn't boot by short power button press")

        if self.has_display:
            logging.info("Display connected, check system ignores short 200ms "
                         "power button press.")
            old_boot_id = self.get_bootid(retry=1)
            self.servo.power_key(self.POWER_BUTTON_IGNORE_PRESS_DURATION)
            time.sleep(self.POWER_BUTTON_IGNORE_PRESS_DELAY)
            power_state = self.get_power_state()
            new_boot_id = self.get_bootid(retry=1)
            if power_state != self.POWER_STATE_S0 or new_boot_id != old_boot_id:
                self._reset_client()
                raise error.TestFail("DUT shutdown from short 200ms power "
                                     "button press")
        else:
            logging.info("No display connected, check system shuts down from "
                         "short 200ms power button check.")
            self.servo.power_key(self.POWER_BUTTON_IGNORE_PRESS_DURATION)
            time.sleep(self.POWER_BUTTON_IGNORE_PRESS_DELAY)
            power_state = self.get_power_state()
            logging.info("Power state = %s", power_state)
            if (power_state != self.POWER_STATE_S5 and
                power_state != self.POWER_STATE_G3):
                self._reset_client()
                raise error.TestFail("DUT didn't shutdown by "
                                    "short power button press")
            self.servo.power_key(self.faft_config.hold_pwr_button_poweron)
            self.switcher.wait_for_client()
            if self.get_power_state() != self.POWER_STATE_S0:
                self._reset_client()
                raise error.TestFail("DUT didn't boot by short power button press")

        logging.info(
                "Shutdown when powerd is still running and wake from S5/G3 "
                "with short power button press.")
        if self.servo.is_localhost() and self.has_display:
            self.check_state(self.debounce_power_button)
        self.switcher.mode_aware_reboot(
                'custom', lambda: self.shutdown_and_wake(
                        self.POWER_BUTTON_POWERD_DURATION,
                        self.POWER_STATE_S5 + '|' + self.POWER_STATE_G3,
                        self.POWER_BUTTON_SHORT_POWER_ON_DURATION))

        logging.info("Shutdown when powerd is stopped and wake from G3 "
                          "with short power button press.")
        self.kill_powerd()
        self.switcher.mode_aware_reboot(
                'custom', lambda: self.shutdown_and_wake(
                        self.POWER_BUTTON_NO_POWERD_DURATION, self.
                        POWER_STATE_G3,
                        self.POWER_BUTTON_SHORT_POWER_ON_DURATION))

        logging.info("Shutdown when powerd is still running and wake from G3 "
                     "with long power button press.")
        self.switcher.mode_aware_reboot(
                'custom', lambda: self.shutdown_and_wake(
                        self.POWER_BUTTON_POWERD_DURATION,
                        self.POWER_STATE_G3,
                        self.POWER_BUTTON_LONG_POWER_ON_DURATION))

        logging.info("Shutdown when powerd is stopped and wake from S5/G3 "
                     "with long power button press.")
        self.kill_powerd()
        self.switcher.mode_aware_reboot(
                'custom', lambda: self.shutdown_and_wake(
                        self.POWER_BUTTON_NO_POWERD_DURATION,
                        self.POWER_STATE_S5 + '|' + self.POWER_STATE_G3,
                        self.POWER_BUTTON_LONG_POWER_ON_DURATION))
