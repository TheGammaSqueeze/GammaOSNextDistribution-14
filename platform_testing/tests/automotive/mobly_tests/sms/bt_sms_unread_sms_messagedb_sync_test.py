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

from utilities import constants
from utilities.main_utils import common_main
from utilities.common_utils import CommonUtils
from bluetooth_sms_test import bluetooth_sms_base_test
from mobly.controllers import android_device

class SMSUnreadMessageDBSyncTest(bluetooth_sms_base_test.BluetoothSMSBaseTest):

    def setup_class(self):
        super().setup_class()
        self.common_utils = CommonUtils(self.target, self.discoverer)

    def setup_test(self):

        # pair the devices
        self.bt_utils.pair_primary_to_secondary()

        # wait for user permissions popup & give contacts and sms permissions
        self.call_utils.wait_with_log(20)
        self.common_utils.click_on_ui_element_with_text('Allow')

        # Clearing the sms from the phone
        self.call_utils.clear_sms_app(self.target)
        # Reboot Phone
        self.target.unload_snippet('mbs')
        self.call_utils.reboot_device(self.target)
        self.call_utils.wait_with_log(30)
        self.target.load_snippet('mbs', android_device.MBS_PACKAGE)
        # send a new sms
        target_phone_number = self.target.mbs.getPhoneNumber()
        self.phone_notpaired.mbs.sendSms(target_phone_number,constants.SMS_TEXT)
        self.call_utils.wait_with_log(10)

    def test_unread_sms_message_db_sync(self):
        # to test that the unread sms appears on phone after pairing

        # Open the sms app
        self.call_utils.open_sms_app()
        # Perform the verifications
        self.call_utils.verify_sms_app_unread_message(True)
        self.call_utils.verify_sms_preview_text(True, constants.SMS_TEXT)
        self.call_utils.verify_sms_preview_timestamp(True)

    def teardown_test(self):
        # Go to home screen
        self.call_utils.press_home()
        super().teardown_test()

if __name__ == '__main__':
    common_main()