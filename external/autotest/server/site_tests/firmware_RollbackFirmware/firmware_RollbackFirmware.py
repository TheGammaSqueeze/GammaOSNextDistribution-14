# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.server.cros import vboot_constants as vboot
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_RollbackFirmware(FirmwareTest):
    """
    Servo based firmware rollback test.

    This test requires a USB disk plugged-in, which contains a ChromeOS test
    image (built by "build_image --test"). On runtime, this test rollbacks
    firmware A and results firmware B boot. It then rollbacks firmware B and
    results recovery boot.
    """
    version = 1
    NEEDS_SERVO_USB = True

    def initialize(self, host, cmdline_args, dev_mode=False):
        """Initialize the test"""
        super(firmware_RollbackFirmware, self).initialize(host, cmdline_args)
        self.backup_firmware()
        self.switcher.setup_mode('dev' if dev_mode else 'normal')
        self.setup_usbkey(usbkey=True, host=False)

    def cleanup(self):
        """Cleanup the test"""
        try:
            if self.is_firmware_saved():
                self.restore_firmware()
        except  Exception as e:
            logging.error("Caught exception: %s", str(e))
        super(firmware_RollbackFirmware, self).cleanup()

    def run_once(self, dev_mode=False):
        """Runs a single iteration of the test."""
        logging.info("Rollback firmware A.")
        self.check_state((self.checkers.fw_tries_checker, 'A'))
        version_a = self.faft_client.bios.get_version('a')
        logging.info("Change A version from %d to %d.", version_a,
                     version_a - 1)
        self.faft_client.bios.set_version('a', version_a - 1)
        self.switcher.mode_aware_reboot()

        logging.info("Expected firmware B boot and rollback firmware B.")
        self.check_state((self.checkers.fw_tries_checker, ('B', False)))
        version_b = self.faft_client.bios.get_version('b')
        logging.info("Change B version from %d to %d.", version_b,
                     version_b - 1)
        self.faft_client.bios.set_version('b', version_b - 1)

        # Older devices (without BROKEN screen) didn't wait for removal in
        # dev mode. Make sure the USB key is not plugged in so they won't
        # start booting immediately and get interrupted by unplug/replug.
        self.servo.switch_usbkey('host')
        self.switcher.simple_reboot()
        self.switcher.bypass_rec_mode()
        self.switcher.wait_for_client()

        logging.info("Expected recovery boot and restores firmware A and B.")
        self.check_state((self.checkers.crossystem_checker, {
                           'mainfw_type': 'recovery',
                           'recovery_reason': (
                                vboot.RECOVERY_REASON['RO_INVALID_RW'],
                                vboot.RECOVERY_REASON['RW_FW_ROLLBACK']),
                           }))
        logging.info("Restore version of firmware A/B to %d/%d.", version_a,
                     version_b)
        self.faft_client.bios.set_version('a', version_a)
        self.faft_client.bios.set_version('b', version_b)
        self.switcher.mode_aware_reboot()

        expected_slot = 'B' if self.fw_vboot2 else 'A'
        logging.info("Expected firmware %s boot, done.", expected_slot)
        self.check_state((self.checkers.fw_tries_checker, expected_slot))
