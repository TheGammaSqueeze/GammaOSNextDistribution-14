#!/usr/bin/env python3
#
# Copyright 2022, The Android Open Source Project
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

"""
ATest Integration Test Class.

The purpose is to prevent potential side-effects from breaking ATest at the
early stage while landing CLs with potential side-effects.

It forks a subprocess with ATest commands to validate if it can pass all the
finding, running logic of the python code, and waiting for TF to exit properly.
    - When running with ROBOLECTRIC tests, it runs without TF, and will exit
    the subprocess with the message "All tests passed"
    - If FAIL, it means something breaks ATest unexpectedly!
"""

from __future__ import print_function

import os
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
import zipfile

_TEST_RUN_DIR_PREFIX = 'atest_integration_tests_%s_'
_LOG_FILE = 'integration_tests.log'
_FAILED_LINE_LIMIT = 50
_EXIT_TEST_FAILED = 1
_EXIT_MISSING_ZIP = 2


class IntegrationConstants:
    """ATest Integration Class for constants definition."""
    FAKE_SRC_ZIP = os.path.join(os.path.dirname(__file__),
                                'atest_integration_fake_src.zip')
    FAKE_SRC_ROOT = ''
    INTEGRATION_TESTS = [
        os.path.join(os.path.dirname(__file__), 'INTEGRATION_TESTS')]

    def __init__(self):
        pass


class ATestIntegrationTest(unittest.TestCase):
    """ATest Integration Test Class."""
    NAME = 'ATestIntegrationTest'
    EXECUTABLE = os.path.join(os.path.dirname(__file__), 'atest-py3')
    OPTIONS = ' -cy --no-bazel-mode '
    EXTRA_ENV = {}
    _RUN_CMD = '{exe} {options} {test}'
    _PASSED_CRITERIA = ['will be rescheduled', 'All tests passed']

    def setUp(self):
        """Set up stuff for testing."""
        self.full_env_vars = os.environ.copy()
        if self.EXTRA_ENV:
            self.full_env_vars.update(self.EXTRA_ENV)
        self.test_passed = False
        self.log = []

    def run_test(self, testcase):
        """Create a subprocess to execute the test command.

        Strategy:
            Fork a subprocess to wait for TF exit properly, and log the error
            if the exit code isn't 0.

        Args:
            testcase: A string of testcase name.
        """
        run_cmd_dict = {'exe': self.EXECUTABLE, 'options': self.OPTIONS,
                        'test': testcase}
        run_command = self._RUN_CMD.format(**run_cmd_dict)
        try:
            subprocess.check_output(run_command,
                                    cwd=self.full_env_vars['ANDROID_BUILD_TOP'],
                                    stderr=subprocess.PIPE,
                                    env=self.full_env_vars,
                                    shell=True)
        except subprocess.CalledProcessError as e:
            self.log.append(e.output.decode())
            return False
        return True

    def get_failed_log(self):
        """Get a trimmed failed log.

        Strategy:
            In order not to show the unnecessary log such as build log,
            it's better to get a trimmed failed log that contains the
            most important information.

        Returns:
            A trimmed failed log.
        """
        failed_log = '\n'.join(filter(None, self.log[-_FAILED_LINE_LIMIT:]))
        return failed_log


def create_test_method(testcase, log_path):
    """Create a test method according to the testcase.

    Args:
        testcase: A testcase name.
        log_path: A file path for storing the test result.

    Returns:
        A created test method, and a test function name.
    """
    test_function_name = 'test_%s' % testcase.replace(' ', '_')

    # pylint: disable=missing-docstring
    def template_test_method(self):
        self.test_passed = self.run_test(testcase)
        with open(log_path, 'a', encoding='utf-8') as log_file:
            log_file.write('\n'.join(self.log))
        failed_message = f'Running command: {testcase} failed.\n'
        failed_message += '' if self.test_passed else self.get_failed_log()
        self.assertTrue(self.test_passed, failed_message)
    return test_function_name, template_test_method


def create_test_run_dir():
    """Create the test run directory in tmp.

    Returns:
        A string of the directory path.
    """
    utc_epoch_time = int(time.time())
    prefix = _TEST_RUN_DIR_PREFIX % utc_epoch_time
    return tempfile.mkdtemp(prefix=prefix)


def init_test_env():
    """Initialize the environment to run the integration test."""
    # Prepare test environment.
    if not os.path.isfile(IntegrationConstants.FAKE_SRC_ZIP):
        print(f'{IntegrationConstants.FAKE_SRC_ZIP} does not exist.')
        sys.exit(_EXIT_MISSING_ZIP)

    # Extract fake src tree and make soong_ui.bash as executable.
    IntegrationConstants.FAKE_SRC_ROOT = tempfile.mkdtemp()
    if os.path.exists(IntegrationConstants.FAKE_SRC_ROOT):
        shutil.rmtree(IntegrationConstants.FAKE_SRC_ROOT)
    os.mkdir(IntegrationConstants.FAKE_SRC_ROOT)
    with zipfile.ZipFile(IntegrationConstants.FAKE_SRC_ZIP, 'r') as zip_ref:
        print(f'Extract {IntegrationConstants.FAKE_SRC_ZIP} to '
              f'{IntegrationConstants.FAKE_SRC_ROOT}')
        zip_ref.extractall(IntegrationConstants.FAKE_SRC_ROOT)
    IntegrationConstants.FAKE_SRC_ROOT = os.path.join(
        IntegrationConstants.FAKE_SRC_ROOT, 'fake_android_src')
    soong_ui = os.path.join(IntegrationConstants.FAKE_SRC_ROOT,
                            'build/soong/soong_ui.bash')
    os.chmod(soong_ui, 0o755)
    os.chdir(IntegrationConstants.FAKE_SRC_ROOT)

    # Copy atest-py3
    dst = os.path.join(IntegrationConstants.FAKE_SRC_ROOT, 'atest')
    shutil.copyfile(ATestIntegrationTest.EXECUTABLE, dst)
    os.chmod(dst, 0o755)
    ATestIntegrationTest.EXECUTABLE = dst

    # Setup env
    ATestIntegrationTest.EXTRA_ENV[
        'ANDROID_BUILD_TOP'] = IntegrationConstants.FAKE_SRC_ROOT
    ATestIntegrationTest.EXTRA_ENV['OUT'] = os.path.join(
        IntegrationConstants.FAKE_SRC_ROOT, 'out')
    ATestIntegrationTest.EXTRA_ENV[
        'ANDROID_HOST_OUT'] = os.path.join(
        IntegrationConstants.FAKE_SRC_ROOT, 'out/host')
    ATestIntegrationTest.EXTRA_ENV[
        'ANDROID_PRODUCT_OUT'] = os.path.join(
        IntegrationConstants.FAKE_SRC_ROOT, 'out/target/product/vsoc_x86_64')
    ATestIntegrationTest.EXTRA_ENV[
        'ANDROID_TARGET_OUT_TESTCASES'] = os.path.join(
        IntegrationConstants.FAKE_SRC_ROOT,
        'out/target/product/vsoc_x86_64/testcase')
    ATestIntegrationTest.EXTRA_ENV['ANDROID_SERIAL'] = ''


if __name__ == '__main__':
    # Init test
    init_test_env()

    print(f'Running tests with {ATestIntegrationTest.EXECUTABLE}\n')
    RESULT = None
    try:
        LOG_PATH = os.path.join(create_test_run_dir(), _LOG_FILE)
        for TEST_PLANS in IntegrationConstants.INTEGRATION_TESTS:
            with open(TEST_PLANS, encoding='utf-8') as test_plans:
                for test in test_plans:
                    # Skip test when the line startswith #.
                    if not test.strip() or test.strip().startswith('#'):
                        continue
                    test_func_name, test_func = create_test_method(
                        test.strip(), LOG_PATH)
                    setattr(ATestIntegrationTest, test_func_name, test_func)
        SUITE = unittest.TestLoader().loadTestsFromTestCase(
            ATestIntegrationTest)
        RESULT = unittest.TextTestRunner(verbosity=2).run(SUITE)
    finally:
        shutil.rmtree(IntegrationConstants.FAKE_SRC_ROOT)
        if RESULT.failures:
            print('Full test log is saved to %s' % LOG_PATH)
            sys.exit(_EXIT_TEST_FAILED)
        else:
            os.remove(LOG_PATH)
