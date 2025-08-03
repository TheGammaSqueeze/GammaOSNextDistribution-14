# Copyright (c) 2011 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.server.cros import vboot_constants as vboot
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_RecoveryButton(FirmwareTest):
    """
    Servo based recovery button test.

    This test requires a USB disk plugged-in, which contains a ChromeOS test
    image (built by "build_image --test"). On runtime, this test emulates
    recovery button pressed and reboots. It then triggers recovery mode in
    two cases: (1) plug in the USB disk before power-on (2) plug in the USB
    disk after power-on, ideally around the time when INSERT screen shows up.
    Both cases should lead to successful recovery boot.
    """
    version = 1
    NEEDS_SERVO_USB = True

    def ensure_normal_boot(self):
        """Ensure normal mode boot this time.

        If not, it may be a test failure during step 2, try to recover to
        normal mode by setting no recovery mode and rebooting the machine.
        """
        if not self.checkers.crossystem_checker({
                'mainfw_type': ('normal', 'developer')
        }):
            self.servo.disable_recovery_mode()
            self.switcher.mode_aware_reboot()

    def check_recovery_state(self):
        """Check if this is a recovery boot."""
        self.check_state((self.checkers.crossystem_checker, {
                'mainfw_type':
                'recovery',
                'recovery_reason':
                vboot.RECOVERY_REASON['RO_MANUAL'],
        }))

    def initialize(self, host, cmdline_args, dev_mode=False, ec_wp=None):
        super(firmware_RecoveryButton, self).initialize(
                host, cmdline_args, ec_wp=ec_wp)
        self.switcher.setup_mode('dev' if dev_mode else 'normal')
        self.setup_usbkey(usbkey=True, host=False)

    def cleanup(self):
        try:
            self.ensure_normal_boot()
        except Exception as e:
            logging.error("Caught exception: %s", str(e))
        super(firmware_RecoveryButton, self).cleanup()

    def run_once(self, dev_mode=False):
        """Runs a single iteration of the test."""
        logging.info("Reboot to recovery mode with the USB stick plugged.")
        self.check_state((self.checkers.crossystem_checker, {
                'mainfw_type': 'developer' if dev_mode else 'normal',
        }))
        self.switcher.reboot_to_mode(to_mode="rec")

        logging.info("Expect a recovery boot from the USB stick.")
        self.check_recovery_state()

        logging.info("Power off, unplug the USB stick and reboot to rec mode.")
        self.switcher.enable_rec_mode_and_reboot(usb_state='host')

        logging.info("Plug in the USB stick after a delay")
        time.sleep(self.faft_config.firmware_screen)
        self.servo.switch_usbkey('dut')
        logging.info("Expect a recovery boot from the USB stick.")
        self.switcher.wait_for_client()
        self.check_recovery_state()

        logging.info("Reboot and then expected normal/dev boot.")
        self.switcher.mode_aware_reboot()
        is_jetstream = self.faft_config.mode_switcher_type == \
                'jetstream_switcher'
        self.check_state((self.checkers.crossystem_checker, {
                'mainfw_type':
                'developer' if dev_mode and not is_jetstream else 'normal',
        }))
