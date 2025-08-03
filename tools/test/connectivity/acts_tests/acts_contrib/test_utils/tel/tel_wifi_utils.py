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

from acts_contrib.test_utils.tel.tel_defines import TYPE_WIFI
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_BETWEEN_STATE_CHECK
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.wifi import wifi_test_utils

WIFI_SSID_KEY = wifi_test_utils.WifiEnums.SSID_KEY
WIFI_PWD_KEY = wifi_test_utils.WifiEnums.PWD_KEY
WIFI_CONFIG_APBAND_2G = 1
WIFI_CONFIG_APBAND_5G = 2
WIFI_CONFIG_APBAND_AUTO = wifi_test_utils.WifiEnums.WIFI_CONFIG_APBAND_AUTO


def get_wifi_signal_strength(ad):
    signal_strength = ad.droid.wifiGetConnectionInfo()['rssi']
    ad.log.info("WiFi Signal Strength is %s" % signal_strength)
    return signal_strength


def get_wifi_usage(ad, sid=None, apk=None):
    if not sid:
        sid = ad.droid.subscriptionGetDefaultDataSubId()
    current_time = int(time.time() * 1000)
    begin_time = current_time - 10 * 24 * 60 * 60 * 1000
    end_time = current_time + 10 * 24 * 60 * 60 * 1000

    if apk:
        uid = ad.get_apk_uid(apk)
        ad.log.debug("apk %s uid = %s", apk, uid)
        try:
            return ad.droid.connectivityQueryDetailsForUid(
                TYPE_WIFI,
                ad.droid.telephonyGetSubscriberIdForSubscription(sid),
                begin_time, end_time, uid)
        except:
            return ad.droid.connectivityQueryDetailsForUid(
                ad.droid.telephonyGetSubscriberIdForSubscription(sid),
                begin_time, end_time, uid)
    else:
        try:
            return ad.droid.connectivityQuerySummaryForDevice(
                TYPE_WIFI,
                ad.droid.telephonyGetSubscriberIdForSubscription(sid),
                begin_time, end_time)
        except:
            return ad.droid.connectivityQuerySummaryForDevice(
                ad.droid.telephonyGetSubscriberIdForSubscription(sid),
                begin_time, end_time)


def check_is_wifi_connected(log, ad, wifi_ssid):
    """Check if ad is connected to wifi wifi_ssid.

    Args:
        log: Log object.
        ad: Android device object.
        wifi_ssid: WiFi network SSID.

    Returns:
        True if wifi is connected to wifi_ssid
        False if wifi is not connected to wifi_ssid
    """
    wifi_info = ad.droid.wifiGetConnectionInfo()
    if wifi_info["supplicant_state"] == "completed" and wifi_info["SSID"] == wifi_ssid:
        ad.log.info("Wifi is connected to %s", wifi_ssid)
        ad.on_mobile_data = False
        return True
    else:
        ad.log.info("Wifi is not connected to %s", wifi_ssid)
        ad.log.debug("Wifi connection_info=%s", wifi_info)
        ad.on_mobile_data = True
        return False


def ensure_wifi_connected(log, ad, wifi_ssid, wifi_pwd=None, retries=3, apm=False):
    """Ensure ad connected to wifi on network wifi_ssid.

    Args:
        log: Log object.
        ad: Android device object.
        wifi_ssid: WiFi network SSID.
        wifi_pwd: optional secure network password.
        retries: the number of retries.

    Returns:
        True if wifi is connected to wifi_ssid
        False if wifi is not connected to wifi_ssid
    """
    if not toggle_airplane_mode(log, ad, apm, strict_checking=False):
        return False

    network = {WIFI_SSID_KEY: wifi_ssid}
    if wifi_pwd:
        network[WIFI_PWD_KEY] = wifi_pwd
    for i in range(retries):
        if not ad.droid.wifiCheckState():
            ad.log.info("Wifi state is down. Turn on Wifi")
            ad.droid.wifiToggleState(True)
        if check_is_wifi_connected(log, ad, wifi_ssid):
            ad.log.info("Wifi is connected to %s", wifi_ssid)
            return verify_internet_connection(log, ad, retries=3)
        else:
            ad.log.info("Connecting to wifi %s", wifi_ssid)
            try:
                ad.droid.wifiConnectByConfig(network)
            except Exception:
                ad.log.info("Connecting to wifi by wifiConnect instead")
                ad.droid.wifiConnect(network)
            time.sleep(20)
            if check_is_wifi_connected(log, ad, wifi_ssid):
                ad.log.info("Connected to Wifi %s", wifi_ssid)
                return verify_internet_connection(log, ad, retries=3)
    ad.log.info("Fail to connected to wifi %s", wifi_ssid)
    return False


def forget_all_wifi_networks(log, ad):
    """Forget all stored wifi network information

    Args:
        log: log object
        ad: AndroidDevice object

    Returns:
        boolean success (True) or failure (False)
    """
    if not ad.droid.wifiGetConfiguredNetworks():
        ad.on_mobile_data = True
        return True
    try:
        old_state = ad.droid.wifiCheckState()
        wifi_test_utils.reset_wifi(ad)
        wifi_toggle_state(log, ad, old_state)
    except Exception as e:
        log.error("forget_all_wifi_networks with exception: %s", e)
        return False
    ad.on_mobile_data = True
    return True


def wifi_reset(log, ad, disable_wifi=True):
    """Forget all stored wifi networks and (optionally) disable WiFi

    Args:
        log: log object
        ad: AndroidDevice object
        disable_wifi: boolean to disable wifi, defaults to True
    Returns:
        boolean success (True) or failure (False)
    """
    if not forget_all_wifi_networks(log, ad):
        ad.log.error("Unable to forget all networks")
        return False
    if not wifi_toggle_state(log, ad, not disable_wifi):
        ad.log.error("Failed to toggle WiFi state to %s!", not disable_wifi)
        return False
    return True


def set_wifi_to_default(log, ad):
    """Set wifi to default state (Wifi disabled and no configured network)

    Args:
        log: log object
        ad: AndroidDevice object

    Returns:
        boolean success (True) or failure (False)
    """
    ad.droid.wifiFactoryReset()
    ad.droid.wifiToggleState(False)
    ad.on_mobile_data = True


def wifi_toggle_state(log, ad, state, retries=3):
    """Toggle the WiFi State

    Args:
        log: log object
        ad: AndroidDevice object
        state: True, False, or None

    Returns:
        boolean success (True) or failure (False)
    """
    for i in range(retries):
        if wifi_test_utils.wifi_toggle_state(ad, state, assert_on_fail=False):
            ad.on_mobile_data = not state
            return True
        time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
    return False


def start_wifi_tethering(log, ad, ssid, password, ap_band=None):
    """Start a Tethering Session

    Args:
        log: log object
        ad: AndroidDevice object
        ssid: the name of the WiFi network
        password: optional password, used for secure networks.
        ap_band=DEPRECATED specification of 2G or 5G tethering
    Returns:
        boolean success (True) or failure (False)
    """
    return wifi_test_utils._assert_on_fail_handler(
        wifi_test_utils.start_wifi_tethering,
        False,
        ad,
        ssid,
        password,
        band=ap_band)


def stop_wifi_tethering(log, ad):
    """Stop a Tethering Session

    Args:
        log: log object
        ad: AndroidDevice object
    Returns:
        boolean success (True) or failure (False)
    """
    return wifi_test_utils._assert_on_fail_handler(
        wifi_test_utils.stop_wifi_tethering, False, ad)