# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import print_function

import difflib
import logging
import math
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import cr50_utils, tpm_utils
from autotest_lib.server import autotest
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_Cr50DeepSleepStress(FirmwareTest):
    """Verify Cr50 deep sleep after running power_SuspendStress.

    Cr50 should enter deep sleep every suspend. Verify that by checking the
    idle deep sleep count.

    @param suspend_count: The number of times to reboot or suspend the device.
    @param reset_type: a str with the cycle type: 'freeze', 'mem', or 'reboot'
    """
    version = 1

    SLEEP_DELAY = 20
    MIN_RESUME = 15
    MIN_SUSPEND = 15
    # Initialize the FWMP with a non-zero value. Use 100, because it's an
    # unused flag and it wont do anything like lock out dev mode or ccd.
    FWMP_FLAGS = '0x100'
    # The deep sleep count may not exactly match the suspend count. This is the
    # ratio of difference the test tolerates. If the difference/total suspend
    # count is greater than this ratio, fail the test.
    TOLERATED_ERROR = 0.05

    def initialize(self, host, cmdline_args, suspend_count, reset_type):
        """Make sure the test is running with access to the Cr50 console"""
        self.host = host
        super(firmware_Cr50DeepSleepStress, self).initialize(host, cmdline_args)
        if not hasattr(self, 'cr50'):
            raise error.TestNAError('Test can only be run on devices with '
                                    'access to the Cr50 console')

        if self.servo.main_device_is_ccd():
            raise error.TestNAError('deep sleep tests can only be run with a '
                                    'servo flex')

        # Reset the device
        self.host.reset_via_servo()

        # Save the original version, so we can make sure Cr50 doesn't rollback.
        self.original_cr50_version = self.cr50.get_active_version_info()
        self._suspend_diff = 0

        # TODO(b/218492933) : find better way to disable rddkeepalive
        # Disable rddkeepalive, so the test can disable ccd.
        self.cr50.send_command('ccd testlab open')
        self.cr50.send_command('rddkeepalive disable')
        # Lock cr50 so the console will be restricted
        self.cr50.set_ccd_level('lock')

    def cleanup(self):
        """Clear the fwmp."""
        try:
            self.clear_fwmp()
        finally:
            super(firmware_Cr50DeepSleepStress, self).cleanup()


    def create_fwmp(self):
        """Create the FWMP."""
        self.clear_fwmp()

        # Clear the TPM owner, so we can set the fwmp.
        tpm_utils.ClearTPMOwnerRequest(self.host, wait_for_ready=True)

        logging.info('Setting FWMP flags to %s', self.FWMP_FLAGS)
        autotest.Autotest(self.host).run_test('firmware_SetFWMP',
                flags=self.FWMP_FLAGS, fwmp_cleared=True,
                check_client_result=True)

        if self.fwmp_is_cleared():
            raise error.TestError('Unable to create the FWMP')


    def check_fwmp(self):
        """Returns an error message if the fwmp doesn't exist."""
        if self.fwmp_is_cleared():
            return 'FWMP was lost during test'
        logging.info('No issues detected with the FWMP')


    def check_cr50_version(self, expected_ver):
        """Return an error message if the version changed running the test."""
        version = self.cr50.get_active_version_info()
        logging.info('running %s', version)

        if version != expected_ver:
            return 'version changed from %s to %s' % (expected_ver, version)


    def run_reboots(self, suspend_count):
        """Reboot the device the requested number of times

        @param suspend_count: the number of times to reboot the device.
        """
        cr50_dev_mode = self.cr50.in_dev_mode()
        # Disable CCD so Cr50 can enter deep sleep
        self.cr50.ccd_disable()
        self.cr50.clear_deep_sleep_count()
        rv = self.check_cr50_deep_sleep(0)
        if rv:
            raise error.TestError('Issue setting up test %s' % rv)
        errors = []

        for i in range(suspend_count):
            if not self._dut_is_responsive():
                raise error.TestFail('Unable to ssh into DUT after %d resets' %
                                     i)
            self.host.run('ls /dev/tpm0')
            # Power off the device
            self.set_ap_off_power_mode('shutdown')

            time.sleep(self.MIN_SUSPEND)

            # Power on the device
            self.servo.power_normal_press()
            time.sleep(self.MIN_RESUME)

            rv = self.check_cr50_deep_sleep(i + 1)
            if rv:
                errors.append(rv)
            # Make sure the device didn't boot into a different mode.
            if self.cr50.in_dev_mode() != cr50_dev_mode:
                errors.append('Switched out of %s mode' %
                              ('dev' if cr50_dev_mode else 'normal'))
            if errors:
                msg = 'Reboot %d failed (%s)' % (i, ' and '.join(errors))
                raise error.TestFail(msg)


    def _dut_is_responsive(self):
        """Returns True if the DUT eventually responds"""
        return self.host.ping_wait_up(180)


    def wait_for_client_after_changing_ccd(self, enable):
        """Change CCD and wait for client.

        @param enable: True to enable ccd. False to disable it.
        @returns an error message
        """
        start_msg = ('' if self._dut_is_responsive() else
                     'DUT unresponsive after suspend/resume')
        logging.info('SSH state afters suspend resume %r', start_msg or 'ok')
        if enable:
            self.cr50.ccd_enable()
        else:
            self.cr50.ccd_disable()
        # power suspend stress needs to ssh into the DUT. If ethernet goes
        # down, raise a test error, so we can tell the difference between
        # dts ethernet issues and the dut going down during the suspend stress.
        if self._dut_is_responsive():
            return
        msg = 'DUT is not pingable after %sabling ccd' % ('en' if enable else
                                                          'dis')
        logging.info(msg)

        # TODO(b/135147658): Raise an error once CCD disable is fixed.
        logging.info('Resetting DUT')
        self.host.reset_via_servo()

        is_sshable = self._dut_is_responsive()

        rv = start_msg or ('' if is_sshable else msg)
        logging.info('ssh state: %r', rv or 'ok')
        return rv


    def run_suspend_resume(self, suspend_count, suspend_type):
        """Suspend the device the requested number of times

        @param suspend_count: the number of times to suspend the device.
        @param suspend_type: the type of suspend to issue("mem" or "freeze")
        """
        # Disable CCD so Cr50 can enter deep sleep
        rv = self.wait_for_client_after_changing_ccd(False)
        if rv:
            raise error.TestFail('Network connection issue %s' % rv)
        self.cr50.clear_deep_sleep_count()
        rv = self.check_cr50_deep_sleep(0)
        if rv:
            raise error.TestError('Issue setting up test %s' % rv)
        client_at = autotest.Autotest(self.host)
        # Duration is set to 0, because it is required but unused when
        # iterations is given.
        client_at.run_test('power_SuspendStress',
                           tag='idle',
                           duration=0,
                           min_suspend=self.MIN_SUSPEND,
                           min_resume=self.MIN_RESUME,
                           check_connection=False,
                           suspend_iterations=suspend_count,
                           suspend_state=suspend_type,
                           check_client_result=True)


    def check_cr50_deep_sleep(self, suspend_count):
        """Verify Cr50 has entered deep sleep the correct number of times.

        Also print ccdstate and sleepmask output to get some basic information
        about the Cr50 state.
        - sleepmask will show what may be preventing Cr50 from entering sleep.
        - ccdstate will show what Cr50 thinks the AP state is. If the AP is 'on'
          Cr50 won't enter deep sleep.
        All of these functions log the state, so no need to log the return
        values.

        @param suspend_count: The number of suspends.
        @returns a message describing errors found in the state
        """
        exp_count = suspend_count if self._enters_deep_sleep else 0
        act_count = self.cr50.get_deep_sleep_count()
        logging.info('suspend %d: deep sleep count exp %d got %d',
                     suspend_count, exp_count, act_count)

        # Cr50 sometimes misses a suspend. Don't fail if the mismatch is within
        # the tolerated difference.
        tolerated_diff = math.ceil(exp_count * self.TOLERATED_ERROR)
        act_diff = exp_count - act_count
        logging.debug('suspend %d: tolerated diff %d got %d', suspend_count,
                      tolerated_diff, act_diff)
        if act_diff != self._suspend_diff:
            logging.warning('suspend %d: mismatch changed from %d to %d',
                            suspend_count, self._suspend_diff, act_diff)
            self._suspend_diff = act_diff

        self.cr50.get_sleepmask()
        self.cr50.get_ccdstate()
        hibernate = self.cr50.was_reset('RESET_FLAG_HIBERNATE')

        errors = []
        if exp_count and not hibernate:
            errors.append('reset during suspend')

        # Use the absolute value, because Cr50 shouldn't suspend more or less
        # than expected.
        if abs(act_diff) > tolerated_diff:
            errors.append('count mismatch expected %d got %d' % (exp_count,
                                                                 act_count))
        return ', '.join(errors) if errors else None


    def check_flog_output(self, original_flog):
        """Check for new flog messages.

        @param original_flog: the original flog output.
        @returns an error message with the flog difference, if there are new
                 entries.
        """
        new_flog = cr50_utils.DumpFlog(self.host).strip()
        logging.info('New FLOG output:\n%s', new_flog)
        diff = difflib.unified_diff(original_flog.splitlines(),
                                    new_flog.splitlines())
        line_diff = '\n'.join(diff)
        if line_diff:
            logging.info('FLOG output:\n%s', line_diff)
            return 'New Flog messages (%s)' % ','.join(diff)
        else:
            logging.info('No new FLOG output')


    def run_once(self, host, suspend_count, reset_type):
        """Verify deep sleep after suspending for the given number of cycles

        The test either suspends to s0i3/s3 or reboots the device depending on
        reset_type. There are three valid reset types: freeze, mem, and reboot.
        The test will make sure that the device is off or in s0i3/s3 long enough
        to ensure Cr50 should be able to enter the corresponding suspend state.
        At the end of the test, it checks that Cr50 entered the suspend state
        the same number of times the DUT suspended.

        @param host: the host object representing the DUT.
        @param suspend_count: The number of cycles to suspend or reboot the
                device.
        @param reset_type: a str with the cycle type: 'freeze', 'mem' or
                'reboot'
        """
        if reset_type not in ['reboot', 'freeze', 'mem']:
            raise error.TestNAError('Invalid reset_type. Use "freeze", "mem" '
                                    'or "reboot"')
        if self.MIN_SUSPEND + self.MIN_RESUME < self.SLEEP_DELAY:
            logging.info('Minimum suspend-resume cycle is %ds. This is '
                         'shorter than the Cr50 idle timeout. Cr50 may not '
                         'enter deep sleep every cycle',
                         self.MIN_SUSPEND + self.MIN_RESUME)
        if not suspend_count:
            raise error.TestFail('Need to provide non-zero suspend_count')
        original_flog = cr50_utils.DumpFlog(self.host).strip()
        logging.debug('Initial FLOG output:\n%s', original_flog)

        suspend_type = reset_type

        # x86 devices should suspend once per reset. ARM will only suspend
        # if the device enters s5.
        if reset_type == 'reboot':
            self._enters_deep_sleep = True
        else:
            is_arm = self.check_ec_capability(['arm'], suppress_warning=True)

            # Check if the device supports S0ix.
            self.s0ix_supported = not self.host.run(
                    'check_powerd_config --suspend_to_idle',
                    ignore_status=True).exit_status

            # Check if the device supports S3.
            self.s3_supported = not self.host.run(
                    'grep -q deep /sys/power/mem_sleep',
                    ignore_status=True).exit_status

            if not self.s0ix_supported and not self.s3_supported:
                raise error.TestError(
                        'S3 and S0ix unsupported, can not run test')

            if not self.s0ix_supported and \
               self.check_cr50_capability(['deep_sleep_in_s0i3']):
                raise error.TestError(
                        'Invalid configuration, S0ix not supported, but '
                        'deep_sleep_in_s0i3 is true')

            if self.check_cr50_capability(['deep_sleep_in_s0i3']) and \
               self.s0ix_supported and not self.s3_supported:
                logging.info('Switching suspend type from "mem" to "freeze" '
                             'to support s0ix(S3 unsupported)')
                suspend_type = 'freeze'

            # Check if the Cr50 enters deep sleep on this device.
            # This variable is used to determine error checks to be performed
            # at the end of testing(Suspend/Resume count vs Cr50 Deep Sleep)
            # Cr50 does not deep sleep on ARM
            # Cr50 does deep sleep in S3
            # Cr50 will only deep sleep in S0i3 on select systems.
            self._enters_deep_sleep = not is_arm and \
                ((suspend_type != 'freeze' or \
                self.check_cr50_capability(['deep_sleep_in_s0i3'])))

        self.create_fwmp()

        main_error = None
        try:
            if reset_type == 'reboot':
                self.run_reboots(suspend_count)
            elif reset_type == 'mem' or reset_type == 'freeze':
                self.run_suspend_resume(suspend_count, suspend_type)
            else:
                raise error.TestError('Test can only be run with reset types:'
                                      'reboot, mem, or freeze')
        except Exception as e:
            main_error = e

        errors = []
        # Autotest has some stages in between run_once and cleanup that may
        # be run if the test succeeds. Do this here to make sure this is
        # always run immediately after the suspend/resume cycles.
        # Collect logs for debugging
        # Console information
        self.cr50.dump_nvmem()
        rv = self.check_cr50_deep_sleep(suspend_count)
        if rv:
            errors.append(rv)
        rv = self.check_cr50_version(self.original_cr50_version)
        if rv:
            errors.append(rv)
        # Reenable CCD. Reestablish network connection.
        rv = self.wait_for_client_after_changing_ccd(True)
        if rv:
            errors.append(rv)
        # Information that requires ssh
        rv = self.check_fwmp()
        if rv:
            errors.append(rv)
        rv = self.check_flog_output(original_flog)
        if rv:
            errors.append(rv)
        secondary_error = 'Suspend issues: %s' % ', '.join(errors)
        if main_error:
            logging.info(secondary_error)
            raise main_error
        if errors:
            raise error.TestFail(secondary_error)
