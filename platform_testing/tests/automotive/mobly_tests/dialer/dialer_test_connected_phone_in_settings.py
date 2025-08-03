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

1. Tap on Phone icon from facet rail or App launcher to launch Dialer app
2. Click on settings icon to launch Dialer ->Settings
3. Verify that Connected phone settings is showing
4. Verify BT  mobile device name is showing if its connected

"""


import sys
import logging
import pprint
from bluetooth_test import bluetooth_base_test
from mobly import asserts
from mobly import base_test

from utilities import constants
from utilities.main_utils import common_main

# Number of seconds for the target to stay discoverable on Bluetooth.
AUTO_LABEL = 'auto'
PHONE_LABEL = 'phone'

class ConnectedPhoneTest(bluetooth_base_test.BluetoothBaseTest):


    def setup_test(self):
        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()

    def test_connected_phone_displayed(self):
        # Navigate to the Dialer App page
        self.call_utils.open_phone_app()
        self.call_utils.wait_with_log(constants.WAIT_TWO_SECONDS)
        # Click on Settings icon to navigate to Dialer -> Settings
        self.call_utils.open_dialer_settings()
        self.call_utils.wait_with_log(constants.WAIT_TWO_SECONDS)

        # Note that this call will fail if the "Connected Phone" title is not discovered,
        # covering step #3 in the header of this test file
        phoneNameInSettings = self.discoverer.mbs.getConnectedPhoneName()

        # Verify displayed phone name matches the one we've given.
        target_name = self.target.mbs.btGetName()
        asserts.assert_true(
             target_name == phoneNameInSettings,
            "When looking for mobile name on phone settings,"
            "expected \'%s\' but found \'%s\'" % (target_name, phoneNameInSettings))


if __name__ == '__main__':
    # Take test args
    common_main()