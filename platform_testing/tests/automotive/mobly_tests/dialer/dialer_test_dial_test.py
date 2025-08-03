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

"""Test of basic calling with given digits
 Steps include:
        1) Precall state check on Seahawk and phone devices. (OK)
        3) Make a call to any digits number using Seahawk
        4) Assert calling number on Sehawk same as ten digits number
        5) End call on Seahawk
        6) Get latest dialed number from the Seahawk
        7) Assert dialed number on the Seahawk same as called ten digits number
"""

from bluetooth_test import bluetooth_base_test
import logging
from utilities import constants
from utilities.main_utils import common_main


class BluetoothDialTest(bluetooth_base_test.BluetoothBaseTest):

    def setup_test(self):
        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()

    def test_dial_large_digits_number(self):
        #Variable
        dialer_test_phone_number = constants.DIALER_THREE_DIGIT_NUMBER
        #Tests the calling three digits number functionality
        logging.info(
            'Calling from %s calling to %s',
            self.target.serial,dialer_test_phone_number
        )
        self.call_utils.wait_with_log(2)
        self.call_utils.dial_a_number(dialer_test_phone_number);
        self.call_utils.make_call()
        self.call_utils.wait_with_log(5)
        self.call_utils.verify_dialing_number(dialer_test_phone_number)
        self.call_utils.wait_with_log(2)
        self.call_utils.is_ongoing_call_displayed_on_home(True)
        self.call_utils.open_phone_app_from_home()
        self.call_utils.end_call()
        self.call_utils.wait_with_log(5)
        self.call_utils.open_call_history()
        self.call_utils.verify_last_dialed_number(dialer_test_phone_number)

if __name__ == '__main__':
    common_main()