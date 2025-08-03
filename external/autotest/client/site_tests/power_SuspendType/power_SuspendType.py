# Lint as: python2, python3
# Copyright (c) 2010 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.bin import test
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.power import power_utils


class power_SuspendType(test.test):
    """class for power_SuspendType test."""
    version = 1

    def run_once(self, desired_suspend_type=None):
        """
        @param desired_suspend_type: check that device supports a specific
                state ("mem" or "freeze"). Just checks to ensure one of the
                2 is returned as the default suspend type
        """
        suspend_state = power_utils.get_sleep_state()

        if desired_suspend_type is None:
            if suspend_state != 'mem' and suspend_state != 'freeze':
                raise error.TestFail(
                        'Did not find valid suspend state, want: freeze or mem, got: '
                        + suspend_state)
        else:
            if suspend_state != desired_suspend_type:
                raise error.TestFail('System does not support suspend type ' +
                                     desired_suspend_type)
        return
