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

from acts import signals
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.loggers.telephony_metric_logger import TelephonyMetricLogger
from acts_contrib.test_utils.tel.tel_defines import SimSlotInfo
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_dsds_utils import dsds_call_streaming_test
from acts_contrib.test_utils.tel.tel_dsds_utils import dsds_voice_call_test
from acts_contrib.test_utils.tel.tel_dsds_utils import enable_slot_after_voice_call_test
from acts_contrib.test_utils.tel.tel_dsds_utils import enable_slot_after_data_call_test
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle

_WAIT_TIME_FOR_MEP_ENABLE_INTERVAL = 60
_WAIT_TIME_FOR_MEP_ENABLE = 180


class Nsa5gDSDSVoiceTest(TelephonyBaseTest):
    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        self.tel_logger = TelephonyMetricLogger.for_test_case()
        if getattr(self.android_devices[0], 'mep', False):
            start_time = time.monotonic()
            timeout = start_time + _WAIT_TIME_FOR_MEP_ENABLE
            while time.monotonic() < timeout:
                mep_logs = self.android_devices[0].search_logcat(
                    "UNSOL_SIM_SLOT_STATUS_CHANGED")
                if mep_logs:
                    for mep_log in mep_logs:
                        if "num_ports=2" in mep_log["log_message"]:
                            break
                time.sleep(_WAIT_TIME_FOR_MEP_ENABLE_INTERVAL)
            else:
                self.log.warning("Couldn't found MEP enabled logs.")

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)

    # psim 5g nsa volte & esim 5g nsa volte & dds slot 0
    @test_tracker_info(uuid="8a8c3f42-f5d7-4299-8d84-64ac5377788f")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_volte_esim_5g_nsa_volte_dds_0(self):
        """A MO VoLTE call dialed at pSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="b05b6aea-7c48-4412-b0b1-f57192fc786c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_volte_esim_5g_nsa_volte_dds_0(self):
        """A MT VoLTE call received at pSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="213d5e6f-97df-4c2a-9745-4e40a704853a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        """A MO VoLTE call dialed at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="48a06a2f-b3d0-4b0e-85e5-2d439ee3147b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        """A MT VoLTE call received at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            streaming=False)

    # psim 5g nsa volte & esim 5g nsa volte & dds slot 1
    @test_tracker_info(uuid="406bd5e5-b549-470d-b15a-20b4bb5ff3db")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        """A MO VoLTE call dialed at pSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="a1e52cee-78ab-4d6e-859b-faf542b8056b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        """A MT VoLTE call received at pSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="3b9d796c-b658-4bff-aae0-1243ce8c3d54")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_volte_psim_5g_nsa_volte_dds_1(self):
        """A MO VoLTE call dialed at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="e3edd065-72e1-4067-901c-1454706e9f43")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_volte_psim_5g_nsa_volte_dds_1(self):
        """A MT VoLTE call received at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            streaming=False)

    # psim 5g nsa volte & esim 4g volte & dds slot 0
    @test_tracker_info(uuid="2890827d-deb2-42ea-921d-3b45f7645d61")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_volte_esim_4g_volte_dds_0(self):
        """A MO VoLTE call dialed at pSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="83d9b127-25da-4c19-a3a0-470a5ced020b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_volte_esim_4g_volte_dds_0(self):
        """A MT VoLTE call received at pSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="14c29c79-d100-4f03-b3df-f2ae4a172cc5")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_4g_volte_psim_5g_nsa_volte_dds_0(self):
        """A MO VoLTE call dialed at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="12a59cc1-8c1e-44a0-836b-0d842c0746a3")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_4g_volte_psim_5g_nsa_volte_dds_0(self):
        """A MT VoLTE call received at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            streaming=False)

    # psim 5g nsa volte & esim 4g volte & dds slot 1
    @test_tracker_info(uuid="9dfa66cc-f464-4964-9e5a-07e01d3e263e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_volte_esim_4g_volte_dds_1(self):
        """A MO VoLTE call dialed at pSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="97e9ecc0-e377-46a8-9b13-ecedcb98922b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_volte_esim_4g_volte_dds_1(self):
        """A MT VoLTE call received at pSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="5814cd18-e33b-45c5-b129-bec7e3992d8e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_4g_volte_psim_5g_nsa_volte_dds_1(self):
        """A MO VoLTE call dialed at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="457dd160-f7b1-4cfd-920f-1f5ab64f6d78")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_4g_volte_psim_5g_nsa_volte_dds_1(self):
        """A MT VoLTE call received at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            streaming=False)

    # psim 4g volte & esim 5g nsa volte & dds slot 0
    @test_tracker_info(uuid="db5fca13-bcd8-420b-9953-256186efa290")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_4g_volte_esim_5g_nsa_volte_dds_0(self):
        """A MO VoLTE call dialed at pSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="2fe76eda-20b2-46ab-a1f4-c2c2bc501f38")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_4g_volte_esim_5g_nsa_volte_dds_0(self):
        """A MT VoLTE call received at pSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="90005074-e21f-47c3-9965-54b513214600")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_volte_psim_4g_volte_dds_0(self):
        """A MO VoLTE call dialed at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="eaf94a45-66d0-41d0-8cb2-153fa3f751f9")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_volte_psim_4g_volte_dds_0(self):
        """A MT VoLTE call received at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 0)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            streaming=False)

    # psim 4g volte & esim 5g nsa volte & dds slot 1
    @test_tracker_info(uuid="8ee47ad7-24b6-4cd3-9443-6ab677695eb7")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_4g_volte_esim_5g_nsa_volte_dds_1(self):
        """A MO VoLTE call dialed at pSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="8795b95d-a138-45cd-b45c-41ad4021589a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_4g_volte_esim_5g_nsa_volte_dds_1(self):
        """A MT VoLTE call received at pSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="33f2fa73-de7b-4b68-b9b8-aa08f6511e1a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_volte_psim_4g_volte_dds_1(self):
        """A MO VoLTE call dialed at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="b1ae55f1-dfd4-4e50-a0e3-df3b3ae29c68")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_volte_psim_4g_volte_dds_1(self):
        """A MT VoLTE call received at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            streaming=False)

    # e+e
    @test_tracker_info(uuid="9c35e485-b813-4af2-b30f-d331337a6eaf")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_0_mo_5g_nsa_volte_esim_port_1_5g_nsa_volte_dds_1(self):
        """A MO VoLTE call dialed at eSIM port 0, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 0 (SimSlotInfo.SLOT_1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="d321af5a-ec10-4933-9fe4-ebbc2ac52756")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_0_mt_5g_nsa_volte_esim_port_1_5g_nsa_volte_dds_1(self):
        """A MT VoLTE call dialed at eSIM port 0, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 0 (SimSlotInfo.SLOT_1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="ca53ccbb-bcc9-4c04-897e-ac95dfb8178d")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_1_mo_5g_nsa_volte_esim_port_0_5g_nsa_volte_dds_1(self):
        """A MO VoLTE call dialed at eSIM port 1, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 0 (SimSlotInfo.SLOT_1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_2,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="0417e06b-2669-454c-b825-ac21a115a4b5")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_1_mt_5g_nsa_volte_esim_port_0_5g_nsa_volte_dds_1(self):
        """A MT VoLTE call dialed at eSIM port 1, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 0 (SimSlotInfo.SLOT_1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_2,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="d61c930e-4fca-46f6-a946-753ddfbd0a46")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_0_mo_5g_nsa_volte_esim_port_1_5g_nsa_volte_dds_2(self):
        """A MO VoLTE call dialed at eSIM port 0, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 1 (SimSlotInfo.SLOT_2)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=2,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="c157cad9-7d04-40fb-9fe5-c9d691d6b7e9")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_0_mt_5g_nsa_volte_esim_port_1_5g_nsa_volte_dds_2(self):
        """A MT VoLTE call dialed at eSIM port 0, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 1 (SimSlotInfo.SLOT_2)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=2,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="acaa152c-4e0a-4caa-86a6-6689ff519e9e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_1_mo_5g_nsa_volte_esim_port_0_5g_nsa_volte_dds_2(self):
        """A MO VoLTE call dialed at eSIM port 1, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 1 (SimSlotInfo.SLOT_2)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=2,
            test_slot=SimSlotInfo.SLOT_2,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="d8f45a79-e86b-4292-96a0-fc32067b1d90")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_1_mt_5g_nsa_volte_esim_port_0_5g_nsa_volte_dds_2(self):
        """A MT VoLTE call dialed at eSIM port 1, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 1 (SimSlotInfo.SLOT_2)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=2,
            test_slot=SimSlotInfo.SLOT_2,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="fa199595-9081-4a1b-b0ce-dcff190dc403")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_0_mo_5g_nsa_volte_esim_port_1_4g_volte_dds_1(self):
        """A MO VoLTE call dialed at eSIM port 0, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 0 (SimSlotInfo.SLOT_1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="a6d3f4b4-085e-4ca4-94f7-d1bcccdaaa03")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_0_mt_5g_nsa_volte_esim_port_1_4g_volte_dds_1(self):
        """A MT VoLTE call dialed at eSIM port 0, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 0 (SimSlotInfo.SLOT_1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="22c202e0-b488-4700-acbe-3c3f53f6d7f1")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_1_mo_4g_volte_esim_port_0_5g_nsa_volte_dds_1(self):
        """A MO VoLTE call dialed at eSIM port 1, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 0 (SimSlotInfo.SLOT_1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_2,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="4a6de885-b092-42ae-9209-7b4d2407034c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_1_mt_4g_volte_esim_port_0_5g_nsa_volte_dds_1(self):
        """A MT VoLTE call dialed at eSIM port 1, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 0 (SimSlotInfo.SLOT_1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_2,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="003d9ffe-c057-4191-8289-3419de874c02")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_0_mo_5g_nsa_volte_esim_port_1_4g_volte_dds_2(self):
        """A MO VoLTE call dialed at eSIM port 0, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 1 (SimSlotInfo.SLOT_2)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "volte"],
            dds_slot=2,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="	c4fdc99c-1d6c-4466-b811-ca948a48b53c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_0_mt_5g_nsa_volte_esim_port_1_4g_volte_dds_2(self):
        """A MT VoLTE call dialed at eSIM port 0, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 1 (SimSlotInfo.SLOT_2)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "volte"],
            dds_slot=2,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="95c6b512-2dbc-4435-a0ba-133d79ace3bc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_1_mo_4g_volte_esim_port_0_5g_nsa_volte_dds_2(self):
        """A MO VoLTE call dialed at eSIM port 1, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 1 (SimSlotInfo.SLOT_2)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "volte"],
            dds_slot=2,
            test_slot=SimSlotInfo.SLOT_2,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="a51d65d2-00e1-489c-8ede-132c4449638c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_1_mt_4g_volte_esim_port_0_5g_nsa_volte_dds_2(self):
        """A MT VoLTE call dialed at eSIM port 1, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 1 (SimSlotInfo.SLOT_2)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["5g_volte", "volte"],
            dds_slot=2,
            test_slot=SimSlotInfo.SLOT_2,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="c0108b40-a7dd-482e-88c5-910996e0e35c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_0_mo_4g_volte_esim_port_1_5g_nsa_volte_dds_1(self):
        """A MO VoLTE call dialed at eSIM port 0, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 0 (SimSlotInfo.SLOT_1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="077db5cb-f41e-4ccd-a921-217b37eeab93")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_0_mt_4g_volte_esim_port_1_5g_nsa_volte_dds_1(self):
        """A MT VoLTE call dialed at eSIM port 0, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 0 (SimSlotInfo.SLOT_1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="a6d910c7-bbf5-4cec-b0c3-e08f0c9ab9e8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_1_mo_5g_nsa_volte_esim_port_0_4g_volte_dds_1(self):
        """A MO VoLTE call dialed at eSIM port 1, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 0 (SimSlotInfo.SLOT_1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_2,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="25678592-49aa-4070-a889-4193ab02d575")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_1_mt_5g_nsa_volte_esim_port_0_4g_volte_dds_1(self):
        """A MT VoLTE call dialed at eSIM port 1, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 0 (SimSlotInfo.SLOT_1)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_2,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="610a4a93-1db5-4938-82b1-cdbcd0de9140")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_0_mo_4g_volte_esim_port_1_5g_nsa_volte_dds_2(self):
        """A MO VoLTE call dialed at eSIM port 0, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 1 (SimSlotInfo.SLOT_2)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "5g_volte"],
            dds_slot=2,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="ac2d2dcd-cc0d-4500-b27b-3806a7f008dd")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_0_mt_4g_volte_esim_port_1_5g_nsa_volte_dds_2(self):
        """A MT VoLTE call dialed at eSIM port 0, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 1 (SimSlotInfo.SLOT_2)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "5g_volte"],
            dds_slot=2,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="b5dfe6dc-a9cd-46d6-a227-359cc3ac05cc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_1_mo_5g_nsa_volte_esim_port_0_4g_volte_dds_2(self):
        """A MO VoLTE call dialed at eSIM port 1, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 1 (SimSlotInfo.SLOT_2)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "5g_volte"],
            dds_slot=2,
            test_slot=SimSlotInfo.SLOT_2,
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="1449a187-d388-4b74-bd8f-d45827441604")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_port_1_mt_5g_nsa_volte_esim_port_0_4g_volte_dds_2(self):
        """A MT VoLTE call dialed at eSIM port 1, where
            - eSIM port 0 5G NSA VoLTE
            - eSIM port 1 5G NSA VoLTE
            - DDS at eSIM port 1 (SimSlotInfo.SLOT_2)
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "5g_volte"],
            dds_slot=2,
            test_slot=SimSlotInfo.SLOT_2,
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="f94d5fd2-79ac-426a-9a0d-1ba72e070b19")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_volte_psim_5g_nsa_volte_disable_psim(self):
        """Disable/enable pSIM with MO voice call
        Test step:
            1. Set the RAT to 5G at both slots.
            2. Disable pSIM.
            3. Switch DDS to eSIM.
            4. Verify RAT at slot 1 (eSIM) and also internet connection.
            5. Make a MO voice call.
            6. Enable pSIM.
            7. Switch DDS to pSIM.
            8. Verify RAT at slot 0 (pSIM) and also internet connection.
        """
        return enable_slot_after_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            0,
            mo_rat=["5g_volte", "5g_volte"],
            call_direction="mo")

    @test_tracker_info(uuid="3b58146a-72d2-4544-b50b-f685d10da20a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_volte_psim_5g_nsa_volte_disable_psim(self):
        """Disable/enable pSIM with MT voice call
        Test step:
            1. Set the RAT to 5G at both slots.
            2. Disable pSIM.
            3. Switch DDS to eSIM.
            4. Verify RAT at slot 1 (eSIM) and also internet connection.
            5. Make a MT voice call.
            6. Enable pSIM.
            7. Switch DDS to pSIM.
            8. Verify RAT at slot 0 (pSIM) and also internet connection.
        """
        return enable_slot_after_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            0,
            mt_rat=["5g_volte", "5g_volte"],
            call_direction="mt")

    @test_tracker_info(uuid="6b7fde1b-d51a-49df-b7d4-bf5e3d091895")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_data_esim_5g_nsa_volte_psim_5g_nsa_volte_disable_psim(self):
        """Disable/enable pSIM with data call
        Test step:
            1. Set the RAT to 5G at both slots.
            2. Disable pSIM.
            3. Switch DDS to eSIM.
            4. Verify RAT at slot 1 (eSIM) and also internet connection.
            5. Make a data call by http download.
            6. Enable pSIM.
            7. Switch DDS to pSIM.
            8. Verify RAT at slot 0 (pSIM) and also internet connection.
        """
        return enable_slot_after_data_call_test(
            self.log,
            self.android_devices[0],
            0,
            rat=["5g_volte", "5g_volte"])

    @test_tracker_info(uuid="dc490360-66b6-4796-a649-73bb09ce0cc1")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_volte_esim_5g_nsa_volte_disable_esim(self):
        """Disable/enable eSIM with MO voice call
        Test step:
            1. Set the RAT to 5G at both slots.
            2. Disable eSIM.
            3. Switch DDS to pSIM.
            4. Verify RAT at slot 0 (pSIM) and also internet connection.
            5. Make a MO voice call.
            6. Enable eSIM.
            7. Switch DDS to eSIM.
            8. Verify RAT at slot 1 (eSIM) and also internet connection.
        """
        return enable_slot_after_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            1,
            mo_rat=["5g_volte", "5g_volte"],
            call_direction="mo")

    @test_tracker_info(uuid="63f57c95-75be-4a51-83c6-609356bb301b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_volte_esim_5g_nsa_volte_disable_esim(self):
        """Disable/enable eSIM with MT voice call
        Test step:
            1. Set the RAT to 5G at both slots.
            2. Disable eSIM.
            3. Switch DDS to pSIM.
            4. Verify RAT at slot 0 (pSIM) and also internet connection.
            5. Make a MT voice call.
            6. Enable eSIM.
            7. Switch DDS to eSIM.
            8. Verify RAT at slot 1 (eSIM) and also internet connection.
        """
        return enable_slot_after_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            1,
            mt_rat=["5g_volte", "5g_volte"],
            call_direction="mt")

    @test_tracker_info(uuid="7ad9e84a-dfa0-44e2-adde-390ae521b50b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_data_psim_5g_nsa_volte_esim_5g_nsa_volte_disable_esim(self):
        """Disable/enable eSIM with data call
        Test step:
            1. Set the RAT to 5G at both slots.
            2. Disable eSIM.
            3. Switch DDS to pSIM.
            4. Verify RAT at slot 0 (pSIM) and also internet connection.
            5. Make a data call by http download.
            6. Enable eSIM.
            7. Switch DDS to eSIM.
            8. Verify RAT at slot 1 (eSIM) and also internet connection.
        """
        return enable_slot_after_data_call_test(
            self.log,
            self.android_devices[0],
            1,
            rat=["5g_volte", "5g_volte"])

    @test_tracker_info(uuid="b1b02578-6e75-4a96-b3f3-c724fafbae2a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_volte_psim_4g_volte_disable_psim(self):
        """Disable/enable pSIM with MO voice call
        Test step:
            1. Set the RAT to LTE at slot 0 and 5G at slot 1.
            2. Disable pSIM.
            3. Switch DDS to eSIM.
            4. Verify RAT at slot 1 (eSIM) and also internet connection.
            5. Make a MO voice call.
            6. Enable pSIM.
            7. Switch DDS to pSIM.
            8. Verify RAT at slot 0 (pSIM) and also internet connection.
        """
        return enable_slot_after_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            0,
            mo_rat=["volte", "5g_volte"],
            call_direction="mo")

    @test_tracker_info(uuid="15bb2fdd-ec38-47dc-a2f0-3251c8d19e3c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_volte_psim_4g_volte_disable_psim(self):
        """Disable/enable pSIM with MT voice call
        Test step:
            1. Set the RAT to LTE at slot 0 and 5G at slot 1.
            2. Disable pSIM.
            3. Switch DDS to eSIM.
            4. Verify RAT at slot 1 (eSIM) and also internet connection.
            5. Make a MT voice call.
            6. Enable pSIM.
            7. Switch DDS to pSIM.
            8. Verify RAT at slot 0 (pSIM) and also internet connection.
        """
        return enable_slot_after_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            0,
            mt_rat=["volte", "5g_volte"],
            call_direction="mt")

    @test_tracker_info(uuid="37d5e72b-723f-4a26-87e9-cf54726476a6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_data_esim_5g_nsa_volte_psim_4g_volte_disable_psim(self):
        """Disable/enable pSIM with data call
        Test step:
            1. Set the RAT to LTE at slot 0 and 5G at slot 1.
            2. Disable pSIM.
            3. Switch DDS to eSIM.
            4. Verify RAT at slot 1 (eSIM) and also internet connection.
            5. Make a data call by http download.
            6. Enable pSIM.
            7. Switch DDS to pSIM.
            8. Verify RAT at slot 0 (pSIM) and also internet connection.
        """
        return enable_slot_after_data_call_test(
            self.log,
            self.android_devices[0],
            0,
            rat=["volte", "5g_volte"])

    @test_tracker_info(uuid="bc0dea98-cfe7-4cdd-8dd9-84eda4212fd4")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_4g_volte_esim_5g_nsa_volte_disable_esim(self):
        """Disable/enable eSIM with MO voice call
        Test step:
            1. Set the RAT to LTE at slot 0 and 5G at slot 1.
            2. Disable eSIM.
            3. Switch DDS to pSIM.
            4. Verify RAT at slot 0 (pSIM) and also internet connection.
            5. Make a MO voice call.
            6. Enable eSIM.
            7. Switch DDS to eSIM.
            8. Verify RAT at slot 1 (eSIM) and also internet connection.
        """
        return enable_slot_after_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            1,
            mo_rat=["volte", "5g_volte"],
            call_direction="mo")

    @test_tracker_info(uuid="cfb8b670-6049-46fd-88ff-b9565ab2b582")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_4g_volte_esim_5g_nsa_volte_disable_esim(self):
        """Disable/enable eSIM with MT voice call
        Test step:
            1. Set the RAT to LTE at slot 0 and 5G at slot 1.
            2. Disable eSIM.
            3. Switch DDS to pSIM.
            4. Verify RAT at slot 0 (pSIM) and also internet connection.
            5. Make a MT voice call.
            6. Enable eSIM.
            7. Switch DDS to eSIM.
            8. Verify RAT at slot 1 (eSIM) and also internet connection.
        """
        return enable_slot_after_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            1,
            mt_rat=["volte", "5g_volte"],
            call_direction="mt")

    @test_tracker_info(uuid="e2a18907-d9a4-491b-82c4-11ca86fc7129")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_data_psim_4g_volte_esim_5g_nsa_volte_disable_esim(self):
        """Disable/enable eSIM with data call
        Test step:
            1. Set the RAT to LTE at slot 0 and 5G at slot 1.
            2. Disable eSIM.
            3. Switch DDS to pSIM.
            4. Verify RAT at slot 0 (pSIM) and also internet connection.
            5. Make a data call by http download.
            6. Enable eSIM.
            7. Switch DDS to eSIM.
            8. Verify RAT at slot 1 (eSIM) and also internet connection.
        """
        return enable_slot_after_data_call_test(
            self.log,
            self.android_devices[0],
            1,
            rat=["volte", "5g_volte"])

    @test_tracker_info(uuid="13220595-9774-4f62-b1fb-3b6b98b51df3")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_4g_volte_psim_5g_nsa_volte_disable_psim(self):
        """Disable/enable pSIM with MO voice call
        Test step:
            1. Set the RAT to 5G at slot 0 and LTE at slot 1.
            2. Disable pSIM.
            3. Switch DDS to eSIM.
            4. Verify RAT at slot 1 (eSIM) and also internet connection.
            5. Make a MO voice call.
            6. Enable pSIM.
            7. Switch DDS to pSIM.
            8. Verify RAT at slot 0 (pSIM) and also internet connection.
        """
        return enable_slot_after_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            0,
            mo_rat=["5g_volte", "volte"],
            call_direction="mo")

    @test_tracker_info(uuid="57e3e643-26a9-4e32-ac55-a0f7e4a72148")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_4g_volte_psim_5g_nsa_volte_disable_psim(self):
        """Disable/enable pSIM with MT voice call
        Test step:
            1. Set the RAT to 5G at slot 0 and LTE at slot 1.
            2. Disable pSIM.
            3. Switch DDS to eSIM.
            4. Verify RAT at slot 1 (eSIM) and also internet connection.
            5. Make a MT voice call.
            6. Enable pSIM.
            7. Switch DDS to pSIM.
            8. Verify RAT at slot 0 (pSIM) and also internet connection.
        """
        return enable_slot_after_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            0,
            mt_rat=["5g_volte", "volte"],
            call_direction="mt")

    @test_tracker_info(uuid="3d809e0d-e75e-4ccd-af38-80d465d14eb7")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_data_esim_4g_volte_psim_5g_nsa_volte_disable_psim(self):
        """Disable/enable pSIM with data call
        Test step:
            1. Set the RAT to 5G at slot 0 and LTE at slot 1.
            2. Disable pSIM.
            3. Switch DDS to eSIM.
            4. Verify RAT at slot 1 (eSIM) and also internet connection.
            5. Make a data call by http download.
            6. Enable pSIM.
            7. Switch DDS to pSIM.
            8. Verify RAT at slot 0 (pSIM) and also internet connection.
        """
        return enable_slot_after_data_call_test(
            self.log,
            self.android_devices[0],
            0,
            rat=["5g_volte", "volte"])

    @test_tracker_info(uuid="c54ab348-367f-43c9-9aae-fa2c3d3badec")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_volte_esim_4g_volte_disable_esim(self):
        """Disable/enable eSIM with MO voice call
        Test step:
            1. Set the RAT to 5G at slot 0 and LTE at slot 1.
            2. Disable eSIM.
            3. Switch DDS to pSIM.
            4. Verify RAT at slot 0 (pSIM) and also internet connection.
            5. Make a MO voice call.
            6. Enable eSIM.
            7. Switch DDS to eSIM.
            8. Verify RAT at slot 1 (eSIM) and also internet connection.
        """
        return enable_slot_after_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            1,
            mo_rat=["5g_volte", "volte"],
            call_direction="mo")

    @test_tracker_info(uuid="db8e5cdc-c34f-48d4-8ebe-2a71e03c159f")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_volte_esim_4g_volte_disable_esim(self):
        """Disable/enable eSIM with MT voice call
        Test step:
            1. Set the RAT to 5G at slot 0 and LTE at slot 1.
            2. Disable eSIM.
            3. Switch DDS to pSIM.
            4. Verify RAT at slot 0 (pSIM) and also internet connection.
            5. Make a MT voice call.
            6. Enable eSIM.
            7. Switch DDS to eSIM.
            8. Verify RAT at slot 1 (eSIM) and also internet connection.
        """
        return enable_slot_after_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            1,
            mt_rat=["5g_volte", "volte"],
            call_direction="mt")

    @test_tracker_info(uuid="a271d5f2-4449-4961-8417-14943fa96144")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_data_psim_5g_nsa_volte_esim_4g_volte_disable_esim(self):
        """Disable/enable eSIM with data call
        Test step:
            1. Set the RAT to 5G at slot 0 and LTE at slot 1.
            2. Disable eSIM.
            3. Switch DDS to pSIM.
            4. Verify RAT at slot 0 (pSIM) and also internet connection.
            5. Make a data call by http download.
            6. Enable eSIM.
            7. Switch DDS to eSIM.
            8. Verify RAT at slot 1 (eSIM) and also internet connection.
        """
        return enable_slot_after_data_call_test(
            self.log,
            self.android_devices[0],
            1,
            rat=["5g_volte", "volte"])

    @test_tracker_info(uuid="f86faed8-5259-4e5d-9e49-40618ad41670")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_wfc_wifi_preferred_esim_5g_nsa_volte_dds_0(self):
        """ A MO vowifi call at pSIM, where
            - pSIM 5G WFC in Wi-Fi preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            0,
            mo_rat=["5g_wfc", "5g_volte"],
            call_direction="mo",
            wfc_mode = [WFC_MODE_WIFI_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="1e13a8be-7ddd-4177-89cb-720d305d766e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_wfc_wifi_preferred_esim_5g_nsa_volte_dds_0(self):
        """ A MT vowifi call at pSIM, where
            - pSIM 5G WFC in Wi-Fi preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            0,
            mt_rat=["5g_wfc", "5g_volte"],
            call_direction="mt",
            wfc_mode = [WFC_MODE_WIFI_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="d32696a1-6e6d-48ca-8612-06e24645cfc6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_wfc_wifi_preferred_psim_5g_nsa_volte_dds_0(self):
        """ A MO vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G WFC in Wi-Fi preferred mode
            - DDS at pSIM (slot 0)

            Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            0,
            mo_rat=["5g_volte", "5g_wfc"],
            call_direction="mo",
            wfc_mode = [None, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="9ed35291-ae87-469a-a12f-8df2c17daa6e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_wfc_wifi_preferred_psim_5g_nsa_volte_dds_0(self):
        """ A MT vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G WFC in Wi-Fi preferred mode
            - DDS at pSIM (slot 0)

            Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            0,
            mt_rat=["5g_volte", "5g_wfc"],
            call_direction="mt",
            wfc_mode = [None, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="a44352d0-8ded-4e42-bd77-59c9f5801954")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_wfc_wifi_preferred_esim_5g_nsa_volte_dds_1(self):
        """ A MO vowifi call at pSIM, where
            - pSIM 5G WFC in Wi-Fi preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)

            Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            1,
            mo_rat=["5g_wfc", "5g_volte"],
            call_direction="mo",
            wfc_mode = [WFC_MODE_WIFI_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="6e99a297-6deb-4674-90e1-1f703971501a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_wfc_wifi_preferred_esim_5g_nsa_volte_dds_1(self):
        """ A MT vowifi call at pSIM, where
            - pSIM 5G WFC in Wi-Fi preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)

            Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            1,
            mt_rat=["5g_wfc", "5g_volte"],
            call_direction="mt",
            wfc_mode = [WFC_MODE_WIFI_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="119f71a5-9d5d-4c66-b958-684672a95a87")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_wfc_wifi_preferred_psim_5g_nsa_volte_dds_1(self):
        """ A MO vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G WFC in Wi-Fi preferred mode
            - DDS at eSIM (slot 1)

            Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            1,
            mo_rat=["5g_volte", "5g_wfc"],
            call_direction="mo",
            wfc_mode = [None, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="55e651d2-4112-4fe9-a70d-f448287b078b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_wfc_wifi_preferred_psim_5g_nsa_volte_dds_1(self):
        """ A MT vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G WFC in Wi-Fi preferred mode
            - DDS at eSIM (slot 1)

            Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            1,
            mt_rat=["5g_volte", "5g_wfc"],
            call_direction="mt",
            wfc_mode = [None, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="10ce825e-8ed8-4bc8-a70f-e0822d391066")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_4g_wfc_wifi_preferred_esim_5g_nsa_volte_dds_0(self):
        """ A MO vowifi call at pSIM, where
            - pSIM 4G WFC in Wi-Fi preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            0,
            mo_rat=["wfc", "5g_volte"],
            call_direction="mo",
            wfc_mode = [WFC_MODE_WIFI_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="39cb207c-10e5-4f6a-8ee4-0f26634070cb")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_4g_wfc_wifi_preferred_esim_5g_nsa_volte_dds_0(self):
        """ A MT vowifi call at pSIM, where
            - pSIM 4G WFC in Wi-Fi preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            0,
            mt_rat=["wfc", "5g_volte"],
            call_direction="mt",
            wfc_mode = [WFC_MODE_WIFI_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="a2245d31-c3ca-42bf-a6ca-72f3d3dc32e9")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_wfc_wifi_preferred_psim_4g_volte_dds_0(self):
        """ A MO vowifi call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G WFC in Wi-Fi preferred mode
            - DDS at pSIM (slot 0)

            Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            0,
            mo_rat=["volte", "5g_wfc"],
            call_direction="mo",
            wfc_mode = [None, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="2d683601-b604-4dba-b5b8-8aec86d70f95")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_wfc_wifi_preferred_psim_4g_volte_dds_0(self):
        """ A MT vowifi call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G WFC in Wi-Fi preferred mode
            - DDS at pSIM (slot 0)

            Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            0,
            mt_rat=["volte", "5g_wfc"],
            call_direction="mt",
            wfc_mode = [None, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="7c79782e-e273-43a4-9176-48a7b5a8cb85")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_4g_wfc_wifi_preferred_esim_5g_nsa_volte_dds_1(self):
        """ A MO vowifi call at pSIM, where
            - pSIM 4G WFC in Wi-Fi preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)

            Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            1,
            mo_rat=["wfc", "5g_volte"],
            call_direction="mo",
            wfc_mode = [WFC_MODE_WIFI_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="1b9c6b37-2345-46af-a6eb-48ebd962c953")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_4g_wfc_wifi_preferred_esim_5g_nsa_volte_dds_1(self):
        """ A MT vowifi call at pSIM, where
            - pSIM 4G WFC in Wi-Fi preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)

            Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            1,
            mt_rat=["wfc", "5g_volte"],
            call_direction="mt",
            wfc_mode = [WFC_MODE_WIFI_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="ce37ebda-f83b-4482-9214-74e82e04ae7f")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_wfc_wifi_preferred_psim_4g_volte_dds_1(self):
        """ A MO vowifi call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G WFC in Wi-Fi preferred mode
            - DDS at eSIM (slot 1)

            Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            1,
            mo_rat=["volte", "5g_wfc"],
            call_direction="mo",
            wfc_mode = [None, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="3ba071ba-bf6f-4c27-ae82-557dabb60291")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_wfc_wifi_preferred_psim_4g_volte_dds_1(self):
        """ A MT vowifi call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G WFC in Wi-Fi preferred mode
            - DDS at eSIM (slot 1)

            Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            1,
            mt_rat=["volte", "5g_wfc"],
            call_direction="mt",
            wfc_mode = [None, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="1385939f-272e-4ba7-ba5d-de1bff60ad01")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_wfc_wifi_preferred_esim_4g_volte_dds_0(self):
        """ A MO vowifi call at pSIM, where
            - pSIM 5G WFC in Wi-Fi preferred mode
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)

            Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            0,
            mo_rat=["5g_wfc", "volte"],
            call_direction="mo",
            wfc_mode = [WFC_MODE_WIFI_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="79d24164-bbcc-49c6-a538-99b39b65749b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_wfc_wifi_preferred_esim_4g_volte_dds_0(self):
        """ A MT vowifi call at pSIM, where
            - pSIM 5G WFC in Wi-Fi preferred mode
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)

            Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            0,
            mt_rat=["5g_wfc", "volte"],
            call_direction="mt",
            wfc_mode = [WFC_MODE_WIFI_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="837186d2-fe35-4d4a-900d-0bc5b71829b7")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_4g_wfc_wifi_preferred_psim_5g_nsa_volte_dds_0(self):
        """ A MO vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G WFC in Wi-Fi preferred mode
            - DDS at pSIM (slot 0)

            Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            0,
            mo_rat=["5g_volte", "wfc"],
            call_direction="mo",
            wfc_mode = [None, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="ace4d07a-07ba-4868-bfa1-c82a81bce4c9")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_4g_wfc_wifi_preferred_psim_5g_nsa_volte_dds_0(self):
        """ A MT vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G WFC in Wi-Fi preferred mode
            - DDS at pSIM (slot 0)

            Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            0,
            mt_rat=["5g_volte", "wfc"],
            call_direction="mt",
            wfc_mode = [None, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="17306fd2-842e-47d9-bd83-e5a34fce1d5a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_wfc_wifi_preferred_esim_4g_volte_dds_1(self):
        """ A MO vowifi call at pSIM, where
            - pSIM 5G WFC in Wi-Fi preferred mode
            - eSIM 4G VoLTE
            - DDS at eSIM (slot 1)

            Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            1,
            mo_rat=["5g_wfc", "volte"],
            call_direction="mo",
            wfc_mode = [WFC_MODE_WIFI_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="352b0f73-f89a-45cf-9810-147e8a1b1522")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_wfc_wifi_preferred_esim_4g_volte_dds_1(self):
        """ A MT vowifi call at pSIM, where
            - pSIM 5G WFC in Wi-Fi preferred mode
            - eSIM 4G VoLTE
            - DDS at eSIM (slot 1)

            Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            1,
            mt_rat=["5g_wfc", "volte"],
            call_direction="mt",
            wfc_mode = [WFC_MODE_WIFI_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="4a574fee-dc59-45a6-99a6-18098053adf3")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_4g_wfc_wifi_preferred_psim_5g_nsa_volte_dds_1(self):
        """ A MO vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G WFC in Wi-Fi preferred mode
            - DDS at eSIM (slot 1)

            Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            1,
            mo_rat=["5g_volte", "wfc"],
            call_direction="mo",
            wfc_mode = [None, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="c70a2aa8-5567-4f74-9a2c-a24214d6af74")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_4g_wfc_wifi_preferred_psim_5g_nsa_volte_dds_1(self):
        """ A MT vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G WFC in Wi-Fi preferred mode
            - DDS at eSIM (slot 1)

            Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            1,
            mt_rat=["5g_volte", "wfc"],
            call_direction="mt",
            wfc_mode = [None, WFC_MODE_WIFI_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True)

    @test_tracker_info(uuid="1f5f9721-0dbb-443d-b54f-2e4acdc2e1a6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_wfc_cellular_preferred_esim_5g_nsa_volte_dds_0(self):
        """ A MO vowifi call at pSIM, where
            - pSIM 5G WFC in cellular preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            0,
            mo_rat=["5g_wfc", "5g_volte"],
            call_direction="mo",
            is_airplane_mode=True,
            wfc_mode = [WFC_MODE_CELLULAR_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="00723b82-3fa5-4263-b56f-a27ba76f24bd")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_wfc_cellular_preferred_esim_5g_nsa_volte_dds_0(self):
        """ A MT vowifi call at pSIM, where
            - pSIM 5G WFC in cellular preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            0,
            mt_rat=["5g_wfc", "5g_volte"],
            call_direction="mt",
            is_airplane_mode=True,
            wfc_mode = [WFC_MODE_CELLULAR_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="5d024b1c-e345-45e8-9759-9f8729799a05")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_wfc_cellular_preferred_psim_5g_nsa_volte_dds_0(self):
        """ A MO vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G WFC in cellular preferred mode
            - DDS at pSIM (slot 0)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            0,
            mo_rat=["5g_volte", "5g_wfc"],
            call_direction="mo",
            is_airplane_mode=True,
            wfc_mode = [None, WFC_MODE_CELLULAR_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="9627755c-3dea-4296-8140-eac0037c4f17")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_wfc_cellular_preferred_psim_5g_nsa_volte_dds_0(self):
        """ A MT vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G WFC in cellular preferred mode
            - DDS at pSIM (slot 0)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            0,
            mt_rat=["5g_volte", "5g_wfc"],
            call_direction="mt",
            is_airplane_mode=True,
            wfc_mode = [None, WFC_MODE_CELLULAR_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="aeddb446-8ec1-4692-9b6c-417aa89205eb")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_wfc_cellular_preferred_esim_5g_nsa_volte_dds_1(self):
        """ A MO vowifi call at pSIM, where
            - pSIM 5G WFC in cellular preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            1,
            mo_rat=["5g_wfc", "5g_volte"],
            call_direction="mo",
            is_airplane_mode=True,
            wfc_mode = [WFC_MODE_CELLULAR_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="4e56a128-0706-4f48-a031-93c77faa5e5a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_wfc_cellular_preferred_esim_5g_nsa_volte_dds_1(self):
        """ A MT vowifi call at pSIM, where
            - pSIM 5G WFC in cellular preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            1,
            mt_rat=["5g_wfc", "5g_volte"],
            call_direction="mt",
            is_airplane_mode=True,
            wfc_mode = [WFC_MODE_CELLULAR_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="8adbe013-4f93-4778-8f82-f7db3be8c318")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_wfc_cellular_preferred_psim_5g_nsa_volte_dds_1(self):
        """ A MO vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G WFC in cellular preferred mode
            - DDS at eSIM (slot 1)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            1,
            mo_rat=["5g_volte", "5g_wfc"],
            call_direction="mo",
            is_airplane_mode=True,
            wfc_mode = [None, WFC_MODE_CELLULAR_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="75fe4f90-8945-4886-92ad-29d0d536163d")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_wfc_cellular_preferred_psim_5g_nsa_volte_dds_1(self):
        """ A MT vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G WFC in cellular preferred mode
            - DDS at eSIM (slot 1)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            1,
            mt_rat=["5g_volte", "5g_wfc"],
            call_direction="mt",
            is_airplane_mode=True,
            wfc_mode = [None, WFC_MODE_CELLULAR_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="25716018-d4cc-4b62-ac00-77d34b3920e1")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_4g_wfc_cellular_preferred_esim_5g_nsa_volte_dds_0(self):
        """ A MO vowifi call at pSIM, where
            - pSIM 4G WFC in cellular preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            0,
            mo_rat=["wfc", "5g_volte"],
            call_direction="mo",
            is_airplane_mode=True,
            wfc_mode = [WFC_MODE_CELLULAR_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="ae7a19bb-f257-4853-83ff-25dd70696d76")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_4g_wfc_cellular_preferred_esim_5g_nsa_volte_dds_0(self):
        """ A MT vowifi call at pSIM, where
            - pSIM 4G WFC in cellular preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            0,
            mt_rat=["wfc", "5g_volte"],
            call_direction="mt",
            is_airplane_mode=True,
            wfc_mode = [WFC_MODE_CELLULAR_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="c498a7fc-8c5d-4b5d-bd9e-47bd77032765")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_wfc_cellular_preferred_psim_4g_volte_dds_0(self):
        """ A MO vowifi call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G WFC in cellular preferred mode
            - DDS at pSIM (slot 0)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            0,
            mo_rat=["volte", "5g_wfc"],
            call_direction="mo",
            is_airplane_mode=True,
            wfc_mode = [None, WFC_MODE_CELLULAR_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="ce7b23af-41f1-4977-a140-6e1a456487dc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_wfc_cellular_preferred_psim_4g_volte_dds_0(self):
        """ A MT vowifi call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G WFC in cellular preferred mode
            - DDS at pSIM (slot 0)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            0,
            mt_rat=["volte", "5g_wfc"],
            call_direction="mt",
            is_airplane_mode=True,
            wfc_mode = [None, WFC_MODE_CELLULAR_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="808fab1e-1fe7-406a-b479-8e9e6a5c2ef5")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_4g_wfc_cellular_preferred_esim_5g_nsa_volte_dds_1(self):
        """ A MO vowifi call at pSIM, where
            - pSIM 4G WFC in cellular preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            1,
            mo_rat=["wfc", "5g_volte"],
            call_direction="mo",
            is_airplane_mode=True,
            wfc_mode = [WFC_MODE_CELLULAR_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="4b0f73a8-a508-4e77-aca2-0155b54b4e2c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_4g_wfc_cellular_preferred_esim_5g_nsa_volte_dds_1(self):
        """ A MT vowifi call at pSIM, where
            - pSIM 4G WFC in cellular preferred mode
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            1,
            mt_rat=["wfc", "5g_volte"],
            call_direction="mt",
            is_airplane_mode=True,
            wfc_mode = [WFC_MODE_CELLULAR_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="4a73fdb3-abf3-4094-9317-74b758991c0a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_5g_nsa_wfc_cellular_preferred_psim_4g_volte_dds_1(self):
        """ A MO vowifi call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G WFC in cellular preferred mode
            - DDS at eSIM (slot 1)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            1,
            mo_rat=["volte", "5g_wfc"],
            call_direction="mo",
            is_airplane_mode=True,
            wfc_mode = [None, WFC_MODE_CELLULAR_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="5247d0dc-2d60-4760-8c27-a9b358992849")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_5g_nsa_wfc_cellular_preferred_psim_4g_volte_dds_1(self):
        """ A MT vowifi call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G WFC in cellular preferred mode
            - DDS at eSIM (slot 1)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            1,
            mt_rat=["volte", "5g_wfc"],
            call_direction="mt",
            is_airplane_mode=True,
            wfc_mode = [None, WFC_MODE_CELLULAR_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="df037a28-c130-4d00-ba2e-28723af26128")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_wfc_cellular_preferred_esim_4g_volte_dds_0(self):
        """ A MO vowifi call at pSIM, where
            - pSIM 5G WFC in cellular preferred mode
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            0,
            mo_rat=["5g_wfc", "volte"],
            call_direction="mo",
            is_airplane_mode=True,
            wfc_mode = [WFC_MODE_CELLULAR_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="900a7a74-064b-43df-b40a-8257ea9a1598")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_wfc_cellular_preferred_esim_4g_volte_dds_0(self):
        """ A MT vowifi call at pSIM, where
            - pSIM 5G WFC in cellular preferred mode
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            0,
            mt_rat=["5g_wfc", "volte"],
            call_direction="mt",
            is_airplane_mode=True,
            wfc_mode = [WFC_MODE_CELLULAR_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="08057239-a1de-42e5-8ff2-560d6a7a7e35")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_4g_wfc_cellular_preferred_psim_5g_nsa_volte_dds_0(self):
        """ A MO vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G WFC in cellular preferred mode
            - DDS at pSIM (slot 0)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            0,
            mo_rat=["5g_volte", "wfc"],
            call_direction="mo",
            is_airplane_mode=True,
            wfc_mode = [None, WFC_MODE_CELLULAR_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="edd15dd4-4abe-4de0-905e-6dd2aebf2697")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_4g_wfc_cellular_preferred_psim_5g_nsa_volte_dds_0(self):
        """ A MT vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G WFC in cellular preferred mode
            - DDS at pSIM (slot 0)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            0,
            mt_rat=["5g_volte", "wfc"],
            call_direction="mt",
            is_airplane_mode=True,
            wfc_mode = [None, WFC_MODE_CELLULAR_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="c230b98b-fbe2-4fc5-b0a0-cc91c5613ade")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mo_5g_nsa_wfc_cellular_preferred_esim_4g_volte_dds_1(self):
        """ A MO vowifi call at pSIM, where
            - pSIM 5G WFC in cellular preferred mode
            - eSIM 4G VoLTE
            - DDS at eSIM (slot 1)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            1,
            mo_rat=["5g_wfc", "volte"],
            call_direction="mo",
            is_airplane_mode=True,
            wfc_mode = [WFC_MODE_CELLULAR_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="1e0eb29c-4850-4f42-b83f-d831305eeaa7")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_psim_mt_5g_nsa_wfc_cellular_preferred_esim_4g_volte_dds_1(self):
        """ A MT vowifi call at pSIM, where
            - pSIM 5G WFC in cellular preferred mode
            - eSIM 4G VoLTE
            - DDS at eSIM (slot 1)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the pSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            1,
            mt_rat=["5g_wfc", "volte"],
            call_direction="mt",
            is_airplane_mode=True,
            wfc_mode = [WFC_MODE_CELLULAR_PREFERRED, None],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="2fd7d04f-1ce7-40d0-86f1-ebf042dfad8b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mo_4g_wfc_cellular_preferred_psim_5g_nsa_volte_dds_1(self):
        """ A MO vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G WFC in cellular preferred mode
            - DDS at eSIM (slot 1)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            1,
            mo_rat=["5g_volte", "wfc"],
            call_direction="mo",
            is_airplane_mode=True,
            wfc_mode = [None, WFC_MODE_CELLULAR_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="fd3bace9-f9ce-4870-8818-74f9b1605716")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_voice_esim_mt_4g_wfc_cellular_preferred_psim_5g_nsa_volte_dds_1(self):
        """ A MT vowifi call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G WFC in cellular preferred mode
            - DDS at eSIM (slot 1)
            - Airplane mode

            Airplane mode and Wi-Fi will be turned off in the end to ensure the eSIM will attach to
            the network with assigned RAT successfully.
        """
        return dsds_voice_call_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            1,
            mt_rat=["5g_volte", "wfc"],
            call_direction="mt",
            is_airplane_mode=True,
            wfc_mode = [None, WFC_MODE_CELLULAR_PREFERRED],
            wifi_network_ssid=self.wifi_network_ssid,
            wifi_network_pass=self.wifi_network_pass,
            turn_off_wifi_in_the_end=True,
            turn_off_airplane_mode_in_the_end=True)

    @test_tracker_info(uuid="f07a4924-0752-41fd-8e52-e75c3c78c538")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_esim_mo_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        """ A MO VoLTE long call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="cac09fa6-5db1-4523-910a-7fe9918a04ac")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_esim_mt_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        """ A MT VoLTE long call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="a0039ac0-9d3d-4acf-801b-4b0d01971153")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_esim_mo_volte_psim_5g_nsa_volte_dds_0(self):
        """ A MO VoLTE long call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="9cf03491-df27-4eda-9e3d-7782a44c0674")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_esim_mt_volte_psim_5g_nsa_volte_dds_0(self):
        """ A MT VoLTE long call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="6c8c7e67-3bec-49b4-8164-963e488df14f")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_esim_mo_5g_nsa_volte_psim_volte_dds_0(self):
        """ A MO VoLTE long call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="9a2bc9a2-18a2-471f-9b21-fd0aea1b126b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_esim_mt_5g_nsa_volte_psim_volte_dds_0(self):
        """ A MT VoLTE long call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="c88a0ed6-f8b6-4033-93db-b160c29d4b9e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_psim_mo_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        """ A MO VoLTE long call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="b4aa294d-679d-4a0e-8cc9-9261bfe8b392")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_psim_mt_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        """ A MT VoLTE long call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="2e20f05f-9434-410f-a40a-a01c0303d1a0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_psim_mo_5g_nsa_volte_esim_volte_dds_1(self):
        """ A MO VoLTE long call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="3f89b354-0cdc-4522-8a67-76773219e5af")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_psim_mt_5g_nsa_volte_esim_volte_dds_1(self):
        """ A MT VoLTE long call at eSIM, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="f18c61c5-3c3b-4645-90eb-e7bdef9b7c74")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_psim_mo_volte_esim_5g_nsa_volte_dds_1(self):
        """ A MO VoLTE long call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="8324ffe2-1332-47fc-af92-a3ed7be9b629")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_psim_mt_volte_esim_5g_nsa_volte_dds_1(self):
        """ A MT VoLTE long call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="e6760078-2a5e-4182-8ba1-57788fc607f1")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_esim_mo_volte_psim_volte_dds_0(self):
        """ A MO VoLTE long call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="c736e4f0-8dbc-480a-8da6-68453cc13d07")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_esim_mt_volte_psim_volte_dds_0(self):
        """ A MO VoLTE long call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="19dc55b5-b989-481d-a980-fcd0ff56abc2")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_psim_mo_volte_esim_volte_dds_1(self):
        """ A MO VoLTE long call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="494e9c90-6c56-4fa1-9fac-ac8f2b1c0dba")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_psim_mt_volte_esim_volte_dds_1(self):
        """ A MT VoLTE long call at eSIM, where
            - pSIM 4G VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            duration=360,
            streaming=False)

    @test_tracker_info(uuid="d253553d-7dc9-4e38-8e20-0839326c20aa")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_esim_mo_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        """ A MO VoLTE long call at eSIM during streaming, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="80a201c5-0bfe-4d7f-b08b-52b7c53b6468")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_esim_mt_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        """ A MT VoLTE long call at eSIM during streaming, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="8938575b-2544-4075-9cf9-3d938ad4d9cb")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_esim_mo_volte_psim_5g_nsa_volte_dds_0(self):
        """ A MO VoLTE long call at eSIM during streaming, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="200c7cce-aba2-40f8-a274-9b05177d00e0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_esim_mt_volte_psim_5g_nsa_volte_dds_0(self):
        """ A MT VoLTE long call at eSIM during streaming, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="26bb9415-44f4-43df-b2e6-abbdfacf33c2")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_esim_mo_5g_nsa_volte_psim_volte_dds_0(self):
        """ A MO VoLTE long call at eSIM during streaming, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="8a8dc1ca-6a85-4dc8-9e34-e17abe61f7b8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_esim_mt_5g_nsa_volte_psim_volte_dds_0(self):
        """ A MT VoLTE long call at eSIM during streaming, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="903a2813-6b27-4020-aaf2-b5ab8b29fa13")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_psim_mo_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        """ A MO VoLTE long call at eSIM during streaming, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="33d8ba2c-fa45-4ec0-aef5-b191b6ddd9a6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_psim_mt_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        """ A MT VoLTE long call at eSIM during streaming, where
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="6db23c84-13d9-47fa-b8f1-45c56e2d6428")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_psim_mo_5g_nsa_volte_esim_volte_dds_1(self):
        """ A MO VoLTE long call at eSIM during streaming, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="3a77b38f-c327-4c43-addf-48832bca7148")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_psim_mt_5g_nsa_volte_esim_volte_dds_1(self):
        """ A MT VoLTE long call at eSIM during streaming, where
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["5g_volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="2898eb67-3dfe-4322-8c69-817e0a95dfda")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_psim_mo_volte_esim_5g_nsa_volte_dds_1(self):
        """ A MO VoLTE long call at eSIM during streaming, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="780e8187-2068-4eca-a9de-e5f2f3491403")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_psim_mt_volte_esim_5g_nsa_volte_dds_1(self):
        """ A MT VoLTE long call at eSIM during streaming, where
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "5g_volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="9b84bd00-fae3-45c0-9e44-dd57d1719bb9")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_esim_mo_volte_psim_volte_dds_0(self):
        """ A MO VoLTE long call at eSIM during streaming, where
            - pSIM 4G VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mo",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="813c6059-bcef-42d3-b70b-9b0ba67ffc20")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_esim_mt_volte_psim_volte_dds_0(self):
        """ A MO VoLTE long call at eSIM during streaming, where
            - pSIM 4G VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            dds_slot=0,
            test_slot=SimSlotInfo.SLOT_1,
            direction="mt",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="970b1d31-195b-4599-80bc-bc46ede43a90")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_psim_mo_volte_esim_volte_dds_1(self):
        """ A MO VoLTE long call at eSIM during streaming, where
            - pSIM 4G VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mo",
            duration=360,
            streaming=True)

    @test_tracker_info(uuid="62843f60-5d1c-44ed-9936-e10d2691e787")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_long_voice_streaming_psim_mt_volte_esim_volte_dds_1(self):
        """ A MT VoLTE long call at eSIM during streaming, where
            - pSIM 4G VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 1)

            After call end will check the eSIM if is attach to the network
            with assigned RAT successfully and data works fine.
        """
        return dsds_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            dds_slot=1,
            test_slot=SimSlotInfo.SLOT_0,
            direction="mt",
            duration=360,
            streaming=True)