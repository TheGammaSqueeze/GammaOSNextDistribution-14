# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.cr50_test import Cr50Test
from autotest_lib.server.cros.servo import chrome_ti50


class firmware_Cr50CCDServoCap(Cr50Test):
    """Verify Cr50 CCD output enable/disable when servo is connected.

    Verify Cr50 will enable/disable the CCD servo output capabilities when servo
    is attached/detached.
    """
    version = 1

    # Time used to wait for Cr50 to detect the servo state. Cr50 updates the ccd
    # state once a second. Wait 2 seconds to be conservative.
    SLEEP = 2

    # A list of the actions we should verify
    TEST_CASES = [
        'fake_servo on, cr50_run reboot',
        'fake_servo on, rdd attach, cr50_run reboot',

        'rdd attach, fake_servo on, cr50_run reboot, fake_servo off',
        'rdd attach, fake_servo on, rdd detach',
        'rdd attach, fake_servo off, rdd detach',
    ]

    ON = 0
    OFF = 1
    UNDETECTABLE = 2
    STATUS_MAP = [ 'on', 'off', 'unknown' ]
    # Create maps for the different ccd states. Mapping each state to 'on',
    # 'off', and 'unknown'. These lists map to the acceptable [ on values, off
    # values, and unknown state values]
    ON_MAP = [ 'on', 'off', '' ]
    ENABLED_MAP = [ 'enabled', 'disabled', '' ]
    CONNECTED_MAP = [ 'connected', 'disconnected', 'undetectable' ]
    ASSERTED_MAP = ['asserted', 'deasserted', '']
    VALID_STATES = {
            'AP': ON_MAP,
            'EC': ON_MAP,
            'AP UART': ON_MAP,
            'Rdd': CONNECTED_MAP,
            'Servo': CONNECTED_MAP,
            'CCD EXT': ENABLED_MAP,
            'CCD_MODE': ASSERTED_MAP,
    }
    # TODO(mruthven): remove CCD_ENABLED_KEYS and mentions of 'CCD EXT' once
    # prepvt and mp images use CCD_MODE.
    # Old ccdstate uses CCD EXT. The new ccdstate output uses CCD_MODE.
    CCD_ENABLED_KEYS = ['CCD EXT', 'CCD_MODE']
    # RESULT_ORDER is a list of the CCD state strings. The order corresponds
    # with the order of the key states in EXPECTED_RESULTS.
    RESULT_ORDER = ['Rdd', 'CCD_MODE', 'Servo']
    # A dictionary containing an order of steps to verify and the expected ccd
    # states as the value.
    #
    # The keys are a list of strings with the order of steps to run.
    #
    # The values are the expected state of [rdd, ccd ext, servo]. The ccdstate
    # strings are in RESULT_ORDER. The order of the EXPECTED_RESULTS key states
    # must match the order in RESULT_ORDER.
    #
    # There are three valid states: UNDETECTABLE, ON, or OFF. Undetectable only
    # describes the servo state when EC uart is enabled. If the ec uart is
    # enabled, cr50 cannot detect servo and the state becomes undetectable. All
    # other ccdstates can only be off or on. Cr50 has a lot of different words
    # for off off and on. So VALID_STATES can be used to convert off, on, and
    # undetectable to the actual state strings.
    EXPECTED_RESULTS = {
        # The state all tests will start with. Servo and the ccd cable are
        # disconnected.
        'reset_ccd state' : [OFF, OFF, OFF],

        # If rdd is attached all ccd functionality will be enabled, and servo
        # will be undetectable.
        'rdd attach' : [ON, ON, UNDETECTABLE],

        # Cr50 cannot detect servo if ccd has been enabled first
        'rdd attach, fake_servo off' : [ON, ON, UNDETECTABLE],
        'rdd attach, fake_servo off, rdd detach' : [OFF, OFF, OFF],
        'rdd attach, fake_servo on' : [ON, ON, UNDETECTABLE],
        'rdd attach, fake_servo on, rdd detach' : [OFF, OFF, ON],
        # Cr50 can detect servo after a reboot even if rdd was attached before
        # servo.
        'rdd attach, fake_servo on, cr50_run reboot' : [ON, ON, ON],
        # Once servo is detached, Cr50 will immediately reenable the EC uart.
        'rdd attach, fake_servo on, cr50_run reboot, fake_servo off' :
            [ON, ON, UNDETECTABLE],

        # Cr50 can detect a servo attach
        'fake_servo on' : [OFF, OFF, ON],
        # Cr50 knows servo is attached when ccd is enabled, so it wont enable
        # uart.
        'fake_servo on, rdd attach' : [ON, ON, ON],
        'fake_servo on, rdd attach, cr50_run reboot' : [ON, ON, ON],
        'fake_servo on, cr50_run reboot' : [OFF, OFF, ON],
    }


    def initialize(self, host, cmdline_args, full_args):
        super(firmware_Cr50CCDServoCap, self).initialize(host, cmdline_args,
                full_args)
        if not hasattr(self, 'cr50'):
            raise error.TestNAError('Test can only be run on devices with '
                                    'access to the Cr50 console')

        if ('servo_v4' not in self.servo.get_servo_type()
                    or not self.servo.main_device_is_flex()):
            raise error.TestNAError('Must use servo v4 with flex(c2d2 or '
                                    'servo_micro)')

        if not self.cr50.servo_dts_mode_is_valid():
            raise error.TestNAError('Need working servo v4 DTS control')

        if not self.cr50.check_servo_monitor():
            raise error.TestNAError('Cannot run on device that does not '
                                    'support servo dectection with '
                                    'ec_uart_en:off/on')
        # Make sure cr50 is open with testlab enabled.
        self.fast_ccd_open(enable_testlab=True)
        if not self.cr50.testlab_is_on():
            raise error.TestNAError('Cr50 testlab mode needs to be enabled')
        logging.info('Cr50 is %s', self.servo.get('gsc_ccd_level'))
        self.cr50.set_cap('UartGscTxECRx', 'Always')
        self.ec_efs_support = (
                self.cr50.uses_board_property('BOARD_EC_CR50_COMM_SUPPORT'))
        self._ccd_prefix = ('' if self.servo.main_device_is_ccd() else
                            self.servo.get_ccd_servo_device())
        # Check EC uart if servo has ccd controls and the board has an EC.
        self.check_ec_uart = (
                self.servo.has_control('ec_board', prefix=self._ccd_prefix)
                and self.check_ec_capability(suppress_warning=True))


    def cleanup(self):
        """Reenable the EC uart"""
        try:
            self.fake_servo('on')
            self.rdd('detach')
            self.rdd('attach')
        finally:
            super(firmware_Cr50CCDServoCap, self).cleanup()


    def state_matches(self, state_dict, state_name, expected_value):
        """Check the current state. Make sure it matches expected value"""
        if state_name in self.CCD_ENABLED_KEYS:
            for state_name in self.CCD_ENABLED_KEYS:
                if state_name in state_dict:
                    logging.info('Using %r for ccd enabled key', state_name)
                    break

        valid_state = self.VALID_STATES[state_name][expected_value]
        # I2C isn't a reliable flag, because the hardware often doesn't support
        # it. Remove any I2C flags from the ccdstate output.
        current_state = state_dict[state_name].replace(' I2C', '')
        if isinstance(valid_state, list):
            return current_state in valid_state
        return current_state == valid_state


    def state_is_on(self, ccdstate, state_name):
        """Returns true if the state is on"""
        return self.state_matches(ccdstate, state_name, self.ON)


    def ccd_ec_uart_works(self):
        """Returns True if the CCD ec uart works."""
        try:
            self.servo.get('ec_board', prefix=self._ccd_prefix)
            logging.info('ccd ec console is responsive')
            return True
        except:
            logging.info('ccd ec console is unresponsive')
            return False


    def check_state_flags(self, ccdstate):
        """Check the state flags against the reset of the device state

        If there is any mismatch between the device state and state flags,
        return a list of errors.
        """
        flags = ccdstate['State flags']
        ap_uart_enabled = 'UARTAP' in flags
        ec_uart_enabled = 'UARTEC' in flags
        ap_uart_tx_enabled = 'UARTAP+TX' in flags
        ec_uart_tx_enabled = 'UARTEC+TX' in flags
        ec_usb_tx_enabled = 'USBEC+TX' in flags

        ccd_ec_uart_enabled = ec_uart_tx_enabled and ec_usb_tx_enabled
        ccd_enabled = ap_uart_enabled or ec_usb_tx_enabled
        output_enabled = ap_uart_tx_enabled
        if not self.ec_efs_support:
            output_enabled |= ec_uart_tx_enabled
            ccd_enabled |= ec_uart_enabled

        ccd_mode_is_asserted = self.state_is_on(ccdstate, 'CCD_MODE')
        mismatch = []
        logging.info('checking state flags')
        if ccd_enabled and not ccd_mode_is_asserted:
            mismatch.append('CCD functionality enabled CCD_MODE asserted')
        if ccd_mode_is_asserted:
            if output_enabled and self.state_is_on(ccdstate, 'Servo'):
                mismatch.append('CCD output is enabled with servo attached')
            if not isinstance(self.cr50, chrome_ti50.ChromeTi50):
                if ap_uart_enabled != self.state_is_on(ccdstate, 'AP UART'):
                    mismatch.append('AP UART enabled without AP UART on')
                if ec_uart_enabled != self.state_is_on(ccdstate, 'EC'):
                    mismatch.append('EC UART enabled without EC on')
            if self.check_ec_uart:
                ccd_ec_uart_works = self.ccd_ec_uart_works()
                if (self.servo.get('ec_uart_en') == 'off'
                    and ccd_ec_uart_enabled and not ccd_ec_uart_works):
                    mismatch.append('ccd ec uart does not work with EC+TX '
                                    'enabled.')
                if not ccd_ec_uart_enabled and ccd_ec_uart_works:
                    mismatch.append('ccd ec uart works with EC+TX disabled.')
        return mismatch



    def verify_ccdstate(self, run):
        """Verify the current state matches the expected result from the run.

        Args:
            run: the string representing the actions that have been run.

        Raises:
            TestError if any of the states are not correct
        """
        if run not in self.EXPECTED_RESULTS:
            raise error.TestError('Add results for %s to EXPECTED_RESULTS' % run)
        expected_states = self.EXPECTED_RESULTS[run]

        # Wait a short time for the ccd state to settle
        time.sleep(self.SLEEP)

        ccdstate = self.cr50.get_ccdstate()
        # Check the state flags. Make sure they're in line with the rest of
        # ccdstate
        mismatch = self.check_state_flags(ccdstate)
        for i, expected_state in enumerate(expected_states):
            name = self.RESULT_ORDER[i]
            if expected_state == None:
                logging.info('No expected %s state skipping check', name)
                continue
            # Check that the current state matches the expected state
            if not self.state_matches(ccdstate, name, expected_state):
                mismatch.append('%s is %r not %r' % (name, ccdstate[name],
                                self.STATUS_MAP[expected_state]))
        if mismatch:
            logging.info(ccdstate)
            raise error.TestFail('Unexpected states after %s: %s' % (run,
                mismatch))


    def cr50_run(self, action):
        """Reboot cr50

        @param action: string 'reboot'
        """
        if action == 'reboot':
            self.cr50.reboot()
            self.cr50.send_command('ccd testlab open')
            time.sleep(self.SLEEP)


    def reset_ccd(self, state=None):
        """detach the ccd cable and disconnect servo.

        State is ignored. It just exists to be consistent with the other action
        functions.

        @param state: a var that is ignored
        """
        self.rdd('detach')
        self.fake_servo('off')


    def rdd(self, state):
        """Attach or detach the ccd cable.

        @param state: string 'attach' or 'detach'
        """
        self.servo.set_dts_mode('on' if state == 'attach' else 'off')
        time.sleep(self.SLEEP)


    def fake_servo(self, state):
        """Mimic servo on/off

        Cr50 monitors the servo EC uart tx signal to detect servo. If the signal
        is pulled up, then Cr50 will think servo is connnected. Enable the ec
        uart to enable the pullup. Disable the it to remove the pullup.

        It takes some time for Cr50 to detect the servo state so wait 2 seconds
        before returning.
        """
        self.servo.set('ec_uart_en', state)

        # Cr50 needs time to detect the servo state
        time.sleep(self.SLEEP)


    def run_steps(self, steps):
        """Do each step in steps and then verify the uart state.

        The uart state is order dependent, so we need to know all of the
        previous steps to verify the state. This will do all of the steps in
        the string and verify the Cr50 CCD uart state after each step.

        @param steps: a comma separated string with the steps to run
        """
        # The order of steps is separated by ', '. Remove the last step and
        # run all of the steps before it.
        separated_steps = steps.rsplit(', ', 1)
        if len(separated_steps) > 1:
            self.run_steps(separated_steps[0])

        step = separated_steps[-1]
        # The func and state are separated by ' '
        func, state = step.split(' ')
        logging.info('running %s', step)
        getattr(self, func)(state)

        # Verify the ccd state is correct
        self.verify_ccdstate(steps)


    def run_once(self):
        """Run through TEST_CASES and verify that Cr50 enables/disables uart"""
        for steps in self.TEST_CASES:
            self.run_steps('reset_ccd state')
            logging.info('TESTING: %s', steps)
            self.run_steps(steps)
            logging.info('VERIFIED: %s', steps)
