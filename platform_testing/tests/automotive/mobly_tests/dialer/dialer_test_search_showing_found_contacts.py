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

  Test of searching shown found contacts

 Steps include:
        1) Precall state check on Seahawk and phone devices.
        2) Upload contacts.vcf to device.
        3) Open Dialpad and enter existent contact number
        4) All Contacts having this number in it will display on right side of
         dialpad in contact search
"""

from utilities import constants
from utilities.main_utils import common_main
from bluetooth_test import bluetooth_base_test

class SearchShowingFoundContact(bluetooth_base_test.BluetoothBaseTest):

    def setup_test(self):
     #Upload contacts to phone device
     file_path = constants.PATH_TO_CONTACTS_VCF_FILE
     #TODO- retest again after (b/308018112) is fixed.
     #contacts were loading manually fpr testing
     self.call_utils.upload_vcf_contacts_to_device(self.target, file_path)
     # Pair the devices
     self.bt_utils.pair_primary_to_secondary()


    def test_search_contact_by_phone_number(self):
       """Tests search contact by Phone number."""
       expected_contact_full_name = constants.EXPECTED_CONTACT_FULL_NAME
       first_digit_of_search_contact_number = constants.FIRST_DIGIT_OF_SEARCH_CONTACT_NUMBER
       self.call_utils.open_phone_app()
       self.call_utils.dial_a_number(first_digit_of_search_contact_number)
       self.call_utils.wait_with_log(3)
       self.call_utils.verify_search_results_contain_target_search(
           expected_contact_full_name
       )

if __name__ == '__main__':
    common_main()