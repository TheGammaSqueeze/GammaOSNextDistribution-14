#!/usr/bin/env python3
#
# Copyright 2018, The Android Open Source Project
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

"""Unittests for module_info."""

# pylint: disable=invalid-name
# pylint: disable=missing-function-docstring
# pylint: disable=too-many-lines

import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest import mock
from atest import constants
from atest import module_info
from atest import unittest_constants as uc
from atest import unittest_utils
from pyfakefs import fake_filesystem_unittest

JSON_FILE_PATH = os.path.join(uc.TEST_DATA_DIR, uc.JSON_FILE)
CC_DEP_PATH = os.path.join(uc.TEST_DATA_DIR, uc.CC_DEP_FILE)
JAVA_DEP_PATH = os.path.join(uc.TEST_DATA_DIR, uc.JAVA_DEP_FILE)
EXPECTED_MOD_TARGET = 'tradefed'
EXPECTED_MOD_TARGET_PATH = ['tf/core']
UNEXPECTED_MOD_TARGET = 'this_should_not_be_in_module-info.json'
MOD_NO_PATH = 'module-no-path'
PATH_TO_MULT_MODULES = 'shared/path/to/be/used'
MULT_MOODULES_WITH_SHARED_PATH = ['module2', 'module1']
PATH_TO_MULT_MODULES_WITH_MULTI_ARCH = 'shared/path/to/be/used2'
TESTABLE_MODULES_WITH_SHARED_PATH = [
    'multiarch1',
    'multiarch2',
    'multiarch3',
    'multiarch3_32',
]

ROBO_MOD_PATH = ['/shared/robo/path']
ROBO_MODULE = 'FooTests'
ASSOCIATED_ROBO_MODULE = 'RunFooTests'
ROBO_MODULE_INFO = {
    constants.MODULE_NAME: ROBO_MODULE,
    constants.MODULE_PATH: ROBO_MOD_PATH,
    constants.MODULE_CLASS: [constants.MODULE_CLASS_JAVA_LIBRARIES],
}
ASSOCIATED_ROBO_MODULE_INFO = {
    constants.MODULE_NAME: ASSOCIATED_ROBO_MODULE,
    constants.MODULE_PATH: ROBO_MOD_PATH,
    constants.MODULE_CLASS: [constants.MODULE_CLASS_ROBOLECTRIC],
}
MOD_PATH_INFO_DICT = {
    ROBO_MOD_PATH[0]: [ASSOCIATED_ROBO_MODULE_INFO, ROBO_MODULE_INFO]
}
MOD_NAME_INFO_DICT = {
    ASSOCIATED_ROBO_MODULE: ASSOCIATED_ROBO_MODULE_INFO,
    ROBO_MODULE: ROBO_MODULE_INFO,
}
MOD_NAME1 = 'mod1'
MOD_NAME2 = 'mod2'
MOD_NAME3 = 'mod3'
MOD_NAME4 = 'mod4'
MOD_INFO_DICT = {}
MODULE_INFO = {
    constants.MODULE_NAME: 'random_name',
    constants.MODULE_PATH: 'a/b/c/path',
    constants.MODULE_CLASS: ['random_class'],
}
NAME_TO_MODULE_INFO = {'random_name': MODULE_INFO}

MOBLY_MODULE = 'mobly-test'
MOBLY_MODULE_NO_TAG = 'mobly-test-no-tag'

# Mocking path allows str only, use os.path instead of Path.
with tempfile.TemporaryDirectory() as temp_dir:
  BUILD_TOP_DIR = temp_dir
SOONG_OUT_DIR = os.path.join(BUILD_TOP_DIR, 'out/soong')
PRODUCT_OUT_DIR = os.path.join(BUILD_TOP_DIR, 'out/target/product/vsoc_x86_64')
HOST_OUT_DIR = os.path.join(BUILD_TOP_DIR, 'out/host/linux-x86')


# TODO: (b/263199608) Suppress too-many-public-methods after refactoring.
# pylint: disable=protected-access, too-many-public-methods
class ModuleInfoUnittests(unittest.TestCase):
  """Unit tests for module_info.py"""

  def setUp(self) -> None:
    for path in [BUILD_TOP_DIR, PRODUCT_OUT_DIR, SOONG_OUT_DIR, HOST_OUT_DIR]:
      if not Path(path).is_dir():
        Path(path).mkdir(parents=True)
    shutil.copy2(JSON_FILE_PATH, PRODUCT_OUT_DIR)
    self.json_file_path = Path(PRODUCT_OUT_DIR).joinpath(uc.JSON_FILE)
    shutil.copy2(CC_DEP_PATH, SOONG_OUT_DIR)
    self.cc_dep_path = Path(SOONG_OUT_DIR).joinpath(uc.CC_DEP_FILE)
    shutil.copy2(JAVA_DEP_PATH, SOONG_OUT_DIR)
    self.java_dep_path = Path(SOONG_OUT_DIR).joinpath(uc.JAVA_DEP_FILE)
    self.merged_dep_path = Path(PRODUCT_OUT_DIR).joinpath(uc.MERGED_DEP_FILE)

  def tearDown(self) -> None:
    if self.merged_dep_path.is_file():
      os.remove(self.merged_dep_path)

  def test_target_name_is_relative_to_build_top(self):
    build_top = '/src/build_top'
    product_out = '/src/build_top/pout'
    env_mock = {
        constants.ANDROID_BUILD_TOP: build_top,
        constants.ANDROID_PRODUCT_OUT: product_out,
    }
    expected_target = os.path.relpath(
        os.path.join(product_out, 'module-info.json'), build_top
    )

    with mock.patch.dict('os.environ', env_mock, clear=True):
      actual_target = module_info.get_module_info_target()

      self.assertEqual(actual_target, expected_target)

  def test_target_name_is_in_absolute_path(self):
    build_top = '/src/build_top'
    product_out = '/somewhere/pout'
    env_mock = {
        constants.ANDROID_BUILD_TOP: build_top,
        constants.ANDROID_PRODUCT_OUT: product_out,
    }
    expected_target = os.path.join(product_out, 'module-info.json')

    with mock.patch.dict('os.environ', env_mock, clear=True):
      actual_target = module_info.get_module_info_target()

      self.assertEqual(actual_target, expected_target)

  @mock.patch.object(module_info.Loader, 'load')
  def test_get_path_to_module_info(self, mock_load_module):
    """Test that we correctly create the path to module info dict."""
    mod_one = 'mod1'
    mod_two = 'mod2'
    mod_path_one = '/path/to/mod1'
    mod_path_two = '/path/to/mod2'
    mod_info_dict = {
        mod_one: {
            constants.MODULE_PATH: [mod_path_one],
            constants.MODULE_NAME: mod_one,
        },
        mod_two: {
            constants.MODULE_PATH: [mod_path_two],
            constants.MODULE_NAME: mod_two,
        },
    }
    mock_load_module.return_value = mod_info_dict
    path_to_mod_info = {
        mod_path_one: [{
            constants.MODULE_NAME: mod_one,
            constants.MODULE_PATH: [mod_path_one],
        }],
        mod_path_two: [{
            constants.MODULE_NAME: mod_two,
            constants.MODULE_PATH: [mod_path_two],
        }],
    }
    self.assertDictEqual(
        path_to_mod_info, module_info.get_path_to_module_info(mod_info_dict)
    )

  def test_is_module(self):
    """Test that we get the module when it's properly loaded."""
    # Load up the test json file and check that module is in it
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    self.assertTrue(mod_info.is_module(EXPECTED_MOD_TARGET))
    self.assertFalse(mod_info.is_module(UNEXPECTED_MOD_TARGET))

  def test_get_path(self):
    """Test that we get the module path when it's properly loaded."""
    # Load up the test json file and check that module is in it
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    self.assertEqual(
        mod_info.get_paths(EXPECTED_MOD_TARGET), EXPECTED_MOD_TARGET_PATH
    )
    self.assertEqual(mod_info.get_paths(MOD_NO_PATH), [])

  def test_get_module_names(self):
    """test that we get the module name properly."""
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    self.assertEqual(
        mod_info.get_module_names(EXPECTED_MOD_TARGET_PATH[0]),
        [EXPECTED_MOD_TARGET],
    )
    unittest_utils.assert_strict_equal(
        self,
        mod_info.get_module_names(PATH_TO_MULT_MODULES),
        MULT_MOODULES_WITH_SHARED_PATH,
    )

  def test_path_to_mod_info(self):
    """test that we get the module name properly."""
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    module_list = []
    for path_to_mod_info in mod_info.path_to_module_info[
        PATH_TO_MULT_MODULES_WITH_MULTI_ARCH
    ]:
      module_list.append(path_to_mod_info.get(constants.MODULE_NAME))
    module_list.sort()
    TESTABLE_MODULES_WITH_SHARED_PATH.sort()
    self.assertEqual(module_list, TESTABLE_MODULES_WITH_SHARED_PATH)

  def test_is_suite_in_compatibility_suites(self):
    """Test is_suite_in_compatibility_suites."""
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    info = {'compatibility_suites': []}
    self.assertFalse(mod_info.is_suite_in_compatibility_suites('cts', info))
    info2 = {'compatibility_suites': ['cts']}
    self.assertTrue(mod_info.is_suite_in_compatibility_suites('cts', info2))
    self.assertFalse(mod_info.is_suite_in_compatibility_suites('vts10', info2))
    info3 = {'compatibility_suites': ['cts', 'vts10']}
    self.assertTrue(mod_info.is_suite_in_compatibility_suites('cts', info3))
    self.assertTrue(mod_info.is_suite_in_compatibility_suites('vts10', info3))
    self.assertFalse(mod_info.is_suite_in_compatibility_suites('ats', info3))

  def test_get_testable_modules(self):
    """Test get_testable_modules."""
    expected_testable_modules = {'Module1', 'Module2', 'Module3'}
    expected_test_suite_modules = {'Module1', 'Module2'}
    expected_null_suite_modules = {'Module3'}
    mod_info = create_module_info(
        modules=[
            test_module(name='Module1', compatibility_suites=['test-suite']),
            test_module(name='Module2', compatibility_suites=['test-suite']),
            test_module(name='Module3'),
            non_test_module(name='Dep1'),
        ]
    )

    actual_all_testable_modules = mod_info.get_testable_modules()
    actual_test_suite_modules = mod_info.get_testable_modules('test-suite')
    actual_null_suite_modules = mod_info.get_testable_modules('null-suite')

    self.assertEqual(actual_all_testable_modules, expected_testable_modules)
    self.assertEqual(actual_test_suite_modules, expected_test_suite_modules)
    self.assertEqual(actual_null_suite_modules, expected_null_suite_modules)

  @mock.patch.dict(
      'os.environ',
      {
          constants.ANDROID_BUILD_TOP: '/',
          constants.ANDROID_PRODUCT_OUT: PRODUCT_OUT_DIR,
      },
  )
  def test_is_mobly_test(self):
    """Test is_mobly_test."""
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    self.assertTrue(
        mod_info.is_mobly_module(mod_info.get_module_info(MOBLY_MODULE))
    )
    self.assertFalse(
        mod_info.is_mobly_module(mod_info.get_module_info(MOBLY_MODULE_NO_TAG))
    )

  @mock.patch.dict(
      'os.environ',
      {
          constants.ANDROID_BUILD_TOP: '/',
          constants.ANDROID_PRODUCT_OUT: PRODUCT_OUT_DIR,
      },
  )
  @mock.patch.object(module_info.ModuleInfo, 'get_robolectric_type')
  def test_is_robolectric_test(self, mock_type):
    """Test is_robolectric_test."""
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    mock_type.return_value = constants.ROBOTYPE_MODERN
    self.assertTrue(mod_info.is_robolectric_test(ROBO_MODULE))
    mock_type.return_value = constants.ROBOTYPE_LEGACY
    self.assertTrue(mod_info.is_robolectric_test(ROBO_MODULE))
    mock_type.return_value = 0
    self.assertFalse(mod_info.is_robolectric_test(ROBO_MODULE))

  @mock.patch.object(module_info.ModuleInfo, 'is_module')
  def test_is_auto_gen_test_config(self, mock_is_module):
    """Test is_auto_gen_test_config correctly detects the module."""
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    mock_is_module.return_value = True
    is_auto_test_config = {'auto_test_config': [True]}
    is_not_auto_test_config = {'auto_test_config': [False]}
    is_not_auto_test_config_again = {'auto_test_config': []}
    MOD_INFO_DICT[MOD_NAME1] = is_auto_test_config
    MOD_INFO_DICT[MOD_NAME2] = is_not_auto_test_config
    MOD_INFO_DICT[MOD_NAME3] = is_not_auto_test_config_again
    MOD_INFO_DICT[MOD_NAME4] = {}
    mod_info.name_to_module_info = MOD_INFO_DICT
    self.assertTrue(mod_info.is_auto_gen_test_config(MOD_NAME1))
    self.assertFalse(mod_info.is_auto_gen_test_config(MOD_NAME2))
    self.assertFalse(mod_info.is_auto_gen_test_config(MOD_NAME3))
    self.assertFalse(mod_info.is_auto_gen_test_config(MOD_NAME4))

  def test_merge_build_system_infos(self):
    """Test _merge_build_system_infos."""
    loader = module_info.Loader(
        module_file=JSON_FILE_PATH, need_merge_fn=lambda: True
    )
    mod_info_1 = {
        constants.MODULE_NAME: 'module_1',
        constants.MODULE_DEPENDENCIES: [],
    }
    name_to_mod_info = {'module_1': mod_info_1}
    expect_deps = ['test_dep_level_1_1', 'test_dep_level_1_2']
    name_to_mod_info = loader._merge_build_system_infos(
        name_to_mod_info, java_bp_info_path=self.java_dep_path
    )
    self.assertEqual(
        name_to_mod_info['module_1'].get(constants.MODULE_DEPENDENCIES),
        expect_deps,
    )

  def test_merge_build_system_infos_missing_keys(self):
    """Test _merge_build_system_infos for keys missing from module-info.json."""
    loader = module_info.Loader(
        module_file=JSON_FILE_PATH, need_merge_fn=lambda: True
    )
    name_to_mod_info = loader._merge_build_system_infos(
        {}, java_bp_info_path=self.java_dep_path
    )

    expect_deps = ['test_dep_level_1_1']
    self.assertEqual(
        name_to_mod_info['not_in_module_info'].get(
            constants.MODULE_DEPENDENCIES
        ),
        expect_deps,
    )

  def test_merge_dependency_with_ori_dependency(self):
    """Test _merge_dependency."""
    loader = module_info.Loader(
        module_file=JSON_FILE_PATH, need_merge_fn=lambda: True
    )
    mod_info_1 = {
        constants.MODULE_NAME: 'module_1',
        constants.MODULE_DEPENDENCIES: ['ori_dep_1'],
    }
    name_to_mod_info = {'module_1': mod_info_1}
    expect_deps = ['ori_dep_1', 'test_dep_level_1_1', 'test_dep_level_1_2']
    name_to_mod_info = loader._merge_build_system_infos(
        name_to_mod_info, java_bp_info_path=self.java_dep_path
    )
    self.assertEqual(
        name_to_mod_info['module_1'].get(constants.MODULE_DEPENDENCIES),
        expect_deps,
    )

  @mock.patch.dict(
      'os.environ',
      {
          constants.ANDROID_BUILD_TOP: uc.TEST_DATA_DIR,
          constants.ANDROID_PRODUCT_OUT: PRODUCT_OUT_DIR,
      },
  )
  def test_get_instrumentation_target_apps(self):
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    artifacts = {
        'AmSlam': {
            os.path.join(
                uc.TEST_DATA_DIR,
                'out/target/product/generic/data/app/AmSlam/AmSlam.apk',
            )
        }
    }
    # 1. If Android.bp is available, use `manifest` to determine the actual
    # manifest.
    bp_context = """android_test    {
            name: "AmSlamTests",
            manifest: 'AndroidManifest.xml',
            instrumentation_for: "AmSlam"
        }"""
    bp_file = os.path.join(uc.TEST_DATA_DIR, 'foo/bar/AmSlam/test/Android.bp')
    with open(bp_file, 'w', encoding='utf-8') as cache:
      cache.write(bp_context)
    self.assertEqual(
        mod_info.get_instrumentation_target_apps('AmSlamTests'), artifacts
    )
    os.remove(bp_file)
    # 2. If Android.bp is unavailable, search `AndroidManifest.xml`
    # arbitrarily.
    self.assertEqual(
        mod_info.get_instrumentation_target_apps('AmSlamTests'), artifacts
    )

  @mock.patch.dict(
      'os.environ',
      {
          constants.ANDROID_BUILD_TOP: uc.TEST_DATA_DIR,
          constants.ANDROID_PRODUCT_OUT: PRODUCT_OUT_DIR,
      },
  )
  def test_get_target_module_by_pkg(self):
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    self.assertEqual(
        'AmSlam',
        mod_info.get_target_module_by_pkg(
            package='c0m.andr0id.settingS',
            search_from=Path(uc.TEST_DATA_DIR).joinpath('foo/bar/AmSlam/test'),
        ),
    )

  @mock.patch.dict(
      'os.environ',
      {
          constants.ANDROID_BUILD_TOP: uc.TEST_DATA_DIR,
          constants.ANDROID_PRODUCT_OUT: PRODUCT_OUT_DIR,
      },
  )
  def test_get_artifact_map(self):
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    artifacts = {
        'AmSlam': {
            os.path.join(
                uc.TEST_DATA_DIR,
                'out/target/product/generic/data/app/AmSlam/AmSlam.apk',
            )
        }
    }
    self.assertEqual(mod_info.get_artifact_map('AmSlam'), artifacts)

  @mock.patch.dict(
      'os.environ',
      {
          constants.ANDROID_BUILD_TOP: uc.TEST_DATA_DIR,
          constants.ANDROID_PRODUCT_OUT: PRODUCT_OUT_DIR,
      },
  )
  def test_get_filepath_from_module(self):
    """Test for get_filepath_from_module."""
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)

    expected_filepath = Path(uc.TEST_DATA_DIR).joinpath(
        'foo/bar/AmSlam', 'AndroidManifest.xml'
    )
    self.assertEqual(
        mod_info.get_filepath_from_module('AmSlam', 'AndroidManifest.xml'),
        expected_filepath,
    )

    expected_filepath = Path(uc.TEST_DATA_DIR).joinpath(
        'foo/bar/AmSlam/test', 'AndroidManifest.xml'
    )
    self.assertEqual(
        mod_info.get_filepath_from_module('AmSlamTests', 'AndroidManifest.xml'),
        expected_filepath,
    )

  def test_get_module_dependency(self):
    """Test get_module_dependency."""
    loader = module_info.Loader(
        module_file=JSON_FILE_PATH, need_merge_fn=lambda: True
    )
    mod_info = loader.load()
    expect_deps = {
        'test_dep_level_1_1',
        'module_1',
        'test_dep_level_1_2',
        'test_dep_level_2_2',
        'test_dep_level_2_1',
        'module_2',
    }
    loader._merge_build_system_infos(
        loader.name_to_module_info, java_bp_info_path=self.java_dep_path
    )
    self.assertEqual(
        mod_info.get_module_dependency('dep_test_module'), expect_deps
    )

  def test_get_module_dependency_w_loop(self):
    """Test get_module_dependency with problem dep file."""
    loader = module_info.Loader(
        module_file=JSON_FILE_PATH, need_merge_fn=lambda: True
    )
    mod_info = loader.load()
    # Java dependency file with a endless loop define.
    java_dep_file = os.path.join(
        uc.TEST_DATA_DIR, 'module_bp_java_loop_deps.json'
    )
    expect_deps = {
        'test_dep_level_1_1',
        'module_1',
        'test_dep_level_1_2',
        'test_dep_level_2_2',
        'test_dep_level_2_1',
        'module_2',
    }
    loader._merge_build_system_infos(
        loader.name_to_module_info, java_bp_info_path=java_dep_file
    )
    self.assertEqual(
        mod_info.get_module_dependency('dep_test_module'), expect_deps
    )

  def test_get_install_module_dependency(self):
    """Test get_install_module_dependency."""
    loader = module_info.Loader(
        module_file=JSON_FILE_PATH, need_merge_fn=lambda: True
    )
    mod_info = loader.load()
    expect_deps = {'module_1', 'test_dep_level_2_1'}
    loader._merge_build_system_infos(
        loader.name_to_module_info, java_bp_info_path=self.java_dep_path
    )
    self.assertEqual(
        mod_info.get_install_module_dependency('dep_test_module'), expect_deps
    )

  def test_cc_merge_build_system_infos(self):
    """Test _merge_build_system_infos for cc."""
    loader = module_info.Loader(
        module_file=JSON_FILE_PATH, need_merge_fn=lambda: True
    )
    mod_info_1 = {
        constants.MODULE_NAME: 'module_cc_1',
        constants.MODULE_DEPENDENCIES: [],
    }
    name_to_mod_info = {'module_cc_1': mod_info_1}
    expect_deps = ['test_cc_dep_level_1_1', 'test_cc_dep_level_1_2']
    name_to_mod_info = loader._merge_build_system_infos(
        name_to_mod_info, cc_bp_info_path=self.cc_dep_path
    )
    self.assertEqual(
        name_to_mod_info['module_cc_1'].get(constants.MODULE_DEPENDENCIES),
        expect_deps,
    )

  def test_is_unit_test(self):
    """Test is_unit_test."""
    module_name = 'myModule'
    maininfo_with_unittest = {
        constants.MODULE_NAME: module_name,
        constants.MODULE_IS_UNIT_TEST: 'true',
    }
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    self.assertTrue(mod_info.is_unit_test(maininfo_with_unittest))

  def test_is_host_unit_test(self):
    """Test is_host_unit_test."""
    module_name = 'myModule'
    maininfo_with_host_unittest = {
        constants.MODULE_NAME: module_name,
        constants.MODULE_IS_UNIT_TEST: 'true',
        'compatibility_suites': ['host-unit-tests'],
        constants.MODULE_INSTALLED: uc.DEFAULT_INSTALL_PATH,
        'auto_test_config': ['true'],
    }

    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)

    self.assertTrue(mod_info.is_host_unit_test(maininfo_with_host_unittest))

  def test_is_device_driven_test(self):
    module_name = 'myModule'
    maininfo_with_device_driven_test = {
        constants.MODULE_NAME: module_name,
        constants.MODULE_TEST_CONFIG: [
            os.path.join(uc.TEST_CONFIG_DATA_DIR, 'a.xml.data')
        ],
        constants.MODULE_INSTALLED: uc.DEFAULT_INSTALL_PATH,
        'supported_variants': ['DEVICE'],
    }
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)

    self.assertTrue(
        mod_info.is_device_driven_test(maininfo_with_device_driven_test)
    )

  def test_not_device_driven_test_when_suite_is_robolectric_test(self):
    module_name = 'myModule'
    maininfo_with_device_driven_test = {
        constants.MODULE_NAME: module_name,
        constants.MODULE_TEST_CONFIG: [
            os.path.join(uc.TEST_CONFIG_DATA_DIR, 'a.xml.data')
        ],
        constants.MODULE_INSTALLED: uc.DEFAULT_INSTALL_PATH,
        'supported_variants': ['DEVICE'],
        'compatibility_suites': ['robolectric-tests'],
    }
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)

    self.assertFalse(
        mod_info.is_device_driven_test(maininfo_with_device_driven_test)
    )

  def test_is_host_driven_test(self):
    """Test is_host_driven_test."""
    test_name = 'myModule'
    expected_host_driven_info = {
        constants.MODULE_NAME: test_name,
        constants.MODULE_TEST_CONFIG: [
            os.path.join(uc.TEST_CONFIG_DATA_DIR, 'a.xml.data')
        ],
        constants.MODULE_INSTALLED: uc.DEFAULT_INSTALL_PATH,
        'supported_variants': ['HOST'],
    }
    mod_info = create_module_info([
        module(
            name=test_name,
            test_config=[os.path.join(uc.TEST_CONFIG_DATA_DIR, 'a.xml.data')],
            installed=uc.DEFAULT_INSTALL_PATH,
            supported_variants=['HOST'],
        )
    ])

    return_value = mod_info.is_host_driven_test(expected_host_driven_info)

    self.assertTrue(return_value)

  # TODO: (b/264015241) Stop mocking build variables.
  # TODO: (b/263199608) Re-write the test after refactoring module-info.py
  @mock.patch.dict(
      'os.environ',
      {
          constants.ANDROID_BUILD_TOP: uc.ATEST_PKG_DIR,
          constants.ANDROID_PRODUCT_OUT: PRODUCT_OUT_DIR,
      },
  )
  def test_has_mainline_modules(self):
    """Test has_mainline_modules."""
    name1 = 'MainModule1'
    mainline_module1 = ['foo2.apk', 'foo3.apk']
    name2 = 'MainModule2'
    mainline_module2 = ['foo1.apex']
    name3 = 'MainModule3'

    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    # found in 'test_mainlne_modules' attribute.
    self.assertTrue(mod_info.has_mainline_modules(name1, mainline_module1))
    # found in the value of 'mainline-param' in test_config.
    self.assertTrue(mod_info.has_mainline_modules(name2, mainline_module2))
    # cannot be found in both 'test_mainline_modules' and 'test_config'.
    self.assertFalse(mod_info.has_mainline_modules(name3, mainline_module2))

  # TODO: (b/264015241) Stop mocking build variables.
  # TODO: (b/263199608) Re-write the test after refactoring module-info.py
  @mock.patch.dict(
      'os.environ',
      {
          constants.ANDROID_BUILD_TOP: os.path.dirname(__file__),
          constants.ANDROID_PRODUCT_OUT: PRODUCT_OUT_DIR,
      },
  )
  def test_get_module_info_for_multi_lib_module(self):
    my_module_name = 'MyMultiArchTestModule'
    multi_arch_json = os.path.join(
        uc.TEST_DATA_DIR, 'multi_arch_module-info.json'
    )
    mod_info = module_info.load_from_file(module_file=multi_arch_json)

    self.assertIsNotNone(mod_info.get_module_info(my_module_name))

  def test_get_modules_by_include_deps_w_testable_module_only_false(self):
    module_1 = module(
        name='module_1',
        dependencies=['dep1', 'dep2'],
    )
    module_2 = module(name='module_2', dependencies=['dep1', 'dep3'])
    mod_info = create_module_info([module_1, module_2])

    self.assertEqual(
        {'module_1', 'module_2'},
        mod_info.get_modules_by_include_deps(
            {'dep1'}, testable_module_only=False
        ),
    )
    self.assertEqual(
        {'module_1'},
        mod_info.get_modules_by_include_deps(
            {'dep2'}, testable_module_only=False
        ),
    )
    self.assertEqual(
        {'module_2'},
        mod_info.get_modules_by_include_deps(
            {'dep3'}, testable_module_only=False
        ),
    )

  @mock.patch.object(module_info.ModuleInfo, 'get_testable_modules')
  def test_get_modules_by_include_deps_w_testable_module_only_true(
      self, _testable_modules
  ):
    module_1 = module(
        name='module_1',
        dependencies=['dep1', 'dep2'],
    )
    module_2 = module(name='module_2', dependencies=['dep1', 'dep3'])
    mod_info = create_module_info([module_1, module_2])
    _testable_modules.return_value = []

    self.assertEqual(
        set(),
        mod_info.get_modules_by_include_deps(
            {'dep1'}, testable_module_only=True
        ),
    )

  def test_get_modules_by_path_in_srcs_no_module_found(self):
    module_1 = module(
        name='module_1',
        srcs=['path/src1', 'path/src2'],
    )
    module_2 = module(name='module_2', srcs=['path/src2', 'path/src3'])
    mod_info = create_module_info([module_1, module_2])

    self.assertEqual(set(), mod_info.get_modules_by_path_in_srcs('path/src4'))

  def test_get_modules_by_path_in_srcs_one_module_found(self):
    module_1 = module(
        name='module_1',
        srcs=['path/src1', 'path/src2'],
    )
    module_2 = module(name='module_2', srcs=['path/src2', 'path/src3'])
    mod_info = create_module_info([module_1, module_2])

    self.assertEqual(
        {'module_1'}, mod_info.get_modules_by_path_in_srcs('path/src1')
    )

  def test_get_modules_by_path_in_srcs_multiple_module_found(self):
    module_1 = module(
        name='module_1',
        srcs=['path/src1', 'path/src2'],
    )
    module_2 = module(name='module_2', srcs=['path/src2', 'path/src3'])
    mod_info = create_module_info([module_1, module_2])

    self.assertEqual(
        {'module_1', 'module_2'},
        mod_info.get_modules_by_path_in_srcs('path/src2'),
    )

  def test_contains_same_mainline_modules(self):
    mainline_modules = {'A.apex', 'B.apk'}
    self.assertTrue(
        module_info.contains_same_mainline_modules(
            mainline_modules, {'B.apk+A.apex'}
        )
    )
    self.assertFalse(
        module_info.contains_same_mainline_modules(
            mainline_modules, {'B.apk+C.apex'}
        )
    )

  def test_get_installed_paths_have_abs_path(self):
    mod_info = create_module_info(
        [module(name='my_module', installed=[Path('/a/b/c/d')])]
    )

    self.assertEqual(
        mod_info.get_installed_paths('my_module'), [Path('/a/b/c/d')]
    )

  @mock.patch.dict(
      'os.environ', {constants.ANDROID_BUILD_TOP: '/mocked/build_top'}
  )
  def test_get_installed_paths_have_relative_path(self):
    mod_info = create_module_info(
        [module(name='my_module', installed=['a/b/c/d'])]
    )

    self.assertEqual(
        mod_info.get_installed_paths('my_module'),
        [Path('/mocked/build_top/a/b/c/d')],
    )


class ModuleInfoTestFixture(fake_filesystem_unittest.TestCase):
  """Fixture for ModuleInfo tests."""

  def setUp(self):
    self.setUpPyfakefs()

  # pylint: disable=protected-access
  def create_empty_module_info(self):
    fake_temp_file_name = next(tempfile._get_candidate_names())
    self.fs.create_file(fake_temp_file_name, contents='{}')
    return module_info.load_from_file(module_file=fake_temp_file_name)

  def create_module_info(self, modules=None):
    mod_info = self.create_empty_module_info()
    modules = modules or []

    for m in modules:
      mod_info.name_to_module_info[m['module_name']] = m
      for path in m['path']:
        if path in mod_info.path_to_module_info:
          mod_info.path_to_module_info[path].append(m)
        else:
          mod_info.path_to_module_info[path] = [m]

    return mod_info


class HasTestConfonfigTest(ModuleInfoTestFixture):
  """Tests has_test_config in various conditions."""

  def test_return_true_if_test_config_is_not_empty(self):
    test_module_info = module(test_config=['config_file'])
    mod_info = self.create_module_info()

    return_value = mod_info.has_test_config(test_module_info)

    self.assertTrue(return_value)

  def test_return_true_if_auto_test_config_is_not_empty(self):
    test_module_info = module(auto_test_config=['no_empty'])
    mod_info = self.create_module_info()

    return_value = mod_info.has_test_config(test_module_info)

    self.assertTrue(return_value)

  def test_return_false_if_auto_test_config_and_test_config_empty(self):
    test_module_info = module(test_config=[], auto_test_config=[])
    mod_info = self.create_module_info()

    return_value = mod_info.has_test_config(test_module_info)

    self.assertFalse(return_value)


class ModuleInfoCompatibilitySuiteTest(ModuleInfoTestFixture):
  """Tests the compatibility suite in the module info."""

  def test_return_true_if_suite_in_test(self):
    test_module_info = module(compatibility_suites=['test_suite'])
    mod_info = self.create_module_info()

    return_value = mod_info.is_suite_in_compatibility_suites(
        'test_suite', test_module_info
    )

    self.assertTrue(return_value)

  def test_return_false_if_suite_not_in_test(self):
    test_module_info = module(compatibility_suites=['no_suite'])
    mod_info = self.create_module_info()

    return_value = mod_info.is_suite_in_compatibility_suites(
        'test_suite', test_module_info
    )

    self.assertFalse(return_value)

  def test_return_false_when_mod_info_is_empty(self):
    test_module_info = None
    mod_info = self.create_module_info()

    return_value = mod_info.is_suite_in_compatibility_suites(
        'test_suite', test_module_info
    )

    self.assertFalse(return_value)

  def test_return_false_when_mod_info_is_not_a_dict(self):
    test_module_info = ['no_a_dict']
    mod_info = self.create_module_info()

    return_value = mod_info.is_suite_in_compatibility_suites(
        'test_suite', test_module_info
    )

    self.assertFalse(return_value)


class RobolectricTestNameTest(ModuleInfoTestFixture):
  """Tests the Robolectric test name in the module info."""

  def test_return_empty_for_a_modern_robolectric_test(self):
    module_name = 'hello_world_test'
    info = modern_robolectric_test_module(name=f'{module_name}')
    mod_info = self.create_module_info(modules=[info])

    return_module = mod_info.get_robolectric_test_name(info)

    self.assertEqual('', return_module)

  def test_return_related_robolectric_run_module_name(self):
    module_name = 'hello_world_test'
    run_module_name = f'Run{module_name}'
    module_path = 'robolectric_path'
    info = non_test_module(name=f'{module_name}', path=module_path)
    mod_info = self.create_module_info(
        modules=[
            info,
            robolectric_class_non_test_module(
                name=f'{run_module_name}', path=module_path
            ),
        ]
    )

    return_module = mod_info.get_robolectric_test_name(info)

    self.assertEqual(run_module_name, return_module)

  def test_return_empty_when_no_related_robolectic_class_module(self):
    module_name = 'hello_world_test'
    run_module_name = f'Run{module_name}'
    module_path = 'robolectric_path'
    info = non_test_module(name=f'{module_name}', path=module_path)
    mod_info = self.create_module_info(
        modules=[
            info,
            non_test_module(name=f'{run_module_name}', path=module_path),
        ]
    )

    return_module = mod_info.get_robolectric_test_name(info)

    self.assertEqual('', return_module)

  def test_return_empty_if_related_module_name_not_start_with_Run(self):
    module_name = 'hello_world_test'
    run_module_name = f'Not_Run{module_name}'
    module_path = 'robolectric_path'
    info = robolectric_class_non_test_module(
        name=f'{run_module_name}', path=module_path
    )
    mod_info = self.create_module_info(
        modules=[
            non_test_module(name=f'{module_name}', path=module_path),
            info,
        ]
    )

    return_module = mod_info.get_robolectric_test_name(info)

    self.assertEqual('', return_module)

  def test_return_itself_for_a_robolectric_class_test_module(self):
    module_name = 'Run_hello_world_test'
    info = robolectric_class_non_test_module(name=f'{module_name}')
    mod_info = self.create_module_info(modules=[info])

    return_module = mod_info.get_robolectric_test_name(info)

    self.assertEqual(module_name, return_module)

  def test_return_empty_if_robolectric_class_module_not_start_with_Run(self):
    module_name = 'hello_world_test'
    info = robolectric_class_non_test_module(name=f'{module_name}')
    mod_info = self.create_module_info(modules=[info])

    return_module = mod_info.get_robolectric_test_name(info)

    self.assertEqual('', return_module)

  def test_return_0_when_no_mod_info(self):
    module_name = 'hello_world_test'
    info = non_test_module(name=module_name)
    mod_info = self.create_module_info(modules=[info])

    return_module = mod_info.get_robolectric_test_name(info)

    self.assertEqual('', return_module)


class RobolectricTestTypeTest(ModuleInfoTestFixture):
  """Tests the Robolectric test type in the module info."""

  def test_modern_robolectric_test_type(self):
    module_name = 'hello_world_test'
    mod_info = self.create_module_info(
        modules=[
            modern_robolectric_test_module(name=f'{module_name}'),
        ]
    )

    return_value = mod_info.get_robolectric_type(module_name)

    self.assertEqual(return_value, constants.ROBOTYPE_MODERN)

  def test_return_modern_if_compliant_with_modern_and_legacy(self):
    module_name = 'hello_world_test'
    module_path = 'robolectric_path'
    run_module_name = f'Run{module_name}'
    mod_info = self.create_module_info(
        modules=[
            modern_robolectric_test_module(
                name=f'{module_name}', path=module_path
            ),
            robolectric_class_non_test_module(
                name=f'{run_module_name}', path=module_path
            ),
        ]
    )

    return_value = mod_info.get_robolectric_type(module_name)

    self.assertEqual(return_value, constants.ROBOTYPE_MODERN)

  def test_not_modern_robolectric_test_if_suite_is_not_robolectric(self):
    module_name = 'hello_world_test'
    mod_info = self.create_module_info(
        modules=[
            non_test_module(
                name=f'{module_name}',
                compatibility_suites='not_robolectric_tests',
            ),
        ]
    )

    return_value = mod_info.get_robolectric_type(module_name)

    self.assertEqual(return_value, 0)

  def test_legacy_robolectric_test_type_if_has_related_run_robolectric_class_module(
      self,
  ):
    module_name = 'hello_world_test'
    run_module_name = f'Run{module_name}'
    module_path = 'robolectric_path'
    mod_info = self.create_module_info(
        modules=[
            non_test_module(name=f'{module_name}', path=module_path),
            robolectric_class_non_test_module(
                name=f'{run_module_name}', path=module_path
            ),
        ]
    )

    return_value = mod_info.get_robolectric_type(module_name)

    self.assertEqual(return_value, constants.ROBOTYPE_LEGACY)

  def test_not_legacy_robolectric_test_type_if_module_is_tradefed_testable(
      self,
  ):
    module_name = 'hello_world_test'
    run_module_name = f'Run{module_name}'
    module_path = 'robolectric_path'
    mod_info = self.create_module_info(
        modules=[
            test_module(name=f'{module_name}', path=module_path),
            robolectric_class_test_module(
                name=f'{run_module_name}', path=module_path
            ),
        ]
    )

    return_value = mod_info.get_robolectric_type(module_name)

    self.assertEqual(return_value, 0)

  def test_robolectric_class_test_module(self):
    module_name = 'Run_hello_world_test'
    mod_info = self.create_module_info(
        modules=[
            robolectric_class_non_test_module(name=f'{module_name}'),
        ]
    )

    return_value = mod_info.get_robolectric_type(module_name)

    self.assertEqual(return_value, constants.ROBOTYPE_LEGACY)

  def test_not_robolectric_test_if_module_name_not_start_with_Run(self):
    module_name = 'hello_world_test'
    mod_info = self.create_module_info(
        modules=[
            robolectric_class_non_test_module(name=f'{module_name}'),
        ]
    )

    return_value = mod_info.get_robolectric_type(module_name)

    self.assertEqual(return_value, 0)

  def test_return_0_when_no_related_robolectic_class_module(self):
    module_name = 'hello_world_test'
    run_module_name = f'Run{module_name}'
    module_path = 'robolectric_path'
    mod_info = self.create_module_info(
        modules=[
            non_test_module(name=f'{module_name}', path=module_path),
            non_test_module(name=f'{run_module_name}', path=module_path),
        ]
    )

    return_value = mod_info.get_robolectric_type(module_name)

    self.assertEqual(return_value, 0)

  def test_return_0_when_no_related_module_name_start_with_Run(self):
    module_name = 'hello_world_test'
    run_module_name = f'Not_Run{module_name}'
    module_path = 'robolectric_path'
    mod_info = self.create_module_info(
        modules=[
            non_test_module(name=f'{module_name}', path=module_path),
            robolectric_class_non_test_module(
                name=f'{run_module_name}', path=module_path
            ),
        ]
    )

    return_value = mod_info.get_robolectric_type(module_name)

    self.assertEqual(return_value, 0)

  def test_return_0_when_no_mod_info(self):
    module_name = 'hello_world_test'
    mod_info = self.create_module_info()

    return_value = mod_info.get_robolectric_type(module_name)

    self.assertEqual(return_value, 0)


class IsLegacyRobolectricClassTest(ModuleInfoTestFixture):
  """Tests is_legacy_robolectric_class in various conditions."""

  def test_return_true_if_module_class_is_robolectric(self):
    test_module_info = module(classes=[constants.MODULE_CLASS_ROBOLECTRIC])
    mod_info = self.create_module_info()

    return_value = mod_info.is_legacy_robolectric_class(test_module_info)

    self.assertTrue(return_value)

  def test_return_false_if_module_class_is_not_robolectric(self):
    test_module_info = module(classes=['not_robolectric'])
    mod_info = self.create_module_info()

    return_value = mod_info.is_legacy_robolectric_class(test_module_info)

    self.assertFalse(return_value)

  def test_return_false_if_module_class_is_empty(self):
    test_module_info = module(classes=[])
    mod_info = self.create_module_info()

    return_value = mod_info.is_legacy_robolectric_class(test_module_info)

    self.assertFalse(return_value)


class IsTestableModuleTest(ModuleInfoTestFixture):
  """Tests is_testable_module in various conditions."""

  def test_return_true_for_tradefed_testable_module(self):
    info = test_module()
    mod_info = self.create_module_info()

    return_value = mod_info.is_testable_module(info)

    self.assertTrue(return_value)

  def test_return_true_for_modern_robolectric_test_module(self):
    info = modern_robolectric_test_module()
    mod_info = self.create_module_info()

    return_value = mod_info.is_testable_module(info)

    self.assertTrue(return_value)

  def test_return_true_for_legacy_robolectric_test_module(self):
    info = legacy_robolectric_test_module()
    mod_info = self.create_module_info(modules=[info])

    return_value = mod_info.is_testable_module(info)

    self.assertTrue(return_value)

  def test_return_false_for_non_tradefed_testable_module(self):
    info = module(
        auto_test_config=[], test_config=[], installed=['installed_path']
    )
    mod_info = self.create_module_info()

    return_value = mod_info.is_testable_module(info)

    self.assertFalse(return_value)

  def test_return_false_for_no_installed_path_module(self):
    info = module(auto_test_config=['true'], installed=[])
    mod_info = self.create_module_info()

    return_value = mod_info.is_testable_module(info)

    self.assertFalse(return_value)

  def test_return_false_if_module_info_is_empty(self):
    info = {}
    mod_info = self.create_module_info()

    return_value = mod_info.is_testable_module(info)

    self.assertFalse(return_value)


def create_module_info(modules=None):
  name_to_module_info = {}
  modules = modules or []

  for m in modules:
    name_to_module_info[m['module_name']] = m

  return module_info.load_from_dict(name_to_module_info)


def test_module(**kwargs):
  kwargs.setdefault('name', 'hello_world_test')
  return test(module(**kwargs))


def non_test_module(**kwargs):
  kwargs.setdefault('name', 'not_a_test')
  return non_test(module(**kwargs))


def modern_robolectric_test_module(**kwargs):
  kwargs.setdefault('name', 'hello_world_test')
  return test(robolectric_tests_suite(module(**kwargs)))


def legacy_robolectric_test_module(**kwargs):
  kwargs.setdefault('name', 'Run_hello_world_test')
  return robolectric_class_non_test_module(**kwargs)


def robolectric_class_test_module(**kwargs):
  kwargs.setdefault('name', 'hello_world_test')
  return test(robolectric_class(module(**kwargs)))


def robolectric_class_non_test_module(**kwargs):
  kwargs.setdefault('name', 'hello_world_test')
  return non_test(robolectric_class(module(**kwargs)))


# pylint: disable=too-many-arguments, too-many-locals
def module(
    name=None,
    path=None,
    installed=None,
    classes=None,
    auto_test_config=None,
    test_config=None,
    shared_libs=None,
    dependencies=None,
    runtime_dependencies=None,
    data=None,
    data_dependencies=None,
    compatibility_suites=None,
    host_dependencies=None,
    srcs=None,
    supported_variants=None,
):
  name = name or 'libhello'

  m = {}

  m['module_name'] = name
  m['class'] = classes or ['ETC']
  m['path'] = [path or '']
  m['installed'] = installed or []
  m['is_unit_test'] = 'false'
  m['auto_test_config'] = auto_test_config or []
  m['test_config'] = test_config or []
  m['shared_libs'] = shared_libs or []
  m['runtime_dependencies'] = runtime_dependencies or []
  m['dependencies'] = dependencies or []
  m['data'] = data or []
  m['data_dependencies'] = data_dependencies or []
  m['compatibility_suites'] = compatibility_suites or []
  m['host_dependencies'] = host_dependencies or []
  m['srcs'] = srcs or []
  m['supported_variants'] = supported_variants or []
  return m


def test(info):
  info['auto_test_config'] = ['true']
  info['installed'] = ['installed_path']
  return info


def non_test(info):
  info['auto_test_config'] = []
  info['installed'] = []
  return info


def robolectric_class(info):
  info['class'] = ['ROBOLECTRIC']
  return info


def robolectric_tests_suite(info):
  info = test(info)
  info.setdefault('compatibility_suites', []).append('robolectric-tests')
  return info


if __name__ == '__main__':
  unittest.main()
