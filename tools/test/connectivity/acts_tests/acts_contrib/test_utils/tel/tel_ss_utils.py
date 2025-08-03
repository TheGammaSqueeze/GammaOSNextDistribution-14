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

from acts import signals
import re
import time

from acts.utils import get_current_epoch_time
from acts_contrib.test_utils.tel.tel_defines import INCALL_UI_DISPLAY_FOREGROUND
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_WFC_ENABLED
from acts_contrib.test_utils.tel.tel_defines import NOT_CHECK_MCALLFORWARDING_OPERATOR_LIST
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_BETWEEN_REG_AND_CALL
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_IN_CALL
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_wfc_enabled
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_subscription_utils import get_incoming_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_slot_index_from_subid
from acts_contrib.test_utils.tel.tel_test_utils import _phone_number_remove_prefix
from acts_contrib.test_utils.tel.tel_test_utils import check_call_state_ring_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import check_call_state_idle_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import get_operator_name
from acts_contrib.test_utils.tel.tel_test_utils import get_user_config_profile
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode_msim
from acts_contrib.test_utils.tel.tel_voice_utils import call_setup_teardown_for_subscription
from acts_contrib.test_utils.tel.tel_voice_utils import dial_phone_number
from acts_contrib.test_utils.tel.tel_voice_utils import disconnect_call_by_id
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.tel.tel_voice_utils import initiate_call
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call
from acts_contrib.test_utils.tel.tel_voice_utils import last_call_drop_reason
from acts_contrib.test_utils.tel.tel_voice_utils import wait_and_answer_call_for_subscription
from acts_contrib.test_utils.tel.tel_voice_utils import wait_for_call_id_clearing
from acts_contrib.test_utils.tel.tel_voice_utils import wait_for_call_offhook_for_subscription
from acts_contrib.test_utils.tel.tel_voice_utils import wait_for_in_call_active
from acts_contrib.test_utils.tel.tel_voice_utils import wait_for_ringing_call_for_subscription


def call_setup_teardown_for_call_forwarding(
    log,
    ad_caller,
    ad_callee,
    forwarded_callee,
    ad_hangup=None,
    verify_callee_func=None,
    verify_after_cf_disabled=None,
    wait_time_in_call=WAIT_TIME_IN_CALL,
    incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND,
    dialing_number_length=None,
    video_state=None,
    call_forwarding_type="unconditional"):
    """ Call process for call forwarding, including make a phone call from
    caller, forward from callee, accept from the forwarded callee and hang up.
    The call is on default voice subscription

    In call process, call from <ad_caller> to <ad_callee>, forwarded to
    <forwarded_callee>, accept the call, (optional) and then hang up from
    <ad_hangup>.

    Args:
        ad_caller: Caller Android Device Object.
        ad_callee: Callee Android Device Object which forwards the call.
        forwarded_callee: Callee Android Device Object which answers the call.
        ad_hangup: Android Device Object end the phone call.
            Optional. Default value is None, and phone call will continue.
        verify_callee_func: func_ptr to verify callee in correct mode
            Optional. Default is None
        verify_after_cf_disabled: If True the test of disabling call forwarding
        will be appended.
        wait_time_in_call: the call duration of a connected call
        incall_ui_display: after answer the call, bring in-call UI to foreground
        or background.
            Optional, default value is INCALL_UI_DISPLAY_FOREGROUND.
            if = INCALL_UI_DISPLAY_FOREGROUND, bring in-call UI to foreground.
            if = INCALL_UI_DISPLAY_BACKGROUND, bring in-call UI to background.
            else, do nothing.
        dialing_number_length: the number of digits used for dialing
        video_state: video call or voice call. Default is voice call.
        call_forwarding_type: type of call forwarding listed below:
            - unconditional
            - busy
            - not_answered
            - not_reachable

    Returns:
        True if call process without any error.
        False if error happened.

    """
    subid_caller = get_outgoing_voice_sub_id(ad_caller)
    subid_callee = get_incoming_voice_sub_id(ad_callee)
    subid_forwarded_callee = get_incoming_voice_sub_id(forwarded_callee)
    return call_setup_teardown_for_call_forwarding_for_subscription(
        log,
        ad_caller,
        ad_callee,
        forwarded_callee,
        subid_caller,
        subid_callee,
        subid_forwarded_callee,
        ad_hangup,
        verify_callee_func,
        wait_time_in_call,
        incall_ui_display,
        dialing_number_length,
        video_state,
        call_forwarding_type,
        verify_after_cf_disabled)


def call_setup_teardown_for_call_forwarding_for_subscription(
        log,
        ad_caller,
        ad_callee,
        forwarded_callee,
        subid_caller,
        subid_callee,
        subid_forwarded_callee,
        ad_hangup=None,
        verify_callee_func=None,
        wait_time_in_call=WAIT_TIME_IN_CALL,
        incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND,
        dialing_number_length=None,
        video_state=None,
        call_forwarding_type="unconditional",
        verify_after_cf_disabled=None):
    """ Call process for call forwarding, including make a phone call from caller,
    forward from callee, accept from the forwarded callee and hang up.
    The call is on specified subscription

    In call process, call from <ad_caller> to <ad_callee>, forwarded to
    <forwarded_callee>, accept the call, (optional) and then hang up from
    <ad_hangup>.

    Args:
        ad_caller: Caller Android Device Object.
        ad_callee: Callee Android Device Object which forwards the call.
        forwarded_callee: Callee Android Device Object which answers the call.
        subid_caller: Caller subscription ID
        subid_callee: Callee subscription ID
        subid_forwarded_callee: Forwarded callee subscription ID
        ad_hangup: Android Device Object end the phone call.
            Optional. Default value is None, and phone call will continue.
        verify_callee_func: func_ptr to verify callee in correct mode
            Optional. Default is None
        wait_time_in_call: the call duration of a connected call
        incall_ui_display: after answer the call, bring in-call UI to foreground
        or background. Optional, default value is INCALL_UI_DISPLAY_FOREGROUND.
            if = INCALL_UI_DISPLAY_FOREGROUND, bring in-call UI to foreground.
            if = INCALL_UI_DISPLAY_BACKGROUND, bring in-call UI to background.
            else, do nothing.
        dialing_number_length: the number of digits used for dialing
        video_state: video call or voice call. Default is voice call.
        call_forwarding_type: type of call forwarding listed below:
            - unconditional
            - busy
            - not_answered
            - not_reachable
        verify_after_cf_disabled: If True the call forwarding will not be
        enabled. This argument is used to verify if the call can be received
        successfully after call forwarding was disabled.

    Returns:
        True if call process without any error.
        False if error happened.

    """
    CHECK_INTERVAL = 5
    begin_time = get_current_epoch_time()
    verify_caller_func = is_phone_in_call
    if not verify_callee_func:
        verify_callee_func = is_phone_in_call
    verify_forwarded_callee_func = is_phone_in_call

    caller_number = ad_caller.telephony['subscription'][subid_caller][
        'phone_num']
    callee_number = ad_callee.telephony['subscription'][subid_callee][
        'phone_num']
    forwarded_callee_number = forwarded_callee.telephony['subscription'][
        subid_forwarded_callee]['phone_num']

    if dialing_number_length:
        skip_test = False
        trunc_position = 0 - int(dialing_number_length)
        try:
            caller_area_code = caller_number[:trunc_position]
            callee_area_code = callee_number[:trunc_position]
            callee_dial_number = callee_number[trunc_position:]
        except:
            skip_test = True
        if caller_area_code != callee_area_code:
            skip_test = True
        if skip_test:
            msg = "Cannot make call from %s to %s by %s digits" % (
                caller_number, callee_number, dialing_number_length)
            ad_caller.log.info(msg)
            raise signals.TestSkip(msg)
        else:
            callee_number = callee_dial_number

    result = True
    msg = "Call from %s to %s (forwarded to %s)" % (
        caller_number, callee_number, forwarded_callee_number)
    if video_state:
        msg = "Video %s" % msg
        video = True
    else:
        video = False
    if ad_hangup:
        msg = "%s for duration of %s seconds" % (msg, wait_time_in_call)
    ad_caller.log.info(msg)

    for ad in (ad_caller, forwarded_callee):
        call_ids = ad.droid.telecomCallGetCallIds()
        setattr(ad, "call_ids", call_ids)
        if call_ids:
            ad.log.info("Pre-exist CallId %s before making call", call_ids)

    if not verify_after_cf_disabled:
        if not set_call_forwarding_by_mmi(
            log,
            ad_callee,
            forwarded_callee,
            call_forwarding_type=call_forwarding_type):
            raise signals.TestFailure(
                    "Failed to register or activate call forwarding.",
                    extras={"fail_reason": "Failed to register or activate call"
                    " forwarding."})

    if call_forwarding_type == "not_reachable":
        if not toggle_airplane_mode_msim(
            log,
            ad_callee,
            new_state=True,
            strict_checking=True):
            return False

    if call_forwarding_type == "busy":
        ad_callee.log.info("Callee is making a phone call to 0000000000 to make"
            " itself busy.")
        ad_callee.droid.telecomCallNumber("0000000000", False)
        time.sleep(2)

        if check_call_state_idle_by_adb(ad_callee):
            ad_callee.log.error("Call state of the callee is idle.")
            if not verify_after_cf_disabled:
                erase_call_forwarding_by_mmi(
                    log,
                    ad_callee,
                    call_forwarding_type=call_forwarding_type)
            return False

    try:
        if not initiate_call(
                log,
                ad_caller,
                callee_number,
                incall_ui_display=incall_ui_display,
                video=video):

            ad_caller.log.error("Caller failed to initiate the call.")
            result = False

            if call_forwarding_type == "not_reachable":
                if toggle_airplane_mode_msim(
                    log,
                    ad_callee,
                    new_state=False,
                    strict_checking=True):
                    time.sleep(10)
            elif call_forwarding_type == "busy":
                hangup_call(log, ad_callee)

            if not verify_after_cf_disabled:
                erase_call_forwarding_by_mmi(
                    log,
                    ad_callee,
                    call_forwarding_type=call_forwarding_type)
            return False
        else:
            ad_caller.log.info("Caller initated the call successfully.")

        if call_forwarding_type == "not_answered":
            if not wait_for_ringing_call_for_subscription(
                    log,
                    ad_callee,
                    subid_callee,
                    incoming_number=caller_number,
                    caller=ad_caller,
                    event_tracking_started=True):
                ad.log.info("Incoming call ringing check failed.")
                return False

            _timeout = 30
            while check_call_state_ring_by_adb(ad_callee) == 1 and _timeout >= 0:
                time.sleep(1)
                _timeout = _timeout - 1

        if not wait_and_answer_call_for_subscription(
                log,
                forwarded_callee,
                subid_forwarded_callee,
                incoming_number=caller_number,
                caller=ad_caller,
                incall_ui_display=incall_ui_display,
                video_state=video_state):

            if not verify_after_cf_disabled:
                forwarded_callee.log.error("Forwarded callee failed to receive"
                    "or answer the call.")
                result = False
            else:
                forwarded_callee.log.info("Forwarded callee did not receive or"
                    " answer the call.")

            if call_forwarding_type == "not_reachable":
                if toggle_airplane_mode_msim(
                    log,
                    ad_callee,
                    new_state=False,
                    strict_checking=True):
                    time.sleep(10)
            elif call_forwarding_type == "busy":
                hangup_call(log, ad_callee)

            if not verify_after_cf_disabled:
                erase_call_forwarding_by_mmi(
                    log,
                    ad_callee,
                    call_forwarding_type=call_forwarding_type)
                return False

        else:
            if not verify_after_cf_disabled:
                forwarded_callee.log.info("Forwarded callee answered the call"
                    " successfully.")
            else:
                forwarded_callee.log.error("Forwarded callee should not be able"
                    " to answer the call.")
                hangup_call(log, ad_caller)
                result = False

        for ad, subid, call_func in zip(
                [ad_caller, forwarded_callee],
                [subid_caller, subid_forwarded_callee],
                [verify_caller_func, verify_forwarded_callee_func]):
            call_ids = ad.droid.telecomCallGetCallIds()
            new_call_ids = set(call_ids) - set(ad.call_ids)
            if not new_call_ids:
                if not verify_after_cf_disabled:
                    ad.log.error(
                        "No new call ids are found after call establishment")
                    ad.log.error("telecomCallGetCallIds returns %s",
                                 ad.droid.telecomCallGetCallIds())
                result = False
            for new_call_id in new_call_ids:
                if not verify_after_cf_disabled:
                    if not wait_for_in_call_active(ad, call_id=new_call_id):
                        result = False
                    else:
                        ad.log.info("callProperties = %s",
                            ad.droid.telecomCallGetProperties(new_call_id))
                else:
                    ad.log.error("No new call id should be found.")

            if not ad.droid.telecomCallGetAudioState():
                if not verify_after_cf_disabled:
                    ad.log.error("Audio is not in call state")
                    result = False

            if call_func(log, ad):
                if not verify_after_cf_disabled:
                    ad.log.info("Call is in %s state", call_func.__name__)
                else:
                    ad.log.error("Call is in %s state", call_func.__name__)
            else:
                if not verify_after_cf_disabled:
                    ad.log.error(
                        "Call is not in %s state, voice in RAT %s",
                        call_func.__name__,
                        ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(subid))
                    result = False

        if not result:
            if call_forwarding_type == "not_reachable":
                if toggle_airplane_mode_msim(
                    log,
                    ad_callee,
                    new_state=False,
                    strict_checking=True):
                    time.sleep(10)
            elif call_forwarding_type == "busy":
                hangup_call(log, ad_callee)

            if not verify_after_cf_disabled:
                erase_call_forwarding_by_mmi(
                    log,
                    ad_callee,
                    call_forwarding_type=call_forwarding_type)
                return False

        elapsed_time = 0
        while (elapsed_time < wait_time_in_call):
            CHECK_INTERVAL = min(CHECK_INTERVAL,
                                 wait_time_in_call - elapsed_time)
            time.sleep(CHECK_INTERVAL)
            elapsed_time += CHECK_INTERVAL
            time_message = "at <%s>/<%s> second." % (elapsed_time,
                                                     wait_time_in_call)
            for ad, subid, call_func in [
                (ad_caller, subid_caller, verify_caller_func),
                (forwarded_callee, subid_forwarded_callee,
                    verify_forwarded_callee_func)]:
                if not call_func(log, ad):
                    if not verify_after_cf_disabled:
                        ad.log.error(
                            "NOT in correct %s state at %s, voice in RAT %s",
                            call_func.__name__, time_message,
                            ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(subid))
                    result = False
                else:
                    if not verify_after_cf_disabled:
                        ad.log.info("In correct %s state at %s",
                                    call_func.__name__, time_message)
                    else:
                        ad.log.error("In correct %s state at %s",
                                    call_func.__name__, time_message)

                if not ad.droid.telecomCallGetAudioState():
                    if not verify_after_cf_disabled:
                        ad.log.error("Audio is not in call state at %s",
                                     time_message)
                    result = False

            if not result:
                if call_forwarding_type == "not_reachable":
                    if toggle_airplane_mode_msim(
                        log,
                        ad_callee,
                        new_state=False,
                        strict_checking=True):
                        time.sleep(10)
                elif call_forwarding_type == "busy":
                    hangup_call(log, ad_callee)

                if not verify_after_cf_disabled:
                    erase_call_forwarding_by_mmi(
                        log,
                        ad_callee,
                        call_forwarding_type=call_forwarding_type)
                    return False

        if ad_hangup:
            if not hangup_call(log, ad_hangup):
                ad_hangup.log.info("Failed to hang up the call")
                result = False
                if call_forwarding_type == "not_reachable":
                    if toggle_airplane_mode_msim(
                        log,
                        ad_callee,
                        new_state=False,
                        strict_checking=True):
                        time.sleep(10)
                elif call_forwarding_type == "busy":
                    hangup_call(log, ad_callee)

                if not verify_after_cf_disabled:
                    erase_call_forwarding_by_mmi(
                        log,
                        ad_callee,
                        call_forwarding_type=call_forwarding_type)
                return False
    finally:
        if not result:
            if verify_after_cf_disabled:
                result = True
            else:
                for ad in (ad_caller, forwarded_callee):
                    last_call_drop_reason(ad, begin_time)
                    try:
                        if ad.droid.telecomIsInCall():
                            ad.log.info("In call. End now.")
                            ad.droid.telecomEndCall()
                    except Exception as e:
                        log.error(str(e))

        if ad_hangup or not result:
            for ad in (ad_caller, forwarded_callee):
                if not wait_for_call_id_clearing(
                        ad, getattr(ad, "caller_ids", [])):
                    result = False

    if call_forwarding_type == "not_reachable":
        if toggle_airplane_mode_msim(
            log,
            ad_callee,
            new_state=False,
            strict_checking=True):
            time.sleep(10)
    elif call_forwarding_type == "busy":
        hangup_call(log, ad_callee)

    if not verify_after_cf_disabled:
        erase_call_forwarding_by_mmi(
            log,
            ad_callee,
            call_forwarding_type=call_forwarding_type)

    if not result:
        return result

    ad_caller.log.info(
        "Make a normal call to callee to ensure the call can be connected after"
        " call forwarding was disabled")
    return call_setup_teardown_for_subscription(
        log, ad_caller, ad_callee, subid_caller, subid_callee, ad_caller,
        verify_caller_func, verify_callee_func, wait_time_in_call,
        incall_ui_display, dialing_number_length, video_state)


def get_call_forwarding_by_adb(log, ad, call_forwarding_type="unconditional"):
    """ Get call forwarding status by adb shell command
        'dumpsys telephony.registry'.

        Args:
            log: log object
            ad: android object
            call_forwarding_type:
                - "unconditional"
                - "busy" (todo)
                - "not_answered" (todo)
                - "not_reachable" (todo)
        Returns:
            - "true": if call forwarding unconditional is enabled.
            - "false": if call forwarding unconditional is disabled.
            - "unknown": if the type is other than 'unconditional'.
            - False: any case other than above 3 cases.
    """
    if call_forwarding_type != "unconditional":
        return "unknown"

    slot_index_of_default_voice_subid = get_slot_index_from_subid(ad,
        get_incoming_voice_sub_id(ad))
    output = ad.adb.shell("dumpsys telephony.registry | grep mCallForwarding")
    if "mCallForwarding" in output:
        result_list = re.findall(r"mCallForwarding=(true|false)", output)
        if result_list:
            result = result_list[slot_index_of_default_voice_subid]
            ad.log.info("mCallForwarding is %s", result)

            if re.search("false", result, re.I):
                return "false"
            elif re.search("true", result, re.I):
                return "true"
            else:
                return False
        else:
            return False
    else:
        ad.log.error("'mCallForwarding' cannot be found in dumpsys.")
        return False


def erase_call_forwarding_by_mmi(
        log,
        ad,
        retry=2,
        call_forwarding_type="unconditional"):
    """ Erase setting of call forwarding (erase the number and disable call
    forwarding) by MMI code.

    Args:
        log: log object
        ad: android object
        retry: times of retry if the erasure failed.
        call_forwarding_type:
            - "unconditional"
            - "busy"
            - "not_answered"
            - "not_reachable"
    Returns:
        True by successful erasure. Otherwise False.
    """
    operator_name = get_operator_name(log, ad)

    run_get_call_forwarding_by_adb = 1
    if operator_name in NOT_CHECK_MCALLFORWARDING_OPERATOR_LIST:
        run_get_call_forwarding_by_adb = 0

    if run_get_call_forwarding_by_adb:
        res = get_call_forwarding_by_adb(log, ad,
            call_forwarding_type=call_forwarding_type)
        if res == "false":
            return True

    user_config_profile = get_user_config_profile(ad)
    is_airplane_mode = user_config_profile["Airplane Mode"]
    is_wfc_enabled = user_config_profile["WFC Enabled"]
    wfc_mode = user_config_profile["WFC Mode"]
    is_wifi_on = user_config_profile["WiFi State"]

    if is_airplane_mode:
        if not toggle_airplane_mode(log, ad, False):
            ad.log.error("Failed to disable airplane mode.")
            return False

    code_dict = {
        "Verizon": {
            "unconditional": "73",
            "busy": "73",
            "not_answered": "73",
            "not_reachable": "73",
            "mmi": "*%s"
        },
        "Sprint": {
            "unconditional": "720",
            "busy": "740",
            "not_answered": "730",
            "not_reachable": "720",
            "mmi": "*%s"
        },
        "Far EasTone": {
            "unconditional": "142",
            "busy": "143",
            "not_answered": "144",
            "not_reachable": "144",
            "mmi": "*%s*2"
        },
        'Generic': {
            "unconditional": "21",
            "busy": "67",
            "not_answered": "61",
            "not_reachable": "62",
            "mmi": "##%s#"
        }
    }

    if operator_name in code_dict:
        code = code_dict[operator_name][call_forwarding_type]
        mmi = code_dict[operator_name]["mmi"]
    else:
        code = code_dict['Generic'][call_forwarding_type]
        mmi = code_dict['Generic']["mmi"]

    result = False
    while retry >= 0:
        if run_get_call_forwarding_by_adb:
            res = get_call_forwarding_by_adb(
                log, ad, call_forwarding_type=call_forwarding_type)
            if res == "false":
                ad.log.info("Call forwarding is already disabled.")
                result = True
                break

        ad.log.info("Erasing and deactivating call forwarding %s..." %
            call_forwarding_type)

        ad.droid.telecomDialNumber(mmi % code)

        time.sleep(3)
        ad.send_keycode("ENTER")
        time.sleep(15)

        # To dismiss the pop-out dialog
        ad.send_keycode("BACK")
        time.sleep(5)
        ad.send_keycode("BACK")

        if run_get_call_forwarding_by_adb:
            res = get_call_forwarding_by_adb(
                log, ad, call_forwarding_type=call_forwarding_type)
            if res == "false" or res == "unknown":
                result = True
                break
            else:
                ad.log.error("Failed to erase and deactivate call forwarding by "
                    "MMI code ##%s#." % code)
                retry = retry - 1
                time.sleep(30)
        else:
            result = True
            break

    if is_airplane_mode:
        if not toggle_airplane_mode(log, ad, True):
            ad.log.error("Failed to enable airplane mode again.")
        else:
            if is_wifi_on:
                ad.droid.wifiToggleState(True)
                if is_wfc_enabled:
                    if not wait_for_wfc_enabled(
                        log, ad,max_time=MAX_WAIT_TIME_WFC_ENABLED):
                        ad.log.error("WFC is not enabled")

    return result

def set_call_forwarding_by_mmi(
        log,
        ad,
        ad_forwarded,
        call_forwarding_type="unconditional",
        retry=2):
    """ Set up the forwarded number and enable call forwarding by MMI code.

    Args:
        log: log object
        ad: android object of the device forwarding the call (primary device)
        ad_forwarded: android object of the device receiving forwarded call.
        retry: times of retry if the erasure failed.
        call_forwarding_type:
            - "unconditional"
            - "busy"
            - "not_answered"
            - "not_reachable"
    Returns:
        True by successful erasure. Otherwise False.
    """

    res = get_call_forwarding_by_adb(log, ad,
        call_forwarding_type=call_forwarding_type)
    if res == "true":
        return True

    if ad.droid.connectivityCheckAirplaneMode():
        ad.log.warning("%s is now in airplane mode.", ad.serial)
        return True

    operator_name = get_operator_name(log, ad)

    code_dict = {
        "Verizon": {
            "unconditional": "72",
            "busy": "71",
            "not_answered": "71",
            "not_reachable": "72",
            "mmi": "*%s%s"
        },
        "Sprint": {
            "unconditional": "72",
            "busy": "74",
            "not_answered": "73",
            "not_reachable": "72",
            "mmi": "*%s%s"
        },
        "Far EasTone": {
            "unconditional": "142",
            "busy": "143",
            "not_answered": "144",
            "not_reachable": "144",
            "mmi": "*%s*%s"
        },
        'Generic': {
            "unconditional": "21",
            "busy": "67",
            "not_answered": "61",
            "not_reachable": "62",
            "mmi": "*%s*%s#",
            "mmi_for_plus_sign": "*%s*"
        }
    }

    if operator_name in code_dict:
        code = code_dict[operator_name][call_forwarding_type]
        mmi = code_dict[operator_name]["mmi"]
        if "mmi_for_plus_sign" in code_dict[operator_name]:
            mmi_for_plus_sign = code_dict[operator_name]["mmi_for_plus_sign"]
    else:
        code = code_dict['Generic'][call_forwarding_type]
        mmi = code_dict['Generic']["mmi"]
        mmi_for_plus_sign = code_dict['Generic']["mmi_for_plus_sign"]

    while retry >= 0:
        if not erase_call_forwarding_by_mmi(
            log, ad, call_forwarding_type=call_forwarding_type):
            retry = retry - 1
            continue

        forwarded_number = ad_forwarded.telephony['subscription'][
            ad_forwarded.droid.subscriptionGetDefaultVoiceSubId()][
            'phone_num']
        ad.log.info("Registering and activating call forwarding %s to %s..." %
            (call_forwarding_type, forwarded_number))

        (forwarded_number_no_prefix, _) = _phone_number_remove_prefix(
            forwarded_number)

        if operator_name == "Far EasTone":
            forwarded_number_no_prefix = "0" + forwarded_number_no_prefix

        run_get_call_forwarding_by_adb = 1
        if operator_name in NOT_CHECK_MCALLFORWARDING_OPERATOR_LIST:
            run_get_call_forwarding_by_adb = 0

        _found_plus_sign = 0
        if re.search("^\+", forwarded_number):
            _found_plus_sign = 1
            forwarded_number.replace("+", "")

        if operator_name in code_dict:
            ad.droid.telecomDialNumber(mmi % (code, forwarded_number_no_prefix))
        else:
            if _found_plus_sign == 0:
                ad.droid.telecomDialNumber(mmi % (code, forwarded_number))
            else:
                ad.droid.telecomDialNumber(mmi_for_plus_sign % code)
                ad.send_keycode("PLUS")

                if "#" in mmi:
                    dial_phone_number(ad, forwarded_number + "#")
                else:
                    dial_phone_number(ad, forwarded_number)

        time.sleep(3)
        ad.send_keycode("ENTER")
        time.sleep(15)

        # To dismiss the pop-out dialog
        ad.send_keycode("BACK")
        time.sleep(5)
        ad.send_keycode("BACK")

        if not run_get_call_forwarding_by_adb:
            return True

        result = get_call_forwarding_by_adb(
            log, ad, call_forwarding_type=call_forwarding_type)
        if result == "false":
            retry = retry - 1
        elif result == "true":
            return True
        elif result == "unknown":
            return True
        else:
            retry = retry - 1

        if retry >= 0:
            ad.log.warning("Failed to register or activate call forwarding %s "
                "to %s. Retry after 15 seconds." % (call_forwarding_type,
                    forwarded_number))
            time.sleep(15)

    ad.log.error("Failed to register or activate call forwarding %s to %s." %
        (call_forwarding_type, forwarded_number))
    return False


def call_setup_teardown_for_call_waiting(log,
                        ad_caller,
                        ad_callee,
                        ad_caller2,
                        ad_hangup=None,
                        ad_hangup2=None,
                        verify_callee_func=None,
                        end_first_call_before_answering_second_call=True,
                        wait_time_in_call=WAIT_TIME_IN_CALL,
                        incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND,
                        dialing_number_length=None,
                        video_state=None,
                        call_waiting=True):
    """ Call process for call waiting, including make the 1st phone call from
    caller, answer the call by the callee, and receive the 2nd call from the
    caller2. The call is on default voice subscription

    In call process, 1st call from <ad_caller> to <ad_callee>, 2nd call from
    <ad_caller2> to <ad_callee>, hang up the existing call or reject the
    incoming call according to the test scenario.

    Args:
        ad_caller: Caller Android Device Object.
        ad_callee: Callee Android Device Object.
        ad_caller2: Caller2 Android Device Object.
        ad_hangup: Android Device Object end the 1st phone call.
            Optional. Default value is None, and phone call will continue.
        ad_hangup2: Android Device Object end the 2nd phone call.
            Optional. Default value is None, and phone call will continue.
        verify_callee_func: func_ptr to verify callee in correct mode
            Optional. Default is None
        end_first_call_before_answering_second_call: If True the 2nd call will
            be rejected on the ringing stage.
        wait_time_in_call: the call duration of a connected call
        incall_ui_display: after answer the call, bring in-call UI to foreground
        or background.
            Optional, default value is INCALL_UI_DISPLAY_FOREGROUND.
            if = INCALL_UI_DISPLAY_FOREGROUND, bring in-call UI to foreground.
            if = INCALL_UI_DISPLAY_BACKGROUND, bring in-call UI to background.
            else, do nothing.
        dialing_number_length: the number of digits used for dialing
        video_state: video call or voice call. Default is voice call.
        call_waiting: True to enable call waiting and False to disable.

    Returns:
        True if call process without any error.
        False if error happened.

    """
    subid_caller = get_outgoing_voice_sub_id(ad_caller)
    subid_callee = get_incoming_voice_sub_id(ad_callee)
    subid_caller2 = get_incoming_voice_sub_id(ad_caller2)
    return call_setup_teardown_for_call_waiting_for_subscription(
        log,
        ad_caller,
        ad_callee,
        ad_caller2,
        subid_caller,
        subid_callee,
        subid_caller2,
        ad_hangup, ad_hangup2,
        verify_callee_func,
        end_first_call_before_answering_second_call,
        wait_time_in_call,
        incall_ui_display,
        dialing_number_length,
        video_state,
        call_waiting)


def call_setup_teardown_for_call_waiting_for_subscription(
        log,
        ad_caller,
        ad_callee,
        ad_caller2,
        subid_caller,
        subid_callee,
        subid_caller2,
        ad_hangup=None,
        ad_hangup2=None,
        verify_callee_func=None,
        end_first_call_before_answering_second_call=True,
        wait_time_in_call=WAIT_TIME_IN_CALL,
        incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND,
        dialing_number_length=None,
        video_state=None,
        call_waiting=True):
    """ Call process for call waiting, including make the 1st phone call from
    caller, answer the call by the callee, and receive the 2nd call from the
    caller2. The call is on specified subscription.

    In call process, 1st call from <ad_caller> to <ad_callee>, 2nd call from
    <ad_caller2> to <ad_callee>, hang up the existing call or reject the
    incoming call according to the test scenario.

    Args:
        ad_caller: Caller Android Device Object.
        ad_callee: Callee Android Device Object.
        ad_caller2: Caller2 Android Device Object.
        subid_caller: Caller subscription ID.
        subid_callee: Callee subscription ID.
        subid_caller2: Caller2 subscription ID.
        ad_hangup: Android Device Object end the 1st phone call.
            Optional. Default value is None, and phone call will continue.
        ad_hangup2: Android Device Object end the 2nd phone call.
            Optional. Default value is None, and phone call will continue.
        verify_callee_func: func_ptr to verify callee in correct mode
            Optional. Default is None
        end_first_call_before_answering_second_call: If True the 2nd call will
            be rejected on the ringing stage.
        wait_time_in_call: the call duration of a connected call
        incall_ui_display: after answer the call, bring in-call UI to foreground
        or background. Optional, default value is INCALL_UI_DISPLAY_FOREGROUND.
            if = INCALL_UI_DISPLAY_FOREGROUND, bring in-call UI to foreground.
            if = INCALL_UI_DISPLAY_BACKGROUND, bring in-call UI to background.
            else, do nothing.
        dialing_number_length: the number of digits used for dialing
        video_state: video call or voice call. Default is voice call.
        call_waiting: True to enable call waiting and False to disable.

    Returns:
        True if call process without any error.
        False if error happened.

    """

    CHECK_INTERVAL = 5
    begin_time = get_current_epoch_time()
    verify_caller_func = is_phone_in_call
    if not verify_callee_func:
        verify_callee_func = is_phone_in_call
    verify_caller2_func = is_phone_in_call

    caller_number = ad_caller.telephony['subscription'][subid_caller][
        'phone_num']
    callee_number = ad_callee.telephony['subscription'][subid_callee][
        'phone_num']
    caller2_number = ad_caller2.telephony['subscription'][subid_caller2][
        'phone_num']
    if dialing_number_length:
        skip_test = False
        trunc_position = 0 - int(dialing_number_length)
        try:
            caller_area_code = caller_number[:trunc_position]
            callee_area_code = callee_number[:trunc_position]
            callee_dial_number = callee_number[trunc_position:]
        except:
            skip_test = True
        if caller_area_code != callee_area_code:
            skip_test = True
        if skip_test:
            msg = "Cannot make call from %s to %s by %s digits" % (
                caller_number, callee_number, dialing_number_length)
            ad_caller.log.info(msg)
            raise signals.TestSkip(msg)
        else:
            callee_number = callee_dial_number

    result = True
    msg = "Call from %s to %s" % (caller_number, callee_number)
    if video_state:
        msg = "Video %s" % msg
        video = True
    else:
        video = False
    if ad_hangup:
        msg = "%s for duration of %s seconds" % (msg, wait_time_in_call)
    ad_caller.log.info(msg)

    for ad in (ad_caller, ad_callee, ad_caller2):
        call_ids = ad.droid.telecomCallGetCallIds()
        setattr(ad, "call_ids", call_ids)
        if call_ids:
            ad.log.info("Pre-exist CallId %s before making call", call_ids)

    if not call_waiting:
        set_call_waiting(log, ad_callee, enable=0)
    else:
        set_call_waiting(log, ad_callee, enable=1)

    first_call_ids = []
    try:
        if not initiate_call(
                log,
                ad_caller,
                callee_number,
                incall_ui_display=incall_ui_display,
                video=video):
            ad_caller.log.error("Initiate call failed.")
            if not call_waiting:
                set_call_waiting(log, ad_callee, enable=1)
            result = False
            return False
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
            if not call_waiting:
                set_call_waiting(log, ad_callee, enable=1)
            result = False
            return False
        else:
            ad_callee.log.info("Callee answered the call successfully")

        for ad, subid, call_func in zip(
            [ad_caller, ad_callee],
            [subid_caller, subid_callee],
            [verify_caller_func, verify_callee_func]):
            call_ids = ad.droid.telecomCallGetCallIds()
            new_call_ids = set(call_ids) - set(ad.call_ids)
            if not new_call_ids:
                ad.log.error(
                    "No new call ids are found after call establishment")
                ad.log.error("telecomCallGetCallIds returns %s",
                             ad.droid.telecomCallGetCallIds())
                result = False
            for new_call_id in new_call_ids:
                first_call_ids.append(new_call_id)
                if not wait_for_in_call_active(ad, call_id=new_call_id):
                    result = False
                else:
                    ad.log.info("callProperties = %s",
                                ad.droid.telecomCallGetProperties(new_call_id))

            if not ad.droid.telecomCallGetAudioState():
                ad.log.error("Audio is not in call state")
                result = False

            if call_func(log, ad):
                ad.log.info("Call is in %s state", call_func.__name__)
            else:
                ad.log.error("Call is not in %s state, voice in RAT %s",
                             call_func.__name__,
                             ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(subid))
                result = False
        if not result:
            if not call_waiting:
                set_call_waiting(log, ad_callee, enable=1)
            return False

        time.sleep(3)
        if not call_waiting:
            if not initiate_call(
                    log,
                    ad_caller2,
                    callee_number,
                    incall_ui_display=incall_ui_display,
                    video=video):
                ad_caller2.log.info("Initiate call failed.")
                if not call_waiting:
                    set_call_waiting(log, ad_callee, enable=1)
                result = False
                return False
            else:
                ad_caller2.log.info("Caller 2 initate 2nd call successfully")

            if not wait_and_answer_call_for_subscription(
                    log,
                    ad_callee,
                    subid_callee,
                    incoming_number=caller2_number,
                    caller=ad_caller2,
                    incall_ui_display=incall_ui_display,
                    video_state=video_state):
                ad_callee.log.info(
                    "Answering 2nd call fail due to call waiting　deactivate.")
            else:
                ad_callee.log.error("Callee should not be able to answer the"
                    " 2nd call due to call waiting deactivated.")
                if not call_waiting:
                    set_call_waiting(log, ad_callee, enable=1)
                result = False
                return False

            time.sleep(3)
            if not hangup_call(log, ad_caller2):
                ad_caller2.log.info("Failed to hang up the 2nd call")
                if not call_waiting:
                    set_call_waiting(log, ad_callee, enable=1)
                result = False
                return False

        else:

            for ad in (ad_callee, ad_caller2):
                call_ids = ad.droid.telecomCallGetCallIds()
                setattr(ad, "call_ids", call_ids)
                if call_ids:
                    ad.log.info("Current existing CallId %s before making the"
                        "　second call.", call_ids)

            if not initiate_call(
                    log,
                    ad_caller2,
                    callee_number,
                    incall_ui_display=incall_ui_display,
                    video=video):
                ad_caller2.log.info("Initiate 2nd call failed.")
                if not call_waiting:
                    set_call_waiting(log, ad_callee, enable=1)
                result = False
                return False
            else:
                ad_caller2.log.info("Caller 2 initate 2nd call successfully")

            if end_first_call_before_answering_second_call:
                try:
                    if not wait_for_ringing_call_for_subscription(
                            log,
                            ad_callee,
                            subid_callee,
                            incoming_number=caller2_number,
                            caller=ad_caller2,
                            event_tracking_started=True):
                        ad_callee.log.info(
                            "2nd incoming call ringing check　failed.")
                        if not call_waiting:
                            set_call_waiting(log, ad_callee, enable=1)
                        return False

                    time.sleep(3)

                    ad_hangup.log.info("Disconnecting first call...")
                    for call_id in first_call_ids:
                        disconnect_call_by_id(log, ad_hangup, call_id)
                    time.sleep(3)

                    ad_callee.log.info("Answering the 2nd ring call...")
                    ad_callee.droid.telecomAcceptRingingCall(video_state)

                    if wait_for_call_offhook_for_subscription(
                            log,
                            ad_callee,
                            subid_callee,
                            event_tracking_started=True):
                        ad_callee.log.info(
                            "Callee answered the 2nd call successfully.")
                    else:
                        ad_callee.log.error("Could not answer the 2nd call.")
                        if not call_waiting:
                            set_call_waiting(log, ad_callee, enable=1)
                        return False
                except Exception as e:
                    log.error(e)
                    if not call_waiting:
                        set_call_waiting(log, ad_callee, enable=1)
                    return False

            else:
                if not wait_and_answer_call_for_subscription(
                        log,
                        ad_callee,
                        subid_callee,
                        incoming_number=caller2_number,
                        caller=ad_caller2,
                        incall_ui_display=incall_ui_display,
                        video_state=video_state):
                    ad_callee.log.error("Failed to answer 2nd call.")
                    if not call_waiting:
                        set_call_waiting(log, ad_callee, enable=1)
                    result = False
                    return False
                else:
                    ad_callee.log.info(
                        "Callee answered the 2nd call successfully.")

            for ad, subid, call_func in zip(
                [ad_callee, ad_caller2],
                [subid_callee, subid_caller2],
                [verify_callee_func, verify_caller2_func]):
                call_ids = ad.droid.telecomCallGetCallIds()
                new_call_ids = set(call_ids) - set(ad.call_ids)
                if not new_call_ids:
                    ad.log.error(
                        "No new call ids are found after 2nd call establishment")
                    ad.log.error("telecomCallGetCallIds returns %s",
                                 ad.droid.telecomCallGetCallIds())
                    result = False
                for new_call_id in new_call_ids:
                    if not wait_for_in_call_active(ad, call_id=new_call_id):
                        result = False
                    else:
                        ad.log.info("callProperties = %s",
                            ad.droid.telecomCallGetProperties(new_call_id))

                if not ad.droid.telecomCallGetAudioState():
                    ad.log.error("Audio is not in 2nd call state")
                    result = False

                if call_func(log, ad):
                    ad.log.info("2nd call is in %s state", call_func.__name__)
                else:
                    ad.log.error("2nd call is not in %s state, voice in RAT %s",
                                 call_func.__name__,
                                 ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(subid))
                    result = False
            if not result:
                if not call_waiting:
                    set_call_waiting(log, ad_callee, enable=1)
                return False

        elapsed_time = 0
        while (elapsed_time < wait_time_in_call):
            CHECK_INTERVAL = min(CHECK_INTERVAL,
                                 wait_time_in_call - elapsed_time)
            time.sleep(CHECK_INTERVAL)
            elapsed_time += CHECK_INTERVAL
            time_message = "at <%s>/<%s> second." % (elapsed_time,
                                                     wait_time_in_call)

            if not end_first_call_before_answering_second_call or \
                not call_waiting:
                for ad, subid, call_func in [
                    (ad_caller, subid_caller, verify_caller_func),
                    (ad_callee, subid_callee, verify_callee_func)]:
                    if not call_func(log, ad):
                        ad.log.error(
                            "The first call NOT in correct %s state at %s,"
                            " voice in RAT %s",
                            call_func.__name__, time_message,
                            ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(subid))
                        result = False
                    else:
                        ad.log.info("The first call in correct %s state at %s",
                                    call_func.__name__, time_message)
                    if not ad.droid.telecomCallGetAudioState():
                        ad.log.error(
                            "The first call audio is not in call state at %s",
                            time_message)
                        result = False
                if not result:
                    if not call_waiting:
                        set_call_waiting(log, ad_callee, enable=1)
                    return False

            if call_waiting:
                for ad, call_func in [(ad_caller2, verify_caller2_func),
                                      (ad_callee, verify_callee_func)]:
                    if not call_func(log, ad):
                        ad.log.error(
                            "The 2nd call NOT in correct %s state at %s,"
                            " voice in RAT %s",
                            call_func.__name__, time_message,
                            ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(subid))
                        result = False
                    else:
                        ad.log.info("The 2nd call in correct %s state at %s",
                                    call_func.__name__, time_message)
                    if not ad.droid.telecomCallGetAudioState():
                        ad.log.error(
                            "The 2nd call audio is not in call state at %s",
                            time_message)
                        result = False
            if not result:
                if not call_waiting:
                    set_call_waiting(log, ad_callee, enable=1)
                return False

        if not end_first_call_before_answering_second_call or not call_waiting:
            ad_hangup.log.info("Hanging up the first call...")
            for call_id in first_call_ids:
                disconnect_call_by_id(log, ad_hangup, call_id)
            time.sleep(5)

        if ad_hangup2 and call_waiting:
            if not hangup_call(log, ad_hangup2):
                ad_hangup2.log.info("Failed to hang up the 2nd call")
                if not call_waiting:
                    set_call_waiting(log, ad_callee, enable=1)
                result = False
                return False
    finally:
        if not result:
            for ad in (ad_caller, ad_callee, ad_caller2):
                last_call_drop_reason(ad, begin_time)
                try:
                    if ad.droid.telecomIsInCall():
                        ad.log.info("In call. End now.")
                        ad.droid.telecomEndCall()
                except Exception as e:
                    log.error(str(e))

        if ad_hangup or not result:
            for ad in (ad_caller, ad_callee):
                if not wait_for_call_id_clearing(
                        ad, getattr(ad, "caller_ids", [])):
                    result = False

        if call_waiting:
            if ad_hangup2 or not result:
                for ad in (ad_caller2, ad_callee):
                    if not wait_for_call_id_clearing(
                            ad, getattr(ad, "caller_ids", [])):
                        result = False
    if not call_waiting:
        set_call_waiting(log, ad_callee, enable=1)
    return result


def get_call_waiting_status(log, ad):
    """ (Todo) Get call waiting status (activated or deactivated) when there is
    any proper method available.
    """
    return True


def set_call_waiting(log, ad, enable=1, retry=1):
    """ Activate/deactivate call waiting by dialing MMI code.

    Args:
        log: log object.
        ad: android object.
        enable: 1 for activation and 0 fir deactivation
        retry: times of retry if activation/deactivation fails

    Returns:
        True by successful activation/deactivation; otherwise False.
    """
    operator_name = get_operator_name(log, ad)

    if operator_name in ["Verizon", "Sprint"]:
        return True

    while retry >= 0:
        if enable:
            ad.log.info("Activating call waiting...")
            ad.droid.telecomDialNumber("*43#")
        else:
            ad.log.info("Deactivating call waiting...")
            ad.droid.telecomDialNumber("#43#")

        time.sleep(3)
        ad.send_keycode("ENTER")
        time.sleep(15)

        ad.send_keycode("BACK")
        time.sleep(5)
        ad.send_keycode("BACK")

        if get_call_waiting_status(log, ad):
            return True
        else:
            retry = retry + 1

    return False


def three_phone_call_forwarding_short_seq(log,
                             phone_a,
                             phone_a_idle_func,
                             phone_a_in_call_check_func,
                             phone_b,
                             phone_c,
                             wait_time_in_call=WAIT_TIME_IN_CALL,
                             call_forwarding_type="unconditional",
                             retry=2):
    """Short sequence of call process with call forwarding.
    Test steps:
        1. Ensure all phones are initially in idle state.
        2. Enable call forwarding on Phone A.
        3. Make a call from Phone B to Phone A, The call should be forwarded to
           PhoneC. Accept the call on Phone C.
        4. Ensure the call is connected and in correct phone state.
        5. Hang up the call on Phone B.
        6. Ensure all phones are in idle state.
        7. Disable call forwarding on Phone A.
        7. Make a call from Phone B to Phone A, The call should NOT be forwarded
           to PhoneC. Accept the call on Phone A.
        8. Ensure the call is connected and in correct phone state.
        9. Hang up the call on Phone B.

    Args:
        phone_a: android object of Phone A
        phone_a_idle_func: function to check idle state on Phone A
        phone_a_in_call_check_func: function to check in-call state on Phone A
        phone_b: android object of Phone B
        phone_c: android object of Phone C
        wait_time_in_call: time to wait in call.
            This is optional, default is WAIT_TIME_IN_CALL
        call_forwarding_type:
            - "unconditional"
            - "busy"
            - "not_answered"
            - "not_reachable"
        retry: times of retry

    Returns:
        True: if call sequence succeed.
        False: for errors
    """
    ads = [phone_a, phone_b, phone_c]

    call_params = [
        (ads[1], ads[0], ads[2], ads[1], phone_a_in_call_check_func, False)
    ]

    if call_forwarding_type != "unconditional":
        call_params.append((
            ads[1],
            ads[0],
            ads[2],
            ads[1],
            phone_a_in_call_check_func,
            True))

    for param in call_params:
        ensure_phones_idle(log, ads)
        if phone_a_idle_func and not phone_a_idle_func(log, phone_a):
            phone_a.log.error("Phone A Failed to Reselect")
            return False

        time.sleep(WAIT_TIME_BETWEEN_REG_AND_CALL)

        log.info(
            "---> Call forwarding %s (caller: %s, callee: %s, callee forwarded:"
            " %s) <---",
            call_forwarding_type,
            param[0].serial,
            param[1].serial,
            param[2].serial)
        while not call_setup_teardown_for_call_forwarding(
                log,
                *param,
                wait_time_in_call=wait_time_in_call,
                call_forwarding_type=call_forwarding_type) and retry >= 0:

            if retry <= 0:
                log.error("Call forwarding %s failed." % call_forwarding_type)
                return False
            else:
                log.info(
                    "RERUN the test case: 'Call forwarding %s'" %
                    call_forwarding_type)

            retry = retry - 1

    return True

def three_phone_call_waiting_short_seq(log,
                             phone_a,
                             phone_a_idle_func,
                             phone_a_in_call_check_func,
                             phone_b,
                             phone_c,
                             wait_time_in_call=WAIT_TIME_IN_CALL,
                             call_waiting=True,
                             scenario=None,
                             retry=2):
    """Short sequence of call process with call waiting.
    Test steps:
        1. Ensure all phones are initially in idle state.
        2. Enable call waiting on Phone A.
        3. Make the 1st call from Phone B to Phone A. Accept the call on Phone B.
        4. Ensure the call is connected and in correct phone state.
        5. Make the 2nd call from Phone C to Phone A. The call should be able to
           income correctly. Whether or not the 2nd call should be answered by
           Phone A depends on the scenario listed in the next step.
        6. Following 8 scenarios will be tested:
           - 1st call ended first by Phone B during 2nd call incoming. 2nd call
             ended by Phone C
           - 1st call ended first by Phone B during 2nd call incoming. 2nd call
             ended by Phone A
           - 1st call ended first by Phone A during 2nd call incoming. 2nd call
             ended by Phone C
           - 1st call ended first by Phone A during 2nd call incoming. 2nd call
             ended by Phone A
           - 1st call ended by Phone B. 2nd call ended by Phone C
           - 1st call ended by Phone B. 2nd call ended by Phone A
           - 1st call ended by Phone A. 2nd call ended by Phone C
           - 1st call ended by Phone A. 2nd call ended by Phone A
        7. Ensure all phones are in idle state.

    Args:
        phone_a: android object of Phone A
        phone_a_idle_func: function to check idle state on Phone A
        phone_a_in_call_check_func: function to check in-call state on Phone A
        phone_b: android object of Phone B
        phone_c: android object of Phone C
        wait_time_in_call: time to wait in call.
            This is optional, default is WAIT_TIME_IN_CALL
        call_waiting: True for call waiting enabled and False for disabled
        scenario: 1-8 for scenarios listed above
        retry: times of retry

    Returns:
        True: if call sequence succeed.
        False: for errors
    """
    ads = [phone_a, phone_b, phone_c]

    sub_test_cases = [
        {
            "description": "1st call ended first by caller1 during 2nd call"
                " incoming. 2nd call ended by caller2",
            "params": (
                ads[1],
                ads[0],
                ads[2],
                ads[1],
                ads[2],
                phone_a_in_call_check_func,
                True)},
        {
            "description": "1st call ended first by caller1 during 2nd call"
                " incoming. 2nd call ended by callee",
            "params": (
                ads[1],
                ads[0],
                ads[2],
                ads[1],
                ads[0],
                phone_a_in_call_check_func,
                True)},
        {
            "description": "1st call ended first by callee during 2nd call"
                " incoming. 2nd call ended by caller2",
            "params": (
                ads[1],
                ads[0],
                ads[2],
                ads[0],
                ads[2],
                phone_a_in_call_check_func,
                True)},
        {
            "description": "1st call ended first by callee during 2nd call"
                " incoming. 2nd call ended by callee",
            "params": (
                ads[1],
                ads[0],
                ads[2],
                ads[0],
                ads[0],
                phone_a_in_call_check_func,
                True)},
        {
            "description": "1st call ended by caller1. 2nd call ended by"
                " caller2",
            "params": (
                ads[1],
                ads[0],
                ads[2],
                ads[1],
                ads[2],
                phone_a_in_call_check_func,
                False)},
        {
            "description": "1st call ended by caller1. 2nd call ended by callee",
            "params": (
                ads[1],
                ads[0],
                ads[2],
                ads[1],
                ads[0],
                phone_a_in_call_check_func,
                False)},
        {
            "description": "1st call ended by callee. 2nd call ended by caller2",
            "params": (
                ads[1],
                ads[0],
                ads[2],
                ads[0],
                ads[2],
                phone_a_in_call_check_func,
                False)},
        {
            "description": "1st call ended by callee. 2nd call ended by callee",
            "params": (
                ads[1],
                ads[0],
                ads[2],
                ads[0],
                ads[0],
                phone_a_in_call_check_func,
                False)}
    ]

    if call_waiting:
        if not scenario:
            test_cases = sub_test_cases
        else:
            test_cases = [sub_test_cases[scenario-1]]
    else:
        test_cases = [
            {
                "description": "Call waiting deactivated",
                "params": (
                    ads[1],
                    ads[0],
                    ads[2],
                    ads[0],
                    ads[0],
                    phone_a_in_call_check_func,
                    False)}
        ]

    results = []

    for test_case in test_cases:
        ensure_phones_idle(log, ads)
        if phone_a_idle_func and not phone_a_idle_func(log, phone_a):
            phone_a.log.error("Phone A Failed to Reselect")
            return False

        time.sleep(WAIT_TIME_BETWEEN_REG_AND_CALL)

        log.info(
            "---> %s (caller1: %s, caller2: %s, callee: %s) <---",
            test_case["description"],
            test_case["params"][1].serial,
            test_case["params"][2].serial,
            test_case["params"][0].serial)

        while not call_setup_teardown_for_call_waiting(
            log,
            *test_case["params"],
            wait_time_in_call=wait_time_in_call,
            call_waiting=call_waiting) and retry >= 0:

            if retry <= 0:
                log.error("Call waiting sub-case: '%s' failed." % test_case[
                    "description"])
                results.append(False)
            else:
                log.info("RERUN the sub-case: '%s'" % test_case["description"])

            retry = retry - 1

    for result in results:
        if not result:
            return False

    return True