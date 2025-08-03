"""
  Copyright (C) 2023 The Android Open Source Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.



  Test Steps:
  (0. Flash device)
  1. Verify by default BT should be ON always
  2. BluetoothManagerService: Startup: Bluetooth persisted state is ON

"""

from mobly import asserts
from utilities.main_utils import common_main
from bluetooth_test import bluetooth_base_test


class BluetoothDefaultStateTest(bluetooth_base_test.BluetoothBaseTest):

    def test_bluetooth_default_state(self):
        # Confirm that the bluetooth state is ON
        asserts.assert_true(
            self.discoverer.mbs.btIsEnabled(),
            "Expected bluetooth to be enabled by default, but it was not.")

    def teardown_test(self):
        # Default state test should still disable bluetooth after checking default state.
        self.discoverer.mbs.btDisable()


if __name__ == '__main__':
    common_main()
