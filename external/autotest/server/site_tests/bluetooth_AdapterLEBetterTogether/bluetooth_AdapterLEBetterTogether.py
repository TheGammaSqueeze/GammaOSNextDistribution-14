# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""A Batch of Bluetooth LE tests for Better Together"""

from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests import \
    BluetoothAdapterQuickTests
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_better_together \
    import BluetoothAdapterBetterTogether

class bluetooth_AdapterLEBetterTogether(BluetoothAdapterBetterTogether):
    """A Batch of Bluetooth LE tests for Better Together. This test is written
       as a batch of tests in order to reduce test time, since auto-test ramp up
       time is costly. The batch is using BluetoothAdapterQuickTests wrapper
       methods to start and end a test and a batch of tests.

       This class can be called to run the entire test batch or to run a
       specific test only
    """

    batch_wrapper = BluetoothAdapterQuickTests.quick_test_batch_decorator
    test_wrapper = BluetoothAdapterQuickTests.quick_test_test_decorator

    @test_wrapper('Smart Unlock', devices={'BLE_PHONE':1})
    def smart_unlock_test(self):
        """Run the smart unlock test"""

        device = self.devices['BLE_PHONE'][0]
        device.RemoveDevice(self.bluetooth_facade.address)
        self.test_smart_unlock(address=device.address)


    @batch_wrapper('Better Together')
    def better_together_batch_run(self, num_iterations=1, test_name=None):
        """Run the Bluetooth LE for Better Together test batch or a specific
           given test. The wrapper of this method is implemented in
           batch_decorator. Using the decorator a test batch method can
           implement the only its core tests invocations and let the
           decorator handle the wrapper, which is taking care for whether to
           run a specific test or the batch as a whole, and running the batch
           in iterations

           @param num_iterations: how many iterations to run
           @param test_name: specific test to run otherwise None to run the
                             whole batch
        """
        self.smart_unlock_test()


    def run_once(self,
                 host,
                 num_iterations=1,
                 args_dict=None,
                 test_name=None,
                 flag='Quick Health'):
        """Run the batch of Bluetooth LE tests for Better Together

        @param host: the DUT, usually a chromebook
        @param num_iterations: the number of rounds to execute the test
        @test_name: the test to run, or None for all tests
        """

        # Initialize and run the test batch or the requested specific test
        self.quick_test_init(host,
                             use_btpeer=True,
                             flag=flag,
                             args_dict=args_dict)
        self.better_together_batch_run(num_iterations, test_name)
        self.quick_test_cleanup()
