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
    Test Script for 5G NSA MMWAVE MMS scenarios
"""

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_message_utils import message_test
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_test_utils import set_phone_silent_mode
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode


class Nsa5gMmwMmsTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()
        for ad in self.android_devices:
            set_phone_silent_mode(self.log, ad, True)

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)

    """ Tests Begin """

    @test_tracker_info(uuid="c6f7483f-6007-4a3b-a02d-5e6ab2b9a742")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_mo_mt(self):
        """Test MMS between two phones in 5g NSA MMW

        Provision devices in 5g NSA MMW
        Send and Verify MMS from PhoneA to PhoneB
        Verify both devices are still on 5g NSA MMW

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmwave',
                            mt_rat='5g_nsa_mmwave',
                            msg_type='mms')

    @test_tracker_info(uuid="8e6ed681-d5b8-4503-8262-a16739c66bdb")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_mo_general(self):
        """Test MO MMS for 1 phone in 5g NSA MMW. The other phone in any network

        Provision PhoneA in 5g NSA MMW
        Send and Verify MMS from PhoneA to PhoneB
        Verify phoneA is still on 5g NSA MMW

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmwave',
                            mt_rat='default',
                            msg_type='mms')

    @test_tracker_info(uuid="d22ea7fd-6c07-4eb2-a1bf-10b03cab3201")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_mt_general(self):
        """Test MT MMS for 1 phone in 5g NSA MMW. The other phone in any network

        Provision PhoneA in 5g NSA MMW
        Send and Verify MMS from PhoneB to PhoneA
        Verify phoneA is still on 5g NSA MMW

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_nsa_mmwave',
                            msg_type='mms')

    @test_tracker_info(uuid="897eb961-236d-4b8f-8a84-42f2010c6621")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_mo_volte(self):
        """Test MO MMS for 1 phone with VoLTE on 5G NSA MMW

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA MMW
        Send and Verify MMS from PhoneA to PhoneB
        Verify PhoneA is still on 5g NSA MMW

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmw_volte',
                            mt_rat='default',
                            msg_type='mms')

    @test_tracker_info(uuid="6e185efe-b876-4dcf-9fc2-915039826dbe")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_mt_volte(self):
        """Test MT MMS for 1 phone with VoLTE on 5G NSA MMW

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA MMW
        Send and Verify MMS from PhoneB to PhoneA
        Verify PhoneA is still on 5g NSA MMW

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_nsa_mmw_volte',
                            msg_type='mms')

    @test_tracker_info(uuid="900d9913-b35d-4d75-859b-12bb28a35b73")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_mo_iwlan(self):
        """ Test MO MMS text function for 1 phone in APM,
        WiFi connected, WFC Cell Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA MMW
        Provision PhoneA for WFC Cell Pref with APM ON
        Send and Verify MMS from PhoneA to PhoneB

        Returns:
            True if pass; False if fail.
        """
        apm_mode = [
            toggle_airplane_mode(self.log, ad, False)
            for ad in self.android_devices
        ]
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmw_wfc',
                            mt_rat='default',
                            msg_type='mms',
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_CELLULAR_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="939a1ec5-1004-4527-b11e-eacbcfe0f632")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_mt_iwlan(self):
        """ Test MT MMS text function for 1 phone in APM,
        WiFi connected, WFC Cell Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA MMW
        Provision PhoneA for WFC Cell Pref with APM ON
        Send and Verify MMS from PhoneB to PhoneA

        Returns:
            True if pass; False if fail.
        """
        apm_mode = [
            toggle_airplane_mode(self.log, ad, False)
            for ad in self.android_devices
        ]
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_nsa_mmw_wfc',
                            msg_type='mms',
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_CELLULAR_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="253e4966-dd1c-487b-87fc-85b675140b24")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_mo_iwlan_apm_off(self):
        """ Test MO MMS, Phone in APM off, WiFi connected, WFC WiFi Pref Mode

        Disable APM on both devices
        Provision PhoneA in 5g NSA MMW
        Provision PhoneA for WFC Wifi Pref with APM OFF
        Send and Verify MMS from PhoneA to PhoneB
        Verify 5g NSA MMW attach for PhoneA

        Returns:
            True if pass; False if fail.
        """
        apm_mode = [
            toggle_airplane_mode(self.log, ad, False)
            for ad in self.android_devices
        ]
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmw_wfc',
                            mt_rat='default',
                            msg_type='mms',
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="884435c5-47d8-4db9-b89e-087fc344a8b9")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_mt_iwlan_apm_off(self):
        """ Test MT MMS, Phone in APM off, WiFi connected, WFC WiFi Pref Mode

        Disable APM on both devices
        Provision PhoneA in 5g NSA MMW
        Provision PhoneA for WFC Wifi Pref with APM OFF
        Send and Verify MMS from PhoneB to PhoneA
        Verify 5g NSA MMW attach for PhoneA

        Returns:
            True if pass; False if fail.
        """
        apm_mode = [
            toggle_airplane_mode(self.log, ad, False)
            for ad in self.android_devices
        ]
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_nsa_mmw_wfc',
                            msg_type='mms',
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="d0085f8f-bb18-4801-8bba-c5d2466922f2")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_long_message_mo_mt(self):
        """Test MMS basic function between two phone. Phones in 5G NSA MMW network.

        Airplane mode is off. Phone in 5G NSA MMW.
        Send MMS from PhoneA to PhoneB.
        Verify received message on PhoneB is correct.

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmwave',
                            mt_rat='5g_nsa_mmwave',
                            msg_type='mms',
                            long_msg=True)

    @test_tracker_info(uuid="f43760c6-b040-46ba-9613-fde4192bf2db")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_long_message_mo(self):
        """Test MO long MMS basic function for 1 phone in 5G NSA MMW network.

        Airplane mode is off. PhoneA in 5G NSA MMW.
        Send long MMS from PhoneA to PhoneB.
        Verify received message on PhoneB is correct.

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmwave',
                            mt_rat='default',
                            msg_type='mms',
                            long_msg=True)

    @test_tracker_info(uuid="dc17e5d2-e022-47af-9b21-cf4e11911e17")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_long_message_mt(self):
        """Test MT long MMS basic function for 1 phone in 5G NSA MMW network.

        Airplane mode is off. PhoneA in nsa 5G NSA MMW.
        Send long MMS from PhoneB to PhoneA.
        Verify received message on PhoneA is correct.

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_nsa_mmwave',
                            msg_type='mms',
                            long_msg=True)

    @test_tracker_info(uuid="2a73b511-988c-4a49-857c-5692f6d6cdd6")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_mo_wifi(self):
        """Test MMS basic function between two phone. Phones in nsa 5g network.

        Airplane mode is off. Phone in 5G NSA MMW.
        Connect to Wifi.
        Send MMS from PhoneA to PhoneB.
        Verify received message on PhoneB is correct.

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmwave',
                            mt_rat='general',
                            msg_type='mms',
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="58414ce6-851a-4527-8243-502f5a8cfa7a")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_mms_mt_wifi(self):
        """Test MMS basic function between two phone. Phones in nsa 5g network.

        Airplane mode is off. Phone in 5G NSA MMW.
        Connect to Wifi.
        Send MMS from PhoneB to PhoneA.
        Verify received message on PhoneA is correct.

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='general',
                            mt_rat='5g_nsa_mmwave',
                            msg_type='mms',
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    """ Tests End """
