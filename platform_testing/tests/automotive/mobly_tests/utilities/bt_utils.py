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
import pprint
import time

from utilities import constants
from mobly import asserts
from mobly.controllers import android_device
from utilities.media_utils import MediaUtils

# Number of seconds for the target to stay discoverable on Bluetooth.
DISCOVERABLE_TIME = 60
TIME_FOR_PROMPT_TO_LOAD = 2
ALLOW_TEXT = "Allow"


class BTUtils:
    """A utility that provides access to Bluetooth connectivity controls."""

    def __init__(self, discoverer, target):
        self.discoverer = discoverer
        self.target = target
        self.target_adrr = None
        self.media_utils = MediaUtils(self.target, self.discoverer)

    # Disable Android Auto pop-up on HU
    def disable_android_auto_popup_on_hu(self):
        logging.info('Disable Android Auto pop-up on HU with adb')
        self.media_utils.execute_shell_on_hu_device(constants.DISABLE_ANDROID_AUTO_POP_UP)

    # Skip Assistant pop-up
    # TODO @vitalidim remove this function after b/314386661 resolved
    def handle_assistant_pop_up(self):
        logging.info('Checking for Assistant pop-up on HU')
        if self.discoverer.mbs.isAssistantImprovementPopUpPresent():
            logging.info('Assistant pop-up is present on HU')
            logging.info('Click on <CONTINUE> button on HU')
            self.discoverer.mbs.skipImprovementCallingAndTextingPopUp()
            asserts.assert_false(self.discoverer.mbs.isAssistantImprovementPopUpPresent(),
                                 'Assistant pop-up should be closed')
        else:
            logging.info('Assistant pop-up is not present on HU')

    def get_info_from_devices(self, discovered_devices):
        discovered_names = [device['Name'] for device in discovered_devices]
        discovered_addresses = [device['Address'] for device in discovered_devices]
        return discovered_names, discovered_addresses

    def discover_secondary_from_primary(self):
        target_name = self.target.mbs.btGetName()
        self.target.log.info('Become discoverable with name "%s" for %ds.',
                             target_name, DISCOVERABLE_TIME)
        self.target.mbs.btBecomeDiscoverable(DISCOVERABLE_TIME)
        self.discoverer.log.info('Looking for Bluetooth devices.')
        discovered_devices = self.discoverer.mbs.btDiscoverAndGetResults()
        self.discoverer.log.debug('Found Bluetooth devices: %s',
                                  pprint.pformat(discovered_devices, indent=2))
        discovered_names, _ = self.get_info_from_devices(discovered_devices)
        logging.info('Verifying the target is discovered by the discoverer.')
        asserts.assert_true(
            target_name in discovered_names,
            'Failed to discover the target device %s over Bluetooth.' %
            target_name)

    def pair_primary_to_secondary(self):
        """Enable discovery on the target so the discoverer can find it."""
        # Turn bluetooth on in both machines
        logging.info('Enabling Bluetooth on both devices')
        self.discoverer.mbs.btEnable()
        self.target.mbs.btEnable()
        self.disable_android_auto_popup_on_hu()
        logging.info('Setting devices to be discoverable')
        self.target.mbs.btBecomeDiscoverable(DISCOVERABLE_TIME)
        self.target.mbs.btStartAutoAcceptIncomingPairRequest()
        target_address = self.target.mbs.btGetAddress()
        logging.info('Scanning for discoverable devices')
        # Discovery of target device is tried 5 times.
        discovered_devices = self.discoverer.mbs.btDiscoverAndGetResults()
        self.discoverer.mbs.btPairDevice(target_address)
        logging.info('Allowing time for contacts to sync')
        time.sleep(constants.SYNC_WAIT_TIME)
        self.press_allow_on_phone()
        paired_devices = self.discoverer.mbs.btGetPairedDevices()
        _, paired_addresses = self.get_info_from_devices(paired_devices)
        asserts.assert_true(
            target_address in paired_addresses,
            'Failed to pair the target device %s over Bluetooth.' %
            target_address)
        time.sleep(constants.DEFAULT_WAIT_TIME_FIVE_SECS)
        self.handle_assistant_pop_up()

    def unpair(self):
        # unpair Discoverer device from Target
        logging.info("Unpair Discoverer device from Target")
        discoverer_address = self.discoverer.mbs.btGetAddress()
        logging.info(f"Discoverer device address: {discoverer_address}")
        target_paired_devices = self.target.mbs.btGetPairedDevices()
        _, target_paired_addresses = self.get_info_from_devices(target_paired_devices)
        logging.info(f"Paired devices to Target: {target_paired_devices}")
        if discoverer_address in target_paired_addresses:
            logging.info(f"Forget Discoverer device <{discoverer_address}> on Target device")
            self.target.mbs.btUnpairDevice(discoverer_address)
        else:
            logging.error("Discoverer device not founded on Target device")
        # unpair Target device from Discoverer
        logging.info("Unpair Target device from Discoverer")
        target_address = self.target.mbs.btGetAddress()
        logging.info(f"Target device address: {target_address}")
        discoverer_paired_devices = self.discoverer.mbs.btGetPairedDevices()
        _, discoverer_paired_addresses = self.get_info_from_devices(
            discoverer_paired_devices)
        logging.info(f"Paired devices to Discoverer: {discoverer_paired_devices}")
        if target_address in discoverer_paired_addresses:
            logging.info(f"Forget Target device <{target_address}> on Discoverer device")
            self.discoverer.mbs.btUnpairDevice(target_address)
        else:
            logging.error("Target device not founded on Discoverer device")

    def press_allow_on_phone(self):
        """ Repeatedly presses "Allow" on prompts until no more prompts appear"""
        logging.info('Attempting to press ALLOW')
        while (self.target.mbs.hasUIElementWithText(ALLOW_TEXT)):
            self.target.mbs.clickUIElementWithText(ALLOW_TEXT)
            logging.info('ALLOW pressed!')
            time.sleep(TIME_FOR_PROMPT_TO_LOAD)

    def bt_disable(self):
        """Disable Bluetooth on a device."""
        self.discoverer.mbs.btUnpairDevice(self.target_adrr)
        self.discoverer.mbs.btDisable()
        self.target.mbs.btDisable()

    def click_on_use_bluetooth_toggle(self):
        logging.info('Click on Use Bluetooth toggle on HU')
        self.discoverer.mbs.clickOnBluetoothToggle()
