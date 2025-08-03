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
"""OPP proxy module."""

from typing import Optional

from mmi2grpc._helpers import assert_description
from mmi2grpc._proxy import ProfileProxy

from pandora.host_grpc import Host
from pandora.host_pb2 import Connection
from pandora_experimental.opp_grpc import Opp


class OPPProxy(ProfileProxy):
    """OPP proxy.

    Implements OPP PTS MMIs.
    """
    connection: Optional[Connection] = None

    def __init__(self, channel):
        super().__init__(channel)

        self.host = Host(channel)
        self.opp = Opp(channel)

        self.connection = None

    @assert_description
    def TSC_OBEX_MMI_iut_accept_connect_OPP(self, pts_addr: bytes, **kwargs):
        """
        Please accept the OBEX CONNECT REQ command for OPP.
        """
        if self.connection is None:
            self.connection = self.host.WaitConnection(address=pts_addr).connection

        return "OK"

    @assert_description
    def TSC_OPP_mmi_user_action_remove_object(self, **kwargs):
        """
        If necessary take action to remove any file(s) named 'BC_BV01.bmp' from
        the IUT.

        Press 'OK' to confirm that the file is not present on the
        IUT.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_put(self, **kwargs):
        """
        Please accept the PUT REQUEST.
        """
        self.opp.AcceptPutOperation()

        return "OK"

    @assert_description
    def TSC_OPP_mmi_user_verify_does_object_exist(self, **kwargs):
        """
        Does the IUT now contain the following files?

        BC_BV01.bmp

        Note: If
        TSPX_supported_extension is not .bmp, the file content of the file will
        not be formatted for the TSPX_supported extension, this is normal.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_slc_connect_l2cap(self, pts_addr: bytes, **kwargs):
        """
        Please accept the l2cap channel connection for an OBEX connection.
        """
        self.connection = self.host.WaitConnection(address=pts_addr).connection

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_reject_action(self, **kwargs):
        """
        Take action to reject the ACTION command sent by PTS.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_disconnect(self, **kwargs):
        """
        Please accept the OBEX DISCONNECT REQ command.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_slc_disconnect(self, **kwargs):
        """
        Please accept the disconnection of the transport channel.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_initiate_slc_connect_rfcomm(self, pts_addr: bytes, **kwargs):
        """
        Take action to create an rfcomm channel for an OBEX connection.
        """

        self.opp.OpenRfcommChannel(address=pts_addr)

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_initiate_slc_connect_l2cap(self, pts_addr: bytes, **kwargs):
        """
        Take action to create an l2cap channel for an OBEX connection.
        """

        self.opp.OpenL2capChannel(address=pts_addr)

        return "OK"

    @assert_description
    def TSC_OPP_mmi_user_verify_opp_format_indication(self, **kwargs):
        """
        Does the IUT display that the tester (OPP server) supports the
        following Object Push Formats: vCards, vCal, vNote, vMsg and Other
        content

        Note: If the IUT does not support format indication, please
        press 'Yes' now.
        Note: Do not connect to the tester until requested.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_initiate_connect_OPP(self, **kwargs):
        """
        Take action to initiate an OBEX CONNECT REQ for OPP.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_initiate_put(self, **kwargs):
        """
        Take action to send a PUT request.  Then allow the operation to
        complete as normal.
        """

        return "OK"

    @assert_description
    def TSC_OPP_mmi_user_verify_client_pushed_file(self, **kwargs):
        """
        Does the file named 'x-ms-bmp' in the recently opened window represent
        the file just pushed by the IUT?
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_initiate_slc_disconnect(self, **kwargs):
        """
        Take action to disconnect the transport channel.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_initiate_disconnect(self, **kwargs):
        """
        Take action to initiate an OBEX DISCONNECT REQ.
        """

        return "OK"
