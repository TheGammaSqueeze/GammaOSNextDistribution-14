#!/usr/bin/env python3
#
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

"""Command line utility for running Android tests through TradeFederation.

atest helps automate the flow of building test modules across the Android
code base and executing the tests via the TradeFederation test harness.

atest is designed to support any test types that can be ran by TradeFederation.
"""

# pylint: disable=too-many-lines

from __future__ import annotations
from __future__ import print_function

from abc import ABC, abstractmethod
import argparse
import collections
from dataclasses import dataclass
import itertools
import logging
import os
from pathlib import Path
import platform
import sys
import tempfile
import time
from typing import Any, Dict, List, Set, Tuple

from atest import arg_parser
from atest import atest_configs
from atest import atest_error
from atest import atest_execution_info
from atest import atest_utils
from atest import bazel_mode
from atest import bug_detector
from atest import cli_translator
from atest import constants
from atest import device_update
from atest import module_info
from atest import result_reporter
from atest import test_runner_handler
from atest.atest_enum import DetectType, ExitCode
from atest.coverage import coverage
from atest.metrics import metrics
from atest.metrics import metrics_base
from atest.metrics import metrics_utils
from atest.test_finders import test_finder_utils
from atest.test_finders import test_info
from atest.test_finders.test_info import TestInfo
from atest.test_runner_invocation import TestRunnerInvocation
from atest.tools import indexing
from atest.tools import start_avd as avd

EXPECTED_VARS = frozenset([
    constants.ANDROID_BUILD_TOP,
    'ANDROID_TARGET_OUT_TESTCASES',
    constants.ANDROID_OUT,
])
TEST_RUN_DIR_PREFIX = '%Y%m%d_%H%M%S'
CUSTOM_ARG_FLAG = '--'
OPTION_NOT_FOR_TEST_MAPPING = (
    'Option "{}" does not work for running tests in TEST_MAPPING files'
)

DEVICE_TESTS = 'tests that require device'
HOST_TESTS = 'tests that do NOT require device'
RESULT_HEADER_FMT = '\nResults from %(test_type)s:'
RUN_HEADER_FMT = '\nRunning %(test_count)d %(test_type)s.'
TEST_COUNT = 'test_count'
TEST_TYPE = 'test_type'
END_OF_OPTION = '--'
HAS_IGNORED_ARGS = False
# Conditions that atest should exit without sending result to metrics.
EXIT_CODES_BEFORE_TEST = [
    ExitCode.ENV_NOT_SETUP,
    ExitCode.TEST_NOT_FOUND,
    ExitCode.OUTSIDE_ROOT,
    ExitCode.AVD_CREATE_FAILURE,
    ExitCode.AVD_INVALID_ARGS,
]

# Stdout print prefix for results directory. May be used in integration tests.
_RESULTS_DIR_PRINT_PREFIX = 'Atest results and logs directory: '
# Log prefix for dry-run run command. May be used in integration tests.
_DRY_RUN_COMMAND_LOG_PREFIX = 'Internal run command from dry-run: '


@dataclass
class Steps:
  """A Dataclass that stores steps and shows step assignments."""

  _build: bool
  _device_update: bool
  _install: bool
  _test: bool

  def has_build(self):
    """Return whether build is in steps."""
    return self._build

  def is_build_only(self):
    """Return whether build is the only one in steps."""
    return self._build and not any(
        (self._test, self._install, self._device_update)
    )

  def has_device_update(self):
    """Return whether device update is in steps."""
    return self._device_update

  def has_install(self):
    """Return whether install is in steps."""
    return self._install

  def has_test(self):
    """Return whether install is the only one in steps."""
    return self._test

  def is_test_only(self):
    """Return whether build is not in steps but test."""
    return self._test and not any(
        (self._build, self._install, self._device_update)
    )


def parse_steps(args: arg_parser.AtestArgParser) -> Steps:
  """Return Steps object.

  Args:
      args: an AtestArgParser object.

  Returns:
      Step object that stores the boolean of build, install and test.
  """
  # Implicitly running 'build', 'install' and 'test' when args.steps is None.
  if not args.steps:
    return Steps(True, args.update_device, True, True)
  build = constants.BUILD_STEP in args.steps
  test = constants.TEST_STEP in args.steps
  install = constants.INSTALL_STEP in args.steps
  if install and not test:
    logging.warning(
        'Installing without test step is currently not '
        'supported; Atest will proceed testing!'
    )
    test = True
  return Steps(build, args.update_device, install, test)


def _get_args_from_config():
  """Get customized atest arguments in the config file.

  If the config has not existed yet, atest will initialize an example
  config file for it without any effective options.

  Returns:
      A list read from the config file.
  """
  _config = Path(atest_utils.get_misc_dir()).joinpath('.atest', 'config')
  if not _config.parent.is_dir():
    _config.parent.mkdir(parents=True)
  args = []
  if not _config.is_file():
    with open(_config, 'w+', encoding='utf8') as cache:
      cache.write(constants.ATEST_EXAMPLE_ARGS)
    return args
  warning = 'Line {} contains {} and will be ignored.'
  print(
      '\n{} {}'.format(
          atest_utils.mark_cyan('Reading config:'),
          atest_utils.mark_yellow(_config),
      )
  )
  # pylint: disable=global-statement:
  global HAS_IGNORED_ARGS
  with open(_config, 'r', encoding='utf8') as cache:
    for entry in cache.readlines():
      # Strip comments.
      arg_in_line = entry.partition('#')[0].strip()
      # Strip test name/path.
      if arg_in_line.startswith('-'):
        # Process argument that contains whitespaces.
        # e.g. ["--serial foo"] -> ["--serial", "foo"]
        if len(arg_in_line.split()) > 1:
          # remove "--" to avoid messing up atest/tradefed commands.
          if END_OF_OPTION in arg_in_line.split():
            HAS_IGNORED_ARGS = True
            print(
                warning.format(
                    atest_utils.mark_yellow(arg_in_line), END_OF_OPTION
                )
            )
          args.extend(arg_in_line.split())
        else:
          if END_OF_OPTION == arg_in_line:
            HAS_IGNORED_ARGS = True
            print(
                warning.format(
                    atest_utils.mark_yellow(arg_in_line), END_OF_OPTION
                )
            )
          args.append(arg_in_line)
  return args


def _parse_args(argv: List[Any]) -> Tuple[argparse.ArgumentParser, List[str]]:
  """Parse command line arguments.

  Args:
      argv: A list of arguments.

  Returns:
      A tuple of an argparse.ArgumentParser class instance holding parsed args
  """
  # Store everything after '--' in custom_args.
  pruned_argv = argv
  custom_args_index = None
  if CUSTOM_ARG_FLAG in argv:
    custom_args_index = argv.index(CUSTOM_ARG_FLAG)
    pruned_argv = argv[:custom_args_index]
  args = arg_parser.create_atest_arg_parser().parse_args(pruned_argv)
  args.custom_args = []
  if custom_args_index is not None:
    for arg in argv[custom_args_index + 1 :]:
      logging.debug('Quoting regex argument %s', arg)
      args.custom_args.append(atest_utils.quote(arg))

  return args


def _configure_logging(verbose: bool, results_dir: str):
  """Configure the logger.

  Args:
      verbose: If true display DEBUG level logs on console.
      results_dir: A directory which stores the ATest execution information.
  """
  log_fmat = '%(asctime)s %(filename)s:%(lineno)s:%(levelname)s: %(message)s'
  date_fmt = '%Y-%m-%d %H:%M:%S'
  log_path = os.path.join(results_dir, 'atest.log')

  # Clear the handlers to prevent logging.basicConfig from being called twice.
  logging.getLogger('').handlers = []
  logging.basicConfig(
      filename=log_path, level=logging.DEBUG, format=log_fmat, datefmt=date_fmt
  )
  # Handler for print the log on console that sets INFO (by default) or DEBUG
  # (verbose mode).
  console = logging.StreamHandler()
  console.name = 'console'
  console.setLevel(logging.INFO)
  if verbose:
    console.setLevel(logging.DEBUG)
  console.setFormatter(logging.Formatter(log_fmat))
  # Attach console handler to logger, so what we see is what we logged.
  logging.getLogger('').addHandler(console)


def _missing_environment_variables():
  """Verify the local environment has been set up to run atest.

  Returns:
      List of strings of any missing environment variables.
  """
  missing = list(
      filter(None, [x for x in EXPECTED_VARS if not os.environ.get(x)])
  )
  if missing:
    logging.error(
        "Local environment doesn't appear to have been "
        'initialized. Did you remember to run lunch? Expected '
        'Environment Variables: %s.',
        missing,
    )
  return missing


def make_test_run_dir():
  """Make the test run dir in ATEST_RESULT_ROOT.

  Returns:
      A string of the dir path.
  """
  if not os.path.exists(constants.ATEST_RESULT_ROOT):
    os.makedirs(constants.ATEST_RESULT_ROOT)
  ctime = time.strftime(TEST_RUN_DIR_PREFIX, time.localtime())
  test_result_dir = tempfile.mkdtemp(
      prefix='%s_' % ctime, dir=constants.ATEST_RESULT_ROOT
  )
  print(_RESULTS_DIR_PRINT_PREFIX + test_result_dir)
  return test_result_dir


def get_extra_args(args):
  """Get extra args for test runners.

  Args:
      args: arg parsed object.

  Returns:
      Dict of extra args for test runners to utilize.
  """
  extra_args = {}
  if args.wait_for_debugger:
    extra_args[constants.WAIT_FOR_DEBUGGER] = None
  if not parse_steps(args).has_install():
    extra_args[constants.DISABLE_INSTALL] = None
  # The key and its value of the dict can be called via:
  # if args.aaaa:
  #     extra_args[constants.AAAA] = args.aaaa
  arg_maps = {
      'all_abi': constants.ALL_ABI,
      'annotation_filter': constants.ANNOTATION_FILTER,
      'bazel_arg': constants.BAZEL_ARG,
      'collect_tests_only': constants.COLLECT_TESTS_ONLY,
      'experimental_coverage': constants.COVERAGE,
      'custom_args': constants.CUSTOM_ARGS,
      'device_only': constants.DEVICE_ONLY,
      'disable_teardown': constants.DISABLE_TEARDOWN,
      'disable_upload_result': constants.DISABLE_UPLOAD_RESULT,
      'dry_run': constants.DRY_RUN,
      'host': constants.HOST,
      'instant': constants.INSTANT,
      'iterations': constants.ITERATIONS,
      'request_upload_result': constants.REQUEST_UPLOAD_RESULT,
      'bazel_mode_features': constants.BAZEL_MODE_FEATURES,
      'rerun_until_failure': constants.RERUN_UNTIL_FAILURE,
      'retry_any_failure': constants.RETRY_ANY_FAILURE,
      'serial': constants.SERIAL,
      'sharding': constants.SHARDING,
      'test_filter': constants.TEST_FILTER,
      'test_timeout': constants.TEST_TIMEOUT,
      'tf_debug': constants.TF_DEBUG,
      'tf_template': constants.TF_TEMPLATE,
      'user_type': constants.USER_TYPE,
      'verbose': constants.VERBOSE,
      'use_tf_min_base_template': constants.USE_TF_MIN_BASE_TEMPLATE,
  }
  not_match = [k for k in arg_maps if k not in vars(args)]
  if not_match:
    raise AttributeError(
        '%s object has no attribute %s' % (type(args).__name__, not_match)
    )
  extra_args.update({
      arg_maps.get(k): v for k, v in vars(args).items() if arg_maps.get(k) and v
  })
  return extra_args


def _validate_exec_mode(args, test_infos: list[TestInfo], host_tests=None):
  """Validate all test execution modes are not in conflict.

  Exit the program with INVALID_EXEC_MODE code if the desired is a host-side
  test but the given is a device-side test.

  If the given is a host-side test and not specified `args.host`, forcibly
  set `args.host` to True.

  Args:
      args: parsed args object.
      test_infos: a list of TestInfo objects.
      host_tests: True if all tests should be deviceless, False if all tests
        should be device tests. Default is set to None, which means tests can be
        either deviceless or device tests.
  """
  all_device_modes = {x.get_supported_exec_mode() for x in test_infos}
  err_msg = None
  # In the case of '$atest <device-only> --host', exit.
  if (host_tests or args.host) and constants.DEVICE_TEST in all_device_modes:
    device_only_tests = [
        x.test_name
        for x in test_infos
        if x.get_supported_exec_mode() == constants.DEVICE_TEST
    ]
    err_msg = (
        'Specified --host, but the following tests are device-only:\n  '
        + '\n  '.join(sorted(device_only_tests))
        + '\nPlease remove the  option when running device-only tests.'
    )
  # In the case of '$atest <host-only> <device-only> --host' or
  # '$atest <host-only> <device-only>', exit.
  if (
      constants.DEVICELESS_TEST in all_device_modes
      and constants.DEVICE_TEST in all_device_modes
  ):
    err_msg = 'There are host-only and device-only tests in command.'
  if host_tests is False and constants.DEVICELESS_TEST in all_device_modes:
    err_msg = 'There are host-only tests in command.'
  if err_msg:
    logging.error(err_msg)
    metrics_utils.send_exit_event(ExitCode.INVALID_EXEC_MODE, logs=err_msg)
    sys.exit(ExitCode.INVALID_EXEC_MODE)
  # The 'adb' may not be available for the first repo sync or a clean build;
  # run `adb devices` in the build step again.
  if atest_utils.has_command('adb'):
    _validate_adb_devices(args, test_infos)
  # In the case of '$atest <host-only>', we add --host to run on host-side.
  # The option should only be overridden if `host_tests` is not set.
  if not args.host and host_tests is None:
    logging.debug('Appending "--host" for a deviceless test...')
    args.host = bool(constants.DEVICELESS_TEST in all_device_modes)


def _validate_adb_devices(args, test_infos):
  """Validate the availability of connected devices via adb command.

  Exit the program with error code if have device-only and host-only.

  Args:
      args: parsed args object.
      test_info: TestInfo object.
  """
  # No need to check device availability if the user does not acquire to test.
  if not parse_steps(args).has_test():
    return
  if args.no_checking_device:
    return
  # No need to check local device availability if the device test is running
  # remotely.
  if args.bazel_mode_features and (
      bazel_mode.Features.EXPERIMENTAL_REMOTE_AVD in args.bazel_mode_features
  ):
    return
  all_device_modes = {x.get_supported_exec_mode() for x in test_infos}
  device_tests = [
      x.test_name
      for x in test_infos
      if x.get_supported_exec_mode() != constants.DEVICELESS_TEST
  ]
  # Only block testing if it is a device test.
  if constants.DEVICE_TEST in all_device_modes:
    if (
        not any((args.host, args.start_avd, args.acloud_create))
        and not atest_utils.get_adb_devices()
    ):
      err_msg = (
          f'Stop running test(s): {", ".join(device_tests)} require a device.'
      )
      atest_utils.colorful_print(err_msg, constants.RED)
      logging.debug(atest_utils.mark_red(constants.REQUIRE_DEVICES_MSG))
      metrics_utils.send_exit_event(ExitCode.DEVICE_NOT_FOUND, logs=err_msg)
      sys.exit(ExitCode.DEVICE_NOT_FOUND)


def _validate_tm_tests_exec_mode(
    args: argparse.Namespace,
    device_test_infos: List[test_info.TestInfo],
    host_test_infos: List[test_info.TestInfo],
):
  """Validate all test execution modes are not in conflict.

  Validate the tests' platform variant setting. For device tests, exit the
  program if any test is found for host-only. For host tests, exit the
  program if any test is found for device-only.

  Args:
      args: parsed args object.
      device_test_infos: TestInfo instances for device tests.
      host_test_infos: TestInfo instances for host tests.
  """

  # No need to verify device tests if atest command is set to only run host
  # tests.
  if device_test_infos and not args.host:
    _validate_exec_mode(args, device_test_infos, host_tests=False)
  if host_test_infos:
    _validate_exec_mode(args, host_test_infos, host_tests=True)


def _has_valid_test_mapping_args(args):
  """Validate test mapping args.

  Not all args work when running tests in TEST_MAPPING files. Validate the
  args before running the tests.

  Args:
      args: parsed args object.

  Returns:
      True if args are valid
  """
  is_test_mapping = atest_utils.is_test_mapping(args)
  if is_test_mapping:
    metrics.LocalDetectEvent(detect_type=DetectType.IS_TEST_MAPPING, result=1)
  else:
    metrics.LocalDetectEvent(detect_type=DetectType.IS_TEST_MAPPING, result=0)
  if not is_test_mapping:
    return True
  options_to_validate = [
      (args.annotation_filter, '--annotation-filter'),
  ]
  for arg_value, arg in options_to_validate:
    if arg_value:
      logging.error(
          atest_utils.mark_red(OPTION_NOT_FOR_TEST_MAPPING.format(arg))
      )
      return False
  return True


def _validate_args(args):
  """Validate setups and args.

  Exit the program with error code if any setup or arg is invalid.

  Args:
      args: parsed args object.
  """
  if _missing_environment_variables():
    sys.exit(ExitCode.ENV_NOT_SETUP)
  if not _has_valid_test_mapping_args(args):
    sys.exit(ExitCode.INVALID_TM_ARGS)


def _print_module_info_from_module_name(mod_info, module_name):
  """print out the related module_info for a module_name.

  Args:
      mod_info: ModuleInfo object.
      module_name: A string of module.

  Returns:
      True if the module_info is found.
  """
  title_mapping = collections.OrderedDict()
  title_mapping[constants.MODULE_COMPATIBILITY_SUITES] = 'Compatibility suite'
  title_mapping[constants.MODULE_PATH] = 'Source code path'
  title_mapping[constants.MODULE_INSTALLED] = 'Installed path'
  target_module_info = mod_info.get_module_info(module_name)
  is_module_found = False
  if target_module_info:
    atest_utils.colorful_print(module_name, constants.GREEN)
    for title_key in title_mapping:
      atest_utils.colorful_print(
          '\t%s' % title_mapping[title_key], constants.CYAN
      )
      for info_value in target_module_info[title_key]:
        print('\t\t{}'.format(info_value))
    is_module_found = True
  return is_module_found


def _print_deprecation_warning(arg_to_deprecate: str):
  """For features that are up for deprecation in the near future, print a message

  to alert the user about the upcoming deprecation.

  Args:
      arg_to_deprecate: the arg with which the to-be-deprecated feature is
        called.
  """
  args_to_deprecation_info = {
      # arg_to_deprecate : (deprecation timeframe, additional info for users)
      '--info': ('is deprecated.', '\nUse CodeSearch or `gomod` instead.')
  }

  warning_message = (
      f'\nWARNING: The `{arg_to_deprecate}` feature '
      + ' '.join(args_to_deprecation_info[arg_to_deprecate])
      + '\nPlease file a bug or feature request to the Atest team if you have'
      ' any concerns.'
  )
  atest_utils.colorful_print(warning_message, constants.RED)


def is_from_test_mapping(test_infos):
  """Check that the test_infos came from TEST_MAPPING files.

  Args:
      test_infos: A set of TestInfos.

  Returns:
      True if the test infos are from TEST_MAPPING files.
  """
  return list(test_infos)[0].from_test_mapping


def _split_test_mapping_tests(test_infos):
  """Split Test Mapping tests into 2 groups: device tests and host tests.

  Args:
      test_infos: A set of TestInfos.

  Returns:
      A tuple of (device_test_infos, host_test_infos), where
      device_test_infos: A set of TestInfos for tests that require device.
      host_test_infos: A set of TestInfos for tests that do NOT require
          device.
  """
  assert is_from_test_mapping(test_infos)
  host_test_infos = {info for info in test_infos if info.host}
  device_test_infos = {info for info in test_infos if not info.host}
  return device_test_infos, host_test_infos


# pylint: disable=too-many-locals
def _run_test_mapping_tests(
    test_type_to_invocations: Dict[str, List[TestRunnerInvocation]],
    extra_args: Dict[str, Any],
) -> ExitCode:
  """Run all tests in TEST_MAPPING files.

  Args:
      test_type_to_invocations: A dict mapping test runner invocations to test
        types.
      extra_args: A dict of extra args for others to utilize.

  Returns:
      Exit code.
  """

  test_results = []
  for test_type, invocations in test_type_to_invocations.items():
    tests = list(
        itertools.chain.from_iterable(i.test_infos for i in invocations)
    )
    if not tests:
      continue
    header = RUN_HEADER_FMT % {TEST_COUNT: len(tests), TEST_TYPE: test_type}
    atest_utils.colorful_print(header, constants.MAGENTA)
    logging.debug('\n'.join([str(info) for info in tests]))

    reporter = result_reporter.ResultReporter(
        collect_only=extra_args.get(constants.COLLECT_TESTS_ONLY),
        wait_for_debugger=atest_configs.GLOBAL_ARGS.wait_for_debugger,
    )
    reporter.print_starting_text()

    tests_exit_code = ExitCode.SUCCESS
    for invocation in invocations:
      tests_exit_code |= invocation.run_all_tests(reporter)

    atest_execution_info.AtestExecutionInfo.result_reporters.append(reporter)
    test_results.append((tests_exit_code, reporter, test_type))

  all_tests_exit_code = ExitCode.SUCCESS
  failed_tests = []
  for tests_exit_code, reporter, test_type in test_results:
    atest_utils.colorful_print(
        RESULT_HEADER_FMT % {TEST_TYPE: test_type}, constants.MAGENTA
    )
    result = tests_exit_code | reporter.print_summary()
    if result:
      failed_tests.append(test_type)
    all_tests_exit_code |= result

  # List failed tests at the end as a reminder.
  if failed_tests:
    atest_utils.colorful_print(
        atest_utils.delimiter('=', 30, prenl=1), constants.YELLOW
    )
    atest_utils.colorful_print('\nFollowing tests failed:', constants.MAGENTA)
    for failure in failed_tests:
      atest_utils.colorful_print(failure, constants.RED)

  return all_tests_exit_code


def _dry_run(results_dir, extra_args, test_infos, mod_info):
  """Only print the commands of the target tests rather than running them in

  actual.

  Args:
      results_dir: Path for saving atest logs.
      extra_args: Dict of extra args for test runners to utilize.
      test_infos: A list of TestInfos.
      mod_info: ModuleInfo object.

  Returns:
      A list of test commands.
  """
  all_run_cmds = []
  for test_runner, tests in test_runner_handler.group_tests_by_test_runners(
      test_infos
  ):
    runner = test_runner(results_dir, mod_info=mod_info, extra_args=extra_args)
    run_cmds = runner.generate_run_commands(tests, extra_args)
    for run_cmd in run_cmds:
      all_run_cmds.append(run_cmd)
      logging.debug(_DRY_RUN_COMMAND_LOG_PREFIX + run_cmd)
      print(
          'Would run test via command: %s' % (atest_utils.mark_green(run_cmd))
      )
  return all_run_cmds


def _print_testable_modules(mod_info, suite):
  """Print the testable modules for a given suite.

  Args:
      mod_info: ModuleInfo object.
      suite: A string of suite name.
  """
  testable_modules = mod_info.get_testable_modules(suite)
  print(
      '\n%s'
      % atest_utils.mark_cyan(
          '%s Testable %s modules' % (len(testable_modules), suite)
      )
  )
  print(atest_utils.delimiter('-'))
  for module in sorted(testable_modules):
    print('\t%s' % module)


def _is_inside_android_root():
  """Identify whether the cwd is inside of Android source tree.

  Returns:
      False if the cwd is outside of the source tree, True otherwise.
  """
  build_top = os.getenv(constants.ANDROID_BUILD_TOP, ' ')
  return build_top in os.getcwd()


def _non_action_validator(args: argparse.ArgumentParser):
  """Method for non-action arguments such as --version, --history,

  --latest_result, etc.

  Args:
      args: An argparse.ArgumentParser object.
  """
  if not _is_inside_android_root():
    atest_utils.colorful_print(
        '\nAtest must always work under ${}!'.format(
            constants.ANDROID_BUILD_TOP
        ),
        constants.RED,
    )
    sys.exit(ExitCode.OUTSIDE_ROOT)
  if args.version:
    print(atest_utils.get_atest_version())
    sys.exit(ExitCode.SUCCESS)
  if args.history:
    atest_execution_info.print_test_result(
        constants.ATEST_RESULT_ROOT, args.history
    )
    sys.exit(ExitCode.SUCCESS)
  if args.latest_result:
    atest_execution_info.print_test_result_by_path(constants.LATEST_RESULT_FILE)
    sys.exit(ExitCode.SUCCESS)


def _dry_run_validator(
    args: argparse.ArgumentParser,
    results_dir: str,
    extra_args: Dict[str, Any],
    test_infos: Set[TestInfo],
    mod_info: module_info.ModuleInfo,
) -> ExitCode:
  """Method which process --dry-run argument.

  Args:
      args: An argparse.ArgumentParser class instance holding parsed args.
      result_dir: A string path of the results dir.
      extra_args: A dict of extra args for test runners to utilize.
      test_infos: A list of test_info.
      mod_info: ModuleInfo object.

  Returns:
      Exit code.
  """
  dry_run_cmds = _dry_run(results_dir, extra_args, test_infos, mod_info)
  if args.generate_runner_cmd:
    dry_run_cmd_str = ' '.join(dry_run_cmds)
    tests_str = ' '.join(args.tests)
    test_commands = atest_utils.gen_runner_cmd_to_file(
        tests_str, dry_run_cmd_str
    )
    print(
        'add command %s to file %s'
        % (
            atest_utils.mark_green(test_commands),
            atest_utils.mark_green(constants.RUNNER_COMMAND_PATH),
        )
    )
  return ExitCode.SUCCESS


def _exclude_modules_in_targets(build_targets):
  """Method that excludes MODULES-IN-* targets.

  Args:
      build_targets: A set of build targets.

  Returns:
      A set of build targets that excludes MODULES-IN-*.
  """
  shrank_build_targets = build_targets.copy()
  logging.debug(
      'Will exclude all "%s*" from the build targets.', constants.MODULES_IN
  )
  for target in build_targets:
    if target.startswith(constants.MODULES_IN):
      logging.debug('Ignore %s.', target)
      shrank_build_targets.remove(target)
  return shrank_build_targets


# pylint: disable=protected-access
def need_rebuild_module_info(args: arg_parser.AtestArgParser) -> bool:
  """Method that tells whether we need to rebuild module-info.json or not.

  Args:
      args: an AtestArgParser object.

  Returns:
      True for forcely/smartly rebuild, otherwise False without rebuilding.
  """
  # +-----------------+
  # | Explicitly pass |  yes
  # |    '--test'     +-------> False (won't rebuild)
  # +--------+--------+
  #          | no
  #          V
  # +-------------------------+
  # | Explicitly pass         |  yes
  # | '--rebuild-module-info' +-------> True (forcely rebuild)
  # +--------+----------------+
  #          | no
  #          V
  # +-------------------+
  # |    Build files    |  no
  # | integrity is good +-------> True (smartly rebuild)
  # +--------+----------+
  #          | yes
  #          V
  #        False (won't rebuild)
  if not parse_steps(args).has_build():
    logging.debug('"--test" mode detected, will not rebuild module-info.')
    return False
  if args.rebuild_module_info:
    msg = (
        f'`{constants.REBUILD_MODULE_INFO_FLAG}` is no longer needed '
        f'since Atest can smartly rebuild {module_info._MODULE_INFO} '
        r'only when needed.'
    )
    atest_utils.colorful_print(msg, constants.YELLOW)
    return True
  logging.debug('Examinating the consistency of build files...')
  if not atest_utils.build_files_integrity_is_ok():
    logging.debug('Found build files were changed.')
    return True
  return False


def need_run_index_targets(args: argparse.ArgumentParser):
  """Method that determines whether Atest need to run index_targets or not.

  The decision flow is as follows: If no build is required, returns False.
  Otherwise, if some index files are missing, returns True. Otherwise, if
  some arguments that doesn't require indexing is present, returns False.
  Otherwise, returns True.

  Args:
      args: An argparse.ArgumentParser object.

  Returns:
      True when none of the above conditions were found.
  """
  has_build_step = parse_steps(args).has_build()
  if not has_build_step:
    logging.debug("Skip indexing because there's no build required.")
    return False

  if not indexing.Indices().has_all_indices():
    logging.debug(
        'Indexing targets is required because some index files do not exist.'
    )
    return True

  no_indexing_args = (
      args.dry_run,
      args.list_modules,
  )
  if any(no_indexing_args):
    logging.debug('Skip indexing for no_indexing_args=%s.', no_indexing_args)
    return False

  return True


def set_build_output_mode(mode: atest_utils.BuildOutputMode):
  """Update environment variable dict accordingly to args.build_output."""
  # Changing this variable does not retrigger builds.
  atest_utils.update_build_env(
      {'ANDROID_QUIET_BUILD': 'true', 'BUILD_OUTPUT_MODE': mode.value}
  )


def get_device_count_config(test_infos, mod_info):
  """Get the amount of desired devices from the test config.

  Args:
      test_infos: A set of TestInfo instances.
      mod_info: ModuleInfo object.

  Returns: the count of devices in test config. If there are more than one
           configs, return the maximum.
  """
  max_count = 0
  for tinfo in test_infos:
    test_config, _ = test_finder_utils.get_test_config_and_srcs(tinfo, mod_info)
    if test_config:
      devices = atest_utils.get_config_device(test_config)
      if devices:
        max_count = max(len(devices), max_count)
  return max_count


def _send_start_event(argv: List[Any], tests: List[str]):
  """Send AtestStartEvent to metrics"""
  os_pyver = (
      f'{platform.platform()}:{platform.python_version()}/'
      f'{atest_utils.get_manifest_branch(True)}:'
      f'{atest_utils.get_atest_version()}'
  )
  metrics.AtestStartEvent(
      command_line=' '.join(argv),
      test_references=tests,
      cwd=os.getcwd(),
      os=os_pyver,
  )


def _get_acloud_proc_and_log(
    args: argparse.ArgumentParser, results_dir: str
) -> Tuple[Any, Any]:
  """Return tuple of acloud process ID and report file."""
  if any((args.acloud_create, args.start_avd)):
    logging.debug('Creating acloud or avd.')
    return avd.acloud_create_validator(results_dir, args)
  return None, None


def has_set_sufficient_devices(
    required_amount: int, serial: List[str] = None
) -> bool:
  """Detect whether sufficient device serial is set for test."""
  given_amount = len(serial) if serial else 0
  # Only check when both given_amount and required_amount are non zero.
  if all((given_amount, required_amount)):
    # Base on TF rules, given_amount can be greater than or equal to
    # required_amount.
    if required_amount > given_amount:
      atest_utils.colorful_print(
          f'The test requires {required_amount} devices, '
          f'but {given_amount} were given.',
          constants.RED,
      )
      return False
  return True


def setup_metrics_tool_name(no_metrics: bool = False):
  """Setup tool_name and sub_tool_name for MetricsBase."""
  if (
      not no_metrics
      and metrics_base.MetricsBase.user_type == metrics_base.INTERNAL_USER
  ):
    metrics_utils.print_data_collection_notice()

    USER_FROM_TOOL = os.getenv(constants.USER_FROM_TOOL)
    metrics_base.MetricsBase.tool_name = (
        USER_FROM_TOOL if USER_FROM_TOOL else constants.TOOL_NAME
    )

    USER_FROM_SUB_TOOL = os.getenv(constants.USER_FROM_SUB_TOOL)
    metrics_base.MetricsBase.sub_tool_name = (
        USER_FROM_SUB_TOOL if USER_FROM_SUB_TOOL else constants.SUB_TOOL_NAME
    )


# pylint: disable=too-many-statements
# pylint: disable=too-many-branches
# pylint: disable=too-many-return-statements
def _main(argv: List[Any], results_dir: str, args: argparse.Namespace):
  """Entry point of atest script.

  Args:
      argv: A list of arguments.
      results_dir: A directory which stores the ATest execution information.
      args: An argparse.Namespace class instance holding parsed args.

  Returns:
      Exit code.
  """
  _begin_time = time.time()
  logging.debug(
      'Running atest script with argv %s, results_dir %s, args %s.',
      argv,
      results_dir,
      args,
  )

  # Sets coverage environment variables.
  if args.experimental_coverage:
    atest_utils.update_build_env(coverage.build_env_vars())
  set_build_output_mode(args.build_output)

  _validate_args(args)
  metrics_utils.get_start_time()
  _send_start_event(argv, args.tests)
  _non_action_validator(args)

  proc_acloud, report_file = _get_acloud_proc_and_log(args, results_dir)
  is_clean = not os.path.exists(
      os.environ.get(constants.ANDROID_PRODUCT_OUT, '')
  )

  # Run Test Mapping or coverage by no-bazel-mode.
  if atest_utils.is_test_mapping(args) or args.experimental_coverage:
    logging.debug('Running test mapping or coverage, disabling bazel mode.')
    atest_utils.colorful_print(
        'Not running using bazel-mode.', constants.YELLOW
    )
    args.bazel_mode = False

  proc_idx = atest_utils.start_threading(lambda: print)
  # Do not index targets while the users intend to dry-run tests.
  if need_run_index_targets(args):
    logging.debug('Starting to index targets in a background thread.')
    proc_idx = atest_utils.start_threading(
        indexing.index_targets,
        daemon=True,
    )
  smart_rebuild = need_rebuild_module_info(args)
  logging.debug('need_rebuild_module_info returned %s', smart_rebuild)

  mod_info = module_info.load(
      force_build=smart_rebuild,
      sqlite_module_cache=args.sqlite_module_cache,
  )
  logging.debug('Obtained module info object: %s', mod_info)

  translator = cli_translator.CLITranslator(
      mod_info=mod_info,
      print_cache_msg=not args.clear_cache,
      bazel_mode_enabled=args.bazel_mode,
      host=args.host,
      bazel_mode_features=args.bazel_mode_features,
  )
  if args.list_modules:
    _print_testable_modules(mod_info, args.list_modules)
    return ExitCode.SUCCESS
  test_infos = set()
  dry_run_args = (args.dry_run, args.generate_runner_cmd)
  # (b/242567487) index_targets may finish after cli_translator; to
  # mitigate the overhead, the main waits until it finished when no index
  # files are available (e.g. fresh repo sync)
  if proc_idx.is_alive() and not indexing.Indices().has_all_indices():
    proc_idx.join()
  find_start = time.time()
  test_infos = translator.translate(args)

  # Only check for sufficient devices if not dry run.
  args.device_count_config = get_device_count_config(test_infos, mod_info)
  if not any(dry_run_args) and not has_set_sufficient_devices(
      args.device_count_config, args.serial
  ):
    return ExitCode.INSUFFICIENT_DEVICES

  find_duration = time.time() - find_start
  if not test_infos:
    return ExitCode.TEST_NOT_FOUND

  test_execution_plan = _create_test_execution_plan(
      test_infos=test_infos,
      results_dir=results_dir,
      mod_info=mod_info,
      args=args,
      dry_run=any(dry_run_args),
  )

  extra_args = test_execution_plan.extra_args

  build_targets = test_execution_plan.required_build_targets()

  # Remove MODULE-IN-* from build targets by default.
  if not args.use_modules_in:
    build_targets = _exclude_modules_in_targets(build_targets)

  if any(dry_run_args):
    return _dry_run_validator(
        args, results_dir, extra_args, test_infos, mod_info
    )

  steps = parse_steps(args)
  device_update_method = _configure_update_method(
    steps=steps,
    plan=test_execution_plan,
    adevice_targets=set(args.adevice_targets))

  if build_targets and steps.has_build():
    if args.experimental_coverage:
      build_targets.update(coverage.build_modules())

    # Add module-info.json target to the list of build targets to keep the
    # file up to date.
    build_targets.add(module_info.get_module_info_target())

    build_targets |= device_update_method.dependencies()

    # Add the -jx as a build target if user specify it.
    if args.build_j:
      build_targets.add(f'-j{args.build_j}')

    build_start = time.time()
    success = atest_utils.build(build_targets)
    build_duration = time.time() - build_start
    metrics.BuildFinishEvent(
        duration=metrics_utils.convert_duration(build_duration),
        success=success,
        targets=build_targets,
    )
    metrics.LocalDetectEvent(
        detect_type=DetectType.BUILD_TIME_PER_TARGET,
        result=int(build_duration / len(build_targets)),
    )
    rebuild_module_info = DetectType.NOT_REBUILD_MODULE_INFO
    if is_clean:
      rebuild_module_info = DetectType.CLEAN_BUILD
    elif args.rebuild_module_info:
      rebuild_module_info = DetectType.REBUILD_MODULE_INFO
    elif smart_rebuild:
      rebuild_module_info = DetectType.SMART_REBUILD_MODULE_INFO
    metrics.LocalDetectEvent(
        detect_type=rebuild_module_info, result=int(build_duration)
    )
    if not success:
      return ExitCode.BUILD_FAILURE
    if proc_acloud:
      proc_acloud.join()
      status = avd.probe_acloud_status(
          report_file, find_duration + build_duration
      )
      if status != 0:
        return status
    # After build step 'adb' command will be available, and stop forward to
    # Tradefed if the tests require a device.
    _validate_adb_devices(args, test_infos)

  device_update_method.update(extra_args.get(constants.SERIAL, []))

  tests_exit_code = ExitCode.SUCCESS
  test_start = time.time()
  if steps.has_test():
    # Only send duration to metrics when no --build.
    if not steps.has_build():
      _init_and_find = time.time() - _begin_time
      logging.debug('Initiation and finding tests took %ss', _init_and_find)
      metrics.LocalDetectEvent(
          detect_type=DetectType.INIT_AND_FIND_MS,
          result=int(_init_and_find * 1000),
      )

    tests_exit_code = test_execution_plan.execute()

    if args.experimental_coverage:
      coverage.generate_coverage_report(results_dir, test_infos, mod_info)
  metrics.RunTestsFinishEvent(
      duration=metrics_utils.convert_duration(time.time() - test_start)
  )
  preparation_time = atest_execution_info.preparation_time(test_start)
  if preparation_time:
    # Send the preparation time only if it's set.
    metrics.RunnerFinishEvent(
        duration=metrics_utils.convert_duration(preparation_time),
        success=True,
        runner_name=constants.TF_PREPARATION,
        test=[],
    )
  if tests_exit_code != ExitCode.SUCCESS:
    tests_exit_code = ExitCode.TEST_FAILURE

  return tests_exit_code


def _configure_update_method(
    *,
    steps: Steps,
    plan: TestExecutionPlan,
    adevice_targets: Set[str],
) -> device_update.DeviceUpdateMethod:

  if not steps.has_device_update():
    return device_update.NoopUpdateMethod()

  if not plan.requires_device_update():
    atest_utils.colorful_print(
        '\nWarning: Device update ignored because it is not required by '
        'tests in this invocation.',
        constants.YELLOW,
    )
    return device_update.NoopUpdateMethod()


  return device_update.AdeviceUpdateMethod(targets=adevice_targets)


def _create_test_execution_plan(
    *,
    test_infos: List[test_info.TestInfo],
    results_dir: str,
    mod_info: module_info.ModuleInfo,
    args: argparse.Namespace,
    dry_run: bool,
) -> TestExecutionPlan:
  """Creates a plan to execute the tests.

  Args:
      test_infos: A list of instances of TestInfo.
      results_dir: A directory which stores the ATest execution information.
      mod_info: An instance of ModuleInfo.
      args: An argparse.Namespace instance holding parsed args.
      dry_run: A boolean of whether this invocation is a dry run.

  Returns:
      An instance of TestExecutionPlan.
  """

  if is_from_test_mapping(test_infos):
    return TestMappingExecutionPlan.create(
        test_infos=test_infos,
        results_dir=results_dir,
        mod_info=mod_info,
        args=args,
    )

  return TestModuleExecutionPlan.create(
      test_infos=test_infos,
      results_dir=results_dir,
      mod_info=mod_info,
      args=args,
      dry_run=dry_run,
  )


class TestExecutionPlan(ABC):
  """Represents how an Atest invocation's tests will execute."""

  def __init__(
      self,
      *,
      extra_args: Dict[str, Any],
  ):
    self._extra_args = extra_args

  @property
  def extra_args(self) -> Dict[str, Any]:
    return self._extra_args

  @abstractmethod
  def execute(self) -> ExitCode:
    """Executes all test runner invocations in this plan."""

  @abstractmethod
  def required_build_targets(self) -> Set[str]:
    """Returns the list of build targets required by this plan."""

  @abstractmethod
  def requires_device_update(self) -> bool:
    """Checks whether this plan requires device update."""


class TestMappingExecutionPlan(TestExecutionPlan):
  """A plan to execute Test Mapping tests."""

  def __init__(
      self,
      *,
      test_type_to_invocations: Dict[str, List[TestRunnerInvocation]],
      extra_args: Dict[str, Any],
  ):
    super().__init__(extra_args=extra_args)
    self._test_type_to_invocations = test_type_to_invocations

  @staticmethod
  def create(
      *,
      test_infos: List[test_info.TestInfo],
      results_dir: str,
      mod_info: module_info.ModuleInfo,
      args: argparse.Namespace,
  ) -> TestMappingExecutionPlan:
    """Creates an instance of TestMappingExecutionPlan.

    Args:
        test_infos: A list of instances of TestInfo.
        results_dir: A directory which stores the ATest execution information.
        mod_info: An instance of ModuleInfo.
        args: An argparse.Namespace instance holding parsed args.

    Returns:
        An instance of TestMappingExecutionPlan.
    """

    device_test_infos, host_test_infos = _split_test_mapping_tests(test_infos)
    _validate_tm_tests_exec_mode(args, device_test_infos, host_test_infos)
    extra_args = get_extra_args(args)

    # TODO: change to another approach that put constants.CUSTOM_ARGS in the
    # end of command to make sure that customized args can override default
    # options.
    # For TEST_MAPPING, set timeout to 600000ms.
    custom_timeout = False
    for custom_args in args.custom_args:
      if '-timeout' in custom_args:
        custom_timeout = True

    if args.test_timeout is None and not custom_timeout:
      extra_args.update({constants.TEST_TIMEOUT: 600000})
      logging.debug(
          'Set test timeout to %sms to align it in TEST_MAPPING.',
          extra_args.get(constants.TEST_TIMEOUT),
      )

    def create_invocations(runner_extra_args, runner_test_infos):
      return test_runner_handler.create_test_runner_invocations(
          test_infos=runner_test_infos,
          results_dir=results_dir,
          mod_info=mod_info,
          extra_args=runner_extra_args,
          minimal_build=args.minimal_build,
      )

    test_type_to_invocations = collections.OrderedDict()
    if extra_args.get(constants.DEVICE_ONLY):
      atest_utils.colorful_print(
          'Option `--device-only` specified. Skip running deviceless tests.',
          constants.MAGENTA,
      )
    else:
      # `host` option needs to be set to True to run host side tests.
      host_extra_args = extra_args.copy()
      host_extra_args[constants.HOST] = True
      test_type_to_invocations.setdefault(HOST_TESTS, []).extend(
          create_invocations(host_extra_args, host_test_infos)
      )

    if extra_args.get(constants.HOST):
      atest_utils.colorful_print(
          'Option `--host` specified. Skip running device tests.',
          constants.MAGENTA,
      )
    else:
      test_type_to_invocations.setdefault(DEVICE_TESTS, []).extend(
          create_invocations(extra_args, device_test_infos)
      )

    return TestMappingExecutionPlan(
        test_type_to_invocations=test_type_to_invocations,
        extra_args=extra_args,
    )

  def requires_device_update(self) -> bool:
    return _requires_device_update(
        [i for invs in self._test_type_to_invocations.values() for i in invs]
    )

  def required_build_targets(self) -> Set[str]:
    build_targets = set()
    for invocation in itertools.chain.from_iterable(
        self._test_type_to_invocations.values()
    ):
      build_targets |= invocation.get_test_runner_reqs()

    return build_targets

  def execute(self) -> ExitCode:
    return _run_test_mapping_tests(
        self._test_type_to_invocations, self.extra_args
    )


class TestModuleExecutionPlan(TestExecutionPlan):
  """A plan to execute the test modules explicitly passed on the command-line."""

  def __init__(
      self,
      *,
      test_runner_invocations: List[TestRunnerInvocation],
      extra_args: Dict[str, Any],
  ):
    super().__init__(extra_args=extra_args)
    self._test_runner_invocations = test_runner_invocations

  @staticmethod
  def create(
      *,
      test_infos: List[test_info.TestInfo],
      results_dir: str,
      mod_info: module_info.ModuleInfo,
      args: argparse.Namespace,
      dry_run: bool,
  ) -> TestModuleExecutionPlan:
    """Creates an instance of TestModuleExecutionPlan.

    Args:
        test_infos: A list of instances of TestInfo.
        results_dir: A directory which stores the ATest execution information.
        mod_info: An instance of ModuleInfo.
        args: An argparse.Namespace instance holding parsed args.
        dry_run: A boolean of whether this invocation is a dry run.

    Returns:
        An instance of TestModuleExecutionPlan.
    """

    if not dry_run:
      _validate_exec_mode(args, test_infos)

    # _validate_exec_mode appends --host automatically when pure
    # host-side tests, so re-parsing extra_args is a must.
    extra_args = get_extra_args(args)

    invocations = test_runner_handler.create_test_runner_invocations(
        test_infos=test_infos,
        results_dir=results_dir,
        mod_info=mod_info,
        extra_args=extra_args,
        minimal_build=args.minimal_build,
    )

    return TestModuleExecutionPlan(
        test_runner_invocations=invocations,
        extra_args=extra_args,
    )

  def requires_device_update(self) -> bool:
    return _requires_device_update(self._test_runner_invocations)

  def required_build_targets(self) -> Set[str]:
    build_targets = set()
    for test_runner_invocation in self._test_runner_invocations:
      build_targets |= test_runner_invocation.get_test_runner_reqs()

    return build_targets

  def execute(self) -> ExitCode:

    reporter = result_reporter.ResultReporter(
        collect_only=self.extra_args.get(constants.COLLECT_TESTS_ONLY),
        wait_for_debugger=atest_configs.GLOBAL_ARGS.wait_for_debugger,
    )
    reporter.print_starting_text()

    exit_code = ExitCode.SUCCESS
    for invocation in self._test_runner_invocations:
      exit_code |= invocation.run_all_tests(reporter)

    atest_execution_info.AtestExecutionInfo.result_reporters.append(reporter)
    return reporter.print_summary() | exit_code


def _requires_device_update(invocations: List[TestRunnerInvocation]) -> bool:
  """Checks if any invocation requires device update."""
  return any(i.requires_device_update() for i in invocations)


if __name__ == '__main__':
  results_dir = make_test_run_dir()
  if END_OF_OPTION in sys.argv:
    end_position = sys.argv.index(END_OF_OPTION)
    final_args = [
        *sys.argv[1:end_position],
        *_get_args_from_config(),
        *sys.argv[end_position:],
    ]
  else:
    final_args = [*sys.argv[1:], *_get_args_from_config()]
  if final_args != sys.argv[1:]:
    print(
        'The actual cmd will be: \n\t{}\n'.format(
            atest_utils.mark_cyan('atest ' + ' '.join(final_args))
        )
    )
    metrics.LocalDetectEvent(detect_type=DetectType.ATEST_CONFIG, result=1)
    if HAS_IGNORED_ARGS:
      atest_utils.colorful_print(
          'Please correct the config and try again.', constants.YELLOW
      )
      sys.exit(ExitCode.EXIT_BEFORE_MAIN)
  else:
    metrics.LocalDetectEvent(detect_type=DetectType.ATEST_CONFIG, result=0)

  args = _parse_args(final_args)
  atest_configs.GLOBAL_ARGS = args
  _configure_logging(args.verbose, results_dir)

  logging.debug(
      'Start of atest run. sys.argv: %s, final_args: %s', sys.argv, final_args
  )

  with atest_execution_info.AtestExecutionInfo(
      final_args, results_dir, atest_configs.GLOBAL_ARGS
  ) as result_file:
    setup_metrics_tool_name(atest_configs.GLOBAL_ARGS.no_metrics)

    exit_code = _main(
        final_args,
        results_dir,
        atest_configs.GLOBAL_ARGS,
    )
    detector = bug_detector.BugDetector(final_args, exit_code)
    if exit_code not in EXIT_CODES_BEFORE_TEST:
      metrics.LocalDetectEvent(
          detect_type=DetectType.BUG_DETECTED, result=detector.caught_result
      )
      if result_file:
        print("Run 'atest --history' to review test result history.")
  banner = os.environ.get('ANDROID_BUILD_BANNER', None)
  skip_banner = os.environ.get('ANDROID_SKIP_BANNER', None)
  if banner and not skip_banner:
    print(banner)
  sys.exit(exit_code)
