# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.server import autotest
from autotest_lib.server import test


class stub_ServerToClientPass(test.test):
    """
    stub_ServerToClientPass test used to kick off stub_Pass client test.

    """
    version = 1

    def run_once(self, host):
        """
        Starting point of this test.

        Note: base class sets host as self._host.

        """
        self.host = host

        self.autotest_client = autotest.Autotest(self.host)
        self.autotest_client.run_test('stub_Pass', check_client_result=True)
