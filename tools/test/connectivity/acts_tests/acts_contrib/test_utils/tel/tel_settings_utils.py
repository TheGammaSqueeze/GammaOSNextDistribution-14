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

import time
from acts_contrib.test_utils.net import ui_utils
from acts_contrib.test_utils.tel.tel_defines import CHIPSET_MODELS_LIST
from acts_contrib.test_utils.tel.tel_defines import GEN_4G
from acts_contrib.test_utils.tel.tel_defines import GEN_5G
from acts_contrib.test_utils.tel.tel_defines import KEYEVENT_DEL
from acts_contrib.test_utils.tel.tel_defines import MOBILE_DATA
from acts_contrib.test_utils.tel.tel_defines import NETWORK_SERVICE_DATA
from acts_contrib.test_utils.tel.tel_defines import SCROLL_DOWN
from acts_contrib.test_utils.tel.tel_defines import SCROLL_UP
from acts_contrib.test_utils.tel.tel_defines import SLOW_SCROLL_DOWN
from acts_contrib.test_utils.tel.tel_defines import USE_SIM
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_BETWEEN_STATE_CHECK
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_device_for_5g
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte
from acts_contrib.test_utils.tel.tel_test_utils import get_current_override_network_type
from acts_contrib.test_utils.tel.tel_test_utils import is_droid_in_network_generation


ATT_APN = {
    'Name': 'NXTGENPHONE',
    'APN': 'NXTGENPHONE',
    'MMSC': 'http://mmsc.mobile.att.net',
    'MMS proxy': 'proxy.mobile.att.net',
    'MMS port': '80',
    'MCC': '310',
    'MNC': '410',
    'APN type': 'default,mms,supi,hipri',
    'APN protocol': 'IPv4',
    'APN roaming protocol': 'IPv4',
    'MVNO type': 'None'
    }

TMO_APN = {
    'Name': 'TMOUS',
    'APN': 'fast.t-mobile.com',
    'MMSC': 'https://mms.msg.eng.t-mobile.com/mms/wapenc',
    'MCC': '310',
    'MNC': '260',
    'APN type': 'default,supi,ia,mms,xcap',
    'APN protocol': 'IPv6',
    'APN roaming protocol': 'IPv4',
    'MVNO type': 'None'
    }

TMO_BEARER = ['HSPA', 'EVDO_B', 'eHRPD', 'LTE', 'HSPAP', 'GPRS', 'EDGE', 'UMTS', '1xRTT', 'EVDO_0',
            'EVDO_A', 'HSDPA', 'HSUPA', 'IS95A', 'IS95B','NR']


def is_current_build_s(ad):
    """Verify current build is S
    Args:
        ad: android device object.

    Returns:
        True: If Build is S
        False: If Build is not S
    """
    build_id = ad.adb.shell('getprop ro.product.build.id')
    s_build = False
    if build_id[0] == 'S':
       s_build = True
    return s_build

def launch_SIMs_settings(ad):
    """Launch SIMs settings page
    Args:
        ad: android device object.
    """
    ad.adb.shell('am start -a android.settings.WIRELESS_SETTINGS')
    ui_utils.wait_and_click(ad, text='SIMs')

def get_resource_value(ad, label_text= None):
    """Get current resource value

    Args:
        ad:  android device object.
        label_text: Enter text to be detected

    Return:
        node attribute value
    """
    if label_text == USE_SIM:
        resource_id = 'android:id/switch_widget'
        label_resource_id = 'com.android.settings:id/switch_text'
        node_attribute = 'checked'
    elif label_text == MOBILE_DATA:
        resource_id = 'android:id/switch_widget'
        label_resource_id = 'android:id/widget_frame'
        label_text = ''
        node_attribute = 'checked'
    elif label_text == 'MCC' or label_text == 'MNC':
        resource_id = 'android:id/summary'
        label_resource_id = 'android:id/title'
        node_attribute = 'text'
    else:
        ad.log.error(
            'Missing arguments, resource_id, label_text and node_attribute'
            )

    resource = {
        'resource_id': resource_id,
    }
    node = ui_utils.wait_and_get_xml_node(ad,
                                        timeout=30,
                                        sibling=resource,
                                        text=label_text,
                                        resource_id=label_resource_id)
    return node.attributes[node_attribute].value

def toggle_sim_test(ad, nw_gen, nr_type=None):
    """Disable and Enable SIM settings
    Args:
        ad:  android device object.
        nw_gen: network generation the phone should be camped on.
        nr_type: check NR network.
    """
    s_build = is_current_build_s(ad)
    if nw_gen  == GEN_5G:
        if not provision_device_for_5g(ad.log, ad, nr_type=nr_type):
            return False
    elif nw_gen == GEN_4G:
        if not phone_setup_volte(ad.log, ad):
            ad.log.error('Phone failed to enable LTE')
            return False
    launch_SIMs_settings(ad)

    switch_value = get_resource_value(ad, USE_SIM)
    if switch_value == 'true':
        ad.log.info('SIM is enabled as expected')
    else:
        ad.log.error('SIM should be enabled but SIM is disabled')
        return False

    label_text = USE_SIM
    label_resource_id = 'com.android.settings:id/switch_text'

    ad.log.info('Start Disabling SIM')
    ui_utils.wait_and_click(ad,
                            text=label_text,
                            resource_id=label_resource_id)

    button_resource_id = 'android:id/button1'
    if any(model in ad.model for model in CHIPSET_MODELS_LIST) and s_build:
        ui_utils.wait_and_click(ad, text='YES', resource_id=button_resource_id)
    else:
        ui_utils.wait_and_click(ad, text='Yes', resource_id=button_resource_id)

    switch_value = get_resource_value(ad, USE_SIM)
    if switch_value == 'false':
        ad.log.info('SIM is disabled as expected')
    else:
        ad.log.error('SIM should be disabled but SIM is enabled')
        return False

    ad.log.info('Start Enabling SIM')
    ui_utils.wait_and_click(ad,
                            text=label_text,
                            resource_id=label_resource_id)

    if any(model in ad.model for model in CHIPSET_MODELS_LIST) and s_build:
        ui_utils.wait_and_click(ad, text='YES', resource_id=button_resource_id)
    elif any(model in ad.model for model in CHIPSET_MODELS_LIST) and not s_build:
        pass
    else:
        ui_utils.wait_and_click(ad, text='Yes', resource_id=button_resource_id)

    switch_value = get_resource_value(ad, USE_SIM)
    if switch_value == 'true':
        ad.log.info('SIM is enabled as expected')
    else:
        ad.log.error('SIM should be enabled but SIM is disabled')
        return False
    time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)

    if nw_gen  == GEN_5G:
        if not is_current_network_5g(ad, nr_type=nr_type, timeout=60):
            ad.log.error('Unable to connect on 5G network')
            return False
        ad.log.info('Success! attached on 5G')
    elif nw_gen  == GEN_4G:
        if not is_droid_in_network_generation(self.log, ad, GEN_4G,
                                            NETWORK_SERVICE_DATA):
            ad.log.error('Failure - expected LTE, current %s',
                         get_current_override_network_type(ad))
            return False
        ad.log.info('Success! attached on LTE')
    return True

def toggle_mobile_data_test(ad, nw_gen, nr_type=None):
    """Disable and Enable SIM settings
    Args:
        ad:  android device object.
        nw_gen: network generation the phone should be camped on.
        nr_type: check NR network.
    """
    if nw_gen  == GEN_5G:
        if not provision_device_for_5g(ad.log, ad, nr_type=nr_type):
            return False
    elif nw_gen == GEN_4G:
        if not phone_setup_volte(ad.log, ad):
            ad.log.error('Phone failed to enable LTE')
            return False

    launch_SIMs_settings(ad)
    switch_value = get_resource_value(ad, MOBILE_DATA)

    if switch_value != 'true':
        ad.log.error('Mobile data should be enabled but it is disabled')
        return False
    ad.log.info('Mobile data is enabled as expected')

    ad.log.info('Start Disabling mobile data')

    ad.droid.telephonyToggleDataConnection(False)
    time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)

    switch_value = get_resource_value(ad, MOBILE_DATA)
    if switch_value != 'false':
        ad.log.error('Mobile data should be disabled but it is enabled')
        return False
    ad.log.info('Mobile data is disabled as expected')

    ad.log.info('Start Enabling mobile data')
    ad.droid.telephonyToggleDataConnection(True)
    time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)

    switch_value = get_resource_value(ad, MOBILE_DATA)
    if switch_value == 'true':
        ad.log.error('Mobile data should be enabled but it is disabled')
        return False
    ad.log.info('Mobile data is enabled as expected')

    if nw_gen  == GEN_5G:
        if not is_current_network_5g(ad, nr_type=nr_type, timeout=60):
            ad.log.error('Failure - expected NR_NSA, current %s',
                     get_current_override_network_type(ad))
        ad.log.info('Success! attached on 5g NSA')
    elif nw_gen  == GEN_4G:
        if not is_droid_in_network_generation(self.log, ad, GEN_4G,
                                            NETWORK_SERVICE_DATA):
            ad.log.error('Failure - expected LTE, current %s',
                         get_current_override_network_type(ad))
            return False
        ad.log.info('Success! attached on LTE')
    return True

def verify_mcc_mnc_value(ad, current_value, expected_value, key):
    """Verify MCC and MNC value

    Args:
        ad: Android device object.
        current_value: Current value of property.
        expected_value: Expected value of property.
        key: Properties for APN settings either MCC or MNC.
    """
    if current_value != expected_value:
        ad.log.info('Current %s value is %s, change it to %s'
            % (key, current_mcc_value, expected_value))
        ui_utils.wait_and_click(ad, text=key)
        for _ in range(len(current_value)):
            caller.adb.shell(KEYEVENT_DEL)
        ui_utils.wait_and_input_text(ad, expected_value)
        ui_utils.wait_and_click(caller, text='OK', resource_id='android:id/button1')
    ad.log.info('Verified Current %s value matched with required value', key)

def wait_and_input_value(ad, key, value):
    """Enter input value to the key using UI
    Args:
        ad: Android device object.
        key: Properties for APN settings.
        value: Value to be entered for corresponding key.
    """
    ui_utils.wait_and_click(ad, text=key)
    ad.log.info('Enter %s: %s' % (key, value))
    ui_utils.wait_and_input_text(ad, value)
    if key not in ['APN roaming protocol', 'APN protocol', 'MVNO type']:
        ui_utils.wait_and_click(ad, text='OK', resource_id='android:id/button1')

def att_apn_test(log, caller, callee, nw_gen, nr_type=None, msg_type=None):
    """ATT APN Test

    Args:
        log: Log object.
        caller: android device object as caller.
        callee: android device object as callee.
        nw_gen: network generation the phone should be camped on.
        nr_type: check NR network.
        msg_type: messaging type sms or mms
    """
    if nw_gen  == GEN_5G:
        mo_rat='5g'
        if not provision_device_for_5g(caller.log, caller, nr_type=nr_type):
            return False
    elif nw_gen == GEN_4G:
        mo_rat='volte'
        if not phone_setup_volte(caller.log, caller):
            caller.log.error('Phone failed to enable LTE')
            return False
    else:
        mo_rat='general'

    launch_SIMs_settings(caller)

    # Scroll down
    if not ui_utils.has_element(caller, text='Access Point Names'):
        for _ in range(3):
            caller.adb.shell(SCROLL_DOWN)

    ui_utils.wait_and_click(caller, text='Access Point Names')
    ui_utils.wait_and_click(caller, content_desc='New APN')

    wait_and_input_value(caller, 'Name', ATT_APN['Name'])
    wait_and_input_value(caller, 'APN', ATT_APN['APN'])
    wait_and_input_value(caller, 'MMSC', ATT_APN['MMSC'])

    # Scroll down
    caller.adb.shell(SCROLL_DOWN)

    wait_and_input_value(caller, 'MMS proxy', ATT_APN['MMS proxy'])
    wait_and_input_value(caller, 'MMS port', ATT_APN['MMS port'])

    caller.log.info('Enter MCC value: %s', ATT_APN['MCC'])
    current_mcc_value = get_resource_value(caller,'MCC')
    verify_mcc_mnc_value(caller, current_mcc_value, ATT_APN['MCC'], 'MCC')

    # Scroll down
    caller.adb.shell(SCROLL_DOWN)

    caller.log.info('Enter MNC value: %s', ATT_APN['MNC'])
    current_mnc_value = get_resource_value(caller,'MNC')
    verify_mcc_mnc_value(caller, current_mnc_value, ATT_APN['MNC'], 'MNC')

    # Scroll down
    caller.adb.shell(SCROLL_DOWN)

    wait_and_input_value(caller, 'APN type', ATT_APN['APN type'])

    # Scroll down
    caller.adb.shell(SCROLL_DOWN)

    wait_and_input_value(caller, 'APN protocol', ATT_APN['APN protocol'])

    wait_and_input_value(caller, 'APN roaming protocol', ATT_APN['APN roaming protocol'])

    # Scroll down
    caller.adb.shell(SCROLL_DOWN)

    wait_and_input_value(caller, 'MVNO type', ATT_APN['MVNO type'])

    ui_utils.wait_and_click(caller, content_desc='More options')

    ui_utils.wait_and_click(caller, text='Save', resource_id='android:id/title')

    node = ui_utils.wait_and_get_xml_node(caller, timeout=30, text=ATT_APN['Name'])
    bounds = node.parentNode.nextSibling.attributes['bounds'].value

    ui_utils.wait_and_click(caller,
                            text='',
                            resource_id='com.android.settings:id/apn_radiobutton',
                            bounds= bounds)
    time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)

    if msg_type is not None:
        message_test(
            log,
            caller,
            callee,
            mo_rat=mo_rat,
            mt_rat='general',
            msg_type=msg_type)

def tmo_apn_test(log, caller, callee, nw_gen, nr_type=None, msg_type=None):
    """TMO APN Test

    Args:
        log: Log object.
        caller: android device object as caller.
        callee: android device object as callee.
        nw_gen: network generation the phone should be camped on.
        nr_type: check NR network.
        msg_type: messaging type sms or mms
    """
    if nw_gen == GEN_5G:
        mo_rat='5g'
        if not provision_device_for_5g(caller.log, caller, nr_type=nr_type):
            return False
    elif nw_gen == GEN_4G:
        mo_rat='volte'
        if not phone_setup_volte(caller.log, caller):
            caller.log.error('Phone failed to enable LTE')
            return False
    else:
        mo_rat='general'

    launch_SIMs_settings(caller)
    time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)

    if not ui_utils.has_element(caller, text='Access Point Names'):
        for _ in range(5):
            caller.adb.shell(SCROLL_DOWN)

    ui_utils.wait_and_click(caller, text='Access Point Names')
    ui_utils.wait_and_click(caller, content_desc='New APN')

    wait_and_input_value(caller, 'Name', TMO_APN['Name'])
    wait_and_input_value(caller, 'APN', TMO_APN['APN'])
    wait_and_input_value(caller, 'MMSC', TMO_APN['MMSC'])

    # Scroll down
    caller.adb.shell(SLOW_SCROLL_DOWN)

    caller.log.info('Enter MCC value: %s', TMO_APN['MCC'])
    current_mcc_value = get_resource_value(caller,'MCC')
    verify_mcc_mnc_value(caller, current_mcc_value, TMO_APN['MCC'], 'MCC')

    # Scroll down
    caller.adb.shell(SLOW_SCROLL_DOWN)

    caller.log.info('Enter MNC value: %s', TMO_APN['MNC'])
    current_mnc_value = get_resource_value(caller,'MNC')
    verify_mcc_mnc_value(caller, current_mnc_value, TMO_APN['MNC'], 'MNC')

    wait_and_input_value(caller, 'APN type', TMO_APN['APN type'])

    # Scroll down
    caller.adb.shell(SLOW_SCROLL_DOWN)

    wait_and_input_value(caller, 'APN protocol', TMO_APN['APN protocol'])
    wait_and_input_value(caller, 'APN roaming protocol', TMO_APN['APN roaming protocol'])

    wait_and_input_value(caller, 'MVNO type', TMO_APN['MVNO type'])

    caller.log.info('Click following supported network: %s', TMO_BEARER)
    ui_utils.wait_and_click(caller, text='Bearer')
    for network in TMO_BEARER:
        if ((network == 'NR') or
            (network == 'IS95B') or
            (network ==  'IS95A') or
            (network ==  'EVDO_0') or
            (network ==  '1xRTT')):
            # Scroll down
            caller.adb.shell(SLOW_SCROLL_DOWN)
            ui_utils.wait_and_click(caller, text=network)
            caller.log.info('Enabled %s', network)
            time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
            # Scroll up
            caller.adb.shell(SCROLL_UP)
        else:
            ui_utils.wait_and_click(caller, text=network)
            caller.log.info('Enabled %s', network)
        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)

    ui_utils.wait_and_click(caller, text='OK', resource_id='android:id/button1')

    ui_utils.wait_and_click(caller, content_desc='More options')

    ui_utils.wait_and_click(caller, text='Save', resource_id='android:id/title')

    node = ui_utils.wait_and_get_xml_node(caller, timeout=30, text=TMO_APN['Name'])
    bounds = node.parentNode.nextSibling.attributes['bounds'].value
    caller.log.info('Bounds: %s', bounds)
    ui_utils.wait_and_click(caller,
                            text='',
                            resource_id='com.android.settings:id/apn_radiobutton',
                            bounds= bounds)
    time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)

    if msg_type is not None:
        message_test(
            log,
            caller,
            callee,
            mo_rat=mo_rat,
            mt_rat='general',
            msg_type=msg_type)
