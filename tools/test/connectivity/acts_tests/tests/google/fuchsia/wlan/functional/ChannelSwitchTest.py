#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
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
"""
Tests STA handling of channel switch announcements.
"""

import random
import time

from acts import asserts
from acts.controllers.access_point import setup_ap
from acts.controllers.ap_lib import hostapd_constants
from acts.utils import rand_ascii_str
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device
from typing import Sequence


class ChannelSwitchTest(WifiBaseTest):
    # Time to wait between issuing channel switches
    WAIT_BETWEEN_CHANNEL_SWITCHES_S = 15

    # For operating class 115 tests.
    GLOBAL_OPERATING_CLASS_115_CHANNELS = [36, 40, 44, 48]
    # A channel outside the operating class.
    NON_GLOBAL_OPERATING_CLASS_115_CHANNEL = 52

    # For operating class 124 tests.
    GLOBAL_OPERATING_CLASS_124_CHANNELS = [149, 153, 157, 161]
    # A channel outside the operating class.
    NON_GLOBAL_OPERATING_CLASS_124_CHANNEL = 52

    def setup_class(self) -> None:
        super().setup_class()
        self.ssid = rand_ascii_str(10)
        if 'dut' in self.user_params:
            if self.user_params['dut'] == 'fuchsia_devices':
                self.dut = create_wlan_device(self.fuchsia_devices[0])
            elif self.user_params['dut'] == 'android_devices':
                self.dut = create_wlan_device(self.android_devices[0])
            else:
                raise ValueError('Invalid DUT specified in config. (%s)' %
                                 self.user_params['dut'])
        else:
            # Default is an android device, just like the other tests
            self.dut = create_wlan_device(self.android_devices[0])
        self.access_point = self.access_points[0]
        self._stop_all_soft_aps()
        self.in_use_interface = None

    def teardown_test(self) -> None:
        self.dut.disconnect()
        self.dut.reset_wifi()
        self.download_ap_logs()
        self.access_point.stop_all_aps()

    # TODO(fxbug.dev/85738): Change band type to an enum.
    def channel_switch(self,
                       band: str,
                       starting_channel: int,
                       channel_switches: Sequence[int],
                       test_with_soft_ap: bool = False) -> None:
        """Setup and run a channel switch test with the given parameters.

        Creates an AP, associates to it, and then issues channel switches
        through the provided channels. After each channel switch, the test
        checks that the DUT is connected for a period of time before considering
        the channel switch successful. If directed to start a SoftAP, the test
        will also check that the SoftAP is on the expected channel after each
        channel switch.

        Args:
            band: band that AP will use, must be a valid band (e.g.
                hostapd_constants.BAND_2G)
            starting_channel: channel number that AP will use at startup
            channel_switches: ordered list of channels that the test will
                attempt to switch to
            test_with_soft_ap: whether to start a SoftAP before beginning the
                channel switches (default is False); note that if a SoftAP is
                started, the test will also check that the SoftAP handles
                channel switches correctly
        """
        asserts.assert_true(
            band in [hostapd_constants.BAND_2G, hostapd_constants.BAND_5G],
            'Failed to setup AP, invalid band {}'.format(band))

        self.current_channel_num = starting_channel
        if band == hostapd_constants.BAND_5G:
            self.in_use_interface = self.access_point.wlan_5g
        elif band == hostapd_constants.BAND_2G:
            self.in_use_interface = self.access_point.wlan_2g
        asserts.assert_true(
            self._channels_valid_for_band([self.current_channel_num], band),
            'starting channel {} not a valid channel for band {}'.format(
                self.current_channel_num, band))

        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind',
                 channel=self.current_channel_num,
                 ssid=self.ssid)
        if test_with_soft_ap:
            self._start_soft_ap()
        self.log.info('sending associate command for ssid %s', self.ssid)
        self.dut.associate(target_ssid=self.ssid)
        asserts.assert_true(self.dut.is_connected(), 'Failed to connect.')

        asserts.assert_true(channel_switches,
                            'Cannot run test, no channels to switch to')
        asserts.assert_true(
            self._channels_valid_for_band(channel_switches, band),
            'channel_switches {} includes invalid channels for band {}'.format(
                channel_switches, band))

        for channel_num in channel_switches:
            if channel_num == self.current_channel_num:
                continue
            self.log.info('channel switch: {} -> {}'.format(
                self.current_channel_num, channel_num))
            self.access_point.channel_switch(self.in_use_interface,
                                             channel_num)
            channel_num_after_switch = self.access_point.get_current_channel(
                self.in_use_interface)
            asserts.assert_equal(channel_num_after_switch, channel_num,
                                 'AP failed to channel switch')
            self.current_channel_num = channel_num

            # Check periodically to see if DUT stays connected. Sometimes
            # CSA-induced disconnects occur seconds after last channel switch.
            for _ in range(self.WAIT_BETWEEN_CHANNEL_SWITCHES_S):
                asserts.assert_true(
                    self.dut.is_connected(),
                    'Failed to stay connected after channel switch.')
                client_channel = self._client_channel()
                asserts.assert_equal(
                    client_channel, channel_num,
                    'Client interface on wrong channel ({})'.format(
                        client_channel))
                if test_with_soft_ap:
                    soft_ap_channel = self._soft_ap_channel()
                    asserts.assert_equal(
                        soft_ap_channel, channel_num,
                        'SoftAP interface on wrong channel ({})'.format(
                            soft_ap_channel))
                time.sleep(1)

    def test_channel_switch_2g(self) -> None:
        """Channel switch through all (US only) channels in the 2 GHz band."""
        self.channel_switch(
            band=hostapd_constants.BAND_2G,
            starting_channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
            channel_switches=hostapd_constants.US_CHANNELS_2G)

    def test_channel_switch_2g_with_soft_ap(self) -> None:
        """Channel switch through (US only) 2 Ghz channels with SoftAP up."""
        self.channel_switch(
            band=hostapd_constants.BAND_2G,
            starting_channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
            channel_switches=hostapd_constants.US_CHANNELS_2G,
            test_with_soft_ap=True)

    def test_channel_switch_2g_shuffled_with_soft_ap(self) -> None:
        """Switch through shuffled (US only) 2 Ghz channels with SoftAP up."""
        channels = hostapd_constants.US_CHANNELS_2G
        random.shuffle(channels)
        self.log.info('Shuffled channel switch sequence: {}'.format(channels))
        self.channel_switch(
            band=hostapd_constants.BAND_2G,
            starting_channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
            channel_switches=channels,
            test_with_soft_ap=True)

    # TODO(fxbug.dev/84777): This test fails.
    def test_channel_switch_5g(self) -> None:
        """Channel switch through all (US only) channels in the 5 GHz band."""
        self.channel_switch(
            band=hostapd_constants.BAND_5G,
            starting_channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
            channel_switches=hostapd_constants.US_CHANNELS_5G)

    # TODO(fxbug.dev/84777): This test fails.
    def test_channel_switch_5g_with_soft_ap(self) -> None:
        """Channel switch through (US only) 5 GHz channels with SoftAP up."""
        self.channel_switch(
            band=hostapd_constants.BAND_5G,
            starting_channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
            channel_switches=hostapd_constants.US_CHANNELS_5G,
            test_with_soft_ap=True)

    def test_channel_switch_5g_shuffled_with_soft_ap(self) -> None:
        """Switch through shuffled (US only) 5 Ghz channels with SoftAP up."""
        channels = hostapd_constants.US_CHANNELS_5G
        random.shuffle(channels)
        self.log.info('Shuffled channel switch sequence: {}'.format(channels))
        self.channel_switch(
            band=hostapd_constants.BAND_5G,
            starting_channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
            channel_switches=channels,
            test_with_soft_ap=True)

    # TODO(fxbug.dev/84777): This test fails.
    def test_channel_switch_regression_global_operating_class_115(
            self) -> None:
        """Channel switch into, through, and out of global op. class 115 channels.

        Global operating class 115 is described in IEEE 802.11-2016 Table E-4.
        Regression test for fxbug.dev/84777.
        """
        channels = self.GLOBAL_OPERATING_CLASS_115_CHANNELS + [
            self.NON_GLOBAL_OPERATING_CLASS_115_CHANNEL
        ]
        self.channel_switch(
            band=hostapd_constants.BAND_5G,
            starting_channel=self.NON_GLOBAL_OPERATING_CLASS_115_CHANNEL,
            channel_switches=channels)

    # TODO(fxbug.dev/84777): This test fails.
    def test_channel_switch_regression_global_operating_class_115_with_soft_ap(
            self) -> None:
        """Test global operating class 124 channel switches, with SoftAP.

        Regression test for fxbug.dev/84777.
        """
        channels = self.GLOBAL_OPERATING_CLASS_115_CHANNELS + [
            self.NON_GLOBAL_OPERATING_CLASS_115_CHANNEL
        ]
        self.channel_switch(
            band=hostapd_constants.BAND_5G,
            starting_channel=self.NON_GLOBAL_OPERATING_CLASS_115_CHANNEL,
            channel_switches=channels,
            test_with_soft_ap=True)

    # TODO(fxbug.dev/84777): This test fails.
    def test_channel_switch_regression_global_operating_class_124(
            self) -> None:
        """Switch into, through, and out of global op. class 124 channels.

        Global operating class 124 is described in IEEE 802.11-2016 Table E-4.
        Regression test for fxbug.dev/64279.
        """
        channels = self.GLOBAL_OPERATING_CLASS_124_CHANNELS + [
            self.NON_GLOBAL_OPERATING_CLASS_124_CHANNEL
        ]
        self.channel_switch(
            band=hostapd_constants.BAND_5G,
            starting_channel=self.NON_GLOBAL_OPERATING_CLASS_124_CHANNEL,
            channel_switches=channels)

    # TODO(fxbug.dev/84777): This test fails.
    def test_channel_switch_regression_global_operating_class_124_with_soft_ap(
            self) -> None:
        """Test global operating class 124 channel switches, with SoftAP.

        Regression test for fxbug.dev/64279.
        """
        channels = self.GLOBAL_OPERATING_CLASS_124_CHANNELS + [
            self.NON_GLOBAL_OPERATING_CLASS_124_CHANNEL
        ]
        self.channel_switch(
            band=hostapd_constants.BAND_5G,
            starting_channel=self.NON_GLOBAL_OPERATING_CLASS_124_CHANNEL,
            channel_switches=channels,
            test_with_soft_ap=True)

    def _channels_valid_for_band(self, channels: Sequence[int],
                                 band: str) -> bool:
        """Determine if the channels are valid for the band (US only).

        Args:
            channels: channel numbers
            band: a valid band (e.g. hostapd_constants.BAND_2G)
        """
        if band == hostapd_constants.BAND_2G:
            band_channels = frozenset(hostapd_constants.US_CHANNELS_2G)
        elif band == hostapd_constants.BAND_5G:
            band_channels = frozenset(hostapd_constants.US_CHANNELS_5G)
        else:
            asserts.fail('Invalid band {}'.format(band))
        channels_set = frozenset(channels)
        if channels_set <= band_channels:
            return True
        return False

    def _start_soft_ap(self) -> None:
        """Start a SoftAP on the DUT.

        Raises:
            EnvironmentError: if the SoftAP does not start
        """
        ssid = rand_ascii_str(10)
        security_type = 'none'
        password = ''
        connectivity_mode = 'local_only'
        operating_band = 'any'

        self.log.info('Starting SoftAP on DUT')

        response = self.dut.device.sl4f.wlan_ap_policy_lib.wlanStartAccessPoint(
            ssid, security_type, password, connectivity_mode, operating_band)
        if response.get('error'):
            raise EnvironmentError('SL4F: Failed to setup SoftAP. Err: %s' %
                                   response['error'])
        self.log.info('SoftAp network (%s) is up.' % ssid)

    def _stop_all_soft_aps(self) -> None:
        """Stops all SoftAPs on Fuchsia Device.

        Raises:
            EnvironmentError: if SoftAP stop call fails
        """
        response = self.dut.device.sl4f.wlan_ap_policy_lib.wlanStopAllAccessPoint(
        )
        if response.get('error'):
            raise EnvironmentError(
                'SL4F: Failed to stop all SoftAPs. Err: %s' %
                response['error'])

    def _client_channel(self) -> int:
        """Determine the channel of the DUT client interface.

        If the interface is not connected, the method will assert a test
        failure.

        Returns: channel number

        Raises:
            EnvironmentError: if client interface channel cannot be
                determined
        """
        status = self.dut.status()
        if status['error']:
            raise EnvironmentError('Could not determine client channel')

        result = status['result']
        if isinstance(result, dict):
            if result.get('Connected'):
                return result['Connected']['channel']['primary']
            asserts.fail('Client interface not connected')
        raise EnvironmentError('Could not determine client channel')

    def _soft_ap_channel(self) -> int:
        """Determine the channel of the DUT SoftAP interface.

        If the interface is not connected, the method will assert a test
        failure.

        Returns: channel number

        Raises:
            EnvironmentError: if SoftAP interface channel cannot be determined.
        """
        iface_ids = self.dut.get_wlan_interface_id_list()
        for iface_id in iface_ids:
            query = self.dut.device.sl4f.wlan_lib.wlanQueryInterface(iface_id)
            if query['error']:
                continue
            query_result = query['result']
            if type(query_result) is dict and query_result.get('role') == 'Ap':
                status = self.dut.device.sl4f.wlan_lib.wlanStatus(iface_id)
                if status['error']:
                    continue
                status_result = status['result']
                if isinstance(status_result, dict):
                    if status_result.get('Connected'):
                        return status_result['Connected']['channel']['primary']
                    asserts.fail('SoftAP interface not connected')
        raise EnvironmentError('Could not determine SoftAP channel')
