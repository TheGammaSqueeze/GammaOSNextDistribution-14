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
"""

Test steps:
1 Tap on Phone icon from facet rail or App launcher to launch Dialer app
2. Go to Dialpad and enter some number or go to Search and type any contact name
3. Go to Settings > More > Bluetooth and disconnect the paired mobile device
4. Tap on listed paired device and reconnect the BT connection
5. Go to dialer app again and verify that its refreshed and earlier typed phone number or contact name is not showing
"""

from bluetooth_test import bluetooth_base_test
import logging
from mobly import asserts
from utilities import constants
from utilities.main_utils import common_main


class DialerPageRefresh(bluetooth_base_test.BluetoothBaseTest):



    def setup_test(self):
        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()

    def test_dialer_page_refresh(self):

        test_number = constants.DIALER_THREE_DIGIT_NUMBER

        # Launcher the dialer app and input a number into the dial pad
        self.call_utils.open_phone_app()
        self.call_utils.dial_a_number(test_number)

        # Navigate to the bluetooth settings
        self.discoverer.mbs.pressHome()
        self.call_utils.open_bluetooth_settings()

        # Disconnect and reconnect the device's bluetooth
        self.call_utils.press_bluetooth_toggle_on_device(self.target.mbs.btGetName())
        self.call_utils.wait_with_log(constants.BT_DEFAULT_TIMEOUT)
        self.call_utils.press_bluetooth_toggle_on_device(self.target.mbs.btGetName())

        # Navigate back to the dial pad and confirm that there is no entered number
        self.discoverer.mbs.pressHome()
        self.call_utils.open_phone_app()
        self.discoverer.mbs.openDialPad()
        stored_number = self.discoverer.mbs.getNumberInDialPad()

        asserts.assert_true(stored_number == constants.DEFAULT_DIAL_PAD_ENTRY,
                            "Expected \'%s\' in dial pad after reconnection, but found %s"
                            % (constants.DEFAULT_DIAL_PAD_ENTRY,
                               str(stored_number)))


if __name__ == '__main__':
    common_main()