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


class LeL2capCoCTest(sl4a_sl4a_base_test.Sl4aSl4aBaseTestClass):

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
        self.dut_scanner_.stop_scanning()
        self.cert_advertiser_.stop_advertising()
        self.dut_security_.remove_all_bonded_devices()
        self.cert_security_.remove_all_bonded_devices()
        super().teardown_test()

    # Scans for the cert device by name. We expect to get back a RPA.
    def __scan_for_cert_by_name(self):
        cert_public_address, irk = self.__get_cert_public_address_and_irk_from_bt_config()
        self.cert_advertiser_.advertise_public_extended_pdu()
        advertising_name = self.cert_advertiser_.get_local_advertising_name()

        # Scan with name and verify we get back a scan result with the RPA
        scan_result_addr = self.dut_scanner_.scan_for_name(advertising_name)
        assertThat(scan_result_addr).isNotNone()
        assertThat(scan_result_addr).isNotEqualTo(cert_public_address)

        return scan_result_addr

    def __scan_for_irk(self):
        cert_public_address, irk = self.__get_cert_public_address_and_irk_from_bt_config()
        rpa_address = self.cert_advertiser_.advertise_public_extended_pdu()
        id_addr = self.dut_scanner_.scan_for_address_with_irk(cert_public_address, ble_address_types["public"], irk)
        self.dut_scanner_.stop_scanning()
        return id_addr

    def __create_le_bond_oob_single_sided(self,
                                          wait_for_oob_data=True,
                                          wait_for_device_bonded=True,
                                          addr=None,
                                          addr_type=ble_address_types["random"]):
        oob_data = self.cert_security_.generate_oob_data(Security.TRANSPORT_LE, wait_for_oob_data)
        if wait_for_oob_data:
            assertThat(oob_data[0]).isEqualTo(0)
            assertThat(oob_data[1]).isNotNone()
        self.dut_security_.create_bond_out_of_band(oob_data[1], addr, addr_type, wait_for_device_bonded)
        return oob_data[1].to_sl4a_address()

    def __create_le_bond_oob_double_sided(self,
                                          wait_for_oob_data=True,
                                          wait_for_device_bonded=True,
                                          addr=None,
                                          addr_type=ble_address_types["random"]):
        # Genearte OOB data on DUT, but we don't use it
        self.dut_security_.generate_oob_data(Security.TRANSPORT_LE, wait_for_oob_data)
        self.__create_le_bond_oob_single_sided(wait_for_oob_data, wait_for_device_bonded, addr, addr_type)

    def __test_le_l2cap_insecure_coc(self):
        logging.info("Testing insecure L2CAP CoC")
        cert_rpa = self.__scan_for_cert_by_name()

        # Listen on an insecure l2cap coc on the cert
        psm = self.cert_l2cap_.listen_using_l2cap_le_coc(False)
        self.dut_l2cap_.create_l2cap_le_coc(cert_rpa, psm, False)

        # Cleanup
        self.dut_scanner_.stop_scanning()
        self.dut_l2cap_.close_l2cap_le_coc_client()
        self.cert_advertiser_.stop_advertising()
        self.cert_l2cap_.close_l2cap_le_coc_server()

    def __test_le_l2cap_secure_coc(self):
        logging.info("Testing secure L2CAP CoC")
        cert_rpa = self.__create_le_bond_oob_single_sided()

        # Listen on an secure l2cap coc on the cert
        psm = self.cert_l2cap_.listen_using_l2cap_le_coc(True)
        self.dut_l2cap_.create_l2cap_le_coc(cert_rpa, psm, True)

        # Cleanup
        self.dut_scanner_.stop_scanning()
        self.dut_l2cap_.close_l2cap_le_coc_client()
        self.cert_advertiser_.stop_advertising()
        self.cert_l2cap_.close_l2cap_le_coc_server()
        self.dut_security_.remove_all_bonded_devices()
        self.cert_security_.remove_all_bonded_devices()

    def __test_le_l2cap_secure_coc_after_irk_scan(self):
        logging.info("Testing secure L2CAP CoC after IRK scan")
        cert_config_addr, irk = self.__get_cert_public_address_and_irk_from_bt_config()
        cert_id_addr = self.__scan_for_irk()

        assertThat(cert_id_addr).isEqualTo(cert_config_addr)
        self.__create_le_bond_oob_single_sided(True, True, cert_id_addr, ble_address_types["public"])
        self.cert_advertiser_.stop_advertising()
        self.__test_le_l2cap_secure_coc()

    def __test_secure_le_l2cap_coc_stress(self):
        for i in range(0, 10):
            self.__test_le_l2cap_secure_coc()

    def __test_insecure_le_l2cap_coc_stress(self):
        for i in range(0, 10):
            self.__test_le_l2cap_insecure_coc()

    def __test_le_l2cap_coc_stress(self):
        #for i in range (0, 10):
        self.__test_le_l2cap_insecure_coc()
        self.__test_le_l2cap_secure_coc()

    def __test_secure_le_l2cap_coc_after_irk_scan_stress(self):
        for i in range(0, 10):
            self.__test_le_l2cap_secure_coc_after_irk_scan()
