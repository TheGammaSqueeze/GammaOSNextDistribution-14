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

import dataclasses
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from typing import Any, Dict, List, Tuple
import unittest


_ENV_BUILD_TOP = 'ANDROID_BUILD_TOP'
_PASSING_CLASS_NAME = 'PassingHostTest'
_FAILING_CLASS_NAME = 'FailingHostTest'
_PASSING_METHOD_NAME = 'testPass'
_FAILING_METHOD_NAME = 'testFAIL'


@dataclasses.dataclass(frozen=True)
class JavaSourceFile:
  class_name: str
  src_body: str


class BazelModeTest(unittest.TestCase):

  def setUp(self):
    self.src_root_path = Path(os.environ['ANDROID_BUILD_TOP'])
    self.test_dir = self.src_root_path.joinpath('atest_bazel_mode_test')
    if self.test_dir.exists():
      shutil.rmtree(self.test_dir)
    self.out_dir_path = Path(tempfile.mkdtemp())
    self.test_env = self.setup_test_env()

  def tearDown(self):
    shutil.rmtree(self.test_dir)
    shutil.rmtree(self.out_dir_path)

  def test_passing_test_returns_zero_exit_code(self):
    module_name = 'passing_java_host_test'
    self.add_passing_test(module_name)

    completed_process = self.run_shell_command(
        f'atest -c -m --bazel-mode {module_name}'
    )

    self.assertEqual(completed_process.returncode, 0)

  def test_failing_test_returns_nonzero_exit_code(self):
    module_name = 'failing_java_host_test'
    self.add_failing_test(module_name)

    completed_process = self.run_shell_command(
        f'atest -c -m --bazel-mode {module_name}'
    )

    self.assertNotEqual(completed_process.returncode, 0)

  def test_passing_test_is_cached_when_rerun(self):
    module_name = 'passing_java_host_test'
    self.add_passing_test(module_name)

    completed_process = self.run_shell_command(
        f'atest -c -m --bazel-mode {module_name} && '
        f'atest --bazel-mode {module_name}'
    )

    self.assert_in_stdout(
        f':{module_name}_host (cached) PASSED', completed_process
    )

  def test_cached_test_reruns_when_modified(self):
    module_name = 'passing_java_host_test'
    java_test_file, _ = self.write_java_test_module(
        module_name, passing_java_test_source()
    )
    self.run_shell_command(f'atest -c -m --bazel-mode {module_name}')

    java_test_file.write_text(
        failing_java_test_source(test_class_name=_PASSING_CLASS_NAME).src_body
    )
    completed_process = self.run_shell_command(
        f'atest --bazel-mode {module_name}'
    )

    self.assert_in_stdout(f':{module_name}_host FAILED', completed_process)

  def test_only_supported_test_run_with_bazel(self):
    module_name = 'passing_java_host_test'
    unsupported_module_name = 'unsupported_passing_java_test'
    self.add_passing_test(module_name)
    self.add_unsupported_passing_test(unsupported_module_name)

    completed_process = self.run_shell_command(
        f'atest -c -m --host --bazel-mode {module_name} '
        f'{unsupported_module_name}'
    )

    self.assert_in_stdout(f':{module_name}_host PASSED', completed_process)
    self.assert_in_stdout(
        f'{_PASSING_CLASS_NAME}#{_PASSING_METHOD_NAME}: PASSED',
        completed_process,
    )

  def test_defaults_to_device_variant(self):
    module_name = 'passing_cc_host_test'
    self.write_cc_test_module(module_name, passing_cc_test_source())

    completed_process = self.run_shell_command(
        f'atest -c -m --bazel-mode {module_name}'
    )

    self.assert_in_stdout('AtestTradefedTestRunner:', completed_process)

  def test_runs_host_variant_when_requested(self):
    module_name = 'passing_cc_host_test'
    self.write_cc_test_module(module_name, passing_cc_test_source())

    completed_process = self.run_shell_command(
        f'atest -c -m --host --bazel-mode {module_name}'
    )

    self.assert_in_stdout(f':{module_name}_host   PASSED', completed_process)

  def test_ignores_host_arg_for_device_only_test(self):
    module_name = 'passing_cc_device_test'
    self.write_cc_test_module(
        module_name, passing_cc_test_source(), host_supported=False
    )

    completed_process = self.run_shell_command(
        f'atest -c -m --host --bazel-mode {module_name}'
    )

    self.assert_in_stdout(
        'Specified --host, but the following tests are device-only',
        completed_process,
    )

  def test_supports_extra_tradefed_reporters(self):
    test_module_name = 'passing_java_host_test'
    self.add_passing_test(test_module_name)

    reporter_module_name = 'test-result-reporter'
    reporter_class_name = 'TestResultReporter'
    expected_output_string = '0xFEEDF00D'

    self.write_java_reporter_module(
        reporter_module_name,
        java_reporter_source(reporter_class_name, expected_output_string),
    )

    self.run_shell_command(f'm {reporter_module_name}', check=True)
    self.run_shell_command(
        f'atest -c -m --bazel-mode {test_module_name} --dry-run', check=True
    )
    self.run_shell_command(
        f'cp ${{ANDROID_HOST_OUT}}/framework/{reporter_module_name}.jar '
        f'{self.out_dir_path}/atest_bazel_workspace/tools/asuite/atest/'
        'bazel/reporter/bazel-result-reporter/host/framework/.',
        check=True,
    )

    completed_process = self.run_shell_command(
        f'atest --bazel-mode {test_module_name} --bazel-arg='
        '--//bazel/rules:extra_tradefed_result_reporters=android.'
        f'{reporter_class_name} --bazel-arg=--test_output=all',
        check=True,
    )

    self.assert_in_stdout(expected_output_string, completed_process)

  def setup_test_env(self) -> Dict[str, Any]:
    test_env = {
        'PATH': os.environ['PATH'],
        'HOME': os.environ['HOME'],
        'OUT_DIR': str(self.out_dir_path),
    }
    return test_env

  def run_shell_command(
      self, shell_command: str, check: bool = False
  ) -> subprocess.CompletedProcess:
    return subprocess.run(
        '. build/envsetup.sh && '
        'lunch aosp_cf_x86_64_pc-userdebug && '
        f'{shell_command}',
        env=self.test_env,
        cwd=self.src_root_path,
        shell=True,
        check=check,
        stderr=subprocess.STDOUT,
        stdout=subprocess.PIPE,
    )

  def add_passing_test(self, module_name: str):
    self.write_java_test_module(module_name, passing_java_test_source())

  def add_failing_test(self, module_name: str):
    self.write_java_test_module(module_name, failing_java_test_source())

  def add_unsupported_passing_test(self, module_name: str):
    self.write_java_test_module(
        module_name, passing_java_test_source(), unit_test=False
    )

  def write_java_test_module(
      self,
      module_name: str,
      test_src: JavaSourceFile,
      unit_test: bool = True,
  ) -> Tuple[Path, Path]:
    test_dir = self.test_dir.joinpath(module_name)
    test_dir.mkdir(parents=True, exist_ok=True)

    src_file_name = f'{test_src.class_name}.java'
    src_file_path = test_dir.joinpath(f'{src_file_name}')
    src_file_path.write_text(test_src.src_body, encoding='utf8')

    bp_file_path = test_dir.joinpath('Android.bp')
    bp_file_path.write_text(
        android_bp(
            java_test_host(
                name=module_name,
                srcs=[
                    str(src_file_name),
                ],
                unit_test=unit_test,
            ),
        ),
        encoding='utf8',
    )
    return (src_file_path, bp_file_path)

  def write_cc_test_module(
      self,
      module_name: str,
      test_src: str,
      host_supported: bool = True,
  ) -> Tuple[Path, Path]:
    test_dir = self.test_dir.joinpath(module_name)
    test_dir.mkdir(parents=True, exist_ok=True)

    src_file_name = f'{module_name}.cpp'
    src_file_path = test_dir.joinpath(f'{src_file_name}')
    src_file_path.write_text(test_src, encoding='utf8')

    bp_file_path = test_dir.joinpath('Android.bp')
    bp_file_path.write_text(
        android_bp(
            cc_test(
                name=module_name,
                srcs=[
                    str(src_file_name),
                ],
                host_supported=host_supported,
            ),
        ),
        encoding='utf8',
    )
    return (src_file_path, bp_file_path)

  def write_java_reporter_module(
      self,
      module_name: str,
      reporter_src: JavaSourceFile,
  ) -> Tuple[Path, Path]:
    test_dir = self.test_dir.joinpath(module_name)
    test_dir.mkdir(parents=True, exist_ok=True)

    src_file_name = f'{reporter_src.class_name}.java'
    src_file_path = test_dir.joinpath(f'{src_file_name}')
    src_file_path.write_text(reporter_src.src_body, encoding='utf8')

    bp_file_path = test_dir.joinpath('Android.bp')
    bp_file_path.write_text(
        android_bp(
            java_library(
                name=module_name,
                srcs=[
                    str(src_file_name),
                ],
            ),
        ),
        encoding='utf8',
    )
    return (src_file_path, bp_file_path)

  def assert_in_stdout(
      self,
      message: str,
      completed_process: subprocess.CompletedProcess,
  ):
    self.assertIn(message, completed_process.stdout.decode())


def passing_java_test_source() -> JavaSourceFile:
  return java_test_source(
      test_class_name=_PASSING_CLASS_NAME,
      test_method_name=_PASSING_METHOD_NAME,
      test_method_body='Assert.assertEquals("Pass", "Pass");',
  )


def failing_java_test_source(
    test_class_name=_FAILING_CLASS_NAME,
) -> JavaSourceFile:
  return java_test_source(
      test_class_name=test_class_name,
      test_method_name=_FAILING_METHOD_NAME,
      test_method_body='Assert.assertEquals("Pass", "Fail");',
  )


def java_test_source(
    test_class_name: str,
    test_method_name: str,
    test_method_body: str,
) -> JavaSourceFile:
  return JavaSourceFile(
      test_class_name,
      f"""\
package android;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runners.JUnit4;
import org.junit.runner.RunWith;

@RunWith(JUnit4.class)
public final class {test_class_name} {{

    @Test
    public void {test_method_name}() {{
        {test_method_body}
    }}
}}
""",
  )


def java_reporter_source(
    reporter_class_name: str,
    output_string: str,
) -> JavaSourceFile:
  return JavaSourceFile(
      reporter_class_name,
      f"""\
package android;

import com.android.tradefed.result.ITestInvocationListener;

public final class {reporter_class_name} implements ITestInvocationListener {{

    @Override
    public void invocationEnded(long elapsedTime) {{
        System.out.println("{output_string}");
    }}
}}
""",
  )


def passing_cc_test_source() -> str:
  return cc_test_source(
      test_suite_name='TestSuite', test_name='PassingTest', test_body=''
  )


def cc_test_source(
    test_suite_name: str,
    test_name: str,
    test_body: str,
) -> str:
  return f"""\
#include <gtest/gtest.h>

TEST({test_suite_name}, {test_name}) {{
    {test_body}
}}
"""


def android_bp(
    modules: str = '',
) -> str:
  return f"""\
package {{
    default_applicable_licenses: ["Android-Apache-2.0"],
}}

{modules}
"""


def cc_test(
    name: str,
    srcs: List[str],
    host_supported: bool,
) -> str:
  src_files = ',\n'.join([f'"{f}"' for f in srcs])

  return f"""\
cc_test {{
    name: "{name}",
    srcs: [
        {src_files},
    ],
    test_options: {{
        unit_test: true,
    }},
    host_supported: {str(host_supported).lower()},
}}
"""


def java_test_host(
    name: str,
    srcs: List[str],
    unit_test: bool,
) -> str:
  src_files = ',\n'.join([f'"{f}"' for f in srcs])

  return f"""\
java_test_host {{
    name: "{name}",
    srcs: [
        {src_files},
    ],
    test_options: {{
        unit_test: {str(unit_test).lower()},
    }},
    static_libs: [
        "junit",
    ],
}}
"""


def java_library(
    name: str,
    srcs: List[str],
) -> str:
  src_files = ',\n'.join([f'"{f}"' for f in srcs])

  return f"""\
java_library_host {{
    name: "{name}",
    srcs: [
        {src_files},
    ],
    libs: [
        "tradefed",
    ],
}}
"""


if __name__ == '__main__':
  unittest.main(verbosity=2)
