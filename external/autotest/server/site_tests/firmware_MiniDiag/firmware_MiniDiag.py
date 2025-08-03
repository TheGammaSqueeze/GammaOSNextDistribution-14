# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_MiniDiag(FirmwareTest):
    """
    Servo based MiniDiag firmware boot test.
    """
    version = 1

    def initialize(self, host, cmdline_args):
        super(firmware_MiniDiag, self).initialize(host, cmdline_args)

        if not self.menu_switcher:
            raise error.TestNAError('Test skipped for menuless UI')
        if not self.faft_config.minidiag_enabled:
            raise error.TestNAError('MiniDiag is not enabled for this board')
        # Need apreset to leave MiniDiag
        if not self.ec.has_command('apreset'):
            raise error.TestNAError('EC command apreset is not supported')

        self.switcher.setup_mode('normal')
        self.setup_usbkey(usbkey=False)

    def run_once(self):
        """Method which actually runs the test."""
        # Trigger MiniDiag by menu navigation
        logging.info('Trigger MiniDiag by menu navigation')
        self.switcher.enable_rec_mode_and_reboot(usb_state='host')
        self.switcher.wait_for_client_offline()
        self.menu_switcher.trigger_rec_to_minidiag()

        # Navigator MiniDiag
        logging.info('Navigate among MiniDiag screens')
        self.menu_switcher.navigate_minidiag_storage()
        self.menu_switcher.navigate_minidiag_quick_memory_check()

        # Leave MiniDiag and reboot
        logging.info('Leave MiniDiag and reboot')
        self.menu_switcher.reset_and_leave_minidiag()
        logging.info('Expect normal mode boot, done')
        self.switcher.wait_for_client()
