#!/usr/bin/env python3
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

import random
import time

from acts.libs.utils.multithread import multithread_func
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_ANDROID_STATE_SETTLING
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_data_utils import wait_for_cell_data_connection
from acts_contrib.test_utils.tel.tel_message_utils import mms_send_receive_verify
from acts_contrib.test_utils.tel.tel_message_utils import sms_send_receive_verify
from acts_contrib.test_utils.tel.tel_parse_utils import parse_mms
from acts_contrib.test_utils.tel.tel_parse_utils import parse_sms_delivery_time
from acts_contrib.test_utils.tel.tel_parse_utils import print_nested_dict
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_csfb_for_subscription
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte_for_subscription
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_iwlan_for_subscription
from acts_contrib.test_utils.tel.tel_subscription_utils import set_message_subid
from acts_contrib.test_utils.tel.tel_subscription_utils import get_subid_on_same_network_of_host_ad
from acts.utils import get_current_epoch_time
from acts.utils import rand_ascii_str

CALCULATE_EVERY_N_CYCLES = 10
MAX_FAIL_COUNT = 10


class TelLiveRilMessageKpiTest(TelephonyBaseTest):
    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        self.sms_4g_over_sgs_test_cycle = self.user_params.get(
            'sms_4g_over_sgs_test_cycle', 1)
        self.sms_4g_over_ims_test_cycle = self.user_params.get(
            'sms_4g_over_ims_test_cycle', 1)
        self.sms_iwlan_test_cycle = self.user_params.get(
            'sms_iwlan_test_cycle', 1)
        self.mms_4g_test_cycle = self.user_params.get('mms_4g_test_cycle', 1)
        self.mms_iwlan_test_cycle = self.user_params.get(
            'mms_iwlan_test_cycle', 1)

    def sms_test(self, ads):
        """Send and receive a short SMS with random length and content between
        two UEs.

        Args:
            ads: list containing Android objects

        Returns:
            True if both sending and receiving are successful. Otherwise False.
        """
        msg_length = random.randint(5, 160)
        msg_body = rand_ascii_str(msg_length)

        if not sms_send_receive_verify(self.log, ads[0], ads[1], [msg_body]):
            ads[0].log.warning('SMS of length %s test failed', msg_length)
            return False
        else:
            ads[0].log.info('SMS of length %s test succeeded', msg_length)
        return True

    def mms_test(self, ads, expected_result=True):
        """Send and receive a MMS with random text length and content between
        two UEs.

        Args:
            ads: list containing Android objects
            expected_result: True to expect successful MMS sending and reception.
                Otherwise False.

        Returns:
            True if both sending and reception are successful. Otherwise False.
        """
        message_length = random.randint(5, 160)
        message_array = [('Test Message', rand_ascii_str(message_length), None)]
        if not mms_send_receive_verify(
                self.log,
                ads[0],
                ads[1],
                message_array,
                expected_result=expected_result):
            self.log.warning('MMS of body length %s test failed', message_length)
            return False
        else:
            self.log.info('MMS of body length %s test succeeded', message_length)
        self.log.info('MMS test of body lengths %s succeeded', message_length)
        return True


    def _test_sms_4g(self, over_iwlan=False, over_ims=False):
        """ Send/receive SMS over SGs/IMS to measure MO SMS setup time and SMS
        delivery time.

        Test steps:
            1. Enable VoLTE when over IMS. Otherwise disable VoLTE.
            2. Send a SMS from MO UE and receive it by MT UE.
            3. Parse logcat of both MO and MT UEs to calculate MO SMS setup time
                and SMS delivery time.

        Args:
            over_iwlan: True for over Wi-Fi and False for over cellular network
            over_ims: True for over IMS and False for over SGs

        Returns:
            True if both sending and reception are successful. Otherwise False.
        """
        ad_mo = self.android_devices[0]
        ad_mt = self.android_devices[1]

        mo_sub_id, mt_sub_id, _ = get_subid_on_same_network_of_host_ad(
            [ad_mo, ad_mt],
            host_sub_id=None,
            type="sms")
        set_message_subid(ad_mt, mt_sub_id)

        cycle = self.sms_4g_over_sgs_test_cycle
        phone_setup_func = phone_setup_csfb_for_subscription
        mo_param = (self.log, ad_mo, mo_sub_id)
        mt_param = (self.log, ad_mt, mt_sub_id)
        wording = "SGs"
        parsing = '4g'
        if over_ims:
            cycle = self.sms_4g_over_ims_test_cycle
            phone_setup_func = phone_setup_volte_for_subscription
            wording = "IMS"
            parsing = 'iwlan'

        if over_iwlan:
            cycle = self.sms_iwlan_test_cycle
            phone_setup_func = phone_setup_iwlan_for_subscription

            mo_param = (
                self.log,
                ad_mo,
                mo_sub_id,
                True,
                WFC_MODE_CELLULAR_PREFERRED,
                self.wifi_network_ssid,
                self.wifi_network_pass)

            mt_param = (
                self.log,
                ad_mt,
                mt_sub_id,
                True,
                WFC_MODE_CELLULAR_PREFERRED,
                self.wifi_network_ssid,
                self.wifi_network_pass)

            wording = 'iwlan'
            parsing = 'iwlan'

        tasks = [
            (phone_setup_func, mo_param),
            (phone_setup_func, mt_param)]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)

        sms_test_summary = []
        result = True
        continuous_fail = 0
        for attempt in range(cycle):
            self.log.info(
                '======> MO/MT SMS over %s %s/%s <======',
                wording,
                attempt+1,
                cycle)
            res = self.sms_test([ad_mo, ad_mt])
            sms_test_summary.append(res)

            if not res:
                continuous_fail += 1
                if not multithread_func(self.log, tasks):
                    self.log.error("Phone Failed to Set Up Properly.")
                    result = False
                self._take_bug_report(
                    self.test_name, begin_time=get_current_epoch_time())
            else:
                time.sleep(random.randint(3,10))

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or (
                attempt == cycle - 1) or continuous_fail >= MAX_FAIL_COUNT:
                parse_sms_delivery_time(self.log, ad_mo, ad_mt, rat=parsing)
                try:
                    sms_test_fail_rate = sms_test_summary.count(
                        False)/len(sms_test_summary)
                    self.log.info(
                        'Fail rate of SMS test over %s: %s/%s (%.2f)',
                        wording,
                        sms_test_summary.count(False),
                        len(sms_test_summary),
                        sms_test_fail_rate)
                except Exception as e:
                    self.log.error(
                        'Fail rate of SMS test over %s: ERROR (%s)',
                        wording,
                        e)

            if continuous_fail >= MAX_FAIL_COUNT:
                self.log.error(
                    'Failed more than %s times in succession. Test is terminated '
                    'forcedly.',
                    MAX_FAIL_COUNT)
                break

        return result


    @test_tracker_info(uuid="13d1a53b-66be-4ac1-b5ee-dfe4c5e4e4e1")
    @TelephonyBaseTest.tel_test_wrap
    def test_sms_4g_over_sgs(self):
        """ Send/receive SMS over SGs to measure MO SMS setup time and SMS
        delivery time.

        Test steps:
            1. Disable VoLTE.
            2. Send a SMS from MO UE and receive it by MT UE.
            3. Parse logcat of both MO and MT UEs to calculate MO SMS setup time
                and SMS delivery time.
        """
        return self._test_sms_4g()


    @test_tracker_info(uuid="293e2955-b38b-4329-b686-fb31d9e46868")
    @TelephonyBaseTest.tel_test_wrap
    def test_sms_4g_over_ims(self):
        """ Send/receive SMS over IMS to measure MO SMS setup time and SMS
        delivery time.

        Test steps:
            1. Enable VoLTE.
            2. Send a SMS from MO UE and receive it by MT UE.
            3. Parse logcat of both MO and MT UEs to calculate MO SMS setup time
                and SMS delivery time.
        """
        return self._test_sms_4g(over_ims=True)


    @test_tracker_info(uuid="862fec2d-8e23-482e-b45c-a42cad134022")
    @TelephonyBaseTest.tel_test_wrap
    def test_sms_iwlan(self):
        """ Send/receive SMS on iwlan to measure MO SMS setup time and SMS
        delivery time.

        Test steps:
            1. Send a SMS from MO UE and receive it by MT UE.
            2. Parse logcat of both MO and MT UEs to calculate MO SMS setup time
                and SMS delivery time.
        """
        return self._test_sms_4g(over_iwlan=True, over_ims=True)


    def _test_mms_4g(self, over_iwlan=False):
        """ Send/receive MMS on LTE to measure MO and MT MMS setup time

        Test steps:
            1. Enable VoLTE when over Wi-Fi (iwlan). Otherwise disable VoLTE.
            2. Send a MMS from MO UE and receive it by MT UE.
            3. Parse logcat of both MO and MT UEs to calculate MO and MT MMS
                setup time.

        Args:
            over_iwlan: True for over Wi-Fi and False for over cellular network

        Returns:
            True if both sending and reception are successful. Otherwise False.
        """
        ad_mo = self.android_devices[0]
        ad_mt = self.android_devices[1]

        mo_sub_id, mt_sub_id, _ = get_subid_on_same_network_of_host_ad(
            [ad_mo, ad_mt],
            host_sub_id=None,
            type="sms")
        set_message_subid(ad_mt, mt_sub_id)

        cycle = self.mms_4g_test_cycle
        phone_setup_func = phone_setup_csfb_for_subscription
        mo_param = (self.log, ad_mo, mo_sub_id)
        mt_param = (self.log, ad_mt, mt_sub_id)
        wording = "LTE"
        if over_iwlan:
            cycle = self.mms_iwlan_test_cycle
            phone_setup_func = phone_setup_iwlan_for_subscription
            wording = "iwlan"

            mo_param = (
                self.log,
                ad_mo,
                mo_sub_id,
                True,
                WFC_MODE_CELLULAR_PREFERRED,
                self.wifi_network_ssid,
                self.wifi_network_pass)

            mt_param = (
                self.log,
                ad_mt,
                mt_sub_id,
                True,
                WFC_MODE_CELLULAR_PREFERRED,
                self.wifi_network_ssid,
                self.wifi_network_pass)

        phone_setup_tasks = [
            (phone_setup_func, mo_param),
            (phone_setup_func, mt_param)]
        if not multithread_func(self.log, phone_setup_tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        if not over_iwlan:
            wait_for_cell_data_connection_tasks = [
                (wait_for_cell_data_connection, (self.log, ad_mo, True)),
                (wait_for_cell_data_connection, (self.log, ad_mt, True))]
            if not multithread_func(self.log, wait_for_cell_data_connection_tasks):
                return False

        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)

        mms_test_summary = []
        result = True
        continuous_fail = 0
        for attempt in range(cycle):
            self.log.info(
                '==================> MO/MT MMS on %s %s/%s <==================',
                wording,
                attempt+1,
                cycle)
            res = self.mms_test([ad_mo, ad_mt])
            mms_test_summary.append(res)

            if not res:
                continuous_fail += 1
                if not multithread_func(self.log, phone_setup_tasks):
                    self.log.error("Phone Failed to Set Up Properly.")
                    result = False
                    break

                if not over_iwlan:
                    if not multithread_func(
                        self.log, wait_for_cell_data_connection_tasks):
                        result = False
                        break
                self._take_bug_report(
                    self.test_name, begin_time=get_current_epoch_time())
            else:
                time.sleep(random.randint(3,10))

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or (
                attempt == cycle - 1) or continuous_fail >= MAX_FAIL_COUNT:
                (
                    mo_res,
                    mo_avg_setup_time,
                    mt_res, mt_avg_setup_time) = parse_mms(ad_mo, ad_mt)

                ad_mo.log.info('================== Sent MMS ==================')
                print_nested_dict(ad_mo, mo_res)
                ad_mt.log.info('================== Received MMS ==================')
                print_nested_dict(ad_mt, mt_res)

                try:
                    ad_mo.log.info(
                        'Average setup time of MO MMS on %s: %.2f sec.',
                        wording, mo_avg_setup_time)
                except Exception as e:
                    ad_mo.log.error(
                        'Average setup time of MO MMS on %s: ERROR (%s)',
                        wording, e)

                try:
                    ad_mt.log.info(
                        'Average setup time of MT MMS on %s: %.2f sec.',
                        wording, mt_avg_setup_time)
                except Exception as e:
                    ad_mt.log.error(
                        'Average setup time of MT MMS on %s: ERROR (%s)',
                        wording, e)

                try:
                    mms_test_fail_rate = mms_test_summary.count(
                        False)/len(mms_test_summary)
                    self.log.info(
                        'Fail rate of MMS test on LTE: %s/%s (%.2f)',
                        mms_test_summary.count(False),
                        len(mms_test_summary),
                        mms_test_fail_rate)
                except Exception as e:
                    self.log.error(
                        'Fail rate of MMS test on %s: ERROR (%s)', wording, e)

            if continuous_fail >= MAX_FAIL_COUNT:
                self.log.error(
                    'Failed more than %s times in succession. Test is terminated '
                    'forcedly.',
                    MAX_FAIL_COUNT)
                break

        return result


    @test_tracker_info(uuid="33d11da8-71f1-40d7-8fc7-86fdc83ce266")
    @TelephonyBaseTest.tel_test_wrap
    def test_mms_4g(self):
        """ Send/receive MMS on LTE to measure MO and MT MMS setup time

        Test steps:
            1. Send a MMS from MO UE and receive it by MT UE.
            2. Parse logcat of both MO and MT UEs to calculate MO and MT MMS
                setup time.
        """
        return self._test_mms_4g()


    @test_tracker_info(uuid="b8a8affa-6559-41d8-9de7-f74406da9ed5")
    @TelephonyBaseTest.tel_test_wrap
    def test_mms_iwlan(self):
        """ Send/receive MMS on iwlan to measure MO and MT MMS setup time

        Test steps:
            1. Send a MMS from MO UE and receive it by MT UE.
            2. Parse logcat of both MO and MT UEs to calculate MO and MT MMS
                setup time.
        """
        return self._test_mms_4g(over_iwlan=True)