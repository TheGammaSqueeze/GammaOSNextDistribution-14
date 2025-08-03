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
"""MAP proxy module."""

from typing import Optional

from mmi2grpc._helpers import assert_description
from mmi2grpc._proxy import ProfileProxy

from pandora.host_grpc import Host
from pandora.host_pb2 import Connection
from pandora_experimental.map_grpc import Map as MapProfile
from pandora_experimental.os_grpc import Os
from pandora_experimental.os_pb2 import ACCESS_MESSAGE


class MAPProxy(ProfileProxy):
    """MAP proxy.

    Implements MAP PTS MMIs.
    """

    connection: Optional[Connection] = None

    def __init__(self, channel):
        super().__init__(channel)

        self.host = Host(channel)
        self.os = Os(channel)
        self.map_profile = MapProfile(channel)

        self.connection = None
        self._init_send_sms()

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

        self.os.SetAccessPermission(address=pts_addr, access_type=ACCESS_MESSAGE)
        self.connection = self.host.WaitConnection(address=pts_addr).connection

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_connect(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please accept the OBEX CONNECT REQ.
        """

        if test in {"MAP/MSE/GOEP/BC/BV-01-I", "MAP/MSE/GOEP/BC/BV-03-I", "MAP/MSE/MMN/BV-02-I"}:
            if self.connection is None:
                self.os.SetAccessPermission(address=pts_addr, access_type=ACCESS_MESSAGE)
                self.connection = self.host.WaitConnection(address=pts_addr).connection

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_initiate_slc_connect(self, **kwargs):
        """
        Take action to create an l2cap channel or rfcomm channel for an OBEX
        connection.

        Note:
        Service Name: MAP-MNS
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_initiate_connect_MAP(self, **kwargs):
        """
        Take action to initiate an OBEX CONNECT REQ for MAP.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_initiate_disconnect(self, **kwargs):
        """
        Take action to initiate an OBEX DISCONNECT REQ.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_disconnect(self, **kwargs):
        """
        Please accept the OBEX DISCONNECT REQ command.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_set_path(self, **kwargs):
        """
        Please accept the SET_PATH command.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_get_srm(self, **kwargs):
        """
        Please accept the GET REQUEST with an SRM ENABLED header.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_accept_browse_folders(self, **kwargs):
        """
        Please accept the browse folders (GET) command.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_set_event_message_MessageRemoved_request(self, **kwargs):
        """
        Send Set Event Report with MessageRemoved Message.
        """
        return "OK"

    @assert_description
    def TSC_MMI_iut_verify_message_have_send(self, **kwargs):
        """
        Verify that the message has been successfully delivered via the network,
        then click OK.  Otherwise click Cancel.
        """

        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_reject_action(self, **kwargs):
        """
        Take action to reject the ACTION command sent by PTS.
        """
        return "OK"

    @assert_description
    def TSC_MMI_iut_send_set_event_message_gsm_request(self, **kwargs):
        """
        Send Set Event Report with New GSM Message.
        """

        self.map_profile.SendSMS()

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_set_event_1_2_request(self, **kwargs):
        """
        Send 1.2 Event Report .
        """
        return "OK"

    @assert_description
    def TSC_OBEX_MMI_iut_reject_session(self, **kwargs):
        """
        Take action to reject the SESSION command sent by PTS.
        """

        return "OK"

    def _init_send_sms(self):

        min_sms_count = 2  # Few test cases requires minimum 2 sms to pass
        for _ in range(min_sms_count):
            self.map_profile.SendSMS()
