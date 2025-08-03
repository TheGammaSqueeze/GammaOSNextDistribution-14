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

import logging
import queue

from blueberry.facade import common_pb2 as common
from blueberry.tests.gd.cert.closable import Closable
from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd_sl4a.lib.ble_lib import generate_ble_scan_objects
from blueberry.tests.gd_sl4a.lib.bt_constants import ble_scan_settings_modes
from blueberry.tests.gd_sl4a.lib.bt_constants import scan_result
from blueberry.tests.sl4a_sl4a.lib import sl4a_sl4a_base_test


class LeScanner(Closable):

    is_scanning = False
    device = None
    filter_list = None
    scan_settings = None
    scan_callback = None

    def __init__(self, device):
        self.device = device

    def __wait_for_scan_result_event(self, expected_event_name, timeout=60):
        try:
            event_info = self.device.ed.pop_event(expected_event_name, timeout)
        except queue.Empty as error:
            logging.error("Could not find scan result event: %s", expected_event_name)
            return None
        return event_info['data']['Result']['deviceInfo']['address']

    def scan_for_address_expect_none(self, address, addr_type):
        if self.is_scanning:
            print("Already scanning!")
            return None
        self.is_scanning = True
        logging.info("Start scanning for identity address {} or type {}".format(address, addr_type))
        self.device.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        self.device.sl4a.bleSetScanSettingsLegacy(False)
        self.filter_list, self.scan_settings, self.scan_callback = generate_ble_scan_objects(self.device.sl4a)
        expected_event_name = scan_result.format(self.scan_callback)

        # Start scanning on SL4A DUT
        self.device.sl4a.bleSetScanFilterDeviceAddressAndType(address, addr_type)
        self.device.sl4a.bleBuildScanFilter(self.filter_list)
        self.device.sl4a.bleStartBleScan(self.filter_list, self.scan_settings, self.scan_callback)

        # Verify that scan result is received on SL4A DUT
        advertising_address = self.__wait_for_scan_result_event(expected_event_name, 1)
        assertThat(advertising_address).isNone()
        logging.info("Filter advertisement with address {}".format(advertising_address))
        return advertising_address

    def scan_for_address(self, address, addr_type):
        if self.is_scanning:
            print("Already scanning!")
            return None
        self.is_scanning = True
        logging.info("Start scanning for identity address {} or type {}".format(address, addr_type))
        self.device.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        self.device.sl4a.bleSetScanSettingsLegacy(False)
        self.filter_list, self.scan_settings, self.scan_callback = generate_ble_scan_objects(self.device.sl4a)
        expected_event_name = scan_result.format(self.scan_callback)

        # Start scanning on SL4A DUT
        self.device.sl4a.bleSetScanFilterDeviceAddressAndType(address, addr_type)
        self.device.sl4a.bleBuildScanFilter(self.filter_list)
        self.device.sl4a.bleStartBleScan(self.filter_list, self.scan_settings, self.scan_callback)

        # Verify that scan result is received on SL4A DUT
        advertising_address = self.__wait_for_scan_result_event(expected_event_name)
        assertThat(advertising_address).isNotNone()
        logging.info("Filter advertisement with address {}".format(advertising_address))
        return advertising_address

    def scan_for_address_with_irk(self, address, addr_type, irk):
        if self.is_scanning:
            print("Already scanning!")
            return None
        self.is_scanning = True
        logging.info("Start scanning for identity address {} or type {} using irk {}".format(address, addr_type, irk))
        self.device.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        self.device.sl4a.bleSetScanSettingsLegacy(False)
        self.filter_list, self.scan_settings, self.scan_callback = generate_ble_scan_objects(self.device.sl4a)
        expected_event_name = scan_result.format(self.scan_callback)

        # Start scanning on SL4A DUT
        self.device.sl4a.bleSetScanFilterDeviceAddressTypeAndIrkHexString(address, addr_type, irk)
        self.device.sl4a.bleBuildScanFilter(self.filter_list)
        self.device.sl4a.bleStartBleScan(self.filter_list, self.scan_settings, self.scan_callback)

        # Verify that scan result is received on SL4A DUT
        advertising_address = self.__wait_for_scan_result_event(expected_event_name)
        assertThat(advertising_address).isNotNone()
        logging.info("Filter advertisement with address {}".format(advertising_address))
        return advertising_address

    def scan_for_address_with_irk_pending_intent(self, address, addr_type, irk):
        if self.is_scanning:
            print("Already scanning!")
            return None
        self.is_scanning = True
        logging.info("Start scanning for identity address {} or type {} using irk {}".format(address, addr_type, irk))
        self.device.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        self.device.sl4a.bleSetScanSettingsLegacy(False)
        self.filter_list, self.scan_settings, self.scan_callback = generate_ble_scan_objects(self.device.sl4a)
        # Hard code here since callback index iterates and will cause this to fail if ran
        # Second as the impl in SL4A sends this since it's a single callback for broadcast.
        expected_event_name = "BleScan1onScanResults"

        # Start scanning on SL4A DUT
        self.device.sl4a.bleSetScanFilterDeviceAddressTypeAndIrkHexString(address, addr_type, irk)
        self.device.sl4a.bleBuildScanFilter(self.filter_list)
        self.device.sl4a.bleStartBleScanPendingIntent(self.filter_list, self.scan_settings)

        # Verify that scan result is received on SL4A DUT
        advertising_address = self.__wait_for_scan_result_event(expected_event_name)
        assertThat(advertising_address).isNotNone()
        logging.info("Filter advertisement with address {}".format(advertising_address))
        return advertising_address

    def scan_for_name(self, name):
        if self.is_scanning:
            print("Already scanning!")
            return
        self.is_scanning = True
        logging.info("Start scanning for name {}".format(name))
        self.device.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        self.device.sl4a.bleSetScanSettingsLegacy(False)
        self.filter_list, self.scan_settings, self.scan_callback = generate_ble_scan_objects(self.device.sl4a)
        expected_event_name = scan_result.format(1)
        self.device.ed.clear_events(expected_event_name)

        # Start scanning on SL4A DUT
        self.device.sl4a.bleSetScanFilterDeviceName(name)
        self.device.sl4a.bleBuildScanFilter(self.filter_list)
        self.device.sl4a.bleStartBleScanPendingIntent(self.filter_list, self.scan_settings)

        # Verify that scan result is received on SL4A DUT
        advertising_address = self.__wait_for_scan_result_event(expected_event_name)
        assertThat(advertising_address).isNotNone()
        logging.info("Filter advertisement with address {}".format(advertising_address))
        return advertising_address

    def stop_scanning(self):
        """
        Warning: no java callback registered for this
        """
        if self.is_scanning:
            logging.info("Stopping scan")
            self.device.sl4a.bleStopBleScan(self.scan_callback)
            self.is_scanning = False

    def close(self):
        self.stop_scanning()
        self.device = None
