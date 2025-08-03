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


class PowerTelPdcchFr2_Preset_Test(PB.PowerCellularPresetLabBaseTest):
    def power_pdcch_test(self):
        """ Measures power during PDCCH only.

        There's nothing to do here other than starting the power measurement
        and deciding for pass or fail, as the base class will handle attaching.
        Requirements for this test are that mac padding is off and that the
        inactivity timer is not enabled. """

        # Measure power and check against threshold
        self.collect_power_data_and_validate()

    def test_preset_nsa_cdrx_fr2(self):
        self.power_pdcch_test()

    def test_preset_nsa_pdcch_fr2(self):
        self.power_pdcch_test()
