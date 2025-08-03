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
from blueberry.tests.topshim.lib.topshim_base_test import TopshimBaseTest
from blueberry.tests.topshim.lib.topshim_device import TRANSPORT_CLASSIC

from mobly import test_runner


class HfpTest(TopshimBaseTest):

    def setup_test(self):
        super().setup_test()
        # Pair dut and cert device.
        self.dut().enable_inquiry_scan()
        self.cert().enable_inquiry_scan()
        self.dut().toggle_discovery(True)
        self.__paired_device = self.dut().find_device()
        self.dut().create_bond(address=self.__paired_device, transport=TRANSPORT_CLASSIC)

    def teardown_test(self):
        super().teardown_test()
        # Test teardown for dut and cert reset.
        self.dut().toggle_discovery(False)
        self.dut().disable_page_scan()
        self.cert().disable_page_scan()

    def test_hfp_connect_with_bond(self):
        state, _ = self.dut().start_slc(address=self.__paired_device)
        assertThat(state).isEqualTo("Connecting")
        state, _ = self.dut().wait_for_hfp_connection_state_change()
        assertThat(state).isEqualTo("Connected")
        state, conn_addr = self.dut().wait_for_hfp_connection_state_change()
        assertThat(state).isEqualTo("SlcConnected")
        assertThat(conn_addr).isEqualTo(self.__paired_device)

        #Extra steps to remove bonding to complete teardown.
        self.dut().remove_bonded_device(self.__paired_device)
        # This is required currently so that the HFP connection state change
        # callback doesn't affect other tests.
        self.dut().wait_for_hfp_connection_state_change()

    def test_hfp_disconnect_with_bond(self):
        state, _ = self.dut().start_slc(address=self.__paired_device)
        self.dut().wait_for_hfp_connection_state_change()  # To connected
        self.dut().wait_for_hfp_connection_state_change()  # To SLC connected

        # Actual test for stopping SLC connection.
        state, _ = self.dut().stop_slc(address=self.__paired_device)
        assertThat(state).isEqualTo("Disconnecting")
        state, _ = self.dut().wait_for_hfp_connection_state_change()
        assertThat(state).isEqualTo("Disconnected")
        #Extra steps to remove bonding to complete teardown.
        self.dut().remove_bonded_device(self.__paired_device)


if __name__ == "__main__":
    test_runner.main()
