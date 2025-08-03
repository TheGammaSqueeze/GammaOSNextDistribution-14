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

import queue
import logging
import time
from datetime import timedelta
from grpc import RpcError

import hci_packets as hci
from blueberry.facade.hci import le_advertising_manager_facade_pb2 as le_advertising_facade
from blueberry.facade.hci import le_initiator_address_facade_pb2 as le_initiator_address_facade
from blueberry.facade import common_pb2 as common
from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert.py_le_acl_manager import PyLeAclManager
from blueberry.tests.gd_sl4a.lib import gd_sl4a_base_test
from blueberry.tests.gd_sl4a.lib.bt_constants import ble_scan_settings_modes, ble_address_types, scan_result, ble_scan_settings_phys, ble_scan_settings_callback_types
from blueberry.tests.gd_sl4a.lib.ble_lib import generate_ble_scan_objects
from blueberry.utils.bt_gatt_utils import setup_gatt_connection
from blueberry.utils.bt_gatt_utils import GattTestUtilsError
from blueberry.utils.bt_gatt_utils import disconnect_gatt_connection
from blueberry.utils.bt_gatt_utils import wait_for_gatt_disconnect_event
from blueberry.utils.bt_gatt_utils import wait_for_gatt_connection
from blueberry.utils.bt_gatt_utils import close_gatt_client
from mobly.controllers.android_device import AndroidDevice
from mobly import asserts
from mobly import test_runner
from mobly.signals import TestFailure


class GattConnectLowLayerTest(gd_sl4a_base_test.GdSl4aBaseTestClass):

    def setup_class(self):
        super().setup_class(cert_module='HCI_INTERFACES')
        self.bluetooth_gatt_list = []
        self.default_timeout = 30  # seconds

    def setup_test(self):
        super().setup_test()
        self.cert_le_acl_manager = PyLeAclManager(self.cert)

    def teardown_test(self):
        try:
            for bluetooth_gatt in self.bluetooth_gatt_list:
                self.dut.sl4a.gattClientClose(bluetooth_gatt)
        except Exception as err:
            logging.error("Failed to close GATT client, error: {}".format(err))
        try:
            safeClose(self.cert_le_acl_manager)
        except RpcError as err:
            logging.error("Failed to close CERT acl manager, error: {}".format(err))
        self.cert_le_acl_manager = None
        super().teardown_test()

    def _set_cert_privacy_policy_with_random_address(self, random_address):
        private_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_STATIC_ADDRESS,
            address_with_type=common.BluetoothAddressWithType(
                address=common.BluetoothAddress(address=bytes(random_address, encoding='utf8')),
                type=common.RANDOM_DEVICE_ADDRESS))
        self.cert.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(private_policy)

    def _start_cert_advertising_with_random_address(self, device_name, random_address):
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME,
                               data=list(bytes(device_name, encoding='utf8')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
        config = le_advertising_facade.AdvertisingConfig(
            advertisement=[gap_data],
            interval_min=512,
            interval_max=768,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_RANDOM_DEVICE_ADDRESS,
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)
        request = le_advertising_facade.CreateAdvertiserRequest(config=config)
        logging.info("Creating advertiser")
        create_response = self.cert.hci_le_advertising_manager.CreateAdvertiser(request)
        logging.info("Created advertiser")
        return create_response

    def _start_dut_scanning_for_address(self, address_type, address):
        logging.info("Start scanning for address {} with address type {}".format(address, address_type))
        self.dut.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(self.dut.sl4a)
        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleSetScanFilterDeviceAddressAndType(address, int(address_type))
        self.dut.sl4a.bleBuildScanFilter(filter_list)
        self.dut.sl4a.bleStartBleScan(filter_list, scan_settings, scan_callback)
        return scan_callback

    def _wait_for_scan_result_event(self, expected_event_name):
        try:
            # Verify if there is scan result
            event_info = self.dut.ed.pop_event(expected_event_name, self.default_timeout)
            # Print out scan result
            mac_address = event_info['data']['Result']['deviceInfo']['address']
            logging.info("Filter advertisement with address {}".format(mac_address))
            return mac_address, event_info
        except queue.Empty as error:
            logging.error("Could not find initial advertisement.")
            return None, None

    def _stop_advertising(self, advertiser_id):
        logging.info("Stop advertising")
        remove_request = le_advertising_facade.RemoveAdvertiserRequest(advertiser_id=advertiser_id)
        self.cert.hci_le_advertising_manager.RemoveAdvertiser(remove_request)
        logging.info("Stopped advertising")

    def _stop_scanning(self, scan_callback):
        logging.info("Stop scanning")
        self.dut.sl4a.bleStopBleScan(scan_callback)
        logging.info("Stopped scanning")

    def _disconnect_gatt(self, device: AndroidDevice, bluetooth_gatt, gatt_callback):
        try:
            disconnect_gatt_connection(device, bluetooth_gatt, gatt_callback)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Cannot disconnect GATT , error={}".format(err))
        finally:
            close_gatt_client(device, bluetooth_gatt)
            if bluetooth_gatt in self.bluetooth_gatt_list:
                self.bluetooth_gatt_list.remove(bluetooth_gatt)

    def _wait_for_gatt_connection(self, device: AndroidDevice, gatt_callback, bluetooth_gatt):
        try:
            wait_for_gatt_connection(device, gatt_callback, bluetooth_gatt, timeout=self.default_timeout)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Cannot observe GATT connection , error={}".format(err))

    def _wait_for_gatt_disconnection(self, device: AndroidDevice, gatt_callback):
        try:
            wait_for_gatt_disconnect_event(device, gatt_callback)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Cannot observe GATT disconnection, error={}".format(err))

    def test_autoconnect_gatt_without_pairing_and_disconnect_quickly(self):
        """
        Steps:
        1. CERT: advertises with Random Static address
        2. DUT: connect without pairing within 30 seconds
        3. CERT: verify GATT connection
        4. Wait 5 seconds
        5. DUT: Disconnect GATT
        6. CERT: Verify that GATT is disconnected within 5 seconds
        """
        # Use random address on cert side
        logging.info("Setting random address")
        RANDOM_ADDRESS = 'D0:05:04:03:02:01'
        DEVICE_NAME = 'Im_The_CERT!'
        self._set_cert_privacy_policy_with_random_address(RANDOM_ADDRESS)
        logging.info("Set random address")

        self.cert_le_acl_manager.listen_for_incoming_connections()

        # Setup cert side to advertise
        create_response = self._start_cert_advertising_with_random_address(DEVICE_NAME, RANDOM_ADDRESS)
        logging.info("Started advertising")

        # Setup SL4A DUT side to scan
        addr_type = ble_address_types["random"]
        scan_callback_token = self._start_dut_scanning_for_address(addr_type, RANDOM_ADDRESS)
        logging.info("Started scanning")

        # Wait for results
        expected_event_name = scan_result.format(scan_callback_token)
        scanned_mac_address, event_info = self._wait_for_scan_result_event(expected_event_name)

        self._stop_scanning(scan_callback_token)
        assertThat(scanned_mac_address).isNotNone()
        assertThat(scanned_mac_address).isEqualTo(RANDOM_ADDRESS)

        autoconnect = True
        try:
            bluetooth_gatt, gatt_callback = setup_gatt_connection(self.dut,
                                                                  RANDOM_ADDRESS,
                                                                  autoconnect,
                                                                  timeout_seconds=self.default_timeout)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Cannot make the first connection , error={}".format(err))
            return
        cert_acl_connection = self.cert_le_acl_manager.complete_incoming_connection()
        self.dut.log.info("Device {} connected first time".format(RANDOM_ADDRESS))
        self.dut.log.info("Sleeping 5 seconds to simulate real life connection")
        time.sleep(5)
        self.dut.log.info("Disconnecting GATT")
        self._disconnect_gatt(self.dut, bluetooth_gatt, gatt_callback)
        self.dut.log.info("Device {} disconnected first time from DUT".format(RANDOM_ADDRESS))
        logging.info("Waiting 5 seconds to disconnect from CERT")
        cert_acl_connection.wait_for_disconnection_complete(timeout=timedelta(seconds=5))
        cert_acl_connection.close()
        self._stop_advertising(create_response.advertiser_id)

    def test_autoconnect_gatt_twice_with_random_address_without_pairing(self):
        """
        Steps:
        1. CERT: advertises with Random Static address
        2. DUT: connect without pairing
        3. CERT: verify GATT connection
        4. Wait 5 seconds
        5. DUT: Disconnect GATT
        6. CERT: Verify that GATT is disconnected within 30 seconds
        7. DUT: Try to connect to Cert again, and verify it can be connected
                within 30 seconds
        """
        # Use random address on cert side
        logging.info("Setting random address")
        RANDOM_ADDRESS = 'D0:05:04:03:02:01'
        DEVICE_NAME = 'Im_The_CERT!'
        self._set_cert_privacy_policy_with_random_address(RANDOM_ADDRESS)
        logging.info("Set random address")

        self.cert_le_acl_manager.listen_for_incoming_connections()

        # Setup cert side to advertise
        create_response = self._start_cert_advertising_with_random_address(DEVICE_NAME, RANDOM_ADDRESS)
        logging.info("Started advertising")

        # Setup SL4A DUT side to scan
        addr_type = ble_address_types["random"]
        scan_callback_token = self._start_dut_scanning_for_address(addr_type, RANDOM_ADDRESS)
        logging.info("Started scanning")

        # Wait for results
        expected_event_name = scan_result.format(scan_callback_token)
        scanned_mac_address, event_info = self._wait_for_scan_result_event(expected_event_name)

        self._stop_scanning(scan_callback_token)
        assertThat(scanned_mac_address).isNotNone()
        assertThat(scanned_mac_address).isEqualTo(RANDOM_ADDRESS)

        logging.info("Setting up first GATT connection to CERT")
        autoconnect = True
        try:
            bluetooth_gatt, gatt_callback = setup_gatt_connection(self.dut,
                                                                  RANDOM_ADDRESS,
                                                                  autoconnect,
                                                                  timeout_seconds=self.default_timeout)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Cannot make the first connection , error={}".format(err))
            return
        cert_acl_connection = self.cert_le_acl_manager.complete_incoming_connection()
        # listen early as GATT might be reconnected on error
        self.cert_le_acl_manager.listen_for_incoming_connections()
        self.dut.log.info("Device {} connected first time".format(RANDOM_ADDRESS))
        self.dut.log.info("Sleeping 5 seconds to simulate real life connection")
        time.sleep(5)
        self.dut.log.info("Disconnecting first GATT connection")
        self._disconnect_gatt(self.dut, bluetooth_gatt, gatt_callback)
        self.dut.log.info("Device {} disconnected first time from DUT".format(RANDOM_ADDRESS))
        logging.info("Waiting 30 seconds to disconnect from CERT")
        cert_acl_connection.wait_for_disconnection_complete(timeout=timedelta(seconds=30))
        logging.info("Setting up second GATT connection to CERT")
        try:
            bluetooth_gatt, gatt_callback = setup_gatt_connection(self.dut,
                                                                  RANDOM_ADDRESS,
                                                                  autoconnect,
                                                                  timeout_seconds=self.default_timeout)
        except GattTestUtilsError as err:
            close_gatt_client(self.dut, bluetooth_gatt)
            logging.error(err)
            asserts.fail("Cannot make the second connection , error={}".format(err))
        cert_acl_connection = self.cert_le_acl_manager.complete_incoming_connection()
        self.dut.log.info("Device {} connected second time".format(RANDOM_ADDRESS))
        self.dut.log.info("Disconnect second GATT connection")
        self._disconnect_gatt(self.dut, bluetooth_gatt, gatt_callback)
        logging.info("Wait for CERT to disconnect")
        cert_acl_connection.wait_for_disconnection_complete(timeout=timedelta(seconds=30))
        cert_acl_connection.close()
        self.dut.log.info("Device {} disconnected second time".format(RANDOM_ADDRESS))
        self._stop_advertising(create_response.advertiser_id)

    def test_disconnect_autoconnect_without_close(self):
        """
        Steps:
        1. CERT: advertises with Random Static address
        2. DUT: connect without pairing within 30 seconds
        3. CERT: verify GATT connection
        4. Wait 5 seconds
        5. DUT: Disconnect GATT, but do not close it, keep CERT advertising ON
        6. CERT: Verify that GATT is disconnected within 5 seconds
        7. CERT: Verify that no further GATT connection is made
        8. CERT: Stop advertising
        """
        # Use random address on cert side
        logging.info("Setting random address")
        RANDOM_ADDRESS = 'D0:05:04:03:02:01'
        DEVICE_NAME = 'Im_The_CERT!'
        self._set_cert_privacy_policy_with_random_address(RANDOM_ADDRESS)
        logging.info("Set random address")

        self.cert_le_acl_manager.listen_for_incoming_connections()

        # Setup cert side to advertise
        create_response = self._start_cert_advertising_with_random_address(DEVICE_NAME, RANDOM_ADDRESS)
        logging.info("Started advertising")

        # Setup SL4A DUT side to scan
        addr_type = ble_address_types["random"]
        scan_callback_token = self._start_dut_scanning_for_address(addr_type, RANDOM_ADDRESS)
        logging.info("Started scanning")

        # Wait for results
        expected_event_name = scan_result.format(scan_callback_token)
        scanned_mac_address, event_info = self._wait_for_scan_result_event(expected_event_name)

        self._stop_scanning(scan_callback_token)
        assertThat(scanned_mac_address).isNotNone()
        assertThat(scanned_mac_address).isEqualTo(RANDOM_ADDRESS)

        autoconnect = True
        try:
            bluetooth_gatt, gatt_callback = setup_gatt_connection(self.dut,
                                                                  RANDOM_ADDRESS,
                                                                  autoconnect,
                                                                  timeout_seconds=self.default_timeout)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Cannot make the first connection , error={}".format(err))
            return
        cert_acl_connection = self.cert_le_acl_manager.complete_incoming_connection()
        self.cert_le_acl_manager.listen_for_incoming_connections()
        self.dut.log.info("Device {} connected first time".format(RANDOM_ADDRESS))
        self.dut.log.info("Sleeping 5 seconds to simulate real life connection")
        time.sleep(5)
        self.dut.log.info("Disconnect first GATT connection")
        self._disconnect_gatt(self.dut, bluetooth_gatt, gatt_callback)
        self.dut.log.info("Device {} disconnected first time from DUT".format(RANDOM_ADDRESS))
        logging.info("Waiting 5 seconds to disconnect from CERT")
        cert_acl_connection.wait_for_disconnection_complete(timeout=timedelta(seconds=5))
        cert_acl_connection.close()
        logging.info("Verifying that no further GATT connection is made")
        try:
            cert_acl_connection = self.cert_le_acl_manager.complete_incoming_connection()
            asserts.fail("Should not have a GATT connection")
        except TestFailure:
            pass
        logging.info("Stop advertising")
        self._stop_advertising(create_response.advertiser_id)

    def test_autoconnect_without_proactive_disconnect(self):
        """
        Steps:
        1. CERT: advertises with Random Static address
        2. DUT: connect without pairing within 30 seconds
        3. CERT: verify GATT connection
        4. Wait 5 seconds
        5. CERT: Turn off advertising
        6. CERT: Disconnect existing GATT connection
        7. DUT: Verify that GATT is disconnected within 5 seconds
        8. CERT: Start advertising
        9. DUT: Verify GATT connects within 5 seconds
        10. CERT: Stop advertising and disconnect DUT
        11. DUT: Verify that GATT disconnects within 5 seconds
        """
        # Use random address on cert side
        logging.info("Setting random address")
        RANDOM_ADDRESS = 'D0:05:04:03:02:01'
        DEVICE_NAME = 'Im_The_CERT!'
        self._set_cert_privacy_policy_with_random_address(RANDOM_ADDRESS)
        logging.info("Set random address")

        self.cert_le_acl_manager.listen_for_incoming_connections()

        # Setup cert side to advertise
        create_response = self._start_cert_advertising_with_random_address(DEVICE_NAME, RANDOM_ADDRESS)
        logging.info("Started advertising")

        # Setup SL4A DUT side to scan
        addr_type = ble_address_types["random"]
        scan_callback_token = self._start_dut_scanning_for_address(addr_type, RANDOM_ADDRESS)
        logging.info("Started scanning")

        # Wait for results
        expected_event_name = scan_result.format(scan_callback_token)
        scanned_mac_address, event_info = self._wait_for_scan_result_event(expected_event_name)

        self._stop_scanning(scan_callback_token)
        assertThat(scanned_mac_address).isNotNone()
        assertThat(scanned_mac_address).isEqualTo(RANDOM_ADDRESS)

        autoconnect = True
        try:
            bluetooth_gatt, gatt_callback = setup_gatt_connection(self.dut,
                                                                  RANDOM_ADDRESS,
                                                                  autoconnect,
                                                                  timeout_seconds=self.default_timeout)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Cannot make the first connection , error={}".format(err))
            return
        cert_acl_connection = self.cert_le_acl_manager.complete_incoming_connection()
        self.dut.log.info("Device {} connected first time".format(RANDOM_ADDRESS))
        self.dut.log.info("Sleeping 5 seconds to simulate real life connection")
        time.sleep(5)
        logging.info("Stopping cert advertising")
        self._stop_advertising(create_response.advertiser_id)
        logging.info("Disconnecting cert")
        cert_acl_connection.disconnect()
        logging.info("Waiting for cert to disconnect")
        cert_acl_connection.wait_for_disconnection_complete(timeout=timedelta(seconds=10))
        cert_acl_connection.close()
        logging.info("Waiting for DUT to see disconnection")
        self._wait_for_gatt_disconnection(self.dut, gatt_callback)
        self.dut.log.info("Device {} disconnected first time from DUT".format(RANDOM_ADDRESS))
        logging.info("Waiting 5 seconds to disconnect from CERT")
        logging.info("Start CERT advertising")
        self.cert_le_acl_manager.listen_for_incoming_connections()
        create_response = self._start_cert_advertising_with_random_address(DEVICE_NAME, RANDOM_ADDRESS)
        logging.info("Waiting for GATT to connect")
        self._wait_for_gatt_connection(self.dut, gatt_callback, bluetooth_gatt)
        logging.info("Waiting on CERT as well for background connection")
        cert_acl_connection = self.cert_le_acl_manager.complete_incoming_connection()
        logging.info("GATT is connected via background connection")
        self.dut.log.info("Sleeping 5 seconds to simulate real life connection")
        time.sleep(5)
        logging.info("Stopping cert advertising")
        self._stop_advertising(create_response.advertiser_id)
        logging.info("Disconnecting cert")
        cert_acl_connection.disconnect()
        logging.info("Waiting for cert to disconnect")
        cert_acl_connection.wait_for_disconnection_complete(timeout=timedelta(seconds=10))
        cert_acl_connection.close()
        logging.info("Waiting for DUT to see disconnection")
        self._wait_for_gatt_disconnection(self.dut, gatt_callback)
        logging.info("Verifying that no further GATT connection is made")
        try:
            self.cert_le_acl_manager.complete_incoming_connection()
            asserts.fail("Should not have a GATT connection")
        except TestFailure:
            pass

    def test_autoconnect_without_proactive_disconnect_repeatedly(self):
        """
        Steps:
        1. CERT: advertises with Random Static address
        2. DUT: connect without pairing within 30 seconds
        3. CERT: verify GATT connection
        4. Wait 5 seconds
        5. CERT: Turn off advertising
        6. CERT: Disconnect existing GATT connection
        7. DUT: Verify that GATT is disconnected within 5 seconds
        8. CERT: Start advertising
        9. DUT: Verify GATT connects within 5 seconds
        10. CERT: Stop advertising and disconnect DUT
        11. DUT: Verify that GATT disconnects within 5 seconds
        12. Repeat step 8 to 11 for 20 times
        """
        # Use random address on cert side
        logging.info("Setting random address")
        RANDOM_ADDRESS = 'D0:05:04:03:02:01'
        DEVICE_NAME = 'Im_The_CERT!'
        self._set_cert_privacy_policy_with_random_address(RANDOM_ADDRESS)
        logging.info("Set random address")

        self.cert_le_acl_manager.listen_for_incoming_connections()

        # Setup cert side to advertise
        create_response = self._start_cert_advertising_with_random_address(DEVICE_NAME, RANDOM_ADDRESS)
        logging.info("Started advertising")

        # Setup SL4A DUT side to scan
        addr_type = ble_address_types["random"]
        scan_callback_token = self._start_dut_scanning_for_address(addr_type, RANDOM_ADDRESS)
        logging.info("Started scanning")

        # Wait for results
        expected_event_name = scan_result.format(scan_callback_token)
        scanned_mac_address, event_info = self._wait_for_scan_result_event(expected_event_name)

        self._stop_scanning(scan_callback_token)
        assertThat(scanned_mac_address).isNotNone()
        assertThat(scanned_mac_address).isEqualTo(RANDOM_ADDRESS)

        autoconnect = True
        try:
            bluetooth_gatt, gatt_callback = setup_gatt_connection(self.dut,
                                                                  RANDOM_ADDRESS,
                                                                  autoconnect,
                                                                  timeout_seconds=self.default_timeout)
        except GattTestUtilsError as err:
            logging.error(err)
            asserts.fail("Cannot make the first connection , error={}".format(err))
            return
        cert_acl_connection = self.cert_le_acl_manager.complete_incoming_connection()
        self.dut.log.info("Device {} connected first time".format(RANDOM_ADDRESS))
        self.dut.log.info("Sleeping 5 seconds to simulate real life connection")
        time.sleep(5)
        logging.info("Stopping CERT advertising")
        self._stop_advertising(create_response.advertiser_id)
        logging.info("Stopped CERT advertising, now disconnect cert")
        cert_acl_connection.disconnect()
        logging.info("Waiting for cert to disconnect")
        cert_acl_connection.wait_for_disconnection_complete(timeout=timedelta(seconds=10))
        cert_acl_connection.close()
        logging.info("Cert disconnected, waiting for DUT to see disconnection event")
        self._wait_for_gatt_disconnection(self.dut, gatt_callback)
        self.dut.log.info("Device {} disconnected first time from DUT".format(RANDOM_ADDRESS))
        logging.info("Waiting 5 seconds to disconnect from CERT")
        for i in range(20):
            logging.info("Start advertising on CERT")
            self.cert_le_acl_manager.listen_for_incoming_connections()
            create_response = self._start_cert_advertising_with_random_address(DEVICE_NAME, RANDOM_ADDRESS)
            self.dut.log.info("Wait on DUT for background GATT connection")
            self._wait_for_gatt_connection(self.dut, gatt_callback, bluetooth_gatt)
            logging.info("Waiting on CERT as well for background connection")
            cert_acl_connection = self.cert_le_acl_manager.complete_incoming_connection()
            logging.info("GATT is connected via background connection")
            self.dut.log.info("Sleeping 5 seconds to simulate real life connection")
            time.sleep(5)
            logging.info("Stop advertising from CERT")
            self._stop_advertising(create_response.advertiser_id)
            logging.info("Disconnect from CERT")
            cert_acl_connection.disconnect()
            logging.info("Waiting on CERT end for disconnection to happen")
            cert_acl_connection.wait_for_disconnection_complete(timeout=timedelta(seconds=10))
            cert_acl_connection.close()
            self.dut.log.info("Waiting on DUT end for disconnection to happen")
            self._wait_for_gatt_disconnection(self.dut, gatt_callback)
        logging.info("Verifying that no further GATT connection is made")
        try:
            cert_acl_connection = self.cert_le_acl_manager.complete_incoming_connection()
            asserts.fail("Should not have a GATT connection")
        except TestFailure:
            pass


if __name__ == '__main__':
    test_runner.main()
