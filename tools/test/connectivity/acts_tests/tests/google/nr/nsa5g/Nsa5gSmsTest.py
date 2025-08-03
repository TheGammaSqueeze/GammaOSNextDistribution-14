#!/usr/bin/env python3.4
#
#   Copyright 2022 - Google
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
    Test Script for 5G SMS scenarios
"""

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_device_for_5g
from acts_contrib.test_utils.tel.tel_data_utils import active_file_download_task
from acts_contrib.test_utils.tel.tel_message_utils import message_test
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte
from acts_contrib.test_utils.tel.tel_test_utils import install_message_apk
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts.libs.utils.multithread import run_multithread_func


class Nsa5gSmsTest(TelephonyBaseTest):
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

    @test_tracker_info(uuid="4a64a262-7433-4a7f-b5c6-a36ff60aeaa2")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_mt(self):
        """Test SMS between two phones in 5g NSA

        Provision devices in 5g NSA
        Send and Verify SMS from PhoneA to PhoneB
        Verify both devices are still on 5g NSA

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g',
                            mt_rat='5g')

    @test_tracker_info(uuid="52b16764-0c9e-45c0-910f-a39d17c7cf7e")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_general(self):
        """Test MO SMS for 1 phone in 5g NSA. The other phone in any network

        Provision PhoneA in 5g NSA
        Send and Verify SMS from PhoneA to PhoneB
        Verify phoneA is still on 5g NSA

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g',
                            mt_rat='default')

    @test_tracker_info(uuid="e9b2494a-0e40-449c-b877-1e4ddc78c536")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mt_general(self):
        """Test MT SMS for 1 phone in 5g NSA. The other phone in any network

        Provision PhoneA in 5g NSA
        Send and Verify SMS from PhoneB to PhoneA
        Verify phoneA is still on 5g NSA

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g')

    @test_tracker_info(uuid="2ce809d4-cbf6-4233-81ad-43f91107b201")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_mt_volte(self):
        """Test SMS between two phones with VoLTE on 5G NSA

        Provision devices on VoLTE
        Provision devices in 5g NSA
        Send and Verify SMS from PhoneA to PhoneB
        Verify both devices are still on 5g NSA

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_volte',
                            mt_rat='5g_volte')

    @test_tracker_info(uuid="e51f3dbb-bb16-4400-b2be-f9422f511087")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_volte(self):
        """Test MO SMS with VoLTE on 5G NSA. The other phone in any network

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA
        Send and Verify SMS from PhoneA to PhoneB
        Verify PhoneA is still on 5g NSA

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_volte',
                            mt_rat='default')

    @test_tracker_info(uuid="5217d427-04a2-4b2b-9ed8-28951e71fc21")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mt_volte(self):
        """Test MT SMS with VoLTE on 5G NSA. The other phone in any network

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA
        Send and Verify SMS from PhoneB to PhoneA
        Verify phoneA is still on 5g NSA

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_volte')

    @test_tracker_info(uuid="49bfb4b3-a6ec-45d4-ad96-09282fb07d1d")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_mt_in_call_volte(self):
        """ Test MO SMS during a MO VoLTE call over 5G NSA.

        Provision devices on VoLTE
        Provision devices in 5g NSA
        Make a Voice call from PhoneA to PhoneB
        Send and Verify SMS from PhoneA to PhoneB
        Verify both devices are still on 5g NSA

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_volte',
                            mt_rat='5g_volte',
                            msg_in_call=True)

    @test_tracker_info(uuid="3d5c8f60-1eaa-4f4a-b539-c529fa36db91")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_in_call_volte(self):
        """ Test MO SMS during a MO VoLTE call over 5G NSA.

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA
        Make a Voice call from PhoneA to PhoneB
        Send and Verify SMS from PhoneA to PhoneB
        Verify phoneA is still on 5g NSA

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_volte',
                            mt_rat='default',
                            msg_in_call=True)

    @test_tracker_info(uuid="c71813f3-bb04-4115-8519-e23046349689")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mt_in_call_volte(self):
        """ Test MT SMS during a MT VoLTE call over 5G NSA.

        Provision PhoneA on VoLTE
        Provision PhoneA in 5g NSA
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
                            mt_rat='5g_volte',
                            msg_in_call=True)

    @test_tracker_info(uuid="1f914d5c-ac24-4794-9fcb-cb28e483d69a")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_mt_iwlan(self):
        """ Test SMS text function between two phones,
        Phones in APM, WiFi connected, WFC Cell Preferred mode.

        Disable APM on both devices
        Provision devices in 5g NSA
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
                            mo_rat='5g_wfc',
                            mt_rat='5g_wfc',
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_CELLULAR_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="2d375f20-a785-42e0-b5a1-968d19bc693d")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_iwlan(self):
        """ Test MO SMS for 1 phone in APM,
        WiFi connected, WFC Cell Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA
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
                            mo_rat='5g_wfc',
                            mt_rat='general',
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_CELLULAR_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="db8b2b5b-bf9e-4a99-9fdb-dbd028567705")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mt_iwlan(self):
        """ Test MT SMS for 1 phone in APM,
        WiFi connected, WFC Cell Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA
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
                            mt_rat='5g_wfc',
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_CELLULAR_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="7274be32-b9dd-4ce3-83d1-f32ab14ce05e")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_mt_iwlan_apm_off(self):
        """ Test MO SMS, Phone in APM off, WiFi connected, WFC WiFi Preferred mode.

        Disable APM on both devices
        Provision devices in 5g NSA
        Provision devices for WFC Wifi Pref with APM OFF
        Send and Verify SMS from PhoneA to PhoneB
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
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="5997a618-efee-478f-8fa9-6cf8ba9cfc58")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_iwlan_apm_off(self):
        """ Test MO SMS for 1 Phone in APM off, WiFi connected,
        WFC WiFi Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA
        Provision PhoneA for WFC Wifi Pref with APM OFF
        Send and Verify SMS from PhoneA to PhoneB
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
                            mt_rat='general',
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="352ca023-2cd1-4b08-877c-20c5d50cc265")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mt_iwlan_apm_off(self):
        """ Test MT SMS for 1 Phone in APM off, WiFi connected,
        WFC WiFi Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA
        Provision PhoneA for WFC Wifi Pref with APM OFF
        Send and Verify SMS from PhoneB to PhoneA
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
                            mo_rat='general',
                            mt_rat='5g_wfc',
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="2d1787f2-d6fe-4b41-b389-2a8f817594e4")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_mt_in_call_iwlan(self):
        """ Test MO SMS, Phone in APM, WiFi connected, WFC WiFi Preferred mode.

        Disable APM on both devices
        Provision devices in 5g NSA
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
                            mo_rat='5g_wfc',
                            mt_rat='5g_wfc',
                            msg_in_call=True,
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="784062e8-02a4-49ce-8fc1-5359ab40bbdd")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_long_message_mo_mt(self):
        """Test SMS basic function between two phone. Phones in nsa 5G network.

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
                            mo_rat='5g',
                            mt_rat='5g',
                            long_msg=True)

    @test_tracker_info(uuid="45dbd61a-6a90-473e-9cfa-03e2408d5f15")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_mt_in_call_csfb(self):
        """ Test MO/MT SMS during a MO csfb call over 5G NSA.

        Disable APM on both devices
        Set up PhoneA/B are in CSFB mode.
        Provision PhoneA/B in 5g NSA.
        Make sure PhoneA/B is able to make/receive call.
        Call from PhoneA to PhoneB, accept on PhoneB, send SMS on PhoneA,
         receive SMS on PhoneB.

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_csfb',
                            mt_rat='5g_csfb',
                            msg_in_call=True)

    @test_tracker_info(uuid="709d5322-3da3-4c77-9180-281bc54ad78e")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_in_call_iwlan(self):
        """ Test MO SMS for 1 Phone in APM, WiFi connected,
        WFC WiFi Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA
        Provision PhoneA for WFC Wifi Pref with APM ON
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
                            mo_rat='5g_wfc',
                            mt_rat='default',
                            msg_in_call=True,
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="6af38572-bbf7-4c11-8f0c-ab2f9b25ac49")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mt_in_call_iwlan(self):
        """ Test MT SMS for 1 Phone in APM, WiFi connected,
        WFC WiFi Preferred mode.

        Disable APM on both devices
        Provision PhoneA in 5g NSA
        Provision PhoneA for WFC Wifi Pref with APM ON
        Make a Voice call from PhoneB to PhoneA
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
                            mo_rat='default',
                            mt_rat='5g_wfc',
                            msg_in_call=True,
                            is_airplane_mode=True,
                            wfc_mode=WFC_MODE_WIFI_PREFERRED,
                            wifi_ssid=self.wifi_network_ssid,
                            wifi_pwd=self.wifi_network_pass)

    @test_tracker_info(uuid="1437adb8-dfb0-49fb-8ecc-b456f60d7f64")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_long_message_mo(self):
        """Test MO long SMS function for 1 phone in nsa 5G network.

        Disable APM on PhoneA
        Provision PhoneA in 5g NSA
        Send SMS from PhoneA to PhoneB
        Verify received message on PhoneB is correct

        Returns:
            True if success.
            False if failed.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g',
                            mt_rat='default',
                            long_msg=True)

    @test_tracker_info(uuid="d34a4840-d1fa-46f1-885b-f67456225f50")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_long_message_mt(self):
        """Test MT long SMS function for 1 phone in nsa 5G network.

        Disable APM on PhoneA
        Provision PhoneA in 5g NSA
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
                            mt_rat='5g',
                            long_msg=True)

    @test_tracker_info(uuid="84e40f15-1d02-44b0-8103-f25f73dae7a1")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mo_in_call_csfb(self):
        """ Test MO SMS during a MO csfb call over 5G NSA.

        Disable APM on PhoneA
        Set up PhoneA are in CSFB mode.
        Provision PhoneA in 5g NSA.
        Make sure PhoneA is able to make call.
        Call from PhoneA to PhoneB, accept on PhoneB, send SMS on PhoneA,
         receive SMS on PhoneB.

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[0],
                            self.android_devices[1],
                            mo_rat='5g_csfb',
                            mt_rat='default',
                            msg_in_call=True)

    @test_tracker_info(uuid="259ccd94-2d70-450e-adf4-949889096cce")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_mt_in_call_csfb(self):
        """ Test MT SMS during a MT csfb call over 5G NSA.

        Disable APM on PhoneA
        Set up PhoneA are in CSFB mode.
        Provision PhoneA in 5g NSA.
        Make sure PhoneA is able to receive call.
        Call from PhoneB to PhoneA, accept on PhoneA, send SMS on PhoneB,
         receive SMS on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        return message_test(self.log,
                            self.android_devices[1],
                            self.android_devices[0],
                            mo_rat='default',
                            mt_rat='5g_csfb',
                            msg_in_call=True)

    @test_tracker_info(uuid="303d5c2f-15bd-4608-96b8-37d16341004e")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_multiple_pdns_mo(self):
        """Test 5G NSA for multiple pdns

        Steps:
            (1) UE supports EN-DC option 3.
            (2) SIM with 5G service.
            (3) UE is provisioned for 5G service and powered off.
            (4) NR cell (Cell 2) that is within the coverage of LTE cell (Cell 1).
            (5) UE is in near cell coverage for LTE (Cell 1) and NR (Cell 2).
            (6) Power on the UE.
            (7) Initiate data transfer while UE is in idle mode.
            (8) During data transferring, send a MO SMS.
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
                                       'sms'))
        results = run_multithread_func(self.log, [download_task, message_task])

        if ((results[0]) & (results[1])):
            self.log.info(
                "PASS - MO SMS test validated over active data transfer")
        elif ((results[0] == False) & (results[1] == True)):
            self.log.error("FAIL - Data Transfer failed")
        elif ((results[0] == True) & (results[1] == False)):
            self.log.error("FAIL - Sending SMS failed")
        else:
            self.log.error("FAILED - MO SMS test over active data transfer")

        return results

    @test_tracker_info(uuid="cc9d2b46-80cc-47a8-926b-3ccf8095cefb")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_sms_multiple_pdns_mt(self):
        """Test 5G NSA for multiple pdns

        Steps:
	    (1) UE supports EN-DC option 3.
	    (2) SIM with 5G service.
	    (3) UE is provisioned for 5G service and powered off.
	    (4) NR cell (Cell 2) that is within the coverage of LTE cell (Cell 1).
	    (5) UE is in near cell coverage for LTE (Cell 1) and NR (Cell 2).
	    (6) Power on the UE.
	    (7) Initiate data transfer while UE is in idle mode.
	    (8) During data transferring, send a MT SMS.
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
                                       'sms'))
        results = run_multithread_func(self.log, [download_task, message_task])

        if ((results[0]) & (results[1])):
            self.log.info(
                "PASS - MT SMS test validated over active data transfer")
        elif ((results[0] == False) & (results[1] == True)):
            self.log.error("FAIL - Data Transfer failed")
        elif ((results[0] == True) & (results[1] == False)):
            self.log.error("FAIL - Sending SMS failed")
        else:
            self.log.error("FAILED - MT SMS test over active data transfer")

        return results

    """ Tests End """
