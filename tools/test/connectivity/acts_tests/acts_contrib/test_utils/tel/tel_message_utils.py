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
from acts.utils import rand_ascii_str
from acts.libs.utils.multithread import multithread_func
from acts.libs.utils.multithread import run_multithread_func
from acts_contrib.test_utils.tel.tel_defines import EventCallStateChanged
from acts_contrib.test_utils.tel.tel_defines import EventMmsSentFailure
from acts_contrib.test_utils.tel.tel_defines import EventMmsSentSuccess
from acts_contrib.test_utils.tel.tel_defines import EventMmsDownloaded
from acts_contrib.test_utils.tel.tel_defines import EventSmsDeliverFailure
from acts_contrib.test_utils.tel.tel_defines import EventSmsDeliverSuccess
from acts_contrib.test_utils.tel.tel_defines import EventSmsReceived
from acts_contrib.test_utils.tel.tel_defines import EventSmsSentFailure
from acts_contrib.test_utils.tel.tel_defines import EventSmsSentSuccess
from acts_contrib.test_utils.tel.tel_defines import INCALL_UI_DISPLAY_BACKGROUND
from acts_contrib.test_utils.tel.tel_defines import INCALL_UI_DISPLAY_FOREGROUND
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_MMS_RECEIVE
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_SMS_RECEIVE
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_SMS_RECEIVE_IN_COLLISION
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_SMS_SENT_SUCCESS_IN_COLLISION
from acts_contrib.test_utils.tel.tel_defines import SMS_OVER_WIFI_PROVIDERS
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_ANDROID_STATE_SETTLING
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_IN_CALL
from acts_contrib.test_utils.tel.tel_defines import VT_STATE_BIDIRECTIONAL
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_DISABLED
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_on_rat
from acts_contrib.test_utils.tel.tel_subscription_utils import get_incoming_message_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_message_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_from_slot_index
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_on_same_network_of_host_ad
from acts_contrib.test_utils.tel.tel_subscription_utils import set_subid_for_message
from acts_contrib.test_utils.tel.tel_test_utils import CallResult
from acts_contrib.test_utils.tel.tel_test_utils import TelResultWrapper
from acts_contrib.test_utils.tel.tel_test_utils import check_phone_number_match
from acts_contrib.test_utils.tel.tel_test_utils import get_device_epoch_time
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_voice_utils import call_setup_teardown
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call
from acts_contrib.test_utils.tel.tel_voice_utils import last_call_drop_reason
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_on_rat
from acts_contrib.test_utils.tel.tel_voice_utils import wait_and_answer_call_for_subscription
from acts_contrib.test_utils.tel.tel_voice_utils import wait_for_in_call_active
from acts_contrib.test_utils.tel.tel_voice_utils import wait_for_call_end
from acts_contrib.test_utils.tel.tel_voice_utils import wait_for_call_offhook_for_subscription
from acts_contrib.test_utils.tel.tel_video_utils import is_phone_in_call_video_bidirectional
from acts_contrib.test_utils.tel.tel_video_utils import video_call_setup_teardown
from acts_contrib.test_utils.tel.tel_video_utils import phone_idle_video
from acts_contrib.test_utils.tel.tel_wifi_utils import ensure_wifi_connected


def send_message_with_random_message_body(
    log, ad_mo, ad_mt, msg_type='sms', long_msg=False, mms_expected_result=True):
    """Test SMS/MMS between two phones.
    Returns:
        True if success.
        False if failed.
    """
    message_lengths = (50, 160, 180)

    if long_msg:
        message_lengths = (800, 1600)
        message_lengths_of_jp_carriers = (800, 1530)
        sender_message_sub_id = get_outgoing_message_sub_id(ad_mo)
        sender_mcc = ad_mo.telephony["subscription"][sender_message_sub_id]["mcc"]
        if str(sender_mcc) in ["440", "441"]:
            message_lengths = message_lengths_of_jp_carriers

    if msg_type == 'sms':
        for length in message_lengths:
            message_array = [rand_ascii_str(length)]
            if not sms_send_receive_verify(log, ad_mo, ad_mt, message_array):
                ad_mo.log.error("SMS of length %s test failed", length)
                return False
            else:
                ad_mo.log.info("SMS of length %s test succeeded", length)
        log.info("SMS test of length %s characters succeeded.",
                    message_lengths)
    elif msg_type == 'mms':
        is_roaming = False
        for ad in [ad_mo, ad_mt]:
            ad.sms_over_wifi = False
            # verizon supports sms over wifi. will add more carriers later
            for sub in ad.telephony["subscription"].values():
                if sub["operator"] in SMS_OVER_WIFI_PROVIDERS:
                    ad.sms_over_wifi = True

            if getattr(ad, 'roaming', False):
                is_roaming = True

        if is_roaming:
            # roaming device does not allow message of length 180
            message_lengths = (50, 160)

        for length in message_lengths:
            message_array = [("Test Message", rand_ascii_str(length), None)]
            result = True
            if not mms_send_receive_verify(
                    log,
                    ad_mo,
                    ad_mt,
                    message_array,
                    expected_result=mms_expected_result):

                if mms_expected_result is True:
                    if ad_mo.droid.telecomIsInCall() or ad_mt.droid.telecomIsInCall():
                        if not mms_receive_verify_after_call_hangup(
                            log, ad_mo, ad_mt, message_array):
                            result = False
                    else:
                        result = False

                if not result:
                    log.error("MMS of body length %s test failed", length)
                    return False
            else:
                log.info("MMS of body length %s test succeeded", length)
        log.info("MMS test of body lengths %s succeeded", message_lengths)
    return True

def message_test(
    log,
    ad_mo,
    ad_mt,
    mo_rat='general',
    mt_rat='general',
    msg_type='sms',
    long_msg=False,
    mms_expected_result=True,
    msg_in_call=False,
    video_or_voice='voice',
    is_airplane_mode=False,
    wfc_mode=None,
    wifi_ssid=None,
    wifi_pwd=None):

    mo_phone_setup_argv = (
        log, ad_mo, 'general', None, False, None, None, None, None, 'sms')
    mt_phone_setup_argv = (
        log, ad_mt, 'general', None, False, None, None, None, None, 'sms')
    verify_caller_func = None
    verify_callee_func = None

    if long_msg:
      for ad in [ad_mo, ad_mt]:
        ad.root_adb()
        # set max sms length to 10000 for long sms test
        ad.adb.shell("settings put global sms_outgoing_check_max_count 10000")

    if mo_rat:
        mo_phone_setup_argv = (
            log,
            ad_mo,
            mo_rat,
            None,
            is_airplane_mode,
            wfc_mode,
            wifi_ssid,
            wifi_pwd,
            None,
            'sms')
        verify_caller_func = is_phone_in_call_on_rat(
            log, ad_mo, rat=mo_rat, only_return_fn=True)

    if mt_rat:
        mt_phone_setup_argv = (
            log,
            ad_mt,
            mt_rat,
            None,
            is_airplane_mode,
            wfc_mode,
            wifi_ssid,
            wifi_pwd,
            None,
            'sms')
        verify_callee_func = is_phone_in_call_on_rat(
            log, ad_mo, rat=mt_rat, only_return_fn=True)

    tasks = [(phone_setup_on_rat, mo_phone_setup_argv),
                (phone_setup_on_rat, mt_phone_setup_argv)]
    if not multithread_func(log, tasks):
        log.error("Phone Failed to Set Up Properly.")
        return False
    time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)

    if wifi_ssid:
        if not wfc_mode or wfc_mode == WFC_MODE_DISABLED:
            tasks = [(ensure_wifi_connected, (log, ad_mo, wifi_ssid, wifi_pwd)),
                    (ensure_wifi_connected, (log, ad_mt, wifi_ssid, wifi_pwd))]
            if not multithread_func(log, tasks):
                log.error("Failed to connected to Wi-Fi.")
                return False

    if msg_in_call:
        if video_or_voice == 'voice':
            if not call_setup_teardown(
                    log,
                    ad_mo,
                    ad_mt,
                    ad_hangup=None,
                    verify_caller_func=verify_caller_func,
                    verify_callee_func=verify_callee_func):
                log.error("Failed to setup a voice call")
                return False
        elif video_or_voice == 'video':
            tasks = [
                (phone_idle_video, (log, ad_mo)),
                (phone_idle_video, (log, ad_mt))]
            if not multithread_func(log, tasks):
                log.error("Phone Failed to Set Up Properly.")
                return False
            time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
            if not video_call_setup_teardown(
                    log,
                    ad_mo,
                    ad_mt,
                    None,
                    video_state=VT_STATE_BIDIRECTIONAL,
                    verify_caller_func=is_phone_in_call_video_bidirectional,
                    verify_callee_func=is_phone_in_call_video_bidirectional):
                log.error("Failed to setup a video call")
                return False

    result = True
    if not send_message_with_random_message_body(
        log, ad_mo, ad_mt, msg_type, long_msg, mms_expected_result):
        log.error("Test failed.")
        result = False

    if msg_in_call:
        if not hangup_call(log, ad_mo):
            ad_mo.log.info("Failed to hang up call!")
            result = False

    return result

def sms_send_receive_verify(log,
                            ad_tx,
                            ad_rx,
                            array_message,
                            max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE,
                            expected_result=True,
                            slot_id_rx=None):
    """Send SMS, receive SMS, and verify content and sender's number.

        Send (several) SMS from droid_tx to droid_rx.
        Verify SMS is sent, delivered and received.
        Verify received content and sender's number are correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object
        ad_rx: Receiver's Android Device Object
        array_message: the array of message to send/receive
        slot_id_rx: the slot on the Receiver's android device (0/1)
    """
    subid_tx = get_outgoing_message_sub_id(ad_tx)
    if slot_id_rx is None:
        subid_rx = get_incoming_message_sub_id(ad_rx)
    else:
        subid_rx = get_subid_from_slot_index(log, ad_rx, slot_id_rx)

    result = sms_send_receive_verify_for_subscription(
        log, ad_tx, ad_rx, subid_tx, subid_rx, array_message, max_wait_time)
    if result != expected_result:
        log_messaging_screen_shot(ad_tx, test_name="sms_tx")
        log_messaging_screen_shot(ad_rx, test_name="sms_rx")
    return result == expected_result

def sms_send_receive_verify_for_subscription(
        log,
        ad_tx,
        ad_rx,
        subid_tx,
        subid_rx,
        array_message,
        max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE):
    """Send SMS, receive SMS, and verify content and sender's number.

        Send (several) SMS from droid_tx to droid_rx.
        Verify SMS is sent, delivered and received.
        Verify received content and sender's number are correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object..
        ad_rx: Receiver's Android Device Object.
        subid_tx: Sender's subscription ID to be used for SMS
        subid_rx: Receiver's subscription ID to be used for SMS
        array_message: the array of message to send/receive
    """
    phonenumber_tx = ad_tx.telephony['subscription'][subid_tx]['phone_num']
    phonenumber_rx = ad_rx.telephony['subscription'][subid_rx]['phone_num']

    for ad in (ad_tx, ad_rx):
        if not getattr(ad, "messaging_droid", None):
            ad.messaging_droid, ad.messaging_ed = ad.get_droid()
            ad.messaging_ed.start()
        else:
            try:
                if not ad.messaging_droid.is_live:
                    ad.messaging_droid, ad.messaging_ed = ad.get_droid()
                    ad.messaging_ed.start()
                else:
                    ad.messaging_ed.clear_all_events()
                ad.messaging_droid.logI(
                    "Start sms_send_receive_verify_for_subscription test")
            except Exception:
                ad.log.info("Create new sl4a session for messaging")
                ad.messaging_droid, ad.messaging_ed = ad.get_droid()
                ad.messaging_ed.start()

    for text in array_message:
        length = len(text)
        ad_tx.log.info("Sending SMS from %s to %s, len: %s, content: %s.",
                       phonenumber_tx, phonenumber_rx, length, text)
        try:
            ad_rx.messaging_ed.clear_events(EventSmsReceived)
            ad_tx.messaging_ed.clear_events(EventSmsSentSuccess)
            ad_tx.messaging_ed.clear_events(EventSmsSentFailure)
            ad_rx.messaging_droid.smsStartTrackingIncomingSmsMessage()
            time.sleep(1)  #sleep 100ms after starting event tracking
            ad_tx.messaging_droid.logI("Sending SMS of length %s" % length)
            ad_rx.messaging_droid.logI("Expecting SMS of length %s" % length)
            ad_tx.messaging_droid.smsSendTextMessage(phonenumber_rx, text,
                                                     True)
            try:
                events = ad_tx.messaging_ed.pop_events(
                    "(%s|%s|%s|%s)" %
                    (EventSmsSentSuccess, EventSmsSentFailure,
                     EventSmsDeliverSuccess,
                     EventSmsDeliverFailure), max_wait_time)
                for event in events:
                    ad_tx.log.info("Got event %s", event["name"])
                    if event["name"] == EventSmsSentFailure or event["name"] == EventSmsDeliverFailure:
                        if event.get("data") and event["data"].get("Reason"):
                            ad_tx.log.error("%s with reason: %s",
                                            event["name"],
                                            event["data"]["Reason"])
                        return False
                    elif event["name"] == EventSmsSentSuccess or event["name"] == EventSmsDeliverSuccess:
                        break
            except Empty:
                ad_tx.log.error("No %s or %s event for SMS of length %s.",
                                EventSmsSentSuccess, EventSmsSentFailure,
                                length)
                return False

            if not wait_for_matching_sms(
                    log,
                    ad_rx,
                    phonenumber_tx,
                    text,
                    max_wait_time,
                    allow_multi_part_long_sms=True):
                ad_rx.log.error("No matching received SMS of length %s.",
                                length)
                return False
        except Exception as e:
            log.error("Exception error %s", e)
            raise
        finally:
            ad_rx.messaging_droid.smsStopTrackingIncomingSmsMessage()
    return True

def sms_in_collision_send_receive_verify(
        log,
        ad_rx,
        ad_rx2,
        ad_tx,
        ad_tx2,
        array_message,
        array_message2,
        max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE_IN_COLLISION):
    """Send 2 SMS', receive both SMS', and verify content and sender's number of
       each SMS.

        Send 2 SMS'. One from ad_tx to ad_rx and the other from ad_tx2 to ad_rx2.
        When ad_rx is identical to ad_rx2, the scenario of SMS' in collision can
        be tested.
        Verify both SMS' are sent, delivered and received.
        Verify received content and sender's number of each SMS is correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object..
        ad_rx: Receiver's Android Device Object.
        ad_tx2: 2nd sender's Android Device Object..
        ad_rx2: 2nd receiver's Android Device Object.
        array_message: the array of message to send/receive from ad_tx to ad_rx
        array_message2: the array of message to send/receive from ad_tx2 to
        ad_rx2
        max_wait_time: Max time to wait for reception of SMS
    """
    rx_sub_id = get_outgoing_message_sub_id(ad_rx)
    rx2_sub_id = get_outgoing_message_sub_id(ad_rx2)

    _, tx_sub_id, _ = get_subid_on_same_network_of_host_ad(
        [ad_rx, ad_tx, ad_tx2],
        host_sub_id=rx_sub_id)
    set_subid_for_message(ad_tx, tx_sub_id)

    _, _, tx2_sub_id = get_subid_on_same_network_of_host_ad(
        [ad_rx2, ad_tx, ad_tx2],
        host_sub_id=rx2_sub_id)
    set_subid_for_message(ad_tx2, tx2_sub_id)

    if not sms_in_collision_send_receive_verify_for_subscription(
        log,
        ad_tx,
        ad_tx2,
        ad_rx,
        ad_rx2,
        tx_sub_id,
        tx2_sub_id,
        rx_sub_id,
        rx_sub_id,
        array_message,
        array_message2,
        max_wait_time):
        log_messaging_screen_shot(
            ad_rx, test_name="sms rx subid: %s" % rx_sub_id)
        log_messaging_screen_shot(
            ad_rx2, test_name="sms rx2 subid: %s" % rx2_sub_id)
        log_messaging_screen_shot(
            ad_tx, test_name="sms tx subid: %s" % tx_sub_id)
        log_messaging_screen_shot(
            ad_tx2, test_name="sms tx subid: %s" % tx2_sub_id)
        return False
    return True

def sms_in_collision_send_receive_verify_for_subscription(
        log,
        ad_tx,
        ad_tx2,
        ad_rx,
        ad_rx2,
        subid_tx,
        subid_tx2,
        subid_rx,
        subid_rx2,
        array_message,
        array_message2,
        max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE_IN_COLLISION):
    """Send 2 SMS', receive both SMS', and verify content and sender's number of
       each SMS.

        Send 2 SMS'. One from ad_tx to ad_rx and the other from ad_tx2 to ad_rx2.
        When ad_rx is identical to ad_rx2, the scenario of SMS' in collision can
        be tested.
        Verify both SMS' are sent, delivered and received.
        Verify received content and sender's number of each SMS is correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object..
        ad_rx: Receiver's Android Device Object.
        ad_tx2: 2nd sender's Android Device Object..
        ad_rx2: 2nd receiver's Android Device Object.
        subid_tx: Sub ID of ad_tx as default Sub ID for outgoing SMS
        subid_tx2: Sub ID of ad_tx2 as default Sub ID for outgoing SMS
        subid_rx: Sub ID of ad_rx as default Sub ID for incoming SMS
        subid_rx2: Sub ID of ad_rx2 as default Sub ID for incoming SMS
        array_message: the array of message to send/receive from ad_tx to ad_rx
        array_message2: the array of message to send/receive from ad_tx2 to
        ad_rx2
        max_wait_time: Max time to wait for reception of SMS
    """

    phonenumber_tx = ad_tx.telephony['subscription'][subid_tx]['phone_num']
    phonenumber_tx2 = ad_tx2.telephony['subscription'][subid_tx2]['phone_num']
    phonenumber_rx = ad_rx.telephony['subscription'][subid_rx]['phone_num']
    phonenumber_rx2 = ad_rx2.telephony['subscription'][subid_rx2]['phone_num']

    for ad in (ad_tx, ad_tx2, ad_rx, ad_rx2):
        ad.send_keycode("BACK")
        if not getattr(ad, "messaging_droid", None):
            ad.messaging_droid, ad.messaging_ed = ad.get_droid()
            ad.messaging_ed.start()
        else:
            try:
                if not ad.messaging_droid.is_live:
                    ad.messaging_droid, ad.messaging_ed = ad.get_droid()
                    ad.messaging_ed.start()
                else:
                    ad.messaging_ed.clear_all_events()
                ad.messaging_droid.logI(
                    "Start sms_send_receive_verify_for_subscription test")
            except Exception:
                ad.log.info("Create new sl4a session for messaging")
                ad.messaging_droid, ad.messaging_ed = ad.get_droid()
                ad.messaging_ed.start()

    for text, text2 in zip(array_message, array_message2):
        length = len(text)
        length2 = len(text2)
        ad_tx.log.info("Sending SMS from %s to %s, len: %s, content: %s.",
                       phonenumber_tx, phonenumber_rx, length, text)
        ad_tx2.log.info("Sending SMS from %s to %s, len: %s, content: %s.",
                       phonenumber_tx2, phonenumber_rx2, length2, text2)

        try:
            ad_rx.messaging_ed.clear_events(EventSmsReceived)
            ad_rx2.messaging_ed.clear_events(EventSmsReceived)
            ad_tx.messaging_ed.clear_events(EventSmsSentSuccess)
            ad_tx.messaging_ed.clear_events(EventSmsSentFailure)
            ad_tx2.messaging_ed.clear_events(EventSmsSentSuccess)
            ad_tx2.messaging_ed.clear_events(EventSmsSentFailure)
            ad_rx.messaging_droid.smsStartTrackingIncomingSmsMessage()
            if ad_rx2 != ad_rx:
                ad_rx2.messaging_droid.smsStartTrackingIncomingSmsMessage()
            time.sleep(1)
            ad_tx.messaging_droid.logI("Sending SMS of length %s" % length)
            ad_tx2.messaging_droid.logI("Sending SMS of length %s" % length2)
            ad_rx.messaging_droid.logI(
                "Expecting SMS of length %s from %s" % (length, ad_tx.serial))
            ad_rx2.messaging_droid.logI(
                "Expecting SMS of length %s from %s" % (length2, ad_tx2.serial))

            tasks = [
                (ad_tx.messaging_droid.smsSendTextMessage,
                (phonenumber_rx, text, True)),
                (ad_tx2.messaging_droid.smsSendTextMessage,
                (phonenumber_rx2, text2, True))]
            multithread_func(log, tasks)
            try:
                tasks = [
                    (ad_tx.messaging_ed.pop_events, ("(%s|%s|%s|%s)" % (
                        EventSmsSentSuccess,
                        EventSmsSentFailure,
                        EventSmsDeliverSuccess,
                        EventSmsDeliverFailure), max_wait_time)),
                    (ad_tx2.messaging_ed.pop_events, ("(%s|%s|%s|%s)" % (
                        EventSmsSentSuccess,
                        EventSmsSentFailure,
                        EventSmsDeliverSuccess,
                        EventSmsDeliverFailure), max_wait_time))
                ]
                results = run_multithread_func(log, tasks)
                res = True
                _ad = ad_tx
                for ad, events in [(ad_tx, results[0]),(ad_tx2, results[1])]:
                    _ad = ad
                    for event in events:
                        ad.log.info("Got event %s", event["name"])
                        if event["name"] == EventSmsSentFailure or \
                            event["name"] == EventSmsDeliverFailure:
                            if event.get("data") and event["data"].get("Reason"):
                                ad.log.error("%s with reason: %s",
                                                event["name"],
                                                event["data"]["Reason"])
                            res = False
                        elif event["name"] == EventSmsSentSuccess or \
                            event["name"] == EventSmsDeliverSuccess:
                            break
                if not res:
                    return False
            except Empty:
                _ad.log.error("No %s or %s event for SMS of length %s.",
                                EventSmsSentSuccess, EventSmsSentFailure,
                                length)
                return False
            if ad_rx == ad_rx2:
                if not wait_for_matching_mt_sms_in_collision(
                    log,
                    ad_rx,
                    phonenumber_tx,
                    phonenumber_tx2,
                    text,
                    text2,
                    max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE_IN_COLLISION):

                    ad_rx.log.error(
                        "No matching received SMS of length %s from %s.",
                        length,
                        ad_rx.serial)
                    return False
            else:
                if not wait_for_matching_mt_sms_in_collision_with_mo_sms(
                    log,
                    ad_rx,
                    ad_rx2,
                    phonenumber_tx,
                    phonenumber_tx2,
                    text,
                    text2,
                    max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE_IN_COLLISION):
                    return False
        except Exception as e:
            log.error("Exception error %s", e)
            raise
        finally:
            ad_rx.messaging_droid.smsStopTrackingIncomingSmsMessage()
            ad_rx2.messaging_droid.smsStopTrackingIncomingSmsMessage()
    return True

def sms_rx_power_off_multiple_send_receive_verify(
        log,
        ad_rx,
        ad_tx,
        ad_tx2,
        array_message_length,
        array_message2_length,
        num_array_message,
        num_array_message2,
        max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE_IN_COLLISION):

    rx_sub_id = get_outgoing_message_sub_id(ad_rx)

    _, tx_sub_id, _ = get_subid_on_same_network_of_host_ad(
        [ad_rx, ad_tx, ad_tx2],
        host_sub_id=rx_sub_id)
    set_subid_for_message(ad_tx, tx_sub_id)

    _, _, tx2_sub_id = get_subid_on_same_network_of_host_ad(
        [ad_rx, ad_tx, ad_tx2],
        host_sub_id=rx_sub_id)
    set_subid_for_message(ad_tx2, tx2_sub_id)

    if not sms_rx_power_off_multiple_send_receive_verify_for_subscription(
        log,
        ad_tx,
        ad_tx2,
        ad_rx,
        tx_sub_id,
        tx2_sub_id,
        rx_sub_id,
        rx_sub_id,
        array_message_length,
        array_message2_length,
        num_array_message,
        num_array_message2):
        log_messaging_screen_shot(
            ad_rx, test_name="sms rx subid: %s" % rx_sub_id)
        log_messaging_screen_shot(
            ad_tx, test_name="sms tx subid: %s" % tx_sub_id)
        log_messaging_screen_shot(
            ad_tx2, test_name="sms tx subid: %s" % tx2_sub_id)
        return False
    return True

def sms_rx_power_off_multiple_send_receive_verify_for_subscription(
        log,
        ad_tx,
        ad_tx2,
        ad_rx,
        subid_tx,
        subid_tx2,
        subid_rx,
        subid_rx2,
        array_message_length,
        array_message2_length,
        num_array_message,
        num_array_message2,
        max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE_IN_COLLISION):

    phonenumber_tx = ad_tx.telephony['subscription'][subid_tx]['phone_num']
    phonenumber_tx2 = ad_tx2.telephony['subscription'][subid_tx2]['phone_num']
    phonenumber_rx = ad_rx.telephony['subscription'][subid_rx]['phone_num']
    phonenumber_rx2 = ad_rx.telephony['subscription'][subid_rx2]['phone_num']

    if not toggle_airplane_mode(log, ad_rx, True):
        ad_rx.log.error("Failed to enable Airplane Mode")
        return False
    ad_rx.stop_services()
    ad_rx.log.info("Rebooting......")
    ad_rx.adb.reboot()

    message_dict = {phonenumber_tx: [], phonenumber_tx2: []}
    for index in range(max(num_array_message, num_array_message2)):
        array_message = [rand_ascii_str(array_message_length)]
        array_message2 = [rand_ascii_str(array_message2_length)]
        for text, text2 in zip(array_message, array_message2):
            message_dict[phonenumber_tx].append(text)
            message_dict[phonenumber_tx2].append(text2)
            length = len(text)
            length2 = len(text2)

            ad_tx.log.info("Sending SMS from %s to %s, len: %s, content: %s.",
                           phonenumber_tx, phonenumber_rx, length, text)
            ad_tx2.log.info("Sending SMS from %s to %s, len: %s, content: %s.",
                           phonenumber_tx2, phonenumber_rx2, length2, text2)

            try:
                for ad in (ad_tx, ad_tx2):
                    ad.send_keycode("BACK")
                    if not getattr(ad, "messaging_droid", None):
                        ad.messaging_droid, ad.messaging_ed = ad.get_droid()
                        ad.messaging_ed.start()
                    else:
                        try:
                            if not ad.messaging_droid.is_live:
                                ad.messaging_droid, ad.messaging_ed = \
                                    ad.get_droid()
                                ad.messaging_ed.start()
                            else:
                                ad.messaging_ed.clear_all_events()
                            ad.messaging_droid.logI(
                                "Start sms_send_receive_verify_for_subscription"
                                " test")
                        except Exception:
                            ad.log.info("Create new sl4a session for messaging")
                            ad.messaging_droid, ad.messaging_ed = ad.get_droid()
                            ad.messaging_ed.start()

                ad_tx.messaging_ed.clear_events(EventSmsSentSuccess)
                ad_tx.messaging_ed.clear_events(EventSmsSentFailure)
                ad_tx2.messaging_ed.clear_events(EventSmsSentSuccess)
                ad_tx2.messaging_ed.clear_events(EventSmsSentFailure)

                if index < num_array_message and index < num_array_message2:
                    ad_tx.messaging_droid.logI(
                        "Sending SMS of length %s" % length)
                    ad_tx2.messaging_droid.logI(
                        "Sending SMS of length %s" % length2)
                    tasks = [
                        (ad_tx.messaging_droid.smsSendTextMessage,
                        (phonenumber_rx, text, True)),
                        (ad_tx2.messaging_droid.smsSendTextMessage,
                        (phonenumber_rx2, text2, True))]
                    multithread_func(log, tasks)
                else:
                    if index < num_array_message:
                        ad_tx.messaging_droid.logI(
                            "Sending SMS of length %s" % length)
                        ad_tx.messaging_droid.smsSendTextMessage(
                            phonenumber_rx, text, True)
                    if index < num_array_message2:
                        ad_tx2.messaging_droid.logI(
                            "Sending SMS of length %s" % length2)
                        ad_tx2.messaging_droid.smsSendTextMessage(
                            phonenumber_rx2, text2, True)

                try:
                    if index < num_array_message and index < num_array_message2:
                        tasks = [
                            (ad_tx.messaging_ed.pop_events, ("(%s|%s|%s|%s)" % (
                                EventSmsSentSuccess,
                                EventSmsSentFailure,
                                EventSmsDeliverSuccess,
                                EventSmsDeliverFailure),
                                max_wait_time)),
                            (ad_tx2.messaging_ed.pop_events, ("(%s|%s|%s|%s)" % (
                                EventSmsSentSuccess,
                                EventSmsSentFailure,
                                EventSmsDeliverSuccess,
                                EventSmsDeliverFailure),
                                max_wait_time))
                        ]
                        results = run_multithread_func(log, tasks)
                        res = True
                        _ad = ad_tx
                        for ad, events in [
                            (ad_tx, results[0]), (ad_tx2, results[1])]:
                            _ad = ad
                            for event in events:
                                ad.log.info("Got event %s", event["name"])
                                if event["name"] == EventSmsSentFailure or \
                                    event["name"] == EventSmsDeliverFailure:
                                    if event.get("data") and \
                                        event["data"].get("Reason"):
                                        ad.log.error("%s with reason: %s",
                                                        event["name"],
                                                        event["data"]["Reason"])
                                    res = False
                                elif event["name"] == EventSmsSentSuccess or \
                                    event["name"] == EventSmsDeliverSuccess:
                                    break
                        if not res:
                            return False
                    else:
                        if index < num_array_message:
                            result = ad_tx.messaging_ed.pop_events(
                                "(%s|%s|%s|%s)" % (
                                    EventSmsSentSuccess,
                                    EventSmsSentFailure,
                                    EventSmsDeliverSuccess,
                                    EventSmsDeliverFailure),
                                max_wait_time)
                            res = True
                            _ad = ad_tx
                            for ad, events in [(ad_tx, result)]:
                                _ad = ad
                                for event in events:
                                    ad.log.info("Got event %s", event["name"])
                                    if event["name"] == EventSmsSentFailure or \
                                        event["name"] == EventSmsDeliverFailure:
                                        if event.get("data") and \
                                            event["data"].get("Reason"):
                                            ad.log.error(
                                                "%s with reason: %s",
                                                event["name"],
                                                event["data"]["Reason"])
                                        res = False
                                    elif event["name"] == EventSmsSentSuccess \
                                        or event["name"] == EventSmsDeliverSuccess:
                                        break
                            if not res:
                                return False
                        if index < num_array_message2:
                            result = ad_tx2.messaging_ed.pop_events(
                                "(%s|%s|%s|%s)" % (
                                    EventSmsSentSuccess,
                                    EventSmsSentFailure,
                                    EventSmsDeliverSuccess,
                                    EventSmsDeliverFailure),
                                max_wait_time)
                            res = True
                            _ad = ad_tx2
                            for ad, events in [(ad_tx2, result)]:
                                _ad = ad
                                for event in events:
                                    ad.log.info("Got event %s", event["name"])
                                    if event["name"] == EventSmsSentFailure or \
                                        event["name"] == EventSmsDeliverFailure:
                                        if event.get("data") and \
                                            event["data"].get("Reason"):
                                            ad.log.error(
                                                "%s with reason: %s",
                                                event["name"],
                                                event["data"]["Reason"])
                                        res = False
                                    elif event["name"] == EventSmsSentSuccess \
                                        or event["name"] == EventSmsDeliverSuccess:
                                        break
                            if not res:
                                return False


                except Empty:
                    _ad.log.error("No %s or %s event for SMS of length %s.",
                                    EventSmsSentSuccess, EventSmsSentFailure,
                                    length)
                    return False

            except Exception as e:
                log.error("Exception error %s", e)
                raise

    ad_rx.wait_for_boot_completion()
    ad_rx.root_adb()
    ad_rx.start_services(skip_setup_wizard=False)

    output = ad_rx.adb.logcat("-t 1")
    match = re.search(r"\d+-\d+\s\d+:\d+:\d+.\d+", output)
    if match:
        ad_rx.test_log_begin_time = match.group(0)

    ad_rx.messaging_droid, ad_rx.messaging_ed = ad_rx.get_droid()
    ad_rx.messaging_ed.start()
    ad_rx.messaging_droid.smsStartTrackingIncomingSmsMessage()
    time.sleep(1)  #sleep 100ms after starting event tracking

    if not toggle_airplane_mode(log, ad_rx, False):
        ad_rx.log.error("Failed to disable Airplane Mode")
        return False

    res = True
    try:
        if not wait_for_matching_multiple_sms(log,
                ad_rx,
                phonenumber_tx,
                phonenumber_tx2,
                messages=message_dict,
                max_wait_time=max_wait_time):
            res =  False
    except Exception as e:
        log.error("Exception error %s", e)
        raise
    finally:
        ad_rx.messaging_droid.smsStopTrackingIncomingSmsMessage()

    return res

def is_sms_match(event, phonenumber_tx, text):
    """Return True if 'text' equals to event['data']['Text']
        and phone number match.

    Args:
        event: Event object to verify.
        phonenumber_tx: phone number for sender.
        text: text string to verify.

    Returns:
        Return True if 'text' equals to event['data']['Text']
            and phone number match.
    """
    return (check_phone_number_match(event['data']['Sender'], phonenumber_tx)
            and event['data']['Text'].strip() == text)

def is_sms_partial_match(event, phonenumber_tx, text):
    """Return True if 'text' starts with event['data']['Text']
        and phone number match.

    Args:
        event: Event object to verify.
        phonenumber_tx: phone number for sender.
        text: text string to verify.

    Returns:
        Return True if 'text' starts with event['data']['Text']
            and phone number match.
    """
    event_text = event['data']['Text'].strip()
    if event_text.startswith("("):
        event_text = event_text.split(")")[-1]
    return (check_phone_number_match(event['data']['Sender'], phonenumber_tx)
            and text.startswith(event_text))

def is_sms_in_collision_match(
    event, phonenumber_tx, phonenumber_tx2, text, text2):
    event_text = event['data']['Text'].strip()
    if event_text.startswith("("):
        event_text = event_text.split(")")[-1]

    for phonenumber, txt in [[phonenumber_tx, text], [phonenumber_tx2, text2]]:
        if check_phone_number_match(
            event['data']['Sender'], phonenumber) and txt.startswith(event_text):
            return True
    return False

def is_sms_in_collision_partial_match(
    event, phonenumber_tx, phonenumber_tx2, text, text2):
    for phonenumber, txt in [[phonenumber_tx, text], [phonenumber_tx2, text2]]:
        if check_phone_number_match(
            event['data']['Sender'], phonenumber) and \
                event['data']['Text'].strip() == txt:
            return True
    return False

def is_sms_match_among_multiple_sms(
    event, phonenumber_tx, phonenumber_tx2, texts=[], texts2=[]):
    for txt in texts:
        if check_phone_number_match(
            event['data']['Sender'], phonenumber_tx) and \
                event['data']['Text'].strip() == txt:
                return True

    for txt in texts2:
        if check_phone_number_match(
            event['data']['Sender'], phonenumber_tx2) and \
                event['data']['Text'].strip() == txt:
                return True

    return False

def is_sms_partial_match_among_multiple_sms(
    event, phonenumber_tx, phonenumber_tx2, texts=[], texts2=[]):
    event_text = event['data']['Text'].strip()
    if event_text.startswith("("):
        event_text = event_text.split(")")[-1]

    for txt in texts:
        if check_phone_number_match(
            event['data']['Sender'], phonenumber_tx) and \
                txt.startswith(event_text):
                return True

    for txt in texts2:
        if check_phone_number_match(
            event['data']['Sender'], phonenumber_tx2) and \
                txt.startswith(event_text):
                return True

    return False

def wait_for_matching_sms(log,
                          ad_rx,
                          phonenumber_tx,
                          text,
                          max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE,
                          allow_multi_part_long_sms=True):
    """Wait for matching incoming SMS.

    Args:
        log: Log object.
        ad_rx: Receiver's Android Device Object
        phonenumber_tx: Sender's phone number.
        text: SMS content string.
        allow_multi_part_long_sms: is long SMS allowed to be received as
            multiple short SMS. This is optional, default value is True.

    Returns:
        True if matching incoming SMS is received.
    """
    if not allow_multi_part_long_sms:
        try:
            ad_rx.messaging_ed.wait_for_event(EventSmsReceived, is_sms_match,
                                              max_wait_time, phonenumber_tx,
                                              text)
            ad_rx.log.info("Got event %s", EventSmsReceived)
            return True
        except Empty:
            ad_rx.log.error("No matched SMS received event.")
            return False
    else:
        try:
            received_sms = ''
            remaining_text = text
            while (remaining_text != ''):
                event = ad_rx.messaging_ed.wait_for_event(
                    EventSmsReceived, is_sms_partial_match, max_wait_time,
                    phonenumber_tx, remaining_text)
                event_text = event['data']['Text'].split(")")[-1].strip()
                event_text_length = len(event_text)
                ad_rx.log.info("Got event %s of text length %s from %s",
                               EventSmsReceived, event_text_length,
                               phonenumber_tx)
                remaining_text = remaining_text[event_text_length:]
                received_sms += event_text
            ad_rx.log.info("Received SMS of length %s", len(received_sms))
            return True
        except Empty:
            ad_rx.log.error(
                "Missing SMS received event of text length %s from %s",
                len(remaining_text), phonenumber_tx)
            if received_sms != '':
                ad_rx.log.error(
                    "Only received partial matched SMS of length %s",
                    len(received_sms))
            return False

def wait_for_matching_mt_sms_in_collision(log,
                          ad_rx,
                          phonenumber_tx,
                          phonenumber_tx2,
                          text,
                          text2,
                          allow_multi_part_long_sms=True,
                          max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE_IN_COLLISION):

    if not allow_multi_part_long_sms:
        try:
            ad_rx.messaging_ed.wait_for_event(
                EventSmsReceived,
                is_sms_in_collision_match,
                max_wait_time,
                phonenumber_tx,
                phonenumber_tx2,
                text,
                text2)
            ad_rx.log.info("Got event %s", EventSmsReceived)
            return True
        except Empty:
            ad_rx.log.error("No matched SMS received event.")
            return False
    else:
        try:
            received_sms = ''
            received_sms2 = ''
            remaining_text = text
            remaining_text2 = text2
            while (remaining_text != '' or remaining_text2 != ''):
                event = ad_rx.messaging_ed.wait_for_event(
                    EventSmsReceived,
                    is_sms_in_collision_partial_match,
                    max_wait_time,
                    phonenumber_tx,
                    phonenumber_tx2,
                    remaining_text,
                    remaining_text2)
                event_text = event['data']['Text'].split(")")[-1].strip()
                event_text_length = len(event_text)

                if event_text in remaining_text:
                    ad_rx.log.info("Got event %s of text length %s from %s",
                                   EventSmsReceived, event_text_length,
                                   phonenumber_tx)
                    remaining_text = remaining_text[event_text_length:]
                    received_sms += event_text
                elif event_text in remaining_text2:
                    ad_rx.log.info("Got event %s of text length %s from %s",
                                   EventSmsReceived, event_text_length,
                                   phonenumber_tx2)
                    remaining_text2 = remaining_text2[event_text_length:]
                    received_sms2 += event_text

            ad_rx.log.info("Received SMS of length %s", len(received_sms))
            ad_rx.log.info("Received SMS of length %s", len(received_sms2))
            return True
        except Empty:
            ad_rx.log.error(
                "Missing SMS received event.")
            if received_sms != '':
                ad_rx.log.error(
                    "Only received partial matched SMS of length %s from %s",
                    len(received_sms), phonenumber_tx)
            if received_sms2 != '':
                ad_rx.log.error(
                    "Only received partial matched SMS of length %s from %s",
                    len(received_sms2), phonenumber_tx2)
            return False

def wait_for_matching_mt_sms_in_collision_with_mo_sms(log,
                          ad_rx,
                          ad_rx2,
                          phonenumber_tx,
                          phonenumber_tx2,
                          text,
                          text2,
                          allow_multi_part_long_sms=True,
                          max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE_IN_COLLISION):

    if not allow_multi_part_long_sms:
        result = True
        try:
            ad_rx.messaging_ed.wait_for_call_offhook_event(
                EventSmsReceived,
                is_sms_match,
                max_wait_time,
                phonenumber_tx,
                text)
            ad_rx.log.info("Got event %s", EventSmsReceived)
        except Empty:
            ad_rx.log.error("No matched SMS received event.")
            result = False

        try:
            ad_rx2.messaging_ed.wait_for_call_offhook_event(
                EventSmsReceived,
                is_sms_match,
                max_wait_time,
                phonenumber_tx2,
                text2)
            ad_rx2.log.info("Got event %s", EventSmsReceived)
        except Empty:
            ad_rx2.log.error("No matched SMS received event.")
            result = False

        return result
    else:
        result = True
        try:
            received_sms = ''
            remaining_text = text
            while remaining_text != '':
                event = ad_rx.messaging_ed.wait_for_event(
                    EventSmsReceived, is_sms_partial_match, max_wait_time,
                    phonenumber_tx, remaining_text)
                event_text = event['data']['Text'].split(")")[-1].strip()
                event_text_length = len(event_text)

                if event_text in remaining_text:
                    ad_rx.log.info("Got event %s of text length %s from %s",
                                   EventSmsReceived, event_text_length,
                                   phonenumber_tx)
                    remaining_text = remaining_text[event_text_length:]
                    received_sms += event_text

            ad_rx.log.info("Received SMS of length %s", len(received_sms))
        except Empty:
            ad_rx.log.error(
                "Missing SMS received event.")
            if received_sms != '':
                ad_rx.log.error(
                    "Only received partial matched SMS of length %s from %s",
                    len(received_sms), phonenumber_tx)
            result = False

        try:
            received_sms2 = ''
            remaining_text2 = text2
            while remaining_text2 != '':
                event2 = ad_rx2.messaging_ed.wait_for_event(
                    EventSmsReceived, is_sms_partial_match, max_wait_time,
                    phonenumber_tx2, remaining_text2)
                event_text2 = event2['data']['Text'].split(")")[-1].strip()
                event_text_length2 = len(event_text2)

                if event_text2 in remaining_text2:
                    ad_rx2.log.info("Got event %s of text length %s from %s",
                                   EventSmsReceived, event_text_length2,
                                   phonenumber_tx2)
                    remaining_text2 = remaining_text2[event_text_length2:]
                    received_sms2 += event_text2

            ad_rx2.log.info("Received SMS of length %s", len(received_sms2))
        except Empty:
            ad_rx2.log.error(
                "Missing SMS received event.")
            if received_sms2 != '':
                ad_rx2.log.error(
                    "Only received partial matched SMS of length %s from %s",
                    len(received_sms2), phonenumber_tx2)
            result = False

        return result

def wait_for_matching_multiple_sms(log,
                        ad_rx,
                        phonenumber_tx,
                        phonenumber_tx2,
                        messages={},
                        allow_multi_part_long_sms=True,
                        max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE):

    if not allow_multi_part_long_sms:
        try:
            ad_rx.messaging_ed.wait_for_event(
                EventSmsReceived,
                is_sms_match_among_multiple_sms,
                max_wait_time,
                phonenumber_tx,
                phonenumber_tx2,
                messages[phonenumber_tx],
                messages[phonenumber_tx2])
            ad_rx.log.info("Got event %s", EventSmsReceived)
            return True
        except Empty:
            ad_rx.log.error("No matched SMS received event.")
            return False
    else:
        all_msgs = []
        for tx, msgs in messages.items():
            for msg in msgs:
                all_msgs.append([tx, msg, msg, ''])

        all_msgs_copy = all_msgs.copy()

        try:
            while (all_msgs != []):
                event = ad_rx.messaging_ed.wait_for_event(
                    EventSmsReceived,
                    is_sms_partial_match_among_multiple_sms,
                    max_wait_time,
                    phonenumber_tx,
                    phonenumber_tx2,
                    messages[phonenumber_tx],
                    messages[phonenumber_tx2])
                event_text = event['data']['Text'].split(")")[-1].strip()
                event_text_length = len(event_text)

                for msg in all_msgs_copy:
                    if event_text in msg[2]:
                        ad_rx.log.info("Got event %s of text length %s from %s",
                                       EventSmsReceived, event_text_length,
                                       msg[0])
                        msg[2] = msg[2][event_text_length:]
                        msg[3] += event_text

                        if msg[2] == "":
                            all_msgs.remove(msg)

            ad_rx.log.info("Received all SMS' sent when power-off.")
        except Empty:
            ad_rx.log.error(
                "Missing SMS received event.")

            for msg in all_msgs_copy:
                if msg[3] != '':
                    ad_rx.log.error(
                        "Only received partial matched SMS of length %s from %s",
                        len(msg[3]), msg[0])
            return False

        return True

def wait_for_sending_sms(ad_tx, max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE):
    try:
        events = ad_tx.messaging_ed.pop_events(
            "(%s|%s|%s|%s)" %
            (EventSmsSentSuccess, EventSmsSentFailure,
                EventSmsDeliverSuccess,
                EventSmsDeliverFailure), max_wait_time)
        for event in events:
            ad_tx.log.info("Got event %s", event["name"])
            if event["name"] == EventSmsSentFailure or \
                event["name"] == EventSmsDeliverFailure:
                if event.get("data") and event["data"].get("Reason"):
                    ad_tx.log.error("%s with reason: %s",
                                    event["name"],
                                    event["data"]["Reason"])
                return False
            elif event["name"] == EventSmsSentSuccess or \
                event["name"] == EventSmsDeliverSuccess:
                return True
    except Empty:
        ad_tx.log.error("No %s or %s event for SMS.",
                        EventSmsSentSuccess, EventSmsSentFailure)
        return False

def voice_call_in_collision_with_mt_sms_msim(
        log,
        ad_primary,
        ad_sms,
        ad_voice,
        sms_subid_ad_primary,
        sms_subid_ad_sms,
        voice_subid_ad_primary,
        voice_subid_ad_voice,
        array_message,
        ad_hangup=None,
        verify_caller_func=None,
        verify_callee_func=None,
        call_direction="mo",
        wait_time_in_call=WAIT_TIME_IN_CALL,
        incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND,
        dialing_number_length=None,
        video_state=None):

    ad_tx = ad_sms
    ad_rx = ad_primary
    subid_tx = sms_subid_ad_sms
    subid_rx = sms_subid_ad_primary

    if call_direction == "mo":
        ad_caller = ad_primary
        ad_callee = ad_voice
        subid_caller = voice_subid_ad_primary
        subid_callee = voice_subid_ad_voice
    elif call_direction == "mt":
        ad_callee = ad_primary
        ad_caller = ad_voice
        subid_callee = voice_subid_ad_primary
        subid_caller = voice_subid_ad_voice


    phonenumber_tx = ad_tx.telephony['subscription'][subid_tx]['phone_num']
    phonenumber_rx = ad_rx.telephony['subscription'][subid_rx]['phone_num']

    tel_result_wrapper = TelResultWrapper(CallResult('SUCCESS'))

    for ad in (ad_tx, ad_rx):
        ad.send_keycode("BACK")
        if not getattr(ad, "messaging_droid", None):
            ad.messaging_droid, ad.messaging_ed = ad.get_droid()
            ad.messaging_ed.start()
        else:
            try:
                if not ad.messaging_droid.is_live:
                    ad.messaging_droid, ad.messaging_ed = ad.get_droid()
                    ad.messaging_ed.start()
                else:
                    ad.messaging_ed.clear_all_events()
            except Exception:
                ad.log.info("Create new sl4a session for messaging")
                ad.messaging_droid, ad.messaging_ed = ad.get_droid()
                ad.messaging_ed.start()

    if not verify_caller_func:
        verify_caller_func = is_phone_in_call
    if not verify_callee_func:
        verify_callee_func = is_phone_in_call

    caller_number = ad_caller.telephony['subscription'][subid_caller][
        'phone_num']
    callee_number = ad_callee.telephony['subscription'][subid_callee][
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

    ad_caller.ed.clear_events(EventCallStateChanged)
    call_begin_time = get_device_epoch_time(ad)
    ad_caller.droid.telephonyStartTrackingCallStateForSubscription(subid_caller)

    for text in array_message:
        length = len(text)
        ad_tx.log.info("Sending SMS from %s to %s, len: %s, content: %s.",
                       phonenumber_tx, phonenumber_rx, length, text)
        try:
            ad_rx.messaging_ed.clear_events(EventSmsReceived)
            ad_tx.messaging_ed.clear_events(EventSmsSentSuccess)
            ad_tx.messaging_ed.clear_events(EventSmsSentFailure)
            ad_rx.messaging_droid.smsStartTrackingIncomingSmsMessage()
            time.sleep(1)  #sleep 100ms after starting event tracking
            ad_tx.messaging_droid.logI("Sending SMS of length %s" % length)
            ad_rx.messaging_droid.logI("Expecting SMS of length %s" % length)
            ad_caller.log.info("Make a phone call to %s", callee_number)

            tasks = [
                (ad_tx.messaging_droid.smsSendTextMessage,
                (phonenumber_rx, text, True)),
                (ad_caller.droid.telecomCallNumber,
                (callee_number, video))]

            run_multithread_func(log, tasks)

            try:
                # Verify OFFHOOK state
                if not wait_for_call_offhook_for_subscription(
                        log,
                        ad_caller,
                        subid_caller,
                        event_tracking_started=True):
                    ad_caller.log.info(
                        "sub_id %s not in call offhook state", subid_caller)
                    last_call_drop_reason(ad_caller, begin_time=call_begin_time)

                    ad_caller.log.error("Initiate call failed.")
                    tel_result_wrapper.result_value = CallResult(
                                                        'INITIATE_FAILED')
                    return tel_result_wrapper
                else:
                    ad_caller.log.info("Caller initate call successfully")
            finally:
                ad_caller.droid.telephonyStopTrackingCallStateChangeForSubscription(
                    subid_caller)
                if incall_ui_display == INCALL_UI_DISPLAY_FOREGROUND:
                    ad_caller.droid.telecomShowInCallScreen()
                elif incall_ui_display == INCALL_UI_DISPLAY_BACKGROUND:
                    ad_caller.droid.showHomeScreen()

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
                    tel_result_wrapper.result_value = CallResult(
                                                        'NO_CALL_ID_FOUND')
                for new_call_id in new_call_ids:
                    if not wait_for_in_call_active(ad, call_id=new_call_id):
                        tel_result_wrapper.result_value = CallResult(
                            'CALL_STATE_NOT_ACTIVE_DURING_ESTABLISHMENT')
                    else:
                        ad.log.info(
                            "callProperties = %s",
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

            if not wait_for_sending_sms(
                ad_tx,
                max_wait_time=MAX_WAIT_TIME_SMS_SENT_SUCCESS_IN_COLLISION):
                return False

            tasks = [
                (wait_for_matching_sms,
                (log, ad_rx, phonenumber_tx, text,
                MAX_WAIT_TIME_SMS_RECEIVE_IN_COLLISION, True)),
                (wait_for_call_end,
                (log, ad_caller, ad_callee, ad_hangup, verify_caller_func,
                    verify_callee_func, call_begin_time, 5, tel_result_wrapper,
                    WAIT_TIME_IN_CALL))]

            results = run_multithread_func(log, tasks)

            if not results[0]:
                ad_rx.log.error("No matching received SMS of length %s.",
                                length)
                return False

            tel_result_wrapper = results[1]

        except Exception as e:
            log.error("Exception error %s", e)
            raise
        finally:
            ad_rx.messaging_droid.smsStopTrackingIncomingSmsMessage()

    return tel_result_wrapper


def is_mms_match(event, phonenumber_tx, text):
    """Return True if 'text' equals to event['data']['Text']
        and phone number match.

    Args:
        event: Event object to verify.
        phonenumber_tx: phone number for sender.
        text: text string to verify.

    Returns:
        Return True if 'text' equals to event['data']['Text']
            and phone number match.
    """
    #TODO:  add mms matching after mms message parser is added in sl4a. b/34276948
    return True


def wait_for_matching_mms(log,
                          ad_rx,
                          phonenumber_tx,
                          text,
                          max_wait_time=MAX_WAIT_TIME_MMS_RECEIVE):
    """Wait for matching incoming SMS.

    Args:
        log: Log object.
        ad_rx: Receiver's Android Device Object
        phonenumber_tx: Sender's phone number.
        text: SMS content string.
        allow_multi_part_long_sms: is long SMS allowed to be received as
            multiple short SMS. This is optional, default value is True.

    Returns:
        True if matching incoming SMS is received.
    """
    try:
        #TODO: add mms matching after mms message parser is added in sl4a. b/34276948
        ad_rx.messaging_ed.wait_for_event(EventMmsDownloaded, is_mms_match,
                                          max_wait_time, phonenumber_tx, text)
        ad_rx.log.info("Got event %s", EventMmsDownloaded)
        return True
    except Empty:
        ad_rx.log.warning("No matched MMS downloaded event.")
        return False


def mms_send_receive_verify(log,
                            ad_tx,
                            ad_rx,
                            array_message,
                            max_wait_time=MAX_WAIT_TIME_MMS_RECEIVE,
                            expected_result=True,
                            slot_id_rx=None):
    """Send MMS, receive MMS, and verify content and sender's number.

        Send (several) MMS from droid_tx to droid_rx.
        Verify MMS is sent, delivered and received.
        Verify received content and sender's number are correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object
        ad_rx: Receiver's Android Device Object
        array_message: the array of message to send/receive
    """
    subid_tx = get_outgoing_message_sub_id(ad_tx)
    if slot_id_rx is None:
        subid_rx = get_incoming_message_sub_id(ad_rx)
    else:
        subid_rx = get_subid_from_slot_index(log, ad_rx, slot_id_rx)

    result = mms_send_receive_verify_for_subscription(
        log, ad_tx, ad_rx, subid_tx, subid_rx, array_message, max_wait_time)
    if result != expected_result:
        log_messaging_screen_shot(ad_tx, test_name="mms_tx")
        log_messaging_screen_shot(ad_rx, test_name="mms_rx")
    return result == expected_result


def sms_mms_send_logcat_check(ad, type, begin_time):
    type = type.upper()
    log_results = ad.search_logcat(
        "%s Message sent successfully" % type, begin_time=begin_time)
    if log_results:
        ad.log.info("Found %s sent successful log message: %s", type,
                    log_results[-1]["log_message"])
        return True
    else:
        log_results = ad.search_logcat(
            "ProcessSentMessageAction: Done sending %s message" % type,
            begin_time=begin_time)
        if log_results:
            for log_result in log_results:
                if "status is SUCCEEDED" in log_result["log_message"]:
                    ad.log.info(
                        "Found BugleDataModel %s send succeed log message: %s",
                        type, log_result["log_message"])
                    return True
    return False


def sms_mms_receive_logcat_check(ad, type, begin_time):
    type = type.upper()
    smshandle_logs = ad.search_logcat(
        "InboundSmsHandler: No broadcast sent on processing EVENT_BROADCAST_SMS",
        begin_time=begin_time)
    if smshandle_logs:
        ad.log.warning("Found %s", smshandle_logs[-1]["log_message"])
    log_results = ad.search_logcat(
        "New %s Received" % type, begin_time=begin_time) or \
        ad.search_logcat("New %s Downloaded" % type, begin_time=begin_time)
    if log_results:
        ad.log.info("Found SL4A %s received log message: %s", type,
                    log_results[-1]["log_message"])
        return True
    else:
        log_results = ad.search_logcat(
            "Received %s message" % type, begin_time=begin_time)
        if log_results:
            ad.log.info("Found %s received log message: %s", type,
                        log_results[-1]["log_message"])
        log_results = ad.search_logcat(
            "ProcessDownloadedMmsAction", begin_time=begin_time)
        for log_result in log_results:
            ad.log.info("Found %s", log_result["log_message"])
            if "status is SUCCEEDED" in log_result["log_message"]:
                ad.log.info("Download succeed with ProcessDownloadedMmsAction")
                return True
    return False


#TODO: add mms matching after mms message parser is added in sl4a. b/34276948
def mms_send_receive_verify_for_subscription(
        log,
        ad_tx,
        ad_rx,
        subid_tx,
        subid_rx,
        array_payload,
        max_wait_time=MAX_WAIT_TIME_MMS_RECEIVE):
    """Send MMS, receive MMS, and verify content and sender's number.

        Send (several) MMS from droid_tx to droid_rx.
        Verify MMS is sent, delivered and received.
        Verify received content and sender's number are correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object..
        ad_rx: Receiver's Android Device Object.
        subid_tx: Sender's subscription ID to be used for SMS
        subid_rx: Receiver's subscription ID to be used for SMS
        array_message: the array of message to send/receive
    """

    phonenumber_tx = ad_tx.telephony['subscription'][subid_tx]['phone_num']
    phonenumber_rx = ad_rx.telephony['subscription'][subid_rx]['phone_num']
    toggle_enforce = False

    for ad in (ad_tx, ad_rx):
        if "Permissive" not in ad.adb.shell("su root getenforce"):
            ad.adb.shell("su root setenforce 0")
            toggle_enforce = True
        if not getattr(ad, "messaging_droid", None):
            ad.messaging_droid, ad.messaging_ed = ad.get_droid()
            ad.messaging_ed.start()
        else:
            try:
                if not ad.messaging_droid.is_live:
                    ad.messaging_droid, ad.messaging_ed = ad.get_droid()
                    ad.messaging_ed.start()
                else:
                    ad.messaging_ed.clear_all_events()
                ad.messaging_droid.logI(
                    "Start mms_send_receive_verify_for_subscription test")
            except Exception:
                ad.log.info("Create new sl4a session for messaging")
                ad.messaging_droid, ad.messaging_ed = ad.get_droid()
                ad.messaging_ed.start()

    for subject, message, filename in array_payload:
        ad_tx.messaging_ed.clear_events(EventMmsSentSuccess)
        ad_tx.messaging_ed.clear_events(EventMmsSentFailure)
        ad_rx.messaging_ed.clear_events(EventMmsDownloaded)
        ad_rx.messaging_droid.smsStartTrackingIncomingMmsMessage()
        ad_tx.log.info(
            "Sending MMS from %s to %s, subject: %s, message: %s, file: %s.",
            phonenumber_tx, phonenumber_rx, subject, message, filename)
        try:
            ad_tx.messaging_droid.smsSendMultimediaMessage(
                phonenumber_rx, subject, message, phonenumber_tx, filename)
            try:
                events = ad_tx.messaging_ed.pop_events(
                    "(%s|%s)" % (EventMmsSentSuccess,
                                 EventMmsSentFailure), max_wait_time)
                for event in events:
                    ad_tx.log.info("Got event %s", event["name"])
                    if event["name"] == EventMmsSentFailure:
                        if event.get("data") and event["data"].get("Reason"):
                            ad_tx.log.error("%s with reason: %s",
                                            event["name"],
                                            event["data"]["Reason"])
                        return False
                    elif event["name"] == EventMmsSentSuccess:
                        break
            except Empty:
                ad_tx.log.warning("No %s or %s event.", EventMmsSentSuccess,
                                  EventMmsSentFailure)
                return False

            if not wait_for_matching_mms(log, ad_rx, phonenumber_tx,
                                         message, max_wait_time):
                return False
        except Exception as e:
            log.error("Exception error %s", e)
            raise
        finally:
            ad_rx.messaging_droid.smsStopTrackingIncomingMmsMessage()
            for ad in (ad_tx, ad_rx):
                if toggle_enforce:
                    ad.send_keycode("BACK")
                    ad.adb.shell("su root setenforce 1")
    return True


def mms_receive_verify_after_call_hangup(
        log, ad_tx, ad_rx, array_message,
        max_wait_time=MAX_WAIT_TIME_MMS_RECEIVE):
    """Verify the suspanded MMS during call will send out after call release.

        Hangup call from droid_tx to droid_rx.
        Verify MMS is sent, delivered and received.
        Verify received content and sender's number are correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object
        ad_rx: Receiver's Android Device Object
        array_message: the array of message to send/receive
    """
    return mms_receive_verify_after_call_hangup_for_subscription(
        log, ad_tx, ad_rx, get_outgoing_message_sub_id(ad_tx),
        get_incoming_message_sub_id(ad_rx), array_message, max_wait_time)


#TODO: add mms matching after mms message parser is added in sl4a. b/34276948
def mms_receive_verify_after_call_hangup_for_subscription(
        log,
        ad_tx,
        ad_rx,
        subid_tx,
        subid_rx,
        array_payload,
        max_wait_time=MAX_WAIT_TIME_MMS_RECEIVE):
    """Verify the suspanded MMS during call will send out after call release.

        Hangup call from droid_tx to droid_rx.
        Verify MMS is sent, delivered and received.
        Verify received content and sender's number are correct.

    Args:
        log: Log object.
        ad_tx: Sender's Android Device Object..
        ad_rx: Receiver's Android Device Object.
        subid_tx: Sender's subscription ID to be used for SMS
        subid_rx: Receiver's subscription ID to be used for SMS
        array_message: the array of message to send/receive
    """

    phonenumber_tx = ad_tx.telephony['subscription'][subid_tx]['phone_num']
    phonenumber_rx = ad_rx.telephony['subscription'][subid_rx]['phone_num']
    for ad in (ad_tx, ad_rx):
        if not getattr(ad, "messaging_droid", None):
            ad.messaging_droid, ad.messaging_ed = ad.get_droid()
            ad.messaging_ed.start()
    for subject, message, filename in array_payload:
        ad_rx.log.info(
            "Waiting MMS from %s to %s, subject: %s, message: %s, file: %s.",
            phonenumber_tx, phonenumber_rx, subject, message, filename)
        ad_rx.messaging_droid.smsStartTrackingIncomingMmsMessage()
        time.sleep(5)
        try:
            hangup_call(log, ad_tx)
            hangup_call(log, ad_rx)
            try:
                ad_tx.messaging_ed.pop_event(EventMmsSentSuccess,
                                             max_wait_time)
                ad_tx.log.info("Got event %s", EventMmsSentSuccess)
            except Empty:
                log.warning("No sent_success event.")
            if not wait_for_matching_mms(log, ad_rx, phonenumber_tx, message):
                return False
        finally:
            ad_rx.messaging_droid.smsStopTrackingIncomingMmsMessage()
    return True


def log_messaging_screen_shot(ad, test_name=""):
    ad.ensure_screen_on()
    ad.send_keycode("HOME")
    ad.adb.shell("am start -n com.google.android.apps.messaging/.ui."
                 "ConversationListActivity")
    time.sleep(3)
    ad.screenshot(test_name)
    ad.adb.shell("am start -n com.google.android.apps.messaging/com.google."
                 "android.apps.messaging.ui.conversation."
                 "LaunchConversationShimActivity -e conversation_id 1")
    time.sleep(3)
    ad.screenshot(test_name)
    ad.send_keycode("HOME")