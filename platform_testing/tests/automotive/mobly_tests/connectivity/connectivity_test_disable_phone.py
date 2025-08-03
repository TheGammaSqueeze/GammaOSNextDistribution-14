"""
  Copyright (C) 2023 The Android Open Source Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.



  Test Steps:
  (0. Flash device)
  1. Verify by default BT should be ON always
  2. BluetoothManagerService: Startup: Bluetooth persisted state is ON

"""

import sys
import logging
import pprint

from mobly import asserts
from mobly import test_runner
from mobly.controllers import android_device

from bluetooth_test import bluetooth_base_test


from utilities.spectatio_utils import CallUtils
from utilities.bt_utils import BTUtils
from utilities import constants

class BluetoothDisableEnablePhoneTest(bluetooth_base_test.BluetoothBaseTest):

    NO_PHONE_TAG = 'no phone'

    def setup_test(self):
        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()


    def test_disable_enable_phone(self):
        # Navigate to the bluetooth settings page
        self.call_utils.open_bluetooth_settings()
        target_name = self.target.mbs.btGetName()
        # Disable phone for the listed paired device via the preference button
        self.call_utils.press_phone_toggle_on_device(target_name)


        # Confirm that the phone button is unchecked
        asserts.assert_false(
            self.discoverer.mbs.isPhonePreferenceChecked(),
            "Expected phone button to be unchecked after pressing it.")
        self.call_utils.wait_with_log(constants.DEFAULT_WAIT_TIME_FIVE_SECS)


        # Click on device and confirm that the summary says "No phone"
        self.discoverer.mbs.pressDeviceInBluetoothSettings(target_name)
        self.call_utils.wait_with_log(constants.WAIT_FOR_LOAD)
        summary = self.discoverer.mbs.getDeviceSummary()
        asserts.assert_true(
            self.NO_PHONE_TAG in summary,
            ("Expected device summary (on Level Two page) to include \'%s\'"
             % self.NO_PHONE_TAG)
        )
        self.call_utils.wait_with_log(constants.DEFAULT_WAIT_TIME_FIVE_SECS)

        # Go back to the bluetooth settings page and enable phone via the preference button
        self.call_utils.press_home()
        self.call_utils.open_bluetooth_settings()
        self.call_utils.press_phone_toggle_on_device(target_name)

        # Confirm that the phone button is re-enabled
        asserts.assert_true(
            self.discoverer.mbs.isPhonePreferenceChecked(),
            "Expected phone button to be checked after pressing it a second time.")

        # Click on the device and confirm that the summary doesn't include "phone"
        self.discoverer.mbs.pressDeviceInBluetoothSettings(target_name)
        self.call_utils.wait_with_log(constants.WAIT_FOR_LOAD)
        summary = self.discoverer.mbs.getDeviceSummary()
        asserts.assert_false(
            self.NO_PHONE_TAG in summary,
            "Found unexpected \'%s\' in device summary after re-enabling phone."
            % self.NO_PHONE_TAG
        ) 



if __name__ == '__main__':
    # Take test args
    test_runner.main()