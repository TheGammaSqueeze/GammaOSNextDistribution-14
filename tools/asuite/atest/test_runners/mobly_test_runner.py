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

"""Mobly test runner."""
import argparse
import dataclasses
import datetime
import logging
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import tempfile
import time
from typing import Any, Dict, List, Optional, Set

import yaml

try:
  from googleapiclient import errors, http
except ModuleNotFoundError as err:
  logging.debug('Import error due to: %s', err)

from atest import atest_configs
from atest import atest_enum
from atest import atest_utils
from atest import constants
from atest import result_reporter

from atest.logstorage import atest_gcp_utils
from atest.logstorage import logstorage_utils
from atest.metrics import metrics
from atest.test_finders import test_info
from atest.test_runners import test_runner_base


_ERROR_TEST_FILE_NOT_FOUND = (
    'Required test file %s not found. If this is your first run, please ensure '
    'that the build step is performed.'
)
_ERROR_NO_MOBLY_TEST_PKG = (
    'No Mobly test package found. Ensure that the Mobly test module is '
    'correctly configured.'
)
_ERROR_NO_TEST_SUMMARY = 'No Mobly test summary found.'
_ERROR_INVALID_TEST_SUMMARY = (
    'Invalid Mobly test summary. Make sure that it contains a final "Summary" '
    'section.'
)
_ERROR_INVALID_TESTPARAMS = (
    'Invalid testparam values. Make sure that they follow the PARAM=VALUE '
    'format.'
)

# TODO(b/287136126): Use host python once compatibility issue is resolved.
PYTHON_3_11 = 'python3.11'

FILE_REQUIREMENTS_TXT = 'requirements.txt'
FILE_SUFFIX_APK = '.apk'

CONFIG_KEY_TESTBEDS = 'TestBeds'
CONFIG_KEY_NAME = 'Name'
CONFIG_KEY_CONTROLLERS = 'Controllers'
CONFIG_KEY_TEST_PARAMS = 'TestParams'
CONFIG_KEY_FILES = 'files'
CONFIG_KEY_ANDROID_DEVICE = 'AndroidDevice'
CONFIG_KEY_MOBLY_PARAMS = 'MoblyParams'
CONFIG_KEY_LOG_PATH = 'LogPath'
LOCAL_TESTBED = 'LocalTestBed'
MOBLY_LOGS_DIR = 'mobly_logs'
CONFIG_FILE = 'mobly_config.yaml'
LATEST_DIR = 'latest'
TEST_SUMMARY_YAML = 'test_summary.yaml'

CVD_SERIAL_PATTERN = r'.+:([0-9]+)$'

SUMMARY_KEY_TYPE = 'Type'
SUMMARY_TYPE_RECORD = 'Record'
SUMMARY_KEY_TEST_CLASS = 'Test Class'
SUMMARY_KEY_TEST_NAME = 'Test Name'
SUMMARY_KEY_BEGIN_TIME = 'Begin Time'
SUMMARY_KEY_END_TIME = 'End Time'
SUMMARY_KEY_RESULT = 'Result'
SUMMARY_RESULT_PASS = 'PASS'
SUMMARY_RESULT_FAIL = 'FAIL'
SUMMARY_RESULT_SKIP = 'SKIP'
SUMMARY_RESULT_ERROR = 'ERROR'
SUMMARY_KEY_DETAILS = 'Details'
SUMMARY_KEY_STACKTRACE = 'Stacktrace'

TEST_STORAGE_PASS = 'pass'
TEST_STORAGE_FAIL = 'fail'
TEST_STORAGE_IGNORED = 'ignored'
TEST_STORAGE_ERROR = 'testError'
TEST_STORAGE_STATUS_UNSPECIFIED = 'testStatusUnspecified'

WORKUNIT_ATEST_MOBLY_RUNNER = 'ATEST_MOBLY_RUNNER'
WORKUNIT_ATEST_MOBLY_TEST_RUN = 'ATEST_MOBLY_TEST_RUN'

FILE_UPLOAD_RETRIES = 3

_MOBLY_RESULT_TO_RESULT_REPORTER_STATUS = {
    SUMMARY_RESULT_PASS: test_runner_base.PASSED_STATUS,
    SUMMARY_RESULT_FAIL: test_runner_base.FAILED_STATUS,
    SUMMARY_RESULT_SKIP: test_runner_base.IGNORED_STATUS,
    SUMMARY_RESULT_ERROR: test_runner_base.FAILED_STATUS,
}

_MOBLY_RESULT_TO_TEST_STORAGE_STATUS = {
    SUMMARY_RESULT_PASS: TEST_STORAGE_PASS,
    SUMMARY_RESULT_FAIL: TEST_STORAGE_FAIL,
    SUMMARY_RESULT_SKIP: TEST_STORAGE_IGNORED,
    SUMMARY_RESULT_ERROR: TEST_STORAGE_ERROR,
}


@dataclasses.dataclass
class MoblyTestFiles:
  """Data class representing required files for a Mobly test.

  Attributes:
      mobly_pkg: The executable Mobly test package. Main build output of
        python_test_host.
      requirements_txt: Optional file with name `requirements.txt` used to
        declare pip dependencies.
      test_apks: Files ending with `.apk`. APKs used by the test.
      misc_data: All other files contained in the test target's `data`.
  """

  mobly_pkg: str
  requirements_txt: Optional[str]
  test_apks: List[str]
  misc_data: List[str]


@dataclasses.dataclass(frozen=True)
class RerunOptions:
  """Data class representing rerun options."""

  iterations: int
  rerun_until_failure: bool
  retry_any_failure: bool


class MoblyTestRunnerError(Exception):
  """Errors encountered by the MoblyTestRunner."""


class MoblyResultUploader:
  """Uploader for Android Build test storage."""

  def __init__(self, extra_args):
    """Set up the build client."""
    self._build_client = None
    self._legacy_client = None
    self._legacy_result_id = None
    self._test_results = {}

    upload_start = time.monotonic()
    creds, self._invocation = atest_gcp_utils.do_upload_flow(extra_args)
    self._root_workunit = None
    self._current_workunit = None

    if creds:
      metrics.LocalDetectEvent(
          detect_type=atest_enum.DetectType.UPLOAD_FLOW_MS,
          result=int((time.monotonic() - upload_start) * 1000),
      )
      self._build_client = logstorage_utils.BuildClient(creds)
      self._legacy_client = logstorage_utils.BuildClient(
          creds,
          api_version=constants.STORAGE_API_VERSION_LEGACY,
          url=constants.DISCOVERY_SERVICE_LEGACY,
      )
      self._setup_root_workunit()
    else:
      logging.debug('Result upload is disabled.')

  def _setup_root_workunit(self):
    """Create and populate fields for the root workunit."""
    self._root_workunit = self._build_client.insert_work_unit(self._invocation)
    self._root_workunit['type'] = WORKUNIT_ATEST_MOBLY_RUNNER
    self._root_workunit['runCount'] = 0

  @property
  def enabled(self):
    """Returns True if the uploader is enabled."""
    return self._build_client is not None

  @property
  def invocation(self):
    """The invocation of the current run."""
    return self._invocation

  @property
  def current_workunit(self):
    """The workunit of the current iteration."""
    return self._current_workunit

  def start_new_workunit(self):
    """Create and start a new workunit for the iteration."""
    if not self.enabled:
      return
    self._current_workunit = self._build_client.insert_work_unit(
        self._invocation
    )
    self._current_workunit['type'] = WORKUNIT_ATEST_MOBLY_TEST_RUN
    self._current_workunit['parentId'] = self._root_workunit['id']

  def set_workunit_iteration_details(
      self, iteration_num: int, rerun_options: RerunOptions
  ):
    """Set iteration-related fields in the current workunit.

    Args:
        iteration_num: Index of the current iteration.
        rerun_options: Rerun options for the test.
    """
    if not self.enabled:
      return
    details = {}
    if rerun_options.retry_any_failure:
      details['childAttemptNumber'] = iteration_num
    else:
      details['childRunNumber'] = iteration_num
    self._current_workunit.update(details)

  def _finalize_workunit(self, workunit: Dict[str, Any]):
    """Finalize the specified workunit."""
    workunit['schedulerState'] = 'completed'
    logging.debug('Finalizing workunit: %s', workunit)
    self._build_client.client.workunit().update(
        resourceId=workunit['id'], body=workunit
    )
    if workunit is not self._root_workunit:
      self._root_workunit['runCount'] += 1

  def finalize_current_workunit(self):
    """Finalize the workunit for the current iteration."""
    if not self.enabled:
      return
    self._test_results.clear()
    self._finalize_workunit(self._current_workunit)
    self._current_workunit = None

  def record_test_result(self, test_result):
    """Record a test result to be uploaded."""
    test_identifier = test_result['testIdentifier']
    class_method = f'{test_identifier["testClass"]}.{test_identifier["method"]}'
    self._test_results[class_method] = test_result

  def upload_test_results(self):
    """Bulk upload all recorded test results."""
    if not (self.enabled and self._test_results):
      return
    response = (
        self._build_client.client.testresult()
        .bulkinsert(
            invocationId=self._invocation['invocationId'],
            body={'testResults': list(self._test_results.values())},
        )
        .execute()
    )
    logging.debug('Uploaded test results: %s', response)

  def _upload_single_file(
      self, path: str, base_dir: str, legacy_result_id: str
  ):
    """Upload a single test file to build storage."""
    invocation_id = self._invocation['invocationId']
    workunit_id = self._current_workunit['id']
    name = os.path.relpath(path, base_dir)
    metadata = {
        'invocationId': invocation_id,
        'workUnitId': workunit_id,
        'name': name,
    }
    logging.debug('Uploading test artifact file %s', name)
    try:
      self._build_client.client.testartifact().update(
          resourceId=name,
          invocationId=invocation_id,
          workUnitId=workunit_id,
          body=metadata,
          legacyTestResultId=legacy_result_id,
          media_body=http.MediaFileUpload(path),
      ).execute(num_retries=FILE_UPLOAD_RETRIES)
    except errors.HttpError as e:
      logging.debug('Failed to upload file %s with error: %s', name, e)

  def upload_test_artifacts(self, log_dir: str):
    """Upload test artifacts and associate them to the workunit.

    Args:
        log_dir: The directory of logs to upload.
    """
    if not self.enabled:
      return
    # Use the legacy API to insert a test result and get a test result
    # id, as it is required for test artifact upload.
    res = (
        self._legacy_client.client.testresult()
        .insert(
            buildId=self.invocation['primaryBuild']['buildId'],
            target=self.invocation['primaryBuild']['buildTarget'],
            attemptId='latest',
            body={
                'status': 'completePass',
            },
        )
        .execute()
    )

    for root, _, file_names in os.walk(log_dir):
      for file_name in file_names:
        self._upload_single_file(
            os.path.join(root, file_name), log_dir, res['id']
        )

  def finalize_invocation(self):
    """Set the root work unit and invocation as complete."""
    if not self.enabled:
      return
    self._finalize_workunit(self._root_workunit)
    self.invocation['runner'] = 'mobly'
    self.invocation['schedulerState'] = 'completed'
    logging.debug('Finalizing invocation: %s', self.invocation)
    self._build_client.update_invocation(self.invocation)
    self._build_client = None

  def add_result_link(self, reporter: result_reporter.ResultReporter):
    """Add the invocation link to the result reporter.

    Args:
        reporter: The result reporter to add to.
    """
    new_result_link = constants.RESULT_LINK % self._invocation['invocationId']
    if isinstance(reporter.test_result_link, list):
      reporter.test_result_link.append(new_result_link)
    elif isinstance(reporter.test_result_link, str):
      reporter.test_result_link = [reporter.test_result_link, new_result_link]
    else:
      reporter.test_result_link = [new_result_link]


class MoblyTestRunner(test_runner_base.TestRunnerBase):
  """Mobly test runner class."""

  NAME: str = 'MoblyTestRunner'
  # Unused placeholder value. Mobly tests will be run from Python virtualenv
  EXECUTABLE: str = '.'

  # Temporary files and directories used by the runner.
  _temppaths: List[str] = []

  def run_tests(
      self,
      test_infos: List[test_info.TestInfo],
      extra_args: Dict[str, Any],
      reporter: result_reporter.ResultReporter,
  ) -> int:
    """Runs the list of test_infos.

    Should contain code for kicking off the test runs using
    test_runner_base.run(). Results should be processed and printed
    via the reporter passed in.

    Args:
        test_infos: List of TestInfo.
        extra_args: Dict of extra args to add to test run.
        reporter: An instance of result_report.ResultReporter.

    Returns:
        0 if tests succeed, non-zero otherwise.
    """
    mobly_args = self._parse_custom_args(
        extra_args.get(constants.CUSTOM_ARGS, [])
    )

    ret_code = atest_enum.ExitCode.SUCCESS
    rerun_options = self._get_rerun_options(extra_args)

    reporter.silent = False
    uploader = MoblyResultUploader(extra_args)

    for tinfo in test_infos:
      try:
        # Pre-test setup
        test_files = self._get_test_files(tinfo)
        py_executable = self._setup_python_env(test_files.requirements_txt)
        serials = atest_configs.GLOBAL_ARGS.serial or self._get_cvd_serials()
        if constants.DISABLE_INSTALL not in extra_args:
          self._install_apks(test_files.test_apks, serials)
        mobly_config = self._generate_mobly_config(
            mobly_args, serials, test_files
        )

        # Generate command and run
        test_cases = self._get_test_cases_from_spec(tinfo)
        mobly_command = self._get_mobly_command(
            py_executable,
            test_files.mobly_pkg,
            mobly_config,
            test_cases,
            mobly_args,
        )
        ret_code |= self._run_and_handle_results(
            mobly_command, tinfo, rerun_options, mobly_args, reporter, uploader
        )
      finally:
        self._cleanup()
        if uploader.enabled:
          uploader.finalize_invocation()
          uploader.add_result_link(reporter)
    return ret_code

  def host_env_check(self) -> None:
    """Checks that host env has met requirements."""

  def get_test_runner_build_reqs(
      self, test_infos: List[test_info.TestInfo]
  ) -> Set[str]:
    """Returns a set of build targets required by the test runner."""
    build_targets = set()
    build_targets.update(test_runner_base.gather_build_targets(test_infos))
    return build_targets

  # pylint: disable=unused-argument
  def generate_run_commands(
      self,
      test_infos: List[test_info.TestInfo],
      extra_args: Dict[str, Any],
      _port: Optional[int] = None,
  ) -> List[str]:
    """Generates a list of run commands from TestInfos.

    Args:
        test_infos: A set of TestInfo instances.
        extra_args: A Dict of extra args to append.
        _port: Unused.

    Returns:
        A list of run commands to run the tests.
    """
    # TODO: to be implemented
    return []

  def _parse_custom_args(self, argv: list[str]) -> argparse.Namespace:
    """Parse custom CLI args into Mobly runner options."""
    parser = argparse.ArgumentParser(prog='atest ... --')
    parser.add_argument(
        '--config',
        help=(
            'Path to a custom Mobly testbed config. Overrides all other '
            'configuration options.'
        ),
    )
    parser.add_argument(
        '--testbed',
        help=(
            'Selects the name of the testbed to use for the test. Only '
            'use this option in conjunction with --config. Defaults to '
            '"LocalTestBed".'
        ),
    )
    parser.add_argument(
        '--testparam',
        metavar='PARAM=VALUE',
        help=(
            'A test param for Mobly, specified in the format '
            '"param=value". These values can then be accessed as '
            'TestClass.user_params in the test. This option is '
            'repeatable.'
        ),
        action='append',
    )
    return parser.parse_args(argv)

  def _get_rerun_options(self, extra_args: dict[str, Any]) -> RerunOptions:
    """Get rerun options from extra_args."""
    iters = extra_args.get(constants.ITERATIONS, 1)
    reruns = extra_args.get(constants.RERUN_UNTIL_FAILURE, 0)
    retries = extra_args.get(constants.RETRY_ANY_FAILURE, 0)
    return RerunOptions(
        max(iters, reruns, retries), bool(reruns), bool(retries)
    )

  def _get_test_files(self, tinfo: test_info.TestInfo) -> MoblyTestFiles:
    """Gets test resource files from a given TestInfo."""
    mobly_pkg = None
    requirements_txt = None
    test_apks = []
    misc_data = []
    logging.debug('Getting test resource files for %s', tinfo.test_name)
    for path in tinfo.data.get(constants.MODULE_INSTALLED):
      path_str = str(path.expanduser().absolute())
      if not path.is_file():
        raise MoblyTestRunnerError(_ERROR_TEST_FILE_NOT_FOUND % path_str)
      if path.name == tinfo.test_name:
        mobly_pkg = path_str
      elif path.name == FILE_REQUIREMENTS_TXT:
        requirements_txt = path_str
      elif path.suffix == FILE_SUFFIX_APK:
        test_apks.append(path_str)
      else:
        misc_data.append(path_str)
      logging.debug('Found test resource file %s.', path_str)
    if mobly_pkg is None:
      raise MoblyTestRunnerError(_ERROR_NO_MOBLY_TEST_PKG)
    return MoblyTestFiles(mobly_pkg, requirements_txt, test_apks, misc_data)

  def _generate_mobly_config(
      self,
      mobly_args: argparse.Namespace,
      serials: List[str],
      test_files: MoblyTestFiles,
  ) -> str:
    """Creates a Mobly YAML config given the test parameters.

    If --config is specified, use that file as the testbed config.

    If --serial is specified, the test will use those specific devices,
    otherwise it will use all ADB-connected devices.

    For each --testparam specified in custom args, the test will add the
    param as a key-value pair under the testbed config's 'TestParams'.
    Values are limited to strings.

    Test resource paths (e.g. APKs) will be added to 'files' under
    'TestParams' so they could be accessed from the test script.

    Also set the Mobly results dir to <atest_results>/mobly_logs.

    Args:
        mobly_args: Custom args for the Mobly runner.
        serials: List of device serials.
        test_files: Files used by the Mobly test.

    Returns:
        Path to the generated config.
    """
    if mobly_args.config:
      config_path = os.path.abspath(os.path.expanduser(mobly_args.config))
      logging.debug('Using existing custom Mobly config at %s', config_path)
      with open(config_path, encoding='utf-8') as f:
        config = yaml.safe_load(f)
    else:
      local_testbed = {
          CONFIG_KEY_NAME: LOCAL_TESTBED,
          CONFIG_KEY_CONTROLLERS: {
              CONFIG_KEY_ANDROID_DEVICE: serials if serials else '*',
          },
          CONFIG_KEY_TEST_PARAMS: {},
      }
      if mobly_args.testparam:
        try:
          local_testbed[CONFIG_KEY_TEST_PARAMS].update(
              dict([param.split('=', 1) for param in mobly_args.testparam])
          )
        except ValueError as e:
          raise MoblyTestRunnerError(_ERROR_INVALID_TESTPARAMS) from e
      if test_files.test_apks or test_files.misc_data:
        files = {}
        files.update({
            Path(test_apk).stem: [test_apk] for test_apk in test_files.test_apks
        })
        files.update({
            Path(misc_file).name: [misc_file]
            for misc_file in test_files.misc_data
        })
        local_testbed[CONFIG_KEY_TEST_PARAMS][CONFIG_KEY_FILES] = files
      config = {
          CONFIG_KEY_TESTBEDS: [local_testbed],
      }
    # Use ATest logs directory as the Mobly log path
    log_path = os.path.join(self.results_dir, MOBLY_LOGS_DIR)
    config[CONFIG_KEY_MOBLY_PARAMS] = {
        CONFIG_KEY_LOG_PATH: log_path,
    }
    os.makedirs(log_path)
    config_path = os.path.join(log_path, CONFIG_FILE)
    logging.debug('Generating Mobly config at %s', config_path)
    with open(config_path, 'w', encoding='utf-8') as f:
      yaml.safe_dump(config, f, indent=4)
    return config_path

  def _setup_python_env(self, requirements_txt: Optional[str]) -> Optional[str]:
    """Sets up the local Python environment.

    If a requirements_txt file exists, creates a Python virtualenv and
    install dependencies. Otherwise, run the Mobly test binary directly.

    Args:
        requirements_txt: Path to the requirements.txt file, where the PyPI
          dependencies are declared. None if no such file exists.

    Returns:
        The virtualenv executable, or None.
    """
    if requirements_txt is None:
      logging.debug(
          'No requirements.txt file found. Running Mobly test package directly.'
      )
      return None
    venv_dir = tempfile.mkdtemp(prefix='venv_')
    logging.debug('Creating virtualenv at %s.', venv_dir)
    subprocess.check_call([PYTHON_3_11, '-m', 'venv', venv_dir])
    self._temppaths.append(venv_dir)
    venv_executable = os.path.join(venv_dir, 'bin', 'python')

    # Install requirements
    logging.debug('Installing dependencies from %s.', requirements_txt)
    cmd = [venv_executable, '-m', 'pip', 'install', '-r', requirements_txt]
    subprocess.check_call(cmd)
    return venv_executable

  def _get_cvd_serials(self) -> List[str]:
    """Gets the serials of cvd devices available for the test.

    Returns:
        A list of device serials.
    """
    if not (
        atest_configs.GLOBAL_ARGS.acloud_create
        or atest_configs.GLOBAL_ARGS.start_avd
    ):
      return []
    devices = atest_utils.get_adb_devices()
    return [
        device for device in devices if re.match(CVD_SERIAL_PATTERN, device)
    ]

  def _install_apks(self, apks: List[str], serials: List[str]) -> None:
    """Installs test APKs to devices.

    This can be toggled off by omitting the --install option.

    If --serial is specified, the APK will be installed to those specific
    devices, otherwise it will install to all ADB-connected devices.

    Args:
        apks: List of APK paths.
        serials: List of device serials.
    """
    serials = serials or atest_utils.get_adb_devices()
    for apk in apks:
      for serial in serials:
        logging.debug('Installing APK %s to device %s.', apk, serial)
        subprocess.check_call(['adb', '-s', serial, 'install', '-r', '-g', apk])

  def _get_test_cases_from_spec(self, tinfo: test_info.TestInfo) -> List[str]:
    """Get the list of test cases to run from the user-specified filters.

    Syntax for test_runner tests:
      MODULE:.#TEST_CASE_1[,TEST_CASE_2,TEST_CASE_3...]
      e.g.: `atest hello-world-test:.#test_hello,test_goodbye` ->
        [test_hello, test_goodbye]

    Syntax for suite_runner tests:
      MODULE:TEST_CLASS#TEST_CASE_1[,TEST_CASE_2,TEST_CASE_3...]
      e.g.: `atest hello-world-suite:HelloWorldTest#test_hello,test_goodbye`
        -> [HelloWorldTest.test_hello, HelloWorldTest.test_goodbye]

    Args:
        tinfo: The TestInfo of the test.

    Returns: List of test cases for the Mobly command.
    """
    if not tinfo.data['filter']:
      return []
    (test_filter,) = tinfo.data['filter']
    if test_filter.methods:
      # If an actual class name is specified, assume this is a
      # suite_runner test and use 'CLASS.METHOD' for the Mobly test
      # selector.
      if test_filter.class_name.isalnum():
        return [
            '%s.%s' % (test_filter.class_name, method)
            for method in test_filter.methods
        ]
      # If the class name is a placeholder character (like '.'), assume
      # this is a test_runner test and use just 'METHOD' for the Mobly
      # test selector.
      return list(test_filter.methods)
    return [test_filter.class_name]

  def _get_mobly_command(
      self,
      py_executable: str,
      mobly_pkg: str,
      config_path: str,
      test_cases: List[str],
      mobly_args: argparse.ArgumentParser,
  ) -> List[str]:
    """Generates a single Mobly test command.

    Args:
        py_executable: Path to the Python executable.
        mobly_pkg: Path to the Mobly test package.
        config_path: Path to the Mobly config.
        test_cases: List of test cases to run.
        mobly_args: Custom args for the Mobly runner.

    Returns:
        The full Mobly test command.
    """
    command = [py_executable] if py_executable is not None else []
    command += [
        mobly_pkg,
        '-c',
        config_path,
        '--test_bed',
        mobly_args.testbed or LOCAL_TESTBED,
    ]
    if test_cases:
      command += ['--tests', *test_cases]
    return command

  # pylint: disable=broad-except
  # pylint: disable=too-many-arguments
  def _run_and_handle_results(
      self,
      mobly_command: List[str],
      tinfo: test_info.TestInfo,
      rerun_options: RerunOptions,
      mobly_args: argparse.ArgumentParser,
      reporter: result_reporter.ResultReporter,
      uploader: MoblyResultUploader,
  ) -> int:
    """Runs for the specified number of iterations and handles results.

    Args:
        mobly_command: Mobly command to run.
        tinfo: The TestInfo of the test.
        rerun_options: Rerun options for the test.
        mobly_args: Custom args for the Mobly runner.
        reporter: The ResultReporter for the test.
        uploader: The MoblyResultUploader used to store results for upload.

    Returns:
        0 if tests succeed, non-zero otherwise.
    """
    logging.debug(
        'Running Mobly test %s for %d iteration(s). '
        'rerun-until-failure: %s, retry-any-failure: %s.',
        tinfo.test_name,
        rerun_options.iterations,
        rerun_options.rerun_until_failure,
        rerun_options.retry_any_failure,
    )
    ret_code = atest_enum.ExitCode.SUCCESS
    for iteration_num in range(rerun_options.iterations):
      # Set up result reporter and uploader
      reporter.runners.clear()
      reporter.pre_test = None
      uploader.start_new_workunit()

      # Run the Mobly test command
      curr_ret_code = self._run_mobly_command(mobly_command)
      ret_code |= curr_ret_code

      # Process results from generated summary file
      latest_log_dir = os.path.join(
          self.results_dir,
          MOBLY_LOGS_DIR,
          mobly_args.testbed or LOCAL_TESTBED,
          LATEST_DIR,
      )
      summary_file = os.path.join(latest_log_dir, TEST_SUMMARY_YAML)
      test_results = self._process_test_results_from_summary(
          summary_file, tinfo, iteration_num, rerun_options.iterations, uploader
      )
      for test_result in test_results:
        reporter.process_test_result(test_result)
      reporter.set_current_iteration_summary(iteration_num)
      try:
        uploader.upload_test_results()
        uploader.upload_test_artifacts(latest_log_dir)
        uploader.set_workunit_iteration_details(iteration_num, rerun_options)
        uploader.finalize_current_workunit()
      except Exception as e:
        logging.debug('Failed to upload test results. Error: %s', e)

      # Break if run ending conditions are met
      if (rerun_options.rerun_until_failure and curr_ret_code != 0) or (
          rerun_options.retry_any_failure and curr_ret_code == 0
      ):
        break
    return ret_code

  def _run_mobly_command(self, mobly_cmd: List[str]) -> int:
    """Runs the Mobly test command.

    Args:
        mobly_cmd: Mobly command to run.

    Returns:
        Return code of the Mobly command.
    """
    proc = self.run(
        shlex.join(mobly_cmd),
        output_to_stdout=bool(atest_configs.GLOBAL_ARGS.verbose),
    )
    return self.wait_for_subprocess(proc)

  # pylint: disable=too-many-locals
  def _process_test_results_from_summary(
      self,
      summary_file: str,
      tinfo: test_info.TestInfo,
      iteration_num: int,
      total_iterations: int,
      uploader: MoblyResultUploader,
  ) -> List[test_runner_base.TestResult]:
    """Parses the Mobly summary file into test results for the ResultReporter

    as well as the MoblyResultUploader.

    Args:
        summary_file: Path to the Mobly summary file.
        tinfo: The TestInfo of the test.
        iteration_num: The index of the current iteration.
        total_iterations: The total number of iterations.
        uploader: The MoblyResultUploader used to store results for upload.
    """
    if not os.path.isfile(summary_file):
      raise MoblyTestRunnerError(_ERROR_NO_TEST_SUMMARY)

    # Find and parse 'Summary' section
    logging.debug('Processing results from summary file %s.', summary_file)
    with open(summary_file, 'r', encoding='utf-8') as f:
      summary = list(yaml.safe_load_all(f))

    # Populate test results
    reported_results = []
    records = [
        entry
        for entry in summary
        if entry[SUMMARY_KEY_TYPE] == SUMMARY_TYPE_RECORD
    ]
    for test_index, record in enumerate(records):
      # Add result for result reporter
      time_elapsed_ms = 0
      if (
          record.get(SUMMARY_KEY_END_TIME) is not None
          and record.get(SUMMARY_KEY_BEGIN_TIME) is not None
      ):
        time_elapsed_ms = (
            record[SUMMARY_KEY_END_TIME] - record[SUMMARY_KEY_BEGIN_TIME]
        )
      test_run_name = record[SUMMARY_KEY_TEST_CLASS]
      test_name = (
          f'{record[SUMMARY_KEY_TEST_CLASS]}.{record[SUMMARY_KEY_TEST_NAME]}'
      )
      if total_iterations > 1:
        test_run_name = f'{test_run_name} (#{iteration_num + 1})'
        test_name = f'{test_name} (#{iteration_num + 1})'
      reported_result = {
          'runner_name': self.NAME,
          'group_name': tinfo.test_name,
          'test_run_name': test_run_name,
          'test_name': test_name,
          'status': get_result_reporter_status_from_mobly_result(
              record[SUMMARY_KEY_RESULT]
          ),
          'details': record[SUMMARY_KEY_STACKTRACE],
          'test_count': test_index + 1,
          'group_total': len(records),
          'test_time': str(datetime.timedelta(milliseconds=time_elapsed_ms)),
          # Below values are unused
          'runner_total': None,
          'additional_info': {},
      }
      reported_results.append(test_runner_base.TestResult(**reported_result))

      # Add result for upload (if enabled)
      if uploader.enabled:
        uploaded_result = {
            'invocationId': uploader.invocation['invocationId'],
            'workUnitId': uploader.current_workunit['id'],
            'testIdentifier': {
                'module': tinfo.test_name,
                'testClass': record[SUMMARY_KEY_TEST_CLASS],
                'method': record[SUMMARY_KEY_TEST_NAME],
            },
            'testStatus': get_test_storage_status_from_mobly_result(
                record[SUMMARY_KEY_RESULT]
            ),
            'timing': {
                'creationTimestamp': record[SUMMARY_KEY_BEGIN_TIME],
                'completeTimestamp': record[SUMMARY_KEY_END_TIME],
            },
        }
        if record[SUMMARY_KEY_RESULT] != SUMMARY_RESULT_PASS:
          uploaded_result['debugInfo'] = {
              'errorMessage': record[SUMMARY_KEY_DETAILS],
              'trace': record[SUMMARY_KEY_STACKTRACE],
          }
        uploader.record_test_result(uploaded_result)

    return reported_results

  def _cleanup(self) -> None:
    """Cleans up temporary host files/directories."""
    logging.debug('Cleaning up temporary dirs/files.')
    for temppath in self._temppaths:
      if os.path.isdir(temppath):
        shutil.rmtree(temppath)
      else:
        os.remove(temppath)
    self._temppaths.clear()


def get_result_reporter_status_from_mobly_result(result: str):
  """Maps Mobly result to a ResultReporter status."""
  return _MOBLY_RESULT_TO_RESULT_REPORTER_STATUS.get(
      result, test_runner_base.ERROR_STATUS
  )


def get_test_storage_status_from_mobly_result(result: str):
  """Maps Mobly result to a test storage status."""
  return _MOBLY_RESULT_TO_TEST_STORAGE_STATUS.get(
      result, TEST_STORAGE_STATUS_UNSPECIFIED
  )
