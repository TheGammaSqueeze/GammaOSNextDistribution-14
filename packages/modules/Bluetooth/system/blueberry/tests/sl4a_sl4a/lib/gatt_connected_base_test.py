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
This is base class for tests that exercises different GATT procedures between two connected devices.
Setup/Teardown methods take care of establishing connection, and doing GATT DB initialization/discovery.

Original file:
    tools/test/connectivity/acts_tests/acts_contrib/test_utils/bt/GattConnectedBaseTest.py
"""

import logging
from queue import Empty

from blueberry.tests.gd_sl4a.lib.bt_constants import bt_default_timeout
from blueberry.tests.sl4a_sl4a.lib.sl4a_sl4a_base_test import Sl4aSl4aBaseTestClass
from blueberry.utils.bt_gatt_constants import GattCallbackError
from blueberry.utils.bt_gatt_constants import GattCallbackString
from blueberry.utils.bt_gatt_constants import GattCharDesc
from blueberry.utils.bt_gatt_constants import GattCharacteristic
from blueberry.utils.bt_gatt_constants import GattDescriptor
from blueberry.utils.bt_gatt_constants import GattEvent
from blueberry.utils.bt_gatt_constants import GattMtuSize
from blueberry.utils.bt_gatt_constants import GattServiceType
from blueberry.utils.bt_gatt_utils import GattTestUtilsError
from blueberry.utils.bt_gatt_utils import disconnect_gatt_connection
from blueberry.utils.bt_gatt_utils import orchestrate_gatt_connection
from blueberry.utils.bt_gatt_utils import setup_gatt_characteristics
from blueberry.utils.bt_gatt_utils import setup_gatt_descriptors


class GattConnectedBaseTest(Sl4aSl4aBaseTestClass):

    TEST_SERVICE_UUID = "3846D7A0-69C8-11E4-BA00-0002A5D5C51B"
    READABLE_CHAR_UUID = "21c0a0bf-ad51-4a2d-8124-b74003e4e8c8"
    READABLE_DESC_UUID = "aa7edd5a-4d1d-4f0e-883a-d145616a1630"
    WRITABLE_CHAR_UUID = "aa7edd5a-4d1d-4f0e-883a-d145616a1630"
    WRITABLE_DESC_UUID = "76d5ed92-ca81-4edb-bb6b-9f019665fb32"
    NOTIFIABLE_CHAR_UUID = "b2c83efa-34ca-11e6-ac61-9e71128cae77"

    def setup_class(self):
        super().setup_class()
        self.central = self.dut
        self.peripheral = self.cert

    def setup_test(self):
        super(GattConnectedBaseTest, self).setup_test()

        self.gatt_server_callback, self.gatt_server = \
            self._setup_multiple_services()
        if not self.gatt_server_callback or not self.gatt_server:
            raise AssertionError('Service setup failed')

        self.bluetooth_gatt, self.gatt_callback, self.adv_callback = (orchestrate_gatt_connection(
            self.central, self.peripheral))
        self.peripheral.sl4a.bleStopBleAdvertising(self.adv_callback)

        self.mtu = GattMtuSize.MIN

        if self.central.sl4a.gattClientDiscoverServices(self.bluetooth_gatt):
            event = self._client_wait(GattEvent.GATT_SERV_DISC)
            self.discovered_services_index = event['data']['ServicesIndex']
        services_count = self.central.sl4a.gattClientGetDiscoveredServicesCount(self.discovered_services_index)
        self.test_service_index = None
        for i in range(services_count):
            disc_service_uuid = (self.central.sl4a.gattClientGetDiscoveredServiceUuid(
                self.discovered_services_index, i).upper())
            if disc_service_uuid == self.TEST_SERVICE_UUID:
                self.test_service_index = i
                break

        if not self.test_service_index:
            print("Service not found")
            return False

        connected_device_list = self.peripheral.sl4a.gattServerGetConnectedDevices(self.gatt_server)
        if len(connected_device_list) == 0:
            logging.info("No devices connected from peripheral.")
            return False

        return True

    def teardown_test(self):
        self.peripheral.sl4a.gattServerClearServices(self.gatt_server)
        self.peripheral.sl4a.gattServerClose(self.gatt_server)

        del self.gatt_server_callback
        del self.gatt_server

        self._orchestrate_gatt_disconnection(self.bluetooth_gatt, self.gatt_callback)

        return super(GattConnectedBaseTest, self).teardown_test()

    def _server_wait(self, gatt_event):
        return self._timed_pop(gatt_event, self.peripheral, self.gatt_server_callback)

    def _client_wait(self, gatt_event: GattEvent):
        return self._timed_pop(gatt_event, self.central, self.gatt_callback)

    def _timed_pop(self, gatt_event: GattEvent, sl4a, gatt_callback):
        expected_event = gatt_event["evt"].format(gatt_callback)
        try:
            return sl4a.ed.pop_event(expected_event, bt_default_timeout)
        except Empty as emp:
            raise AssertionError(gatt_event["err"].format(expected_event))

    def _setup_characteristics_and_descriptors(self, droid):
        characteristic_input = [
            {
                'uuid': self.WRITABLE_CHAR_UUID,
                'property': GattCharacteristic.PROPERTY_WRITE | GattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                'permission': GattCharacteristic.PERMISSION_WRITE
            },
            {
                'uuid': self.READABLE_CHAR_UUID,
                'property': GattCharacteristic.PROPERTY_READ,
                'permission': GattCharacteristic.PROPERTY_READ
            },
            {
                'uuid': self.NOTIFIABLE_CHAR_UUID,
                'property': GattCharacteristic.PROPERTY_NOTIFY | GattCharacteristic.PROPERTY_INDICATE,
                'permission': GattCharacteristic.PERMISSION_READ
            },
        ]
        descriptor_input = [{
            'uuid': self.WRITABLE_DESC_UUID,
            'property': GattDescriptor.PERMISSION_READ[0] | GattDescriptor.PERMISSION_WRITE[0]
        }, {
            'uuid': self.READABLE_DESC_UUID,
            'property': GattDescriptor.PERMISSION_READ[0] | GattDescriptor.PERMISSION_WRITE[0],
        }, {
            'uuid': GattCharDesc.GATT_CLIENT_CHARAC_CFG_UUID,
            'property': GattDescriptor.PERMISSION_READ[0] | GattDescriptor.PERMISSION_WRITE[0],
        }]
        characteristic_list = setup_gatt_characteristics(droid, characteristic_input)
        self.notifiable_char_index = characteristic_list[2]
        descriptor_list = setup_gatt_descriptors(droid, descriptor_input)
        return characteristic_list, descriptor_list

    def _orchestrate_gatt_disconnection(self, bluetooth_gatt, gatt_callback):
        logging.info("Disconnecting from peripheral device.")
        try:
            disconnect_gatt_connection(self.central, bluetooth_gatt, gatt_callback)
        except GattTestUtilsError as err:
            logging.error(err)
            return False
        self.central.sl4a.gattClientClose(bluetooth_gatt)
        return True

    def _find_service_added_event(self, gatt_server_callback, uuid):
        expected_event = GattCallbackString.SERV_ADDED.format(gatt_server_callback)
        try:
            event = self.peripheral.sl4a.ed.pop_event(expected_event, bt_default_timeout)
        except Empty:
            logging.error(GattCallbackError.SERV_ADDED_ERR.format(expected_event))
            return False
        if event['data']['serviceUuid'].lower() != uuid.lower():
            logging.error("Uuid mismatch. Found: {}, Expected {}.".format(event['data']['serviceUuid'], uuid))
            return False
        return True

    def _setup_multiple_services(self):
        gatt_server_callback = (self.peripheral.sl4a.gattServerCreateGattServerCallback())
        gatt_server = self.peripheral.sl4a.gattServerOpenGattServer(gatt_server_callback)
        characteristic_list, descriptor_list = (self._setup_characteristics_and_descriptors(self.peripheral.sl4a))
        self.peripheral.sl4a.gattServerCharacteristicAddDescriptor(characteristic_list[0], descriptor_list[0])
        self.peripheral.sl4a.gattServerCharacteristicAddDescriptor(characteristic_list[1], descriptor_list[1])
        self.peripheral.sl4a.gattServerCharacteristicAddDescriptor(characteristic_list[2], descriptor_list[2])
        gatt_service3 = self.peripheral.sl4a.gattServerCreateService(self.TEST_SERVICE_UUID,
                                                                     GattServiceType.SERVICE_TYPE_PRIMARY)
        for characteristic in characteristic_list:
            self.peripheral.sl4a.gattServerAddCharacteristicToService(gatt_service3, characteristic)
        self.peripheral.sl4a.gattServerAddService(gatt_server, gatt_service3)
        result = self._find_service_added_event(gatt_server_callback, self.TEST_SERVICE_UUID)
        if not result:
            return False, False
        return gatt_server_callback, gatt_server
