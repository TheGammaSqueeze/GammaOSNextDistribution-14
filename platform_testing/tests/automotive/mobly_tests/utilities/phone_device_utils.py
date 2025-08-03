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
import re
import time

from mobly.controllers import android_device
from utilities import constants

class PhoneDeviceUtils:
    """Utility for controlling individual mobile device

    This class provides functions that execute generic call sequences.

    """
    def __init__(self, phone_device):
        self.phone_device = phone_device

    def call_number_from_home_screen(self, number):
        """Assumes the phone is on its home screen.
        Opens the phone app, then dial pad, enters the given number, and starts a call"""
        self.phone_device.mbs.pressPhoneIcon()
        self.phone_device.mbs.pressDialpadIcon()
        logging.info("Calling %s from phone device" % number)
        self.phone_device.mbs.enterNumberOnDialpad(number)
        self.phone_device.mbs.pressCallButton()