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

import sys

from bluetooth_test import bluetooth_base_test

from mobly import asserts
from utilities.main_utils import common_main


class BTSMSDeviceNotPairedTest(bluetooth_base_test.BluetoothBaseTest):

  def setup_test(self):
    # Disable BT on devices
    self.target.mbs.btDisable()
    self.discoverer.mbs.btDisable()

  def test_messenger_device_not_paired(self):
    """Tests launches SMS Audio app and verify <Bluetooth Disconnected> displayed."""
    self.call_utils.open_bluetooth_sms_app()
    asserts.assert_true(self.call_utils.is_bluetooth_sms_disconnected_label_visible(),
                        '<Bluetooth SMS disconnected> label should be present')


if __name__ == '__main__':
  common_main()
