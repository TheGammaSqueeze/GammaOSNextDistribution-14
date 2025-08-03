# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_CorruptMinios(FirmwareTest):
    """
    Servo based corrupt minios test.

    This test requires the device to support MiniOS. On runtime, this test uses
    the dd tool to corrupt the MiniOS partition and try to boot MiniOS from
    firmware manual recovery screen.
    """
    version = 1

    def initialize(self, host, cmdline_args, minios_section):
        super(firmware_CorruptMinios, self).initialize(host, cmdline_args)

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
        self.setup_usbkey(usbkey=False)
        self.minios_section = minios_section
        self.restored_priority = self.faft_client.system.get_minios_priority()

    def cleanup(self):
        if not self.test_skipped:
            try:
                self.switcher.leave_minios()
                self.restore_kernel(kernel_type='MINIOS')
                self.faft_client.system.set_minios_priority(
                        self.restored_priority)
            except Exception as e:
                logging.error('Caught exception: %s', str(e))
        super(firmware_CorruptMinios, self).cleanup()

    def run_once(self):
        """Run a single iteration of the test."""
        logging.info('Corrupt MiniOS section: %r', self.minios_section)
        self.faft_client.minios.corrupt_sig(self.minios_section)

        logging.info('Try to boot with prioritizing the corrupted section')
        self.switcher.launch_minios(self.minios_section)
        self.check_state(self.checkers.minios_checker)
        self.switcher.leave_minios()

        logging.info('Restore MiniOS section: %r', self.minios_section)
        self.faft_client.minios.restore_sig(self.minios_section)
