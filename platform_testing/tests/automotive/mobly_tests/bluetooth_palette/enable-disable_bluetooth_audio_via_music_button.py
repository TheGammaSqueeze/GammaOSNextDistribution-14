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

"""Test of Enable-Disable Bluetooth Audio via Music button

Steps to reproduce:

Launch Bluetooth Audio from All apps
     1. Launch Bluetooth Palette from Action Bar
     2. Tap Enabled- Blue  Music button - it will turn to be grey - disabled
     3. Launch All Apps- Bluetooth Audio app - Verify: 'Connect to Bluetooth' message will be displayed in Bluetooth Audio page
     4. Turn On Music button in Bluetooth Palette - Launch Bluetooth Audio  Verify media source and song metadata from currently played song should be displayed in Media page
     5. Repeat Turning Off/On Media button several times
"""

from bluetooth_test import bluetooth_base_test
from mobly import asserts

from utilities import constants
from utilities.main_utils import common_main

class EnableDisableBluetoothAudioViaMusicButton(bluetooth_base_test.BluetoothBaseTest):
  """Enable and Disable Bluetooth Audio Bluetooth Palette."""

  def setup_test(self):
    """Setup steps before any test is executed."""
    # Pair caller phone with automotive device
    self.bt_utils.pair_primary_to_secondary()

  def test_enable_disable_bluetooth_audio_via_music_button(self):
    """ Enable - Disable Bluetooth Audio via music button"""
    self.call_utils.open_bluetooth_palette()
    self.call_utils.wait_with_log(10)
    asserts.assert_true(self.call_utils.is_bluetooth_media_button_enabled(),'Media Button is Not Enabled')
    self.call_utils.click_on_bluetooth_palette_media_button()
    asserts.assert_false(self.call_utils.is_bluetooth_media_button_enabled(),'Media Button is Not disabled')
    self.call_utils.open_bluetooth_media_app()
    self.call_utils.click_cancel_label_visible_on_bluetooth_audio_page()
    asserts.assert_true(self.call_utils.is_connect_to_bluetooth_label_visible_on_bluetooth_audio_page(), "Connect to Bluetooth Label is not visible")
    self.call_utils.open_bluetooth_palette()
    self.call_utils.click_on_bluetooth_palette_media_button()
    asserts.assert_true(self.call_utils.is_bluetooth_media_button_enabled(),'Media Button is Not Enabled')
    self.call_utils.open_bluetooth_media_app()
    asserts.assert_false(self.call_utils.is_connect_to_bluetooth_label_visible_on_bluetooth_audio_page(), "Connect to Bluetooth Label is visible")


if __name__ == '__main__':
  common_main()