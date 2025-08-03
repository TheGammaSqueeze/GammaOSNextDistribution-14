#!/usr/bin/env python3.4
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
"""
    Test Script for 5G SA SMS scenarios
"""

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_5g_test_utils import disable_apm_mode_both_devices
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_device_for_5g
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_both_devices_for_volte
from acts_contrib.test_utils.tel.tel_5g_test_utils import verify_5g_attach_for_both_devices
from acts_contrib.test_utils.tel.tel_sms_utils import _sms_test_mo
from acts_contrib.test_utils.tel.tel_sms_utils import _long_sms_test_mo


class Sa5gSmsTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)

    """ Tests Begin """

    @test_tracker_info(uuid="8949d1c7-1719-4960-b79c-041b467fb5ef")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_sa_sms_mo_mt(self):
        """Test SMS between two phones in 5g SA

        Provision devices in 5g SA
        Send and Verify SMS from PhoneA to PhoneB
        Verify both devices are still on 5g SA

        Returns:
            True if success.
            False if failed.
        """
        ads = self.android_devices
        if not provision_device_for_5g(self.log, ads, nr_type='sa'):
            return False

        if not _sms_test_mo(self.log, ads):
            return False

        if not verify_5g_attach_for_both_devices(self.log, ads, nr_type='sa'):
            return False

        self.log.info("PASS - SMS test over 5G SA validated")
        return True

    @test_tracker_info(uuid="5c7a717b-1f98-44b7-95e7-0e83afb82a84")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_sa_sms_long_message_mo_mt(self):
        """Test SMS basic function between two phone.

        Phones in sa 5G network.
        Airplane mode is off.
        Send SMS from PhoneA to PhoneB.
        Verify received message on PhoneB is correct.

        Returns:
            True if success.
            False if failed.
        """

        ads = self.android_devices

        if not disable_apm_mode_both_devices(self.log, ads):
            return False

        if not provision_device_for_5g(self.log, ads, nr_type='sa'):
            return False

        return _long_sms_test_mo(self.log, ads)

    """ Tests End """
