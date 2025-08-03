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
    Test Script for 5G NSA MMWAVE Tethering scenarios
"""

import time

from acts.utils import rand_ascii_str
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import NETWORK_SERVICE_DATA
from acts_contrib.test_utils.tel.tel_defines import RAT_3G
from acts_contrib.test_utils.tel.tel_defines import RAT_4G
from acts_contrib.test_utils.tel.tel_defines import RAT_5G
from acts_contrib.test_utils.tel.tel_defines import TETHERING_PASSWORD_HAS_ESCAPE
from acts_contrib.test_utils.tel.tel_defines import TETHERING_SPECIAL_SSID_LIST
from acts_contrib.test_utils.tel.tel_defines import TETHERING_SPECIAL_PASSWORD_LIST
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_DATA_STATUS_CHANGE_DURING_WIFI_TETHERING
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_device_for_5g
from acts_contrib.test_utils.tel.tel_data_utils import test_wifi_tethering
from acts_contrib.test_utils.tel.tel_data_utils import test_setup_tethering
from acts_contrib.test_utils.tel.tel_data_utils import test_start_wifi_tethering_connect_teardown
from acts_contrib.test_utils.tel.tel_data_utils import tethering_check_internet_connection
from acts_contrib.test_utils.tel.tel_data_utils import verify_toggle_apm_tethering_internet_connection
from acts_contrib.test_utils.tel.tel_data_utils import verify_tethering_entitlement_check
from acts_contrib.test_utils.tel.tel_data_utils import wifi_tethering_cleanup
from acts_contrib.test_utils.tel.tel_data_utils import wifi_tethering_setup_teardown
from acts_contrib.test_utils.tel.tel_data_utils import wait_and_verify_device_internet_connection
from acts_contrib.test_utils.tel.tel_data_utils import setup_device_internet_connection
from acts_contrib.test_utils.tel.tel_data_utils import verify_toggle_data_during_wifi_tethering
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_network_generation
from acts_contrib.test_utils.tel.tel_test_utils import set_phone_silent_mode
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.tel.tel_wifi_utils import WIFI_CONFIG_APBAND_5G
from acts_contrib.test_utils.tel.tel_wifi_utils import WIFI_CONFIG_APBAND_2G
from acts_contrib.test_utils.tel.tel_wifi_utils import wifi_reset


class Nsa5gMmwTetheringTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()
        self.stress_test_number = self.get_stress_test_number()
        self.provider = self.android_devices[0]
        self.clients = self.android_devices[1:]
        for ad in self.android_devices:
            set_phone_silent_mode(self.log, ad, True)

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)
        self.number_of_devices = 1

    def teardown_class(self):
        TelephonyBaseTest.teardown_class(self)


    """ Tests Begin """


    @test_tracker_info(uuid="ae6c4a14-0474-448c-ad18-dcedfee7fa5a")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_to_5gwifi(self):
        """WiFi Tethering test: 5G NSA MMW to WiFI 5G Tethering

        1. DUT in 5G NSA MMW mode, attached.
        2. DUT start 5G WiFi Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB

        Returns:
            True if success.
            False if failed.
        """
        return test_wifi_tethering(self.log,
                                   self.provider,
                                   self.clients,
                                   self.clients,
                                   RAT_5G,
                                   WIFI_CONFIG_APBAND_5G,
                                   check_interval=10,
                                   check_iteration=10,
                                   nr_type= 'mmwave')


    @test_tracker_info(uuid="bf6ed593-4fe3-417c-9d04-ad71a8d3095e")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_to_2gwifi(self):
        """WiFi Tethering test: 5G NSA MMW to WiFI 2G Tethering

        1. DUT in 5G NSA MMW mode, attached.
        2. DUT start 2.4G WiFi Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB

        Returns:
            True if success.
            False if failed.
        """
        return test_wifi_tethering(self.log,
                                   self.provider,
                                   self.clients,
                                   self.clients,
                                   RAT_5G,
                                   WIFI_CONFIG_APBAND_2G,
                                   check_interval=10,
                                   check_iteration=10,
                                   nr_type= 'mmwave')


    @test_tracker_info(uuid="96c4bc30-6dd1-4f14-bdbd-bf40b8b24701")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_toggle_apm(self):
        """WiFi Tethering test: Toggle APM during active WiFi 2.4G Tethering from 5G NSA MMW

        1. DUT in 5G NSA MMW mode, idle.
        2. DUT start 2.4G WiFi Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB
        5. DUT toggle APM on, verify WiFi tethering stopped, PhoneB lost WiFi connection.
        6. DUT toggle APM off, verify PhoneA have cellular data and Internet connection.

        Returns:
            True if success.
            False if failed.
        """
        try:
            ssid = rand_ascii_str(10)
            if not test_wifi_tethering(self.log,
                                       self.provider,
                                       self.clients,
                                       [self.clients[0]],
                                       RAT_5G,
                                       WIFI_CONFIG_APBAND_2G,
                                       check_interval=10,
                                       check_iteration=2,
                                       do_cleanup=False,
                                       ssid=ssid,
                                       nr_type= 'mmwave'):
                self.log.error("WiFi Tethering failed.")
                return False

            if not verify_toggle_apm_tethering_internet_connection(self.log,
                                                                   self.provider,
                                                                   self.clients,
                                                                   ssid):
                return False
        finally:
            self.clients[0].droid.telephonyToggleDataConnection(True)
            wifi_reset(self.log, self.clients[0])
        return True


    @test_tracker_info(uuid="e4f7deaa-a2be-4543-9364-17d704b2bf44")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_toggle_data(self):
        """WiFi Tethering test: Toggle Data during active WiFi Tethering from 5G NSA MMW

        1. DUT is on 5G NSA MMW, DUT data connection is on and idle.
        2. DUT start 2.4G WiFi Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB
        5. Disable Data on DUT, verify PhoneB still connected to WiFi, but no Internet access.
        6. Enable Data on DUT, verify PhoneB still connected to WiFi and have Internet access.

        Returns:
            True if success.
            False if failed.
        """
        if not verify_toggle_data_during_wifi_tethering(self.log,
                                                        self.provider,
                                                        self.clients,
                                                        new_gen=RAT_5G,
                                                        nr_type= 'mmwave'):
            return False
        return True


    @test_tracker_info(uuid="e6c30776-c245-42aa-a211-77dbd76c5217")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_entitlement_check(self):
        """5G NSA MMW Tethering Entitlement Check Test

        Get tethering entitlement check result.

        Returns:
            True if entitlement check returns True.
        """

        if not provision_device_for_5g(self.log, self.provider, nr_type= 'mmwave'):
            return False
        return verify_tethering_entitlement_check(self.log,
                                                  self.provider)


    @test_tracker_info(uuid="a73ca034-c90c-4579-96dd-9518d74c2a6c")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_ssid_quotes(self):
        """WiFi Tethering test: 5G NSA MMW wifi tethering SSID name have quotes.
        1. Set SSID name have double quotes.
        2. Start LTE to WiFi (2.4G) tethering.
        3. Verify tethering.

        Returns:
            True if success.
            False if failed.
        """
        ssid = "\"" + rand_ascii_str(10) + "\""
        self.log.info(
            "Starting WiFi Tethering test with ssid: {}".format(ssid))

        return test_wifi_tethering(self.log,
                                   self.provider,
                                   self.clients,
                                   self.clients,
                                   RAT_5G,
                                   WIFI_CONFIG_APBAND_2G,
                                   check_interval=10,
                                   check_iteration=10,
                                   ssid=ssid,
                                   nr_type= 'mmwave')


    @test_tracker_info(uuid="6702831b-f656-4410-a922-d47fae138d68")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_password_escaping_characters(self):
        """WiFi Tethering test: 5G NSA MMW wifi tethering password have escaping characters.
        1. Set password have escaping characters.
            e.g.: '"DQ=/{Yqq;M=(^_3HzRvhOiL8S%`]w&l<Qp8qH)bs<4E9v_q=HLr^)}w$blA0Kg'
        2. Start LTE to WiFi (2.4G) tethering.
        3. Verify tethering.

        Returns:
            True if success.
            False if failed.
        """

        password = TETHERING_PASSWORD_HAS_ESCAPE
        self.log.info(
            "Starting WiFi Tethering test with password: {}".format(password))

        return test_wifi_tethering(self.log,
                                   self.provider,
                                   self.clients,
                                   self.clients,
                                   RAT_5G,
                                   WIFI_CONFIG_APBAND_2G,
                                   check_interval=10,
                                   check_iteration=10,
                                   password=password,
                                   nr_type= 'mmwave')


    @test_tracker_info(uuid="93cf9aa2-740f-42a4-92a8-c506ceb5d448")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_ssid(self):
        """WiFi Tethering test: start 5G NSA MMW WiFi tethering with all kinds of SSIDs.

        For each listed SSID, start WiFi tethering on DUT, client connect WiFi,
        then tear down WiFi tethering.

        Returns:
            True if WiFi tethering succeed on all SSIDs.
            False if failed.
        """
        if not test_setup_tethering(self.log, self.provider, self.clients, RAT_5G, nr_type= 'mmwave'):
            self.log.error("Setup Failed.")
            return False
        ssid_list = TETHERING_SPECIAL_SSID_LIST
        fail_list = {}
        self.number_of_devices = 2
        for ssid in ssid_list:
            password = rand_ascii_str(8)
            self.log.info("SSID: <{}>, Password: <{}>".format(ssid, password))
            if not test_start_wifi_tethering_connect_teardown(self.log,
                                                              self.provider,
                                                              self.clients[0],
                                                              ssid,
                                                              password):
                fail_list[ssid] = password

        if len(fail_list) > 0:
            self.log.error("Failed cases: {}".format(fail_list))
            return False
        else:
            return True


    @test_tracker_info(uuid="ed73ed58-781b-4fe4-991e-fa0cc2726b0d")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_password(self):
        """WiFi Tethering test: start 5G NSA MMW WiFi tethering with all kinds of passwords.

        For each listed password, start WiFi tethering on DUT, client connect WiFi,
        then tear down WiFi tethering.

        Returns:
            True if WiFi tethering succeed on all passwords.
            False if failed.
        """
        if not test_setup_tethering(self.log, self.provider, self.clients, RAT_5G, nr_type= 'mmwave'):
            self.log.error("Setup Failed.")
            return False
        password_list = TETHERING_SPECIAL_PASSWORD_LIST
        fail_list = {}
        self.number_of_devices = 2
        for password in password_list:
            ssid = rand_ascii_str(8)
            self.log.info("SSID: <{}>, Password: <{}>".format(ssid, password))
            if not test_start_wifi_tethering_connect_teardown(self.log,
                                                              self.provider,
                                                              self.clients[0],
                                                              ssid,
                                                              password):
                fail_list[ssid] = password

        if len(fail_list) > 0:
            self.log.error("Failed cases: {}".format(fail_list))
            return False
        else:
            return True


    # Invalid Live Test. Can't rely on the result of this test with live network.
    # Network may decide not to change the RAT when data connection is active.
    @test_tracker_info(uuid="ac18159b-ebfb-42d1-b97b-ff25c5cb7b9e")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_from_5g_nsa_mmw_to_3g(self):
        """WiFi Tethering test: Change Cellular Data RAT generation from 5G NSA MMW to 3G,
            during active WiFi Tethering.

        1. DUT in 5G NSA MMW mode, idle.
        2. DUT start 2.4G WiFi Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verily Internet access on DUT and PhoneB
        5. Change DUT Cellular Data RAT generation from 5G NSA MMW to 3G.
        6. Verify both DUT and PhoneB have Internet access.

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log, self.provider, self.clients, RAT_5G, nr_type= 'mmwave'):
            self.log.error("Verify 5G Internet access failed.")
            return False
        try:
            if not wifi_tethering_setup_teardown(
                    self.log,
                    self.provider, [self.clients[0]],
                    ap_band=WIFI_CONFIG_APBAND_2G,
                    check_interval=10,
                    check_iteration=2,
                    do_cleanup=False):
                self.log.error("WiFi Tethering failed.")
                return False

            if not self.provider.droid.wifiIsApEnabled():
                self.provider.log.error("Provider WiFi tethering stopped.")
                return False

            self.log.info("Provider change RAT from 5G NSA MMW to 3G.")
            if not ensure_network_generation(
                    self.log,
                    self.provider,
                    RAT_3G,
                    voice_or_data=NETWORK_SERVICE_DATA,
                    toggle_apm_after_setting=False):
                self.provider.log.error("Provider failed to reselect to 3G.")
                return False
            time.sleep(WAIT_TIME_DATA_STATUS_CHANGE_DURING_WIFI_TETHERING)
            if not verify_internet_connection(self.log, self.provider):
                self.provider.log.error("Data not available on Provider.")
                return False
            if not self.provider.droid.wifiIsApEnabled():
                self.provider.log.error("Provider WiFi tethering stopped.")
                return False
            if not tethering_check_internet_connection(
                    self.log, self.provider, [self.clients[0]], 10, 5):
                return False
        finally:
            if not wifi_tethering_cleanup(self.log, self.provider,
                                          self.clients):
                return False
        return True


    # Invalid Live Test. Can't rely on the result of this test with live network.
    # Network may decide not to change the RAT when data connection is active.
    @test_tracker_info(uuid="5a2dc4f4-f6ea-4162-b034-4919997161ac")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_from_3g_to_5g_nsa_mmw(self):
        """WiFi Tethering test: Change Cellular Data RAT generation from 3G to 5G NSA MMW,
            during active WiFi Tethering.

        1. DUT in 3G mode, idle.
        2. DUT start 2.4G WiFi Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verily Internet access on DUT and PhoneB
        5. Change DUT Cellular Data RAT generation from 3G to nsa5G.
        6. Verify both DUT and PhoneB have Internet access.

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log, self.provider, self.clients, RAT_3G):
            self.log.error("Verify 3G Internet access failed.")
            return False
        try:
            if not wifi_tethering_setup_teardown(
                    self.log,
                    self.provider, [self.clients[0]],
                    ap_band=WIFI_CONFIG_APBAND_2G,
                    check_interval=10,
                    check_iteration=2,
                    do_cleanup=False):
                self.log.error("WiFi Tethering failed.")
                return False

            if not self.provider.droid.wifiIsApEnabled():
                self.log.error("Provider WiFi tethering stopped.")
                return False

            self.log.info("Provider change RAT from 3G to 5G NSA MMW.")
            if not ensure_network_generation(
                    self.log,
                    self.provider,
                    RAT_5G,
                    voice_or_data=NETWORK_SERVICE_DATA,
                    toggle_apm_after_setting=False,
                    nr_type= 'mmwave'):
                self.log.error("Provider failed to reselect to 5G NSA MMW")
                return False

            time.sleep(WAIT_TIME_DATA_STATUS_CHANGE_DURING_WIFI_TETHERING)
            if not verify_internet_connection(self.log, self.provider):
                self.provider.log.error("Data not available on Provider.")
                return False
            if not self.provider.droid.wifiIsApEnabled():
                self.provider.log.error("Provider WiFi tethering stopped.")
                return False
            if not tethering_check_internet_connection(
                    self.log, self.provider, [self.clients[0]], 10, 5):
                return False
        finally:
            if not wifi_tethering_cleanup(self.log, self.provider, [self.clients[0]]):
                return False
        return True


    # Invalid Live Test. Can't rely on the result of this test with live network.
    # Network may decide not to change the RAT when data connection is active.
    @test_tracker_info(uuid="ac0a5f75-3f08-40fb-83ca-3312019680b9")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_from_5g_nsa_mmw_to_4g(self):
        """WiFi Tethering test: Change Cellular Data RAT generation from 5G NSA MMW to 4G,
            during active WiFi Tethering.

        1. DUT in 5G NSA MMW mode, idle.
        2. DUT start 2.4G WiFi Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verily Internet access on DUT and PhoneB
        5. Change DUT Cellular Data RAT generation from 5G NSA MMW to LTE.
        6. Verify both DUT and PhoneB have Internet access.

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log, self.provider, self.clients, RAT_5G, nr_type= 'mmwave'):
            self.log.error("Verify 5G Internet access failed.")
            return False
        try:
            if not wifi_tethering_setup_teardown(
                    self.log,
                    self.provider, [self.clients[0]],
                    ap_band=WIFI_CONFIG_APBAND_2G,
                    check_interval=10,
                    check_iteration=2,
                    do_cleanup=False):
                self.log.error("WiFi Tethering failed.")
                return False

            if not self.provider.droid.wifiIsApEnabled():
                self.provider.log.error("Provider WiFi tethering stopped.")
                return False

            self.log.info("Provider change RAT from 5G to LTE.")
            if not ensure_network_generation(
                    self.log,
                    self.provider,
                    RAT_4G,
                    voice_or_data=NETWORK_SERVICE_DATA,
                    toggle_apm_after_setting=False):
                self.provider.log.error("Provider failed to reselect to 4G.")
                return False
            time.sleep(WAIT_TIME_DATA_STATUS_CHANGE_DURING_WIFI_TETHERING)
            if not verify_internet_connection(self.log, self.provider):
                self.provider.log.error("Data not available on Provider.")
                return False
            if not self.provider.droid.wifiIsApEnabled():
                self.provider.log.error("Provider WiFi tethering stopped.")
                return False
            if not tethering_check_internet_connection(
                    self.log, self.provider, [self.clients[0]], 10, 5):
                return False
        finally:
            if not wifi_tethering_cleanup(self.log, self.provider,
                                          self.clients):
                return False
        return True


    # Invalid Live Test. Can't rely on the result of this test with live network.
    # Network may decide not to change the RAT when data connection is active.
    @test_tracker_info(uuid="9335bfdc-d0df-4c5e-99fd-6492a2ce2947")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_from_4g_to_5g_nsa_mmw(self):
        """WiFi Tethering test: Change Cellular Data RAT generation from 4G to 5G NSA MMW,
            during active WiFi Tethering.

        1. DUT in 4G mode, idle.
        2. DUT start 2.4G WiFi Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verily Internet access on DUT and PhoneB
        5. Change DUT Cellular Data RAT generation from 4G to 5G NSA MMW.
        6. Verify both DUT and PhoneB have Internet access.

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log, self.provider, self.clients, RAT_4G):
            self.log.error("Verify 4G Internet access failed.")
            return False
        try:
            if not wifi_tethering_setup_teardown(
                    self.log,
                    self.provider, [self.clients[0]],
                    ap_band=WIFI_CONFIG_APBAND_2G,
                    check_interval=10,
                    check_iteration=2,
                    do_cleanup=False):
                self.log.error("WiFi Tethering failed.")
                return False

            if not self.provider.droid.wifiIsApEnabled():
                self.log.error("Provider WiFi tethering stopped.")
                return False

            self.log.info("Provider change RAT from 4G to 5G.")
            if not ensure_network_generation(
                    self.log,
                    self.provider,
                    RAT_5G,
                    voice_or_data=NETWORK_SERVICE_DATA,
                    toggle_apm_after_setting=False,
                    nr_type= 'mmwave'):
                self.log.error("Provider failed to reselect to 5G NSA MMW")
                return False

            time.sleep(WAIT_TIME_DATA_STATUS_CHANGE_DURING_WIFI_TETHERING)
            if not verify_internet_connection(self.log, self.provider):
                self.provider.log.error("Data not available on Provider.")
                return False
            if not self.provider.droid.wifiIsApEnabled():
                self.provider.log.error("Provider WiFi tethering stopped.")
                return False
            if not tethering_check_internet_connection(
                    self.log, self.provider, [self.clients[0]], 10, 5):
                return False
        finally:
            if not wifi_tethering_cleanup(self.log, self.provider, [self.clients[0]]):
                return False
        return True


    @test_tracker_info(uuid="7956472e-962c-4bbe-a08d-37901935c9ac")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_no_password(self):
        """WiFi Tethering test: Start 5G NSA MMW WiFi tethering with no password

        1. DUT is idle.
        2. DUT start 2.4G WiFi Tethering, with no WiFi password.
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB

        Returns:
            True if success.
            False if failed.
        """
        return test_wifi_tethering(self.log,
                                   self.provider,
                                   self.clients,
                                   [self.clients[0]],
                                   RAT_5G,
                                   WIFI_CONFIG_APBAND_2G,
                                   check_interval=10,
                                   check_iteration=10,
                                   password="",
                                   nr_type= 'mmwave')


    @test_tracker_info(uuid="39e73f91-79c7-4cc0-9fa0-a737f88889e8")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_mmw_wifi_tethering_disable_resume_wifi(self):
        """WiFi Tethering test: WiFI connected to 2.4G network,
        start (LTE) 2.4G WiFi tethering, then stop tethering over 5G NSA MMW

        1. DUT in data connected, idle. WiFi connected to 2.4G Network
        2. DUT start 2.4G WiFi Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB
        5. Disable WiFi Tethering on DUT.
        6. Verify DUT automatically connect to previous WiFI network

        Returns:
            True if success.
            False if failed.
        """
        # Ensure provider connecting to wifi network.
        def setup_provider_internet_connection():
            return setup_device_internet_connection(self.log,
                                                    self.provider,
                                                    self.wifi_network_ssid,
                                                    self.wifi_network_pass)

        if not test_wifi_tethering(self.log,
                                   self.provider,
                                   self.clients,
                                   [self.clients[0]],
                                   RAT_5G,
                                   WIFI_CONFIG_APBAND_2G,
                                   check_interval=10,
                                   check_iteration=2,
                                   pre_teardown_func=setup_provider_internet_connection,
                                   nr_type= 'mmwave'):
            return False

        if not wait_and_verify_device_internet_connection(self.log, self.provider):
            return False
        return True


    """ Tests End """
