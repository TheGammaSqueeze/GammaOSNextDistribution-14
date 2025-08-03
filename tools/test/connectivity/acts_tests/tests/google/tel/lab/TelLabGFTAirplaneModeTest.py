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
from acts import signals
from acts.test_decorators import test_tracker_info
from acts.libs.utils.multithread import multithread_func

from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.GFTInOutBaseTest import GFTInOutBaseTest
from acts_contrib.test_utils.tel.gft_inout_defines import VOICE_CALL
from acts_contrib.test_utils.tel.gft_inout_utils import check_no_service_time
from acts_contrib.test_utils.tel.gft_inout_utils import check_back_to_service_time
from acts_contrib.test_utils.tel.gft_inout_utils import mo_voice_call
from acts_contrib.test_utils.tel.tel_defines import RAT_3G
from acts_contrib.test_utils.tel.tel_defines import RAT_4G
from acts_contrib.test_utils.tel.tel_defines import RAT_5G
from acts_contrib.test_utils.tel.tel_defines import GEN_3G
from acts_contrib.test_utils.tel.tel_defines import GEN_4G
from acts_contrib.test_utils.tel.tel_defines import GEN_5G
from acts_contrib.test_utils.tel.tel_defines import YOUTUBE_PACKAGE_NAME
from acts_contrib.test_utils.tel.tel_data_utils import start_youtube_video
from acts_contrib.test_utils.tel.tel_data_utils import wait_for_cell_data_connection
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_network_generation
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phone_default_state
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_test_utils import set_preferred_network_mode_pref

AIRPLANE_MODE_ON_TIME = 60
AIRPLANE_MODE_OFF_TIME = 60
MOBILE_DATA_ON_OFF_CASE = 1
DATA_TRANSFER_CASE = 2
WIFI_HOTSPOT_CASE = 3
IN_CALL_CASE = 4

class TelLabGFTAirplaneModeTest(GFTInOutBaseTest):
    def __init__(self, controllers):
        GFTInOutBaseTest.__init__(self, controllers)


    def setup_test(self):
        for ad in self.android_devices:
            ensure_phone_default_state(self.log, ad)
        GFTInOutBaseTest.setup_test(self)
        self.my_error_msg = ""

    def teardown_test(self):
        for ad in self.android_devices:
            ad.force_stop_apk(YOUTUBE_PACKAGE_NAME)
        ensure_phones_idle(self.log, self.android_devices)

    @test_tracker_info(uuid="c5d2e9b3-478c-4f86-86e5-c8341944d222")
    @TelephonyBaseTest.tel_test_wrap
    def test_airplane_mode_mobile_data_off_3g(self):
        '''
            1.9.5 - 3G Airplane mode on/off - Mobile data off

            Returns:
                True if pass; False if fail
        '''
        tasks = [(self._airplane_mode_helper, (ad, MOBILE_DATA_ON_OFF_CASE, RAT_3G))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            raise signals.TestFailure("_airplane_mode_data_on_off failure: %s"
                %(self.my_error_msg))
        return True

    @test_tracker_info(uuid="22956c54-ab2a-4031-8dfc-95fdb69fb3a6")
    @TelephonyBaseTest.tel_test_wrap
    def test_airplane_mode_mobile_data_off_4g(self):
        '''
            1.13.5 - 4G Airplane mode on/off - Mobile data off

            Returns:
                True if pass; False if fail
        '''
        tasks = [(self._airplane_mode_helper, (ad, MOBILE_DATA_ON_OFF_CASE, RAT_4G))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            raise signals.TestFailure("_airplane_mode_data_on_off failure: %s"
                %(self.my_error_msg))
        return True


    @test_tracker_info(uuid="9ab8e183-6864-4543-855e-4d9a6cb74e42")
    @TelephonyBaseTest.tel_test_wrap
    def test_airplane_mode_mobile_data_off_5g(self):
        '''
            1.14.5 - 5G [NSA/SA] Airplane mode on/off - Mobile data off

            Returns:
                True if pass; False if fail
        '''
        tasks = [(self._airplane_mode_helper, (ad, MOBILE_DATA_ON_OFF_CASE, RAT_5G))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            raise signals.TestFailure("_airplane_mode_data_on_off failure: %s"
                %(self.my_error_msg))
        return True


    @test_tracker_info(uuid="114afeb6-4c60-4da3-957f-b4b0005223be")
    @TelephonyBaseTest.tel_test_wrap
    def test_airplane_mode_voice_call_3g(self):
        '''
            3G 1.9.2 - Airplane mode on/off - Active call

            Returns:
                True if pass; False if fail
        '''
        tasks = [(self._airplane_mode_helper, (ad, IN_CALL_CASE, GEN_3G))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            raise signals.TestFailure("_airplane_mode_voice_call failure: %s"
                %(self.my_error_msg))
        return True


    @test_tracker_info(uuid="0e00ca1a-f896-4a18-a3c8-05514975ecd6")
    @TelephonyBaseTest.tel_test_wrap
    def test_airplane_mode_voice_call_4g(self):
        '''
            4G 1.13.2 - Airplane mode on/off - Active call

            Returns:
                True if pass; False if fail
        '''
        tasks = [(self._airplane_mode_helper, (ad, IN_CALL_CASE, GEN_4G))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            raise signals.TestFailure("_airplane_mode_voice_call failure: %s"
                %(self.my_error_msg))
        return True

    @test_tracker_info(uuid="cb228d48-78b4-48b4-9996-26ac252a9486")
    @TelephonyBaseTest.tel_test_wrap
    def test_airplane_mode_voice_call_5g(self):
        '''
            5G 1.14.2 - [NSA/SA] Airplane mode on/off - Active call
            For NSA, call goes through IMS over LTE (VoLTE).
            For SA, call goes through IMS over LTE/NR (EPSFB or VoNR)
            depends on carrier's implementation.

            Returns:
                True if pass; False if fail
        '''
        tasks = [(self._airplane_mode_helper, (ad, IN_CALL_CASE, GEN_5G))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            raise signals.TestFailure("_airplane_mode_voice_call failure: %s"
                %(self.my_error_msg))
        return True

    def _airplane_mode_mobile_data_off(self, ad):
        """ Mobile data on/off and airplane mode on/off.

        Args:
            ad: android_device object.

        Returns:
            result: True if operation succeed. False if error happens.
        """
        # Repeat for 3 cycles.
        for x in range (3):
            ad.log.info("Turn off mobile data")
            ad.droid.telephonyToggleDataConnection(False)
            if not wait_for_cell_data_connection(self.log, ad, False):
                self.my_error_msg += "fail to turn off mobile data"
                return False
            if not self._airplane_mode_on_off(ad):
                return False
            ad.log.info("Turn on mobile data")
            ad.droid.telephonyToggleDataConnection(True)
            #If True, it will wait for status to be DATA_STATE_CONNECTED
            if not wait_for_cell_data_connection(self.log, ad, True):
                self.my_error_msg += "fail to turn on mobile data"
                return False
            # UE turn airplane mode on then off.
            if not self._airplane_mode_on_off(ad):
                return False
        return True


    def _airplane_mode_on_off(self, ad):
        """ Toggle airplane mode on/off.

        Args:
            ad: android_device object.

        Returns:
            result: True if operation succeed. False if error happens.
        """
        ad.log.info("Turn on airplane mode")
        if not toggle_airplane_mode(self.log, ad, True):
            self.my_error_msg += "Fail to enable airplane mode on. "
            return False
        time.sleep(AIRPLANE_MODE_ON_TIME)
        ad.log.info("Turn off airplane mode")
        if not toggle_airplane_mode(self.log, ad, False):
            self.my_error_msg += "Fail to enable airplane mode off. "
            return False
        time.sleep(AIRPLANE_MODE_OFF_TIME)
        return True


    def _airplane_mode_voice_call(self, ad):
        """ Airplane mode on/off while in-call.

        Args:
            ad: android_device object.

        Returns:
            result: True if operation succeed. False if error happens.
        """
        # Repeat for 3 cycles.
        for x in range (3):
            ad.log.info("Make a MO call.")
            if not mo_voice_call(self.log, ad, VOICE_CALL, False):
                return False
            self.log.info("turn airplane mode on then off during in call")
            if not self._airplane_mode_on_off(ad):
                return False
        return True

    def _airplane_mode_data_transfer(self, ad):
        """ Airplane mode on/off while data transfer.

        Args:
            ad: android_device object.

        Returns:
            result: True if operation succeed. False if error happens.
        """
        # Repeat for 3 cycles.
        for x in range (3):
            ad.log.info("Perform a data transferring. Start streaming")
            if not start_youtube_video(ad):
                ad.log.warning("Fail to bring up youtube video")
                self.my_error_msg += "Fail to bring up youtube video. "
                return False
            self.log.info("turn airplane mode on then off during data transferring")
            if not self._airplane_mode_on_off(ad):
                return False
        return True


    def _airplane_mode_wifi_hotspot(self, ad):
        """ Airplane mode on/off Wi-Fi hotspot enabled

        Args:
            ad: android_device object.

        Returns:
            result: True if operation succeed. False if error happens.
        """
        # Repeat for 3 cycles.
        for x in range (3):
            ad.log.info("Enable Wi-Fi Hotspot on UE")
            #if not start_youtube_video(ad):
            #    return False
            self.log.info("turn airplane mode on then off")
            if not self._airplane_mode_on_off(ad):
                return False
        return True

    def _airplane_mode_helper(self, ad, case= MOBILE_DATA_ON_OFF_CASE, rat=GEN_4G, loop=1):
        self.log.info("Lock network mode to %s." , rat)
        if not ensure_network_generation(self.log, ad, rat):
            raise signals.TestFailure("device fail to register at %s"
                %(rat))

        for x in range(self.user_params.get("apm_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name,x+1, loop))
            if case == MOBILE_DATA_ON_OFF_CASE:
                if not self._airplane_mode_mobile_data_off(ad):
                    return False
            elif case == DATA_TRANSFER_CASE:
                if not self._airplane_mode_data_transfer(ad):
                    return False
            elif case == WIFI_HOTSPOT_CASE:
                if not self._airplane_mode_wifi_hotspot(ad):
                    return False
            elif case == IN_CALL_CASE:
                if not self._airplane_mode_voice_call(ad):
                    return False
            #check radio function
            tasks = [(self.verify_device_status, (ad, VOICE_CALL))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                raise signals.TestFailure("verify_device_status failure: %s"
                    %(self.my_error_msg))
        return True

    @test_tracker_info(uuid="0205ec77-36c1-478f-9d05-c8a72fffdd03")
    @TelephonyBaseTest.tel_test_wrap
    def test_airplane_mode_data_transfer_5g(self):
        '''
            5G - [NSA/SA] Airplane mode on/off - transfer
            For NSA, call goes through IMS over LTE (VoLTE).
            For SA, call goes through IMS over LTE/NR (EPSFB or VoNR)
            depends on carrier's implementation.
            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        tasks = [(self._airplane_mode_helper, (ad, DATA_TRANSFER_CASE, GEN_5G))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            raise signals.TestFailure("_airplane_mode_data_transfer failure: %s"
                %(self.my_error_msg))
        return True

    @test_tracker_info(uuid="c76a1154-29c0-4259-bd4c-05279d80537b")
    @TelephonyBaseTest.tel_test_wrap
    def test_airplane_mode_data_transfer_4g(self):
        '''
            4G - Airplane mode on/off - Data transferring

            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        tasks = [(self._airplane_mode_helper, (ad, DATA_TRANSFER_CASE, GEN_4G))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            raise signals.TestFailure("_airplane_mode_data_transfer failure: %s"
                %(self.my_error_msg))
        return True

    @test_tracker_info(uuid="c16ea0bb-0155-4f5f-97a8-22c7e0e6e2f5")
    @TelephonyBaseTest.tel_test_wrap
    def test_airplane_mode_data_transfer_3g(self):
        '''
            3G - Airplane mode on/off - Data transferring

            Raises:
                TestFailure if not success.

            Returns:
                True if pass; False if fail
        '''
        tasks = [(self._airplane_mode_helper, (ad, DATA_TRANSFER_CASE, GEN_3G))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            raise signals.TestFailure("_airplane_mode_data_transfer failure: %s"
                %(self.my_error_msg))
        return True

    @test_tracker_info(uuid="b1db4e3b-ea0b-4b61-9f2c-4b8fc251c71a")
    @TelephonyBaseTest.tel_test_wrap
    def test_airplane_mode_wifi_hotspot_5g(self):
        '''
            5G -[NSA/SA] Airplane mode off/on - Wi-Fi Hotspot enabled
            For NSA, call goes through IMS over LTE (VoLTE).
            For SA, call goes through IMS over LTE/NR (EPSFB or VoNR)
            depends on carrier's implementation.
            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        tasks = [(self._airplane_mode_helper, (ad, WIFI_HOTSPOT_CASE, GEN_5G))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            raise signals.TestFailure("_airplane_mode_wifi_hotspot failure: %s"
                %(self.my_error_msg))
        return True

    @test_tracker_info(uuid="f21f4554-7755-4019-b8a2-6f86d1ebd57a")
    @TelephonyBaseTest.tel_test_wrap
    def test_airplane_mode_wifi_hotspot_4g(self):
        '''
            4G - Airplane mode off/on - Wi-Fi Hotspot enabled

            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        tasks = [(self._airplane_mode_helper, (ad, WIFI_HOTSPOT_CASE, GEN_4G))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            raise signals.TestFailure("_airplane_mode_wifi_hotspot failure: %s"
                %(self.my_error_msg))
        return True

    @test_tracker_info(uuid="8cf3c617-4534-4b08-b31f-f702c5f8bb8b")
    @TelephonyBaseTest.tel_test_wrap
    def test_airplane_mode_wifi_hotspot_3g(self):
        '''
            3G - Airplane mode off/on - Wi-Fi Hotspot enabled

            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        tasks = [(self._airplane_mode_helper, (ad, WIFI_HOTSPOT_CASE, GEN_3G))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            raise signals.TestFailure("_airplane_mode_wifi_hotspot failure: %s"
                %(self.my_error_msg))
        return True