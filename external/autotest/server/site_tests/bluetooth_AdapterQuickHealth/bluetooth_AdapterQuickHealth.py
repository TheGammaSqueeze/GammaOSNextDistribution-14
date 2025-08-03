# Lint as: python2, python3
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""This class implements a Bluetooth quick health package"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

from autotest_lib.server.site_tests.bluetooth_AdapterAdvMonitor import (
        bluetooth_AdapterAdvMonitor)
from autotest_lib.server.site_tests.bluetooth_AdapterAUHealth import (
        bluetooth_AdapterAUHealth)
from autotest_lib.server.site_tests.bluetooth_AdapterCLHealth import (
        bluetooth_AdapterCLHealth)
from autotest_lib.server.site_tests.bluetooth_AdapterLEHealth import (
        bluetooth_AdapterLEHealth)
from autotest_lib.server.site_tests.bluetooth_AdapterMDHealth import (
        bluetooth_AdapterMDHealth)
from autotest_lib.server.site_tests.bluetooth_AdapterSAHealth import (
        bluetooth_AdapterSAHealth)
from autotest_lib.server.site_tests.bluetooth_AdapterSRHealth import (
        bluetooth_AdapterSRHealth)

from six.moves import range


class bluetooth_AdapterQuickHealth(
        bluetooth_AdapterAdvMonitor.bluetooth_AdapterAdvMonitor,
        bluetooth_AdapterAUHealth.bluetooth_AdapterAUHealth,
        bluetooth_AdapterCLHealth.bluetooth_AdapterCLHealth,
        bluetooth_AdapterLEHealth.bluetooth_AdapterLEHealth,
        bluetooth_AdapterSAHealth.bluetooth_AdapterSAHealth,
        bluetooth_AdapterSRHealth.bluetooth_AdapterSRHealth,
        bluetooth_AdapterMDHealth.bluetooth_AdapterMDHealth):
    """This class implements a Bluetooth quick health package, using methods
    provided in BluetoothAdapterQuickTests,
    The package is running several sub batches of tests.
    A batch is defined as a set of tests, preferably with a common subject, e.g
    'LE Health' batch, or the 'Stand Alone Health' batch.
    The quick health test package is improving test time by doing the minimal
    cleanups between each test and test batches, saving the auto-test ramp up
    time of about 90-120 second per test.
    """

    def run_once(self,
                 host,
                 num_iterations=1,
                 args_dict=None,
                 flag='Quick Health'):
        """Run the package of Bluetooth LE health tests

        @param host: the DUT, usually a chromebook
        @param num_iterations: the number of rounds to execute the test
        """

        # Init the quick test and start the package
        self.quick_test_init(host,
                             use_btpeer=True,
                             flag=flag,
                             args_dict=args_dict)
        self.quick_test_package_start('BT Quick Health')

        # Run health package
        for iter in range(1, num_iterations + 1):
            self.quick_test_package_update_iteration(iter)
            self.sa_health_batch_run()
            self.cl_health_batch_run()
            self.le_health_batch_run()
            self.md_health_batch_run()
            self.sr_health_batch_run()
            self.au_health_batch_run()
            self.advmon_health_batch_run()
            self.quick_test_print_summary()

        # End and cleanup test package
        self.quick_test_package_end()
        self.quick_test_cleanup()
