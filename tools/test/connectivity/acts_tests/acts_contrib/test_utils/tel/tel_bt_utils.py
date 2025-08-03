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

from acts_contrib.test_utils.bt.bt_test_utils import bluetooth_enabled_check
from acts_contrib.test_utils.bt.bt_test_utils import disable_bluetooth
from acts_contrib.test_utils.bt.bt_test_utils import pair_pri_to_sec
from acts_contrib.test_utils.tel.tel_defines import NETWORK_SERVICE_DATA
from acts_contrib.test_utils.tel.tel_data_utils import test_internet_connection
from acts_contrib.test_utils.tel.tel_data_utils import wait_for_cell_data_connection
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_network_generation
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.tel.tel_test_utils import wait_for_state
from acts_contrib.test_utils.tel.tel_voice_utils import call_setup_teardown
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call


def enable_bluetooth_tethering_connection(log, provider, clients):
    for ad in [provider] + clients:
        if not bluetooth_enabled_check(ad):
            ad.log.info("Bluetooth is not enabled")
            return False
        else:
            ad.log.info("Bluetooth is enabled")
    time.sleep(5)
    provider.log.info("Provider enabling bluetooth tethering")
    try:
        provider.droid.bluetoothPanSetBluetoothTethering(True)
    except Exception as e:
        provider.log.warning(
            "Failed to enable provider Bluetooth tethering with %s", e)
        provider.droid.bluetoothPanSetBluetoothTethering(True)

    if wait_for_state(provider.droid.bluetoothPanIsTetheringOn, True):
        provider.log.info("Provider Bluetooth tethering is enabled.")
    else:
        provider.log.error(
            "Failed to enable provider Bluetooth tethering.")
        provider.log.error("bluetoothPanIsTetheringOn = %s",
                           provider.droid.bluetoothPanIsTetheringOn())
        return False
    for client in clients:
        if not (pair_pri_to_sec(provider, client)):
            client.log.error("Client failed to pair with provider")
            return False
        else:
            client.log.info("Client paired with provider")

    time.sleep(5)
    for client in clients:
        client.droid.bluetoothConnectBonded(provider.droid.bluetoothGetLocalAddress())

    time.sleep(20)
    return True


def verify_bluetooth_tethering_connection(log, provider, clients,
                                           change_rat=None,
                                           toggle_data=False,
                                           toggle_tethering=False,
                                           voice_call=False,
                                           toggle_bluetooth=True):
    """Setups up a bluetooth tethering connection between two android devices.

    Returns:
        True if PAN connection and verification is successful,
        false if unsuccessful.
    """


    if not enable_bluetooth_tethering_connection(log, provider, clients):
        return False

    if not test_internet_connection(log, provider, clients):
        log.error("Internet connection check failed")
        return False
    if voice_call:
        log.info("====== Voice call test =====")
        for caller, callee in [(provider, clients[0]),
                               (clients[0], provider)]:
            if not call_setup_teardown(
                    log, caller, callee, ad_hangup=None):
                log.error("Setup Call Failed.")
                hangup_call(log, caller)
                return False
            log.info("Verify data.")
            if not verify_internet_connection(
                    log, clients[0], retries=1):
                clients[0].log.warning(
                    "client internet connection state is not on")
            else:
                clients[0].log.info(
                    "client internet connection state is on")
            hangup_call(log, caller)
            if not verify_internet_connection(
                    log, clients[0], retries=1):
                clients[0].log.warning(
                    "client internet connection state is not on")
                return False
            else:
                clients[0].log.info(
                    "client internet connection state is on")
    if toggle_tethering:
        log.info("====== Toggling provider bluetooth tethering =====")
        provider.log.info("Disable bluetooth tethering")
        provider.droid.bluetoothPanSetBluetoothTethering(False)
        if not test_internet_connection(log, provider, clients, False, True):
            log.error(
                "Internet connection check failed after disable tethering")
            return False
        provider.log.info("Enable bluetooth tethering")
        if not enable_bluetooth_tethering_connection(log,
                provider, clients):
            provider.log.error(
                "Fail to re-enable bluetooth tethering")
            return False
        if not test_internet_connection(log, provider, clients, True, True):
            log.error(
                "Internet connection check failed after enable tethering")
            return False
    if toggle_bluetooth:
        log.info("====== Toggling provider bluetooth =====")
        provider.log.info("Disable provider bluetooth")
        disable_bluetooth(provider.droid)
        time.sleep(10)
        if not test_internet_connection(log, provider, clients, False, True):
            log.error(
                "Internet connection check failed after disable bluetooth")
            return False
        if not enable_bluetooth_tethering_connection(log,
                provider, clients):
            provider.log.error(
                "Fail to re-enable bluetooth tethering")
            return False
        if not test_internet_connection(log, provider, clients, True, True):
            log.error(
                "Internet connection check failed after enable bluetooth")
            return False
    if toggle_data:
        log.info("===== Toggling provider data connection =====")
        provider.log.info("Disable provider data connection")
        provider.droid.telephonyToggleDataConnection(False)
        time.sleep(10)
        if not test_internet_connection(log, provider, clients, False, False):
            return False
        provider.log.info("Enable provider data connection")
        provider.droid.telephonyToggleDataConnection(True)
        if not wait_for_cell_data_connection(log, provider,
                                             True):
            provider.log.error(
                "Provider failed to enable data connection.")
            return False
        if not test_internet_connection(log, provider, clients, True, True):
            log.error(
                "Internet connection check failed after enable data")
            return False
    if change_rat:
        log.info("===== Change provider RAT to %s =====", change_rat)
        if not ensure_network_generation(
                log,
                provider,
                change_rat,
                voice_or_data=NETWORK_SERVICE_DATA,
                toggle_apm_after_setting=False):
            provider.log.error("Provider failed to reselect to %s.",
                                    change_rat)
            return False
        if not test_internet_connection(log, provider, clients, True, True):
            log.error(
                "Internet connection check failed after RAT change to %s",
                change_rat)
            return False
    return True