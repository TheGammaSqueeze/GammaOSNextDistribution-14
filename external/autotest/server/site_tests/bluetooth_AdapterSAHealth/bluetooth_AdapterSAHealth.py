# Lint as: python2, python3
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""A Batch of Bluetooth stand alone health tests"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging

from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests import (
        BluetoothAdapterQuickTests)
from autotest_lib.server.cros.bluetooth import bluetooth_dbus_api_tests
from autotest_lib.server.cros.bluetooth import bluetooth_default_state_test
from autotest_lib.server.cros.bluetooth import bluetooth_valid_address_test
from six.moves import range


class bluetooth_AdapterSAHealth(BluetoothAdapterQuickTests,
        bluetooth_default_state_test.bluetooth_Health_DefaultStateTest,
        bluetooth_valid_address_test.bluetooth_Health_ValidAddressTest,
        bluetooth_dbus_api_tests.BluetoothDBusAPITests):

    """A Batch of Bluetooth stand alone health tests. This test is written as
       a batch of tests in order to reduce test time, since auto-test ramp up
       time is costy. The batch is using BluetoothAdapterQuickTests wrapper
       methods to start and end a test and a batch of tests.

       This class can be called to run the entire test batch or to run a
       specific test only
    """

    test_wrapper = BluetoothAdapterQuickTests.quick_test_test_decorator
    batch_wrapper = BluetoothAdapterQuickTests.quick_test_batch_decorator

    @test_wrapper('Stand Alone noop test', supports_floss=True)
    def sa_noop(self):
        """A no-op test to validate Floss"""
        logging.info("sa_noop ran")

    @test_wrapper('Stand Alone basic test')
    def sa_basic_test(self):
        """Set of basic stand alone test"""

        # The bluetoothd must be running in the beginning.
        self.test_bluetoothd_running()

        # It is possible that the adapter is not powered on yet.
        # Power it on anyway and verify that it is actually powered on.
        self.test_power_on_adapter()

        # Verify that the adapter could be powered off and powered on,
        # and that it is in the correct working state.
        self.test_power_off_adapter()
        self.test_power_on_adapter()
        self.test_adapter_work_state()

        # Verify that the bluetoothd could be simply stopped and then
        # be started again, and that it is in the correct working state.
        self.test_stop_bluetoothd()
        self.test_start_bluetoothd()
        self.test_adapter_work_state()

        # Verify that the adapter could be reset off and on which includes
        # removing all cached information.
        self.test_reset_off_adapter()
        self.test_reset_on_adapter()
        self.test_adapter_work_state()

        # Verify that the adapter supports basic profiles.
        self.test_UUIDs()

        # Verify that the adapter could start and stop discovery.
        self.test_start_discovery()
        self.test_stop_discovery()
        self.test_start_discovery()

        # Verify that the adapter could set both discoverable and
        # non-discoverable successfully.
        self.test_discoverable()
        self.test_nondiscoverable()
        self.test_discoverable()

        # Verify that the adapter could set pairable and non-pairable.
        self.test_pairable()
        self.test_nonpairable()
        self.test_pairable()


    # Remove flags=['Quick Health'] when this test is migrated to stable suite.
    @test_wrapper('Stand Alone power reset test', flags=['Quick Health'])
    def sa_power_reset(self):
        """Adapter power reset test

        Repeatedly reset adapter power to expect that bluetoothd is running.
        Note that the test takes about 2 minutes to complete and is suitable
        to run in function verification test suite.
        """
        for _ in range(20):
            self.test_reset_on_adapter()
            self.test_bluetoothd_running()


    # TODO(b/182172118) - Winky has suspend test issues
    @test_wrapper('Adapter suspend resume test', skip_models=['winky'])
    def sa_adapter_suspend_resume_test(self):
        """Test dapter power states is perserved through suspend resume."""
        def adapter_on_SR_test():
            """Test Case: Power on - SR"""
            self.test_power_on_adapter()
            self.test_bluetoothd_running()
            self.suspend_resume()
            self.test_bluetoothd_running()
            self.test_adapter_work_state()
            self.test_power_on_adapter()


        def adapter_off_SR_test():
            """Test Case: Power off - SR"""
            self.test_power_off_adapter()
            self.test_bluetoothd_running()
            self.suspend_resume()
            self.test_power_off_adapter()
            self.test_bluetoothd_running()

        adapter_on_SR_test()
        adapter_off_SR_test()


    @test_wrapper('Adapter present test', supports_floss=True)
    def sa_adapter_present_test(self):
        """Verify that the client has a Bluetooth adapter."""

        # Reset the adapter (if any) to the powered off state.
        self.test_reset_off_adapter()

        # Verify that there is an adapter. This will only return True if both
        # the kernel and bluetooth daemon see the adapter.
        self.test_has_adapter()

    @test_wrapper('Adapter reboot test')
    def sa_adapter_reboot_test(self):
        """Verify that adapter power setting persist over reboot

        Test whether power setting persist after a reboot and whether
        adapter can be turned on after reboot
        """

        def test_case_adapter_on_reboot():
            """Test Case: Power on - reboot"""
            self.test_power_on_adapter()
            self.test_bluetoothd_running()
            self.reboot()
            self.test_bluetoothd_running()
            self.test_adapter_work_state()

        def test_case_adapter_off_reboot():
            """Test Case: Power on - reboot"""
            self.test_power_off_adapter()
            self.test_bluetoothd_running()
            self.reboot()
            self.test_has_adapter()
            self.test_is_powered_off()
            self.test_power_on_adapter()
            self.test_bluetoothd_running()

        NUM_ITERATIONS = 3
        for i in range(NUM_ITERATIONS):
            logging.debug('Starting reboot test loop number #%d', i)
            test_case_adapter_on_reboot()
            test_case_adapter_off_reboot()



    # TODO(b/145302986): Silencing known firmware issue with AC7260 (WP2)
    @test_wrapper('Adapter DiscoverableTimeout test',
                  skip_chipsets=['Intel-AC7260'])
    def sa_adapter_discoverable_timeout_test(self):
        """Verify that DiscoverableTimout Property works."""
        result = self.test_discoverable_timeout(timeout_values=[0, 7, 15])
        logging.info("Result is %s", result)

    @test_wrapper('Adapter PairableTimeout test')
    def sa_adapter_pairable_timeout_test(self):
        """Verify that PairableTimout Property works."""
        result = self.test_pairable_timeout(timeout_values=[0, 7, 15])
        logging.info("Result is %s", result)


    @test_wrapper('Default state test')
    def sa_default_state_test(self):
        """Verify that the Bluetooth adapter has correct state."""
        self.default_state_test()


    @test_wrapper('Valid address test', supports_floss=True)
    def sa_valid_address_test(self):
        """Verify that the client Bluetooth adapter has a valid address."""
        self.valid_address_test()


    @test_wrapper('Valid adapter ID test')
    def sa_valid_id_test(self):
        """Verify that the adapter has a correctly-formatted ID"""
        self.test_check_valid_adapter_id()


    @test_wrapper('Valid adapter alias test')
    def sa_valid_alias_test(self):
        """Verify that the adapter has a correctly-formatted alias"""
        self.test_check_valid_alias()


    @test_wrapper('DBUS API tests')
    def sa_dbus_api_tests(self):
        """ Verify that the Bluetooth DBus API calls work."""
        self.test_dbus_start_discovery_success()
        self.test_dbus_start_discovery_fail_discovery_in_progress()
        self.test_dbus_start_discovery_fail_power_off()

        self.test_dbus_stop_discovery_success()
        self.test_dbus_stop_discovery_fail_discovery_not_in_progress()
        self.test_dbus_stop_discovery_fail_power_off()

        self.test_dbus_get_supported_capabilities_success()
        self.test_dbus_get_supported_capabilities_success_power_off()

    @test_wrapper('EIR Max Alias Size test')
    def sa_eir_max_name_size_test(self):
        """ Verify that the non-default max eir name size is used """
        EIR_80_char_name = ('1234567890123456789012345678901234567890'
                            '1234567890123456789012345678901234567890')

        self.test_set_adapter_alias(EIR_80_char_name)


    @batch_wrapper('Stand Alone Health')
    def sa_health_batch_run(self, num_iterations=1, test_name=None):
        """Run the stand alone health test batch or a specific given test.
           The wrapper of this method is implemented in batch_decorator.
           Using the decorator a test batch method can implement the only its
           core tests invocations and let the decorator handle the wrapper,
           which is taking care for whether to run a specific test or the
           batch as a whole, and running the batch in iterations

           @param num_iterations: how many interations to run
           @param test_name: specifc test to run otherwise None to run the
                             whole batch
        """
        self.sa_noop()
        self.sa_basic_test()
        self.sa_adapter_suspend_resume_test()
        self.sa_adapter_present_test()
        # self.sa_adapter_reboot_test() disabled since the test is not stable
        self.sa_adapter_discoverable_timeout_test()
        self.sa_default_state_test()
        self.sa_valid_address_test()
        self.sa_dbus_api_tests()
        self.sa_power_reset()


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
                             use_btpeer=False,
                             flag=flag,
                             start_browser=False,
                             floss=floss)
        self.sa_health_batch_run(num_iterations, test_name)
        self.quick_test_cleanup()
