#!/usr/bin/env python3
#
#   Copyright 2022 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.topshim.lib.adapter_client import AdapterClient
from blueberry.tests.topshim.lib.topshim_base_test import TopshimBaseTest
from blueberry.tests.topshim.lib.topshim_device import TRANSPORT_CLASSIC, TRANSPORT_LE

from mobly import test_runner


class LeSecurityTest(TopshimBaseTest):

    DUMMY_ADDRESS = "01:02:03:04:05:06"

    def test_remove_bond_with_no_bonded_devices(self):
        self.dut().remove_bonded_device(self.DUMMY_ADDRESS)
        self.dut().le_rand()

    def test_generate_local_oob_data(self):
        oob_data = self.dut().generate_local_oob_data(TRANSPORT_CLASSIC)
        assertThat(oob_data.is_valid()).isTrue()
        oob_data = self.dut().generate_local_oob_data(TRANSPORT_LE)
        assertThat(oob_data.is_valid()).isTrue()


if __name__ == "__main__":
    test_runner.main()
