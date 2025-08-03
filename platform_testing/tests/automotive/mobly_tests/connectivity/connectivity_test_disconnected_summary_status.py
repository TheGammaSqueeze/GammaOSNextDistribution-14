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
    1. Navigate to the Bluetooth settings page
    2. Disconnect - Connect Mobile Device via Layer1 - via Bluetooth Button
    3. Tap Device to see DisconnectedStatus in Layer2
    4. Reconnect - Via layer 1
    5. Tap device to see Connected status in Layer2

    "Layer Two" represents the device-specific screen (the screen you see when clicking the device in the bluetooth settings page.

"""

import logging

from bluetooth_test import bluetooth_base_test
from mobly import asserts

from utilities import constants
from utilities.main_utils import common_main

MOBILE_DEVICE_NAME = 'target'
AUTOMOTIVE_DEVICE_NAME = 'discoverer'


class BluetoothConnectionStatusOnLevelTwo(bluetooth_base_test.BluetoothBaseTest):

    def setup_test(self):
        # Enables BT on both devices.
        super().setup_test()
        # Set Bluetooth name on target device.
        self.target.mbs.btSetName(MOBILE_DEVICE_NAME)

        self.bt_utils.pair_primary_to_secondary()
        self.call_utils.wait_with_log(constants.DEVICE_CONNECT_WAIT_TIME)

    def test_connection_status_displayed_on_device_screen(self):
        # Open bluetooth settings.
        self.call_utils.open_bluetooth_settings()
        self.call_utils.wait_with_log(2)

        # Find the target device and disconnect it on the Level One page
        self.call_utils.press_bluetooth_toggle_on_device(MOBILE_DEVICE_NAME)
        self.call_utils.wait_with_log(2)

        # Click on the target device.
        self.call_utils.press_device_entry_on_list_of_paired_devices(MOBILE_DEVICE_NAME)
        self.call_utils.wait_with_log(2)

        # Confirm that target device displays "disconnected"
        summary = self.call_utils.get_device_summary()
        logging.info("Summary received reads: %s" % summary)
        asserts.assert_true((constants.DISCONNECTED_SUMMARY_STATUS in summary),
                            "Expected summary  to contain %s, but instead summary reads: %s"
                            % (constants.DISCONNECTED_SUMMARY_STATUS, summary))


if __name__ == '__main__':
    common_main()
