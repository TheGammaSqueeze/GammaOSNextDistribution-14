# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.cr50_test import Cr50Test


class firmware_Cr50Password(Cr50Test):
    """Verify cr50 set password."""
    version = 1
    NEW_PASSWORD = 'robot'


    def run_once(self):
        """Check we can set the cr50 password."""
        # Make sure to enable testlab mode, so we can guarantee the password
        # can be cleared.
        self.fast_ccd_open(enable_testlab=True)
        self.cr50.ccd_reset()

        # Set the password.
        self.set_ccd_password(self.CCD_PASSWORD)
        if self.cr50.password_is_reset():
            raise error.TestFail('Failed to set password')

        # Test 'ccd reset' clears the password.
        self.cr50.ccd_reset()
        if not self.cr50.password_is_reset():
            raise error.TestFail('ccd reset did not clear the password')
        # Set OpenFromUSB to IfOpened, so the test will only be able to open
        # ccd with a console command if the password is set. This is cleared
        # in Cr50Test cleanup.
        self.cr50.set_cap('OpenFromUSB', 'IfOpened')

        # Set the password again while cr50 is open.
        self.set_ccd_password(self.CCD_PASSWORD)
        if self.cr50.password_is_reset():
            raise error.TestFail('Failed to set password')

        # The password can't be changed once it's set.
        # It needs to be cleared first.
        self.set_ccd_password(self.NEW_PASSWORD, expect_error=True)

        self.cr50.reboot()
        if self.cr50.password_is_reset():
            raise error.TestFail('Password cleared after reboot')

        # Verify ccd can't be opened with the wrong password.
        try:
            self.cr50.set_ccd_level('open', self.NEW_PASSWORD)
            raise error.TestFail('Opened ccd with incorrect password')
        except error.TestFail as e:
            logging.info('Cr50 successfully rejected ccd open')

        # Verify ccd can be opened with the correct password.
        self.cr50.set_ccd_level('open', self.CCD_PASSWORD)

        self.cr50.set_ccd_level('lock')
        # The password can't be cleared while the console is locked.
        self.set_ccd_password('clear:' + self.CCD_PASSWORD, expect_error=True)

        if self.cr50.unlock_is_supported():
            self.cr50.send_command('ccd unlock ' + self.CCD_PASSWORD)
            # The password can be cleared while the console is unlocked.
            self.set_ccd_password('clear:' + self.CCD_PASSWORD)

            # Open the console, set the password again.
            self.cr50.send_command('ccd testlab open')
            self.set_ccd_password(self.CCD_PASSWORD)
        else:
            # Open the console.
            self.cr50.send_command('ccd testlab open')

        # The password can't be cleared using the wrong password.
        self.set_ccd_password('clear:' + self.CCD_PASSWORD.lower(),
                              expect_error=True)
        # The password can be cleared using the correct password.
        self.set_ccd_password('clear:' + self.CCD_PASSWORD)
        if not self.cr50.password_is_reset():
            raise error.TestFail('Failed to clear password')

        # The password can be set to anything when there isn't one set.
        self.set_ccd_password(self.NEW_PASSWORD)
        if self.cr50.password_is_reset():
            raise error.TestFail('Failed to set password')


        self.cr50.send_command('ccd testlab open')
        self.cr50.ccd_reset()

        if not self.cr50.unlock_is_supported():
            return

        # Run through the same steps when the password was set with the console
        # unlocked.

        self.host.run('gsctool -a -U')

        # Set the password when the console is unlocked.
        self.set_ccd_password(self.CCD_PASSWORD)

        self.cr50.set_ccd_level('lock')
        # The password can't be cleared while the console is locked.
        self.set_ccd_password('clear:' + self.CCD_PASSWORD, expect_error=True)

        # Unlock the console.
        self.ccd_unlock_from_ap(self.CCD_PASSWORD)
        # The password can be cleared while the console is unlocked.
        self.set_ccd_password('clear:' + self.CCD_PASSWORD)
        # Set the password again when the console is unlocked.
        self.set_ccd_password(self.CCD_PASSWORD)

        self.cr50.send_command('ccd testlab open')
        # The password can be cleared when the console is open.
        self.set_ccd_password('clear:' + self.CCD_PASSWORD)
