# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_MenuPowerOff(FirmwareTest):
    """
    Servo based test for powering off the device through the UI menu.
    """
    version = 1

    # Timeout of confirming DUT shutdown
    POWER_OFF_TIMEOUT = 20

    def initialize(self, host, cmdline_args):
        super(firmware_MenuPowerOff, self).initialize(host, cmdline_args)
        if not self.menu_switcher:
            raise error.TestNAError('Test skipped for menuless UI')
        if not self.faft_config.chrome_ec:
            raise error.TestNAError('Cannot check power state without EC')
        self.switcher.setup_mode('dev')
        self.setup_usbkey(usbkey=False)

    def run_once(self):
        """Method which actually runs the test."""
        self.check_state((self.checkers.mode_checker, 'dev'))
        self.switcher.simple_reboot()

        # Now the device should be in dev screen
        logging.info('Power off device in developer screen')
        self.run_shutdown_process(self.menu_switcher.power_off,
                                  run_power_action=False,
                                  shutdown_timeout=self.POWER_OFF_TIMEOUT)

        # Reboot to rec screen
        self.switcher.enable_rec_mode_and_reboot(usb_state='host')

        # Now the device should be in rec screen
        logging.info('Power off device in recovery screen')
        self.run_shutdown_process(
                self.menu_switcher.power_off,
                post_power_action=self.switcher.bypass_dev_mode,
                shutdown_timeout=self.POWER_OFF_TIMEOUT)
        self.switcher.wait_for_client()
