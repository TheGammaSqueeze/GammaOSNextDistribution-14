#!/usr/bin/env python3
#
# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

from acts import asserts
from acts import utils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device

AP_ROLE = 'Ap'
DEFAULT_SSID = 'testssid'
DEFAULT_SECURITY = 'none'
DEFAULT_PASSWORD = ''
DEFAULT_CONNECTIVITY_MODE = 'local_only'
DEFAULT_OPERATING_BAND = 'any'
TEST_MAC_ADDR = '12:34:56:78:9a:bc'
TEST_MAC_ADDR_SECONDARY = 'bc:9a:78:56:34:12'


class WlanDeprecatedConfigurationTest(WifiBaseTest):
    """Tests for WlanDeprecatedConfigurationFacade"""

    def setup_class(self):
        super().setup_class()
        self.dut = create_wlan_device(self.fuchsia_devices[0])

    def setup_test(self):
        self._stop_soft_aps()

    def teardown_test(self):
        self._stop_soft_aps()

    def _get_ap_interface_mac_address(self):
        """Retrieves mac address from wlan interface with role ap

        Returns:
            string, the mac address of the AP interface

        Raises:
            ConnectionError, if SL4F calls fail
            AttributeError, if no interface has role 'Ap'
        """
        wlan_ifaces = self.dut.device.sl4f.wlan_lib.wlanGetIfaceIdList()
        if wlan_ifaces.get('error'):
            raise ConnectionError('Failed to get wlan interface IDs: %s' %
                                  wlan_ifaces['error'])

        for wlan_iface in wlan_ifaces['result']:
            iface_info = self.dut.device.sl4f.wlan_lib.wlanQueryInterface(
                wlan_iface)
            if iface_info.get('error'):
                raise ConnectionError('Failed to query wlan iface: %s' %
                                      iface_info['error'])

            if iface_info['result']['role'] == AP_ROLE:
                if 'mac_addr' in iface_info['result']:
                    return utils.mac_address_list_to_str(
                            iface_info['result']['mac_addr'])
                elif 'sta_addr' in iface_info['result']:
                    return utils.mac_address_list_to_str(
                            iface_info['result']['sta_addr'])
                raise AttributeError(
                    'AP iface info does not contain MAC address.')
        raise AttributeError(
            'Failed to get ap interface mac address. No AP interface found.')

    def _start_soft_ap(self):
        """Starts SoftAP on DUT.

        Raises:
            ConnectionError, if SL4F call fails.
        """
        self.log.info('Starting SoftAP on Fuchsia device (%s).' %
                      self.dut.device.ip)
        response = self.dut.device.sl4f.wlan_ap_policy_lib.wlanStartAccessPoint(
            DEFAULT_SSID, DEFAULT_SECURITY, DEFAULT_PASSWORD,
            DEFAULT_CONNECTIVITY_MODE, DEFAULT_OPERATING_BAND)
        if response.get('error'):
            raise ConnectionError('Failed to setup SoftAP: %s' %
                                  response['error'])

    def _stop_soft_aps(self):
        """Stops SoftAP on DUT.

        Raises:
            ConnectionError, if SL4F call fails.
        """
        self.log.info('Stopping SoftAP.')
        response = self.dut.device.sl4f.wlan_ap_policy_lib.wlanStopAllAccessPoint(
        )
        if response.get('error'):
            raise ConnectionError('Failed to stop SoftAP: %s' %
                                  response['error'])

    def _suggest_ap_mac_addr(self, mac_addr):
        """Suggests mac address for AP interface.
        Args:
            mac_addr: string, mac address to suggest.

        Raises:
            TestFailure, if SL4F call fails.
        """
        self.log.info(
            'Suggesting AP mac addr (%s) via wlan_deprecated_configuration_lib.'
            % mac_addr)
        response = (self.dut.device.sl4f.wlan_deprecated_configuration_lib.
                    wlanSuggestAccessPointMacAddress(mac_addr))
        if response.get('error'):
            asserts.fail('Failed to suggest AP mac address (%s): %s' %
                         (mac_addr, response['error']))

    def _verify_mac_addr(self, expected_addr):
        """ Verifies mac address of ap interface is set to expected mac address.

        Args:
            Args:
                expected_addr: string, expected mac address

            Raises:
                TestFailure, if actual mac address is not expected mac address.
        """
        set_mac_addr = self._get_ap_interface_mac_address()
        if set_mac_addr != expected_addr:
            asserts.fail(
                'Failed to set AP mac address '
                'via wlan_deprecated_configuration_lib. Expected mac addr: %s,'
                ' Actual mac addr: %s' % (expected_addr, set_mac_addr))
        else:
            self.log.info('AP mac address successfully set to %s' %
                          expected_addr)

    def test_suggest_ap_mac_address(self):
        """Tests suggest ap mac address SL4F call

        1. Get initial mac address
        2. Suggest new mac address
        3. Verify new mac address is set successfully
        4. Reset to initial mac address
        5. Verify initial mac address is reset successfully


        Raises:
            TestFailure, if wlanSuggestAccessPointMacAddress call fails or
                of mac address is not the suggest value
            ConnectionError, if other SL4F calls fail
        """
        # Retrieve initial ap mac address
        self._start_soft_ap()

        self.log.info('Getting initial mac address.')
        initial_mac_addr = self._get_ap_interface_mac_address()
        self.log.info('Initial mac address: %s' % initial_mac_addr)

        if initial_mac_addr != TEST_MAC_ADDR:
            suggested_mac_addr = TEST_MAC_ADDR
        else:
            suggested_mac_addr = TEST_MAC_ADDR_SECONDARY

        self._stop_soft_aps()

        # Suggest and verify new mac address
        self._suggest_ap_mac_addr(suggested_mac_addr)

        self._start_soft_ap()

        self._verify_mac_addr(suggested_mac_addr)

        self._stop_soft_aps()

        # Reset to initial mac address and verify
        self.log.info('Resetting to initial mac address (%s).' %
                      initial_mac_addr)
        self._suggest_ap_mac_addr(initial_mac_addr)

        self._start_soft_ap()

        self._verify_mac_addr(initial_mac_addr)
