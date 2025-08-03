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
SMOKE_DIR = os.path.dirname(os.path.realpath(__file__))
TITLE = "--test-mapping"


def test_TEST_MAPPING_in_CWD(exec):
    """Test the atest default behaviour: find TEST_MAPPING in cwd.

    Args:
        exec: the atest command.

    The test result will be like running:
    $ cd prebuilts/asuite/atest/smoke_test_data/test_mappings
    $ atest
    """
    print(inspect.currentframe().f_code.co_name)
    os.chdir(os.path.join(SMOKE_DIR, 'test_mappings', 'A'))
    expected_rc = 0
    actual_rc = subprocess.call(exec)
    error_msg = 'Failed testing TEST_MAPPING in {}'.format(os.getcwd())
    if not utils.is_passed(expected_rc, actual_rc, '', error_msg):
        utils.init_test_mapping_files(action='delete')
        sys.exit(1)


def test_TEST_MAPPING_in_path(exec):
    """Test finding TEST_MAPPING in a specific path.

    Args:
        exec: the atest command.

    The test result will be like running:
    $ atest -p prebuilts/asuite/atest/smoke_test_data/test_mappings
    """
    print(inspect.currentframe().f_code.co_name)
    dest_dir = os.path.join(SMOKE_DIR, 'test_mappings', 'A')
    args = '{} {}'.format(TITLE, dest_dir)
    cmd = exec + args.split()
    expected_rc = 0
    actual_rc = subprocess.call(cmd)
    error_msg = 'Failed testing TEST_MAPPING in path {}'.format(dest_dir)
    if not utils.is_passed(expected_rc, actual_rc, '', error_msg):
        utils.init_test_mapping_files(action='delete')
        sys.exit(1)


def test_no_TEST_MAPPING(exec):
    """Test cannot find TEST_MAPPING from cwd to ANDROID_BUILD_TOP.

    Args:
        exec: the atest command.

    The test result will be like running:
    $ cd prebuilts/asuite/atest/smoke_test_data
    $ atest
    """
    print(inspect.currentframe().f_code.co_name)
    os.chdir(SMOKE_DIR)
    expected_rc = 4
    actual_rc = subprocess.call(exec)
    error_msg = ('Mismatch exit code: The EXIT_CODE_TEST_NOT_FOUND '
                 'should be {}, but {} found'.format(actual_rc, expected_rc))
    if actual_rc != expected_rc:
        print(error_msg)
        utils.init_test_mapping_files(action='delete')
        sys.exit(1)


def main():
    utils.print_banner(TITLE)
    tests = [test_TEST_MAPPING_in_CWD,
             test_TEST_MAPPING_in_path,
             test_no_TEST_MAPPING]
    utils.init_test_mapping_files(action='create')
    for i, test in enumerate(tests):
        ATEST = ['atest'] if utils.has_devices() else ['atest', '--start-avd']
        print('\n[{}/{}] '.format(i+1, len(tests)), end='')
        tests[i](ATEST)
    utils.init_test_mapping_files(action='delete')
    utils.print_banner(TITLE, True)
