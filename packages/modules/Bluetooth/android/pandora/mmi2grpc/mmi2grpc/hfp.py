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
"""HFP proxy module."""

from mmi2grpc._helpers import assert_description, match_description
from mmi2grpc._proxy import ProfileProxy

from pandora_experimental.hfp_grpc import HFP
from pandora.host_grpc import Host
from pandora.host_pb2 import DISCOVERABLE_GENERAL, CONNECTABLE
from pandora.security_grpc import Security, SecurityStorage
from pandora.security_pb2 import PairingEventAnswer
from pandora_experimental.hfp_pb2 import AUDIO_PATH_HANDSFREE, AUDIO_PATH_SPEAKERS

import sys
import threading
import time

# Standard time to wait before asking for waitConnection
WAIT_DELAY_BEFORE_CONNECTION = 2

IXIT_PHONE_NUMBER = 42
IXIT_SECOND_PHONE_NUMBER = 43


class HFPProxy(ProfileProxy):

    def __init__(self, test, channel, rootcanal, modem):
        super().__init__(channel)
        self.hfp = HFP(channel)
        self.host = Host(channel)
        self.security = Security(channel)
        self.security_storage = SecurityStorage(channel)
        self.rootcanal = rootcanal
        self.modem = modem
        self.connection = None

        self._auto_confirm_requests()

    def asyncWaitConnection(self, pts_addr, delay=WAIT_DELAY_BEFORE_CONNECTION):
        """
        Send a WaitConnection in a grpc callback
        """

        def waitConnectionCallback(self, pts_addr):
            self.connection = self.host.WaitConnection(address=pts_addr).connection

        print(f"HFP placeholder mmi: asyncWaitConnection", file=sys.stderr)
        th = threading.Timer(interval=delay, function=waitConnectionCallback, args=(self, pts_addr))
        th.start()

    def test_started(self, test: str, pts_addr: bytes, **kwargs):
        if test not in ("HFP/AG/SLC/BV-02-C", "HFP/AG/SLC/BV-04-C"):
            self.asyncWaitConnection(pts_addr)

        return "OK"

    @assert_description
    def TSC_delete_pairing_iut(self, pts_addr: bytes, **kwargs):
        """
        Delete the pairing with the PTS using the Implementation Under Test
        (IUT), then click Ok.
        """

        self.security_storage.DeleteBond(public=pts_addr)
        return "OK"

    @assert_description
    def TSC_iut_enable_slc(self, test: str, pts_addr: bytes, **kwargs):
        """
        Click Ok, then initiate a service level connection from the
        Implementation Under Test (IUT) to the PTS.
        """

        def enable_slc():
            time.sleep(2)

            if not self.connection:
                self.connection = self.host.Connect(address=pts_addr).connection

            if "HFP/HF" in test:
                self.hfp.EnableSlcAsHandsfree(connection=self.connection)
            else:
                self.hfp.EnableSlc(connection=self.connection)

        threading.Thread(target=enable_slc).start()

        return "OK"

    @assert_description
    def TSC_iut_search(self, **kwargs):
        """
        Using the Implementation Under Test (IUT), perform a search for the PTS.
        If found, click OK.
        """

        return "OK"

    @assert_description
    def TSC_iut_connect(self, pts_addr: bytes, **kwargs):
        """
        Click Ok, then make a connection request to the PTS from the
        Implementation Under Test (IUT).
        """

        def connect():
            time.sleep(2)
            self.connection = self.host.Connect(address=pts_addr).connection

        threading.Thread(target=connect).start()

        return "OK"

    @assert_description
    def TSC_iut_connectable(self, pts_addr: str, test: str, **kwargs):
        """
        Make the Implementation Under Test (IUT) connectable, then click Ok.
        """

        self.host.SetConnectabilityMode(mode=CONNECTABLE)
        # these two test cases fail if Connection is established in "TSC_iut_enable_slc"
        if test in ("HFP/AG/SLC/BV-02-C", "HFP/AG/SLC/BV-04-C"):
            self.connection = self.host.Connect(address=pts_addr).connection

        return "OK"

    @assert_description
    def TSC_iut_disable_slc(self, test: str, pts_addr: bytes, **kwargs):
        """
        Click Ok, then disable the service level connection using the
        Implementation Under Test (IUT).
        """

        def disable_slc():
            time.sleep(2)
            if "HFP/HF" in test:
                self.hfp.DisableSlcAsHandsfree(connection=self.connection)
            else:
                self.hfp.DisableSlc(connection=self.connection)

        threading.Thread(target=disable_slc).start()

        return "OK"

    @assert_description
    def TSC_make_battery_charged(self, **kwargs):
        """
        Click Ok, then manipulate the Implementation Under Test (IUT) so that
        the battery is fully charged.
        """

        self.hfp.SetBatteryLevel(connection=self.connection, battery_percentage=100)

        return "OK"

    @assert_description
    def TSC_make_battery_discharged(self, **kwargs):
        """
        Manipulate the Implementation Under Test (IUT) so that the battery level
        is not fully charged, then click Ok.
        """

        self.hfp.SetBatteryLevel(connection=self.connection, battery_percentage=42)

        return "OK"

    @assert_description
    def TSC_ag_iut_enable_call(self, **kwargs):
        """
        Click Ok, then place a call from an external line to the Implementation
        Under Test (IUT). Do not answer the call unless prompted to do so.
        """

        def enable_call():
            time.sleep(2)
            self.modem.call(IXIT_PHONE_NUMBER)

        threading.Thread(target=enable_call).start()

        return "OK"

    @assert_description
    def TSC_verify_audio(self, **kwargs):
        """
        Verify the presence of an audio connection, then click Ok.
        """

        # TODO
        time.sleep(2)  # give it time for SCO to come up

        return "OK"

    @assert_description
    def TSC_ag_iut_disable_call_external(self, **kwargs):
        """
        Click Ok, then end the call using the external terminal.
        """

        def disable_call_external():
            time.sleep(2)
            self.hfp.DeclineCall()

        threading.Thread(target=disable_call_external).start()

        return "OK"

    @assert_description
    def TSC_iut_enable_audio_using_codec(self, **kwargs):
        """
        Click OK, then initiate an audio connection using the Codec Connection
        Setup procedure.
        """

        return "OK"

    @assert_description
    def TSC_iut_disable_audio(self, test: str, pts_addr: bytes, **kwargs):
        """
        Click Ok, then close the audio connection (SCO) between the
        Implementation Under Test (IUT) and the PTS.  Do not close the serivice
        level connection (SLC) or power-off the IUT.
        """

        def disable_audio():
            time.sleep(2)
            if "HFP/HF" in test:
                self.hfp.DisconnectToAudioAsHandsfree(connection=self.connection)
            else:
                self.hfp.SetAudioPath(audio_path=AUDIO_PATH_SPEAKERS)

        threading.Thread(target=disable_audio).start()

        return "OK"

    @assert_description
    def TSC_verify_no_audio(self, **kwargs):
        """
        Verify the absence of an audio connection (SCO), then click Ok.
        """

        return "OK"

    @assert_description
    def TSC_iut_enable_audio(self, test: str, pts_addr: bytes, **kwargs):
        """
        Click Ok, then initiate an audio connection (SCO) from the
        Implementation Under Test (IUT) to the PTS.
        """

        def enable_audio():
            time.sleep(2)
            if "HFP/HF" in test:
                self.hfp.ConnectToAudioAsHandsfree(connection=self.connection)
            else:
                self.hfp.SetAudioPath(audio_path=AUDIO_PATH_HANDSFREE)

        threading.Thread(target=enable_audio).start()

        return "OK"

    @assert_description
    def TSC_iut_disable_audio_slc_down_ok(self, pts_addr: bytes, **kwargs):
        """
        Click OK, then close the audio connection (SCO) between the
        Implementation Under Test (IUT) and the PTS.  If necessary, it is OK to
        close the service level connection. Do not power-off the IUT.
        """

        def disable_slc():
            time.sleep(2)
            self.hfp.DisableSlc(connection=self.connection)

        threading.Thread(target=disable_slc).start()

        return "OK"

    @assert_description
    def TSC_ag_iut_call_no_slc(self, **kwargs):
        """
        Place a call from an external line to the Implementation Under Test
        (IUT).  When the call is active, click Ok.
        """

        self.modem.call(IXIT_PHONE_NUMBER)
        time.sleep(5)  # there's a delay before Android registers the call
        self.hfp.AnswerCall()
        time.sleep(2)

        return "OK"

    @assert_description
    def TSC_ag_iut_enable_second_call(self, **kwargs):
        """
        Click Ok, then place a second call from an external line to the
        Implementation Under Test (IUT). Do not answer the call unless prompted
        to do so.
        """

        def enable_second_call():
            time.sleep(2)
            self.modem.call(IXIT_SECOND_PHONE_NUMBER)

        threading.Thread(target=enable_second_call).start()

        return "OK"

    @assert_description
    def TSC_ag_iut_call_swap(self, **kwargs):
        """
        Click Ok, then place the current call on hold and make the incoming/held
        call active using the Implementation Under Test (IUT).
        """

        self.hfp.SwapActiveCall()

        return "OK"

    @assert_description
    def TSC_verify_audio_second_call(self, **kwargs):
        """
        Verify the audio is returned to the 2nd call and then click Ok.  Resume
        action may be needed.  If the audio is not returned to the 2nd call,
        click Cancel.
        """

        return "OK"

    @assert_description
    def TSC_ag_iut_disable_call_after_verdict(self, **kwargs):
        """
        After the test verdict  is given, end all active calls using the
        external line or the Implementation Under Test (IUT).  Click OK to
        continue.
        """

        self.hfp.DeclineCall()

        return "OK"

    @assert_description
    def TSC_disable_ag_cellular_network_expect_no_notification(self, **kwargs):
        """
        Disable the control channel, such that the AG is de-registered. Then,
        click OK.
        """

        return "OK"

    @assert_description
    def TSC_verify_no_ecnr(self, **kwargs):
        """
        Verify that EC and NR functionality is disabled, then click Ok.
        """

        return "OK"

    @assert_description
    def TSC_disable_inband_ring(self, **kwargs):
        """
        Click Ok, then disable the in-band ringtone using the Implemenation
        Under Test (IUT).
        """

        self.hfp.SetInBandRingtone(enabled=False)
        self.host.Reset()

        return "OK"

    @assert_description
    def TSC_wait_until_ringing(self, **kwargs):
        """
        When the Implementation Under Test (IUT) alerts the incoming call, click
        Ok.
        """

        # we are triggering a call from modem_simulator, so the alert is immediate

        return "OK"

    @assert_description
    def TSC_verify_incoming_call_ag(self, **kwargs):
        """
        Verify that there is an incoming call on the Implementation Under Test
        (IUT).
        """

        # we are triggering a call from modem_simulator, so this is guaranteed

        return "OK"

    @assert_description
    def TSC_disable_ag_cellular_network_expect_notification(self, pts_addr: bytes, **kwargs):
        """
        Click OK. Then, disable the control channel, such that the AG is de-
        registered.
        """

        def disable_slc():
            time.sleep(2)
            self.hfp.DisableSlc(connection=self.connection)

        threading.Thread(target=disable_slc).start()

        return "OK"

    @assert_description
    def TSC_adjust_ag_battery_level_expect_no_notification(self, **kwargs):
        """
        Adjust the battery level on the AG to a level that should cause a
        battery level indication to be sent to HF. Then, click OK.
        """

        self.hfp.SetBatteryLevel(connection=self.connection, battery_percentage=42)

        return "OK"

    def TSC_verify_subscriber_number(self, **kwargs):
        """
        Using the Implementation Under Test (IUT), verify that the following is
        a valid Audio Gateway (AG) subscriber number, then click
        Ok."+15551234567"nnNOTE: Subscriber service type is 145
        """

        return "OK"

    def TSC_ag_prepare_at_bldn(self, **kwargs):
        r"""
        Place the Implemenation Under Test (IUT) in a state which will accept an
        outgoing call set-up request from the PTS, then click OK.

        Note:  The
        PTS will send a request to establish an outgoing call from the IUT to
        the last dialed number.  Answer the incoming call when alerted.
        """

        self.hfp.MakeCall(number=str(IXIT_PHONE_NUMBER))
        self.log("Calling")
        time.sleep(2)
        self.hfp.DeclineCall()
        self.log("Declining")
        time.sleep(2)

        return "OK"

    @assert_description
    def TSC_ag_iut_prepare_for_atd(self, **kwargs):
        """
        Place the Implementation Under Test (IUT) in a mode that will allow an
        outgoing call initiated by the PTS, and click Ok.
        """

        return "OK"

    @assert_description
    def TSC_terminal_answer_call(self, **kwargs):
        """
        Click Ok, then answer the incoming call on the external terminal.
        """

        def answer_call():
            time.sleep(2)
            self.log("Answering")
            self.modem.answer_outgoing_call(IXIT_PHONE_NUMBER)

        threading.Thread(target=answer_call).start()

        return "OK"

    @match_description
    def TSC_signal_strength_verify(self, **kwargs):
        """
        Verify that the signal reported on the Implementaion Under Test \(IUT\) is
        proportional to the value \(out of 5\), then click Ok.[0-9]
        """

        return "OK"

    @assert_description
    def TSC_signal_strength_impair(self, **kwargs):
        """
        Impair the cellular signal by placing the Implementation Under Test
        (IUT) under partial RF shielding, then click Ok.
        """

        return "OK"

    @assert_description
    def TSC_verify_network_operator(self, **kwargs):
        """
        Verify the following information matches the network operator reported
        on the Implementation Under Test (IUT), then click Ok:"Android Virtual "
        """

        return "OK"

    @assert_description
    def TSC_INFO_slc_with_30_seconds_wait(self, **kwargs):
        """
        After clicking the OK button, PTS will connect to the IUT and then be
        idle for 30 seconds as part of the test procedure.

        Click OK to proceed.
        """

        return "OK"

    @assert_description
    def TSC_ag_iut_disable_call(self, **kwargs):
        """
        Click Ok, then end the call using the Implemention Under Test IUT).
        """

        def disable_call():
            time.sleep(2)
            self.hfp.DeclineCall()

        threading.Thread(target=disable_call).start()

        return "OK"

    @match_description
    def TSC_dtmf_verify(self, **kwargs):
        """
        Verify the DTMF code, then click Ok. .
        """

        return "OK"

    @assert_description
    def TSC_TWC_instructions(self, **kwargs):
        """
        NOTE: The following rules apply for this test case:

        1.
        TSPX_phone_number - the 1st call
        2. TSPX_second_phone_number - the 2nd
        call

        Edits can be made within the IXIT settings for the above phone
        numbers.
        """

        return "OK"

    def TSC_call_swap_and_disable_held_tester(self, **kwargs):
        """
        Set the Implementation Under Test (IUT) in a state that will allow the
        PTS to initiate a AT+CHLD=1 operation,  then click Ok.

        Note: Upon
        receiving the said command, the IUT will simultaneously drop the active
        call and make the held call active.
        """

        return "OK"

    @assert_description
    def TSC_verify_audio_first_call(self, **kwargs):
        """
        Verify the audio is returned to the 1st call and click Ok. Resume action
        my be needed.  If the audio is not present in the 1st call, click
        Cancel.
        """

        # TODO

        return "OK"

    @assert_description
    def TSC_ag_iut_dial_out_second(self, **kwargs):
        """
        Verify that the last number dialed on the Implementation Under Test
        (IUT) matches the TSPX_Second_phone_number entered in the IXIT settings.
        """

        # TODO

        return "OK"

    @assert_description
    def TSC_prepare_iut_for_vra(self, pts_addr: bytes, test: str, **kwargs):
        """
        Place the Implementation Under Test (IUT) in a state which will allow a
        request from the PTS to activate voice recognition, then click Ok.
        """

        if "HFP/HF" not in test:
            self.hfp.SetVoiceRecognition(
                enabled=True,
                connection=self.connection,
            )

        return "OK"

    @assert_description
    def TSC_prepare_iut_for_vrd(self, **kwargs):
        """
        Place the Implementation Under Test (IUT) in a state which will allow a
        voice recognition deactivation from PTS, then click Ok.
        """

        return "OK"

    @assert_description
    def TSC_ag_iut_clear_call_history(self, **kwargs):
        """
        Clear the call history on  the Implementation Under Test (IUT) such that
        there are zero records of any numbers dialed, then click Ok.
        """

        self.hfp.ClearCallHistory()

        return "OK"

    @assert_description
    def TSC_reject_call(self, test: str, pts_addr: bytes, **kwargs):
        """
        Click Ok, then reject the incoming call using the Implemention Under
        Test (IUT).
        """

        def reject_call():
            time.sleep(2)
            if "HFP/HF" in test:
                self.hfp.DeclineCallAsHandsfree(connection=self.connection)
            else:
                self.hfp.DeclineCall()

        threading.Thread(target=reject_call).start()

        return "OK"

    @assert_description
    def TSC_hf_iut_answer_call(self, pts_addr: bytes, **kwargs):
        """
        Click Ok, then answer the incoming call using the Implementation Under
        Test (IUT).
        """

        def answer_call():
            time.sleep(2)
            self.hfp.AnswerCallAsHandsfree(connection=self.connection)

        threading.Thread(target=answer_call).start()

        return "OK"

    @assert_description
    def TSC_iut_disable_audio_poweroff_ok(self, **kwargs):
        """
        Click Ok, then close the audio connection (SCO) by one of the following
        ways:

        1. Close the service level connection (SLC)
        2. Powering off the
        Implementation Under Test (IUT)
        """

        self.host.Reset()

        return "OK"

    @assert_description
    def TSC_verify_inband_ring(self, **kwargs):
        """
        Verify that the in-band ringtone is audible, then click Ok.
        """

        return "OK"

    @assert_description
    def TSC_verify_inband_ring_muting(self, **kwargs):
        """
        Verify that the in-band ringtone is not audible , then click Ok.
        """

        return "OK"

    @assert_description
    def TSC_hf_iut_disable_call(self, pts_addr: bytes, **kwargs):
        """
        Click Ok, then end the call process from the Implementation Under Test
        (IUT).
        """

        def disable_call():
            time.sleep(2)
            self.hfp.EndCallAsHandsfree(connection=self.connection)

        threading.Thread(target=disable_call).start()

        return "OK"

    @assert_description
    def TSC_mute_inband_ring_iut(self, **kwargs):
        """
        Mute the in-band ringtone on the Implementation Under Test (IUT) and
        then click OK.
        """

        return "OK"

    @assert_description
    def TSC_verify_iut_alerting(self, **kwargs):
        """
        Verify that the Implementation Under Test (IUT) is generating a local
        alert, then click Ok.
        """

        return "OK"

    @assert_description
    def TSC_verify_iut_not_alerting(self, **kwargs):
        """
        Verify that the Implementation Under Test (IUT) is not generating a
        local alert.
        """

        return "OK"

    @assert_description
    def TSC_hf_iut_enable_call_number(self, pts_addr: bytes, **kwargs):
        """
        Click Ok, then place an outgoing call from the Implementation Under Test
        (IUT) using an enterted phone number.
        """

        def disable_call():
            time.sleep(2)
            self.hfp.MakeCallAsHandsfree(connection=self.connection, number="42")

        threading.Thread(target=disable_call).start()

        return "OK"

    @assert_description
    def TSC_hf_iut_enable_call_memory(self, **kwargs):
        """
        Click Ok, then place an outgoing call from the Implementation Under Test
        (IUT) by entering the memory index.  For further clarification please
        see the HFP 1.5 Specification.
        """

        def enable_call():
            time.sleep(2)
            self.hfp.MakeCallAsHandsfree(connection=self.connection, number=">1")

        threading.Thread(target=enable_call).start()

        return "OK"

    @assert_description
    def TSC_hf_iut_call_swap_then_disable_held_alternative(self, pts_addr: bytes, **kwargs):
        """
        Using the Implementation Under Test (IUT), perform one of the following
        two actions:

        1. Click OK, make the held/waiting call active, disabling
        the active call.
        2. Click OK, make the held/waiting call active, placing
        the active call on hold.
        """

        def call_swap_then_disable_held_alternative():
            time.sleep(2)
            self.hfp.CallTransferAsHandsfree(connection=self.connection)

        threading.Thread(target=call_swap_then_disable_held_alternative).start()

        return "OK"

    @assert_description
    def TSC_iut_make_discoverable(self, **kwargs):
        """
        Place the Implementation Under Test (IUT) in discoverable mode, then
        click Ok.
        """

        self.host.SetDiscoverabilityMode(mode=DISCOVERABLE_GENERAL)

        return "OK"

    @assert_description
    def TSC_iut_accept_connection(self, **kwargs):
        """
        Click Ok, then accept the pairing and connection requests on the
        Implementation Under Test (IUT), if prompted.
        """

        return "OK"

    @assert_description
    def TSC_voice_recognition_enable_iut(self, pts_addr: bytes, **kwargs):
        """
        Using the Implementation Under Test (IUT), activate voice recognition.
        """

        self.hfp.SetVoiceRecognitionAsHandsfree(
            enabled=True,
            connection=self.connection,
        )

        return "OK"

    @assert_description
    def TSC_voice_recognition_disable_iut(self, pts_addr: bytes, **kwargs):
        """
        Using the Implementation Under Test (IUT), deactivate voice recognition.
        """

        self.hfp.SetVoiceRecognitionAsHandsfree(
            enabled=False,
            connection=self.connection,
        )

        return "OK"

    @match_description
    def TSC_dtmf_send(self, pts_addr: bytes, dtmf: str, **kwargs):
        r"""
        Send the DTMF code, then click Ok. (?P<dtmf>.*)
        """

        self.hfp.SendDtmfFromHandsfree(
            connection=self.connection,
            code=dtmf[0].encode("ascii")[0],
        )

        return "OK"

    @assert_description
    def TSC_verify_hf_iut_reports_held_and_active_call(self, **kwargs):
        """
        Verify that the Implementation Under Test (IUT) interprets both held and
        active call signals, then click Ok.  If applicable, verify that the
        information is correctly displayed on the IUT, then click Ok.
        """

        return "OK"

    def TSC_rf_shield_iut_or_pts(self, **kwargs):
        """
        Click Ok, then move the PTS and the Implementation Under Test (IUT) out
        of range of each other by performing one of the following IUT specific
        actions:

        1. Hands Free (HF) IUT - Place the IUT in the RF shield box or
        physically take out of range from the PTS.

        2. Audio Gateway (AG) IUT-
        Physically take the IUT out range.  Do not place in the RF shield box as
        it will interfere with the cellular network.

        Note: The PTS can also be
        placed in the RF shield box if necessary.
        """

        def shield_iut_or_pts():
            time.sleep(2)
            self.rootcanal.move_out_of_range()

        threading.Thread(target=shield_iut_or_pts).start()

        return "OK"

    @assert_description
    def TSC_rf_shield_open(self, **kwargs):
        """
        Click Ok, then remove the Implementation Under Test (IUT) and/or the PTS
        from the RF shield.  If the out of range method was used, bring the IUT
        and PTS back within range.
        """

        def shield_open():
            time.sleep(2)
            self.rootcanal.move_in_range()

        threading.Thread(target=shield_open).start()

        return "OK"

    @match_description
    def TSC_verify_speaker_volume(self, volume: str, **kwargs):
        r"""
        Verify that the Hands Free \(HF\) speaker volume is displayed correctly on
        the Implementation Under Test \(IUT\).(?P<volume>[0-9]*)
        """

        return "OK"

    @assert_description
    def TSC_ag_iut_clear_memory(self, **kwargs):
        """
        Clear the memory indexed by TSPX_phone_number_memory on the AG such that
        the memory slot becomes empty, then Click OK.
        """
        self.hfp.ClearCallHistory()

        return "OK"

    @assert_description
    def TSC_enable_call_memory_invalid_tester(self, **kwargs):
        """
        The PTS will send a call request containing an invalid/out of range
        memory index from the TSPX_phone_number_memory_invalid_index found in
        the IXIT settings.
        """

        return "OK"

    @assert_description
    def TSC_hf_iut_enable_call_memory_invalid(self, pts_addr: bytes, **kwargs):
        """
        Click Ok, then attempt to place an outgoing call from the Implementation
        Under Test (IUT) by entering a memory index which does not equal the
        TSPX_phone_number_memory.  For further clarification please see the HFP
        1.5 Specification.
        """

        def enable_call():
            time.sleep(2)
            self.hfp.MakeCallAsHandsfree(connection=self.connection, number=">9999")

        threading.Thread(target=enable_call).start()

        return "OK"

    @assert_description
    def TSC_hf_iut_verify_call_disable(self, **kwargs):
        """
        Verify that the call is disabled on the Implementation Under Test (IUT)
        and then click Ok.
        """

        return "OK"

    @assert_description
    def TSC_verify_subscriber_number_unsupported(self, **kwargs):
        """
        Using the Implementation Under Test (IUT), verify that the subscriber
        number information is not supported by the PTS, then click Ok.
        """

        return "OK"

    @assert_description
    def TSC_query_call_list(self, **kwargs):
        """
        Using the Implementation Under Test (IUT), query the list of currents
        calls on the Audio Gateway (AG), then click Ok.
        """

        return "OK"

    @assert_description
    def TSC_verify_clcc_receipt(self, **kwargs):
        """
        Verify that the +CLCC response(s) received by the Implementation Under
        Test (IUT) contains the correct call status information, then click Ok.
        """

        return "OK"

    @assert_description
    def TSC_impair_ag_signal_expect_no_notification(self, **kwargs):
        """
        Impair the signal to the AG so that a reduction in signal strength can
        be observed. Then, click OK.
        """

        return "OK"

    @assert_description
    def TSC_hf_iut_enable_call_last(self, pts_addr: bytes, **kwargs):
        """
        Click Ok, then place an outgoing call to the last number dialed on the
        Implementation Under Test (IUT).
        """

        def enable_call():
            time.sleep(2)
            self.hfp.MakeCallAsHandsfree(connection=self.connection, number="123")

        threading.Thread(target=enable_call).start()

        return "OK"

    @assert_description
    def TSC_hf_disable_ecnr(self, **kwargs):
        """
        Using the Implemenation Under Test (IUT), disable  EC/NR, then click Ok.
        """

        return "OK"

    @assert_description
    def TSC_ag_iut_answer_call(self, **kwargs):
        """
        Click Ok, then answer the incoming call on the Implementation Under Test
        (IUT).
        """

        self.hfp.AnswerCall()

        return "OK"

    @assert_description
    def TSC_verify_iut_ignore_wrong_bind(self, **kwargs):
        """
        Verify IUT ignores unkown or unexpected indication code. Then click OK.
        """

        return "OK"

    def _auto_confirm_requests(self, times=None):

        def task():
            cnt = 0
            pairing_events = self.security.OnPairing()
            for event in pairing_events:
                if event.WhichOneof("method") in {"just_works", "numeric_comparison"}:
                    if times is None or cnt < times:
                        cnt += 1
                        pairing_events.send(PairingEventAnswer(event=event, confirm=True))

        threading.Thread(target=task).start()
