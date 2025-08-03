#!/usr/bin/env python3.4
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
"""
    Test Script for 5G Tethering scenarios
"""

import time

from acts.utils import rand_ascii_str
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import NETWORK_SERVICE_DATA
from acts_contrib.test_utils.tel.tel_defines import RAT_2G
from acts_contrib.test_utils.tel.tel_defines import RAT_3G
from acts_contrib.test_utils.tel.tel_defines import RAT_4G
from acts_contrib.test_utils.tel.tel_defines import RAT_5G
from acts_contrib.test_utils.tel.tel_defines import TETHERING_PASSWORD_HAS_ESCAPE
from acts_contrib.test_utils.tel.tel_defines import TETHERING_SPECIAL_SSID_LIST
from acts_contrib.test_utils.tel.tel_defines import TETHERING_SPECIAL_PASSWORD_LIST
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_DATA_STATUS_CHANGE_DURING_WIFI_TETHERING
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_device_for_5g
from acts_contrib.test_utils.tel.tel_bt_utils import verify_bluetooth_tethering_connection
from acts_contrib.test_utils.tel.tel_data_utils import run_stress_test
from acts_contrib.test_utils.tel.tel_data_utils import test_wifi_tethering
from acts_contrib.test_utils.tel.tel_data_utils import test_setup_tethering
from acts_contrib.test_utils.tel.tel_data_utils import test_start_wifi_tethering_connect_teardown
from acts_contrib.test_utils.tel.tel_data_utils import test_tethering_wifi_and_voice_call
from acts_contrib.test_utils.tel.tel_data_utils import tethering_check_internet_connection
from acts_contrib.test_utils.tel.tel_data_utils import verify_toggle_apm_tethering_internet_connection
from acts_contrib.test_utils.tel.tel_data_utils import verify_tethering_entitlement_check
from acts_contrib.test_utils.tel.tel_data_utils import wifi_tethering_cleanup
from acts_contrib.test_utils.tel.tel_data_utils import wifi_tethering_setup_teardown
from acts_contrib.test_utils.tel.tel_data_utils import wait_and_verify_device_internet_connection
from acts_contrib.test_utils.tel.tel_data_utils import setup_device_internet_connection
from acts_contrib.test_utils.tel.tel_data_utils import verify_wifi_tethering_when_reboot
from acts_contrib.test_utils.tel.tel_data_utils import setup_device_internet_connection_then_reboot
from acts_contrib.test_utils.tel.tel_data_utils import verify_internet_connection_in_doze_mode
from acts_contrib.test_utils.tel.tel_data_utils import verify_toggle_data_during_wifi_tethering
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_csfb
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_voice_3g
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_network_generation
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_default_state
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_volte
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_csfb
from acts_contrib.test_utils.tel.tel_voice_utils import is_phone_in_call_3g
from acts_contrib.test_utils.tel.tel_wifi_utils import WIFI_CONFIG_APBAND_5G
from acts_contrib.test_utils.tel.tel_wifi_utils import WIFI_CONFIG_APBAND_2G
from acts_contrib.test_utils.tel.tel_wifi_utils import wifi_reset


class Nsa5gTetheringTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()
        self.stress_test_number = self.get_stress_test_number()
        self.provider = self.android_devices[0]
        self.clients = self.android_devices[1:]

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)
        self.number_of_devices = 1

    def teardown_class(self):
        TelephonyBaseTest.teardown_class(self)

    """ Tests Begin """


    @test_tracker_info(uuid="c7957e52-d5e5-499b-b387-fced88fda237")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_to_5gwifi(self):
        """WiFi Tethering test: 5G NSA to WiFI 5G Tethering

        1. DUT in 5G NSA mode, attached.
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
                                   nr_type= 'nsa')


    @test_tracker_info(uuid="0af10a6b-7c01-41fd-95ce-d839a787aa98")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_to_2gwifi(self):
        """WiFi Tethering test: 5G NSA to WiFI 2G Tethering

        1. DUT in 5G NSA mode, attached.
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
                                   nr_type= 'nsa')


    @test_tracker_info(uuid="d7ab31d5-5f96-4b48-aa92-810e6cfcf845")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_toggle_apm(self):
        """WiFi Tethering test: Toggle APM during active WiFi 2.4G Tethering from 5G NSA

        1. DUT in 5G NSA mode, idle.
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
                                       nr_type= 'nsa'):
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

    @test_tracker_info(uuid="3fa61282-eb08-469e-8d7a-1c790556e09c")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_toggle_data(self):
        """WiFi Tethering test: Toggle Data during active WiFi Tethering from 5G NSA

        1. DUT is on 5G NSA, DUT data connection is on and idle.
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
                                                        nr_type= 'nsa'):
            return False
        return True

    @test_tracker_info(uuid="2be31ba7-f69c-410b-86d1-daaeda892533")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_entitlement_check(self):
        """5G NSA Tethering Entitlement Check Test

        Get tethering entitlement check result.

        Returns:
            True if entitlement check returns True.
        """

        if not provision_device_for_5g(self.log, self.provider, nr_type= 'nsa'):
            return False
        return verify_tethering_entitlement_check(self.log,
                                                  self.provider)


    @test_tracker_info(uuid="f07c316c-dbab-4c95-8bec-2a2fc029d5cd")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_to_2gwifi_stress(self):
        """Stress Test 5G NSA to WiFI 2.4G Tethering

        This is a stress test for "test_5g_nsa_wifi_tethering_to_2gwifi".
        Default MINIMUM_SUCCESS_RATE is set to 95%.

        Returns:
            True stress pass rate is higher than MINIMUM_SUCCESS_RATE.
            False otherwise.
        """
        def precondition():
            return ensure_phones_default_state(self.log, self.android_devices)

        def test_case():
            return test_wifi_tethering(self.log,
                                       self.provider,
                                       self.clients,
                                       self.clients,
                                       RAT_5G,
                                       WIFI_CONFIG_APBAND_2G,
                                       check_interval=10,
                                       check_iteration=10,
                                       nr_type= 'nsa')
        return run_stress_test(self.log, self.stress_test_number, precondition, test_case)


    @test_tracker_info(uuid="bbe9175d-8781-4b31-9962-21abb9441022")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_ssid_quotes(self):
        """WiFi Tethering test: 5G NSA wifi tethering SSID name have quotes.
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
                                   nr_type= 'nsa')


    @test_tracker_info(uuid="678c6b04-6733-41e1-bb0c-af8c9d1183cb")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_password_escaping_characters(self):
        """WiFi Tethering test: 5G NSA wifi tethering password have escaping characters.
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
                                   nr_type= 'nsa')


    @test_tracker_info(uuid="eacc5412-fe75-400b-aba9-c0c38bdfff71")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_ssid(self):
        """WiFi Tethering test: start 5G NSA WiFi tethering with all kinds of SSIDs.

        For each listed SSID, start WiFi tethering on DUT, client connect WiFi,
        then tear down WiFi tethering.

        Returns:
            True if WiFi tethering succeed on all SSIDs.
            False if failed.
        """
        if not test_setup_tethering(self.log,
                                    self.provider,
                                    self.clients,
                                    RAT_5G,
                                    nr_type='nsa'):
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


    @test_tracker_info(uuid="249cfa53-edb2-4d9c-8f4f-8291bf22fb36")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_password(self):
        """WiFi Tethering test: start 5G NSA WiFi tethering with all kinds of passwords.

        For each listed password, start WiFi tethering on DUT, client connect WiFi,
        then tear down WiFi tethering.

        Returns:
            True if WiFi tethering succeed on all passwords.
            False if failed.
        """
        if not test_setup_tethering(self.log,
                                    self.provider,
                                    self.clients,
                                    RAT_5G,
                                    nr_type='nsa'):
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


    @test_tracker_info(uuid="9feb4c81-17c6-46ed-9cf7-7aa3d5ae2da0")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_bluetooth_tethering(self):
        """Bluetooth Tethering test: nsa 5G to Bluetooth Tethering

        1. DUT in nsa 5G mode, idle.
        2. DUT start Bluetooth Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB
        5. Toggle provider bluetooth connection
        6. Verify Internet access on DUT and PhoneB

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log,
                                    self.provider,
                                    self.clients,
                                    RAT_5G,
                                    nr_type='nsa'):
            self.log.error("Verify 5G Internet access failed.")
            return False

        return verify_bluetooth_tethering_connection(self.log,
                                                    self.provider,
                                                    self.clients)


    @test_tracker_info(uuid="db70c6ec-5edc-44c2-b61b-1c39516a7475")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_bluetooth_tethering_with_voice_call(self):
        """Bluetooth Tethering test: nsa 5G to Bluetooth Tethering

        1. DUT in nsa 5G mode, idle.
        2. DUT start Bluetooth Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB
        5. Verify provider and client are able to make or receive phone call
        6. Verify Internet access on DUT and PhoneB

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log,
                                    self.provider,
                                    self.clients,
                                    RAT_5G,
                                    nr_type='nsa'):
            self.log.error("Verify 5G Internet access failed.")
            return False

        return verify_bluetooth_tethering_connection(self.log,
                                                    self.provider,
                                                    self.clients,
                                                    toggle_tethering=False,
                                                    toggle_bluetooth=False,
                                                    voice_call=True)


    @test_tracker_info(uuid="12efb94f-7466-40e9-9a79-59b4074ab4dd")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_bluetooth_tethering_toggle_data(self):
        """Bluetooth Tethering test: nsa 5G to Bluetooth Tethering

        1. DUT in nsa 5G mode, idle.
        2. DUT start Bluetooth Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB
        5. Toggle provider data connection
        6. Verify Internet access on DUT and PhoneB

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log,
                                    self.provider,
                                    self.clients,
                                    RAT_5G,
                                    nr_type='nsa'):
            self.log.error("Verify 5G Internet access failed.")
            return False

        return verify_bluetooth_tethering_connection(self.log,
                                                    self.provider,
                                                    self.clients,
                                                    toggle_tethering=False,
                                                    toggle_bluetooth=False,
                                                    toggle_data=True)


    @test_tracker_info(uuid="475b485a-1228-4f18-b9f2-593f96850165")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_bluetooth_tethering_toggle_tethering(self):
        """Bluetooth Tethering test: nsa 5G to Bluetooth Tethering

        1. DUT in nsa 5G mode, idle.
        2. DUT start Bluetooth Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB
        5. Toggle provider bluetooth tethering
        6. Verify Internet access on DUT and PhoneB

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log,
                                    self.provider,
                                    self.clients,
                                    RAT_5G,
                                    nr_type='nsa'):
            self.log.error("Verify 5G Internet access failed.")
            return False

        return verify_bluetooth_tethering_connection(self.log,
                                                    self.provider,
                                                    self.clients,
                                                    toggle_tethering=True,
                                                    toggle_bluetooth=False,
                                                    toggle_data=False)


    @test_tracker_info(uuid="07f8e523-b471-4156-b057-558123973a5b")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_bluetooth_tethering_rat_from_5g_nsa_to_4g(self):
        """Bluetooth Tethering test: nsa 5G to 4G Bluetooth Tethering

        1. DUT in nsa 5G mode, idle.
        2. DUT start bluetooth Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB
        5. Change provider RAT to 4G
        6. Verify Internet access on DUT and PhoneB

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log,
                                    self.provider,
                                    self.clients,
                                    RAT_5G,
                                    nr_type='nsa'):
            self.log.error("Verify 5G Internet access failed.")
            return False

        return verify_bluetooth_tethering_connection(self.log,
                                                    self.provider,
                                                    self.clients,
                                                    toggle_tethering=False,
                                                    toggle_bluetooth=False,
                                                    toggle_data=False,
                                                    change_rat=RAT_4G)


    @test_tracker_info(uuid="93040a69-fa85-431f-ac9d-80091c6c8223")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_bluetooth_tethering_rat_from_5g_nsa_to_3g(self):
        """Bluetooth Tethering test: nsa5G to 3G Bluetooth Tethering

        1. DUT in nsa 5G mode, idle.
        2. DUT start bluetooth Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB
        5. Change provider RAT to 3G
        6. Verify Internet access on DUT and PhoneB

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log,
                                    self.provider,
                                    self.clients,
                                    RAT_5G,
                                    nr_type='nsa'):
            self.log.error("Verify 5G Internet access failed.")
            return False

        return verify_bluetooth_tethering_connection(self.log,
                                                    self.provider,
                                                    self.clients,
                                                    toggle_tethering=False,
                                                    toggle_bluetooth=False,
                                                    toggle_data=False,
                                                    change_rat=RAT_3G)


    @test_tracker_info(uuid="6cc17fc7-13a0-4493-9673-920952a16fcc")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_bluetooth_tethering_rat_from_5g_nsa_to_2g(self):
        """Bluetooth Tethering test: nsa5G to 2G Bluetooth Tethering

        1. DUT in nsa 5G mode, idle.
        2. DUT start bluetooth Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB
        5. Change provider RAT to 2G
        6. Verify Internet access on DUT and PhoneB

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log,
                                    self.provider,
                                    self.clients,
                                    RAT_5G,
                                    nr_type='nsa'):
            self.log.error("Verify 5G Internet access failed.")
            return False

        return verify_bluetooth_tethering_connection(self.log,
                                                    self.provider,
                                                    self.clients,
                                                    toggle_tethering=False,
                                                    toggle_bluetooth=False,
                                                    toggle_data=False,
                                                    change_rat=RAT_2G)


    # Invalid Live Test. Can't rely on the result of this test with live network.
    # Network may decide not to change the RAT when data connection is active.
    @test_tracker_info(uuid="8bef3215-8218-4f7f-9a5f-954fa36ac5d8")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_from_5g_nsa_to_3g(self):
        """WiFi Tethering test: Change Cellular Data RAT generation from nsa 5G to 3G,
            during active WiFi Tethering.

        1. DUT in nsa 5G mode, idle.
        2. DUT start 2.4G WiFi Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verily Internet access on DUT and PhoneB
        5. Change DUT Cellular Data RAT generation from nsa5G to 3G.
        6. Verify both DUT and PhoneB have Internet access.

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log,
                                    self.provider,
                                    self.clients,
                                    RAT_5G,
                                    nr_type='nsa'):
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

            self.log.info("Provider change RAT from nsa 5G to 3G.")
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
    @test_tracker_info(uuid="633f187d-3a8f-46dc-86de-f87b733a83ba")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_from_3g_to_5g_nsa(self):
        """WiFi Tethering test: Change Cellular Data RAT generation from 3G to nsa5G,
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
        if not test_setup_tethering(self.log,
                                    self.provider,
                                    self.clients,
                                    RAT_3G):
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

            self.log.info("Provider change RAT from 3G to 5G.")
            if not ensure_network_generation(
                    self.log,
                    self.provider,
                    RAT_4G,
                    voice_or_data=NETWORK_SERVICE_DATA,
                    toggle_apm_after_setting=False):
                self.log.error("Provider failed to reselect to LTE")
                return False
            if not provision_device_for_5g(self.log,
                                            self.provider,
                                            nr_type='nsa'):
                self.log.error("Provider failed to reselect to nsa 5G")
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
    @test_tracker_info(uuid="28e654a6-2318-4f36-a4f0-16214d8e004d")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_from_5g_nsa_to_4g(self):
        """WiFi Tethering test: Change Cellular Data RAT generation from nsa 5G to 4G,
            during active WiFi Tethering.

        1. DUT in nsa 5G mode, idle.
        2. DUT start 2.4G WiFi Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verily Internet access on DUT and PhoneB
        5. Change DUT Cellular Data RAT generation from nsa5G to LTE.
        6. Verify both DUT and PhoneB have Internet access.

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log,
                                    self.provider,
                                    self.clients,
                                    RAT_5G,
                                    nr_type='nsa'):
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
    @test_tracker_info(uuid="a8190c2a-a9ee-4673-b003-769691d1a575")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_from_4g_to_5g_nsa(self):
        """WiFi Tethering test: Change Cellular Data RAT generation from 4G to nsa5G,
            during active WiFi Tethering.

        1. DUT in 4G mode, idle.
        2. DUT start 2.4G WiFi Tethering
        3. PhoneB disable data, connect to DUT's softAP
        4. Verily Internet access on DUT and PhoneB
        5. Change DUT Cellular Data RAT generation from 4G to nsa5G.
        6. Verify both DUT and PhoneB have Internet access.

        Returns:
            True if success.
            False if failed.
        """
        if not test_setup_tethering(self.log,
                                    self.provider,
                                    self.clients,
                                    RAT_4G):
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
                    RAT_4G,
                    voice_or_data=NETWORK_SERVICE_DATA,
                    toggle_apm_after_setting=False):
                self.log.error("Provider failed to reselect to LTE")
                return False
            if not provision_device_for_5g(self.log,
                                            self.provider,
                                            nr_type='nsa'):
                self.log.error("Provider failed to reselect to nsa 5G")
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


    @test_tracker_info(uuid="ac3ff18a-69e0-4a8a-bbfa-41fad7f05200")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_volte_call(self):
        """WiFi Tethering test: VoLTE call during WiFi tethering
        1. Start LTE to WiFi (2.4G) tethering.
        2. Verify tethering.
        3. Make outgoing VoLTE call on tethering provider.
        4. Verify tethering still works.
        5. Make incoming VoLTE call on tethering provider.
        6. Verify tethering still works.

        Returns:
            True if success.
            False if failed.
        """
        return test_tethering_wifi_and_voice_call(self.log,
                                                self.provider,
                                                self.clients,
                                                RAT_5G,
                                                phone_setup_volte,
                                                is_phone_in_call_volte,
                                                nr_type='nsa')

    @test_tracker_info(uuid="f4b96666-ac71-49f2-89db-a792da7bb88c")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_csfb_call(self):
        """WiFi Tethering test: CSFB call during WiFi tethering
        1. Start 5G NSA to WiFi (2.4G) tethering.
        2. Verify tethering.
        3. Make outgoing CSFB call on tethering provider.
        4. Verify tethering still works.
        5. Make incoming CSFB call on tethering provider.
        6. Verify tethering still works.

        Returns:
            True if success.
            False if failed.
        """
        return test_tethering_wifi_and_voice_call(self.log,
                                                    self.provider,
                                                    self.clients,
                                                    RAT_5G,
                                                    phone_setup_csfb,
                                                    is_phone_in_call_csfb,
                                                    nr_type='nsa')

    @test_tracker_info(uuid="8cfa6ab6-6dcd-4ee5-97f2-db3b0f52ae17")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_3g_call(self):
        """WiFi Tethering test: 3G call during WiFi tethering
        1. Start 5G NSA to WiFi (2.4G) tethering.
        2. Verify tethering.
        3. Make outgoing CS call on tethering provider.
        4. Verify tethering still works.
        5. Make incoming CS call on tethering provider.
        6. Verify tethering still works.

        Returns:
            True if success.
            False if failed.
        """
        return test_tethering_wifi_and_voice_call(self.log,
                                                    self.provider,
                                                    self.clients,
                                                    RAT_5G,
                                                    phone_setup_voice_3g,
                                                    is_phone_in_call_3g,
                                                    nr_type='nsa')

    @test_tracker_info(uuid="ff1f71d7-142c-4e0d-94be-cadbc30828fd")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_no_password(self):
        """WiFi Tethering test: Start 5g NSA WiFi tethering with no password

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
                                   nr_type='nsa')

    @test_tracker_info(uuid="fd6daa93-2ecb-4a23-8f29-6d2db3b940c4")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_reboot(self):
        """WiFi Tethering test: Start 5G NSA to WiFi tethering then Reboot device

        1. DUT is in idle.
        2. DUT start 2.4G WiFi Tethering.
        3. PhoneB disable data, connect to DUT's softAP
        4. Verify Internet access on DUT and PhoneB
        5. Reboot DUT
        6. After DUT reboot, verify tethering is stopped.

        Returns:
            True if success.
            False if failed.
        """
        try:
            if not test_wifi_tethering(self.log,
                                       self.provider,
                                       self.clients,
                                       [self.clients[0]],
                                       RAT_5G,
                                       WIFI_CONFIG_APBAND_2G,
                                       check_interval=10,
                                       check_iteration=2,
                                       do_cleanup=False,
                                       nr_type='nsa'):
                return False

            if not verify_wifi_tethering_when_reboot(self.log,
                                                     self.provider):
                return False
        finally:
            if not wifi_tethering_cleanup(self.log,
                                          self.provider,
                                          self.clients):
                return False
        return True

    @test_tracker_info(uuid="b37b5fec-0fef-4206-aa86-1994b89c9369")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_connect_wifi_reboot(self):
        """WiFi Tethering test: WiFI connected, then start 5G NSA to WiFi tethering,
            then reboot device.

        Initial Condition: DUT in 5G NSA mode, idle, DUT connect to WiFi.
        1. DUT start 2.4G WiFi Tethering.
        2. PhoneB disable data, connect to DUT's softAP
        3. Verify Internet access on DUT and PhoneB
        4. Reboot DUT
        5. After DUT reboot, verify tethering is stopped. DUT is able to connect
            to previous WiFi AP.

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

        # wait for provider connecting to wifi network and verify
        # internet connection is working.
        def wait_and_verify_internet_connection():
            return wait_and_verify_device_internet_connection(self.log,
                                                              self.provider)

        try:
            if not test_wifi_tethering(self.log,
                                       self.provider,
                                       self.clients,
                                       [self.clients[0]],
                                       RAT_5G,
                                       WIFI_CONFIG_APBAND_2G,
                                       check_interval=10,
                                       check_iteration=2,
                                       do_cleanup=False,
                                       pre_teardown_func=setup_provider_internet_connection,
                                       nr_type='nsa'):
                return False

            if not verify_wifi_tethering_when_reboot(self.log,
                                                     self.provider,
                                                     post_reboot_func=wait_and_verify_internet_connection):
                return False

        finally:
            if not wifi_tethering_cleanup(self.log,
                                          self.provider,
                                          self.clients):
                return False
        return True

    @test_tracker_info(uuid="30050de6-4483-4533-b6ac-17625dd743c2")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_connect_reboot_tethering(self):
        """WiFi Tethering test: DUT connected to WiFi, then reboot,
        After reboot, start 5G NSA to WiFi tethering, verify tethering actually works.

        Initial Condition: Device set to 5G NSA mode, idle, DUT connect to WiFi.
        1. Verify Internet is working on DUT (by WiFi).
        2. Reboot DUT.
        3. DUT start 2.4G WiFi Tethering.
        4. PhoneB disable data, connect to DUT's softAP
        5. Verify Internet access on DUT and PhoneB

        Returns:
            True if success.
            False if failed.
        """

        # Ensure provider connecting to wifi network and then reboot.
        def setup_provider_internet_connect_then_reboot():
            return setup_device_internet_connection_then_reboot(self.log,
                                                                self.provider,
                                                                self.wifi_network_ssid,
                                                                self.wifi_network_pass)
        return test_wifi_tethering(self.log,
                                   self.provider,
                                   self.clients,
                                   [self.clients[0]],
                                   RAT_5G,
                                   WIFI_CONFIG_APBAND_2G,
                                   check_interval=10,
                                   check_iteration=2,
                                   pre_teardown_func=setup_provider_internet_connect_then_reboot,
                                   nr_type='nsa')

    @test_tracker_info(uuid="70f20bcf-8064-49e3-a3f0-ff151374d1ac")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_screen_off_enable_doze_mode(self):
        """WiFi Tethering test: Start 5G NSA WiFi tethering, then turn off DUT's screen,
            then enable doze mode, verify internet connection.

        1. Start 5G NSA WiFi tethering on DUT.
        2. PhoneB disable data, and connect to DUT's softAP
        3. Verify Internet access on DUT and PhoneB
        4. Turn off DUT's screen. Wait for 1 minute and
            verify Internet access on Client PhoneB.
        5. Enable doze mode on DUT. Wait for 1 minute and
            verify Internet access on Client PhoneB.
        6. Disable doze mode and turn off wifi tethering on DUT.

        Returns:
            True if success.
            False if failed.
        """
        try:
            if not test_wifi_tethering(self.log,
                                       self.provider,
                                       self.clients,
                                       [self.clients[0]],
                                       RAT_5G,
                                       WIFI_CONFIG_APBAND_2G,
                                       check_interval=10,
                                       check_iteration=2,
                                       do_cleanup=False,
                                       nr_type='nsa'):
                return False
            if not verify_internet_connection_in_doze_mode(self.log,
                                                           self.provider,
                                                           self.clients[0]):
                return False

        finally:
            if not wifi_tethering_cleanup(self.log,
                                          self.provider,
                                          [self.clients[0]]):
                return False
        return True

    @test_tracker_info(uuid="260c63d0-4398-4794-bb3e-1ff3db542ab5")
    @TelephonyBaseTest.tel_test_wrap
    def test_5g_nsa_wifi_tethering_disable_resume_wifi(self):
        """WiFi Tethering test: WiFI connected to 2.4G network,
        start (LTE) 2.4G WiFi tethering, then stop tethering over 5G NSA

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
                                   nr_type='nsa'):
            return False

        if not wait_and_verify_device_internet_connection(self.log, self.provider):
            return False
        return True

    """ Tests End """
