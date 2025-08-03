# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.server import test
from autotest_lib.server.hosts import cros_firmware
from autotest_lib.client.common_lib import error


class fleet_FirmwareUpdate(test.test):
    """Test to update OS bundled firmware and validate DUT is in good state."""
    version = 1

    UPDATE_CMD = "/usr/sbin/chromeos-firmwareupdate --wp=1 --mode=autoupdate"

    def update_os_bundled_firmware(self, host):
        """Update OS bundled firmware, RW only.

        Args:
          host: Target host machine to update firmware.

        raises:
          error.TestFail if update firmware cmd return with non-zero code.
        """
        logging.info("Starting update firmware on %s.", host.hostname)
        try:
            res = host.run(self.UPDATE_CMD)
        except:
            raise error.TestFail("Failed to update firmware.")

    def pre_update_validation(self, host):
        """Validate DUT is in good state before firmware update.

        Args:
          host: Target host machine to do the validation.

        raises:
          error.TestNAError if DUT is not sshable, or not come back after
                            reboot.
        """
        # Ensure the DUT is sshable before firmware update.
        if not host.is_up():
            raise error.TestNAError("DUT is down before firmware update.")

        # Ensure the DUT can reboot normally before firmware update.
        logging.info("Rebooting %s prior firmware update", host.hostname)
        try:
            host.reboot(timeout=host.BOOT_TIMEOUT, wait=True)
        except Exception as e:
            logging.error(e)
            raise error.TestNAError("DUT failed to reboot before firmware"
                                    " update.")

    def is_firmware_updated(self, host):
        """Check whether the DUT is updated to OS bundled firmware.

        Args:
          host: Target host machine to check.
        """
        model = host.get_platform()
        expected = cros_firmware._get_available_firmware(host, model)
        if not expected:
            logging.info("Couldn't get expected version based on model"
                         " info, skip firmware version check.")
        actual = host.run("crossystem fwid").stdout
        logging.debug("Expected firmware: %s, actual firmware on DUT: %s.",
                      expected, actual)
        return expected == actual

    def post_update_validation(self, host):
        """Validate DUT is good after firmware update.

        Args:
          host: Target host machine to do the validation.

        raises:
          error.TestFail if the DUT failed to pass validation.
        """
        try:
            host.reboot(timeout=host.BOOT_TIMEOUT, wait=True)
        except Exception as e:
            logging.error(e)
            raise error.TestFail("DUT didn't come back from reboot after"
                                 " firmware update.")
        if not self.is_firmware_updated(host):
            raise error.TestFail("Firmware on DUT mismatch with OS bundled"
                                 " firmware after update.")

    def run_once(self, host):
        """Main control of test steps:

        1. Pre-update validation, ensure the DUT is in good state before actual
           test to reduce flakiness.
        2. Firmware update, update os bundled firmware in RW portion.
        3. Post-update validation, test if the DUT is still in good state after
           receive firmware update.
        """
        self.pre_update_validation(host)
        # Need to wait for machine fully ready for firmware update to reduce
        # flakiness.
        time.sleep(60)
        if self.is_firmware_updated(host):
            raise error.TestNAError("Firmware version on the DUT is already"
                                    " up-to-date.")
        self.update_os_bundled_firmware(host)
        self.post_update_validation(host)
