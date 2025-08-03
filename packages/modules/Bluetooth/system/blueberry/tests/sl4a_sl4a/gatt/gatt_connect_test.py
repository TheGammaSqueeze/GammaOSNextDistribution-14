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
This test script exercises different GATT connection tests.

Original location:
  tools/test/connectivity/acts_tests/tests/google/ble/gatt/GattConnectTest.py
"""

import logging
import time
from queue import Empty

from blueberry.tests.gd.cert.test_decorators import test_tracker_info
from blueberry.tests.gd_sl4a.lib.bt_constants import scan_result
from blueberry.tests.sl4a_sl4a.lib import sl4a_sl4a_base_test
from blueberry.utils.ble_scan_adv_constants import BleAdvertiseSettingsMode
from blueberry.utils.ble_scan_adv_constants import BleScanSettingsMatchNums
from blueberry.utils.ble_scan_adv_constants import BleScanSettingsModes
from blueberry.utils.bt_constants import BluetoothProfile
from blueberry.utils.bt_gatt_constants import GattCallbackError
from blueberry.utils.bt_gatt_constants import GattCallbackString
from blueberry.utils.bt_gatt_constants import GattCharacteristic
from blueberry.utils.bt_gatt_constants import GattConnectionState
from blueberry.utils.bt_gatt_constants import GattMtuSize
from blueberry.utils.bt_gatt_constants import GattPhyMask
from blueberry.utils.bt_gatt_constants import GattServiceType
from blueberry.utils.bt_gatt_constants import GattTransport
from blueberry.utils.bt_gatt_utils import GattTestUtilsError
from blueberry.utils.bt_gatt_utils import close_gatt_client
from blueberry.utils.bt_gatt_utils import disconnect_gatt_connection
from blueberry.utils.bt_gatt_utils import get_mac_address_of_generic_advertisement
from blueberry.utils.bt_gatt_utils import log_gatt_server_uuids
from blueberry.utils.bt_gatt_utils import orchestrate_gatt_connection
from blueberry.utils.bt_gatt_utils import setup_gatt_connection
from blueberry.utils.bt_gatt_utils import setup_multiple_services
from blueberry.utils.bt_gatt_utils import wait_for_gatt_disconnect_event
from blueberry.utils.bt_test_utils import clear_bonded_devices
from blueberry.tests.gd.cert.truth import assertThat
from mobly import asserts
from mobly import test_runner

PHYSICAL_DISCONNECT_TIMEOUT = 5


class GattConnectTest(sl4a_sl4a_base_test.Sl4aSl4aBaseTestClass):
    adv_instances = []
    bluetooth_gatt_list = []
    gatt_server_list = []
    default_timeout = 10
    default_discovery_timeout = 3

    ADDR_TYPE_PUBLIC = 0
    ADDR_TYPE_RPA = 1
    ADDR_TYPE_NRPA = 2

    def setup_class(self):
        super().setup_class()
        self.central = self.dut
        self.peripheral = self.cert

    def setup_test(self):
        super().setup_test()
        bluetooth_gatt_list = []
        self.gatt_server_list = []
        self.adv_instances = []
        # Ensure there is ample time for a physical disconnect in between
        # testcases.
        logging.info("Waiting for {} seconds for physical GATT disconnections".format(PHYSICAL_DISCONNECT_TIMEOUT))
        time.sleep(PHYSICAL_DISCONNECT_TIMEOUT)

    def teardown_test(self):
        for bluetooth_gatt in self.bluetooth_gatt_list:
            self.central.sl4a.gattClientClose(bluetooth_gatt)
        for gatt_server in self.gatt_server_list:
            self.peripheral.sl4a.gattServerClose(gatt_server)
        for adv in self.adv_instances:
            self.peripheral.sl4a.bleStopBleAdvertising(adv)
        super().teardown_test()
        return True

    def _orchestrate_gatt_disconnection(self, bluetooth_gatt, gatt_callback):
        logging.info("Disconnecting from peripheral device.")
        try:
            disconnect_gatt_connection(self.central, bluetooth_gatt, gatt_callback)
            logging.info("Disconnected GATT, closing GATT client.")
            close_gatt_client(self.central, bluetooth_gatt)
            logging.info("Closed GATT client, removing it from local tracker.")
            if bluetooth_gatt in self.bluetooth_gatt_list:
                self.bluetooth_gatt_list.remove(bluetooth_gatt)
        except GattTestUtilsError as err:
            logging.error(err)
            return False
        return True

    def _find_service_added_event(self, gatt_server_cb, uuid):
        expected_event = GattCallbackString.SERV_ADDED.format(gatt_server_cb)
        try:
            event = self.peripheral.ed.pop_event(expected_event, self.default_timeout)
        except Empty:
            logging.error(GattCallbackError.SERV_ADDED_ERR.format(expected_event))
            return False
        if event['data']['serviceUuid'].lower() != uuid.lower():
            logging.error("Uuid mismatch. Found: {}, Expected {}.".format(event['data']['serviceUuid'], uuid))
            return False
        return True

    def _verify_mtu_changed_on_client_and_server(self, expected_mtu, gatt_callback, gatt_server_callback):
        expected_event = GattCallbackString.MTU_CHANGED.format(gatt_callback)
        try:
            mtu_event = self.central.ed.pop_event(expected_event, self.default_timeout)
            mtu_size_found = mtu_event['data']['MTU']
            if mtu_size_found != expected_mtu:
                logging.error("MTU size found: {}, expected: {}".format(mtu_size_found, expected_mtu))
                return False
        except Empty:
            logging.error(GattCallbackError.MTU_CHANGED_ERR.format(expected_event))
            return False

        expected_event = GattCallbackString.MTU_SERV_CHANGED.format(gatt_server_callback)
        try:
            mtu_event = self.peripheral.ed.pop_event(expected_event, self.default_timeout)
            mtu_size_found = mtu_event['data']['MTU']
            if mtu_size_found != expected_mtu:
                logging.error("MTU size found: {}, expected: {}".format(mtu_size_found, expected_mtu))
                return False
        except Empty:
            logging.error(GattCallbackError.MTU_SERV_CHANGED_ERR.format(expected_event))
            return False
        return True

    @test_tracker_info(uuid='8a3530a3-c8bb-466b-9710-99e694c38618')
    def test_gatt_connect(self):
        """Test GATT connection over LE.

          Test establishing a gatt connection between a GATT server and GATT
          client.

          Steps:
            1. Start a generic advertisement.
            2. Start a generic scanner.
            3. Find the advertisement and extract the mac address.
            4. Stop the first scanner.
            5. Create a GATT connection between the scanner and advertiser.
            6. Disconnect the GATT connection.

          Expected Result:
            Verify that a connection was established and then disconnected
            successfully.

          Returns:
            Pass if True
            Fail if False

          TAGS: LE, Advertising, Filtering, Scanning, GATT
          Priority: 0
          """
        gatt_server_cb = self.peripheral.sl4a.gattServerCreateGattServerCallback()
        gatt_server = self.peripheral.sl4a.gattServerOpenGattServer(gatt_server_cb)
        self.gatt_server_list.append(gatt_server)
        try:
            bluetooth_gatt, gatt_callback, adv_callback = (orchestrate_gatt_connection(self.central, self.peripheral))
            self.bluetooth_gatt_list.append(bluetooth_gatt)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to connect to GATT, error: {}".format(err))
            return
        self.adv_instances.append(adv_callback)
        assertThat(self._orchestrate_gatt_disconnection(bluetooth_gatt, gatt_callback)).isTrue()

    @test_tracker_info(uuid='a839b505-03ac-4783-be7e-1d43129a1948')
    def test_gatt_connect_stop_advertising(self):
        """Test GATT connection over LE then stop advertising

          A test case that verifies the GATT connection doesn't
          disconnect when LE advertisement is stopped.

          Steps:
            1. Start a generic advertisement.
            2. Start a generic scanner.
            3. Find the advertisement and extract the mac address.
            4. Stop the first scanner.
            5. Create a GATT connection between the scanner and advertiser.
            6. Stop the advertiser.
            7. Verify no connection state changed happened.
            8. Disconnect the GATT connection.

          Expected Result:
            Verify that a connection was established and not disconnected
            when advertisement stops.

          Returns:
            Pass if True
            Fail if False

          TAGS: LE, Advertising, Filtering, Scanning, GATT
          Priority: 0
          """
        gatt_server_cb = self.peripheral.sl4a.gattServerCreateGattServerCallback()
        gatt_server = self.peripheral.sl4a.gattServerOpenGattServer(gatt_server_cb)
        self.gatt_server_list.append(gatt_server)
        try:
            bluetooth_gatt, gatt_callback, adv_callback = (orchestrate_gatt_connection(self.central, self.peripheral))
            self.bluetooth_gatt_list.append(bluetooth_gatt)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to connect to GATT, error: {}".format(err))
            return
        self.peripheral.sl4a.bleStopBleAdvertising(adv_callback)
        try:
            event = self.central.ed.pop_event(
                GattCallbackString.GATT_CONN_CHANGE.format(gatt_callback, self.default_timeout))
            logging.error("Connection event found when not expected: {}".format(event))
            asserts.fail("Connection event found when not expected: {}".format(event))
            return
        except Empty:
            logging.info("No connection state change as expected")
        try:
            self._orchestrate_gatt_disconnection(bluetooth_gatt, gatt_callback)
        except Exception as err:
            logging.info("Failed to orchestrate disconnect: {}".format(err))
            asserts.fail("Failed to orchestrate disconnect: {}".format(err))
            return

    @test_tracker_info(uuid='b82f91a8-54bb-4779-a117-73dc7fdb28cc')
    def test_gatt_connect_autoconnect(self):
        """Test GATT connection over LE.

          Test re-establishing a gatt connection using autoconnect
          set to True in order to test connection allowlist.

          Steps:
            1. Start a generic advertisement.
            2. Start a generic scanner.
            3. Find the advertisement and extract the mac address.
            4. Stop the first scanner.
            5. Create a GATT connection between the scanner and advertiser.
            6. Disconnect the GATT connection.
            7. Create a GATT connection with autoconnect set to True
            8. Disconnect the GATT connection.

          Expected Result:
            Verify that a connection was re-established and then disconnected
            successfully.

          Returns:
            Pass if True
            Fail if False

          TAGS: LE, Advertising, Filtering, Scanning, GATT
          Priority: 0
          """
        gatt_server_cb = self.peripheral.sl4a.gattServerCreateGattServerCallback()
        gatt_server = self.peripheral.sl4a.gattServerOpenGattServer(gatt_server_cb)
        self.peripheral.log.info("Opened GATT server on CERT, scanning it from DUT")
        self.gatt_server_list.append(gatt_server)
        autoconnect = False
        mac_address, adv_callback, scan_callback = (get_mac_address_of_generic_advertisement(
            self.central, self.peripheral, self.ADDR_TYPE_PUBLIC))
        self.adv_instances.append(adv_callback)
        self.central.log.info("Discovered BLE advertisement, connecting GATT with autoConnect={}".format(autoconnect))
        try:
            bluetooth_gatt, gatt_callback = setup_gatt_connection(self.central, mac_address, autoconnect)
            self.central.log.info("GATT connected, stopping BLE scanning")
            self.central.sl4a.bleStopBleScan(scan_callback)
            self.central.log.info("Stopped BLE scanning")
            self.bluetooth_gatt_list.append(bluetooth_gatt)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to connect to GATT, error: {}".format(err))
            return
        self.central.log.info("Disconnecting GATT")
        try:
            disconnect_gatt_connection(self.central, bluetooth_gatt, gatt_callback)
            self.central.log.info("GATT disconnected, closing GATT client")
            close_gatt_client(self.central, bluetooth_gatt)
            self.central.log.info("GATT client closed, removing it from in-memory tracker")
            if bluetooth_gatt in self.bluetooth_gatt_list:
                self.bluetooth_gatt_list.remove(bluetooth_gatt)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to disconnect GATT, error: {}".format(err))
            return
        autoconnect = True
        self.central.log.info("Connecting GATT with autoConnect={}".format(autoconnect))
        bluetooth_gatt = self.central.sl4a.gattClientConnectGatt(gatt_callback, mac_address, autoconnect,
                                                                 GattTransport.TRANSPORT_LE, False,
                                                                 GattPhyMask.PHY_LE_1M_MASK)
        self.central.log.info("Waiting for GATt to become connected")
        self.bluetooth_gatt_list.append(bluetooth_gatt)
        expected_event = GattCallbackString.GATT_CONN_CHANGE.format(gatt_callback)
        try:
            event = self.central.ed.pop_event(expected_event, self.default_timeout)
            self.central.log.info("Received event={}".format(event))
        except Empty:
            logging.error(GattCallbackError.GATT_CONN_CHANGE_ERR.format(expected_event))
            asserts.fail(GattCallbackError.GATT_CONN_CHANGE_ERR.format(expected_event))
            return
        found_state = event['data']['State']
        expected_state = GattConnectionState.STATE_CONNECTED
        assertThat(found_state).isEqualTo(expected_state)
        assertThat(self._orchestrate_gatt_disconnection(bluetooth_gatt, gatt_callback)).isTrue()

    @test_tracker_info(uuid='e506fa50-7cd9-4bd8-938a-6b85dcfe6bc6')
    def test_gatt_connect_opportunistic(self):
        """Test opportunistic GATT connection over LE.

          Test establishing a gatt connection between a GATT server and GATT
          client in opportunistic mode.

          Steps:
            1. Start a generic advertisement.
            2. Start a generic scanner.
            3. Find the advertisement and extract the mac address.
            4. Stop the first scanner.
            5. Create GATT connection 1 between the scanner and advertiser normally
            6. Create GATT connection 2 between the scanner and advertiser using
               opportunistic mode
            7. Disconnect GATT connection 1

          Expected Result:
            Verify GATT connection 2 automatically disconnects when GATT connection
            1 disconnect

          Returns:
            Pass if True
            Fail if False

          TAGS: LE, Advertising, Filtering, Scanning, GATT
          Priority: 0
          """
        gatt_server_cb = self.peripheral.sl4a.gattServerCreateGattServerCallback()
        gatt_server = self.peripheral.sl4a.gattServerOpenGattServer(gatt_server_cb)
        self.gatt_server_list.append(gatt_server)
        mac_address, adv_callback, scan_callback = (get_mac_address_of_generic_advertisement(
            self.central, self.peripheral))
        # Make GATT connection 1
        try:
            bluetooth_gatt_1, gatt_callback_1 = setup_gatt_connection(self.central,
                                                                      mac_address,
                                                                      False,
                                                                      transport=GattTransport.TRANSPORT_AUTO,
                                                                      opportunistic=False)
            self.central.sl4a.bleStopBleScan(scan_callback)
            self.adv_instances.append(adv_callback)
            self.bluetooth_gatt_list.append(bluetooth_gatt_1)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to connect to GATT 1, error: {}".format(err))
            return
        # Make GATT connection 2
        try:
            bluetooth_gatt_2, gatt_callback_2 = setup_gatt_connection(self.central,
                                                                      mac_address,
                                                                      False,
                                                                      transport=GattTransport.TRANSPORT_AUTO,
                                                                      opportunistic=True)
            self.bluetooth_gatt_list.append(bluetooth_gatt_2)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to connect to GATT 2, error: {}".format(err))
            return
        # Disconnect GATT connection 1
        try:
            disconnect_gatt_connection(self.central, bluetooth_gatt_1, gatt_callback_1)
            close_gatt_client(self.central, bluetooth_gatt_1)
            if bluetooth_gatt_1 in self.bluetooth_gatt_list:
                self.bluetooth_gatt_list.remove(bluetooth_gatt_1)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to disconnect GATT 1, error: {}".format(err))
            return
        # Confirm that GATT connection 2 also disconnects
        wait_for_gatt_disconnect_event(self.central, gatt_callback_2)
        close_gatt_client(self.central, bluetooth_gatt_2)
        if bluetooth_gatt_2 in self.bluetooth_gatt_list:
            self.bluetooth_gatt_list.remove(bluetooth_gatt_2)

    @test_tracker_info(uuid='4416d483-dec3-46cb-8038-4d82620f873a')
    def test_gatt_request_out_of_bounds_mtu(self):
        """Test GATT connection over LE and exercise an out of bound MTU size.

          Test establishing a gatt connection between a GATT server and GATT
          client. Request an MTU size that is the MIN value minus 1.

          Steps:
            1. Start a generic advertisement.
            2. Start a generic scanner.
            3. Find the advertisement and extract the mac address.
            4. Stop the first scanner.
            5. Create a GATT connection between the scanner and advertiser.
            6. From the scanner (client) request MTU size change to the
            minimum value minus one.
            7. Find the MTU changed event on the client.
            8. Disconnect the GATT connection.

          Expected Result:
            Verify that an MTU changed event was not discovered and that
            it didn't cause an exception when requesting an out of bounds
            MTU.

          Returns:
            Pass if True
            Fail if False

          TAGS: LE, Advertising, Filtering, Scanning, GATT, MTU
          Priority: 0
          """
        gatt_server_cb = self.peripheral.sl4a.gattServerCreateGattServerCallback()
        gatt_server = self.peripheral.sl4a.gattServerOpenGattServer(gatt_server_cb)
        self.gatt_server_list.append(gatt_server)
        try:
            bluetooth_gatt, gatt_callback, adv_callback = (orchestrate_gatt_connection(self.central, self.peripheral))
            self.bluetooth_gatt_list.append(bluetooth_gatt)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to connect to GATT, error: {}".format(err))
            return
        self.adv_instances.append(adv_callback)
        unexpected_mtu = GattMtuSize.MIN - 1
        self.central.sl4a.gattClientRequestMtu(bluetooth_gatt, unexpected_mtu)
        assertThat(self._verify_mtu_changed_on_client_and_server(unexpected_mtu, gatt_callback,
                                                                 gatt_server_cb)).isFalse()
        assertThat(self._orchestrate_gatt_disconnection(bluetooth_gatt, gatt_callback)).isTrue()

    @test_tracker_info(uuid='31ffb9ca-cc75-43fb-9802-c19f1c5856b6')
    def test_gatt_connect_trigger_on_read_rssi(self):
        """Test GATT connection over LE read RSSI.

        Test establishing a gatt connection between a GATT server and GATT
        client then read the RSSI.

        Steps:
          1. Start a generic advertisement.
          2. Start a generic scanner.
          3. Find the advertisement and extract the mac address.
          4. Stop the first scanner.
          5. Create a GATT connection between the scanner and advertiser.
          6. From the scanner, request to read the RSSI of the advertiser.
          7. Disconnect the GATT connection.

        Expected Result:
          Verify that a connection was established and then disconnected
          successfully. Verify that the RSSI was ready correctly.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, Advertising, Filtering, Scanning, GATT, RSSI
        Priority: 1
        """
        gatt_server_cb = self.peripheral.sl4a.gattServerCreateGattServerCallback()
        gatt_server = self.peripheral.sl4a.gattServerOpenGattServer(gatt_server_cb)
        self.gatt_server_list.append(gatt_server)
        try:
            bluetooth_gatt, gatt_callback, adv_callback = (orchestrate_gatt_connection(self.central, self.peripheral))
            self.bluetooth_gatt_list.append(bluetooth_gatt)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to connect to GATT, error: {}".format(err))
            return
        self.adv_instances.append(adv_callback)
        expected_event = GattCallbackString.RD_REMOTE_RSSI.format(gatt_callback)
        if self.central.sl4a.gattClientReadRSSI(bluetooth_gatt):
            try:
                self.central.ed.pop_event(expected_event, self.default_timeout)
            except Empty:
                logging.error(GattCallbackError.RD_REMOTE_RSSI_ERR.format(expected_event))
        assertThat(self._orchestrate_gatt_disconnection(bluetooth_gatt, gatt_callback)).isTrue()

    @test_tracker_info(uuid='dee9ef28-b872-428a-821b-cc62f27ba936')
    def test_gatt_connect_trigger_on_services_discovered(self):
        """Test GATT connection and discover services of peripheral.

          Test establishing a gatt connection between a GATT server and GATT
          client the discover all services from the connected device.

          Steps:
            1. Start a generic advertisement.
            2. Start a generic scanner.
            3. Find the advertisement and extract the mac address.
            4. Stop the first scanner.
            5. Create a GATT connection between the scanner and advertiser.
            6. From the scanner (central device), discover services.
            7. Disconnect the GATT connection.

          Expected Result:
            Verify that a connection was established and then disconnected
            successfully. Verify that the service were discovered.

          Returns:
            Pass if True
            Fail if False

          TAGS: LE, Advertising, Filtering, Scanning, GATT, Services
          Priority: 1
          """
        gatt_server_cb = self.peripheral.sl4a.gattServerCreateGattServerCallback()
        gatt_server = self.peripheral.sl4a.gattServerOpenGattServer(gatt_server_cb)
        self.gatt_server_list.append(gatt_server)
        try:
            bluetooth_gatt, gatt_callback, adv_callback = (orchestrate_gatt_connection(self.central, self.peripheral))
            self.bluetooth_gatt_list.append(bluetooth_gatt)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to connect to GATT, error: {}".format(err))
            return
        self.adv_instances.append(adv_callback)
        if self.central.sl4a.gattClientDiscoverServices(bluetooth_gatt):
            expected_event = GattCallbackString.GATT_SERV_DISC.format(gatt_callback)
            try:
                event = self.central.ed.pop_event(expected_event, self.default_timeout)
            except Empty:
                logging.error(GattCallbackError.GATT_SERV_DISC_ERR.format(expected_event))
                asserts.fail(GattCallbackError.GATT_SERV_DISC_ERR.format(expected_event))
                return
        assertThat(self._orchestrate_gatt_disconnection(bluetooth_gatt, gatt_callback)).isTrue()

    @test_tracker_info(uuid='01883bdd-0cf8-48fb-bf15-467bbd4f065b')
    def test_gatt_connect_trigger_on_services_discovered_iterate_attributes(self):
        """Test GATT connection and iterate peripherals attributes.

        Test establishing a gatt connection between a GATT server and GATT
        client and iterate over all the characteristics and descriptors of the
        discovered services.

        Steps:
          1. Start a generic advertisement.
          2. Start a generic scanner.
          3. Find the advertisement and extract the mac address.
          4. Stop the first scanner.
          5. Create a GATT connection between the scanner and advertiser.
          6. From the scanner (central device), discover services.
          7. Iterate over all the characteristics and descriptors of the
          discovered features.
          8. Disconnect the GATT connection.

        Expected Result:
          Verify that a connection was established and then disconnected
          successfully. Verify that the services, characteristics, and descriptors
          were discovered.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, Advertising, Filtering, Scanning, GATT, Services
        Characteristics, Descriptors
        Priority: 1
        """
        gatt_server_cb = self.peripheral.sl4a.gattServerCreateGattServerCallback()
        gatt_server = self.peripheral.sl4a.gattServerOpenGattServer(gatt_server_cb)
        self.gatt_server_list.append(gatt_server)
        try:
            bluetooth_gatt, gatt_callback, adv_callback = (orchestrate_gatt_connection(self.central, self.peripheral))
            self.bluetooth_gatt_list.append(bluetooth_gatt)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to connect to GATT, error: {}".format(err))
            return
        self.adv_instances.append(adv_callback)
        if self.central.sl4a.gattClientDiscoverServices(bluetooth_gatt):
            expected_event = GattCallbackString.GATT_SERV_DISC.format(gatt_callback)
            try:
                event = self.central.ed.pop_event(expected_event, self.default_timeout)
                discovered_services_index = event['data']['ServicesIndex']
            except Empty:
                logging.error(GattCallbackError.GATT_SERV_DISC_ERR.format(expected_event))
                asserts.fail(GattCallbackError.GATT_SERV_DISC_ERR.format(expected_event))
                return
            log_gatt_server_uuids(self.central, discovered_services_index)
        assertThat(self._orchestrate_gatt_disconnection(bluetooth_gatt, gatt_callback)).isTrue()

    @test_tracker_info(uuid='d4277bee-da99-4f48-8a4d-f81b5389da18')
    def test_gatt_connect_with_service_uuid_variations(self):
        """Test GATT connection with multiple service uuids.

        Test establishing a gatt connection between a GATT server and GATT
        client with multiple service uuid variations.

        Steps:
          1. Start a generic advertisement.
          2. Start a generic scanner.
          3. Find the advertisement and extract the mac address.
          4. Stop the first scanner.
          5. Create a GATT connection between the scanner and advertiser.
          6. From the scanner (central device), discover services.
          7. Verify that all the service uuid variations are found.
          8. Disconnect the GATT connection.

        Expected Result:
          Verify that a connection was established and then disconnected
          successfully. Verify that the service uuid variations are found.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, Advertising, Filtering, Scanning, GATT, Services
        Priority: 2
        """
        try:
            gatt_server_cb, gatt_server = setup_multiple_services(self.peripheral)
            self.gatt_server_list.append(gatt_server)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to setup GATT service, error: {}".format(err))
            return
        try:
            bluetooth_gatt, gatt_callback, adv_callback = (orchestrate_gatt_connection(self.central, self.peripheral))
            self.bluetooth_gatt_list.append(bluetooth_gatt)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to connect to GATT, error: {}".format(err))
            return
        self.adv_instances.append(adv_callback)
        if self.central.sl4a.gattClientDiscoverServices(bluetooth_gatt):
            expected_event = GattCallbackString.GATT_SERV_DISC.format(gatt_callback)
            try:
                event = self.central.ed.pop_event(expected_event, self.default_timeout)
            except Empty:
                logging.error(GattCallbackError.GATT_SERV_DISC_ERR.format(expected_event))
                asserts.fail(GattCallbackError.GATT_SERV_DISC_ERR.format(expected_event))
                return
            discovered_services_index = event['data']['ServicesIndex']
            log_gatt_server_uuids(self.central, discovered_services_index)

        assertThat(self._orchestrate_gatt_disconnection(bluetooth_gatt, gatt_callback)).isTrue()

    @test_tracker_info(uuid='7d3442c5-f71f-44ae-bd35-f2569f01b3b8')
    def test_gatt_connect_in_quick_succession(self):
        """Test GATT connections multiple times.

        Test establishing a gatt connection between a GATT server and GATT
        client with multiple iterations.

        Steps:
          1. Start a generic advertisement.
          2. Start a generic scanner.
          3. Find the advertisement and extract the mac address.
          4. Stop the first scanner.
          5. Create a GATT connection between the scanner and advertiser.
          6. Disconnect the GATT connection.
          7. Repeat steps 5 and 6 twenty times.

        Expected Result:
          Verify that a connection was established and then disconnected
          successfully twenty times.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, Advertising, Filtering, Scanning, GATT, Stress
        Priority: 1
        """
        gatt_server_cb = self.peripheral.sl4a.gattServerCreateGattServerCallback()
        gatt_server = self.peripheral.sl4a.gattServerOpenGattServer(gatt_server_cb)
        self.gatt_server_list.append(gatt_server)
        mac_address, adv_callback, scan_callback = get_mac_address_of_generic_advertisement(
            self.central, self.peripheral)
        autoconnect = False
        for i in range(100):
            logging.info("Starting connection iteration {}".format(i + 1))
            try:
                bluetooth_gatt, gatt_callback = setup_gatt_connection(self.central, mac_address, autoconnect)
                self.central.sl4a.bleStopBleScan(scan_callback)
            except GattTestUtilsError as err:
                logging.error(err)
                asserts.fail("Failed to connect to GATT at iteration {}, error: {}".format(i + 1, err))
                return
            test_result = self._orchestrate_gatt_disconnection(bluetooth_gatt, gatt_callback)
            if not test_result:
                logging.info("Failed to disconnect from peripheral device.")
                asserts.fail("Failed to disconnect from peripheral device.")
                return
        self.adv_instances.append(adv_callback)

    @test_tracker_info(uuid='148469d9-7ab0-4c08-b2e9-7e49e88da1fc')
    def test_gatt_connect_on_path_attack(self):
        """Test GATT connection with permission write encrypted with on-path attacker prevention

        Test establishing a gatt connection between a GATT server and GATT
        client while the GATT server's characteristic includes the property
        write value and the permission write encrypted on-path attacker prevention
        value. This will prompt LE pairing and then the devices will create a bond.

        Steps:
          1. Create a GATT server and server callback on the peripheral device.
          2. Create a unique service and characteristic uuid on the peripheral.
          3. Create a characteristic on the peripheral with these properties:
              GattCharacteristic.PROPERTY_WRITE,
              GattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM
          4. Create a GATT service on the peripheral.
          5. Add the characteristic to the GATT service.
          6. Create a GATT connection between your central and peripheral device.
          7. From the central device, discover the peripheral's services.
          8. Iterate the services found until you find the unique characteristic
              created in step 3.
          9. Once found, write a random but valid value to the characteristic.
          10. Start pairing helpers on both devices immediately after attempting
              to write to the characteristic.
          11. Within 10 seconds of writing the characteristic, there should be
              a prompt to bond the device from the peripheral. The helpers will
              handle the UI interaction automatically. (see
              BluetoothConnectionFacade.java bluetoothStartPairingHelper).
          12. Verify that the two devices are bonded.

        Expected Result:
          Verify that a connection was established and the devices are bonded.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, Advertising, Filtering, Scanning, GATT, Characteristic, OnPathAttacker
        Priority: 1
        """
        gatt_server_cb = self.peripheral.sl4a.gattServerCreateGattServerCallback()
        gatt_server = self.peripheral.sl4a.gattServerOpenGattServer(gatt_server_cb)
        self.gatt_server_list.append(gatt_server)
        service_uuid = "3846D7A0-69C8-11E4-BA00-0002A5D5C51B"
        test_uuid = "aa7edd5a-4d1d-4f0e-883a-d145616a1630"
        bonded = False
        characteristic = self.peripheral.sl4a.gattServerCreateBluetoothGattCharacteristic(
            test_uuid, GattCharacteristic.PROPERTY_WRITE, GattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM)
        gatt_service = self.peripheral.sl4a.gattServerCreateService(service_uuid, GattServiceType.SERVICE_TYPE_PRIMARY)
        self.peripheral.sl4a.gattServerAddCharacteristicToService(gatt_service, characteristic)
        self.peripheral.sl4a.gattServerAddService(gatt_server, gatt_service)
        assertThat(self._find_service_added_event(gatt_server_cb, service_uuid)).isTrue()
        bluetooth_gatt, gatt_callback, adv_callback = (orchestrate_gatt_connection(self.central, self.peripheral))
        self.bluetooth_gatt_list.append(bluetooth_gatt)
        self.adv_instances.append(adv_callback)
        if self.central.sl4a.gattClientDiscoverServices(bluetooth_gatt):
            expected_event = GattCallbackString.GATT_SERV_DISC.format(gatt_callback)
            try:
                event = self.central.ed.pop_event(expected_event, self.default_timeout)
            except Empty:
                logging.error(GattCallbackError.GATT_SERV_DISC_ERR.format(expected_event))
                asserts.fail(GattCallbackError.GATT_SERV_DISC_ERR.format(expected_event))
                return
            discovered_services_index = event['data']['ServicesIndex']
        else:
            logging.info("Failed to discover services.")
            asserts.fail("Failed to discover services.")
            return
        test_value = [1, 2, 3, 4, 5, 6, 7]
        services_count = self.central.sl4a.gattClientGetDiscoveredServicesCount(discovered_services_index)
        for i in range(services_count):
            characteristic_uuids = (self.central.sl4a.gattClientGetDiscoveredCharacteristicUuids(
                discovered_services_index, i))
            for characteristic_uuid in characteristic_uuids:
                if characteristic_uuid == test_uuid:
                    self.central.sl4a.bluetoothStartPairingHelper()
                    self.peripheral.sl4a.bluetoothStartPairingHelper()
                    self.central.sl4a.gattClientCharacteristicSetValue(bluetooth_gatt, discovered_services_index, i,
                                                                       characteristic_uuid, test_value)
                    self.central.sl4a.gattClientWriteCharacteristic(bluetooth_gatt, discovered_services_index, i,
                                                                    characteristic_uuid)
                    start_time = time.time() + self.default_timeout
                    target_name = self.peripheral.sl4a.bluetoothGetLocalName()
                    while time.time() < start_time and bonded == False:
                        bonded_devices = \
                            self.central.sl4a.bluetoothGetBondedDevices()
                        for device in bonded_devices:
                            if ('name' in device.keys() and device['name'] == target_name):
                                bonded = True
                                break
                    bonded = False
                    target_name = self.central.sl4a.bluetoothGetLocalName()
                    while time.time() < start_time and bonded == False:
                        bonded_devices = \
                            self.peripheral.sl4a.bluetoothGetBondedDevices()
                        for device in bonded_devices:
                            if ('name' in device.keys() and device['name'] == target_name):
                                bonded = True
                                break

        # Dual mode devices will establish connection over the classic transport,
        # in order to establish bond over both transports, and do SDP. Starting
        # disconnection before all this is finished is not safe, might lead to
        # race conditions, i.e. bond over classic tranport shows up after LE
        # bond is already removed.
        time.sleep(4)

        for ad in [self.central, self.peripheral]:
            assertThat(clear_bonded_devices(ad)).isTrue()

        # Necessary sleep time for entries to update unbonded state
        time.sleep(2)

        for ad in [self.central, self.peripheral]:
            bonded_devices = ad.sl4a.bluetoothGetBondedDevices()
            if len(bonded_devices) > 0:
                logging.error("Failed to unbond devices: {}".format(bonded_devices))
                asserts.fail("Failed to unbond devices: {}".format(bonded_devices))
                return
        assertThat(self._orchestrate_gatt_disconnection(bluetooth_gatt, gatt_callback)).isTrue()

    @test_tracker_info(uuid='cc3fc361-7bf1-4ee2-9e46-4a27c88ce6a8')
    def test_gatt_connect_get_connected_devices(self):
        """Test GATT connections show up in getConnectedDevices

        Test establishing a gatt connection between a GATT server and GATT
        client. Verify that active connections show up using
        BluetoothManager.getConnectedDevices API.

        Steps:
          1. Start a generic advertisement.
          2. Start a generic scanner.
          3. Find the advertisement and extract the mac address.
          4. Stop the first scanner.
          5. Create a GATT connection between the scanner and advertiser.
          7. Verify the GATT Client has an open connection to the GATT Server.
          8. Verify the GATT Server has an open connection to the GATT Client.
          9. Disconnect the GATT connection.

        Expected Result:
          Verify that a connection was established, connected devices are found
          on both the central and peripheral devices, and then disconnected
          successfully.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, Advertising, Scanning, GATT
        Priority: 2
        """
        gatt_server_cb = self.peripheral.sl4a.gattServerCreateGattServerCallback()
        gatt_server = self.peripheral.sl4a.gattServerOpenGattServer(gatt_server_cb)
        self.gatt_server_list.append(gatt_server)
        try:
            bluetooth_gatt, gatt_callback, adv_callback = (orchestrate_gatt_connection(self.central, self.peripheral))
            self.bluetooth_gatt_list.append(bluetooth_gatt)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Failed to connect to GATT, error: {}".format(err))
            return
        conn_cen_devices = self.central.sl4a.bluetoothGetConnectedLeDevices(BluetoothProfile.GATT)
        conn_per_devices = self.peripheral.sl4a.bluetoothGetConnectedLeDevices(BluetoothProfile.GATT_SERVER)
        target_name = self.peripheral.sl4a.bluetoothGetLocalName()
        error_message = ("Connected device {} not found in list of connected "
                         "devices {}")
        if not any(d['name'] == target_name for d in conn_cen_devices):
            logging.error(error_message.format(target_name, conn_cen_devices))
            asserts.fail(error_message.format(target_name, conn_cen_devices))
            return
        # For the GATT server only check the size of the list since
        # it may or may not include the device name.
        target_name = self.central.sl4a.bluetoothGetLocalName()
        if not conn_per_devices:
            logging.error(error_message.format(target_name, conn_per_devices))
            asserts.fail(error_message.format(target_name, conn_per_devices))
            return
        self.adv_instances.append(adv_callback)
        assertThat(self._orchestrate_gatt_disconnection(bluetooth_gatt, gatt_callback)).isTrue()

    @test_tracker_info(uuid='a0a37ca6-9fa8-4d35-9fdb-0e25b4b8a363')
    def test_gatt_connect_second_adv_after_canceling_first_adv(self):
        """Test GATT connection to peripherals second advertising address.

        Test the ability of cancelling GATT connections and trying to reconnect
        to the same device via a different address.

        Steps:
          1. A starts advertising
          2. B starts scanning and finds A's mac address
          3. Stop advertisement from step 1. Start a new advertisement on A and
            find the new new mac address, B knows of both old and new address.
          4. B1 sends connect request to old address of A
          5. B1 cancel connect attempt after 10 seconds
          6. B1 sends connect request to new address of A
          7. Verify B1 establish connection to A in less than 10 seconds

        Expected Result:
          Verify that a connection was established only on the second
          advertisement's mac address.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, Advertising, Scanning, GATT
        Priority: 3
        """
        autoconnect = False
        transport = GattTransport.TRANSPORT_AUTO
        opportunistic = False
        # Setup a basic Gatt server on the peripheral
        gatt_server_cb = self.peripheral.sl4a.gattServerCreateGattServerCallback()
        gatt_server = self.peripheral.sl4a.gattServerOpenGattServer(gatt_server_cb)

        # Set advertisement settings to include local name in advertisement
        # and set the advertising mode to low_latency.
        self.peripheral.sl4a.bleSetAdvertiseSettingsIsConnectable(True)
        self.peripheral.sl4a.bleSetAdvertiseDataIncludeDeviceName(True)
        self.peripheral.sl4a.bleSetAdvertiseSettingsAdvertiseMode(BleAdvertiseSettingsMode.LOW_LATENCY)

        # Setup necessary advertisement objects.
        advertise_data = self.peripheral.sl4a.bleBuildAdvertiseData()
        advertise_settings = self.peripheral.sl4a.bleBuildAdvertiseSettings()
        advertise_callback = self.peripheral.sl4a.bleGenBleAdvertiseCallback()

        # Step 1: Start advertisement
        self.peripheral.sl4a.bleStartBleAdvertising(advertise_callback, advertise_data, advertise_settings)

        # Setup scan settings for low_latency scanning and to include the local name
        # of the advertisement started in step 1.
        filter_list = self.central.sl4a.bleGenFilterList()
        self.central.sl4a.bleSetScanSettingsNumOfMatches(BleScanSettingsMatchNums.ONE)
        self.central.sl4a.bleSetScanFilterDeviceName(self.peripheral.sl4a.bluetoothGetLocalName())
        self.central.sl4a.bleBuildScanFilter(filter_list)
        self.central.sl4a.bleSetScanSettingsScanMode(BleScanSettingsModes.LOW_LATENCY)

        # Setup necessary scan objects.
        scan_settings = self.central.sl4a.bleBuildScanSetting()
        scan_callback = self.central.sl4a.bleGenScanCallback()

        # Step 2: Start scanning on central Android device and find peripheral
        # address.
        self.central.sl4a.bleStartBleScan(filter_list, scan_settings, scan_callback)
        expected_event_name = scan_result.format(scan_callback)
        try:
            mac_address_pre_restart = self.central.ed.pop_event(
                expected_event_name, self.default_timeout)['data']['Result']['deviceInfo']['address']
            logging.info("Peripheral advertisement found with mac address: {}".format(mac_address_pre_restart))
        except Empty:
            logging.info("Peripheral advertisement not found")
            asserts.fail("Peripheral advertisement not found")
            return
        finally:
            self.peripheral.sl4a.bleStopBleAdvertising(advertise_callback)

        # Step 3: Restart peripheral advertising such that a new mac address is
        # created.
        self.peripheral.sl4a.bleStartBleAdvertising(advertise_callback, advertise_data, advertise_settings)

        mac_address_post_restart = mac_address_pre_restart

        while True:
            try:
                mac_address_post_restart = self.central.ed.pop_event(
                    expected_event_name, self.default_timeout)['data']['Result']['deviceInfo']['address']
                logging.info("Peripheral advertisement found with mac address: {}".format(mac_address_post_restart))
            except Empty:
                logging.info("Peripheral advertisement not found")
                asserts.fail("Peripheral advertisement not found")
                return

            if mac_address_pre_restart != mac_address_post_restart:
                break


if __name__ == '__main__':
    test_runner.main()
