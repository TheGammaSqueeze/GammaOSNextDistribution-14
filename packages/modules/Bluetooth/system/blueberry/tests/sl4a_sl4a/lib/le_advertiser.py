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
import logging
import queue

from blueberry.facade import common_pb2 as common
from blueberry.tests.gd.cert.closable import Closable
from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd_sl4a.lib.ble_lib import generate_ble_advertise_objects
from blueberry.tests.gd_sl4a.lib.bt_constants import adv_succ
from blueberry.tests.gd_sl4a.lib.bt_constants import ble_advertise_settings_modes
from blueberry.tests.sl4a_sl4a.lib import sl4a_sl4a_base_test


class LeAdvertiser(Closable):

    is_advertising = False
    device = None
    default_timeout = 10  # seconds
    advertise_callback = None
    advertise_data = None
    advertise_settings = None

    def __init__(self, device):
        self.device = device

    def __wait_for_event(self, expected_event_name):
        try:
            event_info = self.device.ed.pop_event(expected_event_name, self.default_timeout)
            logging.info(event_info)
        except queue.Empty as error:
            logging.error("Failed to find event: %s", expected_event_name)
            return False
        return True

    def advertise_public_extended_pdu(self, address_type=common.RANDOM_DEVICE_ADDRESS, name="SL4A Device"):
        if self.is_advertising:
            logging.info("Already advertising!")
            return
        logging.info("Configuring advertisement with address type %d", address_type)
        self.is_advertising = True
        self.device.sl4a.bleSetScanSettingsLegacy(False)
        self.device.sl4a.bleSetAdvertiseSettingsIsConnectable(True)
        self.device.sl4a.bleSetAdvertiseDataIncludeDeviceName(True)
        self.device.sl4a.bleSetAdvertiseSettingsAdvertiseMode(ble_advertise_settings_modes['low_latency'])
        self.device.sl4a.bleSetAdvertiseSettingsOwnAddressType(address_type)
        self.advertise_callback, self.advertise_data, self.advertise_settings = generate_ble_advertise_objects(
            self.device.sl4a)
        self.device.sl4a.bleStartBleAdvertising(self.advertise_callback, self.advertise_data, self.advertise_settings)

        # Wait for SL4A cert to start advertising
        assertThat(self.__wait_for_event(adv_succ.format(self.advertise_callback))).isTrue()
        logging.info("Advertising started")

    def get_local_advertising_name(self):
        return self.device.sl4a.bluetoothGetLocalName()

    def stop_advertising(self):
        if self.is_advertising:
            logging.info("Stopping advertisement")
            self.device.sl4a.bleStopBleAdvertising(self.advertise_callback)
            self.is_advertising = False

    def close(self):
        self.stop_advertising()
        self.device = None
