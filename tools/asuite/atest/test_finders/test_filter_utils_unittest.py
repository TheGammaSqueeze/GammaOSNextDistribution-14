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

"""Unittests for test_finder_utils."""


import os
import tempfile
import unittest

from atest import unittest_constants as uc
from atest import unittest_utils
from atest.test_finders import test_filter_utils


class TestFinderUtilsUnittests(unittest.TestCase):

  def test_split_methods(self):
    """Test _split_methods method."""
    # Class
    unittest_utils.assert_strict_equal(
        self,
        test_filter_utils.split_methods('Class.Name'),
        ('Class.Name', set()),
    )
    unittest_utils.assert_strict_equal(
        self,
        test_filter_utils.split_methods('Class.Name#Method'),
        ('Class.Name', {'Method'}),
    )
    unittest_utils.assert_strict_equal(
        self,
        test_filter_utils.split_methods('Class.Name#Method,Method2'),
        ('Class.Name', {'Method', 'Method2'}),
    )
    unittest_utils.assert_strict_equal(
        self,
        test_filter_utils.split_methods('Class.Name#Method,Method2'),
        ('Class.Name', {'Method', 'Method2'}),
    )
    unittest_utils.assert_strict_equal(
        self,
        test_filter_utils.split_methods('Class.Name#Method,Method2'),
        ('Class.Name', {'Method', 'Method2'}),
    )
    self.assertRaises(
        test_filter_utils.TooManyMethodsError,
        test_filter_utils.split_methods,
        'class.name#Method,class.name.2#method',
    )
    self.assertRaises(
        test_filter_utils.MoreThanOneClassError,
        test_filter_utils.split_methods,
        'class.name1,class.name2,class.name3',
    )
    # Path
    unittest_utils.assert_strict_equal(
        self,
        test_filter_utils.split_methods('foo/bar/class.java'),
        ('foo/bar/class.java', set()),
    )
    unittest_utils.assert_strict_equal(
        self,
        test_filter_utils.split_methods('foo/bar/class.java#Method'),
        ('foo/bar/class.java', {'Method'}),
    )
    # Multiple parameters
    unittest_utils.assert_strict_equal(
        self,
        test_filter_utils.split_methods('Class.Name#method[1],method[2,[3,4]]'),
        ('Class.Name', {'method[1]', 'method[2,[3,4]]'}),
    )
    unittest_utils.assert_strict_equal(
        self,
        test_filter_utils.split_methods('Class.Name#method[1],method[[2,3],4]'),
        ('Class.Name', {'method[1]', 'method[[2,3],4]'}),
    )

  def test_is_parameterized_java_class(self):
    """Test is_parameterized_java_class method."""

    matched_contents = (
        ['@ParameterizedTest'],
        ['@RunWith(Parameterized.class)'],
        ['@RunWith(Parameterized::class)'],
        [' @RunWith( Parameterized.class ) '],
        ['@RunWith(TestParameterInjector.class)'],
        ['@RunWith(JUnitParamsRunner.class)'],
        ['@RunWith(DataProviderRunner.class)'],
        ['@RunWith(JukitoRunner.class)'],
        ['@RunWith(Theories.class)'],
        ['@RunWith(BedsteadJUnit4.class)'],
    )
    not_matched_contents = (
        ['// @RunWith(Parameterized.class)'],
        ['*RunWith(Parameterized.class)'],
        ['// @ParameterizedTest'],
    )
    # Test matched patterns
    for matched_content in matched_contents:
      try:
        tmp_file = tempfile.NamedTemporaryFile(mode='wt')
        tmp_file.writelines(matched_content)
        tmp_file.flush()

        self.assertTrue(
            test_filter_utils.is_parameterized_java_class(tmp_file.name)
        )
      finally:
        tmp_file.close()

    # Test not matched patterns
    for not_matched_content in not_matched_contents:
      try:
        tmp_file = tempfile.NamedTemporaryFile(mode='wt')
        tmp_file.writelines(not_matched_content)
        tmp_file.flush()

        self.assertFalse(
            test_filter_utils.is_parameterized_java_class(tmp_file.name)
        )
      finally:
        tmp_file.close()

  def test_get_package_name(self):
    """Test get_package_name"""
    package_name = 'com.test.hello_world_test'
    target_java = os.path.join(
        uc.TEST_DATA_DIR, 'class_file_path_testing', 'hello_world_test.java'
    )
    self.assertEqual(
        package_name, test_filter_utils.get_package_name(target_java)
    )
    target_kt = os.path.join(
        uc.TEST_DATA_DIR, 'class_file_path_testing', 'hello_world_test.kt'
    )
    self.assertEqual(
        package_name, test_filter_utils.get_package_name(target_kt)
    )
