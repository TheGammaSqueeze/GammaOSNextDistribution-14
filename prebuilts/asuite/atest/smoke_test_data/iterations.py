# Copyright 2020, The Android Open Source Project
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

import inspect
import os
import subprocess
import sys

import utils

# Must assign TITLE so that smoke_tests is able to print it.
TITLE = "Iteration Runs"
ATEST = "atest"
ARGS = "-c {}={} {}"
ITERS = "--iterations"
RERUN = "--rerun-until-failure"
RETRY = "--retry-any-failure"
RUNS = 5
PASS_TEST_NAME = "atest_will_pass_tests"
FAIL_TEST_NAME = "atest_will_fail_tests"


class Iterations():
    """Class of testing --iterations, --rerun-until-failure,
    --retry-any-failure arguments."""

    def run_test(self, test_arg, expected_pass):
        """Define pass_tests/fail_tests from expect_pass.

        The test result will be like running:
        $ atest -c --retry-any-failure=5 atest_will_fail_tests

        Args:
            test_arg: A string of "--iterations", etc.
            expected_pass: A boolean of expected test result.
        """
        if expected_pass:
            args = ARGS.format(test_arg, RUNS, PASS_TEST_NAME)
            pass_tests = 3
            fail_tests = 0
        else:
            args = ARGS.format(test_arg, RUNS, FAIL_TEST_NAME)
            pass_tests = 1
            fail_tests = 2
        # Define expected_threads from the argument.
        if test_arg == ITERS:
            expected_threads = (pass_tests+fail_tests) * RUNS
        elif test_arg == RERUN:
            expected_threads = (pass_tests+fail_tests) * 1
        elif test_arg == RETRY:
            expected_threads = pass_tests + (fail_tests*RUNS)

        print('test{}'.format(test_arg).replace('-', '_'))
        cmd = '{} {}'.format(ATEST, args)
        print('Running: {}'.format(cmd))
        # 1. Check return code
        if expected_pass:
            if subprocess.call(cmd, shell=True) != 0:
                print('Failed testing:\n {}'.format(cmd))
                sys.exit(1)
        else:
            if subprocess.call(cmd, shell=True) == 0:
                print('Testing:\n{}\nis expected failure,'
                      ' but it passed.'.format(cmd))
                sys.exit(1)

        # 2. Should observe same threads in host_log.
        if not utils.is_identical(expected_threads,
                                  utils.get_test_threads(),
                                  "Test threads"):
            sys.exit(1)

        # 3. Should observe same amount of passed/failed test in end_host_log.
        if not utils.has_correct_passed_failed_counts(pass_tests, fail_tests):
            sys.exit(1)


def main():
    utils.print_banner(TITLE)
    iters = Iterations()
    # There are 3 test arguments:
    # "--iterations": runs atest_will_pass_test and expect pass.
    # "--rerun-until-failure": runs atest_will_fail_test and expect fail.
    # "--retry-any-failure": runs atest_will_fail_test and expect fail.
    test_args = [(ITERS, True), (RERUN, False), (RETRY, False)]
    for i, _ in enumerate(test_args):
        print('\n[{}/{}] '.format(i+1, len(test_args)), end='')
        utils.func_wrapper(iters.run_test, test_args[i])
    utils.print_banner(TITLE, True)
