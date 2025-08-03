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

from queue import Empty
from acts_contrib.test_utils.tel.tel_defines import OverrideNetworkContainer
from acts_contrib.test_utils.tel.tel_defines import DisplayInfoContainer
from acts_contrib.test_utils.tel.tel_defines import EventDisplayInfoChanged

def is_current_network_5g_nsa(ad, sub_id = None, mmwave = None, timeout=30):
    """Verifies 5G NSA override network type
    Args:
        ad: android device object.
        sub_id: The target SIM for querying.
        mmwave: True to detect 5G millimeter wave, False to detect sub-6,
            None to detect both.
        timeout: max time to wait for event.

    Returns:
        True: if data is on nsa5g NSA
        False: if data is not on nsa5g NSA
    """
    sub_id = sub_id if sub_id else ad.droid.subscriptionGetDefaultDataSubId()

    def _nsa_display_monitor(ad, sub_id, mmwave, timeout):
        ad.ed.clear_events(EventDisplayInfoChanged)
        ad.droid.telephonyStartTrackingDisplayInfoChangeForSubscription(sub_id)
        if mmwave:
            nsa_band = OverrideNetworkContainer.OVERRIDE_NETWORK_TYPE_NR_MMWAVE
        else:
            nsa_band = OverrideNetworkContainer.OVERRIDE_NETWORK_TYPE_NR_NSA
        try:
            event = ad.ed.wait_for_event(
                    EventDisplayInfoChanged,
                    ad.ed.is_event_match,
                    timeout=timeout,
                    field=DisplayInfoContainer.OVERRIDE,
                    value=nsa_band)
            ad.log.info("Got expected event %s", event)
            return True
        except Empty:
            ad.log.info("No event for display info change with <%s>", nsa_band)
            ad.screenshot("5g_nsa_icon_checking")
            return False
        finally:
            ad.droid.telephonyStopTrackingServiceStateChangeForSubscription(
                sub_id)

    if mmwave is None:
        return _nsa_display_monitor(
            ad, sub_id, mmwave=False, timeout=timeout) or _nsa_display_monitor(
            ad, sub_id, mmwave=True, timeout=timeout)
    else:
        return _nsa_display_monitor(ad, sub_id, mmwave, timeout)


def is_current_network_5g_sa(ad, sub_id = None, mmwave = None):
    """Verifies 5G SA override network type

    Args:
        ad: android device object.
        sub_id: The target SIM for querying.
        mmwave: True to detect 5G millimeter wave, False to detect sub-6,
            None to detect both.

    Returns:
        True: if data is on 5g SA
        False: if data is not on 5g SA
    """
    sub_id = sub_id if sub_id else ad.droid.subscriptionGetDefaultDataSubId()
    current_rat = ad.droid.telephonyGetCurrentDataNetworkTypeForSubscription(
        sub_id)
    # TODO(richardwychang): check SA MMWAVE when function ready.
    sa_type = ['NR',]
    if mmwave is None:
        if current_rat in sa_type:
            ad.log.debug("Network is currently connected to %s", current_rat)
            return True
        else:
            ad.log.error(
                "Network is currently connected to %s, Expected on %s",
                current_rat, sa_type)
            ad.screenshot("5g_sa_icon_checking")
            return False
    elif mmwave:
        ad.log.error("SA MMWAVE currently not support.")
        return False
    else:
        if current_rat == 'NR':
            ad.log.debug("Network is currently connected to %s", current_rat)
            return True
        else:
            ad.log.error(
                "Network is currently connected to %s, Expected on NR",
                current_rat)
            ad.screenshot("5g_sa_icon_checking")
            return False


def is_current_network_5g(ad, sub_id = None, nr_type = None, mmwave = None,
                          timeout = 30):
    """Verifies 5G override network type

    Args:
        ad: android device object
        sub_id: The target SIM for querying.
        nr_type: 'sa' for 5G standalone, 'nsa' for 5G non-standalone.
        mmwave: True to detect 5G millimeter wave, False to detect sub-6,
            None to detect both.
        timeout: max time to wait for event.

    Returns:
        True: if data is on 5G regardless of SA or NSA
        False: if data is not on 5G refardless of SA or NSA
    """
    sub_id = sub_id if sub_id else ad.droid.subscriptionGetDefaultDataSubId()

    if nr_type == 'nsa':
        return is_current_network_5g_nsa(
            ad, sub_id=sub_id, mmwave=mmwave, timeout=timeout)
    elif nr_type == 'sa':
        return is_current_network_5g_sa(ad, sub_id=sub_id, mmwave=mmwave)
    else:
        return is_current_network_5g_nsa(
            ad, sub_id=sub_id, mmwave=mmwave,
            timeout=timeout) or is_current_network_5g_sa(
                ad, sub_id=sub_id, mmwave=mmwave)
