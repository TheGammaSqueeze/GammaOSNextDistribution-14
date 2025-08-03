# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import utils
from autotest_lib.server import test
from autotest_lib.server.hosts.tls_client import connection
from autotest_lib.server.hosts.tls_client import exec_dut_command


class infra_TLSExecDUTCommand(test.test):
    """
    Run a command on the host via the TLS API (ExecDutCommand) and ensure the
    behavior matches the desired test.

    """

    version = 1

    def run_once(self, host, case):
        """
        Run the test.

        @param host: A host object representing the DUT.
        @param case: The case to run.

        """
        tlsconn = connection.TLSConnection()
        self.tlsclient = exec_dut_command.TLSExecDutCommandClient(
                tlsconn, host.hostname)
        if case == "basic":
            self.basic()
        elif case == "stress":
            self.stress()
        elif case == "stress_fail":
            self.stress_fail()
        elif case == "timeout":
            self.timeout()
        else:
            raise error.TestError("Case {} does not exist".format(case))

    def timeout(self):
        """Test that the timeout is respected."""
        try:
            self.tlsclient.run_cmd("sleep 10", timeout=5)
        except error.CmdTimeoutError:
            return
        raise error.TestError("Command did not timeout.")

    def stress(self):
        """Basic command 500 times in a row."""
        for i in range(500):
            self.basic()

    def stress_fail(self):
        """Test a cmd that should return exit_status of 1 does so, reliably."""
        for i in range(500):
            res = self.tlsclient.run_cmd("NonExistingCommand")
            if res.exit_status == 0:
                raise error.TestError(
                        "TLS SSH exit status was: '{}'. Expected != 0".format(
                                res.exit_status))

    def basic(self):
        """Run a command over the TLS ExecDutCommand API. Verify output."""
        res = self.tlsclient.run_cmd("echo success")
        if not isinstance(res, utils.CmdResult):
            raise error.TestError(
                "Client returned type: '{}'. Expected type: 'utils.CmdResult'"
                .format(type(res)))
        if res.exit_status != 0:
            logging.info("STD_ERR of res {}".format(res.stderr))
            raise error.TestError(
                    "TLS SSH exit status was: '{}'. Expected: '0'".format(
                            res.exit_status))
        if res.stdout != "success\n":
            raise error.TestError("TLS returned: '{}'. Expected: '{}'".format(
                    res.stdout, "success\n"))
