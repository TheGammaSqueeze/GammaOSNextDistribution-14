#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
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

import acts_contrib.test_utils.power.cellular.cellular_pdcch_power_test as cppt


class PowerTelMac_MimoSweep_Test(cppt.PowerTelPDCCHTest):

    def test_lte_mac_1x1(self):
        self.power_pdcch_test()

    def test_lte_mac_2x2(self):
        self.power_pdcch_test()

    def test_lte_mac_4x4(self):
        self.power_pdcch_test()
