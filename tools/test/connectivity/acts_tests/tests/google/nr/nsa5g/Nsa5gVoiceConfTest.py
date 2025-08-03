#!/usr/bin/env python3.4
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
"""
    Test Script for 5G Voice Conference scenarios
"""

from acts import signals
from acts.test_decorators import test_tracker_info
from acts.libs.utils.multithread import multithread_func
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import CALL_STATE_ACTIVE
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_CONFERENCE
from acts_contrib.test_utils.tel.tel_defines import GEN_5G
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_device_for_5g
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_voice_general
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_voice_3g
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte
from acts_contrib.test_utils.tel.tel_ss_utils import three_phone_call_forwarding_short_seq
from acts_contrib.test_utils.tel.tel_ss_utils import three_phone_call_waiting_short_seq
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_voice_sub_id
from acts_contrib.test_utils.tel.tel_test_utils import get_capability_for_subscription
from acts_contrib.test_utils.tel.tel_test_utils import install_dialer_apk
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_volte
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_wcdma
from acts_contrib.test_utils.tel.tel_voice_conf_utils import _get_expected_call_state
from acts_contrib.test_utils.tel.tel_voice_conf_utils import _test_ims_conference_merge_drop_first_call_from_host
from acts_contrib.test_utils.tel.tel_voice_conf_utils import _test_ims_conference_merge_drop_first_call_from_participant
from acts_contrib.test_utils.tel.tel_voice_conf_utils import _test_ims_conference_merge_drop_second_call_from_host
from acts_contrib.test_utils.tel.tel_voice_conf_utils import _test_ims_conference_merge_drop_second_call_from_participant
from acts_contrib.test_utils.tel.tel_voice_conf_utils import _test_call_mo_mo_add_swap_x
from acts_contrib.test_utils.tel.tel_voice_conf_utils import _test_call_mo_mt_add_swap_x
from acts_contrib.test_utils.tel.tel_voice_conf_utils import _test_call_mt_mt_add_swap_x
from acts_contrib.test_utils.tel.tel_voice_conf_utils import _three_phone_hangup_call_verify_call_state


class Nsa5gVoiceConfTest(TelephonyBaseTest):
    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        if not get_capability_for_subscription(
                self.android_devices[0], CAPABILITY_CONFERENCE,
                get_outgoing_voice_sub_id(self.android_devices[0])):
            self.android_devices[0].log.error(
                "Conference call is not supported, abort test.")
            raise signals.TestAbortClass(
                "Conference call is not supported, abort test.")

        self.dialer_util = self.user_params.get("dialer_apk", None)
        if isinstance(self.dialer_util, list):
            self.dialer_util = self.dialer_util[0]

        if self.dialer_util:
            ads = self.android_devices
            for ad in ads:
                install_dialer_apk(ad, self.dialer_util)

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)

    """ Tests Begin """

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="68a8be5b-a5e0-4475-a5bd-42adfdb6535f")
    def test_5g_nsa_volte_mo_mo_add_5g_nsa_volte_merge_drop_second_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneA (nsa 5G VoLTE) to PhoneC (nsa 5G VoLTE), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneC, verify call continues.
        5. End call on PhoneB, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="bd828db0-dcc5-4d49-b049-48dd76f5f2f0")
    def test_5g_nsa_volte_mo_mo_add_5g_nsa_volte_merge_drop_second_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneA (nsa 5G VoLTE) to PhoneC (nsa 5G VoLTE), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-C, verify call continues.
        5. On PhoneA disconnect call between A-B, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="5c3a3370-094d-43ff-b024-58a8a2983274")
    def test_5g_nsa_volte_mo_mo_add_5g_nsa_volte_merge_drop_first_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneA (nsa 5G VoLTE) to PhoneC (nsa 5G VoLTE), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneB, verify call continues.
        5. End call on PhoneC, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="9ae61738-925b-44f8-b3a7-0e58f2ebd6ec")
    def test_5g_nsa_volte_mo_mo_add_5g_nsa_volte_merge_drop_first_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneA (nsa 5G VoLTE) to PhoneC (nsa 5G VoLTE), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-B, verify call continues.
        5. On PhoneA disconnect call between A-C, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="e3488699-9eb1-4a66-a2bd-b229c51e63c9")
    def test_5g_nsa_volte_mo_mt_add_5g_nsa_volte_merge_drop_second_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneC, verify call continues.
        5. End call on PhoneB, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="15b23934-ac8c-4c3a-9967-c19e46c0d86b")
    def test_5g_nsa_volte_mo_mt_add_5g_nsa_volte_merge_drop_second_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-C, verify call continues.
        5. On PhoneA disconnect call between A-B, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="2d0b60db-44cd-48c2-8361-6a3af051eb22")
    def test_5g_nsa_volte_mo_mt_add_5g_nsa_volte_merge_drop_first_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneB, verify call continues.
        5. End call on PhoneC, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="c6b47773-70f2-413c-8f6c-29dc4abce44f")
    def test_5g_nsa_volte_mo_mt_add_5g_nsa_volte_merge_drop_first_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-B, verify call continues.
        5. On PhoneA disconnect call between A-C, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="47fe393b-9605-4c52-b322-c3baeb6aae3e")
    def test_5g_nsa_volte_mt_mt_add_5g_nsa_volte_merge_drop_second_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (nsa 5G VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneC, verify call continues.
        5. End call on PhoneB, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="7d633e59-2918-4e7c-b01d-576bc2f31ab3")
    def test_5g_nsa_volte_mt_mt_add_5g_nsa_volte_merge_drop_second_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (nsa 5G VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-C, verify call continues.
        5. On PhoneA disconnect call between A-B, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="23faa690-e96f-40bc-ab2c-7c4e51f1323c")
    def test_5g_nsa_volte_mt_mt_add_5g_nsa_volte_merge_drop_first_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (nsa 5G VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneB, verify call continues.
        5. End call on PhoneC, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="67dff58b-8111-41ba-9b8e-f06056b0db0a")
    def test_5g_nsa_volte_mt_mt_add_5g_nsa_volte_merge_drop_first_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (nsa 5G VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-B, verify call continues.
        5. On PhoneA disconnect call between A-C, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="4cf986be-d63c-472e-9702-9a30bf0d4f79")
    def test_5g_nsa_volte_mo_mo_add_4g_volte_merge_drop_second_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (VoLTE), accept on PhoneB.
        2. Call from PhoneA (nsa 5G VoLTE) to PhoneC (VoLTE), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneC, verify call continues.
        5. End call on PhoneB, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="d77bd012-390d-433a-a705-b0a4d263d73c")
    def test_5g_nsa_volte_mo_mo_add_4g_volte_merge_drop_second_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (VoLTE), accept on PhoneB.
        2. Call from PhoneA (nsa 5G VoLTE) to PhoneC (VoLTE), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-C, verify call continues.
        5. On PhoneA disconnect call between A-B, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="3d3945bc-39c3-42dc-9deb-a3f7fb46bba9")
    def test_5g_nsa_volte_mo_mo_add_4g_volte_merge_drop_first_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (VoLTE), accept on PhoneB.
        2. Call from PhoneA (nsa 5G VoLTE) to PhoneC (VoLTE), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneB, verify call continues.
        5. End call on PhoneC, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="6bce0033-da88-4601-bc2f-7fd930b4cc6f")
    def test_5g_nsa_volte_mo_mo_add_4g_volte_merge_drop_first_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (VoLTE), accept on PhoneB.
        2. Call from PhoneA (nsa 5G VoLTE) to PhoneC (VoLTE), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-B, verify call continues.
        5. On PhoneA disconnect call between A-C, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="c76be000-eda1-452e-a813-5f2b3c1ea85d")
    def test_5g_nsa_volte_mo_mt_add_4g_volte_merge_drop_second_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (VoLTE), accept on PhoneB.
        2. Call from PhoneC (VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneC, verify call continues.
        5. End call on PhoneB, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="ea104940-872a-4cf7-a47c-db620762c818")
    def test_5g_nsa_volte_mo_mt_add_4g_volte_merge_drop_second_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (VoLTE), accept on PhoneB.
        2. Call from PhoneC (VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-C, verify call continues.
        5. On PhoneA disconnect call between A-B, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="1dde3ce7-cd02-4434-99b9-af025b5ada05")
    def test_5g_nsa_volte_mo_mt_add_4g_volte_merge_drop_first_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (VoLTE), accept on PhoneB.
        2. Call from PhoneC (VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneB, verify call continues.
        5. End call on PhoneC, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="3ae5aee3-94c2-40ad-b8a1-c3e05a6c8365")
    def test_5g_nsa_volte_mo_mt_add_4g_volte_merge_drop_first_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5G VoLTE) to PhoneB (VoLTE), accept on PhoneB.
        2. Call from PhoneC (VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-B, verify call continues.
        5. On PhoneA disconnect call between A-C, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="f45fb3c8-40b5-4731-9743-c6c63460ff9e")
    def test_5g_nsa_volte_mt_mt_add_4g_volte_merge_drop_second_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        2. Call from PhoneC (VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneC, verify call continues.
        5. End call on PhoneB, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="1ae31dcf-b5e5-44f1-aec7-472fa6300c16")
    def test_5g_nsa_volte_mt_mt_add_4g_volte_merge_drop_second_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        2. Call from PhoneC (VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-C, verify call continues.
        5. On PhoneA disconnect call between A-B, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="b35ec011-e98c-4b79-98ea-7d6acf63c3fe")
    def test_5g_nsa_volte_mt_mt_add_4g_volte_merge_drop_first_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        2. Call from PhoneC (VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneB, verify call continues.
        5. End call on PhoneC, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="22db778c-bedb-4099-8764-f2832d87dcd9")
    def test_5g_nsa_volte_mt_mt_add_4g_volte_merge_drop_first_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        2. Call from PhoneC (VoLTE) to PhoneA (nsa 5G VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-B, verify call continues.
        5. On PhoneA disconnect call between A-C, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="e98ca225-7c65-4c61-bde7-9c3ef350dc30")
    def test_4g_volte_mo_mo_add_5g_nsa_volte_merge_drop_second_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneA (VoLTE) to PhoneC (nsa 5G VoLTE), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneC, verify call continues.
        5. End call on PhoneB, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="3d7cf9ce-7658-46f7-a852-c429cd772bda")
    def test_4g_volte_mo_mo_add_5g_nsa_volte_merge_drop_second_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneA (VoLTE) to PhoneC (nsa 5G VoLTE), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-C, verify call continues.
        5. On PhoneA disconnect call between A-B, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="5803ba4f-4b73-4f88-a9c4-1b61a4afd84b")
    def test_4g_volte_mo_mo_add_5g_nsa_volte_merge_drop_first_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneA (VoLTE) to PhoneC (nsa 5G VoLTE), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneB, verify call continues.
        5. End call on PhoneC, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="1f44800c-2bb2-46a3-9011-818f00e75e3f")
    def test_4g_volte_mo_mo_add_5g_nsa_volte_merge_drop_first_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneA (VoLTE) to PhoneC (nsa 5G VoLTE), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-B, verify call continues.
        5. On PhoneA disconnect call between A-C, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="fbc13772-314d-4b9a-8940-6edfe577c9f4")
    def test_4g_volte_mo_mt_add_5g_nsa_volte_merge_drop_second_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneC, verify call continues.
        5. End call on PhoneB, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="7b4e7c11-d633-4dc5-8fb1-25ad1ef4a4e8")
    def test_4g_volte_mo_mt_add_5g_nsa_volte_merge_drop_second_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-C, verify call continues.
        5. On PhoneA disconnect call between A-B, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="98adc3f4-ab57-4485-946f-08c1e8a69efb")
    def test_4g_volte_mo_mt_add_5g_nsa_volte_merge_drop_first_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneB, verify call continues.
        5. End call on PhoneC, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="93adad3d-c3ca-4fc1-88ec-e32c5330efd6")
    def test_4g_volte_mo_mt_add_5g_nsa_volte_merge_drop_first_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (VoLTE) to PhoneB (nsa 5G VoLTE), accept on PhoneB.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-B, verify call continues.
        5. On PhoneA disconnect call between A-C, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="6064eed6-0b23-4089-af30-f0c1f3ea844e")
    def test_4g_volte_mt_mt_add_5g_nsa_volte_merge_drop_second_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (nsa 5G VoLTE) to PhoneA (VoLTE), accept on PhoneA.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneC, verify call continues.
        5. End call on PhoneB, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="845e0521-fcb9-4609-a325-3ecb7681e3d8")
    def test_4g_volte_mt_mt_add_5g_nsa_volte_merge_drop_second_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (nsa 5G VoLTE) to PhoneA (VoLTE), accept on PhoneA.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-C, verify call continues.
        5. On PhoneA disconnect call between A-B, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="c676851a-e723-420f-be64-b4dbcab9cbea")
    def test_4g_volte_mt_mt_add_5g_nsa_volte_merge_drop_first_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (nsa 5G VoLTE) to PhoneA (VoLTE), accept on PhoneA.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneB, verify call continues.
        5. End call on PhoneC, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="a2aac8d9-5f87-4d95-a4d3-d8b174652a0e")
    def test_4g_volte_mt_mt_add_5g_nsa_volte_merge_drop_first_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (nsa 5G VoLTE) to PhoneA (VoLTE), accept on PhoneA.
        2. Call from PhoneC (nsa 5G VoLTE) to PhoneA (VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-B, verify call continues.
        5. On PhoneA disconnect call between A-C, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="9c1a213f-8c85-4234-a974-2add9f698389")
    def test_5g_nsa_volte_mo_mo_add_wcdma_merge_drop_second_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5g VoLTE) to PhoneB (WCDMA), accept on PhoneB.
        2. Call from PhoneA (nsa 5g VoLTE) to PhoneC (WCDMA), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneC, verify call continues.
        5. End call on PhoneB, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_voice_3g,
            phone_setup_c=phone_setup_voice_3g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_wcdma,
            verify_phone_c_network_subscription=is_phone_in_call_wcdma)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="53882070-35d3-4b8b-bc09-4debce8a094c")
    def test_5g_nsa_volte_mo_mo_add_wcdma_merge_drop_second_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5g VoLTE) to PhoneB (WCDMA), accept on PhoneB.
        2. Call from PhoneA (nsa 5g VoLTE) to PhoneC (WCDMA), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-C, verify call continues.
        5. On PhoneA disconnect call between A-B, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_voice_3g,
            phone_setup_c=phone_setup_voice_3g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_wcdma,
            verify_phone_c_network_subscription=is_phone_in_call_wcdma)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="a69588a5-3e08-4d33-92f7-5397c4d1c58d")
    def test_5g_nsa_volte_mo_mo_add_wcdma_merge_drop_first_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5g VoLTE) to PhoneB (WCDMA), accept on PhoneB.
        2. Call from PhoneA (nsa 5g VoLTE) to PhoneC (WCDMA), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneB, verify call continues.
        5. End call on PhoneC, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_voice_3g,
            phone_setup_c=phone_setup_voice_3g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_wcdma,
            verify_phone_c_network_subscription=is_phone_in_call_wcdma)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="da541f3e-6054-4f92-bbe4-242d03d90da2")
    def test_5g_nsa_volte_mo_mo_add_wcdma_merge_drop_first_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5g VoLTE) to PhoneB (WCDMA), accept on PhoneB.
        2. Call from PhoneA (nsa 5g VoLTE) to PhoneC (WCDMA), accept on PhoneC.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-B, verify call continues.
        5. On PhoneA disconnect call between A-C, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_voice_3g,
            phone_setup_c=phone_setup_voice_3g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_wcdma,
            verify_phone_c_network_subscription=is_phone_in_call_wcdma)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="54269ba5-bb78-428e-afd0-cba5783d797a")
    def test_5g_nsa_volte_mo_mt_add_wcdma_merge_drop_second_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5g VoLTE) to PhoneB (WCDMA), accept on PhoneB.
        2. Call from PhoneC (WCDMA) to PhoneA (nsa 5g VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneC, verify call continues.
        5. End call on PhoneB, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_voice_3g,
            phone_setup_c=phone_setup_voice_3g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_wcdma,
            verify_phone_c_network_subscription=is_phone_in_call_wcdma)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="d2bf3176-f9e8-4d2a-ad2a-c49fed9fd913")
    def test_5g_nsa_volte_mo_mt_add_wcdma_merge_drop_second_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5g VoLTE) to PhoneB (WCDMA), accept on PhoneB.
        2. Call from PhoneC (WCDMA) to PhoneA (nsa 5g VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-C, verify call continues.
        5. On PhoneA disconnect call between A-B, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_voice_3g,
            phone_setup_c=phone_setup_voice_3g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_wcdma,
            verify_phone_c_network_subscription=is_phone_in_call_wcdma)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="93c54a43-6b64-404a-9306-b9a74b563e41")
    def test_5g_nsa_volte_mo_mt_add_wcdma_merge_drop_first_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5g VoLTE) to PhoneB (WCDMA), accept on PhoneB.
        2. Call from PhoneC (WCDMA) to PhoneA (nsa 5g VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneB, verify call continues.
        5. End call on PhoneC, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_voice_3g,
            phone_setup_c=phone_setup_voice_3g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_wcdma,
            verify_phone_c_network_subscription=is_phone_in_call_wcdma)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="8e699115-1513-4c86-acd4-099b05acb859")
    def test_5g_nsa_volte_mo_mt_add_wcdma_merge_drop_first_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneA (nsa 5g VoLTE) to PhoneB (WCDMA), accept on PhoneB.
        2. Call from PhoneC (WCDMA) to PhoneA (nsa 5g VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-B, verify call continues.
        5. On PhoneA disconnect call between A-C, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_voice_3g,
            phone_setup_c=phone_setup_voice_3g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_wcdma,
            verify_phone_c_network_subscription=is_phone_in_call_wcdma)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="48edf043-1f45-4490-85fc-36ec49328768")
    def test_5g_nsa_volte_mt_mt_add_wcdma_merge_drop_second_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (WCDMA) to PhoneA (nsa 5g VoLTE), accept on PhoneA.
        2. Call from PhoneC (WCDMA) to PhoneA (nsa 5g VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneC, verify call continues.
        5. End call on PhoneB, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_voice_3g,
            phone_setup_c=phone_setup_voice_3g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_wcdma,
            verify_phone_c_network_subscription=is_phone_in_call_wcdma)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="a276b9d4-ddef-4712-9f96-aa4b71c7b35d")
    def test_5g_nsa_volte_mt_mt_add_wcdma_merge_drop_second_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (WCDMA) to PhoneA (nsa 5g VoLTE), accept on PhoneA.
        2. Call from PhoneC (WCDMA) to PhoneA (nsa 5g VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-C, verify call continues.
        5. On PhoneA disconnect call between A-B, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_voice_3g,
            phone_setup_c=phone_setup_voice_3g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_wcdma,
            verify_phone_c_network_subscription=is_phone_in_call_wcdma)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_second_call_from_host(
            self.log, self.android_devices, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="d702a65e-c2c9-44cf-be13-16ae56377647")
    def test_5g_nsa_volte_mt_mt_add_wcdma_merge_drop_first_call_from_participant(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (WCDMA) to PhoneA (nsa 5g VoLTE), accept on PhoneA.
        2. Call from PhoneC (WCDMA) to PhoneA (nsa 5g VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. End call on PhoneB, verify call continues.
        5. End call on PhoneC, verify call end on PhoneA.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_voice_3g,
            phone_setup_c=phone_setup_voice_3g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_wcdma,
            verify_phone_c_network_subscription=is_phone_in_call_wcdma)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_participant(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="dd3df9eb-a600-4397-b257-b8465592af82")
    def test_5g_nsa_volte_mt_mt_add_wcdma_merge_drop_first_call_from_host(
            self):
        """ Test nsa 5g VoLTE Conference Call among three phones.

        1. Call from PhoneB (WCDMA) to PhoneA (nsa 5g VoLTE), accept on PhoneA.
        2. Call from PhoneC (WCDMA) to PhoneA (nsa 5g VoLTE), accept on PhoneA.
        3. On PhoneA, merge to conference call.
        4. On PhoneA disconnect call between A-B, verify call continues.
        5. On PhoneA disconnect call between A-C, verify call continues.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices
        call_ab_id, call_ac_id = _test_call_mt_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=0,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_voice_3g,
            phone_setup_c=phone_setup_voice_3g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_wcdma,
            verify_phone_c_network_subscription=is_phone_in_call_wcdma)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _test_ims_conference_merge_drop_first_call_from_host(
            self.log, ads, call_ab_id, call_ac_id)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="d1174d8f-80e4-4adf-8866-e00ac7706849")
    def test_5g_nsa_volte_mo_mo_add_5g_nsa_volte_swap_twice_drop_held(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneA (nsa 5g VoLTE) call PhoneC (nsa 5g VoLTE), accept on PhoneC.
        Swap active call on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneB, check if call continues between AC.

        Returns:
            True if pass; False if fail.

        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=2,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[1],
            ad_verify=ads[0],
            call_id=call_ac_id,
            call_state=CALL_STATE_ACTIVE,
            ads_active=[ads[0], ads[2]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="c631b985-1fc4-4de6-95cd-00258283ef62")
    def test_5g_nsa_volte_mo_mo_add_5g_nsa_volte_swap_twice_drop_active(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneA (nsa 5g VoLTE) call PhoneC (nsa 5g VoLTE), accept on PhoneC.
        Swap active call on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneC, check if call continues between AB.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=2,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[2],
            ad_verify=ads[0],
            call_id=call_ab_id,
            call_state=_get_expected_call_state(ads[0]),
            ads_active=[ads[0], ads[1]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="0af58d3e-a7cc-4e5b-89df-b002deee022a")
    def test_5g_nsa_volte_mo_mt_add_5g_nsa_volte_swap_twice_drop_held(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneC (nsa 5g VoLTE) call PhoneA (nsa 5g VoLTE), accept on PhoneA.
        Swap active call on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneB, check if call continues between AC.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=2,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[1],
            ad_verify=ads[0],
            call_id=call_ac_id,
            call_state=CALL_STATE_ACTIVE,
            ads_active=[ads[0], ads[2]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="d00d0b87-4df2-40af-9b5e-dd425ec5ca4f")
    def test_5g_nsa_volte_mo_mt_add_5g_nsa_volte_swap_twice_drop_active(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneC (nsa 5g VoLTE) call PhoneA (nsa 5g VoLTE), accept on PhoneA.
        Swap active call on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneC, check if call continues between AB.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=2,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[2],
            ad_verify=ads[0],
            call_id=call_ab_id,
            call_state=_get_expected_call_state(ads[0]),
            ads_active=[ads[0], ads[1]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="dc46308c-197f-4372-afca-b2ec131dcc34")
    def test_5g_nsa_volte_mo_mo_add_5g_nsa_volte_swap_once_drop_held(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneA (nsa 5g VoLTE) call PhoneC (nsa 5g VoLTE), accept on PhoneC.
        Swap active call on PhoneA.
        Hangup call from PhoneC, check if call continues between AB.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=1,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[2],
            ad_verify=ads[0],
            call_id=call_ab_id,
            call_state=CALL_STATE_ACTIVE,
            ads_active=[ads[0], ads[1]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="471cc6d1-03aa-41a0-b13a-fdd696f17fca")
    def test_5g_nsa_volte_mo_mo_add_5g_nsa_volte_swap_once_drop_active(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneA (nsa 5g VoLTE) call PhoneC (nsa 5g VoLTE), accept on PhoneC.
        Swap active call on PhoneA.
        Hangup call from PhoneB, check if call continues between AC.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=1,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[1],
            ad_verify=ads[0],
            call_id=call_ac_id,
            call_state=_get_expected_call_state(ads[0]),
            ads_active=[ads[0], ads[2]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="bb9d69eb-40f4-494d-9399-7dbd046962e3")
    def test_5g_nsa_volte_mo_mt_add_5g_nsa_volte_swap_once_drop_held(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneC (nsa 5g VoLTE) call PhoneA (nsa 5g VoLTE), accept on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneC, check if call continues between AB.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=1,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False
        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[2],
            ad_verify=ads[0],
            call_id=call_ab_id,
            call_state=CALL_STATE_ACTIVE,
            ads_active=[ads[0], ads[1]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="33c9e964-c9d4-4cbc-894f-8682122598a7")
    def test_5g_nsa_volte_mo_mt_add_5g_nsa_volte_swap_once_drop_active(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneC (nsa 5g VoLTE) call PhoneA (nsa 5g VoLTE), accept on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneB, check if call continues between AC.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=1,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[1],
            ad_verify=ads[0],
            call_id=call_ac_id,
            call_state=_get_expected_call_state(ads[0]),
            ads_active=[ads[0], ads[2]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="43e4cc02-93d9-4540-97b6-f5656d7d669f")
    def test_5g_nsa_volte_mo_mo_add_4g_volte_swap_twice_drop_held(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (VoLTE), accept on PhoneB.
        PhoneA (nsa 5g VoLTE) call PhoneC (VoLTE), accept on PhoneC.
        Swap active call on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneB, check if call continues between AC.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=2,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[1],
            ad_verify=ads[0],
            call_id=call_ac_id,
            call_state=CALL_STATE_ACTIVE,
            ads_active=[ads[0], ads[2]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="f3081bbd-9f18-4c0b-a1ab-630d5e622259")
    def test_5g_nsa_volte_mo_mo_add_4g_volte_swap_twice_drop_active(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (VoLTE), accept on PhoneB.
        PhoneA (nsa 5g VoLTE) call PhoneC (VoLTE), accept on PhoneC.
        Swap active call on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneC, check if call continues between AB.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=2,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[2],
            ad_verify=ads[0],
            call_id=call_ab_id,
            call_state=_get_expected_call_state(ads[0]),
            ads_active=[ads[0], ads[1]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="ff4a731b-3bf9-45d5-beeb-e8ad53ceb722")
    def test_5g_nsa_volte_mo_mt_add_4g_volte_swap_twice_drop_held(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (VoLTE), accept on PhoneB.
        PhoneC (VoLTE) call PhoneA (nsa 5g VoLTE), accept on PhoneA.
        Swap active call on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneB, check if call continues between AC.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=2,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[1],
            ad_verify=ads[0],
            call_id=call_ac_id,
            call_state=CALL_STATE_ACTIVE,
            ads_active=[ads[0], ads[2]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="9ebae664-41bb-45fe-8f52-08c2dabab659")
    def test_5g_nsa_volte_mo_mt_add_4g_volte_swap_twice_drop_active(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (VoLTE), accept on PhoneB.
        PhoneC (VoLTE) call PhoneA (nsa 5g VoLTE), accept on PhoneA.
        Swap active call on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneC, check if call continues between AB.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=2,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[2],
            ad_verify=ads[0],
            call_id=call_ab_id,
            call_state=_get_expected_call_state(ads[0]),
            ads_active=[ads[0], ads[1]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="b8ae81dd-3f67-4c2c-aec0-d6368efb872e")
    def test_5g_nsa_volte_mo_mo_add_4g_volte_swap_once_drop_held(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (VoLTE), accept on PhoneB.
        PhoneA (nsa 5g VoLTE) call PhoneC (VoLTE), accept on PhoneC.
        Swap active call on PhoneA.
        Hangup call from PhoneC, check if call continues between AB.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=1,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[2],
            ad_verify=ads[0],
            call_id=call_ab_id,
            call_state=CALL_STATE_ACTIVE,
            ads_active=[ads[0], ads[1]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="2c588e87-8b69-4a8e-b55a-4e101806297c")
    def test_5g_nsa_volte_mo_mo_add_4g_volte_swap_once_drop_active(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (VoLTE), accept on PhoneB.
        PhoneA (nsa 5g VoLTE) call PhoneC (VoLTE), accept on PhoneC.
        Swap active call on PhoneA.
        Hangup call from PhoneB, check if call continues between AC.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=1,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[1],
            ad_verify=ads[0],
            call_id=call_ac_id,
            call_state=_get_expected_call_state(ads[0]),
            ads_active=[ads[0], ads[2]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="367e8d25-5a9d-4c0d-914d-c241399bb343")
    def test_5g_nsa_volte_mo_mt_add_4g_volte_swap_once_drop_held(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (VoLTE), accept on PhoneB.
        PhoneC (VoLTE) call PhoneA (nsa 5g VoLTE), accept on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneC, check if call continues between AB.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=1,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False
        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[2],
            ad_verify=ads[0],
            call_id=call_ab_id,
            call_state=CALL_STATE_ACTIVE,
            ads_active=[ads[0], ads[1]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="8a8f915e-025a-4992-b05d-bf74173e3852")
    def test_5g_nsa_volte_mo_mt_add_4g_volte_swap_once_drop_active(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (nsa 5g VoLTE) call PhoneB (VoLTE), accept on PhoneB.
        PhoneC (VoLTE) call PhoneA (nsa 5g VoLTE), accept on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneB, check if call continues between AC.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=1,
            phone_setup_a=provision_device_for_5g,
            phone_setup_b=phone_setup_volte,
            phone_setup_c=phone_setup_volte,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[1],
            ad_verify=ads[0],
            call_id=call_ac_id,
            call_state=_get_expected_call_state(ads[0]),
            ads_active=[ads[0], ads[2]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="880c3f9e-1a97-489a-80b1-db4c9aaa7be0")
    def test_4g_volte_mo_mo_add_5g_nsa_volte_swap_twice_drop_held(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneA (VoLTE) call PhoneC (nsa 5g VoLTE), accept on PhoneC.
        Swap active call on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneB, check if call continues between AC.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=2,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[1],
            ad_verify=ads[0],
            call_id=call_ac_id,
            call_state=CALL_STATE_ACTIVE,
            ads_active=[ads[0], ads[2]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="b869f455-bf1b-4f03-bba3-a6f029f9f594")
    def test_4g_volte_mo_mo_add_5g_nsa_volte_swap_twice_drop_active(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneA (VoLTE) call PhoneC (nsa 5g VoLTE), accept on PhoneC.
        Swap active call on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneC, check if call continues between AB.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=2,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[2],
            ad_verify=ads[0],
            call_id=call_ab_id,
            call_state=_get_expected_call_state(ads[0]),
            ads_active=[ads[0], ads[1]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="32ce220a-7abe-40f4-bd59-e948e7e8f735")
    def test_4g_volte_mo_mt_add_5g_nsa_volte_swap_twice_drop_held(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneC (nsa 5g VoLTE) call PhoneA (VoLTE), accept on PhoneA.
        Swap active call on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneB, check if call continues between AC.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=2,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[1],
            ad_verify=ads[0],
            call_id=call_ac_id,
            call_state=CALL_STATE_ACTIVE,
            ads_active=[ads[0], ads[2]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="0b6bc57b-4dfb-4d2a-9160-fbebcecd8970")
    def test_4g_volte_mo_mt_add_5g_nsa_volte_swap_twice_drop_active(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneC (nsa 5g VoLTE) call PhoneA (VoLTE), accept on PhoneA.
        Swap active call on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneC, check if call continues between AB.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=2,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[2],
            ad_verify=ads[0],
            call_id=call_ab_id,
            call_state=_get_expected_call_state(ads[0]),
            ads_active=[ads[0], ads[1]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="1d70289e-bbef-420a-b484-77c5573cc8fb")
    def test_4g_volte_mo_mo_add_5g_nsa_volte_swap_once_drop_held(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneA (VoLTE) call PhoneC (nsa 5g VoLTE), accept on PhoneC.
        Swap active call on PhoneA.
        Hangup call from PhoneC, check if call continues between AB.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=1,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[2],
            ad_verify=ads[0],
            call_id=call_ab_id,
            call_state=CALL_STATE_ACTIVE,
            ads_active=[ads[0], ads[1]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="3a62d358-ccb6-43fc-acb5-469dc9c5d5b2")
    def test_4g_volte_mo_mo_add_5g_nsa_volte_swap_once_drop_active(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneA (VoLTE) call PhoneC (nsa 5g VoLTE), accept on PhoneC.
        Swap active call on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneB, check if call continues between AC.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mo_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=1,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[1],
            ad_verify=ads[0],
            call_id=call_ac_id,
            call_state=_get_expected_call_state(ads[0]),
            ads_active=[ads[0], ads[2]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="52e0a840-3fb3-4351-b2f0-d67cf9928a16")
    def test_4g_volte_mo_mt_add_5g_nsa_volte_swap_once_drop_held(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneC (nsa 5g VoLTE) call PhoneA (VoLTE), accept on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneC, check if call continues between AB.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=1,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False
        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[2],
            ad_verify=ads[0],
            call_id=call_ab_id,
            call_state=CALL_STATE_ACTIVE,
            ads_active=[ads[0], ads[1]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="6b63555a-c009-4431-be14-abc546cff911")
    def test_4g_volte_mo_mt_add_5g_nsa_volte_swap_once_drop_active(self):
        """Test swap feature in nsa 5g VoLTE call.

        PhoneA (VoLTE) call PhoneB (nsa 5g VoLTE), accept on PhoneB.
        PhoneC (nsa 5g VoLTE) call PhoneA (VoLTE), accept on PhoneA.
        Swap active call on PhoneA.
        Hangup call from PhoneB, check if call continues between AC.

        Returns:
            True if pass; False if fail.
        """
        ads = self.android_devices

        call_ab_id, call_ac_id = _test_call_mo_mt_add_swap_x(
            log=self.log,
            ads=ads,
            num_swaps=1,
            phone_setup_a=phone_setup_volte,
            phone_setup_b=provision_device_for_5g,
            phone_setup_c=provision_device_for_5g,
            verify_phone_a_network_subscription=is_phone_in_call_volte,
            verify_phone_b_network_subscription=is_phone_in_call_volte,
            verify_phone_c_network_subscription=is_phone_in_call_volte)
        if call_ab_id is None or call_ac_id is None:
            return False

        return _three_phone_hangup_call_verify_call_state(
            log=self.log,
            ad_hangup=ads[1],
            ad_verify=ads[0],
            call_id=call_ac_id,
            call_state=_get_expected_call_state(ads[0]),
            ads_active=[ads[0], ads[2]])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="f4990e20-4a40-4238-9a2a-a75d9be3d354")
    def test_5g_nsa_volte_call_forwarding_unconditional(self):

        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0], GEN_5G)),
                 (phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_voice_general, (self.log, ads[2]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        return three_phone_call_forwarding_short_seq(
            self.log,
            ads[0],
            None,
            None,
            ads[1],
            ads[2],
            call_forwarding_type="unconditional")

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="26b85c3f-5a38-465a-a6e3-dfd03c6ea315")
    def test_5g_nsa_volte_call_forwarding_busy(self):

        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0], GEN_5G)),
                 (phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_voice_general, (self.log, ads[2]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        return three_phone_call_forwarding_short_seq(
            self.log,
            ads[0],
            None,
            None,
            ads[1],
            ads[2],
            call_forwarding_type="busy")

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="96638a39-efe2-40e2-afb6-6a97f87c4af5")
    def test_5g_nsa_volte_call_forwarding_not_answered(self):

        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0], GEN_5G)),
                 (phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_voice_general, (self.log, ads[2]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        return three_phone_call_forwarding_short_seq(
            self.log,
            ads[0],
            None,
            None,
            ads[1],
            ads[2],
            call_forwarding_type="not_answered")

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="a13e586a-3345-49d8-9e84-ca33bd3fbd7d")
    def test_5g_nsa_volte_call_forwarding_not_reachable(self):

        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0], GEN_5G)),
                 (phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_voice_general, (self.log, ads[2]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        return three_phone_call_forwarding_short_seq(
            self.log,
            ads[0],
            None,
            None,
            ads[1],
            ads[2],
            call_forwarding_type="not_reachable")

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="e9a6027b-7dd1-4dca-a700-e4d42c9c947d")
    def test_call_waiting_scenario_1(self):
        """ Call waiting scenario 1: 1st call ended first by caller1 during 2nd
        call incoming. 2nd call ended by caller2.
        """
        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0], GEN_5G)),
                 (phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_voice_general, (self.log, ads[2]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        return three_phone_call_waiting_short_seq(self.log,
                                                  ads[0],
                                                  None,
                                                  None,
                                                  ads[1],
                                                  ads[2],
                                                  call_waiting=True,
                                                  scenario=1)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="3fe02cb7-68d7-4762-882a-02bff8ce32f9")
    def test_call_waiting_scenario_2(self):
        """ Call waiting scenario 2: 1st call ended first by caller1 during 2nd
        call incoming. 2nd call ended by callee.
        """
        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0], GEN_5G)),
                 (phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_voice_general, (self.log, ads[2]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        return three_phone_call_waiting_short_seq(self.log,
                                                  ads[0],
                                                  None,
                                                  None,
                                                  ads[1],
                                                  ads[2],
                                                  call_waiting=True,
                                                  scenario=2)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="bf5eb9ad-1fa2-468d-99dc-3cbcee8c89f8")
    def test_call_waiting_scenario_3(self):
        """ Call waiting scenario 3: 1st call ended first by callee during 2nd
        call incoming. 2nd call ended by caller2.
        """
        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0], GEN_5G)),
                 (phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_voice_general, (self.log, ads[2]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        return three_phone_call_waiting_short_seq(self.log,
                                                  ads[0],
                                                  None,
                                                  None,
                                                  ads[1],
                                                  ads[2],
                                                  call_waiting=True,
                                                  scenario=3)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="f2e4b6a9-6a6f-466c-884c-c0ef79d6ff01")
    def test_call_waiting_scenario_4(self):
        """Call waiting scenario 4: 1st call ended first by callee during 2nd
        call incoming. 2nd call ended by callee.
        """
        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0], GEN_5G)),
                 (phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_voice_general, (self.log, ads[2]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        return three_phone_call_waiting_short_seq(self.log,
                                                  ads[0],
                                                  None,
                                                  None,
                                                  ads[1],
                                                  ads[2],
                                                  call_waiting=True,
                                                  scenario=4)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="f2d36f45-63f6-4e01-9844-6fa53c26def7")
    def test_call_waiting_scenario_5(self):
        """ Call waiting scenario 5: 1st call ended by caller1. 2nd call ended
        by caller2.
        """
        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0], GEN_5G)),
                 (phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_voice_general, (self.log, ads[2]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        return three_phone_call_waiting_short_seq(self.log,
                                                  ads[0],
                                                  None,
                                                  None,
                                                  ads[1],
                                                  ads[2],
                                                  call_waiting=True,
                                                  scenario=5)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="7eb2a89d-30ad-4a34-8e63-87d0181b91aa")
    def test_call_waiting_scenario_6(self):
        """Call waiting scenario 6: 1st call ended by caller1. 2nd call ended by
        callee.
        """
        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0], GEN_5G)),
                 (phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_voice_general, (self.log, ads[2]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        return three_phone_call_waiting_short_seq(self.log,
                                                  ads[0],
                                                  None,
                                                  None,
                                                  ads[1],
                                                  ads[2],
                                                  call_waiting=True,
                                                  scenario=6)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="c63882e5-5b72-4ca6-8e36-260c50f42028")
    def test_call_waiting_scenario_7(self):
        """ Call waiting scenario 7: 1st call ended by callee. 2nd call ended by
        caller2.
        """
        ads = self.android_devices

        tasks = [(phone_setup_voice_general, (self.log, ads[0])),
                 (phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_voice_general, (self.log, ads[2]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        return three_phone_call_waiting_short_seq(self.log,
                                                  ads[0],
                                                  None,
                                                  None,
                                                  ads[1],
                                                  ads[2],
                                                  call_waiting=True,
                                                  scenario=7)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="f9be652f-a307-4fa5-9b30-ea78404110bd")
    def test_call_waiting_scenario_8(self):
        """Call waiting scenario 8: 1st call ended by callee. 2nd call ended by
        callee.
        """
        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0], GEN_5G)),
                 (phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_voice_general, (self.log, ads[2]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        return three_phone_call_waiting_short_seq(self.log,
                                                  ads[0],
                                                  None,
                                                  None,
                                                  ads[1],
                                                  ads[2],
                                                  call_waiting=True,
                                                  scenario=8)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="b2e816b5-8e8f-4863-981c-47847d9527e0")
    def test_call_waiting_deactivated(self):

        ads = self.android_devices

        tasks = [(phone_setup_volte, (self.log, ads[0], GEN_5G)),
                 (phone_setup_voice_general, (self.log, ads[1])),
                 (phone_setup_voice_general, (self.log, ads[2]))]
        if not multithread_func(self.log, tasks):
            self.log.error("Phone Failed to Set Up Properly.")
            return False

        return three_phone_call_waiting_short_seq(self.log,
                                                  ads[0],
                                                  None,
                                                  None,
                                                  ads[1],
                                                  ads[2],
                                                  call_waiting=False)
