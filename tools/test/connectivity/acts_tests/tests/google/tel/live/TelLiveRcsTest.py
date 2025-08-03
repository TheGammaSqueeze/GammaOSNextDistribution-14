#!/usr/bin/env python3.4
#
#   Copyright 2016 - Google
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
"""
    Test Script for RCS.
"""
from time import sleep

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest


class TelLiveRcsTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)

    def teardown_class(self):
        TelephonyBaseTest.teardown_class(self)

    def test_verify_provisioning(self):
        ad = self.android_devices[0]
        ad.log.info("Start RCS provisioning")
        ad.droid.startRCSProvisioning("UP_1.0", "6.0", "Goog", "RCSAndrd-1.0")
        sleep(20)
        isRcsVolteSingleRegistrationCapable = ad.droid.isRcsVolteSingleRegistrationCapable()
        configXml = ad.droid.getRCSConfigXml()
        ad.log.info("isRcsVolteSingleRegistrationCapable: %r", isRcsVolteSingleRegistrationCapable)
        ad.log.info("RCS Config XML: %s", configXml)
        result = configXml.find("<parm name=\"rcsVolteSingleRegistration\" value=\"1\"/>")
        return result != -1

    def test_is_single_reg_capable(self, ad):
        """ Test single registration provisioning.

        """

        return ad.droid.isRcsVolteSingleRegistrationCapable()

    def test_unregister(self):
        ad = self.android_devices[0]
        return ad.droid.unregister()

