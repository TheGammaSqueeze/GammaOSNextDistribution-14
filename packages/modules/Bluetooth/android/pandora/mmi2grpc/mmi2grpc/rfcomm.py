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
"""Rfcomm proxy module."""

from mmi2grpc._helpers import assert_description
from mmi2grpc._proxy import ProfileProxy

from pandora_experimental.rfcomm_grpc import RFCOMM
from pandora.host_grpc import Host

import sys
import threading
import os
import socket


class RFCOMMProxy(ProfileProxy):

    # The UUID for Serial-Port Profile
    SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"
    # TSPX_SERVICE_NAME_TESTER
    SERVICE_NAME = "COM5"

    def __init__(self, channel: str):
        super().__init__(channel)
        self.rfcomm = RFCOMM(channel)
        self.host = Host(channel)
        self.server = None
        self.connection = None

    @assert_description
    def TSC_RFCOMM_mmi_iut_initiate_slc(self, pts_addr: bytes, test: str, **kwargs):
        """
        Take action to initiate an RFCOMM service level connection (l2cap).
        """

        try:
            self.connection = self.rfcomm.ConnectToServer(address=pts_addr, uuid=self.SPP_UUID).connection
        except Exception as e:
            if test == "RFCOMM/DEVA/RFC/BV-01-C":
                print(f'{test}: PTS disconnected as expected', file=sys.stderr)
                return "OK"
            else:
                print(f'{test}: PTS disconnected unexpectedly', file=sys.stderr)
                raise e
        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_accept_slc(self, pts_addr: bytes, **kwargs):
        """
        Take action to accept the RFCOMM service level connection from the
        tester.
        """

        self.server = self.rfcomm.StartServer(uuid=self.SPP_UUID, name=self.SERVICE_NAME).server

        self.host.WaitConnection(address=pts_addr)

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_accept_sabm(self, **kwargs):
        """
        Take action to accept the SABM operation initiated by the tester.

        Note:
        Make sure that the RFCOMM server channel is set correctly in
        TSPX_server_channel_iut
        """

        self.connection = self.rfcomm.AcceptConnection(server=self.server).connection
        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_respond_PN(self, **kwargs):
        """
        Take action to respond PN.
        """

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_initiate_sabm_control_channel(self, **kwargs):
        """
        Take action to initiate an SABM operation for the RFCOMM control
        channel.
        """

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_initiate_PN(self, **kwargs):
        """
        Take action to initiate PN.
        """

        return "OK"

    def TSC_RFCOMM_mmi_iut_initiate_sabm_data_channel(self, **kwargs):
        """
        Take action to initiate an SABM operation for an RFCOMM data channel.
        Note: RFCOMM server channel can be found on PTS's SDP record
        """

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_accept_disc(self, **kwargs):
        """
        Take action to accept the DISC operation initiated by the tester.
        """

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_accept_data_link_connection(self, **kwargs):
        """
        Take action to accept a new DLC initiated by the tester.
        """

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_initiate_close_session(self, **kwargs):
        """
        Take action to close the RFCOMM session.
        """

        self.rfcomm.Disconnect(connection=self.connection)

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_respond_RLS(self, **kwargs):
        """
        Take action to respond RLS command.
        """

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_initiate_MSC(self, **kwargs):
        """
        Take action to initiate MSC command.
        """

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_respond_RPN(self, **kwargs):
        """
        Take action to respond RPN.
        """

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_respond_NSC(self, **kwargs):
        """
        Take action to respond NSC.
        """

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_initiate_close_dlc(self, **kwargs):
        """
        Take action to close the DLC.
        """

        self.rfcomm.Disconnect(connection=self.connection)

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_respond_Test(self, **kwargs):
        """
        Take action to respond Test.
        """

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_respond_MSC(self, **kwargs):
        """
        Take action to respond MSC.
        """

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_send_data(self, **kwargs):
        """
        Take action to send data on the open DLC on PTS with at least 2 frames.
        """

        self.rfcomm.Send(connection=self.connection, data=b'Some data to send')
        self.rfcomm.Send(connection=self.connection, data=b'More data to send')
        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_user_wait_no_uih_data(self, **kwargs):
        """
        Please wait while the tester confirms no data is sent ...
        """

        return "OK"

    @assert_description
    def TSC_RFCOMM_mmi_iut_initiate_RLS_framing_error(self, **kwargs):
        """
        Take action to initiate RLS command with Framing Error status.
        """

        return "OK"
