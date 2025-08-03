#!/usr/bin/env python3
#
# Copyright 2021, The Android Open Source Project
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

"""Unit tests for bazel_mode."""
# pylint: disable=invalid-name
# pylint: disable=missing-function-docstring
# pylint: disable=too-many-lines

import argparse
from io import StringIO
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import tempfile
from typing import List
import unittest
from unittest import mock
from atest import bazel_mode
from atest import constants
from atest import module_info
from atest.test_finders import example_finder, test_finder_base, test_info
from atest.test_runners import atest_tf_test_runner
from pyfakefs import fake_filesystem_unittest


ATEST_TF_RUNNER = atest_tf_test_runner.AtestTradefedTestRunner.NAME
BAZEL_RUNNER = bazel_mode.BazelTestRunner.NAME
MODULE_BUILD_TARGETS = {'foo1', 'foo2', 'foo3'}
MODULE_NAME = 'foo'


class GenerationTestFixture(fake_filesystem_unittest.TestCase):
  """Fixture for workspace generation tests."""

  def setUp(self):
    self.setUpPyfakefs()

    self._src_root_path = Path('/src')
    self.out_dir_path = self._src_root_path.joinpath('out')
    self.out_dir_path.mkdir(parents=True)
    self.product_out_path = self.out_dir_path.joinpath('product')
    self.host_out_path = self.out_dir_path.joinpath('host')
    self.workspace_out_path = self.out_dir_path.joinpath('workspace')

    self._resource_root = self._src_root_path.joinpath(
        'tools/asuite/atest/bazel'
    )

    self.workspace_md5_checksum = self.workspace_out_path.joinpath(
        'workspace_md5_checksum'
    )
    self.resource_manager = bazel_mode.ResourceManager(
        src_root_path=self._src_root_path,
        resource_root_path=self._resource_root,
        product_out_path=self.product_out_path,
        md5_checksum_file_path=self.workspace_md5_checksum,
    )

    bazel_rules = self.resource_manager.get_resource_file_path('rules')
    bazel_rules.mkdir(parents=True)
    self.rules_bzl_file = bazel_rules.joinpath('rules.bzl')
    self.rules_bzl_file.touch()

    bazel_configs = self.resource_manager.get_resource_file_path('configs')
    bazel_configs.mkdir(parents=True)
    bazel_configs.joinpath('configs.bzl').touch()

    self.resource_manager.get_resource_file_path('WORKSPACE').touch()
    self.resource_manager.get_resource_file_path('bazelrc').touch()

    rules_python = self.resource_manager.get_src_file_path(
        'build/bazel_common_rules/rules/python/stubs'
    )
    rules_python.mkdir(parents=True)
    rules_java = self.resource_manager.get_src_file_path(
        'external/bazelbuild-rules_java'
    )
    rules_java.mkdir(parents=True)

  def create_workspace_generator(
      self,
      modules=None,
      enabled_features=None,
      jdk_path=None,
  ):
    mod_info = self.create_module_info(modules)

    generator = bazel_mode.WorkspaceGenerator(
        resource_manager=self.resource_manager,
        workspace_out_path=self.workspace_out_path,
        host_out_path=self.host_out_path,
        build_out_dir=self.out_dir_path,
        mod_info=mod_info,
        jdk_path=jdk_path,
        enabled_features=enabled_features,
    )

    return generator

  def run_generator(self, mod_info, enabled_features=None, jdk_path=None):
    generator = bazel_mode.WorkspaceGenerator(
        resource_manager=self.resource_manager,
        workspace_out_path=self.workspace_out_path,
        host_out_path=self.host_out_path,
        build_out_dir=self.out_dir_path,
        mod_info=mod_info,
        jdk_path=jdk_path,
        enabled_features=enabled_features,
    )

    generator.generate()

  # pylint: disable=protected-access
  def create_empty_module_info(self):
    fake_temp_file = self.product_out_path.joinpath(
        next(tempfile._get_candidate_names())
    )
    self.fs.create_file(fake_temp_file, contents='{}')
    return module_info.load_from_file(module_file=fake_temp_file)

  def create_module_info(self, modules=None):
    mod_info = self.create_empty_module_info()
    modules = modules or []

    prerequisites = frozenset().union(
        bazel_mode.TestTarget.DEVICE_TEST_PREREQUISITES,
        bazel_mode.TestTarget.DEVICELESS_TEST_PREREQUISITES,
    )

    for module_name in prerequisites:
      info = host_module(name=module_name, path='prebuilts')
      info[constants.MODULE_INFO_ID] = module_name
      mod_info.name_to_module_info[module_name] = info

    for m in modules:
      m[constants.MODULE_INFO_ID] = m['module_name']
      mod_info.name_to_module_info[m['module_name']] = m
      for path in m['path']:
        if path in mod_info.path_to_module_info:
          mod_info.path_to_module_info[path].append(m)
        else:
          mod_info.path_to_module_info[path] = [m]

    return mod_info

  def assertSymlinkTo(self, symlink_path, target_path):
    self.assertEqual(symlink_path.resolve(strict=False), target_path)

  def assertTargetInWorkspace(self, name, package=''):
    build_file = self.workspace_out_path.joinpath(package, 'BUILD.bazel')
    contents = build_file.read_text(encoding='utf8')
    occurrences = len(self.find_target_by_name(name, contents))

    if occurrences == 1:
      return

    cardinality = 'Multiple' if occurrences else 'Zero'
    self.fail(f"{cardinality} targets named '{name}' found in '{contents}'")

  def assertTargetNotInWorkspace(self, name, package=''):
    build_file = self.workspace_out_path.joinpath(package, 'BUILD.bazel')

    if not build_file.exists():
      return

    contents = build_file.read_text(encoding='utf8')
    matches = self.find_target_by_name(name, contents)

    if not matches:
      return

    self.fail(f"Unexpectedly found target(s) named '{name}' in '{contents}'")

  def assertInBuildFile(self, substring, package=''):
    build_file = self.workspace_out_path.joinpath(package, 'BUILD.bazel')
    self.assertIn(substring, build_file.read_text(encoding='utf8'))

  def assertNotInBuildFile(self, substring, package=''):
    build_file = self.workspace_out_path.joinpath(package, 'BUILD.bazel')
    self.assertNotIn(substring, build_file.read_text(encoding='utf8'))

  def assertFileInWorkspace(self, relative_path, package=''):
    path = self.workspace_out_path.joinpath(package, relative_path)
    self.assertTrue(path.exists())

  def assertDirInWorkspace(self, relative_path, package=''):
    path = self.workspace_out_path.joinpath(package, relative_path)
    self.assertTrue(path.is_dir())

  def assertFileNotInWorkspace(self, relative_path, package=''):
    path = self.workspace_out_path.joinpath(package, relative_path)
    self.assertFalse(path.exists())

  def find_target_by_name(self, name: str, contents: str) -> List[str]:
    return re.findall(rf'\bname\s*=\s*"{name}"', contents)

  def add_device_def_to_filesystem(self):
    bazel_device_def = self.resource_manager.get_resource_file_path(
        'device_def'
    )
    bazel_device_def.mkdir(parents=True)
    bazel_device_def.joinpath('device_def.bzl').touch()


class BasicWorkspaceGenerationTest(GenerationTestFixture):
  """Tests for basic workspace generation and update."""

  def test_generate_workspace_when_nonexistent(self):
    workspace_generator = self.create_workspace_generator()
    shutil.rmtree(workspace_generator.workspace_out_path, ignore_errors=True)

    workspace_generator.generate()

    self.assertTrue(workspace_generator.workspace_out_path.is_dir())

  def test_regenerate_workspace_when_features_changed(self):
    workspace_generator = self.create_workspace_generator(
        enabled_features={bazel_mode.Features.NULL_FEATURE}
    )
    workspace_generator.generate()
    workspace_stat = workspace_generator.workspace_out_path.stat()

    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    new_workspace_stat = workspace_generator.workspace_out_path.stat()

    self.assertNotEqual(workspace_stat, new_workspace_stat)

  def test_not_regenerate_when_feature_does_not_affect_workspace(self):
    workspace_generator = self.create_workspace_generator(
        enabled_features={bazel_mode.Features.NULL_FEATURE}
    )
    workspace_generator.generate()
    workspace_stat = workspace_generator.workspace_out_path.stat()

    parser = argparse.ArgumentParser()
    bazel_mode.add_parser_arguments(parser, dest='bazel_mode_features')
    # pylint: disable=no-member
    args = parser.parse_args([
        bazel_mode.Features.NULL_FEATURE.arg_flag,
        '--experimental-bes-publish',
    ])
    workspace_generator = self.create_workspace_generator(
        enabled_features=set(args.bazel_mode_features)
    )
    workspace_generator.generate()
    new_workspace_stat = workspace_generator.workspace_out_path.stat()

    self.assertEqual(workspace_stat, new_workspace_stat)

  def test_not_regenerate_workspace_when_features_unchanged(self):
    workspace_generator = self.create_workspace_generator(
        enabled_features={bazel_mode.Features.NULL_FEATURE}
    )
    workspace_generator.generate()
    workspace_stat = workspace_generator.workspace_out_path.stat()

    workspace_generator = self.create_workspace_generator(
        enabled_features={bazel_mode.Features.NULL_FEATURE}
    )
    workspace_generator.generate()
    new_workspace_stat = workspace_generator.workspace_out_path.stat()

    self.assertEqual(workspace_stat, new_workspace_stat)

  def test_regenerate_workspace_when_module_info_deleted(self):
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    workspace_stat = workspace_generator.workspace_out_path.stat()

    workspace_generator.mod_info.mod_info_file_path.unlink()
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()

    new_workspace_stat = workspace_generator.workspace_out_path.stat()
    self.assertNotEqual(workspace_stat, new_workspace_stat)

  def test_not_regenerate_workspace_when_module_info_unchanged(self):
    workspace_generator1 = self.create_workspace_generator()
    workspace_generator1.generate()
    workspace_stat = workspace_generator1.workspace_out_path.stat()

    workspace_generator2 = self.create_workspace_generator()
    workspace_generator2.generate()
    new_workspace_stat = workspace_generator2.workspace_out_path.stat()

    self.assertEqual(workspace_stat, new_workspace_stat)

  def test_not_regenerate_workspace_when_module_only_touched(self):
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    workspace_stat = workspace_generator.workspace_out_path.stat()

    Path(workspace_generator.mod_info.mod_info_file_path).touch()
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()

    new_workspace_stat = workspace_generator.workspace_out_path.stat()
    self.assertEqual(workspace_stat, new_workspace_stat)

  def test_regenerate_workspace_when_module_info_changed(self):
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    workspace_stat = workspace_generator.workspace_out_path.stat()

    mod_info_file_path = workspace_generator.mod_info.mod_info_file_path
    with open(mod_info_file_path, 'a', encoding='utf8') as f:
      f.write(' ')
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()

    new_workspace_stat = workspace_generator.workspace_out_path.stat()
    self.assertNotEqual(workspace_stat, new_workspace_stat)

  def test_regenerate_workspace_when_md5_file_removed(self):
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    workspace_stat = workspace_generator.workspace_out_path.stat()

    self.workspace_md5_checksum.unlink()
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    new_workspace_stat = workspace_generator.workspace_out_path.stat()

    self.assertNotEqual(workspace_stat, new_workspace_stat)

  def test_regenerate_workspace_when_md5_file_is_broken(self):
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    workspace_stat = workspace_generator.workspace_out_path.stat()

    self.workspace_md5_checksum.write_text('broken checksum file')
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    new_workspace_stat = workspace_generator.workspace_out_path.stat()

    self.assertNotEqual(workspace_stat, new_workspace_stat)

  def test_not_regenerate_workspace_when_workspace_files_unaffected(self):
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    workspace_stat = workspace_generator.workspace_out_path.stat()

    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    new_workspace_stat = workspace_generator.workspace_out_path.stat()

    self.assertEqual(workspace_stat, new_workspace_stat)

  def test_scrub_old_workspace_when_regenerating(self):
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    some_file = workspace_generator.workspace_out_path.joinpath('some_file')
    some_file.touch()
    self.assertTrue(some_file.is_file())

    # Remove the module_info file to regenerate the workspace.
    workspace_generator.mod_info.mod_info_file_path.unlink()
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()

    self.assertFalse(some_file.is_file())

  def test_regenerate_workspace_when_resource_file_changed(self):
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    workspace_stat = workspace_generator.workspace_out_path.stat()

    with open(self.rules_bzl_file, 'a', encoding='utf8') as f:
      f.write(' ')
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()

    new_workspace_stat = workspace_generator.workspace_out_path.stat()
    self.assertNotEqual(workspace_stat, new_workspace_stat)

  def test_not_regenerate_workspace_when_resource_file_only_touched(self):
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    workspace_stat = workspace_generator.workspace_out_path.stat()

    self.rules_bzl_file.touch()
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()

    new_workspace_stat = workspace_generator.workspace_out_path.stat()
    self.assertEqual(workspace_stat, new_workspace_stat)

  def test_copy_workspace_resources(self):
    gen = self.create_workspace_generator()

    gen.generate()

    self.assertFileInWorkspace('WORKSPACE')
    self.assertFileInWorkspace('.bazelrc')
    self.assertDirInWorkspace('bazel/rules')
    self.assertDirInWorkspace('bazel/configs')

  def test_generated_target_name(self):
    mod_info = self.create_module_info(
        modules=[host_unit_test_module(name='hello_world_test')]
    )
    info = mod_info.get_module_info('hello_world_test')
    info[constants.MODULE_INFO_ID] = 'new_hello_world_test'

    self.run_generator(mod_info)

    self.assertTargetInWorkspace('new_hello_world_test')
    self.assertTargetNotInWorkspace('hello_world_test')

  def test_generate_host_unit_test_module_target(self):
    mod_info = self.create_module_info(
        modules=[host_unit_test_module(name='hello_world_test')]
    )

    self.run_generator(mod_info)

    self.assertTargetInWorkspace('hello_world_test_host')

  def test_not_generate_host_test_module_target(self):
    mod_info = self.create_module_info(
        modules=[
            host_test_module(name='hello_world_test'),
        ]
    )

    self.run_generator(mod_info)

    self.assertTargetNotInWorkspace('hello_world_test')

  def test_not_generate_test_module_target_with_invalid_installed_path(self):
    mod_info = self.create_module_info(
        modules=[
            test_module(name='hello_world_test', installed='out/invalid/path')
        ]
    )

    self.run_generator(mod_info)

    self.assertTargetNotInWorkspace('hello_world_test_device')
    self.assertTargetNotInWorkspace('hello_world_test_host')

  def test_generate_variable_file(self):
    gen = self.create_workspace_generator()

    gen.generate()

    self.assertFileInWorkspace('BUILD.bazel')
    self.assertFileInWorkspace('constants.bzl')


class MultiConfigUnitTestModuleTestTargetGenerationTest(GenerationTestFixture):
  """Tests for test target generation of test modules with multi-configs."""

  def setUp(self):
    super().setUp()
    super().add_device_def_to_filesystem()

  def test_generate_test_rule_imports(self):
    mod_info = self.create_module_info(
        modules=[
            multi_config(
                host_unit_suite(
                    test_module(name='hello_world_test', path='example/tests')
                )
            ),
        ]
    )

    self.run_generator(
        mod_info,
        enabled_features=set(
            [bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST]
        ),
    )

    self.assertInBuildFile(
        'load("//bazel/rules:tradefed_test.bzl",'
        ' "tradefed_device_driven_test", "tradefed_deviceless_test")\n',
        package='example/tests',
    )

  def test_not_generate_device_test_import_when_feature_disabled(self):
    mod_info = self.create_module_info(
        modules=[
            multi_config(
                host_unit_suite(
                    test_module(name='hello_world_test', path='example/tests')
                )
            ),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        'load("//bazel/rules:tradefed_test.bzl", "tradefed_deviceless_test")\n',
        package='example/tests',
    )

  def test_generate_test_targets(self):
    mod_info = self.create_module_info(
        modules=[
            multi_config(
                host_unit_suite(
                    test_module(name='hello_world_test', path='example/tests')
                )
            ),
        ]
    )

    self.run_generator(
        mod_info,
        enabled_features=set(
            [bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST]
        ),
    )

    self.assertTargetInWorkspace(
        'hello_world_test_device', package='example/tests'
    )
    self.assertTargetInWorkspace(
        'hello_world_test_host', package='example/tests'
    )

  def test_not_generate_device_test_target_when_feature_disabled(self):
    mod_info = self.create_module_info(
        modules=[
            multi_config(
                host_unit_suite(
                    test_module(name='hello_world_test', path='example/tests')
                )
            ),
        ]
    )

    self.run_generator(mod_info)

    self.assertTargetNotInWorkspace(
        'hello_world_test_device', package='example/tests'
    )
    self.assertTargetInWorkspace(
        'hello_world_test_host', package='example/tests'
    )


class DeviceTestModuleTestTargetGenerationTest(GenerationTestFixture):
  """Tests for device test module test target generation."""

  def setUp(self):
    super().setUp()
    super().add_device_def_to_filesystem()

  def test_generate_device_driven_test_target(self):
    mod_info = self.create_module_info(
        modules=[
            device_test_module(name='hello_world_test', path='example/tests'),
        ]
    )

    self.run_generator(
        mod_info,
        enabled_features=set(
            [bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST]
        ),
    )

    self.assertInBuildFile(
        'load("//bazel/rules:tradefed_test.bzl",'
        ' "tradefed_device_driven_test")\n',
        package='example/tests',
    )
    self.assertDirInWorkspace('device_def')
    self.assertTargetInWorkspace(
        'hello_world_test_device', package='example/tests'
    )

  def test_generate_target_with_suites(self):
    mod_info = self.create_module_info(
        modules=[
            device_test_module(
                name='hello_world_test',
                path='example/tests',
                compatibility_suites=['cts', 'mts'],
            ),
        ]
    )

    self.run_generator(
        mod_info,
        enabled_features=set(
            [bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST]
        ),
    )

    self.assertInBuildFile(
        '    suites = [\n        "cts",\n        "mts",\n    ],\n',
        package='example/tests',
    )

  def test_generate_target_with_host_dependencies(self):
    mod_info = self.create_module_info(
        modules=[
            device_test_module(
                name='hello_world_test',
                path='example/tests',
                host_dependencies=['vts_dep', 'cts_dep'],
            ),
            host_module(name='vts_dep'),
            host_module(name='cts_dep'),
        ]
    )

    self.run_generator(
        mod_info,
        enabled_features=set(
            [bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST]
        ),
    )

    self.assertInBuildFile(
        '    tradefed_deps = [\n'
        '        "//:cts_dep",\n'
        '        "//:vts_dep",\n'
        '    ],\n',
        package='example/tests',
    )

  def test_generate_target_with_device_dependencies(self):
    mod_info = self.create_module_info(
        modules=[
            host_test_module(
                name='hello_world_test',
                path='example/tests',
                target_dependencies=['helper_app'],
            ),
            device_module(name='helper_app'),
        ]
    )

    self.run_generator(
        mod_info,
        enabled_features=set(
            [bazel_mode.Features.EXPERIMENTAL_HOST_DRIVEN_TEST]
        ),
    )

    self.assertInBuildFile(
        '    device_data = [\n        "//:helper_app",\n    ],\n',
        package='example/tests',
    )

  def test_generate_target_with_tags(self):
    mod_info = self.create_module_info(
        modules=[
            device_test_module(
                name='hello_world_test',
                path='example/tests',
                test_options_tags=['no-remote'],
            ),
        ]
    )

    self.run_generator(
        mod_info,
        enabled_features=set(
            [bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST]
        ),
    )

    self.assertInBuildFile(
        '    tags = [\n        "no-remote",\n    ],\n',
        package='example/tests',
    )

  def test_generate_host_driven_test_target(self):
    mod_info = self.create_module_info(
        modules=[
            host_test_module(name='hello_world_test', path='example/tests'),
        ]
    )

    self.run_generator(
        mod_info,
        enabled_features=set(
            [bazel_mode.Features.EXPERIMENTAL_HOST_DRIVEN_TEST]
        ),
    )

    self.assertInBuildFile(
        'tradefed_host_driven_device_test(', package='example/tests'
    )

  def test_generate_multi_config_device_test_target(self):
    mod_info = self.create_module_info(
        modules=[
            multi_config(
                test_module(name='hello_world_test', path='example/tests')
            ),
        ]
    )

    self.run_generator(
        mod_info,
        enabled_features=set([
            bazel_mode.Features.EXPERIMENTAL_HOST_DRIVEN_TEST,
            bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST,
        ]),
    )

    self.assertInBuildFile(
        'load("//bazel/rules:tradefed_test.bzl", '
        '"tradefed_device_driven_test", '
        '"tradefed_host_driven_device_test")\n',
        package='example/tests',
    )
    self.assertTargetInWorkspace(
        'hello_world_test_device', package='example/tests'
    )
    self.assertTargetInWorkspace(
        'hello_world_test_host', package='example/tests'
    )

  def test_not_generate_host_driven_test_target_when_feature_disabled(self):
    mod_info = self.create_module_info(
        modules=[
            multi_config(
                test_module(name='hello_world_test', path='example/tests')
            ),
        ]
    )

    self.run_generator(
        mod_info,
        enabled_features=set(
            [bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST]
        ),
    )

    self.assertTargetInWorkspace(
        'hello_world_test_device', package='example/tests'
    )
    self.assertTargetNotInWorkspace(
        'hello_world_test_host', package='example/tests'
    )

  def test_raise_when_prerequisite_not_in_module_info(self):
    mod_info = self.create_module_info(
        modules=[
            device_test_module(),
        ]
    )
    del mod_info.name_to_module_info['aapt']

    with self.assertRaises(Exception) as context:
      self.run_generator(
          mod_info,
          enabled_features=set(
              [bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST]
          ),
      )

    self.assertIn('aapt', str(context.exception))


class HostUnitTestModuleTestTargetGenerationTest(GenerationTestFixture):
  """Tests for host unit test module test target generation."""

  def test_generate_deviceless_test_import(self):
    mod_info = self.create_module_info(
        modules=[
            host_unit_test_module(name='hello_world_test'),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        'load("//bazel/rules:tradefed_test.bzl", "tradefed_deviceless_test")\n'
    )

  def test_generate_deviceless_test_target(self):
    mod_info = self.create_module_info(
        modules=[
            host_unit_test_module(
                name='hello_world_test', path='example/tests'
            ),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        'tradefed_deviceless_test(\n'
        '    name = "hello_world_test_host",\n'
        '    module_name = "hello_world_test",\n'
        '    test = "//example/tests:hello_world_test",\n'
        ')',
        package='example/tests',
    )

  def test_generate_target_with_tags(self):
    mod_info = self.create_module_info(
        modules=[
            host_unit_test_module(
                name='hello_world_test',
                path='example/tests',
                test_options_tags=['no-remote'],
            ),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        '    tags = [\n        "no-remote",\n    ],\n',
        package='example/tests',
    )

  def test_generate_test_module_prebuilt(self):
    mod_info = self.create_module_info(
        modules=[
            host_unit_test_module(name='hello_world_test'),
        ]
    )

    self.run_generator(mod_info)

    self.assertTargetInWorkspace('hello_world_test')

  def test_raise_when_prerequisite_not_in_module_info(self):
    mod_info = self.create_module_info(
        modules=[
            host_unit_test_module(),
        ]
    )
    del mod_info.name_to_module_info['adb']

    with self.assertRaises(Exception) as context:
      self.run_generator(mod_info)

    self.assertIn('adb', str(context.exception))

  def test_raise_when_prerequisite_module_missing_path(self):
    mod_info = self.create_module_info(
        modules=[
            host_unit_test_module(),
        ]
    )
    mod_info.name_to_module_info['adb'].get('path').clear()

    with self.assertRaises(Exception) as context:
      self.run_generator(mod_info)

    self.assertIn('adb', str(context.exception))

  def test_warning_when_prerequisite_module_has_multiple_path(self):
    mod_info = self.create_module_info(
        modules=[
            host_unit_test_module(),
        ]
    )
    mod_info.name_to_module_info['adb'].get('path').append('the/2nd/path')

    with self.assertWarns(Warning) as context:
      self.run_generator(mod_info)

    self.assertIn('adb', str(context.warnings[0].message))


class RemoteAvdTestTargetGenerationTest(GenerationTestFixture):
  """Unit tests for generating Bazel targets on remote AVD."""

  def setUp(self):
    super().setUp()
    super().add_device_def_to_filesystem()

  def test_generate_remote_avd_test_target(self):
    mod_info = self.create_module_info(
        modules=[
            device_test_module(name='hello_world_test', path='example/tests'),
        ]
    )

    self.run_generator(
        mod_info,
        enabled_features=set([
            bazel_mode.Features.EXPERIMENTAL_REMOTE_AVD,
            bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST,
        ]),
    )

    self.assertInBuildFile(
        'load("//bazel/rules:tradefed_test.bzl",'
        ' "tradefed_device_driven_test")\n',
        package='example/tests',
    )
    self.assertDirInWorkspace('device_def')
    self.assertTargetInWorkspace(
        'hello_world_test_device', package='example/tests'
    )

  def test_generate_remote_avd_test_target_no_device_test_flag(self):
    mod_info = self.create_module_info(
        modules=[
            device_test_module(name='hello_world_test', path='example/tests'),
        ]
    )

    with self.assertRaises(Exception) as context:
      self.run_generator(
          mod_info,
          enabled_features=set([bazel_mode.Features.EXPERIMENTAL_REMOTE_AVD]),
      )

    self.assertIn(
        '--experimental-device-driven-test" flag is not set',
        str(context.exception),
    )


class RobolectricTestModuleTestTargetGenerationTest(GenerationTestFixture):
  """Tests for robolectric test module test target generation."""

  def setUp(self):
    super().setUp()
    self.robolectric_template_path = (
        self.resource_manager.get_resource_file_path(
            bazel_mode.ROBOLECTRIC_CONFIG, True
        )
    )
    self.fs.create_file(self.robolectric_template_path, contents='')
    # ResourceManager only calculates md5 when registering files. So, it is
    # necessary to call get_resource_file_path() again after writing files.
    self.resource_manager.get_resource_file_path(
        bazel_mode.ROBOLECTRIC_CONFIG, True
    )

  def test_generate_robolectric_test_target(self):
    module_name = 'hello_world_test'
    mod_info = self.create_module_info(
        modules=[
            robolectric_test_module(
                name=f'{module_name}', compatibility_suites='robolectric-tests'
            ),
        ]
    )

    self.run_generator(
        mod_info,
        enabled_features=set(
            [bazel_mode.Features.EXPERIMENTAL_ROBOLECTRIC_TEST]
        ),
    )

    self.assertInBuildFile(
        'load("//bazel/rules:tradefed_test.bzl",'
        ' "tradefed_robolectric_test")\n',
    )
    self.assertTargetInWorkspace(f'{module_name}_host')

  def test_not_generate_when_feature_disabled(self):
    module_name = 'hello_world_test'
    mod_info = self.create_module_info(
        modules=[
            robolectric_test_module(
                name=f'{module_name}', compatibility_suites='robolectric-tests'
            ),
        ]
    )

    self.run_generator(mod_info)

    self.assertTargetNotInWorkspace(f'{module_name}_host')

  def test_not_generate_for_legacy_robolectric_test_type(self):
    module_name = 'hello_world_test'
    module_path = 'example/tests'
    mod_info = self.create_module_info(
        modules=[
            robolectric_test_module(name=f'{module_name}', path=module_path),
        ]
    )

    self.run_generator(
        mod_info,
        enabled_features=set(
            [bazel_mode.Features.EXPERIMENTAL_ROBOLECTRIC_TEST]
        ),
    )

    self.assertFileNotInWorkspace('BUILD.bazel', package=f'{module_path}')

  def test_generate_jdk_target(self):
    gen = self.create_workspace_generator(jdk_path=Path('jdk_src_root'))

    gen.generate()

    self.assertInBuildFile(
        'filegroup(\n'
        f'    name = "{bazel_mode.JDK_NAME}",\n'
        '    srcs = glob([\n'
        f'        "{bazel_mode.JDK_NAME}_files/**",\n',
        package=f'{bazel_mode.JDK_PACKAGE_NAME}',
    )

  def test_not_generate_jdk_target_when_no_jdk_path(self):
    gen = self.create_workspace_generator(jdk_path=None)

    gen.generate()

    self.assertFileNotInWorkspace(
        'BUILD.bazel', package=f'{bazel_mode.JDK_PACKAGE_NAME}'
    )

  def test_create_symlinks_to_jdk(self):
    jdk_path = Path('jdk_path')
    gen = self.create_workspace_generator(jdk_path=jdk_path)

    gen.generate()

    self.assertSymlinkTo(
        self.workspace_out_path.joinpath(
            f'{bazel_mode.JDK_PACKAGE_NAME}/{bazel_mode.JDK_NAME}_files'
        ),
        self.resource_manager.get_src_file_path(f'{jdk_path}'),
    )

  def test_generate_android_all_target(self):
    gen = self.create_workspace_generator(jdk_path=Path('jdk_src_root'))

    gen.generate()

    self.assertInBuildFile(
        'filegroup(\n'
        '    name = "android-all",\n'
        '    srcs = glob([\n'
        '        "android-all_files/**",\n',
        package='android-all',
    )

  def test_not_generate_android_all_target_when_no_jdk_path(self):
    gen = self.create_workspace_generator(jdk_path=None)

    gen.generate()

    self.assertFileNotInWorkspace('BUILD.bazel', package='android-all')

  def test_create_symlinks_to_android_all(self):
    module_name = 'android-all'
    gen = self.create_workspace_generator(jdk_path=Path('jdk_src_root'))

    gen.generate()

    self.assertSymlinkTo(
        self.workspace_out_path.joinpath(f'{module_name}/{module_name}_files'),
        self.host_out_path.joinpath(f'testcases/{module_name}'),
    )

  def test_regenerate_workspace_when_robolectric_template_changed(self):
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    workspace_stat = workspace_generator.workspace_out_path.stat()

    with open(self.robolectric_template_path, 'a', encoding='utf8') as f:
      f.write(' ')
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()

    new_workspace_stat = workspace_generator.workspace_out_path.stat()
    self.assertNotEqual(workspace_stat, new_workspace_stat)

  def test_not_regenerate_workspace_when_robolectric_template_touched(self):
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()
    workspace_stat = workspace_generator.workspace_out_path.stat()

    self.robolectric_template_path.touch()
    workspace_generator = self.create_workspace_generator()
    workspace_generator.generate()

    new_workspace_stat = workspace_generator.workspace_out_path.stat()
    self.assertEqual(workspace_stat, new_workspace_stat)


class ModulePrebuiltTargetGenerationTest(GenerationTestFixture):
  """Tests for module prebuilt target generation."""

  def test_generate_prebuilt_import(self):
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        'load("//bazel/rules:soong_prebuilt.bzl", "soong_prebuilt")\n'
    )

  def test_generate_prebuilt_target_for_multi_config_test_module(self):
    mod_info = self.create_module_info(
        modules=[
            multi_config(supported_test_module(name='libhello')),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        'soong_prebuilt(\n'
        '    name = "libhello",\n'
        '    module_name = "libhello",\n'
        '    files = select({\n'
        '        "//bazel/rules:device": glob(["libhello/device/**/*"]),\n'
        '        "//bazel/rules:host": glob(["libhello/host/**/*"]),\n'
        '    }),\n'
        '    suites = [\n'
        '        "host-unit-tests",\n'
        '    ],\n'
        ')\n'
    )

  def test_create_symlinks_to_testcases_for_multi_config_test_module(self):
    module_name = 'hello_world_test'
    mod_info = self.create_module_info(
        modules=[multi_config(supported_test_module(name=module_name))]
    )
    module_out_path = self.workspace_out_path.joinpath(module_name)

    self.run_generator(mod_info)

    self.assertSymlinkTo(
        module_out_path.joinpath(f'host/testcases/{module_name}'),
        self.host_out_path.joinpath(f'testcases/{module_name}'),
    )
    self.assertSymlinkTo(
        module_out_path.joinpath(f'device/testcases/{module_name}'),
        self.product_out_path.joinpath(f'testcases/{module_name}'),
    )

  def test_generate_files_for_host_only_test_module(self):
    mod_info = self.create_module_info(
        modules=[
            host_only_config(supported_test_module(name='test1')),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        '    files = select({\n'
        '        "//bazel/rules:host": glob(["test1/host/**/*"]),\n'
        '    }),\n'
    )

  def test_generate_files_for_device_only_test_module(self):
    mod_info = self.create_module_info(
        modules=[
            device_only_config(supported_test_module(name='test1')),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        '    files = select({\n'
        '        "//bazel/rules:device": glob(["test1/device/**/*"]),\n'
        '    }),\n'
    )

  def test_not_create_device_symlinks_for_host_only_test_module(self):
    mod_info = self.create_module_info(
        modules=[
            host_only_config(supported_test_module(name='test1')),
        ]
    )

    self.run_generator(mod_info)

    self.assertFileNotInWorkspace('test1/device')

  def test_not_create_host_symlinks_for_device_test_module(self):
    mod_info = self.create_module_info(
        modules=[
            device_only_config(supported_test_module(name='test1')),
        ]
    )

    self.run_generator(mod_info)

    self.assertFileNotInWorkspace('test1/host')


class ModuleSharedLibGenerationTest(GenerationTestFixture):
  """Tests for module shared libs target generation."""

  def test_not_generate_runtime_deps_when_all_configs_incompatible(self):
    mod_info = self.create_module_info(
        modules=[
            host_only_config(supported_test_module(shared_libs=['libdevice'])),
            device_only_config(module(name='libdevice')),
        ]
    )

    self.run_generator(mod_info)

    self.assertNotInBuildFile('runtime_deps')

  def test_generate_runtime_deps_when_configs_compatible(self):
    mod_info = self.create_module_info(
        modules=[
            multi_config(supported_test_module(shared_libs=['libmulti'])),
            multi_config_module(name='libmulti'),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        '    runtime_deps = select({\n'
        '        "//bazel/rules:device": [\n'
        '            "//:libmulti",\n'
        '        ],\n'
        '        "//bazel/rules:host": [\n'
        '            "//:libmulti",\n'
        '        ],\n'
        '    }),\n'
    )

  def test_generate_runtime_deps_when_configs_partially_compatible(self):
    mod_info = self.create_module_info(
        modules=[
            multi_config(
                supported_test_module(
                    shared_libs=[
                        'libhost',
                    ]
                )
            ),
            host_module(name='libhost'),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        '    runtime_deps = select({\n'
        '        "//bazel/rules:device": [\n'
        '        ],\n'
        '        "//bazel/rules:host": [\n'
        '            "//:libhost",\n'
        '        ],\n'
        '    }),\n'
    )

  def test_generate_runtime_deps_with_mixed_compatibility(self):
    mod_info = self.create_module_info(
        modules=[
            multi_config(
                supported_test_module(
                    shared_libs=['libhost', 'libdevice', 'libmulti']
                )
            ),
            host_module(name='libhost'),
            device_module(name='libdevice'),
            multi_config_module(name='libmulti'),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        '    runtime_deps = select({\n'
        '        "//bazel/rules:device": [\n'
        '            "//:libdevice",\n'
        '            "//:libmulti",\n'
        '        ],\n'
        '        "//bazel/rules:host": [\n'
        '            "//:libhost",\n'
        '            "//:libmulti",\n'
        '        ],\n'
        '    }),\n'
    )

  def test_generate_runtime_deps_recursively(self):
    mod_info = self.create_module_info(
        modules=[
            multi_config(
                supported_test_module(
                    shared_libs=[
                        'libdirect',
                    ]
                )
            ),
            multi_config_module(
                name='libdirect',
                shared_libs=[
                    'libtransitive',
                ],
            ),
            multi_config_module(name='libtransitive'),
        ]
    )

    self.run_generator(mod_info)

    self.assertTargetInWorkspace('libtransitive')

  def test_generate_shared_runtime_deps_once(self):
    mod_info = self.create_module_info(
        modules=[
            multi_config(
                supported_test_module(
                    shared_libs=[
                        'libleft',
                        'libright',
                    ]
                )
            ),
            multi_config_module(
                name='libleft',
                shared_libs=[
                    'libshared',
                ],
            ),
            multi_config_module(
                name='libright',
                shared_libs=[
                    'libshared',
                ],
            ),
            multi_config_module(name='libshared'),
        ]
    )

    self.run_generator(mod_info)

    self.assertTargetInWorkspace('libshared')

  def test_generate_runtime_deps_in_order(self):
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(shared_libs=['libhello2', 'libhello1']),
            host_module(name='libhello1'),
            host_module(name='libhello2'),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        '            "//:libhello1",\n            "//:libhello2",\n'
    )

  def test_generate_target_for_shared_lib(self):
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(shared_libs=['libhello']),
            host_module(name='libhello'),
        ]
    )

    self.run_generator(mod_info)

    self.assertTargetInWorkspace('libhello')

  def test_not_generate_for_missing_shared_lib_module(self):
    mod_info = self.create_module_info(
        modules=[supported_test_module(shared_libs=['libhello'])]
    )

    self.run_generator(mod_info)

    self.assertNotInBuildFile('            "//:libhello",\n')
    self.assertTargetNotInWorkspace('libhello')

  def test_not_generate_when_shared_lib_uninstalled(self):
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(shared_libs=['libhello']),
            host_module(name='libhello', installed=[]),
        ]
    )

    self.run_generator(mod_info)

    self.assertNotInBuildFile('            "//:libhello",\n')
    self.assertTargetNotInWorkspace('libhello')

  def test_not_generate_when_shared_lib_installed_path_unsupported(self):
    unsupported_install_path = 'out/other'
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(shared_libs=['libhello']),
            shared_lib(
                module('libhello', installed=[unsupported_install_path])
            ),
        ]
    )

    self.run_generator(mod_info)

    self.assertNotInBuildFile('"//:libhello",\n')
    self.assertTargetNotInWorkspace('libhello')

  def test_not_generate_when_shared_lib_install_path_ambiguous(self):
    ambiguous_install_path = 'out/f1'
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(shared_libs=['libhello']),
            module(name='libhello', installed=[ambiguous_install_path]),
        ]
    )

    self.run_generator(mod_info)

    self.assertNotInBuildFile('"//:libhello",\n')
    self.assertTargetNotInWorkspace('libhello')

  def test_generate_target_for_rlib_dependency(self):
    mod_info = self.create_module_info(
        modules=[
            multi_config(
                host_unit_suite(
                    module(
                        name='hello_world_test',
                        dependencies=['libhost', 'libdevice'],
                    )
                )
            ),
            rlib(module(name='libhost', supported_variants=['HOST'])),
            rlib(module(name='libdevice', supported_variants=['DEVICE'])),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        'soong_uninstalled_prebuilt(\n'
        '    name = "libhost",\n'
        '    module_name = "libhost",\n'
        ')\n'
    )
    self.assertInBuildFile(
        'soong_uninstalled_prebuilt(\n'
        '    name = "libdevice",\n'
        '    module_name = "libdevice",\n'
        ')\n'
    )
    self.assertInBuildFile(
        '    runtime_deps = select({\n'
        '        "//bazel/rules:device": [\n'
        '            "//:libdevice",\n'
        '        ],\n'
        '        "//bazel/rules:host": [\n'
        '            "//:libhost",\n'
        '        ],\n'
        '    }),\n'
    )

  def test_generate_target_for_rlib_dylib_dependency(self):
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(dependencies=['libhello']),
            rlib(module(name='libhello', dependencies=['libworld'])),
            host_only_config(dylib(module(name='libworld'))),
        ]
    )

    self.run_generator(mod_info)

    self.assertTargetInWorkspace('libworld')

  def test_generate_target_for_dylib_dependency(self):
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(dependencies=['libhello']),
            host_only_config(dylib(module(name='libhello'))),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        'soong_prebuilt(\n'
        '    name = "libhello",\n'
        '    module_name = "libhello",\n'
    )

  def test_generate_target_for_uninstalled_dylib_dependency(self):
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(dependencies=['libhello']),
            dylib(module(name='libhello', installed=[])),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        'soong_uninstalled_prebuilt(\n'
        '    name = "libhello",\n'
        '    module_name = "libhello",\n'
        ')\n'
    )

  def test_not_generate_target_for_non_runtime_dependency(self):
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(dependencies=['libhello']),
            host_module(name='libhello', classes=['NOT_SUPPORTED']),
        ]
    )

    self.run_generator(mod_info)

    self.assertNotInBuildFile('"//:libhello",\n')
    self.assertTargetNotInWorkspace('libhello')

  def test_generate_target_for_runtime_dependency(self):
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(runtime_dependencies=['libhello']),
            host_only_config(
                module(name='libhello', classes=['SHARED_LIBRARIES'])
            ),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        '    runtime_deps = select({\n'
        '        "//bazel/rules:host": [\n'
        '            "//:libhello",\n'
        '        ],\n'
        '    }),\n'
    )


class SharedLibPrebuiltTargetGenerationTest(GenerationTestFixture):
  """Tests for runtime dependency module prebuilt target generation."""

  def test_create_multi_config_target_symlinks(self):
    host_file1 = self.host_out_path.joinpath('a/b/f1')
    host_file2 = self.host_out_path.joinpath('a/c/f2')
    device_file1 = self.product_out_path.joinpath('a/b/f1')
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(shared_libs=['libhello']),
            multi_config_module(
                name='libhello',
                installed=[str(host_file1), str(host_file2), str(device_file1)],
            ),
        ]
    )
    package_path = self.workspace_out_path

    self.run_generator(mod_info)

    self.assertSymlinkTo(
        package_path.joinpath('libhello/host/a/b/f1'), host_file1
    )
    self.assertSymlinkTo(
        package_path.joinpath('libhello/host/a/c/f2'), host_file2
    )
    self.assertSymlinkTo(
        package_path.joinpath('libhello/device/a/b/f1'), device_file1
    )

  def test_create_symlinks_to_installed_path_for_non_tf_testable_deps(self):
    host_file = self.host_out_path.joinpath('a/b/f1')
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(shared_libs=['libhello']),
            host_module(
                name='libhello',
                installed=[str(host_file)],
            ),
        ]
    )
    package_path = self.workspace_out_path

    self.run_generator(mod_info)

    self.assertSymlinkTo(
        package_path.joinpath('libhello/host/a/b/f1'), host_file
    )

  def test_create_symlinks_to_installed_path_for_lib_with_test_config(self):
    host_file = self.host_out_path.joinpath('a/b/f1')
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(shared_libs=['libhello']),
            host_module(
                name='libhello', installed=[str(host_file)], path='src/lib'
            ),
        ]
    )
    self.fs.create_file(Path('src/lib/AndroidTest.xml'), contents='')
    package_path = self.workspace_out_path

    self.run_generator(mod_info)

    self.assertSymlinkTo(
        package_path.joinpath('src/lib/libhello/host/a/b/f1'), host_file
    )

  def test_generate_for_host_only_shared_lib_dependency(self):
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(shared_libs=['libhello']),
            host_module(name='libhello'),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        '    files = select({\n'
        '        "//bazel/rules:host": glob(["libhello/host/**/*"]),\n'
        '    }),\n'
    )
    self.assertFileNotInWorkspace('libhello/device')

  def test_generate_for_device_only_shared_lib_dependency(self):
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(shared_libs=['libhello']),
            device_module(name='libhello'),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        '    files = select({\n'
        '        "//bazel/rules:device": glob(["libhello/device/**/*"]),\n'
        '    }),\n'
    )
    self.assertFileNotInWorkspace('libhello/host')


class DataDependenciesGenerationTest(GenerationTestFixture):
  """Tests for module data dependencies target generation."""

  def test_generate_target_for_data_dependency(self):
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(data_dependencies=['libdata']),
            host_module(name='libdata'),
        ]
    )

    self.run_generator(mod_info)

    self.assertInBuildFile(
        '    data = select({\n'
        '        "//bazel/rules:host": [\n'
        '            "//:libdata",\n'
        '        ],\n'
        '    }),\n'
    )
    self.assertTargetInWorkspace('libdata')

  def test_not_generate_target_for_data_file(self):
    # Data files are included in "data", but not in "data_dependencies".
    mod_info = self.create_module_info(
        modules=[
            supported_test_module(data=['libdata']),
            host_module(name='libdata'),
        ]
    )

    self.run_generator(mod_info)

    self.assertTargetNotInWorkspace('libdata')


def create_empty_module_info():
  return module_info.load_from_dict({})


def create_module_info(modules=None):
  mod_info = create_empty_module_info()
  modules = modules or []

  for m in modules:
    mod_info.name_to_module_info[m['module_name']] = m

  return mod_info


def host_unit_test_module(**kwargs):
  return host_unit_suite(host_test_module(**kwargs))


# We use the below alias in situations where the actual type is irrelevant to
# the test as long as it is supported in Bazel mode.
supported_test_module = host_unit_test_module


def host_test_module(**kwargs):
  kwargs.setdefault('name', 'hello_world_test')
  return host_only_config(test_module(**kwargs))


def device_test_module(**kwargs):
  kwargs.setdefault('name', 'hello_world_test')
  return device_only_config(test_module(**kwargs))


def robolectric_test_module(**kwargs):
  kwargs.setdefault('name', 'hello_world_test')
  return host_only_config(robolectric(test_module(**kwargs)))


def host_module(**kwargs):
  m = module(**kwargs)

  if 'installed' in kwargs:
    return m

  return host_only_config(m)


def device_module(**kwargs):
  m = module(**kwargs)

  if 'installed' in kwargs:
    return m

  return device_only_config(m)


def multi_config_module(**kwargs):
  m = module(**kwargs)

  if 'installed' in kwargs:
    return m

  return multi_config(m)


def test_module(**kwargs):
  kwargs.setdefault('name', 'hello_world_test')
  return test(module(**kwargs))


# TODO(b/274822450): Using a builder pattern to reduce the number of parameters
#  instead of disabling the warning.
# pylint: disable=too-many-arguments
# pylint: disable=too-many-locals
def module(
    name=None,
    path=None,
    installed=None,
    classes=None,
    auto_test_config=None,
    shared_libs=None,
    dependencies=None,
    runtime_dependencies=None,
    data=None,
    data_dependencies=None,
    compatibility_suites=None,
    host_dependencies=None,
    target_dependencies=None,
    test_options_tags=None,
    supported_variants=None,
):
  name = name or 'libhello'

  m = {}

  m['module_name'] = name
  m['class'] = classes or ['']
  m['path'] = [path or '']
  m['installed'] = installed or []
  m['is_unit_test'] = 'false'
  m['auto_test_config'] = auto_test_config or []
  m['shared_libs'] = shared_libs or []
  m['runtime_dependencies'] = runtime_dependencies or []
  m['dependencies'] = dependencies or []
  m['data'] = data or []
  m['data_dependencies'] = data_dependencies or []
  m['compatibility_suites'] = compatibility_suites or []
  m['host_dependencies'] = host_dependencies or []
  m['target_dependencies'] = target_dependencies or []
  m['test_options_tags'] = test_options_tags or []
  m['supported_variants'] = supported_variants or []
  return m


def test(info):
  info['auto_test_config'] = ['true']
  return info


def shared_lib(info):
  info['class'] = ['SHARED_LIBRARIES']
  return info


def rlib(info):
  info['class'] = ['RLIB_LIBRARIES']
  info['installed'] = []
  return info


def dylib(info):
  info['class'] = ['DYLIB_LIBRARIES']
  return info


def robolectric(info):
  info['class'] = ['ROBOLECTRIC']
  return info


def host_unit_suite(info):
  info = test(info)
  info.setdefault('compatibility_suites', []).append('host-unit-tests')
  return info


def multi_config(info):
  name = info.get('module_name', 'lib')
  info['installed'] = [
      f'out/host/linux-x86/{name}/{name}.jar',
      f'out/product/vsoc_x86/{name}/{name}.apk',
  ]
  info['supported_variants'] = [
      'DEVICE',
      'HOST',
  ]
  return info


def host_only_config(info):
  name = info.get('module_name', 'lib')
  info['installed'] = [
      f'out/host/linux-x86/{name}/{name}.jar',
  ]
  info['supported_variants'] = [
      'HOST',
  ]
  return info


def device_only_config(info):
  name = info.get('module_name', 'lib')
  info['installed'] = [
      f'out/product/vsoc_x86/{name}/{name}.jar',
  ]
  info['supported_variants'] = [
      'DEVICE',
  ]
  return info


class PackageTest(fake_filesystem_unittest.TestCase):
  """Tests for Package."""

  class FakeTarget(bazel_mode.Target):
    """Fake target used for tests."""

    def __init__(self, name, imports=None):
      self._name = name
      self._imports = imports or set()

    def name(self):
      return self._name

    def required_imports(self):
      return self._imports

    def write_to_build_file(self, f):
      f.write(f'{self._name}\n')

  def setUp(self):
    self.setUpPyfakefs()
    self.workspace_out_path = Path('/workspace_out_path')
    self.workspace_out_path.mkdir()

  def test_raise_when_adding_existing_target(self):
    target_name = '<fake_target>'
    package = bazel_mode.Package('p')
    package.add_target(self.FakeTarget(target_name))

    with self.assertRaises(Exception) as context:
      package.add_target(self.FakeTarget(target_name))

    self.assertIn(target_name, str(context.exception))

  def test_write_build_file_in_package_dir(self):
    package_path = 'abc/def'
    package = bazel_mode.Package(package_path)
    expected_path = self.workspace_out_path.joinpath(
        package_path, 'BUILD.bazel'
    )

    package.generate(self.workspace_out_path)

    self.assertTrue(expected_path.exists())

  def test_write_load_statements_in_sorted_order(self):
    package = bazel_mode.Package('p')
    target1 = self.FakeTarget(
        'target1',
        imports={
            bazel_mode.Import('z.bzl', 'symbol1'),
        },
    )
    target2 = self.FakeTarget(
        'target2',
        imports={
            bazel_mode.Import('a.bzl', 'symbol2'),
        },
    )
    package.add_target(target1)
    package.add_target(target2)

    package.generate(self.workspace_out_path)

    self.assertIn(
        'load("a.bzl", "symbol2")\nload("z.bzl", "symbol1")\n\n',
        self.package_build_file_text(package),
    )

  def test_write_load_statements_with_symbols_grouped_by_bzl(self):
    package = bazel_mode.Package('p')
    target1 = self.FakeTarget(
        'target1',
        imports={
            bazel_mode.Import('a.bzl', 'symbol1'),
            bazel_mode.Import('a.bzl', 'symbol3'),
        },
    )
    target2 = self.FakeTarget(
        'target2',
        imports={
            bazel_mode.Import('a.bzl', 'symbol2'),
        },
    )
    package.add_target(target1)
    package.add_target(target2)

    package.generate(self.workspace_out_path)

    self.assertIn(
        'load("a.bzl", "symbol1", "symbol2", "symbol3")\n\n',
        self.package_build_file_text(package),
    )

  def test_write_targets_in_add_order(self):
    package = bazel_mode.Package('p')
    target1 = self.FakeTarget('target1')
    target2 = self.FakeTarget('target2')
    package.add_target(target2)  # Added out of order.
    package.add_target(target1)

    package.generate(self.workspace_out_path)

    self.assertIn('target2\n\ntarget1\n', self.package_build_file_text(package))

  def test_generate_parent_package_when_nested_exists(self):
    parent_path = Path('parent')
    parent = bazel_mode.Package(parent_path.name)
    nested = bazel_mode.Package(parent_path.joinpath('nested'))
    nested.generate(self.workspace_out_path)

    parent.generate(self.workspace_out_path)

    self.assertTrue(self.workspace_out_path.joinpath(parent_path).is_dir())

  def package_build_file_text(self, package):
    return self.workspace_out_path.joinpath(
        package.path, 'BUILD.bazel'
    ).read_text(encoding='utf8')


class DecorateFinderMethodTest(GenerationTestFixture):
  """Tests for _decorate_find_method()."""

  def test_host_unit_test_with_host_arg_runner_is_overridden(self):
    def original_find_method(obj, test_id):
      return self.create_single_test_infos(
          obj, test_id, test_name=MODULE_NAME, runner=ATEST_TF_RUNNER
      )

    mod_info = self.create_module_info(
        modules=[host_unit_test_module(name=MODULE_NAME)]
    )
    original_finder = self.create_finder(mod_info, original_find_method)
    new_finder = bazel_mode.create_new_finder(
        mod_info, original_finder, host=True
    )

    test_infos = new_finder.find_method(
        new_finder.test_finder_instance, MODULE_NAME
    )

    self.assertEqual(len(test_infos), 1)
    self.assertEqual(test_infos[0].test_runner, BAZEL_RUNNER)

  def test_host_unit_test_without_host_arg_runner_is_overridden(self):
    def original_find_method(obj, test_id):
      return self.create_single_test_infos(
          obj, test_id, test_name=MODULE_NAME, runner=ATEST_TF_RUNNER
      )

    mod_info = self.create_module_info(
        modules=[host_unit_test_module(name=MODULE_NAME)]
    )
    original_finder = self.create_finder(mod_info, original_find_method)
    new_finder = bazel_mode.create_new_finder(
        mod_info, original_finder, host=False
    )

    test_infos = new_finder.find_method(
        new_finder.test_finder_instance, MODULE_NAME
    )

    self.assertEqual(len(test_infos), 1)
    self.assertEqual(test_infos[0].test_runner, BAZEL_RUNNER)

  def test_device_test_with_host_arg_runner_is_preserved(self):
    def original_find_method(obj, test_id):
      return self.create_single_test_infos(
          obj, test_id, test_name=MODULE_NAME, runner=ATEST_TF_RUNNER
      )

    mod_info = self.create_module_info(
        modules=[device_test_module(name=MODULE_NAME)]
    )
    original_finder = self.create_finder(mod_info, original_find_method)
    new_finder = bazel_mode.create_new_finder(
        mod_info,
        original_finder,
        host=True,
        enabled_features=[bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST],
    )

    test_infos = new_finder.find_method(
        new_finder.test_finder_instance, MODULE_NAME
    )

    self.assertEqual(len(test_infos), 1)
    self.assertEqual(test_infos[0].test_runner, ATEST_TF_RUNNER)

  def test_device_test_without_host_arg_runner_is_overridden(self):
    def original_find_method(obj, test_id):
      return self.create_single_test_infos(
          obj, test_id, test_name=MODULE_NAME, runner=ATEST_TF_RUNNER
      )

    mod_info = self.create_module_info(
        modules=[device_test_module(name=MODULE_NAME)]
    )
    original_finder = self.create_finder(mod_info, original_find_method)
    new_finder = bazel_mode.create_new_finder(
        mod_info,
        original_finder,
        host=False,
        enabled_features=[bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST],
    )

    test_infos = new_finder.find_method(
        new_finder.test_finder_instance, MODULE_NAME
    )

    self.assertEqual(len(test_infos), 1)
    self.assertEqual(test_infos[0].test_runner, BAZEL_RUNNER)

  def test_multi_config_test_with_host_arg_runner_is_overridden(self):
    def original_find_method(obj, test_id):
      return self.create_single_test_infos(
          obj, test_id, test_name=MODULE_NAME, runner=ATEST_TF_RUNNER
      )

    mod_info = self.create_module_info(
        modules=[multi_config(supported_test_module(name=MODULE_NAME))]
    )
    original_finder = self.create_finder(mod_info, original_find_method)
    new_finder = bazel_mode.create_new_finder(
        mod_info,
        original_finder,
        host=True,
        enabled_features=[bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST],
    )

    test_infos = new_finder.find_method(
        new_finder.test_finder_instance, MODULE_NAME
    )

    self.assertEqual(len(test_infos), 1)
    self.assertEqual(test_infos[0].test_runner, BAZEL_RUNNER)

  def test_multi_config_test_without_host_arg_runner_is_overridden(self):
    def original_find_method(obj, test_id):
      return self.create_single_test_infos(
          obj, test_id, test_name=MODULE_NAME, runner=ATEST_TF_RUNNER
      )

    mod_info = self.create_module_info(
        modules=[multi_config(supported_test_module(name=MODULE_NAME))]
    )
    original_finder = self.create_finder(mod_info, original_find_method)
    new_finder = bazel_mode.create_new_finder(
        mod_info,
        original_finder,
        host=False,
        enabled_features=[bazel_mode.Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST],
    )

    test_infos = new_finder.find_method(
        new_finder.test_finder_instance, MODULE_NAME
    )

    self.assertEqual(len(test_infos), 1)
    self.assertEqual(test_infos[0].test_runner, BAZEL_RUNNER)

  def test_host_non_unit_test_with_host_arg_runner_is_overridden(self):
    def original_find_method(obj, test_id):
      return self.create_single_test_infos(
          obj, test_id, test_name=MODULE_NAME, runner=ATEST_TF_RUNNER
      )

    mod_info = self.create_module_info(
        modules=[host_test_module(name=MODULE_NAME)]
    )
    original_finder = self.create_finder(mod_info, original_find_method)
    new_finder = bazel_mode.create_new_finder(
        mod_info,
        original_finder,
        host=True,
        enabled_features=[bazel_mode.Features.EXPERIMENTAL_HOST_DRIVEN_TEST],
    )

    test_infos = new_finder.find_method(
        new_finder.test_finder_instance, MODULE_NAME
    )

    self.assertEqual(len(test_infos), 1)
    self.assertEqual(test_infos[0].test_runner, BAZEL_RUNNER)

  def test_disable_device_driven_test_feature_runner_is_preserved(self):
    def original_find_method(obj, test_id):
      return self.create_single_test_infos(
          obj, test_id, test_name=MODULE_NAME, runner=ATEST_TF_RUNNER
      )

    mod_info = self.create_module_info(
        modules=[device_test_module(name=MODULE_NAME)]
    )
    original_finder = self.create_finder(mod_info, original_find_method)
    new_finder = bazel_mode.create_new_finder(
        mod_info, original_finder, host=False
    )

    test_infos = new_finder.find_method(
        new_finder.test_finder_instance, MODULE_NAME
    )

    self.assertEqual(len(test_infos), 1)
    self.assertEqual(test_infos[0].test_runner, ATEST_TF_RUNNER)

  def test_disable_host_driven_test_feature_runner_is_preserved(self):
    def original_find_method(obj, test_id):
      return self.create_single_test_infos(
          obj, test_id, test_name=MODULE_NAME, runner=ATEST_TF_RUNNER
      )

    mod_info = self.create_module_info(
        modules=[host_test_module(name=MODULE_NAME)]
    )
    original_finder = self.create_finder(mod_info, original_find_method)
    new_finder = bazel_mode.create_new_finder(
        mod_info, original_finder, host=True
    )

    test_infos = new_finder.find_method(
        new_finder.test_finder_instance, MODULE_NAME
    )

    self.assertEqual(len(test_infos), 1)
    self.assertEqual(test_infos[0].test_runner, ATEST_TF_RUNNER)

  # pylint: disable=unused-argument
  def create_single_test_infos(
      self, obj, test_id, test_name=MODULE_NAME, runner=ATEST_TF_RUNNER
  ):
    """Create list of test_info.TestInfo."""
    return [test_info.TestInfo(test_name, runner, MODULE_BUILD_TARGETS)]

  def create_finder(self, mod_info, find_method):
    return test_finder_base.Finder(
        example_finder.ExampleFinder(mod_info), find_method, 'FINDER_NAME'
    )


class BazelTestRunnerTest(fake_filesystem_unittest.TestCase):
  """Tests for BazelTestRunner."""

  def test_return_empty_build_reqs_when_no_test_infos(self):
    run_command = self.mock_run_command(side_effect=Exception(''))
    runner = self.create_bazel_test_runner(
        modules=[
            supported_test_module(name='test1', path='path1'),
        ],
        run_command=run_command,
    )

    reqs = runner.get_test_runner_build_reqs([])

    self.assertFalse(reqs)

  def test_query_bazel_test_targets_deps_with_host_arg(self):
    query_file_contents = StringIO()

    def get_query_file_content(args: List[str], _) -> str:
      query_file_contents.write(_get_query_file_content(args))
      return ''

    runner = self.create_bazel_test_runner(
        modules=[
            multi_config(host_unit_test_module(name='test1', path='path1')),
            multi_config(host_unit_test_module(name='test2', path='path2')),
            multi_config(test_module(name='test3', path='path3')),
        ],
        run_command=get_query_file_content,
        host=True,
    )

    runner.get_test_runner_build_reqs([
        test_info_of('test2'),
        test_info_of('test1'),  # Intentionally out of order.
        test_info_of('test3'),
    ])

    self.assertEqual(
        'deps(tests(//path1:test1_host + '
        '//path2:test2_host + '
        '//path3:test3_host))',
        query_file_contents.getvalue(),
    )

  def test_query_bazel_test_targets_deps_without_host_arg(self):
    query_file_contents = StringIO()

    def get_query_file_content(args: List[str], _) -> str:
      query_file_contents.write(_get_query_file_content(args))
      return ''

    runner = self.create_bazel_test_runner(
        modules=[
            multi_config(host_unit_test_module(name='test1', path='path1')),
            host_unit_test_module(name='test2', path='path2'),
            multi_config(test_module(name='test3', path='path3')),
        ],
        run_command=get_query_file_content,
    )

    runner.get_test_runner_build_reqs([
        test_info_of('test2'),
        test_info_of('test1'),
        test_info_of('test3'),
    ])

    self.assertEqual(
        'deps(tests(//path1:test1_device + '
        '//path2:test2_host + '
        '//path3:test3_device))',
        query_file_contents.getvalue(),
    )

  def test_trim_whitespace_in_bazel_query_output(self):
    run_command = self.mock_run_command(
        return_value='\n'.join(['  test1:host  ', 'test2:device  ', '  '])
    )
    runner = self.create_bazel_test_runner(
        modules=[
            supported_test_module(name='test1', path='path1'),
        ],
        run_command=run_command,
    )

    reqs = runner.get_test_runner_build_reqs([test_info_of('test1')])

    self.assertSetEqual({'test1-host', 'test2-target'}, reqs)

  def test_build_variants_in_bazel_query_output(self):
    run_command = self.mock_run_command(
        return_value='\n'.join([
            'test1:host',
            'test2:host',
            'test2:device',
            'test3:device',
            'test4:host',
            'test4:host',
        ])
    )
    runner = self.create_bazel_test_runner(
        modules=[
            supported_test_module(name='test1', path='path1'),
            supported_test_module(name='test2', path='path2'),
            supported_test_module(name='test3', path='path3'),
            supported_test_module(name='test4', path='path4'),
        ],
        run_command=run_command,
    )

    reqs = runner.get_test_runner_build_reqs([
        test_info_of('test1'),
        test_info_of('test2'),
        test_info_of('test3'),
        test_info_of('test4'),
    ])

    self.assertSetEqual(
        {'test1-host', 'test2', 'test3-target', 'test4-host'}, reqs
    )

  def test_generate_single_run_command(self):
    test_infos = [test_info_of('test1')]
    runner = self.create_bazel_test_runner_for_tests(test_infos)

    cmd = runner.generate_run_commands(test_infos, {})

    self.assertEqual(1, len(cmd))

  def test_generate_run_command_containing_targets_with_host_arg(self):
    test_infos = [
        test_info_of('test1'),
        test_info_of('test2'),
        test_info_of('test3'),
    ]
    runner = self.create_bazel_test_runner(
        [
            multi_config(host_unit_test_module(name='test1', path='path')),
            multi_config(host_unit_test_module(name='test2', path='path')),
            multi_config(test_module(name='test3', path='path')),
        ],
        host=True,
    )

    cmd = runner.generate_run_commands(test_infos, {})

    self.assertTokensIn(
        ['//path:test1_host', '//path:test2_host', '//path:test3_host'], cmd[0]
    )

  def test_generate_run_command_containing_targets_without_host_arg(self):
    test_infos = [test_info_of('test1'), test_info_of('test2')]
    runner = self.create_bazel_test_runner(
        [
            multi_config(host_unit_test_module(name='test1', path='path')),
            host_unit_test_module(name='test2', path='path'),
        ],
    )

    cmd = runner.generate_run_commands(test_infos, {})

    self.assertTokensIn(['//path:test1_device', '//path:test2_host'], cmd[0])

  def test_generate_run_command_with_multi_bazel_args(self):
    test_infos = [test_info_of('test1')]
    runner = self.create_bazel_test_runner_for_tests(test_infos)
    extra_args = {
        constants.BAZEL_ARG: [['--option1=value1'], ['--option2=value2']]
    }

    cmd = runner.generate_run_commands(test_infos, extra_args)

    self.assertTokensIn(['--option1=value1', '--option2=value2'], cmd[0])

  def test_generate_run_command_with_multi_custom_args(self):
    test_infos = [test_info_of('test1')]
    runner = self.create_bazel_test_runner_for_tests(test_infos)
    extra_args = {constants.CUSTOM_ARGS: ['-hello', '--world=value']}

    cmd = runner.generate_run_commands(test_infos, extra_args)

    self.assertTokensIn(
        ['--test_arg=-hello', '--test_arg=--world=value'], cmd[0]
    )

  def test_generate_run_command_with_custom_and_bazel_args(self):
    test_infos = [test_info_of('test1')]
    runner = self.create_bazel_test_runner_for_tests(test_infos)
    extra_args = {
        constants.CUSTOM_ARGS: ['-hello', '--world=value'],
        constants.BAZEL_ARG: [['--option1=value1']],
    }

    cmd = runner.generate_run_commands(test_infos, extra_args)

    self.assertTokensIn(
        ['--test_arg=-hello', '--test_arg=--world=value', '--option1=value1'],
        cmd[0],
    )

  def test_generate_run_command_removes_serial(self):
    test_infos = [test_info_of('test1')]
    runner = self.create_bazel_test_runner_for_tests(test_infos)
    extra_args = {constants.CUSTOM_ARGS: ['--serial=0.0.0.0']}

    cmd = runner.generate_run_commands(test_infos, extra_args)

    self.assertNotIn('--test-arg=--serial', shlex.split(cmd[0]))
    self.assertNotIn('--test-arg=--0.0.0.0', shlex.split(cmd[0]))

  def test_generate_run_command_with_tf_supported_all_abi_arg(self):
    test_infos = [test_info_of('test1')]
    runner = self.create_bazel_test_runner_for_tests(test_infos)
    extra_args = {constants.ALL_ABI: True}

    cmd = runner.generate_run_commands(test_infos, extra_args)

    self.assertTokensIn(['--test_arg=--all-abi'], cmd[0])

  def test_generate_run_command_with_iterations_args(self):
    test_infos = [test_info_of('test1')]
    runner = self.create_bazel_test_runner_for_tests(test_infos)
    extra_args = {constants.ITERATIONS: 2}

    cmd = runner.generate_run_commands(test_infos, extra_args)

    self.assertTokensIn(['--runs_per_test=2'], cmd[0])
    self.assertNotIn('--test_arg=--retry-strategy', shlex.split(cmd[0]))

  def test_generate_run_command_with_testinfo_filter(self):
    test_filter = test_filter_of('class1', ['method1'])
    test_infos = [test_info_of('test1', test_filters=[test_filter])]
    runner = self.create_bazel_test_runner_for_tests(test_infos)

    cmd = runner.generate_run_commands(test_infos, {})

    self.assertTokensIn(
        [
            '--test_arg=--atest-include-filter',
            '--test_arg=test1:class1#method1',
        ],
        cmd[0],
    )

  def test_generate_run_command_with_bes_publish_enabled(self):
    test_infos = [test_info_of('test1')]
    extra_args = {
        constants.BAZEL_MODE_FEATURES: [
            bazel_mode.Features.EXPERIMENTAL_BES_PUBLISH
        ]
    }
    build_metadata = bazel_mode.BuildMetadata(
        'master', 'aosp_cf_x86_64_phone-userdebug'
    )
    env = {
        'ATEST_BAZELRC': '/dir/atest.bazelrc',
        'ATEST_BAZEL_BES_PUBLISH_CONFIG': 'bes_publish',
    }
    runner = self.create_bazel_test_runner_for_tests(
        test_infos, build_metadata=build_metadata, env=env
    )

    cmd = runner.generate_run_commands(
        test_infos,
        extra_args,
    )

    self.assertTokensIn(
        [
            '--bazelrc=/dir/atest.bazelrc',
            '--config=bes_publish',
            '--build_metadata=ab_branch=master',
            '--build_metadata=ab_target=aosp_cf_x86_64_phone-userdebug',
        ],
        cmd[0],
    )

  def test_generate_run_command_with_no_bazel_detailed_summary(self):
    test_infos = [test_info_of('test1')]
    extra_args = {
        constants.BAZEL_MODE_FEATURES: [
            bazel_mode.Features.NO_BAZEL_DETAILED_SUMMARY
        ]
    }
    runner = self.create_bazel_test_runner_for_tests(test_infos)

    cmd = runner.generate_run_commands(test_infos, extra_args)

    self.assertTokensNotIn(
        [
            '--test_summary=detailed',
        ],
        cmd[0],
    )

  def test_generate_run_command_without_no_bazel_detailed_summary(self):
    test_infos = [test_info_of('test1')]
    extra_args = {}
    runner = self.create_bazel_test_runner_for_tests(test_infos)

    cmd = runner.generate_run_commands(test_infos, extra_args)

    self.assertTokensIn(
        [
            '--test_summary=detailed',
        ],
        cmd[0],
    )

  def test_generate_run_command_with_return_until_failure(self):
    test_infos = [test_info_of('test1')]
    extra_args = {constants.RERUN_UNTIL_FAILURE: 5}
    runner = self.create_bazel_test_runner_for_tests(test_infos)

    cmd = runner.generate_run_commands(test_infos, extra_args)

    self.assertTokensIn(
        [
            '--test_arg=--retry-strategy',
            '--test_arg=RERUN_UNTIL_FAILURE',
            '--test_arg=--max-testcase-run-count',
            '--test_arg=5',
        ],
        cmd[0],
    )

  def test_not_zip_test_output_files_when_bes_publish_not_enabled(self):
    test_infos = [test_info_of('test1')]
    extra_args = {}
    runner = self.create_bazel_test_runner_for_tests(test_infos)

    cmd = runner.generate_run_commands(
        test_infos,
        extra_args,
    )

    self.assertTokensIn(
        [
            '--nozip_undeclared_test_outputs',
        ],
        cmd[0],
    )

  def test_zip_test_output_files_when_bes_publish_enabled(self):
    test_infos = [test_info_of('test1')]
    extra_args = {
        constants.BAZEL_MODE_FEATURES: [
            bazel_mode.Features.EXPERIMENTAL_BES_PUBLISH
        ]
    }
    build_metadata = bazel_mode.BuildMetadata(
        'master', 'aosp_cf_x86_64_phone-userdebug'
    )
    env = {
        'ATEST_BAZELRC': '/dir/atest.bazelrc',
        'ATEST_BAZEL_BES_PUBLISH_CONFIG': 'bes_publish',
    }
    runner = self.create_bazel_test_runner_for_tests(
        test_infos, build_metadata=build_metadata, env=env
    )

    cmd = runner.generate_run_commands(
        test_infos,
        extra_args,
    )

    self.assertTokensNotIn(
        [
            '--nozip_undeclared_test_outputs',
        ],
        cmd[0],
    )

  def test_generate_run_command_with_remote_enabled(self):
    test_infos = [test_info_of('test1')]
    extra_args = {
        constants.BAZEL_MODE_FEATURES: [bazel_mode.Features.EXPERIMENTAL_REMOTE]
    }
    env = {
        'ATEST_BAZELRC': '/dir/atest.bazelrc',
        'ATEST_BAZEL_REMOTE_CONFIG': 'remote_deviceless',
    }
    runner = self.create_bazel_test_runner_for_tests(test_infos, env=env)

    cmd = runner.generate_run_commands(
        test_infos,
        extra_args,
    )

    self.assertTokensIn(
        [
            '--config=remote_deviceless',
        ],
        cmd[0],
    )

  def test_generate_run_command_with_remote_avd_enabled(self):
    test_infos = [test_info_of('test1')]
    extra_args = {
        constants.BAZEL_MODE_FEATURES: [
            bazel_mode.Features.EXPERIMENTAL_REMOTE_AVD
        ]
    }
    env = {
        'ATEST_BAZELRC': '/dir/atest.bazelrc',
        'ATEST_BAZEL_REMOTE_AVD_CONFIG': 'remote_avd',
    }
    runner = self.create_bazel_test_runner_for_tests(test_infos, env=env)

    cmd = runner.generate_run_commands(
        test_infos,
        extra_args,
    )

    self.assertTokensIn(
        [
            '--config=remote_avd',
        ],
        cmd[0],
    )

  def test_generate_run_command_with_remote_avd_config_not_found(self):
    test_infos = [test_info_of('test1')]
    extra_args = {
        constants.BAZEL_MODE_FEATURES: [
            bazel_mode.Features.EXPERIMENTAL_REMOTE_AVD
        ]
    }
    env = {
        'ATEST_BAZELRC': '/dir/atest.bazelrc',
    }
    runner = self.create_bazel_test_runner_for_tests(test_infos, env=env)

    with self.assertRaises(Exception) as context:
      runner.generate_run_commands(
          test_infos,
          extra_args,
      )

    self.assertIn(
        'ATEST_BAZEL_REMOTE_AVD_CONFIG environment variable is not set.',
        str(context.exception),
    )

  def test_generate_run_command_with_verbose_args(self):
    test_infos = [test_info_of('test1')]
    runner = self.create_bazel_test_runner_for_tests(test_infos)
    extra_args = {constants.VERBOSE: True}

    cmd = runner.generate_run_commands(test_infos, extra_args)

    self.assertTokensIn(['--test_output=all'], cmd[0])

  def test_disable_test_result_caching_with_wait_for_debug_args(self):
    test_infos = [test_info_of('test1')]
    runner = self.create_bazel_test_runner_for_tests(test_infos)
    extra_args = {constants.WAIT_FOR_DEBUGGER: True}

    cmd = runner.generate_run_commands(test_infos, extra_args)

    self.assertTokensIn(
        ['--test_arg=--wait-for-debugger', '--cache_test_results=no'], cmd[0]
    )

  def test_cache_test_results_arg_not_used_with_wait_for_debug_args(self):
    test_infos = [test_info_of('test1')]
    runner = self.create_bazel_test_runner_for_tests(test_infos)
    extra_args = {
        constants.WAIT_FOR_DEBUGGER: True,
        constants.BAZEL_ARG: [['--cache_test_resultsfoo']],
    }

    cmd = runner.generate_run_commands(test_infos, extra_args)

    self.assertTokensNotIn(['--cache_test_resultsfoo'], cmd[0])

  def test_retrieve_test_output_info_for_host_test(self):
    test_infos = [test_info_of('test1')]
    runner = self.create_bazel_test_runner_for_tests(test_infos)

    output_file_path, package_name, target_suffix = (
        runner.retrieve_test_output_info(test_infos[0])
    )

    self.assertEqual(
        f'/src/workspace/{bazel_mode.BAZEL_TEST_LOGS_DIR_NAME}'
        f'/path/test1_host/{bazel_mode.TEST_OUTPUT_DIR_NAME}',
        str(output_file_path),
    )
    self.assertEqual('path', package_name)
    self.assertEqual('host', target_suffix)

  def test_retrieve_test_output_info_for_device_driven_test(self):
    runner = self.create_bazel_test_runner(
        modules=[
            multi_config(device_test_module(name='test1', path='path1')),
        ],
    )

    output_file_path, package_name, target_suffix = (
        runner.retrieve_test_output_info(test_info_of('test1'))
    )

    self.assertEqual(
        f'/src/workspace/{bazel_mode.BAZEL_TEST_LOGS_DIR_NAME}'
        f'/path1/test1_device/{bazel_mode.TEST_OUTPUT_DIR_NAME}',
        str(output_file_path),
    )
    self.assertEqual('path1', package_name)
    self.assertEqual('device', target_suffix)

  def test_result_dir_symlink_to_test_output_dir(self):
    self.setUpPyfakefs()
    test_infos = [test_info_of('test1')]
    runner = self.create_bazel_test_runner_for_tests(test_infos)

    runner.organize_test_logs(test_infos)

    self.assertSymlinkTo(
        Path('result_dir/log/path/test1_host'),
        Path(
            f'/src/workspace/{bazel_mode.BAZEL_TEST_LOGS_DIR_NAME}'
            f'/path/test1_host/{bazel_mode.TEST_OUTPUT_DIR_NAME}'
        ),
    )

  def test_not_create_result_log_dir_when_test_output_zip_exist(self):
    self.setUpPyfakefs()
    test_infos = [test_info_of('test1')]
    runner = self.create_bazel_test_runner_for_tests(test_infos)
    test_output_zip = Path(
        f'/src/workspace/{bazel_mode.BAZEL_TEST_LOGS_DIR_NAME}'
        f'/path/test1_host/{bazel_mode.TEST_OUTPUT_DIR_NAME}'
        f'/{bazel_mode.TEST_OUTPUT_ZIP_NAME}'
    )
    self.fs.create_file(test_output_zip, contents='')

    runner.organize_test_logs(test_infos)

    self.assertFalse(Path('result_dir/log/').exists())

  def test_avoid_result_dir_symlink_duplication(self):
    self.setUpPyfakefs()
    test_infos = [test_info_of('test1')]
    old_symlink_to_dir = Path(tempfile.mkdtemp())
    log_path = Path('result_dir/log/path/test1_host')
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.symlink_to(old_symlink_to_dir)
    runner = self.create_bazel_test_runner_for_tests(test_infos)

    runner.organize_test_logs(test_infos)

    self.assertSymlinkTo(log_path, old_symlink_to_dir)

  def create_bazel_test_runner(
      self,
      modules,
      run_command=None,
      host=False,
      build_metadata=None,
      env=None,
      enable_features=None,
  ):
    return bazel_mode.BazelTestRunner(
        'result_dir',
        mod_info=create_module_info(modules),
        src_top=Path('/src'),
        workspace_path=Path('/src/workspace'),
        run_command=run_command or self.mock_run_command(),
        extra_args={constants.HOST: host},
        build_metadata=build_metadata,
        env=env,
        generate_workspace_fn=lambda *_: None,
        enabled_features=enable_features or [],
    )

  def create_bazel_test_runner_for_tests(
      self, test_infos, build_metadata=None, env=None
  ):
    return self.create_bazel_test_runner(
        modules=[
            supported_test_module(name=t.test_name, path='path')
            for t in test_infos
        ],
        build_metadata=build_metadata,
        env=env,
    )

  def create_completed_process(self, args, returncode, stdout):
    return subprocess.CompletedProcess(args, returncode, stdout)

  def mock_run_command(self, **kwargs):
    return mock.create_autospec(bazel_mode.default_run_command, **kwargs)

  def assertTokensIn(self, expected_tokens, s):
    tokens = shlex.split(s)
    for token in expected_tokens:
      self.assertIn(token, tokens)

  def assertTokensNotIn(self, unexpected_tokens, s):
    tokens = shlex.split(s)
    for token in unexpected_tokens:
      self.assertNotIn(token, tokens)

  def assertSymlinkTo(self, symlink_path, target_path):
    self.assertEqual(symlink_path.resolve(strict=False), target_path)


class FeatureParserTest(unittest.TestCase):
  """Tests for parsing Bazel mode feature flags."""

  def test_parse_args_with_bazel_mode_feature(self):
    parser = argparse.ArgumentParser()
    bazel_mode.add_parser_arguments(parser, dest='bazel_mode_features')
    # pylint: disable=no-member
    args = parser.parse_args([bazel_mode.Features.NULL_FEATURE.arg_flag])

    self.assertListEqual(
        [bazel_mode.Features.NULL_FEATURE], args.bazel_mode_features
    )

  def test_parse_args_without_bazel_mode_feature(self):
    parser = argparse.ArgumentParser()
    parser.add_argument('--foo', action='append_const', const='foo', dest='foo')
    bazel_mode.add_parser_arguments(parser, dest='bazel_mode_features')
    args = parser.parse_args(['--foo'])

    self.assertIsNone(args.bazel_mode_features)


def test_info_of(module_name, test_filters=None):
  return test_info.TestInfo(
      module_name,
      BAZEL_RUNNER,
      [],
      data={constants.TI_FILTER: frozenset(test_filters)}
      if test_filters
      else None,
  )


def test_filter_of(class_name, methods=None):
  return test_info.TestFilter(
      class_name, frozenset(methods) if methods else frozenset()
  )


def _get_query_file_content(args: List[str]) -> str:
  for arg in args:
    if arg.startswith('--query_file='):
      return Path(arg.split('=')[1]).read_text(encoding='utf-8')

  raise FileNotFoundError('Query file not found!')


if __name__ == '__main__':
  unittest.main()
