# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.server.cros.pvs import test_with_pass_criteria

HOURS = 60 * 60


class power_QualTestWrapper(test_with_pass_criteria.test_with_pass_criteria):
    """
    power_QualTestWrapper extends test_with_pass_criteria for the purpose of
    power qualification testing. We use the add_prefix_test method to add the
    two tests which must run before each power qualification test
    """

    version = 1

    def initialize(self, **args_dict):
        """
        initialize implements the initialize call in test.test, is called before
        execution of the test. In this wrapper, initialize also adds the test
        prefixes necessary for the power_Qual tests
        """
        super(power_QualTestWrapper,
              self).initialize(test_to_wrap=args_dict['test_to_wrap'])
        self.add_prefix_test(
                'power_BatteryCharge', {
                        'percent_target_charge':
                        args_dict['percent_target_charge'],
                        'max_run_time': 5 * HOURS,
                        'timeout': 6 * HOURS
                })
        self.add_prefix_test('power_WaitForCoolDown', {'target_temp': 48})
