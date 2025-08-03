#!/usr/bin/env python3
#
#   Copyright 2020 - Google
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

import random
import time
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_ANDROID_STATE_SETTLING
from acts_contrib.test_utils.tel.tel_message_utils import sms_send_receive_verify
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte_for_subscription
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_csfb_for_subscription
from acts_contrib.test_utils.tel.tel_subscription_utils import set_message_subid
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_on_same_network_of_host_ad
from acts_contrib.test_utils.tel.tel_test_utils import install_message_apk
from acts.utils import rand_ascii_str
from acts.libs.utils.multithread import multithread_func

class TelLiveStressSmsTest(TelephonyBaseTest):
    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        self.short_sms_cs_4g_cycle = \
            self.user_params.get("short_sms_cs_4g_cycle", 1)
        self.long_sms_cs_4g_cycle = \
            self.user_params.get("long_sms_cs_4g_cycle", 1)
        self.short_sms_ims_cycle = \
            self.user_params.get("short_sms_ims_cycle", 1)
        self.long_sms_ims_cycle = \
            self.user_params.get("long_sms_ims_cycle", 1)

        self.message_util = self.user_params.get("message_apk", None)
        if isinstance(self.message_util, list):
            self.message_util = self.message_util[0]

        if self.message_util:
            ads = self.android_devices
            for ad in ads:
                install_message_apk(ad, self.message_util)

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)

    def sms_test(self, ads, type='short', rat='volte'):
        if type == 'short':
            sms_length = random.randint(50, 180)
            sms_body = rand_ascii_str(sms_length)
        else:
            sms_length = random.randint(800, 1600)
            sms_body = rand_ascii_str(sms_length)

        mo_sub_id, mt_sub_id, _ = get_subid_on_same_network_of_host_ad(
            ads, host_sub_id=None, type="sms")
        set_message_subid(ads[1], mt_sub_id)

        if rat == 'volte':
            phone_setup_func = phone_setup_volte_for_subscription
        else:
            phone_setup_func = phone_setup_csfb_for_subscription

        tasks = [(phone_setup_func, (self.log, ads[0], mo_sub_id)),
                (phone_setup_func, (self.log, ads[1], mt_sub_id))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)

        if not sms_send_receive_verify(self.log, ads[0], ads[1], [sms_body]):
            ads[0].log.warning("SMS of length %s test failed", sms_length)
            return False

        ads[0].log.info("SMS of length %s test succeeded", sms_length)
        time.sleep(random.randint(5, 10))
        return True

    def calculte_fail_rate(self, result_dict={}):
        result_list = []
        for key in result_dict:
            if result_dict[key]:
                result_list.append(True)
            else:
                result_list.append(False)

        fail_rate = result_list.count(False)/len(result_list)
        self.log.info('Test result: %s', result_dict)
        return fail_rate

    @test_tracker_info(uuid="e54d15bf-db9b-4389-bfe8-c9c5dad7ef33")
    @TelephonyBaseTest.tel_test_wrap
    def test_stress_short_sms_ims(self):
        cycle = self.short_sms_ims_cycle
        result_dict = {}
        for attempt in range(cycle):
            self.log.info(
                "============> Stress short SMS over IMS test cycle %s <============",
                attempt+1)
            result = self.sms_test(self.android_devices, 'short', 'volte')
            result_dict['cycle_%s' % str(attempt+1)] = result

        fail_rate = self.calculte_fail_rate(result_dict)
        self.log.info('Fail rate of short SMS over IMS: %s', fail_rate)

    @test_tracker_info(uuid="45858a3c-0c37-4b3d-af4d-60f374d0a2e5")
    @TelephonyBaseTest.tel_test_wrap
    def test_stress_long_sms_ims(self):
        cycle = self.long_sms_ims_cycle
        result_dict = {}
        for attempt in range(cycle):
            self.log.info(
                "============> Stress long SMS over IMS test cycle %s <============",
                attempt+1)
            result = self.sms_test(self.android_devices, 'long', 'volte')
            result_dict['cycle_%s' % str(attempt+1)] = result

        fail_rate = self.calculte_fail_rate(result_dict)
        self.log.info('Fail rate of long SMS over IMS: %s', fail_rate)

    @test_tracker_info(uuid="319aec8f-2e04-4420-9f8a-388c092ddd39")
    @TelephonyBaseTest.tel_test_wrap
    def test_stress_short_sms_cs_4g(self):
        cycle = self.short_sms_cs_4g_cycle
        result_dict = {}
        for attempt in range(cycle):
            self.log.info(
                "============> Stress short CS SMS on LTE test cycle %s <============",
                attempt+1)
            result = self.sms_test(self.android_devices, 'short', 'csfb')
            result_dict['cycle_%s' % str(attempt+1)] = result

        fail_rate = self.calculte_fail_rate(result_dict)
        self.log.info('Fail rate of short CS SMS on LTE: %s', fail_rate)

    @test_tracker_info(uuid="aa8a2e7a-1ca7-4406-801d-f8e1923d0695")
    @TelephonyBaseTest.tel_test_wrap
    def test_stress_long_sms_cs_4g(self):
        cycle = self.long_sms_cs_4g_cycle
        result_dict = {}
        for attempt in range(cycle):
            self.log.info(
                "============> Stress long CS SMS on LTE test cycle %s <============",
                attempt+1)
            result = self.sms_test(self.android_devices, 'long', 'csfb')
            result_dict['cycle_%s' % str(attempt+1)] = result

        fail_rate = self.calculte_fail_rate(result_dict)
        self.log.info('Fail rate of long CS SMS on LTE: %s', fail_rate)