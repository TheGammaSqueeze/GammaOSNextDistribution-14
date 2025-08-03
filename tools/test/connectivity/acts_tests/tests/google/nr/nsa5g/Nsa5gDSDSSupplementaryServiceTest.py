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
from acts_contrib.test_utils.tel.loggers.telephony_metric_logger import TelephonyMetricLogger
from acts_contrib.test_utils.tel.tel_defines import SimSlotInfo
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_CONFERENCE
from acts_contrib.test_utils.tel.tel_dsds_utils import erase_call_forwarding
from acts_contrib.test_utils.tel.tel_dsds_utils import msim_call_forwarding
from acts_contrib.test_utils.tel.tel_dsds_utils import msim_call_voice_conf
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_ss_utils import set_call_waiting
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_voice_sub_id
from acts_contrib.test_utils.tel.tel_test_utils import get_capability_for_subscription

_WAIT_TIME_FOR_MEP_ENABLE_INTERVAL = 60
_WAIT_TIME_FOR_MEP_ENABLE = 180


class Nsa5gDSDSSupplementaryServiceTest(TelephonyBaseTest):
    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        self.message_lengths = (50, 160, 180)
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
        ensure_phones_idle(self.log, self.android_devices)
        erase_call_forwarding(self.log, self.android_devices[0])
        set_call_waiting(self.log, self.android_devices[0], enable=1)

    # psim 5g nsa volte & esim 5g nsa volte & dds slot 0
    @test_tracker_info(uuid="d1a50121-a245-4e51-a6aa-7836878339aa")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_callee_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_0(self):
        """Call forwarding unconditional test on pSIM of the primary device.
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CFU on pSIM of the primary device.
                2. Let the 2nd device call the pSIM of the primary device. The
                   call should be forwarded to the 3rd device. Answer and then
                   hang up the call.
                3. Disable CFU on pSIM of the primary device.
                4. Let the 2nd device call the pSIM of the primary device. The
                   call should NOT be forwarded to the primary device. Answer
                   and then hang up the call.
                5. Disable and erase CFU on the primary device.
        """
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            0,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            callee_rat=["5g_volte", "5g_volte"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="c268fee2-6f09-48c2-98d8-97cc06de0e61")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_callee_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        """Call forwarding unconditional test on eSIM of the primary device.
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CFU on eSIM of the primary device.
                2. Let the 2nd device call the eSIM of the primary device. The
                   call should be forwarded to the 3rd device. Answer and then
                   hang up the call.
                3. Disable CFU on eSIM of the primary device.
                4. Let the 2nd device call the eSIM of the primary device. The
                   call should NOT be forwarded to the primary device. Answer
                   and then hang up the call.
                5. Disable and erase CFU on the primary device.
        """
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            0,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            callee_rat=["5g_volte", "5g_volte"],
            call_forwarding_type="unconditional")

    # psim 5g nsa volte & esim 5g nsa volte & dds slot 1
    @test_tracker_info(uuid="df98b0d6-3643-4e01-b9c5-d41b40d95146")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_callee_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        """Call forwarding unconditional test on pSIM of the primary device.
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CFU on pSIM of the primary device.
                2. Let the 2nd device call the pSIM of the primary device. The
                   call should be forwarded to the 3rd device. Answer and then
                   hang up the call.
                3. Disable CFU on pSIM of the primary device.
                4. Let the 2nd device call the pSIM of the primary device. The
                   call should NOT be forwarded to the primary device. Answer
                   and then hang up the call.
                5. Disable and erase CFU on the primary device.
        """
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            1,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            callee_rat=["5g_volte", "5g_volte"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="99a61d4e-f0fa-4f65-b3bd-67d2a90cdfe2")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_callee_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_1(self):
        """Call forwarding unconditional test on eSIM of the primary device.
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CFU on eSIM of the primary device.
                2. Let the 2nd device call the eSIM of the primary device. The
                   call should be forwarded to the 3rd device. Answer and then
                   hang up the call.
                3. Disable CFU on eSIM of the primary device.
                4. Let the 2nd device call the eSIM of the primary device. The
                   call should NOT be forwarded to the primary device. Answer
                   and then hang up the call.
                5. Disable and erase CFU on the primary device.
        """
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            1,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            callee_rat=["5g_volte", "5g_volte"],
            call_forwarding_type="unconditional")

    # psim 5g nsa volte & esim 4g volte & dds slot 0
    @test_tracker_info(uuid="9fb2da2e-00f6-4d0f-a921-49786ffbb758")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_callee_psim_5g_nsa_volte_esim_4g_volte_dds_0(self):
        """Call forwarding unconditional test on pSIM of the primary device.
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CFU on pSIM of the primary device.
                2. Let the 2nd device call the pSIM of the primary device. The
                   call should be forwarded to the 3rd device. Answer and then
                   hang up the call.
                3. Disable CFU on pSIM of the primary device.
                4. Let the 2nd device call the pSIM of the primary device. The
                   call should NOT be forwarded to the primary device. Answer
                   and then hang up the call.
                5. Disable and erase CFU on the primary device.
        """
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            0,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            callee_rat=["5g_volte", "volte"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="da42b577-30a6-417d-a545-629ccbfaebb2")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_callee_esim_4g_volte_psim_5g_nsa_volte_dds_0(self):
        """Call forwarding unconditional test on eSIM of the primary device.
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CFU on eSIM of the primary device.
                2. Let the 2nd device call the eSIM of the primary device. The
                   call should be forwarded to the 3rd device. Answer and then
                   hang up the call.
                3. Disable CFU on eSIM of the primary device.
                4. Let the 2nd device call the eSIM of the primary device. The
                   call should NOT be forwarded to the primary device. Answer
                   and then hang up the call.
                5. Disable and erase CFU on the primary device.
        """
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            0,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            callee_rat=["5g_volte", "volte"],
            call_forwarding_type="unconditional")

    # psim 5g nsa volte & esim 4g volte & dds slot 1
    @test_tracker_info(uuid="e9ab2c2f-8b2c-4f26-879d-b872947ee3a1")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_callee_psim_5g_nsa_volte_esim_4g_volte_dds_1(self):
        """Call forwarding unconditional test on pSIM of the primary device.
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CFU on pSIM of the primary device.
                2. Let the 2nd device call the pSIM of the primary device. The
                   call should be forwarded to the 3rd device. Answer and then
                   hang up the call.
                3. Disable CFU on pSIM of the primary device.
                4. Let the 2nd device call the pSIM of the primary device. The
                   call should NOT be forwarded to the primary device. Answer
                   and then hang up the call.
                5. Disable and erase CFU on the primary device.
        """
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            1,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            callee_rat=["5g_volte", "volte"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="080e6cf2-7bb1-4ce8-9f15-c082cbb0fd8c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_callee_esim_4g_volte_psim_5g_nsa_volte_dds_1(self):
        """Call forwarding unconditional test on eSIM of the primary device.
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CFU on eSIM of the primary device.
                2. Let the 2nd device call the eSIM of the primary device. The
                   call should be forwarded to the 3rd device. Answer and then
                   hang up the call.
                3. Disable CFU on eSIM of the primary device.
                4. Let the 2nd device call the eSIM of the primary device. The
                   call should NOT be forwarded to the primary device. Answer
                   and then hang up the call.
                5. Disable and erase CFU on the primary device.
        """
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            1,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            callee_rat=["5g_volte", "volte"],
            call_forwarding_type="unconditional")

    # psim 4g volte & esim 5g nsa volte & dds slot 0
    @test_tracker_info(uuid="0da6f8e9-dfea-408b-91d9-e10fb6dad086")
    def test_msim_cfu_callee_psim_4g_volte_esim_5g_nsa_volte_dds_0(self):
        """Call forwarding unconditional test on pSIM of the primary device.
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CFU on pSIM of the primary device.
                2. Let the 2nd device call the pSIM of the primary device. The
                   call should be forwarded to the 3rd device. Answer and then
                   hang up the call.
                3. Disable CFU on pSIM of the primary device.
                4. Let the 2nd device call the pSIM of the primary device. The
                   call should NOT be forwarded to the primary device. Answer
                   and then hang up the call.
                5. Disable and erase CFU on the primary device.
        """
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            0,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            callee_rat=["volte", "5g_volte"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="dadde63d-4a4d-4fe7-82bd-25ecff856900")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_callee_esim_5g_nsa_volte_psim_4g_volte_dds_0(self):
        """Call forwarding unconditional test on eSIM of the primary device.
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CFU on eSIM of the primary device.
                2. Let the 2nd device call the eSIM of the primary device. The
                   call should be forwarded to the 3rd device. Answer and then
                   hang up the call.
                3. Disable CFU on eSIM of the primary device.
                4. Let the 2nd device call the eSIM of the primary device. The
                   call should NOT be forwarded to the primary device. Answer
                   and then hang up the call.
                5. Disable and erase CFU on the primary device.
        """
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            0,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            callee_rat=["volte", "5g_volte"],
            call_forwarding_type="unconditional")

    # psim 4g volte & esim 5g nsa volte & dds slot 1
    @test_tracker_info(uuid="0e951ee2-4a38-4b97-8a79-f6b3c66bf4d5")
    def test_msim_cfu_callee_psim_4g_volte_esim_5g_nsa_volte_dds_1(self):
        """Call forwarding unconditional test on pSIM of the primary device.
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CFU on pSIM of the primary device.
                2. Let the 2nd device call the pSIM of the primary device. The
                   call should be forwarded to the 3rd device. Answer and then
                   hang up the call.
                3. Disable CFU on pSIM of the primary device.
                4. Let the 2nd device call the pSIM of the primary device. The
                   call should NOT be forwarded to the primary device. Answer
                   and then hang up the call.
                5. Disable and erase CFU on the primary device.
        """
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            1,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            callee_rat=["volte", "5g_volte"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="0f15a135-aa30-46fb-956a-99b5b1109783")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_cfu_callee_esim_5g_nsa_volte_psim_4g_volte_dds_1(self):
        """Call forwarding unconditional test on eSIM of the primary device.
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CFU on eSIM of the primary device.
                2. Let the 2nd device call the eSIM of the primary device. The
                   call should be forwarded to the 3rd device. Answer and then
                   hang up the call.
                3. Disable CFU on eSIM of the primary device.
                4. Let the 2nd device call the eSIM of the primary device. The
                   call should NOT be forwarded to the primary device. Answer
                   and then hang up the call.
                5. Disable and erase CFU on the primary device.
        """
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            1,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            callee_rat=["volte", "5g_volte"],
            call_forwarding_type="unconditional")

    # psim 5g nsa volte & esim 5g nsa volte & dds slot 0
    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="edfbc065-7a1d-4ac8-94fe-58106bd5f0a0")
    def test_msim_conf_call_host_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_0(self):
        """Conference call test on pSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CW on pSIM of the primary device.
                2. Let the pSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the pSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Merge 2 active calls.
        """
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 0, host_rat=["5g_volte", "5g_volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="fbae3ef2-6ecc-48fb-b21c-155b2b4fd5d6")
    def test_msim_conf_call_host_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        """Conference call test on eSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CW on eSIM of the primary device.
                2. Let the eSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the eSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Merge 2 active calls.
        """
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 0, host_rat=["5g_volte", "5g_volte"])

    # psim 5g nsa volte & esim 5g nsa volte & dds slot 1
    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="404b7bf8-0706-4d27-a1ff-231ea6d5c34b")
    def test_msim_conf_call_host_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        """Conference call test on pSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CW on pSIM of the primary device.
                2. Let the pSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the pSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Merge 2 active calls.
        """
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 1, host_rat=["5g_volte", "5g_volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="cd74af3e-ced5-4275-990c-0561bfeee81d")
    def test_msim_conf_call_host_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_1(self):
        """Conference call test on eSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CW on eSIM of the primary device.
                2. Let the eSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the eSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Merge 2 active calls.
        """
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 1, host_rat=["5g_volte", "5g_volte"])

    # psim 5g nsa volte & esim 4g volte & dds slot 0
    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="ff107828-0b09-47fb-ba85-b0e13b89970f")
    def test_msim_conf_call_host_psim_5g_nsa_volte_esim_4g_volte_dds_0(self):
        """Conference call test on pSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CW on pSIM of the primary device.
                2. Let the pSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the pSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Merge 2 active calls.
        """
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 0, host_rat=["5g_volte", "volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="4a3152e2-8cc6-477d-9dd6-55f3ac35681e")
    def test_msim_conf_call_host_esim_4g_volte_psim_5g_nsa_volte_dds_0(self):
        """Conference call test on eSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CW on eSIM of the primary device.
                2. Let the eSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the eSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Merge 2 active calls.
        """
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 0, host_rat=["5g_volte", "volte"])

    # psim 5g nsa volte & esim 4g volte & dds slot 1
    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="4aa8e15a-16b5-4173-b0d7-1a6cf00cf240")
    def test_msim_conf_call_host_psim_5g_nsa_volte_esim_4g_volte_dds_1(self):
        """Conference call test on pSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CW on pSIM of the primary device.
                2. Let the pSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the pSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Merge 2 active calls.
        """
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 1, host_rat=["5g_volte", "volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="82d9ca6c-8c3d-4a54-ae85-c3d52aab8bc4")
    def test_msim_conf_call_host_esim_4g_volte_psim_5g_nsa_volte_dds_1(self):
        """Conference call test on eSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CW on eSIM of the primary device.
                2. Let the eSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the eSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Merge 2 active calls.
        """
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 1, host_rat=["5g_volte", "volte"])

    # psim 4g volte & esim 5g nsa volte & dds slot 0
    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="d8dc0e1b-bfad-4040-ab44-91b15160dd86")
    def test_msim_conf_call_host_psim_4g_volte_esim_5g_nsa_volte_dds_0(self):
        """Conference call test on pSIM of the primary device
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CW on pSIM of the primary device.
                2. Let the pSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the pSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Merge 2 active calls.
        """
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 0, host_rat=["volte", "5g_volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="8d8d1050-9e73-4ec9-a9dd-7f68ccd11483")
    def test_msim_conf_call_host_esim_5g_nsa_volte_psim_4g_volte_dds_0(self):
        """Conference call test on eSIM of the primary device
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CW on eSIM of the primary device.
                2. Let the eSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the eSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Merge 2 active calls.
        """
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 0, host_rat=["volte", "5g_volte"])

    # psim 4g volte & esim 5g nsa volte & dds slot 1
    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="8f46e57c-c7a2-49e9-9e4c-1f83ab67cd5e")
    def test_msim_conf_call_host_psim_4g_volte_esim_5g_nsa_volte_dds_1(self):
        """Conference call test on pSIM of the primary device
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)

            Test steps:
                1. Enable CW on pSIM of the primary device.
                2. Let the pSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the pSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Merge 2 active calls.
        """
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 1, host_rat=["volte", "5g_volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="7975fc5b-4146-4370-9f1b-1ad1987a14f3")
    def test_msim_conf_call_host_esim_5g_nsa_volte_psim_4g_volte_dds_1(self):
        """Conference call test on eSIM of the primary device
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)

            Test steps:
                1. Enable CW on eSIM of the primary device.
                2. Let the eSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the eSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Merge 2 active calls.
        """
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 1, host_rat=["volte", "5g_volte"])

    # psim 5g nsa volte & esim 5g nsa volte & dds slot 0
    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="1050ee12-d1aa-47c9-ad3a-589ad6c6b695")
    def test_msim_cw_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_0(self):
        """Call waiting test on pSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CW on pSIM of the primary device.
                2. Let the pSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the pSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Hang up 2 calls from the 2nd and 3rd devices.
                6. Disable CW on pSIM of the primary device.
                7. Repeat step 2 & 3. In the step 3 the primary device should
                   not receive the incoming call.
        """
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["5g_volte", "5g_volte"],
            merge=False, disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["5g_volte", "5g_volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="74ae2673-fefb-459c-a415-366a12477956")
    def test_msim_cw_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_0(self):
        """Call waiting test on eSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CW on eSIM of the primary device.
                2. Let the eSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the eSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Hang up 2 calls from the 2nd and 3rd devices.
                6. Disable CW on eSIM of the primary device.
                7. Repeat step 2 & 3. In the step 3 the primary device should
                   not receive the incoming call.
        """
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["5g_volte", "5g_volte"],
            merge=False, disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["5g_volte", "5g_volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    # psim 5g nsa volte & esim 5g nsa volte & dds slot 1
    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="73b26c81-8080-4df0-a491-875e1290b5aa")
    def test_msim_cw_psim_5g_nsa_volte_esim_5g_nsa_volte_dds_1(self):
        """Call waiting test on pSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)

            Test steps:
                1. Enable CW on pSIM of the primary device.
                2. Let the pSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the pSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Hang up 2 calls from the 2nd and 3rd devices.
                6. Disable CW on pSIM of the primary device.
                7. Repeat step 2 & 3. In the step 3 the primary device should
                   not receive the incoming call.
        """
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["5g_volte", "5g_volte"],
            merge=False, disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["5g_volte", "5g_volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="32804d38-7def-4507-921d-f906d1cf9dfa")
    def test_msim_cw_esim_5g_nsa_volte_psim_5g_nsa_volte_dds_1(self):
        """Call waiting test on eSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 1)

            Test steps:
                1. Enable CW on eSIM of the primary device.
                2. Let the eSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the eSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Hang up 2 calls from the 2nd and 3rd devices.
                6. Disable CW on eSIM of the primary device.
                7. Repeat step 2 & 3. In the step 3 the primary device should
                   not receive the incoming call.
        """
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["5g_volte", "5g_volte"],
            merge=False, disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["5g_volte", "5g_volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    # psim 5g nsa volte & esim 4g volte & dds slot 0
    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="753a8651-8230-4714-aa5c-32ed7e7d7c04")
    def test_msim_cw_psim_5g_nsa_volte_esim_4g_volte_dds_0(self):
        """Call waiting test on pSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CW on pSIM of the primary device.
                2. Let the pSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the pSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Hang up 2 calls from the 2nd and 3rd devices.
                6. Disable CW on pSIM of the primary device.
                7. Repeat step 2 & 3. In the step 3 the primary device should
                   not receive the incoming call.
        """
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["5g_volte", "volte"],
            merge=False, disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["5g_volte", "volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="fc92c004-5862-4035-98b4-5ea3d3c2c5e9")
    def test_msim_cw_esim_4g_volte_psim_5g_nsa_volte_dds_0(self):
        """Call waiting test on eSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CW on eSIM of the primary device.
                2. Let the eSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the eSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Hang up 2 calls from the 2nd and 3rd devices.
                6. Disable CW on eSIM of the primary device.
                7. Repeat step 2 & 3. In the step 3 the primary device should
                   not receive the incoming call.
        """
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["5g_volte", "volte"],
            merge=False, disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["5g_volte", "volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    # psim 5g nsa volte & esim 4g volte & dds slot 1
    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="753a8651-8230-4714-aa5c-32ed7e7d7c04")
    def test_msim_cw_psim_5g_nsa_volte_esim_4g_volte_dds_1(self):
        """Call waiting test on pSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CW on pSIM of the primary device.
                2. Let the pSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the pSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Hang up 2 calls from the 2nd and 3rd devices.
                6. Disable CW on pSIM of the primary device.
                7. Repeat step 2 & 3. In the step 3 the primary device should
                   not receive the incoming call.
        """
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["5g_volte", "volte"],
            merge=False, disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["5g_volte", "volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="fc92c004-5862-4035-98b4-5ea3d3c2c5e9")
    def test_msim_cw_esim_4g_volte_psim_5g_nsa_volte_dds_1(self):
        """Call waiting test on eSIM of the primary device
            - pSIM 5G NSA VoLTE
            - eSIM 4G VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CW on eSIM of the primary device.
                2. Let the eSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the eSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Hang up 2 calls from the 2nd and 3rd devices.
                6. Disable CW on eSIM of the primary device.
                7. Repeat step 2 & 3. In the step 3 the primary device should
                   not receive the incoming call.
        """
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["5g_volte", "volte"],
            merge=False, disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["5g_volte", "volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    # psim 4g volte & esim 5g nsa volte & dds slot 0
    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="4c02fc60-b838-40a1-879f-675d8c4b91af")
    def test_msim_cw_psim_4g_volte_esim_5g_nsa_volte_dds_0(self):
        """Call waiting test on pSIM of the primary device
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CW on pSIM of the primary device.
                2. Let the pSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the pSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Hang up 2 calls from the 2nd and 3rd devices.
                6. Disable CW on pSIM of the primary device.
                7. Repeat step 2 & 3. In the step 3 the primary device should
                   not receive the incoming call.
        """
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["volte", "5g_volte"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["volte", "5g_volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="cbe58062-bd7f-48b5-aab1-84355a3fcf55")
    def test_msim_cw_esim_5g_nsa_volte_psim_4g_volte_dds_0(self):
        """Call waiting test on eSIM of the primary device
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at pSIM (slot 0)

            Test steps:
                1. Enable CW on eSIM of the primary device.
                2. Let the eSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the eSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Hang up 2 calls from the 2nd and 3rd devices.
                6. Disable CW on eSIM of the primary device.
                7. Repeat step 2 & 3. In the step 3 the primary device should
                   not receive the incoming call.
        """
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["volte", "5g_volte"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["volte", "5g_volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    # psim 4g volte & esim 5g nsa volte & dds slot 1
    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="80c7e356-9419-484f-9b34-65ca5544bc39")
    def test_msim_cw_psim_4g_volte_esim_5g_nsa_volte_dds_1(self):
        """Call waiting test on pSIM of the primary device
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CW on pSIM of the primary device.
                2. Let the pSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the pSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Hang up 2 calls from the 2nd and 3rd devices.
                6. Disable CW on pSIM of the primary device.
                7. Repeat step 2 & 3. In the step 3 the primary device should
                   not receive the incoming call.
        """
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["volte", "5g_volte"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["volte", "5g_volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="6cd6b062-d68a-4b1b-b6ca-92af72ebe3b9")
    def test_msim_cw_esim_5g_nsa_volte_psim_4g_volte_dds_1(self):
        """Call waiting test on eSIM of the primary device
            - pSIM 4G VoLTE
            - eSIM 5G NSA VoLTE
            - DDS at eSIM (slot 1)

            Test steps:
                1. Enable CW on eSIM of the primary device.
                2. Let the eSIM of primary device call the 2nd device. Keep the
                   call active.
                3. Let the 3rd device call the eSIM of the primary device. Keep
                   both calls active.
                4. Swap the call twice.
                5. Hang up 2 calls from the 2nd and 3rd devices.
                6. Disable CW on eSIM of the primary device.
                7. Repeat step 2 & 3. In the step 3 the primary device should
                   not receive the incoming call.
        """
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["volte", "5g_volte"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["volte", "5g_volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result