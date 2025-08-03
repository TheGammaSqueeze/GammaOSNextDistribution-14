# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from threading import Timer
import logging
import re
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


def delayed(seconds): # pylint:disable=missing-docstring
    def decorator(f): # pylint:disable=missing-docstring
        def wrapper(*args, **kargs): # pylint:disable=missing-docstring
            t = Timer(seconds, f, args, kargs)
            t.start()
        return wrapper
    return decorator


class firmware_ECLidSwitch(FirmwareTest):
    """
    Servo based EC lid switch test.
    """
    version = 1

    # Delay between closing and opening the lid
    LID_DELAY = 1

    # Delay to allow FAFT client receive command
    RPC_DELAY = 2

    # Delay between shutdown and wakeup by lid switch
    WAKE_DELAY = 10

    # Number of tries when checking power state
    POWER_STATE_CHECK_TRIES = 50

    # Delay between checking power state
    POWER_STATE_CHECK_DELAY = 0.5

    def initialize(self, host, cmdline_args):
        super(firmware_ECLidSwitch, self).initialize(host, cmdline_args)
        # Only run in normal mode
        self.switcher.setup_mode('normal')

    def cleanup(self):
        self.faft_client.system.run_shell_command_get_status(
                "rm -rf /tmp/power_manager")

        return super().cleanup()

    def _open_lid(self):
        """Open lid by servo."""
        self.servo.set('lid_open', 'yes')

    def _close_lid(self):
        """Close lid by servo."""
        self.servo.set('lid_open', 'no')

    @delayed(RPC_DELAY)
    def delayed_open_lid(self):
        """Delay by RPC_DELAY and then open lid by servo."""
        self._open_lid()

    @delayed(RPC_DELAY)
    def delayed_close_lid(self):
        """Delay by RPC_DELAY and then close lid by servo."""
        self._close_lid()

    def _wake_by_lid_switch(self):
        """Wake DUT with lid switch."""
        self._close_lid()
        time.sleep(self.LID_DELAY)
        self._open_lid()

    def delayed_wake(self):
        """
        Wait for WAKE_DELAY, and then wake DUT with lid switch.
        """
        time.sleep(self.WAKE_DELAY)
        self._wake_by_lid_switch()

    def immediate_wake(self):
        """Wake DUT with lid switch."""
        self._wake_by_lid_switch()

    def shutdown_cmd(self):
        """Shut down the DUT but don't wait for ping failures."""
        self.run_shutdown_cmd(wait_for_offline=False)

    def shutdown_and_wake(self, shutdown_func, wake_func):
        """Software shutdown and wake with check for power state

        Args:
          shutdown_func: Function to shut down DUT.
          wake_func: Delayed function to wake DUT.
        """

        # Call shutdown function to power down device
        logging.debug('calling shutdown_func')
        shutdown_func()

        # Check device shutdown to correct power state
        shutdown_power_states = '|'.join(
                [self.POWER_STATE_S5, self.POWER_STATE_G3])
        if not self.wait_power_state(shutdown_power_states,
                                     self.POWER_STATE_CHECK_TRIES,
                                     self.POWER_STATE_CHECK_DELAY):
            raise error.TestFail(
                    'The device failed to reach %s after calling shutdown function.',
                    shutdown_power_states)

        # Call wake function to wake up device
        logging.debug('calling wake_func')
        wake_func()

        # Check power state to verify device woke up to S0
        wake_power_state = self.POWER_STATE_S0
        if not self.wait_power_state(wake_power_state,
                                     self.POWER_STATE_CHECK_TRIES,
                                     self.POWER_STATE_CHECK_DELAY):
            raise error.TestFail(
                    'The device failed to reach %s after calling wake function.',
                    wake_power_state)
        # Wait for the DUT to boot and respond to ssh before we move on.
        self.switcher.wait_for_client()

    def _get_keyboard_backlight(self):
        """Get keyboard backlight brightness.

        Returns:
          Backlight brightness percentage 0~100. If it is disabled, 0 is
            returned.
        """
        cmd = 'ectool pwmgetkblight'
        pattern_percent = re.compile(
            'Current keyboard backlight percent: (\d*)')
        pattern_disable = re.compile('Keyboard backlight disabled.')
        lines = self.faft_client.system.run_shell_command_get_output(cmd)
        for line in lines:
            matched_percent = pattern_percent.match(line)
            if matched_percent is not None:
                return int(matched_percent.group(1))
            matched_disable = pattern_disable.match(line)
            if matched_disable is not None:
                return 0
        raise error.TestError('Cannot get keyboard backlight status.')

    def _set_keyboard_backlight(self, value):
        """Set keyboard backlight brightness.

        Args:
          value: Backlight brightness percentage 0~100.
        """
        cmd = 'ectool pwmsetkblight %d' % value
        self.faft_client.system.run_shell_command(cmd)

    def check_keycode(self):
        """Check that lid open/close do not send power button keycode.

        Returns:
          True if no power button keycode is captured. Otherwise, False.
        """
        # Don't check the keycode if we don't have a keyboard.
        if not self.check_ec_capability(['keyboard'], suppress_warning=True):
            return True

        self._open_lid()
        self.delayed_close_lid()
        if self.faft_client.system.check_keys([]) < 0:
            return False
        self.delayed_open_lid()
        if self.faft_client.system.check_keys([]) < 0:
            return False
        return True

    def check_backlight(self):
        """Check if lid open/close controls keyboard backlight as expected.

        Returns:
          True if keyboard backlight is turned off when lid close and on when
           lid open.
        """
        if not self.check_ec_capability(['kblight'], suppress_warning=True):
            return True
        ok = True
        original_value = self._get_keyboard_backlight()
        self._set_keyboard_backlight(100)

        self._close_lid()
        if self._get_keyboard_backlight() != 0:
            logging.error("Keyboard backlight still on when lid close.")
            ok = False
        self._open_lid()
        if self._get_keyboard_backlight() == 0:
            logging.error("Keyboard backlight still off when lid open.")
            ok = False

        self._set_keyboard_backlight(original_value)
        return ok

    def check_keycode_and_backlight(self):
        """
        Disable powerd to prevent DUT shutting down during test. Then check
        if lid switch event controls keycode and backlight as we expected.
        """
        ok = True
        logging.info("Disable use_lid in powerd")
        self.faft_client.system.run_shell_command(
                "mkdir -p /tmp/power_manager && "
                "echo 0 > /tmp/power_manager/use_lid && "
                "mount --bind /tmp/power_manager /var/lib/power_manager && "
                "restart powerd")
        if not self.check_keycode():
            logging.error("check_keycode failed.")
            ok = False
        if not self.check_backlight():
            logging.error("check_backlight failed.")
            ok = False
        logging.info("Restarting powerd")
        self.faft_client.system.run_shell_command(
                'umount /var/lib/power_manager && restart powerd')
        return ok

    def run_once(self):
        """Runs a single iteration of the test."""
        if not self.check_ec_capability(['lid']):
            raise error.TestNAError("Nothing needs to be tested on this device")

        logging.info("Shut down and then wake up DUT after a delay.")
        self.shutdown_and_wake(shutdown_func=self.shutdown_cmd,
                               wake_func=self.delayed_wake)

        logging.info("Shut down and then wake up DUT immediately.")
        self.shutdown_and_wake(shutdown_func=self.shutdown_cmd,
                               wake_func=self.immediate_wake)

        logging.info("Close and then open the lid when not logged in.")
        self.shutdown_and_wake(shutdown_func=self._close_lid,
                               wake_func=self.immediate_wake)

        logging.info("Check keycode and backlight.")
        self.check_state(self.check_keycode_and_backlight)
