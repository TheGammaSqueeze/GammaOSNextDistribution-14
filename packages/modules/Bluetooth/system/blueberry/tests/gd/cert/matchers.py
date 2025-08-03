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

import bluetooth_packets_python3 as bt_packets
import logging
import sys

from bluetooth_packets_python3 import l2cap_packets
from bluetooth_packets_python3.l2cap_packets import CommandCode, LeCommandCode
from bluetooth_packets_python3.l2cap_packets import ConfigurationResponseResult
from bluetooth_packets_python3.l2cap_packets import ConnectionResponseResult
from bluetooth_packets_python3.l2cap_packets import InformationRequestInfoType
from bluetooth_packets_python3.l2cap_packets import LeCreditBasedConnectionResponseResult

from blueberry.utils import bluetooth
import hci_packets as hci


class HciMatchers(object):

    @staticmethod
    def CommandComplete(opcode):
        return lambda msg: HciMatchers._is_matching_command_complete(msg.payload, opcode)

    @staticmethod
    def ExtractMatchingCommandComplete(packet_bytes, opcode=None):
        return HciMatchers._extract_matching_command_complete(packet_bytes, opcode)

    @staticmethod
    def _is_matching_command_complete(packet_bytes, opcode=None):
        return HciMatchers._extract_matching_command_complete(packet_bytes, opcode) is not None

    @staticmethod
    def _extract_matching_command_complete(packet_bytes, opcode=None):
        event = HciMatchers._extract_matching_event(packet_bytes, hci.EventCode.COMMAND_COMPLETE)
        if not isinstance(event, hci.CommandComplete):
            return None
        if opcode and event.command_op_code != opcode:
            return None
        return event

    @staticmethod
    def CommandStatus(opcode=None):
        return lambda msg: HciMatchers._is_matching_command_status(msg.payload, opcode)

    @staticmethod
    def ExtractMatchingCommandStatus(packet_bytes, opcode=None):
        return HciMatchers._extract_matching_command_status(packet_bytes, opcode)

    @staticmethod
    def _is_matching_command_status(packet_bytes, opcode=None):
        return HciMatchers._extract_matching_command_status(packet_bytes, opcode) is not None

    @staticmethod
    def _extract_matching_command_status(packet_bytes, opcode=None):
        event = HciMatchers._extract_matching_event(packet_bytes, hci.EventCode.COMMAND_STATUS)
        if not isinstance(event, hci.CommandStatus):
            return None
        if opcode and event.command_op_code != opcode:
            return None
        return event

    @staticmethod
    def EventWithCode(event_code):
        return lambda msg: HciMatchers._is_matching_event(msg.payload, event_code)

    @staticmethod
    def ExtractEventWithCode(packet_bytes, event_code):
        return HciMatchers._extract_matching_event(packet_bytes, event_code)

    @staticmethod
    def _is_matching_event(packet_bytes, event_code):
        return HciMatchers._extract_matching_event(packet_bytes, event_code) is not None

    @staticmethod
    def _extract_matching_event(packet_bytes, event_code):
        try:
            event = hci.Event.parse_all(packet_bytes)
            return event if event.event_code == event_code else None
        except Exception as exn:
            print(sys.stderr, f"Failed to parse incoming event: {exn}")
            print(sys.stderr, f"Event data: {' '.join([f'{b:02x}' for b in packet_bytes])}")
            return None

    @staticmethod
    def LeEventWithCode(subevent_code):
        return lambda msg: HciMatchers._extract_matching_le_event(msg.payload, subevent_code) is not None

    @staticmethod
    def ExtractLeEventWithCode(packet_bytes, subevent_code):
        return HciMatchers._extract_matching_le_event(packet_bytes, subevent_code)

    @staticmethod
    def _extract_matching_le_event(packet_bytes, subevent_code):
        event = HciMatchers._extract_matching_event(packet_bytes, hci.EventCode.LE_META_EVENT)
        if (not isinstance(event, hci.LeMetaEvent) or event.subevent_code != subevent_code):
            return None

        return event

    @staticmethod
    def LeAdvertisement(subevent_code=hci.SubeventCode.EXTENDED_ADVERTISING_REPORT, address=None, data=None):
        return lambda msg: HciMatchers._extract_matching_le_advertisement(msg.payload, subevent_code, address, data
                                                                         ) is not None

    @staticmethod
    def ExtractLeAdvertisement(packet_bytes,
                               subevent_code=hci.SubeventCode.EXTENDED_ADVERTISING_REPORT,
                               address=None,
                               data=None):
        return HciMatchers._extract_matching_le_advertisement(packet_bytes, subevent_code, address, data)

    @staticmethod
    def _extract_matching_le_advertisement(packet_bytes,
                                           subevent_code=hci.SubeventCode.EXTENDED_ADVERTISING_REPORT,
                                           address=None,
                                           data=None):
        event = HciMatchers._extract_matching_le_event(packet_bytes, subevent_code)
        if event is None:
            return None

        matched = False
        for response in event.responses:
            matched |= (address == None or response.address == bluetooth.Address(address)) and (data == None or
                                                                                                data in packet_bytes)

        return event if matched else None

    @staticmethod
    def LeConnectionComplete():
        return lambda msg: HciMatchers._extract_le_connection_complete(msg.payload) is not None

    @staticmethod
    def ExtractLeConnectionComplete(packet_bytes):
        return HciMatchers._extract_le_connection_complete(packet_bytes)

    @staticmethod
    def _extract_le_connection_complete(packet_bytes):
        event = HciMatchers._extract_matching_le_event(packet_bytes, hci.SubeventCode.CONNECTION_COMPLETE)
        if event is not None:
            return event

        return HciMatchers._extract_matching_le_event(packet_bytes, hci.SubeventCode.ENHANCED_CONNECTION_COMPLETE)

    @staticmethod
    def LogEventCode():
        return lambda event: logging.info("Received event: %x" % hci.Event.parse(event.payload).event_code)

    @staticmethod
    def LinkKeyRequest():
        return HciMatchers.EventWithCode(hci.EventCode.LINK_KEY_REQUEST)

    @staticmethod
    def IoCapabilityRequest():
        return HciMatchers.EventWithCode(hci.EventCode.IO_CAPABILITY_REQUEST)

    @staticmethod
    def IoCapabilityResponse():
        return HciMatchers.EventWithCode(hci.EventCode.IO_CAPABILITY_RESPONSE)

    @staticmethod
    def UserPasskeyNotification():
        return HciMatchers.EventWithCode(hci.EventCode.USER_PASSKEY_NOTIFICATION)

    @staticmethod
    def UserPasskeyRequest():
        return HciMatchers.EventWithCode(hci.EventCode.USER_PASSKEY_REQUEST)

    @staticmethod
    def UserConfirmationRequest():
        return HciMatchers.EventWithCode(hci.EventCode.USER_CONFIRMATION_REQUEST)

    @staticmethod
    def LinkKeyNotification():
        return HciMatchers.EventWithCode(hci.EventCode.LINK_KEY_NOTIFICATION)

    @staticmethod
    def SimplePairingComplete():
        return HciMatchers.EventWithCode(hci.EventCode.SIMPLE_PAIRING_COMPLETE)

    @staticmethod
    def Disconnect():
        return HciMatchers.EventWithCode(hci.EventCode.DISCONNECT)

    @staticmethod
    def DisconnectionComplete():
        return HciMatchers.EventWithCode(hci.EventCode.DISCONNECTION_COMPLETE)

    @staticmethod
    def RemoteOobDataRequest():
        return HciMatchers.EventWithCode(hci.EventCode.REMOTE_OOB_DATA_REQUEST)

    @staticmethod
    def PinCodeRequest():
        return HciMatchers.EventWithCode(hci.EventCode.PIN_CODE_REQUEST)

    @staticmethod
    def LoopbackOf(packet):
        return HciMatchers.Exactly(hci.LoopbackCommand(payload=packet))

    @staticmethod
    def Exactly(packet):
        data = bytes(packet.serialize())
        return lambda event: data == event.payload


class AdvertisingMatchers(object):

    @staticmethod
    def AdvertisingCallbackMsg(type, advertiser_id=None, status=None, data=None):
        return lambda event: True if event.message_type == type and (advertiser_id == None or advertiser_id == event.advertiser_id) \
            and (status == None or status == event.status) and (data == None or data == event.data) else False

    @staticmethod
    def AddressMsg(type, advertiser_id=None, address=None):
        return lambda event: True if event.message_type == type and (advertiser_id == None or advertiser_id == event.advertiser_id) \
            and (address == None or address == event.address) else False


class ScanningMatchers(object):

    @staticmethod
    def ScanningCallbackMsg(type, status=None, data=None):
        return lambda event: True if event.message_type == type and (status == None or status == event.status) \
            and (data == None or data == event.data) else False


class NeighborMatchers(object):

    @staticmethod
    def InquiryResult(address):
        return lambda msg: NeighborMatchers._is_matching_inquiry_result(msg.packet, address)

    @staticmethod
    def _is_matching_inquiry_result(packet, address):
        event = HciMatchers.ExtractEventWithCode(packet, hci.EventCode.INQUIRY_RESULT)
        if not isinstance(event, hci.InquiryResult):
            return False
        return any((bluetooth.Address(address) == response.bd_addr for response in event.responses))

    @staticmethod
    def InquiryResultwithRssi(address):
        return lambda msg: NeighborMatchers._is_matching_inquiry_result_with_rssi(msg.packet, address)

    @staticmethod
    def _is_matching_inquiry_result_with_rssi(packet, address):
        event = HciMatchers.ExtractEventWithCode(packet, hci.EventCode.INQUIRY_RESULT_WITH_RSSI)
        if not isinstance(event, hci.InquiryResultWithRssi):
            return False
        return any((bluetooth.Address(address) == response.address for response in event.responses))

    @staticmethod
    def ExtendedInquiryResult(address):
        return lambda msg: NeighborMatchers._is_matching_extended_inquiry_result(msg.packet, address)

    @staticmethod
    def _is_matching_extended_inquiry_result(packet, address):
        event = HciMatchers.ExtractEventWithCode(packet, hci.EventCode.EXTENDED_INQUIRY_RESULT)
        if not isinstance(event, (hci.ExtendedInquiryResult, hci.ExtendedInquiryResultRaw)):
            return False
        return bluetooth.Address(address) == event.address


class L2capMatchers(object):

    @staticmethod
    def ConnectionRequest(psm):
        return lambda packet: L2capMatchers._is_matching_connection_request(packet, psm)

    @staticmethod
    def ConnectionResponse(scid):
        return lambda packet: L2capMatchers._is_matching_connection_response(packet, scid)

    @staticmethod
    def ConfigurationResponse(result=ConfigurationResponseResult.SUCCESS):
        return lambda packet: L2capMatchers._is_matching_configuration_response(packet, result)

    @staticmethod
    def ConfigurationRequest(cid=None):
        return lambda packet: L2capMatchers._is_matching_configuration_request_with_cid(packet, cid)

    @staticmethod
    def ConfigurationRequestWithErtm():
        return lambda packet: L2capMatchers._is_matching_configuration_request_with_ertm(packet)

    @staticmethod
    def ConfigurationRequestView(dcid):
        return lambda request_view: request_view.GetDestinationCid() == dcid

    @staticmethod
    def DisconnectionRequest(scid, dcid):
        return lambda packet: L2capMatchers._is_matching_disconnection_request(packet, scid, dcid)

    @staticmethod
    def DisconnectionResponse(scid, dcid):
        return lambda packet: L2capMatchers._is_matching_disconnection_response(packet, scid, dcid)

    @staticmethod
    def EchoResponse():
        return lambda packet: L2capMatchers._is_control_frame_with_code(packet, CommandCode.ECHO_RESPONSE)

    @staticmethod
    def CommandReject():
        return lambda packet: L2capMatchers._is_control_frame_with_code(packet, CommandCode.COMMAND_REJECT)

    @staticmethod
    def LeCommandReject():
        return lambda packet: L2capMatchers._is_le_control_frame_with_code(packet, LeCommandCode.COMMAND_REJECT)

    @staticmethod
    def LeConnectionParameterUpdateRequest():
        return lambda packet: L2capMatchers._is_le_control_frame_with_code(
            packet, LeCommandCode.CONNECTION_PARAMETER_UPDATE_REQUEST)

    @staticmethod
    def LeConnectionParameterUpdateResponse(result=l2cap_packets.ConnectionParameterUpdateResponseResult.ACCEPTED):
        return lambda packet: L2capMatchers._is_matching_connection_parameter_update_response(packet, result)

    @staticmethod
    def CreditBasedConnectionRequest(psm):
        return lambda packet: L2capMatchers._is_matching_credit_based_connection_request(packet, psm)

    @staticmethod
    def CreditBasedConnectionResponse(result=LeCreditBasedConnectionResponseResult.SUCCESS):
        return lambda packet: L2capMatchers._is_matching_credit_based_connection_response(packet, result)

    @staticmethod
    def CreditBasedConnectionResponseUsedCid():
        return lambda packet: L2capMatchers._is_matching_credit_based_connection_response(
            packet, LeCreditBasedConnectionResponseResult.SOURCE_CID_ALREADY_ALLOCATED
        ) or L2capMatchers._is_le_control_frame_with_code(packet, LeCommandCode.COMMAND_REJECT)

    @staticmethod
    def LeDisconnectionRequest(scid, dcid):
        return lambda packet: L2capMatchers._is_matching_le_disconnection_request(packet, scid, dcid)

    @staticmethod
    def LeDisconnectionResponse(scid, dcid):
        return lambda packet: L2capMatchers._is_matching_le_disconnection_response(packet, scid, dcid)

    @staticmethod
    def LeFlowControlCredit(cid):
        return lambda packet: L2capMatchers._is_matching_le_flow_control_credit(packet, cid)

    @staticmethod
    def SFrame(req_seq=None, f=None, s=None, p=None):
        return lambda packet: L2capMatchers._is_matching_supervisory_frame(packet, req_seq, f, s, p)

    @staticmethod
    def IFrame(tx_seq=None, payload=None, f=None):
        return lambda packet: L2capMatchers._is_matching_information_frame(packet, tx_seq, payload, f, fcs=False)

    @staticmethod
    def IFrameWithFcs(tx_seq=None, payload=None, f=None):
        return lambda packet: L2capMatchers._is_matching_information_frame(packet, tx_seq, payload, f, fcs=True)

    @staticmethod
    def IFrameStart(tx_seq=None, payload=None, f=None):
        return lambda packet: L2capMatchers._is_matching_information_start_frame(packet, tx_seq, payload, f, fcs=False)

    @staticmethod
    def Data(payload):
        return lambda packet: packet.GetPayload().GetBytes() == payload

    @staticmethod
    def FirstLeIFrame(payload, sdu_size):
        return lambda packet: L2capMatchers._is_matching_first_le_i_frame(packet, payload, sdu_size)

    # this is a hack - should be removed
    @staticmethod
    def PartialData(payload):
        return lambda packet: payload in packet.GetPayload().GetBytes()

    # this is a hack - should be removed
    @staticmethod
    def PacketPayloadRawData(payload):
        return lambda packet: payload in packet.payload

    # this is a hack - should be removed
    @staticmethod
    def PacketPayloadWithMatchingPsm(psm):
        return lambda packet: None if psm != packet.psm else packet

    # this is a hack - should be removed
    @staticmethod
    def PacketPayloadWithMatchingCid(cid):
        return lambda packet: None if cid != packet.fixed_cid else packet

    @staticmethod
    def ExtractBasicFrame(scid):
        return lambda packet: L2capMatchers._basic_frame_for(packet, scid)

    @staticmethod
    def ExtractBasicFrameWithFcs(scid):
        return lambda packet: L2capMatchers._basic_frame_with_fcs_for(packet, scid)

    @staticmethod
    def InformationRequestWithType(info_type):
        return lambda packet: L2capMatchers._information_request_with_type(packet, info_type)

    @staticmethod
    def InformationResponseExtendedFeatures(supports_ertm=None,
                                            supports_streaming=None,
                                            supports_fcs=None,
                                            supports_fixed_channels=None):
        return lambda packet: L2capMatchers._is_matching_information_response_extended_features(
            packet, supports_ertm, supports_streaming, supports_fcs, supports_fixed_channels)

    @staticmethod
    def _basic_frame(packet):
        if packet is None:
            return None
        return l2cap_packets.BasicFrameView(bt_packets.PacketViewLittleEndian(list(packet.payload)))

    @staticmethod
    def _basic_frame_with_fcs(packet):
        if packet is None:
            return None
        return l2cap_packets.BasicFrameWithFcsView(bt_packets.PacketViewLittleEndian(list(packet.payload)))

    @staticmethod
    def _basic_frame_for(packet, scid):
        frame = L2capMatchers._basic_frame(packet)
        if frame.GetChannelId() != scid:
            return None
        return frame

    @staticmethod
    def _basic_frame_with_fcs_for(packet, scid):
        frame = L2capMatchers._basic_frame(packet)
        if frame.GetChannelId() != scid:
            return None
        frame = L2capMatchers._basic_frame_with_fcs(packet)
        if frame is None:
            return None
        return frame

    @staticmethod
    def _information_frame(packet):
        standard_frame = l2cap_packets.StandardFrameView(packet)
        if standard_frame.GetFrameType() != l2cap_packets.FrameType.I_FRAME:
            return None
        return l2cap_packets.EnhancedInformationFrameView(standard_frame)

    @staticmethod
    def _information_frame_with_fcs(packet):
        standard_frame = l2cap_packets.StandardFrameWithFcsView(packet)
        if standard_frame is None:
            return None
        if standard_frame.GetFrameType() != l2cap_packets.FrameType.I_FRAME:
            return None
        return l2cap_packets.EnhancedInformationFrameWithFcsView(standard_frame)

    @staticmethod
    def _information_start_frame(packet):
        start_frame = L2capMatchers._information_frame(packet)
        if start_frame is None:
            return None
        return l2cap_packets.EnhancedInformationStartFrameView(start_frame)

    @staticmethod
    def _information_start_frame_with_fcs(packet):
        start_frame = L2capMatchers._information_frame_with_fcs(packet)
        if start_frame is None:
            return None
        return l2cap_packets.EnhancedInformationStartFrameWithFcsView(start_frame)

    @staticmethod
    def _supervisory_frame(packet):
        standard_frame = l2cap_packets.StandardFrameView(packet)
        if standard_frame.GetFrameType() != l2cap_packets.FrameType.S_FRAME:
            return None
        return l2cap_packets.EnhancedSupervisoryFrameView(standard_frame)

    @staticmethod
    def _is_matching_information_frame(packet, tx_seq, payload, f, fcs=False):
        if fcs:
            frame = L2capMatchers._information_frame_with_fcs(packet)
        else:
            frame = L2capMatchers._information_frame(packet)
        if frame is None:
            return False
        if tx_seq is not None and frame.GetTxSeq() != tx_seq:
            return False
        if payload is not None and frame.GetPayload().GetBytes() != payload:
            return False
        if f is not None and frame.GetF() != f:
            return False
        return True

    @staticmethod
    def _is_matching_information_start_frame(packet, tx_seq, payload, f, fcs=False):
        if fcs:
            frame = L2capMatchers._information_start_frame_with_fcs(packet)
        else:
            frame = L2capMatchers._information_start_frame(packet)
        if frame is None:
            return False
        if tx_seq is not None and frame.GetTxSeq() != tx_seq:
            return False
        if payload is not None and frame.GetPayload().GetBytes() != payload:
            return False
        if f is not None and frame.GetF() != f:
            return False
        return True

    @staticmethod
    def _is_matching_supervisory_frame(packet, req_seq, f, s, p):
        frame = L2capMatchers._supervisory_frame(packet)
        if frame is None:
            return False
        if req_seq is not None and frame.GetReqSeq() != req_seq:
            return False
        if f is not None and frame.GetF() != f:
            return False
        if s is not None and frame.GetS() != s:
            return False
        if p is not None and frame.GetP() != p:
            return False
        return True

    @staticmethod
    def _is_matching_first_le_i_frame(packet, payload, sdu_size):
        first_le_i_frame = l2cap_packets.FirstLeInformationFrameView(packet)
        return first_le_i_frame.GetPayload().GetBytes() == payload and first_le_i_frame.GetL2capSduLength() == sdu_size

    @staticmethod
    def _control_frame(packet):
        if packet.GetChannelId() != 1:
            return None
        return l2cap_packets.ControlView(packet.GetPayload())

    @staticmethod
    def _le_control_frame(packet):
        if packet.GetChannelId() != 5:
            return None
        return l2cap_packets.LeControlView(packet.GetPayload())

    @staticmethod
    def control_frame_with_code(packet, code):
        frame = L2capMatchers._control_frame(packet)
        if frame is None or frame.GetCode() != code:
            return None
        return frame

    @staticmethod
    def le_control_frame_with_code(packet, code):
        frame = L2capMatchers._le_control_frame(packet)
        if frame is None or frame.GetCode() != code:
            return None
        return frame

    @staticmethod
    def _is_control_frame_with_code(packet, code):
        return L2capMatchers.control_frame_with_code(packet, code) is not None

    @staticmethod
    def _is_le_control_frame_with_code(packet, code):
        return L2capMatchers.le_control_frame_with_code(packet, code) is not None

    @staticmethod
    def _is_matching_connection_request(packet, psm):
        frame = L2capMatchers.control_frame_with_code(packet, CommandCode.CONNECTION_REQUEST)
        if frame is None:
            return False
        request = l2cap_packets.ConnectionRequestView(frame)
        return request.GetPsm() == psm

    @staticmethod
    def _is_matching_connection_response(packet, scid):
        frame = L2capMatchers.control_frame_with_code(packet, CommandCode.CONNECTION_RESPONSE)
        if frame is None:
            return False
        response = l2cap_packets.ConnectionResponseView(frame)
        return response.GetSourceCid() == scid and response.GetResult(
        ) == ConnectionResponseResult.SUCCESS and response.GetDestinationCid() != 0

    @staticmethod
    def _is_matching_configuration_request_with_cid(packet, cid=None):
        frame = L2capMatchers.control_frame_with_code(packet, CommandCode.CONFIGURATION_REQUEST)
        if frame is None:
            return False
        request = l2cap_packets.ConfigurationRequestView(frame)
        dcid = request.GetDestinationCid()
        return cid is None or cid == dcid

    @staticmethod
    def _is_matching_configuration_request_with_ertm(packet):
        frame = L2capMatchers.control_frame_with_code(packet, CommandCode.CONFIGURATION_REQUEST)
        if frame is None:
            return False
        request = l2cap_packets.ConfigurationRequestView(frame)
        config_bytes = request.GetBytes()
        # TODO(b/153189503): Use packet struct parser.
        return b"\x04\x09\x03" in config_bytes

    @staticmethod
    def _is_matching_configuration_response(packet, result=ConfigurationResponseResult.SUCCESS):
        frame = L2capMatchers.control_frame_with_code(packet, CommandCode.CONFIGURATION_RESPONSE)
        if frame is None:
            return False
        response = l2cap_packets.ConfigurationResponseView(frame)
        return response.GetResult() == result

    @staticmethod
    def _is_matching_disconnection_request(packet, scid, dcid):
        frame = L2capMatchers.control_frame_with_code(packet, CommandCode.DISCONNECTION_REQUEST)
        if frame is None:
            return False
        request = l2cap_packets.DisconnectionRequestView(frame)
        return request.GetSourceCid() == scid and request.GetDestinationCid() == dcid

    @staticmethod
    def _is_matching_disconnection_response(packet, scid, dcid):
        frame = L2capMatchers.control_frame_with_code(packet, CommandCode.DISCONNECTION_RESPONSE)
        if frame is None:
            return False
        response = l2cap_packets.DisconnectionResponseView(frame)
        return response.GetSourceCid() == scid and response.GetDestinationCid() == dcid

    @staticmethod
    def _is_matching_le_disconnection_response(packet, scid, dcid):
        frame = L2capMatchers.le_control_frame_with_code(packet, LeCommandCode.DISCONNECTION_RESPONSE)
        if frame is None:
            return False
        response = l2cap_packets.LeDisconnectionResponseView(frame)
        return response.GetSourceCid() == scid and response.GetDestinationCid() == dcid

    @staticmethod
    def _is_matching_le_disconnection_request(packet, scid, dcid):
        frame = L2capMatchers.le_control_frame_with_code(packet, LeCommandCode.DISCONNECTION_REQUEST)
        if frame is None:
            return False
        request = l2cap_packets.LeDisconnectionRequestView(frame)
        return request.GetSourceCid() == scid and request.GetDestinationCid() == dcid

    @staticmethod
    def _is_matching_le_flow_control_credit(packet, cid):
        frame = L2capMatchers.le_control_frame_with_code(packet, LeCommandCode.LE_FLOW_CONTROL_CREDIT)
        if frame is None:
            return False
        request = l2cap_packets.LeFlowControlCreditView(frame)
        return request.GetCid() == cid

    @staticmethod
    def _information_request_with_type(packet, info_type):
        frame = L2capMatchers.control_frame_with_code(packet, CommandCode.INFORMATION_REQUEST)
        if frame is None:
            return None
        request = l2cap_packets.InformationRequestView(frame)
        if request.GetInfoType() != info_type:
            return None
        return request

    @staticmethod
    def _information_response_with_type(packet, info_type):
        frame = L2capMatchers.control_frame_with_code(packet, CommandCode.INFORMATION_RESPONSE)
        if frame is None:
            return None
        response = l2cap_packets.InformationResponseView(frame)
        if response.GetInfoType() != info_type:
            return None
        return response

    @staticmethod
    def _is_matching_information_response_extended_features(packet, supports_ertm, supports_streaming, supports_fcs,
                                                            supports_fixed_channels):
        frame = L2capMatchers._information_response_with_type(packet,
                                                              InformationRequestInfoType.EXTENDED_FEATURES_SUPPORTED)
        if frame is None:
            return False
        features = l2cap_packets.InformationResponseExtendedFeaturesView(frame)
        if supports_ertm is not None and features.GetEnhancedRetransmissionMode() != supports_ertm:
            return False
        if supports_streaming is not None and features.GetStreamingMode != supports_streaming:
            return False
        if supports_fcs is not None and features.GetFcsOption() != supports_fcs:
            return False
        if supports_fixed_channels is not None and features.GetFixedChannels() != supports_fixed_channels:
            return False
        return True

    @staticmethod
    def _is_matching_connection_parameter_update_response(packet, result):
        frame = L2capMatchers.le_control_frame_with_code(packet, LeCommandCode.CONNECTION_PARAMETER_UPDATE_RESPONSE)
        if frame is None:
            return False
        return l2cap_packets.ConnectionParameterUpdateResponseView(frame).GetResult() == result

    @staticmethod
    def _is_matching_credit_based_connection_request(packet, psm):
        frame = L2capMatchers.le_control_frame_with_code(packet, LeCommandCode.LE_CREDIT_BASED_CONNECTION_REQUEST)
        if frame is None:
            return False
        request = l2cap_packets.LeCreditBasedConnectionRequestView(frame)
        return request.GetLePsm() == psm

    @staticmethod
    def _is_matching_credit_based_connection_response(packet, result):
        frame = L2capMatchers.le_control_frame_with_code(packet, LeCommandCode.LE_CREDIT_BASED_CONNECTION_RESPONSE)
        if frame is None:
            return False
        response = l2cap_packets.LeCreditBasedConnectionResponseView(frame)
        return response.GetResult() == result and (result != LeCreditBasedConnectionResponseResult.SUCCESS or
                                                   response.GetDestinationCid() != 0)

    @staticmethod
    def LinkSecurityInterfaceCallbackEvent(type):
        return lambda event: True if event.event_type == type else False


class SecurityMatchers(object):

    @staticmethod
    def UiMsg(type, address=None):
        return lambda event: True if event.message_type == type and (address == None or address == event.peer
                                                                    ) else False

    @staticmethod
    def BondMsg(type, address=None, reason=None):
        return lambda event: True if event.message_type == type and (address == None or address == event.peer) and (
            reason == None or reason == event.reason) else False

    @staticmethod
    def HelperMsg(type, address=None):
        return lambda event: True if event.message_type == type and (address == None or address == event.peer
                                                                    ) else False


class IsoMatchers(object):

    @staticmethod
    def Data(payload):
        return lambda packet: packet.payload == payload

    @staticmethod
    def PacketPayloadWithMatchingCisHandle(cis_handle):
        return lambda packet: None if cis_handle != packet.handle else packet
