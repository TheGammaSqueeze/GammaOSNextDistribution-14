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
"""PBAP proxy module."""

from typing import Optional

from grpc import RpcError

from mmi2grpc._helpers import assert_description, match_description
from mmi2grpc._proxy import ProfileProxy
from pandora.host_grpc import Host
from pandora.host_pb2 import Connection
from pandora_experimental.os_grpc import Os
from pandora_experimental.os_pb2 import ACCESS_PHONEBOOK
from pandora_experimental.pbap_grpc import PBAP


class PBAPProxy(ProfileProxy):
    """PBAP proxy.

    Implements PBAP PTS MMIs.
    """

    connection: Optional[Connection] = None

    def __init__(self, channel):
        super().__init__(channel)

        self.host = Host(channel)
        self.pbap = PBAP(channel)
        self.os = Os(channel)

        self.connection = None

    @assert_description
    def TSC_MMI_iut_connectable(self, **kwargs):
        """
        Click OK when the IUT becomes connectable.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_slc_connect_l2cap(self, pts_addr: bytes, **kwargs):
        """
        Please accept the l2cap channel connection for an OBEX connection.
        """
        self.os.SetAccessPermission(address=pts_addr, access_type=ACCESS_PHONEBOOK)
        self.connection = self.host.WaitConnection(address=pts_addr).connection

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_connect(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please accept the OBEX CONNECT REQ.
        """
        if ("PBAP/PSE/GOEP/BC/BV-03-I" in test):
            if self.connection is None:
                self.os.SetAccessPermission(address=pts_addr, access_type=ACCESS_PHONEBOOK)
                self.connection = self.host.WaitConnection(address=pts_addr).connection

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_set_path(self, **kwargs):
        """
        Please accept the SET_PATH command.
        """

        return "OK"

    @assert_description
    def TSC_MMI_verify_vcard(self, **kwargs):
        """
        Verify the content vcard sent by the IUT is accurate.
        """

        return "OK"

    @match_description
    def TSC_MMI_verify_phonebook_size(self, **kwargs):
        """
        Verify that the phonebook size = (?P<size>[0-9]+)

        Note: Owner's card is also
        included in the count.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_reject_action(self, **kwargs):
        """
        Take action to reject the ACTION command sent by PTS.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_reject_session(self, **kwargs):
        """
        Take action to reject the SESSION command sent by PTS.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_get_srm(self, **kwargs):
        """
        Please accept the GET REQUEST with an SRM ENABLED header.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_disconnect(self, **kwargs):
        """
        Please accept the OBEX DISCONNECT REQ command.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_put(self, **kwargs):
        """
        Please accept the PUT REQUEST.
        """

        return "OK"

    @assert_description
    def TSC_MMI_verify_user_confirmation(self, **kwargs):
        """
        Click Ok if the Implementation Under Test (IUT) was prompted to accept
        the PBAP connection.
        """

        return "OK"

    @match_description
    def TSC_MMI_verify_newmissedcall(self, **kwargs):
        """
         Verify that the new missed calls = (?P<size>[0-9]+)
        """

        return "OK"
