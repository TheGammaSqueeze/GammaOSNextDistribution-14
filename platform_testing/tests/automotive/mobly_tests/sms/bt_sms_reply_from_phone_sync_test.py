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
 Test to verify Reply sms sync in IVI device when replied from phone

 Steps include:
        1) Precall state check on IVI and phone devices.
        2) Send sms to paired phone from unpaired phone
        3) Reply to the sms from paired phone
        4) Verify the reply sms sync in IVI device
"""
from utilities import constants
from utilities.main_utils import common_main
from utilities.common_utils import CommonUtils
from bluetooth_sms_test import bluetooth_sms_base_test
from mobly.controllers import android_device

class SMSReplyFromPhoneSync(bluetooth_sms_base_test.BluetoothSMSBaseTest):

    def setup_class(self):
        super().setup_class()
        self.common_utils = CommonUtils(self.target, self.discoverer)

    def setup_test(self):

        # pair the devices
        self.bt_utils.pair_primary_to_secondary()

    def test_reply_from_phone_sms_sync(self):

        # wait for user permissions popup & give contacts and sms permissions
        self.call_utils.wait_with_log(20)
        self.common_utils.click_on_ui_element_with_text('Allow')

        # send a new sms
        target_phone_number = self.target.mbs.getPhoneNumber()
        self.phone_notpaired.mbs.sendSms(target_phone_number,constants.SMS_REPLY_TEXT)
        self.call_utils.wait_with_log(10)
        # Verify the new UNREAD sms in IVI device
        self.call_utils.open_sms_app()
        self.call_utils.verify_sms_app_unread_message(True)

        # REPLY to the message on paired phone
        self.call_utils.open_notification_on_phone(self.target)
        self.call_utils.wait_with_log(3)
        self.target.mbs.clickUIElementWithText(constants.REPLY_SMS)

        # Verify the SYNC Reply sms in IVI device
        self.call_utils.press_home()
        self.call_utils.open_sms_app()
        self.call_utils.verify_sms_app_unread_message(False)
        self.call_utils.verify_sms_preview_text(True, constants.REPLY_SMS)

    def teardown_test(self):
         # Go to home screen
         self.call_utils.press_home()
         super().teardown_test()

if __name__ == '__main__':
  common_main()