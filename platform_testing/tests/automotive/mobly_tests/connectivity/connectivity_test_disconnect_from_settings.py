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
  1. Navigate to bluetooth settings and press the Bluetooth toggle (on device listing)
  to disconnect.
  2.  Confirm that Bluetooth, Media, and Audio are disabled
  3. Press the bluetooth toggle on the device listing to reconnect
  4. Confirm that Bluetooth, Media, and Audio are enabled

"""

from mobly import asserts

from utilities import constants
from utilities.main_utils import common_main
from bluetooth_test import bluetooth_base_test


class BluetoothDisconnectFromSettingsTest(bluetooth_base_test.BluetoothBaseTest):

    def setup_test(self):
        super().setup_test()
        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()

    def test_disconnect_from_settings(self):
        # Allow time for connection to fully sync.
        self.call_utils.wait_with_log(constants.WAIT_THIRTY_SECONDS)

        # Navigate to Bluetooth Settings
        self.call_utils.open_bluetooth_settings()
        self.call_utils.wait_with_log(constants.WAIT_TWO_SECONDS)
        # Press the Bluetooth toggle button to disconnect
        # (Recall that the toggle is the leftmost of the three buttons listed with the name)
        self.call_utils.press_bluetooth_toggle_on_device(self.target.mbs.btGetName())
        self.call_utils.wait_with_log(constants.WAIT_TWO_SECONDS)

        # Confirm that Bluetooth, Media, and Audio are disabled
        asserts.assert_true(
            self.call_utils.validate_three_preference_buttons(False),
            "Expected bluetooth, media, and phone buttons to be disabled after disconnection.")

        # Press the Bluetooth button to reconnect
        self.call_utils.press_bluetooth_toggle_on_device(self.target.mbs.btGetName())
        self.call_utils.wait_with_log(constants.SYNC_WAIT_TIME)

        # Confirm that Bluetooth, Media, and Audio are enabled
        asserts.assert_true(
            self.call_utils.validate_three_preference_buttons(True),
            "Expected bluetooth, media, and phone buttons to be enabled after reconnection.")


if __name__ == '__main__':
    # Take test args
    common_main()
