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
1. Launch Dialer -Settings -  Turn Active Call ON
https://screenshot.googleplex.com/3QDmSzeiis4TH92
2. Switch to Launcher or Maps or any page
3. Call to Paired Device and Answer an incoming call
4. Verify the  dialer - dial in progress page displayed in Full view when user tap to Answer phone call
Video Demo: https://drive.google.com/file/d/15HK77k47j8oDonbjY0IegoJnJESEcvwM/view?usp=sharing&resourcekey=0-CUtdhL4Y0z_uwnjl2QMjew
"""

from bluetooth_sms_test import bluetooth_sms_base_test
import logging
from mobly import asserts
from utilities import phone_device_utils
from utilities import constants
from utilities.main_utils import common_main


class DialerActiveCallOn(bluetooth_sms_base_test.BluetoothSMSBaseTest):

    def setup_class(self):
        super().setup_class()
        self.phone_utils = (phone_device_utils.PhoneDeviceUtils(self.phone_notpaired))

    def setup_test(self):
        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()

    def test_active_call_on(self):

        # Navigate to dialer settings
        self.call_utils.open_phone_app()
        self.call_utils.open_dialer_settings()
        # Enable Active Call
        self.call_utils.press_active_call_toggle()
        asserts.assert_true(self.call_utils.is_active_call_enabled(),
                            "Expected Active Call to be enabled after pressing the toggle.")

        # Call the paired phone device (from an unpaired phone) and answer the call.
        # call from the unpaired phone to the paired phone
        callee_number = self.target.mbs.getPhoneNumber()
        self.phone_utils.call_number_from_home_screen(callee_number)
        self.call_utils.press_home()

        # Receive and answer the call
        self.call_utils.wait_with_log(10)
        self.discoverer.mbs.clickUIElementWithText(constants.ACCEPT_CALL_TEXT)
        self.call_utils.wait_with_log(2)
        # Verify that the in-progress call is displayed in full-screen view.
        asserts.assert_true(
            self.call_utils.is_active_call_ongoing_full_screen(),
            "After enabling Active Call, expected received call to be fullscreen, "
            "but it was not (i.e., no Active Call Control Bar found)"
        )

    def teardown_test(self):
        # Navigate to dialer settings
        self.call_utils.end_call_using_adb_command(self.phone_notpaired)
        self.call_utils.open_phone_app()
        self.call_utils.open_dialer_settings()
        self.call_utils.press_active_call_toggle()
        super().teardown_test()


if __name__ == '__main__':
    common_main()