# Copyright (c) 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest
from autotest_lib.server.cros.faft.firmware_test import ConnectionError


class firmware_CorruptRecoveryCache(FirmwareTest):
    """
    This test corrupts RECOVERY_MRC_CACHE and makes sure the DUT recreates the
    cache and boots into recovery. This only applies to intel chips.

    The expected behavior is that if the RECOVERY_MRC_CACHE is corrupted then
    it will be recreated and still boot into recovery mode.
    """
    version = 1
    NEEDS_SERVO_USB = True

    REBUILD_CACHE_MSG = "MRC: cache data 'RECOVERY_MRC_CACHE' needs update."
    RECOVERY_CACHE_SECTION = 'RECOVERY_MRC_CACHE'
    FIRMWARE_LOG_CMD = 'cbmem -1' + ' | grep ' + REBUILD_CACHE_MSG[:3]

    def initialize(self, host, cmdline_args, dev_mode=False):
        super(firmware_CorruptRecoveryCache, self).initialize(
                host, cmdline_args)
        self.backup_firmware()
        self.switcher.setup_mode('dev' if dev_mode else 'normal',
                                 allow_gbb_force=True)
        self.setup_usbkey(usbkey=True, host=False)

    def cleanup(self):
        try:
            if self.is_firmware_saved():
                self.restore_firmware()
        except ConnectionError:
            logging.error("ERROR: DUT did not come up.  Need to cleanup!")
        super(firmware_CorruptRecoveryCache, self).cleanup()

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

    def check_cache_rebuilt(self):
        """Checks the firmware log to ensure that the recovery cache was rebuilt
        successfully.

        @return True if cache rebuilt otherwise false
        """
        logging.info("Checking if cache was rebuilt.")
        return self.faft_client.system.run_shell_command_check_output(
                self.FIRMWARE_LOG_CMD, self.REBUILD_CACHE_MSG)

    def boot_to_recovery(self):
        """Boots the device into recovery mode."""
        logging.info('Reboot into Recovery...')
        self.switcher.reboot_to_mode(to_mode='rec')

        self.check_state((self.checkers.crossystem_checker, {
                'mainfw_type': 'recovery'
        }))

    def run_once(self):
        """Runs a single iteration of the test."""
        if not self.cache_exist():
            raise error.TestNAError('No RECOVERY_MRC_CACHE was found on DUT.')

        self.faft_client.bios.corrupt_mrc_cache()
        self.boot_to_recovery()

        if not self.check_cache_rebuilt():
            raise error.TestFail('Recovery Cache was not rebuilt.')

        logging.info('Reboot out of Recovery')
        self.switcher.simple_reboot()
