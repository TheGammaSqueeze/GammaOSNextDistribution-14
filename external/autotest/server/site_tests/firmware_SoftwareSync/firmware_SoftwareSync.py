# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import cr50_utils
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest
from autotest_lib.server.cros import vboot_constants as vboot


class firmware_SoftwareSync(FirmwareTest):
    """
    Servo based EC software sync test.
    """
    version = 1

    def initialize(self, host, cmdline_args, dev_mode=False):
        # This test tries to corrupt EC firmware. Should disable EC WP.
        super(firmware_SoftwareSync, self).initialize(host, cmdline_args,
                                                      ec_wp=False)

        # Don't bother if there is no Chrome EC.
        if not self.check_ec_capability():
            raise error.TestNAError("Nothing needs to be tested on this device")

        if self._no_ec_sync:
            raise error.TestNAError(
                    "User selected to disable EC software sync")

        # In order to test software sync, it must be enabled.
        self.clear_set_gbb_flags(vboot.GBB_FLAG_DISABLE_EC_SOFTWARE_SYNC, 0)
        self.backup_firmware()
        self.switcher.setup_mode('dev' if dev_mode else 'normal')
        self.setup_usbkey(usbkey=False)
        self.setup_rw_boot()
        self.dev_mode = dev_mode

        if self.ec.check_feature('EC_FEATURE_EFS2'):
            self.original_ccd_level = self.cr50.get_ccd_level()

            # CCD needs to be open for 'ec_comm corrupt' run.
            self.fast_ccd_open(reset_ccd=False, dev_mode=dev_mode)
            logging.info("CCD opened.")
        else:
            self.original_ccd_level = None

        # Check for cr50 support on a device and get the boot mode. It should
        # be NORMAL at this point, even if EFS2 is not supported.
        if hasattr(self, 'cr50'):
            res = cr50_utils.GSCTool(host, ['-a', '-g']).stdout.strip()
            if 'NORMAL' in res:
                pass
            elif 'error' in res.lower():
                raise error.TestFail('TPM Vendor command GET_BOOT_MODE failed:'
                                    ' %r' % res)
            else:
                raise error.TestFail('GET_BOOT_MODE did not return NORMAL:'
                                     ' %r' % res)

    def cleanup(self):
        try:
            if self.is_firmware_saved():
                self.restore_firmware()
        except Exception as e:
            logging.error("Caught exception: %s", str(e))

        try:
            if self.original_ccd_level:
                self.cr50.set_ccd_level(self.original_ccd_level)
        except Exception as e:
            logging.error("Failed to restore ccd to %r: %s",
                          self.original_ccd_level, str(e))

        super(firmware_SoftwareSync, self).cleanup()

    def record_hash(self):
        """Record current EC hash."""
        self._ec_hash = self.faft_client.ec.get_active_hash()
        logging.info("Stored EC hash: %s", self._ec_hash)

    def corrupt_active_rw(self):
        """Corrupt the active RW portion."""
        section = 'rw'
        try:
            if self.servo.get_ec_active_copy() == 'RW_B':
                section = 'rw_b'
        except error.TestFail:
            # Skip the failure, as ec_active_copy is new.
            # TODO(waihong): Remove this except clause.
            pass
        logging.info("Corrupt the EC section: %s", section)
        self.faft_client.ec.corrupt_body(section)

    def software_sync_checker(self):
        """Check EC firmware is restored by software sync."""
        ec_hash = self.faft_client.ec.get_active_hash()
        logging.info("Current EC hash: %s", ec_hash)
        if self._ec_hash != ec_hash:
            return False
        return self.checkers.ec_act_copy_checker('RW')

    def wait_software_sync_and_boot(self):
        """Wait for software sync to update EC."""
        if self.dev_mode:
            time.sleep(self.faft_config.software_sync_update +
                       self.faft_config.firmware_screen)
            self.servo.ctrl_d()
        else:
            time.sleep(self.faft_config.software_sync_update)

    def run_test_corrupt_ec_rw(self):
        """Runs a single iteration of the test."""
        logging.info("Corrupt EC firmware RW body.")
        self.check_state((self.checkers.ec_act_copy_checker, 'RW'))
        self.record_hash()
        self.corrupt_active_rw()
        logging.info("Reboot AP, check EC hash, and software sync it.")
        self.switcher.simple_reboot(reboot_type='warm')
        self.wait_software_sync_and_boot()
        self.switcher.wait_for_client()

        logging.info("Expect EC in RW and RW is restored.")
        self.check_state(self.software_sync_checker)

    def run_test_corrupt_hash_in_cr50(self):
        """Run the test corrupting ECRW hash in CR50."""

        self.check_state((self.checkers.ec_act_copy_checker, 'RW'))
        self.record_hash()

        logging.info('Corrupt ECRW hashcode in TPM kernel NV index.')
        ec_corrupt_cmd = 'ec_comm corrupt'
        self.cr50.send_command(ec_corrupt_cmd)

        try:
            # Reboot EC but keep AP off, so that it can earn a time to check
            # if boot_mode is NO_BOOT and EC is in RO.
            logging.info('Reset EC with AP off.')
            self.ec.reboot('ap-off')
            time.sleep(5)

            # The boot mode should be "NO_BOOT".
            logging.info('Check the boot mode is NO_BOOT mode.')
            if not self.cr50.check_boot_mode('NO_BOOT'):
                raise error.TestFail('Boot mode is not NO_BOOT.')

            # Check if the current EC image is RO.
            logging.info('Check EC is in RO.')
            if not self.ec.check_ro_rw('RO'):
                raise error.TestFail('EC is not in RO mode.')
        finally:
            # Wake up AP by tapping power button.
            logging.info('Wake up AP.')
            self.servo.power_short_press()

        # Wait for software sync is done.
        self.wait_software_sync_and_boot()
        self.switcher.wait_for_client()

        # The boot mode should be "NORMAL".
        logging.info('Check the boot mode is NORMAL mode.')
        if not self.cr50.check_boot_mode('NORMAL'):
            logging.warning('You may want to run %r in cr50 console to uncorrupt'
                         ' EC hash.', ec_corrupt_cmd)
            raise error.TestFail('Boot mode is not NORMAL.')

        logging.info('Expect EC in RW and RW is restored.')
        self.check_state(self.software_sync_checker)

    def run_once(self):
        """Entry point of this test."""
        self.run_test_corrupt_ec_rw()

        if self.ec.check_feature('EC_FEATURE_EFS2'):
            self.run_test_corrupt_hash_in_cr50()
