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

import time
from datetime import datetime, timedelta

from acts import signals
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_ANDROID_STATE_SETTLING
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_data_utils import activate_and_verify_cellular_data
from acts_contrib.test_utils.tel.tel_data_utils import active_file_download_test
from acts_contrib.test_utils.tel.tel_data_utils import deactivate_and_verify_cellular_data
from acts_contrib.test_utils.tel.tel_ims_utils import toggle_wfc
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_wfc_enabled
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_wfc_disabled
from acts_contrib.test_utils.tel.tel_parse_utils import print_nested_dict
from acts_contrib.test_utils.tel.tel_parse_utils import parse_setup_data_call
from acts_contrib.test_utils.tel.tel_parse_utils import parse_deactivate_data_call
from acts_contrib.test_utils.tel.tel_parse_utils import parse_setup_data_call_on_iwlan
from acts_contrib.test_utils.tel.tel_parse_utils import parse_deactivate_data_call_on_iwlan
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_4g_for_subscription
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_iwlan
from acts_contrib.test_utils.tel.tel_subscription_utils import get_default_data_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import set_dds_on_slot_0
from acts_contrib.test_utils.tel.tel_subscription_utils import set_dds_on_slot_1
from acts_contrib.test_utils.tel.tel_subscription_utils import get_slot_index_from_data_sub_id
from acts.utils import get_current_epoch_time
from acts.libs.utils.multithread import multithread_func

CALCULATE_EVERY_N_CYCLES = 10


class TelLiveRilDataKpiTest(TelephonyBaseTest):
    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        self.cycle_cellular_data_cycle = self.user_params.get(
            "cycle_cellular_data_cycle", 1)
        self.cycle_wfc_cycle = self.user_params.get("cycle_wfc_cycle", 1)
        self.dds_switch_test_cycle = self.user_params.get(
            "dds_switch_test_cycle", 1)
        self.http_download_duration = self.user_params.get(
            "http_download_duration", 3600)

    def cycle_cellular_data(self, ad):
        """ Toggle off and then toggle on again cellular data.

        Args:
            ad: Android object

        Returns:
            True if cellular data is cycled successfully. Otherwise False.
        """
        if not deactivate_and_verify_cellular_data(self.log, ad):
            return False

        if not activate_and_verify_cellular_data(self.log, ad):
            return False

        return True

    def cycle_wfc(self, ad):
        """ Toggle off and then toggle on again WFC.

        Args:
            ad: Android object

        Returns:
            True if WFC is cycled successfully. Otherwise False.
        """
        if not toggle_wfc(self.log, ad, new_state=False):
            return False

        if not wait_for_wfc_disabled(self.log, ad):
            return False

        if not toggle_wfc(self.log, ad, new_state=True):
            return False

        if not wait_for_wfc_enabled(self.log, ad):
            return False

        return True

    def switch_dds(self, ad):
        """Switch DDS to the other sub ID.

        Args:
            ad: Android object

        Returns:
            True if DDS is switched successfully. Otherwise False.
        """
        current_dds_slot = get_slot_index_from_data_sub_id(ad)

        if current_dds_slot == 0:
            if set_dds_on_slot_1(ad):
                return True
        else:
            if set_dds_on_slot_0(ad):
                return True

        return False

    @test_tracker_info(uuid="27424b59-efa9-47c3-89b4-4b5415003a58")
    @TelephonyBaseTest.tel_test_wrap
    def test_cycle_cellular_data_4g(self):
        """Cycle cellular data on LTE to measure data call setup time,
            deactivate time and LTE validation time.

        Test steps:
            1. Set up UE on LTE and ensure cellular data is connected.
            2. Cycle cellular data.
            3. Parse logcat to calculate data call setup time, deactivate time
                and LTE validation time.
        """
        ad = self.android_devices[0]

        cycle = self.cycle_cellular_data_cycle

        tasks = [(
            phone_setup_4g_for_subscription,
            (self.log, ad, get_default_data_sub_id(ad)))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)

        cycle_cellular_data_summary = []
        for attempt in range(cycle):
            ad.log.info(
                '======> Cycling cellular data %s/%s <======',
                attempt+1, cycle)
            res = self.cycle_cellular_data(ad)
            cycle_cellular_data_summary.append(res)
            if not res:
                self._take_bug_report(
                    self.test_name, begin_time=get_current_epoch_time())

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or attempt == cycle - 1:
                (
                    res,
                    lst,
                    avg_data_call_setup_time,
                    avg_validation_time_on_lte) = parse_setup_data_call(ad)

                ad.log.info('====== Setup data call list ======')
                print_nested_dict(ad, res)

                ad.log.info('====== Data call setup time list ======')
                for item in lst:
                    print_nested_dict(ad, item)
                    ad.log.info('------------------')

                (
                    res,
                    lst,
                    avg_deactivate_data_call_time) = parse_deactivate_data_call(ad)

                ad.log.info('====== Deactivate data call list ======')
                print_nested_dict(ad, res)

                ad.log.info('====== Data call deactivate time list ======')
                for item in lst:
                    print_nested_dict(ad, item)
                    ad.log.info('------------------')

                ad.log.info(
                    'Average data call setup time on LTE: %.2f sec.',
                    avg_data_call_setup_time)
                ad.log.info(
                    'Average validation time on LTE: %.2f sec.',
                    avg_validation_time_on_lte)
                ad.log.info(
                    'Average deactivate data call time on LTE: %.2f sec.',
                    avg_deactivate_data_call_time)

                try:
                    fail_rate = cycle_cellular_data_summary.count(False)/len(
                            cycle_cellular_data_summary)
                    self.log.info(
                        'Fail rate of cycling cellular data on LTE: %s/%s (%.2f)',
                        cycle_cellular_data_summary.count(False),
                        len(cycle_cellular_data_summary),
                        fail_rate)
                except Exception as e:
                    self.log.error(
                        'Fail rate of cycling cellular data on LTE: ERROR (%s)',
                        e)

    @test_tracker_info(uuid="9f4ab929-176d-4f26-8e14-12bd6c25e80a")
    @TelephonyBaseTest.tel_test_wrap
    def test_cycle_wfc(self):
        """Cycle WFC to measure data call setup time and deactivate time on
            iwlan.

        Test steps:
            1. Set up UE on iwlan and ensure WFC is registered in Wi-Fi-preferred
                mode.
            2. Cycle WFC.
            3. Parse logcat to calculate data call setup time and deactivate time
                on iwlan.
        """
        ad = self.android_devices[0]

        cycle = self.cycle_wfc_cycle

        tasks = [(phone_setup_iwlan, (
            self.log,
            ad,
            False,
            WFC_MODE_WIFI_PREFERRED,
            self.wifi_network_ssid,
            self.wifi_network_pass))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)

        cycle_wfc_summary = []
        for attempt in range(cycle):
            ad.log.info(
                '==================> Cycling WFC %s/%s <==================',
                attempt+1, cycle)
            res = self.cycle_wfc(ad)
            cycle_wfc_summary.append(res)
            if not res:
                self._take_bug_report(
                    self.test_name, begin_time=get_current_epoch_time())

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or attempt == cycle - 1:
                (
                    res,
                    lst,
                    avg_data_call_setup_time) = parse_setup_data_call_on_iwlan(ad)

                ad.log.info('====== Setup data call list ======')
                print_nested_dict(ad, res)

                ad.log.info('====== Data call setup time list ======')
                for item in lst:
                    print_nested_dict(ad, item)
                    ad.log.info('------------------')

                (
                    res,
                    lst,
                    avg_deactivate_data_call_time) = parse_deactivate_data_call_on_iwlan(ad)

                ad.log.info('====== Deactivate data call list ======')
                print_nested_dict(ad, res)

                ad.log.info('====== Data call deactivate time list ======')
                for item in lst:
                    print_nested_dict(ad, item)
                    ad.log.info('------------------')

                ad.log.info(
                    'Average WFC data call setup time: %.2f sec.',
                    avg_data_call_setup_time)
                ad.log.info(
                    'Average WFC deactivate data call time: %.2f sec.',
                    avg_deactivate_data_call_time)

                try:
                    fail_rate = cycle_wfc_summary.count(False)/len(
                        cycle_wfc_summary)
                    self.log.info(
                        'Fail rate of cycling WFC: %s/%s (%.2f)',
                        cycle_wfc_summary.count(False),
                        len(cycle_wfc_summary),
                        fail_rate)
                except Exception as e:
                    self.log.error('Fail rate of cycling WFC: ERROR (%s)', e)

    @test_tracker_info(uuid="77388597-d764-4db3-be6f-656e56dc253a")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch(self):
        """ Switch DDS to measure DDS switch time and LTE validation time.

        Test steps:
            1. Switch DDS.
            2. Parse logcat to calculate DDS switch time and LTE validation time.
        """
        ad = self.android_devices[0]
        cycle = self.dds_switch_test_cycle

        if not getattr(ad, 'dsds', False):
            raise signals.TestSkip("UE is in single mode. Test will be skipped.")

        dds_switch_summary = []
        for attempt in range(cycle):
            self.log.info(
                '======> DDS switch on LTE %s/%s <======',
                attempt+1,
                cycle)
            if self.switch_dds(ad):
                dds_switch_summary.append(True)
            else:
                dds_switch_summary.append(False)
                self._take_bug_report(
                    self.test_name, begin_time=get_current_epoch_time())

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or attempt == cycle - 1:
                (
                    res,
                    lst,
                    avg_data_call_setup_time,
                    avg_validation_time_on_lte) = parse_setup_data_call(
                        ad, dds_switch=True)

                ad.log.info('====== Setup data call list ======')
                print_nested_dict(ad, res)

                ad.log.info('====== Data call setup time list ======')
                for item in lst:
                    print_nested_dict(ad, item)
                    ad.log.info('------------------')

                try:
                    ad.log.info(
                        'Average data call setup time on LTE: %.2f sec.',
                        avg_data_call_setup_time)
                except Exception as e:
                    ad.log.error(
                        'Average data call setup time on LTE: ERROR (%s)', e)

                try:
                    ad.log.info(
                        'Average validation time on LTE: %.2f sec.',
                        avg_validation_time_on_lte)
                except Exception as e:
                    ad.log.error('Average validation tim on LTE: ERROR (%s)', e)

                try:
                    fail_rate = dds_switch_summary.count(False)/len(dds_switch_summary)
                    self.log.info(
                        'Fail rate of cycling cellular data on LTE: %s/%s (%.2f)',
                        dds_switch_summary.count(False),
                        len(dds_switch_summary),
                        fail_rate)
                except Exception as e:
                    self.log.error(
                        'Fail rate of cycling cellular data on LTE: ERROR (%s)',
                        e)

    @test_tracker_info(uuid="ac0b6541-d900-4413-8ccb-839ae998804e")
    @TelephonyBaseTest.tel_test_wrap
    def test_http_download(self, method='sl4a'):
        """HTTP download large file for a long time to ensure there is no issue
            related to the stability.

        Test steps:
            1. HTTP download a large file (e.g., 512MB) for a long time

        Returns:
            False if the download is interrupted. Otherwise True.
        """
        ad = self.android_devices[0]

        duration = self.http_download_duration

        start_time = datetime.now()

        result = True
        while datetime.now() - start_time <= timedelta(seconds=duration):
            if not active_file_download_test(
                self.log, ad, file_name='512MB', method=method):
                result = False
                self._take_bug_report(
                    self.test_name, begin_time=get_current_epoch_time())
        return result