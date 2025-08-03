#!/usr/bin/env python3
#
#   Copyright 2022 - Google
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

from acts_contrib.test_utils.tel.tel_data_utils import active_file_download_task
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_voice_sub_id
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.tel.tel_voice_utils import initiate_call
from acts_contrib.test_utils.tel.tel_voice_utils import wait_and_answer_call


def initiate_call_verify_operation(log, caller, callee, download=False):
    """Initiate call and verify operations with an option of data idle or data download

    Args:
        log: log object.
        caller:  android device object as caller.
        callee:  android device object as callee.
        download: True if download operation is to be performed else False

    Return:
        True: if call initiated and verified operations successfully
        False: for errors
    """
    caller_number = caller.telephony['subscription'][get_outgoing_voice_sub_id(
        caller)]['phone_num']
    callee_number = callee.telephony['subscription'][get_outgoing_voice_sub_id(
        callee)]['phone_num']
    if not initiate_call(log, caller, callee_number):
        caller.log.error("Phone was unable to initate a call")
        return False

    if not wait_and_answer_call(log, callee, caller_number):
        callee.log.error(
            "Callee failed to receive incoming call or answered the call.")
        return False

    if download:
        if not active_file_download_task(log, caller, "10MB"):
            caller.log.error("Unable to download file")
            return False

    if not hangup_call(log, caller):
        caller.log.error("Unable to hang up the call")
        return False
    return True
