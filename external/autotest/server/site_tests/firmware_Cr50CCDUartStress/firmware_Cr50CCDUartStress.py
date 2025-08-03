# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


"""The autotest performing uart_stress_tester on EC uart port. """
import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.cr50_test import Cr50Test


FLAG_FILENAME = '/tmp/chargen_testing'
# A ChromeOS TPM command to burden CR50.
TPM_CMD = ('trunks_client --stress_test')
# A command line to burden Cr50 with TPM_CMD as long as FLAG_FILENAME exists.
CR50_LOAD_GEN_CMD = 'while [ -f %s ]; do %s; done &' % (FLAG_FILENAME, TPM_CMD)

# Character generator
CHARGEN_CMD = 'chargen'

class firmware_Cr50CCDUartStress(Cr50Test):
    """A test that checks character loss with a UART and TPM stress workload."""

    version = 1
    flag_filename = '/tmp/chargen_testing'

    def initialize(self, host, cmdline_args, full_args):
        """Initialize the test

        Raises:
            TestNAError: if the test environment is not properly set.
                         e.g. the servo type doesn't support this test, or
                         EC Uart command, chargen is not available.
        """
        super(firmware_Cr50CCDUartStress,
              self).initialize(host, cmdline_args, full_args)

        # Don't bother if there is no Chrome EC or if EC hibernate doesn't work.
        if not self.check_ec_capability():
            raise error.TestNAError('Nothing needs to be tested on this device')

        # Check EC chargen is available.
        if not self.ec.has_command(CHARGEN_CMD):
            raise error.TestNAError('chargen command is not available in EC.')
        logging.info('Checked EC has the uart command, %r.', CHARGEN_CMD)

        # Check CCD is in servo_type.
        servo_type = self.servo.get_servo_version()
        if 'ccd' not in servo_type:
            raise error.TestNAError('unsupported servo type: %s' % servo_type)
        logging.info('Checked the servo type is %r.', servo_type)

        # Fast open cr50 and enable testlab.
        self.fast_ccd_open(enable_testlab=True)
        logging.info('CCD opened.')

        # Change active device to the ccd device
        if not self.servo.enable_ccd_servo_device():
            raise error.TestNAError('Cannot make ccd active')
        self.active_dev = self.servo.get_active_device_prefix()

        # Store the original status of EC ec3po_interp_connect.
        self.ec_ec3po_connect = self.servo.get('ec_ec3po_interp_connect',
                                               prefix=self.active_dev)
        # turn off EC ec3po_interp_connect
        self.servo.set('ec_ec3po_interp_connect', 'off', prefix=self.active_dev)
        logging.info('Turned off ec3po.')

    def cleanup(self):
        """Clean up Uart stress test, then cleanup Cr50Test"""
        try:
            # Terminate cr50 stressing command run.
            self.host.run('rm -f ' + FLAG_FILENAME)

            # Restore EC ec3po interpreter connect config.
            if hasattr(self, 'ec_ec3po_connect'):
                self.servo.set('ec_ec3po_interp_connect', self.ec_ec3po_connect,
                               prefix=self.active_dev)
            logging.info('Recovered ec3po.')
        finally:
            # Cleanup super class
            super(firmware_Cr50CCDUartStress, self).cleanup()

    def run_once(self, duration):
        """The method called by the control file to start the test.

        Args:
            duration: time in seconds to run uart_stress_tester.

        Raises:
            TestFail: uart_stress_tester returned non-zero exit code for
                      character loss or other reasons.
        """

        # Run TPM command to stress cr50 in CPU.
        logging.info('Start to stress cr50 with TPM commands.')
        self.host.run('touch ' + FLAG_FILENAME)
        self.host.run('nohup sh -c %r &> /dev/null' % CR50_LOAD_GEN_CMD)

        # Run uart_stress_tester.
        uart_pty = self.servo.get('raw_ec_uart_pty', prefix=self.active_dev)
        testcmd = 'uart_stress_tester.py -t %d -d %s' % (duration, uart_pty)

        logging.info('Run Uart stress tester for %d seconds.', duration)
        logging.info(testcmd)
        try:
            self.servo.system(testcmd, timeout=duration*2)
        except error.AutoservRunError:
            raise error.TestFail('Uart stress tester failed.')

        logging.info('Uart stress tester passed.')
