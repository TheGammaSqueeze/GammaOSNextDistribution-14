# Lint as: python2, python3
# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server import test
from autotest_lib.server.cros.cellular.callbox_utils import CallboxLookup as cbl
from autotest_lib.server.cros.cellular.callbox_utils import cmw500_cellular_simulator as cmw
from autotest_lib.server.cros.cellular.simulation_utils import ChromebookCellularDut
from autotest_lib.server.cros.cellular.simulation_utils import LteSimulation


class cellular_Callbox_AssertSMS(test.test):
    """
    Asserts that SMS functionality works.

    This test asserts that SMS messages are received. It does so by connecting
    to the callbox, setting the text message to be a unique string, asserting
    that the text is not in /var/log/net.log, restarting the modemmanager
    with log level in debug mode so that SMS messages are sent to
    /var/log/net.log, sending the SMS message, then asserting that the
    string appears in /var/log/net.log
    """
    version = 1

    def run_once(self, host):
        """Simple test that asserts that SMS messages are received
        by the Chromebook DUT
        """
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
        host.run("stop modemmanager")
        host.run("start modemmanager MM_LOGLEVEL=DEBUG")
        text_string = "SMSWRAPPER" + str(time.time()) + "SMSWRAPPER"
        try:
            grep_out = host.run("cat /var/log/net.log | grep %s" % text_string)
        except:
            pass
        else:
            raise error.TestFailure(
                    "Expected not to find '%s', got '%s'"
                    % (text_string, grep_out))
        self.simulation.send_sms(text_string)
        try:
            grep_out = host.run("cat /var/log/net.log | grep %s" % text_string)
        except:
            raise error.TestFailure(
                "Expected string (%s) not found in /var/log/net.log"
                % text_string)
