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

import binascii
import io
import logging
import os
import queue

from blueberry.tests.gd.cert.context import get_current_context

from blueberry.tests.sl4a_sl4a.lib import sl4a_sl4a_base_test
from blueberry.tests.gd_sl4a.lib.bt_constants import ble_address_types
from mobly import test_runner


class LeAdvertisingTest(sl4a_sl4a_base_test.Sl4aSl4aBaseTestClass):

    def setup_class(self):
        super().setup_class()

    def setup_test(self):
        super().setup_test()

    def teardown_test(self):
        super().teardown_test()

    def test_advertise_name(self):
        rpa_address = self.cert_advertiser_.advertise_public_extended_pdu()
        self.dut_scanner_.scan_for_name(self.cert_advertiser_.get_local_advertising_name())
        self.dut_scanner_.stop_scanning()
        self.cert_advertiser_.stop_advertising()

    def test_advertise_name_stress(self):
        for i in range(0, 10):
            self.test_advertise_name()

    def test_advertise_name_twice_no_stop(self):
        rpa_address = self.cert_advertiser_.advertise_public_extended_pdu()
        self.dut_scanner_.scan_for_name(self.cert_advertiser_.get_local_advertising_name())
        self.dut_scanner_.stop_scanning()
        rpa_address = self.cert_advertiser_.advertise_public_extended_pdu()
        self.dut_scanner_.scan_for_name(self.cert_advertiser_.get_local_advertising_name())
        self.dut_scanner_.stop_scanning()
        self.cert_advertiser_.stop_advertising()


if __name__ == '__main__':
    test_runner.main()
