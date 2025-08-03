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
        1) Precall state check on Seahawk and phone devices.
        2) Upload contacts.vcf to device.
        3) Add the contact as favorite
        4) Assert the contact is added to favorites
        5) Remove the contact form favorites
        6) Verify contact is removed from the favorites
"""

# import logging

import sys
import logging
import pprint

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly.controllers import android_device
from bluetooth_test import bluetooth_base_test

from utilities import constants
from utilities import spectatio_utils
from utilities import bt_utils


class AddRemoveFavoriteContact(bluetooth_base_test.BluetoothBaseTest):
  """Enable and Disable Bluetooth from Bluetooth Palette."""

  def setup_test(self):
    """Setup steps before any test is executed."""
   # Todo - testing was done by loading contacts manually , this function needs to be tested.
    # Upload contacts to phone device
    file_path = 'platform_testing/tests/automotive/mobly_tests/utils/contacts_test.vcf'
    self.call_utils.upload_vcf_contacts_to_device(
        self.target,
        file_path,
    )
    self.call_utils.wait_with_log(5)
    # Pair caller phone with automotive device
    self.bt_utils.pair_primary_to_secondary()

  def test_add_remove_favorite_contact(self):
    """Tests add remove favorite contact."""
    contact_name = "John Smith"
    self.call_utils.open_phone_app()

    # Adding the contacts to favorites from the favorites tab and verifying it
    self.call_utils.add_favorites_from_favorites_tab(
        contact_name)
    self.call_utils.is_contact_in_favorites(
        contact_name, True)
    self.call_utils.wait_with_log(10)

    # Removing the contacts from favorites and verifying it
    self.call_utils.open_details_page(contact_name)
    self.call_utils.add_remove_favorite_contact()
    self.call_utils.close_details_page()
    self.call_utils.is_contact_in_favorites(
        contact_name, False)

if __name__ == '__main__':
    common_main()