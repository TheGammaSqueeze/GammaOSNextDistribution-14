#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
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

from acts import asserts
from acts import utils
from acts.controllers.access_point import setup_ap
from acts.controllers.ap_lib import hostapd_constants
from acts.controllers.ap_lib.hostapd_security import Security
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device


# TODO(fxb/68956): Add security protocol check to mixed mode tests when info is
# available.
class WlanTargetSecurityTest(WifiBaseTest):
    """Tests Fuchsia's target security concept and security upgrading

    Testbed Requirements:
    * One Fuchsia device
    * One Whirlwind Access Point
    """

    def setup_class(self):
        if 'dut' in self.user_params and self.user_params[
                'dut'] != 'fuchsia_devices':
            raise AttributeError(
                'WlanTargetSecurityTest is only relevant for Fuchsia devices.')

        self.dut = create_wlan_device(self.fuchsia_devices[0])
        if self.dut.device.association_mechanism != 'policy':
            raise AttributeError(
                'Must use WLAN policy layer to test target security.')

        self.access_point = self.access_points[0]

    def teardown_class(self):
        self.dut.disconnect()
        self.access_point.stop_all_aps()

    def teardown_test(self):
        self.dut.disconnect()
        self.download_ap_logs()
        self.access_point.stop_all_aps()

    def on_fail(self, test_name, begin_time):
        super().on_fail(test_name, begin_time)
        self.access_point.stop_all_aps()

    def on_exception(self, test_name, begin_time):
        super().on_exception(test_name, begin_time)
        self.dut.disconnect()
        self.access_point.stop_all_aps()

    def setup_ap(self, security_mode=None):
        """ Sets up an AP using the provided security mode.

        Args:
            security_mode: string, security mode for AP
        Returns:
            Tuple, (ssid, password). Returns a password even if for open
                security, since non-open target securities require a credential
                to attempt a connection.
        """
        ssid = utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G)
        # Length 13, so it can be used for WEP or WPA
        password = utils.rand_ascii_str(13)
        security_profile = None

        if security_mode:
            security_profile = Security(security_mode=security_mode,
                                        password=password)

        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=ssid,
                 security=security_profile)

        return (ssid, password)

    # Open Security on AP
    def test_associate_open_ap_with_open_target_security(self):
        ssid, _ = self.setup_ap()
        asserts.assert_true(self.dut.associate(ssid), 'Failed to associate.')

    def test_reject_open_ap_with_wep_target_security(self):
        ssid, password = self.setup_ap()
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WEP_STRING,
                               target_pwd=password),
            'Should not have associated.')

    def test_reject_open_ap_with_wpa_target_security(self):
        ssid, password = self.setup_ap()
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA_STRING,
                               target_pwd=password),
            'Should not have associated.')

    def test_reject_open_ap_with_wpa2_target_security(self):
        ssid, password = self.setup_ap()
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA2_STRING,
                               target_pwd=password),
            'Should not have associated.')

    def test_reject_open_ap_with_wpa3_target_security(self):
        ssid, password = self.setup_ap()
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA3_STRING,
                               target_pwd=password),
            'Should not have associated.')

    # WEP Security on AP
    def test_reject_wep_ap_with_open_target_security(self):
        ssid, _ = self.setup_ap(hostapd_constants.WEP_STRING)
        asserts.assert_false(self.dut.associate(ssid),
                             'Should not have associated.')

    def test_associate_wep_ap_with_wep_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WEP_STRING)
        asserts.assert_true(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WEP_STRING,
                               target_pwd=password), 'Failed to associate.')

    def test_reject_wep_ap_with_wpa_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WEP_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA_STRING,
                               target_pwd=password),
            'Should not have associated.')

    def test_reject_wep_ap_with_wpa2_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WEP_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA2_STRING,
                               target_pwd=password),
            'Should not have associated.')

    def test_reject_wep_ap_with_wpa3_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WEP_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA3_STRING,
                               target_pwd=password),
            'Should not have associated.')

    # WPA Security on AP
    def test_reject_wpa_ap_with_open_target_security(self):
        ssid, _ = self.setup_ap(hostapd_constants.WPA_STRING)
        asserts.assert_false(self.dut.associate(ssid),
                             'Should not have associated.')

    def test_reject_wpa_ap_with_wep_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WEP_STRING,
                               target_pwd=password),
            'Should not have associated.')

    def test_associate_wpa_ap_with_wpa_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA_STRING)
        asserts.assert_true(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA_STRING,
                               target_pwd=password), 'Failed to associate.')

    def test_reject_wpa_ap_with_wpa2_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA2_STRING,
                               target_pwd=password),
            'Should not have associated.')

    def test_reject_wpa_ap_with_wpa3_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA3_STRING,
                               target_pwd=password),
            'Should not have associated.')

    # WPA2 Security on AP
    def test_reject_wpa2_ap_with_open_target_security(self):
        ssid, _ = self.setup_ap(hostapd_constants.WPA2_STRING)
        asserts.assert_false(self.dut.associate(ssid),
                             'Should not have associated.')

    def test_reject_wpa2_ap_with_wep_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA2_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WEP_STRING,
                               target_pwd=password),
            'Should not have associated.')

    def test_associate_wpa2_ap_with_wpa_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA2_STRING)
        asserts.assert_true(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA_STRING,
                               target_pwd=password), 'Failed to associate.')

    def test_associate_wpa2_ap_with_wpa2_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA2_STRING)
        asserts.assert_true(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA2_STRING,
                               target_pwd=password), 'Failed to associate.')

    def test_reject_wpa2_ap_with_wpa3_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA2_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA3_STRING,
                               target_pwd=password),
            'Should not have associated.')

    # WPA/WPA2 Security on AP
    def test_reject_wpa_wpa2_ap_with_open_target_security(self):
        ssid, _ = self.setup_ap(hostapd_constants.WPA_MIXED_STRING)
        asserts.assert_false(self.dut.associate(ssid),
                             'Should not have associated.')

    def test_reject_wpa_wpa2_ap_with_wep_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA_MIXED_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WEP_STRING,
                               target_pwd=password),
            'Should not have associated.')

    def test_associate_wpa_wpa2_ap_with_wpa_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA_MIXED_STRING)
        asserts.assert_true(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA_STRING,
                               target_pwd=password), 'Failed to associate.')

    def test_associate_wpa_wpa2_ap_with_wpa2_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA_MIXED_STRING)
        asserts.assert_true(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA2_STRING,
                               target_pwd=password), 'Failed to associate.')

    def test_reject_wpa_wpa2_ap_with_wpa3_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA_MIXED_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA3_STRING,
                               target_pwd=password),
            'Should not have associated.')

    # WPA3 Security on AP
    def test_reject_wpa3_ap_with_open_target_security(self):
        ssid, _ = self.setup_ap(hostapd_constants.WPA3_STRING)
        asserts.assert_false(self.dut.associate(ssid),
                             'Should not have associated.')

    def test_reject_wpa3_ap_with_wep_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA3_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WEP_STRING,
                               target_pwd=password),
            'Should not have associated.')

    def test_associate_wpa3_ap_with_wpa_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA3_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA_STRING,
                               target_pwd=password),
            'Expected failure to associate. WPA credentials for WPA3 was '
            'temporarily disabled, see https://fxbug.dev/85817 for context. '
            'If this feature was reenabled, please update this test\'s '
            'expectation.')

    def test_associate_wpa3_ap_with_wpa2_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA3_STRING)
        asserts.assert_true(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA2_STRING,
                               target_pwd=password), 'Failed to associate.')

    def test_associate_wpa3_ap_with_wpa3_target_security(self):
        ssid, password = self.setup_ap(hostapd_constants.WPA3_STRING)
        asserts.assert_true(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA3_STRING,
                               target_pwd=password), 'Failed to associate.')

    # WPA2/WPA3 Security on AP
    def test_reject_wpa2_wpa3_ap_with_open_target_security(self):
        ssid, _ = self.setup_ap(hostapd_constants.WPA2_WPA3_MIXED_STRING)
        asserts.assert_false(self.dut.associate(ssid),
                             'Should not have associated.')

    def test_reject_wpa2_wpa3_ap_with_wep_target_security(self):
        ssid, password = self.setup_ap(
            hostapd_constants.WPA2_WPA3_MIXED_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WEP_STRING,
                               target_pwd=password),
            'Should not have associated.')

    def test_associate_wpa2_wpa3_ap_with_wpa_target_security(self):
        ssid, password = self.setup_ap(
            hostapd_constants.WPA2_WPA3_MIXED_STRING)
        asserts.assert_false(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA_STRING,
                               target_pwd=password),
            'Expected failure to associate. WPA credentials for WPA3 was '
            'temporarily disabled, see https://fxbug.dev/85817 for context. '
            'If this feature was reenabled, please update this test\'s '
            'expectation.')

    def test_associate_wpa2_wpa3_ap_with_wpa2_target_security(self):
        ssid, password = self.setup_ap(
            hostapd_constants.WPA2_WPA3_MIXED_STRING)
        asserts.assert_true(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA2_STRING,
                               target_pwd=password), 'Failed to associate.')

    def test_associate_wpa2_wpa3_ap_with_wpa3_target_security(self):
        ssid, password = self.setup_ap(
            hostapd_constants.WPA2_WPA3_MIXED_STRING)
        asserts.assert_true(
            self.dut.associate(ssid,
                               target_security=hostapd_constants.WPA3_STRING,
                               target_pwd=password), 'Failed to associate.')
