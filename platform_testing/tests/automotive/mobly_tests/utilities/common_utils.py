#  Copyright (C) 2023 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import logging
import time

from utilities import constants


class CommonUtils:
    """A common utilities for HU and phone device"""

    def __init__(self, target, discoverer):
        self.target = target
        self.discoverer = discoverer

    # Checking is current page has UI element with exact text on HU
    def has_ui_element_with_text(self, ui_element_text_content):
        logging.info('Checking is current page has UI element on HU with text <%s>',
                     ui_element_text_content)
        is_current_page_has_ui_element = self.discoverer.mbs.hasUIElementWithText(
            ui_element_text_content)
        logging.info('Current page on HU has UI element with text: %s',
                     is_current_page_has_ui_element)
        return is_current_page_has_ui_element

    # Click on UI element on HU with exact text
    def click_on_ui_element_with_text(self, ui_element_text_content):
        logging.info('Click on UI element on HU with text <%s>', ui_element_text_content)
        if self.has_ui_element_with_text(ui_element_text_content) is True:
            self.discoverer.mbs.clickUIElementWithText(ui_element_text_content)

    # Wait for specific time
    def wait_with_log(self, wait_time):
        logging.info("Sleep for %s seconds", wait_time)
        time.sleep(wait_time)

    # Enable WIFI on phone device
    def enable_wifi_on_phone_device(self):
        logging.info("Enable WIFI on phone device")
        self.target.mbs.wifiEnable()

    # Grant restricted permission on HU
    def grant_restricted_permission_on_hu(self, permission):
        logging.info('Grant dangerous permission <%s> on HU', permission)
        self.discoverer.mbs.grantRestrictedPermissionsForBTMedia(permission)

    # TODO remove this function after BT Media local mac address issue resolved b/316199227
    # Grant LOCAL_MAC_ADDRESS permission
    def grant_local_mac_address_permission(self):
        logging.info("Grant <LOCAL_MAC_ADDRESS> permission on HU")
        self.grant_restricted_permission_on_hu(constants.LOCAL_MAC_ADDRESS_PERMISSION)
