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

import re
import sys
import time
from threading import Thread

from mmi2grpc._helpers import assert_description, match_description
from mmi2grpc._proxy import ProfileProxy
from mmi2grpc._rootcanal import Dongle

from pandora_experimental.gatt_grpc import GATT
from pandora.host_grpc import Host
from pandora.host_pb2 import PUBLIC, RANDOM
from pandora.security_grpc import Security, SecurityStorage
from pandora.security_pb2 import PairingEventAnswer
from pandora_experimental.gatt_pb2 import (
    INVALID_HANDLE,
    READ_NOT_PERMITTED,
    UNKNOWN_ERROR,
    INSUFFICIENT_AUTHENTICATION,
    ATTRIBUTE_NOT_FOUND,
    APPLICATION_ERROR,
    INVALID_ATTRIBUTE_LENGTH,
    WRITE_NOT_PERMITTED,
    PERMISSION_NONE,
    PROPERTY_READ,
    PROPERTY_WRITE,
    PERMISSION_READ,
    PERMISSION_WRITE,
    PERMISSION_READ_ENCRYPTED,
    PERMISSION_READ_ENCRYPTED_MITM,
    PERMISSION_WRITE_ENCRYPTED,
    PERMISSION_WRITE_ENCRYPTED_MITM,
    ENABLE_NOTIFICATION_VALUE,
    ENABLE_INDICATION_VALUE,
)
from pandora_experimental.gatt_pb2 import GattServiceParams
from pandora_experimental.gatt_pb2 import GattCharacteristicParams
from pandora_experimental.gatt_pb2 import GattDescriptorParams
from pandora_experimental.gatt_pb2 import ReadCharacteristicResponse
from pandora_experimental.gatt_pb2 import ReadCharacteristicsFromUuidResponse

# Tests that need GATT cache cleared before discovering services.
NEEDS_CACHE_CLEARED = {
    "GATT/CL/GAD/BV-01-C",
    "GATT/CL/GAD/BV-06-C",
}

MMI_SERVER = {
    "GATT/SR/GAD/BV-01-C",
}

BASE_UUID = "0000XXXX-0000-1000-8000-00805F9B34FB"

# These UUIDs are used as reference for GATT server tests
BASE_READ_WRITE_SERVICE_UUID = "0000FFFE-0000-1000-8000-00805F9B34FB"
BASE_READ_CHARACTERISTIC_UUID = "0000FFD-0000-1000-8000-00805F9B34FB"
BASE_WRITE_CHARACTERISTIC_UUID = "0000FFFA-0000-1000-8000-00805F9B34FB"
BASE_READ_WRITE_ENCRYPTED_CHARACTERISTIC_UUID = "0000FFF9-0000-1000-8000-00805F9B34FB"
BASE_READ_WRITE_ENCRYPTED_MITM_CHARACTERISTIC_UUID = "0000FFF8-0000-1000-8000-00805F9B34FB"
BASE_READ_DESCRIPTOR_UUID = "0000FFF7-0000-1000-8000-00805F9B34FB"
BASE_WRITE_DESCRIPTOR_UUID = "0000FFF6-0000-1000-8000-00805F9B34FB"
CUSTOM_SERVICE_UUID = "0000FFF5-0000-1000-8000-00805F9B34FB"
CUSTOM_CHARACTERISTIC_UUID = "0000FFF4-0000-1000-8000-00805F9B34FB"


class GATTProxy(ProfileProxy):

    def __init__(self, channel, rootcanal):
        super().__init__(channel)
        self.gatt = GATT(channel)
        self.host = Host(channel)
        self.security = Security(channel)
        self.security_storage = SecurityStorage(channel)
        self.rootcanal = rootcanal
        self.connection = None
        self.services = None
        self.characteristics = None
        self.descriptors = None
        self.read_response = None
        self.write_response = None
        self.characteristic_notification_received = False
        self.handles = []
        self.written_over_length = False
        self.last_added_service = None

    def test_started(self, test: str, **kwargs):
        self.rootcanal.select_pts_dongle(Dongle.CSR_RCK_PTS_DONGLE)

        return "OK"

    @assert_description
    def MMI_IUT_INITIATE_CONNECTION(self, test, pts_addr: bytes, **kwargs):
        """
        Please initiate a GATT connection to the PTS.

        Description: Verify that
        the Implementation Under Test (IUT) can initiate GATT connect request to
        PTS.
        """

        self.connection = self.host.ConnectLE(own_address_type=RANDOM, public=pts_addr).connection
        if test in NEEDS_CACHE_CLEARED:
            self.gatt.ClearCache(connection=self.connection)
        return "OK"

    @assert_description
    def MMI_IUT_INITIATE_DISCONNECTION(self, **kwargs):
        """
        Please initiate a GATT disconnection to the PTS.

        Description: Verify
        that the Implementation Under Test (IUT) can initiate GATT disconnect
        request to PTS.
        """

        assert self.connection is not None
        self.host.Disconnect(connection=self.connection)
        self.connection = None
        self.services = None
        self.characteristics = None
        self.descriptors = None
        self.read_response = None
        self.write_response = None
        self.characteristic_notification_received = False
        self.handles = []
        self.written_over_length = False
        self.last_added_service = None
        return "OK"

    @assert_description
    def MMI_IUT_MTU_EXCHANGE(self, **kwargs):
        """
        Please send exchange MTU command to the PTS.

        Description: Verify that
        the Implementation Under Test (IUT) can send Exchange MTU command to the
        tester.
        """

        assert self.connection is not None
        self.gatt.ExchangeMTU(mtu=512, connection=self.connection)
        return "OK"

    def MMI_IUT_SEND_PREPARE_WRITE_REQUEST_VALID_SIZE(self, description: str, **kwargs):
        """
        Please send prepare write request with handle = 'XXXX'O and size = 'XXX'
        to the PTS.

        Description: Verify that the Implementation Under Test
        (IUT) can send data according to negotiate MTU size.
        """

        assert self.connection is not None
        matches = re.findall("'([a0-Z9]*)'O and size = '([a0-Z9]*)'", description)
        handle = int(matches[0][0], 16)
        data = bytes([1]) * int(matches[0][1])
        self.gatt.WriteAttFromHandle(connection=self.connection,\
                handle=handle, value=data)
        return "OK"

    @assert_description
    def MMI_IUT_DISCOVER_PRIMARY_SERVICES(self, **kwargs):
        """
        Please send discover all primary services command to the PTS.
        Description: Verify that the Implementation Under Test (IUT) can send
        Discover All Primary Services.
        """

        assert self.connection is not None
        self.services = self.gatt.DiscoverServices(connection=self.connection).services
        return "OK"

    def MMI_SEND_PRIMARY_SERVICE_UUID(self, description: str, **kwargs):
        """
        Please send discover primary services with UUID value set to 'XXXX'O to
        the PTS.

        Description: Verify that the Implementation Under Test (IUT)
        can send Discover Primary Services UUID = 'XXXX'O.
        """

        assert self.connection is not None
        uuid = formatUuid(re.findall("'([a0-Z9]*)'O", description)[0])
        self.services = self.gatt.DiscoverServiceByUuid(connection=self.connection,\
                uuid=uuid).services
        return "OK"

    def MMI_SEND_PRIMARY_SERVICE_UUID_128(self, description: str, **kwargs):
        """
        Please send discover primary services with UUID value set to
        'XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX'O to the PTS.

        Description:
        Verify that the Implementation Under Test (IUT) can send Discover
        Primary Services UUID = 'XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX'O.
        """

        assert self.connection is not None
        uuid = formatUuid(re.findall("'([a0-Z9-]*)'O", description)[0])
        self.services = self.gatt.DiscoverServiceByUuid(connection=self.connection,\
                uuid=uuid).services
        return "OK"

    def MMI_CONFIRM_PRIMARY_SERVICE_UUID(self, **kwargs):
        """
        Please confirm IUT received primary services uuid = 'XXXX'O , Service
        start handle = 'XXXX'O, end handle = 'XXXX'O in database. Click Yes if
        IUT received it, otherwise click No.

        Description: Verify that the
        Implementation Under Test (IUT) can send Discover primary service by
        UUID in database.
        """

        # Android doesn't store services discovered by UUID.
        return "Yes"

    @assert_description
    def MMI_CONFIRM_NO_PRIMARY_SERVICE_SMALL(self, **kwargs):
        """
        Please confirm that IUT received NO service uuid found in the small
        database file. Click Yes if NO service found, otherwise click No.
        Description: Verify that the Implementation Under Test (IUT) can send
        Discover primary service by UUID in small database.
        """

        # Android doesn't store services discovered by UUID.
        return "Yes"

    def MMI_CONFIRM_PRIMARY_SERVICE_UUID_128(self, **kwargs):
        """
        Please confirm IUT received primary services uuid=
        'XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX'O, Service start handle =
        'XXXX'O, end handle = 'XXXX'O in database. Click Yes if IUT received it,
        otherwise click No.

        Description: Verify that the Implementation Under
        Test (IUT) can send Discover primary service by UUID in database.
        """

        # Android doesn't store services discovered by UUID.
        return "Yes"

    def MMI_CONFIRM_PRIMARY_SERVICE(self, test, description: str, **kwargs):
        """
        Please confirm IUT received primary services Primary Service = 'XXXX'O
        Primary Service = 'XXXX'O  in database. Click Yes if IUT received it,
        otherwise click No.

        Description: Verify that the Implementation Under
        Test (IUT) can send Discover all primary services in database.
        """

        if test not in MMI_SERVER:
            assert self.services is not None
            all_matches = list(map(formatUuid, re.findall("'([a0-Z9]*)'O", description)))
            assert all(uuid in list(map(lambda service: service.uuid, self.services))\
                    for uuid in all_matches)
        return "OK"

    @assert_description
    def MMI_IUT_FIND_INCLUDED_SERVICES(self, **kwargs):
        """
        Please send discover all include services to the PTS to discover all
        Include Service supported in the PTS. Discover primary service if
        needed.

        Description: Verify that the Implementation Under Test (IUT)
        can send Discover all include services command.
        """

        assert self.connection is not None
        self.services = self.gatt.DiscoverServices(connection=self.connection).services
        return "OK"

    @assert_description
    def MMI_CONFIRM_NO_INCLUDE_SERVICE(self, **kwargs):
        """
        There is no include service in the database file.

        Description: Verify
        that the Implementation Under Test (IUT) can send Discover all include
        services in database.
        """

        assert self.connection is not None
        assert self.services is not None
        for service in self.services:
            assert len(service.included_services) == 0
        return "OK"

    def MMI_CONFIRM_INCLUDE_SERVICE(self, description: str, **kwargs):
        """
        Please confirm IUT received include services:

        Attribute Handle = 'XXXX'O, Included Service Attribute handle = 'XXXX'O,
        End Group Handle = 'XXXX'O, Service UUID = 'XXXX'O

        Click Yes if IUT received it, otherwise click No.

        Description: Verify
        that the Implementation Under Test (IUT) can send Discover all include
        services in database.
        """

        assert self.connection is not None
        assert self.services is not None
        """
        Number of checks can vary but information is always the same,
        so we need to iterate through the services and check if its included
        services match one of these.
        """
        all_matches = re.findall("'([a0-Z9]*)'O", description)
        found_services = 0
        for service in self.services:
            for i in range(0, len(all_matches), 4):
                if compareIncludedServices(service,\
                        (stringHandleToInt(all_matches[i])),\
                        stringHandleToInt(all_matches[i + 1]),\
                        formatUuid(all_matches[i + 3])):
                    found_services += 1
        assert found_services == (len(all_matches) / 4)
        return "Yes"

    def MMI_IUT_DISCOVER_SERVICE_UUID(self, description: str, **kwargs):
        """
        Discover all characteristics of service UUID= 'XXXX'O,  Service start
        handle = 'XXXX'O, end handle = 'XXXX'O.

        Description: Verify that the
        Implementation Under Test (IUT) can send Discover all charactieristics
        of a service.
        """

        assert self.connection is not None
        service_uuid = formatUuid(re.findall("'([a0-Z9]*)'O", description)[0])
        self.services = self.gatt.DiscoverServices(connection=self.connection).services
        self.characteristics = getCharacteristicsForServiceUuid(self.services, service_uuid)
        return "OK"

    def MMI_CONFIRM_ALL_CHARACTERISTICS_SERVICE(self, description: str, **kwargs):
        """
        Please confirm IUT received all characteristics of service
        handle='XXXX'O handle='XXXX'O handle='XXXX'O handle='XXXX'O
        handle='XXXX'O handle='XXXX'O handle='XXXX'O handle='XXXX'O
        handle='XXXX'O handle='XXXX'O handle='XXXX'O  in database. Click Yes if
        IUT received it, otherwise click No.

        Description: Verify that the
        Implementation Under Test (IUT) can send Discover all characteristics of
        a service in database.
        """

        assert self.characteristics is not None
        all_matches = list(map(stringCharHandleToInt, re.findall("'([a0-Z9]*)'O", description)))
        assert all(handle in list(map(lambda char: char.handle, self.characteristics))\
                for handle in all_matches)
        return "Yes"

    def MMI_IUT_DISCOVER_SERVICE_UUID_RANGE(self, description: str, **kwargs):
        """
        Please send discover characteristics by UUID. Range start from handle =
        'XXXX'O end handle = 'XXXX'O characteristics UUID = 0xXXXX'O.
        Description: Verify that the Implementation Under Test (IUT) can send
        Discover characteristics by UUID.
        """

        assert self.connection is not None
        handles = re.findall("'([a0-Z9]*)'O", description)
        """
        PTS sends UUIDS description formatted differently in this MMI,
        so we need to check for each known format.
        """
        uuid_match = re.findall("0x([a0-Z9]*)'O", description)
        if len(uuid_match) == 0:
            uuid_match = re.search("UUID = (.*)'O", description)
            uuid = formatUuid(uuid_match[1])
        else:
            uuid = formatUuid(uuid_match[0])
        self.services = self.gatt.DiscoverServices(connection=self.connection).services
        self.characteristics = getCharacteristicsRange(self.services,\
                stringHandleToInt(handles[0]), stringHandleToInt(handles[1]), uuid)
        return "OK"

    def MMI_CONFIRM_CHARACTERISTICS(self, description: str, **kwargs):
        """
        Please confirm IUT received characteristic handle='XXXX'O UUID='XXXX'O
        in database. Click Yes if IUT received it, otherwise click No.
        Description: Verify that the Implementation Under Test (IUT) can send
        Discover primary service by UUID in database.
        """

        assert self.characteristics is not None
        all_matches = re.findall("'([a0-Z9-]*)'O", description)
        for characteristic in self.characteristics:
            if characteristic.handle == stringHandleToInt(all_matches[0])\
                    and characteristic.uuid == formatUuid(all_matches[1]):
                return "Yes"
        raise ValueError

    @assert_description
    def MMI_CONFIRM_NO_CHARACTERISTICSUUID_SMALL(self, **kwargs):
        """
        Please confirm that IUT received NO 128 bit uuid in the small database
        file. Click Yes if NO handle found, otherwise click No.

        Description:
        Verify that the Implementation Under Test (IUT) can discover
        characteristics by UUID in small database.
        """

        assert self.characteristics is not None
        assert len(self.characteristics) == 0
        return "OK"

    def MMI_IUT_DISCOVER_DESCRIPTOR_RANGE(self, description: str, **kwargs):
        """
        Please send discover characteristics descriptor range start from handle
        = 'XXXX'O end handle = 'XXXX'O to the PTS.

        Description: Verify that the
        Implementation Under Test (IUT) can send Discover characteristics
        descriptor.
        """

        assert self.connection is not None
        handles = re.findall("'([a0-Z9]*)'O", description)
        self.services = self.gatt.DiscoverServices(connection=self.connection).services
        self.descriptors = getDescriptorsRange(self.services,\
                stringHandleToInt(handles[0]), stringHandleToInt(handles[1]))
        return "OK"

    def MMI_CONFIRM_CHARACTERISTICS_DESCRIPTORS(self, description: str, **kwargs):
        """
        Please confirm IUT received characteristic descriptors handle='XXXX'O
        UUID=0xXXXX  in database. Click Yes if IUT received it, otherwise click
        No.

        Description: Verify that the Implementation Under Test (IUT) can
        send Discover characteristic descriptors in database.
        """

        assert self.descriptors is not None
        handle = stringHandleToInt(re.findall("'([a0-Z9]*)'O", description)[0])
        uuid = formatUuid(re.search("UUID=0x(.*)  ", description)[1])
        for descriptor in self.descriptors:
            if descriptor.handle == handle and descriptor.uuid == uuid:
                return "Yes"
        raise ValueError

    def MMI_IUT_DISCOVER_ALL_SERVICE_RECORD(self, pts_addr: bytes, description: str, **kwargs):
        """
        Please send Service Discovery to discover all primary Services. Click
        YES if GATT='XXXX'O services are discovered, otherwise click No.
        Description: Verify that the Implementation Under Test (IUT) can
        discover basic rate all primary services.
        """

        uuid = formatSdpUuid(re.findall("'([a0-Z9]*)'O", description)[0])
        self.services = self.gatt.DiscoverServicesSdp(address=pts_addr).service_uuids
        assert uuid in self.services
        return "Yes"

    def MMI_IUT_SEND_READ_CHARACTERISTIC_HANDLE(self, description: str, **kwargs):
        """
        Please send read characteristic handle = 'XXXX'O to the PTS.
        Description: Verify that the Implementation Under Test (IUT) can send
        Read characteristic.
        """

        assert self.connection is not None
        handle = stringHandleToInt(re.findall("'([a0-Z9]*)'O", description)[0])

        def read():
            nonlocal handle
            self.read_response = self.gatt.ReadCharacteristicFromHandle(\
                    connection=self.connection, handle=handle)

        worker = Thread(target=read)
        worker.start()
        worker.join(timeout=30)
        return "OK"

    @assert_description
    def MMI_IUT_READ_TIMEOUT(self, **kwargs):
        """
        Please wait for 30 seconds timeout to abort the procedure.

        Description:
        Verify that the Implementation Under Test (IUT) can handle timeout after
        send Read characteristic without receiving response in 30 seconds.
        """

        return "OK"

    @assert_description
    def MMI_IUT_CONFIRM_READ_INVALID_HANDLE(self, **kwargs):
        """
        Please confirm IUT received Invalid handle error. Click Yes if IUT
        received it, otherwise click No.

        Description: Verify that the
        Implementation Under Test (IUT) indicate Invalid handle error when read
        a characteristic.
        """

        if type(self.read_response) is ReadCharacteristicResponse:
            assert self.read_response.status == INVALID_HANDLE
        elif type(self.read_response) is ReadCharacteristicsFromUuidResponse:
            assert self.read_response.characteristics_read is not None
            assert INVALID_HANDLE in\
                    list(map(lambda characteristic_read: characteristic_read.status,\
                            self.read_response.characteristics_read))
        return "Yes"

    @assert_description
    def MMI_IUT_CONFIRM_READ_NOT_PERMITTED(self, **kwargs):
        """
        Please confirm IUT received read is not permitted error. Click Yes if
        IUT received it, otherwise click No.

        Description: Verify that the
        Implementation Under Test (IUT) indicate read is not permitted error
        when read a characteristic.
        """

        # Android read error doesn't return an error code so we have to also
        # compare to the generic error code here.
        if type(self.read_response) is ReadCharacteristicResponse:
            assert self.read_response.status == READ_NOT_PERMITTED or\
                   self.read_response.status == UNKNOWN_ERROR
        elif type(self.read_response) is ReadCharacteristicsFromUuidResponse:
            assert self.read_response.characteristics_read is not None
            status_list = list(map(lambda characteristic_read: characteristic_read.status,\
                    self.read_response.characteristics_read))
            assert READ_NOT_PERMITTED in status_list or\
                     UNKNOWN_ERROR in status_list
        return "Yes"

    @assert_description
    def MMI_IUT_CONFIRM_READ_AUTHENTICATION(self, **kwargs):
        """
        Please confirm IUT received authentication error. Click Yes if IUT
        received it, otherwise click No.

        Description: Verify that the
        Implementation Under Test (IUT) indicate authentication error when read
        a characteristic.
        """

        if type(self.read_response) is ReadCharacteristicResponse:
            assert self.read_response.status == INSUFFICIENT_AUTHENTICATION
        elif type(self.read_response) is ReadCharacteristicsFromUuidResponse:
            assert self.read_response.characteristics_read is not None
            assert INSUFFICIENT_AUTHENTICATION in\
                    list(map(lambda characteristic_read: characteristic_read.status,\
                            self.read_response.characteristics_read))
        return "Yes"

    def MMI_IUT_SEND_READ_CHARACTERISTIC_UUID(self, description: str, **kwargs):
        """
        Please send read using characteristic UUID = 'XXXX'O handle range =
        'XXXX'O to 'XXXX'O to the PTS.

        Description: Verify that the
        Implementation Under Test (IUT) can send Read characteristic by UUID.
        """

        assert self.connection is not None
        matches = re.findall("'([a0-Z9]*)'O", description)
        self.read_response = self.gatt.ReadCharacteristicsFromUuid(\
                connection=self.connection, uuid=formatUuid(matches[0]),\
                start_handle=stringHandleToInt(matches[1]),\
                end_handle=stringHandleToInt(matches[2]))
        return "OK"

    @assert_description
    def MMI_IUT_CONFIRM_ATTRIBUTE_NOT_FOUND(self, **kwargs):
        """
        Please confirm IUT received attribute not found error. Click Yes if IUT
        received it, otherwise click No.

        Description: Verify that the
        Implementation Under Test (IUT) indicate attribute not found error when
        read a characteristic.
        """

        # Android read error doesn't return an error code so we have to also
        # compare to the generic error code here.
        if type(self.read_response) is ReadCharacteristicResponse:
            assert self.read_response.status == ATTRIBUTE_NOT_FOUND or\
                    self.read_response.status == UNKNOWN_ERROR
        elif type(self.read_response) is ReadCharacteristicsFromUuidResponse:
            assert self.read_response.characteristics_read is not None
            status_list = list(map(lambda characteristic_read: characteristic_read.status,\
                    self.read_response.characteristics_read))
            assert ATTRIBUTE_NOT_FOUND in status_list or\
                    UNKNOWN_ERROR in status_list
        return "Yes"

    def MMI_IUT_SEND_READ_GREATER_OFFSET(self, description: str, **kwargs):
        """
        Please send read to handle = 'XXXX'O and offset greater than 'XXXX'O to
        the PTS.

        Description: Verify that the Implementation Under Test (IUT)
        can send Read with invalid offset.
        """

        # Android handles the read offset internally, so we just do read with handle here.
        # Unfortunately for testing, this will always work.
        assert self.connection is not None
        handle = stringHandleToInt(re.findall("'([a0-Z9]*)'O", description)[0])
        self.read_response = self.gatt.ReadCharacteristicFromHandle(\
                connection=self.connection, handle=handle)
        return "OK"

    @assert_description
    def MMI_IUT_CONFIRM_READ_INVALID_OFFSET(self, **kwargs):
        """
        Please confirm IUT received Invalid offset error. Click Yes if IUT
        received it, otherwise click No.

        Description: Verify that the
        Implementation Under Test (IUT) indicate Invalid offset error when read
        a characteristic.
        """

        # Android handles read offset internally, so we can't read with wrong offset.
        return "Yes"

    @assert_description
    def MMI_IUT_CONFIRM_READ_APPLICATION(self, **kwargs):
        """
        Please confirm IUT received Application error. Click Yes if IUT received
        it, otherwise click No.

        Description: Verify that the Implementation
        Under Test (IUT) indicate Application error when read a characteristic.
        """

        if type(self.read_response) is ReadCharacteristicResponse:
            assert self.read_response.status == APPLICATION_ERROR
        elif type(self.read_response) is ReadCharacteristicsFromUuidResponse:
            assert self.read_response.characteristics_read is not None
            assert APPLICATION_ERROR in\
                    list(map(lambda characteristic_read: characteristic_read.status,\
                            self.read_response.characteristics_read))
        return "Yes"

    def MMI_IUT_CONFIRM_READ_CHARACTERISTIC_VALUE(self, description: str, **kwargs):
        """
        Please confirm IUT received characteristic value='XX'O in random
        selected adopted database. Click Yes if IUT received it, otherwise click
        No.

        Description: Verify that the Implementation Under Test (IUT) can
        send Read characteristic to PTS random select adopted database.
        """

        characteristic_value = bytes.fromhex(re.findall("'([a0-Z9]*)'O", description)[0])
        if type(self.read_response) is ReadCharacteristicResponse:
            assert self.read_response.value is not None
            assert characteristic_value in self.read_response.value.value
        elif type(self.read_response) is ReadCharacteristicsFromUuidResponse:
            assert self.read_response.characteristics_read is not None
            assert characteristic_value in list(map(\
                    lambda characteristic_read: characteristic_read.value.value,\
                    self.read_response.characteristics_read))
        return "Yes"

    def MMI_IUT_READ_BY_TYPE_UUID(self, description: str, **kwargs):
        """
        Please send read by type characteristic UUID = 'XXXX'O to the PTS.
        Description: Verify that the Implementation Under Test (IUT) can send
        Read characteristic.
        """

        assert self.connection is not None
        matches = re.findall("'([a0-Z9]*)'O", description)
        self.read_response = self.gatt.ReadCharacteristicsFromUuid(\
                connection=self.connection, uuid=formatUuid(matches[0]),\
                start_handle=0x0001,\
                end_handle=0xffff)
        return "OK"

    def MMI_IUT_READ_BY_TYPE_UUID_ALT(self, description: str, **kwargs):
        """
        Please send read by type characteristic UUID =
        'XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX'O to the PTS.

        Description:
        Verify that the Implementation Under Test (IUT) can send Read
        characteristic.
        """

        assert self.connection is not None
        uuid = formatUuid(re.findall("'([a0-Z9-]*)'O", description)[0])
        self.read_response = self.gatt.ReadCharacteristicsFromUuid(\
                connection=self.connection, uuid=uuid, start_handle=0x0001, end_handle=0xffff)
        return "OK"

    def MMI_IUT_CONFIRM_READ_HANDLE_VALUE(self, description: str, **kwargs):
        """
        Please confirm IUT Handle='XX'O characteristic
        value='XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX'O in random
        selected adopted database. Click Yes if it matches the IUT, otherwise
        click No.

        Description: Verify that the Implementation Under Test (IUT)
        can send Read long characteristic to PTS random select adopted database.
        """

        bytes_value = bytes.fromhex(re.search("value='(.*)'O", description)[1])
        if type(self.read_response) is ReadCharacteristicResponse:
            assert self.read_response.value is not None
            assert self.read_response.value.value == bytes_value
        elif type(self.read_response) is ReadCharacteristicsFromUuidResponse:
            assert self.read_response.characteristics_read is not None
            assert bytes_value in list(map(\
                    lambda characteristic_read: characteristic_read.value.value,\
                    self.read_response.characteristics_read))
        return "Yes"

    def MMI_IUT_SEND_READ_DESCIPTOR_HANDLE(self, description: str, **kwargs):
        """
        Please send read characteristic descriptor handle = 'XXXX'O to the PTS.
        Description: Verify that the Implementation Under Test (IUT) can send
        Read characteristic descriptor.
        """

        assert self.connection is not None
        handle = stringHandleToInt(re.findall("'([a0-Z9]*)'O", description)[0])
        self.read_response = self.gatt.ReadCharacteristicDescriptorFromHandle(\
                connection=self.connection, handle=handle)
        return "OK"

    def MMI_IUT_CONFIRM_READ_DESCRIPTOR_VALUE(self, description: str, **kwargs):
        """
        Please confirm IUT received Descriptor value='XXXXXXXX'O in random
        selected adopted database. Click Yes if IUT received it, otherwise click
        No.

        Description: Verify that the Implementation Under Test (IUT) can
        send Read Descriptor to PTS random select adopted database.
        """

        assert self.read_response.value is not None
        bytes_value = bytes.fromhex(re.search("value='(.*)'O", description)[1])
        assert self.read_response.value.value == bytes_value
        return "Yes"

    def MMI_IUT_SEND_WRITE_REQUEST(self, description: str, **kwargs):
        """
        Please send write request with characteristic handle = 'XXXX'O with <=
        'X' byte of any octet value to the PTS.

        Description: Verify that the
        Implementation Under Test (IUT) can send write request.
        """

        assert self.connection is not None
        matches = re.findall("'([a0-Z9]*)'O with <= '([a0-Z9]*)'", description)
        handle = stringHandleToInt(matches[0][0])
        data = bytes([1]) * int(matches[0][1])

        def write():
            nonlocal handle
            nonlocal data
            self.write_response = self.gatt.WriteAttFromHandle(connection=self.connection,\
                handle=handle, value=data)

        worker = Thread(target=write)
        worker.start()
        worker.join(timeout=30)
        return "OK"

    @assert_description
    def MMI_IUT_WRITE_TIMEOUT(self, **kwargs):
        """
        Please wait for 30 second timeout to abort the procedure.

        Description:
        Verify that the Implementation Under Test (IUT) can handle timeout after
        send Write characteristic without receiving response in 30 seconds.
        """

        return "OK"

    @assert_description
    def MMI_IUT_CONFIRM_WRITE_INVALID_HANDLE(self, **kwargs):
        """
        Please confirm IUT received Invalid handle error. Click Yes if IUT
        received it, otherwise click No.

        Description: Verify that the
        Implementation Under Test (IUT) indicate Invalid handle error when write
        a characteristic.
        """

        assert self.write_response is not None
        assert self.write_response.status == INVALID_HANDLE
        return "Yes"

    @assert_description
    def MMI_IUT_CONFIRM_WRITE_NOT_PERMITTED(self, **kwargs):
        """
        Please confirm IUT received write is not permitted error. Click Yes if
        IUT received it, otherwise click No.

        Description: Verify that the
        Implementation Under Test (IUT) indicate write is not permitted error
        when write a characteristic.
        """

        assert self.write_response is not None
        assert self.write_response.status == WRITE_NOT_PERMITTED
        return "Yes"

    def MMI_IUT_SEND_PREPARE_WRITE(self, description: str, **kwargs):
        """
        Please send prepare write request with handle = 'XXXX'O <= 'XX' byte of
        any octet value to the PTS.

        Description: Verify that the Implementation
        Under Test (IUT) can send prepare write request.
        """

        assert self.connection is not None
        matches = re.findall("'([a0-Z9]*)'O <= '([a0-Z9]*)'", description)
        handle = stringHandleToInt(matches[0][0])
        data = bytes([1]) * int(matches[0][1])
        self.write_response = self.gatt.WriteAttFromHandle(connection=self.connection,\
                handle=handle, value=data)
        return "OK"

    def _mmi_150(self, description: str, **kwargs):
        """
        Please send an ATT_Write_Request to Client Support Features handle =
        'XXXX'O to enable Multiple Handle Value Notifications.

        Discover all
        characteristics if needed.
        """

        return self.MMI_IUT_WRITE_SUPPORT_FEATURE_MULTIPLE_HANDLE_VALUE(description, **kwargs)

    def MMI_IUT_WRITE_SUPPORT_FEATURE_MULTIPLE_HANDLE_VALUE(self, description: str, **kwargs):
        """
        Please send an ATT_Write_Request to Client Support Features handle =
        'XXXX'O to enable Multiple Handle Value Notifications.

        Discover all
        characteristics if needed.
        """

        assert self.connection is not None
        handle = stringHandleToInt(re.findall("'([a0-Z9]*)'O", description)[0])
        data = bytes([4])  # Multiple Handle Value Notifications
        self.write_response = self.gatt.WriteAttFromHandle(connection=self.connection,\
                handle=handle, value=data)
        return "OK"

    def MMI_IUT_SEND_PREPARE_WRITE_GREATER_OFFSET(self, description: str, **kwargs):
        """
        Please send prepare write request with handle = 'XXXX'O and offset
        greater than 'XX' byte to the PTS.

        Description: Verify that the
        Implementation Under Test (IUT) can send prepare write request.
        """

        assert self.connection is not None
        matches = re.findall("'([a0-Z9]*)'O and offset greater than '([a0-Z9]*)'", description)
        handle = stringHandleToInt(matches[0][0])
        # Android APIs does not permit offset write, however we can test this by writing a value
        # longer than the characteristic's value size. As sometimes this MMI description will ask
        # for values greater than 512 bytes, we have to check for this or Android Bluetooth will
        # crash. Setting written_over_length to True in order to perform the check in next MMI.
        offset = int(matches[0][1]) + 1
        if offset <= 512:
            data = bytes([1]) * offset
            self.written_over_length = True
        else:
            data = bytes([1]) * 512
        self.write_response = self.gatt.WriteAttFromHandle(connection=self.connection,\
                handle=handle, value=data)
        return "OK"

    @assert_description
    def MMI_IUT_SEND_EXECUTE_WRITE_REQUEST(self, **kwargs):
        """
        Please send execute write request to the PTS.

        Description: Verify that
        the Implementation Under Test (IUT) can send execute write request.
        """

        # PTS Sends this MMI after the MMI_IUT_SEND_PREPARE_WRITE_GREATER_OFFSET,
        # nothing to do as we already wrote.
        return "OK"

    @assert_description
    def MMI_IUT_CONFIRM_WRITE_INVALID_OFFSET(self, **kwargs):
        """
        Please confirm IUT received Invalid offset error. Click Yes if IUT
        received it, otherwise click No.

        Description: Verify that the
        Implementation Under Test (IUT) indicate Invalid offset error when write
        a characteristic.
        """

        assert self.write_response is not None
        # See MMI_IUT_SEND_PREPARE_WRITE_GREATER_OFFSET
        if self.written_over_length == True:
            assert self.write_response.status == INVALID_ATTRIBUTE_LENGTH
        return "OK"

    def MMI_IUT_SEND_WRITE_REQUEST_GREATER(self, description: str, **kwargs):
        """
        Please send write request with characteristic handle = 'XXXX'O with
        greater than 'X' byte of any octet value to the PTS.

        Description:
        Verify that the Implementation Under Test (IUT) can send write request.
        """

        assert self.connection is not None
        matches = re.findall("'([a0-Z9]*)'O with greater than '([a0-Z9]*)'", description)
        handle = stringHandleToInt(matches[0][0])
        data = bytes([1]) * (int(matches[0][1]) + 1)
        self.write_response = self.gatt.WriteAttFromHandle(connection=self.connection,\
                handle=handle, value=data)
        return "OK"

    @assert_description
    def MMI_IUT_CONFIRM_WRITE_INVALID_LENGTH(self, **kwargs):
        """
        Please confirm IUT received Invalid attribute value length error. Click
        Yes if IUT received it, otherwise click No.

        Description: Verify that
        the Implementation Under Test (IUT) indicate Invalid attribute value
        length error when write a characteristic.
        """

        assert self.write_response is not None
        assert self.write_response.status == INVALID_ATTRIBUTE_LENGTH
        return "OK"

    def MMI_IUT_SEND_PREPARE_WRITE_REQUEST_GREATER(self, description: str, **kwargs):
        """
        Please send prepare write request with handle = 'XXXX'O with greater
        than 'XX' byte of any octet value to the PTS.

        Description: Verify that
        the Implementation Under Test (IUT) can send prepare write request.
        """

        assert self.connection is not None
        matches = re.findall("'([a0-Z9]*)'O with greater than '([a0-Z9]*)'", description)
        handle = stringHandleToInt(matches[0][0])
        data = bytes([1]) * (int(matches[0][1]) + 1)
        self.write_response = self.gatt.WriteAttFromHandle(connection=self.connection,\
                handle=handle, value=data)
        return "OK"

    def MMI_IUT_SEND_WRITE_COMMAND(self, description: str, **kwargs):
        """
        Please send write command with handle = 'XXXX'O with <= 'X' bytes of any
        octet value to the PTS.

        Description: Verify that the Implementation
        Under Test (IUT) can send write request.
        """

        assert self.connection is not None
        matches = re.findall("'([a0-Z9]*)'O with <= '([a0-Z9]*)'", description)
        handle = stringHandleToInt(matches[0][0])
        data = bytes([1]) * int(matches[0][1])
        self.write_response = self.gatt.WriteAttFromHandle(connection=self.connection,\
                handle=handle, value=data)
        return "OK"

    def MMI_MAKE_IUT_CONNECTABLE(self, **kwargs):
        """
        Please prepare IUT into a connectable mode.

        Description: Verify that
        the Implementation Under Test (IUT) can accept GATT connect request from
        PTS.
        """
        self.advertise = self.host.Advertise(
            legacy=True,
            connectable=True,
            own_address_type=PUBLIC,
        )
        self.pairing_events = self.security.OnPairing()
        time.sleep(1)
        self.gatt.RegisterService(service=GattServiceParams(
            uuid=BASE_READ_WRITE_SERVICE_UUID,
            characteristics=[
                GattCharacteristicParams(
                    uuid=BASE_READ_CHARACTERISTIC_UUID,
                    properties=PROPERTY_READ,
                    permissions=PERMISSION_READ,
                    descriptors=[
                        GattDescriptorParams(
                            uuid=BASE_READ_DESCRIPTOR_UUID,
                            properties=PROPERTY_READ,
                            permissions=PERMISSION_READ,
                        ),
                    ],
                ),
                GattCharacteristicParams(
                    uuid=BASE_WRITE_CHARACTERISTIC_UUID,
                    properties=PROPERTY_WRITE,
                    permissions=PERMISSION_WRITE,
                ),
                GattCharacteristicParams(
                    uuid=BASE_READ_WRITE_ENCRYPTED_CHARACTERISTIC_UUID,
                    properties=PROPERTY_READ | PROPERTY_WRITE,
                    permissions=PERMISSION_READ_ENCRYPTED | PERMISSION_WRITE_ENCRYPTED,
                ),
                GattCharacteristicParams(
                    uuid=BASE_READ_WRITE_ENCRYPTED_MITM_CHARACTERISTIC_UUID,
                    properties=PROPERTY_READ | PROPERTY_WRITE,
                    permissions=PERMISSION_READ_ENCRYPTED_MITM | PERMISSION_WRITE_ENCRYPTED_MITM,
                ),
            ],
        ))

        return "OK"

    def MMI_CONFIRM_IUT_PRIMARY_SERVICE_128(self, **kwargs):
        """
        Please confirm IUT have following primary services UUID= 'XXXX'O
        Service start handle = 'XXXX'O, end handle = 'XXXX'O. Click Yes if IUT
        have it, otherwise click No.

        Description: Verify that the
        Implementation Under Test (IUT) can respond Discover all primary
        services by UUID.
        """

        return "Yes"

    def MMI_CONFIRM_CHARACTERISTICS_SERVICE(self, **kwargs):
        """
        Please confirm IUT have following characteristics in services UUID=
        'XXXX'O handle='XXXX'O handle='XXXX'O handle='XXXX'O handle='XXXX'O .
        Click Yes if IUT have it, otherwise click No.

        Description: Verify that
        the Implementation Under Test (IUT) can respond Discover all
        characteristics of a service.
        """

        return "Yes"

    def MMI_CONFIRM_SERVICE_UUID(self, **kwargs):
        """
        Please confirm the following handles for GATT Service UUID = 0xXXXX.
        Start Handle = 0xXXXX
        End Handle = 0xXXXX
        """

        return "Yes"

    def MMI_IUT_ENTER_HANDLE_INVALID(self, **kwargs):
        """
        Please input a handle(0x)(Range 0x0001-0xFFFF) that is known to be
        invalid.

        Description: Verify that the Implementation Under Test (IUT)
        can issue an Invalid Handle Response.
        """

        return "FFFF"

    def MMI_IUT_NO_SECURITY(self, **kwargs):
        """
        Please make sure IUT does not initiate security procedure.

        Description:
        PTS will delete bond information. Test case requires that no
        authentication or authorization procedure has been performed between the
        IUT and the test system.
        """

        return "OK"

    def MMI_IUT_ENTER_UUID_READ_NOT_PERMITTED(self, **kwargs):
        """
        Enter UUID(0x) response with Read Not Permitted.

        Description: Verify
        that the Implementation Under Test (IUT) can respond Read Not Permitted.
        """

        self.last_added_service = self.gatt.RegisterService(service=GattServiceParams(
            uuid=CUSTOM_SERVICE_UUID,
            characteristics=[
                GattCharacteristicParams(
                    uuid=CUSTOM_CHARACTERISTIC_UUID,
                    properties=PROPERTY_READ,
                    permissions=PERMISSION_NONE,
                ),
            ],
        ))
        return CUSTOM_CHARACTERISTIC_UUID[4:8].upper()

    def MMI_IUT_ENTER_HANDLE_READ_NOT_PERMITTED(self, **kwargs):
        """
        Please input a handle(0x)(Range 0x0001-0xFFFF) that doesn't permit
        reading (i.e. Read Not Permitted)

        Description: Verify that the
        Implementation Under Test (IUT) can issue a Read Not Permitted Response.
        """

        return "{:04x}".format(self.last_added_service.service.characteristics[0].handle)

    def MMI_IUT_ENTER_UUID_ATTRIBUTE_NOT_FOUND(self, **kwargs):
        """
        Enter UUID(0x) response with Attribute Not Found.

        Description: Verify
        that the Implementation Under Test (IUT) can respond Attribute Not
        Found.
        """

        return CUSTOM_CHARACTERISTIC_UUID[4:8].upper()

    def MMI_IUT_ENTER_UUID_INSUFFICIENT_AUTHENTICATION(self, **kwargs):
        """
        Enter UUID(0x) response with Insufficient Authentication.

        Description:
        Verify that the Implementation Under Test (IUT) can respond Insufficient
        Authentication.
        """

        self.last_added_service = self.gatt.RegisterService(service=GattServiceParams(
            uuid=CUSTOM_SERVICE_UUID,
            characteristics=[
                GattCharacteristicParams(
                    uuid=CUSTOM_CHARACTERISTIC_UUID,
                    properties=PROPERTY_READ,
                    permissions=PERMISSION_READ_ENCRYPTED,
                ),
            ],
        ))
        return CUSTOM_CHARACTERISTIC_UUID[4:8].upper()

    def MMI_IUT_ENTER_HANDLE_INSUFFICIENT_AUTHENTICATION(self, **kwargs):
        """
        Enter Handle(0x)(Range 0x0001-0xFFFF) response with Insufficient
        Authentication.

        Description: Verify that the Implementation Under Test
        (IUT) can respond Insufficient Authentication.
        """

        return "{:04x}".format(self.last_added_service.service.characteristics[0].handle)

    def MMI_IUT_ENTER_HANDLE_READ_NOT_PERMITTED(self, **kwargs):
        """
        Please input a handle(0x)(Range 0x0001-0xFFFF) that doesn't permit
        reading (i.e. Read Not Permitted)

        Description: Verify that the
        Implementation Under Test (IUT) can issue a Read Not Permitted Response.
        """

        self.last_added_service = self.gatt.RegisterService(service=GattServiceParams(
            uuid=CUSTOM_SERVICE_UUID,
            characteristics=[
                GattCharacteristicParams(
                    uuid=CUSTOM_CHARACTERISTIC_UUID,
                    properties=PROPERTY_READ,
                    permissions=PERMISSION_NONE,
                ),
            ],
        ))
        return "{:04x}".format(self.last_added_service.service.characteristics[0].handle)

    def MMI_IUT_CONFIRM_READ_MULTIPLE_HANDLE_VALUES(self, **kwargs):
        """
        Please confirm IUT Handle pair = 'XXXX'O 'XXXX'O
        value='XXXXXXXXXXXXXXXXXXXXXXXXXXX in random selected
        adopted database. Click Yes if it matches the IUT, otherwise click No.
        Description: Verify that the Implementation Under Test (IUT) can send
        Read multiple characteristics.
        """

        return "OK"

    def MMI_IUT_ENTER_HANDLE_WRITE_NOT_PERMITTED(self, **kwargs):
        """
        Enter Handle(0x) response with Write Not Permitted.

        Description:
        Verify that the Implementation Under Test (IUT) can respond Write Not
        Permitted.
        """

        self.last_added_service = self.gatt.RegisterService(service=GattServiceParams(
            uuid=CUSTOM_SERVICE_UUID,
            characteristics=[
                GattCharacteristicParams(
                    uuid=CUSTOM_CHARACTERISTIC_UUID,
                    properties=PROPERTY_WRITE,
                    permissions=PERMISSION_NONE,
                ),
            ],
        ))
        return "{:04x}".format(self.last_added_service.service.characteristics[0].handle)

    def MMI_IUT_SEND_INDICATION(self, description: str, **kwargs):
        """
        Please write to client characteristic configuration handle = 'XXXX'O to
        enable indication to the PTS. Discover all characteristics if needed.
        Description: Verify that the Implementation Under Test (IUT) can receive
        indication sent from PTS.
        """
        assert self.connection is not None
        handle = stringHandleToInt(re.findall("'([a0-Z9]*)'O", description)[0])
        self.handles.append(handle)
        self.gatt.SetCharacteristicNotificationFromHandle(connection=self.connection,\
                handle=handle, enable_value=ENABLE_INDICATION_VALUE)

        return "OK"

    @assert_description
    def MMI_IUT_RECEIVE_INDICATION(self, **kwargs):
        """
        Please confirm IUT received indication from PTS. Click YES if received,
        otherwise NO.

        Description: Verify that the Implementation Under Test
        (IUT) can receive indication send from PTS.
        """
        for handle in self.handles:
            self.characteristic_notification_received = self.gatt.WaitCharacteristicNotification(
                connection=self.connection, handle=handle).characteristic_notification_received
            assert self.characteristic_notification_received

        return "OK"

    def MMI_IUT_SEND_NOTIFICATION(self, description: str, **kwargs):
        """
        Please write to client characteristic configuration handle = 'XXXX'O to
        enable notification to the PTS. Discover all characteristics if needed.
        Description: Verify that the Implementation Under Test (IUT) can receive
        notification sent from PTS.
        """
        assert self.connection is not None
        handle = stringHandleToInt(re.findall("'([a0-Z9]*)'O", description)[0])
        self.handles.append(handle)
        self.gatt.SetCharacteristicNotificationFromHandle(connection=self.connection,\
                handle=handle, enable_value=ENABLE_NOTIFICATION_VALUE)

        return "OK"

    @assert_description
    def MMI_IUT_RECEIVE_NOTIFICATION(self, **kwargs):
        """
        Please confirm IUT received notification from PTS. Click YES if
        received, otherwise NO.

        Description: Verify that the Implementation
        Under Test (IUT) can receive notification send from PTS.
        """
        for handle in self.handles:
            self.characteristic_notification_received = self.gatt.WaitCharacteristicNotification(
                connection=self.connection, handle=handle).characteristic_notification_received
            assert self.characteristic_notification_received

        return "OK"

    @assert_description
    def MMI_IUT_DELETE_SECUIRTY_KEY(self, pts_addr: bytes, **kwargs):
        """
        Please delete security key before connecting to PTS if IUT was bonded
        previously.
        """
        self.security_storage.DeleteBond(public=pts_addr)

        return "OK"

    def MMI_IUT_WRITE_SUPPORT_FEATURE(self, description: str, **kwargs):
        """
        Please write to client support feature handle = 'XXXX'O to enable Robust
        Caching. Discover all characteristics if needed.
        """
        assert self.connection is not None
        handle = stringHandleToInt(re.findall("'([a0-Z9]*)'O", description)[0])
        data = bytes([1])
        self.write_response = self.gatt.WriteAttFromHandle(connection=self.connection,\
                handle=handle, value=data)

        return "OK"

    @match_description
    def _mmi_2004(self, pts_addr: bytes, passkey: str, **kwargs):
        """
        Please confirm that 6 digit number is matched with (?P<passkey>[0-9]+).
        """

        for event in self.pairing_events:
            if event.address == pts_addr and event.numeric_comparison == int(passkey):
                self.pairing_events.send(PairingEventAnswer(
                    event=event,
                    confirm=True,
                ))
                return "OK"

        assert False

    @match_description
    def MMI_CONFIRM_IUT_PRIMARY_SERVICE(self, services: str, **kwargs):
        """
        Please confirm IUT have following primary services UUID= (?P<services>.*?).
        Click Yes if IUT have it, otherwise click No.
        Description: Verify that the Implementation Under Test \(IUT\) can respond
        Discover all primary services by UUID.
        """

        return "OK"

    @match_description
    def MMI_CONFIRM_PASSKEY(self, pts_addr: bytes, passkey: str, **kwargs):
        """
        The secureId is (?P<passkey>[0-9]+).
        """

        for event in self.pairing_events:
            if event.address == pts_addr and event.passkey_entry_request:
                self.pairing_events.send(PairingEventAnswer(event=event, passkey=int(passkey)))
                return "OK"

        assert False


def stringHandleToInt(handle: str):
    return int(handle, 16)


# Discovered characteristics handles are 1 more than PTS handles in one test.
def stringCharHandleToInt(handle: str):
    return (int(handle, 16) + 1)


def formatUuid(uuid: str):
    """
    Formats PTS described UUIDs to be of the right format.
    Right format is: 'XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX'
    PTS described format can be:
    - 'XXXX'
    - 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX'
    - 'XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX'
    """
    uuid_len = len(uuid)
    if uuid_len == 4:
        return BASE_UUID.replace(BASE_UUID[4:8], uuid.upper())
    elif uuid_len == 32 or uuid_len == 39:
        uuidCharList = list(uuid.replace('-', '').upper())
        uuidCharList.insert(20, '-')
        uuidCharList.insert(16, '-')
        uuidCharList.insert(12, '-')
        uuidCharList.insert(8, '-')
        return ''.join(uuidCharList)
    else:
        return uuid.upper()


# PTS asks wrong uuid for services discovered by SDP in some tests
def formatSdpUuid(uuid: str):
    if uuid[3] == '1':
        uuid = uuid[:3] + 'f'
    return BASE_UUID.replace(BASE_UUID[4:8], uuid.upper())


def compareIncludedServices(service, service_handle, included_handle, included_uuid):
    """
    Compares included services with given values.
    The service_handle passed by the PTS is
    [primary service handle] + [included service number].
    """
    included_service_count = 1
    for included_service in service.included_services:
        if service.handle == (service_handle - included_service_count)\
                and included_service.handle == included_handle\
                and included_service.uuid == included_uuid:
            return True
        included_service_count += 1
    return False


def getCharacteristicsForServiceUuid(services, uuid):
    """
    Return an array of characteristics for matching service uuid.
    """
    for service in services:
        if service.uuid == uuid:
            return service.characteristics
    return []


def getCharacteristicsRange(services, start_handle, end_handle, uuid):
    """
    Return an array of characteristics of which handles are
    between start_handle and end_handle and uuid matches.
    """
    characteristics_list = []
    for service in services:
        for characteristic in service.characteristics:
            if characteristic.handle >= start_handle\
                    and characteristic.handle <= end_handle\
                    and characteristic.uuid == uuid:
                characteristics_list.append(characteristic)
    return characteristics_list


def getDescriptorsRange(services, start_handle, end_handle):
    """
    Return an array of descriptors of which handles are
    between start_handle and end_handle.
    """
    descriptors_list = []
    for service in services:
        for characteristic in service.characteristics:
            for descriptor in characteristic.descriptors:
                if descriptor.handle >= start_handle and descriptor.handle <= end_handle:
                    descriptors_list.append(descriptor)
    return descriptors_list
