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
Procedure:
1. Make a phone call via Phone - Remote Phone call answer phone call
2.  Launch  Dialer
Verify: Dialer page launched
"""

from bluetooth_sms_test import bluetooth_sms_base_test
import logging
from utilities import constants
from utilities import phone_device_utils
from utilities.main_utils import common_main

from mobly import asserts

class BTDialerPhoneCard(bluetooth_sms_base_test.BluetoothSMSBaseTest):

    def setup_class(self):
        super().setup_class()
        self.phone_utils = (phone_device_utils.PhoneDeviceUtils(self.target))

    def setup_test(self):
        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()


    def test_launch_dialer_from_phone_card(self):
        # setup phones
        # call from the unpaired phone to the paired phone
        callee_number = self.phone_notpaired.mbs.getPhoneNumber()
        self.phone_utils.call_number_from_home_screen(callee_number)
        self.call_utils.wait_with_log(constants.DEFAULT_WAIT_TIME_FIFTEEN_SECS)

        # accept the call (on the unpaired phone)
        self.phone_notpaired.mbs.clickUIElementWithText(constants.ACCEPT_CALL_TEXT)
        self.call_utils.wait_with_log(constants.WAIT_FOR_LOAD)

        # Confirm that a call is ongoing
        asserts.assert_true(
            self.discoverer.mbs.isOngoingCallDisplayedOnHome(),
            "Expected no ongoing call to be displayed on home after Declining call, but found one."
        )

        # Launch the dialer from the phone card
        self.call_utils.press_home()
        self.call_utils.press_dialer_button_on_phone_card()
        # Verify that the in-progress call is displayed in full-screen view.
        asserts.assert_true(
            self.call_utils.is_active_call_ongoing_full_screen(),
            "After enabling Active Call, expected received call to be fullscreen, "
            "but it was not (i.e., no Active Call Control Bar found)"
        )

        self.call_utils.end_call()

    def teardown_test(self):
        # End call if test failed
        self.call_utils.end_call_using_adb_command(self.phone_notpaired)
        super().teardown_test()

if __name__ == '__main__':
    common_main()