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
    Test Script for 5G MSA mmWave Activation scenarios
"""

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_test_utils import reboot_device
from acts_contrib.test_utils.tel.tel_test_utils import cycle_airplane_mode
from acts_contrib.test_utils.tel.tel_5g_test_utils import test_activation_by_condition
from acts_contrib.test_utils.tel.tel_test_utils import set_phone_silent_mode


class Nsa5gMmwActivationTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()
        for ad in self.android_devices:
            set_phone_silent_mode(self.log, ad, True)

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)
        self.number_of_devices = 1

    def teardown_class(self):
        TelephonyBaseTest.teardown_class(self)

    """ Tests Begin """

    @test_tracker_info(uuid="6831cf7f-349e-43ae-9a89-5e183a755671")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_activation_from_apm(self):
        """ Verifies 5G NSA mmWave activation from Airplane Mode

        Toggle Airplane mode on and off
        Ensure phone attach, data on, LTE attach
        Wait for 120 secs for ENDC attach
        Verify is data network type is NR_NSA

        Returns:
            True if pass; False if fail.
        """

        return test_activation_by_condition(
            self.android_devices[0],
            nr_type='mmwave',
            precond_func=lambda: cycle_airplane_mode(self.android_devices[0]))

    @test_tracker_info(uuid="21fb9b5c-40e8-4804-b05b-017395bb2e79")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_activation_from_reboot(self):
        """ Verifies 5G NSA mmWave activation from Reboot

        Reboot device
        Ensure phone attach, data on, LTE attach
        Wait for 120 secs for ENDC attach
        Verify is data network type is NR_NSA

        Returns:
            True if pass; False if fail.
        """

        return test_activation_by_condition(
            self.android_devices[0],
            nr_type='mmwave',
            precond_func=lambda: reboot_device(self.android_devices[0]))

    @test_tracker_info(uuid="2cef7ec0-ea74-458f-a98e-143d0be71f31")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_activation_from_3g(self):
        """ Verifies 5G NSA mmWave activation from 3G Mode Pref

        Change Mode to 3G and wait for 15 secs
        Change Mode back to 5G
        Ensure phone attach, data on, LTE attach
        Wait for 120 secs for ENDC attach
        Verify is data network type is NR_NSA

        Returns:
            True if pass; False if fail.
        """

        return test_activation_by_condition(self.android_devices[0],
                                            from_3g=True,
                                            nr_type='mmwave')

    """ Tests End """
