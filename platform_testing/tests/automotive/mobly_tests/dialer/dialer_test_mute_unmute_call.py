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
        1) Precall state check on devices.
        2) Make a call using Dial application on IVI device.
        3) Assert calling number on IVI device same as called
        4) Mute call
        5) Unmute call
        6) End call
        7) Assert no any exceptions
"""


from bluetooth_test import bluetooth_base_test
import logging
from utilities import constants
from utilities.main_utils import common_main

class BluetoothMuteUnmuteCallTest(bluetooth_base_test.BluetoothBaseTest):

    def setup_test(self):
        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()

    def test_dial_phone_number(self):
        """Tests mute and unmute during phone call functionality."""
        #Variable
        dialer_test_phone_number = constants.DIALER_THREE_DIGIT_NUMBER
        logging.info(
            'Calling from %s calling to %s',
            self.target.serial,
            dialer_test_phone_number,)
        self.call_utils.dial_a_number(dialer_test_phone_number)
        self.call_utils.make_call()
        self.call_utils.wait_with_log(5)
        self.call_utils.verify_dialing_number(dialer_test_phone_number)
        self.call_utils.wait_with_log(5)

        # MicroPhone chip displays on status bar during unmuted ongoing call
        self.call_utils.is_microphone_displayed_on_status_bar(
            True)

        self.call_utils.mute_call()
        self.call_utils.wait_with_log(15)

        # MicroPhone chip goes way after 10-15 seconds after call is muted
        self.call_utils.is_microphone_displayed_on_status_bar(
            False)

        self.call_utils.unmute_call()

        # MicroPhone chip displays on again status bar during when call is unmuted
        self.call_utils.is_microphone_displayed_on_status_bar(
            True)
        self.call_utils.end_call()

if __name__ == '__main__':
        common_main()