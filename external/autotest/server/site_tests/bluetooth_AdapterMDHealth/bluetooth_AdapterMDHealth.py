# Lint as: python2, python3
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""A Batch of Bluetooth Multiple Devices health tests"""

import time
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests import \
     BluetoothAdapterQuickTests
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_hidreports_tests \
     import BluetoothAdapterHIDReportTests

class bluetooth_AdapterMDHealth(BluetoothAdapterQuickTests,
                                BluetoothAdapterHIDReportTests):
    """A Batch of Bluetooth multiple devices health tests. This test is written
       as a batch of tests in order to reduce test time, since auto-test ramp up
       time is costy. The batch is using BluetoothAdapterQuickTests wrapper
       methods to start and end a test and a batch of tests.

       This class can be called to run the entire test batch or to run a
       specific test only
    """

    test_wrapper = BluetoothAdapterQuickTests.quick_test_test_decorator
    batch_wrapper = BluetoothAdapterQuickTests.quick_test_batch_decorator


    def discover_and_pair(self, device):
        """ Discovers and pairs given device. Automatically connects too.

        @param device: meta object for bt peer device
        """
        self.test_discover_device(device.address)
        time.sleep(self.TEST_SLEEP_SECS)
        self.test_pairing(device.address, device.pin, trusted=True)
        time.sleep(self.TEST_SLEEP_SECS)
        self.test_connection_by_adapter(device.address)


    def pair_and_test_connection(self, devtuples):
        """ Tests connection and pairing with multiple devices

        @param devtuples: array of tuples consisting of the following:
            * device: meta object for peer device
            * device_test: Optional; test function to run w/
                           device (eg. mouse click)
        """
        try:
            for device, _ in devtuples:
                self.discover_and_pair(device)

            # Sleep for a few seconds to give HID controllers time to
            # initialize, may prevent some flakiness in tests
            time.sleep(self.TEST_SLEEP_SECS)

            for device, device_test in devtuples:
                if device_test is not None:
                    device_test(device)

        finally:
            for device, _ in devtuples:
                self.test_disconnection_by_adapter(device.address)
                self.test_remove_pairing(device.address)


    @test_wrapper('One classic and one BLE connection',
                  devices={'BLE_MOUSE':1, 'KEYBOARD':1}, supports_floss=True)
    def md_two_connections_test(self):
        """test whether DUT can connect to classic keyboard and ble mouse at the
           same time
        """
        devices = [
            (self.devices['BLE_MOUSE'][0], self.test_mouse_left_click),
            (self.devices['KEYBOARD'][0], self.run_keyboard_tests)
        ]

        self.pair_and_test_connection(devices)


    @test_wrapper('Two BLE connections',
                  devices={
                          'BLE_MOUSE': 1,
                          'BLE_KEYBOARD': 1
                  },
                  supports_floss=True)
    def md_two_ble_hid_connections_test(self):
        """ test whether DUT can connect to ble keyboard and ble mouse at the
            same time
        """
        devices = [
            (self.devices['BLE_MOUSE'][0], self.test_mouse_left_click),
            (self.devices['BLE_KEYBOARD'][0], self.run_keyboard_tests)
        ]

        self.pair_and_test_connection(devices)


    @test_wrapper('Two classic connections',
                  devices={
                          'MOUSE': 1,
                          'KEYBOARD': 1
                  },
                  supports_floss=True)
    def md_two_cl_hid_connections_test(self):
        """ test whether DUT can connect to classic mouse and classic keyboard
            at the same time
        """
        devices = [
            (self.devices['MOUSE'][0], self.test_mouse_left_click),
            (self.devices['KEYBOARD'][0], self.run_keyboard_tests)
        ]

        self.pair_and_test_connection(devices)


    @batch_wrapper('Multiple Devices Health')
    def md_health_batch_run(self, num_iterations=1, test_name=None):
        """Run the multiple devices health test batch or a specific given test.
           The wrapper of this method is implemented in batch_decorator.
           Using the decorator a test batch method can implement the only its
           core tests invocations and let the decorator handle the wrapper,
           which is taking care for whether to run a specific test or the
           batch as a whole, and running the batch in iterations

           @param num_iterations: how many interations to run
           @param test_name: specifc test to run otherwise None to run the
                             whole batch
        """
        self.md_two_connections_test()
        self.md_two_ble_hid_connections_test()
        self.md_two_cl_hid_connections_test()


    def run_once(self,
                 host,
                 num_iterations=1,
                 args_dict=None,
                 test_name=None,
                 flag='Quick Health',
                 floss=False):
        """Run the batch of Bluetooth stand health tests

        @param host: the DUT, usually a chromebook
        @param num_iterations: the number of rounds to execute the test
        """
        # Initialize and run the test batch or the requested specific test
        self.quick_test_init(host,
                             use_btpeer=True,
                             flag=flag,
                             args_dict=args_dict,
                             floss=floss)
        self.md_health_batch_run(num_iterations, test_name)
        self.quick_test_cleanup()
