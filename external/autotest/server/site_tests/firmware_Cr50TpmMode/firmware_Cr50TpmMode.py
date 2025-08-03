# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
import logging

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import cr50_utils
from autotest_lib.server.cros.faft.cr50_test import Cr50Test


class firmware_Cr50TpmMode(Cr50Test):
    """Verify TPM disabling and getting back enabled after reset."""
    version = 1

    def init_tpm_mode(self):
        """Reset the device."""
        logging.info('Reset')
        self.host.reset_via_servo()
        self.switcher.wait_for_client()

    def cleanup(self):
        """Initialize TPM mode by resetting CR50"""
        try:
            self.init_tpm_mode()
        finally:
            super(firmware_Cr50TpmMode, self).cleanup()

    def get_tpm_mode(self, long_opt):
        """Query the current TPM mode.

        Args:
            long_opt: Boolean to decide whether to use long opt
                      for gsctool command.
        """
        opt_text = '--tpm_mode' if long_opt else '-m'
        return cr50_utils.GSCTool(self.host, ['-a', opt_text]).stdout.strip()

    def set_tpm_mode(self, disable_tpm, long_opt):
        """Disable or Enable TPM mode.

        Args:
            disable_tpm: Disable TPM if True.
                         Enable (or Confirm Enabling) otherwise.
            long_opt: Boolean to decide whether to use long opt
                      for gsctool command.

        """
        mode_param = 'disable' if disable_tpm else 'enable'
        opt_text = '--tpm_mode' if long_opt else '-m'
        result = cr50_utils.GSCTool(
                self.host, ['-a', opt_text, mode_param]).stdout.strip()
        logging.info('TPM Mode: %r', result)
        return result

    def run_test_tpm_mode(self, disable_tpm, long_opt):
        """Run a test for the case of either disabling TPM or enabling.

        Args:
            disable_tpm: Disable TPM if True. Enable TPM otherwise.
            long_opt: Boolean to decide whether to use long opt
                      for gsctool command.

        Raises:
            TestFail: If test fails for unexpected TPM mode change.
        """
        self.init_tpm_mode()

        # Check if TPM is enabled through console command.
        logging.info('Get TPM Mode')
        if not self.cr50.tpm_is_enabled():
            raise error.TestFail('TPM is not enabled after reset,')

        # Check if Key Ladder is enabled.
        if self.cr50.keyladder_is_disabled():
            raise error.TestFail('Failed to restore H1 Key Ladder')

        # Check if TPM is enabled through gsctool.
        output_log = self.get_tpm_mode(long_opt)
        logging.info(output_log)
        if not 'enabled (0)' in output_log.lower():
            raise error.TestFail('Failed to read TPM mode after reset')

        # Check if CR50 responds to a TPM request.
        if self.tpm_is_responsive():
            logging.info('Checked TPM response')
        else:
            raise error.TestFail('Failed to check TPM response')

        # Change TPM Mode
        logging.info('Set TPM Mode')

        output_log = self.set_tpm_mode(disable_tpm, long_opt)
        logging.info(output_log)

        # Check the result of TPM Mode.
        if disable_tpm:
            if not 'disabled (2)' in output_log.lower():
                raise error.TestFail('Failed to disable TPM: %s' % output_log)

            # Check if TPM is disabled. The run should fail.
            if self.tpm_is_responsive():
                raise error.TestFail('TPM responded')
            else:
                logging.info('TPM did not respond')

            if not self.cr50.keyladder_is_disabled():
                raise error.TestFail('Failed to revoke H1 Key Ladder')
        else:
            if not 'enabled (1)' in output_log.lower():
                raise error.TestFail('Failed to enable TPM: %s' % output_log)

            # Check if TPM is enabled still.
            if self.tpm_is_responsive():
                logging.info('Checked TPM response')
            else:
                raise error.TestFail('Failed to check TPM response')

            # Subsequent set-TPM-mode vendor command should fail.
            try:
                output_log = self.set_tpm_mode(not disable_tpm, long_opt)
            except error.AutoservRunError:
                logging.info('Expectedly failed to disable TPM mode');
            else:
                raise error.TestFail('Unexpected result in disabling TPM mode:'
                        ' %s' % output_log)

    def run_once(self):
        """Test Disabling TPM and Enabling TPM"""
        long_opts = [True, False]

        # One iteration runs with the short opt '-m',
        # and the other runs with the long opt '--tpm_mode'
        for long_opt in long_opts:
            # Test 1. Disabling TPM
            logging.info('Disabling TPM')
            self.run_test_tpm_mode(True, long_opt)

            # Test 2. Enabling TPM
            logging.info('Enabling TPM')
            self.run_test_tpm_mode(False, long_opt)
