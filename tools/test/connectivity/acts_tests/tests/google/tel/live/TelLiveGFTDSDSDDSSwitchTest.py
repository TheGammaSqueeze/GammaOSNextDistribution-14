#!/usr/bin/env python3
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

import time

from acts import signals
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.loggers.protos.telephony_metric_pb2 import TelephonyVoiceTestResult
from acts_contrib.test_utils.tel.loggers.telephony_metric_logger import TelephonyMetricLogger
from acts_contrib.test_utils.tel.tel_defines import SimSlotInfo
from acts_contrib.test_utils.tel.tel_dsds_utils import dsds_dds_swap_call_streaming_test
from acts_contrib.test_utils.tel.tel_dsds_utils import dsds_dds_swap_message_streaming_test
from acts_contrib.test_utils.tel.tel_defines import YOUTUBE_PACKAGE_NAME
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_SMS_RECEIVE
from acts_contrib.test_utils.tel.tel_defines import INVALID_SUB_ID
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_DISABLED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_data_utils import reboot_test
from acts_contrib.test_utils.tel.tel_data_utils import start_youtube_video
from acts_contrib.test_utils.tel.tel_data_utils import wait_for_cell_data_connection_for_subscription
from acts_contrib.test_utils.tel.tel_ims_utils import is_volte_enabled
from acts_contrib.test_utils.tel.tel_ims_utils import set_wfc_mode_for_subscription
from acts_contrib.test_utils.tel.tel_ims_utils import toggle_volte_for_subscription
from acts_contrib.test_utils.tel.tel_ims_utils import toggle_wfc_for_subscription
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_wfc_enabled
from acts_contrib.test_utils.tel.tel_message_utils import sms_send_receive_verify_for_subscription
from acts_contrib.test_utils.tel.tel_message_utils import mms_send_receive_verify
from acts_contrib.test_utils.tel.tel_message_utils import log_messaging_screen_shot
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte_for_subscription
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_on_rat
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_from_slot_index
from acts_contrib.test_utils.tel.tel_subscription_utils import get_default_data_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_slot_index_from_subid
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_from_slot_index
from acts_contrib.test_utils.tel.tel_subscription_utils import set_message_subid
from acts_contrib.test_utils.tel.tel_subscription_utils import set_subid_for_data
from acts_contrib.test_utils.tel.tel_subscription_utils import set_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import set_dds_on_slot_0
from acts_contrib.test_utils.tel.tel_subscription_utils import set_dds_on_slot_1
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_on_same_network_of_host_ad
from acts_contrib.test_utils.tel.tel_test_utils import verify_http_connection
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_on_rat
from acts_contrib.test_utils.tel.tel_voice_utils import two_phone_call_msim_for_slot
from acts_contrib.test_utils.tel.tel_wifi_utils import check_is_wifi_connected
from acts_contrib.test_utils.tel.tel_wifi_utils import ensure_wifi_connected
from acts.utils import rand_ascii_str

CallResult = TelephonyVoiceTestResult.CallResult.Value
_WAIT_TIME_FOR_MEP_ENABLE_INTERVAL = 60
_WAIT_TIME_FOR_MEP_ENABLE = 180


class TelLiveGFTDSDSDDSSwitchTest(TelephonyBaseTest):
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
        self.android_devices[0].force_stop_apk(YOUTUBE_PACKAGE_NAME)
        ensure_phones_idle(self.log, self.android_devices)

    def _msim_message_test(
        self,
        ad_mo,
        ad_mt,
        mo_sub_id,
        mt_sub_id, msg="SMS",
        max_wait_time=MAX_WAIT_TIME_SMS_RECEIVE,
        expected_result=True):
        """Make MO/MT SMS/MMS at specific slot.

        Args:
            ad_mo: Android object of the device sending SMS/MMS
            ad_mt: Android object of the device receiving SMS/MMS
            mo_sub_id: Sub ID of MO device
            mt_sub_id: Sub ID of MT device
            max_wait_time: Max wait time before SMS/MMS is received.
            expected_result: True for successful sending/receiving and False on
                             the contrary

        Returns:
            True if the result matches expected_result and False on the
            contrary.
        """

        if msg == "SMS":
            for length in self.message_lengths:
                message_array = [rand_ascii_str(length)]
                if not sms_send_receive_verify_for_subscription(
                    self.log,
                    ad_mo,
                    ad_mt,
                    mo_sub_id,
                    mt_sub_id,
                    message_array,
                    max_wait_time):
                    ad_mo.log.warning(
                        "%s of length %s test failed", msg, length)
                    return False
                else:
                    ad_mo.log.info(
                        "%s of length %s test succeeded", msg, length)
            self.log.info("%s test of length %s characters succeeded.",
                msg, self.message_lengths)

        elif msg == "MMS":
            for length in self.message_lengths:
                message_array = [("Test Message", rand_ascii_str(length), None)]

                if not mms_send_receive_verify(
                    self.log,
                    ad_mo,
                    ad_mt,
                    message_array,
                    max_wait_time,
                    expected_result):
                    self.log.warning("%s of body length %s test failed",
                        msg, length)
                    return False
                else:
                    self.log.info(
                        "%s of body length %s test succeeded", msg, length)
            self.log.info("%s test of body lengths %s succeeded",
                          msg, self.message_lengths)
        return True

    def _test_dds_switch_during_data_transfer(
        self,
        slot_0_nw_gen="volte",
        slot_1_nw_gen="volte",
        call_slot=0,
        call_direction=None,
        call_or_sms_or_mms="call",
        streaming=True,
        is_airplane_mode=False,
        wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED]):
        """Switch DDS and make voice call (VoLTE/WFC/CS call)/SMS/MMS together
        with Youtube playing after each DDS switch at specific slot in specific
        RAT.

        Test step:
            1. Get sub ID of each slot of the primary device.
            2. Set up phones in desired RAT.
            3. Switch DDS to slot 0.
            4. Check HTTP connection after DDS switch.
            5. Play Youtube.
            6. Make voice call (VoLTE/WFC/CS call)/SMS/MMS
            7. Switch DDS to slot 1 and repeat step 4-6.
            8. Switch DDS to slot 0 again and repeat step 4-6.

        Args:
            1, slot_0_nw_gen: Network generation of slot 0 on the primary device
            2, slot_1_nw_gen: Network generation of slot 1 on the primary device
            3. call_slot: Slot for making voice call
            4. call_direction: "mo" or "mt" or None to stoping making call.
            5. call_or_sms_or_mms: Voice call or SMS or MMS
            6. streaming: True for playing Youtube after DDS switch and False on
                the contrary.
            7. is_airplane_mode: True of False for WFC setup
            8. wfc_mode: Cellular preferred or Wi-Fi preferred.

        Returns:
            True or False
        """
        ad = self.android_devices[0]
        slot_0_subid = get_subid_from_slot_index(self.log, ad, 0)
        slot_1_subid = get_subid_from_slot_index(self.log, ad, 1)

        if slot_0_subid == INVALID_SUB_ID or slot_1_subid == INVALID_SUB_ID:
            ad.log.error("Not all slots have valid sub ID.")
            raise signals.TestFailure("Failed",
                extras={"fail_reason": "Not all slots have valid sub ID"})

        ad.log.info(
            "Step 0: Set up phone in desired RAT (slot 0: %s, slot 1: %s)",
            slot_0_nw_gen, slot_1_nw_gen)

        if not phone_setup_on_rat(
            self.log,
            ad,
            slot_0_nw_gen,
            slot_0_subid,
            is_airplane_mode,
            wfc_mode[0],
            self.wifi_network_ssid,
            self.wifi_network_pass):

            self.log.error("Phone Failed to Set Up Properly.")
            self.tel_logger.set_result(CallResult("CALL_SETUP_FAILURE"))
            raise signals.TestFailure("Failed",
                extras={"fail_reason": "Phone Failed to Set Up Properly."})

        if not phone_setup_on_rat(
            self.log,
            ad,
            slot_1_nw_gen,
            slot_1_subid,
            is_airplane_mode,
            wfc_mode[1],
            self.wifi_network_ssid,
            self.wifi_network_pass):

            self.log.error("Phone Failed to Set Up Properly.")
            self.tel_logger.set_result(CallResult("CALL_SETUP_FAILURE"))
            raise signals.TestFailure("Failed",
                extras={"fail_reason": "Phone Failed to Set Up Properly."})

        is_slot0_in_call = is_phone_in_call_on_rat(
            self.log, ad, slot_0_nw_gen, True)
        is_slot1_in_call = is_phone_in_call_on_rat(
            self.log, ad, slot_1_nw_gen, True)

        for attempt in range(3):
            if attempt != 0:
                ad.log.info("Repeat step 1 to 4.")

            ad.log.info("Step 1: Switch DDS.")
            if attempt % 2 == 0:
                set_dds_on_slot_0(ad)
            else:
                set_dds_on_slot_1(ad)

            ad.log.info("Step 2: Check HTTP connection after DDS switch.")
            if not verify_http_connection(self.log, ad):
                ad.log.error("Failed to verify http connection.")
                return False
            else:
                ad.log.info("Verify http connection successfully.")

            if streaming:
                ad.log.info("Step 3: Start Youtube streaming.")
                if not start_youtube_video(ad):
                    ad.log.warning("Fail to bring up youtube video")
                time.sleep(10)
            else:
                ad.log.info("Step 3: Skip Youtube streaming.")

            if not call_direction:
                return True
            else:
                expected_result = True
                if call_direction == "mo":
                    ad_mo = self.android_devices[0]
                    ad_mt = self.android_devices[1]
                    mo_sub_id = get_subid_from_slot_index(self.log, ad, call_slot)
                    if call_or_sms_or_mms == "call":
                        set_voice_sub_id(ad_mo, mo_sub_id)
                        _, mt_sub_id, _ = get_subid_on_same_network_of_host_ad(
                            self.android_devices)

                        if call_slot == 0:
                            is_mo_in_call = is_slot0_in_call
                        elif call_slot == 1:
                            is_mo_in_call = is_slot1_in_call
                        is_mt_in_call = None

                    elif call_or_sms_or_mms == "sms":
                        set_message_subid(ad_mo, mo_sub_id)
                        _, mt_sub_id, _ = get_subid_on_same_network_of_host_ad(
                            self.android_devices, type="sms")
                        set_message_subid(ad_mt, mt_sub_id)

                    elif call_or_sms_or_mms == "mms":
                        current_data_sub_id = get_default_data_sub_id(ad_mo)
                        if mo_sub_id != current_data_sub_id:
                            ad_mo.log.warning(
                                "Current data sub ID (%s) does not match"
                                " message sub ID (%s). MMS should NOT be sent.",
                                current_data_sub_id, mo_sub_id)
                            expected_result = False
                        set_message_subid(ad_mo, mo_sub_id)
                        _, mt_sub_id, _ = get_subid_on_same_network_of_host_ad(
                            self.android_devices, type="sms")
                        set_message_subid(ad_mt, mt_sub_id)
                        set_subid_for_data(ad_mt, mt_sub_id)
                        ad_mt.droid.telephonyToggleDataConnection(True)

                elif call_direction == "mt":
                    ad_mo = self.android_devices[1]
                    ad_mt = self.android_devices[0]
                    mt_sub_id = get_subid_from_slot_index(self.log, ad, call_slot)
                    if call_or_sms_or_mms == "call":
                        set_voice_sub_id(ad_mt, mt_sub_id)
                        _, mo_sub_id, _ = get_subid_on_same_network_of_host_ad(
                            self.android_devices)

                        if call_slot == 0:
                            is_mt_in_call = is_slot0_in_call
                        elif call_slot == 1:
                            is_mt_in_call = is_slot1_in_call
                        is_mo_in_call = None

                    elif call_or_sms_or_mms == "sms":
                        set_message_subid(ad_mt, mt_sub_id)
                        _, mo_sub_id, _ = get_subid_on_same_network_of_host_ad(
                            self.android_devices, type="sms")
                        set_message_subid(ad_mo, mo_sub_id)

                    elif call_or_sms_or_mms == "mms":
                        current_data_sub_id = get_default_data_sub_id(ad_mt)
                        if mt_sub_id != current_data_sub_id:
                            ad_mt.log.warning(
                                "Current data sub ID (%s) does not match"
                                " message sub ID (%s). MMS should NOT be"
                                " received.", current_data_sub_id, mt_sub_id)
                            expected_result = False
                        set_message_subid(ad_mt, mt_sub_id)
                        _, mo_sub_id, _ = get_subid_on_same_network_of_host_ad(
                            self.android_devices, type="sms")
                        set_message_subid(ad_mo, mo_sub_id)
                        set_subid_for_data(ad_mo, mo_sub_id)
                        ad_mo.droid.telephonyToggleDataConnection(True)

                if call_or_sms_or_mms == "call":
                    self.log.info("Step 4: Make voice call.")
                    mo_slot = get_slot_index_from_subid(ad_mo, mo_sub_id)
                    mt_slot = get_slot_index_from_subid(ad_mt, mt_sub_id)
                    result = two_phone_call_msim_for_slot(
                        self.log,
                        ad_mo,
                        mo_slot,
                        None,
                        is_mo_in_call,
                        ad_mt,
                        mt_slot,
                        None,
                        is_mt_in_call)
                    self.tel_logger.set_result(result.result_value)

                    if not result:
                        self.log.error(
                            "Failed to make MO call from %s slot %s to %s"
                            " slot %s", ad_mo.serial, mo_slot, ad_mt.serial,
                            mt_slot)
                        raise signals.TestFailure("Failed",
                            extras={"fail_reason": str(result.result_value)})
                else:
                    self.log.info("Step 4: Send %s.", call_or_sms_or_mms)
                    if call_or_sms_or_mms == "sms":
                        result = self._msim_message_test(
                            ad_mo,
                            ad_mt,
                            mo_sub_id,
                            mt_sub_id,
                            msg=call_or_sms_or_mms.upper())
                    elif call_or_sms_or_mms == "mms":
                        result = self._msim_message_test(
                            ad_mo,
                            ad_mt,
                            mo_sub_id,
                            mt_sub_id,
                            msg=call_or_sms_or_mms.upper(),
                            expected_result=expected_result)
                    if not result:
                        log_messaging_screen_shot(
                            ad_mo, test_name="%s_tx" % call_or_sms_or_mms)
                        log_messaging_screen_shot(
                            ad_mt, test_name="%s_rx" % call_or_sms_or_mms)

                        return False
        return True

    def _test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
        self,
        slot_0_nw_gen="volte",
        slot_1_nw_gen="volte",
        call_slot=0,
        call_direction=None,
        streaming=True,
        airplane_mode_cycling=False,
        cellular_data_cycling=False,
        wifi_cycling=False,
        enable_volte=[True, True],
        enable_wfc=[True, True],
        wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
        is_airplane_mode=False,
        is_wifi_connected=False):
        """Switch DDS and make VoLTE/WFC call together with Youtube playing
        after each DDS switch at specific slot in specific RAT.

        Test step:
            1. Get sub ID of each slot of the primary device.
            2. Set up phones in desired RAT.
            3. Toggle on/off VoLTE/WFC and set WFC mode.
            4. Airplane mode or cellular data or Wi-Fi cycling.
            5. Switch DDS to slot 0.
            6. Check HTTP connection after DDS switch.
            7. Play Youtube.
            8. Make VoLTE or WFC call.
            9. Switch DDS to slot 1 and repeat step 6-8.
            10. Switch DDS to slot 0 again and repeat step 6-8.

        Args:
            1, slot_0_nw_gen: Network generation of slot 0 on the primary device
            2, slot_1_nw_gen: Network generation of slot 1 on the primary device
            3. call_slot: Slot for making voice call
            4. call_direction: "mo" or "mt" or None to stoping making call.
            5. streaming: True for playing Youtube after DDS switch and False on
                the contrary.
            6. airplane_mode_cycling: True for cycling airplane
            7. cellular_data_cycling: True for cycling cellular data
            8. wifi_cycling: True for cycling Wi-Fi
            9. enable_volte: True for enabling and False for disabling VoLTE for
               each slot on the primary device
            10. enable_wfc: True for enabling and False for disabling WFC for
                each slot on the primary device
            11. wfc_mode: Cellular preferred or Wi-Fi preferred.
            12. is_airplane_mode: True of False for WFC setup

        Returns:
            True or False
        """
        ad = self.android_devices[0]
        slot_0_subid = get_subid_from_slot_index(self.log, ad, 0)
        slot_1_subid = get_subid_from_slot_index(self.log, ad, 1)

        if slot_0_subid == INVALID_SUB_ID or slot_1_subid == INVALID_SUB_ID:
            ad.log.error("Not all slots have valid sub ID.")
            raise signals.TestFailure("Failed",
                extras={"fail_reason": "Not all slots have valid sub ID"})

        ad.log.info(
            "Step 0: Set up phone in desired RAT (slot 0: %s, slot 1: %s)",
            slot_0_nw_gen, slot_1_nw_gen)

        if not phone_setup_on_rat(
            self.log,
            ad,
            slot_0_nw_gen,
            slot_0_subid,
            is_airplane_mode,
            wfc_mode[0],
            self.wifi_network_ssid,
            self.wifi_network_pass):

            self.log.error("Phone Failed to Set Up Properly.")
            self.tel_logger.set_result(CallResult("CALL_SETUP_FAILURE"))
            raise signals.TestFailure("Failed",
                extras={"fail_reason": "Phone Failed to Set Up Properly."})

        if not phone_setup_on_rat(
            self.log,
            ad,
            slot_1_nw_gen,
            slot_1_subid,
            is_airplane_mode,
            wfc_mode[1],
            self.wifi_network_ssid,
            self.wifi_network_pass):

            self.log.error("Phone Failed to Set Up Properly.")
            self.tel_logger.set_result(CallResult("CALL_SETUP_FAILURE"))
            raise signals.TestFailure("Failed",
                extras={"fail_reason": "Phone Failed to Set Up Properly."})

        is_slot0_in_call = is_phone_in_call_on_rat(
            self.log, ad, slot_0_nw_gen, True)
        is_slot1_in_call = is_phone_in_call_on_rat(
            self.log, ad, slot_1_nw_gen, True)

        if is_wifi_connected:
            if not ensure_wifi_connected(
                self.log,
                ad,
                self.wifi_network_ssid,
                self.wifi_network_pass,
                apm=False):
                return False
            time.sleep(5)

        ad.log.info("Step 1: Enable/disable VoLTE and WFC.")
        for sub_id, volte in zip([slot_0_subid, slot_1_subid], enable_volte):
            if not toggle_volte_for_subscription(
                self.log,
                ad,
                sub_id,
                new_state=volte):
                return False

        for sub_id, wfc, mode in \
            zip([slot_0_subid, slot_1_subid], enable_wfc, wfc_mode):
            if not toggle_wfc_for_subscription(self.log, ad, new_state=wfc, sub_id=sub_id):
                return False
            if not set_wfc_mode_for_subscription(ad, mode, sub_id=sub_id):
                return False

        if airplane_mode_cycling:
            ad.log.info("Step 2: Airplane mode cycling.")
            ad.log.info("Step 2-1: Toggle on airplane mode.")
            if not toggle_airplane_mode(self.log, ad, True):
                ad.log.error("Failed to toggle on airplane mode.")
                return False
            time.sleep(5)
            ad.log.info("Step 2-2: Toggle off airplane mode.")
            if not toggle_airplane_mode(self.log, ad, False):
                ad.log.error("Failed to toggle off airplane mode.")
                return False

            if is_airplane_mode:
                time.sleep(5)
                ad.log.info("Step 2-3: Toggle on airplane mode again.")
                if not toggle_airplane_mode(self.log, ad, True):
                    ad.log.error("Failed to toggle on airplane mode.")
                    return False

                if wfc_mode[0] or wfc_mode[1]:
                    time.sleep(5)
                    ad.log.info("Step 2-4: Toggle on Wi-Fi again.")
                    if not ensure_wifi_connected(
                        self.log,
                        ad,
                        self.wifi_network_ssid,
                        self.wifi_network_pass,
                        apm=is_airplane_mode):
                        return False
                    time.sleep(5)

        if cellular_data_cycling:
            if call_slot == 0:
                sub_id = slot_0_subid
            elif call_slot == 1:
                sub_id = slot_1_subid
            ad.log.info("Step 2: Cellular data cycling")
            ad.log.info("Step 2-1: Toggle off cellular data.")
            ad.droid.telephonyToggleDataConnection(False)
            if not check_is_wifi_connected(
                self.log,
                ad,
                self.wifi_network_ssid):
                if not wait_for_cell_data_connection_for_subscription(
                        self.log, ad, sub_id, False):
                    ad.log.error("Failed to disable cellular data")
                    return False

                if not verify_internet_connection(
                    self.log,
                    ad,
                    expected_state=False):
                    ad.log.error("Internet still accessible when cellular data"
                    " is disabled.")
                    return False
            time.sleep(5)
            ad.log.info("Step 2-2: Toggle on cellular data.")
            ad.droid.telephonyToggleDataConnection(True)
            if not check_is_wifi_connected(
                self.log,
                ad,
                self.wifi_network_ssid):
                if not wait_for_cell_data_connection_for_subscription(
                        self.log, ad, sub_id, True):
                    ad.log.error("Failed to enable cellular data")
                    return False
                if not verify_internet_connection(self.log, ad, retries=3):
                    ad.log.error(
                        "Internet inaccessible when cellular data is enabled.")
                    return False

        if wifi_cycling:
            ad.log.info("Step 2: Wi-Fi cycling")
            ad.log.info("Step 2-1: Toggle on Wi-Fi.")
            if not ensure_wifi_connected(
                self.log,
                ad,
                self.wifi_network_ssid,
                self.wifi_network_pass,
                apm=is_airplane_mode):
                return False
            time.sleep(5)
            ad.log.info("Step 2-2: Toggle off Wi-Fi.")
            ad.droid.wifiToggleState(False)
            time.sleep(5)

            if (call_slot == 0 and slot_0_nw_gen == "wfc") or \
                (call_slot == 1 and slot_1_nw_gen == "wfc") or is_wifi_connected:
                if not ensure_wifi_connected(
                    self.log,
                    ad,
                    self.wifi_network_ssid,
                    self.wifi_network_pass,
                    apm=is_airplane_mode):
                    return False

        for attempt in range(3):
            if attempt != 0:
                ad.log.info("Repeat step 1 to 4.")

            ad.log.info("Step 3: Switch DDS.")
            if attempt % 2 == 0:
                set_dds_on_slot_0(ad)
            else:
                set_dds_on_slot_1(ad)

            ad.log.info("Step 4: Check HTTP connection after DDS switch.")
            if not verify_http_connection(self.log, ad):
                ad.log.error("Failed to verify http connection.")
                return False
            else:
                ad.log.info("Verify http connection successfully.")

            if streaming:
                ad.log.info("Step 5: Start Youtube streaming.")
                if not start_youtube_video(ad):
                    ad.log.warning("Fail to bring up youtube video")
                time.sleep(10)
            else:
                ad.log.info("Step 5: Skip Youtube streaming.")

            if not call_direction:
                return True
            else:
                if call_direction == "mo":
                    ad_mo = self.android_devices[0]
                    ad_mt = self.android_devices[1]
                    mo_sub_id = get_subid_from_slot_index(self.log, ad, call_slot)

                    set_voice_sub_id(ad_mo, mo_sub_id)
                    _, mt_sub_id, _ = get_subid_on_same_network_of_host_ad(
                        self.android_devices)

                    if call_slot == 0:
                        is_mo_in_call = is_slot0_in_call
                    elif call_slot == 1:
                        is_mo_in_call = is_slot1_in_call
                    is_mt_in_call = None

                elif call_direction == "mt":
                    ad_mo = self.android_devices[1]
                    ad_mt = self.android_devices[0]
                    mt_sub_id = get_subid_from_slot_index(self.log, ad, call_slot)

                    set_voice_sub_id(ad_mt, mt_sub_id)
                    _, mo_sub_id, _ = get_subid_on_same_network_of_host_ad(
                        self.android_devices)

                    if call_slot == 0:
                        is_mt_in_call = is_slot0_in_call
                    elif call_slot == 1:
                        is_mt_in_call = is_slot1_in_call
                    is_mo_in_call = None

                if (call_slot == 0 and slot_0_nw_gen == "wfc") or \
                    (call_slot == 1 and slot_1_nw_gen == "wfc"):
                    if not wait_for_wfc_enabled(self.log, ad):
                        return False

                self.log.info("Step 6: Make voice call.")
                mo_slot = get_slot_index_from_subid(ad_mo, mo_sub_id)
                mt_slot = get_slot_index_from_subid(ad_mt, mt_sub_id)
                result = two_phone_call_msim_for_slot(
                    self.log,
                    ad_mo,
                    mo_slot,
                    None,
                    is_mo_in_call,
                    ad_mt,
                    mt_slot,
                    None,
                    is_mt_in_call)
                self.tel_logger.set_result(result.result_value)

                if not result:
                    self.log.error(
                        "Failed to make MO call from %s slot %s to %s slot %s",
                        ad_mo.serial, mo_slot, ad_mt.serial, mt_slot)
                    raise signals.TestFailure("Failed",
                        extras={"fail_reason": str(result.result_value)})

        return True

    def _test_dds_switch_volte_cycling(self, slot=0):
        """ VoLTE cycling after DDS switch.

        Test steps:
            1. Enable VoLTE.
            2. Disable VoLTE.
            3. Switch DDS to slot 0.
            4. Check HTTP connection after DDS switch.
            5. Enable VoLTE again.
            6. Check if IMS can be registered successfully and if VoLTE is
               available.
            7. Repeat steps 2-6 for 2 times and each time DDS should be switched
               to another slot.

        Args:
            slot: slot to be tested

        Returns:
            True or False
        """
        ad = self.android_devices[0]
        slot_0_subid = get_subid_from_slot_index(self.log, ad, 0)
        slot_1_subid = get_subid_from_slot_index(self.log, ad, 1)

        if slot == 0:
            sub_id = slot_0_subid
        elif slot == 1:
            sub_id = slot_1_subid

        ad.log.info("Step 1: Enable VoLTE for sub ID %s.", sub_id)
        if not phone_setup_volte_for_subscription(self.log, ad, sub_id):
            return False

        for attempt in range(3):
            if attempt != 0:
                ad.log.info("Repeat step 2 to 4.")

            ad.log.info("Step 2-1: Disable VoLTE for sub ID %s.", sub_id)
            if not toggle_volte_for_subscription(
                self.log, ad, sub_id, new_state=False):
                return False

            ad.log.info(
                "Step 2-2: Ensure VoLTE is disabled for sub ID %s.", sub_id)
            if is_volte_enabled(self.log, ad, sub_id):
                return False

            ad.log.info("Step 3: Switch DDS.")
            if attempt % 2 == 0:
                set_dds_on_slot_0(ad)
            else:
                set_dds_on_slot_1(ad)

            ad.log.info("Step 4: Check HTTP connection after DDS switch.")
            if not verify_http_connection(self.log, ad):
                ad.log.error("Failed to verify http connection.")
                return False
            else:
                ad.log.info("Verify http connection successfully.")

            ad.log.info(
                "Step 5: Enable VoLTE again after DDS switch for sub ID %s.",
                sub_id)
            if not phone_setup_volte_for_subscription(self.log, ad, sub_id):
                return False

        return True

    @test_tracker_info(uuid="06908fb0-aaaa-4c95-b073-ea5ba8977050")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_when_volte_enabled(self):
        ad = self.android_devices[0]
        slot_0_subid = get_subid_from_slot_index(self.log, ad, 0)
        slot_1_subid = get_subid_from_slot_index(self.log, ad, 1)

        ad.log.info("Step 0: Ensure VoLTE is enabled as initial condition.")
        if not phone_setup_volte_for_subscription(self.log, ad, slot_0_subid):
            return False
        if not phone_setup_volte_for_subscription(self.log, ad, slot_1_subid):
            return False

        for attempt in range(3):
            ad.log.info("Step 1: Switch DDS.")
            if attempt % 2 == 0:
                set_dds_on_slot_0(ad)
            else:
                set_dds_on_slot_1(ad)

            ad.log.info("Step 2: Check HTTP connection after DDS switch.")
            if not verify_http_connection(self.log, ad):
                ad.log.error("Failed to verify http connection.")
                return False
            else:
                ad.log.info("Verify http connection successfully.")

            ad.log.info("Step 3: Ensure VoLTE is still enabled after DDS"
                " switch.")
            if not phone_setup_volte_for_subscription(
                self.log, ad, slot_0_subid):
                return False
            if not phone_setup_volte_for_subscription(
                self.log, ad, slot_1_subid):
                return False
        return True

    @test_tracker_info(uuid="6b41d84c-4485-47b0-a5d8-eac16ed36258")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_and_reboot_when_volte_enabled(self):
        ad = self.android_devices[0]
        slot_0_subid = get_subid_from_slot_index(self.log, ad, 0)
        slot_1_subid = get_subid_from_slot_index(self.log, ad, 1)

        ad.log.info("Step 0: Ensure VoLTE is enabled as initial condition.")
        if not phone_setup_volte_for_subscription(self.log, ad, slot_0_subid):
            return False
        if not phone_setup_volte_for_subscription(self.log, ad, slot_1_subid):
            return False

        for attempt in range(3):
            ad.log.info("Step 1: Switch DDS.")
            if attempt % 2 == 0:
                set_dds_on_slot_0(ad)
            else:
                set_dds_on_slot_1(ad)

            ad.log.info("Step 2: Check HTTP connection after DDS switch.")
            if not verify_http_connection(self.log, ad):
                ad.log.error("Failed to verify http connection.")
                return False
            else:
                ad.log.info("Verify http connection successfully.")

            ad.log.info("Step 3: Reboot.")
            if not reboot_test(self.log, ad):
                return False

            ad.log.info("Step 4: Ensure VoLTE is still enabled after DDS"
                " switch.")
            if not phone_setup_volte_for_subscription(
                self.log, ad, slot_0_subid):
                return False
            if not phone_setup_volte_for_subscription(
                self.log, ad, slot_1_subid):
                return False
        return True

    @test_tracker_info(uuid="bb440f33-ab0d-4999-885c-5c1f933430c4")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_when_volte_cycling_psim(self):
        return self._test_dds_switch_volte_cycling(slot=0)

    @test_tracker_info(uuid="cbd5a4ae-be37-4435-b9db-fe58e8fdac5f")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_when_volte_cycling_esim(self):
        return self._test_dds_switch_volte_cycling(slot=1)


    @test_tracker_info(uuid="2df5faf9-8318-4acb-9068-e6ec0481c2ca")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_volte(self):
        return self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte")

    @test_tracker_info(uuid="eb73506e-c604-48df-be04-9b602a801afc")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_call_mo_volte_psim(self):
        return self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mo")

    @test_tracker_info(uuid="2e2feab1-65a8-40a7-8666-0c46cb3411a4")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_call_mo_volte_esim(self):
        return self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mo")

    @test_tracker_info(uuid="f2a7d62c-1f54-4081-b7bb-4782c5482b41")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_call_mt_volte_psim(self):
        return self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mt")

    @test_tracker_info(uuid="df211078-b260-499d-8f7e-86cad039c5f5")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_call_mt_volte_esim(self):
        return self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mt")

    @test_tracker_info(uuid="bd77782d-f43d-40c6-9982-47cd452d980f")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_call_mo_csfb_psim(self):
        return self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="csfb",
            slot_1_nw_gen="csfb",
            call_slot=0,
            call_direction="mo")

    @test_tracker_info(uuid="361a6f69-e6ea-4013-960d-732931fcd130")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_call_mo_csfb_esim(self):
        return self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="csfb",
            slot_1_nw_gen="csfb",
            call_slot=1,
            call_direction="mo")

    @test_tracker_info(uuid="26186d4f-0b0d-41c5-ab91-04e9851461f0")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_call_mt_csfb_psim(self):
        return self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="csfb",
            slot_1_nw_gen="csfb",
            call_slot=0,
            call_direction="mt")

    @test_tracker_info(uuid="7d31c644-a470-4eb9-b272-f0cfc34d23cb")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_call_mt_csfb_esim(self):
        return self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="csfb",
            slot_1_nw_gen="csfb",
            call_slot=1,
            call_direction="mt")

    @test_tracker_info(uuid="614076a6-b042-45f3-84fe-c84591e02f78")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_sms_volte(self):
        result = True
        self.log.info("Scenario 1: MO SMS at slot 0")
        if not self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mo",
            call_or_sms_or_mms="sms"):
            result = False
        self.log.info("Scenario 2: MO SMS at slot 1")
        if not self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mo",
            call_or_sms_or_mms="sms"):
            result = False
        self.log.info("Scenario 3: MT SMS at slot 0")
        if not self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mt",
            call_or_sms_or_mms="sms"):
            result = False
        self.log.info("Scenario 4: MT SMS at slot 1")
        if not self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mt",
            call_or_sms_or_mms="sms"):
            result = False
        return result

    @test_tracker_info(uuid="5e61f007-7b01-4dee-ac2d-fd2225ac3dbd")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_mms_volte(self):
        result = True
        self.log.info("Scenario 1: MO MMS at slot 0")
        if not self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mo",
            call_or_sms_or_mms="mms"):
            result = False
        self.log.info("Scenario 2: MO MMS at slot 1")
        if not self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mo",
            call_or_sms_or_mms="mms"):
            result = False
        self.log.info("Scenario 3: MT MMS at slot 0")
        if not self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mt",
            call_or_sms_or_mms="mms"):
            result = False
        self.log.info("Scenario 4: MT MMS at slot 1")
        if not self._test_dds_switch_during_data_transfer(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mt",
            call_or_sms_or_mms="mms"):
            result = False
        return result

    @test_tracker_info(uuid="47b9bf08-2c17-4646-b1d3-3d191318bc0d")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_psim(self):
        return self._test_dds_switch_during_data_transfer(
            "volte", "volte", 0, "mo", "call", False)

    @test_tracker_info(uuid="eef31675-f0a3-4086-8474-d67614ede507")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_esim(self):
        return self._test_dds_switch_during_data_transfer(
            "volte", "volte", 1, "mo", "call", False)

    @test_tracker_info(uuid="ce8b6ce8-d314-49ca-bead-391c15809235")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_psim(self):
        return self._test_dds_switch_during_data_transfer(
            "volte", "volte", 0, "mt", "call", False)

    @test_tracker_info(uuid="64c941e0-fcab-43ca-a988-f667398f1997")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_esim(self):
        return self._test_dds_switch_during_data_transfer(
            "volte", "volte", 1, "mt", "call", False)

    @test_tracker_info(uuid="28963e86-f8ce-4324-8ce8-8e6628fd2d99")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_sms_volte(self):
        result = True
        self.log.info("Scenario 1: MO SMS at slot 0")
        if not self._test_dds_switch_during_data_transfer(
            "volte", "volte", 0, "mo", "sms", False):
            result = False
        self.log.info("Scenario 2: MO SMS at slot 1")
        if not self._test_dds_switch_during_data_transfer(
            "volte", "volte", 1, "mo", "sms", False):
            result = False
        self.log.info("Scenario 3: MT SMS at slot 0")
        if not self._test_dds_switch_during_data_transfer(
            "volte", "volte", 0, "mt", "sms", False):
            result = False
        self.log.info("Scenario 4: MT SMS at slot 1")
        if not self._test_dds_switch_during_data_transfer(
            "volte", "volte", 1, "mt", "sms", False):
            result = False
        return result

    @test_tracker_info(uuid="915c0eb3-1a84-4eb0-8cba-cafe32c0d604")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_mms_volte(self):
        result = True
        self.log.info("Scenario 1: MO MMS at slot 0")
        if not self._test_dds_switch_during_data_transfer(
            "volte", "volte", 0, "mo", "mms", False):
            result = False
        self.log.info("Scenario 2: MO MMS at slot 1")
        if not self._test_dds_switch_during_data_transfer(
            "volte", "volte", 1, "mo", "mms", False):
            result = False
        self.log.info("Scenario 3: MT MMS at slot 0")
        if not self._test_dds_switch_during_data_transfer(
            "volte", "volte", 0, "mt", "mms", False):
            result = False
        self.log.info("Scenario 4: MT MMS at slot 1")
        if not self._test_dds_switch_during_data_transfer(
            "volte", "volte", 1, "mt", "mms", False):
            result = False
        return result

    @test_tracker_info(uuid="d58939c0-d246-453e-9eac-54152d6dc70c")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_psim_with_wfc_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED])

    @test_tracker_info(uuid="61dfb957-6349-4190-8e63-973558b1292b")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_esim_with_wfc_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED])

    @test_tracker_info(uuid="127377f2-973f-4758-b138-4c0dd276f893")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_psim_with_wfc_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED])

    @test_tracker_info(uuid="a149d357-27a7-490d-a30b-70f44cd43ac7")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_esim_with_wfc_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED])

    @test_tracker_info(uuid="8823e87c-0e4d-435d-a17f-84e1b65c1012")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_psim_with_wfc_on_wifi_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_wifi_connected=True)

    @test_tracker_info(uuid="bd038a77-baa6-483d-9af0-fe18d50d7f1f")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_esim_with_wfc_on_wifi_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_wifi_connected=True)

    @test_tracker_info(uuid="3f96fb8a-d4ee-49b8-8958-45cd509ed7b8")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_psim_with_wfc_on_wifi_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_wifi_connected=True)

    @test_tracker_info(uuid="1f89da8b-e559-4e96-afc7-0d2127616c56")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_esim_with_wfc_on_wifi_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_wifi_connected=True)

    @test_tracker_info(uuid="bc522b4d-2d26-4b5f-b82c-f92356f103d0")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_psim_cellular_preferred_apm_on_with_volte_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="970ccdb4-c7b3-4b56-b93b-f811407c82cb")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_esim_cellular_preferred_apm_on_with_volte_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="26c8b63e-631c-42d0-868b-03c2db6181b7")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_psim_wifi_preferred_apm_off_with_volte_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="efd73091-8667-42a3-8551-eafa497fc383")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_esim_wifi_preferred_apm_off_with_volte_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="618768b9-83c2-4ab7-b1fb-10a4037c5834")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_psim_cellular_preferred_apm_on_with_volte_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="24d695a1-7421-4038-bb07-4d81f3f6d05b")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_esim_cellular_preferred_apm_on_with_volte_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="2f28db8f-c0c3-4cf6-9f6f-439c9e32d9f3")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_psim_wifi_preferred_apm_off_with_volte_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="60d851c5-f79d-46e7-b921-b510bcdc9024")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_esim_wifi_preferred_apm_off_with_volte_on_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="092b1e43-3de4-4b08-b526-4f3f1e71a47a")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_psim_with_wfc_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED])

    @test_tracker_info(uuid="3b876b39-1cfb-4adb-a45c-11a02890f8e1")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_esim_with_wfc_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED])

    @test_tracker_info(uuid="96c42ffb-5b4e-4ab0-b52a-8b498a25f759")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_psim_with_wfc_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED])

    @test_tracker_info(uuid="fbd4c30c-fef9-4100-b704-eb27d3bcb7ae")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_esim_with_wfc_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED])

    @test_tracker_info(uuid="fbd88b9d-e27b-4c06-a983-a780d0c00623")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_psim_with_wfc_on_wifi_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_wifi_connected=True)

    @test_tracker_info(uuid="0fabca6f-28c4-4843-af70-f33d806d8dc1")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_esim_with_wfc_on_wifi_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_wifi_connected=True)

    @test_tracker_info(uuid="9b060264-69d8-437e-9981-b86e213952c5")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_psim_with_wfc_on_wifi_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_wifi_connected=True)

    @test_tracker_info(uuid="3a2effa4-728a-4160-9e0c-2aeafc7ba153")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_esim_with_wfc_on_wifi_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_wifi_connected=True)

    @test_tracker_info(uuid="ad491362-8bcc-4097-84af-932878002ce6")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_psim_cellular_preferred_apm_on_with_volte_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="c137fe4e-380b-4dc5-8996-c8c5654596f7")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_esim_cellular_preferred_apm_on_with_volte_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="e7936ce8-1652-4b21-b3f0-5327084b823c")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_psim_wifi_preferred_apm_off_with_volte_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="86db06b4-907f-4085-af8e-75c983831bb0")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_esim_wifi_preferred_apm_off_with_volte_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="e43b23b5-022a-4830-9110-839ece333f6f")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_psim_cellular_preferred_apm_on_with_volte_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="00d0bfc2-2121-4ba9-9dd7-72bf78380121")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_esim_cellular_preferred_apm_on_with_volte_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="4921a948-54d4-4945-81ea-02893d10b6e6")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_psim_wifi_preferred_apm_off_with_volte_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="ed4b8ba4-1b31-4e3c-9be3-0e184e324523")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_esim_wifi_preferred_apm_off_with_volte_on_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="1fb43960-51dd-4be9-adf1-51c84cb8d85a")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_psim_with_wfc_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED])

    @test_tracker_info(uuid="1052e02f-5a4b-4826-9c47-9ab6d142f300")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_esim_with_wfc_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED])

    @test_tracker_info(uuid="23bb1991-6ff1-4528-aeee-1ec0c7b525be")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_psim_with_wfc_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED])

    @test_tracker_info(uuid="1d5842c5-91f5-4c87-9f65-67891d255d43")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_esim_with_wfc_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED])

    @test_tracker_info(uuid="380bd592-5437-4e16-9564-5f47b066cab2")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_psim_with_wfc_on_wifi_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_wifi_connected=True)

    @test_tracker_info(uuid="90bb2647-71f1-44cd-bff3-5bbb720e59b7")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_esim_with_wfc_on_wifi_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_wifi_connected=True)

    @test_tracker_info(uuid="5bca72c8-62d0-41bf-8888-310cd235dac4")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_psim_with_wfc_on_wifi_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_wifi_connected=True)

    @test_tracker_info(uuid="13805ecf-3cf9-44c8-98bc-a099edb36340")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_esim_with_wfc_on_wifi_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_wifi_connected=True)

    @test_tracker_info(uuid="33ed3dee-581f-4ae8-b236-1317377a6ca1")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_psim_cellular_preferred_apm_on_with_with_volte_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="88391458-6886-483f-a997-c62fd6dfd0b8")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_esim_cellular_preferred_apm_on_with_with_volte_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="966bcb75-dd2d-4400-a880-e7407989ee52")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_psim_wifi_preferred_apm_off_with_with_volte_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="7ff48189-5b4b-4b2d-a96a-fa66e86d0596")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_esim_wifi_preferred_apm_off_with_with_volte_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="ab503377-7150-4a6d-a7c1-b21009a69402")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_psim_wfc_cellular_preferred_apm_on_with_with_volte_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="7f02ee60-19e9-4602-8f6d-a13b976a6bba")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_esim_wfc_cellular_preferred_apm_on_with_with_volte_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="e93fa3ac-c5cd-4e21-b872-5172aa22d030")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_psim_wifi_preferred_apm_off_with_with_volte_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="c2af6998-f702-4e36-bbaa-f099a307b21a")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_esim_wifi_preferred_apm_off_with_with_volte_on_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="45ba6e90-bdaa-4dc0-a504-c596bafdfaad")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_psim_with_wfc_off_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[False, False],
            wfc_mode=[WFC_MODE_DISABLED, WFC_MODE_DISABLED])

    @test_tracker_info(uuid="1b573f40-3eaf-4149-baad-2e73e5bf15f4")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_esim_with_wfc_off_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[False, False],
            wfc_mode=[WFC_MODE_DISABLED, WFC_MODE_DISABLED])

    @test_tracker_info(uuid="13511fb6-2984-40c3-b1b9-22f27f241c07")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_psim_with_wfc_off_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[False, False],
            wfc_mode=[WFC_MODE_DISABLED, WFC_MODE_DISABLED])

    @test_tracker_info(uuid="61cf33d1-e1b2-427a-bb38-29a4c7566947")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_esim_with_wfc_off_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[False, False],
            wfc_mode=[WFC_MODE_DISABLED, WFC_MODE_DISABLED])

    @test_tracker_info(uuid="31a2a741-c825-46f8-8e0a-8487fab9940e")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_psim_with_wfc_off_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[False, False],
            wfc_mode=[WFC_MODE_DISABLED, WFC_MODE_DISABLED])

    @test_tracker_info(uuid="9fb2a85f-08b3-4d5d-9e03-3f7f67039148")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_esim_with_wfc_off_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[False, False],
            wfc_mode=[WFC_MODE_DISABLED, WFC_MODE_DISABLED])

    @test_tracker_info(uuid="30eba519-b349-4a75-9f31-3fea0d1a8447")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_psim_with_wfc_off_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[False, False],
            wfc_mode=[WFC_MODE_DISABLED, WFC_MODE_DISABLED])

    @test_tracker_info(uuid="37240938-3ce1-4ad2-b35a-a8862dc2c70f")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_esim_with_wfc_off_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[False, False],
            wfc_mode=[WFC_MODE_DISABLED, WFC_MODE_DISABLED])

    @test_tracker_info(uuid="a7321b9c-fb2c-4a03-9566-05e4244ae6fd")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_psim_with_wfc_off_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[False, False],
            wfc_mode=[WFC_MODE_DISABLED, WFC_MODE_DISABLED])

    @test_tracker_info(uuid="6de8d678-2f72-41ea-9ed9-47b27afee038")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_volte_esim_with_wfc_off_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[False, False],
            wfc_mode=[WFC_MODE_DISABLED, WFC_MODE_DISABLED])

    @test_tracker_info(uuid="7bc16dcf-6dab-4eec-931d-9b342caa7a32")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_psim_with_wfc_off_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[False, False],
            wfc_mode=[WFC_MODE_DISABLED, WFC_MODE_DISABLED])

    @test_tracker_info(uuid="9c13e51b-385d-4df6-90b7-33b5e185f225")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_volte_esim_with_wfc_off_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="volte",
            slot_1_nw_gen="volte",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[True, True],
            enable_wfc=[False, False],
            wfc_mode=[WFC_MODE_DISABLED, WFC_MODE_DISABLED])

    @test_tracker_info(uuid="161341e7-5c2d-45f9-9b11-4f44d542cd01")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_psim_cellular_preferred_apm_on_with_volte_off_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="4d43f92f-562f-4bf1-bc25-71410f14425c")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_esim_cellular_preferred_apm_on_with_volte_off_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="fe83e92d-c554-4f81-a447-d58300426da7")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_psim_wifi_preferred_apm_off_with_volte_off_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="76ba6834-1523-4ce8-80b9-079f2428da67")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_esim_wifi_preferred_apm_off_with_volte_off_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="15289348-8e09-4997-b5a3-f66bb7e7dca1")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_psim_cellular_preferred_apm_on_with_volte_off_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="1ae4fa98-1ac3-4194-a483-097b7262415b")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_esim_cellular_preferred_apm_on_with_volte_off_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="13d0af2c-2870-4cbc-8a38-311f93cd4bd7")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_psim_wifi_preferred_apm_off_with_volte_off_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="5fbe2002-a02f-4750-81e5-4a06d7b62740")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_esim_wifi_preferred_apm_off_with_volte_off_apm_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            airplane_mode_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="f8f523d2-e432-45d4-a850-469a22894bc7")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_psim_cellular_preferred_apm_on_with_volte_off_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="750a8690-f9dd-4779-b13f-4011f478f194")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_esim_cellular_preferred_apm_on_with_volte_off_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="9fcda7e5-1705-4bbe-8f18-f005437c71f2")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_psim_wifi_preferred_apm_off_with_volte_off_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="0e054729-945a-4f6f-ad29-4d832f0b11ed")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_esim_wifi_preferred_apm_off_with_volte_off_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="2e387739-cc4a-4d48-aa56-83bf621835b1")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_psim_cellular_preferred_apm_on_with_volte_off_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="a0e2deda-9148-4665-abc8-7665e3818d06")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_esim_cellular_preferred_apm_on_with_volte_off_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="6a43acef-aaa1-4fe8-ae7e-c8e045bf8814")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_psim_wifi_preferred_apm_off_with_volte_off_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="95524166-212f-4e82-9e02-2f9b58d92a9f")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_esim_wifi_preferred_apm_off_with_volte_off_cellular_data_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            cellular_data_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="828ae64c-41b3-4974-a412-342d3ca16ce3")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_psim_cellular_preferred_apm_on_with_volte_off_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="343cf9dc-4f4c-4c0c-bd7b-ba664381f6bd")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_esim_cellular_preferred_apm_on_with_volte_off_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="6ef18605-bf4c-43d8-83fd-0bf71311973e")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_psim_wifi_preferred_apm_off_with_volte_off_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="9bddfe69-68e1-4d04-aeaa-75fb0f9ed9aa")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mo_wfc_esim_wifi_preferred_apm_off_with_volte_off_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mo",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="81e7d2b6-e3eb-4651-807f-66bf8eeeea93")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_psim_cellular_preferred_apm_on_with_volte_off_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="8c6da88b-be3a-4c0c-a239-255faf03a28b")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_esim_cellular_preferred_apm_on_with_volte_off_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_CELLULAR_PREFERRED, WFC_MODE_CELLULAR_PREFERRED],
            is_airplane_mode=True)

    @test_tracker_info(uuid="1b9ef6b4-c0c0-4375-b1a5-d569b946491e")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_psim_wifi_preferred_apm_off_with_volte_off_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=0,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    @test_tracker_info(uuid="5771fedb-5eed-4868-84a3-0d7a01474dcf")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_call_mt_wfc_esim_wifi_preferred_apm_off_with_volte_off_wifi_cycling(self):
        return self._test_dds_switch_during_data_transfer_with_apm_cycling_and_ims_setting(
            slot_0_nw_gen="wfc",
            slot_1_nw_gen="wfc",
            call_slot=1,
            call_direction="mt",
            streaming=True,
            wifi_cycling=True,
            enable_volte=[False, False],
            enable_wfc=[True, True],
            wfc_mode=[WFC_MODE_WIFI_PREFERRED, WFC_MODE_WIFI_PREFERRED],
            is_airplane_mode=False)

    # e+e call
    @test_tracker_info(uuid="ee94a2f1-9aac-4698-adc7-e5525541fac0")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_esim_port_0_volte_esim_port_1_volte(self):
        """ LTE DDS swap call test(Initial DDS is on esim port 0).

        1. Check HTTP connection when DDS is on esim port 0 and idle.
        2. Switch DDS to esim port 1.
        3. Check HTTP connection when DDS is on esim port 1 and idle.
        4. Switch DDS to esim port 0, make sure data works fine.
        """
        return dsds_dds_swap_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot = [SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            init_dds=1,
            test_slot=[None, None, None])

    @test_tracker_info(uuid="d74b7a10-b944-4b6b-8271-420beae810f4")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_esim_port_0_mo_volte_esim_port_1_volte(self):
        """ LTE DDS swap call test(Initial DDS is on esim port 0).

        1. Make MO call via esim port 0 when DDS is on esim port 0 and idle.
        2. Switch DDS to esim port 1.
        3. Make MO call via esim port 0 when DDS is on esim port 1 and idle.
        4. Switch DDS to esim port 0, make sure data works fine.

        After call end will check the dds slot if is attach to the network
        with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot = [SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            init_dds=1,
            test_slot=[
                SimSlotInfo.SLOT_1,
                SimSlotInfo.SLOT_1,
                SimSlotInfo.SLOT_1],
            direction="mo",
            duration=30,
            streaming=False)

    @test_tracker_info(uuid="b424f8de-591e-4a72-ba4e-5c0771e9629d")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_esim_port_0_mt_volte_esim_port_1_volte(self):
        """ LTE DDS swap call test(Initial DDS is on esim port 0).

        1. Receive MT call via esim port 0 when DDS is on esim port 0 and idle.
        2. Switch DDS to esim port 1.
        3. Receive MT call via esim port 0 when DDS is on esim port 1 and idle.
        4. Switch DDS to esim port 0, make sure data works fine.

        After call end will check the dds slot if is attach to the network
        with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot = [SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            init_dds=1,
            test_slot=[
                SimSlotInfo.SLOT_1,
                SimSlotInfo.SLOT_1,
                SimSlotInfo.SLOT_1],
            direction="mt",
            duration=30,
            streaming=False)

    @test_tracker_info(uuid="55b0f7ce-351f-4a46-a0cb-bf54a2404edb")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_esim_port_1_mo_volte_esim_port_0_volte(self):
        """ LTE DDS swap call test(Initial DDS is on esim port 0).

        1. Make MO call via esim port 1 when DDS is on esim port 0 and idle.
        2. Switch DDS to esim port 1.
        3. Make MO call via esim port 1 when DDS is on esim port 1 and idle.
        4. Switch DDS to esim port 0, make sure data works fine.

        After call end will check the dds slot if is attach to the network
        with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot = [SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            init_dds=1,
            test_slot=[
                SimSlotInfo.SLOT_2,
                SimSlotInfo.SLOT_2,
                SimSlotInfo.SLOT_2],
            direction="mo",
            duration=30,
            streaming=False)

    @test_tracker_info(uuid="27ecd41f-9e08-44ce-9d5d-95158cb7a354")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_esim_port_1_mt_volte_esim_port_0_volte(self):
        """ LTE DDS swap call test(Initial DDS is on esim port 0).

        1. Receive MT call via esim port 1 when DDS is on esim port 0 and idle.
        2. Switch DDS to esim port 1.
        3. Receive MT call via esim port 1 when DDS is on esim port 1 and idle.
        4. Switch DDS to esim port 0, make sure data works fine.

        After call end will check the dds slot if is attach to the network
        with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            sim_slot = [SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            init_dds=1,
            test_slot=[
                SimSlotInfo.SLOT_2,
                SimSlotInfo.SLOT_2,
                SimSlotInfo.SLOT_2],
            direction="mt",
            duration=30,
            streaming=False)

    # e+e message
    @test_tracker_info(uuid="")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_sms_esim_port_0_mo_volte_esim_port_1_volte(self):
        """ LTE DDS swap SMS test(Initial DDS is on esim_port_0).

        1. Make MO SMS via esim_port_0 when DDS is on esim_port_0 and idle.
        2. Switch DDS to esim_port_1.
        3. Make MO SMS via esim_port_0 when DDS is on esim_port_1 and idle.
        4. Switch DDS to esim_port_0, make sure data works fine.

        After Make SMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=[
                SimSlotInfo.SLOT_1,
                SimSlotInfo.SLOT_1,
                SimSlotInfo.SLOT_1],
            init_dds=1,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_sms_esim_port_0_mt_volte_esim_port_1_volte(self):
        """ LTE DDS swap SMS test(Initial DDS is on esim_port_0).

        1. Make MT SMS via esim_port_0 when DDS is on esim_port_0 and idle.
        2. Switch DDS to esim_port_1.
        3. Make MT SMS via esim_port_0 when DDS is on esim_port_1 and idle.
        4. Switch DDS to esim_port_0, make sure data works fine.

        After Receive SMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=[
                SimSlotInfo.SLOT_1,
                SimSlotInfo.SLOT_1,
                SimSlotInfo.SLOT_1],
            init_dds=1,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_sms_esim_port_1_mo_volte_esim_port_0_volte(self):
        """ LTE DDS swap SMS test(Initial DDS is on esim_port_0).

        1. Make MO SMS via esim_port_1 when DDS is on esim_port_0 and idle.
        2. Switch DDS to esim_port_1.
        3. Make MO SMS via esim_port_1 when DDS is on esim_port_1 and idle.
        4. Switch DDS to esim_port_0, make sure data works fine.

        After Make SMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=[
                SimSlotInfo.SLOT_2,
                SimSlotInfo.SLOT_2,
                SimSlotInfo.SLOT_2],
            init_dds=1,
            msg_type="SMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_sms_esim_port_1_mt_volte_esim_port_0_volte(self):
        """ LTE DDS swap SMS test(Initial DDS is on esim_port_0).

        1. Make MT SMS via esim_port_1 when DDS is on esim_port_0 and idle.
        2. Switch DDS to esim_port_1.
        3. Make MT SMS via esim_port_1 when DDS is on esim_port_1 and idle.
        4. Switch DDS to esim_port_0, make sure data works fine.

        After Make SMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=[
                SimSlotInfo.SLOT_2,
                SimSlotInfo.SLOT_2,
                SimSlotInfo.SLOT_2],
            init_dds=1,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_mms_esim_port_0_mo_volte_esim_port_1_volte(self):
        """ LTE DDS swap MMS test(Initial DDS is on esim_port_0).

        1. Make MO MMS via esim_port_0 when DDS is on esim_port_0 and idle.
        2. Switch DDS to esim_port_1.
        3. Make MO MMS via esim_port_0 when DDS is on esim_port_1 and idle.
        4. Switch DDS to esim_port_0, make sure data works fine.

        After Make MMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=[
                SimSlotInfo.SLOT_1,
                SimSlotInfo.SLOT_1,
                SimSlotInfo.SLOT_1],
            init_dds=1,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_mms_esim_port_0_mt_volte_esim_port_1_volte(self):
        """ LTE DDS swap MMS test(Initial DDS is on esim_port_0).

        1. Make MT MMS via esim_port_0 when DDS is on esim_port_0 and idle.
        2. Switch DDS to esim_port_1.
        3. Make MT MMS via esim_port_0 when DDS is on esim_port_1 and idle.
        4. Switch DDS to esim_port_0, make sure data works fine.

        After Receive MMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=[
                SimSlotInfo.SLOT_1,
                SimSlotInfo.SLOT_1,
                SimSlotInfo.SLOT_1],
            init_dds=1,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_mms_esim_port_1_mo_volte_esim_port_0_volte(self):
        """ LTE DDS swap MMS test(Initial DDS is on esim_port_0).

        1. Make MO MMS via esim_port_1 when DDS is on esim_port_0 and idle.
        2. Switch DDS to esim_port_1.
        3. Make MO MMS via esim_port_1 when DDS is on esim_port_1 and idle.
        4. Switch DDS to esim_port_0, make sure data works fine.

        After Make MMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=[
                SimSlotInfo.SLOT_2,
                SimSlotInfo.SLOT_2,
                SimSlotInfo.SLOT_2],
            init_dds=1,
            msg_type="MMS",
            direction="mo",
            streaming=False)

    @test_tracker_info(uuid="")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_mms_esim_port_1_mt_volte_esim_port_0_volte(self):
        """ LTE DDS swap MMS test(Initial DDS is on esim_port_0).

        1. Make MT MMS via esim_port_1 when DDS is on esim_port_0 and idle.
        2. Switch DDS to esim_port_1.
        3. Make MT MMS via esim_port_1 when DDS is on esim_port_1 and idle.
        4. Switch DDS to esim_port_0, make sure data works fine.

        After Make MMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            sim_slot=[SimSlotInfo.SLOT_1, SimSlotInfo.SLOT_2],
            test_rat=["volte", "volte"],
            test_slot=[
                SimSlotInfo.SLOT_2,
                SimSlotInfo.SLOT_2,
                SimSlotInfo.SLOT_2],
            init_dds=1,
            msg_type="MMS",
            direction="mt",
            streaming=False)