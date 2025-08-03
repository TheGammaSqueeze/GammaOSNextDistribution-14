# Lint as: python2, python3
# Copyright (c) 2011 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.bin import test


class cellular_ValidateTestEnvironment(test.test):
    """
    Verify that the test setup common to all other tests has no failures.
    """
    version = 1

    def run_once(self, test_env):
        """ Runs the test once """
        with test_env:
            self.test_env = test_env
            # Do nothing else. This is enough to initialize and terminate the
            # test environment.
