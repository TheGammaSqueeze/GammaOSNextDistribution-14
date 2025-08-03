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

"""Unittests for atest_utils."""

# pylint: disable=invalid-name

import hashlib
from io import StringIO
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

from atest import arg_parser
from atest import atest_error
from atest import atest_utils
from atest import constants
from atest import unittest_constants
from atest import unittest_utils
from atest.atest_enum import FilterType
from atest.test_finders import test_info
from pyfakefs import fake_filesystem_unittest

TEST_MODULE_NAME_A = 'ModuleNameA'
TEST_RUNNER_A = 'FakeTestRunnerA'
TEST_BUILD_TARGET_A = set(['bt1', 'bt2'])
TEST_DATA_A = {'test_data_a_1': 'a1', 'test_data_a_2': 'a2'}
TEST_SUITE_A = 'FakeSuiteA'
TEST_MODULE_CLASS_A = 'FAKE_MODULE_CLASS_A'
TEST_INSTALL_LOC_A = set(['host', 'device'])
TEST_FINDER_A = 'MODULE'
TEST_INFO_A = test_info.TestInfo(
    TEST_MODULE_NAME_A,
    TEST_RUNNER_A,
    TEST_BUILD_TARGET_A,
    TEST_DATA_A,
    TEST_SUITE_A,
    TEST_MODULE_CLASS_A,
    TEST_INSTALL_LOC_A,
)
TEST_INFO_A.test_finder = TEST_FINDER_A
TEST_ZIP_DATA_DIR = 'zip_files'
TEST_SINGLE_ZIP_NAME = 'single_file.zip'
TEST_MULTI_ZIP_NAME = 'multi_file.zip'

REPO_INFO_OUTPUT = """Manifest branch: test_branch
Manifest merge branch: refs/heads/test_branch
Manifest groups: all,-notdefault
----------------------------
"""


class ConcatenatePathTest(unittest.TestCase):
  """Class that tests path concatenation."""

  @classmethod
  def setUpClass(cls):
    """Mock the environment variables for the entire test class"""
    cls.build_top = '/src/build_top'
    cls.prod_out = '/src/build_top/product_out'
    cls.host_out = '/src/build_top/host_out'
    cls.target_out_cases = '/src/build_top/product_out/testcases'
    cls.host_out_cases = '/src/build_top/host_out/testcases'
    cls.target_product = 'test_target_product'
    cls.build_variant = 'test_build_variant'
    cls.mock_getenv = mock.patch.dict(
        os.environ,
        {
            'ANDROID_BUILD_TOP': cls.build_top,
            'ANDROID_PRODUCT_OUT': cls.prod_out,
            'ANDROID_TARGET_OUT_TESTCASES': cls.target_out_cases,
            'ANDROID_HOST_OUT': cls.host_out,
            'ANDROID_HOST_OUT_TESTCASES': cls.host_out_cases,
            'TARGET_PRODUCT': cls.target_product,
            'TARGET_BUILD_VARIANT': cls.build_variant,
        },
    )
    cls.mock_getenv.start()

  @classmethod
  def tearDownClass(cls):
    """Clean up the mocks after the test class finishes"""
    cls.mock_getenv.stop()

  def test_get_vars(self):
    """Test the values of AndroidVariables are expected"""
    variables = atest_utils.AndroidVariables()

    self.assertEqual(variables.build_top, self.build_top)
    self.assertEqual(variables.product_out, self.prod_out)
    self.assertEqual(variables.target_out_cases, self.target_out_cases)
    self.assertEqual(variables.host_out, self.host_out)
    self.assertEqual(variables.host_out_cases, self.host_out_cases)
    self.assertEqual(variables.target_product, self.target_product)
    self.assertEqual(variables.build_variant, self.build_variant)

  def test_atest_utils_get_build_top(self):
    """Test concatenating strings with get_build_top()."""
    expected_path = Path(self.build_top, 'path/to/project')

    return_path = atest_utils.get_build_top('path/to/project')

    self.assertEqual(expected_path, return_path)

  def test_atest_utils_get_product_out(self):
    """Test concatenating strings with get_product_out()."""
    expected_path = Path(self.prod_out, 'module-info.json')

    return_path = atest_utils.get_product_out('module-info.json')

    self.assertEqual(expected_path, return_path)

  def test_atest_utils_get_host_out(self):
    """Test concatenating strings with get_host_out()."""
    expected_path = Path(self.host_out, 'bin/adb')

    return_path = atest_utils.get_host_out('bin', 'adb')

    self.assertEqual(expected_path, return_path)


class GetBuildOutDirTests(unittest.TestCase):
  """Test get_build_out_dir() for various conditions."""

  def setUp(self) -> None:
    self.abs_OUT_DIR = '/somewhere/out'
    self.rel_OUT_DIR = 'somewhere/out'
    self.abs_OUT_DIR_COMMON_BASE = '/somewhere/common_out'
    self.rel_OUT_DIR_COMMON_BASE = 'somewhere/common_out'

  def test_get_build_abs_out_dir(self):
    """Test when OUT_DIR is an absolute path."""
    with mock.patch.dict(
        'os.environ',
        {
            constants.ANDROID_BUILD_TOP: '/src/build/top',
            'OUT_DIR': self.abs_OUT_DIR,
        },
    ):
      expected_out_dir = Path(self.abs_OUT_DIR)

      returned_out_dir = atest_utils.get_build_out_dir()

      self.assertEqual(expected_out_dir, returned_out_dir)

  def test_get_build_rel_out_dir(self):
    """Test when OUT_DIR is a relative path."""
    with mock.patch.dict(
        'os.environ',
        {
            constants.ANDROID_BUILD_TOP: '/src/build/top',
            'OUT_DIR': self.rel_OUT_DIR,
        },
    ):
      expected_out_dir = atest_utils.get_build_top(self.rel_OUT_DIR)

      returned_out_dir = atest_utils.get_build_out_dir()

      self.assertEqual(expected_out_dir, returned_out_dir)

  def test_get_build_abs_out_dir_common_base(self):
    """Test whe OUT_DIR_COMMON_BASE is an absolute path."""
    build_top_path = '/src/build/top'
    branch_name = Path(build_top_path).name
    with mock.patch.dict(
        'os.environ',
        {
            constants.ANDROID_BUILD_TOP: build_top_path,
            'OUT_DIR_COMMON_BASE': self.abs_OUT_DIR_COMMON_BASE,
        },
    ):
      expected_out_dir = Path(self.abs_OUT_DIR_COMMON_BASE, branch_name)

      returned_out_dir = atest_utils.get_build_out_dir()

      self.assertEqual(expected_out_dir, returned_out_dir)

  def test_get_build_rel_out_dir_common_base(self):
    """Test whe OUT_DIR_COMMON_BASE is a relative path."""
    build_top_path = '/src/build/top'
    branch_name = Path(build_top_path).name
    with mock.patch.dict(
        'os.environ',
        {
            constants.ANDROID_BUILD_TOP: build_top_path,
            'OUT_DIR_COMMON_BASE': self.rel_OUT_DIR_COMMON_BASE,
        },
    ):
      expected_out_dir = Path(
          build_top_path, self.rel_OUT_DIR_COMMON_BASE, branch_name
      )

      returned_out_dir = atest_utils.get_build_out_dir()

      self.assertEqual(expected_out_dir, returned_out_dir)

  def test_get_build_out_dir(self):
    """Test when OUT_DIR and OUT_DIR_COMMON_BASE are null."""
    with mock.patch.dict(
        'os.environ', {constants.ANDROID_BUILD_TOP: '/src/build/top'}
    ):
      expected_out_dir = atest_utils.get_build_top('out')

      returned_out_dir = atest_utils.get_build_out_dir()

      self.assertEqual(expected_out_dir, returned_out_dir)


# pylint: disable=protected-access
# pylint: disable=too-many-public-methods
class AtestUtilsUnittests(unittest.TestCase):
  """Unit tests for atest_utils.py"""

  def test_capture_fail_section_has_fail_section(self):
    """Test capture_fail_section when has fail section."""
    test_list = [
        'AAAAAA',
        'FAILED: Error1',
        '^\n',
        'Error2\n',
        '[  6% 191/2997] BBBBBB\n',
        'CCCCC',
        '[  20% 322/2997] DDDDDD\n',
        'EEEEE',
    ]
    want_list = ['FAILED: Error1', '^\n', 'Error2\n']
    self.assertEqual(want_list, atest_utils._capture_fail_section(test_list))

  def test_capture_fail_section_no_fail_section(self):
    """Test capture_fail_section when no fail section."""
    test_list = ['[ 6% 191/2997] XXXXX', 'YYYYY: ZZZZZ']
    want_list = []
    self.assertEqual(want_list, atest_utils._capture_fail_section(test_list))

  def test_is_test_mapping_none_test_mapping_args(self):
    """Test method is_test_mapping."""
    non_tm_args = ['--host-unit-test-only']

    for argument in non_tm_args:
      args = arg_parser.create_atest_arg_parser().parse_args([argument])
      self.assertFalse(
          atest_utils.is_test_mapping(args),
          'Option %s indicates NOT a test_mapping!' % argument,
      )

  def test_is_test_mapping_test_mapping_args(self):
    """Test method is_test_mapping."""
    tm_args = ['--test-mapping', '--include-subdirs']

    for argument in tm_args:
      args = arg_parser.create_atest_arg_parser().parse_args([argument])
      self.assertTrue(
          atest_utils.is_test_mapping(args),
          'Option %s indicates a test_mapping!' % argument,
      )

  def test_is_test_mapping_implicit_test_mapping(self):
    """Test method is_test_mapping."""
    args = arg_parser.create_atest_arg_parser().parse_args(
        ['--test', '--build', ':postsubmit']
    )
    self.assertTrue(
        atest_utils.is_test_mapping(args),
        'Option %s indicates a test_mapping!' % args,
    )

  def test_is_test_mapping_with_testname(self):
    """Test method is_test_mapping."""
    irrelevant_args = ['--test', ':postsubmit', 'testname']

    args = arg_parser.create_atest_arg_parser().parse_args(irrelevant_args)
    self.assertFalse(
        atest_utils.is_test_mapping(args),
        'Option %s indicates a test_mapping!' % args,
    )

  def test_is_test_mapping_false(self):
    """Test method is_test_mapping."""
    args = arg_parser.create_atest_arg_parser().parse_args(
        ['--test', '--build', 'hello_atest']
    )

    self.assertFalse(atest_utils.is_test_mapping(args))

  def test_has_colors(self):
    """Test method _has_colors."""
    # stream is file I/O
    stream = open('/tmp/test_has_colors.txt', 'wb')
    self.assertFalse(atest_utils._has_colors(stream))
    stream.close()

    # stream is not a tty(terminal).
    stream = mock.Mock()
    stream.isatty.return_value = False
    self.assertFalse(atest_utils._has_colors(stream))

    # stream is a tty(terminal).
    stream = mock.Mock()
    stream.isatty.return_value = True
    self.assertTrue(atest_utils._has_colors(stream))

  @mock.patch('atest.atest_utils._has_colors')
  def test_colorize(self, mock_has_colors):
    """Test method colorize."""
    original_str = 'test string'
    green_no = 2

    # _has_colors() return False.
    mock_has_colors.return_value = False
    converted_str = atest_utils.colorize(
        original_str, green_no, bp_color=constants.RED
    )
    self.assertEqual(original_str, converted_str)

    # Green text with red background.
    mock_has_colors.return_value = True
    converted_str = atest_utils.colorize(
        original_str, green_no, bp_color=constants.RED
    )
    green_highlight_string = '\x1b[1;32;41m%s\x1b[0m' % original_str
    self.assertEqual(green_highlight_string, converted_str)

    # Green text, no background.
    mock_has_colors.return_value = True
    converted_str = atest_utils.colorize(original_str, green_no)
    green_no_highlight_string = '\x1b[1;32m%s\x1b[0m' % original_str
    self.assertEqual(green_no_highlight_string, converted_str)

  @mock.patch('atest.atest_utils._has_colors')
  def test_colorful_print(self, mock_has_colors):
    """Test method colorful_print."""
    testing_str = 'color_print_test'
    green_no = 2

    # _has_colors() return False.
    mock_has_colors.return_value = False
    capture_output = StringIO()
    sys.stdout = capture_output
    atest_utils.colorful_print(
        testing_str, green_no, bp_color=constants.RED, auto_wrap=False
    )
    sys.stdout = sys.__stdout__
    uncolored_string = testing_str
    self.assertEqual(capture_output.getvalue(), uncolored_string)

    # Green text with red background, but no wrap.
    mock_has_colors.return_value = True
    capture_output = StringIO()
    sys.stdout = capture_output
    atest_utils.colorful_print(
        testing_str, green_no, bp_color=constants.RED, auto_wrap=False
    )
    sys.stdout = sys.__stdout__
    green_highlight_no_wrap_string = '\x1b[1;32;41m%s\x1b[0m' % testing_str
    self.assertEqual(capture_output.getvalue(), green_highlight_no_wrap_string)

    # Green text, no background, no wrap.
    mock_has_colors.return_value = True
    capture_output = StringIO()
    sys.stdout = capture_output
    atest_utils.colorful_print(testing_str, green_no, auto_wrap=False)
    sys.stdout = sys.__stdout__
    green_no_high_no_wrap_string = '\x1b[1;32m%s\x1b[0m' % testing_str
    self.assertEqual(capture_output.getvalue(), green_no_high_no_wrap_string)

    # Green text with red background and wrap.
    mock_has_colors.return_value = True
    capture_output = StringIO()
    sys.stdout = capture_output
    atest_utils.colorful_print(
        testing_str, green_no, bp_color=constants.RED, auto_wrap=True
    )
    sys.stdout = sys.__stdout__
    green_highlight_wrap_string = '\x1b[1;32;41m%s\x1b[0m\n' % testing_str
    self.assertEqual(capture_output.getvalue(), green_highlight_wrap_string)

    # Green text with wrap, but no background.
    mock_has_colors.return_value = True
    capture_output = StringIO()
    sys.stdout = capture_output
    atest_utils.colorful_print(testing_str, green_no, auto_wrap=True)
    sys.stdout = sys.__stdout__
    green_wrap_no_highlight_string = '\x1b[1;32m%s\x1b[0m\n' % testing_str
    self.assertEqual(capture_output.getvalue(), green_wrap_no_highlight_string)

  def test_is_supported_mainline_module(self):
    """Test the installed artifacts are supported."""
    self.assertTrue(atest_utils.is_supported_mainline_module('out/foo.apk'))
    self.assertTrue(atest_utils.is_supported_mainline_module('out/foo.apks'))
    self.assertTrue(atest_utils.is_supported_mainline_module('out/foo.apex'))
    self.assertFalse(atest_utils.is_supported_mainline_module('out/foo.capex'))

  def test_get_test_and_mainline_modules(self):
    """Test whether the given test reference is a mainline module test."""
    # regular test.
    self.assertIsNone(atest_utils.get_test_and_mainline_modules('test_name'))
    # missing trailing bracket.
    self.assertIsNone(
        atest_utils.get_test_and_mainline_modules('test_name[foo.apk+goo.apex')
    )
    # valid mainline module syntax
    self.assertIsNotNone(
        atest_utils.get_test_and_mainline_modules('test_name[foo.apk]')
    )
    self.assertIsNotNone(
        atest_utils.get_test_and_mainline_modules('test_name[foo.apk+goo.apex]')
    )

  def test_get_test_info_cache_path(self):
    """Test method get_test_info_cache_path."""
    input_file_name = 'mytest_name'
    cache_root = '/a/b/c'
    expect_hashed_name = (
        '%s.cache' % hashlib.md5(str(input_file_name).encode()).hexdigest()
    )
    self.assertEqual(
        os.path.join(cache_root, expect_hashed_name),
        atest_utils.get_test_info_cache_path(input_file_name, cache_root),
    )

  def test_get_and_load_cache(self):
    """Test method update_test_info_cache and load_test_info_cache."""
    test_reference = 'myTestRefA'
    test_cache_dir = tempfile.mkdtemp()
    atest_utils.update_test_info_cache(
        test_reference, [TEST_INFO_A], test_cache_dir
    )
    unittest_utils.assert_equal_testinfo_sets(
        self,
        set([TEST_INFO_A]),
        atest_utils.load_test_info_cache(test_reference, test_cache_dir),
    )

  @mock.patch('subprocess.check_output')
  def test_get_modified_files(self, mock_co):
    """Test method get_modified_files"""
    mock_co.side_effect = [
        x.encode('utf-8')
        for x in ['/a/b/', '\n', 'test_fp1.java\nc/test_fp2.java']
    ]
    self.assertEqual(
        {'/a/b/test_fp1.java', '/a/b/c/test_fp2.java'},
        atest_utils.get_modified_files(''),
    )
    mock_co.side_effect = [
        x.encode('utf-8') for x in ['/a/b/', 'test_fp4', '/test_fp3.java']
    ]
    self.assertEqual(
        {'/a/b/test_fp4', '/a/b/test_fp3.java'},
        atest_utils.get_modified_files(''),
    )

  def test_delimiter(self):
    """Test method delimiter"""
    self.assertEqual('\n===\n\n', atest_utils.delimiter('=', 3, 1, 2))

  def test_has_python_module(self):
    """Test method has_python_module"""
    self.assertFalse(atest_utils.has_python_module('M_M'))
    self.assertTrue(atest_utils.has_python_module('os'))

  @mock.patch.object(atest_utils, 'matched_tf_error_log', return_value=True)
  def test_read_zip_single_text(self, _matched):
    """Test method extract_zip_text include only one text file."""
    zip_path = os.path.join(
        unittest_constants.TEST_DATA_DIR,
        TEST_ZIP_DATA_DIR,
        TEST_SINGLE_ZIP_NAME,
    )
    expect_content = '\nfile1_line1\nfile1_line2\n'
    self.assertEqual(expect_content, atest_utils.extract_zip_text(zip_path))

  @mock.patch.object(atest_utils, 'matched_tf_error_log', return_value=True)
  def test_read_zip_multi_text(self, _matched):
    """Test method extract_zip_text include multiple text files."""
    zip_path = os.path.join(
        unittest_constants.TEST_DATA_DIR, TEST_ZIP_DATA_DIR, TEST_MULTI_ZIP_NAME
    )
    expect_content = '\nfile1_line1\nfile1_line2\n\nfile2_line1\nfile2_line2\n'
    self.assertEqual(expect_content, atest_utils.extract_zip_text(zip_path))

  def test_matched_tf_error_log(self):
    """Test method extract_zip_text include multiple text files."""
    matched_content = '05-25 17:37:04 E/XXXXX YYYYY'
    not_matched_content = '05-25 17:37:04 I/XXXXX YYYYY'
    # Test matched content
    self.assertEqual(True, atest_utils.matched_tf_error_log(matched_content))
    # Test not matched content
    self.assertEqual(
        False, atest_utils.matched_tf_error_log(not_matched_content)
    )

  # pylint: disable=no-member
  def test_read_test_record_proto(self):
    """Test method read_test_record."""
    test_record_file_path = os.path.join(
        unittest_constants.TEST_DATA_DIR, 'test_record.proto.testonly'
    )
    test_record = atest_utils.read_test_record(test_record_file_path)
    self.assertEqual(
        test_record.children[0].inline_test_record.test_record_id,
        'x86 hello_world_test',
    )

  def test_load_json_safely_file_inexistent(self):
    """Test method load_json_safely if file does not exist."""
    json_file_path = Path(unittest_constants.TEST_DATA_DIR).joinpath(
        'not_exist.json'
    )
    self.assertEqual({}, atest_utils.load_json_safely(json_file_path))

  def test_load_json_safely_valid_json_format(self):
    """Test method load_json_safely if file exists and format is valid."""
    json_file_path = Path(unittest_constants.TEST_DATA_DIR).joinpath(
        'module-info.json'
    )
    content = atest_utils.load_json_safely(json_file_path)
    self.assertEqual(
        'MainModule1', content.get('MainModule1').get('module_name')
    )
    self.assertEqual(
        [], content.get('MainModule2').get('test_mainline_modules')
    )

  def test_load_json_safely_invalid_json_format(self):
    """Test method load_json_safely if file exist but content is invalid."""
    json_file_path = Path(unittest_constants.TEST_DATA_DIR).joinpath(
        'not-valid-module-info.json'
    )
    self.assertEqual({}, atest_utils.load_json_safely(json_file_path))

  @mock.patch('os.getenv')
  def test_get_manifest_branch(self, mock_env):
    """Test method get_manifest_branch"""
    build_top = tempfile.TemporaryDirectory()
    mock_env.return_value = build_top.name
    repo_dir = Path(build_top.name).joinpath('.repo')
    portal_xml = repo_dir.joinpath('manifest.xml')
    manifest_dir = repo_dir.joinpath('manifests')
    target_xml = manifest_dir.joinpath('Default.xml')
    repo_dir.mkdir()
    manifest_dir.mkdir()
    content_portal = '<manifest><include name="Default.xml" /></manifest>'
    content_manifest = """<manifest>
            <remote name="aosp" fetch=".." review="https://android-review.googlesource.com/" />
            <default revision="MONSTER-dev" remote="aosp" sync-j="4" />
        </manifest>"""

    # 1. The manifest.xml(portal) contains 'include' directive: 'Default.xml'.
    # Search revision in .repo/manifests/Default.xml.
    with open(portal_xml, 'w') as cache:
      cache.write(content_portal)
    with open(target_xml, 'w') as cache:
      cache.write(content_manifest)
    self.assertEqual('MONSTER-dev', atest_utils.get_manifest_branch())
    self.assertEqual('aosp-MONSTER-dev', atest_utils.get_manifest_branch(True))
    os.remove(target_xml)
    os.remove(portal_xml)

    # 2. The manifest.xml contains neither 'include' nor 'revision' directive,
    # keep searching revision in .repo/manifests/default.xml by default.
    with open(portal_xml, 'w') as cache:
      cache.write('<manifest></manifest>')
    default_xml = manifest_dir.joinpath('default.xml')
    with open(default_xml, 'w') as cache:
      cache.write(content_manifest)
    self.assertEqual('MONSTER-dev', atest_utils.get_manifest_branch())
    os.remove(default_xml)
    os.remove(portal_xml)

    # 3. revision was directly defined in 'manifest.xml'.
    with open(portal_xml, 'w') as cache:
      cache.write(content_manifest)
    self.assertEqual('MONSTER-dev', atest_utils.get_manifest_branch())
    os.remove(portal_xml)

    # 4. Return None if the included xml does not exist.
    with open(portal_xml, 'w') as cache:
      cache.write(content_portal)
    self.assertEqual('', atest_utils.get_manifest_branch())
    os.remove(portal_xml)

  def test_has_wildcard(self):
    """Test method of has_wildcard"""
    self.assertFalse(atest_utils.has_wildcard('test1'))
    self.assertFalse(atest_utils.has_wildcard(['test1']))
    self.assertTrue(atest_utils.has_wildcard('test1?'))
    self.assertTrue(atest_utils.has_wildcard(['test1', 'b*', 'a?b*']))

  # pylint: disable=anomalous-backslash-in-string
  def test_quote(self):
    """Test method of quote()"""
    target_str = r'TEST_(F|P)[0-9].*\w$'
    expected_str = "'TEST_(F|P)[0-9].*\w$'"
    self.assertEqual(atest_utils.quote(target_str), expected_str)
    self.assertEqual(atest_utils.quote('TEST_P224'), 'TEST_P224')

  @mock.patch('builtins.input', return_value='')
  def test_prompt_with_yn_result(self, mock_input):
    """Test method of prompt_with_yn_result"""
    msg = 'Do you want to continue?'
    mock_input.return_value = ''
    self.assertTrue(atest_utils.prompt_with_yn_result(msg, True))
    self.assertFalse(atest_utils.prompt_with_yn_result(msg, False))
    mock_input.return_value = 'y'
    self.assertTrue(atest_utils.prompt_with_yn_result(msg, True))
    mock_input.return_value = 'nO'
    self.assertFalse(atest_utils.prompt_with_yn_result(msg, True))

  def test_get_android_junit_config_filters(self):
    """Test method of get_android_junit_config_filters"""
    no_filter_test_config = os.path.join(
        unittest_constants.TEST_DATA_DIR, 'filter_configs', 'no_filter.cfg'
    )
    self.assertEqual(
        {}, atest_utils.get_android_junit_config_filters(no_filter_test_config)
    )

    filtered_test_config = os.path.join(
        unittest_constants.TEST_DATA_DIR, 'filter_configs', 'filter.cfg'
    )
    filter_dict = atest_utils.get_android_junit_config_filters(
        filtered_test_config
    )
    include_annotations = filter_dict.get(constants.INCLUDE_ANNOTATION)
    include_annotations.sort()
    self.assertEqual(['include1', 'include2'], include_annotations)
    exclude_annotation = filter_dict.get(constants.EXCLUDE_ANNOTATION)
    exclude_annotation.sort()
    self.assertEqual(['exclude1', 'exclude2'], exclude_annotation)

  def test_md5sum_file_existent(self):
    """Test method of md5sum for an existent file."""
    with tempfile.NamedTemporaryFile() as tmp_file:
      with open(tmp_file.name, 'w', encoding='utf-8') as f:
        f.write('some context')
      expected_md5 = '6d583707b0149c07cc19a05f5fdc320c'

      actual_md5 = atest_utils.md5sum(tmp_file.name)

      self.assertEqual(actual_md5, expected_md5)

  def test_md5sum_file_inexistent(self):
    """Test method of md5sum for an inexistent file."""
    inexistent_file = os.path.join('/somewhere/does/not/exist')
    expected_md5 = ''

    actual_md5 = atest_utils.md5sum(inexistent_file)

    self.assertEqual(actual_md5, expected_md5)

  def test_check_md5(self):
    """Test method of check_md5"""
    file1 = os.path.join(
        unittest_constants.TEST_DATA_DIR, unittest_constants.JSON_FILE
    )
    checksum_file = '/tmp/_tmp_module-info.json'
    atest_utils.save_md5([file1], '/tmp/_tmp_module-info.json')
    self.assertTrue(atest_utils.check_md5(checksum_file))
    os.remove(checksum_file)
    self.assertFalse(atest_utils.check_md5(checksum_file))
    self.assertTrue(atest_utils.check_md5(checksum_file, missing_ok=True))

  def test_get_config_parameter(self):
    """Test method of get_config_parameter"""
    parameter_config = os.path.join(
        unittest_constants.TEST_DATA_DIR, 'parameter_config', 'parameter.cfg'
    )
    no_parameter_config = os.path.join(
        unittest_constants.TEST_DATA_DIR, 'parameter_config', 'no_parameter.cfg'
    )

    # Test parameter empty value
    self.assertEqual(
        set(), atest_utils.get_config_parameter(no_parameter_config)
    )

    # Test parameter empty value
    self.assertEqual(
        {'value_1', 'value_2', 'value_3', 'value_4'},
        atest_utils.get_config_parameter(parameter_config),
    )

  def test_get_config_device(self):
    """Test method of get_config_device"""
    device_config = os.path.join(
        unittest_constants.TEST_DATA_DIR,
        'parameter_config',
        'multiple_device.cfg',
    )
    self.assertEqual(
        {'device_1', 'device_2'}, atest_utils.get_config_device(device_config)
    )

  def test_get_mainline_param(self):
    """Test method of get_mainline_param"""
    mainline_param_config = os.path.join(
        unittest_constants.TEST_DATA_DIR,
        'parameter_config',
        'mainline_param.cfg',
    )
    self.assertEqual(
        {'foo1.apex', 'foo2.apk+foo3.apk'},
        atest_utils.get_mainline_param(mainline_param_config),
    )
    no_mainline_param_config = os.path.join(
        unittest_constants.TEST_DATA_DIR, 'parameter_config', 'parameter.cfg'
    )
    self.assertEqual(
        set(), atest_utils.get_mainline_param(no_mainline_param_config)
    )

  def test_get_full_annotation_class_name(self):
    """Test method of get_full_annotation_class_name."""
    app_mode_full = 'android.platform.test.annotations.AppModeFull'
    presubmit = 'android.platform.test.annotations.Presubmit'
    module_info = {
        'srcs': [
            os.path.join(
                unittest_constants.TEST_DATA_DIR,
                'annotation_testing',
                'Annotation.src',
            )
        ]
    }
    # get annotation class from keyword
    self.assertEqual(
        atest_utils.get_full_annotation_class_name(module_info, 'presubmit'),
        presubmit,
    )
    # get annotation class from an accurate fqcn keyword.
    self.assertEqual(
        atest_utils.get_full_annotation_class_name(module_info, presubmit),
        presubmit,
    )
    # accept fqcn keyword in lowercase.
    self.assertEqual(
        atest_utils.get_full_annotation_class_name(
            module_info, 'android.platform.test.annotations.presubmit'
        ),
        presubmit,
    )
    # unable to get annotation class from keyword.
    self.assertNotEqual(
        atest_utils.get_full_annotation_class_name(
            module_info, 'appleModefull'
        ),
        app_mode_full,
    )
    # do not support partial-correct keyword.
    self.assertNotEqual(
        atest_utils.get_full_annotation_class_name(
            module_info, 'android.platform.test.annotations.pres'
        ),
        presubmit,
    )

  def test_has_mixed_type_filters_one_module_with_one_type_return_false(self):
    """Test method of has_mixed_type_filters"""
    filter_1 = test_info.TestFilter('CLASS', frozenset(['METHOD']))
    test_data_1 = {constants.TI_FILTER: [filter_1]}
    test_info_1 = test_info.TestInfo(
        'MODULE', 'RUNNER', set(), test_data_1, 'SUITE', '', set()
    )
    self.assertFalse(atest_utils.has_mixed_type_filters([test_info_1]))

  def test_has_mixed_type_filters_one_module_with_mixed_types_return_true(self):
    """Test method of has_mixed_type_filters"""
    filter_1 = test_info.TestFilter('CLASS', frozenset(['METHOD']))
    filter_2 = test_info.TestFilter('CLASS', frozenset(['METHOD*']))
    test_data_2 = {constants.TI_FILTER: [filter_1, filter_2]}
    test_info_2 = test_info.TestInfo(
        'MODULE', 'RUNNER', set(), test_data_2, 'SUITE', '', set()
    )
    self.assertTrue(atest_utils.has_mixed_type_filters([test_info_2]))

  def test_has_mixed_type_filters_two_module_with_mixed_types_return_false(
      self,
  ):
    """Test method of has_mixed_type_filters"""
    filter_1 = test_info.TestFilter('CLASS', frozenset(['METHOD']))
    test_data_1 = {constants.TI_FILTER: [filter_1]}
    test_info_1 = test_info.TestInfo(
        'MODULE', 'RUNNER', set(), test_data_1, 'SUITE', '', set()
    )
    filter_3 = test_info.TestFilter('CLASS', frozenset(['METHOD*']))
    test_data_3 = {constants.TI_FILTER: [filter_3]}
    test_info_3 = test_info.TestInfo(
        'MODULE3', 'RUNNER', set(), test_data_3, 'SUITE', '', set()
    )
    self.assertFalse(
        atest_utils.has_mixed_type_filters([test_info_1, test_info_3])
    )

  def test_get_filter_types(self):
    """Test method of get_filter_types."""
    filters = set(['CLASS#METHOD'])
    expect_types = set([FilterType.REGULAR_FILTER.value])
    self.assertEqual(atest_utils.get_filter_types(filters), expect_types)

    filters = set(['CLASS#METHOD*'])
    expect_types = set([FilterType.WILDCARD_FILTER.value])
    self.assertEqual(atest_utils.get_filter_types(filters), expect_types)

    filters = set(['CLASS#METHOD', 'CLASS#METHOD*'])
    expect_types = set(
        [FilterType.WILDCARD_FILTER.value, FilterType.REGULAR_FILTER.value]
    )
    self.assertEqual(atest_utils.get_filter_types(filters), expect_types)

    filters = set(['CLASS#METHOD?', 'CLASS#METHOD*'])
    expect_types = set([FilterType.WILDCARD_FILTER.value])
    self.assertEqual(atest_utils.get_filter_types(filters), expect_types)

  def test_get_bp_content(self):
    """Method get_bp_content."""
    # 1. "manifest" and "instrumentation_for" are defined.
    content = """android_test    {
                // comment
                instrumentation_for: "AmSlam", // comment
                manifest: "AndroidManifest-test.xml",
                name: "AmSlamTests",
        }"""
    expected_result = {
        'AmSlamTests': {
            'target_module': 'AmSlam',
            'manifest': 'AndroidManifest-test.xml',
        }
    }
    temp_dir = tempfile.TemporaryDirectory()
    tmpbp = Path(temp_dir.name).joinpath('Android.bp')
    with open(tmpbp, 'w') as cache:
      cache.write(content)
    self.assertEqual(
        atest_utils.get_bp_content(tmpbp, 'android_test'), expected_result
    )
    temp_dir.cleanup()

    # 2. Only name is defined, will give default manifest and null
    # target_module.
    content = """android_app    {
                // comment
                name: "AmSlam",
                srcs: ["src1.java", "src2.java"]
        }"""
    expected_result = {
        'AmSlam': {'target_module': '', 'manifest': 'AndroidManifest.xml'}
    }
    temp_dir = tempfile.TemporaryDirectory()
    tmpbp = Path(temp_dir.name).joinpath('Android.bp')
    with open(tmpbp, 'w') as cache:
      cache.write(content)
    self.assertEqual(
        atest_utils.get_bp_content(tmpbp, 'android_app'), expected_result
    )
    temp_dir.cleanup()

    # 3. Not even an Android.bp.
    content = """LOCAL_PATH := $(call my-dir)
                # comment
                include $(call all-subdir-makefiles)
                LOCAL_MODULE := atest_foo_test
        }"""
    temp_dir = tempfile.TemporaryDirectory()
    tmpbp = Path(temp_dir.name).joinpath('Android.mk')
    with open(tmpbp, 'w') as cache:
      cache.write(content)
    self.assertEqual(atest_utils.get_bp_content(tmpbp, 'android_app'), {})
    temp_dir.cleanup()

  def test_get_manifest_info(self):
    """test get_manifest_info method."""
    # An instrumentation test:
    test_xml = os.path.join(
        unittest_constants.TEST_DATA_DIR,
        'foo/bar/AmSlam/test/AndroidManifest.xml',
    )
    expected = {
        'package': 'com.android.settings.tests.unit',
        'target_package': 'c0m.andr0id.settingS',
        'persistent': False,
    }
    self.assertEqual(expected, atest_utils.get_manifest_info(test_xml))

    # A target module:
    target_xml = os.path.join(
        unittest_constants.TEST_DATA_DIR, 'foo/bar/AmSlam/AndroidManifest.xml'
    )
    expected = {
        'package': 'c0m.andr0id.settingS',
        'target_package': '',
        'persistent': False,
    }
    self.assertEqual(expected, atest_utils.get_manifest_info(target_xml))


class GetTradefedInvocationTimeTest(fake_filesystem_unittest.TestCase):
  """Tests of get_tradefed_invocation_time for various conditions."""

  def setUp(self):
    self.setUpPyfakefs()
    self.log_path = '/somewhere/atest/log'

  def test_get_tradefed_invocation_time_second_only(self):
    """Test the parser can handle second and millisecond properly."""
    end_host_log_file = Path(
        self.log_path,
        'inv_hashed_path',
        'inv_hashed_subpath',
        'end_host_log_test1.txt',
    )
    contents = """
=============== Consumed Time ==============
    x86_64 HelloWorldTests: 1s
    x86_64 hallo-welt: 768 ms
Total aggregated tests run time: 1s
============== Modules Preparation Times ==============
    x86_64 HelloWorldTests => prep = 2580 ms || clean = 298 ms
    x86_64 hallo-welt => prep = 1736 ms || clean = 243 ms
Total preparation time: 4s  ||  Total tear down time: 541 ms
=======================================================
=============== Summary ===============
Total Run time: 6s
2/2 modules completed
Total Tests       : 3
PASSED            : 3
FAILED            : 0
============== End of Results =============="""
    self.fs.create_file(end_host_log_file, contents=contents)
    test = 1 * 1000 + 768
    prep = 2580 + 1736
    teardown = 298 + 243
    expected_elapsed_time = (test, prep, teardown)

    actual_elapsed_time = atest_utils.get_tradefed_invocation_time(
        self.log_path
    )

    self.assertEqual(actual_elapsed_time, expected_elapsed_time)

  def test_get_tradefed_invocation_time_from_hours_to_milliseconds(self):
    """Test whether the parse can handle from hour to ms properly."""
    end_host_log_file = Path(
        self.log_path,
        'inv_hashed_path',
        'inv_hashed_subpath',
        'end_host_log_test2.txt',
    )
    contents = """
=============== Consumed Time ==============
    x86_64 HelloWorldTests: 27m 19s
    x86_64 hallo-welt: 3m 2s
Total aggregated tests run time: 31m
============== Modules Preparation Times ==============
    x86_64 HelloWorldTests => prep = 2580 ms || clean = 1298 ms
    x86_64 hallo-welt => prep = 1736 ms || clean = 1243 ms
Total preparation time: 1h 24m 17s ||  Total tear down time: 3s
=======================================================
=============== Summary ===============
Total Run time: 2h 5m 17s
2/2 modules completed
Total Tests       : 3
PASSED            : 3
FAILED            : 0
============== End of Results =============="""
    self.fs.create_file(end_host_log_file, contents=contents)
    test = (27 * 60 + 19) * 1000 + (3 * 60 + 2) * 1000
    prep = 2580 + 1736
    teardown = 1298 + 1243
    expected_elapsed_time = (test, prep, teardown)

    actual_elapsed_time = atest_utils.get_tradefed_invocation_time(
        self.log_path
    )

    self.assertEqual(actual_elapsed_time, expected_elapsed_time)

  def test_get_tradefed_invocation_time_null_result(self):
    """Test whether the parser returns null tuple when no keywords found."""
    end_host_log_file = Path(
        self.log_path,
        'inv_hashed_path',
        'inv_hashed_subpath',
        'end_host_log_test4.txt',
    )
    contents = 'some\ncontext'
    self.fs.create_file(end_host_log_file, contents=contents)
    expected_elapsed_time = (0, 0, 0)

    actual_elapsed_time = atest_utils.get_tradefed_invocation_time(
        self.log_path
    )

    self.assertEqual(actual_elapsed_time, expected_elapsed_time)


if __name__ == '__main__':
  unittest.main()
