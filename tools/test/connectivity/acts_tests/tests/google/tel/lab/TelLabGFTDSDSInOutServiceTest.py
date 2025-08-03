#!/usr/bin/env python3
#
#   Copyright 2022 - The Android Open Source Project
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
from acts import asserts
from acts.test_decorators import test_tracker_info
from acts.libs.utils.multithread import multithread_func
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.GFTInOutBaseTest import GFTInOutBaseTest
from acts_contrib.test_utils.tel.loggers.telephony_metric_logger import TelephonyMetricLogger
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_ims_registered
from acts_contrib.test_utils.tel.tel_data_utils import active_file_download_test
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_data_utils import wait_for_cell_data_connection
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_from_slot_index
from acts_contrib.test_utils.tel.tel_data_utils import wait_for_network_service
from acts_contrib.test_utils.tel.tel_subscription_utils import set_dds_on_slot
from acts_contrib.test_utils.tel.tel_subscription_utils import set_message_subid
from acts_contrib.test_utils.tel.tel_subscription_utils import set_voice_sub_id
from acts_contrib.test_utils.tel.tel_defines import INVALID_SUB_ID

from acts_contrib.test_utils.tel.gft_inout_utils import mo_voice_call
from acts_contrib.test_utils.tel.gft_inout_defines import VOICE_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import VOLTE_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import CSFB_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import WFC_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import NO_SERVICE_POWER_LEVEL
from acts_contrib.test_utils.tel.gft_inout_defines import IN_SERVICE_POWER_LEVEL

IDLE_CASE = 1
DATA_TRANSFER_CASE = 2
DATA_OFF_CASE = 3
IN_CALL_CASE = 4
CALL_DATA_CASE = 5
_VOLTE = "volte"


class TelLabGFTDSDSInOutServiceTest(GFTInOutBaseTest):
    def __init__(self, controllers):
        GFTInOutBaseTest.__init__(self, controllers)
        self.my_error_msg = ""

    def teardown_test(self):
        GFTInOutBaseTest.teardown_class(self)
        ensure_phones_idle(self.log, self.android_devices)

    def _dsds_in_out_service_test(self,
                                  case=IDLE_CASE,
                                  loop=1,
                                  idle_time=60,
                                  dds_slot=0,
                                  voice_slot=0,
                                  sms_slot=0,
                                  psim_rat=_VOLTE,
                                  esim_rat=_VOLTE):
        '''
            b/201599180
            Move UE from coverage area to no service area and UE shows no service
            Wait for a period of time, then re-enter coverage area

            Args:
                case: include IDLE_CAS, DATA_TRANSFER_CASE, DATA_OFF_CASE,
                    IN_CALL_CASE, CALL_DATA_CASE
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
                dds_slot: Preferred data slot
                voice_slot: Preferred voice slot
                sms_slot: Preferred SMS slot
                psim_rat: RAT on psim
                esim_rat: RAT on esim
            Returns:
                True if pass; False if fail
            Raises:
                TestFailure if not success.
        '''
        tasks = [(set_dds_on_slot, (ad, dds_slot))
                 for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            asserts.skip("Fail to to set up DDS")
        for ad in self.android_devices:
            voice_sub_id = get_subid_from_slot_index(self.log, ad, voice_slot)
            if voice_sub_id == INVALID_SUB_ID:
                asserts.skip("Failed to get sub ID ar slot %s.", voice_slot)
            else:
                ad.log.info(
                    "get_subid_from_slot_index voice_slot=%s. voice_sub_id=%s",
                    voice_slot, voice_sub_id)
            if not set_voice_sub_id(ad, voice_sub_id):
                ad.log.info("Fail to to set voice to slot %s", voice_sub_id)
            else:
                ad.log.info("set voice to slot %s", voice_sub_id)
        tasks = [(set_message_subid, (ad, sms_slot))
                 for ad in self.android_devices]
        if multithread_func(self.log, tasks):
            asserts.skip("Fail to to set up sms to slot %s", sms_slot)
        else:
            ad.log.info("set up sms to slot %s", sms_slot)

        for x in range(loop):
            self.log.info("%s loop: %s/%s", self.current_test_name, x + 1,
                          loop)
            if case == IDLE_CASE:
                asserts.assert_true(
                    self._dsds_in_out_service_idle_test(idle_time),
                    "Fail: %s." % ("_dsds_in_out_service_idle_test failure"),
                    extras={"failure_cause": self.my_error_msg})
            elif case == DATA_TRANSFER_CASE:
                asserts.assert_true(
                    self._dsds_in_out_service_data_transfer_test(idle_time),
                    "Fail: %s." %
                    ("_dsds_in_out_service_data_transfer_test failure"),
                    extras={"failure_cause": self.my_error_msg})
            elif case == DATA_OFF_CASE:
                asserts.assert_true(
                    self._dsds_in_out_service_data_off_test(idle_time),
                    "Fail: %s." %
                    ("_dsds_in_out_service_data_off_test failure"),
                    extras={"failure_cause": self.my_error_msg})
            elif case == IN_CALL_CASE:
                asserts.assert_true(
                    self._dsds_in_out_service_in_call_test(idle_time),
                    "Fail: %s." %
                    ("_dsds_in_out_service_in_call_test failure"),
                    extras={"failure_cause": self.my_error_msg})
            elif case == CALL_DATA_CASE:
                asserts.assert_true(
                    self._dsds_in_out_service_in_call_transfer_test(idle_time),
                    "Fail: %s." %
                    ("_dsds_in_out_service_in_call_transfer_test failure"),
                    extras={"failure_cause": self.my_error_msg})

            tasks = [(wait_for_network_service, (
                self.log,
                ad,
            )) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                asserts.assert_true(
                    False,
                    "Fail: %s." % ("wait_for_network_service failure"),
                    extras={"failure_cause": self.my_error_msg})
            tasks = [(self.verify_device_status, (ad, VOICE_CALL))
                     for ad in self.android_devices]
            asserts.assert_true(multithread_func(self.log, tasks),
                                "Fail: %s." % ("verify_device_status failure"),
                                extras={"failure_cause": self.my_error_msg})
        return True

    def _dsds_in_out_service_idle_test(self, idle_time=60):
        '''
            (1) UE is in idle
            (2) Move UE from coverage area to no service area and UE shows no service
            (3) Wait for a period of time, then re-enter coverage area

            Args:
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        return self._in_out_service_idle(idle_time)

    def _dsds_in_out_service_in_call_transfer_test(self, idle_time=60):
        '''
            (1) UE is performing data transfer (E.g. Use FTP or browse tools)
            (2) UE makes a MO call
            (3) Move UE from coverage area to no service area and UE shows no service
            (4) Wait for a period of time, then re-enter coverage area

            Args:
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        error_msg = ""
        tasks_a = [(active_file_download_test, (
            self.log,
            ad,
        )) for ad in self.android_devices]
        tasks_b = [(mo_voice_call, (self.log, ad, VOICE_CALL, False))
                   for ad in self.android_devices]
        tasks_b.extend(tasks_a)
        if not multithread_func(self.log, tasks_b):
            error_msg = "fail to perfrom data transfer/voice call"
            self.my_error_msg += error_msg
            return False
        self._in_out_service_idle(idle_time)
        return True

    def _dsds_in_out_service_in_call_test(self, idle_time=60):
        '''
            (1) UE is in call
            (2) Move UE from coverage area to no service area and UE shows no service
            (3) Wait for a period of time, then re-enter coverage area

            Args:
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        error_msg = ""
        tasks = [(mo_voice_call, (self.log, ad, VOICE_CALL, False))
                 for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            error_msg = "MO voice call fail"
            self.my_error_msg += error_msg
            self.log.error(error_msg)
            return False
        return self._in_out_service_idle(idle_time)

    def _dsds_in_out_service_data_off_test(self, idle_time=60):
        '''
            (1) Disable UE mobile data
            (2) Move UE from coverage area to no service area and UE shows no service
            (3) Wait for a period of time, then re-enter coverage area

            Args:
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        for ad in self.android_devices:
            ad.log.info("Turn off mobile data")
            ad.droid.telephonyToggleDataConnection(False)
            if not wait_for_cell_data_connection(self.log, ad, False):
                self.my_error_msg += "fail to turn off mobile data"
                return False
        self._in_out_service_idle(idle_time)
        for ad in self.android_devices:
            ad.log.info("Turn on mobile data")
            ad.droid.telephonyToggleDataConnection(True)
            #If True, it will wait for status to be DATA_STATE_CONNECTED
            if not wait_for_cell_data_connection(self.log, ad, True):
                self.my_error_msg += "fail to turn on mobile data"
                return False
        return True

    def _dsds_in_out_service_data_transfer_test(self,
                                                idle_time=60,
                                                file_name="10MB"):
        '''
            (1) UE is performing data transfer (E.g. Use FTP or browse tools)
            (2) Move UE from coverage area to no service area and UE shows no service
            (3) Wait for 1 min, then re-enter coverage area

            Args:
                idle_time: idle time at no service area
                file_name:
            Returns:
                True if pass; False if fail
            Raises:
                TestFailure if not success.
        '''
        tasks_a = [(self._in_out_service_idle, (idle_time))]
        tasks_b = [(active_file_download_test, (self.log, ad, file_name))
                   for ad in self.android_devices]
        tasks_b.extend(tasks_a)
        if not multithread_func(self.log, tasks_b):
            error_msg = " data transfer fail. "
            self.my_error_msg += error_msg
            self.log.error(error_msg)
        tasks = [(self.verify_device_status, (ad, VOICE_CALL))
                 for ad in self.android_devices]
        asserts.assert_true(multithread_func(self.log, tasks),
                            "Fail: %s." % ("verify_device_status failure"),
                            extras={"failure_cause": self.my_error_msg})
        return True

    def _in_out_service_idle(self, idle_time):
        '''
            adjust cellular signal

            Args:
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
        time.sleep(idle_time)
        self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
        return True

    @test_tracker_info(uuid="053465d8-a682-404c-a0fb-8e79f6ca581d")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_idle_msim_4g_esim_4g_dds_sim1_1min(self,
                                                       loop=50,
                                                       idle_time=60):
        '''
            1.8.17 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary idle mode - 1 min

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IDLE_CASE, loop, idle_time, 0)

    @test_tracker_info(uuid="1ba35ced-41d1-456d-84e2-a40a0d7402b2")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_idle_msim_4g_esim_4g_dds_sim2_1min(self,
                                                       loop=50,
                                                       idle_time=60):
        '''
            1.8.18 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary idle mode - 1 min

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IDLE_CASE, loop, idle_time, 1)

    @test_tracker_info(uuid="53697dd9-a2f6-4eb5-8b2c-5c9f2a5417ad")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_idle_msim_4g_esim_4g_dds_sim1_2min(self,
                                                       loop=1,
                                                       idle_time=120):
        '''
            1.8.19 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service
            Stationary idle mode - 2 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IDLE_CASE, loop, idle_time, 0)

    @test_tracker_info(uuid="f329bb22-c74f-4688-9983-eaf88131a630")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_idle_msim_4g_esim_4g_dds_sim2_2min(self,
                                                       loop=1,
                                                       idle_time=120):
        '''
            1.8.20 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary idle mode - 2 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IDLE_CASE, loop, idle_time, 1)

    @test_tracker_info(uuid="4d8cba59-921b-441c-94dc-8c43a12593ea")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_idle_msim_4g_esim_4g_dds_sim1_5min(self,
                                                       loop=1,
                                                       idle_time=300):
        '''
            1.8.21 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary idle mode - 5 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IDLE_CASE, loop, idle_time, 0)

    @test_tracker_info(uuid="dfb3646f-b21f-41f4-a70b-f7ca93ff56ec")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_idle_msim_4g_esim_4g_dds_sim2_5min(self,
                                                       loop=1,
                                                       idle_time=300):
        '''
            1.8.22 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary idle mode - 5 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IDLE_CASE, loop, idle_time, 1)

    @test_tracker_info(uuid="95e026e1-8f3e-4b9e-8d13-96a2d3be2d23")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_idle_msim_4g_esim_4g_dds_sim1_10min(
            self, loop=1, idle_time=600):
        '''
            1.8.23 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary idle mode - 10 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IDLE_CASE, loop, idle_time, 0)

    @test_tracker_info(uuid="935ed9be-94ef-4f46-b742-4bfac16b876d")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_idle_msim_4g_esim_4g_dds_sim2_10min(
            self, loop=1, idle_time=600):
        '''
            1.8.24 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary idle mode - 10 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IDLE_CASE, loop, idle_time, 1)

    @test_tracker_info(uuid="919e478e-6ea4-4bdc-b7d8-0252c7fa1510")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_4g_esim_4g_dds_sim1_1min(
            self, loop=20, idle_time=60):
        '''
            1.8.25 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary data transfer - 1 min

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(DATA_TRANSFER_CASE, loop,
                                              idle_time, 0)

    @test_tracker_info(uuid="0826b234-7619-4ad9-b1e9-81d4d7e51be4")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_4g_esim_4g_dds_sim2_1min(
            self, loop=20, idle_time=60):
        '''
            1.8.26 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary data transfer - 1 min

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(DATA_TRANSFER_CASE, loop,
                                              idle_time, 1)

    @test_tracker_info(uuid="baf5a72d-2a44-416b-b50d-80a4e6d75373")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_4g_esim_4g_dds_sim1_2min(
            self, loop=20, idle_time=120):
        '''
            1.8.27 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary data transfer - 2 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(DATA_TRANSFER_CASE, loop,
                                              idle_time, 0)

    @test_tracker_info(uuid="e74bbe30-6ced-4122-8088-3f7f7bcd35d1")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_4g_esim_4g_dds_sim2_2min(
            self, loop=20, idle_time=120):
        '''
            1.8.28 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary data transfer - 2 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(DATA_TRANSFER_CASE, loop,
                                              idle_time, 1)

    @test_tracker_info(uuid="d605bdc1-c262-424b-aa05-dd64db0f150d")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_4g_esim_4g_dds_sim1_5min(
            self, loop=20, idle_time=300):
        '''
            1.8.29 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary data transfer - 5 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(DATA_TRANSFER_CASE, loop,
                                              idle_time, 0)

    @test_tracker_info(uuid="590f6292-c19e-44f9-9050-8e4ad6ef0047")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_4g_esim_4g_dds_sim2_5min(
            self, loop=20, idle_time=300):
        '''
            1.8.30 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary data transfer - 5 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(DATA_TRANSFER_CASE, loop,
                                              idle_time, 1)

    @test_tracker_info(uuid="6d4c631d-d4b1-4974-bcf5-f63d655a43d8")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_4g_esim_4g_dds_sim1_10min(
            self, loop=20, idle_time=600):
        '''
            1.8.31 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary data transfer - 10 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(DATA_TRANSFER_CASE, loop,
                                              idle_time, 0)

    @test_tracker_info(uuid="ec4c4b08-d306-4d95-af07-485953afe741")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_4g_esim_4g_dds_sim2_10min(
            self, loop=20, idle_time=600):
        '''
            1.8.32 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary data transfer - 10 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(DATA_TRANSFER_CASE, loop,
                                              idle_time, 1)

    @test_tracker_info(uuid="9a3827bd-132b-42de-968d-802b7e2e22cc")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_off_msim_4g_esim_4g_dds_sim1_1min(
            self, loop=50, idle_time=60):
        '''
            1.8.33 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary data off - 1 min

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(DATA_OFF_CASE, loop, idle_time,
                                              0)

    @test_tracker_info(uuid="4c42e33b-188c-4c62-8def-f47c46a07555")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_off_msim_4g_esim_4g_dds_sim1_2min(
            self, loop=50, idle_time=120):
        '''
            1.8.34 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary data off - 2 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(DATA_OFF_CASE, loop, idle_time,
                                              0)

    @test_tracker_info(uuid="d40ee1cb-0e63-43f4-8b45-6a3a9bc1fcaa")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_off_msim_4g_esim_4g_dds_sim1_5min(
            self, loop=10, idle_time=300):
        '''
            1.8.35 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary data off - 5 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(DATA_OFF_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=0)

    @test_tracker_info(uuid="a0bb09bf-36c2-45cc-91d3-5441fd90a2ee")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_off_msim_4g_esim_4g_dds_sim1_10min(
            self, loop=10, idle_time=600):
        '''
            1.8.36 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary data off - 10 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(DATA_OFF_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=0)

    @test_tracker_info(uuid="d267f0bb-427a-4bed-9d78-20dbc193588f")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_in_call_msim_4g_esim_4g_dds_sim1_call_sim1_1min(
            self, loop=10, idle_time=60):
        '''
            1.8.37 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall - 1 min

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IN_CALL_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=0,
                                              voice_slot=0,
                                              sms_slot=0)

    @test_tracker_info(uuid="f0fcfc8f-4867-4b3c-94b8-4b406fa4ce8f")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_in_call_msim_4g_esim_4g_dds_sim2_call_sim2_1min(
            self, loop=10, idle_time=60):
        '''
            1.8.38 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall - 1 min

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IN_CALL_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=1,
                                              voice_slot=1,
                                              sms_slot=1)

    @test_tracker_info(uuid="5f96c891-fdb3-4367-afba-539eeb57ff0f")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_in_call_msim_4g_esim_4g_dds_sim1_call_sim1_2min(
            self, loop=10, idle_time=120):
        '''
            1.8.39 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall - 2 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IN_CALL_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=0,
                                              voice_slot=0,
                                              sms_slot=0)

    @test_tracker_info(uuid="3920a8b7-492b-4bc4-9b3d-6d7df9861934")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_in_call_msim_4g_esim_4g_dds_sim2_call_sim2_2min(
            self, loop=10, idle_time=120):
        '''
            1.8.40 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall - 2 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IN_CALL_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=1,
                                              voice_slot=1,
                                              sms_slot=1)

    @test_tracker_info(uuid="b8fac57b-fdf8-48e6-a51f-349a512d2df7")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_in_call_msim_4g_esim_4g_dds_sim1_call_sim1_5min(
            self, loop=10, idle_time=300):
        '''
            1.8.41 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall - 5 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IN_CALL_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=0,
                                              voice_slot=0,
                                              sms_slot=0)

    @test_tracker_info(uuid="0f0f7749-5cf8-4030-aae5-d28cb3a26d9b")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_in_call_msim_4g_esim_4g_dds_sim2_call_sim2_5min(
            self, loop=10, idle_time=300):
        '''
            1.8.42 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall - 5 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IN_CALL_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=1,
                                              voice_slot=1,
                                              sms_slot=1)

    @test_tracker_info(uuid="53c8bc90-b9a6-46c7-a412-fe5b9f8df3c3")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_in_call_msim_4g_esim_4g_dds_sim1_call_sim1_10min(
            self, loop=10, idle_time=600):
        '''
            1.8.43 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall - 10 mins
            (1) SIM1 (pSIM): Carrier1, VoLTE
            (2) SIM2 (eSIM): Carrier2, VoLTE
            (3) DDS (Data preferred) on SIM1
            (4) Call/SMS Preference: on SIM1
            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IN_CALL_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=0,
                                              voice_slot=0,
                                              sms_slot=0)

    @test_tracker_info(uuid="cff1893e-ea14-4e32-83ae-9116ffd96da4")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_in_call_msim_4g_esim_4g_dds_sim2_call_sim2_10min(
            self, loop=10, idle_time=600):
        '''
            1.8.44 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall - 10 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(IN_CALL_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=1,
                                              voice_slot=1,
                                              sms_slot=1)

    @test_tracker_info(uuid="a73d70d2-d5dd-4901-8cfe-6e54bdd4ddc3")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_call_data_msim_4g_esim_4g_dds_sim1_call_sim1_1min(
            self, loop=10, idle_time=60):
        '''
            1.8.45 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall + data transfer - 1 min

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(CALL_DATA_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=0,
                                              voice_slot=0,
                                              sms_slot=0)

    @test_tracker_info(uuid="6d331e0e-368d-4752-810c-ad497ccb0001")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_call_data_msim_4g_esim_4g_dds_sim2_call_sim2_1min(
            self, loop=10, idle_time=60):
        '''
            1.8.46 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall + data transfer - 1 min

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(CALL_DATA_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=1,
                                              voice_slot=1,
                                              sms_slot=1)

    @test_tracker_info(uuid="2b4c5912-f654-45ad-8195-284c602c194f")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_call_data_msim_4g_esim_4g_dds_sim1_call_sim1_2min(
            self, loop=10, idle_time=120):
        '''
            1.8.47 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall + data transfer - 2 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(CALL_DATA_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=0,
                                              voice_slot=0,
                                              sms_slot=0)

    @test_tracker_info(uuid="d0428b18-6c5b-42d9-96fd-423a0512b95e")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_call_data_msim_4g_esim_4g_dds_sim2_call_sim2_2min(
            self, loop=10, idle_time=120):
        '''
            1.8.48 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall + data transfer - 2 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(CALL_DATA_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=1,
                                              voice_slot=1,
                                              sms_slot=1)

    @test_tracker_info(uuid="66b2ec18-d2f8-46b3-8626-0688f4f7c0dc")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_call_data_msim_4g_esim_4g_dds_sim1_call_sim1_5min(
            self, loop=10, idle_time=300):
        '''
            1.8.49 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall + data transfer - 5 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(CALL_DATA_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=0,
                                              voice_slot=0,
                                              sms_slot=0)

    @test_tracker_info(uuid="4634689f-3ab5-4826-9057-668b0fe15402")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_call_data_msim_4g_esim_4g_dds_sim2_call_sim2_5min(
            self, loop=10, idle_time=300):
        '''
            1.8.50 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall + data transfer - 5 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(CALL_DATA_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=1,
                                              voice_slot=1,
                                              sms_slot=1)

    @test_tracker_info(uuid="5a097f66-dbd4-49d4-957c-d0e9584de36b")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_call_data_msim_4g_esim_4g_dds_sim1_call_sim1_10min(
            self, loop=10, idle_time=600):
        '''
            1.8.51 - [DDS:SIM1][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall + data transfer - 10 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(CALL_DATA_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=0,
                                              voice_slot=0,
                                              sms_slot=0)

    @test_tracker_info(uuid="d99c0700-27b1-4b0c-881b-ccf908a70287")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_call_data_msim_4g_esim_4g_dds_sim2_call_sim2_10min(
            self, loop=10, idle_time=600):
        '''
            1.8.52 - [DDS:SIM2][SIM1:VoLTE, SIM2:VoLTE] In/Out service -
            Stationary incall + data transfer - 10 mins

            Args:
                loop: repeat this test cases for how many times
                idle_time: idle time at no service area
            Returns:
                True if pass; False if fail
        '''
        loop = self.user_params.get("4g_dsds_io_cycle", 1)
        return self._dsds_in_out_service_test(CALL_DATA_CASE,
                                              loop,
                                              idle_time,
                                              dds_slot=1,
                                              voice_slot=1,
                                              sms_slot=1)
