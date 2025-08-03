# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib import error
from autotest_lib.server import test


class infra_DutServers(test.test):
    """
    Verify the dutserver dut flag reaches a test.
    """
    version = 1

    def run_once(self, host, dut_servers):
        """
        Starting point of this test.
        Note: base class sets host as self._host.

        @param host:        The host address of the DUT
        @param dut_servers: A list of server specified by --dut_servers flag.

        @returns: Nothing but will raise an error if the dut_servers is empty.
        """
        self.host = host
        if not dut_servers:
            raise error.TestError("DUT Server list is empty")
        for c in dut_servers:
            if not c:
                raise error.TestError("DUT Server list %s has empty elements",
                                      dut_servers)
