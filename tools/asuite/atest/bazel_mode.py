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

"""Implementation of Atest's Bazel mode.

Bazel mode runs tests using Bazel by generating a synthetic workspace that
contains test targets. Using Bazel allows Atest to leverage features such as
sandboxing, caching, and remote execution.
"""
# pylint: disable=missing-function-docstring
# pylint: disable=missing-class-docstring
# pylint: disable=too-many-lines

from __future__ import annotations

from abc import ABC, abstractmethod
import argparse
from collections import OrderedDict, defaultdict, deque
from collections.abc import Iterable
import contextlib
import dataclasses
import enum
import functools
import logging
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import tempfile
import time
from types import MappingProxyType
from typing import Any, Callable, Dict, IO, List, Set, Tuple
import warnings
from xml.etree import ElementTree as ET

from atest import atest_configs
from atest import atest_utils
from atest import constants
from atest import module_info
from atest.atest_enum import DetectType, ExitCode
from atest.metrics import metrics
from atest.proto import file_md5_pb2
from atest.test_finders import test_finder_base
from atest.test_finders import test_info
from atest.test_runners import atest_tf_test_runner as tfr
from atest.test_runners import test_runner_base as trb
from google.protobuf.message import DecodeError


JDK_PACKAGE_NAME = 'prebuilts/robolectric_jdk'
JDK_NAME = 'jdk'
ROBOLECTRIC_CONFIG = 'build/make/core/robolectric_test_config_template.xml'

BAZEL_TEST_LOGS_DIR_NAME = 'bazel-testlogs'
TEST_OUTPUT_DIR_NAME = 'test.outputs'
TEST_OUTPUT_ZIP_NAME = 'outputs.zip'

_BAZEL_WORKSPACE_DIR = 'atest_bazel_workspace'
_SUPPORTED_BAZEL_ARGS = MappingProxyType({
    # https://docs.bazel.build/versions/main/command-line-reference.html#flag--runs_per_test
    constants.ITERATIONS: lambda arg_value: [
        f'--runs_per_test={str(arg_value)}'
    ],
    # https://docs.bazel.build/versions/main/command-line-reference.html#flag--flaky_test_attempts
    constants.RETRY_ANY_FAILURE: lambda arg_value: [
        f'--flaky_test_attempts={str(arg_value)}'
    ],
    # https://docs.bazel.build/versions/main/command-line-reference.html#flag--test_output
    constants.VERBOSE: (
        lambda arg_value: ['--test_output=all'] if arg_value else []
    ),
    constants.BAZEL_ARG: lambda arg_value: [
        item for sublist in arg_value for item in sublist
    ],
})

# Maps Bazel configuration names to Soong variant names.
_CONFIG_TO_VARIANT = {
    'host': 'host',
    'device': 'target',
}


class AbortRunException(Exception):
  pass


@enum.unique
class Features(enum.Enum):
  NULL_FEATURE = ('--null-feature', 'Enables a no-action feature.', True)
  EXPERIMENTAL_DEVICE_DRIVEN_TEST = (
      '--experimental-device-driven-test',
      'Enables running device-driven tests in Bazel mode.',
      True,
  )
  EXPERIMENTAL_REMOTE_AVD = (
      '--experimental-remote-avd',
      'Enables running device-driven tests in remote AVD.',
      False,
  )
  EXPERIMENTAL_BES_PUBLISH = (
      '--experimental-bes-publish',
      'Upload test results via BES in Bazel mode.',
      False,
  )
  EXPERIMENTAL_JAVA_RUNTIME_DEPENDENCIES = (
      '--experimental-java-runtime-dependencies',
      (
          'Mirrors Soong Java `libs` and `static_libs` as Bazel target '
          'dependencies in the generated workspace. Tradefed test rules use '
          'these dependencies to set up the execution environment and ensure '
          'that all transitive runtime dependencies are present.'
      ),
      True,
  )
  EXPERIMENTAL_REMOTE = (
      '--experimental-remote',
      'Use Bazel remote execution and caching where supported.',
      False,
  )
  EXPERIMENTAL_HOST_DRIVEN_TEST = (
      '--experimental-host-driven-test',
      'Enables running host-driven device tests in Bazel mode.',
      True,
  )
  EXPERIMENTAL_ROBOLECTRIC_TEST = (
      '--experimental-robolectric-test',
      'Enables running Robolectric tests in Bazel mode.',
      True,
  )
  NO_BAZEL_DETAILED_SUMMARY = (
      '--no-bazel-detailed-summary',
      'Disables printing detailed summary of Bazel test results.',
      False,
  )

  def __init__(self, arg_flag, description, affects_workspace):
    self._arg_flag = arg_flag
    self._description = description
    self.affects_workspace = affects_workspace

  @property
  def arg_flag(self):
    return self._arg_flag

  @property
  def description(self):
    return self._description


def add_parser_arguments(parser: argparse.ArgumentParser, dest: str):
  for _, member in Features.__members__.items():
    parser.add_argument(
        member.arg_flag,
        action='append_const',
        const=member,
        dest=dest,
        help=member.description,
    )


def get_bazel_workspace_dir() -> Path:
  return atest_utils.get_build_out_dir(_BAZEL_WORKSPACE_DIR)


def generate_bazel_workspace(
    mod_info: module_info.ModuleInfo, enabled_features: Set[Features] = None
):
  """Generate or update the Bazel workspace used for running tests."""

  start = time.time()
  src_root_path = Path(os.environ.get(constants.ANDROID_BUILD_TOP))
  workspace_path = get_bazel_workspace_dir()
  resource_manager = ResourceManager(
      src_root_path=src_root_path,
      resource_root_path=_get_resource_root(),
      product_out_path=Path(os.environ.get(constants.ANDROID_PRODUCT_OUT)),
      md5_checksum_file_path=workspace_path.joinpath('workspace_md5_checksum'),
  )
  jdk_path = _read_robolectric_jdk_path(
      resource_manager.get_src_file_path(ROBOLECTRIC_CONFIG, True)
  )

  workspace_generator = WorkspaceGenerator(
      resource_manager=resource_manager,
      workspace_out_path=workspace_path,
      host_out_path=Path(os.environ.get(constants.ANDROID_HOST_OUT)),
      build_out_dir=atest_utils.get_build_out_dir(),
      mod_info=mod_info,
      jdk_path=jdk_path,
      enabled_features=enabled_features,
  )
  workspace_generator.generate()

  metrics.LocalDetectEvent(
      detect_type=DetectType.BAZEL_WORKSPACE_GENERATE_TIME,
      result=int(time.time() - start),
  )


def get_default_build_metadata():
  return BuildMetadata(
      atest_utils.get_manifest_branch(), atest_utils.get_build_target()
  )


class ResourceManager:
  """Class for managing files required to generate a Bazel Workspace."""

  def __init__(
      self,
      src_root_path: Path,
      resource_root_path: Path,
      product_out_path: Path,
      md5_checksum_file_path: Path,
  ):
    self._root_type_to_path = {
        file_md5_pb2.RootType.SRC_ROOT: src_root_path,
        file_md5_pb2.RootType.RESOURCE_ROOT: resource_root_path,
        file_md5_pb2.RootType.ABS_PATH: Path(),
        file_md5_pb2.RootType.PRODUCT_OUT: product_out_path,
    }
    self._md5_checksum_file = md5_checksum_file_path
    self._file_checksum_list = file_md5_pb2.FileChecksumList()

  def get_src_file_path(
      self, rel_path: Path = None, affects_workspace: bool = False
  ) -> Path:
    """Get the abs file path from the relative path of source_root.

    Args:
        rel_path: A relative path of the source_root.
        affects_workspace: A boolean of whether the file affects the workspace.

    Returns:
        A abs path of the file.
    """
    return self._get_file_path(
        file_md5_pb2.RootType.SRC_ROOT, rel_path, affects_workspace
    )

  def get_resource_file_path(
      self,
      rel_path: Path = None,
      affects_workspace: bool = False,
  ) -> Path:
    """Get the abs file path from the relative path of resource_root.

    Args:
        rel_path: A relative path of the resource_root.
        affects_workspace: A boolean of whether the file affects the workspace.

    Returns:
        A abs path of the file.
    """
    return self._get_file_path(
        file_md5_pb2.RootType.RESOURCE_ROOT, rel_path, affects_workspace
    )

  def get_product_out_file_path(
      self, rel_path: Path = None, affects_workspace: bool = False
  ) -> Path:
    """Get the abs file path from the relative path of product out.

    Args:
        rel_path: A relative path to the product out.
        affects_workspace: A boolean of whether the file affects the workspace.

    Returns:
        An abs path of the file.
    """
    return self._get_file_path(
        file_md5_pb2.RootType.PRODUCT_OUT, rel_path, affects_workspace
    )

  def _get_file_path(
      self,
      root_type: file_md5_pb2.RootType,
      rel_path: Path,
      affects_workspace: bool = True,
  ) -> Path:
    abs_path = self._root_type_to_path[root_type].joinpath(rel_path or Path())

    if not affects_workspace:
      return abs_path

    if abs_path.is_dir():
      for file in abs_path.glob('**/*'):
        self._register_file(root_type, file)
    else:
      self._register_file(root_type, abs_path)
    return abs_path

  def _register_file(self, root_type: file_md5_pb2.RootType, abs_path: Path):
    if not abs_path.is_file():
      logging.debug(' ignore %s: not a file.', abs_path)
      return

    rel_path = abs_path
    if abs_path.is_relative_to(self._root_type_to_path[root_type]):
      rel_path = abs_path.relative_to(self._root_type_to_path[root_type])

    self._file_checksum_list.file_checksums.append(
        file_md5_pb2.FileChecksum(
            root_type=root_type,
            rel_path=str(rel_path),
            md5sum=atest_utils.md5sum(abs_path),
        )
    )

  def register_file_with_abs_path(self, abs_path: Path):
    """Register a file which affects the workspace.

    Args:
        abs_path: A abs path of the file.
    """
    self._register_file(file_md5_pb2.RootType.ABS_PATH, abs_path)

  def save_affects_files_md5(self):
    with open(self._md5_checksum_file, 'wb') as f:
      f.write(self._file_checksum_list.SerializeToString())

  def check_affects_files_md5(self):
    """Check all affect files are consistent with the actual MD5."""
    if not self._md5_checksum_file.is_file():
      return False

    with open(self._md5_checksum_file, 'rb') as f:
      file_md5_list = file_md5_pb2.FileChecksumList()

      try:
        file_md5_list.ParseFromString(f.read())
      except DecodeError:
        logging.warning('Failed to parse the workspace md5 checksum file.')
        return False

      for file_md5 in file_md5_list.file_checksums:
        abs_path = Path(self._root_type_to_path[file_md5.root_type]).joinpath(
            file_md5.rel_path
        )
        if not abs_path.is_file():
          return False
        if atest_utils.md5sum(abs_path) != file_md5.md5sum:
          return False
      return True


class WorkspaceGenerator:
  """Class for generating a Bazel workspace."""

  # pylint: disable=too-many-arguments
  def __init__(
      self,
      resource_manager: ResourceManager,
      workspace_out_path: Path,
      host_out_path: Path,
      build_out_dir: Path,
      mod_info: module_info.ModuleInfo,
      jdk_path: Path = None,
      enabled_features: Set[Features] = None,
  ):
    """Initializes the generator.

    Args:
        workspace_out_path: Path where the workspace will be output.
        host_out_path: Path of the ANDROID_HOST_OUT.
        build_out_dir: Path of OUT_DIR
        mod_info: ModuleInfo object.
        enabled_features: Set of enabled features.
    """
    if (
        enabled_features
        and Features.EXPERIMENTAL_REMOTE_AVD in enabled_features
        and Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST not in enabled_features
    ):
      raise ValueError(
          'Cannot run remote device test because '
          '"--experimental-device-driven-test" flag is'
          ' not set.'
      )
    self.enabled_features = enabled_features or set()
    self.resource_manager = resource_manager
    self.workspace_out_path = workspace_out_path
    self.host_out_path = host_out_path
    self.build_out_dir = build_out_dir
    self.mod_info = mod_info
    self.path_to_package = {}
    self.jdk_path = jdk_path

  def generate(self):
    """Generate a Bazel workspace.

    If the workspace md5 checksum file doesn't exist or is stale, a new
    workspace will be generated. Otherwise, the existing workspace will be
    reused.
    """
    start = time.time()
    enabled_features_file = self.workspace_out_path.joinpath(
        'atest_bazel_mode_enabled_features'
    )
    enabled_features_file_contents = '\n'.join(
        sorted(f.name for f in self.enabled_features if f.affects_workspace)
    )

    if self.workspace_out_path.exists():
      # Update the file with the set of the currently enabled features to
      # make sure that changes are detected in the workspace checksum.
      enabled_features_file.write_text(enabled_features_file_contents)
      if self.resource_manager.check_affects_files_md5():
        return

      # We raise an exception if rmtree fails to avoid leaving stale
      # files in the workspace that could interfere with execution.
      shutil.rmtree(self.workspace_out_path)

    atest_utils.colorful_print('Generating Bazel workspace.\n', constants.RED)

    self._add_test_module_targets()

    self.workspace_out_path.mkdir(parents=True)
    self._generate_artifacts()

    # Note that we write the set of enabled features despite having written
    # it above since the workspace no longer exists at this point.
    enabled_features_file.write_text(enabled_features_file_contents)

    self.resource_manager.get_product_out_file_path(
        self.mod_info.mod_info_file_path.relative_to(
            self.resource_manager.get_product_out_file_path()
        ),
        True,
    )
    self.resource_manager.register_file_with_abs_path(enabled_features_file)
    self.resource_manager.save_affects_files_md5()
    metrics.LocalDetectEvent(
        detect_type=DetectType.FULL_GENERATE_BAZEL_WORKSPACE_TIME,
        result=int(time.time() - start),
    )

  def _add_test_module_targets(self):
    seen = set()

    for name, info in self.mod_info.name_to_module_info.items():
      # Ignore modules that have a 'host_cross_' prefix since they are
      # duplicates of existing modules. For example,
      # 'host_cross_aapt2_tests' is a duplicate of 'aapt2_tests'. We also
      # ignore modules with a '_32' suffix since these also are redundant
      # given that modules have both 32 and 64-bit variants built by
      # default. See b/77288544#comment6 and b/23566667 for more context.
      if name.endswith('_32') or name.startswith('host_cross_'):
        continue

      if (
          Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST in self.enabled_features
          and self.mod_info.is_device_driven_test(info)
      ):
        self._resolve_dependencies(
            self._add_device_test_target(info, False), seen
        )

      if self.mod_info.is_host_unit_test(info):
        self._resolve_dependencies(self._add_deviceless_test_target(info), seen)
      elif (
          Features.EXPERIMENTAL_ROBOLECTRIC_TEST in self.enabled_features
          and self.mod_info.is_modern_robolectric_test(info)
      ):
        self._resolve_dependencies(
            self._add_tradefed_robolectric_test_target(info), seen
        )
      elif (
          Features.EXPERIMENTAL_HOST_DRIVEN_TEST in self.enabled_features
          and self.mod_info.is_host_driven_test(info)
      ):
        self._resolve_dependencies(
            self._add_device_test_target(info, True), seen
        )

  def _resolve_dependencies(self, top_level_target: Target, seen: Set[Target]):

    stack = [deque([top_level_target])]

    while stack:
      top = stack[-1]

      if not top:
        stack.pop()
        continue

      target = top.popleft()

      # Note that we're relying on Python's default identity-based hash
      # and equality methods. This is fine since we actually DO want
      # reference-equality semantics for Target objects in this context.
      if target in seen:
        continue

      seen.add(target)

      next_top = deque()

      for ref in target.dependencies():
        info = ref.info or self._get_module_info(ref.name)
        ref.set(self._add_prebuilt_target(info))
        next_top.append(ref.target())

      stack.append(next_top)

  def _add_device_test_target(
      self, info: Dict[str, Any], is_host_driven: bool
  ) -> Target:
    package_name = self._get_module_path(info)
    name_suffix = 'host' if is_host_driven else 'device'
    name = f'{info[constants.MODULE_INFO_ID]}_{name_suffix}'

    def create():
      return TestTarget.create_device_test_target(
          name,
          package_name,
          info,
          is_host_driven,
      )

    return self._add_target(package_name, name, create)

  def _add_deviceless_test_target(self, info: Dict[str, Any]) -> Target:
    package_name = self._get_module_path(info)
    name = f'{info[constants.MODULE_INFO_ID]}_host'

    def create():
      return TestTarget.create_deviceless_test_target(
          name,
          package_name,
          info,
      )

    return self._add_target(package_name, name, create)

  def _add_tradefed_robolectric_test_target(
      self, info: Dict[str, Any]
  ) -> Target:
    package_name = self._get_module_path(info)
    name = f'{info[constants.MODULE_INFO_ID]}_host'

    return self._add_target(
        package_name,
        name,
        lambda: TestTarget.create_tradefed_robolectric_test_target(
            name, package_name, info, f'//{JDK_PACKAGE_NAME}:{JDK_NAME}'
        ),
    )

  def _add_prebuilt_target(self, info: Dict[str, Any]) -> Target:
    package_name = self._get_module_path(info)
    name = info[constants.MODULE_INFO_ID]

    def create():
      return SoongPrebuiltTarget.create(
          self,
          info,
          package_name,
      )

    return self._add_target(package_name, name, create)

  def _add_target(
      self, package_path: str, target_name: str, create_fn: Callable
  ) -> Target:

    package = self.path_to_package.get(package_path)

    if not package:
      package = Package(package_path)
      self.path_to_package[package_path] = package

    target = package.get_target(target_name)

    if target:
      return target

    target = create_fn()
    package.add_target(target)

    return target

  def _get_module_info(self, module_name: str) -> Dict[str, Any]:
    info = self.mod_info.get_module_info(module_name)

    if not info:
      raise LookupError(
          f'Could not find module `{module_name}` in module_info file'
      )

    return info

  def _get_module_path(self, info: Dict[str, Any]) -> str:
    mod_path = info.get(constants.MODULE_PATH)

    if len(mod_path) < 1:
      module_name = info['module_name']
      raise ValueError(f'Module `{module_name}` does not have any path')

    if len(mod_path) > 1:
      module_name = info['module_name']
      # We usually have a single path but there are a few exceptions for
      # modules like libLLVM_android and libclang_android.
      # TODO(yangbill): Raise an exception for multiple paths once
      # b/233581382 is resolved.
      warnings.formatwarning = lambda msg, *args, **kwargs: f'{msg}\n'
      warnings.warn(
          f'Module `{module_name}` has more than one path: `{mod_path}`'
      )

    return mod_path[0]

  def _generate_artifacts(self):
    """Generate workspace files on disk."""

    self._create_base_files()

    self._add_workspace_resource(src='rules', dst='bazel/rules')
    self._add_workspace_resource(src='configs', dst='bazel/configs')

    if Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST in self.enabled_features:
      self._add_workspace_resource(src='device_def', dst='device_def')

    self._add_bazel_bootstrap_files()

    # Symlink to package with toolchain definitions.
    self._symlink(src='prebuilts/build-tools', target='prebuilts/build-tools')

    device_infra_path = 'vendor/google/tools/atest/device_infra'
    if self.resource_manager.get_src_file_path(device_infra_path).exists():
      self._symlink(src=device_infra_path, target=device_infra_path)

    self._link_required_src_file_path(
        'build/bazel_common_rules/rules/python/stubs'
    )
    self._link_required_src_file_path('external/bazelbuild-rules_java')

    self._create_constants_file()

    self._generate_robolectric_resources()

    for package in self.path_to_package.values():
      package.generate(self.workspace_out_path)

  def _generate_robolectric_resources(self):
    if not self.jdk_path:
      return

    self._generate_jdk_resources()
    self._generate_android_all_resources()

  def _generate_jdk_resources(self):
    # TODO(b/265596946): Create the JDK toolchain instead of using
    # a filegroup.
    return self._add_target(
        JDK_PACKAGE_NAME,
        JDK_NAME,
        lambda: FilegroupTarget(
            JDK_PACKAGE_NAME,
            JDK_NAME,
            self.resource_manager.get_src_file_path(self.jdk_path),
        ),
    )

  def _generate_android_all_resources(self):
    package_name = 'android-all'
    name = 'android-all'

    return self._add_target(
        package_name,
        name,
        lambda: FilegroupTarget(
            package_name, name, self.host_out_path.joinpath(f'testcases/{name}')
        ),
    )

  def _symlink(self, *, src, target):
    """Create a symbolic link in workspace pointing to source file/dir.

    Args:
        src: A string of a relative path to root of Android source tree. This is
          the source file/dir path for which the symbolic link will be created.
        target: A string of a relative path to workspace root. This is the
          target file/dir path where the symbolic link will be created.
    """
    symlink = self.workspace_out_path.joinpath(target)
    symlink.parent.mkdir(parents=True, exist_ok=True)
    symlink.symlink_to(self.resource_manager.get_src_file_path(src))

  def _create_base_files(self):
    self._add_workspace_resource(src='WORKSPACE', dst='WORKSPACE')
    self._add_workspace_resource(src='bazelrc', dst='.bazelrc')

    self.workspace_out_path.joinpath('BUILD.bazel').touch()

  def _add_bazel_bootstrap_files(self):
    self._symlink(
        src='tools/asuite/atest/bazel/resources/bazel.sh', target='bazel.sh'
    )
    self._symlink(
        src='prebuilts/jdk/jdk17/BUILD.bazel',
        target='prebuilts/jdk/jdk17/BUILD.bazel',
    )
    self._symlink(
        src='prebuilts/jdk/jdk17/linux-x86',
        target='prebuilts/jdk/jdk17/linux-x86',
    )
    self._symlink(
        src='prebuilts/bazel/linux-x86_64/bazel',
        target='prebuilts/bazel/linux-x86_64/bazel',
    )

  def _add_workspace_resource(self, src, dst):
    """Add resource to the given destination in workspace.

    Args:
        src: A string of a relative path to root of Bazel artifacts. This is the
          source file/dir path that will be added to workspace.
        dst: A string of a relative path to workspace root. This is the
          destination file/dir path where the artifacts will be added.
    """
    src = self.resource_manager.get_resource_file_path(src, True)
    dst = self.workspace_out_path.joinpath(dst)
    dst.parent.mkdir(parents=True, exist_ok=True)

    if src.is_file():
      shutil.copy(src, dst)
    else:
      shutil.copytree(src, dst, ignore=shutil.ignore_patterns('__init__.py'))

  def _create_constants_file(self):
    def variable_name(target_name):
      return re.sub(r'[.-]', '_', target_name) + '_label'

    targets = []
    seen = set()

    for module_name in TestTarget.DEVICELESS_TEST_PREREQUISITES.union(
        TestTarget.DEVICE_TEST_PREREQUISITES
    ):
      info = self.mod_info.get_module_info(module_name)
      target = self._add_prebuilt_target(info)
      self._resolve_dependencies(target, seen)
      targets.append(target)

    with self.workspace_out_path.joinpath('constants.bzl').open('w') as f:
      writer = IndentWriter(f)
      for target in targets:
        writer.write_line(
            '%s = "%s"'
            % (variable_name(target.name()), target.qualified_name())
        )

  def _link_required_src_file_path(self, path):
    if not self.resource_manager.get_src_file_path(path).exists():
      raise RuntimeError(f'Path `{path}` does not exist in source tree.')

    self._symlink(src=path, target=path)


def _get_resource_root():
  return Path(os.path.dirname(__file__)).joinpath('bazel/resources')


class Package:
  """Class for generating an entire Package on disk."""

  def __init__(self, path: str):
    self.path = path
    self.imports = defaultdict(set)
    self.name_to_target = OrderedDict()

  def add_target(self, target):
    target_name = target.name()

    if target_name in self.name_to_target:
      raise ValueError(
          f'Cannot add target `{target_name}` which already'
          f' exists in package `{self.path}`'
      )

    self.name_to_target[target_name] = target

    for i in target.required_imports():
      self.imports[i.bzl_package].add(i.symbol)

  def generate(self, workspace_out_path: Path):
    package_dir = workspace_out_path.joinpath(self.path)
    package_dir.mkdir(parents=True, exist_ok=True)

    self._create_filesystem_layout(package_dir)
    self._write_build_file(package_dir)

  def _create_filesystem_layout(self, package_dir: Path):
    for target in self.name_to_target.values():
      target.create_filesystem_layout(package_dir)

  def _write_build_file(self, package_dir: Path):
    with package_dir.joinpath('BUILD.bazel').open('w') as f:
      f.write('package(default_visibility = ["//visibility:public"])\n')
      f.write('\n')

      for bzl_package, symbols in sorted(self.imports.items()):
        symbols_text = ', '.join('"%s"' % s for s in sorted(symbols))
        f.write(f'load("{bzl_package}", {symbols_text})\n')

      for target in self.name_to_target.values():
        f.write('\n')
        target.write_to_build_file(f)

  def get_target(self, target_name: str) -> Target:
    return self.name_to_target.get(target_name, None)


@dataclasses.dataclass(frozen=True)
class Import:
  bzl_package: str
  symbol: str


@dataclasses.dataclass(frozen=True)
class Config:
  name: str
  out_path: Path


class ModuleRef:

  @staticmethod
  def for_info(info) -> ModuleRef:
    return ModuleRef(info=info)

  @staticmethod
  def for_name(name) -> ModuleRef:
    return ModuleRef(name=name)

  def __init__(self, info=None, name=None):
    self.info = info
    self.name = name
    self._target = None

  def target(self) -> Target:
    if not self._target:
      target_name = self.info[constants.MODULE_INFO_ID]
      raise ValueError(f'Target not set for ref `{target_name}`')

    return self._target

  def set(self, target):
    self._target = target


class Target(ABC):
  """Abstract class for a Bazel target."""

  @abstractmethod
  def name(self) -> str:
    pass

  def package_name(self) -> str:
    pass

  def qualified_name(self) -> str:
    return f'//{self.package_name()}:{self.name()}'

  def required_imports(self) -> Set[Import]:
    return set()

  def supported_configs(self) -> Set[Config]:
    return set()

  def dependencies(self) -> List[ModuleRef]:
    return []

  def write_to_build_file(self, f: IO):
    pass

  def create_filesystem_layout(self, package_dir: Path):
    pass


class FilegroupTarget(Target):

  def __init__(self, package_name: str, target_name: str, srcs_root: Path):
    self._package_name = package_name
    self._target_name = target_name
    self._srcs_root = srcs_root

  def name(self) -> str:
    return self._target_name

  def package_name(self) -> str:
    return self._package_name

  def write_to_build_file(self, f: IO):
    writer = IndentWriter(f)
    build_file_writer = BuildFileWriter(writer)

    writer.write_line('filegroup(')

    with writer.indent():
      build_file_writer.write_string_attribute('name', self._target_name)
      build_file_writer.write_glob_attribute(
          'srcs', [f'{self._target_name}_files/**']
      )

    writer.write_line(')')

  def create_filesystem_layout(self, package_dir: Path):
    symlink = package_dir.joinpath(f'{self._target_name}_files')
    symlink.symlink_to(self._srcs_root)


class TestTarget(Target):
  """Class for generating a test target."""

  DEVICELESS_TEST_PREREQUISITES = frozenset({
      'adb',
      'atest-tradefed',
      'atest_script_help.sh',
      'atest_tradefed.sh',
      'tradefed',
      'tradefed-test-framework',
      'bazel-result-reporter',
  })

  DEVICE_TEST_PREREQUISITES = frozenset(
      DEVICELESS_TEST_PREREQUISITES.union(
          frozenset({
              'aapt',
              'aapt2',
              'compatibility-tradefed',
              'vts-core-tradefed-harness',
          })
      )
  )

  @staticmethod
  def create_deviceless_test_target(
      name: str, package_name: str, info: Dict[str, Any]
  ):
    return TestTarget(
        package_name,
        'tradefed_deviceless_test',
        {
            'name': name,
            'test': ModuleRef.for_info(info),
            'module_name': info['module_name'],
            'tags': info.get(constants.MODULE_TEST_OPTIONS_TAGS, []),
        },
        TestTarget.DEVICELESS_TEST_PREREQUISITES,
    )

  @staticmethod
  def create_device_test_target(
      name: str, package_name: str, info: Dict[str, Any], is_host_driven: bool
  ):
    rule = (
        'tradefed_host_driven_device_test'
        if is_host_driven
        else 'tradefed_device_driven_test'
    )

    return TestTarget(
        package_name,
        rule,
        {
            'name': name,
            'test': ModuleRef.for_info(info),
            'module_name': info['module_name'],
            'suites': set(info.get(constants.MODULE_COMPATIBILITY_SUITES, [])),
            'tradefed_deps': list(
                map(
                    ModuleRef.for_name, info.get(constants.MODULE_HOST_DEPS, [])
                )
            ),
            'tags': info.get(constants.MODULE_TEST_OPTIONS_TAGS, []),
        },
        TestTarget.DEVICE_TEST_PREREQUISITES,
    )

  @staticmethod
  def create_tradefed_robolectric_test_target(
      name: str, package_name: str, info: Dict[str, Any], jdk_label: str
  ):
    return TestTarget(
        package_name,
        'tradefed_robolectric_test',
        {
            'name': name,
            'test': ModuleRef.for_info(info),
            'module_name': info['module_name'],
            'tags': info.get(constants.MODULE_TEST_OPTIONS_TAGS, []),
            'jdk': jdk_label,
        },
        TestTarget.DEVICELESS_TEST_PREREQUISITES,
    )

  def __init__(
      self,
      package_name: str,
      rule_name: str,
      attributes: Dict[str, Any],
      prerequisites=frozenset(),
  ):
    self._attributes = attributes
    self._package_name = package_name
    self._rule_name = rule_name
    self._prerequisites = prerequisites

  def name(self) -> str:
    return self._attributes['name']

  def package_name(self) -> str:
    return self._package_name

  def required_imports(self) -> Set[Import]:
    return {Import('//bazel/rules:tradefed_test.bzl', self._rule_name)}

  def dependencies(self) -> List[ModuleRef]:
    prerequisite_refs = map(ModuleRef.for_name, self._prerequisites)

    declared_dep_refs = []
    for value in self._attributes.values():
      if isinstance(value, Iterable):
        declared_dep_refs.extend(
            [dep for dep in value if isinstance(dep, ModuleRef)]
        )
      elif isinstance(value, ModuleRef):
        declared_dep_refs.append(value)

    return declared_dep_refs + list(prerequisite_refs)

  def write_to_build_file(self, f: IO):
    prebuilt_target_name = self._attributes['test'].target().qualified_name()
    writer = IndentWriter(f)
    build_file_writer = BuildFileWriter(writer)

    writer.write_line(f'{self._rule_name}(')

    with writer.indent():
      build_file_writer.write_string_attribute('name', self._attributes['name'])

      build_file_writer.write_string_attribute(
          'module_name', self._attributes['module_name']
      )

      build_file_writer.write_string_attribute('test', prebuilt_target_name)

      build_file_writer.write_label_list_attribute(
          'tradefed_deps', self._attributes.get('tradefed_deps')
      )

      build_file_writer.write_string_list_attribute(
          'suites', sorted(self._attributes.get('suites', []))
      )

      build_file_writer.write_string_list_attribute(
          'tags', sorted(self._attributes.get('tags', []))
      )

      build_file_writer.write_label_attribute(
          'jdk', self._attributes.get('jdk', None)
      )

    writer.write_line(')')


def _read_robolectric_jdk_path(test_xml_config_template: Path) -> Path:
  if not test_xml_config_template.is_file():
    return None

  xml_root = ET.parse(test_xml_config_template).getroot()
  option = xml_root.find(".//option[@name='java-folder']")
  jdk_path = Path(option.get('value', ''))

  if not jdk_path.is_relative_to('prebuilts/jdk'):
    raise ValueError(
        f'Failed to get "java-folder" from `{test_xml_config_template}`'
    )

  return jdk_path


class BuildFileWriter:
  """Class for writing BUILD files."""

  def __init__(self, underlying: IndentWriter):
    self._underlying = underlying

  def write_string_attribute(self, attribute_name, value):
    if value is None:
      return

    self._underlying.write_line(f'{attribute_name} = "{value}",')

  def write_label_attribute(self, attribute_name: str, label_name: str):
    if label_name is None:
      return

    self._underlying.write_line(f'{attribute_name} = "{label_name}",')

  def write_string_list_attribute(self, attribute_name, values):
    if not values:
      return

    self._underlying.write_line(f'{attribute_name} = [')

    with self._underlying.indent():
      for value in values:
        self._underlying.write_line(f'"{value}",')

    self._underlying.write_line('],')

  def write_label_list_attribute(
      self, attribute_name: str, modules: List[ModuleRef]
  ):
    if not modules:
      return

    self._underlying.write_line(f'{attribute_name} = [')

    with self._underlying.indent():
      for label in sorted(set(m.target().qualified_name() for m in modules)):
        self._underlying.write_line(f'"{label}",')

    self._underlying.write_line('],')

  def write_glob_attribute(self, attribute_name: str, patterns: List[str]):
    self._underlying.write_line(f'{attribute_name} = glob([')

    with self._underlying.indent():
      for pattern in patterns:
        self._underlying.write_line(f'"{pattern}",')

    self._underlying.write_line(']),')


@dataclasses.dataclass(frozen=True)
class Dependencies:
  static_dep_refs: List[ModuleRef]
  runtime_dep_refs: List[ModuleRef]
  data_dep_refs: List[ModuleRef]
  device_data_dep_refs: List[ModuleRef]


class SoongPrebuiltTarget(Target):
  """Class for generating a Soong prebuilt target on disk."""

  @staticmethod
  def create(
      gen: WorkspaceGenerator, info: Dict[str, Any], package_name: str = ''
  ):
    module_name = info['module_name']

    configs = [
        Config('host', gen.host_out_path),
        Config('device', gen.resource_manager.get_product_out_file_path()),
    ]

    installed_paths = get_module_installed_paths(
        info, gen.resource_manager.get_src_file_path()
    )
    config_files = group_paths_by_config(configs, installed_paths)

    # For test modules, we only create symbolic link to the 'testcases'
    # directory since the information in module-info is not accurate.
    if gen.mod_info.is_tradefed_testable_module(info):
      config_files = {
          c: [c.out_path.joinpath(f'testcases/{module_name}')]
          for c in config_files.keys()
      }

    enabled_features = gen.enabled_features

    return SoongPrebuiltTarget(
        info,
        package_name,
        config_files,
        Dependencies(
            static_dep_refs=find_static_dep_refs(
                gen.mod_info,
                info,
                configs,
                gen.resource_manager.get_src_file_path(),
                enabled_features,
            ),
            runtime_dep_refs=find_runtime_dep_refs(
                gen.mod_info,
                info,
                configs,
                gen.resource_manager.get_src_file_path(),
                enabled_features,
            ),
            data_dep_refs=find_data_dep_refs(
                gen.mod_info,
                info,
                configs,
                gen.resource_manager.get_src_file_path(),
            ),
            device_data_dep_refs=find_device_data_dep_refs(gen, info),
        ),
        [
            c
            for c in configs
            if c.name
            in map(str.lower, info.get(constants.MODULE_SUPPORTED_VARIANTS, []))
        ],
    )

  def __init__(
      self,
      info: Dict[str, Any],
      package_name: str,
      config_files: Dict[Config, List[Path]],
      deps: Dependencies,
      supported_configs: List[Config],
  ):
    self._target_name = info[constants.MODULE_INFO_ID]
    self._module_name = info[constants.MODULE_NAME]
    self._package_name = package_name
    self.config_files = config_files
    self.deps = deps
    self.suites = info.get(constants.MODULE_COMPATIBILITY_SUITES, [])
    self._supported_configs = supported_configs

  def name(self) -> str:
    return self._target_name

  def package_name(self) -> str:
    return self._package_name

  def required_imports(self) -> Set[Import]:
    return {
        Import('//bazel/rules:soong_prebuilt.bzl', self._rule_name()),
    }

  @functools.lru_cache(maxsize=128)
  def supported_configs(self) -> Set[Config]:
    # We deduce the supported configs from the installed paths since the
    # build exports incorrect metadata for some module types such as
    # Robolectric. The information exported from the build is only used if
    # the module does not have any installed paths.
    # TODO(b/232929584): Remove this once all modules correctly export the
    #  supported variants.
    supported_configs = set(self.config_files.keys())
    if supported_configs:
      return supported_configs

    return self._supported_configs

  def dependencies(self) -> List[ModuleRef]:
    all_deps = set(self.deps.runtime_dep_refs)
    all_deps.update(self.deps.data_dep_refs)
    all_deps.update(self.deps.device_data_dep_refs)
    all_deps.update(self.deps.static_dep_refs)
    return list(all_deps)

  def write_to_build_file(self, f: IO):
    writer = IndentWriter(f)
    build_file_writer = BuildFileWriter(writer)

    writer.write_line(f'{self._rule_name()}(')

    with writer.indent():
      writer.write_line(f'name = "{self._target_name}",')
      writer.write_line(f'module_name = "{self._module_name}",')
      self._write_files_attribute(writer)
      self._write_deps_attribute(
          writer, 'static_deps', self.deps.static_dep_refs
      )
      self._write_deps_attribute(
          writer, 'runtime_deps', self.deps.runtime_dep_refs
      )
      self._write_deps_attribute(writer, 'data', self.deps.data_dep_refs)

      build_file_writer.write_label_list_attribute(
          'device_data', self.deps.device_data_dep_refs
      )
      build_file_writer.write_string_list_attribute(
          'suites', sorted(self.suites)
      )

    writer.write_line(')')

  def create_filesystem_layout(self, package_dir: Path):
    prebuilts_dir = package_dir.joinpath(self._target_name)
    prebuilts_dir.mkdir()

    for config, files in self.config_files.items():
      config_prebuilts_dir = prebuilts_dir.joinpath(config.name)
      config_prebuilts_dir.mkdir()

      for f in files:
        rel_path = f.relative_to(config.out_path)
        symlink = config_prebuilts_dir.joinpath(rel_path)
        symlink.parent.mkdir(parents=True, exist_ok=True)
        symlink.symlink_to(f)

  def _rule_name(self):
    return (
        'soong_prebuilt' if self.config_files else 'soong_uninstalled_prebuilt'
    )

  def _write_files_attribute(self, writer: IndentWriter):
    if not self.config_files:
      return

    writer.write('files = ')
    write_config_select(
        writer,
        self.config_files,
        lambda c, _: writer.write(
            f'glob(["{self._target_name}/{c.name}/**/*"])'
        ),
    )
    writer.write_line(',')

  def _write_deps_attribute(self, writer, attribute_name, module_refs):
    config_deps = filter_configs(
        group_targets_by_config(r.target() for r in module_refs),
        self.supported_configs(),
    )

    if not config_deps:
      return

    for config in self.supported_configs():
      config_deps.setdefault(config, [])

    writer.write(f'{attribute_name} = ')
    write_config_select(
        writer,
        config_deps,
        lambda _, targets: write_target_list(writer, targets),
    )
    writer.write_line(',')


def group_paths_by_config(
    configs: List[Config], paths: List[Path]
) -> Dict[Config, List[Path]]:

  config_files = defaultdict(list)

  for f in paths:
    matching_configs = [c for c in configs if _is_relative_to(f, c.out_path)]

    if not matching_configs:
      continue

    # The path can only appear in ANDROID_HOST_OUT for host target or
    # ANDROID_PRODUCT_OUT, but cannot appear in both.
    if len(matching_configs) > 1:
      raise ValueError(
          f'Installed path `{f}` is not in'
          ' ANDROID_HOST_OUT or ANDROID_PRODUCT_OUT'
      )

    config_files[matching_configs[0]].append(f)

  return config_files


def group_targets_by_config(
    targets: List[Target],
) -> Dict[Config, List[Target]]:

  config_to_targets = defaultdict(list)

  for target in targets:
    for config in target.supported_configs():
      config_to_targets[config].append(target)

  return config_to_targets


def filter_configs(
    config_dict: Dict[Config, Any],
    configs: Set[Config],
) -> Dict[Config, Any]:
  return {k: v for (k, v) in config_dict.items() if k in configs}


def _is_relative_to(path1: Path, path2: Path) -> bool:
  """Return True if the path is relative to another path or False."""
  # Note that this implementation is required because Path.is_relative_to only
  # exists starting with Python 3.9.
  try:
    path1.relative_to(path2)
    return True
  except ValueError:
    return False


def get_module_installed_paths(
    info: Dict[str, Any], src_root_path: Path
) -> List[Path]:

  # Install paths in module-info are usually relative to the Android
  # source root ${ANDROID_BUILD_TOP}. When the output directory is
  # customized by the user however, the install paths are absolute.
  def resolve(install_path_string):
    install_path = Path(install_path_string)
    if not install_path.expanduser().is_absolute():
      return src_root_path.joinpath(install_path)
    return install_path

  return map(resolve, info.get(constants.MODULE_INSTALLED, []))


def find_runtime_dep_refs(
    mod_info: module_info.ModuleInfo,
    info: module_info.Module,
    configs: List[Config],
    src_root_path: Path,
    enabled_features: List[Features],
) -> List[ModuleRef]:
  """Return module references for runtime dependencies."""

  # We don't use the `dependencies` module-info field for shared libraries
  # since it's ambiguous and could generate more targets and pull in more
  # dependencies than necessary. In particular, libraries that support both
  # static and dynamic linking could end up becoming runtime dependencies
  # even though the build specifies static linking. For example, if a target
  # 'T' is statically linked to 'U' which supports both variants, the latter
  # still appears as a dependency. Since we can't tell, this would result in
  # the shared library variant of 'U' being added on the library path.
  libs = set()
  libs.update(info.get(constants.MODULE_SHARED_LIBS, []))
  libs.update(info.get(constants.MODULE_RUNTIME_DEPS, []))

  if Features.EXPERIMENTAL_JAVA_RUNTIME_DEPENDENCIES in enabled_features:
    libs.update(info.get(constants.MODULE_LIBS, []))

  runtime_dep_refs = _find_module_refs(mod_info, configs, src_root_path, libs)

  runtime_library_class = {'RLIB_LIBRARIES', 'DYLIB_LIBRARIES'}
  # We collect rlibs even though they are technically static libraries since
  # they could refer to dylibs which are required at runtime. Generating
  # Bazel targets for these intermediate modules keeps the generator simple
  # and preserves the shape (isomorphic) of the Soong structure making the
  # workspace easier to debug.
  for dep_name in info.get(constants.MODULE_DEPENDENCIES, []):
    dep_info = mod_info.get_module_info(dep_name)
    if not dep_info:
      continue
    if not runtime_library_class.intersection(
        dep_info.get(constants.MODULE_CLASS, [])
    ):
      continue
    runtime_dep_refs.append(ModuleRef.for_info(dep_info))

  return runtime_dep_refs


def find_data_dep_refs(
    mod_info: module_info.ModuleInfo,
    info: module_info.Module,
    configs: List[Config],
    src_root_path: Path,
) -> List[ModuleRef]:
  """Return module references for data dependencies."""

  return _find_module_refs(
      mod_info, configs, src_root_path, info.get(constants.MODULE_DATA_DEPS, [])
  )


def find_device_data_dep_refs(
    gen: WorkspaceGenerator,
    info: module_info.Module,
) -> List[ModuleRef]:
  """Return module references for device data dependencies."""

  return _find_module_refs(
      gen.mod_info,
      [Config('device', gen.resource_manager.get_product_out_file_path())],
      gen.resource_manager.get_src_file_path(),
      info.get(constants.MODULE_TARGET_DEPS, []),
  )


def find_static_dep_refs(
    mod_info: module_info.ModuleInfo,
    info: module_info.Module,
    configs: List[Config],
    src_root_path: Path,
    enabled_features: List[Features],
) -> List[ModuleRef]:
  """Return module references for static libraries."""

  if Features.EXPERIMENTAL_JAVA_RUNTIME_DEPENDENCIES not in enabled_features:
    return []

  static_libs = set()
  static_libs.update(info.get(constants.MODULE_STATIC_LIBS, []))
  static_libs.update(info.get(constants.MODULE_STATIC_DEPS, []))

  return _find_module_refs(mod_info, configs, src_root_path, static_libs)


def _find_module_refs(
    mod_info: module_info.ModuleInfo,
    configs: List[Config],
    src_root_path: Path,
    module_names: List[str],
) -> List[ModuleRef]:
  """Return module references for modules."""

  module_refs = []

  for name in module_names:
    info = mod_info.get_module_info(name)
    if not info:
      continue

    installed_paths = get_module_installed_paths(info, src_root_path)
    config_files = group_paths_by_config(configs, installed_paths)
    if not config_files:
      continue

    module_refs.append(ModuleRef.for_info(info))

  return module_refs


class IndentWriter:

  def __init__(self, f: IO):
    self._file = f
    self._indent_level = 0
    self._indent_string = 4 * ' '
    self._indent_next = True

  def write_line(self, text: str = ''):
    if text:
      self.write(text)

    self._file.write('\n')
    self._indent_next = True

  def write(self, text):
    if self._indent_next:
      self._file.write(self._indent_string * self._indent_level)
      self._indent_next = False

    self._file.write(text)

  @contextlib.contextmanager
  def indent(self):
    self._indent_level += 1
    yield
    self._indent_level -= 1


def write_config_select(
    writer: IndentWriter,
    config_dict: Dict[Config, Any],
    write_value_fn: Callable,
):
  writer.write_line('select({')

  with writer.indent():
    for config, value in sorted(config_dict.items(), key=lambda c: c[0].name):

      writer.write(f'"//bazel/rules:{config.name}": ')
      write_value_fn(config, value)
      writer.write_line(',')

  writer.write('})')


def write_target_list(writer: IndentWriter, targets: List[Target]):
  writer.write_line('[')

  with writer.indent():
    for label in sorted(set(t.qualified_name() for t in targets)):
      writer.write_line(f'"{label}",')

  writer.write(']')


def _decorate_find_method(mod_info, finder_method_func, host, enabled_features):
  """A finder_method decorator to override TestInfo properties."""

  def use_bazel_runner(finder_obj, test_id):
    test_infos = finder_method_func(finder_obj, test_id)
    if not test_infos:
      return test_infos
    for tinfo in test_infos:
      m_info = mod_info.get_module_info(tinfo.test_name)

      # TODO(b/262200630): Refactor the duplicated logic in
      # _decorate_find_method() and _add_test_module_targets() to
      # determine whether a test should run with Atest Bazel Mode.

      # Only enable modern Robolectric tests since those are the only ones
      # TF currently supports.
      if mod_info.is_modern_robolectric_test(m_info):
        if Features.EXPERIMENTAL_ROBOLECTRIC_TEST in enabled_features:
          tinfo.test_runner = BazelTestRunner.NAME
        continue

      # Only run device-driven tests in Bazel mode when '--host' is not
      # specified and the feature is enabled.
      if not host and mod_info.is_device_driven_test(m_info):
        if Features.EXPERIMENTAL_DEVICE_DRIVEN_TEST in enabled_features:
          tinfo.test_runner = BazelTestRunner.NAME
        continue

      if mod_info.is_suite_in_compatibility_suites(
          'host-unit-tests', m_info
      ) or (
          Features.EXPERIMENTAL_HOST_DRIVEN_TEST in enabled_features
          and mod_info.is_host_driven_test(m_info)
      ):
        tinfo.test_runner = BazelTestRunner.NAME
    return test_infos

  return use_bazel_runner


def create_new_finder(
    mod_info: module_info.ModuleInfo,
    finder: test_finder_base.TestFinderBase,
    host: bool,
    enabled_features: List[Features] = None,
):
  """Create new test_finder_base.Finder with decorated find_method.

  Args:
    mod_info: ModuleInfo object.
    finder: Test Finder class.
    host: Whether to run the host variant.
    enabled_features: List of enabled features.

  Returns:
      List of ordered find methods.
  """
  return test_finder_base.Finder(
      finder.test_finder_instance,
      _decorate_find_method(
          mod_info, finder.find_method, host, enabled_features or []
      ),
      finder.finder_info,
  )


class RunCommandError(subprocess.CalledProcessError):
  """CalledProcessError but including debug information when it fails."""

  def __str__(self):
    return f'{super().__str__()}\nstdout={self.stdout}\n\nstderr={self.stderr}'


def default_run_command(args: List[str], cwd: Path) -> str:
  result = subprocess.run(
      args=args,
      cwd=cwd,
      text=True,
      capture_output=True,
      check=False,
  )
  if result.returncode:
    # Provide a more detailed log message including stdout and stderr.
    raise RunCommandError(
        result.returncode, result.args, result.stdout, result.stderr
    )
  return result.stdout


@dataclasses.dataclass
class BuildMetadata:
  build_branch: str
  build_target: str


class BazelTestRunner(trb.TestRunnerBase):
  """Bazel Test Runner class."""

  NAME = 'BazelTestRunner'
  EXECUTABLE = 'none'

  # pylint: disable=redefined-outer-name
  # pylint: disable=too-many-arguments
  def __init__(
      self,
      results_dir,
      mod_info: module_info.ModuleInfo,
      extra_args: Dict[str, Any] = None,
      src_top: Path = None,
      workspace_path: Path = None,
      run_command: Callable = default_run_command,
      build_metadata: BuildMetadata = None,
      env: Dict[str, str] = None,
      generate_workspace_fn: Callable = generate_bazel_workspace,
      enabled_features: Set[str] = None,
      **kwargs,
  ):
    super().__init__(results_dir, **kwargs)
    self.mod_info = mod_info
    self.src_top = src_top or Path(os.environ.get(constants.ANDROID_BUILD_TOP))
    self.starlark_file = _get_resource_root().joinpath(
        'format_as_soong_module_name.cquery'
    )

    self.bazel_workspace = workspace_path or get_bazel_workspace_dir()
    self.bazel_binary = self.bazel_workspace.joinpath('bazel.sh')
    self.run_command = run_command
    self._extra_args = extra_args or {}
    self.build_metadata = build_metadata or get_default_build_metadata()
    self.env = env or os.environ
    self._generate_workspace_fn = generate_workspace_fn
    self._enabled_features = (
        enabled_features
        if enabled_features is not None
        else atest_configs.GLOBAL_ARGS.bazel_mode_features
    )

  # pylint: disable=unused-argument
  def run_tests(self, test_infos, extra_args, reporter):
    """Run the list of test_infos.

    Args:
        test_infos: List of TestInfo.
        extra_args: Dict of extra args to add to test run.
        reporter: An instance of result_report.ResultReporter.
    """
    ret_code = ExitCode.SUCCESS

    try:
      run_cmds = self.generate_run_commands(test_infos, extra_args)
    except AbortRunException as e:
      atest_utils.colorful_print(f'Stop running test(s): {e}', constants.RED)
      return ExitCode.ERROR

    for run_cmd in run_cmds:
      subproc = self.run(run_cmd, output_to_stdout=True)
      ret_code |= self.wait_for_subprocess(subproc)

    self.organize_test_logs(test_infos)

    return ret_code

  def organize_test_logs(self, test_infos: List[test_info.TestInfo]):
    for t_info in test_infos:
      test_output_dir, package_name, target_suffix = (
          self.retrieve_test_output_info(t_info)
      )
      if test_output_dir.joinpath(TEST_OUTPUT_ZIP_NAME).exists():
        # TEST_OUTPUT_ZIP file exist when BES uploading is enabled.
        # Showing the BES link to users instead of the local log.
        continue

      # AtestExecutionInfo will find all log files in 'results_dir/log'
      # directory and generate an HTML file to display to users when
      # 'results_dir/log' directory exist.
      log_path = Path(self.results_dir).joinpath(
          'log', f'{package_name}', f'{t_info.test_name}_{target_suffix}'
      )
      log_path.parent.mkdir(parents=True, exist_ok=True)
      if not log_path.is_symlink():
        log_path.symlink_to(test_output_dir)

  def _get_feature_config_or_warn(self, feature, env_var_name):
    feature_config = self.env.get(env_var_name)
    if not feature_config:
      logging.warning(
          'Ignoring `%s` because the `%s` environment variable is not set.',
          # pylint: disable=no-member
          feature,
          env_var_name,
      )
    return feature_config

  def _get_bes_publish_args(self, feature: Features) -> List[str]:
    bes_publish_config = self._get_feature_config_or_warn(
        feature, 'ATEST_BAZEL_BES_PUBLISH_CONFIG'
    )

    if not bes_publish_config:
      return []

    branch = self.build_metadata.build_branch
    target = self.build_metadata.build_target

    return [
        f'--config={bes_publish_config}',
        f'--build_metadata=ab_branch={branch}',
        f'--build_metadata=ab_target={target}',
    ]

  def _get_remote_args(self, feature):
    remote_config = self._get_feature_config_or_warn(
        feature, 'ATEST_BAZEL_REMOTE_CONFIG'
    )
    if not remote_config:
      return []
    return [f'--config={remote_config}']

  def _get_remote_avd_args(self, feature):
    remote_avd_config = self._get_feature_config_or_warn(
        feature, 'ATEST_BAZEL_REMOTE_AVD_CONFIG'
    )
    if not remote_avd_config:
      raise ValueError(
          'Cannot run remote device test because '
          'ATEST_BAZEL_REMOTE_AVD_CONFIG '
          'environment variable is not set.'
      )
    return [f'--config={remote_avd_config}']

  def host_env_check(self):
    """Check that host env has everything we need.

    We actually can assume the host env is fine because we have the same
    requirements that atest has. Update this to check for android env vars
    if that changes.
    """

  def get_test_runner_build_reqs(self, test_infos) -> Set[str]:
    if not test_infos:
      return set()

    self._generate_workspace_fn(
        self.mod_info,
        self._enabled_features,
    )

    deps_expression = ' + '.join(
        sorted(self.test_info_target_label(i) for i in test_infos)
    )

    with tempfile.NamedTemporaryFile() as query_file:
      with open(query_file.name, 'w', encoding='utf-8') as _query_file:
        _query_file.write(f'deps(tests({deps_expression}))')

      query_args = [
          str(self.bazel_binary),
          'cquery',
          f'--query_file={query_file.name}',
          '--output=starlark',
          f'--starlark:file={self.starlark_file}',
      ]

      output = self.run_command(query_args, self.bazel_workspace)

    targets = set()
    robolectric_tests = set(
        filter(
            self._is_robolectric_test_suite,
            [test.test_name for test in test_infos],
        )
    )

    modules_to_variant = _parse_cquery_output(output)

    for module, variants in modules_to_variant.items():

      # Skip specifying the build variant for Robolectric test modules
      # since they are special. Soong builds them with the `target`
      # variant although are installed as 'host' modules.
      if module in robolectric_tests:
        targets.add(module)
        continue

      targets.add(_soong_target_for_variants(module, variants))

    return targets

  def _is_robolectric_test_suite(self, module_name: str) -> bool:
    return self.mod_info.is_robolectric_test_suite(
        self.mod_info.get_module_info(module_name)
    )

  def test_info_target_label(self, test: test_info.TestInfo) -> str:
    module_name = test.test_name
    info = self.mod_info.get_module_info(module_name)
    package_name = info.get(constants.MODULE_PATH)[0]
    target_suffix = self.get_target_suffix(info)

    return f'//{package_name}:{module_name}_{target_suffix}'

  def retrieve_test_output_info(
      self, test_info: test_info.TestInfo
  ) -> Tuple[Path, str, str]:
    """Return test output information.

    Args:
        test_info (test_info.TestInfo): Information about the test.

    Returns:
        Tuple[Path, str, str]: A tuple containing the following elements:
            - test_output_dir (Path): Absolute path of the test output
                folder.
            - package_name (str): Name of the package.
            - target_suffix (str): Target suffix.
    """
    module_name = test_info.test_name
    info = self.mod_info.get_module_info(module_name)
    package_name = info.get(constants.MODULE_PATH)[0]
    target_suffix = self.get_target_suffix(info)

    test_output_dir = Path(
        self.bazel_workspace,
        BAZEL_TEST_LOGS_DIR_NAME,
        package_name,
        f'{module_name}_{target_suffix}',
        TEST_OUTPUT_DIR_NAME,
    )

    return test_output_dir, package_name, target_suffix

  def get_target_suffix(self, info: Dict[str, Any]) -> str:
    """Return 'host' or 'device' accordingly to the variant of the test."""
    if not self._extra_args.get(
        constants.HOST, False
    ) and self.mod_info.is_device_driven_test(info):
      return 'device'
    return 'host'

  @staticmethod
  def _get_bazel_feature_args(
      feature: Features, extra_args: Dict[str, Any], generator: Callable
  ) -> List[str]:
    if feature not in extra_args.get('BAZEL_MODE_FEATURES', []):
      return []
    return generator(feature)

  # pylint: disable=unused-argument
  def generate_run_commands(self, test_infos, extra_args, port=None):
    """Generate a list of run commands from TestInfos.

    Args:
        test_infos: A set of TestInfo instances.
        extra_args: A Dict of extra args to append.
        port: Optional. An int of the port number to send events to.

    Returns:
        A list of run commands to run the tests.
    """
    startup_options = ''
    bazelrc = self.env.get('ATEST_BAZELRC')

    if bazelrc:
      startup_options = f'--bazelrc={bazelrc}'

    target_patterns = ' '.join(
        self.test_info_target_label(i) for i in test_infos
    )

    bazel_args = parse_args(test_infos, extra_args)

    # If BES is not enabled, use the option of
    # '--nozip_undeclared_test_outputs' to not compress the test outputs.
    # And the URL of test outputs will be printed in terminal.
    bazel_args.extend(
        self._get_bazel_feature_args(
            Features.EXPERIMENTAL_BES_PUBLISH,
            extra_args,
            self._get_bes_publish_args,
        )
        or ['--nozip_undeclared_test_outputs']
    )
    bazel_args.extend(
        self._get_bazel_feature_args(
            Features.EXPERIMENTAL_REMOTE, extra_args, self._get_remote_args
        )
    )
    bazel_args.extend(
        self._get_bazel_feature_args(
            Features.EXPERIMENTAL_REMOTE_AVD,
            extra_args,
            self._get_remote_avd_args,
        )
    )

    # This is an alternative to shlex.join that doesn't exist in Python
    # versions < 3.8.
    bazel_args_str = ' '.join(shlex.quote(arg) for arg in bazel_args)

    # Use 'cd' instead of setting the working directory in the subprocess
    # call for a working --dry-run command that users can run.
    return [
        f'cd {self.bazel_workspace} && '
        f'{self.bazel_binary} {startup_options} '
        f'test {target_patterns} {bazel_args_str}'
    ]


def parse_args(
    test_infos: List[test_info.TestInfo], extra_args: Dict[str, Any]
) -> Dict[str, Any]:
  """Parse commandline args and passes supported args to bazel.

  Args:
      test_infos: A set of TestInfo instances.
      extra_args: A Dict of extra args to append.

  Returns:
      A list of args to append to the run command.
  """

  args_to_append = []
  # Make a copy of the `extra_args` dict to avoid modifying it for other
  # Atest runners.
  extra_args_copy = extra_args.copy()

  # Remove the `--host` flag since we already pass that in the rule's
  # implementation.
  extra_args_copy.pop(constants.HOST, None)

  # Remove the serial arg since Bazel mode does not support device tests and
  # the serial / -s arg conflicts with the TF null device option specified in
  # the rule implementation (-n).
  extra_args_copy.pop(constants.SERIAL, None)

  # Map args to their native Bazel counterparts.
  for arg in _SUPPORTED_BAZEL_ARGS:
    if arg not in extra_args_copy:
      continue
    args_to_append.extend(_map_to_bazel_args(arg, extra_args_copy[arg]))
    # Remove the argument since we already mapped it to a Bazel option
    # and no longer need it mapped to a Tradefed argument below.
    del extra_args_copy[arg]

  # TODO(b/215461642): Store the extra_args in the top-level object so
  # that we don't have to re-parse the extra args to get BAZEL_ARG again.
  tf_args, _ = tfr.extra_args_to_tf_args(extra_args_copy)

  # Add ATest include filter argument to allow testcase filtering.
  tf_args.extend(tfr.get_include_filter(test_infos))

  args_to_append.extend([f'--test_arg={i}' for i in tf_args])

  # Disable test result caching when wait-for-debugger flag is set.
  if '--wait-for-debugger' in tf_args:
    # Remove the --cache_test_results flag if it's already set.
    args_to_append = [
        arg
        for arg in args_to_append
        if not arg.startswith('--cache_test_results')
    ]
    args_to_append.append('--cache_test_results=no')

  # Default to --test_output=errors unless specified otherwise
  if not any(arg.startswith('--test_output=') for arg in args_to_append):
    args_to_append.append('--test_output=errors')

  # Default to --test_summary=detailed unless specified otherwise, or if the
  # feature is disabled
  if not any(arg.startswith('--test_summary=') for arg in args_to_append) and (
      Features.NO_BAZEL_DETAILED_SUMMARY
      not in extra_args.get('BAZEL_MODE_FEATURES', [])
  ):
    args_to_append.append('--test_summary=detailed')

  return args_to_append


def _map_to_bazel_args(arg: str, arg_value: Any) -> List[str]:
  return (
      _SUPPORTED_BAZEL_ARGS[arg](arg_value)
      if arg in _SUPPORTED_BAZEL_ARGS
      else []
  )


def _parse_cquery_output(output: str) -> Dict[str, Set[str]]:
  module_to_build_variants = defaultdict(set)

  for line in filter(bool, map(str.strip, output.splitlines())):
    module_name, build_variant = line.split(':')
    module_to_build_variants[module_name].add(build_variant)

  return module_to_build_variants


def _soong_target_for_variants(
    module_name: str, build_variants: Set[str]
) -> str:

  if not build_variants:
    raise ValueError(
        f'Missing the build variants for module {module_name} in cquery output!'
    )

  if len(build_variants) > 1:
    return module_name

  return f'{module_name}-{_CONFIG_TO_VARIANT[list(build_variants)[0]]}'
