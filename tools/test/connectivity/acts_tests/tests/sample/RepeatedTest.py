#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import statistics

from acts.base_test import BaseTestClass
from acts.test_decorators import repeated_test
from acts import signals


def get_median_current(test_results, _):
    """Returns the median current, or a failure if the test failed."""
    # If the last run was not a pass signal, the test exceeded
    # acceptable_failures.
    if not isinstance(test_results[-1], signals.TestPass):
        return test_results[-1]

    # Only look at results within the good range (i.e., passing results).
    valid_results = filter(lambda result: isinstance(result, signals.TestPass),
                           test_results)

    # Gather the current measurements and return the median.
    median_current = statistics.median(
        map(lambda result: result.extras['current'], valid_results))
    return signals.TestPass('Pass msg! Current: %s' % median_current,
                            extras={'current': median_current})


class RepeatedTest(BaseTestClass):

    @repeated_test(num_passes=3, acceptable_failures=0)
    def test_repeated_case(self, _):
        self.log.info('This logic executes three times.')

    @repeated_test(num_passes=3, acceptable_failures=2,
                   result_selector=get_median_current)
    def test_repeated_case_pass(self, attempt_number):
        """The end result of this test is a pass with current=3.5"""
        returned_results = [
            signals.TestPass('0Pass msg!', extras={'current': 3.5}),
            signals.TestFailure('Fail msg!', extras={'current': 100.0}),
            signals.TestPass('1Pass msg!', extras={'current': 3.2}),
            signals.TestPass('2Pass msg!', extras={'current': 3.6})
        ]
        raise returned_results[attempt_number - 1]

    @repeated_test(num_passes=3, acceptable_failures=2,
                   result_selector=get_median_current)
    def test_repeated_case_with_failures(self, attempt_number):
        """The end result of this test is the last failure to occur."""
        returned_results = [
            signals.TestPass('Pass msg!', extras={'current': 3.5}),
            signals.TestFailure('Fail msg!', extras={'current': 100.0}),
            signals.TestFailure('Fail msg!', extras={'current': 58.1}),
            signals.TestFailure('Fail msg!', extras={'current': 74.2}),
        ]
        raise returned_results[(attempt_number - 1) % 4]
