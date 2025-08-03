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

from typing import Sequence
from future import standard_library
standard_library.install_aliases()

import json
import logging
import re
import os
import urllib.parse
import time
import acts.controllers.iperf_server as ipf
import struct

from acts import signals
from acts.controllers.android_device import AndroidDevice
from queue import Empty
from acts.asserts import abort_all
from acts.controllers.adb_lib.error import AdbCommandError, AdbError
from acts.controllers.android_device import list_adb_devices
from acts.controllers.android_device import list_fastboot_devices

from acts.libs.proc.job import TimeoutError
from acts_contrib.test_utils.net import ui_utils
from acts_contrib.test_utils.tel.loggers.protos.telephony_metric_pb2 import TelephonyVoiceTestResult
from acts_contrib.test_utils.tel.tel_defines import CarrierConfigs
from acts_contrib.test_utils.tel.tel_defines import AOSP_PREFIX
from acts_contrib.test_utils.tel.tel_defines import CARD_POWER_DOWN
from acts_contrib.test_utils.tel.tel_defines import CARD_POWER_UP
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_CONFERENCE
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_VOLTE
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_VOLTE_PROVISIONING
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_VOLTE_OVERRIDE_WFC_PROVISIONING
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_HIDE_ENHANCED_4G_LTE_BOOL
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_VT
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_WFC
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_WFC_MODE_CHANGE
from acts_contrib.test_utils.tel.tel_defines import CARRIER_UNKNOWN
from acts_contrib.test_utils.tel.tel_defines import COUNTRY_CODE_LIST
from acts_contrib.test_utils.tel.tel_defines import DIALER_PACKAGE_NAME
from acts_contrib.test_utils.tel.tel_defines import DATA_ROAMING_ENABLE
from acts_contrib.test_utils.tel.tel_defines import DATA_ROAMING_DISABLE
from acts_contrib.test_utils.tel.tel_defines import GEN_4G
from acts_contrib.test_utils.tel.tel_defines import GEN_UNKNOWN
from acts_contrib.test_utils.tel.tel_defines import INVALID_SIM_SLOT_INDEX
from acts_contrib.test_utils.tel.tel_defines import INVALID_SUB_ID
from acts_contrib.test_utils.tel.tel_defines import MAX_SCREEN_ON_TIME
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_AIRPLANEMODE_EVENT
from acts_contrib.test_utils.tel.tel_defines import MESSAGE_PACKAGE_NAME
from acts_contrib.test_utils.tel.tel_defines import NETWORK_SERVICE_DATA
from acts_contrib.test_utils.tel.tel_defines import NETWORK_SERVICE_VOICE
from acts_contrib.test_utils.tel.tel_defines import PHONE_NUMBER_STRING_FORMAT_7_DIGIT
from acts_contrib.test_utils.tel.tel_defines import PHONE_NUMBER_STRING_FORMAT_10_DIGIT
from acts_contrib.test_utils.tel.tel_defines import PHONE_NUMBER_STRING_FORMAT_11_DIGIT
from acts_contrib.test_utils.tel.tel_defines import PHONE_NUMBER_STRING_FORMAT_12_DIGIT
from acts_contrib.test_utils.tel.tel_defines import SimSlotInfo
from acts_contrib.test_utils.tel.tel_defines import RAT_UNKNOWN
from acts_contrib.test_utils.tel.tel_defines import SERVICE_STATE_EMERGENCY_ONLY
from acts_contrib.test_utils.tel.tel_defines import SERVICE_STATE_IN_SERVICE
from acts_contrib.test_utils.tel.tel_defines import SERVICE_STATE_MAPPING
from acts_contrib.test_utils.tel.tel_defines import SERVICE_STATE_OUT_OF_SERVICE
from acts_contrib.test_utils.tel.tel_defines import SERVICE_STATE_POWER_OFF
from acts_contrib.test_utils.tel.tel_defines import SIM_STATE_ABSENT
from acts_contrib.test_utils.tel.tel_defines import SIM_STATE_LOADED
from acts_contrib.test_utils.tel.tel_defines import SIM_STATE_NOT_READY
from acts_contrib.test_utils.tel.tel_defines import SIM_STATE_PIN_REQUIRED
from acts_contrib.test_utils.tel.tel_defines import SIM_STATE_READY
from acts_contrib.test_utils.tel.tel_defines import SIM_STATE_UNKNOWN
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_ANDROID_STATE_SETTLING
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_BETWEEN_STATE_CHECK
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_FOR_STATE_CHANGE
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_SYNC_DATE_TIME_FROM_NETWORK
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_ONLY
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import EventActiveDataSubIdChanged
from acts_contrib.test_utils.tel.tel_defines import EventDisplayInfoChanged
from acts_contrib.test_utils.tel.tel_defines import EventServiceStateChanged
from acts_contrib.test_utils.tel.tel_defines import NetworkCallbackContainer
from acts_contrib.test_utils.tel.tel_defines import ServiceStateContainer
from acts_contrib.test_utils.tel.tel_defines import DisplayInfoContainer
from acts_contrib.test_utils.tel.tel_defines import OverrideNetworkContainer
from acts_contrib.test_utils.tel.tel_logging_utils import disable_qxdm_logger
from acts_contrib.test_utils.tel.tel_logging_utils import get_screen_shot_log
from acts_contrib.test_utils.tel.tel_logging_utils import log_screen_shot
from acts_contrib.test_utils.tel.tel_logging_utils import start_qxdm_logger
from acts_contrib.test_utils.tel.tel_lookup_tables import connection_type_from_type_string
from acts_contrib.test_utils.tel.tel_lookup_tables import is_valid_rat
from acts_contrib.test_utils.tel.tel_lookup_tables import get_allowable_network_preference
from acts_contrib.test_utils.tel.tel_lookup_tables import get_voice_mail_count_check_function
from acts_contrib.test_utils.tel.tel_lookup_tables import get_voice_mail_check_number
from acts_contrib.test_utils.tel.tel_lookup_tables import network_preference_for_generation
from acts_contrib.test_utils.tel.tel_lookup_tables import operator_name_from_network_name
from acts_contrib.test_utils.tel.tel_lookup_tables import operator_name_from_plmn_id
from acts_contrib.test_utils.tel.tel_lookup_tables import rat_family_from_rat
from acts_contrib.test_utils.tel.tel_lookup_tables import rat_generation_from_rat
from acts_contrib.test_utils.tel.tel_subscription_utils import get_slot_index_from_subid
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_by_adb
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_from_logical_slot
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_from_slot_index
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_incoming_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import set_incoming_voice_sub_id
from acts.utils import adb_shell_ping
from acts.utils import load_config
from acts.logger import epoch_to_log_line_timestamp
from acts.utils import get_current_epoch_time
from acts.utils import exe_cmd

log = logging
STORY_LINE = "+19523521350"
CallResult = TelephonyVoiceTestResult.CallResult.Value


class TelResultWrapper(object):
    """Test results wrapper for Telephony test utils.

    In order to enable metrics reporting without refactoring
    all of the test utils this class is used to keep the
    current return boolean scheme in tact.
    """

    def __init__(self, result_value):
        self._result_value = result_value

    @property
    def result_value(self):
        return self._result_value

    @result_value.setter
    def result_value(self, result_value):
        self._result_value = result_value

    def __bool__(self):
        return self._result_value == CallResult('SUCCESS')


def abort_all_tests(log, msg):
    log.error("Aborting all ongoing tests due to: %s.", msg)
    abort_all(msg)


def get_phone_number_by_adb(ad):
    return phone_number_formatter(
        ad.adb.shell("service call iphonesubinfo 13"))


def get_iccid_by_adb(ad):
    return ad.adb.shell("service call iphonesubinfo 11")


def get_operator_by_adb(ad):
    operator = ad.adb.getprop("gsm.sim.operator.alpha")
    if "," in operator:
        operator = operator.strip()[0]
    return operator


def get_plmn_by_adb(ad):
    plmn_id = ad.adb.getprop("gsm.sim.operator.numeric")
    if "," in plmn_id:
        plmn_id = plmn_id.strip()[0]
    return plmn_id


def get_sub_id_by_adb(ad):
    return ad.adb.shell("service call iphonesubinfo 5")


def setup_droid_properties_by_adb(log, ad, sim_filename=None):

    sim_data = None
    if sim_filename:
        try:
            sim_data = load_config(sim_filename)
        except Exception:
            log.warning("Failed to load %s!", sim_filename)

    sub_id = get_sub_id_by_adb(ad)
    iccid = get_iccid_by_adb(ad)
    ad.log.info("iccid = %s", iccid)
    if sim_data.get(iccid) and sim_data[iccid].get("phone_num"):
        phone_number = phone_number_formatter(sim_data[iccid]["phone_num"])
    else:
        phone_number = get_phone_number_by_adb(ad)
        if not phone_number and hasattr(ad, phone_number):
            phone_number = ad.phone_number
    if not phone_number:
        ad.log.error("Failed to find valid phone number for %s", iccid)
        abort_all_tests(ad.log, "Failed to find valid phone number for %s")
    sub_record = {
        'phone_num': phone_number,
        'iccid': get_iccid_by_adb(ad),
        'sim_operator_name': get_operator_by_adb(ad),
        'operator': operator_name_from_plmn_id(get_plmn_by_adb(ad))
    }
    device_props = {'subscription': {sub_id: sub_record}}
    ad.log.info("subId %s SIM record: %s", sub_id, sub_record)
    setattr(ad, 'telephony', device_props)


def setup_droid_properties(log, ad, sim_filename=None, all_sub=False):

    if ad.skip_sl4a:
        return setup_droid_properties_by_adb(
            log, ad, sim_filename=sim_filename)
    refresh_droid_config(log, ad, all_sub)
    sim_data = {}
    if sim_filename:
        try:
            sim_data = load_config(sim_filename)
        except Exception:
            log.warning("Failed to load %s!", sim_filename)
    if not ad.telephony["subscription"]:
        abort_all_tests(ad.log, "No valid subscription")
    ad.log.debug("Subscription DB %s", ad.telephony["subscription"])
    result = True
    active_sub_id = get_outgoing_voice_sub_id(ad)
    for sub_id, sub_info in ad.telephony["subscription"].items():
        ad.log.debug("Loop for Subid %s", sub_id)
        sub_info["operator"] = get_operator_name(log, ad, sub_id)
        iccid = sub_info["iccid"]
        if not iccid:
            ad.log.warning("Unable to find ICC-ID for subscriber %s", sub_id)
            continue
        if sub_info.get("phone_num"):
            if iccid in sim_data and sim_data[iccid].get("phone_num"):
                if not check_phone_number_match(sim_data[iccid]["phone_num"],
                                                sub_info["phone_num"]):
                    ad.log.warning(
                        "phone_num %s in sim card data file for iccid %s"
                        "  do not match phone_num %s from subscription",
                        sim_data[iccid]["phone_num"], iccid,
                        sub_info["phone_num"])
                sub_info["phone_num"] = sim_data[iccid]["phone_num"]
        else:
            if iccid in sim_data and sim_data[iccid].get("phone_num"):
                sub_info["phone_num"] = sim_data[iccid]["phone_num"]
            elif sub_id == active_sub_id:
                phone_number = get_phone_number_by_secret_code(
                    ad, sub_info["sim_operator_name"])
                if phone_number:
                    sub_info["phone_num"] = phone_number
                elif getattr(ad, "phone_num", None):
                    sub_info["phone_num"] = ad.phone_number
        if (not sub_info.get("phone_num")) and sub_id == active_sub_id:
            ad.log.info("sub_id %s sub_info = %s", sub_id, sub_info)
            ad.log.error(
                "Unable to retrieve phone number for sub %s with iccid"
                " %s from device or testbed config or sim card file %s",
                sub_id, iccid, sim_filename)
            result = False
        if not hasattr(
                ad, 'roaming'
        ) and sub_info["sim_plmn"] != sub_info["network_plmn"] and sub_info["sim_operator_name"].strip(
        ) not in sub_info["network_operator_name"].strip():
            ad.log.info("roaming is not enabled, enable it")
            setattr(ad, 'roaming', True)
        ad.log.info("SubId %s info: %s", sub_id, sorted(sub_info.items()))
    get_phone_capability(ad)
    data_roaming = getattr(ad, 'roaming', False)
    if get_cell_data_roaming_state_by_adb(ad) != data_roaming:
        set_cell_data_roaming_state_by_adb(ad, data_roaming)
        # Setup VoWiFi MDN for Verizon. b/33187374
    if not result:
        abort_all_tests(ad.log, "Failed to find valid phone number")

    ad.log.debug("telephony = %s", ad.telephony)


def refresh_droid_config(log, ad, all_sub = False):
    """ Update Android Device telephony records for each sub_id.

    Args:
        log: log object
        ad: android device object
        all_sub: True to record all sub id(include inactive SIM.)

    Returns:
        None
    """
    if not getattr(ad, 'telephony', {}):
        setattr(ad, 'telephony', {"subscription": {}})
    droid = ad.droid
    sub_info_list = droid.subscriptionGetAllSubInfoList()
    ad.log.info("SubInfoList is %s", sub_info_list)
    if not sub_info_list: return
    active_sub_id = get_outgoing_voice_sub_id(ad)
    for sub_info in sub_info_list:
        sub_id = sub_info["subscriptionId"]
        sim_slot = sub_info["simSlotIndex"]
        if sub_info.get("carrierId"):
            carrier_id = sub_info["carrierId"]
        else:
            carrier_id = -1
        if sub_info.get("isOpportunistic"):
            isopportunistic = sub_info["isOpportunistic"]
        else:
            isopportunistic = -1

        if sim_slot != INVALID_SIM_SLOT_INDEX or all_sub:
            if sub_id not in ad.telephony["subscription"]:
                ad.telephony["subscription"][sub_id] = {}
            sub_record = ad.telephony["subscription"][sub_id]
            if sub_info.get("iccId"):
                sub_record["iccid"] = sub_info["iccId"]
            else:
                sub_record[
                    "iccid"] = droid.telephonyGetSimSerialNumberForSubscription(
                        sub_id)
            sub_record["sim_slot"] = sim_slot
            if sub_info.get("mcc"):
                sub_record["mcc"] = sub_info["mcc"]
            if sub_info.get("mnc"):
                sub_record["mnc"] = sub_info["mnc"]
            if sub_info.get("displayName"):
                sub_record["display_name"] = sub_info["displayName"]
            try:
                sub_record[
                    "phone_type"] = droid.telephonyGetPhoneTypeForSubscription(
                        sub_id)
            except:
                if not sub_record.get("phone_type"):
                    sub_record["phone_type"] = droid.telephonyGetPhoneType()
            sub_record[
                "sim_plmn"] = droid.telephonyGetSimOperatorForSubscription(
                    sub_id)
            sub_record[
                "sim_operator_name"] = droid.telephonyGetSimOperatorNameForSubscription(
                    sub_id)
            sub_record[
                "network_plmn"] = droid.telephonyGetNetworkOperatorForSubscription(
                    sub_id)
            sub_record[
                "network_operator_name"] = droid.telephonyGetNetworkOperatorNameForSubscription(
                    sub_id)
            sub_record[
                "sim_country"] = droid.telephonyGetSimCountryIsoForSubscription(
                    sub_id)
            if active_sub_id == sub_id:
                try:
                    sub_record[
                        "carrier_id"] = ad.droid.telephonyGetSimCarrierId()
                    sub_record[
                        "carrier_id_name"] = ad.droid.telephonyGetSimCarrierIdName(
                        )
                except:
                    ad.log.info("Carrier ID is not supported")
            if carrier_id == 2340:
                ad.log.info("SubId %s info: %s", sub_id, sorted(
                    sub_record.items()))
                continue
            if carrier_id == 1989 and isopportunistic == "true":
                ad.log.info("SubId %s info: %s", sub_id, sorted(
                    sub_record.items()))
                continue
            if not sub_info.get("number"):
                sub_info[
                    "number"] = droid.telephonyGetLine1NumberForSubscription(
                        sub_id)
            if sub_info.get("number"):
                if sub_record.get("phone_num"):
                    # Use the phone number provided in sim info file by default
                    # as the sub_info["number"] may not be formatted in a
                    # dialable number
                    if not check_phone_number_match(sub_info["number"],
                                                    sub_record["phone_num"]):
                        ad.log.info(
                            "Subscriber phone number changed from %s to %s",
                            sub_record["phone_num"], sub_info["number"])
                        sub_record["phone_num"] = sub_info["number"]
                else:
                    sub_record["phone_num"] = phone_number_formatter(
                        sub_info["number"])
            ad.log.info("SubId %s info: %s", sub_id, sorted(
                sub_record.items()))


def get_phone_number_by_secret_code(ad, operator):
    if "T-Mobile" in operator:
        ad.droid.telecomDialNumber("#686#")
        ad.send_keycode("ENTER")
        for _ in range(12):
            output = ad.search_logcat("mobile number")
            if output:
                result = re.findall(r"mobile number is (\S+)",
                                    output[-1]["log_message"])
                ad.send_keycode("BACK")
                return result[0]
            else:
                time.sleep(5)
    return ""


def get_user_config_profile(ad):
    return {
        "Airplane Mode":
        ad.droid.connectivityCheckAirplaneMode(),
        "IMS Registered":
        ad.droid.telephonyIsImsRegistered(),
        "Preferred Network Type":
        ad.droid.telephonyGetPreferredNetworkTypes(),
        "VoLTE Platform Enabled":
        ad.droid.imsIsEnhanced4gLteModeSettingEnabledByPlatform(),
        "VoLTE Enabled":
        ad.droid.imsIsEnhanced4gLteModeSettingEnabledByUser(),
        "VoLTE Available":
        ad.droid.telephonyIsVolteAvailable(),
        "VT Available":
        ad.droid.telephonyIsVideoCallingAvailable(),
        "VT Enabled":
        ad.droid.imsIsVtEnabledByUser(),
        "VT Platform Enabled":
        ad.droid.imsIsVtEnabledByPlatform(),
        "WiFi State":
        ad.droid.wifiCheckState(),
        "WFC Available":
        ad.droid.telephonyIsWifiCallingAvailable(),
        "WFC Enabled":
        ad.droid.imsIsWfcEnabledByUser(),
        "WFC Platform Enabled":
        ad.droid.imsIsWfcEnabledByPlatform(),
        "WFC Mode":
        ad.droid.imsGetWfcMode()
    }


def get_num_active_sims(log, ad):
    """ Get the number of active SIM cards by counting slots

    Args:
        ad: android_device object.

    Returns:
        result: The number of loaded (physical) SIM cards
    """
    # using a dictionary as a cheap way to prevent double counting
    # in the situation where multiple subscriptions are on the same SIM.
    # yes, this is a corner corner case.
    valid_sims = {}
    subInfo = ad.droid.subscriptionGetAllSubInfoList()
    for info in subInfo:
        ssidx = info['simSlotIndex']
        if ssidx == INVALID_SIM_SLOT_INDEX:
            continue
        valid_sims[ssidx] = True
    return len(valid_sims.keys())


def toggle_airplane_mode_by_adb(log, ad, new_state=None):
    """ Toggle the state of airplane mode.

    Args:
        log: log handler.
        ad: android_device object.
        new_state: Airplane mode state to set to.
            If None, opposite of the current state.
        strict_checking: Whether to turn on strict checking that checks all features.

    Returns:
        result: True if operation succeed. False if error happens.
    """
    cur_state = bool(int(ad.adb.shell("settings get global airplane_mode_on")))
    if new_state == cur_state:
        ad.log.info("Airplane mode already in %s", new_state)
        return True
    elif new_state is None:
        new_state = not cur_state
    ad.log.info("Change airplane mode from %s to %s", cur_state, new_state)
    try:
        ad.adb.shell("settings put global airplane_mode_on %s" % int(new_state))
        ad.adb.shell("am broadcast -a android.intent.action.AIRPLANE_MODE "
                     "--ez state %s" % new_state)
    except Exception as e:
        ad.log.error(e)
        return False
    changed_state = bool(int(ad.adb.shell("settings get global airplane_mode_on")))
    return changed_state == new_state


def toggle_airplane_mode(log, ad, new_state=None, strict_checking=True):
    """ Toggle the state of airplane mode.

    Args:
        log: log handler.
        ad: android_device object.
        new_state: Airplane mode state to set to.
            If None, opposite of the current state.
        strict_checking: Whether to turn on strict checking that checks all features.

    Returns:
        result: True if operation succeed. False if error happens.
    """
    if ad.skip_sl4a:
        return toggle_airplane_mode_by_adb(log, ad, new_state)
    else:
        return toggle_airplane_mode_msim(
            log, ad, new_state, strict_checking=strict_checking)


def get_telephony_signal_strength(ad):
    #{'evdoEcio': -1, 'asuLevel': 28, 'lteSignalStrength': 14, 'gsmLevel': 0,
    # 'cdmaAsuLevel': 99, 'evdoDbm': -120, 'gsmDbm': -1, 'cdmaEcio': -160,
    # 'level': 2, 'lteLevel': 2, 'cdmaDbm': -120, 'dbm': -112, 'cdmaLevel': 0,
    # 'lteAsuLevel': 28, 'gsmAsuLevel': 99, 'gsmBitErrorRate': 0,
    # 'lteDbm': -112, 'gsmSignalStrength': 99}
    try:
        signal_strength = ad.droid.telephonyGetSignalStrength()
        if not signal_strength:
            signal_strength = {}
    except Exception as e:
        ad.log.error(e)
        signal_strength = {}
    return signal_strength


def get_lte_rsrp(ad):
    try:
        if ad.adb.getprop("ro.build.version.release")[0] in ("9", "P"):
            out = ad.adb.shell(
                "dumpsys telephony.registry | grep -i signalstrength")
            if out:
                lte_rsrp = out.split()[9]
                if lte_rsrp:
                    ad.log.info("lte_rsrp: %s ", lte_rsrp)
                    return lte_rsrp
        else:
            out = ad.adb.shell(
            "dumpsys telephony.registry |grep -i primary=CellSignalStrengthLte")
            if out:
                lte_cell_info = out.split('mLte=')[1]
                lte_rsrp = re.match(r'.*rsrp=(\S+).*', lte_cell_info).group(1)
                if lte_rsrp:
                    ad.log.info("lte_rsrp: %s ", lte_rsrp)
                    return lte_rsrp
    except Exception as e:
        ad.log.error(e)
    return None


def break_internet_except_sl4a_port(ad, sl4a_port):
    ad.log.info("Breaking internet using iptables rules")
    ad.adb.shell("iptables -I INPUT 1 -p tcp --dport %s -j ACCEPT" % sl4a_port,
                 ignore_status=True)
    ad.adb.shell("iptables -I INPUT 2 -p tcp --sport %s -j ACCEPT" % sl4a_port,
                 ignore_status=True)
    ad.adb.shell("iptables -I INPUT 3 -j DROP", ignore_status=True)
    ad.adb.shell("ip6tables -I INPUT -j DROP", ignore_status=True)
    return True


def resume_internet_with_sl4a_port(ad, sl4a_port):
    ad.log.info("Bring internet back using iptables rules")
    ad.adb.shell("iptables -D INPUT -p tcp --dport %s -j ACCEPT" % sl4a_port,
                 ignore_status=True)
    ad.adb.shell("iptables -D INPUT -p tcp --sport %s -j ACCEPT" % sl4a_port,
                 ignore_status=True)
    ad.adb.shell("iptables -D INPUT -j DROP", ignore_status=True)
    ad.adb.shell("ip6tables -D INPUT -j DROP", ignore_status=True)
    return True


def test_data_browsing_success_using_sl4a(log, ad):
    result = True
    web_page_list = ['https://www.google.com', 'https://www.yahoo.com',
                     'https://www.amazon.com', 'https://www.nike.com',
                     'https://www.facebook.com']
    for website in web_page_list:
        if not verify_http_connection(log, ad, website, retry=0):
            ad.log.error("Failed to browse %s successfully!", website)
            result = False
    return result


def test_data_browsing_failure_using_sl4a(log, ad):
    result = True
    web_page_list = ['https://www.youtube.com', 'https://www.cnn.com',
                     'https://www.att.com', 'https://www.nbc.com',
                     'https://www.verizonwireless.com']
    for website in web_page_list:
        if not verify_http_connection(log, ad, website, retry=0,
                                      expected_state=False):
            ad.log.error("Browsing to %s worked!", website)
            result = False
    return result


def is_expected_event(event_to_check, events_list):
    """ check whether event is present in the event list

    Args:
        event_to_check: event to be checked.
        events_list: list of events
    Returns:
        result: True if event present in the list. False if not.
    """
    for event in events_list:
        if event in event_to_check['name']:
            return True
    return False


def is_sim_ready(log, ad, sim_slot_id=None):
    """ check whether SIM is ready.

    Args:
        ad: android_device object.
        sim_slot_id: check the SIM status for sim_slot_id
            This is optional. If this is None, check default SIM.

    Returns:
        result: True if all SIMs are ready. False if not.
    """
    if sim_slot_id is None:
        status = ad.droid.telephonyGetSimState()
    else:
        status = ad.droid.telephonyGetSimStateForSlotId(sim_slot_id)
    if status != SIM_STATE_READY:
        ad.log.info("Sim state is %s, not ready", status)
        return False
    return True


def is_sim_ready_by_adb(log, ad):
    state = ad.adb.getprop("gsm.sim.state")
    ad.log.info("gsm.sim.state = %s", state)
    return state == SIM_STATE_READY or state == SIM_STATE_LOADED


def wait_for_sim_ready_by_adb(log, ad, wait_time=90):
    return _wait_for_droid_in_state(log, ad, wait_time, is_sim_ready_by_adb)


def is_sims_ready_by_adb(log, ad):
    states = list(ad.adb.getprop("gsm.sim.state").split(","))
    ad.log.info("gsm.sim.state = %s", states)
    for state in states:
        if state != SIM_STATE_READY and state != SIM_STATE_LOADED:
            return False
    return True


def wait_for_sims_ready_by_adb(log, ad, wait_time=90):
    return _wait_for_droid_in_state(log, ad, wait_time, is_sims_ready_by_adb)


def get_service_state_by_adb(log, ad):
    output = ad.adb.shell("dumpsys telephony.registry | grep mServiceState")
    if "mVoiceRegState" in output:
        result = re.findall(r"mVoiceRegState=(\S+)\((\S+)\)", output)
        if result:
            if getattr(ad, 'dsds', False):
                default_slot = getattr(ad, 'default_slot', 0)
                ad.log.info("mVoiceRegState is %s %s", result[default_slot][0],
                            result[default_slot][1])
                return result[default_slot][1]
            else:
                ad.log.info("mVoiceRegState is %s %s", result[0][0],
                            result[0][1])
                return result[0][1]
    else:
        result = re.search(r"mServiceState=(\S+)", output)
        if result:
            ad.log.info("mServiceState=%s %s", result.group(1),
                        SERVICE_STATE_MAPPING[result.group(1)])
            return SERVICE_STATE_MAPPING[result.group(1)]


def _is_expecting_event(event_recv_list):
    """ check for more event is expected in event list

    Args:
        event_recv_list: list of events
    Returns:
        result: True if more events are expected. False if not.
    """
    for state in event_recv_list:
        if state is False:
            return True
    return False


def _set_event_list(event_recv_list, sub_id_list, sub_id, value):
    """ set received event in expected event list

    Args:
        event_recv_list: list of received events
        sub_id_list: subscription ID list
        sub_id: subscription id of current event
        value: True or False
    Returns:
        None.
    """
    for i in range(len(sub_id_list)):
        if sub_id_list[i] == sub_id:
            event_recv_list[i] = value


def _wait_for_bluetooth_in_state(log, ad, state, max_wait):
    # FIXME: These event names should be defined in a common location
    _BLUETOOTH_STATE_ON_EVENT = 'BluetoothStateChangedOn'
    _BLUETOOTH_STATE_OFF_EVENT = 'BluetoothStateChangedOff'
    ad.ed.clear_events(_BLUETOOTH_STATE_ON_EVENT)
    ad.ed.clear_events(_BLUETOOTH_STATE_OFF_EVENT)

    ad.droid.bluetoothStartListeningForAdapterStateChange()
    try:
        bt_state = ad.droid.bluetoothCheckState()
        if bt_state == state:
            return True
        if max_wait <= 0:
            ad.log.error("Time out: bluetooth state still %s, expecting %s",
                         bt_state, state)
            return False

        event = {
            False: _BLUETOOTH_STATE_OFF_EVENT,
            True: _BLUETOOTH_STATE_ON_EVENT
        }[state]
        event = ad.ed.pop_event(event, max_wait)
        ad.log.info("Got event %s", event['name'])
        return True
    except Empty:
        ad.log.error("Time out: bluetooth state still in %s, expecting %s",
                     bt_state, state)
        return False
    finally:
        ad.droid.bluetoothStopListeningForAdapterStateChange()


# TODO: replace this with an event-based function
def _wait_for_wifi_in_state(log, ad, state, max_wait):
    return _wait_for_droid_in_state(log, ad, max_wait,
        lambda log, ad, state: \
                (True if ad.droid.wifiCheckState() == state else False),
                state)


def toggle_airplane_mode_msim(log, ad, new_state=None, strict_checking=True):
    """ Toggle the state of airplane mode.

    Args:
        log: log handler.
        ad: android_device object.
        new_state: Airplane mode state to set to.
            If None, opposite of the current state.
        strict_checking: Whether to turn on strict checking that checks all features.

    Returns:
        result: True if operation succeed. False if error happens.
    """

    cur_state = ad.droid.connectivityCheckAirplaneMode()
    if cur_state == new_state:
        ad.log.info("Airplane mode already in %s", new_state)
        return True
    elif new_state is None:
        new_state = not cur_state
        ad.log.info("Toggle APM mode, from current tate %s to %s", cur_state,
                    new_state)
    sub_id_list = []
    active_sub_info = ad.droid.subscriptionGetAllSubInfoList()
    if active_sub_info:
        for info in active_sub_info:
            sub_id_list.append(info['subscriptionId'])

    ad.ed.clear_all_events()
    time.sleep(0.1)
    service_state_list = []
    if new_state:
        service_state_list.append(SERVICE_STATE_POWER_OFF)
        ad.log.info("Turn on airplane mode")

    else:
        # If either one of these 3 events show up, it should be OK.
        # Normal SIM, phone in service
        service_state_list.append(SERVICE_STATE_IN_SERVICE)
        # NO SIM, or Dead SIM, or no Roaming coverage.
        service_state_list.append(SERVICE_STATE_OUT_OF_SERVICE)
        service_state_list.append(SERVICE_STATE_EMERGENCY_ONLY)
        ad.log.info("Turn off airplane mode")

    for sub_id in sub_id_list:
        ad.droid.telephonyStartTrackingServiceStateChangeForSubscription(
            sub_id)

    timeout_time = time.time() + MAX_WAIT_TIME_AIRPLANEMODE_EVENT
    ad.droid.connectivityToggleAirplaneMode(new_state)

    try:
        try:
            event = ad.ed.wait_for_event(
                EventServiceStateChanged,
                is_event_match_for_list,
                timeout=MAX_WAIT_TIME_AIRPLANEMODE_EVENT,
                field=ServiceStateContainer.SERVICE_STATE,
                value_list=service_state_list)
            ad.log.info("Got event %s", event)
        except Empty:
            ad.log.warning("Did not get expected service state change to %s",
                           service_state_list)
        finally:
            for sub_id in sub_id_list:
                ad.droid.telephonyStopTrackingServiceStateChangeForSubscription(
                    sub_id)
    except Exception as e:
        ad.log.error(e)

    # APM on (new_state=True) will turn off bluetooth but may not turn it on
    try:
        if new_state and not _wait_for_bluetooth_in_state(
                log, ad, False, timeout_time - time.time()):
            ad.log.error(
                "Failed waiting for bluetooth during airplane mode toggle")
            if strict_checking: return False
    except Exception as e:
        ad.log.error("Failed to check bluetooth state due to %s", e)
        if strict_checking:
            raise

    # APM on (new_state=True) will turn off wifi but may not turn it on
    if new_state and not _wait_for_wifi_in_state(log, ad, False,
                                                 timeout_time - time.time()):
        ad.log.error("Failed waiting for wifi during airplane mode toggle on")
        if strict_checking: return False

    if ad.droid.connectivityCheckAirplaneMode() != new_state:
        ad.log.error("Set airplane mode to %s failed", new_state)
        return False
    return True


def wait_for_cbrs_data_active_sub_change_event(
        ad,
        event_tracking_started=False,
        timeout=120):
    """Wait for an data change event on specified subscription.

    Args:
        ad: android device object.
        event_tracking_started: True if event tracking already state outside
        timeout: time to wait for event

    Returns:
        True: if data change event is received.
        False: if data change event is not received.
    """
    if not event_tracking_started:
        ad.ed.clear_events(EventActiveDataSubIdChanged)
        ad.droid.telephonyStartTrackingActiveDataChange()
    try:
        ad.ed.wait_for_event(
            EventActiveDataSubIdChanged,
            is_event_match,
            timeout=timeout)
        ad.log.info("Got event activedatasubidchanged")
    except Empty:
        ad.log.info("No event for data subid change")
        return False
    finally:
        if not event_tracking_started:
            ad.droid.telephonyStopTrackingActiveDataChange()
    return True


def is_current_data_on_cbrs(ad, cbrs_subid):
    """Verifies if current data sub is on CBRS

    Args:
        ad: android device object.
        cbrs_subid: sub_id against which we need to check

    Returns:
        True: if data is on cbrs
        False: if data is not on cbrs
    """
    if cbrs_subid is None:
        return False
    current_data = ad.droid.subscriptionGetActiveDataSubscriptionId()
    ad.log.info("Current Data subid %s cbrs_subid %s", current_data, cbrs_subid)
    if current_data == cbrs_subid:
        return True
    else:
        return False


def get_current_override_network_type(ad, timeout=30):
    """Returns current override network type

    Args:
        ad: android device object.
        timeout: max time to wait for event

    Returns:
        value: current override type
        -1: if no event received
    """
    override_value_list = [OverrideNetworkContainer.OVERRIDE_NETWORK_TYPE_NR_NSA,
                           OverrideNetworkContainer.OVERRIDE_NETWORK_TYPE_NONE,
                           OverrideNetworkContainer.OVERRIDE_NETWORK_TYPE_NR_MMWAVE,
                           OverrideNetworkContainer.OVERRIDE_NETWORK_TYPE_LTE_CA,
                           OverrideNetworkContainer.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO]
    ad.ed.clear_events(EventDisplayInfoChanged)
    ad.droid.telephonyStartTrackingDisplayInfoChange()
    try:
        event = ad.ed.wait_for_event(
                    EventDisplayInfoChanged,
                    is_event_match_for_list,
                    timeout=timeout,
                    field=DisplayInfoContainer.OVERRIDE,
                    value_list=override_value_list)
        override_type = event['data']['override']
        ad.log.info("Current Override Type is %s", override_type)
        return override_type
    except Empty:
        ad.log.info("No event for display info change")
        return -1
    finally:
        ad.droid.telephonyStopTrackingDisplayInfoChange()
    return -1


def _phone_number_remove_prefix(number):
    """Remove the country code and other prefix from the input phone number.
    Currently only handle phone number with the following formats:
        (US phone number format)
        +1abcxxxyyyy
        1abcxxxyyyy
        abcxxxyyyy
        abc xxx yyyy
        abc.xxx.yyyy
        abc-xxx-yyyy
        (EEUK phone number format)
        +44abcxxxyyyy
        0abcxxxyyyy

    Args:
        number: input phone number

    Returns:
        Phone number without country code or prefix
    """
    if number is None:
        return None, None
    for country_code in COUNTRY_CODE_LIST:
        if number.startswith(country_code):
            return number[len(country_code):], country_code
    if number[0] == "1" or number[0] == "0":
        return number[1:], None
    return number, None


def check_phone_number_match(number1, number2):
    """Check whether two input phone numbers match or not.

    Compare the two input phone numbers.
    If they match, return True; otherwise, return False.
    Currently only handle phone number with the following formats:
        (US phone number format)
        +1abcxxxyyyy
        1abcxxxyyyy
        abcxxxyyyy
        abc xxx yyyy
        abc.xxx.yyyy
        abc-xxx-yyyy
        (EEUK phone number format)
        +44abcxxxyyyy
        0abcxxxyyyy

        There are some scenarios we can not verify, one example is:
            number1 = +15555555555, number2 = 5555555555
            (number2 have no country code)

    Args:
        number1: 1st phone number to be compared.
        number2: 2nd phone number to be compared.

    Returns:
        True if two phone numbers match. Otherwise False.
    """
    number1 = phone_number_formatter(number1)
    number2 = phone_number_formatter(number2)
    # Handle extra country code attachment when matching phone number
    if number1[-7:] in number2 or number2[-7:] in number1:
        return True
    else:
        logging.info("phone number1 %s and number2 %s does not match" %
                     (number1, number2))
        return False


def get_call_state_by_adb(ad):
    slot_index_of_default_voice_subid = get_slot_index_from_subid(ad,
        get_incoming_voice_sub_id(ad))
    output = ad.adb.shell("dumpsys telephony.registry | grep mCallState")
    if "mCallState" in output:
        call_state_list = re.findall("mCallState=(\d)", output)
        if call_state_list:
            return call_state_list[slot_index_of_default_voice_subid]


def check_call_state_connected_by_adb(ad):
    return "2" in get_call_state_by_adb(ad)


def check_call_state_idle_by_adb(ad):
    return "0" in get_call_state_by_adb(ad)


def check_call_state_ring_by_adb(ad):
    return "1" in get_call_state_by_adb(ad)


def get_incoming_call_number_by_adb(ad):
    output = ad.adb.shell(
        "dumpsys telephony.registry | grep mCallIncomingNumber")
    return re.search(r"mCallIncomingNumber=(.*)", output).group(1)


def dumpsys_all_call_info(ad):
    """ Get call information by dumpsys telecom. """
    output = ad.adb.shell("dumpsys telecom")
    calls = re.findall("Call TC@\d+: {(.*?)}", output, re.DOTALL)
    calls_info = []
    for call in calls:
        call_info = {}
        for attr in ("startTime", "endTime", "direction", "isInterrupted",
                     "callTechnologies", "callTerminationsReason",
                     "connectionService", "isVideoCall", "callProperties"):
            match = re.search(r"%s: (.*)" % attr, call)
            if match:
                if attr in ("startTime", "endTime"):
                    call_info[attr] = epoch_to_log_line_timestamp(
                        int(match.group(1)))
                else:
                    call_info[attr] = match.group(1)
        call_info["inCallServices"] = re.findall(r"name: (.*)", call)
        calls_info.append(call_info)
    ad.log.debug("calls_info = %s", calls_info)
    return calls_info


def dumpsys_carrier_config(ad):
    output = ad.adb.shell("dumpsys carrier_config").split("\n")
    output_phone_id_0 = []
    output_phone_id_1 = []
    current_output = []
    for line in output:
        if "Phone Id = 0" in line:
            current_output = output_phone_id_0
        elif "Phone Id = 1" in line:
            current_output = output_phone_id_1
        current_output.append(line.strip())

    configs = {}
    if ad.adb.getprop("ro.build.version.release")[0] in ("9", "P"):
        phone_count = 1
        if "," in ad.adb.getprop("gsm.network.type"):
            phone_count = 2
    else:
        phone_count = ad.droid.telephonyGetPhoneCount()

    slot_0_subid = get_subid_from_logical_slot(ad, 0)
    if slot_0_subid != INVALID_SUB_ID:
        configs[slot_0_subid] = {}

    if phone_count == 2:
        slot_1_subid = get_subid_from_logical_slot(ad, 1)
        if slot_1_subid != INVALID_SUB_ID:
            configs[slot_1_subid] = {}

    attrs = [attr for attr in dir(CarrierConfigs) if not attr.startswith("__")]
    for attr in attrs:
        attr_string = getattr(CarrierConfigs, attr)
        values = re.findall(
            r"%s = (\S+)" % attr_string, "\n".join(output_phone_id_0))

        if slot_0_subid != INVALID_SUB_ID:
            if values:
                value = values[-1]
                if value == "true":
                    configs[slot_0_subid][attr_string] = True
                elif value == "false":
                    configs[slot_0_subid][attr_string] = False
                elif attr_string == CarrierConfigs.DEFAULT_WFC_IMS_MODE_INT:
                    if value == "0":
                        configs[slot_0_subid][attr_string] = WFC_MODE_WIFI_ONLY
                    elif value == "1":
                        configs[slot_0_subid][attr_string] = \
                            WFC_MODE_CELLULAR_PREFERRED
                    elif value == "2":
                        configs[slot_0_subid][attr_string] = \
                            WFC_MODE_WIFI_PREFERRED
                else:
                    try:
                        configs[slot_0_subid][attr_string] = int(value)
                    except Exception:
                        configs[slot_0_subid][attr_string] = value
            else:
                configs[slot_0_subid][attr_string] = None

        if phone_count == 2:
            if slot_1_subid != INVALID_SUB_ID:
                values = re.findall(
                    r"%s = (\S+)" % attr_string, "\n".join(output_phone_id_1))
                if values:
                    value = values[-1]
                    if value == "true":
                        configs[slot_1_subid][attr_string] = True
                    elif value == "false":
                        configs[slot_1_subid][attr_string] = False
                    elif attr_string == CarrierConfigs.DEFAULT_WFC_IMS_MODE_INT:
                        if value == "0":
                            configs[slot_1_subid][attr_string] = \
                                WFC_MODE_WIFI_ONLY
                        elif value == "1":
                            configs[slot_1_subid][attr_string] = \
                                WFC_MODE_CELLULAR_PREFERRED
                        elif value == "2":
                            configs[slot_1_subid][attr_string] = \
                                WFC_MODE_WIFI_PREFERRED
                    else:
                        try:
                            configs[slot_1_subid][attr_string] = int(value)
                        except Exception:
                            configs[slot_1_subid][attr_string] = value
                else:
                    configs[slot_1_subid][attr_string] = None
    return configs


def get_phone_capability(ad):
    carrier_configs = dumpsys_carrier_config(ad)
    for sub_id in carrier_configs:
        capabilities = []
        if carrier_configs[sub_id][CarrierConfigs.VOLTE_AVAILABLE_BOOL]:
            capabilities.append(CAPABILITY_VOLTE)
        if carrier_configs[sub_id][CarrierConfigs.WFC_IMS_AVAILABLE_BOOL]:
            capabilities.append(CAPABILITY_WFC)
        if carrier_configs[sub_id][CarrierConfigs.EDITABLE_WFC_MODE_BOOL]:
            capabilities.append(CAPABILITY_WFC_MODE_CHANGE)
        if carrier_configs[sub_id][CarrierConfigs.SUPPORT_CONFERENCE_CALL_BOOL]:
            capabilities.append(CAPABILITY_CONFERENCE)
        if carrier_configs[sub_id][CarrierConfigs.VT_AVAILABLE_BOOL]:
            capabilities.append(CAPABILITY_VT)
        if carrier_configs[sub_id][CarrierConfigs.VOLTE_PROVISIONED_BOOL]:
            capabilities.append(CAPABILITY_VOLTE_PROVISIONING)
        if carrier_configs[sub_id][CarrierConfigs.VOLTE_OVERRIDE_WFC_BOOL]:
            capabilities.append(CAPABILITY_VOLTE_OVERRIDE_WFC_PROVISIONING)
        if carrier_configs[sub_id][CarrierConfigs.HIDE_ENHANCED_4G_LTE_BOOL]:
            capabilities.append(CAPABILITY_HIDE_ENHANCED_4G_LTE_BOOL)

        ad.log.info("Capabilities of sub ID %s: %s", sub_id, capabilities)
        if not getattr(ad, 'telephony', {}):
            ad.telephony["subscription"] = {}
            ad.telephony["subscription"][sub_id] = {}
            setattr(
                ad.telephony["subscription"][sub_id],
                'capabilities', capabilities)

        else:
            ad.telephony["subscription"][sub_id]["capabilities"] = capabilities
        if CAPABILITY_WFC not in capabilities:
            wfc_modes = []
        else:
            if carrier_configs[sub_id].get(
                CarrierConfigs.EDITABLE_WFC_MODE_BOOL, False):
                wfc_modes = [
                    WFC_MODE_CELLULAR_PREFERRED,
                    WFC_MODE_WIFI_PREFERRED]
            else:
                wfc_modes = [
                    carrier_configs[sub_id].get(
                        CarrierConfigs.DEFAULT_WFC_IMS_MODE_INT,
                        WFC_MODE_CELLULAR_PREFERRED)
                ]
        if carrier_configs[sub_id].get(
            CarrierConfigs.WFC_SUPPORTS_WIFI_ONLY_BOOL,
            False) and WFC_MODE_WIFI_ONLY not in wfc_modes:
            wfc_modes.append(WFC_MODE_WIFI_ONLY)
        ad.telephony["subscription"][sub_id]["wfc_modes"] = wfc_modes
        if wfc_modes:
            ad.log.info("Supported WFC modes for sub ID %s: %s", sub_id,
                wfc_modes)


def get_capability_for_subscription(ad, capability, subid):
    if capability in ad.telephony["subscription"][subid].get(
        "capabilities", []):
        ad.log.info('Capability "%s" is available for sub ID %s.',
            capability, subid)
        return True
    else:
        ad.log.info('Capability "%s" is NOT available for sub ID %s.',
            capability, subid)
        return False


def phone_number_formatter(input_string, formatter=None):
    """Get expected format of input phone number string.

    Args:
        input_string: (string) input phone number.
            The input could be 10/11/12 digital, with or without " "/"-"/"."
        formatter: (int) expected format, this could be 7/10/11/12
            if formatter is 7: output string would be 7 digital number.
            if formatter is 10: output string would be 10 digital (standard) number.
            if formatter is 11: output string would be "1" + 10 digital number.
            if formatter is 12: output string would be "+1" + 10 digital number.

    Returns:
        If no error happen, return phone number in expected format.
        Else, return None.
    """
    if not input_string:
        return ""
    # make sure input_string is 10 digital
    # Remove white spaces, dashes, dots
    input_string = input_string.replace(" ", "").replace("-", "").replace(
        ".", "")

    # Remove a country code with '+' sign and add 0 for Japan/Korea Carriers.
    if (len(input_string) == 13
            and (input_string[0:3] == "+81" or input_string[0:3] == "+82")):
        input_string = "0" + input_string[3:]
        return input_string

    if not formatter:
        return input_string

    # Remove leading 0 for the phone with area code started with 0
    input_string = input_string.lstrip("0")

    # Remove "1"  or "+1"from front
    if (len(input_string) == PHONE_NUMBER_STRING_FORMAT_11_DIGIT
            and input_string[0] == "1"):
        input_string = input_string[1:]
    elif (len(input_string) == PHONE_NUMBER_STRING_FORMAT_12_DIGIT
          and input_string[0:2] == "+1"):
        input_string = input_string[2:]
    elif (len(input_string) == PHONE_NUMBER_STRING_FORMAT_7_DIGIT
          and formatter == PHONE_NUMBER_STRING_FORMAT_7_DIGIT):
        return input_string
    elif len(input_string) != PHONE_NUMBER_STRING_FORMAT_10_DIGIT:
        return None
    # change input_string according to format
    if formatter == PHONE_NUMBER_STRING_FORMAT_12_DIGIT:
        input_string = "+1" + input_string
    elif formatter == PHONE_NUMBER_STRING_FORMAT_11_DIGIT:
        input_string = "1" + input_string
    elif formatter == PHONE_NUMBER_STRING_FORMAT_10_DIGIT:
        input_string = input_string
    elif formatter == PHONE_NUMBER_STRING_FORMAT_7_DIGIT:
        input_string = input_string[3:]
    else:
        return None
    return input_string


def get_internet_connection_type(log, ad):
    """Get current active connection type name.

    Args:
        log: Log object.
        ad: Android Device Object.
    Returns:
        current active connection type name.
    """
    if not ad.droid.connectivityNetworkIsConnected():
        return 'none'
    return connection_type_from_type_string(
        ad.droid.connectivityNetworkGetActiveConnectionTypeName())


def verify_http_connection(log,
                           ad,
                           url="https://www.google.com",
                           retry=5,
                           retry_interval=15,
                           expected_state=True):
    """Make ping request and return status.

    Args:
        log: log object
        ad: Android Device Object.
        url: Optional. The ping request will be made to this URL.
            Default Value is "http://www.google.com/".

    """
    if not getattr(ad, "data_droid", None):
        ad.data_droid, ad.data_ed = ad.get_droid()
        ad.data_ed.start()
    else:
        try:
            if not ad.data_droid.is_live:
                ad.data_droid, ad.data_ed = ad.get_droid()
                ad.data_ed.start()
        except Exception:
            ad.log.info("Start new sl4a session for file download")
            ad.data_droid, ad.data_ed = ad.get_droid()
            ad.data_ed.start()
    for i in range(0, retry + 1):
        try:
            http_response = ad.data_droid.httpPing(url)
        except Exception as e:
            ad.log.info("httpPing with %s", e)
            http_response = None
        if (expected_state and http_response) or (not expected_state
                                                  and not http_response):
            ad.log.info("Http ping response for %s meet expected %s", url,
                        expected_state)
            return True
        if i < retry:
            time.sleep(retry_interval)
    ad.log.error("Http ping to %s is %s after %s second, expecting %s", url,
                 http_response, i * retry_interval, expected_state)
    return False


def _generate_file_directory_and_file_name(url, out_path):
    file_name = url.split("/")[-1]
    if not out_path:
        file_directory = "/sdcard/Download/"
    elif not out_path.endswith("/"):
        file_directory, file_name = os.path.split(out_path)
    else:
        file_directory = out_path
    return file_directory, file_name


def _check_file_existence(ad, file_path, expected_file_size=None):
    """Check file existance by file_path. If expected_file_size
       is provided, then also check if the file meet the file size requirement.
    """
    out = None
    try:
        out = ad.adb.shell('stat -c "%%s" %s' % file_path)
    except AdbError:
        pass
    # Handle some old version adb returns error message "No such" into std_out
    if out and "No such" not in out:
        if expected_file_size:
            file_size = int(out)
            if file_size >= expected_file_size:
                ad.log.info("File %s of size %s exists", file_path, file_size)
                return True
            else:
                ad.log.info("File %s is of size %s, does not meet expected %s",
                            file_path, file_size, expected_file_size)
                return False
        else:
            ad.log.info("File %s exists", file_path)
            return True
    else:
        ad.log.info("File %s does not exist.", file_path)
        return False


def verify_internet_connection_by_ping(log,
                                       ad,
                                       retries=1,
                                       expected_state=True,
                                       timeout=60):
    """Verify internet connection by ping test.

    Args:
        log: log object
        ad: Android Device Object.

    """
    begin_time = get_current_epoch_time()
    ip_addr = "54.230.144.105"
    for dest in ("www.google.com", "www.amazon.com", ip_addr):
        for i in range(retries):
            ad.log.info("Ping %s - attempt %d", dest, i + 1)
            result = adb_shell_ping(
                ad, count=5, timeout=timeout, loss_tolerance=40, dest_ip=dest)
            if result == expected_state:
                ad.log.info(
                    "Internet connection by pinging to %s is %s as expected",
                    dest, expected_state)
                if dest == ip_addr:
                    ad.log.warning("Suspect dns failure")
                    ad.log.info("DNS config: %s",
                                ad.adb.shell("getprop | grep dns").replace(
                                    "\n", " "))
                    return False
                return True
            else:
                ad.log.warning(
                    "Internet connection test by pinging %s is %s, expecting %s",
                    dest, result, expected_state)
                if get_current_epoch_time() - begin_time < timeout * 1000:
                    time.sleep(5)
    ad.log.error("Ping test doesn't meet expected %s", expected_state)
    return False


def verify_internet_connection(log, ad, retries=3, expected_state=True):
    """Verify internet connection by ping test and http connection.

    Args:
        log: log object
        ad: Android Device Object.

    """
    if ad.droid.connectivityNetworkIsConnected() != expected_state:
        ad.log.info("NetworkIsConnected = %s, expecting %s",
                    not expected_state, expected_state)
    if verify_internet_connection_by_ping(
            log, ad, retries=retries, expected_state=expected_state):
        return True
    for url in ("https://www.google.com", "https://www.amazon.com"):
        if verify_http_connection(
                log, ad, url=url, retry=retries,
                expected_state=expected_state):
            return True
    ad.log.info("DNS config: %s", " ".join(
        ad.adb.shell("getprop | grep dns").split()))
    ad.log.info("Interface info:\n%s", ad.adb.shell("ifconfig"))
    ad.log.info("NetworkAgentInfo: %s",
                ad.adb.shell("dumpsys connectivity | grep NetworkAgentInfo"))
    return False


def iperf_test_with_options(log,
                            ad,
                            iperf_server,
                            iperf_option,
                            timeout=180,
                            rate_dict=None,
                            blocking=True,
                            log_file_path=None):
    """iperf adb run helper.

    Args:
        log: log object
        ad: Android Device Object.
        iperf_server: The iperf host url".
        iperf_option: The options to pass to iperf client
        timeout: timeout for file download to complete.
        rate_dict: dictionary that can be passed in to save data
        blocking: run iperf in blocking mode if True
        log_file_path: location to save logs
    Returns:
        True if iperf runs without throwing an exception
    """
    try:
        if log_file_path:
            ad.adb.shell("rm %s" % log_file_path, ignore_status=True)
        ad.log.info("Running adb iperf test with server %s", iperf_server)
        ad.log.info("iperf options are %s", iperf_option)
        if not blocking:
            ad.run_iperf_client_nb(
                iperf_server,
                iperf_option,
                timeout=timeout + 60,
                log_file_path=log_file_path)
            return True
        result, data = ad.run_iperf_client(
            iperf_server, iperf_option, timeout=timeout + 120)
        ad.log.info("iperf test result with server %s is %s", iperf_server,
                    result)
        if result:
            iperf_str = ''.join(data)
            iperf_result = ipf.IPerfResult(iperf_str, 'None')
            if "-u" in iperf_option:
                udp_rate = iperf_result.avg_rate
                if udp_rate is None:
                    ad.log.warning(
                        "UDP rate is none, IPerf server returned error: %s",
                        iperf_result.error)
                ad.log.info("iperf3 UDP DL speed is %.6s Mbps", (udp_rate/1000000))
            else:
                tx_rate = iperf_result.avg_send_rate
                rx_rate = iperf_result.avg_receive_rate
                if (tx_rate or rx_rate) is None:
                    ad.log.warning(
                        "A TCP rate is none, iperf server returned error: %s",
                        iperf_result.error)
                ad.log.info(
                    "iperf3 TCP - UL speed is %.6s Mbps, DL speed is %.6s Mbps",
                    (tx_rate/1000000), (rx_rate/1000000))
            if rate_dict is not None:
                rate_dict["Uplink"] = tx_rate
                rate_dict["Downlink"] = rx_rate
        return result
    except AdbError as e:
        ad.log.warning("Fail to run iperf test with exception %s", e)
        raise


def iperf_udp_test_by_adb(log,
                          ad,
                          iperf_server,
                          port_num=None,
                          reverse=False,
                          timeout=180,
                          limit_rate=None,
                          pacing_timer=None,
                          omit=10,
                          ipv6=False,
                          rate_dict=None,
                          blocking=True,
                          log_file_path=None,
                          retry=5):
    """Iperf test by adb using UDP.

    Args:
        log: log object
        ad: Android Device Object.
        iperf_Server: The iperf host url".
        port_num: TCP/UDP server port
        reverse: whether to test download instead of upload
        timeout: timeout for file download to complete.
        limit_rate: iperf bandwidth option. None by default
        omit: the omit option provided in iperf command.
        ipv6: whether to run the test as ipv6
        rate_dict: dictionary that can be passed in to save data
        blocking: run iperf in blocking mode if True
        log_file_path: location to save logs
        retry: times of retry when the server is unavailable
    """
    iperf_option = "-u -i 1 -t %s -O %s -J" % (timeout, omit)
    if limit_rate:
        iperf_option += " -b %s" % limit_rate
    if pacing_timer:
        iperf_option += " --pacing-timer %s" % pacing_timer
    if ipv6:
        iperf_option += " -6"
    if reverse:
        iperf_option += " -R"
    for _ in range(retry):
        if port_num:
            iperf_option_final = iperf_option + " -p %s" % port_num
            port_num += 1
        else:
            iperf_option_final = iperf_option
        try:
            return iperf_test_with_options(log,
                                           ad,
                                           iperf_server,
                                           iperf_option_final,
                                           timeout,
                                           rate_dict,
                                           blocking,
                                           log_file_path)
        except (AdbCommandError, TimeoutError) as error:
            continue
        except AdbError:
            return False


def iperf_test_by_adb(log,
                      ad,
                      iperf_server,
                      port_num=None,
                      reverse=False,
                      timeout=180,
                      limit_rate=None,
                      omit=10,
                      ipv6=False,
                      rate_dict=None,
                      blocking=True,
                      log_file_path=None,
                      retry=5):
    """Iperf test by adb using TCP.

    Args:
        log: log object
        ad: Android Device Object.
        iperf_server: The iperf host url".
        port_num: TCP/UDP server port
        reverse: whether to test download instead of upload
        timeout: timeout for file download to complete.
        limit_rate: iperf bandwidth option. None by default
        omit: the omit option provided in iperf command.
        ipv6: whether to run the test as ipv6
        rate_dict: dictionary that can be passed in to save data
        blocking: run iperf in blocking mode if True
        log_file_path: location to save logs
        retry: times of retry when the server is unavailable
    """
    iperf_option = "-t %s -O %s -J" % (timeout, omit)
    if limit_rate:
        iperf_option += " -b %s" % limit_rate
    if ipv6:
        iperf_option += " -6"
    if reverse:
        iperf_option += " -R"
    for _ in range(retry):
        if port_num:
            iperf_option_final = iperf_option + " -p %s" % port_num
            port_num += 1
        else:
            iperf_option_final = iperf_option
        try:
            return iperf_test_with_options(log,
                                           ad,
                                           iperf_server,
                                           iperf_option_final,
                                           timeout,
                                           rate_dict=rate_dict,
                                           blocking=blocking,
                                           log_file_path=log_file_path)
        except (AdbCommandError, TimeoutError) as error:
            continue
        except AdbError:
            return False


def trigger_modem_crash(ad, timeout=120):
    cmd = "echo restart > /sys/kernel/debug/msm_subsys/modem"
    ad.log.info("Triggering Modem Crash from kernel using adb command %s", cmd)
    ad.adb.shell(cmd)
    time.sleep(timeout)
    return True


def trigger_modem_crash_by_modem(ad, timeout=120):
    begin_time = get_device_epoch_time(ad)
    ad.adb.shell(
        "setprop persist.vendor.sys.modem.diag.mdlog false",
        ignore_status=True)
    # Legacy pixels use persist.sys.modem.diag.mdlog.
    ad.adb.shell(
        "setprop persist.sys.modem.diag.mdlog false", ignore_status=True)
    disable_qxdm_logger(ad)
    cmd = ('am instrument -w -e request "4b 25 03 00" '
           '"com.google.mdstest/com.google.mdstest.instrument.'
           'ModemCommandInstrumentation"')
    ad.log.info("Crash modem by %s", cmd)
    ad.adb.shell(cmd, ignore_status=True)
    time.sleep(timeout)  # sleep time for sl4a stability
    reasons = ad.search_logcat("modem subsystem failure reason", begin_time)
    if reasons:
        ad.log.info("Modem crash is triggered successfully")
        ad.log.info(reasons[-1]["log_message"])
        return True
    else:
        ad.log.warning("There is no modem subsystem failure reason logcat")
        return False


def phone_switch_to_msim_mode(ad, retries=3, timeout=60):
    result = False
    if not ad.is_apk_installed("com.google.mdstest"):
        raise signals.TestAbortClass("mdstest is not installed")
    mode = ad.droid.telephonyGetPhoneCount()
    if mode == 2:
        ad.log.info("Device already in MSIM mode")
        return True
    for i in range(retries):
        ad.adb.shell(
        "setprop persist.vendor.sys.modem.diag.mdlog false", ignore_status=True)
        ad.adb.shell(
        "setprop persist.sys.modem.diag.mdlog false", ignore_status=True)
        disable_qxdm_logger(ad)
        cmd = ('am instrument -w -e request "WriteEFS" -e item '
               '"/google/pixel_multisim_config" -e data  "02 00 00 00" '
               '"com.google.mdstest/com.google.mdstest.instrument.'
               'ModemConfigInstrumentation"')
        ad.log.info("Switch to MSIM mode by using %s", cmd)
        ad.adb.shell(cmd, ignore_status=True)
        time.sleep(timeout)
        ad.adb.shell("setprop persist.radio.multisim.config dsds")
        reboot_device(ad)
        # Verify if device is really in msim mode
        mode = ad.droid.telephonyGetPhoneCount()
        if mode == 2:
            ad.log.info("Device correctly switched to MSIM mode")
            result = True
            if "Sprint" in ad.adb.getprop("gsm.sim.operator.alpha"):
                cmd = ('am instrument -w -e request "WriteEFS" -e item '
                       '"/google/pixel_dsds_imei_mapping_slot_record" -e data "03"'
                       ' "com.google.mdstest/com.google.mdstest.instrument.'
                       'ModemConfigInstrumentation"')
                ad.log.info("Switch Sprint to IMEI1 slot using %s", cmd)
                ad.adb.shell(cmd, ignore_status=True)
                time.sleep(timeout)
                reboot_device(ad)
            break
        else:
            ad.log.warning("Attempt %d - failed to switch to MSIM", (i + 1))
    return result


def phone_switch_to_ssim_mode(ad, retries=3, timeout=30):
    result = False
    if not ad.is_apk_installed("com.google.mdstest"):
        raise signals.TestAbortClass("mdstest is not installed")
    mode = ad.droid.telephonyGetPhoneCount()
    if mode == 1:
        ad.log.info("Device already in SSIM mode")
        return True
    for i in range(retries):
        ad.adb.shell(
        "setprop persist.vendor.sys.modem.diag.mdlog false", ignore_status=True)
        ad.adb.shell(
        "setprop persist.sys.modem.diag.mdlog false", ignore_status=True)
        disable_qxdm_logger(ad)
        cmds = ('am instrument -w -e request "WriteEFS" -e item '
                '"/google/pixel_multisim_config" -e data  "01 00 00 00" '
                '"com.google.mdstest/com.google.mdstest.instrument.'
                'ModemConfigInstrumentation"',
                'am instrument -w -e request "WriteEFS" -e item "/nv/item_files'
                '/modem/uim/uimdrv/uim_extended_slot_mapping_config" -e data '
                '"00 01 02 01" "com.google.mdstest/com.google.mdstest.'
                'instrument.ModemConfigInstrumentation"')
        for cmd in cmds:
            ad.log.info("Switch to SSIM mode by using %s", cmd)
            ad.adb.shell(cmd, ignore_status=True)
            time.sleep(timeout)
        ad.adb.shell("setprop persist.radio.multisim.config ssss")
        reboot_device(ad)
        # Verify if device is really in ssim mode
        mode = ad.droid.telephonyGetPhoneCount()
        if mode == 1:
            ad.log.info("Device correctly switched to SSIM mode")
            result = True
            break
        else:
            ad.log.warning("Attempt %d - failed to switch to SSIM", (i + 1))
    return result


def lock_lte_band_by_mds(ad, band):
    disable_qxdm_logger(ad)
    ad.log.info("Write band %s locking to efs file", band)
    if band == "4":
        item_string = (
            "4B 13 26 00 08 00 00 00 40 00 08 00 0B 00 08 00 00 00 00 00 00 00 "
            "2F 6E 76 2F 69 74 65 6D 5F 66 69 6C 65 73 2F 6D 6F 64 65 6D 2F 6D "
            "6D 6F 64 65 2F 6C 74 65 5F 62 61 6E 64 70 72 65 66 00")
    elif band == "13":
        item_string = (
            "4B 13 26 00 08 00 00 00 40 00 08 00 0A 00 00 10 00 00 00 00 00 00 "
            "2F 6E 76 2F 69 74 65 6D 5F 66 69 6C 65 73 2F 6D 6F 64 65 6D 2F 6D "
            "6D 6F 64 65 2F 6C 74 65 5F 62 61 6E 64 70 72 65 66 00")
    else:
        ad.log.error("Band %s is not supported", band)
        return False
    cmd = ('am instrument -w -e request "%s" com.google.mdstest/com.google.'
           'mdstest.instrument.ModemCommandInstrumentation')
    for _ in range(3):
        if "SUCCESS" in ad.adb.shell(cmd % item_string, ignore_status=True):
            break
    else:
        ad.log.error("Fail to write band by %s" % (cmd % item_string))
        return False

    # EFS Sync
    item_string = "4B 13 30 00 2A 00 2F 00"

    for _ in range(3):
        if "SUCCESS" in ad.adb.shell(cmd % item_string, ignore_status=True):
            break
    else:
        ad.log.error("Fail to sync efs by %s" % (cmd % item_string))
        return False
    time.sleep(5)
    reboot_device(ad)


def get_cell_data_roaming_state_by_adb(ad):
    """Get Cell Data Roaming state. True for enabled, False for disabled"""
    state_mapping = {"1": True, "0": False}
    return state_mapping[ad.adb.shell("settings get global data_roaming")]


def set_cell_data_roaming_state_by_adb(ad, state):
    """Set Cell Data Roaming state."""
    state_mapping = {True: "1", False: "0"}
    ad.log.info("Set data roaming to %s", state)
    ad.adb.shell("settings put global data_roaming %s" % state_mapping[state])


def toggle_cell_data_roaming(ad, state):
    """Enable cell data roaming for default data subscription.

    Wait for the data roaming status to be DATA_STATE_CONNECTED
        or DATA_STATE_DISCONNECTED.

    Args:
        log: Log object.
        ad: Android Device Object.
        state: True or False for enable or disable cell data roaming.

    Returns:
        True if success.
        False if failed.
    """
    state_int = {True: DATA_ROAMING_ENABLE, False: DATA_ROAMING_DISABLE}[state]
    action_str = {True: "Enable", False: "Disable"}[state]
    if ad.droid.connectivityCheckDataRoamingMode() == state:
        ad.log.info("Data roaming is already in state %s", state)
        return True
    if not ad.droid.connectivitySetDataRoaming(state_int):
        ad.error.info("Fail to config data roaming into state %s", state)
        return False
    if ad.droid.connectivityCheckDataRoamingMode() == state:
        ad.log.info("Data roaming is configured into state %s", state)
        return True
    else:
        ad.log.error("Data roaming is not configured into state %s", state)
        return False


def verify_incall_state(log, ads, expected_status):
    """Verify phones in incall state or not.

    Verify if all phones in the array <ads> are in <expected_status>.

    Args:
        log: Log object.
        ads: Array of Android Device Object. All droid in this array will be tested.
        expected_status: If True, verify all Phones in incall state.
            If False, verify all Phones not in incall state.

    """
    result = True
    for ad in ads:
        if ad.droid.telecomIsInCall() is not expected_status:
            ad.log.error("InCall status:%s, expected:%s",
                         ad.droid.telecomIsInCall(), expected_status)
            result = False
    return result


def verify_active_call_number(log, ad, expected_number):
    """Verify the number of current active call.

    Verify if the number of current active call in <ad> is
        equal to <expected_number>.

    Args:
        ad: Android Device Object.
        expected_number: Expected active call number.
    """
    calls = ad.droid.telecomCallGetCallIds()
    if calls is None:
        actual_number = 0
    else:
        actual_number = len(calls)
    if actual_number != expected_number:
        ad.log.error("Active Call number is %s, expecting", actual_number,
                     expected_number)
        return False
    return True


def num_active_calls(log, ad):
    """Get the count of current active calls.

    Args:
        log: Log object.
        ad: Android Device Object.

    Returns:
        Count of current active calls.
    """
    calls = ad.droid.telecomCallGetCallIds()
    return len(calls) if calls else 0


def get_carrier_provisioning_for_subscription(ad, feature_flag,
                                              tech, sub_id=None):
    """ Gets Provisioning Values for Subscription Id

    Args:
        ad: Android device object.
        sub_id: Subscription Id
        feature_flag: voice, video, ut, sms
        tech: wlan, wwan

    """
    try:
        if sub_id is None:
            sub_id = ad.droid.subscriptionGetDefaultVoiceSubId()
        result = ad.droid.imsMmTelIsSupported(sub_id, feature_flag, tech)
        ad.log.info("SubId %s - imsMmTelIsSupported for %s on %s - %s",
                    sub_id, feature_flag, tech, result)
        return result
    except Exception as e:
        ad.log.error(e)
        return False


def _wait_for_droid_in_state(log, ad, max_time, state_check_func, *args,
                             **kwargs):
    while max_time >= 0:
        if state_check_func(log, ad, *args, **kwargs):
            return True

        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
        max_time -= WAIT_TIME_BETWEEN_STATE_CHECK

    return False


def _wait_for_droid_in_state_for_subscription(
        log, ad, sub_id, max_time, state_check_func, *args, **kwargs):
    while max_time >= 0:
        if state_check_func(log, ad, sub_id, *args, **kwargs):
            return True

        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
        max_time -= WAIT_TIME_BETWEEN_STATE_CHECK

    return False


def _wait_for_droids_in_state(log, ads, max_time, state_check_func, *args,
                              **kwargs):
    while max_time > 0:
        success = True
        for ad in ads:
            if not state_check_func(log, ad, *args, **kwargs):
                success = False
                break
        if success:
            return True

        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
        max_time -= WAIT_TIME_BETWEEN_STATE_CHECK

    return False


def _is_attached(log, ad, voice_or_data):
    return _is_attached_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), voice_or_data)


def _is_attached_for_subscription(log, ad, sub_id, voice_or_data):
    rat = get_network_rat_for_subscription(log, ad, sub_id, voice_or_data)
    ad.log.info("Sub_id %s network RAT is %s for %s", sub_id, rat,
                voice_or_data)
    return rat != RAT_UNKNOWN


def is_voice_attached(log, ad):
    return _is_attached_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), NETWORK_SERVICE_VOICE)


def wait_for_data_attach(log, ad, max_time):
    """Wait for android device to attach on data.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        Return True if device attach data within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, _is_attached,
                                    NETWORK_SERVICE_DATA)


def wait_for_data_attach_for_subscription(log, ad, sub_id, max_time):
    """Wait for android device to attach on data in subscription id.

    Args:
        log: log object.
        ad:  android device.
        sub_id: subscription id.
        max_time: maximal wait time.

    Returns:
        Return True if device attach data within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state_for_subscription(
        log, ad, sub_id, max_time, _is_attached_for_subscription,
        NETWORK_SERVICE_DATA)


def get_phone_number(log, ad):
    """Get phone number for default subscription

    Args:
        log: log object.
        ad: Android device object.

    Returns:
        Phone number.
    """
    return get_phone_number_for_subscription(log, ad,
                                             get_outgoing_voice_sub_id(ad))


def get_phone_number_for_subscription(log, ad, subid):
    """Get phone number for subscription

    Args:
        log: log object.
        ad: Android device object.
        subid: subscription id.

    Returns:
        Phone number.
    """
    number = None
    try:
        number = ad.telephony['subscription'][subid]['phone_num']
    except KeyError:
        number = ad.droid.telephonyGetLine1NumberForSubscription(subid)
    return number


def set_phone_number(log, ad, phone_num):
    """Set phone number for default subscription

    Args:
        log: log object.
        ad: Android device object.
        phone_num: phone number string.

    Returns:
        True if success.
    """
    return set_phone_number_for_subscription(log, ad,
                                             get_outgoing_voice_sub_id(ad),
                                             phone_num)


def set_phone_number_for_subscription(log, ad, subid, phone_num):
    """Set phone number for subscription

    Args:
        log: log object.
        ad: Android device object.
        subid: subscription id.
        phone_num: phone number string.

    Returns:
        True if success.
    """
    try:
        ad.telephony['subscription'][subid]['phone_num'] = phone_num
    except Exception:
        return False
    return True


def get_operator_name(log, ad, subId=None):
    """Get operator name (e.g. vzw, tmo) of droid.

    Args:
        ad: Android device object.
        sub_id: subscription ID
            Optional, default is None

    Returns:
        Operator name.
    """
    try:
        if subId is not None:
            result = operator_name_from_plmn_id(
                ad.droid.telephonyGetNetworkOperatorForSubscription(subId))
        else:
            result = operator_name_from_plmn_id(
                ad.droid.telephonyGetNetworkOperator())
    except KeyError:
        try:
            if subId is not None:
                result = ad.droid.telephonyGetNetworkOperatorNameForSubscription(
                    subId)
            else:
                result = ad.droid.telephonyGetNetworkOperatorName()
            result = operator_name_from_network_name(result)
        except Exception:
            result = CARRIER_UNKNOWN
    ad.log.info("Operator Name is %s", result)
    return result


def get_model_name(ad):
    """Get android device model name

    Args:
        ad: Android device object

    Returns:
        model name string
    """
    # TODO: Create translate table.
    model = ad.model
    if (model.startswith(AOSP_PREFIX)):
        model = model[len(AOSP_PREFIX):]
    return model


def is_droid_in_rat_family(log, ad, rat_family, voice_or_data=None):
    return is_droid_in_rat_family_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), rat_family,
        voice_or_data)


def is_droid_in_rat_family_for_subscription(log,
                                            ad,
                                            sub_id,
                                            rat_family,
                                            voice_or_data=None):
    return is_droid_in_rat_family_list_for_subscription(
        log, ad, sub_id, [rat_family], voice_or_data)


def is_droid_in_rat_familiy_list(log, ad, rat_family_list, voice_or_data=None):
    return is_droid_in_rat_family_list_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), rat_family_list,
        voice_or_data)


def is_droid_in_rat_family_list_for_subscription(log,
                                                 ad,
                                                 sub_id,
                                                 rat_family_list,
                                                 voice_or_data=None):
    service_list = [NETWORK_SERVICE_DATA, NETWORK_SERVICE_VOICE]
    if voice_or_data:
        service_list = [voice_or_data]

    for service in service_list:
        nw_rat = get_network_rat_for_subscription(log, ad, sub_id, service)
        if nw_rat == RAT_UNKNOWN or not is_valid_rat(nw_rat):
            continue
        if rat_family_from_rat(nw_rat) in rat_family_list:
            return True
    return False


def is_droid_in_network_generation(log, ad, nw_gen, voice_or_data):
    """Checks if a droid in expected network generation ("2g", "3g" or "4g").

    Args:
        log: log object.
        ad: android device.
        nw_gen: expected generation "4g", "3g", "2g".
        voice_or_data: check voice network generation or data network generation
            This parameter is optional. If voice_or_data is None, then if
            either voice or data in expected generation, function will return True.

    Returns:
        True if droid in expected network generation. Otherwise False.
    """
    return is_droid_in_network_generation_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), nw_gen, voice_or_data)


def is_droid_in_network_generation_for_subscription(log, ad, sub_id, nw_gen,
                                                    voice_or_data):
    """Checks if a droid in expected network generation ("2g", "3g" or "4g").

    Args:
        log: log object.
        ad: android device.
        nw_gen: expected generation "4g", "3g", "2g".
        voice_or_data: check voice network generation or data network generation
            This parameter is optional. If voice_or_data is None, then if
            either voice or data in expected generation, function will return True.

    Returns:
        True if droid in expected network generation. Otherwise False.
    """
    service_list = [NETWORK_SERVICE_DATA, NETWORK_SERVICE_VOICE]

    if voice_or_data:
        service_list = [voice_or_data]

    for service in service_list:
        nw_rat = get_network_rat_for_subscription(log, ad, sub_id, service)
        ad.log.info("%s network rat is %s", service, nw_rat)
        if nw_rat == RAT_UNKNOWN or not is_valid_rat(nw_rat):
            continue

        if rat_generation_from_rat(nw_rat) == nw_gen:
            ad.log.info("%s network rat %s is expected %s", service, nw_rat,
                        nw_gen)
            return True
        else:
            ad.log.info("%s network rat %s is %s, does not meet expected %s",
                        service, nw_rat, rat_generation_from_rat(nw_rat),
                        nw_gen)
            return False

    return False


def get_network_rat(log, ad, voice_or_data):
    """Get current network type (Voice network type, or data network type)
       for default subscription id

    Args:
        ad: Android Device Object
        voice_or_data: Input parameter indicating to get voice network type or
            data network type.

    Returns:
        Current voice/data network type.
    """
    return get_network_rat_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), voice_or_data)


def get_network_rat_for_subscription(log, ad, sub_id, voice_or_data):
    """Get current network type (Voice network type, or data network type)
       for specified subscription id

    Args:
        ad: Android Device Object
        sub_id: subscription ID
        voice_or_data: Input parameter indicating to get voice network type or
            data network type.

    Returns:
        Current voice/data network type.
    """
    if voice_or_data == NETWORK_SERVICE_VOICE:
        ret_val = ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(
            sub_id)
    elif voice_or_data == NETWORK_SERVICE_DATA:
        ret_val = ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(
            sub_id)
    else:
        ret_val = ad.droid.telephonyGetNetworkTypeForSubscription(sub_id)

    if ret_val is None:
        log.error("get_network_rat(): Unexpected null return value")
        return RAT_UNKNOWN
    else:
        return ret_val


def get_network_gen(log, ad, voice_or_data):
    """Get current network generation string (Voice network type, or data network type)

    Args:
        ad: Android Device Object
        voice_or_data: Input parameter indicating to get voice network generation
            or data network generation.

    Returns:
        Current voice/data network generation.
    """
    return get_network_gen_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), voice_or_data)


def get_network_gen_for_subscription(log, ad, sub_id, voice_or_data):
    """Get current network generation string (Voice network type, or data network type)

    Args:
        ad: Android Device Object
        voice_or_data: Input parameter indicating to get voice network generation
            or data network generation.

    Returns:
        Current voice/data network generation.
    """
    try:
        return rat_generation_from_rat(
            get_network_rat_for_subscription(log, ad, sub_id, voice_or_data))
    except KeyError as e:
        ad.log.error("KeyError %s", e)
        return GEN_UNKNOWN


def check_voice_mail_count(log, ad, voice_mail_count_before,
                           voice_mail_count_after):
    """function to check if voice mail count is correct after leaving a new voice message.
    """
    return get_voice_mail_count_check_function(get_operator_name(log, ad))(
        voice_mail_count_before, voice_mail_count_after)


def get_voice_mail_number(log, ad):
    """function to get the voice mail number
    """
    voice_mail_number = get_voice_mail_check_number(get_operator_name(log, ad))
    if voice_mail_number is None:
        return get_phone_number(log, ad)
    return voice_mail_number


def reset_preferred_network_type_to_allowable_range(log, ad):
    """If preferred network type is not in allowable range, reset to GEN_4G
    preferred network type.

    Args:
        log: log object
        ad: android device object

    Returns:
        None
    """
    for sub_id, sub_info in ad.telephony["subscription"].items():
        current_preference = \
            ad.droid.telephonyGetPreferredNetworkTypesForSubscription(sub_id)
        ad.log.debug("sub_id network preference is %s", current_preference)
        try:
            if current_preference not in get_allowable_network_preference(
                    sub_info["operator"], sub_info["phone_type"]):
                network_preference = network_preference_for_generation(
                    GEN_4G, sub_info["operator"], sub_info["phone_type"])
                ad.droid.telephonySetPreferredNetworkTypesForSubscription(
                    network_preference, sub_id)
        except KeyError:
            pass


def set_phone_screen_on(log, ad, screen_on_time=MAX_SCREEN_ON_TIME):
    """Set phone screen on time.

    Args:
        log: Log object.
        ad: Android device object.
        screen_on_time: screen on time.
            This is optional, default value is MAX_SCREEN_ON_TIME.
    Returns:
        True if set successfully.
    """
    ad.droid.setScreenTimeout(screen_on_time)
    return screen_on_time == ad.droid.getScreenTimeout()


def set_phone_silent_mode(log, ad, silent_mode=True):
    """Set phone silent mode.

    Args:
        log: Log object.
        ad: Android device object.
        silent_mode: set phone silent or not.
            This is optional, default value is True (silent mode on).
    Returns:
        True if set successfully.
    """
    ad.droid.toggleRingerSilentMode(silent_mode)
    ad.droid.setMediaVolume(0)
    ad.droid.setVoiceCallVolume(0)
    ad.droid.setAlarmVolume(0)
    ad.adb.ensure_root()
    ad.adb.shell("setprop ro.audio.silent 1", ignore_status=True)
    ad.adb.shell("cmd notification set_dnd on", ignore_status=True)
    return silent_mode == ad.droid.checkRingerSilentMode()


def set_preferred_network_mode_pref(log,
                                    ad,
                                    sub_id,
                                    network_preference,
                                    timeout=WAIT_TIME_ANDROID_STATE_SETTLING):
    """Set Preferred Network Mode for Sub_id
    Args:
        log: Log object.
        ad: Android device object.
        sub_id: Subscription ID.
        network_preference: Network Mode Type
    """
    begin_time = get_device_epoch_time(ad)
    if ad.droid.telephonyGetPreferredNetworkTypesForSubscription(
            sub_id) == network_preference:
        ad.log.info("Current ModePref for Sub %s is in %s", sub_id,
                    network_preference)
        return True
    ad.log.info("Setting ModePref to %s for Sub %s", network_preference,
                sub_id)
    while timeout >= 0:
        if ad.droid.telephonySetPreferredNetworkTypesForSubscription(
                network_preference, sub_id):
            return True
        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
        timeout = timeout - WAIT_TIME_BETWEEN_STATE_CHECK
    error_msg = "Failed to set sub_id %s PreferredNetworkType to %s" % (
        sub_id, network_preference)
    search_results = ad.search_logcat(
        "REQUEST_SET_PREFERRED_NETWORK_TYPE error", begin_time=begin_time)
    if search_results:
        log_message = search_results[-1]["log_message"]
        if "DEVICE_IN_USE" in log_message:
            error_msg = "%s due to DEVICE_IN_USE" % error_msg
        else:
            error_msg = "%s due to %s" % (error_msg, log_message)
    ad.log.error(error_msg)
    return False


def set_call_state_listen_level(log, ad, value, sub_id):
    """Set call state listen level for subscription id.

    Args:
        log: Log object.
        ad: Android device object.
        value: True or False
        sub_id :Subscription ID.

    Returns:
        True or False
    """
    if sub_id == INVALID_SUB_ID:
        log.error("Invalid Subscription ID")
        return False
    ad.droid.telephonyAdjustPreciseCallStateListenLevelForSubscription(
        "Foreground", value, sub_id)
    ad.droid.telephonyAdjustPreciseCallStateListenLevelForSubscription(
        "Ringing", value, sub_id)
    ad.droid.telephonyAdjustPreciseCallStateListenLevelForSubscription(
        "Background", value, sub_id)
    return True


def is_event_match(event, field, value):
    """Return if <field> in "event" match <value> or not.

    Args:
        event: event to test. This event need to have <field>.
        field: field to match.
        value: value to match.

    Returns:
        True if <field> in "event" match <value>.
        False otherwise.
    """
    return is_event_match_for_list(event, field, [value])


def is_event_match_for_list(event, field, value_list):
    """Return if <field> in "event" match any one of the value
        in "value_list" or not.

    Args:
        event: event to test. This event need to have <field>.
        field: field to match.
        value_list: a list of value to match.

    Returns:
        True if <field> in "event" match one of the value in "value_list".
        False otherwise.
    """
    try:
        value_in_event = event['data'][field]
    except KeyError:
        return False
    for value in value_list:
        if value_in_event == value:
            return True
    return False


def is_network_call_back_event_match(event, network_callback_id,
                                     network_callback_event):
    try:
        return (
            (network_callback_id == event['data'][NetworkCallbackContainer.ID])
            and (network_callback_event == event['data']
                 [NetworkCallbackContainer.NETWORK_CALLBACK_EVENT]))
    except KeyError:
        return False


def is_build_id(log, ad, build_id):
    """Return if ad's build id is the same as input parameter build_id.

    Args:
        log: log object.
        ad: android device object.
        build_id: android build id.

    Returns:
        True if ad's build id is the same as input parameter build_id.
        False otherwise.
    """
    actual_bid = ad.droid.getBuildID()

    ad.log.info("BUILD DISPLAY: %s", ad.droid.getBuildDisplay())
    #In case we want to log more stuff/more granularity...
    #log.info("{} BUILD ID:{} ".format(ad.serial, ad.droid.getBuildID()))
    #log.info("{} BUILD FINGERPRINT: {} "
    # .format(ad.serial), ad.droid.getBuildFingerprint())
    #log.info("{} BUILD TYPE: {} "
    # .format(ad.serial), ad.droid.getBuildType())
    #log.info("{} BUILD NUMBER: {} "
    # .format(ad.serial), ad.droid.getBuildNumber())
    if actual_bid.upper() != build_id.upper():
        ad.log.error("%s: Incorrect Build ID", ad.model)
        return False
    return True


def is_uri_equivalent(uri1, uri2):
    """Check whether two input uris match or not.

    Compare Uris.
        If Uris are tel URI, it will only take the digit part
        and compare as phone number.
        Else, it will just do string compare.

    Args:
        uri1: 1st uri to be compared.
        uri2: 2nd uri to be compared.

    Returns:
        True if two uris match. Otherwise False.
    """

    #If either is None/empty we return false
    if not uri1 or not uri2:
        return False

    try:
        if uri1.startswith('tel:') and uri2.startswith('tel:'):
            uri1_number = get_number_from_tel_uri(uri1)
            uri2_number = get_number_from_tel_uri(uri2)
            return check_phone_number_match(uri1_number, uri2_number)
        else:
            return uri1 == uri2
    except AttributeError as e:
        return False


def get_call_uri(ad, call_id):
    """Get call's uri field.

    Get Uri for call_id in ad.

    Args:
        ad: android device object.
        call_id: the call id to get Uri from.

    Returns:
        call's Uri if call is active and have uri field. None otherwise.
    """
    try:
        call_detail = ad.droid.telecomCallGetDetails(call_id)
        return call_detail["Handle"]["Uri"]
    except:
        return None


def get_number_from_tel_uri(uri):
    """Get Uri number from tel uri

    Args:
        uri: input uri

    Returns:
        If input uri is tel uri, return the number part.
        else return None.
    """
    if uri.startswith('tel:'):
        uri_number = ''.join(
            i for i in urllib.parse.unquote(uri) if i.isdigit())
        return uri_number
    else:
        return None


def install_carriersettings_apk(ad, carriersettingsapk, skip_setup_wizard=True):
    """ Carrier Setting Installation Steps

    Pull sl4a apk from device. Terminate all sl4a sessions,
    Reboot the device to bootloader, wipe the device by fastboot.
    Reboot the device. wait for device to complete booting
    """
    status = True
    if carriersettingsapk is None:
        ad.log.warning("CarrierSettingsApk is not provided, aborting")
        return False
    ad.log.info("Push carriersettings apk to the Android device.")
    android_apk_path = "/product/priv-app/CarrierSettings/CarrierSettings.apk"
    ad.adb.push("%s %s" % (carriersettingsapk, android_apk_path))
    ad.stop_services()

    attempts = 3
    for i in range(1, attempts + 1):
        try:
            if ad.serial in list_adb_devices():
                ad.log.info("Reboot to bootloader")
                ad.adb.reboot("bootloader", ignore_status=True)
                time.sleep(30)
            if ad.serial in list_fastboot_devices():
                ad.log.info("Reboot in fastboot")
                ad.fastboot.reboot()
            ad.wait_for_boot_completion()
            ad.root_adb()
            if ad.is_sl4a_installed():
                break
            time.sleep(10)
            break
        except Exception as e:
            ad.log.warning(e)
            if i == attempts:
                abort_all_tests(log, str(e))
            time.sleep(5)
    try:
        ad.start_adb_logcat()
    except:
        ad.log.error("Failed to start adb logcat!")
    if skip_setup_wizard:
        ad.exit_setup_wizard()
    return status


def bring_up_sl4a(ad, attemps=3):
    for i in range(attemps):
        try:
            droid, ed = ad.get_droid()
            ed.start()
            ad.log.info("Brought up new sl4a session")
            break
        except Exception as e:
            if i < attemps - 1:
                ad.log.info(e)
                time.sleep(10)
            else:
                ad.log.error(e)
                raise


def reboot_device(ad, recover_sim_state=True):
    sim_state = is_sim_ready(ad.log, ad)
    ad.reboot()
    if ad.qxdm_log:
        start_qxdm_logger(ad)
    ad.unlock_screen()
    if recover_sim_state:
        if not unlock_sim(ad):
            ad.log.error("Unable to unlock SIM")
            return False
        if sim_state and not _wait_for_droid_in_state(
                log, ad, MAX_WAIT_TIME_FOR_STATE_CHANGE, is_sim_ready):
            ad.log.error("Sim state didn't reach pre-reboot ready state")
            return False
    return True


def unlocking_device(ad, device_password=None):
    """First unlock device attempt, required after reboot"""
    ad.unlock_screen(device_password)
    time.sleep(2)
    ad.adb.wait_for_device(timeout=180)
    if not ad.is_waiting_for_unlock_pin():
        return True
    else:
        ad.unlock_screen(device_password)
        time.sleep(2)
        ad.adb.wait_for_device(timeout=180)
        if ad.wait_for_window_ready():
            return True
    ad.log.error("Unable to unlock to user window")
    return False


def refresh_sl4a_session(ad):
    try:
        ad.droid.logI("Checking SL4A connection")
        ad.log.debug("Existing sl4a session is active")
        return True
    except Exception as e:
        ad.log.warning("Existing sl4a session is NOT active: %s", e)
    try:
        ad.terminate_all_sessions()
    except Exception as e:
        ad.log.info("terminate_all_sessions with error %s", e)
    ad.ensure_screen_on()
    ad.log.info("Open new sl4a connection")
    bring_up_sl4a(ad)


def get_sim_state(ad):
    try:
        state = ad.droid.telephonyGetSimState()
    except Exception as e:
        ad.log.error(e)
        state = ad.adb.getprop("gsm.sim.state")
    return state


def is_sim_locked(ad):
    return get_sim_state(ad) == SIM_STATE_PIN_REQUIRED


def is_sim_lock_enabled(ad):
    # TODO: add sl4a fascade to check if sim is locked
    return getattr(ad, "is_sim_locked", False)


def unlock_sim(ad):
    #The puk and pin can be provided in testbed config file.
    #"AndroidDevice": [{"serial": "84B5T15A29018214",
    #                   "adb_logcat_param": "-b all",
    #                   "puk": "12345678",
    #                   "puk_pin": "1234"}]
    if not is_sim_locked(ad):
        return True
    else:
        ad.is_sim_locked = True
    puk_pin = getattr(ad, "puk_pin", "1111")
    try:
        if not hasattr(ad, 'puk'):
            ad.log.info("Enter SIM pin code")
            ad.droid.telephonySupplyPin(puk_pin)
        else:
            ad.log.info("Enter PUK code and pin")
            ad.droid.telephonySupplyPuk(ad.puk, puk_pin)
    except:
        # if sl4a is not available, use adb command
        ad.unlock_screen(puk_pin)
        if is_sim_locked(ad):
            ad.unlock_screen(puk_pin)
    time.sleep(30)
    return not is_sim_locked(ad)


def send_dialer_secret_code(ad, secret_code):
    """Send dialer secret code.

    ad: android device controller
    secret_code: the secret code to be sent to dialer. the string between
                 code prefix *#*# and code postfix #*#*. *#*#<xxx>#*#*
    """
    action = 'android.provider.Telephony.SECRET_CODE'
    uri = 'android_secret_code://%s' % secret_code
    intent = ad.droid.makeIntent(
        action,
        uri,
        None,  # type
        None,  # extras
        None,  # categories,
        None,  # packagename,
        None,  # classname,
        0x01000000)  # flags
    ad.log.info('Issuing dialer secret dialer code: %s', secret_code)
    ad.droid.sendBroadcastIntent(intent)


def enable_radio_log_on(ad):
    if ad.adb.getprop("persist.vendor.radio.adb_log_on") != "1":
        ad.log.info("Enable radio adb_log_on and reboot")
        adb_disable_verity(ad)
        ad.adb.shell("setprop persist.vendor.radio.adb_log_on 1")
        reboot_device(ad)


def adb_disable_verity(ad):
    if ad.adb.getprop("ro.boot.veritymode") == "enforcing":
        ad.adb.disable_verity()
        reboot_device(ad)
        ad.adb.remount()


def recover_build_id(ad):
    build_fingerprint = ad.adb.getprop(
        "ro.vendor.build.fingerprint") or ad.adb.getprop(
            "ro.build.fingerprint")
    if not build_fingerprint:
        return
    build_id = build_fingerprint.split("/")[3]
    if ad.adb.getprop("ro.build.id") != build_id:
        build_id_override(ad, build_id)


def check_and_enable_privacy_usage_diagnostics(ad):
    try:
        ad.ensure_screen_on()
        ad.send_keycode('HOME')
    # open the UI page on which we need to enable the setting
        cmd = ('am start -n com.google.android.gms/com.google.android.gms.'
               'usagereporting.settings.UsageReportingActivity')
        ad.adb.shell(cmd)
    # perform the toggle using UI
        resource = {
        'resource_id': 'android:id/switch_widget',
        }
        node = ui_utils.wait_and_get_xml_node(ad,
                                        timeout=30,
                                        sibling=resource,
                                        text="Usage & diagnostics",
                                        resource_id="com.google.android.gms:id/switch_text")
        current_state = node.attributes['checked'].value

        if current_state == "false":
            ad.log.info("Enabling Usage & diagnostics")
            ui_utils.wait_and_click(ad,
                                    text="Usage & diagnostics",
                                    resource_id="com.google.android.gms:id/switch_text")
        else:
            ad.log.info("Usage & diagnostics is already enabled")

    except Exception:
        ad.log.info("Unable to toggle Usage and Diagnostics")


def build_id_override(ad, new_build_id=None, postfix=None):
    build_fingerprint = ad.adb.getprop(
        "ro.build.fingerprint") or ad.adb.getprop(
            "ro.vendor.build.fingerprint")
    if build_fingerprint:
        build_id = build_fingerprint.split("/")[3]
    else:
        build_id = None
    existing_build_id = ad.adb.getprop("ro.build.id")
    if postfix is not None and postfix in build_id:
        ad.log.info("Build id already contains %s", postfix)
        if postfix == 'STATIONARY_TEST':
            check_and_enable_privacy_usage_diagnostics(ad)
        return
    if not new_build_id:
        if postfix and build_id:
            new_build_id = "%s.%s" % (build_id, postfix)
    if not new_build_id or existing_build_id == new_build_id:
        return
    ad.log.info("Override build id %s with %s", existing_build_id,
                new_build_id)
    check_and_enable_privacy_usage_diagnostics(ad)
    adb_disable_verity(ad)
    ad.adb.remount()
    if "backup.prop" not in ad.adb.shell("ls /sdcard/"):
        ad.adb.shell("cp /system/build.prop /sdcard/backup.prop")
    ad.adb.shell("cat /system/build.prop | grep -v ro.build.id > /sdcard/test.prop")
    ad.adb.shell("echo ro.build.id=%s >> /sdcard/test.prop" % new_build_id)
    ad.adb.shell("cp /sdcard/test.prop /system/build.prop")
    reboot_device(ad)
    ad.log.info("ro.build.id = %s", ad.adb.getprop("ro.build.id"))


def enable_connectivity_metrics(ad):
    cmds = [
        "pm enable com.android.connectivity.metrics",
        "am startservice -a com.google.android.gms.usagereporting.OPTIN_UR",
        "am broadcast -a com.google.gservices.intent.action.GSERVICES_OVERRIDE"
        " -e usagestats:connectivity_metrics:enable_data_collection 1",
        "am broadcast -a com.google.gservices.intent.action.GSERVICES_OVERRIDE"
        " -e usagestats:connectivity_metrics:telephony_snapshot_period_millis 180000"
        # By default it turn on all modules
        #"am broadcast -a com.google.gservices.intent.action.GSERVICES_OVERRIDE"
        #" -e usagestats:connectivity_metrics:data_collection_bitmap 62"
    ]
    for cmd in cmds:
        ad.adb.shell(cmd, ignore_status=True)


def force_connectivity_metrics_upload(ad):
    cmd = "cmd jobscheduler run --force com.android.connectivity.metrics %s"
    for job_id in [2, 3, 5, 4, 1, 6]:
        ad.adb.shell(cmd % job_id, ignore_status=True)


def system_file_push(ad, src_file_path, dst_file_path):
    """Push system file on a device.

    Push system file need to change some system setting and remount.
    """
    cmd = "%s %s" % (src_file_path, dst_file_path)
    out = ad.adb.push(cmd, timeout=300, ignore_status=True)
    skip_sl4a = True if "sl4a.apk" in src_file_path else False
    if "Read-only file system" in out:
        ad.log.info("Change read-only file system")
        adb_disable_verity(ad)
        out = ad.adb.push(cmd, timeout=300, ignore_status=True)
        if "Read-only file system" in out:
            ad.reboot(skip_sl4a)
            out = ad.adb.push(cmd, timeout=300, ignore_status=True)
            if "error" in out:
                ad.log.error("%s failed with %s", cmd, out)
                return False
            else:
                ad.log.info("push %s succeed")
                if skip_sl4a: ad.reboot(skip_sl4a)
                return True
        else:
            return True
    elif "error" in out:
        return False
    else:
        return True


def set_preferred_apn_by_adb(ad, pref_apn_name):
    """Select Pref APN
       Set Preferred APN on UI using content query/insert
       It needs apn name as arg, and it will match with plmn id
    """
    try:
        plmn_id = get_plmn_by_adb(ad)
        out = ad.adb.shell("content query --uri content://telephony/carriers "
                           "--where \"apn='%s' and numeric='%s'\"" %
                           (pref_apn_name, plmn_id))
        if "No result found" in out:
            ad.log.warning("Cannot find APN %s on device", pref_apn_name)
            return False
        else:
            apn_id = re.search(r'_id=(\d+)', out).group(1)
            ad.log.info("APN ID is %s", apn_id)
            ad.adb.shell("content insert --uri content:"
                         "//telephony/carriers/preferapn --bind apn_id:i:%s" %
                         (apn_id))
            out = ad.adb.shell("content query --uri "
                               "content://telephony/carriers/preferapn")
            if "No result found" in out:
                ad.log.error("Failed to set prefer APN %s", pref_apn_name)
                return False
            elif apn_id == re.search(r'_id=(\d+)', out).group(1):
                ad.log.info("Preferred APN set to %s", pref_apn_name)
                return True
    except Exception as e:
        ad.log.error("Exception while setting pref apn %s", e)
        return True


def check_apm_mode_on_by_serial(ad, serial_id):
    try:
        apm_check_cmd = "|".join(("adb -s %s shell dumpsys wifi" % serial_id,
                                  "grep -i airplanemodeon", "cut -f2 -d ' '"))
        output = exe_cmd(apm_check_cmd)
        if output.decode("utf-8").split("\n")[0] == "true":
            return True
        else:
            return False
    except Exception as e:
        ad.log.warning("Exception during check apm mode on %s", e)
        return True


def set_apm_mode_on_by_serial(ad, serial_id):
    try:
        cmd1 = "adb -s %s shell settings put global airplane_mode_on 1" % serial_id
        cmd2 = "adb -s %s shell am broadcast -a android.intent.action.AIRPLANE_MODE" % serial_id
        exe_cmd(cmd1)
        exe_cmd(cmd2)
    except Exception as e:
        ad.log.warning("Exception during set apm mode on %s", e)
        return True


def print_radio_info(ad, extra_msg=""):
    for prop in ("gsm.version.baseband", "persist.radio.ver_info",
                 "persist.radio.cnv.ver_info"):
        output = ad.adb.getprop(prop)
        ad.log.info("%s%s = %s", extra_msg, prop, output)


def wait_for_state(state_check_func,
                   state,
                   max_wait_time=MAX_WAIT_TIME_FOR_STATE_CHANGE,
                   checking_interval=WAIT_TIME_BETWEEN_STATE_CHECK,
                   *args,
                   **kwargs):
    while max_wait_time >= 0:
        if state_check_func(*args, **kwargs) == state:
            return True
        time.sleep(checking_interval)
        max_wait_time -= checking_interval
    return False


def power_off_sim_by_adb(ad, sim_slot_id,
                         timeout=MAX_WAIT_TIME_FOR_STATE_CHANGE):
    """Disable pSIM/eSIM SUB by adb command.

    Args:
        ad: android device object.
        sim_slot_id: slot 0 or slot 1.
        timeout: wait time for state change.

    Returns:
        True if success, False otherwise.
    """
    release_version =  int(ad.adb.getprop("ro.build.version.release"))
    if sim_slot_id == 0 and release_version < 12:
        ad.log.error(
            "The disable pSIM SUB command only support for Android S or higher "
            "version, abort test.")
        raise signals.TestSkip(
            "The disable pSIM SUB command only support for Android S or higher "
            "version, abort test.")
    try:
        if sim_slot_id:
            ad.adb.shell("am broadcast -a android.telephony.euicc.action."
                "TEST_PROFILE -n com.google.android.euicc/com.android.euicc."
                "receiver.ProfileTestReceiver --es 'operation' 'switch' --ei "
                "'subscriptionId' -1")
        else:
            sub_id = get_subid_by_adb(ad, sim_slot_id)
            # The command only support for Android S. (b/159605922)
            ad.adb.shell(
                "cmd phone disable-physical-subscription %d" % sub_id)
    except Exception as e:
        ad.log.error(e)
        return False
    while timeout > 0:
        if get_subid_by_adb(ad, sim_slot_id) == INVALID_SUB_ID:
            return True
        timeout = timeout - WAIT_TIME_BETWEEN_STATE_CHECK
        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
    sim_state = ad.adb.getprop("gsm.sim.state").split(",")
    ad.log.warning("Fail to power off SIM slot %d, sim_state=%s",
        sim_slot_id, sim_state[sim_slot_id])
    return False


def change_slot(ad: AndroidDevice, sim_slot: Sequence[SimSlotInfo],
                timeout: int = MAX_WAIT_TIME_FOR_STATE_CHANGE) -> bool:
    """Enable Sims for Slot Mapping Mode.

    Args:
        ad: android device object.
        sim_slot: a list which contains 2 slots for logical slot 0 and 1.
            e.g. [SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1]
        timeout: wait time for state change.

    Returns:
        True if success, False otherwise.
    """
    if not getattr(ad, "mep", False): return

    port_id = [sim_slot[0].value[1], sim_slot[1].value[1]]
    phy_slot_id = [sim_slot[0].value[2], sim_slot[1].value[2]]
    ad.adb.shell(
        "am broadcast -a android.telephony.euicc.action.TEST_PROFILE "
        "-n com.google.android.euicc/com.android.euicc.receiver."
        "ProfileTestReceiver --es 'operation' 'changeSlot' --es "
        "'simSlotMapping' \"[{'port':%d,'physical':%d,'logical':0},{'port':%d,"
        "'physical':%d,'logical':1}]\"" % (port_id[0], phy_slot_id[0],
        port_id[1], phy_slot_id[1]))
    time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)

    while timeout > 0:
        sim_state = ad.adb.getprop("gsm.sim.state").split(",")
        if (get_subid_from_slot_index(
            ad.log, ad, sim_slot[0].value[0]) != INVALID_SUB_ID
            ) and (get_subid_from_slot_index(
                ad.log, ad, sim_slot[1].value[0]) != INVALID_SUB_ID):
            if (set(sim_state) - {
                SIM_STATE_UNKNOWN, SIM_STATE_ABSENT, SIM_STATE_NOT_READY}):
                get_phone_capability(ad)
                return True
        timeout = timeout - WAIT_TIME_BETWEEN_STATE_CHECK
        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)

    ad.log.warning("Fail to change SIM slot %s, sim_state=%s",
        sim_slot, sim_state)
    return False


def power_on_sim_by_adb(ad, sim_slot_id,
                         timeout=MAX_WAIT_TIME_FOR_STATE_CHANGE):
    """Enable pSIM/eSIM SUB by adb command.

    Args:
        ad: android device object.
        sim_slot_id: slot 0 or slot 1.
        timeout: wait time for state change.

    Returns:
        True if success, False otherwise.
    """
    release_version =  int(ad.adb.getprop("ro.build.version.release"))
    if sim_slot_id == 0 and release_version < 12:
        ad.log.error(
            "The enable pSIM SUB command only support for Android S or higher "
            "version, abort test.")
        raise signals.TestSkip(
            "The enable pSIM SUB command only support for Android S or higher "
            "version, abort test.")
    try:
        output = ad.adb.shell(
            "dumpsys isub | grep addSubInfoRecord | grep slotIndex=%d" %
            sim_slot_id)
        pattern = re.compile(r"subId=(\d+)")
        sub_id = pattern.findall(output)
        sub_id = int(sub_id[-1]) if sub_id else INVALID_SUB_ID
        if sim_slot_id:
            ad.adb.shell("am broadcast -a android.telephony.euicc.action."
                "TEST_PROFILE -n com.google.android.euicc/com.android.euicc."
                "receiver.ProfileTestReceiver --es 'operation' 'switch' --ei "
                "'subscriptionId' %d" % sub_id)
        else:
            # The command only support for Android S or higher. (b/159605922)
            ad.adb.shell(
                "cmd phone enable-physical-subscription %d" % sub_id)
    except Exception as e:
        ad.log.error(e)
        return False
    while timeout > 0:
        if get_subid_by_adb(ad, sim_slot_id) != INVALID_SUB_ID:
            return True
        timeout = timeout - WAIT_TIME_BETWEEN_STATE_CHECK
        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
    sim_state = ad.adb.getprop("gsm.sim.state").split(",")
    ad.log.warning("Fail to power on SIM slot %d, sim_state=%s",
        sim_slot_id, sim_state[sim_slot_id])
    return False


def power_off_sim(ad, sim_slot_id=None,
                  timeout=MAX_WAIT_TIME_FOR_STATE_CHANGE):
    try:
        if sim_slot_id is None:
            ad.droid.telephonySetSimPowerState(CARD_POWER_DOWN)
            verify_func = ad.droid.telephonyGetSimState
            verify_args = []
        else:
            ad.droid.telephonySetSimStateForSlotId(sim_slot_id,
                                                   CARD_POWER_DOWN)
            verify_func = ad.droid.telephonyGetSimStateForSlotId
            verify_args = [sim_slot_id]
    except Exception as e:
        ad.log.error(e)
        return False
    while timeout > 0:
        sim_state = verify_func(*verify_args)
        if sim_state in (
            SIM_STATE_UNKNOWN, SIM_STATE_ABSENT, SIM_STATE_NOT_READY):
            ad.log.info("SIM slot is powered off, SIM state is %s", sim_state)
            return True
        timeout = timeout - WAIT_TIME_BETWEEN_STATE_CHECK
        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
    ad.log.warning("Fail to power off SIM slot, sim_state=%s",
                   verify_func(*verify_args))
    return False


def power_on_sim(ad, sim_slot_id=None):
    try:
        if sim_slot_id is None:
            ad.droid.telephonySetSimPowerState(CARD_POWER_UP)
            verify_func = ad.droid.telephonyGetSimState
            verify_args = []
        else:
            ad.droid.telephonySetSimStateForSlotId(sim_slot_id, CARD_POWER_UP)
            verify_func = ad.droid.telephonyGetSimStateForSlotId
            verify_args = [sim_slot_id]
    except Exception as e:
        ad.log.error(e)
        return False
    if wait_for_state(verify_func, SIM_STATE_READY,
                      MAX_WAIT_TIME_FOR_STATE_CHANGE,
                      WAIT_TIME_BETWEEN_STATE_CHECK, *verify_args):
        ad.log.info("SIM slot is powered on, SIM state is READY")
        return True
    elif verify_func(*verify_args) == SIM_STATE_PIN_REQUIRED:
        ad.log.info("SIM is pin locked")
        return True
    else:
        ad.log.error("Fail to power on SIM slot")
        return False


def get_device_epoch_time(ad):
    return int(1000 * float(ad.adb.shell("date +%s.%N")))


def synchronize_device_time(ad):
    ad.adb.shell("put global auto_time 0", ignore_status=True)
    try:
        ad.adb.droid.setTime(get_current_epoch_time())
    except Exception:
        try:
            ad.adb.shell("date `date +%m%d%H%M%G.%S`")
        except Exception:
            pass
    try:
        ad.adb.shell(
            "am broadcast -a android.intent.action.TIME_SET",
            ignore_status=True)
    except Exception:
        pass


def revert_default_telephony_setting(ad):
    toggle_airplane_mode_by_adb(ad.log, ad, True)
    default_data_roaming = int(
        ad.adb.getprop("ro.com.android.dataroaming") == 'true')
    default_network_preference = int(
        ad.adb.getprop("ro.telephony.default_network"))
    ad.log.info("Default data roaming %s, network preference %s",
                default_data_roaming, default_network_preference)
    new_data_roaming = abs(default_data_roaming - 1)
    new_network_preference = abs(default_network_preference - 1)
    ad.log.info(
        "Set data roaming = %s, mobile data = 0, network preference = %s",
        new_data_roaming, new_network_preference)
    ad.adb.shell("settings put global mobile_data 0")
    ad.adb.shell("settings put global data_roaming %s" % new_data_roaming)
    ad.adb.shell("settings put global preferred_network_mode %s" %
                 new_network_preference)


def verify_default_telephony_setting(ad):
    ad.log.info("carrier_config: %s", dumpsys_carrier_config(ad))
    default_data_roaming = int(
        ad.adb.getprop("ro.com.android.dataroaming") == 'true')
    default_network_preference = int(
        ad.adb.getprop("ro.telephony.default_network"))
    ad.log.info("Default data roaming %s, network preference %s",
                default_data_roaming, default_network_preference)
    data_roaming = int(ad.adb.shell("settings get global data_roaming"))
    mobile_data = int(ad.adb.shell("settings get global mobile_data"))
    network_preference = int(
        ad.adb.shell("settings get global preferred_network_mode"))
    airplane_mode = int(ad.adb.shell("settings get global airplane_mode_on"))
    result = True
    ad.log.info("data_roaming = %s, mobile_data = %s, "
                "network_perference = %s, airplane_mode = %s", data_roaming,
                mobile_data, network_preference, airplane_mode)
    if airplane_mode:
        ad.log.error("Airplane mode is on")
        result = False
    if data_roaming != default_data_roaming:
        ad.log.error("Data roaming is %s, expecting %s", data_roaming,
                     default_data_roaming)
        result = False
    if not mobile_data:
        ad.log.error("Mobile data is off")
        result = False
    if network_preference != default_network_preference:
        ad.log.error("preferred_network_mode is %s, expecting %s",
                     network_preference, default_network_preference)
        result = False
    return result


def get_carrier_id_version(ad):
    out = ad.adb.shell("dumpsys activity service TelephonyDebugService | " \
                       "grep -i carrier_list_version")
    if out and ":" in out:
        version = out.split(':')[1].lstrip()
    else:
        version = "0"
    ad.log.debug("Carrier Config Version is %s", version)
    return version


def get_carrier_config_version(ad):
    out = ad.adb.shell("dumpsys carrier_config | grep version_string")
    if out and "-" in out:
        version = out.split('-')[1]
    else:
        version = "0"
    ad.log.debug("Carrier Config Version is %s", version)
    return version


def get_er_db_id_version(ad):
    out = ad.adb.shell("dumpsys activity service TelephonyDebugService | \
                        grep -i \"Database Version\"")
    if out and ":" in out:
        version = out.split(':', 2)[2].lstrip()
    else:
        version = "0"
    ad.log.debug("Emergency database Version is %s", version)
    return version

def get_database_content(ad):
    out = ad.adb.shell("dumpsys activity service TelephonyDebugService | \
                        egrep -i \EmergencyNumber:Number-54321")
    if out:
        return True
    result = ad.adb.shell(r"dumpsys activity service TelephonyDebugService | \
                egrep -i \updateOtaEmergencyNumberListDatabaseAndNotify")
    ad.log.error("Emergency Number is incorrect. %s ", result)
    return False


def add_whitelisted_account(ad, user_account,user_password, retries=3):
    if not ad.is_apk_installed("com.google.android.tradefed.account"):
        ad.log.error("GoogleAccountUtil is not installed")
        return False
    for _ in range(retries):
        ad.ensure_screen_on()
        output = ad.adb.shell(
            'am instrument -w -e account "%s@gmail.com" -e password '
            '"%s" -e sync true -e wait-for-checkin false '
            'com.google.android.tradefed.account/.AddAccount' %
            (user_account, user_password))
        if "result=SUCCESS" in output:
            ad.log.info("Google account is added successfully")
            return True
    ad.log.error("Failed to add google account - %s", output)
    return False

def install_apk(ad, apk_path, app_package_name):
    """Install assigned apk to specific device.

    Args:
        ad: android device object
        apk_path: The path of apk (please refer to the "Resources" section in
            go/mhbe-resources for supported file stores.)
        app_package_name: package name of the application

    Returns:
        True if success, False if fail.
    """
    ad.log.info("Install %s from %s", app_package_name, apk_path)
    ad.adb.install("-r -g %s" % apk_path, timeout=300, ignore_status=True)
    time.sleep(3)
    if not ad.is_apk_installed(app_package_name):
        ad.log.info("%s is not installed.", app_package_name)
        return False
    if ad.get_apk_version(app_package_name):
        ad.log.info("Current version of %s: %s", app_package_name,
                    ad.get_apk_version(app_package_name))
    return True

def install_dialer_apk(ad, dialer_util):
    """Install dialer.apk to specific device.

    Args:
        ad: android device object.
        dialer_util: path of dialer.apk

    Returns:
        True if success, False if fail.
    """
    ad.log.info("Install dialer_util %s", dialer_util)
    ad.adb.install("-r -g %s" % dialer_util, timeout=300, ignore_status=True)
    time.sleep(3)
    if not ad.is_apk_installed(DIALER_PACKAGE_NAME):
        ad.log.info("%s is not installed", DIALER_PACKAGE_NAME)
        return False
    if ad.get_apk_version(DIALER_PACKAGE_NAME):
        ad.log.info("Current version of %s: %s", DIALER_PACKAGE_NAME,
                    ad.get_apk_version(DIALER_PACKAGE_NAME))
    return True


def install_message_apk(ad, message_util):
    """Install message.apk to specific device.

    Args:
        ad: android device object.
        message_util: path of message.apk

    Returns:
        True if success, False if fail.
    """
    ad.log.info("Install message_util %s", message_util)
    ad.adb.install("-r -g %s" % message_util, timeout=300, ignore_status=True)
    time.sleep(3)
    if not ad.is_apk_installed(MESSAGE_PACKAGE_NAME):
        ad.log.info("%s is not installed", MESSAGE_PACKAGE_NAME)
        return False
    if ad.get_apk_version(MESSAGE_PACKAGE_NAME):
        ad.log.info("Current version of %s: %s", MESSAGE_PACKAGE_NAME,
                    ad.get_apk_version(MESSAGE_PACKAGE_NAME))
    return True


def install_googleaccountutil_apk(ad, account_util):
    ad.log.info("Install account_util %s", account_util)
    ad.ensure_screen_on()
    ad.adb.install("-r %s" % account_util, timeout=300, ignore_status=True)
    time.sleep(3)
    if not ad.is_apk_installed("com.google.android.tradefed.account"):
        ad.log.info("com.google.android.tradefed.account is not installed")
        return False
    return True


def install_googlefi_apk(ad, fi_util):
    ad.log.info("Install fi_util %s", fi_util)
    ad.ensure_screen_on()
    ad.adb.install("-r -g --user 0 %s" % fi_util,
                   timeout=300, ignore_status=True)
    time.sleep(3)
    if not check_fi_apk_installed(ad):
        return False
    return True


def check_fi_apk_installed(ad):
    if not ad.is_apk_installed("com.google.android.apps.tycho"):
        ad.log.warning("com.google.android.apps.tycho is not installed")
        return False
    return True


def add_google_account(ad, retries=3):
    if not ad.is_apk_installed("com.google.android.tradefed.account"):
        ad.log.error("GoogleAccountUtil is not installed")
        return False
    for _ in range(retries):
        ad.ensure_screen_on()
        output = ad.adb.shell(
            'am instrument -w -e account "%s@gmail.com" -e password '
            '"%s" -e sync true -e wait-for-checkin false '
            'com.google.android.tradefed.account/.AddAccount' %
            (ad.user_account, ad.user_password))
        if "result=SUCCESS" in output:
            ad.log.info("Google account is added successfully")
            return True
    ad.log.error("Failed to add google account - %s", output)
    return False


def remove_google_account(ad, retries=3):
    if not ad.is_apk_installed("com.google.android.tradefed.account"):
        ad.log.error("GoogleAccountUtil is not installed")
        return False
    for _ in range(retries):
        ad.ensure_screen_on()
        output = ad.adb.shell(
            'am instrument -w '
            'com.google.android.tradefed.account/.RemoveAccounts')
        if "result=SUCCESS" in output:
            ad.log.info("google account is removed successfully")
            return True
    ad.log.error("Fail to remove google account due to %s", output)
    return False


def my_current_screen_content(ad, content):
    ad.adb.shell("uiautomator dump --window=WINDOW")
    out = ad.adb.shell("cat /sdcard/window_dump.xml | grep -E '%s'" % content)
    if not out:
        ad.log.warning("NOT FOUND - %s", content)
        return False
    return True


def activate_esim_using_suw(ad):
    _START_SUW = ('am start -a android.intent.action.MAIN -n '
                  'com.google.android.setupwizard/.SetupWizardTestActivity')
    _STOP_SUW = ('am start -a com.android.setupwizard.EXIT')

    toggle_airplane_mode(ad.log, ad, new_state=False, strict_checking=False)
    ad.adb.shell("settings put system screen_off_timeout 1800000")
    ad.ensure_screen_on()
    ad.send_keycode("MENU")
    ad.send_keycode("HOME")
    for _ in range(3):
        ad.log.info("Attempt %d - activating eSIM", (_ + 1))
        ad.adb.shell(_START_SUW)
        time.sleep(10)
        log_screen_shot(ad, "start_suw")
        for _ in range(4):
            ad.send_keycode("TAB")
            time.sleep(0.5)
        ad.send_keycode("ENTER")
        time.sleep(15)
        log_screen_shot(ad, "activate_esim")
        get_screen_shot_log(ad)
        ad.adb.shell(_STOP_SUW)
        time.sleep(5)
        current_sim = get_sim_state(ad)
        ad.log.info("Current SIM status is %s", current_sim)
        if current_sim not in (SIM_STATE_ABSENT, SIM_STATE_UNKNOWN):
            break
    return True


def activate_google_fi_account(ad, retries=10):
    _FI_APK = "com.google.android.apps.tycho"
    _FI_ACTIVATE_CMD = ('am start -c android.intent.category.DEFAULT -n '
                        'com.google.android.apps.tycho/.AccountDetailsActivity --ez '
                        'in_setup_wizard false --ez force_show_account_chooser '
                        'false')
    toggle_airplane_mode(ad.log, ad, new_state=False, strict_checking=False)
    ad.adb.shell("settings put system screen_off_timeout 1800000")
    page_match_dict = {
       "SelectAccount" : "Choose an account to use",
       "Setup" : "Activate Google Fi to use your device for calls",
       "Switch" : "Switch to the Google Fi mobile network",
       "WiFi" : "Fi to download your SIM",
       "Connect" : "Connect to the Google Fi mobile network",
       "Move" : "Move number",
       "Data" : "first turn on mobile data",
       "Activate" : "This takes a minute or two, sometimes longer",
       "Welcome" : "Welcome to Google Fi",
       "Account" : "Your current cycle ends in"
    }
    page_list = ["Account", "Setup", "WiFi", "Switch", "Connect",
                 "Activate", "Move", "Welcome", "Data"]
    for _ in range(retries):
        ad.force_stop_apk(_FI_APK)
        ad.ensure_screen_on()
        ad.send_keycode("MENU")
        ad.send_keycode("HOME")
        ad.adb.shell(_FI_ACTIVATE_CMD)
        time.sleep(15)
        for page in page_list:
            if my_current_screen_content(ad, page_match_dict[page]):
                ad.log.info("Ready for Step %s", page)
                log_screen_shot(ad, "fi_activation_step_%s" % page)
                if page in ("Setup", "Switch", "Connect", "WiFi"):
                    ad.send_keycode("TAB")
                    ad.send_keycode("TAB")
                    ad.send_keycode("ENTER")
                    time.sleep(30)
                elif page == "Move" or page == "SelectAccount":
                    ad.send_keycode("TAB")
                    ad.send_keycode("ENTER")
                    time.sleep(5)
                elif page == "Welcome":
                    ad.send_keycode("TAB")
                    ad.send_keycode("TAB")
                    ad.send_keycode("TAB")
                    ad.send_keycode("ENTER")
                    ad.log.info("Activation SUCCESS using Fi App")
                    time.sleep(5)
                    ad.send_keycode("TAB")
                    ad.send_keycode("TAB")
                    ad.send_keycode("ENTER")
                    return True
                elif page == "Activate":
                    time.sleep(60)
                    if my_current_screen_content(ad, page_match_dict[page]):
                        time.sleep(60)
                elif page == "Account":
                    return True
                elif page == "Data":
                    ad.log.error("Mobile Data is turned OFF by default")
                    ad.send_keycode("TAB")
                    ad.send_keycode("TAB")
                    ad.send_keycode("ENTER")
            else:
                ad.log.info("NOT FOUND - Page %s", page)
                log_screen_shot(ad, "fi_activation_step_%s_failure" % page)
                get_screen_shot_log(ad)
    return False


def check_google_fi_activated(ad, retries=20):
    if check_fi_apk_installed(ad):
        _FI_APK = "com.google.android.apps.tycho"
        _FI_LAUNCH_CMD = ("am start -n %s/%s.AccountDetailsActivity" \
                          % (_FI_APK, _FI_APK))
        toggle_airplane_mode(ad.log, ad, new_state=False, strict_checking=False)
        ad.adb.shell("settings put system screen_off_timeout 1800000")
        ad.force_stop_apk(_FI_APK)
        ad.ensure_screen_on()
        ad.send_keycode("HOME")
        ad.adb.shell(_FI_LAUNCH_CMD)
        time.sleep(10)
        if not my_current_screen_content(ad, "Your current cycle ends in"):
            ad.log.warning("Fi is not activated")
            return False
        ad.send_keycode("HOME")
        return True
    else:
        ad.log.info("Fi Apk is not yet installed")
        return False


def cleanup_configupdater(ad):
    cmds = ('rm -rf /data/data/com.google.android.configupdater/shared_prefs',
            'rm /data/misc/carrierid/carrier_list.pb',
            'setprop persist.telephony.test.carrierid.ota true',
            'rm /data/user_de/0/com.android.providers.telephony/shared_prefs'
            '/CarrierIdProvider.xml')
    for cmd in cmds:
        ad.log.info("Cleanup ConfigUpdater - %s", cmd)
        ad.adb.shell(cmd, ignore_status=True)


def pull_carrier_id_files(ad, carrier_id_path):
    os.makedirs(carrier_id_path, exist_ok=True)
    ad.log.info("Pull CarrierId Files")
    cmds = ('/data/data/com.google.android.configupdater/shared_prefs/',
            '/data/misc/carrierid/',
            '/data/user_de/0/com.android.providers.telephony/shared_prefs/',
            '/data/data/com.android.providers.downloads/databases/downloads.db')
    for cmd in cmds:
        cmd = cmd + " %s" % carrier_id_path
        ad.adb.pull(cmd, timeout=30, ignore_status=True)


def bring_up_connectivity_monitor(ad):
    monitor_apk = None
    for apk in ("com.google.telephonymonitor",
                "com.google.android.connectivitymonitor"):
        if ad.is_apk_installed(apk):
            ad.log.info("apk %s is installed", apk)
            monitor_apk = apk
            break
    if not monitor_apk:
        ad.log.info("ConnectivityMonitor|TelephonyMonitor is not installed")
        return False
    toggle_connectivity_monitor_setting(ad, True)

    if not ad.is_apk_running(monitor_apk):
        ad.log.info("%s is not running", monitor_apk)
        # Reboot
        ad.log.info("reboot to bring up %s", monitor_apk)
        reboot_device(ad)
        for i in range(30):
            if ad.is_apk_running(monitor_apk):
                ad.log.info("%s is running after reboot", monitor_apk)
                return True
            else:
                ad.log.info(
                    "%s is not running after reboot. Wait and check again",
                    monitor_apk)
                time.sleep(30)
        ad.log.error("%s is not running after reboot", monitor_apk)
        return False
    else:
        ad.log.info("%s is running", monitor_apk)
        return True


def get_host_ip_address(ad):
    cmd = "|".join(("ifconfig", "grep eno1 -A1", "grep inet", "awk '{$1=$1};1'", "cut -d ' ' -f 2"))
    destination_ip = exe_cmd(cmd)
    destination_ip = (destination_ip.decode("utf-8")).split("\n")[0]
    ad.log.info("Host IP is %s", destination_ip)
    return destination_ip


def load_scone_cat_simulate_data(ad, simulate_data, sub_id=None):
    """ Load radio simulate data
    ad: android device controller
    simulate_data: JSON object of simulate data
    sub_id: RIL sub id, should be 0 or 1
    """
    ad.log.info("load_scone_cat_simulate_data")

    #Check RIL sub id
    if sub_id is None or sub_id > 1:
        ad.log.error("The value of RIL sub_id should be 0 or 1")
        return False

    action = "com.google.android.apps.scone.cat.action.SetSimulateData"

    #add sub id
    simulate_data["SubId"] = sub_id
    try:
        #dump json
        extra = json.dumps(simulate_data)
        ad.log.info("send simulate_data=[%s]" % extra)
        #send data
        ad.adb.shell("am broadcast -a " + action + " --es simulate_data '" + extra + "'")
    except Exception as e:
        ad.log.error("Exception error to send CAT: %s", e)
        return False

    return True


def load_scone_cat_data_from_file(ad, simulate_file_path, sub_id=None):
    """ Load radio simulate data
    ad: android device controller
    simulate_file_path: JSON file of simulate data
    sub_id: RIL sub id, should be 0 or 1
    """
    ad.log.info("load_radio_simulate_data_from_file from %s" % simulate_file_path)
    radio_simulate_data = {}

    #Check RIL sub id
    if sub_id is None or sub_id > 1:
        ad.log.error("The value of RIL sub_id should be 0 or 1")
        raise ValueError

    with open(simulate_file_path, 'r') as f:
        try:
            radio_simulate_data = json.load(f)
        except Exception as e:
            ad.log.error("Exception error to load %s: %s", f, e)
            return False

    for item in radio_simulate_data:
        result = load_scone_cat_simulate_data(ad, item, sub_id)
        if result == False:
            ad.log.error("Load CAT command fail")
            return False
        time.sleep(0.1)

    return True


def toggle_connectivity_monitor_setting(ad, state=True):
    monitor_setting = ad.adb.getprop("persist.radio.enable_tel_mon")
    ad.log.info("radio.enable_tel_mon setting is %s", monitor_setting)
    current_state = True if monitor_setting == "user_enabled" else False
    if current_state == state:
        return True
    elif state is None:
        state = not current_state
    expected_monitor_setting = "user_enabled" if state else "disabled"
    cmd = "setprop persist.radio.enable_tel_mon %s" % expected_monitor_setting
    ad.log.info("Toggle connectivity monitor by %s", cmd)
    ad.adb.shell(
        "am start -n com.android.settings/.DevelopmentSettings",
        ignore_status=True)
    ad.adb.shell(cmd)
    monitor_setting = ad.adb.getprop("persist.radio.enable_tel_mon")
    ad.log.info("radio.enable_tel_mon setting is %s", monitor_setting)
    return monitor_setting == expected_monitor_setting


def get_rx_tx_power_levels(log, ad):
    """ Obtains Rx and Tx power levels from the MDS application.

    The method requires the MDS app to be installed in the DUT.

    Args:
        log: logger object
        ad: an android device

    Return:
        A tuple where the first element is an array array with the RSRP value
        in Rx chain, and the second element is the transmitted power in dBm.
        Values for invalid Rx / Tx chains are set to None.
    """
    cmd = ('am instrument -w -e request "80 00 e8 03 00 08 00 00 00" -e '
           'response wait "com.google.mdstest/com.google.mdstest.instrument.'
           'ModemCommandInstrumentation"')
    output = ad.adb.shell(cmd)

    if 'result=SUCCESS' not in output:
        raise RuntimeError('Could not obtain Tx/Rx power levels from MDS. Is '
                           'the MDS app installed?')

    response = re.search(r"(?<=response=).+", output)

    if not response:
        raise RuntimeError('Invalid response from the MDS app:\n' + output)

    # Obtain a list of bytes in hex format from the response string
    response_hex = response.group(0).split(' ')

    def get_bool(pos):
        """ Obtain a boolean variable from the byte array. """
        return response_hex[pos] == '01'

    def get_int32(pos):
        """ Obtain an int from the byte array. Bytes are printed in
        little endian format."""
        return struct.unpack(
            '<i', bytearray.fromhex(''.join(response_hex[pos:pos + 4])))[0]

    rx_power = []
    RX_CHAINS = 4

    for i in range(RX_CHAINS):
        # Calculate starting position for the Rx chain data structure
        start = 12 + i * 22

        # The first byte in the data structure indicates if the rx chain is
        # valid.
        if get_bool(start):
            rx_power.append(get_int32(start + 2) / 10)
        else:
            rx_power.append(None)

    # Calculate the position for the tx chain data structure
    tx_pos = 12 + RX_CHAINS * 22

    tx_valid = get_bool(tx_pos)
    if tx_valid:
        tx_power = get_int32(tx_pos + 2) / -10
    else:
        tx_power = None

    return rx_power, tx_power


def set_time_sync_from_network(ad, action):
    if (action == 'enable'):
        ad.log.info('Enabling sync time from network.')
        ad.adb.shell('settings put global auto_time 1')

    elif (action == 'disable'):
        ad.log.info('Disabling sync time from network.')
        ad.adb.shell('settings put global auto_time 0')

    time.sleep(WAIT_TIME_SYNC_DATE_TIME_FROM_NETWORK)


def datetime_handle(ad, action, set_datetime_value='', get_year=False):
    get_value = ''
    if (action == 'get'):
        if (get_year):
            datetime_string = ad.adb.shell('date')
            datetime_list = datetime_string.split()
            try:
                get_value = datetime_list[5]
            except Exception as e:
                ad.log.error("Fail to get year from datetime: %s. " \
                                "Exception error: %s", datetime_list
                                , str(e))
                raise signals.TestSkip("Fail to get year from datetime" \
                                    ", the format is changed. Skip the test.")
        else:
            get_value = ad.adb.shell('date')

    elif (action == 'set'):
        ad.adb.shell('date %s' % set_datetime_value)
        time.sleep(WAIT_TIME_SYNC_DATE_TIME_FROM_NETWORK)
        ad.adb.shell('am broadcast -a android.intent.action.TIME_SET')

    return get_value


def change_voice_subid_temporarily(ad, sub_id, state_check_func, params=None):
    result = False
    voice_sub_id_changed = False
    current_sub_id = get_incoming_voice_sub_id(ad)
    if current_sub_id != sub_id:
        set_incoming_voice_sub_id(ad, sub_id)
        voice_sub_id_changed = True

    if not params:
        if state_check_func():
            result = True
    else:
        if state_check_func(*params):
            result = True

    if voice_sub_id_changed:
        set_incoming_voice_sub_id(ad, current_sub_id)

    return result


def check_voice_network_type(ads, voice_init=True):
    """
    Args:
        ad: Android device object
        voice_init: check voice network type before initiate call
    Return:
        voice_network_list: Network Type for all android devices
    """
    voice_network_list = []
    for ad in ads:
        voice_network_list.append(ad.droid.telephonyGetCurrentVoiceNetworkType())
        if voice_init:
            ad.log.debug("Voice Network Type Before Call is %s",
                            ad.droid.telephonyGetCurrentVoiceNetworkType())
        else:
            ad.log.debug("Voice Network Type During Call is %s",
                            ad.droid.telephonyGetCurrentVoiceNetworkType())
    return voice_network_list


def cycle_airplane_mode(ad):
    """Turn on APM and then off."""
    # APM toggle
    if not toggle_airplane_mode(ad.log, ad, True):
        ad.log.info("Failed to turn on airplane mode.")
        return False
    if not toggle_airplane_mode(ad.log, ad, False):
        ad.log.info("Failed to turn off airplane mode.")
        return False
    return True