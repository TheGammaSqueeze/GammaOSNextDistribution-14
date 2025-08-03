# Copyright (c) 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import common
from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest

class firmware_RecoveryStress(FirmwareTest):
    """This test checks the following FAFT hardware requirement:
      - USB stick is plugged into Servo board, not DUT
      - Recovery boot with USB stick using power_state:rec
      - Recovery boot with USB stick using power_state:rec_force_mrc
    """
    version = 1
    NEEDS_SERVO_USB = True

    REBUILD_CACHE_MSG = "MRC: cache data 'RECOVERY_MRC_CACHE' needs update."
    FIRMWARE_LOG_CMD = 'cbmem -1' + ' | grep ' + REBUILD_CACHE_MSG[:3]
    RECOVERY_CACHE_SECTION = 'RECOVERY_MRC_CACHE'

    def initialize(self, host, mode, cmdline_args):
        super(firmware_RecoveryStress, self).initialize(
                host, cmdline_args)
        self.mode = mode
        if self.mode not in ['rec', 'rec_force_mrc']:
            raise error.TestFail('Wrong mode given. Not supported for this test.')
        self.switcher.setup_mode('normal')
        self.setup_usbkey(usbkey=True, host=False)

    def check_cache_rebuilt(self):
        """Checks the firmware log to ensure that the recovery cache was rebuilt
        during recovery boot.

        @return True if cache rebuilt
        """
        logging.info('Checking if cache was rebuilt.')

        return self.faft_client.system.run_shell_command_check_output(
                self.FIRMWARE_LOG_CMD, self.REBUILD_CACHE_MSG)

    def cache_exist(self):
        """Checks the firmware log to ensure that the recovery cache exists.

        @return True if cache exists
        """
        logging.info("Checking if device has RECOVERY_MRC_CACHE")

        # If flashrom can read the section, this means it exists.
        command = ('flashrom -p host -r -i %s:/dev/null'
                   % self.RECOVERY_CACHE_SECTION)
        return self.faft_client.system.run_shell_command_check_output(
            command, 'SUCCESS')

    def run_once(self, reboot_iterations=1):
        """Running recovery boot cycles to ensure booting through
        USB successful."""

        logging.info("Check DUT is booting into USB image through recovery mode")

        if self.mode == "rec_force_mrc":
            if not self.faft_config.rec_force_mrc:
                raise error.TestNAError('DUT cannot force memory training.')

            elif not self.cache_exist():
                raise error.TestNAError('No RECOVERY_MRC_CACHE was found on DUT.')

        for i in range(reboot_iterations):
            logging.info('======== RUNNING RECOVERY BOOT ITERATION %d/%d '
                    '========', i+1, reboot_iterations)
            self.switcher.reboot_to_mode(to_mode=self.mode)
            self.check_state((self.checkers.crossystem_checker, {
                    'mainfw_type': 'recovery'
            }))
            if self.mode == "rec_force_mrc":
                if not self.check_cache_rebuilt():
                    raise error.TestFail('Recovery Cache was not rebuilt.')

        logging.info("Simple reboot to boot into internal disk")
        self.switcher.mode_aware_reboot()
