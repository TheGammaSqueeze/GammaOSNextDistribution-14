# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""A Batch of Bluetooth advertising tests"""

from autotest_lib.server.cros.bluetooth.bluetooth_adapter_tests import (
        SUSPEND_POWER_DOWN_CHIPSETS, SUSPEND_RESET_IF_NO_PEER_CHIPSETS,
        SUSPEND_POWER_DOWN_MODELS)
from autotest_lib.server.cros.bluetooth import advertisements_data
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests import \
     BluetoothAdapterQuickTests
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_leadvertising_tests \
     import bluetooth_AdapterLEAdvertising

class bluetooth_AdapterAdvHealth(BluetoothAdapterQuickTests,
        bluetooth_AdapterLEAdvertising):

    """A Batch of Bluetooth advertising tests. This test is written as
       a batch of tests in order to reduce test time, since auto-test ramp up
       time is costy. The batch is using BluetoothAdapterQuickTests wrapper
       methods to start and end a test and a batch of tests.

       This class can be called to run the entire test batch or to run a
       specific test only
    """

    test_wrapper = BluetoothAdapterQuickTests.quick_test_test_decorator
    batch_wrapper = BluetoothAdapterQuickTests.quick_test_batch_decorator


    # TODO(b/192419579) - RTL8822 and 8852 can't advertise 4 connectable
    #                     advertisements.
    @test_wrapper('Multiple LE advertising test',
                  skip_chipsets=[
                          'Realtek-RTL8822C-USB', 'Realtek-RTL8822C-UART',
                          'Realtek-RTL8852A-USB'
                  ],
                  skip_common_errors=True)
    def adv_multiple_advertising_test(self):
        """Run all test cases for multiple advertisements."""
        self.run_le_advertising_test(
            self.host, advertisements_data.ADVERTISEMENTS,
            'multi_advertising', num_iterations=1)


    @test_wrapper('Single LE advertising test')
    def adv_single_advertising_test(self):
        """Run all test cases for single advertisements."""
        self.run_le_advertising_test(
            self.host, advertisements_data.ADVERTISEMENTS,
            'single_advertising', num_iterations=1)


    # TODO(b/150897528) - Scarlet Dru loses firmware around suspend
    # TODO(b/182172118) - Winky has suspend test issues
    # TODO(b/189813813) - Scarlet Dumo loses firmware around suspend
    @test_wrapper('Suspend resume LE advertising test',
                  skip_models=SUSPEND_POWER_DOWN_MODELS + ['winky'],
                  skip_chipsets=SUSPEND_POWER_DOWN_CHIPSETS +
                  SUSPEND_RESET_IF_NO_PEER_CHIPSETS,
                  skip_common_errors=True)
    def adv_suspend_resume_advertising_test(self):
        """Run all test cases for advertisements involving suspend resume."""
        self.run_le_advertising_test(
            self.host, advertisements_data.ADVERTISEMENTS,
            'suspend_resume', num_iterations=1)


    @test_wrapper('Reboot LE advertising test')
    def adv_reboot_advertising_test(self):
        """Run all test cases for advertisements involving reboot."""
        self.run_le_advertising_test(
            self.host, advertisements_data.ADVERTISEMENTS,
            'reboot', num_iterations=1)


    @test_wrapper('Advertising peer test', devices={'BLE_MOUSE':1})
    def adv_peer_test(self):
        """Verify advertising from a peer"""

        device = self.devices['BLE_MOUSE'][0]

        self.advertising_peer_test(device)

    @test_wrapper('Advertising Nearby test')
    def adv_nearby_test(self):
        """Verify minimal Nearby advertising requirements"""

        self.run_le_advertising_test(self.host,
                                     advertisements_data.ADVERTISEMENTS,
                                     'nearby',
                                     num_iterations=1)

    @test_wrapper('Broadcast advertising test')
    def adv_broadcast_test(self):
        """Verify broadcast advertising capability"""

        self.run_le_advertising_test(self.host,
                                     advertisements_data.ADVERTISEMENTS,
                                     'broadcast',
                                     num_iterations=1)

    # TODO(b/150897528) - Scarlet Dru loses firmware around suspend
    # TODO(b/189813813) - Scarlet Dumo loses firmware around suspend
    @test_wrapper('Advertising suspend peer test',
                  devices={'BLE_MOUSE': 1},
                  skip_models=SUSPEND_POWER_DOWN_MODELS,
                  skip_chipsets=SUSPEND_POWER_DOWN_CHIPSETS +
                  SUSPEND_RESET_IF_NO_PEER_CHIPSETS,
                  skip_common_errors=True)
    def adv_suspend_peer_test(self):
        """Verify advertising around suspend from a peer"""

        device = self.devices['BLE_MOUSE'][0]

        self.advertising_peer_suspend_resume_test(device)

    @batch_wrapper('Advertising Health')
    def adv_health_batch_run(self, num_iterations=1, test_name=None):
        """Run the advertising health test batch or a specific given test.
           The wrapper of this method is implemented in batch_decorator.
           Using the decorator a test batch method can implement the only its
           core tests invocations and let the decorator handle the wrapper,
           which is taking care for whether to run a specific test or the
           batch as a whole, and running the batch in iterations

           @param num_iterations: how many iterations to run
           @param test_name: specific test to run otherwise None to run the
                             whole batch
        """
        self.adv_peer_test()
        self.adv_multiple_advertising_test()
        self.adv_single_advertising_test()
        self.adv_suspend_resume_advertising_test()
        self.adv_reboot_advertising_test()
        self.adv_nearby_test()


    def run_once(self,
                 host,
                 num_iterations=1,
                 args_dict=None,
                 test_name=None,
                 flag='Quick Health',
                 peer_required=True):
        """Run the batch of Bluetooth advertising health tests

        @param host: the DUT, usually a chromebook
        @param num_iterations: the number of rounds to execute the test
        """
        # Initialize and run the test batch or the requested specific test
        self.quick_test_init(host,
                             use_btpeer=peer_required,
                             flag=flag,
                             args_dict=args_dict)
        self.adv_health_batch_run(num_iterations, test_name)
        self.quick_test_cleanup()
