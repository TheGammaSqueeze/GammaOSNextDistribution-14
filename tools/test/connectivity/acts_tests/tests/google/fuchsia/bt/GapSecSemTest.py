#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
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
PTS GAP/SEC/SEM Automation

Optional custom parameter "collect_detailed_pass_logs"
    Used to collect link keys and extra logs on pass results
    to be used for certification purposes.
"""

from acts import signals
from acts.base_test import BaseTestClass
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.bt.bt_test_utils import generate_id_by_size
from acts_contrib.test_utils.fuchsia.bt_test_utils import le_scan_for_device_by_name
from acts_contrib.test_utils.fuchsia.bt_test_utils import get_link_keys
from acts_contrib.test_utils.fuchsia.bt_test_utils import unbond_all_known_devices
from contextlib import suppress
import inspect


class GapSecSemTest(BaseTestClass):
    gatt_connect_err_message = "Gatt connection failed with: {}"
    gatt_disconnect_err_message = "Gatt disconnection failed with: {}"
    ble_advertise_interval = 50
    scan_timeout_seconds = 60

    def setup_class(self):
        super().setup_class()
        self.pri_dut = self.fuchsia_devices[0]
        # TODO: fxb/57968 Provide Facade for setting secure connections only mode,
        # for the interim set this manually in the build.
        self.sec_dut = self.fuchsia_devices[1]
        for fd in self.fuchsia_devices:
            fd.sl4f.bts_lib.initBluetoothSys()
        # Optional user param for collecting enough information for
        # certification on pass results.
        self.collect_detailed_pass_logs = self.user_params.get(
            "collect_detailed_pass_logs", False)

    def on_fail(self, test_name, begin_time):
        for fd in self.fuchsia_devices:
            fd.take_bug_report(test_name, begin_time)

    def teardown_test(self):
        # Stop scanning and advertising on all devices at the end of a test.
        with suppress(Exception):
            for fd in self.fuchsia_devices:
                fd.sl4f.ble_lib.bleStopBleAdvertising()
                fd.bleStopBleScan()
        for fd in self.fuchsia_devices:
            unbond_all_known_devices(fd, self.log)

    def teardown_class(self):
        for fd in self.fuchsia_devices:
            fd.sl4f.bts_lib.requestDiscovery(False)

    def on_pass(self, test_name, begin_time):
        if self.collect_detailed_pass_logs == True:
            for fd in self.fuchsia_devices:
                fd.take_bt_snoop_log(test_name)
                fd.take_bug_report(test_name, begin_time)

    def _orchestrate_gatt_connection(self, central, peripheral):
        """ Orchestrate a GATT connetion from the input Central
        Fuchsia device to the Peripheral Fuchsia device.
        Args:
                central: The central Fuchsia device
                peripheral: The peripheral Fuchsia device
                peripheral: The peripheral role Fuchsia device   
        Returns:
            Dictionary of device info if connection successful.
        """
        adv_name = generate_id_by_size(10)
        adv_data = {
            "name": adv_name,
            "appearance": None,
            "service_data": None,
            "tx_power_level": None,
            "service_uuids": None,
            "manufacturer_data": None,
            "uris": None,
        }
        scan_response = None
        connectable = True

        peripheral.sl4f.ble_lib.bleStartBleAdvertising(
            adv_data, scan_response, self.ble_advertise_interval, connectable)
        scan_filter = {"name_substring": adv_name}
        central.sl4f.gattc_lib.bleStartBleScan(scan_filter)
        device = le_scan_for_device_by_name(central,
                                            self.log,
                                            adv_name,
                                            self.scan_timeout_seconds,
                                            partial_match=False,
                                            self_manage_scan=False)
        if device is None:
            raise signals.TestFailure("Scanner unable to find advertisement.")
        connect_result = central.sl4f.gattc_lib.bleConnectToPeripheral(
            device["id"])
        if connect_result.get("error") is not None:
            raise signals.TestFailure(
                self.gatt_connect_err_message.format(
                    connect_result.get("error")))
        self.log.info("Connection Successful...")

        return device

    def _orchestrate_gap_sec_sem_37_to_44_test(self, test_name, central,
                                               peripheral,
                                               is_central_pairing_initiator,
                                               security_level):
        """ Performs GAP/SEC/SEM/BV-37 to 44 tests.
            These set of tests deal with varying modes and directions of
            service level connections with LE secure Connections.

            Args:
                test_name: The name of the test for logging purposes
                central: The central role Fuchsia device
                peripheral: The peripheral role Fuchsia device
                is_central_pairing_initiator: True if Central should initiate
                    the pairing. False if Peripheral should initiate.
                security_level: 1 for Encrypted, 2 for Authenticated
            Orchestration Steps:
                1. Perform GATT connection from Central to Peripheral
                2. Pair with specified security_level in direction specified
                    by is_central_pairing_initiator.
                3. Exchange pairing pins
                4. Collect link keys if applicable
                5. Disconnect device
                6. Forget pairing.
        """
        input_capabilities = "NONE"
        output_capabilities = "NONE"

        central.sl4f.bts_lib.acceptPairing("KEYBOARD", "DISPLAY")

        peripheral.sl4f.bts_lib.acceptPairing("KEYBOARD", "DISPLAY")

        device = self._orchestrate_gatt_connection(central, peripheral)
        # TODO: fxb/71289 Change once all peer IDs are ints and not strings
        identifier = int("0x{}".format(device["id"]), 0)
        bondable = True
        transport = 2  #LE
        if is_central_pairing_initiator:
            pair_result = central.sl4f.bts_lib.pair(identifier, security_level,
                                                    bondable, transport)
        if not is_central_pairing_initiator:
            device_list = peripheral.sl4f.bts_lib.getKnownRemoteDevices(
            )['result']
            print(device_list)
            for id_dict in device_list:
                d = device_list[id_dict]
                name = None
                if d['connected'] is True:
                    did = d['id']
            pair_result = peripheral.sl4f.bts_lib.pair(did, security_level,
                                                       bondable, transport)

        pins_transferred = False
        pairing_pin = central.sl4f.bts_lib.getPairingPin()['result']
        if pairing_pin != "0" and pairing_pin is not None:
            peripheral.sl4f.bts_lib.inputPairingPin(pairing_pin)
            pins_transferred = True
        if not pins_transferred:
            pairing_pin = peripheral.sl4f.bts_lib.getPairingPin()['result']
            if pairing_pin != "0":
                central.sl4f.bts_lib.inputPairingPin(pairing_pin)

        if self.collect_detailed_pass_logs == True:
            save_path = f"{central.log_path}/{test_name}_stash_secure.store"
            self.log.info(
                f"Known Link Keys: {get_link_keys(central, save_path)}")
            save_path = f"{peripheral.log_path}/{test_name}_stash_secure.store"
            self.log.info(
                f"Known Link Keys: {get_link_keys(peripheral, save_path)}")

        disconnect_result = central.sl4f.gattc_lib.bleDisconnectPeripheral(
            device["id"])
        if disconnect_result.get("error") is not None:
            raise signals.TestFailure(
                self.gatt_disconnect_err_message.format(
                    disconnect_result.get("error")))
        self.log.info("Disconnection Successful...")

        central.sl4f.bts_lib.forgetDevice(identifier)

        raise signals.TestPass("Success")

    def test_gap_sec_sem_bv_37_c(self):
        central = self.pri_dut
        peripheral = self.sec_dut
        is_central_pairing_initiator = True
        security_level = 1  # Encrypted
        test_name = inspect.currentframe().f_code.co_name
        self._orchestrate_gap_sec_sem_37_to_44_test(
            test_name, central, peripheral, is_central_pairing_initiator,
            security_level)

    def test_gap_sec_sem_bv_38_c(self):
        central = self.pri_dut
        peripheral = self.sec_dut
        is_central_pairing_initiator = True
        security_level = 2  # Authenticated
        test_name = inspect.currentframe().f_code.co_name
        self._orchestrate_gap_sec_sem_37_to_44_test(
            test_name, central, peripheral, is_central_pairing_initiator,
            security_level)

    def test_gap_sec_sem_bv_39_c(self):
        central = self.pri_dut
        peripheral = self.sec_dut
        is_central_pairing_initiator = False
        security_level = 1  # Encrypted
        test_name = inspect.currentframe().f_code.co_name
        self._orchestrate_gap_sec_sem_37_to_44_test(
            test_name, central, peripheral, is_central_pairing_initiator,
            security_level)

    def test_gap_sec_sem_bv_40_c(self):
        central = self.pri_dut
        peripheral = self.sec_dut
        is_central_pairing_initiator = False
        security_level = 2  # Authenticated
        test_name = inspect.currentframe().f_code.co_name
        self._orchestrate_gap_sec_sem_37_to_44_test(
            test_name, central, peripheral, is_central_pairing_initiator,
            security_level)

    def test_gap_sec_sem_bv_41_c(self):
        central = self.sec_dut
        peripheral = self.pri_dut
        is_central_pairing_initiator = True
        security_level = 1  # Encrypted
        test_name = inspect.currentframe().f_code.co_name
        self._orchestrate_gap_sec_sem_37_to_44_test(
            test_name, central, peripheral, is_central_pairing_initiator,
            security_level)

    def test_gap_sec_sem_bv_42_c(self):
        central = self.sec_dut
        peripheral = self.pri_dut
        is_central_pairing_initiator = True
        security_level = 2  # Authenticated
        test_name = inspect.currentframe().f_code.co_name
        self._orchestrate_gap_sec_sem_37_to_44_test(
            test_name, central, peripheral, is_central_pairing_initiator,
            security_level)

    def test_gap_sec_sem_bv_43_c(self):
        central = self.sec_dut
        peripheral = self.pri_dut
        is_central_pairing_initiator = False
        security_level = 1  # Encrypted
        test_name = inspect.currentframe().f_code.co_name
        self._orchestrate_gap_sec_sem_37_to_44_test(
            test_name, central, peripheral, is_central_pairing_initiator,
            security_level)

    def test_gap_sec_sem_bv_44_c(self):
        central = self.sec_dut
        peripheral = self.pri_dut
        is_central_pairing_initiator = False
        security_level = 2  # Authenticated
        test_name = inspect.currentframe().f_code.co_name
        self._orchestrate_gap_sec_sem_37_to_44_test(
            test_name, central, peripheral, is_central_pairing_initiator,
            security_level)
