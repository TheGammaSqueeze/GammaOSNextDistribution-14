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
    Test Script for 5G IMS Settings scenarios
"""

from acts import signals
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import CarrierConfigs
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_VOLTE
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_WFC
from acts_contrib.test_utils.tel.tel_defines import RAT_NR
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_on_rat
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_voice_sub_id
from acts_contrib.test_utils.tel.tel_test_utils import dumpsys_carrier_config
from acts_contrib.test_utils.tel.tel_voice_utils import change_ims_setting


class Nsa5gImsSettingsTest(TelephonyBaseTest):

    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        self.dut = self.android_devices[0]
        self.dut_client = self.android_devices[1]
        self.skip_reset_between_cases = False
        self.subid = get_outgoing_voice_sub_id(self.dut)
        self.dut_capabilities = self.dut.telephony["subscription"][
            self.subid].get("capabilities", [])
        self.dut.log.info("DUT capabilities: %s", self.dut_capabilities)
        if CAPABILITY_VOLTE not in self.dut_capabilities:
            raise signals.TestAbortClass("VoLTE is not supported")
        if CAPABILITY_WFC not in self.dut_capabilities:
            raise signals.TestAbortClass("WFC is not supported")
        self.carrier_configs = dumpsys_carrier_config(self.dut)[self.subid]
        self.default_wfc_mode = self.carrier_configs.get(
            CarrierConfigs.DEFAULT_WFC_IMS_MODE_INT, None)
        self.dut_wfc_modes = self.dut.telephony[
            "subscription"][self.subid].get("wfc_modes", [])


    """ Tests Begin """

    @test_tracker_info(uuid="9ed17e7b-c933-4b46-9349-a76fbcba3a24")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_volte_wifi_connected_toggle_wfc(self):
        """Test for WiFi Calling settings:
        nsa 5G + VoLTE Enabled + WiFi Connected, Toggling WFC

        Steps:
        1. Setup DUT Idle, 5G network type, VoLTE enabled.
        2. Make sure DUT WiFi connected, WFC disabled.
        3. Set DUT WFC enabled (WiFi Preferred), verify DUT WFC available,
            report iwlan rat.
        4. Set DUT WFC disabled, verify DUT WFC unavailable,
            not report iwlan rat.

        Expected Results:
        3. DUT WiFi Calling feature bit return True, network rat is iwlan.
        4. DUT WiFi Calling feature bit return False, network rat is not iwlan.
        """

        if not phone_setup_on_rat(self.log, self.dut, rat='5g_volte'):
            self.log.error("Phone Failed to setup Properly")
            return False

        if not change_ims_setting(log=self.log,
                                ad=self.dut,
                                dut_client= self.dut_client,
                                wifi_network_ssid=self.wifi_network_ssid,
                                wifi_network_pass=self.wifi_network_pass,
                                subid=self.subid,
                                dut_capabilities=self.dut_capabilities,
                                airplane_mode=False,
                                wifi_enabled=True,
                                volte_enabled=True,
                                wfc_enabled=True,
                                nw_gen=RAT_NR,
                                wfc_mode=self.default_wfc_mode):
            return False
        if not change_ims_setting(log=self.log,
                                ad=self.dut,
                                dut_client= self.dut_client,
                                wifi_network_ssid=self.wifi_network_ssid,
                                wifi_network_pass=self.wifi_network_pass,
                                dut_capabilities=self.dut_capabilities,
                                subid=self.subid,
                                airplane_mode=False,
                                wifi_enabled=True,
                                volte_enabled=True,
                                wfc_enabled=False,
                                nw_gen=RAT_NR,
                                wfc_mode=None):
            return False
        return change_ims_setting(log=self.log,
                                ad=self.dut,
                                dut_client= self.dut_client,
                                wifi_network_ssid=self.wifi_network_ssid,
                                wifi_network_pass=self.wifi_network_pass,
                                dut_capabilities=self.dut_capabilities,
                                subid=self.subid,
                                airplane_mode=False,
                                wifi_enabled=True,
                                volte_enabled=True,
                                wfc_enabled=True,
                                nw_gen=RAT_NR,
                                wfc_mode=None)


    @test_tracker_info(uuid="425b16ec-869d-4a0b-a1bc-a3243503525f")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_connected_toggle_wfc(self):
        """Test for WiFi Calling settings:
        nsa 5G + VoLTE Disabled + WiFi Connected, Toggling WFC

        Steps:
        1. Setup DUT Idle, nsa 5G network type, VoLTE disabled.
        2. Make sure DUT WiFi connected, WFC disabled.
        3. Set DUT WFC enabled (WiFi Preferred), verify DUT WFC available,
            report iwlan rat.
        4. Set DUT WFC disabled, verify DUT WFC unavailable,
            not report iwlan rat.

        Expected Results:
        3. DUT WiFi Calling feature bit return True, network rat is iwlan.
        4. DUT WiFi Calling feature bit return False, network rat is not iwlan.
        """

        if not phone_setup_on_rat(self.log, self.dut, rat='5g_csfb'):
            self.log.error("Phone Failed to setup Properly")
            return False

        if not change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=False,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode=self.default_wfc_mode):
            return False
        if not change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=False,
                                       wfc_enabled=False,
                                       nw_gen=RAT_NR,
                                       wfc_mode=None):
            return False
        return change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=False,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode=None)


    @test_tracker_info(uuid="9405d8cf-5b73-4d47-a83d-da115caed177")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_volte_wfc_enabled_toggle_wifi(self):
        """Test for WiFi Calling settings:
        nsa 5G + VoLTE Enabled + WFC enabled, Toggling WiFi

        Steps:
        1. Setup DUT Idle, nsa 5G network type, VoLTE enabled.
        2. Make sure DUT WiFi disconnected, WFC enabled (WiFi Preferred).
        3. DUT connect WiFi, verify DUT WFC available, report iwlan rat.
        4. DUT disconnect WiFi,verify DUT WFC unavailable, not report iwlan rat.

        Expected Results:
        3. DUT WiFi Calling feature bit return True, network rat is iwlan.
        4. DUT WiFi Calling feature bit return False, network rat is not iwlan.
        """
        if not phone_setup_on_rat(self.log, self.dut, rat='5g_volte'):
            self.log.error("Phone Failed to setup Properly")
            return False

        if not change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=True,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode=self.default_wfc_mode):
            return False
        if not change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=True,
                                       wfc_enabled=False,
                                       nw_gen=RAT_NR,
                                       wfc_mode=None):
            return False
        return change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=True,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode=None)


    @test_tracker_info(uuid="548f07a6-74e2-4071-ba41-2acf3cd51752")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wfc_enabled_toggle_wifi(self):
        """Test for WiFi Calling settings:
        nsa 5G + VoLTE Disabled + WFC enabled, Toggling WiFi

        Steps:
        1. Setup DUT Idle, nsa 5G network type, VoLTE disabled.
        2. Make sure DUT WiFi disconnected, WFC enabled (WiFi Preferred).
        3. DUT connect WiFi, verify DUT WFC available, report iwlan rat.
        4. DUT disconnect WiFi,verify DUT WFC unavailable, not report iwlan rat.

        Expected Results:
        3. DUT WiFi Calling feature bit return True, network rat is iwlan.
        4. DUT WiFi Calling feature bit return False, network rat is not iwlan.
        """
        if not phone_setup_on_rat(self.log, self.dut, rat='5g_csfb'):
            self.log.error("Phone Failed to setup Properly")
            return False

        if not change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=False,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode=self.default_wfc_mode):
            return False
        if not change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=False,
                                       volte_enabled=False,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode=None):
            return False
        return change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=False,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode=None)


    @test_tracker_info(uuid="f53f8403-b993-4a3b-8d3f-ffc87cdc856e")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wfc_enabled_wifi_connected_toggle_volte(self):
        """Test for WiFi Calling settings:
        nsa 5G + VoLTE Enabled + WiFi Connected + WFC enabled, toggle VoLTE setting

        Steps:
        1. Setup DUT Idle, 5G network type, VoLTE enabled.
        2. Make sure DUT WiFi connected, WFC enabled (WiFi Preferred).
            Verify DUT WFC available, report iwlan rat.
        3. Disable VoLTE on DUT, verify in 2 minutes period,
            DUT does not lost WiFi Calling, DUT still report WFC available,
            rat iwlan.
        4. Enable VoLTE on DUT, verify in 2 minutes period,
            DUT does not lost WiFi Calling, DUT still report WFC available,
            rat iwlan.

        Expected Results:
        2. DUT WiFi Calling feature bit return True, network rat is iwlan.
        3. DUT WiFi Calling feature bit return True, network rat is iwlan.
        4. DUT WiFi Calling feature bit return True, network rat is iwlan.
        """
        if not phone_setup_on_rat(self.log, self.dut, rat='5g_volte'):
            self.log.error("Phone Failed to setup Properly")
            return False

        if not change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=True,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode=self.default_wfc_mode):
            return False
        if not change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=False,
                                       wfc_enabled=True,
                                       wfc_mode=None):
            return False
        return change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=True,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode=None)


    @test_tracker_info(uuid="9d951951-bc04-4db1-85e3-f0980f564fd3")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_volte_wfc_wifi_preferred_to_cellular_preferred(self):
        """Test for WiFi Calling settings:
        nsa 5G + VoLTE Enabled + WiFi Connected + WiFi Preferred,
        change WFC to Cellular Preferred

        Steps:
        1. Setup DUT Idle, nsa 5G network type, VoLTE enabled.
        2. Make sure DUT WiFi connected, WFC is set to WiFi Preferred.
            Verify DUT WFC available, report iwlan rat.
        3. Change WFC setting to Cellular Preferred.
        4. Verify DUT report WFC not available.

        Expected Results:
        2. DUT WiFi Calling feature bit return True, network rat is iwlan.
        4. DUT WiFI Calling feature bit return False, network rat is not iwlan.
        """
        if WFC_MODE_WIFI_PREFERRED not in self.dut_wfc_modes:
            raise signals.TestSkip("WFC_MODE_WIFI_PREFERRED is not supported")
        if WFC_MODE_CELLULAR_PREFERRED not in self.dut_wfc_modes:
            raise signals.TestSkip(
                "WFC_MODE_CELLULAR_PREFERRED is not supported")

        if not phone_setup_on_rat(self.log, self.dut, rat='5g_volte'):
            self.log.error("Phone Failed to setup Properly")
            return False

        if not change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=True,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode=self.default_wfc_mode):
            return False
        return change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=True,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode= WFC_MODE_CELLULAR_PREFERRED)


    @test_tracker_info(uuid="9d951951-bc04-4db1-85e3-f0980f564fd3")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wfc_wifi_preferred_to_cellular_preferred(self):
        """Test for WiFi Calling settings:
        nsa 5G + WiFi Connected + WiFi Preferred, change WFC to Cellular Preferred

        Steps:
        1. Setup DUT Idle, nsa 5G network type, VoLTE disabled.
        2. Make sure DUT WiFi connected, WFC is set to WiFi Preferred.
            Verify DUT WFC available, report iwlan rat.
        3. Change WFC setting to Cellular Preferred.
        4. Verify DUT report WFC not available.

        Expected Results:
        2. DUT WiFi Calling feature bit return True, network rat is iwlan.
        4. DUT WiFI Calling feature bit return False, network rat is not iwlan.
        """
        if WFC_MODE_WIFI_PREFERRED not in self.dut_wfc_modes:
            raise signals.TestSkip("WFC_MODE_WIFI_PREFERRED is not supported")
        if WFC_MODE_CELLULAR_PREFERRED not in self.dut_wfc_modes:
            raise signals.TestSkip(
                "WFC_MODE_CELLULAR_PREFERRED is not supported")

        if not phone_setup_on_rat(self.log, self.dut, rat='5g_csfb'):
            self.log.error("Phone Failed to setup Properly")
            return False

        if not change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=False,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode= WFC_MODE_WIFI_PREFERRED):
            return False
        return change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=False,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode= WFC_MODE_CELLULAR_PREFERRED)


    @test_tracker_info(uuid="bb3acfce-1ea9-40c2-9151-12fbc235e359")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_volte_wfc_cellular_preferred_to_wifi_preferred(self):
        """Test for WiFi Calling settings:
        nsa 5G + VoLTE Enabled + WiFi Connected + Cellular Preferred,
        change WFC to WiFi Preferred

        Steps:
        1. Setup DUT Idle, nsa 5G network type, VoLTE enabled.
        2. Make sure DUT WiFi connected, WFC is set to Cellular Preferred.
            Verify DUT WFC not available.
        3. Change WFC setting to WiFi Preferred.
        4. Verify DUT report WFC available.

        Expected Results:
        2. DUT WiFi Calling feature bit return False, network rat is not iwlan.
        4. DUT WiFI Calling feature bit return True, network rat is iwlan.
        """
        if WFC_MODE_WIFI_PREFERRED not in self.dut_wfc_modes:
            raise signals.TestSkip("WFC_MODE_WIFI_PREFERRED is not supported")
        if WFC_MODE_CELLULAR_PREFERRED not in self.dut_wfc_modes:
            raise signals.TestSkip(
                "WFC_MODE_CELLULAR_PREFERRED is not supported")
        if not phone_setup_on_rat(self.log, self.dut, rat='5g_volte'):
            self.log.error("Phone Failed to setup Properly")
            return False

        if not change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=True,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode= WFC_MODE_CELLULAR_PREFERRED):
            return False
        return change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=True,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode=WFC_MODE_WIFI_PREFERRED)


    @test_tracker_info(uuid="d4dea8e0-5795-4769-80e2-29c0f951a84a")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wfc_cellular_preferred_to_wifi_preferred(self):
        """Test for WiFi Calling settings:
        nsa 5G + WiFi Connected + Cellular Preferred, change WFC to WiFi Preferred

        Steps:
        1. Setup DUT Idle, nsa 5G network type, VoLTE disabled.
        2. Make sure DUT WiFi connected, WFC is set to Cellular Preferred.
            Verify DUT WFC not available.
        3. Change WFC setting to WiFi Preferred.
        4. Verify DUT report WFC available.

        Expected Results:
        2. DUT WiFi Calling feature bit return False, network rat is not iwlan.
        4. DUT WiFI Calling feature bit return True, network rat is iwlan.
        """
        if WFC_MODE_WIFI_PREFERRED not in self.dut_wfc_modes:
            raise signals.TestSkip("WFC_MODE_WIFI_PREFERRED is not supported")
        if WFC_MODE_CELLULAR_PREFERRED not in self.dut_wfc_modes:
            raise signals.TestSkip(
                "WFC_MODE_CELLULAR_PREFERRED is not supported")

        if not phone_setup_on_rat(self.log, self.dut, rat='5g_csfb'):
            self.log.error("Phone Failed to setup Properly")
            return False

        if not change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=False,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode=WFC_MODE_CELLULAR_PREFERRED):
            return False
        return change_ims_setting(log=self.log,
                                       ad=self.dut,
                                       dut_client= self.dut_client,
                                       wifi_network_ssid=self.wifi_network_ssid,
                                       wifi_network_pass=self.wifi_network_pass,
                                       dut_capabilities=self.dut_capabilities,
                                       subid=self.subid,
                                       airplane_mode=False,
                                       wifi_enabled=True,
                                       volte_enabled=False,
                                       wfc_enabled=True,
                                       nw_gen=RAT_NR,
                                       wfc_mode=WFC_MODE_WIFI_PREFERRED)


