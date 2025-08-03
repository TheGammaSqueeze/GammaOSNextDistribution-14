#!/usr/bin/env python3
#
# Copyright 2022, The Android Open Source Project
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

"""Integration tests for the Atest Bazel mode feature."""

# pylint: disable=invalid-name
# pylint: disable=missing-class-docstring
# pylint: disable=missing-function-docstring

import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from typing import Any, Dict
import unittest


class ResultCompareTest(unittest.TestCase):

  def setUp(self):
    self.src_root_path = Path(os.environ['ANDROID_BUILD_TOP'])
    self.out_dir_path = Path(tempfile.mkdtemp())
    self.test_env = self.setup_test_env()

  def tearDown(self):
    shutil.rmtree(self.out_dir_path)

  def test_standard_mode_and_bazel_mode_result_equal(self):
    standard_mode_result = self.get_test_result(
        shell_cmd='atest -c -m --host --host-unit-test-only'
    )

    bazel_mode_result = self.get_test_result(
        shell_cmd=(
            'atest -c --bazel-mode --host --host-unit-test-only '
            '--bazel-arg=--test_timeout=300'
        ),
        is_bazel_mode=True,
    )

    self.assert_test_result_equal(standard_mode_result, bazel_mode_result)

  def setup_test_env(self) -> Dict[str, Any]:
    test_env = {
        'PATH': os.environ['PATH'],
        'HOME': os.environ['HOME'],
        'OUT_DIR': str(self.out_dir_path),
    }
    return test_env

  def get_test_result(
      self,
      shell_cmd: str,
      is_bazel_mode: bool = False,
  ) -> Dict[str, str]:
    result_file_name = 'test_result'
    if is_bazel_mode:
      shell_cmd = (
          f'{shell_cmd} --bazel-arg=--build_event_json_file={result_file_name}'
      )

    completed_process = self.run_shell_command(shell_cmd)
    result_file_path = self.get_result_file_path(
        completed_process, result_file_name, is_bazel_mode
    )

    if is_bazel_mode:
      return parse_bazel_result(result_file_path)
    return parse_standard_result(result_file_path)

  def get_result_file_path(
      self,
      completed_process: subprocess.CompletedProcess,
      result_file_name: str,
      is_bazel_mode: bool = False,
  ) -> Path:
    if is_bazel_mode:
      return self.out_dir_path.joinpath(
          'atest_bazel_workspace', result_file_name
      )

    result_file_path = None
    for line in completed_process.stdout.decode().splitlines():
      if line.startswith('Test Logs have been saved in '):
        result_file_path = Path(
            re.sub('Test Logs have been saved in ', '', line).replace(
                'log', result_file_name
            )
        )
        break

    if not result_file_path:
      raise Exception('Could not find test result filepath')

    return result_file_path

  def run_shell_command(
      self,
      shell_command: str,
  ) -> subprocess.CompletedProcess:
    return subprocess.run(
        '. build/envsetup.sh && '
        'lunch aosp_cf_x86_64_pc-userdebug && '
        f'{shell_command}',
        env=self.test_env,
        cwd=self.src_root_path,
        shell=True,
        check=False,
        stderr=subprocess.STDOUT,
        stdout=subprocess.PIPE,
    )

  def assert_test_result_equal(self, result1, result2):
    self.assertEqual(set(result1.keys()), set(result2.keys()))

    print(
        '{0:100}  {1:20}  {2}'.format(
            'Test', 'Atest Standard Mode', 'Atest Bazel Mode'
        )
    )
    count = 0
    for k, v in result1.items():
      if v != result2[k]:
        count += 1
        print('{0:100}  {1:20}  {2}'.format(k, v, result2[k]))
    print(
        f'Total Number of Host Unit Test: {len(result1)}. {count} tests '
        'have different results.'
    )

    self.assertEqual(count, 0)


def parse_standard_result(result_file: Path) -> Dict[str, str]:
  result = {}
  with result_file.open('r') as f:
    json_result = json.loads(f.read())
    for k, v in json_result['test_runner']['AtestTradefedTestRunner'].items():
      name = k.split()[-1]
      if name in result:
        raise Exception(f'Duplicated Test Target: `{name}`')

      # Test passed when there are no failed test cases and no errors.
      result[name] = (
          'PASSED'
          if v['summary']['FAILED'] == 0 and not v.get('ERROR')
          else 'FAILED'
      )
  return result


def parse_bazel_result(result_file: Path) -> Dict[str, str]:
  result = {}
  with result_file.open('r') as f:
    content = f.read()
    events = content.splitlines()

    for e in events:
      json_event = json.loads(e)
      if 'testSummary' in json_event['id']:
        name = (
            json_event['id']['testSummary']['label']
            .split(':')[-1]
            .removesuffix('_host')
        )
        result[name] = json_event['testSummary']['overallStatus']
  return result


if __name__ == '__main__':
  unittest.main(verbosity=2)
