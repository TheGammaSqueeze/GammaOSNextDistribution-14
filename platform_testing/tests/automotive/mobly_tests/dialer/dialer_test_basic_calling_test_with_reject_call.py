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

"""Test of basic calling between two phones
Steps include:
        1) Precall state check on both devices. (OK)
        2) auto_target calling phone_callee's number.
        3) Verifying that the auto_target is "offhook" (ringing).
        4) Verifying that the phone_callee is ringing.
        5) Decline phone call at the phone_callee.
        6) Waiting 15 seconds.
        7) Re-verifying that both devices are in call.
        8) Hanging up (by default this is done by the auto_target).
        9) Verifying that both devices reach a postcall state.
"""
from bluetooth_sms_test import bluetooth_sms_base_test
import logging
from utilities import constants
from utilities.main_utils import common_main


class CallingDeclineBtTest(bluetooth_sms_base_test.BluetoothSMSBaseTest):

    def setup_test(self):
        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()

    def test_basic_call(self):
         # call the callee phone with automotive device
        target_phone_number = self.phone_notpaired.mbs.getPhoneNumber()
        logging.info(
                'Calling from %s calling to %s',
                self.phone_notpaired.serial,
                self.target.serial,
            )
        self.call_utils.dial_a_number(target_phone_number);
        self.call_utils.make_call()
        self.call_utils.wait_with_log(5)
        self.call_utils.end_call()

if __name__ == '__main__':
    common_main()