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
from acts_contrib.test_utils.tel.loggers.protos.telephony_metric_pb2 import TelephonyVoiceTestResult
from acts_contrib.test_utils.tel.loggers.telephony_metric_logger import TelephonyMetricLogger
from acts_contrib.test_utils.tel.tel_defines import SimSlotInfo
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import INVALID_SUB_ID
from acts_contrib.test_utils.tel.tel_dsds_utils import dsds_message_streaming_test
from acts_contrib.test_utils.tel.tel_dsds_utils import dsds_message_test
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_on_rat
from acts_contrib.test_utils.tel.tel_subscription_utils import get_incoming_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_message_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_slot_index_from_subid
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_from_slot_index
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_on_same_network_of_host_ad
from acts_contrib.test_utils.tel.tel_subscription_utils import set_message_subid
from acts_contrib.test_utils.tel.tel_subscription_utils import set_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import set_dds_on_slot
from acts_contrib.test_utils.tel.tel_message_utils import log_messaging_screen_shot
from acts_contrib.test_utils.tel.tel_message_utils import sms_in_collision_send_receive_verify_for_subscription
from acts_contrib.test_utils.tel.tel_message_utils import sms_rx_power_off_multiple_send_receive_verify_for_subscription
from acts_contrib.test_utils.tel.tel_message_utils import voice_call_in_collision_with_mt_sms_msim
from acts_contrib.test_utils.tel.tel_test_utils import verify_http_connection
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_on_rat
from acts.utils import rand_ascii_str
from acts.libs.utils.multithread import multithread_func

_WAIT_TIME_FOR_MEP_ENABLE_INTERVAL = 60
_WAIT_TIME_FOR_MEP_ENABLE = 180


CallResult = TelephonyVoiceTestResult.CallResult.Value

class TelLiveGFTDSDSMessageTest(TelephonyBaseTest):
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

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)

    def _msim_sms_collision_test(
        self,
        ad_mo,
        ad_mo2,
        ad_mt,
        ad_mt2,
        mo1_sub_id,
        mo2_sub_id,
        mt_sub_id,
        mt2_sub_id):
        """Send 2 SMS' at the same time. SMS collision can be met when both SMS'
        are sent to the same recipient.

        Args:
            ad_mo: Android object of the device sending SMS
            ad_mo2: Android object of the device sending SMS
            ad_mt: Android object of the device receiving SMS
            ad_mt2: Android object of the device receiving SMS
            mo1_sub_id: Sub ID of ad_mo
            mo2_sub_id: Sub ID of ad_mo2
            mt_sub_id: Sub ID of ad_mt
            mt2_sub_id: Sub ID of ad_mt2

        Returns:
            True if both SMS' are sent and received successfully and False on
            the contrary.
        """
        for length in self.message_lengths:
            message_array = [rand_ascii_str(length)]
            message_array2 = [rand_ascii_str(length)]
            if not sms_in_collision_send_receive_verify_for_subscription(
                self.log,
                ad_mo,
                ad_mo2,
                ad_mt,
                ad_mt2,
                mo1_sub_id,
                mo2_sub_id,
                mt_sub_id,
                mt2_sub_id,
                message_array,
                message_array2):
                self.log.warning(
                    "Test of SMS collision with length %s failed", length)
                return False
            else:
                self.log.info(
                    "Test of SMS collision with length %s succeeded", length)
        self.log.info(
            "Test of SMS collision with lengths %s characters succeeded.",
            self.message_lengths)
        return True

    def _msim_rx_power_off_multiple_sms_test(
        self,
        ad_mo,
        ad_mo2,
        ad_mt,
        mo1_sub_id,
        mo2_sub_id,
        mt_sub_id,
        mt2_sub_id,
        num_array_message,
        num_array_message2):
        """Power off the recipient and then send 2 SMS'. Make sure all SMS' can
        be received correctly after the recipient is poweron on again.

        Args:
            ad_mo: Android object of the device sending SMS
            ad_mo2: Android object of the device sending SMS
            ad_mt: Android object of the device receiving SMS
            mo1_sub_id: Sub ID of ad_mo
            mo2_sub_id: Sub ID of ad_mo2
            mt_sub_id: Sub ID of ad_mt
            mt2_sub_id: Sub ID of ad_mt2
            num_array_message: Number of messages to be sent by ad_mo
            num_array_message2: Number of messages to be sent by ad_mo2

        Returns:
            True if all SMS' are sent and received successfully and False on
            the contrary.
        """
        for length in self.message_lengths:
            if not sms_rx_power_off_multiple_send_receive_verify_for_subscription(
                self.log,
                ad_mo,
                ad_mo2,
                ad_mt,
                mo1_sub_id,
                mo2_sub_id,
                mt_sub_id,
                mt2_sub_id,
                length,
                length,
                num_array_message,
                num_array_message2):
                self.log.warning(
                    "Test of multiple SMS with length %s during rx power off"
                    " failed.", length)
                return False
            else:
                self.log.info(
                    "Test of multiple SMS with length %s during rx power off"
                    " succeeded.", length)
        self.log.info(
            "Test of multiple SMS with lengths %s characters during rx power"
            "off succeeded.", self.message_lengths)
        return True

    def _test_msim_voice_call_in_collision_with_mt_sms(
            self,
            mo_voice_slot,
            mt_sms_slot,
            dds_slot,
            rat=["", ""],
            call_direction="mo"):
        """Make MO/MT voice call in collision with MT SMS at specific slot in
        specific RAT with DDS at specific slot.

        Args:
            mo_voice_slot: Slot for voice call. 0 for pSIM or 1 for eSIM
            mt_sms_slot: Slot for MT SMS. 0 for pSIM or 1 for eSIM
            dds_slot: Preferred data slot
            rat: RAT for both slots of the primary device
            call_direction: "mo" or "mt"

        Returns:
            True or False
        """
        ads = self.android_devices
        ad = ads[0]
        ad_mo_sms = ads[2]
        ad_mt_voice = ads[1]

        mo_voice_sub_id = get_subid_from_slot_index(self.log, ad, mo_voice_slot)
        if mo_voice_sub_id == INVALID_SUB_ID:
            ad.log.warning("Failed to get sub ID ar slot %s.", mo_voice_slot)
            return False
        mo_voice_other_sub_id = get_subid_from_slot_index(
            self.log, ad, 1-mo_voice_slot)
        set_voice_sub_id(ad, mo_voice_sub_id)

        _, mt_voice_sub_id, _ = get_subid_on_same_network_of_host_ad(ads)
        set_voice_sub_id(ad_mt_voice, mt_voice_sub_id)
        ad_mt_voice.log.info("Sub ID for incoming call at slot %s: %s",
            get_slot_index_from_subid(ad_mt_voice, mt_voice_sub_id),
            get_incoming_voice_sub_id(ad_mt_voice))

        set_message_subid(
            ad, get_subid_from_slot_index(self.log, ad, mt_sms_slot))

        self.log.info("Step 1: Switch DDS.")
        if not set_dds_on_slot(ads[0], dds_slot):
            ads[0].log.error(
                "Failed to set DDS at slot %s on %s",(dds_slot, ads[0].serial))
            return False

        self.log.info("Step 2: Check HTTP connection after DDS switch.")
        if not verify_http_connection(self.log,
           ads[0],
           url="https://www.google.com",
           retry=5,
           retry_interval=15,
           expected_state=True):

            self.log.error("Failed to verify http connection.")
            return False
        else:
            self.log.info("Verify http connection successfully.")

        phone_setup_on_rat(
            self.log, ad, rat[1-mo_voice_slot], mo_voice_other_sub_id)
        phone_setup_on_rat(self.log, ad, 'general')
        phone_setup_on_rat(self.log, ad_mt_voice, 'general')
        phone_setup_on_rat(self.log, ad_mo_sms, 'general', sub_id_type='sms')

        if mo_voice_slot == 0 or mo_voice_slot == 1:
            mo_phone_setup_func = phone_setup_on_rat(
                self.log,
                ad,
                rat[mo_voice_slot],
                only_return_fn=True)

        is_mo_in_call = is_phone_in_call_on_rat(
            self.log, ad, rat[mo_voice_slot], only_return_fn=True)

        self.log.info("Step 3: Set up the phone in desired RAT.")
        tasks = [(mo_phone_setup_func, (self.log, ad, mo_voice_sub_id))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            self.tel_logger.set_result(CallResult("CALL_SETUP_FAILURE"))
            raise signals.TestFailure("Failed",
                extras={"fail_reason": "Phone Failed to Set Up Properly."})

        self.log.info("Step 4: Make a MO voice call in collision with MT SMS.")
        result = voice_call_in_collision_with_mt_sms_msim(
            self.log,
            ad,
            ad_mo_sms,
            ad_mt_voice,
            get_subid_from_slot_index(self.log, ad, mt_sms_slot),
            get_outgoing_message_sub_id(ad_mo_sms),
            mo_voice_sub_id,
            mt_voice_sub_id,
            [rand_ascii_str(50)],
            ad,
            is_mo_in_call,
            None,
            call_direction)

        call_result = True
        sms_result = True

        try:
            self.tel_logger.set_result(result.result_value)
            if not result:
                call_result = False
        except:
            sms_result = False

        extras = {}
        if not sms_result:
            log_messaging_screen_shot(ad_mo_sms, test_name="sms_tx")
            log_messaging_screen_shot(ad, test_name="sms_rx")
            extras = {"sms_fail_reason": "SMS failed"}

        if not call_result:
            self.log.error(
                "Failed to make MO call from %s slot %s to %s slot %s",
                ad.serial,
                mo_voice_slot,
                ad_mt_voice.serial,
                get_slot_index_from_subid(
                    ad_mt_voice, mt_voice_sub_id))
            extras = {"call_fail_reason": str(result.result_value)}

        if not sms_result or not call_result:
            raise signals.TestFailure("Failed", extras=extras)

        return True

    def multiple_mt_sms(
        self,
        slot_0_nw_gen="volte",
        slot_1_nw_gen="volte",
        power_off=False):
        """Receive multiple MT SMS' at the same time at specific slot in specific
        RAT to make SMS collision.

        Args:
            slot_0_nw_gen: Network generation (RAT) at pSIM
            slot_1_nw_gen: Network generation (RAT) at eSIM
            power_off: True if MT SMS' have to be sent when target DUT is
            power-off.

        Returns:
            True of False
        """
        ad = self.android_devices[0]
        slot_0_subid = get_subid_from_slot_index(ad.log, ad, 0)
        slot_1_subid = get_subid_from_slot_index(ad.log, ad, 1)

        if slot_0_subid == INVALID_SUB_ID or slot_1_subid == INVALID_SUB_ID:
            self.log.error("Not all slots have valid sub ID.")
            raise signals.TestFailure("Failed",
                extras={"fail_reason": "Not all slots have valid sub ID"})

        slot0_phone_setup_func = phone_setup_on_rat(
            self.log,
            ad,
            slot_0_nw_gen,
            only_return_fn=True)

        tasks = [(slot0_phone_setup_func, (self.log, ad, slot_0_subid))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            self.tel_logger.set_result(CallResult("CALL_SETUP_FAILURE"))
            raise signals.TestFailure("Failed",
                extras={"fail_reason": "Phone Failed to Set Up Properly."})

        slot1_phone_setup_func = phone_setup_on_rat(
            self.log,
            ad,
            slot_1_nw_gen,
            only_return_fn=True)

        tasks = [(slot1_phone_setup_func, (self.log, ad, slot_1_subid))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            self.tel_logger.set_result(CallResult("CALL_SETUP_FAILURE"))
            raise signals.TestFailure("Failed",
                extras={"fail_reason": "Phone Failed to Set Up Properly."})

        ad_host = self.android_devices[0]
        ad_mo_1 = self.android_devices[1]
        ad_mo_2 = self.android_devices[2]

        for host_sub_id in [
            [slot_0_subid, slot_1_subid],
            [slot_1_subid, slot_0_subid]]:
            _, mo1_sub_id, _ = get_subid_on_same_network_of_host_ad(
                self.android_devices, host_sub_id=host_sub_id[0])
            set_message_subid(ad_mo_1, mo1_sub_id)

            _, _, mo2_sub_id = get_subid_on_same_network_of_host_ad(
                self.android_devices, host_sub_id=host_sub_id[1])
            set_message_subid(ad_mo_2, mo2_sub_id)

            if power_off:
                res = self._msim_rx_power_off_multiple_sms_test(
                    ad_mo_1,
                    ad_mo_2,
                    ad_host,
                    mo1_sub_id,
                    mo2_sub_id,
                    host_sub_id[0],
                    host_sub_id[1],
                    5,
                    5)
            else:
                res = self._msim_sms_collision_test(
                    ad_mo_1,
                    ad_mo_2,
                    ad_host,
                    ad_host,
                    mo1_sub_id,
                    mo2_sub_id,
                    host_sub_id[0],
                    host_sub_id[1])

            if not res:
                log_messaging_screen_shot(
                    ad_host, test_name="sms rx subid: %s" % host_sub_id[0])
                log_messaging_screen_shot(
                    ad_host, test_name="sms rx subid: %s" % host_sub_id[1])
                log_messaging_screen_shot(
                    ad_mo_1, test_name="sms tx subid: %s" % mo1_sub_id)
                log_messaging_screen_shot(
                    ad_mo_2, test_name="sms tx subid: %s" % mo2_sub_id)

                return False
        return True

    @test_tracker_info(uuid="4ae61fdf-2078-4e50-ae03-cb2e9299ce8d")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["volte", "volte"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="0e8801f8-7203-45ba-aff3-cb667fd538e1")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_psim_dds_slot_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="d54c2b4e-2e32-49f0-9536-879eb6f6577e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["volte", "volte"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="feed9119-df31-46f7-afd8-addf4052422a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_psim_dds_slot_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="1da9965c-c863-4e6e-9374-a082fa16d6fd")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_esim_dds_slot_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="64aec600-851f-4bde-b66c-130c69d1d5b6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["volte", "volte"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="9ce40c2c-3a59-4612-a0cc-4fcba887856c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_esim_dds_slot_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="4e46081d-733d-47d9-be4d-9e492de38bcd")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["volte", "volte"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="5ede96ed-78b5-4cfb-94a3-44c34d610bef")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_csfb_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["volte", "csfb"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="ae681d36-e450-4453-88a8-e9abf4bdf723")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_csfb_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["volte", "csfb"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="6490abf9-7fc9-4168-ba20-7da0cb18d96e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_csfb_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["volte", "csfb"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="71590c9e-add0-4cbb-a530-07f58d26d954")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_csfb_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["volte", "csfb"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="1b033914-8a26-48e0-829a-c85b5a93ce42")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_csfb_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["volte", "csfb"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="15ebac40-5dc3-47ee-a787-ae6f9d71aff6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_csfb_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["volte", "csfb"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="b38390d2-b5ab-414b-9c61-2324395a56a6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_csfb_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["volte", "csfb"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="1c4a3a34-800a-4117-8c32-b6ec7d58a5cb")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_csfb_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["volte", "csfb"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="c7645032-8006-448e-ae3e-86c9223482cf")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_csfb_volte_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["csfb", "volte"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="a4455da1-6314-4d2e-a6eb-c7e063a5fd10")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_csfb_volte_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["csfb", "volte"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="60828bcc-0111-4d97-ac01-b43ff9c33b11")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_csfb_volte_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["csfb", "volte"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="d0f04ab9-c1fe-41b1-8ffc-7bf7cbb408ea")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_csfb_volte_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["csfb", "volte"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="97ad2e6f-8b71-49d4-870c-2f4438351880")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_csfb_volte_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["csfb", "volte"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="8353bce2-a800-440c-9822-a922343d0ff5")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_csfb_volte_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["csfb", "volte"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="7659d23d-8cf4-4ace-8e53-b26fc2fca38c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_csfb_volte_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["csfb", "volte"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="91577f12-4a0e-4743-82bc-1b7581a6940d")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_csfb_volte_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["csfb", "volte"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="a5f2c1b0-5ae7-4187-ad63-4782dc47f62b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_3g_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["volte", "3g"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="0c983462-5372-4aae-a484-53da4d2b9553")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_3g_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["volte", "3g"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="8266aaac-9d67-42c3-9260-d80c377b1ef9")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_3g_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["volte", "3g"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="d6ae749b-5e69-489e-8fda-fcb38aaa6cb0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_3g_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["volte", "3g"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="f4985e53-d530-491c-94cd-51ba22a34eff")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_3g_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["volte", "3g"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="b4fc2379-6937-404a-a659-249c1ccf9dd0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_3g_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["volte", "3g"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="e1027a25-b19f-4fb7-bfb9-79919e380c25")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_3g_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["volte", "3g"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="7cf99f83-0542-42c8-8e72-1653e381aa6c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_3g_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["volte", "3g"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="c1084606-a63b-41da-a0cb-2db972b6a8ce")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_3g_volte_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["3g", "volte"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="4806716c-047a-4a33-a317-97d3cce5d2ca")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_3g_volte_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["3g", "volte"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="2877ff0b-d567-4683-baa3-20e254ed025c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_3g_volte_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["3g", "volte"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="6bf3ea1b-e75c-4844-a311-5a18b1b7a1b8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_3g_volte_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["3g", "volte"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="fb7bf8b2-fa44-4e05-a0ab-16e7b1907e6b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_3g_volte_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["3g", "volte"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="d9090125-61cb-4ef5-97de-06c2ec8529bd")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_3g_volte_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["3g", "volte"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="d764c5ea-a34a-4b29-ab50-63bd63ebe5c4")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_3g_volte_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["3g", "volte"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="fe7d2f8c-eeb6-4ae9-a57d-1636d3153d2b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_3g_volte_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["3g", "volte"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="b9a5cb40-4986-4811-90e7-628d1729ccb2")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_csfb_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["csfb", "csfb"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="220665c1-4c63-4450-b8bb-17fc6df24498")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_csfb_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["csfb", "csfb"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="968217a6-320f-41f0-b401-7c377309d983")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_csfb_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["csfb", "csfb"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="c6a5bf63-af40-4619-a0eb-0d1835fde36c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_csfb_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["csfb", "csfb"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="ea9f4e72-0dea-4f5f-b5ff-4a0bad0d29a0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_csfb_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["csfb", "csfb"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="4eb935f0-2b11-4b2d-8faa-9a022e36813a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_csfb_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["csfb", "csfb"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="765e31fd-b412-43a8-a6a8-5d3ae66cab18")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_csfb_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["csfb", "csfb"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="bc6ada03-6a5e-4fe7-80c4-3aebc9fa426f")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_csfb_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["csfb", "csfb"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="a42994d0-bdb3-487e-98f2-665899d3edba")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_csfb_3g_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["csfb", "3g"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="d8ef0ac8-9cb1-4f32-8211-84dee563af00")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_csfb_3g_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["csfb", "3g"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="f4eb2254-5148-4cf9-b53f-56d8665de645")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_csfb_3g_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["csfb", "3g"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="fd546290-f7e7-47ff-b165-a9bb01e91c64")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_csfb_3g_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["csfb", "3g"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="d6994024-e845-48e2-9cd6-d72e97480a8a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_csfb_3g_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["csfb", "3g"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="c816165e-49d8-4d0a-8bb5-e64ad910a55a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_csfb_3g_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["csfb", "3g"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="647d546f-b325-4b91-be84-0bedf5a33210")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_csfb_3g_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["csfb", "3g"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="98b7e161-4953-4566-a96c-21545bf05e51")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_csfb_3g_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["csfb", "3g"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="9a3d1330-e70e-4ac0-a8bc-fec5710a8dcd")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_3g_csfb_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["3g", "csfb"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="51b4edd3-a867-409e-b367-2fd8cf0eb4a6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_3g_csfb_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["3g", "csfb"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="dba9cb2b-84bd-47db-a5a6-826e54a1bbeb")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_3g_csfb_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["3g", "csfb"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="263494aa-f3c4-450e-b5bf-b9331d9c9dd8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_3g_csfb_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["3g", "csfb"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="7ba231b8-edc9-4f64-ba7e-5f0360c4eed5")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_3g_csfb_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["3g", "csfb"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="ca1e9c35-07f2-4e32-8a59-61efc37f11a4")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_3g_csfb_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["3g", "csfb"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="f19252c0-8ff6-4267-adcd-f676407333e6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_3g_csfb_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["3g", "csfb"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="34ef2001-d80d-4818-b458-1e8a9556e5cd")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_3g_csfb_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["3g", "csfb"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="947ceba7-9aeb-402c-ba36-4856bc4352eb")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_3g_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["3g", "3g"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="9f9677e1-1215-49ed-a671-22e7779659a9")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_3g_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["3g", "3g"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="f77112c8-85e8-4584-a0b7-bba11c23be7d")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_3g_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["3g", "3g"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="facc19fd-7846-488e-9cf1-755f81d0fee2")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_3g_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["3g", "3g"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="5a26f35e-c038-409e-8941-7e0b475ebda8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_3g_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["3g", "3g"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="c303aa26-0fd0-44d7-b2fc-32782deaf5ea")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_3g_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["3g", "3g"], msg="SMS", direction="mo")

    @test_tracker_info(uuid="45cbddd3-889d-46ab-8d7f-9dd971287155")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_3g_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["3g", "3g"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="7dacd6b2-9d21-4c4d-bec4-fdfe685cdce8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_3g_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["3g", "3g"], msg="SMS", direction="mt")

    @test_tracker_info(uuid="24268e9f-b047-4c67-92f9-22e0bd8b3a11")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["volte", "volte"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="1d72b01d-5ca7-4899-ae57-ecbeff09bc39")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_psim_dds_slot_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="ca2ad510-7f5e-49e4-861e-d433f86c2237")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["volte", "volte"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="63a0480a-18dd-43e5-82e9-45e008346ea9")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_psim_dds_slot_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_0,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="5e51f0d9-f1b6-4bfe-88ab-f28ebaa6ee55")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_esim_dds_slot_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="fcc7e8aa-41a4-48a1-9586-d6080c77a79b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["volte", "volte"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="f633bf56-2d15-462b-994d-e9294d87ca23")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_esim_dds_slot_0(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_0, SimSlotInfo.SLOT_1],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=0,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="3c336061-32cf-4e9a-bb1e-b54e3357e644")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["volte", "volte"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="50ee8103-0196-4194-b982-9d07c68e57e4")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_csfb_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["volte", "csfb"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="ec09405d-b12d-405c-9bfd-ba3eb20eb752")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_csfb_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["volte", "csfb"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="26bea731-b653-4e9f-98d1-1b290b959bfc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_csfb_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["volte", "csfb"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="ecc010da-1798-4da3-b041-13e2b2547548")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_csfb_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["volte", "csfb"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="cf4c5bd0-525a-497a-a0f8-17acd9dbeabd")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_csfb_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["volte", "csfb"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="603f22db-913b-4ad3-b148-7c6d3624bc09")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_csfb_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["volte", "csfb"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="561efaf1-7fe4-4196-991e-d03eee28fb4e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_csfb_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["volte", "csfb"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="6f383ef0-d99a-4a3d-b137-e24fa03306b9")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_csfb_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["volte", "csfb"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="eeaa1262-c2a0-4f47-baa5-7435fa9e9315")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_csfb_volte_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["csfb", "volte"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="478f5497-cc21-4634-8b97-df70dbe286c0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_csfb_volte_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["csfb", "volte"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="1c4af9c6-87d6-438c-aba7-70d8bb4b357e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_csfb_volte_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["csfb", "volte"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="825daee3-db6c-404a-a454-cea98182bf5a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_csfb_volte_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["csfb", "volte"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="50fe9f3e-eae1-4a01-8655-02340f85037a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_csfb_volte_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["csfb", "volte"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="bae89139-f73f-4a06-bb65-a0bae385fae9")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_csfb_volte_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["csfb", "volte"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="42e897e3-4411-45a0-bf62-3ea6f59c2617")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_csfb_volte_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["csfb", "volte"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="9847b0c8-517e-42ea-9306-8a4a1cd46cd8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_csfb_volte_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["csfb", "volte"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="5057f8e4-19e7-42c0-bc63-1678d8ce1504")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_3g_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["volte", "3g"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="52bb44ae-0263-4415-8a61-337a8f990f8b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_3g_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["volte", "3g"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="deb00e73-b63a-4ed8-8b7f-953704b5d783")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_3g_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["volte", "3g"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="e0aa9846-2c02-4ba1-aeef-08a673c497ae")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_3g_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["volte", "3g"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="ef06ae23-6f52-4c3b-b228-5c95ed780cd2")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_3g_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["volte", "3g"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="69a62cd6-290b-4e58-81ff-0b35ac82262c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_3g_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["volte", "3g"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="645cef41-ddf8-49b4-8a58-2da019883f32")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_3g_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["volte", "3g"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="b0b8aac3-cc85-47d9-828a-8016138fe466")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_3g_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["volte", "3g"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="1dcebefb-3338-4550-96fa-07b64493db1c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_3g_volte_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["3g", "volte"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="3d06854e-5b86-46fb-9ca2-a217b026733d")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_3g_volte_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["3g", "volte"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="0c0f72bc-4076-411d-8a8d-fc6ae414a73a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_3g_volte_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["3g", "volte"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="b63211fa-baf0-4dff-bd18-d7f80e85e551")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_3g_volte_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["3g", "volte"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="80c2fe4d-e87a-45d7-9b83-23863e75cd94")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_3g_volte_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["3g", "volte"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="84be29a1-0b29-4785-baaa-6cf84c503fa6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_3g_volte_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["3g", "volte"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="591d2948-2257-4a46-8ccb-5c628d85fc43")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_3g_volte_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["3g", "volte"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="67e4dae5-8ca5-475f-af0e-f91b89df68ed")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_3g_volte_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["3g", "volte"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="35d33d3e-f618-4ce1-8b40-3dac0ef2731a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_csfb_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["csfb", "csfb"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="179e49c7-7066-4285-9b5b-3ef639d8c5e4")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_csfb_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["csfb", "csfb"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="09d6954f-d760-47e5-8667-3ed317fdbfbc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_csfb_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["csfb", "csfb"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="80f8c18f-2bd6-4310-be39-472d7a24e08a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_csfb_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["csfb", "csfb"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="b700d261-7616-4226-95cc-59ec54cc2678")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_csfb_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["csfb", "csfb"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="5cb2cc81-bf3e-4025-b85b-2bf1a4797e41")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_csfb_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["csfb", "csfb"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="1af2ac12-4d2d-4a36-8c46-8b3013eadab2")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_csfb_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["csfb", "csfb"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="66d8108e-8dd9-42e3-b2cd-49a538beecf6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_csfb_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["csfb", "csfb"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="a35df875-72eb-43d7-874c-a7b3f0aea2a9")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_csfb_3g_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["csfb", "3g"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="cf718bda-75d6-4906-a33e-110610b01d4d")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_csfb_3g_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["csfb", "3g"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="342cbc1a-7151-425c-9bd6-81808a5eb7e6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_csfb_3g_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["csfb", "3g"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="01e33aa4-27a9-48fd-b9e8-313980d06b0d")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_csfb_3g_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["csfb", "3g"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="fe527335-731e-49a5-a07e-f4999c536153")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_csfb_3g_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["csfb", "3g"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="1c56f866-3b3c-45c0-9c13-face44246aca")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_csfb_3g_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["csfb", "3g"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="4affd77a-afdc-4ac9-ba8a-a3599efe1e96")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_csfb_3g_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["csfb", "3g"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="8440c05e-28d9-45c7-b32e-127f240d12f0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_csfb_3g_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["csfb", "3g"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="a53ebb84-945e-4068-a78a-fd78362e8073")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_3g_csfb_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["3g", "csfb"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="23bedcbc-7c09-430d-a162-04de75244fd8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_3g_csfb_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["3g", "csfb"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="e1e1ef53-d91b-4b10-9bd6-e065ca48ab94")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_3g_csfb_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["3g", "csfb"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="e813ae3b-b875-43f6-a055-d2119cec9786")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_3g_csfb_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["3g", "csfb"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="d5863aab-a46a-4363-8bf8-5dcfc29a9055")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_3g_csfb_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["3g", "csfb"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="79a0bd58-0de0-471e-9e53-9cc655700428")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_3g_csfb_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["3g", "csfb"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="e9a340f4-22a7-4786-bb5b-370295324d5a")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_3g_csfb_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["3g", "csfb"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="8a261b43-2532-4c47-ac0c-3a5dd0d51b69")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_3g_csfb_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["3g", "csfb"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="2efdf7da-d2ec-4580-a164-5f7b740f9ac6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_3g_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 0, mo_rat=["3g", "3g"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="459e9b40-ad4e-4a89-ac89-f3c8ec472d3f")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_3g_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            0, None, 1, mo_rat=["3g", "3g"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="130a0e85-1653-4ddf-81b9-dadd26dde1e3")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_3g_psim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 0, mt_rat=["3g", "3g"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="e81f0b33-38b3-4a4d-9e05-fb44a689230b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_3g_psim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 0, 1, mt_rat=["3g", "3g"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="61894370-93b5-4ab5-80c7-d50948d38471")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_3g_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 0, mo_rat=["3g", "3g"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="8d41ee9a-fed9-4472-ada7-007e56690c67")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_3g_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            1, None, 1, mo_rat=["3g", "3g"], msg="MMS", direction="mo")

    @test_tracker_info(uuid="6aa41641-2619-48f6-8c5f-1c06260f0e28")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_3g_esim_dds_slot_0(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 0, mt_rat=["3g", "3g"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="94d8e05d-eb99-4a71-be00-e725cbd05cae")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_3g_esim_dds_slot_1(self):
        return dsds_message_test(
            self.log,
            self.android_devices,
            None, 1, 1, mt_rat=["3g", "3g"], msg="MMS", direction="mt")

    @test_tracker_info(uuid="207a23b7-17f1-4e27-892d-6c276f463b07")
    @TelephonyBaseTest.tel_test_wrap
    def test_sms_mo_mt_at_the_same_time(
        self,
        slot_0_nw_gen="volte",
        slot_1_nw_gen="volte"):
        ad = self.android_devices[0]
        slot_0_subid = get_subid_from_slot_index(ad.log, ad, 0)
        slot_1_subid = get_subid_from_slot_index(ad.log, ad, 1)

        if slot_0_subid == INVALID_SUB_ID or slot_1_subid == INVALID_SUB_ID:
            self.log.error("Not all slots have valid sub ID.")
            raise signals.TestFailure("Failed",
                extras={"fail_reason": "Not all slots have valid sub ID"})

        slot0_phone_setup_func = phone_setup_on_rat(
            self.log,
            ad,
            slot_0_nw_gen,
            only_return_fn=True)

        tasks = [(slot0_phone_setup_func, (self.log, ad, slot_0_subid))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            self.tel_logger.set_result(CallResult("CALL_SETUP_FAILURE"))
            raise signals.TestFailure("Failed",
                extras={"fail_reason": "Phone Failed to Set Up Properly."})

        slot1_phone_setup_func = phone_setup_on_rat(
            self.log,
            ad,
            slot_1_nw_gen,
            only_return_fn=True)

        tasks = [(slot1_phone_setup_func, (self.log, ad, slot_1_subid))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            self.tel_logger.set_result(CallResult("CALL_SETUP_FAILURE"))
            raise signals.TestFailure("Failed",
                extras={"fail_reason": "Phone Failed to Set Up Properly."})

        ad_host = self.android_devices[0]
        ad_mt = self.android_devices[1]
        ad_mo = self.android_devices[2]

        for ads in [[slot_0_subid, slot_1_subid], [slot_1_subid, slot_0_subid]]:
            set_message_subid(ad_host, ads[0])
            _, mt_sub_id, _ = get_subid_on_same_network_of_host_ad(
                self.android_devices, host_sub_id=ads[0])

            _, _, mo_sub_id = get_subid_on_same_network_of_host_ad(
                self.android_devices, host_sub_id=ads[1])
            set_message_subid(ad_mo, mo_sub_id)

            res = self._msim_sms_collision_test(
                ad_host,
                ad_mo,
                ad_mt,
                ad_host,
                ads[0],
                mo_sub_id,
                mt_sub_id,
                ads[1])

            if not res:
                log_messaging_screen_shot(
                    ad_host,
                    test_name="sms tx subid: %s; sms rx subid: %s" % (
                        ads[0], ads[1]))
                log_messaging_screen_shot(
                    ad_mo, test_name="sms tx subid: %s" % mo_sub_id)
                log_messaging_screen_shot(
                    ad_mt, test_name="sms rx subid: %s" % mt_sub_id)

                return False

    @test_tracker_info(uuid="e0483de8-f760-4e40-a451-a867c7f94d3a")
    @TelephonyBaseTest.tel_test_wrap
    def test_sms_mt_at_both_slots_at_the_same_time(
        self,
        slot_0_nw_gen="volte",
        slot_1_nw_gen="volte"):
        return self.multiple_mt_sms(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            power_off=False)

    @test_tracker_info(uuid="6594e5b5-6baf-4f1e-865b-0ea70ef23aee")
    @TelephonyBaseTest.tel_test_wrap
    def test_sms_mt_at_both_slots_when_power_off(
        self,
        slot_0_nw_gen="volte",
        slot_1_nw_gen="volte"):
        return self.multiple_mt_sms(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            power_off=True)

    @test_tracker_info(uuid="13dfe0a1-c393-4c1a-b636-ac39d47a70b5")
    @TelephonyBaseTest.tel_test_wrap
    def test_interworking_between_mo_voice_call_in_collision_with_mt_sms(self):
        result = True
        self.log.info("Scenario 1: MO voice call at slot 0 & MT SMS at slot 0")
        if not self._test_msim_voice_call_in_collision_with_mt_sms(
            0, 0, 0, rat=["volte", "volte"], call_direction="mo"):
            result =  False
        self.log.info("Scenario 2: MO voice call at slot 0 & MT SMS at slot 1")
        if not self._test_msim_voice_call_in_collision_with_mt_sms(
            0, 1, 0, rat=["volte", "volte"], call_direction="mo"):
            result =  False
        self.log.info("Scenario 3: MO voice call at slot 1 & MT SMS at slot 0")
        if not self._test_msim_voice_call_in_collision_with_mt_sms(
            1, 0, 0, rat=["volte", "volte"], call_direction="mo"):
            result =  False
        self.log.info("Scenario 1: MO voice call at slot 1 & MT SMS at slot 1")
        if not self._test_msim_voice_call_in_collision_with_mt_sms(
            1, 1, 0, rat=["volte", "volte"], call_direction="mo"):
            result =  False
        return result

    @test_tracker_info(uuid="a5c90676-c2ba-4ac6-b639-8f895d98480c")
    @TelephonyBaseTest.tel_test_wrap
    def test_interworking_between_mt_voice_call_in_collision_with_mt_sms(self):
        result = True
        self.log.info("Scenario 1: MT voice call at slot 0 & MT SMS at slot 0")
        if not self._test_msim_voice_call_in_collision_with_mt_sms(
            0, 0, 0, rat=["volte", "volte"], call_direction="mt"):
            result =  False
        self.log.info("Scenario 2: MT voice call at slot 0 & MT SMS at slot 1")
        if not self._test_msim_voice_call_in_collision_with_mt_sms(
            0, 1, 0, rat=["volte", "volte"], call_direction="mt"):
            result =  False
        self.log.info("Scenario 3: MT voice call at slot 1 & MT SMS at slot 0")
        if not self._test_msim_voice_call_in_collision_with_mt_sms(
            1, 0, 0, rat=["volte", "volte"], call_direction="mt"):
            result =  False
        self.log.info("Scenario 4: MT voice call at slot 1 & MT SMS at slot 1")
        if not self._test_msim_voice_call_in_collision_with_mt_sms(
            1, 1, 0, rat=["volte", "volte"], call_direction="mt"):
            result =  False
        return result

    # e+e
    @test_tracker_info(uuid="7acde3ba-9478-4feb-924c-ff48b7c1faa6")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_esim_port_0_dds_slot_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="6819f18a-afc2-4d90-9db1-701e098002fc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_esim_port_0_dds_slot_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="55090c8a-43e7-452c-94dd-4f49070999d3")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_esim_port_0_dds_slot_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=2,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="ac211113-66bd-447b-9164-287fd410411c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_esim_port_0_dds_slot_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=2,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="52d7db08-333e-4a3e-93f2-79413c107efc")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_esim_port_1_dds_slot_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=2,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="a4d9fbd5-6288-4a5a-ab2f-1d823c05eb00")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_esim_port_1_dds_slot_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=2,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="2aadcb03-1b36-47dd-ad0a-096e934210ff")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mo_volte_esim_port_1_dds_slot_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=1,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="50653a52-ea60-4186-9d57-34fe06743c80")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_sms_mt_volte_esim_port_1_dds_slot_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=1,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="6068c87d-93c9-43eb-a355-c2233230e3bd")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_esim_port_0_dds_slot_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="7e5620fe-6f8d-4d00-a1ad-a633d5929980")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_esim_port_0_dds_slot_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="15829355-42c0-4e15-b17b-c269e5c28801")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_esim_port_0_dds_slot_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=2,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="9f95ad3c-9af0-4475-9f8b-b2264bb65190")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_esim_port_0_dds_slot_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_1,
            dds_slot=2,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="8453ca48-8fff-448a-b085-e94d7844f5e0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_esim_port_1_dds_slot_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=2,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="0fbbf77d-063e-4f7b-87a9-1aab268b36f1")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_esim_port_1_dds_slot_2(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=2,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="e02e9bc0-bbd7-440a-b784-70c895036a42")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mo_volte_esim_port_1_dds_slot_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=1,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="59b06e08-ac7f-429c-a67c-83b37b049602")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_mms_mt_volte_esim_port_1_dds_slot_1(self):
        return dsds_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=SimSlotInfo.SLOT_2,
            dds_slot=1,
            msg_type="MMS",
            direction="mt",
            streaming=False)