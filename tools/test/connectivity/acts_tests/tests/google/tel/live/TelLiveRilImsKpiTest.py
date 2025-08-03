#!/usr/bin/env python3
#
#   Copyright 2021 - Google
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
from datetime import datetime

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.loggers.telephony_metric_logger import TelephonyMetricLogger
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_data_utils import airplane_mode_test
from acts_contrib.test_utils.tel.tel_data_utils import reboot_test
from acts_contrib.test_utils.tel.tel_data_utils import wait_for_network_service
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_CELLULAR_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_WIFI_CONNECTION
from acts_contrib.test_utils.tel.tel_ims_utils import set_wfc_mode
from acts_contrib.test_utils.tel.tel_logging_utils import start_pixellogger_always_on_logging
from acts_contrib.test_utils.tel.tel_logging_utils import wait_for_log
from acts_contrib.test_utils.tel.tel_parse_utils import print_nested_dict
from acts_contrib.test_utils.tel.tel_parse_utils import parse_ims_reg
from acts_contrib.test_utils.tel.tel_parse_utils import ON_IMS_MM_TEL_CONNECTED_4G_SLOT0
from acts_contrib.test_utils.tel.tel_parse_utils import ON_IMS_MM_TEL_CONNECTED_4G_SLOT1
from acts_contrib.test_utils.tel.tel_parse_utils import ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT0
from acts_contrib.test_utils.tel.tel_parse_utils import ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT1
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_iwlan
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_iwlan_for_subscription
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte_for_subscription
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_idle_volte
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_idle_iwlan
from acts_contrib.test_utils.tel.tel_subscription_utils import get_slot_index_from_voice_sub_id
from acts_contrib.test_utils.tel.tel_subscription_utils import get_all_sub_id
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_volte
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_iwlan
from acts_contrib.test_utils.tel.tel_voice_utils import two_phone_call_short_seq
from acts_contrib.test_utils.tel.tel_wifi_utils import check_is_wifi_connected
from acts.utils import get_current_epoch_time

SETUP_PHONE_FAIL = 'SETUP_PHONE_FAIL'
VERIFY_NETWORK_FAIL = 'VERIFY_NETWORK_FAIL'
VERIFY_INTERNET_FAIL = 'VERIFY_INTERNET_FAIL'
TOGGLE_OFF_APM_FAIL = 'TOGGLE_OFF_APM_FAIL'

CALCULATE_EVERY_N_CYCLES = 10


def test_result(result_list, cycle, min_fail=0, failrate=0):
    failure_count = len(list(filter(lambda x: (x != True), result_list)))
    if failure_count >= min_fail:
        if failure_count >= cycle * failrate:
            return False
    return True

def wait_for_wifi_disconnected(ad, wifi_ssid):
    """Wait until Wifi is disconnected.

    Args:
        ad: Android object
        wifi_ssid: to specify the Wifi AP which should be disconnected.

    Returns:
        True if Wifi is disconnected before time-out. Otherwise False.
    """
    wait_time = 0
    while wait_time < MAX_WAIT_TIME_WIFI_CONNECTION:
        if check_is_wifi_connected(ad.log, ad, wifi_ssid):
            ad.droid.wifiToggleState(False)
            time.sleep(3)
            wait_time = wait_time + 3
        else:
            ad.log.info('Wifi is disconnected.')
            return True

    if check_is_wifi_connected(ad.log, ad, wifi_ssid):
        ad.log.error('Wifi still is connected to %s.', wifi_ssid)
        return False
    else:
        ad.log.info('Wifi is disconnected.')
        return True

class TelLiveRilImsKpiTest(TelephonyBaseTest):
    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        start_pixellogger_always_on_logging(self.android_devices[0])
        self.tel_logger = TelephonyMetricLogger.for_test_case()
        self.user_params["telephony_auto_rerun"] = 0
        self.reboot_4g_test_cycle = self.user_params.get(
            'reboot_4g_test_cycle', 1)
        self.reboot_iwlan_test_cycle = self.user_params.get(
            'reboot_iwlan_test_cycle', 1)
        self.cycle_apm_4g_test_cycle = self.user_params.get(
            'cycle_apm_4g_test_cycle', 1)
        self.cycle_wifi_in_apm_mode_test_cycle = self.user_params.get(
            'cycle_wifi_in_apm_mode_test_cycle', 1)
        self.ims_handover_4g_to_iwlan_with_voice_call_wfc_wifi_preferred_test_cycle = self.user_params.get(
            'ims_handover_4g_to_iwlan_with_voice_call_wfc_wifi_preferred_test_cycle', 1)
        self.ims_handover_4g_to_iwlan_wfc_wifi_preferred_test_cycle = self.user_params.get(
            'ims_handover_4g_to_iwlan_wfc_wifi_preferred_test_cycle', 1)
        self.ims_handover_iwlan_to_4g_wfc_wifi_preferred_test_cycle = self.user_params.get(
            'ims_handover_iwlan_to_4g_wfc_wifi_preferred_test_cycle', 1)
        self.ims_handover_iwlan_to_4g_with_voice_call_wfc_wifi_preferred_test_cycle = self.user_params.get(
            'ims_handover_iwlan_to_4g_with_voice_call_wfc_wifi_preferred_test_cycle', 1)
        self.ims_handover_iwlan_to_4g_wfc_cellular_preferred_test_cycle = self.user_params.get(
            'ims_handover_iwlan_to_4g_wfc_cellular_preferred_test_cycle', 1)
        self.ims_handover_iwlan_to_4g_with_voice_call_wfc_cellular_preferred_test_cycle = self.user_params.get(
            'ims_handover_iwlan_to_4g_with_voice_call_wfc_cellular_preferred_test_cycle', 1)

    def teardown_test(self):
        for ad in self.android_devices:
            toggle_airplane_mode(self.log, ad, False)

    @test_tracker_info(uuid="d6a59a3c-2bbc-4ed3-a41e-4492b4ab8a50")
    @TelephonyBaseTest.tel_test_wrap
    def test_reboot_4g(self):
        """Reboot UE and measure bootup IMS registration time on LTE.

        Test steps:
            1. Enable VoLTE at all slots and ensure IMS is registered over LTE
                cellular network at all slots.
            2. Reboot UE.
            3. Parse logcat to calculate IMS registration time on LTE after
                bootup.
        """
        ad = self.android_devices[0]
        cycle = self.reboot_4g_test_cycle
        voice_slot = get_slot_index_from_voice_sub_id(ad)

        if getattr(ad, 'dsds', False):
            the_other_slot = 1 - voice_slot
        else:
            the_other_slot = None

        result = []
        search_intervals = []
        exit_due_to_high_fail_rate = False
        for attempt in range(cycle):
            _continue = True
            self.log.info(
                '==================> Reboot on LTE %s/%s <==================',
                attempt+1,
                cycle)

            sub_id_list = get_all_sub_id(ad)
            for sub_id in sub_id_list:
                if not phone_setup_volte_for_subscription(self.log, ad, sub_id):
                    result.append(SETUP_PHONE_FAIL)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                if not wait_for_network_service(self.log, ad):
                    result.append(VERIFY_NETWORK_FAIL)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                begin_time = datetime.now()
                if reboot_test(self.log, ad):
                    result.append(True)
                else:
                    result.append(False)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                end_time = datetime.now()
                search_intervals.append([begin_time, end_time])

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or (
                attempt == cycle - 1) or exit_due_to_high_fail_rate:

                ad.log.info(
                    '====== Test result of IMS bootup registration at slot %s '
                    '======',
                    voice_slot)
                ad.log.info(result)

                for slot in [voice_slot, the_other_slot]:
                    if slot is None:
                        continue

                    ims_reg, parsing_fail, avg_ims_reg_duration = parse_ims_reg(
                        ad, search_intervals, '4g', 'reboot', slot=slot)
                    ad.log.info(
                        '====== IMS bootup registration at slot %s ======', slot)
                    for msg in ims_reg:
                        print_nested_dict(ad, msg)

                    ad.log.info(
                        '====== Attempt of parsing fail at slot %s ======' % slot)
                    for msg in parsing_fail:
                        ad.log.info(msg)

                    ad.log.warning('====== Summary ======')
                    ad.log.warning(
                        '%s/%s cycles failed.',
                        (len(result) - result.count(True)),
                        len(result))
                    for attempt, value in enumerate(result):
                        if value is not True:
                            ad.log.warning('Cycle %s: %s', attempt+1, value)
                    try:
                        fail_rate = (
                            len(result) - result.count(True))/len(result)
                        ad.log.info(
                            'Fail rate of IMS bootup registration at slot %s: %s',
                            slot,
                            fail_rate)
                    except Exception as e:
                        ad.log.error(
                            'Fail rate of IMS bootup registration at slot %s: '
                            'ERROR (%s)',
                            slot,
                            e)

                    ad.log.info(
                        'Number of trials with valid parsed logs: %s',
                        len(ims_reg))
                    ad.log.info(
                        'Average IMS bootup registration time at slot %s: %s',
                        slot,
                        avg_ims_reg_duration)

            if exit_due_to_high_fail_rate:
                break

        return test_result(result, cycle)

    @test_tracker_info(uuid="c97dd2f2-9e8a-43d4-9352-b53abe5ac6a4")
    @TelephonyBaseTest.tel_test_wrap
    def test_reboot_iwlan(self):
        """Reboot UE and measure bootup IMS registration time over iwlan.

        Test steps:
            1. Enable VoLTE at all slots; enable WFC and set WFC mode to
                Wi-Fi-preferred mode; connect Wi-Fi and ensure IMS is registered
                at all slots over iwlan.
            2. Reboot UE.
            3. Parse logcat to calculate IMS registration time over iwlan after
                bootup.
        """
        ad = self.android_devices[0]
        cycle = self.reboot_iwlan_test_cycle
        voice_slot = get_slot_index_from_voice_sub_id(ad)

        if getattr(ad, 'dsds', False):
            the_other_slot = 1 - voice_slot
        else:
            the_other_slot = None

        result = []
        search_intervals = []
        exit_due_to_high_fail_rate = False
        for attempt in range(cycle):
            _continue = True
            self.log.info(
                '==================> Reboot on iwlan %s/%s <==================',
                attempt+1,
                cycle)

            sub_id_list = get_all_sub_id(ad)
            for sub_id in sub_id_list:
                if not phone_setup_iwlan_for_subscription(
                    self.log,
                    ad,
                    sub_id,
                    False,
                    WFC_MODE_WIFI_PREFERRED,
                    self.wifi_network_ssid,
                    self.wifi_network_pass):

                    result.append(SETUP_PHONE_FAIL)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

                    wait_for_wifi_disconnected(ad, self.wifi_network_ssid)

            if _continue:
                if not verify_internet_connection(self.log, ad):
                    result.append(VERIFY_INTERNET_FAIL)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                begin_time = datetime.now()
                if reboot_test(self.log, ad, wifi_ssid=self.wifi_network_ssid):
                    result.append(True)
                else:
                    result.append(False)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                end_time = datetime.now()
                search_intervals.append([begin_time, end_time])

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or (
                attempt == cycle - 1) or exit_due_to_high_fail_rate:

                ad.log.info(
                    '====== Test result of IMS bootup registration at slot %s '
                    '======',
                    voice_slot)
                ad.log.info(result)

                for slot in [voice_slot, the_other_slot]:
                    if slot is None:
                        continue

                    ims_reg, parsing_fail, avg_ims_reg_duration = parse_ims_reg(
                        ad, search_intervals, 'iwlan', 'reboot', slot=slot)
                    ad.log.info(
                        '====== IMS bootup registration at slot %s ======', slot)
                    for msg in ims_reg:
                        print_nested_dict(ad, msg)

                    ad.log.info(
                        '====== Attempt of parsing fail at slot %s ======' % slot)
                    for msg in parsing_fail:
                        ad.log.info(msg)

                    ad.log.warning('====== Summary ======')
                    ad.log.warning(
                        '%s/%s cycles failed.',
                        (len(result) - result.count(True)),
                        len(result))
                    for attempt, value in enumerate(result):
                        if value is not True:
                            ad.log.warning('Cycle %s: %s', attempt+1, value)

                    try:
                        fail_rate = (
                            len(result) - result.count(True))/len(result)
                        ad.log.info(
                            'Fail rate of IMS bootup registration at slot %s: %s',
                            slot,
                            fail_rate)
                    except Exception as e:
                        ad.log.error(
                            'Fail rate of IMS bootup registration at slot %s: '
                            'ERROR (%s)',
                            slot,
                            e)

                    ad.log.info(
                        'Number of trials with valid parsed logs: %s',
                        len(ims_reg))
                    ad.log.info(
                        'Average IMS bootup registration time at slot %s: %s',
                        slot, avg_ims_reg_duration)
            if exit_due_to_high_fail_rate:
                break

        return test_result(result, cycle)

    @test_tracker_info(uuid="45ed4572-7de9-4e1b-b2ec-58dea722fa3e")
    @TelephonyBaseTest.tel_test_wrap
    def test_cycle_airplane_mode_4g(self):
        """Cycle airplane mode and measure IMS registration time on LTE

        Test steps:
            1. Enable VoLTE at all slots and ensure IMS is registered on LTE at
                all slots.
            2. Cycle airplane mode.
            3. Parse logcat to calculate IMS registration time right after
                recovery of cellular service.
        """
        ad = self.android_devices[0]
        cycle = self.cycle_apm_4g_test_cycle
        voice_slot = get_slot_index_from_voice_sub_id(ad)

        if getattr(ad, 'dsds', False):
            the_other_slot = 1 - voice_slot
        else:
            the_other_slot = None

        result = []
        search_intervals = []
        exit_due_to_high_fail_rate = False
        for attempt in range(cycle):
            _continue = True
            self.log.info(
                '============> Cycle airplane mode on LTE %s/%s <============',
                attempt+1,
                cycle)

            sub_id_list = get_all_sub_id(ad)
            for sub_id in sub_id_list:
                if not phone_setup_volte_for_subscription(self.log, ad, sub_id):
                    result.append(SETUP_PHONE_FAIL)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                if not wait_for_network_service(self.log, ad):
                    result.append(VERIFY_NETWORK_FAIL)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                begin_time = datetime.now()
                if airplane_mode_test(self.log, ad):
                    result.append(True)
                else:
                    result.append(False)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                end_time = datetime.now()
                search_intervals.append([begin_time, end_time])

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or (
                attempt == cycle - 1) or exit_due_to_high_fail_rate:

                ad.log.info(
                    '====== Test result of IMS registration at slot %s ======',
                    voice_slot)
                ad.log.info(result)

                for slot in [voice_slot, the_other_slot]:
                    if slot is None:
                        continue

                    ims_reg, parsing_fail, avg_ims_reg_duration = parse_ims_reg(
                        ad, search_intervals, '4g', 'apm', slot=slot)
                    ad.log.info(
                        '====== IMS registration at slot %s ======', slot)
                    for msg in ims_reg:
                        print_nested_dict(ad, msg)

                    ad.log.info(
                        '====== Attempt of parsing fail at slot %s ======' % slot)
                    for msg in parsing_fail:
                        ad.log.info(msg)

                    ad.log.warning('====== Summary ======')
                    ad.log.warning('%s/%s cycles failed.', (len(result) - result.count(True)), len(result))
                    for attempt, value in enumerate(result):
                        if value is not True:
                            ad.log.warning('Cycle %s: %s', attempt+1, value)

                    try:
                        fail_rate = (
                            len(result) - result.count(True))/len(result)
                        ad.log.info(
                            'Fail rate of IMS registration at slot %s: %s',
                            slot,
                            fail_rate)
                    except Exception as e:
                        ad.log.error(
                            'Fail rate of IMS registration at slot %s: '
                            'ERROR (%s)',
                            slot,
                            e)

                    ad.log.info(
                        'Number of trials with valid parsed logs: %s',
                        len(ims_reg))
                    ad.log.info(
                        'Average IMS registration time at slot %s: %s',
                        slot, avg_ims_reg_duration)

            if exit_due_to_high_fail_rate:
                break

        return test_result(result, cycle)

    @test_tracker_info(uuid="915c9403-8bbc-45c7-be53-8b0de4191716")
    @TelephonyBaseTest.tel_test_wrap
    def test_cycle_wifi_in_apm_mode(self):
        """Cycle Wi-Fi in airplane mode and measure IMS registration time over
            iwlan.

        Test steps:
            1. Enable VoLTE; enable WFC and set WFC mode to Wi-Fi-preferred mode;
                turn on airplane mode and connect Wi-Fi to ensure IMS is
                registered over iwlan.
            2. Cycle Wi-Fi.
            3. Parse logcat to calculate IMS registration time right after
                recovery of Wi-Fi connection in airplane mode.
        """
        ad = self.android_devices[0]
        cycle = self.cycle_wifi_in_apm_mode_test_cycle
        voice_slot = get_slot_index_from_voice_sub_id(ad)

        result = []
        search_intervals = []
        exit_due_to_high_fail_rate = False
        for attempt in range(cycle):
            _continue = True
            self.log.info(
                '============> Cycle WiFi in airplane mode %s/%s <============',
                attempt+1,
                cycle)

            begin_time = datetime.now()

            if not wait_for_wifi_disconnected(ad, self.wifi_network_ssid):
                result.append(False)
                self._take_bug_report(
                    self.test_name, begin_time=get_current_epoch_time())
                _continue = False
                if not test_result(result, cycle, 10, 0.1):
                    exit_due_to_high_fail_rate = True

            if _continue:
                if not phone_setup_iwlan(
                    self.log,
                    ad,
                    True,
                    WFC_MODE_WIFI_PREFERRED,
                    self.wifi_network_ssid,
                    self.wifi_network_pass):

                    result.append(False)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                if not verify_internet_connection(self.log, ad):
                    result.append(VERIFY_INTERNET_FAIL)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                if not wait_for_wifi_disconnected(
                    ad, self.wifi_network_ssid):
                    result.append(False)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                result.append(True)
                end_time = datetime.now()
                search_intervals.append([begin_time, end_time])

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or (
                attempt == cycle - 1) or exit_due_to_high_fail_rate:

                ad.log.info(
                    '====== Test result of IMS registration at slot %s ======',
                    voice_slot)
                ad.log.info(result)

                ims_reg, parsing_fail, avg_ims_reg_duration = parse_ims_reg(
                    ad, search_intervals, 'iwlan', 'apm')
                ad.log.info(
                    '====== IMS registration at slot %s ======', voice_slot)
                for msg in ims_reg:
                    ad.log.info(msg)

                ad.log.info(
                    '====== Attempt of parsing fail at slot %s ======' % voice_slot)
                for msg in parsing_fail:
                    ad.log.info(msg)

                ad.log.warning('====== Summary ======')
                ad.log.warning(
                    '%s/%s cycles failed.',
                    (len(result) - result.count(True)),
                    len(result))
                for attempt, value in enumerate(result):
                    if value is not True:
                        ad.log.warning('Cycle %s: %s', attempt+1, value)

                try:
                    fail_rate = (len(result) - result.count(True))/len(result)
                    ad.log.info(
                        'Fail rate of IMS registration at slot %s: %s',
                        voice_slot,
                        fail_rate)
                except Exception as e:
                    ad.log.error(
                        'Fail rate of IMS registration at slot %s: ERROR (%s)',
                        voice_slot,
                        e)

                ad.log.info(
                    'Number of trials with valid parsed logs: %s', len(ims_reg))
                ad.log.info(
                    'Average IMS registration time at slot %s: %s',
                    voice_slot, avg_ims_reg_duration)

            if exit_due_to_high_fail_rate:
                break
        toggle_airplane_mode(self.log, ad, False)
        return test_result(result, cycle)

    def ims_handover_4g_to_iwlan_wfc_wifi_preferred(self, voice_call=False):
        """Connect WFC to make IMS registration hand over from LTE to iwlan in
            Wi-Fi-preferred mode. Measure IMS handover time.

        Test steps:
            1. Enable WFC and set WFC mode to Wi-Fi-preferred mode.
            2. Ensure Wi-Fi are disconnected and all cellular services are
                available.
            3. (Optional) Make a VoLTE call and keep the call active.
            4. Connect Wi-Fi. The IMS registration should hand over from LTE
                to iwlan.
            5. Parse logcat to calculate the IMS handover time.

        Args:
            voice_call: True if an active VoLTE call is desired in the background
                during IMS handover procedure. Otherwise False.
        """
        ad = self.android_devices[0]
        if voice_call:
            cycle = self.ims_handover_4g_to_iwlan_with_voice_call_wfc_wifi_preferred_test_cycle
        else:
            cycle = self.ims_handover_4g_to_iwlan_wfc_wifi_preferred_test_cycle

        voice_slot = get_slot_index_from_voice_sub_id(ad)

        result = []
        search_intervals = []
        exit_due_to_high_fail_rate = False

        if not set_wfc_mode(self.log, ad, WFC_MODE_WIFI_PREFERRED):
            return False

        for attempt in range(cycle):
            _continue = True
            self.log.info(
                '======> IMS handover from LTE to iwlan in WFC wifi-preferred '
                'mode %s/%s <======',
                attempt+1,
                cycle)

            begin_time = datetime.now()

            if not wait_for_wifi_disconnected(ad, self.wifi_network_ssid):
                result.append(False)
                self._take_bug_report(
                    self.test_name, begin_time=get_current_epoch_time())
                _continue = False
                if not test_result(result, cycle, 10, 0.1):
                    exit_due_to_high_fail_rate = True

            if _continue:
                if not wait_for_network_service(
                    self.log,
                    ad,
                    wifi_connected=False,
                    ims_reg=True):

                    result.append(False)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                if voice_call:
                    ad_mt = self.android_devices[1]
                    call_params = [(
                        ad,
                        ad_mt,
                        None,
                        is_phone_in_call_volte,
                        None)]
                    call_result = two_phone_call_short_seq(
                        self.log,
                        ad,
                        phone_idle_volte,
                        is_phone_in_call_volte,
                        ad_mt,
                        None,
                        None,
                        wait_time_in_call=30,
                        call_params=call_params)
                    self.tel_logger.set_result(call_result.result_value)
                    if not call_result:
                        self._take_bug_report(
                            self.test_name, begin_time=get_current_epoch_time())
                        _continue = False
                        if not test_result(result, cycle, 10, 0.1):
                            exit_due_to_high_fail_rate = True

            if _continue:
                if not phone_setup_iwlan(
                    self.log,
                    ad,
                    False,
                    WFC_MODE_WIFI_PREFERRED,
                    self.wifi_network_ssid,
                    self.wifi_network_pass):

                    result.append(False)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                if voice_slot == 0:
                    ims_pattern = ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT0
                else:
                    ims_pattern = ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT1

                if wait_for_log(ad, ims_pattern, begin_time=begin_time):
                    ad.log.info(
                        'IMS registration is handed over from LTE to iwlan.')
                else:
                    ad.log.error(
                        'IMS registration is NOT yet handed over from LTE to '
                        'iwlan.')

            if voice_call:
                hangup_call(self.log, ad)

            if _continue:
                if not verify_internet_connection(self.log, ad):
                    result.append(VERIFY_INTERNET_FAIL)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                if not wait_for_wifi_disconnected(
                    ad, self.wifi_network_ssid):
                    result.append(False)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                if voice_slot == 0:
                    ims_pattern = ON_IMS_MM_TEL_CONNECTED_4G_SLOT0
                else:
                    ims_pattern = ON_IMS_MM_TEL_CONNECTED_4G_SLOT1

                if wait_for_log(ad, ims_pattern, begin_time=begin_time):
                    ad.log.info(
                        'IMS registration is handed over from iwlan to LTE.')
                else:
                    ad.log.error(
                        'IMS registration is NOT yet handed over from iwlan to '
                        'LTE.')

            if _continue:
                result.append(True)
                end_time = datetime.now()
                search_intervals.append([begin_time, end_time])

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or (
                attempt == cycle - 1) or exit_due_to_high_fail_rate:

                ad.log.info(
                    '====== Test result of IMS registration at slot %s ======',
                    voice_slot)
                ad.log.info(result)

                ims_reg, parsing_fail, avg_ims_reg_duration = parse_ims_reg(
                    ad, search_intervals, 'iwlan', 'apm')
                ad.log.info(
                    '====== IMS registration at slot %s ======', voice_slot)
                for msg in ims_reg:
                    ad.log.info(msg)

                ad.log.info(
                    '====== Attempt of parsing fail at slot %s ======' % voice_slot)
                for msg in parsing_fail:
                    ad.log.info(msg)

                ad.log.warning('====== Summary ======')
                ad.log.warning(
                    '%s/%s cycles failed.',
                    (len(result) - result.count(True)),
                    len(result))
                for attempt, value in enumerate(result):
                    if value is not True:
                        ad.log.warning('Cycle %s: %s', attempt+1, value)

                try:
                    fail_rate = (len(result) - result.count(True))/len(result)
                    ad.log.info(
                        'Fail rate of IMS registration at slot %s: %s',
                        voice_slot,
                        fail_rate)
                except Exception as e:
                    ad.log.error(
                        'Fail rate of IMS registration at slot %s: ERROR (%s)',
                        voice_slot,
                        e)

                ad.log.info(
                    'Number of trials with valid parsed logs: %s',len(ims_reg))
                ad.log.info(
                    'Average IMS registration time at slot %s: %s',
                    voice_slot, avg_ims_reg_duration)

            if exit_due_to_high_fail_rate:
                break

        return test_result(result, cycle)

    @test_tracker_info(uuid="e3d1aaa8-f673-4a2b-adb1-cfa525a4edbd")
    @TelephonyBaseTest.tel_test_wrap
    def test_ims_handover_4g_to_iwlan_with_voice_call_wfc_wifi_preferred(self):
        """Connect WFC to make IMS registration hand over from LTE to iwlan in
            Wi-Fi-preferred mode. Measure IMS handover time.

        Test steps:
            1. Enable WFC and set WFC mode to Wi-Fi-preferred mode.
            2. Ensure Wi-Fi are disconnected and all cellular services are
                available.
            3. Make a VoLTE call and keep the call active.
            4. Connect Wi-Fi. The IMS registration should hand over from LTE
                to iwlan.
            5. Parse logcat to calculate the IMS handover time.
        """
        return self.ims_handover_4g_to_iwlan_wfc_wifi_preferred(True)

    @test_tracker_info(uuid="bd86fb46-04bd-4642-923a-747e6c9d4282")
    @TelephonyBaseTest.tel_test_wrap
    def test_ims_handover_4g_to_iwlan_wfc_wifi_preferred(self):
        """Connect WFC to make IMS registration hand over from LTE to iwlan in
            Wi-Fi-preferred mode. Measure IMS handover time.

        Test steps:
            1. Enable WFC and set WFC mode to Wi-Fi-preferred mode.
            2. Ensure Wi-Fi are disconnected and all cellular services are
                available.
            3. Connect Wi-Fi. The IMS registration should hand over from LTE
                to iwlan.
            4. Parse logcat to calculate the IMS handover time.
        """
        return self.ims_handover_4g_to_iwlan_wfc_wifi_preferred(False)

    def ims_handover_iwlan_to_4g_wfc_wifi_preferred(self, voice_call=False):
        """Disconnect Wi-Fi to make IMS registration hand over from iwlan to LTE
            in Wi-Fi-preferred mode. Measure IMS handover time.

        Test steps:
            1. Enable WFC, set WFC mode to Wi-Fi-preferred mode, and then
                connect Wi-Fi to let IMS register over iwlan.
            2. (Optional) Make a WFC call and keep the call active.
            3. Disconnect Wi-Fi. The IMS registration should hand over from iwlan
                to LTE.
            4. Parse logcat to calculate the IMS handover time.

        Args:
            voice_call: True if an active WFC call is desired in the background
                during IMS handover procedure. Otherwise False.
        """
        ad = self.android_devices[0]
        if voice_call:
            cycle = self.ims_handover_iwlan_to_4g_with_voice_call_wfc_wifi_preferred_test_cycle
        else:
            cycle = self.ims_handover_iwlan_to_4g_wfc_wifi_preferred_test_cycle
        voice_slot = get_slot_index_from_voice_sub_id(ad)

        result = []
        search_intervals = []
        exit_due_to_high_fail_rate = False
        for attempt in range(cycle):
            _continue = True
            self.log.info(
                '======> IMS handover from iwlan to LTE in WFC wifi-preferred '
                'mode %s/%s <======',
                attempt+1,
                cycle)

            begin_time = datetime.now()

            if not phone_setup_iwlan(
                self.log,
                ad,
                False,
                WFC_MODE_WIFI_PREFERRED,
                self.wifi_network_ssid,
                self.wifi_network_pass):

                result.append(False)
                self._take_bug_report(
                    self.test_name, begin_time=get_current_epoch_time())
                _continue = False
                if not test_result(result, cycle, 10, 0.1):
                    exit_due_to_high_fail_rate = True

                wait_for_wifi_disconnected(ad, self.wifi_network_ssid)

            if _continue:
                if voice_slot == 0:
                    ims_pattern = ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT0
                else:
                    ims_pattern = ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT1

                if wait_for_log(ad, ims_pattern, begin_time=begin_time):
                    ad.log.info(
                        'IMS registration is handed over from LTE to iwlan.')
                else:
                    ad.log.error(
                        'IMS registration is NOT yet handed over from LTE to '
                        'iwlan.')

            if _continue:
                if not verify_internet_connection(self.log, ad):
                    result.append(VERIFY_INTERNET_FAIL)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                if voice_call:
                    ad_mt = self.android_devices[1]
                    call_params = [(
                        ad,
                        ad_mt,
                        None,
                        is_phone_in_call_iwlan,
                        None)]
                    call_result = two_phone_call_short_seq(
                        self.log,
                        ad,
                        phone_idle_iwlan,
                        is_phone_in_call_iwlan,
                        ad_mt,
                        None,
                        None,
                        wait_time_in_call=30,
                        call_params=call_params)
                    self.tel_logger.set_result(call_result.result_value)
                    if not call_result:
                        self._take_bug_report(
                            self.test_name, begin_time=get_current_epoch_time())
                        _continue = False
                        if not test_result(result, cycle, 10, 0.1):
                            exit_due_to_high_fail_rate = True

            if _continue:
                if not wait_for_wifi_disconnected(
                    ad, self.wifi_network_ssid):
                    result.append(False)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                if voice_slot == 0:
                    ims_pattern = ON_IMS_MM_TEL_CONNECTED_4G_SLOT0
                else:
                    ims_pattern = ON_IMS_MM_TEL_CONNECTED_4G_SLOT1

                if wait_for_log(ad, ims_pattern, begin_time=begin_time):
                    ad.log.info(
                        'IMS registration is handed over from iwlan to LTE.')
                else:
                    ad.log.error(
                        'IMS registration is NOT yet handed over from iwlan to '
                        'LTE.')

            if voice_call:
                hangup_call(self.log, ad)

            if _continue:
                if not wait_for_network_service(
                    self.log,
                    ad,
                    wifi_connected=False,
                    ims_reg=True):

                    result.append(False)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                result.append(True)
                end_time = datetime.now()
                search_intervals.append([begin_time, end_time])

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or (
                attempt == cycle - 1) or exit_due_to_high_fail_rate:

                ad.log.info(
                    '====== Test result of IMS registration at slot %s ======',
                    voice_slot)
                ad.log.info(result)

                ims_reg, parsing_fail, avg_ims_reg_duration = parse_ims_reg(
                    ad, search_intervals, '4g', 'wifi_off')
                ad.log.info(
                    '====== IMS registration at slot %s ======', voice_slot)
                for msg in ims_reg:
                    ad.log.info(msg)

                ad.log.info(
                    '====== Attempt of parsing fail at slot %s ======' % voice_slot)
                for msg in parsing_fail:
                    ad.log.info(msg)

                ad.log.warning('====== Summary ======')
                ad.log.warning(
                    '%s/%s cycles failed.',
                    (len(result) - result.count(True)),
                    len(result))
                for attempt, value in enumerate(result):
                    if value is not True:
                        ad.log.warning('Cycle %s: %s', attempt+1, value)

                try:
                    fail_rate = (len(result) - result.count(True))/len(result)
                    ad.log.info(
                        'Fail rate of IMS registration at slot %s: %s',
                        voice_slot,
                        fail_rate)
                except Exception as e:
                    ad.log.error(
                        'Fail rate of IMS registration at slot %s: ERROR (%s)',
                        voice_slot,
                        e)

                ad.log.info(
                    'Number of trials with valid parsed logs: %s', len(ims_reg))
                ad.log.info(
                    'Average IMS registration time at slot %s: %s',
                    voice_slot, avg_ims_reg_duration)

            if exit_due_to_high_fail_rate:
                break

        return test_result(result, cycle)

    @test_tracker_info(uuid="6ce623a6-7ef9-42db-8099-d5c449e70bff")
    @TelephonyBaseTest.tel_test_wrap
    def test_ims_handover_iwlan_to_4g_wfc_wifi_preferred(self):
        """Disconnect Wi-Fi to make IMS registration hand over from iwlan to LTE
            in Wi-Fi-preferred mode. Measure IMS handover time.

        Test steps:
            1. Enable WFC, set WFC mode to Wi-Fi-preferred mode, and then
                connect Wi-Fi to let IMS register over iwlan.
            2. Disconnect Wi-Fi. The IMS registration should hand over from iwlan
                to LTE.
            3. Parse logcat to calculate the IMS handover time.
        """
        return self.ims_handover_iwlan_to_4g_wfc_wifi_preferred(False)

    @test_tracker_info(uuid="b965ab09-d8b1-423f-bb98-2cdd43babbe3")
    @TelephonyBaseTest.tel_test_wrap
    def test_ims_handover_iwlan_to_4g_with_voice_call_wfc_wifi_preferred(self):
        """Disconnect Wi-Fi to make IMS registration hand over from iwlan to LTE
            in Wi-Fi-preferred mode. Measure IMS handover time.

        Test steps:
            1. Enable WFC, set WFC mode to Wi-Fi-preferred mode, and then
                connect Wi-Fi to let IMS register over iwlan.
            2. Make a WFC call and keep the call active.
            3. Disconnect Wi-Fi. The IMS registration should hand over from iwlan
                to LTE.
            4. Parse logcat to calculate the IMS handover time.
        """
        return self.ims_handover_iwlan_to_4g_wfc_wifi_preferred(True)

    def ims_handover_iwlan_to_4g_wfc_cellular_preferred(self, voice_call=False):
        """Turn off airplane mode to make IMS registration hand over from iwlan to LTE
            in WFC cellular-preferred mode. Measure IMS handover time.

        Test steps:
            1. Enable WFC, set WFC mode to cellular-preferred mode, turn on
                airplane mode and then connect Wi-Fi to let IMS register over
                iwlan.
            2. (Optional) Make a WFC call and keep the call active.
            3. Turn off airplane mode. The IMS registration should hand over
                from iwlan to LTE.
            4. Parse logcat to calculate the IMS handover time.

        Args:
            voice_call: True if an active WFC call is desired in the background
                during IMS handover procedure. Otherwise False.
        """
        ad = self.android_devices[0]
        if voice_call:
            cycle = self.ims_handover_iwlan_to_4g_with_voice_call_wfc_cellular_preferred_test_cycle
        else:
            cycle = self.ims_handover_iwlan_to_4g_wfc_cellular_preferred_test_cycle

        voice_slot = get_slot_index_from_voice_sub_id(ad)

        result = []
        search_intervals = []
        exit_due_to_high_fail_rate = False
        for attempt in range(cycle):
            _continue = True

            self.log.info(
                '======> IMS handover from iwlan to LTE in WFC '
                'cellular-preferred mode %s/%s <======',
                attempt+1,
                cycle)

            begin_time = datetime.now()

            if not phone_setup_iwlan(
                self.log,
                ad,
                True,
                WFC_MODE_CELLULAR_PREFERRED,
                self.wifi_network_ssid,
                self.wifi_network_pass):

                result.append(False)
                self._take_bug_report(
                    self.test_name, begin_time=get_current_epoch_time())
                _continue = False
                if not test_result(result, cycle, 10, 0.1):
                    exit_due_to_high_fail_rate = True

                toggle_airplane_mode(self.log, ad, False)

            if _continue:
                if voice_slot == 0:
                    ims_pattern = ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT0
                else:
                    ims_pattern = ON_IMS_MM_TEL_CONNECTED_IWLAN_SLOT1

                if wait_for_log(ad, ims_pattern, begin_time=begin_time):
                    ad.log.info(
                        'IMS registration is handed over from LTE to iwlan.')
                else:
                    ad.log.error(
                        'IMS registration is NOT yet handed over from LTE to '
                        'iwlan.')

            if _continue:
                if not verify_internet_connection(self.log, ad):
                    result.append(VERIFY_INTERNET_FAIL)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                if voice_call:
                    ad_mt = self.android_devices[1]
                    call_params = [(
                        ad,
                        ad_mt,
                        None,
                        is_phone_in_call_iwlan,
                        None)]
                    call_result = two_phone_call_short_seq(
                        self.log,
                        ad,
                        phone_idle_iwlan,
                        is_phone_in_call_iwlan,
                        ad_mt,
                        None,
                        None,
                        wait_time_in_call=30,
                        call_params=call_params)
                    self.tel_logger.set_result(call_result.result_value)
                    if not call_result:
                        self._take_bug_report(
                            self.test_name, begin_time=get_current_epoch_time())
                        _continue = False
                        if not test_result(result, cycle, 10, 0.1):
                            exit_due_to_high_fail_rate = True

            if _continue:
                if not toggle_airplane_mode(self.log, ad, False):
                    result.append(TOGGLE_OFF_APM_FAIL)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                if voice_slot == 0:
                    ims_pattern = ON_IMS_MM_TEL_CONNECTED_4G_SLOT0
                else:
                    ims_pattern = ON_IMS_MM_TEL_CONNECTED_4G_SLOT1

                if wait_for_log(ad, ims_pattern, begin_time=begin_time):
                    ad.log.info(
                        'IMS registration is handed over from iwlan to LTE.')
                else:
                    ad.log.error(
                        'IMS registration is NOT yet handed over from iwlan to '
                        'LTE.')

            if voice_call:
                hangup_call(self.log, ad)

            if _continue:
                if not wait_for_network_service(
                    self.log,
                    ad,
                    wifi_connected=True,
                    wifi_ssid=self.wifi_network_ssid,
                    ims_reg=True):

                    result.append(False)
                    self._take_bug_report(
                        self.test_name, begin_time=get_current_epoch_time())
                    _continue = False
                    if not test_result(result, cycle, 10, 0.1):
                        exit_due_to_high_fail_rate = True

            if _continue:
                result.append(True)
                end_time = datetime.now()
                search_intervals.append([begin_time, end_time])

            if (attempt+1) % CALCULATE_EVERY_N_CYCLES == 0 or (
                attempt == cycle - 1) or exit_due_to_high_fail_rate:

                ad.log.info(
                    '====== Test result of IMS registration at slot %s ======',
                    voice_slot)
                ad.log.info(result)

                ims_reg, parsing_fail, avg_ims_reg_duration = parse_ims_reg(
                    ad, search_intervals, '4g', 'apm')
                ad.log.info(
                    '====== IMS registration at slot %s ======', voice_slot)
                for msg in ims_reg:
                    ad.log.info(msg)

                ad.log.info(
                    '====== Attempt of parsing fail at slot %s ======' % voice_slot)
                for msg in parsing_fail:
                    ad.log.info(msg)

                ad.log.warning('====== Summary ======')
                ad.log.warning(
                    '%s/%s cycles failed.',
                    (len(result) - result.count(True)),
                    len(result))
                for attempt, value in enumerate(result):
                    if value is not True:
                        ad.log.warning('Cycle %s: %s', attempt+1, value)

                try:
                    fail_rate = (len(result) - result.count(True))/len(result)
                    ad.log.info(
                        'Fail rate of IMS registration at slot %s: %s',
                        voice_slot,
                        fail_rate)
                except Exception as e:
                    ad.log.error(
                        'Fail rate of IMS registration at slot %s: ERROR (%s)',
                        voice_slot,
                        e)

                ad.log.info(
                    'Number of trials with valid parsed logs: %s', len(ims_reg))
                ad.log.info(
                    'Average IMS registration time at slot %s: %s',
                    voice_slot, avg_ims_reg_duration)

            if exit_due_to_high_fail_rate:
                break

        return test_result(result, cycle)

    @test_tracker_info(uuid="ce69fac3-931b-4177-82ea-dbae50b2b310")
    @TelephonyBaseTest.tel_test_wrap
    def test_ims_handover_iwlan_to_4g_wfc_cellular_preferred(self):
        """Turn off airplane mode to make IMS registration hand over from iwlan to LTE
            in WFC cellular-preferred mode. Measure IMS handover time.

        Test steps:
            1. Enable WFC, set WFC mode to cellular-preferred mode, turn on
                airplane mode and then connect Wi-Fi to let IMS register over
                iwlan.
            2. Turn off airplane mode. The IMS registration should hand over
                from iwlan to LTE.
            3. Parse logcat to calculate the IMS handover time.
        """
        return self.ims_handover_iwlan_to_4g_wfc_cellular_preferred(False)

    @test_tracker_info(uuid="0ac7d43e-34e6-4ea3-92f4-e413e90a8bc1")
    @TelephonyBaseTest.tel_test_wrap
    def test_ims_handover_iwlan_to_4g_with_voice_call_wfc_cellular_preferred(self):
        """Turn off airplane mode to make IMS registration hand over from iwlan to LTE
            in WFC cellular-preferred mode. Measure IMS handover time.

        Test steps:
            1. Enable WFC, set WFC mode to cellular-preferred mode, turn on
                airplane mode and then connect Wi-Fi to let IMS register over
                iwlan.
            2. Make a WFC call and keep the call active.
            3. Turn off airplane mode. The IMS registration should hand over
                from iwlan to LTE.
            4. Parse logcat to calculate the IMS handover time.
        """
        return self.ims_handover_iwlan_to_4g_wfc_cellular_preferred(True)