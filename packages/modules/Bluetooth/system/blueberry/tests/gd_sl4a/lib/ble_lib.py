#!/usr/bin/env python3
#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
"""
Ble libraries
"""

import time
import queue
import logging

from blueberry.tests.gd_sl4a.lib.bt_constants import ble_advertise_settings_modes
from blueberry.tests.gd_sl4a.lib.bt_constants import small_timeout
from blueberry.tests.gd_sl4a.lib.bt_constants import adv_fail
from blueberry.tests.gd_sl4a.lib.bt_constants import adv_succ
from blueberry.tests.gd_sl4a.lib.bt_constants import advertising_set_on_own_address_read
from blueberry.tests.gd_sl4a.lib.bt_constants import advertising_set_started
from blueberry.tests.gd_sl4a.lib.bt_constants import bluetooth_on
from blueberry.tests.gd_sl4a.lib.bt_constants import bluetooth_off
from blueberry.tests.gd_sl4a.lib.bt_constants import bt_default_timeout


def enable_bluetooth(sl4a, ed):
    if sl4a.bluetoothCheckState():
        return True

    sl4a.bluetoothToggleState(True)
    expected_bluetooth_on_event_name = bluetooth_on
    try:
        ed.pop_event(expected_bluetooth_on_event_name, bt_default_timeout)
    except Exception:
        logging.info("Failed to toggle Bluetooth on (no broadcast received)")
        if sl4a.bluetoothCheckState():
            logging.info(".. actual state is ON")
            return True
        logging.info(".. actual state is OFF")
        return False

    return True


def disable_bluetooth(sl4a, ed):
    if not sl4a.bluetoothCheckState():
        return True
    sl4a.bluetoothToggleState(False)
    expected_bluetooth_off_event_name = bluetooth_off
    try:
        ed.pop_event(expected_bluetooth_off_event_name, bt_default_timeout)
    except Exception:
        logging.info("Failed to toggle Bluetooth off (no broadcast received)")
        if sl4a.bluetoothCheckState():
            logging.info(".. actual state is ON")
            return False
        logging.info(".. actual state is OFF")
        return True
    return True


def generate_ble_scan_objects(sl4a):
    """Generate generic LE scan objects.

    Args:
        sl4a: The SL4A object to generate LE scan objects from.

    Returns:
        filter_list: The generated scan filter list id.
        scan_settings: The generated scan settings id.
        scan_callback: The generated scan callback id.
    """
    filter_list = sl4a.bleGenFilterList()
    scan_settings = sl4a.bleBuildScanSetting()
    scan_callback = sl4a.bleGenScanCallback()
    return filter_list, scan_settings, scan_callback


def generate_ble_advertise_objects(sl4a):
    """Generate generic LE advertise objects.

    Args:
        sl4a: The SL4A object to generate advertise LE objects from.

    Returns:
        advertise_callback: The generated advertise callback id.
        advertise_data: The generated advertise data id.
        advertise_settings: The generated advertise settings id.
    """
    advertise_callback = sl4a.bleGenBleAdvertiseCallback()
    advertise_data = sl4a.bleBuildAdvertiseData()
    advertise_settings = sl4a.bleBuildAdvertiseSettings()
    return advertise_callback, advertise_data, advertise_settings


class BleLib():

    def __init__(self, dut):
        self.advertisement_list = []
        self.dut = dut
        self.default_timeout = 5
        self.set_advertisement_list = []
        self.generic_uuid = "0000{}-0000-1000-8000-00805f9b34fb"

    def _verify_ble_adv_started(self, advertise_callback):
        """Helper for verifying if an advertisment started or not"""
        regex = "({}|{})".format(adv_succ.format(advertise_callback), adv_fail.format(advertise_callback))
        try:
            event = self.dut.ed.pop_events(regex, 5, small_timeout)
        except queue.Empty:
            logging.error("Failed to get success or failed event.")
            return
        if event[0]["name"] == adv_succ.format(advertise_callback):
            logging.info("Advertisement started successfully.")
            return True
        else:
            logging.info("Advertisement failed to start.")
            return False

    def start_generic_connectable_advertisement(self, line):
        """Start a connectable LE advertisement"""
        scan_response = None
        if line:
            scan_response = bool(line)
        self.dut.sl4a.bleSetAdvertiseSettingsAdvertiseMode(ble_advertise_settings_modes['low_latency'])
        self.dut.sl4a.bleSetAdvertiseSettingsIsConnectable(True)
        advertise_callback, advertise_data, advertise_settings = (generate_ble_advertise_objects(self.dut.sl4a))
        if scan_response:
            self.dut.sl4a.bleStartBleAdvertisingWithScanResponse(advertise_callback, advertise_data, advertise_settings,
                                                                 advertise_data)
        else:
            self.dut.sl4a.bleStartBleAdvertising(advertise_callback, advertise_data, advertise_settings)
        if self._verify_ble_adv_started(advertise_callback):
            logging.info("Tracking Callback ID: {}".format(advertise_callback))
            self.advertisement_list.append(advertise_callback)
            logging.info(self.advertisement_list)

    def start_connectable_advertisement_set(self, line):
        """Start Connectable Advertisement Set"""
        adv_callback = self.dut.sl4a.bleAdvSetGenCallback()
        adv_data = {
            "includeDeviceName": True,
        }
        self.dut.sl4a.bleAdvSetStartAdvertisingSet({
            "connectable": True,
            "legacyMode": False,
            "primaryPhy": "PHY_LE_1M",
            "secondaryPhy": "PHY_LE_1M",
            "interval": 320
        }, adv_data, None, None, None, 0, 0, adv_callback)
        evt = self.dut.ed.pop_event(advertising_set_started.format(adv_callback), self.default_timeout)
        set_id = evt['data']['setId']
        logging.error("did not receive the set started event!")
        evt = self.dut.ed.pop_event(advertising_set_on_own_address_read.format(set_id), self.default_timeout)
        address = evt['data']['address']
        logging.info("Advertiser address is: {}".format(str(address)))
        self.set_advertisement_list.append(adv_callback)

    def stop_all_advertisement_set(self, line):
        """Stop all Advertisement Sets"""
        for adv in self.set_advertisement_list:
            try:
                self.dut.sl4a.bleAdvSetStopAdvertisingSet(adv)
            except Exception as err:
                logging.error("Failed to stop advertisement: {}".format(err))

    def adv_add_service_uuid_list(self, line):
        """Add service UUID to the LE advertisement inputs:
         [uuid1 uuid2 ... uuidN]"""
        uuids = line.split()
        uuid_list = []
        for uuid in uuids:
            if len(uuid) == 4:
                uuid = self.generic_uuid.format(line)
            uuid_list.append(uuid)
        self.dut.sl4a.bleSetAdvertiseDataSetServiceUuids(uuid_list)

    def adv_data_include_local_name(self, is_included):
        """Include local name in the advertisement. inputs: [true|false]"""
        self.dut.sl4a.bleSetAdvertiseDataIncludeDeviceName(bool(is_included))

    def adv_data_include_tx_power_level(self, is_included):
        """Include tx power level in the advertisement. inputs: [true|false]"""
        self.dut.sl4a.bleSetAdvertiseDataIncludeTxPowerLevel(bool(is_included))

    def adv_data_add_manufacturer_data(self, line):
        """Include manufacturer id and data to the advertisment:
        [id data1 data2 ... dataN]"""
        info = line.split()
        manu_id = int(info[0])
        manu_data = []
        for data in info[1:]:
            manu_data.append(int(data))
        self.dut.sl4a.bleAddAdvertiseDataManufacturerId(manu_id, manu_data)

    def start_generic_nonconnectable_advertisement(self, line):
        """Start a nonconnectable LE advertisement"""
        self.dut.sl4a.bleSetAdvertiseSettingsAdvertiseMode(ble_advertise_settings_modes['low_latency'])
        self.dut.sl4a.bleSetAdvertiseSettingsIsConnectable(False)
        advertise_callback, advertise_data, advertise_settings = (generate_ble_advertise_objects(self.dut.sl4a))
        self.dut.sl4a.bleStartBleAdvertising(advertise_callback, advertise_data, advertise_settings)
        if self._verify_ble_adv_started(advertise_callback):
            logging.info("Tracking Callback ID: {}".format(advertise_callback))
            self.advertisement_list.append(advertise_callback)
            logging.info(self.advertisement_list)

    def stop_all_advertisements(self, line):
        """Stop all LE advertisements"""
        for callback_id in self.advertisement_list:
            logging.info("Stopping Advertisement {}".format(callback_id))
            self.dut.sl4a.bleStopBleAdvertising(callback_id)
            time.sleep(1)
        self.advertisement_list = []

    def ble_stop_advertisement(self, callback_id):
        """Stop an LE advertisement"""
        if not callback_id:
            logging.info("Need a callback ID")
            return
        callback_id = int(callback_id)
        if callback_id not in self.advertisement_list:
            logging.info("Callback not in list of advertisements.")
            return
        self.dut.sl4a.bleStopBleAdvertising(callback_id)
        self.advertisement_list.remove(callback_id)

    def start_max_advertisements(self, line):
        scan_response = None
        if line:
            scan_response = bool(line)
        while (True):
            try:
                self.dut.sl4a.bleSetAdvertiseSettingsAdvertiseMode(ble_advertise_settings_modes['low_latency'])
                self.dut.sl4a.bleSetAdvertiseSettingsIsConnectable(True)
                advertise_callback, advertise_data, advertise_settings = (generate_ble_advertise_objects(self.dut.sl4a))
                if scan_response:
                    self.dut.sl4a.bleStartBleAdvertisingWithScanResponse(advertise_callback, advertise_data,
                                                                         advertise_settings, advertise_data)
                else:
                    self.dut.sl4a.bleStartBleAdvertising(advertise_callback, advertise_data, advertise_settings)
                if self._verify_ble_adv_started(advertise_callback):
                    logging.info("Tracking Callback ID: {}".format(advertise_callback))
                    self.advertisement_list.append(advertise_callback)
                    logging.info(self.advertisement_list)
                else:
                    logging.info("Advertisements active: {}".format(len(self.advertisement_list)))
                    return False
            except Exception as err:
                logging.info("Advertisements active: {}".format(len(self.advertisement_list)))
                return True
