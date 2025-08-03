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

from mobly import asserts
from bluetooth_test import bluetooth_base_test

from utilities.main_utils import common_main

class DisableEnableHFPBluetoothProfile(bluetooth_base_test.BluetoothBaseTest):
  """Enable and Disable Bluetooth from Bluetooth Palette."""

  def setup_test(self):
    """Setup steps before any test is executed."""
    # Pair caller phone with automotive device
    self.call_utils.press_home()
    self.bt_utils.pair_primary_to_secondary()

  def test_disable_enable_phone_hfp_bluetooth_profile(self):
    """Disable - Enable Phone-HFP Bluetooth profile"""
    self.call_utils.open_bluetooth_palette()
    self.call_utils.wait_with_log(60)
    self.call_utils.click_phone_button()
    self.call_utils.wait_with_log(10)
    asserts.assert_false(self.call_utils.verify_disabled_phone_profile(),'Phone is disabled')
    self.call_utils.open_phone_app()
    asserts.assert_true(self.call_utils.verify_bluetooth_hfp_error_displayed(),'Bluetooth hfp error is displayed')
    self.call_utils.open_bluetooth_palette()
    self.call_utils.wait_with_log(5)
    self.call_utils.click_phone_button()
    self.call_utils.wait_with_log(5)
    self.call_utils.open_phone_app()
    asserts.assert_true(self.call_utils.verify_dialer_recents_tab(),'Dialer recents tab is displayed')
    asserts.assert_true(self.call_utils.verify_dialer_contacts_tab(),'Dialer contacts tab is displayed')
    asserts.assert_true(self.call_utils.verify_dialer_favorites_tab(),'Dialer favorites tab is displayed')
    asserts.assert_true(self.call_utils.verify_dialer_dialpad_tab(),'Dialer dialpad tab is displayed')

if __name__ == '__main__':
  common_main()