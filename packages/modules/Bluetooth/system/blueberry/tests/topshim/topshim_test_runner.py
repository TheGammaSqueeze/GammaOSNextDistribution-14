#!/usr/bin/env python3
#
#   Copyright 2022 - The Android Open Source Project
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

from blueberry.tests.topshim.adapter.adapter_test import AdapterTest
from blueberry.tests.topshim.hfp.hfp_test import HfpTest
from blueberry.tests.topshim.power.suspend_test import SuspendTest
from blueberry.tests.topshim.security.classic_security_test import ClassicSecurityTest
from blueberry.tests.topshim.security.le_security_test import LeSecurityTest

from mobly import suite_runner
import argparse

ALL_TESTS = [AdapterTest, ClassicSecurityTest, HfpTest, LeSecurityTest, SuspendTest]


def main():
    """
    Local test runner that allows  to specify list of tests to and customize
    test config file location
    """
    parser = argparse.ArgumentParser(description="Run local Topshim to Topshim tests.")
    parser.add_argument('-c',
                        '--config',
                        type=str,
                        required=True,
                        metavar='<PATH>',
                        help='Path to the test configuration file.')
    parser.add_argument('--tests',
                        '--test_case',
                        nargs='+',
                        type=str,
                        metavar='[ClassA[.test_a] ClassB[.test_b] ...]',
                        help='A list of test classes and optional tests to execute.')
    parser.add_argument("--all_tests", "-A", type=bool, dest="all_tests", default=False, nargs="?")
    parser.add_argument("--presubmit", type=bool, dest="presubmit", default=False, nargs="?")
    parser.add_argument("--postsubmit", type=bool, dest="postsubmit", default=False, nargs="?")
    args = parser.parse_args()
    test_list = ALL_TESTS
    if args.all_tests:
        test_list = ALL_TESTS
    elif args.presubmit:
        test_list = ALL_TESTS
    elif args.postsubmit:
        test_list = ALL_TESTS
    # Do not pass this layer's cmd line argument to next layer
    argv = ["--config", args.config]
    if args.tests:
        argv.append("--tests")
        for test in args.tests:
            argv.append(test)

    suite_runner.run_suite(test_list, argv=argv)


if __name__ == "__main__":
    main()
