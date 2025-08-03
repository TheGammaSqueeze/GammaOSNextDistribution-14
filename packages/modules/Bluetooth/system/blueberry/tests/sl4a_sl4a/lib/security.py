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

from blueberry.tests.gd.cert.closable import Closable
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.sl4a_sl4a.lib.oob_data import OobData


class Security:

    # Events sent from SL4A
    SL4A_EVENT_GENERATE_OOB_DATA_SUCCESS = "GeneratedOobData"
    SL4A_EVENT_GENERATE_OOB_DATA_ERROR = "ErrorOobData"
    SL4A_EVENT_BONDED = "Bonded"
    SL4A_EVENT_UNBONDED = "Unbonded"

    # Matches tBT_TRANSPORT
    # Used Strings because ints were causing gRPC problems
    TRANSPORT_AUTO = "0"
    TRANSPORT_BREDR = "1"
    TRANSPORT_LE = "2"

    __default_timeout = 10  # seconds
    __default_bonding_timeout = 60  # seconds
    __device = None

    def __init__(self, device):
        self.__device = device
        self.__device.sl4a.bluetoothStartPairingHelper(True)

    # Returns a tuple formatted as <statuscode, OobData>. The OobData is
    # populated if the statuscode is 0 (SUCCESS), else it will be None
    def generate_oob_data(self, transport, wait_for_oob_data_callback=True):
        logging.info("Generating local OOB data")
        self.__device.sl4a.bluetoothGenerateLocalOobData(transport)

        if wait_for_oob_data_callback is False:
            return 0, None
        else:
            # Check for oob data generation success
            try:
                generate_success_event = self.__device.ed.pop_event(self.SL4A_EVENT_GENERATE_OOB_DATA_SUCCESS,
                                                                    self.__default_timeout)
            except queue.Empty as error:
                logging.error("Failed to generate OOB data!")
                # Check if generating oob data failed without blocking
                try:
                    generate_failure_event = self.__device.ed.pop_event(self.SL4A_EVENT_GENERATE_OOB_DATA_ERROR, 0)
                except queue.Empty as error:
                    logging.error("Failed to generate OOB Data without error code")
                    assertThat(True).isFalse()

                errorcode = generate_failure_event["data"]["Error"]
                logging.info("Generating local oob data failed with error code %d", errorcode)
                return errorcode, None

        logging.info("OOB ADDR with Type: %s", generate_success_event["data"]["address_with_type"])
        return 0, OobData(generate_success_event["data"]["address_with_type"],
                          generate_success_event["data"]["confirmation"], generate_success_event["data"]["randomizer"])

    def ensure_device_bonded(self):
        bond_state = None
        try:
            bond_state = self.__device.ed.pop_event(self.SL4A_EVENT_BONDED, self.__default_bonding_timeout)
        except queue.Empty as error:
            logging.error("Failed to get bond event!")

        assertThat(bond_state).isNotNone()
        logging.info("Bonded: %s", bond_state["data"]["bonded_state"])
        assertThat(bond_state["data"]["bonded_state"]).isEqualTo(True)

    def create_bond_out_of_band(self,
                                oob_data,
                                bt_device_object_address=None,
                                bt_device_object_address_type=-1,
                                wait_for_device_bonded=True):
        assertThat(oob_data).isNotNone()
        oob_data_address = oob_data.to_sl4a_address()
        oob_data_address_type = oob_data.to_sl4a_address_type()

        # If a BT Device object address isn't specified, default to the oob data
        # address and type
        if bt_device_object_address is None:
            bt_device_object_address = oob_data_address
            bt_device_object_address_type = oob_data_address_type

        logging.info("Bonding OOB with device addr=%s, device addr type=%s, oob addr=%s, oob addr type=%s",
                     bt_device_object_address, bt_device_object_address_type, oob_data_address, oob_data_address_type)
        bond_start = self.__device.sl4a.bluetoothCreateLeBondOutOfBand(
            oob_data_address, oob_data_address_type, oob_data.confirmation, oob_data.randomizer,
            bt_device_object_address, bt_device_object_address_type)
        assertThat(bond_start).isTrue()

        if wait_for_device_bonded:
            self.ensure_device_bonded()

    def create_bond_numeric_comparison(self, address, transport=TRANSPORT_LE, wait_for_device_bonded=True):
        assertThat(address).isNotNone()
        if transport == self.TRANSPORT_LE:
            self.__device.sl4a.bluetoothLeBond(address)
        else:
            self.__device.sl4a.bluetoothBond(address)
        self.ensure_device_bonded()

    def remove_all_bonded_devices(self):
        bonded_devices = self.__device.sl4a.bluetoothGetBondedDevices()
        for device in bonded_devices:
            logging.info(device)
            self.remove_bond(device["address"])

    def remove_bond(self, address):
        if self.__device.sl4a.bluetoothUnbond(address):
            bond_state = None
            try:
                bond_state = self.__device.ed.pop_event(self.SL4A_EVENT_UNBONDED, self.__default_timeout)
            except queue.Empty as error:
                logging.error("Failed to get bond event!")
            assertThat(bond_state).isNotNone()
            assertThat(bond_state["data"]["bonded_state"]).isEqualTo(False)
        else:
            logging.info("remove_bond: Bluetooth Device with address: %s does not exist", address)

    def close(self):
        self.remove_all_bonded_devices()
        self.__device.sl4a.bluetoothStartPairingHelper(False)
        self.__device = None
