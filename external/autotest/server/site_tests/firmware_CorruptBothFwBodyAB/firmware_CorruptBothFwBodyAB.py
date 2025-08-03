# Copyright (c) 2011 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.server.cros import vboot_constants as vboot
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_CorruptBothFwBodyAB(FirmwareTest):
    """
    Servo based both firmware body A and B corruption test.

    The firmware verification fails on loading RW firmware and enters recovery
    mode. It requires a USB disk plugged-in, which contains a
    ChromeOS test image (built by "build_image --test").
    """
    version = 1
    NEEDS_SERVO_USB = True

    def initialize(self, host, cmdline_args, dev_mode=False):
        """Initialize the test"""
        super(firmware_CorruptBothFwBodyAB, self).initialize(host, cmdline_args)
        self.backup_firmware()
        self.switcher.setup_mode('dev' if dev_mode else 'normal')
        self.setup_usbkey(usbkey=True, host=False)

    def cleanup(self):
        """Cleanup the test"""
        try:
            if self.is_firmware_saved():
                self.restore_firmware()
        except Exception as e:
            logging.error("Caught exception: %s", str(e))
        super(firmware_CorruptBothFwBodyAB, self).cleanup()

    def run_once(self, dev_mode=False):
        """Runs a single iteration of the test."""
        logging.info("Corrupt both firmware body A and B.")
        self.check_state((self.checkers.crossystem_checker, {
                    'mainfw_type': 'developer' if dev_mode else 'normal',
                    }))
        offset_a, byte_a = self.faft_client.bios.get_body_one_byte('a')
        offset_b, byte_b = self.faft_client.bios.get_body_one_byte('b')
        self.faft_client.bios.modify_body('a', offset_a, byte_a + 1)
        self.faft_client.bios.modify_body('b', offset_b, byte_b + 1)

        # Older devices (without BROKEN screen) didn't wait for removal in
        # dev mode. Make sure the USB key is not plugged in so they won't
        # start booting immediately and get interrupted by unplug/replug.
        self.servo.switch_usbkey('host')
        self.switcher.simple_reboot()
        self.switcher.bypass_rec_mode()
        self.switcher.wait_for_client()

        logging.info("Expected recovery boot and restore firmware.")
        self.check_state((self.checkers.crossystem_checker, {
                              'mainfw_type': 'recovery',
                              'recovery_reason':
                              (vboot.RECOVERY_REASON['RO_INVALID_RW'],
                              vboot.RECOVERY_REASON['RW_VERIFY_BODY']),
                              }))
        self.faft_client.bios.modify_body('a', offset_a, byte_a)
        self.faft_client.bios.modify_body('b', offset_b, byte_b)
        self.switcher.mode_aware_reboot()

        logging.info("Expected normal boot, done.")

        if self.faft_config.clear_dev_on_rec:
            dev_mode=False

        self.check_state((self.checkers.crossystem_checker, {
                    'mainfw_type': 'developer' if dev_mode else 'normal',
                    }))
