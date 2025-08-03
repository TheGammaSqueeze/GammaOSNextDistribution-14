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




from bluetooth_test import bluetooth_base_test
from mobly import asserts
from utilities.main_utils import common_main
from utilities import constants



class BluetoothPalette(bluetooth_base_test.BluetoothBaseTest):
  """Bluetooth Palette Verification."""

  def setup_test(self):
    # Pair the devices
    self.bt_utils.pair_primary_to_secondary()

  def test_bluetooth_palette_verification(self):
    """Tests Bluetooth Palette Verification."""
    self.call_utils.open_bluetooth_palette()
    self.call_utils.wait_with_log(5)
    asserts.assert_true(self.call_utils.has_bluetooth_button(),'Unable to verify Bluetooth')
    asserts.assert_true(self.call_utils.has_bluetooth_palette_phone_button(),'Unable to verify Phone')
    asserts.assert_true(self.call_utils.has_bluetooth_palette_media_button(),'Unable to verify Media')
    asserts.assert_true(self.call_utils.verify_device_name(),'Unable to verify Device Name')
    self.call_utils.click_bluetooth_button()
    self.call_utils.wait_with_log(10)
    asserts.assert_false(self.call_utils.is_bluetooth_button_enabled(),'Bluetooth Button is Not disabled')
    asserts.assert_false(self.call_utils.is_bluetooth_phone_button_enabled(),'Phone Button is Not disabled')
    asserts.assert_false(self.call_utils.is_bluetooth_media_button_enabled(),'Media Button is Not disabled')


if __name__ == '__main__':
    common_main()
