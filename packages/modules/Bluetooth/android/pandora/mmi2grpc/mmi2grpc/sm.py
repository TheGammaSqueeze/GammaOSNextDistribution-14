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
"""SMP proxy module."""
from queue import Empty, Queue
from threading import Thread
import sys
import asyncio

from mmi2grpc._helpers import assert_description, match_description
from mmi2grpc._proxy import ProfileProxy
from mmi2grpc._rootcanal import Dongle

from pandora.security_grpc import Security
from pandora.security_pb2 import LE_LEVEL3, PairingEventAnswer
from pandora.host_grpc import Host
from pandora.host_pb2 import PUBLIC, RANDOM


def debug(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)


class SMProxy(ProfileProxy):

    def __init__(self, channel, rootcanal):
        super().__init__(channel)
        self.security = Security(channel)
        self.host = Host(channel)
        self.rootcanal = rootcanal
        self.connection = None
        self.pairing_stream = None
        self.passkey_queue = Queue()
        self._handle_pairing_requests()

    def test_started(self, test: str, **kwargs):
        self.rootcanal.select_pts_dongle(Dongle.CSR_RCK_PTS_DONGLE)

        return "OK"

    @assert_description
    def MMI_IUT_ENABLE_CONNECTION_SM(self, pts_addr: bytes, **kwargs):
        """
        Initiate an connection from the IUT to the PTS.
        """
        self.connection = self.host.ConnectLE(own_address_type=RANDOM, public=pts_addr).connection
        return "OK"

    @assert_description
    def MMI_ASK_IUT_PERFORM_PAIRING_PROCESS(self, **kwargs):
        """
        Please start pairing process.
        """

        def secure():
            if self.connection:
                self.security.Secure(connection=self.connection, le=LE_LEVEL3)

        Thread(target=secure).start()
        return "OK"

    @assert_description
    def MMI_IUT_SEND_DISCONNECTION_REQUEST(self, **kwargs):
        """
        Please initiate a disconnection to the PTS.

        Description: Verify that
        the Implementation Under Test(IUT) can initiate a disconnect request to
        PTS.
        """
        self.host.Disconnect(connection=self.connection)
        self.connection = None
        return "OK"

    def MMI_LESC_NUMERIC_COMPARISON(self, **kwargs):
        """
        Please confirm the following number matches IUT: 385874.
        """
        return "OK"

    @assert_description
    def MMI_ASK_IUT_PERFORM_RESET(self, **kwargs):
        """
        Please reset your device.
        """
        self.host.Reset()
        return "OK"

    @assert_description
    def MMI_TESTER_ENABLE_CONNECTION_SM(self, **kwargs):
        """
        Action: Place the IUT in connectable mode
        """
        self.advertise = self.host.Advertise(
            legacy=True,
            connectable=True,
            own_address_type=PUBLIC,
        )

        return "OK"

    @assert_description
    def MMI_IUT_SMP_TIMEOUT_30_SECONDS(self, **kwargs):
        """
        Wait for the 30 seconds. Lower tester will not send corresponding or
        next SMP message.
        """
        return "OK"

    @assert_description
    def MMI_IUT_SMP_TIMEOUT_ADDITIONAL_10_SECONDS(self, **kwargs):
        """
        Wait for an additional 10 seconds. Lower test will send corresponding or
        next SMP message.
        """
        return "OK"

    @match_description
    def MMI_DISPLAY_PASSKEY_CODE(self, passkey: str, **kwargs):
        """
        Please enter (?P<passkey>[0-9]*) in the IUT.
        """
        self.passkey_queue.put(passkey)
        return "OK"

    @assert_description
    def MMI_ENTER_PASSKEY_CODE(self, **kwargs):
        """
        Please enter 6 digit passkey code.
        """

        return "OK"

    @assert_description
    def MMI_ENTER_WRONG_DYNAMIC_PASSKEY_CODE(self, **kwargs):
        """
        Please enter invalid 6 digit pin code.
        """

        return "OK"

    @match_description
    def MMI_IUT_ABORT_PAIRING_PROCESS_DISCONNECT(self, **kwargs):
        """
        Lower tester expects IUT aborts pairing process(, and disconnect|. Click OK to confirm pairing is aborted).
        """

        return "OK"

    @assert_description
    def MMI_IUT_ACCEPT_CONNECTION_BR_EDR(self, **kwargs):
        """
        Please prepare IUT into a connectable mode in BR/EDR.

        Description:
        Verify that the Implementation Under Test (IUT) can accept a connect
        request from PTS.
        """

        return "OK"

    @assert_description
    def _mmi_2001(self, **kwargs):
        """
        Please verify the passKey is correct: 000000
        """
        return "OK"

    @assert_description
    def MMI_IUT_INITIATE_CONNECTION_BR_EDR_PAIRING(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please initiate a connection over BR/EDR to the PTS, and initiate
        pairing process.

        Description: Verify that the Implementation Under Test
        (IUT) can initiate a connect request over BR/EDR to PTS, and initiate
        pairing process.
        """
        self.connection = self.host.Connect(address=pts_addr).connection

        return "OK"

    @assert_description
    def MMI_ASK_IUT_PERFORM_FEATURE_EXCHANGE_OVER_BR(self, **kwargs):
        """
        Please start pairing feature exchange over BR/EDR.
        """

        return "OK"

    @assert_description
    def MMI_IUT_INITIATES_ENCRYPTION(self, **kwargs):
        """
        Initiates encryption with the PTS.
        """

        return "OK"

    @assert_description
    def _mmi_20117(self, **kwargs):
        """
        Please start encryption using previously distributed key.

        Description:
        Verify that the Implementation Under Test (IUT) can successfully start
        and complete encryption with previously distributed key.
        """

        return "OK"

    def _handle_pairing_requests(self):

        def task():
            pairing_events = self.security.OnPairing()
            for event in pairing_events:
                if event.just_works or event.numeric_comparison:
                    pairing_events.send(PairingEventAnswer(event=event, confirm=True))
                if event.passkey_entry_request:
                    try:
                        passkey = self.passkey_queue.get(timeout=15)
                        pairing_events.send(PairingEventAnswer(event=event, passkey=int(passkey)))
                    except Empty:
                        debug("No passkey provided within 15 seconds")

        Thread(target=task).start()
