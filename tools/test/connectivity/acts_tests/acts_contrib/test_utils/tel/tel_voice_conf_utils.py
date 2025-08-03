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

from acts import signals
from acts.libs.utils.multithread import multithread_func
from acts_contrib.test_utils.tel.tel_defines import CALL_CAPABILITY_MANAGE_CONFERENCE
from acts_contrib.test_utils.tel.tel_defines import CALL_PROPERTY_CONFERENCE
from acts_contrib.test_utils.tel.tel_defines import CALL_STATE_ACTIVE
from acts_contrib.test_utils.tel.tel_defines import CALL_STATE_HOLDING
from acts_contrib.test_utils.tel.tel_defines import PHONE_TYPE_GSM
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_IN_CALL
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_voice_2g
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_voice_3g
from acts_contrib.test_utils.tel.tel_subscription_utils import get_incoming_voice_sub_id
from acts_contrib.test_utils.tel.tel_test_utils import get_call_uri
from acts_contrib.test_utils.tel.tel_test_utils import num_active_calls
from acts_contrib.test_utils.tel.tel_test_utils import verify_incall_state
from acts_contrib.test_utils.tel.tel_test_utils import is_uri_equivalent
from acts_contrib.test_utils.tel.tel_voice_utils import call_setup_teardown
from acts_contrib.test_utils.tel.tel_voice_utils import get_cep_conference_call_id
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.tel.tel_voice_utils import initiate_call
from acts_contrib.test_utils.tel.tel_voice_utils import swap_calls
from acts_contrib.test_utils.tel.tel_voice_utils import wait_and_reject_call_for_subscription


def _three_phone_call_mo_add_mo(log, ads, phone_setups, verify_funcs):
    """Use 3 phones to make MO calls.

    Call from PhoneA to PhoneB, accept on PhoneB.
    Call from PhoneA to PhoneC, accept on PhoneC.

    Args:
        ads: list of ad object.
            The list should have three objects.
        phone_setups: list of phone setup functions.
            The list should have three objects.
        verify_funcs: list of phone call verify functions.
            The list should have three objects.

    Returns:
        If success, return 'call_AB' id in PhoneA.
        if fail, return None.
    """

    class _CallException(Exception):
        pass

    try:
        verify_func_a, verify_func_b, verify_func_c = verify_funcs
        tasks = []
        for ad, setup_func in zip(ads, phone_setups):
            if setup_func is not None:
                tasks.append((setup_func, (log, ad)))
        if tasks != [] and not multithread_func(log, tasks):
            log.error("Phone Failed to Set Up Properly.")
            raise _CallException("Setup failed.")
        for ad in ads:
            ad.droid.telecomCallClearCallList()
            if num_active_calls(log, ad) != 0:
                ad.log.error("Phone Call List is not empty.")
                raise _CallException("Clear call list failed.")

        log.info("Step1: Call From PhoneA to PhoneB.")
        if not call_setup_teardown(
                log,
                ads[0],
                ads[1],
                ad_hangup=None,
                verify_caller_func=verify_func_a,
                verify_callee_func=verify_func_b):
            raise _CallException("PhoneA call PhoneB failed.")

        calls = ads[0].droid.telecomCallGetCallIds()
        ads[0].log.info("Calls in PhoneA %s", calls)
        if num_active_calls(log, ads[0]) != 1:
            raise _CallException("Call list verify failed.")
        call_ab_id = calls[0]

        log.info("Step2: Call From PhoneA to PhoneC.")
        if not call_setup_teardown(
                log,
                ads[0],
                ads[2],
                ad_hangup=None,
                verify_caller_func=verify_func_a,
                verify_callee_func=verify_func_c):
            raise _CallException("PhoneA call PhoneC failed.")
        if not verify_incall_state(log, [ads[0], ads[1], ads[2]],
                                   True):
            raise _CallException("Not All phones are in-call.")

    except _CallException:
        return None

    return call_ab_id


def _test_call_mo_mo_add_swap_x(log,
                                ads,
                                num_swaps,
                                phone_setup_a=None,
                                phone_setup_b=None,
                                phone_setup_c=None,
                                verify_phone_a_network_subscription=None,
                                verify_phone_b_network_subscription=None,
                                verify_phone_c_network_subscription=None):
    """Test swap feature in VoLTE call.

    PhoneA call PhoneB , accept on PhoneB.
    PhoneA call PhoneC , accept on PhoneC.
    Swap active call on PhoneA.(N times)

    Args:
        num_swaps: do swap for 'num_swaps' times.
            This value can be 0 (no swap operation).

    Returns:
        call_ab_id, call_ac_id if succeed;
        None, None if failed.

    """
    if ((phone_setup_a == phone_setup_voice_3g) or (phone_setup_a == phone_setup_voice_2g)):
        # make sure PhoneA is GSM phone before proceed.
        if (ads[0].droid.telephonyGetPhoneType() != PHONE_TYPE_GSM):
            raise signals.TestSkip("not GSM phone, abort swap test.")

    if (((phone_setup_b == phone_setup_voice_3g)
        and (phone_setup_c == phone_setup_voice_3g)) or
        ((phone_setup_b == phone_setup_voice_2g)
        and (phone_setup_c == phone_setup_voice_2g))):
        # make sure PhoneB and PhoneC are GSM phone before proceed.
        for ad in [ads[1], ads[2]]:
            if (ad.droid.telephonyGetPhoneType() != PHONE_TYPE_GSM):
                raise signals.TestSkip("not GSM phone, abort swap test.")

    call_ab_id = _three_phone_call_mo_add_mo(log,
        [ads[0], ads[1], ads[2]],
        [phone_setup_a, phone_setup_b, phone_setup_c], [
            verify_phone_a_network_subscription, verify_phone_b_network_subscription,
            verify_phone_c_network_subscription
        ])
    if call_ab_id is None:
        log.error("Failed to get call_ab_id")
        return None, None

    calls = ads[0].droid.telecomCallGetCallIds()
    ads[0].log.info("Calls in PhoneA %s", calls)
    if num_active_calls(log, ads[0]) != 2:
        return None, None
    if calls[0] == call_ab_id:
        call_ac_id = calls[1]
    else:
        call_ac_id = calls[0]

    if num_swaps > 0:
        log.info("Step3: Begin Swap x%s test.", num_swaps)
        if not swap_calls(log, ads, call_ab_id, call_ac_id,
                          num_swaps):
            log.error("Swap test failed.")
            return None, None

    return call_ab_id, call_ac_id


def _merge_ims_conference_call(log, ads, call_ab_id, call_ac_id):
    """Merge IMS conference call for both cases of CEP enabled and disabled.

    PhoneA in IMS (VoLTE or WiFi Calling) call with PhoneB.
    PhoneA in IMS (VoLTE or WiFi Calling) call with PhoneC.
    Merge calls to conference on PhoneA.

    Args:
        call_ab_id: call id for call_AB on PhoneA.
        call_ac_id: call id for call_AC on PhoneA.

    Returns:
        call_id for conference
    """

    log.info("Step4: Merge to Conf Call and verify Conf Call.")
    ads[0].droid.telecomCallJoinCallsInConf(call_ab_id, call_ac_id)
    time.sleep(WAIT_TIME_IN_CALL)
    calls = ads[0].droid.telecomCallGetCallIds()
    ads[0].log.info("Calls in PhoneA %s", calls)

    call_conf_id = None
    if num_active_calls(log, ads[0]) != 1:
        ads[0].log.info("Total number of call ids is not 1.")
        call_conf_id = get_cep_conference_call_id(ads[0])
        if call_conf_id is not None:
            log.info("New conference call id is found. CEP enabled.")
            calls.remove(call_conf_id)
            if (set(ads[0].droid.telecomCallGetCallChildren(
                call_conf_id)) != set(calls)):
                ads[0].log.error(
                    "Children list %s for conference call is not correct.",
                    ads[0].droid.telecomCallGetCallChildren(call_conf_id))
                return None

            if (CALL_PROPERTY_CONFERENCE not in ads[0]
                    .droid.telecomCallGetProperties(call_conf_id)):
                ads[0].log.error("Conf call id % properties wrong: %s", call_conf_id,
                        ads[0].droid.telecomCallGetProperties(call_conf_id))
                return None

            if (CALL_CAPABILITY_MANAGE_CONFERENCE not in ads[0]
                    .droid.telecomCallGetCapabilities(call_conf_id)):
                ads[0].log.error(
                    "Conf call id %s capabilities wrong: %s", call_conf_id,
                    ads[0].droid.telecomCallGetCapabilities(call_conf_id))
                return None

            if (call_ab_id in calls) or (call_ac_id in calls):
                log.error("Previous call ids should not in new call"
                " list after merge.")
                return None
    else:
        for call_id in calls:
            if call_id != call_ab_id and call_id != call_ac_id:
                call_conf_id = call_id
                log.info("CEP not enabled.")

    if not call_conf_id:
        log.error("Merge call fail, no new conference call id.")
        raise signals.TestFailure(
            "Calls were not merged. Failed to merge calls.",
            extras={"fail_reason": "Calls were not merged."
                " Failed to merge calls."})
    if not verify_incall_state(log, [ads[0], ads[1], ads[2]], True):
        return False

    # Check if Conf Call is currently active
    if ads[0].droid.telecomCallGetCallState(
            call_conf_id) != CALL_STATE_ACTIVE:
        ads[0].log.error(
            "Call_ID: %s, state: %s, expected: STATE_ACTIVE", call_conf_id,
            ads[0].droid.telecomCallGetCallState(call_conf_id))
        return None

    return call_conf_id


def _hangup_call(log, ad, device_description='Device'):
    if not hangup_call(log, ad):
        ad.log.error("Failed to hang up on %s", device_description)
        return False
    return True


def _test_ims_conference_merge_drop_second_call_from_participant(
        log, ads, call_ab_id, call_ac_id):
    """Test conference merge and drop in IMS (VoLTE or WiFi Calling) call.
    (supporting both cases of CEP enabled and disabled).

    PhoneA in IMS (VoLTE or WiFi Calling) call with PhoneB.
    PhoneA in IMS (VoLTE or WiFi Calling) call with PhoneC.
    Merge calls to conference on PhoneA (CEP enabled IMS conference).
    Hangup on PhoneC, check call continues between AB.
    Hangup on PhoneB, check A ends.

    Args:
        call_ab_id: call id for call_AB on PhoneA.
        call_ac_id: call id for call_AC on PhoneA.

    Returns:
        True if succeed;
        False if failed.
    """

    call_conf_id = _merge_ims_conference_call(log, ads, call_ab_id, call_ac_id)
    if call_conf_id is None:
        return False

    log.info("Step5: End call on PhoneC and verify call continues.")
    if not _hangup_call(log, ads[2], "PhoneC"):
        return False
    time.sleep(WAIT_TIME_IN_CALL)
    calls = ads[0].droid.telecomCallGetCallIds()
    ads[0].log.info("Calls in PhoneA %s", calls)
    if not verify_incall_state(log, [ads[0], ads[1]], True):
        return False
    if not verify_incall_state(log, [ads[2]], False):
        return False

    log.info("Step6: End call on PhoneB and verify PhoneA end.")
    if not _hangup_call(log, ads[1], "PhoneB"):
        return False
    time.sleep(WAIT_TIME_IN_CALL)
    if not verify_incall_state(log, [ads[0], ads[1], ads[2]], False):
        return False
    return True


def _test_ims_conference_merge_drop_second_call_from_host(
        log, ads, call_ab_id, call_ac_id):
    """Test conference merge and drop in IMS (VoLTE or WiFi Calling) call.
    (CEP enabled).

    PhoneA in IMS (VoLTE or WiFi Calling) call with PhoneB.
    PhoneA in IMS (VoLTE or WiFi Calling) call with PhoneC.
    Merge calls to conference on PhoneA (CEP enabled IMS conference).
    On PhoneA, disconnect call between A-C, verify PhoneA PhoneB still in call.
    On PhoneA, disconnect call between A-B, verify PhoneA PhoneB disconnected.

    Args:
        call_ab_id: call id for call_AB on PhoneA.
        call_ac_id: call id for call_AC on PhoneA.

    Returns:
        True if succeed;
        False if failed.
    """
    call_ab_uri = get_call_uri(ads[0], call_ab_id)
    call_ac_uri = get_call_uri(ads[0], call_ac_id)

    call_conf_id = _merge_ims_conference_call(log, ads, call_ab_id, call_ac_id)
    if call_conf_id is None:
        return False

    calls = ads[0].droid.telecomCallGetCallIds()
    calls.remove(call_conf_id)

    if not calls:
        raise signals.TestSkip('CEP is not supported. The test will be skipped.')

    log.info("Step5: Disconnect call A-C and verify call continues.")
    call_to_disconnect = None
    for call in calls:
        new_uri = get_call_uri(ads[0], call)
        if not new_uri:
            ads[0].log.warning('New URI should NOT be None.')
            raise signals.TestSkip('Invalid URI is found on the host. The test will be skipped.')
        else:
            ads[0].log.info('URI for call ID %s: %s', call, new_uri)
        if is_uri_equivalent(call_ac_uri, new_uri):
            call_to_disconnect = call
            calls.remove(call_to_disconnect)
            break
    if call_to_disconnect is None:
        log.error("Can NOT find call on host represents A-C.")
        return False
    else:
        ads[0].droid.telecomCallDisconnect(call_to_disconnect)
    time.sleep(WAIT_TIME_IN_CALL)
    if not verify_incall_state(log, [ads[0], ads[1]], True):
        return False
    if not verify_incall_state(log, [ads[2]], False):
        return False

    log.info(
        "Step6: Disconnect call A-B and verify PhoneA PhoneB end.")
    calls = ads[0].droid.telecomCallGetCallIds()
    call_to_disconnect = None
    for call in calls:
        new_uri = get_call_uri(ads[0], call)
        if not new_uri:
            ads[0].log.warning('New URI should NOT be None.')
            raise signals.TestSkip('Invalid URI is found on the host. The test will be skipped.')
        else:
            ads[0].log.info('URI for call ID %s: %s', call, new_uri)
        if is_uri_equivalent(call_ab_uri, new_uri):
            call_to_disconnect = call
            calls.remove(call_to_disconnect)
            break
    if call_to_disconnect is None:
        log.error("Can NOT find call on host represents A-B.")
        return False
    else:
        ads[0].droid.telecomCallDisconnect(call_to_disconnect)
    time.sleep(WAIT_TIME_IN_CALL)
    if not verify_incall_state(log, [ads[0], ads[1], ads[2]], False):
        return False
    return True


def _test_ims_conference_merge_drop_first_call_from_participant(
        log, ads, call_ab_id, call_ac_id):
    """Test conference merge and drop in IMS (VoLTE or WiFi Calling) call.
    (CEP enabled).

    PhoneA in IMS (VoLTE or WiFi Calling) call with PhoneB.
    PhoneA in IMS (VoLTE or WiFi Calling) call with PhoneC.
    Merge calls to conference on PhoneA (CEP enabled IMS conference).
    Hangup on PhoneB, check call continues between AC.
    Hangup on PhoneC, check A ends.

    Args:
        call_ab_id: call id for call_AB on PhoneA.
        call_ac_id: call id for call_AC on PhoneA.

    Returns:
        True if succeed;
        False if failed.
    """
    call_conf_id = _merge_ims_conference_call(log, ads, call_ab_id, call_ac_id)
    if call_conf_id is None:
        return False

    log.info("Step5: End call on PhoneB and verify call continues.")
    if not _hangup_call(log, ads[1], "PhoneB"):
        return False
    time.sleep(WAIT_TIME_IN_CALL)
    if not verify_incall_state(log, [ads[0], ads[2]], True):
        return False
    if not verify_incall_state(log, [ads[1]], False):
        return False

    log.info("Step6: End call on PhoneC and verify PhoneA end.")
    if not _hangup_call(log, ads[2], "PhoneC"):
        return False
    time.sleep(WAIT_TIME_IN_CALL)
    if not verify_incall_state(log, [ads[0], ads[1], ads[2]], False):
        return False
    return True


def _test_ims_conference_merge_drop_first_call_from_host(
        log, ads, call_ab_id, call_ac_id):
    """Test conference merge and drop in IMS (VoLTE or WiFi Calling) call.
    (CEP enabled).

    PhoneA in IMS (VoLTE or WiFi Calling) call with PhoneB.
    PhoneA in IMS (VoLTE or WiFi Calling) call with PhoneC.
    Merge calls to conference on PhoneA (CEP enabled IMS conference).
    On PhoneA, disconnect call between A-B, verify PhoneA PhoneC still in call.
    On PhoneA, disconnect call between A-C, verify PhoneA PhoneC disconnected.

    Args:
        call_ab_id: call id for call_AB on PhoneA.
        call_ac_id: call id for call_AC on PhoneA.

    Returns:
        True if succeed;
        False if failed.
    """
    call_ab_uri = get_call_uri(ads[0], call_ab_id)
    call_ac_uri = get_call_uri(ads[0], call_ac_id)

    call_conf_id = _merge_ims_conference_call(log, ads, call_ab_id, call_ac_id)
    if call_conf_id is None:
        return False

    calls = ads[0].droid.telecomCallGetCallIds()
    calls.remove(call_conf_id)

    if not calls:
        raise signals.TestSkip('CEP is not supported. The test will be skipped.')

    log.info("Step5: Disconnect call A-B and verify call continues.")
    call_to_disconnect = None
    for call in calls:
        new_uri = get_call_uri(ads[0], call)
        if not new_uri:
            ads[0].log.warning('New URI should NOT be None.')
            raise signals.TestSkip('Invalid URI is found on the host. The test will be skipped.')
        else:
            ads[0].log.info('URI for call ID %s: %s', call, new_uri)
        if is_uri_equivalent(call_ab_uri, new_uri):
            call_to_disconnect = call
            calls.remove(call_to_disconnect)
            break
    if call_to_disconnect is None:
        log.error("Can NOT find call on host represents A-B.")
        return False
    else:
        ads[0].droid.telecomCallDisconnect(call_to_disconnect)
    time.sleep(WAIT_TIME_IN_CALL)
    if not verify_incall_state(log, [ads[0], ads[2]], True):
        return False
    if not verify_incall_state(log, [ads[1]], False):
        return False

    log.info(
        "Step6: Disconnect call A-C and verify PhoneA PhoneC end.")
    calls = ads[0].droid.telecomCallGetCallIds()
    call_to_disconnect = None
    for call in calls:
        new_uri = get_call_uri(ads[0], call)
        if not new_uri:
            ads[0].log.warning('New URI should NOT be None.')
            raise signals.TestSkip('Invalid URI is found on the host. The test will be skipped.')
        else:
            ads[0].log.info('URI for call ID %s: %s', call, new_uri)
        if is_uri_equivalent(call_ac_uri, new_uri):
            call_to_disconnect = call
            calls.remove(call_to_disconnect)
            break
    if call_to_disconnect is None:
        log.error("Can NOT find call on host represents A-C.")
        return False
    else:
        ads[0].droid.telecomCallDisconnect(call_to_disconnect)
    time.sleep(WAIT_TIME_IN_CALL)
    if not verify_incall_state(log, [ads[0], ads[1], ads[2]], False):
        return False
    return True


def _three_phone_call_mo_add_mt(
    log,
    ads,
    phone_setups,
    verify_funcs,
    reject_once=False):
    """Use 3 phones to make MO call and MT call.

    Call from PhoneA to PhoneB, accept on PhoneB.
    Call from PhoneC to PhoneA, accept on PhoneA.

    Args:
        ads: list of ad object.
            The list should have three objects.
        phone_setups: list of phone setup functions.
            The list should have three objects.
        verify_funcs: list of phone call verify functions.
            The list should have three objects.
        reject_once: True for rejecting the second call once.

    Returns:
        If success, return 'call_AB' id in PhoneA.
        if fail, return None.
    """

    class _CallException(Exception):
        pass

    try:
        verify_func_a, verify_func_b, verify_func_c = verify_funcs
        tasks = []
        for ad, setup_func in zip(ads, phone_setups):
            if setup_func is not None:
                tasks.append((setup_func, (log, ad)))
        if tasks != [] and not multithread_func(log, tasks):
            log.error("Phone Failed to Set Up Properly.")
            raise _CallException("Setup failed.")
        for ad in ads:
            ad.droid.telecomCallClearCallList()
            if num_active_calls(log, ad) != 0:
                ad.log.error("Phone Call List is not empty.")
                raise _CallException("Clear call list failed.")

        log.info("Step1: Call From PhoneA to PhoneB.")
        if not call_setup_teardown(
                log,
                ads[0],
                ads[1],
                ad_hangup=None,
                verify_caller_func=verify_func_a,
                verify_callee_func=verify_func_b):
            raise _CallException("PhoneA call PhoneB failed.")

        calls = ads[0].droid.telecomCallGetCallIds()
        ads[0].log.info("Calls in PhoneA %s", calls)
        if num_active_calls(log, ads[0]) != 1:
            raise _CallException("Call list verify failed.")
        call_ab_id = calls[0]

        log.info("Step2: Call From PhoneC to PhoneA.")
        if reject_once:
            log.info("Step2-1: Reject incoming call once.")
            if not initiate_call(
                log,
                ads[2],
                ads[0].telephony['subscription'][get_incoming_voice_sub_id(
                    ads[0])]['phone_num']):
                ads[2].log.error("Initiate call failed.")
                raise _CallException("Failed to initiate call.")

            if not wait_and_reject_call_for_subscription(
                    log,
                    ads[0],
                    get_incoming_voice_sub_id(ads[0]),
                    incoming_number= \
                        ads[2].telephony['subscription'][
                            get_incoming_voice_sub_id(
                                ads[2])]['phone_num']):
                ads[0].log.error("Reject call fail.")
                raise _CallException("Failed to reject call.")

            _hangup_call(log, ads[2], "PhoneC")
            time.sleep(15)

        if not call_setup_teardown(
                log,
                ads[2],
                ads[0],
                ad_hangup=None,
                verify_caller_func=verify_func_c,
                verify_callee_func=verify_func_a):
            raise _CallException("PhoneA call PhoneC failed.")
        if not verify_incall_state(log, [ads[0], ads[1], ads[2]],
                                   True):
            raise _CallException("Not All phones are in-call.")

    except _CallException:
        return None
    return call_ab_id


def _test_call_mo_mt_add_swap_x(log,
                                ads,
                                num_swaps,
                                phone_setup_a=None,
                                phone_setup_b=None,
                                phone_setup_c=None,
                                verify_phone_a_network_subscription=None,
                                verify_phone_b_network_subscription=None,
                                verify_phone_c_network_subscription=None):
    """Test swap feature in VoLTE call.

    PhoneA call PhoneB, accept on PhoneB.
    PhoneC call PhoneA, accept on PhoneA.
    Swap active call on PhoneA. (N times)

    Args:
        num_swaps: do swap for 'num_swaps' times.
            This value can be 0 (no swap operation).

    Returns:
        call_ab_id, call_ac_id if succeed;
        None, None if failed.

    """
    if ((phone_setup_a == phone_setup_voice_3g) or (phone_setup_a == phone_setup_voice_2g)):
        # make sure PhoneA is GSM phone before proceed.
        if (ads[0].droid.telephonyGetPhoneType() != PHONE_TYPE_GSM):
            raise signals.TestSkip("not GSM phone, abort swap test.")

    if (((phone_setup_b == phone_setup_voice_3g)
        and (phone_setup_c == phone_setup_voice_3g)) or
        ((phone_setup_b == phone_setup_voice_2g)
        and (phone_setup_c == phone_setup_voice_2g))):
        # make sure PhoneB and PhoneC are GSM phone before proceed.
        for ad in [ads[1], ads[2]]:
            if (ad.droid.telephonyGetPhoneType() != PHONE_TYPE_GSM):
                raise signals.TestSkip("not GSM phone, abort swap test.")

    call_ab_id = _three_phone_call_mo_add_mt(log,
        [ads[0], ads[1], ads[2]],
        [phone_setup_a, phone_setup_b, phone_setup_c], [
            verify_phone_a_network_subscription, verify_phone_b_network_subscription,
            verify_phone_c_network_subscription
            ])
    if call_ab_id is None:
        log.error("Failed to get call_ab_id")
        return None, None

    calls = ads[0].droid.telecomCallGetCallIds()
    ads[0].log.info("Calls in PhoneA %s", calls)
    if num_active_calls(log, ads[0]) != 2:
        return None, None
    if calls[0] == call_ab_id:
        call_ac_id = calls[1]
    else:
        call_ac_id = calls[0]

    if num_swaps > 0:
        log.info("Step3: Begin Swap x%s test.", num_swaps)
        if not swap_calls(log, ads, call_ab_id, call_ac_id,
                          num_swaps):
            log.error("Swap test failed.")
            return None, None

    return call_ab_id, call_ac_id


def _three_phone_call_mt_add_mt(log, ads, phone_setups, verify_funcs):
    """Use 3 phones to make MT call and MT call.

    Call from PhoneB to PhoneA, accept on PhoneA.
    Call from PhoneC to PhoneA, accept on PhoneA.

    Args:
        ads: list of ad object.
            The list should have three objects.
        phone_setups: list of phone setup functions.
            The list should have three objects.
        verify_funcs: list of phone call verify functions.
            The list should have three objects.

    Returns:
        If success, return 'call_AB' id in PhoneA.
        if fail, return None.
    """

    class _CallException(Exception):
        pass

    try:
        verify_func_a, verify_func_b, verify_func_c = verify_funcs
        tasks = []
        for ad, setup_func in zip(ads, phone_setups):
            if setup_func is not None:
                tasks.append((setup_func, (log, ad)))
        if tasks != [] and not multithread_func(log, tasks):
            log.error("Phone Failed to Set Up Properly.")
            raise _CallException("Setup failed.")
        for ad in ads:
            ad.droid.telecomCallClearCallList()
            if num_active_calls(log, ad) != 0:
                ad.log.error("Phone Call List is not empty.")
                raise _CallException("Clear call list failed.")

        log.info("Step1: Call From PhoneB to PhoneA.")
        if not call_setup_teardown(
                log,
                ads[1],
                ads[0],
                ad_hangup=None,
                verify_caller_func=verify_func_b,
                verify_callee_func=verify_func_a):
            raise _CallException("PhoneB call PhoneA failed.")

        calls = ads[0].droid.telecomCallGetCallIds()
        ads[0].log.info("Calls in PhoneA %s", calls)
        if num_active_calls(log, ads[0]) != 1:
            raise _CallException("Call list verify failed.")
        call_ab_id = calls[0]

        log.info("Step2: Call From PhoneC to PhoneA.")
        if not call_setup_teardown(
                log,
                ads[2],
                ads[0],
                ad_hangup=None,
                verify_caller_func=verify_func_c,
                verify_callee_func=verify_func_a):
            raise _CallException("PhoneA call PhoneC failed.")
        if not verify_incall_state(log, [ads[0], ads[1], ads[2]],
                                  True):
            raise _CallException("Not All phones are in-call.")

    except _CallException:
        return None

    return call_ab_id

def _test_call_mt_mt_add_swap_x(log,
                                ads,
                                num_swaps,
                                phone_setup_a=None,
                                phone_setup_b=None,
                                phone_setup_c=None,
                                verify_phone_a_network_subscription=None,
                                verify_phone_b_network_subscription=None,
                                verify_phone_c_network_subscription=None):
    """Test swap feature in VoLTE call.

    PhoneB call PhoneA, accept on PhoneA.
    PhoneC call PhoneA, accept on PhoneA.
    Swap active call on PhoneA. (N times)

    Args:
        num_swaps: do swap for 'num_swaps' times.
            This value can be 0 (no swap operation).

    Returns:
        call_ab_id, call_ac_id if succeed;
        None, None if failed.

    """
    if ((phone_setup_a == phone_setup_voice_3g) or (phone_setup_a == phone_setup_voice_2g)):
        # make sure PhoneA is GSM phone before proceeSSd.
        if (ads[0].droid.telephonyGetPhoneType() != PHONE_TYPE_GSM):
            raise signals.TestSkip("not GSM phone, abort swap test.")

    if (((phone_setup_b == phone_setup_voice_3g)
        and (phone_setup_c == phone_setup_voice_3g)) or
        ((phone_setup_b == phone_setup_voice_2g)
        and (phone_setup_c == phone_setup_voice_2g))):
        # make sure PhoneB and PhoneC are GSM phone before proceed.
        for ad in [ads[1], ads[2]]:
            if (ad.droid.telephonyGetPhoneType() != PHONE_TYPE_GSM):
                raise signals.TestSkip("not GSM phone, abort swap test.")

    call_ab_id = _three_phone_call_mt_add_mt(log,
        [ads[0], ads[1], ads[2]],
        [phone_setup_a, phone_setup_b, phone_setup_c], [
            verify_phone_a_network_subscription, verify_phone_b_network_subscription,
            verify_phone_c_network_subscription
        ])
    if call_ab_id is None:
        log.error("Failed to get call_ab_id")
        return None, None

    calls = ads[0].droid.telecomCallGetCallIds()
    ads[0].log.info("Calls in PhoneA %s", calls)
    if num_active_calls(log, ads[0]) != 2:
        return None, None
    if calls[0] == call_ab_id:
        call_ac_id = calls[1]
    else:
        call_ac_id = calls[0]

    if num_swaps > 0:
        log.info("Step3: Begin Swap x%s test.", num_swaps)
        if not swap_calls(log, ads, call_ab_id, call_ac_id,
                          num_swaps):
            log.error("Swap test failed.")
            return None, None

    return call_ab_id, call_ac_id


def _three_phone_hangup_call_verify_call_state(
        log, ad_hangup, ad_verify, call_id, call_state, ads_active):
    """Private Test utility for swap test.

    Hangup on 'ad_hangup'.
    Verify 'call_id' on 'ad_verify' is in expected 'call_state'
    Verify each ad in ads_active are 'in-call'.

    Args:
        ad_hangup: android object to hangup call.
        ad_verify: android object to verify call id state.
        call_id: call id in 'ad_verify'.
        call_state: expected state for 'call_id'.
            'call_state' is either CALL_STATE_HOLDING or CALL_STATE_ACTIVE.
        ads_active: list of android object.
            Each one of them should be 'in-call' after 'hangup' operation.

    Returns:
        True if no error happened. Otherwise False.

    """
    ad_hangup.log.info("Hangup, verify call continues.")
    if not _hangup_call(log, ad_hangup):
        ad_hangup.log.error("Phone fails to hang up")
        return False
    time.sleep(WAIT_TIME_IN_CALL)

    if ad_verify.droid.telecomCallGetCallState(call_id) != call_state:
        ad_verify.log.error(
            "Call_id: %s, state: %s, expected: %s", call_id,
            ad_verify.droid.telecomCallGetCallState(call_id), call_state)
        return False
    ad_verify.log.info("Call in expected %s state", call_state)

    if not verify_incall_state(log, ads_active, True):
        ads_active.log.error("Phone not in call state")
        return False
    if not verify_incall_state(log, [ad_hangup], False):
        ad_hangup.log.error("Phone not in hangup state")
        return False

    return True


def _get_expected_call_state(ad):
    if "vzw" in [
            sub["operator"]
            for sub in ad.telephony["subscription"].values()
    ]:
        return CALL_STATE_ACTIVE
    return CALL_STATE_HOLDING

def _test_wcdma_conference_merge_drop(log, ads, call_ab_id, call_ac_id):
    """Test conference merge and drop in WCDMA/CSFB_WCDMA call.

    PhoneA in WCDMA (or CSFB_WCDMA) call with PhoneB.
    PhoneA in WCDMA (or CSFB_WCDMA) call with PhoneC.
    Merge calls to conference on PhoneA.
    Hangup on PhoneC, check call continues between AB.
    Hangup on PhoneB, check A ends.

    Args:
        call_ab_id: call id for call_AB on PhoneA.
        call_ac_id: call id for call_AC on PhoneA.

    Returns:
        True if succeed;
        False if failed.
    """
    log.info("Step4: Merge to Conf Call and verify Conf Call.")
    ads[0].droid.telecomCallJoinCallsInConf(call_ab_id, call_ac_id)
    time.sleep(WAIT_TIME_IN_CALL)
    calls = ads[0].droid.telecomCallGetCallIds()
    ads[0].log.info("Calls in PhoneA %s", calls)
    num_calls = num_active_calls(log, ads[0])
    if num_calls != 3:
        ads[0].log.error("Total number of call ids is not 3.")
        if num_calls == 2:
            if call_ab_id in calls and call_ac_id in calls:
                ads[0].log.error("Calls were not merged."
                    " Failed to merge calls.")
                raise signals.TestFailure(
                    "Calls were not merged. Failed to merge calls.",
                    extras={"fail_reason": "Calls were not merged."
                        " Failed to merge calls."})
        return False
    call_conf_id = None
    for call_id in calls:
        if call_id != call_ab_id and call_id != call_ac_id:
            call_conf_id = call_id
    if not call_conf_id:
        log.error("Merge call fail, no new conference call id.")
        return False
    if not verify_incall_state(log, [ads[0], ads[1], ads[2]], True):
        return False

    if ads[0].droid.telecomCallGetCallState(
            call_conf_id) != CALL_STATE_ACTIVE:
        ads[0].log.error(
            "Call_id: %s, state: %s, expected: STATE_ACTIVE", call_conf_id,
            ads[0].droid.telecomCallGetCallState(call_conf_id))
        return False

    log.info("Step5: End call on PhoneC and verify call continues.")
    if not _hangup_call(log, ads[2], "PhoneC"):
        return False
    time.sleep(WAIT_TIME_IN_CALL)
    calls = ads[0].droid.telecomCallGetCallIds()
    ads[0].log.info("Calls in PhoneA %s", calls)
    if num_active_calls(log, ads[0]) != 1:
        return False
    if not verify_incall_state(log, [ads[0], ads[1]], True):
        return False
    if not verify_incall_state(log, [ads[2]], False):
        return False

    log.info("Step6: End call on PhoneB and verify PhoneA end.")
    if not _hangup_call(log, ads[1], "PhoneB"):
        return False
    time.sleep(WAIT_TIME_IN_CALL)
    if not verify_incall_state(log, [ads[0], ads[1], ads[2]], False):
        return False
    return True