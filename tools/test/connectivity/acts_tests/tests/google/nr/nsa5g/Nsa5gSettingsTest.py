#!/usr/bin/env python3.4
#
#   Copyright 2022 - Google
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
'''
    Test Script for Telephony Settings on nsa 5G
'''

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import GEN_5G
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_settings_utils import att_apn_test
from acts_contrib.test_utils.tel.tel_settings_utils import tmo_apn_test
from acts_contrib.test_utils.tel.tel_settings_utils import toggle_mobile_data_test
from acts_contrib.test_utils.tel.tel_settings_utils import toggle_sim_test


class Nsa5gSettingsTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()
        self.number_of_devices = 1

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)

    """ Tests Begin """

    @test_tracker_info(uuid='57debc2d-ca17-4363-8d03-9bc068fdc624')
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_disable_enable_sim(self):
        """Test sim disable and enable

        Steps:
            1. Provision device to nsa 5G
            2. Launch Settings - Network & Internet
            3. Click on SIMs
            4. Toggle Use SIM switch to Disable
            5. Verify Use SIM switch is disabled
            6. Toggle Use SIM switch to Enable
            7. Verify Use SIM switch is Enabled
            8. Verify SIM is connected to nsa 5G

        Returns:
            True is tests passes else False
        """
        ad = self.android_devices[0]
        return toggle_sim_test(ad, GEN_5G, 'nsa')

    @test_tracker_info(uuid='7233780b-eabf-4bb6-ae96-3574d0cd4fa2')
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_disable_enable_mobile_data(self):
        """Test sim disable and enable

        Steps:
            1. Provision device to nsa 5G
            2. Launch Settings - Network & Internet
            3. Click on SIMs
            4. Toggle Mobile Data switch to Disable
            5. Verify Mobile Data switch is disabled
            6. Toggle Mobile Data switch to Enable
            7. Verify Mobile Data switch is Enabled
            8. Verify Mobile Data is connected to nsa 5G

        Returns:
            True is tests passes else False
        """
        ad = self.android_devices[0]

        return toggle_mobile_data_test(ad, GEN_5G, 'nsa')

    @test_tracker_info(uuid='42e45721-0052-4bcc-8a10-2686dfb86648')
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_apn_settings_att_sms(self):
        """Test ATT APN and SMS

        Steps:
            1. Provision device to nsa 5G
            2. Launch Settings - Network & Internet
            3. Click on SIMs
            4. Click on Access Point Names
            5. Add New APN
            6. Save New APN
            7. Switch APN to New APN
            8. Check Network is connected to nsa 5G
            9. Send SMS

        Returns:
            True is tests passes else False
        """
        caller, callee = self.android_devices[0], self.android_devices[1]

        return att_apn_test(self.log, caller, callee, GEN_5G, nr_type='nsa', msg_type='sms')

    @test_tracker_info(uuid='5a295807-c5cb-4a5a-ad25-e44d4a16cfe6')
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_apn_settings_att_mms(self):
        """Test ATT APN and MMS

        Steps:
            1. Provision device to nsa 5G
            2. Launch Settings - Network & Internet
            3. Click on SIMs
            4. Click on Access Point Names
            5. Add New APN
            6. Add ATT APN details and Save
            7. Switch APN to New APN
            8. Check Network is connected to nsa 5G
            9. Send MMS

        Returns:
            True is tests passes else False
        """
        caller, callee = self.android_devices[0], self.android_devices[1]

        return att_apn_test(self.log, caller, callee, GEN_5G, nr_type='nsa', msg_type='mms')

    @test_tracker_info(uuid='952b3861-0516-42e6-a221-23934bdab13c')
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_apn_settings_tmo_sms(self):
        """Test TMO APN and SMS

        Steps:
            1. Provision device to nsa 5G
            2. Launch Settings - Network & Internet
            3. Click on SIMs
            4. Click on Access Point Names
            5. Add New APN
            6. Save New APN
            7. Switch APN to New APN
            8. Check Network is connected to nsa 5G
            9. Send SMS

        Returns:
            True is tests passes else False
        """
        caller, callee = self.android_devices[0], self.android_devices[1]

        return tmo_apn_test(self.log, caller, callee, GEN_5G, nr_type='nsa', msg_type='sms')

    @test_tracker_info(uuid='62d66d9e-b7de-419b-b079-9d0112cb28dc')
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_apn_settings_tmo_mms(self):
        """Test TMO APN and MMS

        Steps:
            1. Provision device to nsa 5G
            2. Launch Settings - Network & Internet
            3. Click on SIMs
            4. Click on Access Point Names
            5. Add New APN
            6. Add ATT APN details and Save
            7. Switch APN to New APN
            8. Check Network is connected to nsa 5G
            9. Send MMS

        Returns:
            True is tests passes else False
        """
        caller, callee = self.android_devices[0], self.android_devices[1]

        return tmo_apn_test(self.log, caller, callee, GEN_5G, nr_type='nsa', msg_type='mms')
