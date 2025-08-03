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


import time
from acts.utils import rand_ascii_str
from acts_contrib.test_utils.tel.tel_message_utils import sms_send_receive_verify
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_message_sub_id
from acts_contrib.test_utils.tel.tel_voice_utils import call_setup_teardown
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call

message_lengths = (50, 160, 180)


def _sms_test(log, ads):
    """Test SMS between two phones.
    Returns:
        True if success.
        False if failed.
    """
    for length in message_lengths:
        message_array = [rand_ascii_str(length)]
        if not sms_send_receive_verify(log, ads[0], ads[1],
                                       message_array):
            ads[0].log.error("SMS of length %s test failed", length)
            return False
        else:
            ads[0].log.info("SMS of length %s test succeeded", length)
    log.info("SMS test of length %s characters succeeded.",
                  message_lengths)
    return True

def _long_sms_test(log, ads):
    """Test SMS between two phones.
    Returns:
        True if success.
        False if failed.
    """

    long_message_lengths = (800, 1600)
    long_message_lengths_of_jp_carriers = (800, 1530)
    sender_message_sub_id = get_outgoing_message_sub_id(ads[0])
    sender_mcc = ads[0].telephony["subscription"][sender_message_sub_id]["mcc"]

    if str(sender_mcc) in ["440", "441"]:
        long_message_lengths = long_message_lengths_of_jp_carriers
    for length in long_message_lengths:
        message_array = [rand_ascii_str(length)]
        if not sms_send_receive_verify(log, ads[0], ads[1],
                                       message_array):
            ads[0].log.warning("SMS of length %s test failed", length)
            return False
        else:
            ads[0].log.info("SMS of length %s test succeeded", length)
            time.sleep(30)
    log.info("SMS test of length %s characters succeeded.",
                  message_lengths)
    return True

def _sms_test_mo(log, ads):
    return _sms_test(log, [ads[0], ads[1]])

def _sms_test_mt(log, ads):
    return _sms_test(log, [ads[1], ads[0]])

def _long_sms_test_mo(log, ads):
    return _long_sms_test(log, [ads[0], ads[1]])

def _long_sms_test_mt(log, ads):
    return _long_sms_test(log, [ads[1], ads[0]])


def test_sms_mo_in_call(log, ads, caller_func=None, callee_func=None):
    """Test MO SMS in call.

        log: log object
        ads: list of android objects, this list should have two ad.
        caller_func: function to verify caller is in correct state while in-call.
        callee_func: function to verify callee is in correct state while in-call.

    Returns:
        True if pass; False if fail.
    """

    log.info("Begin In Call SMS Test.")
    if not call_setup_teardown(
            log,
            ads[0],
            ads[1],
            ad_hangup=None,
            verify_caller_func=caller_func,
            verify_callee_func=callee_func):
        return False

    sms_result = True
    if not _sms_test_mo(log, ads):
        log.error("SMS test fail.")
        sms_result = False

    if not hangup_call(log, ads[0]):
        ads[0].log.info("Failed to hang up call!")
        sms_result = False

    return sms_result

