# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import pprint
import time
import re

from autotest_lib.client.common_lib import error
from autotest_lib.server import autotest
from autotest_lib.server.cros.faft.cr50_test import Cr50Test


class firmware_Cr50DeviceState(Cr50Test):
    """Verify Cr50 tracks the EC and AP state correctly.

    Put the device through S0, S0ix, S3, and G3. Cr50 responds to these state
    changes by enabling/disabling uart and changing its suspend type. Verify
    that none of these cause any interrupt storms on Cr50. Make sure that there
    aren't any interrupt storms and that Cr50 enters regular or deep sleep a
    reasonable amount of times.
    """
    version = 1

    DEEP_SLEEP_STEP_SUFFIX = ' Num Deep Sleep Steps'

    KEY_CMD_END_TIME = -4
    # Use negative numbers to keep track of counts not in the IRQ list.
    KEY_DEEP_SLEEP = -3
    KEY_TIME = -2
    KEY_RESET = -1
    KEY_REGULAR_SLEEP = 112
    INT_NAME = {
        KEY_RESET  : 'Reset Count',
        KEY_DEEP_SLEEP  : 'Deep Sleep Count',
        KEY_TIME  : 'Cr50 Time',
        4 : 'HOST_CMD_DONE',
        81  : 'GPIO0',
        98  : 'GPIO1',
        103 : 'I2CS WRITE',
        KEY_REGULAR_SLEEP : 'PMU WAKEUP',
        113 : 'AC present FED',
        114 : 'AC present RED',
        124 : 'RBOX_INTR_PWRB',
        130 : 'SPS CS deassert',
        138 : 'SPS RXFIFO LVL',
        159 : 'SPS RXFIFO overflow',
        160 : 'EVENT TIMER',
        174 : 'CR50_RX_SERVO_TX',
        177 : 'CR50_TX_SERVO_RX',
        181 : 'AP_TX_CR50_RX',
        184 : 'AP_RX_CR50_TX',
        188 : 'EC_TX_CR50_RX',
        191 : 'EC_RX_CR50_TX',
        193 : 'USB',
    }
    IGNORED_KEYS = [KEY_CMD_END_TIME]
    SLEEP_KEYS = [ KEY_REGULAR_SLEEP, KEY_DEEP_SLEEP ]
    # USB, AP UART, and EC UART should be disabled if ccd is disabled.
    CCD_KEYS = [ 181, 184, 188, 191, 193 ]

    # Cr50 won't enable any form of sleep until it has been up for 20 seconds.
    SLEEP_DELAY = 20
    # The time in seconds to wait in each state. Wait one minute so it's long
    # enough for cr50 to settle into whatever state. 60 seconds is also long
    # enough that cr50 has enough time to enter deep sleep twice, so we can
    # catch extra wakeups.
    SLEEP_TIME = 60
    SHORT_WAIT = 5
    CONSERVATIVE_WAIT_TIME = SLEEP_TIME * 2
    # Cr50 should wake up twice per second while in regular sleep
    SLEEP_RATE = 2

    DEEP_SLEEP_MAX = 2
    ARM = 'ARM '
    # If there are over 100,000 interrupts, it is an interrupt storm.
    DEFAULT_COUNTS = [0, 100000]
    # A dictionary of ok count values for each irq that shouldn't follow the
    # DEFAULT_COUNTS range.
    EXPECTED_IRQ_COUNT_RANGE = {
        KEY_RESET : [0, 0],
        KEY_DEEP_SLEEP : [0, DEEP_SLEEP_MAX],
        KEY_TIME : [0, CONSERVATIVE_WAIT_TIME],
        'S0ix' + DEEP_SLEEP_STEP_SUFFIX : [0, 0],
        # Cr50 may enter deep sleep an extra time, because of how the test
        # collects taskinfo counts. Just verify that it does enter deep sleep
        'S3' + DEEP_SLEEP_STEP_SUFFIX : [1, 2],
        'G3' + DEEP_SLEEP_STEP_SUFFIX : [1, 2],
        # ARM devices don't enter deep sleep in S3
        ARM + 'S3' + DEEP_SLEEP_STEP_SUFFIX : [0, 0],
        ARM + 'G3' + DEEP_SLEEP_STEP_SUFFIX : [1, 2],
        # Regular sleep is calculated based on the cr50 time
    }

    # Each line relevant taskinfo output should be 13 characters long with only
    # digits or spaces. Use this information to make sure every taskinfo command
    # gets the full relevant output. There are 4 characters for the irq number
    # and 9 for the count.
    GET_TASKINFO = ['IRQ counts by type:\s+(([\d ]{13}\r\n)+)Service calls']

    START = ''
    INCREASE = '+'
    DS_RESUME = 'DS'

    TMP_POWER_MANAGER_PATH = '/tmp/power_manager'
    POWER_MANAGER_PATH = '/var/lib/power_manager'
    # TODO(mruthven): remove ec chan restriction once soraka stops spamming host
    # command output. The extra activity makes it look like a interrupt storm on
    # the EC uart.
    CHAN_ALL = 0xffffffff
    CHAN_EVENTS = 0x20
    CHAN_ACPI = 0x400
    CHAN_HOSTCMD = 0x80
    CHAN_USBCHARGE = 0x200000
    CHAN_RESTRICTED = CHAN_ALL ^ (CHAN_EVENTS | CHAN_ACPI | CHAN_HOSTCMD
                                  | CHAN_USBCHARGE)


    def initialize(self, host, cmdline_args, full_args):
        super(firmware_Cr50DeviceState, self).initialize(host, cmdline_args,
                                                         full_args)
        # Don't bother if there is no Chrome EC or if EC hibernate doesn't work.
        if not self.check_ec_capability():
            raise error.TestNAError("Nothing needs to be tested on this device")

        # If the TPM is reset in S0i3, the CR50 may enter deep sleep during S0i3.
        # Cr50 may enter deep sleep an extra time, because of how the test
        # collects taskinfo counts. So the range is set conservatively to 0-2.
        if self.check_cr50_capability(['deep_sleep_in_s0i3']):
            irq_s0ix_deep_sleep_key = 'S0ix' + self.DEEP_SLEEP_STEP_SUFFIX
            self.EXPECTED_IRQ_COUNT_RANGE[irq_s0ix_deep_sleep_key] = [0, 2]

    def mount_power_config(self):
        """Mounts power_manager settings to tmp,
        ensuring that any changes do not persist across reboots
        """
        self.faft_client.system.run_shell_command(
                'mkdir -p %s && \
            echo 0 > %s/suspend_to_idle && \
            mount --bind %s %s && \
            restart powerd' %
                (self.TMP_POWER_MANAGER_PATH, self.TMP_POWER_MANAGER_PATH,
                 self.TMP_POWER_MANAGER_PATH, self.POWER_MANAGER_PATH), True)

    def umount_power_config(self):
        """Unmounts power_manager settings"""
        self.faft_client.system.run_shell_command(
                'umount %s && restart powerd' % self.POWER_MANAGER_PATH, True)

    def set_suspend_to_idle(self, value):
        """Set suspend_to_idle by writing to power_manager settings"""
        # Suspend to idle expects 0/1 so %d is used
        self.faft_client.system.run_shell_command(
                'echo %d > %s/suspend_to_idle' %
                (value, self.TMP_POWER_MANAGER_PATH), True)


    def log_sleep_debug_information(self):
        """Log some information used for debugging sleep issues"""
        logging.debug(
            self.cr50.send_command_retry_get_output('sleepmask',
                                                    ['sleepmask.*>'],
                                                    safe=True)[0])
        logging.debug(
            self.cr50.send_command_retry_get_output('sysinfo',
                                                    ['sysinfo.*>'],
                                                    safe=True)[0])


    def get_taskinfo_output(self):
        """Return a dict with the irq numbers as keys and counts as values"""
        output = self.cr50.send_command_retry_get_output('taskinfo',
            self.GET_TASKINFO, safe=True, retries=10)[0][1].strip()
        logging.debug(output)
        return output


    def get_irq_counts(self):
        """Return a dict with the irq numbers as keys and counts as values"""
        irq_counts = { self.KEY_REGULAR_SLEEP : 0 }
        # Running all of these commands may take a while. Track how much time
        # commands are running, so we can offset the cr50 time to set sleep
        # expectations
        start_cmd_time = int(self.cr50.gettime())
        irq_counts[self.KEY_TIME] = start_cmd_time

        output = self.get_taskinfo_output()
        irq_list = re.findall('\d+\s+\d+\r', output)
        # Make sure the regular sleep irq is in the dictionary, even if cr50
        # hasn't seen any interrupts. It's important the test sees that there's
        # never an interrupt.
        for irq_info in irq_list:
            logging.debug(irq_info)
            num, count = irq_info.split()
            irq_counts[int(num)] = int(count)
        irq_counts[self.KEY_RESET] = int(self.cr50.get_reset_count())
        irq_counts[self.KEY_DEEP_SLEEP] = int(self.cr50.get_deep_sleep_count())
        # Log some information, so we can debug issues with sleep.
        self.log_sleep_debug_information()
        # Track when the commands end, so the test can ignore the time spent
        # running these console commands.
        end_cmd_time = int(self.cr50.gettime())
        irq_counts[self.KEY_CMD_END_TIME] = end_cmd_time
        logging.info('Commands finished in %d seconds',
                     end_cmd_time - start_cmd_time)
        return irq_counts


    def get_expected_count(self, irq_key, cr50_time, idle):
        """Get the expected irq increase for the given irq and state

        Args:
            irq_key: the irq int
            cr50_time: the cr50 time in seconds
            idle: Cr50 should enter regular sleep while the device is idle.

        Returns:
            A list with the expected irq count range [min, max]
        """
        # CCD will prevent sleep
        if self.ccd_enabled and (irq_key in self.SLEEP_KEYS or
            self.DEEP_SLEEP_STEP_SUFFIX in str(irq_key)):
            return [0, 0]
        if irq_key == self.KEY_REGULAR_SLEEP:
            # If cr50_time is really low, we probably woke cr50 up using
            # taskinfo, which would be a pmu wakeup.
            if cr50_time < 5:
                return [0, 1]
            # Only enforce the minimum regular sleep count if the device is
            # idle. Cr50 may not enter regular sleep during power state
            # transitions.
            if idle:
                min_count = max(cr50_time - self.SLEEP_DELAY, 0)
            else:
                min_count = 0
            # Check that cr50 isn't continuously entering and exiting sleep.
            # The PMU wakeups should happen around twice a second. Depending
            # on TPM activity it may occur more often. Add 2 to the multiplier
            # to allow for extra wakeups. This is mostly to catch issues that
            # cause cr50 to wake up 100 times a second
            max_count = cr50_time * (self.SLEEP_RATE + 2)
            return [min_count, max_count]
        # If ccd is disabled, ccd irq counts should not increase.
        if not self.ccd_enabled and (irq_key in self.CCD_KEYS):
            return [0, 0]
        return self.EXPECTED_IRQ_COUNT_RANGE.get(irq_key, self.DEFAULT_COUNTS)


    def check_increase(self, irq_key, name, increase, expected_range):
        """Verify the irq count is within the expected range

        Args:
            irq_key: the irq int
            name: the irq name string
            increase: the irq count
            expected_range: A list with the valid irq count range [min, max]

        Returns:
            '' if increase is in the given range. If the increase isn't in the
            range, it returns an error message.
        """
        min_count, max_count = expected_range
        if min_count > increase or max_count < increase:
            err_msg = '%s %s: %s not in range %s' % (name, irq_key, increase,
                expected_range)
            return err_msg
        return ''


    def get_step_events(self):
        """Use the deep sleep counts to determine the step events"""
        ds_counts = self.get_irq_step_counts(self.KEY_DEEP_SLEEP)
        events = []
        for i, count in enumerate(ds_counts):
            if not i:
                events.append(self.START)
            elif count != ds_counts[i - 1]:
                # If the deep sleep count changed, Cr50 recovered deep sleep
                # and the irq counts are reset.
                events.append(self.DS_RESUME)
            else:
                events.append(self.INCREASE)
        return events


    def get_irq_step_counts(self, irq_key):
        """Get a list of the all of the step counts for the given irq"""
        return [ irq_dict.get(irq_key, 0) for irq_dict in self.steps ]


    def check_for_errors(self, state):
        """Check for unexpected IRQ counts at each step.

        Find the irq count errors and add them to run_errors.

        Args:
            state: The power state: S0, S0ix, S3, or G3.
        """
        num_steps = len(self.steps)
        # Get all of the deep sleep counts
        events = self.get_step_events()

        irq_list = list(self.irqs)
        irq_list.sort()

        # Pad the start of the step names, so the names align with each step
        # count.
        irq_diff = ['%24s|%s|' % ('', '|'.join(self.step_names))]
        step_errors = [ [] for i in range(num_steps) ]

        end_cmd_times = self.get_irq_step_counts(self.KEY_CMD_END_TIME)
        start_cmd_times = self.get_irq_step_counts(self.KEY_TIME)
        cr50_time_diff = []
        for i, start_time in enumerate(start_cmd_times):
            # The test collects a lot of information using console commands.
            # These take a long time to run and cr50 isn't idle. The idle
            # time is the time this step started minus the time the last set of
            # commands finished running.
            if events[i] == self.INCREASE:
                cr50_time_diff.append(start_time - end_cmd_times[i - 1])
            else:
                cr50_time_diff.append(start_time)

        # Go through each irq and update its info in the progress dict
        for irq_key in irq_list:
            if irq_key in self.IGNORED_KEYS:
                continue
            name = self.INT_NAME.get(irq_key, 'Unknown')
            # Print the IRQ name on the left of the column and the irq number
            # on the right.
            irq_progress_str = ['%-19s %3s' % (name, irq_key)]

            irq_counts = self.get_irq_step_counts(irq_key)
            for step, count in enumerate(irq_counts):
                event = events[step]

                # The deep sleep counts are not reset after deep sleep. Change
                # the event to INCREASE.
                if irq_key == self.KEY_DEEP_SLEEP and event == self.DS_RESUME:
                    event = self.INCREASE

                if event == self.INCREASE:
                    count -= irq_counts[step - 1]

                # Check that the count increase is within the expected value.
                if event != self.START:
                    step_name = self.step_names[step].strip()
                    # TODO(b/153891388): raise actual error once the servo
                    # character loss issue is fixed.
                    if count < 0:
                        raise error.TestNAError('%s test found negative %s '
                                                'count %r (likely due to servo '
                                                'dropping characters)' %
                                                (step, step_name, count))
                    expected_range = self.get_expected_count(irq_key,
                            cr50_time_diff[step], idle='idle' in step_name)

                    rv = self.check_increase(irq_key, name, count,
                            expected_range)
                    if rv:
                        logging.info('Unexpected count in %s test: %s %s',
                                     state, step_name, rv)
                        step_errors[step].append(rv)

                irq_progress_str.append(' %2s %8d' % (event, count))

            # Separate each step count with '|'. Add '|' to the start and end of
            # the line.
            irq_diff.append('|%s|' % '|'.join(irq_progress_str))

        errors = {}

        ds_key = self.ARM if self.is_arm else ''
        ds_key += state + self.DEEP_SLEEP_STEP_SUFFIX
        expected_range = self.get_expected_count(ds_key, 0, False)
        rv = self.check_increase(None, ds_key, events.count(self.DS_RESUME),
                expected_range)
        if rv:
            logging.info('Unexpected count for %s %s', state, rv)
            errors[ds_key] = rv
        for i, step_error in enumerate(step_errors):
            if step_error:
                logging.error('Step %d errors:\n%s', i,
                        pprint.pformat(step_error))
                step = '%s step %d %s' % (state, i, self.step_names[i].strip())
                errors[step] = step_error

        logging.info('DIFF %s IRQ Counts:\n%s', state, '\n'.join(irq_diff))
        if errors:
            logging.info('ERRORS %s IRQ Counts:\n%s', state,
                    pprint.pformat(errors))
            self.run_errors.update(errors)


    def ap_is_on_after_power_button_press(self):
        """Returns True if the AP is on after pressing the power button"""
        self.servo.power_normal_press()
        # Give the AP some time to turn on
        time.sleep(self.cr50.SHORT_WAIT)
        return self.cr50.ap_is_on()


    def trigger_s0(self):
        """Press the power button so the DUT will wake up."""
        if self.ap_is_on_after_power_button_press():
            return
        # Try a second time to see if the AP comes up.
        if not self.ap_is_on_after_power_button_press():
            raise error.TestError('Could not wake the AP using power button')
        logging.warning('Had to press power button twice to wake the AP')


    def enter_state(self, state):
        """Get the command to enter the power state"""
        block = True
        if state == 'S0':
            self.trigger_s0()
            # Suppress host command output, so it doesn't look like an interrupt
            # storm. Set it whenever the system enters S0 to ensure the setting
            # is restored if the EC enters hibernate.
            time.sleep(2)
            logging.info('Setting EC chan %x', self.CHAN_RESTRICTED)
            self.ec.send_command('chan 0x%x' % self.CHAN_RESTRICTED)
        else:
            if state == 'S0ix':
                self.set_suspend_to_idle(True)
                self.suspend()
            elif state == 'S3':
                self.set_suspend_to_idle(False)
                self.suspend()
            elif state == 'G3':
                self.faft_client.system.run_shell_command('poweroff', True)

        time.sleep(self.SHORT_WAIT)
        # check state transition
        if not self.wait_power_state(state, self.SHORT_WAIT):
            raise error.TestFail('Platform failed to reach %s state.' % state)


    def stage_irq_add(self, irq_dict, name=''):
        """Add the current irq counts to the stored dictionary of irq info"""
        self.steps.append(irq_dict)
        self.step_names.append(name.center(12))
        self.irqs.update(irq_dict.keys())
        logging.info('%s:\n%s', name, pprint.pformat(irq_dict))


    def reset_irq_counts(self):
        """Reset the test IRQ counts"""
        self.steps = []
        self.step_names = []
        self.irqs = set()


    def run_transition(self, state):
        """Enter the given power state and reenter s0

        Enter the power state and return to S0. Wait long enough to ensure cr50
        will enter sleep mode, so we can verify that as well.

        Args:
            state: the power state: S0ix, S3, or G3
        """
        self.reset_irq_counts()

        # Enter the given state
        self.enter_state(state)
        self.stage_irq_add(self.get_irq_counts(), 'entered %s' % state)

        logging.info('waiting %d seconds', self.SLEEP_TIME)
        time.sleep(self.SLEEP_TIME)
        # Nothing is really happening. Cr50 should basically be idle during
        # SLEEP_TIME.
        self.stage_irq_add(self.get_irq_counts(), 'idle in %s' % state)

        # Return to S0
        self.enter_state('S0')
        self.stage_irq_add(self.get_irq_counts(), 'entered S0')

        logging.info('waiting %d seconds', self.SLEEP_TIME)
        time.sleep(self.SLEEP_TIME)

        self.stage_irq_add(self.get_irq_counts(), 'idle in S0')


    def verify_state(self, state):
        """Verify cr50 behavior while running through the power state"""

        try:
            self.run_transition(state)
        finally:
            # reset the system to S0 no matter what happens
            self.trigger_s0()
            # Reenable EC chan output.
            time.sleep(2)
            logging.info('Setting EC chan %x', self.CHAN_ALL)
            self.ec.send_command('chan 0x%x' % self.CHAN_ALL)

        # Check that the progress of the irq counts seems reasonable
        self.check_for_errors(state)


    def is_arm_family(self):
        """Returns True if the DUT is an ARM device."""
        arch = self.host.run('arch').stdout.strip()
        return arch in ['aarch64', 'armv7l']


    def run_through_power_states(self):
        """Go through S0ix, S3, and G3. Verify there are no interrupt storms"""
        self._try_to_bring_dut_up()
        self.run_errors = {}
        self.ccd_str = 'ccd ' + ('enabled' if self.ccd_enabled else 'disabled')
        logging.info('Running through states with %s', self.ccd_str)

        self.cr50.get_ccdstate()
        if not self.cr50.get_sleepmask() and self.ccd_enabled:
            logging.info('Sleepmask is not keeping cr50 up with ccd enabled')
            self.all_errors[self.ccd_str] = 'usb is not active with ccd enabled'
            return

        # Login before entering S0ix so cr50 will be able to enter regular sleep
        client_at = autotest.Autotest(self.host)
        client_at.run_test('login_LoginSuccess')

        self.mount_power_config()
        try:
            if self.s0ix_supported:
                self.verify_state('S0ix')

            if self.s3_supported:
                self.verify_state('S3')
        finally:
            self.umount_power_config()

        # Enter G3
        self.verify_state('G3')
        if self.run_errors:
            self.all_errors[self.ccd_str] = self.run_errors


    def run_once(self, host):
        """Go through S0ix, S3, and G3. Verify there are no interrupt storms"""
        self.all_errors = {}
        self.host = host
        self.is_arm = self.is_arm_family()
        supports_dts_control = self.cr50.servo_dts_mode_is_valid()

        if supports_dts_control:
            self.cr50.ccd_disable(raise_error=True)

        self.ccd_enabled = self.cr50.ccd_is_enabled()
        # Check if the device supports S0ix.
        self.s0ix_supported = not self.host.run(
                'check_powerd_config --suspend_to_idle',
                ignore_status=True).exit_status
        # Check if the device supports S3.
        self.s3_supported = not self.host.run(
                'grep -q deep /sys/power/mem_sleep',
                ignore_status=True).exit_status

        self.run_through_power_states()

        if supports_dts_control:
            ccd_was_enabled = self.ccd_enabled
            self.cr50.ccd_enable(raise_error=supports_dts_control)
            self.ccd_enabled = self.cr50.ccd_is_enabled()
            # If the first run had ccd disabled, and the test was able to enable
            # ccd, run through the states again to make sure there are no issues
            # come up when ccd is enabled.
            if not ccd_was_enabled and self.ccd_enabled:
                self.run_through_power_states()
        else:
            logging.info('Current setup only supports test with ccd %sabled.',
                    'en' if self.ccd_enabled else 'dis')

        self.trigger_s0()
        if self.all_errors:
            raise error.TestFail('Unexpected Device State: %s' %
                    self.all_errors)
        if not supports_dts_control:
            raise error.TestNAError('Verified device state with %s. Please '
                    'run with type c servo v4 to test full device state.' %
                    self.ccd_str)
