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
import time

from blueberry.tests.gd.cert.context import get_current_context
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd_sl4a.lib.bt_constants import ble_address_types
from blueberry.tests.sl4a_sl4a.lib import sl4a_sl4a_base_test
from blueberry.tests.sl4a_sl4a.lib.security import Security
from mobly import test_runner


class LeScanningTest(sl4a_sl4a_base_test.Sl4aSl4aBaseTestClass):

    def __get_cert_public_address_and_irk_from_bt_config(self):
        # Pull IRK from SL4A cert side to pass in from SL4A DUT side when scanning
        bt_config_file_path = os.path.join(get_current_context().get_full_output_path(),
                                           "DUT_%s_bt_config.conf" % self.cert.serial)
        try:
            self.cert.adb.pull(["/data/misc/bluedroid/bt_config.conf", bt_config_file_path])
        except AdbError as error:
            logging.error("Failed to pull SL4A cert BT config")
            return False
        logging.debug("Reading SL4A cert BT config")
        with io.open(bt_config_file_path) as f:
            for line in f.readlines():
                stripped_line = line.strip()
                if (stripped_line.startswith("Address")):
                    address_fields = stripped_line.split(' ')
                    # API currently requires public address to be capitalized
                    address = address_fields[2].upper()
                    logging.debug("Found cert address: %s" % address)
                    continue
                if (stripped_line.startswith("LE_LOCAL_KEY_IRK")):
                    irk_fields = stripped_line.split(' ')
                    irk = irk_fields[2]
                    logging.debug("Found cert IRK: %s" % irk)
                    continue

        return address, irk

    def setup_class(self):
        super().setup_class()

    def setup_test(self):
        assertThat(super().setup_test()).isTrue()

    def teardown_test(self):
        super().teardown_test()

    def test_scan_result_address(self):
        cert_public_address, irk = self.__get_cert_public_address_and_irk_from_bt_config()
        self.cert_advertiser_.advertise_public_extended_pdu()
        advertising_name = self.cert_advertiser_.get_local_advertising_name()

        # Scan with name and verify we get back a scan result with the RPA
        scan_result_addr = self.dut_scanner_.scan_for_name(advertising_name)
        assertThat(scan_result_addr).isNotNone()
        assertThat(scan_result_addr).isNotEqualTo(cert_public_address)

        # Bond
        logging.info("Bonding with %s", scan_result_addr)
        self.dut_security_.create_bond_numeric_comparison(scan_result_addr)
        self.dut_scanner_.stop_scanning()

        # Start advertising again and scan for identity address
        scan_result_addr = self.dut_scanner_.scan_for_address(cert_public_address, ble_address_types["public"])
        assertThat(scan_result_addr).isNotNone()
        assertThat(scan_result_addr).isNotEqualTo(cert_public_address)

        # Teardown advertiser and scanner
        self.dut_scanner_.stop_scanning()
        self.cert_advertiser_.stop_advertising()


if __name__ == '__main__':
    test_runner.main()