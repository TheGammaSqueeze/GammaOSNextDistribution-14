#!/usr/bin/env python3
#
# Copyright (C) 2019 The Android Open Source Project
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

from acts import signals

from acts.base_test import BaseTestClass
from acts import asserts


class NetstackIfaceTest(BaseTestClass):
    default_timeout = 10
    active_scan_callback_list = []
    active_adv_callback_list = []
    droid = None

    def setup_class(self):
        super().setup_class()
        if (len(self.fuchsia_devices) < 1):
            self.log.error(
                "NetstackFuchsiaTest Init: Not enough fuchsia devices.")
        self.log.info("Running testbed setup with one fuchsia devices")
        self.dut = self.fuchsia_devices[0]

    def _enable_all_interfaces(self):
        interfaces = self.dut.sl4f.netstack_lib.netstackListInterfaces()
        for item in interfaces.get("result"):
            identifier = item.get('id')
            self.dut.sl4f.netstack_lib.enableInterface(identifier)

    def setup_test(self):
        # Always make sure all interfaces listed are in an up state.
        self._enable_all_interfaces()

    def teardown_test(self):
        # Always make sure all interfaces listed are in an up state.
        self._enable_all_interfaces()

    def test_list_interfaces(self):
        """Test listing all interfaces.

        Steps:
        1. Call ListInterfaces FIDL api.
        2. Verify there is at least one interface returned.

        Expected Result:
        There were no errors in retrieving the list of interfaces.
        There was at least one interface in the list.

        Returns:
          signals.TestPass if no errors
          signals.TestFailure if there are any errors during the test.

        TAGS: Netstack
        Priority: 1
        """
        interfaces = self.dut.sl4f.netstack_lib.netstackListInterfaces()
        if interfaces.get('error') is not None:
            raise signals.TestFailure("Failed with {}".format(
                interfaces.get('error')))
        if len(interfaces.get('result')) < 1:
            raise signals.TestFailure("No interfaces found.")
        self.log.info("Interfaces found: {}".format(interfaces.get('result')))
        raise signals.TestPass("Success")

    def test_toggle_wlan_interface(self):
        """Test toggling the wlan interface if it exists.

        Steps:
        1. Call ListInterfaces FIDL api.
        2. Find the wlan interface.
        3. Disable the interface.
        4. Verify interface attributes in a down state.
        5. Enable the interface.
        6. Verify interface attributes in an up state.

        Expected Result:
        WLAN interface was successfully brought down and up again.

        Returns:
          signals.TestPass if no errors
          signals.TestFailure if there are any errors during the test.
          signals.TestSkip if there are no wlan interfaces.

        TAGS: Netstack
        Priority: 1
        """

        def get_wlan_interfaces():
            result = self.dut.sl4f.netstack_lib.netstackListInterfaces()
            if (error := result.get('error')):
                raise signals.TestFailure(
                    f'unable to list interfaces: {error}')
            return [
                interface for interface in result.get('result')
                if 'wlan' in interface.get('name')
            ]

        def get_ids(interfaces):
            return [get_id(interface) for interface in interfaces]

        wlan_interfaces = get_wlan_interfaces()
        if not wlan_interfaces:
            raise signals.TestSkip('no wlan interface found')
        interface_ids = get_ids(wlan_interfaces)

        # Disable the interfaces.
        for identifier in interface_ids:
            result = self.dut.sl4f.netstack_lib.disableInterface(identifier)
            if (error := result.get('error')):
                raise signals.TestFailure(
                    f'failed to disable wlan interface {identifier}: {error}')

        # Retrieve the interfaces again.
        disabled_wlan_interfaces = get_wlan_interfaces()
        disabled_interface_ids = get_ids(wlan_interfaces)

        if not disabled_interface_ids == interface_ids:
            raise signals.TestFailure(
                f'disabled interface IDs do not match original interface IDs: original={interface_ids} disabled={disabled_interface_ids}'
            )

        # Check the current state of the interfaces.
        for interface in disabled_interfaces:
            if len(interface_info.get('ipv4_addresses')) > 0:
                raise signals.TestFailure(
                    f'no Ipv4 Address should be present: {interface}')

            # TODO (35981): Verify other values when interface down.

        # Re-enable the interfaces.
        for identifier in disabled_interface_ids:
            result = self.dut.sl4f.netstack_lib.enableInterface(identifier)
            if (error := result.get('error')):
                raise signals.TestFailure(
                    f'failed to enable wlan interface {identifier}: {error}')

        # TODO (35981): Verify other values when interface up.
        raise signals.TestPass("Success")
