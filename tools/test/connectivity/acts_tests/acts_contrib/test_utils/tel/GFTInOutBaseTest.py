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
from acts import asserts
from acts.test_decorators import test_info
from acts.test_decorators import test_tracker_info
from acts.logger import epoch_to_log_line_timestamp
from acts.utils import get_current_epoch_time
from acts.libs.utils.multithread import multithread_func
from acts.base_test import BaseTestClass
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_logging_utils import get_screen_shot_log
from acts_contrib.test_utils.tel.tel_logging_utils import get_screen_shot_logs
from acts_contrib.test_utils.tel.tel_logging_utils import log_screen_shot
from acts_contrib.test_utils.tel.tel_test_utils import get_service_state_by_adb
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call
from acts_contrib.test_utils.tel.gft_inout_utils import mo_voice_call
from acts_contrib.test_utils.tel.gft_inout_utils import get_voice_call_type
from acts_contrib.test_utils.tel.gft_inout_utils import verify_data_connection
from acts_contrib.test_utils.tel.gft_inout_utils import check_network_service
from acts_contrib.test_utils.tel.gft_inout_defines import NO_SERVICE_POWER_LEVEL
from acts_contrib.test_utils.tel.gft_inout_defines import IN_SERVICE_POWER_LEVEL
from acts_contrib.test_utils.tel.gft_inout_defines import NO_SERVICE_AREA
from acts_contrib.test_utils.tel.gft_inout_defines import IN_SERVICE_AREA
from acts_contrib.test_utils.tel.gft_inout_defines import WIFI_AREA
from acts_contrib.test_utils.tel.gft_inout_defines import NO_WIFI_AREA
from acts_contrib.test_utils.tel.gft_inout_defines import NO_SERVICE_TIME
from acts_contrib.test_utils.tel.gft_inout_defines import WAIT_FOR_SERVICE_TIME
from acts_contrib.test_utils.tel.loggers.telephony_metric_logger import TelephonyMetricLogger

CELLULAR_PORT = 0
WIFI_PORT = 1
UNKNOWN = "UNKNOWN"


class GFTInOutBaseTest(TelephonyBaseTest):
    def __init__(self, controllers):
        TelephonyBaseTest.__init__(self, controllers)
        self.my_error_msg = ""
        self.tel_logger = TelephonyMetricLogger.for_test_case()

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)
        self.user_params["telephony_auto_rerun"] = 0
        self.my_error_msg = ""

    def teardown_test(self):
        TelephonyBaseTest.teardown_test(self)
        begin_time = get_current_epoch_time()
        for ad in self.android_devices:
            hangup_call(self.log, ad)
        get_screen_shot_logs(self.android_devices)
        for ad in self.android_devices:
            ad.adb.shell("rm -rf /sdcard/Pictures/screenvideo_*", ignore_status=True)

    def check_network(self):
        """check service state of network
        Returns:
            return True if android_devices are in service else return false
        """
        for ad in self.android_devices:
            network_type_voice = ad.droid.telephonyGetCurrentVoiceNetworkType()
            network_type_data = ad.droid.telephonyGetCurrentDataNetworkType()
            data_state = ad.droid.telephonyGetDataConnectionState()
            service_state = get_service_state_by_adb(ad.log,ad)
            wifi_info = ad.droid.wifiGetConnectionInfo()
            sim_state = ad.droid.telephonyGetSimState()
            if ad.droid.wifiCheckState():
                if wifi_info["supplicant_state"] == "completed":
                    ad.log.info("Wifi is connected=%s" %(wifi_info["SSID"]))
                else:
                    ad.log.info("wifi_info =%s" %(wifi_info))
            else:
                ad.log.info("Wifi state is down.")
            ad.log.info("data_state=%s" %(data_state))
            ad.log.info("sim_state=%s" %(sim_state))
            ad.log.info("networkType_voice=%s" %(network_type_voice))
            ad.log.info("network_type_data=%s" %(network_type_data))
            ad.log.info("service_state=%s" %(service_state))
            if network_type_voice == UNKNOWN:
                return False
        return True

    def adjust_cellular_signal(self, power, adjust_gradually=False):
        """Sets the attenuation value of cellular port
        Args:
             power: power level for attenuator to be set
        Returns:
            return True if ceullular port is set
        """
        if self.user_params.get("Attenuator"):
            if adjust_gradually:
                self.log.info("adjust cellular signal gradually to mini-circuits to %s" %(power))
                self.adjust_atten_slowly(10, NO_SERVICE_AREA)
            else:
                self.log.info("adjust cellular signal set mini-circuits to %s" %(power))
                self.attenuators[CELLULAR_PORT].set_atten(power)
            return True
        else:
            self.log.info("Attenuator is set to False in config file")
            return False

    def adjust_wifi_signal(self, power, adjust_gradually=False):
        """Sets the attenuation value of wifi port
        Args:
             power: power level for attenuator to be set
        Returns:
            return True if wifi port is set
        """
        if self.user_params.get("Attenuator"):
            if adjust_gradually:
                self.log.info("adjust wifi signal set mini-circuits to %s" %(power))
                self.adjust_atten_slowly(10, NO_WIFI_AREA)
            else:
                self.log.info("adjust wifi signal and set mini-circuits to %s" %(power))
                self.attenuators[WIFI_PORT].set_atten(power)
                self.attenuators[2].set_atten(power)
                self.attenuators[3].set_atten(power)
        else:
            self.log.info("Attenuator is set to False in config file")
            return False
        return True

    def adjust_attens(self, power):
        """Sets the attenuation value of all attenuators in the group
        Args:
             power: power level for attenuator to be set
        Returns:
            return True if all ports are set
        """
        if self.user_params.get("Attenuator"):
            self.log.info("set attenuator ports to %s" %(power))
            self.attenuators[0].set_atten(power)
            self.attenuators[1].set_atten(power)
            self.attenuators[2].set_atten(power)
            self.attenuators[3].set_atten(power)
        else:
            self.log.info("Attenuator is set to False in config file")
            return False
        return True

    def adjust_atten(self, port , power):
        """Sets the attenuation value of given port
        Args:
            port: port of attenuator
            power: power level for attenuator to be set
        Returns:
            return True if given port is set
        """
        if self.user_params.get("Attenuator"):
            self.log.info("set attenuator port=%s to %s" %(port, power))
            self.attenuators[port].set_atten(power)
        else:
            self.log.info("Attenuator is set to False in config file")
            return False
        return True

    def adjust_atten_slowly(self, adjust_level, move_to, step=9 , step_time=5):
        """adjust attenuator slowly
        Args:
            adjust_level: adjust power level for each cycle
            move_to: NO_SERVICE_AREA, IN_SERVICE_AREA , WIFI_AREA, NO_WIFI_AREA
            step: adjust attenuator how many time
            step_time: wait for how many sec for each loop
        Returns:
            return True if given port is set
        """
        if move_to == NO_SERVICE_AREA:
            self.log.info("Move UE from coverage area to no/poor service area")
            power_level = IN_SERVICE_POWER_LEVEL
            for x in range (step):
                power_level += adjust_level
                self.log.info("adjust power level = %s" %power_level)
                self.adjust_cellular_signal(power_level)
                time.sleep(step_time)
                self.log.info("wait device to be in no service state")
                self.check_network()
        elif move_to == IN_SERVICE_AREA:
            # Move UE to service area
            self.log.info("Move UE to service area")
            power_level = NO_SERVICE_POWER_LEVEL
            for x in range (step):
                power_level -= adjust_level
                self.log.info("adjust power level = %s" %power_level)
                self.adjust_cellular_signal(power_level)
                time.sleep(step_time)
                self.log.info("wait device to be in service state")
                self.check_network()
        elif move_to == WIFI_AREA:
            self.log.info("Move UE to good wifi area")
            power_level = IN_SERVICE_POWER_LEVEL
            for x in range (step):
                power_level += adjust_level
                self.log.info("adjust power level = %s" %power_level)
                self.adjust_wifi_signal(power_level)
                time.sleep(step_time)
                self.log.info("wait device to connected to wifi")
                self.check_network()
        elif move_to == NO_WIFI_AREA:
            self.log.info("Move UE to poor/no wifi area")
            power_level = NO_SERVICE_POWER_LEVEL
            for x in range (step):
                power_level -= adjust_level
                self.log.info("adjust power level = %s" %power_level)
                self.adjust_wifi_signal(power_level)
                time.sleep(step_time)
                self.log.info("wait device to disconnected from wifi")
                self.check_network()
        return True

    def verify_device_status(self, ad, call_type=None, end_call=True,
        talk_time=30, verify_data=True, verify_voice=True, data_retries=2, voice_retries=2):
        """verfiy device status includes network service, data connection and voice call
        Args:
            ad: android device
            call_type: WFC call, VOLTE call. CSFB call, voice call
            end_call: hangup call after voice call flag
            talk_time: in call duration in sec
            verify_data: flag to check data connection
            verify_voice: flag to check voice
            data_retries: retry times for data verification
            voice_retris:retry times for voice call
        Returns:
            return True if pass
        """
        tasks = [(check_network_service, (ad, )) for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            log_screen_shot(ad, "check_network_service_fail")
            ad.log.info("check_network_service fail")
            return False
        else:
            self.log.info("check_network_service pass")
        if verify_data:
            tasks = [(verify_data_connection, (ad, data_retries ))
                for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                log_screen_shot(ad, "verify_data_connection_fail")
                ad.log.info("verify_data_connection_fail")
                return False
            else:
                self.log.info("verify_data_connection pass")
        if verify_voice:
            if call_type:
                tasks = [(mo_voice_call, (self.log, ad, call_type, end_call,
                    talk_time, voice_retries)) for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                log_screen_shot(ad, "verify_voice_call_fail")
                ad.log.info("verify_voice_call_fail")
                return False
            else:
                self.log.info("verify_voice_call pass")
                return True
        return True


    def _on_failure(self, error_msg="", assert_on_fail=True, test_result=False):
        """ operation on fail

        Args:
            error_msg: error message to be written to log

        """
        if assert_on_fail:
            asserts.assert_true(False, "assert_on_fail: %s."
                %(error_msg),extras={"failure_cause": error_msg})
        for ad in self.android_devices:
            log_screen_shot(ad, error_msg)
        self.log.info(error_msg)

