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

import re
import sys

from mmi2grpc._helpers import assert_description
from mmi2grpc._proxy import ProfileProxy

from pandora.host_grpc import Host
from pandora_experimental.pan_grpc import PAN
from pandora_experimental.os_grpc import Os

# IP address of PTS
TSPX_PTS_IP_ADDRESS = "192.168.168.100"


class PANProxy(ProfileProxy):

    def __init__(self, channel):
        super().__init__(channel)
        self.host = Host(channel)
        self.pan = PAN(channel)
        self.os = Os(channel)

    def TSC_BNEP_mmi_iut_accept_transport(self, pts_addr: bytes, **kwargs):
        """
        Take action to accept the PAN transport from the tester.

        Note: The IUT
        must accept the Basic L2cap configuration for this test case.
        """

        # Only accepting pairing here.
        self.pan.EnableTethering()
        self.host.WaitConnection(address=pts_addr)

        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_accept_setup(self, pts_addr: bytes, **kwargs):
        """
        Take action to accept setup connection.
        """

        return "OK"

    def TSC_BNEP_mmi_iut_initiate_transport(self, test: str, pts_addr: bytes, **kwargs):
        """
        Take action to initiate an PAN transport .

        Note: The IUT must require
        Basic L2cap configuration for this test case.
        """

        self.host.Connect(address=pts_addr)
        if test in "BNEP/CTRL/BV-02-C":
            self.pan.ConnectPan(address=pts_addr)

        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_initiate_setup(self, **kwargs):
        """
        Take action to initiate setup connection
        """

        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_accept_unknown(self, **kwargs):
        """
        Take action to response to reserve control message
        """
        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_accept_filter_network(self, **kwargs):
        """
        Take action to accept filter network.
        """
        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_accept_filter_multicast(self, **kwargs):
        """
        Take action to accept filter multicast.
        """
        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_confirm_general_ethernet(self, **kwargs):
        """
        Please confirm IUT received general ethernet request.
        """
        return "OK"

    @assert_description
    def TSC_PAN_mmi_iut_send_arp_probe_request(self, **kwargs):
        """
        Take action to send ARP probe request for the process of choosing a
        valid LINKLOCAL IP address.

        Notes:
        (1) It may be necessary to clear
        the assigned IP on the IUT first in order to trigger ARP request.
        (2)
        PTS anticipates an ARP request which has the destination protocol
        address field matching the value set in TSPX_iut_ip_address.
        """

        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_accept_general_ethernet(self, **kwargs):
        """
        Take action to accept general ethernet.
        """

        return "OK"

    @assert_description
    def TSC_PAN_mmi_iut_dhcp_discovery_request(self, **kwargs):
        """
        Take action to send dhcp discovery request
        """

        return "OK"

    @assert_description
    def TSC_PAN_mmi_iut_icmp_echo_reply(self, **kwargs):
        """
        Take action to respond with ICMP echo reply
        """

        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_initiate_general_ethernet(self, **kwargs):
        """
        Take action to initiate general ethernet
        """

        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_initiate_compressed_ethernet_dest(self, **kwargs):
        """
        Take action to initiate compressed ethernet destination
        """

        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_confirm_compressed_ethernet_dest(self, **kwargs):
        """
        Please confirm IUT received compressed ethernet destination request.
        """

        return "OK"

    @assert_description
    def TSC_PAN_mmi_iut_dhcp_request_request(self, **kwargs):
        """
        Take action to send dhcp request
        """

        return "OK"

    @assert_description
    def TSC_PAN_mmi_confirm_ip_address_configured_from_DHCP(self, **kwargs):
        """
        Click OK if the IUT has configured a new IP address assigned by the DHCP
        server.

        Note: If IUT is able to handle multiple IP addresses, any
        active IP connections may be maintained. If IUT is able to handle one
        single IP address at the time any active applications SHALL be
        terminated.
        """

        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_initiate_compressed_ethernet_source(self, **kwargs):
        """
        Take action to initiate compressed ethernet source
        """

        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_confirm_compressed_ethernet_source(self, **kwargs):
        """
        Please confirm IUT received compressed ethernet source request.
        """

        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_initiate_compressed_ethernet(self, **kwargs):
        """
        Take action to initiate compressed ethernet
        """

        return "OK"

    @assert_description
    def TSC_BNEP_mmi_iut_confirm_compressed_ethernet(self, **kwargs):
        """
        Please confirm IUT received compressed ethernet request.
        """

        return "OK"

    @assert_description
    def TSC_PAN_mmi_confirm_linklocal_ip_address_selected(self, **kwargs):
        """
        Click OK if the IUT has selected a LINKLOCAL IP address:
        """

        return "OK"

    @assert_description
    def TSC_PAN_mmi_iut_icmp_echo_request(self, **kwargs):
        """
        Take action to send ICMP echo request
        """

        self.os.SendPing(ip_address=TSPX_PTS_IP_ADDRESS)

        return "OK"

    @assert_description
    def TSC_PAN_mmi_iut_receive_icmp_echo_reply(self, **kwargs):
        """
         Has the IUT received the ICMP echo reply from PTS?
        """

        return "OK"

    @assert_description
    def TSC_PAN_mmi_iut_send_dns_request(self, **kwargs):
        """
        Take action to send DNS request
        """

        return "OK"
