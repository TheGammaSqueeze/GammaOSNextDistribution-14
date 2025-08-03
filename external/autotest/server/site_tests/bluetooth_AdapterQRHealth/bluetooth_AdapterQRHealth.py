# Lint as: python2, python3
# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""A Batch of Bluetooth Quality Report tests"""

import collections
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.bluetooth.bluetooth_audio_test_data import (
        A2DP_MEDIUM, HFP_WBS, HFP_NBS, HFP_WBS_MEDIUM, HFP_NBS_MEDIUM)
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_qr_tests import (
        BluetoothAdapterQRTests, QR_UNSUPPORTED_CHIPSETS)
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests import (
        BluetoothAdapterQuickTests)


class bluetooth_AdapterQRHealth(BluetoothAdapterQuickTests,
                                BluetoothAdapterQRTests):
    """A Batch of Bluetooth audio health tests"""

    test_wrapper = BluetoothAdapterQuickTests.quick_test_test_decorator
    batch_wrapper = BluetoothAdapterQuickTests.quick_test_batch_decorator

    def run_test_method(self,
                        devices,
                        test_method,
                        test_profile,
                        logging_and_check=True):
        """Common procedure to run a specific test method.

        @param devices: a list of devices.
        @param test_method: the test method to run.
        @param test_profile: audio test profile to use.
        @param logging_and_check: set this to True to opend the quality
                                  report log checking.
        """

        if not isinstance(devices, collections.Iterable):
            devices = (devices, )

        num_devices = len(devices)

        # Make sure WBS profile works fine.
        if test_profile in (HFP_WBS, HFP_WBS_MEDIUM):
            if self.check_wbs_capability():
                if not self.bluetooth_facade.enable_wbs(True):
                    raise error.TestError('failed to enble wbs')
            else:
                raise error.TestNAError(
                        'The DUT does not support WBS. Skip the test.')
        elif test_profile in (HFP_NBS, HFP_NBS_MEDIUM):
            if not self.bluetooth_facade.enable_wbs(False):
                raise error.TestError('failed to disable wbs')

        time.sleep(3)

        self.test_reset_on_adapter()
        self.test_bluetoothd_running()

        for device in devices:
            if device.device_type == 'BLUETOOTH_AUDIO':
                self.initialize_bluetooth_audio(device, test_profile)

            self.test_discover_device(device.address)
            self.test_pairing(device.address, device.pin, trusted=True)
            self.test_connection_by_adapter(device.address)

            time.sleep(2)

        if logging_and_check:
            self.dut_btmon_log_path = self.start_new_btmon()
            self.enable_disable_debug_log(enable=True)
            self.enable_disable_quality_debug_log(enable=True)

        test_method()

        if logging_and_check:
            self.test_send_log()
            self.check_qr_event_log(num_devices=num_devices)
            self.enable_disable_quality_debug_log(enable=False)
            self.enable_disable_debug_log(enable=False)

        for device in devices:
            self.test_disconnection_by_adapter(device.address)

            if device.device_type == 'BLUETOOTH_AUDIO':
                self.cleanup_bluetooth_audio(device, test_profile)

    # Remove flags=['Quick Health'] when this test is migrated to stable suite.
    @test_wrapper('Quality Report A2DP test',
                  devices={'BLUETOOTH_AUDIO': 1},
                  flags=['Quick Health'],
                  skip_chipsets=QR_UNSUPPORTED_CHIPSETS)
    def qr_a2dp_test(self):
        """Quality Report A2DP test"""
        device = self.devices['BLUETOOTH_AUDIO'][0]
        test_profile = A2DP_MEDIUM
        test_method = lambda: self.qr_a2dp(device, test_profile)

        self.run_test_method(device, test_method, test_profile)

    # Remove flags=['Quick Health'] when this test is migrated to stable suite.
    @test_wrapper('Quality Report power cycle and A2DP test',
                  devices={'BLUETOOTH_AUDIO': 1},
                  flags=['Quick Health'],
                  skip_chipsets=QR_UNSUPPORTED_CHIPSETS)
    def qr_power_cycle_a2dp_test(self):
        """Quality Report power cycle and A2DP test"""
        device = self.devices['BLUETOOTH_AUDIO'][0]
        test_profile = A2DP_MEDIUM
        test_method = lambda: self.qr_power_cycle_a2dp(device, test_profile)

        self.run_test_method(device, test_method, test_profile)

    # Remove flags=['Quick Health'] when this test is migrated to stable suite.
    @test_wrapper('Quality Report HFP NBS dut as source test',
                  devices={'BLUETOOTH_AUDIO': 1},
                  flags=['Quick Health'],
                  skip_chipsets=QR_UNSUPPORTED_CHIPSETS)
    def qr_hfp_nbs_dut_as_src_test(self):
        """Quality Report HFP NBS dut as source test"""
        device = self.devices['BLUETOOTH_AUDIO'][0]
        test_profile = HFP_NBS_MEDIUM
        test_method = lambda: self.qr_hfp_dut_as_src(device, test_profile)

        self.run_test_method(device, test_method, test_profile)

    # Remove flags=['Quick Health'] when this test is migrated to stable suite.
    @test_wrapper('Quality Report HFP WBS dut as source test',
                  devices={'BLUETOOTH_AUDIO': 1},
                  flags=['Quick Health'],
                  skip_chipsets=QR_UNSUPPORTED_CHIPSETS)
    def qr_hfp_wbs_dut_as_src_test(self):
        """Quality Report HFP WBS dut as source test"""
        device = self.devices['BLUETOOTH_AUDIO'][0]
        test_profile = HFP_WBS_MEDIUM
        test_method = lambda: self.qr_hfp_dut_as_src(device, test_profile)

        self.run_test_method(device, test_method, test_profile)

    # Remove flags=['Quick Health'] when this test is migrated to stable suite.
    @test_wrapper('Quality Report disabled A2DP test',
                  devices={'BLUETOOTH_AUDIO': 1},
                  flags=['Quick Health'],
                  skip_chipsets=QR_UNSUPPORTED_CHIPSETS)
    def qr_disabled_a2dp_test(self):
        """Quality Report disabled A2DP test"""
        device = self.devices['BLUETOOTH_AUDIO'][0]
        test_profile = A2DP_MEDIUM
        test_method = lambda: self.qr_disabled_a2dp(device, test_profile)

        self.run_test_method(device,
                             test_method,
                             test_profile,
                             logging_and_check=False)

    # Remove flags=['Quick Health'] when this test is migrated to stable suite.
    @test_wrapper('Quality Report A2DP and classic keyboard test',
                  devices={
                          'BLUETOOTH_AUDIO': 1,
                          "KEYBOARD": 1
                  },
                  flags=['Quick Health'],
                  skip_chipsets=QR_UNSUPPORTED_CHIPSETS)
    def qr_a2dp_cl_keyboard_test(self):
        """Quality Report A2DP and classic keyboard test"""
        audio_device = self.devices['BLUETOOTH_AUDIO'][0]
        keyboard_device = self.devices['KEYBOARD'][0]
        test_profile = A2DP_MEDIUM
        test_method = lambda: self.qr_a2dp_cl_keyboard(
                audio_device, keyboard_device, test_profile)

        self.run_test_method((audio_device, keyboard_device),
                             test_method,
                             test_profile)

    # Remove flags=['Quick Health'] when this test is migrated to stable suite.
    @test_wrapper(
            'Quality Report HFP WBS dut as sink and classic keyboard test',
            devices={
                    'BLUETOOTH_AUDIO': 1,
                    'KEYBOARD': 1
            },
            flags=['Quick Health'],
            skip_chipsets=QR_UNSUPPORTED_CHIPSETS)
    def qr_hfp_wbs_dut_as_sink_cl_keyboard_test(self):
        """Quality Report HFP WBS dut as sink and classic keyboard test"""
        audio_device = self.devices['BLUETOOTH_AUDIO'][0]
        keyboard_device = self.devices['KEYBOARD'][0]
        test_profile = HFP_WBS_MEDIUM
        test_method = lambda: self.qr_hfp_dut_as_sink_cl_keyboard(
                audio_device, keyboard_device, test_profile)

        self.run_test_method((audio_device, keyboard_device),
                             test_method,
                             test_profile)

    # Remove flags=['Quick Health'] when this test is migrated to stable suite.
    @test_wrapper(
            'Quality Report HFP NBS dut as sink and classic keyboard test',
            devices={
                    'BLUETOOTH_AUDIO': 1,
                    'KEYBOARD': 1
            },
            flags=['Quick Health'],
            skip_chipsets=QR_UNSUPPORTED_CHIPSETS)
    def qr_hfp_nbs_dut_as_sink_cl_keyboard_test(self):
        """Quality Report HFP NBS dut as sink and classic keyboard test"""
        audio_device = self.devices['BLUETOOTH_AUDIO'][0]
        keyboard_device = self.devices['KEYBOARD'][0]
        test_profile = HFP_NBS_MEDIUM
        test_method = lambda: self.qr_hfp_dut_as_sink_cl_keyboard(
                audio_device, keyboard_device, test_profile)

        self.run_test_method((audio_device, keyboard_device),
                             test_method,
                             test_profile)

    @batch_wrapper('Bluetooth BQR Batch Health Tests')
    def qr_health_batch_run(self, num_iterations=1, test_name=None):
        """Run the bluetooth audio health test batch or a specific given test.

        @param num_iterations: how many iterations to run
        @param test_name: specific test to run otherwise None to run the
                whole batch
        """
        self.qr_a2dp_test()
        self.qr_power_cycle_a2dp_test()
        self.qr_hfp_nbs_dut_as_src_test()
        self.qr_hfp_wbs_dut_as_src_test()
        self.qr_disabled_a2dp_test()
        self.qr_a2dp_cl_keyboard_test()
        self.qr_hfp_wbs_dut_as_sink_cl_keyboard_test()
        self.qr_hfp_nbs_dut_as_sink_cl_keyboard_test()

    def run_once(self,
                 host,
                 num_iterations=1,
                 args_dict=None,
                 test_name=None,
                 flag='Quick Health'):
        """Run the batch of Bluetooth stand health tests

        @param host: the DUT, usually a chromebook
        @param num_iterations: the number of rounds to execute the test
        @param test_name: the test to run, or None for all tests
        """
        self.host = host

        self.quick_test_init(host,
                             use_btpeer=True,
                             flag=flag,
                             args_dict=args_dict)
        self.qr_health_batch_run(num_iterations, test_name)
        self.quick_test_cleanup()
