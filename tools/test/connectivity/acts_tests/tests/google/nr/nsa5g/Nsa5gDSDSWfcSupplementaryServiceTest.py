#!/usr/bin/env python3
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

from acts import signals
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.loggers.telephony_metric_logger import TelephonyMetricLogger
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_CONFERENCE
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_dsds_utils import erase_call_forwarding
from acts_contrib.test_utils.tel.tel_dsds_utils import msim_volte_wfc_call_forwarding
from acts_contrib.test_utils.tel.tel_dsds_utils import msim_volte_wfc_call_voice_conf
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_voice_sub_id
from acts_contrib.test_utils.tel.tel_test_utils import get_capability_for_subscription
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_wifi_utils import set_wifi_to_default


class Nsa5gDSDSWfcSupplementaryServiceTest(TelephonyBaseTest):
    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        self.tel_logger = TelephonyMetricLogger.for_test_case()
        toggle_airplane_mode(self.log, self.android_devices[0], False)
        erase_call_forwarding(self.log, self.android_devices[0])
        if not get_capability_for_subscription(
            self.android_devices[0],
            CAPABILITY_CONFERENCE,
            get_outgoing_voice_sub_id(self.android_devices[0])):
            self.android_devices[0].log.error(
                "Conference call is not supported, abort test.")
            raise signals.TestAbortClass(
                "Conference call is not supported, abort test.")

    def teardown_test(self):
        toggle_airplane_mode(self.log, self.android_devices[0], False)
        ensure_phones_idle(self.log, self.android_devices)
        erase_call_forwarding(self.log, self.android_devices[0])
        set_wifi_to_default(self.log, self.android_devices[0])

    @test_tracker_info(uuid="53169ee2-eb70-423e-bbe0-3112f34d2d73")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_psim_5g_nsa_wfc_wifi_preferred_apm_off_dds_0(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            callee_rat=['5g_wfc', 'general'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="f0b1c9ce-a386-4b25-8a44-8ca4897fc650")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_psim_5g_nsa_wfc_wifi_preferred_apm_off_dds_1(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            1,
            callee_rat=['5g_wfc', 'general'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="c952fe28-823d-412d-a3ac-797bd6e2dc09")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_psim_5g_nsa_volte_cellular_preferred_wifi_on_dds_0(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            callee_rat=["5g_volte", "general"],
            is_wifi_connected=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="d9e58366-46ea-454a-a1b1-466ec91112ef")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_esim_5g_nsa_wfc_wifi_preferred_apm_off_dds_0(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            callee_rat=['general', '5g_wfc'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="18ce70a6-972c-4723-8e65-0c9814d14e76")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_esim_5g_nsa_wfc_wifi_preferred_apm_off_dds_1(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            1,
            callee_rat=['general', '5g_wfc'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="d843d4cd-c562-47f1-b35b-57a84896314e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_esim_5g_nsa_volte_cellular_preferred_wifi_on_dds_0(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            callee_rat=["general", "5g_volte"],
            is_wifi_connected=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="556a0737-f2c2-44c4-acfd-4eeb57e4c15e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cw_hold_swap_psim_5g_nsa_wfc_wifi_preferred_apm_off_dds_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            host_rat=['5g_wfc', 'general'],
            merge=False,
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="d86de799-73ed-432e-b9b8-e762df459ad0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cw_hold_swap_psim_5g_nsa_wfc_wifi_preferred_apm_off_dds_1(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            1,
            host_rat=['5g_wfc', 'general'],
            merge=False,
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="9b9a9cd0-218f-4694-b5b7-ec2818abad48")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cw_hold_swap_psim_5g_nsa_volte_cellular_preferred_wifi_on_dds_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            host_rat=["5g_volte", "general"],
            merge=False,
            is_airplane_mode=False,
            is_wifi_connected=True,
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="02dd5686-0a55-497f-8b0c-9f624b6d7af5")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cw_hold_swap_esim_5g_nsa_wfc_wifi_preferred_apm_off_dds_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            host_rat=['general', '5g_wfc'],
            merge=False,
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="1527f060-8226-4507-a502-09e55096da0a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cw_hold_swap_esim_5g_nsa_wfc_wifi_preferred_apm_off_dds_1(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            1,
            host_rat=['general', '5g_wfc'],
            merge=False,
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="e6db2878-8d64-4566-95f9-e8cbf28723e8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cw_hold_swap_esim_5g_nsa_volte_cellular_preferred_wifi_on_dds_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            host_rat=["general", "5g_volte"],
            merge=False,
            is_airplane_mode=False,
            is_wifi_connected=True,
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="5dfb45b7-2706-418f-a5c1-2f8ca9602a29")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_conf_psim_5g_nsa_wfc_wifi_preferred_apm_off_dds_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            host_rat=['5g_wfc', 'general'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="3b520d38-e1f4-46dd-90a7-90d91766e290")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_conf_psim_5g_nsa_wfc_wifi_preferred_apm_off_dds_1(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            1,
            host_rat=['5g_wfc', 'general'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="f3f09280-bd34-46dc-b813-e017d671ddba")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_conf_psim_5g_nsa_volte_cellular_preferred_wifi_on_dds_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            host_rat=["5g_volte", "general"],
            is_wifi_connected=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="f157ba39-b4ae-464a-840a-56e94ba62736")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_conf_esim_5g_wfc_wifi_preferred_apm_off_dds_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            host_rat=['general', '5g_wfc'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="872413fa-ae9c-4482-9e87-a3a4a2738bab")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_conf_esim_5g_nsa_wfc_wifi_preferred_apm_off_dds_1(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            1,
            host_rat=['general', '5g_wfc'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="18023ab7-fa96-4dda-a9ed-dd7562a0d185")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_conf_esim_5g_nsa_volte_cellular_preferred_wifi_on_dds_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            host_rat=["general", "5g_volte"],
            is_wifi_connected=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)