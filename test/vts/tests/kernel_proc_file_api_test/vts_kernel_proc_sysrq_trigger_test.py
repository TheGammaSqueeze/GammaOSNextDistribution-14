#!/usr/bin/env python
#
# Copyright (C) 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import os
import unittest

import proc_utils as utils


class VtsKernelProcSysrqTriggerTest(unittest.TestCase):
    def setUp(self):
        """Initializes tests.

        Data file path, device, remote shell instance and temporary directory
        are initialized.
        """
        serial_number = os.environ.get("ANDROID_SERIAL")
        self.assertTrue(serial_number, "$ANDROID_SERIAL is empty.")
        self.dut = utils.AndroidDevice(serial_number)

    def testProcSysrqTrigger(self):
        filepath = "/proc/sysrq-trigger"

        # This command only performs a best effort attempt to remount all
        # filesystems. Check that it doesn't throw an error.
        self.dut.shell.Execute("echo u > %s" % filepath)

        # Reboot the device.
        self.dut.shell.Execute("echo b > %s" % filepath)
        self.assertTrue(self.dut.IsShutdown(10), "Device is still alive.")
        self.assertTrue(self.dut.WaitForBootCompletion(600))
        self.assertTrue(self.dut.Root())

if __name__ == "__main__":
    try:
        suite = unittest.TestLoader().loadTestsFromTestCase(
            VtsKernelProcSysrqTriggerTest)
        results = unittest.TextTestRunner(verbosity=2).run(suite)
    finally:
        if results.failures:
            sys.exit(1)
