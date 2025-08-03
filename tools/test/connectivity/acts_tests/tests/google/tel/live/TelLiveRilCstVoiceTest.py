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
from datetime import datetime

from acts import signals
from acts.libs.proc import job
from acts.libs.utils.multithread import multithread_func
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.loggers.protos.telephony_metric_pb2 import TelephonyVoiceTestResult
from acts_contrib.test_utils.tel.loggers.telephony_metric_logger import TelephonyMetricLogger
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import INVALID_SUB_ID
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_data_utils import reboot_test
from acts_contrib.test_utils.tel.tel_ims_utils import toggle_wfc_for_subscription
from acts_contrib.test_utils.tel.tel_ims_utils import set_wfc_mode_for_subscription
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_wfc_enabled
from acts_contrib.test_utils.tel.tel_logging_utils import start_pixellogger_always_on_logging
from acts_contrib.test_utils.tel.tel_parse_utils import check_ims_cst_reg
from acts_contrib.test_utils.tel.tel_parse_utils import parse_cst_reg
from acts_contrib.test_utils.tel.tel_parse_utils import print_nested_dict
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_on_rat
from acts_contrib.test_utils.tel.tel_subscription_utils import get_incoming_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_slot_index_from_subid
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_from_slot_index
from acts_contrib.test_utils.tel.tel_subscription_utils import set_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import set_dds_on_slot
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_on_same_network_of_host_ad
from acts_contrib.test_utils.tel.tel_test_utils import verify_http_connection
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_on_rat
from acts_contrib.test_utils.tel.tel_voice_utils import two_phone_call_msim_for_slot
from acts.utils import set_location_service
from acts.utils import get_current_epoch_time

WAIT_FOR_CST_REG_TIMEOUT = 120
CALCULATE_EVERY_N_CYCLES = 10

CallResult = TelephonyVoiceTestResult.CallResult.Value


class TelLiveRilCstVoiceTest(TelephonyBaseTest):
    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        start_pixellogger_always_on_logging(self.android_devices[0])
        self.tel_logger = TelephonyMetricLogger.for_test_case()


    def teardown_test(self):
        self.enable_cst(self.android_devices[0], None, False)
        self.force_roaming(self.android_devices[0], None, 0)
        ensure_phones_idle(self.log, self.android_devices)


    def enable_cst(self, ad, slot=0, enable=True):
        """Enable/disable Cross SIM Calling by SL4A API at given slot

            Args:
                ad: Android object
                slot: 0 for pSIM and 1 for eSIM
                enable: True fo enabling and False for disabling

            Raises:
                TestFailure if False is returned by
                imsMmTelIsCrossSimCallingEnabled.
        """
        if slot is None:
            slots = [0, 1]
        else:
            slots = [slot]

        for slot in slots:
            sub_id = get_subid_from_slot_index(self.log, ad, slot)
            if ad.droid.imsMmTelIsCrossSimCallingEnabled(sub_id) == enable:
                ad.log.info(
                    'Status of backup calling at slot %s is already %s.',
                    slot, enable)
            else:
                ad.droid.imsMmTelSetCrossSimCallingEnabled(sub_id, enable)
                time.sleep(3)
                if ad.droid.imsMmTelIsCrossSimCallingEnabled(sub_id) == enable:
                    ad.log.info(
                        'Backup calling at slot %s is set to %s successfully.',
                        slot, enable)
                else:
                    ad.log.error(
                        'Backup calling at slot %s is NOT set to %s.',
                        slot, enable)
                    raise signals.TestFailure(
                        "Failed",
                        extras={"fail_reason": "Failed to set Backup calling."})


    def get_force_roaming_state(self, ad, slot=0):
        """Get the value of the property:
                getprop persist.vendor.radio.force_roaming

            Args:
                ad: Android object
                slot: 0 for pSIM and 1 for eSIM

            Returns:
                0 for not roaming and 1 for roaming
        """
        cmd = 'adb -s %s shell getprop persist.vendor.radio.force_roaming%s' % (
            ad.serial, slot)
        result = job.run(cmd)
        return result.stdout


    def force_roaming(self, ad, slot=0, roaming=0):
        """Force assigned slot to roam ot not to roam by setting specific property

            Args:
                ad: Android object
                slot: 0 for pSIM and 1 for eSIM
                roaming: 1 to force to roam. Otherwise 0.

            Returns:
                True or False
        """
        if slot is None:
            slots = [0, 1]
        else:
            slots = [slot]

        need_reboot = 0
        for slot in slots:
            roamimg_state = self.get_force_roaming_state(ad, slot)
            if roamimg_state:
                if roamimg_state == str(roaming):
                    if roaming:
                        ad.log.info('Slot %s is already roaming.' % slot)
                    else:
                        ad.log.info('Slot %s is already on home network.' % slot)
                else:
                    cmd = 'adb -s %s shell setprop persist.vendor.radio.force_roaming%s %s' % (ad.serial, slot, roaming)
                    result = job.run(cmd)
                    self.log.info(result)
                    need_reboot = 1

        if not need_reboot:
            return True
        else:
            result = True
            if reboot_test(self.log, ad):
                for slot in slots:
                    roamimg_state = self.get_force_roaming_state(ad, slot)
                    if roamimg_state == str(roaming):
                        if roaming:
                            ad.log.info('Slot %s is now roaming.' % slot)
                        else:
                            ad.log.info('Slot %s is now on home network.' % slot)
                    else:
                        if roaming:
                            ad.log.error(
                                'Slot %s is expected to be roaming (roamimg state: %s).' % roamimg_state)
                        else:
                            ad.log.error(
                                'Slot %s is expected to be on home network (roamimg state: %s).' % roamimg_state)
                        result = False
            return result


    def msim_cst_registration(
            self,
            cst_slot,
            rat=["", ""],
            wfc_mode=WFC_MODE_WIFI_PREFERRED,
            force_roaming=False,
            test_cycles=1):
        """Make MO/MT voice call at specific slot in specific RAT with DDS at
        specific slot.

        Test step:
        1. Get sub IDs of specific slots of both MO and MT devices.
        2. Switch DDS to specific slot.
        3. Check HTTP connection after DDS switch.
        4. Set up phones in desired RAT.

        Args:
            cst_slot: Slot at which CST registered
            rat: RAT for both slots
            wfc_mode: cullelar-preferred or wifi-preferred
            force_roaming: True for fake roaming by setprop
            test_cycles: Amount of the test cycles

        Returns:
            True in the end. Otherwise the exception TestFailure will be raised.

        Raises:
            TestFailure if:
                1. Invalid sub ID is returned.
                2. DDS cannot be switched successfully.
                3. Http connection cannot be verified.
        """
        ads = self.android_devices
        set_location_service(ads[0], True)
        test_cycles = int(test_cycles)
        cst_reg_search_intervals = []
        cst_reg_fail = 0
        exit_due_to_high_fail_rate = False
        for attempt in range(test_cycles):
            self.log.info(
                '======> Test cycle %s/%s <======', attempt + 1, test_cycles)
            cst_reg_begin_time = datetime.now()
            cst_slot_sub_id = get_subid_from_slot_index(
                self.log, ads[0], cst_slot)
            if cst_slot_sub_id == INVALID_SUB_ID:
                ads[0].log.warning(
                    "Failed to get sub ID ar slot %s.", cst_slot)
                raise signals.TestFailure(
                    'Failed',
                    extras={
                        'fail_reason': 'Slot ID %s at slot %s is invalid.' % (
                            cst_slot_sub_id, cst_slot)})
            other_sub_id = get_subid_from_slot_index(
                self.log, ads[0], 1-cst_slot)
            self.enable_cst(ads[0], slot=cst_slot, enable=True)

            self.log.info("Step 1: Switch DDS.")
            if not set_dds_on_slot(ads[0], 1 - cst_slot):
                ads[0].log.warning("Failed to set DDS to slot %s.", 1 - cst_slot)
                raise signals.TestFailure(
                    'Failed',
                    extras={
                        'fail_reason': 'Failed to set DDS to slot %s.' % 1 - cst_slot})

            self.log.info("Step 2: Check HTTP connection after DDS switch.")
            if not verify_http_connection(self.log,
                ads[0],
                url="https://www.google.com",
                retry=5,
                retry_interval=15,
                expected_state=True):
                self.log.error("Failed to verify http connection.")
                raise signals.TestFailure(
                    'Failed',
                    extras={
                        'fail_reason': 'Failed to verify http connection.'})
            else:
                self.log.info("Verify http connection successfully.")

            self.log.info("Step 3: Set up phones in desired RAT.")
            phone_setup_on_rat(
                self.log,
                ads[0],
                rat[1-cst_slot],
                other_sub_id)

            phone_setup_on_rat(
                self.log,
                ads[0],
                rat[cst_slot],
                cst_slot_sub_id,
                False,
                wfc_mode)

            if toggle_wfc_for_subscription(
                self.log, ads[0], True, cst_slot_sub_id):
                if set_wfc_mode_for_subscription(
                    ads[0], wfc_mode, cst_slot_sub_id):
                    pass

            if force_roaming:
                self.force_roaming(ads[0], cst_slot, 1)

            if not wait_for_wfc_enabled(self.log, ads[0]):
                cst_reg_fail += 1
                if cst_reg_fail >= test_cycles/10:
                    exit_due_to_high_fail_rate = True

            cst_reg_end_time = datetime.now()
            ims_cst_reg_res = check_ims_cst_reg(
                ads[0],
                cst_slot,
                search_interval=[cst_reg_begin_time, cst_reg_end_time])

            while not ims_cst_reg_res:
                if (datetime.now() - cst_reg_end_time).total_seconds() > WAIT_FOR_CST_REG_TIMEOUT:
                    break
                time.sleep(1)
                ims_cst_reg_res = check_ims_cst_reg(
                    ads[0],
                    cst_slot,
                    search_interval=[cst_reg_begin_time, datetime.now()])

            if not ims_cst_reg_res:
                ads[0].log.error('IMS radio tech is NOT CrossStackEpdg.')
                cst_reg_fail += 1
                if cst_reg_fail >= test_cycles/10:
                    exit_due_to_high_fail_rate = True

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or (
                attempt == test_cycles - 1) or exit_due_to_high_fail_rate:

                parsing_fail = parse_cst_reg(
                    ads[0], cst_slot, cst_reg_search_intervals)
                ads[0].log.info('====== Failed cycles of CST registration ======')
                for each_dict in parsing_fail:
                    print_nested_dict(ads[0], each_dict)

            self.enable_cst(ads[0], None, False)

            if exit_due_to_high_fail_rate:
                ads[0].log.error(
                    'Test case is stopped due to fail rate is greater than 10%.')
                break

        return True


    def msim_cst_call_voice(
            self,
            mo_slot,
            mt_slot,
            mo_rat=["", ""],
            mt_rat=["", ""],
            call_direction="mo",
            wfc_mode=WFC_MODE_WIFI_PREFERRED,
            force_roaming=False,
            test_cycles=1,
            call_cycles=1):
        """Make MO/MT voice call at specific slot in specific RAT with DDS at
        specific slot.

        Test step:
        1. Get sub IDs of specific slots of both MO and MT devices.
        2. Switch DDS to specific slot.
        3. Check HTTP connection after DDS switch.
        4. Set up phones in desired RAT.
        5. Make voice call.

        Args:
            mo_slot: Slot making MO call (0 or 1)
            mt_slot: Slot receiving MT call (0 or 1)
            mo_rat: RAT for both slots of MO device
            mt_rat: RAT for both slots of MT device
            call_direction: "mo" or "mt"

        Returns:
            True in the end. Otherwise the exception TestFailure will be raised.

        Raises:
            TestFailure if:
                1. Invalid sub ID is returned.
                2. DDS cannot be switched successfully.
                3. Http connection cannot be verified.
        """
        ads = self.android_devices
        if call_direction == "mo":
            ad_mo = ads[0]
            ad_mt = ads[1]
        else:
            ad_mo = ads[1]
            ad_mt = ads[0]

        test_cycles = int(test_cycles)
        call_cycles = int(call_cycles)
        set_location_service(ads[0], True)
        cst_reg_search_intervals = []
        cst_reg_fail = 0
        exit_due_to_high_fail_rate = False
        dialed_call_amount = 0
        call_result_list = []
        call_result_cycle_list = []
        for attempt in range(test_cycles):
            self.log.info(
                '======> Test cycle %s/%s <======', attempt + 1, test_cycles)
            cst_reg_begin_time = datetime.now()
            if mo_slot is not None:
                mo_sub_id = get_subid_from_slot_index(self.log, ad_mo, mo_slot)
                if mo_sub_id == INVALID_SUB_ID:
                    ad_mo.log.warning(
                        "Failed to get sub ID ar slot %s.", mo_slot)
                    raise signals.TestFailure(
                        'Failed',
                        extras={
                            'fail_reason': 'Slot ID %s at slot %s is invalid.' % (
                                mo_sub_id, mo_slot)})
                mo_other_sub_id = get_subid_from_slot_index(
                    self.log, ad_mo, 1-mo_slot)
                set_voice_sub_id(ad_mo, mo_sub_id)
                self.enable_cst(ads[0], slot=mo_slot, enable=True)
            else:
                _, mo_sub_id, _ = get_subid_on_same_network_of_host_ad(ads)
                if mo_sub_id == INVALID_SUB_ID:
                    ad_mo.log.warning(
                        "Failed to get sub ID ar slot %s.", mo_slot)
                    raise signals.TestFailure(
                        'Failed',
                        extras={
                            'fail_reason': 'Slot ID %s at slot %s is invalid.' % (
                                mo_sub_id, mo_slot)})
                mo_slot = "auto"
                set_voice_sub_id(ad_mo, mo_sub_id)
            ad_mo.log.info("Sub ID for outgoing call at slot %s: %s",
                mo_slot, get_outgoing_voice_sub_id(ad_mo))

            if mt_slot is not None and mt_slot is not 'auto':
                mt_sub_id = get_subid_from_slot_index(
                    self.log, ad_mt, mt_slot)
                if mt_sub_id == INVALID_SUB_ID:
                    ad_mt.log.warning(
                        "Failed to get sub ID at slot %s.", mt_slot)
                    raise signals.TestFailure(
                        'Failed',
                        extras={
                            'fail_reason': 'Slot ID %s at slot %s is invalid.' % (
                                mt_sub_id, mt_slot)})
                mt_other_sub_id = get_subid_from_slot_index(
                    self.log, ad_mt, 1-mt_slot)
                set_voice_sub_id(ad_mt, mt_sub_id)
                self.enable_cst(ads[0], slot=mt_slot, enable=True)
            else:
                _, mt_sub_id, _ = get_subid_on_same_network_of_host_ad(ads)
                if mt_sub_id == INVALID_SUB_ID:
                    ad_mt.log.warning(
                        "Failed to get sub ID at slot %s.", mt_slot)
                    raise signals.TestFailure(
                        'Failed',
                        extras={
                            'fail_reason': 'Slot ID %s at slot %s is invalid.' % (
                                mt_sub_id, mt_slot)})
                mt_slot = "auto"
                set_voice_sub_id(ad_mt, mt_sub_id)
            ad_mt.log.info("Sub ID for incoming call at slot %s: %s", mt_slot,
                get_incoming_voice_sub_id(ad_mt))

            self.log.info("Step 1: Switch DDS.")

            dds_slot = 1
            if call_direction == "mo":
                dds_slot = 1 - get_slot_index_from_subid(ad_mo, mo_sub_id)
            else:
                dds_slot = 1 - get_slot_index_from_subid(ad_mt, mt_sub_id)

            if not set_dds_on_slot(ads[0], dds_slot):
                ads[0].log.warning("Failed to set DDS to slot %s.", dds_slot)
                raise signals.TestFailure(
                    'Failed',
                    extras={
                        'fail_reason': 'Failed to set DDS to slot %s.' % dds_slot})

            self.log.info("Step 2: Check HTTP connection after DDS switch.")
            if not verify_http_connection(self.log,
                ads[0],
                url="https://www.google.com",
                retry=5,
                retry_interval=15,
                expected_state=True):
                self.log.error("Failed to verify http connection.")
                raise signals.TestFailure(
                    'Failed',
                    extras={
                        'fail_reason': 'Failed to verify http connection.'})
            else:
                self.log.info("Verify http connection successfully.")

            if mo_slot == 0 or mo_slot == 1:
                phone_setup_on_rat(
                    self.log,
                    ad_mo,
                    mo_rat[1-mo_slot],
                    mo_other_sub_id)

                mo_phone_setup_argv = (
                self.log,
                ad_mo,
                mo_rat[mo_slot],
                mo_sub_id,
                False,
                wfc_mode)

                is_mo_in_call = is_phone_in_call_on_rat(
                    self.log, ad_mo, mo_rat[mo_slot], only_return_fn=True)
            else:
                mo_phone_setup_argv = (self.log, ad_mo, 'general')
                is_mo_in_call = is_phone_in_call_on_rat(
                    self.log, ad_mo, 'general', only_return_fn=True)

            if mt_slot == 0 or mt_slot == 1:
                phone_setup_on_rat(
                    self.log,
                    ad_mt,
                    mt_rat[1-mt_slot],
                    mt_other_sub_id)

                mt_phone_setup_argv = (
                self.log,
                ad_mt,
                mt_rat[mt_slot],
                mt_sub_id,
                False,
                wfc_mode)

                is_mt_in_call = is_phone_in_call_on_rat(
                    self.log, ad_mt, mt_rat[mt_slot], only_return_fn=True)
            else:
                mt_phone_setup_argv = (self.log, ad_mt, 'general')
                is_mt_in_call = is_phone_in_call_on_rat(
                    self.log, ad_mt, 'general', only_return_fn=True)

            self.log.info("Step 3: Set up phones in desired RAT.")

            tasks = [(phone_setup_on_rat, mo_phone_setup_argv),
                    (phone_setup_on_rat, mt_phone_setup_argv)]
            if not multithread_func(self.log, tasks):
                self.log.error("Phone Failed to Set Up Properly.")
                self.tel_logger.set_result(CallResult("CALL_SETUP_FAILURE"))
                raise signals.TestFailure("Failed",
                    extras={"fail_reason": "Phone Failed to Set Up Properly."})

            if toggle_wfc_for_subscription(self.log, ad_mo, True, mo_sub_id):
                if set_wfc_mode_for_subscription(ad_mo, wfc_mode, mo_sub_id):
                    pass

            if force_roaming:
                self.force_roaming(ads[0], 1-dds_slot, 1)

            if not wait_for_wfc_enabled(self.log, ads[0]):
                cst_reg_fail += 1
                if cst_reg_fail >= test_cycles/10:
                    exit_due_to_high_fail_rate = True

            cst_reg_end_time = datetime.now()
            ims_cst_reg_res = check_ims_cst_reg(
                ads[0],
                1-dds_slot,
                search_interval=[cst_reg_begin_time, cst_reg_end_time])

            while not ims_cst_reg_res:
                if (datetime.now() - cst_reg_end_time).total_seconds() > WAIT_FOR_CST_REG_TIMEOUT:
                    break
                time.sleep(1)
                ims_cst_reg_res = check_ims_cst_reg(
                    ads[0],
                    1-dds_slot,
                    search_interval=[cst_reg_begin_time, datetime.now()])

            if not ims_cst_reg_res:
                ads[0].log.error('IMS radio tech is NOT CrossStackEpdg.')
                cst_reg_fail += 1
                if cst_reg_fail >= test_cycles/10:
                    exit_due_to_high_fail_rate = True

            if ims_cst_reg_res and not exit_due_to_high_fail_rate:
                self.log.info("Step 4: Make voice call.")
                for cycle in range(
                    dialed_call_amount, dialed_call_amount+call_cycles):
                    self.log.info(
                        '======> CST voice call %s/%s <======',
                        cycle + 1,
                        dialed_call_amount+call_cycles)
                    result = two_phone_call_msim_for_slot(
                        self.log,
                        ad_mo,
                        get_slot_index_from_subid(ad_mo, mo_sub_id),
                        None,
                        is_mo_in_call,
                        ad_mt,
                        get_slot_index_from_subid(ad_mt, mt_sub_id),
                        None,
                        is_mt_in_call)
                    self.tel_logger.set_result(result.result_value)

                    if not result:
                        self.log.error(
                            "Failed to make MO call from %s slot %s to %s slot %s",
                                ad_mo.serial, mo_slot, ad_mt.serial, mt_slot)
                        call_result_list.append(False)
                        call_result_cycle_list.append(cycle + 1)
                        self._take_bug_report(
                            self.test_name, begin_time=get_current_epoch_time())
                    else:
                        call_result_list.append(True)
                dialed_call_amount = dialed_call_amount + call_cycles

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or (
                attempt == test_cycles - 1) or exit_due_to_high_fail_rate:

                parsing_fail = parse_cst_reg(
                    ad_mo, 1-dds_slot, cst_reg_search_intervals)
                ads[0].log.info('====== Failed cycles of CST registration ======')
                for each_dict in parsing_fail:
                    print_nested_dict(ads[0], each_dict)

                ads[0].log.info('====== Failed cycles of CST voice call ======')
                for index, value in enumerate(call_result_list):
                    if not value:
                        ads[0].log.warning(
                            'CST voice call cycle %s failed.', index+1)

                try:
                    fail_rate = (
                        len(call_result_list) - call_result_list.count(True))/len(
                            call_result_list)
                    ads[0].log.info('====== Summary ======')
                    ads[0].log.info(
                        'Total CST calls: %s',
                        len(call_result_list))
                    ads[0].log.warning(
                        'Total failed CST calls: %s',
                        call_result_list.count(False))
                    ads[0].log.info(
                        'Fail rate of CST voice call: %s', fail_rate)
                except Exception as e:
                    ads[0].log.error(
                        'Fail rate of CST voice call: ERROR (%s)', e)

            self.enable_cst(ads[0], None, False)

            if exit_due_to_high_fail_rate:
                ads[0].log.error(
                    'Test case is stopped due to fail rate is greater than 10%.')
                break

        return True


    @test_tracker_info(uuid="5475514a-8897-4dd4-900f-1dd435191d0b")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_psim_mo_cst_call_wifi_preferred(self):
        return self.msim_cst_call_voice(
            0,
            None,
            mo_rat=["2g", "general"],
            call_direction="mo",
            test_cycles=self.user_params.get(
                "psim_mo_cst_call_wifi_preferred_test_cycle", 1),
            call_cycles=self.user_params.get("cst_call_cycle", 1))


    @test_tracker_info(uuid="40c182b7-af25-428a-bae5-9203eed949d8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_psim_mo_cst_call_cellular_preferred(self):
        return self.msim_cst_call_voice(
            0,
            None,
            mo_rat=["2g", "general"],
            call_direction="mo",
            wfc_mode=WFC_MODE_CELLULAR_PREFERRED,
            test_cycles=self.user_params.get(
                "psim_mo_cst_call_cellular_preferred_test_cycle", 1),
            call_cycles=self.user_params.get("cst_call_cycle", 1))