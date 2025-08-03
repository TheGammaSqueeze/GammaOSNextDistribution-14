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
from acts.libs.utils.multithread import multithread_func
from acts.libs.utils.multithread import run_multithread_func
from acts_contrib.test_utils.net import ui_utils as uutils
from acts.controllers.android_lib.errors import AndroidDeviceError
from acts_contrib.test_utils.tel.tel_logging_utils import log_screen_shot
from acts_contrib.test_utils.tel.tel_logging_utils import get_screen_shot_log
from acts_contrib.test_utils.tel.tel_logging_utils import get_screen_shot_logs

RESOURCE_ID_ENABLE_CHAT_FEATURE = "com.google.android.apps.messaging:id/switchWidget"
RESOURCE_ID_RCS_SETTINGS = "com.google.android.apps.messaging/.ui.appsettings.RcsSettingsActivity"
RESOURCE_ID_START_CHAT = "com.google.android.apps.messaging:id/start_chat_fab"

def go_to_message_app(ad):
    """Launch message app.

    Args:
        ad: android devices

    Returns:
        True if pass; False if fail
    """
    ad.log.info("Launch message settings")
    ad.adb.shell("am start -n com.google.android.apps.messaging/.ui."
        "ConversationListActivity")
    log_screen_shot(ad, "launch_msg_settings")
    if uutils.has_element(ad, resource_id=RESOURCE_ID_START_CHAT):
        return True
    else:
        return False

def go_to_rcs_settings(ad):
    """Goes to RCS settings.

    Args:
        ad: android devices
    Returns:
        True if pass; False if fail
    """
    ad.log.info("Go to chat features settings")
    ad.adb.shell("am start -n com.google.android.apps.messaging/.ui."
        "appsettings.RcsSettingsActivity")
    log_screen_shot(ad, "launch_rcs_settings")
    if uutils.has_element(ad, text="Chat features"):
        return True
    else:
        return False

def is_rcs_enabled(ad):
    """Checks RCS feature is enabled or not.

        Args:
            ad: android devices
        Returns:
            True if RCS is enabled; False if RCS is not enabled
    """
    go_to_rcs_settings(ad)
    if uutils.has_element(ad, text="Status: Connected", timeout=30):
        ad.log.info("RCS is connected")
        return True
    return False


def enable_chat_feature(ad):
    """Enable chat feature.

    Args:
        ad: android devices

    Returns:
        True if pass; False if fail
    """
    if not is_rcs_enabled(ad):
        ad.log.info("Try to enable chat feature")
        go_to_rcs_settings(ad)
        time.sleep(2)
        if uutils.has_element(ad, resource_id=RESOURCE_ID_ENABLE_CHAT_FEATURE):
            uutils.wait_and_click(ad, resource_id=RESOURCE_ID_ENABLE_CHAT_FEATURE,
                matching_node=1)
            ad.log.info("Click on enable chat features")
            time.sleep(2)
            log_screen_shot(ad, "enable_chat_feature")
        if uutils.has_element(ad, text="Status: Connected", timeout=30):
            ad.log.info("RCS status shows connected")
        if uutils.has_element(ad, text="Verify your number"):
            uutils.wait_and_click(ad, text="Verify your number")
            ad.log.info("Click on Verify your number")
            time.sleep(2)
            log_screen_shot(ad, "verify_number")
            if not uutils.has_element(ad, text=ad.phone_number, timeout=30):
                uutils.wait_and_input_text(ad, input_text=ad.phone_number)
                ad.log.info("input phone number %s", ad.phone_number)
                time.sleep(2)
                log_screen_shot(ad, "input_phone_num")
            # click verify now
            if uutils.has_element(ad, text="Verify now"):
                uutils.wait_and_click(ad, text="Verify now")
                ad.log.info("Click verify now")
                time.sleep(2)
                log_screen_shot(ad, "verify_now")
                # wait for RCS to be enabled
                time.sleep(120)
    else:
        ad.log.info("RCS is already enabled")
    if not is_rcs_enabled(ad):
        ad.log.info("RCS is not enabled")
        return False
    return True


def disable_chat_feature(ad):
    """Disable chat feature.

    Args:
        ad: android devices

    Returns:
        True if pass; False if fail
    """
    go_to_rcs_settings(ad)
    time.sleep(2)
    log_screen_shot(ad, "before_disable_chat_feature")
    if uutils.has_element(ad, text="Status: Connected", timeout=30):
        ad.log.info("RCS is connected")
        uutils.wait_and_click(ad, resource_id=RESOURCE_ID_ENABLE_CHAT_FEATURE,
            matching_node=1)
        time.sleep(2)
        ad.log.info("Turn off chat features")
        if uutils.has_element(ad, text="Turn off", timeout=30):
            uutils.wait_and_click(ad, text="Turn off")
            time.sleep(2)
            log_screen_shot(ad, "after_disable_chat_feature")
            return True
    else:
        ad.log.info("RCS is not connected")
    return False

def is_rcs_connected(ad, begin_time=None):
    """search logcat for RCS related message.

    Args:
        ad: android devices
        begin_time: only the lines with time stamps later than begin_time
            will be searched.
    Returns:
        True if found RCS connected message; False if fail
    """
    bugle_log_results = ad.search_logcat('BugleRcsEngine', begin_time)
    ad.log.info('BugleRcsEngine result %s' %bugle_log_results)
    log_results = ad.search_logcat('Enter PublishedState', begin_time)
    ad.log.info('Enter PublishedState result %s' %log_results)
    if log_results:
        ad.log.info("RCS is connected")
        return True
    return False
