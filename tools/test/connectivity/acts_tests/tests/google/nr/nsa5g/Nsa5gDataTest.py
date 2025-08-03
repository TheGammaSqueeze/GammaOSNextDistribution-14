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
    Test Script for 5G Data scenarios
"""

import time

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import GEN_5G
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_USER_PLANE_DATA
from acts_contrib.test_utils.tel.tel_defines import NETWORK_MODE_NR_LTE_GSM_WCDMA
from acts_contrib.test_utils.tel.tel_defines import NetworkCallbackCapabilitiesChanged
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_ANDROID_STATE_SETTLING
from acts_contrib.test_utils.tel.tel_data_utils import browsing_test
from acts_contrib.test_utils.tel.tel_data_utils import check_data_stall_detection
from acts_contrib.test_utils.tel.tel_data_utils import check_data_stall_recovery
from acts_contrib.test_utils.tel.tel_data_utils import check_network_validation_fail
from acts_contrib.test_utils.tel.tel_data_utils import data_connectivity_single_bearer
from acts_contrib.test_utils.tel.tel_data_utils import test_data_connectivity_multi_bearer
from acts_contrib.test_utils.tel.tel_data_utils import test_wifi_connect_disconnect
from acts_contrib.test_utils.tel.tel_data_utils import verify_for_network_callback
from acts_contrib.test_utils.tel.tel_data_utils import wifi_cell_switching
from acts_contrib.test_utils.tel.tel_data_utils import airplane_mode_test
from acts_contrib.test_utils.tel.tel_data_utils import reboot_test
from acts_contrib.test_utils.tel.tel_5g_utils import is_current_network_5g
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_device_for_5g
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte
from acts_contrib.test_utils.tel.tel_test_utils import break_internet_except_sl4a_port
from acts_contrib.test_utils.tel.tel_test_utils import get_current_override_network_type
from acts_contrib.test_utils.tel.tel_test_utils import get_device_epoch_time
from acts_contrib.test_utils.tel.tel_test_utils import iperf_test_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import iperf_udp_test_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import resume_internet_with_sl4a_port
from acts_contrib.test_utils.tel.tel_test_utils import set_preferred_network_mode_pref
from acts_contrib.test_utils.tel.tel_test_utils import test_data_browsing_failure_using_sl4a
from acts_contrib.test_utils.tel.tel_test_utils import test_data_browsing_success_using_sl4a
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.tel.tel_wifi_utils import ensure_wifi_connected
from acts_contrib.test_utils.tel.tel_wifi_utils import wifi_reset
from acts_contrib.test_utils.tel.tel_wifi_utils import wifi_toggle_state


class Nsa5gDataTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()
        self.iperf_server_ip = self.user_params.get("iperf_server", '0.0.0.0')
        self.iperf_tcp_port = int(
            self.user_params.get("iperf_tcp_port", 0))
        self.iperf_udp_port = int(
            self.user_params.get("iperf_udp_port", 0))
        self.iperf_duration = int(
            self.user_params.get("iperf_duration", 60))

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)
        self.provider = self.android_devices[0]
        self.clients = self.android_devices[1:]

    def teardown_class(self):
        TelephonyBaseTest.teardown_class(self)


    """ Tests Begin """

    @test_tracker_info(uuid="a73b749f-746c-4089-a8ad-4e47aed180f6")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_data_browsing(self):
        """ Verifying connectivity of internet and  browsing websites on 5G NSA network.

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
                                               NETWORK_MODE_NR_LTE_GSM_WCDMA):
            ad.log.error("Failed to set network mode to NSA")
            return False
        ad.log.info("Set network mode to NSA successfully")
        ad.log.info("Waiting for 5g NSA attach for 60 secs")
        if is_current_network_5g(ad, nr_type = 'nsa', timeout=60):
            ad.log.info("Success! attached on 5g NSA")
        else:
            ad.log.error("Failure - expected NR_NSA, current %s",
                         get_current_override_network_type(ad))
            # Can't attach 5g NSA, exit test!
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
        ad.log.error("5G NSA Connectivity and Data Browsing test FAIL for all 3 iterations")
        return False


    @test_tracker_info(uuid="c7727c26-b588-461f-851b-802bfa3a86af")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_data_stall_recovery(self):
        """ Verifies 5G NSA data stall

        Set Mode to 5G
        Wait for 5G attached on NSA
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

        if not provision_device_for_5g(ad.log, ad, nr_type='nsa'):
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
            ad.log.info("PASS - data stall over 5G NSA")
        else:
            ad.log.error("FAIL - data stall over 5G NSA")
        return result


    @test_tracker_info(uuid="bb823c3e-d84e-44b5-a9b4-89f65ab2d02c")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_metered_cellular(self):
        """ Verifies 5G Meteredness API

        Set Mode to 5G
        Wait for 5G attached on NSA
        Register for Connectivity callback
        Verify value of metered flag

        Returns:
            True if pass; False if fail.
        """
        ad = self.android_devices[0]
        try:
            wifi_toggle_state(ad.log, ad, False)
            toggle_airplane_mode(ad.log, ad, False)
            if not provision_device_for_5g(ad.log, ad, nr_type='nsa'):
                return False

            return verify_for_network_callback(ad.log, ad,
                NetworkCallbackCapabilitiesChanged, apm_mode=False)
        except Exception as e:
            ad.log.error(e)
            return False


    @test_tracker_info(uuid="192a605c-d7a9-4c34-800a-96a7d3177d7b")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_metered_wifi(self):
        """ Verifies 5G Meteredness API

        Set Mode to 5G, Wifi Connected
        Register for Connectivity callback
        Verify value of metered flag

        Returns:
            True if pass; False if fail.
        """
        ad = self.android_devices[0]
        try:
            toggle_airplane_mode(ad.log, ad, False)
            if not provision_device_for_5g(ad.log, ad, nr_type='nsa'):
                return False
            wifi_toggle_state(ad.log, ad, True)
            if not ensure_wifi_connected(ad.log, ad,
                                         self.wifi_network_ssid,
                                         self.wifi_network_pass):
                ad.log.error("WiFi connect fail.")
                return False
            return verify_for_network_callback(ad.log, ad,
                 NetworkCallbackCapabilitiesChanged)
        except Exception as e:
            ad.log.error(e)
            return False
        finally:
            wifi_toggle_state(ad.log, ad, False)


    @test_tracker_info(uuid="be0c110d-52d4-4af8-bf1c-96c4807d1f07")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_udp_throughput(self):
        """ Verifies UDP DL throughput over 5G

        Set Mode to 5G, Wifi Disconnected
        Verify device attached to 5G NSA
        Perform iperf test using UDP and measure throughput

        Returns:
            True if pass; False if fail.
        """
        ad = self.android_devices[0]
        try:
            toggle_airplane_mode(ad.log, ad, False)
            if not provision_device_for_5g(ad.log, ad, nr_type='nsa'):
                return False
            wifi_toggle_state(ad.log, ad, False)
            return iperf_udp_test_by_adb(ad.log,
                                         ad,
                                         self.iperf_server_ip,
                                         self.iperf_udp_port,
                                         True,
                                         self.iperf_duration)
        except Exception as e:
            ad.log.error(e)
            return False


    @test_tracker_info(uuid="47b87533-dc33-4c27-95ff-0b5316e6a193")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_tcp_throughput(self):
        """ Verifies TCP DL throughput over 5G

        Set Mode to 5G, Wifi Disconnected
        Verify device attached to 5G NSA
        Perform iperf test using TCP and measure throughput

        Returns:
            True if pass; False if fail.
        """
        ad = self.android_devices[0]
        try:
            toggle_airplane_mode(ad.log, ad, False)
            if not provision_device_for_5g(ad.log, ad, nr_type='nsa'):
                return False
            wifi_toggle_state(ad.log, ad, False)
            return iperf_test_by_adb(ad.log,
                                     ad,
                                     self.iperf_server_ip,
                                     self.iperf_tcp_port,
                                     True,
                                     self.iperf_duration)
        except Exception as e:
            ad.log.error(e)
            return False


    @test_tracker_info(uuid="79393af4-cbc1-4d00-8e02-fe76e8b28367")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_bursty_data(self):
        """ Verifies Bursty data transfer over 5G

        Set Mode to 5G, Wifi Disconnected
        Verify device attached to 5G NSA
        Perform iperf test using burst of data

        Returns:
            True if pass; False if fail.
        """
        ad = self.android_devices[0]
        try:
            toggle_airplane_mode(ad.log, ad, False)
            if not provision_device_for_5g(ad.log, ad, nr_type='nsa'):
                return False
            wifi_toggle_state(ad.log, ad, False)
            return iperf_udp_test_by_adb(ad.log,
                                         ad,
                                         self.iperf_server_ip,
                                         self.iperf_udp_port,
                                         True,
                                         self.iperf_duration,
                                         limit_rate="10M",
                                         pacing_timer="1000000")
        except Exception as e:
            ad.log.error(e)
            return False

    @test_tracker_info(uuid="7179f0f1-f0ca-4496-8f4a-7eebc616a41a")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_switching(self):
        """Test data connection network switching when phone camped on nsa 5G.

        Ensure phone is camped on nsa 5G
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
                                   self.wifi_network_pass, nr_type='nsa')


    @test_tracker_info(uuid="75066e0a-0e2e-4346-a253-6ed11d1c4d23")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_multi_bearer(self):
        """Test nsa5G data connection before call and in call. (VoLTE call)

        Turn off airplane mode, disable WiFi, enable Cellular Data.
        Make sure phone in nsa5G, verify Internet.
        Initiate a voice call. verify Internet.
        Disable Cellular Data, verify Internet is inaccessible.
        Enable Cellular Data, verify Internet.
        Hangup Voice Call, verify Internet.

        Returns:
            True if success.
            False if failed.
        """
        return test_data_connectivity_multi_bearer(
            self.log, self.android_devices, '5g_volte', nr_type='nsa')


    @test_tracker_info(uuid="e88b226e-3842-4c45-a33e-d4fee7d8f6f0")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_data_connectivity(self):
        """Test data connection in nsa5g.

        Turn off airplane mode, disable WiFi, enable Cellular Data.
        Ensure phone data generation is nsa 5g.
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
        return data_connectivity_single_bearer(ad.log, ad, GEN_5G, nr_type='nsa')


    @test_tracker_info(uuid="4c70e09d-f215-4c5b-8c61-f9e9def43d30")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_not_associated(self):
        """Test data connection in nsa 5g.

        Turn off airplane mode, enable WiFi (but not connected), enable Cellular Data.
        Ensure phone data generation is nsa 5g.
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
        return data_connectivity_single_bearer(ad.log, ad, GEN_5G, nr_type='nsa')


    @test_tracker_info(uuid="8308bf40-7f1b-443f-bde6-19d9ff97e471")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_connect_disconnect(self):
        """Perform multiple connects and disconnects from WiFi and verify that
            data switches between WiFi and Cell.

        Steps:
        1. DUT Cellular Data is on nsa 5G. Reset Wifi on DUT
        2. Connect DUT to a WiFi AP
        3. Repeat steps 1-2, alternately disconnecting and disabling wifi

        Expected Results:
        1. Verify Data on Cell
        2. Verify Data on Wifi

        Returns:
            True if success.
            False if failed.
        """
        if not provision_device_for_5g(self.log, self.provider, nr_type='nsa'):
            return False

        return test_wifi_connect_disconnect(self.log, self.provider, self.wifi_network_ssid, self.wifi_network_pass)

    @test_tracker_info(uuid="4a61c7c9-f4ed-4e21-b04b-d7a81347b8aa")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_airplane_mode(self):
        """Test airplane mode basic on Phone and Live SIM on 5G NSA.

        Ensure phone is on 5G NSA.
        Ensure phone attach, data on, WiFi off and verify Internet.
        Turn on airplane mode to make sure detach.
        Turn off airplane mode to make sure attach.
        Verify Internet connection.

        Returns:
            True if pass; False if fail.
        """
        if not provision_device_for_5g(self.log, self.android_devices[0], nr_type='nsa'):
            return False
        return airplane_mode_test(self.log, self.android_devices[0])

    @test_tracker_info(uuid="091cde37-0bac-4399-83aa-cbd5a83b07a1")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_reboot(self):
        """Test 5G NSA service availability after reboot.

        Ensure phone is on 5G NSA.
        Ensure phone attach, data on, WiFi off and verify Internet.
        Reboot Device.
        Verify Network Connection.

        Returns:
            True if pass; False if fail.
        """
        if not provision_device_for_5g(self.log, self.android_devices[0], nr_type='nsa'):
            return False
        if not verify_internet_connection(self.log, self.android_devices[0]):
            return False
        return reboot_test(self.log, self.android_devices[0])

    """ Tests End """
