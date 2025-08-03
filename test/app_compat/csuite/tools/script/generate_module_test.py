# Lint as: python3
#
# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Tests for the generate_module package."""

import io
import os
from xml.etree import cElementTree as ET
import csuite_test
import generate_module
from pyfakefs import fake_filesystem_unittest

_AUTO_GENERATE_NOTE = 'THIS FILE WAS AUTO-GENERATED. DO NOT EDIT MANUALLY!'


class WriteTestModuleTest(csuite_test.TestCase):

  def test_output_contains_license(self):
    out = io.StringIO()

    generate_module.write_module(generate_module.DEFAULT_BUILD_MODULE_TEMPLATE,
                                 'a.package.name', out)

    self.assertIn('Copyright', out.getvalue())
    self.assertIn('Android Open Source Project', out.getvalue())

  def test_output_is_valid_xml(self):
    out = io.StringIO()

    generate_module.write_module(generate_module.DEFAULT_TEST_MODULE_TEMPLATE,
                                 'a.package.name', out)

    self.assert_valid_xml(out.getvalue())

  def test_output_contains_package_name(self):
    package_name = 'a.package.name'
    out = io.StringIO()

    generate_module.write_module(generate_module.DEFAULT_TEST_MODULE_TEMPLATE,
                                 'a.package.name', out)

    self.assertIn(package_name, out.getvalue())

  def assert_valid_xml(self, xml_str: bytes) -> None:
    try:
      ET.parse(io.BytesIO(xml_str.encode('utf8')))
    except ET.ParseError as e:
      self.fail('Input \'%s\' is not a valid XML document: %s' % (xml_str, e))


class WriteBuildModuleTest(csuite_test.TestCase):

  def test_output_contains_license(self):
    out = io.StringIO()

    generate_module.write_module(generate_module.DEFAULT_BUILD_MODULE_TEMPLATE,
                                 'a.package.name', out)

    self.assertIn('Copyright', out.getvalue())
    self.assertIn('Android Open Source Project', out.getvalue())

  def test_output_is_valid_build_file(self):
    package_name = 'a.package.name'
    out = io.StringIO()

    generate_module.write_module(generate_module.DEFAULT_BUILD_MODULE_TEMPLATE,
                                 'a.package.name', out)

    out_str = out.getvalue()
    self.assert_braces_balanced(out_str)
    self.assertIn('csuite_config', out_str)
    self.assertIn(package_name, out_str)

  def assert_braces_balanced(self, generated_str: bytes) -> None:
    """Checks whether all braces in the provided string are balanced."""
    count = 0

    for c in generated_str:
      if c == '{':
        count += 1
      elif c == '}':
        count -= 1

      if count < 0:
        break

    self.assertEqual(count, 0,
                     'Braces in \'%s\' are not balanced' % generated_str)


class ParsePackageListTest(csuite_test.TestCase):

  def test_accepts_empty_lines(self):
    lines = io.StringIO('\n\n\npackage_name\n\n')

    package_list = generate_module.parse_package_list(lines)

    self.assertListEqual(['package_name'], list(package_list))

  def test_strips_trailing_whitespace(self):
    lines = io.StringIO('  package_name  ')

    package_list = generate_module.parse_package_list(lines)

    self.assertListEqual(['package_name'], list(package_list))

  def test_duplicate_package_name(self):
    lines = io.StringIO('\n\npackage_name\n\npackage_name\n')

    package_list = generate_module.parse_package_list(lines)

    self.assertListEqual(['package_name'], list(package_list))

  def test_ignore_comment_lines(self):
    lines = io.StringIO('\n# Comments.\npackage_name\n')

    package_list = generate_module.parse_package_list(lines)

    self.assertListEqual(['package_name'], list(package_list))


class ParseArgsTest(fake_filesystem_unittest.TestCase):

  def setUp(self):
    super(ParseArgsTest, self).setUp()
    self.setUpPyfakefs()

  def test_configuration_file_not_exist(self):
    package_list_file_path = '/test/package_list.txt'
    root_dir = '/test/modules'
    os.makedirs(root_dir)

    with self.assertRaises(SystemExit):
      generate_module.parse_args(
          ['--package-list', package_list_file_path, '--root-dir', root_dir],
          out=io.StringIO(),
          err=io.StringIO())

  def test_module_dir_not_exist(self):
    package_list_file_path = '/test/package_list.txt'
    package_name1 = 'package_name_1'
    package_name2 = 'package_name_2'
    self.fs.create_file(
        package_list_file_path, contents=(package_name1 + '\n' + package_name2))
    root_dir = '/test/modules'

    with self.assertRaises(SystemExit):
      generate_module.parse_args(
          ['--package-list', package_list_file_path, '--root-dir', root_dir],
          out=io.StringIO(),
          err=io.StringIO())

  def test_test_module_template_file_not_exist(self):
    package_list_file_path = '/test/package_list.txt'
    package_name1 = 'package_name_1'
    package_name2 = 'package_name_2'
    self.fs.create_file(
        package_list_file_path, contents=(package_name1 + '\n' + package_name2))
    root_dir = '/test/modules'
    os.makedirs(root_dir)
    template_file_path = '/test/template.txt'

    with self.assertRaises(SystemExit):
      generate_module.parse_args([
          '--package-list', package_list_file_path, '--root-dir', root_dir,
          '--test', template_file_path
      ],
                                 out=io.StringIO(),
                                 err=io.StringIO())

  def test_build_module_template_file_not_exist(self):
    package_list_file_path = '/test/package_list.txt'
    package_name1 = 'package_name_1'
    package_name2 = 'package_name_2'
    self.fs.create_file(
        package_list_file_path, contents=(package_name1 + '\n' + package_name2))
    root_dir = '/test/modules'
    os.makedirs(root_dir)
    template_file_path = '/test/template.txt'

    with self.assertRaises(SystemExit):
      generate_module.parse_args([
          '--package-list', package_list_file_path, '--root-dir', root_dir,
          '--template', template_file_path
      ],
                                 out=io.StringIO(),
                                 err=io.StringIO())


class GenerateAllModulesFromConfigTest(fake_filesystem_unittest.TestCase):

  def setUp(self):
    super(GenerateAllModulesFromConfigTest, self).setUp()
    self.setUpPyfakefs()

  def test_creates_package_files(self):
    package_list_file_path = '/test/package_list.txt'
    package_name1 = 'package_name_1'
    package_name2 = 'package_name_2'
    self.fs.create_file(
        package_list_file_path, contents=(package_name1 + '\n' + package_name2))
    root_dir = '/test/modules'
    self.fs.create_dir(root_dir)

    generate_module.generate_all_modules_from_config(package_list_file_path,
                                                     root_dir)

    self.assertTrue(
        os.path.exists(os.path.join(root_dir, package_name1, 'Android.bp')))
    self.assertTrue(
        os.path.exists(
            os.path.join(root_dir, package_name1, 'AndroidTest.xml')))
    self.assertTrue(
        os.path.exists(os.path.join(root_dir, package_name2, 'Android.bp')))
    self.assertTrue(
        os.path.exists(
            os.path.join(root_dir, package_name2, 'AndroidTest.xml')))

  def test_removes_all_existing_package_files(self):
    root_dir = '/test/'
    package_dir = '/test/existing_package/'
    self.fs.create_file(
        'test/existing_package/AndroidTest.xml', contents=_AUTO_GENERATE_NOTE)
    self.fs.create_file(
        'test/existing_package/Android.bp', contents=_AUTO_GENERATE_NOTE)

    generate_module.remove_existing_package_files(root_dir)

    self.assertFalse(os.path.exists(package_dir))


if __name__ == '__main__':
  csuite_test.main()
