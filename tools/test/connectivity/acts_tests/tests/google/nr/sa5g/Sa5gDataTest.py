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
    Test Script for 5G SA Data scenarios
"""

import time

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import GEN_5G
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_USER_PLANE_DATA
from acts_contrib.test_utils.tel.tel_defines import NETWORK_MODE_NR_ONLY
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_ANDROID_STATE_SETTLING
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_device_for_5g
from acts_contrib.test_utils.tel.tel_5g_utils import is_current_network_5g
from acts_contrib.test_utils.tel.tel_data_utils import browsing_test
from acts_contrib.test_utils.tel.tel_data_utils import check_data_stall_detection
from acts_contrib.test_utils.tel.tel_data_utils import check_data_stall_recovery
from acts_contrib.test_utils.tel.tel_data_utils import check_network_validation_fail
from acts_contrib.test_utils.tel.tel_data_utils import data_connectivity_single_bearer
from acts_contrib.test_utils.tel.tel_data_utils import reboot_test
from acts_contrib.test_utils.tel.tel_data_utils import test_wifi_connect_disconnect
from acts_contrib.test_utils.tel.tel_data_utils import wifi_cell_switching
from acts_contrib.test_utils.tel.tel_test_utils import break_internet_except_sl4a_port
from acts_contrib.test_utils.tel.tel_test_utils import get_current_override_network_type
from acts_contrib.test_utils.tel.tel_test_utils import get_device_epoch_time
from acts_contrib.test_utils.tel.tel_test_utils import resume_internet_with_sl4a_port
from acts_contrib.test_utils.tel.tel_test_utils import set_preferred_network_mode_pref
from acts_contrib.test_utils.tel.tel_test_utils import test_data_browsing_failure_using_sl4a
from acts_contrib.test_utils.tel.tel_test_utils import test_data_browsing_success_using_sl4a
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.tel.tel_wifi_utils import wifi_reset
from acts_contrib.test_utils.tel.tel_wifi_utils import wifi_toggle_state


class Sa5gDataTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)
        self.provider = self.android_devices[0]
        self.clients = self.android_devices[1:]

    def teardown_class(self):
        TelephonyBaseTest.teardown_class(self)


    """ Tests Begin """

    @test_tracker_info(uuid="2bb5fa22-d931-426f-a11d-22f514d867d0")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_sa_data_browsing(self):
        """ Verifying connectivity of internet and  browsing websites on 5G SA network.

        Ensure
            1. ping to IP of websites is successful.
            2. http ping to IP of websites is successful.
            3. browsing websites is successful.
        Returns:
            True if pass; False if fail.
        """
        ad = self.android_devices[0]
        wifi_toggle_state(ad.log, ad, False)
        sub_id = ad.droid.subscriptionGetDefaultSubId()
        if not set_preferred_network_mode_pref(ad.log, ad, sub_id,
                                               NETWORK_MODE_NR_ONLY):
            ad.log.error("Failed to set network mode to SA")
            return False
        ad.log.info("Set network mode to SA successfully")
        ad.log.info("Waiting for 5g SA attach for 60 secs")
        if is_current_network_5g(ad, nr_type = 'sa'):
            ad.log.info("Success! attached on 5g SA")
        else:
            ad.log.error("Failure - expected NR, current %s",
                         get_current_override_network_type(ad))
            return False
        for iteration in range(3):
            connectivity = False
            browsing = False
            ad.log.info("Attempt %d", iteration + 1)
            if not verify_internet_connection(self.log, ad):
                ad.log.error("Failed to connect to internet!")
            else:
                ad.log.info("Connect to internet successfully!")
                connectivity = True
            if not browsing_test(ad.log, ad):
                ad.log.error("Failed to browse websites!")
            else:
                ad.log.info("Successful to browse websites!")
                browsing = True
            if connectivity and browsing:
                return True
            time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
        ad.log.error("5G SA Connectivity and Data Browsing test FAIL for all 3 iterations")
        return False


    @test_tracker_info(uuid="785b0ef8-b326-42f8-a399-51ebc1f9e93e")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_sa_data_stall_recovery(self):
        """ Verifies 5G SA data stall

        Set Mode to 5G
        Wait for 5G attached on SA
        Browse websites for success
        Trigger data stall and verify browsing fails
        Resume data and verify browsing success

        Returns:
            True if pass; False if fail.
        """
        ad = self.android_devices[0]
        result = True
        wifi_toggle_state(ad.log, ad, False)
        toggle_airplane_mode(ad.log, ad, False)

        if not provision_device_for_5g(ad.log, ad, nr_type= 'sa'):
            return False

        cmd = ('ss -l -p -n | grep "tcp.*droid_script" | tr -s " " '
               '| cut -d " " -f 5 | sed s/.*://g')
        sl4a_port = ad.adb.shell(cmd)

        if not test_data_browsing_success_using_sl4a(ad.log, ad):
            ad.log.error("Browsing failed before the test, aborting!")
            return False

        begin_time = get_device_epoch_time(ad)
        break_internet_except_sl4a_port(ad, sl4a_port)

        if not test_data_browsing_failure_using_sl4a(ad.log, ad):
            ad.log.error("Browsing after breaking the internet, aborting!")
            result = False

        if not check_data_stall_detection(ad):
            ad.log.warning("NetworkMonitor unable to detect Data Stall")

        if not check_network_validation_fail(ad, begin_time):
            ad.log.warning("Unable to detect NW validation fail")

        if not check_data_stall_recovery(ad, begin_time):
            ad.log.error("Recovery was not triggered")
            result = False

        resume_internet_with_sl4a_port(ad, sl4a_port)
        time.sleep(MAX_WAIT_TIME_USER_PLANE_DATA)
        if not test_data_browsing_success_using_sl4a(ad.log, ad):
            ad.log.error("Browsing failed after resuming internet")
            result = False
        if result:
            ad.log.info("PASS - data stall over 5G SA")
        else:
            ad.log.error("FAIL - data stall over 5G SA")
        return result


    @test_tracker_info(uuid="6c0ef257-6381-4c1d-8b8e-0371db7e08ac")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_sa_wifi_connect_disconnect(self):
        """Perform multiple connects and disconnects from WiFi and verify that
            data switches between WiFi and Cell.

        Steps:
        1. DUT Cellular Data is on sa 5G. Reset Wifi on DUT
        2. Connect DUT to a WiFi AP
        3. Repeat steps 1-2, alternately disconnecting and disabling wifi

        Expected Results:
        1. Verify Data on Cell
        2. Verify Data on Wifi

        Returns:
            True if success.
            False if failed.
        """
        if not provision_device_for_5g(self.log, self.provider, nr_type= 'sa'):
            return False

        return test_wifi_connect_disconnect(self.log, self.provider, self.wifi_network_ssid, self.wifi_network_pass)


    @test_tracker_info(uuid="f70ad253-4b77-4d9b-b7dc-d7cd3e945e5f")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_sa_wifi_switching(self):
        """Test data connection network switching when phone camped on sa 5G.

        Ensure phone is camped on sa 5G
        Ensure WiFi can connect to live network,
        Airplane mode is off, data connection is on, WiFi is on.
        Turn off WiFi, verify data is on cell and browse to google.com is OK.
        Turn on WiFi, verify data is on WiFi and browse to google.com is OK.
        Turn off WiFi, verify data is on cell and browse to google.com is OK.

        Returns:
            True if pass.
        """
        ad = self.android_devices[0]
        return wifi_cell_switching(ad.log, ad, GEN_5G, self.wifi_network_ssid,
                                   self.wifi_network_pass, nr_type= 'sa')


    @test_tracker_info(uuid="8df1b65c-197e-40b3-83a4-6da1f0a51b97")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_sa_wifi_not_associated(self):
        """Test data connection in sa 5g.

        Turn off airplane mode, enable WiFi (but not connected), enable Cellular Data.
        Ensure phone data generation is sa 5g.
        Verify Internet.
        Disable Cellular Data, verify Internet is inaccessible.
        Enable Cellular Data, verify Internet.

        Returns:
            True if success.
            False if failed.
        """
        ad = self.android_devices[0]
        wifi_reset(ad.log, ad)
        wifi_toggle_state(ad.log, ad, False)
        wifi_toggle_state(ad.log, ad, True)
        return data_connectivity_single_bearer(ad.log, ad, GEN_5G, nr_type= 'sa')


    @test_tracker_info(uuid="6c1ec0a6-223e-4bcd-b958-b85f5eb03943")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_sa_reboot(self):
        """Test 5G SA service availability after reboot.

        Ensure phone is on 5G SA.
        Ensure phone attach, data on, WiFi off and verify Internet.
        Reboot Device.
        Verify Network Connection.

        Returns:
            True if pass; False if fail.
        """
        if not provision_device_for_5g(self.log, self.android_devices[0], nr_type='sa'):
            return False
        if not verify_internet_connection(self.log, self.android_devices[0]):
            return False
        return reboot_test(self.log, self.android_devices[0])

    """ Tests End """
