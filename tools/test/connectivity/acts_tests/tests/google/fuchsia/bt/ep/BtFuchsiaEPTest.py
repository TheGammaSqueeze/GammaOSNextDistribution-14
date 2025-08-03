#!/usr/bin/env python3
#
# Copyright (C) 2020 The Android Open Source Project
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
Setup:
This test only requires two fuchsia devices.
"""

from acts import signals
from acts.base_test import BaseTestClass
from acts.controllers.fuchsia_lib.ssh import FuchsiaSSHError
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.bt.bt_test_utils import generate_id_by_size
from acts_contrib.test_utils.fuchsia.bt_test_utils import bredr_scan_for_device_by_name
from acts_contrib.test_utils.fuchsia.bt_test_utils import le_scan_for_device_by_name
from acts_contrib.test_utils.fuchsia.bt_test_utils import unbond_all_known_devices
from acts_contrib.test_utils.fuchsia.bt_test_utils import verify_device_state_by_name
import time


class BtFuchsiaEPTest(BaseTestClass):
    ble_advertise_interval = 50
    scan_timeout_seconds = 60
    default_iterations = 1000
    adv_name = generate_id_by_size(10)
    test_adv_data = {
        "name": adv_name,
        "appearance": None,
        "service_data": None,
        "tx_power_level": None,
        "service_uuids": None,
        "manufacturer_data": None,
        "uris": None,
    }
    test_connectable = True
    test_scan_response = None

    def setup_class(self):
        super().setup_class()
        for fd in self.fuchsia_devices:
            fd.sl4f.bts_lib.initBluetoothSys()
        self.pri_dut = self.fuchsia_devices[0]
        self.sec_dut = self.fuchsia_devices[1]

    def on_fail(self, test_name, begin_time):
        for fd in self.fuchsia_devices:
            fd.take_bug_report(test_name, begin_time)
        self._unbond_all_known_devices()
        self.sec_dut.sl4f.ble_lib.bleStopBleAdvertising()
        self._kill_media_services()

    def teardown_class(self):
        self._kill_media_services()

    def _kill_media_services(self):
        """Kill any BT services related to A2DP/AVRCP on all Fuchsia devices.
        """
        ssh_timeout = 30
        for fd in self.fuchsia_devices:
            try:
                fd.ssh.run("killall bt-a2dp*", timeout_sec=ssh_timeout)
                fd.ssh.run("killall bt-avrcp*", timeout_sec=ssh_timeout)
            except FuchsiaSSHError:
                pass

    def _unbond_all_known_devices(self):
        """For all Fuchsia devices, unbond any known pairings.
        """
        time.sleep(5)
        for fd in self.fuchsia_devices:
            unbond_all_known_devices(fd, self.log)

    def test_ble_awareness(self):
        """Verify that Fuchsia devices can advertise and scan each other

        Verify a Fuchsia device that starts a BLE advertisesement can be
        found by a Fuchsia BLE scanner.

        Steps:
        1. On one Fuchsia device set an advertisement
        2. On one Fuchsia device, scan for the advertisement by name

        Expected Result:
        Verify that there are no errors after each GATT connection.

        Returns:
          signals.TestPass if no errors
          signals.TestFailure if there are any errors during the test.

        TAGS: BLE
        Priority: 0
        """

        self.sec_dut.sl4f.ble_lib.bleStartBleAdvertising(
            self.test_adv_data, self.test_scan_response,
            self.ble_advertise_interval, self.test_connectable)

        device = le_scan_for_device_by_name(self.pri_dut, self.log,
                                            self.adv_name,
                                            self.scan_timeout_seconds)
        self.sec_dut.sl4f.ble_lib.bleStopBleAdvertising()
        if device is None:
            raise signals.TestFailure("Scanner unable to find advertisement.")
        raise signals.TestPass("Success")

    def test_gatt_central_peripheral(self):
        """Verify that Fuchsia devices can perform GATT operations

        Verify a Fuchsia devices can perform GATT connections and interactions.

        Steps:
        1. On one Fuchsia device set an advertisement
        2. On one Fuchsia device, scan for the advertisement by name
        3. Perform GATT connection over LE
        4. Pair both devices.
        5. Perform GATT read/write operations.
        6. Perform GATT disconnection.

        Expected Result:
        Verify that there are no errors after each GATT connection.

        Returns:
          signals.TestPass if no errors
          signals.TestFailure if there are any errors during the test.

        TAGS: BLE
        Priority: 0
        """
        self._unbond_all_known_devices()

        source_device_name = generate_id_by_size(10)
        self.pri_dut.sl4f.bts_lib.setName(source_device_name)

        self.sec_dut.sl4f.ble_lib.bleStartBleAdvertising(
            self.test_adv_data, self.test_scan_response,
            self.ble_advertise_interval, self.test_connectable)

        device = le_scan_for_device_by_name(self.pri_dut, self.log,
                                            self.adv_name,
                                            self.scan_timeout_seconds)
        if device is None:
            raise signals.TestFailure("Scanner unable to find advertisement.")

        connect_result = self.pri_dut.sl4f.gattc_lib.bleConnectToPeripheral(
            device["id"])
        if connect_result.get("error") is not None:
            raise signals.TestFailure("GATT Connection failed with: {}".format(
                connect_result.get("error")))

        if not verify_device_state_by_name(self.pri_dut, self.log,
                                           self.adv_name, "CONNECTED", None):
            raise signals.TestFailure(
                "Failed to connect to device {}.".format(target_device_name))

        if not verify_device_state_by_name(
                self.sec_dut, self.log, source_device_name, "CONNECTED", None):
            raise signals.TestFailure(
                "Failed to connect to device {}.".format(source_device_name))

        security_level = "ENCRYPTED"
        non_bondable = False
        transport = 2  #LE
        self.pri_dut.sl4f.bts_lib.pair(device["id"], security_level,
                                       non_bondable, transport)

        services = None
        if not verify_device_state_by_name(self.pri_dut, self.log,
                                           self.adv_name, "BONDED", services):
            raise signals.TestFailure(
                "Failed to pair device {}.".format(target_device_name))

        if not verify_device_state_by_name(self.sec_dut, self.log,
                                           source_device_name, "BONDED",
                                           services):
            raise signals.TestFailure(
                "Failed to pair device {}.".format(source_device_name))

        disconnect_result = self.pri_dut.sl4f.gattc_lib.bleDisconnectPeripheral(
            device["id"])
        if disconnect_result.get("error") is not None:
            raise signals.TestFailure(
                "GATT Disconnection failed with: {}".format(
                    connect_result.get("error")))

        self.sec_dut.sl4f.ble_lib.bleStopBleAdvertising()

        # TODO: Setup Proper GATT server and verify services published are found

        raise signals.TestPass("Success")

    def test_pairing_a2dp(self):
        """Verify that Fuchsia devices can pair to each other and establish
            an A2DP connection

            Verify that Fuchsia devices can pair to each other and establish
            an A2DP connection

            Steps:
            1. Clear out all bonded devices
            2. Stop any A2DP services running on the device
                Needed to take ownership of the services
            3. Init sink and source opposite devices
            4. Start pairing delegate for all Fuchsia devices
            5. Set sink device to be discoverable
            6. Discover sink device from source device
            7. Connect to sink device from source device
            8. Pair to sink device
            9. Validate paired devices and services present

            Expected Result:
            Verify devices are successfully paired and appropriate a2dp
            services are running.

            Returns:
            signals.TestPass if no errors
            signals.TestFailure if there are any errors during the test.

            TAGS: BREDR, A2DP
            Priority: 0
        """
        self._unbond_all_known_devices()
        self._kill_media_services()

        source_device_name = generate_id_by_size(10)
        target_device_name = generate_id_by_size(10)

        self.pri_dut.sl4f.bts_lib.setName(source_device_name)
        self.sec_dut.sl4f.bts_lib.setName(target_device_name)

        input_capabilities = "NONE"
        output_capabilities = "NONE"

        # Initialize a2dp on both devices.
        self.pri_dut.sl4f.avdtp_lib.init()
        self.sec_dut.sl4f.avdtp_lib.init()

        self.pri_dut.sl4f.bts_lib.acceptPairing(input_capabilities,
                                                output_capabilities)

        self.sec_dut.sl4f.bts_lib.acceptPairing(input_capabilities,
                                                output_capabilities)
        self.sec_dut.sl4f.bts_lib.setDiscoverable(True)

        unique_mac_addr_id = bredr_scan_for_device_by_name(
            self.pri_dut, self.log, target_device_name,
            self.scan_timeout_seconds)

        if not unique_mac_addr_id:
            raise signals.TestFailure(
                "Failed to find device {}.".format(target_device_name))

        connect_result = self.pri_dut.sl4f.bts_lib.connectDevice(
            unique_mac_addr_id)
        if connect_result.get("error") is not None:
            raise signals.TestFailure("Failed to connect with {}.".format(
                connect_result.get("error")))

        # We pair before checking the CONNECTED status because BR/EDR semantics
        # were recently changed such that if pairing is not confirmed, then bt
        # does not report connected = True.
        security_level = "NONE"
        bondable = True
        transport = 1  #BREDR
        pair_result = self.pri_dut.sl4f.bts_lib.pair(unique_mac_addr_id,
                                                     security_level, bondable,
                                                     transport)
        if pair_result.get("error") is not None:
            raise signals.TestFailure("Failed to pair with {}.".format(
                pair_result.get("error")))

        if not verify_device_state_by_name(
                self.pri_dut, self.log, target_device_name, "CONNECTED", None):
            raise signals.TestFailure(
                "Failed to connect to device {}.".format(target_device_name))

        if not verify_device_state_by_name(
                self.sec_dut, self.log, source_device_name, "CONNECTED", None):
            raise signals.TestFailure(
                "Failed to connect to device {}.".format(source_device_name))

        #TODO: Validation of services and paired devices (b/175641870)
        # A2DP sink: 0000110b-0000-1000-8000-00805f9b34fb
        # A2DP source: 0000110a-0000-1000-8000-00805f9b34fb
        #TODO: Make an easy function for checking/updating devices
        services = None
        if not verify_device_state_by_name(self.pri_dut, self.log,
                                           target_device_name, "BONDED",
                                           services):
            raise signals.TestFailure(
                "Failed to pair device {}.".format(target_device_name))

        if not verify_device_state_by_name(self.sec_dut, self.log,
                                           source_device_name, "BONDED",
                                           services):
            raise signals.TestFailure(
                "Failed to pair device {}.".format(source_device_name))

        raise signals.TestPass("Success")
