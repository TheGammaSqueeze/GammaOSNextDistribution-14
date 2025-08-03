# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_MenuModeTransition(FirmwareTest):
    """
    Servo based test for manual mode transitions through the UI menu.
    """
    version = 1

    def initialize(self, host, cmdline_args):
        super(firmware_MenuModeTransition, self).initialize(host, cmdline_args)
        if not self.menu_switcher:
            raise error.TestNAError('Test skipped for menuless UI')
        self.switcher.setup_mode('normal')
        self.setup_usbkey(usbkey=False)

    def run_once(self):
        """Method which actually runs the test."""
        self.check_state((self.checkers.mode_checker, 'normal'))

        # Trigger to-dev by menu navigation
        logging.info('Trigger to-dev by menu navigation.')
        self.switcher.enable_rec_mode_and_reboot(usb_state='host')
        self.switcher.wait_for_client_offline()
        self.menu_switcher.trigger_rec_to_dev()

        # Now the device should be in dev mode screen
        self.menu_switcher.dev_boot_from_internal()
        self.switcher.wait_for_client()

        logging.info('Expected dev mode boot.')
        self.check_state((self.checkers.mode_checker, 'dev'))

        # Trigger to-norm by menu navigation
        logging.info('Trigger to-norm by menu navigation.')
        self.switcher.simple_reboot()
        self.switcher.wait_for_client_offline()
        self.menu_switcher.trigger_dev_to_normal()
        self.switcher.wait_for_client()

        logging.info('Expected normal mode boot, done.')
        self.check_state((self.checkers.mode_checker, 'normal'))
