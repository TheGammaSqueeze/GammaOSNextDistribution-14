#!/usr/bin/env python3
#
#   Copyright 2021 - The Android secure Source Project
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
from acts.controllers.ap_lib.hostapd_utils import generate_random_password
from acts.controllers.ap_lib.hostapd_security import Security
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device


class WlanMiscScenarioTest(WifiBaseTest):
    """Random scenario tests, usually to reproduce certain bugs, that do not
    fit into a specific test category, but should still be run in CI to catch
    regressions.
    """

    def setup_class(self):
        super().setup_class()
        dut = self.user_params.get('dut', None)
        if dut:
            if dut == 'fuchsia_devices':
                self.dut = create_wlan_device(self.fuchsia_devices[0])
            elif dut == 'android_devices':
                self.dut = create_wlan_device(self.android_devices[0])
            else:
                raise ValueError('Invalid DUT specified in config. (%s)' %
                                 self.user_params['dut'])
        else:
            # Default is an Fuchsia device
            self.dut = create_wlan_device(self.fuchsia_devices[0])
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

    def test_connect_to_wpa2_after_wpa3_rejection(self):
        """Test association to non-WPA3 network after receiving a WPA3
        rejection, which was triggering a firmware hang.

        Bug: https://bugs.fuchsia.dev/p/fuchsia/issues/detail?id=71233
        """
        # Setup a WPA3 network
        wpa3_ssid = utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=wpa3_ssid,
                 security=Security(security_mode='wpa3',
                                   password=generate_random_password('wpa3')))
        # Attempt to associate with wrong password, expecting failure
        self.log.info('Attempting to associate WPA3 with wrong password.')
        asserts.assert_false(
            self.dut.associate(wpa3_ssid,
                               target_pwd='wrongpass',
                               target_security='wpa3'),
            'Associated with WPA3 network using the wrong password')

        self.access_point.stop_all_aps()

        # Setup a WPA2 Network
        wpa2_ssid = utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_5G)
        wpa2_password = generate_random_password('wpa2')
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=wpa2_ssid,
                 security=Security(security_mode='wpa2',
                                   password=wpa2_password))

        # Attempt to associate, expecting success
        self.log.info('Attempting to associate with WPA2 network.')
        asserts.assert_true(
            self.dut.associate(wpa2_ssid,
                               target_pwd=wpa2_password,
                               target_security='wpa2'),
            'Failed to associate with WPA2 network after a WPA3 rejection.')
