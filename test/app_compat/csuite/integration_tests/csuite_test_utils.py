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
"""Utilities for C-Suite integration tests."""

import argparse
import contextlib
import logging
import os
import pathlib
import shlex
import shutil
import stat
import subprocess
import sys
import tempfile
from typing import Sequence, Text
import zipfile
import csuite_test

# Export symbols to reduce the number of imports tests have to list.
TestCase = csuite_test.TestCase  # pylint: disable=invalid-name
get_device_serial = csuite_test.get_device_serial

# Keep any created temporary directories for debugging test failures. The
# directories do not need explicit removal since they are created using the
# system's temporary-file facility.
_KEEP_TEMP_DIRS = False


class CSuiteHarness(contextlib.AbstractContextManager):
  """Interface class for interacting with the C-Suite harness.

  WARNING: Explicitly clean up created instances or use as a context manager.
  Not doing so will result in a ResourceWarning for the implicit cleanup which
  confuses the TradeFed Python test output parser.
  """

  def __init__(self):
    self._suite_dir = pathlib.Path(tempfile.mkdtemp(prefix='csuite'))
    logging.debug('Created harness directory: %s', self._suite_dir)

    with zipfile.ZipFile(_get_standalone_zip_path(), 'r') as f:
      f.extractall(self._suite_dir)

    # Add owner-execute permission on scripts since zip does not preserve them.
    self._launcher_binary = self._suite_dir.joinpath(
        'android-csuite/tools/csuite-tradefed')
    _add_owner_exec_permission(self._launcher_binary)

    self._testcases_dir = self._suite_dir.joinpath('android-csuite/testcases')

  def __exit__(self, unused_type, unused_value, unused_traceback):
    self.cleanup()

  def cleanup(self):
    if _KEEP_TEMP_DIRS:
      return
    shutil.rmtree(self._suite_dir, ignore_errors=True)


  def run_and_wait(self, flags: Sequence[Text]) -> subprocess.CompletedProcess:
    """Starts the Tradefed launcher and waits for it to complete."""

    env = os.environ.copy()

    # Unset environment variables that would cause the script to think it's in a
    # build tree.
    env.pop('ANDROID_BUILD_TOP', None)
    env.pop('ANDROID_HOST_OUT', None)

    # Unset environment variables that would cause TradeFed to find test configs
    # other than the ones created by the test.
    env.pop('ANDROID_HOST_OUT_TESTCASES', None)
    env.pop('ANDROID_TARGET_OUT_TESTCASES', None)

    # Unset environment variables that might cause the suite to pick up a
    # connected device that wasn't explicitly specified.
    env.pop('ANDROID_SERIAL', None)

    # Unset environment variables that might cause the TradeFed to load classes
    # that weren't included in the standalone suite zip.
    env.pop('TF_GLOBAL_CONFIG', None)
    # Ensure the process operates in standalone mode
    env['LOCAL_MODE'] = "1"
    # Set the environment variable that TradeFed requires to find test modules.
    env['ANDROID_TARGET_OUT_TESTCASES'] = self._testcases_dir
    jdk17_path = '/jdk/jdk17/linux-x86'
    if os.path.isdir(jdk17_path):
      env['JAVA_HOME'] = jdk17_path
      java_path = jdk17_path + '/bin'
      env['PATH'] = java_path + ':' + env['PATH']

    return _run_command([self._launcher_binary] + flags, env=env)


class PackageRepository(contextlib.AbstractContextManager):
  """A file-system based APK repository for use in tests.

  WARNING: Explicitly clean up created instances or use as a context manager.
  Not doing so will result in a ResourceWarning for the implicit cleanup which
  confuses the TradeFed Python test output parser.
  """

  def __init__(self):
    self._root_dir = pathlib.Path(tempfile.mkdtemp(prefix='csuite_apk_dir'))
    logging.info('Created repository directory: %s', self._root_dir)

  def __exit__(self, unused_type, unused_value, unused_traceback):
    self.cleanup()

  def cleanup(self):
    if _KEEP_TEMP_DIRS:
      return
    shutil.rmtree(self._root_dir, ignore_errors=True)

  def get_path(self) -> pathlib.Path:
    """Returns the path to the repository's root directory."""
    return self._root_dir

  def add_package_apks(self, package_name: Text,
                       apk_paths: Sequence[pathlib.Path]):
    """Adds the provided package APKs to the repository."""
    apk_dir = self._root_dir.joinpath(package_name)

    # Raises if the directory already exists.
    apk_dir.mkdir()
    for f in apk_paths:
      shutil.copy(f, apk_dir)


class Adb:
  """Encapsulates adb functionality to simplify usage in tests.

  Most methods in this class raise an exception if they fail to execute. This
  behavior can be overridden by using the check parameter.
  """

  def __init__(self,
               adb_binary_path: pathlib.Path = None,
               device_serial: Text = None):
    self._args = [adb_binary_path or 'adb']

    device_serial = device_serial or get_device_serial()
    if device_serial:
      self._args.extend(['-s', device_serial])

  def shell(self,
            args: Sequence[Text],
            check: bool = None) -> subprocess.CompletedProcess:
    """Runs an adb shell command and waits for it to complete.

    Note that the exit code of the returned object corresponds to that of
    the adb command and not the command executed in the shell.

    Args:
      args: a sequence of program arguments to pass to the shell.
      check: whether to raise if the process terminates with a non-zero exit
        code.

    Returns:
      An object representing a process that has finished and that can be
      queried.
    """
    return self.run(['shell'] + args, check)

  def run(self,
          args: Sequence[Text],
          check: bool = None) -> subprocess.CompletedProcess:
    """Runs an adb command and waits for it to complete."""
    return _run_command(self._args + args, check=check)

  def uninstall(self, package_name: Text, check: bool = None):
    """Uninstalls the specified package."""
    self.run(['uninstall', package_name], check=check)

  def list_packages(self) -> Sequence[Text]:
    """Lists packages installed on the device."""
    p = self.shell(['pm', 'list', 'packages'])
    return [l.split(':')[1] for l in p.stdout.splitlines()]


def _run_command(args, check=False, **kwargs) -> subprocess.CompletedProcess:
  """A wrapper for subprocess.run that overrides defaults and adds logging."""
  env = kwargs.get('env', {})

  # Log the command-line for debugging failed tests. Note that we convert
  # tokens to strings for _shlex_join.
  env_str = ['env', '-i'] + [f'{k}={v}' for k, v in env.items()]
  args_str = [str(t) for t in args]

  # Override some defaults. Note that 'check' deviates from this pattern to
  # avoid getting warnings about using subprocess.run without an explicitly set
  # `check` parameter.
  kwargs.setdefault('capture_output', True)
  kwargs.setdefault('universal_newlines', True)

  logging.debug('Running command: %s', _shlex_join(env_str + args_str))

  return subprocess.run(args, check=check, **kwargs)


def _add_owner_exec_permission(path: pathlib.Path):
  path.chmod(path.stat().st_mode | stat.S_IEXEC)


def get_test_app_apks(app_module_name: Text) -> Sequence[pathlib.Path]:
  """Returns a test app's apk file paths."""
  return [_get_test_file(app_module_name + '.apk')]


def _get_standalone_zip_path():
  """Returns the suite standalone zip file's path."""
  return _get_test_file('csuite-standalone.zip')


def _get_test_file(name: Text) -> pathlib.Path:
  test_dir = _get_test_dir()
  test_file = test_dir.joinpath(name)

  if not test_file.exists():
    raise RuntimeError(f'Unable to find the file `{name}` in the test '
                       'execution dir `{test_dir}`; are you missing a data '
                       'dependency in the build module?')

  return test_file


def _shlex_join(split_command: Sequence[Text]) -> Text:
  """Concatenate tokens and return a shell-escaped string."""
  # This is an alternative to shlex.join that doesn't exist in Python versions
  # < 3.8.
  return ' '.join(shlex.quote(t) for t in split_command)


def _get_test_dir() -> pathlib.Path:
  return pathlib.Path(__file__).parent


def main():
  global _KEEP_TEMP_DIRS

  parser = argparse.ArgumentParser(parents=[csuite_test.create_arg_parser()])
  parser.add_argument(
      '--log-level',
      choices=['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL'],
      default='WARNING',
      help='sets the logging level threshold')
  parser.add_argument(
      '--keep-temp-dirs',
      type=bool,
      help='keeps any created temporary directories for debugging failures')
  args, unittest_argv = parser.parse_known_args(sys.argv)

  _KEEP_TEMP_DIRS = args.keep_temp_dirs
  logging.basicConfig(level=getattr(logging, args.log_level))

  csuite_test.run_tests(args, unittest_argv)
