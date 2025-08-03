#!/usr/bin/env python3
#
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

import acts_contrib.test_utils.power.cellular.cellular_power_preset_base_test as PB


class PowerTelIdle_Preset_Test(PB.PowerCellularPresetLabBaseTest):
    def power_tel_idle_test(self):
        """ Measures power when the device is on RRC idle state."""
        idle_wait_time = self.simulation.rrc_sc_timer + 30
        # Wait for RRC status change to trigger
        self.cellular_simulator.wait_until_idle_state(idle_wait_time)

        # Measure power and check against threshold
        self.collect_power_data_and_validate()

    def test_preset_LTE_idle(self):
        self.power_tel_idle_test()

    def test_preset_sa_idle_fr1(self):
        self.power_tel_idle_test()
