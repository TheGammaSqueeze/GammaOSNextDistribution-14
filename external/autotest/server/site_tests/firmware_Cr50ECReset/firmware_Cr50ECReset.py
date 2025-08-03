# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import print_function

import logging
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.cr50_test import Cr50Test
from autotest_lib.server.cros.servo import servo


class firmware_Cr50ECReset(Cr50Test):
    """Make sure 'cr50 ecrst' works as intended

    EC_RST_L needs to be able to wake the EC from hibernate and hold the EC in
    reset. This test verifies the hardware works as intended
    """
    version = 1

    # Delays used by the test. Time is given in seconds
    # Used to wait long enough for the EC to enter/resume from hibernate
    EC_SETTLE_TIME = 10
    RELEASE_RESET_DELAY = 3
    SHORT_PULSE = 1


    def initialize(self, host, cmdline_args, full_args):
        super(firmware_Cr50ECReset, self).initialize(host, cmdline_args,
                                                     full_args)
        if not self.faft_config.gsc_can_wake_ec_with_reset:
            raise error.TestNAError("This DUT has a hardware limitation that "
                                    "prevents cr50 from waking the EC with "
                                    "EC_RST_L.")

        # TODO(b/186535695): EC hibernate puts cr50 into reset, so the test
        # can't verify cr50 behavior while the EC is hibernate.
        if 'c2d2' in self.servo.get_servo_type():
            raise error.TestNAError('Cannot run test with c2d2')

        # Don't bother if there is no Chrome EC or if EC hibernate doesn't work.
        if not self.check_ec_capability():
            raise error.TestNAError("Nothing needs to be tested on this device")

        # Verify the EC can wake from hibernate with a power button press. If it
        # can't, it's a device or servo issue.
        try:
            self.check_ec_hibernate()
        except error.TestError as e:
            raise error.TestNAError('Unsupported setup: %s' % str(e))


    def cleanup(self):
        """Make sure the EC is on, if there is a Chrome EC."""
        try:
            if self.check_ec_capability():
                self.guarantee_ec_is_up()
        except Exception as e:
            logging.info('Issue recovering EC: %r', e)
            logging.info('Trying power state reset')
            self.host.servo.get_power_state_controller().reset()
        finally:
            super(firmware_Cr50ECReset, self).cleanup()


    def ec_is_up(self):
        """If the console is responsive, then the EC is awake"""
        time.sleep(self.EC_SETTLE_TIME)
        try:
            self.ec.send_command_get_output('time', ['.*>'])
        except servo.UnresponsiveConsoleError as e:
            logging.info(str(e))
            return False
        else:
            return True


    def cold_reset(self, state):
        """Set cold reset"""
        self.servo.set('cold_reset', state)


    def power_button(self, state):
        """Press or release the power button"""
        self.servo.set('pwr_button', 'press' if state == 'on' else 'release')


    def cr50_ecrst(self, state):
        """Set ecrst on cr50"""
        self.cr50.send_command('ecrst ' + state)


    def wake_ec(self, wake_method):
        """Pulse the wake method to wake the EC

        Args:
            wake_method: a function that takes in 'on' or 'off' to control the
                         wake source.
        """
        wake_method('on')
        time.sleep(self.SHORT_PULSE)
        wake_method('off')


    def ec_hibernate(self):
        """Put the EC in hibernate"""
        self.ec.send_command('hibernate')
        if self.ec_is_up():
            raise error.TestError('Could not put the EC into hibernate')


    def guarantee_ec_is_up(self):
        """Make sure ec isn't held in reset. Use the power button to wake it

        The power button wakes the EC on all systems. Use that to wake the EC
        and make sure all versions of ecrst are released.
        """
        self.cold_reset('off')
        self.cr50_ecrst('off')
        time.sleep(self.RELEASE_RESET_DELAY)
        self.wake_ec(self.power_button)
        if not self.ec_is_up():
            raise error.TestError('Could not recover EC with power button')


    def can_wake_ec(self, wake_method):
        """Put the EC in hibernate and verify it can wake up with wake_method

        Args:
            wake_method: a function that takes in 'on' or 'off' to control the
                         wake source.
        Returns:
            True if wake_method can be used to wake the EC from hibernate
        """
        self.ec_hibernate()
        self.wake_ec(self.cold_reset)
        wake_successful = self.ec_is_up()
        self.guarantee_ec_is_up()
        return wake_successful


    def check_basic_ecrst(self):
        """Verify cr50 can hold the EC in reset"""
        self.cr50_ecrst('on')
        if self.ec_is_up():
            raise error.TestFail('Could not use cr50 ecrst to hold the EC in '
                                 'reset')
        # Verify cr50 can release the EC from reset
        self.cr50_ecrst('off')
        if not self.ec_is_up():
            raise error.TestFail('Could not release the EC from reset')
        self.guarantee_ec_is_up()

    def check_ec_hibernate(self):
        """Verify EC hibernate"""
        try:
            self.ec_hibernate()
        except error.TestError as e:
            if 'Could not put the EC into hibernate' in str(e):
                raise error.TestNAError("EC hibernate doesn't work.")
        finally:
            self.guarantee_ec_is_up()


    def run_once(self):
        """Make sure 'cr50 ecrst' works as intended."""
        failed_wake = []

        # Open cr50 so the test has access to ecrst
        self.fast_ccd_open(True)

        self.check_basic_ecrst()

        if not self.can_wake_ec(self.cr50_ecrst):
            failed_wake.append('cr50 ecrst')

        if not self.can_wake_ec(self.cold_reset):
            failed_wake.append('servo cold_reset')

        if failed_wake:
            raise error.TestFail('Failed to wake EC with %s' %
                                 ' and '.join(failed_wake))
