#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
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
from acts_contrib.test_utils.tel.gft_inout_defines import VOLTE_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import CSFB_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import WFC_CALL
from acts_contrib.test_utils.tel.tel_defines  import DATA_STATE_CONNECTED
from acts_contrib.test_utils.tel.tel_defines  import SERVICE_STATE_IN_SERVICE
from acts_contrib.test_utils.tel.tel_defines  import SERVICE_STATE_OUT_OF_SERVICE
from acts_contrib.test_utils.tel.tel_logging_utils import log_screen_shot
from acts_contrib.test_utils.tel.tel_ims_utils import is_ims_registered
from acts_contrib.test_utils.tel.tel_test_utils import get_telephony_signal_strength
from acts_contrib.test_utils.tel.tel_test_utils import get_service_state_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.tel.tel_voice_utils import initiate_call
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_iwlan
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_volte
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_csfb
from acts_contrib.test_utils.tel.tel_data_utils import browsing_test


def check_no_service_time(ad, timeout=120):
    """ check device is no service or not

        Args:
            ad: android device
            timeout: timeout time for device back to service

        Returns:
            True if pass; False if fail.
    """

    for i in range (timeout):
        service_state = get_service_state_by_adb(ad.log,ad)
        if service_state != SERVICE_STATE_IN_SERVICE:
            ad.log.info("device becomes no/limited service in %s sec and service_state=%s"
                %(i+1, service_state))
            get_telephony_signal_strength(ad)
            return True
        time.sleep(1)
    get_telephony_signal_strength(ad)
    check_network_service(ad)
    ad.log.info("device does not become no/limited service in %s sec and service_state=%s"
        %(timeout, service_state))
    return False


def check_back_to_service_time(ad, timeout=120):
    """ check device is back to service or not

        Args:
            ad: android device
            timeout: timeout time for device back to service

        Returns:
            True if pass; False if fail.
    """
    for i in range (timeout):
        service_state = get_service_state_by_adb(ad.log,ad)
        if service_state == SERVICE_STATE_IN_SERVICE:
            if i==0:
                check_network_service(ad)
                ad.log.info("Skip check_back_to_service_time. Service_state=%s"
                    %(service_state))
                return True
            else:
                ad.log.info("device is back to service in %s sec and service_state=%s"
                    %(i+1, service_state))
                get_telephony_signal_strength(ad)
                return True
        time.sleep(1)
    get_telephony_signal_strength(ad)
    ad.log.info("device is not back in service in %s sec and service_state=%s"
        %(timeout, service_state))
    return False


def check_network_service(ad):
    """ check network service

        Args:
            ad: android device

        Returns:
            True if ad is in service; False if ad is not in service.
    """
    network_type_voice = ad.droid.telephonyGetCurrentVoiceNetworkType()
    network_type_data = ad.droid.telephonyGetCurrentDataNetworkType()
    service_state = get_service_state_by_adb(ad.log,ad)
    sim_state = ad.droid.telephonyGetSimState()
    ad.log.info("sim_state=%s" %(sim_state))
    ad.log.info("networkType_voice=%s" %(network_type_voice))
    ad.log.info("networkType_data=%s" %(network_type_data))
    ad.log.info("service_state=%s" %(service_state))
    if service_state == SERVICE_STATE_OUT_OF_SERVICE:
        log_screen_shot(ad, "device_out_of_service")
        return False
    return True


def mo_voice_call(log, ad, call_type, end_call=True, talk_time=15,
    retries=1, retry_time=30):
    """ MO voice call and check call type.
        End call if necessary.

        Args:
            log: log
            ad: android device
            call_type: WFC call, VOLTE call. CSFB call, voice call
            end_call: hangup call after voice call flag
            talk_time: in call duration in sec
            retries: retry times
            retry_time: wait for how many sec before next retry

        Returns:
            True if pass; False if fail.
    """
    callee_number = ad.mt_phone_number
    ad.log.info("MO voice call. call_type=%s" %(call_type))
    if is_phone_in_call(log, ad):
        ad.log.info("%s is in call. hangup_call before initiate call" %(callee_number))
        hangup_call(log, ad)
        time.sleep(1)

    for i in range(retries):
        ad.log.info("mo_voice_call attempt %d", i + 1)
        if initiate_call(log, ad, callee_number):
            time.sleep(5)
            check_voice_call_type(ad,call_type)
            get_voice_call_type(ad)
            break
        else:
            ad.log.error("initiate_call fail attempt %d", i + 1)
            time.sleep(retry_time)
            if i+1 == retries:
                ad.log.error("mo_voice_call retry failure")
                return False

    time.sleep(10)
    if end_call:
        time.sleep(talk_time)
        if is_phone_in_call(log, ad):
            ad.log.info("end voice call")
            if not hangup_call(log, ad):
                ad.log.error("end call fail")
                ad.droid.telecomShowInCallScreen()
                log_screen_shot(ad, "end_call_fail")
                return False
        else:
            #Call drop is unexpected
            ad.log.error("%s Unexpected call drop" %(call_type))
            ad.droid.telecomShowInCallScreen()
            log_screen_shot(ad, "call_drop")
            return False
        ad.log.info("%s successful" %(call_type))
    return True


def check_voice_call_type(ad, call_type):
    """ check current voice call type

        Args:
            ad: android device
            call_type: WFC call, VOLTE call. CSFB call, voice call

        Returns:
            True if pass; False if fail.
    """
    if is_phone_in_call(ad.log, ad):
        ad.droid.telecomShowInCallScreen()
        log_screen_shot(ad, "expected_call_type_%s" %call_type)
        if call_type == CSFB_CALL:
            if not is_phone_in_call_csfb(ad.log, ad):
                ad.log.error("current call is not %s" %(call_type))
                return False
            else:
                ad.log.info("current call is CSFB %s" %(call_type))
        elif call_type == WFC_CALL:
            if not is_phone_in_call_iwlan(ad.log, ad):
                ad.log.error("current call is not %s" %(call_type))
                return False
            else:
                ad.log.info("current call is VoWiFi %s" %(call_type))
        elif call_type == VOLTE_CALL:
            if not is_phone_in_call_volte(ad.log, ad):
                ad.log.error("current call is not %s" %(call_type))
                return False
            else:
                ad.log.info(" current call is VOLTE %s" %(call_type))
    else:
        ad.log.error("device is not in call")
        return False
    return True


def get_voice_call_type(ad):
    """ get current voice call type

        Args:
            ad: android device

        Returns:
            call type
    """
    if is_phone_in_call(ad.log, ad):
        if is_phone_in_call_csfb(ad.log, ad):
            ad.log.info("current call is CSFB")
            return CSFB_CALL
        elif is_phone_in_call_iwlan(ad.log, ad):
            ad.log.info("current call is VoWiFi")
            return WFC_CALL
        elif is_phone_in_call_volte(ad.log, ad):
            ad.log.info("current call is VOLTE")
            return VOLTE_CALL
    else:
        ad.log.error("device is not in call")
    return "UNKNOWN"


def verify_data_connection(ad, retries=3, retry_time=30):
    """ verify data connection

        Args:
            ad: android device
            retries: retry times
            retry_time: wait for how many sec before next retry

        Returns:
            True if pass; False if fail.
    """
    for i in range(retries):
        data_state = ad.droid.telephonyGetDataConnectionState()
        wifi_info = ad.droid.wifiGetConnectionInfo()
        if wifi_info["supplicant_state"] == "completed":
            ad.log.info("Wifi is connected=%s" %(wifi_info["SSID"]))
        ad.log.info("verify_data_connection attempt %d", i + 1)
        if not verify_internet_connection(ad.log, ad, retries=3):
            data_state = ad.droid.telephonyGetDataConnectionState()
            network_type_data = ad.droid.telephonyGetCurrentDataNetworkType()
            ad.log.error("verify_internet fail. data_state=%s, network_type_data=%s"
                %(data_state, network_type_data))
            ad.log.info("verify_data_connection fail attempt %d", i + 1)
            log_screen_shot(ad, "verify_internet")
            time.sleep(retry_time)
        else:
            ad.log.info("verify_data_connection pass")
            return True
    return False


def check_ims_state(ad):
    """ check current ism state

        Args:
            ad: android device

        Returns:
            ims state
    """
    r1 = is_ims_registered(ad.log, ad)
    r2 = ad.droid.imsIsEnhanced4gLteModeSettingEnabledByPlatform()
    r3 = ad.droid.imsIsEnhanced4gLteModeSettingEnabledByUser()
    r4 = ad.droid.telephonyIsVolteAvailable()
    ad.log.info("telephonyIsImsRegistered=%s" %(r1))
    ad.log.info("imsIsEnhanced4gLteModeSettingEnabledByPlatform=%s" %(r2))
    ad.log.info("imsIsEnhanced4gLteModeSettingEnabledByUser=%s" %(r3))
    ad.log.info("telephonyIsVolteAvailable=%s" %(r4))
    return r1


def browsing_test_ping_retry(ad):
    """ If browse test fails, use ping to test data connection

        Args:
            ad: android device

        Returns:
            True if pass; False if fail.
    """
    if not browsing_test(ad.log, ad):
        ad.log.error("Failed to browse websites!")
        if verify_data_connection(ad):
            ad.log.info("Ping success!")
            return True
        else:
            ad.log.info("Ping fail!")
            return False
    else:
        ad.log.info("Successful to browse websites!")