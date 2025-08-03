# Copyright 2023, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Tests for device_update.py"""

import sys
import unittest

from atest import device_update


class AdeviceUpdateMethodTest(unittest.TestCase):

  @unittest.skipUnless(sys.platform.startswith('linux'), 'requires Linux')
  def test_update_succeeds(self):
    adevice = device_update.AdeviceUpdateMethod(adevice_path='/bin/true')

    self.assertIsNone(adevice.update())

  @unittest.skipUnless(sys.platform.startswith('linux'), 'requires Linux')
  def test_update_fails(self):
    adevice = device_update.AdeviceUpdateMethod(adevice_path='/bin/false')

    self.assertRaises(device_update.Error, adevice.update)

  def test_dependencies_non_empty(self):
    adevice = device_update.AdeviceUpdateMethod()

    deps = adevice.dependencies()

    self.assertTrue(deps)
