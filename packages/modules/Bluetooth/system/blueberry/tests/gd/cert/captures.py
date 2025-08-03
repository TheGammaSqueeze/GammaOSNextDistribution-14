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
from bluetooth_packets_python3 import l2cap_packets
from bluetooth_packets_python3.l2cap_packets import CommandCode, LeCommandCode
from blueberry.tests.gd.cert.capture import Capture
from blueberry.tests.gd.cert.matchers import HciMatchers
from blueberry.tests.gd.cert.matchers import L2capMatchers
from blueberry.tests.gd.cert.matchers import SecurityMatchers
from blueberry.facade.security.facade_pb2 import UiMsgType
import hci_packets as hci


class HalCaptures(object):

    @staticmethod
    def ReadBdAddrCompleteCapture():
        return Capture(lambda packet: packet.payload[0:5] == b'\x0e\x0a\x01\x09\x10',
                       lambda packet: hci.Event.parse_all(packet.payload))

    @staticmethod
    def ConnectionRequestCapture():
        return Capture(lambda packet: packet.payload[0:2] == b'\x04\x0a',
                       lambda packet: hci.Event.parse_all(packet.payload))

    @staticmethod
    def ConnectionCompleteCapture():
        return Capture(lambda packet: packet.payload[0:3] == b'\x03\x0b\x00',
                       lambda packet: hci.Event.parse_all(packet.payload))

    @staticmethod
    def DisconnectionCompleteCapture():
        return Capture(lambda packet: packet.payload[0:2] == b'\x05\x04',
                       lambda packet: hci.Event.parse_all(packet.payload))

    @staticmethod
    def LeConnectionCompleteCapture():
        return Capture(
            lambda packet: packet.payload[0] == 0x3e and (packet.payload[2] == 0x01 or packet.payload[2] == 0x0a),
            lambda packet: hci.Event.parse_all(packet.payload))


class HciCaptures(object):

    @staticmethod
    def ReadLocalOobDataCompleteCapture():
        return Capture(
            HciMatchers.CommandComplete(hci.OpCode.READ_LOCAL_OOB_DATA),
            lambda packet: HciMatchers.ExtractMatchingCommandComplete(packet.payload, hci.OpCode.READ_LOCAL_OOB_DATA))

    @staticmethod
    def ReadLocalOobExtendedDataCompleteCapture():
        return Capture(
            HciMatchers.CommandComplete(hci.OpCode.READ_LOCAL_OOB_EXTENDED_DATA), lambda packet: HciMatchers.
            ExtractMatchingCommandComplete(packet.payload, hci.OpCode.READ_LOCAL_OOB_EXTENDED_DATA))

    @staticmethod
    def ReadBdAddrCompleteCapture():
        return Capture(HciMatchers.CommandComplete(hci.OpCode.READ_BD_ADDR),
                       lambda packet: hci.Event.parse_all(packet.payload))

    @staticmethod
    def ConnectionRequestCapture():
        return Capture(HciMatchers.EventWithCode(hci.EventCode.CONNECTION_REQUEST),
                       lambda packet: hci.Event.parse_all(packet.payload))

    @staticmethod
    def ConnectionCompleteCapture():
        return Capture(HciMatchers.EventWithCode(hci.EventCode.CONNECTION_COMPLETE),
                       lambda packet: hci.Event.parse_all(packet.payload))

    @staticmethod
    def DisconnectionCompleteCapture():
        return Capture(HciMatchers.EventWithCode(hci.EventCode.DISCONNECTION_COMPLETE),
                       lambda packet: hci.Event.parse_all(packet.payload))

    @staticmethod
    def LeConnectionCompleteCapture():
        return Capture(HciMatchers.LeConnectionComplete(),
                       lambda packet: HciMatchers.ExtractLeConnectionComplete(packet.payload))

    @staticmethod
    def SimplePairingCompleteCapture():
        return Capture(HciMatchers.EventWithCode(hci.EventCode.SIMPLE_PAIRING_COMPLETE),
                       lambda packet: hci.Event.parse_all(packet.payload))


class L2capCaptures(object):

    @staticmethod
    def ConnectionRequest(psm):
        return Capture(L2capMatchers.ConnectionRequest(psm), L2capCaptures._extract_connection_request)

    @staticmethod
    def _extract_connection_request(packet):
        frame = L2capMatchers.control_frame_with_code(packet, CommandCode.CONNECTION_REQUEST)
        return l2cap_packets.ConnectionRequestView(frame)

    @staticmethod
    def ConnectionResponse(scid):
        return Capture(L2capMatchers.ConnectionResponse(scid), L2capCaptures._extract_connection_response)

    @staticmethod
    def _extract_connection_response(packet):
        frame = L2capMatchers.control_frame_with_code(packet, CommandCode.CONNECTION_RESPONSE)
        return l2cap_packets.ConnectionResponseView(frame)

    @staticmethod
    def ConfigurationRequest(cid=None):
        return Capture(L2capMatchers.ConfigurationRequest(cid), L2capCaptures._extract_configuration_request)

    @staticmethod
    def _extract_configuration_request(packet):
        frame = L2capMatchers.control_frame_with_code(packet, CommandCode.CONFIGURATION_REQUEST)
        return l2cap_packets.ConfigurationRequestView(frame)

    @staticmethod
    def CreditBasedConnectionRequest(psm):
        return Capture(L2capMatchers.CreditBasedConnectionRequest(psm),
                       L2capCaptures._extract_credit_based_connection_request)

    @staticmethod
    def _extract_credit_based_connection_request(packet):
        frame = L2capMatchers.le_control_frame_with_code(packet, LeCommandCode.LE_CREDIT_BASED_CONNECTION_REQUEST)
        return l2cap_packets.LeCreditBasedConnectionRequestView(frame)

    @staticmethod
    def CreditBasedConnectionResponse():
        return Capture(L2capMatchers.CreditBasedConnectionResponse(),
                       L2capCaptures._extract_credit_based_connection_response)

    @staticmethod
    def _extract_credit_based_connection_response(packet):
        frame = L2capMatchers.le_control_frame_with_code(packet, LeCommandCode.LE_CREDIT_BASED_CONNECTION_RESPONSE)
        return l2cap_packets.LeCreditBasedConnectionResponseView(frame)

    @staticmethod
    def LinkSecurityInterfaceCallbackEvent(type):
        return Capture(L2capMatchers.LinkSecurityInterfaceCallbackEvent(type), L2capCaptures._extract_address)

    @staticmethod
    def _extract_address(packet):
        return packet.address


class SecurityCaptures(object):

    @staticmethod
    def DisplayPasskey():
        return Capture(SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PASSKEY), SecurityCaptures._extract_passkey)

    @staticmethod
    def _extract_passkey(event):
        if event is None:
            return None
        return event.numeric_value
