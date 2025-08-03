#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

"""Generic telephony utility functions. Cloned from test_utils.tel."""

import re
import struct
import time
from queue import Empty

from acts.logger import epoch_to_log_line_timestamp
from acts.controllers.adb_lib.error import AdbCommandError

INCALL_UI_DISPLAY_FOREGROUND = "foreground"
INCALL_UI_DISPLAY_BACKGROUND = "background"
INCALL_UI_DISPLAY_DEFAULT = "default"

# Max time to wait after caller make a call and before
# callee start ringing
MAX_WAIT_TIME_ACCEPT_CALL_TO_OFFHOOK_EVENT = 30

# Max time to wait after toggle airplane mode and before
# get expected event
MAX_WAIT_TIME_AIRPLANEMODE_EVENT = 90

# Wait time between state check retry
WAIT_TIME_BETWEEN_STATE_CHECK = 5

# Constant for Data Roaming State
DATA_ROAMING_ENABLE = 1
DATA_ROAMING_DISABLE = 0

# Constant for Telephony Manager Call State
TELEPHONY_STATE_RINGING = "RINGING"
TELEPHONY_STATE_IDLE = "IDLE"
TELEPHONY_STATE_OFFHOOK = "OFFHOOK"
TELEPHONY_STATE_UNKNOWN = "UNKNOWN"

# Constant for Service State
SERVICE_STATE_EMERGENCY_ONLY = "EMERGENCY_ONLY"
SERVICE_STATE_IN_SERVICE = "IN_SERVICE"
SERVICE_STATE_OUT_OF_SERVICE = "OUT_OF_SERVICE"
SERVICE_STATE_POWER_OFF = "POWER_OFF"
SERVICE_STATE_UNKNOWN = "UNKNOWN"

# Constant for Network Mode
NETWORK_MODE_GSM_ONLY = "NETWORK_MODE_GSM_ONLY"
NETWORK_MODE_WCDMA_ONLY = "NETWORK_MODE_WCDMA_ONLY"
NETWORK_MODE_LTE_ONLY = "NETWORK_MODE_LTE_ONLY"

# Constant for Events
EVENT_CALL_STATE_CHANGED = "CallStateChanged"
EVENT_SERVICE_STATE_CHANGED = "ServiceStateChanged"


class CallStateContainer:
    INCOMING_NUMBER = "incomingNumber"
    SUBSCRIPTION_ID = "subscriptionId"
    CALL_STATE = "callState"


class ServiceStateContainer:
    VOICE_REG_STATE = "voiceRegState"
    VOICE_NETWORK_TYPE = "voiceNetworkType"
    DATA_REG_STATE = "dataRegState"
    DATA_NETWORK_TYPE = "dataNetworkType"
    OPERATOR_NAME = "operatorName"
    OPERATOR_ID = "operatorId"
    IS_MANUAL_NW_SELECTION = "isManualNwSelection"
    ROAMING = "roaming"
    IS_EMERGENCY_ONLY = "isEmergencyOnly"
    NETWORK_ID = "networkId"
    SYSTEM_ID = "systemId"
    SUBSCRIPTION_ID = "subscriptionId"
    SERVICE_STATE = "serviceState"


def dumpsys_last_call_info(ad):
    """ Get call information by dumpsys telecom. """
    num = dumpsys_last_call_number(ad)
    output = ad.adb.shell("dumpsys telecom")
    result = re.search(r"Call TC@%s: {(.*?)}" % num, output, re.DOTALL)
    call_info = {"TC": num}
    if result:
        result = result.group(1)
        for attr in ("startTime", "endTime", "direction", "isInterrupted",
                     "callTechnologies", "callTerminationsReason",
                     "isVideoCall", "callProperties"):
            match = re.search(r"%s: (.*)" % attr, result)
            if match:
                if attr in ("startTime", "endTime"):
                    call_info[attr] = epoch_to_log_line_timestamp(
                        int(match.group(1)))
                else:
                    call_info[attr] = match.group(1)
    ad.log.debug("call_info = %s", call_info)
    return call_info


def dumpsys_last_call_number(ad):
    output = ad.adb.shell("dumpsys telecom")
    call_nums = re.findall("Call TC@(\d+):", output)
    if not call_nums:
        return 0
    else:
        return int(call_nums[-1])


def get_device_epoch_time(ad):
    return int(1000 * float(ad.adb.shell("date +%s.%N")))


def get_outgoing_voice_sub_id(ad):
    """ Get outgoing voice subscription id
    """
    if hasattr(ad, "outgoing_voice_sub_id"):
        return ad.outgoing_voice_sub_id
    else:
        return ad.droid.subscriptionGetDefaultVoiceSubId()


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
    try:
        output = ad.adb.shell(cmd)
    except AdbCommandError as e:
        log.error(e)
        output = None

    if not output or 'result=SUCCESS' not in output:
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


def initiate_call(log,
                  ad,
                  callee_number,
                  emergency=False,
                  incall_ui_display=INCALL_UI_DISPLAY_FOREGROUND,
                  video=False):
    """Make phone call from caller to callee.

    Args:
        log: log object.
        ad: Caller android device object.
        callee_number: Callee phone number.
        emergency : specify the call is emergency.
            Optional. Default value is False.
        incall_ui_display: show the dialer UI foreground or background
        video: whether to initiate as video call

    Returns:
        result: if phone call is placed successfully.
    """
    ad.ed.clear_events(EVENT_CALL_STATE_CHANGED)
    sub_id = get_outgoing_voice_sub_id(ad)
    begin_time = get_device_epoch_time(ad)
    ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)
    try:
        # Make a Call
        ad.log.info("Make a phone call to %s", callee_number)
        if emergency:
            ad.droid.telecomCallEmergencyNumber(callee_number)
        else:
            ad.droid.telecomCallNumber(callee_number, video)

        # Verify OFFHOOK state
        if not wait_for_call_offhook_for_subscription(
                log, ad, sub_id, event_tracking_started=True):
            ad.log.info("sub_id %s not in call offhook state", sub_id)
            last_call_drop_reason(ad, begin_time=begin_time)
            return False
        else:
            return True
    finally:
        if hasattr(ad, "sdm_log") and getattr(ad, "sdm_log"):
            ad.adb.shell("i2cset -fy 3 64 6 1 b", ignore_status=True)
            ad.adb.shell("i2cset -fy 3 65 6 1 b", ignore_status=True)
        ad.droid.telephonyStopTrackingCallStateChangeForSubscription(sub_id)
        if incall_ui_display == INCALL_UI_DISPLAY_FOREGROUND:
            ad.droid.telecomShowInCallScreen()
        elif incall_ui_display == INCALL_UI_DISPLAY_BACKGROUND:
            ad.droid.showHomeScreen()


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


def is_phone_in_call(log, ad):
    """Return True if phone in call.

    Args:
        log: log object.
        ad:  android device.
    """
    try:
        return ad.droid.telecomIsInCall()
    except:
        return "mCallState=2" in ad.adb.shell(
            "dumpsys telephony.registry | grep mCallState")


def last_call_drop_reason(ad, begin_time=None):
    reasons = ad.search_logcat(
        "qcril_qmi_voice_map_qmi_to_ril_last_call_failure_cause", begin_time)
    reason_string = ""
    if reasons:
        log_msg = "Logcat call drop reasons:"
        for reason in reasons:
            log_msg = "%s\n\t%s" % (log_msg, reason["log_message"])
            if "ril reason str" in reason["log_message"]:
                reason_string = reason["log_message"].split(":")[-1].strip()
        ad.log.info(log_msg)
    reasons = ad.search_logcat("ACTION_FORBIDDEN_NO_SERVICE_AUTHORIZATION",
                               begin_time)
    if reasons:
        ad.log.warning("ACTION_FORBIDDEN_NO_SERVICE_AUTHORIZATION is seen")
    ad.log.info("last call dumpsys: %s",
                sorted(dumpsys_last_call_info(ad).items()))
    return reason_string


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


def toggle_airplane_mode_by_adb(log, ad, new_state=None):
    """ Toggle the state of airplane mode.

    Args:
        log: log handler.
        ad: android_device object.
        new_state: Airplane mode state to set to.
            If None, opposite of the current state.

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
        ad.adb.shell("am broadcast -a android.intent.action.AIRPLANE_MODE")
    except Exception as e:
        ad.log.error(e)
        return False
    changed_state = bool(int(ad.adb.shell("settings get global airplane_mode_on")))
    return changed_state == new_state


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
                EVENT_SERVICE_STATE_CHANGED,
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


def toggle_cell_data_roaming(ad, state):
    """Enable cell data roaming for default data subscription.

    Wait for the data roaming status to be DATA_STATE_CONNECTED
        or DATA_STATE_DISCONNECTED.

    Args:
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


def wait_for_call_offhook_event(
        log,
        ad,
        sub_id,
        event_tracking_started=False,
        timeout=MAX_WAIT_TIME_ACCEPT_CALL_TO_OFFHOOK_EVENT):
    """Wait for an incoming call on specified subscription.

    Args:
        log: log object.
        ad: android device object.
        event_tracking_started: True if event tracking already state outside
        timeout: time to wait for event

    Returns:
        True: if call offhook event is received.
        False: if call offhook event is not received.
    """
    if not event_tracking_started:
        ad.ed.clear_events(EVENT_CALL_STATE_CHANGED)
        ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)
    try:
        ad.ed.wait_for_event(
            EVENT_CALL_STATE_CHANGED,
            is_event_match,
            timeout=timeout,
            field=CallStateContainer.CALL_STATE,
            value=TELEPHONY_STATE_OFFHOOK)
        ad.log.info("Got event %s", TELEPHONY_STATE_OFFHOOK)
    except Empty:
        ad.log.info("No event for call state change to OFFHOOK")
        return False
    finally:
        if not event_tracking_started:
            ad.droid.telephonyStopTrackingCallStateChangeForSubscription(
                sub_id)
    return True


def wait_for_call_offhook_for_subscription(
        log,
        ad,
        sub_id,
        event_tracking_started=False,
        timeout=MAX_WAIT_TIME_ACCEPT_CALL_TO_OFFHOOK_EVENT,
        interval=WAIT_TIME_BETWEEN_STATE_CHECK):
    """Wait for an incoming call on specified subscription.

    Args:
        log: log object.
        ad: android device object.
        sub_id: subscription ID
        timeout: time to wait for ring
        interval: checking interval

    Returns:
        True: if incoming call is received and answered successfully.
        False: for errors
    """
    if not event_tracking_started:
        ad.ed.clear_events(EVENT_CALL_STATE_CHANGED)
        ad.droid.telephonyStartTrackingCallStateForSubscription(sub_id)
    offhook_event_received = False
    end_time = time.time() + timeout
    try:
        while time.time() < end_time:
            if not offhook_event_received:
                if wait_for_call_offhook_event(log, ad, sub_id, True,
                                               interval):
                    offhook_event_received = True
            telephony_state = ad.droid.telephonyGetCallStateForSubscription(
                sub_id)
            telecom_state = ad.droid.telecomGetCallState()
            if telephony_state == TELEPHONY_STATE_OFFHOOK and (
                    telecom_state == TELEPHONY_STATE_OFFHOOK):
                ad.log.info("telephony and telecom are in OFFHOOK state")
                return True
            else:
                ad.log.info(
                    "telephony in %s, telecom in %s, expecting OFFHOOK state",
                    telephony_state, telecom_state)
            if offhook_event_received:
                time.sleep(interval)
    finally:
        if not event_tracking_started:
            ad.droid.telephonyStopTrackingCallStateChangeForSubscription(
                sub_id)


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


def wait_for_droid_in_call(log, ad, max_time):
    """Wait for android to be in call state.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        If phone become in call state within max_time, return True.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, is_phone_in_call)


def _wait_for_droid_in_state(log, ad, max_time, state_check_func, *args,
                             **kwargs):
    while max_time >= 0:
        if state_check_func(log, ad, *args, **kwargs):
            return True

        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
        max_time -= WAIT_TIME_BETWEEN_STATE_CHECK

    return False


# TODO: replace this with an event-based function
def _wait_for_wifi_in_state(log, ad, state, max_wait):
    return _wait_for_droid_in_state(log, ad, max_wait,
        lambda log, ad, state: \
                (True if ad.droid.wifiCheckState() == state else False),
                state)
