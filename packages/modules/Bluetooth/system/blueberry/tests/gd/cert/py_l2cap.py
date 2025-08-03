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

from blueberry.facade.l2cap.classic import facade_pb2 as l2cap_facade_pb2
from blueberry.facade.l2cap.classic.facade_pb2 import LinkSecurityInterfaceCallbackEventType
from blueberry.facade.l2cap.le import facade_pb2 as l2cap_le_facade_pb2
from blueberry.facade.l2cap.le.facade_pb2 import SecurityLevel
from bluetooth_packets_python3 import l2cap_packets
from blueberry.tests.gd.cert.event_stream import FilteringEventStream
from blueberry.tests.gd.cert.event_stream import EventStream, IEventStream
from blueberry.tests.gd.cert.closable import Closable, safeClose
from blueberry.tests.gd.cert.py_hci import PyHci
from blueberry.tests.gd.cert.matchers import HciMatchers
from blueberry.tests.gd.cert.matchers import L2capMatchers
from blueberry.tests.gd.cert.truth import assertThat
import hci_packets as hci


class PyL2capChannel(IEventStream):

    def __init__(self, device, psm, l2cap_stream):
        self._device = device
        self._psm = psm
        self._le_l2cap_stream = l2cap_stream
        self._our_le_l2cap_view = FilteringEventStream(self._le_l2cap_stream,
                                                       L2capMatchers.PacketPayloadWithMatchingPsm(self._psm))

    def get_event_queue(self):
        return self._our_le_l2cap_view.get_event_queue()

    def send(self, payload):
        self._device.l2cap.SendDynamicChannelPacket(
            l2cap_facade_pb2.DynamicChannelPacket(psm=self._psm, payload=payload))

    def close_channel(self):
        self._device.l2cap.CloseChannel(l2cap_facade_pb2.CloseChannelRequest(psm=self._psm))

    def set_traffic_paused(self, paused):
        self._device.l2cap.SetTrafficPaused(l2cap_facade_pb2.SetTrafficPausedRequest(psm=self._psm, paused=paused))


class _ClassicConnectionResponseFutureWrapper(object):
    """
    The future object returned when we send a connection request from DUT. Can be used to get connection status and
    create the corresponding PyL2capDynamicChannel object later
    """

    def __init__(self, grpc_response_future, device, psm, l2cap_stream):
        self._grpc_response_future = grpc_response_future
        self._device = device
        self._psm = psm
        self._l2cap_stream = l2cap_stream

    def get_channel(self):
        return PyL2capChannel(self._device, self._psm, self._l2cap_stream)


class PyL2cap(Closable):

    def __init__(self, device, cert_address, has_security=False):
        self._device = device
        self._cert_address = cert_address
        self._hci = PyHci(device)
        self._l2cap_stream = EventStream(self._device.l2cap.FetchL2capData(empty_proto.Empty()))
        self._security_connection_event_stream = EventStream(
            self._device.l2cap.FetchSecurityConnectionEvents(empty_proto.Empty()))
        if has_security == False:
            self._hci.register_for_events(hci.EventCode.LINK_KEY_REQUEST)

    def close(self):
        safeClose(self._l2cap_stream)
        safeClose(self._security_connection_event_stream)
        safeClose(self._hci)

    def register_dynamic_channel(self, psm=0x33, mode=l2cap_facade_pb2.RetransmissionFlowControlMode.BASIC):
        self._device.l2cap.SetDynamicChannel(
            l2cap_facade_pb2.SetEnableDynamicChannelRequest(psm=psm, retransmission_mode=mode))
        return PyL2capChannel(self._device, psm, self._l2cap_stream)

    def connect_dynamic_channel_to_cert(self, psm=0x33, mode=l2cap_facade_pb2.RetransmissionFlowControlMode.BASIC):
        """
        Send open Dynamic channel request to CERT.
        Get a future for connection result, to be used after CERT accepts request
        """
        self.register_dynamic_channel(psm, mode)
        response_future = self._device.l2cap.OpenChannel.future(
            l2cap_facade_pb2.OpenChannelRequest(psm=psm, remote=self._cert_address, mode=mode))

        return _ClassicConnectionResponseFutureWrapper(response_future, self._device, psm, self._l2cap_stream)

    def get_channel_queue_buffer_size(self):
        return self._device.l2cap.GetChannelQueueDepth(empty_proto.Empty()).size

    def initiate_connection_for_security(self):
        """
        Establish an ACL for the specific purpose of pairing devices
        """
        self._device.l2cap.InitiateConnectionForSecurity(self._cert_address)

    def get_security_connection_event_stream(self):
        """
        Stream of Link related events.  Events are returned with an address.
        Events map to the LinkSecurityInterfaceListener callbacks
        """
        return self._security_connection_event_stream

    def security_link_hold(self):
        """
        Holds open the ACL indefinitely allowing for the security handshake
        to take place
        """
        self._device.l2cap.SecurityLinkHold(self._cert_address)

    def security_link_ensure_authenticated(self):
        """
        Triggers authentication process by sending HCI event AUTHENTICATION_REQUESTED
        """
        self._device.l2cap.SecurityLinkEnsureAuthenticated(self._cert_address)

    def security_link_release(self):
        """
        Releases a Held open ACL allowing for the ACL to time out after the default time
        """
        self._device.l2cap.SecurityLinkRelease(self._cert_address)

    def security_link_disconnect(self):
        """
        Immediately release and disconnect ACL
        """
        self._device.l2cap.SecurityLinkDisconnect(self._cert_address)

    def verify_security_connection(self):
        """
        Verify that we get a connection and a link key request
        """
        assertThat(self.get_security_connection_event_stream()).emits(
            lambda event: event.event_type == LinkSecurityInterfaceCallbackEventType.ON_CONNECTED)
        assertThat(self._hci.get_event_stream()).emits(HciMatchers.LinkKeyRequest())


class PyLeL2capFixedChannel(IEventStream):

    def __init__(self, device, cid, l2cap_stream):
        self._device = device
        self._cid = cid
        self._le_l2cap_stream = l2cap_stream
        self._our_le_l2cap_view = FilteringEventStream(self._le_l2cap_stream,
                                                       L2capMatchers.PacketPayloadWithMatchingCid(self._cid))

    def get_event_queue(self):
        return self._our_le_l2cap_view.get_event_queue()

    def send(self, payload):
        self._device.l2cap_le.SendFixedChannelPacket(
            l2cap_le_facade_pb2.FixedChannelPacket(cid=self._cid, payload=payload))

    def close_channel(self):
        self._device.l2cap_le.SetFixedChannel(
            l2cap_le_facade_pb2.SetEnableFixedChannelRequest(cid=self._cid, enable=False))


class PyLeL2capDynamicChannel(IEventStream):

    def __init__(self, device, cert_address, psm, l2cap_stream):
        self._device = device
        self._cert_address = cert_address
        self._psm = psm
        self._le_l2cap_stream = l2cap_stream
        self._our_le_l2cap_view = FilteringEventStream(self._le_l2cap_stream,
                                                       L2capMatchers.PacketPayloadWithMatchingPsm(self._psm))

    def get_event_queue(self):
        return self._our_le_l2cap_view.get_event_queue()

    def send(self, payload):
        self._device.l2cap_le.SendDynamicChannelPacket(
            l2cap_le_facade_pb2.DynamicChannelPacket(psm=self._psm, payload=payload))

    def close_channel(self):
        self._device.l2cap_le.CloseDynamicChannel(
            l2cap_le_facade_pb2.CloseDynamicChannelRequest(remote=self._cert_address, psm=self._psm))


class _CreditBasedConnectionResponseFutureWrapper(object):
    """
    The future object returned when we send a connection request from DUT. Can be used to get connection status and
    create the corresponding PyLeL2capDynamicChannel object later
    """

    def __init__(self, grpc_response_future, device, cert_address, psm, le_l2cap_stream):
        self._grpc_response_future = grpc_response_future
        self._device = device
        self._cert_address = cert_address
        self._psm = psm
        self._le_l2cap_stream = le_l2cap_stream

    def get_status(self):
        return l2cap_packets.LeCreditBasedConnectionResponseResult(self._grpc_response_future.result().status)

    def get_channel(self):
        assertThat(self.get_status()).isEqualTo(l2cap_packets.LeCreditBasedConnectionResponseResult.SUCCESS)
        return PyLeL2capDynamicChannel(self._device, self._cert_address, self._psm, self._le_l2cap_stream)


class PyLeL2cap(Closable):

    def __init__(self, device):
        self._device = device
        self._le_l2cap_stream = EventStream(self._device.l2cap_le.FetchL2capData(empty_proto.Empty()))

    def close(self):
        safeClose(self._le_l2cap_stream)

    def enable_fixed_channel(self, cid=4):
        self._device.l2cap_le.SetFixedChannel(l2cap_le_facade_pb2.SetEnableFixedChannelRequest(cid=cid, enable=True))

    def get_fixed_channel(self, cid=4):
        return PyLeL2capFixedChannel(self._device, cid, self._le_l2cap_stream)

    def register_coc(self, cert_address, psm=0x33, security_level=SecurityLevel.NO_SECURITY):
        self._device.l2cap_le.SetDynamicChannel(
            l2cap_le_facade_pb2.SetEnableDynamicChannelRequest(psm=psm, enable=True, security_level=security_level))
        return PyLeL2capDynamicChannel(self._device, cert_address, psm, self._le_l2cap_stream)

    def connect_coc_to_cert(self, cert_address, psm=0x33):
        """
        Send open LE COC request to CERT. Get a future for connection result, to be used after CERT accepts request
        """
        self.register_coc(cert_address, psm)
        response_future = self._device.l2cap_le.OpenDynamicChannel.future(
            l2cap_le_facade_pb2.OpenDynamicChannelRequest(psm=psm, remote=cert_address))

        return _CreditBasedConnectionResponseFutureWrapper(response_future, self._device, cert_address, psm,
                                                           self._le_l2cap_stream)

    def update_connection_parameter(self,
                                    conn_interval_min=0x10,
                                    conn_interval_max=0x10,
                                    conn_latency=0x0a,
                                    supervision_timeout=0x64,
                                    min_ce_length=12,
                                    max_ce_length=12):
        self._device.l2cap_le.SendConnectionParameterUpdate(
            l2cap_le_facade_pb2.ConnectionParameter(conn_interval_min=conn_interval_min,
                                                    conn_interval_max=conn_interval_max,
                                                    conn_latency=conn_latency,
                                                    supervision_timeout=supervision_timeout,
                                                    min_ce_length=min_ce_length,
                                                    max_ce_length=max_ce_length))
