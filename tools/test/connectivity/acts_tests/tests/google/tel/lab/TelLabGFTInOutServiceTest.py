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
from acts_contrib.test_utils.tel.gft_inout_defines import VOICE_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import VOLTE_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import CSFB_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import NO_SERVICE_POWER_LEVEL
from acts_contrib.test_utils.tel.gft_inout_defines import IN_SERVICE_POWER_LEVEL
from acts_contrib.test_utils.tel.gft_inout_utils import check_no_service_time
from acts_contrib.test_utils.tel.gft_inout_utils import check_back_to_service_time
from acts_contrib.test_utils.tel.gft_inout_utils import mo_voice_call
from acts_contrib.test_utils.tel.gft_inout_utils import check_ims_state
from acts_contrib.test_utils.tel.tel_defines import SERVICE_STATE_IN_SERVICE
from acts_contrib.test_utils.tel.tel_data_utils import wait_for_cell_data_connection
from acts_contrib.test_utils.tel.tel_ims_utils import toggle_volte
from acts_contrib.test_utils.tel.tel_data_utils import active_file_download_test
from acts_contrib.test_utils.tel.tel_test_utils import get_service_state_by_adb
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.tel.tel_logging_utils import log_screen_shot
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_ims_registered
from acts_contrib.test_utils.tel.gft_inout_defines import WAIT_FOR_SERVICE_TIME

IDLE_CASE = 1
DATA_TRANSFER_CASE = 2
PDP_OFF_CASE = 3
IN_CALL_CASE = 4
CALL_DATA_CASE = 5


class TelLabGFTInOutServiceTest(GFTInOutBaseTest):
    def __init__(self, controllers):
        GFTInOutBaseTest.__init__(self, controllers)
        self.my_error_msg = ""

    def setup_test(self):
        self.check_network()
        self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
        self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
        for ad in self.android_devices:
            ad.droid.wifiToggleState(False)
        GFTInOutBaseTest.setup_test(self)
        self.check_network()
        self.my_error_msg = ""

    #@test_tracker_info(uuid="c602e556-8273-4c75-b8fa-4d51ba514654")
    @test_tracker_info(uuid="aebbafc8-8388-47ac-a22a-c82c177b6eb8")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_no_service_idle_1min(self, idle_time=60):
        """ UE is in idle
            Move UE from coverage area to no service area and UE shows no service
            Wait for 1 min, then re-enter coverage area
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time)

    #@test_tracker_info(uuid="c602e556-8273-4c75-b8fa-4d51ba514654")
    @test_tracker_info(uuid="e5c4e835-0bfd-41cd-8e8d-46eafef67f90")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_no_service_idle_2min(self, idle_time=120):
        """ UE is in idle
            Move UE from coverage area to no service area and UE shows no service
            Wait for 2 min, then re-enter coverage area
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time)

    #@test_tracker_info(uuid="1d437482-caff-4695-9f3f-f3daf6793540")
    @test_tracker_info(uuid="3ffc18f7-d56b-4eca-9add-d5c6c1bbff65")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_no_service_idle_5min(self, idle_time=300):
        """ UE is in idle
            Move UE from coverage area to no service area and UE shows no service
            Wait for 5 min, then re-enter coverage area
            Args:
                loop: cycle
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time)

    #@test_tracker_info(uuid="339b4bf5-57a1-48f0-b26a-83a7db21b08b")
    @test_tracker_info(uuid="29ce2e4f-f63e-4350-bb3a-eb3a65c7e924")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_no_service_idle_10min(self, idle_time=600):
        """ UE is in idle
            Move UE from coverage area to no service area and UE shows no service
            Wait for 10 min, then re-enter coverage area
            Args:
                loop: cycle
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time)

    #@test_tracker_info(uuid="65ebac02-8d5a-48c2-bd26-6d931d6048f1")
    @test_tracker_info(uuid="e99d0b85-d8ba-47e0-9361-8bedb38b06ce")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_no_service_data_transfer_1min(self, idle_time=60):
        """ In/Out service - Stationary data transfer - 1 min
            UE is performing data transfer (E.g. Use FTP or browse tools)
            move UE from coverage area to no service area and UE shows no service
            Wait for 1 min, then re-enter coverage area
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, DATA_TRANSFER_CASE)

    #@test_tracker_info(uuid="ec3e7de4-bcf6-4a8a-ae04-868bd7925191")
    @test_tracker_info(uuid="fcdd8a4f-54df-4487-9e30-cbd589bfab5b")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_no_service_data_transfer_2min(self, idle_time=120):
        """ In/Out service - Stationary data transfer - 2 min
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, DATA_TRANSFER_CASE)
    #@test_tracker_info(uuid="8bd7017d-0a88-4423-a94b-1e37060bba1d")
    @test_tracker_info(uuid="fcdd8a4f-54df-4487-9e30-cbd589bfab5b")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_no_service_data_transfer_5min(self, idle_time=300):
        """ In/Out service - Stationary data transfer - 5 min
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, DATA_TRANSFER_CASE)
    #@test_tracker_info(uuid="c3b9c52d-41d3-449c-99ff-4bb830ca0219")
    @test_tracker_info(uuid="4684282a-e16f-42d8-bbb3-da4f6348f720")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_no_service_data_transfer_10min(self, idle_time=600):
        """ In/Out service - Stationary data transfer - 10 min
            Args:
                idle_time: idle time in service area
                file_name: download filename
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, DATA_TRANSFER_CASE)
    #@test_tracker_info(uuid="86a6b3b3-e754-4bde-b418-d4273b1ad907")
    @test_tracker_info(uuid="1667cf8f-ad33-45a1-b4b9-256dd5054635")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_service_incall_1min(self, idle_time=60):
        """ In/Out service - Stationary incall - 1 min
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, IN_CALL_CASE)
    #@test_tracker_info(uuid="0f8772cd-6f86-48eb-b583-4cbaf80a21a9")
    @test_tracker_info(uuid="b6d71f75-d0a8-4010-b8eb-46083469d089")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_service_incall_2min(self, idle_time=120):
        """ In/Out service - Stationary incall - 2 min
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, IN_CALL_CASE)
    #@test_tracker_info(uuid="11f24c0f-db33-4eb3-b847-9aed447eb820")
    @test_tracker_info(uuid="eb7a3821-dbb2-4e24-9649-fc8ca99afcc0")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_service_incall_5min(self, idle_time=300):
        """ In/Out service - Stationary incall - 5 min
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, IN_CALL_CASE)
    #@test_tracker_info(uuid="e318921b-de6b-428b-b2c4-3db7786d7558")
    @test_tracker_info(uuid="cc8ca8f3-7d93-4a61-baba-c503aba322f9")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_service_incall_10min(self, idle_time=600):
        """ In/Out service - Stationary incall - 10 min
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, IN_CALL_CASE)
    #@test_tracker_info(uuid="f6cf0019-e123-4ebd-990b-0fa5b236840c")
    @test_tracker_info(uuid="5c3ea073-58f1-4af1-a196-c3cd507cbe62")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_service_call_date_1min(self, idle_time=60):
        """ In/Out service - Stationary incall + data transfer - 1 mins
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, CALL_DATA_CASE)
    #@test_tracker_info(uuid="2f49a9de-0383-4ec6-a8ee-c62f52ea0cf2")
    @test_tracker_info(uuid="7f25090f-e340-413d-8a6a-0a50d38c8dad")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_service_call_date_2min(self, idle_time=120):
        """ In/Out service - Stationary incall + data transfer - 2 mins
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, CALL_DATA_CASE)

    #@test_tracker_info(uuid="73a6eedb-791f-4486-b815-8067a95efd5c")
    @test_tracker_info(uuid="f72f2f46-4e21-4fc2-add5-c3d4424ecd35")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_service_call_date_5min(self, idle_time=300):
        """ In/Out service - Stationary incall + data transfer - 5 mins
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, CALL_DATA_CASE)

    #@test_tracker_info(uuid="5cfbc90a-97e1-43e9-a69e-4ce2815c544d")
    @test_tracker_info(uuid="b57bd9fc-8d7f-41d8-9bb5-f7ac39860ede")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_service_call_date_10min(self, idle_time=600):
        """ In/Out service - Stationary incall + data transfer - 10 mins
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, CALL_DATA_CASE)


    #@test_tracker_info(uuid="c70180c9-5a36-4dc5-9ccc-3e6c0b5e6d37")
    @test_tracker_info(uuid="ea27ee5d-1bd7-4b24-a044-1201ac4cde2f")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_service_pdp_off_1min(self, idle_time=60):
        """ In/Out service - Stationary data off - 1 min
            Disable UE mobile data
            Move UE from coverage area to no service area and UE shows no service
            Wait for 1 min, then re-enter coverage area
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, PDP_OFF_CASE)

    #@test_tracker_info(uuid="50cc8e73-d96f-45a6-91cd-bf51de5241d2")
    @test_tracker_info(uuid="904ff9c7-5566-4f7b-9168-a73f9ee5c967")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_service_pdp_off_2min(self, idle_time=120):
        """ In/Out service - Stationary data off - 2 min
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, PDP_OFF_CASE)

    #@test_tracker_info(uuid="1f25d40c-1bfe-4d18-b57c-d7be69664f0d")
    @test_tracker_info(uuid="b4b271e3-90f7-4ffb-8938-5df5791dfc0d")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_service_pdp_off_5min(self, idle_time=300):
        """ In/Out service - Stationary data off - 5 min
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, PDP_OFF_CASE)

    #@test_tracker_info(uuid="b076b0d0-a105-4be9-aa0b-db0d782f70f2")
    @test_tracker_info(uuid="fadc53a3-429f-48fc-bd84-7f795a82797a")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_service_pdp_off_10min(self, idle_time=600):
        """ In/Out service - Stationary data off - 10 min
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        return self._test_in_out_service_idle(idle_time, PDP_OFF_CASE)
    def _test_in_out_service_idle(self, idle_time, case= IDLE_CASE, loop=1):
        """ UE is in idle
            Move UE from coverage area to no service area and UE shows no service
            Args:
                idle_time: idle time in service area
                case: include IDLE_CAS, DATA_TRANSFER_CASE, PDP_OFF_CASE,
                    IN_CALL_CASE, CALL_DATA_CASE
                loop: cycle
            Returns:
                True if pass; False if fail.
        """
        test_result = True
        if 'autoio_cycle' in self.user_params:
            loop = self.user_params.get('autoio_cycle')
        for attempt in range(1, loop + 1):
            self.log.info(f"{self.current_test_name} loop: {attempt}/{loop}")
            if case == IDLE_CASE:
                if not self._in_out_service_idle_only(idle_time):
                    test_result = False
            elif case == DATA_TRANSFER_CASE:
                if not self._data_transfer_mode(idle_time):
                    test_result = False
            elif case == PDP_OFF_CASE:
                if not self._in_out_service_pdp_off(idle_time):
                    test_result = False
            elif case == IN_CALL_CASE:
                if not self._in_call_in_out_service(idle_time):
                    test_result = False
            elif case == CALL_DATA_CASE:
                if not self._call_data_in_out_service(idle_time):
                    test_result = False
            asserts.assert_true(test_result, "Fail: %s." %(self.my_error_msg),
                extras={"failure_cause": self.my_error_msg})
            tasks = [(wait_for_ims_registered, (self.log, ad, )) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                tasks = [(check_ims_state, (ad, )) for ad in self.android_devices]
                if not multithread_func(self.log, tasks):
                    test_result = False
                    self._on_failure("ims is not register, ")
            time.sleep(WAIT_FOR_SERVICE_TIME)
            tasks = [(self.verify_device_status, (ad, VOICE_CALL))
                for ad in self.android_devices]
            test_result = multithread_func(self.log, tasks)
            asserts.assert_true(test_result, "Fail: %s." %("verify_device_status failure"),
                extras={"failure_cause": self.my_error_msg})
        return test_result

    def _in_out_service_idle_only(self, no_service_time=60, check_back_to_service=True,
        check_no_service=True):
        """ Move UE from coverage area to no service area and UE shows no service
            Wait for no_service_time sec , then re-enter coverage area
            Args:
                no_service_time: stay at no service area time in sec
                check_back_to_service: check device is back to service flag
                check_no_service: check device is no service flag
            Returns:
                True if pass; False if fail.
        """
        test_result = True
        error_msg = ""
        if 'check_no_service' in self.user_params:
            loop = self.user_params.get('check_no_service')
        if 'check_back_to_service' in self.user_params:
            loop = self.user_params.get('check_back_to_service')
        for ad in self.android_devices:
            network_type = ad.droid.telephonyGetNetworkType()
            service_state = get_service_state_by_adb(self.log,ad)
            ad.log.info("service_state=%s. network_type=%s"
                %(service_state ,network_type))
            if service_state != SERVICE_STATE_IN_SERVICE:
                error_msg = "Device is not ready for test. Service_state=%s." %(service_state)
                ad.log.info("Device is not ready for test. Service_state=%s." %(service_state))
                self.my_error_msg += error_msg
                return False
        self.log.info("Move UE from coverage area to no service area")
        self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
        if check_no_service:
            tasks = [(check_no_service_time, (ad, )) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self.check_network()
                error_msg = "Device does not become no service"
                self.my_error_msg += error_msg
                self.log.info(error_msg)
                return False
            else:
                self.log.info("wait for %s sec in no/limited service area" %(no_service_time))
                time.sleep(no_service_time)
        self.log.info("Move UE back to service area")
        self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
        if check_back_to_service:
            test_result = self._check_after_no_service()
        return test_result

    def _data_transfer_mode(self, idle_time, file_name="10MB"):
        """ Download file and in/out service
            Args:
                idle_time: stay at no service area time in sec
                file_name: file to be download
            Returns:
                True if pass; False if fail.
        """
        error_msg =""
        tasks_a = [(self._in_out_service_idle_only, (idle_time, False,))]
        tasks_b = [(active_file_download_test, (self.log, ad, file_name))
            for ad in self.android_devices]
        tasks_b.extend(tasks_a)
        if not multithread_func(self.log, tasks_b):
            error_msg = " data transfer fail. "
            self.my_error_msg +=  error_msg
            self.log.info(error_msg)
        return self._check_after_no_service()

    def _in_out_service_pdp_off(self, idle_time):
        """ UE is in idle
            Disable UE mobile data
            Move UE from coverage area to no/limited service area
            enable UE mobile data
            After UE show no service, re-enter coverage area
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        error_msg =""
        for ad in self.android_devices:
            ad.log.info("Turn off mobile data")
            ad.droid.telephonyToggleDataConnection(False)
            if not wait_for_cell_data_connection(self.log, ad, False):
                self.my_error_msg += "fail to turn off mobile data"
                return False
        if not self._in_out_service_idle_only(idle_time, False):
            return False
        for ad in self.android_devices:
            ad.log.info("Turn on mobile data")
            ad.droid.telephonyToggleDataConnection(True)
            #If True, it will wait for status to be DATA_STATE_CONNECTED
            if not wait_for_cell_data_connection(self.log, ad, True):
                self.my_error_msg += "fail to turn on mobile data"
                return False
        return self._check_after_no_service()

    def _in_call_in_out_service(self, idle_time):
        """ UE is in call
            Move UE from coverage area to no/limited service area
            After UE show no service, re-enter coverage area
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        error_msg = ""
        tasks = [(mo_voice_call, (self.log, ad, VOICE_CALL, False)) for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            error_msg = "MO voice call fail"
            self.my_error_msg += error_msg
            self.log.info(error_msg)
            return False
        if not self._in_out_service_idle_only(idle_time, False):
            return False
        return self._check_after_no_service()

    def _call_data_in_out_service(self, idle_time):
        """ UE is performing data transfer (E.g. Use FTP or browse tools)
            UE makes a MO call
            Move UE from coverage area to no/limited service area
            After UE show no service, re-enter coverage area
            Args:
                idle_time: idle time in service area
            Returns:
                True if pass; False if fail.
        """
        tasks_a = [(active_file_download_test, (self.log, ad, )) for ad in self.android_devices]
        tasks_b= [(mo_voice_call, (self.log, ad, VOICE_CALL, False)) for ad in self.android_devices]
        tasks_b.extend(tasks_a)
        if not multithread_func(self.log, tasks_b):
            error_msg = "fail to perfrom data transfer/voice call"
            self.my_error_msg += error_msg
            return False
        if not self._in_out_service_idle_only(idle_time, False):
            return False
        return self._check_after_no_service()

    def _check_after_no_service(self):
        """ check device is back to service or not
            Returns:
                True if pass; False if fail.
        """
        tasks = [(check_back_to_service_time, (ad,)) for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            error_msg = "Device is not back to the service"
            self.my_error_msg += error_msg
            self.log.info(error_msg)
            return False
        return True


    @test_tracker_info(uuid="4b8fee71-0d9b-4355-b175-84ea3c2a222a")
    @TelephonyBaseTest.tel_test_wrap
    def test_ID_1_1_5_ims_on_off(self, loop=1):
        '''
            1.1.5 - In/Out service - IMS on -> no service
            -> service area -> IMS off

            Args:
                loop: repeat this test cases for how many times

            Returns:
                True if pass; False if fail
            Raises:
                TestFailure if not success.
        '''
        error_msg = ""
        test_result = True
        if 'ims_cycle' in self.user_params:
            loop = self.user_params.get('ims_cycle')

        for x in range (loop):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.my_error_msg += "cycle%s: " %(x+1)
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)

            self.log.info("Turn on IMS")
            tasks = [(toggle_volte, (self.log, ad, True)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("fail to toggle volte, ")
                return False

            tasks = [(wait_for_ims_registered, (self.log, ad, )) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("ims is not register, ")
                return False
            self.log.info("Move to no service area")
            self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(60)

            self.log.info("Turn off IMS")
            tasks = [(toggle_volte, (self.log, ad, False)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("fail to toggle volte, ")
                return False
            self.log.info("Move back to service area and verify device status")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            tasks = [(self.verify_device_status, (ad, VOICE_CALL))
                for ad in self.android_devices]
            test_result = multithread_func(self.log, tasks)
            if not test_result:
                self._on_failure("verify_device_status fail, ")
                return False
        if not test_result:
            asserts.assert_true(test_result, "[Fail]%s" %(error_msg),
                extras={"failure_cause": error_msg})
        return test_result


    @test_tracker_info(uuid="6b963676-fd28-4626-ad54-e1aa04274a37")
    @TelephonyBaseTest.tel_test_wrap
    def test_ID_1_1_6_ims_on_off(self, loop=1):
        '''
            1.1.6 - In/Out service - IMS on -> Enter no service area
            -> service area -> IMS off

            Args:
                loop: repeat this test cases for how many times

            Returns:
                True if pass; False if fail
        '''
        error_msg = ""
        test_result = True
        if 'ims_cycle' in self.user_params:
            loop = self.user_params.get('ims_cycle')

        for x in range (loop):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.my_error_msg += "cycle%s: " %(x+1)
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)

            tasks = [(check_ims_state, (ad, )) for ad in self.android_devices]
            multithread_func(self.log, tasks)

            self.log.info("Turn on IMS")
            tasks = [(toggle_volte, (self.log, ad, True)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("fail to toggle volte, ")
                return False

            tasks = [(check_ims_state, (ad, )) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("ims is not register, ")
                return False

            self.log.info("Move to no service area")
            self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(60)

            self.log.info("Move back to service area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.log.info("Turn off IMS")
            tasks = [(toggle_volte, (self.log, ad, False)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("fail to toggle volte, ")
                return False
            tasks = [(self.verify_device_status, (ad, VOICE_CALL))
                for ad in self.android_devices]
            test_result = multithread_func(self.log, tasks)
            if not test_result:
                self._on_failure( "verify_device_status fail, ")
                return False
        return test_result

    @test_tracker_info(uuid="640db83f-6ba8-4df5-9c8c-dcf52a1904a1")
    @TelephonyBaseTest.tel_test_wrap
    def test_ID_1_1_7_ims_on_off(self, loop=1):
        '''
            1.1.7 - In/Out service - IMS off
            -> IMS on under no service area -> Back service area

            Args:
                loop: repeat this test cases for how many times

            Returns:
                True if pass; False if fail
        '''
        error_msg = ""
        test_result = True
        if 'ims_cycle' in self.user_params:
            loop = self.user_params.get('ims_cycle')

        for x in range (loop):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.my_error_msg += "cycle%s: " %(x+1)
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)

            tasks = [(check_ims_state, (ad, )) for ad in self.android_devices]
            multithread_func(self.log, tasks)

            self.log.info("Turn off IMS")
            tasks = [(toggle_volte, (self.log, ad, False)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("fail to toggle volte, ")
                return False

            tasks = [(check_ims_state, (ad, )) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("ims is not register, ")
                return False

            self.log.info("CSFB call in service area")
            tasks = [(mo_voice_call, (self.log, ad, CSFB_CALL, True, 30))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("csfb_call_fail, ")
                return False

            self.log.info("Move to no service area then turn on IMS")
            self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(60)
            tasks = [(toggle_volte, (self.log, ad, True)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("fail to toggle volte, ")
                return False

            self.log.info("Move back to service area and verify device status, VOLTE call")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            tasks = [(self.verify_device_status, (ad, VOLTE_CALL))
                for ad in self.android_devices]
            test_result = multithread_func(self.log, tasks)
            if not test_result:
                self._on_failure( "verify_device_status fail, ")
                return False
        return test_result


    @test_tracker_info(uuid="fcb72af6-b9d0-4911-9819-79abc58d5213")
    @TelephonyBaseTest.tel_test_wrap
    def test_ID_1_1_8_ims_on_off(self, loop=1, sleepTimer=15):
        '''
            1.1.8 - In/Out service - IMS off -> Enter no service area
            -> service area -> IMS on

            Args:
                loop: repeat this test cases for how many times

            Returns:
                True if pass; False if fail
            Raises:
                TestFailure if not success.
        '''
        error_msg = ""
        test_result = True
        if 'ims_cycle' in self.user_params:
            loop = self.user_params.get('ims_cycle')

        for x in range (loop):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.my_error_msg += "cycle%s: " %(x+1)
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)

            tasks = [(check_ims_state, (ad, )) for ad in self.android_devices]
            multithread_func(self.log, tasks)

            self.log.info("Turn off IMS")
            tasks = [(toggle_volte, (self.log, ad, False)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("fail to toggle volte ")
                return False

            tasks = [(check_ims_state, (ad, )) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("ims is not register, ")
                return False

            self.log.info("CSFB call in service area")
            tasks = [(mo_voice_call, (self.log, ad, CSFB_CALL, True, 30))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("csfb_call_fail, ")
                return False

            self.log.info("Move to no service area")
            self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(60)
            self.log.info("Move back to service area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.log.info("Turn on ims")
            tasks = [(toggle_volte, (self.log, ad, True)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self._on_failure("fail to toggle volte ")
                return False
            self.log.info("Verify device status, VOLTE call")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            tasks = [(self.verify_device_status, (ad, VOLTE_CALL))
                for ad in self.android_devices]
            test_result = multithread_func(self.log, tasks)
            if not test_result:
                self._on_failure("verify_device_status fail, ")
                return False
        return test_result


    @test_tracker_info(uuid="36250121-fe44-4953-ba9f-b806d7bb0e28")
    @TelephonyBaseTest.tel_test_wrap
    def test_ID_1_1_49_in_out_service_dialing(self, loop=1):
        '''
            1.1.49 - In/Out service - Stationary dialing stage

            Args:
                loop: repeat this test cases for how many times

            Returns:
                True if pass; False if fail
        '''
        error_msg = ""
        test_result = True
        if 'autoio_cycle' in self.user_params:
            loop = self.user_params.get('autoio_cycle')

        for x in range (loop):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.my_error_msg += "cycle%s: " %(x+1)
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            for ad in self.android_devices:
                ad.log.info("initiate voice call to %s " %(ad.mt_phone_number))
                ad.droid.telecomCallNumber(ad.mt_phone_number)
            self.log.info("Move to no service area")
            self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(30)
            tasks = [(hangup_call, (self.log, ad)) for ad in self.android_devices]
            multithread_func(self.log, tasks)
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            tasks = [(wait_for_ims_registered, (self.log, ad, )) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                tasks = [(check_ims_state, (ad, )) for ad in self.android_devices]
                if not multithread_func(self.log, tasks):
                    test_result = False
                    self._on_failure("ims is not register, ")
            time.sleep(WAIT_FOR_SERVICE_TIME)
            tasks = [(self.verify_device_status, (ad, VOICE_CALL))
                for ad in self.android_devices]
            test_result = multithread_func(self.log, tasks)
            asserts.assert_true(test_result, "Fail: %s." %("verify_device_status failure"),
                extras={"failure_cause": self.my_error_msg})
        return test_result
