# Lint as: python2, python3
# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server import test
from autotest_lib.server.cros.cellular.callbox_utils import CallboxLookup as cbl
from autotest_lib.server.cros.cellular.callbox_utils import cmw500_cellular_simulator as cmw
from autotest_lib.server.cros.cellular.simulation_utils import ChromebookCellularDut
from autotest_lib.server.cros.cellular.simulation_utils import LteSimulation


class cellular_Callbox_AssertCellularData(test.test):
    """
    Asserts that cellular data works.

    The test establishes a connection to the appropriate CMW500 callbox. Then
    it asserts that the cellular data connection provided to it matches the
    data connection provided by ethernet. Any differences are considered an
    error. If the cellular data connection is not provided, the second curl
    will throw an exception.
    """
    version = 1

    def run_once(self, host):
        """Simple test that asserts that data provided through simulated
        cellular connection matches network ethernet."""
        self.log = logging.getLogger()
        self.sim = cmw.CMW500CellularSimulator(cbl.callboxes[host.hostname],
                                               5025)
        self.dut = ChromebookCellularDut.ChromebookCellularDut(host, self.log)
        self.simulation = LteSimulation.LteSimulation(
                self.sim, self.log, self.dut, {
                        'attach_retries': 1,
                        'attach_timeout': 120
                }, None)
        parameter_list = [
                'band', '2', 'bw', '20', 'mimo', '2x2', 'tm', '1', 'pul', '0',
                'pdl', 'high'
        ]
        self.simulation.parse_parameters(parameter_list)
        self.simulation.start()
        a = host.run("curl --interface eth0 google.com")
        b = host.run("curl --interface rmnet_data0 google.com")
        if a.stdout != b.stdout:
            raise error.TestFailure(
                    "Ethernet and cellular curl output not equal.")
