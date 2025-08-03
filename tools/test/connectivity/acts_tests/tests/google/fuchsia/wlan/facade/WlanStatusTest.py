#!/usr/bin/env python3
#
#   Copyright 2020 - The Android secure Source Project
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
Test to verify that a DUT's client interface's status can be queried.
"""

from acts import signals
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest


class WlanStatusTest(WifiBaseTest):
    """WLAN status test class.

    Test Bed Requirements:
    * One or more Fuchsia devices with WLAN client capabilities.
    """

    def setup_class(self):
        super().setup_class()
        for fd in self.fuchsia_devices:
            fd.configure_wlan(association_mechanism='policy',
                              preserve_saved_networks=True)

    def on_fail(self, test_name, begin_time):
        for fd in self.fuchsia_devices:
            super().on_device_fail(fd, test_name, begin_time)
            fd.configure_wlan(association_mechanism='policy',
                              preserve_saved_networks=True)

    def test_wlan_stopped_client_status(self):
        """Queries WLAN status on DUTs with no WLAN ifaces.

        Tests that DUTs without WLAN interfaces have empty results and return
        an error when queried for status.
        """
        for fd in self.fuchsia_devices:
            fd.deconfigure_wlan()

            status = fd.sl4f.wlan_lib.wlanStatus()
            self.log.debug(status)
            if not status["error"] or status["result"]:
                raise signals.TestFailure(
                    "DUT's WLAN client status should be empty")

        raise signals.TestPass("Success")

    def test_wlan_started_client_status(self):
        """Queries WLAN status on DUTs with WLAN ifaces.

        Tests that, once WLAN client interfaces have been created, each one
        returns a result and that none of them return errors when queried for
        status.
        """
        for fd in self.fuchsia_devices:
            fd.configure_wlan(association_mechanism='policy',
                              preserve_saved_networks=True)

            status = fd.sl4f.wlan_lib.wlanStatus()
            self.log.debug(status)
            if status["error"] or not status["result"]:
                raise signals.TestFailure(
                    "DUT's WLAN client status should be populated")

        raise signals.TestPass("Success")
