# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_ECSystemLocked(FirmwareTest):
    """
    Ensure that CONFIG_SYSTEM_UNLOCKED is not set.
    """
    version = 1

    def run_once(self):
        """Runs a single iteration of the test."""
        if not self.check_ec_capability():
            raise error.TestNAError(
                    "Nothing needs to be tested on this device")

        self.set_ec_write_protect_and_reboot(True)

        logging.info("Querying sysinfo.")
        verdict = self.ec.send_command_get_output("sysinfo",
                                                  ["Flags:\s+([^\s]+)\s*.*$"])

        if len(verdict) > 0 and len(verdict[0]) > 1:
            if verdict[0][1] != 'locked':
                raise error.TestFail(
                        "Device is not locked, sysinfo returned %s" %
                        verdict[0][0])
        else:
            raise error.TestFail("Could not parse sysinfo")
