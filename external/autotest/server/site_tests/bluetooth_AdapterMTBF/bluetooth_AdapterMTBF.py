# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""A Bluetooth adapter MTBF test of some Bluetooth use cases.

   To add a new use case we just need to inherit from the existing test class
   and then call the desired test methods in the batch method below. This allows
   the test case to be used as both part of a MTBF batch and a normal batch.
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import threading
import time

import common
from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_audio_tests import (
        BluetoothAdapterAudioTests)
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_better_together \
        import BluetoothAdapterBetterTogether
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_hidreports_tests \
        import BluetoothAdapterHIDReportTests
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests import (
        BluetoothAdapterQuickTests)
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_tests import (
        TABLET_MODELS)
from autotest_lib.client.cros.bluetooth.bluetooth_audio_test_data import A2DP_LONG
from six.moves import range

# Iterations to run the mouse report test, this equals about 10 mins
MOUSE_TEST_ITERATION = 15
# Iterations to run the keyboard report test, this equals about 10 mins
KEYBOARD_TEST_ITERATION = 60
A2DP_TEST_DURATION_SEC = 600
# Wait for some time before stating a new concurrent thread
SLEEP_BETWEEN_THREADS = 15

class bluetooth_AdapterMTBF(BluetoothAdapterBetterTogether,
                            BluetoothAdapterHIDReportTests,
                            BluetoothAdapterAudioTests):
    """A Batch of Bluetooth adapter tests for MTBF. This test is written
       as a batch of tests in order to reduce test time, since auto-test ramp up
       time is costly. The batch is using BluetoothAdapterQuickTests wrapper
       methods to start and end a test and a batch of tests.

       This class can be called to run the entire test batch or to run a
       specific test only
    """

    MTBF_TIMEOUT_MINS = 300
    batch_wrapper = BluetoothAdapterQuickTests.quick_test_batch_decorator
    mtbf_wrapper = BluetoothAdapterQuickTests.quick_test_mtbf_decorator
    test_wrapper = BluetoothAdapterQuickTests.quick_test_test_decorator

    @test_wrapper('MTBF Typical Use Cases',
                  devices={'BLE_MOUSE': 1,
                           'BLE_PHONE': 1,
                           'BLUETOOTH_AUDIO': 1,
                           'KEYBOARD': 1})
    def typical_use_cases_test(self):
        """Do some initialization work then start the typical MTBF test loop"""

        # TODO(b/165606673) - Remove blooglet once the bug is fixed
        self.skip_wake_test = self.host.get_model_from_cros_config() in \
                              TABLET_MODELS + ['blooglet']
        mouse = self.devices['BLE_MOUSE'][0]
        phone = self.devices['BLE_PHONE'][0]
        audio = self.devices['BLUETOOTH_AUDIO'][0]
        keyboard = self.devices['KEYBOARD'][0]

        self.test_device_pairing(mouse)
        self.test_device_pairing(keyboard)

        self.run_typical_use_cases(mouse, phone, audio, keyboard)


    @mtbf_wrapper(timeout_mins=MTBF_TIMEOUT_MINS, test_name='typical_use_cases')
    def run_typical_use_cases(self, mouse, phone, audio, keyboard):
        """Run typical MTBF test scenarios:
           1. Run the better together test
           2. Run the concurrent mouse and A2DP tests for 10 minutes
           3. Suspend/Resume
           4. Run the concurrent mouse amd keyboard tests for 10 minutes
           5. Suspend and wake up the DUT by mouse
           6. Run the concurrent mouse, keyboard and A2DP tests for 10 minutes
        """
        # Run the better together test on the phone
        self.test_better_together(phone)

        # Restore the discovery filter since better together test changed it
        self.test_set_discovery_filter({'Transport':'auto'})

        self.test_mouse_and_audio(mouse, audio)
        self.test_suspend_resume(mouse, keyboard)
        self.test_mouse_and_keyboard(mouse, keyboard)
        # Mouse wakeup test
        self.test_suspend_and_mouse_wakeup(mouse, keyboard)
        self.test_hid_and_audio(mouse, keyboard, audio)


    def test_mouse_and_audio(self, mouse, audio):
        """Run the mouse and audio tests concurrently for 10 mins"""
        audio_thread = threading.Thread(
            target=self.test_audio, args=(audio, A2DP_TEST_DURATION_SEC))
        mouse_thread = threading.Thread(
            target=self.test_mouse, args=(mouse, MOUSE_TEST_ITERATION))

        audio_thread.start()
        time.sleep(SLEEP_BETWEEN_THREADS)
        mouse_thread.start()
        time.sleep(SLEEP_BETWEEN_THREADS)
        audio_thread.join()
        mouse_thread.join()
        if self.fails:
            raise error.TestFail(self.fails)


    def test_mouse_and_keyboard(self, mouse, keyboard):
        """Run the mouse and keyboard tests concurrently for 10 mins"""
        mouse_thread = threading.Thread(
            target=self.test_mouse, args=(mouse, MOUSE_TEST_ITERATION))
        keyboard_thread = \
            threading.Thread(target=self.test_keyboard,
                             args=(keyboard, KEYBOARD_TEST_ITERATION))
        time.sleep(SLEEP_BETWEEN_THREADS)
        mouse_thread.start()
        time.sleep(SLEEP_BETWEEN_THREADS)
        keyboard_thread.start()
        mouse_thread.join()
        keyboard_thread.join()
        if self.fails:
            raise error.TestFail(self.fails)


    def test_hid_and_audio(self, mouse, keyboard, audio):
        """Run the audio, mouse and keyboard tests concurrently for 10 mins"""
        audio_thread = threading.Thread(
            target=self.test_audio, args=(audio, A2DP_TEST_DURATION_SEC))
        mouse_thread = threading.Thread(
            target=self.test_mouse, args=(mouse, MOUSE_TEST_ITERATION))
        keyboard_thread = \
            threading.Thread(target=self.test_keyboard,
                             args=(keyboard, KEYBOARD_TEST_ITERATION))
        audio_thread.start()
        time.sleep(SLEEP_BETWEEN_THREADS)
        mouse_thread.start()
        time.sleep(SLEEP_BETWEEN_THREADS)
        keyboard_thread.start()
        audio_thread.join()
        mouse_thread.join()
        keyboard_thread.join()
        if self.fails:
            raise error.TestFail(self.fails)


    def test_mouse(self, mouse, iteration):
        """Run mouse report test for certain iterations"""
        for i in range(iteration):
            self.run_mouse_tests(device=mouse)


    def test_keyboard(self, keyboard, iteration):
        """Run keyboard report test for certain iterations"""
        for i in range(iteration):
            self.run_keyboard_tests(device=keyboard)


    def test_audio(self, device, duration):
        """Test A2DP

           This test plays A2DP audio on the DUT and record on the peer device,
           then verify the legitimacy of the frames recorded.

        """
        self.bluetooth_facade.remove_device_object(device.address)
        device.RemoveDevice(self.bluetooth_facade.address)

        self.initialize_bluetooth_audio(device, A2DP_LONG)
        self.test_device_set_discoverable(device, True)
        self.test_discover_device(device.address)
        self.test_pairing(device.address, device.pin, trusted=True)
        device.SetTrustedByRemoteAddress(self.bluetooth_facade.address)
        self.test_connection_by_adapter(device.address)
        self.test_a2dp_sinewaves(device, A2DP_LONG, duration)
        self.test_disconnection_by_adapter(device.address)
        self.cleanup_bluetooth_audio(device, A2DP_LONG)
        self.test_remove_device_object(device.address)


    def test_device_pairing(self, device):
        """Test device pairing"""

        # Remove the pairing first
        self.bluetooth_facade.remove_device_object(device.address)
        device.RemoveDevice(self.bluetooth_facade.address)

        self.test_device_set_discoverable(device, True)
        self.test_discover_device(device.address)
        time.sleep(self.TEST_SLEEP_SECS)
        self.test_pairing(device.address, device.pin, trusted=True)
        time.sleep(self.TEST_SLEEP_SECS)
        self.test_connection_by_adapter(device.address)


    def test_suspend_resume(self, mouse, keyboard):
        """Test the device can connect after suspending and resuming"""
        boot_id = self.host.get_boot_id()
        suspend = self.suspend_async(suspend_time=15)
        start_time = self.bluetooth_facade.get_device_utc_time()

        self.test_device_set_discoverable(mouse, False)

        self.test_suspend_and_wait_for_sleep(
            suspend, sleep_timeout=15)
        self.test_wait_for_resume(boot_id,
                                  suspend,
                                  resume_timeout=15,
                                  test_start_time=start_time)

        # LE can't reconnect without advertising/discoverable
        self.test_device_set_discoverable(mouse, True)
        self.test_device_is_connected(mouse.address)
        self.test_hid_device_created(mouse.address)

        self.test_connection_by_device(keyboard)
        self.test_hid_device_created(keyboard.address)


    def test_suspend_and_mouse_wakeup(self, mouse, keyboard):
        """Test the device can be waken up by the mouse"""
        if self.skip_wake_test:
            return

        self.run_peer_wakeup_device('MOUSE', mouse, should_pair=False)
        # Make sure we're actually connected
        self.test_device_is_connected(mouse.address)
        self.test_hid_device_created(mouse.address)

        self.test_connection_by_device(keyboard)
        self.test_hid_device_created(keyboard.address)


    @test_wrapper('MTBF Better Together Stress', devices={'BLE_PHONE': 1})
    def better_together_stress_test(self):
        """Run better together stress test"""

        phone = self.devices['BLE_PHONE'][0]
        phone.RemoveDevice(self.bluetooth_facade.address)
        self.run_better_together_stress(address=phone.address)


    def test_better_together(self, phone):
        """Test better together"""
        # Clean up the environment
        self.bluetooth_facade.disconnect_device(phone.address)
        self.bluetooth_facade.remove_device_object(phone.address)
        phone.RemoveDevice(self.bluetooth_facade.address)
        self.test_smart_unlock(address=phone.address)


    @mtbf_wrapper(
        timeout_mins=MTBF_TIMEOUT_MINS, test_name='better_together_stress')
    def run_better_together_stress(self, address):
        """Run better together stress test"""

        self.test_smart_unlock(address)


    @batch_wrapper('Adapter MTBF')
    def mtbf_batch_run(self, num_iterations=1, test_name=None):
        """Run the Bluetooth MTBF test batch or a specific
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
        # TODO: finalize the test cases that need to be run as MTBF
        self.typical_use_cases_test()


    def run_once(self,
                 host,
                 num_iterations=1,
                 test_name=None,
                 args_dict=None):
        """Run the batch of Bluetooth MTBF tests

        @param host: the DUT, usually a chromebook
        @param num_iterations: the number of rounds to execute the test
        @test_name: the test to run, or None for all tests
        """

        # Initialize and run the test batch or the requested specific test
        self.set_fail_fast(args_dict, True)
        self.quick_test_init(host, use_btpeer=True, args_dict=args_dict)

        self.mtbf_batch_run(num_iterations, test_name)

        self.quick_test_cleanup()
