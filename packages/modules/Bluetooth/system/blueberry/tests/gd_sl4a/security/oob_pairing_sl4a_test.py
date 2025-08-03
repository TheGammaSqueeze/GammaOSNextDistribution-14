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

from google.protobuf import empty_pb2 as empty_proto

import hci_packets as hci

from blueberry.tests.gd_sl4a.lib import gd_sl4a_base_test
from blueberry.tests.gd_sl4a.lib.bt_constants import ble_scan_settings_phys
from blueberry.tests.gd.cert.matchers import SecurityMatchers
from blueberry.tests.gd.cert.py_le_security import PyLeSecurity
from blueberry.tests.gd.cert.truth import assertThat

from blueberry.facade import common_pb2 as common
from blueberry.facade.hci import le_advertising_manager_facade_pb2 as le_advertising_facade
from blueberry.facade.hci import le_initiator_address_facade_pb2 as le_initiator_address_facade
from blueberry.facade.security.facade_pb2 import AdvertisingCallbackMsgType
from blueberry.facade.security.facade_pb2 import BondMsgType
from blueberry.facade.security.facade_pb2 import LeAuthRequirementsMessage
from blueberry.facade.security.facade_pb2 import LeIoCapabilityMessage
from blueberry.facade.security.facade_pb2 import LeOobDataPresentMessage
from blueberry.facade.security.facade_pb2 import UiCallbackMsg
from blueberry.facade.security.facade_pb2 import UiCallbackType
from blueberry.facade.security.facade_pb2 import UiMsgType

from mobly import test_runner

LeIoCapabilities = LeIoCapabilityMessage.LeIoCapabilities
LeOobDataFlag = LeOobDataPresentMessage.LeOobDataFlag

DISPLAY_ONLY = LeIoCapabilityMessage(capabilities=LeIoCapabilities.DISPLAY_ONLY)

OOB_NOT_PRESENT = LeOobDataPresentMessage(data_present=LeOobDataFlag.NOT_PRESENT)


class OobData:

    address = None
    confirmation = None
    randomizer = None

    def __init__(self, address, confirmation, randomizer):
        self.address = address
        self.confirmation = confirmation
        self.randomizer = randomizer


class OobPairingSl4aTest(gd_sl4a_base_test.GdSl4aBaseTestClass):
    # Events sent from SL4A
    SL4A_EVENT_GENERATED = "GeneratedOobData"
    SL4A_EVENT_ERROR = "ErrorOobData"
    SL4A_EVENT_BONDED = "Bonded"
    SL4A_EVENT_UNBONDED = "Unbonded"

    # Matches tBT_TRANSPORT
    # Used Strings because ints were causing gRPC problems
    TRANSPORT_AUTO = "0"
    TRANSPORT_BREDR = "1"
    TRANSPORT_LE = "2"

    def setup_class(self):
        super().setup_class(cert_module='SECURITY')
        self.default_timeout = 5  # seconds

    def setup_test(self):
        super().setup_test()
        self.cert_security = PyLeSecurity(self.cert)

    def teardown_test(self):
        self.cert_security.close()
        super().teardown_test()

    def __get_test_irk(self):
        return bytes(
            bytearray([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10]))

    def _generate_sl4a_oob_data(self, transport):
        logging.info("Fetching OOB data...")
        self.dut.sl4a.bluetoothGenerateLocalOobData(transport)
        try:
            event_info = self.dut.ed.pop_event(self.SL4A_EVENT_GENERATED, self.default_timeout)
        except queue.Empty as error:
            logging.error("Failed to generate OOB data!")
            return None
        logging.info("Data received!")
        logging.info(event_info["data"])
        return OobData(event_info["data"]["address_with_type"], event_info["data"]["confirmation"],
                       event_info["data"]["randomizer"])

    def _generate_cert_oob_data(self, transport):
        if transport == self.TRANSPORT_LE:
            oob_data = self.cert.security.GetLeOutOfBandData(empty_proto.Empty())
            # GetLeOutOfBandData adds null terminator to string in C code
            # (length 17) before passing back to python via gRPC where it is
            # converted back to bytes. Remove the null terminator for handling
            # in python test, since length is known to be 16 for
            # confirmation_value and random_value
            oob_data.confirmation_value = oob_data.confirmation_value[:-1]
            oob_data.random_value = oob_data.random_value[:-1]
            return oob_data
        return None

    def _set_cert_privacy_policy_with_random_address_but_advertise_resolvable(self, irk):
        # Random static address below, no random resolvable address at this point
        random_address_bytes = "DD:34:02:05:5C:EE".encode()
        private_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_RESOLVABLE_ADDRESS,
            address_with_type=common.BluetoothAddressWithType(
                address=common.BluetoothAddress(address=random_address_bytes), type=common.RANDOM_DEVICE_ADDRESS),
            rotation_irk=irk)
        self.cert.security.SetLeInitiatorAddressPolicy(private_policy)
        # Bluetooth MAC address must be upper case
        return random_address_bytes.decode('utf-8').upper()

    def __advertise_rpa_random_policy(self, legacy_pdus, irk):
        DEVICE_NAME = 'Im_The_CERT!'
        logging.info("Getting random address")
        ADDRESS = self._set_cert_privacy_policy_with_random_address_but_advertise_resolvable(irk)
        logging.info("Done %s" % ADDRESS)

        # Setup cert side to advertise
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME,
                               data=list(bytes(DEVICE_NAME, encoding='utf8')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
        config = le_advertising_facade.AdvertisingConfig(
            advertisement=[gap_data],
            interval_min=512,
            interval_max=768,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_RANDOM_DEVICE_ADDRESS,
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)
        extended_config = le_advertising_facade.ExtendedAdvertisingConfig(
            include_tx_power=True,
            connectable=True,
            legacy_pdus=legacy_pdus,
            advertising_config=config,
            secondary_advertising_phy=ble_scan_settings_phys["1m"])
        request = le_advertising_facade.ExtendedCreateAdvertiserRequest(config=extended_config)
        logging.info("Creating %s PDU advertiser..." % ("Legacy" if legacy_pdus else "Extended"))
        create_response = self.cert.hci_le_advertising_manager.ExtendedCreateAdvertiser(request)
        logging.info("%s PDU advertiser created." % ("Legacy" if legacy_pdus else "Extended"))
        return (ADDRESS, create_response)

    def _advertise_rpa_random_legacy_pdu(self, irk):
        return self.__advertise_rpa_random_policy(True, irk)

    def _advertise_rpa_random_extended_pdu(self, irk):
        return self.__advertise_rpa_random_policy(False, irk)

    def _stop_advertising(self, advertiser_id):
        logging.info("Stop advertising")
        remove_request = le_advertising_facade.RemoveAdvertiserRequest(advertiser_id=advertiser_id)
        self.cert.hci_le_advertising_manager.RemoveAdvertiser(remove_request)
        logging.info("Stopped advertising")

    def _wait_for_own_address(self):
        own_address = common.BluetoothAddress()

        def get_address(event):
            if event.message_type == AdvertisingCallbackMsgType.OWN_ADDRESS_READ:
                nonlocal own_address
                own_address = event.address.address
                return True
            return False

        assertThat(self.cert_security.get_advertising_callback_event_stream()).emits(get_address)
        return own_address

    def _wait_for_advertising_set_started(self):
        advertising_started = False

        def get_advertising_set_started(event):
            if event.message_type == AdvertisingCallbackMsgType.ADVERTISING_SET_STARTED:
                nonlocal advertising_started
                if event.advertising_started == 1:
                    advertising_started = True
                return True
            return False

        assertThat(self.cert_security.get_advertising_callback_event_stream()).emits(get_advertising_set_started)
        return advertising_started

    def _wait_for_yes_no_dialog(self):
        address_with_type = common.BluetoothAddressWithType()

        def get_address_with_type(event):
            if event.message_type == UiMsgType.DISPLAY_PAIRING_PROMPT:
                nonlocal address_with_type
                address_with_type = event.peer
                return True
            return False

        assertThat(self.cert_security.get_ui_stream()).emits(get_address_with_type)
        return address_with_type

    def test_sl4a_classic_generate_oob_data(self):
        oob_data = self._generate_sl4a_oob_data(self.TRANSPORT_BREDR)
        logging.info("OOB data received")
        logging.info(oob_data)
        assertThat(oob_data).isNotNone()

    def test_sl4a_classic_generate_oob_data_twice(self):
        self.test_sl4a_classic_generate_oob_data()
        self.test_sl4a_classic_generate_oob_data()

    def test_sl4a_ble_generate_oob_data(self):
        oob_data = self._generate_sl4a_oob_data(self.TRANSPORT_LE)
        assertThat(oob_data).isNotNone()

    def test_cert_ble_generate_oob_data(self):
        oob_data = self._generate_cert_oob_data(self.TRANSPORT_LE)
        assertThat(oob_data).isNotNone()

    def _bond_sl4a_cert_oob(self):
        self.cert.security.SetLeIoCapability(DISPLAY_ONLY)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert.security.SetLeAuthRequirements(LeAuthRequirementsMessage(bond=1, mitm=1, secure_connections=1))

        RANDOM_ADDRESS, create_response = self._advertise_rpa_random_extended_pdu(self.__get_test_irk())

        self._wait_for_advertising_set_started()

        get_own_address_request = le_advertising_facade.GetOwnAddressRequest(
            advertiser_id=create_response.advertiser_id)
        self.cert.hci_le_advertising_manager.GetOwnAddress(get_own_address_request)
        advertising_address = self._wait_for_own_address()

        oob_data = self._generate_cert_oob_data(self.TRANSPORT_LE)
        assertThat(oob_data).isNotNone()

        self.dut.sl4a.bluetoothCreateBondOutOfBand(
            advertising_address.decode("utf-8").upper(), self.TRANSPORT_LE, oob_data.confirmation_value.hex(),
            oob_data.random_value.hex())

        address_with_type = self._wait_for_yes_no_dialog()
        self.cert.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=address_with_type))

        assertThat(self.cert_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(BondMsgType.DEVICE_BONDED))

        try:
            bond_state = self.dut.ed.pop_event(self.SL4A_EVENT_BONDED, self.default_timeout)
        except queue.Empty as error:
            logging.error("Failed to get bond event!")

        assertThat(bond_state).isNotNone()
        logging.info("Bonded: %s", bond_state["data"]["bonded_state"])
        assertThat(bond_state["data"]["bonded_state"]).isEqualTo(True)
        self._stop_advertising(create_response.advertiser_id)
        return (RANDOM_ADDRESS, advertising_address)

    def test_sl4a_create_bond_out_of_band(self):
        self._bond_sl4a_cert_oob()

    def test_generate_oob_after_pairing(self):
        self._bond_sl4a_cert_oob()
        assertThat(self._generate_sl4a_oob_data(self.TRANSPORT_LE)).isNotNone()

    def test_remove_bond(self):
        self.dut.sl4a.bluetoothUnbond(self._bond_sl4a_cert_oob()[1].decode().upper())
        bond_state = None
        try:
            bond_state = self.dut.ed.pop_event(self.SL4A_EVENT_UNBONDED, self.default_timeout)
        except queue.Empty as error:
            logging.error("Failed to get bond event!")

        assertThat(bond_state).isNotNone()
        assertThat(bond_state["data"]["bonded_state"]).isEqualTo(False)


if __name__ == '__main__':
    test_runner.main()
