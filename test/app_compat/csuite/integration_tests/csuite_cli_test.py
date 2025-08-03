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
"""Tests the C-Suite command line interface."""

import csuite_test_utils


class CSuiteCliTest(csuite_test_utils.TestCase):

  def test_prints_suite_name(self):
    with csuite_test_utils.CSuiteHarness() as harness:
      completed_process = harness.run_and_wait(
          ['run', 'commandAndExit', 'version'])

    self.assertEqual(0, completed_process.returncode,
                     msg=str(completed_process))
    self.assertIn('App Compatibility Test Suite', completed_process.stdout,
                  msg=str(completed_process))


if __name__ == '__main__':
  csuite_test_utils.main()
