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

from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.GFTInOutBaseTest import GFTInOutBaseTest
from acts_contrib.test_utils.tel.gft_inout_defines import NO_SERVICE_POWER_LEVEL
from acts_contrib.test_utils.tel.gft_inout_defines import IN_SERVICE_POWER_LEVEL
from acts_contrib.test_utils.tel.tel_defines import INVALID_SUB_ID
from acts_contrib.test_utils.tel.tel_data_utils import start_youtube_video
from acts_contrib.test_utils.tel.tel_subscription_utils import set_dds_on_slot
from acts_contrib.test_utils.tel.tel_subscription_utils import set_dds_on_slot_0
from acts_contrib.test_utils.tel.tel_subscription_utils import set_dds_on_slot_1
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_dsds_utils import dsds_voice_call_test
from acts_contrib.test_utils.tel.tel_subscription_utils import set_dds_on_slot_0
from acts_contrib.test_utils.tel.tel_subscription_utils import set_dds_on_slot_1

_5G_VOLTE = "5g_volte"
_VOLTE = "volte"
_NO_SERVICE_TIME = 30
_ERROR_MSG_DATA_TRANSFER_FAILURE = "_test_in_out_service_data_transfer failure"
_ERROR_MSG_IDLE_FAILURE = "_test_in_out_service_idle failure"


class TelLabGFTDSDSTest(GFTInOutBaseTest):
    def __init__(self, controllers):
        # requirs 2 android devices to run DSDS test
        GFTInOutBaseTest.__init__(self, controllers)
        self.tel_logger = TelephonyMetricLogger.for_test_case()
        self.my_error_msg = ""

    def teardown_test(self):
        GFTInOutBaseTest.teardown_class(self)
        ensure_phones_idle(self.log, self.android_devices)

    @test_tracker_info(uuid="90ef8e20-64bb-4bf8-81b6-431de524f2af")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_msim_5g_esim_5g_dds_sim1(self, loop=1):
        '''
            1.7.19 - [SA/NSA][DDS:SIM1][SIM1:5G, SIM2:5G]
            Attach to 5G after in/out service during idle
            SIM1 (pSIM) : Carrier 1 with 5G SIM.
            SIM2 (eSIM) : Carrier 2 with 5G SIM.
            DDS (Data preferred) on SIM1 and this slot has the 5G capability.

            (1) Moves to no service area during data idle.
            (2) Moves to service area.
            (3) Makes a MOMT voice/VT call on SIM1.
            (4) Makes a MOMT voice/VT call on SIM2.
            (5) Starts streaming.
            Args:
                loop: repeat this test cases for how many times
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("dsds_io_cycle", 1)):
            self.log.info("%s loop: %s/%s" %
                          (self.current_test_name, x + 1, loop))
            asserts.assert_true(self._test_in_out_service_idle(
                _5G_VOLTE, _5G_VOLTE, 0),
                                "[Fail]%s" % (_ERROR_MSG_IDLE_FAILURE),
                                extras={"failure_cause": self.my_error_msg})
        return True

    @test_tracker_info(uuid="21b3ff34-e42a-4d42-ba98-87c510e83967")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_msim_5g_esim_5g_dds_sim2(self, loop=1):
        '''
            1.7.20 [SA/NSA][DDS:SIM2][SIM1:5G, SIM2:5G]
            Attach to 5G after in/out service during idle.
            SIM1 (pSIM) : Carrier 1 with 5G SIM.
            SIM2 (eSIM) : Carrier 2 with 5G SIM.
            DDS (Data preferred) on SIM2

            (1) Moves to no service area during data idle.
            (2) Moves to service area.
            (3) Makes a MOMT voice/VT call on SIM2.
            (4) Makes a MOMT voice/VT call on SIM1.
            (5) Starts streaming.

            Args:
                loop: repeat this test cases for how many times
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("dsds_io_cycle", 1)):
            self.log.info("%s loop: %s/%s" %
                          (self.current_test_name, x + 1, loop))
            asserts.assert_true(self._test_in_out_service_idle(
                _5G_VOLTE, _5G_VOLTE, 1),
                                "[Fail]%s" % (_ERROR_MSG_IDLE_FAILURE),
                                extras={"failure_cause": self.my_error_msg})
        return True

    @test_tracker_info(uuid="f1311823-e6e4-478e-a38d-2344389698b7")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_msim_4g_esim_5g_dds_sim1(self, loop=1):
        '''
            1.7.21 - [SA/NSA][DDS:SIM1][SIM1:VoLTE, SIM2:5G]
            Attach to 5G after in/out service during idle
            SIM1 (pSIM) : Carrier 1 with 4G SIM or 5G SIM locks in 4G.
            SIM2 (eSIM) : Carrier 2 with 5G SIM.
            DDS (Data preferred) on SIM1.

            (1) Move to no service area during data idle.
            (2) Moves to service area.
            (3) Makes a MOMT voice/VT call on SIM1.
            (4) Makes a MOMT voice/VT call on SIM2.
            (5) Starts streaming.

            Args:
                loop: repeat this test cases for how many times
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("dsds_io_cycle", 1)):
            self.log.info("%s loop: %s/%s" %
                          (self.current_test_name, x + 1, loop))
            asserts.assert_true(self._test_in_out_service_idle(
                _VOLTE, _5G_VOLTE, 0),
                                "[Fail]%s" % (_ERROR_MSG_IDLE_FAILURE),
                                extras={"failure_cause": self.my_error_msg})
        return True

    @test_tracker_info(uuid="7dc38fd5-741f-42b0-a476-3aa51610d184")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_msim_4g_esim_5g_dds_sim2(self, loop=1):
        '''
            1.7.22 - [SA/NSA][DDS:SIM2][SIM1:VoLTE, SIM2:5G]
            Attach to 5G after in/out service during idle
            SIM1 (pSIM) : Carrier 1 with 4G SIM or 5G SIM locks in 4G.
            SIM2 (eSIM) : Carrier 2 with 5G SIM.
            DDS (Data preferred) on SIM2.

            (1) Moves to no service area during data idle.
            (2) Moves to service area.
            (3) Makes a MOMT voice/VT call on SIM2.
            (4) Makes a MOMT voice/VT call on SIM1.
            (5) Starts streaming.

            Args:
                loop: repeat this test cases for how many times
            Returns:
                True if pass; False if fail
            Raises:
                TestFailure if not success.
        '''
        for x in range(self.user_params.get("dsds_io_cycle", 1)):
            self.log.info("%s loop: %s/%s" %
                          (self.current_test_name, x + 1, loop))
            asserts.assert_true(self._test_in_out_service_idle(
                _VOLTE, _5G_VOLTE, 1),
                                "[Fail]%s" % (_ERROR_MSG_IDLE_FAILURE),
                                extras={"failure_cause": self.my_error_msg})
        return True

    @test_tracker_info(uuid="a47cdaf6-87b6-416e-a0e4-ebdd2ec5f3f1")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_msim_5g_esim_4g_dds_sim1(self, loop=1):
        '''
            1.7.23 - [SA/NSA][DDS:SIM1][SIM1:5G, SIM2:VoLTE]
            Attach to 5G after in/out service during idle
            SIM1 (pSIM) : Carrier 1 with 5G SIM.
            SIM2 (eSIM) : Carrier 2 with 4G SIM
            DDS (Data preferred) on SIM1.

            (1) Moves to no service area during data idle.
            (2) Moves to service area.
            (3) Makes a MOMT voice/VT call on SIM1.
            (4) Makes a MOMT voice/VT call on SIM2.
            (5) Starts streaming.

            Args:
                loop: repeat this test cases for how many times
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("dsds_io_cycle", 1)):
            self.log.info("%s loop: %s/%s" %
                          (self.current_test_name, x + 1, loop))
            asserts.assert_true(self._test_in_out_service_idle(
                _5G_VOLTE, _VOLTE, 0),
                                "[Fail]%s" % (_ERROR_MSG_IDLE_FAILURE),
                                extras={"failure_cause": self.my_error_msg})
        return True

    @test_tracker_info(uuid="5e2e3ce2-6d37-48dd-9007-6aa3f593150b")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_msim_5g_esim_4g_dds_sim2(self, loop=1):
        '''
            1.7.24 - [SA/NSA][DDS:SIM2][SIM1:5G, SIM2:VoLTE]
            Attach to 5G after in/out service during idle
            SIM1 (pSIM) : Carrier 1 with 5G SIM.
            SIM2 (eSIM) : Carrier 2 with 4G SIM
            DDS (Data preferred) on SIM1.

            (1) Moves to no service area during data idle.
            (2) Moves to service area.
            (3) Makes a MOMT voice/VT call on SIM1.
            (4) Makes a MOMT voice/VT call on SIM2.
            (5) Starts streaming.

            Args:
                loop: repeat this test cases for how many times
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("dsds_io_cycle", 1)):
            self.log.info("%s loop: %s/%s" %
                          (self.current_test_name, x + 1, loop))
            asserts.assert_true(self._test_in_out_service_idle(
                _5G_VOLTE, _VOLTE, 1),
                                "[Fail]%s" % (_ERROR_MSG_IDLE_FAILURE),
                                extras={"failure_cause": self.my_error_msg})
        return True

    @test_tracker_info(uuid="51f291f0-af5f-400c-9678-4f129695bb68")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_5g_esim_5g_dds_sim1(self, loop=1):
        '''
            1.7.25 - [SA/NSA][DDS:SIM1][SIM1:5G, SIM2:5G]
            Attach to 5G after in/out service during data transferring
            SIM1 (pSIM) : Carrier 1 with 5G SIM.
            SIM2 (eSIM) : Carrier 2 with 5G SIM
            DDS (Data preferred) on SIM1.

            (1) Moves to no service area during data transferring..
            (2) Moves to service area.
            (3) Makes a MOMT voice/VT call on SIM1.
            (4) Makes a MOMT voice/VT call on SIM2.
            (5) Starts streaming.

            Args:
                loop: repeat this test cases for how many times
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("dsds_io_cycle", 1)):
            self.log.info("%s loop: %s/%s" %
                          (self.current_test_name, x + 1, loop))
            asserts.assert_true(self._test_in_out_service_data_transfer(
                _5G_VOLTE, _5G_VOLTE, 0),
                                "[Fail]%s" %
                                (_ERROR_MSG_DATA_TRANSFER_FAILURE),
                                extras={"failure_cause": self.my_error_msg})
        return True

    @test_tracker_info(uuid="d0b134c5-380f-4c74-8ab9-8322de1c59e9")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_5g_esim_5g_dds_sim2(self, loop=1):
        '''
            1.7.26 - [SA/NSA][DDS:SIM2][SIM1:5G, SIM2:5G]
            Attach to 5G after in/out service during data transferring
            SIM1 (pSIM) : Carrier 1 with 5G SIM.
            SIM2 (eSIM) : Carrier 2 with 5G SIM
            DDS (Data preferred) on SIM2.

            (1) Moves to no service area during data transferring..
            (2) Moves to service area.
            (3) Makes a MOMT voice/VT call on SIM1.
            (4) Makes a MOMT voice/VT call on SIM2.
            (5) Starts streaming.

            Args:
                loop: repeat this test cases for how many times
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("dsds_io_cycle", 1)):
            self.log.info("%s loop: %s/%s" %
                          (self.current_test_name, x + 1, loop))
            asserts.assert_true(
                self._test_in_out_service_data_transfer(_VOLTE, _5G_VOLTE, 1),
                "[Fail]%s" % (_ERROR_MSG_DATA_TRANSFER_FAILURE),
                extras={"failure_cause": self.my_error_msg})
        return True

    @test_tracker_info(uuid="c28a9ea5-28a8-4d21-ba25-cb38aca30170")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_4g_esim_5g_dds_sim1(self, loop=1):
        '''
            1.7.27 - [SA/NSA][DDS:SIM1][SIM1:VoLTE, SIM2:5G]
            Attach to 5G after in/out service during data transferring
            SIM1 (pSIM) : Carrier 1 with 4G SIM.
            SIM2 (eSIM) : Carrier 2 with 5G SIM
            DDS (Data preferred) on SIM1.

            (1) Moves to no service area during data transferring..
            (2) Moves to service area.
            (3) Makes a MOMT voice/VT call on SIM1.
            (4) Makes a MOMT voice/VT call on SIM2.
            (5) Starts streaming.

            Args:
                loop: repeat this test cases for how many times
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("dsds_io_cycle", 1)):
            self.log.info("%s loop: %s/%s" %
                          (self.current_test_name, x + 1, loop))
            asserts.assert_true(
                self._test_in_out_service_data_transfer(_VOLTE, _5G_VOLTE, 0),
                "[Fail]%s" % (_ERROR_MSG_DATA_TRANSFER_FAILURE),
                extras={"failure_cause": self.my_error_msg})
        return True

    @test_tracker_info(uuid="c28a9ea5-28a8-4d21-ba25-cb38aca30170")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_4g_esim_5g_dds_sim2(self, loop=1):
        '''
            1.7.28 - [SA/NSA][DDS:SIM2][SIM1:VoLTE, SIM2:5G]
            Attach to 5G after in/out service during data transferring
            SIM1 (pSIM) : Carrier 1 with 4G SIM.
            SIM2 (eSIM) : Carrier 2 with 5G SIM
            DDS (Data preferred) on SIM2.

            (1) Moves to no service area during data transferring..
            (2) Moves to service area.
            (3) Makes a MOMT voice/VT call on SIM1.
            (4) Makes a MOMT voice/VT call on SIM2.
            (5) Start a download via speedtest lab mode.

            Args:
                loop: repeat this test cases for how many times
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("dsds_io_cycle", 1)):
            self.log.info("%s loop: %s/%s" %
                          (self.current_test_name, x + 1, loop))
            asserts.assert_true(
                self._test_in_out_service_data_transfer(_VOLTE, _5G_VOLTE, 1),
                "[Fail]%s" % (_ERROR_MSG_DATA_TRANSFER_FAILURE),
                extras={"failure_cause": self.my_error_msg})
        return True

    @test_tracker_info(uuid="7d6a85c0-0194-4705-8a80-49f21cebc4ed")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_5g_esim_4g_dds_sim1(self, loop=1):
        '''
            1.7.29 - [SA/NSA][DDS:SIM1][SIM1:5G, SIM2:VoLTE]
            Attach to 5G after in/out service during data transferring
            SIM1 (pSIM) : Carrier 1 with 5G SIM.
            SIM2 (eSIM) : Carrier 2 with 4G SIM
            DDS (Data preferred) on SIM1.

            (1) Move to no service area during data transferring..
            (2) Move to service area.
            (3) Make a MOMT voice/VT call on SIM1.
            (4) Makes a MOMT voice/VT call on SIM2.
            (5) Starts streaming.

            Args:
                loop: repeat this test cases for how many times
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("dsds_io_cycle", 1)):
            self.log.info("%s loop: %s/%s" %
                          (self.current_test_name, x + 1, loop))
            asserts.assert_true(
                self._test_in_out_service_data_transfer(_5G_VOLTE, _VOLTE, 0),
                "[Fail]%s" % (_ERROR_MSG_DATA_TRANSFER_FAILURE),
                extras={"failure_cause": self.my_error_msg})
        return True

    @test_tracker_info(uuid="43cd405f-d510-4193-9bff-795db12dbb30")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_data_transfer_msim_5g_esim_4g_dds_sim2(self, loop=1):
        '''
            1.7.30 - [SA/NSA][DDS:SIM2][SIM1:5G, SIM2:VoLTE]
            Attach to 5G after in/out service during data transferring
            SIM1 (pSIM) : Carrier 1 with 5G SIM.
            SIM2 (eSIM) : Carrier 2 with 4G SIM
            DDS (Data preferred) on SIM2.

            (1) Move to no service area during data transferring..
            (2) Move to service area.
            (3) Make a MOMT voice/VT call on SIM1.
            (4) Make a MOMT voice/VT call on SIM2.
            (5) start streaming.

            Args:
                loop: repeat this test cases for how many times
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("dsds_io_cycle", 1)):
            self.log.info("%s loop: %s/%s" %
                          (self.current_test_name, x + 1, loop))
            asserts.assert_true(
                self._test_in_out_service_data_transfer(_5G_VOLTE, _VOLTE, 1),
                "[Fail]%s" % (_ERROR_MSG_DATA_TRANSFER_FAILURE),
                extras={"failure_cause": self.my_error_msg})
        return True

    def _test_in_out_service_idle(self,
                                  psim_rat=_5G_VOLTE,
                                  esim_rat=_5G_VOLTE,
                                  dds_slot=0,
                                  momt_direction="mo"):
        ad = self.android_devices[0]
        set_dds_on_slot(ad, dds_slot)
        self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
        time.sleep(_NO_SERVICE_TIME)
        self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
        return self._test_mo_voice_call(psim_rat, esim_rat, dds_slot,
                                        momt_direction)

    def _test_in_out_service_data_transfer(self,
                                           psim_rat=_5G_VOLTE,
                                           esim_rat=_5G_VOLTE,
                                           dds_slot=0,
                                           momt_direction="mo"):
        ad = self.android_devices[0]
        set_dds_on_slot(ad, dds_slot)
        # start streaming
        if not start_youtube_video(ad):
            ad.log.warning("Fail to bring up youtube video")
            time.sleep(10)
        self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
        time.sleep(_NO_SERVICE_TIME)
        self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
        return self._test_mo_voice_call(psim_rat, esim_rat, dds_slot,
                                        momt_direction)

    def _test_mo_voice_call(self,
                            psim_rat=_5G_VOLTE,
                            esim_rat=_5G_VOLTE,
                            dds_slot=0,
                            momt_direction="mo"):
        ad = self.android_devices[0]
        # Make a MOMT voice on SIM1
        test_result = dsds_voice_call_test(self.log,
                                           self.tel_logger,
                                           self.android_devices,
                                           0,
                                           None,
                                           dds_slot,
                                           mo_rat=[psim_rat, esim_rat],
                                           call_direction=momt_direction)
        ensure_phones_idle(self.log, self.android_devices)
        # Make a MOMT voice on SIM2
        test_result = dsds_voice_call_test(self.log,
                                           self.tel_logger,
                                           self.android_devices,
                                           1,
                                           None,
                                           dds_slot,
                                           mo_rat=[psim_rat, esim_rat],
                                           call_direction=momt_direction)
        # start streaming
        if not start_youtube_video(ad):
            ad.log.warning("Fail to bring up youtube video")
            time.sleep(10)
        return test_result
