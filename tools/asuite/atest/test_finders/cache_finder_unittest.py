#!/usr/bin/env python3
#
# Copyright 2019, The Android Open Source Project
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

"""Unittests for cache_finder."""

import os
import unittest
from unittest import mock
from atest import atest_utils
from atest import constants
from atest import module_info
from atest import unittest_constants as uc
from atest.test_finders import cache_finder
from atest.test_finders import test_info
from pyfakefs import fake_filesystem_unittest


# pylint: disable=protected-access
class CacheFinderUnittests(unittest.TestCase):
  """Unit tests for cache_finder.py"""

  def setUp(self):
    """Set up stuff for testing."""
    self.cache_finder = cache_finder.CacheFinder()
    self.cache_finder.module_info = mock.Mock(spec=module_info.ModuleInfo)

  @mock.patch.object(atest_utils, 'get_cache_root')
  def test_find_test_by_cache_cache_not_exist(self, mock_get_cache_root):
    """Test find_test_by_cache input not cached test."""
    not_cached_test = 'mytest1'
    mock_get_cache_root.return_value = os.path.join(
        uc.TEST_DATA_DIR, 'cache_root'
    )

    self.assertIsNone(self.cache_finder.find_test_by_cache(not_cached_test))

  @mock.patch.object(
      cache_finder.CacheFinder, '_is_test_infos_valid', return_value=True
  )
  @mock.patch.object(atest_utils, 'get_cache_root')
  def test_find_test_by_cache_cache_exist_and_valid(
      self, mock_get_cache_root, _mock_is_info_valid
  ):
    """Test find_test_by_cache input valid cached test."""
    cached_test = 'hello_world_test'
    mock_get_cache_root.return_value = os.path.join(
        uc.TEST_DATA_DIR, 'cache_root'
    )

    self.assertIsNotNone(self.cache_finder.find_test_by_cache(cached_test))

  @mock.patch.object(
      cache_finder.CacheFinder, '_is_test_filter_valid', return_value=True
  )
  @mock.patch.object(
      cache_finder.CacheFinder, '_is_test_build_target_valid', return_value=True
  )
  @mock.patch.object(
      cache_finder.CacheFinder, '_is_test_path_valid', return_value=True
  )
  @mock.patch.object(atest_utils, 'load_test_info_cache')
  def test_find_test_by_cache_wo_latest_info(
      self,
      mock_load_cache,
      _mock_path_valid,
      _mock_build_target_valid,
      _mock_filer_valid,
  ):
    """Test find_test_by_cache cached not valid path."""
    cached_test = 'hello_world_test'
    latest_test_info = test_info.TestInfo(None, None, None)
    # Add a new attribute to make it different with current one.
    latest_test_info.__setattr__('new_key', 1)
    mock_load_cache.return_value = {latest_test_info}

    self.assertIsNone(self.cache_finder.find_test_by_cache(cached_test))

  @mock.patch.object(
      cache_finder.CacheFinder, '_is_test_build_target_valid', return_value=True
  )
  @mock.patch.object(atest_utils, 'get_cache_root')
  def test_find_test_by_cache_wo_valid_path(
      self, mock_get_cache_root, _mock_build_target_valid
  ):
    """Test find_test_by_cache cached not valid path."""
    cached_test = 'hello_world_test'
    mock_get_cache_root.return_value = os.path.join(
        uc.TEST_DATA_DIR, 'cache_root'
    )
    # Mock the path to make it not the same as the sample cache.
    self.cache_finder.module_info.get_paths.return_value = [
        'not/matched/test/path'
    ]

    self.assertIsNone(self.cache_finder.find_test_by_cache(cached_test))

  @mock.patch.object(
      cache_finder.CacheFinder, '_is_test_path_valid', return_value=True
  )
  @mock.patch.object(atest_utils, 'get_cache_root')
  def test_find_test_by_cache_wo_valid_build_target(
      self, mock_get_cache_root, _mock_path_valid
  ):
    """Test find_test_by_cache method cached not valid build targets."""
    cached_test = 'hello_world_test'
    mock_get_cache_root.return_value = os.path.join(
        uc.TEST_DATA_DIR, 'cache_root'
    )
    # Always return None for is_module function to simulate checking valid
    # build target.
    self.cache_finder.module_info.is_module.return_value = None

    self.assertIsNone(self.cache_finder.find_test_by_cache(cached_test))

  @mock.patch.object(
      cache_finder.CacheFinder, '_is_test_filter_valid', return_value=False
  )
  @mock.patch.object(
      cache_finder.CacheFinder, '_is_test_build_target_valid', return_value=True
  )
  @mock.patch.object(
      cache_finder.CacheFinder, '_is_test_path_valid', return_value=True
  )
  @mock.patch.object(atest_utils, 'get_cache_root')
  def test_find_test_by_cache_wo_valid_java_filter(
      self,
      mock_get_cache_root,
      _mock_path_valid,
      _mock_build_target_valid,
      _mock_filer_valid,
  ):
    """Test _is_test_filter_valid method cached not valid java filter."""
    cached_test = 'hello_world_test'
    mock_get_cache_root.return_value = os.path.join(
        uc.TEST_DATA_DIR, 'cache_root'
    )

    self.assertIsNone(self.cache_finder.find_test_by_cache(cached_test))

  def test_is_test_build_target_valid_module_in(self):
    """Test _is_test_build_target_valid method if target has MODULES-IN."""
    t_info = test_info.TestInfo(
        'mock_name', 'mock_runner', {'MODULES-IN-my-test-dir'}
    )
    self.cache_finder.module_info.is_module.return_value = False
    self.assertTrue(self.cache_finder._is_test_build_target_valid(t_info))

  def test_is_test_build_target_valid(self):
    """Test _is_test_build_target_valid method."""
    t_info = test_info.TestInfo('mock_name', 'mock_runner', {'my-test-target'})
    self.cache_finder.module_info.is_module.return_value = False
    self.assertFalse(self.cache_finder._is_test_build_target_valid(t_info))


class CacheFinderTestFilterUnittests(fake_filesystem_unittest.TestCase):
  """Unit tests for cache_finder.py"""

  def setUp(self):
    """Set up stuff for testing."""
    self.setUpPyfakefs()
    self.cache_finder = cache_finder.CacheFinder()
    self.cache_finder.module_info = mock.Mock(spec=module_info.ModuleInfo)

  def test_is_class_in_module_for_java_class(self):
    """Test _is_class_in_module method if input is java class."""
    self.fs.create_file(
        'src/a/b/c/MyTestClass.java',
        contents='package android.test;\npublic class MyTestClass {\n',
    )
    mock_mod = {constants.MODULE_SRCS: ['src/a/b/c/MyTestClass.java']}
    self.cache_finder.module_info.get_module_info.return_value = mock_mod

    # Should not match if class name does not exist.
    self.assertFalse(
        self.cache_finder._is_class_in_module('MyModule', 'a.b.c.MyTestClass')
    )
    # Should match if class name exist.
    self.assertTrue(
        self.cache_finder._is_class_in_module(
            'MyModule', 'android.test.MyTestClass'
        )
    )

  def test_is_class_in_module_for_java_package(self):
    """Test _is_class_in_module method if input is java package."""
    self.fs.create_file(
        'src/a/b/c/MyTestClass.java',
        contents='package android.test;\npublic class MyTestClass {\n',
    )
    mock_mod = {constants.MODULE_SRCS: ['src/a/b/c/MyTestClass.java']}
    self.cache_finder.module_info.get_module_info.return_value = mock_mod

    # Should not match if package name does not match the src.
    self.assertFalse(self.cache_finder._is_class_in_module('MyModule', 'a.b.c'))
    # Should match if package name matches the src.
    self.assertTrue(
        self.cache_finder._is_class_in_module('MyModule', 'android.test')
    )


if __name__ == '__main__':
  unittest.main()
