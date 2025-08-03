#!/usr/bin/env python3
#
#   Copyright 2021 - Google
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import re
import time
from queue import Empty
from acts import signals
from acts.logger import epoch_to_log_line_timestamp
from acts.utils import get_current_epoch_time
from acts_contrib.test_utils.tel.loggers.protos.telephony_metric_pb2 import TelephonyVoiceTestResult
from acts_contrib.test_utils.tel.tel_defines import CarrierConfigs
from acts_contrib.test_utils.tel.tel_defines import CARRIER_NTT_DOCOMO, CARRIER_KDDI, CARRIER_RAKUTEN, CARRIER_SBM
from acts_contrib.test_utils.tel.tel_defines import CALL_PROPERTY_HIGH_DEF_AUDIO
from acts_contrib.test_utils.tel.tel_defines import CALL_STATE_ACTIVE
from acts_contrib.test_utils.tel.tel_defines import CALL_STATE_HOLDING
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_VOLTE
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_WFC
from acts_contrib.test_utils.tel.tel_defines import DIRECTION_MOBILE_ORIGINATED
from acts_contrib.test_utils.tel.tel_defines import GEN_2G
from acts_contrib.test_utils.tel.tel_defines import GEN_3G
from acts_contrib.test_utils.tel.tel_defines import INCALL_UI_DISPLAY_BACKGROUND
from acts_contrib.test_utils.tel.tel_defines import INCALL_UI_DISPLAY_FOREGROUND
from acts_contrib.test_utils.tel.tel_defines import INVALID_SUB_ID
from acts_contrib.test_utils.tel.tel_defines import MAX_SAVED_VOICE_MAIL
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_ACCEPT_CALL_TO_OFFHOOK_EVENT
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_CALL_DROP
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_CALL_IDLE_EVENT
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_CALL_INITIATION
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_CALLEE_RINGING
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_TELECOM_RINGING
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_VOICE_MAIL_COUNT
from acts_contrib.test_utils.tel.tel_defines import NETWORK_SERVICE_DATA
from acts_contrib.test_utils.tel.tel_defines import NETWORK_SERVICE_VOICE
from acts_contrib.test_utils.tel.tel_defines import RAT_1XRTT
from acts_contrib.test_utils.tel.tel_defines import RAT_IWLAN
from acts_contrib.test_utils.tel.tel_defines import RAT_LTE
from acts_contrib.test_utils.tel.tel_defines import RAT_UMTS
from acts_contrib.test_utils.tel.tel_defines import RAT_UNKNOWN
from acts_contrib.test_utils.tel.tel_defines import TELEPHONY_STATE_IDLE
from acts_contrib.test_utils.tel.tel_defines import TELEPHONY_STATE_OFFHOOK
from acts_contrib.test_utils.tel.tel_defines import TELEPHONY_STATE_RINGING
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_ANDROID_STATE_SETTLING
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_BETWEEN_REG_AND_CALL
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_BETWEEN_STATE_CHECK
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_IN_CALL
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_LEAVE_VOICE_MAIL
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_REJECT_CALL
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_VOICE_MAIL_SERVER_RESPONSE
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import EventCallStateChanged
from acts_contrib.test_utils.tel.tel_defines import EventMessageWaitingIndicatorChanged
from acts_contrib.test_utils.tel.tel_defines import CallStateContainer
from acts_contrib.test_utils.tel.tel_defines import MessageWaitingIndicatorContainer
from acts_contrib.test_utils.tel.tel_ims_utils import is_wfc_enabled
from acts_contrib.test_utils.tel.tel_ims_utils import toggle_volte
from acts_contrib.test_utils.tel.tel_ims_utils import toggle_wfc
from acts_contrib.test_utils.tel.tel_ims_utils import set_wfc_mode
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_volte_enabled
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_wfc_enabled
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_wfc_disabled
from acts_contrib.test_utils.tel.tel_lookup_tables import get_voice_mail_delete_digit
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_phone_setup_utils import wait_for_network_rat
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phone_subscription
from acts_contrib.test_utils.tel.tel_phone_setup_utils import wait_for_not_network_rat
from acts_contrib.test_utils.tel.tel_phone_setup_utils import wait_for_voice_attach
from acts_contrib.test_utils.tel.tel_subscription_utils import get_incoming_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import set_subid_for_outgoing_call
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_from_slot_index
from acts_contrib.test_utils.tel.tel_test_utils import _wait_for_droid_in_state
from acts_contrib.test_utils.tel.tel_test_utils import check_call_state_connected_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import check_call_state_idle_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import check_phone_number_match
from acts_contrib.test_utils.tel.tel_test_utils import check_voice_mail_count
from acts_contrib.test_utils.tel.tel_test_utils import check_voice_network_type
from acts_contrib.test_utils.tel.tel_test_utils import get_call_uri
from acts_contrib.test_utils.tel.tel_test_utils import get_device_epoch_time
from acts_contrib.test_utils.tel.tel_test_utils import get_network_gen_for_subscription
from acts_contrib.test_utils.tel.tel_test_utils import get_network_rat
from acts_contrib.test_utils.tel.tel_test_utils import get_network_rat_for_subscription
from acts_contrib.test_utils.tel.tel_test_utils import get_number_from_tel_uri
from acts_contrib.test_utils.tel.tel_test_utils import get_operator_name
from acts_contrib.test_utils.tel.tel_test_utils import get_user_config_profile
from acts_contrib.test_utils.tel.tel_test_utils import get_voice_mail_number
from acts_contrib.test_utils.tel.tel_test_utils import is_event_match
from acts_contrib.test_utils.tel.tel_test_utils import is_event_match_for_list
from acts_contrib.test_utils.tel.tel_test_utils import num_active_calls
from acts_contrib.test_utils.tel.tel_test_utils import TelResultWrapper
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import verify_incall_state
from acts_contrib.test_utils.tel.tel_test_utils import wait_for_state
from acts_contrib.test_utils.tel.tel_wifi_utils import ensure_wifi_connected
from acts_contrib.test_utils.tel.tel_wifi_utils import wifi_toggle_state

CallResult = TelephonyVoiceTestResult.CallResult.Value
result_dict ={}
voice_call_type = {}


def check_call_status(ad, voice_type_init=None, voice_type_in_call=None):
    """"
    Args:
        ad: Android device object
        voice_type_init: Voice network type before initiate call
        voice_type_in_call: Voice network type in call state

    Return:
         voice_call_type_dict: Voice call status
    """
    dut = str(ad.serial)
    network_type = voice_type_init + "_" + voice_type_in_call
    if network_type == "NR_NR":
        voice_call_type_dict = update_voice_call_type_dict(dut, "VoNR")
    elif network_type == "NR_LTE":
        voice_call_type_dict = update_voice_call_type_dict(dut, "EPSFB")
    elif network_type == "LTE_LTE":
        voice_call_type_dict = update_voice_call_type_dict(dut, "VoLTE")
    elif (network_type == "LTE_WCDMA" or network_type == "LTE_EDGE" or
        network_type == "LTE_GSM"):
        voice_call_type_dict = update_voice_call_type_dict(dut, "CSFB")
    elif network_type == "EDGE_EDGE":
        voice_call_type_dict = update_voice_call_type_dict(dut, "EDGE")
    elif network_type == "GSM_GSM":
        voice_call_type_dict = update_voice_call_type_dict(dut, "GSM")
    else:
        voice_call_type_dict = update_voice_call_type_dict(dut, "UNKNOWN")
    return voice_call_type_dict


def update_voice_call_type_dict(dut, key):
    """
    Args:
        dut: Serial Number of android device object
        key: Network subscription parameter (VoNR or EPSFB or VoLTE or CSFB or UNKNOWN)
    Return:
        voice_call_type: Voice call status
    """
    if dut not in voice_call_type.keys():
        voice_call_type[dut] = {key:0}
    if key not in voice_call_type[dut].keys():
        voice_call_type[dut].update({key:0})
    voice_call_type[dut][key] += 1
    return voice_call_type


def dial_phone_number(ad, callee_number):
    for number in str(callee_number):
        if number == "#":
            ad.send_keycode("POUND")
        elif number == "*":
            ad.send_keycode("STAR")
        elif number in ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"]:
            ad.send_keycode("%s" % number)


def disconnect_call_by_id(log, ad, call_id):
    """Disconnect call by call id.
    """
    ad.droid.telecomCallDisconnect(call_id)
    return True


def dumpsys_last_call_info(ad):
    """ Get call information by dumpsys telecom. """
    num = dumpsys_last_call_number(ad)
    output = ad.adb.shell("dumpsys telecom")
    result = re.search(r"Call TC@%s: {(.*?)}" % num, output, re.DOTALL)
    call_info = {"TC": num}
    if result:
        result = result.group(1)
        for attr in ("startTime", "endTime", "direction", "isInterrupted",
                     "callTechnologies", "callTerminationsReason",
                     "isVideoCall", "callProperties"):
            match = re.search(r"%s: (.*)" % attr, result)
            if match:
                if attr in ("startTime", "endTime"):
                    call_info[attr] = epoch_to_log_line_timestamp(
                        int(match.group(1)))
                else:
                    call_info[attr] = match.group(1)
    ad.log.debug("call_info = %s", call_info)
    return call_info


def dumpsys_last_call_number(ad):
    output = ad.adb.shell("dumpsys telecom")
    call_nums = re.findall("Call TC@(\d+):", output)
    if not call_nums:
        return 0
    else:
        return int(call_nums[-1])


def dumpsys_new_call_info(ad, last_tc_number, retries=3, interval=5):
    for i in range(retries):
        if dumpsys_last_call_number(ad) > last_tc_number:
            call_info = dumpsys_last_call_info(ad)
            ad.log.info("New call info = %s", sorted(call_info.items()))
            return call_info
        else:
            time.sleep(interval)
    ad.log.error("New call is not in sysdump telecom")
    return {}


def emergency_dialer_call_by_keyevent(ad, callee_number):
    for i in range(3):
        if "EmergencyDialer" in ad.get_my_current_focus_window():
            ad.log.info("EmergencyDialer is the current focus window")
            break
        elif i <= 2:
            ad.adb.shell("am start -a com.android.phone.EmergencyDialer.DIAL")
            time.sleep(1)
        else:
            ad.log.error("Unable to bring up EmergencyDialer")
            return False
    ad.log.info("Make a phone call to %s", callee_number)
    dial_phone_number(ad, callee_number)
    ad.send_keycode("CALL")


def get_current_voice_rat(log, ad):
    """Return current Voice RAT

    Args:
        ad: Android device object.
    """
    return get_current_voice_rat_for_subscription(
        log, ad, get_outgoing_voice_sub_id(ad))


def get_current_voice_rat_for_subscription(log, ad, sub_id):
    """Return current Voice RAT for subscription id.

    Args:
        ad: Android device object.
        sub_id: subscription id.
    """
    return get_network_rat_for_subscription(log, ad, sub_id,
                                            NETWORK_SERVICE_VOICE)


def hangup_call_by_adb(ad):
    """Make emergency call by EmergencyDialer.

    Args:
        ad: Caller android device object.
        callee_number: Callee phone number.
    """
    ad.log.info("End call by adb")
    ad.send_keycode("ENDCALL")


def hangup_call(log, ad, is_emergency=False):
    """Hang up ongoing active call.

    Args:
        log: log object.
        ad: android device object.

    Returns:
        True: if all calls are cleared
        False: for errors
    """
    # short circuit in case no calls are active
    if not ad.droid.telecomIsInCall():
        ad.log.warning("No active call exists.")
        return True
    ad.ed.clear_events(EventCallStateChanged)
    ad.droid.telephonyStartTrackingCallState()
    ad.log.info("Hangup call.")
    if is_emergency:
        for call in ad.droid.telecomCallGetCallIds():
            ad.droid.telecomCallDisconnect(call)
    else:
        ad.droid.telecomEndCall()

    try:
        ad.ed.wait_for_event(
            EventCallStateChanged,
            is_event_match,
            timeout=MAX_WAIT_TIME_CALL_IDLE_EVENT,
            field=CallStateContainer.CALL_STATE,
            value=TELEPHONY_STATE_IDLE)
    except Empty:
        ad.log.warning("Call state IDLE event is not received after hang up.")
    finally:
        ad.droid.telephonyStopTrackingCallStateChange()
    if not wait_for_state(ad.droid.telecomIsInCall, False, 15, 1):
        ad.log.error("Telecom is in call, hangup call failed.")
        return False
    return True


def initiate_emergency_dialer_call_by_adb(
        log,
        ad,
        callee_number,
        timeout=MAX_WAIT_TIME_CALL_INITIATION,
        checking_interval=5):
    """Make emergency call by EmergencyDialer.

    Args:
        ad: Caller android device object.
        callee_number: Callee phone number.
        emergency : specify the call is emergency.
        Optional. Default value is False.

    Returns:
        result: if phone call is placed successfully.
    """
    try:
        # Make a Call
        ad.wakeup_screen()
        ad.send_keycode("MENU")
        ad.log.info("Call %s", callee_number)
        ad.adb.shell("am start -a com.android.phone.EmergencyDialer.DIAL")
        ad.adb.shell(
            "am start -a android.intent.action.CALL_EMERGENCY -d tel:%s" %
            callee_number)
        if not timeout: return True
        ad.log.info("Check call state")
        # Verify Call State
        elapsed_time = 0
        while elapsed_time < timeout:
            time.sleep(checking_interval)
            elapsed_time += checking_interval
            if check_call_state_connected_by_adb(ad):
                ad.log.info("Call to %s is connected", callee_number)
                return True
            if check_call_state_idle_by_adb(ad):
                ad.log.info("Call to %s failed", callee_number)
                return False
        ad.log.info("Make call to %s failed", callee_number)
        return False
    except Exception as e:
        ad.log.error("initiate emergency call failed with error %s", e)


def initiate_call(log,
                  ad,
                  callee_number,
                  emergency=False,
                  incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND,
                  video=False):
    """Make phone call from caller to callee.

    Args:
        ad_caller: Caller android device object.
        callee_number: Callee phone number.
        emergency : specify the call is emergency.
            Optional. Default value is False.
        incall_ui_display: show the dialer UI foreground or backgroud
        video: whether to initiate as video call

    Returns:
        result: if phone call is placed successfully.
    """
    ad.ed.clear_events(EventCallStateChanged)
    sub_id = get_outgoing_voice_sub_id(ad)
    begin_time = get_device_epoch_time(ad)
    ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)
    try:
        # Make a Call
        ad.log.info("Make a phone call to %s", callee_number)
        if emergency:
            ad.droid.telecomCallEmergencyNumber(callee_number)
        else:
            ad.droid.telecomCallNumber(callee_number, video)

        # Verify OFFHOOK state
        if not wait_for_call_offhook_for_subscription(
                log, ad, sub_id, event_tracking_started=True):
            ad.log.info("sub_id %s not in call offhook state", sub_id)
            last_call_drop_reason(ad, begin_time=begin_time)
            return False
        else:
            return True

    finally:
        if hasattr(ad, "sdm_log") and getattr(ad, "sdm_log"):
            ad.adb.shell("i2cset -fy 3 64 6 1 b", ignore_status=True)
            ad.adb.shell("i2cset -fy 3 65 6 1 b", ignore_status=True)
        ad.droid.telephonyStopTrackingCallStateChangeForSubscription(sub_id)

        if incall_ui_display == INCALL_UI_DISPLAY_FOREGROUND:
            ad.droid.telecomShowInCallScreen()
        elif incall_ui_display == INCALL_UI_DISPLAY_BACKGROUND:
            ad.droid.showHomeScreen()


def last_call_drop_reason(ad, begin_time=None):
    reasons = ad.search_logcat(
        "qcril_qmi_voice_map_qmi_to_ril_last_call_failure_cause", begin_time)
    reason_string = ""
    if reasons:
        log_msg = "Logcat call drop reasons:"
        for reason in reasons:
            log_msg = "%s\n\t%s" % (log_msg, reason["log_message"])
            if "ril reason str" in reason["log_message"]:
                reason_string = reason["log_message"].split(":")[-1].strip()
        ad.log.info(log_msg)
    reasons = ad.search_logcat("ACTION_FORBIDDEN_NO_SERVICE_AUTHORIZATION",
                               begin_time)
    if reasons:
        ad.log.warning("ACTION_FORBIDDEN_NO_SERVICE_AUTHORIZATION is seen")
    ad.log.info("last call dumpsys: %s",
                sorted(dumpsys_last_call_info(ad).items()))
    return reason_string


def call_reject(log, ad_caller, ad_callee, reject=True):
    """Caller call Callee, then reject on callee.


    """
    subid_caller = ad_caller.droid.subscriptionGetDefaultVoiceSubId()
    subid_callee = ad_callee.incoming_voice_sub_id
    ad_caller.log.info("Sub-ID Caller %s, Sub-ID Callee %s", subid_caller,
                       subid_callee)
    return call_reject_for_subscription(log, ad_caller, ad_callee,
                                        subid_caller, subid_callee, reject)


def call_reject_for_subscription(log,
                                 ad_caller,
                                 ad_callee,
                                 subid_caller,
                                 subid_callee,
                                 reject=True):
    """
    """

    caller_number = ad_caller.telephony['subscription'][subid_caller][
        'phone_num']
    callee_number = ad_callee.telephony['subscription'][subid_callee][
        'phone_num']

    ad_caller.log.info("Call from %s to %s", caller_number, callee_number)
    if not initiate_call(log, ad_caller, callee_number):
        ad_caller.log.error("Initiate call failed")
        return False

    if not wait_and_reject_call_for_subscription(
            log, ad_callee, subid_callee, caller_number, WAIT_TIME_REJECT_CALL,
            reject):
        ad_callee.log.error("Reject call fail.")
        return False
    # Check if incoming call is cleared on callee or not.
    if ad_callee.droid.telephonyGetCallStateForSubscription(
            subid_callee) == TELEPHONY_STATE_RINGING:
        ad_callee.log.error("Incoming call is not cleared")
        return False
    # Hangup on caller
    hangup_call(log, ad_caller)
    return True


def call_reject_leave_message(log,
                              ad_caller,
                              ad_callee,
                              verify_caller_func=None,
                              wait_time_in_call=WAIT_TIME_LEAVE_VOICE_MAIL):
    """On default voice subscription, Call from caller to callee,
    reject on callee, caller leave a voice mail.

    1. Caller call Callee.
    2. Callee reject incoming call.
    3. Caller leave a voice mail.
    4. Verify callee received the voice mail notification.

    Args:
        ad_caller: caller android device object.
        ad_callee: callee android device object.
        verify_caller_func: function to verify caller is in correct state while in-call.
            This is optional, default is None.
        wait_time_in_call: time to wait when leaving a voice mail.
            This is optional, default is WAIT_TIME_LEAVE_VOICE_MAIL

    Returns:
        True: if voice message is received on callee successfully.
        False: for errors
    """
    subid_caller = get_outgoing_voice_sub_id(ad_caller)
    subid_callee = get_incoming_voice_sub_id(ad_callee)
    return call_reject_leave_message_for_subscription(
        log, ad_caller, ad_callee, subid_caller, subid_callee,
        verify_caller_func, wait_time_in_call)


def check_reject_needed_for_voice_mail(log, ad_callee):
    """Check if the carrier requires reject call to receive voice mail or just keep ringing
    Requested in b//155935290
    Four Japan carriers do not need to reject
    SBM, KDDI, Ntt Docomo, Rakuten
    Args:
        log: log object
        ad_callee: android device object
    Returns:
        True if callee's carrier is not one of the four Japan carriers
        False if callee's carrier is one of the four Japan carriers
    """

    operators_no_reject = [CARRIER_NTT_DOCOMO,
                           CARRIER_KDDI,
                           CARRIER_RAKUTEN,
                           CARRIER_SBM]
    operator_name = get_operator_name(log, ad_callee)

    return operator_name not in operators_no_reject


def _is_on_message_waiting_event_true(event):
    """Private function to return if the received EventMessageWaitingIndicatorChanged
    event MessageWaitingIndicatorContainer.IS_MESSAGE_WAITING field is True.
    """
    return event['data'][MessageWaitingIndicatorContainer.IS_MESSAGE_WAITING]


def call_reject_leave_message_for_subscription(
        log,
        ad_caller,
        ad_callee,
        subid_caller,
        subid_callee,
        verify_caller_func=None,
        wait_time_in_call=WAIT_TIME_LEAVE_VOICE_MAIL):
    """On specific voice subscription, Call from caller to callee,
    reject on callee, caller leave a voice mail.

    1. Caller call Callee.
    2. Callee reject incoming call.
    3. Caller leave a voice mail.
    4. Verify callee received the voice mail notification.

    Args:
        ad_caller: caller android device object.
        ad_callee: callee android device object.
        subid_caller: caller's subscription id.
        subid_callee: callee's subscription id.
        verify_caller_func: function to verify caller is in correct state while in-call.
            This is optional, default is None.
        wait_time_in_call: time to wait when leaving a voice mail.
            This is optional, default is WAIT_TIME_LEAVE_VOICE_MAIL

    Returns:
        True: if voice message is received on callee successfully.
        False: for errors
    """

    # Currently this test utility only works for TMO and ATT and SPT.
    # It does not work for VZW (see b/21559800)
    # "with VVM TelephonyManager APIs won't work for vm"

    caller_number = ad_caller.telephony['subscription'][subid_caller][
        'phone_num']
    callee_number = ad_callee.telephony['subscription'][subid_callee][
        'phone_num']

    ad_caller.log.info("Call from %s to %s", caller_number, callee_number)

    try:
        voice_mail_count_before = ad_callee.droid.telephonyGetVoiceMailCountForSubscription(
            subid_callee)
        ad_callee.log.info("voice mail count is %s", voice_mail_count_before)
        # -1 means there are unread voice mail, but the count is unknown
        # 0 means either this API not working (VZW) or no unread voice mail.
        if voice_mail_count_before != 0:
            log.warning("--Pending new Voice Mail, please clear on phone.--")

        if not initiate_call(log, ad_caller, callee_number):
            ad_caller.log.error("Initiate call failed.")
            return False
        if check_reject_needed_for_voice_mail(log, ad_callee):
            carrier_specific_delay_reject = 30
        else:
            carrier_specific_delay_reject = 2
        carrier_reject_call = not check_reject_needed_for_voice_mail(log, ad_callee)

        if not wait_and_reject_call_for_subscription(
                log, ad_callee, subid_callee, incoming_number=caller_number, delay_reject=carrier_specific_delay_reject,
                reject=carrier_reject_call):
            ad_callee.log.error("Reject call fail.")
            return False

        ad_callee.droid.telephonyStartTrackingVoiceMailStateChangeForSubscription(
            subid_callee)

        # ensure that all internal states are updated in telecom
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
        ad_callee.ed.clear_events(EventCallStateChanged)

        if verify_caller_func and not verify_caller_func(log, ad_caller):
            ad_caller.log.error("Caller not in correct state!")
            return False

        # TODO: b/26293512 Need to play some sound to leave message.
        # Otherwise carrier voice mail server may drop this voice mail.
        time.sleep(wait_time_in_call)

        if not verify_caller_func:
            caller_state_result = ad_caller.droid.telecomIsInCall()
        else:
            caller_state_result = verify_caller_func(log, ad_caller)
        if not caller_state_result:
            ad_caller.log.error("Caller not in correct state after %s seconds",
                                wait_time_in_call)

        if not hangup_call(log, ad_caller):
            ad_caller.log.error("Error in Hanging-Up Call")
            return False

        ad_callee.log.info("Wait for voice mail indicator on callee.")
        try:
            event = ad_callee.ed.wait_for_event(
                EventMessageWaitingIndicatorChanged,
                _is_on_message_waiting_event_true)
            ad_callee.log.info("Got event %s", event)
        except Empty:
            ad_callee.log.warning("No expected event %s",
                                  EventMessageWaitingIndicatorChanged)
            return False
        voice_mail_count_after = ad_callee.droid.telephonyGetVoiceMailCountForSubscription(
            subid_callee)
        ad_callee.log.info(
            "telephonyGetVoiceMailCount output - before: %s, after: %s",
            voice_mail_count_before, voice_mail_count_after)

        # voice_mail_count_after should:
        # either equals to (voice_mail_count_before + 1) [For ATT and SPT]
        # or equals to -1 [For TMO]
        # -1 means there are unread voice mail, but the count is unknown
        if not check_voice_mail_count(log, ad_callee, voice_mail_count_before,
                                      voice_mail_count_after):
            log.error("before and after voice mail count is not incorrect.")
            return False
    finally:
        ad_callee.droid.telephonyStopTrackingVoiceMailStateChangeForSubscription(
            subid_callee)
    return True


def call_voicemail_erase_all_pending_voicemail(log, ad):
    """Script for phone to erase all pending voice mail.
    This script only works for TMO and ATT and SPT currently.
    This script only works if phone have already set up voice mail options,
    and phone should disable password protection for voice mail.

    1. If phone don't have pending voice message, return True.
    2. Dial voice mail number.
        For TMO, the number is '123'
        For ATT, the number is phone's number
        For SPT, the number is phone's number
    3. Wait for voice mail connection setup.
    4. Wait for voice mail play pending voice message.
    5. Send DTMF to delete one message.
        The digit is '7'.
    6. Repeat steps 4 and 5 until voice mail server drop this call.
        (No pending message)
    6. Check telephonyGetVoiceMailCount result. it should be 0.

    Args:
        log: log object
        ad: android device object
    Returns:
        False if error happens. True is succeed.
    """
    log.info("Erase all pending voice mail.")
    count = ad.droid.telephonyGetVoiceMailCount()
    if count == 0:
        ad.log.info("No Pending voice mail.")
        return True
    if count == -1:
        ad.log.info("There is pending voice mail, but the count is unknown")
        count = MAX_SAVED_VOICE_MAIL
    else:
        ad.log.info("There are %s voicemails", count)

    voice_mail_number = get_voice_mail_number(log, ad)
    delete_digit = get_voice_mail_delete_digit(get_operator_name(log, ad))
    if not initiate_call(log, ad, voice_mail_number):
        log.error("Initiate call to voice mail failed.")
        return False
    time.sleep(WAIT_TIME_VOICE_MAIL_SERVER_RESPONSE)
    callId = ad.droid.telecomCallGetCallIds()[0]
    time.sleep(WAIT_TIME_VOICE_MAIL_SERVER_RESPONSE)
    while (is_phone_in_call(log, ad) and (count > 0)):
        ad.log.info("Press %s to delete voice mail.", delete_digit)
        ad.droid.telecomCallPlayDtmfTone(callId, delete_digit)
        ad.droid.telecomCallStopDtmfTone(callId)
        time.sleep(WAIT_TIME_VOICE_MAIL_SERVER_RESPONSE)
        count -= 1
    if is_phone_in_call(log, ad):
        hangup_call(log, ad)

    # wait for telephonyGetVoiceMailCount to update correct result
    remaining_time = MAX_WAIT_TIME_VOICE_MAIL_COUNT
    while ((remaining_time > 0)
           and (ad.droid.telephonyGetVoiceMailCount() != 0)):
        time.sleep(1)
        remaining_time -= 1
    current_voice_mail_count = ad.droid.telephonyGetVoiceMailCount()
    ad.log.info("telephonyGetVoiceMailCount: %s", current_voice_mail_count)
    return (current_voice_mail_count == 0)


def call_setup_teardown(log,
                        ad_caller,
                        ad_callee,
                        ad_hangup=None,
                        verify_caller_func=None,
                        verify_callee_func=None,
                        wait_time_in_call=WAIT_TIME_IN_CALL,
                        incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND,
                        dialing_number_length=None,
                        video_state=None,
                        slot_id_callee=None,
                        voice_type_init=None,
                        call_stats_check=False,
                        result_info=result_dict):
    """ Call process, including make a phone call from caller,
    accept from callee, and hang up. The call is on default voice subscription

    In call process, call from <droid_caller> to <droid_callee>,
    accept the call, (optional)then hang up from <droid_hangup>.

    Args:
        ad_caller: Caller Android Device Object.
        ad_callee: Callee Android Device Object.
        ad_hangup: Android Device Object end the phone call.
            Optional. Default value is None, and phone call will continue.
        verify_call_mode_caller: func_ptr to verify caller in correct mode
            Optional. Default is None
        verify_call_mode_caller: func_ptr to verify caller in correct mode
            Optional. Default is None
        incall_ui_display: after answer the call, bring in-call UI to foreground or
            background. Optional, default value is INCALL_UI_DISPLAY_FOREGROUND.
            if = INCALL_UI_DISPLAY_FOREGROUND, bring in-call UI to foreground.
            if = INCALL_UI_DISPLAY_BACKGROUND, bring in-call UI to background.
            else, do nothing.
        dialing_number_length: the number of digits used for dialing
        slot_id_callee : the slot if of the callee to call to

    Returns:
        True if call process without any error.
        False if error happened.

    """
    subid_caller = get_outgoing_voice_sub_id(ad_caller)
    if slot_id_callee is None:
        subid_callee = get_incoming_voice_sub_id(ad_callee)
    else:
        subid_callee = get_subid_from_slot_index(log, ad_callee, slot_id_callee)

    return call_setup_teardown_for_subscription(
        log, ad_caller, ad_callee, subid_caller, subid_callee, ad_hangup,
        verify_caller_func, verify_callee_func, wait_time_in_call,
        incall_ui_display, dialing_number_length, video_state,
        voice_type_init, call_stats_check, result_info)


def call_setup_teardown_for_subscription(
        log,
        ad_caller,
        ad_callee,
        subid_caller,
        subid_callee,
        ad_hangup=None,
        verify_caller_func=None,
        verify_callee_func=None,
        wait_time_in_call=WAIT_TIME_IN_CALL,
        incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND,
        dialing_number_length=None,
        video_state=None,
        voice_type_init=None,
        call_stats_check=False,
        result_info=result_dict):
    """ Call process, including make a phone call from caller,
    accept from callee, and hang up. The call is on specified subscription

    In call process, call from <droid_caller> to <droid_callee>,
    accept the call, (optional)then hang up from <droid_hangup>.

    Args:
        ad_caller: Caller Android Device Object.
        ad_callee: Callee Android Device Object.
        subid_caller: Caller subscription ID
        subid_callee: Callee subscription ID
        ad_hangup: Android Device Object end the phone call.
            Optional. Default value is None, and phone call will continue.
        verify_call_mode_caller: func_ptr to verify caller in correct mode
            Optional. Default is None
        verify_call_mode_caller: func_ptr to verify caller in correct mode
            Optional. Default is None
        incall_ui_display: after answer the call, bring in-call UI to foreground or
            background. Optional, default value is INCALL_UI_DISPLAY_FOREGROUND.
            if = INCALL_UI_DISPLAY_FOREGROUND, bring in-call UI to foreground.
            if = INCALL_UI_DISPLAY_BACKGROUND, bring in-call UI to background.
            else, do nothing.

    Returns:
        TelResultWrapper which will evaluate as False if error.

    """
    CHECK_INTERVAL = 5
    begin_time = get_current_epoch_time()
    if not verify_caller_func:
        verify_caller_func = is_phone_in_call
    if not verify_callee_func:
        verify_callee_func = is_phone_in_call

    caller_number = ad_caller.telephony['subscription'][subid_caller][
        'phone_num']
    callee_number = ad_callee.telephony['subscription'][subid_callee][
        'phone_num']

    callee_number = truncate_phone_number(
        log,
        caller_number,
        callee_number,
        dialing_number_length)

    tel_result_wrapper = TelResultWrapper(CallResult('SUCCESS'))
    msg = "Call from %s to %s" % (caller_number, callee_number)
    if video_state:
        msg = "Video %s" % msg
        video = True
    else:
        video = False
    if ad_hangup:
        msg = "%s for duration of %s seconds" % (msg, wait_time_in_call)
    ad_caller.log.info(msg)

    for ad in (ad_caller, ad_callee):
        call_ids = ad.droid.telecomCallGetCallIds()
        setattr(ad, "call_ids", call_ids)
        if call_ids:
            ad.log.info("Pre-exist CallId %s before making call", call_ids)

    if not initiate_call(
            log,
            ad_caller,
            callee_number,
            incall_ui_display=incall_ui_display,
            video=video):
        ad_caller.log.error("Initiate call failed.")
        tel_result_wrapper.result_value = CallResult('INITIATE_FAILED')
        return tel_result_wrapper
    else:
        ad_caller.log.info("Caller initate call successfully")
    if not wait_and_answer_call_for_subscription(
            log,
            ad_callee,
            subid_callee,
            incoming_number=caller_number,
            caller=ad_caller,
            incall_ui_display=incall_ui_display,
            video_state=video_state):
        ad_callee.log.error("Answer call fail.")
        tel_result_wrapper.result_value = CallResult(
            'NO_RING_EVENT_OR_ANSWER_FAILED')
        return tel_result_wrapper
    else:
        ad_callee.log.info("Callee answered the call successfully")

    for ad, call_func in zip([ad_caller, ad_callee],
                                [verify_caller_func, verify_callee_func]):
        call_ids = ad.droid.telecomCallGetCallIds()
        new_call_ids = set(call_ids) - set(ad.call_ids)
        if not new_call_ids:
            ad.log.error(
                "No new call ids are found after call establishment")
            ad.log.error("telecomCallGetCallIds returns %s",
                            ad.droid.telecomCallGetCallIds())
            tel_result_wrapper.result_value = CallResult('NO_CALL_ID_FOUND')
        for new_call_id in new_call_ids:
            if not wait_for_in_call_active(ad, call_id=new_call_id):
                tel_result_wrapper.result_value = CallResult(
                    'CALL_STATE_NOT_ACTIVE_DURING_ESTABLISHMENT')
            else:
                ad.log.info("callProperties = %s",
                            ad.droid.telecomCallGetProperties(new_call_id))

        if not ad.droid.telecomCallGetAudioState():
            ad.log.error("Audio is not in call state")
            tel_result_wrapper.result_value = CallResult(
                'AUDIO_STATE_NOT_INCALL_DURING_ESTABLISHMENT')

        if call_func(log, ad):
            ad.log.info("Call is in %s state", call_func.__name__)
        else:
            ad.log.error("Call is not in %s state, voice in RAT %s",
                            call_func.__name__,
                            ad.droid.telephonyGetCurrentVoiceNetworkType())
            tel_result_wrapper.result_value = CallResult(
                'CALL_DROP_OR_WRONG_STATE_DURING_ESTABLISHMENT')
    if not tel_result_wrapper:
        return tel_result_wrapper

    if call_stats_check:
        voice_type_in_call = check_voice_network_type([ad_caller, ad_callee], voice_init=False)
        phone_a_call_type = check_call_status(ad_caller,
                                                voice_type_init[0],
                                                voice_type_in_call[0])
        result_info["Call Stats"] = phone_a_call_type
        ad_caller.log.debug("Voice Call Type: %s", phone_a_call_type)
        phone_b_call_type = check_call_status(ad_callee,
                                                voice_type_init[1],
                                                voice_type_in_call[1])
        result_info["Call Stats"] = phone_b_call_type
        ad_callee.log.debug("Voice Call Type: %s", phone_b_call_type)

    return wait_for_call_end(
        log,
        ad_caller,
        ad_callee,
        ad_hangup,
        verify_caller_func,
        verify_callee_func,
        begin_time,
        check_interval=CHECK_INTERVAL,
        tel_result_wrapper=TelResultWrapper(CallResult('SUCCESS')),
        wait_time_in_call=wait_time_in_call)


def two_phone_call_leave_voice_mail(
        log,
        caller,
        caller_idle_func,
        caller_in_call_check_func,
        callee,
        callee_idle_func,
        wait_time_in_call=WAIT_TIME_LEAVE_VOICE_MAIL):
    """Call from caller to callee, reject on callee, caller leave a voice mail.

    1. Caller call Callee.
    2. Callee reject incoming call.
    3. Caller leave a voice mail.
    4. Verify callee received the voice mail notification.

    Args:
        caller: caller android device object.
        caller_idle_func: function to check caller's idle state.
        caller_in_call_check_func: function to check caller's in-call state.
        callee: callee android device object.
        callee_idle_func: function to check callee's idle state.
        wait_time_in_call: time to wait when leaving a voice mail.
            This is optional, default is WAIT_TIME_LEAVE_VOICE_MAIL

    Returns:
        True: if voice message is received on callee successfully.
        False: for errors
    """

    ads = [caller, callee]

    # Make sure phones are idle.
    ensure_phones_idle(log, ads)
    if caller_idle_func and not caller_idle_func(log, caller):
        caller.log.error("Caller Failed to Reselect")
        return False
    if callee_idle_func and not callee_idle_func(log, callee):
        callee.log.error("Callee Failed to Reselect")
        return False

    # TODO: b/26337871 Need to use proper API to check phone registered.
    time.sleep(WAIT_TIME_BETWEEN_REG_AND_CALL)

    # Make call and leave a message.
    if not call_reject_leave_message(
            log, caller, callee, caller_in_call_check_func, wait_time_in_call):
        log.error("make a call and leave a message failed.")
        return False
    return True


def two_phone_call_short_seq(log,
                             phone_a,
                             phone_a_idle_func,
                             phone_a_in_call_check_func,
                             phone_b,
                             phone_b_idle_func,
                             phone_b_in_call_check_func,
                             call_sequence_func=None,
                             wait_time_in_call=WAIT_TIME_IN_CALL,
                             call_params=None):
    """Call process short sequence.
    1. Ensure phone idle and in idle_func check return True.
    2. Call from PhoneA to PhoneB, accept on PhoneB.
    3. Check phone state, hangup on PhoneA.
    4. Ensure phone idle and in idle_func check return True.
    5. Call from PhoneA to PhoneB, accept on PhoneB.
    6. Check phone state, hangup on PhoneB.

    Args:
        phone_a: PhoneA's android device object.
        phone_a_idle_func: function to check PhoneA's idle state.
        phone_a_in_call_check_func: function to check PhoneA's in-call state.
        phone_b: PhoneB's android device object.
        phone_b_idle_func: function to check PhoneB's idle state.
        phone_b_in_call_check_func: function to check PhoneB's in-call state.
        call_sequence_func: default parameter, not implemented.
        wait_time_in_call: time to wait in call.
            This is optional, default is WAIT_TIME_IN_CALL
        call_params: list of call parameters for both MO/MT devices, including:
            - MO device object
            - MT device object
            - Device object hanging up the call
            - Function to check the in-call state of MO device
            - Function to check the in-call state of MT device

            and the format should be:
            [(Args for the 1st call), (Args for the 2nd call), ......]

            The format of args for each call should be:
            (mo_device_obj, mt_device_obj, device_obj_hanging_up,
            mo_in_call_check_func, mt_in_call_check_func)

            Example of a call, which will not be hung up:

            call_params = [
                (ads[0], ads[1], None, mo_in_call_check_func,
                mt_in_call_check_func)
            ]

    Returns:
        TelResultWrapper which will evaluate as False if error.
    """
    ads = [phone_a, phone_b]

    if not call_params:
        call_params = [
            (ads[0], ads[1], ads[0], phone_a_in_call_check_func,
            phone_b_in_call_check_func),
            (ads[0], ads[1], ads[1], phone_a_in_call_check_func,
            phone_b_in_call_check_func),
        ]

    tel_result = TelResultWrapper(CallResult('SUCCESS'))
    for param in call_params:
        # Make sure phones are idle.
        ensure_phones_idle(log, ads)
        if phone_a_idle_func and not phone_a_idle_func(log, phone_a):
            phone_a.log.error("Phone A Failed to Reselect")
            return TelResultWrapper(CallResult('CALL_SETUP_FAILURE'))
        if phone_b_idle_func and not phone_b_idle_func(log, phone_b):
            phone_b.log.error("Phone B Failed to Reselect")
            return TelResultWrapper(CallResult('CALL_SETUP_FAILURE'))

        # TODO: b/26337871 Need to use proper API to check phone registered.
        time.sleep(WAIT_TIME_BETWEEN_REG_AND_CALL)

        # Make call.
        log.info("---> Call test: %s to %s <---", param[0].serial,
                 param[1].serial)
        tel_result = call_setup_teardown(
                log, *param, wait_time_in_call=wait_time_in_call)
        if not tel_result:
            log.error("Call Iteration Failed")
            break

    return tel_result

def two_phone_call_msim_short_seq(log,
                             phone_a,
                             phone_a_idle_func,
                             phone_a_in_call_check_func,
                             phone_b,
                             phone_b_idle_func,
                             phone_b_in_call_check_func,
                             call_sequence_func=None,
                             wait_time_in_call=WAIT_TIME_IN_CALL):
    """Call process short sequence.
    1. Ensure phone idle and in idle_func check return True.
    2. Call from PhoneA to PhoneB, accept on PhoneB.
    3. Check phone state, hangup on PhoneA.
    4. Ensure phone idle and in idle_func check return True.
    5. Call from PhoneA to PhoneB, accept on PhoneB.
    6. Check phone state, hangup on PhoneB.
    Args:
        phone_a: PhoneA's android device object.
        phone_a_idle_func: function to check PhoneA's idle state.
        phone_a_in_call_check_func: function to check PhoneA's in-call state.
        phone_b: PhoneB's android device object.
        phone_b_idle_func: function to check PhoneB's idle state.
        phone_b_in_call_check_func: function to check PhoneB's in-call state.
        call_sequence_func: default parameter, not implemented.
        wait_time_in_call: time to wait in call.
            This is optional, default is WAIT_TIME_IN_CALL
    Returns:
        True: if call sequence succeed.
        False: for errors
    """
    ads = [phone_a, phone_b]
    call_params = [
        (ads[0], ads[1], ads[0], phone_a_in_call_check_func,
         phone_b_in_call_check_func),
        (ads[0], ads[1], ads[1], phone_a_in_call_check_func,
         phone_b_in_call_check_func),
    ]
    for param in call_params:
        # Make sure phones are idle.
        ensure_phones_idle(log, ads)
        if phone_a_idle_func and not phone_a_idle_func(log, phone_a):
            phone_a.log.error("Phone A Failed to Reselect")
            return False
        if phone_b_idle_func and not phone_b_idle_func(log, phone_b):
            phone_b.log.error("Phone B Failed to Reselect")
            return False
        # TODO: b/26337871 Need to use proper API to check phone registered.
        time.sleep(WAIT_TIME_BETWEEN_REG_AND_CALL)
        # Make call.
        log.info("--> Call test: %s to %s <--", phone_a.serial, phone_b.serial)
        slots = 2
        for slot in range(slots):
            set_subid_for_outgoing_call(
                            ads[0], get_subid_from_slot_index(log,ads[0],slot))
            set_subid_for_outgoing_call(
                            ads[1], get_subid_from_slot_index(log,ads[1],slot))
            time.sleep(WAIT_TIME_BETWEEN_REG_AND_CALL)
            if not call_setup_teardown(log, *param,slot_id_callee = slot,
                                       wait_time_in_call=wait_time_in_call):
                log.error("Call Iteration Failed")
                return False
            if not call_setup_teardown(log, *param,slot_id_callee = 1-slot,
                                       wait_time_in_call=wait_time_in_call):
                log.error("Call Iteration Failed")
                return False
    return True

def two_phone_call_long_seq(log,
                            phone_a,
                            phone_a_idle_func,
                            phone_a_in_call_check_func,
                            phone_b,
                            phone_b_idle_func,
                            phone_b_in_call_check_func,
                            call_sequence_func=None,
                            wait_time_in_call=WAIT_TIME_IN_CALL):
    """Call process long sequence.
    1. Ensure phone idle and in idle_func check return True.
    2. Call from PhoneA to PhoneB, accept on PhoneB.
    3. Check phone state, hangup on PhoneA.
    4. Ensure phone idle and in idle_func check return True.
    5. Call from PhoneA to PhoneB, accept on PhoneB.
    6. Check phone state, hangup on PhoneB.
    7. Ensure phone idle and in idle_func check return True.
    8. Call from PhoneB to PhoneA, accept on PhoneA.
    9. Check phone state, hangup on PhoneA.
    10. Ensure phone idle and in idle_func check return True.
    11. Call from PhoneB to PhoneA, accept on PhoneA.
    12. Check phone state, hangup on PhoneB.

    Args:
        phone_a: PhoneA's android device object.
        phone_a_idle_func: function to check PhoneA's idle state.
        phone_a_in_call_check_func: function to check PhoneA's in-call state.
        phone_b: PhoneB's android device object.
        phone_b_idle_func: function to check PhoneB's idle state.
        phone_b_in_call_check_func: function to check PhoneB's in-call state.
        call_sequence_func: default parameter, not implemented.
        wait_time_in_call: time to wait in call.
            This is optional, default is WAIT_TIME_IN_CALL

    Returns:
        TelResultWrapper which will evaluate as False if error.

    """
    ads = [phone_a, phone_b]

    call_params = [
        (ads[0], ads[1], ads[0], phone_a_in_call_check_func,
         phone_b_in_call_check_func),
        (ads[0], ads[1], ads[1], phone_a_in_call_check_func,
         phone_b_in_call_check_func),
        (ads[1], ads[0], ads[0], phone_b_in_call_check_func,
         phone_a_in_call_check_func),
        (ads[1], ads[0], ads[1], phone_b_in_call_check_func,
         phone_a_in_call_check_func),
    ]

    tel_result = TelResultWrapper(CallResult('SUCCESS'))
    for param in call_params:
        # Make sure phones are idle.
        ensure_phones_idle(log, ads)
        if phone_a_idle_func and not phone_a_idle_func(log, phone_a):
            phone_a.log.error("Phone A Failed to Reselect")
            return TelResultWrapper(CallResult('CALL_SETUP_FAILURE'))
        if phone_b_idle_func and not phone_b_idle_func(log, phone_b):
            phone_b.log.error("Phone B Failed to Reselect")
            return TelResultWrapper(CallResult('CALL_SETUP_FAILURE'))

        # TODO: b/26337871 Need to use proper API to check phone registered.
        time.sleep(WAIT_TIME_BETWEEN_REG_AND_CALL)

        # Make call.
        log.info("---> Call test: %s to %s <---", param[0].serial,
                 param[1].serial)
        tel_result = call_setup_teardown(
                log, *param, wait_time_in_call=wait_time_in_call)
        if not tel_result:
            log.error("Call Iteration Failed")
            break

    return tel_result

def two_phone_call_msim_for_slot(log,
                             phone_a,
                             phone_a_slot,
                             phone_a_idle_func,
                             phone_a_in_call_check_func,
                             phone_b,
                             phone_b_slot,
                             phone_b_idle_func,
                             phone_b_in_call_check_func,
                             call_sequence_func=None,
                             wait_time_in_call=WAIT_TIME_IN_CALL,
                             retry=2):
    """Call process between 2 phones with specific slot.
    1. Ensure phone idle and in idle_func    check return True.
    2. Call from PhoneA to PhoneB, accept on PhoneB.
    3. Check phone state, hangup on PhoneA.
    4. Ensure phone idle and in idle_func check return True.
    5. Call from PhoneA to PhoneB, accept on PhoneB.
    6. Check phone state, hangup on PhoneB.

    Args:
        phone_a: PhoneA's android device object.
        phone_a_slot: 0 or 1 (pSIM or eSIM)
        phone_a_idle_func: function to check PhoneA's idle state.
        phone_a_in_call_check_func: function to check PhoneA's in-call state.
        phone_b: PhoneB's android device object.
        phone_b_slot: 0 or 1 (pSIM or eSIM)
        phone_b_idle_func: function to check PhoneB's idle state.
        phone_b_in_call_check_func: function to check PhoneB's in-call state.
        call_sequence_func: default parameter, not implemented.
        wait_time_in_call: time to wait in call.
            This is optional, default is WAIT_TIME_IN_CALL
        retry: times of retry if call_setup_teardown failed.

    Returns:
        True: if call sequence succeed.
        False: for errors
    """
    ads = [phone_a, phone_b]

    call_params = [
        (ads[0], ads[1], ads[0], phone_a_in_call_check_func,
         phone_b_in_call_check_func),
        (ads[0], ads[1], ads[1], phone_a_in_call_check_func,
         phone_b_in_call_check_func),
    ]

    tel_result = TelResultWrapper(CallResult('SUCCESS'))
    for param in call_params:
        # Make sure phones are idle.
        ensure_phones_idle(log, ads)
        if phone_a_idle_func and not phone_a_idle_func(log, phone_a):
            phone_a.log.error("Phone A Failed to Reselect")
            return TelResultWrapper(CallResult('CALL_SETUP_FAILURE'))
        if phone_b_idle_func and not phone_b_idle_func(log, phone_b):
            phone_b.log.error("Phone B Failed to Reselect")
            return TelResultWrapper(CallResult('CALL_SETUP_FAILURE'))

        # TODO: b/26337871 Need to use proper API to check phone registered.
        time.sleep(WAIT_TIME_BETWEEN_REG_AND_CALL)

        # Make call.
        log.info("--> Call test: %s slot %s to %s slot %s <--", phone_a.serial,
            phone_a_slot, phone_b.serial, phone_b_slot)

        mo_default_voice_subid = get_subid_from_slot_index(log,ads[0],
            phone_a_slot)
        if mo_default_voice_subid == INVALID_SUB_ID:
            log.warning("Sub ID of MO (%s) slot %s is invalid.", phone_a.serial,
                phone_a_slot)
            return TelResultWrapper(CallResult('CALL_SETUP_FAILURE'))
        set_subid_for_outgoing_call(
                            ads[0], mo_default_voice_subid)

        mt_default_voice_subid = get_subid_from_slot_index(log,ads[1],
            phone_b_slot)
        if mt_default_voice_subid == INVALID_SUB_ID:
            log.warning("Sub ID of MT (%s) slot %s is invalid.", phone_b.serial,
                phone_b_slot)
            return TelResultWrapper(CallResult('CALL_SETUP_FAILURE'))

        tel_result = call_setup_teardown(
            log,
            *param,
            slot_id_callee=phone_b_slot,
            wait_time_in_call=wait_time_in_call)

        while not tel_result:
            if retry <= 0:
                log.error("Call Iteration failed.")
                break
            else:
                log.info("RERUN call_setup_teardown.")
                tel_result = call_setup_teardown(
                    log,
                    *param,
                    slot_id_callee=phone_b_slot,
                    wait_time_in_call=wait_time_in_call)

            retry = retry - 1

    return tel_result


def is_phone_in_call(log, ad):
    """Return True if phone in call.

    Args:
        log: log object.
        ad:  android device.
    """
    try:
        return ad.droid.telecomIsInCall()
    except:
        return "mCallState=2" in ad.adb.shell(
            "dumpsys telephony.registry | grep mCallState")


def is_phone_in_call_active(ad, call_id=None):
    """Return True if phone in active call.

    Args:
        log: log object.
        ad:  android device.
        call_id: the call id
    """
    if ad.droid.telecomIsInCall():
        if not call_id:
            call_id = ad.droid.telecomCallGetCallIds()[0]
        call_state = ad.droid.telecomCallGetCallState(call_id)
        ad.log.info("%s state is %s", call_id, call_state)
        return call_state == "ACTIVE"
    else:
        ad.log.info("Not in telecomIsInCall")
        return False


def is_phone_in_call_volte(log, ad):
    """Return if phone is in VoLTE call.

    Args:
        ad: Android device object.
    """
    return is_phone_in_call_volte_for_subscription(
        log, ad, get_outgoing_voice_sub_id(ad))


def is_phone_in_call_volte_for_subscription(log, ad, sub_id):
    """Return if phone is in VoLTE call for subscription id.

    Args:
        ad: Android device object.
        sub_id: subscription id.
    """
    if not ad.droid.telecomIsInCall():
        ad.log.error("Not in call.")
        return False
    nw_type = get_network_rat_for_subscription(log, ad, sub_id,
                                               NETWORK_SERVICE_VOICE)
    if nw_type != RAT_LTE:
        ad.log.error("Voice rat on: %s. Expected: LTE", nw_type)
        return False
    return True


def is_phone_in_call_csfb(log, ad):
    """Return if phone is in CSFB call.

    Args:
        ad: Android device object.
    """
    return is_phone_in_call_csfb_for_subscription(
        log, ad, get_outgoing_voice_sub_id(ad))


def is_phone_in_call_csfb_for_subscription(log, ad, sub_id):
    """Return if phone is in CSFB call for subscription id.

    Args:
        ad: Android device object.
        sub_id: subscription id.
    """
    if not ad.droid.telecomIsInCall():
        ad.log.error("Not in call.")
        return False
    nw_type = get_network_rat_for_subscription(log, ad, sub_id,
                                               NETWORK_SERVICE_VOICE)
    if nw_type == RAT_LTE:
        ad.log.error("Voice rat on: %s. Expected: not LTE", nw_type)
        return False
    return True


def is_phone_in_call_3g(log, ad):
    """Return if phone is in 3G call.

    Args:
        ad: Android device object.
    """
    return is_phone_in_call_3g_for_subscription(log, ad,
                                                get_outgoing_voice_sub_id(ad))


def is_phone_in_call_3g_for_subscription(log, ad, sub_id):
    """Return if phone is in 3G call for subscription id.

    Args:
        ad: Android device object.
        sub_id: subscription id.
    """
    if not ad.droid.telecomIsInCall():
        ad.log.error("Not in call.")
        return False
    nw_gen = get_network_gen_for_subscription(log, ad, sub_id,
                                              NETWORK_SERVICE_VOICE)
    if nw_gen != GEN_3G:
        ad.log.error("Voice rat on: %s. Expected: 3g", nw_gen)
        return False
    return True


def is_phone_in_call_2g(log, ad):
    """Return if phone is in 2G call.

    Args:
        ad: Android device object.
    """
    return is_phone_in_call_2g_for_subscription(log, ad,
                                                get_outgoing_voice_sub_id(ad))


def is_phone_in_call_2g_for_subscription(log, ad, sub_id):
    """Return if phone is in 2G call for subscription id.

    Args:
        ad: Android device object.
        sub_id: subscription id.
    """
    if not ad.droid.telecomIsInCall():
        ad.log.error("Not in call.")
        return False
    nw_gen = get_network_gen_for_subscription(log, ad, sub_id,
                                              NETWORK_SERVICE_VOICE)
    if nw_gen != GEN_2G:
        ad.log.error("Voice rat on: %s. Expected: 2g", nw_gen)
        return False
    return True


def is_phone_in_call_1x(log, ad):
    """Return if phone is in 1x call.

    Args:
        ad: Android device object.
    """
    return is_phone_in_call_1x_for_subscription(log, ad,
                                                get_outgoing_voice_sub_id(ad))


def is_phone_in_call_1x_for_subscription(log, ad, sub_id):
    """Return if phone is in 1x call for subscription id.

    Args:
        ad: Android device object.
        sub_id: subscription id.
    """
    if not ad.droid.telecomIsInCall():
        ad.log.error("Not in call.")
        return False
    nw_type = get_network_rat_for_subscription(log, ad, sub_id,
                                               NETWORK_SERVICE_VOICE)
    if nw_type != RAT_1XRTT:
        ad.log.error("Voice rat on: %s. Expected: 1xrtt", nw_type)
        return False
    return True


def is_phone_in_call_wcdma(log, ad):
    """Return if phone is in WCDMA call.

    Args:
        ad: Android device object.
    """
    return is_phone_in_call_wcdma_for_subscription(
        log, ad, get_outgoing_voice_sub_id(ad))


def is_phone_in_call_wcdma_for_subscription(log, ad, sub_id):
    """Return if phone is in WCDMA call for subscription id.

    Args:
        ad: Android device object.
        sub_id: subscription id.
    """
    # Currently checking 'umts'.
    # Changes may needed in the future.
    if not ad.droid.telecomIsInCall():
        ad.log.error("Not in call.")
        return False
    nw_type = get_network_rat_for_subscription(log, ad, sub_id,
                                               NETWORK_SERVICE_VOICE)
    if nw_type != RAT_UMTS:
        ad.log.error("%s voice rat on: %s. Expected: umts", nw_type)
        return False
    return True


def is_phone_in_call_iwlan(log, ad, call_id=None):
    """Return if phone is in WiFi call.

    Args:
        ad: Android device object.
    """
    if not ad.droid.telecomIsInCall():
        ad.log.error("Not in call.")
        return False
    if not ad.droid.telephonyIsImsRegistered():
        ad.log.info("IMS is not registered.")
        return False
    if not ad.droid.telephonyIsWifiCallingAvailable():
        ad.log.info("IsWifiCallingAvailable is False")
        return False
    if not call_id:
        call_ids = ad.droid.telecomCallGetCallIds()
        if call_ids:
            call_id = call_ids[-1]
    if not call_id:
        ad.log.error("Failed to get call id")
        return False
    else:
        call_prop = ad.droid.telecomCallGetProperties(call_id)
        if "WIFI" not in call_prop:
            ad.log.info("callProperties = %s, expecting WIFI", call_prop)
            return False
    nw_type = get_network_rat(log, ad, NETWORK_SERVICE_DATA)
    if nw_type != RAT_IWLAN:
        ad.log.warning("Data rat on: %s. Expected: iwlan", nw_type)
    return True


def is_phone_in_call_not_iwlan(log, ad):
    """Return if phone is in WiFi call for subscription id.

    Args:
        ad: Android device object.
        sub_id: subscription id.
    """
    if not ad.droid.telecomIsInCall():
        ad.log.error("Not in call.")
        return False
    nw_type = get_network_rat(log, ad, NETWORK_SERVICE_DATA)
    if nw_type == RAT_IWLAN:
        ad.log.error("Data rat on: %s. Expected: not iwlan", nw_type)
        return False
    if is_wfc_enabled(log, ad):
        ad.log.error("WiFi Calling feature bit is True.")
        return False
    return True


def swap_calls(log,
               ads,
               call_hold_id,
               call_active_id,
               num_swaps=1,
               check_call_status=True):
    """PhoneA in call with B and C. Swap active/holding call on PhoneA.

    Swap call and check status on PhoneA.
        (This step may have multiple times according to 'num_swaps'.)
    Check if all 3 phones are 'in-call'.

    Args:
        ads: list of ad object, at least three need to pass in.
            Swap operation will happen on ads[0].
            ads[1] and ads[2] are call participants.
        call_hold_id: id for the holding call in ads[0].
            call_hold_id should be 'STATE_HOLDING' when calling this function.
        call_active_id: id for the active call in ads[0].
            call_active_id should be 'STATE_ACTIVE' when calling this function.
        num_swaps: how many swap/check operations will be done before return.
        check_call_status: This is optional. Default value is True.
            If this value is True, then call status (active/hold) will be
            be checked after each swap operation.

    Returns:
        If no error happened, return True, otherwise, return False.
    """
    if check_call_status:
        # Check status before swap.
        if ads[0].droid.telecomCallGetCallState(
                call_active_id) != CALL_STATE_ACTIVE:
            ads[0].log.error(
                "Call_id:%s, state:%s, expected: STATE_ACTIVE", call_active_id,
                ads[0].droid.telecomCallGetCallState(call_active_id))
            return False
        if ads[0].droid.telecomCallGetCallState(
                call_hold_id) != CALL_STATE_HOLDING:
            ads[0].log.error(
                "Call_id:%s, state:%s, expected: STATE_HOLDING", call_hold_id,
                ads[0].droid.telecomCallGetCallState(call_hold_id))
            return False

    i = 1
    while (i <= num_swaps):
        ads[0].log.info("swap_test %s: swap and check call status.", i)
        ads[0].droid.telecomCallHold(call_active_id)
        time.sleep(WAIT_TIME_IN_CALL)
        # Swap object reference
        call_active_id, call_hold_id = call_hold_id, call_active_id
        if check_call_status:
            # Check status
            if ads[0].droid.telecomCallGetCallState(
                    call_active_id) != CALL_STATE_ACTIVE:
                ads[0].log.error(
                    "Call_id:%s, state:%s, expected: STATE_ACTIVE",
                    call_active_id,
                    ads[0].droid.telecomCallGetCallState(call_active_id))
                return False
            if ads[0].droid.telecomCallGetCallState(
                    call_hold_id) != CALL_STATE_HOLDING:
                ads[0].log.error(
                    "Call_id:%s, state:%s, expected: STATE_HOLDING",
                    call_hold_id,
                    ads[0].droid.telecomCallGetCallState(call_hold_id))
                return False
        # TODO: b/26296375 add voice check.

        i += 1

    #In the end, check all three phones are 'in-call'.
    if not verify_incall_state(log, [ads[0], ads[1], ads[2]], True):
        return False

    return True


def get_audio_route(log, ad):
    """Gets the audio route for the active call

    Args:
        log: logger object
        ad: android_device object

    Returns:
        Audio route string ["BLUETOOTH", "EARPIECE", "SPEAKER", "WIRED_HEADSET"
            "WIRED_OR_EARPIECE"]
    """

    audio_state = ad.droid.telecomCallGetAudioState()
    return audio_state["AudioRoute"]


def set_audio_route(log, ad, route):
    """Sets the audio route for the active call

    Args:
        log: logger object
        ad: android_device object
        route: string ["BLUETOOTH", "EARPIECE", "SPEAKER", "WIRED_HEADSET"
            "WIRED_OR_EARPIECE"]

    Returns:
        If no error happened, return True, otherwise, return False.
    """
    ad.droid.telecomCallSetAudioRoute(route)
    return True


def is_property_in_call_properties(log, ad, call_id, expected_property):
    """Return if the call_id has the expected property

    Args:
        log: logger object
        ad: android_device object
        call_id: call id.
        expected_property: expected property.

    Returns:
        True if call_id has expected_property. False if not.
    """
    properties = ad.droid.telecomCallGetProperties(call_id)
    return (expected_property in properties)


def is_call_hd(log, ad, call_id):
    """Return if the call_id is HD call.

    Args:
        log: logger object
        ad: android_device object
        call_id: call id.

    Returns:
        True if call_id is HD call. False if not.
    """
    return is_property_in_call_properties(log, ad, call_id,
                                          CALL_PROPERTY_HIGH_DEF_AUDIO)


def get_cep_conference_call_id(ad):
    """Get CEP conference call id if there is an ongoing CEP conference call.

    Args:
        ad: android device object.

    Returns:
        call id for CEP conference call if there is an ongoing CEP conference call.
        None otherwise.
    """
    for call in ad.droid.telecomCallGetCallIds():
        if len(ad.droid.telecomCallGetCallChildren(call)) != 0:
            return call
    return None


def is_phone_in_call_on_rat(log, ad, rat='volte', only_return_fn=None):
    if rat.lower() == 'volte' or rat.lower() == '5g_volte':
        if only_return_fn:
            return is_phone_in_call_volte
        else:
            return is_phone_in_call_volte(log, ad)

    elif rat.lower() == 'csfb' or rat.lower() == '5g_csfb':
        if only_return_fn:
            return is_phone_in_call_csfb
        else:
            return is_phone_in_call_csfb(log, ad)

    elif rat.lower() == '3g':
        if only_return_fn:
            return is_phone_in_call_3g
        else:
            return is_phone_in_call_3g(log, ad)

    elif rat.lower() == '2g':
        if only_return_fn:
            return is_phone_in_call_2g
        else:
            return is_phone_in_call_2g(log, ad)

    elif rat.lower() == 'wfc' or rat.lower() == '5g_wfc':
        if only_return_fn:
            return is_phone_in_call_iwlan
        else:
            return is_phone_in_call_iwlan(log, ad)
    else:
        return None


def hold_unhold_test(log, ads):
    """ Test hold/unhold functionality.

    PhoneA is in call with PhoneB. The call on PhoneA is active.
    Get call list on PhoneA.
    Hold call_id on PhoneA.
    Check call_id state.
    Unhold call_id on PhoneA.
    Check call_id state.

    Args:
        log: log object
        ads: List of android objects.
            This list should contain 2 android objects.
            ads[0] is the ad to do hold/unhold operation.

    Returns:
        List of test result and call states.
        The first element of the list is always the test result.
        True if pass; False if fail.
        The rest of the list contains call states.
    """
    call_list = ads[0].droid.telecomCallGetCallIds()
    log.info("Calls in PhoneA %s", call_list)
    if num_active_calls(ads[0].log, ads[0]) != 1:
        log.error("No voice call or too many voice calls in PhoneA!")
        call_state_list = [ads[0].droid.telecomCallGetCallState(call_id) for call_id in call_list]
        return [False] + call_state_list
    call_id = call_list[0]

    call_state = ads[0].droid.telecomCallGetCallState(call_id)
    if call_state != CALL_STATE_ACTIVE:
        log.error("Call_id:%s, state:%s, expected: STATE_ACTIVE",
                  call_id,
                  ads[0].droid.telecomCallGetCallState(call_id))
        return [False, call_state]
    # TODO: b/26296375 add voice check.

    log.info("Hold call_id %s on PhoneA", call_id)
    ads[0].droid.telecomCallHold(call_id)
    time.sleep(WAIT_TIME_IN_CALL)

    call_state = ads[0].droid.telecomCallGetCallState(call_id)
    if call_state != CALL_STATE_HOLDING:
        ads[0].log.error("Call_id:%s, state:%s, expected: STATE_HOLDING",
                         call_id,
                         ads[0].droid.telecomCallGetCallState(call_id))
        return [False, call_state]
    # TODO: b/26296375 add voice check.

    log.info("Unhold call_id %s on PhoneA", call_id)
    ads[0].droid.telecomCallUnhold(call_id)
    time.sleep(WAIT_TIME_IN_CALL)

    call_state = ads[0].droid.telecomCallGetCallState(call_id)
    if call_state != CALL_STATE_ACTIVE:
        log.error("Call_id:%s, state:%s, expected: STATE_ACTIVE",
                  call_id,
                  call_state)
        return [False, call_state]
    # TODO: b/26296375 add voice check.

    return [True, call_state]


def phone_setup_call_hold_unhold_test(log,
                                      ads,
                                      call_direction=DIRECTION_MOBILE_ORIGINATED,
                                      caller_func=None,
                                      callee_func=None):
    """Test hold and unhold in voice call.

    1. Clear call list.
    2. Set up MO/MT call.
    3. Test hold and unhold in call.
    4. hangup call.

    Args:
        log: log object
        ads: list of android objects, this list should have two ad.
        call_direction: MO(DIRECTION_MOBILE_ORIGINATED) or MT(DIRECTION_MOBILE_TERMINATED) call.
        caller_func: function to verify caller is in correct state while in-call.
        callee_func: function to verify callee is in correct state while in-call.

    Returns:
        True if pass; False if fail.
    """

    ads[0].droid.telecomCallClearCallList()
    if num_active_calls(log, ads[0]) != 0:
        ads[0].log.error("call list is not empty")
        return False
    log.info("begin hold/unhold test")

    ad_caller = ads[0]
    ad_callee = ads[1]

    if call_direction != DIRECTION_MOBILE_ORIGINATED:
        ad_caller = ads[1]
        ad_callee = ads[0]

    if not call_setup_teardown(
                log,
                ad_caller,
                ad_callee,
                ad_hangup=None,
                verify_caller_func=caller_func,
                verify_callee_func=callee_func):
        return False

    if not hold_unhold_test(ads[0].log, ads)[0]:
        log.error("hold/unhold test fail.")
        # hangup call in case voice call is still active.
        hangup_call(log, ads[0])
        return False

    if not hangup_call(log, ads[0]):
        log.error("call hangup failed")
        return False
    return True


def _test_call_long_duration(log, ads, dut_incall_check_func, total_duration):

    log.info("Long Duration Call Test. Total duration = %s",
                  total_duration)
    return call_setup_teardown(
        log,
        ads[0],
        ads[1],
        ads[0],
        verify_caller_func=dut_incall_check_func,
        wait_time_in_call=total_duration)


def _wait_for_ringing_event(log, ad, wait_time):
    """Wait for ringing event.

    Args:
        log: log object.
        ad: android device object.
        wait_time: max time to wait for ringing event.

    Returns:
        event_ringing if received ringing event.
        otherwise return None.
    """
    event_ringing = None

    try:
        event_ringing = ad.ed.wait_for_event(
            EventCallStateChanged,
            is_event_match,
            timeout=wait_time,
            field=CallStateContainer.CALL_STATE,
            value=TELEPHONY_STATE_RINGING)
        ad.log.info("Receive ringing event")
    except Empty:
        ad.log.info("No Ringing Event")
    finally:
        return event_ringing


def wait_for_telecom_ringing(log, ad, max_time=MAX_WAIT_TIME_TELECOM_RINGING):
    """Wait for android to be in telecom ringing state.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time. This is optional.
            Default Value is MAX_WAIT_TIME_TELECOM_RINGING.

    Returns:
        If phone become in telecom ringing state within max_time, return True.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(
        log, ad, max_time, lambda log, ad: ad.droid.telecomIsRinging())


def wait_for_ringing_call(log, ad, incoming_number=None):
    """Wait for an incoming call on default voice subscription and
       accepts the call.

    Args:
        log: log object.
        ad: android device object.
        incoming_number: Expected incoming number.
            Optional. Default is None

    Returns:
        True: if incoming call is received and answered successfully.
        False: for errors
        """
    return wait_for_ringing_call_for_subscription(
        log, ad, get_incoming_voice_sub_id(ad), incoming_number)


def wait_for_ringing_call_for_subscription(
        log,
        ad,
        sub_id,
        incoming_number=None,
        caller=None,
        event_tracking_started=False,
        timeout=MAX_WAIT_TIME_CALLEE_RINGING,
        interval=WAIT_TIME_BETWEEN_STATE_CHECK):
    """Wait for an incoming call on specified subscription.

    Args:
        log: log object.
        ad: android device object.
        sub_id: subscription ID
        incoming_number: Expected incoming number. Default is None
        event_tracking_started: True if event tracking already state outside
        timeout: time to wait for ring
        interval: checking interval

    Returns:
        True: if incoming call is received and answered successfully.
        False: for errors
    """
    if not event_tracking_started:
        ad.ed.clear_events(EventCallStateChanged)
        ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)
    ring_event_received = False
    end_time = time.time() + timeout
    try:
        while time.time() < end_time:
            if not ring_event_received:
                event_ringing = _wait_for_ringing_event(log, ad, interval)
                if event_ringing:
                    if incoming_number and not check_phone_number_match(
                            event_ringing['data']
                        [CallStateContainer.INCOMING_NUMBER], incoming_number):
                        ad.log.error(
                            "Incoming Number not match. Expected number:%s, actual number:%s",
                            incoming_number, event_ringing['data'][
                                CallStateContainer.INCOMING_NUMBER])
                        return False
                    ring_event_received = True
            telephony_state = ad.droid.telephonyGetCallStateForSubscription(
                sub_id)
            telecom_state = ad.droid.telecomGetCallState()
            if telephony_state == TELEPHONY_STATE_RINGING and (
                    telecom_state == TELEPHONY_STATE_RINGING):
                ad.log.info("callee is in telephony and telecom RINGING state")
                if caller:
                    if caller.droid.telecomIsInCall():
                        caller.log.info("Caller telecom is in call state")
                        return True
                    else:
                        caller.log.info("Caller telecom is NOT in call state")
                else:
                    return True
            else:
                ad.log.info(
                    "telephony in %s, telecom in %s, expecting RINGING state",
                    telephony_state, telecom_state)
            time.sleep(interval)
    finally:
        if not event_tracking_started:
            ad.droid.telephonyStopTrackingCallStateChangeForSubscription(
                sub_id)


def wait_for_call_offhook_for_subscription(
        log,
        ad,
        sub_id,
        event_tracking_started=False,
        timeout=MAX_WAIT_TIME_ACCEPT_CALL_TO_OFFHOOK_EVENT,
        interval=WAIT_TIME_BETWEEN_STATE_CHECK):
    """Wait for an incoming call on specified subscription.

    Args:
        log: log object.
        ad: android device object.
        sub_id: subscription ID
        timeout: time to wait for ring
        interval: checking interval

    Returns:
        True: if incoming call is received and answered successfully.
        False: for errors
    """
    if not event_tracking_started:
        ad.ed.clear_events(EventCallStateChanged)
        ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)
    offhook_event_received = False
    end_time = time.time() + timeout
    try:
        while time.time() < end_time:
            if not offhook_event_received:
                if wait_for_call_offhook_event(log, ad, sub_id, True,
                                               interval):
                    offhook_event_received = True
            telephony_state = ad.droid.telephonyGetCallStateForSubscription(
                sub_id)
            telecom_state = ad.droid.telecomGetCallState()
            if telephony_state == TELEPHONY_STATE_OFFHOOK and (
                    telecom_state == TELEPHONY_STATE_OFFHOOK):
                ad.log.info("telephony and telecom are in OFFHOOK state")
                return True
            else:
                ad.log.info(
                    "telephony in %s, telecom in %s, expecting OFFHOOK state",
                    telephony_state, telecom_state)
            if offhook_event_received:
                time.sleep(interval)
    finally:
        if not event_tracking_started:
            ad.droid.telephonyStopTrackingCallStateChangeForSubscription(
                sub_id)


def wait_for_call_offhook_event(
        log,
        ad,
        sub_id,
        event_tracking_started=False,
        timeout=MAX_WAIT_TIME_ACCEPT_CALL_TO_OFFHOOK_EVENT):
    """Wait for an incoming call on specified subscription.

    Args:
        log: log object.
        ad: android device object.
        event_tracking_started: True if event tracking already state outside
        timeout: time to wait for event

    Returns:
        True: if call offhook event is received.
        False: if call offhook event is not received.
    """
    if not event_tracking_started:
        ad.ed.clear_events(EventCallStateChanged)
        ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)
    try:
        ad.ed.wait_for_event(
            EventCallStateChanged,
            is_event_match,
            timeout=timeout,
            field=CallStateContainer.CALL_STATE,
            value=TELEPHONY_STATE_OFFHOOK)
        ad.log.info("Got event %s", TELEPHONY_STATE_OFFHOOK)
    except Empty:
        ad.log.info("No event for call state change to OFFHOOK")
        return False
    finally:
        if not event_tracking_started:
            ad.droid.telephonyStopTrackingCallStateChangeForSubscription(
                sub_id)
    return True


def wait_and_answer_call_for_subscription(
        log,
        ad,
        sub_id,
        incoming_number=None,
        incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND,
        timeout=MAX_WAIT_TIME_CALLEE_RINGING,
        caller=None,
        video_state=None):
    """Wait for an incoming call on specified subscription and
       accepts the call.

    Args:
        log: log object.
        ad: android device object.
        sub_id: subscription ID
        incoming_number: Expected incoming number.
            Optional. Default is None
        incall_ui_display: after answer the call, bring in-call UI to foreground or
            background. Optional, default value is INCALL_UI_DISPLAY_FOREGROUND.
            if = INCALL_UI_DISPLAY_FOREGROUND, bring in-call UI to foreground.
            if = INCALL_UI_DISPLAY_BACKGROUND, bring in-call UI to background.
            else, do nothing.

    Returns:
        True: if incoming call is received and answered successfully.
        False: for errors
    """
    ad.ed.clear_events(EventCallStateChanged)
    ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)
    try:
        if not wait_for_ringing_call_for_subscription(
                log,
                ad,
                sub_id,
                incoming_number=incoming_number,
                caller=caller,
                event_tracking_started=True,
                timeout=timeout):
            ad.log.info("Incoming call ringing check failed.")
            return False
        ad.log.info("Accept the ring call")
        ad.droid.telecomAcceptRingingCall(video_state)

        if wait_for_call_offhook_for_subscription(
                log, ad, sub_id, event_tracking_started=True):
            return True
        else:
            ad.log.error("Could not answer the call.")
            return False
    except Exception as e:
        log.error(e)
        return False
    finally:
        ad.droid.telephonyStopTrackingCallStateChangeForSubscription(sub_id)
        if incall_ui_display == INCALL_UI_DISPLAY_FOREGROUND:
            ad.droid.telecomShowInCallScreen()
        elif incall_ui_display == INCALL_UI_DISPLAY_BACKGROUND:
            ad.droid.showHomeScreen()


def wait_and_reject_call(log,
                         ad,
                         incoming_number=None,
                         delay_reject=WAIT_TIME_REJECT_CALL,
                         reject=True):
    """Wait for an incoming call on default voice subscription and
       reject the call.

    Args:
        log: log object.
        ad: android device object.
        incoming_number: Expected incoming number.
            Optional. Default is None
        delay_reject: time to wait before rejecting the call
            Optional. Default is WAIT_TIME_REJECT_CALL

    Returns:
        True: if incoming call is received and reject successfully.
        False: for errors
    """
    return wait_and_reject_call_for_subscription(log, ad,
                                                 get_incoming_voice_sub_id(ad),
                                                 incoming_number, delay_reject,
                                                 reject)


def wait_and_reject_call_for_subscription(log,
                                          ad,
                                          sub_id,
                                          incoming_number=None,
                                          delay_reject=WAIT_TIME_REJECT_CALL,
                                          reject=True):
    """Wait for an incoming call on specific subscription and
       reject the call.

    Args:
        log: log object.
        ad: android device object.
        sub_id: subscription ID
        incoming_number: Expected incoming number.
            Optional. Default is None
        delay_reject: time to wait before rejecting the call
            Optional. Default is WAIT_TIME_REJECT_CALL

    Returns:
        True: if incoming call is received and reject successfully.
        False: for errors
    """

    if not wait_for_ringing_call_for_subscription(log, ad, sub_id,
                                                  incoming_number):
        ad.log.error(
            "Could not reject a call: incoming call in ringing check failed.")
        return False

    ad.ed.clear_events(EventCallStateChanged)
    ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)
    if reject is True:
        # Delay between ringing and reject.
        time.sleep(delay_reject)
        is_find = False
        # Loop the call list and find the matched one to disconnect.
        for call in ad.droid.telecomCallGetCallIds():
            if check_phone_number_match(
                    get_number_from_tel_uri(get_call_uri(ad, call)),
                    incoming_number):
                ad.droid.telecomCallDisconnect(call)
                ad.log.info("Callee reject the call")
                is_find = True
        if is_find is False:
            ad.log.error("Callee did not find matching call to reject.")
            return False
    else:
        # don't reject on callee. Just ignore the incoming call.
        ad.log.info("Callee received incoming call. Ignore it.")
    try:
        ad.ed.wait_for_event(
            EventCallStateChanged,
            is_event_match_for_list,
            timeout=MAX_WAIT_TIME_CALL_IDLE_EVENT,
            field=CallStateContainer.CALL_STATE,
            value_list=[TELEPHONY_STATE_IDLE, TELEPHONY_STATE_OFFHOOK])
    except Empty:
        ad.log.error("No onCallStateChangedIdle event received.")
        return False
    finally:
        ad.droid.telephonyStopTrackingCallStateChangeForSubscription(sub_id)
    return True


def wait_and_answer_call(log,
                         ad,
                         incoming_number=None,
                         incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND,
                         caller=None,
                         video_state=None):
    """Wait for an incoming call on default voice subscription and
       accepts the call.

    Args:
        ad: android device object.
        incoming_number: Expected incoming number.
            Optional. Default is None
        incall_ui_display: after answer the call, bring in-call UI to foreground or
            background. Optional, default value is INCALL_UI_DISPLAY_FOREGROUND.
            if = INCALL_UI_DISPLAY_FOREGROUND, bring in-call UI to foreground.
            if = INCALL_UI_DISPLAY_BACKGROUND, bring in-call UI to background.
            else, do nothing.

    Returns:
        True: if incoming call is received and answered successfully.
        False: for errors
        """
    return wait_and_answer_call_for_subscription(
        log,
        ad,
        get_incoming_voice_sub_id(ad),
        incoming_number,
        incall_ui_display=incall_ui_display,
        caller=caller,
        video_state=video_state)


def wait_for_in_call_active(ad,
                            timeout=MAX_WAIT_TIME_ACCEPT_CALL_TO_OFFHOOK_EVENT,
                            interval=WAIT_TIME_BETWEEN_STATE_CHECK,
                            call_id=None):
    """Wait for call reach active state.

    Args:
        log: log object.
        ad:  android device.
        call_id: the call id
    """
    if not call_id:
        call_id = ad.droid.telecomCallGetCallIds()[0]
    args = [ad, call_id]
    if not wait_for_state(is_phone_in_call_active, True, timeout, interval,
                          *args):
        ad.log.error("Call did not reach ACTIVE state")
        return False
    else:
        return True


def wait_for_droid_in_call(log, ad, max_time):
    """Wait for android to be in call state.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        If phone become in call state within max_time, return True.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, is_phone_in_call)


def wait_for_call_id_clearing(ad,
                              previous_ids,
                              timeout=MAX_WAIT_TIME_CALL_DROP):
    while timeout > 0:
        new_call_ids = ad.droid.telecomCallGetCallIds()
        if len(new_call_ids) <= len(previous_ids):
            return True
        time.sleep(5)
        timeout = timeout - 5
    ad.log.error("Call id clearing failed. Before: %s; After: %s",
                 previous_ids, new_call_ids)
    return False


def wait_for_call_end(
        log,
        ad_caller,
        ad_callee,
        ad_hangup,
        verify_caller_func,
        verify_callee_func,
        call_begin_time,
        check_interval=5,
        tel_result_wrapper=TelResultWrapper(CallResult('SUCCESS')),
        wait_time_in_call=WAIT_TIME_IN_CALL):
    elapsed_time = 0
    while (elapsed_time < wait_time_in_call):
        check_interval = min(check_interval, wait_time_in_call - elapsed_time)
        time.sleep(check_interval)
        elapsed_time += check_interval
        time_message = "at <%s>/<%s> second." % (elapsed_time, wait_time_in_call)
        for ad, call_func in [(ad_caller, verify_caller_func),
                              (ad_callee, verify_callee_func)]:
            if not call_func(log, ad):
                ad.log.error(
                    "NOT in correct %s state at %s, voice in RAT %s",
                    call_func.__name__,
                    time_message,
                    ad.droid.telephonyGetCurrentVoiceNetworkType())
                tel_result_wrapper.result_value = CallResult(
                    'CALL_DROP_OR_WRONG_STATE_AFTER_CONNECTED')
            else:
                ad.log.info("In correct %s state at %s",
                    call_func.__name__, time_message)
            if not ad.droid.telecomCallGetAudioState():
                ad.log.error("Audio is not in call state at %s", time_message)
                tel_result_wrapper.result_value = CallResult(
                        'AUDIO_STATE_NOT_INCALL_AFTER_CONNECTED')

        if not tel_result_wrapper:
            break

    if not tel_result_wrapper:
        for ad in (ad_caller, ad_callee):
            last_call_drop_reason(ad, call_begin_time)
            try:
                if ad.droid.telecomIsInCall():
                    ad.log.info("In call. End now.")
                    ad.droid.telecomEndCall()
            except Exception as e:
                log.error(str(e))
    else:
        if ad_hangup:
            if not hangup_call(log, ad_hangup):
                ad_hangup.log.info("Failed to hang up the call")
                tel_result_wrapper.result_value = CallResult('CALL_HANGUP_FAIL')

    if ad_hangup or not tel_result_wrapper:
        for ad in (ad_caller, ad_callee):
            if not wait_for_call_id_clearing(ad, getattr(ad, "caller_ids", [])):
                tel_result_wrapper.result_value = CallResult(
                    'CALL_ID_CLEANUP_FAIL')

    return tel_result_wrapper


def check_call(log, dut, dut_client):
    result = True
    if not call_setup_teardown(log, dut_client, dut,
                               dut):
        if not call_setup_teardown(log, dut_client,
                                   dut, dut):
            dut.log.error("MT call failed")
            result = False
    if not call_setup_teardown(log, dut, dut_client,
                               dut):
        dut.log.error("MO call failed")
        result = False
    return result


def check_call_in_wfc(log, dut, dut_client):
    result = True
    if not call_setup_teardown(log, dut_client, dut,
                               dut, None, is_phone_in_call_iwlan):
        if not call_setup_teardown(log, dut_client,
                                   dut, dut, None,
                                   is_phone_in_call_iwlan):
            dut.log.error("MT WFC call failed")
            result = False
    if not call_setup_teardown(log, dut, dut_client,
                               dut, is_phone_in_call_iwlan):
        dut.log.error("MO WFC call failed")
        result = False
    return result


def check_call_in_volte(log, dut, dut_client):
    result = True
    if not call_setup_teardown(log, dut_client, dut,
                               dut, None, is_phone_in_call_volte):
        if not call_setup_teardown(log, dut_client,
                                   dut, dut, None,
                                   is_phone_in_call_volte):
            dut.log.error("MT VoLTE call failed")
            result = False
    if not call_setup_teardown(log, dut, dut_client,
                               dut, is_phone_in_call_volte):
        dut.log.error("MO VoLTE call failed")
        result = False
    return result


def change_ims_setting(log,
                       ad,
                       dut_client,
                       wifi_network_ssid,
                       wifi_network_pass,
                       subid,
                       dut_capabilities,
                       airplane_mode,
                       wifi_enabled,
                       volte_enabled,
                       wfc_enabled,
                       nw_gen=RAT_LTE,
                       wfc_mode=None):
    result = True
    ad.log.info(
        "Setting APM %s, WIFI %s, VoLTE %s, WFC %s, WFC mode %s",
        airplane_mode, wifi_enabled, volte_enabled, wfc_enabled, wfc_mode)

    toggle_airplane_mode_by_adb(log, ad, airplane_mode)
    if wifi_enabled:
        if not ensure_wifi_connected(log, ad,
                                     wifi_network_ssid,
                                     wifi_network_pass,
                                     apm=airplane_mode):
            ad.log.error("Fail to connected to WiFi")
            result = False
    else:
        if not wifi_toggle_state(log, ad, False):
            ad.log.error("Failed to turn off WiFi.")
            result = False
    toggle_volte(log, ad, volte_enabled)
    toggle_wfc(log, ad, wfc_enabled)
    if wfc_mode:
        set_wfc_mode(log, ad, wfc_mode)
    wfc_mode = ad.droid.imsGetWfcMode()
    if wifi_enabled or not airplane_mode:
        if not ensure_phone_subscription(log, ad):
            ad.log.error("Failed to find valid subscription")
            result = False
    if airplane_mode:
        if (CAPABILITY_WFC in dut_capabilities) and (wifi_enabled
                                                          and wfc_enabled):
            if not wait_for_wfc_enabled(log, ad):
                result = False
            elif not check_call_in_wfc(log, ad, dut_client):
                result = False
        else:
            if not wait_for_state(
                    ad.droid.telephonyGetCurrentVoiceNetworkType,
                    RAT_UNKNOWN):
                ad.log.error(
                    "Voice RAT is %s not UNKNOWN",
                    ad.droid.telephonyGetCurrentVoiceNetworkType())
                result = False
            else:
                ad.log.info("Voice RAT is in UNKKNOWN")
    else:
        if (wifi_enabled and wfc_enabled) and (
                wfc_mode == WFC_MODE_WIFI_PREFERRED) and (
                    CAPABILITY_WFC in dut_capabilities):
            if not wait_for_wfc_enabled(log, ad):
                result = False
            if not wait_for_state(
                    ad.droid.telephonyGetCurrentVoiceNetworkType,
                    RAT_UNKNOWN):
                ad.log.error(
                    "Voice RAT is %s, not UNKNOWN",
                    ad.droid.telephonyGetCurrentVoiceNetworkType())
            if not check_call_in_wfc(log, ad, dut_client):
                result = False
        else:
            if not wait_for_wfc_disabled(log, ad):
               ad.log.error("WFC is not disabled")
               result = False
            if volte_enabled and CAPABILITY_VOLTE in dut_capabilities:
               if not wait_for_volte_enabled(log, ad):
                    result = False
               if not check_call_in_volte(log, ad, dut_client):
                    result = False
            else:
                if not wait_for_not_network_rat(
                        log,
                        ad,
                        nw_gen,
                        voice_or_data=NETWORK_SERVICE_VOICE):
                    ad.log.error(
                        "Voice RAT is %s",
                        ad.droid.telephonyGetCurrentVoiceNetworkType(
                        ))
                    result = False
                if not wait_for_voice_attach(log, ad):
                    result = False
                if not check_call(log, ad, dut_client):
                    result = False
    user_config_profile = get_user_config_profile(ad)
    ad.log.info("user_config_profile: %s ",
                      sorted(user_config_profile.items()))
    return result


def verify_default_ims_setting(log,
                       ad,
                       dut_client,
                       carrier_configs,
                       default_wfc_enabled,
                       default_volte,
                       wfc_mode=None):
    result = True
    airplane_mode = ad.droid.connectivityCheckAirplaneMode()
    default_wfc_mode = carrier_configs.get(
        CarrierConfigs.DEFAULT_WFC_IMS_MODE_INT, wfc_mode)
    if default_wfc_enabled:
        wait_for_wfc_enabled(log, ad)
    else:
        wait_for_wfc_disabled(log, ad)
        if airplane_mode:
            wait_for_network_rat(
                log,
                ad,
                RAT_UNKNOWN,
                voice_or_data=NETWORK_SERVICE_VOICE)
        else:
            if default_volte:
                wait_for_volte_enabled(log, ad)
            else:
                wait_for_not_network_rat(
                    log,
                    ad,
                    RAT_UNKNOWN,
                    voice_or_data=NETWORK_SERVICE_VOICE)

    if not ensure_phone_subscription(log, ad):
        ad.log.error("Failed to find valid subscription")
        result = False
    user_config_profile = get_user_config_profile(ad)
    ad.log.info("user_config_profile = %s ",
                      sorted(user_config_profile.items()))
    if user_config_profile["VoLTE Enabled"] != default_volte:
        ad.log.error("VoLTE mode is not %s", default_volte)
        result = False
    else:
        ad.log.info("VoLTE mode is %s as expected",
                          default_volte)
    if user_config_profile["WFC Enabled"] != default_wfc_enabled:
        ad.log.error("WFC enabled is not %s", default_wfc_enabled)
    if user_config_profile["WFC Enabled"]:
        if user_config_profile["WFC Mode"] != default_wfc_mode:
            ad.log.error(
                "WFC mode is not %s after IMS factory reset",
                default_wfc_mode)
            result = False
        else:
            ad.log.info("WFC mode is %s as expected",
                              default_wfc_mode)
    if default_wfc_enabled and \
        default_wfc_mode == WFC_MODE_WIFI_PREFERRED:
        if not check_call_in_wfc(log, ad, dut_client):
            result = False
    elif not airplane_mode:
        if default_volte:
            if not check_call_in_volte(log, ad, dut_client):
                result = False
        else:
            if not check_call(log, ad, dut_client):
                result = False
    if result == False:
        user_config_profile = get_user_config_profile(ad)
        ad.log.info("user_config_profile = %s ",
                          sorted(user_config_profile.items()))
    return result


def truncate_phone_number(
    log,
    caller_number,
    callee_number,
    dialing_number_length,
    skip_inter_area_call=False):
    """This function truncates the phone number of the caller/callee to test
    7/10/11/12 digit dialing for North American numbering plan, and distinguish
    if this is an inter-area call by comparing the area code.

    Args:
        log: logger object
        caller_number: phone number of the caller
        callee_number: phone number of the callee
        dialing_number_length: the length of phone number (usually 7/10/11/12)
        skip_inter_area_call: True to raise a TestSkip exception to skip dialing
            the inter-area call. Otherwise False.

    Returns:
        The truncated phone number of the callee
    """

    if not dialing_number_length:
        return callee_number

    trunc_position = 0 - int(dialing_number_length)
    try:
        caller_area_code = caller_number[:trunc_position]
        callee_area_code = callee_number[:trunc_position]
        callee_dial_number = callee_number[trunc_position:]

        if caller_area_code != callee_area_code:
            skip_inter_area_call = True

    except:
        skip_inter_area_call = True

    if skip_inter_area_call:
        msg = "Cannot make call from %s to %s by %s digits since inter-area \
        call is not allowed" % (
            caller_number, callee_number, dialing_number_length)
        log.info(msg)
        raise signals.TestSkip(msg)
    else:
        callee_number = callee_dial_number

    return callee_number

