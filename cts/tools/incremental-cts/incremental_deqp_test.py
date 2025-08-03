# Lint as: python3
#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Tests for incremental_deqp."""

import incremental_deqp
import os
import unittest
from unittest.mock import MagicMock
from unittest.mock import patch

@patch('incremental_deqp.AdbHelper', MagicMock())
class IncrementalDeqpTest(unittest.TestCase):

  def setUp(self):
    testfile_dir = os.path.dirname(os.path.abspath(__file__))
    self.testdata_dir = testfile_dir +'/testdata'
    self.dependency_collector = incremental_deqp.DeqpDependencyCollector(None, None, None)
    self.parser = incremental_deqp._get_parser()

  def test_update_dependency(self):
    """Test update_dependency gets correct dEQP dependency from dump file."""
    dump_file = 'testdata/perf_dump.txt'
    deps = set()
    self.dependency_collector.update_dependency(deps, dump_file)
    self.assertEqual(len(deps),2)
    self.assertIn('file_2', deps)
    self.assertIn('file_3', deps)

  def test_check_test_log_all_test_executed(self):
    """Test check_test_log returns true if all tests are executed."""
    test_file = 'testdata/test_list.txt'
    log_file = 'testdata/log_1.qpa'
    self.assertTrue(self.dependency_collector.check_test_log(test_file, log_file))

  def test_check_test_log_test_crashed(self):
    """Test check_test_log returns false if tests are crashed."""
    test_file = 'testdata/test_list.txt'
    log_file = 'testdata/log_2.qpa'
    self.assertFalse(self.dependency_collector.check_test_log(test_file, log_file))

  def test_get_test_binary_name(self):
    """Test get_test_binary_name gets dEQP binary name based on test name."""
    self.assertEqual(self.dependency_collector.get_test_binary_name('vk-32'), 'deqp-binary')
    self.assertEqual(self.dependency_collector.get_test_binary_name('vk-64'), 'deqp-binary64')
    with self.assertRaises(incremental_deqp.TestError):
      self.dependency_collector.get_test_binary_name('test')

  def test_get_test_log_name(self):
    """Test get_test_log_name gets correct test log name based on test name."""
    self.assertEqual(self.dependency_collector.get_test_log_name('test'), 'test.qpa')

  def test_get_perf_name(self):
    """Test get_perf_name gets correct perf file name based on test name."""
    self.assertEqual(self.dependency_collector.get_test_perf_name('test'), 'test.data')

  def test_get_perf_dump_name(self):
    """Test get_perf_dump_name gets correct perf dump file name based on test name."""
    self.assertEqual(self.dependency_collector.get_perf_dump_name('test'), 'test-perf-dump.txt')

  def test_get_test_list_name(self):
    """Test get_test_list_name gets test list name based on test name."""
    self.assertEqual(self.dependency_collector.get_test_list_name('vk-32'), 'vk-master-subset.txt')
    self.assertEqual(self.dependency_collector.get_test_list_name('gles3-32'),
                     'gles3-master-subset.txt')
    with self.assertRaises(incremental_deqp.TestError):
      self.dependency_collector.get_test_list_name('test')

  def test_valid_dependency(self):
    """Test if dependency is valid."""
    self.assertTrue(incremental_deqp._is_deqp_dependency('/file/a.so'))
    self.assertFalse(incremental_deqp._is_deqp_dependency('/apex/a.so'))

  def test_build_helper_compare_build_with_device_files_true(self):
    """Test BuildHelper.compare_base_build_with_device_files returns true."""
    build_helper = incremental_deqp.BuildHelper()
    deqp_deps = ['/system/deqp_dependency_file_a.so', '/vendor/deqp_dependency_file_b.so']
    base_build_file = './testdata/base_build_target-files.zip'

    def side_effect(command):
      if 'file_a.so' in command:
        return b'placeholder\nplaceholder\n'
      if 'file_b.so' in command:
        return b'placeholder\nplaceholder\nplaceholder\n\n'

    adb = incremental_deqp.AdbHelper()
    adb.run_shell_command = MagicMock(side_effect=side_effect)
    self.assertTrue(build_helper.compare_base_build_with_device_files(
        deqp_deps, adb, base_build_file)[0])

  def test_compare_build_with_device_files_false(self):
    """Test BuildHelper.compare_base_build_with_device_files returns false."""
    deqp_deps = ['/system/deqp_dependency_file_a.so', '/vendor/deqp_dependency_file_b.so']
    build_helper = incremental_deqp.BuildHelper()
    base_build_file = './testdata/base_build_target-files.zip'
    def side_effect(command):
      if 'file_a.so' in command:
        return b'different text'
      if 'file_b.so' in command:
        return b'placeholder\nplaceholder\nplaceholder\n\n'

    adb = incremental_deqp.AdbHelper()
    adb.run_shell_command = MagicMock(side_effect=side_effect)
    self.assertFalse(build_helper.compare_base_build_with_device_files(
        deqp_deps, adb, base_build_file)[0])

  def test_build_helper_compare_build_with_current_build_true(self):
    """Test BuildHelper.compare_base_build_with_current_build returns true."""
    build_helper = incremental_deqp.BuildHelper()
    deqp_deps = ['/system/deqp_dependency_file_a.so', '/vendor/deqp_dependency_file_b.so']
    base_build_file = './testdata/base_build_target-files.zip'

    self.assertTrue(build_helper.compare_base_build_with_current_build(
        deqp_deps, base_build_file, base_build_file)[0])

  def test_build_helper_compare_build_with_current_build_false(self):
    """Test BuildHelper.compare_base_build_with_current_build returns false."""
    build_helper = incremental_deqp.BuildHelper()
    deqp_deps = ['/system/deqp_dependency_file_a.so', '/vendor/deqp_dependency_file_b.so']
    base_build_file = './testdata/base_build_target-files.zip'
    current_build_file = './testdata/current_build_target-files.zip'

    self.assertFalse(build_helper.compare_base_build_with_current_build(
        deqp_deps, current_build_file, base_build_file)[0])

  def test_build_helper_get_system_fingerprint(self):
    """Test BuildHelper gets system fingerprint."""
    build_helper = incremental_deqp.BuildHelper()
    build_file = './testdata/base_build_target-files.zip'

    self.assertEqual(('generic/aosp_cf_x86_64_phone/vsoc_x86_64:S/AOSP.MASTER/7363308:'
                      'userdebug/test-keys'), build_helper.get_system_fingerprint(build_file))


  @patch('incremental_deqp.BuildHelper', autospec=True)
  @patch('incremental_deqp._save_deqp_deps', autospec=True)
  @patch('incremental_deqp.DeqpDependencyCollector', autospec=True)
  @patch('incremental_deqp.AdbHelper', autospec=True)
  def test_local_run_generate_deps_only(self, adb_helper_mock, dependency_collector_mock,
                                        save_deps_mock, build_helper_mock):
    """Test generate_deps_only option in local_run."""
    dependency_collector_mock.return_value.get_deqp_dependency.return_value = {'a.so'}
    args = self.parser.parse_args(['--generate_deps_only'])
    incremental_deqp._local_run(args, '')
    save_deps_mock.assert_called_once_with({'a.so'}, 'dEQP-dependency.txt')
    build_helper_mock.assert_not_called()

  def test_local_run_missing_extra_deps(self):
    """Test local_run throws exception if extra_deqp_dependency.txt is missing."""
    args = self.parser.parse_args(['-t ./testdata'])
    with self.assertRaises(incremental_deqp.TestResourceError):
      incremental_deqp._local_run(args, '')

  @patch('incremental_deqp._generate_report', autospec=True)
  @patch('incremental_deqp.BuildHelper', autospec=True)
  @patch('incremental_deqp._save_deqp_deps', autospec=True)
  @patch('incremental_deqp.DeqpDependencyCollector', autospec=True)
  @patch('incremental_deqp.AdbHelper', autospec=True)
  def test_local_run_compare_build(self, adb_helper_mock, dependency_collector_mock,
                                   save_deps_mock, build_helper_mock, generate_report_mock):
    """Test local_run could compare build based on dependency."""
    dependency_collector_mock.return_value.get_deqp_dependency.return_value = {'a.so'}
    build_helper_mock.return_value.compare_base_build_with_device_files.return_value = [False, {}]
    args = self.parser.parse_args(['-b', 'base_build', '-t', self.testdata_dir])

    incremental_deqp._local_run(args, '')

    save_deps_mock.assert_called_once_with({'a.so', 'extra_a.so'}, 'dEQP-dependency.txt')
    build_helper_mock.assert_called_once_with(False)

if __name__ == '__main__':
  unittest.main()
