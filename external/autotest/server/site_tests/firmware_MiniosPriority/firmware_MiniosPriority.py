# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_MiniosPriority(FirmwareTest):
    """
    Servo based MiniOS boot priority test.

    This test requires the device support MiniOS. At runtime, this test uses the
    crossystem tool to modify the MiniOS priority and try to boot MiniOS from
    firmware manual recovery screen. After booting, this test will verify if the
    device successfully boot to the MiniOS. This test does not cover verifying
    if the device successfully boots to the specified partition.
    """
    version = 1

    def initialize(self, host, cmdline_args, minios_priority):
        super(firmware_MiniosPriority, self).initialize(host, cmdline_args)

        self.test_skipped = True
        if not self.menu_switcher:
            raise error.TestNAError('Test skipped for menuless UI')
        if not self.faft_config.chrome_ec:
            raise error.TestNAError('Cannot check power state without EC')
        if not self.faft_config.minios_enabled:
            raise error.TestNAError('MiniOS is not enabled for this board')
        self.test_skipped = False

        self.host = host
        self.switcher.setup_mode('normal')
        self.setup_usbkey(usbkey=False)
        self.minios_priority = minios_priority
        self.restored_priority = self.faft_client.system.get_minios_priority()

    def cleanup(self):
        if not self.test_skipped:
            try:
                self.switcher.leave_minios()
                self.faft_client.system.set_minios_priority(
                        self.restored_priority)
            except Exception as e:
                logging.error('Caught exception: %s', str(e))
        super(firmware_MiniosPriority, self).cleanup()

    def run_once(self):
        """Run a single iteration of the test."""
        self.switcher.launch_minios(self.minios_priority)
        self.check_state(self.checkers.minios_checker)
