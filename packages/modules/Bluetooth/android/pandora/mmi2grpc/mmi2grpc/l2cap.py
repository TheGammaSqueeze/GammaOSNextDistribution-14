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

import time
import sys

from mmi2grpc._helpers import assert_description
from mmi2grpc._helpers import match_description
from mmi2grpc._proxy import ProfileProxy
from mmi2grpc._rootcanal import Dongle

from pandora.host_grpc import Host
from pandora.host_pb2 import PUBLIC, RANDOM, Connection
from pandora.security_pb2 import PairingEventAnswer
from pandora.security_grpc import Security
from pandora_experimental.l2cap_grpc import L2CAP

from typing import Optional


class L2CAPProxy(ProfileProxy):
    test_status_map = {}  # record test status and pass them between MMI
    LE_DATA_PACKET_LARGE = "data: LE_DATA_PACKET_LARGE"
    LE_DATA_PACKET1 = "data: LE_PACKET1"
    connection: Optional[Connection] = None

    def __init__(self, channel, rootcanal):
        super().__init__(channel)
        self.l2cap = L2CAP(channel)
        self.host = Host(channel)
        self.security = Security(channel)
        self.rootcanal = rootcanal

        self.connection = None
        self.pairing_events = None

    def test_started(self, test: str, **kwargs):
        self.rootcanal.select_pts_dongle(Dongle.CSR_RCK_PTS_DONGLE)

        return "OK"

    @assert_description
    def MMI_IUT_SEND_LE_CREDIT_BASED_CONNECTION_REQUEST(self, test: str, pts_addr: bytes, **kwargs):
        """
        Using the Implementation Under Test (IUT), send a LE Credit based
        connection request to PTS.

        Description: Verify that IUT can setup LE
        credit based channel.
        """

        tests_target_to_fail = [
            'L2CAP/LE/CFC/BV-01-C',
            'L2CAP/LE/CFC/BV-04-C',
            'L2CAP/LE/CFC/BV-10-C',
            'L2CAP/LE/CFC/BV-11-C',
            'L2CAP/LE/CFC/BV-12-C',
            'L2CAP/LE/CFC/BV-14-C',
            'L2CAP/LE/CFC/BV-16-C',
            'L2CAP/LE/CFC/BV-18-C',
            'L2CAP/LE/CFC/BV-19-C',
            "L2CAP/LE/CFC/BV-21-C",
        ]
        tests_require_secure_connection = []

        # This MMI is called twice in 'L2CAP/LE/CFC/BV-04-C'
        # We are not sure whether the lower tester’s BluetoothServerSocket
        # will be closed after first connection is established.
        # Based on what we find, the first connection request is successful,
        # but the 2nd connection fails.
        # In PTS real world test, the system asks the human tester
        # whether it is connected. The human tester will press “Yes” twice.
        # So we use a counter to return “OK” for the 2nd call.
        if self.connection and test == 'L2CAP/LE/CFC/BV-02-C':
            return "OK"

        assert self.connection is None, f"the connection should be None for the first call"

        self.connection = next(self.advertise).connection

        psm = 0x25  # default TSPX_spsm value
        if test == 'L2CAP/LE/CFC/BV-04-C':
            psm = 0xF1  # default TSPX_psm_unsupported value
        if test == 'L2CAP/LE/CFC/BV-10-C':
            psm = 0xF2  # default TSPX_psm_authentication_required value
        if test == 'L2CAP/LE/CFC/BV-12-C':
            psm = 0xF3  # default TSPX_psm_authorization_required value

        secure_connection = test in tests_require_secure_connection

        try:
            self.l2cap.CreateLECreditBasedChannel(connection=self.connection, psm=psm, secure=secure_connection)
        except Exception as e:
            if test in tests_target_to_fail:
                self.test_status_map[test] = 'OK'
                print(test, 'target to fail', file=sys.stderr)
                return "OK"
            else:
                print(test, 'CreateLECreditBasedChannel failed', e, file=sys.stderr)
                raise e

        return "OK"

    @assert_description
    def MMI_TESTER_ENABLE_LE_CONNECTION(self, test: str, **kwargs):
        """
        Place the IUT into LE connectable mode.
        """
        self.advertise = self.host.Advertise(
            legacy=True,
            connectable=True,
            own_address_type=PUBLIC,
        )
        # not strictly necessary, but can save time on waiting connection
        tests_to_open_bluetooth_server_socket = [
            "L2CAP/COS/CFC/BV-01-C",
            "L2CAP/COS/CFC/BV-02-C",
            "L2CAP/COS/CFC/BV-03-C",
            "L2CAP/COS/CFC/BV-04-C",
            "L2CAP/LE/CFC/BV-03-C",
            "L2CAP/LE/CFC/BV-05-C",
            "L2CAP/LE/CFC/BV-06-C",
            "L2CAP/LE/CFC/BV-09-C",
            "L2CAP/LE/CFC/BV-13-C",
            "L2CAP/LE/CFC/BV-20-C",
            "L2CAP/LE/CFC/BI-01-C",
        ]
        tests_require_secure_connection = [
            "L2CAP/LE/CFC/BV-13-C",
        ]
        tests_connection_target_to_failed = [
            "L2CAP/LE/CFC/BV-05-C",
        ]

        if test in tests_to_open_bluetooth_server_socket:
            secure_connection = test in tests_require_secure_connection
            self.l2cap.ListenL2CAPChannel(connection=self.connection, secure=secure_connection)
            try:
                self.l2cap.AcceptL2CAPChannel(connection=self.connection)
            except Exception as e:
                if test in tests_connection_target_to_failed:
                    self.test_status_map[test] = 'OK'
                    print(test, 'connection targets to fail', file=sys.stderr)
                else:
                    raise e
        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_SEND_LE_DATA_PACKET_LARGE(self, **kwargs):
        """
        Upper Tester command IUT to send LE data packet(s) to the PTS.
        Description : The Implementation Under Test(IUT) should send multiple LE
        frames of LE data to PTS.
        """
        # NOTES: the data packet is made to be only 1 frame because of the
        # undeterministic behavior of the PTS-bot
        # this happened on "L2CAP/LE/CFC/BV-03-C"
        # when multiple frames are used, sometimes pass, sometimes fail
        # the PTS said "Failed to receive L2CAP data", but snoop log showed
        # all data frames arrived
        # it seemed like when the time gap between the 1st frame and 2nd frame
        # larger than 100ms this problem will occur
        self.l2cap.SendData(connection=self.connection, data=bytes(self.LE_DATA_PACKET_LARGE, "utf-8"))
        return "OK"

    @match_description
    def MMI_UPPER_TESTER_CONFIRM_LE_DATA(self, sent_data: str, test: str, **kwargs):
        """
        Did the Upper Tester send the data (?P<sent_data>[0-9A-F]*) to to the
        PTS\? Click Yes if it matched, otherwise click No.

        Description: The Implementation Under Test
        \(IUT\) send data is receive correctly in the PTS.
        """
        if test == 'L2CAP/COS/CFC/BV-02-C':
            hex_LE_DATA_PACKET = self.LE_DATA_PACKET1.encode("utf-8").hex().upper()
        else:
            hex_LE_DATA_PACKET = self.LE_DATA_PACKET_LARGE.encode("utf-8").hex().upper()
        if sent_data != hex_LE_DATA_PACKET:
            print(f"data not match, sent_data:{sent_data} and {hex_LE_DATA_PACKET}", file=sys.stderr)
            raise Exception(f"data not match, sent_data:{sent_data} and {hex_LE_DATA_PACKET}")
        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_SEND_LE_DATA_PACKET4(self, **kwargs):
        """
        Upper Tester command IUT to send at least 4 frames of LE data packets to
        the PTS.
        """
        self.l2cap.SendData(
            connection=self.connection,
            data=b"this is a large data package with at least 4 frames: MMI_UPPER_TESTER_SEND_LE_DATA_PACKET_LARGE")
        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_SEND_LE_DATA_PACKET_CONTINUE(self, **kwargs):
        """
        IUT continue to send LE data packet(s) to the PTS.
        """
        self.l2cap.SendData(
            connection=self.connection,
            data=b"this is a large data package with at least 4 frames: MMI_UPPER_TESTER_SEND_LE_DATA_PACKET_LARGE")
        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_CONFIRM_RECEIVE_COMMAND_NOT_UNDERSTAOOD(self, test: str, **kwargs):
        """
        Did Implementation Under Test(IUT) receive L2CAP Reject with 'command
        not understood' error?
        Click Yes if it is, otherwise click No.
        Description : Verify that after receiving the Command Reject from the
        Lower Tester, the IUT inform the Upper Tester.
        """
        if self.test_status_map[test] != "OK":
            print('error in MI_UPPER_TESTER_CONFIRM_RECEIVE_COMMAND_NOT_UNDERSTAOOD', file=sys.stderr)
            raise Exception("Unexpected RECEIVE_COMMAND")
        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_CONFIRM_DATA_RECEIVE(self, **kwargs):
        """
        Please confirm the Upper Tester receive data
        """
        data = self.l2cap.ReceiveData(connection=self.connection)
        assert data, "data received should not be empty"
        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_PSM(self, test: str, **kwargs):
        """
        Did Implementation Under Test(IUT) receive Request Reject with 'LE_PSM
        not supported' 0x0002 error.Click Yes if it is, otherwise click No.
        Description : Verify that after receiving the Credit Based Connection
        Request reject from the Lower Tester, the IUT inform the Upper Tester.
        """
        if self.test_status_map[test] != "OK":
            print('error in MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_PSM', file=sys.stderr)
            raise Exception("Unexpected RECEIVE_COMMAND")
        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_AUTHENTICATION(self, test: str, **kwargs):
        """
        Did Implementation Under Test(IUT) receive Connection refused
        'Insufficient Authentication' 0x0005 error?

        Click Yes if IUT received
        it, otherwise click NO.

        Description: Verify that after receiving the
        Credit Based Connection Request Refused With No Resources error from the
        Lower Tester, the IUT informs the Upper Tester.
        """
        if self.test_status_map[test] != "OK":
            print('error in MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_AUTHENTICATION', file=sys.stderr)
            raise Exception("Unexpected RECEIVE_COMMAND")
        return "OK"

    @assert_description
    def _mmi_135(self, test: str, **kwargs):
        """
        Please make sure an authentication requirement exists for a channel
        L2CAP.
        When receiving Credit Based Connection Request from PTS, please
        respond with Result 0x0005 (Insufficient Authentication)
        """
        return self.MMI_IUT_SEND_INSUFFICIENT_AUTHENTICATION_ON_LE(test, **kwargs)

    @assert_description
    def MMI_IUT_SEND_INSUFFICIENT_AUTHENTICATION_ON_LE(self, test: str, **kwargs):
        """
        Please make sure an authentication requirement exists for a channel
        L2CAP.
        When receiving Credit Based Connection Request from PTS, please
        respond with Result 0x0005 (Insufficient Authentication)
        """
        if self.test_status_map[test] != "OK":
            print('error in _mmi_135', file=sys.stderr)
            raise Exception("Unexpected RECEIVE_COMMAND")
        return "OK"

    @assert_description
    def _mmi_136(self, **kwargs):
        """
        Please make sure an authorization requirement exists for a channel
        L2CAP.
        When receiving Credit Based Connection Request from PTS, please
        respond with Result 0x0006 (Insufficient Authorization)
        """
        return self.MMI_IUT_SEND_INSUFFICIENT_AUTHORIZATION_ON_LE(**kwargs)

    @assert_description
    def MMI_IUT_SEND_INSUFFICIENT_AUTHORIZATION_ON_LE(self, **kwargs):
        """
        Please make sure an authorization requirement exists for a channel
        L2CAP.
        When receiving Credit Based Connection Request from PTS, please
        respond with Result 0x0006 (Insufficient Authorization)
        """
        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_AUTHORIZATION(self, test: str, **kwargs):
        """
        Did Implementation Under Test(IUT) receive Connection refused
        'Insufficient Authorization' 0x0006 error?

        Click Yes if IUT received
        it, otherwise click NO.

        Description: Verify that after receiving the
        Credit Based Connection Request Refused With No Resources error from the
        Lower Tester, the IUT informs the Upper Tester.
        """
        if self.test_status_map[test] != "OK":
            print('error in MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_AUTHORIZATION', file=sys.stderr)
            raise Exception("Unexpected RECEIVE_COMMAND")
        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_ENCRYPTION_KEY_SIZE(self, test: str, **kwargs):
        """
        Did Implementation Under Test(IUT) receive Connection refused
        'Insufficient Encryption Key Size' 0x0007 error?

        Click Yes if IUT
        received it, otherwise click NO.

        Description: Verify that after
        receiving the Credit Based Connection Request Refused With No Resources
        error from the Lower Tester, the IUT informs the Upper Tester.
        """
        if self.test_status_map[test] != "OK":
            print('error in MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_ENCRYPTION_KEY_SIZE', file=sys.stderr)
            raise Exception("Unexpected RECEIVE_COMMAND")
        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_INVALID_SOURCE_CID(self, test: str, **kwargs):
        """
        Did Implementation Under Test(IUT) receive Connection refused 'Invalid
        Source CID' 0x0009 error? And does not send anything over refuse LE data
        channel? Click Yes if it is, otherwise click No.
        Description : Verify
        that after receiving the Credit Based Connection Request refused with
        Invalid Source CID error from the Lower Tester, the IUT inform the Upper
        Tester.
        """
        if self.test_status_map[test] != "OK":
            print('error in MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_INVALID_SOURCE_CID', file=sys.stderr)
            raise Exception("Unexpected RECEIVE_COMMAND")
        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_SOURCE_CID_ALREADY_ALLOCATED(self, test: str, **kwargs):
        """
        Did Implementation Under Test(IUT) receive Connection refused 'Source
        CID Already Allocated' 0x000A error? And did not send anything over
        refuse LE data channel.Click Yes if it is, otherwise click No.
        Description : Verify that after receiving the Credit Based Connection
        Request refused with Source CID Already Allocated error from the Lower
        Tester, the IUT inform the Upper Tester.
        """
        if self.test_status_map[test] != "OK":
            print('error in MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_SOURCE_CID_ALREADY_ALLOCATED', file=sys.stderr)
            raise Exception("Unexpected RECEIVE_COMMAND")
        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_UNACCEPTABLE_PARAMETERS(self, test: str, **kwargs):
        """
        Did Implementation Under Test(IUT) receive Connection refused
        'Unacceptable Parameters' 0x000B error? Click Yes if it is, otherwise
        click No.
        Description: Verify that after receiving the Credit Based
        Connection Request refused with Unacceptable Parameters error from the
        Lower Tester, the IUT inform the Upper Tester.
        """
        if self.test_status_map[test] != "OK":
            print('error in MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_UNACCEPTABLE_PARAMETERS', file=sys.stderr)
            raise Exception("Unexpected RECEIVE_COMMAND")
        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_RESOURCES(self, test: str, **kwargs):
        """
        Did Implementation Under Test(IUT) receive Connection refused
        'Insufficient Resources' 0x0004 error? Click Yes if it is, otherwise
        click No.
        Description : Verify that after receiving the Credit Based
        Connection Request refused with No resources error from the Lower
        Tester, the IUT inform the Upper Tester.
        """
        if self.test_status_map[test] != "OK":
            print('error in MMI_UPPER_TESTER_CONFIRM_RECEIVE_REJECT_RESOURCES', file=sys.stderr)
            raise Exception("Unexpected RECEIVE_COMMAND")
        return "OK"

    def MMI_IUT_ENABLE_LE_CONNECTION(self, pts_addr: bytes, **kwargs):
        """
        Initiate or create LE ACL connection to the PTS.
        """
        self.connection = self.host.ConnectLE(own_address_type=RANDOM, public=pts_addr).connection
        return "OK"

    @assert_description
    def MMI_IUT_SEND_ACL_DISCONNECTION(self, test: str, **kwargs):
        """
        Initiate an ACL disconnection from the IUT to the PTS.
        Description :
        The Implementation Under Test(IUT) should disconnect ACL channel by
        sending a disconnect request to PTS.
        """
        # Skipping disconnect for below test cases as host.disconnect grpc is not concluded (there is no profile disconnection).
        tests_to_skip_disconnect = [
            "L2CAP/COS/CED/BV-01-C",
            "L2CAP/COS/CED/BV-04-C",
            "L2CAP/COS/CED/BV-09-C",
            "L2CAP/COS/CFD/BV-08-C",
            "L2CAP/CMC/BI-05-C",
            "L2CAP/CMC/BI-06-C",
        ]
        if test not in tests_to_skip_disconnect:
            self.host.Disconnect(connection=self.connection)

        return "OK"

    def MMI_TESTER_ENABLE_CONNECTION(self, **kwargs):
        """
        Action: Place the IUT in connectable mode.

        Description: PTS requires that the IUT be in connectable mode.
        The PTS will attempt to establish an ACL connection.
        """

        return "OK"

    @assert_description
    def MMI_IUT_INITIATE_ACL_CONNECTION(self, pts_addr: bytes, **kwargs):
        """
        Using the Implementation Under Test(IUT), initiate ACL Create Connection
        Request to the PTS.

        Description : The Implementation Under Test(IUT)
        should create ACL connection request to PTS.
        """
        self.pairing_events = self.security.OnPairing()
        self.connection = self.host.Connect(address=pts_addr).connection
        return "OK"

    @assert_description
    def _mmi_2001(self, **kwargs):
        """
        Please verify the passKey is correct: 000000
        """
        passkey = "000000"
        for event in self.pairing_events:
            if event.numeric_comparison == int(passkey):
                self.pairing_events.send(PairingEventAnswer(event=event, confirm=True))
                return "OK"
            assert False, "The passkey does not match"
        assert False, "Unexpected pairing event"

    @assert_description
    def MMI_IUT_SEND_CONFIG_REQ(self, **kwargs):
        """
        Please send Configure Request.
        """

        return "OK"

    @assert_description
    def MMI_IUT_SEND_CONFIG_RSP(self, **kwargs):
        """
        Please send Configure Response.
        """

        return "OK"

    @assert_description
    def MMI_IUT_SEND_DISCONNECT_RSP(self, **kwargs):
        """
        Please send L2CAP Disconnection Response to PTS.
        """

        return "OK"

    @assert_description
    def MMI_UPPER_TESTER_SEND_LE_DATA_PACKET1(self, **kwargs):
        """
        Upper Tester command IUT to send a non-segmented LE data packet to the
        PTS with any values.
         Description : The Implementation Under Test(IUT)
        should send none segmantation LE frame of LE data to the PTS.
        """
        self.l2cap.SendData(connection=self.connection, data=bytes(self.LE_DATA_PACKET1, "utf-8"))
        return "OK"

    @assert_description
    def MMI_IUT_SEND_L2CAP_DATA(self, **kwargs):
        """
        Using the Implementation Under Test(IUT), send L2CAP_Data over the
        assigned channel with correct DCID to the PTS.
        """

        return "OK"

    @assert_description
    def MMI_IUT_DISABLE_CONNECTION(self, **kwargs):
        """
         Initiate an L2CAP disconnection from the IUT to the PTS.

        Description :
        The Implementation Under Test(IUT) should disconnect the active L2CAP
        channel by sending a disconnect request to PTS.
        """

        return "OK"

    @assert_description
    def MMI_IUT_SEND_CONFIGURE_CONNECTION_ACCORDING_TO_FEATURE(self, **kwargs):
        """
        Please initiate Information Request procedure to discover supported
        features and configure connection.
        """

        return "OK"

    @assert_description
    def MMI_IUT_REPORT_ERROR(self, **kwargs):
        """
        Did the Implementation Under Test(IUT) inform the Upper Tester the
        connection attempt failed?
        """

        return "OK"

    @assert_description
    def MMI_IUT_SEND_L2CAP_CONNECTION_REQ(self, **kwargs):
        """
        Please send L2CAP Connection REQ to PTS.
        """

        return "OK"

    @assert_description
    def MMI_CONFIRM_UPPER_TESTER_DOES_NOT_RECEIVE_DATA(self, **kwargs):
        """
        Please confirm the IUT does not send the L2CAP Data to the Upper Tester.
        Click Yes if the IUT is not sending data to the Upper Tester. Otherwise
        click No if it is.
        """

        return "OK"
