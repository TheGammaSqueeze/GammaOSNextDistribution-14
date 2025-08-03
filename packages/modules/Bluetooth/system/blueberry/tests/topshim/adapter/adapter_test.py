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
from blueberry.tests.topshim.lib.adapter_client import AdapterClient

from mobly import test_runner


class AdapterTest(TopshimBaseTest):

    def test_verify_adapter_started(self):
        print("Adapter is verified when test starts")

    def test_enable_inquiry_scan(self):
        status, discovery_mode = self.dut().enable_inquiry_scan()
        assertThat(status).isEqualTo("Success")
        assertThat(discovery_mode).isEqualTo("ConnectableDiscoverable")

    def test_enable_page_scan(self):
        status, discovery_mode = self.dut().enable_page_scan()
        assertThat(status).isEqualTo("Success")
        assertThat(discovery_mode).isEqualTo("Connectable")

    def test_disable_page_scan(self):
        status, discovery_mode = self.dut().disable_page_scan()
        assertThat(status).isEqualTo("Success")
        assertThat(discovery_mode).isEqualTo("None_")

    def test_set_local_io_caps(self):
        status, caps = self.dut().set_local_io_caps(3)
        assertThat(status).isEqualTo("Success")
        assertThat(caps).isEqualTo("None_")

    def test_start_discovery(self):
        state = self.dut().toggle_discovery(True)
        assertThat(state).isEqualTo("Started")
        # Reset device to not discovering.
        self.dut().toggle_discovery(False)

    def test_cancel_discovery(self):
        self.dut().toggle_discovery(True)
        state = self.dut().toggle_discovery(False)
        assertThat(state).isEqualTo("Stopped")

    def test_find_device_device_available(self):
        self.dut().enable_inquiry_scan()
        self.cert().enable_inquiry_scan()
        self.dut().toggle_discovery(True)
        device_addr = self.dut().find_device()
        assertThat(device_addr).isNotNone()
        # Reset DUT device discovering and scanning to None
        self.dut().disable_page_scan()
        self.dut().toggle_discovery(False)
        # Reset CERT device to not discoverable
        self.cert().disable_page_scan()


if __name__ == "__main__":
    test_runner.main()
