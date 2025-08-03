# #  Copyright (C) 2023 The Android Open Source Project
# #
# #  Licensed under the Apache License, Version 2.0 (the "License");
# #  you may not use this file except in compliance with the License.
# #  You may obtain a copy of the License at
# #
# #       http://www.apache.org/licenses/LICENSE-2.0
# #
# #  Unless required by applicable law or agreed to in writing, software
# #  distributed under the License is distributed on an "AS IS" BASIS,
# #  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# #  See the License for the specific language governing permissions and
# #  limitations under the License.
# #
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


from bluetooth_sms_test import bluetooth_sms_base_test

from mobly import asserts
from utilities.main_utils import common_main


class BTSMSUtilityTest(bluetooth_sms_base_test.BluetoothSMSBaseTest):

  def test_sms_poc(self):
    """Tests launches SMS app and also tests bt status all three devices for the poc"""

    # opening the sms app
    self.call_utils.open_bluetooth_sms_app()
    self.phone_notpaired.mbs.btDisable()

    # tests bt status of all three allocated devices
    asserts.assert_true(self.target.mbs.btIsEnabled(),
                        '<Bluetooth> should be ON')
    asserts.assert_true(self.discoverer.mbs.btIsEnabled(),
                        '<Bluetooth> should be ON')
    asserts.assert_false(self.phone_notpaired.mbs.btIsEnabled(),
                        '<Bluetooth> should be OFF')


if __name__ == '__main__':
  common_main()
