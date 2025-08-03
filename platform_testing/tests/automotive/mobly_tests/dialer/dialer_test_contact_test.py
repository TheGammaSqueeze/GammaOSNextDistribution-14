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


import logging

from bluetooth_test import bluetooth_base_test
from utilities.main_utils import common_main

from mobly import asserts
from mobly import test_runner
from mobly.controllers import android_device

from utilities import constants
from utilities import spectatio_utils
from utilities import bt_utils



# Number of seconds for the target to stay discoverable on Bluetooth.
DISCOVERABLE_TIME = 60

class CallContactTest(bluetooth_base_test.BluetoothBaseTest):

    def setup_test(self):
        # Upload contacts to phone device
        file_path = constants.PATH_TO_CONTACTS_VCF_FILE
        self.call_utils.upload_vcf_contacts_to_device(self.target, file_path)

        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()

    def test_call_contact(self):
        # Navigate to the Contacts page
        self.call_utils.open_phone_app()
        self.call_utils.open_contacts()
        self.call_utils.wait_with_log(5)

        contactToCall = super.discoverer.mbs.getFirstContactFromContactList()
        logging.info("Attempting to call contact: %s", contactToCall)

        super.discoverer.mbs.callContact(contactToCall)

        # end_call() acts as an automatic verifying that a call is underway
        # since end_call() will throw an exception if no end_call button is available.
        self.call_utils.end_call()
        self.call_utils.wait_with_log(2)


if __name__ == '__main__':
    common_main()
