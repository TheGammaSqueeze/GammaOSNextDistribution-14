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

import collections
import time
import random
import logging
from acts import asserts
from acts import signals
from acts.test_decorators import test_tracker_info
from acts.libs.utils.multithread import multithread_func
from acts.utils import get_current_epoch_time

from acts_contrib.test_utils.tel.tel_atten_utils import set_rssi
from acts_contrib.test_utils.tel.tel_defines import SignalStrengthContainer
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.GFTInOutBaseTest import GFTInOutBaseTest
from acts_contrib.test_utils.tel.gft_inout_defines import VOLTE_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import WFC_CALL
from acts_contrib.test_utils.tel.gft_inout_defines import NO_SERVICE_POWER_LEVEL
from acts_contrib.test_utils.tel.gft_inout_defines import IN_SERVICE_POWER_LEVEL
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.wifi import wifi_power_test_utils as wputils
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_ims_registered
from acts_contrib.test_utils.tel.tel_logging_utils import log_screen_shot
from acts_contrib.test_utils.tel.tel_logging_utils import start_qxdm_loggers
from acts_contrib.test_utils.tel.tel_logging_utils import start_sdm_loggers
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_iwlan
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_ims_utils import toggle_wfc
from acts_contrib.test_utils.tel.tel_ims_utils import is_wfc_enabled
from acts_contrib.test_utils.tel.gft_inout_utils import mo_voice_call

WAIT_TIME_AT_NO_SERVICE_AREA = 300


class TelLabGFTVoWifiStressTest(GFTInOutBaseTest):

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
            ad.droid.telephonyStartTrackingSignalStrengthChange()
        # Ensure IMS on
        self.log.info("Turn on ims")
        tasks = [(phone_setup_volte, (self.log, ad, )) for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            for ad in self.android_devices:
                log_screen_shot(ad, self.test_name)
            error_msg = "fail to setup volte"
            self.log.error(error_msg)
        # ensure WFC is enabled
        tasks = [(toggle_wfc, (self.log, ad,True)) for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            for ad in self.android_devices:
                log_screen_shot(ad, self.test_name)
            error_msg = "device does not support WFC!"
            self.log.error(error_msg)


    def teardown_test(self):
        super().teardown_test()
        tasks = [(toggle_airplane_mode, (self.log, ad, False))
            for ad in self.android_devices]
        multithread_func(self.log, tasks)
        for ad in self.android_devices:
            ad.droid.telephonyStopTrackingSignalStrengthChange()

    def _check_signal_strength(self):
        """
            check cellular signal strength
        """
        for ad in self.android_devices:
            # SIGNAL_STRENGTH_LTE = "lteSignalStrength"
            # SIGNAL_STRENGTH_LTE_DBM = "lteDbm"
            # SIGNAL_STRENGTH_LTE_LEVEL = "lteLevel"
            result = ad.droid.telephonyGetSignalStrength()
            ad.log.info("lteDbm: {}".format(result[SignalStrengthContainer.
                SIGNAL_STRENGTH_LTE_DBM]))
            ad.log.info("lteSignalStrength: {}".format(result[SignalStrengthContainer.
                SIGNAL_STRENGTH_LTE]))
            ad.log.info("ltelevel: {}".format(result[SignalStrengthContainer.
                SIGNAL_STRENGTH_LTE_LEVEL]))

    @TelephonyBaseTest.tel_test_wrap
    def test_wifi_cellular_signal(self, wfc_mode=WFC_MODE_WIFI_PREFERRED):
        """
            check WiFi and cellular signal

            Args:
                wfc_mode: wfc mode

            Returns:
                True if pass; False if fail
        """
        tasks = [(phone_setup_iwlan, (self.log, ad, False, wfc_mode,
            self.wifi_ssid)) for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            error_msg = "fail to setup WFC mode to %s" %(wfc_mode)
            self.log.error(error_msg)
        cellular_power_level = 0
        wifi_power_level = 0
        for x in range(20):
            self.adjust_cellular_signal(cellular_power_level)
            self.adjust_wifi_signal(wifi_power_level)
            time.sleep(5)
            for ad in self.android_devices:
                log_screen_shot(ad)
                wifi_rssi = wputils.get_wifi_rssi(ad)
                ad.log.info("wifi_power_level to %s , wifi_rssi=%s"
                    %(wifi_power_level, wifi_rssi))
            cellular_power_level += 5
            wifi_power_level += 5
            self._check_signal_strength()
        return True


    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="176a0230-c35d-454d-a1f7-c706f71c5dbd")
    def test_wfc_marginal_area_random_stress(self, wfc_mode=WFC_MODE_WIFI_PREFERRED):
        """
            b/213907614 marginal area with random
            Adjusts WiFi and cellular signal randomly

            Args:
                wfc_mode: wfc mode

            Returns:
                True if pass; False if fail
        """
        fail_count = collections.defaultdict(int)
        loop = self.user_params.get("marginal_cycle", 5)
        error_msg = ""
        self.log.info("Start test at cellular and wifi area")
        self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
        self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
        self.check_network()
        tasks = [(phone_setup_iwlan, (self.log, ad, False, wfc_mode,
            self.wifi_ssid)) for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            error_msg = "fail to setup WFC mode to %s" %(wfc_mode)
            fail_count["fail_to_setup_WFC_mode"] += 1
            iteration_result = False
            self.log.error(error_msg)

        for i in range(1, loop + 1):
            msg = "Stress Test %s Iteration: <%s> / <%s>" % (
                self.test_name, i, loop)
            begin_time = get_current_epoch_time()
            self.log.info(msg)
            iteration_result = True
            self.log.info("%s loop: %s/%s" %(self.current_test_name, i, loop))

            self.log.info("Randomly adjust wifi and cellular signal")
            for x in range (1):
                cellular_power_level = random.randint(30, 50)
                wifi_power_level = random.randint(5, 30)
                self.log.info("adjust wifi power level to %s"%wifi_power_level )
                self.log.info("adjust cellular power level to %s" %cellular_power_level)
                self.adjust_wifi_signal(wifi_power_level)
                self.adjust_cellular_signal(cellular_power_level)
                time.sleep(10)
                self._check_signal_strength()
                for ad in self.android_devices:
                    log_screen_shot(ad)
                    wifi_rssi = wputils.get_wifi_rssi(ad)
                    ad.log.info("wifi_power_level to %s , wifi_rssi=%s"
                        %(wifi_power_level, wifi_rssi))
                self.log.info("check ims status")
                tasks = [(wait_for_ims_registered, (self.log, ad, ))
                    for ad in self.android_devices]
                if not multithread_func(self.log, tasks):
                    error_msg = "Fail: IMS is not registered"
                    fail_count["IMS_is_not_registered"] += 1
                    iteration_result = False
                    self.log.error("%s:%s", msg, error_msg)
                tasks = [(is_wfc_enabled, (self.log, ad, ))
                    for ad in self.android_devices]
                if not multithread_func(self.log, tasks):
                    self.log.info("WiFi Calling feature bit is False.")
                    self.log.info("Set call_type to VOLTE_CALL")
                    call_type = VOLTE_CALL
                else:
                    self.log.info("Set call_type to WFC_CALL")
                    call_type = WFC_CALL
                if not self._voice_call(self.android_devices, call_type, end_call=True):
                    self.log.info("voice call failure")
                    tasks = [(self.verify_device_status, (ad, call_type, True,
                        30, True, True)) for ad in self.android_devices]
                    if not multithread_func(self.log, tasks):
                        error_msg = "Verify_device_status fail"
                        fail_count["verify_device_status_fail"] += 1
                        iteration_result = False
                        self.log.error("%s:%s", msg, error_msg)
                self.log.info("%s %s", msg, iteration_result)

            if not iteration_result:
                self._take_bug_report("%s_No_%s" % (self.test_name, i), begin_time)
                if self.sdm_log:
                    start_sdm_loggers(self.log, self.android_devices)
                else:
                    start_qxdm_loggers(self.log, self.android_devices)
        test_result = True
        for failure, count in fail_count.items():
            if count:
                self.log.error("%s: %s %s failures in %s iterations",
                    self.test_name, count, failure, loop)
                test_result = False
        return test_result


    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="111d5810-bc44-4873-b18c-265afa283d34")
    def test_wfc_marginal_area_cellcular_good_stress(self,
        wfc_mode=WFC_MODE_WIFI_PREFERRED):
        """
            b/213907614
            Keeps cellular signal good and adjust WiFi signal slowly

            Args:
                wfc_mode: wfc mode
            Returns:
                True if pass; False if fail
        """
        loop = self.user_params.get("marginal_cycle", 5)
        fail_count = collections.defaultdict(int)
        error_msg = ""

        self.log.info("Start test at cellular and wifi area")
        self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
        self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
        self.check_network()
        tasks = [(phone_setup_iwlan, (self.log, ad, False, wfc_mode,
            self.wifi_ssid)) for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            error_msg = "fail to setup WFC mode to %s" %(wfc_mode)
            fail_count["fail_to_setup_WFC_mode"] += 1
            iteration_result = False
            # self.log.error("%s:%s", msg, error_msg)
            self.log.error(error_msg)

        for i in range(1, loop + 1):
            msg = "Stress Test %s Iteration: <%s> / <%s>" % (
                self.test_name, i, loop)
            begin_time = get_current_epoch_time()
            self.log.info(msg)
            iteration_result = True
            self.log.info("%s loop: %s/%s" %(self.current_test_name, i, loop))

            self.log.info("Move to poor wifi area slowly")
            wifi_power_level = 5
            for x in range (5):
                self.log.info("adjust wifi power level to %s" %wifi_power_level)
                self.adjust_wifi_signal(wifi_power_level)
                time.sleep(5)
                self._check_signal_strength()
                self.log.info("check ims status")
                tasks = [(wait_for_ims_registered, (self.log, ad, ))
                    for ad in self.android_devices]
                if not multithread_func(self.log, tasks):
                    error_msg = "Fail: IMS is not registered"
                    fail_count["IMS_is_not_registered"] += 1
                    iteration_result = False
                    self.log.error("%s:%s", msg, error_msg)
                tasks = [(is_wfc_enabled, (self.log, ad, ))
                    for ad in self.android_devices]
                if not multithread_func(self.log, tasks):
                    self.log.info("WiFi Calling feature bit is False.")
                    self.log.info("Set call_type tp VOLTE_CALL")
                    call_type = VOLTE_CALL
                else:
                    self.log.info("Set call_type to WFC_CALL")
                    call_type = WFC_CALL
                if not self._voice_call(self.android_devices, call_type, end_call=True):
                    error_msg = "Fail: voice call failure"
                    fail_count["voice_call_failure"] += 1
                    iteration_result = False
                    self.log.error("%s:%s", msg, error_msg)
                wifi_power_level += 5
            self.log.info("Move back to wifi area slowly")
            for x in range (5):
                self.log.info("adjust wifi power level to %s" %wifi_power_level)
                self.adjust_wifi_signal(wifi_power_level)
                time.sleep(5)
                self._check_signal_strength()
                for ad in self.android_devices:
                    wifi_rssi = wputils.get_wifi_rssi(ad)
                    ad.log.info("wifi_power_level to %s , wifi_rssi=%s"
                        %(wifi_power_level, wifi_rssi))
                self.log.info("check ims status")
                tasks = [(wait_for_ims_registered, (self.log, ad, ))
                    for ad in self.android_devices]
                if not multithread_func(self.log, tasks):
                    error_msg = "Fail: IMS is not registered"
                    fail_count["IMS_is_not_registered"] += 1
                    iteration_result = False
                    self.log.error("%s:%s", msg, error_msg)
                tasks = [(is_wfc_enabled, (self.log, ad, ))
                    for ad in self.android_devices]
                if not multithread_func(self.log, tasks):
                    self.log.info("WiFi Calling feature bit is False.")
                    self.log.info("Set call_type to VOLTE_CALL")
                    call_type = VOLTE_CALL
                else:
                    self.log.info("Set call_type to WFC_CALL")
                    call_type = WFC_CALL
                if not self._voice_call(self.android_devices, call_type,
                    end_call=True):
                    error_msg = "voice call failure"
                    fail_count["voice_call_failure"] += 1
                    iteration_result = False
                    self.log.error("%s:%s", msg, error_msg)
                wifi_power_level -=5

            self.log.info("%s %s", msg, iteration_result)
            if not iteration_result:
                self._take_bug_report("%s_No_%s" % (self.test_name, i), begin_time)
                if self.sdm_log:
                    start_sdm_loggers(self.log, self.android_devices)
                else:
                    start_qxdm_loggers(self.log, self.android_devices)
        test_result = True
        for failure, count in fail_count.items():
            if count:
                self.log.error("%s: %s %s failures in %s iterations",
                    self.test_name, count, failure, loop)
                test_result = False
        return test_result


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