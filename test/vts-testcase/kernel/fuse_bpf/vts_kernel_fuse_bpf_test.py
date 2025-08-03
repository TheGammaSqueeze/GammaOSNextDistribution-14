#!/usr/bin/env python
#
# Copyright (C) 2022 The Android Open Source Project
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
import time
import unittest

from vts.testcases.kernel.utils import adb
from vts.testcases.vndk import utils

class VtsKernelFuseBpfTest(unittest.TestCase):

    def setUp(self):
        serial_number = os.environ.get("ANDROID_SERIAL")
        self.assertTrue(serial_number, "$ANDROID_SERIAL is empty.")
        self.dut = utils.AndroidDevice(serial_number)
        self.adb = adb.ADB(serial_number)

    def testFuseBpfEnabled(self):
        out_api, err, return_code = self.dut.Execute("getprop ro.vendor.api_level")
        first_api_level = 0
        try:
            first_api_level = int(out_api)
        except:
            pass
        out_running, err, return_code = self.dut.Execute("getprop ro.fuse.bpf.is_running")
        self.assertTrue(first_api_level < 34 or out_running.strip() == "true",
                           "fuse-bpf is disabled")


if __name__ == "__main__":
    # Setting verbosity is required to generate output that the TradeFed test
    # runner can parse.
    unittest.main(verbosity=3)
