#!/usr/bin/env python3.4
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
"""
    Test Script for CellBroadcast initialization Test
"""

import time

from acts.logger import epoch_to_log_line_timestamp
from acts.keys import Config
from acts.base_test import BaseTestClass
from acts.test_decorators import test_tracker_info
from acts.utils import load_config
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_test_utils import reboot_device
from acts_contrib.test_utils.tel.tel_test_utils import get_device_epoch_time


class CellBroadcastInitializationTest(TelephonyBaseTest):
    def setup_test(self):
        TelephonyBaseTest.setup_class()
        self.number_of_devices = 1
        self.cbr_init_iteration = self.user_params.get("cbr_init_iteration",
                                                       50)

    def teardown_class(self):
        TelephonyBaseTest.teardown_class(self)

    def _get_current_time_in_secs(self, ad):
        try:
            c_time = get_device_epoch_time(ad)
            c_time = epoch_to_log_line_timestamp(c_time).split()[1].split(
                '.')[0]
            return self._convert_formatted_time_to_secs(c_time)
        except Exception as e:
            ad.log.error(e)

    def _convert_formatted_time_to_secs(self, formatted_time):
        try:
            time_list = formatted_time.split(":")
            return int(time_list[0]) * 3600 + int(time_list[1]) * 60 + int(
                time_list[2])
        except Exception as e:
            self.log.error(e)

    def _verify_channel_config_4400(self, ad):
        #TODO add all channel checks as constants in tel_defines
        channel_4400__log = 'SmsBroadcastConfigInfo: Id \\[4400'
        return ad.search_logcat(channel_4400__log)

    @test_tracker_info(uuid="30f30fa4-f57a-40bd-a37a-141a8efb5a04")
    @TelephonyBaseTest.tel_test_wrap
    def test_reboot_stress(self):
        """ Verifies channel 4400 is set correctly after device boot up
        only applicable to US carriers
        after every boot up, search logcat to verify channel 4400 is set
        default iterations is 50
        config param : cbr_init_iteration

        """
        ad = self.android_devices[0]

        current_cbr_version = ad.get_apk_version(
            'com.google.android.cellbroadcast')
        ad.log.info("Current cbr apk version is %s.", current_cbr_version)

        failure_count = 0
        begin_time = self._get_current_time_in_secs(ad)
        for iteration in range(1, self.cbr_init_iteration + 1):
            msg = "Stress CBR reboot initialization test Iteration: <%s>/<%s>" % (
                iteration, self.cbr_init_iteration)
            self.log.info(msg)
            ad.reboot()
            ad.wait_for_boot_completion()
            self.log.info("Rebooted")
            #TODO make sleep time a constant in tel_defines WAIT_TIME_CBR_INIT_AFTER_REBOOT
            time.sleep(40)
            if not self._verify_channel_config_4400(ad):
                failure_count += 1
                self.log.error('Iteration failed at %d ' % iteration)
        end_time = self._get_current_time_in_secs(ad)
        self.log.debug('Test completed from %s to %s' % (begin_time, end_time))
        result = True
        if failure_count > 0:
            result = False
            self.log.error(
                'CBR reboot init stress test: <%s> failures in %s iterations',
                failure_count, self.cbr_init_iteration)
        return result
