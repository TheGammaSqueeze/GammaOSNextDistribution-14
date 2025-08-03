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
from acts_contrib.test_utils.tel.tel_message_utils import mms_send_receive_verify
from acts_contrib.test_utils.tel.tel_message_utils import mms_receive_verify_after_call_hangup
from acts_contrib.test_utils.tel.tel_voice_utils import call_setup_teardown
from acts_contrib.test_utils.tel.tel_test_utils import get_operator_name

message_lengths = (50, 160, 180)
long_message_lengths = (800, 1600)


def _mms_test_mo(log, ads, expected_result=True):
    return _mms_test(log,
        [ads[0], ads[1]], expected_result=expected_result)

def _mms_test_mt(log, ads, expected_result=True):
    return _mms_test(log,
        [ads[1], ads[0]], expected_result=expected_result)

def _mms_test(log, ads, expected_result=True):
    """Test MMS between two phones.

    Returns:
        True if success.
        False if failed.
    """
    for length in message_lengths:
        message_array = [("Test Message", rand_ascii_str(length), None)]
        if not mms_send_receive_verify(
                log,
                ads[0],
                ads[1],
                message_array,
                expected_result=expected_result):
            log.warning("MMS of body length %s test failed", length)
            return False
        else:
            log.info("MMS of body length %s test succeeded", length)
    log.info("MMS test of body lengths %s succeeded",
                  message_lengths)
    return True

def _long_mms_test_mo(log, ads):
    return _long_mms_test(log, [ads[0], ads[1]])

def _long_mms_test_mt(log, ads):
    return _long_mms_test(log, [ads[1], ads[0]])

def _long_mms_test(log, ads):
    """Test MMS between two phones.

    Returns:
        True if success.
        False if failed.
    """
    for length in long_message_lengths:
        message_array = [("Test Message", rand_ascii_str(length), None)]
        if not mms_send_receive_verify(log, ads[0], ads[1],
                                       message_array):
            log.warning("MMS of body length %s test failed", length)
            return False
        else:
            log.info("MMS of body length %s test succeeded", length)
            time.sleep(30)
    log.info("MMS test of body lengths %s succeeded",
                  message_lengths)
    return True

def _mms_test_after_call_hangup(log, ads):
    """Test MMS send out after call hang up.

    Returns:
        True if success.
        False if failed.
    """
    args = [
        log, ads[0], ads[1], [("Test Message", "Basic Message Body",
                               None)]
    ]
    if get_operator_name(log, ads[0]) in ["spt", "Sprint"]:
        args.append(30)
    if not mms_send_receive_verify(*args):
        log.info("MMS send in call is suspended.")
        if not mms_receive_verify_after_call_hangup(*args):
            log.error(
                "MMS is not send and received after call release.")
            return False
        else:
            log.info("MMS is send and received after call release.")
            return True
    else:
        log.info("MMS is send and received successfully in call.")
        return True

def _mms_test_mo_after_call_hangup(log, ads):
    return _mms_test_after_call_hangup(log, [ads[0], ads[1]])

def _mms_test_mt_after_call_hangup(log, ads):
    return _mms_test_after_call_hangup(log, [ads[1], ads[0]])

def test_mms_mo_in_call(log, ads, wifi=False, caller_func=None, callee_func=None):
    """Test MO MMS in call.

        log: log object
        ads: list of android objects, this list should have two ad.
        wifi: If true, sending sms over wifi.
        caller_func: function to verify caller is in correct state while in-call.
        callee_func: function to verify callee is in correct state while in-call.

    Returns:
        True if pass; False if fail.

    """

    log.info("Begin In Call MMS Test.")
    if not call_setup_teardown(
            log,
            ads[0],
            ads[1],
            ad_hangup=None,
            verify_caller_func=caller_func,
            verify_callee_func=callee_func):
        return False

    if ads[0].sms_over_wifi and wifi:
        return _mms_test_mo(log, ads)
    else:
        return _mms_test_mo_after_call_hangup(log, ads)


