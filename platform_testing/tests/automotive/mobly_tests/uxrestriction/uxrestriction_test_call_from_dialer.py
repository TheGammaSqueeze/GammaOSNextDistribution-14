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
        1) Precall state check on devices. (OK)
        2) Enable the drive mode in IVI device
        3) Make a call to any digits number using IVI device
        4) Assert calling number on IVI device same as called
        5) End call on IVI device
        6) Get latest dialed number from the IVI device
        7) Assert dialed number on the IVI device same as called ten digits number
        8) Enable the Park Mode in IVI device
"""

from bluetooth_test import bluetooth_base_test
from utilities import constants
from utilities.main_utils import common_main


class UxRestrictionBluetoothCallFromDialerTest(bluetooth_base_test.BluetoothBaseTest):

    def setup_test(self):
        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()
        #set driving mode
        self.call_utils.enable_driving_mode()

    def test_call_from_dialer_during_drive_mode(self):
        #Tests the calling ten digits number functionality during drive mode.
        #Variable
        dialer_test_phone_number = constants.DIALER_THREE_DIGIT_NUMBER;

        self.call_utils.dial_a_number(dialer_test_phone_number)
        self.call_utils.make_call()
        self.call_utils.wait_with_log(5)
        self.call_utils.verify_dialing_number(dialer_test_phone_number)
        self.call_utils.end_call()
        self.call_utils.wait_with_log(5)
        self.call_utils.open_call_history()
        self.call_utils.verify_last_dialed_number(dialer_test_phone_number)

    def teardown_test(self):
        #Disable driving mode
        self.call_utils.disable_driving_mode()
        super().teardown_test()

if __name__ == '__main__':
    common_main()