# Copyright 2017, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Aggregates test runners, groups tests by test runners and kicks off tests."""

# pylint: disable=import-outside-toplevel

from __future__ import annotations

import itertools
from typing import Any, Dict, List

from atest import atest_error
from atest import bazel_mode
from atest import module_info
from atest.test_finders import test_info
from atest.test_runner_invocation import TestRunnerInvocation
from atest.test_runners import atest_tf_test_runner
from atest.test_runners import mobly_test_runner
from atest.test_runners import robolectric_test_runner
from atest.test_runners import suite_plan_test_runner
from atest.test_runners import test_runner_base
from atest.test_runners import vts_tf_test_runner

_TEST_RUNNERS = {
    atest_tf_test_runner.AtestTradefedTestRunner.NAME: (
        atest_tf_test_runner.AtestTradefedTestRunner
    ),
    mobly_test_runner.MoblyTestRunner.NAME: mobly_test_runner.MoblyTestRunner,
    robolectric_test_runner.RobolectricTestRunner.NAME: (
        robolectric_test_runner.RobolectricTestRunner
    ),
    suite_plan_test_runner.SuitePlanTestRunner.NAME: (
        suite_plan_test_runner.SuitePlanTestRunner
    ),
    vts_tf_test_runner.VtsTradefedTestRunner.NAME: (
        vts_tf_test_runner.VtsTradefedTestRunner
    ),
    bazel_mode.BazelTestRunner.NAME: bazel_mode.BazelTestRunner,
}


def _get_test_runners():
  """Returns the test runners.

  If external test runners are defined outside atest, they can be try-except
  imported into here.

  Returns:
      Dict of test runner name to test runner class.
  """
  test_runners_dict = _TEST_RUNNERS
  # Example import of example test runner:
  try:
    from test_runners import example_test_runner

    test_runners_dict[example_test_runner.ExampleTestRunner.NAME] = (
        example_test_runner.ExampleTestRunner
    )
  except ImportError:
    pass
  return test_runners_dict


def group_tests_by_test_runners(test_infos):
  """Group the test_infos by test runners

  Args:
      test_infos: List of TestInfo.

  Returns:
      List of tuples (test runner, tests).
  """
  tests_by_test_runner = []
  test_runner_dict = _get_test_runners()
  key = lambda x: x.test_runner
  sorted_test_infos = sorted(list(test_infos), key=key)
  for test_runner, tests in itertools.groupby(sorted_test_infos, key):
    # groupby returns a grouper object, we want to operate on a list.
    tests = list(tests)
    test_runner_class = test_runner_dict.get(test_runner)
    if test_runner_class is None:
      raise atest_error.UnknownTestRunnerError(
          'Unknown Test Runner %s' % test_runner
      )
    tests_by_test_runner.append((test_runner_class, tests))
  return tests_by_test_runner


def create_test_runner_invocations(
    *,
    test_infos: List[test_info.TestInfo],
    results_dir: str,
    mod_info: module_info.ModuleInfo,
    extra_args: Dict[str, Any],
    minimal_build: bool,
) -> List[TestRunnerInvocation]:
  """Creates TestRunnerInvocation instances.

  Args:
      test_infos: A list of instances of TestInfo.
      results_dir: A directory which stores the ATest execution information.
      mod_info: An instance of ModuleInfo.
      extra_args: A dict of arguments for the test runner to utilize.
      minimal_build: A boolean setting whether or not this invocation will
        minimize the build target set.

  Returns:
      A list of TestRunnerInvocation instances.
  """

  test_runner_invocations = []
  for test_runner_class, tests in group_tests_by_test_runners(test_infos):
    test_runner = test_runner_class(
        results_dir,
        mod_info=mod_info,
        extra_args=extra_args,
        minimal_build=minimal_build,
    )

    test_runner_invocations.extend(
        test_runner.create_invocations(extra_args=extra_args, test_infos=tests)
    )

  return test_runner_invocations
