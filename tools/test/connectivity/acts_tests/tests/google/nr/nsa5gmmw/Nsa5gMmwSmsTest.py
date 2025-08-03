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
    Test Script for 5G NSA MMWAVE SMS scenarios
"""

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_message_utils import message_test
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_test_utils import set_phone_silent_mode
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode


class Nsa5gMmwSmsTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()
        for ad in self.android_devices:
            set_phone_silent_mode(self.log, ad, True)

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)

    """ Tests Begin """

    @test_tracker_info(uuid="fb333cd5-2eaa-4d63-be26-fdf1e67d01b0")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mo_mt(self):
        """Test SMS between two phones in 5g NSA MMW

        Provision devices in 5g NSA MMW
        Send and Verify SMS from PhoneA to PhoneB
        Verify both devices are still on 5g NSA MMW

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmwave',
                            mt_rat='5g_nsa_mmwave')

    @test_tracker_info(uuid="3afc92e8-69f7-4ead-a416-4df9753da27a")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mo_general(self):
        """Test MO SMS for 1 phone in 5g NSA MMW. The other phone in any network

        Provision PhoneA in 5g NSA MMW
        Send and Verify SMS from PhoneA to PhoneB
        Verify phoneA is still on 5g NSA MMW

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmwave',
                            mt_rat='default')

    @test_tracker_info(uuid="ee57da72-8e30-42ad-a7b3-d05bb4762724")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mt_general(self):
        """Test MT SMS for 1 phone in 5g NSA MMW. The other phone in any network

        Provision PhoneB in 5g NSA MMW
        Send and Verify SMS from PhoneB to PhoneA
        Verify phoneA is still on 5g NSA MMW

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_nsa_mmwave')

    @test_tracker_info(uuid="1f75e117-f0f5-45fe-8896-91e0d2e61e9c")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mo_mt_volte(self):
        """Test SMS between two phones with VoLTE on 5g NSA MMW

        Provision devices on VoLTE
        Provision devices in 5g NSA MMW
        Send and Verify SMS from PhoneA to PhoneB
        Verify both devices are still on 5g NSA MMW

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmw_volte',
                            mt_rat='5g_nsa_mmw_volte')

    @test_tracker_info(uuid="f58fe4ed-77e0-40ff-8599-27d95cb27e14")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mo_volte(self):
        """Test MO SMS with VoLTE on 5g NSA MMW. The other phone in any network

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA MMW
        Send and Verify SMS from PhoneA to PhoneB
        Verify PhoneA is still on 5g NSA MMW

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmw_volte',
                            mt_rat='default')

    @test_tracker_info(uuid="f60ac2b0-0feb-441e-9048-fe1b2878f8b6")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mt_volte(self):
        """Test MT SMS with VoLTE on 5g NSA MMW. The other phone in any network

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA MMW
        Send and Verify SMS from PhoneB to PhoneA
        Verify phoneA is still on 5g NSA MMW

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_nsa_mmw_volte')

    @test_tracker_info(uuid="6b27d804-abcd-4558-894d-545428a5dff4")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mo_iwlan(self):
        """ Test MO SMS for 1 phone in APM,
        WiFi connected, WFC Cell Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA MMW
        Provision PhoneA for WFC Cell Pref with APM ON
        Send and Verify SMS from PhoneA to PhoneB

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
                            mt_rat='general',
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_CELLULAR_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="0b848508-a1e8-4652-9e13-74749a7ccd2e")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mt_iwlan(self):
        """ Test MT SMS for 1 phone in APM,
        WiFi connected, WFC Cell Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA MMW
        Provision PhoneA for WFC Cell Pref with APM ON
        Send and Verify SMS from PhoneB to PhoneA

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
                            mo_rat='general',
                            mt_rat='5g_nsa_mmw_wfc',
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_CELLULAR_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="9fc07594-6dbf-4b7a-b5a5-f4c06032fa35")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mo_iwlan_apm_off(self):
        """ Test MO SMS for 1 Phone in APM off, WiFi connected,
        WFC WiFi Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA MMW
        Provision PhoneA for WFC Wifi Pref with APM OFF
        Send and Verify SMS from PhoneA to PhoneB
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
                            mt_rat='general',
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="b76c0eaf-6e6b-4da7-87a0-26895f93a554")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mt_iwlan_apm_off(self):
        """ Test MT SMS for 1 Phone in APM off, WiFi connected,
        WFC WiFi Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA MMW
        Provision PhoneA for WFC Wifi Pref with APM OFF
        Send and Verify SMS from PhoneB to PhoneA
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
                            mo_rat='general',
                            mt_rat='5g_nsa_mmw_wfc',
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="43694343-e6f0-4430-972f-53f61c7b51b0")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_long_message_mo_mt(self):
        """Test SMS basic function between two phone. Phones in 5G NSA MMW network.

        Airplane mode is off.
        Send SMS from PhoneA to PhoneB.
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
                            long_msg=True)

    @test_tracker_info(uuid="846dcf2d-911f-46a0-adb1-e32667b8ebd3")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_long_message_mo(self):
        """Test MO long SMS function for 1 phone in 5G NSA MMW network.

        Disable APM on PhoneA
        Provision PhoneA in 5g NSA MMW
        Send SMS from PhoneA to PhoneB
        Verify received message on PhoneB is correct

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmwave',
                            mt_rat='default',
                            long_msg=True)

    @test_tracker_info(uuid="4d2951c3-d80c-4860-8dd9-9709cb7dfaa8")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_long_message_mt(self):
        """Test MT long SMS function for 1 phone in 5G NSA MMW network.

        Disable APM on PhoneA
        Provision PhoneA in 5g NSA MMW
        Send SMS from PhoneB to PhoneA
        Verify received message on PhoneA is correct

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_nsa_mmwave',
                            long_msg=True)

    @test_tracker_info(uuid="bee8b263-fe61-4a5c-a857-eb849426f0c7")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mo_mt_in_call_volte(self):
        """ Test MO SMS during a MO VoLTE call over 5G NSA MMW.

        Provision devices on VoLTE
        Provision devices in 5g NSA MMW
        Make a Voice call from PhoneA to PhoneB
        Send and Verify SMS from PhoneA to PhoneB
        Verify both devices are still on 5g NSA

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmw_volte',
                            mt_rat='5g_nsa_mmw_volte',
                            msg_in_call=True)

    @test_tracker_info(uuid="1a5ca91a-91f6-4b5d-b4b9-f436bb3870ff")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mo_in_call_volte(self):
        """ Test MO SMS during a MO VoLTE call over 5G NSA MMW.

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA MMW
        Make a Voice call from PhoneA to PhoneB
        Send and Verify SMS from PhoneA to PhoneB
        Verify phoneA is still on 5g NSA

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_nsa_mmw_volte',
                            mt_rat='default',
                            msg_in_call=True)

    @test_tracker_info(uuid="8a334ce7-a431-4fdc-bd65-f094d5ae727b")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mt_in_call_volte(self):
        """ Test MT SMS during a MT VoLTE call over 5G NSA.

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA MMW
        Make a Voice call from PhoneB to PhoneA
        Send and Verify SMS from PhoneB to PhoneA
        Verify phoneA is still on 5g NSA

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_nsa_mmw_volte',
                            msg_in_call=True)

    @test_tracker_info(uuid="ff29b86c-df34-4c6e-b880-baa321442fcf")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mo_mt_iwlan(self):
        """ Test SMS text function between two phones,
        Phones in APM, WiFi connected, WFC Cell Preferred mode.

        Disable APM on both devices
        Provision devices in 5g NSA MMW
        Provision devices for WFC Cell Pref with APM ON
        Send and Verify SMS from PhoneA to PhoneB

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
                            mt_rat='5g_nsa_mmw_wfc',
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_CELLULAR_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="010a85d9-84d5-4893-aa43-478490bbf03c")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mo_mt_iwlan_apm_off(self):
        """ Test MO SMS, Phone in APM off, WiFi connected, WFC WiFi Preferred mode.

        Disable APM on both devices
        Provision devices in 5g NSA MMW
        Provision devices for WFC Wifi Pref with APM OFF
        Send and Verify SMS from PhoneA to PhoneB
        Verify 5g NSA MMW attach for both devices

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
                            mt_rat='5g_nsa_mmw_wfc',
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="40c11bd3-e0a2-46f9-b7f0-ccb8366e85b7")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_sms_mo_mt_in_call_iwlan(self):
        """ Test MO SMS, Phone in APM, WiFi connected, WFC WiFi Preferred mode.

        Disable APM on both devices
        Provision devices in 5g NSA MMW
        Provision devices for WFC Wifi Pref with APM ON
        Make a Voice call from PhoneA to PhoneB
        Send and Verify SMS from PhoneA to PhoneB

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
                            mt_rat='5g_nsa_mmw_wfc',
                            msg_in_call=True,
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    """ Tests End """
