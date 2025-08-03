# Copyright (c) 2011 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.server.cros.faft.firmware_test import FirmwareTest
from autotest_lib.server.cros.servo import chrome_ec
from autotest_lib.server.cros import vboot_constants as vboot


class firmware_DevMode(FirmwareTest):
    """
    Servo based developer firmware boot test.
    """
    version = 1

    def initialize(self, host, cmdline_args, ec_wp=None):
        super(firmware_DevMode, self).initialize(
                host, cmdline_args, ec_wp=ec_wp)
        self.switcher.setup_mode('normal')
        self.setup_usbkey(usbkey=False)

    def run_once(self):
        """Method which actually runs the test."""
        self.check_state((self.checkers.crossystem_checker, {
                'devsw_boot': '0',
                'mainfw_type': 'normal',
        }))

        logging.info("Enable dev mode.")
        self.switcher.reboot_to_mode("dev", sync_before_boot=False)

        logging.info("Expected developer mode boot and enable normal mode.")
        self.check_state((self.checkers.crossystem_checker, {
                'devsw_boot': '1',
                'mainfw_type': 'developer',
        }))
        self.switcher.reboot_to_mode(to_mode='normal')

        logging.info("Expected normal mode boot, done.")
        self.check_state((self.checkers.crossystem_checker, {
                'devsw_boot': '0',
                'mainfw_type': 'normal',
        }))

        if self.check_ec_capability():
            gbb = self.faft_client.bios.get_gbb_flags()
            if gbb & vboot.GBB_FLAG_DISABLE_EC_SOFTWARE_SYNC:
                # In order to test that entering dev mode does not work when
                # EC_IN_RW=1, EC software sync must be enabled.  If EC software
                # sync is disabled, then we must skip this portion of the test.
                logging.info("Skipping dev mode transition in EC RW test.")
                return

            logging.info("Rebooting into fake recovery mode (EC still in RW).")
            self._client.power_off_via_servo()
            self.ec.set_hostevent(chrome_ec.HOSTEVENT_KEYBOARD_RECOVERY)
            self.servo.power_short_press()

            logging.info("Trying to transition to dev mode with EC_IN_RW=1.")
            self.switcher.trigger_rec_to_dev()
            self.switcher.bypass_dev_mode()
            if not self._client.ping_wait_up(
                    timeout=self.faft_config.delay_reboot_to_ping):
                logging.info("DUT didn't come back up (expected!), rebooting.")
                self.switcher.simple_reboot(sync_before_boot=False)
            self.switcher.wait_for_client()

            logging.info("DUT is back up, should still be in normal mode now.")
            self.check_state((self.checkers.crossystem_checker, {
                    'devsw_boot': '0',
                    'mainfw_type': 'normal',
            }))
