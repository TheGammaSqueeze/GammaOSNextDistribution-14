#!/usr/bin/env python3
#
#   Copyright 2017 - The Android Open Source Project
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

import os
import subprocess
import sys
import tempfile
import unittest


class TestResult(object):
    """
    Attributes:
        failures_future: The list of failed test cases during this test.
        output_file: The file containing the stderr/stdout for this test.
        test_suite: The unittest.TestSuite used. Useful for debugging.
        test_filename: The *_test.py file that ran in this test.
    """
    def __init__(self, test_result, output_file, test_suite, test_filename):
        self.test_result = test_result
        self.output_file = output_file
        self.test_suite = test_suite
        self.test_filename = test_filename


def run_all_unit_tests():
    suite = unittest.TestSuite()
    test_files = []
    loader = unittest.TestLoader()
    for root, _, files in os.walk(os.path.dirname(__file__)):
        for filename in files:
            if filename.endswith('_test.py'):
                test_files.append(os.path.join(root, filename))
                try:
                    suite.addTest(loader.discover(root, filename))
                except ImportError as e:
                    if 'Start directory is not importable' not in e.args[0]:
                        raise
                    message = '. Did you forget to add an __init__.py file?'
                    raise ImportError(e.args[0] + message)

    output_dir = tempfile.mkdtemp()

    results = []

    for index, test in enumerate(suite._tests):
        output_file = os.path.join(output_dir, 'test_%s.output' % index)

        test_result = subprocess.Popen([sys.executable, test_files[index]],
                                       stdout=open(output_file, 'w+'),
                                       stderr=subprocess.STDOUT)
        results.append(
            TestResult(test_result, output_file, test, test_files[index]))

    all_failures = []
    for index, result in enumerate(results):
        try:
            failures = result.test_result.wait(timeout=60)
            if failures:
                print('Failure logs for %s:' % result.test_filename,
                      file=sys.stderr)
                with open(result.output_file, 'r') as out_file:
                    print(out_file.read(), file=sys.stderr)
                all_failures.append(result.test_filename + ' (failed)')
        except subprocess.TimeoutExpired:
            all_failures.append(result.test_filename + ' (timed out)')
            print('The following test timed out: %r' % result.test_filename,
                  file=sys.stderr)
            with open(result.output_file, 'r') as out_file:
                print(out_file.read(), file=sys.stderr)

    # Prints a summary over all unit tests failed.
    if all_failures:
        print('The following tests failed:', file=sys.stderr)
        for failure in all_failures:
            print('    ', failure, file=sys.stderr)

    exit(bool(all_failures))


if __name__ == '__main__':
    run_all_unit_tests()
