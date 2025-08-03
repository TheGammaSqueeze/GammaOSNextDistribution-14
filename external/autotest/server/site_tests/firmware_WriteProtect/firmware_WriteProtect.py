# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_WriteProtect(FirmwareTest):
    """
    This test checks whether the hardware write-protect state reported by
    crossystem matches the real write-protect state driven by Servo.
    """
    version = 1

    def initialize(self, host, cmdline_args, dev_mode=False):
        """Initialize the test"""
        super(firmware_WriteProtect, self).initialize(host, cmdline_args)
        self.switcher.setup_mode('dev' if dev_mode else 'normal',
                                 allow_gbb_force=True)
        self._original_wp = 'on' in self.servo.get('fw_wp_state')

    def cleanup(self):
        """Cleanup the test"""
        try:
            if hasattr(self, '_original_wp'):
                self.set_ap_write_protect_and_reboot(self._original_wp)
        except Exception as e:
            logging.error('Caught exception: %s', str(e))
        super(firmware_WriteProtect, self).cleanup()

    def run_once(self):
        """Runs a single iteration of the test."""
        logging.info('Enable write-protect.')
        self.set_ap_write_protect_and_reboot(True)
        self.check_state((self.checkers.crossystem_checker, {'wpsw_cur': '1'}))
        logging.info('Now disable write-protect and check again.')
        self.set_ap_write_protect_and_reboot(False)
        self.check_state((self.checkers.crossystem_checker, {'wpsw_cur': '0'}))
        logging.info('Enable write-protect again to check final state.')
        self.set_ap_write_protect_and_reboot(True)
        self.check_state((self.checkers.crossystem_checker, {'wpsw_cur': '1'}))
