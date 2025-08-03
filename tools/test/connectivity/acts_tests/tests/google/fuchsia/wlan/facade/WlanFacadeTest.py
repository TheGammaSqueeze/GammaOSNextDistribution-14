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
"""
Script for verifying that we can invoke methods of the WlanFacade.

"""
import array

from acts import asserts, signals
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device


class WlanFacadeTest(WifiBaseTest):

    def setup_class(self):
        super().setup_class()
        if len(self.fuchsia_devices) < 1:
            raise signals.TestAbortClass(
                "Sorry, please try verifying FuchsiaDevice is in your "
                "config file and try again.")
        self.dut = create_wlan_device(self.fuchsia_devices[0])

    def test_get_phy_id_list(self):
        result = self.dut.device.sl4f.wlan_lib.wlanPhyIdList()
        error = result['error']
        asserts.assert_true(error is None, error)

        self.log.info('Got Phy IDs %s' % result['result'])
        return True

    def test_get_country(self):
        wlan_lib = self.dut.device.sl4f.wlan_lib

        result = wlan_lib.wlanPhyIdList()
        error = result['error']
        asserts.assert_true(error is None, error)
        phy_id = result['result'][0]

        result = wlan_lib.wlanGetCountry(phy_id)
        error = result['error']
        asserts.assert_true(error is None, error)

        country_bytes = result['result']
        country_string = str(array.array('b', country_bytes),
                             encoding='us-ascii')
        self.log.info('Got country %s (%s)', country_string, country_bytes)
        return True

    def test_get_dev_path(self):
        wlan_lib = self.dut.device.sl4f.wlan_lib

        result = wlan_lib.wlanPhyIdList()
        error = result['error']
        asserts.assert_true(error is None, error)
        phy_id = result['result'][0]

        result = wlan_lib.wlanGetDevPath(phy_id)
        error = result['error']
        asserts.assert_true(error is None, error)

        dev_path = result['result']
        self.log.info('Got device path: %s', dev_path)
        return True
