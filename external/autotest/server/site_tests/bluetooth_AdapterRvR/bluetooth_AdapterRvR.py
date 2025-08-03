# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""

Bluetooth tests which involve modifying attenuation between the
DUT and peer using controllable variable attentuator.

These tests can only be run in test bed containing variable attenuator
and bluetooth peer.

"""




import logging

from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests import (
        BluetoothAdapterQuickTests)
from autotest_lib.server.cros.bluetooth.bluetooth_rvr_tests import (
        BluetoothAdapterRvRTests)

test_wrapper = BluetoothAdapterQuickTests.quick_test_test_decorator
batch_wrapper = BluetoothAdapterQuickTests.quick_test_batch_decorator


class bluetooth_AdapterRvR(BluetoothAdapterQuickTests,
                           BluetoothAdapterRvRTests):
    """ Collection of Bluetooth Range vs Rate tests.

    rvr_show_rssi_vs_attenuation : This is not a test. It display RSSI vs
    attenutation for the test bed. This is used lab team to verify test beds.

    """

    @test_wrapper(
            'RSSI vs Attenuation',
            devices={'MOUSE': 1},
    )
    def rvr_show_rssi_vs_attenuation(self):
        """ Record RSSI at increasing attenuation """
        try:
            device_type = 'MOUSE'
            device = self.devices[device_type][0]
            logging.debug(' attenutor is %s', self.bt_attenuator)
            rssi_dict = self.check_rssi_vs_attenuation(device,
                                                       self.bt_attenuator)
            if rssi_dict == {}:
                logging.info(
                        'check_rssi_vs_attenuation did not return any data')
                return False
            else:
                logging.info('--------------------------')
                logging.info('Total attenutation : RSSI')
                for attn in sorted(list(rssi_dict.keys())):
                    rssi = rssi_dict[attn]
                    logging.info('%s : %s', attn, rssi)
                logging.info('--------------------------')
                return True
        except Exception as e:
            logging.error('Exception in rvr_show_rssi_vs_attenuation %s',
                          str(e))
            return False

    @batch_wrapper('Range vs Rate tests')
    def rvr_health_batch_run(self, num_iterations=1, test_name=None):
        """ Batch of Range vs Rate tests health tests. """
        self.rvr_show_rssi_vs_attenutation()

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
        self.rvr_health_batch_run(num_iterations, test_name)
        self.quick_test_cleanup()
