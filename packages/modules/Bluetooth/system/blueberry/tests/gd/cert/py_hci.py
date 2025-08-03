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
from datetime import timedelta

from google.protobuf import empty_pb2 as empty_proto
from blueberry.tests.gd.cert.event_stream import EventStream
from blueberry.tests.gd.cert.event_stream import FilteringEventStream
from blueberry.tests.gd.cert.event_stream import IEventStream
from blueberry.tests.gd.cert.closable import Closable
from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.captures import HciCaptures
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.facade.hci import hci_facade_pb2 as hci_facade
from blueberry.facade import common_pb2 as common
from blueberry.tests.gd.cert.matchers import HciMatchers
import hci_packets as hci
import blueberry.utils.bluetooth as bluetooth


class PyHciAclConnection(IEventStream):

    def __init__(self, handle, acl_stream, device):
        self.handle = int(handle)
        self.device = device
        # todo, handle we got is 0, so doesn't match - fix before enabling filtering
        self.our_acl_stream = FilteringEventStream(acl_stream, None)

    def send(self, pb_flag, b_flag, data: bytes):
        assert isinstance(data, bytes)
        acl = hci.Acl(handle=self.handle, packet_boundary_flag=pb_flag, broadcast_flag=b_flag, payload=data)
        self.device.hci.SendAcl(common.Data(payload=acl.serialize()))

    def send_first(self, data: bytes):
        self.send(hci.PacketBoundaryFlag.FIRST_AUTOMATICALLY_FLUSHABLE, hci.BroadcastFlag.POINT_TO_POINT, data)

    def send_continuing(self, data):
        self.send(hci.PacketBoundaryFlag.CONTINUING_FRAGMENT, hci.BroadcastFlag.POINT_TO_POINT, data)

    def get_event_queue(self):
        return self.our_acl_stream.get_event_queue()


class PyHciLeAclConnection(IEventStream):

    def __init__(self, handle, acl_stream, device, peer, peer_type, peer_resolvable, local_resolvable):
        self.handle = int(handle)
        self.device = device
        self.peer = peer
        self.peer_type = peer_type
        self.peer_resolvable = peer_resolvable
        self.local_resolvable = local_resolvable
        # todo, handle we got is 0, so doesn't match - fix before enabling filtering
        self.our_acl_stream = FilteringEventStream(acl_stream, None)

    def send(self, pb_flag, b_flag, data: bytes):
        assert isinstance(data, bytes)
        acl = hci.Acl(handle=self.handle, packet_boundary_flag=pb_flag, broadcast_flag=b_flag, payload=data)
        self.device.hci.SendAcl(common.Data(payload=acl.serialize()))

    def send_first(self, data: bytes):
        self.send(hci.PacketBoundaryFlag.FIRST_AUTOMATICALLY_FLUSHABLE, hci.BroadcastFlag.POINT_TO_POINT, data)

    def send_continuing(self, data: bytes):
        self.send(hci.PacketBoundaryFlag.CONTINUING_FRAGMENT, hci.BroadcastFlag.POINT_TO_POINT, data)

    def get_event_queue(self):
        return self.our_acl_stream.get_event_queue()

    def local_resolvable_address(self):
        return self.local_resolvable

    def peer_resolvable_address(self):
        return self.peer_resolvable

    def peer_address(self):
        return self.peer


class PyHciAdvertisement(object):

    def __init__(self, handle, py_hci):
        self.handle = handle
        self.py_hci = py_hci

    def set_data(self, complete_name):
        self.py_hci.send_command(
            hci.LeSetExtendedAdvertisingData(
                advertising_handle=self.handle,
                operation=hci.Operation.COMPLETE_ADVERTISEMENT,
                fragment_preference=hci.FragmentPreference.CONTROLLER_SHOULD_NOT,
                advertising_data=[hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME,
                                              data=list(complete_name))]))

    def set_scan_response(self, shortened_name):
        self.py_hci.send_command(
            hci.LeSetExtendedScanResponseData(advertising_handle=self.handle,
                                              operation=hci.Operation.COMPLETE_ADVERTISEMENT,
                                              fragment_preference=hci.FragmentPreference.CONTROLLER_SHOULD_NOT,
                                              scan_response_data=[
                                                  hci.GapData(data_type=hci.GapDataType.SHORTENED_LOCAL_NAME,
                                                              data=list(shortened_name))
                                              ]))

    def start(self):
        self.py_hci.send_command(
            hci.LeSetExtendedAdvertisingEnable(enable=hci.Enable.ENABLED,
                                               enabled_sets=[
                                                   hci.EnabledSet(advertising_handle=self.handle,
                                                                  duration=0,
                                                                  max_extended_advertising_events=0)
                                               ]))
        assertThat(self.py_hci.get_event_stream()).emits(
            HciMatchers.CommandComplete(hci.OpCode.LE_SET_EXTENDED_ADVERTISING_ENABLE))


class PyHci(Closable):

    event_stream = None
    le_event_stream = None
    acl_stream = None

    def __init__(self, device, acl_streaming=False):
        """
            If you are planning on personally using the ACL data stream
            coming from HCI, specify acl_streaming=True. You probably only
            want this if you are testing HCI itself.
        """
        self.device = device
        self.event_stream = EventStream(self.device.hci.StreamEvents(empty_proto.Empty()))
        self.le_event_stream = EventStream(self.device.hci.StreamLeSubevents(empty_proto.Empty()))
        if acl_streaming:
            self.register_for_events(hci.EventCode.ROLE_CHANGE, hci.EventCode.CONNECTION_REQUEST,
                                     hci.EventCode.CONNECTION_COMPLETE, hci.EventCode.CONNECTION_PACKET_TYPE_CHANGED)
            self.register_for_le_events(hci.SubeventCode.ENHANCED_CONNECTION_COMPLETE)
            self.acl_stream = EventStream(self.device.hci.StreamAcl(empty_proto.Empty()))

    def close(self):
        safeClose(self.event_stream)
        safeClose(self.le_event_stream)
        safeClose(self.acl_stream)

    def get_event_stream(self):
        return self.event_stream

    def get_le_event_stream(self):
        return self.le_event_stream

    def get_raw_acl_stream(self):
        if self.acl_stream is None:
            raise Exception("Please construct '%s' with acl_streaming=True!" % self.__class__.__name__)
        return self.acl_stream

    def register_for_events(self, *event_codes):
        for event_code in event_codes:
            self.device.hci.RequestEvent(hci_facade.EventRequest(code=int(event_code)))

    def register_for_le_events(self, *event_codes):
        for event_code in event_codes:
            self.device.hci.RequestLeSubevent(hci_facade.EventRequest(code=int(event_code)))

    def send_command(self, command: hci.Packet):
        self.device.hci.SendCommand(common.Data(payload=command.serialize()))

    def enable_inquiry_and_page_scan(self):
        self.send_command(hci.WriteScanEnable(scan_enable=hci.ScanEnable.INQUIRY_AND_PAGE_SCAN))

    def read_own_address(self) -> bluetooth.Address:
        self.send_command(hci.ReadBdAddr())
        read_bd_addr = HciCaptures.ReadBdAddrCompleteCapture()
        assertThat(self.event_stream).emits(read_bd_addr)
        return read_bd_addr.get().bd_addr

    def initiate_connection(self, remote_addr):
        self.send_command(
            hci.CreateConnection(bd_addr=bluetooth.Address(remote_addr),
                                 packet_type=0xcc18,
                                 page_scan_repetition_mode=hci.PageScanRepetitionMode.R1,
                                 clock_offset=0x0,
                                 clock_offset_valid=hci.ClockOffsetValid.INVALID,
                                 allow_role_switch=hci.CreateConnectionRoleSwitch.ALLOW_ROLE_SWITCH))

    def accept_connection(self):
        connection_request = HciCaptures.ConnectionRequestCapture()
        assertThat(self.event_stream).emits(connection_request)

        self.send_command(
            hci.AcceptConnectionRequest(bd_addr=bluetooth.Address(connection_request.get().bd_addr),
                                        role=hci.AcceptConnectionRequestRole.REMAIN_PERIPHERAL))
        return self.complete_connection()

    def complete_connection(self):
        connection_complete = HciCaptures.ConnectionCompleteCapture()
        assertThat(self.event_stream).emits(connection_complete)

        handle = connection_complete.get().connection_handle
        if self.acl_stream is None:
            raise Exception("Please construct '%s' with acl_streaming=True!" % self.__class__.__name__)
        return PyHciAclConnection(handle, self.acl_stream, self.device)

    def set_random_le_address(self, addr):
        self.send_command(hci.LeSetRandomAddress(random_address=bluetooth.Address(addr)))
        assertThat(self.event_stream).emits(HciMatchers.CommandComplete(hci.OpCode.LE_SET_RANDOM_ADDRESS))

    def initiate_le_connection(self, remote_addr):
        self.send_command(
            hci.LeExtendedCreateConnection(initiator_filter_policy=hci.InitiatorFilterPolicy.USE_PEER_ADDRESS,
                                           own_address_type=hci.OwnAddressType.RANDOM_DEVICE_ADDRESS,
                                           peer_address_type=hci.AddressType.RANDOM_DEVICE_ADDRESS,
                                           peer_address=bluetooth.Address(remote_addr),
                                           initiating_phys=1,
                                           phy_scan_parameters=[
                                               hci.LeCreateConnPhyScanParameters(scan_interval=0x60,
                                                                                 scan_window=0x30,
                                                                                 conn_interval_min=0x18,
                                                                                 conn_interval_max=0x28,
                                                                                 conn_latency=0,
                                                                                 supervision_timeout=0x1f4,
                                                                                 min_ce_length=0,
                                                                                 max_ce_length=0)
                                           ]))
        assertThat(self.event_stream).emits(HciMatchers.CommandStatus(hci.OpCode.LE_EXTENDED_CREATE_CONNECTION))

    def incoming_le_connection(self):
        connection_complete = HciCaptures.LeConnectionCompleteCapture()
        assertThat(self.le_event_stream).emits(connection_complete)

        handle = connection_complete.get().connection_handle
        peer = connection_complete.get().peer_address
        peer_type = connection_complete.get().peer_address_type
        local_resolvable = connection_complete.get().local_resolvable_private_address
        peer_resolvable = connection_complete.get().peer_resolvable_private_address
        if self.acl_stream is None:
            raise Exception("Please construct '%s' with acl_streaming=True!" % self.__class__.__name__)
        return PyHciLeAclConnection(handle, self.acl_stream, self.device, repr(peer), peer_type, repr(peer_resolvable),
                                    repr(local_resolvable))

    def incoming_le_connection_fails(self):
        connection_complete = HciCaptures.LeConnectionCompleteCapture()
        assertThat(self.le_event_stream).emitsNone(connection_complete, timeout=timedelta(seconds=5))

    def add_device_to_resolving_list(self, peer_address_type, peer_address, peer_irk, local_irk):
        self.send_command(
            hci.LeAddDeviceToResolvingList(peer_identity_address_type=peer_address_type,
                                           peer_identity_address=bluetooth.Address(peer_address),
                                           peer_irk=peer_irk,
                                           local_irk=local_irk))

    def create_advertisement(self,
                             handle,
                             own_address: str,
                             properties=hci.LegacyAdvertisingEventProperties.ADV_IND,
                             min_interval=400,
                             max_interval=450,
                             channel_map=7,
                             own_address_type=hci.OwnAddressType.RANDOM_DEVICE_ADDRESS,
                             peer_address_type=hci.PeerAddressType.PUBLIC_DEVICE_OR_IDENTITY_ADDRESS,
                             peer_address='00:00:00:00:00:00',
                             filter_policy=hci.AdvertisingFilterPolicy.ALL_DEVICES,
                             tx_power=0xF8,
                             sid=1,
                             scan_request_notification=hci.Enable.DISABLED):

        self.send_command(
            hci.LeSetExtendedAdvertisingParametersLegacy(advertising_handle=handle,
                                                         legacy_advertising_event_properties=properties,
                                                         primary_advertising_interval_min=min_interval,
                                                         primary_advertising_interval_max=max_interval,
                                                         primary_advertising_channel_map=channel_map,
                                                         own_address_type=own_address_type,
                                                         peer_address_type=peer_address_type,
                                                         peer_address=bluetooth.Address(peer_address),
                                                         advertising_filter_policy=filter_policy,
                                                         advertising_tx_power=tx_power,
                                                         advertising_sid=sid,
                                                         scan_request_notification_enable=scan_request_notification))

        self.send_command(
            hci.LeSetAdvertisingSetRandomAddress(advertising_handle=handle,
                                                 random_address=bluetooth.Address(own_address)))

        return PyHciAdvertisement(handle, self)
