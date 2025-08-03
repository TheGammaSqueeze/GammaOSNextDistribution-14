#!/usr/bin/env python3
#
# Copyright 2017, The Android Open Source Project
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

"""Unittests for cli_translator."""

from importlib import reload
from io import StringIO
import json
import os
import re
import sys
import tempfile
import unittest
from unittest import mock
from atest import arg_parser
from atest import atest_utils
from atest import cli_translator as cli_t
from atest import constants
from atest import module_info
from atest import test_finder_handler
from atest import test_mapping
from atest import unittest_constants as uc
from atest import unittest_utils
from atest.metrics import metrics
from atest.test_finders import module_finder
from atest.test_finders import test_finder_base
from atest.test_finders import test_finder_utils
from pyfakefs import fake_filesystem_unittest


# TEST_MAPPING related consts
TEST_MAPPING_TOP_DIR = os.path.join(uc.TEST_DATA_DIR, 'test_mapping')
TEST_MAPPING_DIR = os.path.join(TEST_MAPPING_TOP_DIR, 'folder1')
TEST_1 = test_mapping.TestDetail({'name': 'test1', 'host': True})
TEST_2 = test_mapping.TestDetail({'name': 'test2'})
TEST_3 = test_mapping.TestDetail({'name': 'test3'})
TEST_4 = test_mapping.TestDetail({'name': 'test4'})
TEST_5 = test_mapping.TestDetail({'name': 'test5'})
TEST_6 = test_mapping.TestDetail({'name': 'test6'})
TEST_7 = test_mapping.TestDetail({'name': 'test7'})
TEST_8 = test_mapping.TestDetail({'name': 'test8'})
TEST_9 = test_mapping.TestDetail({'name': 'test9'})
TEST_10 = test_mapping.TestDetail({'name': 'test10'})

SEARCH_DIR_RE = re.compile(r'^find ([^ ]*).*$')
BUILD_TOP_DIR = tempfile.TemporaryDirectory().name
PRODUCT_OUT_DIR = os.path.join(BUILD_TOP_DIR, 'out/target/product/vsoc_x86_64')
HOST_OUT_DIR = os.path.join(BUILD_TOP_DIR, 'out/host/linux-x86')


# pylint: disable=unused-argument
def gettestinfos_side_effect(
    test_names, test_mapping_test_details=None, is_rebuild_module_info=False
):
  """Mock return values for _get_test_info."""
  test_infos = []
  for test_name in test_names:
    if test_name == uc.MODULE_NAME:
      test_infos.append(uc.MODULE_INFO)
    if test_name == uc.CLASS_NAME:
      test_infos.append(uc.CLASS_INFO)
    if test_name == uc.HOST_UNIT_TEST_NAME_1:
      test_infos.append(uc.MODULE_INFO_HOST_1)
    if test_name == uc.HOST_UNIT_TEST_NAME_2:
      test_infos.append(uc.MODULE_INFO_HOST_2)
  return test_infos


# pylint: disable=protected-access
class CLITranslatorUnittests(unittest.TestCase):
  """Unit tests for cli_t.py"""

  def setUp(self):
    """Run before execution of every test"""
    self.ctr = cli_t.CLITranslator()

    # Create a mock of args.
    self.args = arg_parser.create_atest_arg_parser().parse_args()
    self.args.tests = []
    # Test mapping related args
    self.args.test_mapping = False
    self.args.include_subdirs = False
    self.args.enable_file_patterns = False
    self.args.rebuild_module_info = False
    # Cache finder related args
    self.args.clear_cache = False
    self.ctr.mod_info = mock.Mock
    self.ctr.mod_info.name_to_module_info = {}

  def tearDown(self):
    """Run after execution of every test"""
    reload(uc)

  @mock.patch.object(atest_utils, 'update_test_info_cache')
  @mock.patch('builtins.input', return_value='n')
  @mock.patch.object(module_finder.ModuleFinder, 'find_test_by_module_name')
  @mock.patch.object(module_finder.ModuleFinder, 'get_fuzzy_searching_results')
  @mock.patch.object(metrics, 'FindTestFinishEvent')
  @mock.patch.object(test_finder_handler, 'get_find_methods_for_test')
  # pylint: disable=too-many-locals
  def test_get_test_infos(
      self,
      mock_getfindmethods,
      _metrics,
      mock_getfuzzyresults,
      mock_findtestbymodule,
      mock_input,
      _mock_update_test_info,
  ):
    """Test _get_test_infos method."""
    ctr = cli_t.CLITranslator()
    find_method_return_module_info = lambda x, y: uc.MODULE_INFOS
    # pylint: disable=invalid-name
    find_method_return_module_class_info = (
        lambda x, test: uc.MODULE_INFOS
        if test == uc.MODULE_NAME
        else uc.CLASS_INFOS
    )
    find_method_return_nothing = lambda x, y: None
    one_test = [uc.MODULE_NAME]
    mult_test = [uc.MODULE_NAME, uc.CLASS_NAME]

    # Let's make sure we return what we expect.
    expected_test_infos = [uc.MODULE_INFO]
    mock_getfindmethods.return_value = [
        test_finder_base.Finder(None, find_method_return_module_info, None)
    ]
    unittest_utils.assert_equal_testinfo_lists(
        self, ctr._get_test_infos(one_test), expected_test_infos
    )

    # Check we receive multiple test infos.
    expected_test_infos = [uc.MODULE_INFO, uc.CLASS_INFO]
    mock_getfindmethods.return_value = [
        test_finder_base.Finder(
            None, find_method_return_module_class_info, None
        )
    ]
    unittest_utils.assert_equal_testinfo_lists(
        self, ctr._get_test_infos(mult_test), expected_test_infos
    )

    # Check return null set when we have no tests found or multiple results.
    mock_getfindmethods.return_value = [
        test_finder_base.Finder(None, find_method_return_nothing, None)
    ]
    null_test_info = []
    mock_getfuzzyresults.return_value = []
    self.assertEqual(null_test_info, ctr._get_test_infos(one_test))
    self.assertEqual(null_test_info, ctr._get_test_infos(mult_test))

    # Check returning test_info when the user says Yes.
    mock_input.return_value = 'Y'
    mock_getfindmethods.return_value = [
        test_finder_base.Finder(None, find_method_return_module_info, None)
    ]
    mock_getfuzzyresults.return_value = one_test
    mock_findtestbymodule.return_value = uc.MODULE_INFO
    unittest_utils.assert_equal_testinfo_lists(
        self, ctr._get_test_infos([uc.TYPO_MODULE_NAME]), [uc.MODULE_INFO]
    )

    # Check the method works for test mapping.
    test_detail1 = test_mapping.TestDetail(uc.TEST_MAPPING_TEST)
    test_detail2 = test_mapping.TestDetail(uc.TEST_MAPPING_TEST_WITH_OPTION)
    expected_test_infos = [uc.MODULE_INFO, uc.CLASS_INFO]
    mock_getfindmethods.return_value = [
        test_finder_base.Finder(
            None, find_method_return_module_class_info, None
        )
    ]
    test_infos = ctr._get_test_infos(mult_test, [test_detail1, test_detail2])
    unittest_utils.assert_equal_testinfo_lists(
        self, test_infos, expected_test_infos
    )
    for test_info in test_infos:
      if test_info == uc.MODULE_INFO:
        self.assertEqual(
            test_detail1.options, test_info.data[constants.TI_MODULE_ARG]
        )
      else:
        self.assertEqual(
            test_detail2.options, test_info.data[constants.TI_MODULE_ARG]
        )

  @mock.patch.object(atest_utils, 'update_test_info_cache')
  @mock.patch.object(metrics, 'FindTestFinishEvent')
  @mock.patch.object(test_finder_handler, 'get_find_methods_for_test')
  def test_get_test_infos_2(
      self, mock_getfindmethods, _metrics, _mock_update_test_info
  ):
    """Test _get_test_infos method."""
    ctr = cli_t.CLITranslator()
    find_method_return_module_info2 = lambda x, y: uc.MODULE_INFOS2
    find_method_ret_mod_cls_info2 = (
        lambda x, test: uc.MODULE_INFOS2
        if test == uc.MODULE_NAME
        else uc.CLASS_INFOS2
    )
    one_test = [uc.MODULE_NAME]
    mult_test = [uc.MODULE_NAME, uc.CLASS_NAME]
    # Let's make sure we return what we expect.
    expected_test_infos = [uc.MODULE_INFO, uc.MODULE_INFO2]
    mock_getfindmethods.return_value = [
        test_finder_base.Finder(None, find_method_return_module_info2, None)
    ]
    unittest_utils.assert_equal_testinfo_lists(
        self, ctr._get_test_infos(one_test), expected_test_infos
    )
    # Check we receive multiple test infos.
    expected_test_infos = [
        uc.MODULE_INFO,
        uc.MODULE_INFO2,
        uc.CLASS_INFO,
        uc.CLASS_INFO2,
    ]
    mock_getfindmethods.return_value = [
        test_finder_base.Finder(None, find_method_ret_mod_cls_info2, None)
    ]
    unittest_utils.assert_equal_testinfo_lists(
        self, ctr._get_test_infos(mult_test), expected_test_infos
    )
    # Check the method works for test mapping.
    test_detail1 = test_mapping.TestDetail(uc.TEST_MAPPING_TEST)
    test_detail2 = test_mapping.TestDetail(uc.TEST_MAPPING_TEST_WITH_OPTION)
    expected_test_infos = [
        uc.MODULE_INFO,
        uc.MODULE_INFO2,
        uc.CLASS_INFO,
        uc.CLASS_INFO2,
    ]
    mock_getfindmethods.return_value = [
        test_finder_base.Finder(None, find_method_ret_mod_cls_info2, None)
    ]
    test_infos = ctr._get_test_infos(mult_test, [test_detail1, test_detail2])
    unittest_utils.assert_equal_testinfo_lists(
        self, test_infos, expected_test_infos
    )
    for test_info in test_infos:
      if test_info in [uc.MODULE_INFO, uc.MODULE_INFO2]:
        self.assertEqual(
            test_detail1.options, test_info.data[constants.TI_MODULE_ARG]
        )
      elif test_info in [uc.CLASS_INFO, uc.CLASS_INFO2]:
        self.assertEqual(
            test_detail2.options, test_info.data[constants.TI_MODULE_ARG]
        )

  @mock.patch.object(module_finder.ModuleFinder, 'get_fuzzy_searching_results')
  @mock.patch.object(metrics, 'FindTestFinishEvent')
  @mock.patch.object(test_finder_handler, 'get_find_methods_for_test')
  def test_get_test_infos_with_mod_info(
      self,
      mock_getfindmethods,
      _metrics,
      mock_getfuzzyresults,
  ):
    """Test _get_test_infos method."""
    mod_info = module_info.load_from_file(
        module_file=os.path.join(uc.TEST_DATA_DIR, uc.JSON_FILE)
    )
    ctr = cli_t.CLITranslator(mod_info=mod_info)
    null_test_info = []
    mock_getfindmethods.return_value = []
    mock_getfuzzyresults.return_value = []
    unittest_utils.assert_equal_testinfo_lists(
        self, ctr._get_test_infos('not_exist_module'), null_test_info
    )

  @mock.patch.object(
      test_finder_utils, 'find_host_unit_tests', return_value=set()
  )
  @mock.patch.object(
      cli_t.CLITranslator,
      '_get_test_infos',
      side_effect=gettestinfos_side_effect,
  )
  def test_translate_class(self, _info, _find):
    """Test translate method for tests by class name."""
    # Check that we can find a class.
    self.args.tests = [uc.CLASS_NAME]
    self.args.host_unit_test_only = False
    test_infos = self.ctr.translate(self.args)
    unittest_utils.assert_strict_equal(
        self, _gather_build_targets(test_infos), uc.CLASS_BUILD_TARGETS
    )
    unittest_utils.assert_equal_testinfo_lists(
        self, test_infos, [uc.CLASS_INFO]
    )

  @mock.patch.object(
      test_finder_utils, 'find_host_unit_tests', return_value=set()
  )
  @mock.patch.object(
      cli_t.CLITranslator,
      '_get_test_infos',
      side_effect=gettestinfos_side_effect,
  )
  def test_translate_module(self, _info, _find):
    """Test translate method for tests by module or class name."""
    # Check that we get all the build targets we expect.
    self.args.tests = [uc.MODULE_NAME, uc.CLASS_NAME]
    self.args.host_unit_test_only = False
    test_infos = self.ctr.translate(self.args)
    unittest_utils.assert_strict_equal(
        self,
        _gather_build_targets(test_infos),
        uc.MODULE_CLASS_COMBINED_BUILD_TARGETS,
    )
    unittest_utils.assert_equal_testinfo_lists(
        self, test_infos, [uc.MODULE_INFO, uc.CLASS_INFO]
    )

  @mock.patch.object(os, 'getcwd', return_value='/src/build_top/somewhere')
  @mock.patch.object(test_finder_utils, 'find_host_unit_tests', return_value=[])
  @mock.patch.object(cli_t.CLITranslator, '_find_tests_by_test_mapping')
  @mock.patch.object(
      cli_t.CLITranslator,
      '_get_test_infos',
      side_effect=gettestinfos_side_effect,
  )
  def test_translate_test_mapping(
      self, _info, mock_testmapping, _find_unit_tests, _getcwd
  ):
    """Test translate method for tests in test mapping."""
    # Check that test mappings feeds into get_test_info properly.
    test_detail1 = test_mapping.TestDetail(uc.TEST_MAPPING_TEST)
    test_detail2 = test_mapping.TestDetail(uc.TEST_MAPPING_TEST_WITH_OPTION)
    mock_testmapping.return_value = ([test_detail1, test_detail2], None)
    self.args.tests = []
    self.args.host = False
    self.args.host_unit_test_only = False
    test_infos = self.ctr.translate(self.args)
    unittest_utils.assert_strict_equal(
        self,
        _gather_build_targets(test_infos),
        uc.MODULE_CLASS_COMBINED_BUILD_TARGETS,
    )
    unittest_utils.assert_equal_testinfo_lists(
        self, test_infos, [uc.MODULE_INFO, uc.CLASS_INFO]
    )

  @mock.patch.object(cli_t.CLITranslator, '_find_tests_by_test_mapping')
  @mock.patch.object(
      cli_t.CLITranslator,
      '_get_test_infos',
      side_effect=gettestinfos_side_effect,
  )
  def test_translate_test_mapping_all(self, _info, mock_testmapping):
    """Test translate method for tests in test mapping."""
    # Check that test mappings feeds into get_test_info properly.
    test_detail1 = test_mapping.TestDetail(uc.TEST_MAPPING_TEST)
    test_detail2 = test_mapping.TestDetail(uc.TEST_MAPPING_TEST_WITH_OPTION)
    mock_testmapping.return_value = ([test_detail1, test_detail2], None)
    self.args.tests = ['src_path:all']
    self.args.test_mapping = True
    self.args.host = False
    test_infos = self.ctr.translate(self.args)
    unittest_utils.assert_strict_equal(
        self,
        _gather_build_targets(test_infos),
        uc.MODULE_CLASS_COMBINED_BUILD_TARGETS,
    )
    unittest_utils.assert_equal_testinfo_lists(
        self, test_infos, [uc.MODULE_INFO, uc.CLASS_INFO]
    )

  def test_find_tests_by_test_mapping_presubmit(self):
    """Test _find_tests_by_test_mapping method to locate presubmit tests."""
    # TODO: (b/264015241) Stop mocking build variables.
    os_environ_mock = {constants.ANDROID_BUILD_TOP: uc.TEST_DATA_DIR}
    with mock.patch.dict('os.environ', os_environ_mock, clear=True):
      tests, all_tests = self.ctr._find_tests_by_test_mapping(
          path=TEST_MAPPING_DIR,
          file_name='test_mapping_sample',
          checked_files=set(),
      )
    expected = set([TEST_1, TEST_2, TEST_5, TEST_7, TEST_9])
    expected_all_tests = {
        'presubmit': expected,
        'postsubmit': set([TEST_3, TEST_6, TEST_8, TEST_10]),
        'other_group': set([TEST_4]),
    }
    self.assertEqual(expected, tests)
    self.assertEqual(expected_all_tests, all_tests)

  def test_find_tests_by_test_mapping_postsubmit(self):
    """Test _find_tests_by_test_mapping method to locate postsubmit tests."""
    # TODO: (b/264015241) Stop mocking build variables.
    os_environ_mock = {constants.ANDROID_BUILD_TOP: uc.TEST_DATA_DIR}
    with mock.patch.dict('os.environ', os_environ_mock, clear=True):
      tests, all_tests = self.ctr._find_tests_by_test_mapping(
          path=TEST_MAPPING_DIR,
          test_groups=[constants.TEST_GROUP_POSTSUBMIT],
          file_name='test_mapping_sample',
          checked_files=set(),
      )
    expected_presubmit = set([TEST_1, TEST_2, TEST_5, TEST_7, TEST_9])
    expected = set([TEST_3, TEST_6, TEST_8, TEST_10])
    expected_all_tests = {
        'presubmit': expected_presubmit,
        'postsubmit': set([TEST_3, TEST_6, TEST_8, TEST_10]),
        'other_group': set([TEST_4]),
    }
    self.assertEqual(expected, tests)
    self.assertEqual(expected_all_tests, all_tests)

  def test_find_tests_by_test_mapping_all_group(self):
    """Test _find_tests_by_test_mapping method to locate postsubmit tests."""
    # TODO: (b/264015241) Stop mocking build variables.
    os_environ_mock = {constants.ANDROID_BUILD_TOP: uc.TEST_DATA_DIR}
    with mock.patch.dict('os.environ', os_environ_mock, clear=True):
      tests, all_tests = self.ctr._find_tests_by_test_mapping(
          path=TEST_MAPPING_DIR,
          test_groups=[constants.TEST_GROUP_ALL],
          file_name='test_mapping_sample',
          checked_files=set(),
      )
    expected_presubmit = set([TEST_1, TEST_2, TEST_5, TEST_7, TEST_9])
    expected = set([
        TEST_1,
        TEST_2,
        TEST_3,
        TEST_4,
        TEST_5,
        TEST_6,
        TEST_7,
        TEST_8,
        TEST_9,
        TEST_10,
    ])
    expected_all_tests = {
        'presubmit': expected_presubmit,
        'postsubmit': set([TEST_3, TEST_6, TEST_8, TEST_10]),
        'other_group': set([TEST_4]),
    }
    self.assertEqual(expected, tests)
    self.assertEqual(expected_all_tests, all_tests)

  def test_find_tests_by_test_mapping_include_subdir(self):
    """Test _find_tests_by_test_mapping method to include sub directory."""
    # TODO: (b/264015241) Stop mocking build variables.
    os_environ_mock = {constants.ANDROID_BUILD_TOP: uc.TEST_DATA_DIR}
    with mock.patch.dict('os.environ', os_environ_mock, clear=True):
      tests, all_tests = self.ctr._find_tests_by_test_mapping(
          path=TEST_MAPPING_TOP_DIR,
          file_name='test_mapping_sample',
          include_subdirs=True,
          checked_files=set(),
      )
    expected = set([TEST_1, TEST_2, TEST_5, TEST_7, TEST_9])
    expected_all_tests = {
        'presubmit': expected,
        'postsubmit': set([TEST_3, TEST_6, TEST_8, TEST_10]),
        'other_group': set([TEST_4]),
    }
    self.assertEqual(expected, tests)
    self.assertEqual(expected_all_tests, all_tests)

  @mock.patch('builtins.input', return_value='')
  def test_confirm_running(self, mock_input):
    """Test _confirm_running method."""
    self.assertTrue(self.ctr._confirm_running([TEST_1]))
    mock_input.return_value = 'N'
    self.assertFalse(self.ctr._confirm_running([TEST_2]))

  def test_print_fuzzy_searching_results(self):
    """Test _print_fuzzy_searching_results"""
    modules = [uc.MODULE_NAME, uc.MODULE2_NAME]
    capture_output = StringIO()
    sys.stdout = capture_output
    self.ctr._print_fuzzy_searching_results(modules)
    sys.stdout = sys.__stdout__
    output = 'Did you mean the following modules?\n{0}\n{1}\n'.format(
        uc.MODULE_NAME, uc.MODULE2_NAME
    )
    self.assertEqual(capture_output.getvalue(), output)

  def test_filter_comments(self):
    """Test filter_comments method"""
    file_with_comments = os.path.join(
        TEST_MAPPING_TOP_DIR, 'folder6', 'test_mapping_sample_with_comments'
    )
    file_with_comments_golden = os.path.join(
        TEST_MAPPING_TOP_DIR, 'folder6', 'test_mapping_sample_golden'
    )
    test_mapping_dict = json.loads(self.ctr.filter_comments(file_with_comments))
    test_mapping_dict_gloden = None
    with open(file_with_comments_golden) as json_file:
      test_mapping_dict_gloden = json.load(json_file)

    self.assertEqual(test_mapping_dict, test_mapping_dict_gloden)

  @mock.patch.object(module_info.ModuleInfo, 'get_testable_modules')
  def test_extract_testable_modules_by_wildcard(self, mock_mods):
    """Test _extract_testable_modules_by_wildcard method."""
    mod_info = module_info.load_from_file(
        module_file=os.path.join(uc.TEST_DATA_DIR, uc.JSON_FILE)
    )
    ctr = cli_t.CLITranslator(mod_info=mod_info)
    mock_mods.return_value = [
        'test1',
        'test2',
        'test3',
        'test11',
        'Test22',
        'Test100',
        'aTest101',
    ]
    # test '*'
    expr1 = ['test*']
    result1 = ['test1', 'test2', 'test3', 'test11']
    self.assertEqual(ctr._extract_testable_modules_by_wildcard(expr1), result1)
    # test '?'
    expr2 = ['test?']
    result2 = ['test1', 'test2', 'test3']
    self.assertEqual(ctr._extract_testable_modules_by_wildcard(expr2), result2)
    # test '*' and '?'
    expr3 = ['*Test???']
    result3 = ['Test100', 'aTest101']
    self.assertEqual(ctr._extract_testable_modules_by_wildcard(expr3), result3)

  @mock.patch.object(os, 'getcwd', return_value='/src/build_top/somewhere')
  @mock.patch.object(
      test_finder_utils,
      'find_host_unit_tests',
      return_value=[uc.HOST_UNIT_TEST_NAME_1, uc.HOST_UNIT_TEST_NAME_2],
  )
  @mock.patch.object(cli_t.CLITranslator, '_find_tests_by_test_mapping')
  @mock.patch.object(
      cli_t.CLITranslator,
      '_get_test_infos',
      side_effect=gettestinfos_side_effect,
  )
  def test_translate_test_mapping_host_unit_test(
      self, _info, mock_testmapping, _find_unit_tests, _getcwd
  ):
    """Test translate method for tests belong to host unit tests."""
    # Check that test mappings feeds into get_test_info properly.
    test_detail1 = test_mapping.TestDetail(uc.TEST_MAPPING_TEST)
    test_detail2 = test_mapping.TestDetail(uc.TEST_MAPPING_TEST_WITH_OPTION)
    mock_testmapping.return_value = ([test_detail1, test_detail2], None)
    self.args.tests = []
    self.args.host = False
    self.args.host_unit_test_only = False
    test_infos = self.ctr.translate(self.args)
    unittest_utils.assert_equal_testinfo_lists(
        self,
        test_infos,
        [
            uc.MODULE_INFO,
            uc.CLASS_INFO,
            uc.MODULE_INFO_HOST_1,
            uc.MODULE_INFO_HOST_2,
        ],
    )

  @mock.patch.object(
      test_finder_utils,
      'find_host_unit_tests',
      return_value=[uc.HOST_UNIT_TEST_NAME_1, uc.HOST_UNIT_TEST_NAME_2],
  )
  @mock.patch.object(cli_t.CLITranslator, '_find_tests_by_test_mapping')
  @mock.patch.object(
      cli_t.CLITranslator,
      '_get_test_infos',
      side_effect=gettestinfos_side_effect,
  )
  def test_translate_test_mapping_without_host_unit_test(
      self, _info, mock_testmapping, _find_unit_tests
  ):
    """Test translate method not using host unit tests if test_mapping arg ."""
    # Check that test mappings feeds into get_test_info properly.
    test_detail1 = test_mapping.TestDetail(uc.TEST_MAPPING_TEST)
    test_detail2 = test_mapping.TestDetail(uc.TEST_MAPPING_TEST_WITH_OPTION)
    mock_testmapping.return_value = ([test_detail1, test_detail2], None)
    self.args.tests = []
    self.args.host = False
    self.args.test_mapping = True
    self.args.host_unit_test_only = False
    test_infos = self.ctr.translate(self.args)
    unittest_utils.assert_equal_testinfo_lists(
        self, test_infos, [uc.MODULE_INFO, uc.CLASS_INFO]
    )


class ParseTestIdentifierTest(unittest.TestCase):
  """Test parse_test_identifier with different test names."""

  def test_no_mainline_modules(self):
    """non-mainline module testing."""
    given = 'testName'

    identifier = cli_t.parse_test_identifier(given)

    self.assertEqual('testName', identifier.test_name)
    self.assertEqual([], identifier.module_names)
    self.assertEqual([], identifier.binary_names)

  def test_single_mainline_module(self):
    """only one mainline module."""
    given = 'testName[Module1.apk]'

    identifier = cli_t.parse_test_identifier(given)

    self.assertEqual('testName', identifier.test_name)
    self.assertEqual(['Module1'], identifier.module_names)
    self.assertEqual(['Module1.apk'], identifier.binary_names)

  def test_multiple_mainline_modules(self):
    """multiple mainline modules."""
    given = 'testName[Module1.apk+Module2.apex]'

    identifier = cli_t.parse_test_identifier(given)

    self.assertEqual('testName', identifier.test_name)
    self.assertEqual(['Module1', 'Module2'], identifier.module_names)
    self.assertEqual(['Module1.apk', 'Module2.apex'], identifier.binary_names)

  def test_missing_closing_bracket(self):
    """test the brackets are not in pair"""
    given = 'testName[Module1.apk+Module2.apex'

    identifier = cli_t.parse_test_identifier(given)

    self.assertEqual(given, identifier.test_name)
    self.assertEqual([], identifier.module_names)
    self.assertEqual([], identifier.binary_names)


class VerifyMainlineModuleTest(fake_filesystem_unittest.TestCase):
  """test verify_mainline_modules sub-methods."""

  def setUp(self):
    """Setup func."""
    self.setUpPyfakefs()

  def test_verified_mainline_modules_no_brackets(self):
    """True for it's not in mainline module pattern. (no brackets)"""
    test_name = 'module0'
    mod_info = create_module_info([
        module(name=test_name),
    ])

    test_identifier = cli_t.parse_test_identifier(test_name)
    translator = cli_t.CLITranslator(mod_info)

    self.assertTrue(translator._verified_mainline_modules(test_identifier))

  def test_verified_mainline_modules_not_valid_test_module(self):
    """False for test_name is not a module."""
    test_name = 'module1[foo.apk+goo.apk]'
    mod_info = create_module_info([
        module(name='module_1'),
        module(name='foo'),
        module(name='goo'),
    ])

    test_identifier = cli_t.parse_test_identifier(test_name)
    translator = cli_t.CLITranslator(mod_info)

    self.assertFalse(translator._verified_mainline_modules(test_identifier))

  def test_verified_mainline_modules_not_valid_mainline_module(self):
    """False for mainline_modules are not modules."""
    test_name = 'module2[foo.apk+goo.apk]'
    mod_info = create_module_info([module(name='module2')])

    test_identifier = cli_t.parse_test_identifier(test_name)
    translator = cli_t.CLITranslator(mod_info)

    self.assertFalse(translator._verified_mainline_modules(test_identifier))

  def test_verified_mainline_modules_no_test_mainline_modules(self):
    """False for no definition in `test_mainline_modules` attribute."""
    test_name = 'module3[foo.apk+goo.apex]'
    mod_info = create_module_info([
        module(name='module3', test_mainline_modules=[]),
        module(name='foo', installed=['out/path/foo.apk']),
        module(name='goo', installed=['out/path/goo.apex']),
    ])

    test_identifier = cli_t.parse_test_identifier(test_name)
    translator = cli_t.CLITranslator(mod_info)

    self.assertFalse(translator._verified_mainline_modules(test_identifier))

  def test_verified_mainline_modules_test_mainline_modules(self):
    """True for definition in `test_mainline_modules` attribute."""
    test_name = 'module4[foo.apk+goo.apex]'
    mod_info = create_module_info([
        module(name='module4', test_mainline_modules=['foo.apk+goo.apex']),
        module(name='foo', installed=['out/path/foo.apk']),
        module(name='goo', installed=['out/path/goo.apex']),
    ])

    test_identifier = cli_t.parse_test_identifier(test_name)
    translator = cli_t.CLITranslator(mod_info)

    self.assertTrue(translator._verified_mainline_modules(test_identifier))

  def test_verified_mainline_modules_were_in_test_config(self):
    """True for mainline modules were defined in the test_config."""
    test_name = 'module5[foo.apk+goo.apex]'
    mainline_config = 'out/module3/AndroidTest.xml'
    self.fs.create_file(
        mainline_config,
        contents="""
            <configuration description="Mainline Module tests">
                <option name="config"
                        key="parameter" value="value_1" />
                <option name="config-descriptor:metadata"
                        key="mainline-param" value="foo.apk+goo.apex" />
            </configuration>
            """,
    )
    mod_info = create_module_info([
        module(
            name='module5',
            test_config=[mainline_config],
            test_mainline_modules=[],
            auto_test_config=[],
        ),
        module(name='foo', installed=['out/path/foo.apk']),
        module(name='goo', installed=['out/path/goo.apex']),
    ])

    test_identifier = cli_t.parse_test_identifier(test_name)
    translator = cli_t.CLITranslator(mod_info)

    self.assertTrue(translator._verified_mainline_modules(test_identifier))

  def test_verified_mainline_modules_were_in_auto_config(self):
    """True for 'auto_test_config'=[True]"""
    test_name = 'module6[foo.apk+goo.apex]'
    mod_info = create_module_info([
        module(
            name='module6',
            test_config=['somewhere/AndroidTest.xml'],
            auto_test_config=[True],
        ),
        module(name='foo', installed=['out/path/foo.apk']),
        module(name='goo', installed=['out/path/goo.apex']),
    ])

    test_identifier = cli_t.parse_test_identifier(test_name)
    translator = cli_t.CLITranslator(mod_info)

    self.assertTrue(translator._verified_mainline_modules(test_identifier))

  def test_verified_mainline_modules_have_no_association(self):
    """False for null auto_test_config and null `mainline-param` in the test_config."""
    test_name = 'module7[foo.apk+goo.apex]'
    config = 'out/module7/AndroidTest.xml'
    self.fs.create_file(
        config,
        contents="""
            <configuration description="Mainline Module tests">
                <option name="config"
                        key="parameter" value="value_1" />
            </configuration>
            """,
    )
    mod_info = create_module_info([
        module(name='module7', test_config=[config], auto_test_config=[]),
        module(name='foo', installed=['out/path/foo.apk']),
        module(name='goo', installed=['out/path/goo.apex']),
    ])

    test_identifier = cli_t.parse_test_identifier(test_name)
    translator = cli_t.CLITranslator(mod_info)

    self.assertFalse(translator._verified_mainline_modules(test_identifier))

  def test_verified_mainline_modules_were_in_auto_config(self):
    """False for the given mainline is a capex file."""
    test_name = 'module8[foo.apk+goo.apex]'
    mod_info = create_module_info([
        module(name='module8', test_mainline_modules=['foo.apk+goo.apex']),
        module(name='foo', installed=['out/path/foo.apk']),
        module(name='goo', installed=['out/path/goo.capex']),
    ])

    test_identifier = cli_t.parse_test_identifier(test_name)
    translator = cli_t.CLITranslator(mod_info)

    self.assertFalse(translator._verified_mainline_modules(test_identifier))


def create_module_info(modules=None):
  """wrapper func for creating module_info.ModuleInfo"""
  name_to_module_info = {}
  modules = modules or []

  for m in modules:
    name_to_module_info[m['module_name']] = m

  return module_info.load_from_dict(name_to_module_info)


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
    test_mainline_modules=None,
):
  """return a module info dict."""
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
  m['test_mainline_modules'] = test_mainline_modules or []
  return m


def _gather_build_targets(test_infos):
  targets = set()
  for t_info in test_infos:
    targets |= t_info.build_targets
  return targets


if __name__ == '__main__':
  unittest.main()
