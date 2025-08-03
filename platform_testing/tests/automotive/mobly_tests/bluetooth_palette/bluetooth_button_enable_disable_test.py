#  Copyright (C) 2023 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.


from bluetooth_test import bluetooth_base_test
from mobly import asserts

from utilities import constants
from utilities.main_utils import common_main


class BluetoothPalette(bluetooth_base_test.BluetoothBaseTest):
    """Enable and Disable Bluetooth from Bluetooth Palette."""

    def setup_test(self):
        """Setup steps before any test is executed."""
        # Pair the devices
        self.bt_utils.pair_primary_to_secondary();

    def test_enable_disable_bluetooth_button(self):
        """Tests enable and disable functionality of bluetooth."""
        self.call_utils.open_bluetooth_palette()
        self.call_utils.wait_with_log(5)
        asserts.assert_true(self.call_utils.is_bluetooth_connected(), 'Bluetooth Connected')
        self.call_utils.click_bluetooth_button()
        self.call_utils.wait_with_log(5)
        asserts.assert_false(self.call_utils.is_bluetooth_connected(), 'Bluetooth Disconnected')


if __name__ == '__main__':
    common_main()
