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

import time

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.loggers.protos.telephony_metric_pb2 import TelephonyVoiceTestResult
from acts_contrib.test_utils.tel.loggers.telephony_metric_logger import TelephonyMetricLogger
from acts_contrib.test_utils.tel.tel_defines import SimSlotInfo
from acts_contrib.test_utils.tel.tel_defines import YOUTUBE_PACKAGE_NAME
from acts_contrib.test_utils.tel.tel_dsds_utils import dsds_message_streaming_test
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest

_WAIT_TIME_FOR_MEP_ENABLE_INTERVAL = 60
_WAIT_TIME_FOR_MEP_ENABLE = 180

CallResult = TelephonyVoiceTestResult.CallResult.Value


class Nsa5gDSDSMessageTest(TelephonyBaseTest):
    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        self.tel_logger = TelephonyMetricLogger.for_test_case()
        if getattr(self.android_devices[0], 'mep', False):
            start_time = time.monotonic()
            timeout = start_time + _WAIT_TIME_FOR_MEP_ENABLE
            while time.monotonic() < timeout:
                mep_logs = self.android_devices[0].search_logcat("UNSOL_SIM_SLOT_STATUS_CHANGED")
                if mep_logs:
                    for mep_log in mep_logs:
                        if "num_ports=2" in mep_log["log_message"]:
                            break
                time.sleep(_WAIT_TIME_FOR_MEP_ENABLE_INTERVAL)
            else:
                self.log.warning("Couldn't found MEP enabled logs.")

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)

    @test_tracker_info(uuid="123a50bc-f0a0-4129-9377-cc63c76d5727")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="5dcf76bc-369f-4d47-b3ec-318559a95843")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="245a6148-cd45-4b82-bf4c-5679ebe15e29")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="5a93d377-d9bc-477c-bfab-2496064e3522")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="dd4a9fb5-b0fe-492b-ad24-61e022d13a22")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="09100a8f-b7ed-41a0-9f04-e716115cabb8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="b5971c57-bbe9-4e87-a6f2-9953fa770a15")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="142b11d4-b593-4a09-8fc6-35e310739244")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="87759475-0208-4d9b-b5b9-814fdb97f09c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_psim_5g_nsa_volte_esim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="2f14e81d-330f-4cdd-837c-1168185ffec4")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_psim_5g_nsa_volte_esim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="38f01127-54bf-4c55-b7d8-d8f41352b399")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_psim_5g_nsa_volte_esim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="4e0c9692-a758-4169-85fe-c33bd2651525")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_psim_5g_nsa_volte_esim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="9cc45474-1fca-4008-8499-87829d6516ea")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_esim_4g_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="341786de-5b23-438a-a91b-97cf420ef5fd")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_esim_4g_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="527e8629-6e0d-4742-98c0-5cbc868c430e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_esim_4g_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="66277aa0-0a9a-4a25-828f-b0315ae7fd0e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_esim_4g_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="a4d797b6-2699-48de-b36b-b10a1901305b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_psim_4g_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="371286ba-f1da-4459-a7e8-0368d0fae147")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_psim_4g_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="183cda35-45aa-485d-b3d4-975d78f7d361")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_psim_4g_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="d9cb69ce-c462-4fd4-b716-bfb1fd2ed86a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_psim_4g_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="dfe54cea-8396-4af4-8aee-9dadad602e5b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_esim_5g_nsa_volte_psim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="fd4ae44c-3527-4b90-8d33-face10e160a6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_esim_5g_nsa_volte_psim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="51d5e05d-66e7-4369-91e0-6cdc573d9a59")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_esim_5g_nsa_volte_psim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="38271a0f-2efb-4991-9f24-6da9f003ddd4")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_esim_5g_nsa_volte_psim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="dde1e900-abcd-4a5f-8872-02456ea248ee")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="5a8ad6dc-687a-498e-8b99-119f3cbb781c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="765443f4-d4a0-45fe-8c97-763feb4b588b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="026b9e8f-400e-4b59-b40d-d4e741838be0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="468536c1-de6e-48e7-b59e-11f17389ac12")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="c4ae7f6b-bc20-4cb2-8e41-8a02171aec6f")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="2d70443e-b442-48e0-9c1f-ce1409184ff8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="47fbc6c0-ca76-44c0-a166-d8c99d16b6ac")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="39684fbc-73d1-48cb-af3f-07a366a6b190")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_psim_5g_nsa_volte_esim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="6adf4163-4969-4129-bbac-4ebdac4c4cf5")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_psim_5g_nsa_volte_esim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="7b636038-5b0c-4844-ba2a-2e76ed787f72")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_psim_5g_nsa_volte_esim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="b5008ad4-372d-4849-b47b-583be6aa080a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_psim_5g_nsa_volte_esim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="fd6b33b6-c654-4ec0-becc-2fd7ec10c291")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_esim_4g_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="0267c4e8-e5b8-4001-912f-76c387a15f79")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_esim_4g_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="a54caa16-dfc6-46e1-a376-b4b585e2e840")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_esim_4g_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="f6af184a-933b-467e-81a7-44ef48b56540")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_esim_4g_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="2e89f125-aacc-4c36-a1c2-308cd83b0e22")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_psim_4g_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="03c23c94-3cc5-4ecf-9b87-273c815b9f53")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_psim_4g_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="d2af382a-0f87-46c0-b2de-84f5a549e32c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_psim_4g_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="bf788d99-954b-47e2-b465-8565bb30e907")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_psim_4g_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="f0b1d46b-6ddc-4625-b653-38e323e542ad")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_esim_5g_nsa_volte_psim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="b17a4943-1f69-428a-bd63-13144b2bc592")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_esim_5g_nsa_volte_psim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="98d7b7b8-0bd3-4362-957b-56c8b19ac3d4")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_esim_5g_nsa_volte_psim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="5f0f4174-548d-43ab-b520-5e2211fdaacc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_esim_5g_nsa_volte_psim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="09cd2c80-5c94-4b97-badd-b9d23712cbad")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mo_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="SMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="deed7037-932e-4c08-bbf0-989144a51193")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mt_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="14fe5ef1-e6aa-4615-887a-ac26043c2dfc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mo_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="1f07d373-dc81-42f4-a5c5-461304f1e7bf")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mt_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="a9f066d3-a5db-4319-a5c9-f7a20f84cd6e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mo_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="688485af-cdc7-43b7-af01-baf6bc695b70")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mt_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="7fef6173-1f37-45d3-be94-60fea340444c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mo_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="71b15942-6c8f-41b3-8dc9-5a1dea64aad4")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mt_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="6cbc50e7-e135-405d-bf69-ab074d345d80")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mo_psim_5g_nsa_volte_esim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="SMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="d976560a-1ea1-421a-9c2d-906cbfb7654e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mt_psim_5g_nsa_volte_esim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="0e3a10b2-2351-49a2-9282-99aae1372bf0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mo_psim_5g_nsa_volte_esim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="e713c430-0bfa-4d25-91f3-1b6fec84b3a5")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mt_psim_5g_nsa_volte_esim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="770bec4d-c1c9-4936-8683-3fb796827eba")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mo_esim_4g_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="3f34328b-9295-4740-a48b-3ffadbab3fb5")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mt_esim_4g_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="eeaeb58a-7566-498e-a4d1-ce1cbd82f362")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mo_esim_4g_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="7550ef0b-b0d3-4932-95d3-119abdad53ad")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mt_esim_4g_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="6dd693f4-6c61-4048-9027-02c17874dbd0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mo_psim_4g_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="SMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="976d5c30-63af-4e49-952e-2cd4147b7c8d")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mt_psim_4g_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="b2c94d26-c806-417d-a751-618491dce246")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mo_psim_4g_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="02739364-2848-4242-bb6e-41a03ec358ed")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mt_psim_4g_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="811880fd-c422-4548-8dfb-cddbfb1dc6c0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mo_esim_5g_nsa_volte_psim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="9e02ade7-c2b6-4b7e-ab15-b42c119f4141")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mt_esim_5g_nsa_volte_psim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="ba2ce2de-a0a6-4abe-adb8-110541e60cb1")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mo_esim_5g_nsa_volte_psim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="46e1397c-7296-4aac-8e0f-7049d04427bc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_sms_mt_esim_5g_nsa_volte_psim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="181c1ac9-625e-450d-b566-834e20ecd59d")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mo_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="MMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="b37aceed-7f67-4ae3-aba8-0f94d24d81e2")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mt_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="0fb13f48-bfd7-4019-8a33-e229677b3357")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mo_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="016369fa-3420-45f5-9ed2-3776816f4e4b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mt_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="65fdbecf-9ea5-4881-9e99-4a1ed90b76cc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mo_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="3e6b4bcf-30cd-4502-8811-2a5a7a9142a5")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mt_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="a49b7a91-8811-403b-b8ed-ac0edad69c2c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mo_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="961db859-ad50-4b13-8555-e523843d3e0c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mt_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="398fea0a-4ef4-4a6d-bea0-76ab0b2e2c34")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mo_psim_5g_nsa_volte_esim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="MMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="06503954-caff-47ba-8ed3-7793fca4e94a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mt_psim_5g_nsa_volte_esim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="bc43d539-7bbd-4b12-b88a-ecf0229f1ed5")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mo_psim_5g_nsa_volte_esim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="d558a53b-396e-4a9e-aec1-929f41f8ad2a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mt_psim_5g_nsa_volte_esim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="74afcd0a-e121-4028-99c6-48cad25b18b8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mo_esim_4g_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="5042ec42-f1b3-466a-8e06-6e1c3de2dffb")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mt_esim_4g_volte_psim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="6f286c93-004b-4360-9afa-78f15a0a5549")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mo_esim_4g_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="0313548b-653b-44ea-bb63-76b69b67e456")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mt_esim_4g_volte_psim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="4c1e4667-2b0d-4f4d-a419-c349ef767dbc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mo_psim_4g_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="MMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="f82650db-d0d9-4990-a3c6-b918eabeddc6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mt_psim_4g_volte_esim_5g_nsa_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=0,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="76dca39c-8ead-435b-8b5f-8b167946a18e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mo_psim_4g_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="29d8ffec-be68-4d12-b2ad-b2e8f95347c1")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mt_psim_4g_volte_esim_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="625bc42e-c9c7-442e-8464-72aab6055ef8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mo_esim_5g_nsa_volte_psim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="18f852da-0877-4624-bbcd-d59a168780dc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mt_esim_5g_nsa_volte_psim_4g_volte_dds_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="cac044ec-176d-4eef-885d-ba419ab634eb")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mo_esim_5g_nsa_volte_psim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=True)

    @test_tracker_info(uuid="245ee61e-f768-403c-9005-7eed90deedd7")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_youtube_and_mms_mt_esim_5g_nsa_volte_psim_4g_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    # e+e
    @test_tracker_info(uuid="3233f15d-0b42-4215-88af-ccb78bce5cc1")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_esim_port_0_5g_nsa_volte_esim_port_1_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="213be122-d935-49c2-9db5-cb1828c23687")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_esim_port_0_5g_nsa_volte_esim_port_1_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="22441a1d-0152-4870-a14c-7f733e82b417")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_esim_port_1_5g_nsa_volte_esim_port_0_5g_nsa_volte_dds_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=2,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="cf6871f4-8ad2-4f2a-8ae0-72ee030e42cd")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_esim_port_1_5g_nsa_volte_esim_port_0_5g_nsa_volte_dds_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=2,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="56ec2aa6-e3ca-4f1b-8167-6276842610d2")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_esim_port_1_5g_nsa_volte_esim_port_0_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="a185e6f8-fb1b-4876-84ed-fab6c51010ca")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_esim_port_1_5g_nsa_volte_esim_port_0_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="3089b689-49e7-4e63-b931-5073c323411d")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_esim_port_0_5g_nsa_volte_esim_port_1_5g_nsa_volte_dds_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=2,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="2b6a9461-0a8c-4452-beac-f133fb0e383b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_esim_port_0_5g_nsa_volte_esim_port_1_5g_nsa_volte_dds_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=2,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="b1da493c-aa11-4bf4-814d-ecd2fdffb835")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_esim_port_0_5g_nsa_volte_esim_port_1_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="7bbd85ca-7484-4850-99c9-00761715af5f")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_esim_port_0_5g_nsa_volte_esim_port_1_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="02d00988-5d41-467b-9eaa-abb84a5e89f6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_esim_port_1_5g_nsa_volte_esim_port_0_5g_nsa_volte_dds_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=2,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="fc3ce672-d9a8-4346-b46d-bbc9c0c8db35")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_esim_port_1_5g_nsa_volte_esim_port_0_5g_nsa_volte_dds_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=2,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="121ca139-e457-49f6-ba0e-ffc2008ccdd8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_esim_port_1_5g_nsa_volte_esim_port_0_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="8cb07ef0-b421-43d2-80a7-48440975dc2c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_esim_port_1_5g_nsa_volte_esim_port_0_5g_nsa_volte_dds_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="43ae713b-3309-47b9-8036-59f5dd124dab")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_esim_port_0_5g_nsa_volte_esim_port_1_5g_nsa_volte_dds_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=2,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="3ad59561-5860-4c8d-87f5-adf5af973757")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_esim_port_0_5g_nsa_volte_esim_port_1_5g_nsa_volte_dds_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=2,
            msg_type="MMS",
            direction="mt",
            streaming=False)