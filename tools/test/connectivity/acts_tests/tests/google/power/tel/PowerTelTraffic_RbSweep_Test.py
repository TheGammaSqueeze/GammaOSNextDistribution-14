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

import acts_contrib.test_utils.power.cellular.cellular_traffic_power_test as ctpt


class PowerTelTraffic_RbSweep_Test(ctpt.PowerTelTrafficTest):

    def test_lte_direction_dl_8(self):
        self.power_tel_traffic_test()

    def test_lte_direction_dl_24(self):
        self.power_tel_traffic_test()

    def test_lte_direction_dl_52(self):
        self.power_tel_traffic_test()

    def test_lte_direction_dl_76(self):
        self.power_tel_traffic_test()

    def test_lte_direction_dl_100(self):
        self.power_tel_traffic_test()

    def test_lte_direction_ul_8(self):
        self.power_tel_traffic_test()

    def test_lte_direction_ul_25(self):
        self.power_tel_traffic_test()

    def test_lte_direction_ul_50(self):
        self.power_tel_traffic_test()

    def test_lte_direction_ul_75(self):
        self.power_tel_traffic_test()

    def test_lte_direction_ul_100(self):
        self.power_tel_traffic_test()
