# Copyright (c) 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import string
import random
import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


def random_string(length, chars):
    """Generate a random string of characters.

    @param length: the length of string to generate.
    @param chars: the set of characters to use when generating the string.

    @returns The generated string.
    """
    return ''.join(random.SystemRandom().choice(chars) for _ in range(length))


class firmware_SysfsVPD(FirmwareTest):
    """
    Servo based test for reading VPD data through sysfs.

    This test writes random test strings to the RO and RW sections of VPD data
    and verifies that they can be read through sysfs after a reboot.
    """
    version = 1

    # Length of test string to generate and write to VPD
    _TEST_VAL_LENGTH = 8

    # Character set to use when generating string to write to VPD
    _TEST_VAL_CHARS = string.ascii_lowercase + string.digits

    # Name of key to write to RO section of VPD
    VPD_RO_TEST_KEY = "RO_TEST"

    # Name of key to write to RW section of VPD
    VPD_RW_TEST_KEY = "RW_TEST"

    def initialize(self, host, cmdline_args, dev_mode=False):
        """Initialize the test"""
        super(firmware_SysfsVPD, self).initialize(host, cmdline_args)

        fwver = self.faft_client.system.run_shell_command_get_output(
                'crossystem fwid')[0]
        try:
            fwver_major = int(fwver.split('.')[1])
        except ValueError:
            raise error.TestFail('Could not determine firmware version')
        # Only run this test for 8846 or newer because previous firmware
        # versions don't add the pointer to ACPI that Linux uses to look up
        # cbmem which is then used to get the VPD.
        # See b:156407743 for details.
        self.disable_test = fwver_major < 8846

        # Backup and mode switching is expensive so skip if we won't be
        # doing anything anyway.
        if self.disable_test:
            raise error.TestNAError("Firmware too old for SysfsVPD")

        self.host = host
        self.backup_firmware()
        self.switcher.setup_mode('dev' if dev_mode else 'normal',
                                 allow_gbb_force=True)

    def cleanup(self):
        """Cleanup the test"""
        try:
            if self.is_firmware_saved():
                self.restore_firmware()
        finally:
            super(firmware_SysfsVPD, self).cleanup()

    def run_once(self, dev_mode=False):
        """Runs a single iteration of the test."""
        # Log the initial VPD sections so we can manually restore VPD from
        # test logs if necessary.
        logging.info("Logging initial RO+RW VPD data")
        self.host.run("vpd -i RO_VPD -l")
        self.host.run("vpd -i RW_VPD -l")

        # Generate a random string and write it to RO section of VPD
        vpd_ro_test_val = random_string(length=self._TEST_VAL_LENGTH,
                                        chars=self._TEST_VAL_CHARS)
        logging.info("Writting RO test data to VPD (key = %s, val = %s)",
                     self.VPD_RO_TEST_KEY, vpd_ro_test_val)
        self.host.run("vpd -i RO_VPD -s %s=%s" %
                      (self.VPD_RO_TEST_KEY, vpd_ro_test_val))

        # Generate a random string and write it to RW section of VPD
        vpd_rw_test_val = random_string(length=self._TEST_VAL_LENGTH,
                                        chars=self._TEST_VAL_CHARS)
        logging.info("Writting RW test data to VPD (key = %s, val = %s)",
                     self.VPD_RW_TEST_KEY, vpd_rw_test_val)
        self.host.run("vpd -i RW_VPD -s %s=%s" %
                      (self.VPD_RW_TEST_KEY, vpd_rw_test_val))

        # Reboot DUT to load new VPD data in sysfs
        logging.info('Rebooting DUT')
        self.host.reset_via_servo()

        # Verify RO test string can be read through sysfs and matches test value
        logging.info('Verifying RO VPD test data in sysfs')
        try:
            path = "/sys/firmware/vpd/ro/%s" % self.VPD_RO_TEST_KEY
            result = self.host.run("cat %s" % path)
            value = result.stdout.strip()
        except error.AutoservRunError:
            raise error.TestFail("Failed to read back RO VPD data")
        if value != vpd_ro_test_val:
            raise error.TestFail(
                "Mismatched RO VPD data, read %s (expected %s)" %
                (value, vpd_ro_test_val))

        # Verify RW test string can be read through sysfs and matches test value
        logging.info('Verifying RW VPD test data in sysfs')
        try:
            path = "/sys/firmware/vpd/rw/%s" % self.VPD_RW_TEST_KEY
            result = self.host.run("cat %s" % path)
            value = result.stdout.strip()
        except error.AutoservRunError:
            raise error.TestFail("Failed to read back RW VPD data")
        if value != vpd_rw_test_val:
            raise error.TestFail(
                "Mismatched RW VPD data, read %s (expected %s)" %
                (value, vpd_rw_test_val))

        # Remove the test keys from VPD
        logging.info("Deleting test data from VPD")
        self.host.run("vpd -i RO_VPD -d %s" % self.VPD_RO_TEST_KEY)
        self.host.run("vpd -i RW_VPD -d %s" % self.VPD_RW_TEST_KEY)
