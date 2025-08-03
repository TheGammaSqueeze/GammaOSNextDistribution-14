# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest
from autotest_lib.server.cros.power import servo_charger
from autotest_lib.server.cros.servo import servo


class firmware_ECWakeFromULP(FirmwareTest):
    """
    Servo based EC wake from ULP test.
    """
    version = 1

    # Retries allowed for reaching designed states.
    POWER_STATE_RETRY_COUNT = 10

    def initialize(self, host, cmdline_args):
        super(firmware_ECWakeFromULP, self).initialize(host, cmdline_args)
        self.setup_pdtester(min_batt_level=10)
        # Only run in normal mode
        self.switcher.setup_mode('normal')
        self.charge_manager = servo_charger.ServoV4ChargeManager(
                host, host.servo)
        # stop charging to test hibernate
        self.charge_manager.stop_charging()

    def cleanup(self):
        # The DUT might be still hibernated. Force the reboot.
        if not self.is_ec_console_responsive():
            logging.info('System is still hibernated; reboot.')
            self.switcher.simple_reboot('cold', sync_before_boot=False)

        if not self.wait_power_state(self.POWER_STATE_S0,
                                     self.POWER_STATE_RETRY_COUNT):
            logging.info('System is S5/G3; press pwrbtn to boot to S0.')
            self.servo.power_short_press()

        # Restore the lid_open switch in case the test failed in the middle.
        if self.check_ec_capability(['lid']):
            self.servo.set('lid_open', 'yes')

        self.charge_manager.start_charging()

        super(firmware_ECWakeFromULP, self).cleanup()

    def hibernate_and_wake(self, host, wake_func, wake_state):
        """Shutdown to G3/S5, hibernate EC, and then wake by power button."""
        self.run_shutdown_cmd()
        if not self.wait_power_state(self.POWER_STATE_G3,
                                     self.POWER_STATE_RETRY_COUNT):
            raise error.TestFail('Platform failed to reach G3 state.')

        self.ec.send_command('hibernate')
        time.sleep(self.WAKE_DELAY)

        if self.is_ec_console_responsive():
            raise error.TestFail('The DUT is not in hibernate mode.')
        else:
            logging.info('Hibernated. EC console in not responsive. ')

        # wake system
        wake_func()
        if not self.wait_power_state(wake_state, self.POWER_STATE_RETRY_COUNT):
            raise error.TestFail('Platform failed to reach %s state.' %
                                 wake_state)
        if wake_state == self.POWER_STATE_S0:
            self.switcher.wait_for_client()

    def is_ec_console_responsive(self):
        """Test if EC console is responsive."""
        try:
            self.ec.send_command_get_output('help', ['.*>'])
            return True
        except servo.UnresponsiveConsoleError:
            return False

    def wake_by_lid_switch(self):
        """Wake up the device by lid switch."""
        self.servo.set('lid_open', 'no')
        time.sleep(self.LID_DELAY)
        self.servo.set('lid_open', 'yes')

    def run_once(self, host):
        """Runs a single iteration of the test."""
        if not self.check_ec_capability():
            raise error.TestNAError(
                    "Nothing needs to be tested on this device")

        if self.servo.main_device_is_ccd():
            raise error.TestNAError(
                    'With CCD, we can\'t wake up the DUT from '
                    'hibernate by power button. Skip hibernate '
                    'test.')
        elif not self.faft_config.hibernate:
            raise error.TestNAError('The device does not support hibernate. '
                                    'Skip hibernate test.')
        elif not self._client.has_battery():
            raise error.TestNAError(
                    'The device claims to have hibernate support, but does not '
                    'have a battery. It probably does not actually have '
                    'hibernate support, edit the device.json file in '
                    'fw-testing-configs. Skip hibernate test.')

        # Test hibernate and wake by power button
        wake_src = 'power button'
        logging.info('EC hibernate and wake by power button.')
        self.hibernate_and_wake(host, self.servo.power_short_press,
                                self.POWER_STATE_S0)

        # Test hibernate and wake by lid switch
        wake_src = 'lid switch'
        if not self.check_ec_capability(['lid']):
            logging.info(
                    'The device has no lid. '
                    'Skip testing hibernate/wake by %s.', wake_src)
        elif 'c2d2' in self.servo.get_servo_type():
            logging.info('The servo is c2d2. We can\'t wake up the DUT from '
                         'hibernate by lid open. Skip hibernate test')
        else:
            logging.info('Hibernate and wake by %s.', wake_src)
            self.hibernate_and_wake(host, self.wake_by_lid_switch,
                                    self.POWER_STATE_S0)

        # Test hibernate and wake by AC on
        wake_src = 'AC on'
        self.charge_manager.stop_charging()
        logging.info('Hibernate and wake by %s.', wake_src)
        if self.faft_config.ac_on_can_wake_ap_from_ulp:
            logging.info('AC on event can wake AP from ULP.')
            wake_state = self.POWER_STATE_S0
        else:
            logging.info('AC on event cannot wake AP from ULP.')
            wake_state = self.POWER_STATE_G3
        self.hibernate_and_wake(host, self.charge_manager.start_charging,
                                wake_state)

        if not self.faft_config.ac_on_can_wake_ap_from_ulp:
            # Put AP back to S0
            self.servo.power_short_press()
