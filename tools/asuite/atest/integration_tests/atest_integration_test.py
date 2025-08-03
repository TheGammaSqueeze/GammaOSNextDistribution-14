#!/usr/bin/env python3
#
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

"""Base test module for Atest integration tests."""
import concurrent.futures
import json
import logging
import os
import pathlib
import re
import shutil
import subprocess
import sys
import time
from typing import Any

import split_build_test_script

# Exporting for test modules' typing reference
SplitBuildTestScript = split_build_test_script.SplitBuildTestScript
StepInput = split_build_test_script.StepInput
StepOutput = split_build_test_script.StepOutput
ParallelTestRunner = split_build_test_script.ParallelTestRunner

# Note: The following constants should ideally be imported from their
#       corresponding prod source code, but this makes local execution of the
#       integration test harder due to some special dependencies in the prod
#       code. Therefore we copy the definition here for now in favor of easier
#       local integration test execution. If value changes in the source code
#       breaking the integration test becomes a problem in the future, we can
#       reconsider importing these constants.
# Printed before the html log line. Defined in atest/atest_utils.py.
_HTML_LOG_PRINT_PREFIX = 'To access logs, press "ctrl" and click on'
# Stdout print prefix for results directory. Defined in atest/atest_main.py
_RESULTS_DIR_PRINT_PREFIX = 'Atest results and logs directory: '


class LogEntry:
  """Represents a single log entry."""

  def __init__(
      self,
      timestamp_str,
      src_file_name,
      src_file_line_number,
      log_level,
      content_lines,
  ):
    """Initializes a LogEntry object from a logging line.

    Args:
        timestamp_str: The timestamp header string in each log entry.
        src_file_name: The source file name in the log entry.
        src_file_line_number: The source file line number in the log entry.
        log_level: The log level string in the log entry.
        content_lines: A list of log entry content lines.
    """
    self._timestamp_string = timestamp_str
    self._source_file_name = src_file_name
    self._source_file_line_number = src_file_line_number
    self._log_level = log_level
    self._content_lines = content_lines

  def get_timestamp(self) -> float:
    """Returns the timestamp of the log entry as an epoch time."""
    return time.mktime(
        time.strptime(self._timestamp_string, '%Y-%m-%d %H:%M:%S')
    )

  def get_timestamp_string(self) -> str:
    """Returns the timestamp of the log entry as a string."""
    return self._timestamp_string

  def get_source_file_name(self) -> str:
    """Returns the source file name of the log entry."""
    return self._source_file_name

  def get_source_file_line_number(self) -> int:
    """Returns the source file line number of the log entry."""
    return self._source_file_line_number

  def get_log_level(self) -> str:
    """Returns the log level of the log entry."""
    return self._log_level

  def get_content(self) -> str:
    """Returns the content of the log entry."""
    return '\n'.join(self._content_lines)


class AtestRunResult:
  """A class to store Atest run result and get detailed run information."""

  def __init__(
      self,
      completed_process: subprocess.CompletedProcess[str],
      env: dict[str, str],
      repo_root: str,
      config: split_build_test_script.IntegrationTestConfiguration,
  ):
    self._completed_process = completed_process
    self._env = env
    self._repo_root = repo_root
    self._config = config

  def get_returncode(self) -> int:
    """Returns the return code of the completed process."""
    return self._completed_process.returncode

  def get_stdout(self) -> str:
    """Returns the standard output of the completed process."""
    return self._completed_process.stdout

  def get_stderr(self) -> str:
    """Returns the standard error of the completed process."""
    return self._completed_process.stderr

  def get_cmd_list(self) -> list[str]:
    """Returns the command list used in the process run."""
    return self._completed_process.args

  def get_results_dir_path(self, snapshot_ready=False) -> pathlib.Path:
    """Returns the atest results directory path.

    Args:
        snapshot_ready: Whether to make the result root directory snapshot
          ready. When set to True and called in build environment, this method
          will copy the path into <repo_root>/out with dereferencing so that the
          directory can be safely added to snapshot.

    Raises:
        RuntimeError: Failed to parse the result dir path.

    Returns:
        The Atest result directory path.
    """
    results_dir = None
    for line in self.get_stdout().splitlines(keepends=False):
      if line.startswith(_RESULTS_DIR_PRINT_PREFIX):
        results_dir = pathlib.Path(line[len(_RESULTS_DIR_PRINT_PREFIX) :])
    if not results_dir:
      raise RuntimeError('Failed to parse the result directory from stdout.')

    if self._config.is_test_env or not snapshot_ready:
      return results_dir

    result_dir_copy_path = pathlib.Path(self._env['OUT_DIR']).joinpath(
        'atest_integration_tests', results_dir.name
    )
    if not result_dir_copy_path.exists():
      shutil.copytree(results_dir, result_dir_copy_path, symlinks=False)

    return result_dir_copy_path

  def get_test_result_dict(self) -> dict[str, Any]:
    """Gets the atest results loaded from the test_result json.

    Returns:
        Atest result information loaded from the test_result json file. The test
        result usually contains information about test runners and test
        pass/fail results.
    """
    json_path = self.get_results_dir_path() / 'test_result'
    with open(json_path, 'r', encoding='utf-8') as f:
      return json.load(f)

  def get_atest_log(self) -> str:
    """Gets the log content read from the atest log file."""
    log_path = self.get_results_dir_path() / 'atest.log'
    return log_path.read_text(encoding='utf-8')

  def get_atest_log_entries(self) -> list[LogEntry]:
    """Gets the parsed atest log entries list from the atest log file.

    This method parse the atest log file and construct a new entry when a line
    starts with a time string, source file name, line number, and log level.

    Returns:
      A list of parsed log entries.
    """
    entries = []
    last_content_lines = []
    for line in self.get_atest_log().splitlines():
      regex = r'^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) (.+?):(\d+):(\w+): (.*)'
      match = re.match(regex, line)
      if match:
        last_content_lines = [match.group(5)]
        entries.append(
            LogEntry(
                match.group(1),
                match.group(2),
                int(match.group(3)),
                match.group(4),
                last_content_lines,
            )
        )
      else:
        if last_content_lines:
          last_content_lines.append(line)

    return entries

  def get_atest_log_values_from_prefix(self, prefix: str) -> list[str]:
    """Gets log values from lines starting with the given log prefix."""
    res = []
    for entry in self.get_atest_log_entries():
      content = entry.get_content()
      if content.startswith(prefix):
        res.append(content[len(prefix) :])
    return res

  def check_returncode(self) -> None:
    """Checks the return code and raises an exception if non-zero."""

    def add_line_prefix(msg: str):
      return (
          ''.join(('> %s' % line for line in msg.splitlines(keepends=True)))
          if msg
          else msg
      )

    stderr = (
        f'stderr:\n{add_line_prefix(self.get_stderr())}\n'
        if self.get_stderr() and self.get_stderr().strip()
        else ''
    )

    if self.get_returncode() != 0:
      raise RuntimeError(
          f'Atest command {self.get_cmd_list()} finished with exit code'
          f' {self.get_returncode()}.\n'
          f'stdout:\n{add_line_prefix(self.get_stdout())}\n{stderr}'
      )

  def get_local_reproduce_debug_cmd(self) -> str:
    """Returns a full reproduce command for local debugging purpose.

    Returns:
        A command that can be executed directly in command line to
        reproduce the atest command.
    """
    return '(cd {dir} && {env} {cmd})'.format(
        dir=self._repo_root,
        env=' '.join((k + '=' + v for k, v in self._env.items())),
        cmd=' '.join(self.get_cmd_list()),
    )


class AtestTestCase(split_build_test_script.SplitBuildTestTestCase):
  """Base test case for build-test environment split integration tests."""

  # Default include list of repo paths for snapshot
  _default_snapshot_include_paths = [
      '$OUT_DIR/host/linux-x86',
      '$OUT_DIR/target/product/*/module-info*',
      '$OUT_DIR/target/product/*/testcases',
      '$OUT_DIR/target/product/*/data',
      '$OUT_DIR/target/product/*/all_modules.txt',
      '$OUT_DIR/soong/module_bp*',
      'tools/asuite/atest/test_runners/roboleaf_launched.txt',
      '.repo/manifest.xml',
      'build/soong/soong_ui.bash',
      'build/bazel_common_rules/rules/python/stubs',
      'build/bazel/bin',
      'external/bazelbuild-rules_java',
      'tools/asuite/atest/bazel/resources/bazel.sh',
      'prebuilts/bazel/linux-x86_64',
      'prebuilts/build-tools/path/linux-x86/python3',
      'prebuilts/build-tools/linux-x86/bin/py3-cmd',
      'prebuilts/build-tools',
      'prebuilts/asuite/atest/linux-x86/atest-py3',
  ]

  # Default exclude list of repo paths for snapshot
  _default_snapshot_exclude_paths = [
      '$OUT_DIR/host/linux-x86/bin/go',
      '$OUT_DIR/host/linux-x86/bin/soong_build',
      '$OUT_DIR/host/linux-x86/obj',
  ]

  # Default list of environment variables to take and restore in snapshots
  _default_snapshot_env_keys = [
      split_build_test_script.ANDROID_BUILD_TOP_KEY,
      'ANDROID_HOST_OUT',
      'ANDROID_PRODUCT_OUT',
      'ANDROID_HOST_OUT_TESTCASES',
      'ANDROID_TARGET_OUT_TESTCASES',
      'OUT',
      'OUT_DIR',
      'PATH',
      'HOST_OUT_TESTCASES',
      'ANDROID_JAVA_HOME',
      'JAVA_HOME',
  ]

  def create_atest_script(self, name: str = None) -> SplitBuildTestScript:
    """Create an instance of atest integration test utility."""
    script = self.create_split_build_test_script(name)
    script.add_snapshot_restore_exclude_paths(
        ['$OUT_DIR/atest_bazel_workspace']
    )
    return script

  def create_step_output(self) -> StepOutput:
    """Create a step output object with default values."""
    out = StepOutput()
    out.add_snapshot_include_paths(self._default_snapshot_include_paths)
    out.add_snapshot_exclude_paths(self._default_snapshot_exclude_paths)
    out.add_snapshot_env_keys(self._default_snapshot_env_keys)
    out.add_snapshot_include_paths(self._get_jdk_path_list())
    return out

  def run_atest_command(
      self,
      cmd: str,
      step_in: split_build_test_script.StepInput,
      print_output: bool = True,
      use_prebuilt_atest_binary=None,
  ) -> AtestRunResult:
    """Run either `atest-dev` or `atest` command through subprocess.

    Args:
        cmd: command string for Atest. Do not add 'atest-dev' or 'atest' in the
          beginning of the command.
        step_in: The step input object from build or test step.
        print_output: Whether to print the stdout and stderr while the command
          is running.
        use_prebuilt_atest_binary: Whether to run the command using the prebuilt
          atest binary instead of the atest-dev binary.

    Returns:
        An AtestRunResult object containing the run information.
    """
    if use_prebuilt_atest_binary is None:
      use_prebuilt_atest_binary = step_in.get_config().use_prebuilt_atest_binary
    complete_cmd = (
        f'{"atest" if use_prebuilt_atest_binary else "atest-dev"}'
        f' {cmd}{step_in.get_device_serial_args_or_empty()}'
    )

    result = AtestRunResult(
        self._run_shell_command(
            complete_cmd.split(),
            env=step_in.get_env(),
            cwd=step_in.get_repo_root(),
            print_output=print_output,
        ),
        step_in.get_env(),
        step_in.get_repo_root(),
        step_in.get_config(),
    )

    wrap_output_lines = lambda output_str: ''.join(
        ('    > %s' % line for line in output_str.splitlines(True))
    )
    logging.debug('Executed an atest command: %s', complete_cmd)
    logging.debug(
        '  Atest command stdout:\n%s', wrap_output_lines(result.get_stdout())
    )
    logging.debug('  Atest log:\n%s', wrap_output_lines(result.get_atest_log()))

    return result

  def _run_shell_command(
      self,
      cmd: list[str],
      env: dict[str, str],
      cwd: str,
      print_output: bool = True,
  ) -> subprocess.CompletedProcess[str]:
    """Execute shell command with real time output printing and capture."""

    def read_output(read_src, print_dst, capture_dst):
      while (output := read_src.readline()) or process.poll() is None:
        if output:
          if print_output:
            print(output, end='', file=print_dst)
          capture_dst.append(output)

    with subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        env=env,
        cwd=cwd,
    ) as process:
      stdout = []
      stderr = []
      with concurrent.futures.ThreadPoolExecutor() as executor:
        stdout_future = executor.submit(
            read_output, process.stdout, sys.stdout, stdout
        )
        stderr_future = executor.submit(
            read_output, process.stderr, sys.stderr, stderr
        )
      stdout_future.result()
      stderr_future.result()

      return subprocess.CompletedProcess(
          cmd, process.poll(), ''.join(stdout), ''.join(stderr)
      )

  def _get_jdk_path_list(self) -> str:
    """Get the relative jdk directory in build environment."""
    if split_build_test_script.ANDROID_BUILD_TOP_KEY not in os.environ:
      return []
    absolute_path = pathlib.Path(os.environ['ANDROID_JAVA_HOME'])
    while not absolute_path.name.startswith('jdk'):
      absolute_path = absolute_path.parent
    if not absolute_path.name.startswith('jdk'):
      raise ValueError(
          'Unrecognized jdk directory ' + os.environ['ANDROID_JAVA_HOME']
      )
    repo_root = pathlib.Path(
        os.environ[split_build_test_script.ANDROID_BUILD_TOP_KEY]
    )
    return [absolute_path.relative_to(repo_root).as_posix()]


def main():
  """Main method to run the integration tests."""

  def argparser_update_func(parser):
    parser.add_argument(
        '--use-prebuilt-atest-binary',
        action='store_true',
        default=False,
        help=(
            'Set the default atest binary to the prebuilt `atest` instead'
            ' of `atest-dev`.'
        ),
    )

  def config_update_function(config, args):
    config.use_prebuilt_atest_binary = args.use_prebuilt_atest_binary

  split_build_test_script.main(
      argv=sys.argv,
      make_before_build=['atest'],
      argparser_update_func=argparser_update_func,
      config_update_function=config_update_function,
  )
