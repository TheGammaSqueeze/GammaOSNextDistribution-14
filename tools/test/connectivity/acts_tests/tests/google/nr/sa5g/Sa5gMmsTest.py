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
    Test Script for 5G SA MMS scenarios
"""

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_5g_test_utils import disable_apm_mode_both_devices
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_device_for_5g
from acts_contrib.test_utils.tel.tel_5g_test_utils import verify_5g_attach_for_both_devices
from acts_contrib.test_utils.tel.tel_mms_utils import _mms_test_mo
from acts_contrib.test_utils.tel.tel_mms_utils import _long_mms_test_mo
from acts_contrib.test_utils.tel.tel_wifi_utils import ensure_wifi_connected


class Sa5gMmsTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()
        self.number_of_devices = 2

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)

    """ Tests Begin """

    @test_tracker_info(uuid="74e2ae79-aee4-46e0-9326-fcd3b7f19128")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_sa_mms_mo_mt(self):
        """Test MMS between two phones in 5g SA

        Provision devices in 5g SA
        Send and Verify MMS from PhoneA to PhoneB
        Verify both devices are still on 5g SA

        Returns:
            True if success.
            False if failed.
        """
        ads = self.android_devices
        if not provision_device_for_5g(self.log, ads, nr_type='sa'):
            return False

        if not _mms_test_mo(self.log, ads):
            return False

        if not verify_5g_attach_for_both_devices(self.log, ads, nr_type='sa'):
            return False

        self.log.info("PASS - mms test over 5g sa validated")
        return True

    @test_tracker_info(uuid="6cd173f5-bd1d-44bb-aac2-ac63f37b9a62")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_sa_mms_long_message_mo_mt(self):
        """Test MMS basic function between two phone. Phones in sa 5G network.

        Airplane mode is off. Phone in 5G SA.
        Send MMS from PhoneA to PhoneB.
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

        return _long_mms_test_mo(self.log, ads)

    @test_tracker_info(uuid="83d24fb5-1ebd-42e0-a3d1-b85b607234e2")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_sa_mms_mo_mt_wifi(self):
        """Test MMS basic function between two phone. Phones in sa 5g network.

        Airplane mode is off. Phone in sa 5G.
        Connect to Wifi.
        Send MMS from PhoneA to PhoneB.
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

        ensure_wifi_connected(self.log, ads[0], self.wifi_network_ssid,
                              self.wifi_network_pass)

        return _mms_test_mo(self.log, ads)

    """ Tests End """
