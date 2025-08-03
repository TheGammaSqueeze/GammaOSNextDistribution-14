#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
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

import logging

from blueberry.tests.gd.cert.closable import Closable
from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.event_stream import EventStream
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.facade import common_pb2 as common
from google.protobuf import empty_pb2 as empty_proto

from blueberry.facade.security.facade_pb2 import AuthenticationRequirements
from blueberry.facade.security.facade_pb2 import AuthenticationRequirementsMessage
from blueberry.facade.security.facade_pb2 import SecurityPolicyMessage
from blueberry.facade.security.facade_pb2 import IoCapabilities
from blueberry.facade.security.facade_pb2 import IoCapabilityMessage
from blueberry.facade.security.facade_pb2 import OobDataBondMessage
from blueberry.facade.security.facade_pb2 import OobDataMessage
from blueberry.facade.security.facade_pb2 import UiMsgType
from blueberry.facade.security.facade_pb2 import UiCallbackMsg
from blueberry.facade.security.facade_pb2 import UiCallbackType


class PySecurity(Closable):
    """
        Abstraction for security tasks and GRPC calls
    """

    _io_capabilities_name_lookup = {
        IoCapabilities.DISPLAY_ONLY: "DISPLAY_ONLY",
        IoCapabilities.DISPLAY_YES_NO_IO_CAP: "DISPLAY_YES_NO_IO_CAP",
        IoCapabilities.KEYBOARD_ONLY: "KEYBOARD_ONLY",
        IoCapabilities.NO_INPUT_NO_OUTPUT: "NO_INPUT_NO_OUTPUT",
    }

    _auth_reqs_name_lookup = {
        AuthenticationRequirements.NO_BONDING: "NO_BONDING",
        AuthenticationRequirements.NO_BONDING_MITM_PROTECTION: "NO_BONDING_MITM_PROTECTION",
        AuthenticationRequirements.DEDICATED_BONDING: "DEDICATED_BONDING",
        AuthenticationRequirements.DEDICATED_BONDING_MITM_PROTECTION: "DEDICATED_BONDING_MITM_PROTECTION",
        AuthenticationRequirements.GENERAL_BONDING: "GENERAL_BONDING",
        AuthenticationRequirements.GENERAL_BONDING_MITM_PROTECTION: "GENERAL_BONDING_MITM_PROTECTION",
    }

    _ui_event_stream = None
    _bond_event_stream = None
    _oob_data_event_stream = None

    def __init__(self, device):
        logging.info("DUT: Init")
        self._device = device
        self._device.wait_channel_ready()
        self._ui_event_stream = EventStream(self._device.security.FetchUiEvents(empty_proto.Empty()))
        self._bond_event_stream = EventStream(self._device.security.FetchBondEvents(empty_proto.Empty()))
        self._enforce_security_policy_stream = EventStream(
            self._device.security.FetchEnforceSecurityPolicyEvents(empty_proto.Empty()))
        self._disconnect_event_stream = EventStream(self._device.security.FetchDisconnectEvents(empty_proto.Empty()))
        self._oob_data_event_stream = EventStream(
            self._device.security.FetchGetOutOfBandDataEvents(empty_proto.Empty()))

    def create_bond(self, address, type):
        """
            Triggers stack under test to create bond
        """
        logging.info("DUT: Creating bond to '%s' from '%s'" % (str(address), str(self._device.address)))
        self._device.security.CreateBond(
            common.BluetoothAddressWithType(address=common.BluetoothAddress(address=address), type=type))

    def create_bond_out_of_band(self, address, type, p192_oob_data, p256_oob_data):
        """
            Triggers stack under test to create bond using Out of Band method
        """

        logging.info("DUT: Creating OOB bond to '%s' from '%s'" % (str(address), str(self._device.address)))

        self._device.security.CreateBondOutOfBand(
            OobDataBondMessage(
                address=common.BluetoothAddressWithType(address=common.BluetoothAddress(address=address), type=type),
                p192_data=OobDataMessage(
                    address=common.BluetoothAddressWithType(
                        address=common.BluetoothAddress(address=address), type=type),
                    confirmation_value=bytes(bytearray(p192_oob_data[0])),
                    random_value=bytes(bytearray(p192_oob_data[1]))),
                p256_data=OobDataMessage(
                    address=common.BluetoothAddressWithType(
                        address=common.BluetoothAddress(address=address), type=type),
                    confirmation_value=bytes(bytearray(p256_oob_data[0])),
                    random_value=bytes(bytearray(p256_oob_data[1])))))

    def remove_bond(self, address, type):
        """
            Removes bond from stack under test
        """
        self._device.security.RemoveBond(
            common.BluetoothAddressWithType(address=common.BluetoothAddress(address=address), type=type))

    def set_io_capabilities(self, io_capabilities):
        """
            Set the IO Capabilities used for the DUT
        """
        logging.info("DUT: setting IO Capabilities data to '%s'" % self._io_capabilities_name_lookup.get(
            io_capabilities, "ERROR"))
        self._device.security.SetIoCapability(IoCapabilityMessage(capability=io_capabilities))

    def set_authentication_requirements(self, auth_reqs):
        """
            Establish authentication requirements for the stack
        """
        logging.info("DUT: setting Authentication Requirements data to '%s'" % self._auth_reqs_name_lookup.get(
            auth_reqs, "ERROR"))
        self._device.security.SetAuthenticationRequirements(AuthenticationRequirementsMessage(requirement=auth_reqs))

    def __send_ui_callback(self, address, callback_type, b, uid, pin):
        """
            Send a callback from the UI as if the user pressed a button on the dialog
        """
        logging.info("DUT: Sending user input response uid: %d; response: %s" % (uid, b))
        self._device.security.SendUiCallback(
            UiCallbackMsg(
                message_type=callback_type,
                boolean=b,
                unique_id=uid,
                pin=bytes(pin),
                address=common.BluetoothAddressWithType(
                    address=common.BluetoothAddress(address=address),
                    type=common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)))

    def enable_secure_simple_pairing(self):
        """
            This is called when you want to enable SSP for testing
            Since the stack under test already enables it by default
            we do not need to do anything here for the time being
        """
        pass

    def enable_secure_connections(self):
        pass

    def accept_pairing(self, cert_address, reply_boolean):
        """
            Here we pass, but in cert we perform pairing flow tasks.
            This was added here in order to be more dynamic, but the stack
            under test will handle the pairing flow.
        """
        pass

    def accept_oob_pairing(self, cert_address, reply_boolean):
        """
            Here we pass, but in cert we perform pairing flow tasks.
            This was added here in order to be more dynamic, but the stack
            under test will handle the pairing flow.
        """
        pass

    def wait_for_passkey(self, cert_address):
        """
            Respond to the UI event
        """
        passkey = -1

        def get_passkey(event):
            if event.message_type == UiMsgType.DISPLAY_PASSKEY:
                nonlocal passkey
                passkey = event.numeric_value
                return True
            return False

        logging.info("DUT: Waiting for expected UI event")
        assertThat(self._ui_event_stream).emits(get_passkey)
        return passkey

    def input_pin(self, cert_address, pin):
        """
            Respond to the UI event
        """
        logging.info("DUT: Inputting pin code: %s" % str(pin))
        self.on_user_input(
            cert_address=cert_address, reply_boolean=True, expected_ui_event=UiMsgType.DISPLAY_PIN_ENTRY, pin=pin)

    def on_user_input(self, cert_address, reply_boolean, expected_ui_event, pin=[]):
        """
            Respond to the UI event
        """
        if expected_ui_event is None:
            return

        ui_id = -1

        def get_unique_id(event):
            if event.message_type == expected_ui_event:
                nonlocal ui_id
                ui_id = event.unique_id
                return True
            return False

        logging.info("DUT: Waiting for expected UI event")
        assertThat(self._ui_event_stream).emits(get_unique_id)
        callback_type = UiCallbackType.YES_NO if len(pin) == 0 else UiCallbackType.PIN
        self.__send_ui_callback(cert_address, callback_type, reply_boolean, ui_id, pin)

    def get_address(self):
        return self._device.address

    def wait_for_bond_event(self, expected_bond_event):
        """
            A bond event will be triggered once the bond process
            is complete.  For the DUT we need to wait for it,
            for Cert it isn't needed.
        """
        logging.info("DUT: Waiting for Bond Event: %s " % expected_bond_event)
        assertThat(self._bond_event_stream).emits(
            lambda event: event.message_type == expected_bond_event or logging.info("DUT: Actual Bond Event: %s" % event.message_type)
        )

    def wait_for_enforce_security_event(self, expected_enforce_security_event):
        """
            We expect a 'True' or 'False' from the enforce security call

            This interface will allow the caller to wait for a callback
            result from enforcing security policy over the facade.
        """
        logging.info("DUT: Waiting for enforce security event")
        assertThat(self._enforce_security_policy_stream).emits(
            lambda event: event.result == expected_enforce_security_event or logging.info(event.result))

    def wait_for_disconnect_event(self):
        """
            The Address is expected to be returned
        """
        logging.info("DUT: Waiting for Disconnect Event")
        assertThat(self._disconnect_event_stream).emits(lambda event: logging.info("event: %s" % event.address) or True)

    def enforce_security_policy(self, address, type, policy):
        """
            Call to enforce classic security policy
        """
        self._device.security.EnforceSecurityPolicy(
            SecurityPolicyMessage(
                address=common.BluetoothAddressWithType(address=common.BluetoothAddress(address=address), type=type),
                policy=policy))

    def get_oob_data_from_controller(self, oob_data_present):
        self._device.security.GetOutOfBandData(empty_proto.Empty())
        oob_data = []

        def get_oob_data(event):
            nonlocal oob_data
            oob_data = [
                list(event.p192_data.confirmation_value),
                list(event.p192_data.random_value), [0 for i in range(0, 16)], [0 for i in range(0, 16)]
            ]
            return True

        assertThat(self._oob_data_event_stream).emits(get_oob_data)
        return oob_data

    def close(self):
        safeClose(self._ui_event_stream)
        safeClose(self._bond_event_stream)
        safeClose(self._enforce_security_policy_stream)
        safeClose(self._disconnect_event_stream)
        safeClose(self._oob_data_event_stream)
