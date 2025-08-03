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


class PowerTelPdcch_BandSweep_Test(cppt.PowerTelPDCCHTest):

    def test_lte_pdcch_b1(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b2(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b3(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b4(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b5(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b7(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b8(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b12(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b13(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b14(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b18(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b19(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b20(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b28(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b30(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b40(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b41(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b42(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b66(self):
        self.power_pdcch_test()

    def test_lte_pdcch_b71(self):
        self.power_pdcch_test()
