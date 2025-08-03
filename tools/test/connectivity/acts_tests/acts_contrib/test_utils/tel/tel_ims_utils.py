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
from acts_contrib.test_utils.tel.tel_subscription_utils import get_incoming_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import set_incoming_voice_sub_id
from acts_contrib.test_utils.tel.tel_defines import CARRIER_FRE
from acts_contrib.test_utils.tel.tel_defines import INVALID_SUB_ID
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_FOR_STATE_CHANGE
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_VOLTE_ENABLED
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_WFC_DISABLED
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_WFC_ENABLED
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_BETWEEN_STATE_CHECK
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_DISABLED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_ONLY
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import CARRIER_VZW, CARRIER_ATT, \
    CARRIER_BELL, CARRIER_ROGERS, CARRIER_KOODO, CARRIER_VIDEOTRON, CARRIER_TELUS
from acts_contrib.test_utils.tel.tel_logging_utils import start_adb_tcpdump
from acts_contrib.test_utils.tel.tel_test_utils import _wait_for_droid_in_state
from acts_contrib.test_utils.tel.tel_test_utils import _wait_for_droid_in_state_for_subscription
from acts_contrib.test_utils.tel.tel_test_utils import bring_up_sl4a
from acts_contrib.test_utils.tel.tel_test_utils import change_voice_subid_temporarily
from acts_contrib.test_utils.tel.tel_test_utils import get_operator_name
from acts_contrib.test_utils.tel.tel_test_utils import wait_for_state


class TelImsUtilsError(Exception):
    pass


def show_enhanced_4g_lte(ad, sub_id):
    result = True
    capabilities = ad.telephony["subscription"][sub_id].get("capabilities", [])
    if capabilities:
        if "hide_enhanced_4g_lte" in capabilities:
            result = False
            ad.log.info(
                '"Enhanced 4G LTE MODE" is hidden for sub ID %s.', sub_id)
            show_enhanced_4g_lte_mode = getattr(
                ad, "show_enhanced_4g_lte_mode", False)
            if show_enhanced_4g_lte_mode in ["true", "True"]:
                current_voice_sub_id = get_outgoing_voice_sub_id(ad)
                if sub_id != current_voice_sub_id:
                    set_incoming_voice_sub_id(ad, sub_id)

                ad.log.info(
                    'Show "Enhanced 4G LTE MODE" forcibly for sub ID %s.',
                    sub_id)
                ad.adb.shell(
                    "am broadcast \
                        -a com.google.android.carrier.action.LOCAL_OVERRIDE \
                        -n com.google.android.carrier/.ConfigOverridingReceiver \
                        --ez hide_enhanced_4g_lte_bool false")
                ad.telephony["subscription"][sub_id]["capabilities"].remove(
                    "hide_enhanced_4g_lte")

                if sub_id != current_voice_sub_id:
                    set_incoming_voice_sub_id(ad, current_voice_sub_id)

                result = True
    return result


def toggle_volte(log, ad, new_state=None):
    """Toggle enable/disable VoLTE for default voice subscription.

    Args:
        ad: Android device object.
        new_state: VoLTE mode state to set to.
            True for enable, False for disable.
            If None, opposite of the current state.

    Raises:
        TelImsUtilsError if platform does not support VoLTE.
    """
    return toggle_volte_for_subscription(
        log, ad, get_outgoing_voice_sub_id(ad), new_state)


def toggle_volte_for_subscription(log, ad, sub_id, new_state=None):
    """Toggle enable/disable VoLTE for specified voice subscription.

    Args:
        ad: Android device object.
        sub_id: Optional. If not assigned the default sub ID for voice call will
            be used.
        new_state: VoLTE mode state to set to.
            True for enable, False for disable.
            If None, opposite of the current state.
    """
    if not show_enhanced_4g_lte(ad, sub_id):
        return False

    current_state = None
    result = True

    if sub_id is None:
        sub_id = ad.droid.subscriptionGetDefaultVoiceSubId()

    try:
        current_state = ad.droid.imsMmTelIsAdvancedCallingEnabled(sub_id)
    except Exception as e:
        ad.log.warning(e)

    if current_state is not None:
        if new_state is None:
            new_state = not current_state
        if new_state != current_state:
            ad.log.info(
                "Toggle Enhanced 4G LTE Mode from %s to %s on sub_id %s",
                current_state, new_state, sub_id)
            ad.droid.imsMmTelSetAdvancedCallingEnabled(sub_id, new_state)
        check_state = ad.droid.imsMmTelIsAdvancedCallingEnabled(sub_id)
        if check_state != new_state:
            ad.log.error("Failed to toggle Enhanced 4G LTE Mode to %s, still \
                set to %s on sub_id %s", new_state, check_state, sub_id)
            result = False
        return result
    else:
        # TODO: b/26293960 No framework API available to set IMS by SubId.
        voice_sub_id_changed = False
        current_sub_id = get_incoming_voice_sub_id(ad)
        if current_sub_id != sub_id:
            set_incoming_voice_sub_id(ad, sub_id)
            voice_sub_id_changed = True

        # b/139641554
        ad.terminate_all_sessions()
        bring_up_sl4a(ad)

        if not ad.droid.imsIsEnhanced4gLteModeSettingEnabledByPlatform():
            ad.log.info(
                "Enhanced 4G Lte Mode Setting is not enabled by platform for \
                    sub ID %s.", sub_id)
            return False

        current_state = ad.droid.imsIsEnhanced4gLteModeSettingEnabledByUser()
        ad.log.info("Current state of Enhanced 4G Lte Mode Setting for sub \
            ID %s: %s", sub_id, current_state)
        ad.log.info("New desired state of Enhanced 4G Lte Mode Setting for sub \
            ID %s: %s", sub_id, new_state)

        if new_state is None:
            new_state = not current_state
        if new_state != current_state:
            ad.log.info(
                "Toggle Enhanced 4G LTE Mode from %s to %s for sub ID %s.",
                current_state, new_state, sub_id)
            ad.droid.imsSetEnhanced4gMode(new_state)
            time.sleep(5)

        check_state = ad.droid.imsIsEnhanced4gLteModeSettingEnabledByUser()
        if check_state != new_state:
            ad.log.error("Failed to toggle Enhanced 4G LTE Mode to %s, \
                still set to %s on sub_id %s", new_state, check_state, sub_id)
            result = False

        if voice_sub_id_changed:
            set_incoming_voice_sub_id(ad, current_sub_id)

        return result


def toggle_wfc(log, ad, new_state=None):
    """ Toggle WFC enable/disable

    Args:
        log: Log object
        ad: Android device object.
        new_state: WFC state to set to.
            True for enable, False for disable.
            If None, opposite of the current state.
    """
    return toggle_wfc_for_subscription(
        log, ad, new_state, get_outgoing_voice_sub_id(ad))


def toggle_wfc_for_subscription(log, ad, new_state=None, sub_id=None):
    """ Toggle WFC enable/disable for specified voice subscription.

    Args:
        ad: Android device object.
        sub_id: Optional. If not assigned the default sub ID for voice call will
            be used.
        new_state: WFC state to set to.
            True for enable, False for disable.
            If None, opposite of the current state.
    """
    current_state = None
    result = True

    if sub_id is None:
        sub_id = ad.droid.subscriptionGetDefaultVoiceSubId()

    try:
        current_state = ad.droid.imsMmTelIsVoWiFiSettingEnabled(sub_id)
    except Exception as e:
        ad.log.warning(e)

    if current_state is not None:
        if new_state is None:
            new_state = not current_state
        if new_state != current_state:
            ad.log.info(
                "Toggle Wi-Fi calling from %s to %s on sub_id %s",
                current_state, new_state, sub_id)
            ad.droid.imsMmTelSetVoWiFiSettingEnabled(sub_id, new_state)
        check_state = ad.droid.imsMmTelIsVoWiFiSettingEnabled(sub_id)
        if check_state != new_state:
            ad.log.error("Failed to toggle Wi-Fi calling to %s, \
                still set to %s on sub_id %s", new_state, check_state, sub_id)
            result = False
        return result
    else:
        voice_sub_id_changed = False
        if not sub_id:
            sub_id = get_outgoing_voice_sub_id(ad)
        else:
            current_sub_id = get_incoming_voice_sub_id(ad)
            if current_sub_id != sub_id:
                set_incoming_voice_sub_id(ad, sub_id)
                voice_sub_id_changed = True

        # b/139641554
        ad.terminate_all_sessions()
        bring_up_sl4a(ad)

        if not ad.droid.imsIsWfcEnabledByPlatform():
            ad.log.info("WFC is not enabled by platform for sub ID %s.", sub_id)
            return False

        current_state = ad.droid.imsIsWfcEnabledByUser()
        ad.log.info("Current state of WFC Setting for sub ID %s: %s",
            sub_id, current_state)
        ad.log.info("New desired state of WFC Setting for sub ID %s: %s",
            sub_id, new_state)

        if new_state is None:
            new_state = not current_state
        if new_state != current_state:
            ad.log.info("Toggle WFC user enabled from %s to %s for sub ID %s",
                current_state, new_state, sub_id)
            ad.droid.imsSetWfcSetting(new_state)

        if voice_sub_id_changed:
            set_incoming_voice_sub_id(ad, current_sub_id)

        return True


def is_enhanced_4g_lte_mode_setting_enabled(ad, sub_id, enabled_by="platform"):
    voice_sub_id_changed = False
    current_sub_id = get_incoming_voice_sub_id(ad)
    if current_sub_id != sub_id:
        set_incoming_voice_sub_id(ad, sub_id)
        voice_sub_id_changed = True
    if enabled_by == "platform":
        res = ad.droid.imsIsEnhanced4gLteModeSettingEnabledByPlatform()
    else:
        res = ad.droid.imsIsEnhanced4gLteModeSettingEnabledByUser()
    if not res:
        ad.log.info("Enhanced 4G Lte Mode Setting is NOT enabled by %s for sub \
            ID %s.", enabled_by, sub_id)
        if voice_sub_id_changed:
            set_incoming_voice_sub_id(ad, current_sub_id)
        return False
    if voice_sub_id_changed:
        set_incoming_voice_sub_id(ad, current_sub_id)
    ad.log.info("Enhanced 4G Lte Mode Setting is enabled by %s for sub ID %s.",
        enabled_by, sub_id)
    return True


def set_enhanced_4g_mode(ad, sub_id, state):
    voice_sub_id_changed = False
    current_sub_id = get_incoming_voice_sub_id(ad)
    if current_sub_id != sub_id:
        set_incoming_voice_sub_id(ad, sub_id)
        voice_sub_id_changed = True

    ad.droid.imsSetEnhanced4gMode(state)
    time.sleep(5)

    if voice_sub_id_changed:
        set_incoming_voice_sub_id(ad, current_sub_id)


def wait_for_enhanced_4g_lte_setting(log,
                                     ad,
                                     sub_id,
                                     max_time=MAX_WAIT_TIME_FOR_STATE_CHANGE):
    """Wait for android device to enable enhance 4G LTE setting.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        Return True if device report VoLTE enabled bit true within max_time.
        Return False if timeout.
    """
    return wait_for_state(
        is_enhanced_4g_lte_mode_setting_enabled,
        True,
        max_time,
        WAIT_TIME_BETWEEN_STATE_CHECK,
        ad,
        sub_id,
        enabled_by="platform")


def set_wfc_mode(log, ad, wfc_mode):
    """Set WFC enable/disable and mode.

    Args:
        log: Log object
        ad: Android device object.
        wfc_mode: WFC mode to set to.
            Valid mode includes: WFC_MODE_WIFI_ONLY, WFC_MODE_CELLULAR_PREFERRED,
            WFC_MODE_WIFI_PREFERRED, WFC_MODE_DISABLED.

    Returns:
        True if success. False if ad does not support WFC or error happened.
    """
    return set_wfc_mode_for_subscription(
        ad, wfc_mode, get_outgoing_voice_sub_id(ad))


def set_wfc_mode_for_subscription(ad, wfc_mode, sub_id=None):
    """Set WFC enable/disable and mode subscription based

    Args:
        ad: Android device object.
        wfc_mode: WFC mode to set to.
            Valid mode includes: WFC_MODE_WIFI_ONLY, WFC_MODE_CELLULAR_PREFERRED,
            WFC_MODE_WIFI_PREFERRED.
        sub_id: subscription Id

    Returns:
        True if success. False if ad does not support WFC or error happened.
    """
    if wfc_mode not in [
        WFC_MODE_WIFI_ONLY,
        WFC_MODE_CELLULAR_PREFERRED,
        WFC_MODE_WIFI_PREFERRED,
        WFC_MODE_DISABLED]:

        ad.log.error("Given WFC mode (%s) is not correct.", wfc_mode)
        return False

    current_mode = None
    result = True

    if sub_id is None:
        sub_id = ad.droid.subscriptionGetDefaultVoiceSubId()

    try:
        current_mode = ad.droid.imsMmTelGetVoWiFiModeSetting(sub_id)
        ad.log.info("Current WFC mode of sub ID %s: %s", sub_id, current_mode)
    except Exception as e:
        ad.log.warning(e)

    if current_mode is not None:
        try:
            if not ad.droid.imsMmTelIsVoWiFiSettingEnabled(sub_id):
                if wfc_mode is WFC_MODE_DISABLED:
                    ad.log.info("WFC is already disabled.")
                    return True
                ad.log.info(
                    "WFC is disabled for sub ID %s. Enabling WFC...", sub_id)
                ad.droid.imsMmTelSetVoWiFiSettingEnabled(sub_id, True)

            if wfc_mode is WFC_MODE_DISABLED:
                ad.log.info(
                    "WFC is enabled for sub ID %s. Disabling WFC...", sub_id)
                ad.droid.imsMmTelSetVoWiFiSettingEnabled(sub_id, False)
                return True
            operator_name = get_operator_name(ad.log, ad, sub_id)
            ad.log.info("Set wfc mode to %s for sub ID %s.", wfc_mode, sub_id)
            ad.root_adb()
            if CARRIER_ATT == operator_name:
                ad.adb.shell("setprop dbg.att.force_wfc_nv_enabled true")
            ad.adb.shell("setprop dbg.force_wfc_activated true")
            ad.droid.imsMmTelSetVoWiFiModeSetting(sub_id, wfc_mode)
            mode = ad.droid.imsMmTelGetVoWiFiModeSetting(sub_id)
            if mode != wfc_mode:
                ad.log.error("WFC mode for sub ID %s is %s, not in %s",
                    sub_id, mode, wfc_mode)
                return False
        except Exception as e:
            ad.log.error(e)
            return False
        return True
    else:
        voice_sub_id_changed = False
        if not sub_id:
            sub_id = get_outgoing_voice_sub_id(ad)
        else:
            current_sub_id = get_incoming_voice_sub_id(ad)
            if current_sub_id != sub_id:
                set_incoming_voice_sub_id(ad, sub_id)
                voice_sub_id_changed = True

        # b/139641554
        ad.terminate_all_sessions()
        bring_up_sl4a(ad)

        if wfc_mode != WFC_MODE_DISABLED and wfc_mode not in ad.telephony[
            "subscription"][get_outgoing_voice_sub_id(ad)].get("wfc_modes", []):
            ad.log.error("WFC mode %s is not supported", wfc_mode)
            raise signals.TestSkip("WFC mode %s is not supported" % wfc_mode)
        try:
            ad.log.info("Set wfc mode to %s", wfc_mode)
            if wfc_mode != WFC_MODE_DISABLED:
                start_adb_tcpdump(ad, interface="wlan0", mask="all")
            if not ad.droid.imsIsWfcEnabledByPlatform():
                if wfc_mode == WFC_MODE_DISABLED:
                    if voice_sub_id_changed:
                        set_incoming_voice_sub_id(ad, current_sub_id)
                    return True
                else:
                    ad.log.error("WFC not supported by platform.")
                    if voice_sub_id_changed:
                        set_incoming_voice_sub_id(ad, current_sub_id)
                    return False
            ad.droid.imsSetWfcMode(wfc_mode)
            mode = ad.droid.imsGetWfcMode()
            if voice_sub_id_changed:
                set_incoming_voice_sub_id(ad, current_sub_id)
            if mode != wfc_mode:
                ad.log.error("WFC mode is %s, not in %s", mode, wfc_mode)
                return False
        except Exception as e:
            ad.log.error(e)
            if voice_sub_id_changed:
                set_incoming_voice_sub_id(ad, current_sub_id)
            return False
        return True


def set_ims_provisioning_for_subscription(ad, feature_flag, value, sub_id=None):
    """ Sets Provisioning Values for Subscription Id

    Args:
        ad: Android device object.
        sub_id: Subscription Id
        feature_flag: voice or video
        value: enable or disable
    """
    try:
        if sub_id is None:
            sub_id = ad.droid.subscriptionGetDefaultVoiceSubId()
        ad.log.info("SubId %s - setprovisioning for %s to %s",
                    sub_id, feature_flag, value)
        result = ad.droid.provisioningSetProvisioningIntValue(sub_id,
                    feature_flag, value)
        if result == 0:
            return True
        return False
    except Exception as e:
        ad.log.error(e)
        return False


def get_ims_provisioning_for_subscription(ad, feature_flag, tech, sub_id=None):
    """ Gets Provisioning Values for Subscription Id

    Args:
        ad: Android device object.
        sub_id: Subscription Id
        feature_flag: voice, video, ut, sms
        tech: lte, iwlan
    """
    try:
        if sub_id is None:
            sub_id = ad.droid.subscriptionGetDefaultVoiceSubId()
        result = ad.droid.provisioningGetProvisioningStatusForCapability(
                    sub_id, feature_flag, tech)
        ad.log.info("SubId %s - getprovisioning for %s on %s - %s",
                    sub_id, feature_flag, tech, result)
        return result
    except Exception as e:
        ad.log.error(e)
        return False


def activate_wfc_on_device(log, ad):
    """ Activates WiFi calling on device.

        Required for certain network operators.

    Args:
        log: Log object
        ad: Android device object
    """
    activate_wfc_on_device_for_subscription(log, ad,
                                            ad.droid.subscriptionGetDefaultSubId())


def activate_wfc_on_device_for_subscription(log, ad, sub_id):
    """ Activates WiFi calling on device for a subscription.

    Args:
        log: Log object
        ad: Android device object
        sub_id: Subscription id (integer)
    """
    if not sub_id or INVALID_SUB_ID == sub_id:
        ad.log.error("Subscription id invalid")
        return
    operator_name = get_operator_name(log, ad, sub_id)
    if operator_name in (CARRIER_VZW, CARRIER_ATT, CARRIER_BELL, CARRIER_ROGERS,
                         CARRIER_TELUS, CARRIER_KOODO, CARRIER_VIDEOTRON, CARRIER_FRE):
        ad.log.info("Activating WFC on operator : %s", operator_name)
        if not ad.is_apk_installed("com.google.android.wfcactivation"):
            ad.log.error("WFC Activation Failed, wfc activation apk not installed")
            return
        wfc_activate_cmd ="am start --ei EXTRA_LAUNCH_CARRIER_APP 0 --ei " \
                    "android.telephony.extra.SUBSCRIPTION_INDEX {} -n ".format(sub_id)
        if CARRIER_ATT == operator_name:
            ad.adb.shell("setprop dbg.att.force_wfc_nv_enabled true")
            wfc_activate_cmd = wfc_activate_cmd+\
                               "\"com.google.android.wfcactivation/" \
                               ".WfcActivationActivity\""
        elif CARRIER_VZW == operator_name:
            ad.adb.shell("setprop dbg.vzw.force_wfc_nv_enabled true")
            wfc_activate_cmd = wfc_activate_cmd + \
                               "\"com.google.android.wfcactivation/" \
                               ".VzwEmergencyAddressActivity\""
        else:
            wfc_activate_cmd = wfc_activate_cmd+ \
                               "\"com.google.android.wfcactivation/" \
                               ".can.WfcActivationCanadaActivity\""
        ad.adb.shell(wfc_activate_cmd)


def is_ims_registered(log, ad, sub_id=None):
    """Return True if IMS registered.

    Args:
        log: log object.
        ad: android device.
        sub_id: Optional. If not assigned the default sub ID of voice call will
            be used.

    Returns:
        Return True if IMS registered.
        Return False if IMS not registered.
    """
    if not sub_id:
        return ad.droid.telephonyIsImsRegistered()
    else:
        return change_voice_subid_temporarily(
            ad, sub_id, ad.droid.telephonyIsImsRegistered)


def wait_for_ims_registered(log, ad, max_time=MAX_WAIT_TIME_WFC_ENABLED):
    """Wait for android device to register on ims.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        Return True if device register ims successfully within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, is_ims_registered)


def is_volte_available(log, ad, sub_id=None):
    """Return True if VoLTE is available.

    Args:
        log: log object.
        ad: android device.
        sub_id: Optional. If not assigned the default sub ID of voice call will
            be used.

    Returns:
        Return True if VoLTE is available.
        Return False if VoLTE is not available.
    """
    if not sub_id:
        return ad.droid.telephonyIsVolteAvailable()
    else:
        return change_voice_subid_temporarily(
            ad, sub_id, ad.droid.telephonyIsVolteAvailable)


def is_volte_enabled(log, ad, sub_id=None):
    """Return True if VoLTE feature bit is True.

    Args:
        log: log object.
        ad: android device.
        sub_id: Optional. If not assigned the default sub ID of voice call will
            be used.

    Returns:
        Return True if VoLTE feature bit is True and IMS registered.
        Return False if VoLTE feature bit is False or IMS not registered.
    """
    if not is_ims_registered(log, ad, sub_id):
        ad.log.info("IMS is not registered for sub ID %s.", sub_id)
        return False
    if not is_volte_available(log, ad, sub_id):
        ad.log.info("IMS is registered for sub ID %s, IsVolteCallingAvailable "
            "is False", sub_id)
        return False
    else:
        ad.log.info("IMS is registered for sub ID %s, IsVolteCallingAvailable "
            "is True", sub_id)
        return True


def wait_for_volte_enabled(
    log, ad, max_time=MAX_WAIT_TIME_VOLTE_ENABLED,sub_id=None):
    """Wait for android device to report VoLTE enabled bit true.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        Return True if device report VoLTE enabled bit true within max_time.
        Return False if timeout.
    """
    if not sub_id:
        return _wait_for_droid_in_state(log, ad, max_time, is_volte_enabled)
    else:
        return _wait_for_droid_in_state_for_subscription(
            log, ad, sub_id, max_time, is_volte_enabled)


def toggle_video_calling(log, ad, new_state=None):
    """Toggle enable/disable Video calling for default voice subscription.

    Args:
        ad: Android device object.
        new_state: Video mode state to set to.
            True for enable, False for disable.
            If None, opposite of the current state.

    Raises:
        TelImsUtilsError if platform does not support Video calling.
    """
    if not ad.droid.imsIsVtEnabledByPlatform():
        if new_state is not False:
            raise TelImsUtilsError("VT not supported by platform.")
        # if the user sets VT false and it's unavailable we just let it go
        return False

    current_state = ad.droid.imsIsVtEnabledByUser()
    if new_state is None:
        new_state = not current_state
    if new_state != current_state:
        ad.droid.imsSetVtSetting(new_state)
    return True


def toggle_video_calling_for_subscription(ad, new_state=None, sub_id=None):
    """Toggle enable/disable Video calling for subscription.

    Args:
        ad: Android device object.
        new_state: Video mode state to set to.
            True for enable, False for disable.
            If None, opposite of the current state.
        sub_id: subscription Id
    """
    try:
        if sub_id is None:
            sub_id = ad.droid.subscriptionGetDefaultVoiceSubId()
        current_state = ad.droid.imsMmTelIsVtSettingEnabled(sub_id)
        if new_state is None:
            new_state = not current_state
        if new_state != current_state:
            ad.log.info("SubId %s - Toggle VT from %s to %s", sub_id,
                        current_state, new_state)
            ad.droid.imsMmTelSetVtSettingEnabled(sub_id, new_state)
    except Exception as e:
        ad.log.error(e)
        return False
    return True


def is_video_enabled(log, ad):
    """Return True if Video Calling feature bit is True.

    Args:
        log: log object.
        ad: android device.

    Returns:
        Return True if Video Calling feature bit is True and IMS registered.
        Return False if Video Calling feature bit is False or IMS not registered.
    """
    video_status = ad.droid.telephonyIsVideoCallingAvailable()
    if video_status is True and is_ims_registered(log, ad) is False:
        ad.log.error(
            "Error! Video Call is Available, but IMS is not registered.")
        return False
    return video_status


def wait_for_video_enabled(log, ad, max_time=MAX_WAIT_TIME_VOLTE_ENABLED):
    """Wait for android device to report Video Telephony enabled bit true.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        Return True if device report Video Telephony enabled bit true within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, is_video_enabled)


def is_wfc_enabled(log, ad):
    """Return True if WiFi Calling feature bit is True.

    Args:
        log: log object.
        ad: android device.

    Returns:
        Return True if WiFi Calling feature bit is True and IMS registered.
        Return False if WiFi Calling feature bit is False or IMS not registered.
    """
    if not is_ims_registered(log, ad):
        ad.log.info("IMS is not registered.")
        return False
    if not ad.droid.telephonyIsWifiCallingAvailable():
        ad.log.info("IMS is registered, IsWifiCallingAvailable is False")
        return False
    else:
        ad.log.info("IMS is registered, IsWifiCallingAvailable is True")
        return True


def wait_for_wfc_enabled(log, ad, max_time=MAX_WAIT_TIME_WFC_ENABLED):
    """Wait for android device to report WiFi Calling enabled bit true.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.
            Default value is MAX_WAIT_TIME_WFC_ENABLED.

    Returns:
        Return True if device report WiFi Calling enabled bit true within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, is_wfc_enabled)


def wait_for_wfc_disabled(log, ad, max_time=MAX_WAIT_TIME_WFC_DISABLED):
    """Wait for android device to report WiFi Calling enabled bit false.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.
            Default value is MAX_WAIT_TIME_WFC_DISABLED.

    Returns:
        Return True if device report WiFi Calling enabled bit false within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(
        log, ad, max_time, lambda log, ad: not is_wfc_enabled(log, ad))
