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
TITLE = "--include-subdirs"
SMOKE_DIR = os.path.dirname(os.path.realpath(__file__))
DEST_DIR = os.path.join(SMOKE_DIR, 'test_mappings', 'A')
ATEST = ['atest']
MSG = 'Failed testing {} in {}'
EXPECTED_RC = 0
PRE = ':presubmit'
POST = ':postsubmit'
ALL = ':all'


class IncludeSubdirs():
    """Class of testing --include-subdirs argument."""

    def run_test(self, testgroup, expected_tests):
        """Method that accepts testgroup and return code to check
        if the test meets the passed criteria.

        The test result will be like running:
        $ atest --include-subdirs :presubmit /path/to/smoke/test_mappings/A

        Args:
            testgroup: A string of either ':presubmit', ':postsubmit'
                       or ':all'
            expected_tests: An integer of tests in the TEST_MAPPING files.
        """
        print('test_include_subdirs_{}'.format(testgroup).replace(':', ''))
        args = [TITLE, testgroup, DEST_DIR]
        cmd = ATEST.copy()
        cmd.extend(args)
        print('Running: {}'.format(' '.join(cmd)))

        # 1. Search :presubmit by default, return code must be 0.
        if not utils.is_passed(EXPECTED_RC,
                           subprocess.call(cmd),
                           args,
                           MSG.format(testgroup, DEST_DIR)):
            sys.exit(1)
        # 2. The actual running tests must be the same with expected.
        if not utils.is_identical(expected_tests,
                                  utils.get_test_counts(),
                                  'Test numbers'):
            sys.exit(1)


def main():
    utils.print_banner(TITLE)
    utils.init_test_mapping_files(action='create')
    tis = IncludeSubdirs()

    # There are 3 test arguments:
    # 1. ":presubmit", expect 2 tests.
    # 2. ":postsubmit", expect 3 tests.
    # 3. ":all", Since "HelloWorldATest" appears in both TEST_MAPPING files,
    #    we expect running 4 tests after merging.
    test_args = [(PRE, 2), (POST, 3), (ALL, 4)]
    for i, _ in enumerate(test_args):
        print('\n[{}/{}] '.format(i+1, len(test_args)), end='')
        utils.func_wrapper(tis.run_test, test_args[i])
    utils.init_test_mapping_files(action='delete')
    utils.print_banner(TITLE, True)
