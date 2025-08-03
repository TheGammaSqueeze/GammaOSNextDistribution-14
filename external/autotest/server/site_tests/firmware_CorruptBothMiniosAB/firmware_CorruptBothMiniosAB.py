# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_CorruptBothMiniosAB(FirmwareTest):
    """
    Servo based corrupt minios test.

    This test requires the device support MiniOS. On runtime, this test uses the
    KernelHandler to corrupt both MiniOS partitions, tries to boot MiniOS from
    firmware manual recovery screen, and expects a failed boot.
    """
    version = 1

    def initialize(self, host, cmdline_args):
        super(firmware_CorruptBothMiniosAB,
              self).initialize(host, cmdline_args)

        self.test_skipped = True
        if not self.menu_switcher:
            raise error.TestNAError('Test skipped for menuless UI')
        if not self.faft_config.chrome_ec:
            raise error.TestNAError('Cannot check power state without EC')
        if not self.faft_config.minios_enabled:
            raise error.TestNAError('MiniOS is not enabled for this board')
        self.test_skipped = False

        self.backup_kernel(kernel_type='MINIOS')

        self.host = host
        self.switcher.setup_mode('normal')
        self.setup_usbkey(usbkey=True, host=True, used_for_recovery=True)

    def cleanup(self):
        if not self.test_skipped:
            try:
                self.switcher.leave_minios()
                self.restore_kernel(kernel_type='MINIOS')
            except Exception as e:
                logging.error('Caught exception: %s', str(e))
        super(firmware_CorruptBothMiniosAB, self).cleanup()

    def run_once(self):
        """Run a single iteration of the test."""
        logging.info('Corrupt both MiniOS sections')
        self.faft_client.minios.corrupt_sig('a')
        self.faft_client.minios.corrupt_sig('b')

        # Try to boot to MiniOS and expect a failed boot
        self.switcher.launch_minios()
        logging.info('DUT should fail to boot MiniOS, verifying...')
        if self.host.ping_wait_up(
                timeout=self.faft_config.delay_reboot_to_ping):
            raise error.TestFail('DUT should not come back up!')

        # Verify that DUT stayed in recovery screen by trying a USB boot
        logging.info('Boot from USB to verify that DUT stayed in recovery')
        self.servo.switch_usbkey('dut')
        self.switcher.wait_for_client()
        self.check_state((self.checkers.mode_checker, 'rec',
                          'Device didn\'t boot from USB in recovery screen'))
        self.switcher.mode_aware_reboot()

        logging.info('Restore both MiniOS sections')
        self.faft_client.minios.restore_sig('a')
        self.faft_client.minios.restore_sig('b')
