# Copyright (C) 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import threading
import textwrap
import uuid
import re
import sys

from mmi2grpc._helpers import assert_description, match_description
from mmi2grpc._proxy import ProfileProxy
from mmi2grpc._rootcanal import Dongle

from pandora.host_grpc import Host
from pandora.host_pb2 import RANDOM
from pandora.security_grpc import Security
from pandora.security_pb2 import LE_LEVEL3, PairingEventAnswer
from pandora_experimental.gatt_grpc import GATT

BASE_UUID = uuid.UUID("00000000-0000-1000-8000-00805F9B34FB")


def short_uuid(full: uuid.UUID) -> int:
    return (uuid.UUID(full).int - BASE_UUID.int) >> 96


class HOGPProxy(ProfileProxy):

    def __init__(self, channel, rootcanal):
        super().__init__(channel)
        self.host = Host(channel)
        self.security = Security(channel)
        self.gatt = GATT(channel)
        self.rootcanal = rootcanal
        self.connection = None
        self.pairing_stream = self.security.OnPairing()
        self.characteristic_reads = {}

    def test_started(self, test: str, **kwargs):
        self.rootcanal.select_pts_dongle(Dongle.CSR_RCK_PTS_DONGLE)

        return "OK"

    @assert_description
    def IUT_INITIATE_CONNECTION(self, pts_addr: bytes, **kwargs):
        """
        Please initiate a GATT connection to the PTS.

        Description: Verify that
        the Implementation Under Test (IUT) can initiate a GATT connect request
        to the PTS.
        """

        self.connection = self.host.ConnectLE(own_address_type=RANDOM, public=pts_addr).connection

        def secure():
            self.security.Secure(connection=self.connection, le=LE_LEVEL3)

        threading.Thread(target=secure).start()

        return "OK"

    @match_description
    def _mmi_2004(self, pts_addr: bytes, passkey: str, **kwargs):
        """
        Please confirm that 6 digit number is matched with (?P<passkey>[0-9]*).
        """
        received = []
        for event in self.pairing_stream:
            if event.address == pts_addr and event.numeric_comparison == int(passkey):
                self.pairing_stream.send(PairingEventAnswer(
                    event=event,
                    confirm=True,
                ))
                return "OK"
            received.append(event.numeric_comparison)

        assert False, f"mismatched passcode: expected {passkey}, received {received}"

    @match_description
    def IUT_SEND_WRITE_REQUEST(self, handle: str, properties: str, **kwargs):
        r"""
        Please send write request to handle (?P<handle>\S*) with following value.
        Client
        Characteristic Configuration:
             Properties: \[0x00(?P<properties>\S*)\]
        """

        self.gatt.WriteAttFromHandle(
            connection=self.connection,
            handle=int(handle, base=16),
            value=bytes([int(f"0x{properties}", base=16), 0]),
        )

        return "OK"

    @match_description
    def USER_CONFIRM_CHARACTERISTIC(self, body: str, **kwargs):
        r"""
        Please verify that following attribute handle/UUID pair was returned
        containing the UUID for the (.*)\.

        (?P<body>.*)
        """

        PATTERN = re.compile(
            textwrap.dedent(r"""
                Attribute Handle = (\S*)
                Characteristic Properties = (?P<properties>\S*)
                Handle = (?P<handle>\S*)
                UUID = (?P<uuid>\S*)
                """).strip().replace("\n", " "))

        targets = set()

        for match in PATTERN.finditer(body):
            targets.add((
                int(match.group("properties"), base=16),
                int(match.group("handle"), base=16),
                int(match.group("uuid"), base=16),
            ))

        assert len(targets) == body.count("Characteristic Properties"), "safety check that regex is matching something"

        services = self.gatt.DiscoverServices(connection=self.connection).services

        for service in services:
            for characteristic in service.characteristics:
                uuid_16 = short_uuid(characteristic.uuid)
                key = (characteristic.properties, characteristic.handle, uuid_16)
                if key in targets:
                    targets.remove(key)

        assert not targets, f"could not find handles: {targets}"

        return "OK"

    @match_description
    def USER_CONFIRM_CHARACTERISTIC_DESCRIPTOR(self, body: str, **kwargs):
        r"""
        Please verify that following attribute handle/UUID pair was returned
        containing the UUID for the (.*)\.

        (?P<body>.*)
        """

        PATTERN = re.compile(rf"handle = (?P<handle>\S*)\s* uuid = (?P<uuid>\S*)")

        targets = set()

        for match in PATTERN.finditer(body):
            targets.add((
                int(match.group("handle"), base=16),
                int(match.group("uuid"), base=16),
            ))

        assert len(targets) == body.count("uuid = "), "safety check that regex is matching something"

        services = self.gatt.DiscoverServices(connection=self.connection).services

        for service in services:
            for characteristic in service.characteristics:
                for descriptor in characteristic.descriptors:
                    uuid_16 = short_uuid(descriptor.uuid)
                    key = (descriptor.handle, uuid_16)
                    if key in targets:
                        targets.remove(key)

        assert not targets, f"could not find handles: {targets}"

        return "OK"

    @match_description
    def USER_CONFIRM_SERVICE_HANDLE(self, service_name: str, body: str, **kwargs):
        r"""
        Please confirm the following handles for (?P<service_name>.*)\.

        (?P<body>.*)
        """

        PATTERN = re.compile(r"Start Handle: (?P<start_handle>\S*) End Handle: (?P<end_handle>\S*)")

        SERVICE_UUIDS = {
            "Device Information": 0x180A,
            "Battery Service": 0x180F,
            "Human Interface Device": 0x1812,
        }

        target_uuid = SERVICE_UUIDS[service_name]

        services = self.gatt.DiscoverServices(connection=self.connection).services

        assert len(
            PATTERN.findall(body)) == body.count("Start Handle:"), "safety check that regex is matching something"

        for match in PATTERN.finditer(body):
            start_handle = match.group("start_handle")

            for service in services:
                if service.handle == int(start_handle, base=16):
                    assert (short_uuid(service.uuid) == target_uuid), "service UUID does not match expected type"
                    break
            else:
                assert False, f"cannot find service with start handle {start_handle}"

        return "OK"

    @assert_description
    def _mmi_1(self, **kwargs):
        """
        Please confirm that the IUT ignored the received Notification and did
        not report the values to the Upper Tester.
        """

        # TODO

        return "OK"

    @match_description
    def IUT_CONFIG_NOTIFICATION(self, value: str, **kwargs):
        r"""
        Please write to Client Characteristic Configuration Descriptor of Report
        characteristic to enable notification.

        Descriptor handle value: (?P<value>\S*)
        """

        self.gatt.WriteAttFromHandle(
            connection=self.connection,
            handle=int(value, base=16),
            value=bytes([0x01, 0x00]),
        )

        return "OK"

    @match_description
    def IUT_READ_CHARACTERISTIC(self, test: str, characteristic_name: str, handle: str, **kwargs):
        r"""
        Please send Read Request to read (?P<characteristic_name>.*) characteristic with handle =
        (?P<handle>\S*).
        """

        TESTS_READING_CHARACTERISTIC_NOT_DESCRIPTORS = [
            "HOGP/RH/HGRF/BV-01-I",
            "HOGP/RH/HGRF/BV-10-I",
            "HOGP/RH/HGRF/BV-12-I",
        ]

        action = (self.gatt.ReadCharacteristicFromHandle if test in TESTS_READING_CHARACTERISTIC_NOT_DESCRIPTORS else
                  self.gatt.ReadCharacteristicDescriptorFromHandle)

        handle = int(handle, base=16)
        self.characteristic_reads[handle] = action(
            connection=self.connection,
            handle=handle,
        ).value.value

        return "OK"

    @match_description
    def USER_CONFIRM_READ_RESULT(self, characteristic_name: str, body: str, **kwargs):
        r"""
        Please verify following (?P<characteristic_name>.*) Characteristic value is Read.

        (?P<body>.*)
        """

        blocks = re.split("Handle:", body)

        HEX = "[0-9A-F]"
        PATTERN = re.compile(f"0x{HEX*2}(?:{HEX*2})?")

        num_checks = 0

        for block in blocks:
            data = PATTERN.findall(block)
            if not data:
                continue

            # first hex value is the handle, rest is the expected data
            handle, *data = data

            handle = int(handle, base=16)

            actual = self.characteristic_reads[handle]

            expected = []
            for word in data:
                if len(word) == len("0x0000"):
                    first = int(word[2:4], base=16)
                    second = int(word[4:6], base=16)

                    if "bytes in LSB order" in body:
                        little = first
                        big = second
                    else:
                        little = second
                        big = first

                    expected.append(little)
                    expected.append(big)
                else:
                    expected.append(int(word, base=16))

            expected = bytes(expected)

            num_checks += 1
            assert (expected == actual), f"Got unexpected value for handle {handle}: {repr(expected)} != {repr(actual)}"

        assert (body.count("Handle:") == num_checks), "safety check that regex is matching something"

        return "OK"

    @assert_description
    def MMI_VERIFY_SECURE_ID(self, pts_addr: bytes, **kwargs):
        """
        Please enter the secure ID.
        """

        for event in self.pairing_stream:
            if event.address == pts_addr and event.passkey_entry_notification:
                print(f"Got passkey entry {event.passkey_entry_notification}", file=sys.stderr)
                return str(event.passkey_entry_notification)

        assert False
