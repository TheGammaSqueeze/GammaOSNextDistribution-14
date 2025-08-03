# Copyright 2023, The Android Open Source Project
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

"""Unittests for mobly_test_runner."""
# pylint: disable=protected-access
# pylint: disable=invalid-name

import argparse
import os
import pathlib
import unittest
from unittest import mock

from atest import constants
from atest import result_reporter
from atest import unittest_constants
from atest.test_finders import test_info
from atest.test_runners import mobly_test_runner
from atest.test_runners import test_runner_base


TEST_NAME = 'SampleMoblyTest'
MOBLY_PKG = 'mobly/SampleMoblyTest'
REQUIREMENTS_TXT = 'mobly/requirements.txt'
APK_1 = 'mobly/snippet1.apk'
APK_2 = 'mobly/snippet2.apk'
MISC_FILE = 'mobly/misc_file.txt'
RESULTS_DIR = 'atest_results/sample_test'
SERIAL_1 = 'serial1'
SERIAL_2 = 'serial2'
ADB_DEVICE = 'adb_device'
MOBLY_SUMMARY_FILE = os.path.join(
    unittest_constants.TEST_DATA_DIR, 'mobly', 'sample_test_summary.yaml'
)
MOCK_TEST_FILES = mobly_test_runner.MoblyTestFiles('', None, [], [])


class MoblyResultUploaderUnittests(unittest.TestCase):
  """Unit tests for MoblyResultUploader."""

  def setUp(self) -> None:
    self.patchers = [
        mock.patch(
            'atest.logstorage.atest_gcp_utils.do_upload_flow',
            return_value=('creds', {'invocationId': 'I00001'}),
        ),
        mock.patch('atest.logstorage.logstorage_utils.BuildClient'),
    ]
    for patcher in self.patchers:
      patcher.start()
    self.uploader = mobly_test_runner.MoblyResultUploader({})
    self.uploader._root_workunit = {'id': 'WU00001', 'runCount': 0}
    self.uploader._current_workunit = {'id': 'WU00010'}

  def tearDown(self) -> None:
    mock.patch.stopall()

  def test_start_new_workunit(self):
    """Tests that start_new_workunit sets correct workunit fields."""
    self.uploader._build_client.insert_work_unit.return_value = {}
    self.uploader.start_new_workunit()

    self.assertEqual(
        self.uploader.current_workunit,
        {
            'type': mobly_test_runner.WORKUNIT_ATEST_MOBLY_TEST_RUN,
            'parentId': 'WU00001',
        },
    )

  def test_set_workunit_iteration_details_with_repeats(self):
    """Tests that set_workunit_iteration_details sets the run number for

    repeated tests.
    """
    rerun_options = mobly_test_runner.RerunOptions(3, False, False)
    self.uploader.set_workunit_iteration_details(1, rerun_options)

    self.assertEqual(self.uploader.current_workunit['childRunNumber'], 1)

  def test_set_workunit_iteration_details_with_retries(self):
    """Tests that set_workunit_iteration_details sets the run number for

    retried tests.
    """
    rerun_options = mobly_test_runner.RerunOptions(3, False, True)
    self.uploader.set_workunit_iteration_details(1, rerun_options)

    self.assertEqual(self.uploader.current_workunit['childAttemptNumber'], 1)

  def test_finalize_current_workunit(self):
    """Tests that finalize_current_workunit sets correct workunit fields."""
    workunit = self.uploader.current_workunit
    self.uploader.finalize_current_workunit()

    self.assertEqual(workunit['schedulerState'], 'completed')
    self.assertEqual(self.uploader._root_workunit['runCount'], 1)
    self.assertIsNone(self.uploader.current_workunit)

  def test_finalize_invocation(self):
    """Tests that finalize_invocation sets correct fields."""
    invocation = self.uploader.invocation
    root_workunit = self.uploader._root_workunit
    self.uploader.finalize_invocation()

    self.assertEqual(root_workunit['schedulerState'], 'completed')
    self.assertEqual(root_workunit['runCount'], 0)
    self.assertEqual(invocation['runner'], 'mobly')
    self.assertEqual(invocation['schedulerState'], 'completed')
    self.assertFalse(self.uploader.enabled)

  @mock.patch('atest.constants.RESULT_LINK', 'link:%s')
  def test_add_result_link(self):
    """Tests that add_result_link correctly sets the result link."""
    reporter = result_reporter.ResultReporter()

    reporter.test_result_link = ['link:I00000']
    self.uploader.add_result_link(reporter)
    self.assertEqual(reporter.test_result_link, ['link:I00000', 'link:I00001'])

    reporter.test_result_link = 'link:I00000'
    self.uploader.add_result_link(reporter)
    self.assertEqual(reporter.test_result_link, ['link:I00000', 'link:I00001'])

    reporter.test_result_link = None
    self.uploader.add_result_link(reporter)
    self.assertEqual(reporter.test_result_link, ['link:I00001'])


class MoblyTestRunnerUnittests(unittest.TestCase):
  """Unit tests for MoblyTestRunner."""

  def setUp(self) -> None:
    self.runner = mobly_test_runner.MoblyTestRunner(RESULTS_DIR)
    self.tinfo = test_info.TestInfo(
        test_name=TEST_NAME,
        test_runner=mobly_test_runner.MoblyTestRunner.EXECUTABLE,
        build_targets=[],
    )
    self.reporter = result_reporter.ResultReporter()
    self.mobly_args = argparse.Namespace(config='', testbed='', testparam=[])

  @mock.patch.object(pathlib.Path, 'is_file')
  def test_get_test_files_all_files_present(self, is_file) -> None:
    """Tests _get_test_files with all files present."""
    is_file.return_value = True
    files = [MOBLY_PKG, REQUIREMENTS_TXT, APK_1, APK_2, MISC_FILE]
    file_paths = [pathlib.Path(f) for f in files]
    self.tinfo.data[constants.MODULE_INSTALLED] = file_paths

    test_files = self.runner._get_test_files(self.tinfo)

    self.assertTrue(test_files.mobly_pkg.endswith(MOBLY_PKG))
    self.assertTrue(test_files.requirements_txt.endswith(REQUIREMENTS_TXT))
    self.assertTrue(test_files.test_apks[0].endswith(APK_1))
    self.assertTrue(test_files.test_apks[1].endswith(APK_2))
    self.assertTrue(test_files.misc_data[0].endswith(MISC_FILE))

  @mock.patch.object(pathlib.Path, 'is_file')
  def test_get_test_files_no_mobly_pkg(self, is_file) -> None:
    """Tests _get_test_files with missing mobly_pkg."""
    is_file.return_value = True
    files = [REQUIREMENTS_TXT, APK_1, APK_2]
    self.tinfo.data[constants.MODULE_INSTALLED] = [
        pathlib.Path(f) for f in files
    ]

    with self.assertRaisesRegex(
        mobly_test_runner.MoblyTestRunnerError, 'No Mobly test package'
    ):
      self.runner._get_test_files(self.tinfo)

  @mock.patch.object(pathlib.Path, 'is_file')
  def test_get_test_files_file_not_found(self, is_file) -> None:
    """Tests _get_test_files with file not found in file system."""
    is_file.return_value = False
    files = [MOBLY_PKG, REQUIREMENTS_TXT, APK_1, APK_2]
    self.tinfo.data[constants.MODULE_INSTALLED] = [
        pathlib.Path(f) for f in files
    ]

    with self.assertRaisesRegex(
        mobly_test_runner.MoblyTestRunnerError, 'Required test file'
    ):
      self.runner._get_test_files(self.tinfo)

  @mock.patch('builtins.open')
  @mock.patch('os.makedirs')
  @mock.patch('yaml.safe_dump')
  def test_generate_mobly_config_no_serials(self, yaml_dump, *_) -> None:
    """Tests _generate_mobly_config with no serials provided."""
    self.runner._generate_mobly_config(self.mobly_args, None, MOCK_TEST_FILES)

    expected_config = {
        'TestBeds': [{
            'Name': 'LocalTestBed',
            'Controllers': {
                'AndroidDevice': '*',
            },
            'TestParams': {},
        }],
        'MoblyParams': {
            'LogPath': 'atest_results/sample_test/mobly_logs',
        },
    }
    self.assertEqual(yaml_dump.call_args.args[0], expected_config)

  @mock.patch('builtins.open')
  @mock.patch('os.makedirs')
  @mock.patch('yaml.safe_dump')
  def test_generate_mobly_config_with_serials(self, yaml_dump, *_) -> None:
    """Tests _generate_mobly_config with serials provided."""
    self.runner._generate_mobly_config(
        self.mobly_args, [SERIAL_1, SERIAL_2], MOCK_TEST_FILES
    )

    expected_config = {
        'TestBeds': [{
            'Name': 'LocalTestBed',
            'Controllers': {
                'AndroidDevice': [SERIAL_1, SERIAL_2],
            },
            'TestParams': {},
        }],
        'MoblyParams': {
            'LogPath': 'atest_results/sample_test/mobly_logs',
        },
    }
    self.assertEqual(yaml_dump.call_args.args[0], expected_config)

  @mock.patch('builtins.open')
  @mock.patch('os.makedirs')
  @mock.patch('yaml.safe_dump')
  def test_generate_mobly_config_with_testparams(self, yaml_dump, *_) -> None:
    """Tests _generate_mobly_config with custom testparams."""
    self.mobly_args.testparam = ['foo=bar']
    self.runner._generate_mobly_config(self.mobly_args, None, MOCK_TEST_FILES)

    expected_config = {
        'TestBeds': [{
            'Name': 'LocalTestBed',
            'Controllers': {
                'AndroidDevice': '*',
            },
            'TestParams': {
                'foo': 'bar',
            },
        }],
        'MoblyParams': {
            'LogPath': 'atest_results/sample_test/mobly_logs',
        },
    }
    self.assertEqual(yaml_dump.call_args.args[0], expected_config)

  def test_generate_mobly_config_with_invalid_testparams(self) -> None:
    """Tests _generate_mobly_config with invalid testparams."""
    self.mobly_args.testparam = ['foobar']
    with self.assertRaisesRegex(
        mobly_test_runner.MoblyTestRunnerError, 'Invalid testparam values'
    ):
      self.runner._generate_mobly_config(self.mobly_args, None, [])

  @mock.patch('builtins.open')
  @mock.patch('os.makedirs')
  @mock.patch('yaml.safe_dump')
  def test_generate_mobly_config_with_test_files(self, yaml_dump, *_) -> None:
    """Tests _generate_mobly_config with test files."""
    test_apks = ['files/my_app1.apk', 'files/my_app2.apk']
    misc_data = ['files/some_file.txt']
    test_files = mobly_test_runner.MoblyTestFiles('', '', test_apks, misc_data)
    self.runner._generate_mobly_config(self.mobly_args, None, test_files)

    expected_config = {
        'TestBeds': [{
            'Name': 'LocalTestBed',
            'Controllers': {
                'AndroidDevice': '*',
            },
            'TestParams': {
                'files': {
                    'my_app1': ['files/my_app1.apk'],
                    'my_app2': ['files/my_app2.apk'],
                    'some_file.txt': ['files/some_file.txt'],
                },
            },
        }],
        'MoblyParams': {
            'LogPath': 'atest_results/sample_test/mobly_logs',
        },
    }
    self.assertEqual(yaml_dump.call_args.args[0], expected_config)

  @mock.patch('atest.atest_configs.GLOBAL_ARGS.acloud_create', True)
  @mock.patch('atest.atest_utils.get_adb_devices')
  def test_get_cvd_serials(self, get_adb_devices) -> None:
    """Tests _get_cvd_serials returns correct serials."""
    devices = ['localhost:1234', '127.0.0.1:5678', 'AD12345']
    get_adb_devices.return_value = devices

    self.assertEqual(self.runner._get_cvd_serials(), devices[:2])

  @mock.patch('atest.atest_utils.get_adb_devices', return_value=[ADB_DEVICE])
  @mock.patch('subprocess.check_call')
  def test_install_apks_no_serials(self, check_call, _) -> None:
    """Tests _install_apks with no serials provided."""
    self.runner._install_apks([APK_1], None)

    expected_cmds = [['adb', '-s', ADB_DEVICE, 'install', '-r', '-g', APK_1]]
    self.assertEqual(
        [call.args[0] for call in check_call.call_args_list], expected_cmds
    )

  @mock.patch('atest.atest_utils.get_adb_devices', return_value=[ADB_DEVICE])
  @mock.patch('subprocess.check_call')
  def test_install_apks_with_serials(self, check_call, _) -> None:
    """Tests _install_apks with serials provided."""
    self.runner._install_apks([APK_1], [SERIAL_1, SERIAL_2])

    expected_cmds = [
        ['adb', '-s', SERIAL_1, 'install', '-r', '-g', APK_1],
        ['adb', '-s', SERIAL_2, 'install', '-r', '-g', APK_1],
    ]
    self.assertEqual(
        [call.args[0] for call in check_call.call_args_list], expected_cmds
    )

  def test_get_test_cases_from_spec_with_class_and_methods(self) -> None:
    """Tests _get_test_cases_from_spec with both class and methods defined."""
    self.tinfo.data = {
        'filter': frozenset({
            test_info.TestFilter(
                class_name='SampleClass', methods=frozenset({'test1', 'test2'})
            )
        })
    }

    self.assertCountEqual(
        self.runner._get_test_cases_from_spec(self.tinfo),
        ['SampleClass.test1', 'SampleClass.test2'],
    )

  def test_get_test_cases_from_spec_with_class_only(self) -> None:
    """Tests _get_test_cases_from_spec with only test class defined."""
    self.tinfo.data = {
        'filter': frozenset({
            test_info.TestFilter(class_name='SampleClass', methods=frozenset())
        })
    }

    self.assertCountEqual(
        self.runner._get_test_cases_from_spec(self.tinfo), ['SampleClass']
    )

  def test_get_test_cases_from_spec_with_method_only(self) -> None:
    """Tests _get_test_cases_from_spec with only methods defined."""
    self.tinfo.data = {
        'filter': frozenset({
            test_info.TestFilter(
                class_name='.', methods=frozenset({'test1', 'test2'})
            )
        })
    }

    self.assertCountEqual(
        self.runner._get_test_cases_from_spec(self.tinfo), ['test1', 'test2']
    )

  @mock.patch.object(
      mobly_test_runner.MoblyTestRunner,
      '_process_test_results_from_summary',
      return_value=(),
  )
  @mock.patch('atest.test_runners.mobly_test_runner.MoblyResultUploader')
  def test_run_and_handle_results_with_iterations(self, uploader, _) -> None:
    """Tests _run_and_handle_results with multiple iterations."""
    with mock.patch.object(
        mobly_test_runner.MoblyTestRunner,
        '_run_mobly_command',
        side_effect=(1, 1, 0, 0, 1),
    ) as run_mobly_command:
      runner = mobly_test_runner.MoblyTestRunner(RESULTS_DIR)
      runner._run_and_handle_results(
          [],
          self.tinfo,
          mobly_test_runner.RerunOptions(5, False, False),
          self.mobly_args,
          self.reporter,
          uploader,
      )
      self.assertEqual(run_mobly_command.call_count, 5)

  @mock.patch.object(
      mobly_test_runner.MoblyTestRunner,
      '_process_test_results_from_summary',
      return_value=(),
  )
  @mock.patch('atest.test_runners.mobly_test_runner.MoblyResultUploader')
  def test_run_and_handle_results_with_rerun_until_failure(
      self, uploader, _
  ) -> None:
    """Tests _run_and_handle_results with rerun_until_failure."""
    with mock.patch.object(
        mobly_test_runner.MoblyTestRunner,
        '_run_mobly_command',
        side_effect=(0, 0, 1, 0, 1),
    ) as run_mobly_command:
      runner = mobly_test_runner.MoblyTestRunner(RESULTS_DIR)
      runner._run_and_handle_results(
          [],
          self.tinfo,
          mobly_test_runner.RerunOptions(5, True, False),
          self.mobly_args,
          self.reporter,
          uploader,
      )
      self.assertEqual(run_mobly_command.call_count, 3)

  @mock.patch.object(
      mobly_test_runner.MoblyTestRunner,
      '_process_test_results_from_summary',
      return_value=(),
  )
  @mock.patch('atest.test_runners.mobly_test_runner.MoblyResultUploader')
  def test_run_and_handle_results_with_retry_any_failure(
      self, uploader, _
  ) -> None:
    """Tests _run_and_handle_results with retry_any_failure."""
    with mock.patch.object(
        mobly_test_runner.MoblyTestRunner,
        '_run_mobly_command',
        side_effect=(1, 1, 1, 0, 0),
    ) as run_mobly_command:
      runner = mobly_test_runner.MoblyTestRunner(RESULTS_DIR)
      runner._run_and_handle_results(
          [],
          self.tinfo,
          mobly_test_runner.RerunOptions(5, False, True),
          self.mobly_args,
          self.reporter,
          uploader,
      )
      self.assertEqual(run_mobly_command.call_count, 4)

  @mock.patch('atest.test_runners.mobly_test_runner.MoblyResultUploader')
  def test_process_test_results_from_summary_show_correct_names(
      self, uploader
  ) -> None:
    """Tests _process_results_from_summary outputs correct test names."""
    test_results = self.runner._process_test_results_from_summary(
        MOBLY_SUMMARY_FILE, self.tinfo, 0, 1, uploader
    )

    result = test_results[0]
    self.assertEqual(result.runner_name, self.runner.NAME)
    self.assertEqual(result.group_name, TEST_NAME)
    self.assertEqual(result.test_run_name, 'SampleTest')
    self.assertEqual(result.test_name, 'SampleTest.test_should_pass')

    test_results = self.runner._process_test_results_from_summary(
        MOBLY_SUMMARY_FILE, self.tinfo, 2, 3, uploader
    )

    result = test_results[0]
    self.assertEqual(result.test_run_name, 'SampleTest (#3)')
    self.assertEqual(result.test_name, 'SampleTest.test_should_pass (#3)')

  @mock.patch('atest.test_runners.mobly_test_runner.MoblyResultUploader')
  def test_process_test_results_from_summary_show_correct_status_and_details(
      self, uploader
  ) -> None:
    """Tests _process_results_from_summary outputs correct test status and

    details.
    """
    test_results = self.runner._process_test_results_from_summary(
        MOBLY_SUMMARY_FILE, self.tinfo, 0, 1, uploader
    )

    # passed case
    self.assertEqual(test_results[0].status, test_runner_base.PASSED_STATUS)
    self.assertEqual(test_results[0].details, None)
    # failed case
    self.assertEqual(test_results[1].status, test_runner_base.FAILED_STATUS)
    self.assertEqual(test_results[1].details, 'mobly.signals.TestFailure')
    # errored case
    self.assertEqual(test_results[2].status, test_runner_base.FAILED_STATUS)
    self.assertEqual(test_results[2].details, 'Exception: error')
    # skipped case
    self.assertEqual(test_results[3].status, test_runner_base.IGNORED_STATUS)
    self.assertEqual(test_results[3].details, 'mobly.signals.TestSkip')

  @mock.patch('atest.test_runners.mobly_test_runner.MoblyResultUploader')
  def test_process_test_results_from_summary_show_correct_stats(
      self, uploader
  ) -> None:
    """Tests _process_results_from_summary outputs correct stats."""
    test_results = self.runner._process_test_results_from_summary(
        MOBLY_SUMMARY_FILE, self.tinfo, 0, 1, uploader
    )

    self.assertEqual(test_results[0].test_count, 1)
    self.assertEqual(test_results[0].group_total, 4)
    self.assertEqual(test_results[0].test_time, '0:00:01')
    self.assertEqual(test_results[1].test_count, 2)
    self.assertEqual(test_results[1].group_total, 4)
    self.assertEqual(test_results[1].test_time, '0:00:00')

  @mock.patch('atest.test_runners.mobly_test_runner.MoblyResultUploader')
  def test_process_test_results_from_summary_create_correct_uploader_result(
      self, uploader
  ) -> None:
    """Tests _process_results_from_summary creates correct result for the

    uploader.
    """
    uploader.enabled = True
    uploader.invocation = {'invocationId': 'I12345'}
    uploader.current_workunit = {'id': 'WU12345'}
    self.runner._process_test_results_from_summary(
        MOBLY_SUMMARY_FILE, self.tinfo, 0, 1, uploader
    )

    expected_results = {
        'invocationId': 'I12345',
        'workUnitId': 'WU12345',
        'testIdentifier': {
            'module': TEST_NAME,
            'testClass': 'SampleTest',
            'method': 'test_should_error',
        },
        'testStatus': mobly_test_runner.TEST_STORAGE_ERROR,
        'timing': {'creationTimestamp': 1000, 'completeTimestamp': 2000},
        'debugInfo': {'errorMessage': 'error', 'trace': 'Exception: error'},
    }

    self.assertEqual(
        uploader.record_test_result.call_args_list[2].args[0], expected_results
    )


if __name__ == '__main__':
  unittest.main()
