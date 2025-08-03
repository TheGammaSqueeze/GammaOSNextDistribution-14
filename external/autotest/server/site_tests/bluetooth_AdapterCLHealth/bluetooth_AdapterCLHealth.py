# Lint as: python2, python3
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""A Batch of of Bluetooth Classic health tests"""


from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests import \
     BluetoothAdapterQuickTests
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_pairing_tests import \
     BluetoothAdapterPairingTests
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_hidreports_tests \
     import BluetoothAdapterHIDReportTests
from autotest_lib.server.cros.bluetooth.bluetooth_sdp_tests import \
     BluetoothSDPTests


class bluetooth_AdapterCLHealth(BluetoothAdapterQuickTests,
        BluetoothAdapterPairingTests,
        BluetoothAdapterHIDReportTests,
        BluetoothSDPTests):
    """A Batch of Bluetooth Classic health tests. This test is written as a batch
       of tests in order to reduce test time, since auto-test ramp up time is
       costly. The batch is using BluetoothAdapterQuickTests wrapper methods to
       start and end a test and a batch of tests.

       This class can be called to run the entire test batch or to run a
       specific test only
    """

    test_wrapper = BluetoothAdapterQuickTests.quick_test_test_decorator
    batch_wrapper = BluetoothAdapterQuickTests.quick_test_batch_decorator


    @test_wrapper('Discovery Test', devices={"MOUSE": 1}, supports_floss=True)
    def cl_adapter_discovery_test(self):
        """Performs pairing test with mouse peripheral"""
        device = self.devices['MOUSE'][0]

        self.test_discover_device(device.address)
        self.test_device_name(device.address, device.name)


    @test_wrapper('Discoverable Test',
                  devices={"MOUSE": 1},
                  supports_floss=True)
    def cl_adapter_discoverable_test(self):
        """Verifies that DUT can become discoverable and be discovered"""

        device = self.devices['MOUSE'][0]

        # Put DUT into discoverable state
        self.test_discoverable()

        # Try and discover DUT from peripheral device
        self.test_discover_by_device(device)


    @test_wrapper('Pairing Test', devices={"MOUSE": 1}, supports_floss=True)
    def cl_adapter_pairing_test(self):
        """Performs pairing test with mouse peripheral"""
        device = self.devices['MOUSE'][0]
        self.pairing_test(device,
                          check_connected_method=\
                          self.test_mouse_right_click)


    @test_wrapper('keyboard Pairing Test',
                  devices={"KEYBOARD": 1},
                  supports_floss=True)
    def cl_adapter_keyboard_pairing_test(self):
        """Performs pairing test with keyboard peripheral"""
        device = self.devices['KEYBOARD'][0]
        self.pairing_test(device,
                          check_connected_method=\
                          self.run_keyboard_tests)


    @test_wrapper('Pairing Suspend Resume Test',
                  devices={"MOUSE": 1},
                  supports_floss=True)
    def cl_adapter_pairing_suspend_resume_test(self):
        """Performs pairing test over resume with mouse peripheral"""
        device = self.devices['MOUSE'][0]
        self.pairing_test(device,
                          check_connected_method=\
                          self.test_mouse_right_click,
                          suspend_resume=True)


    @test_wrapper('Pairing Twice Test',
                  devices={"MOUSE": 1},
                  supports_floss=True)
    def cl_adapter_pairing_twice_test(self):
        """Performs pairing twice test with  mouse peripheral"""
        device = self.devices['MOUSE'][0]
        self.pairing_test(device,
                          check_connected_method=\
                          self.test_mouse_right_click,
                          pairing_twice=True)


    @test_wrapper('HID Reports Test',
                  devices={"MOUSE": 1},
                  supports_floss=True)
    def cl_HID_reports_test(self):
        """Performs HID report test with mouse peripheral"""
        device = self.devices['MOUSE'][0]
        self.run_hid_reports_test(device,
                          check_connected_method=\
                          self.test_mouse_right_click)


    @test_wrapper('HID keyboard Reports Test',
                  devices={'KEYBOARD': 1},
                  supports_floss=True)
    def cl_HID_keyboard_reports_test(self):
        """Performs HID report test with keyboard peripheral"""
        device = self.devices['KEYBOARD'][0]
        self.run_hid_reports_test(device,
                          check_connected_method=\
                          self.run_keyboard_tests)


    @test_wrapper('HID Reconnect Speed Test',
                  devices={"MOUSE": 1},
                  flags=['Quick Health'],
                  supports_floss=True)
    def cl_HID_reconnect_speed_test(self):
        """Performs HID reconnect speed test with mouse peripheral"""
        device = self.devices['MOUSE'][0]
        self.hid_reconnect_speed(device=device, device_type='MOUSE')


    @test_wrapper('HID Reports Suspend Resume Test',
                  devices={"MOUSE": 1},
                  supports_floss=True)
    def cl_HID_reports_suspend_resume_test(self):
        """Performs HID report test over resume with mouse peripheral"""
        device = self.devices['MOUSE'][0]
        self.run_hid_reports_test(device,
                          check_connected_method=\
                          self.test_mouse_right_click, suspend_resume=True)


    @test_wrapper('HID Reports Reboot Test',
                  devices={"MOUSE": 1},
                  supports_floss=True)
    def cl_HID_reports_reboot_test(self):
        """Performs HID report test over reboot with mouse peripheral"""
        device = self.devices['MOUSE'][0]
        self.run_hid_reports_test(device,
                          check_connected_method=\
                          self.test_mouse_right_click, reboot=True)


    @test_wrapper('HID Reports Restart Test',
                  devices={"MOUSE": 1},
                  flags=['Quick Health'],
                  supports_floss=True)
    def cl_HID_reports_restart_test(self):
        """Performs HID report test over bluetoothd restart with mouse
           peripheral
        """
        device = self.devices['MOUSE'][0]
        self.run_hid_reports_test(
                device,
                check_connected_method=self.test_mouse_move_in_xy,
                restart=True)


    @test_wrapper('Connect Disconnect by Device Loop Test',
                  devices={"MOUSE": 1},
                  flags=['Quick Health'],
                  supports_floss=True)
    def cl_connect_disconnect_by_device_loop_test(self):
        """Performs connect/disconnect by device test with mouse peripheral"""
        device = self.devices['MOUSE'][0]
        self.connect_disconnect_by_device_loop(
                device=device,
                loops=3,
                device_type='MOUSE',
                check_connected_method=self.test_mouse_move_in_xy)


    @test_wrapper('Connect Disconnect Loop Test',
                  devices={"MOUSE": 1},
                  supports_floss=True)
    def cl_connect_disconnect_loop_test(self):
        """Performs connect/disconnect test with mouse peripheral"""
        device = self.devices['MOUSE'][0]
        self.connect_disconnect_loop(device=device, loops=3)


    @test_wrapper('Page scan during Inquiry', devices={"MOUSE": 1})
    def cl_page_scan_during_inquiry(self):
        """Checks page scan is working during inquiry.

        Scan and pair peer device.
        Start inquiry.
        Disconnect peer device from DUT.
        Reconnect peer device by initiating connection from peer.
        """
        device = self.devices['MOUSE'][0]

        # Setup
        self.assert_discover_and_pair(device)
        self.test_start_discovery()

        # Disconnection should set up page scan so a reconnect immediately
        # afterwards should always succeed
        self.test_disconnection_by_adapter(device.address)
        self.test_connection_by_device(device)

        # Cleanup
        self.test_stop_discovery()

    @test_wrapper('SDP Service Browse Test', devices={"BLUETOOTH_TESTER":1})
    def cl_sdp_service_browse_test(self):
        """Performs sdp browse with tester peripheral"""
        device = self.devices['BLUETOOTH_TESTER'][0]
        self.sdp_service_browse_test(device=device)


    @test_wrapper('SDP Service Attribute Request Test', \
                  devices={"BLUETOOTH_TESTER":1})
    def cl_sdp_service_attribute_request_test(self):
        """Performs sdp browse with tester peripheral"""
        device = self.devices['BLUETOOTH_TESTER'][0]
        self.sdp_service_attribute_request_test(device=device)


    @test_wrapper('SDP Service Search Attribute Request Test', \
                  devices={"BLUETOOTH_TESTER":1})
    def cl_sdp_service_search_attribute_request_test(self):
        """Performs sdp browse with tester peripheral"""
        device = self.devices['BLUETOOTH_TESTER'][0]
        self.sdp_service_search_attribute_request_test(device=device)


    @test_wrapper('SDP Service Search Request Basic Test', \
                  devices={"BLUETOOTH_TESTER":1})
    def cl_sdp_service_search_request_basic_test(self):
        """Performs sdp browse with tester peripheral"""
        device = self.devices['BLUETOOTH_TESTER'][0]
        self.sdp_service_search_request_basic_test(device=device)


    @batch_wrapper('Classic Health')
    def cl_health_batch_run(self, num_iterations=1, test_name=None):
        """Run the Classic health test batch or a specific given test.
           The wrapper of this method is implemented in batch_decorator.
           Using the decorator a test batch method can implement the only its
           core tests invocations and let the decorator handle the wrapper,
           which is taking care for whether to run a specific test or the
           batch as a whole, and running the batch in iterations

           @param num_iterations: how many interations to run
           @param test_name: specifc test to run otherwise None to run the
                             whole batch
        """
        self.cl_HID_keyboard_reports_test()
        self.cl_HID_reports_reboot_test()
        self.cl_HID_reports_restart_test()
        self.cl_HID_reports_suspend_resume_test()
        self.cl_HID_reports_test()
        self.cl_adapter_discoverable_test()
        self.cl_adapter_discovery_test()
        self.cl_adapter_keyboard_pairing_test()
        self.cl_adapter_pairing_suspend_resume_test()
        self.cl_adapter_pairing_test()
        self.cl_adapter_pairing_twice_test()
        self.cl_connect_disconnect_by_device_loop_test()
        self.cl_connect_disconnect_loop_test()
        self.cl_sdp_service_attribute_request_test()
        self.cl_sdp_service_browse_test()
        self.cl_sdp_service_search_attribute_request_test()
        self.cl_sdp_service_search_request_basic_test()


    def run_once(self,
                 host,
                 num_iterations=1,
                 args_dict=None,
                 test_name=None,
                 flag='Quick Health',
                 floss=False):
        """Run the batch of Bluetooth Classic health tests

        @param host: the DUT, usually a chromebook
        @param num_iterations: the number of rounds to execute the test
        @test_name: the test to run, or None for all tests
        """

        # Initialize and run the test batch or the requested specific test
        self.quick_test_init(host,
                             use_btpeer=True,
                             flag=flag,
                             args_dict=args_dict,
                             floss=floss)
        self.cl_health_batch_run(num_iterations, test_name)
        self.quick_test_cleanup()
