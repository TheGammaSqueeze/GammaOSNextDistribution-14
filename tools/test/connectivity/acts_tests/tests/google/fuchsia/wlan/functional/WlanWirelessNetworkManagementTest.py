#!/usr/bin/env python3
#
#   Copyright 2022 - The Android Open Source Project
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

import time

from datetime import datetime, timedelta, timezone
from typing import FrozenSet

from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device
from acts import asserts
from acts import signals
from acts import utils
from acts.controllers.access_point import setup_ap
from acts.controllers.ap_lib import hostapd_constants
from acts.controllers.ap_lib.radio_measurement import BssidInformation, BssidInformationCapabilities, NeighborReportElement, PhyType
from acts.controllers.ap_lib.wireless_network_management import BssTransitionManagementRequest


# TODO(fxbug.dev/103440) WNM support should be visible/controllable in ACTS.
# When ACTS can see WNM features that are enabled (through ACTS config) or
# ACTS can enable WNM features (through new APIs), additional tests should be
# added to this suite that check that features function properly when the DUT is
# configured to support those features.
class WlanWirelessNetworkManagementTest(WifiBaseTest):
    """Tests Fuchsia's Wireless Network Management (AKA 802.11v) support.

    Testbed Requirements:
    * One Fuchsia device
    * One Whirlwind access point

    Existing Fuchsia drivers do not yet support WNM features out-of-the-box, so these
    tests check that WNM features are not enabled.
    """

    def setup_class(self):
        if 'dut' in self.user_params and self.user_params[
                'dut'] != 'fuchsia_devices':
            raise AttributeError(
                'WlanWirelessNetworkManagementTest is only relevant for Fuchsia devices.'
            )

        self.dut = create_wlan_device(self.fuchsia_devices[0])
        if self.dut.device.association_mechanism != 'policy':
            raise AttributeError('Must use WLAN policy layer to test WNM.')
        self.access_point = self.access_points[0]

    def teardown_class(self):
        self.dut.disconnect()
        self.access_point.stop_all_aps()

    def teardown_test(self):
        self.dut.disconnect()
        self.download_ap_logs()
        self.access_point.stop_all_aps()

    def on_fail(self, test_name: str, begin_time: str):
        super().on_fail(test_name, begin_time)
        self.access_point.stop_all_aps()

    def on_exception(self, test_name: str, begin_time: str):
        super().on_exception(test_name, begin_time)
        self.dut.disconnect()
        self.access_point.stop_all_aps()

    def setup_ap(
        self,
        ssid: str,
        channel: int = hostapd_constants.AP_DEFAULT_CHANNEL_2G,
        wnm_features: FrozenSet[hostapd_constants.WnmFeature] = frozenset()):
        """Sets up an AP using the provided parameters.

        Args:
            ssid: SSID for the AP.
            channel: which channel number to set the AP to (default is
                AP_DEFAULT_CHANNEL_2G).
            wnm_features: Wireless Network Management features to enable
                (default is no WNM features).
        """
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind',
                 channel=channel,
                 ssid=ssid,
                 security=None,
                 wnm_features=wnm_features)

    def _get_client_mac(self) -> str:
        """Get the MAC address of the DUT client interface.

        Returns:
            str, MAC address of the DUT client interface.
        Raises:
            ValueError if there is no DUT client interface.
            ConnectionError if the DUT interface query fails.
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

            if iface_info['result']['role'] == 'Client':
                return utils.mac_address_list_to_str(
                    iface_info['result']['sta_addr'])
        raise ValueError(
            'Failed to get client interface mac address. No client interface found.'
        )

    def test_bss_transition_ap_supported_dut_unsupported(self):
        ssid = utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G)
        wnm_features = frozenset(
            [hostapd_constants.WnmFeature.BSS_TRANSITION_MANAGEMENT])
        self.setup_ap(ssid, wnm_features=wnm_features)
        asserts.assert_true(self.dut.associate(ssid), 'Failed to associate.')
        asserts.assert_true(self.dut.is_connected(), 'Failed to connect.')
        client_mac = self._get_client_mac()

        ext_capabilities = self.access_point.get_sta_extended_capabilities(
            self.access_point.wlan_2g, client_mac)
        asserts.assert_false(
            ext_capabilities.bss_transition,
            'DUT is incorrectly advertising BSS Transition Management support')

    def test_wnm_sleep_mode_ap_supported_dut_unsupported(self):
        ssid = utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G)
        wnm_features = frozenset([hostapd_constants.WnmFeature.WNM_SLEEP_MODE])
        self.setup_ap(ssid, wnm_features=wnm_features)
        asserts.assert_true(self.dut.associate(ssid), 'Failed to associate.')
        asserts.assert_true(self.dut.is_connected(), 'Failed to connect.')
        client_mac = self._get_client_mac()

        ext_capabilities = self.access_point.get_sta_extended_capabilities(
            self.access_point.wlan_2g, client_mac)
        asserts.assert_false(
            ext_capabilities.wnm_sleep_mode,
            'DUT is incorrectly advertising WNM Sleep Mode support')

    def test_btm_req_ignored_dut_unsupported(self):
        ssid = utils.rand_ascii_str(hostapd_constants.AP_SSID_LENGTH_2G)
        wnm_features = frozenset(
            [hostapd_constants.WnmFeature.BSS_TRANSITION_MANAGEMENT])
        # Setup 2.4 GHz AP.
        self.setup_ap(ssid,
                      channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                      wnm_features=wnm_features)

        asserts.assert_true(self.dut.associate(ssid), 'Failed to associate.')
        # Verify that DUT is actually associated (as seen from AP).
        client_mac = self._get_client_mac()
        asserts.assert_true(
            client_mac
            in self.access_point.get_stas(self.access_point.wlan_2g),
            'Client MAC not included in list of associated STAs on the 2.4GHz band'
        )

        # Setup 5 GHz AP with same SSID.
        self.setup_ap(ssid,
                      channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                      wnm_features=wnm_features)

        # Construct a BTM request.
        dest_bssid = self.access_point.get_bssid_from_ssid(
            ssid, self.access_point.wlan_5g)
        dest_bssid_info = BssidInformation(
            security=True, capabilities=BssidInformationCapabilities())
        neighbor_5g_ap = NeighborReportElement(
            dest_bssid,
            dest_bssid_info,
            operating_class=126,
            channel_number=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
            phy_type=PhyType.VHT)
        btm_req = BssTransitionManagementRequest(
            disassociation_imminent=True, candidate_list=[neighbor_5g_ap])

        # Send BTM request from 2.4 GHz AP to DUT
        self.access_point.send_bss_transition_management_req(
            self.access_point.wlan_2g, client_mac, btm_req)

        # Check that DUT has not reassociated.
        REASSOC_DEADLINE = datetime.now(timezone.utc) + timedelta(seconds=2)
        while datetime.now(timezone.utc) < REASSOC_DEADLINE:
            # Fail if DUT has reassociated to 5 GHz AP (as seen from AP).
            if client_mac in self.access_point.get_stas(
                    self.access_point.wlan_5g):
                raise signals.TestFailure(
                    'DUT unexpectedly roamed to target BSS after BTM request')
            else:
                time.sleep(0.25)

        # DUT should have stayed associated to original AP.
        asserts.assert_true(
            client_mac
            in self.access_point.get_stas(self.access_point.wlan_2g),
            'DUT lost association on the 2.4GHz band after BTM request')
