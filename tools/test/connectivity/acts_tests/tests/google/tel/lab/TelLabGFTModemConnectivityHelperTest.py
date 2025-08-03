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
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.GFTInOutBaseTest import GFTInOutBaseTest
from acts.controllers.android_device import DEFAULT_QXDM_LOG_PATH
from acts.controllers.android_device import DEFAULT_SDM_LOG_PATH
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.tel.tel_voice_utils import initiate_call
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_volte

from acts.test_decorators import test_tracker_info
from acts.logger import epoch_to_log_line_timestamp
from acts.utils import get_current_epoch_time
from acts.libs.utils.multithread import multithread_func

from acts_contrib.test_utils.tel.gft_inout_defines import NO_SERVICE_POWER_LEVEL
from acts_contrib.test_utils.tel.gft_inout_defines import IN_SERVICE_POWER_LEVEL
from acts_contrib.test_utils.tel.gft_inout_defines import NO_SERVICE_AREA
from acts_contrib.test_utils.tel.gft_inout_defines import IN_SERVICE_AREA
from acts_contrib.test_utils.tel.gft_inout_defines import WIFI_AREA
from acts_contrib.test_utils.tel.gft_inout_defines import NO_WIFI_AREA
from acts_contrib.test_utils.tel.gft_inout_defines import NO_SERVICE_TIME
from acts_contrib.test_utils.tel.gft_inout_defines import WAIT_FOR_SERVICE_TIME


class TelLabGFTModemConnectivityHelperTest(GFTInOutBaseTest):
    def __init__(self, controllers):
        GFTInOutBaseTest.__init__(self, controllers)

    def setup_test(self):
        self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
        self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
        GFTInOutBaseTest.setup_test(self)
        self.check_network()

    """Tests"""

    @test_tracker_info(uuid="c602e556-8273-4c75-b8fa-4d51ba514654")
    @TelephonyBaseTest.tel_test_wrap
    def test_in_out_no_service(self):
        """In/Out service - Stationary idle mode - 1 min
            UE is in idle
            Move UE from coverage area to no service area and UE shows no service
            Wait for 1 min, then re-enter coverage area

            Returns:
                True if pass; False if fail.
        """
        self.adjust_cellular_signal(NO_SERVICE_POWER_LEVEL)
        self.adjust_wifi_signal(NO_SERVICE_POWER_LEVEL)
        time.sleep(NO_SERVICE_TIME)
        self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
        self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)
        time.sleep(WAIT_FOR_SERVICE_TIME)
        if not self.check_network():
            return False
        return True

    @test_tracker_info(uuid="e58c7347-a930-4a3a-a740-a6d1cfb19ca0")
    @TelephonyBaseTest.tel_test_wrap
    def test_volte_call_cellular(self, loop=1):
        """ UE is in call
            Move UE from coverage area to no service area and UE shows no service
            Wait for 1 min, then re-enter coverage area

            Args:
                loop: cycle
            Returns:
                True if pass; False if fail.
        """
        test_result = True
        for x in range(loop):
            self.log.info("%s loop: %s/%s" %
                          (self.current_test_name, x + 1, loop))
            self.adjust_cellular_signal(IN_SERVICE_POWER_LEVEL)
            self.adjust_wifi_signal(IN_SERVICE_POWER_LEVEL)

            # Make a MO VoLTE call in service area.
            tasks = [(self._initiate_call, (ad, ))
                     for ad in self.android_devices]
            if not multithread_func(self.log, tasks):
                return False
            tasks = [(is_phone_in_call_volte, (
                ad.log,
                ad,
            )) for ad in self.android_devices]
            test_result = multithread_func(self.log, tasks)

            # Move to poor service -> no service area
            self.adjust_atten_slowly(10, NO_SERVICE_AREA)
            time.sleep(NO_SERVICE_TIME)
            # check call status
            tasks = [(is_phone_in_call_volte, (
                ad.log,
                ad,
            )) for ad in self.android_devices]
            test_result = multithread_func(self.log, tasks)
            for ad in self.android_devices:
                telecom_state = ad.droid.telecomGetCallState()
                ad.log.info("telecom_state %s" % (telecom_state))
            # Move back to service area
            self.adjust_atten_slowly(10, IN_SERVICE_AREA)
            time.sleep(WAIT_FOR_SERVICE_TIME)
        return test_result

    def _initiate_call(self, ad):
        """ initiate a voice call

        Args:
            ad: caller

        Returns:
            return True if call initiate successfully
        """
        mt = ad.mt_phone_number
        ad.log.info("set mt number to %s" % (mt))
        ad.log.info("check network status before initiate call")
        self.check_network()
        ad.log.info("_initiate_call to %s" % (mt))
        if not initiate_call(self.log, ad, mt):
            return False
        return True
