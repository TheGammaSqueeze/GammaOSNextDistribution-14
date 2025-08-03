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


class OobPairingTest(sl4a_sl4a_base_test.Sl4aSl4aBaseTestClass):

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

    def __get_dut_public_address_and_irk_from_bt_config(self):
        # Pull IRK from SL4A dut side
        bt_config_file_path = os.path.join(get_current_context().get_full_output_path(),
                                           "DUT_%s_bt_config.conf" % self.dut.serial)
        try:
            self.dut.adb.pull(["/data/misc/bluedroid/bt_config.conf", bt_config_file_path])
        except AdbError as error:
            logging.error("Failed to pull SL4A dut BT config")
            return False
        logging.debug("Reading SL4A dut BT config")
        with io.open(bt_config_file_path) as f:
            for line in f.readlines():
                stripped_line = line.strip()
                if (stripped_line.startswith("Address")):
                    address_fields = stripped_line.split(' ')
                    # API currently requires public address to be capitalized
                    address = address_fields[2].upper()
                    logging.debug("Found dut address: %s" % address)
                    continue
                if (stripped_line.startswith("LE_LOCAL_KEY_IRK")):
                    irk_fields = stripped_line.split(' ')
                    irk = irk_fields[2]
                    logging.debug("Found dut IRK: %s" % irk)
                    continue

        return address, irk

    def setup_class(self):
        super().setup_class()

    def setup_test(self):
        assertThat(super().setup_test()).isTrue()

    def teardown_test(self):
        super().teardown_test()

    def __test_scan(self, address_type="public"):
        cert_public_address, irk = self.__get_cert_public_address_and_irk_from_bt_config()
        rpa_address = self.cert_advertiser_.advertise_public_extended_pdu()
        self.dut_scanner_.start_identity_address_scan(cert_public_address, ble_address_types[address_type])
        self.dut_scanner_.stop_scanning()
        self.cert_advertiser_.stop_advertising()

    def __create_le_bond_oob_single_sided(self, wait_for_oob_data=True, wait_for_device_bonded=True):
        oob_data = self.cert_security_.generate_oob_data(Security.TRANSPORT_LE, wait_for_oob_data)
        if wait_for_oob_data:
            assertThat(oob_data[0]).isEqualTo(0)
            assertThat(oob_data[1]).isNotNone()
        self.dut_security_.create_bond_out_of_band(oob_data[1], None, -1, wait_for_device_bonded)

    def __create_le_bond_oob_double_sided(self, wait_for_oob_data=True, wait_for_device_bonded=True):
        # Genearte OOB data on DUT, but we don't use it
        self.dut_security_.generate_oob_data(Security.TRANSPORT_LE, wait_for_oob_data)
        self.__create_le_bond_oob_single_sided(wait_for_oob_data, wait_for_device_bonded)

    def test_classic_generate_local_oob_data(self):
        oob_data = self.dut_security_.generate_oob_data(Security.TRANSPORT_BREDR)
        assertThat(oob_data[0]).isEqualTo(0)
        assertThat(oob_data[1]).isNotNone()
        oob_data = self.dut_security_.generate_oob_data(Security.TRANSPORT_BREDR)
        assertThat(oob_data[0]).isEqualTo(0)
        assertThat(oob_data[1]).isNotNone()

    def test_classic_generate_local_oob_data_stress(self):
        for i in range(1, 20):
            self.test_classic_generate_local_oob_data()

    def test_le_generate_local_oob_data(self):
        oob_data = self.dut_security_.generate_oob_data(Security.TRANSPORT_LE)
        assertThat(oob_data[0]).isEqualTo(0)
        assertThat(oob_data[1]).isNotNone()
        oob_data = self.cert_security_.generate_oob_data(Security.TRANSPORT_LE)
        assertThat(oob_data[0]).isEqualTo(0)
        assertThat(oob_data[1]).isNotNone()

    def test_le_generate_local_oob_data_stress(self):
        for i in range(1, 20):
            self.test_le_generate_local_oob_data()

    def test_le_bond(self):
        self.__create_le_bond_oob_single_sided()

    def test_le_bond_oob_stress(self):
        for i in range(0, 10):
            logging.info("Stress #%d" % i)
            self.__create_le_bond_oob_single_sided()
            self.dut_security_.remove_all_bonded_devices()
            self.cert_security_.remove_all_bonded_devices()

    def test_le_generate_local_oob_data_after_le_bond_oob(self):
        self.__create_le_bond_oob_single_sided()
        self.test_le_generate_local_oob_data()

    def test_le_generate_oob_data_while_bonding(self):
        self.__create_le_bond_oob_double_sided(True, False)
        self.dut_security_.generate_oob_data(Security.TRANSPORT_LE, False)
        for i in range(0, 10):
            oob_data = self.dut_security_.generate_oob_data(Security.TRANSPORT_LE, True)
            logging.info("OOB Data came back with code: %d", oob_data[0])

    def test_le_oob_advertiser_not_using_public_address(self):
        #TODO(optedoblivion): Use sysprop and make another test to handle non privacy case
        oob_data = self.dut_security_.generate_oob_data(Security.TRANSPORT_LE)
        assertThat(oob_data[0]).isEqualTo(0)
        assertThat(oob_data[1]).isNotNone()
        advertiser_address = oob_data[1].to_sl4a_address()
        dut_public_address, dut_irk = self.__get_dut_public_address_and_irk_from_bt_config()
        logging.info("DUT Advertiser Address: %s " % advertiser_address)
        logging.info("DUT Public Address: %s " % dut_public_address)
        assertThat(advertiser_address).isNotEqualTo(dut_public_address)


if __name__ == '__main__':
    test_runner.main()
