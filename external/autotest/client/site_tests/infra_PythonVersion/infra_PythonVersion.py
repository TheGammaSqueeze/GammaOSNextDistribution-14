# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import sys

from autotest_lib.client.bin import test
from autotest_lib.client.common_lib import error


class infra_PythonVersion(test.test):
    """Test to be run locally for checking Python version in Autotest."""
    version = 1

    def run_once(self, case):
        """Verify the running Python Version is as expected."""
        if sys.version_info.major != case:
            raise error.TestFail("Not running in python version %s" % case)
        return
