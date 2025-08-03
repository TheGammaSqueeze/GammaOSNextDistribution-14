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

from acts import signals
import time
from acts.base_test import BaseTestClass
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device


class ToggleWlanInterfaceStressTest(BaseTestClass):

    def setup_class(self):
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

    def test_iface_toggle_and_ping(self):
        """Test that we don't error out when toggling WLAN interfaces.

        Steps:
        1. Find a WLAN interface
        2. Destroy it
        3. Create a new WLAN interface
        4. Ping after association
        5. Repeat 1-4 1,000 times

        Expected Result:
        Verify there are no errors in destroying the wlan interface.

        Returns:
          signals.TestPass if no errors
          signals.TestFailure if there are any errors during the test.

        TAGS: WLAN, Stability
        Priority: 1
        """

        # Test assumes you've already connected to some AP.

        for i in range(1000):
            wlan_interfaces = self.dut.get_wlan_interface_id_list()
            print(wlan_interfaces)
            if len(wlan_interfaces) < 1:
                raise signals.TestFailure(
                    "Not enough wlan interfaces for test")
            if not self.dut.destroy_wlan_interface(wlan_interfaces[0]):
                raise signals.TestFailure("Failed to destroy WLAN interface")
            # Really make sure it is dead
            self.fuchsia_devices[0].ssh.run(
                f"wlan iface del {wlan_interfaces[0]}")
            # Grace period
            time.sleep(2)
            self.fuchsia_devices[0].ssh.run(
                'wlan iface new --phy 0 --role Client')
            end_time = time.time() + 300
            while time.time() < end_time:
                time.sleep(1)
                if self.dut.is_connected():
                    try:
                        ping_result = self.dut.ping("8.8.8.8", 10, 1000, 1000,
                                                    25)
                        print(ping_result)
                    except Exception as err:
                        # TODO: Once we gain more stability, fail test when pinging fails
                        print("some err {}".format(err))
                    time.sleep(2)  #give time for some traffic
                    break
            if not self.dut.is_connected():
                raise signals.TestFailure("Failed at iteration {}".format(i +
                                                                          1))
            self.log.info("Iteration {} successful".format(i + 1))
        raise signals.TestPass("Success")
