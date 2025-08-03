# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.cr50_test import Cr50Test


class firmware_Cr50Unlock(Cr50Test):
    """Verify cr50 unlock."""
    version = 1


    def send_unlock_console_command(self, password=''):
        """Sent the unlock console command with the given password."""
        time.sleep(self.cr50.CCD_PASSWORD_RATE_LIMIT)
        self.cr50.send_command('ccd unlock ' + password)

    def run_once(self):
        """Check cr50 can see dev mode open works correctly"""

        if not self.cr50.unlock_is_supported():
            raise error.TestNAError('Unlock not supported')

        if not self.faft_config.has_powerbutton:
            raise error.TestNAError('Can not run test without power button')

        # Make sure testlab mode is enabled, so we can guarantee the password
        # can be cleared.
        self.fast_ccd_open(enable_testlab=True)
        self.cr50.ccd_reset()
        # Set the password
        self.set_ccd_password(self.CCD_PASSWORD)
        if self.cr50.password_is_reset():
            raise error.TestFail('Failed to set password')

        # Test the ccd password rate limit.
        self.cr50.set_ccd_level('lock')
        # Wait long enough to ensure that the failed ccd unlock command starts
        # the rate limit.
        time.sleep(5)
        self.cr50.send_command('ccd unlock ' + self.CCD_PASSWORD.lower())
        # Cr50 should reject the correct ccd unlock because of the rate limit.
        self.cr50.send_command_get_output('ccd unlock ' + self.CCD_PASSWORD,
                    ['Busy'])
        if self.cr50.get_ccd_level() == 'unlock':
            raise error.TestFail('Rate limit did not prevent unlock.')
        logging.info('Verified password rate limit.')

        # Verify unlock from the cr50 console.
        self.cr50.set_ccd_level('lock')
        self.send_unlock_console_command(self.CCD_PASSWORD)
        if self.cr50.get_ccd_level() != 'unlock':
            raise error.TestFail('Could not unlock cr50 with the password')

        self.cr50.set_ccd_level('lock')
        # Try with the lowercase version of the passsword. Make sure it doesn't
        # work.
        self.send_unlock_console_command(self.CCD_PASSWORD.lower())
        if self.cr50.get_ccd_level() != 'lock':
            raise error.TestFail('Unlocked cr50 from AP with incorrect '
                    'password')

        # Verify unlock from the AP.
        self.ccd_unlock_from_ap(self.CCD_PASSWORD)
        if self.cr50.get_ccd_level() != 'unlock':
            raise error.TestFail('Could not unlock cr50 from the AP with the '
                    'password.')

        self.cr50.set_ccd_level('lock')
        self.ccd_unlock_from_ap(self.CCD_PASSWORD.lower(), expect_error=True)
        if self.cr50.get_ccd_level() != 'lock':
            raise error.TestFail('Unlocked cr50 from AP with incorrect '
                    'password')

        # CCD needs to be unlocked to clear the password.
        self.ccd_unlock_from_ap(self.CCD_PASSWORD)
        # Clear the password which has set at the beginning of this test.
        self.set_ccd_password('clear:' + self.CCD_PASSWORD)
        if not self.cr50.password_is_reset():
            raise error.TestFail('Unable to clear password')
