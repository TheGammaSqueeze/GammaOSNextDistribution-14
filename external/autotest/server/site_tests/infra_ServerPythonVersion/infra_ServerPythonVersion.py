# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import sys

from autotest_lib.client.common_lib import error
from autotest_lib.server import autotest
from autotest_lib.server import test


class infra_ServerPythonVersion(test.test):
    """Checks the version on the server, then client."""
    version = 1

    def run_once(self, host, case):
        """
        Starting point of this test.

        Note: base class sets host as self._host.

        """
        self.host = host

        self.autotest_client = autotest.Autotest(self.host)
        if sys.version_info.major != case:
            raise error.TestFail("Not running in python version %s" % case)

        self.autotest_client.run_test('infra_PythonVersion',
                                      case=case,
                                      check_client_result=True)
