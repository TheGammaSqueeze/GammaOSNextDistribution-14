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
"""AVRCP proxy module."""

import time
from typing import Optional

from grpc import RpcError

from mmi2grpc._audio import AudioSignal
from mmi2grpc._helpers import assert_description
from mmi2grpc._proxy import ProfileProxy
from pandora.a2dp_grpc import A2DP
from pandora.a2dp_pb2 import Sink, Source
from pandora_experimental.avrcp_grpc import AVRCP
from pandora.host_grpc import Host
from pandora.host_pb2 import Connection
from pandora_experimental.mediaplayer_grpc import MediaPlayer
from pandora_experimental.mediaplayer_pb2 import NONE, ALL, GROUP


class AVRCPProxy(ProfileProxy):
    """AVRCP proxy.

    Implements AVRCP and AVCTP PTS MMIs.
    """

    connection: Optional[Connection] = None
    sink: Optional[Sink] = None
    source: Optional[Source] = None

    def __init__(self, channel):
        super().__init__(channel)

        self.host = Host(channel)
        self.a2dp = A2DP(channel)
        self.avrcp = AVRCP(channel)
        self.mediaplayer = MediaPlayer(channel)

    @assert_description
    def TSC_AVDTP_mmi_iut_accept_connect(self, test: str, pts_addr: bytes, **kwargs):
        """
        If necessary, take action to accept the AVDTP Signaling Channel
        Connection initiated by the tester.

        Description: Make sure the IUT
        (Implementation Under Test) is in a state to accept incoming Bluetooth
        connections.  Some devices may need to be on a specific screen, like a
        Bluetooth settings screen, in order to pair with PTS.  If the IUT is
        still having problems pairing with PTS, try running a test case where
        the IUT connects to PTS to establish pairing.

        """
        self.connection = self.host.WaitConnection(address=pts_addr).connection
        if ("TG" in test and "TG/VLH" not in test) or "CT/VLH" in test:
            try:
                self.source = self.a2dp.WaitSource(connection=self.connection).source
            except RpcError:
                pass
        else:
            try:
                self.sink = self.a2dp.WaitSink(connection=self.connection).sink
            except RpcError:
                pass
        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_iut_accept_connect_control(self, **kwargs):
        """
        Please wait while PTS creates an AVCTP control channel connection.
        Action: Make sure the IUT is in a connectable state.

        """
        #TODO: Wait for connection to be established and AVCTP control channel to be open
        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_iut_accept_disconnect_control(self, **kwargs):
        """
        Please wait while PTS disconnects the AVCTP control channel connection.

        """
        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_unit_info(self, **kwargs):
        """
        Take action to send a valid response to the [Unit Info] command sent by
        the PTS.

        """
        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_subunit_info(self, **kwargs):
        """
        Take action to send a valid response to the [Subunit Info] command sent
        by the PTS.

        """
        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_iut_accept_connect_browsing(self, **kwargs):
        """
        Please wait while PTS creates an AVCTP browsing channel connection.
        Action: Make sure the IUT is in a connectable state.

        """
        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_get_folder_items_media_player_list(self, **kwargs):
        """
        Take action to send a valid response to the [Get Folder Items] with the
        scope <Media Player List> command sent by the PTS.

        """
        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_user_confirm_media_players(self, **kwargs):
        """
        Do the following media players exist on the IUT?

        Media Player:
        Bluetooth Player


        Note: Some media players may not be listed above.

        """
        #TODO: Verify the media players available
        return "OK"

    @assert_description
    def TSC_AVP_mmi_iut_initiate_disconnect(self, **kwargs):
        """
        Take action to disconnect all A2DP and/or AVRCP connections.

        """
        assert self.connection is not None
        self.host.Disconnect(connection=self.connection)

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_set_addressed_player(self, **kwargs):
        """
        Take action to send a valid response to the [Set Addressed Player]
        command sent by the PTS.

        """
        return "OK"

    @assert_description
    def _mmi_1002(self, test: str, pts_addr: bytes, **kwargs):
        """
        If necessary, take action to accept the AVDTP Signaling Channel
        Connection initiated by the tester.

        Description: Make sure the IUT
        (Implementation Under Test) is in a state to accept incoming Bluetooth
        connections.  Some devices may need to be on a specific screen, like a
        Bluetooth settings screen, in order to pair with PTS.  If the IUT is
        still having problems pairing with PTS, try running a test case where
        the IUT connects to PTS to establish pairing.
        """
        self.connection = self.host.WaitConnection(address=pts_addr).connection
        try:
            self.sink = self.a2dp.WaitSink(connection=self.connection).sink
        except RpcError:
            pass

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_send_AVCT_ConnectRsp(self, **kwargs):
        """
        Upon a call to the callback function ConnectInd_CBTest_System,  use the
        Upper Tester to send an AVCT_ConnectRsp message to the IUT with the
        following parameter values:
           * BD_ADDR = BD_ADDRLower_Tester
           *
        Connect Result = Valid value for L2CAP connect response result.
           *
        Status = Valid value for L2CAP connect response status.

        The IUT should
        then initiate an L2CAP_ConnectRsp and L2CAP_ConfigRsp.
        """

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_verify_ConnectInd_CB(self, **kwargs):
        """
        Press 'OK' if the following conditions were met :

        1. The IUT returns
        the following AVCT_EventRegistration output parameters to the Upper
        Tester:
           * Result = 0x0000 (Event successfully registered)

        2. The IUT
        calls the ConnectInd_CBTest_System function in the Upper Tester with the
        following parameter values:
           * BD_ADDR = BD_ADDRLower_Tester

        3. After
        reception of any expected AVCT_EventRegistration command from the Upper
        Tester and the L2CAP_ConnectReq from the Lower Tester, the IUT issues an
        L2CAP_ConnectRsp to the Lower Tester.
        """

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_register_ConnectInd_CB(self, **kwargs):
        """
        Using the Upper Tester register the function ConnectInd_CBTest_System
        for callback on the AVCT_Connect_Ind event by sending an
        AVCT_EventRegistration command to the IUT with the following parameter
        values:
           * Event = AVCT_Connect_Ind
           * Callback =
        ConnectInd_CBTest_System
           * PID = PIDTest_System

        Press 'OK' to
        continue once the IUT has responded.
        """

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_register_DisconnectInd_CB(self, **kwargs):
        """
        Using the Upper Tester register the DisconnectInd_CBTest_System function
        for callback on the AVCT_Disconnect_Ind event by sending an
        AVCT_EventRegistration command to the IUT with the following parameter
        values :
           * Event = AVCT_Disconnect_Ind
           * Callback =
        DisconnectInd_CBTest_System
           * PID = PIDTest_System

        Press 'OK' to
        continue once the IUT has responded.
        """

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_verify_DisconnectInd_CB(self, **kwargs):
        """
        Press 'OK' if the following conditions were met :

        1. The IUT returns
        the following AVCT_EventRegistration output parameters to the Upper
        Tester:
           * Result = 0x0000 (Event successfully registered)

        2. The IUT
        calls the DisconnectInd_CBTest_System function in the Upper Tester with
        the following parameter values:
           * BD_ADDR = BD_ADDRLower_Tester
        """

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_verify_AVCT_SendMessage_TG(self, **kwargs):
        """
        Press 'OK' if the following conditions were met :

        1. The IUT returns
        the following AVCT_EventRegistration output parameters to the Upper
        Tester:
           * Result = 0x0000 (Event successfully registered)

        2. The IUT
        calls the MessageInd_CBTest_System callback function of the test system
        with the following parameters:
           * BD_ADDR = BD_ADDRTest_System
           *
        Transaction = TRANSTest_System
           * Type = 0
           * Data =
        DATA[]Lower_Tester
           * Length = LengthOf(DATA[]Lower_Tester)
        """

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_iut_reject_invalid_profile_id(self, **kwargs):
        """
        Take action to reject the AVCTP DATA request with an invalid profile id.
        The IUT is expected to set the ipid field to invalid and return only the
        avctp header (no body data should be sent).
        """
        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_verify_fragmented_AVCT_SendMessage_TG(self, **kwargs):
        """
        Press 'OK' if the following condition was met :

        The IUT receives three
        AVCTP packets from the Lower Tester, reassembles the message and calls
        the MessageInd_CBTestSystem callback function with the following
        parameters:
           * BD_ADDR = BD_ADDRTest_System
           * Transaction =
        TRANSTest_System
           * Type = 0x01 (Command Message)
           * Data =
        ADDRESSdata_buffer (Buffer holding DATA[]Lower_Tester)
           * Length =
        LengthOf(DATA[]Lower_Tester)
        """

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_iut_initiate_avctp_data_response(self, **kwargs):
        """
        Take action to send the data specified in TSPX_avctp_iut_response_data
        to the tester.

        Note: If TSPX_avctp_psm = '0017'(AVRCP control channel
        psm), a valid AVRCP response may be sent to the tester.
        """
        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_register_MessageInd_CB_TG(self, **kwargs):
        """
        Using the Upper Tester register the function MessageInd_CBTest_System
        for callback on the AVCT_MessageRec_Ind event by sending an
        AVCT_EventRegistration command to the IUT with the following parameter
        values:
           * Event = AVCT_MessageRec_Ind
           * Callback =
        MessageInd_CBTest_System
           * PID = PIDTest_System

        Press 'OK' to
        continue once the IUT has responded.
        """
        #TODO: Remove trailing space post "values:" from docstring description

        return "OK"

    @assert_description
    def TSC_AVDTP_mmi_iut_initiate_connect(self, test: str, pts_addr: bytes, **kwargs):
        """
        Create an AVDTP signaling channel.

        Action: Create an audio or video
        connection with PTS.
        """
        self.connection = self.host.Connect(address=pts_addr).connection
        if ("TG" in test and "TG/VLH" not in test) or "CT/VLH" in test:
            self.source = self.a2dp.OpenSource(connection=self.connection).source

        return "OK"

    @assert_description
    def _mmi_690(self, **kwargs):
        """
        Press 'YES' if the IUT indicated receiving the [PLAY] command.  Press
        'NO' otherwise.

        Description: Verify that the Implementation Under Test
        (IUT) successfully indicated that the current operation was pressed. Not
        all commands (fast forward and rewind for example) have a noticeable
        effect when pressed for a short period of time.  For commands like that
        it is acceptable to assume the effect took place and press 'YES'.
        """

        return "Yes"

    @assert_description
    def _mmi_691(self, **kwargs):
        """
        Press 'YES' if the IUT indicated receiving the [STOP] command.  Press
        'NO' otherwise.

        Description: Verify that the Implementation Under Test
        (IUT) successfully indicated that the current operation was pressed. Not
        all commands (fast forward and rewind for example) have a noticeable
        effect when pressed for a short period of time.  For commands like that
        it is acceptable to assume the effect took place and press 'YES'.
        """

        return "Yes"

    @assert_description
    def _mmi_540(self, **kwargs):
        """
        Press 'YES' if the IUT supports press and hold functionality for the
        [PLAY] command.  Press 'NO' otherwise.

        Description: Verify press and
        hold functionality of passthrough operations that support press and
        hold.  Not all operations support press and hold, pressing 'NO' will not
        fail the test case.
        """

        return "Yes"

    @assert_description
    def _mmi_615(self, **kwargs):
        """
        Press 'YES' if the IUT indicated press and hold functionality for the
        [PLAY] command.  Press 'NO' otherwise.

        Description: Verify that the
        Implementation Under Test (IUT) successfully indicated that the current
        operation was held.
        """

        return "Yes"

    @assert_description
    def _mmi_541(self, **kwargs):
        """
        Press 'YES' if the IUT supports press and hold functionality for the
        [STOP] command.  Press 'NO' otherwise.

        Description: Verify press and
        hold functionality of passthrough operations that support press and
        hold.  Not all operations support press and hold, pressing 'NO' will not
        fail the test case.
        """

        return "Yes"

    @assert_description
    def _mmi_616(self, **kwargs):
        """
        Press 'YES' if the IUT indicated press and hold functionality for the
        [STOP] command.  Press 'NO' otherwise.

        Description: Verify that the
        Implementation Under Test (IUT) successfully indicated that the current
        operation was held.
        """

        return "Yes"

    @assert_description
    def TSC_AVRCP_mmi_user_confirm_media_is_streaming(self, **kwargs):
        """
        Press 'OK' when the IUT is in a state where media is playing.
        Description: PTS is preparing the streaming state for the next
        passthrough command, if the current streaming state is not relevant to
        this IUT, please press 'OK to continue.
        """

        suspended = self.a2dp.IsSuspended(source=self.source).value
        return "Yes" if not suspended else "No"

    @assert_description
    def TSC_AVRCP_mmi_iut_reject_invalid_get_capabilities(self, **kwargs):
        """
        The IUT should reject the invalid Get Capabilities command sent by PTS.
        Description: Verify that the IUT can properly reject a Get Capabilities
        command that contains an invalid capability.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_get_capabilities(self, **kwargs):
        """
        Take action to send a valid response to the [Get Capabilities] command
        sent by the PTS.
        """
        # This will be done as part as the a2dp.OpenSource or a2dp.WaitSource

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_get_element_attributes(self, **kwargs):
        """
        Take action to send a valid response to the [Get Element Attributes]
        command sent by the PTS.
        """
        # This will be done as part as the a2dp.OpenSource or a2dp.WaitSource

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_reject_invalid_command_control_channel(self, **kwargs):
        """
        PTS has sent an invalid command over the control channel.  The IUT must
        respond with a general reject on the control channel.

        Description:
        Verify that the IUT can properly reject an invalid command sent over the
        control channel.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_reject_invalid_command_browsing_channel(self, **kwargs):
        """
        PTS has sent an invalid command over the browsing channel.  The IUT must
        respond with a general reject on the browsing channel.

        Description:
        Verify that the IUT can properly reject an invalid command sent over the
        browsing channel.
        """

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_register_ConnectCfm_CB(self, **kwargs):
        """
        Using the Upper Tester send an AVCT_EventRegistration command from the
        AVCTP Upper Interface to the IUT with the following input parameter
        values:
           * Event = AVCT_Connect_Cfm
           * Callback =
        ConnectCfm_CBTest_System
           * PID = PIDTest_System

        Press 'OK' to
        continue once the IUT has responded.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_set_browsed_player(self, **kwargs):
        """
        Take action to send a valid response to the [Set Browsed Player] command
        sent by the PTS.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_get_folder_items_virtual_file_system(self, **kwargs):
        """
        Take action to send a valid response to the [Get Folder Items] with the
        scope <Virtual File System> command sent by the PTS.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_user_confirm_virtual_file_system(self, **kwargs):
        """
        Are the following items found in the current folder?

        Folder:
        com.android.pandora


        Note: Some media elements and folders may not be
        listed above.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_change_path_down(self, **kwargs):
        """
        Take action to send a valid response to the [Change Path] <Down> command
        sent by the PTS.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_change_path_up(self, **kwargs):
        """
        Take action to send a valid response to the [Change Path] <Up> command
        sent by the PTS.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_user_action_track_playing(self, **kwargs):
        """
        Place the IUT into a state where a track is currently playing, then
        press 'OK' to continue.
        """
        self.mediaplayer.Play()

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_get_item_attributes(self, **kwargs):
        """
        Take action to send a valid response to the [Get Item Attributes]
        command sent by the PTS.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_reject_list_player_application_setting_values_invalid_attribute(self, **kwargs):
        """
        PTS has sent a List Player Application Setting Values command with an
        invalid Attribute Id.  The IUT must respond with the error code: Invalid
        Parameter (0x01).

        Description: Verify that the IUT can properly reject
        a List Player Application Setting Values command that contains an
        invalid attribute id.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_reject_set_player_application_setting_value_invalid_pair(self, **kwargs):
        """
        PTS has sent a Set Player Application Setting Value command with an
        invalid Attribute and Value.  The IUT must respond with the error code:
        Invalid Parameter (0x01).

        Description: Verify that the IUT can properly
        reject a Set Player Application Setting Value command that contains an
        invalid attribute and value.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_reject_get_current_player_application_setting_value_invalid_attribute(self, **kwargs):
        """
        PTS has sent a Get Current Player Application Setting Value command with
        an invalid Attribute.  The IUT must respond with the error code: Invalid
        Parameter (0x01).

        Description: Verify that the IUT can properly reject
        an Get Current Player Application Setting Value command that contains an
        invalid attribute.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_get_current_player_application_setting_value(self, **kwargs):
        """
        Take action to send a valid response to the [Get Current Player
        Application Setting Value] command sent by the PTS.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_list_player_application_setting_attributes(self, **kwargs):
        """
        Take action to send a valid response to the [List Player Application
        Setting Attributes] command sent by the PTS.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_list_player_application_setting_values(self, **kwargs):
        """
        Take action to send a valid response to the [List Player Application
        Setting Values] command sent by the PTS.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_reject_set_addressed_player_invalid_player_id(self, **kwargs):
        """
        PTS has sent a Set Addressed Player command with an invalid Player Id.
        The IUT must respond with the error code: Invalid Player Id (0x11).
        Description: Verify that the IUT can properly reject a Set Addressed
        Player command that contains an invalid player id.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_initiate_register_notification_changed_player_application_setting_changed(self, **kwargs):
        """
        Take action to trigger a [Register Notification, Changed] response for
        <Player Application Setting Changed> to the PTS from the IUT.  This can
        be accomplished by changing a Player Application Setting (Equalizer,
        Repeat Mode, Shuffle, Scan) on the IUT.

        Description: Verify that the
        Implementation Under Test (IUT) can update database by sending a valid
        Player Application Setting Changed Notification to the PTS.
        """

        nextShuffleMode = NONE
        self.mediaplayer.StartTestPlayback()
        currentShuffleMode = self.mediaplayer.GetShuffleMode().mode
        if (currentShuffleMode == NONE):
            nextShuffleMode = ALL
        elif (currentShuffleMode == ALL):
            nextShuffleMode = GROUP
        elif (currentShuffleMode == GROUP):
            nextShuffleMode = ALL
        self.mediaplayer.SetShuffleMode(mode=nextShuffleMode)
        self.mediaplayer.StopTestPlayback()

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_reject_get_folder_items_out_of_range(self, **kwargs):
        """
        PTS has sent a Get Folder Items command with invalid values for Start
        and End.  The IUT must respond with the error code: Range Out Of Bounds
        (0x0B).

        Description: Verify that the IUT can properly reject a Get
        Folder Items command that contains an invalid start and end index.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_reject_change_path_down_invalid_uid(self, **kwargs):
        """
        PTS has sent a Change Path Down command with an invalid folder UID.  The
        IUT must respond with the error code: Does Not Exist (0x09).
        Description: Verify that the IUT can properly reject an Change Path Down
        command that contains an invalid UID.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_reject_get_item_attributes_invalid_uid_counter(self, **kwargs):
        """
        PTS has sent a Get Item Attributes command with an invalid UID Counter.
        The IUT must respond with the error code: UID Changed (0x05).
        Description: Verify that the IUT can properly reject a Get Item
        Attributes command that contains an invalid UID Counter.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_play_item(self, **kwargs):
        """
        Take action to send a valid response to the [Play Item] command sent by
        the PTS.
        """

        self.mediaplayer.Forward()

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_reject_play_item_invalid_uid(self, **kwargs):
        """
        PTS has sent a Play Item command with an invalid UID.  The IUT must
        respond with the error code: Does Not Exist (0x09).

        Description: Verify
        that the IUT can properly reject a Play Item command that contains an
        invalid UID.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_initiate_register_notification_changed_track_changed(self, **kwargs):
        """
        Take action to trigger a [Register Notification, Changed] response for
        <Track Changed> to the PTS from the IUT.  This can be accomplished by
        changing the currently playing track on the IUT.

        Description: Verify
        that the Implementation Under Test (IUT) can update database by sending
        a valid Track Changed Notification to the PTS.
        """

        self.mediaplayer.Play()
        self.mediaplayer.Forward()

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_user_action_change_track(self, **kwargs):
        """
        Take action to change the currently playing track.
        """
        self.mediaplayer.Forward()

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_reject_set_browsed_player_invalid_player_id(self, **kwargs):
        """
        PTS has sent a Set Browsed Player command with an invalid Player Id.
        The IUT must respond with the error code: Invalid Player Id (0x11).
        Description: Verify that the IUT can properly reject a Set Browsed
        Player command that contains an invalid player id.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_reject_register_notification_notify_invalid_event_id(self, **kwargs):
        """
        PTS has sent a Register Notification command with an invalid Event Id.
        The IUT must respond with the error code: Invalid Parameter (0x01).
        Description: Verify that the IUT can properly reject a Register
        Notification command that contains an invalid event Id.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_user_action_play_large_metadata_media(self, **kwargs):
        """
        Start playing a media item with more than 512 bytes worth of metadata,
        then press 'OK'.
        """

        self.mediaplayer.SetLargeMetadata()

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_user_confirm_now_playing_list_updated_with_local(self, **kwargs):
        """
        Is the newly added media item listed below?

        Media Element: Title1
        Media
        Element: Title2
        Media Element: Title3
        Media Element: Title4
        Media
        Element: Title5
        Media Element: Title6
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_user_confirm_now_playing_list(self, **kwargs):
        """
        Do the following items match the current now playing list?

        Media
        Element: Title1
        Media Element: Title2
        Media Element: Title3
        Media
        Element: Title4
        Media Element: Title5
        Media Element: Title6


        Note: Some
        now playing items may not be listed above.
        """

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_user_action_queue_now_playing(self, **kwargs):
        """
        Take action to populate the now playing list with multiple items.  Then
        make sure a track is playing and press 'OK'.

        Note: If the
        NOW_PLAYING_CONTENT_CHANGED notification has been registered, this
        message will disappear when the notification changed is received.
        """
        self.mediaplayer.Play()

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_accept_get_folder_items_now_playing(self, **kwargs):
        """
        Take action to send a valid response to the [Get Folder Items] with the
        scope <Now Playing> command sent by the PTS.
        """
        self.mediaplayer.Forward()

        return "OK"

    @assert_description
    def TSC_AVRCP_mmi_iut_initiate_register_notification_changed_now_playing_content_changed(self, **kwargs):
        """
        Take action to trigger a [Register Notification, Changed] response for
        <Now Playing Content Changed> to the PTS from the IUT.  This can be
        accomplished by adding tracks to the Now Playing List on the IUT.
        Description: Verify that the Implementation Under Test (IUT) can update
        database by sending a valid Now Playing Changed Notification to the PTS.
        """
        self.mediaplayer.UpdateQueue()
        self.mediaplayer.Play()

        return "OK"

    @assert_description
    def _mmi_1016(self, test: str, pts_addr: bytes, **kwargs):
        """
        Create an AVDTP signaling channel.

        Action: Create an audio or video
        connection with PTS.
        """
        self.connection = self.host.Connect(address=pts_addr).connection
        if "TG" in test:
            try:
                self.source = self.a2dp.OpenSource(connection=self.connection).source
            except RpcError:
                pass
        else:
            try:
                self.sink = self.a2dp.WaitSink(connection=self.connection).sink
            except RpcError:
                pass

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_send_AVCT_ConnectReq(self, pts_addr: bytes, **kwargs):
        """
        Using the Upper Tester, send an AVCT_ConnectReq command to the IUT with
        the following input parameter values:
           * BD_ADDR = BD_ADDRLower_Tester
        * PID = PIDTest_System

        The IUT should then initiate an
        L2CAP_ConnectReq.
        """

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_verify_ConnectCfm_CB(self, pts_addr: bytes, **kwargs):
        """
        Press 'OK' if the following conditions were met :

        1. The IUT returns
        the following AVCT_ConnectReq output parameters to the Upper Tester:
        * Result = 0x0000 (Event successfully registered)

        2. The IUT calls the
        ConnectCfm_CBTest_System function in the Upper Tester with the following
        parameters:
           * BD_ADDR = BD_ADDRLower_Tester
           * Connect Result =
        0x0000 (L2CAP Connect Request successful)
           * Config Result = 0x0000
        (L2CAP Configure successful)
           * Status = L2CAP Connect Request Status
        """

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_register_DisconnectCfm_CB(self, pts_addr: bytes, **kwargs):
        """
        Using the Upper Tester register the function DisconnectCfm_CBTest_System
        for callback on the AVCT_Disconnect_Cfm event by sending an
        AVCT_EventRegistration command to the IUT with the following parameter
        values:
           * Event = AVCT_Disconnect_Cfm
           * Callback =
        DisconnectCfm_CBTest_System
           * PID = PIDTest_System

        Press 'OK' to
        continue once the IUT has responded.
        """

        return "OK"

    def TSC_AVCTP_mmi_send_AVCT_Disconnect_Req(self, test: str, pts_addr: bytes, **kwargs):
        """
        Using the Upper Tester send an AVCT_DisconnectReq command to the IUT
        with the following parameter values:
           * BD_ADDR = BD_ADDRLower_Tester
        * PID = PIDTest_System

        The IUT should then initiate an
        L2CAP_DisconnectReq.
        """
        # Currently disconnect is required in TG role
        if "TG" in test:
            time.sleep(3)
            self.host.Disconnect(connection=self.connection)
            self.connection = None

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_verify_DisconnectCfm_CB(self, **kwargs):
        """
        Press 'OK' if the following conditions were met :

        1. The IUT returns
        the following AVCT_EventRegistration output parameters to the Upper
        Tester:
           * Result = 0x0000 (Event successfully registered)

        2. The IUT
        calls the DisconnectCfm_CBTest_System function in the Upper Tester with
        the following parameter values:
           * BD_ADDR = BD_ADDRLower_Tester
           *
        Disconnect Result = 0x0000 (L2CAP disconnect success)

        3. The IUT
        returns the following AVCT_DisconnectReq output parameter values to the
        Upper Tester:
           * RSP = 0x0000 (Request accepted)
        """

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_send_AVCT_SendMessage_TG(self, **kwargs):
        """
        Upon a call to the call back function MessageInd_CBTest_System, use the
        Upper Tester to send an AVCT_SendMessage command to the IUT with the
        following parameter values:
           * BD_ADDR = BD_ADDRTest_System
           *
        Transaction = TRANSTest_System
           * Type = CRTest_System = 1 (Response
        Message)
           * PID = PIDTest_System
           * Data = ADDRESSdata_buffer
        (Buffer containing DATA[]Upper_Tester)
           * Length =
        LengthOf(DATA[]Upper_Tester) <= MTU – 3bytes
        """

        return "OK"

    @assert_description
    def TSC_AVCTP_mmi_verify_MessageInd_CB_TG(self, **kwargs):
        """
        Press 'OK' if the following conditions were met :

        1. The
        MessageInd_CBTest_System function in the Upper Tester is called with the
        following parameters:
           * BD_ADDR = BD_ADDRLower_Tester
           *
        Transaction = TRANSTest_System
           * Type = 0x00 (Command message)
           *
        Data = ADDRESSdata_buffer (Buffer containing DATA[]Lower_Tester)
           *
        Length = LengthOf(DATA[]Lower_Tester)

        2. the IUT returns the following
        AVCT_SendMessage output parameters to the Upper Tester:
           * Result =
        0x0000 (Request accepted)
        """

        return "OK"
