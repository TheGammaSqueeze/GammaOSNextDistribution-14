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
Original file:
    tools/test/connectivity/acts_tests/acts_contrib/test_utils/bt/bt_gatt_utils.py
"""

import logging
import pprint
from queue import Empty

from blueberry.utils.bt_gatt_constants import GattCallbackError
from blueberry.utils.bt_gatt_constants import GattCallbackString
from blueberry.utils.bt_gatt_constants import GattCharacteristic
from blueberry.utils.bt_gatt_constants import GattConnectionState
from blueberry.utils.bt_gatt_constants import GattDescriptor
from blueberry.utils.bt_gatt_constants import GattPhyMask
from blueberry.utils.bt_gatt_constants import GattServiceType
from blueberry.utils.bt_gatt_constants import GattTransport
from blueberry.utils.bt_test_utils import BtTestUtilsError
from blueberry.utils.bt_test_utils import get_mac_address_of_generic_advertisement
from mobly.controllers.android_device import AndroidDevice
from mobly.controllers.android_device_lib.event_dispatcher import EventDispatcher
from mobly.controllers.android_device_lib.sl4a_client import Sl4aClient

default_timeout = 10
log = logging


class GattTestUtilsError(Exception):
    pass


def setup_gatt_connection(central: AndroidDevice,
                          mac_address,
                          autoconnect,
                          transport=GattTransport.TRANSPORT_AUTO,
                          opportunistic=False,
                          timeout_seconds=default_timeout):
    gatt_callback = central.sl4a.gattCreateGattCallback()
    log.info("Gatt Connect to mac address {}.".format(mac_address))
    bluetooth_gatt = central.sl4a.gattClientConnectGatt(gatt_callback, mac_address, autoconnect, transport,
                                                        opportunistic, GattPhyMask.PHY_LE_1M_MASK)
    expected_event = GattCallbackString.GATT_CONN_CHANGE.format(gatt_callback)
    try:
        event = central.ed.pop_event(expected_event, timeout_seconds)
    except Empty:
        close_gatt_client(central, bluetooth_gatt)
        raise GattTestUtilsError("Could not establish a connection to "
                                 "peripheral. Expected event: {}".format(expected_event))
    logging.info("Got connection event {}".format(event))
    if event['data']['State'] != GattConnectionState.STATE_CONNECTED:
        close_gatt_client(central, bluetooth_gatt)
        raise GattTestUtilsError("Could not establish a connection to "
                                 "peripheral. Event Details: {}".format(pprint.pformat(event)))
    return bluetooth_gatt, gatt_callback


def wait_for_gatt_connection(central: AndroidDevice, gatt_callback, bluetooth_gatt, timeout):
    expected_event = GattCallbackString.GATT_CONN_CHANGE.format(gatt_callback)
    try:
        event = central.ed.pop_event(expected_event, timeout=timeout)
    except Empty:
        close_gatt_client(central, bluetooth_gatt)
        raise GattTestUtilsError("Could not establish a connection to "
                                 "peripheral. Expected event: {}".format(expected_event))
    if event['data']['State'] != GattConnectionState.STATE_CONNECTED:
        close_gatt_client(central, bluetooth_gatt)
        try:
            central.sl4a.gattClientClose(bluetooth_gatt)
        except Exception:
            logging.debug("Failed to close gatt client.")
        raise GattTestUtilsError("Could not establish a connection to "
                                 "peripheral. Event Details: {}".format(pprint.pformat(event)))


def close_gatt_client(central: AndroidDevice, bluetooth_gatt):
    try:
        central.sl4a.gattClientClose(bluetooth_gatt)
    except Exception:
        log.debug("Failed to close gatt client.")


def disconnect_gatt_connection(central: AndroidDevice, bluetooth_gatt, gatt_callback):
    central.sl4a.gattClientDisconnect(bluetooth_gatt)
    wait_for_gatt_disconnect_event(central, gatt_callback)
    return


def wait_for_gatt_disconnect_event(central: AndroidDevice, gatt_callback):
    expected_event = GattCallbackString.GATT_CONN_CHANGE.format(gatt_callback)
    try:
        event = central.ed.pop_event(expected_event, default_timeout)
    except Empty:
        raise GattTestUtilsError(GattCallbackError.GATT_CONN_CHANGE_ERR.format(expected_event))
    found_state = event['data']['State']
    expected_state = GattConnectionState.STATE_DISCONNECTED
    if found_state != expected_state:
        raise GattTestUtilsError("GATT connection state change expected {}, found {}".format(
            expected_event, found_state))
    return


def orchestrate_gatt_connection(central: AndroidDevice,
                                peripheral: AndroidDevice,
                                transport=GattTransport.TRANSPORT_LE,
                                mac_address=None,
                                autoconnect=False,
                                opportunistic=False):
    adv_callback = None
    if mac_address is None:
        if transport == GattTransport.TRANSPORT_LE:
            try:
                mac_address, adv_callback, scan_callback = (get_mac_address_of_generic_advertisement(
                    central, peripheral))
            except BtTestUtilsError as err:
                raise GattTestUtilsError("Error in getting mac address: {}".format(err))
        else:
            mac_address = peripheral.sl4a.bluetoothGetLocalAddress()
            adv_callback = None
    bluetooth_gatt, gatt_callback = setup_gatt_connection(central, mac_address, autoconnect, transport, opportunistic)
    return bluetooth_gatt, gatt_callback, adv_callback


def run_continuous_write_descriptor(cen_droid: Sl4aClient,
                                    cen_ed: EventDispatcher,
                                    per_droid: Sl4aClient,
                                    per_ed: EventDispatcher,
                                    gatt_server,
                                    gatt_server_callback,
                                    bluetooth_gatt,
                                    services_count,
                                    discovered_services_index,
                                    number_of_iterations=100000):
    log.info("Starting continuous write")
    bt_device_id = 0
    status = 1
    offset = 1
    test_value = [1, 2, 3, 4, 5, 6, 7]
    test_value_return = [1, 2, 3]
    for _ in range(number_of_iterations):
        try:
            for i in range(services_count):
                characteristic_uuids = (cen_droid.gattClientGetDiscoveredCharacteristicUuids(
                    discovered_services_index, i))
                log.info(characteristic_uuids)
                for characteristic in characteristic_uuids:
                    descriptor_uuids = (cen_droid.gattClientGetDiscoveredDescriptorUuids(
                        discovered_services_index, i, characteristic))
                    log.info(descriptor_uuids)
                    for descriptor in descriptor_uuids:
                        cen_droid.gattClientDescriptorSetValue(bluetooth_gatt, discovered_services_index, i,
                                                               characteristic, descriptor, test_value)
                        cen_droid.gattClientWriteDescriptor(bluetooth_gatt, discovered_services_index, i,
                                                            characteristic, descriptor)
                        expected_event = \
                            GattCallbackString.DESC_WRITE_REQ.format(
                                gatt_server_callback)
                        try:
                            event = per_ed.pop_event(expected_event, default_timeout)
                        except Empty:
                            log.error(GattCallbackError.DESC_WRITE_REQ_ERR.format(expected_event))
                            return False
                        request_id = event['data']['requestId']
                        found_value = event['data']['value']
                        if found_value != test_value:
                            log.error("Values didn't match. Found: {}, Expected: " "{}".format(found_value, test_value))
                        per_droid.gattServerSendResponse(gatt_server, bt_device_id, request_id, status, offset,
                                                         test_value_return)
                        expected_event = GattCallbackString.DESC_WRITE.format(bluetooth_gatt)
                        try:
                            cen_ed.pop_event(expected_event, default_timeout)
                        except Empty:
                            log.error(GattCallbackError.DESC_WRITE_ERR.format(expected_event))
                            raise Exception("Thread ended prematurely.")
        except Exception as err:
            log.error("Continuing but found exception: {}".format(err))


def setup_characteristics_and_descriptors_read_write(droid: Sl4aClient):
    characteristic_input = [
        {
            'uuid': "aa7edd5a-4d1d-4f0e-883a-d145616a1630",
            'property': GattCharacteristic.PROPERTY_WRITE | GattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            'permission': GattCharacteristic.PERMISSION_WRITE
        },
        {
            'uuid': "21c0a0bf-ad51-4a2d-8124-b74003e4e8c8",
            'property': GattCharacteristic.PROPERTY_WRITE | GattCharacteristic.PROPERTY_READ,
            'permission': GattCharacteristic.PERMISSION_READ
        },
        {
            'uuid': "6774191f-6ec3-4aa2-b8a8-cf830e41fda6",
            'property': GattCharacteristic.PROPERTY_NOTIFY | GattCharacteristic.PROPERTY_READ,
            'permission': GattCharacteristic.PERMISSION_READ
        },
    ]
    descriptor_input = [{
        'uuid': "aa7edd5a-4d1d-4f0e-883a-d145616a1630",
        'property': GattDescriptor.PERMISSION_READ[0] | GattDescriptor.PERMISSION_WRITE[0],
    }, {
        'uuid': "76d5ed92-ca81-4edb-bb6b-9f019665fb32",
        'property': GattDescriptor.PERMISSION_READ[0] | GattDescriptor.PERMISSION_WRITE[0],
    }]
    characteristic_list = setup_gatt_characteristics(droid, characteristic_input)
    descriptor_list = setup_gatt_descriptors(droid, descriptor_input)
    return characteristic_list, descriptor_list


def setup_multiple_services(peripheral: AndroidDevice):
    per_droid, per_ed = peripheral.sl4a, peripheral.sl4a.ed
    gatt_server_callback = per_droid.gattServerCreateGattServerCallback()
    gatt_server = per_droid.gattServerOpenGattServer(gatt_server_callback)
    characteristic_list, descriptor_list = (setup_characteristics_and_descriptors_read_write(per_droid))
    per_droid.gattServerCharacteristicAddDescriptor(characteristic_list[1], descriptor_list[0])
    per_droid.gattServerCharacteristicAddDescriptor(characteristic_list[2], descriptor_list[1])
    gattService = per_droid.gattServerCreateService("00000000-0000-1000-8000-00805f9b34fb",
                                                    GattServiceType.SERVICE_TYPE_PRIMARY)
    gattService2 = per_droid.gattServerCreateService("FFFFFFFF-0000-1000-8000-00805f9b34fb",
                                                     GattServiceType.SERVICE_TYPE_PRIMARY)
    gattService3 = per_droid.gattServerCreateService("3846D7A0-69C8-11E4-BA00-0002A5D5C51B",
                                                     GattServiceType.SERVICE_TYPE_PRIMARY)
    for characteristic in characteristic_list:
        per_droid.gattServerAddCharacteristicToService(gattService, characteristic)
    per_droid.gattServerAddService(gatt_server, gattService)
    expected_event = GattCallbackString.SERV_ADDED.format(gatt_server_callback)
    try:
        per_ed.pop_event(expected_event, default_timeout)
    except Empty:
        peripheral.sl4a.gattServerClose(gatt_server)
        raise GattTestUtilsError(GattCallbackError.SERV_ADDED_ERR.format(expected_event))
    for characteristic in characteristic_list:
        per_droid.gattServerAddCharacteristicToService(gattService2, characteristic)
    per_droid.gattServerAddService(gatt_server, gattService2)
    try:
        per_ed.pop_event(expected_event, default_timeout)
    except Empty:
        peripheral.sl4a.gattServerClose(gatt_server)
        raise GattTestUtilsError(GattCallbackError.SERV_ADDED_ERR.format(expected_event))
    for characteristic in characteristic_list:
        per_droid.gattServerAddCharacteristicToService(gattService3, characteristic)
    per_droid.gattServerAddService(gatt_server, gattService3)
    try:
        per_ed.pop_event(expected_event, default_timeout)
    except Empty:
        peripheral.sl4a.gattServerClose(gatt_server)
        raise GattTestUtilsError(GattCallbackError.SERV_ADDED_ERR.format(expected_event))
    return gatt_server_callback, gatt_server


def setup_characteristics_and_descriptors_notify_read(droid: Sl4aClient):
    characteristic_input = [
        {
            'uuid': "aa7edd5a-4d1d-4f0e-883a-d145616a1630",
            'property': GattCharacteristic.PROPERTY_WRITE | GattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            'permission': GattCharacteristic.PROPERTY_WRITE
        },
        {
            'uuid': "21c0a0bf-ad51-4a2d-8124-b74003e4e8c8",
            'property': GattCharacteristic.PROPERTY_NOTIFY | GattCharacteristic.PROPERTY_READ,
            'permission': GattCharacteristic.PERMISSION_READ
        },
        {
            'uuid': "6774191f-6ec3-4aa2-b8a8-cf830e41fda6",
            'property': GattCharacteristic.PROPERTY_NOTIFY | GattCharacteristic.PROPERTY_READ,
            'permission': GattCharacteristic.PERMISSION_READ
        },
    ]
    descriptor_input = [{
        'uuid': "aa7edd5a-4d1d-4f0e-883a-d145616a1630",
        'property': GattDescriptor.PERMISSION_READ[0] | GattDescriptor.PERMISSION_WRITE[0],
    }, {
        'uuid': "76d5ed92-ca81-4edb-bb6b-9f019665fb32",
        'property': GattDescriptor.PERMISSION_READ[0] | GattDescriptor.PERMISSION_WRITE[0],
    }]
    characteristic_list = setup_gatt_characteristics(droid, characteristic_input)
    descriptor_list = setup_gatt_descriptors(droid, descriptor_input)
    return characteristic_list, descriptor_list


def setup_gatt_characteristics(droid: Sl4aClient, input):
    characteristic_list = []
    for item in input:
        index = droid.gattServerCreateBluetoothGattCharacteristic(item['uuid'], item['property'], item['permission'])
        characteristic_list.append(index)
    return characteristic_list


def setup_gatt_descriptors(droid: Sl4aClient, input):
    descriptor_list = []
    for item in input:
        index = droid.gattServerCreateBluetoothGattDescriptor(
            item['uuid'],
            item['property'],
        )
        descriptor_list.append(index)
    log.info("setup descriptor list: {}".format(descriptor_list))
    return descriptor_list


def setup_gatt_mtu(central: AndroidDevice, bluetooth_gatt, gatt_callback, mtu):
    """utility function to set mtu for GATT connection.

    Steps:
    1. Request mtu change.
    2. Check if the mtu is changed to the new value

    Args:
        central: test device for client to scan.
        bluetooth_gatt: GATT object
        mtu: new mtu value to be set

    Returns:
        If success, return True.
        if fail, return False
    """
    central.sl4a.gattClientRequestMtu(bluetooth_gatt, mtu)
    expected_event = GattCallbackString.MTU_CHANGED.format(gatt_callback)
    try:
        mtu_event = central.ed.pop_event(expected_event, default_timeout)
        mtu_size_found = mtu_event['data']['MTU']
        if mtu_size_found != mtu:
            log.error("MTU size found: {}, expected: {}".format(mtu_size_found, mtu))
            return False
    except Empty:
        log.error(GattCallbackError.MTU_CHANGED_ERR.format(expected_event))
        return False
    return True


def log_gatt_server_uuids(central: AndroidDevice, discovered_services_index, bluetooth_gatt=None):
    services_count = central.sl4a.gattClientGetDiscoveredServicesCount(discovered_services_index)
    for i in range(services_count):
        service = central.sl4a.gattClientGetDiscoveredServiceUuid(discovered_services_index, i)
        log.info("Discovered service uuid {}".format(service))
        characteristic_uuids = (central.sl4a.gattClientGetDiscoveredCharacteristicUuids(discovered_services_index, i))
        for j in range(len(characteristic_uuids)):
            descriptor_uuids = (central.sl4a.gattClientGetDiscoveredDescriptorUuidsByIndex(
                discovered_services_index, i, j))
            if bluetooth_gatt:
                char_inst_id = central.sl4a.gattClientGetCharacteristicInstanceId(bluetooth_gatt,
                                                                                  discovered_services_index, i, j)
                log.info("Discovered characteristic handle uuid: {} {}".format(
                    hex(char_inst_id), characteristic_uuids[j]))
                for k in range(len(descriptor_uuids)):
                    desc_inst_id = central.sl4a.gattClientGetDescriptorInstanceId(bluetooth_gatt,
                                                                                  discovered_services_index, i, j, k)
                    log.info("Discovered descriptor handle uuid: {} {}".format(hex(desc_inst_id), descriptor_uuids[k]))
            else:
                log.info("Discovered characteristic uuid: {}".format(characteristic_uuids[j]))
                for k in range(len(descriptor_uuids)):
                    log.info("Discovered descriptor uuid {}".format(descriptor_uuids[k]))
