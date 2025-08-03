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

import copy
import re
import statistics

from acts import signals
from acts_contrib.test_utils.tel.tel_defines import INVALID_SUB_ID
from acts_contrib.test_utils.tel.tel_subscription_utils import get_slot_index_from_data_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_slot_index_from_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_from_slot_index

SETUP_DATA_CALL = 'SETUP_DATA_CALL'
SETUP_DATA_CALL_REQUEST = '> SETUP_DATA_CALL'
SETUP_DATA_CALL_RESPONSE = '< SETUP_DATA_CALL'
IS_CAPTIVEPORTAL = r'isCaptivePortal: isSuccessful()=true'

DEACTIVATE_DATA_CALL = 'DEACTIVATE_DATA_CALL'
DEACTIVATE_DATA_CALL_REQUEST = '> DEACTIVATE_DATA_CALL'
DEACTIVATE_DATA_CALL_RESPONSE = '< DEACTIVATE_DATA_CALL'
UNSOL_DATA_CALL_LIST_CHANGED = 'UNSOL_DATA_CALL_LIST_CHANGED'

IWLAN_DATA_SERVICE = 'IWlanDataService'
IWLAN_SETUP_DATA_CALL_REQUEST = '> REQUEST_SETUP_DATA_CALL'
IWLAN_SETUP_DATA_CALL_RESPONSE = 'setupDataCallResponse'
IWLAN_SEND_ACK = '> send ACK for serial'

IWLAN_DEACTIVATE_DATA_CALL_REQUEST = '> REQUEST_DEACTIVATE_DATA_CALL'
IWLAN_DEACTIVATE_DATA_CALL_RESPONSE = 'deactivateDataCallResponse'

SET_PREFERRED_DATA_MODEM = 'SET_PREFERRED_DATA_MODEM'

WHI_IWLAN_DATA_SERVICE = 'IwlanDataService'
WHI_IWLAN_SETUP_DATA_CALL_REQUEST = r'IwlanDataService\[\d\]: Setup data call'
WHI_IWLAN_SETUP_DATA_CALL_RESPONSE = r'IwlanDataService\[\d\]: Tunnel opened!'
WHI_IWLAN_DEACTIVATE_DATA_CALL_REQUEST = r'IwlanDataService\[\d\]: Deactivate data call'
WHI_IWLAN_DEACTIVATE_DATA_CALL_RESPONSE = r'IwlanDataService\[\d\]: Tunnel closed!'

ON_ENABLE_APN_IMS_SLOT0 = 'DCT-C-0 : onEnableApn: apnType=ims, request type=NORMAL'
ON_ENABLE_APN_IMS_SLOT1 = 'DCT-C-1 : onEnableApn: apnType=ims, request type=NORMAL'
ON_ENABLE_APN_IMS_HANDOVER_SLOT0 = 'DCT-C-0 : onEnableApn: apnType=ims, request type=HANDOVER'
ON_ENABLE_APN_IMS_HANDOVER_SLOT1 = 'DCT-C-1 : onEnableApn: apnType=ims, request type=HANDOVER'
RADIO_ON_4G_SLOT0 = r'GsmCdmaPhone: \[0\] Event EVENT_RADIO_ON Received'
RADIO_ON_4G_SLOT1 = r'GsmCdmaPhone: \[1\] Event EVENT_RADIO_ON Received'
RADIO_ON_IWLAN = 'Switching to new default network.*WIFI CONNECTED'
WIFI_OFF = 'setWifiEnabled.*enable=false'
ON_IMS_MM_TEL_CONNECTED_4G_SLOT0 = r'ImsPhone: \[0\].*onImsMmTelConnected imsRadioTech=WWAN'
ON_IMS_MM_TEL_CONNECTED_4G_SLOT1 = r'ImsPhone: \[1\].*onImsMmTelConnected imsRadioTech=WWAN'
ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT0 = r'ImsPhone: \[0\].*onImsMmTelConnected imsRadioTech=WLAN'
ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT1 = r'ImsPhone: \[1\].*onImsMmTelConnected imsRadioTech=WLAN'

DEFAULT_MO_SMS_BODY = 'MO SMS body not yet found'
DEFAULT_MT_SMS_BODY = 'MT SMS body not yet found'

MMS_SERVICE = 'MmsService:'
MMS_SEND_REQUEST_ID_PATTERN = r'SendRequest@(\S+)'
MMS_DOWNLOAD_REQUEST_ID_PATTERN = r'DownloadRequest@(\S+)'
MMS_START_NEW_NW_REQUEST = 'start new network request'
MMS_200_OK = '200 OK'

SMS_SEND_TEXT_MESSAGE = 'smsSendTextMessage'
MO_SMS_LOGCAT_PATTERN = r'smsSendTextMessage.*"(\S+)", true|false'
SEND_SMS = 'SEND_SMS'
SEND_SMS_REQUEST = '> SEND_SMS'
SEND_SMS_RESPONSE = '< SEND_SMS'
SEND_SMS_EXPECT_MORE = 'SEND_SMS_EXPECT_MORE'
UNSOL_RESPONSE_NEW_SMS = '< UNSOL_RESPONSE_NEW_SMS'
SMS_RECEIVED = 'SmsReceived'
MT_SMS_CONTENT_PATTERN = 'sl4a.*?SmsReceived.*?"Text":"(.*?)"'

SEND_SMS_OVER_IMS = r'ImsSmsDispatcher \[(\d)\]'
SEND_SMS_REQUEST_OVER_IMS = 'sendSms:  mRetryCount'
SEND_SMS_RESPONSE_OVER_IMS = 'onSendSmsResult token'
SMS_RECEIVED_OVER_IMS = 'SMS received'
SMS_RECEIVED_OVER_IMS_SLOT0 = r'ImsSmsDispatcher \[0\]: SMS received'
SMS_RECEIVED_OVER_IMS_SLOT1 = r'ImsSmsDispatcher \[1\]: SMS received'

IMS_REGISTERED_CST_SLOT0 = 'IMS_REGISTERED.*CrossStackEpdg.*SLID:0'
IMS_REGISTERED_CST_SLOT1 = 'IMS_REGISTERED.*CrossStackEpdg.*SLID:1'
ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT0 = r'ImsPhone: \[0\].*onImsMmTelConnected imsRadioTech=WLAN'
ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT1 = r'ImsPhone: \[1\].*onImsMmTelConnected imsRadioTech=WLAN'


def print_nested_dict(ad, d):
    divider = "------"
    for k, v in d.items():
        if isinstance(v, dict):
            ad.log.info('%s %s %s', divider, k, divider)
            print_nested_dict(ad, v)
        else:
            ad.log.info('%s: %s', k, v)


def get_slot_from_logcat(msg):
    """Get slot index from specific pattern in logcat

    Args:
        msg: logcat message string

    Returns:
        0 for pSIM or 1 for eSIM
    """
    res = re.findall(r'\[(PHONE[\d])\]', msg)
    try:
        phone = res[0]
    except:
        phone = None
    return phone


def get_apn_from_logcat(msg):
    """Get APN from logcat

    Args:
        msg: logcat message string

    Returns:
        APN
    """
    res = re.findall(r'DataProfile=[^/]+/[^/]+/[^/]+/([^/]+)/', msg)
    try:
        apn = res[0]
    except:
        apn = None
    return apn


def parse_setup_data_call(ad, apn='internet', dds_switch=False):
    """Search in logcat for lines containing data call setup procedure.
        Calculate the data call setup time with given APN and validation
        time on LTE.

    Args:
        ad: Android object
        apn: access point name
        dds_switch: True for switching DDS. Otherwise False.

    Returns:
        setup_data_call: Dictionary containing data call setup request and
            response messages for each data call. The format is shown as
            below:
            {
                message_id:
                {
                    'request':
                    {
                        'message': logcat message body of data call setup
                            request message
                        'time_stamp': time stamp in text format
                        'datetime_obj': datetime object of time stamp
                        'apn': access point name of this request
                        'phone': 0 for pSIM or 1 for eSIM
                    }
                    'response':
                    {
                        'message': logcat message body of data call setup
                            response message
                        'time_stamp': time stamp in text format
                        'datetime_obj': datetime object of time stamp
                        'cause': failure cause if data call setup failed
                        'cid': PDP context ID
                        'ifname': the name of the interface of the network
                        'phone': 0 for pSIM or 1 for eSIM
                        'unsol_data_call_list_changed': message of
                            unsol_data_call_list_changed
                        'unsol_data_call_list_changed_time': time stamp of
                            the message unsol_data_call_list_changed
                        'is_captive_portal': message of LTE validation pass
                        'data_call_setup_time': time between data call setup
                            request and unsol_data_call_list_changed
                        'validation_time_on_lte': time between data call
                            setup response and LTE validation pass
                    }
                }
            }

        data_call_setup_time_list: List. This is a summary of necessary
            messages of data call setup procedure The format is shown as
            below:
                [
                    {
                        'request': logcat message body of data call setup
                            request message
                        'response': logcat message body of data call setup
                            response message
                        'unsol_data_call_list_changed': message of
                            unsol_data_call_list_changed
                        'start': time stamp of data call setup request
                        'end': time stamp of the message
                            unsol_data_call_list_changed
                        'duration': time between data call setup request and
                            unsol_data_call_list_changed
                        'validation_time_on_lte': time between data call
                            setup response and LTE validation pass
                    }
                ]

        avg_data_call_setup_time: average of data call setup time

        avg_validation_time_on_lte: average of time for validation time on
            LTE
    """
    ad.log.info('====== Start to search logcat ====== ')
    logcat = ad.search_logcat(
        r'%s\|%s\|%s\|%s' % (
            SET_PREFERRED_DATA_MODEM,
            SETUP_DATA_CALL,
            UNSOL_DATA_CALL_LIST_CHANGED, IS_CAPTIVEPORTAL))

    if not logcat:
        return False

    for msg in logcat:
        ad.log.info(msg["log_message"])

    dds_slot = get_slot_index_from_data_sub_id(ad)

    set_preferred_data_modem = {}
    setup_data_call = {}
    data_call_setup_time_list = []
    last_message_id = None

    for line in logcat:
        if line['message_id']:
            if SET_PREFERRED_DATA_MODEM in line['log_message']:
                set_preferred_data_modem['message'] = line['log_message']
                set_preferred_data_modem['time_stamp'] = line['time_stamp']
                set_preferred_data_modem[
                    'datetime_obj'] = line['datetime_obj']

            if SETUP_DATA_CALL_REQUEST in line['log_message']:
                found_apn = get_apn_from_logcat(line['log_message'])
                if found_apn != apn:
                    continue

                phone = get_slot_from_logcat(line['log_message'])
                if not phone:
                    continue

                if not dds_switch:
                    if str(dds_slot) not in phone:
                        continue

                msg_id = line['message_id']
                last_message_id = line['message_id']
                if msg_id not in setup_data_call:
                    setup_data_call[msg_id] = {}

                setup_data_call[msg_id]['request'] = {
                    'message': line['log_message'],
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj'],
                    'apn': found_apn,
                    'phone': phone}

                if set_preferred_data_modem:
                    setup_data_call[msg_id]['request'][
                        'set_preferred_data_modem_message'] = set_preferred_data_modem['message']
                    setup_data_call[msg_id]['request'][
                        'set_preferred_data_modem_time_stamp'] = set_preferred_data_modem['time_stamp']
                    setup_data_call[msg_id]['request'][
                        'set_preferred_data_modem_datetime_obj'] = set_preferred_data_modem['datetime_obj']
                    set_preferred_data_modem = {}

            if SETUP_DATA_CALL_RESPONSE in line['log_message']:
                phone = get_slot_from_logcat(line['log_message'])
                if not phone:
                    continue

                if not dds_switch:
                    if str(dds_slot) not in phone:
                        continue

                msg_id = line['message_id']
                if msg_id not in setup_data_call:
                    continue

                if 'request' not in setup_data_call[msg_id]:
                    continue

                last_message_id = line['message_id']

                setup_data_call[msg_id]['response'] = {
                    'message': line['log_message'],
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj'],
                    'cause': '0',
                    'cid': None,
                    'ifname': None,
                    'phone': phone,
                    'unsol_data_call_list_changed': None,
                    'unsol_data_call_list_changed_time': None,
                    'is_captive_portal': None,
                    'data_call_setup_time': None,
                    'validation_time_on_lte': None}

                res = re.findall(r'cause=(\d+)', line['log_message'])
                try:
                    cause = res[0]
                    setup_data_call[msg_id]['response']['cause'] = cause
                except:
                    pass

                res = re.findall(r'cid=(\d+)', line['log_message'])
                try:
                    cid = res[0]
                    setup_data_call[msg_id]['response']['cid'] = cid
                except:
                    pass

                res = re.findall(r'ifname=(\S+)', line['log_message'])
                try:
                    ifname = res[0]
                    setup_data_call[msg_id]['response']['ifname'] = ifname
                except:
                    pass

        if UNSOL_DATA_CALL_LIST_CHANGED in line['log_message']:
            if not last_message_id:
                continue

            phone = get_slot_from_logcat(line['log_message'])
            if not phone:
                continue

            if not dds_switch:
                if str(dds_slot) not in phone:
                    continue

            if 'request' not in setup_data_call[last_message_id]:
                continue

            if 'response' not in setup_data_call[last_message_id]:
                continue

            cid =  setup_data_call[last_message_id]['response']['cid']
            if 'cid = %s' % cid not in line['log_message']:
                continue

            if setup_data_call[last_message_id]['response']['cause'] != '0':
                continue

            if dds_switch:
                if 'set_preferred_data_modem_message' not in setup_data_call[
                    last_message_id]['request']:
                    continue
                data_call_start_time = setup_data_call[last_message_id][
                    'request']['set_preferred_data_modem_datetime_obj']

            else:
                data_call_start_time = setup_data_call[last_message_id][
                    'request']['datetime_obj']

            data_call_end_time = line['datetime_obj']
            setup_data_call[last_message_id]['response'][
                'unsol_data_call_list_changed_time'] = data_call_end_time
            setup_data_call[last_message_id]['response'][
                'unsol_data_call_list_changed'] = line['log_message']
            data_call_setup_time = data_call_end_time - data_call_start_time
            setup_data_call[last_message_id]['response'][
                'data_call_setup_time'] = data_call_setup_time.total_seconds()

            if apn == 'ims':
                data_call_setup_time_list.append(
                    {'request': setup_data_call[
                        last_message_id]['request']['message'],
                    'response': setup_data_call[
                        last_message_id]['response']['message'],
                    'unsol_data_call_list_changed': setup_data_call[
                        last_message_id]['response'][
                            'unsol_data_call_list_changed'],
                    'start': data_call_start_time,
                    'end': data_call_end_time,
                    'duration': setup_data_call[last_message_id]['response'][
                        'data_call_setup_time']})

                last_message_id = None

        if IS_CAPTIVEPORTAL in line['log_message']:
            if not last_message_id:
                continue

            if 'request' not in setup_data_call[last_message_id]:
                continue

            if 'response' not in setup_data_call[last_message_id]:
                continue

            if dds_switch:
                data_call_start_time = setup_data_call[last_message_id][
                    'request']['set_preferred_data_modem_datetime_obj']

            else:
                data_call_start_time = setup_data_call[last_message_id][
                    'request']['datetime_obj']

            setup_data_call[last_message_id]['response'][
                'is_captive_portal'] = line['log_message']
            validation_start_time_on_lte = setup_data_call[
                last_message_id]['response']['datetime_obj']
            validation_end_time_on_lte = line['datetime_obj']
            validation_time_on_lte = (
                validation_end_time_on_lte - validation_start_time_on_lte).total_seconds()
            setup_data_call[last_message_id]['response'][
                'validation_time_on_lte'] = validation_time_on_lte

            data_call_setup_time_list.append(
                {'request': setup_data_call[last_message_id]['request'][
                    'message'],
                'response': setup_data_call[last_message_id]['response'][
                    'message'],
                'unsol_data_call_list_changed': setup_data_call[
                    last_message_id]['response']['unsol_data_call_list_changed'],
                'start': data_call_start_time,
                'end': setup_data_call[last_message_id]['response'][
                    'unsol_data_call_list_changed_time'],
                'duration': setup_data_call[last_message_id]['response'][
                    'data_call_setup_time'],
                'validation_time_on_lte': validation_time_on_lte})

            last_message_id = None

    duration_list = []
    for item in data_call_setup_time_list:
        if 'duration' in item:
            duration_list.append(item['duration'])

    try:
        avg_data_call_setup_time = statistics.mean(duration_list)
    except:
        avg_data_call_setup_time = None

    validation_time_on_lte_list = []
    for item in data_call_setup_time_list:
        if 'validation_time_on_lte' in item:
            validation_time_on_lte_list.append(
                item['validation_time_on_lte'])

    try:
        avg_validation_time_on_lte = statistics.mean(
            validation_time_on_lte_list)
    except:
        avg_validation_time_on_lte = None

    return (
        setup_data_call,
        data_call_setup_time_list,
        avg_data_call_setup_time,
        avg_validation_time_on_lte)


def parse_setup_data_call_on_iwlan(ad):
    """Search in logcat for lines containing data call setup procedure.
        Calculate the data call setup time with given APN on iwlan.

    Args:
        ad: Android object
        apn: access point name

    Returns:
        setup_data_call: Dictionary containing data call setup request and
            response messages for each data call. The format is shown as
            below:
            {
                message_id:
                {
                    'request':
                    {
                        'message': logcat message body of data call setup
                            request message
                        'time_stamp': time stamp in text format
                        'datetime_obj': datetime object of time stamp
                    }
                    'response':
                    {
                        'message': logcat message body of data call setup
                            response message
                        'time_stamp': time stamp in text format
                        'datetime_obj': datetime object of time stamp
                        'cause': failure cause if data call setup failed
                        'data_call_setup_time': time between data call setup
                            request and response
                    }
                }
            }

        data_call_setup_time_list:
            List. This is a summary of mecessary messages of data call setup
                procedure The format is shown as below:
                [
                    {
                        'request': logcat message body of data call setup
                            request message
                        'response': logcat message body of data call setup
                            response message
                        'start': time stamp of data call setup request
                        'end': time stamp of data call setup response
                        'duration': time between data call setup request and
                            response
                    }
                ]

        avg_data_call_setup_time: average of data call setup time
    """
    ad.log.info('====== Start to search logcat ====== ')
    logcat = ad.search_logcat(r'%s\|%s' % (
        IWLAN_DATA_SERVICE, WHI_IWLAN_DATA_SERVICE))

    found_iwlan_data_service = 1
    if not logcat:
        found_iwlan_data_service = 0

    if not found_iwlan_data_service:
        (
            setup_data_call,
            data_call_setup_time_list,
            avg_data_call_setup_time,
            _) = parse_setup_data_call(ad, apn='ims')

        return (
            setup_data_call,
            data_call_setup_time_list,
            avg_data_call_setup_time)

    for msg in logcat:
        ad.log.info(msg["log_message"])

    setup_data_call = {}
    data_call_setup_time_list = []
    last_message_id = None

    whi_msg_index = None
    for line in logcat:
        serial = None
        cause = None
        if IWLAN_SETUP_DATA_CALL_REQUEST in line['log_message']:
            match_res = re.findall(
                r'%s:\s(\d+)' % IWLAN_DATA_SERVICE, line['log_message'])
            if match_res:
                try:
                    serial = match_res[0]
                except:
                    pass

            if not serial:
                continue

            msg_id = serial
            last_message_id = serial
            if msg_id not in setup_data_call:
                setup_data_call[msg_id] = {}

            setup_data_call[msg_id]['request'] = {
                'message': line['log_message'],
                'time_stamp': line['time_stamp'],
                'datetime_obj': line['datetime_obj']}

        else:
            if re.search(
                WHI_IWLAN_SETUP_DATA_CALL_REQUEST, line['log_message']):
                if whi_msg_index is None:
                    whi_msg_index = 0
                else:
                    whi_msg_index = whi_msg_index + 1

                if str(whi_msg_index) not in setup_data_call:
                    setup_data_call[str(whi_msg_index)] = {}

                setup_data_call[str(whi_msg_index)]['request'] = {
                    'message': line['log_message'],
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj']}

        if IWLAN_SETUP_DATA_CALL_RESPONSE in line['log_message']:
            match_res = re.findall(r'Serial = (\d+)', line['log_message'])
            if match_res:
                try:
                    serial = match_res[0]
                except:
                    pass

            if serial:
                msg_id = serial
            else:
                msg_id = last_message_id

            if msg_id not in setup_data_call:
                continue

            if 'request' not in setup_data_call[msg_id]:
                continue

            setup_data_call[msg_id]['response'] = {
                'message': None,
                'time_stamp': None,
                'datetime_obj': None,
                'cause': None,
                'data_call_setup_time': None}

            match_res = re.findall(
                r'Fail Cause = (\d+)', line['log_message'])
            if match_res:
                try:
                    cause = match_res[0]
                except:
                    cause = None

            if cause != '0':
                continue

            setup_data_call[msg_id]['response']['message'] = line[
                'log_message']
            setup_data_call[msg_id]['response']['time_stamp'] = line[
                'time_stamp']
            setup_data_call[msg_id]['response']['datetime_obj'] = line[
                'datetime_obj']
            setup_data_call[msg_id]['response']['cause'] = 0

            data_call_start_time = setup_data_call[last_message_id][
                'request']['datetime_obj']
            data_call_end_time = line['datetime_obj']
            data_call_setup_time = data_call_end_time - data_call_start_time
            setup_data_call[last_message_id]['response'][
                'data_call_setup_time'] = data_call_setup_time.total_seconds()

            data_call_setup_time_list.append(
                {'request': setup_data_call[last_message_id]['request'][
                    'message'],
                'response': setup_data_call[last_message_id]['response'][
                    'message'],
                'start': setup_data_call[last_message_id]['request'][
                    'datetime_obj'],
                'end': setup_data_call[last_message_id]['response'][
                    'datetime_obj'],
                'duration': setup_data_call[last_message_id]['response'][
                    'data_call_setup_time']})

            last_message_id = None

        else:
            if re.search(
                WHI_IWLAN_SETUP_DATA_CALL_RESPONSE, line['log_message']):
                if whi_msg_index is None:
                    continue

                if 'response' in setup_data_call[str(whi_msg_index)]:
                    ad.log.error('Duplicated setup data call response is '
                    'found or the request message is lost.')
                    continue

                setup_data_call[str(whi_msg_index)]['response'] = {
                    'message': line['log_message'],
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj'],
                    'data_call_setup_time': None}

                data_call_start_time = setup_data_call[str(whi_msg_index)][
                    'request']['datetime_obj']
                data_call_end_time = line['datetime_obj']
                data_call_setup_time = data_call_end_time - data_call_start_time
                setup_data_call[str(whi_msg_index)]['response'][
                    'data_call_setup_time'] = data_call_setup_time.total_seconds()

                data_call_setup_time_list.append(
                    {'request': setup_data_call[str(whi_msg_index)][
                        'request']['message'],
                    'response': setup_data_call[str(whi_msg_index)][
                        'response']['message'],
                    'start': setup_data_call[str(whi_msg_index)]['request'][
                        'datetime_obj'],
                    'end': setup_data_call[str(whi_msg_index)]['response'][
                        'datetime_obj'],
                    'duration': setup_data_call[str(whi_msg_index)][
                        'response']['data_call_setup_time']})

    duration_list = []
    for item in data_call_setup_time_list:
        if 'duration' in item:
            duration_list.append(item['duration'])

    try:
        avg_data_call_setup_time = statistics.mean(duration_list)
    except:
        avg_data_call_setup_time = None

    ad.log.warning('setup_data_call: %s', setup_data_call)
    ad.log.warning('duration list: %s', duration_list)
    ad.log.warning('avg_data_call_setup_time: %s', avg_data_call_setup_time)

    return (
        setup_data_call,
        data_call_setup_time_list,
        avg_data_call_setup_time)


def parse_deactivate_data_call(ad):
    """Search in logcat for lines containing data call deactivation procedure.
        Calculate the data call deactivation time on LTE.

    Args:
        ad: Android object

    Returns:
        deactivate_data_call: Dictionary containing data call deactivation
            request and response messages for each data call. The format is
            shown as below:
            {
                message_id:
                {
                    'request':
                    {
                        'message': logcat message body of data call
                            deactivation request message
                        'time_stamp': time stamp in text format
                        'datetime_obj': datetime object of time stamp
                        'cid': PDP context ID
                        'phone': 0 for pSIM or 1 for eSIM
                    }
                    'response':
                    {
                        'message': logcat message body of data call
                            deactivation response message
                        'time_stamp': time stamp in text format
                        'datetime_obj': datetime object of time stamp
                        'phone': 0 for pSIM or 1 for eSIM
                        'unsol_data_call_list_changed': message of
                            unsol_data_call_list_changed
                        'deactivate_data_call_time': time between data call
                            deactivation request and unsol_data_call_list_changed
                    }
                }
            }

        deactivate_data_call_time_list: List. This is a summary of necessary
            messages of data call deactivation procedure The format is shown
            as below:
                [
                    {
                        'request': logcat message body of data call
                            deactivation request message
                        'response': logcat message body of data call
                            deactivation response message
                        'unsol_data_call_list_changed': message of
                            unsol_data_call_list_changed
                        'start': time stamp of data call deactivation request
                        'end': time stamp of the message
                            unsol_data_call_list_changed
                        'duration': time between data call deactivation
                            request and unsol_data_call_list_changed
                    }
                ]

        avg_deactivate_data_call_time: average of data call deactivation time
    """
    ad.log.info('====== Start to search logcat ====== ')
    logcat = ad.search_logcat(
        r'%s\|%s' % (DEACTIVATE_DATA_CALL, UNSOL_DATA_CALL_LIST_CHANGED))
    if not logcat:
        return False

    for msg in logcat:
        ad.log.info(msg["log_message"])

    dds_slot = get_slot_index_from_data_sub_id(ad)

    deactivate_data_call = {}
    deactivate_data_call_time_list = []
    last_message_id = None

    for line in logcat:
        if line['message_id']:
            if DEACTIVATE_DATA_CALL_REQUEST in line['log_message']:
                phone = get_slot_from_logcat(line['log_message'])
                if not phone:
                    continue

                if str(dds_slot) not in phone:
                    continue

                msg_id = line['message_id']
                last_message_id = line['message_id']
                if msg_id not in deactivate_data_call:
                    deactivate_data_call[msg_id] = {}

                deactivate_data_call[msg_id]['request'] = {
                    'message': line['log_message'],
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj'],
                    'cid': None,
                    'phone': dds_slot}

                res = re.findall(r'cid = (\d+)', line['log_message'])
                try:
                    cid = res[0]
                    deactivate_data_call[msg_id]['request']['cid'] = cid
                except:
                    pass

            if DEACTIVATE_DATA_CALL_RESPONSE in line['log_message']:
                phone = get_slot_from_logcat(line['log_message'])
                if not phone:
                    continue

                if str(dds_slot) not in phone:
                    continue

                msg_id = line['message_id']
                if msg_id not in deactivate_data_call:
                    continue

                if 'request' not in deactivate_data_call[msg_id]:
                    continue

                last_message_id = line['message_id']

                deactivate_data_call[msg_id]['response'] = {
                    'message': line['log_message'],
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj'],
                    'phone': dds_slot,
                    'unsol_data_call_list_changed': None,
                    'deactivate_data_call_time': None}

        if UNSOL_DATA_CALL_LIST_CHANGED in line['log_message']:
            if not last_message_id:
                continue

            phone = get_slot_from_logcat(line['log_message'])
            if not phone:
                continue

            if str(dds_slot) not in phone:
                continue

            if 'request' not in deactivate_data_call[last_message_id]:
                continue

            if 'response' not in deactivate_data_call[last_message_id]:
                continue

            cid = deactivate_data_call[last_message_id]['request']['cid']
            if 'cid = %s' % cid not in line['log_message']:
                continue

            deactivate_data_call_start_time = deactivate_data_call[
                last_message_id]['request']['datetime_obj']
            deactivate_data_call_end_time = line['datetime_obj']
            deactivate_data_call[last_message_id]['response'][
                'unsol_data_call_list_changed'] = line['log_message']
            deactivate_data_call_time = (
                deactivate_data_call_end_time - deactivate_data_call_start_time)
            deactivate_data_call[last_message_id]['response'][
                'deactivate_data_call_time'] = deactivate_data_call_time.total_seconds()
            deactivate_data_call_time_list.append(
                {'request': deactivate_data_call[last_message_id][
                    'request']['message'],
                'response': deactivate_data_call[last_message_id][
                    'response']['message'],
                'unsol_data_call_list_changed': deactivate_data_call[
                    last_message_id]['response'][
                        'unsol_data_call_list_changed'],
                'start': deactivate_data_call_start_time,
                'end': deactivate_data_call_end_time,
                'duration': deactivate_data_call_time.total_seconds()})

            last_message_id = None

    duration_list = []
    for item in deactivate_data_call_time_list:
        if 'duration' in item:
            duration_list.append(item['duration'])

    try:
        avg_deactivate_data_call_time = statistics.mean(duration_list)
    except:
        avg_deactivate_data_call_time = None

    return (
        deactivate_data_call,
        deactivate_data_call_time_list,
        avg_deactivate_data_call_time)


def parse_deactivate_data_call_on_iwlan(ad):
    """Search in logcat for lines containing data call deactivation procedure.
        Calculate the data call deactivation time on iwlan.

    Args:
        ad: Android object

    Returns:
        deactivate_data_call: Dictionary containing data call deactivation
            request and response messages for each data call. The format is
            shown as below:
            {
                message_id:
                {
                    'request':
                    {
                        'message': logcat message body of data call
                            deactivation request message
                        'time_stamp': time stamp in text format
                        'datetime_obj': datetime object of time stamp
                    }
                    'response':
                    {
                        'message': logcat message body of data call
                            deactivation response message
                        'time_stamp': time stamp in text format
                        'datetime_obj': datetime object of time stamp
                        'send_ack_for_serial_time': time stamp of ACK
                        'deactivate_data_call_time': time between data call
                            deactivation request and ACK
                    }
                }
            }

        deactivate_data_call_time_list: List. This is a summary of necessary
            messages of data call deactivation procedure The format is shown
            as below:
                [
                    {
                        'request': logcat message body of data call
                            deactivation request message
                        'response': logcat message body of data call
                            deactivation response message
                        'start': time stamp of data call deactivation request
                        'end': time stamp of the ACK
                        'duration': time between data call deactivation
                            request and ACK
                    }
                ]

        avg_deactivate_data_call_time: average of data call deactivation time
    """
    ad.log.info('====== Start to search logcat ====== ')
    logcat = ad.search_logcat(r'%s\|%s' % (
        IWLAN_DATA_SERVICE, WHI_IWLAN_DATA_SERVICE))

    found_iwlan_data_service = 1
    if not logcat:
        found_iwlan_data_service = 0

    if not found_iwlan_data_service:
        (
            deactivate_data_call,
            deactivate_data_call_time_list,
            avg_deactivate_data_call_time) = parse_deactivate_data_call(ad)

        return (
            deactivate_data_call,
            deactivate_data_call_time_list,
            avg_deactivate_data_call_time)

    for msg in logcat:
        ad.log.info(msg["log_message"])

    deactivate_data_call = {}
    deactivate_data_call_time_list = []
    last_message_id = None

    whi_msg_index = None
    for line in logcat:
        serial = None
        if IWLAN_DEACTIVATE_DATA_CALL_REQUEST in line['log_message']:
            match_res = re.findall(
                r'%s:\s(\d+)' % IWLAN_DATA_SERVICE, line['log_message'])
            if match_res:
                try:
                    serial = match_res[0]
                except:
                    serial = None

            if not serial:
                continue

            msg_id = serial
            last_message_id = serial
            if msg_id not in deactivate_data_call:
                deactivate_data_call[msg_id] = {}

            deactivate_data_call[msg_id]['request'] = {
                'message': line['log_message'],
                'time_stamp': line['time_stamp'],
                'datetime_obj': line['datetime_obj']}
        else:
            if re.search(WHI_IWLAN_DEACTIVATE_DATA_CALL_REQUEST, line[
                'log_message']):
                if whi_msg_index is None:
                    whi_msg_index = 0
                else:
                    whi_msg_index = whi_msg_index + 1

                if str(whi_msg_index) not in deactivate_data_call:
                    deactivate_data_call[str(whi_msg_index)] = {}

                deactivate_data_call[str(whi_msg_index)]['request'] = {
                    'message': line['log_message'],
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj']}

        if IWLAN_DEACTIVATE_DATA_CALL_RESPONSE in line['log_message']:
            if 'response' not in deactivate_data_call[last_message_id]:
                deactivate_data_call[msg_id]['response'] = {}

            deactivate_data_call[msg_id]['response'] = {
                'message': line['log_message'],
                'time_stamp': line['time_stamp'],
                'datetime_obj': line['datetime_obj'],
                'send_ack_for_serial_time': None,
                'deactivate_data_call_time': None}

        else:
            if re.search(WHI_IWLAN_DEACTIVATE_DATA_CALL_RESPONSE, line[
                'log_message']):
                if whi_msg_index is None:
                    continue

                if 'response' in deactivate_data_call[str(whi_msg_index)]:
                    ad.log.error('Duplicated deactivate data call response'
                    'is found or the request message is lost.')
                    continue

                deactivate_data_call[str(whi_msg_index)]['response'] = {
                    'message': line['log_message'],
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj'],
                    'deactivate_data_call_time': None}

                deactivate_data_call_start_time = deactivate_data_call[
                    str(whi_msg_index)]['request']['datetime_obj']
                deactivate_data_call_end_time = line['datetime_obj']
                deactivate_data_call_time = (
                    deactivate_data_call_end_time - deactivate_data_call_start_time)
                deactivate_data_call[str(whi_msg_index)]['response'][
                    'deactivate_data_call_time'] = deactivate_data_call_time.total_seconds()
                deactivate_data_call_time_list.append(
                    {'request': deactivate_data_call[str(whi_msg_index)][
                        'request']['message'],
                    'response': deactivate_data_call[str(whi_msg_index)][
                        'response']['message'],
                    'start': deactivate_data_call_start_time,
                    'end': deactivate_data_call_end_time,
                    'duration': deactivate_data_call_time.total_seconds()})

        if IWLAN_SEND_ACK in line['log_message']:
            match_res = re.findall(
                r'%s:\s(\d+)' % IWLAN_DATA_SERVICE, line['log_message'])
            if match_res:
                try:
                    serial = match_res[0]
                except:
                    serial = None

            if not serial:
                continue

            msg_id = serial

            if msg_id not in deactivate_data_call:
                continue

            if 'response' not in deactivate_data_call[msg_id]:
                continue

            deactivate_data_call[msg_id]['response'][
                'send_ack_for_serial_time'] = line['datetime_obj']

            deactivate_data_call_start_time = deactivate_data_call[msg_id][
                'request']['datetime_obj']
            deactivate_data_call_end_time = line['datetime_obj']
            deactivate_data_call_time = (
                deactivate_data_call_end_time - deactivate_data_call_start_time)
            deactivate_data_call[msg_id]['response'][
                'deactivate_data_call_time'] = deactivate_data_call_time.total_seconds()
            deactivate_data_call_time_list.append(
                {'request': deactivate_data_call[msg_id]['request'][
                    'message'],
                'response': deactivate_data_call[msg_id]['response'][
                    'message'],
                'start': deactivate_data_call_start_time,
                'end': deactivate_data_call_end_time,
                'duration': deactivate_data_call_time.total_seconds()})

            last_message_id = None

    duration_list = []
    for item in deactivate_data_call_time_list:
        if 'duration' in item:
            duration_list.append(item['duration'])

    try:
        avg_deactivate_data_call_time = statistics.mean(duration_list)
    except:
        avg_deactivate_data_call_time = None

    return (
        deactivate_data_call,
        deactivate_data_call_time_list,
        avg_deactivate_data_call_time)


def parse_ims_reg(
    ad,
    search_intervals=None,
    rat='4g',
    reboot_or_apm='reboot',
    slot=None):
    """Search in logcat for lines containing messages about IMS registration.

    Args:
        ad: Android object
        search_intervals: List. Only lines with time stamp in given time
            intervals will be parsed.
            E.g., [(begin_time1, end_time1), (begin_time2, end_time2)]
            Both begin_time and end_time should be datetime object.
        rat: "4g" for IMS over LTE or "iwlan" for IMS over Wi-Fi
        reboot_or_apm: specify the scenario "reboot" or "apm"
        slot: 0 for pSIM and 1 for eSIM

    Returns:
        (ims_reg, parsing_fail, avg_ims_reg_duration)

        ims_reg: List of dictionaries containing found lines for start and
            end time stamps. Each dict represents a cycle of the test.

            [
                {'start': message on start time stamp,
                'end': message on end time stamp,
                'duration': time difference between start and end}
            ]
        parsing_fail: List of dictionaries containing the cycle number and
            missing messages of each failed cycle

            [
                'attempt': failed cycle number
                'missing_msg' missing messages which should be found
            ]
        avg_ims_reg_duration: average of the duration in ims_reg

    """
    if slot is None:
        slot = get_slot_index_from_voice_sub_id(ad)
        ad.log.info('Default voice slot: %s', slot)
    else:
        if get_subid_from_slot_index(ad.log, ad, slot) == INVALID_SUB_ID:
            ad.log.error('Slot %s is invalid.', slot)
            raise signals.TestFailure('Failed',
                extras={'fail_reason': 'Slot %s is invalid.' % slot})

        ad.log.info('Assigned slot: %s', slot)

    start_command = {
        'reboot': {
            '0': {'4g': ON_ENABLE_APN_IMS_SLOT0,
                'iwlan': ON_ENABLE_APN_IMS_HANDOVER_SLOT0 + '\|' + ON_ENABLE_APN_IMS_SLOT0},
            '1': {'4g': ON_ENABLE_APN_IMS_SLOT1,
                'iwlan': ON_ENABLE_APN_IMS_HANDOVER_SLOT1 + '\|' + ON_ENABLE_APN_IMS_SLOT1}
        },
        'apm':{
            '0': {'4g': RADIO_ON_4G_SLOT0, 'iwlan': RADIO_ON_IWLAN},
            '1': {'4g': RADIO_ON_4G_SLOT1, 'iwlan': RADIO_ON_IWLAN}
        },
        'wifi_off':{
            '0': {'4g': WIFI_OFF, 'iwlan': WIFI_OFF},
            '1': {'4g': WIFI_OFF, 'iwlan': WIFI_OFF}
        },
    }

    end_command = {
        '0': {'4g': ON_IMS_MM_TEL_CONNECTED_4G_SLOT0,
            'iwlan': ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT0},
        '1': {'4g': ON_IMS_MM_TEL_CONNECTED_4G_SLOT1,
            'iwlan': ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT1}
    }

    ad.log.info('====== Start to search logcat ======')
    logcat = ad.search_logcat('%s\|%s' % (
        start_command[reboot_or_apm][str(slot)][rat],
        end_command[str(slot)][rat]))

    if not logcat:
        raise signals.TestFailure('Failed',
            extras={'fail_reason': 'No line matching the given pattern can '
            'be found in logcat.'})

    for msg in logcat:
        ad.log.info(msg["log_message"])

    ims_reg = []
    ims_reg_duration_list = []
    parsing_fail = []

    start_command['reboot'] = {
        '0': {'4g': ON_ENABLE_APN_IMS_SLOT0,
            'iwlan': ON_ENABLE_APN_IMS_HANDOVER_SLOT0 + '|' + ON_ENABLE_APN_IMS_SLOT0},
        '1': {'4g': ON_ENABLE_APN_IMS_SLOT1,
            'iwlan': ON_ENABLE_APN_IMS_HANDOVER_SLOT1 + '|' + ON_ENABLE_APN_IMS_SLOT1}
    }

    keyword_dict = {
        'start': start_command[reboot_or_apm][str(slot)][rat],
        'end': end_command[str(slot)][rat]
    }

    for attempt, interval in enumerate(search_intervals):
        if isinstance(interval, list):
            try:
                begin_time, end_time = interval
            except Exception as e:
                ad.log.error(e)
                continue

            ad.log.info('Parsing begin time: %s', begin_time)
            ad.log.info('Parsing end time: %s', end_time)

            temp_keyword_dict = copy.deepcopy(keyword_dict)
            for line in logcat:
                if begin_time and line['datetime_obj'] < begin_time:
                    continue

                if end_time and line['datetime_obj'] > end_time:
                    break

                for key in temp_keyword_dict:
                    if temp_keyword_dict[key] and not isinstance(
                        temp_keyword_dict[key], dict):
                        res = re.findall(
                            temp_keyword_dict[key], line['log_message'])
                        if res:
                            ad.log.info('Found: %s', line['log_message'])
                            temp_keyword_dict[key] = {
                                'message': line['log_message'],
                                'time_stamp': line['datetime_obj']}
                            break

            for key in temp_keyword_dict:
                if temp_keyword_dict[key] == keyword_dict[key]:
                    ad.log.error(
                        '"%s" is missing in cycle %s.',
                        keyword_dict[key],
                        attempt)
                    parsing_fail.append({
                        'attempt': attempt,
                        'missing_msg': keyword_dict[key]})
            try:
                ims_reg_duration = (
                    temp_keyword_dict['end'][
                        'time_stamp'] - temp_keyword_dict[
                            'start'][
                                'time_stamp']).total_seconds()
                ims_reg_duration_list.append(ims_reg_duration)
                ims_reg.append({
                    'start': temp_keyword_dict['start'][
                        'message'],
                    'end': temp_keyword_dict['end'][
                        'message'],
                    'duration': ims_reg_duration})
            except Exception as e:
                ad.log.error(e)

    try:
        avg_ims_reg_duration = statistics.mean(ims_reg_duration_list)
    except:
        avg_ims_reg_duration = None

    return ims_reg, parsing_fail, avg_ims_reg_duration


def parse_mo_sms(logcat):
    """Search in logcat for lines containing messages about SMS sending on
        LTE.

    Args:
        logcat: List containing lines of logcat

    Returns:
        send_sms: Dictionary containing found lines for each SMS
            request and response messages together with their time stamps.
            {
                'message_id':{
                    'request':{
                        'message': logcat message body of SMS request
                        'time_stamp': time stamp in text format
                        'datetime_obj': datetime object of the time stamp
                    },
                    'response':{
                        'message': logcat message body of SMS response
                        'time_stamp': time stamp in text format
                        'datetime_obj': datetime object of the time stamp
                        'sms_delivery_time': time between SMS request and
                            response
                    }
                }
            }

        summary: the format is listed below:
            {
                'request': logcat message body of SMS request
                'response': logcat message body of SMS response
                'unsol_response_new_sms': unsolicited response message upon
                    SMS receiving on MT UE
                'sms_body': message body of SMS
                'mo_start': time stamp of MO SMS request message
                'mo_end': time stamp of MO SMS response message
                'mo_signal_duration': time between MO SMS request and response
                'delivery_time': time between MO SMS request and
                    unsol_response_new_sms on MT UE
            }

        avg_setup_time: average of mo_signal_duration
    """
    send_sms = {}
    summary = []
    sms_body = DEFAULT_MO_SMS_BODY
    msg_id = None
    if not logcat:
        return False

    for line in logcat:
        res = re.findall(MO_SMS_LOGCAT_PATTERN, line['log_message'])
        if res:
            try:
                sms_body = res[0]
            except:
                sms_body = 'Cannot find MO SMS body'

        if line['message_id']:
            msg_id = line['message_id']
            if SEND_SMS_REQUEST in line[
                'log_message'] and SEND_SMS_EXPECT_MORE not in line[
                    'log_message']:
                if msg_id not in send_sms:
                    send_sms[msg_id] = {}

                send_sms[msg_id]['sms_body'] = sms_body
                sms_body = DEFAULT_MO_SMS_BODY
                send_sms[msg_id]['request'] = {
                    'message': line['log_message'],
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj']}

            if SEND_SMS_RESPONSE in line[
                'log_message'] and SEND_SMS_EXPECT_MORE not in line[
                    'log_message']:
                if msg_id not in send_sms:
                    continue

                if 'request' not in send_sms[msg_id]:
                    continue

                if "error" in line['log_message']:
                    continue

                send_sms[msg_id]['response'] = {
                    'message': line['log_message'],
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj'],
                    'sms_delivery_time': None}

                mo_sms_start_time = send_sms[msg_id]['request'][
                    'datetime_obj']
                mo_sms_end_time = line['datetime_obj']
                sms_delivery_time = mo_sms_end_time - mo_sms_start_time
                send_sms[msg_id]['response'][
                    'sms_delivery_time'] = sms_delivery_time.total_seconds()
                summary.append(
                    {'request': send_sms[msg_id]['request']['message'],
                    'response': send_sms[msg_id]['response']['message'],
                    'unsol_response_new_sms': None,
                    'sms_body': send_sms[msg_id]['sms_body'],
                    'mo_start': mo_sms_start_time,
                    'mo_end': mo_sms_end_time,
                    'mo_signal_duration': sms_delivery_time.total_seconds(),
                    'delivery_time': None})

    duration_list = []
    for item in summary:
        if 'mo_signal_duration' in item:
            duration_list.append(item['mo_signal_duration'])

    try:
        avg_setup_time = statistics.mean(duration_list)
    except:
        avg_setup_time = None

    return send_sms, summary, avg_setup_time


def parse_mo_sms_iwlan(logcat):
    """Search in logcat for lines containing messages about SMS sending on
        iwlan.

    Args:
        logcat: List containing lines of logcat

    Returns:
        send_sms: Dictionary containing found lines for each SMS
            request and response messages together with their time stamps.
            {
                'message_id':{
                    'request':{
                        'message': logcat message body of SMS request
                        'time_stamp': time stamp in text format
                        'datetime_obj': datetime object of the time stamp
                    },
                    'response':{
                        'message': logcat message body of SMS response
                        'time_stamp': time stamp in text format
                        'datetime_obj': datetime object of the time stamp
                        'sms_delivery_time': time between SMS request and
                            response
                    }
                }
            }

        summary: List containing dictionaries for each SMS. The format is
            listed below:
            [
                {
                    'request': logcat message body of SMS request
                    'response': logcat message body of SMS response
                    'sms_body': message body of SMS
                    'mo_start': time stamp of MO SMS request message
                    'mo_end': time stamp of MO SMS response message
                    'mo_signal_duration': time between MO SMS request and
                        response
                    'delivery_time': time between MO SMS request and
                        MT SMS received message
                }
            ]

        avg_setup_time: average of mo_signal_duration
    """
    send_sms = {}
    summary = []
    sms_body = DEFAULT_MO_SMS_BODY
    msg_id = None

    if not logcat:
        return False

    for line in logcat:
        res = re.findall(MO_SMS_LOGCAT_PATTERN, line['log_message'])
        if res:
            try:
                sms_body = res[0]
            except:
                sms_body = 'Cannot find MO SMS body'

        if SEND_SMS_REQUEST_OVER_IMS in line['log_message']:
            if msg_id is None:
                msg_id = '0'
            else:
                msg_id = str(int(msg_id) + 1)

            if msg_id not in send_sms:
                send_sms[msg_id] = {}

            send_sms[msg_id]['sms_body'] = sms_body
            sms_body = DEFAULT_MO_SMS_BODY
            send_sms[msg_id]['request'] = {
                'message': line['log_message'],
                'time_stamp': line['time_stamp'],
                'datetime_obj': line['datetime_obj']}

        if SEND_SMS_RESPONSE_OVER_IMS in line['log_message']:

            if msg_id not in send_sms:
                continue

            if 'request' not in send_sms[msg_id]:
                continue

            if "error" in line['log_message']:
                continue

            send_sms[msg_id]['response'] = {
                'message': line['log_message'],
                'time_stamp': line['time_stamp'],
                'datetime_obj': line['datetime_obj'],
                'sms_delivery_time': None}

            mo_sms_start_time = send_sms[msg_id]['request'][
                'datetime_obj']
            mo_sms_end_time = line['datetime_obj']
            sms_delivery_time = mo_sms_end_time - mo_sms_start_time
            send_sms[msg_id]['response'][
                'sms_delivery_time'] = sms_delivery_time.total_seconds()
            summary.append(
                {'request': send_sms[msg_id]['request']['message'],
                'response': send_sms[msg_id]['response']['message'],
                'unsol_response_new_sms': None,
                'sms_body': send_sms[msg_id]['sms_body'],
                'mo_start': mo_sms_start_time,
                'mo_end': mo_sms_end_time,
                'mo_signal_duration': sms_delivery_time.total_seconds(),
                'delivery_time': None})

    duration_list = []
    for item in summary:
        if 'mo_signal_duration' in item:
            duration_list.append(item['mo_signal_duration'])

    try:
        avg_setup_time = statistics.mean(duration_list)
    except:
        avg_setup_time = None

    return send_sms, summary, avg_setup_time


def parse_mt_sms(logcat):
    """Search in logcat for lines containing messages about SMS receiving on
        LTE.

    Args:
        logcat: List containing lines of logcat

    Returns:
        received_sms_list: List containing dictionaries for each received
            SMS. The format is listed below:
        [
            {
                'message': logcat message body of unsolicited response
                    message
                'sms_body': message body of SMS
                'time_stamp': time stamp of unsolicited response message in
                        text format
                'datetime_obj': datetime object of the time stamp
                'sms_delivery_time': time between SMS request and
                    response
            }
        ]
    """
    received_sms_list = []
    if not logcat:
        return False

    for line in logcat:
        if UNSOL_RESPONSE_NEW_SMS in line['log_message']:

            # if received_sms_list:
            #     if received_sms_list[-1]['sms_body'] is None:
            #         del received_sms_list[-1]

            received_sms_list.append(
                {'message': line['log_message'],
                'sms_body': DEFAULT_MT_SMS_BODY,
                'time_stamp': line['time_stamp'],
                'datetime_obj': line['datetime_obj']})
        else:
            res = re.findall(MT_SMS_CONTENT_PATTERN, line['log_message'])

            if res:
                try:
                    sms_body = res[0]
                except:
                    sms_body = 'Cannot find MT SMS body'

                if received_sms_list[-1]['sms_body'] == DEFAULT_MT_SMS_BODY:
                    received_sms_list[-1]['sms_body'] = sms_body
                    continue

    return received_sms_list


def parse_mt_sms_iwlan(logcat):
    """Search in logcat for lines containing messages about SMS receiving on
        iwlan.

    Args:
        logcat: List containing lines of logcat

    Returns:
        received_sms_list: List containing dictionaries for each received
            SMS. The format is listed below:
        [
            {
                'message': logcat message body of SMS received message
                'sms_body': message body of SMS
                'time_stamp': time stamp of SMS received message in
                        text format
                'datetime_obj': datetime object of the time stamp
            }
        ]
    """
    received_sms_list = []
    if not logcat:
        return False

    for line in logcat:
        if re.findall(
            SMS_RECEIVED_OVER_IMS_SLOT0 + '|' + SMS_RECEIVED_OVER_IMS_SLOT1,
            line['log_message']):
            received_sms_list.append(
                {'message': line['log_message'],
                'sms_body': DEFAULT_MT_SMS_BODY,
                'time_stamp': line['time_stamp'],
                'datetime_obj': line['datetime_obj']})
        else:
            res = re.findall(MT_SMS_CONTENT_PATTERN, line['log_message'])

            if res:
                try:
                    sms_body = res[0]
                except:
                    sms_body = 'Cannot find MT SMS body'

                if received_sms_list[-1]['sms_body'] == DEFAULT_MT_SMS_BODY:
                    received_sms_list[-1]['sms_body'] = sms_body
                    continue

    return received_sms_list


def parse_sms_delivery_time(log, ad_mo, ad_mt, rat='4g'):
    """Calculate the SMS delivery time (time between MO SMS request and MT
        unsolicited response message or MT SMS received message) from logcat
        of both MO and MT UE.

    Args:
        ad_mo: MO Android object
        ad_mt: MT Android object
        rat: '4g' for LTE and 'iwlan' for iwlan

    Returns:
        None
    """
    ad_mo.log.info('====== Start to search logcat ====== ')
    mo_logcat = ad_mo.search_logcat(
        r'%s\|%s\|%s\|%s' % (
            SMS_SEND_TEXT_MESSAGE,
            SEND_SMS,
            SEND_SMS_REQUEST_OVER_IMS,
            SEND_SMS_RESPONSE_OVER_IMS))
    ad_mt.log.info('====== Start to search logcat ====== ')
    mt_logcat = ad_mt.search_logcat(
        r'%s\|%s\|%s' % (
            UNSOL_RESPONSE_NEW_SMS, SMS_RECEIVED, SMS_RECEIVED_OVER_IMS))

    for msg in mo_logcat:
        ad_mo.log.info(msg["log_message"])
    for msg in mt_logcat:
        ad_mt.log.info(msg["log_message"])

    if rat == 'iwlan':
        _, mo_sms_summary, avg = parse_mo_sms_iwlan(mo_logcat)
        received_sms_list = parse_mt_sms_iwlan(mt_logcat)
    else:
        _, mo_sms_summary, avg = parse_mo_sms(mo_logcat)
        received_sms_list = parse_mt_sms(mt_logcat)

    sms_delivery_time = []
    for mo_sms in mo_sms_summary:
        for mt_sms in received_sms_list:
            if mo_sms['sms_body'] == mt_sms['sms_body']:
                mo_sms['delivery_time'] = (
                    mt_sms['datetime_obj'] - mo_sms['mo_start']).total_seconds()
                mo_sms['unsol_response_new_sms'] = mt_sms['message']
                sms_delivery_time.append(mo_sms['delivery_time'])

    try:
        avg_sms_delivery_time = statistics.mean(sms_delivery_time)
    except:
        avg_sms_delivery_time = None

    ad_mo.log.info('====== MO SMS summary ======')
    for item in mo_sms_summary:
        ad_mo.log.info('------------------')
        print_nested_dict(ad_mo, item)
    ad_mt.log.info('====== Received SMS list ======')
    for item in received_sms_list:
        ad_mt.log.info('------------------')
        print_nested_dict(ad_mt, item)

    ad_mo.log.info('%s SMS were actually sent.', len(mo_sms_summary))
    ad_mt.log.info('%s SMS were actually received.', len(received_sms_list))
    ad_mo.log.info('Average MO SMS setup time: %.2f sec.', avg)
    log.info(
        'Average SMS delivery time: %.2f sec.', avg_sms_delivery_time)


def parse_mms(ad_mo, ad_mt):
    """Search in logcat for lines containing messages about SMS sending and
        receiving. Calculate MO & MT MMS setup time.

    Args:
        ad_mo: MO Android object
        ad_mt: MT Android object

    Returns:
        send_mms: Dictionary containing each sent MMS. The format is shown
            as below:
            {
                mms_msg_id:
                {
                    MMS_START_NEW_NW_REQUEST:
                    {
                        'time_stamp': time stamp of MMS request on MO UE in
                        text format
                        'datetime_obj': datetime object of time stamp
                    },
                    MMS_200_OK:
                    {
                        'time_stamp': time stamp of '200 OK' for MMS request
                        in text format
                        'datetime_obj': datetime object of time stamp
                        'setup_time': MO MMS setup time. Time between MMS
                        request and 200 OK
                    }
                }

            }

        mo_avg_setup_time: average of MO MMS setup time

        receive_mms： Dictionary containing each received MMS. The format is
            shown as below:
            {
                mms_msg_id:
                {
                    MMS_START_NEW_NW_REQUEST:
                    {
                        'time_stamp': time stamp of MMS request on MT UE in
                        text format
                        'datetime_obj': datetime object of time stamp
                    },
                    MMS_200_OK:
                    {
                        'time_stamp': time stamp of '200 OK' for MMS request
                        in text format
                        'datetime_obj': datetime object of time stamp
                        'setup_time': MT MMS setup time. Time between MMS
                        request and 200 OK
                    }
                }

            }

        mt_avg_setup_time: average of MT MMS setup time
    """
    send_mms = {}
    receive_mms = {}
    mo_setup_time_list = []
    mt_setup_time_list = []

    ad_mo.log.info('====== Start to search logcat ====== ')
    mo_logcat = ad_mo.search_logcat(MMS_SERVICE)
    for msg in mo_logcat:
        ad_mo.log.info(msg["log_message"])

    ad_mt.log.info('====== Start to search logcat ====== ')
    mt_logcat = ad_mt.search_logcat(MMS_SERVICE)
    for msg in mt_logcat:
        ad_mt.log.info(msg["log_message"])

    if not mo_logcat or not mt_logcat:
        return False

    for line in mo_logcat:
        find_res = re.findall(
            MMS_SEND_REQUEST_ID_PATTERN, line['log_message'])

        message_id = None
        try:
            message_id = find_res[0]
        except:
            pass

        if message_id:
            mms_msg_id = message_id
            if mms_msg_id not in send_mms:
                send_mms[mms_msg_id] = {}
            if MMS_START_NEW_NW_REQUEST in line['log_message']:
                send_mms[mms_msg_id][MMS_START_NEW_NW_REQUEST] = {
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj']}

            if MMS_200_OK in line['log_message']:
                send_mms[mms_msg_id][MMS_200_OK] = {
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj'],
                    'setup_time': None}

                if MMS_START_NEW_NW_REQUEST in send_mms[mms_msg_id]:
                    setup_time = line['datetime_obj'] - send_mms[mms_msg_id][
                        MMS_START_NEW_NW_REQUEST]['datetime_obj']
                    send_mms[mms_msg_id][MMS_200_OK][
                        'setup_time'] = setup_time.total_seconds()
                    mo_setup_time_list.append(setup_time.total_seconds())

    for line in mt_logcat:
        find_res = re.findall(
            MMS_DOWNLOAD_REQUEST_ID_PATTERN, line['log_message'])

        message_id = None
        try:
            message_id = find_res[0]
        except:
            pass

        if message_id:
            mms_msg_id = message_id
            if mms_msg_id not in receive_mms:
                receive_mms[mms_msg_id] = {}
            if MMS_START_NEW_NW_REQUEST in line['log_message']:
                receive_mms[mms_msg_id][MMS_START_NEW_NW_REQUEST] = {
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj']}

            if MMS_200_OK in line['log_message']:
                receive_mms[mms_msg_id][MMS_200_OK] = {
                    'time_stamp': line['time_stamp'],
                    'datetime_obj': line['datetime_obj'],
                    'setup_time': None}

                if MMS_START_NEW_NW_REQUEST in receive_mms[mms_msg_id]:
                    setup_time = line['datetime_obj'] - receive_mms[
                        mms_msg_id][MMS_START_NEW_NW_REQUEST]['datetime_obj']
                    receive_mms[mms_msg_id][MMS_200_OK][
                        'setup_time'] = setup_time.total_seconds()
                    mt_setup_time_list.append(setup_time.total_seconds())

    try:
        mo_avg_setup_time = statistics.mean(mo_setup_time_list)
    except:
        mo_avg_setup_time = None

    try:
        mt_avg_setup_time = statistics.mean(mt_setup_time_list)
    except:
        mt_avg_setup_time = None

    return send_mms, mo_avg_setup_time, receive_mms, mt_avg_setup_time


def parse_cst_reg(ad, slot, search_intervals=None):
    """ Check if IMS CST and WFC is registered at given slot by parsing logcat.

        Args:
            ad: Android object
            slot: 0 for pSIM and 1 for eSIM
            search_intervals: List. Only lines with time stamp in given time
                intervals will be parsed.
                E.g., [(begin_time1, end_time1), (begin_time2, end_time2)]
                Both begin_time and end_time should be datetime object.

        Returns: List of attampt number and error messages of not found pattern
            of failing cycles
    """
    cst = {
        'ims_registered': {
            '0': IMS_REGISTERED_CST_SLOT0,
            '1': IMS_REGISTERED_CST_SLOT1
        },
        'iwlan': {
            '0': ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT0,
            '1': ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT1
        }
    }
    ad.log.info('====== Start to search logcat ====== ')
    logcat = ad.search_logcat(
        '%s\|%s' % (
            cst['ims_registered'][str(slot)], cst['iwlan'][str(slot)]))

    for line in logcat:
        msg = line["log_message"]
        ad.log.info(msg)

    parsing_fail = []
    keyword_dict = {
        'ims_registered': cst['ims_registered'][str(slot)],
        'iwlan': cst['iwlan'][str(slot)]
    }
    for attempt, interval in enumerate(search_intervals):
        if isinstance(interval, list):
            try:
                begin_time, end_time = interval
            except Exception as e:
                ad.log.error(e)
                continue

            ad.log.info('Parsing begin time: %s', begin_time)
            ad.log.info('Parsing end time: %s', end_time)

            temp_keyword_dict = copy.deepcopy(keyword_dict)
            for line in logcat:
                if begin_time and line['datetime_obj'] < begin_time:
                    continue

                if end_time and line['datetime_obj'] > end_time:
                    break

                for key in temp_keyword_dict:
                    if temp_keyword_dict[key] and not isinstance(
                        temp_keyword_dict[key], dict):
                        res = re.findall(
                            temp_keyword_dict[key], line['log_message'])
                        if res:
                            ad.log.info('Found: %s', line['log_message'])
                            temp_keyword_dict[key] = {
                                'message': line['log_message'],
                                'time_stamp': line['datetime_obj']}
                            break

            for key in temp_keyword_dict:
                if temp_keyword_dict[key] == keyword_dict[key]:
                    ad.log.error(
                        '"%s" is missing in cycle %s.',
                        keyword_dict[key],
                        attempt+1)
                    parsing_fail.append({
                        'attempt': attempt+1,
                        'missing_msg': keyword_dict[key]})

    return parsing_fail


def check_ims_cst_reg(ad, slot, search_interval=None):
    """ Check if IMS CST is registered at given slot by parsing logcat.

        Args:
            ad: Android object
            slot: 0 for pSIM and 1 for eSIM
            search_intervals: List. Only lines with time stamp in given time
                intervals will be parsed.
                E.g., [(begin_time1, end_time1), (begin_time2, end_time2)]
                Both begin_time and end_time should be datetime object.

        Returns: True for successful registration. Otherwise False
    """
    ims_cst_reg = {
        '0': IMS_REGISTERED_CST_SLOT0,
        '1': IMS_REGISTERED_CST_SLOT1
    }
    logcat = ad.search_logcat('%s' % ims_cst_reg[str(slot)])
    if isinstance(search_interval, list):
        try:
            begin_time, end_time = search_interval
        except Exception as e:
            ad.log.error(e)

        for line in logcat:
            if begin_time and line['datetime_obj'] < begin_time:
                continue

            if end_time and line['datetime_obj'] > end_time:
                break

            res = re.findall(ims_cst_reg[str(slot)], line['log_message'])
            if res:
                ad.log.info(
                    'IMS CST is registered due to following message '
                    'found: %s', line['log_message'])
                return True
    return False