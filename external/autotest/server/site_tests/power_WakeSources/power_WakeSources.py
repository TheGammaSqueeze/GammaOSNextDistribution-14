# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import re
import time

from autotest_lib.client.common_lib import autotest_enum, error
from autotest_lib.server import test
from autotest_lib.server.cros import servo_keyboard_utils
from autotest_lib.server.cros.dark_resume_utils import DarkResumeUtils
from autotest_lib.server.cros.faft.utils.config import Config as FAFTConfig
from autotest_lib.server.cros.power import servo_charger
from autotest_lib.server.cros.servo import chrome_ec


# Possible states base can be forced into.
BASE_STATE = autotest_enum.AutotestEnum('ATTACH', 'DETACH', 'RESET')

# Possible states for tablet mode as defined in common/tablet_mode.c via
# crrev.com/c/1797370.
TABLET_MODE = autotest_enum.AutotestEnum('ON', 'OFF', 'RESET')

# List of wake sources expected to cause a full resume.
FULL_WAKE_SOURCES = [
    'PWR_BTN', 'LID_OPEN', 'BASE_ATTACH', 'BASE_DETACH', 'INTERNAL_KB',
    'USB_KB', 'TABLET_MODE_ON', 'TABLET_MODE_OFF'
]

# List of wake sources expected to cause a dark resume.
DARK_RESUME_SOURCES = ['RTC', 'AC_CONNECTED', 'AC_DISCONNECTED']

# Time in future after which RTC goes off when testing wake due to RTC alarm.
RTC_WAKE_SECS = 20

# Max time taken by the device to suspend. This includes the time powerd takes
# trigger the suspend after receiving the suspend request from autotest script.
SECS_FOR_SUSPENDING = 20

# Time to allow lid transition to take effect.
WAIT_TIME_LID_TRANSITION_SECS = 5

# Time to wait for the DUT to see USB keyboard after restting the Atmega USB
# emulator on Servo.
USB_PRESENT_DELAY = 1


class power_WakeSources(test.test):
    """
    Verify that wakes from input devices can trigger a full
    resume. Currently tests :
        1. power button
        2. lid open
        3. base attach
        4. base detach

    Also tests that dark resume wake sources work as expected, such as:
        1. RTC
        2. AC_CONNECTED
        3. AC_DISCONNECTED

    """
    version = 1

    def _after_resume(self, wake_source):
        """Cleanup to perform after resuming the device.

        @param wake_source: Wake source that has been tested.
        """
        if wake_source in ['BASE_ATTACH', 'BASE_DETACH']:
            self._force_base_state(BASE_STATE.RESET)
        elif wake_source in ['TABLET_MODE_ON', 'TABLET_MODE_OFF']:
            self._force_tablet_mode(TABLET_MODE.RESET)
        elif wake_source in ['AC_CONNECTED', 'AC_DISCONNECTED']:
            self._chg_manager.start_charging()

    def _before_suspend(self, wake_source):
        """Prep before suspend.

        @param wake_source: Wake source that is going to be tested.

        @return: Boolean, whether _before_suspend action is successful.
        """
        if wake_source == 'BASE_ATTACH':
            # Force detach before suspend so that attach won't be ignored.
            self._force_base_state(BASE_STATE.DETACH)
        elif wake_source == 'BASE_DETACH':
            # Force attach before suspend so that detach won't be ignored.
            self._force_base_state(BASE_STATE.ATTACH)
        elif wake_source == 'LID_OPEN':
            # Set the power policy for lid closed action to suspend.
            return self._host.run(
                'set_power_policy --lid_closed_action suspend',
                ignore_status=True).exit_status == 0
        elif wake_source == 'USB_KB':
            # Initialize USB keyboard.
            self._host.servo.set_nocheck('init_usb_keyboard', 'on')
        elif wake_source == 'TABLET_MODE_ON':
            self._force_tablet_mode(TABLET_MODE.OFF)
        elif wake_source == 'TABLET_MODE_OFF':
            self._force_tablet_mode(TABLET_MODE.ON)
        elif wake_source == 'AC_CONNECTED':
            self._chg_manager.stop_charging()
        elif wake_source == 'AC_DISCONNECTED':
            self._chg_manager.start_charging()
        return True

    def _force_tablet_mode(self, mode):
        """Send EC command to force the tablet mode.

        @param mode: mode to force. One of the |TABLET_MODE| enum.
        """
        ec_cmd = 'tabletmode '
        ec_arg = {
            TABLET_MODE.ON: 'on',
            TABLET_MODE.OFF: 'off',
            TABLET_MODE.RESET: 'r'
        }

        ec_cmd += ec_arg[mode]
        self._ec.send_command(ec_cmd)

    def _force_base_state(self, base_state):
        """Send EC command to force the |base_state|.

        @param base_state: State to force base to. One of |BASE_STATE| enum.
        """
        ec_cmd = 'basestate '
        ec_arg = {
            BASE_STATE.ATTACH: 'a',
            BASE_STATE.DETACH: 'd',
            BASE_STATE.RESET: 'r'
        }

        ec_cmd += ec_arg[base_state]
        self._ec.send_command(ec_cmd)

    def _x86_get_ec_wake_mask(self):
        # Check both the S0ix and S3 wake masks.
        try:
            s0ix_wake_mask = int(self._host.run(
                    'ectool hostevent get %d' %
                    chrome_ec.EC_HOST_EVENT_LAZY_WAKE_MASK_S0IX).stdout,
                                 base=16)
        except error.AutoservRunError as e:
            s0ix_wake_mask = 0
            logging.info(
                    '"ectool hostevent get" failed for s0ix wake mask with'
                    ' exception: %s', str(e))

        try:
            s3_wake_mask = int(self._host.run(
                    'ectool hostevent get %d' %
                    chrome_ec.EC_HOST_EVENT_LAZY_WAKE_MASK_S3).stdout,
                               base=16)
        except error.AutoservRunError as e:
            s3_wake_mask = 0
            logging.info(
                    '"ectool hostevent get" failed for s3 wake mask with'
                    ' exception: %s', str(e))

        return s0ix_wake_mask | s3_wake_mask

    def _arm_get_ec_wake_mask(self):
        try:
            s3_mkbpwakemask_out = self._host.run(
                    'ectool mkbpwakemask get hostevent').stdout
            match = re.match(r'MBKP hostevent wake mask: (0x[0-9A-Fa-f]+)',
                             s3_mkbpwakemask_out)
            if match:
                return int(match.group(1), base=16)
            else:
                logging.info(
                        '"ectool mkbpwakemask get hostevent" returned: %s',
                        s3_mkbpwakemask_out)
        except error.AutoservRunError as e:
            logging.info(
                    '"ectool mkbpwakemask get hostevent" failed with'
                    ' exception: %s', str(e))

        return 0

    def _is_valid_wake_source(self, wake_source):
        """Check if |wake_source| is valid for DUT.

        @param wake_source: wake source to verify.
        @return: False if |wake_source| is not valid for DUT, True otherwise
        """
        if wake_source in ['BASE_ATTACH', 'BASE_DETACH']:
            return self._ec.has_command('basestate')
        if wake_source in ['TABLET_MODE_ON', 'TABLET_MODE_OFF']:
            return self._ec.has_command('tabletmode')
        if wake_source == 'LID_OPEN':
            return self._dr_utils.host_has_lid()
        if wake_source == 'INTERNAL_KB':
            return self._faft_config.has_keyboard
        if wake_source == 'USB_KB':
            # Initialize USB keyboard.
            self._host.servo.set_nocheck('init_usb_keyboard', 'on')
            time.sleep(USB_PRESENT_DELAY)
            # Check if DUT can see a wake capable Atmel USB keyboard.
            if servo_keyboard_utils.is_servo_usb_keyboard_present(
                    self._host):
                if servo_keyboard_utils.is_servo_usb_wake_capable(
                        self._host):
                    return True
                else:
                    logging.warning(
                        'Atmel USB keyboard does not have wake capability.'
                        ' Please run firmware_FlashServoKeyboardMap Autotest '
                        'to update the Atmel firmware.')
                    return False
            else:
                logging.warning(
                    'DUT cannot see a Atmel USB keyboard. '
                    ' Please plug in USB C charger into Servo if using V4.')

                return False
        if wake_source in ['AC_CONNECTED', 'AC_DISCONNECTED']:
            arch = self._host.get_architecture()
            wake_mask = 0
            if not self._chg_manager:
                logging.warning(
                    'Unable to test AC connect/disconnect with this '
                    'servo setup')
                return False
            elif arch.startswith('x86'):
                wake_mask = self._x86_get_ec_wake_mask()
            elif arch.startswith('arm'):
                wake_mask = self._arm_get_ec_wake_mask()

            supported = False
            if wake_source == 'AC_CONNECTED':
                supported = wake_mask & chrome_ec.HOSTEVENT_AC_CONNECTED
            elif wake_source == 'AC_DISCONNECTED':
                supported = wake_mask & chrome_ec.HOSTEVENT_AC_DISCONNECTED

            if not supported:
                logging.info(
                        '%s not supported. Platforms launched in 2020 or before'
                        ' may not require it. Wake mask: 0x%x', wake_source,
                        wake_mask)
                return False

        return True

    def _test_wake(self, wake_source, full_wake):
        """Test if |wake_source| triggers a full resume.

        @param wake_source: wake source to test. One of |FULL_WAKE_SOURCES|.
        @return: True, if we are able to successfully test the |wake source|
            triggers a full wake.
        """
        is_success = True
        logging.info(
                'Testing wake by %s triggers a %s wake when dark resume is '
                'enabled.', wake_source, 'full' if full_wake else 'dark')
        if not self._before_suspend(wake_source):
            logging.error('Before suspend action failed for %s', wake_source)
            # Still run the _after_resume callback since we can do things like
            # stop charging.
            self._after_resume(wake_source)
            return False

        count_before = self._dr_utils.count_dark_resumes()
        self._dr_utils.suspend(SECS_FOR_SUSPENDING + RTC_WAKE_SECS)
        logging.info('DUT suspended! Waiting to resume...')
        # Wait at least |SECS_FOR_SUSPENDING| secs for the kernel to
        # fully suspend.
        time.sleep(SECS_FOR_SUSPENDING)
        self._trigger_wake(wake_source)

        # Wait until it would be unclear if the RTC or wake_source triggered the
        # wake.
        if not self._host.wait_up(timeout=RTC_WAKE_SECS - 1):
            logging.error(
                    'Device did not resume from suspend for %s.'
                    ' Waking system with power button then RTC.', wake_source)
            self._trigger_wake('PWR_BTN')
            self._after_resume(wake_source)
            if not self._host.is_up():
                raise error.TestFail(
                        'Device failed to wakeup from backup wake sources'
                        ' (power button and RTC).')

            return False

        count_after = self._dr_utils.count_dark_resumes()
        if full_wake:
            if count_before != count_after:
                logging.error('%s incorrectly caused a dark resume.',
                              wake_source)
                is_success = False
            elif is_success:
                logging.info('%s caused a full resume.', wake_source)
        else:
            if count_before == count_after:
                logging.error('%s incorrectly caused a full resume.',
                              wake_source)
                is_success = False
            elif is_success:
                logging.info('%s caused a dark resume.', wake_source)

        self._after_resume(wake_source)
        return is_success

    def _trigger_wake(self, wake_source):
        """Trigger wake using the given |wake_source|.

        @param wake_source : wake_source that is being tested.
            One of |FULL_WAKE_SOURCES|.
        """
        if wake_source == 'PWR_BTN':
            self._host.servo.power_short_press()
        elif wake_source == 'LID_OPEN':
            self._host.servo.lid_close()
            time.sleep(WAIT_TIME_LID_TRANSITION_SECS)
            self._host.servo.lid_open()
        elif wake_source == 'BASE_ATTACH':
            self._force_base_state(BASE_STATE.ATTACH)
        elif wake_source == 'BASE_DETACH':
            self._force_base_state(BASE_STATE.DETACH)
        elif wake_source == 'TABLET_MODE_ON':
            self._force_tablet_mode(TABLET_MODE.ON)
        elif wake_source == 'TABLET_MODE_OFF':
            self._force_tablet_mode(TABLET_MODE.OFF)
        elif wake_source == 'INTERNAL_KB':
            self._host.servo.ctrl_key()
        elif wake_source == 'USB_KB':
            self._host.servo.set_nocheck('usb_keyboard_enter_key', '10')
        elif wake_source == 'RTC':
            # The RTC will wake on its own. We just need to wait
            time.sleep(RTC_WAKE_SECS)
        elif wake_source == 'AC_CONNECTED':
            self._chg_manager.start_charging()
        elif wake_source == 'AC_DISCONNECTED':
            self._chg_manager.stop_charging()

    def cleanup(self):
        """cleanup."""
        self._dr_utils.stop_resuspend_on_dark_resume(False)
        self._dr_utils.teardown()

    def initialize(self, host):
        """Initialize wake sources tests.

        @param host: Host on which the test will be run.
        """
        self._host = host
        self._dr_utils = DarkResumeUtils(host)
        self._dr_utils.stop_resuspend_on_dark_resume()
        self._ec = chrome_ec.ChromeEC(self._host.servo)
        self._faft_config = FAFTConfig(self._host.get_platform())
        self._kstr = host.get_kernel_version()
        # TODO(b/168939843) : Look at implementing AC plug/unplug w/ non-PD RPMs
        # in the lab.
        try:
            self._chg_manager = servo_charger.ServoV4ChargeManager(
                host, host.servo)
        except error.TestNAError:
            logging.warning('Servo does not support AC switching.')
            self._chg_manager = None

    def run_once(self):
        """Body of the test."""

        test_ws = set(
            ws for ws in FULL_WAKE_SOURCES if self._is_valid_wake_source(ws))
        passed_ws = set(ws for ws in test_ws if self._test_wake(ws, True))
        failed_ws = test_ws.difference(passed_ws)
        skipped_ws = set(FULL_WAKE_SOURCES).difference(test_ws)

        test_dark_ws = set(ws for ws in DARK_RESUME_SOURCES
                           if self._is_valid_wake_source(ws))
        skipped_ws.update(set(DARK_RESUME_SOURCES).difference(test_dark_ws))
        for ws in test_dark_ws:
            if self._test_wake(ws, False):
                passed_ws.add(ws)
            else:
                failed_ws.add(ws)

        test_keyval = {}

        for ws in passed_ws:
            test_keyval.update({ws: 'PASS'})
        for ws in failed_ws:
            test_keyval.update({ws: 'FAIL'})
        for ws in skipped_ws:
            test_keyval.update({ws: 'SKIPPED'})
        self.write_test_keyval(test_keyval)

        if passed_ws:
            logging.info('[%s] woke the device as expected.',
                         ''.join(str(elem) + ', ' for elem in passed_ws))

        if skipped_ws:
            logging.info(
                '[%s] are not wake sources on this platform. '
                'Please test manually if not the case.',
                ''.join(str(elem) + ', ' for elem in skipped_ws))

        if failed_ws:
            raise error.TestFail(
                '[%s] wake sources did not behave as expected.' %
                (''.join(str(elem) + ', ' for elem in failed_ws)))
