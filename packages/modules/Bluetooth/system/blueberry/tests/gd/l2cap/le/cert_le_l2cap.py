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

from blueberry.tests.gd.cert.captures import L2capCaptures
from blueberry.tests.gd.cert.closable import Closable
from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.event_stream import FilteringEventStream
from blueberry.tests.gd.cert.event_stream import IEventStream
from blueberry.tests.gd.cert.matchers import L2capMatchers
from blueberry.tests.gd.cert.py_le_acl_manager import PyLeAclManager
from blueberry.tests.gd.cert.truth import assertThat
import bluetooth_packets_python3 as bt_packets
from bluetooth_packets_python3 import l2cap_packets
from bluetooth_packets_python3.l2cap_packets import LeCommandCode
from bluetooth_packets_python3.l2cap_packets import LeCreditBasedConnectionResponseResult


class CertLeL2capChannel(IEventStream):

    def __init__(self, device, scid, dcid, acl_stream, acl, control_channel, initial_credits=0):
        self._device = device
        self._scid = scid
        self._dcid = dcid
        self._acl_stream = acl_stream
        self._acl = acl
        self._control_channel = control_channel
        self._our_acl_view = FilteringEventStream(acl_stream, L2capMatchers.ExtractBasicFrame(scid))
        self._credits_left = initial_credits

    def get_event_queue(self):
        return self._our_acl_view.get_event_queue()

    def send(self, packet):
        frame = l2cap_packets.BasicFrameBuilder(self._dcid, packet)
        self._acl.send(frame.Serialize())
        self._credits_left -= 1

    def send_first_le_i_frame(self, sdu_size, packet):
        frame = l2cap_packets.FirstLeInformationFrameBuilder(self._dcid, sdu_size, packet)
        self._acl.send(frame.Serialize())
        self._credits_left -= 1

    def disconnect_and_verify(self):
        assertThat(self._scid).isNotEqualTo(1)
        self._control_channel.send(l2cap_packets.LeDisconnectionRequestBuilder(1, self._dcid, self._scid))

        assertThat(self._control_channel).emits(L2capMatchers.LeDisconnectionResponse(self._scid, self._dcid))

    def verify_disconnect_request(self):
        assertThat(self._control_channel).emits(L2capMatchers.LeDisconnectionRequest(self._dcid, self._scid))

    def send_credits(self, num_credits):
        self._control_channel.send(l2cap_packets.LeFlowControlCreditBuilder(2, self._scid, num_credits))

    def credits_left(self):
        return self._credits_left


class CertLeL2cap(Closable):

    def __init__(self, device):
        self._device = device
        self._le_acl_manager = PyLeAclManager(device)
        self._le_acl = None

        self.control_table = {
            LeCommandCode.DISCONNECTION_REQUEST: self._on_disconnection_request_default,
            LeCommandCode.DISCONNECTION_RESPONSE: self._on_disconnection_response_default,
            LeCommandCode.LE_FLOW_CONTROL_CREDIT: self._on_credit,
        }

        self._cid_to_cert_channels = {}

    def close(self):
        self._le_acl_manager.close()
        safeClose(self._le_acl)

    def connect_le_acl(self, remote_addr):
        self._le_acl = self._le_acl_manager.connect_to_remote(remote_addr)
        self.control_channel = CertLeL2capChannel(
            self._device, 5, 5, self._get_acl_stream(), self._le_acl, control_channel=None)
        self._get_acl_stream().register_callback(self._handle_control_packet)

    def wait_for_connection(self):
        self._le_acl = self._le_acl_manager.wait_for_connection()
        self.control_channel = CertLeL2capChannel(
            self._device, 5, 5, self._get_acl_stream(), self._le_acl, control_channel=None)
        self._get_acl_stream().register_callback(self._handle_control_packet)

    def open_fixed_channel(self, cid=4):
        channel = CertLeL2capChannel(self._device, cid, cid, self._get_acl_stream(), self._le_acl, None, 0)
        return channel

    def open_channel(self, signal_id, psm, scid, mtu=1000, mps=100, initial_credit=6):
        self.control_channel.send(
            l2cap_packets.LeCreditBasedConnectionRequestBuilder(signal_id, psm, scid, mtu, mps, initial_credit))

        response = L2capCaptures.CreditBasedConnectionResponse()
        assertThat(self.control_channel).emits(response)
        channel = CertLeL2capChannel(self._device, scid,
                                     response.get().GetDestinationCid(), self._get_acl_stream(), self._le_acl,
                                     self.control_channel,
                                     response.get().GetInitialCredits())
        self._cid_to_cert_channels[scid] = channel
        return channel

    def open_channel_with_expected_result(self, psm=0x33, result=LeCreditBasedConnectionResponseResult.SUCCESS):
        self.control_channel.send(l2cap_packets.LeCreditBasedConnectionRequestBuilder(1, psm, 0x40, 1000, 100, 6))

        response = L2capMatchers.CreditBasedConnectionResponse(result)
        assertThat(self.control_channel).emits(response)

    def verify_and_respond_open_channel_from_remote(self,
                                                    psm=0x33,
                                                    result=LeCreditBasedConnectionResponseResult.SUCCESS,
                                                    our_scid=None):
        request = L2capCaptures.CreditBasedConnectionRequest(psm)
        assertThat(self.control_channel).emits(request)
        (scid, dcid) = self._respond_connection_request_default(request.get(), result, our_scid)
        channel = CertLeL2capChannel(self._device, scid, dcid, self._get_acl_stream(), self._le_acl,
                                     self.control_channel,
                                     request.get().GetInitialCredits())
        self._cid_to_cert_channels[scid] = channel
        return channel

    def verify_and_reject_open_channel_from_remote(self, psm=0x33):
        request = L2capCaptures.CreditBasedConnectionRequest(psm)
        assertThat(self.control_channel).emits(request)
        sid = request.get().GetIdentifier()
        reject = l2cap_packets.LeCommandRejectNotUnderstoodBuilder(sid)
        self.control_channel.send(reject)

    def verify_le_flow_control_credit(self, channel):
        assertThat(self.control_channel).emits(L2capMatchers.LeFlowControlCredit(channel._dcid))

    def _respond_connection_request_default(self,
                                            request,
                                            result=LeCreditBasedConnectionResponseResult.SUCCESS,
                                            our_scid=None):
        sid = request.GetIdentifier()
        their_scid = request.GetSourceCid()
        mtu = request.GetMtu()
        mps = request.GetMps()
        initial_credits = request.GetInitialCredits()
        # If our_scid is not specified, we use the same value - their scid as their scid
        if our_scid is None:
            our_scid = their_scid
        our_dcid = their_scid
        response = l2cap_packets.LeCreditBasedConnectionResponseBuilder(sid, our_scid, mtu, mps, initial_credits,
                                                                        result)
        self.control_channel.send(response)
        return (our_scid, our_dcid)

    def get_control_channel(self):
        return self.control_channel

    def _get_acl_stream(self):
        return self._le_acl.acl_stream

    def _on_disconnection_request_default(self, request):
        disconnection_request = l2cap_packets.LeDisconnectionRequestView(request)
        sid = disconnection_request.GetIdentifier()
        scid = disconnection_request.GetSourceCid()
        dcid = disconnection_request.GetDestinationCid()
        response = l2cap_packets.LeDisconnectionResponseBuilder(sid, dcid, scid)
        self.control_channel.send(response)

    def _on_disconnection_response_default(self, request):
        disconnection_response = l2cap_packets.LeDisconnectionResponseView(request)

    def _on_credit(self, l2cap_le_control_view):
        credit_view = l2cap_packets.LeFlowControlCreditView(l2cap_le_control_view)
        cid = credit_view.GetCid()
        if cid not in self._cid_to_cert_channels:
            return
        self._cid_to_cert_channels[cid]._credits_left += credit_view.GetCredits()

    def _handle_control_packet(self, l2cap_packet):
        packet_bytes = l2cap_packet.payload
        l2cap_view = l2cap_packets.BasicFrameView(bt_packets.PacketViewLittleEndian(list(packet_bytes)))
        if l2cap_view.GetChannelId() != 5:
            return
        request = l2cap_packets.LeControlView(l2cap_view.GetPayload())
        fn = self.control_table.get(request.GetCode())
        if fn is not None:
            fn(request)
        return
