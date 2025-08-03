#!/usr/bin/env python3
#
#   Copyright 2020 - Google
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

from acts.libs.utils.multithread import multithread_func
from acts_contrib.test_utils.tel.tel_defines import NETWORK_MODE_NR_LTE_GSM_WCDMA
from acts_contrib.test_utils.tel.tel_defines import NETWORK_MODE_NR_ONLY
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import GEN_4G
from acts_contrib.test_utils.tel.tel_defines import NETWORK_SERVICE_DATA
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_ANDROID_STATE_SETTLING
from acts_contrib.test_utils.tel.tel_defines import NETWORK_MODE_WCDMA_ONLY
from acts_contrib.test_utils.tel.tel_5g_utils import is_current_network_5g_nsa
from acts_contrib.test_utils.tel.tel_5g_utils import is_current_network_5g_sa
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_iwlan
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_csfb
from acts_contrib.test_utils.tel.tel_phone_setup_utils import wait_for_network_generation
from acts_contrib.test_utils.tel.tel_test_utils import set_preferred_network_mode_pref
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_test_utils import get_current_override_network_type
from acts_contrib.test_utils.tel.tel_wifi_utils import ensure_wifi_connected
from acts_contrib.test_utils.tel.tel_wifi_utils import wifi_toggle_state


def provision_device_for_5g(log, ads, nr_type = None, mmwave = None):
    """Provision Devices for 5G

    Args:
        log: Log object.
        ads: android device object(s).
        nr_type: NR network type.
        mmwave: True to detect 5G millimeter wave, False to detect sub-6,
            None to detect both.

    Returns:
        True: Device(s) are provisioned on 5G
        False: Device(s) are not provisioned on 5G
    """
    if nr_type == 'sa':
        if not provision_device_for_5g_sa(
            log, ads, mmwave=mmwave):
            return False
    elif nr_type == 'nsa':
        if not provision_device_for_5g_nsa(
            log, ads, mmwave=mmwave):
            return False
    elif nr_type == 'mmwave':
        if not provision_device_for_5g_nsa(
            log, ads, mmwave=mmwave):
            return False
    else:
        if not provision_device_for_5g_nsa(
            log, ads, mmwave=mmwave):
            return False
    return True


def provision_device_for_5g_nsa(log, ads, mmwave = None):
    """Provision Devices for 5G NSA

    Args:
        log: Log object.
        ads: android device object(s).
        mmwave: True to detect 5G millimeter wave, False to detect sub-6,
            None to detect both.

    Returns:
        True: Device(s) are provisioned on 5G NSA
        False: Device(s) are not provisioned on 5G NSA
    """

    if isinstance(ads, list):
        # Mode Pref
        tasks = [(set_preferred_mode_for_5g, [ad]) for ad in ads]
        if not multithread_func(log, tasks):
            log.error("failed to set preferred network mode on 5g")
            return False
        # Attach
        tasks = [(is_current_network_5g_nsa, [ad, None, mmwave]) for ad in ads]
        if not multithread_func(log, tasks):
            log.error("phone not on 5g")
            return False
        return True
    else:
        # Mode Pref
        set_preferred_mode_for_5g(ads)

        # Attach nsa5g
        if not is_current_network_5g_nsa(ads, mmwave=mmwave):
            ads.log.error("Phone not attached on 5g")
            return False
        return True


def provision_both_devices_for_volte(log, ads, nw_gen, nr_type=None):
    # LTE or NR attach and enable VoLTE on both phones
    tasks = [(phone_setup_volte, (log, ads[0], nw_gen, nr_type)),
             (phone_setup_volte, (log, ads[1], nw_gen, nr_type))]
    if not multithread_func(log, tasks):
        log.error("phone failed to set up in volte")
        return False
    return True


def provision_both_devices_for_csfb(log, ads):
    tasks = [(phone_setup_csfb, (log, ads[0])),
             (phone_setup_csfb, (log, ads[1]))]
    if not multithread_func(log, tasks):
        log.error("Phone Failed to Set Up in csfb.")
        return False
    return True


def provision_both_devices_for_wfc_cell_pref(log,
                                             ads,
                                             wifi_ssid,
                                             wifi_pass,
                                             apm_mode=False):
    tasks = [(phone_setup_iwlan,
              (log, ads[0], apm_mode, WFC_MODE_CELLULAR_PREFERRED,
               wifi_ssid, wifi_pass)),
             (phone_setup_iwlan,
              (log, ads[1], apm_mode, WFC_MODE_CELLULAR_PREFERRED,
               wifi_ssid, wifi_pass))]
    if not multithread_func(log, tasks):
        log.error("failed to setup in wfc_cell_pref mode")
        return False
    return True


def provision_both_devices_for_wfc_wifi_pref(log,
                                             ads,
                                             wifi_ssid,
                                             wifi_pass,
                                             apm_mode=False):
    tasks = [(phone_setup_iwlan,
              (log, ads[0], apm_mode, WFC_MODE_WIFI_PREFERRED,
               wifi_ssid, wifi_pass)),
             (phone_setup_iwlan,
              (log, ads[1], apm_mode, WFC_MODE_WIFI_PREFERRED,
               wifi_ssid, wifi_pass))]
    if not multithread_func(log, tasks):
        log.error("failed to setup in wfc_wifi_pref mode")
        return False
    return True


def disable_apm_mode_both_devices(log, ads):
    # Turn off airplane mode
    log.info("Turn off apm mode on both devices")
    tasks = [(toggle_airplane_mode, (log, ads[0], False)),
             (toggle_airplane_mode, (log, ads[1], False))]
    if not multithread_func(log, tasks):
        log.error("Failed to turn off airplane mode")
        return False
    return True


def connect_both_devices_to_wifi(log,
                                 ads,
                                 wifi_ssid,
                                 wifi_pass):
    tasks = [(ensure_wifi_connected, (log, ad, wifi_ssid, wifi_pass))
             for ad in ads]
    if not multithread_func(log, tasks):
        log.error("phone failed to connect to wifi.")
        return False
    return True


def verify_5g_attach_for_both_devices(log, ads, nr_type = None, mmwave = None):
    """Verify the network is attached

    Args:
        log: Log object.
        ads: android device object(s).
        nr_type: 'sa' for 5G standalone, 'nsa' for 5G non-standalone,
            'mmwave' for 5G millimeter wave.
        mmwave: True to detect 5G millimeter wave, False to detect sub-6,
            None to detect both.

    Returns:
        True: Device(s) are attached on 5G
        False: Device(s) are not attached on 5G NSA
    """

    if nr_type=='sa':
        # Attach
        tasks = [(is_current_network_5g_sa, [ad, None, mmwave]) for ad in ads]
        if not multithread_func(log, tasks):
            log.error("phone not on 5g sa")
            return False
        return True
    else:
        # Attach
        tasks = [(is_current_network_5g_nsa, [ad, None, mmwave]) for ad in ads]
        if not multithread_func(log, tasks):
            log.error("phone not on 5g nsa")
            return False
        return True


def set_preferred_mode_for_5g(ad, sub_id=None, mode=None):
    """Set Preferred Network Mode for 5G NSA
    Args:
        ad: Android device object.
        sub_id: Subscription ID.
        mode: 5G Network Mode Type
    """
    if sub_id is None:
        sub_id = ad.droid.subscriptionGetDefaultSubId()
    if mode is None:
        mode = NETWORK_MODE_NR_LTE_GSM_WCDMA
    return set_preferred_network_mode_pref(ad.log, ad, sub_id, mode)


def provision_device_for_5g_sa(log, ads, mmwave = None):
    """Provision Devices for 5G SA

    Args:
        log: Log object.
        ads: android device object(s).
        mmwave: True to detect 5G millimeter wave, False to detect sub-6,
            None to detect both.

    Returns:
        True: Device(s) are provisioned on 5G SA
        False: Device(s) are not provisioned on 5G SA
    """

    if isinstance(ads, list):
        # Mode Pref
        tasks = [(set_preferred_mode_for_5g, [ad, None, NETWORK_MODE_NR_ONLY]) for ad in ads]
        if not multithread_func(log, tasks):
            log.error("failed to set preferred network mode on 5g SA")
            return False

        tasks = [(is_current_network_5g_sa, [ad, None, mmwave]) for ad in ads]
        if not multithread_func(log, tasks):
            log.error("phone not on 5g SA")
            return False
        return True
    else:
        # Mode Pref
        set_preferred_mode_for_5g(ads, None, NETWORK_MODE_NR_ONLY)

        if not is_current_network_5g_sa(ads, None, mmwave):
            ads.log.error("Phone not attached on SA 5g")
            return False
        return True


def check_current_network_5g(
    ad, sub_id = None, nr_type = None, mmwave = None, timeout = 30):
    """Verifies data network type is on 5G

    Args:
        ad: android device object.
        sub_id: The target SIM for querying.
        nr_type: 'sa' for 5G standalone, 'nsa' for 5G non-standalone, 'mmwave' for 5G millimeter
                wave.
        mmwave: True to detect 5G millimeter wave, False to detect sub-6,
            None to detect both.
        timeout: max time to wait for event.

    Returns:
        True: if data is on 5g
        False: if data is not on 5g
    """
    sub_id = sub_id if sub_id else ad.droid.subscriptionGetDefaultDataSubId()

    if nr_type == 'sa':
        if not is_current_network_5g_sa(ad, sub_id, mmwave=mmwave):
            return False
    else:
        if not is_current_network_5g_nsa(ad, sub_id, mmwave=mmwave,
                                         timeout=timeout):
            return False
    return True


def test_activation_by_condition(ad, sub_id=None, from_3g=False, nr_type=None,
                                 precond_func=None, mmwave=None):
    """Test 5G activation based on various pre-conditions.

    Args:
        ad: android device object.
        sub_id: The target SIM for querying.
        from_3g: If true, test 5G activation from 3G attaching. Otherwise, starting from 5G attaching.
        nr_type: check the band of NR network. Default is to check sub-6.
        precond_func: A function to execute pre conditions before testing 5G activation.
        mmwave: True to detect 5G millimeter wave, False to detect sub-6,
            None to detect both.

    Returns:
        If success, return true. Otherwise, return false.
    """
    sub_id = sub_id if sub_id else ad.droid.subscriptionGetDefaultDataSubId()

    wifi_toggle_state(ad.log, ad, False)
    toggle_airplane_mode(ad.log, ad, False)
    if not from_3g:
        set_preferred_mode_for_5g(ad)
    for iteration in range(3):
        ad.log.info("Attempt %d", iteration + 1)
        sub_id=ad.droid.subscriptionGetDefaultSubId()
        if from_3g:
            # Set mode pref to 3G
            set_preferred_network_mode_pref(ad.log,
                                            ad,
                                            sub_id,
                                            NETWORK_MODE_WCDMA_ONLY)
            time.sleep(15)
            # Set mode pref to 5G
            set_preferred_mode_for_5g(ad)

        elif precond_func:
            if not precond_func():
                return False
        # LTE attach
        if not wait_for_network_generation(
                ad.log, ad, GEN_4G, voice_or_data=NETWORK_SERVICE_DATA):
            ad.log.error("Fail to ensure initial data in 4G")
        # 5G attach
        ad.log.info("Waiting for 5g NSA attach for 60 secs")
        if is_current_network_5g_nsa(ad, sub_id, mmwave=mmwave, timeout=60):
            ad.log.info("Success! attached on 5g NSA")
            return True
        else:
            ad.log.error("Failure - expected NR_NSA, current %s",
                         get_current_override_network_type(ad))
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
    ad.log.info("nsa5g attach test FAIL for all 3 iterations")
    return False
