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
from blueberry.tests.topshim.lib.topshim_device import TRANSPORT_CLASSIC

from mobly import test_runner


class ClassicSecurityTest(TopshimBaseTest):

    def test_create_classic_bond(self):
        self.dut().enable_inquiry_scan()
        self.cert().enable_inquiry_scan()
        self.dut().toggle_discovery(True)
        address = self.dut().find_device()
        state, conn_addr = self.dut().create_bond(address=address, transport=TRANSPORT_CLASSIC)
        assertThat(state).isEqualTo("Bonded")
        assertThat(conn_addr).isEqualTo(address)


if __name__ == "__main__":
    test_runner.main()
