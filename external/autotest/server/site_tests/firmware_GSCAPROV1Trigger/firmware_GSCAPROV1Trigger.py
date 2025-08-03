# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import re
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import cr50_utils
from autotest_lib.server.cros.faft.cr50_test import Cr50Test


class firmware_GSCAPROV1Trigger(Cr50Test):
    """Verify GSC response after triggering AP RO V1 verification."""
    version = 1

    # This only verifies V1 output right now.
    TEST_AP_RO_VER = 1

    # DBG image has to be able to set the AP RO hash with the board id set.
    MIN_DBG_VER = '1.6.100'

    VERIFICATION_PASSED = 1
    VERIFICATION_FAILED = 2

    DIGEST_RE = r' digest ([0-9a-f]{64})'
    CALCULATED_DIGEST_RE = 'Calculated' + DIGEST_RE
    STORED_DIGEST_RE = 'Stored' + DIGEST_RE

    def initialize(self, host, cmdline_args, full_args={}):
        """Initialize servo"""
        self.ran_test = False
        super(firmware_GSCAPROV1Trigger,
              self).initialize(host,
                               cmdline_args,
                               full_args,
                               restore_cr50_image=True)
        if not self.cr50.ap_ro_version_is_supported(self.TEST_AP_RO_VER):
            raise error.TestNAError('GSC does not support AP RO v%s' %
                                    self.TEST_AP_RO_VER)

        dbg_ver = cr50_utils.InstallImage(self.host,
                                          self.get_saved_dbg_image_path(),
                                          '/tmp/cr50.bin')[1][1]
        if cr50_utils.GetNewestVersion(dbg_ver,
                                       self.MIN_DBG_VER) == self.MIN_DBG_VER:
            raise error.TestNAError('Update DBG image to 6.100 or newer.')

    def update_to_dbg_and_clear_hash(self):
        """Clear the Hash."""
        # Make sure the AP is up before trying to update.
        self.recover_dut()
        self._retry_cr50_update(self._dbg_image_path, 3, False)
        self.cr50.send_command('ap_ro_info erase')
        time.sleep(3)
        ap_ro_info = self.cr50.get_ap_ro_info()
        logging.info(ap_ro_info)
        if ap_ro_info['hash']:
            raise error.TestError('Could not erase hash')

    def after_run_once(self):
        """Reboot cr50 to recover the dut."""
        try:
            self.recover_dut()
        finally:
            super(firmware_GSCAPROV1Trigger, self).after_run_once()

    def set_hash(self):
        """Set the Hash."""
        self.recover_dut()
        result = self.host.run('ap_ro_hash.py -v True GBB')
        logging.info(result)
        time.sleep(3)
        ap_ro_info = self.cr50.get_ap_ro_info()
        logging.info(ap_ro_info)
        if not ap_ro_info['hash']:
            raise error.TestError('Could not set hash %r' % result)

    def rollback_to_release_image(self):
        """Update to the release image."""
        self._retry_cr50_update(self.get_saved_cr50_original_path(),
                                3,
                                rollback=True)
        logging.info(self.cr50.get_ap_ro_info())

    def cleanup(self):
        """Clear the AP RO hash."""
        try:
            if not self.ran_test:
                return
            logging.info('Cleanup')
            self.recover_dut()
            self.update_to_dbg_and_clear_hash()
            self.rollback_to_release_image()
        finally:
            super(firmware_GSCAPROV1Trigger, self).cleanup()

    def recover_dut(self):
        """Reboot gsc to recover the dut."""
        logging.info('Recover DUT')
        ap_ro_info = self.cr50.get_ap_ro_info()
        logging.info(ap_ro_info)
        if ap_ro_info['result'] != self.VERIFICATION_FAILED:
            self._try_to_bring_dut_up()
            return
        time.sleep(3)
        self.cr50.send_command('ccd testlab open')
        time.sleep(3)
        self.cr50.reboot()
        time.sleep(self.faft_config.delay_reboot_to_ping)
        logging.info(self.cr50.get_ap_ro_info())
        self._try_to_bring_dut_up()
        self.cr50.send_command('ccd testlab open')

    def trigger_verification(self):
        """Trigger verification."""
        try:
            self.recover_dut()
            result = self.host.run('gsctool -aB start',
                                   ignore_timeout=True,
                                   ignore_status=True,
                                   timeout=20)
            logging.info(result)
        finally:
            time.sleep(5)
            ap_ro_info = self.cr50.get_ap_ro_info()
            logging.info(ap_ro_info)
            self.hash_results.append(ap_ro_info['result'])
            self.servo.record_uart_capture()

    def run_once(self):
        """Save hash and trigger verification"""
        self.ran_test = True
        self.hash_results = []
        # The DBG image can set the hash when the board id is saved. The release
        # image can't. Set the hash with the DBG image, so the test doesn't need
        # to erase the board id. This test verifies triggering AP RO
        # verification. It's not about saving the hash.
        self.update_to_dbg_and_clear_hash()
        self.set_hash()
        self.rollback_to_release_image()
        # CCD has to be open to trigger verification.
        self.fast_ccd_open(True)

        # Trigger verification multiple times. Make sure it doesn't fail or
        # change.
        self.trigger_verification()
        self.trigger_verification()
        self.trigger_verification()
        self.trigger_verification()

        self.servo.record_uart_capture()
        cr50_uart_file = self.servo.get_uart_logfile('cr50')
        if not cr50_uart_file:
            logging.info('No cr50 uart file')
            return
        with open(cr50_uart_file, 'r') as f:
            contents = f.read()

        self.recover_dut()

        # GSC only prints calculated and stored hashes after AP RO verificaiton
        # fails. These sets will be empty if verification passed every time.
        calculated = set(re.findall(self.CALCULATED_DIGEST_RE, contents))
        stored = set(re.findall(self.STORED_DIGEST_RE, contents))
        logging.info('Stored: %r', stored)
        logging.info('Calculated: %r', calculated)
        logging.info('Results: %r', self.hash_results)

        if self.VERIFICATION_FAILED in self.hash_results:
            raise error.TestFail(
                    'Verification failed -- stored: %r calculated: %r' %
                    (stored, calculated))
        if len(calculated) > 1:
            raise error.TestFail('Multiple calculated digests %r' % calculated)
        # This shouldn't happen. Raise TestNA, so it's easy to see.
        if self.VERIFICATION_PASSED not in self.hash_results:
            raise error.TestNAError(
                    'Verification Not Run -- stored: %r calculated: %r' %
                    (stored, calculated))

        # TODO(b/218705748): change the hash and verify verification fails.
