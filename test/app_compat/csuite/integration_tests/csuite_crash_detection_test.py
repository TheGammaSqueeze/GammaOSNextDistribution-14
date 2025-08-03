# Lint as: python3
#
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
"""Tests C-Suite's crash detection behavior."""

import csuite_test_utils


class CrashDetectionTest(csuite_test_utils.TestCase):

  def setUp(self):
    super().setUp()
    self.adb = csuite_test_utils.Adb()
    self.repo = csuite_test_utils.PackageRepository()
    self.harness = csuite_test_utils.CSuiteHarness()

  def tearDown(self):
    super().tearDown()
    self.harness.cleanup()
    self.repo.cleanup()

  def test_no_crash_test_passes(self):
    test_app_package = 'android.csuite.nocrashtestapp'
    self.adb.run(['logcat', '-c'])

    completed_process = self.run_test(
        test_app_package=test_app_package,
        test_app_module='csuite_no_crash_test_app')

    self.expect_regex(completed_process.stdout, r"""PASSED\s*:\s*1""",
                      msg=str(completed_process))
    self.expect_app_launched(test_app_package, msg=str(completed_process))
    self.expect_package_not_installed(test_app_package,
                                      msg=str(completed_process))

  def test_crash_on_launch_test_fails(self):
    test_app_package = 'android.csuite.crashonlaunchtestapp'
    self.adb.run(['logcat', '-c'])

    completed_process = self.run_test(
        test_app_package=test_app_package,
        test_app_module='csuite_crash_on_launch_test_app')

    self.expect_regex(completed_process.stdout, r"""FAILED\s*:\s*1""",
                      msg=str(completed_process))
    self.expect_regex(completed_process.stdout,
                      r"""Intentional exception""",
                      msg=str(completed_process))
    self.expect_package_not_installed(test_app_package,
                                      msg=str(completed_process))

  def run_test(self, test_app_package, test_app_module):
    """Set up and run the launcher for a given test app."""

    # We don't check the return code since adb returns non-zero exit code if
    # the package does not exist.
    self.adb.uninstall(test_app_package, check=False)
    self.assert_package_not_installed(test_app_package)

    self.repo.add_package_apks(
        test_app_package, csuite_test_utils.get_test_app_apks(test_app_module))

    file_resolver_class = 'com.android.csuite.config.AppRemoteFileResolver'

    return self.harness.run_and_wait([
        '--serial',
        csuite_test_utils.get_device_serial(),
        'run',
        'commandAndExit',
        'csuite-app-launch',
        '--enable-module-dynamic-download',
        '--dynamic-download-args',
        '%s:uri-template=file://%s/{package}' %
        (file_resolver_class, self.repo.get_path()),
        '--package',
        test_app_package
    ])

  def expect_regex(self, s, regex, msg=None):
    with self.subTest():
      self.assertRegex(s, regex, msg=msg)

  def assert_package_not_installed(self, package_name, msg=None):
    self.assertNotIn(package_name, self.adb.list_packages(), msg=msg)

  def expect_package_not_installed(self, package_name, msg=None):
    with self.subTest():
      self.assert_package_not_installed(package_name, msg=msg)

  def expect_app_launched(self, tag, msg=None):
    logcat_process = self.adb.run(['logcat', '-d', '-v', 'brief', '-s', tag])
    with self.subTest():
      self.assertIn('App launched', logcat_process.stdout, msg=msg)


if __name__ == '__main__':
  csuite_test_utils.main()
