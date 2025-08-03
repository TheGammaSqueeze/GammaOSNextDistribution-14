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
1. Go to car Settings - Bluetooth - Select AG- Paired mobile Device - select property
2. Change Settings by toggling off ex-Turn Off 'Phone Calls'
3. Verify that Bluetooth Connection status changed from Connected to Connected (no phone)
4. Call to AG-Mobile device
Verify: Phone call will not go through Carkit
5. Launch Dialer and verify: Contacts. Recent, Favorites should not be displayed if Contacts-Settings disabled
"""

from bluetooth_test import bluetooth_base_test
import logging
from mobly import asserts
from utilities import constants
from utilities.main_utils import common_main


class BluetoothSettingsReflection(bluetooth_base_test.BluetoothBaseTest):



    def setup_test(self):
        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()

    def test_bluetooth_settings_reflected_in_dialer(self):

        # Open bluetooth settings
        self.call_utils.press_home()
        self.call_utils.open_bluetooth_settings()
        self.call_utils.wait_with_log(constants.WAIT_FOR_LOAD)
        # Disconnect phone
        self.call_utils.press_phone_toggle_on_device(self.target.debug_tag)
        self.call_utils.wait_with_log(constants.WAIT_FOR_LOAD)
        # Open dialer settings
        self.call_utils.press_home()
        self.call_utils.wait_with_log(constants.WAIT_FOR_LOAD)
        self.call_utils.open_phone_app()
        # Confirm disconnected messages
        self.call_utils.wait_with_log(constants.DEFAULT_WAIT_TIME_FIVE_SECS)
        asserts.assert_true(
            self.discoverer.mbs.hasUIElementWithText(constants.NO_PHONE_MESSAGE),
            "After disconnecting, expected Dialer screen to display a message prompting the user "
            "to connect their phone, but did not find expected text: \'%s\' "
            % constants.NO_PHONE_MESSAGE
        )

        # Confirm that Recents, Contacts, Favorites, and Dialpad are not available
        self.expect_no_label(constants.DIALER_RECENTS_LABEL)
        self.expect_no_label(constants.DIALER_CONTACTS_LABEL)
        self.expect_no_label(constants.DIALER_FAVORITES_LABEL)
        self.expect_no_label(constants.DIALER_DIALPAD_LABEL)

    def expect_no_label(self, label):
        asserts.assert_false(
            self.discoverer.mbs.hasUIElementWithText(label),
            "After disconnecting, found an element with label \'%s\' on Dialer screen, when"
            "none was expected" % label
        )


if __name__ == '__main__':
    common_main()