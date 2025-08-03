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

"""Module to facilitate integration test within the build and test environment.

This module provides utilities for running tests in both build and test
environments, managing environment variables, and snapshotting the workspace for
restoration later.
"""

import argparse
import atexit
import concurrent.futures
import copy
import datetime
import functools
import itertools
import logging
import multiprocessing
import os
import pathlib
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
import traceback
from typing import Any, Callable, Iterator
import unittest
import zipfile

from snapshot import Snapshot

# Env key for the storage tar path.
SNAPSHOT_STORAGE_TAR_KEY = 'SNAPSHOT_STORAGE_TAR_PATH'

# Env key for the repo root
ANDROID_BUILD_TOP_KEY = 'ANDROID_BUILD_TOP'


class IntegrationTestConfiguration:
  """Internal class to store integration test configuration."""

  device_serial: str = None
  is_build_env: bool = False
  is_test_env: bool = False
  is_device_serial_required = True
  snapshot_storage_path: pathlib.Path = None
  snapshot_storage_tar_path: pathlib.Path = None
  workspace_path: pathlib.Path = None
  is_tar_snapshot: bool = False


class StepInput:
  """Input information for a build/test step."""

  def __init__(self, env, repo_root, config, objs):
    self._env = env
    self._repo_root = repo_root
    self._config = config
    self._objs = objs

  def get_device_serial_args_or_empty(self) -> str:
    """Gets command arguments for device serial. May return empty string."""
    if self._config.device_serial:
      return ' -s ' + self._config.device_serial
    if self._config.is_device_serial_required:
      raise RuntimeError('Device serial is required but not set')
    return ''

  def get_device_serial(self) -> str:
    """Returns the serial of the connected device. Throws if not set."""
    if not self._config.device_serial:
      raise RuntimeError('Device serial is not set')
    return self._config.device_serial

  def get_env(self):
    """Get environment variables."""
    return self._env

  def get_repo_root(self) -> str:
    """Get repo root directory."""
    return self._repo_root

  def get_obj(self, name: str) -> Any:
    """Get an object saved in previous snapshot."""
    return self._objs.get(name, None)

  def get_config(self) -> IntegrationTestConfiguration:
    """Get the integration test configuration."""
    return self._config


class StepOutput:
  """Output information generated from a build step."""

  def __init__(self):
    self._snapshot_include_paths: list[str] = []
    self._snapshot_exclude_paths: list[str] = []
    self._snapshot_env_keys: list[str] = []
    self._snapshot_objs: dict[str, Any] = {}

  def add_snapshot_include_paths(self, paths: list[str]) -> None:
    """Add paths to include in snapshot artifacts."""
    self._snapshot_include_paths.extend(paths)

  def set_snapshot_include_paths(self, paths: list[str]) -> None:
    """Set the snapshot include paths.

    Note that the default include paths will be removed.
    Use add_snapshot_include_paths if that's not intended.

    Args:
        paths: The new list of paths to include for snapshot.
    """
    self._snapshot_include_paths.clear()
    self._snapshot_include_paths.extend(paths)

  def add_snapshot_exclude_paths(self, paths: list[str]) -> None:
    """Add paths to exclude from snapshot artifacts."""
    self._snapshot_exclude_paths.extend(paths)

  def add_snapshot_env_keys(self, keys: list[str]) -> None:
    """Add environment variable keys for snapshot."""
    self._snapshot_env_keys.extend(keys)

  def add_snapshot_obj(self, name: str, obj: Any):
    """Add objects to save in snapshot."""
    self._snapshot_objs[name] = obj

  def get_snapshot_include_paths(self):
    """Returns the stored snapshot include path list."""
    return self._snapshot_include_paths

  def get_snapshot_exclude_paths(self):
    """Returns the stored snapshot exclude path list."""
    return self._snapshot_exclude_paths

  def get_snapshot_env_keys(self):
    """Returns the stored snapshot env key list."""
    return self._snapshot_env_keys

  def get_snapshot_objs(self):
    """Returns the stored snapshot object dictionary."""
    return self._snapshot_objs


class SplitBuildTestScript:
  """Utility for running integration test in build and test environment."""

  def __init__(self, name: str, config: IntegrationTestConfiguration) -> None:
    self._config = config
    self._id: str = name
    self._snapshot: Snapshot = Snapshot(self._config.snapshot_storage_path)
    self._has_already_run: bool = False
    self._steps: list[self._Step] = []
    self._snapshot_restore_exclude_paths: list[str] = []

  def add_build_step(self, step_func: Callable[StepInput, StepOutput]):
    """Add a build step.

    Args:
        step_func: A function that takes a StepInput object and returns a
          StepOutput object.

    Raises:
        RuntimeError: Unexpected step orders detected.
    """
    if self._steps and isinstance(self._steps[-1], self._BuildStep):
      raise RuntimeError(
          'Two adjacent build steps are unnecessary. Combine them.'
      )
    self._steps.append(self._BuildStep(step_func))

  def add_test_step(self, step_func: Callable[StepInput, None]):
    """Add a test step.

    Args:
        step_func: A function that takes a StepInput object.

    Raises:
        RuntimeError: Unexpected step orders detected.
    """
    if not self._steps or isinstance(self._steps[-1], self._TestStep):
      raise RuntimeError('A build step is required before a test step.')
    self._steps.append(self._TestStep(step_func))

  def _exception_to_dict(self, exception: Exception):
    """Converts an exception object to a dictionary to be saved by json."""
    return {
        'type': exception.__class__.__name__,
        'message': str(exception),
        'traceback': ''.join(traceback.format_tb(exception.__traceback__)),
    }

  def _dict_to_exception(self, exception_dict: dict[str, str]):
    """Converts a dictionary to an exception object."""
    return RuntimeError(
        'The last build step raised an exception:\n'
        f'{exception_dict["type"]}: {exception_dict["message"]}\n'
        'Traceback (from saved snapshot):\n'
        f'{exception_dict["traceback"]}'
    )

  def run(self):
    """Run the steps added previously.

    This function cannot be executed more than once.
    Raises:
        RuntimeError: When attempted to run the script multiple times.
    """
    if self._has_already_run:
      raise RuntimeError(f'Script {self.name} has already run.')
    self._has_already_run = True

    build_step_exception_key = '_internal_build_step_exception'

    for index, step in enumerate(self._steps):
      if isinstance(step, self._BuildStep) and self._config.is_build_env:
        env = os.environ
        step_in = StepInput(
            env,
            self._get_repo_root(os.environ),
            self._config,
            {},
        )
        last_exception = None
        try:
          step_out = step.get_step_func()(step_in)
        # pylint: disable=broad-exception-caught
        except Exception as e:
          last_exception = e
          step_out = StepOutput()
          step_out.add_snapshot_obj(
              build_step_exception_key, self._exception_to_dict(e)
          )

        self._take_snapshot(
            self._get_repo_root(os.environ),
            self._id + '_' + str(index // 2),
            step_out,
            env,
        )

        if last_exception:
          raise last_exception

      if isinstance(step, self._TestStep) and self._config.is_test_env:
        env, objs = self._restore_snapshot(self._id + '_' + str(index // 2))

        if build_step_exception_key in objs:
          raise self._dict_to_exception(objs[build_step_exception_key])

        step_in = StepInput(
            env,
            self._get_repo_root(env),
            self._config,
            objs,
        )
        step.get_step_func()(step_in)

  def add_snapshot_restore_exclude_paths(self, paths: list[str]) -> None:
    """Add paths to ignore during snapshot directory restore."""
    self._snapshot_restore_exclude_paths.extend(paths)

  def _take_snapshot(
      self,
      repo_root: str,
      name: str,
      step_out: StepOutput,
      env: dict[str, str],
  ) -> None:
    """Take a snapshot of the repository and environment."""
    self._snapshot.take_snapshot(
        name,
        repo_root,
        include_paths=step_out.get_snapshot_include_paths(),
        exclude_paths=step_out.get_snapshot_exclude_paths(),
        env_keys=step_out.get_snapshot_env_keys(),
        env=env,
        objs=step_out.get_snapshot_objs(),
    )

  def _restore_snapshot(self, name: str) -> None:
    """Restore the repository and environment from a snapshot."""
    return self._snapshot.restore_snapshot(
        name,
        self._config.workspace_path.as_posix(),
        exclude_paths=self._snapshot_restore_exclude_paths,
    )

  def _get_repo_root(self, env) -> str:
    """Get repo root directory."""
    if self._config.is_build_env:
      return os.environ[ANDROID_BUILD_TOP_KEY]
    return env[ANDROID_BUILD_TOP_KEY]

  class _Step:
    """Parent class to build step and test step for typing declaration."""

  class _BuildStep(_Step):

    def __init__(self, step_func: Callable[StepInput, StepOutput]):
      self._step_func = step_func

    def get_step_func(self) -> Callable[StepInput, StepOutput]:
      """Returns the stored step function for build."""
      return self._step_func

  class _TestStep(_Step):

    def __init__(self, step_func: Callable[StepInput, None]):
      self._step_func = step_func

    def get_step_func(self) -> Callable[StepInput, None]:
      """Returns the stored step function for test."""
      return self._step_func


class SplitBuildTestTestCase(unittest.TestCase):
  """Base test case class for split build-test scripting tests."""

  # Internal config to be injected to the test case from main.
  injected_config: IntegrationTestConfiguration = None

  def create_split_build_test_script(
      self, name: str = None
  ) -> SplitBuildTestScript:
    """Return an instance of SplitBuildTestScript with the given name.

    Args:
        name: The name of the script. The name will be used to store snapshots
          and it's recommended to set the name to test id such as self.id().
          Defaults to the test id if not set.
    """
    if not name:
      name = self.id()
      main_module_name = '__main__'
      if name.startswith(main_module_name):
        script_name = pathlib.Path(sys.modules[main_module_name].__file__).stem
        name = name.replace(main_module_name, script_name)
    return SplitBuildTestScript(name, self.injected_config)


class _FileCompressor:
  """Class for compressing and decompressing files."""

  def compress_all_sub_files(self, root_path: pathlib.Path) -> None:
    """Compresses all files in the given directory and subdirectories.

    Args:
        root_path: The path to the root directory.
    """
    cpu_count = multiprocessing.cpu_count()
    with concurrent.futures.ThreadPoolExecutor(
        max_workers=cpu_count
    ) as executor:
      for file_path in root_path.rglob('*'):
        if file_path.is_file():
          executor.submit(self.compress_file, file_path)

  def compress_file(self, file_path: pathlib.Path) -> None:
    """Compresses a single file to zip.

    Args:
        file_path: The path to the file to compress.
    """
    with zipfile.ZipFile(
        file_path.with_suffix('.zip'), 'w', zipfile.ZIP_DEFLATED
    ) as zip_file:
      zip_file.write(file_path, arcname=file_path.name)
    file_path.unlink()

  def decompress_all_sub_files(self, root_path: pathlib.Path) -> None:
    """Decompresses all compressed sub files in the given directory.

    Args:
        root_path: The path to the root directory.
    """
    cpu_count = multiprocessing.cpu_count()
    with concurrent.futures.ThreadPoolExecutor(
        max_workers=cpu_count
    ) as executor:
      for file_path in root_path.rglob('*.zip'):
        executor.submit(self.decompress_file, file_path)

  def decompress_file(self, file_path: pathlib.Path) -> None:
    """Decompresses a single zip file.

    Args:
        file_path: The path to the compressed file.
    """
    with zipfile.ZipFile(file_path, 'r') as zip_file:
      zip_file.extractall(file_path.parent)
    file_path.unlink()


class ParallelTestRunner(unittest.TextTestRunner):
  """A class that holds the logic of parallel test execution.

  Test methods wrapped by decorators defined in this class will be pre-executed
  at the beginning of the test run in parallel and have the results cached when
  the test runner is also this class. Available decorators: `run_in_parallel`
  for runnint test method in parallel during both build and test env,
  `run_in_parallel_in_build_env` for parallel run in build env only, and
  `run_in_parallel_in_test_env` for parallel run in test env only.
  """

  _RUN_IN_PARALLEL = 'run_in_parallel'
  _RUN_IN_PARALLEL_IN_BUILD_ENV = 'run_in_parallel_in_build_env'
  _RUN_IN_PARALLEL_IN_TEST_ENV = 'run_in_parallel_in_test_env'
  _DECORATOR_NAME = 'decorator_name'

  @classmethod
  def _cache_first(
      cls, func: Callable[[Any], Any], decorator_name: str
  ) -> Callable[[Any], Any]:
    """Cache a function's first call result and consumes it in the next call.

    This decorator is similar to the built-in `functools.cache` decorator except
    that this decorator caches the first call's run result and emit it in the
    next run of the function, regardless of the function's input argument value
    changes. Caching only the first call of the test ensures test retries emit
    fresh results.

    Args:
        func: The function to cache.
        decorator_name: The name of the decorator.

    Returns:
        The wrapped function with queue caching ability.
    """
    setattr(func, cls._DECORATOR_NAME, decorator_name)

    class _ResultCache:
      result = None
      is_to_be_cached = False

    result_cache = _ResultCache()

    @functools.wraps(func)
    def _wrapped(*args, only_set_next_run_caching=False, **kwargs):
      if only_set_next_run_caching:
        result_cache.is_to_be_cached = True
        return

      def _get_fresh_call_result():
        try:
          return (func(*args, **kwargs), None)
        # pylint: disable-next=broad-exception-caught
        except Exception as e:
          return (None, e)

      if result_cache.is_to_be_cached:
        result = _get_fresh_call_result()
        result_cache.result = result
        result_cache.is_to_be_cached = False
      elif result_cache.result:
        result = result_cache.result
        result_cache.result = None
      else:
        result = _get_fresh_call_result()
      if result[1]:
        raise result[1]
      return result[0]

    return _wrapped

  @classmethod
  def run_in_parallel(cls, func: Callable[[Any], Any]) -> Callable[[Any], Any]:
    """Hint that a test method can run in parallel."""
    return cls._cache_first(func, cls.run_in_parallel.__name__)

  @classmethod
  def run_in_parallel_in_build_env(
      cls, func: Callable[[Any], Any]
  ) -> Callable[[Any], Any]:
    """Hint that a test method can run in parallel in build env only."""
    return cls._cache_first(func, cls.run_in_parallel.__name__)

  @classmethod
  def run_in_parallel_in_test_env(
      cls, func: Callable[[Any], Any]
  ) -> Callable[[Any], Any]:
    """Hint that a test method can run in parallel in test env only."""
    return cls._cache_first(func, cls.run_in_parallel.__name__)

  def run(self, test):
    """Executes parallel tests first and then non-parallel tests."""
    for test_suite in test:
      self._pre_execute_parallel_tests(test_suite)
    return super().run(test)

  @staticmethod
  def _get_test_function(test: unittest.TestCase) -> Callable[Any, Any]:
    """Gets the test function from a TestCase class wrapped by unittest."""
    return getattr(test, test.id().split('.')[-1])

  @classmethod
  def _get_parallel_tests(
      cls, test_suite: unittest.TestSuite
  ) -> Iterator[unittest.TestCase]:
    """Returns a list of test cases to be run in parallel from a test suite."""
    and_combine = lambda *funcs: functools.reduce(
        lambda accu, func: lambda item: accu(item) and func(item), funcs
    )
    or_combine = lambda *funcs: functools.reduce(
        lambda accu, func: lambda item: accu(item) or func(item), funcs
    )
    is_decorated = lambda decorator, test: decorator.__name__ == getattr(
        cls._get_test_function(test),
        cls._DECORATOR_NAME,
        None,
    )
    is_parallel = functools.partial(is_decorated, cls.run_in_parallel)
    is_parallel_in_build = functools.partial(
        is_decorated, cls.run_in_parallel_in_build_env
    )
    is_parallel_in_test = functools.partial(
        is_decorated, cls.run_in_parallel_in_test_env
    )
    is_in_build_env = lambda test: test.injected_config.is_build_env
    is_in_test_env = lambda test: test.injected_config.is_test_env
    combined_filter = or_combine(
        and_combine(is_parallel_in_build, is_in_build_env),
        and_combine(is_parallel_in_test, is_in_test_env),
        is_parallel,
    )
    return filter(combined_filter, test_suite)

  @classmethod
  def _pre_execute_parallel_tests(cls, test_suite: unittest.TestSuite) -> None:
    """Pre-execute parallel tests in the test suite."""
    with concurrent.futures.ThreadPoolExecutor(
        max_workers=multiprocessing.cpu_count()
    ) as executor:

      def _execute_test(test):
        # We can't directly call test.run because the function would either not
        # know that it's being pre-executed or not know whether it's being
        # executed by this test runner. We can't call the test function directly
        # because setup and teardown would be missed. We can't set properties
        # of the test function here because the test function has already been
        # wrapped by unittest. The only way we can let the test function know
        # that it needs to cache the next run is to call the function with a
        # parameter first before calling the run method.
        cls._get_test_function(test).__func__(only_set_next_run_caching=True)
        return executor.submit(test.run)

      for class_name, class_group in itertools.groupby(
          cls._get_parallel_tests(test_suite),
          lambda obj: f'{obj.__class__.__module__}.{obj.__class__}',
      ):
        test_group = list(class_group)
        logging.info(
            'Pre-executing %s of %s tests in parallel...',
            len(test_group),
            class_name,
        )

        list(concurrent.futures.as_completed(map(_execute_test, test_group)))


def _configure_logging(verbose: bool, log_file_dir_path: pathlib.Path):
  """Configure the logger.

  Args:
      verbose: If true display DEBUG level logs on console.
      log_file_dir_path: A directory which stores the log file.
  """
  log_file = log_file_dir_path.joinpath('asuite_integration_tests.log')
  if log_file.exists():
    timestamp = datetime.datetime.now().strftime('%Y-%m-%d_%H:%M:%S')
    log_file = log_file_dir_path.joinpath(
        f'asuite_integration_tests_{timestamp}.log'
    )
  log_file.parent.mkdir(parents=True, exist_ok=True)

  log_format = '%(asctime)s %(filename)s:%(lineno)s:%(levelname)s: %(message)s'
  date_format = '%Y-%m-%d %H:%M:%S'
  logging.basicConfig(
      filename=log_file.as_posix(),
      level=logging.DEBUG,
      format=log_format,
      datefmt=date_format,
  )
  console = logging.StreamHandler()
  console.name = 'console'
  console.setLevel(logging.INFO)
  if verbose:
    console.setLevel(logging.DEBUG)
  console.setFormatter(logging.Formatter(log_format))
  logging.getLogger('').addHandler(console)


def _parse_known_args(
    argv: list[str],
    argparser_update_func: Callable[argparse.ArgumentParser, None] = None,
) -> tuple[argparse.Namespace, list[str]]:
  """Parse command line args and check required args being provided."""

  description = """A script to build and/or run the Asuite integration tests.
Usage examples:
   python <script_path>: Runs both the build and test steps.
   python <script_path> -b -t: Runs both the build and test steps.
   python <script_path> -b: Runs only the build steps.
   python <script_path> -t: Runs only the test steps.
"""

  parser = argparse.ArgumentParser(
      add_help=True,
      description=description,
      formatter_class=argparse.RawDescriptionHelpFormatter,
  )

  parser.add_argument(
      '-b',
      '--build',
      action='store_true',
      default=False,
      help=(
          'Run build steps. Can be set to true together with the test option.'
          ' If both build and test are unset, will run both steps.'
      ),
  )
  parser.add_argument(
      '-t',
      '--test',
      action='store_true',
      default=False,
      help=(
          'Run test steps. Can be set to true together with the build option.'
          ' If both build and test are unset, will run both steps.'
      ),
  )
  parser.add_argument(
      '--tar_snapshot',
      action='store_true',
      default=False,
      help=(
          'Whether to tar and untar the snapshot storage into/from a single'
          ' file.'
      ),
  )
  parser.add_argument(
      '-v',
      '--verbose',
      action='store_true',
      default=False,
      help='Whether to set log level to verbose.',
  )

  # The below flags are passed in by the TF Python test runner.
  parser.add_argument(
      '-s',
      '--serial',
      help=(
          'The device serial. Required in test mode when ANDROID_BUILD_TOP is'
          ' not set.'
      ),
  )
  parser.add_argument(
      '--test-output-file',
      help=(
          'The file in which to store the unit test results. This option is'
          ' usually set by TradeFed when running the script with python and'
          ' is optional during manual script execution.'
      ),
  )

  if argparser_update_func:
    argparser_update_func(parser)

  return parser.parse_known_args(argv)


def _run_test(
    config: IntegrationTestConfiguration,
    argv: list[str],
    test_output_file_path: str = None,
) -> None:
  """Execute integration tests with given test configuration."""

  compressor = _FileCompressor()

  def cleanup() -> None:
    if config.workspace_path.exists():
      shutil.rmtree(config.workspace_path)
    if config.snapshot_storage_path.exists():
      shutil.rmtree(config.snapshot_storage_path)

  if config.is_test_env and config.is_tar_snapshot:
    if not config.snapshot_storage_tar_path.exists():
      raise EnvironmentError(
          f'Snapshot tar {config.snapshot_storage_tar_path} does not'
          ' exist. Have you run the build mode with --tar_snapshot'
          ' option enabled?'
      )
    with tarfile.open(config.snapshot_storage_tar_path, 'r') as tar:
      tar.extractall(config.snapshot_storage_path.parent.as_posix())

    logging.info(
        'Decompressing the snapshot storage with %s threads...',
        multiprocessing.cpu_count(),
    )
    start_time = time.time()
    compressor.decompress_all_sub_files(config.snapshot_storage_path)
    logging.info(
        'Decompression finished in {:.2f} seconds'.format(
            time.time() - start_time
        )
    )

    atexit.register(cleanup)

  def unittest_main(stream=None):
    # Note that we use a type and not an instance for 'testRunner'
    # since TestProgram forwards its constructor arguments when creating
    # an instance of the runner type. Not doing so would require us to
    # make sure that the parameters passed to TestProgram are aligned
    # with those for creating a runner instance.
    class TestRunner(ParallelTestRunner):
      """Writes test results to the TF-provided file."""

      def __init__(self, *args: Any, **kwargs: Any) -> None:
        super().__init__(stream=stream, *args, **kwargs)

    class TestLoader(unittest.TestLoader):
      """Injects the test configuration to the test classes."""

      def loadTestsFromTestCase(self, *args, **kwargs):
        test_suite = super().loadTestsFromTestCase(*args, **kwargs)
        for test in test_suite:
          test.injected_config = config
        return test_suite

    # Setting verbosity is required to generate output that the TradeFed
    # test runner can parse.
    unittest.main(
        testRunner=TestRunner,
        verbosity=3,
        argv=argv,
        testLoader=TestLoader(),
        exit=config.is_test_env,
    )

  if test_output_file_path:
    pathlib.Path(test_output_file_path).parent.mkdir(exist_ok=True)

    with open(test_output_file_path, 'w', encoding='utf-8') as test_output_file:
      unittest_main(stream=test_output_file)
  else:
    unittest_main(stream=None)

  if config.is_build_env and config.is_tar_snapshot:
    logging.info(
        'Compressing the snapshot storage with %s threads...',
        multiprocessing.cpu_count(),
    )
    start_time = time.time()
    compressor.compress_all_sub_files(config.snapshot_storage_path)
    logging.info(
        'Compression finished in {:.2f} seconds'.format(
            time.time() - start_time
        )
    )

    with tarfile.open(config.snapshot_storage_tar_path, 'w') as tar:
      tar.add(
          config.snapshot_storage_path,
          arcname=config.snapshot_storage_path.name,
      )
    cleanup()


def main(
    argv: list[str] = None,
    make_before_build: list[str] = None,
    argparser_update_func: Callable[argparse.ArgumentParser, None] = None,
    config_update_function: Callable[
        [IntegrationTestConfiguration, argparse.Namespace], None
    ] = None,
) -> None:
  """Main method to start the integration tests.

  Args:
      argv: A list of arguments to parse.
      make_before_build: A list of targets to make before running build steps.
      argparser_update_func: A function that takes an ArgumentParser object and
        updates it.
      config_update_function: A function that takes a
        IntegrationTestConfiguration config and the parsed args to updates the
        config.

  Raises:
      EnvironmentError: When some environment variables are missing.
  """
  if not argv:
    argv = sys.argv
  if make_before_build is None:
    make_before_build = []

  args, unittest_argv = _parse_known_args(argv, argparser_update_func)

  snapshot_storage_dir_name = 'snapshot_storage'
  snapshot_storage_tar_name = 'snapshot.tar'

  integration_test_out_path = pathlib.Path(
      tempfile.gettempdir(),
      'asuite_integration_tests_%s'
      % pathlib.Path('~').expanduser().name.replace(' ', '_'),
  )

  if SNAPSHOT_STORAGE_TAR_KEY in os.environ:
    snapshot_storage_tar_path = pathlib.Path(
        os.environ[SNAPSHOT_STORAGE_TAR_KEY]
    )
    snapshot_storage_tar_path.parent.mkdir(parents=True, exist_ok=True)
  else:
    snapshot_storage_tar_path = integration_test_out_path.joinpath(
        snapshot_storage_tar_name
    )

  _configure_logging(args.verbose, snapshot_storage_tar_path.parent)

  logging.debug('The os environ is: %s', os.environ)

  # When the build or test is unset, assume it's a local run for both build
  # and test steps.
  is_build_test_unset = not args.build and not args.test
  config = IntegrationTestConfiguration()
  config.is_build_env = args.build or is_build_test_unset
  config.is_test_env = args.test or is_build_test_unset
  config.device_serial = args.serial
  config.snapshot_storage_path = integration_test_out_path.joinpath(
      snapshot_storage_dir_name
  )
  config.snapshot_storage_tar_path = snapshot_storage_tar_path
  config.workspace_path = integration_test_out_path.joinpath('workspace')
  # Device serial is not required during local run, and
  # ANDROID_BUILD_TOP_KEY env being available implies it's local run.
  config.is_device_serial_required = ANDROID_BUILD_TOP_KEY not in os.environ
  config.is_tar_snapshot = args.tar_snapshot

  if config_update_function:
    config_update_function(config, args)

  if config.is_build_env:
    if ANDROID_BUILD_TOP_KEY not in os.environ:
      raise EnvironmentError(
          f'Environment variable {ANDROID_BUILD_TOP_KEY} is required to'
          ' build the integration test.'
      )

    repo_root = os.environ[ANDROID_BUILD_TOP_KEY]

    total, used, free = shutil.disk_usage(repo_root)
    logging.debug(
        'Disk usage: Total: {:.2f} GB, Used: {:.2f} GB, Free: {:.2f} GB'.format(
            total / (1024**3), used / (1024**3), free / (1024**3)
        )
    )

    if 'OUT_DIR' in os.environ:
      out_dir = os.environ['OUT_DIR']
      if os.path.isabs(out_dir) and not pathlib.Path(out_dir).is_relative_to(
          repo_root
      ):
        raise EnvironmentError(
            f'$OUT_DIR {out_dir} not relative to the repo root'
            f' {repo_root} is not supported yet.'
        )
    elif 'HOST_OUT' in os.environ:
      out_dir = (
          pathlib.Path(os.environ['HOST_OUT']).relative_to(repo_root).parts[0]
      )
    else:
      out_dir = 'out'
    os.environ['OUT_DIR'] = out_dir

    for target in make_before_build:
      subprocess.check_call(
          f'build/soong/soong_ui.bash --make-mode {target}'.split(),
          cwd=repo_root,
      )

  if config.is_build_env ^ config.is_test_env:
    _run_test(config, unittest_argv, args.test_output_file)
    return

  build_config = copy.deepcopy(config)
  build_config.is_test_env = False

  test_config = copy.deepcopy(config)
  test_config.is_build_env = False

  _run_test(build_config, unittest_argv, args.test_output_file)
  _run_test(test_config, unittest_argv, args.test_output_file)
