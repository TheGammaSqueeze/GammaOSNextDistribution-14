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

from threading import Thread
from time import sleep
from mmi2grpc._helpers import assert_description, match_description
from mmi2grpc._proxy import ProfileProxy

from pandora_experimental.hid_grpc import HID
from pandora.host_grpc import Host
from pandora_experimental.hid_pb2 import HID_REPORT_TYPE_OUTPUT


class HIDProxy(ProfileProxy):

    def __init__(self, channel, rootcanal):
        super().__init__(channel)
        self.hid = HID(channel)
        self.host = Host(channel)
        self.rootcanal = rootcanal
        self.connection = None

    @assert_description
    def TSC_MMI_iut_enable_connection(self, pts_addr: bytes, **kwargs):
        """
        Click Ok, then using the Implementation Under Test (IUT) connect to the
        PTS.
        """

        self.rootcanal.move_in_range()
        self.connection = self.host.Connect(address=pts_addr).connection

        return "OK"

    @assert_description
    def TSC_MMI_iut_release_connection(self, pts_addr: bytes, **kwargs):
        """
        Click Ok, then release the HID connection from the Implementation Under
        Test (IUT) by closing the Interrupt Channel followed by the Control
        Channel.

        Description:  This can be done using the anticipated L2CAP
        Disconnection Requests.  If the host is unable to perform the connection
        request, the IUT may break the ACL or Baseband Link by going out of
        range.
        """

        # Performing out of range action
        def disconnect():
            sleep(2)
            self.rootcanal.move_out_of_range()

        Thread(target=disconnect).start()

        return "OK"

    @assert_description
    def TSC_MMI_iut_disable_connection(self, pts_addr: bytes, **kwargs):
        """
        Disable the connection using the Implementation UnderTest (IUT).

        Note:
        The IUT may either disconnect the Interupt Control Channels or send a
        host initiated virtual cable unplug and wait for the PTS to disconnect
        the channels.
        """

        self.host.Disconnect(connection=self.connection)
        self.connection = None

        return "OK"

    @assert_description
    def TSC_HID_MMI_iut_accept_connection_ready_confirm(self, **kwargs):
        """
        Please prepare the IUT to accept connection from PTS and then click OK.
        """

        self.rootcanal.move_in_range()

        return "OK"

    @assert_description
    def TSC_MMI_iut_connectable_enter_pw_dev(self, **kwargs):
        """
        Make the Implementation Under Test (IUT) connectable, then click Ok.
        """

        self.rootcanal.move_in_range()

        return "OK"

    @assert_description
    def TSC_HID_MMI_iut_accept_control_channel(self, pts_addr: bytes, **kwargs):
        """
        Accept the control channel connection from the Implementation Under Test
        (IUT).
        """

        return "OK"

    @assert_description
    def TSC_MMI_tester_release_connection(self, **kwargs):
        """
        Place the Implementation Under Test (IUT) in a state which will allow
        the PTS to perform an HID connection release, then click Ok.

        Note:  The
        PTS will send an L2CAP disconnect request for the Interrupt channel,
        then the control channel.
        """

        return "OK"

    @assert_description
    def TSC_MMI_host_iut_prepare_to_receive_pointing_data(self, **kwargs):
        """
        Place the Implementation Under Test (IUT) in a state to receive and
        verify HID pointing data, then click Ok.
        """

        return "OK"

    @assert_description
    def TSC_MMI_host_iut_verify_pointing_data(self, **kwargs):
        """
        Verify that the pointer on the Implementation Under Test (IUT) moved to
        the left (X< 0), then click Ok.
        """

        # TODO: implement!

        return "OK"

    @assert_description
    def TSC_MMI_host_send_output_report(self, pts_addr: bytes, **kwargs):
        """
        Send an output report from the HOST.
        """

        self.hid.SendHostReport(
            address=pts_addr,
            report_type=HID_REPORT_TYPE_OUTPUT,
            report="8",  # keyboard enable num-lock
        )

        return "OK"

    @match_description
    def TSC_MMI_verify_output_report(self, **kwargs):
        """
        Verify that the output report is correct.  nnOutput Report =0(?:0|1)'
        """

        # TODO: check the report matches the num-lock setting

        return "OK"

    @assert_description
    def TSC_MMI_rf_shield_iut_or_tester(self, pts_addr: bytes, **kwargs):
        """
        Click Ok, then perform one of the following actions:

        1. Move the PTS
        and Implementation Under Test (IUT) out of range of each other.
        2. Place
        either the PTS or IUT in an RF sheild box.
        """

        def disconnect():
            sleep(2)
            self.rootcanal.move_out_of_range()

        Thread(target=disconnect).start()

        return "OK"

    @assert_description
    def TSC_MMI_iut_auto_connection(self, pts_addr: bytes, **kwargs):
        """
        Click OK, then initiate a HID connection automatically from the IUT to
        the PTS
        """

        def connect():
            sleep(1)
            self.rootcanal.move_in_range()
            self.connection = self.host.Connect(address=pts_addr).connection

        Thread(target=connect).start()

        return "OK"
