#   Copyright 2022 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
import time

import acts_contrib.test_utils.power.cellular.cellular_power_preset_base_test as PB


class PowerTelAirplaneModeTest(PB.PowerCellularPresetLabBaseTest):

    def power_tel_airplane_mode_test(self):
        """Measure power while airplane mode is on. """
        # Start airplane mode
        self.cellular_dut.toggle_airplane_mode(True)

        # Allow airplane mode to propagate
        time.sleep(3)

        # Measure power and check against threshold
        self.collect_power_data_and_validate()

class PowerTelAirplaneMode_Test(PowerTelAirplaneModeTest):
    def test_airplane_mode(self):
        self.power_tel_airplane_mode_test()