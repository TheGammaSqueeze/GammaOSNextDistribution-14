# Lint as: python2, python3
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Server side bluetooth tests about sending bluetooth HID reports."""

from __future__ import absolute_import

import logging
import time

import common
from autotest_lib.server.cros.bluetooth import bluetooth_adapter_tests


class BluetoothAdapterHIDReportTests(
        bluetooth_adapter_tests.BluetoothAdapterTests):
    """Server side bluetooth tests about sending bluetooth HID reports.

    This test tries to send HID reports to a DUT and verifies if the DUT
    could receive the reports correctly. For the time being, only bluetooth
    mouse events are tested. Bluetooth keyboard events will be supported
    later.
    """

    HID_TEST_SLEEP_SECS = 5

    def run_mouse_tests(self, device):
        """Run all bluetooth mouse reports tests.

        @param device: the bluetooth HID device.

        """
        self.test_mouse_left_click(device)
        self.test_mouse_right_click(device)
        self.test_mouse_move_in_x(device, 80)
        self.test_mouse_move_in_y(device, -50)
        self.test_mouse_move_in_xy(device, -60, 100)
        self.test_mouse_scroll_down(device, 70)
        self.test_mouse_scroll_up(device, 40)
        self.test_mouse_click_and_drag(device, 90, 30)


    def run_keyboard_tests(self, device):
        """Run all bluetooth keyboard reports tests.

        @param device: the bluetooth HID device.

        """

        self.test_keyboard_input_from_trace(device, "simple_text")


    def run_battery_reporting_tests(self, device):
        """Run battery reporting tests.

        @param device: the Bluetooth device.

        """

        self.test_battery_reporting(device)

    def run_hid_reports_test(self,
                             device,
                             check_connected_method=lambda device: True,
                             suspend_resume=False,
                             reboot=False,
                             restart=False):
        """Running Bluetooth HID reports tests."""
        logging.info("run hid reports test")
        # Reset the adapter and set it pairable.
        if not self.test_reset_on_adapter():
            return
        if not self.test_pairable():
            return

        def run_hid_test():
            """Checks if the device is connected and can be used."""
            time.sleep(self.HID_TEST_SLEEP_SECS)
            if not self.test_device_name(device.address, device.name):
                return False

            time.sleep(self.HID_TEST_SLEEP_SECS)
            if not check_connected_method(device):
                return False
            return True

        dev_paired = False
        dev_connected = False
        try:
            # Let the adapter pair, and connect to the target device.
            self.test_discover_device(device.address)
            dev_paired = self.test_pairing(device.address,
                                           device.pin,
                                           trusted=True)
            if not dev_paired:
                return
            dev_connected = self.test_connection_by_adapter(device.address)
            if not dev_connected:
                return

            # Run hid test to make sure profile is connected
            if not run_hid_test():
                return

            if suspend_resume:
                self.suspend_resume()

                time.sleep(self.HID_TEST_SLEEP_SECS)
                if not self.test_device_is_paired(device.address):
                    return

                # Check if peripheral is connected after suspend resume, reconnect
                # and try again if it isn't.
                if not self.ignore_failure(check_connected_method, device):
                    logging.info("device not connected after suspend_resume")
                    self.test_connection_by_device(device)
                run_hid_test()

            if reboot:
                # If we expect the DUT to automatically reconnect to the peer on
                # boot, we reset the peer into a connectable state
                if self.platform_will_reconnect_on_boot():
                    logging.info(
                            "Restarting peer to accept DUT connection on boot")
                    device_type = self.get_peer_device_type(device)
                    self.reset_emulated_device(device, device_type)

                self.reboot()

                time.sleep(self.HID_TEST_SLEEP_SECS)
                # TODO(b/173146480) - Power on the adapter for now until this bug
                # is resolved.
                if not self.bluetooth_facade.is_powered_on():
                    self.test_power_on_adapter()

                if not self.test_device_is_paired(device.address):
                    return

                time.sleep(self.HID_TEST_SLEEP_SECS)
                if not self.platform_will_reconnect_on_boot():
                    self.test_connection_by_device(device)

                else:
                    self.test_device_is_connected(device.address)
                run_hid_test()

            if restart:
                self.test_stop_bluetoothd()
                self.test_start_bluetoothd()

                if not self.ignore_failure(self.test_device_is_connected,
                                           device.address):
                    self.test_connection_by_device(device)
                run_hid_test()

        finally:
            # Cleans up the test
            if dev_connected:
                self.test_disconnection_by_adapter(device.address)
            if dev_paired:
                self.test_remove_pairing(device.address)
