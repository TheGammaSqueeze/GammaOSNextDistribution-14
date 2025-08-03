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

from google.protobuf import empty_pb2 as empty_proto
from blueberry.tests.gd.cert.event_stream import EventStream
from blueberry.tests.gd.cert.event_stream import FilteringEventStream
from blueberry.tests.gd.cert.event_stream import IEventStream
from blueberry.tests.gd.cert.closable import Closable
from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.captures import HciCaptures
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert.matchers import HciMatchers
from blueberry.facade import common_pb2 as common
import hci_packets as hci
from blueberry.utils import bluetooth


class PyHalAclConnection(IEventStream):

    def __init__(self, handle, acl_stream, device):
        self.handle = int(handle)
        self.device = device
        self.our_acl_stream = FilteringEventStream(acl_stream, None)

    def send(self, pb_flag, b_flag, data: bytes):
        assert isinstance(data, bytes)
        acl = hci.Acl(handle=self.handle, packet_boundary_flag=pb_flag, broadcast_flag=b_flag, payload=data)
        self.device.hal.SendAcl(common.Data(payload=acl.serialize()))

    def send_first(self, data: bytes):
        assert isinstance(data, bytes)
        self.send(hci.PacketBoundaryFlag.FIRST_AUTOMATICALLY_FLUSHABLE, hci.BroadcastFlag.POINT_TO_POINT, data)

    def get_event_queue(self):
        return self.our_acl_stream.get_event_queue()


class PyHalAdvertisement(object):

    def __init__(self, handle, py_hal, is_legacy):
        self.handle = handle
        self.py_hal = py_hal
        self.legacy = is_legacy

    def set_data(self, complete_name):
        advertising_data = [hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME, data=list(complete_name))]

        if self.legacy:
            self.py_hal.send_hci_command(hci.LeSetAdvertisingData(advertising_data=advertising_data))
            self.py_hal.wait_for_complete(hci.OpCode.LE_SET_ADVERTISING_DATA)
        else:
            self.py_hal.send_hci_command(
                hci.LeSetExtendedAdvertisingData(advertising_handle=self.handle,
                                                 operation=hci.Operation.COMPLETE_ADVERTISEMENT,
                                                 fragment_preference=hci.FragmentPreference.CONTROLLER_SHOULD_NOT,
                                                 advertising_data=advertising_data))
            self.py_hal.wait_for_complete(hci.OpCode.LE_SET_EXTENDED_ADVERTISING_DATA)

    def set_scan_response(self, shortened_name):
        advertising_data = [hci.GapData(data_type=hci.GapDataType.SHORTENED_LOCAL_NAME, data=list(shortened_name))]

        if self.legacy:
            self.py_hal.send_hci_command(hci.LeSetScanResponseData(advertising_data=advertising_data))
            self.py_hal.wait_for_complete(hci.OpCode.LE_SET_SCAN_RESPONSE_DATA)
        else:
            self.py_hal.send_hci_command(
                hci.LeSetExtendedScanResponseData(advertising_handle=self.handle,
                                                  operation=hci.Operation.COMPLETE_ADVERTISEMENT,
                                                  fragment_preference=hci.FragmentPreference.CONTROLLER_SHOULD_NOT,
                                                  scan_response_data=advertising_data))
            self.py_hal.wait_for_complete(hci.OpCode.LE_SET_EXTENDED_SCAN_RESPONSE_DATA)

    def start(self):
        if self.legacy:
            self.py_hal.send_hci_command(hci.LeSetAdvertisingEnable(advertising_enable=hci.Enable.ENABLED))
            self.py_hal.wait_for_complete(hci.OpCode.LE_SET_ADVERTISING_ENABLE)
        else:
            self.py_hal.send_hci_command(
                hci.LeSetExtendedAdvertisingEnable(enable=hci.Enable.ENABLED,
                                                   enabled_sets=[
                                                       hci.EnabledSet(advertising_handle=self.handle,
                                                                      duration=0,
                                                                      max_extended_advertising_events=0)
                                                   ]))
            self.py_hal.wait_for_complete(hci.OpCode.LE_SET_EXTENDED_ADVERTISING_ENABLE)

    def stop(self):
        if self.legacy:
            self.py_hal.send_hci_command(hci.LeSetAdvertisingEnable(advertising_enable=hci.Enable.DISABLED))
            self.py_hal.wait_for_complete(hci.OpCode.LE_SET_ADVERTISING_ENABLE)
        else:
            self.py_hal.send_hci_command(
                hci.LeSetExtendedAdvertisingEnable(enable=hci.Enable.DISABLED,
                                                   enabled_sets=[
                                                       hci.EnabledSet(advertising_handle=self.handle,
                                                                      duration=0,
                                                                      max_extended_advertising_events=0)
                                                   ]))
            self.py_hal.wait_for_complete(hci.OpCode.LE_SET_EXTENDED_ADVERTISING_ENABLE)


class PyHal(Closable):

    def __init__(self, device):
        self.device = device

        self.hci_event_stream = EventStream(self.device.hal.StreamEvents(empty_proto.Empty()))
        self.acl_stream = EventStream(self.device.hal.StreamAcl(empty_proto.Empty()))

        self.event_mask = 0x1FFF_FFFF_FFFF  # Default Event Mask (Core Vol 4 [E] 7.3.1)
        self.le_event_mask = 0x0000_0000_001F  # Default LE Event Mask (Core Vol 4 [E] 7.8.1)

        # We don't deal with SCO for now

    def close(self):
        safeClose(self.hci_event_stream)
        safeClose(self.acl_stream)

    def get_hci_event_stream(self):
        return self.hci_event_stream

    def wait_for_complete(self, opcode):
        assertThat(self.hci_event_stream).emits(HciMatchers.CommandComplete(opcode))

    def wait_for_status(self, opcode):
        assertThat(self.hci_event_stream).emits(HciMatchers.CommandStatus(opcode))

    def get_acl_stream(self):
        return self.acl_stream

    def send_hci_command(self, command: hci.Packet):
        self.device.hal.SendCommand(common.Data(payload=command.serialize()))

    def send_acl(self, handle, pb_flag, b_flag, data: bytes):
        acl = hci.Acl(handle=handle, packet_boundary_flag=pb_flag, broadcast_flag=b_flag, payload=data)
        self.device.hal.SendAcl(common.Data(payload=acl.serialize()))

    def send_acl_first(self, handle, data: bytes):
        self.send_acl(handle, hci.PacketBoundaryFlag.FIRST_NON_AUTOMATICALLY_FLUSHABLE,
                      hci.BroadcastFlag.POINT_TO_POINT, data)

    def read_own_address(self) -> bluetooth.Address:
        self.send_hci_command(hci.ReadBdAddr())
        read_bd_addr = HciCaptures.ReadBdAddrCompleteCapture()
        assertThat(self.hci_event_stream).emits(read_bd_addr)
        return read_bd_addr.get().bd_addr

    def set_random_le_address(self, addr):
        self.send_hci_command(hci.LeSetRandomAddress(random_address=bluetooth.Address(addr)))
        self.wait_for_complete(hci.OpCode.LE_SET_RANDOM_ADDRESS)

    def set_scan_parameters(self):
        self.send_hci_command(
            hci.LeSetExtendedScanParameters(own_address_type=hci.OwnAddressType.RANDOM_DEVICE_ADDRESS,
                                            scanning_filter_policy=hci.LeScanningFilterPolicy.ACCEPT_ALL,
                                            scanning_phys=1,
                                            parameters=[
                                                hci.PhyScanParameters(le_scan_type=hci.LeScanType.ACTIVE,
                                                                      le_scan_interval=6553,
                                                                      le_scan_window=6553)
                                            ]))
        self.wait_for_complete(hci.OpCode.LE_SET_EXTENDED_SCAN_PARAMETERS)

    def unmask_event(self, *event_codes):
        for event_code in event_codes:
            self.event_mask |= 1 << (int(event_code) - 1)
        self.send_hci_command(hci.SetEventMask(event_mask=self.event_mask))

    def unmask_le_event(self, *subevent_codes):
        for subevent_code in subevent_codes:
            self.le_event_mask |= 1 << (int(subevent_code) - 1)
        self.send_hci_command(hci.LeSetEventMask(le_event_mask=self.le_event_mask))

    def start_scanning(self):
        self.send_hci_command(
            hci.LeSetExtendedScanEnable(enable=hci.Enable.ENABLED,
                                        filter_duplicates=hci.FilterDuplicates.DISABLED,
                                        duration=0,
                                        period=0))
        self.wait_for_complete(hci.OpCode.LE_SET_EXTENDED_SCAN_ENABLE)

    def stop_scanning(self):
        self.send_hci_command(
            hci.LeSetExtendedScanEnable(enable=hci.Enable.DISABLED,
                                        filter_duplicates=hci.FilterDuplicates.DISABLED,
                                        duration=0,
                                        period=0))
        self.wait_for_complete(hci.OpCode.LE_SET_EXTENDED_SCAN_ENABLE)

    def reset(self):
        self.send_hci_command(hci.Reset())
        self.wait_for_complete(hci.OpCode.RESET)

    def enable_inquiry_and_page_scan(self):
        self.send_hci_command(hci.WriteScanEnable(scan_enable=hci.ScanEnable.INQUIRY_AND_PAGE_SCAN))

    def initiate_connection(self, remote_addr):
        self.send_hci_command(
            hci.CreateConnection(bd_addr=bluetooth.Address(remote_addr),
                                 packet_type=0xcc18,
                                 page_scan_repetition_mode=hci.PageScanRepetitionMode.R1,
                                 clock_offset=0x0,
                                 clock_offset_valid=hci.ClockOffsetValid.INVALID,
                                 allow_role_switch=hci.CreateConnectionRoleSwitch.ALLOW_ROLE_SWITCH))

    def accept_connection(self):
        connection_request = HciCaptures.ConnectionRequestCapture()
        assertThat(self.hci_event_stream).emits(connection_request)

        self.send_hci_command(
            hci.AcceptConnectionRequest(bd_addr=connection_request.get().bd_addr,
                                        role=hci.AcceptConnectionRequestRole.REMAIN_PERIPHERAL))
        return self.complete_connection()

    def complete_connection(self):
        connection_complete = HciCaptures.ConnectionCompleteCapture()
        assertThat(self.hci_event_stream).emits(connection_complete)

        handle = connection_complete.get().connection_handle
        return PyHalAclConnection(handle, self.acl_stream, self.device)

    def initiate_le_connection(self, remote_addr):
        self.send_hci_command(
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
        self.wait_for_status(hci.OpCode.LE_EXTENDED_CREATE_CONNECTION)

    def add_to_filter_accept_list(self, remote_addr):
        self.send_hci_command(
            hci.LeAddDeviceToFilterAcceptList(address_type=hci.FilterAcceptListAddressType.RANDOM,
                                              address=bluetooth.Address(remote_addr)))

    def initiate_le_connection_by_filter_accept_list(self, remote_addr):
        self.send_hci_command(
            hci.LeExtendedCreateConnection(initiator_filter_policy=hci.InitiatorFilterPolicy.USE_FILTER_ACCEPT_LIST,
                                           own_address_type=hci.OwnAddressType.RANDOM_DEVICE_ADDRESS,
                                           peer_address_type=hci.AddressType.RANDOM_DEVICE_ADDRESS,
                                           peer_address=bluetooth.Address('00:00:00:00:00:00'),
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

    def complete_le_connection(self):
        connection_complete = HciCaptures.LeConnectionCompleteCapture()
        assertThat(self.hci_event_stream).emits(connection_complete)

        handle = connection_complete.get().connection_handle
        return PyHalAclConnection(handle, self.acl_stream, self.device)

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

        self.send_hci_command(
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
        self.wait_for_complete(hci.OpCode.LE_SET_EXTENDED_ADVERTISING_PARAMETERS)
        self.send_hci_command(
            hci.LeSetAdvertisingSetRandomAddress(advertising_handle=handle,
                                                 random_address=bluetooth.Address(own_address)))
        self.wait_for_complete(hci.OpCode.LE_SET_ADVERTISING_SET_RANDOM_ADDRESS)

        return PyHalAdvertisement(handle, self, False)

    def create_legacy_advertisement(self,
                                    advertising_type=hci.AdvertisingType.ADV_IND,
                                    min_interval=400,
                                    max_interval=450,
                                    channel_map=7,
                                    own_address_type=hci.OwnAddressType.RANDOM_DEVICE_ADDRESS,
                                    peer_address_type=hci.PeerAddressType.PUBLIC_DEVICE_OR_IDENTITY_ADDRESS,
                                    peer_address='00:00:00:00:00:00',
                                    filter_policy=hci.AdvertisingFilterPolicy.ALL_DEVICES):

        self.send_hci_command(
            hci.LeSetAdvertisingParameters(advertising_interval_min=min_interval,
                                           advertising_interval_max=max_interval,
                                           advertising_type=advertising_type,
                                           own_address_type=own_address_type,
                                           peer_address_type=peer_address_type,
                                           peer_address=bluetooth.Address(peer_address),
                                           advertising_channel_map=channel_map,
                                           advertising_filter_policy=filter_policy))
        self.wait_for_complete(hci.OpCode.LE_SET_ADVERTISING_PARAMETERS)

        return PyHalAdvertisement(None, self, True)
