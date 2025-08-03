# Lint as: python2, python3
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""
Server side bluetooth tests on adapter pairing and connecting to a bluetooth
HID device.
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging
import time

import common
from autotest_lib.server.cros.bluetooth import bluetooth_adapter_tests
from six.moves import range


class BluetoothAdapterPairingTests(
        bluetooth_adapter_tests.BluetoothAdapterTests):
    """Server side bluetooth adapter pairing and connecting to bluetooth device

    This test tries to verify that the adapter of the DUT could
    pair and connect to a bluetooth HID device correctly.

    In particular, the following subtests are performed. Look at the
    docstrings of the subtests for more details.
    -

    Refer to BluetoothAdapterTests for all subtests performed in this test.

    """

    # TODO(josephsih): Reduce the sleep intervals to speed up the tests.
    PAIR_TEST_SLEEP_SECS = 5

    def pairing_test(self, device, check_connected_method=lambda device: True,
                     pairing_twice=False, suspend_resume=False, reboot=False):
        """Running Bluetooth adapter tests about pairing to a device."""

        # Reset the adapter to forget previously paired devices if any.
        if not self.test_reset_on_adapter():
            return

        # The adapter must be set to the pairable state.
        if not self.test_pairable():
            return

        # Test if the adapter could discover the target device.
        time.sleep(self.PAIR_TEST_SLEEP_SECS)
        if not self.test_discover_device(device.address):
            return

        # Test if the discovered device class of service is correct.
        if not self.test_device_class_of_service(device.address,
                                                 device.class_of_service):
            return

        # Test if the discovered device class of device is correct.
        if not self.test_device_class_of_device(device.address,
                                                device.class_of_device):
            return

        # Verify that the adapter could pair with the device.
        # Also set the device trusted when pairing is done.
        # Device will be connected at the end of pairing.
        if not self.test_pairing(device.address, device.pin, trusted=True):
            return

        # Test if the discovered device name is correct.
        # Sometimes, it takes quite a long time after discovering
        # the device (more than 60 seconds) to resolve the device name.
        # Hence, it is safer to test the device name after pairing and
        # connection is done.
        if not self.test_device_name(device.address, device.name):
            return

        # Run hid test to make sure profile is connected
        if not check_connected_method(device):
            return

        # Test if the device is still connected after suspend/resume.
        if suspend_resume:
            self.suspend_resume()

            time.sleep(self.PAIR_TEST_SLEEP_SECS)
            if not self.test_device_is_paired(device.address):
                return


            # check if peripheral is connected after suspend resume
            if not self.ignore_failure(check_connected_method, device):
                logging.info("device not connected after suspend_resume")
                self.test_connection_by_device(device)
                time.sleep(self.PAIR_TEST_SLEEP_SECS)
                if not check_connected_method(device):
                    return
            else:
                logging.info("device remains connected after suspend_resume")

            time.sleep(self.PAIR_TEST_SLEEP_SECS)
            if not self.test_device_name(device.address, device.name):
                return

        # Test if the device is still connected after reboot.
        # if reboot:
        #     self.host.reboot()

        #     time.sleep(self.PAIR_TEST_SLEEP_SECS)
        #     self.test_device_is_paired(device.address)

        #     # After a reboot, we need to wake the peripheral
        #     # as it is not connected.
        #     time.sleep(self.PAIR_TEST_SLEEP_SECS)
        #     self.test_connection_by_adapter(device.address)

        #     time.sleep(self.PAIR_TEST_SLEEP_SECS)
        #     self.test_device_is_connected(device.address)

        #     time.sleep(self.PAIR_TEST_SLEEP_SECS)
        #     self.test_device_name(device.address, device.name)

        # Verify that the adapter could disconnect the device.
        if not self.test_disconnection_by_adapter(device.address):
            return

        time.sleep(self.PAIR_TEST_SLEEP_SECS)

        def test_connection():
            """Tests connection inited by either the device or the adapter"""
            if device.can_init_connection:
                # Verify that the device could initiate the connection.
                if not self.test_connection_by_device(device):
                    return False

                # With raspberry pi peer, it takes a moment before the device is
                # registered as an input device. Without delay, the input recorder
                # doesn't find the device
                time.sleep(1)
                return check_connected_method(device)
            # Adapter inited connection.
            # Reconnect so that we can test disconnection from the kit.
            return self.test_connection_by_adapter(device.address)

        if not test_connection():
            return

        def test_disconnection():
            """Tests disconnection inited by either the device or the adapter"""
            # TODO(alent): Needs a new capability, but this is a good proxy
            if device.can_init_connection:
                # Verify that the device could initiate the disconnection.
                return self.test_disconnection_by_device(device)
            # Adapter inited connection.
            return self.test_disconnection_by_adapter(device.address)

        if not test_disconnection():
            return

        # Verify that the adapter could remove the paired device.
        if not self.test_remove_pairing(device.address):
            return

        # Check if the device could be re-paired after being forgotten.
        if pairing_twice:
            # Test if the adapter could discover the target device again.
            time.sleep(self.PAIR_TEST_SLEEP_SECS)
            if not self.test_discover_device(device.address):
                return

            # Verify that the adapter could pair with the device again.
            # Also set the device trusted when pairing is done.
            time.sleep(self.PAIR_TEST_SLEEP_SECS)
            if not self.test_pairing(device.address, device.pin, trusted=True):
                return

            # Verify that the adapter could remove the paired device again.
            time.sleep(self.PAIR_TEST_SLEEP_SECS)
            self.test_remove_pairing(device.address)


    def connect_disconnect_loop(self, device, loops):
        """Perform a connect disconnect loop test"""

        # First pair and disconnect, to emulate real life scenario
        self.test_discover_device(device.address)
        # self.bluetooth_facade.is_discovering() doesn't work as expected:
        # crbug:905374
        # self.test_stop_discovery()
        time.sleep(self.PAIR_TEST_SLEEP_SECS)
        self.test_pairing(device.address, device.pin, trusted=False)

        # Verify device is now connected
        self.test_device_is_connected(device.address)
        self.test_hid_device_created(device.address)

        # Disconnect the device
        self.test_disconnection_by_adapter(device.address)
        total_duration_by_adapter = 0
        loop_cnt = 0
        for i in range(0, loops):

            # Verify device didn't connect automatically
            time.sleep(2)
            self.test_device_is_not_connected(device.address)

            start_time = time.time()
            self.test_connection_by_adapter(device.address)
            end_time = time.time()
            time_diff = end_time - start_time

            # Verify device is now connected
            self.test_device_is_connected(device.address)
            self.test_hid_device_created(device.address)

            self.test_disconnection_by_adapter(device.address)

            if not bool(self.fails):
                loop_cnt += 1
                total_duration_by_adapter += time_diff
                logging.info('%d: Connection establishment duration %f sec',
                             i, time_diff)
            else:
                break

        if not bool(self.fails):
            logging.info('Average duration (by adapter) %f sec',
                         total_duration_by_adapter/loop_cnt)


    def connect_disconnect_by_device_loop(
            self,
            device,
            loops,
            device_type,
            check_connected_method=lambda device: True):
        """Perform a connect disconnect loop test"""

        # Reset the adapter to forget previously paired devices if any.
        self.test_reset_on_adapter()
        self.test_pairable()
        # First pair and disconnect, to emulate real life scenario
        self.test_discover_device(device.address)
        time.sleep(self.PAIR_TEST_SLEEP_SECS)
        self.test_pairing(device.address, device.pin, trusted=True)

        # Verify device is now connected
        self.test_device_is_connected(device.address)
        self.test_hid_device_created(device.address)

        # Make device not discoverable and disconnect
        self.test_device_set_discoverable(device, False)
        self.test_disconnection_by_device(device)

        total_reconnection_duration = 0
        loop_cnt = 0
        for i in range(0, loops):

            # Verify device didn't connect automatically
            time.sleep(2)
            self.test_device_is_not_connected(device.address)

            start_time = time.time()
            if 'BLE' in device_type:
                self.test_device_set_discoverable(device, True)
                self.test_device_is_connected(device.address,
                                              sleep_interval=0.1)
            else:
                self.test_connection_by_device(device, post_connection_delay=0)

            check_connected_method(device)
            end_time = time.time()
            time_diff = end_time - start_time

            if 'BLE' in device_type:
                self.test_device_set_discoverable(device, False)

            self.test_disconnection_by_device(device)

            if not bool(self.fails):
                loop_cnt += 1
                total_reconnection_duration += time_diff
                logging.info('%d: Connection establishment duration %f sec', i,
                             time_diff)
            else:
                break

        self.test_remove_pairing(device.address)
        if not bool(self.fails):
            average_reconnection_duration = total_reconnection_duration / loop_cnt
            logging.info('Average duration (by device) %f sec',
                         average_reconnection_duration)
            return average_reconnection_duration


    def auto_reconnect_loop(self,
                            device,
                            loops,
                            check_connected_method=lambda device: True,
                            restart_adapter=False):
        """Running a loop to verify the paired peer can auto reconnect"""

        # Let the adapter pair, and connect to the target device first
        self.test_discover_device(device.address)
        self.test_pairing(device.address, device.pin, trusted=True)

        # Verify device is now connected
        self.test_connection_by_adapter(device.address)
        self.test_hid_device_created(device.address)

        total_reconnection_duration = 0
        loop_cnt = 0
        for i in range(loops):
            # Restart either the adapter or the peer
            if restart_adapter:
                self.test_power_off_adapter()
                self.test_power_on_adapter()
                start_time = time.time()
            else:
                # Restart and clear peer HID device
                self.restart_peers()
                start_time = time.time()

            # Verify that the device is reconnected. Wait for the input device
            # to become available before checking the profile connection.
            self.test_device_is_connected(device.address)
            self.test_hid_device_created(device.address)

            check_connected_method(device)
            end_time = time.time()
            time_diff = end_time - start_time

            if not bool(self.fails):
                total_reconnection_duration += time_diff
                loop_cnt += 1
                logging.info('%d: Reconnection duration %f sec', i, time_diff)
            else:
                break

        if not bool(self.fails):
            logging.info('Average Reconnection duration %f sec',
                         total_reconnection_duration/loop_cnt)


    def hid_reconnect_speed(self, device, device_type):
        """Test the HID device reconnect speed

        @param device: the meta data with the peer device
        @param device_type: The device type (used to check if it's LE)
        """

        duration = self.connect_disconnect_by_device_loop(
                device=device,
                loops=3,
                device_type=device_type,
                check_connected_method=self.test_hid_device_created_speed)
        if duration is not None:
            self.test_hid_device_reconnect_time(duration, device_type)
