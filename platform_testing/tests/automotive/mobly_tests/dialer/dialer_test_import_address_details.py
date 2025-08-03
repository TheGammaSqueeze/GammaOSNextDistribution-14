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
1. Go to Dialer >Contacts and click on '>' icon in front of contact name to see the contact details
2. Verify address information stored in phone contacts rendered in dialer app successfully

"""

import logging

from mobly import asserts
from bluetooth_test import bluetooth_base_test
from mobly.controllers import android_device

from utilities.main_utils import common_main
from utilities import constants
from utilities import spectatio_utils
from utilities import bt_utils


class ImportAddressDetailsTest(bluetooth_base_test.BluetoothBaseTest):
    VCF_ADDRESS_HEADER = "ADR"

    def get_first_address(self, vcf_path):
        """ Reads the first address from the given vcf file'"""

        with open(vcf_path, mode='r') as vcf_file:
            for line in vcf_file:
                if line.startswith(self.VCF_ADDRESS_HEADER):
                    return line


    def setup_test(self):
        # Upload contacts to phone device
        file_path = constants.PATH_TO_CONTACTS_VCF_FILE
        self.call_utils.upload_vcf_contacts_to_device(self.target, file_path)

        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()

    def test_import_address_details(self):
        # Open the dialer app, and then the contacts page
        self.call_utils.open_phone_app()
        self.call_utils.wait_with_log(2)
        self.call_utils.open_contacts()
        self.call_utils.wait_with_log(2)
        self.call_utils.open_first_contact_details()
        self.call_utils.wait_with_log(2)

        # Import the first contact's address from the discoverer device.
        display_address = self.call_utils.get_home_address_from_details()

        # Import the list of contact addresses from the VCF file.
        vcf_line = self.get_first_address(constants.PATH_TO_CONTACTS_VCF_FILE)

        # Confirm that these two lists contain the same data.
        asserts.assert_true(
            self.compare_display_address_to_vcf_line(display_address, vcf_line),
            ("Displayed address does not match address stored in VCF file: " +
             "\n\tDisplayed address: %s" +
             "\n\tVCF address: %s") % (display_address, vcf_line))

    def teardown_test(self):
        # Turn Bluetooth off on both devices after test finishes.
        super().teardown_test()

    def compare_display_address_to_vcf_line(self, display_address, vcf_address):
        """Confirm that each portion of a display-able street address appears in the vcf line.
            Comparison is done portion-by-portion because the VCF line contains metadata and delimiters,
            meaning this comparison could hypothetically give a false positive if parts of the address are entirely composed
            of other parts of the address (for example, a house number being identical to the zip code."""
        parts = display_address.split()
        for part in parts:
            if not part in vcf_address:
                logging.info("\tAddress mismatch: %s not found in %s" % (part, vcf_address))
                return False
        return True


if __name__ == '__main__':
    common_main()
