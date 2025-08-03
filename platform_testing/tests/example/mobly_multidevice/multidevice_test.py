#!/usr/bin/env python3
#
# Copyright (C) 2020 The Android Open Source Project
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

import sys

from mobly import base_test
from mobly import test_runner
from mobly.controllers import android_device


class MultiDeviceTest(base_test.BaseTestClass):

  def setup_class(self):
    # Registering android_device controller module declares the test's
    # dependency on Android device hardware.
    self.ads = self.register_controller(android_device)

  def test_multidevice(self):
    # Verify 2 devices are allocated.
    assert len(self.ads) == 2, "Failed to get multiple devices"


if __name__ == '__main__':
  index = sys.argv.index('--')
  sys.argv = sys.argv[:1] + sys.argv[index + 1:]
  test_runner.main()
