#!/usr/bin/env python3.4
#
#   Copyright 2020 - Google
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
    Test Script for 5G MMS scenarios
"""

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_message_utils import message_test
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte
from acts_contrib.test_utils.tel.tel_data_utils import active_file_download_task
from acts_contrib.test_utils.tel.tel_test_utils import install_message_apk
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_device_for_5g
from acts.libs.utils.multithread import run_multithread_func


class Nsa5gMmsTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()

        self.message_util = self.user_params.get("message_apk", None)
        if isinstance(self.message_util, list):
            self.message_util = self.message_util[0]

        if self.message_util:
            ads = self.android_devices
            for ad in ads:
                install_message_apk(ad, self.message_util)

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)

    """ Tests Begin """

    @test_tracker_info(uuid="bc484c2c-8086-42db-94cd-a1e4a35f35cf")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_mt(self):
        """Test MMS between two phones in 5g NSA

        Provision devices in 5g NSA
        Send and Verify MMS from PhoneA to PhoneB
        Verify both devices are still on 5g NSA

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g',
                            mt_rat='5g',
                            msg_type='mms')

    @test_tracker_info(uuid="88bd6658-30fa-41b1-b5d9-0f9dadd83219")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_general(self):
        """Test MO MMS for 1 phone in 5g NSA. The other phone in any network

        Provision PhoneA in 5g NSA
        Send and Verify MMS from PhoneA to PhoneB
        Verify phoneA is still on 5g NSA

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g',
                            mt_rat='default',
                            msg_type='mms')

    @test_tracker_info(uuid="11f2e2c8-bb63-43fa-b279-e7bb32f80596")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mt_general(self):
        """Test MT MMS for 1 phone in 5g NSA. The other phone in any network

        Provision PhoneA in 5g NSA
        Send and Verify MMS from PhoneB to PhoneA
        Verify phoneA is still on 5g NSA

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g',
                            msg_type='mms')

    @test_tracker_info(uuid="51d42104-cb87-4c9b-9a16-302e246a21dc")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_mt_volte(self):
        """Test MMS between two phones with VoLTE on 5G NSA

        Provision devices on VoLTE
        Provision devices in 5g NSA
        Send and Verify MMS from PhoneA to PhoneB
        Verify both devices are still on 5g NSA

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_volte',
                            mt_rat='5g_volte',
                            msg_type='mms')

    @test_tracker_info(uuid="97d6b071-aef2-40c1-8245-7be6c31870a6")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_mt_in_call_volte(self):
        """ Test MO MMS during a VoLTE call over 5G NSA.

        Provision devices on VoLTE
        Provision devices in 5g NSA
        Make a Voice call from PhoneA to PhoneB
        Send and Verify MMS from PhoneA to PhoneB
        Verify both devices are still on 5g NSA

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_volte',
                            mt_rat='5g_volte',
                            msg_type='mms',
                            msg_in_call=True)

    @test_tracker_info(uuid="bbb4b80c-fc1b-4377-b3c7-eeed642c5980")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_mt_iwlan(self):
        """ Test MMS text function between two phones,
        Phones in APM, WiFi connected, WFC Cell Preferred mode.

        Disable APM on both devices
        Provision devices in 5g NSA
        Provision devices for WFC Cell Pref with APM ON
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
                            mo_rat='5g_wfc',
                            mt_rat='5g_wfc',
                            msg_type='mms',
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_CELLULAR_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="d36d95dc-0973-4711-bb08-c29ce23495e4")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_mt_iwlan_apm_off(self):
        """ Test MO MMS, Phone in APM off, WiFi connected, WFC WiFi Pref Mode

        Disable APM on both devices
        Provision devices in 5g NSA
        Provision devices for WFC Wifi Pref with APM OFF
        Send and Verify MMS from PhoneA to PhoneB
        Verify 5g NSA attach for both devices

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
                            mo_rat='5g_wfc',
                            mt_rat='5g_wfc',
                            msg_type='mms',
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="74ffb79e-f1e9-4087-a9d2-e07878e47869")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_mt_in_call_iwlan(self):
        """ Test MO MMS, Phone in APM, WiFi connected, WFC WiFi Pref mode

        Disable APM on both devices
        Provision devices in 5g NSA
        Provision devices for WFC Wifi Pref with APM ON
        Make a Voice call from PhoneA to PhoneB
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
                            mo_rat='5g_wfc',
                            mt_rat='5g_wfc',
                            msg_type='mms',
                            msg_in_call=True,
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="68c8e0ca-bea4-45e4-92cf-19424ee47ca4")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_mt_in_call_volte_wifi(self):
        """ Test MMS during VoLTE call and WiFi connected

        Make sure PhoneA/B are in 5G NSA (with VoLTE).
        Make sure PhoneA/B are able to make/receive call.
        Connect PhoneA/B to Wifi.
        Call from PhoneA to PhoneB, accept on PhoneB, send MMS on PhoneA.
        Make sure PhoneA/B are in 5G NSA.

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_volte',
                            mt_rat='5g_volte',
                            msg_type='mms',
                            msg_in_call=True,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="8c795c3a-59d4-408c-9b99-5287e79ba00b")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_long_message_mo_mt(self):
        """Test MMS basic function between two phone. Phones in nsa 5G network.

        Airplane mode is off. Phone in nsa 5G.
        Send MMS from PhoneA to PhoneB.
        Verify received message on PhoneB is correct.

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g',
                            mt_rat='5g',
                            msg_type='mms',
                            long_msg=True)

    @test_tracker_info(uuid="e09b82ab-69a9-4eae-8cbe-b6f2cff993ad")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_wifi(self):
        """Test MMS basic function between two phone. Phones in nsa 5g network.

        Airplane mode is off. Phone in nsa 5G.
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
                            mo_rat='5g',
                            mt_rat='general',
                            msg_type='mms',
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="fedae24f-2577-4f84-9d76-53bbbe109d48")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mt_wifi(self):
        """Test MMS basic function between two phone. Phones in nsa 5g network.

        Airplane mode is off. Phone in nsa 5G.
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
                            mt_rat='5g',
                            msg_type='mms',
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="156bf832-acc2-4729-a69d-b471cd5cfbde")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_mt_in_call_csfb_wifi(self):
        """ Test MO/MT MMS during a MO csfb call and devices connect to Wifi.

        Disable APM on both devices
        Set up PhoneA/PhoneB are in CSFB mode.
        Provision PhoneA/B in 5g NSA.
        Make sure PhoneA/B is able to make/receive call.
        Connect PhoneA/B to Wifi.
        Call from PhoneA to PhoneB, accept on PhoneB, send MMS on PhoneA,
         receive MMS on B.

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_csfb',
                            mt_rat='5g_csfb',
                            msg_type='mms',
                            msg_in_call=True,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="a76e4adc-ce37-47d4-9925-4ebe175f7b9c")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_volte(self):
        """Test MO MMS for 1 phone with VoLTE on 5G NSA

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA
        Send and Verify MMS from PhoneA to PhoneB
        Verify PhoneA is still on 5g NSA

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_volte',
                            mt_rat='default',
                            msg_type='mms')

    @test_tracker_info(uuid="c2282b01-e89f-49db-8925-79d38b63a373")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mt_volte(self):
        """Test MT MMS for 1 phone with VoLTE on 5G NSA

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA
        Send and Verify MMS from PhoneB to PhoneA
        Verify PhoneA is still on 5g NSA

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_volte',
                            msg_type='mms')

    @test_tracker_info(uuid="fd9bc699-940f-4a4a-abf1-31080e54ab56")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_in_call_volte(self):
        """ Test MO MMS during a VoLTE call over 5G NSA.

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA
        Make a Voice call from PhoneA to PhoneB
        Send and Verify MMS from PhoneA to PhoneB
        Verify PhoneA is still on 5g NSA

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_volte',
                            mt_rat='default',
                            msg_type='mms',
                            msg_in_call=True)

    @test_tracker_info(uuid="cfbae1e0-842a-470a-914a-a3a25a18dc81")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mt_in_call_volte(self):
        """ Test MT MMS during a VoLTE call over 5G NSA.

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA
        Make a Voice call from PhoneB to PhoneA
        Send and Verify MMS from PhoneB to PhoneA
        Verify PhoneA is still on 5g NSA

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_volte',
                            msg_type='mms',
                            msg_in_call=True)

    @test_tracker_info(uuid="fc8a996b-04b5-40e0-be25-cbbabf4d7957")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_iwlan(self):
        """ Test MO MMS text function for 1 phone in APM,
        WiFi connected, WFC Cell Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA
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
                            mo_rat='5g_wfc',
                            mt_rat='default',
                            msg_type='mms',
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_CELLULAR_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="7f354997-38b5-49cd-8bee-12d0589e0380")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mt_iwlan(self):
        """ Test MT MMS text function for 1 phone in APM,
        WiFi connected, WFC Cell Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA
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
                            mt_rat='5g_wfc',
                            msg_type='mms',
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_CELLULAR_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="592ea897-cba1-4ab5-a4ed-54ac1f8d3039")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_iwlan_apm_off(self):
        """ Test MO MMS, Phone in APM off, WiFi connected, WFC WiFi Pref Mode

        Disable APM on both devices
        Provision PhoneA in 5g NSA
        Provision PhoneA for WFC Wifi Pref with APM OFF
        Send and Verify MMS from PhoneA to PhoneB
        Verify 5g NSA attach for PhoneA

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
                            mo_rat='5g_wfc',
                            mt_rat='default',
                            msg_type='mms',
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="3824205d-6a36-420f-a448-51ebb30948c2")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mt_iwlan_apm_off(self):
        """ Test MT MMS, Phone in APM off, WiFi connected, WFC WiFi Pref Mode

        Disable APM on both devices
        Provision PhoneA in 5g NSA
        Provision PhoneA for WFC Wifi Pref with APM OFF
        Send and Verify MMS from PhoneB to PhoneA
        Verify 5g NSA attach for PhoneA

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
                            mt_rat='5g_wfc',
                            msg_type='mms',
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="91da5493-c810-4b1e-84f0-9d292a7b23eb")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_in_call_iwlan(self):
        """ Test MO MMS, Phone in APM, WiFi connected, WFC WiFi Pref mode

        Disable APM on both devices
        Provision PhoneA in 5g NSA
        Provision PhoneA for WFC Wifi Pref with APM ON
        Make a Voice call from PhoneA to PhoneB
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
                            mo_rat='5g_wfc',
                            mt_rat='default',
                            msg_type='mms',
                            msg_in_call=True,
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="3e6a6700-1fcb-4db1-a757-e80801032605")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mt_in_call_iwlan(self):
        """ Test MT MMS, Phone in APM, WiFi connected, WFC WiFi Pref mode

        Disable APM on both devices
        Provision PhoneA in 5g NSA
        Provision PhoneA for WFC Wifi Pref with APM ON
        Make a Voice call from PhoneB to PhoneA
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
                            mt_rat='5g_wfc',
                            msg_type='mms',
                            msg_in_call=True,
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="dc483cc-d7c7-4cdd-9500-4bfc4f1b5bab")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_in_call_volte_wifi(self):
        """ Test MO MMS during VoLTE call and WiFi connected

        Make sure PhoneA is in 5G NSA (with VoLTE).
        Make sure PhoneA is able to make call.
        Connect PhoneA to Wifi.
        Call from PhoneA to PhoneB, accept on PhoneB, send MMS on PhoneA.
        Make sure PhoneA is in 5G NSA.

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_volte',
                            mt_rat='default',
                            msg_type='mms',
                            msg_in_call=True,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="95472ce7-0947-4199-bb6a-8fbb189f3c5c")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mt_in_call_volte_wifi(self):
        """ Test MT MMS during VoLTE call and WiFi connected

        Make sure PhoneA is in 5G NSA (with VoLTE).
        Make sure PhoneA is able to receive call.
        Connect PhoneA to Wifi.
        Call from PhoneB to PhoneA, accept on PhoneA, send MMS on PhoneB.
        Make sure PhoneA is in 5G NSA.

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_volte',
                            msg_type='mms',
                            msg_in_call=True,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="738e2d29-c82d-4a4a-9f4b-e8f8688151ee")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_long_message_mo(self):
        """Test MO long MMS basic function for 1 phone in nsa 5G network.

        Airplane mode is off. PhoneA in nsa 5G.
        Send long MMS from PhoneA to PhoneB.
        Verify received message on PhoneB is correct.

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g',
                            mt_rat='default',
                            msg_type='mms',
                            long_msg=True)

    @test_tracker_info(uuid="68f4f0d6-b798-4d0b-9500-ce49f009b61a")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_long_message_mt(self):
        """Test MT long MMS basic function for 1 phone in nsa 5G network.

        Airplane mode is off. PhoneA in nsa 5G.
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
                            mt_rat='5g',
                            msg_type='mms',
                            long_msg=True)

    @test_tracker_info(uuid="a379fac4-1aa6-46e0-8cef-6d2452702e04")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mo_in_call_csfb_wifi(self):
        """ Test MO MMS during a MO csfb call and device connects to Wifi.

        Disable APM on PhoneA
        Set up PhoneA in CSFB mode.
        Provision PhoneA in 5g NSA.
        Make sure PhoneA is able to make call.
        Connect PhoneA to Wifi.
        Call from PhoneA to PhoneB, accept on PhoneB, send MMS on PhoneA,
         receive MMS on B.

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_csfb',
                            mt_rat='default',
                            msg_type='mms',
                            msg_in_call=True,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="1a6543b1-b7d6-4260-8276-88aee649c4b2")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_mt_in_call_csfb_wifi(self):
        """ Test MT MMS during a MT csfb call and device connects to Wifi.

        Disable APM on PhoneA
        Set up PhoneA is CSFB mode.
        Provision PhoneA in 5g NSA.
        Make sure PhoneA is able to receive call.
        Connect PhoneA to Wifi.
        Call from PhoneB to PhoneA, accept on PhoneA, send MMS on PhoneB,
         receive MMS on A.

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_csfb',
                            msg_type='mms',
                            msg_in_call=True,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="536c8e25-2d72-46a6-89e1-03f70c5a28a3")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_multiple_pdns_mo(self):
        """Test 5G NSA for multiple pdns

        Steps:
            (1) UE supports EN-DC option 3.
            (2) SIM with 5G service.
            (3) UE is provisioned for 5G service and powered off.
            (4) NR cell (Cell 2) that is within the coverage of LTE cell (Cell 1).
            (5) UE is in near cell coverage for LTE (Cell 1) and NR (Cell 2).
            (6) Power on the UE.
            (7) Initiate data transfer while UE is in idle mode.
            (8) During data transferring, send a MO MMS.
            (9) End the data transfer

        Returns:
            True if pass; False if fail.
        """
        cell_1 = self.android_devices[0]
        cell_2 = self.android_devices[1]
        if not phone_setup_volte(self.log, cell_1):
            cell_1.log.error("Failed to setup on VoLTE")
            return False

        if not verify_internet_connection(self.log, cell_1):
            return False
        if not provision_device_for_5g(self.log, cell_2, nr_type='nsa'):
            cell_2.log.error("Failed to setup on 5G NSA")
            return False
        if not verify_internet_connection(self.log, cell_2):
            return False
        if not active_file_download_task(self.log, cell_2):
            return False
        download_task = active_file_download_task(self.log, cell_2, "10MB")
        message_task = (message_test, (self.log, cell_2, cell_1, '5g', 'volte',
                                       'mms'))
        results = run_multithread_func(self.log, [download_task, message_task])

        if ((results[0]) & (results[1])):
            self.log.info(
                "PASS - MO MMS test validated over active data transfer")
        elif ((results[0] == False) & (results[1] == True)):
            self.log.error("FAIL - Data Transfer failed")
        elif ((results[0] == True) & (results[1] == False)):
            self.log.error("FAIL - Sending MMS failed")
        else:
            self.log.error("FAILED - MO MMS test over active data transfer")

        return results

    @test_tracker_info(uuid="10212ab7-a03f-4e11-889e-236b8d1d8afc")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mms_multiple_pdns_mt(self):
        """Test 5G NSA for multiple pdns

        Steps:
            (1) UE supports EN-DC option 3.
            (2) SIM with 5G service.
            (3) UE is provisioned for 5G service and powered off.
            (4) NR cell (Cell 2) that is within the coverage of LTE cell (Cell 1).
            (5) UE is in near cell coverage for LTE (Cell 1) and NR (Cell 2).
            (6) Power on the UE.
            (7) Initiate data transfer while UE is in idle mode.
            (8) During data transferring, send a MT MMS.
            (9) End the data transfer.

        Returns:
            True if pass; False if fail.
        """
        cell_1 = self.android_devices[0]
        cell_2 = self.android_devices[1]

        if not phone_setup_volte(self.log, cell_1):
            cell_1.log.error("Failed to setup on VoLTE")
            return False
        if not verify_internet_connection(self.log, cell_1):
            return False
        if not provision_device_for_5g(self.log, cell_2, nr_type='nsa'):
            cell_2.log.error("Failed to setup on 5G NSA")
            return False
        if not verify_internet_connection(self.log, cell_2):
            return False
        if not active_file_download_task(self.log, cell_2):
            return False

        download_task = active_file_download_task(self.log, cell_2, "10MB")
        message_task = (message_test, (self.log, cell_1, cell_2, 'volte', '5g',
                                       'mms'))
        results = run_multithread_func(self.log, [download_task, message_task])

        if ((results[0]) & (results[1])):
            self.log.info(
                "PASS - MT MMS test validated over active data transfer")
        elif ((results[0] == False) & (results[1] == True)):
            self.log.error("FAIL - Data Transfer failed")
        elif ((results[0] == True) & (results[1] == False)):
            self.log.error("FAIL - Sending MMS failed")
        else:
            self.log.error("FAILED - MT MMS test over active data transfer")

        return results

    """ Tests End """
