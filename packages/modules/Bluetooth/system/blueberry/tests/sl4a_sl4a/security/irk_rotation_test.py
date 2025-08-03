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

from blueberry.facade import common_pb2 as common
from blueberry.tests.gd.cert.context import get_current_context
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd_sl4a.lib.ble_lib import disable_bluetooth
from blueberry.tests.gd_sl4a.lib.ble_lib import enable_bluetooth
from blueberry.tests.gd_sl4a.lib.bt_constants import ble_address_types
from blueberry.tests.sl4a_sl4a.lib import sl4a_sl4a_base_test
from blueberry.tests.sl4a_sl4a.lib.security import Security
from blueberry.utils.bt_gatt_constants import GattCallbackString
from blueberry.utils.bt_gatt_constants import GattTransport
from mobly import test_runner


class IrkRotationTest(sl4a_sl4a_base_test.Sl4aSl4aBaseTestClass):

    def setup_class(self):
        super().setup_class()
        self.default_timeout = 10  # seconds

    def setup_test(self):
        assertThat(super().setup_test()).isTrue()

    def teardown_test(self):
        current_test_dir = get_current_context().get_full_output_path()
        self.cert.adb.pull([
            "/data/misc/bluetooth/logs/btsnoop_hci.log",
            os.path.join(current_test_dir, "CERT_%s_btsnoop_hci.log" % self.cert.serial)
        ])
        self.cert.adb.pull([
            "/data/misc/bluetooth/logs/btsnoop_hci.log.last",
            os.path.join(current_test_dir, "CERT_%s_btsnoop_hci.log.last" % self.cert.serial)
        ])
        super().teardown_test()
        self.cert.adb.shell("setprop bluetooth.core.gap.le.privacy.enabled \'\'")

    def _wait_for_event(self, expected_event_name, device):
        try:
            event_info = device.ed.pop_event(expected_event_name, self.default_timeout)
            logging.info(event_info)
        except queue.Empty as error:
            logging.error("Failed to find event: %s", expected_event_name)
            return False
        return True

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

    def __test_le_reconnect_after_irk_rotation_cert_privacy_enabled(self):
        self._test_le_reconnect_after_irk_rotation(True)

    def test_le_reconnect_after_irk_rotation_cert_privacy_disabled(self):
        self.cert.sl4a.bluetoothDisableBLE()
        disable_bluetooth(self.cert.sl4a, self.cert.ed)
        self.cert.adb.shell("setprop bluetooth.core.gap.le.privacy.enabled false")
        self.cert.adb.shell("device_config put bluetooth INIT_logging_debug_enabled_for_all true")
        enable_bluetooth(self.cert.sl4a, self.cert.ed)
        self.cert.sl4a.bluetoothDisableBLE()
        self._test_le_reconnect_after_irk_rotation(False)

    def _bond_remote_device(self, cert_privacy_enabled, cert_public_address):
        if cert_privacy_enabled:
            self.cert_advertiser_.advertise_public_extended_pdu()
        else:
            self.cert_advertiser_.advertise_public_extended_pdu(common.PUBLIC_DEVICE_ADDRESS)

        advertising_device_name = self.cert_advertiser_.get_local_advertising_name()
        connect_address = self.dut_scanner_.scan_for_name(advertising_device_name)

        # Bond
        logging.info("Bonding with %s", connect_address)
        self.dut_security_.create_bond_numeric_comparison(connect_address)
        self.dut_scanner_.stop_scanning()
        self.cert_advertiser_.stop_advertising()

        return connect_address

    def _test_le_reconnect_after_irk_rotation(self, cert_privacy_enabled):

        cert_public_address, irk = self.__get_cert_public_address_and_irk_from_bt_config()
        self._bond_remote_device(cert_privacy_enabled, cert_public_address)

        # Remove all bonded devices to rotate the IRK
        logging.info("Unbonding all devices")
        self.dut_security_.remove_all_bonded_devices()
        self.cert_security_.remove_all_bonded_devices()

        # Bond again
        logging.info("Rebonding remote device")
        connect_address = self._bond_remote_device(cert_privacy_enabled, cert_public_address)

        # Connect GATT
        logging.info("Connecting GATT to %s", connect_address)
        gatt_callback = self.dut.sl4a.gattCreateGattCallback()
        bluetooth_gatt = self.dut.sl4a.gattClientConnectGatt(gatt_callback, connect_address, False,
                                                             GattTransport.TRANSPORT_LE, False, None)
        assertThat(bluetooth_gatt).isNotNone()
        expected_event_name = GattCallbackString.GATT_CONN_CHANGE.format(gatt_callback)
        assertThat(self._wait_for_event(expected_event_name, self.dut)).isTrue()

        # Close GATT connection
        logging.info("Closing GATT connection")
        self.dut.sl4a.gattClientClose(bluetooth_gatt)

        # Reconnect GATT
        logging.info("Reconnecting GATT")
        gatt_callback = self.dut.sl4a.gattCreateGattCallback()
        bluetooth_gatt = self.dut.sl4a.gattClientConnectGatt(gatt_callback, connect_address, False,
                                                             GattTransport.TRANSPORT_LE, False, None)
        assertThat(bluetooth_gatt).isNotNone()
        expected_event_name = GattCallbackString.GATT_CONN_CHANGE.format(gatt_callback)
        assertThat(self._wait_for_event(expected_event_name, self.dut)).isTrue()

        # Disconnect GATT
        logging.info("Disconnecting GATT")
        self.dut.sl4a.gattClientDisconnect(gatt_callback)
        expected_event_name = GattCallbackString.GATT_CONN_CHANGE.format(gatt_callback)
        assertThat(self._wait_for_event(expected_event_name, self.dut)).isTrue()

        # Reconnect GATT
        logging.info("Reconnecting GATT")
        self.dut.sl4a.gattClientReconnect(gatt_callback)
        expected_event_name = GattCallbackString.GATT_CONN_CHANGE.format(gatt_callback)
        assertThat(self._wait_for_event(expected_event_name, self.dut)).isTrue()


if __name__ == '__main__':
    test_runner.main()
