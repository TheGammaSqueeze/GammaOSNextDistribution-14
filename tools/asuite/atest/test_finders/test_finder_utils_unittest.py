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

"""Unittests for test_finder_utils."""

# pylint: disable=invalid-name
# pylint: disable=missing-function-docstring

import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock

from atest import constants
from atest import module_info
from atest import unittest_constants as uc
from atest import unittest_utils
from atest.test_finders import test_finder_utils
from atest.test_finders import test_info

JSON_FILE_PATH = os.path.join(uc.TEST_DATA_DIR, uc.JSON_FILE)
CLASS_DIR = 'foo/bar/jank/src/android/jank/cts/ui'
OTHER_DIR = 'other/dir/'
OTHER_CLASS_NAME = 'test.java'
CLASS_NAME3 = 'test2'
INT_DIR1 = os.path.join(uc.TEST_DATA_DIR, 'integration_dir_testing/int_dir1')
INT_DIR2 = os.path.join(uc.TEST_DATA_DIR, 'integration_dir_testing/int_dir2')
INT_FILE_NAME = 'int_dir_testing'
FIND_TWO = uc.ROOT + 'other/dir/test.java\n' + uc.FIND_ONE
FIND_THREE = '/a/b/c.java\n/d/e/f.java\n/g/h/i.java'
FIND_THREE_LIST = ['/a/b/c.java', '/d/e/f.java', '/g/h/i.java']
VTS_XML = 'VtsAndroidTest.xml.data'
VTS_BITNESS_XML = 'VtsBitnessAndroidTest.xml'
VTS_PUSH_DIR = 'vts_push_files'
VTS_PLAN_DIR = 'vts_plan_files'
VTS_XML_TARGETS = {
    'VtsTestName',
    'DATA/nativetest/vts_treble_vintf_test/vts_treble_vintf_test',
    'DATA/nativetest64/vts_treble_vintf_test/vts_treble_vintf_test',
    'DATA/lib/libhidl-gen-hash.so',
    'DATA/lib64/libhidl-gen-hash.so',
    'hal-hidl-hash/frameworks/hardware/interfaces/current.txt',
    'hal-hidl-hash/hardware/interfaces/current.txt',
    'hal-hidl-hash/system/hardware/interfaces/current.txt',
    'hal-hidl-hash/system/libhidl/transport/current.txt',
    'target_with_delim',
    'out/dir/target',
    'push_file1_target1',
    'push_file1_target2',
    'push_file2_target1',
    'push_file2_target2',
    'CtsDeviceInfo.apk',
    'DATA/app/sl4a/sl4a.apk',
}
VTS_PLAN_TARGETS = {
    os.path.join(
        uc.TEST_DATA_DIR, VTS_PLAN_DIR, 'vts-staging-default.xml.data'
    ),
    os.path.join(uc.TEST_DATA_DIR, VTS_PLAN_DIR, 'vts-aa.xml.data'),
    os.path.join(uc.TEST_DATA_DIR, VTS_PLAN_DIR, 'vts-bb.xml.data'),
    os.path.join(uc.TEST_DATA_DIR, VTS_PLAN_DIR, 'vts-cc.xml.data'),
    os.path.join(uc.TEST_DATA_DIR, VTS_PLAN_DIR, 'vts-dd.xml.data'),
}
XML_TARGETS = {
    'CtsJankDeviceTestCases',
    'perf-setup',
    'cts-tradefed',
    'GtsEmptyTestApp',
}
PATH_TO_MODULE_INFO_WITH_AUTOGEN = {
    'foo/bar/jank': [{'auto_test_config': True}]
}
PATH_TO_MODULE_INFO_WITH_MULTI_AUTOGEN = {
    'foo/bar/jank': [{'auto_test_config': True}, {'auto_test_config': True}]
}
PATH_TO_MODULE_INFO_WITH_MULTI_AUTOGEN_AND_ROBO = {
    'foo/bar': [{'auto_test_config': True}, {'auto_test_config': True}],
    'foo/bar/jank': [
        {constants.MODULE_CLASS: [constants.MODULE_CLASS_ROBOLECTRIC]}
    ],
}
UNIT_TEST_SEARCH_ROOT = 'my/unit/test/root'
IT_TEST_MATCHED_1_PATH = os.path.join(UNIT_TEST_SEARCH_ROOT, 'sub1')
UNIT_TEST_MATCHED_2_PATH = os.path.join(UNIT_TEST_SEARCH_ROOT, 'sub1', 'sub2')
UNIT_TEST_NOT_MATCHED_1_PATH = os.path.join(
    os.path.dirname(UNIT_TEST_SEARCH_ROOT), 'sub1'
)
UNIT_TEST_MODULE_1 = 'unit_test_module_1'
UNIT_TEST_MODULE_2 = 'unit_test_module_2'
UNIT_TEST_MODULE_3 = 'unit_test_module_3'
DALVIK_TEST_CONFIG = 'AndroidDalvikTest.xml.data'
LIBCORE_TEST_CONFIG = 'AndroidLibCoreTest.xml.data'
DALVIK_XML_TARGETS = XML_TARGETS | test_finder_utils.DALVIK_TEST_DEPS
BUILD_TOP_DIR = tempfile.TemporaryDirectory().name
PRODUCT_OUT_DIR = os.path.join(BUILD_TOP_DIR, 'out/target/product/vsoc_x86_64')
HOST_OUT_DIR = tempfile.NamedTemporaryFile().name


# pylint: disable=protected-access
# pylint: disable=too-many-public-methods
# pylint: disable=unnecessary-comprehension
class TestFinderUtilsUnittests(unittest.TestCase):
  """Unit tests for test_finder_utils.py"""

  @mock.patch.object(
      test_finder_utils, 'has_method_in_file', return_value=False
  )
  @mock.patch('builtins.input', return_value='0')
  def test_extract_test_path(self, _, has_method):
    """Test extract_test_dir method."""
    paths = [os.path.join(uc.ROOT, CLASS_DIR, uc.CLASS_NAME + '.java')]
    unittest_utils.assert_strict_equal(
        self, test_finder_utils.extract_test_path(uc.FIND_ONE), paths
    )
    paths = [os.path.join(uc.ROOT, CLASS_DIR, uc.CLASS_NAME + '.java')]
    unittest_utils.assert_strict_equal(
        self, test_finder_utils.extract_test_path(FIND_TWO), paths
    )
    has_method.return_value = True
    paths = [os.path.join(uc.ROOT, CLASS_DIR, uc.CLASS_NAME + '.java')]
    unittest_utils.assert_strict_equal(
        self, test_finder_utils.extract_test_path(uc.FIND_ONE, 'method'), paths
    )

  def test_has_method_in_file(self):
    """Test has_method_in_file method."""
    test_path = os.path.join(
        uc.TEST_DATA_DIR, 'class_file_path_testing', 'hello_world_test.cc'
    )
    self.assertTrue(
        test_finder_utils.has_method_in_file(
            test_path, frozenset(['PrintHelloWorld'])
        )
    )
    self.assertFalse(
        test_finder_utils.has_method_in_file(
            test_path, frozenset(['PrintHelloWorld1'])
        )
    )
    test_path = os.path.join(
        uc.TEST_DATA_DIR, 'class_file_path_testing', 'hello_world_test.java'
    )
    self.assertTrue(
        test_finder_utils.has_method_in_file(
            test_path, frozenset(['testMethod1'])
        )
    )
    test_path = os.path.join(
        uc.TEST_DATA_DIR, 'class_file_path_testing', 'hello_world_test.java'
    )
    self.assertFalse(
        test_finder_utils.has_method_in_file(
            test_path, frozenset(['testMethod', 'testMethod2'])
        )
    )
    test_path = os.path.join(
        uc.TEST_DATA_DIR, 'class_file_path_testing', 'hello_world_test.java'
    )
    self.assertFalse(
        test_finder_utils.has_method_in_file(
            test_path, frozenset(['testMethod'])
        )
    )

  # TODO: (b/263330492) Stop mocking build environment variables.
  def test_has_method_in_kt_file(self):
    """Test has_method_in_file method with kt class path."""
    test_path = os.path.join(
        uc.TEST_DATA_DIR, 'class_file_path_testing', 'hello_world_test.kt'
    )
    os_environ_mock = {constants.ANDROID_BUILD_TOP: uc.TEST_DATA_DIR}
    with mock.patch.dict('os.environ', os_environ_mock, clear=True):
      self.assertTrue(
          test_finder_utils.has_method_in_file(
              test_path, frozenset(['testMethod1'])
          )
      )
      self.assertFalse(
          test_finder_utils.has_method_in_file(
              test_path, frozenset(['testMethod'])
          )
      )
      self.assertTrue(
          test_finder_utils.has_method_in_file(
              test_path, frozenset(['testMethod1', 'testMethod2'])
          )
      )
      self.assertFalse(
          test_finder_utils.has_method_in_file(
              test_path, frozenset(['testMethod', 'testMethod2'])
          )
      )

  @mock.patch('builtins.input', return_value='1')
  def test_extract_selected_tests(self, mock_input):
    """Test method extract_selected_tests method."""
    tests = []
    self.assertEqual(test_finder_utils.extract_selected_tests(tests), None)
    paths = [os.path.join(uc.ROOT, CLASS_DIR, uc.CLASS_NAME + '.java')]
    unittest_utils.assert_strict_equal(
        self, test_finder_utils.extract_test_path(uc.FIND_ONE), paths
    )
    paths = [os.path.join(uc.ROOT, OTHER_DIR, OTHER_CLASS_NAME)]
    mock_input.return_value = '1'
    unittest_utils.assert_strict_equal(
        self, test_finder_utils.extract_test_path(FIND_TWO), paths
    )
    # Test inputing out-of-range integer or a string
    mock_input.return_value = '100'
    self.assertEqual(
        test_finder_utils.extract_selected_tests(uc.CLASS_NAME), []
    )
    mock_input.return_value = 'lOO'
    self.assertEqual(
        test_finder_utils.extract_selected_tests(uc.CLASS_NAME), []
    )

  @mock.patch('builtins.input', return_value='1')
  def test_extract_test_from_multiselect(self, mock_input):
    """Test method extract_selected_tests method."""
    # selecting 'All'
    paths = ['/a/b/c.java', '/d/e/f.java', '/g/h/i.java']
    mock_input.return_value = '3'
    unittest_utils.assert_strict_equal(
        self,
        sorted(test_finder_utils.extract_selected_tests(FIND_THREE_LIST)),
        sorted(paths),
    )
    # multi-select
    paths = ['/a/b/c.java', '/g/h/i.java']
    mock_input.return_value = '0,2'
    unittest_utils.assert_strict_equal(
        self,
        sorted(test_finder_utils.extract_selected_tests(FIND_THREE_LIST)),
        sorted(paths),
    )
    # selecting a range
    paths = ['/d/e/f.java', '/g/h/i.java']
    mock_input.return_value = '1-2'
    unittest_utils.assert_strict_equal(
        self, test_finder_utils.extract_selected_tests(FIND_THREE_LIST), paths
    )
    # mixed formats
    paths = ['/a/b/c.java', '/d/e/f.java', '/g/h/i.java']
    mock_input.return_value = '0,1-2'
    unittest_utils.assert_strict_equal(
        self,
        sorted(test_finder_utils.extract_selected_tests(FIND_THREE_LIST)),
        sorted(paths),
    )
    # input unsupported formats, return empty
    paths = []
    mock_input.return_value = '?/#'
    unittest_utils.assert_strict_equal(
        self, test_finder_utils.extract_test_path(FIND_THREE), paths
    )

  @mock.patch('os.path.isdir')
  def test_is_equal_or_sub_dir(self, mock_isdir):
    """Test is_equal_or_sub_dir method."""
    self.assertTrue(test_finder_utils.is_equal_or_sub_dir('/a/b/c', '/'))
    self.assertTrue(test_finder_utils.is_equal_or_sub_dir('/a/b/c', '/a'))
    self.assertTrue(test_finder_utils.is_equal_or_sub_dir('/a/b/c', '/a/b/c'))
    self.assertFalse(test_finder_utils.is_equal_or_sub_dir('/a/b', '/a/b/c'))
    self.assertFalse(test_finder_utils.is_equal_or_sub_dir('/a', '/f'))
    mock_isdir.return_value = False
    self.assertFalse(test_finder_utils.is_equal_or_sub_dir('/a/b', '/a'))

  @mock.patch('os.path.isdir', return_value=True)
  @mock.patch('os.path.isfile', side_effect=unittest_utils.isfile_side_effect)
  def test_find_parent_module_dir(self, _isfile, _isdir):
    """Test _find_parent_module_dir method."""
    abs_class_dir = '/%s' % CLASS_DIR
    mock_module_info = mock.Mock(spec=module_info.ModuleInfo)
    mock_module_info.path_to_module_info = {}
    unittest_utils.assert_strict_equal(
        self,
        test_finder_utils.find_parent_module_dir(
            uc.ROOT, abs_class_dir, mock_module_info
        ),
        uc.MODULE_DIR,
    )

  @mock.patch('os.path.isdir', return_value=True)
  @mock.patch('os.path.isfile', return_value=False)
  def test_find_parent_module_dir_with_autogen_config(self, _isfile, _isdir):
    """Test _find_parent_module_dir method."""
    abs_class_dir = '/%s' % CLASS_DIR
    mock_module_info = mock.Mock(spec=module_info.ModuleInfo)
    mock_module_info.path_to_module_info = PATH_TO_MODULE_INFO_WITH_AUTOGEN
    unittest_utils.assert_strict_equal(
        self,
        test_finder_utils.find_parent_module_dir(
            uc.ROOT, abs_class_dir, mock_module_info
        ),
        uc.MODULE_DIR,
    )

  @mock.patch('os.path.isdir', return_value=True)
  @mock.patch('os.path.isfile', side_effect=[False] * 5 + [True])
  def test_find_parent_module_dir_with_autogen_subconfig(self, _isfile, _isdir):
    """Test _find_parent_module_dir method.

    This case is testing when the auto generated config is in a
    sub-directory of a larger test that contains a test config in a parent
    directory.
    """
    abs_class_dir = '/%s' % CLASS_DIR
    mock_module_info = mock.Mock(spec=module_info.ModuleInfo)
    mock_module_info.path_to_module_info = (
        PATH_TO_MODULE_INFO_WITH_MULTI_AUTOGEN
    )
    unittest_utils.assert_strict_equal(
        self,
        test_finder_utils.find_parent_module_dir(
            uc.ROOT, abs_class_dir, mock_module_info
        ),
        uc.MODULE_DIR,
    )

  @mock.patch('os.path.isdir', return_value=True)
  @mock.patch('os.path.isfile', return_value=False)
  def test_find_parent_module_dir_with_multi_autogens(self, _isfile, _isdir):
    """Test _find_parent_module_dir method.

    This case returns folders with multiple autogenerated configs defined.
    """
    abs_class_dir = '/%s' % CLASS_DIR
    mock_module_info = mock.Mock(spec=module_info.ModuleInfo)
    mock_module_info.path_to_module_info = (
        PATH_TO_MODULE_INFO_WITH_MULTI_AUTOGEN
    )
    unittest_utils.assert_strict_equal(
        self,
        test_finder_utils.find_parent_module_dir(
            uc.ROOT, abs_class_dir, mock_module_info
        ),
        uc.MODULE_DIR,
    )

  @mock.patch('os.path.isdir', return_value=True)
  @mock.patch('os.path.isfile', return_value=False)
  def test_find_parent_module_dir_with_robo_and_autogens(self, _isfile, _isdir):
    """Test _find_parent_module_dir method.

    This case returns folders with multiple autogenerated configs defined
    with a Robo test above them, which is the expected result.
    """
    abs_class_dir = '/%s' % CLASS_DIR
    mock_module_info = mock.Mock(spec=module_info.ModuleInfo)
    mock_module_info.path_to_module_info = (
        PATH_TO_MODULE_INFO_WITH_MULTI_AUTOGEN_AND_ROBO
    )
    unittest_utils.assert_strict_equal(
        self,
        test_finder_utils.find_parent_module_dir(
            uc.ROOT, abs_class_dir, mock_module_info
        ),
        uc.MODULE_DIR,
    )

  @mock.patch('os.path.isdir', return_value=True)
  @mock.patch('os.path.isfile', return_value=False)
  def test_find_parent_module_dir_robo(self, _isfile, _isdir):
    """Test _find_parent_module_dir method.

    Make sure we behave as expected when we encounter a robo module path.
    """
    abs_class_dir = '/%s' % CLASS_DIR
    mock_module_info = mock.Mock(spec=module_info.ModuleInfo)
    mock_module_info.is_legacy_robolectric_class.return_value = True
    rel_class_dir_path = os.path.relpath(abs_class_dir, uc.ROOT)
    mock_module_info.path_to_module_info = {rel_class_dir_path: [{}]}
    unittest_utils.assert_strict_equal(
        self,
        test_finder_utils.find_parent_module_dir(
            uc.ROOT, abs_class_dir, mock_module_info
        ),
        rel_class_dir_path,
    )

  def test_get_targets_from_xml(self):
    """Test get_targets_from_xml method."""
    # Mocking Etree is near impossible, so use a real file, but mocking
    # ModuleInfo is still fine. Just have it return False when it finds a
    # module that states it's not a module.
    mock_module_info = mock.Mock(spec=module_info.ModuleInfo)
    mock_module_info.is_module.side_effect = lambda module: (
        not module == 'is_not_module'
    )
    xml_file = os.path.join(uc.TEST_DATA_DIR, constants.MODULE_CONFIG + '.data')
    unittest_utils.assert_strict_equal(
        self,
        test_finder_utils.get_targets_from_xml(xml_file, mock_module_info),
        XML_TARGETS,
    )

  def test_get_targets_from_dalvik_xml(self):
    """Test get_targets_from_xml method with dalvik class."""
    # Mocking Etree is near impossible, so use a real file, but mocking
    # ModuleInfo is still fine. Just have it return False when it finds a
    # module that states it's not a module.
    mock_module_info = mock.Mock(spec=module_info.ModuleInfo)
    mock_module_info.is_module.side_effect = lambda module: (
        not module == 'is_not_module'
    )
    xml_file = os.path.join(uc.TEST_DATA_DIR, DALVIK_TEST_CONFIG)
    unittest_utils.assert_strict_equal(
        self,
        test_finder_utils.get_targets_from_xml(xml_file, mock_module_info),
        DALVIK_XML_TARGETS,
    )

  def test_get_targets_from_libcore_xml(self):
    """Test get_targets_from_xml method with libcore class."""
    # Mocking Etree is near impossible, so use a real file, but mocking
    # ModuleInfo is still fine. Just have it return False when it finds a
    # module that states it's not a module.
    mock_module_info = mock.Mock(spec=module_info.ModuleInfo)
    mock_module_info.is_module.side_effect = lambda module: (
        not module == 'is_not_module'
    )
    xml_file = os.path.join(uc.TEST_DATA_DIR, LIBCORE_TEST_CONFIG)
    unittest_utils.assert_strict_equal(
        self,
        test_finder_utils.get_targets_from_xml(xml_file, mock_module_info),
        DALVIK_XML_TARGETS,
    )

  @mock.patch.object(
      test_finder_utils,
      '_VTS_PUSH_DIR',
      os.path.join(uc.TEST_DATA_DIR, VTS_PUSH_DIR),
  )
  def test_get_targets_from_vts_xml(self):
    """Test get_targets_from_vts_xml method."""
    # Mocking Etree is near impossible, so use a real file, but mock out
    # ModuleInfo,
    mock_module_info = mock.Mock(spec=module_info.ModuleInfo)
    mock_module_info.is_module.return_value = True
    xml_file = os.path.join(uc.TEST_DATA_DIR, VTS_XML)
    unittest_utils.assert_strict_equal(
        self,
        test_finder_utils.get_targets_from_vts_xml(
            xml_file, '', mock_module_info
        ),
        VTS_XML_TARGETS,
    )

  @mock.patch('builtins.input', return_value='0')
  def test_search_integration_dirs(self, mock_input):
    """Test search_integration_dirs."""
    mock_input.return_value = '0'
    paths = [os.path.join(uc.ROOT, INT_DIR1, INT_FILE_NAME + '.xml')]
    int_dirs = [INT_DIR1]
    test_result = test_finder_utils.search_integration_dirs(
        INT_FILE_NAME, int_dirs
    )
    unittest_utils.assert_strict_equal(self, test_result, paths)
    int_dirs = [INT_DIR1, INT_DIR2]
    test_result = test_finder_utils.search_integration_dirs(
        INT_FILE_NAME, int_dirs
    )
    unittest_utils.assert_strict_equal(self, test_result, paths)

  @mock.patch('os.path.isfile', return_value=False)
  @mock.patch('os.environ.get', return_value=uc.TEST_CONFIG_DATA_DIR)
  @mock.patch('builtins.input', return_value='0')
  # pylint: disable=too-many-statements
  def test_find_class_file(self, mock_input, _mock_env, _mock_isfile):
    """Test find_class_file."""
    # 1. Java class(find).
    java_tmp_test_result = []
    mock_input.return_value = '0'
    java_class = os.path.join(
        uc.FIND_PATH, uc.FIND_PATH_TESTCASE_JAVA + '.java'
    )
    java_tmp_test_result.extend(
        test_finder_utils.find_class_file(
            uc.FIND_PATH, uc.FIND_PATH_TESTCASE_JAVA
        )
    )
    mock_input.return_value = '1'
    kt_class = os.path.join(uc.FIND_PATH, uc.FIND_PATH_TESTCASE_JAVA + '.kt')
    java_tmp_test_result.extend(
        test_finder_utils.find_class_file(
            uc.FIND_PATH, uc.FIND_PATH_TESTCASE_JAVA
        )
    )
    self.assertTrue(java_class in java_tmp_test_result)
    self.assertTrue(kt_class in java_tmp_test_result)

    # 2. Java class(read index).
    del java_tmp_test_result[:]
    mock_input.return_value = '0'
    _mock_isfile = True
    java_class = os.path.join(
        uc.FIND_PATH, uc.FIND_PATH_TESTCASE_JAVA + '.java'
    )
    java_tmp_test_result.extend(
        test_finder_utils.find_class_file(
            uc.FIND_PATH, uc.FIND_PATH_TESTCASE_JAVA
        )
    )
    mock_input.return_value = '1'
    kt_class = os.path.join(uc.FIND_PATH, uc.FIND_PATH_TESTCASE_JAVA + '.kt')
    java_tmp_test_result.extend(
        test_finder_utils.find_class_file(
            uc.FIND_PATH, uc.FIND_PATH_TESTCASE_JAVA
        )
    )
    self.assertTrue(java_class in java_tmp_test_result)
    self.assertTrue(kt_class in java_tmp_test_result)

    # 3. Qualified Java class(find).
    del java_tmp_test_result[:]
    mock_input.return_value = '0'
    _mock_isfile = False
    java_qualified_class = '{0}.{1}'.format(
        uc.FIND_PATH_FOLDER, uc.FIND_PATH_TESTCASE_JAVA
    )
    java_tmp_test_result.extend(
        test_finder_utils.find_class_file(uc.FIND_PATH, java_qualified_class)
    )
    mock_input.return_value = '1'
    java_tmp_test_result.extend(
        test_finder_utils.find_class_file(uc.FIND_PATH, java_qualified_class)
    )
    self.assertTrue(java_class in java_tmp_test_result)
    self.assertTrue(kt_class in java_tmp_test_result)

    # 4. Qualified Java class(read index).
    del java_tmp_test_result[:]
    mock_input.return_value = '0'
    _mock_isfile = True
    java_qualified_class = '{0}.{1}'.format(
        uc.FIND_PATH_FOLDER, uc.FIND_PATH_TESTCASE_JAVA
    )
    java_tmp_test_result.extend(
        test_finder_utils.find_class_file(uc.FIND_PATH, java_qualified_class)
    )
    mock_input.return_value = '1'
    java_tmp_test_result.extend(
        test_finder_utils.find_class_file(uc.FIND_PATH, java_qualified_class)
    )
    self.assertTrue(java_class in java_tmp_test_result)
    self.assertTrue(kt_class in java_tmp_test_result)

    # 5. CC class(find).
    cc_tmp_test_result = []
    _mock_isfile = False
    mock_input.return_value = '0'
    cpp_class = os.path.join(uc.FIND_PATH, uc.FIND_PATH_FILENAME_CC + '.cpp')
    cc_tmp_test_result.extend(
        test_finder_utils.find_class_file(
            uc.FIND_PATH, uc.FIND_PATH_TESTCASE_CC, True
        )
    )
    mock_input.return_value = '1'
    cc_class = os.path.join(uc.FIND_PATH, uc.FIND_PATH_FILENAME_CC + '.cc')
    cc_tmp_test_result.extend(
        test_finder_utils.find_class_file(
            uc.FIND_PATH, uc.FIND_PATH_TESTCASE_CC, True
        )
    )
    self.assertTrue(cpp_class in cc_tmp_test_result)
    self.assertTrue(cc_class in cc_tmp_test_result)

    # 6. CC class(read index).
    del cc_tmp_test_result[:]
    mock_input.return_value = '0'
    _mock_isfile = True
    cpp_class = os.path.join(uc.FIND_PATH, uc.FIND_PATH_FILENAME_CC + '.cpp')
    cc_tmp_test_result.extend(
        test_finder_utils.find_class_file(
            uc.FIND_PATH, uc.FIND_PATH_TESTCASE_CC, True
        )
    )
    mock_input.return_value = '1'
    cc_class = os.path.join(uc.FIND_PATH, uc.FIND_PATH_FILENAME_CC + '.cc')
    cc_tmp_test_result.extend(
        test_finder_utils.find_class_file(
            uc.FIND_PATH, uc.FIND_PATH_TESTCASE_CC, True
        )
    )
    self.assertTrue(cpp_class in cc_tmp_test_result)
    self.assertTrue(cc_class in cc_tmp_test_result)

  @mock.patch('builtins.input', return_value='0')
  @mock.patch.object(test_finder_utils, 'get_dir_path_and_filename')
  @mock.patch('os.path.exists', return_value=True)
  def test_get_int_dir_from_path(self, _exists, _find, mock_input):
    """Test get_int_dir_from_path."""
    mock_input.return_value = '0'
    int_dirs = [INT_DIR1]
    path = os.path.join(uc.ROOT, INT_DIR1, INT_FILE_NAME + '.xml')
    _find.return_value = (INT_DIR1, INT_FILE_NAME + '.xml')
    test_result = test_finder_utils.get_int_dir_from_path(path, int_dirs)
    unittest_utils.assert_strict_equal(self, test_result, INT_DIR1)
    _find.return_value = (INT_DIR1, None)
    test_result = test_finder_utils.get_int_dir_from_path(path, int_dirs)
    unittest_utils.assert_strict_equal(self, test_result, None)
    int_dirs = [INT_DIR1, INT_DIR2]
    _find.return_value = (INT_DIR1, INT_FILE_NAME + '.xml')
    test_result = test_finder_utils.get_int_dir_from_path(path, int_dirs)
    unittest_utils.assert_strict_equal(self, test_result, INT_DIR1)

  def test_get_install_locations(self):
    """Test get_install_locations."""
    host_installed_paths = ['out/host/a/b']
    host_expect = set(['host'])
    self.assertEqual(
        test_finder_utils.get_install_locations(host_installed_paths),
        host_expect,
    )
    device_installed_paths = ['out/target/c/d']
    device_expect = set(['device'])
    self.assertEqual(
        test_finder_utils.get_install_locations(device_installed_paths),
        device_expect,
    )
    both_installed_paths = ['out/host/e', 'out/target/f']
    both_expect = set(['host', 'device'])
    self.assertEqual(
        test_finder_utils.get_install_locations(both_installed_paths),
        both_expect,
    )
    no_installed_paths = []
    no_expect = set()
    self.assertEqual(
        test_finder_utils.get_install_locations(no_installed_paths), no_expect
    )

  # Disable the fail test due to the breakage if test xml rename to xml.data.
  # pylint: disable=pointless-string-statement
  '''
    def test_get_plans_from_vts_xml(self):
        """Test get_plans_from_vts_xml method."""
        xml_path = os.path.join(uc.TEST_DATA_DIR, VTS_PLAN_DIR,
                                'vts-staging-default.xml.data')
        self.assertEqual(
            test_finder_utils.get_plans_from_vts_xml(xml_path),
            VTS_PLAN_TARGETS)
        xml_path = os.path.join(uc.TEST_DATA_DIR, VTS_PLAN_DIR, 'NotExist.xml')
        self.assertRaises(atest_error.XmlNotExistError,
                          test_finder_utils.get_plans_from_vts_xml, xml_path)
    '''

  def test_get_levenshtein_distance(self):
    """Test get_levenshetine distance module correctly returns distance."""
    self.assertEqual(
        test_finder_utils.get_levenshtein_distance(uc.MOD1, uc.FUZZY_MOD1), 1
    )

    self.assertEqual(
        test_finder_utils.get_levenshtein_distance(
            uc.MOD2, uc.FUZZY_MOD2, dir_costs=(1, 2, 3)
        ),
        3,
    )

    self.assertEqual(
        test_finder_utils.get_levenshtein_distance(
            uc.MOD3, uc.FUZZY_MOD3, dir_costs=(1, 2, 1)
        ),
        8,
    )

  # pylint: disable=consider-iterating-dictionary
  def test_get_cc_class_info(self):
    """Test get_cc_class_info method."""
    file_path = os.path.join(uc.TEST_DATA_DIR, 'my_cc_test.cc')
    class_info = test_finder_utils.get_cc_class_info(file_path)

    # 1. Ensure all classes are in the class info dict.
    expect_classes = {
        'Class1',
        'FClass',
        'FlagClass1',
        'FFlagClass',
        'ValueParamClass1',
        'ValueParamClass2',
        'TypedTestClass',
        'TypedParamTestClass',
    }
    self.assertEqual({key for key in class_info.keys()}, expect_classes)

    # 2. Ensure methods are correctly mapping to the right class.
    self.assertEqual(class_info['ValueParamClass1']['methods'], {'VPMethod1'})
    self.assertEqual(class_info['ValueParamClass2']['methods'], {'VPMethod2'})
    self.assertEqual(class_info['TypedTestClass']['methods'], {'TypedTestName'})
    self.assertEqual(
        class_info['TypedParamTestClass']['methods'], {'TypedParamTestName'}
    )
    self.assertEqual(class_info['Class1']['methods'], {'Method1', 'Method2'})
    self.assertEqual(class_info['FClass']['methods'], {'FMethod1', 'FMethod2'})
    self.assertEqual(
        class_info['FlagClass1']['methods'], {'Method1', 'Method2'}
    )
    self.assertEqual(
        class_info['FFlagClass']['methods'], {'FMethod1', 'FMethod2'}
    )

    # 3. Ensure prefixes are correctly mapping to the right class.
    self.assertEqual(
        class_info['TypedParamTestClass']['prefixes'],
        {'Instantiation3', 'Instantiation4'},
    )
    self.assertEqual(
        class_info['ValueParamClass1']['prefixes'], {'Instantiation1'}
    )
    self.assertEqual(
        class_info['ValueParamClass2']['prefixes'], {'Instantiation2'}
    )

    # 4. Ensure we can tell typed test.
    self.assertTrue(class_info['TypedParamTestClass']['typed'])
    self.assertTrue(class_info['TypedTestClass']['typed'])
    self.assertFalse(class_info['ValueParamClass1']['typed'])
    self.assertFalse(class_info['FClass']['typed'])
    self.assertFalse(class_info['Class1']['typed'])
    self.assertFalse(class_info['FFlagClass']['typed'])
    self.assertFalse(class_info['FlagClass1']['typed'])

  def test_get_java_method(self):
    """Test get_java_method"""
    expect_methods = {'testMethod1', 'testMethod2'}
    target_java = os.path.join(
        uc.TEST_DATA_DIR, 'class_file_path_testing', 'hello_world_test.java'
    )
    self.assertEqual(
        expect_methods, test_finder_utils.get_java_methods(target_java)
    )
    target_kt = os.path.join(
        uc.TEST_DATA_DIR, 'class_file_path_testing', 'hello_world_test.kt'
    )
    self.assertEqual(
        expect_methods, test_finder_utils.get_java_methods(target_kt)
    )

  def test_get_parent_cls_name(self):
    """Test get_parent_cls_name"""
    parent_cls = 'AtestClass'
    target_java = os.path.join(
        uc.TEST_DATA_DIR, 'path_testing', 'PathTesting.java'
    )
    self.assertEqual(
        parent_cls, test_finder_utils.get_parent_cls_name(target_java)
    )
    parent_cls = 'AtestClassKt'
    target_java = os.path.join(
        uc.TEST_DATA_DIR, 'path_testing', 'PathTesting.kt'
    )
    self.assertEqual(
        parent_cls, test_finder_utils.get_parent_cls_name(target_java)
    )

  @staticmethod
  def _get_paths_side_effect(module_name):
    """Mock return values for module_info.get_paths."""
    if module_name == UNIT_TEST_MODULE_1:
      return [IT_TEST_MATCHED_1_PATH]
    if module_name == UNIT_TEST_MODULE_2:
      return [UNIT_TEST_MATCHED_2_PATH]
    if module_name == UNIT_TEST_MODULE_3:
      return [UNIT_TEST_NOT_MATCHED_1_PATH]
    return []

  @mock.patch.object(
      module_info.ModuleInfo,
      'get_all_host_unit_tests',
      return_value=[UNIT_TEST_MODULE_1, UNIT_TEST_MODULE_2, UNIT_TEST_MODULE_3],
  )
  @mock.patch.object(
      module_info.ModuleInfo,
      'get_paths',
  )
  def test_find_host_unit_tests(self, _get_paths, _mock_get_unit_tests):
    """Test find_host_unit_tests"""
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    _get_paths.side_effect = self._get_paths_side_effect
    expect_unit_tests = [UNIT_TEST_MODULE_1, UNIT_TEST_MODULE_2]
    self.assertEqual(
        sorted(expect_unit_tests),
        sorted(
            test_finder_utils.find_host_unit_tests(
                mod_info, UNIT_TEST_SEARCH_ROOT
            )
        ),
    )

  def test_get_annotated_methods(self):
    """Test get_annotated_methods"""
    sample_path = os.path.join(uc.TEST_DATA_DIR, 'annotation', 'sample.txt')
    real_methods = list(
        test_finder_utils.get_annotated_methods('TestAnnotation1', sample_path)
    )
    real_methods.sort()
    expect_methods = ['annotation1_method1', 'annotation1_method2']
    expect_methods.sort()
    self.assertEqual(expect_methods, real_methods)

  @mock.patch('os.path.isfile', side_effect=unittest_utils.isfile_side_effect)
  def test_get_test_config_use_androidtestxml(self, _isfile):
    """Test get_test_config_and_srcs using default AndroidTest.xml"""
    android_root = '/'
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    t_info = test_info.TestInfo(
        'androidtest_config_module', 'mock_runner', build_targets=set()
    )
    expect_config = os.path.join(
        android_root, uc.ANDTEST_CONFIG_PATH, constants.MODULE_CONFIG
    )
    result, _ = test_finder_utils.get_test_config_and_srcs(t_info, mod_info)
    self.assertEqual(expect_config, result)

  @mock.patch('os.path.isfile', side_effect=unittest_utils.isfile_side_effect)
  def test_get_test_config_single_config(self, _isfile):
    """Test get_test_config_and_srcs manually set it's config"""
    android_root = '/'
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    t_info = test_info.TestInfo(
        'single_config_module', 'mock_runner', build_targets=set()
    )
    expect_config = os.path.join(
        android_root, uc.SINGLE_CONFIG_PATH, uc.SINGLE_CONFIG_NAME
    )
    result, _ = test_finder_utils.get_test_config_and_srcs(t_info, mod_info)
    self.assertEqual(expect_config, result)

  @mock.patch('os.path.isfile', side_effect=unittest_utils.isfile_side_effect)
  def test_get_test_config_main_multiple_config(self, _isfile):
    """Test get_test_config_and_srcs which is the main module of multiple config"""
    android_root = '/'
    mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
    t_info = test_info.TestInfo(
        'multiple_config_module', 'mock_runner', build_targets=set()
    )
    expect_config = os.path.join(
        android_root, uc.MULTIPLE_CONFIG_PATH, uc.MAIN_CONFIG_NAME
    )
    result, _ = test_finder_utils.get_test_config_and_srcs(t_info, mod_info)
    self.assertEqual(expect_config, result)

  @mock.patch('os.path.isfile', side_effect=unittest_utils.isfile_side_effect)
  def test_get_test_config_subtest_in_multiple_config(self, _isfile):
    """Test get_test_config_and_srcs not the main module of multiple config"""
    android_root = '/'
    mock_dict = {
        'ANDROID_BUILD_TOP': android_root,
        'ANDROID_PRODUCT_OUT': PRODUCT_OUT_DIR,
        'ANDROID_HOST_OUT': HOST_OUT_DIR,
    }
    with mock.patch.dict('os.environ', mock_dict, clear=True):
      mod_info = module_info.load_from_file(module_file=JSON_FILE_PATH)
      t_info = test_info.TestInfo(
          'Multiple2', 'mock_runner', build_targets=set()
      )
      expect_config = Path(
          android_root, uc.MULTIPLE_CONFIG_PATH, uc.SUB_CONFIG_NAME_2
      )

      result, _ = test_finder_utils.get_test_config_and_srcs(t_info, mod_info)

      self.assertEqual(expect_config, result)

  def test_is_test_from_kernel_xml_input_xml_not_exist_return_false(self):
    not_exist_xml = 'not/exist/xml/path'
    test_name = 'test_name'

    exist = test_finder_utils.is_test_from_kernel_xml(not_exist_xml, test_name)

    self.assertEqual(exist, False)

  def test_parse_test_reference_input_module_class_method_match(self):
    test_module = 'myModule'
    test_class = 'myClass'
    test_method = 'myTest::Method'
    test_ref = f'{test_module}:{test_class}#{test_method}'

    result = test_finder_utils.parse_test_reference(test_ref)

    self.assertEqual(test_module, result['module_name'])
    self.assertEqual(test_class, result['pkg_class_name'])
    self.assertEqual(test_method, result['method_name'])

  def test_parse_test_reference_input_module_class_match(self):
    test_module = 'myModule'
    test_class = 'myClass'
    test_ref = f'{test_module}:{test_class}'

    result = test_finder_utils.parse_test_reference(test_ref)

    self.assertEqual(test_module, result['module_name'])
    self.assertEqual(test_class, result['pkg_class_name'])
    self.assertEqual('', result.get('method_name', ''))

  def test_parse_test_reference_input_module_class_parameter_method_match(self):
    test_module = 'myModule'
    test_class = 'myClass'
    test_method = 'myTest::Method[0]'
    test_ref = f'{test_module}:{test_class}#{test_method}'

    result = test_finder_utils.parse_test_reference(test_ref)

    self.assertEqual(test_module, result['module_name'])
    self.assertEqual(test_class, result['pkg_class_name'])
    self.assertEqual(test_method, result['method_name'])

  def test_parse_test_reference_input_module_class_multiple_methods_match(self):
    test_module = 'myModule'
    test_class = 'myClass'
    test_method = 'myTest::Method[0],myTest::Method[1]'
    test_ref = f'{test_module}:{test_class}#{test_method}'

    result = test_finder_utils.parse_test_reference(test_ref)

    self.assertEqual(test_module, result['module_name'])
    self.assertEqual(test_class, result['pkg_class_name'])
    self.assertEqual(test_method, result['method_name'])

  def test_parse_test_reference_input_class_method_not_match(self):
    test_class = 'myClass'
    test_method = 'myTest::Method'
    test_ref = f'{test_class}#{test_method}'

    result = test_finder_utils.parse_test_reference(test_ref)

    self.assertEqual(result, dict())

  def test_parse_test_reference_input_module_dashed_match(self):
    test_module = 'my-module'
    test_class = 'BR/EI/ZH'
    test_ref = f'{test_module}:{test_class}'

    result = test_finder_utils.parse_test_reference(test_ref)

    self.assertEqual(test_module, result['module_name'])
    self.assertEqual(test_class, result['pkg_class_name'])

  def test_parse_test_reference_input_module_pkg_method_match(self):
    test_module = 'myModule'
    test_package = 'my.package'
    test_method = 'myTest::Method'
    test_ref = f'{test_module}:{test_package}#{test_method}'

    result = test_finder_utils.parse_test_reference(test_ref)

    self.assertEqual(test_module, result['module_name'])
    self.assertEqual(test_package, result['pkg_class_name'])
    self.assertEqual(test_method, result['method_name'])

  def test_parse_test_reference_input_plan_class_match(self):
    test_module = 'my/Module'
    test_class = 'class'
    test_ref = f'{test_module}:{test_class}'

    result = test_finder_utils.parse_test_reference(test_ref)

    self.assertEqual(test_module, result['module_name'])
    self.assertEqual(test_class, result['pkg_class_name'])
    self.assertEqual('', result.get('method_name', ''))

  def test_parse_test_reference_input_module_parameter_class_and_method_match(
      self,
  ):
    test_module = 'myModule'
    test_class = 'myClass/abc0'
    test_method = 'myTest0/Method[0]'
    test_ref = f'{test_module}:{test_class}#{test_method}'

    result = test_finder_utils.parse_test_reference(test_ref)

    self.assertEqual(test_module, result['module_name'])
    self.assertEqual(test_class, result['pkg_class_name'])
    self.assertEqual(test_method, result['method_name'])


if __name__ == '__main__':
  unittest.main()
