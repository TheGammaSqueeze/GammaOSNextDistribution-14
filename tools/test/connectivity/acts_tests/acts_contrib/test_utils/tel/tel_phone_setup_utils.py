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
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_VOLTE
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_WFC
from acts_contrib.test_utils.tel.tel_defines import CARRIER_FRE
from acts_contrib.test_utils.tel.tel_defines import CARRIER_TMO
from acts_contrib.test_utils.tel.tel_defines import CARRIER_VZW
from acts_contrib.test_utils.tel.tel_defines import GEN_2G
from acts_contrib.test_utils.tel.tel_defines import GEN_3G
from acts_contrib.test_utils.tel.tel_defines import GEN_4G
from acts_contrib.test_utils.tel.tel_defines import GEN_5G
from acts_contrib.test_utils.tel.tel_defines import INVALID_SUB_ID
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_CALL_DROP
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_NW_SELECTION
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_VOLTE_ENABLED
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_WFC_ENABLED
from acts_contrib.test_utils.tel.tel_defines import NETWORK_SERVICE_DATA
from acts_contrib.test_utils.tel.tel_defines import NETWORK_SERVICE_VOICE
from acts_contrib.test_utils.tel.tel_defines import NETWORK_MODE_CDMA
from acts_contrib.test_utils.tel.tel_defines import NETWORK_MODE_GSM_ONLY
from acts_contrib.test_utils.tel.tel_defines import NETWORK_MODE_GSM_UMTS
from acts_contrib.test_utils.tel.tel_defines import NETWORK_MODE_LTE_CDMA_EVDO
from acts_contrib.test_utils.tel.tel_defines import NETWORK_MODE_LTE_GSM_WCDMA
from acts_contrib.test_utils.tel.tel_defines import NETWORK_MODE_LTE_ONLY
from acts_contrib.test_utils.tel.tel_defines import RAT_1XRTT
from acts_contrib.test_utils.tel.tel_defines import RAT_5G
from acts_contrib.test_utils.tel.tel_defines import RAT_FAMILY_CDMA2000
from acts_contrib.test_utils.tel.tel_defines import RAT_FAMILY_LTE
from acts_contrib.test_utils.tel.tel_defines import RAT_FAMILY_GSM
from acts_contrib.test_utils.tel.tel_defines import RAT_FAMILY_WCDMA
from acts_contrib.test_utils.tel.tel_defines import RAT_FAMILY_WLAN
from acts_contrib.test_utils.tel.tel_defines import RAT_UNKNOWN
from acts_contrib.test_utils.tel.tel_defines import TELEPHONY_STATE_IDLE
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_1XRTT_VOICE_ATTACH
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_ANDROID_STATE_SETTLING
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_DISABLED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_5g_utils import is_current_network_5g
from acts_contrib.test_utils.tel.tel_ims_utils import toggle_volte
from acts_contrib.test_utils.tel.tel_ims_utils import toggle_volte_for_subscription
from acts_contrib.test_utils.tel.tel_ims_utils import set_wfc_mode
from acts_contrib.test_utils.tel.tel_ims_utils import set_wfc_mode_for_subscription
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_enhanced_4g_lte_setting
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_volte_enabled
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_wfc_enabled
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_wfc_disabled
from acts_contrib.test_utils.tel.tel_lookup_tables import network_preference_for_generation
from acts_contrib.test_utils.tel.tel_lookup_tables import rat_families_for_network_preference
from acts_contrib.test_utils.tel.tel_lookup_tables import rat_family_for_generation
from acts_contrib.test_utils.tel.tel_lookup_tables import rat_family_from_rat
from acts_contrib.test_utils.tel.tel_lookup_tables import rat_generation_from_rat
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_message_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_from_slot_index
from acts_contrib.test_utils.tel.tel_subscription_utils import get_default_data_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import set_default_sub_for_all_services
from acts_contrib.test_utils.tel.tel_test_utils import _is_attached
from acts_contrib.test_utils.tel.tel_test_utils import _is_attached_for_subscription
from acts_contrib.test_utils.tel.tel_test_utils import _wait_for_droid_in_state
from acts_contrib.test_utils.tel.tel_test_utils import _wait_for_droid_in_state_for_subscription
from acts_contrib.test_utils.tel.tel_test_utils import get_capability_for_subscription
from acts_contrib.test_utils.tel.tel_test_utils import get_cell_data_roaming_state_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import get_network_rat_for_subscription
from acts_contrib.test_utils.tel.tel_test_utils import get_operator_name
from acts_contrib.test_utils.tel.tel_test_utils import get_telephony_signal_strength
from acts_contrib.test_utils.tel.tel_test_utils import is_droid_in_network_generation_for_subscription
from acts_contrib.test_utils.tel.tel_test_utils import is_droid_in_rat_family_for_subscription
from acts_contrib.test_utils.tel.tel_test_utils import is_droid_in_rat_family_list_for_subscription
from acts_contrib.test_utils.tel.tel_test_utils import reset_preferred_network_type_to_allowable_range
from acts_contrib.test_utils.tel.tel_test_utils import set_cell_data_roaming_state_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import set_preferred_network_mode_pref
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import wait_for_data_attach_for_subscription
from acts_contrib.test_utils.tel.tel_wifi_utils import ensure_wifi_connected
from acts_contrib.test_utils.tel.tel_wifi_utils import set_wifi_to_default
from acts.libs.utils.multithread import multithread_func


def phone_setup_iwlan(log,
                      ad,
                      is_airplane_mode,
                      wfc_mode,
                      wifi_ssid=None,
                      wifi_pwd=None,
                      nw_gen=None,
                      nr_type=None):
    """Phone setup function for epdg call test.
    Set WFC mode according to wfc_mode.
    Set airplane mode according to is_airplane_mode.
    Make sure phone connect to WiFi. (If wifi_ssid is not None.)
    Wait for phone to be in iwlan data network type.
    Wait for phone to report wfc enabled flag to be true.
    Args:
        log: Log object.
        ad: Android device object.
        is_airplane_mode: True to turn on airplane mode. False to turn off airplane mode.
        wfc_mode: WFC mode to set to.
        wifi_ssid: WiFi network SSID. This is optional.
            If wifi_ssid is None, then phone_setup_iwlan will not attempt to connect to wifi.
        wifi_pwd: WiFi network password. This is optional.
        nw_gen: network type selection. This is optional.
            GEN_4G for 4G, GEN_5G for 5G or None for doing nothing.
        nr_type: NR network type
    Returns:
        True if success. False if fail.
    """
    return phone_setup_iwlan_for_subscription(log, ad,
                                              get_outgoing_voice_sub_id(ad),
                                              is_airplane_mode, wfc_mode,
                                              wifi_ssid, wifi_pwd, nw_gen, nr_type)


def phone_setup_iwlan_for_subscription(log,
                                       ad,
                                       sub_id,
                                       is_airplane_mode,
                                       wfc_mode,
                                       wifi_ssid=None,
                                       wifi_pwd=None,
                                       nw_gen=None,
                                       nr_type=None):
    """Phone setup function for epdg call test for subscription id.
    Enable VoLTE. (b/235019060#comment20)
    Set WFC mode according to wfc_mode.
    Set airplane mode according to is_airplane_mode.
    Make sure phone connect to WiFi. (If wifi_ssid is not None.)
    Wait for phone to be in iwlan data network type.
    Wait for phone to report wfc enabled flag to be true.
    Args:
        log: Log object.
        ad: Android device object.
        sub_id: subscription id.
        is_airplane_mode: True to turn on airplane mode. False to turn off airplane mode.
        wfc_mode: WFC mode to set to.
        wifi_ssid: WiFi network SSID. This is optional.
            If wifi_ssid is None, then phone_setup_iwlan will not attempt to connect to wifi.
        wifi_pwd: WiFi network password. This is optional.
        nw_gen: network type selection. This is optional.
            GEN_4G for 4G, GEN_5G for 5G or None for doing nothing.
        nr_type: NR network type
    Returns:
        True if success. False if fail.
    """
    if not get_capability_for_subscription(ad, CAPABILITY_WFC, sub_id):
        ad.log.error("WFC is not supported, abort test.")
        raise signals.TestSkip("WFC is not supported, abort test.")

    if nw_gen:
        if not ensure_network_generation_for_subscription(
                log, ad, sub_id, nw_gen, voice_or_data=NETWORK_SERVICE_DATA,
                nr_type=nr_type):
            ad.log.error("Failed to set to %s data.", nw_gen)
            return False
    toggle_volte_for_subscription(log, ad, sub_id, True)
    toggle_airplane_mode(log, ad, is_airplane_mode, strict_checking=False)

    # Pause at least for 4 seconds is necessary after airplane mode was turned
    # on due to the mechanism of deferring Wi-Fi (b/191481736)
    if is_airplane_mode:
        time.sleep(5)

    # check if WFC supported phones
    if wfc_mode != WFC_MODE_DISABLED and not ad.droid.imsIsWfcEnabledByPlatform(
    ):
        ad.log.error("WFC is not enabled on this device by checking "
                     "ImsManager.isWfcEnabledByPlatform")
        return False
    if wifi_ssid is not None:
        if not ensure_wifi_connected(log, ad, wifi_ssid, wifi_pwd, apm=is_airplane_mode):
            ad.log.error("Fail to bring up WiFi connection on %s.", wifi_ssid)
            return False
    else:
        ad.log.info("WiFi network SSID not specified, available user "
                    "parameters are: wifi_network_ssid, wifi_network_ssid_2g, "
                    "wifi_network_ssid_5g")
    if not set_wfc_mode_for_subscription(ad, wfc_mode, sub_id):
        ad.log.error("Unable to set WFC mode to %s.", wfc_mode)
        return False

    if wfc_mode != WFC_MODE_DISABLED:
        if not wait_for_wfc_enabled(log, ad, max_time=MAX_WAIT_TIME_WFC_ENABLED):
            ad.log.error("WFC is not enabled")
            return False

    return True


def phone_setup_iwlan_cellular_preferred(log,
                                         ad,
                                         wifi_ssid=None,
                                         wifi_pwd=None):
    """Phone setup function for iwlan Non-APM CELLULAR_PREFERRED test.
    Set WFC mode according to CELLULAR_PREFERRED.
    Set airplane mode according to False.
    Make sure phone connect to WiFi. (If wifi_ssid is not None.)
    Make sure phone don't report iwlan data network type.
    Make sure phone don't report wfc enabled flag to be true.

    Args:
        log: Log object.
        ad: Android device object.
        wifi_ssid: WiFi network SSID. This is optional.
            If wifi_ssid is None, then phone_setup_iwlan will not attempt to connect to wifi.
        wifi_pwd: WiFi network password. This is optional.

    Returns:
        True if success. False if fail.
    """
    toggle_airplane_mode(log, ad, False, strict_checking=False)
    try:
        toggle_volte(log, ad, True)
        if not wait_for_network_generation(
                log, ad, GEN_4G, voice_or_data=NETWORK_SERVICE_DATA):
            if not ensure_network_generation(
                    log, ad, GEN_4G, voice_or_data=NETWORK_SERVICE_DATA):
                ad.log.error("Fail to ensure data in 4G")
                return False
    except Exception as e:
        ad.log.error(e)
        ad.droid.telephonyToggleDataConnection(True)
    if wifi_ssid is not None:
        if not ensure_wifi_connected(log, ad, wifi_ssid, wifi_pwd):
            ad.log.error("Connect to WiFi failed.")
            return False
    if not set_wfc_mode(log, ad, WFC_MODE_CELLULAR_PREFERRED):
        ad.log.error("Set WFC mode failed.")
        return False
    if not wait_for_not_network_rat(
            log, ad, RAT_FAMILY_WLAN, voice_or_data=NETWORK_SERVICE_DATA):
        ad.log.error("Data rat in iwlan mode.")
        return False
    elif not wait_for_wfc_disabled(log, ad, MAX_WAIT_TIME_WFC_ENABLED):
        ad.log.error("Should report wifi calling disabled within %s.",
                     MAX_WAIT_TIME_WFC_ENABLED)
        return False
    return True


def phone_setup_data_for_subscription(log, ad, sub_id, network_generation,
                                        nr_type=None):
    """Setup Phone <sub_id> Data to <network_generation>

    Args:
        log: log object
        ad: android device object
        sub_id: subscription id
        network_generation: network generation, e.g. GEN_2G, GEN_3G, GEN_4G, GEN_5G
        nr_type: NR network type e.g. NSA, SA, MMWAVE

    Returns:
        True if success, False if fail.
    """
    toggle_airplane_mode(log, ad, False, strict_checking=False)
    set_wifi_to_default(log, ad)
    if not set_wfc_mode(log, ad, WFC_MODE_DISABLED):
        ad.log.error("Disable WFC failed.")
        return False
    if not ensure_network_generation_for_subscription(
            log,
            ad,
            sub_id,
            network_generation,
            voice_or_data=NETWORK_SERVICE_DATA,
            nr_type=nr_type):
        get_telephony_signal_strength(ad)
        return False
    return True


def phone_setup_5g(log, ad, nr_type=None):
    """Setup Phone default data sub_id data to 5G.

    Args:
        log: log object
        ad: android device object

    Returns:
        True if success, False if fail.
    """
    return phone_setup_5g_for_subscription(log, ad,
                                           get_default_data_sub_id(ad), nr_type=nr_type)


def phone_setup_5g_for_subscription(log, ad, sub_id, nr_type=None):
    """Setup Phone <sub_id> Data to 5G.

    Args:
        log: log object
        ad: android device object
        sub_id: subscription id
        nr_type: NR network type e.g. NSA, SA, MMWAVE

    Returns:
        True if success, False if fail.
    """
    return phone_setup_data_for_subscription(log, ad, sub_id, GEN_5G,
                                        nr_type=nr_type)


def phone_setup_4g(log, ad):
    """Setup Phone default data sub_id data to 4G.

    Args:
        log: log object
        ad: android device object

    Returns:
        True if success, False if fail.
    """
    return phone_setup_4g_for_subscription(log, ad,
                                           get_default_data_sub_id(ad))


def phone_setup_4g_for_subscription(log, ad, sub_id):
    """Setup Phone <sub_id> Data to 4G.

    Args:
        log: log object
        ad: android device object
        sub_id: subscription id

    Returns:
        True if success, False if fail.
    """
    return phone_setup_data_for_subscription(log, ad, sub_id, GEN_4G)


def phone_setup_3g(log, ad):
    """Setup Phone default data sub_id data to 3G.

    Args:
        log: log object
        ad: android device object

    Returns:
        True if success, False if fail.
    """
    return phone_setup_3g_for_subscription(log, ad,
                                           get_default_data_sub_id(ad))


def phone_setup_3g_for_subscription(log, ad, sub_id):
    """Setup Phone <sub_id> Data to 3G.

    Args:
        log: log object
        ad: android device object
        sub_id: subscription id

    Returns:
        True if success, False if fail.
    """
    return phone_setup_data_for_subscription(log, ad, sub_id, GEN_3G)


def phone_setup_2g(log, ad):
    """Setup Phone default data sub_id data to 2G.

    Args:
        log: log object
        ad: android device object

    Returns:
        True if success, False if fail.
    """
    return phone_setup_2g_for_subscription(log, ad,
                                           get_default_data_sub_id(ad))


def phone_setup_2g_for_subscription(log, ad, sub_id):
    """Setup Phone <sub_id> Data to 3G.

    Args:
        log: log object
        ad: android device object
        sub_id: subscription id

    Returns:
        True if success, False if fail.
    """
    return phone_setup_data_for_subscription(log, ad, sub_id, GEN_2G)


def phone_setup_csfb(log, ad, nw_gen=GEN_4G, nr_type=None):
    """Setup phone for CSFB call test.

    Setup Phone to be in 4G mode.
    Disabled VoLTE.

    Args:
        log: log object
        ad: Android device object.
        nw_gen: GEN_4G or GEN_5G

    Returns:
        True if setup successfully.
        False for errors.
    """
    return phone_setup_csfb_for_subscription(log, ad,
                                        get_outgoing_voice_sub_id(ad), nw_gen, nr_type=nr_type)


def phone_setup_csfb_for_subscription(log, ad, sub_id, nw_gen=GEN_4G, nr_type=None):
    """Setup phone for CSFB call test for subscription id.

    Setup Phone to be in 4G mode.
    Disabled VoLTE.

    Args:
        log: log object
        ad: Android device object.
        sub_id: subscription id.
        nw_gen: GEN_4G or GEN_5G
        nr_type: NR network type e.g. NSA, SA, MMWAVE

    Returns:
        True if setup successfully.
        False for errors.
    """
    capabilities = ad.telephony["subscription"][sub_id].get("capabilities", [])
    if capabilities:
        if "hide_enhanced_4g_lte" in capabilities:
            show_enhanced_4g_lte_mode = getattr(ad, "show_enhanced_4g_lte_mode", False)
            if show_enhanced_4g_lte_mode in ["false", "False", False]:
                ad.log.warning("'VoLTE' option is hidden. Test will be skipped.")
                raise signals.TestSkip("'VoLTE' option is hidden. Test will be skipped.")

    if nw_gen == GEN_4G:
        if not phone_setup_4g_for_subscription(log, ad, sub_id):
            ad.log.error("Failed to set to 4G data.")
            return False
    elif nw_gen == GEN_5G:
        if not phone_setup_5g_for_subscription(log, ad, sub_id, nr_type=nr_type):
            ad.log.error("Failed to set to 5G data.")
            return False

    if ad.telephony["subscription"][sub_id]["operator"] != CARRIER_VZW \
            and ad.telephony["subscription"][sub_id]["operator"] != CARRIER_TMO :
        if not toggle_volte_for_subscription(log, ad, sub_id, False):
            return False

    if not wait_for_voice_attach_for_subscription(log, ad, sub_id,
                                                  MAX_WAIT_TIME_NW_SELECTION):
        return False

    return phone_idle_csfb_for_subscription(log, ad, sub_id, nw_gen)


def phone_setup_volte(log, ad, nw_gen=GEN_4G, nr_type=None):
    """Setup VoLTE enable.

    Args:
        log: log object
        ad: android device object.
        nw_gen: GEN_4G or GEN_5G

    Returns:
        True: if VoLTE is enabled successfully.
        False: for errors
    """
    if not get_capability_for_subscription(ad, CAPABILITY_VOLTE,
        get_outgoing_voice_sub_id(ad)):
        ad.log.error("VoLTE is not supported, abort test.")
        raise signals.TestSkip("VoLTE is not supported, abort test.")
    return phone_setup_volte_for_subscription(log, ad,
                        get_outgoing_voice_sub_id(ad), nw_gen, nr_type= nr_type)


def phone_setup_volte_for_subscription(log, ad, sub_id, nw_gen=GEN_4G,
                                        nr_type=None):
    """Setup VoLTE enable for subscription id.
    Args:
        log: log object
        ad: android device object.
        sub_id: subscription id.
        nw_gen: GEN_4G or GEN_5G.
        nr_type: NR network type.

    Returns:
        True: if VoLTE is enabled successfully.
        False: for errors
    """
    if not get_capability_for_subscription(ad, CAPABILITY_VOLTE,
        get_outgoing_voice_sub_id(ad)):
        ad.log.error("VoLTE is not supported, abort test.")
        raise signals.TestSkip("VoLTE is not supported, abort test.")

    if nw_gen == GEN_4G:
        if not phone_setup_4g_for_subscription(log, ad, sub_id):
            ad.log.error("Failed to set to 4G data.")
            return False
    elif nw_gen == GEN_5G:
        if not phone_setup_5g_for_subscription(log, ad, sub_id,
                                        nr_type=nr_type):
            ad.log.error("Failed to set to 5G data.")
            return False
    operator_name = get_operator_name(log, ad, sub_id)
    if not wait_for_enhanced_4g_lte_setting(log, ad, sub_id):
        ad.log.error("Enhanced 4G LTE setting is not available")
        return False
    toggle_volte_for_subscription(log, ad, sub_id, True)
    return phone_idle_volte_for_subscription(log, ad, sub_id, nw_gen,
                                        nr_type=nr_type)


def phone_setup_voice_3g(log, ad):
    """Setup phone voice to 3G.

    Args:
        log: log object
        ad: Android device object.

    Returns:
        True if setup successfully.
        False for errors.
    """
    return phone_setup_voice_3g_for_subscription(log, ad,
                                                 get_outgoing_voice_sub_id(ad))


def phone_setup_voice_3g_for_subscription(log, ad, sub_id):
    """Setup phone voice to 3G for subscription id.

    Args:
        log: log object
        ad: Android device object.
        sub_id: subscription id.

    Returns:
        True if setup successfully.
        False for errors.
    """
    if not phone_setup_3g_for_subscription(log, ad, sub_id):
        ad.log.error("Failed to set to 3G data.")
        return False
    if not wait_for_voice_attach_for_subscription(log, ad, sub_id,
                                                  MAX_WAIT_TIME_NW_SELECTION):
        return False
    return phone_idle_3g_for_subscription(log, ad, sub_id)


def phone_setup_voice_2g(log, ad):
    """Setup phone voice to 2G.

    Args:
        log: log object
        ad: Android device object.

    Returns:
        True if setup successfully.
        False for errors.
    """
    return phone_setup_voice_2g_for_subscription(log, ad,
                                                 get_outgoing_voice_sub_id(ad))


def phone_setup_voice_2g_for_subscription(log, ad, sub_id):
    """Setup phone voice to 2G for subscription id.

    Args:
        log: log object
        ad: Android device object.
        sub_id: subscription id.

    Returns:
        True if setup successfully.
        False for errors.
    """
    if not phone_setup_2g_for_subscription(log, ad, sub_id):
        ad.log.error("Failed to set to 2G data.")
        return False
    if not wait_for_voice_attach_for_subscription(log, ad, sub_id,
                                                  MAX_WAIT_TIME_NW_SELECTION):
        return False
    return phone_idle_2g_for_subscription(log, ad, sub_id)


def phone_setup_voice_general(log, ad):
    """Setup phone for voice general call test.

    Make sure phone attached to voice.
    Make necessary delay.

    Args:
        ad: Android device object.

    Returns:
        True if setup successfully.
        False for errors.
    """
    return phone_setup_voice_general_for_subscription(
        log, ad, get_outgoing_voice_sub_id(ad))


def phone_setup_voice_general_for_slot(log,ad,slot_id):
    return phone_setup_voice_general_for_subscription(
        log, ad, get_subid_from_slot_index(log,ad,slot_id))


def phone_setup_voice_general_for_subscription(log, ad, sub_id):
    """Setup phone for voice general call test for subscription id.

    Make sure phone attached to voice.
    Make necessary delay.

    Args:
        ad: Android device object.
        sub_id: subscription id.

    Returns:
        True if setup successfully.
        False for errors.
    """
    toggle_airplane_mode(log, ad, False, strict_checking=False)
    if not wait_for_voice_attach_for_subscription(log, ad, sub_id,
                                                  MAX_WAIT_TIME_NW_SELECTION):
        # if phone can not attach voice, try phone_setup_voice_3g
        return phone_setup_voice_3g_for_subscription(log, ad, sub_id)
    return True


def phone_setup_data_general(log, ad):
    """Setup phone for data general test.

    Make sure phone attached to data.
    Make necessary delay.

    Args:
        ad: Android device object.

    Returns:
        True if setup successfully.
        False for errors.
    """
    return phone_setup_data_general_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultDataSubId())


def phone_setup_data_general_for_subscription(log, ad, sub_id):
    """Setup phone for data general test for subscription id.

    Make sure phone attached to data.
    Make necessary delay.

    Args:
        ad: Android device object.
        sub_id: subscription id.

    Returns:
        True if setup successfully.
        False for errors.
    """
    toggle_airplane_mode(log, ad, False, strict_checking=False)
    if not wait_for_data_attach_for_subscription(log, ad, sub_id,
                                                 MAX_WAIT_TIME_NW_SELECTION):
        # if phone can not attach data, try reset network preference settings
        reset_preferred_network_type_to_allowable_range(log, ad)

    return wait_for_data_attach_for_subscription(log, ad, sub_id,
                                                 MAX_WAIT_TIME_NW_SELECTION)


def phone_setup_rat_for_subscription(log, ad, sub_id, network_preference,
                                     rat_family):
    toggle_airplane_mode(log, ad, False, strict_checking=False)
    set_wifi_to_default(log, ad)
    if not set_wfc_mode(log, ad, WFC_MODE_DISABLED):
        ad.log.error("Disable WFC failed.")
        return False
    return ensure_network_rat_for_subscription(log, ad, sub_id,
                                               network_preference, rat_family)


def phone_setup_lte_gsm_wcdma(log, ad):
    return phone_setup_lte_gsm_wcdma_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId())


def phone_setup_lte_gsm_wcdma_for_subscription(log, ad, sub_id):
    return phone_setup_rat_for_subscription(
        log, ad, sub_id, NETWORK_MODE_LTE_GSM_WCDMA, RAT_FAMILY_LTE)


def phone_setup_gsm_umts(log, ad):
    return phone_setup_gsm_umts_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId())


def phone_setup_gsm_umts_for_subscription(log, ad, sub_id):
    return phone_setup_rat_for_subscription(
        log, ad, sub_id, NETWORK_MODE_GSM_UMTS, RAT_FAMILY_WCDMA)


def phone_setup_gsm_only(log, ad):
    return phone_setup_gsm_only_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId())


def phone_setup_gsm_only_for_subscription(log, ad, sub_id):
    return phone_setup_rat_for_subscription(
        log, ad, sub_id, NETWORK_MODE_GSM_ONLY, RAT_FAMILY_GSM)


def phone_setup_lte_cdma_evdo(log, ad):
    return phone_setup_lte_cdma_evdo_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId())


def phone_setup_lte_cdma_evdo_for_subscription(log, ad, sub_id):
    return phone_setup_rat_for_subscription(
        log, ad, sub_id, NETWORK_MODE_LTE_CDMA_EVDO, RAT_FAMILY_LTE)


def phone_setup_cdma(log, ad):
    return phone_setup_cdma_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId())


def phone_setup_cdma_for_subscription(log, ad, sub_id):
    return phone_setup_rat_for_subscription(log, ad, sub_id, NETWORK_MODE_CDMA,
                                            RAT_FAMILY_CDMA2000)


def phone_idle_volte(log, ad):
    """Return if phone is idle for VoLTE call test.

    Args:
        ad: Android device object.
    """
    return phone_idle_volte_for_subscription(log, ad,
                                             get_outgoing_voice_sub_id(ad))


def phone_idle_volte_for_subscription(log, ad, sub_id, nw_gen=GEN_4G,
                                    nr_type=None):
    """Return if phone is idle for VoLTE call test for subscription id.
    Args:
        ad: Android device object.
        sub_id: subscription id.
        nw_gen: GEN_4G or GEN_5G.
        nr_type: NR network type e.g. NSA, SA, MMWAVE
    """
    if nw_gen == GEN_5G:
        if not is_current_network_5g(ad, sub_id=sub_id, nr_type=nr_type):
            ad.log.error("Not in 5G coverage.")
            return False
    else:
        if not wait_for_network_rat_for_subscription(
                log, ad, sub_id, RAT_FAMILY_LTE,
                voice_or_data=NETWORK_SERVICE_VOICE):
            ad.log.error("Voice rat not in LTE mode.")
            return False
    if not wait_for_volte_enabled(log, ad, MAX_WAIT_TIME_VOLTE_ENABLED, sub_id):
        ad.log.error(
            "Failed to <report volte enabled true> within %s seconds.",
            MAX_WAIT_TIME_VOLTE_ENABLED)
        return False
    return True


def phone_idle_iwlan(log, ad):
    """Return if phone is idle for WiFi calling call test.

    Args:
        ad: Android device object.
    """
    return phone_idle_iwlan_for_subscription(log, ad,
                                             get_outgoing_voice_sub_id(ad))


def phone_idle_iwlan_for_subscription(log, ad, sub_id):
    """Return if phone is idle for WiFi calling call test for subscription id.

    Args:
        ad: Android device object.
        sub_id: subscription id.
    """
    if not wait_for_wfc_enabled(log, ad, MAX_WAIT_TIME_WFC_ENABLED):
        ad.log.error("Failed to <report wfc enabled true> within %s seconds.",
                     MAX_WAIT_TIME_WFC_ENABLED)
        return False
    return True


def phone_idle_not_iwlan(log, ad):
    """Return if phone is idle for non WiFi calling call test.

    Args:
        ad: Android device object.
    """
    return phone_idle_not_iwlan_for_subscription(log, ad,
                                                 get_outgoing_voice_sub_id(ad))


def phone_idle_not_iwlan_for_subscription(log, ad, sub_id):
    """Return if phone is idle for non WiFi calling call test for sub id.

    Args:
        ad: Android device object.
        sub_id: subscription id.
    """
    if not wait_for_not_network_rat_for_subscription(
            log, ad, sub_id, RAT_FAMILY_WLAN,
            voice_or_data=NETWORK_SERVICE_DATA):
        log.error("{} data rat in iwlan mode.".format(ad.serial))
        return False
    return True


def phone_idle_csfb(log, ad):
    """Return if phone is idle for CSFB call test.

    Args:
        ad: Android device object.
    """
    return phone_idle_csfb_for_subscription(log, ad,
                                            get_outgoing_voice_sub_id(ad))


def phone_idle_csfb_for_subscription(log, ad, sub_id, nw_gen=GEN_4G, nr_type=None):
    """Return if phone is idle for CSFB call test for subscription id.

    Args:
        ad: Android device object.
        sub_id: subscription id.
        nw_gen: GEN_4G or GEN_5G
    """
    if nw_gen == GEN_5G:
        if not is_current_network_5g(ad, sub_id=sub_id, nr_type=nr_type):
            ad.log.error("Not in 5G coverage.")
            return False
    else:
        if not wait_for_network_rat_for_subscription(
                log, ad, sub_id, RAT_FAMILY_LTE,
                voice_or_data=NETWORK_SERVICE_DATA):
            ad.log.error("Data rat not in lte mode.")
            return False
    return True


def phone_idle_3g(log, ad):
    """Return if phone is idle for 3G call test.

    Args:
        ad: Android device object.
    """
    return phone_idle_3g_for_subscription(log, ad,
                                          get_outgoing_voice_sub_id(ad))


def phone_idle_3g_for_subscription(log, ad, sub_id):
    """Return if phone is idle for 3G call test for subscription id.

    Args:
        ad: Android device object.
        sub_id: subscription id.
    """
    return wait_for_network_generation_for_subscription(
        log, ad, sub_id, GEN_3G, voice_or_data=NETWORK_SERVICE_VOICE)


def phone_idle_2g(log, ad):
    """Return if phone is idle for 2G call test.

    Args:
        ad: Android device object.
    """
    return phone_idle_2g_for_subscription(log, ad,
                                          get_outgoing_voice_sub_id(ad))


def phone_idle_2g_for_subscription(log, ad, sub_id):
    """Return if phone is idle for 2G call test for subscription id.

    Args:
        ad: Android device object.
        sub_id: subscription id.
    """
    return wait_for_network_generation_for_subscription(
        log, ad, sub_id, GEN_2G, voice_or_data=NETWORK_SERVICE_VOICE)


def phone_setup_on_rat(
    log,
    ad,
    rat='volte',
    sub_id=None,
    is_airplane_mode=False,
    wfc_mode=None,
    wifi_ssid=None,
    wifi_pwd=None,
    only_return_fn=None,
    sub_id_type='voice',
    nr_type='nsa'):

    if sub_id is None:
        if sub_id_type == 'sms':
            sub_id = get_outgoing_message_sub_id(ad)
        else:
            sub_id = get_outgoing_voice_sub_id(ad)

    if get_default_data_sub_id(ad) != sub_id and '5g' in rat.lower():
        ad.log.warning('Default data sub ID is NOT given sub ID %s.', sub_id)
        network_preference = network_preference_for_generation(
            GEN_5G,
            ad.telephony["subscription"][sub_id]["operator"],
            ad.telephony["subscription"][sub_id]["phone_type"])

        ad.log.info("Network preference for %s is %s", GEN_5G,
                    network_preference)

        if not set_preferred_network_mode_pref(log, ad, sub_id,
            network_preference):
            return False

        if not wait_for_network_generation_for_subscription(
            log,
            ad,
            sub_id,
            GEN_5G,
            max_wait_time=30,
            voice_or_data=NETWORK_SERVICE_DATA,
            nr_type=nr_type):

            ad.log.warning('Non-DDS slot (sub ID: %s) cannot attach 5G network.', sub_id)
            ad.log.info('Check if sub ID %s can attach LTE network.', sub_id)

            if not wait_for_network_generation_for_subscription(
                log,
                ad,
                sub_id,
                GEN_4G,
                voice_or_data=NETWORK_SERVICE_DATA):
                return False

            if "volte" in rat.lower():
                phone_setup_volte_for_subscription(log, ad, sub_id, None)
            elif "wfc" in rat.lower():
                return phone_setup_iwlan_for_subscription(
                    log,
                    ad,
                    sub_id,
                    is_airplane_mode,
                    wfc_mode,
                    wifi_ssid,
                    wifi_pwd)
            elif "csfb" in rat.lower():
                return phone_setup_csfb_for_subscription(log, ad, sub_id, None)
            return True

    if rat.lower() == '5g_volte':
        if only_return_fn:
            return phone_setup_volte_for_subscription
        else:
            return phone_setup_volte_for_subscription(log, ad, sub_id, GEN_5G, nr_type='nsa')

    elif rat.lower() == '5g_nsa_mmw_volte':
        if only_return_fn:
            return phone_setup_volte_for_subscription
        else:
            return phone_setup_volte_for_subscription(log, ad, sub_id, GEN_5G,
                                                    nr_type='mmwave')

    elif rat.lower() == '5g_csfb':
        if only_return_fn:
            return phone_setup_csfb_for_subscription
        else:
            return phone_setup_csfb_for_subscription(log, ad, sub_id, GEN_5G, nr_type='nsa')

    elif rat.lower() == '5g_wfc':
        if only_return_fn:
            return phone_setup_iwlan_for_subscription
        else:
            return phone_setup_iwlan_for_subscription(
                log,
                ad,
                sub_id,
                is_airplane_mode,
                wfc_mode,
                wifi_ssid,
                wifi_pwd,
                GEN_5G,
                nr_type='nsa')

    elif rat.lower() == '5g_nsa_mmw_wfc':
        if only_return_fn:
            return phone_setup_iwlan_for_subscription
        else:
            return phone_setup_iwlan_for_subscription(
                log,
                ad,
                sub_id,
                is_airplane_mode,
                wfc_mode,
                wifi_ssid,
                wifi_pwd,
                GEN_5G,
                nr_type='mmwave')

    elif rat.lower() == 'volte':
        if only_return_fn:
            return phone_setup_volte_for_subscription
        else:
            return phone_setup_volte_for_subscription(log, ad, sub_id)

    elif rat.lower() == 'csfb':
        if only_return_fn:
            return phone_setup_csfb_for_subscription
        else:
            return phone_setup_csfb_for_subscription(log, ad, sub_id)

    elif rat.lower() == '5g':
        if only_return_fn:
            return phone_setup_5g_for_subscription
        else:
            return phone_setup_5g_for_subscription(log, ad, sub_id, nr_type='nsa')

    elif rat.lower() == '5g_nsa_mmwave':
        if only_return_fn:
            return phone_setup_5g_for_subscription
        else:
            return phone_setup_5g_for_subscription(log, ad, sub_id,
                                            nr_type='mmwave')

    elif rat.lower() == '3g':
        if only_return_fn:
            return phone_setup_voice_3g_for_subscription
        else:
            return phone_setup_voice_3g_for_subscription(log, ad, sub_id)

    elif rat.lower() == '2g':
        if only_return_fn:
            return phone_setup_voice_2g_for_subscription
        else:
            return phone_setup_voice_2g_for_subscription(log, ad, sub_id)

    elif rat.lower() == 'wfc':
        if only_return_fn:
            return phone_setup_iwlan_for_subscription
        else:
            return phone_setup_iwlan_for_subscription(
                log,
                ad,
                sub_id,
                is_airplane_mode,
                wfc_mode,
                wifi_ssid,
                wifi_pwd)
    elif rat.lower() == 'default':
        if only_return_fn:
            return ensure_phone_default_state
        else:
            return ensure_phone_default_state(log, ad)
    else:
        if only_return_fn:
            return phone_setup_voice_general_for_subscription
        else:
            return phone_setup_voice_general_for_subscription(log, ad, sub_id)


def wait_for_network_idle(
    log,
    ad,
    rat,
    sub_id,
    nr_type='nsa'):
    """Wait for attaching to network with assigned RAT and IMS/WFC registration

    This function can be used right after network service recovery after turning
    off airplane mode or switching DDS. It will ensure DUT has attached to the
    network with assigned RAT, and VoLTE/WFC has been ready.

    Args:
        log: log object
        ad: Android object
        rat: following RAT are supported:
            - 5g
            - 5g_volte
            - 5g_csfb
            - 5g_wfc
            - 4g (LTE)
            - volte (LTE)
            - csfb (LTE)
            - wfc (LTE)

    Returns:
        True or False
    """
    if get_default_data_sub_id(ad) != sub_id and '5g' in rat.lower():
        ad.log.warning('Default data sub ID is NOT given sub ID %s.', sub_id)
        network_preference = network_preference_for_generation(
            GEN_5G,
            ad.telephony["subscription"][sub_id]["operator"],
            ad.telephony["subscription"][sub_id]["phone_type"])

        ad.log.info("Network preference for %s is %s", GEN_5G,
                    network_preference)

        if not set_preferred_network_mode_pref(log, ad, sub_id,
            network_preference):
            return False

        if not wait_for_network_generation_for_subscription(
            log,
            ad,
            sub_id,
            GEN_5G,
            max_wait_time=30,
            voice_or_data=NETWORK_SERVICE_DATA,
            nr_type=nr_type):

            ad.log.warning('Non-DDS slot (sub ID: %s) cannot attach 5G network.', sub_id)
            ad.log.info('Check if sub ID %s can attach LTE network.', sub_id)

            if not wait_for_network_generation_for_subscription(
                log,
                ad,
                sub_id,
                GEN_4G,
                voice_or_data=NETWORK_SERVICE_DATA):
                return False

            if rat.lower() == '5g':
                rat = '4g'
            elif rat.lower() == '5g_volte':
                rat = 'volte'
            elif rat.lower() == '5g_wfc':
                rat = 'wfc'
            elif rat.lower() == '5g_csfb':
                rat = 'csfb'

    if rat.lower() == '5g_volte':
        if not phone_idle_volte_for_subscription(log, ad, sub_id, GEN_5G, nr_type=nr_type):
            return False
    elif rat.lower() == '5g_csfb':
        if not phone_idle_csfb_for_subscription(log, ad, sub_id, GEN_5G, nr_type=nr_type):
            return False
    elif rat.lower() == '5g_wfc':
        if not wait_for_network_generation_for_subscription(
            log,
            ad,
            sub_id,
            GEN_5G,
            voice_or_data=NETWORK_SERVICE_DATA,
            nr_type=nr_type):
            return False
        if not wait_for_wfc_enabled(log, ad):
            return False
    elif rat.lower() == '5g':
        if not wait_for_network_generation_for_subscription(
            log,
            ad,
            sub_id,
            GEN_5G,
            voice_or_data=NETWORK_SERVICE_DATA,
            nr_type=nr_type):
            return False
    elif rat.lower() == 'volte':
        if not phone_idle_volte_for_subscription(log, ad, sub_id, GEN_4G):
            return False
    elif rat.lower() == 'csfb':
        if not phone_idle_csfb_for_subscription(log, ad, sub_id, GEN_4G):
            return False
    elif rat.lower() == 'wfc':
        if not wait_for_network_generation_for_subscription(
            log,
            ad,
            sub_id,
            GEN_4G,
            voice_or_data=NETWORK_SERVICE_DATA):
            return False
        if not wait_for_wfc_enabled(log, ad):
            return False
    elif rat.lower() == '4g':
        if not wait_for_network_generation_for_subscription(
            log,
            ad,
            sub_id,
            GEN_4G,
            voice_or_data=NETWORK_SERVICE_DATA):
            return False
    return True


def ensure_preferred_network_type_for_subscription(
        ad,
        network_preference
        ):
    sub_id = ad.droid.subscriptionGetDefaultSubId()
    if not ad.droid.telephonySetPreferredNetworkTypesForSubscription(
            network_preference, sub_id):
        ad.log.error("Set sub_id %s Preferred Networks Type %s failed.",
                     sub_id, network_preference)
    return True


def ensure_network_rat(log,
                       ad,
                       network_preference,
                       rat_family,
                       voice_or_data=None,
                       max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                       toggle_apm_after_setting=False):
    """Ensure ad's current network is in expected rat_family.
    """
    return ensure_network_rat_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), network_preference,
        rat_family, voice_or_data, max_wait_time, toggle_apm_after_setting)


def ensure_network_rat_for_subscription(
        log,
        ad,
        sub_id,
        network_preference,
        rat_family,
        voice_or_data=None,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        toggle_apm_after_setting=False):
    """Ensure ad's current network is in expected rat_family.
    """
    if not ad.droid.telephonySetPreferredNetworkTypesForSubscription(
            network_preference, sub_id):
        ad.log.error("Set sub_id %s Preferred Networks Type %s failed.",
                     sub_id, network_preference)
        return False
    if is_droid_in_rat_family_for_subscription(log, ad, sub_id, rat_family,
                                               voice_or_data):
        ad.log.info("Sub_id %s in RAT %s for %s", sub_id, rat_family,
                    voice_or_data)
        return True

    if toggle_apm_after_setting:
        toggle_airplane_mode(log, ad, new_state=True, strict_checking=False)
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
        toggle_airplane_mode(log, ad, new_state=None, strict_checking=False)

    result = wait_for_network_rat_for_subscription(
        log, ad, sub_id, rat_family, max_wait_time, voice_or_data)

    log.info(
        "End of ensure_network_rat_for_subscription for %s. "
        "Setting to %s, Expecting %s %s. Current: voice: %s(family: %s), "
        "data: %s(family: %s)", ad.serial, network_preference, rat_family,
        voice_or_data,
        ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(sub_id),
        rat_family_from_rat(
            ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(
                sub_id)),
        ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(sub_id),
        rat_family_from_rat(
            ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(
                sub_id)))
    return result


def ensure_network_preference(log,
                              ad,
                              network_preference,
                              voice_or_data=None,
                              max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                              toggle_apm_after_setting=False):
    """Ensure that current rat is within the device's preferred network rats.
    """
    return ensure_network_preference_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), network_preference,
        voice_or_data, max_wait_time, toggle_apm_after_setting)


def ensure_network_preference_for_subscription(
        log,
        ad,
        sub_id,
        network_preference,
        voice_or_data=None,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        toggle_apm_after_setting=False):
    """Ensure ad's network preference is <network_preference> for sub_id.
    """
    rat_family_list = rat_families_for_network_preference(network_preference)
    if not ad.droid.telephonySetPreferredNetworkTypesForSubscription(
            network_preference, sub_id):
        log.error("Set Preferred Networks failed.")
        return False
    if is_droid_in_rat_family_list_for_subscription(
            log, ad, sub_id, rat_family_list, voice_or_data):
        return True

    if toggle_apm_after_setting:
        toggle_airplane_mode(log, ad, new_state=True, strict_checking=False)
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
        toggle_airplane_mode(log, ad, new_state=False, strict_checking=False)

    result = wait_for_preferred_network_for_subscription(
        log, ad, sub_id, network_preference, max_wait_time, voice_or_data)

    ad.log.info(
        "End of ensure_network_preference_for_subscription. "
        "Setting to %s, Expecting %s %s. Current: voice: %s(family: %s), "
        "data: %s(family: %s)", network_preference, rat_family_list,
        voice_or_data,
        ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(sub_id),
        rat_family_from_rat(
            ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(
                sub_id)),
        ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(sub_id),
        rat_family_from_rat(
            ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(
                sub_id)))
    return result


def ensure_network_generation(log,
                              ad,
                              generation,
                              max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                              voice_or_data=None,
                              toggle_apm_after_setting=False,
                              nr_type=None):
    """Ensure ad's network is <network generation> for default subscription ID.

    Set preferred network generation to <generation>.
    Toggle ON/OFF airplane mode if necessary.
    Wait for ad in expected network type.
    """
    return ensure_network_generation_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), generation,
        max_wait_time, voice_or_data, toggle_apm_after_setting, nr_type=nr_type)


def ensure_network_generation_for_subscription(
        log,
        ad,
        sub_id,
        generation,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        voice_or_data=None,
        toggle_apm_after_setting=False,
        nr_type=None):
    """Ensure ad's network is <network generation> for specified subscription ID.

        Set preferred network generation to <generation>.
        Toggle ON/OFF airplane mode if necessary.
        Wait for ad in expected network type.

    Args:
        log: log object.
        ad: android device object.
        sub_id: subscription id.
        generation: network generation, e.g. GEN_2G, GEN_3G, GEN_4G, GEN_5G.
        max_wait_time: the time to wait for NW selection.
        voice_or_data: check voice network generation or data network generation
            This parameter is optional. If voice_or_data is None, then if
            either voice or data in expected generation, function will return True.
        toggle_apm_after_setting: Cycle airplane mode if True, otherwise do nothing.

    Returns:
        True if success, False if fail.
    """
    ad.log.info(
        "RAT network type voice: %s, data: %s",
        ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(sub_id),
        ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(sub_id))

    try:
        ad.log.info("Finding the network preference for generation %s for "
                    "operator %s phone type %s", generation,
                    ad.telephony["subscription"][sub_id]["operator"],
                    ad.telephony["subscription"][sub_id]["phone_type"])
        network_preference = network_preference_for_generation(
            generation, ad.telephony["subscription"][sub_id]["operator"],
            ad.telephony["subscription"][sub_id]["phone_type"])
        if ad.telephony["subscription"][sub_id]["operator"] == CARRIER_FRE \
            and generation == GEN_4G:
            network_preference = NETWORK_MODE_LTE_ONLY
        ad.log.info("Network preference for %s is %s", generation,
                    network_preference)
        rat_family = rat_family_for_generation(
            generation, ad.telephony["subscription"][sub_id]["operator"],
            ad.telephony["subscription"][sub_id]["phone_type"])
    except KeyError as e:
        ad.log.error("Failed to find a rat_family entry for generation %s"
                     " for subscriber id %s with error %s", generation,
                     sub_id, e)
        return False

    if not set_preferred_network_mode_pref(log, ad, sub_id,
                                           network_preference):
        return False

    if hasattr(ad, "dsds") and voice_or_data == "data" and sub_id != get_default_data_sub_id(ad):
        ad.log.info("MSIM - Non DDS, ignore data RAT")
        return True

    if (generation == GEN_5G) or (generation == RAT_5G):
        if is_current_network_5g(ad, sub_id=sub_id, nr_type=nr_type):
            ad.log.info("Current network type is 5G.")
            return True
        else:
            ad.log.error("Not in 5G coverage for Sub %s.", sub_id)
            return False

    if is_droid_in_network_generation_for_subscription(
            log, ad, sub_id, generation, voice_or_data):
        return True

    if toggle_apm_after_setting:
        toggle_airplane_mode(log, ad, new_state=True, strict_checking=False)
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
        toggle_airplane_mode(log, ad, new_state=False, strict_checking=False)

    result = wait_for_network_generation_for_subscription(
        log, ad, sub_id, generation, max_wait_time, voice_or_data)

    ad.log.info(
        "Ensure network %s %s %s. With network preference %s, "
        "current: voice: %s(family: %s), data: %s(family: %s)", generation,
        voice_or_data, result, network_preference,
        ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(sub_id),
        rat_generation_from_rat(
            ad.droid.telephonyGetCurrentVoiceNetworkTypeForSubscription(
                sub_id)),
        ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(sub_id),
        rat_generation_from_rat(
            ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(
                sub_id)))
    if not result:
        get_telephony_signal_strength(ad)
    return result


def wait_for_network_rat(log,
                         ad,
                         rat_family,
                         max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                         voice_or_data=None):
    return wait_for_network_rat_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), rat_family,
        max_wait_time, voice_or_data)


def wait_for_network_rat_for_subscription(
        log,
        ad,
        sub_id,
        rat_family,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        voice_or_data=None):
    return _wait_for_droid_in_state_for_subscription(
        log, ad, sub_id, max_wait_time,
        is_droid_in_rat_family_for_subscription, rat_family, voice_or_data)


def wait_for_not_network_rat(log,
                             ad,
                             rat_family,
                             max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                             voice_or_data=None):
    return wait_for_not_network_rat_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), rat_family,
        max_wait_time, voice_or_data)


def wait_for_not_network_rat_for_subscription(
        log,
        ad,
        sub_id,
        rat_family,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        voice_or_data=None):
    return _wait_for_droid_in_state_for_subscription(
        log, ad, sub_id, max_wait_time,
        lambda log, ad, sub_id, *args, **kwargs: not is_droid_in_rat_family_for_subscription(log, ad, sub_id, rat_family, voice_or_data)
    )


def wait_for_preferred_network(log,
                               ad,
                               network_preference,
                               max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                               voice_or_data=None):
    return wait_for_preferred_network_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), network_preference,
        max_wait_time, voice_or_data)


def wait_for_preferred_network_for_subscription(
        log,
        ad,
        sub_id,
        network_preference,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        voice_or_data=None):
    rat_family_list = rat_families_for_network_preference(network_preference)
    return _wait_for_droid_in_state_for_subscription(
        log, ad, sub_id, max_wait_time,
        is_droid_in_rat_family_list_for_subscription, rat_family_list,
        voice_or_data)


def wait_for_network_generation(log,
                                ad,
                                generation,
                                max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
                                voice_or_data=None):
    return wait_for_network_generation_for_subscription(
        log, ad, ad.droid.subscriptionGetDefaultSubId(), generation,
        max_wait_time, voice_or_data)


def wait_for_network_generation_for_subscription(
        log,
        ad,
        sub_id,
        generation,
        max_wait_time=MAX_WAIT_TIME_NW_SELECTION,
        voice_or_data=None,
        nr_type=None):

    if generation == GEN_5G:
        if is_current_network_5g(ad, sub_id=sub_id, nr_type=nr_type):
            ad.log.info("Current network type is 5G.")
            return True
        else:
            ad.log.error("Not in 5G coverage for Sub %s.", sub_id)
            return False

    return _wait_for_droid_in_state_for_subscription(
        log, ad, sub_id, max_wait_time,
        is_droid_in_network_generation_for_subscription, generation,
        voice_or_data)


def ensure_phones_idle(log, ads, max_time=MAX_WAIT_TIME_CALL_DROP):
    """Ensure ads idle (not in call).
    """
    result = True
    for ad in ads:
        if not ensure_phone_idle(log, ad, max_time=max_time):
            result = False
    return result


def ensure_phone_idle(log, ad, max_time=MAX_WAIT_TIME_CALL_DROP, retry=2):
    """Ensure ad idle (not in call).
    """
    while ad.droid.telecomIsInCall() and retry > 0:
        ad.droid.telecomEndCall()
        time.sleep(3)
        retry -= 1
    if not wait_for_droid_not_in_call(log, ad, max_time=max_time):
        ad.log.error("Failed to end call")
        return False
    return True


def ensure_phone_subscription(log, ad):
    """Ensure Phone Subscription.
    """
    #check for sim and service
    duration = 0
    while duration < MAX_WAIT_TIME_NW_SELECTION:
        subInfo = ad.droid.subscriptionGetAllSubInfoList()
        if subInfo and len(subInfo) >= 1:
            ad.log.debug("Find valid subcription %s", subInfo)
            break
        else:
            ad.log.info("Did not find any subscription")
            time.sleep(5)
            duration += 5
    else:
        ad.log.error("Unable to find a valid subscription!")
        return False
    while duration < MAX_WAIT_TIME_NW_SELECTION:
        data_sub_id = ad.droid.subscriptionGetDefaultDataSubId()
        voice_sub_id = ad.droid.subscriptionGetDefaultVoiceSubId()
        if data_sub_id > INVALID_SUB_ID or voice_sub_id > INVALID_SUB_ID:
            ad.log.debug("Find valid voice or data sub id")
            break
        else:
            ad.log.info("Did not find valid data or voice sub id")
            if getattr(ad, 'mep', False):
                default_slot = getattr(ad, "default_slot", 1)
                if get_subid_from_slot_index(ad.log, ad, default_slot) != INVALID_SUB_ID:
                    ad.log.info("Slot %s is the default slot.", default_slot)
                    set_default_sub_for_all_services(ad, default_slot)
                else:
                    ad.log.warning("Slot %s is NOT a valid slot. Slot %s will be used by default.",
                        default_slot, 1-default_slot)
                    set_default_sub_for_all_services(ad, 1-default_slot)
                    setattr(ad, "default_slot", 1-default_slot)
            elif getattr(ad, 'dsds', False):
                default_slot = getattr(ad, "default_slot", 0)
                if get_subid_from_slot_index(ad.log, ad, default_slot) != INVALID_SUB_ID:
                    ad.log.info("Slot %s is the default slot.", default_slot)
                    set_default_sub_for_all_services(ad, default_slot)
                else:
                    ad.log.warning("Slot %s is NOT a valid slot. Slot %s will be used by default.",
                        default_slot, 1-default_slot)
                    set_default_sub_for_all_services(ad, 1-default_slot)
                    setattr(ad, "default_slot", 1-default_slot)
            time.sleep(5)
            duration += 5
    else:
        ad.log.error("Unable to find valid data or voice sub id")
        return False
    while duration < MAX_WAIT_TIME_NW_SELECTION:
        data_sub_id = ad.droid.subscriptionGetDefaultDataSubId()
        if data_sub_id > INVALID_SUB_ID:
            data_rat = get_network_rat_for_subscription(
                log, ad, data_sub_id, NETWORK_SERVICE_DATA)
        else:
            data_rat = RAT_UNKNOWN
        if voice_sub_id > INVALID_SUB_ID:
            voice_rat = get_network_rat_for_subscription(
                log, ad, voice_sub_id, NETWORK_SERVICE_VOICE)
        else:
            voice_rat = RAT_UNKNOWN
        if data_rat != RAT_UNKNOWN or voice_rat != RAT_UNKNOWN:
            ad.log.info("Data sub_id %s in %s, voice sub_id %s in %s",
                        data_sub_id, data_rat, voice_sub_id, voice_rat)
            return True
        else:
            ad.log.info("Did not attach for data or voice service")
            time.sleep(5)
            duration += 5
    else:
        ad.log.error("Did not attach for voice or data service")
        return False


def ensure_phone_default_state(log, ad, check_subscription=True, retry=2):
    """Ensure ad in default state.
    Phone not in call.
    Phone have no stored WiFi network and WiFi disconnected.
    Phone not in airplane mode.
    Phone is data on.
    """
    result = True
    if not toggle_airplane_mode(log, ad, False, False):
        ad.log.error("Fail to turn off airplane mode")
        result = False
    try:
        ad.droid.telephonyToggleDataConnection(True)
        set_wifi_to_default(log, ad)
        while ad.droid.telecomIsInCall() and retry > 0:
            ad.droid.telecomEndCall()
            time.sleep(3)
            retry -= 1
        if not wait_for_droid_not_in_call(log, ad):
            ad.log.error("Failed to end call")
        #ad.droid.telephonyFactoryReset()
        data_roaming = getattr(ad, 'roaming', False)
        if get_cell_data_roaming_state_by_adb(ad) != data_roaming:
            set_cell_data_roaming_state_by_adb(ad, data_roaming)
        #remove_mobile_data_usage_limit(ad)
        if not wait_for_not_network_rat(
                log, ad, RAT_FAMILY_WLAN, voice_or_data=NETWORK_SERVICE_DATA):
            ad.log.error("%s still in %s", NETWORK_SERVICE_DATA,
                         RAT_FAMILY_WLAN)
            result = False

        if check_subscription and not ensure_phone_subscription(log, ad):
            ad.log.error("Unable to find a valid subscription!")
            result = False
    except Exception as e:
        ad.log.error("%s failure, toggle APM instead", e)
        toggle_airplane_mode_by_adb(log, ad, True)
        toggle_airplane_mode_by_adb(log, ad, False)
        ad.send_keycode("ENDCALL")
        ad.adb.shell("settings put global wfc_ims_enabled 0")
        ad.adb.shell("settings put global mobile_data 1")

    return result


def ensure_phones_default_state(log, ads, check_subscription=True):
    """Ensure ads in default state.
    Phone not in call.
    Phone have no stored WiFi network and WiFi disconnected.
    Phone not in airplane mode.

    Returns:
        True if all steps of restoring default state succeed.
        False if any of the steps to restore default state fails.
    """
    tasks = []
    for ad in ads:
        tasks.append((ensure_phone_default_state, (log, ad,
                                                   check_subscription)))
    if not multithread_func(log, tasks):
        log.error("Ensure_phones_default_state Fail.")
        return False
    return True


def is_phone_not_in_call(log, ad):
    """Return True if phone not in call.

    Args:
        log: log object.
        ad:  android device.
    """
    in_call = ad.droid.telecomIsInCall()
    call_state = ad.droid.telephonyGetCallState()
    if in_call:
        ad.log.info("Device is In Call")
    if call_state != TELEPHONY_STATE_IDLE:
        ad.log.info("Call_state is %s, not %s", call_state,
                    TELEPHONY_STATE_IDLE)
    return ((not in_call) and (call_state == TELEPHONY_STATE_IDLE))


def wait_for_droid_not_in_call(log, ad, max_time=MAX_WAIT_TIME_CALL_DROP):
    """Wait for android to be not in call state.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        If phone become not in call state within max_time, return True.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, is_phone_not_in_call)


def wait_for_voice_attach(log, ad, max_time=MAX_WAIT_TIME_NW_SELECTION):
    """Wait for android device to attach on voice.

    Args:
        log: log object.
        ad:  android device.
        max_time: maximal wait time.

    Returns:
        Return True if device attach voice within max_time.
        Return False if timeout.
    """
    return _wait_for_droid_in_state(log, ad, max_time, _is_attached,
                                    NETWORK_SERVICE_VOICE)


def wait_for_voice_attach_for_subscription(
        log, ad, sub_id, max_time=MAX_WAIT_TIME_NW_SELECTION):
    """Wait for android device to attach on voice in subscription id.

    Args:
        log: log object.
        ad:  android device.
        sub_id: subscription id.
        max_time: maximal wait time.

    Returns:
        Return True if device attach voice within max_time.
        Return False if timeout.
    """
    if not _wait_for_droid_in_state_for_subscription(
            log, ad, sub_id, max_time, _is_attached_for_subscription,
            NETWORK_SERVICE_VOICE):
        return False

    # TODO: b/26295983 if pone attach to 1xrtt from unknown, phone may not
    # receive incoming call immediately.
    if ad.droid.telephonyGetCurrentVoiceNetworkType() == RAT_1XRTT:
        time.sleep(WAIT_TIME_1XRTT_VOICE_ATTACH)
    return True
