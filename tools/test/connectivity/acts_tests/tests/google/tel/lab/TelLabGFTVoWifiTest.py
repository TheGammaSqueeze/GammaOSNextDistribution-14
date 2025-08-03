#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
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
import logging
from acts import asserts
from acts import signals
from acts.test_decorators import test_tracker_info
from acts.libs.utils.multithread import multithread_func
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.GFTInOutBaseTest import GFTInOutBaseTest
from acts_contrib.test_utils.tel.tel_atten_utils import set_rssi
from acts_contrib.test_utils.tel.gft_inout_defines import VOICE_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import VOLTE_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import CSFB_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import WFC_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import NO_VOICE_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import NO_SERVICE_POWER_LEVEL
from acts_contrib.test_utils.tel.gft_inout_defines import IN_SERVICE_POWER_LEVEL
from acts_contrib.test_utils.tel.gft_inout_defines import NO_SERVICE_TIME
from acts_contrib.test_utils.tel.gft_inout_defines import WAIT_FOR_SERVICE_TIME
from acts_contrib.test_utils.tel.gft_inout_utils import check_back_to_service_time
from acts_contrib.test_utils.tel.gft_inout_utils import mo_voice_call
from acts_contrib.test_utils.tel.gft_inout_utils import get_voice_call_type
from acts_contrib.test_utils.tel.gft_inout_utils import browsing_test_ping_retry
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_DISABLED
from acts_contrib.test_utils.tel.tel_defines import CALL_STATE_ACTIVE
from acts_contrib.test_utils.tel.tel_defines import CALL_STATE_HOLDING
from acts_contrib.test_utils.tel.tel_data_utils import browsing_test
from acts_contrib.test_utils.tel.tel_ims_utils import toggle_wfc
from acts_contrib.test_utils.tel.tel_ims_utils import toggle_volte
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_ims_registered
from acts_contrib.test_utils.tel.tel_logging_utils import log_screen_shot
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_iwlan
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.tel.gft_inout_utils import verify_data_connection
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_IN_CALL

WAIT_TIME_AT_NO_SERVICE_AREA = 300


class TelLabGFTVoWifiTest(GFTInOutBaseTest):

    def __init__(self, controllers):
        GFTInOutBaseTest.__init__(self, controllers)
        self.wifi_ssid = self.user_params.get('wifi_network_ssid')
        self.wifi_pw = self.user_params.get('wifi_network_pw')
        self.my_error_msg = ""
        self.rssi = ""
        logging.info("wifi_ssid = %s" %self.wifi_ssid)
        logging.info("wifi_pw = %s" %self.wifi_pw )

    def setup_test(self):
        self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
        self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
        GFTInOutBaseTest.setup_test(self)
        for ad in self.android_devices:
            ad.droid.wifiToggleState(True)
        # Ensure IMS on
        self.log.info("Turn on ims")
        tasks = [(phone_setup_volte, (self.log, ad, )) for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            for ad in self.android_devices:
                log_screen_shot(ad, self.test_name)
            error_msg = "fail to setup volte"
            self.log.error(error_msg)
            asserts.assert_true(False, "Fail: %s." %(error_msg),
                extras={"failure_cause": self.my_error_msg})
            asserts.skip(error_msg)
        # ensure WFC is enabled
        tasks = [(toggle_wfc, (self.log, ad,True)) for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            for ad in self.android_devices:
                log_screen_shot(ad, self.test_name)
            error_msg = "device does not support WFC! Skip test"
            asserts.skip(error_msg)


    def teardown_test(self):
        super().teardown_test()
        tasks = [(toggle_airplane_mode, (self.log, ad, False))
            for ad in self.android_devices]
        multithread_func(self.log, tasks)


    @test_tracker_info(uuid="21ec1aff-a161-4dc9-9682-91e0dd8a13a7")
    @TelephonyBaseTest.tel_test_wrap
    def test_wfc_in_out_wifi(self, loop=1, wfc_mode=WFC_MODE_WIFI_PREFERRED):
        """
            Enable Wi-Fi calling in Wi-Fi Preferred mode and connect to a
            valid Wi-Fi AP. Test VoWiFi call under WiFi and cellular area
            -> move to WiFi only area -> move to Cellular only area
            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wfc mode
            Returns:
                True if pass; False if fail
        """
        test_result = True
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Start test at cellular and wifi area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            self.check_network()
            if self._enable_wifi_calling(wfc_mode):
                if not self._voice_call(self.android_devices, WFC_CALL, end_call=True):
                    self.log.info("VoWiFi call failure")
                    return False
                self.log.info("Move to no service area and wifi area")
                self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
                time.sleep(WAIT_TIME_AT_NO_SERVICE_AREA)
                # check call status
                for ad in self.android_devices:
                    get_voice_call_type(ad)
                self.log.info("Move back to service area and no wifi area")
                self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
                self.adjust_wifi_signal(NO_SERVICE_POWER_LEVEL)
                self.log.info("check cellular data")
                # self._data_retry_mechanism()
                tasks = [(verify_data_connection, (ad, 3))
                    for ad in self.android_devices]
                if not multithread_func(self.log, tasks):
                    self.log.info("verify_data_connection failure")
            self.log.info("Verify device state after in-out service")
            tasks = [(check_back_to_service_time, (ad,)) for ad in self.android_devices]
            test_result = multithread_func(self.log, tasks)
            if test_result:
                test_result = self._voice_call(self.android_devices, VOICE_CALL)
            else:
                self.log.info("device is not back to service")
        return test_result

    def _enable_wifi_calling(self, wfc_mode, call_type=NO_VOICE_CALL,
        end_call=True, is_airplane_mode=False, talk_time=30):
        """ Enable Wi-Fi calling in Wi-Fi Preferred mode and connect to a
            valid Wi-Fi AP.

            Args:
                wfc_mode: wfc mode
                call_type: None would not make any calls
                end_call: hang up call
                is_airplane_mode: toggle airplane mode on or off
                talk_time: call duration

            Returns:
                True if pass; False if fail.
        """
        self.log.info("Move in WiFi area and set WFC mode to %s, airplane mode=%s"
            %(wfc_mode, is_airplane_mode))
        self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
        time.sleep(10)
        tasks = [(phone_setup_iwlan, (self.log, ad, is_airplane_mode, wfc_mode,
            self.wifi_ssid))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            self.my_error_msg += "fail to setup WFC mode to %s, " %(wfc_mode)
            raise signals.TestFailure(self.my_error_msg)
        if call_type != NO_VOICE_CALL:
           if not self._voice_call(self.android_devices, call_type, end_call, talk_time):
               self.log.error("%s failuer" %call_type)
               return False
        return True

    def _voice_call(self, ads, call_type, end_call=True, talk_time=15):
        """ Enable Wi-Fi calling in Wi-Fi Preferred mode and connect to a
            valid Wi-Fi AP.
            Args:
                ads: android devices
                call_type: WFC call, VOLTE call. CSFB call, voice call
                end_call: hangup call after voice call flag
                talk_time: in call duration in sec
            Returns:
                True if pass; False if fail.
        """
        tasks = [(mo_voice_call, (self.log, ad, call_type, end_call, talk_time))
            for ad in ads]
        if not multithread_func(self.log, tasks):
            error_msg = "%s failure" %(call_type)
            self.log.error(error_msg)
            self.my_error_msg += error_msg
            return False
        return True

    @test_tracker_info(uuid="3ca05651-a6c9-4b6b-84c0-a5d761757061")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_idle_wifi_preferred(self, wfc_mode=WFC_MODE_WIFI_PREFERRED):
        ''' In/Out Service - Idle + VoWiFi registered in Wi-Fi Preferred mode
            Enable Wi-Fi calling in Wi-Fi Preferred mode and connect to a valid Wi-Fi AP.
            Idle in service area.
            Move to no service area for 1 minute when idle.
            Move back to service area and verfiy device status.

            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wfc mode

            Returns:
                True if pass; False if fail
            Raises:
                TestFailure if not success.
        '''
        return self._in_out_wifi_wfc_mode(1, wfc_mode)


    @test_tracker_info(uuid="b06121de-f458-4fc0-b9ef-efac02e46181")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_idle_cellular_preferred(self, loop=1,wfc_mode=WFC_MODE_CELLULAR_PREFERRED):
        ''' In/Out Service - Idle + VoLTE registered in Cellular preferred mode
            Enable Wi-Fi calling in Cellular preferred mode and connect to a valid Wi-Fi AP.
            Idle in service area.
            Move to no service area for 1 minute when idle.
            Move back to service area and verify device status

            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wfc mode

            Returns:
                True if pass; False if fail
            Raises:
                TestFailure if not success.
        '''
        asserts.assert_true(self._in_out_wifi_wfc_mode(1, WFC_MODE_CELLULAR_PREFERRED),
            "Fail: %s." %(self.my_error_msg), extras={"failure_cause": self.my_error_msg})

    def _in_out_wifi_wfc_mode(self, loop=1, wfc_mode=WFC_MODE_CELLULAR_PREFERRED):
        error_msg = ""
        test_result = True
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.my_error_msg += "cylce%s: " %(x+1)
            self.log.info("Move in Wi-Fi area and set to %s" %(wfc_mode))
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            if not self._enable_wifi_calling(wfc_mode):
                error_msg = "Fail to setup WFC mode"
                self.log.info(error_msg)
                self.my_error_msg += error_msg
                return False
            self.log.info("Idle in service area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.check_network()

            self.log.info("Move to no service area in idle mode for 1 min")
            self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(NO_SERVICE_TIME)

            self.log.info("Move back to service area and verify device status")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.log.info("Verify device status after in-out service")
            tasks = [(check_back_to_service_time, (ad,)) for ad in self.android_devices]
            test_result = multithread_func(self.log, tasks)
            if test_result:
                tasks = [(self.verify_device_status, (ad, VOICE_CALL))
                    for ad in self.android_devices]
                if not  multithread_func(self.log, tasks):
                    error_msg = "verify_device_status fail, "
                    self.log.info(error_msg)
            else:
                error_msg = "device is not back to service, "
                self.log.info(error_msg)
            self.my_error_msg += error_msg
        return test_result

    @test_tracker_info(uuid="95bf5006-4ff6-4e7e-a02d-156e6b43f129")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_wifi_apm_on(self):
        '''
            1.1.4 In/Out Service - Idle + VoWiFi registered in Airplane on
            + Wi-Fi on in default mode

            Returns:
                True if pass; False if fail
            Raises:
                TestFailure if not success.
        '''
        asserts.assert_true(self._ID_1_1_4_in_out_vowifi(1, 180), "Fail: %s."
            %(self.my_error_msg), extras={"failure_cause": self.my_error_msg})
        asserts.assert_true(self._ID_1_1_4_in_out_vowifi(1, 60), "Fail: %s."
            %(self.my_error_msg), extras={"failure_cause": self.my_error_msg})
        return True

    def _ID_1_1_4_in_out_vowifi(self, loop=1, idle_time=60):
        '''
            1.1.4 In/Out Service - Idle + VoWiFi registered in Airplane on
            + Wi-Fi on in default mode

            Args:
                loop: repeat this test cases for how many times
                idle_time: at no service area

            Returns:
                True if pass; False if fail
        '''
        error_msg = ""
        test_result = True
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.my_error_msg += "cylce%s: " %(x+1)
            self.log.info("Enable Wi-Fi calling in Airplane on")
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)

            ad = self.android_devices[0]
            wfc_mode = ad.droid.imsGetWfcMode()
            tasks = [(phone_setup_iwlan, (self.log, ad, True, wfc_mode, self.wifi_ssid))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self.my_error_msg += "fail to setup WFC mode to %s, " %(wfc_mode)
                raise signals.TestFailure(self.my_error_msg)
            self.log.info("idle in service area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            time.sleep(10)
            self.log.info("Move to no service area for %s sec" %(idle_time))
            self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(idle_time)

            self.log.info("Move back to service area and verify device status")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.log.info("Verify device status after in-out service")
            tasks = [(check_back_to_service_time, (ad,)) for ad in self.android_devices]
            test_result = multithread_func(self.log, tasks)
            if test_result:
                tasks = [(self.verify_device_status, (ad, VOICE_CALL))
                    for ad in self.android_devices]
                test_result = multithread_func(self.log, tasks)
                if not test_result:
                    error_msg = "verify_device_status fail, "
            else:
                error_msg = "device is not back to service, "
                self.log.info(error_msg)
        return test_result


    def _device_status_check(self, call_type=None, end_call=True,
        talk_time=30, verify_data=True, verify_voice=True):
        '''
            Check device status
            Args:
                ad: android device
                call_type: WFC call, VOLTE call. CSFB call, voice call
                end_call: hangup call after voice call flag
                talk_time: in call duration in sec
                verify_data: flag to check data connection
                verify_voice: flag to check voice
            Returns:
                True if pass; False if fail
        '''
        tasks = [(check_back_to_service_time, (ad,))
            for ad in self.android_devices]
        if multithread_func(self.log, tasks):
            tasks = [(self.verify_device_status, (ad, call_type, end_call,
                talk_time, verify_data, verify_voice)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self.my_error_msg += "Verify_device_status fail, "
                return False
        else:
            self.my_error_msg += "device is not back to service, "
            return False
        return True

    def _move_in_out_wifi_cellular_area(self, cellular_power_level,
        wifi_power_level, hangup=False):
        '''
            Moves in out wifi/cellular area

            Args:
                cellular_power_level: cellular power level
                wifi_power_level: wifi power level

            Raises:
                TestFailure if not success.

            Returns:
                True if pass; False if fail
        '''
        self.adjust_cellular_signal(cellular_power_level)
        self.adjust_wifi_signal(wifi_power_level)
        time.sleep(WAIT_FOR_SERVICE_TIME)
        tasks = [(wait_for_ims_registered, (self.log, ad, ))
            for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            return False
        if hangup:
            for ad in self.android_devices:
                hangup_call(self.log, ad)
                time.sleep(3)
        return True

    @test_tracker_info(uuid="7d308a3e-dc01-4bc1-b986-14f6adc9d2ed")
    @TelephonyBaseTest.tel_test_wrap
    def test_hand_in_out_vowifi_incall (self, loop=1, wfc_mode = WFC_MODE_WIFI_PREFERRED):
        '''1.2.17 - [Wi-Fi Preferred] Hand In/Out while VoWiFi incall

            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wfc mode

            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        test_result = True
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Start test at wifi area and no service area")
            self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            if not self._enable_wifi_calling(wfc_mode, call_type=WFC_CALL,
                end_call=False):
                self.log.info("WFC call failure")
                test_result = False
            self.log.info("Move out Wi-Fi area to VoLTE area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(WAIT_FOR_SERVICE_TIME)
            self.log.info("check cellular data")
            # self._data_retry_mechanism()
            tasks = [(verify_data_connection, (ad, 3))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self.log.info("verify_data_connection failure")
            for ad in self.android_devices:
                hangup_call(self.log, ad)
            # Make a MO VoLTE call and verify data connection
            if not self._voice_call(self.android_devices, VOLTE_CALL, False):
                self.log.info("VOLTE call failure")
                test_result = False
            #Move back to Wi-Fi area during incall.
            self.log.info("Move back to Wi-Fi area during incall.")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            time.sleep(WAIT_FOR_SERVICE_TIME)
            for ad in self.android_devices:
                hangup_call(self.log, ad)
            # check device status
            test_result = self._device_status_check()
        return test_result


    @test_tracker_info(uuid="9dda069f-068c-47c8-b9e1-2b1a0f3a6bdd")
    @TelephonyBaseTest.tel_test_wrap
    def test_hand_in_out_vowifi_incall_stress_ims_on(self, loop=1,
        wfc_mode=WFC_MODE_WIFI_PREFERRED):
        '''
            1.2.18 - [Wi-Fi Preferred] Hand In/Out while VoWiFi incall
            - Stress, IMS on

            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wfc mode
            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        test_result = True
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Start test at wifi area and service area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            if not self._enable_wifi_calling(wfc_mode, call_type=WFC_CALL,
                end_call=False):
                raise signals.TestFailure("VoWiFi call failure: %s"
                    %(self.my_error_msg))
            # Move out Wi-Fi area to VoLTE area during incall.
            self.log.info("Move out Wi-Fi area to VoLTE area")
            if not self._move_in_out_wifi_cellular_area(
                IN_SERVICE_POWER_LEVEL,NO_SERVICE_POWER_LEVEL):
                raise signals.TestFailure("ims is not registered: %s"
                    %(self.my_error_msg))
            self.log.info("Move back to Wi-Fi area")
            if not self._move_in_out_wifi_cellular_area(
                IN_SERVICE_POWER_LEVEL, IN_SERVICE_POWER_LEVEL, True):
                raise signals.TestFailure("ims is not registered: %s"
                    %(self.my_error_msg))
            if not self._device_status_check():
                raise signals.TestFailure(self.my_error_msg)
        return test_result


    @test_tracker_info(uuid="e3633a6b-425a-4e4f-a58c-2d6aea56ec96")
    @TelephonyBaseTest.tel_test_wrap
    def test_hand_in_out_vowifi_incall_stress_ims_off(self, loop=1,
        wfc_mode = WFC_MODE_WIFI_PREFERRED):
        '''
            [Wi-Fi Preferred] Hand In/Out while VoWiFi incall -
            Hand In/Out stress, IMS on - Hand In/Out, IMS off

            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wfc mode

            Raises:
                TestFailure if not success.

            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            tasks = [(toggle_volte, (self.log, ad, False))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                raise signals.TestFailure("fail to turn off IMS: %s"
                    %(self.my_error_msg))
            self.log.info("Start test at wifi area and service area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            if not self._enable_wifi_calling(wfc_mode, call_type=WFC_CALL,
                end_call=False):
                raise signals.TestFailure("VoWiFi call failure: %s"
                    %(self.my_error_msg))
            #Move out Wi-Fi area to VoLTE area during incall.
            self.log.info("Move out Wi-Fi area to VoLTE area")
            self._move_in_out_wifi_cellular_area(
                IN_SERVICE_POWER_LEVEL, IN_SERVICE_POWER_LEVEL)
            time.sleep(3)
            #Make a MO CSFB call "
            if not self._voice_call(self.android_devices, CSFB_CALL, False):
                raise signals.TestFailure("CSFB call failure: %s"
                    %(self.my_error_msg))
            #Move back to Wi-Fi area during incall.
            self.log.info("Move to WiFi only area and no VoLTE area")
            self._move_in_out_wifi_cellular_area(NO_SERVICE_POWER_LEVEL,
                IN_SERVICE_POWER_LEVEL, True)
            if not self._device_status_check():
                raise signals.TestFailure(self.my_error_msg)
        return True



    @test_tracker_info(uuid="1f0697e5-6798-4cb1-af3f-c246cac59a40")
    @TelephonyBaseTest.tel_test_wrap
    def test_rove_in_out_ims_on_cellular_preferred(self, loop=1,
        wfc_mode=WFC_MODE_CELLULAR_PREFERRED):
        '''
            [Cellular Preferred] Rove In/Out when idle - IMS on

            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wfc mode

            Raises:
                TestFailure if not success.

            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("roveinout_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Move in Wi-Fi area in cellular preferred mode")
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            time.sleep(10)
            if not self._enable_wifi_calling(wfc_mode, call_type=VOLTE_CALL,
                end_call=False):
                raise signals.TestFailure("VoLTE call failure: %s"
                    %(self.my_error_msg))

            self.log.info("Move out Wi-Fi area to VoLTE area")
            self.adjust_wifi_signal(NO_SERVICE_POWER_LEVEL)
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            time.sleep(WAIT_FOR_SERVICE_TIME)
            self.log.info("check cellular data")
            # self._data_retry_mechanism()
            tasks = [(verify_data_connection, (ad, 3))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self.log.info("verify_data_connection failure")
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            tasks = [(wait_for_ims_registered, (self.log, ad, )) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                raise signals.TestFailure("IMS is not registered: %s"
                    %(self.my_error_msg))
            if not self._device_status_check():
                raise signals.TestFailure(self.my_error_msg)
        return True


    @test_tracker_info(uuid="89690d28-e21e-4baf-88cf-be04675b764b")
    @TelephonyBaseTest.tel_test_wrap
    def test_rove_in_out_ims_on_wifi_preferred(self, loop=1, wfc_mode=WFC_MODE_WIFI_PREFERRED):
        ''' 1.2.154 - [Wi-Fi Preferred] Rove In/Out when idle - IMS on

            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wfc mode

            Raises:
                TestFailure if not success.

            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("roveinout_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Move in Wi-Fi area in wifi preferred mode")
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            time.sleep(10)
            if not self._enable_wifi_calling(wfc_mode, call_type=WFC_CALL,
                end_call=False):
                raise signals.TestFailure("VoWiFi call failure: %s"
                    %(self.my_error_msg))

            self.log.info("Move out Wi-Fi area to VoLTE area when idle.")
            self.adjust_wifi_signal(NO_SERVICE_POWER_LEVEL)
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)

            tasks = [(wait_for_ims_registered, (self.log, ad, ))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                raise signals.TestFailure("IMS is not registered: %s"
                    %(self.my_error_msg))
            self.log.info("check cellular data")
            # self._data_retry_mechanism()
            tasks = [(verify_data_connection, (ad, 3))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self.log.info("verify_data_connection failure")
            self.log.info("Move back to Wi-Fi area when idle.")
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)

            tasks = [(wait_for_ims_registered, (self.log, ad, ))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                raise signals.TestFailure("IMS is not registered: %s"
                    %(self.my_error_msg))
            if not self._device_status_check():
                raise signals.TestFailure(self.my_error_msg)
        return True


    @test_tracker_info(uuid="cd453193-4769-4fa5-809c-a6afb1d833c3")
    @TelephonyBaseTest.tel_test_wrap
    def test_rove_in_out_ims_off_wifi_preferred(self, loop=1, wfc_mode=WFC_MODE_WIFI_PREFERRED):
        ''' [Wi-Fi Preferred] Rove In/Out when idle - IMS off

            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wfc mode

            Raises:
                TestFailure if not success.

            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("roveinout_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Turn off IMS")
            tasks = [(toggle_volte, (self.log, ad, False))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                raise signals.TestFailure("fail to turn off IMS: %s"
                    %(self.my_error_msg))
            self.log.info("Move in Wi-Fi area in wifi preferred mode")
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            time.sleep(10)
            if not self._enable_wifi_calling(wfc_mode, call_type=WFC_CALL,
                end_call=False):
                raise signals.TestFailure("VoWiFi call failure: %s"
                    %(self.my_error_msg))

            self.log.info("Move out Wi-Fi area to VoLTE area when idle.")
            self.adjust_wifi_signal(NO_SERVICE_POWER_LEVEL)
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)

            tasks = [(wait_for_ims_registered, (self.log, ad, ))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                raise signals.TestFailure("IMS is not registered: %s"
                    %(self.my_error_msg))
            self.log.info("check cellular data")
            # self._data_retry_mechanism()
            tasks = [(verify_data_connection, (ad, 3))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self.log.info("verify_data_connection failure")
            self.log.info("Move back to Wi-Fi area when idle.")
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)

            tasks = [(wait_for_ims_registered, (self.log, ad, ))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                raise signals.TestFailure("IMS is not registered: %s"
                    %(self.my_error_msg))
            if not self._device_status_check():
                raise signals.TestFailure(self.my_error_msg)
        return True


    @test_tracker_info(uuid="2632e594-3715-477b-b905-405ac8e490a9")
    @TelephonyBaseTest.tel_test_wrap
    def test_vowifi_airplane_mode_on(self):
        '''
            Enable Wi-Fi calling in Airplane on + Wi-Fi on in default mode and
            connect to a valid Wi-Fi AP.
            Make a MO VoWiFi call in service area.
            Move to no service area for 1 minute during incall.
            Move back to service area

            Returns:
                True if pass; False if fail
            Raises:
                TestFailure if not success.
        '''
        asserts.assert_true(self._ID_1_1_11_vowifi_airplane_mode_on(1, 60),
            "Fail: %s." %(self.my_error_msg), extras={"failure_cause": self.my_error_msg})
        asserts.assert_true(self._ID_1_1_11_vowifi_airplane_mode_on(1, 180),
            "Fail: %s." %(self.my_error_msg), extras={"failure_cause": self.my_error_msg})
        return True

    def _ID_1_1_11_vowifi_airplane_mode_on(self, loop=1, idle_time=60):
        '''
            1.1.11 - In/Out Service - VoWiFi incall in Airplane on + Wi-Fi on in default mode

            Args:
                loop: repeat this test cases for how many times
                idle_time: at no service area

            Returns:
                True if pass; False if fail
        '''
        error_msg = ""
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.my_error_msg += "cylce%s: " %(x+1)
            self.log.info("idle in service area")
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            # Make a MO VoWiFi call in service area.
            self.log.info("Enable Wi-Fi calling in Airplane on")
            ad = self.android_devices[0]
            wfc_mode = ad.droid.imsGetWfcMode()
            tasks = [(phone_setup_iwlan, (self.log, ad, True, wfc_mode, self.wifi_ssid))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self.my_error_msg += "fail to setup WFC mode to %s, " %(wfc_mode)
                raise signals.TestFailure(self.my_error_msg)
            self.log.info("Move to no service area for %s sec" %(idle_time))
            self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(idle_time)
            self.log.info("Move back to service area and verify device status")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            if not self._device_status_check():
                raise signals.TestFailure(self.my_error_msg)
        return True


    @test_tracker_info(uuid="2b1f19c5-1214-41bd-895f-86987f1cf2b5")
    @TelephonyBaseTest.tel_test_wrap
    def test_vowifi_call_wifi_preferred(self, loop=1 ,wfc_mode=WFC_MODE_WIFI_PREFERRED):
        '''
            In/Out Service - VoWiFi incall in Wi-Fi Preferred mode

            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wifi prefer mode

            Returns:
                True if pass; False if fail
            Raises:
                TestFailure if not success.
        '''
        for x in range(self.user_params.get("roveinout_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Start test at cellular and wifi area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            self.check_network()
            if not self._enable_wifi_calling(wfc_mode, call_type=WFC_CALL,
                end_call=False):
                raise signals.TestFailure("VoWiFi call failure: %s"
                    %(self.my_error_msg))

            self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(WAIT_FOR_SERVICE_TIME)
            # check call status
            for ad in self.android_devices:
                get_voice_call_type(ad)
            self.log.info("Move back to service area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.log.info("Verify device state after in-out service")
            if not self._device_status_check():
                raise signals.TestFailure(self.my_error_msg)
        return True



    @test_tracker_info(uuid="63dfa017-8bdb-4c61-a29e-7c347982a5ac")
    @TelephonyBaseTest.tel_test_wrap
    def test_volte_call_cellular_preferred(self, loop=1, wfc_mode=WFC_MODE_CELLULAR_PREFERRED):
        '''
            In/Out Service - VoLTE incall in Cellular preferred mode
            Make sure that MO/MT VoWiFi call can be made after In/Out service
            in Wi-Fi Preferred mode and Airplane on + Wi-Fi on and MO/MT
            VoLTE call can be made in Cellular preferred mode.

            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wifi prefer mode

            Returns:
                True if pass; False if fail
            Raises:
                TestFailure if not success.
        '''
        for x in range(self.user_params.get("roveinout_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Start test at cellular and wifi area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            self.check_network()

            if not self._enable_wifi_calling(wfc_mode, call_type=VOLTE_CALL,
                end_call=False):
                raise signals.TestFailure("VoLTE call failure: %s"
                    %(self.my_error_msg))
            self.log.info(" Move to no service area for 1 minute during incall.")
            self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(WAIT_FOR_SERVICE_TIME)
            # check call status
            for ad in self.android_devices:
                get_voice_call_type(ad)
            self.log.info("Move back to service area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.log.info("Verify device state after in-out service")
            if not self._device_status_check(call_type=VOLTE_CALL):
                raise signals.TestFailure(self.my_error_msg)
        return True


    @test_tracker_info(uuid="4f196186-b163-4c78-bdd9-d8fd7dc79dac")
    @TelephonyBaseTest.tel_test_wrap
    def test_wfc_in_out_wifi_disabled(self, loop=1, wfc_mode=WFC_MODE_DISABLED):
        """
            [LAB][Wi-Fi Preferred/Cellular Preferred] In/Out Wi-Fi only area with
            Wi-Fi calling disabled - Idle -> Make sure that radio function can work
            after in/out Wi-Fi only area when Wi-Fi calling disabled.

            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wfc mode
            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        """
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Start test at cellular and wifi area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            if not self._enable_wifi_calling(wfc_mode, ):
                raise signals.TestFailure("_enable_wifi_calling failure: %s"
                    %(self.my_error_msg))
            self.log.info("Move out cellular area to Wi-Fi only area")
            self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(WAIT_TIME_AT_NO_SERVICE_AREA)
            self.log.info("Move back to service area and no wifi area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(NO_SERVICE_POWER_LEVEL)

            self.log.info("Verify device state after in-out service")
            if not self._device_status_check(call_type=VOICE_CALL):
                raise signals.TestFailure(self.my_error_msg)
        return True

    @test_tracker_info(uuid="d597a694-fae9-426b-ba5e-97a9844cba4f")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_wifi_browsing_wifi_preferred(self, loop=1, wfc_mode=WFC_MODE_WIFI_PREFERRED):
        '''
            [LAB][Wi-Fi Preferred] In/Out Wi-Fi only area with Wi-Fi calling enabled
            Browsing -> Make sure that radio function can work after in/out Wi-Fi
            only area in Wi-Fi preferred mode.

            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wfc mode

            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Start test at cellular and wifi area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            self.check_network()
            if not self._enable_wifi_calling(wfc_mode, call_type=WFC_CALL,
                end_call=False):
                raise signals.TestFailure("VoWiFi call failure: %s"
                    %(self.my_error_msg))
            #Keep browsing then move out cellular area to Wi-Fi only area
            tasks_a = [(self._in_out_browse, ())]
            tasks_b = [(browsing_test_ping_retry, (ad, )) for ad in self.android_devices]
            tasks_b.extend(tasks_a)
            if not multithread_func(self.log, tasks_b):
                raise signals.TestFailure("in/out browsing failure: %s"
                    %(self.my_error_msg))
            self.log.info("Verify device state after in-out service")
            if not self._device_status_check(call_type=VOICE_CALL):
                raise signals.TestFailure(self.my_error_msg)
        return True

    @test_tracker_info(uuid="c7d3dc90-c0ed-48f8-b674-6d5b1efea3cc")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_wifi_browsing_wfc_disabled(self, loop=1, wfc_mode=WFC_MODE_DISABLED):
        '''
            [LAB][Wi-Fi Preferred/Cellular Preferred] In/Out Wi-Fi only area
            with Wi-Fi calling disabled - Browsing
            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wfc mode

            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Start test at cellular and wifi area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            if not self._enable_wifi_calling(wfc_mode, call_type=WFC_CALL,
                end_call=False):
                raise signals.TestFailure("VoWiFi call failure: %s"
                    %(self.my_error_msg))
            # Keep browsing then move out cellular area to Wi-Fi only area
            tasks_a = [(self._in_out_browse, ())]
            tasks_b = [(browsing_test_ping_retry, (ad, )) for ad in self.android_devices]
            tasks_b.extend(tasks_a)
            if not multithread_func(self.log, tasks_b):
                for ad in self.android_devices:
                    log_screen_shot(ad, "browsing_failure")
                raise signals.TestFailure("in/out browsing failure: %s"
                    %(self.my_error_msg))
            if not self._device_status_check(call_type=VOICE_CALL):
                raise signals.TestFailure(self.my_error_msg)
        return True

    def _in_out_browse(self):
        '''
            Move out cellular area to Wi-Fi only area and
            move back to service area and no wifi area
        '''
        self.log.info("Move out cellular area to Wi-Fi only area")
        self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
        # browsing at no service area
        time.sleep(WAIT_TIME_AT_NO_SERVICE_AREA)
        self.log.info("Move back to service area and no wifi area")
        self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
        self.adjust_wifi_signal(NO_SERVICE_POWER_LEVEL)
        return True

    @test_tracker_info(uuid="9029f3bb-3aca-42be-9241-ed21aab418ff")
    @TelephonyBaseTest.tel_test_wrap
    def test_hand_in_out_vowifi_incall_data_transfer(self, loop=1,
        wfc_mode=WFC_MODE_WIFI_PREFERRED):
        '''
            [Wi-Fi Preferred] Hand In/Out while VoWiFi incall -
            Data transferring -> Make sure that IMS can register between Wi-Fi
            and LTE NW and no call dropped during data transferring after
            hand in/out in Wi-Fi Preferred mode.

            Args:
                loop: repeat this test cases for how many times
                wfc_mode: wfc mode

            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Start test at cellular and wifi area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            self.check_network()
            if not self._enable_wifi_calling(wfc_mode, call_type=WFC_CALL,
                end_call=False):
                raise signals.TestFailure("VoWiFi call failure: %s"
                    %(self.my_error_msg))
            #Download a large file in the background then make a MO VoWiFi call.
            if not self._voice_call(self.android_devices, WFC_CALL, False,):
                    error_msg = "VoWiFi call failure, "
                    self.log.info(error_msg)
                    self._on_failure(error_msg)
            self.log.info("Move out Wi-Fi area to VoLTE area during incall + data transferring.")
            self.adjust_wifi_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(WAIT_FOR_SERVICE_TIME)
            for ad in self.android_devices:
                hangup_call(self.log, ad)
            if not self._device_status_check(call_type=VOLTE_CALL):
                raise signals.TestFailure(self.my_error_msg)
            # Download a file in the background then make a MO VoLTE call.
            self.log.info("Move back to Wi-Fi area during incall + data transferring.")
            # Move back to Wi-Fi area during incall + data transferring.
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            time.sleep(160)
            for ad in self.android_devices:
                hangup_call(self.log, ad)
            if not self._device_status_check(call_type=VOICE_CALL):
                raise signals.TestFailure(self.my_error_msg)
        return True


    @test_tracker_info(uuid="45c1f623-5eeb-4ee4-8739-2b0ebcd5f19f")
    @TelephonyBaseTest.tel_test_wrap
    def test_rove_in_out_ims_on_airplane_mode(self, loop=1,):
        '''
            [Wi-Fi Calling+Airplane On] Rove In/Out when idle - IMS on ->
            Make sure that IMS can register between Wi-Fi and LTE NW and
            VoWiFi call can be made after rove in/out.

            Args:
                loop: repeat this test cases for how many times

            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.my_error_msg += "cylce%s: " %(x+1)

            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.log.info("Enable Wi-Fi calling in Airplane on")
            ad = self.android_devices[0]
            wfc_mode = ad.droid.imsGetWfcMode()
            tasks = [(phone_setup_iwlan, (self.log, ad, True, wfc_mode, self.wifi_ssid))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self.my_error_msg += "fail to setup WFC mode to %s, " %(wfc_mode)
                raise signals.TestFailure(self.my_error_msg)

            self.log.info("Make a MO VoWiFi call in service area")
            if not self._voice_call(self.android_devices, WFC_CALL, False):
                raise signals.TestFailure("VoWiFi call failure: %s"
                    %(self.my_error_msg))

            self.log.info("Move out Wi-Fi area to VoLTE area when idle.")
            self.adjust_wifi_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(WAIT_FOR_SERVICE_TIME)
            # if not self._device_status_check(call_type=VOICE_CALL):
            #    raise signals.TestFailure(self.my_error_msg)
            self.log.info("Move back to Wi-Fi area when idle.")
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            self.log.info("Verify device status after in-out service")
            self.log.info("Wait for maximum to 160 sec before IMS switched")
            tasks = [(wait_for_ims_registered, (self.log, ad, 160))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                asserts.assert_true(False, "Fail: %s." %("wait_for_ims_registered failure"),
                    extras={"failure_cause": self.my_error_msg})
            # turn off APM
            self.log.info("Turn off airplande mode")
            tasks = [(toggle_airplane_mode, (self.log, ad, False))
                for ad in self.android_devices]
            multithread_func(self.log, tasks)
        return True


    @test_tracker_info(uuid="9b586a06-3b33-41be-ae0f-aacf157212b9")
    @TelephonyBaseTest.tel_test_wrap
    def test_wifi_rove_out_wfc(self,loop=1, wfc_mode=WFC_MODE_WIFI_PREFERRED, idle_time=180):
        '''
            [Wi-Fi Preferred] Rove In/Out when idle for 1 hours - Wi-Fi calling enabled

            Args:
                loop: repeat this test cases for how many times
                wfc_mode:
                idle_time: how long device will be idle

            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Start test at cellular and wifi area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            if not self._enable_wifi_calling(wfc_mode, call_type=WFC_CALL,
                end_call=False):
                raise signals.TestFailure("VoWiFi call failure: %s"
                    %(self.my_error_msg))

            if not self._voice_call(self.android_devices, WFC_CALL, ):
                raise signals.TestFailure("VoWiFi call failure: %s"
                    %(self.my_error_msg))
            time.sleep(idle_time)

            self.log.info("Move out Wi-Fi area to VoLTE area when idle.")
            self.adjust_wifi_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(30)
            self.log.info("check cellular data")
            # self._data_retry_mechanism()
            tasks = [(verify_data_connection, (ad, 3))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self.log.info("verify_data_connection failure")
            if not self._voice_call(self.android_devices, VOLTE_CALL, ):
                raise signals.TestFailure("VOLTE call failure: %s"
                    %(self.my_error_msg))
            self.log.info("Move back to Wi-Fi area when idle.")
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            if not self._device_status_check(call_type=WFC_CALL):
                raise signals.TestFailure(self.my_error_msg)
        return True


    @test_tracker_info(uuid="fb431706-737d-4020-b3d1-347dc4d7ce03")
    @TelephonyBaseTest.tel_test_wrap
    def test_wifi_rove_out_no_wfc(self,loop=1, wfc_mode=WFC_MODE_DISABLED,
        idle_time=180):
        '''
            [Wi-Fi Preferred] Rove In/Out when idle for 1 hours
            Wi-Fi calling disabled. Make sure that IMS can register between
            Wi-Fi and LTE NW and VoWiFi call can be made after rove in/out.

            Args:
                loop: repeat this test cases for how many times
                wfc_mode:
                idle_time: how long device will be idle

            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Start test at cellular and wifi area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            self.check_network()
            if not self._enable_wifi_calling(wfc_mode):
                raise signals.TestFailure("_enable_wifi_calling failure: %s"
                    %(self.my_error_msg))

            if not self._voice_call(self.android_devices, VOLTE_CALL, ):
                raise signals.TestFailure("VOLTE call failure: %s"
                    %(self.my_error_msg))
            time.sleep(idle_time)

            self.log.info("Move out Wi-Fi area to VoLTE area when idle.")
            self.adjust_wifi_signal(NO_SERVICE_POWER_LEVEL)
            time.sleep(30)
            self.log.info("check cellular data")
            # self._data_retry_mechanism()
            tasks = [(verify_data_connection, (ad, 3))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                self.log.info("verify_data_connection failure")
            if not self._voice_call(self.android_devices, VOLTE_CALL, ):
                raise signals.TestFailure("VOLTE call failure: %s"
                    %(self.my_error_msg))
            self.log.info("Move back to Wi-Fi area when idle.")
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            # Enable Wi-Fi calling in Wi-Fi Preferred mode
            if not self._enable_wifi_calling(WFC_MODE_WIFI_PREFERRED):
                raise signals.TestFailure("_enable_wifi_calling failure: %s"
                    %(self.my_error_msg))
            # check device status
            if not self._device_status_check(call_type=WFC_CALL):
                raise signals.TestFailure(self.my_error_msg)
        return True


    @test_tracker_info(uuid="5ddfa906-7756-42b4-b1c4-2ac507211547")
    @TelephonyBaseTest.tel_test_wrap
    def test_hand_in_out_vowifi_incall_call_hold(self,loop=1,
        wfc_mode=WFC_MODE_WIFI_PREFERRED, idle_time=180):
        '''
            [NSA/SA][Wi-Fi Preferred] Hand In/Out while VoWiFi incall - Hold
            Ensure IMS can register between Wi-Fi and LTE/NR NW and no call dropped
            during incall with hold on after hand in/out in Wi-Fi Preferred mode.

            Args:
                loop: repeat this test cases for how many times
                wfc_mode:
                idle_time: how long device will be idle

            Raises:
                TestFailure if not success.
            Returns:
                True if pass; False if fail
        '''
        for x in range(self.user_params.get("wfc_cycle", 1)):
            self.log.info("%s loop: %s/%s" %(self.current_test_name, x+1, loop))
            self.log.info("Start test at wifi area and service area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            #Make a MO VoWiFi call and hold the call.
            if not self._enable_wifi_calling(wfc_mode, call_type=WFC_CALL,
                end_call=False):
                raise signals.TestFailure("VoWiFi call failure: %s"
                    %(self.my_error_msg))
            tasks = [(self._call_hold, (ad,)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                raise signals.TestFailure("fail to hold call: %s"
                    %(self.my_error_msg))

            # Move out Wi-Fi area to 4G area during incall
            self.log.info("Move out Wi-Fi area to VoLTE area")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(NO_SERVICE_POWER_LEVEL)
            # Unhold the call
            tasks = [(self._call_unhold, (ad,)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                raise signals.TestFailure("fail to unhold call: %s"
                    %(self.my_error_msg))
            time.sleep(30)
            for ad in self.android_devices:
                hangup_call(self.log, ad)

            # Make a MO VoLTE call and hold the call.
            if not self._voice_call(self.android_devices, VOLTE_CALL, False):
                raise signals.TestFailure("VoLTE call failure: %s"
                    %(self.my_error_msg))
            tasks = [(self._call_hold, (ad,)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                raise signals.TestFailure("fail to hold call: %s"
                    %(self.my_error_msg))

            #Move back to Wi-Fi area during incall.
            self.log.info("Move back to Wi-Fi area during incall.")
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
            tasks = [(self._call_unhold, (ad,)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                raise signals.TestFailure("fail to unhold call: %s"
                    %(self.my_error_msg))
            time.sleep(30)
            for ad in self.android_devices:
                hangup_call(self.log, ad)
            # check device status
            if not self._device_status_check(call_type=WFC_CALL):
                raise signals.TestFailure(self.my_error_msg)
        return True


    def _call_hold(self, ad, wait_time=WAIT_TIME_IN_CALL):
        '''
            Press call hold

            Args:
                ad: android device
                wait_time: wait time after press hold/unhold in sec

            Returns:
                True if pass; False if fail
        '''
        if ad.droid.telecomIsInCall():
            ad.droid.telecomShowInCallScreen()
            call_list = ad.droid.telecomCallGetCallIds()
            ad.log.info("Calls in PhoneA %s", call_list)
            call_id = call_list[0]
            call_state = ad.droid.telecomCallGetCallState(call_id)
            if call_state != CALL_STATE_ACTIVE:
                ad.log.error("Call_id:%s, state:%s, expected: STATE_ACTIVE",
                        call_id,
                        ad.droid.telecomCallGetCallState(call_id))
                return False
            ad.log.info("Hold call_id %s on PhoneA", call_id)
            log_screen_shot(ad, "before_call_hold")
            ad.droid.telecomCallHold(call_id)
            time.sleep(wait_time)

            call_state = ad.droid.telecomCallGetCallState(call_id)
            log_screen_shot(ad, "after_call_hold")
            if call_state != CALL_STATE_HOLDING:
                ad.log.error("Call_id:%s, state:%s, expected: STATE_HOLDING",
                                call_id,
                                ad.droid.telecomCallGetCallState(call_id))
                log_screen_shot(ad, "hold_failure")
                return False
        else:
            ad.log.info("device is not in call")
            return False
        return True

    def _call_unhold(self, ad, wait_time=WAIT_TIME_IN_CALL):
        '''
            Press call unhold

            Args:
                ad: android device
                wait_time: wait time after press hold/unhold in sec

            Returns:
                True if pass; False if fail
        '''
        if ad.droid.telecomIsInCall():
            ad.droid.telecomShowInCallScreen()
            call_list = ad.droid.telecomCallGetCallIds()
            ad.log.info("Calls in PhoneA %s", call_list)
            call_id = call_list[0]
            call_state = ad.droid.telecomCallGetCallState(call_id)
            if call_state != CALL_STATE_HOLDING:
                ad.log.error("Call_id:%s, state:%s, expected: STATE_HOLDING",
                        call_id,
                        ad.droid.telecomCallGetCallState(call_id))
                return False
            ad.log.info("Unhold call_id %s on PhoneA", call_id)
            log_screen_shot(ad, "before_unhold")
            ad.droid.telecomCallUnhold(call_id)
            time.sleep(wait_time)
            call_state = ad.droid.telecomCallGetCallState(call_id)
            log_screen_shot(ad, "after_unhold")
            if call_state != CALL_STATE_ACTIVE:
                ad.log.error("Call_id:%s, state:%s, expected: STATE_ACTIVE",
                        call_id,
                        call_state)
                log_screen_shot(ad, "_unhold_failure")
                return False
        else:
            ad.log.info("device is not in call")
            return False
        return True

