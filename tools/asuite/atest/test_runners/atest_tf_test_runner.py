# Copyright 2017, The Android Open Source Project
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

"""Atest Tradefed test runner class."""

# pylint: disable=too-many-lines

from __future__ import annotations
from __future__ import print_function

from abc import ABC, abstractmethod
import dataclasses
import enum
from functools import partial
import json
import logging
import os
from pathlib import Path
import re
import select
import shutil
import socket
import time
from typing import Any, Dict, List, Set, Tuple

from atest import atest_configs
from atest import atest_error
from atest import atest_utils
from atest import constants
from atest import module_info
from atest import result_reporter
from atest.atest_enum import DetectType, ExitCode
from atest.coverage import coverage
from atest.logstorage import atest_gcp_utils
from atest.logstorage import logstorage_utils
from atest.metrics import metrics
from atest.test_finders import test_finder_utils
from atest.test_finders import test_info
from atest.test_runner_invocation import TestRunnerInvocation
from atest.test_runners import test_runner_base as trb
from atest.test_runners.event_handler import EventHandler

POLL_FREQ_SECS = 10
SOCKET_HOST = '127.0.0.1'
SOCKET_QUEUE_MAX = 1
SOCKET_BUFFER = 4096
SELECT_TIMEOUT = 0.5

# Socket Events of form FIRST_EVENT {JSON_DATA}\nSECOND_EVENT {JSON_DATA}
# EVENT_RE has groups for the name and the data. "." does not match \n.
EVENT_RE = re.compile(
    r'\n*(?P<event_name>[A-Z_]+) (?P<json_data>{.*})(?=\n|.)*'
)

# Remove aapt from build dependency, use prebuilt version instead.
EXEC_DEPENDENCIES = ('adb', 'fastboot')

LOG_FOLDER_NAME = 'log'

_INTEGRATION_FINDERS = frozenset(['', 'INTEGRATION', 'INTEGRATION_FILE_PATH'])

# AAPT binary name
_AAPT = 'aapt'

# The exist code mapping of tradefed.
_TF_EXIT_CODE = [
    'NO_ERROR',
    'CONFIG_EXCEPTION',
    'NO_BUILD',
    'DEVICE_UNRESPONSIVE',
    'DEVICE_UNAVAILABLE',
    'FATAL_HOST_ERROR',
    'THROWABLE_EXCEPTION',
    'NO_DEVICE_ALLOCATED',
    'WRONG_JAVA_VERSION',
]


class Error(Exception):
  """Module-level error."""


class TradeFedExitError(Error):
  """Raised when TradeFed exists before test run has finished."""

  def __init__(self, exit_code):
    super().__init__()
    self.exit_code = exit_code

  def __str__(self):
    tf_error_reason = self._get_exit_reason(self.exit_code)
    return (
        'TradeFed subprocess exited early with exit code='
        f'{self.exit_code}({tf_error_reason}).'
    )

  def _get_exit_reason(self, exit_code):
    if 0 < exit_code < len(_TF_EXIT_CODE):
      return atest_utils.mark_red(_TF_EXIT_CODE[exit_code])
    return 'Unknown exit status'


class AtestTradefedTestRunner(trb.TestRunnerBase):
  """TradeFed Test Runner class."""

  NAME = 'AtestTradefedTestRunner'
  EXECUTABLE = 'atest_tradefed.sh'
  # Common base template used by all TF tests
  _TF_LOCAL_MIN = 'template/atest_local_min'
  # Base template used by the device tests (tests requires device to run)
  _TF_DEVICE_TEST_TEMPLATE = 'template/atest_device_test_base'
  # Base template used by the deviceless tests
  _TF_DEVICELESS_TEST_TEMPLATE = 'template/atest_deviceless_test_base'
  # Use --no-enable-granular-attempts to control reporter replay behavior.
  # TODO(b/142630648): Enable option enable-granular-attempts
  # in sharding mode.
  _LOG_ARGS = (
      '--{log_root_option_name}={log_path} '
      '{log_ext_option} '
      '--no-enable-granular-attempts'
  )
  _RUN_CMD = (
      '{env} {exe} {template} '
      '--template:map test=atest '
      '--template:map log_saver={log_saver} '
      '{tf_customize_template} {log_args} {args}'
  )
  _BUILD_REQ = {'tradefed-core'}
  _RERUN_OPTION_GROUP = [
      constants.ITERATIONS,
      constants.RERUN_UNTIL_FAILURE,
      constants.RETRY_ANY_FAILURE,
  ]

  # We're using a class attribute because we're recreating runner instances
  # for different purposes throughout an invocation.
  # TODO(b/283352341): Remove this once we refactor to have runner instances.
  _MINIMAL_BUILD_TARGETS = set()

  def __init__(
      self,
      results_dir: str,
      extra_args: Dict[str, Any],
      mod_info: module_info.ModuleInfo = None,
      minimal_build: bool = False,
      **kwargs,
  ):
    """Init stuff for base class."""
    super().__init__(results_dir, **kwargs)
    self.module_info = mod_info
    self.log_path = os.path.join(results_dir, LOG_FOLDER_NAME)
    # (b/275537997) results_dir could be '' in test_runner_handler; only
    # mkdir when it is invoked by run_tests.
    if results_dir:
      Path(self.log_path).mkdir(parents=True, exist_ok=True)
    self.log_args = {
        'log_root_option_name': constants.LOG_ROOT_OPTION_NAME,
        'log_ext_option': constants.LOG_SAVER_EXT_OPTION,
        'log_path': self.log_path,
        'proto_path': os.path.join(
            self.results_dir, constants.ATEST_TEST_RECORD_PROTO
        ),
    }
    self.run_cmd_dict = {
        'env': '',
        'exe': self.EXECUTABLE,
        'template': self._TF_LOCAL_MIN,
        'log_saver': constants.ATEST_TF_LOG_SAVER,
        'tf_customize_template': '',
        'args': '',
        'log_args': self._LOG_ARGS.format(**self.log_args),
    }
    # Only set to verbose mode if the console handler is DEBUG level.
    self.is_verbose = False
    for handler in logging.getLogger('').handlers:
      if handler.name == 'console' and handler.level == logging.DEBUG:
        self.is_verbose = True
    self.root_dir = os.environ.get(constants.ANDROID_BUILD_TOP)
    self._is_host_enabled = extra_args.get(constants.HOST, False)
    self._minimal_build = minimal_build
    logging.debug('Enable minimal build: %s' % self._minimal_build)
    metrics.LocalDetectEvent(
        detect_type=DetectType.IS_MINIMAL_BUILD, result=int(self._minimal_build)
    )

  def requires_device_update(
      self, test_infos: List[test_info.TestInfo]
  ) -> bool:
    """Checks whether this runner requires device update."""

    requires_device = False
    for info in test_infos:
      test = self._create_test(info)
      requires_device |= test.requires_device()

    return requires_device

  # @typing.override
  def create_invocations(
      self,
      extra_args: Dict[str, Any],
      test_infos: List[test_info.TestInfo],
  ) -> List[TestRunnerInvocation]:
    """Create separate test runner invocations for device and deviceless tests.

    Args:
        extra_args: Dict of extra args to pass to the invocations
        test_infos: A list of TestInfos.

    Returns:
        A list of TestRunnerInvocation instances.
    """
    invocations = []
    device_test_infos, deviceless_test_infos = self._partition_tests(test_infos)
    if deviceless_test_infos:
      extra_args_for_deviceless_test = extra_args.copy()
      extra_args_for_deviceless_test.update({constants.HOST: True})
      invocations.append(
          TestRunnerInvocation(
              test_runner=self,
              extra_args=extra_args_for_deviceless_test,
              test_infos=deviceless_test_infos,
          )
      )
    if device_test_infos:
      invocations.append(
          TestRunnerInvocation(
              test_runner=self,
              extra_args=extra_args,
              test_infos=device_test_infos,
          )
      )

    return invocations

  def _partition_tests(
      self,
      test_infos: List[test_info.TestInfo],
  ) -> (List[test_info.TestInfo], List[test_info.TestInfo]):
    """Partition input tests into two lists based on whether it requires device.

    Args:
        test_infos: A list of TestInfos.

    Returns:
        Two lists one contains device tests the other contains deviceless tests.
    """
    device_test_infos = []
    deviceless_test_infos = []

    for info in test_infos:
      test = self._create_test(info)
      if test.requires_device():
        device_test_infos.append(info)
      else:
        deviceless_test_infos.append(info)

    return device_test_infos, deviceless_test_infos

  def _try_set_gts_authentication_key(self):
    """Set GTS authentication key if it is available or exists.

    Strategy:
        Get APE_API_KEY from os.environ:
            - If APE_API_KEY is already set by user -> do nothing.
        Get the APE_API_KEY from constants:
            - If the key file exists -> set to env var.
        If APE_API_KEY isn't set and the key file doesn't exist:
            - Warn user some GTS tests may fail without authentication.
    """
    if os.environ.get('APE_API_KEY'):
      logging.debug('APE_API_KEY is set by developer.')
      return
    ape_api_key = constants.GTS_GOOGLE_SERVICE_ACCOUNT
    key_path = os.path.join(self.root_dir, ape_api_key)
    if ape_api_key and os.path.exists(key_path):
      logging.debug('Set APE_API_KEY: %s', ape_api_key)
      os.environ['APE_API_KEY'] = key_path
    else:
      logging.debug(
          'APE_API_KEY not set, some GTS tests may fail without authentication.'
      )

  def run_tests(self, test_infos, extra_args, reporter):
    """Run the list of test_infos. See base class for more.

    Args:
        test_infos: A list of TestInfos.
        extra_args: Dict of extra args to add to test run.
        reporter: An instance of result_report.ResultReporter.

    Returns:
        0 if tests succeed, non-zero otherwise.
    """
    logging.debug('TF test runner running tests %s', test_infos)
    reporter.log_path = self.log_path
    reporter.rerun_options = self._extract_rerun_options(extra_args)
    # Set google service key if it's available or found before
    # running tests.
    self._try_set_gts_authentication_key()
    result = 0
    upload_start = time.time()
    creds, inv = atest_gcp_utils.do_upload_flow(extra_args)
    metrics.LocalDetectEvent(
        detect_type=DetectType.UPLOAD_FLOW_MS,
        result=int((time.time() - upload_start) * 1000),
    )
    try:
      verify_key = atest_utils.get_verify_key(
          [test_infos[0].test_name], extra_args
      )
      # Change CWD to repo root to ensure TF can find prebuilt SDKs
      # for some path-sensitive tests like robolectric.
      os.chdir(os.path.abspath(os.getenv(constants.ANDROID_BUILD_TOP)))

      # Copy symbols if there are tests belong to native test.
      self._handle_native_tests(test_infos)

      if os.getenv(trb.OLD_OUTPUT_ENV_VAR):
        result = self.run_tests_raw(test_infos, extra_args, reporter)
      else:
        result = self.run_tests_pretty(test_infos, extra_args, reporter)
    except atest_error.DryRunVerificationError as e:
      atest_utils.colorful_print(str(e), constants.RED)
      return ExitCode.VERIFY_FAILURE
    finally:
      if inv:
        try:
          logging.disable(logging.INFO)
          # Always set invocation status to completed due to the ATest
          # handle whole process by its own.
          inv['schedulerState'] = 'completed'
          logstorage_utils.BuildClient(creds).update_invocation(inv)
          reporter.test_result_link = (
              constants.RESULT_LINK % inv['invocationId']
          )
        finally:
          logging.disable(logging.NOTSET)
    return result

  def run_tests_raw(self, test_infos, extra_args, reporter):
    """Run the list of test_infos. See base class for more.

    Args:
        test_infos: A list of TestInfos.
        extra_args: Dict of extra args to add to test run.
        reporter: An instance of result_report.ResultReporter.

    Returns:
        0 if tests succeed, non-zero otherwise.
    """
    reporter.register_unsupported_runner(self.NAME)

    ret_code = ExitCode.SUCCESS
    run_cmds = self.generate_run_commands(test_infos, extra_args)
    logging.debug('Running test: %s', run_cmds[0])
    subproc = self.run(
        run_cmds[0],
        output_to_stdout=True,
        env_vars=self.generate_env_vars(extra_args),
    )
    ret_code |= self.wait_for_subprocess(subproc)
    return ret_code

  def run_tests_pretty(self, test_infos, extra_args, reporter):
    """Run the list of test_infos. See base class for more.

    Args:
        test_infos: A list of TestInfos.
        extra_args: Dict of extra args to add to test run.
        reporter: An instance of result_report.ResultReporter.

    Returns:
        0 if tests succeed, non-zero otherwise.
    """
    ret_code = ExitCode.SUCCESS
    server = self._start_socket_server()
    run_cmds = self.generate_run_commands(
        test_infos, extra_args, server.getsockname()[1]
    )
    logging.debug('Running test: %s', run_cmds[0])
    subproc = self.run(
        run_cmds[0],
        output_to_stdout=self.is_verbose,
        env_vars=self.generate_env_vars(extra_args),
    )
    self.handle_subprocess(
        subproc,
        partial(self._start_monitor, server, subproc, reporter, extra_args),
    )
    server.close()
    ret_code |= self.wait_for_subprocess(subproc)
    return ret_code

  # pylint: disable=too-many-branches
  # pylint: disable=too-many-locals
  def _start_monitor(self, server, tf_subproc, reporter, extra_args):
    """Polling and process event.

    Args:
        server: Socket server object.
        tf_subproc: The tradefed subprocess to poll.
        reporter: Result_Reporter object.
        extra_args: Dict of extra args to add to test run.
    """
    inputs = [server]
    event_handlers = {}
    data_map = {}
    inv_socket = None
    while inputs:
      try:
        readable, _, _ = select.select(inputs, [], [], SELECT_TIMEOUT)
        for socket_object in readable:
          if socket_object is server:
            conn, addr = socket_object.accept()
            logging.debug('Accepted connection from %s', addr)
            conn.setblocking(False)
            inputs.append(conn)
            data_map[conn] = ''
            # The First connection should be invocation
            # level reporter.
            if not inv_socket:
              inv_socket = conn
          else:
            # Count invocation level reporter events
            # without showing real-time information.
            if inv_socket == socket_object:
              reporter.silent = True
              event_handler = event_handlers.setdefault(
                  socket_object, EventHandler(reporter, self.NAME)
              )
            else:
              event_handler = event_handlers.setdefault(
                  socket_object,
                  EventHandler(
                      result_reporter.ResultReporter(
                          collect_only=extra_args.get(
                              constants.COLLECT_TESTS_ONLY
                          ),
                      ),
                      self.NAME,
                  ),
              )
            recv_data = self._process_connection(
                data_map, socket_object, event_handler
            )
            if not recv_data:
              inputs.remove(socket_object)
              socket_object.close()
      finally:
        # Subprocess ended and all socket clients were closed.
        if tf_subproc.poll() is not None and len(inputs) == 1:
          inputs.pop().close()
          if not reporter.all_test_results:
            if atest_configs.GLOBAL_ARGS.user_type:
              atest_utils.colorful_print(
                  "The test module doesn't support "
                  f"'{atest_configs.GLOBAL_ARGS.user_type}' "
                  'user type, please check test config.',
                  constants.RED,
              )
            atest_utils.colorful_print(
                r'No test results available. TradeFed did not find'
                r' any test cases to run. This is possibly due to'
                r' the no tests matching the current test filters'
                r' or misconfigured AndroidTest.xml. Test Logs'
                r' are saved in '
                f'{reporter.log_path}.',
                constants.RED,
                constants.WHITE,
            )
          if not data_map:
            metrics.LocalDetectEvent(
                detect_type=DetectType.TF_EXIT_CODE,
                result=tf_subproc.returncode,
            )
            raise TradeFedExitError(tf_subproc.returncode)
          self._handle_log_associations(event_handlers)

  def _process_connection(self, data_map, conn, event_handler):
    """Process a socket connection between TF and ATest.

    Expect data of form EVENT_NAME {JSON_DATA}.  Multiple events will be
    \n deliminated.  Need to buffer data in case data exceeds socket
    buffer.
    E.q.
        TEST_RUN_STARTED {runName":"hello_world_test","runAttempt":0}\n
        TEST_STARTED {"start_time":2172917, "testName":"PrintHelloWorld"}\n
    Args:
        data_map: The data map of all connections.
        conn: Socket connection.
        event_handler: EventHandler object.

    Returns:
        True if conn.recv() has data , False otherwise.
    """
    # Set connection into blocking mode.
    conn.settimeout(None)
    data = conn.recv(SOCKET_BUFFER)
    if isinstance(data, bytes):
      data = data.decode()
    logging.debug('received: %s', data)
    if data:
      data_map[conn] += data
      while True:
        match = EVENT_RE.match(data_map[conn])
        if not match:
          break
        try:
          event_data = json.loads(match.group('json_data'))
        except ValueError:
          logging.debug('Json incomplete, wait for more data')
          break
        event_name = match.group('event_name')
        event_handler.process_event(event_name, event_data)
        data_map[conn] = data_map[conn][match.end() :]
    return bool(data)

  def _start_socket_server(self):
    """Start a TCP server."""
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    # Port 0 lets the OS pick an open port between 1024 and 65535.
    server.bind((SOCKET_HOST, 0))
    server.listen(SOCKET_QUEUE_MAX)
    server.settimeout(POLL_FREQ_SECS)
    logging.debug('Socket server started on port %s', server.getsockname()[1])
    return server

  def generate_env_vars(self, extra_args):
    """Convert extra args and test_infos into env vars.

    Args:
        extra_args: Dict of extra args to add to test run.
        test_infos: A list of TestInfos.

    Returns:
        A dict modified from os.getenv.copy().
    """
    env_vars = os.environ.copy()
    if constants.TF_GLOBAL_CONFIG and is_log_upload_enabled(extra_args):
      env_vars['TF_GLOBAL_CONFIG'] = constants.TF_GLOBAL_CONFIG
    debug_port = extra_args.get(constants.TF_DEBUG, '')
    if debug_port:
      env_vars['TF_DEBUG'] = 'true'
      env_vars['TF_DEBUG_PORT'] = str(debug_port)

    filtered_paths = []
    for path in str(env_vars.get('PYTHONPATH', '')).split(':'):
      # TODO (b/166216843) Remove the hacky PYTHON path workaround.
      if (
          str(path).startswith('/tmp/Soong.python_')
          and str(path).find('googleapiclient') > 0
      ):
        continue
      filtered_paths.append(path)
    if filtered_paths:
      env_vars['PYTHONPATH'] = ':'.join(filtered_paths)

    # Use prebuilt aapt if there's no aapt under android system path which
    # is aligned with build system.
    # https://android.googlesource.com/platform/build/+/master/core/config.mk#529
    if self._is_missing_exec(_AAPT):
      prebuilt_aapt = Path.joinpath(
          atest_utils.get_prebuilt_sdk_tools_dir(), _AAPT
      )
      if os.path.exists(prebuilt_aapt):
        env_vars['PATH'] = str(prebuilt_aapt.parent) + ':' + env_vars['PATH']

    # Add an env variable for the classpath that only contains the host jars
    # required for the tests we'll be running.
    if self._minimal_build:
      self._generate_host_jars_env_var(env_vars)

    return env_vars

  def _generate_host_jars_env_var(self, env_vars):
    def is_host_jar(p):
      return p.suffix == '.jar' and p.is_relative_to(
          Path(os.getenv(constants.ANDROID_HOST_OUT))
      )

    all_host_jars = []

    for target in AtestTradefedTestRunner._MINIMAL_BUILD_TARGETS:
      if target.variant != Variant.HOST:
        continue
      # Only use the first host jar because the same jar may be installed
      # to multiple places.
      module_host_jars = [
          p
          for p in self.module_info.get_installed_paths(target.module_name)
          if is_host_jar(p)
      ]
      all_host_jars.extend(
          [str(module_host_jars[0])] if module_host_jars else []
      )

    env_vars['ATEST_HOST_JARS'] = ':'.join(set(all_host_jars))
    logging.debug(
        'Set env ATEST_HOST_JARS: %s.', env_vars.get('ATEST_HOST_JARS')
    )

  # pylint: disable=unnecessary-pass
  # Please keep above disable flag to ensure host_env_check is overridden.
  def host_env_check(self):
    """Check that host env has everything we need.

    We actually can assume the host env is fine because we have the same
    requirements that atest has. Update this to check for android env vars
    if that changes.
    """
    pass

  @staticmethod
  def _is_missing_exec(executable):
    """Check if system build executable is available.

    Args:
        executable: Executable we are checking for.

    Returns:
        True if executable is missing, False otherwise.
    """
    output = shutil.which(executable)
    if not output:
      return True
    # TODO: Check if there is a clever way to determine if system adb is
    # good enough.
    root_dir = os.environ.get(constants.ANDROID_BUILD_TOP, '')
    return os.path.commonprefix([output, root_dir]) != root_dir

  def _use_minimal_build(self, test_infos: List[test_info.TestInfo]) -> bool:

    if not self._minimal_build:
      return False

    unsupported = set()
    for t_info in test_infos:
      if t_info.test_finder in [
          'CONFIG',
          'INTEGRATION',
          'INTEGRATION_FILE_PATH',
      ]:
        unsupported.add(t_info.test_name)
      # For ltp and kselftest, keep it as no-minimal-build.
      elif t_info.test_name in (
          constants.REQUIRED_LTP_TEST_MODULES
          + constants.REQUIRED_KSELFTEST_TEST_MODULES
      ):
        unsupported.add(t_info.test_name)

    if not unsupported:
      return True

    logging.warn(
        'Minimal build was disabled because the following tests do not support'
        ' it: %s',
        unsupported,
    )
    return False

  def get_test_runner_build_reqs(
      self, test_infos: List[test_info.TestInfo]
  ) -> Set[str]:
    """Return the build requirements.

    Args:
        test_infos: List of TestInfo.

    Returns:
        Set of build targets.
    """
    if self._use_minimal_build(test_infos):
      return self._get_test_runner_reqs_minimal(test_infos)

    return self._get_test_runner_build_reqs_maximal(test_infos)

  def _get_test_runner_build_reqs_maximal(
      self, test_infos: List[test_info.TestInfo]
  ) -> Set[str]:
    build_req = self._BUILD_REQ.copy()
    # Use different base build requirements if google-tf is around.
    if self.module_info.is_module(constants.GTF_MODULE):
      build_req = {constants.GTF_TARGET}
    # Always add ATest's own TF target.
    build_req.add(constants.ATEST_TF_MODULE)
    # Add adb if we can't find it.
    for executable in EXEC_DEPENDENCIES:
      if self._is_missing_exec(executable):
        if self.module_info.is_module(executable):
          build_req.add(executable)

    # Force rebuilt all jars under $ANDROID_HOST_OUT to prevent old version
    # host jars break the test.
    build_req |= self._get_host_framework_targets()

    build_req |= trb.gather_build_targets(test_infos)
    return build_req

  def _get_test_runner_reqs_minimal(
      self, test_infos: List[test_info.TestInfo]
  ) -> Set[str]:

    build_targets = set()
    runtime_targets = set()

    for info in test_infos:
      test = self._create_test(info)
      build_targets.update(test.query_build_targets())
      runtime_targets.update(test.query_runtime_targets())

    AtestTradefedTestRunner._MINIMAL_BUILD_TARGETS = runtime_targets

    build_targets = {t.name() for t in build_targets}

    return build_targets

  def _create_test(self, t_info: test_info.TestInfo) -> Test:

    info = self.module_info.get_module_info(t_info.raw_test_name)

    if not info:
      # In cases module info does not exist (e.g. TF integration tests), use the
      # TestInfo to determine the test type. In the future we should ensure all
      # tests have their corresponding module info and only rely on the module
      # info to determine the test type.
      logging.warning(
          'Could not find module information for %s', t_info.raw_test_name
      )
      return self._guess_test_type_for_missing_module(t_info)

    def _select_variant(info):
      variants = self.module_info.build_variants(info)
      if len(variants) < 2:
        return Variant.HOST if variants[0] == 'HOST' else Variant.DEVICE
      return Variant.HOST if self._is_host_enabled else Variant.DEVICE

    if not self._is_host_enabled and self.module_info.requires_device(info):
      return DeviceTest(info, _select_variant(info), t_info.mainline_modules)

    return DevicelessTest(info, _select_variant(info))

  def _guess_test_type_for_missing_module(
      self, t_info: test_info.TestInfo
  ) -> Test:
    """Determine the test type (device or deviceless) without module info."""
    if (
        not self._is_host_enabled
        and t_info.get_supported_exec_mode() != constants.DEVICELESS_TEST
    ):
      return DeviceTest(None, Variant.DEVICE, t_info.mainline_modules)

    return DevicelessTest(None, Variant.HOST)

  def _get_host_framework_targets(self) -> Set[str]:
    """Get the build targets for all the existing jars under host framework.

    Returns:
        A set of build target name under $(ANDROID_HOST_OUT)/framework.
    """
    host_targets = set()
    if not self.module_info:
      return host_targets

    framework_host_dir = Path(
        os.environ.get(constants.ANDROID_HOST_OUT)
    ).joinpath('framework')
    if framework_host_dir.is_dir():
      jars = framework_host_dir.glob('*.jar')
      for jar in jars:
        if self.module_info.is_module(jar.stem):
          host_targets.add(jar.stem)
      logging.debug('Found exist host framework target:%s', host_targets)
    return host_targets

  def _parse_extra_args(self, test_infos, extra_args):
    """Convert the extra args into something tf can understand.

    Args:
        extra_args: Dict of args

    Returns:
        Tuple of args to append and args not supported.
    """
    args_to_append, args_not_supported = extra_args_to_tf_args(
        extra_args, self.module_info
    )

    # Set exclude instant app annotation for non-instant mode run.
    if constants.INSTANT not in extra_args and self._has_instant_app_config(
        test_infos, self.module_info
    ):
      args_to_append.append(constants.TF_TEST_ARG)
      args_to_append.append(
          '{tf_class}:{option_name}:{option_value}'.format(
              tf_class=constants.TF_AND_JUNIT_CLASS,
              option_name=constants.TF_EXCLUDE_ANNOTATE,
              option_value=constants.INSTANT_MODE_ANNOTATE,
          )
      )
    # Force append --enable-parameterized-modules if args_to_append has
    # --module-parameter in args_to_append
    if constants.TF_MODULE_PARAMETER in args_to_append:
      if constants.TF_ENABLE_PARAMETERIZED_MODULES not in args_to_append:
        args_to_append.append(constants.TF_ENABLE_PARAMETERIZED_MODULES)
    # If all the test config has config with auto enable parameter, force
    # exclude those default parameters(ex: instant_app, secondary_user)
    # TODO: (b/228433541) Remove the limitation after the root cause fixed.
    if len(test_infos) <= 1 and self._is_all_tests_parameter_auto_enabled(
        test_infos
    ):
      if constants.TF_ENABLE_PARAMETERIZED_MODULES not in args_to_append:
        args_to_append.append(constants.TF_ENABLE_PARAMETERIZED_MODULES)
        for exclude_parameter in constants.DEFAULT_EXCLUDE_PARAS:
          args_to_append.append('--exclude-module-parameters')
          args_to_append.append(exclude_parameter)
    return args_to_append, args_not_supported

  def generate_run_commands(self, test_infos, extra_args, port=None):
    """Generate a single run command from TestInfos.

    Args:
        test_infos: A list of TestInfo instances.
        extra_args: A Dict of extra args to append.
        port: Optional. An int of the port number to send events to. If None,
          then subprocess reporter in TF won't try to connect.

    Returns:
        A list that contains the string of atest tradefed run command.
        Only one command is returned.
    """
    if extra_args.get(constants.USE_TF_MIN_BASE_TEMPLATE):
      self.run_cmd_dict['template'] = self._TF_LOCAL_MIN
    else:
      self.run_cmd_dict['template'] = (
          self._TF_DEVICELESS_TEST_TEMPLATE
          if extra_args.get(constants.HOST)
          else self._TF_DEVICE_TEST_TEMPLATE
      )

    args = self._create_test_args(test_infos)

    # Create a copy of args as more args could be added to the list.
    test_args = list(args)
    if port:
      test_args.extend(['--subprocess-report-port', str(port)])
    if extra_args.get(constants.INVOCATION_ID, None):
      test_args.append(
          '--invocation-data invocation_id=%s'
          % extra_args[constants.INVOCATION_ID]
      )
    if extra_args.get(constants.WORKUNIT_ID, None):
      test_args.append(
          '--invocation-data work_unit_id=%s'
          % extra_args[constants.WORKUNIT_ID]
      )
    if extra_args.get(constants.LOCAL_BUILD_ID, None):
      # TODO: (b/207584685) Replace with TF local build solutions.
      test_args.append('--use-stub-build true')
      test_args.append(
          '--stub-build-id %s' % extra_args[constants.LOCAL_BUILD_ID]
      )
      test_args.append(
          '--stub-build-target %s' % extra_args[constants.BUILD_TARGET]
      )
    for info in test_infos:
      if atest_utils.get_test_and_mainline_modules(info.test_name):
        # TODO(b/253641058) Remove this once mainline module
        # binaries are stored under testcase directory.
        if not extra_args.get(constants.DRY_RUN):
          self._copy_mainline_module_binary(info.mainline_modules)
        test_args.append(constants.TF_ENABLE_MAINLINE_PARAMETERIZED_MODULES)
        break
    # For detailed logs, set TF options log-level/log-level-display as
    # 'VERBOSE' by default.
    log_level = 'VERBOSE'
    test_args.extend(['--log-level-display', log_level])
    test_args.extend(['--log-level', log_level])

    # Set no-early-device-release by default to speed up TF teardown time.
    # TODO(b/300882567) remove this forever when it's the default behavor.
    test_args.extend(['--no-early-device-release'])

    args_to_add, args_not_supported = self._parse_extra_args(
        test_infos, extra_args
    )

    # If multiple devices in test config, automatically append
    # --replicate-parent-setup and --multi-device-count
    device_count = atest_configs.GLOBAL_ARGS.device_count_config
    if device_count and device_count > 1:
      args_to_add.append('--replicate-parent-setup')
      args_to_add.append('--multi-device-count')
      args_to_add.append(str(device_count))
      os.environ.pop(constants.ANDROID_SERIAL, None)
    else:
      # TODO(b/122889707) Remove this after finding the root cause.
      env_serial = os.environ.get(constants.ANDROID_SERIAL)
      # Use the env variable ANDROID_SERIAL if it's set by user but only
      # when the target tests are not deviceless tests.
      if (
          env_serial
          and '--serial' not in args_to_add
          and '-n' not in args_to_add
      ):
        args_to_add.append('--serial')
        args_to_add.append(env_serial)

    test_args.extend(args_to_add)
    if args_not_supported:
      logging.info(
          '%s does not support the following args %s',
          self.EXECUTABLE,
          args_not_supported,
      )

    # Only need to check one TestInfo to determine if the tests are
    # configured in TEST_MAPPING.
    for_test_mapping = test_infos and test_infos[0].from_test_mapping
    if is_log_upload_enabled(extra_args):
      test_args.extend(atest_utils.get_result_server_args(for_test_mapping))
    self.run_cmd_dict['args'] = ' '.join(test_args)
    self.run_cmd_dict['tf_customize_template'] = (
        self._extract_customize_tf_templates(extra_args, test_infos)
    )

    # By default using ATestFileSystemLogSaver no matter what running under
    # aosp or internal branches. Only switch using google log saver if user
    # tend to upload test result to AnTS which could be detected by the
    # invocation_id in extra args.
    if is_log_upload_enabled(extra_args):
      self.use_google_log_saver()

    run_commands = [self._RUN_CMD.format(**self.run_cmd_dict)]
    logging.debug('TF test runner generated run commands %s', run_commands)
    return run_commands

  def _flatten_test_infos(self, test_infos):
    """Sort and group test_infos by module_name and sort and group filters

    by class name.

        Example of three test_infos in a set:
            Module1, {(classA, {})}
            Module1, {(classB, {Method1})}
            Module1, {(classB, {Method2}}
        Becomes a set with one element:
            Module1, {(ClassA, {}), (ClassB, {Method1, Method2})}
        Where:
              Each line is a test_info namedtuple
              {} = Frozenset
              () = TestFilter namedtuple

    Args:
        test_infos: A list of TestInfo namedtuples.

    Returns:
        A list of TestInfos flattened.
    """
    results = []
    for module, group in atest_utils.sort_and_group(
        test_infos, lambda x: x.test_name
    ):

      # module is a string, group is a generator of grouped TestInfos.
      # Module Test, so flatten test_infos:
      no_filters = False
      filters = set()
      test_runner = None
      test_finder = None
      build_targets = set()
      data = {}
      module_args = []
      for test_info_i in group:
        data.update(test_info_i.data)
        # Extend data with constants.TI_MODULE_ARG instead of
        # overwriting.
        module_args.extend(test_info_i.data.get(constants.TI_MODULE_ARG, []))
        test_runner = test_info_i.test_runner
        test_finder = test_info_i.test_finder
        build_targets |= test_info_i.build_targets
        test_filters = test_info_i.data.get(constants.TI_FILTER)
        if not test_filters or no_filters:
          # test_info wants whole module run, so hardcode no filters.
          no_filters = True
          filters = set()
          continue
        filters |= test_filters
      if module_args:
        data[constants.TI_MODULE_ARG] = module_args
      data[constants.TI_FILTER] = self.flatten_test_filters(filters)
      results.append(
          test_info.TestInfo(
              test_name=module,
              test_runner=test_runner,
              test_finder=test_finder,
              build_targets=build_targets,
              data=data,
          )
      )
    return results

  @staticmethod
  def flatten_test_filters(filters):
    """Sort and group test_filters by class_name.

        Example of three test_filters in a frozenset:
            classA, {}
            classB, {Method1}
            classB, {Method2}
        Becomes a frozenset with these elements:
            classA, {}
            classB, {Method1, Method2}
        Where:
            Each line is a TestFilter namedtuple
            {} = Frozenset

    Args:
        filters: A frozenset of test_filters.

    Returns:
        A frozenset of test_filters flattened.
    """
    results = set()
    for class_name, group in atest_utils.sort_and_group(
        filters, lambda x: x.class_name
    ):

      # class_name is a string, group is a generator of TestFilters
      assert class_name is not None
      methods = set()
      for test_filter in group:
        if not test_filter.methods:
          # Whole class should be run
          methods = set()
          break
        methods |= test_filter.methods
      results.add(test_info.TestFilter(class_name, frozenset(methods)))
    return frozenset(results)

  def _is_all_tests_parameter_auto_enabled(self, test_infos):
    """Check if all the test infos are parameter auto enabled.

    Args:
        test_infos: A set of TestInfo instances.

    Returns: True if all tests are parameter auto enabled, False otherwise.
    """
    for info in test_infos:
      if not self._is_parameter_auto_enabled_cfg(info, self.module_info):
        return False
    return True

  def _create_test_args(self, test_infos):
    """Compile TF command line args based on the given test infos.

    Args:
        test_infos: A list of TestInfo instances.

    Returns: A list of TF arguments to run the tests.
    """
    args = []
    if not test_infos:
      return []

    if atest_configs.GLOBAL_ARGS.group_test:
      test_infos = self._flatten_test_infos(test_infos)

    has_integration_test = False

    # Because current --include-filter arg will not working if ATest pass
    # both --module and --include-filter to TF, only test by --module will
    # be run. Make a check first, only use --module if all tests are all
    # parameter auto enabled.
    # Only auto-enable the parameter if there's only one test.
    # TODO: (b/228433541) Remove the limitation after the root cause fixed.
    use_module_arg = False
    if len(test_infos) <= 1:
      use_module_arg = self._is_all_tests_parameter_auto_enabled(test_infos)

    for info in test_infos:
      # Integration test exists in TF's jar, so it must have the option
      # if it's integration finder.
      if info.test_finder in _INTEGRATION_FINDERS:
        has_integration_test = True
      # For non-parameterize test module, use --include-filter, but for
      # tests which have auto enable parameterize config use --module
      # instead.
      if use_module_arg and self._is_parameter_auto_enabled_cfg(
          info, self.module_info
      ):
        args.extend([constants.TF_MODULE_FILTER, info.test_name])
      else:
        args.extend([constants.TF_INCLUDE_FILTER, info.test_name])
      for option in info.data.get(constants.TI_MODULE_ARG, []):
        if constants.TF_INCLUDE_FILTER_OPTION == option[0]:
          suite_filter = constants.TF_SUITE_FILTER_ARG_VALUE_FMT.format(
              test_name=info.test_name, option_value=option[1]
          )
          args.extend([constants.TF_INCLUDE_FILTER, suite_filter])
        elif constants.TF_EXCLUDE_FILTER_OPTION == option[0]:
          suite_filter = constants.TF_SUITE_FILTER_ARG_VALUE_FMT.format(
              test_name=info.test_name, option_value=option[1]
          )
          args.extend([constants.TF_EXCLUDE_FILTER, suite_filter])
        else:
          module_arg = constants.TF_MODULE_ARG_VALUE_FMT.format(
              test_name=info.test_name,
              option_name=option[0],
              option_value=option[1],
          )
          args.extend([constants.TF_MODULE_ARG, module_arg])

    # Add ATest include filter
    args.extend(get_include_filter(test_infos))

    # TODO (b/141090547) Pass the config path to TF to load configs.
    # Compile option in TF if finder is not INTEGRATION or not set.
    if not has_integration_test:
      args.append(constants.TF_SKIP_LOADING_CONFIG_JAR)
    return args

  def _extract_rerun_options(self, extra_args):
    """Extract rerun options to a string for output.

    Args:
        extra_args: Dict of extra args for test runners to use.

    Returns: A string of rerun options.
    """
    extracted_options = [
        '{} {}'.format(arg, extra_args[arg])
        for arg in extra_args
        if arg in self._RERUN_OPTION_GROUP
    ]
    return ' '.join(extracted_options)

  def _extract_customize_tf_templates(self, extra_args, test_infos):
    """Extract tradefed template options to a string for output.

    Args:
        extra_args: Dict of extra args for test runners to use.
        test_infos: A set of TestInfo instances.

    Returns: A string of tradefed template options.
    """
    tf_templates = extra_args.get(constants.TF_TEMPLATE, [])
    tf_template_keys = [i.split('=')[0] for i in tf_templates]
    for info in test_infos:
      if (
          info.aggregate_metrics_result
          and 'metric_post_processor' not in tf_template_keys
      ):
        template_key = 'metric_post_processor'
        template_value = 'google/template/postprocessors/metric-file-aggregate-disabled'
        tf_templates.append(f'{template_key}={template_value}')
    return ' '.join(['--template:map %s' % x for x in tf_templates])

  def _handle_log_associations(self, event_handlers):
    """Handle TF's log associations information data.

    log_association dict:
    {'loggedFile': '/tmp/serial-util11375755456514097276.ser',
     'dataName': 'device_logcat_setup_127.0.0.1:58331',
     'time': 1602038599.856113},

    Args:
        event_handlers: Dict of {socket_object:EventHandler}.
    """
    log_associations = []
    for _, event_handler in event_handlers.items():
      if event_handler.log_associations:
        log_associations += event_handler.log_associations
    device_test_end_log_time = ''
    device_teardown_log_time = ''
    for log_association in log_associations:
      if 'device_logcat_test' in log_association.get('dataName', ''):
        device_test_end_log_time = log_association.get('time')
      if 'device_logcat_teardown' in log_association.get('dataName', ''):
        device_teardown_log_time = log_association.get('time')
    if device_test_end_log_time and device_teardown_log_time:
      teardowntime = float(device_teardown_log_time) - float(
          device_test_end_log_time
      )
      logging.debug('TF logcat teardown time=%s seconds.', teardowntime)
      metrics.LocalDetectEvent(
          detect_type=DetectType.TF_TEARDOWN_LOGCAT, result=int(teardowntime)
      )

  @staticmethod
  def _has_instant_app_config(test_infos, mod_info):
    """Check if one of the input tests defined instant app mode in config.

    Args:
        test_infos: A set of TestInfo instances.
        mod_info: ModuleInfo object.

    Returns: True if one of the tests set up instant app mode.
    """
    for tinfo in test_infos:
      test_config, _ = test_finder_utils.get_test_config_and_srcs(
          tinfo, mod_info
      )
      if test_config:
        parameters = atest_utils.get_config_parameter(test_config)
        if constants.TF_PARA_INSTANT_APP in parameters:
          return True
    return False

  @staticmethod
  def _is_parameter_auto_enabled_cfg(tinfo, mod_info):
    """Check if input tests contains auto enable support parameters.

    Args:
        test_infos: A set of TestInfo instances.
        mod_info: ModuleInfo object.

    Returns: True if input test has parameter setting which is not in the
             exclude list.
    """
    test_config, _ = test_finder_utils.get_test_config_and_srcs(tinfo, mod_info)
    if test_config:
      parameters = atest_utils.get_config_parameter(test_config)
      if (
          parameters
          - constants.DEFAULT_EXCLUDE_PARAS
          - constants.DEFAULT_EXCLUDE_NOT_PARAS
      ):
        return True
    return False

  def _handle_native_tests(self, test_infos):
    """Handling some extra tasks for running native tests from tradefed.

    Args:
        test_infos: A set of TestInfo instances.
    """
    for tinfo in test_infos:
      test_config, _ = test_finder_utils.get_test_config_and_srcs(
          tinfo, self.module_info
      )
      if test_config:
        module_name, device_path = atest_utils.get_config_gtest_args(
            test_config
        )
        if module_name and device_path:
          atest_utils.copy_native_symbols(module_name, device_path)

  # TODO(b/253641058) remove copying files once mainline module
  # binaries are stored under testcase directory.
  def _copy_mainline_module_binary(self, mainline_modules):
    """Copies mainline module binaries to out/dist/mainline_modules_{arch}

    Copies the mainline module binaries to the location that
    MainlineModuleHandler in TF expects since there is no way to
    explicitly tweak the search path.

    Args:
        mainline_modules: A list of mainline modules.
    """
    config = atest_utils.get_android_config()
    arch = config.get('TARGET_ARCH')
    dest_dir = atest_utils.DIST_OUT_DIR.joinpath(f'mainline_modules_{arch}')
    dest_dir.mkdir(parents=True, exist_ok=True)

    for module in mainline_modules:
      target_module_info = self.module_info.get_module_info(module)
      installed_paths = target_module_info[constants.MODULE_INSTALLED]

      for installed_path in installed_paths:
        file_name = Path(installed_path).name
        dest_path = Path(dest_dir).joinpath(file_name)
        if dest_path.exists():
          atest_utils.colorful_print(
              'Replacing APEX in %s with %s' % (dest_path, installed_path),
              constants.CYAN,
          )
          logging.debug(
              'deleting the old file: %s and copy a new binary', dest_path
          )
          dest_path.unlink()
        shutil.copyfile(installed_path, dest_path)

        break

  def use_google_log_saver(self):
    """Replace the original log saver to google log saver."""
    self.log_args.update({
        'log_root_option_name': constants.GOOGLE_LOG_SAVER_LOG_ROOT_OPTION_NAME,
        'log_ext_option': constants.GOOGLE_LOG_SAVER_EXT_OPTION,
    })
    self.run_cmd_dict.update({
        'log_saver': constants.GOOGLE_LOG_SAVER,
        'log_args': self._LOG_ARGS.format(**self.log_args),
    })


def is_log_upload_enabled(extra_args: Dict[str, Any]) -> bool:
  """Check if input extra_args include google log saver related args.

  Args:
      extra_args: Dict of args.
  """
  return bool(extra_args.get(constants.INVOCATION_ID, None))


def generate_annotation_filter_args(
    arg_value: Any,
    mod_info: module_info.ModuleInfo,
    test_infos: List[test_info.TestInfo],
) -> List[str]:
  """Generate TF annotation filter arguments.

  Args:
      arg_value: Argument value for annotation filter.
      mod_info: ModuleInfo object.
      test_infos: A set of TestInfo instances.

  Returns:
      List of TF annotation filter arguments.
  """
  annotation_filter_args = []
  for info in test_infos:
    test_name = info.test_name
    for keyword in arg_value:
      annotation = atest_utils.get_full_annotation_class_name(
          mod_info.get_module_info(test_name), keyword
      )
      if annotation:
        module_arg = constants.TF_MODULE_ARG_VALUE_FMT.format(
            test_name=test_name,
            option_name=constants.INCLUDE_ANNOTATION,
            option_value=annotation,
        )
        annotation_filter_args.extend([constants.TF_MODULE_ARG, module_arg])
      logging.error(
          atest_utils.mark_red(f'Cannot find similar annotation: {keyword}')
      )
  return annotation_filter_args


def extra_args_to_tf_args(
    extra_args: Dict[str, Any], mod_info: module_info.ModuleInfo = None
) -> Tuple[Dict[str, Any], Dict[str, Any]]:
  """Convert the extra args into atest_tf_test_runner supported args.

  Args:
      extra_args: Dict of args
      mod_info: ModuleInfo object.

  Returns:
      Tuple of ARGS that atest_tf supported and not supported.
  """
  supported_args = []
  unsupported_args = []

  def constant_list(*value):
    return lambda *_: value

  # pylint: disable=unused-argument
  def print_message(message):
    def inner(*args):
      print(message)
      return []

    return inner

  # Mapping supported TF arguments to the processing function.
  supported_tf_args = dict({
      constants.WAIT_FOR_DEBUGGER: constant_list('--wait-for-debugger'),
      constants.DISABLE_INSTALL: constant_list('--disable-target-preparers'),
      constants.SERIAL: lambda arg_value: [
          j for d in arg_value for j in ('--serial', d)
      ],
      constants.SHARDING: lambda arg_value: ['--shard-count', str(arg_value)],
      constants.DISABLE_TEARDOWN: constant_list('--disable-teardown'),
      constants.HOST: constant_list(
          '-n', '--prioritize-host-config', '--skip-host-arch-check'
      ),
      constants.CUSTOM_ARGS:
      # custom args value is a list.
      lambda arg_value: arg_value,
      constants.ALL_ABI: constant_list('--all-abi'),
      constants.INSTANT: constant_list(
          constants.TF_ENABLE_PARAMETERIZED_MODULES,
          constants.TF_MODULE_PARAMETER,
          'instant_app',
      ),
      constants.USER_TYPE: lambda arg_value: [
          constants.TF_ENABLE_PARAMETERIZED_MODULES,
          '--enable-optional-parameterization',
          constants.TF_MODULE_PARAMETER,
          str(arg_value),
      ],
      constants.ITERATIONS: lambda arg_value: [
          '--retry-strategy',
          constants.ITERATIONS,
          '--max-testcase-run-count',
          str(arg_value),
      ],
      constants.RERUN_UNTIL_FAILURE: lambda arg_value: [
          '--retry-strategy',
          constants.RERUN_UNTIL_FAILURE,
          '--max-testcase-run-count',
          str(arg_value),
      ],
      constants.RETRY_ANY_FAILURE: lambda arg_value: [
          '--retry-strategy',
          constants.RETRY_ANY_FAILURE,
          '--max-testcase-run-count',
          str(arg_value),
      ],
      constants.COLLECT_TESTS_ONLY: constant_list('--collect-tests-only'),
      constants.TF_DEBUG: print_message('Please attach process to your IDE...'),
      constants.ANNOTATION_FILTER: generate_annotation_filter_args,
      constants.TEST_FILTER: lambda arg_value: [
          '--test-arg',
          (
              'com.android.tradefed.testtype.AndroidJUnitTest:'
              f'include-filter:{arg_value}'
          ),
          '--test-arg',
          (
              'com.android.tradefed.testtype.GTest:native-test-flag:'
              f'--gtest_filter={arg_value}'
          ),
          '--test-arg',
          (
              'com.android.tradefed.testtype.HostGTest:native-test-flag:'
              f'--gtest_filter={arg_value}'
          ),
      ],
      constants.TEST_TIMEOUT: lambda arg_value: [
          '--test-arg',
          (
              'com.android.tradefed.testtype.AndroidJUnitTest:'
              f'shell-timeout:{arg_value}'
          ),
          '--test-arg',
          (
              'com.android.tradefed.testtype.AndroidJUnitTest:'
              f'test-timeout:{arg_value}'
          ),
          '--test-arg',
          (
              'com.android.tradefed.testtype.HostGTest:'
              f'native-test-timeout:{arg_value}'
          ),
          '--test-arg',
          (
              'com.android.tradefed.testtype.GTest:'
              f'native-test-timeout:{arg_value}'
          ),
          '--test-arg',
          (
              'com.android.compatibility.testtype.LibcoreTest:'
              f'test-timeout:{arg_value}'
          ),
      ],
      constants.COVERAGE: lambda _: coverage.tf_args(mod_info),
  })

  for arg in extra_args:
    if arg in supported_tf_args:
      tf_args = supported_tf_args[arg](extra_args[arg])
      if tf_args:
        supported_args.extend(tf_args)
      continue

    if arg in (
        constants.TF_TEMPLATE,
        constants.INVOCATION_ID,
        constants.WORKUNIT_ID,
        constants.REQUEST_UPLOAD_RESULT,
        constants.DISABLE_UPLOAD_RESULT,
        constants.LOCAL_BUILD_ID,
        constants.BUILD_TARGET,
        constants.DRY_RUN,
        constants.DEVICE_ONLY,
    ):
      continue
    unsupported_args.append(arg)
  return supported_args, unsupported_args


def get_include_filter(test_infos: List[test_info.TestInfo]) -> List[str]:
  """Generate a list of tradefed filter argument from TestInfos.

  Args:
      test_infos: a List of TestInfo object.

  The include filter pattern looks like:
      --atest-include-filter <module-name>:<include-filter-value>

  Returns:
      List of Tradefed command args.
  """
  instrumentation_filters = []
  tf_args = []
  for info in test_infos:
    filters = []
    for test_info_filter in info.data.get(constants.TI_FILTER, []):
      filters.extend(test_info_filter.to_list_of_tf_strings())

    for test_filter in filters:
      filter_arg = constants.TF_ATEST_INCLUDE_FILTER_VALUE_FMT.format(
          test_name=info.test_name, test_filter=test_filter
      )
      tf_args.extend([constants.TF_ATEST_INCLUDE_FILTER, filter_arg])

  return tf_args


@enum.unique
class Variant(enum.Enum):
  """The variant of a build module."""

  NONE = ''
  HOST = 'host'
  DEVICE = 'target'

  def __init__(self, suffix):
    self._suffix = suffix

  @property
  def suffix(self) -> str:
    """The suffix without the 'dash' used to qualify build targets."""
    return self._suffix


@dataclasses.dataclass(frozen=True)
class Target:
  """A build target."""

  module_name: str
  variant: Variant

  def name(self) -> str:
    """The name to use on the command-line to build this target."""
    if not self.variant.suffix:
      return self.module_name
    return f'{self.module_name}-{self.variant.suffix}'


class Test(ABC):
  """A test that can be run."""

  _DEFAULT_HARNESS_TARGETS = frozenset(
      [
          Target('atest-tradefed', Variant.HOST),
          Target('atest_script_help.sh', Variant.HOST),
          Target('atest_tradefed.sh', Variant.HOST),
          Target('tradefed', Variant.HOST),
      ]
      + [Target(t, Variant.HOST) for t in constants.GTF_TARGETS]
  )

  def query_build_targets(self) -> Set[Target]:
    """Returns the list of build targets required to run this test."""
    build_targets = set()
    build_targets.update(self._get_harness_build_targets())
    build_targets.update(self._get_test_build_targets())
    return build_targets

  @abstractmethod
  def query_runtime_targets(self) -> Set[Target]:
    """Returns the list of targets required during runtime."""

  @abstractmethod
  def _get_test_build_targets(self) -> Set[Target]:
    """Returns the list of build targets of test and its dependencies."""

  @abstractmethod
  def _get_harness_build_targets(self) -> Set[Target]:
    """Returns the list of build targets of test harness and its dependencies."""

  @abstractmethod
  def requires_device(self) -> bool:
    """Returns true if the test requires a device, otherwise false."""


class DeviceTest(Test):
  """A device test that can be run."""

  def __init__(
      self, info: Dict[str, Any], variant: Variant, mainline_modules: Set[str]
  ):

    self._info = info
    self._variant = variant
    self._mainline_modules = mainline_modules

  def query_runtime_targets(self) -> Set[Target]:
    return self.query_build_targets() | _get_host_required_deps(self._info)

  def requires_device(self):
    return True

  def _get_test_build_targets(self) -> Set[Target]:
    module_name = self._info[constants.MODULE_INFO_ID]
    build_targets = set([Target(module_name, self._variant)])
    build_targets.update(_get_libs_deps(self._info, self._variant))
    build_targets.update(
        Target(m, Variant.NONE) for m in self._mainline_modules
    )
    return build_targets

  def _get_harness_build_targets(self):
    build_targets = set(Test._DEFAULT_HARNESS_TARGETS)
    build_targets.update(
        set([
            Target('adb', Variant.HOST),
            Target('aapt', Variant.HOST),
            Target('aapt2', Variant.HOST),
            Target('compatibility-host-util', Variant.HOST),
        ])
    )

    # Auto-generated Java tests use a module template that uses the Dalvik
    # test runner and requires the implementation jars. See
    # https://source.corp.google.com/android-internal/build/make/core/java_test_config_template.xml.
    # These dependencies should ideally be automatically added by the build
    # rule since Atest can fall out of sync otherwise.
    # TODO(b/284987354): Remove these targets once the build rule adds the
    # required deps.
    if _is_dalvik_test_module(self._info):
      build_targets.add(Target('cts-dalvik-host-test-runner', Variant.HOST))
      build_targets.add(Target('cts-dalvik-device-test-runner', Variant.DEVICE))

    if 'vts' in self._info.get(constants.MODULE_COMPATIBILITY_SUITES, []):
      # Note that we do not include `compatibility-tradefed` which is
      # already included in the VTS harness.
      build_targets.add(Target('vts-core-tradefed-harness', Variant.HOST))
    else:
      build_targets.add(Target('compatibility-tradefed', Variant.HOST))

    return build_targets


class DevicelessTest(Test):

  def __init__(self, info: Dict[str, Any], variant: Variant):
    self._info = info
    self._variant = variant

  def _get_test_build_targets(self) -> Set[Target]:
    module_name = self._info[constants.MODULE_INFO_ID]
    return set([Target(module_name, self._variant)])

  def _get_harness_build_targets(self):
    build_targets = set(Test._DEFAULT_HARNESS_TARGETS)
    build_targets.update(
        set([
            # TODO(b/277116853): Remove the adb dependency for deviceless tests.
            Target('adb', Variant.HOST),
        ])
    )
    return build_targets

  def query_runtime_targets(self) -> Set[Target]:
    return self.query_build_targets()

  def requires_device(self):
    return False


def _get_libs_deps(info: Dict[str, Any], variant: Variant) -> Set[Target]:

  # We only need the runtime dependencies with host variant since TradeFed
  # won't push any runtime dependencies to the test device and the runtime
  # dependencies with device variant should already exist on the test device.
  if variant != Variant.HOST:
    return set()

  deps = set()
  deps.update([Target(m, variant) for m in info.get(constants.MODULE_LIBS, [])])

  return deps


def _get_host_required_deps(info: Dict[str, Any]) -> Set[Target]:

  deps = set()
  deps.update(
      Target(m, Variant.HOST) for m in info.get(constants.MODULE_HOST_DEPS, [])
  )

  return deps


def _is_dalvik_test_module(info: Dict[str, Any]) -> bool:
  return 'JAVA_LIBRARIES' in info.get(
      constants.MODULE_CLASS, []
  ) and True in info.get(constants.MODULE_AUTO_TEST_CONFIG, [])
