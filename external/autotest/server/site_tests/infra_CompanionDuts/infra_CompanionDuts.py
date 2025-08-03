# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib import error
from autotest_lib.server import test


class infra_CompanionDuts(test.test):
    """
    Verify the companion dut flag reaches a test.

    """
    version = 1

    def run_once(self, host, companions):
        """
        Starting point of this test.

        Note: base class sets host as self._host.

        """
        self.host = host
        for c in companions:
            dut_out = c.run('echo True').stdout.strip()
            if dut_out != 'True':
                raise error.TestError("Companion DUT stdout != True (got: %s)",
                                      dut_out)
