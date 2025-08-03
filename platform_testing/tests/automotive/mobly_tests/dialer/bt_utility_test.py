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

"""This file is just a proof-of-concept stub checking to make sure the MBS utilities are
working as expected. """


import sys
import logging
import pprint

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly.controllers import android_device

from utilities import constants
from utilities import spectatio_utils
from utilities import bt_utils
from utilities.main_utils import common_main

from bluetooth_test import bluetooth_base_test



# Number of seconds for the target to stay discoverable on Bluetooth.
DISCOVERABLE_TIME = 60

class UtilityClassTest(bluetooth_base_test.BluetoothBaseTest):

    def setup_test(self):
        super().setup_test()
        # Pair caller phone with automotive device
        self.bt_utils.pair_primary_to_secondary()

    def test_call_utility(self):
        # Navigate to the phone app page
        self.call_utils.open_phone_app()


if __name__ == '__main__':
    # Take test args
    common_main()