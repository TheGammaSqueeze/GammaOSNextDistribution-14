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
    Test Script for 5G Voice scenarios
"""

from acts import signals
from acts.libs.utils.multithread import multithread_func
from acts.libs.utils.multithread import run_multithread_func
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.loggers.telephony_metric_logger import TelephonyMetricLogger
from acts_contrib.test_utils.tel.loggers.protos.telephony_metric_pb2 import TelephonyVoiceTestResult
from acts_contrib.test_utils.tel.tel_defines import DIRECTION_MOBILE_ORIGINATED
from acts_contrib.test_utils.tel.tel_defines import DIRECTION_MOBILE_TERMINATED
from acts_contrib.test_utils.tel.tel_defines import GEN_5G
from acts_contrib.test_utils.tel.tel_defines import TOTAL_LONG_CALL_DURATION
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_IN_CALL_FOR_IMS
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_PREFERRED
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_ONLY
from acts_contrib.test_utils.tel.tel_5g_utils import is_current_network_5g
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_both_devices_for_volte
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_device_for_5g
from acts_contrib.test_utils.tel.tel_5g_test_utils import verify_5g_attach_for_both_devices
from acts_contrib.test_utils.tel.tel_data_utils import active_file_download_task
from acts_contrib.test_utils.tel.tel_data_utils import call_epdg_to_epdg_wfc
from acts_contrib.test_utils.tel.tel_data_utils import get_mobile_data_usage
from acts_contrib.test_utils.tel.tel_data_utils import remove_mobile_data_usage_limit
from acts_contrib.test_utils.tel.tel_data_utils import set_mobile_data_usage_limit
from acts_contrib.test_utils.tel.tel_data_utils import test_call_setup_in_active_data_transfer
from acts_contrib.test_utils.tel.tel_data_utils import test_call_setup_in_active_youtube_video
from acts_contrib.test_utils.tel.tel_data_utils import wifi_cell_switching
from acts_contrib.test_utils.tel.tel_data_utils import test_wifi_cell_switching_in_call
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_idle_2g
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_idle_csfb
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_idle_iwlan
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_idle_volte
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_csfb
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_iwlan
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_voice_2g
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_voice_3g
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_voice_general
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_test_utils import install_dialer_apk
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_voice_utils import _test_call_long_duration
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_2g
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_3g
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_csfb
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_iwlan
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_volte
from acts_contrib.test_utils.tel.tel_voice_utils import phone_setup_call_hold_unhold_test
from acts_contrib.test_utils.tel.tel_voice_utils import call_setup_teardown
from acts_contrib.test_utils.tel.tel_voice_utils import call_voicemail_erase_all_pending_voicemail
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_active
from acts_contrib.test_utils.tel.tel_voice_utils import two_phone_call_leave_voice_mail
from acts_contrib.test_utils.tel.tel_voice_utils import two_phone_call_long_seq
from acts_contrib.test_utils.tel.tel_voice_utils import two_phone_call_short_seq
from acts_contrib.test_utils.tel.tel_ops_utils import initiate_call_verify_operation


CallResult = TelephonyVoiceTestResult.CallResult.Value


class Nsa5gVoiceTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()
        self.number_of_devices = 2
        self.message_lengths = (50, 160, 180)
        self.tel_logger = TelephonyMetricLogger.for_test_case()
        self.stress_test_number = self.get_stress_test_number()
        self.long_call_duration = self.user_params.get(
            "long_call_duration",
            TOTAL_LONG_CALL_DURATION)

        self.dialer_util = self.user_params.get("dialer_apk", None)
        if isinstance(self.dialer_util, list):
            self.dialer_util = self.dialer_util[0]

        if self.dialer_util:
            ads = self.android_devices
            for ad in ads:
                install_dialer_apk(ad, self.dialer_util)

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)

    """ Tests Begin """

    @test_tracker_info(uuid="1bef3da1-4608-4b0e-8b78-f3f7be0115d5")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_volte_to_volte(self):
        """ 5g nsa volte to volte call test

        1. Make sure PhoneA is in nsa5g mode (with volte).
        2. Make sure PhoneB is in nsa5g mode (with volte).
        3. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneA.
        4. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneB.
        5. Verify both PhoneA and PhoneB gets attached back to nsa5g

        Raises:
            TestFailure if not success.
        """
        ads = self.android_devices
        if not provision_both_devices_for_volte(self.log, ads, GEN_5G,
                                                nr_type='nsa'):
            return False

        # VoLTE calls
        result = two_phone_call_short_seq(
            self.log, ads[0], None, is_phone_in_call_volte, ads[1],
            None, is_phone_in_call_volte, None,
            WAIT_TIME_IN_CALL_FOR_IMS)
        if not result:
            self.log.error("Failure is volte call during 5g nsa")
            return False

        if not verify_5g_attach_for_both_devices(self.log, ads, nr_type='nsa'):
            return False

        self.log.info("PASS - volte test over 5g nsa validated")
        return True

    @test_tracker_info(uuid="3df252a4-308a-49c3-8e37-08e9c4e8efef")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_volte_to_3g(self):
        """ 5g nsa volte to 3g call test

        1. Make sure PhoneA is in nsa5g mode (with volte).
        2. Make sure PhoneB is in 3g mode.
        3. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneA.
        4. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneB.
        5. Verify both PhoneA and PhoneB gets attached back to nsa5g

        Raises:
            TestFailure if not success.
        """
        ads = self.android_devices

        # LTE attach
        tasks = [(phone_setup_volte, (self.log, ads[0], GEN_5G, 'nsa')),
                 (phone_setup_voice_3g, (self.log, ads[1]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone failed to set up in volte/3g")
            return False

        # VoLTE to 3G
        result = two_phone_call_short_seq(
            self.log, ads[0], None, is_phone_in_call_volte, ads[1],
            None, is_phone_in_call_3g, None,
            WAIT_TIME_IN_CALL_FOR_IMS)
        if not result:
            self.log.error("Failure is volte to 3g call during 5g nsa")
            return False

        # Attach nsa5g
        if not is_current_network_5g(ads[0], nr_type = 'nsa'):
            ads[0].log.error("Phone not attached on 5g nsa after call end.")
            return False

        self.log.info("PASS - VoLTE to 3g over 5g nsa validated")
        return True


    @test_tracker_info(uuid="3a8147d6-c136-42cb-92ca-2023b8eed85e")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_volte_mo_hold_unhold(self):
        """ 5g nsa volte mo hold unhold test

        1. Make sure PhoneA is in nsa 5g (with volte)
        2. Make sure PhoneB is in nsa 5g (with volte)
        3. Call from PhoneA to PhoneB, accept on PhoneB
        4. Make sure PhoneA/B are in call
        5. Hold and unhold on PhoneA
        6. Verify both PhoneA and PhoneB gets attached back to nsa5g

        Raises:
            TestFailure if not success.
        """
        ads = self.android_devices
        if not provision_both_devices_for_volte(self.log, ads, GEN_5G,
                                                nr_type='nsa'):
            return False

        if not phone_setup_call_hold_unhold_test(self.log,
                                                 ads,
                                                 DIRECTION_MOBILE_ORIGINATED,
                                                 caller_func=is_phone_in_call_volte):
            return False

        if not verify_5g_attach_for_both_devices(self.log, ads, nr_type='nsa'):
            return False
        return True

    @test_tracker_info(uuid="1825f9d9-dcf1-4407-922d-3f218d5b8932")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_volte_mt_hold_unhold(self):
        """ 5g nsa volte mt hold unhold test

        1. Make sure PhoneA is in nsa 5g (with volte)
        2. Make sure PhoneB is in nsa 5g (with volte)
        3. Call from PhoneB to PhoneA, accept on PhoneA
        4. Make sure PhoneA/B are in call
        5. Hold and unhold on PhoneA
        6. Verify both PhoneA and PhoneB gets attached back to nsa5g

        Raises:
            TestFailure if not success.
        """
        ads = self.android_devices
        if not provision_both_devices_for_volte(self.log, ads, GEN_5G,
                                                nr_type='nsa'):
            return False

        if not phone_setup_call_hold_unhold_test(self.log,
                                                 ads,
                                                 DIRECTION_MOBILE_TERMINATED,
                                                 callee_func=is_phone_in_call_volte):
            return False

        if not verify_5g_attach_for_both_devices(self.log, ads, nr_type='nsa'):
            return False
        return True


    @test_tracker_info(uuid="c082a9b0-fb66-4d3a-9fdd-1ce5710624be")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_mo_volte_in_active_data_transfer(self):
        """Test call can be established during active data connection in 5G NSA.

        Turn off airplane mode, disable WiFi, enable Cellular Data.
        Make sure phone in 5G NSA.
        Starting downloading file from Internet.
        Initiate a MO voice call. Verify call can be established.
        Hangup Voice Call, verify file is downloaded successfully.
        Note: file download will be suspended when call is initiated if voice
              is using voice channel and voice channel and data channel are
              on different RATs.

        Returns:
            True if success.
            False if failed.
        """
        return test_call_setup_in_active_data_transfer(
            self.log,
            self.android_devices,
            rat='5g_volte',
            nr_type='nsa',
            call_direction=DIRECTION_MOBILE_ORIGINATED)


    @test_tracker_info(uuid="aaa98e51-0bde-472a-abc3-5dc180f56a08")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_mt_volte_in_active_data_transfer(self):
        """Test call can be established during active data connection in 5G NSA.

        Turn off airplane mode, disable WiFi, enable Cellular Data.
        Make sure phone in 5G NSA.
        Starting downloading file from Internet.
        Initiate a MT voice call. Verify call can be established.
        Hangup Voice Call, verify file is downloaded successfully.
        Note: file download will be suspended when call is initiated if voice
              is using voice channel and voice channel and data channel are
              on different RATs.

        Returns:
            True if success.
            False if failed.
        """
        return test_call_setup_in_active_data_transfer(
            self.log,
            self.android_devices,
            rat='5g_volte',
            nr_type='nsa',
            call_direction=DIRECTION_MOBILE_TERMINATED)


    @test_tracker_info(uuid="96b7d8c9-d32a-4abf-8326-6b060d116ac2")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_epdg_to_epdg_wfc_wifi_preferred(self):
        """ WiFi Preferred, WiFi calling to WiFi Calling test on 5G NSA

        1. Setup PhoneA WFC mode: WIFI_PREFERRED, APM off.
        2. Setup PhoneB WFC mode: WIFI_PREFERRED, APM off .
        3. Set PhoneA/PhoneB on 5G NSA
        4. Make sure PhoneA/PhoneB on 5G NSA before testing
        5. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneA.
        6. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneB.
        7. Make sure PhoneA/PhoneB on 5G NSA after testing

        Returns:
            True if pass; False if fail.
        """
        return call_epdg_to_epdg_wfc(self.log,
                                     self.android_devices,
                                     False,
                                     WFC_MODE_WIFI_PREFERRED,
                                     self.wifi_network_ssid,
                                     self.wifi_network_pass,
                                     GEN_5G)


    @test_tracker_info(uuid="29fa7f44-8d6a-4948-8178-33c9a9aab334")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_mo_volte_in_active_youtube(self):
        """Test call can be established during active youtube video on 5G NSA.

        1. Enable VoLTE on PhoneA.
        2. Set up PhoneA on 5G NSA.
        3. Make sure phoneA is on 5G NSA.
        4. Starting an youtube video.
        5. Initiate a MO voice call. Verify call can be established.
        6. Make sure phoneA is on 5G NSA.

        Returns:
            True if success.
            False if failed.
        """
        return test_call_setup_in_active_youtube_video(
            self.log,
            self.android_devices,
            rat='5g_volte',
            nr_type='nsa',
            call_direction=DIRECTION_MOBILE_ORIGINATED)

    @test_tracker_info(uuid="4e138477-3536-48bd-ab8a-7fb7c228b3e6")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_mt_volte_in_active_youtube(self):
        """Test call can be established during active youtube video on 5G NSA.

        1. Enable VoLTE on PhoneA.
        2. Set up PhoneA on 5G NSA.
        3. Make sure phoneA is on 5G NSA.
        4. Starting an youtube video.
        5. Initiate a MT voice call. Verify call can be established.
        6. Make sure phoneA is on 5G NSA.

        Returns:
            True if success.
            False if failed.
        """
        return test_call_setup_in_active_youtube_video(
            self.log,
            self.android_devices,
            rat='5g_volte',
            nr_type='nsa',
            call_direction=DIRECTION_MOBILE_TERMINATED)


    @test_tracker_info(uuid="0d477f6f-3464-4b32-a5e5-0fd134f2753d")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_mo_vowifi_in_active_data_transfer(self):
        """Test MO voice wifi call can be established during active data connection on 5G NSA.

        1. Turn off airplane mode, turn on wfc and wifi on phoneA.
        2. Set PhoneA on 5G NSA
        3. Make sure PhoneA on 5G NSA before testing
        4. Starting downloading file from Internet.
        5. Initiate a MO voice call. Verify call can be established.
        6. Hangup Voice Call, verify file is downloaded successfully.
        7. Make sure PhoneA on 5G NSA after testing

        Returns:
            True if success.
            False if failed.
        """
        return test_call_setup_in_active_data_transfer(
            self.log,
            self.android_devices,
            rat='5g_wfc',
            is_airplane_mode=False,
            wfc_mode=WFC_MODE_WIFI_PREFERRED,
            wifi_ssid=self.wifi_network_ssid,
            wifi_pwd=self.wifi_network_pass,
            nr_type='nsa',
            call_direction=DIRECTION_MOBILE_ORIGINATED)


    @test_tracker_info(uuid="4d1d7dd9-b373-4361-8301-8517ef77b57b")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_mt_vowifi_in_active_data_transfer(self):
        """Test MT voice wifi call can be established during active data connection on 5G NSA.

        1. Turn off airplane mode, turn on wfc and wifi on phoneA.
        2. Set PhoneA on 5G NSA
        3. Make sure PhoneA on 5G NSA before testing
        4. Starting downloading file from Internet.
        5. Initiate a MT voice call. Verify call can be established.
        6. Hangup Voice Call, verify file is downloaded successfully.
        7. Make sure PhoneA on 5G NSA after testing

        Returns:
            True if success.
            False if failed.
        """
        return test_call_setup_in_active_data_transfer(
            self.log,
            self.android_devices,
            rat='5g_wfc',
            is_airplane_mode=False,
            wfc_mode=WFC_MODE_WIFI_PREFERRED,
            wifi_ssid=self.wifi_network_ssid,
            wifi_pwd=self.wifi_network_pass,
            nr_type='nsa',
            call_direction=DIRECTION_MOBILE_TERMINATED)


    @test_tracker_info(uuid="e360bc3a-96b3-4fdf-9bf3-fe3aa08b1af5")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_epdg_mo_hold_unhold_wfc_wifi_preferred(self):
        """ WiFi Preferred, WiFi calling MO call hold/unhold test on 5G NSA

        1. Setup PhoneA WFC mode: WIFI_PREFERRED.
        2. Set preferred network of phoneA on 5G NSA
        3. Verify phoneA is on 5G NSA.
        4. Call from PhoneA to PhoneB, accept on PhoneB.
        5. Hold and unhold on PhoneA.
        6. Verify phoneA is on 5G NSA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        if not phone_setup_iwlan(self.log, ads[0], False,
                                 WFC_MODE_WIFI_PREFERRED,
                                 self.wifi_network_ssid,
                                 self.wifi_network_pass):
            ads[0].log.error(
                "Failed to setup iwlan with APM off and WIFI and WFC on")
            return False

        if not provision_device_for_5g(self.log, ads[0], nr_type='nsa'):
            return False

        if not phone_setup_call_hold_unhold_test(self.log,
                                                 ads,
                                                 DIRECTION_MOBILE_ORIGINATED,
                                                 caller_func=is_phone_in_call_iwlan):
            return False

        if not is_current_network_5g(ads[0], nr_type = 'nsa'):
            ads[0].log.error("Phone not attached on 5G NSA after call.")
            return False
        return True


    @test_tracker_info(uuid="d2335c83-87ec-4a0e-97a8-b53f769b0d21")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_epdg_mt_hold_unhold_wfc_wifi_preferred(self):
        """ WiFi Preferred, WiFi calling MT call hold/unhold test on 5G NSA

        1. Setup PhoneA WFC mode: WIFI_PREFERRED.
        2. Set preferred network of phoneA on 5G NSA
        3. Verify if phoneA is on 5G NSA.
        4. Call from PhoneB to PhoneA, accept on PhoneA.
        5. Hold and unhold on PhoneA.
        6. Verify if phoneA is on 5G NSA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        if not phone_setup_iwlan(self.log, ads[0], False,
                                 WFC_MODE_WIFI_PREFERRED,
                                 self.wifi_network_ssid,
                                 self.wifi_network_pass):
            ads[0].log.error(
                "Failed to setup iwlan with APM off and WIFI and WFC on")
            return False

        if not provision_device_for_5g(self.log, ads[0], nr_type='nsa'):
            return False

        if not phone_setup_call_hold_unhold_test(self.log,
                                                 ads,
                                                 DIRECTION_MOBILE_TERMINATED,
                                                 callee_func=is_phone_in_call_iwlan):
            return False

        if not is_current_network_5g(ads[0], nr_type = 'nsa'):
            ads[0].log.error("Phone not attached on 5G NSA after call.")
            return False
        return True


    @test_tracker_info(uuid="03dc3296-5f2f-4141-b2e5-bb0d8ea6ffec")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_volte_to_csfb_3g(self):
        """ VoLTE nsa 5G to CSFB 3G call test

        1. Make Sure PhoneA is in nsa 5G mode (with VoLTE).
        2. Make Sure PhoneB is in CSFB mode (without VoLTE).
        3. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneA.
        4. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneB.

        Raises:
            TestFailure if not success.
        """
        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0])), (phone_setup_csfb,
                                                           (self.log, ads[1]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone failed to set up in VoLTE/CSFB")
            return False

        if not provision_device_for_5g(self.log, ads[0], nr_type='nsa'):
            return False

        result = two_phone_call_short_seq(
            self.log, ads[0], None, is_phone_in_call_volte, ads[1],
            phone_idle_csfb, is_phone_in_call_csfb, None)
        self.tel_logger.set_result(result.result_value)
        if not result:
            raise signals.TestFailure("Failed",
                extras={"fail_reason": str(result.result_value)})


    @test_tracker_info(uuid="1955604b-5b81-4b06-b846-ba55b46ea997")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_volte_to_2g(self):
        """ nsa 5G to 2G call test (with VoLTE)

        1. Make Sure PhoneA is in nsa 5G mode (with VoLTE).
        2. Make Sure PhoneB is in 2G mode.
        3. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneA.
        4. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneB.

        Raises:
            TestFailure if not success.
        """
        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0])),
                 (phone_setup_voice_2g, (self.log, ads[1]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone failed to set up in VoLTE/2G")
            return False

        if not provision_device_for_5g(self.log, ads[0], nr_type='nsa'):
            return False

        result = two_phone_call_short_seq(
            self.log, ads[0], None, is_phone_in_call_volte, ads[1],
            phone_idle_2g, is_phone_in_call_2g, None)
        self.tel_logger.set_result(result.result_value)
        if not result:
            raise signals.TestFailure("Failed",
                extras={"fail_reason": str(result.result_value)})


    @test_tracker_info(uuid="547d0b7e-8130-4166-bd57-af8257d5d0c4")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_epdg_to_volte_wfc_wifi_preferred(self):
        """ WiFi Preferred, WiFi calling to nsa5g VoLTE test

        1. Setup PhoneA WFC mode: WIFI_PREFERRED.
        2. Make Sure PhoneB is in nsa5G mode (with VoLTE enabled).
        3. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneA.
        4. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneB.

        Raises:
            TestFailure if not success.
        """
        ads = self.android_devices
        tasks = [(phone_setup_iwlan,
                  (self.log, ads[0], False, WFC_MODE_WIFI_PREFERRED,
                   self.wifi_network_ssid, self.wifi_network_pass)),
                 (phone_setup_volte, (self.log, ads[1]))]

        if not multithread_func(self.log, tasks):
            self.log.error("Phone failed to set up in VoLTE")
            return False

        if not provision_device_for_5g(self.log, ads[1], nr_type='nsa'):
            return False

        result = two_phone_call_short_seq(
            self.log, ads[0], phone_idle_iwlan, is_phone_in_call_iwlan, ads[1],
            None, is_phone_in_call_volte, None,
            WAIT_TIME_IN_CALL_FOR_IMS)
        self.tel_logger.set_result(result.result_value)
        if not result:
            raise signals.TestFailure("Failed",
                extras={"fail_reason": str(result.result_value)})


    @test_tracker_info(uuid="119b54f8-fe4f-4158-baa3-c869a7ce12b9")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_epdg_to_volte_apm_wfc_wifi_only(self):
        """ Airplane + WiFi Only, WiFi calling to nsa5g VoLTE test

        1. Setup PhoneA in airplane mode, WFC mode: WIFI_ONLY.
        2. Make Sure PhoneB is in nsa 5G mode (with VoLTE enabled).
        3. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneA.
        4. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneB.

        Raises:
            TestFailure if not success.
        """
        ads = self.android_devices
        tasks = [(phone_setup_iwlan,
                  (self.log, ads[0], True, WFC_MODE_WIFI_ONLY,
                   self.wifi_network_ssid, self.wifi_network_pass)),
                 (phone_setup_volte, (self.log, ads[1]))]

        if not multithread_func(self.log, tasks):
            self.log.error("Phone failed to set up in VoLTE")
            return False

        if not provision_device_for_5g(self.log, ads[1], nr_type='nsa'):
            return False

        result = two_phone_call_short_seq(
            self.log, ads[0], phone_idle_iwlan, is_phone_in_call_iwlan, ads[1],
            None, is_phone_in_call_volte, None,
            WAIT_TIME_IN_CALL_FOR_IMS)
        self.tel_logger.set_result(result.result_value)

        toggle_airplane_mode(self.log, ads[0], False)

        if not result:
            raise signals.TestFailure("Failed",
                extras={"fail_reason": str(result.result_value)})


    @test_tracker_info(uuid="cb2eac2b-fc48-409d-bff2-a06849d6cd1a")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_epdg_to_volte_apm_wfc_wifi_preferred(self):
        """ Airplane + WiFi Preferred, WiFi calling to nsa5g VoLTE test

        1. Setup PhoneA in airplane mode, WFC mode: WIFI_PREFERRED.
        2. Make Sure PhoneB is in nsa 5G mode (with VoLTE enabled).
        3. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneA.
        4. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneB.

        Raises:
            TestFailure if not success.
        """
        ads = self.android_devices
        tasks = [(phone_setup_iwlan,
                  (self.log, ads[0], True, WFC_MODE_WIFI_PREFERRED,
                   self.wifi_network_ssid, self.wifi_network_pass)),
                 (phone_setup_volte, (self.log, ads[1]))]

        if not multithread_func(self.log, tasks):
            self.log.error("Phone failed to set up in VoLTE")
            return False

        if not provision_device_for_5g(self.log, ads[1], nr_type='nsa'):
            return False

        result = two_phone_call_short_seq(
            self.log, ads[0], phone_idle_iwlan, is_phone_in_call_iwlan, ads[1],
            None, is_phone_in_call_volte, None,
            WAIT_TIME_IN_CALL_FOR_IMS)
        self.tel_logger.set_result(result.result_value)

        toggle_airplane_mode(self.log, ads[0], False)

        if not result:
            raise signals.TestFailure("Failed",
                extras={"fail_reason": str(result.result_value)})


    @test_tracker_info(uuid="c06d6c81-ce26-474d-b41e-fcfe1ec6e761")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_volte_to_volte_long(self):
        """ VoLTE to nsa 5G VoLTE call test

        1. Make Sure PhoneA is in LTE mode (with VoLTE).
        2. Make Sure PhoneB is in nsa 5G mode (with VoLTE).
        3. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneA.
        4. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneB.
        5. Call from PhoneB to PhoneA, accept on PhoneA, hang up on PhoneB.
        6. Call from PhoneB to PhoneA, accept on PhoneA, hang up on PhoneA.

        Raises:
            TestFailure if not success.
        """
        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0])),
                (phone_setup_volte, (self.log, ads[1], GEN_5G, 'nsa'))]
        if not multithread_func(self.log, tasks):
            self.log.error("phone failed to set up in volte")
            return False

        result = two_phone_call_long_seq(
            self.log, ads[0], phone_idle_volte, is_phone_in_call_volte, ads[1],
            None, is_phone_in_call_volte, None,
            WAIT_TIME_IN_CALL_FOR_IMS)
        self.tel_logger.set_result(result.result_value)
        if not result:
            raise signals.TestFailure("Failed",
                extras={"fail_reason": str(result.result_value)})


    @test_tracker_info(uuid="3c45ab10-9e8c-4804-9f32-c775baa3d5e7")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_volte_to_volte_loop(self):
        """ Stress test: VoLTE to nsa 5g VoLTE call test

        1. Make Sure PhoneA is in LTE mode (with VoLTE).
        2. Make Sure PhoneB is in nsa 5G mode (with VoLTE).
        3. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneA.
        4. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneB.
        5. Call from PhoneB to PhoneA, accept on PhoneA, hang up on PhoneB.
        6. Call from PhoneB to PhoneA, accept on PhoneA, hang up on PhoneA.
        7. Repeat step 3~6.

        Returns:
            True if pass; False if fail.
        """

        MINIMUM_SUCCESS_RATE = .95
        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0])),
                (phone_setup_volte, (self.log, ads[1], GEN_5G, 'nsa'))]
        if not multithread_func(self.log, tasks):
            self.log.error("phone failed to set up in volte")
            return False

        success_count = 0
        fail_count = 0

        for i in range(1, self.stress_test_number + 1):

            if two_phone_call_long_seq(
                    self.log, ads[0], phone_idle_volte, is_phone_in_call_volte,
                    ads[1], None, is_phone_in_call_volte, None,
                    WAIT_TIME_IN_CALL_FOR_IMS):
                success_count += 1
                result_str = "Succeeded"

            else:
                fail_count += 1
                result_str = "Failed"

            self.log.info("Iteration %s %s. Current: %s / %s passed.", i,
                          result_str, success_count, self.stress_test_number)

        self.log.info("Final Count - Success: %s, Failure: %s - %s%",
                      success_count, fail_count,
                      str(100 * success_count / (success_count + fail_count)))
        if success_count / (
                success_count + fail_count) >= MINIMUM_SUCCESS_RATE:
            return True
        else:
            return False


    @test_tracker_info(uuid="875292c8-14f8-43aa-8770-3d0099da9c53")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_voicemail_indicator_volte(self):
        """Test Voice Mail notification in nsa 5G (VoLTE enabled).
        This script currently only works for TMO now.

        1. Make sure DUT (ads[0]) in nsa 5g VoLTE mode. Both PhoneB and DUT idle.
        2. Make call from PhoneB to DUT, reject on DUT.
        3. On PhoneB, leave a voice mail to DUT.
        4. Verify DUT receive voice mail notification.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        tasks = [(phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_volte, (self.log, ads[0]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        if not provision_device_for_5g(self.log, ads[0], nr_type='nsa'):
            return False

        if not call_voicemail_erase_all_pending_voicemail(self.log, ads[0]):
            self.log.error("Failed to clear voice mail.")
            return False

        return two_phone_call_leave_voice_mail(self.log, ads[1], None, None,
                                               ads[0], phone_idle_volte)


    @test_tracker_info(uuid="b866dcfa-f133-424d-bb5d-f381a1f63602")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_long_duration_volte(self):
        """ Test call drop rate for nsa 5g VoLTE long duration call.

        Steps:
        1. Setup nsa 5g VoLTE for DUT.
        2. Make nsa 5g VoLTE call from DUT to PhoneB.
        3. For <total_duration> time, check if DUT drop call or not.

        Expected Results:
        DUT should not drop call.

        Returns:
        False if DUT call dropped during test.
        Otherwise True.
        """
        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0])),
                 (phone_setup_voice_general, (self.log, ads[1]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        if not provision_device_for_5g(self.log, ads[0], nr_type='nsa'):
            return False

        return _test_call_long_duration(self.log, ads,
            is_phone_in_call_volte, self.long_call_duration)


    @test_tracker_info(uuid="527e6676-25c7-4adc-94e7-bb6d3b8f6c24")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_volte_after_mobile_data_usage_limit_reached(self):
        """ VoLTE to VoLTE call test after mobile data usage limit reached

        1. Set the data limit to the current usage
        2. Make Sure PhoneA is in nsa 5G mode (with VoLTE).
        3. Make Sure PhoneB is in nsa 5G mode (with VoLTE).
        4. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneA.
        5. Call from PhoneA to PhoneB, accept on PhoneB, hang up on PhoneB.

        Raises:
            TestFailure if not success.
        """
        ads = self.android_devices
        try:
            subscriber_id = ads[0].droid.telephonyGetSubscriberId()
            data_usage = get_mobile_data_usage(ads[0], subscriber_id)
            set_mobile_data_usage_limit(ads[0], data_usage, subscriber_id)

            if not provision_device_for_5g(self.log, ads, nr_type='nsa'):
                self.log.error("Phone Failed to Set Up Properly.")
                self.tel_logger.set_result(CallResult("CALL_SETUP_FAILURE"))
                raise signals.TestFailure("Failed",
                    extras={"fail_reason": "Phone Failed to Set Up Properly."})

            result = two_phone_call_short_seq(
                self.log, ads[0], None, is_phone_in_call_volte,
                ads[1], None, is_phone_in_call_volte, None,
                WAIT_TIME_IN_CALL_FOR_IMS)
            self.tel_logger.set_result(result.result_value)

            if not result:
                raise signals.TestFailure("Failed",
                    extras={"fail_reason": str(result.result_value)})
        finally:
            remove_mobile_data_usage_limit(ads[0], subscriber_id)


    @test_tracker_info(uuid="2c28a974-dc31-4f01-b555-d3e0a8374f37")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_volte_in_call_wifi_toggling(self):
        """ General voice to voice call.

        1. Make Sure PhoneA in nsa5G.
        2. Make Sure PhoneB in nsa5G.
        3. Call from PhoneA to PhoneB.
        4. Toggling Wifi connnection in call.
        5. Verify call is active.
        6. Hung up the call on PhoneA

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        result = True

        if not provision_device_for_5g(self.log, ads, nr_type='nsa'):
                self.log.error("Phone Failed to Set Up Properly.")
                self.tel_logger.set_result(CallResult("CALL_SETUP_FAILURE"))
                raise signals.TestFailure("Failed",
                    extras={"fail_reason": "Phone Failed to Set Up Properly."})

        if not call_setup_teardown(self.log, ads[0], ads[1], None, None, None,
                                   5):
            self.log.error("Call setup failed")
            return False
        else:
            self.log.info("Call setup succeed")

        if not wifi_cell_switching(self.log, ads[0], None, self.wifi_network_ssid,
                                   self.wifi_network_pass):
            ads[0].log.error("Failed to do WIFI and Cell switch in call")
            result = False

        if not is_phone_in_call_active(ads[0]):
            return False
        else:
            if not ads[0].droid.telecomCallGetAudioState():
                ads[0].log.error("Audio is not on call")
                result = False
            else:
                ads[0].log.info("Audio is on call")
            hangup_call(self.log, ads[0])
            return result

    @test_tracker_info(uuid="95802175-06d5-4774-8ce8-fdf7922eca20")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_mo_vowifi_in_active_youtube(self):
        """Test call can be established during active youtube video on 5G NSA.

        Turn off airplane mode, turn on wfc and wifi.
        Starting youtube video.
        Initiate a MO voice call. Verify call can be established.

        Returns:
            True if success.
            False if failed.
        """
        return test_call_setup_in_active_youtube_video(
            self.log,
            self.android_devices,
            rat='5g_wfc',
            is_airplane_mode=False,
            wfc_mode=WFC_MODE_WIFI_PREFERRED,
            wifi_ssid=self.wifi_network_ssid,
            wifi_pwd=self.wifi_network_pass,
            nr_type='nsa',
            call_direction=DIRECTION_MOBILE_ORIGINATED)

    @test_tracker_info(uuid="f827a8b5-039c-4cc1-b030-78a09119acfc")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_mt_vowifi_in_active_youtube(self):
        """Test call can be established during active youtube_video on 5G NSA.

        Turn off airplane mode, turn on wfc and wifi.
        Starting an youtube video.
        Initiate a MT voice call. Verify call can be established.

        Returns:
            True if success.
            False if failed.
        """
        return test_call_setup_in_active_youtube_video(
            self.log,
            self.android_devices,
            rat='5g_wfc',
            is_airplane_mode=False,
            wfc_mode=WFC_MODE_WIFI_PREFERRED,
            wifi_ssid=self.wifi_network_ssid,
            wifi_pwd=self.wifi_network_pass,
            nr_type='nsa',
            call_direction=DIRECTION_MOBILE_TERMINATED)

    @test_tracker_info(uuid="af3254d0-a84a-47c8-8ebc-11517b7b4944")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_mo_vowifi_apm_in_active_data_transfer(self):
        """Test call can be established during active data connection on 5G NSA.

        Turn on wifi-calling, airplane mode and wifi.
        Starting downloading file from Internet.
        Initiate a MO voice call. Verify call can be established.
        Hangup Voice Call, verify file is downloaded successfully.

        Returns:
            True if success.
            False if failed.
        """
        return test_call_setup_in_active_data_transfer(
            self.log,
            self.android_devices,
            rat='5g_wfc',
            is_airplane_mode=True,
            wfc_mode=WFC_MODE_WIFI_PREFERRED,
            wifi_ssid=self.wifi_network_ssid,
            wifi_pwd=self.wifi_network_pass,
            nr_type='nsa',
            call_direction=DIRECTION_MOBILE_ORIGINATED)

    @test_tracker_info(uuid="5c58af94-8c24-481b-a555-bdbf36db5f6e")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_mt_vowifi_apm_in_active_data_transfer(self):
        """Test call can be established during active data connection on 5G NSA.

        Turn on wifi-calling, airplane mode and wifi.
        Starting downloading file from Internet.
        Initiate a MT voice call. Verify call can be established.
        Hangup Voice Call, verify file is downloaded successfully.

        Returns:
            True if success.
            False if failed.
        """
        return test_call_setup_in_active_data_transfer(
            self.log,
            self.android_devices,
            rat='5g_wfc',
            is_airplane_mode=True,
            wfc_mode=WFC_MODE_WIFI_PREFERRED,
            wifi_ssid=self.wifi_network_ssid,
            wifi_pwd=self.wifi_network_pass,
            nr_type='nsa',
            call_direction=DIRECTION_MOBILE_TERMINATED)

    @test_tracker_info(uuid="bcd874ae-58e1-4954-88af-bb3dd54d4abf")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_mo_vowifi_apm_in_active_youtube(self):
        """Test call can be established during active youtube video on 5G NSA.

        Turn on wifi-calling, airplane mode and wifi.
        Starting an youtube video.
        Initiate a MO voice call. Verify call can be established.

        Returns:
            True if success.
            False if failed.
        """
        return test_call_setup_in_active_youtube_video(
            self.log,
            self.android_devices,
            rat='5g_wfc',
            is_airplane_mode=True,
            wfc_mode=WFC_MODE_WIFI_PREFERRED,
            wifi_ssid=self.wifi_network_ssid,
            wifi_pwd=self.wifi_network_pass,
            nr_type='nsa',
            call_direction=DIRECTION_MOBILE_ORIGINATED)

    @test_tracker_info(uuid="ad96f1cf-0d17-4a39-86cf-cacb5f4cc81c")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_mt_vowifi_apm_in_active_youtube(self):
        """Test call can be established during active youtube video on 5G NSA.

        Turn on wifi-calling, airplane mode and wifi.
        Starting youtube video.
        Initiate a MT voice call. Verify call can be established.

        Returns:
            True if success.
            False if failed.
        """
        return test_call_setup_in_active_youtube_video(
            self.log,
            self.android_devices,
            rat='5g_wfc',
            is_airplane_mode=True,
            wfc_mode=WFC_MODE_WIFI_PREFERRED,
            wifi_ssid=self.wifi_network_ssid,
            wifi_pwd=self.wifi_network_pass,
            nr_type='nsa',
            call_direction=DIRECTION_MOBILE_TERMINATED)

    @test_tracker_info(uuid="9d1121c1-aae4-428b-9167-09d4efdb7e37")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_call_wfc_in_call_wifi_toggling(self):
        """ General voice to voice call on 5G NSA. TMO Only Test

        1. Make Sure PhoneA in wfc with APM off.
        2. Make Sure PhoneB in Voice Capable.
        3. Call from PhoneA to PhoneB.
        4. Toggling Wifi connection in call.
        5. Verify call is active.
        6. Hung up the call on PhoneA

        Returns:
            True if pass; False if fail.
        """

        ads = self.android_devices

        if not phone_setup_volte(
                self.log, ads[0], nw_gen=GEN_5G, nr_type='nsa'):
            return False
        tasks = [(phone_setup_iwlan,
                  (self.log, ads[0], False, WFC_MODE_WIFI_PREFERRED,
                   self.wifi_network_ssid, self.wifi_network_pass)),
                 (phone_setup_voice_general, (self.log, ads[1]))]

        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False
        return test_wifi_cell_switching_in_call(self.log,
                                                ads,
                                                self.wifi_network_ssid,
                                                self.wifi_network_pass)

    @test_tracker_info(uuid="e42cb2bc-db0b-4053-a052-7d95e55bc815")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_volte_call_during_data_idle_and_transfer_mo(self):
        """Test 5G NSA for VoLTE call during data idle and data transfer.

        Steps:
	    (1) Provision both devices on 5G NSA.
	    (2) Initiate MO VoLTE call during data idle.
	    (3) End call.
	    (4) Initiate a MO VoLTE call and start a download.
	    (5) Start another download and initiate MO VoLTE call during data transferring.
	    (6) End call.
	    (7) Initiate a MO VoLTE call and start a download.

        Returns:
            True if pass; False if fail.
        """
        cell_1 = self.android_devices[0]
        cell_2 = self.android_devices[1]

        if not provision_device_for_5g(self.log, [cell_1, cell_2], nr_type='nsa'):
            cell_1.log.error("Failed to setup on 5G NSA")
            return False

        # Initiate call during data idle and end call
        if not initiate_call_verify_operation(self.log, cell_1, cell_2):
            cell_1.log.error("Phone was unable to initate a call")
            return False

        # Initiate call and start a download
        if not initiate_call_verify_operation(self.log, cell_1, cell_2, True):
            cell_1.log.error("Phone was unable to initate a call and verify download")
            return False

        download_task = active_file_download_task(self.log, cell_1, "10MB")
        call_task = (initiate_call_verify_operation, (self.log, cell_1, cell_2))

        results = run_multithread_func(self.log, [download_task, call_task])

        if ((results[0]) & (results[1])):
            self.log.info("PASS - Validate VoLTE call during data transferring")
        elif ((results[0] == False) & (results[1] == True)):
            self.log.error("FAIL - Data Transfer failed")
        elif ((results[0] == True) & (results[1] == False)):
            self.log.error("FAIL - Call Initiation failed")
        else:
            self.log.error("FAILED - Validate VoLTE call during data transferring")

        if not initiate_call_verify_operation(self.log, cell_1, cell_2, True):
            cell_1.log.error("Phone was unable to initate a call and verify download")
            return False


    @test_tracker_info(uuid="c69ec37d-133f-42c5-babd-91f763dd5b21")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_volte_call_during_data_idle_and_transfer_mt(self):
        """Test 5G NSA for VoLTE call during data idle and data transfer.

        Steps:
	    (1) Provision both devices on 5G NSA.
	    (2) Initiate MT VoLTE call during data idle.
	    (3) End call.
	    (4) Initiate a MO VoLTE call and start a download.
	    (5) Start another download and initiate MT VoLTE call during data transferring.
	    (6) End call.
	    (7) Initiate a MO VoLTE call and start a download.

        Returns:
            True if pass; False if fail.
        """
        cell_1 = self.android_devices[0]
        cell_2 = self.android_devices[1]

        if not provision_device_for_5g(self.log, [cell_1, cell_2], nr_type='nsa'):
            cell_1.log.error("Failed to setup on 5G NSA")
            return False

        # Initiate call during data idle and end call
        if not initiate_call_verify_operation(self.log, cell_2, cell_1):
            cell_2.log.error("Phone was unable to initate a call")
            return False

        # Initiate call and start a download
        if not initiate_call_verify_operation(self.log, cell_1, cell_2, True):
            cell_1.log.error("Phone was unable to initate a call and verify download")
            return False

        download_task = active_file_download_task(self.log, cell_2, "10MB")
        call_task = (initiate_call_verify_operation, (self.log, cell_2, cell_1))

        results = run_multithread_func(self.log, [download_task, call_task])

        if ((results[0]) & (results[1])):
            self.log.info("PASS - Validate MT VoLTE call during data transferring")
        elif ((results[0] == False) & (results[1] == True)):
            self.log.error("FAIL - Data Transfer failed")
        elif ((results[0] == True) & (results[1] == False)):
            self.log.error("FAIL - Call Initiation failed")
        else:
            self.log.error("FAILED - Validate MT VoLTE call during data transferring")

        if not initiate_call_verify_operation(self.log, cell_1, cell_2, True):
            cell_1.log.error("Phone was unable to initate a call and verify download")
            return False

    """ Tests End """
