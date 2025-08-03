# Lint as: python2, python3
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Server side bluetooth adapter stress tests involving power consumption."""

import logging
import multiprocessing
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_tests import (
        test_case_log)
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests import (
        BluetoothAdapterQuickTests)


class bluetooth_AdapterPowerMeasure(BluetoothAdapterQuickTests):
    """Server side bluetooth adapter power consumption test."""

    test_wrapper = BluetoothAdapterQuickTests.quick_test_test_decorator
    batch_wrapper = BluetoothAdapterQuickTests.quick_test_batch_decorator


    def _check_legitimate_board(self):
        """Check if this is a legitimate board to run the test.

        Only a limited set of boards are supported for now, primarily
        the kukui family of barods.

        @raises: TestNAError if the board is not legitimate.

        """
        board = self.host.get_board().split(':')[1]
        if board not in ('kukui'):
            raise error.TestNAError('%s not legitimate to run the test.' %
                                    board)


    def _initialize_servod(self, device):
        """Perform initialize for servod task.

        @param device: the peer device

        @raises: TestError if not able to enable or start servo.
        """
        self.count_fail_to_sleep = 0
        self.count_fail_to_resume = 0
        self.count_system_resume_prematurely = 0
        self.count_success = 0

        # When the autotest restarts ui, chrome would issue some Bluetooth
        # commands which may prevent the system from suspending properly.
        # Hence, let's stop ui for now.
        self.host.run_short('stop ui')

        board = self.host.get_board().split(':')[1]
        logging.info('board: %s', board)

        # TODO(b/152737849): figure out a way to support more boards.
        self._check_legitimate_board()

        # device is a pure XMLRPC server running as chameleond
        # on the bluetooth peer. We need to enable Servod.
        if not device.EnableServod(board):
            raise error.TestError('Failed to enable Servod.')

        # Start the Servod process on the bluetooth peer.
        if not device.servod.Start():
            raise error.TestError('Failed to start Servod on bluetooth peer.')


    def _cleanup_servod(self, device):
        """Perform cleanup for servod.

        @param device: the peer device
        """
        if not device.servod.Stop():
            logging.error('Failed to stop Servod on bluetooth peer.')

        self.host.run_short('start ui')

        logging.info('count_fail_to_sleep: %d', self.count_fail_to_sleep)
        logging.info('count_fail_to_resume: %d', self.count_fail_to_resume)
        logging.info('count_system_resume_prematurely: %d',
                     self.count_system_resume_prematurely)
        logging.info('count_success: %d', self.count_success)


    # ---------------------------------------------------------------
    # Definitions of test cases
    # ---------------------------------------------------------------

    @test_case_log
    def test_case_suspend_power_measurement(self, host, device, max_power_mw,
                                            suspend_time_secs,
                                            resume_network_timeout_secs=60):
        """Test Case: measure the Bluetooth chip power consumption on suspend"""

        def print_debug_count():
            """Print the debug message about count values."""
            logging.debug('count_fail_to_sleep: %d', self.count_fail_to_sleep)
            logging.debug('count_fail_to_resume: %d', self.count_fail_to_resume)
            logging.debug('count_system_resume_prematurely: %d',
                          self.count_system_resume_prematurely)
            logging.debug('count_success: %d', self.count_success)

        def action_suspend():
            """Calls the host method suspend."""
            host.suspend(suspend_time=suspend_time_secs,
                         allow_early_resume=True)

        boot_id = host.get_boot_id()
        proc = multiprocessing.Process(target=action_suspend)
        proc.daemon = True
        start_time = time.time()
        proc.start()

        # Block waiting until the system has suspended.
        try:
            host.test_wait_for_sleep(suspend_time_secs)
        except Exception as e:
            logging.error('host.test_wait_for_sleep failed: %s', e)
            self.count_fail_to_sleep += 1
            print_debug_count()
            # Skip this time since the system failed to suspend.
            proc.join()
            return

        # Test the Bluetooth chip power consumption.
        if self.test_power_consumption(device, max_power_mw):
            self.count_success += 1

        # Block waiting until the system has resumed.
        try:
            host.test_wait_for_resume(
                    boot_id, suspend_time_secs + resume_network_timeout_secs)
        except Exception as e:
            logging.error('host.test_wait_for_resume failed: %s', e)
            self.count_fail_to_resume += 1

        # If the system resumes prematurely, do not conduct the test in
        # this iteration.
        actual_suspend_time_secs = time.time() - start_time
        if actual_suspend_time_secs < suspend_time_secs:
            logging.error('actual suspension time %f is less than expected %f',
                          actual_suspend_time_secs, suspend_time_secs)
            self.count_system_resume_prematurely += 1

        print_debug_count()
        proc.join()

        if self.count_success == 0:
            raise error.TestError('System failed to suspend/resume.')


    # ---------------------------------------------------------------
    # Definitions of test wrapper tests and batch wrapper tests.
    # ---------------------------------------------------------------


    @test_wrapper('Power measurement test', devices={'BLUETOOTH_BASE':1})
    def pw_measurement_suspension_test(self):
        """power measurement test during system suspension."""
        device = self.devices['BLUETOOTH_BASE'][0]
        self._initialize_servod(device)
        self.test_power_on_adapter()
        self.test_bluetoothd_running()
        self.test_case_suspend_power_measurement(self.host, device,
                                                 self.max_power_mw,
                                                 self.suspend_time_secs)
        self._cleanup_servod(device)


    @batch_wrapper('Bluetooth Power Measurement Health Tests')
    def pw_health_batch_run(self, num_iterations=1, test_name=None):
        """Run bluetooth power measurement health test batch or a specific test.

        @param num_iterations: how many iterations to run
        @param test_name: specific test to run otherwise None to run the
                whole batch
        """
        self.pw_measurement_suspension_test()


    def run_once(self,
                 host,
                 num_iterations=1,
                 args_dict=None,
                 test_name=None,
                 max_power_mw=3,
                 suspend_time_secs=30,
                 flag='Quick Health'):
        """Running Bluetooth adapter power consumption autotest during system
        suspension.

        @param host: the DUT host.
        @param num_iterations: number of times to perform the tests.
        @param test_name: the test to run, or None for all tests
        @param max_power_mw: max power allowed in milli-watt
        @param suspend_time_secs: the system suspension duration in seconds

        """
        self.host = host
        self.max_power_mw = max_power_mw
        self.suspend_time_secs = suspend_time_secs

        self.quick_test_init(host,
                             use_btpeer=True,
                             flag=flag,
                             args_dict=args_dict)
        self.pw_health_batch_run(num_iterations, test_name)
        self.quick_test_cleanup()
