# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import cr50_utils
from autotest_lib.server.cros.faft.cr50_test import Cr50Test


class firmware_Cr50FIPSDS(Cr50Test):
    """
    Verify cr50 fips works after coming out of deep sleep.
    """
    version = 1

    def apshutdown(self):
        """Shutdown the AP and give cr50 enough time to enter deep sleep."""
        self.cr50.ccd_disable()
        self.set_ap_off_power_mode('shutdown')
        self.cr50.clear_deep_sleep_count()
        time.sleep(30)

    def check_ds_resume(self):
        """Check the system resumed ok."""

        if not self.cr50.fips_crypto_allowed():
            raise error.TestFail('Crypto not allowed after deep sleep')
        # Make sure the EC jumped to RW. This could catch ec-efs issues.
        logging.info(
                self.ec.send_command_get_output('sysinfo', ['Jumped: yes']))
        if not self.cr50.get_deep_sleep_count():
            raise error.TestError('Cr50 did not enter deep sleep')
        # Make sure the DUT fully booted and is sshable.
        logging.info('Running %r', cr50_utils.GetRunningVersion(self.host))

    def run_once(self, host):
        """Verify FIPS after deep sleep."""
        if not self.cr50.has_command('fips'):
            raise error.TestNAError('Cr50 does not support fips')

        # Verify EC sysjump works on deep sleep resume.
        self.apshutdown()
        self.ec.reboot()
        time.sleep(7)
        self.check_ds_resume()

        # Verify the AP can boot after resume without EC reset.
        self.apshutdown()
        self.servo.power_normal_press()
        time.sleep(7)
        self.check_ds_resume()
