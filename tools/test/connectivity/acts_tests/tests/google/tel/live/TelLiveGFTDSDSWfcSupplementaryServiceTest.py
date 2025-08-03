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
from acts_contrib.test_utils.tel.loggers.protos.telephony_metric_pb2 import TelephonyVoiceTestResult
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

CallResult = TelephonyVoiceTestResult.CallResult.Value


class TelLiveGFTDSDSWfcSupplementaryServiceTest(TelephonyBaseTest):
    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        self.message_lengths = (50, 160, 180)
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

    @test_tracker_info(uuid="3d328dd0-acb6-48be-9cb2-ffffb15bf2cd")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_wfc_psim_cellular_preferred_apm_on_with_volte_on_dds_slot_0(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            callee_rat=['wfc', 'general'],
            is_airplane_mode=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="aac41970-4fdb-4f22-bf33-2092ce14db6e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_wfc_psim_wifi_preferred_apm_off_with_volte_on_dds_slot_0(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            callee_rat=['wfc', 'general'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="716a795a-529f-450a-800d-80c1dd7c0e3f")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_wfc_psim_cellular_preferred_apm_on_with_volte_on_dds_slot_1(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            1,
            callee_rat=['wfc', 'general'],
            is_airplane_mode=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="0743331b-78a4-4721-91e7-4c6b894b4b61")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_wfc_psim_wifi_preferred_apm_off_with_volte_on_dds_slot_1(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            1,
            callee_rat=['wfc', 'general'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="65e8192f-c8af-454e-a142-0ba95f801fb4")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_volte_psim_cellular_preferred_wifi_on_dds_slot_0(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            callee_rat=["volte", "general"],
            is_wifi_connected=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="29175f3c-0f7b-4baf-8399-a37cc92acce0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_wfc_esim_cellular_preferred_apm_on_with_volte_on_dds_slot_0(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            callee_rat=['general', 'wfc'],
            is_airplane_mode=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="a652a973-7445-4b3d-83cf-7b3ff2e1b47d")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_wfc_esim_wifi_preferred_apm_off_with_volte_on_dds_slot_0(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            callee_rat=['general', 'wfc'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="8ff9bc8f-8740-4198-b437-19994f07758b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_wfc_esim_cellular_preferred_apm_on_with_volte_on_dds_slot_1(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            1,
            callee_rat=['general', 'wfc'],
            is_airplane_mode=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="3341cfec-4720-4c20-97c2-29409c727fab")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_wfc_esim_wifi_preferred_apm_off_with_volte_on_dds_slot_1(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            1,
            callee_rat=['general', 'wfc'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="7cfea32a-6de2-4285-99b1-1219efaf542b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_volte_esim_cellular_preferred_wifi_on_dds_slot_0(self):
        return msim_volte_wfc_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            callee_rat=["general", "volte"],
            is_wifi_connected=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="27422851-620c-4009-8e2a-730a97d88cb0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_waiting_hold_swap_wfc_psim_cellular_preferred_apm_on_with_volte_on_dds_slot_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            host_rat=['wfc', 'general'],
            merge=False,
            is_airplane_mode=True,
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="f741f336-7eee-473e-b68f-c3505dbab935")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_waiting_hold_swap_wfc_psim_wifi_preferred_apm_off_with_volte_on_dds_slot_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            host_rat=['wfc', 'general'],
            merge=False,
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="4c2c9896-1cfd-4d4c-9594-97c600ac3f50")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_waiting_hold_swap_wfc_psim_cellular_preferred_apm_on_with_volte_on_dds_slot_1(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            1,
            host_rat=['wfc', 'general'],
            merge=False,
            is_airplane_mode=True,
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="74491391-8ea5-4bad-868b-332218a8b015")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_waiting_hold_swap_wfc_psim_wifi_preferred_apm_off_with_volte_on_dds_slot_1(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            1,
            host_rat=['wfc', 'general'],
            merge=False,
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="40185d6d-e127-4696-9ed8-53dbe355b1c3")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_waiting_hold_swap_volte_psim_cellular_preferred_wifi_on_dds_slot_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            host_rat=["volte", "general"],
            merge=False,
            is_airplane_mode=False,
            is_wifi_connected=True,
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="b07a6693-3d1c-496a-b2fc-90711b2bf4f6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_waiting_hold_swap_wfc_esim_cellular_preferred_apm_on_with_volte_on_dds_slot_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            host_rat=['general', 'wfc'],
            merge=False,
            is_airplane_mode=True,
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="c4461963-5d99-4c6a-b2f6-92de2437e0e7")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_waiting_hold_swap_wfc_esim_wifi_preferred_apm_off_with_volte_on_dds_slot_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            host_rat=['general', 'wfc'],
            merge=False,
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="cece707d-fa13-4748-a777-873eaaa27bca")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_waiting_hold_swap_wfc_esim_cellular_preferred_apm_on_with_volte_on_dds_slot_1(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            1,
            host_rat=['general', 'wfc'],
            merge=False,
            is_airplane_mode=True,
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="bae04c51-99eb-43a5-9f30-f16ac369bb71")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_waiting_hold_swap_wfc_esim_wifi_preferred_apm_off_with_volte_on_dds_slot_1(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            1,
            host_rat=['general', 'wfc'],
            merge=False,
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="c1d2c088-8782-45cd-b320-effecf6838b4")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_waiting_hold_swap_volte_esim_cellular_preferred_wifi_on_dds_slot_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            host_rat=["general", "volte"],
            merge=False,
            is_airplane_mode=False,
            is_wifi_connected=True,
            reject_once=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="bb4119c9-f5bc-4ef1-acbd-e8f4099f2ba9")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_conf_call_wfc_psim_cellular_preferred_apm_on_with_volte_on_dds_slot_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            host_rat=['wfc', 'general'],
            is_airplane_mode=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="2e48ad65-bfa9-43d3-aa3a-62f412d931cc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_conf_call_wfc_psim_wifi_preferred_apm_off_with_volte_on_dds_slot_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            host_rat=['wfc', 'general'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="39a9c791-16d0-4476-94e9-fc04e9f5f65a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_conf_call_wfc_psim_cellular_preferred_apm_on_with_volte_on_dds_slot_1(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            1,
            host_rat=['wfc', 'general'],
            is_airplane_mode=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="daba5874-0aaa-4f47-9548-e484dd72a8c6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_conf_call_wfc_psim_wifi_preferred_apm_off_with_volte_on_dds_slot_1(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            1,
            host_rat=['wfc', 'general'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="ef96a46b-8898-4d5e-a494-31b8047fc986")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_conf_call_volte_psim_cellular_preferred_wifi_on_dds_slot_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            0,
            host_rat=["volte", "general"],
            is_wifi_connected=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="c565b2af-512c-4097-a4f7-7d920ea78373")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_conf_call_wfc_esim_cellular_preferred_apm_on_with_volte_on_dds_slot_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            host_rat=['general', 'wfc'],
            is_airplane_mode=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="078db8f5-eaf9-409c-878b-70c13be18802")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_conf_call_wfc_esim_wifi_preferred_apm_off_with_volte_on_dds_slot_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            host_rat=['general', 'wfc'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="77c70690-6206-43a5-9789-e9ff39235d42")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_conf_call_wfc_esim_cellular_preferred_apm_on_with_volte_on_dds_slot_1(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            1,
            host_rat=['general', 'wfc'],
            is_airplane_mode=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="b48138dd-5c03-4592-a96d-f63833456197")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_conf_call_wfc_esim_wifi_preferred_apm_off_with_volte_on_dds_slot_1(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            1,
            host_rat=['general', 'wfc'],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)

    @test_tracker_info(uuid="c2e3ff0e-6112-4b79-92e2-2fabeaf87b1f")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_conf_call_volte_esim_cellular_preferred_wifi_on_dds_slot_0(self):
        return msim_volte_wfc_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            0,
            host_rat=["general", "volte"],
            is_wifi_connected=True,
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass)