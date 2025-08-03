# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
""" Bluetooth test that checks the RSSI of Bluetooth peers
This to used for checking Bluetooth test beds

"""
from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging

from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests import (
        BluetoothAdapterQuickTests)

from autotest_lib.server.cros.bluetooth.bluetooth_adapter_tests import (
        test_retry_and_log)

test_wrapper = BluetoothAdapterQuickTests.quick_test_test_decorator
batch_wrapper = BluetoothAdapterQuickTests.quick_test_batch_decorator


class bluetooth_PeerVerify(BluetoothAdapterQuickTests):
    """Test to check the RSSI of btpeers."""

    @test_wrapper('Check if rssi of peers > -70 ',
                  devices={'MOUSE': -1},
                  use_all_peers=True)
    @test_retry_and_log(False)
    def check_rssi(self):
        """Check if RSSI > -70. """
        try:
            rssi_list = []
            self.result = {}
            for n, device in enumerate(self.devices['MOUSE']):
                rssi = self.get_device_sample_rssi(device)
                rssi_list.append(rssi)
                logging.info('RSSI for peer %s is %s', n, rssi)
            logging.info('RSSI values are %s', rssi_list)
            self.results = {'rssi': rssi_list}
            return all([
                    True if rssi is not None and rssi > -70 else False
                    for rssi in rssi_list
            ])
        except Exception as e:
            logging.debug('exception in test_check_rssi %s', str(e))
            return False

    @batch_wrapper('Verify Peer RSSI')
    def verify_peer_batch_run(self, num_iterations=1, test_name=None):
        """ Batch of checks for btpeer. """
        self.check_rssi()

    def run_once(self,
                 host,
                 num_iterations=1,
                 args_dict=None,
                 test_name=None,
                 flag='Quick Health'):
        """Running Bluetooth adapter suspend resume with peer autotest.

        @param host: the DUT, usually a chromebook
        @param num_iterations: the number of times to execute the test
        @param test_name: the test to run or None for all tests
        @param flag: run tests with this flag (default: Quick Health)

        """

        # Initialize and run the test batch or the requested specific test
        self.quick_test_init(host,
                             use_btpeer=True,
                             flag=flag,
                             args_dict=args_dict)
        self.verify_peer_batch_run(num_iterations, test_name)
        self.quick_test_cleanup()
